package io.nop.ib.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2583 Phase-1 regression pin (audit Major): {@code compile -pl} must not
 * short-circuit the whole scope when the FIRST module has no main/test sources.
 * Pre-fix, {@code -pl <no-source-parent>,<has-source-child>} returned EXIT_OK
 * with the child's compile silently skipped (fake green). Mutation probe
 * evidence recorded in the plan Closure section.
 */
class TestPlNoSourceModuleNoShortCircuit {
    @TempDir
    Path repo;

    private Map<String, Object> module(String artifactId, String basedir, boolean withDirs,
                                       Path absOut, Path absTestOut) {
        Map<String, Object> mod = new java.util.LinkedHashMap<>();
        mod.put("groupId", "g");
        mod.put("artifactId", artifactId);
        mod.put("version", "1");
        mod.put("packaging", "jar");
        mod.put("basedir", basedir);
        mod.put("dependencies", List.of());
        if (withDirs) {
            mod.put("compilerExecutions", List.of(Map.of("id", "default", "release", "25")));
            mod.put("outputDirectory", absOut.toString());
            mod.put("testOutputDirectory", absTestOut.toString());
            mod.put("sourceDirectory", basedir + "/src/main/java");
            mod.put("testSourceDirectory", basedir + "/src/test/java");
        } else {
            // declares the default source roots but has NO sources on disk
            mod.put("compilerExecutions", List.of());
        }
        mod.put("execExecutionIds", List.of());
        mod.put("surefire", new java.util.LinkedHashMap<>());
        mod.put("externalArtifacts", List.of());
        mod.put("resources", List.of());
        mod.put("testResources", List.of());
        return mod;
    }

    @Test
    void noSourceParentDoesNotSkipSourceChild() throws Exception {
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Files.createDirectories(repo.resolve("child/target/classes"));
        Files.createDirectories(repo.resolve("child/target/test-classes"));
        Files.createDirectories(repo.resolve("child/src/test/java/g"));
        Files.writeString(repo.resolve("child/src/test/java/g/ChildTest.java"),
                "package g;\npublic class ChildTest { public int v() { return 1; } }\n");
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", List.of(
                module("parent", "parent", false, null, null),
                module("child", "child", true,
                        repo.resolve("child/target/classes"),
                        repo.resolve("child/target/test-classes"))));
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));

        var engine = new io.nop.ib.engine.IbEngine(repo);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int code = CompileCommand.runCompile(engine, null, ":parent,:child", "fail", false, true,
                new java.io.PrintWriter(out, true), new java.io.PrintWriter(err, true),
                RunLog.start(repo, "compile"));
        assertEquals(Commands.EXIT_OK, code, () -> "err=" + err + " out=" + out);
        assertTrue(out.toString().contains("\"files\":1"),
                "the has-source child module must actually be compiled after the "
                        + "no-source parent is enumerated: " + out);
    }
}
