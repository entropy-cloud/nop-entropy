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
 * Silent-empty plan diagnostic pin: an existing java path whose plan produces
 * zero compiled modules must NOT come out as a bare green "compiled: []" —
 * the run carries an explicit warning naming the paths (fake-green guard).
 * Reproduction shape: the module's model facts lack an output dir, so the
 * executor's pass-1 skip leaves the outcome empty while the path exists.
 */
class TestCompilePlanEmptyDiagnostic {
    @TempDir
    Path repo;

    @Test
    void existingJavaPlannedToNothingCarriesWarning() throws Exception {
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Files.createDirectories(repo.resolve("m/src/main/java/g"));
        Files.writeString(repo.resolve("m/src/main/java/g/Sample.java"),
                "package g;\npublic class Sample { public int v() { return 1; } }\n");
        // module facts WITHOUT output directories: executor pass-1 skips it,
        // plan yields compile files but the outcome compiles nothing
        Map<String, Object> mod = new java.util.LinkedHashMap<>();
        mod.put("groupId", "g");
        mod.put("artifactId", "m");
        mod.put("version", "1");
        mod.put("packaging", "jar");
        mod.put("basedir", "m");
        mod.put("dependencies", List.of());
        mod.put("compilerExecutions", List.of(Map.of("id", "default", "release", "25")));
        mod.put("sourceDirectory", "m/src/main/java");
        mod.put("execExecutionIds", List.of());
        mod.put("surefire", new java.util.LinkedHashMap<>());
        mod.put("externalArtifacts", List.of());
        mod.put("resources", List.of());
        mod.put("testResources", List.of());
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", List.of(mod));
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));

        var engine = new io.nop.ib.engine.IbEngine(repo);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int code = CompileCommand.runCompile(engine,
                List.of("m/src/main/java/g/Sample.java"), null, "fail", false, true,
                new java.io.PrintWriter(out, true), new java.io.PrintWriter(err, true),
                RunLog.start(repo, "compile"));
        assertEquals(Commands.EXIT_OK, code, () -> "err=" + err + " out=" + out);
        String stdout = out.toString();
        assertTrue(stdout.contains("model/module facts missing?"), () -> "out=" + stdout);
        assertTrue(stdout.contains("m/src/main/java/g/Sample.java"), () -> "out=" + stdout);
        assertTrue(stdout.contains("\"compiledModules\":[]"), () -> "out=" + stdout);
    }

    @Test
    void nothingToCompileWithoutJavaPathsStaysSilent() throws Exception {
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Files.createDirectories(repo.resolve("m"));
        Files.writeString(repo.resolve("m/README.md"), "doc only\n");
        Map<String, Object> mod = new java.util.LinkedHashMap<>();
        mod.put("groupId", "g");
        mod.put("artifactId", "m");
        mod.put("version", "1");
        mod.put("packaging", "jar");
        mod.put("basedir", "m");
        mod.put("dependencies", List.of());
        mod.put("execExecutionIds", List.of());
        mod.put("surefire", new java.util.LinkedHashMap<>());
        mod.put("externalArtifacts", List.of());
        mod.put("resources", List.of());
        mod.put("testResources", List.of());
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", List.of(mod));
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));

        var engine = new io.nop.ib.engine.IbEngine(repo);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int code = CompileCommand.runCompile(engine,
                List.of("m/README.md"), null, "fail", false, true,
                new java.io.PrintWriter(out, true), new java.io.PrintWriter(err, true),
                RunLog.start(repo, "compile"));
        assertEquals(Commands.EXIT_OK, code, () -> "err=" + err + " out=" + out);
        String stdout = out.toString();
        assertTrue(stdout.contains("\"compiledModules\":[]"), () -> "out=" + stdout);
        assertTrue(!stdout.contains("model/module facts missing?"),
                () -> "non-java path must not warn: out=" + stdout);
    }
}
