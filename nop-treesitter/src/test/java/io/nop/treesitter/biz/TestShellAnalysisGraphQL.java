package io.nop.treesitter.biz;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.GraphQLEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end through the GraphQL engine (Minimum Rules #22) and the real bean
 * file (Minimum Rules #23): a query reaches the container-wired
 * {@link ShellAnalysisBizModel}, which resolves the bash grammar through the
 * provider — including its external scanner — and returns the layer's structured
 * output.
 *
 * <p>The path exercised is the one an agent actually uses, so nothing here
 * constructs the analyzer by hand.</p>
 */
class TestShellAnalysisGraphQL {

    private static GraphQLEngine engine;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        ShellAnalysisBizModel bizModel = BeanContainer.getBeanByType(ShellAnalysisBizModel.class);
        assertNotNull(bizModel, "the shell facade must be registered in app-treesitter.beans.xml");
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new TestParseTreeSitterGraphQL.BizModelSchemaLoader(bizModel));
        engine.init();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static ApiRequest<Map<String, Object>> request(Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> runQuery(String query, Map<String, Object> data) {
        IGraphQLExecutionContext context = engine.newRpcContext(GraphQLOperationType.query, query,
                request(data));
        ApiResponse<?> response = FutureHelper.syncGet(engine.executeRpcAsync(context));
        assertTrue(response.isOk(), String.valueOf(response));
        return (Map<String, Object>) response.get();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> runListQuery(String query, Map<String, Object> data) {
        IGraphQLExecutionContext context = engine.newRpcContext(GraphQLOperationType.query, query,
                request(data));
        ApiResponse<?> response = FutureHelper.syncGet(engine.executeRpcAsync(context));
        assertTrue(response.isOk(), String.valueOf(response));
        return (List<Map<String, Object>>) response.get();
    }

    @Test
    void provenSplitReachesTheLayerThroughTheGraphQlEntry() {
        Map<String, Object> data = new HashMap<>();
        data.put("source", "cat /etc/passwd\n");
        data.put("language", "bash");

        Map<String, Object> result =
                runQuery("TreeSitterShell__splitProvenShellCommands", data);
        assertEquals(Boolean.TRUE, result.get("proven"));
        List<?> commands = (List<?>) result.get("commands");
        assertEquals(1, commands.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> command = (Map<String, Object>) commands.get(0);
        assertEquals("cat", command.get("name"));
    }

    @Test
    void anUnprovableScriptIsRefusedRatherThanReturnedEmpty() {
        Map<String, Object> data = new HashMap<>();
        data.put("source", "if then rm -rf /tmp/example\n");
        data.put("language", "bash");

        Map<String, Object> result =
                runQuery("TreeSitterShell__splitProvenShellCommands", data);
        assertEquals(Boolean.FALSE, result.get("proven"));
        assertEquals("SYNTAX_ERROR", result.get("reason"));
        assertFalse(result.containsKey("commands"),
                "a refusal must not carry a command list that reads as 'no commands'");
    }

    @Test
    void theSummaryCarriesTheProofStatusAsAField() {
        Map<String, Object> data = new HashMap<>();
        data.put("source", "echo hi; ls -la | grep x\n");
        data.put("language", "bash");

        Map<String, Object> result = runQuery("TreeSitterShell__analyzeShell", data);
        assertEquals("PROVEN", result.get("proofStatus"));
        assertEquals(List.of("echo", "ls", "grep"), result.get("commandNames"));
        assertEquals(List.of(";", "|"), result.get("connectors"));
    }

    @Test
    void theLiteralHarvestIsNamedAndFlaggedAsIncomplete() {
        Map<String, Object> data = new HashMap<>();
        data.put("source", "echo \"$(rm -rf /tmp/x)\"\n");
        data.put("language", "bash");

        List<Map<String, Object>> rows = runListQuery("TreeSitterShell__listLiteralCommands", data);
        assertEquals(2, rows.size(), "the command inside the substitution is a command too");
        assertEquals("echo", rows.get(0).get("name"));
        assertEquals("rm", rows.get(1).get("name"));

        Map<String, Object> partialData = new HashMap<>();
        partialData.put("source", "rm -rf $TARGET /data\n");
        partialData.put("language", "bash");
        List<Map<String, Object>> partial = runListQuery("TreeSitterShell__listLiteralCommands", partialData);
        assertEquals(1, partial.size());
        assertEquals(Boolean.TRUE, partial.get(0).get("partial"),
                "an incomplete argv must say so on the row itself");
    }

    @Test
    void heredocExtractionReachesTheLayer() {
        Map<String, Object> data = new HashMap<>();
        data.put("source", "apply_patch <<'PATCH'\nbody\nPATCH\n");
        data.put("language", "bash");
        data.put("allowedCommands", List.of());

        Map<String, Object> result = runQuery("TreeSitterShell__extractShellHeredoc", data);
        assertEquals(Boolean.TRUE, result.get("found"));
        assertEquals("'PATCH'", result.get("delimiter"));
        assertEquals("body\n", result.get("body"));
        assertEquals(Boolean.TRUE, result.get("bodyIsLiteral"));
    }

    /** The fixed agent-style fixture, end to end through every capability. */
    @Test
    void theFixtureAnalysesEndToEndThroughGraphQl() throws Exception {
        String fixture = Files.readString(Path.of("src/test/resources/shell/agent-composite.sh"),
                StandardCharsets.UTF_8);
        Map<String, Object> data = new HashMap<>();
        data.put("source", fixture);
        data.put("language", "bash");

        Map<String, Object> result = runQuery("TreeSitterShell__analyzeShell", data);

        assertEquals("NOT_PROVEN", result.get("proofStatus"));
        assertEquals(Boolean.TRUE, result.get("hasDynamicConstructs"));

        @SuppressWarnings("unchecked")
        List<String> kinds = (List<String>) result.get("dynamicConstructKinds");
        assertTrue(kinds.contains("HEREDOC_BODY"), kinds.toString());
        assertTrue(kinds.contains("BACKGROUND_OPERATOR"), kinds.toString());
        assertTrue(kinds.contains("CONCATENATION"), kinds.toString());
        assertTrue(kinds.contains("COMMAND_SUBSTITUTION"), kinds.toString());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paths = (List<Map<String, Object>>) result.get("paths");
        List<String> pathValues = paths.stream().map(p -> String.valueOf(p.get("path"))).toList();
        assertTrue(pathValues.contains("/etc/config"), pathValues.toString());
        assertTrue(pathValues.contains("/tmp/config.bak"), pathValues.toString());
        for (Map<String, Object> path : paths) {
            assertEquals("HARVESTED", path.get("confidence"),
                    "nothing in an unproven script may claim to be proven-sourced");
        }
    }

    @Test
    void anUnknownGrammarReturnsTheDefinedError() {
        Map<String, Object> data = new HashMap<>();
        data.put("source", "echo hi");
        data.put("language", "no-such-grammar");

        IGraphQLExecutionContext context = engine.newRpcContext(GraphQLOperationType.query,
                "TreeSitterShell__analyzeShell", request(data));
        ApiResponse<?> response = FutureHelper.syncGet(engine.executeRpcAsync(context));
        assertFalse(response.isOk());
        assertTrue(String.valueOf(response.getCode()).contains("unknown-language"),
                String.valueOf(response.getCode()));
    }

    /** A grammar that resolves but is not bash must be refused, not analysed. */
    @Test
    void aNonBashGrammarIsRefused() {
        Map<String, Object> data = new HashMap<>();
        data.put("source", "{\"a\": 1}");
        data.put("language", "json");

        IGraphQLExecutionContext context = engine.newRpcContext(GraphQLOperationType.query,
                "TreeSitterShell__analyzeShell", request(data));
        ApiResponse<?> response = FutureHelper.syncGet(engine.executeRpcAsync(context));
        assertFalse(response.isOk(), "the layer's contract is written against the bash grammar");
        assertTrue(String.valueOf(response.getCode()).contains("unsupported-shell-language"),
                String.valueOf(response.getCode()));
    }
}
