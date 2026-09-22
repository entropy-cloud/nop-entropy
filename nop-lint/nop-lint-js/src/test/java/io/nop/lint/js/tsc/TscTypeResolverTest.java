package io.nop.lint.js.tsc;

import io.nop.lint.core.semantic.TypeResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link TscTypeResolver} adapter matrix (roadmap item 20 Phase 2):
 * lazy availability probing (no process before the first query), explicit
 * and default-project initialization, query pass-through against the
 * scripted {@link FakeTscPeer}, and the exception mapping that turns every
 * bridge failure into the engine's degrade signal
 * ({@link TypeResolutionException}). No real Node or typescript is involved.
 */
public class TscTypeResolverTest {

    @TempDir
    Path tempDir;

    @Test
    public void availabilityProbeStaysSideEffectFree() {
        TscTypeResolver resolver = new TscTypeResolver(peerConfig("ok", null, 2));
        // No query has happened: nothing spawned, and a usable environment
        // probes true without starting anything.
        assertTrue(resolver.isAvailable(), "the fake peer environment is usable");
        assertTrue(resolver.isAvailable(), "repeated probes stay cheap and side-effect free");
        resolver.close();
    }

    @Test
    public void queriesRunThroughTheBridgeAfterExplicitInit() throws IOException {
        TscTypeResolver resolver = new TscTypeResolver(peerConfig("ok", null, 2));
        try {
            resolver.initProject(writeTsconfig("{\"include\": [\"a\"]}"));
            assertTrue(resolver.isAssignableTo("a.ts", 0, 4, "number"),
                    "the fake peer answers assignable for non-'never' targets");
            assertFalse(resolver.isAssignableTo("a.ts", 0, 0, "never"),
                    "the fake peer's deterministic negative case");
            assertEquals("FakeType:0:4", resolver.typeNameAt("a.ts", 0, 4));
        } finally {
            resolver.close();
        }
    }

    @Test
    public void defaultProjectBindsOnFirstQuery() throws IOException {
        TscBridgeConfig config = peerConfig("ok", null, 2);
        TscTypeResolver resolver = new TscTypeResolver(config, writeTsconfig("{\"include\": [\"a\"]}"));
        try {
            // No explicit initProject: the default project joins on the
            // first real query (lazy, one init).
            assertEquals("FakeType:1:2", resolver.typeNameAt("a.ts", 1, 2));
            assertTrue(resolver.isAssignableTo("a.ts", 0, 0, "number"));
        } finally {
            resolver.close();
        }
    }

    @Test
    public void missingTypescriptMapsToTypeResolutionFailure() throws IOException {
        TscTypeResolver resolver = new TscTypeResolver(peerConfig("no-typescript", null, 2));
        TypeResolutionException ex = assertThrows(TypeResolutionException.class,
                () -> resolver.initProject(writeTsconfig("x")));
        assertTrue(ex.getMessage().contains("initProject"), ex.getMessage());
        assertFalse(resolver.isAvailable(), "the bridge went terminal-unavailable");
        resolver.close();
    }

    @Test
    public void structuredQueryErrorsMapToTypeResolutionFailures() throws IOException {
        TscTypeResolver resolver = new TscTypeResolver(peerConfig("ok", null, 2));
        try {
            resolver.initProject(writeTsconfig("x"));
            // a tsconfig the fake peer cannot find → structured TSCONFIG_INVALID frame
            TypeResolutionException ex = assertThrows(TypeResolutionException.class,
                    () -> resolver.initProject(tempDir.resolve("no-such.json")));
            assertTrue(ex.getMessage().contains("initProject"), ex.getMessage());
        } finally {
            resolver.close();
        }
    }

    @Test
    public void closedResolverStaysUnavailable() throws IOException {
        TscTypeResolver resolver = new TscTypeResolver(peerConfig("ok", null, 2));
        resolver.initProject(writeTsconfig("x"));
        resolver.close();
        TypeResolutionException ex = assertThrows(TypeResolutionException.class,
                () -> resolver.typeNameAt("a.ts", 0, 0));
        assertTrue(ex.getMessage().contains("getTypeAtLocation"), ex.getMessage());
    }

    // ==================== helpers ====================

    private Path writeTsconfig(String content) throws IOException {
        Path tsconfig = tempDir.resolve("tsconfig-" + System.nanoTime() + ".json");
        Files.writeString(tsconfig, content);
        return tsconfig;
    }

    private TscBridgeConfig peerConfig(String mode, String markerFile, int maxRestarts) {
        return TscBridgeConfig.forPeerCommand(NodeTscBridgeTest.peerCommand(mode, markerFile),
                Duration.ofSeconds(20), Duration.ofSeconds(10), maxRestarts);
    }
}
