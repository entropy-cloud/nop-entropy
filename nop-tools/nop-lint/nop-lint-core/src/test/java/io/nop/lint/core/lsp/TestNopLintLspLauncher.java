package io.nop.lint.core.lsp;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.testing.JavaBindingTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end over the wire format (Minimum Rules #22): real {@code
 * Content-Length}-framed requests flow through {@link NopLintLspLauncher}'s
 * frame loop to the server, and the framed stdout is parsed back out — the
 * initialize response and the publishDiagnostics notification are asserted
 * as decoded JSON-RPC messages, never through a mock transport.
 */
public class TestNopLintLspLauncher {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static String frame(String body) {
        return "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length
                + "\r\n\r\n" + body;
    }

    private static List<Map<String, Object>> unframe(String stdout) {
        List<Map<String, Object>> messages = new ArrayList<>();
        int at = 0;
        while (at < stdout.length()) {
            int headerEnd = stdout.indexOf("\r\n\r\n", at);
            int length = Integer.parseInt(stdout.substring(at, headerEnd)
                    .split(":")[1].trim());
            String body = stdout.substring(headerEnd + 4, headerEnd + 4 + length);
            messages.add(JsonTool.parseMap(body));
            at = headerEnd + 4 + length;
        }
        return messages;
    }

    @Test
    public void framedLifecycleProducesResponseAndDiagnosticsNotification() throws Exception {
        List<io.nop.lint.core.rule.RuleDslModel> rules = new ArrayList<>();
        new RuleSetLoader().loadRuleSet("/test/lint/cli-rules")
                .rulesByLanguage().values().forEach(rules::addAll);
        NopLintLanguageServer server = new NopLintLanguageServer(
                JavaBindingTestSupport.registryWithJava(), rules);

        StringBuilder wire = new StringBuilder();
        wire.append(frame(JsonTool.serialize(Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "initialize",
                "params", Map.of()), false)));
        wire.append(frame(JsonTool.serialize(Map.of(
                "jsonrpc", "2.0", "method", "textDocument/didOpen",
                "params", Map.of("textDocument", Map.of(
                        "uri", "file:///demo/Warn.java",
                        "languageId", "java",
                        "text", "class Warn {\n    void x() {\n"
                                + "        System.out.println(\"w\");\n    }\n}\n"))), false)));
        wire.append(frame(JsonTool.serialize(Map.of(
                "jsonrpc", "2.0", "id", 2, "method", "shutdown"), false)));
        wire.append(frame(JsonTool.serialize(Map.of(
                "jsonrpc", "2.0", "method", "exit"), false)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NopLintLspLauncher launcher = new NopLintLspLauncher(server,
                new ByteArrayInputStream(wire.toString().getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream()));
        launcher.run();

        List<Map<String, Object>> messages = unframe(out.toString(StandardCharsets.UTF_8));
        assertTrue(messages.size() >= 2, "at least the response and one notification: "
                + messages);

        Map<String, Object> initResponse = messages.get(0);
        assertEquals(1, initResponse.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) initResponse.get("result");
        assertNotNull(result.get("capabilities"));

        Map<String, Object> diagnosticsNotification = messages.stream()
                .filter(m -> "textDocument/publishDiagnostics".equals(m.get("method")))
                .findFirst().orElse(null);
        assertNotNull(diagnosticsNotification,
                "the didOpen must push diagnostics: " + messages);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) diagnosticsNotification.get("params");
        assertEquals("file:///demo/Warn.java", params.get("uri"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> diagnostics =
                (List<Map<String, Object>>) params.get("diagnostics");
        assertEquals(1, diagnostics.size());
        assertEquals("demo/no-print", diagnostics.get(0).get("code"));
        assertTrue(server.isShutdownRequested(), "the shutdown request was processed");
    }
}
