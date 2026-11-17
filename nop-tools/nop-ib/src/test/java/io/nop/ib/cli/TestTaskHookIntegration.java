package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 12 Phase 3 pins: the task execution path is WIRED into runCompile
 * (runtime call, not mere class existence — plan guide Minimum Rules #23),
 * the GEN_OR_MODEL fallback suppression is gated on a declared before-compile
 * task (modules without one keep the whole-build fallback), and a failing task
 * delegates to Maven with the task failure as the explicit reason (design 08
 * IB-T5/T6).
 */
class TestTaskHookIntegration {
    @TempDir
    Path repo;

    static final String HELPER = """
            import java.nio.file.Files;
            import java.nio.file.Path;
            public class GenHelper {
                public static void main(String[] args) throws Exception {
                    Path out = Path.of(args[0]);
                    Files.createDirectories(out.getParent());
                    Files.writeString(out, "generated");
                }
            }
            """;
    static Path helperClasses;

    @Test
    void wiredTaskRunsInCompileAndFallbackSuppressionGatesOnDeclaration() throws Exception {
        compileHelper();
        // module with a declared before-compile task
        Path modA = repo.resolve("modA");
        Files.createDirectories(modA.resolve("model"));
        Files.writeString(modA.resolve("nop-ib.tasks.json"), """
                [{"name": "gen", "trigger": "before-compile",
                  "inputs": ["model/*.xgen"], "outputs": ["src/main/java/**/_gen/**"],
                  "exec": {"type": "java", "mainClass": "GenHelper",
                           "arguments": ["src/main/java/_gen/Gen.java"], "classpathScope": "compile"}}]
                """);
        Files.writeString(modA.resolve("model/gen.xgen"), "template v1");
        // module WITHOUT any task declaration (fallback must remain for it)
        Path modB = repo.resolve("modB");
        Files.createDirectories(modB.resolve("model"));
        Files.writeString(modB.resolve("model/gen.xgen"), "template v1");
        // L1 heuristic surface: a gen-*.xgen template in the basedir escalates the module
        Files.createDirectories(modB.resolve("tpl"));
        Files.writeString(modB.resolve("tpl/gen-model.xgen"), "xgen template");

        IbEngine engine = engineWithModules();

        // suppression pin 1: module WITH a before-compile task does not fall back
        var planner = engine.planner();
        var plan = planner.plan(List.of("modA/model/gen.xgen"), null, false);
        assertFalse(plan.requiresMavenFallback(),
                "declared before-compile task must suppress the GEN_OR_MODEL whole-build fallback");
        assertTrue(plan.getFallbackReason() == null || plan.getFallbackReason().isEmpty());

        // suppression pin 2: module WITHOUT a task keeps the fallback
        var planB = engine.planner().plan(List.of("modB/model/gen.xgen"), null, false);
                
        assertTrue(planB.requiresMavenFallback(),
                "GEN_OR_MODEL change without a declared task still falls back to Maven");

        // wiring pin: runCompile executes the task and the generated file lands
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        io.nop.ib.cli.RunLog log = io.nop.ib.cli.RunLog.start(repo, "compile");
        int code = CompileCommand.runCompile(engine, List.of("modA/model/gen.xgen"), null,
                "fail", false, true, new java.io.PrintWriter(outBuf, true),
                new java.io.PrintWriter(outBuf, true), log);
        log.close();
        assertEquals(0, code, outBuf.toString());
        assertTrue(Files.exists(modA.resolve("src/main/java/_gen/Gen.java")),
                "the declared task must have executed inside the compile path");
        String json = outBuf.toString();
        assertTrue(json.contains("\"tasks\""), "feedback JSON carries the tasks record");
        assertTrue(json.contains("\"executed\""), "task recorded as executed, not silently skipped");

        // incremental: second run skips the fork
        java.io.ByteArrayOutputStream out2 = new java.io.ByteArrayOutputStream();
        io.nop.ib.cli.RunLog log2 = io.nop.ib.cli.RunLog.start(repo, "compile");
        int code2 = CompileCommand.runCompile(engine, List.of("modA/model/gen.xgen"), null,
                "fail", false, true, new java.io.PrintWriter(out2, true),
                new java.io.PrintWriter(out2, true), log2);
        log2.close();
        assertEquals(0, code2);
    }

    @Test
    void failingTaskDelegatesToMavenWithExplicitReason() throws Exception {
        compileHelper();
        Path modA = repo.resolve("modA");
        Files.createDirectories(modA.resolve("model"));
        Files.writeString(modA.resolve("nop-ib.tasks.json"), """
                [{"name": "gen", "trigger": "before-compile",
                  "inputs": ["model/*.xgen"], "outputs": ["src/main/java/**/_gen/**"],
                  "exec": {"type": "java", "mainClass": "NoSuchMainClass"}}]
                """);
        Files.writeString(modA.resolve("model/gen.xgen"), "template v1");
        IbEngine engine = engineWithModules();

        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream errBuf = new java.io.ByteArrayOutputStream();
        io.nop.ib.cli.RunLog log = io.nop.ib.cli.RunLog.start(repo, "compile");
        int code = CompileCommand.runCompile(engine, List.of("modA/model/gen.xgen"), null,
                "fail", false, true, new java.io.PrintWriter(outBuf, true),
                new java.io.PrintWriter(errBuf, true), log);
        log.close();
        // Maven fallback itself will fail (no mvn project here) but the failure must
        // be EXPLICIT: the stderr names the task, the fallback reason carries it
        assertTrue(errBuf.toString().contains("task failed"), () -> errBuf.toString());
        assertTrue(errBuf.toString().contains("gen"), "reason names the failed task");
        assertTrue(errBuf.toString().contains("NoSuchMainClass"), "stderr tail is surfaced");
    }

    private IbEngine engineWithModules() throws Exception {
        for (String name : new String[]{"modA", "modB"}) {
            Path classes = repo.resolve(name + "/target/classes");
            Files.createDirectories(classes);
            Files.copy(helperClasses.resolve("GenHelper.class"), classes.resolve("GenHelper.class"));
            Files.createDirectories(repo.resolve(name));
        }
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
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
        }
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("formatVersion", "1");
        model.put("repoRoot", repo.toString());
        model.put("pomHashes", new java.util.LinkedHashMap<>());
        model.put("modules", mods);
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve(".nop-ib/model.json"),
                io.nop.ib.json.JsonOut.write(model));
        return new IbEngine(repo);
    }

    private void compileHelper() throws Exception {
        if (helperClasses != null)
            return;
        Path tmp = Files.createTempDirectory("ib-helper");
        Path src = tmp.resolve("GenHelper.java");
        Files.writeString(src, HELPER);
        Path classes = Files.createDirectories(tmp.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> objs = fm.getJavaFileObjectsFromStrings(List.of(src.toString()));
            var task = compiler.getTask(null, null, null, List.of("-d", classes.toString()), null, objs);
            assertTrue(task.call(), "helper compiled");
        }
        helperClasses = classes;
    }
}
