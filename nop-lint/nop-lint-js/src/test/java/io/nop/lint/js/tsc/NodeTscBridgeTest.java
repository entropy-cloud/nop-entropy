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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 1 process-management matrix (Minimum Rules #25) against the
 * scripted {@link FakeTscPeer}: handshake success and failure (missing
 * typescript, dead peer), structured query error frames, per-query deadline,
 * crash detection with one bounded restart, restart exhaustion entering the
 * explicit unavailable state, and the tsconfig-hash program-cache behavior.
 * No Node or typescript installation is involved — the peer is the current
 * JVM re-spawned with a mode argument.
 */
public class NodeTscBridgeTest {

    @TempDir
    Path tempDir;

    // ==================== handshake ====================

    @Test
    public void handshakeSucceedsAndQueriesReuseTheProcess() throws IOException {
        NodeTscBridge bridge = bridge("ok", null);
        try {
            assertTrue(bridge.isAvailable(), "the fake peer environment is usable");
            Path tsconfig = writeTsconfig("include a.ts");
            NodeTscBridge.ProjectInfo info = bridge.initProject(tsconfig);
            assertTrue(info.rebuilt(), "the first init builds the program");
            assertEquals("FakeType:0:4", bridge.getTypeAtLocation("a.ts", 0, 4),
                    "the rendered type echoes the queried position deterministically");
            assertEquals("FakeType:0:4", bridge.getTypeAtLocation("a.ts", 0, 4),
                    "a second identical query goes through the same resident process");
            assertTrue(bridge.isTypeAssignableTo("a.ts", 0, 0, "number"));
            assertFalse(bridge.isTypeAssignableTo("a.ts", 0, 0, "never"),
                    "the fake peer's deterministic negative case");
        } finally {
            bridge.close();
        }
    }

    @Test
    public void missingTypescriptBindingIsAnExplicitUnavailableState() {
        NodeTscBridge bridge = bridge("no-typescript", null);
        TscBridgeUnavailableException ex = assertThrows(TscBridgeUnavailableException.class,
                () -> bridge.initProject(writeTsconfig("x")));
        assertTrue(ex.getMessage().contains("typescript"), ex.getMessage());
        // the bridge is terminal-unavailable: no retry, no silent skip
        assertThrows(TscBridgeUnavailableException.class, () -> bridge.getTypeAtLocation("a.ts", 0, 0));
        assertFalse(bridge.isAvailable());
        bridge.close();
    }

    @Test
    public void peerDyingBeforeReadyIsAnExplicitUnavailableState() {
        NodeTscBridge bridge = bridge("exit-before-ready", null);
        TscBridgeUnavailableException ex = assertThrows(TscBridgeUnavailableException.class,
                () -> bridge.initProject(writeTsconfig("x")));
        assertTrue(ex.getMessage().contains("ready"), ex.getMessage());
        assertFalse(bridge.isAvailable());
        bridge.close();
    }

    // ==================== structured query failures ====================

    @Test
    public void errorFramesAreStructuredFailuresWithTheirCode() throws IOException {
        NodeTscBridge bridge = bridge("ok", null);
        try {
            bridge.initProject(writeTsconfig("include a.ts"));
            TscQueryException ex = assertThrows(TscQueryException.class,
                    () -> bridge.initProject(tempDir.resolve("no-such-tsconfig.json")));
            assertEquals("TSCONFIG_INVALID", ex.code(),
                    "the peer's structured error code surfaces verbatim");
            assertTrue(bridge.isAvailable(), "a structured error frame leaves the healthy process up");
        } finally {
            bridge.close();
        }
    }

    @Test
    public void unparseablePeerFramesAreProtocolViolations() throws IOException {
        NodeTscBridge bridge = bridge("garbage-response", null, 1);
        try {
            // the garbage-response peer answers every frame — including the
            // first initProject — with a non-JSON line
            TscQueryException ex = assertThrows(TscQueryException.class,
                    () -> bridge.initProject(writeTsconfig("x")));
            assertEquals("BAD_FRAME", ex.code());
        } finally {
            bridge.close();
        }
    }

    @Test
    public void queryDeadlineIsAStructuredTimeoutAndRecyclesThePeer() throws IOException {
        // hang-once-then-ok parks on the query before its marker exists, so
        // the recycled (re-spawned) peer answers instead of hanging again.
        Path marker = tempDir.resolve("hang-marker.txt");
        NodeTscBridge bridge = bridge("hang-once-then-ok", marker.toString(), Duration.ofMillis(3000),
                Duration.ofMillis(200), 1);
        try {
            bridge.initProject(writeTsconfig("x"));
            long startedAt = System.nanoTime();
            TscQueryException ex = assertThrows(TscQueryException.class,
                    () -> bridge.getTypeAtLocation("a.ts", 0, 0));
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            assertEquals("TIMEOUT", ex.code());
            assertTrue(elapsedMs < 2500, "the deadline bound the wait: " + elapsedMs + "ms");
            // the hung process was recycled: one restart is left, so the
            // next query re-spawns and succeeds (the fresh peer answers).
            assertEquals("FakeType:1:1", bridge.getTypeAtLocation("a.ts", 1, 1));
        } finally {
            bridge.close();
        }
    }

    // ==================== crash detection and bounded restart ====================

    @Test
    public void crashedProcessRestartsWithinBudget() throws IOException {
        Path marker = tempDir.resolve("crash-marker.txt");
        NodeTscBridge bridge = bridge("crash-once-then-ok", marker.toString());
        try {
            bridge.initProject(writeTsconfig("x"));
            TscQueryException ex = assertThrows(TscQueryException.class,
                    () -> bridge.getTypeAtLocation("a.ts", 0, 0));
            assertEquals("BRIDGE_CRASHED", ex.code(), "the crash surfaces as a structured failure");
            // the restarted incarnation (marker file present) answers normally
            assertEquals("FakeType:2:2", bridge.getTypeAtLocation("a.ts", 2, 2),
                    "the bounded restart brought the bridge back");
        } finally {
            bridge.close();
        }
    }

    @Test
    public void restartExhaustionIsTerminalAndExplicit() throws IOException {
        NodeTscBridge bridge = bridge("crash-on-first-query", null, Duration.ofSeconds(5),
                Duration.ofSeconds(5), 1);
        try {
            bridge.initProject(writeTsconfig("x"));
            assertThrows(TscQueryException.class, () -> bridge.getTypeAtLocation("a.ts", 0, 0),
                    "first crash consumes one restart slot");
            assertThrows(TscQueryException.class, () -> bridge.getTypeAtLocation("a.ts", 0, 0),
                    "second crash exhausts the budget");
            TscBridgeUnavailableException ex = assertThrows(TscBridgeUnavailableException.class,
                    () -> bridge.getTypeAtLocation("a.ts", 0, 0));
            assertTrue(ex.getMessage().contains("restart budget"), ex.getMessage());
            assertFalse(bridge.isAvailable(), "the unavailable state is observable");
            // and it never silently recovers
            assertThrows(TscBridgeUnavailableException.class, () -> bridge.initProject(writeTsconfig("y")));
        } finally {
            bridge.close();
        }
    }

    // ==================== program cache: tsconfig-hash invalidation ====================

    @Test
    public void tsconfigChangeInvalidatesTheProgramCache() throws IOException {
        NodeTscBridge bridge = bridge("ok", null);
        try {
            Path tsconfig = writeTsconfig("{\"include\": [\"a\"]}");
            NodeTscBridge.ProjectInfo first = bridge.initProject(tsconfig);
            assertTrue(first.rebuilt());

            NodeTscBridge.ProjectInfo unchanged = bridge.initProject(tsconfig);
            assertFalse(unchanged.rebuilt(), "an unchanged tsconfig must not rebuild");
            assertEquals(first.cacheKey(), unchanged.cacheKey());

            Files.writeString(tsconfig, "{\"include\": [\"a\", \"b\"]}");
            NodeTscBridge.ProjectInfo invalidated = bridge.initProject(tsconfig);
            assertTrue(invalidated.rebuilt(), "a tsconfig change must rebuild (design 11 §4)");
            assertNotEquals(first.cacheKey(), invalidated.cacheKey(),
                    "the cache key hash covers the changed config content");
        } finally {
            bridge.close();
        }
    }

    // ==================== helpers ====================

    private Path writeTsconfig(String content) throws IOException {
        Path tsconfig = tempDir.resolve("tsconfig-" + System.nanoTime() + ".json");
        Files.writeString(tsconfig, content);
        return tsconfig;
    }

    private NodeTscBridge bridge(String mode, String markerFile) {
        return bridge(mode, markerFile, Duration.ofSeconds(20), Duration.ofSeconds(10), 2);
    }

    private NodeTscBridge bridge(String mode, String markerFile, int maxRestarts) {
        return bridge(mode, markerFile, Duration.ofSeconds(20), Duration.ofSeconds(10), maxRestarts);
    }

    private NodeTscBridge bridge(String mode, String markerFile, Duration handshake, Duration query,
                                 int maxRestarts) {
        TscBridgeConfig config = TscBridgeConfig.forPeerCommand(
                peerCommand(mode, markerFile), handshake, query, maxRestarts);
        return new NodeTscBridge(config);
    }

    static List<String> peerCommand(String mode, String markerFile) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(FakeTscPeer.class.getName());
        command.add(mode);
        if (markerFile != null) {
            command.add(markerFile);
        }
        return command;
    }
}
