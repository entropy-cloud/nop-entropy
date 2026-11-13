package io.nop.lint.js.tsc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer-side protocol guard proofs (plan 2531 W-09, red⑧ — M-P2-1):
 * the single-flight protocol's one defense is "the next frame answers the
 * in-flight request", and an error frame must obey it too. Two scripted
 * peer modes drive the guards:
 *
 * <ul>
 * <li>{@code error-stale-id} — the first incarnation answers every query
 * with an error frame whose id is not in flight (the helper's synthetic
 * frames do the same with id=-1): the bridge must detect the correlation
 * mismatch, recycle the process, and name the peer's code — not surface the
 * stale error verbatim against the wrong request.</li>
 * <li>{@code half-frame-then-die} — the first incarnation writes half a
 * JSON line and exits: the undecodable frame is a stream-desync signal and
 * must recycle immediately (W-12's consumer-facing twin), burning exactly
 * one restart slot so the very next query recovers.</li>
 * </ul>
 *
 * <p>Recycle is observed at runtime through the peer's pid report: a
 * recycled bridge spans two distinct peer processes (zero product seam).
 * The protocol-version face of W-09 lives in {@code TscProtocolTest}.</p>
 */
public class TestTscBridgeProtocolGuards_W_09 {

    @TempDir
    Path tempDir;

    @Test
    public void testW_09_errorFrameWithStaleIdIsRecycledAndNotMisattributed() throws Exception {
        Path marker = tempDir.resolve("stale-id-marker.txt");
        NodeTscBridge bridge = bridge("error-stale-id", marker.toString(), 1);
        try {
            bridge.initProject(writeTsconfig("stale"));
            TscQueryException ex = assertThrows(bridge, 0, 0);
            // the stale error frame is a correlation violation of THIS bridge,
            // not the peer's answer to this request: BAD_FRAME form, carrying
            // the peer's code for diagnosis (death-cause consistency)
            assertEquals("BAD_FRAME", ex.code(),
                    "a stale-id error frame must surface as a correlation violation, got code "
                            + ex.code() + ": " + ex.getMessage());
            assertTrue(ex.getMessage().contains("STALE_ROUTE"),
                    "the violation must carry the peer's error code: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("does not match request"),
                    "the violation must name the correlation mismatch: " + ex.getMessage());
            // the recycle lets the very next query succeed on the fresh peer
            assertEquals("FakeType:1:1", bridge.getTypeAtLocation("a.ts", 1, 1),
                    "the recycled bridge must recover on the next query");
            // runtime recycle evidence: two generations served this bridge
            assertEquals(2, readPids(marker).size(),
                    "the stale-id recycle must have replaced the peer process");
        } finally {
            bridge.close();
        }
    }

    @Test
    public void testW_09_halfFrameDesyncRecyclesAndBurnsExactlyOneRestartSlot() throws Exception {
        Path marker = tempDir.resolve("half-frame-marker.txt");
        NodeTscBridge bridge = bridge("half-frame-then-die", marker.toString(), 1);
        try {
            bridge.initProject(writeTsconfig("half"));
            TscQueryException ex = assertThrows(bridge, 0, 0);
            assertEquals("BAD_FRAME", ex.code(),
                    "the undecodable half frame must surface as BAD_FRAME");
            // W-12 activation: the decode-side recycle means the very next
            // query recovers with the single remaining restart slot — a late
            // recycle would burn that slot on the dead process first and
            // leave the next query against an exhausted bridge
            assertEquals("FakeType:1:1", bridge.getTypeAtLocation("a.ts", 1, 1),
                    "the BAD_FRAME recycle must burn exactly one restart slot: the next "
                            + "query respawns and succeeds");
            // the budget is spent but the bridge is READY and keeps serving
            assertEquals("FakeType:2:2", bridge.getTypeAtLocation("a.ts", 2, 2),
                    "a READY bridge never burns restarts on healthy queries");
        } finally {
            bridge.close();
        }
    }

    // ==================== helpers ====================

    private static TscQueryException assertThrows(NodeTscBridge bridge, int line, int col) {
        try {
            bridge.getTypeAtLocation("a.ts", line, col);
        } catch (TscQueryException e) {
            return e;
        } catch (TscBridgeUnavailableException e) {
            throw new AssertionError("the bridge must recycle, not exhaust: " + e.getMessage(), e);
        }
        throw new AssertionError("the query against the poisoning peer must fail");
    }

    private Path writeTsconfig(String tag) throws IOException {
        Path tsconfig = tempDir.resolve("tsconfig-" + tag + ".json");
        Files.writeString(tsconfig, "{\"include\": [\"a\"]}");
        return tsconfig;
    }

    private NodeTscBridge bridge(String mode, String markerFile, int maxRestarts) {
        TscBridgeConfig config = TscBridgeConfig.forPeerCommand(
                NodeTscBridgeTest.peerCommand(mode, markerFile),
                Duration.ofSeconds(20), Duration.ofSeconds(5), maxRestarts);
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
            throw new AssertionError("cannot read the peer pid report " + pidFile, e);
        }
    }
}
