package io.nop.ai.agent.engine;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.agent.budget.NoOpBudgetProvider;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.guardrail.NoOpContentGuardrail;
import io.nop.ai.agent.runtime.lock.NoOpSessionTakeoverLock;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2056 Phase 2: verify INFO-level awareness messages for NoOp
 * implementations that have no production alternative.
 */
public class TestSecureDefaultsInfoAwareness {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private ListAppender<ILoggingEvent> appender;
    private Logger engineLogger;

    @BeforeEach
    void attachAppender() {
        engineLogger = (Logger) LoggerFactory.getLogger(DefaultAgentEngine.class);
        appender = new ListAppender<>();
        appender.start();
        engineLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        if (engineLogger != null && appender != null) {
            engineLogger.detachAppender(appender);
            appender.stop();
        }
    }

    private List<ILoggingEvent> infoEvents() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .collect(Collectors.toList());
    }

    private List<ILoggingEvent> warnEvents() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .collect(Collectors.toList());
    }

    private long countInfosMentioning(String keyword) {
        return infoEvents().stream()
                .filter(e -> e.getFormattedMessage() != null
                        && e.getFormattedMessage().contains(keyword))
                .count();
    }

    private long countWarnsMentioning(String keyword) {
        return warnEvents().stream()
                .filter(e -> e.getFormattedMessage() != null
                        && e.getFormattedMessage().contains(keyword))
                .count();
    }

    private IChatService dummyChatService() {
        return new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(ChatResponse.success(new ChatAssistantMessage()));
            }

            @Override
            public ChatResponse call(ChatRequest request, ICancelToken cancelToken) {
                return ChatResponse.success(new ChatAssistantMessage());
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {};
            }
        };
    }

    private IToolManager dummyToolManager() {
        return new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call, IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, "ok"));
            }

            @Override
            public CompletableFuture<io.nop.ai.toolkit.model.AiToolCallsResponse> callTools(
                    io.nop.ai.toolkit.model.AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                return new AiToolModel();
            }
        };
    }

    @Test
    void defaultConstructionEmitsWarnForNoOpContentGuardrail() {
        IChatService chat = dummyChatService();
        IToolManager tools = dummyToolManager();

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, tools);

        assertTrue(countWarnsMentioning("NoOpContentGuardrail") >= 1,
                "Default construction must emit a WARN message about NoOpContentGuardrail. Messages: "
                        + warnEvents().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }

    @Test
    void defaultConstructionEmitsInfoForNoOpBudgetProvider() {
        IChatService chat = dummyChatService();
        IToolManager tools = dummyToolManager();

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, tools);

        assertTrue(countInfosMentioning("NoOpBudgetProvider") >= 1,
                "Default construction must emit an INFO message about NoOpBudgetProvider. Messages: "
                        + infoEvents().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }

    @Test
    void noOpSessionTakeoverLockDoesNotEmitAnyNewMessage() {
        IChatService chat = dummyChatService();
        IToolManager tools = dummyToolManager();

        DefaultAgentEngine engine = new DefaultAgentEngine(chat, tools);

        // NoOpSessionTakeoverLock should NOT trigger any check message
        assertEquals(0, countInfosMentioning("NoOpSessionTakeoverLock"),
                "NoOpSessionTakeoverLock must NOT emit any INFO message. Messages: "
                        + infoEvents().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
        assertEquals(0, countInfosMentioning("SessionTakeoverLock"),
                "NoOpSessionTakeoverLock must NOT emit any message about takeover lock");
    }

    @Test
    void builderPathEmitsWarnForNoOpContentGuardrail() {
        IChatService chat = dummyChatService();
        IToolManager tools = dummyToolManager();

        DefaultAgentEngine engine = DefaultAgentEngine.builder(chat, tools).build();

        assertTrue(countWarnsMentioning("NoOpContentGuardrail") >= 1,
                "Builder path must emit a WARN message about NoOpContentGuardrail");
    }

    @Test
    void builderPathEmitsInfoForNoOpBudgetProvider() {
        IChatService chat = dummyChatService();
        IToolManager tools = dummyToolManager();

        DefaultAgentEngine engine = DefaultAgentEngine.builder(chat, tools).build();

        assertTrue(countInfosMentioning("NoOpBudgetProvider") >= 1,
                "Builder path must emit an INFO message about NoOpBudgetProvider");
    }

    @Test
    void customContentGuardrailDoesNotEmitInfo() {
        IChatService chat = dummyChatService();
        IToolManager tools = dummyToolManager();

        IContentGuardrail custom = (direction, content, ctx) -> io.nop.ai.agent.guardrail.GuardrailResult.PassResult.instance();
        DefaultAgentEngine engine = DefaultAgentEngine.builder(chat, tools)
                .contentGuardrail(custom)
                .build();

        assertEquals(0, countWarnsMentioning("NoOpContentGuardrail") + countInfosMentioning("NoOpContentGuardrail"),
                "Custom IContentGuardrail must NOT emit NoOpContentGuardrail messages. Messages: "
                        + warnEvents().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList())
                        + infoEvents().stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList()));
    }
}
