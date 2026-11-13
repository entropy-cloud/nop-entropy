package io.nop.lint.js.tsc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The generation-fence proofs for the bridge restart path (plan 2531 W-01,
 * red① — M-P1-1): a recycled generation must never leak its residual
 * traffic into the fresh generation. Two poison sources drive the two
 * failure branches, both as the hang-past-deadline family with a marker
 * switching the incarnation:
 *
 * <ul>
 * <li>{@code hang-respond-past-deadline} — the first incarnation flushes a
 * complete response frame and then parks; the near-zero deadline recycles
 * the process while the frame is still in flight, so the stale <b>data
 * frame</b> is the branch-A poison (crossing into the fresh handshake kills
 * the healthy peer and voids the restart budget).</li>
 * <li>{@code hang-silent-past-deadline} — the first incarnation parks with
 * no response; the recycle's SIGKILL closes the pipe and the old reader
 * offers its <b>EOF</b>, the branch-B poison (terminating the fresh bridge
 * and orphaning a healthy peer).</li>
 * </ul>
 *
 * <p>Red-evidence form (plan 2531 adjudication 8): a bounded-trial loop —
 * any single trial observing a poisoned recovery is a red; post-fix the
 * same trial count must observe zero escapes. Each trial additionally
 * asserts the death-cause consistency of the recovery chain (red①
 * assertion 5) and, after close, that no spawned peer process survives
 * (red① assertion 4 — the orphaned-process face carried by the peer's pid
 * report and {@link ProcessHandle}, zero product seam).</p>
 */
public class TestNodeTscBridgeGenerationFence_W_01 {

    /**
     * The trial budget of the bounded loop-red capture (adjudication 8):
     * each trial costs one or two JVM respawns (hundreds of ms each), so 30
     * trials bound the whole proof well under CI budget while giving the
     * unfenced races far more than one window to surface.
     */
    private static final int TRIALS = 30;

    @TempDir
    Path tempDir;

    @Test
    public void testW_01_staleResponseFrameNeverKillsTheFreshGeneration() throws Exception {
        for (int trial = 0; trial < TRIALS; trial++) {
            Path marker = tempDir.resolve("respond-" + trial + "-marker.txt");
            NodeTscBridge bridge = bridge("hang-respond-past-deadline", marker.toString());
            try {
                bridge.initProject(writeTsconfig(trial));
                TscQueryException timeout = queryExpectTimeout(bridge, trial);
                // death-cause consistency: the timeout names its own cause,
                // never a misattributed "peer exited"
                assertTrue(timeout.getMessage().contains("deadline budget"),
                        "trial " + trial + ": the failure must name the real death cause: "
                                + timeout.getMessage());
                // branch-A fence: the recovery handshake must succeed on the
                // fresh generation — a stale response frame in the new queue
                // would fail validateReady, kill the healthy peer, and void
                // the budget (observable as an unavailable bridge here)
                assertEquals("FakeType:1:1", bridge.getTypeAtLocation("a.ts", 1, 1),
                        "trial " + trial + ": the fresh generation was poisoned by a stale frame");
            } finally {
                bridge.close();
            }
            assertNoPeerAlive(marker, "respond trial " + trial);
        }
    }

    @Test
    public void testW_01_staleEofNeverTerminatesTheFreshGeneration() throws Exception {
        for (int trial = 0; trial < TRIALS; trial++) {
            Path marker = tempDir.resolve("silent-" + trial + "-marker.txt");
            NodeTscBridge bridge = bridge("hang-silent-past-deadline", marker.toString());
            try {
                bridge.initProject(writeTsconfig(trial));
                TscQueryException timeout = queryExpectTimeout(bridge, trial);
                assertTrue(timeout.getMessage().contains("deadline budget"),
                        "trial " + trial + ": the failure must name the real death cause: "
                                + timeout.getMessage());
                // branch-B fence: the stale EOF must not terminate the fresh
                // handshake — poisoning it would misjudge the brand-new peer
                // as permanently exhausted (and orphan it)
                assertEquals("FakeType:1:1", bridge.getTypeAtLocation("a.ts", 1, 1),
                        "trial " + trial + ": the fresh generation was terminated by a stale EOF");
            } finally {
                bridge.close();
            }
            assertNoPeerAlive(marker, "silent trial " + trial);
        }
    }

    // ==================== helpers ====================

    private static TscQueryException queryExpectTimeout(NodeTscBridge bridge, int trial) {
        try {
            bridge.getTypeAtLocation("a.ts", 0, 0);
        } catch (TscQueryException e) {
            assertEquals("TIMEOUT", e.code(),
                    "trial " + trial + ": the hung peer must surface as a structured timeout");
            return e;
        }
        return fail("trial " + trial + ": the query against a hung peer must time out");
    }

    private Path writeTsconfig(int trial) throws IOException {
        Path tsconfig = tempDir.resolve("tsconfig-" + trial + ".json");
        Files.writeString(tsconfig, "{\"include\": [\"a\"]}");
        return tsconfig;
    }

    private NodeTscBridge bridge(String mode, String markerFile) {
        // The query deadline (300ms) is far exceeded by the hang-respond
        // peer's paced streaming write (the frame never completes) and by
        // the hang-silent peer's total silence, so the recycle fires before
        // any stranded frame could be consumed — while still leaving a
        // fresh peer's tens-of-ms answer plenty of budget for the recovery
        // query. A normal handshake budget lets the fresh generation boot
        // (a JVM respawn takes hundreds of ms).
        TscBridgeConfig config = TscBridgeConfig.forPeerCommand(
                NodeTscBridgeTest.peerCommand(mode, markerFile),
                Duration.ofSeconds(20), Duration.ofMillis(300), 1);
        return new NodeTscBridge(config);
    }

    private static List<Long> readPids(Path marker) {
        Path pidFile = Path.of(marker.toString() + ".pid");
        if (!Files.exists(pidFile)) {
            return List.of();
        }
        try {
            List<Long> pids = new ArrayList<>();
            for (String line : Files.readAllLines(pidFile)) {
                if (!line.isBlank()) {
                    pids.add(Long.parseLong(line.trim()));
                }
            }
            return pids;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the peer pid report " + pidFile, e);
        }
    }

    /**
     * Red① assertion 4: after the recycle and the close, every peer this
     * trial spawned must be gone — a generation-unfenced bridge leaves a
     * healthy respawned peer orphaned until JVM exit (the branch-B face and
     * CS-001). SIGKILL is asynchronous, so the wait is bounded.
     */
    private static void assertNoPeerAlive(Path marker, String context) throws InterruptedException {
        List<Long> pids = readPids(marker);
        assertTrue(pids.size() >= 2, context + ": the trial must have spanned two generations "
                + "(the hung one and its replacement); pid report: " + pids);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        List<Long> alive = pids;
        while (System.nanoTime() < deadline) {
            alive = pids.stream().filter(pid -> ProcessHandle.of(pid).map(ProcessHandle::isAlive)
                    .orElse(false)).toList();
            if (alive.isEmpty()) {
                return;
            }
            Thread.sleep(50);
        }
        fail(context + ": recycled peer processes still alive after close: " + alive);
    }
}
