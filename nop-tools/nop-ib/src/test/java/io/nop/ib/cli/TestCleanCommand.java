package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import io.nop.ib.model.IbModule;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 11 clean pins: fast-path product invalidation with explicit
 * invalidation of every derived state (unit index, test-cache entry) — a
 * cleaned module must not serve a cached green or an index-derived selection.
 */
class TestCleanCommand {

    /** fixture root with two modules; both targets pre-populated + index + cache */
    private IbEngine setup(boolean withCache) throws Exception {
        Path repo = Files.createTempDirectory("ib-clean");
        Files.createDirectories(repo.resolve(".nop-ib/units"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        java.util.List<Map<String, Object>> mods = new java.util.ArrayList<>();
        for (String name : new String[]{"modA", "modB"}) {
            Map<String, Object> mod = new java.util.LinkedHashMap<>();
            mod.put("groupId", "g");
            mod.put("artifactId", name);
            mod.put("version", "1");
            mod.put("packaging", "jar");
            mod.put("basedir", name);
            mod.put("dependencies", List.of());
            mod.put("compilerExecutions", List.of());
            mod.put("execExecutionIds", List.of());
            mod.put("surefire", new java.util.LinkedHashMap<>());
            mod.put("externalArtifacts", List.of());
            mod.put("resources", List.of());
            mod.put("testResources", List.of());
            mod.put("outputDirectory", name + "/target/classes");
            mod.put("testOutputDirectory", name + "/target/test-classes");
            mods.add(mod);
            Files.createDirectories(repo.resolve(name + "/target/classes"));
            Files.writeString(repo.resolve(name + "/target/classes/A.class"), "x");
            Files.createDirectories(repo.resolve(name + "/target/test-classes"));
            io.nop.ib.index.UnitIndex index = io.nop.ib.index.UnitIndex.empty(name);
            io.nop.ib.index.ClassInfo info = new io.nop.ib.index.ClassInfo();
            info.setClassName(name + ".Fix");
            info.setContentHash("h");
            info.setAbiDigest("abi");
            info.setSourceFile("src/main/java/x.java");
            index.put(info);
            engineSave(repo, name, index);
        }
        model.put("modules", mods);
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));
        IbEngine engine = new IbEngine(repo);
        if (withCache) {
            TestCacheStore cache = new TestCacheStore(engine);
            cache.record("modA", "fpA", 0, 10, 0, 0, 0);
            cache.save();
        }
        return engine;
    }

    private void engineSave(Path repo, String id, io.nop.ib.index.UnitIndex index) {
        new io.nop.ib.index.UnitIndexStore(repo).save(index);
    }

    /** -pl cleans only the selected module; the other stays intact. */
    @Test
    void plSelectsSingleModule() throws Exception {
        IbEngine engine = setup(false);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = CleanCommand.clean(engine, "modA", false,
                new java.io.PrintStream(out), new java.io.PrintStream(out));
        assertEquals(0, code);
        assertFalse(Files.exists(engine.getRepoRoot().resolve("modA/target/classes")),
                "selected module target must be gone");
        assertTrue(Files.exists(engine.getRepoRoot().resolve("modB/target/classes")),
                "unselected module must stay");
        assertNull(engine.index("modA"), "cleaned module's unit index must be dropped");
        assertEquals("abi", engine.index("modB").getClasses().values().iterator().next().getAbiDigest(),
                "unselected module's index must survive");
    }

    /** clean-all covers every module and drops matching test-cache entries. */
    @Test
    void cleanAllDropsTestCacheEntries() throws Exception {
        IbEngine engine = setup(true);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = CleanCommand.clean(engine, null, true,
                new java.io.PrintStream(out), new java.io.PrintStream(out));
        assertEquals(0, code);
        String json = out.toString();
        assertTrue(json.contains("\"cleaned\""), "json output carries the cleaned list");
        assertFalse(Files.exists(engine.getRepoRoot().resolve("modA/target/classes")));
        assertFalse(Files.exists(engine.getRepoRoot().resolve("modB/target/classes")));
        assertNull(engine.index("modA"));
        assertNull(engine.index("modB"));
        TestCacheStore reloaded = new TestCacheStore(engine);
        assertNull(reloaded.hit("modA", "fpA"),
                "cleaned module's cached green must be dropped");
    }

    /** unknown -pl id and stale model are explicit usage errors, not partial cleans. */
    @Test
    void explicitFailuresNoPartialClean() throws Exception {
        IbEngine engine = setup(false);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        assertEquals(3, CleanCommand.clean(engine, "nope", false,
                new java.io.PrintStream(out), new java.io.PrintStream(out)),
                "unknown -pl id is a usage error");
        // stale model: record a pom hash then change the pom on disk
        engine.model().getPomHashes().put("pom.xml",
                io.nop.ib.model.ModelStore.sha256(engine.getRepoRoot().resolve("pom.xml")));
        Files.writeString(engine.getRepoRoot().resolve("pom.xml"), "<project changed/>");
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        assertEquals(3, CleanCommand.clean(engine, null, false,
                new java.io.PrintStream(out), new java.io.PrintStream(err)),
                "stale model must refuse clean (no partial clean)");
        assertTrue(err.toString().contains("model refresh"),
                "the error names the remedy");
        assertTrue(Files.exists(engine.getRepoRoot().resolve("modA/target/classes")),
                "nothing was deleted under a stale model");
    }
}
