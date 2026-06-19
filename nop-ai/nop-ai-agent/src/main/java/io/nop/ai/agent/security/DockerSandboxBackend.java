package io.nop.ai.agent.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Functional opt-in {@link ISandboxBackend} that isolates command execution
 * inside a Docker container (plan 219 Phase 2, design §7.1 "Docker 容器 |
 * 服务器端 shell/code 执行隔离（推荐）").
 *
 * <p><b>Interaction mode</b> (plan 219 Phase 2 Decision): the backend
 * talks to Docker via the {@code docker} CLI through {@link ProcessBuilder}
 * — NOT via a Docker Java client library. Rationale: (1) zero additional
 * Maven dependency; (2) the Docker CLI is a server-side universal
 * prerequisite; (3) the model is symmetric with {@link NoOpSandboxBackend}'s
 * ProcessBuilder host execution. The backend is therefore a thin
 * command-builder + result-parser around {@code docker run} /
 * {@code docker kill} / {@code docker rm -f}.
 *
 * <p><b>Fail-closed guarantee</b>: this backend NEVER falls back to host
 * execution. Any failure to reach Docker, start the container, or observe
 * a clean exit surfaces as a {@link SandboxException} with the matching
 * {@link SandboxFailureReason}:
 *
 * <table border="1">
 *   <caption>Failure classification (plan 219 Phase 2 Decision)</caption>
 *   <tr><th>Observation</th><th>Reason</th></tr>
 *   <tr><td>{@code Process.start()} throws {@link IOException}, OR
 *           stderr matches {@code "Cannot connect to the Docker daemon"}
 *           / {@code "docker: command not found"}</td>
 *       <td>{@link SandboxFailureReason#DOCKER_UNAVAILABLE}</td></tr>
 *   <tr><td>stderr matches {@code "Unable to find image"} /
 *           {@code "permission denied"} / OCI runtime error</td>
 *       <td>{@link SandboxFailureReason#CONTAINER_START_FAILED}</td></tr>
 *   <tr><td>{@link Process#waitFor(long, TimeUnit)} returns false
 *           (wall budget exhausted)</td>
 *       <td>{@link SandboxFailureReason#TIMEOUT} +
 *           {@code docker kill} + {@code docker rm -f} cleanup</td></tr>
 *   <tr><td>Container exit code = 137 (SIGKILL, typically OOM-killer
 *           under {@code --memory})</td>
 *       <td>{@link SandboxFailureReason#RESOURCE_LIMIT_EXCEEDED}</td></tr>
 *   <tr><td>Container exit code = 124 (the {@code timeout(1)} command's
 *           SIGTERM signal — used when we wrap the command)</td>
 *       <td>{@link SandboxFailureReason#TIMEOUT}</td></tr>
 *   <tr><td>Any other non-zero exit not classified above</td>
 *       <td>{@link SandboxFailureReason#CONTAINER_START_FAILED}
 *           (conservative bucket — never silently swallowed)</td></tr>
 * </table>
 *
 * <p><b>Cleanup</b>: every container is launched with {@code --rm} so a
 * natural exit auto-removes it. On timeout the backend issues
 * {@code docker kill <name>} (the {@code --rm} flag then auto-removes the
 * killed container) followed by a defensive {@code docker rm -f <name>}
 * that ignores any error — so a container always ends up removed whether
 * the kill path or the natural-exit path fired. The container name is a
 * generated UUID so concurrent executions never collide.
 *
 * <p><b>Resource limits mapping</b>:
 * <ul>
 *   <li>{@link SandboxConfig#getCpuSeconds()} → {@code --cpus=<n>}</li>
 *   <li>{@link SandboxConfig#getMemoryMb()} → {@code --memory=<n>m}</li>
 *   <li>{@link SandboxConfig#getWallSeconds()} → enforced via
 *       {@link Process#waitFor} + {@code docker kill} on timeout
 *       (Docker itself does not have a wall-time flag for
 *       {@code docker run}; {@code --stop-timeout} is a daemon setting,
 *       not a per-run flag).</li>
 *   <li>{@link SandboxConfig#getNetworkMode()} == DENY →
 *       {@code --network none}; ALLOW → no network flag (container uses
 *       Docker's default bridge network).</li>
 *   <li>{@link SandboxConfig#getMaxOutputBytes()} → enforced by the
 *       stdout-drain loop on the Java side (truncates rather than
 *       erroring).</li>
 * </ul>
 *
 * <p><b>Working directory mapping</b>: the request's
 * {@link SandboxRequest#getWorkingDirectory()} is mounted read-write at
 * {@code /workspace} inside the container ({@code -v <host>:<container>}),
 * and the container's working directory is set to {@code /workspace}
 * ({@code --workdir /workspace}). When the request has no working
 * directory, no {@code -v}/{@code --workdir} flags are emitted (the
 * container's image-default workdir applies).
 *
 * <p><b>Thread safety</b>: stateless after construction — safe for
 * concurrent use across sessions. Each {@link #execute} call owns its own
 * container name, Process, and reader thread.
 */
public final class DockerSandboxBackend implements ISandboxBackend {

    /** Canonical workdir mount point inside the container. */
    public static final String CONTAINER_WORKDIR = "/workspace";

    private final String dockerImage;
    private final SandboxConfig defaultConfig;
    private final List<Path> allowedBaseDirs;

    /**
     * Build a backend that runs commands in the named Docker image using
     * the supplied default {@link SandboxConfig}. Per-call
     * {@link SandboxRequest#getConfig()} overrides the default.
     *
     * @param dockerImage   the Docker image reference (e.g.
     *                      {@code "alpine:3.19"} or
     *                      {@code "ghcr.io/org/runtime:v1.2"}); must be
     *                      non-empty and already pullable by the daemon
     *                      (image lifecycle management is out of scope —
     *                      see plan 219 Non-Goals)
     * @param defaultConfig fallback config used when a request does not
     *                      supply its own; may be {@code null} in which
     *                      case {@link SandboxConfig#defaults()} is used
     */
    public DockerSandboxBackend(String dockerImage, SandboxConfig defaultConfig) {
        this(dockerImage, defaultConfig, defaultAllowedBaseDirs());
    }

    /** Convenience: {@code new DockerSandboxBackend(image, SandboxConfig.defaults())}. */
    public DockerSandboxBackend(String dockerImage) {
        this(dockerImage, SandboxConfig.defaults(), defaultAllowedBaseDirs());
    }

    /**
     * Build a backend with an explicit working-directory host-path
     * whitelist (plan 270 finding 13-7). Before a working directory is
     * mounted into the container, {@link #validateHostPath} checks that the
     * path contains no {@code ..} traversal, resolves to a real existing
     * path, and is equal to or nested under at least one
     * {@code allowedBaseDirs} entry (each resolved to its real path).
     *
     * <p>A request with no working directory never triggers validation
     * (no mount is emitted). Pass an empty list to deny every host mount.
     *
     * @param dockerImage     the Docker image reference; non-empty
     * @param defaultConfig   fallback config; {@code null} →
     *                        {@link SandboxConfig#defaults()}
     * @param allowedBaseDirs the allowed real base directories for mounts;
     *                        never {@code null} (use an empty list to deny
     *                        all mounts)
     */
    public DockerSandboxBackend(String dockerImage, SandboxConfig defaultConfig,
                                List<Path> allowedBaseDirs) {
        this.dockerImage = Objects.requireNonNull(dockerImage,
                "dockerImage must not be null");
        if (dockerImage.isEmpty()) {
            throw new IllegalArgumentException("dockerImage must not be empty");
        }
        this.defaultConfig = defaultConfig != null ? defaultConfig : SandboxConfig.defaults();
        this.allowedBaseDirs = List.copyOf(Objects.requireNonNull(allowedBaseDirs,
                "allowedBaseDirs must not be null (use empty list to deny all mounts)"));
    }

    /**
     * Default whitelist: the JVM's temporary-file directory tree. The
     * integration tests mount a JUnit {@code @TempDir} which lives under
     * {@code java.io.tmpdir} on every platform. Resolved to its real path
     * so a {@code tmpdir} that is itself a symlink is normalised once at
     * construction time.
     */
    private static List<Path> defaultAllowedBaseDirs() {
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp == null || tmp.isEmpty()) {
            return List.of();
        }
        try {
            return List.of(Paths.get(tmp).toRealPath());
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The Docker image this backend runs commands in. */
    public String getDockerImage() {
        return dockerImage;
    }

    /** The fallback config used when a request does not supply its own. */
    public SandboxConfig getDefaultConfig() {
        return defaultConfig;
    }

    /** The immutable working-directory host-path whitelist (plan 270 finding 13-7). */
    public List<Path> getAllowedBaseDirs() {
        return allowedBaseDirs;
    }

    @Override
    public SandboxResult execute(SandboxRequest request) {
        SandboxConfig config = request.getConfig() != null
                ? request.getConfig()
                : defaultConfig;
        // Plan 270 (finding 13-7): validate the working-directory host path
        // BEFORE building the docker command so an unverified path can never
        // reach `docker run -v`. Fail-closed: a violation raises a
        // SandboxException instead of mounting.
        validateHostPath(request.getWorkingDirectory(), allowedBaseDirs);
        String containerName = "nop-sandbox-" + UUID.randomUUID();
        List<String> dockerCmd = buildDockerCommand(
                containerName, dockerImage, request, config);

        ProcessBuilder pb = new ProcessBuilder(dockerCmd).redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            // docker binary not on PATH, or exec() failed entirely.
            throw new SandboxException(SandboxFailureReason.DOCKER_UNAVAILABLE,
                    "DockerSandboxBackend: failed to launch 'docker' CLI for command "
                            + request.getCommand() + ": " + e.getMessage(),
                    e);
        }

        long startNanos = System.nanoTime();
        int maxBytes = config.getMaxOutputBytes();
        AtomicReference<StringBuilder> capturedRef = new AtomicReference<>();
        Thread reader = new Thread(
                () -> drainStream(process.getInputStream(), maxBytes, capturedRef),
                "nop-docker-sandbox-reader");
        reader.setDaemon(true);
        reader.start();

        boolean timedOut;
        try {
            timedOut = !process.waitFor(config.getWallSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timedOut = true;
        }

        if (timedOut) {
            killContainer(containerName);
            awaitReader(reader, config.getWallSeconds());
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            throw new SandboxException(SandboxFailureReason.TIMEOUT,
                    "DockerSandboxBackend: wall-time budget (" + config.getWallSeconds()
                            + "s) exceeded for command " + request.getCommand()
                            + " (container " + containerName + " killed)");
        }

        awaitReader(reader, config.getWallSeconds());
        int exitCode = exitCodeOrNegative(process);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        String stdout = capturedToString(capturedRef);

        // Failure classification (plan 219 Phase 2 Decision).
        SandboxFailureReason reason = classifyFailure(exitCode, stdout);
        if (reason != null) {
            throw new SandboxException(reason,
                    "DockerSandboxBackend: container " + containerName
                            + " exited with code " + exitCode
                            + " (reason=" + reason + ")"
                            + (stdout.isEmpty() ? "" : "; output: " + truncateForMessage(stdout)));
        }

        return new SandboxResult(exitCode, stdout, "", elapsedMs, false);
    }

    // ========================================================================
    // Command construction — extracted so tests can verify the Docker flag
    // mapping without launching Docker.
    // ========================================================================

    /**
     * Validate a working-directory host path before mounting it into the
     * container (plan 270 finding 13-7). Fail-closed: any violation raises a
     * {@link SandboxException} with reason
     * {@link SandboxFailureReason#HOST_PATH_NOT_ALLOWED} so the path never
     * reaches {@code docker run -v}.
     *
     * <p>Rules (all must hold):
     * <ol>
     *   <li>{@code null} working directory → no mount requested → valid (no-op).</li>
     *   <li>The path string must not contain any {@code ..} name component
     *       (path-traversal defense).</li>
     *   <li>The path must resolve to a real existing path via
     *       {@link Path#toRealPath()} (symlinks followed) — a dangling link
     *       or non-existent path is rejected.</li>
     *   <li>The resolved real path must be equal to, or nested under, at
     *       least one entry in {@code allowedBaseDirs}.</li>
     * </ol>
     *
     * <p>Visible for testing so focused tests can exercise each rule without
     * a Docker daemon.
     *
     * @param workingDirectory the host working directory to mount, or
     *                         {@code null} when no mount is requested
     * @param allowedBaseDirs  the allowed real base directories; never null
     */
    static void validateHostPath(java.io.File workingDirectory, List<Path> allowedBaseDirs) {
        if (workingDirectory == null) {
            return;
        }
        String pathStr = workingDirectory.getPath();
        // Rule 2: reject any ".." name component.
        for (String part : pathStr.replace("\\", "/").split("/")) {
            if ("..".equals(part)) {
                throw new SandboxException(SandboxFailureReason.HOST_PATH_NOT_ALLOWED,
                        "DockerSandboxBackend: hostPath contains '..' traversal component: " + pathStr);
            }
        }
        // Rule 3: resolve the real existing path (symlinks followed).
        Path real;
        try {
            real = workingDirectory.toPath().toRealPath();
        } catch (IOException e) {
            throw new SandboxException(SandboxFailureReason.HOST_PATH_NOT_ALLOWED,
                    "DockerSandboxBackend: hostPath does not resolve to a real path: " + pathStr, e);
        }
        // Rule 4: must be under (or equal to) an allowed base dir.
        if (allowedBaseDirs == null || allowedBaseDirs.isEmpty()) {
            throw new SandboxException(SandboxFailureReason.HOST_PATH_NOT_ALLOWED,
                    "DockerSandboxBackend: no allowedBaseDirs configured, hostPath mount denied: " + real);
        }
        for (Path base : allowedBaseDirs) {
            Path normalizedBase = base;
            try {
                normalizedBase = base.toRealPath();
            } catch (IOException ignored) {
                // an allowedBaseDir that cannot be resolved is treated as its
                // lexical form; toRealPath below already normalises `real`.
            }
            if (real.equals(normalizedBase) || real.startsWith(normalizedBase)) {
                return;
            }
        }
        throw new SandboxException(SandboxFailureReason.HOST_PATH_NOT_ALLOWED,
                "DockerSandboxBackend: hostPath outside allowedBaseDirs: " + real);
    }

    /**
     * Build the {@code docker run} argv list for the given request. Visible
     * for testing (the tests assert the exact flag mapping).
     *
     * <p>Flag order is stable: {@code run --rm --name ... --cpus ...
     * --memory ... [--network none] [-v ...] [--workdir ...] [-e ...]
     * <image> <cmd...>}.
     */
    static List<String> buildDockerCommand(String containerName, String image,
                                           SandboxRequest request, SandboxConfig config) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        // --rm: container auto-removes on exit (natural or SIGKILL).
        cmd.add("--rm");
        cmd.add("--name");
        cmd.add(containerName);
        // Resource limits.
        cmd.add("--cpus");
        cmd.add(Integer.toString(config.getCpuSeconds()));
        cmd.add("--memory");
        cmd.add(config.getMemoryMb() + "m");
        if (config.getNetworkMode() == SandboxConfig.NetworkMode.DENY) {
            cmd.add("--network");
            cmd.add("none");
        }
        // Working directory mapping (read-write mount + workdir).
        if (request.getWorkingDirectory() != null) {
            String hostPath = request.getWorkingDirectory().getAbsolutePath();
            cmd.add("-v");
            cmd.add(hostPath + ":" + CONTAINER_WORKDIR);
            cmd.add("--workdir");
            cmd.add(CONTAINER_WORKDIR);
        }
        // Environment overlay.
        for (Map.Entry<String, String> e : request.getEnvironmentVariables().entrySet()) {
            cmd.add("-e");
            cmd.add(e.getKey() + "=" + e.getValue());
        }
        // Image + wrapped command.
        cmd.add(image);
        cmd.addAll(request.getCommand());
        return cmd;
    }

    /**
     * Map an observed (exitCode, stderr) pair to a
     * {@link SandboxFailureReason}, or return {@code null} if the exit
     * code is 0 (clean success). Visible for testing.
     */
    static SandboxFailureReason classifyFailure(int exitCode, String captured) {
        if (exitCode == 0) {
            return null;
        }
        // Docker daemon unreachable / binary missing — emitted to stderr
        // before the container could ever start.
        if (containsAny(captured,
                "Cannot connect to the Docker daemon",
                "docker: command not found",
                "Is the docker daemon running")) {
            return SandboxFailureReason.DOCKER_UNAVAILABLE;
        }
        // Image missing / OCI runtime / permission — the container failed
        // to start (nothing actually ran inside).
        if (containsAny(captured,
                "Unable to find image",
                "permission denied",
                "OCI runtime failed",
                "no such image")) {
            return SandboxFailureReason.CONTAINER_START_FAILED;
        }
        // 137 = 128 + SIGKILL(9): typically the OOM-killer triggered by
        // the --memory ceiling.
        if (exitCode == 137) {
            return SandboxFailureReason.RESOURCE_LIMIT_EXCEEDED;
        }
        // 124 = the timeout(1) command's exit code when it sends SIGTERM.
        // (Reserved for the case where a successor wraps the command in
        // `timeout`; the backend itself does not wrap.)
        if (exitCode == 124) {
            return SandboxFailureReason.TIMEOUT;
        }
        // Anything else with a non-zero exit is conservatively bucketed
        // as CONTAINER_START_FAILED — never silently swallowed.
        return SandboxFailureReason.CONTAINER_START_FAILED;
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String truncateForMessage(String s) {
        return s.length() <= 256 ? s : s.substring(0, 256) + "...(truncated)";
    }

    // ========================================================================
    // Container cleanup
    // ========================================================================

    /**
     * Kill and force-remove a container by name. Best-effort: errors are
     * swallowed because (1) {@code --rm} already auto-removes the
     * container on kill, and (2) a cleanup failure must not mask the
     * original timeout exception.
     */
    private static void killContainer(String containerName) {
        try {
            runShortCommand("docker", "kill", containerName);
        } catch (Exception ignored) {
            // best-effort — --rm should already clean up on kill
        }
        try {
            runShortCommand("docker", "rm", "-f", containerName);
        } catch (Exception ignored) {
            // best-effort fallback — container may already be gone
        }
    }

    private static void runShortCommand(String... argv) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(Arrays.asList(argv)).redirectErrorStream(true).start();
        // Drain so the child does not block on a full pipe.
        byte[] buf = new byte[1024];
        try (InputStream in = p.getInputStream()) {
            //noinspection StatementWithEmptyBody
            while (in.read(buf) != -1) {
                // discard
            }
        }
        p.waitFor(5, TimeUnit.SECONDS);
    }

    // ========================================================================
    // Stream draining + helpers — shared shape with NoOpSandboxBackend but
    // kept independent (no shared base class to avoid coupling the two
    // backends).
    // ========================================================================

    private static void drainStream(InputStream in, int maxBytes, AtomicReference<StringBuilder> capturedRef) {
        StringBuilder captured = new StringBuilder(Math.min(maxBytes, 8192));
        byte[] buf = new byte[4096];
        boolean truncated = false;
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                if (truncated) {
                    continue;
                }
                int remaining = maxBytes - captured.length();
                if (remaining <= 0) {
                    truncated = true;
                    continue;
                }
                int take = Math.min(n, remaining);
                captured.append(new String(buf, 0, take, StandardCharsets.UTF_8));
                if (take < n) {
                    truncated = true;
                }
            }
        } catch (IOException ignored) {
            // best-effort — keep the partial capture
        } finally {
            capturedRef.set(captured);
        }
    }

    private static String capturedToString(AtomicReference<StringBuilder> ref) {
        StringBuilder sb = ref.get();
        return sb != null ? sb.toString() : "";
    }

    private static void awaitReader(Thread reader, int wallSeconds) {
        long graceMillis = Math.max(500L, Math.min(2_000L, wallSeconds * 500L));
        try {
            reader.join(graceMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (reader.isAlive()) {
            reader.interrupt();
        }
    }

    private static int exitCodeOrNegative(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException stillAlive) {
            return -1;
        }
    }
}
