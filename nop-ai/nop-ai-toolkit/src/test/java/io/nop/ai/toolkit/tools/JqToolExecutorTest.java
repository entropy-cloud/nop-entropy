package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.3（plan 2366）：JqToolExecutor 资源护栏接线回归（P1-1.9-001）。
 * cancelToken → future.cancel(true) → jq 检查点终止（无僵尸计算）；沙箱 env 注入；deadline fail-fast。
 */
public class JqToolExecutorTest {

    private static ExecutorService delegate;
    private static SingleThreadExecutorWrapper pool;
    private final JqToolExecutor executor = new JqToolExecutor();

    @BeforeAll
    static void initPool() {
        delegate = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jq-tool-test-worker");
            t.setDaemon(true);
            return t;
        });
        pool = new SingleThreadExecutorWrapper(delegate);
    }

    @AfterAll
    static void destroyPool() {
        if (delegate != null)
            delegate.shutdownNow();
    }

    private AiToolCall createCall(String expression, String data) {
        XNode node = XNode.make("jq");
        node.setAttr("id", "1");
        node.makeChild("expression").setContentValue(expression);
        node.makeChild("data").setContentValue(data);
        return AiToolCall.fromNode(node);
    }

    @Test
    public void testBasicSuccess() {
        AiToolCall call = createCall(".a", "{\"a\": 1}");
        AiToolCallResult result = executor.executeAsync(call, newContext(null, 0, null))
                .toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("1"));
    }

    /** cancelToken.cancel() 后底层 jq 计算真正终止（无僵尸计算）：worker 线程被释放。 */
    @Test
    public void testCancelTokenStopsUnderlyingComputation() throws Exception {
        // deterministic mid-flight cancellation: the token is pre-cancelled, so the
        // executeAsync-registered hook fires immediately and future.cancel(true) lands
        // while the (slow materializing) jq computation is still in flight — the future
        // ends cancelled regardless of when the worker finishes
        // dedicated executor: the zombie check below terminates it
        ExecutorService dedicated = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jq-cancel-test-worker");
            t.setDaemon(true);
            return t;
        });
        try {
            TestCancelToken token = new TestCancelToken();
            token.cancel("test-cancel");
            IToolExecuteContext context = newContext(token, 0, null, new SingleThreadExecutorWrapper(dedicated));

            AiToolCall call = createCall("[range(5000000)]|length", "null");
            CompletableFuture<AiToolCallResult> future = executor.executeAsync(call, context)
                    .toCompletableFuture();

            assertThrows(CancellationException.class, future::join);

            // no zombie: the single worker thread must be released after cancellation
            dedicated.shutdown();
            assertTrue(dedicated.awaitTermination(5, TimeUnit.SECONDS),
                    "jq computation still running after cancel (zombie)");
        } finally {
            dedicated.shutdownNow();
        }
    }

    /** 工具只见沙箱 env：$ENV 与 env 内建都不泄漏全进程变量。 */
    @Test
    public void testSandboxedEnvsOnly() {
        Map<String, String> envs = new LinkedHashMap<>();
        envs.put("JQ_TK_TEST_VAR", "42");
        IToolExecuteContext context = newContext(null, 0, envs);

        AiToolCall readVar = createCall("$ENV.JQ_TK_TEST_VAR", "null");
        AiToolCallResult result = executor.executeAsync(readVar, context).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("42"));

        AiToolCall noPath = createCall("$ENV | has(\"PATH\")", "null");
        AiToolCallResult pathResult = executor.executeAsync(noPath, context).toCompletableFuture().join();
        assertTrue(pathResult.getOutput().getBody().contains("false"));

        AiToolCall noPathBuiltin = createCall("env | has(\"PATH\")", "null");
        AiToolCallResult builtinResult = executor.executeAsync(noPathBuiltin, context).toCompletableFuture().join();
        assertTrue(builtinResult.getOutput().getBody().contains("false"));
    }

    /** expireAt 已过：入口 fail-fast（覆盖不经过 jq 检查点的表达式）。 */
    @Test
    public void testExpiredDeadlineFailsFast() {
        IToolExecuteContext context = newContext(null, System.currentTimeMillis() - 1000, null);
        AiToolCall call = createCall(".a", "{\"a\": 1}");
        AiToolCallResult result = executor.executeAsync(call, context).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("deadline already exceeded"));
    }

    /** expireAt 未过：正常执行不受影响。 */
    @Test
    public void testFutureDeadlinePasses() {
        IToolExecuteContext context = newContext(null, System.currentTimeMillis() + 60_000, null);
        AiToolCall call = createCall(".a + 1", "{\"a\": 1}");
        AiToolCallResult result = executor.executeAsync(call, context).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
    }

    private IToolExecuteContext newContext(TestCancelToken token, long expireAt, Map<String, String> envs) {
        return newContext(token, expireAt, envs, pool);
    }

    private IToolExecuteContext newContext(TestCancelToken token, long expireAt, Map<String, String> envs,
                                           IThreadPoolExecutor executorRef) {
        return new IToolExecuteContext() {
            @Override public File getWorkDir() {
                return new File(".");
            }

            @Override public Map<String, String> getEnvs() {
                return envs == null ? Map.of() : envs;
            }

            @Override public long getExpireAt() {
                return expireAt;
            }

            @Override public ICancelToken getCancelToken() {
                return token;
            }

            @Override public IToolFileSystem getFileSystem() {
                return null;
            }

            @Override public IThreadPoolExecutor getExecutor() {
                return executorRef;
            }
        };
    }

    /** Single-thread IThreadPoolExecutor wrapper: termination observable for zombie checks. */
    static class SingleThreadExecutorWrapper implements IThreadPoolExecutor {
        private final ExecutorService delegate;

        SingleThreadExecutorWrapper(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override public String getName() {
            return "jq-tool-test";
        }

        @Override public io.nop.commons.concurrent.executor.ThreadPoolConfig getConfig() {
            return null;
        }

        @Override public io.nop.commons.concurrent.executor.ThreadPoolStats stats() {
            return null;
        }

        @Override public void execute(Runnable command) {
            delegate.execute(command);
        }

        @Override public <V> CompletableFuture<V> submit(Callable<V> callable) {
            CompletableFuture<V> future = new CompletableFuture<>();
            delegate.execute(() -> {
                try {
                    future.complete(callable.call());
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }

        @Override public <V> CompletableFuture<V> submit(Runnable task, V result) {
            return submit(() -> {
                task.run();
                return result;
            });
        }

        @Override public void destroy() {
            delegate.shutdownNow();
        }

        @Override public void refreshConfig() {
            // no-op
        }
    }

    static class TestCancelToken implements ICancelToken {
        private volatile boolean cancelled;
        private volatile String reason;
        private final java.util.List<Consumer<String>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override public boolean isCancelled() {
            return cancelled;
        }

        @Override public String getCancelReason() {
            return reason;
        }

        @Override public void appendOnCancel(Consumer<String> task) {
            if (cancelled) {
                task.accept(reason);
            } else {
                listeners.add(task);
            }
        }

        @Override public void removeOnCancel(Consumer<String> task) {
            listeners.remove(task);
        }

        void cancel(String cancelReason) {
            this.reason = cancelReason;
            this.cancelled = true;
            for (Consumer<String> listener : listeners) {
                listener.accept(cancelReason);
            }
        }
    }
}
