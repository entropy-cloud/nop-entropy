package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Wave 9 pins: the opt-in test-result cache. Zero-fake-green contract — only
 * greens are stored/served, missing fingerprint never hits, fingerprints are
 * stable for identical inputs and change when any component changes.
 */
class TestTestCacheStore {

    private IbEngine engine;
    private IbModule module;

    private IbEngine setup() throws Exception {
        Path repo = Files.createTempDirectory("ib-tcache");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        // write a minimal model.json so engine.model() works
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        Map<String, Object> mod = new java.util.LinkedHashMap<>();
        mod.put("groupId", "g");
        mod.put("artifactId", "fixture");
        mod.put("version", "1");
        mod.put("packaging", "jar");
        mod.put("basedir", "fixture");
        mod.put("dependencies", List.of());
        mod.put("compilerExecutions", List.of());
        mod.put("execExecutionIds", List.of());
        mod.put("surefire", new java.util.LinkedHashMap<>());
        mod.put("externalArtifacts", List.of());
        mod.put("resources", List.of());
        mod.put("testResources", List.of());
        model.put("modules", List.of(mod));
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));
        return new IbEngine(repo);
    }

    /** writes a minimal unit index for "fixture" so fingerprint() has its input. */
    private void installIndexFixture() {
        io.nop.ib.index.UnitIndex index = io.nop.ib.index.UnitIndex.empty("fixture");
        io.nop.ib.index.ClassInfo info = new io.nop.ib.index.ClassInfo();
        info.setClassName("g.FixTest");
        info.setContentHash("h1");
        info.setAbiDigest("abi1");
        info.setSourceFile("src/test/java/g/FixTest.java");
        index.put(info);
        engine.getUnitStore().save(index);
    }

    /** red results are never stored: record(red) drops any prior green. */
    @Test
    void redResultsAreNeverStored() throws Exception {
        engine = setup();
        TestCacheStore store = new TestCacheStore(engine);
        store.record("fixture", "fp1", 0, 10, 0, 0, 0);
        assertNotNull(store.hit("fixture", "fp1"));
        store.record("fixture", "fp1", 1, 10, 2, 0, 0);
        assertNull(store.hit("fixture", "fp1"), "red run must drop the cached green");
    }

    /** null fingerprint (missing index/stale model) never hits. */
    @Test
    void nullFingerprintNeverHits() throws Exception {
        engine = setup();
        TestCacheStore store = new TestCacheStore(engine);
        store.record("fixture", null, 0, 5, 0, 0, 0);
        assertNull(store.hit("fixture", null));
        assertNull(store.hit("fixture", "anything"));
    }

    /** fingerprint changes when any component changes; stable otherwise. */
    @Test
    void fingerprintSensitivity() throws Exception {
        engine = setup();
        installIndexFixture();
        TestCacheStore store = new TestCacheStore(engine);
        IbModule mod = engine.model().module("fixture");
        mod.setSurefireArgLine("-Xmx1G");
        String fp1 = store.fingerprint(engine, mod);
        assertNotNull(fp1, "module with a unit index yields a fingerprint");
        String fp2 = store.fingerprint(engine, mod);
        assertEquals(fp1, fp2, "same inputs → same fingerprint");
        mod.setSurefireArgLine("-Xmx2G");
        assertNotEquals(fp1, store.fingerprint(engine, mod), "argLine change → new fingerprint");
        mod.setSurefireArgLine("-Xmx1G");
        mod.getSurefireSystemProperties().put("k", "v");
        assertNotEquals(fp1, store.fingerprint(engine, mod), "sysprop change → new fingerprint");
        mod.getSurefireSystemProperties().clear();
        mod.setSurefireGroups("fast");
        assertNotEquals(fp1, store.fingerprint(engine, mod), "groups change → new fingerprint");
        mod.setSurefireGroups(null);
        assertEquals(fp1, store.fingerprint(engine, mod), "restored inputs → original fingerprint");
    }

    /** store round-trip survives process restart (file persistence). */
    @Test
    void persistenceRoundTrip() throws Exception {
        engine = setup();
        TestCacheStore store = new TestCacheStore(engine);
        store.record("fixture", "fpX", 0, 233, 0, 0, 0);
        store.save();
        TestCacheStore reloaded = new TestCacheStore(engine);
        Map<String, Object> hit = reloaded.hit("fixture", "fpX");
        assertNotNull(hit);
        assertEquals(233, ((Number) hit.get("tests")).intValue());
    }

    /**
     * plan 2583 pin: the fingerprint folds in the nop-ib version and JDK
     * version — a GREEN recorded under a different ib/JDK runtime must NOT
     * serve a hit (mutation probe: remove the dims line from fingerprint()
     * and this pin goes red via the first assertion).
     */
    @Test
    void runtimeDimensionChangeInvalidatesOldGreen() throws Exception {
        engine = setup();
        installIndexFixture();
        TestCacheStore store = new TestCacheStore(engine);
        IbModule mod = engine.model().module("fixture");
        String fp1 = store.fingerprint(engine, mod);
        store.record("fixture", fp1, 0, 5, 0, 0, 0);
        assertNotNull(store.hit("fixture", fp1), "green under the current runtime hits");
        var original = TestCacheStore.RUNTIME_DIMS;
        TestCacheStore.RUNTIME_DIMS = () -> "ib=0.0.9-older,jdk=21";
        try {
            String fp2 = store.fingerprint(engine, mod);
            assertNotEquals(fp1, fp2,
                    "a changed ib/JDK runtime dimension must change the fingerprint");
            assertNull(store.hit("fixture", fp2),
                    "a GREEN recorded under the old runtime must not serve a hit");
        } finally {
            TestCacheStore.RUNTIME_DIMS = original;
        }
        assertEquals(fp1, store.fingerprint(engine, mod),
                "restored dims → original fingerprint");
    }

    /** the dims themselves name both the ib version and the JDK version. */
    @Test
    void runtimeDimensionsCarryVersionAndJdk() {
        String dims = TestCacheStore.RUNTIME_DIMS.get();
        org.junit.jupiter.api.Assertions.assertTrue(dims.contains("ib=" + io.nop.ib.NopIbMain.VERSION),
                dims);
        org.junit.jupiter.api.Assertions.assertTrue(dims.contains("jdk=" + Runtime.version().feature()),
                dims);
    }
}
