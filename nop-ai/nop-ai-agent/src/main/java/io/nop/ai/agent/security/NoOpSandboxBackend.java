package io.nop.ai.agent.security;

import io.nop.api.core.time.CoreMetrics;
import java.io.IOException;
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
 * ({@link SandboxProcessCleanup#killTree}) so a runaway child does not
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
 * <p><b>Reader exit compensation + success-path harvest (CRA 3.1-MA-17,
 * plan 2501 Phase 4)</b>: a command that detaches a background twin
 * ({@code cmd &}) leaves the twin holding the stdout pipe, so no EOF
 * arrives. The reader is settled by closing the pipe (bounded grace +
 * forced EOF — {@code interrupt()} alone cannot break a native pipe
 * read); if it still cannot finish, the descendant tree is harvested to
 * close the pipe. A reader that terminates is indistinguishable from the
 * clean case; one that does not marks the result {@code
 * outputIncomplete} — a partial/empty capture is never silently
 * delivered as complete output. Shared implementation:
 * {@link SandboxProcessCleanup} (dual-fork convergence with
 * {@link DockerSandboxBackend}).
 *
 * <p><b>What is NOT enforced</b>: {@link SandboxConfig#getCpuCores()},
 * {@link SandboxConfig#getMemoryMb()}, and
 * {@link SandboxConfig#getNetworkMode()} require an isolator (cgroup /
 * namespace). The host backend records the configured values on the
 * passed-in {@link SandboxConfig} but does not honour them — a caller that
 * needs these limits MUST wire a functional backend such as
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

        long startNanos = CoreMetrics.nanoTime();

        // Spawn a dedicated reader thread so the calling thread is free to
        // honour the wall-time budget via waitFor(timeout). The reader
        // caps the captured payload at maxOutputBytes and drains the rest
        // so the child does not block on a full pipe before we can kill it.
        int maxBytes = config.getMaxOutputBytes();
        AtomicReference<StringBuilder> capturedRef = new AtomicReference<>();
        Thread reader = new Thread(() -> SandboxProcessCleanup.drainStream(
                        process.getInputStream(), maxBytes, capturedRef),
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
            SandboxProcessCleanup.killTree(process);
            // Reader thread will finish on its own once the killed process
            // closes its stdout pipe; the compensated wait additionally
            // closes the stream if a grandchild keeps it open (MA-17).
            boolean readerSettled =
                    SandboxProcessCleanup.awaitReaderExit(process, reader, config.getWallSeconds());
            long elapsedMs = (CoreMetrics.nanoTime() - startNanos) / 1_000_000L;
            return new SandboxResult(
                    /* exitCode */ exitCodeOrNegative(process),
                    SandboxProcessCleanup.capturedToString(capturedRef),
                    /* stderr already merged into captured */ "",
                    elapsedMs,
                    /* timedOut */ true,
                    /* outputIncomplete */ !readerSettled);
        }

        // Process exited naturally — settle the reader: bounded grace with
        // exit compensation; if a detached grandchild still holds the stdout
        // pipe, harvest the descendant tree (MA-17 success-path reap) and
        // give the reader one short re-join. A reader that still cannot
        // finish marks the result outputIncomplete (MA-17, plan 2501
        // Phase 4) — an empty/partial delivery is never silent.
        boolean readerSettled =
                SandboxProcessCleanup.awaitReaderSettled(process, reader, config.getWallSeconds());
        int exitCode = exitCodeOrNegative(process);
        long elapsedMs = (CoreMetrics.nanoTime() - startNanos) / 1_000_000L;
        return new SandboxResult(exitCode, SandboxProcessCleanup.capturedToString(capturedRef),
                "", elapsedMs, false, !readerSettled);
    }

    private static int exitCodeOrNegative(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException stillAlive) {
            return -1;
        }
    }
}
