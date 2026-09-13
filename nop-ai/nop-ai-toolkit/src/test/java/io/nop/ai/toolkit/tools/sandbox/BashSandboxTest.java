package io.nop.ai.toolkit.tools.sandbox;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.tools.BashExecutor;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Plan 335 Phase 2: resource-limit enforcement + real-backend isolation proof for the Bash
 * sandbox backends. The destructive-command regex is NOT the primary control here — the
 * isolation backend (resource limits, working-directory jail, network deny) is.
 */
public class BashSandboxTest {

    @TempDir
    File tempDir;

    // ========================================================================
    // HostBashSandbox: wall-time timeout is enforced by the real host backend
    // (no Docker needed).
    // ========================================================================

    @Test
    void hostBackendEnforcesWallTimeTimeout() {
        assumeTrue(isShAvailable(), "sh not available on this host — host-backend launch tests require a POSIX shell");
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.builder().wallSeconds(1).build(),
                List.of(tempDir.toPath()));
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "sleep 30"))
                .workingDirectory(tempDir)
                .config(BashSandboxConfig.builder().wallSeconds(1).build())
                .build();

        long start = System.nanoTime();
        BashSandboxResult result = sandbox.execute(request);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertTrue(result.isTimedOut(), "a command exceeding the wall budget must be timed out");
        assertTrue(elapsedMs < 15_000L,
                "the runaway command must be killed well under the 30s sleep, elapsed=" + elapsedMs + "ms");
    }

    @Test
    void hostBackendCapturesExitCodeAndOutput() {
        assumeTrue(isShAvailable(), "sh not available on this host — host-backend launch tests require a POSIX shell");
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.builder().wallSeconds(10).build(),
                List.of(tempDir.toPath()));
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "echo isolated-output"))
                .workingDirectory(tempDir)
                .config(BashSandboxConfig.builder().wallSeconds(10).build())
                .build();

        BashSandboxResult result = sandbox.execute(request);
        assertFalse(result.isTimedOut());
        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("isolated-output"));
    }

    // ========================================================================
    // Working-directory jail: a working directory outside the allowedBaseDirs
    // whitelist is rejected BEFORE any process/container launches. This is the
    // CI fallback that asserts the jail is wired (no Docker daemon required).
    // ========================================================================

    @Test
    void hostBackendRejectsWorkingDirOutsideJail() {
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.defaults(),
                List.of(tempDir.toPath()));
        File outside = new File(System.getProperty("user.home"), "nop-sandbox-jail-probe");
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "echo should-not-run"))
                .workingDirectory(outside)
                .config(BashSandboxConfig.defaults())
                .build();

        BashSandboxException ex = assertThrows(BashSandboxException.class, () -> sandbox.execute(request));
        assertEquals(BashSandboxFailureReason.HOST_PATH_NOT_ALLOWED, ex.getReason());
    }

    @Test
    void hostBackendRejectsTraversalWorkingDir() {
        HostBashSandbox sandbox = new HostBashSandbox(
                BashSandboxConfig.defaults(),
                List.of(tempDir.toPath()));
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "echo no"))
                .workingDirectory(new File(tempDir, "../../escape"))
                .config(BashSandboxConfig.defaults())
                .build();

        BashSandboxException ex = assertThrows(BashSandboxException.class, () -> sandbox.execute(request));
        assertEquals(BashSandboxFailureReason.HOST_PATH_NOT_ALLOWED, ex.getReason());
    }

    // ========================================================================
    // DockerBashSandbox: resource-limit + jail + network-deny wiring, asserted
    // via the command builder WITHOUT launching Docker (CI fallback).
    // ========================================================================

    @Test
    void dockerBackendWiresAllResourceLimitsByDefault() {
        BashSandboxConfig config = BashSandboxConfig.builder()
                .cpuCores(1.5)
                .memoryMb(512)
                .wallSeconds(45)
                .networkMode(BashSandboxConfig.NetworkMode.DENY)
                .build();
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "echo hi"))
                .workingDirectory(tempDir)
                .environmentVariables(Map.of("FOO", "bar"))
                .config(config)
                .build();

        List<String> cmd = DockerBashSandbox.buildDockerCommand("c1", "alpine:3.19", request, config);
        String joined = String.join(" ", cmd);

        assertTrue(joined.contains("--cpus 1.5"), "cpu quota must be wired: " + joined);
        assertTrue(joined.contains("--memory 512m"), "memory limit must be wired: " + joined);
        assertTrue(joined.contains("--network none"), "network deny must be wired by default: " + joined);
        assertTrue(joined.contains("--rm"), "container must auto-remove: " + joined);
        assertTrue(joined.contains("-v " + tempDir.getAbsolutePath() + ":" + DockerBashSandbox.CONTAINER_WORKDIR),
                "working dir must be mounted at the container workdir: " + joined);
        assertTrue(joined.contains("--workdir " + DockerBashSandbox.CONTAINER_WORKDIR),
                "container workdir must be set: " + joined);
        assertTrue(joined.contains("-e FOO=bar"), "env overlay must be wired: " + joined);
        assertTrue(joined.endsWith("alpine:3.19 sh -c echo hi"),
                "image + command must be the tail: " + joined);
    }

    @Test
    void dockerBackendNetworkAllowOmitsNoneFlag() {
        BashSandboxConfig config = BashSandboxConfig.builder()
                .networkMode(BashSandboxConfig.NetworkMode.ALLOW)
                .build();
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "echo hi"))
                .config(config)
                .build();

        List<String> cmd = DockerBashSandbox.buildDockerCommand("c1", "alpine:3.19", request, config);
        assertFalse(String.join(" ", cmd).contains("--network"),
                "ALLOW mode must not emit a network flag: " + cmd);
    }

    @Test
    void dockerBackendRejectsInvalidEnvKeyBeforeLaunch() {
        assertThrows(BashSandboxException.class,
                () -> DockerBashSandbox.validateEnvironmentVariableKey("--privileged"),
                "an env key that looks like a docker flag must be rejected");
        assertThrows(BashSandboxException.class,
                () -> DockerBashSandbox.validateEnvironmentVariableKey("1ABC"),
                "an env key starting with a digit must be rejected");
        assertThrows(BashSandboxException.class,
                () -> DockerBashSandbox.validateEnvironmentVariableKey("A B"),
                "an env key with whitespace must be rejected");
    }

    @Test
    void dockerBackendRejectsWorkingDirOutsideJailBeforeLaunch() {
        DockerBashSandbox sandbox = new DockerBashSandbox("alpine:3.19",
                BashSandboxConfig.defaults(),
                List.of(tempDir.toPath()));
        File outside = new File(System.getProperty("user.home"), "nop-sandbox-docker-jail-probe");
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c", "echo no"))
                .workingDirectory(outside)
                .config(BashSandboxConfig.defaults())
                .build();

        BashSandboxException ex = assertThrows(BashSandboxException.class, () -> sandbox.execute(request));
        assertEquals(BashSandboxFailureReason.HOST_PATH_NOT_ALLOWED, ex.getReason());
    }

    @Test
    void dockerBackendClassifiesFailuresFailClosed() {
        assertEquals(BashSandboxFailureReason.BACKEND_UNAVAILABLE,
                DockerBashSandbox.classifyFailure(1, "Cannot connect to the Docker daemon"));
        assertEquals(BashSandboxFailureReason.RESOURCE_LIMIT_EXCEEDED,
                DockerBashSandbox.classifyFailure(137, ""));
        assertEquals(BashSandboxFailureReason.TIMEOUT,
                DockerBashSandbox.classifyFailure(124, ""));
        assertEquals(BashSandboxFailureReason.CONTAINER_START_FAILED,
                DockerBashSandbox.classifyFailure(42, "something else"),
                "an unclassified non-zero exit must be conservatively fail-closed, never null");
    }

    // ========================================================================
    // Real-backend isolation proof (conditional on Docker being available).
    // When Docker is unreachable in CI, the test is skipped and the wiring
    // tests above serve as the CI fallback (per Phase 2 Exit Criteria).
    // ========================================================================

    @Test
    void dockerBackendRealNetworkEgressIsDenied() throws IOException, InterruptedException {
        assumeTrue(isDockerAvailable(), "Docker daemon not available — wiring tests cover the CI fallback");
        assumeTrue(isImageAvailable("alpine:3.19"), "alpine:3.19 image not available");

        DockerBashSandbox sandbox = new DockerBashSandbox("alpine:3.19",
                BashSandboxConfig.builder()
                        .wallSeconds(20)
                        .networkMode(BashSandboxConfig.NetworkMode.DENY)
                        .build(),
                List.of(tempDir.toPath()));
        BashSandboxRequest request = BashSandboxRequest.builder()
                .command(List.of("sh", "-c",
                        "wget -q -T 3 -O- http://example.com >/dev/null 2>&1; echo exit=$?"))
                .workingDirectory(tempDir)
                .config(BashSandboxConfig.builder()
                        .wallSeconds(20)
                        .networkMode(BashSandboxConfig.NetworkMode.DENY)
                        .build())
                .build();

        BashSandboxResult result = sandbox.execute(request);
        assertTrue(result.getStdout().contains("exit=1") || result.getExitCode() != 0,
                "a network request under --network none must fail (real-backend isolation proof). "
                        + "stdout=" + result.getStdout() + " exit=" + result.getExitCode());
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

    private static boolean isDockerAvailable() {
        try {
            Process p = new ProcessBuilder(List.of("docker", "version", "--format", "{{.Server.Version}}"))
                    .redirectErrorStream(true).start();
            byte[] buf = new byte[1024];
            //noinspection StatementWithEmptyBody
            try (var in = p.getInputStream()) {
                while (in.read(buf) != -1) {
                    // drain
                }
            }
            return p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            // docker CLI 缺失或无权启动进程 —— 等价于不可用，让 assumeTrue 跳过
            return false;
        }
    }

    private static boolean isImageAvailable(String image) {
        try {
            Process p = new ProcessBuilder(List.of("docker", "image", "inspect", image))
                    .redirectErrorStream(true).start();
            byte[] buf = new byte[1024];
            //noinspection StatementWithEmptyBody
            try (var in = p.getInputStream()) {
                while (in.read(buf) != -1) {
                    // drain
                }
            }
            return p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            // docker 不可用 —— 让上层 assumeTrue 跳过
            return false;
        }
    }

    // ========================================================================
    // The destructive-command regex is defense-in-depth, NOT the primary
    // control: a command that bypasses the regex still runs through the
    // sandbox seam (isolation backend), never on a raw host ProcessBuilder.
    // ========================================================================

    @Test
    void destructiveRegexIsNotTheOnlyGate() {
        RecordingBackend backend = new RecordingBackend();
        BashExecutor executor = new BashExecutor(backend);

        io.nop.ai.toolkit.model.AiToolCall call =
                io.nop.ai.toolkit.model.AiToolCall.fromNode(XNode.make("bash"));
        call.getNode().makeChild("command").setContentValue("echo not-destructive-but-untrusted");

        executor.executeAsync(call, new MockContext()).toCompletableFuture().join();

        assertEquals(1, backend.count.get(),
                "every command — even one the regex lets through — must still route through the isolation seam");
    }

    private static final class RecordingBackend implements IBashSandbox {
        final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();

        @Override
        public BashSandboxResult execute(BashSandboxRequest request) {
            count.incrementAndGet();
            return new BashSandboxResult(0, "", "", false);
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
