package io.nop.ai.agent.engine;

import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.session.InMemorySessionStore;
import io.nop.ai.agent.support.ChatResponseFixtures;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.ai.toolkit.tools.BashExecutor;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxConfig;
import io.nop.ai.toolkit.tools.sandbox.HostBashSandbox;
import io.nop.ai.agent.tool.TaskOutputExecutor;
import io.nop.ai.agent.tool.TaskStopExecutor;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.json.JsonTool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2301 (WI25) Phase 2 anti-hollow end-to-end: under real engine
 * assembly — bash runInBackground submits and returns a taskId confirmation
 * immediately, the command forks onto the registry's background executor
 * (never the dispatch pool), the terminal state is bridged into the session
 * input channel as a FOLLOWUP injection, the next execution merges the
 * notification into the LLM context, and the task-output tool reads the
 * captured output through the same registry instance.
 */
public class TestBackgroundTaskEndToEnd {

    private static final String SESSION_ID = "s-bg-e2e";
    private static final String TMPDIR = System.getProperty("java.io.tmpdir");

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void cleanup() {
        CoreInitialization.destroy();
    }

    /**
     * Records every ChatRequest (for merged-context assertions) and returns
     * scripted responses in order.
     */
    static class ScriptedChatService implements io.nop.ai.api.chat.IChatService {
        final ConcurrentLinkedQueue<ChatResponse> steps = new ConcurrentLinkedQueue<>();
        final List<io.nop.ai.api.chat.ChatRequest> requests =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        void enqueue(ChatResponse response) {
            steps.add(response);
        }

        @Override
        public CompletionStage<ChatResponse> callAsync(io.nop.ai.api.chat.ChatRequest request,
                                                       ICancelToken cancelToken) {
            calls.incrementAndGet();
            requests.add(request);
            ChatResponse r = steps.poll();
            if (r == null) {
                throw new AssertionError("script exhausted at call " + calls.get());
            }
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                io.nop.ai.api.chat.ChatRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onComplete();
        }
    }

    /**
     * Routes bash / task-output / task-stop to their real executors — the
     * context handed in here is the engine-built AgentToolExecuteContext, so
     * the registry bridge and dual-source argument resolution are exercised
     * exactly as in production dispatch.
     */
    static class RoutingToolManager implements IToolManager {
        private final BashExecutor bash = new BashExecutor(newHostSandbox());
        private final TaskOutputExecutor taskOutput = new TaskOutputExecutor();
        private final TaskStopExecutor taskStop = new TaskStopExecutor();

        static HostBashSandbox newHostSandbox() {
            BashSandboxConfig config = BashSandboxConfig.builder().wallSeconds(30).build();
            return new HostBashSandbox(config, List.of(Paths.get(System.getProperty("java.io.tmpdir"))));
        }

        @Override
        public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                            IToolExecuteContext context) {
            return switch (toolName) {
                case "bash" -> bash.executeAsync(call, context).toCompletableFuture();
                case "task-output" -> taskOutput.executeAsync(call, context).toCompletableFuture();
                case "task-stop" -> taskStop.executeAsync(call, context).toCompletableFuture();
                default -> CompletableFuture.completedFuture(
                        AiToolCallResult.errorResult(call.getId(), "Unknown tool: " + toolName));
            };
        }

        @Override
        public CompletableFuture<AiToolCallsResponse> callTools(io.nop.ai.toolkit.model.AiToolCalls calls,
                                                                IToolExecuteContext context) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public List<AiToolModel> listTools() {
            return List.of();
        }

