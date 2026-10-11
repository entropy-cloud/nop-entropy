package io.nop.ai.coder;

import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.ai.core.prompt.IPromptTemplateManager;
import io.nop.ai.core.prompt.PromptTemplateManager;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI1 end-to-end wiring proof (plan 2414 Phase 4): the ai.xlib
 * {@code TaskStep-generator} transform must run against a fixture task XML, and
 * the migrated {@code ai:Execute} tag script must execute the rebuilt
 * {@code AiCommand} on the {@code IChatService} stack — xlib chatOptions map
 * binding, ChatExchange facade return — against a mock chat service, with no
 * real LLM.
 *
 * <p>Bean isolation follows the W7GatewayTestSupport pattern: initialization is
 * stopped before the IoC initializer and a {@link StaticBeanContainer} supplies
 * exactly {@code IChatService} (mock) and {@code IPromptTemplateManager} (real),
 * which is all {@code AiCommand.create()} resolves through
 * {@code BeanContainer.getBeanByType}.
 */
public class AiTaskStepExecuteWiringTest {

    static final String XLIB_PATH = "/nop/ai/xlib/ai.xlib";

    @BeforeAll
    static void initMinimalContainer() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);

        StaticBeanContainer container = new StaticBeanContainer();

        // plan 2434/WI7：core mock 族已下沉 core test scope（coder 不可见），
        // 改用内联匿名 IChatService stub（行为断言与原 MockChatService 链等价）。
        IChatService chatService = new IChatService() {
            @Override
            public java.util.concurrent.CompletionStage<ChatResponse> callAsync(ChatRequest request,
                    io.nop.api.core.util.ICancelToken cancelToken) {
                assertEquals("mock", request.getOptions().getProvider(),
                        "xlib chatOptions map must bind onto the new ChatOptions");
                return java.util.concurrent.CompletableFuture
                        .completedFuture(ChatResponse.success(new ChatAssistantMessage("echo: hi")));
            }

            @Override
            public java.util.concurrent.Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                    ChatRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                throw new UnsupportedOperationException("stream not expected in wiring test");
            }
        };
        container.registerBean("nopChatService", chatService);

        IPromptTemplateManager promptManager = new PromptTemplateManager();
        container.registerBean("nopPromptTemplateManager", promptManager);

        BeanContainer.registerInstance(container);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private IXplTag loadTag(String tagName) {
        IXplTag tag = XplLibHelper.getTag(XLIB_PATH, tagName, true);
        assertNotNull(tag, tagName + " tag must load and compile from ai.xlib");
        return tag;
    }

    private XNode loadFixture() {
        return XNode.parse(io.nop.core.resource.VirtualFileSystem.instance()
                .getResource("/test/ai/ai-execute-wiring.task.xml").readText());
    }

    @Test
    public void testTaskStepGeneratorTransformsFixtureToExecute() {
        IXplTag generator = loadTag("TaskStep-generator");
        XNode taskNode = loadFixture();
        XNode step = taskNode.childByTag("steps").childByTag("step");
        assertNotNull(step);

        Map<String, Object> args = new HashMap<>();
        args.put("node", step);
        args.put(XLangConstants.SCOPE_VAR_DSL_ROOT, taskNode);
        XNode transformed = generator.generateNode(XLang.newEvalScope(new HashMap<String, Object>()), args);

        assertEquals("step", transformed.getTagName());
        XNode source = transformed.childByTag("source");
        XNode execNode = source == null ? null : source.childByTag("ai:Execute");
        assertNotNull(execNode, "generator must rewrite ai:TaskStep into an ai:Execute step");
        assertEquals("mock", execNode.attrText("provider"), "task-level ai:chatOptions attrs must merge onto the Execute node");
        assertEquals(Boolean.TRUE.toString(), execNode.attrText("asyncExec"));
    }

    @Test
    public void testExecuteTagRunsAgainstMockChatService() {
        loadTag("Execute"); // compiles the edited Execute script body

        IXplTag executeTag = XplLibHelper.getTag(XLIB_PATH, "Execute", true);
        Map<String, Object> args = new HashMap<>();
        args.put("promptPath", "/test/ai/execute-wiring.prompt.yaml");
        args.put("provider", "mock");
        args.put("model", "test-model");
        args.put("contextLength", 30000);
        args.put("maxTokens", 100);
        args.put("useResponseCache", false);
        args.put("asyncExec", false);
        args.put("retryTimesPerRequest", 2);
        args.put("inputs", new HashMap<>(Map.of("word", "hi")));
        args.put("workMode", null);
        args.put("attachments", null);
        args.put("cancelToken", null);

        Map<String, Object> scopeVars = new HashMap<>();
        scopeVars.put("word", "hi");
        IEvalScope scope = XLang.newEvalScope(scopeVars);
        Object result = executeTag.invokeWithNamedArgs(scope, args);

        assertTrue(result instanceof ChatExchange,
                "sync Execute must return the ChatExchange facade, actual=" + (result == null ? "null" : result.getClass()));
        ChatExchange exchange = (ChatExchange) result;
        assertTrue(exchange.isValid());
        assertEquals("echo: hi", exchange.getContent());
    }
}
