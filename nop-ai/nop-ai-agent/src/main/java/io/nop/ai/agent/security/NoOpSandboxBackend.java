package io.nop.ai.agent.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shipped default {@link ISandboxBackend} used as the
 * {@code DefaultAgentEngine} default (plan 219 Phase 1, design §7.1
 * "Noop | 无隔离（默认）"). Executes the command directly on the host JVM
 * via {@link ProcessBuilder} — there is <b>no isolation</b>: this is the
 * Layer 1 designable baseline, not a fallback target.
 *
 * <p><b>What IS enforced</b>: the two limits that do not need an isolator
 * — {@link SandboxConfig#getWallSeconds()} (via
 * {@link Process#waitFor(long, java.util.concurrent.TimeUnit)}) and
 * {@link SandboxConfig#getMaxOutputBytes()} (per-stream truncation). On
 * wall-time exhaustion the process tree is forcibly terminated
 * ({@code Process.descendants().forEach(p -> p.destroyForcibly())} +
 * {@code destroyForcibly()} on the root) so a runaway child does not
 * outlive its parent.
 *
 * <p><b>Stream draining</b>: the child's stdout/stderr (merged via
 * {@link ProcessBuilder#redirectErrorStream(boolean)}) is drained on a
 * <i>dedicated reader thread</i> while the calling thread blocks in
 * {@link Process#waitFor(long, TimeUnit)}. This is mandatory: an inline
 * (same-thread) read would deadlock the moment a quiet command (e.g.
 * {@code sleep 30}) fills no pipe — the parent would block on
 * {@code read()}, never reach {@code waitFor}, and the wall-time budget
 * would never fire. The reader thread also enforces the
 * {@link SandboxConfig#getMaxOutputBytes()} cap and discards the overflow
 * so the child cannot block on a full pipe before its parent can kill it.
 *
 * <p><b>What is NOT enforced</b>: {@link SandboxConfig#getCpuCores()},
 * {@link SandboxConfig#getMemoryMb()}, and
 * {@link SandboxConfig#getNetworkMode()} require an isolator (cgroup /
 * namespace). The host backend records the configured values on the
 * passed-in {@link SandboxConfig} but does not honour them — a caller
 * that needs these limits MUST wire a functional backend such as
 * {@code DockerSandboxBackend}.
 *
 * <p><b>Fail-closed behaviour</b>: the host backend never falls back to
 * anything (there is nothing below it). It surfaces failures as:
 * <ul>
 *   <li>{@link SandboxResult} with the captured exit code / stdout /
 *       stderr for a command that completed (zero or non-zero exit);</li>
 *   <li>{@link SandboxResult} with {@code timedOut=true} for a command
 *       that exceeded the wall budget (process tree killed, partial
 *       output captured);</li>
 *   <li>{@link SandboxException}({@link SandboxFailureReason#CONTAINER_START_FAILED})
 *       if {@link ProcessBuilder#start()} itself throws (the host cannot
 *       even launch the argv — e.g. binary not found). The reason bucket
 *       is conservative: a {@code DOCKER_UNAVAILABLE} mapping is not
 *       applicable on the host backend.</li>
 * </ul>
 *
 * <p><b>Thread safety</b>: the backend is stateless — safe for concurrent
 * use across sessions. Each {@link #execute} call owns its own
 * {@link Process} / reader thread / output buffers.
 */
public final class NoOpSandboxBackend implements ISandboxBackend {

    /** Stateless singleton — also reused as the executor Builder null fallback. */
    public static final NoOpSandboxBackend INSTANCE = new NoOpSandboxBackend();

    public NoOpSandboxBackend() {
        // no configuration — the per-call SandboxConfig supplies all limits
    }

    @Override
    public SandboxResult execute(SandboxRequest request) {
        SandboxConfig config = request.getConfig();
        ProcessBuilder pb = new ProcessBuilder(request.getCommand());
        if (request.getWorkingDirectory() != null) {
            pb.directory(request.getWorkingDirectory());
        }
        if (!request.getEnvironmentVariables().isEmpty()) {
            pb.environment().putAll(request.getEnvironmentVariables());
        }
        // Merge stderr into stdout so a single reader thread captures both
        // streams in arrival order — this matches what a user sees in a
        // terminal and keeps the output-truncation logic single-stream.
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new SandboxException(SandboxFailureReason.CONTAINER_START_FAILED,
                    "NoOpSandboxBackend: failed to launch host process for command "
                            + request.getCommand() + ": " + e.getMessage(),
                    e);
        }

        long startNanos = System.nanoTime();

        // Spawn a dedicated reader thread so the calling thread is free to
        // honour the wall-time budget via waitFor(timeout). The reader
        // caps the captured payload at maxOutputBytes and drains the rest
        // so the child does not block on a full pipe before we can kill it.
        int maxBytes = config.getMaxOutputBytes();
        AtomicReference<StringBuilder> capturedRef = new AtomicReference<>();
        Thread reader = new Thread(() -> drainStream(process.getInputStream(), maxBytes, capturedRef),
                "nop-sandbox-stdout-reader");
        reader.setDaemon(true);
        reader.start();

        boolean timedOut;
        try {
            timedOut = !process.waitFor(config.getWallSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Restore interrupt status and treat as timeout — caller asked
            // us to stop, so the process must be cleaned up.
            Thread.currentThread().interrupt();
            timedOut = true;
        }

        if (timedOut) {
            killTree(process);
            // Reader thread will finish on its own once the killed process
            // closes its stdout pipe; join with a short grace period so we
            // capture whatever was buffered before the kill.
            awaitReader(reader, config.getWallSeconds());
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            return new SandboxResult(
                    /* exitCode */ exitCodeOrNegative(process),
                    capturedToString(capturedRef),
                    /* stderr already merged into captured */ "",
                    elapsedMs,
                    /* timedOut */ true);
        }

        // Process exited naturally — wait for the reader to observe EOF so
        // we capture the full payload up to maxBytes.
        awaitReader(reader, config.getWallSeconds());
        int exitCode = exitCodeOrNegative(process);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        return new SandboxResult(exitCode, capturedToString(capturedRef), "", elapsedMs, false);
    }

    /**
     * Read from {@code in} into a buffer held in {@code capturedRef} until
     * EOF, capping the captured payload at {@code maxBytes}. Bytes beyond
     * the cap are drained (read-and-discarded) so the child process cannot
     * block on a full pipe before its parent can terminate it.
     */
    private static void drainStream(InputStream in, int maxBytes, AtomicReference<StringBuilder> capturedRef) {
        StringBuilder captured = new StringBuilder(Math.min(maxBytes, 8192));
        byte[] buf = new byte[4096];
        boolean truncated = false;
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                if (truncated) {
                    // Already over the cap — keep draining so the child
                    // does not block on a full pipe.
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
            // Stream closed unexpectedly — keep the partial capture. This
            // is best-effort: the exit code / kill path below will still
            // produce a coherent SandboxResult.
        } finally {
            capturedRef.set(captured);
        }
    }

    private static String capturedToString(AtomicReference<StringBuilder> ref) {
        StringBuilder sb = ref.get();
        return sb != null ? sb.toString() : "";
    }

    private static void awaitReader(Thread reader, int wallSeconds) {
        // Wait a short grace period for the reader to observe EOF after
        // the process exits / is killed. We do not block indefinitely —
        // a buggy child could keep its stdout pipe open. The reader is a
        // daemon thread, so leaving it alive does not prevent JVM exit.
        long graceMillis = Math.max(500L, Math.min(2_000L, wallSeconds * 500L));
        try {
            reader.join(graceMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (reader.isAlive()) {
            // Best-effort nudge — interrupt the read. InputStream.read may
            // or may not honour interrupt, but this is the best we can do
            // portably.
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

    /**
     * Forcibly terminate the process and its descendants. Best-effort:
     * ProcessHandle.descendants() may not be supported on every platform,
     * in which case we still kill the root and let the OS reap any
     * orphaned children.
     */
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
            // ProcessHandle API unsupported — root-only kill below is the
            // best we can do.
        }
        process.destroyForcibly();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
