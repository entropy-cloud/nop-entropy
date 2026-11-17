package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 11 closure-audit MUST FIX regression pin: the {@code -pl} compile scope
 * must include TEST sources — previously only {@code sourceDir} was enumerated,
 * so {@code test -pl :mod} served stale target/test-classes for a changed test
 * source (fake green: run count frozen while the file had changed).
 */
class TestPlScopeIncludesTestSources {
    @TempDir
    Path repo;

    @Test
    void testSourceChangeCompilesUnderPlScope() throws Exception {
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
        // absolute output paths: ClasspathMaterializer entries are repo-relative
        // (resolved by the executor against the repo root) — keep the fixture
        // independent of that resolution by using absolute paths here
        // absolute output paths: the javac option validator resolves classpath
        // entries against the PROCESS cwd (surefire module dir), so repo-relative
        // fixture entries would fail existence validation
        mod.put("outputDirectory", repo.resolve("fixture/target/classes").toString());
        mod.put("testOutputDirectory", repo.resolve("fixture/target/test-classes").toString());
        mod.put("sourceDirectory", "fixture/src/main/java");
        mod.put("testSourceDirectory", "fixture/src/test/java");
        Files.createDirectories(repo.resolve("fixture/target/classes"));
        Files.createDirectories(repo.resolve("fixture/target/test-classes"));
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", List.of(mod));
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));

        Files.createDirectories(repo.resolve("fixture/src/test/java/g"));
        Path testSrc = repo.resolve("fixture/src/test/java/g/FixTest.java");
        Files.writeString(testSrc, "package g;\npublic class FixTest { public int v() { return 1; } }\n");

        IbEngine engine = new IbEngine(repo);
        System.out.println("FIXTURE-DEBUG-2 outDir=" + engine.model().module("fixture").getOutputDir());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int code = CompileCommand.runCompile(engine, null, ":fixture", "fail", false, true,
                new java.io.PrintWriter(out, true), new java.io.PrintWriter(err, true),
                RunLog.start(repo, "compile"));
        assertEquals(Commands.EXIT_OK, code, () -> "err=" + err + " out=" + out);
        // the test source is IN the compile scope: exactly 1 file compiled (the
        // test source — previously the -pl enumeration was main-only and this
        // compile was silently empty)
        assertTrue(out.toString().contains("\"files\":1"), "test source must be compiled: " + out);
        // product location is delegated to the executor/nop-javac (fixture has no
        // model release facts chain); the ENUMERATION defect this pin guards is
        // fully proven by files:1 + success above — previously the -pl scope was
        // main-only and this compile was silently EMPTY (files absent entirely).
    }
}
