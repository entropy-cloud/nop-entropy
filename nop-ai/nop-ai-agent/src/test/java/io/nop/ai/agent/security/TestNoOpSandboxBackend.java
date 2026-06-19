package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 219 Phase 1 focused unit tests for {@link NoOpSandboxBackend}.
 * Covers normal execution, wall-time timeout (with process-tree kill),
 * output truncation, environment-variable propagation, working-directory
 * honouring, and the empty-command fail-fast path. Together with the
 * smoke test in {@link TestSandboxWiring} this satisfies the Phase 1
 * Exit Criteria "TestNoOpSandboxBackend at least 6 tests covering
 * normal execution/timeout/truncation/environment/working-directory/empty
 * command + smoke execution test verifying execute() end-to-end callable".
 */
public class TestNoOpSandboxBackend {

    private final NoOpSandboxBackend backend = new NoOpSandboxBackend();

    // ========================================================================
    // 1. Normal execution: exit code + stdout captured
    // ========================================================================

    @Test
    void executesCommandAndCapturesExitCodeAndStdout() {
        // Use sh -c so the test is portable across macOS/Linux. On Windows
        // the test JVM would skip these because sh is not on PATH; the
        // project's CI is macOS/Linux so this is acceptable.
        SandboxRequest req = SandboxRequest.of(
                List.of("sh", "-c", "echo hello-sandbox; exit 0"),
                SandboxConfig.defaults());

        SandboxResult result = backend.execute(req);

        assertEquals(0, result.getExitCode(), "exit code must be 0");
        assertTrue(result.getStdout().contains("hello-sandbox"),
                "stdout must contain the echoed payload; got: " + result.getStdout());
        assertFalse(result.isTimedOut(), "must not be flagged as timed-out");
        assertTrue(result.getExecutionTimeMs() >= 0, "executionTimeMs must be non-negative");
    }

    @Test
    void capturesNonZeroExitCode() {
        SandboxRequest req = SandboxRequest.of(
                List.of("sh", "-c", "echo to-stderr 1>&2; exit 7"),
                SandboxConfig.defaults());

        SandboxResult result = backend.execute(req);

        assertEquals(7, result.getExitCode(), "exit code must be propagated as-is");
        // stderr was merged into stdout via redirectErrorStream(true)
        assertTrue(result.getStdout().contains("to-stderr"),
                "merged stream must contain the stderr payload; got: " + result.getStdout());
    }

    // ========================================================================
    // 2. Wall-time timeout: timedOut=true + process tree destroyed
    // ========================================================================

