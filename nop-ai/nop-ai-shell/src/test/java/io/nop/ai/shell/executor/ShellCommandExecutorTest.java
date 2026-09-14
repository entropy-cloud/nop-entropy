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
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.LocalToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.FileHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(ShellCommandExecutorTest.class);

    private ShellCommandExecutor executor;
    private ShellCommandRegistry registry;
    private ICancelToken cancelToken;
    private IToolFileSystem fileSystem;
    private String workDir;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        registry = new ShellCommandRegistry();
        registry.registerCommand(new EchoCommand());
        registry.registerCommand(new LsCommand());

        tempDir = Files.createTempDirectory("shell-test");
        fileSystem = new LocalToolFileSystem(tempDir.toFile());
        workDir = tempDir.toAbsolutePath().toString();

        fileSystem.writeText("file1.txt", "content", false);
        fileSystem.mkdirs("subdir");

        executor = new ShellCommandExecutor(registry, fileSystem);
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
        if (tempDir != null && Files.exists(tempDir) && !FileHelper.deleteAll(tempDir.toFile())) {
            LOG.warn("nop.test.fail-delete-temp-dir:dir={}", tempDir);
        }
    }

    private IShellCommandExecutionContext createContext(IShellInput stdin, IShellOutput stdout, IShellOutput stderr) {
        return new DefaultShellExecutionContext(
                stdin, stdout, stderr,
                new HashMap<>(), workDir, new String[0], fileSystem, cancelToken
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
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo hello world > test_output.txt", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        String content = fileSystem.readText("test_output.txt", 0).getContent();
        assertTrue(content.contains("hello world"));
    }

    @Test
    void testOutputAppendToFile() throws Exception {
        fileSystem.writeText("test_append.txt", "line1\n", false);

        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo line2 >> test_append.txt", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        String content = fileSystem.readText("test_append.txt", 0).getContent();
        assertTrue(content.contains("line1"));
        assertTrue(content.contains("line2"));
    }

    @Test
    void testInputRedirectFromFile() throws Exception {
        fileSystem.writeText("test_input.txt", "hello from file\n", false);

        // echo 不读 stdin，无法观察输入重定向是否生效；注册一个读 stdin 并回显的
        // 测试专用命令（cat），使重定向内容进入 stdout 可断言
        registry.registerCommand(new AbstractShellCommand() {
            @Override
            public String name() { return "cat"; }

            @Override
            public String description() { return "copy stdin to stdout"; }

            @Override
            public String usage() { return "cat"; }

            @Override
            public int execute(IShellCommandExecutionContext context) throws Exception {
                context.stdout().print(context.stdin().readAllText());
                context.stdout().flush();
                return 0;
            }
        });

        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("cat < test_input.txt", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello from file"),
                "input redirect must feed the file content to the command stdin — got: " + result.stdout());
    }

    @Test
    void testStderrRedirectToStdout() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo test 2>&1 > test_stderr.txt", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        String content = fileSystem.readText("test_stderr.txt", 0).getContent();
        assertTrue(content.contains("test"));
    }

    @Test
    void testMergeStdoutAndStderr() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo stdout &> test_merge.txt", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        String content = fileSystem.readText("test_merge.txt", 0).getContent();
        assertTrue(content.contains("stdout"));
    }

    /**
     * M6-P1 (round-2 audit): {@code &>} merge must write each byte exactly
     * once. Pre-fix, handleMergeRedirect built {@code new TeeOutput(fileOutput,
     * fileOutput)} — the same instance twice — so every write was fanned out
     * to two legs writing the same buffer and each flush doubled the merged
     * file content ({@code echo stdout &> f} produced two lines while the old
     * {@code contains("stdout")} assertion stayed green).
     */
    @Test
    void testMergeStdoutAndStderrExactSingleLine() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo stdout &> test_merge_exact.txt", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        String content = fileSystem.readText("test_merge_exact.txt", 0).getContent();
        assertEquals("stdout\n", content,
                "&> merge must write the output exactly once (M6-P1) — got: " + content);
    }

    /**
     * M6-P1 (round-2 audit): {@code &>>} append-merge must append the merged
     * content without duplicating it across runs.
     */
    @Test
    void testMergeAppendExactContent() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult r1 = executor.execute("echo first &> test_merge_append.txt", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(0, r1.exitCode());

        ExecutionResult r2 = executor.execute("echo second &>> test_merge_append.txt", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(0, r2.exitCode());

        String content = fileSystem.readText("test_merge_append.txt", 0).getContent();
        assertEquals("first\nsecond\n", content,
                "&>> must append one line per run, no duplication (M6-P1) — got: " + content);
    }

    /**
     * M6-P1 (round-2 audit): the merged file must capture BOTH stdout and
     * stderr, in execution order, each byte exactly once.
     */
    @Test
    void testMergeCapturesStdoutAndStderrInOrder() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult r1 = executor.execute("echo out &> test_merge_both.txt", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(0, r1.exitCode());

        ExecutionResult r2 = executor.execute("echo err >&2 &>> test_merge_both.txt", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(0, r2.exitCode());

        String content = fileSystem.readText("test_merge_both.txt", 0).getContent();
        assertEquals("out\nerr\n", content,
                "merged file must contain stdout and stderr lines once each, in order (M6-P1) — got: "
                        + content);
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
        executor = new ShellCommandExecutor(registry, fileSystem);
        assertFalse(executor.getExportedEnv().containsKey("GROUP_VAR"),
                "GROUP_VAR must not exist in the exported env before the group runs");

        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        // 真实 group 表达式（BashSyntaxParser 把 env 赋值挂到后续命令上，故用
        // "export GROUP_VAR=1 echo in_group" 形态）：export 发生在 group 内部，
        // group 结束后环境必须还原，不得泄漏到外部。
        ExecutionResult result = executor.execute("{ export GROUP_VAR=1 echo in_group; }", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertFalse(executor.getExportedEnv().containsKey("GROUP_VAR"),
                "export inside a group must not leak to the enclosing environment (group restores env)");

        // 对照：group 之外的同形态 export 必须泄漏——证明上面的还原断言是 group
        // 隔离语义，而非 export 机制整体失效
        ExecutionResult outside = executor.execute("export GROUP_VAR=1 echo leaked", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(0, outside.exitCode());
        assertEquals("1", executor.getExportedEnv().get("GROUP_VAR"),
                "outside a group, an export must leak into the exported env");
    }

    @Test
    void testSubshellExprEnvironmentRestore() throws Exception {
        executor = new ShellCommandExecutor(registry, fileSystem);
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

        ShellCommandExecutor localExecutor = new ShellCommandExecutor(localRegistry, fileSystem);
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

        ShellCommandExecutor localExecutor = new ShellCommandExecutor(registry, null, rejectingChecker, fileSystem);
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

        ShellCommandExecutor localExecutor = new ShellCommandExecutor(registry, null, selectiveChecker, fileSystem);
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
    void testDefaultExecutorAssemblesDefaultCommandChecker() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("rm -rf /", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(126, result.exitCode(), "dangerous command must be rejected by the default checker");
        assertTrue(result.stderr().contains("blocked") || result.stderr().contains("deny"),
                "rejection reason must be returned, got: " + result.stderr());
    }

    @Test
    void testDefaultExecutorStillAllowsSafeCommands() throws Exception {
        IShellCommandExecutionContext context = createContext(
                new BlockingQueueShellInput(1),
                new BlockingQueueShellOutput(),
                new BlockingQueueShellOutput()
        );

        ExecutionResult result = executor.execute("echo hello", context)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("hello"));
    }

    @Test
    void testDefaultCommandCheckerAllowsEcho() {
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
