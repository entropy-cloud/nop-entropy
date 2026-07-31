package io.nop.ai.core.api.tool;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.json.JsonSchema;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;

import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA4.3-04 focused tests for {@link DefaultAiChatFunctionTool} / {@link ToolSpecification}.
 */
public class TestDefaultAiChatFunctionTool {

    @BizModel("TestToolService")
    public static class TestToolService {
        public static String greet(@Name("name") String name) {
            return "hello " + name;
        }

        public static int add(@Name("a") int a, @Name("b") int b) {
            return a + b;
        }
    }

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static IFunctionModel method(String name, int argCount) {
        return ReflectionManager.instance().getClassModel(TestToolService.class).getStaticMethod(name, argCount);
    }

    @Test
    public void testFromMethodBuildsTool() {
        DefaultAiChatFunctionTool tool = DefaultAiChatFunctionTool.fromMethod(method("greet", 1));
        assertEquals("TestToolService__greet", tool.getName());
        assertNotNull(tool.getInputSchema(), "input schema must be derived from method parameters");
        assertEquals("object", tool.getInputSchema().getType());
    }

    @Test
    public void testCallToolInvokesFunction() {
        DefaultAiChatFunctionTool tool = DefaultAiChatFunctionTool.fromMethod(method("greet", 1));
        Object result = tool.callTool(Map.of("name", "nop"));
        assertEquals("hello nop", result);
    }

    @Test
    public void testCallToolAsyncInvokesFunction() throws Exception {
        DefaultAiChatFunctionTool tool = DefaultAiChatFunctionTool.fromMethod(method("add", 2));
        CompletionStage<Object> stage = tool.callToolAsync(Map.of("a", 2, "b", 3));
        assertEquals(5, stage.toCompletableFuture().get());
    }

    @Test
    public void testToSpecRoundTrip() {
        DefaultAiChatFunctionTool tool = DefaultAiChatFunctionTool.fromMethod(method("greet", 1));
        tool.setDescription("greet the user");
        ToolSpecification spec = tool.toSpec();
        assertEquals("TestToolService__greet", spec.getName());
        assertEquals("greet the user", spec.getDescription());
        JsonSchema schema = spec.getInputSchema();
        assertNotNull(schema);
        assertTrue(schema.getProperties().containsKey("name"),
                "spec input schema must expose the name parameter");
        assertEquals(tool.toSpec(), tool.toSpec(), "toSpec must be stable (cached)");
    }
}