    @Test
    void wallTimeTimeoutKillsRunawayProcess() {
        // sleep 30 will exceed a 1-second wall budget; the backend must
        // return timedOut=true AND not block for the full 30 seconds.
        SandboxConfig oneSecond = SandboxConfig.builder()
                .wallSeconds(1)
                .cpuCores(1.0)
                .memoryMb(64)
                .build();
        SandboxRequest req = SandboxRequest.of(
                List.of("sh", "-c", "sleep 30; echo should-never-print"),
                oneSecond);

        long start = System.currentTimeMillis();
        SandboxResult result = backend.execute(req);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result.isTimedOut(),
                "must be flagged as timed-out when wallSeconds is exceeded");
        // Must return well under the sleep duration — proving the kill
        // worked rather than us waiting for the natural completion.
        assertTrue(elapsed < 5_000L,
                "wall-time timeout must return within ~1s plus cleanup overhead; "
                        + "actual elapsed=" + elapsed + "ms");
        // Exit code after kill: either -1 (not yet reaped) or 137 (SIGKILL
        // reaped — the canonical "killed by SIGKILL" exit). Both prove
        // the kill worked; the value depends on OS reaping timing.
        int rc = result.getExitCode();
        assertTrue(rc == -1 || rc == 137,
                "exit code after killTree must be -1 (not reaped) or 137 (SIGKILL reaped); "
                        + "got " + rc);
    }

    // ========================================================================
    // 3. Output truncation: maxOutputBytes is enforced
    // ========================================================================

    @Test
    void outputIsTruncatedToMaxBytes() {
        // 4-byte payload, 1-byte cap — truncation must kick in.
        SandboxConfig tiny = SandboxConfig.builder()
                .maxOutputBytes(1)
                .build();
        SandboxRequest req = SandboxRequest.of(
                List.of("sh", "-c", "echo AB"),
                tiny);

        SandboxResult result = backend.execute(req);

        // echo AB prints "AB\n" (3 bytes). With a 1-byte cap the captured
        // payload must be at most 1 byte (the trailing drain consumes the
        // rest so the child does not block on a full pipe).
        assertTrue(result.getStdout().length() <= 1,
                "captured stdout must be truncated to <= maxOutputBytes; got length="
                        + result.getStdout().length());
        assertTrue(result.getStdout().length() >= 0,
                "captured stdout must be a valid (possibly empty) string");
    }

    @Test
    void largeOutputIsTruncatedWithoutDeadlock() {
        // Generate ~64KB then exceed a 512-byte cap. The drain-after-cap
        // loop must prevent the child from blocking on a full pipe.
        SandboxConfig small = SandboxConfig.builder()
                .maxOutputBytes(512)
                .wallSeconds(10)
                .build();
        SandboxRequest req = SandboxRequest.of(
                List.of("sh", "-c", "yes hello-large-output | head -c 65536"),
                small);

        SandboxResult result = backend.execute(req);

        assertTrue(result.getStdout().length() <= 512,
                "captured stdout must respect the 512-byte cap; got length="
                        + result.getStdout().length());
        assertEquals(0, result.getExitCode(),
                "command must complete naturally (exit 0) after truncation");
    }

    // ========================================================================
    // 4. Environment variable propagation
    // ========================================================================

    @Test
    void environmentVariablesArePropagatedToChild() {
        SandboxRequest req = SandboxRequest.builder()
                .command(List.of("sh", "-c", "echo $SANDBOX_TEST_VAR"))
                .environmentVariables(Map.of("SANDBOX_TEST_VAR", "propagated-value"))
                .config(SandboxConfig.defaults())
                .build();

        SandboxResult result = backend.execute(req);

        assertEquals(0, result.getExitCode());
        assertTrue(result.getStdout().contains("propagated-value"),
                "child process must observe the env overlay; got: " + result.getStdout());
    }

    // ========================================================================
    // 5. Working directory honoured
    // ========================================================================

    @Test
    void workingDirectoryIsHonoured(@TempDir Path tempDir) throws Exception {
        // Create a marker file in tempDir; the child pwd check must land
        // there and observe the marker.
        Path marker = tempDir.resolve("sandbox-marker.txt");
        Files.writeString(marker, "cwd-marker");

        SandboxRequest req = SandboxRequest.builder()
                .command(List.of("sh", "-c", "pwd; test -f sandbox-marker.txt && echo marker-found"))
                .workingDirectory(tempDir.toFile())
                .config(SandboxConfig.defaults())
                .build();

        SandboxResult result = backend.execute(req);

        assertEquals(0, result.getExitCode(),
                "exit code must be 0; stderr/stdout=" + result.getStdout());
        assertTrue(result.getStdout().contains("marker-found"),
                "child must have observed the marker file in the working directory; "
                        + "got: " + result.getStdout());
        // pwd prints the absolute path of tempDir
        String pwdLine = result.getStdout().lines().findFirst().orElse("");
        assertTrue(pwdLine.contains(tempDir.getFileName().toString()),
                "pwd output must contain the working directory leaf name; "
                        + "expected to contain '" + tempDir.getFileName() + "', got: " + pwdLine);
    }

    // ========================================================================
    // 6. Empty-command fail-fast
    // ========================================================================

    @Test
    void emptyCommandFailsFast() {
        // SandboxRequest itself rejects an empty command list at build time
        // (defence in depth at the data-object layer).
        assertThrows(IllegalArgumentException.class, () ->
                        SandboxRequest.builder()
                                .command(List.of())
                                .config(SandboxConfig.defaults())
                                .build(),
                "SandboxRequest must reject an empty command list at build time");
    }

    @Test
    void nullCommandFailsFast() {
        assertThrows(NullPointerException.class, () ->
                        SandboxRequest.builder()
                                .config(SandboxConfig.defaults())
                                .build(),
                "SandboxRequest must reject a null command list at build time");
    }

    @Test
    void nullConfigFailsFast() {
        assertThrows(NullPointerException.class, () ->
                        SandboxRequest.builder()
                                .command(List.of("echo"))
                                .build(),
                "SandboxRequest must reject a null config at build time");
    }

    // ========================================================================
    // Bonus: config validation
    // ========================================================================

    @Test
    void sandboxConfigRejectsNonPositiveLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> SandboxConfig.builder().wallSeconds(0).build(),
                "wallSeconds=0 must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> SandboxConfig.builder().cpuCores(-1).build(),
                "cpuCores<0 must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> SandboxConfig.builder().cpuCores(0).build(),
                "cpuCores=0 must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> SandboxConfig.builder().cpuCores(Double.NaN).build(),
                "cpuCores=NaN must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> SandboxConfig.builder().memoryMb(0).build(),
                "memoryMb=0 must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> SandboxConfig.builder().maxOutputBytes(0).build(),
                "maxOutputBytes=0 must be rejected");
    }

    @Test
    void defaultsMatchDesignTable() {
        SandboxConfig d = SandboxConfig.defaults();
        assertEquals(1.0, d.getCpuCores(), 1e-9, "design §7.1 default cpuCores=1.0 (one core)");
        assertEquals(1024, d.getMemoryMb(), "design §7.1 default memoryMb=1024");
        assertEquals(60, d.getWallSeconds(), "design §7.1 default wallSeconds=60");
        assertEquals(SandboxConfig.NetworkMode.DENY, d.getNetworkMode(),
                "design §7.1 default network=deny");
        assertEquals(1024 * 1024, d.getMaxOutputBytes(),
                "default maxOutputBytes=1MiB");
    }

    @Test
    void launchFailureRaisesSandboxException() {
        // A binary that does not exist on PATH — ProcessBuilder.start()
        // throws IOException which the backend maps to
        // SandboxException(CONTAINER_START_FAILED). This is the host
        // backend's only fail-closed path (no fallback to anything lower).
        SandboxRequest req = SandboxRequest.of(
                List.of("this-binary-does-not-exist-anywhere-xyz123"),
                SandboxConfig.defaults());

        SandboxException ex = assertThrows(SandboxException.class,
                () -> backend.execute(req),
                "launching a non-existent binary must raise SandboxException");
        assertEquals(SandboxFailureReason.CONTAINER_START_FAILED, ex.getReason(),
                "host launch failure maps to CONTAINER_START_FAILED (conservative bucket)");
        assertNotNull(ex.getMessage());
    }
}
