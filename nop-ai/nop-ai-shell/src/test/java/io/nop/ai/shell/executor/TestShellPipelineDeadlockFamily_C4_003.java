package io.nop.ai.shell.executor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.ai.shell.commands.AbstractShellCommand;
import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.commands.ShellCommandRegistry;
import io.nop.ai.shell.commands.impl.EchoCommand;
import io.nop.ai.shell.io.BlockingQueueShellInput;
import io.nop.ai.shell.io.BlockingQueueShellOutput;
import io.nop.ai.shell.io.ShellChunk;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.2-C4-003（plan 2501 Phase 3，MA-06 shell 管道死锁/弃管族案单 diff，C10-001
 * 单保护面覆盖五成员 C6-004/C8-001/C8-003/C8-011/C4-003）：
 * <p>
 * ①两自锁拓扑钉（C8-001 面）：「单命令 >1024 chunk」（produce-then-collect 自锁）与
 * 「末级 stage 1025-chunk」（收集被拓扑性禁止在末级完成前启动）；②REE 提交中止回滚
 * （C4-003 独有面：已提交阶段取消 + 队列解除阻塞 + 无线程滞留）；③阶段 lambda 异常
 * 注入（C8-003 面：上游解锁 + 异常可观测）；④{@code 0<&1} 自引用拒绝（C8-011 面）；
 * ⑤collectOutput 去吞（F9 面：失败可观测不再静默空串）。
 * 2498 池隔离钉（{@code TestShellPipelinePoolIsolation_C4_004_3_2}）与既有
 * {@code ShellConcurrencyEdgeCaseTest} 零回归由模块门禁承载。
 */
public class TestShellPipelineDeadlockFamily_C4_003 {

    /** 指定行数写 stdout 的命令（构造 >1024 chunk 负载）。 */
    static final class BigLinesCommand extends AbstractShellCommand {
        @Override public String name() { return "biglines"; }
        @Override public String description() { return "writes N lines"; }
        @Override public String usage() { return "biglines N"; }

        @Override
        public int execute(IShellCommandExecutionContext ctx) {
            int n = Integer.parseInt(ctx.positionalArguments()[0]);
            for (int i = 1; i <= n; i++) {
                ctx.stdout().println("line-" + i);
            }
            return 0;
        }
    }

    /** 读空 stdin 到 EOF 后回写行数的命令（读 stdin 的负载，0<&1 拓扑用）。 */
    static final class CountLinesCommand extends AbstractShellCommand {
        @Override public String name() { return "countlines"; }
        @Override public String description() { return "counts stdin lines"; }
        @Override public String usage() { return "countlines"; }

        @Override
        public int execute(IShellCommandExecutionContext ctx) throws Exception {
            int count = 0;
            String line;
            while ((line = ctx.stdin().readLine()) != null) {
                count++;
            }
            ctx.stdout().println("count=" + count);
            return 0;
        }
    }

    /** 立即抛异常的命令（阶段 lambda 异常注入）。 */
    static final class FailFastCommand extends AbstractShellCommand {
        @Override public String name() { return "failfast"; }
        @Override public String description() { return "fails"; }
        @Override public String usage() { return "failfast"; }

        @Override
        public int execute(IShellCommandExecutionContext ctx) {
            throw new IllegalStateException("injected stage failure");
        }
    }

    private final AtomicReference<ThreadPoolExecutor> injectedPool = new AtomicReference<>();

    private ShellCommandExecutor newDefaultExecutor(ShellCommandRegistry registry) {
        return new ShellCommandExecutor(registry, new io.nop.ai.toolkit.fs.LocalToolFileSystem(null));
    }

    @AfterEach
    void tearDown() {
        ThreadPoolExecutor pool = injectedPool.get();
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private IShellCommandExecutionContext defaultContext() {
        return new DefaultShellExecutionContext(
                new BlockingQueueShellInput(16),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput(),
                new HashMap<>(), "/", new String[0], null, new NoopCancelToken());
    }

    static final class NoopCancelToken implements io.nop.api.core.util.ICancelToken {
        @Override public boolean isCancelled() { return false; }
        @Override public String getCancelReason() { return null; }
        @Override public void appendOnCancel(java.util.function.Consumer<String> task) { }
        @Override public void appendOnCancelTask(Runnable task) { }
        @Override public void removeOnCancel(java.util.function.Consumer<String> task) { }
    }

    private static boolean hasWarn(ListAppender<ILoggingEvent> appender, String needle) {
        return appender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(needle));
    }

