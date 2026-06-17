package io.nop.ai.agent.engine;

import io.nop.ai.agent.runtime.recovery.IRecoveryManager;
import io.nop.ai.agent.runtime.recovery.NoOpRecoveryManager;
import io.nop.ai.agent.runtime.recovery.RecoveryScanResult;
import io.nop.ai.agent.runtime.recovery.ScheduledRecoveryManager;
import io.nop.ai.agent.runtime.lock.AiAgentSessionLockTable;
import io.nop.ai.agent.session.AiAgentSessionTable;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolCalls;
import io.nop.ai.toolkit.model.AiToolCallsResponse;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.concurrent.executor.ThreadPoolStats;
import io.nop.commons.lang.IDestroyable;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 222 Phase 2 engine-wiring + setter-injection tests: verifies that
 * {@code DefaultAgentEngine} ships with {@link NoOpRecoveryManager} as the
 * default (zero behaviour regression) and that
 * {@code setRecoveryManager} accepts a functional manager.
 *
 * <p>Coverage map:
 * <ul>
 *   <li>{@link #noOpIsShippedDefault} — new engine defaults to NoOp instance</li>
 *   <li>{@link #setRecoveryManagerInjectsFunctionalManager} — setter assigns</li>
 *   <li>{@link #setRecoveryManagerNullFallsBackToNoOp} — null-safe fallback</li>
 *   <li>{@link #noOpDefaultScanOnceIsExplicitZero} — NoOp scanOnce semantic</li>
 * </ul>
 */
public class TestRecoveryManagerEngineWiring {

    private DataSource dataSource;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        String dbUrl = "jdbc:h2:mem:test-recovery-wiring-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl(dbUrl);
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;
        createTables();
    }

    @AfterEach
    void tearDown() {
        if (dataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) dataSource).close();
            } catch (Exception ignored) {
                // best-effort close during teardown
            }
        }
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(AiAgentSessionLockTable.DDL_CREATE_TABLE);
            stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create tables", e);
        }
    }

    @Test
    void noOpIsShippedDefault() {
        // A freshly constructed engine (no setter call) must hold a
        // NoOpRecoveryManager instance — the engine does not start a daemon
        // by default (zero behaviour regression).
        DefaultAgentEngine engine = newEngine();
        IRecoveryManager mgr = engine.getRecoveryManager();
        assertTrue(mgr instanceof NoOpRecoveryManager,
                "shipped default recoveryManager must be a NoOpRecoveryManager instance");
        assertSame(NoOpRecoveryManager.noOp(), mgr,
                "default is the NoOp singleton");
    }

    @Test
    void setRecoveryManagerInjectsFunctionalManager() {
        DefaultAgentEngine engine = newEngine();
        ScheduledRecoveryManager functional =
                new ScheduledRecoveryManager(dataSource, new NoOpScheduler());

        engine.setRecoveryManager(functional);

        assertSame(functional, engine.getRecoveryManager(),
                "setRecoveryManager must assign the functional manager");
    }

    @Test
    void setRecoveryManagerNullFallsBackToNoOp() {
        DefaultAgentEngine engine = newEngine();
        // null must fall back to the NoOp default (consistent with the
        // setSessionTakeoverLock / setAuditLogger null-safe pattern).
        engine.setRecoveryManager(null);

        IRecoveryManager mgr = engine.getRecoveryManager();
        assertTrue(mgr instanceof NoOpRecoveryManager,
                "setRecoveryManager(null) must fall back to NoOpRecoveryManager");
    }

    @Test
    void noOpDefaultScanOnceIsExplicitZero() {
        // The shipped NoOp default's scanOnce must return an explicit
        // all-zero result (not null/placeholder) — Minimum Rules #24.
        DefaultAgentEngine engine = newEngine();
        RecoveryScanResult result = engine.getRecoveryManager().scanOnce();
        assertEquals(0, result.getStaleLocksCleaned());
        assertEquals(0, result.getOrphanSessionsDetected());
        assertTrue(result.getOrphanSessionIds().isEmpty());
        assertEquals(0L, result.getScanDurationMs());
        assertEquals(0L, result.getScannedAt());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private DefaultAgentEngine newEngine() {
        // Minimal engine construction — only the recoveryManager wiring is
        // under test, so the chat service / tool manager are trivial stubs
        // that are never invoked (no execute() call).
        IChatService chat = new IChatService() {
            @Override
            public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
                return subscriber -> {
                };
            }
        };
        IToolManager tools = new IToolManager() {
            @Override
            public CompletableFuture<AiToolCallResult> callTool(String toolName, AiToolCall call,
                                                                 IToolExecuteContext context) {
                return CompletableFuture.completedFuture(AiToolCallResult.successResult(0, ""));
            }

            @Override
            public CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context) {
                return null;
            }

            @Override
            public List<AiToolModel> listTools() {
                return Collections.emptyList();
            }

            @Override
            public AiToolModel loadTool(String toolName) {
                AiToolModel model = new AiToolModel();
                model.setName(toolName);
                return model;
            }
        };
        // 2-arg constructor uses an InMemorySessionStore by default.
        return new DefaultAgentEngine(chat, tools);
    }

    /**
     * Minimal no-op {@link IScheduledExecutor} for engine-wiring tests —
     * never actually schedules anything (the engine does not call
     * start()/stop(); only the field assignment is verified here).
     */
    static final class NoOpScheduler implements IScheduledExecutor, IDestroyable {
        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return new CompletableFuture<>();
        }

        @Override
        public void execute(Runnable command) {
        }

        @Override
        public void destroy() {
        }

        @Override
        public String getName() {
            return "no-op-scheduler";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return null;
        }

        @Override
        public ThreadPoolStats stats() {
            return null;
        }

        @Override
        public <V> CompletableFuture<V> submit(Callable<V> callable) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public void refreshConfig() {
        }
    }
}
