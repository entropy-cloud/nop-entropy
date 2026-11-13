package io.nop.ib.cli;

import io.nop.ib.compile.MavenFallback;
import io.nop.ib.engine.GitChanges;
import io.nop.ib.engine.IbEngine;
import io.nop.ib.json.JsonOut;
import io.nop.ib.lock.BuildLock;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import io.nop.ib.scope.PlanResult;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code nop-ib test} / {@code nop-ib check}: test selection + Surefire delegation
 * (design 05 §7.2 phase 1 / IB-D8). Delegating to Maven's surefire:test on the
 * module's own pom keeps cwd/argLine/system-property semantics faithful; the
 * selection set is nop-ib's. Run tests take the shared build lock; `mvnq` applies
 * the same lock, so one protocol governs both.
 *
 * <p>Selection re-runs after a Maven fallback compile (design 01 §13.3: a fallback
 * compile invalidates the pre-fallback selection).
 */
public final class TestCommand {
    private static final Pattern SUREFIRE_SUMMARY =
            Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");

    public static int test(IbEngine engine, List<String> paths, String plModules,
                           List<String> explicitClasses, boolean allowPartial, boolean json,
                           boolean includeCompile, String onFallback,
                           PrintWriter out, PrintWriter err) {
        IbModel model = engine.model();
        List<String> changed = paths != null && !paths.isEmpty() ? paths
                : (plModules != null ? List.of() : GitChanges.changedPaths(engine.getRepoRoot()));
        if (plModules != null && (paths == null || paths.isEmpty()) && explicitClasses.isEmpty()) {
            // -pl module scope with no explicit classes: run each module's full suite
            for (String artifactId : plModules.split(",")) {
                IbModule module = model.module(artifactId.trim());
                if (module == null) {
                    err.println("usage error: -pl module not in reactor: " + artifactId);
                    return Commands.EXIT_USAGE;
                }
                TestRun run = runSurefire(engine, module, null, json, out, err);
                report(engine, run, json, out);
                if (run.exitCode != Commands.EXIT_OK)
                    return run.exitCode;
            }
            return Commands.EXIT_OK;
        }

        // plan scope: compile (optional) + affected tests
        PlanResult plan = engine.planner().plan(changed, engine.stalenessReason(), allowPartial);

        if (includeCompile) {
            int compileExit = CompileCommand.compile(engine, changed, null, onFallback,
                    allowPartial, json, out, err);
            if (compileExit != Commands.EXIT_OK)
                return compileExit;
            // re-judge the selection after compilation (unit indexes refreshed)
            plan = engine.planner().plan(changed, engine.stalenessReason(), allowPartial);
        }

        if (plan.requiresMavenFallback()) {
            // fallback: module full tests via Maven (delegate compile+test to Maven)
            MavenFallback fallback = new MavenFallback(engine.getRepoRoot());
            StringBuilder mout = new StringBuilder();
            StringBuilder merr = new StringBuilder();
            int code = fallback.delegate("-pl " + String.join(",",
                    plan.getCompileModules().isEmpty()
                            ? List.of(model.moduleForPath(changed.get(0)).getArtifactId())
                            : plan.getCompileModules())
                    + " -am test -q", mout, merr);
            TestRun run = new TestRun("maven-full", null);
            run.exitCode = code == MavenFallback.EXIT_LOCK_TIMEOUT ? Commands.EXIT_LOCK_TIMEOUT
                    : (code == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED);
            run.summary = extractSummaries(mout.toString());
            run.output = mout.toString();
            report(engine, run, json, out);
            return run.exitCode;
        }

        if (explicitClasses.isEmpty() && plan.getSelectedTests().isEmpty()
                && plan.getFullTestModules().isEmpty()) {
            out.println(json ? JsonOut.write(Map.of("ok", true, "tests",
                    Map.of("selected", 0, "run", 0, "passed", 0, "failed", 0)))
                    : "no affected tests selected");
            return Commands.EXIT_OK;
        }

        int failures = 0;
        List<TestRun> runs = new ArrayList<>();
        if (!explicitClasses.isEmpty()) {
            // --class mode: classes may be module-qualified (module:Class) or bare
            for (String spec : explicitClasses) {
                String moduleId;
                String className;
                int colon = spec.indexOf(':');
                if (colon > 0) {
                    moduleId = spec.substring(0, colon);
                    className = spec.substring(colon + 1);
                } else {
                    moduleId = null;
                    className = spec;
                }
                IbModule module = moduleId != null ? model.module(moduleId)
                        : findModuleForClass(engine, className);
                if (module == null) {
                    err.println("cannot resolve module for test class: " + spec);
                    return Commands.EXIT_USAGE;
                }
                TestRun run = runSurefire(engine, module, List.of(className), json, out, err);
                runs.add(run);
                if (run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
        } else {
            // full-test modules first (widening), then selected classes per module
            for (String moduleId : plan.getFullTestModules()) {
                IbModule module = model.module(moduleId);
                if (module == null)
                    continue;
                if ("true".equals(module.getSurefireSkipTests())) {
                    runs.add(TestRun.skipped(moduleId, "skipTests=true (model fact)"));
                    continue;
                }
                // no test source tree (model fact): nothing exists to miss — skip honestly
                if (module.getTestSourceDir() == null
                        || !java.nio.file.Files.isDirectory(engine.getRepoRoot().resolve(module.getTestSourceDir()))) {
                    runs.add(TestRun.skipped(moduleId, "no test source directory (model fact)"));
                    continue;
                }
                Path testProducts = module.getTestOutputDir() != null
                        ? engine.getRepoRoot().resolve(module.getTestOutputDir()) : null;
                if (testProducts == null || !java.nio.file.Files.isDirectory(testProducts)) {
                    // widened module without test products: surefire:test has nothing to
                    // run — delegate a real module build+test to Maven (zero-fake-green)
                    TestRun run = runMavenModuleTest(engine, module, json, out, err);
                    runs.add(run);
                    if (run.exitCode != Commands.EXIT_OK)
                        failures++;
                    continue;
                }
                TestRun run = runSurefire(engine, module, null, json, out, err);
                runs.add(run);
                if (run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
            for (Map.Entry<String, List<String>> e : plan.getSelectedTests().entrySet()) {
                IbModule module = model.module(e.getKey());
                if (module == null)
                    continue;
                TestRun run = runSurefire(engine, module, e.getValue(), json, out, err);
                runs.add(run);
                if (run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
        }
        for (TestRun run : runs)
            report(engine, run, json, out);
        return failures == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
    }

    private static IbModule findModuleForClass(IbEngine engine, String className) {
        for (IbModule module : engine.model().getModules()) {
            var index = engine.index(module.getArtifactId());
            if (index != null && index.classInfo(className) != null)
                return module;
        }
        return null;
    }

    /** Module-scoped Maven build+test for widened modules lacking test products. */
    static TestRun runMavenModuleTest(IbEngine engine, IbModule module, boolean json,
                                      PrintWriter out, PrintWriter err) {
        TestRun run = new TestRun(module.getArtifactId(), null);
        run.kind = "maven-module";
        // no outer BuildLock: the mvnq wrapper takes the same .mvn/build.lock itself;
        // acquiring it here (JVM-scoped FileChannel lock) would self-deadlock the child
        MavenFallback fallback = new MavenFallback(engine.getRepoRoot(),
                java.time.Duration.ofMinutes(30));
        StringBuilder mout = new StringBuilder();
        int code = fallback.delegate("-pl :" + module.getArtifactId() + " -am test -q",
                mout, new StringBuilder());
        run.output = mout.toString();
        run.exitCode = code == MavenFallback.EXIT_LOCK_TIMEOUT ? Commands.EXIT_LOCK_TIMEOUT
                : (code == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED);
        run.summary = extractSummaries(mout.toString());
        if (!json && !mout.isEmpty())
            err.println(mout);
        return run;
    }

    static TestRun runSurefire(IbEngine engine, IbModule module, List<String> testClasses,
                               boolean json, PrintWriter out, PrintWriter err) {
        TestRun run = new TestRun(module.getArtifactId(), testClasses);
        StringBuilder args = new StringBuilder("-pl :" + module.getArtifactId() + " surefire:test");
        if (testClasses != null && !testClasses.isEmpty()) {
            args.append(" -Dtest=").append(String.join(",", testClasses));
            args.append(" -DfailIfNoSpecifiedTests=false");
        }
        // no outer BuildLock: mvnq takes .mvn/build.lock itself (see runMavenModuleTest)
        MavenFallback fallback = new MavenFallback(engine.getRepoRoot());
        StringBuilder mout = new StringBuilder();
        int code = fallback.delegate(args.toString(), mout, new StringBuilder());
        run.output = mout.toString();
        run.exitCode = code == MavenFallback.EXIT_LOCK_TIMEOUT ? Commands.EXIT_LOCK_TIMEOUT
                : (code == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED);
        run.summary = extractSummaries(mout.toString());
        if (!json && !mout.isEmpty())
            err.println(mout);
        return run;
    }

    static List<int[]> extractSummaries(String output) {
        List<int[]> out = new ArrayList<>();
        Matcher m = SUREFIRE_SUMMARY.matcher(output);
        while (m.find()) {
            out.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))});
        }
        return out;
    }

    private static void report(IbEngine engine, TestRun run, boolean json, PrintWriter out) {
        int total = 0, failed = 0, errors = 0, skipped = 0;
        for (int[] s : run.summary) {
            total += s[0];
            failed += s[1];
            errors += s[2];
            skipped += s[3];
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", run.exitCode == Commands.EXIT_OK);
        m.put("module", run.module);
        m.put("kind", run.kind);
        m.put("selected", run.selectedClasses);
        m.put("run", total);
        m.put("failed", failed);
        m.put("errors", errors);
        m.put("skipped", skipped);
        if (run.exitCode == Commands.EXIT_LOCK_TIMEOUT)
            m.put("lockTimeout", true);
        out.println(json ? JsonOut.write(m)
                : String.format("[nop-ib] %s tests: run=%d failed=%d errors=%d skipped=%d%s",
                        run.module, total, failed, errors, skipped,
                        run.selectedClasses != null ? " (selected=" + run.selectedClasses.size() + ")" : " (full)"));
    }

    static final class TestRun {
        final String module;
        final List<String> selectedClasses;
        String kind = "surefire";
        int exitCode = Commands.EXIT_OK;
        List<int[]> summary = new ArrayList<>();
        String output = "";

        TestRun(String module, List<String> selectedClasses) {
            this.module = module;
            this.selectedClasses = selectedClasses;
        }

        static TestRun skipped(String module, String reason) {
            TestRun run = new TestRun(module, null);
            run.kind = "skipped";
            run.output = reason;
            return run;
        }
    }
}
