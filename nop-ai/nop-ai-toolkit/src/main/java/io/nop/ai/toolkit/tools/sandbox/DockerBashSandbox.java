package io.nop.ai.toolkit.tools.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * The selected isolation backend (plan 335 DR-3a): isolates command execution inside a Docker
 * container, reusing the nop-ai-agent {@code DockerSandboxBackend} pattern ({@code allowedBaseDirs}
 * whitelist + resource limits + network egress deny). Talks to Docker via the {@code docker} CLI
 * through {@link ProcessBuilder} — no extra Maven dependency.
 *
 * <p><b>Fail-closed guarantee</b>: this backend NEVER falls back to host execution. Any failure to
 * reach Docker, start the container, or observe a clean exit surfaces as a {@link BashSandboxException}
 * with the matching {@link BashSandboxFailureReason}. A timeout issues {@code docker kill} +
 * {@code docker rm -f} cleanup.
 *
 * <p><b>Resource limits mapping</b>:
 * <ul>
 *   <li>{@code cpuCores} → {@code --cpus=<n>} (fractional core quota).</li>
 *   <li>{@code memoryMb} → {@code --memory=<n>m}.</li>
 *   <li>{@code wallSeconds} → enforced via {@code Process#waitFor} + {@code docker kill} on timeout.</li>
 *   <li>{@code networkMode == DENY} → {@code --network none} (the default).</li>
 *   <li>{@code maxOutputBytes} → enforced by the stdout-drain loop (truncates rather than erroring).</li>
 *   <li>{@code allowedBaseDirs} → working-directory host-path whitelist (validated before launch).</li>
 * </ul>
 *
 * <p>Stateless after construction — safe for concurrent use.
 */
public final class DockerBashSandbox implements IBashSandbox {

    public static final String CONTAINER_WORKDIR = "/workspace";

    static final Pattern ENV_KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final String dockerImage;
    private final BashSandboxConfig defaultConfig;
    private final List<Path> allowedBaseDirs;

    public DockerBashSandbox(String dockerImage) {
        this(dockerImage, BashSandboxConfig.defaults(), List.of());
    }

    public DockerBashSandbox(String dockerImage, BashSandboxConfig defaultConfig) {
        this(dockerImage, defaultConfig, List.of());
    }

    public DockerBashSandbox(String dockerImage, BashSandboxConfig defaultConfig, List<Path> allowedBaseDirs) {
        if (dockerImage == null || dockerImage.isEmpty()) {
            throw new IllegalArgumentException("dockerImage must not be null or empty");
        }
        this.dockerImage = dockerImage;
        this.defaultConfig = defaultConfig != null ? defaultConfig : BashSandboxConfig.defaults();
        this.allowedBaseDirs = allowedBaseDirs != null ? List.copyOf(allowedBaseDirs) : List.of();
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public BashSandboxConfig getDefaultConfig() {
        return defaultConfig;
    }

    public List<Path> getAllowedBaseDirs() {
        return allowedBaseDirs;
    }

    @Override
    public BashSandboxResult execute(BashSandboxRequest request) {
        BashSandboxConfig config = request.getConfig() != null ? request.getConfig() : defaultConfig;
        BashSandboxPaths.validateWorkingDirectory(request.getWorkingDirectory(), allowedBaseDirs);
        String containerName = "nop-bash-sandbox-" + UUID.randomUUID();
        List<String> dockerCmd = buildDockerCommand(containerName, dockerImage, request, config);

        ProcessBuilder pb = new ProcessBuilder(dockerCmd).redirectErrorStream(true);
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new BashSandboxException(BashSandboxFailureReason.BACKEND_UNAVAILABLE,
                    "DockerBashSandbox: failed to launch 'docker' CLI for command "
                            + request.getCommand() + ": " + e.getMessage(),
                    e);
        }

        int maxBytes = config.getMaxOutputBytes();
        AtomicReference<StringBuilder> capturedRef = new AtomicReference<>();
        Thread reader = new Thread(() -> drainStream(process.getInputStream(), maxBytes, capturedRef),
                "nop-bash-sandbox-docker-reader");
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
            return new BashSandboxResult(exitCodeOrNegative(process), capturedToString(capturedRef), "", true);
        }

        awaitReader(reader, config.getWallSeconds());
        int exitCode = exitCodeOrNegative(process);
        String stdout = capturedToString(capturedRef);

        BashSandboxFailureReason reason = classifyFailure(exitCode, stdout);
        if (reason != null) {
            throw new BashSandboxException(reason,
                    "DockerBashSandbox: container " + containerName + " exited with code " + exitCode
                            + " (reason=" + reason + ")"
                            + (stdout.isEmpty() ? "" : "; output: " + truncateForMessage(stdout)));
        }

        return new BashSandboxResult(exitCode, stdout, "", false);
    }

    static List<String> buildDockerCommand(String containerName, String image,
                                           BashSandboxRequest request, BashSandboxConfig config) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--cpus");
        cmd.add(Double.toString(config.getCpuCores()));
        cmd.add("--memory");
        cmd.add(config.getMemoryMb() + "m");
        if (config.getNetworkMode() == BashSandboxConfig.NetworkMode.DENY) {
            cmd.add("--network");
            cmd.add("none");
        }
        if (request.getWorkingDirectory() != null) {
            String hostPath = request.getWorkingDirectory().getAbsolutePath();
            cmd.add("-v");
            cmd.add(hostPath + ":" + CONTAINER_WORKDIR);
            cmd.add("--workdir");
            cmd.add(CONTAINER_WORKDIR);
        }
        for (Map.Entry<String, String> e : request.getEnvironmentVariables().entrySet()) {
            validateEnvironmentVariableKey(e.getKey());
            cmd.add("-e");
            cmd.add(e.getKey() + "=" + e.getValue());
        }
        cmd.add(image);
        cmd.addAll(request.getCommand());
        return cmd;
    }

    static void validateEnvironmentVariableKey(String key) {
        if (key == null || !ENV_KEY_PATTERN.matcher(key).matches()) {
            throw new BashSandboxException(BashSandboxFailureReason.INVALID_ENVIRONMENT_VARIABLE,
                    "DockerBashSandbox: invalid environment variable name rejected: " + key);
        }
    }

    static BashSandboxFailureReason classifyFailure(int exitCode, String captured) {
        if (exitCode == 0) {
            return null;
        }
        if (containsAny(captured,
                "Cannot connect to the Docker daemon",
                "docker: command not found",
                "Is the docker daemon running")) {
            return BashSandboxFailureReason.BACKEND_UNAVAILABLE;
        }
        if (containsAny(captured,
                "Unable to find image",
                "permission denied",
                "OCI runtime failed",
                "no such image")) {
            return BashSandboxFailureReason.CONTAINER_START_FAILED;
        }
        if (exitCode == 137) {
            return BashSandboxFailureReason.RESOURCE_LIMIT_EXCEEDED;
        }
        if (exitCode == 124) {
            return BashSandboxFailureReason.TIMEOUT;
        }
        return BashSandboxFailureReason.CONTAINER_START_FAILED;
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
        byte[] buf = new byte[1024];
        try (InputStream in = p.getInputStream()) {
            while (in.read(buf) != -1) {
                // discard
            }
        }
        p.waitFor(5, TimeUnit.SECONDS);
    }

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
            // keep the partial capture
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