    /**
     * ①单命令 >1024 chunk 拓扑（C8-001 机制 A）：命令在调用线程生产、收集在生产结束
     * 之后——第 1025 个 chunk 起生产者在自己的队列上自锁。修复前 execute 永不返回（先红）。
     */
    @Test
    @Timeout(60)
    public void singleCommandLargeOutputCompletes() throws Exception {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        registry.registerCommand(new BigLinesCommand());
        ShellCommandExecutor executor = newDefaultExecutor(registry);
        try {
            ExecutionResult result = executor.execute("biglines 1100", defaultContext())
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);
            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("line-1100"), "full output collected past the queue capacity");
            assertEquals(1100, result.stdout().split("\n").length);
        } finally {
            executor.close();
        }
    }

    /**
     * ①末级 stage 1025-chunk 拓扑（C8-001 机制 B）：末级收集只在末级 stage future 完成
     * 后启动——末级 >1024 chunk 在自己的队列上自锁。修复前 pipeline future 永不落定（先红）。
     */
    @Test
    @Timeout(60)
    public void lastStageLargeOutputCompletes() throws Exception {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        registry.registerCommand(new EchoCommand());
        registry.registerCommand(new BigLinesCommand());
        ShellCommandExecutor executor = newDefaultExecutor(registry);
        try {
            ExecutionResult result = executor.execute("echo x | biglines 1100", defaultContext())
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);
            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("line-1100"), "last-stage collection runs concurrently with production");
        } finally {
            executor.close();
        }
    }

    /**
     * ②REE 饱和池提交中止回滚（C4-003 独有面）：小池（1 线程 + 队列 1）被占位任务
     * 饱和，第 2 个阶段提交 REE——回滚必须取消已提交阶段（取消排队任务使其永不运行）
     * 并关闭已建队列；占位任务释放后池必须无滞留线程且可正常服务新任务。
     * 修复前已提交阶段在释放后运行并无消费者阻塞 queue.put 永久钉死线程（先红）。
     */
    @Test
    @Timeout(60)
    public void reeAbortRollsBackCommittedStages() throws Exception {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        registry.registerCommand(new BigLinesCommand());
        ShellCommandRegistry localRegistry = registry;
        ThreadPoolExecutor tiny = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1));
        injectedPool.set(tiny);

        java.util.concurrent.CountDownLatch releaseBlocker = new java.util.concurrent.CountDownLatch(1);
        tiny.submit(() -> {
            releaseBlocker.await();
            return null;
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(registry, tiny, null,
                new io.nop.ai.toolkit.fs.LocalToolFileSystem(null));
        try {
            // 阶段 a 入队（占满队列）；阶段 b 提交 REE
            Exception failure = assertThrows(Exception.class,
                    () -> executor.execute("biglines 2000 | biglines 2000 | biglines 2000", defaultContext()));
            Throwable cause = failure;
            while (cause != null && !(cause instanceof RejectedExecutionException)) {
                cause = cause.getCause();
            }
            assertNotNull(cause, "stage submission rejection must surface: " + failure);

            // 释放占位任务：回滚已取消排队阶段 → 池排空无线程滞留
            releaseBlocker.countDown();
            long deadline = System.currentTimeMillis() + 8000;
            while (tiny.getActiveCount() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(0, tiny.getActiveCount(),
                    "no stage thread may stay pinned after the submission-abort rollback");
            assertEquals(0, tiny.getQueue().size(), "no cancelled stage task may linger in the pool queue");

            // 池健康：新任务可正常服务
            java.util.concurrent.Future<Integer> probe = tiny.submit(() -> 42);
            assertEquals(42, probe.get(5, TimeUnit.SECONDS), "pool must stay serviceable after rollback");
        } finally {
            executor.close();
            tiny.shutdownNow();
        }
    }

    /**
     * ③阶段 lambda 异常注入（C8-003 面）：中段阶段异常 → 上游阶段解锁退出（无
     * queue.put 永久钉死）、下游照常 EOF 收束、失败 WARN 可观测（CL-008①：非末位
     * 阶段异常不再静默丢失）。修复前上游阶段永久钉死泄漏线程（先红：线程滞留断言）。
     */
    @Test
    @Timeout(60)
    public void stageExceptionUnblocksUpstreamAndIsObserved() throws Exception {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        registry.registerCommand(new BigLinesCommand());
        registry.registerCommand(new FailFastCommand());
        registry.registerCommand(new EchoCommand());
        ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(100));
        injectedPool.set(pool);

        Logger logger = (Logger) LoggerFactory.getLogger(ShellCommandExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        ShellCommandExecutor executor = new ShellCommandExecutor(registry, pool, null,
                new io.nop.ai.toolkit.fs.LocalToolFileSystem(null));
        try {
            ExecutionResult result = executor.execute("biglines 3000 | failfast | echo x", defaultContext())
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);
            assertEquals(0, result.exitCode(), "downstream completes normally on upstream stage failure");
            assertTrue(result.stdout().contains("x"));

            long deadline = System.currentTimeMillis() + 8000;
            while (pool.getActiveCount() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(0, pool.getActiveCount(),
                    "the upstream producer must be unblocked when its consumer stage dies (no thread leak)");
            assertTrue(hasWarn(appender, "pipeline stage 1 failed"),
                    "non-last stage failure must be observed (WARN), not silently dropped");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            executor.close();
            pool.shutdownNow();
        }
    }

    /**
     * ④{@code 0<&1} 自引用拒绝（C8-011 面）：stdin 指向自身 stdout 的确定性挂死拓扑
     * 必须显式拒绝（命令读自身永远不会被写入的 stdout 队列）。修复前确定性挂死（先红）。
     */
    @Test
    @Timeout(60)
    public void selfReferentialInputRedirectionRejected() throws Exception {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        registry.registerCommand(new CountLinesCommand());
        ShellCommandExecutor executor = newDefaultExecutor(registry);
        try {
            Exception failure = assertThrows(Exception.class, () -> {
                executor.execute("countlines 0<&1", defaultContext()).toCompletableFuture()
                        .get(20, TimeUnit.SECONDS);
            }, "0<&1 self-referential redirection must be rejected, not hang");
            assertTrue(String.valueOf(failure).contains("0<&1"),
                    "rejection must name the offending redirection: " + failure);
        } finally {
            executor.close();
        }
    }

    /**
     * ⑤collectOutput 去吞（F9 面）：收集失败必须可观测（WARN + 异常传播），不得静默
     * 返回空串。修复前 catch-Exception 返回 ""（先红）。
     */
    @Test
    @Timeout(30)
    public void collectFailureIsObservableNotSilentEmptyString() throws Exception {
        ShellCommandExecutor executor = newDefaultExecutor(new ShellCommandRegistry());
        Method collectOutput = ShellCommandExecutor.class.getDeclaredMethod("collectOutput",
                BlockingQueueShellOutput.class);
        collectOutput.setAccessible(true);

        // 敌意队列：take/poll 均注入失败
        LinkedBlockingQueue<ShellChunk> hostile = new LinkedBlockingQueue<ShellChunk>() {
            @Override public ShellChunk take() { throw new IllegalStateException("injected collection failure"); }
            @Override public ShellChunk poll(long timeout, TimeUnit unit) {
                throw new IllegalStateException("injected collection failure");
            }
        };
        BlockingQueueShellOutput output = new BlockingQueueShellOutput(hostile);

        Logger logger = (Logger) LoggerFactory.getLogger(ShellCommandExecutor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                    () -> collectOutput.invoke(executor, output),
                    "collection failure must propagate, not return a silent empty string");
            assertTrue(failure.getCause() instanceof IllegalStateException,
                    "injected failure surfaces: " + failure.getCause());
            assertTrue(hasWarn(appender, "collect") || hasWarn(appender, "collection"),
                    "collection failure must be logged");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            executor.close();
        }
    }

    /**
     * rule 22 端到端 + rule 23 接线：真实多阶段链（缺省专用池装配）从入口到终态输出
     * 完整走通——生产/消费/收集全部经新拓扑运行（回滚与收集并行化只在修复后代码路径
     * 存在，上列用例通过即接线证明）。
     */
    @Test
    @Timeout(60)
    public void endToEndRealPipelineChainReachesTerminalOutput() throws Exception {
        ShellCommandRegistry registry = new ShellCommandRegistry();
        registry.registerCommand(new EchoCommand());
        registry.registerCommand(new BigLinesCommand());
        registry.registerCommand(new CountLinesCommand());
        ShellCommandExecutor executor = newDefaultExecutor(registry);
        try {
            ExecutionResult result = executor.execute("echo seed | biglines 1500 | countlines", defaultContext())
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);
            assertEquals(0, result.exitCode());
            assertTrue(result.stdout().contains("count=1500"),
                    "terminal output of the real chained pipeline: " + result.stdout());
            assertFalse(result.stdout().contains("line-"), "only the last stage's output is the result");
        } finally {
            executor.close();
        }
    }
}
