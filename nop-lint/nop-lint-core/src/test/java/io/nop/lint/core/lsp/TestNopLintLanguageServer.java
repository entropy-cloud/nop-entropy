package io.nop.lint.core.lsp;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.testing.JavaBindingTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The LSP core's lifecycle (roadmap item 41): initialize capabilities, the
 * didOpen → publishDiagnostics push carrying the real rule id and LSP
 * 0-based coordinates, the didChange refresh over the incremental parse,
 * the didClose clear, and the fail-closed faces (unknown method, unknown
 * language). All messages run through {@link NopLintLanguageServer#onMessage}
 * with a recording sink.
 */
public class TestNopLintLanguageServer {

    private static NopLintLanguageServer server;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        List<io.nop.lint.core.rule.RuleDslModel> rules = new ArrayList<>();
        new RuleSetLoader().loadRuleSet("/test/lint/cli-rules")
                .rulesByLanguage().values().forEach(rules::addAll);
        server = new NopLintLanguageServer(
                JavaBindingTestSupport.registryWithJava(), rules);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static final String VIOLATING = "class Warn {\n    void x() {\n"
            + "        System.out.println(\"w\");\n    }\n}\n";

    private record Publish(String uri, List<Map<String, Object>> diagnostics) {
    }

    private static class RecordingSink implements NopLintLanguageServer.DiagnosticsSink {
        final List<Publish> publishes = new ArrayList<>();

        @Override
        public void publish(String uri, List<Map<String, Object>> diagnostics) {
            publishes.add(new Publish(uri, diagnostics));
        }

        Publish last() {
            return publishes.get(publishes.size() - 1);
        }
    }

    private static Map<String, Object> request(String method, Object id, Map<String, Object> params) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("jsonrpc", "2.0");
        msg.put("method", method);
        if (id != null) {
            msg.put("id", id);
        }
        if (params != null) {
            msg.put("params", params);
        }
        return msg;
    }

    private static Map<String, Object> textDocument(String uri, String languageId, String text) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("uri", uri);
        if (languageId != null) {
            doc.put("languageId", languageId);
        }
        doc.put("text", text);
        return Map.of("textDocument", doc);
    }

    @Test
    public void initializeAdvertisesFullSyncCapabilities() {
        Map<String, Object> response = server.onMessage(
                request("initialize", 1, Map.of()), new RecordingSink());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
        assertEquals(1, capabilities.get("textDocumentSync"));
    }

    @Test
    public void didOpenPushesDiagnosticsWithLspCoordinates() {
        RecordingSink sink = new RecordingSink();
        Map<String, Object> params = new HashMap<>(textDocument(
                "file:///demo/Warn.java", "java", VIOLATING));
        params.put("textDocument", ((Map<String, Object>) params.get("textDocument")));
        server.onMessage(request("textDocument/didOpen", null, params), sink);

        Publish publish = sink.last();
        assertEquals("file:///demo/Warn.java", publish.uri());
        assertEquals(1, publish.diagnostics().size());
        Map<String, Object> diagnostic = publish.diagnostics().get(0);
        assertEquals("demo/no-print", diagnostic.get("code"));
        assertEquals(2, diagnostic.get("severity"));
        @SuppressWarnings("unchecked")
        Map<String, Object> range = (Map<String, Object>) diagnostic.get("range");
        @SuppressWarnings("unchecked")
        Map<String, Object> start = (Map<String, Object>) range.get("start");
        // the println sits on source line 3 (1-based) → LSP line 2
        assertEquals(2, start.get("line"));
    }

    @Test
    public void didChangeRefreshesDiagnosticsOverTheIncrementalParse() {
        RecordingSink sink = new RecordingSink();
        String uri = "file:///demo/Change.java";
        Map<String, Object> openParams = new HashMap<>(
                textDocument(uri, "java", VIOLATING));
        server.onMessage(request("textDocument/didOpen", null, openParams), sink);

        Map<String, Object> changeParams = Map.of(
                "textDocument", Map.of("uri", uri),
                "contentChanges", List.of(Map.of("text", "class Clean {\n}\n")));
        server.onMessage(request("textDocument/didChange", null, changeParams), sink);

        Publish publish = sink.last();
        assertEquals(uri, publish.uri());
        assertTrue(publish.diagnostics().isEmpty(),
                "the edited content is clean: " + publish.diagnostics());
    }

    @Test
    public void didCloseClearsDiagnostics() {
        RecordingSink sink = new RecordingSink();
        String uri = "file:///demo/Close.java";
        server.onMessage(request("textDocument/didOpen", null,
                new HashMap<>(textDocument(uri, "java", VIOLATING))), sink);
        server.onMessage(request("textDocument/didClose", null,
                Map.of("textDocument", Map.of("uri", uri))), sink);

        assertTrue(sink.last().diagnostics().isEmpty());
    }

    @Test
    public void unknownMethodFailsTheRequest() {
        Map<String, Object> response = server.onMessage(
                request("textDocument/quickFix", 9, Map.of()), new RecordingSink());
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("error");
        assertTrue(String.valueOf(error.get("message")).contains("quickFix"));
    }

    @Test
    public void unknownLanguageFailsTheNotificationLoudly() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> server.onMessage(request("textDocument/didOpen", null,
                        new HashMap<>(textDocument("file:///x.sig", "unknown-lang", "[]"))),
                        new RecordingSink()));
        assertTrue(ex.getMessage().contains("unknown-lang"), ex.getMessage());
    }

    @Test
    public void shutdownMarksTheServer() {
        server.onMessage(request("shutdown", 77, Map.of()), new RecordingSink());
        assertTrue(server.isShutdownRequested());
    }
}
