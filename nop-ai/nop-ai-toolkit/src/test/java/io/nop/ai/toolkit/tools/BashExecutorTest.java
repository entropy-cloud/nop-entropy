package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxConfig;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxFailureReason;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxRequest;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxResult;
import io.nop.ai.toolkit.tools.sandbox.HostBashSandbox;
import io.nop.ai.toolkit.tools.sandbox.IBashSandbox;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class BashExecutorTest {
    private BashExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new BashExecutor(newHostSandbox());
    }

    /**
     * Host backend whitelisting the current working directory and the JVM tmpdir tree so the
     * working-directory jail lets the test commands run.
     */
    private static HostBashSandbox newHostSandbox() {
        BashSandboxConfig config = BashSandboxConfig.builder()
                .wallSeconds(15)
                .build();
        java.util.List<java.nio.file.Path> allowed = java.util.List.of(
                cwdRealPath(),
                Paths.get(System.getProperty("java.io.tmpdir")));
        return new HostBashSandbox(config, allowed);
    }

    private static java.nio.file.Path cwdRealPath() {
        try {
            return Paths.get(".").toRealPath();
        } catch (java.io.IOException e) {
            return Paths.get(".").toAbsolutePath().normalize();
        }
    }

    @Test
    void testToolName() {
        assertEquals("bash", executor.getToolName());
    }

    @Test
    void testExecuteEchoCommand() {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("echo Hello World");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("Hello World"));
    }

    @Test
    void testExecuteCommandWithExitCode() {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("exit 0");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void testExecuteCommandFailure() {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("exit 1");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertEquals(1, result.getExitCode());
    }

    // ========================================================================
    // M8-P1 round-4: an ordinary command failure must surface as a real tool
    // failure result carrying the real exit code and output — it must NEVER be
    // swallowed into "Sandbox refused execution [CONTAINER_START_FAILED]".
    // ========================================================================

    /**
     * toResult fidelity (CI fallback of the docker e2e test): a sandbox returning a non-zero
     * BashSandboxResult must produce a failure result with the real exit code and the command
     * output in the error body — the bash.tool.xml error contract.
     */
    @Test
    void testCommandFailurePreservesExitCodeAndOutput() {
        IBashSandbox failing = req -> new BashSandboxResult(
                2, "ls: cannot access '/nonexistent': No such file or directory", "", false);
        BashExecutor wired = new BashExecutor(failing);

        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("ls /nonexistent");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = wired.executeAsync(call, new MockContext()).toCompletableFuture().join();

        assertEquals("failure", result.getStatus());
        assertEquals(2, result.getExitCode(),
                "the command failure exit code must be preserved on the tool result");
        assertTrue(result.getError().getBody().contains("No such file or directory"),
                "the command output must be preserved in the error body, got: " + result.getError().getBody());
    }

    /**
     * Real end-to-end (bash tool entry → HostBashSandbox → sh -c → result): a failing command
     * comes back with its real exit code and output through the complete chain.
     */
    @Test
    void testRealHostBackendCommandFailurePreservesExitCodeAndOutput() {
        assumeTrue(isShAvailable(), "sh not available on this host — host-backend tests require a POSIX shell");

        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("echo cmd-failing-output; exit 2");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();

        assertEquals("failure", result.getStatus());
        assertEquals(2, result.getExitCode(),
                "the real host command exit code must be preserved");
        assertTrue(result.getError().getBody().contains("cmd-failing-output"),
                "the real host command output must be preserved in the error body, got: "
                        + result.getError().getBody());
    }

    private static boolean isShAvailable() {
        try {
            Process p = new ProcessBuilder(List.of("sh", "-c", "echo ok"))
                    .redirectErrorStream(true).start();
            byte[] buf = new byte[64];
            //noinspection StatementWithEmptyBody
            try (var in = p.getInputStream()) {
                while (in.read(buf) != -1) {
                    // drain
                }
            }
            return p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void testExecuteWithWorkingDir() {
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        assumeFalse(isWin, "workingDir test uses a posix command");
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.setAttr("workingDir", System.getProperty("java.io.tmpdir"));
        node.makeChild("command").setContentValue("pwd");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
    }

    @Test
    void testExecuteWithEnv() {
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        XNode node = XNode.make("bash");
        node.makeChild("command").setContentValue(isWin ? "echo %MY_VAR%" : "echo $MY_VAR");
        XNode env = node.makeChild("env");
        env.setAttr("name", "MY_VAR");
        env.setAttr("value", "test_value");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("test_value"));
    }

    @Test
    void testDestructiveCommandRejected() {
        XNode node = XNode.make("bash");
        node.makeChild("command").setContentValue("rm -rf /");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Command blocked"),
                "Destructive command must be blocked. Error: " + result.getError().getBody());
    }

    @Test
    void testEmptyCommandRejected() {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("empty command"),
                "Empty command must be rejected with validation message. Error: " + result.getError().getBody());
    }

    @Test
    void testBlankCommandRejected() {
        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("   \t ");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("empty command"));
    }

    @Test
    void testDangerousEnvVarRejected() {
        boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
        XNode node = XNode.make("bash");
        node.makeChild("command").setContentValue(isWin ? "echo %LD_PRELOAD%" : "echo $LD_PRELOAD");
        XNode env = node.makeChild("env");
        env.setAttr("name", "LD_PRELOAD");
        env.setAttr("value", "/tmp/malicious.so");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext()).toCompletableFuture().join();
        assertEquals("success", result.getStatus(),
                "Command must still run with dangerous env var stripped");
    }

    // ========================================================================
    // Plan 335 Phase 1: fail-closed default + wiring verification
    // ========================================================================

    /**
     * Fail-closed (Rule #24): when no backend is wired, the executor must refuse with an explicit
     * error and must NEVER spawn a host process. The structural guarantee is that BashExecutor
     * contains no ProcessBuilder code — it delegates entirely to the IBashSandbox seam, so a null
     * backend can only produce an error result (asserted here), never a host execution.
     */
    @Test
    void testFailClosedWhenNoBackendWired() {
        BashExecutor noBackend = new BashExecutor();
        noBackend.setSandbox(null);

        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("echo should-not-run");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = noBackend.executeAsync(call, new MockContext()).toCompletableFuture().join();

        assertEquals("failure", result.getStatus());
        assertNotNull(result.getError());
        assertTrue(result.getError().getBody().contains("fail-closed"),
                "expected fail-closed error, got: " + result.getError().getBody());
    }

    /**
     * Wiring verification (Rule #23): BashExecutor routes execution through the IBashSandbox seam
     * at runtime — the sandbox's execute() is actually invoked, and the legacy host ProcessBuilder
     * path is no longer used (the executor delegates, not duplicates).
     */
    @Test
    void testWiringRoutesThroughSeam() {
        RecordingSandbox sandbox = new RecordingSandbox();
        BashExecutor wired = new BashExecutor(sandbox);

        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("echo wired");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = wired.executeAsync(call, new MockContext()).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertEquals(1, sandbox.invocationCount(),
                "BashExecutor must delegate to the IBashSandbox seam");
        assertNotNull(sandbox.lastRequest());
        assertTrue(sandbox.lastRequest().getCommand().contains("echo wired"),
                "the command must be passed to the seam");
    }

    /**
     * Fail-closed when the configured backend refuses: the executor surfaces the refusal as an
     * error result and does not fall back to the host shell.
     */
    @Test
    void testFailClosedWhenBackendRefuses() {
        IBashSandbox refusing = req -> {
            throw new io.nop.ai.toolkit.tools.sandbox.BashSandboxException(
                    BashSandboxFailureReason.BACKEND_UNAVAILABLE, "daemon down");
        };
        BashExecutor wired = new BashExecutor(refusing);

        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("echo refused");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = wired.executeAsync(call, new MockContext()).toCompletableFuture().join();

        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("BACKEND_UNAVAILABLE"),
                "expected backend-unavailable error, got: " + result.getError().getBody());
    }

    /**
     * Wiring verification (M8-P1 round-4, Phase 2): the opt-in path used by the beans.xml
     * assembly example — {@code setSandbox(...)} — must route execution through the injected
     * backend at runtime (the field IS consumed, not a dead setter).
     */
    @Test
    void testWiringViaSetterInjectionRoutesExecution() {
        RecordingSandbox sandbox = new RecordingSandbox();
        BashExecutor wired = new BashExecutor();
        wired.setSandbox(sandbox);

        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("echo setter-wired");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = wired.executeAsync(call, new MockContext()).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertEquals(1, sandbox.invocationCount(),
                "the sandbox injected via setSandbox must be invoked at runtime");
    }

    /**
     * Fail-closed (M8-P1 round-4, Phase 2): with no backend wired the executor must refuse with
     * the explicit NO_BACKEND_ERROR message — never a silent success and never an empty result.
     */
    @Test
    void testUnwiredFailsClosedWithExplicitMessage() {
        BashExecutor noBackend = new BashExecutor();

        XNode node = XNode.make("bash");
        node.setAttr("id", "1");
        node.makeChild("command").setContentValue("echo should-not-run");
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = noBackend.executeAsync(call, new MockContext()).toCompletableFuture().join();

        assertEquals("failure", result.getStatus());
        assertEquals(BashExecutor.NO_BACKEND_ERROR, result.getError().getBody(),
                "the unwired executor must surface the explicit fail-closed message verbatim");
    }

    /**
     * A recording sandbox that captures the request and returns a canned success result, so the
     * wiring test can assert the seam was invoked without spawning a real process.
     */
    static final class RecordingSandbox implements IBashSandbox {
        private final AtomicInteger count = new AtomicInteger();
        private BashSandboxRequest last;

        @Override
        public BashSandboxResult execute(BashSandboxRequest request) {
            count.incrementAndGet();
            last = request;
            return new BashSandboxResult(0, "ok", "", false);
        }

        int invocationCount() {
            return count.get();
        }

        BashSandboxRequest lastRequest() {
            return last;
        }
    }

    static class MockContext implements IToolExecuteContext {
        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return Map.of(); }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public IToolFileSystem getFileSystem() { return null; }
        @Override public IThreadPoolExecutor getExecutor() { return SyncThreadPoolExecutor.INSTANCE; }
    }
}