        @Override
        public AiToolModel loadTool(String toolName) {
            return null;
        }
    }

    @Test
    void backgroundBashCompletesNotifiesAndIsReadable() throws Exception {
        InMemorySessionStore store = new InMemorySessionStore();
        ScriptedChatService chat = new ScriptedChatService();
        DefaultAgentEngine engine = new DefaultAgentEngine.Builder(chat, new RoutingToolManager())
                .sessionStore(store)
                // the secure-default tool access checker hardcodes a deny
                // for bash — this E2E explicitly allow-lists it (the test
                // subject is the background-mode wiring, not the deny list)
                .toolAccessChecker((toolName, ctx) -> io.nop.ai.agent.security.ToolAccessResult.allow())
                .build();
        engine.initBackgroundSupport(null, null);
        IBackgroundTaskRegistry registry = engine.getBackgroundTaskRegistry();
        assertNotNull(registry);

        // execute #1: LLM asks for a background bash run, then closes the round
        chat.enqueue(ChatResponseFixtures.assistantWithToolCalls("starting the build",
                toolCall("call-1", "bash", Map.of(
                        "command", "echo Hello E2E",
                        "workingDir", TMPDIR,
                        "runInBackground", true))));
        chat.enqueue(ChatResponseFixtures.assistantText("submitted"));
        engine.execute(new AgentMessageRequest("test-background-agent", "run my build", SESSION_ID, Map.of()))
                .get(30, TimeUnit.SECONDS);

        String taskId = extractTaskId(chat);
        assertTrue(taskId.startsWith("bg-"), "confirmation taskId: " + taskId);

        // the fork runs off the dispatch pool — poll for the terminal state
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline
                && IBackgroundTaskRegistry.STATUS_RUNNING.equals(
                        registry.snapshot(taskId) == null ? null : registry.snapshot(taskId).getStatus())) {
            Thread.sleep(50);
        }
        assertEquals(IBackgroundTaskRegistry.STATUS_COMPLETED, registry.snapshot(taskId).getStatus(),
                "background bash must complete");
        assertTrue(registry.snapshot(taskId).getOutputTail().contains("Hello E2E"),
                "captured output carries stdout");

        // bridge: the notification must be staged into the (now idle) session
        AgentSession session = store.get(SESSION_ID);
        assertNotNull(session);
        String staged = stagedInjections(session);
        assertTrue(staged.contains("[BACKGROUND_TASK taskId=" + taskId + " tool=bash status=COMPLETED"),
                "completion notification staged: " + staged);

        // execute #2: the staged notification merges into the context and the
        // LLM reads the captured output through the task-output tool
        chat.enqueue(ChatResponseFixtures.assistantWithToolCalls("checking the task",
                toolCall("call-2", "task-output", Map.of("taskId", taskId))));
        chat.enqueue(ChatResponseFixtures.assistantText("read done"));
        engine.execute(new AgentMessageRequest("test-background-agent", "check the task", SESSION_ID, Map.of()))
                .get(30, TimeUnit.SECONDS);

        // the merged FOLLOWUP injection is consumed at the round boundary
        // (WI8 semantics) — the final LLM request sees BOTH the notification
        // (as a user message in history) and the task-output tool result
        assertTrue(chat.requests.size() >= 4,
                "four LLM calls expected (2 + tool round + injection turn), got " + chat.requests.size());
        String lastRequest = JsonTool.stringify(chat.requests.get(chat.requests.size() - 1));
        assertTrue(lastRequest.contains("[BACKGROUND_TASK taskId=" + taskId + " tool=bash status=COMPLETED"),
                "completion notification merged into session history: " + lastRequest);
        assertTrue(lastRequest.contains("Hello E2E"),
                "task-output tool result (captured stdout) visible to the LLM");
        assertTrue(registry.snapshot(taskId).isNotificationSent(),
                "successful bridge must hold the exactly-once claim");
    }

    private static io.nop.ai.api.chat.messages.ChatToolCall toolCall(String id, String name,
                                                                     Map<String, Object> arguments) {
        io.nop.ai.api.chat.messages.ChatToolCall call = new io.nop.ai.api.chat.messages.ChatToolCall();
        call.setId(id);
        call.setName(name);
        call.setArguments(arguments);
        return call;
    }

    private static String extractTaskId(ScriptedChatService chat) {
        Pattern pattern = Pattern.compile("taskId=(bg-\\d+)");
        for (io.nop.ai.api.chat.ChatRequest request : chat.requests) {
            Matcher matcher = pattern.matcher(JsonTool.stringify(request));
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("no background taskId found in the recorded requests: "
                + chat.requests.stream().map(JsonTool::stringify)
                        .reduce("", (a, b) -> a + "\n----\n" + b));
    }

    private static String stagedInjections(AgentSession session) {
        StringBuilder sb = new StringBuilder();
        for (AgentSession.PendingInjection injection : session.getPendingInjections()) {
            sb.append(injection.getSemantics()).append(':').append(injection.getMessageJson()).append('\n');
        }
        return sb.toString();
    }
}
