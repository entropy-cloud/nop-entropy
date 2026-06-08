package io.nop.ai.shell.executor;

import io.nop.ai.shell.checker.DefaultCommandChecker;
import io.nop.ai.shell.checker.ICommandCheckContext;
import io.nop.ai.shell.checker.ICommandChecker;
import io.nop.ai.shell.commands.AbstractShellCommand;
import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommand;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.commands.ShellCommandRegistry;
import io.nop.ai.shell.commands.impl.EchoCommand;
import io.nop.ai.shell.commands.impl.LsCommand;
import io.nop.ai.shell.io.BlockingQueueShellInput;
import io.nop.ai.shell.io.BlockingQueueShellOutput;
import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.ai.shell.model.SimpleCommand;
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

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class ShellCommandExecutorTest {

    private ShellCommandExecutor executor;
    private ShellCommandRegistry registry;
    private ICancelToken cancelToken;

    @BeforeEach
    void setUp() {
        registry = new ShellCommandRegistry();
        registry.registerCommand(new EchoCommand());
        registry.registerCommand(new LsCommand());

        executor = new ShellCommandExecutor(registry);
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

    private IShellCommandExecutionContext createContext(IShellInput stdin, IShellOutput stdout, IShellOutput stderr) {
        return new DefaultShellExecutionContext(
                stdin, stdout, stderr,
                new HashMap<>(), "/", new String[0], null, cancelToken
        );
    }

    @Test
    void testSimpleCommandExecution() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo hello world", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertNotNull(result.stdout());
        assertTrue(result.stdout().contains("hello world"));
    }

    @Test
    void testLsCommand() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("ls", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertNotNull(result.stdout());
        assertTrue(result.stdout().contains("file1.txt"));
    }

    @Test
    void testLsLongFormat() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("ls -l", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertNotNull(result.stdout());
        assertTrue(result.stdout().contains("user"));
        assertTrue(result.stdout().contains("group"));
    }

    @Test
    void testPipelineExecution() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo test | echo second", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertNotNull(result.stdout());
        assertTrue(result.stdout().contains("second"));
    }

    @Test
    void testOutputRedirectToFile() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        testFile.toFile().deleteOnExit();

        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo hello world > " + testFile.toAbsolutePath(), context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        String content = Files.readString(testFile);
        assertTrue(content.contains("hello world"));
    }

    @Test
    void testOutputAppendToFile() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        testFile.toFile().deleteOnExit();
        Files.writeString(testFile, "line1\n");

        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo line2 >> " + testFile.toAbsolutePath(), context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        String content = Files.readString(testFile);
        assertTrue(content.contains("line1"));
        assertTrue(content.contains("line2"));
    }

    @Test
    void testInputRedirectFromFile() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        testFile.toFile().deleteOnExit();
        Files.writeString(testFile, "hello from file\n");

        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo < " + testFile.toAbsolutePath(), context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
    }

    @Test
    void testStderrRedirectToStdout() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        testFile.toFile().deleteOnExit();

        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo test 2>&1 > " + testFile.toAbsolutePath(), context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        String content = Files.readString(testFile);
        assertTrue(content.contains("test"));
    }

    @Test
    void testMergeStdoutAndStderr() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        testFile.toFile().deleteOnExit();

        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo stdout &> " + testFile.toAbsolutePath(), context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        String content = Files.readString(testFile);
        assertTrue(content.contains("stdout"));
    }

    @Test
    void testCommandNotFoundReturns127() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("nonexistent_cmd", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(127, result.exitCode());
        assertTrue(result.stderr().contains("Command not found"));
    }

    @Test
    void testSemicolonBothCommandsExecute() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo line1; echo line2", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("line1"));
        assertTrue(result.stdout().contains("line2"));
    }

    @Test
    void testBackgroundExprReturnsImmediately() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo bg &", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("running in background"));
    }

    @Test
    void testGroupExprEnvironmentRestore() throws Exception {
        executor = new ShellCommandExecutor(registry);
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo inside_group", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
    }

    @Test
    void testSubshellExprEnvironmentRestore() throws Exception {
        executor = new ShellCommandExecutor(registry);
        Map<String, String> beforeEnv = new HashMap<>(executor.getExportedEnv());

        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("(echo subshell)", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertEquals(beforeEnv, executor.getExportedEnv());
    }

    @Test
    void testCloseCancelsBackgroundJobs() throws Exception {
        AtomicBoolean wasInterrupted = new AtomicBoolean(false);
        AtomicBoolean started = new AtomicBoolean(false);

        ShellCommandRegistry localRegistry = new ShellCommandRegistry();
        localRegistry.registerCommand(new AbstractShellCommand() {
            @Override
            public String name() { return "slow"; }

            @Override
            public String description() { return "slow command"; }

            @Override
            public String usage() { return "slow"; }

            @Override
            public int execute(IShellCommandExecutionContext context) throws Exception {
                started.set(true);
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    wasInterrupted.set(true);
                    Thread.currentThread().interrupt();
                }
                return 0;
            }
        });

        ShellCommandExecutor localExecutor = new ShellCommandExecutor(localRegistry);
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = localExecutor.execute("slow &", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertFalse(localExecutor.getBackgroundJobs().isEmpty());

        for (int i = 0; i < 100 && !started.get(); i++) {
            Thread.sleep(10);
        }
        assertTrue(started.get(), "Background job should have started");

        localExecutor.close();

        assertTrue(localExecutor.getBackgroundJobs().isEmpty(), "Background jobs should be cleared after close");
    }

    @Test
    void testPreCheckRejectionReturns126() throws Exception {
        ICommandChecker rejectingChecker = (command, ctx) -> {
            if (command.getCommand().equals("echo")) {
                return "echo is not allowed";
            }
            return null;
        };

        ShellCommandExecutor localExecutor = new ShellCommandExecutor(registry, null, rejectingChecker);
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = localExecutor.execute("echo hello", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(126, result.exitCode());
        assertTrue(result.stderr().contains("echo is not allowed"));
    }

    @Test
    void testPreCheckAllowsNonBlockedCommands() throws Exception {
        ICommandChecker selectiveChecker = (command, ctx) -> {
            if (command.getCommand().equals("dangerous")) {
                return "dangerous command blocked";
            }
            return null;
        };

        ShellCommandExecutor localExecutor = new ShellCommandExecutor(registry, null, selectiveChecker);
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = localExecutor.execute("echo hello", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello"));
    }

    @Test
    void testDefaultCommandCheckerAllowsAll() {
        DefaultCommandChecker checker = new DefaultCommandChecker();
        assertNull(checker.check(
                SimpleCommand.builder("echo").arg("test").build(),
                new ICommandCheckContext() {
                    @Override public String workingDirectory() { return "/"; }
                    @Override public Map<String, String> environment() { return Map.of(); }
                    @Override public boolean isRegisteredCommand(String name) { return true; }
                }
        ));
    }

    @Test
    void testExternalCommandAdapterThrowsException() {
        io.nop.ai.shell.adapter.ExternalCommandAdapter adapter = new io.nop.ai.shell.adapter.ExternalCommandAdapter();
        SimpleCommand cmd = SimpleCommand.builder("git").arg("status").build();

        assertThrows(UnsupportedOperationException.class, () -> {
            adapter.execute(cmd, null, null, null, null);
        });
    }

    @Test
    void testExecutorImplementsCloseable() {
        assertTrue(java.io.Closeable.class.isAssignableFrom(ShellCommandExecutor.class));
    }
}
