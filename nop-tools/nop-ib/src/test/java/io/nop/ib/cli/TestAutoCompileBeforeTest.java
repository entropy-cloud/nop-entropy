package io.nop.ib.cli;

import io.nop.ib.engine.IbEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 11 pins (owner directive 2026-11-18): {@code nop-ib test} defaults to a
 * pre-compile through the SAME channel as check ({@link CompileCommand#runCompile}
 * via the {@link TestCommand#compileFn} seam) over a scope mirroring the test
 * scope; {@code --no-compile} is the explicit opt-out; a red compile blocks test
 * dispatch (zero fake green — daily log 2026-11-17: bare {@code test} ran stale
 * target/classes, 114 fake vs 118 real); the JSON contract carries
 * {@code compiled: true|false|"skipped"}.
 */
class TestAutoCompileBeforeTest {

    private Path repo;
    private IbEngine engine;
    private int compileCalls;
    private List<String> compilePlScopes;
    private int stubExit;

    @BeforeEach
    void setup() throws Exception {
        repo = Files.createTempDirectory("ib-autocompile");
        Files.createDirectories(repo.resolve(".nop-ib"));
        Files.writeString(repo.resolve(".gitignore"), "_tmp/\n.nop-ib/\n");
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
        engine = new IbEngine(repo);
        io.nop.ib.index.UnitIndex index = io.nop.ib.index.UnitIndex.empty("fixture");
        io.nop.ib.index.ClassInfo info = new io.nop.ib.index.ClassInfo();
        info.setClassName("g.FixTest");
        info.setContentHash("h1");
        info.setAbiDigest("abi1");
        info.setSourceFile("src/test/java/g/FixTest.java");
        index.put(info);
        engine.getUnitStore().save(index);
        // clean git repo so GitChanges.changedPaths returns [] deterministically
        git("init");
        git("config", "user.email", "t@t");
        git("config", "user.name", "t");
        git("add", "-A");
        git("commit", "-m", "init");
        compileCalls = 0;
        compilePlScopes = new ArrayList<>();
        stubExit = 0;
        TestCommand.compileFn = (eng, paths, pl, onFallback, allowPartial, json, out, err, log) -> {
            compileCalls++;
            if (pl != null)
                compilePlScopes.add(pl);
            return stubExit;
        };
    }

    @AfterEach
    void restore() {
        TestCommand.compileFn = CompileCommand::runCompile;
    }

    private void git(String... args) throws Exception {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "git";
        System.arraycopy(args, 0, cmd, 1, args.length);
        new ProcessBuilder(cmd).directory(repo.toFile())
                .redirectErrorStream(true).start().waitFor();
    }

    private String outBuf;

    private int run(boolean noCompile, boolean json, List<String> paths, String pl,
                    List<String> classes) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw, true);
        PrintWriter err = new PrintWriter(new StringWriter(), true);
        RunLog log = RunLog.start(repo, "test");
        try {
            return TestCommand.runTest(engine, paths, pl, classes, false, json,
                    false /*includeCompile: plain `test`, not `check`*/, "maven", false,
                    "junit", null, false, noCompile, out, err, log);
        } finally {
            log.close();
            outBuf = sw.toString();
        }
    }

    /** pin 1: default (no flag) compiles BEFORE dispatching tests. */
    @Test
    void defaultPathCompilesBeforeDispatch() {
        int code = run(false, true, List.of(), ":fixture", List.of());
        assertEquals(Commands.EXIT_OK, code);
        assertEquals(1, compileCalls, "default test must run the pre-compile exactly once");
        String out = outBuf;
        assertTrue(out.contains("\"compiled\":true"), "compiled=true in JSON: " + out);
        // ordering: the compile line precedes the (skipped) module dispatch report
        assertTrue(out.indexOf("\"compiled\":true") < out.indexOf("\"kind\":\"skipped\""),
                "compile must happen before test dispatch");
    }

    /** pin 2: --no-compile skips the pre-compile entirely (v1 behavior, explicit). */
    @Test
    void noCompileSkipsPreCompile() {
        int code = run(true, true, List.of(), ":fixture", List.of());
        assertEquals(Commands.EXIT_OK, code);
        assertEquals(0, compileCalls, "--no-compile must not invoke the compile channel");
        assertTrue(outBuf.contains("\"compiled\":false"),
                "--no-compile must be explicit in JSON, never silent: " + outBuf);
    }

    /** pin 3: a red compile ends the run with the compile exit — no test dispatch. */
    @Test
    void redCompileBlocksDispatch() {
        stubExit = Commands.EXIT_FAILED;
        int code = run(false, true, List.of(), ":fixture", List.of());
        assertEquals(Commands.EXIT_FAILED, code, "compile exit must propagate as the test exit");
        assertEquals(1, compileCalls);
        assertFalse(outBuf.contains("\"kind\""),
                "no module test report may appear after a red compile: " + outBuf);
    }

    /** pin 4a: JSON compiled=skipped when nothing changed in scope. */
    @Test
    void nothingToCompileReportsSkipped() {
        int code = run(false, true, List.of(), null, List.of());
        assertEquals(Commands.EXIT_OK, code);
        assertEquals(0, compileCalls, "empty change set must not invoke the compile channel");
        assertTrue(outBuf.contains("\"compiled\":\"skipped\""), outBuf);
    }

    /** pin 4b: --class scope compiles the owning modules of the requested classes. */
    @Test
    void classScopeCompilesOwningModule() {
        stubExit = Commands.EXIT_FAILED; // stop right after the pre-compile (no dispatch)
        int code = run(false, false, List.of(), null, List.of("g.FixTest"));
        assertEquals(Commands.EXIT_FAILED, code);
        assertEquals(1, compileCalls);
        assertEquals(List.of("fixture"), compilePlScopes,
                "--class without module prefix must resolve the owning module as compile scope");
    }
}
