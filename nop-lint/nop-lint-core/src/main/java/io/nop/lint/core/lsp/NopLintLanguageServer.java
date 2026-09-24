package io.nop.lint.core.lsp;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.cli.TargetScanner;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.rule.RuleDslModel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The transport-agnostic LSP core (roadmap item 41): implements the
 * editor-facing v1 surface — {@code initialize}, {@code
 * textDocument/didOpen}, {@code textDocument/didChange} (full-document
 * sync), {@code textDocument/didClose}, {@code shutdown}/{@code exit} —
 * with diagnostics pushed through a caller-supplied sink as {@code
 * textDocument/publishDiagnostics}. Transport (stdio Content-Length
 * framing) lives in {@link NopLintLspLauncher}; the core is plain Java so
 * the whole lifecycle is unit-testable.
 *
 * <p>The editor bundle is the item-38 lifecycle shape: languages are
 * discovered once, the rule set is loaded once, and the engine is pinned to
 * {@link LintProfile#FAST} — the design 11 fast-profile budget is the whole
 * point of the editor face. didChange reuses the previous tree through
 * {@code parseIncremental} (roadmap item 16), so an edit re-lexes only the
 * changed region.</p>
 *
 * <p>Fail-closed faces: an unregistered language aborts that message with a
 * structured error (never an empty-diagnostic success); the client's
 * {@code languageId} wins over the URI extension, which falls back to the
 * scanner's extension table.</p>
 */
public final class NopLintLanguageServer {

    private static final int SEVERITY_ERROR = 1;
    private static final int SEVERITY_WARNING = 2;
    private static final int SEVERITY_INFO = 3;
    private static final int SEVERITY_HINT = 4;

    private final LanguageRegistry registry;
    private final List<RuleDslModel> rules;
    private final LintEngine engine;
    private final Map<String, OpenDocument> documents = new ConcurrentHashMap<>();
    private volatile boolean shutdownRequested;

    /**
     * The production bundle (item 38 lifecycle shape): discovered
     * languages + the conventional rule set + the FAST engine.
     */
    public static NopLintLanguageServer create() {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        List<RuleDslModel> rules = new ArrayList<>();
        new RuleSetLoader().loadRuleSet(RuleSetLoader.DEFAULT_RULES_PREFIX)
                .rulesByLanguage().values().forEach(rules::addAll);
        return new NopLintLanguageServer(registry, rules);
    }

    public NopLintLanguageServer(LanguageRegistry registry, List<RuleDslModel> rules) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.rules = List.copyOf(rules);
        this.engine = new LintEngine(registry, LintProfile.FAST);
    }

    /**
     * Handles one decoded JSON-RPC message. Requests (with an id) return a
     * response map; notifications drive the {@code sink} — called with the
     * URI and the diagnostic list — and return null. Unknown methods fail
     * the response (never a silent no-op).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> onMessage(Map<String, Object> message, DiagnosticsSink sink) {
        String method = (String) message.get("method");
        Object id = message.get("id");
        Map<String, Object> params = (Map<String, Object>) message.getOrDefault("params",
                Map.of());
        try {
            switch (method == null ? "" : method) {
                case "initialize":
                    Map<String, Object> capabilities = new HashMap<>();
                    capabilities.put("textDocumentSync", 1); // full document sync
                    return result(id, Map.of("capabilities", capabilities));
                case "initialized":
                    return null;
                case "shutdown":
                    shutdownRequested = true;
                    return result(id, null);
                case "exit":
                    return null;
                case "textDocument/didOpen":
                    didOpen(params, sink);
                    return null;
                case "textDocument/didChange":
                    didChange(params, sink);
                    return null;
                case "textDocument/didClose":
                    didClose(params, sink);
                    return null;
                default:
                    throw new NopLintException("unsupported LSP method '" + method + "'");
            }
        } catch (Exception e) {
            if (id == null) {
                // a failed notification must still surface: the transport
                // reports it on stderr, it is never silently dropped
                throw e instanceof RuntimeException re ? re : new NopLintException(
                        "LSP notification failed: " + e.getMessage(), e);
            }
            return error(id, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    public boolean isShutdownRequested() {
        return shutdownRequested;
    }

    private void didOpen(Map<String, Object> params, DiagnosticsSink sink) {
        Map<String, Object> doc = (Map<String, Object>) params.get("textDocument");
        String uri = (String) doc.get("uri");
        String text = (String) doc.get("text");
        String clientLanguageId = (String) doc.get("languageId");
        if (uri == null || text == null) {
            throw new NopLintException("didOpen requires textDocument.uri and .text");
        }
        LintLanguageBridge bridge = resolveLanguage(clientLanguageId, uri);
        LintTree tree = bridge.language().parse(text);
        OpenDocument open = new OpenDocument(uri, bridge, text, tree);
        documents.put(uri, open);
        lintAndPublish(open, sink);
    }

    @SuppressWarnings("unchecked")
    private void didChange(Map<String, Object> params, DiagnosticsSink sink) {
        String uri = (String) ((Map<String, Object>) params.get("textDocument")).get("uri");
        OpenDocument previous = documents.get(uri);
        if (previous == null) {
            throw new NopLintException("didChange for a document that was never opened: " + uri);
        }
        List<Map<String, Object>> changes = (List<Map<String, Object>>) params.get("contentChanges");
        if (changes == null || changes.isEmpty()) {
            throw new NopLintException("didChange requires a contentChanges entry");
        }
        String text = (String) changes.get(changes.size() - 1).get("text");

        // item 16: the previous tree is reused — parseIncremental re-lexes
        // only the changed region of a full-text resync
        LintTree newTree = previous.language.parseIncremental(previous.tree,
                text.getBytes(StandardCharsets.UTF_8));
        OpenDocument updated = new OpenDocument(uri, previous.bridge, text, newTree);
        documents.put(uri, updated);
        lintAndPublish(updated, sink);
    }

    private void didClose(Map<String, Object> params, DiagnosticsSink sink) {
        String uri = (String) ((Map<String, Object>) params.get("textDocument")).get("uri");
        documents.remove(uri);
        sink.publish(uri, List.of());
    }

    private void lintAndPublish(OpenDocument doc, DiagnosticsSink sink) {
        LintResult result = engine.lint(rules, doc.languageId(), doc.uri, doc.text);
        LineIndex lines = new LineIndex(doc.text);
        List<Map<String, Object>> diagnostics = new ArrayList<>(result.diagnostics().size());
        for (Diagnostic diagnostic : result.diagnostics()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("range", range(lines, doc.utf8, diagnostic.range().startByte(),
                    diagnostic.range().endByte()));
            view.put("severity", severity(diagnostic.severity()));
            view.put("code", diagnostic.ruleId());
            view.put("source", "nop-lint");
            view.put("message", diagnostic.message());
            diagnostics.add(view);
        }
        sink.publish(doc.uri, diagnostics);
    }

    private LintLanguageBridge resolveLanguage(String clientLanguageId, String uri) {
        String id = clientLanguageId;
        if (id == null || id.isBlank()) {
            String name = uri.substring(uri.lastIndexOf('/') + 1);
            int dot = name.lastIndexOf('.');
            id = TargetScanner.languageIdForExtension(dot < 0 ? TargetScanner.NO_EXTENSION
                    : name.substring(dot + 1));
        }
        return new LintLanguageBridge(registry.resolve(id), id.toLowerCase(Locale.ROOT));
    }

    private static int severity(String severity) {
        return switch (severity) {
            case "error" -> SEVERITY_ERROR;
            case "warning" -> SEVERITY_WARNING;
            case "info" -> SEVERITY_INFO;
            case "hint" -> SEVERITY_HINT;
            default -> SEVERITY_INFO;
        };
    }

    /**
     * The LSP position is a 0-based line plus a UTF-16 character offset
     * within the line; the engine speaks 1-based lines and UTF-8 byte
     * offsets, so both axes are converted here (the character axis via the
     * UTF-16 length of the prefix text).
     */
    static Map<String, Object> position(LineIndex lines, byte[] utf8, int byteOffset) {
        int line = lines.lineOfByte(byteOffset); // 1-based
        int lineStart = lines.lineStartByte(line);
        int character = new String(utf8, lineStart, byteOffset - lineStart,
                StandardCharsets.UTF_8).length();
        Map<String, Object> pos = new LinkedHashMap<>();
        pos.put("line", line - 1);
        pos.put("character", character);
        return pos;
    }

    static Map<String, Object> range(LineIndex lines, byte[] utf8, int startByte, int endByte) {
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("start", position(lines, utf8, startByte));
        // the LSP range end is EXCLUSIVE: the position of endByte itself —
        // an off-by-one here systematically underlines one character short
        range.put("end", position(lines, utf8, endByte));
        return range;
    }

    private static Map<String, Object> result(Object id, Object resultValue) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", resultValue);
        return response;
    }

    private static Map<String, Object> error(Object id, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", -32603, "message", message));
        return response;
    }

    private record LintLanguageBridge(io.nop.lint.core.lang.LintLanguage language,
                                      String languageId) {
    }

    private static final class OpenDocument {
        final String uri;
        final LintLanguageBridge bridge;
        final String text;
        final byte[] utf8;
        final io.nop.lint.core.lang.LintLanguage language;
        final LintTree tree;

        OpenDocument(String uri, LintLanguageBridge bridge, String text, LintTree tree) {
            this.uri = uri;
            this.bridge = bridge;
            this.text = text;
            this.utf8 = text.getBytes(StandardCharsets.UTF_8);
            this.language = bridge.language();
            this.tree = tree;
        }

        String languageId() {
            return bridge.languageId();
        }
    }

    /**
     * The diagnostics push face — the transport turns it into a JSON-RPC
     * notification.
     */
    public interface DiagnosticsSink {
        void publish(String uri, List<Map<String, Object>> diagnostics);
    }
}
