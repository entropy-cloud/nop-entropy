package io.nop.ib.service;

import io.nop.ib.engine.IbEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 11 item 3 pins (resource recycling): engines idle beyond
 * {@code nop.ib.engineIdleTtlMillis} (default 30 min) are reaped by
 * {@link ServiceCore#reapIdleEngines()} — running roots are never reaped
 * (same {@code isEvictable} guard as LRU), and a reaped root cold-loads a
 * fresh, lossless engine (all state lives in {@code .nop-ib/}). The clock is
 * injectable so TTL expiry is deterministic.
 */
class TestEngineIdleReap {

    /** same fixture shape as TestServiceCore: minimal maven project + model.json. */
    private Path fixtureProject() throws Exception {
        Path repo = Files.createTempDirectory("ib-ttl");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", List.of());
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));
        return repo;
    }

    /** pin 1: an idle root past the TTL is reaped; a fresh engine is built next access. */
    @Test
    void idleRootIsReapedAfterTtl() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000L);
        ServiceCore core = new ServiceCore(4, new ProjectQueue(), now::get);
        Path root = fixtureProject().toRealPath();
        IbEngine first = core.engineFor(root);

        now.addAndGet(ServiceCore.DEFAULT_ENGINE_IDLE_TTL_MILLIS - 1000);
        assertEquals(0, core.reapIdleEngines(), "before the TTL nothing is reaped");

        now.addAndGet(2000); // past TTL
        assertEquals(1, core.reapIdleEngines(), "the idle engine is reaped at TTL expiry");
        IbEngine second = core.engineFor(root);
        assertNotSame(first, second,
                "a reaped root cold-loads a NEW engine instance (old one released)");
    }

    /** pin 2: a root with queued/running jobs is never reaped, however old it is. */
    @Test
    void runningRootIsNeverReaped() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000L);
        ProjectQueue lane = new ProjectQueue();
        ServiceCore core = new ServiceCore(4, lane, now::get);
        Path root = fixtureProject().toRealPath();
        IbEngine engine = core.engineFor(root);
        Object gate = new Object();
        JobRegistry registry = core.registry();
        BuildJob blocked = registry.create("p", "status", root, List.of(), false);
        assertTrue(lane.submit(blocked, () -> {
            try {
                synchronized (gate) {
                    gate.wait();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));
        try {
            now.addAndGet(ServiceCore.DEFAULT_ENGINE_IDLE_TTL_MILLIS * 10);
            assertEquals(0, core.reapIdleEngines(),
                    "a root with a queued/running job must not be reaped (isEvictable guard)");
            assertEquals(engine, core.engineFor(root), "the live engine instance stays cached");
        } finally {
            synchronized (gate) {
                gate.notifyAll();
            }
        }
    }

    /** pin 3: reaping is lossless — the rebuilt engine loads the same on-disk state. */
    @Test
    void rebuildAfterReapIsLossless() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000L);
        ServiceCore core = new ServiceCore(4, new ProjectQueue(), now::get);
        Path repo = fixtureProject();
        Path root = repo.toRealPath();
        IbEngine first = core.engineFor(root);
        var before = first.model();
        now.addAndGet(ServiceCore.DEFAULT_ENGINE_IDLE_TTL_MILLIS + 1);
        assertEquals(1, core.reapIdleEngines());
        IbEngine rebuilt = core.engineFor(root);
        assertNotSame(first, rebuilt);
        // state was only ever on disk (.nop-ib/model.json) — the cold-loaded
        // engine exposes the identical facts
        assertEquals(before.getRepoRoot(), rebuilt.model().getRepoRoot());
        assertEquals(before.getModules().size(), rebuilt.model().getModules().size());
    }

    /** health exposes engines / heapUsedMb / uptimeMs (wave 11 item 3). */
    @Test
    void healthExposesEngineAndRuntimeFacts() throws Exception {
        ServiceCore core = new ServiceCore(4);
        Path root = fixtureProject().toRealPath();
        core.engineFor(root);
        Map<String, Object> health = core.health();
        assertEquals(1, ((Number) health.get("engines")).intValue(),
                "engines = current engine cache size");
        assertTrue(((Number) health.get("heapUsedMb")).doubleValue() > 0,
                "heapUsedMb is a positive heap figure");
        assertTrue(((Number) health.get("uptimeMs")).longValue() >= 0,
                "uptimeMs is service uptime in millis");
    }

    /** TTL override: nop.ib.engineIdleTtlMillis shortens the reap horizon. */
    @Test
    void ttlIsOverridableViaSystemProperty() throws Exception {
        String key = "nop.ib.engineIdleTtlMillis";
        String old = System.getProperty(key);
        System.setProperty(key, "500");
        try {
            AtomicLong now = new AtomicLong(1_000_000L);
            ServiceCore core = new ServiceCore(4, new ProjectQueue(), now::get);
            Path root = fixtureProject().toRealPath();
            core.engineFor(root);
            now.addAndGet(499);
            assertEquals(0, core.reapIdleEngines());
            now.addAndGet(2); // past the 500ms override
            assertEquals(1, core.reapIdleEngines(), "override TTL (not the 30min default) governs");
        } finally {
            if (old == null)
                System.clearProperty(key);
            else
                System.setProperty(key, old);
        }
    }

    /** reaping an empty cache is a no-op. */
    @Test
    void reapOnEmptyCacheIsNoop() {
        AtomicLong now = new AtomicLong(1_000_000L);
        ServiceCore core = new ServiceCore(4, new ProjectQueue(), now::get);
        assertEquals(0, core.reapIdleEngines());
        assertTrue(core.health().containsKey("engines"));
    }
}
