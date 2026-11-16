package io.nop.ai.shell.executor;

import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommand;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.commands.ShellCommandRegistry;
import io.nop.ai.shell.io.BlockingQueueShellInput;
import io.nop.ai.shell.io.BlockingQueueShellOutput;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.LocalToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 3.2-C4-004 regression (plan 2498 Phase 4): shell pipeline stages must
 * not share the platform global worker pool. Pre-fix the default assembly
 * ({@code ShellCommandExecutor(registry, fileSystem)} → null executor) fell
 * back to {@code GlobalExecutors.globalWorker()} (core=4/max=30/queue=10000),
 * so pipeline stage tasks competed with every other platform consumer —
 * unrelated load amplified pipeline latency and the deep queue made saturation
 * unobservable. Post-fix the default assembly creates a dedicated named
 * bounded module pool ({@code nop-ai-shell-pipeline}), isolated from the
 * platform worker and from the foreground bash tool pool; an injected
 * executor keeps precedence.
 */
public class TestShellPipelinePoolIsolation_C4_004_3_2 {

    /**
     * Internal shell command that records the thread executing it. It touches
     * neither stdin nor stdout, so both a pipeline head (whose stdin is the
     * never-closed context queue) and a pipeline tail (whose stdin is the
     * previous stage's queue) terminate immediately.
     */
    static final class ThreadRecordingCommand implements IShellCommand {
        final List<String> threads = new CopyOnWriteArrayList<>();

        @Override
        public String name() {
            return "threadrec";
        }

        @Override
        public String description() {
            return "record executing thread";
        }

        @Override
        public String usage() {
            return "threadrec";
        }

        @Override
        public String getHelp() {
            return usage();
        }

        @Override
        public int execute(IShellCommandExecutionContext context) {
            threads.add(Thread.currentThread().getName());
            return 0;
        }
    }

    private Path tempDir;
    private ShellCommandRegistry registry;
    private IToolFileSystem fileSystem;
    private ICancelToken cancelToken;
    private ThreadRecordingCommand threadRec;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("shell-pool-test");
        fileSystem = new LocalToolFileSystem(tempDir.toFile());
        threadRec = new ThreadRecordingCommand();
        registry = new ShellCommandRegistry();
        registry.registerCommand(threadRec);
        cancelToken = new ICancelToken() {
            private volatile boolean cancelled = false;

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public String getCancelReason() {
                return null;
            }

            @Override
            public void appendOnCancel(java.util.function.Consumer<String> task) {
            }

            @Override
            public void appendOnCancelTask(Runnable task) {
            }

            @Override
            public void removeOnCancel(java.util.function.Consumer<String> task) {
            }
        };
    }

    @AfterEach
    void tearDown() {
        if (tempDir != null && Files.exists(tempDir)) {
            io.nop.commons.util.FileHelper.deleteAll(tempDir.toFile());
        }
    }

    private IShellCommandExecutionContext createContext() {
        return new DefaultShellExecutionContext(
                new BlockingQueueShellInput(64), new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput(),
                new HashMap<>(), tempDir.toAbsolutePath().toString(), new String[0], fileSystem, cancelToken
        );
    }

    private static boolean threadAlive(String name) {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> name.equals(t.getName()) && t.isAlive());
    }

    private static void awaitUntil(String message, BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    @Test
    void pipelineStagesRunOnDedicatedPoolNotGlobalWorker_C4_004_3_2() throws Exception {
        // real default-carrier assembly chain: the 2-arg convenience
        // constructor (null executor → previously globalWorker)
        ShellCommandExecutor executor = new ShellCommandExecutor(registry, fileSystem);
        try {
            ExecutionResult result = executor.execute("threadrec a | threadrec b", createContext())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertEquals(0, result.exitCode(), "the pipeline must succeed");
            assertTrue(threadRec.threads.size() >= 2,
                    "both pipeline stages must have executed");
            for (String threadName : threadRec.threads) {
                assertTrue(threadName.startsWith("nop-ai-shell-pipeline"),
                        "pipeline stages must run on the dedicated module pool, got: " + threadName);
                assertFalse(threadName.startsWith("nop-global-worker"),
                        "pipeline stages must never run on the platform global worker, got: " + threadName);
            }
        } finally {
            executor.close();
        }
    }

    @Test
    void injectedExecutorKeepsPrecedence_C4_004_3_2() throws Exception {
        io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor injected =
                io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor.newExecutor(
                        "c4-004-3-2-injected-pool", 4, 64, true);
        try {
            ShellCommandExecutor executor = new ShellCommandExecutor(registry, injected,
                    null, fileSystem);
            executor.execute("threadrec a | threadrec b", createContext())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);

            assertTrue(threadRec.threads.size() >= 2);
            for (String threadName : threadRec.threads) {
                assertTrue(threadName.startsWith("c4-004-3-2-injected-pool"),
                        "an explicitly injected executor must keep precedence, got: " + threadName);
            }
        } finally {
            injected.destroy();
        }
    }

    /**
     * Minor-8 守卫双向钉（误判修复回归）：真同步 executor（caller 线程内联）必须仍被拒绝；
     * 判定语义 = 探针执行线程 == 调用线程，而非"探针在构造返回前完成"——后者会把探针被
     * 异步 worker 抢跑完成的健康线程池误判为内联（满载构建下 inject 前置测试随机炸）。
     */
    @Test
    void syncInlineExecutorStillRejected_C4_004_3_2() {
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new ShellCommandExecutor(registry, Runnable::run, null, fileSystem));
        assertTrue(e.getMessage().contains("must be asynchronous"),
                "inline executor rejection message must identify the sync-executor contract: "
                        + e.getMessage());
    }

    @Test
    void closeTerminatesTheOwnedPipelinePool_C4_004_3_2() throws Exception {
        ShellCommandExecutor executor = new ShellCommandExecutor(registry, fileSystem);
        // a pipeline is required: only pipeline stages run on the pool
        // (single commands execute synchronously on the caller thread)
        executor.execute("threadrec a | threadrec b", createContext())
                .toCompletableFuture().get(10, TimeUnit.SECONDS);

        // a pool thread is now live (fixed pool keeps core workers)
        awaitUntil("precondition: the owned pipeline pool thread must be live",
                () -> threadRec.threads.stream()
                        .filter(n -> n.startsWith("nop-ai-shell-pipeline"))
                        .anyMatch(TestShellPipelinePoolIsolation_C4_004_3_2::threadAlive));

        executor.close();

        awaitUntil("close() must terminate the owned pipeline pool (no orphan nop-ai-shell-pipeline thread)",
                () -> threadRec.threads.stream()
                        .filter(n -> n.startsWith("nop-ai-shell-pipeline"))
                        .noneMatch(TestShellPipelinePoolIsolation_C4_004_3_2::threadAlive));
    }
}
