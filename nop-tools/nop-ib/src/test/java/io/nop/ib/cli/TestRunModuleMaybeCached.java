package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 9 phase-2 pins (audit M1): the {@code --cache} wiring in
 * {@link TestCommand#runModuleMaybeCached} — a fingerprint hit returns an
 * explicit {@code cached-up-to-date} run WITHOUT executing, and {@code --force}
 * bypasses the cache (the execution path runs even when a hit exists).
 */
class TestRunModuleMaybeCached {

    private IbEngine setup() throws Exception {
        Path repo = Files.createTempDirectory("ib-tcache-cmd");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
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
        IbEngine engine = new IbEngine(repo);
        io.nop.ib.index.UnitIndex index = io.nop.ib.index.UnitIndex.empty("fixture");
        io.nop.ib.index.ClassInfo info = new io.nop.ib.index.ClassInfo();
        info.setClassName("g.FixTest");
        info.setContentHash("h1");
        info.setAbiDigest("abi1");
        info.setSourceFile("src/test/java/g/FixTest.java");
        index.put(info);
        engine.getUnitStore().save(index);
        return engine;
    }

    private static void seedGreenHit(IbEngine engine, TestCacheStore cache, IbModule module) {
        String fp = cache.fingerprint(engine, module);
        cache.record(module.getArtifactId(), fp, 0, 233, 0, 0, 1);
    }

    /** a fingerprint hit returns kind=cached-up-to-date with the stored summary. */
    @Test
    void cacheHitReturnsCachedUpToDateWithoutExecuting() throws Exception {
        IbEngine engine = setup();
        IbModule module = engine.model().module("fixture");
        TestCacheStore cache = new TestCacheStore(engine);
        seedGreenHit(engine, cache, module);
        PrintWriter out = new PrintWriter(new java.io.StringWriter(), true);
        PrintWriter err = new PrintWriter(new java.io.StringWriter(), true);
        TestCommand.TestRun run = TestCommand.runModuleMaybeCached(
                engine, cache, false, module, null, "junit", true, out, err, 2);
        assertEquals("cached-up-to-date", run.kind, "hit must serve the cached kind");
        assertEquals(0, run.exitCode, "cached green exits OK");
        assertEquals(1, run.summary.size(), "stored summary is propagated");
        assertNotEquals("junit-fork", run.kind, "hit must NOT fork JUnit");
        assertTrue(!run.warnings.isEmpty(), "cached runs carry the advisory warning");
        assertTrue(run.warnings.get(0).contains("--force"), "warning names the escape hatch");
    }

    /** --force bypasses the cache even when a valid hit exists. */
    @Test
    void forceBypassesCacheEvenWithHit() throws Exception {
        IbEngine engine = setup();
        IbModule module = engine.model().module("fixture");
        TestCacheStore cache = new TestCacheStore(engine);
        seedGreenHit(engine, cache, module);
        PrintWriter out = new PrintWriter(new java.io.StringWriter(), true);
        PrintWriter err = new PrintWriter(new java.io.StringWriter(), true);
        // in this fixture the execution path cannot fork real JUnit; the pin is
        // that the result is NOT the cached kind — any execution-shaped outcome
        // (here: skipped, no dispatchable classes in the fixture index) proves
        // the cache was bypassed rather than served
        TestCommand.TestRun run = TestCommand.runModuleMaybeCached(
                engine, cache, true, module, null, "junit", true, out, err, 2);
        assertNotEquals("cached-up-to-date", run.kind,
                "--force must not serve the cached kind even with a valid hit");
    }

    /** cache=null (no --cache) keeps the legacy path byte-for-byte. */
    @Test
    void nullCacheKeepsLegacyPath() throws Exception {
        IbEngine engine = setup();
        IbModule module = engine.model().module("fixture");
        PrintWriter out = new PrintWriter(new java.io.StringWriter(), true);
        PrintWriter err = new PrintWriter(new java.io.StringWriter(), true);
        TestCommand.TestRun run = TestCommand.runModuleMaybeCached(
                engine, null, false, module, null, "junit", true, out, err, 2);
        assertNotEquals("cached-up-to-date", run.kind,
                "without --cache the cached kind is unreachable");
    }
}
