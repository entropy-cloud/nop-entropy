package io.nop.ai.shell.executor;

import io.nop.ai.shell.commands.AbstractShellCommand;
import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.commands.ShellCommandRegistry;
import io.nop.ai.shell.io.BlockingQueueShellInput;
import io.nop.ai.shell.io.BlockingQueueShellOutput;
import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.LocalToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ShellConcurrencyEdgeCaseTest {

    private ShellCommandRegistry registry;
    private ICancelToken cancelToken;
    private IToolFileSystem fileSystem;

    @BeforeEach
    void setUp() throws IOException {
        registry = new ShellCommandRegistry();
        registry.registerCommand(new io.nop.ai.shell.commands.impl.EchoCommand());
        registry.registerCommand(new io.nop.ai.shell.commands.impl.LsCommand());

        Path tempDir = Files.createTempDirectory("shell-test");
        tempDir.toFile().deleteOnExit();
        fileSystem = new LocalToolFileSystem(tempDir.toFile());

        cancelToken = new ICancelToken() {
            @Override public boolean isCancelled() { return false; }
            @Override public String getCancelReason() { return null; }
            @Override public void appendOnCancel(java.util.function.Consumer<String> task) {}
            @Override public void appendOnCancelTask(Runnable task) {}
            @Override public void removeOnCancel(java.util.function.Consumer<String> task) {}
        };
    }

    private IShellCommandExecutionContext createContext(IShellInput stdin, IShellOutput stdout, IShellOutput stderr) {
        return new DefaultShellExecutionContext(
                stdin, stdout, stderr,
                new HashMap<>(), "/", new String[0], null, cancelToken
        );
    }

    private IShellCommandExecutionContext defaultContext() {
        return createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );
    }

    @Test
    void testPipelineThreeStages() throws Exception {
        ShellCommandExecutor executor = new ShellCommandExecutor(registry, fileSystem);
        ExecutionResult result = executor.execute("echo aaa | echo bbb | echo ccc", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("ccc"));
        assertFalse(result.stdout().contains("aaa"));
    }

    @Test
    void testPipelineStagesRunConcurrently() throws Exception {
        AtomicBoolean stage1Started = new AtomicBoolean(false);
        AtomicBoolean stage2Reached = new AtomicBoolean(false);

        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "blocker"; }
            @Override public String description() { return "blocks"; }
            @Override public String usage() { return "blocker"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) throws Exception {
                stage1Started.set(true);
                ctx.stdout().println("data-from-blocker");
                Thread.sleep(500);
                return 0;
            }
        });
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "waiter"; }
            @Override public String description() { return "waits"; }
            @Override public String usage() { return "waiter"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) throws Exception {
                stage2Reached.set(true);
                ((BlockingQueueShellOutput) ctx.stdout()).close();
                return 0;
            }
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        CompletableFuture<ExecutionResult> future = executor.execute("blocker | waiter", defaultContext())
                .toCompletableFuture();

        for (int i = 0; i < 100 && !stage1Started.get(); i++) {
            Thread.sleep(10);
        }
        assertTrue(stage1Started.get(), "Stage 1 should have started");

        assertTrue(stage2Reached.get() || future.isDone(),
                "Stage 2 should start while stage 1 is still running (concurrent pipeline)");

        ExecutionResult result = future.get(5, TimeUnit.SECONDS);
        assertEquals(0, result.exitCode());
    }

    @Test
    void testPipelineFailingMiddleStage() throws Exception {
        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "failcmd"; }
            @Override public String description() { return "fails"; }
            @Override public String usage() { return "failcmd"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) throws Exception {
                ctx.stderr().println("intentional failure");
                return 1;
            }
        });
        localRegistry.registerCommand(new io.nop.ai.shell.commands.impl.EchoCommand());

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        ExecutionResult result = executor.execute("failcmd | echo after", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode(), "Last stage exit code should be used");
        assertTrue(result.stdout().contains("after"));
    }

    @Test
    void testPipelineLastStageFailsReturnsNonZero() throws Exception {
        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new io.nop.ai.shell.commands.impl.EchoCommand());
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "failtail"; }
            @Override public String description() { return "fails at end"; }
            @Override public String usage() { return "failtail"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) throws Exception {
                return 42;
            }
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        ExecutionResult result = executor.execute("echo ok | failtail", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(42, result.exitCode(), "Pipeline exit code should be last stage's code");
    }

    @Test
    void testLogicalAndShortCircuitLeftFails() throws Exception {
        AtomicBoolean rightExecuted = new AtomicBoolean(false);
        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "failcmd"; }
            @Override public String description() { return "fails"; }
            @Override public String usage() { return "failcmd"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) { return 1; }
        });
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "neverrun"; }
            @Override public String description() { return "never"; }
            @Override public String usage() { return "neverrun"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) {
                rightExecuted.set(true);
                return 0;
            }
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        ExecutionResult result = executor.execute("failcmd && neverrun", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(1, result.exitCode());
        assertFalse(rightExecuted.get(), "Right side of && should NOT execute when left fails");
    }

    @Test
    void testLogicalAndBothSucceed() throws Exception {
        ShellCommandExecutor executor = new ShellCommandExecutor(registry, fileSystem);
        ExecutionResult result = executor.execute("echo first && echo second", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("second"));
    }

    @Test
    void testLogicalOrShortCircuitLeftSucceeds() throws Exception {
        AtomicBoolean rightExecuted = new AtomicBoolean(false);
        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new io.nop.ai.shell.commands.impl.EchoCommand());
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "neverrun"; }
            @Override public String description() { return "never"; }
            @Override public String usage() { return "neverrun"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) {
                rightExecuted.set(true);
                return 0;
            }
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        ExecutionResult result = executor.execute("echo ok || neverrun", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertFalse(rightExecuted.get(), "Right side of || should NOT execute when left succeeds");
    }

    @Test
    void testLogicalOrLeftFailsRightRuns() throws Exception {
        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "failcmd"; }
            @Override public String description() { return "fails"; }
            @Override public String usage() { return "failcmd"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) { return 1; }
        });
        localRegistry.registerCommand(new io.nop.ai.shell.commands.impl.EchoCommand());

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        ExecutionResult result = executor.execute("failcmd || echo fallback", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("fallback"));
    }

    @Test
    void testBackgroundJobRemovedFromMapOnCompletion() throws Exception {
        ShellCommandExecutor executor = new ShellCommandExecutor(registry, fileSystem);
        ExecutionResult result = executor.execute("echo done &", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());

        for (int i = 0; i < 100 && !executor.getBackgroundJobs().isEmpty(); i++) {
            Thread.sleep(20);
        }

        assertTrue(executor.getBackgroundJobs().isEmpty(),
                "Background job should be removed from map after completion");
    }

    @Test
    void testBackgroundJobWithExceptionStillRemovedFromMap() throws Exception {
        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "crash"; }
            @Override public String description() { return "crashes"; }
            @Override public String usage() { return "crash"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) {
                throw new IllegalStateException("intentional crash");
            }
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        ExecutionResult result = executor.execute("crash &", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode(), "Background launch always returns 0");

        for (int i = 0; i < 100 && !executor.getBackgroundJobs().isEmpty(); i++) {
            Thread.sleep(20);
        }

        assertTrue(executor.getBackgroundJobs().isEmpty(),
                "Background job should be removed from map even after exception");
    }

    @Test
    void testMultipleBackgroundJobs() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "counter"; }
            @Override public String description() { return "counts"; }
            @Override public String usage() { return "counter"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) {
                executionCount.incrementAndGet();
                return 0;
            }
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);

        ExecutionResult r1 = executor.execute("counter &", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        ExecutionResult r2 = executor.execute("counter &", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        ExecutionResult r3 = executor.execute("counter &", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(r1.stdout().contains("[1]"));
        assertTrue(r2.stdout().contains("[2]"));
        assertTrue(r3.stdout().contains("[3]"));

        for (int i = 0; i < 200 && executionCount.get() < 3; i++) {
            Thread.sleep(20);
        }
        assertEquals(3, executionCount.get(), "All 3 background jobs should have executed");

        for (int i = 0; i < 100 && !executor.getBackgroundJobs().isEmpty(); i++) {
            Thread.sleep(20);
        }
        assertTrue(executor.getBackgroundJobs().isEmpty(), "All background jobs should be removed after completion");
    }

    @Test
    void testCloseIdempotent() throws Exception {
        ShellCommandExecutor executor = new ShellCommandExecutor(registry, fileSystem);
        executor.close();
        executor.close();
        executor.close();
    }

    @Test
    void testCloseWithNoBackgroundJobs() throws Exception {
        ShellCommandExecutor executor = new ShellCommandExecutor(registry, fileSystem);
        executor.execute("echo hello", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        executor.close();
        assertTrue(executor.getBackgroundJobs().isEmpty());
    }

    @Test
    void testSemicolonExitCodeIsLastCommand() throws Exception {
        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new io.nop.ai.shell.commands.impl.EchoCommand());
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "failcmd"; }
            @Override public String description() { return "fails"; }
            @Override public String usage() { return "failcmd"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) { return 5; }
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        ExecutionResult result = executor.execute("failcmd; echo after", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode(), "Semicolon exit code is the last command's exit code");
        assertTrue(result.stdout().contains("after"));
    }

    @Test
    void testPipelineWithCommandNotFoundInMiddle() throws Exception {
        ShellCommandExecutor executor = new ShellCommandExecutor(registry, fileSystem);
        ExecutionResult result = executor.execute("nonexistent_cmd | echo recovery", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode(), "Last stage determines exit code");
        assertTrue(result.stdout().contains("recovery"));
    }

    @Test
    void testConcurrentPipelineStagesAllGetEof() throws Exception {
        AtomicInteger stage2InputLines = new AtomicInteger(0);
        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "producer"; }
            @Override public String description() { return "produces"; }
            @Override public String usage() { return "producer"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) throws Exception {
                ctx.stdout().println("line1");
                ctx.stdout().println("line2");
                return 0;
            }
        });
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "consumer"; }
            @Override public String description() { return "consumes"; }
            @Override public String usage() { return "consumer"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) throws Exception {
                while (true) {
                    var chunk = ctx.stdin().read();
                    if (chunk == null || chunk.isEof()) break;
                    if (chunk.isText()) {
                        String text = chunk.asText();
                        for (int i = 0; i < text.length(); i++) {
                            if (text.charAt(i) == '\n') stage2InputLines.incrementAndGet();
                        }
                    }
                }
                return 0;
            }
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        ExecutionResult result = executor.execute("producer | consumer", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertEquals(2, stage2InputLines.get(), "Consumer should receive both lines from producer");
    }

    @Test
    void testSlowProducerFastConsumerPipeline() throws Exception {
        AtomicBoolean consumerGotEof = new AtomicBoolean(false);
        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "slowprod"; }
            @Override public String description() { return "slow prod"; }
            @Override public String usage() { return "slowprod"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) throws Exception {
                for (int i = 0; i < 5; i++) {
                    ctx.stdout().println("chunk" + i);
                    Thread.sleep(50);
                }
                return 0;
            }
        });
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "fastcon"; }
            @Override public String description() { return "fast con"; }
            @Override public String usage() { return "fastcon"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) throws Exception {
                while (true) {
                    var chunk = ctx.stdin().read();
                    if (chunk == null || chunk.isEof()) {
                        consumerGotEof.set(true);
                        break;
                    }
                }
                return 0;
            }
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        ExecutionResult result = executor.execute("slowprod | fastcon", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(consumerGotEof.get(), "Consumer must receive EOF after producer closes output");
    }

    @Test
    void testBackgroundSlowJobCancelledOnClose() throws Exception {
        AtomicBoolean started = new AtomicBoolean(false);

        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override public String name() { return "veryslow"; }
            @Override public String description() { return "very slow"; }
            @Override public String usage() { return "veryslow"; }
            @Override
            public int execute(IShellCommandExecutionContext ctx) {
                started.set(true);
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 0;
            }
        });

        ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
        executor.execute("veryslow &", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        for (int i = 0; i < 100 && !started.get(); i++) {
            Thread.sleep(10);
        }
        assertTrue(started.get());

        assertFalse(executor.getBackgroundJobs().isEmpty(), "Job should be in map before close");

        executor.close();

        assertTrue(executor.getBackgroundJobs().isEmpty(), "Job should be cleared after close");
    }

    @Test
    void testSubshellWithBackgroundJobInside() throws Exception {
        ShellCommandExecutor executor = new ShellCommandExecutor(registry, fileSystem);
        ExecutionResult result = executor.execute("(echo inside)", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("inside"));
    }

    @Test
    void testExportedEnvDoesNotLeakBetweenExecutors() throws Exception {
        ShellCommandExecutor executor1 = new ShellCommandExecutor(registry, fileSystem);
        ShellCommandExecutor executor2 = new ShellCommandExecutor(registry, fileSystem);

        Map<String, String> env1 = executor1.getExportedEnv();
        Map<String, String> env2 = executor2.getExportedEnv();

        assertNotSame(env1, env2);
        assertTrue(env1.isEmpty());
        assertTrue(env2.isEmpty());
    }

    @Test
    void testCheckerWithPipelineChecksAllStages() throws Exception {
        AtomicReference<String> checkedCommand = new AtomicReference<>();
        io.nop.ai.shell.checker.ICommandChecker checker = (command, ctx) -> {
            checkedCommand.set(command.getCommand());
            if (command.getCommand().equals("blocked")) {
                return "blocked command";
            }
            return null;
        };

        ShellCommandExecutor executor = new ShellCommandExecutor(registry, null, checker, fileSystem);
        ExecutionResult result = executor.execute("echo ok | blocked", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(126, result.exitCode());
        assertTrue(result.stderr().contains("blocked command"));
    }

    @Test
    void testCheckerWithLogicalAndChecksBothSides() throws Exception {
        AtomicInteger checkCount = new AtomicInteger(0);
        io.nop.ai.shell.checker.ICommandChecker checker = (command, ctx) -> {
            checkCount.incrementAndGet();
            return null;
        };

        ShellCommandExecutor executor = new ShellCommandExecutor(registry, null, checker, fileSystem);
        ExecutionResult result = executor.execute("echo first && echo second", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertEquals(2, checkCount.get(), "Both sides of && should be pre-checked");
    }

    @Test
    void testCheckerWithBackgroundChecksInner() throws Exception {
        AtomicBoolean innerChecked = new AtomicBoolean(false);
        io.nop.ai.shell.checker.ICommandChecker checker = (command, ctx) -> {
            innerChecked.set(true);
            return null;
        };

        ShellCommandExecutor executor = new ShellCommandExecutor(registry, null, checker, fileSystem);
        ExecutionResult result = executor.execute("echo bg &", defaultContext())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(innerChecked.get(), "Background inner command should be pre-checked");
    }
}
