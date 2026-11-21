package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import io.nop.ib.index.UnitIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2583 Phase-1 regression pins (audit B3): the engine's in-process unit
 * index cache MUST be invalidated after a fast-path compile, so the SAME
 * invocation's later stages (test selection / {@code --cache} fingerprints)
 * see the freshly persisted indexes. Pre-fix, {@code IbEngine.index()}
 * {@code computeIfAbsent} kept the pre-compile snapshot (or a cached null)
 * forever — a newly compiled test class was invisible to the dispatch surface
 * and the cache fingerprint served the stale contentHash (fake green).
 */
class TestPostCompileIndexRefresh {
    @TempDir
    Path repo;

    private IbEngine engine;

    private void writeModel() throws Exception {
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Map<String, Object> mod = new java.util.LinkedHashMap<>();
        mod.put("groupId", "g");
        mod.put("artifactId", "fixture");
        mod.put("version", "1");
        mod.put("packaging", "jar");
        mod.put("basedir", "fixture");
        mod.put("dependencies", List.of());
        mod.put("compilerExecutions", List.of(Map.of("id", "default", "release", "25")));
        mod.put("execExecutionIds", List.of());
        mod.put("surefire", new java.util.LinkedHashMap<>());
        mod.put("externalArtifacts", List.of());
        mod.put("resources", List.of());
        mod.put("testResources", List.of());
        mod.put("outputDirectory", repo.resolve("fixture/target/classes").toString());
        mod.put("testOutputDirectory", repo.resolve("fixture/target/test-classes").toString());
        mod.put("sourceDirectory", "fixture/src/main/java");
        mod.put("testSourceDirectory", "fixture/src/test/java");
        // classpath pre-validation checks entry existence — create the output dirs
        Files.createDirectories(repo.resolve("fixture/target/classes"));
        Files.createDirectories(repo.resolve("fixture/target/test-classes"));
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", List.of(mod));
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));
    }

    private String lastCompileOut;

    private int compile(String testSource, String body) throws Exception {
        Files.createDirectories(repo.resolve("fixture/src/test/java/g"));
        Files.writeString(repo.resolve(testSource),
                "package g;\npublic class FixTest { public int v() { return " + body + "; } }\n");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int code = CompileCommand.runCompile(engine, List.of(testSource), null, "fail", false, true,
                new java.io.PrintWriter(out, true), new java.io.PrintWriter(err, true),
                RunLog.start(repo, "compile"));
        lastCompileOut = out + " / err=" + err;
        return code;
    }

    /**
     * Pin 1 (the B3 shape): the index cache is populated (with null) BEFORE the
     * compile — exactly what a check/test pre-compile does — and must still
     * observe the new test class after the compile inside the same engine.
     */
    @Test
    void sameInvocationSeesNewlyCompiledTestClass() throws Exception {
        writeModel();
        engine = new IbEngine(repo);
        // pre-compile touch: caches null (no index persisted yet)
        assertEquals(null, engine.index("fixture"));
        int code = compile("fixture/src/test/java/g/FixTest.java", "1");
        assertEquals(Commands.EXIT_OK, code, () -> "compile failed");
        UnitIndex fresh = engine.index("fixture");
        assertNotNull(fresh, "post-compile index must not serve the cached pre-compile null");
        // dispatch-surface fact: the newly compiled class is in the index (the
        // test-selection/full-module dispatch reads exactly this index)
        assertNotNull(fresh.classInfo("g.FixTest"),
                "the dispatch surface must contain the newly compiled test class; compile="
                        + lastCompileOut + " classes=" + fresh.getClasses().keySet());
    }

    /** Pin 2: the --cache fingerprint reflects the NEW contentHash after a recompile. */
    @Test
    void cacheFingerprintTracksPostCompileContentHash() throws Exception {
        writeModel();
        engine = new IbEngine(repo);
        assertEquals(null, engine.index("fixture"));
        assertEquals(Commands.EXIT_OK, compile("fixture/src/test/java/g/FixTest.java", "1"));
        TestCacheStore cache = new TestCacheStore(engine);
        String fp1 = cache.fingerprint(engine, engine.model().module("fixture"));
        assertNotNull(fp1);
        // warm the cache with the PRE-compile snapshot (the staleness shape the
        // B3 fix targets: a cached index entry predating the compile)
        engine.index("fixture");
        assertEquals(Commands.EXIT_OK, compile("fixture/src/test/java/g/FixTest.java", "2"));
        String fp2 = cache.fingerprint(engine, engine.model().module("fixture"));
        assertNotEquals(fp1, fp2,
                "a changed source compiled through the fast path must change the fingerprint");
    }
}
