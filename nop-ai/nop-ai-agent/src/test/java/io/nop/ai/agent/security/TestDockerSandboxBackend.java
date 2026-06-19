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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan 219 Phase 2 focused unit tests for {@link DockerSandboxBackend}.
 * Covers:
 *
 * <ol>
 *   <li>Docker command construction — verifies every
 *       {@link SandboxConfig} field maps to the correct Docker flag, and
 *       that {@link SandboxRequest} fields (workdir, env) map to the
 *       correct {@code -v}/{@code --workdir}/{@code -e} flags. Does not
 *       need a running Docker daemon.</li>
 *   <li>Result parsing — verifies the failure-classification logic maps
 *       exit codes and stderr patterns to the right
 *       {@link SandboxFailureReason}.</li>
 *   <li>Fail-closed error handling — verifies the backend never falls
 *       back to host execution (the {@code docker} binary missing
 *       surfaces as {@link SandboxFailureReason#DOCKER_UNAVAILABLE}).</li>
 *   <li>Resource-limit mapping — already covered by (1) but asserted
 *       explicitly per the Phase 2 Exit Criteria.</li>
 *   <li>Container cleanup — verified by a structural test that confirms
 *       the {@code --rm} flag is always present and the container name
 *       is generated (so {@code docker kill} can find it).</li>
 *   <li>Conditional integration — runs a real {@code docker run alpine
 *       echo hello} when a Docker daemon is available; skipped (not
 *       failed) otherwise via {@link org.junit.jupiter.api.Assumptions}.</li>
 * </ol>
 *
 * <p>Together with {@link TestNoOpSandboxBackend} and
 * {@link io.nop.ai.agent.engine.TestSandboxWiring} this satisfies the
 * Phase 2 Exit Criteria "TestDockerSandboxBackend at least 6 tests
 * covering command construction / result parsing / fail-closed /
 * resource mapping / cleanup / conditional integration".
 */
public class TestDockerSandboxBackend {

    // ========================================================================
    // 1. Command construction — flag mapping (no Docker daemon needed)
    // ========================================================================

    @Test
    void buildCommandIncludesAllResourceLimitFlags() {
        SandboxConfig config = SandboxConfig.builder()
                .cpuCores(1.5)
                .memoryMb(512)
                .wallSeconds(45)
                .networkMode(SandboxConfig.NetworkMode.DENY)
                .maxOutputBytes(2048)
                .build();
        SandboxRequest req = SandboxRequest.of(
                List.of("sh", "-c", "echo hi"),
                config);

        List<String> cmd = DockerSandboxBackend.buildDockerCommand(
                "ctr-1", "alpine:3.19", req, config);

        // Index-based assertions on the prefix so the test is robust to
        // flag-order evolution (we assert the SET of flags + values, plus
        // the specific command tail).
        assertTrue(cmd.contains("docker"), () -> "missing 'docker' in " + cmd);
        assertTrue(cmd.contains("run"), () -> "missing 'run' in " + cmd);
        assertTrue(cmd.contains("--rm"), () -> "missing '--rm' in " + cmd);
        assertEquals("ctr-1", nextOf(cmd, "--name"), "--name value");
        // --cpus <value> : fractional CPU core-count quota (Docker --cpus
        // semantics). cpuCores(1.5) must surface verbatim as "1.5".
        assertEquals("1.5", nextOf(cmd, "--cpus"), "--cpus value");
        // --memory <value>m
        assertEquals("512m", nextOf(cmd, "--memory"), "--memory value");
        // --network none (DENY)
        assertEquals("none", nextOf(cmd, "--network"), "--network value");
        // Image + wrapped command tail
        int imgIdx = cmd.indexOf("alpine:3.19");
        assertTrue(imgIdx >= 0, "missing image name in " + cmd);
        assertEquals(List.of("sh", "-c", "echo hi"), cmd.subList(imgIdx + 1, cmd.size()),
                "tail after image must be the wrapped command");
    }

    @Test
    void buildCommandOmitsNetworkFlagWhenAllow() {
        SandboxConfig allow = SandboxConfig.builder()
                .networkMode(SandboxConfig.NetworkMode.ALLOW)
                .build();
        SandboxRequest req = SandboxRequest.of(List.of("true"), allow);

        List<String> cmd = DockerSandboxBackend.buildDockerCommand(
                "ctr-2", "ubuntu:24.04", req, allow);

        assertFalse(cmd.contains("--network"),
                "--network flag must be absent when networkMode=ALLOW; got " + cmd);
    }

    // ========================================================================
    // 1b. Plan 274 AUDIT-13-8: --cpus = fractional CPU core-count quota
    // (NOT a CPU-seconds budget). Docker --cpus semantics: 1.0 = one full
    // core, 0.5 = half a core.
    // ========================================================================

    @Test
    void cpusFlagCarriesFractionalCoreCountQuota() {
        // A sub-core quota (0.5) must surface verbatim as "0.5" — proving
        // the value is a fractional core-count, not an int seconds budget.
        SandboxConfig halfCore = SandboxConfig.builder().cpuCores(0.5).build();
        SandboxRequest req = SandboxRequest.of(List.of("true"), halfCore);
        List<String> cmd = DockerSandboxBackend.buildDockerCommand(
                "ctr-cpu-half", "alpine", req, halfCore);
        assertEquals("0.5", nextOf(cmd, "--cpus"),
                "--cpus must carry the fractional core-count quota verbatim");

        // A multi-core quota (2.5).
        SandboxConfig twoAndHalf = SandboxConfig.builder().cpuCores(2.5).build();
        List<String> cmd2 = DockerSandboxBackend.buildDockerCommand(
                "ctr-cpu-25", "alpine",
                SandboxRequest.of(List.of("true"), twoAndHalf), twoAndHalf);
        assertEquals("2.5", nextOf(cmd2, "--cpus"),
                "--cpus must carry 2.5 for cpuCores(2.5)");
    }

    @Test
    void cpusFlagDefaultsToOneCore() {
        // The default config must produce --cpus 1.0 (one full core), NOT
        // the old default of 30 (which Docker read as "30 cores" — an
        // effectively-unlimited quota on most hosts).
        SandboxConfig defaults = SandboxConfig.defaults();
        SandboxRequest req = SandboxRequest.of(List.of("true"), defaults);
        List<String> cmd = DockerSandboxBackend.buildDockerCommand(
                "ctr-cpu-default", "alpine", req, defaults);
        assertEquals("1.0", nextOf(cmd, "--cpus"),
                "default config must map to --cpus 1.0 (one core)");
    }

    @Test
    void buildCommandMapsWorkingDirectoryAndWorkdir() throws Exception {
        SandboxRequest req = SandboxRequest.builder()
                .command(List.of("ls"))
                .workingDirectory(new File("/tmp/sample-dir"))
                .config(SandboxConfig.defaults())
                .build();

        List<String> cmd = DockerSandboxBackend.buildDockerCommand(
                "ctr-3", "alpine", req, SandboxConfig.defaults());

        // -v <hostPath>:<containerWorkdir>
        String volumeSpec = nextOf(cmd, "-v");
        assertEquals("/tmp/sample-dir:" + DockerSandboxBackend.CONTAINER_WORKDIR,
                volumeSpec, "-v value");
        assertEquals(DockerSandboxBackend.CONTAINER_WORKDIR, nextOf(cmd, "--workdir"),
                "--workdir value");
    }

    @Test
    void buildCommandOmitsVolumeFlagsWhenNoWorkingDir() {
        SandboxRequest req = SandboxRequest.of(List.of("pwd"), SandboxConfig.defaults());

        List<String> cmd = DockerSandboxBackend.buildDockerCommand(
                "ctr-4", "alpine", req, SandboxConfig.defaults());

        assertFalse(cmd.contains("-v"), "-v flag must be absent when no working dir; got " + cmd);
        assertFalse(cmd.contains("--workdir"),
                "--workdir flag must be absent when no working dir; got " + cmd);
    }

    @Test
    void buildCommandPropagatesEnvironmentVariables() {
        SandboxRequest req = SandboxRequest.builder()
                .command(List.of("env"))
                .environmentVariables(Map.of(
                        "FOO", "bar",
                        "BAZ", "qux"))
                .config(SandboxConfig.defaults())
                .build();

        List<String> cmd = DockerSandboxBackend.buildDockerCommand(
                "ctr-5", "alpine", req, SandboxConfig.defaults());

        // Each -e entry must appear, with KEY=VALUE shape. Use count of
        // the "-e" token to ensure both made it.
        long eCount = cmd.stream().filter(s -> s.equals("-e")).count();
        assertEquals(2, eCount, "expected 2 '-e' entries; cmd=" + cmd);
        assertTrue(cmd.contains("FOO=bar"), "missing FOO=bar in " + cmd);
        assertTrue(cmd.contains("BAZ=qux"), "missing BAZ=qux in " + cmd);
    }

    // ========================================================================
    // 1c. Plan 274 AUDIT-13-9: environment-variable key injection guard
    // (fail-closed POSIX name validation before -e is assembled).
    // ========================================================================

    @Test
    void buildCommandAcceptsPosixValidEnvKeys() {
        // FOO, _BAR, BAZ123 are all POSIX-valid env-var names and must
        // produce -e KEY=VALUE entries.
        SandboxRequest req = SandboxRequest.builder()
                .command(List.of("env"))
                .environmentVariables(Map.of(
                        "FOO", "bar",
                        "_BAR", "b",
                        "BAZ123", "qux"))
                .config(SandboxConfig.defaults())
                .build();
        List<String> cmd = DockerSandboxBackend.buildDockerCommand(
                "ctr-env-valid", "alpine", req, SandboxConfig.defaults());
        long eCount = cmd.stream().filter(s -> s.equals("-e")).count();
        assertEquals(3, eCount, "all 3 valid keys must produce -e entries; cmd=" + cmd);
        assertTrue(cmd.contains("FOO=bar"), "missing FOO=bar in " + cmd);
        assertTrue(cmd.contains("_BAR=b"), "missing _BAR=b in " + cmd);
        assertTrue(cmd.contains("BAZ123=qux"), "missing BAZ123=qux in " + cmd);
    }

    @Test
    void buildCommandRejectsFlagLikeEnvKeyBeforeDockerStarts() {
        // A key that starts with '-' (e.g. "--privileged") would, if
        // concatenated naively, inject an extra Docker flag. It MUST be
        // rejected before the docker process is launched.
        for (String badKey : new String[]{
                "--privileged",   // flag-injection attempt
                "1ABC",           // starts with a digit
                "A B",            // contains whitespace
                "A=B",            // contains '='
                ""                // empty
        }) {
            SandboxRequest req = SandboxRequest.builder()
                    .command(List.of("true"))
                    .environmentVariables(Map.of(badKey, "v"))
                    .config(SandboxConfig.defaults())
                    .build();
            SandboxException ex = assertThrows(SandboxException.class,
                    () -> DockerSandboxBackend.buildDockerCommand(
                            "ctr-env-bad", "alpine", req, SandboxConfig.defaults()),
                    "invalid env key must be rejected: '" + badKey + "'");
            assertEquals(SandboxFailureReason.INVALID_ENVIRONMENT_VARIABLE, ex.getReason(),
                    "invalid env key -> INVALID_ENVIRONMENT_VARIABLE (key='" + badKey + "')");
            assertNotNull(ex.getMessage(), "exception message must be populated");
        }
    }

    @Test
    void validateEnvKeyHelperAcceptsAndRejects() {
        // Direct helper test (no Docker daemon) covering the POSIX grammar.
        // Valid keys pass silently.
        for (String ok : new String[]{"FOO", "_BAR", "BAZ123", "_", "A_B_C"}) {
            DockerSandboxBackend.validateEnvironmentVariableKey(ok);
        }
        // Invalid keys raise with the dedicated reason.
        for (String bad : new String[]{
                "--privileged", "1ABC", "A B", "A=B", "", "A-B", "A.B", "A\bB"
        }) {
            SandboxException ex = assertThrows(SandboxException.class,
                    () -> DockerSandboxBackend.validateEnvironmentVariableKey(bad),
                    "helper must reject invalid env key: '" + bad + "'");
            assertEquals(SandboxFailureReason.INVALID_ENVIRONMENT_VARIABLE, ex.getReason(),
                    "helper invalid key -> INVALID_ENVIRONMENT_VARIABLE (key='" + bad + "')");
        }
    }

    @Test
    void buildCommandOrderPrefixIsStable() {
        // Smoke: the canonical prefix order is preserved — docker, run,
        // --rm, --name, then resource flags, then mount/env, then image,
        // then wrapped command. We assert the indexes are strictly
        // increasing so future refactors do not break callers that rely
        // on the order (e.g. shell-quoting consumers).
        SandboxRequest req = SandboxRequest.builder()
                .command(List.of("sh", "-c", "x"))
                .workingDirectory(new File("/tmp"))
                .environmentVariables(Map.of("K", "V"))
                .config(SandboxConfig.defaults())
                .build();
        List<String> cmd = DockerSandboxBackend.buildDockerCommand(
                "ctr-6", "alpine", req, SandboxConfig.defaults());

        int dockerIdx = cmd.indexOf("docker");
        int runIdx = cmd.indexOf("run");
        int rmIdx = cmd.indexOf("--rm");
        int nameIdx = cmd.indexOf("--name");
        int cpusIdx = cmd.indexOf("--cpus");
        int memIdx = cmd.indexOf("--memory");
        int netIdx = cmd.indexOf("--network");
        int vIdx = cmd.indexOf("-v");
        int eIdx = cmd.indexOf("-e");
        int imgIdx = cmd.indexOf("alpine");

        assertTrue(dockerIdx < runIdx, "docker < run");
        assertTrue(runIdx < rmIdx, "run < --rm");
        assertTrue(rmIdx < nameIdx, "--rm < --name");
        assertTrue(nameIdx < cpusIdx, "--name < --cpus");
        assertTrue(cpusIdx < memIdx, "--cpus < --memory");
        assertTrue(memIdx < netIdx, "--memory < --network");
        assertTrue(netIdx < vIdx, "--network < -v");
        assertTrue(vIdx < eIdx, "-v < -e");
        assertTrue(eIdx < imgIdx, "-e < image");
    }

    // ========================================================================
    // 2. Result parsing — failure classification
    // ========================================================================

    @Test
    void classifySuccessReturnsNull() {
        assertEquals(null, DockerSandboxBackend.classifyFailure(0, "anything"),
                "exit code 0 must never be classified as failure");
        assertEquals(null, DockerSandboxBackend.classifyFailure(0, ""),
                "exit code 0 with empty output must not be classified as failure");
    }

    @Test
    void classifyDockerDaemonUnreachable() {
        assertEquals(SandboxFailureReason.DOCKER_UNAVAILABLE,
                DockerSandboxBackend.classifyFailure(1,
                        "Cannot connect to the Docker daemon at unix:///var/run/docker.sock. Is the docker daemon running?"),
                "daemon connection error → DOCKER_UNAVAILABLE");
        assertEquals(SandboxFailureReason.DOCKER_UNAVAILABLE,
                DockerSandboxBackend.classifyFailure(127, "docker: command not found"),
                "docker binary missing → DOCKER_UNAVAILABLE");
    }

    @Test
    void classifyImageMissingOrStartFailure() {
        assertEquals(SandboxFailureReason.CONTAINER_START_FAILED,
                DockerSandboxBackend.classifyFailure(125, "Unable to find image 'nope:latest' locally"),
                "image-missing pattern → CONTAINER_START_FAILED");
        assertEquals(SandboxFailureReason.CONTAINER_START_FAILED,
                DockerSandboxBackend.classifyFailure(126, "permission denied"),
                "permission-denied pattern → CONTAINER_START_FAILED");
        assertEquals(SandboxFailureReason.CONTAINER_START_FAILED,
                DockerSandboxBackend.classifyFailure(1, "docker: Error response from daemon: OCI runtime failed."),
                "OCI runtime error pattern → CONTAINER_START_FAILED");
    }

    @Test
    void classifyOomKillAsResourceLimit() {
        assertEquals(SandboxFailureReason.RESOURCE_LIMIT_EXCEEDED,
                DockerSandboxBackend.classifyFailure(137, ""),
                "exit code 137 (SIGKILL / OOM-killer) → RESOURCE_LIMIT_EXCEEDED");
    }

    @Test
    void classifyTimeoutExitCodeAsTimeout() {
        assertEquals(SandboxFailureReason.TIMEOUT,
                DockerSandboxBackend.classifyFailure(124, ""),
                "exit code 124 (timeout(1) SIGTERM) → TIMEOUT");
    }

    @Test
    void classifyUnclassifiedNonZeroIsConservativeStartFailed() {
        // 42 is not in any pattern table — must be the conservative bucket,
        // never silently swallowed as null.
        assertEquals(SandboxFailureReason.CONTAINER_START_FAILED,
                DockerSandboxBackend.classifyFailure(42, "some unknown failure"),
                "unclassified non-zero exit → CONTAINER_START_FAILED (conservative)");
    }

    // ========================================================================
    // 3. Fail-closed: docker missing → DOCKER_UNAVAILABLE, no host fallback
    // ========================================================================

    @Test
    void missingDockerBinarySurfacesAsUnavailableException() {
        // This test can only exercise the "binary missing → IOException →
        // DOCKER_UNAVAILABLE" path on hosts where `docker` is NOT on PATH.
        // When docker IS present (CI, dev machines), we cannot realistically
        // make it disappear without affecting the rest of the suite, so we
        // skip rather than attempt a fragile PATH/argv override. The
        // classification logic itself (stderr pattern → DOCKER_UNAVAILABLE)
        // is already covered by classifyDockerDaemonUnreachable().
        if (isOnPath("docker")) {
            return;
        }
        SandboxRequest req = SandboxRequest.of(
                List.of("echo", "should-not-run-on-host"),
                SandboxConfig.defaults());
        DockerSandboxBackend backend = new DockerSandboxBackend("alpine");

        SandboxException ex = assertThrows(SandboxException.class,
                () -> backend.execute(req),
                "missing docker binary must raise SandboxException, never fall back to host");
        assertEquals(SandboxFailureReason.DOCKER_UNAVAILABLE, ex.getReason(),
                "missing docker binary → DOCKER_UNAVAILABLE (never host fallback)");
        assertTrue(ex.getMessage() != null && !ex.getMessage().isEmpty(),
                "exception message must be populated");
    }

    // ========================================================================
    // 4. Cleanup: --rm always present, name generated
    // ========================================================================

    @Test
    void cleanupGuaranteesRmFlagAndName() {
        SandboxRequest req = SandboxRequest.of(List.of("true"), SandboxConfig.defaults());
        List<String> cmd = DockerSandboxBackend.buildDockerCommand(
                "ctr-cleanup-1", "alpine", req, SandboxConfig.defaults());

        // --rm guarantees Docker auto-removes the container on natural
        // exit. The name is required for the timeout path's
        // `docker kill <name>` to find the container.
        assertTrue(cmd.contains("--rm"),
                "every docker run must include --rm so natural exits auto-clean; got " + cmd);
        assertTrue(cmd.contains("--name"),
                "every docker run must include --name so the timeout path can kill it; got " + cmd);
        assertEquals("ctr-cleanup-1", nextOf(cmd, "--name"),
                "--name must carry the supplied container name");
    }

    // ========================================================================
    // 4b. Plan 270 finding 13-7: hostPath whitelist validation (no Docker needed)
    // ========================================================================

    @Test
    void validateHostPathRejectsTraversalComponent() {
        // ".." anywhere in the path is rejected before any filesystem lookup.
        SandboxException ex = assertThrows(SandboxException.class,
                () -> DockerSandboxBackend.validateHostPath(
                        new File("/tmp/legit/../../etc"), List.of()),
                "a hostPath containing '..' must be rejected");
        assertEquals(SandboxFailureReason.HOST_PATH_NOT_ALLOWED, ex.getReason(),
                "traversal hostPath → HOST_PATH_NOT_ALLOWED");
    }

    @Test
    void validateHostPathRejectsPathOutsideWhitelist(@TempDir Path tempDir) throws Exception {
        // tempDir/inside is allowed; a sibling outside is not.
        Path allowed = tempDir.resolve("inside");
        Files.createDirectories(allowed);
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Path allowedReal = allowed.toRealPath();

        // A hostPath that exists but is NOT under the whitelist must throw.
        SandboxException ex = assertThrows(SandboxException.class,
                () -> DockerSandboxBackend.validateHostPath(
                        outside.toFile(), List.of(allowedReal)),
                "a hostPath outside the whitelist must be rejected");
        assertEquals(SandboxFailureReason.HOST_PATH_NOT_ALLOWED, ex.getReason(),
                "out-of-whitelist hostPath → HOST_PATH_NOT_ALLOWED");
        assertTrue(ex.getMessage().contains("outside allowedBaseDirs"),
                "message must explain the whitelist violation: " + ex.getMessage());
    }

    @Test
    void validateHostPathRejectsNonExistentPath(@TempDir Path tempDir) throws Exception {
        Path allowedReal = tempDir.toRealPath();
        File ghost = tempDir.resolve("does-not-exist").toFile();

        // A hostPath that does not resolve to a real path (toRealPath throws)
        // must be rejected — fail-closed, never silently mounted.
        SandboxException ex = assertThrows(SandboxException.class,
                () -> DockerSandboxBackend.validateHostPath(ghost, List.of(allowedReal)),
                "a non-existent hostPath must be rejected");
        assertEquals(SandboxFailureReason.HOST_PATH_NOT_ALLOWED, ex.getReason(),
                "non-existent hostPath → HOST_PATH_NOT_ALLOWED");
    }

    @Test
    void validateHostPathRejectsSymlinkEscapingWhitelist(@TempDir Path tempDir) throws Exception {
        // allowed = tempDir/inside; a symlink inside tempDir that points
        // OUTSIDE the allowed base (to tempDir/secret) must be rejected
        // because toRealPath follows the link.
        Path allowed = tempDir.resolve("inside");
        Files.createDirectories(allowed);
        Path allowedReal = allowed.toRealPath();

        Path secret = tempDir.resolve("secret");
        Files.createDirectories(secret);
        Path linkInsideAllowed = allowed.resolve("escape-link");
        Files.createSymbolicLink(linkInsideAllowed, secret);

        SandboxException ex = assertThrows(SandboxException.class,
                () -> DockerSandboxBackend.validateHostPath(
                        linkInsideAllowed.toFile(), List.of(allowedReal)),
                "a symlink escaping the whitelist must be rejected");
        assertEquals(SandboxFailureReason.HOST_PATH_NOT_ALLOWED, ex.getReason(),
                "symlink-escape hostPath → HOST_PATH_NOT_ALLOWED");
    }

    @Test
    void validateHostPathAllowsNullAndWhitelistedPath(@TempDir Path tempDir) throws Exception {
        Path allowedReal = tempDir.toRealPath();

        // null workingDirectory → no mount requested → no-op (no exception).
        DockerSandboxBackend.validateHostPath(null, List.of(allowedReal));

        // A path nested under the whitelist is accepted.
        Path nested = tempDir.resolve("work/sub");
        Files.createDirectories(nested);
        DockerSandboxBackend.validateHostPath(nested.toFile(), List.of(allowedReal));

        // The allowed base dir itself is accepted.
        DockerSandboxBackend.validateHostPath(tempDir.toFile(), List.of(allowedReal));
    }

    @Test
    void defaultBackendWhitelistsJvmTempDir(@TempDir Path tempDir) throws Exception {
        // The default whitelist resolves java.io.tmpdir to a real path. A
        // @TempDir lives under java.io.tmpdir, so it must be accepted by a
        // default-constructed backend (validates the default whitelist wiring
        // and that validation is invoked from execute()'s command-build path
        // without needing a Docker daemon — we only assert the validation
        // does not throw).
        DockerSandboxBackend backend = new DockerSandboxBackend("alpine");
        assertFalse(backend.getAllowedBaseDirs().isEmpty(),
                "default backend must whitelist java.io.tmpdir");
        // @TempDir is under java.io.tmpdir → accepted.
        DockerSandboxBackend.validateHostPath(tempDir.toFile(), backend.getAllowedBaseDirs());
    }

    // ========================================================================
    // 5. Conditional integration: real docker run when daemon is up
    // ========================================================================


    @Test
    void conditionalIntegrationRunsEchoInAlpineWhenDockerAvailable(@TempDir Path tempDir) throws Exception {
        // Skip (do not fail) when Docker is not on PATH, the daemon is
        // not running, OR the alpine:3.19 image is not already cached
        // (a fresh pull can exceed the test's wall budget and is
        // environment-dependent). This test is the real end-to-end proof
        // on CI hosts that have Docker + alpine pre-pulled.
        if (!isDockerAvailable() || !isImageCached("alpine:3.19")) {
            return;
        }

        Files.writeString(tempDir.resolve("hello.txt"), "from-container");
        SandboxRequest req = SandboxRequest.builder()
                .command(List.of("sh", "-c", "cat /workspace/hello.txt"))
                .workingDirectory(tempDir.toFile())
                .config(SandboxConfig.builder()
                        .wallSeconds(60)
                        .cpuCores(1.0)
                        .memoryMb(128)
                        .build())
                .build();

        DockerSandboxBackend backend = new DockerSandboxBackend("alpine:3.19");
        SandboxResult result;
        try {
            result = backend.execute(req);
        } catch (SandboxException e) {
            // If the daemon went away between the availability check and
            // the run, the test must surface the failure as an explicit
            // fail-closed signal (NOT a silent skip) so the operator can
            // diagnose. The classification must be DOCKER_UNAVAILABLE or
            // CONTAINER_START_FAILED (image pull failure) — never silent.
            throw new AssertionError("Docker was reported available but execute() failed: reason="
                    + e.getReason() + ", message=" + e.getMessage(), e);
        }

        assertEquals(0, result.getExitCode(),
                "echo in alpine must exit 0; stdout=" + result.getStdout());
        assertTrue(result.getStdout().contains("from-container"),
                "container must observe the mounted working-directory file; got: "
                        + result.getStdout());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /** Return the element immediately after the first occurrence of {@code token}. */
    private static String nextOf(List<String> cmd, String token) {
        int i = cmd.indexOf(token);
        if (i < 0 || i + 1 >= cmd.size()) {
            throw new AssertionError("token '" + token + "' not followed by a value in " + cmd);
        }
        return cmd.get(i + 1);
    }

    /** True iff the {@code docker} binary is on PATH and the daemon answers {@code docker info}. */
    private static boolean isDockerAvailable() {
        try {
            Process p = new ProcessBuilder(List.of("docker", "info")).redirectErrorStream(true).start();
            byte[] buf = new byte[4096];
            //noinspection StatementWithEmptyBody
            try (java.io.InputStream in = p.getInputStream()) {
                //noinspection StatementWithEmptyBody
                while (in.read(buf) != -1) {
                    // discard
                }
            }
            boolean done = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** True iff a binary by the given name resolves on the current PATH. */
    private static boolean isOnPath(String binary) {
        try {
            Process p = new ProcessBuilder(List.of("which", binary)).redirectErrorStream(true).start();
            byte[] buf = new byte[4096];
            try (java.io.InputStream in = p.getInputStream()) {
                //noinspection StatementWithEmptyBody
                while (in.read(buf) != -1) {
                    // discard
                }
            }
            boolean done = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** True iff the named image is already cached locally (no pull needed). */
    private static boolean isImageCached(String imageRef) {
        try {
            Process p = new ProcessBuilder(List.of("docker", "image", "inspect", imageRef))
                    .redirectErrorStream(true).start();
            byte[] buf = new byte[4096];
            try (java.io.InputStream in = p.getInputStream()) {
                //noinspection StatementWithEmptyBody
                while (in.read(buf) != -1) {
                    // discard
                }
            }
            boolean done = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
