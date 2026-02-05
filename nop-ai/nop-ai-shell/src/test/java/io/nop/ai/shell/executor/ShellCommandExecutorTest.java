package io.nop.ai.shell.executor;

import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.commands.ShellCommandRegistry;
import io.nop.ai.shell.commands.impl.EchoCommand;
import io.nop.ai.shell.commands.impl.LsCommand;
import io.nop.ai.shell.io.BlockingQueueShellInput;
import io.nop.ai.shell.io.BlockingQueueShellOutput;
import io.nop.ai.shell.io.FileShellInput;
import io.nop.ai.shell.io.FileShellOutput;
import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Disabled
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
            private boolean cancelled = false;
            private String reason;

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public String getCancelReason() {
                return reason;
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

    @Test
    void testSimpleCommandExecution() throws Exception {
        BlockingQueueShellOutput stdout = new BlockingQueueShellOutput();
        BlockingQueueShellOutput stderr = new BlockingQueueShellOutput();

        Map<String, String> env = new HashMap<>();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
            new BlockingQueueShellInput(1),
            stdout,
            stderr,
            env,
            "/",
            new String[0],
            null,
            cancelToken
        );

        var result = executor.execute("echo hello world", context).toCompletableFuture().get();

        assertEquals(0, result.exitCode());
        assertNotNull(result.stdout());
        assertTrue(result.stdout().contains("hello world"));
    }

    @Test
    void testLsCommand() throws Exception {
        BlockingQueueShellOutput stdout = new BlockingQueueShellOutput();
        BlockingQueueShellOutput stderr = new BlockingQueueShellOutput();

        Map<String, String> env = new HashMap<>();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
            new BlockingQueueShellInput(1),
            stdout,
            stderr,
            env,
            "/",
            new String[0],
            null,
            cancelToken
        );

        var result = executor.execute("ls", context).toCompletableFuture().get();

        assertEquals(0, result.exitCode());
        assertNotNull(result.stdout());
        assertTrue(result.stdout().contains("file1.txt"));
    }

    @Test
    void testLsLongFormat() throws Exception {
        BlockingQueueShellOutput stdout = new BlockingQueueShellOutput();
        BlockingQueueShellOutput stderr = new BlockingQueueShellOutput();

        Map<String, String> env = new HashMap<>();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
            new BlockingQueueShellInput(1),
            stdout,
            stderr,
            env,
            "/",
            new String[0],
            null,
            cancelToken
        );

        var result = executor.execute("ls -l", context).toCompletableFuture().get();

        assertEquals(0, result.exitCode());
        assertNotNull(result.stdout());
        assertTrue(result.stdout().contains("user"));
        assertTrue(result.stdout().contains("group"));
    }

    @Test
    void testPipelineExecution() throws Exception {
        BlockingQueueShellOutput stdout = new BlockingQueueShellOutput();
        BlockingQueueShellOutput stderr = new BlockingQueueShellOutput();

        Map<String, String> env = new HashMap<>();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
            new BlockingQueueShellInput(1),
            stdout,
            stderr,
            env,
            "/",
            new String[0],
            null,
            cancelToken
        );

        var result = executor.execute("echo test | echo second", context).toCompletableFuture().get();

        assertEquals(0, result.exitCode());
        assertNotNull(result.stdout());
        assertTrue(result.stdout().contains("second"));
    }

    @Test
    void testEnvironmentVariables() throws Exception {
        BlockingQueueShellOutput stdout = new BlockingQueueShellOutput();
        BlockingQueueShellOutput stderr = new BlockingQueueShellOutput();

        Map<String, String> env = new HashMap<>();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
            new BlockingQueueShellInput(1),
            stdout,
            stderr,
            env,
            "/",
            new String[]{},
            null,
            cancelToken
        );

        var result1 = executor.execute("export TEST=123; echo $TEST", context).toCompletableFuture().get();
        var result2 = executor.execute("echo $TEST", context).toCompletableFuture().get();

        assertEquals(0, result2.exitCode());
    }

    @Test
    void testOutputRedirectToFile() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        testFile.toFile().deleteOnExit();

        Map<String, String> env = new HashMap<>();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
            new BlockingQueueShellInput(1),
            new BlockingQueueShellOutput(),
            new BlockingQueueShellOutput(),
            env,
            "/",
            new String[0],
            null,
            cancelToken
        );

        var result = executor.execute("echo hello world > " + testFile.toAbsolutePath(), context).toCompletableFuture().get();

        assertEquals(0, result.exitCode());
        String content = Files.readString(testFile);
        assertTrue(content.contains("hello world"));
    }

    @Test
    void testOutputAppendToFile() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        testFile.toFile().deleteOnExit();

        Files.writeString(testFile, "line1\n");

        Map<String, String> env = new HashMap<>();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
            new BlockingQueueShellInput(1),
            new BlockingQueueShellOutput(),
            new BlockingQueueShellOutput(),
            env,
            "/",
            new String[0],
            null,
            cancelToken
        );

        var result = executor.execute("echo line2 >> " + testFile.toAbsolutePath(), context).toCompletableFuture().get();

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

        Map<String, String> env = new HashMap<>();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
            new BlockingQueueShellInput(1),
            new BlockingQueueShellOutput(),
            new BlockingQueueShellOutput(),
            env,
            "/",
            new String[0],
            null,
            cancelToken
        );

        var result = executor.execute("echo < " + testFile.toAbsolutePath(), context).toCompletableFuture().get();

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello from file"));
    }

    @Test
    void testStderrRedirectToStdout() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        testFile.toFile().deleteOnExit();

        Map<String, String> env = new HashMap<>();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
            new BlockingQueueShellInput(1),
            new BlockingQueueShellOutput(),
            new BlockingQueueShellOutput(),
            env,
            "/",
            new String[0],
            null,
            cancelToken
        );

        var result = executor.execute("echo test 2>&1 > " + testFile.toAbsolutePath(), context).toCompletableFuture().get();

        assertEquals(0, result.exitCode());
        String content = Files.readString(testFile);
        assertTrue(content.contains("test"));
    }

    @Test
    void testMergeStdoutAndStderr() throws Exception {
        Path testFile = Files.createTempFile("test", ".txt");
        testFile.toFile().deleteOnExit();

        Map<String, String> env = new HashMap<>();
        IShellCommandExecutionContext context = new DefaultShellExecutionContext(
            new BlockingQueueShellInput(1),
            new BlockingQueueShellOutput(),
            new BlockingQueueShellOutput(),
            env,
            "/",
            new String[0],
            null,
            cancelToken
        );

        var result = executor.execute("echo stdout &> " + testFile.toAbsolutePath(), context).toCompletableFuture().get();

        assertEquals(0, result.exitCode());
        String content = Files.readString(testFile);
        assertTrue(content.contains("stdout"));
    }
}
