package io.nop.ib.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 11 item 2 pins: whitelisted real-Maven passthrough. Whitelist hits
 * delegate through the injectable Runner seam (no real install runs in tests);
 * non-whitelisted goals are rejected with exit 3 and the allowed set in the
 * message; the real mvn exit code passes through verbatim.
 */
class TestMvnPassthrough {

    @AfterEach
    void restoreSeam() {
        MvnCommand.runner = (root, args, out, err) ->
                new io.nop.ib.compile.MavenFallback(root).delegate(args, out, err);
        MvnCommand.preCompileFn = MvnCommand::defaultPreCompile;
    }

    /** stub the pre-compile green (no real compile in tests that don't pin it). */
    private void stubPreCompileGreen() {
        MvnCommand.preCompileFn = (root, plModules, out, err) -> 0;
    }

    private record RunOut(int exit, String out, String err) {
    }

    private RunOut run(String... args) throws Exception {
        Path repo = Files.createTempDirectory("ib-mvn");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = io.nop.ib.NopIbMain.run(
                args, repo, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new RunOut(exit, out.toString(), err.toString());
    }

    @Test
    void whitelistedGoalDelegatesThroughSeam() throws Exception {
        List<String> received = new ArrayList<>();
        AtomicBoolean invoked = new AtomicBoolean(false);
        MvnCommand.runner = (root, args, out, err) -> {
            invoked.set(true);
            received.addAll(args);
            try {
                out.append("fake maven output line\n");
            } catch (java.io.IOException e) {
                throw new io.nop.ib.NopIbException("fake runner io error", e);
            }
            return 0;
        };
        RunOut r = run("mvn", "--json", "-pl", ":some-module", "install");
        assertEquals(0, r.exit());
        assertTrue(invoked.get(), "delegation must go through the Runner seam");
        assertEquals(List.of("-pl", ":some-module", "install"), received,
                "mvn args must reach the runner verbatim (-pl selector intact)");
        assertTrue(r.out().contains("\"cmd\":\"mvn\""));
        assertTrue(r.out().contains("\"exit\":0"));
        assertTrue(r.out().contains("fake maven output line"), "tail passthrough");
    }

    @Test
    void dependencyAndSurefireGoalsAreWhitelisted() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MvnCommand.runner = (root, args, out, err) -> {
            invoked.set(true);
            return 0;
        };
        assertEquals(0, run("mvn", "--json", "dependency:tree").exit());
        stubPreCompileGreen();
        assertEquals(0, run("mvn", "--json", "surefire:test").exit());
        assertEquals(0, run("mvn", "--json", "clean", "package").exit());
        assertTrue(invoked.get());
    }

    @Test
    void surefireGoalRunsPreCompileAndPassesModuleScope() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MvnCommand.runner = (root, args, out, err) -> {
            invoked.set(true);
            return 0;
        };
        java.util.List<String> scopes = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        MvnCommand.preCompileFn = (root, plModules, out, err) -> {
            calls.incrementAndGet();
            scopes.add(plModules);
            return 0;
        };
        RunOut r = run("mvn", "--json", "-pl", ":some-module", "surefire:test");
        assertEquals(0, r.exit());
        assertEquals(1, calls.get(), "surefire passthrough must pre-compile exactly once");
        assertEquals(":some-module", scopes.get(0),
                "-pl value passes through (runCompile strips the : coordinate form itself)");
        assertTrue(invoked.get(), "green pre-compile must still delegate to Maven");
    }

    @Test
    void surefireGoalWithoutPlPreCompilesRepoChangeSet() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MvnCommand.runner = (root, args, out, err) -> {
            invoked.set(true);
            return 0;
        };
        java.util.List<String> scopes = new java.util.ArrayList<>();
        MvnCommand.preCompileFn = (root, plModules, out, err) -> {
            scopes.add(plModules);
            return 0;
        };
        assertEquals(0, run("mvn", "--json", "surefire:test").exit());
        assertEquals(1, scopes.size());
        assertNull(scopes.get(0), "no -pl -> null module scope (compile falls back to the change set)");
        assertTrue(invoked.get());
    }

    @Test
    void redPreCompileBlocksSurefireDelegation() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MvnCommand.runner = (root, args, out, err) -> {
            invoked.set(true);
            return 0;
        };
        MvnCommand.preCompileFn = (root, plModules, out, err) -> 1;
        RunOut r = run("mvn", "--json", "surefire:test");
        assertEquals(1, r.exit(), "a red pre-compile passes its exit code through");
        assertFalse(invoked.get(), "zero fake green: red pre-compile must block surefire dispatch");
        assertTrue(r.out().contains("pre-compile failed"), "the block reason must be visible");
    }

    @Test
    void nonSurefireGoalsSkipPreCompile() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MvnCommand.runner = (root, args, out, err) -> {
            invoked.set(true);
            return 0;
        };
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        MvnCommand.preCompileFn = (root, plModules, out, err) -> {
            calls.incrementAndGet();
            return 0;
        };
        assertEquals(0, run("mvn", "--json", "dependency:tree").exit());
        assertEquals(0, run("mvn", "--json", "clean", "package").exit());
        assertEquals(0, calls.get(), "install/package/clean/dependency:* compile themselves — no pre-compile");
        assertTrue(invoked.get());
    }

    @Test
    void nonWhitelistedGoalRejectedWithExit3AndAllowedSet() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean(false);
        MvnCommand.runner = (root, args, out, err) -> {
            invoked.set(true);
            return 0;
        };
        RunOut r = run("mvn", "--json", "deploy");
        assertEquals(Commands.EXIT_USAGE, r.exit(), "whitelist violation exits 3");
        assertFalse(invoked.get(), "rejected goal must never reach Maven");
        assertTrue(r.err().contains("deploy"));
        assertTrue(r.err().contains("install") && r.err().contains("surefire:*"),
                "rejection message lists the allowed set");
        // reactor selectors of -pl are not goals — never trigger the rejection
        assertEquals(0, run("mvn", "--json", "-pl", ":mod-a", "install").exit());
    }

    @Test
    void realMavenExitCodePassesThroughVerbatim() throws Exception {
        MvnCommand.runner = (root, args, out, err) -> 7;
        assertEquals(7, run("mvn", "package").exit(),
                "the mvn exit code is passed through, not remapped");
    }
}
