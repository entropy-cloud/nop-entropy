package io.nop.ai.toolkit.tools.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Explicit opt-in {@link IBashSandbox} that executes the command directly on the host JVM via
 * {@link ProcessBuilder} (plan 335 DR-3a: "host execution only as explicit opt-in configuration").
 *
 * <p>This is <b>not</b> a fallback and <b>not</b> the default — {@code BashExecutor} never
 * constructs it implicitly. An integrator that wants host execution must wire it explicitly
 * (e.g. {@code new BashExecutor(new HostBashSandbox(config))}).
 *
 * <p><b>What IS enforced</b>: {@link BashSandboxConfig#getWallSeconds()} (via
 * {@code Process#waitFor} + process-tree kill on timeout), {@link BashSandboxConfig#getMaxOutputBytes()}
 * (per-stream truncation), and the {@link BashSandboxConfig#getAllowedBaseDirs()} working-directory
 * jail (validated before launch).
 *
 * <p><b>What is NOT enforced on the host</b>: {@code cpuCores}, {@code memoryMb}, and
 * {@code networkMode} require an isolator (cgroup/namespace). The host backend records the
 * configured values but does not honour them — a caller that needs these limits MUST wire
 * {@link DockerBashSandbox}.
 *
 * <p><b>Fail-closed behaviour</b>: the host backend never falls back to anything (there is
 * nothing below it). A launch failure surfaces as {@link BashSandboxException}
 * ({@link BashSandboxFailureReason#CONTAINER_START_FAILED}).
 *
 * <p>Stateless after construction — safe for concurrent use.
 */
public final class HostBashSandbox implements IBashSandbox {

    private final BashSandboxConfig defaultConfig;
    private final List<Path> allowedBaseDirs;

    public HostBashSandbox() {
        this(BashSandboxConfig.defaults(), List.of());
    }

    public HostBashSandbox(BashSandboxConfig defaultConfig) {
        this(defaultConfig, List.of());
    }

    public HostBashSandbox(BashSandboxConfig defaultConfig, List<Path> allowedBaseDirs) {
        this.defaultConfig = defaultConfig != null ? defaultConfig : BashSandboxConfig.defaults();
        this.allowedBaseDirs = allowedBaseDirs != null ? List.copyOf(allowedBaseDirs) : List.of();
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

        ProcessBuilder pb = new ProcessBuilder(request.getCommand());
        if (request.getWorkingDirectory() != null) {
            pb.directory(request.getWorkingDirectory());
        }
        if (!request.getEnvironmentVariables().isEmpty()) {
            pb.environment().putAll(request.getEnvironmentVariables());
        }
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new BashSandboxException(BashSandboxFailureReason.CONTAINER_START_FAILED,
                    "HostBashSandbox: failed to launch host process for command "
                            + request.getCommand() + ": " + e.getMessage(),
                    e);
        }

        int maxBytes = config.getMaxOutputBytes();
        AtomicReference<StringBuilder> capturedRef = new AtomicReference<>();
        Thread reader = new Thread(() -> drainStream(process.getInputStream(), maxBytes, capturedRef),
                "nop-bash-sandbox-reader");
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
            killTree(process);
            awaitReader(reader, config.getWallSeconds());
            return new BashSandboxResult(exitCodeOrNegative(process), capturedToString(capturedRef), "", true);
        }

        awaitReader(reader, config.getWallSeconds());
        int exitCode = exitCodeOrNegative(process);
        return new BashSandboxResult(exitCode, capturedToString(capturedRef), "", false);
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

    private static void killTree(Process process) {
        try {
            process.descendants().forEach(h -> {
                try {
                    h.destroyForcibly();
                } catch (Exception ignored) {
                    // best-effort
                }
            });
        } catch (UnsupportedOperationException | SecurityException ignored) {
            // ProcessHandle API unsupported — root-only kill below
        }
        process.destroyForcibly();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
