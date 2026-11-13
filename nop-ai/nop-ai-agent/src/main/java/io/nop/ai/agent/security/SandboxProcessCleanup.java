package io.nop.ai.agent.security;

import io.nop.commons.util.IoHelper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CRA 3.1-MA-17 (plan 2501 Phase 4): shared process/reader cleanup contract
 * for the two {@link ISandboxBackend} implementations. {@link
 * DockerSandboxBackend} and {@link NoOpSandboxBackend} previously carried
 * structurally identical but independently drifting copies of this logic
 * (the audit's F8 fork): the Docker side had no local CLI-process destroy on
 * the timeout path, the reader fallback was interrupt-only (ineffective
 * against a thread blocked in a native pipe read), and the terminal buffer
 * was published as a live object a late reader could still mutate. This
 * class is the single implementation both backends delegate to — dual fork
 * convergence — aligned with the nop-ai-toolkit sandbox family's three-part
 * cleanup standard landed in plan 2501 Phase 2 (3.2-C6-001/C6-002/C6-003):
 * forcible process-tree destruction, stream-close reader exit compensation
 * (closing the pipe forces the blocked read to abort), and visibly-failed
 * cleanup. The package contains no shared base class by design; this static
 * utility removes the duplication without coupling the backends' identity.
 */
final class SandboxProcessCleanup {

    /**
     * Short second-chance join after reader exit compensation / grandchild
     * harvest — enough for the reader to publish its final snapshot and
     * exit, never unbounded.
     */
    private static final long REJOIN_MILLIS = 500L;

    private SandboxProcessCleanup() {
    }

    /**
     * Forcibly terminate the process and its descendants, then boundedly wait
     * for the root's death. Best-effort: ProcessHandle.descendants() may not
     * be supported on every platform, in which case we still kill the root
     * and let the OS reap any orphaned children.
     */
    static void killTree(Process process) {
        reapTree(process);
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Forcibly terminate the process tree (descendants first, then the root)
     * without waiting for the root's death. Best-effort per handle. Used as
     * the leak backstop on the Docker timeout path (the local {@code docker
     * run} CLI is destroyed even when the container kill failed or wedged)
     * and as the success-path grandchild harvest (a detached twin keeps the
     * stdout write end open — killing it closes the pipe).
     */
    static void reapTree(Process process) {
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
    }

    /**
     * Bounded wait for the reader thread with exit compensation. {@code
     * interrupt()} has no effect on a thread blocked in a native pipe read
     * (a detached twin/grandchild keeps the stdout write end open, so no EOF
     * arrives) — closing the stream forces the blocked read to abort, after
     * which the reader publishes its final immutable snapshot and exits.
     *
     * @return {@code true} if the reader terminated within the compensated
     *         grace budget (its terminal snapshot is published); {@code
     *         false} if it is still alive (the caller must mark the result
     *         {@code outputIncomplete} — output is never silently presented
     *         as complete)
     */
    static boolean awaitReaderExit(Process process, Thread reader, int wallSeconds) {
        long graceMillis = Math.max(500L, Math.min(2_000L, wallSeconds * 500L));
        try {
            reader.join(graceMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!reader.isAlive()) {
            return true;
        }
        // MA-17 reader exit compensation: close the pipe — interrupt alone
        // cannot break a thread blocked in a native InputStream.read.
        IoHelper.safeClose(process.getInputStream());
        reader.interrupt();
        try {
            reader.join(REJOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return !reader.isAlive();
    }

    /**
     * Success-path reader settle: bounded wait with exit compensation; if
     * the reader is still alive a detached grandchild is holding the stdout
     * pipe — harvest the (root already exited) descendant tree to close it,
     * then give the reader one short re-join.
     *
     * @return {@code true} if the reader terminated (full output captured);
     *         {@code false} if output may be incomplete (caller marks the
     *         result {@code outputIncomplete})
     */
    static boolean awaitReaderSettled(Process process, Thread reader, int wallSeconds) {
        if (awaitReaderExit(process, reader, wallSeconds)) {
            return true;
        }
        // Grandchild harvest: killing the pipe holders closes the stdout
        // write end so the reader can observe EOF.
        reapTree(process);
        try {
            reader.join(REJOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return !reader.isAlive();
    }

    /**
     * Read from {@code in} into a buffer held in {@code capturedRef} until
     * EOF, capping the captured payload at {@code maxBytes}. Bytes beyond
     * the cap are drained (read-and-discarded) so the child process cannot
     * block on a full pipe before its parent can terminate it.
     *
     * <p>Terminal publication (MA-17, 3.2-C6-008 aligned): the reference is
     * set exactly once, to an immutable {@code StringBuilder} copy — a
     * caller observing the buffer can never race a still-draining reader,
     * and the pipe fd is released as soon as this drain ends.
     */
    static void drainStream(InputStream in, int maxBytes, AtomicReference<StringBuilder> capturedRef) {
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
        } catch (IOException e) {
            // Stream closed unexpectedly (exit compensation / process death) —
            // keep the partial capture; the exit code / kill path still
            // produces a coherent result.
        } finally {
            capturedRef.set(new StringBuilder(captured));
            IoHelper.safeClose(in);
        }
    }

    static String capturedToString(AtomicReference<StringBuilder> ref) {
        StringBuilder sb = ref.get();
        return sb != null ? sb.toString() : "";
    }
}
