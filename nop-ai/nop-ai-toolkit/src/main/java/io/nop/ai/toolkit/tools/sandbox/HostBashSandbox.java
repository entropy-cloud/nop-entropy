package io.nop.ai.toolkit.tools.sandbox;

import io.nop.commons.util.IoHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Explicit opt-in {@link IBashSandbox} that executes the command directly on the host JVM via
 * {@link ProcessBuilder} (plan 335 DR-3a: "host execution only as explicit opt-in configuration").
 *
 * <p>This is <b>not</b> a fallback and <b>not</b> the default — {@code BashExecutor} never
 * constructs it implicitly. An integrator that wants host execution must wire it explicitly
 * (e.g. {@code new BashExecutor(new HostBashSandbox(config))}.
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
 * <p><b>Declared output boundary (CRA 3.2-C6-001, plan 2501 Phase 2)</b>: a command that
 * detaches a background twin ({@code cmd &}) leaves the twin holding the stdout pipe. The
 * reader thread is terminated by closing the pipe (bounded grace + forced EOF), so no thread
 * or fd leaks from the sandbox; the twin itself keeps running detached and whatever it writes
 * after the main process exits is discarded and never attributed to the tool result.
 *
 * <p><b>Fail-closed behaviour</b>: the host backend never falls back to anything (there is
 * nothing below it). A launch failure surfaces as {@link BashSandboxException}
 * ({@link BashSandboxFailureReason#CONTAINER_START_FAILED}).
 *
 * <p>Stateless after construction — safe for concurrent use.
 */
public final class HostBashSandbox implements IBashSandbox {
    /** Plan 2285 (WI4): incremental-progress publish cadence (time-based). */
    private static final long PUBLISH_INTERVAL_NS = 50_000_000L;


    private final BashSandboxConfig defaultConfig;
    private final List<Path> allowedBaseDirs;
    /** Plan 2285 (WI4): callId-addressed in-flight registry for progress/cancel. */
    private final BashExecutionTracker tracker = new BashExecutionTracker();

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
        // C6-001 (MA-02, plan 2501 Phase 2): the child's stdin used to stay a PIPE
        // nobody closes — a `cat`-style command could only be reaped by the wall
        // timeout. Point stdin at the null device so such commands see EOF.
        pb.redirectInput(nullInput());

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
        // C4-001 member (MA-02): unique reader name — thread dumps must be able to
        // attribute a (terminating) reader to its command
        String readerName = "nop-bash-sandbox-reader-"
                + (request.getCallId() != null ? request.getCallId() : UUID.randomUUID());
        Thread reader = new Thread(() -> drainStream(process.getInputStream(), maxBytes, capturedRef),
                readerName);
        reader.setDaemon(true);
        reader.start();

        // Plan 2285 (WI4): register in-flight under the caller's callId so
        // heartbeat progress snapshots and abandon-time cancellation can
        // address this process; unregistered in every exit path.
        String callId = request.getCallId();
        if (callId != null) {
            tracker.register(callId, process, capturedRef, null);
        }
        try {
            return doExecute(config, process, reader, capturedRef);
        } finally {
            if (callId != null) {
                tracker.unregister(callId);
            }
        }
    }

    private BashSandboxResult doExecute(BashSandboxConfig config, Process process, Thread reader,
                                        AtomicReference<StringBuilder> capturedRef) {
        boolean timedOut;
        try {
            timedOut = !process.waitFor(config.getWallSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timedOut = true;
        }

        if (timedOut) {
            killTree(process);
            awaitReader(process, reader, config.getWallSeconds());
            return new BashSandboxResult(exitCodeOrNegative(process), capturedToString(capturedRef), "", true);
        }

        awaitReader(process, reader, config.getWallSeconds());
        int exitCode = exitCodeOrNegative(process);
        return new BashSandboxResult(exitCode, capturedToString(capturedRef), "", false);
    }

    @Override
    public BashProgressSnapshot progressSnapshot(String callId) {
        return tracker.progressSnapshot(callId);
    }

    @Override
    public boolean cancelExecution(String callId) {
        return tracker.cancelExecution(callId);
    }

    private static void drainStream(InputStream in, int maxBytes, AtomicReference<StringBuilder> capturedRef) {
        StringBuilder captured = new StringBuilder(Math.min(maxBytes, 8192));
        byte[] buf = new byte[4096];
        boolean truncated = false;
        // Plan 2285 (WI4): publish an immutable copy periodically so the
        // in-flight progress registry sees incremental output during the run
        // (previously the buffer was only published in the finally block).
        // Time-based rate limit (first chunk publishes immediately); a copy
        // per chunk would be quadratic for max-bytes outputs.
        long lastPublishNs = 0L;
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
                long nowNs = System.nanoTime();
                if (nowNs - lastPublishNs >= PUBLISH_INTERVAL_NS) {
                    lastPublishNs = nowNs;
                    capturedRef.set(new StringBuilder(captured));
                }
            }
        } catch (IOException ignored) {
            // keep the partial capture
        } finally {
            // C6-008 (MA-02 member): publish an immutable snapshot — never the live
            // buffer a (possibly still-draining) reader could keep appending to
            capturedRef.set(new StringBuilder(captured));
            // C6-001: release the pipe fd as soon as this drain ends
            IoHelper.safeClose(in);
        }
    }

    private static String capturedToString(AtomicReference<StringBuilder> ref) {
        StringBuilder sb = ref.get();
        return sb != null ? sb.toString() : "";
    }

    /**
     * C6-001 (MA-02, plan 2501 Phase 2): bounded wait for the reader thread with
     * exit compensation. {@code interrupt()} has no effect on a thread blocked in a
     * native pipe read (a {@code cmd &} twin process keeps the stdout write end
     * open, so no EOF arrives) — closing the pipe forces the blocked read to abort,
     * after which the reader publishes its final snapshot and exits. Residual
     * boundary: the detached twin keeps running and its output is discarded (see
     * the class javadoc).
     */
    private static void awaitReader(Process process, Thread reader, int wallSeconds) {
        long graceMillis = Math.max(500L, Math.min(2_000L, wallSeconds * 500L));
        try {
            reader.join(graceMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (reader.isAlive()) {
            IoHelper.safeClose(process.getInputStream());
            reader.interrupt();
        }
    }

    private static ProcessBuilder.Redirect nullInput() {
        File nul = File.separatorChar == '\\' ? new File("NUL") : new File("/dev/null");
        return ProcessBuilder.Redirect.from(nul);
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
