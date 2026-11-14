package io.nop.ib.cli;

import io.nop.ib.compile.ClasspathMaterializer;
import io.nop.ib.compile.MavenFallback;
import io.nop.ib.junit.JUnitForkExecutor;
import io.nop.ib.engine.GitChanges;
import io.nop.ib.engine.IbEngine;
import io.nop.ib.json.JsonOut;
import io.nop.ib.lock.BuildLock;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import io.nop.ib.scope.PlanResult;

import java.io.PrintWriter;
import java.nio.file.Files;
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
                           boolean includeCompile, String onFallback, boolean rerunFailed,
                           String runner,
                           PrintWriter out, PrintWriter err) {
        IbModel model = engine.model();
        List<String> changed = paths != null && !paths.isEmpty() ? paths
                : (plModules != null ? List.of() : GitChanges.changedPaths(engine.getRepoRoot()));

        // --rerun-failed: rerun the classes recorded by the previous failed run
        if (rerunFailed) {
            Map<String, List<String>> failed = readLastFailed(engine);
            if (failed.isEmpty()) {
                out.println(json ? JsonOut.write(Map.of("ok", true, "tests",
                        Map.of("selected", 0, "note", "no recorded failed tests")))
                        : "no recorded failed tests");
                return Commands.EXIT_OK;
            }
            int failures = 0;
            for (Map.Entry<String, List<String>> e : failed.entrySet()) {
                IbModule module = model.module(e.getKey());
                if (module == null)
                    continue;
                TestRun run = runSurefire(engine, module, e.getValue(), json, out, err);
                recordLastFailed(engine, module.getArtifactId(), run);
                report(engine, run, json, out);
                if (run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
            return failures == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
        }
        if (plModules != null && (paths == null || paths.isEmpty()) && explicitClasses.isEmpty()) {
            // -pl module scope with no explicit classes: run each module's full suite
            for (String artifactId : plModules.split(",")) {
                IbModule module = model.module(artifactId.trim());
                if (module == null) {
                    err.println("usage error: -pl module not in reactor: " + artifactId);
                    return Commands.EXIT_USAGE;
                }
                TestRun run = runModuleTests(engine, module, null, runner, json, out, err);
                report(engine, run, json, out);
                if (run.exitCode != Commands.EXIT_OK)
                    return run.exitCode;
            }
            return Commands.EXIT_OK;
        }

        // --class mode is an explicit execution request: independent of the working
        // tree change set and of plan/fallback state (roadmap item 22)
        if (!explicitClasses.isEmpty()) {
            int failures = 0;
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
                TestRun run = runModuleTests(engine, module, List.of(className), runner, json, out, err);
                recordLastFailed(engine, module.getArtifactId(), run);
                report(engine, run, json, out);
                if (run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
            return failures == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
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
            return Commands.EXIT_OK; // handled above
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
                TestRun run = runModuleTests(engine, module, null, runner, json, out, err);
                recordLastFailed(engine, moduleId, run);
                runs.add(run);
                if (run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
            for (Map.Entry<String, List<String>> e : plan.getSelectedTests().entrySet()) {
                IbModule module = model.module(e.getKey());
                if (module == null)
                    continue;
                TestRun run = runModuleTests(engine, module, e.getValue(), runner, json, out, err);
                recordLastFailed(engine, e.getKey(), run);
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

    /**
     * Fast-path execution: forked JUnit JVM (design 02 contract). Falls back to
     * surefire ONLY when the junit setup cannot be satisfied (no platform artifacts
     * on the classpath / launcher jar unavailable) — the fallback reason is
     * explicit in the run kind. Missing test classes trigger one test-compile
     * retry via Maven (adaptive expansion, design 01 §十).
     */
    static TestRun runModuleTests(IbEngine engine, IbModule module, List<String> classes,
                                  String runner, boolean json, PrintWriter out, PrintWriter err) {
        if (!"junit".equals(runner))
            return runSurefire(engine, module, classes, json, out, err);
        if (classes == null) {
            // full-module fork: enumerate test classes from the unit index
            var index = engine.index(module.getArtifactId());
            if (index == null) {
                TestRun run = runSurefire(engine, module, null, json, out, err);
                run.kind = "surefire(index-missing)";
                return run;
            }
            classes = new ArrayList<>();
            for (var info : index.testClasses())
                classes.add(info.getClassName());
            if (classes.isEmpty()) {
                TestRun run = TestRun.skipped(module.getArtifactId(), "no test classes in unit index");
                return run;
            }
        }
        ClasspathMaterializer materializer = new ClasspathMaterializer(engine.model());
        ClasspathMaterializer.Materialized cp = materializer.testClasspath(module);
        cp.assertNoIntraProjectJars(engine.model());
        if (!cp.complete()) {
            TestRun run = runSurefire(engine, module, classes, json, out, err);
            run.kind = "surefire(classpath-incomplete)";
            return run;
        }
        JUnitForkExecutor executor = new JUnitForkExecutor(engine.model());
        List<String> forkWarnings = new ArrayList<>();
        JUnitForkExecutor.ForkResult fork =
                executeForks(executor, module, cp, classes, forkWarnings);
        if (fork.exitCode() == -3) {
            // junit setup unsatisfiable -> explicit fallback
            TestRun run = runSurefire(engine, module, classes, json, out, err);
            run.kind = "surefire(" + String.join("; ", fork.warnings()) + ")";
            return run;
        }
        TestRun run = new TestRun(module.getArtifactId(), classes);
        run.kind = "junit-fork";
        run.warnings.addAll(fork.warnings());
        Map<String, Object> summary = fork.summary();
        int tests = numOf(summary.get("tests"));
        int failed = numOf(summary.get("failures"));
        int skipped = numOf(summary.get("skipped"));
        run.summary.add(new int[]{tests, failed, 0, skipped});
        Object failedList = summary.get("failed");
        if (failedList instanceof Iterable<?> iter) {
            StringBuilder sb = new StringBuilder();
            for (Object o : iter)
                sb.append(o).append('\n');
            run.output = sb.toString();
        }
        if (fork.exitCode() == 2 && !summary.isEmpty()) {
            // missing classes: one test-compile retry via Maven
            io.nop.ib.compile.MavenFallback fallback =
                    new io.nop.ib.compile.MavenFallback(engine.getRepoRoot());
            StringBuilder mout = new StringBuilder();
            int code = fallback.delegate("-pl :" + module.getArtifactId() + " test-compile -q",
                    mout, new StringBuilder());
            if (code == 0) {
                fork = executor.execute(module, cp, classes == null ? List.of() : classes);
                run.summary.clear();
                int t2 = numOf(fork.summary().get("tests"));
                int f2 = numOf(fork.summary().get("failures"));
                int s2 = numOf(fork.summary().get("skipped"));
                run.summary.add(new int[]{t2, f2, 0, s2});
                run.exitCode = f2 > 0 ? Commands.EXIT_FAILED : Commands.EXIT_OK;
                return run;
            }
        }
        run.exitCode = fork.exitCode() == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
        return run;
    }

    private static int numOf(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    /**
     * Per-class forks with bounded parallelism (mirrors surefire forkCount=4 /
     * parallel=classes): isolation across classes avoids cross-class pollution in
     * a single shared JVM, and the batch watchdog applies per fork.
     */
    /**
     * Process-level isolation (one fork per class) means parallelism is bounded by
     * CPU/memory, not by JUnit statics. Default: cores-1; override via
     * --fork-parallelism or system property nop.ib.forkParallelism. Heavy
     * TestContainers-style classes still serialize themselves on external
     * resources; a hung class is killed by the per-fork watchdog and cannot block
     * the rest of the module.
     */
    private static final int FORK_PARALLELISM =
            Math.max(1, Integer.getInteger("nop.ib.forkParallelism",
                    Runtime.getRuntime().availableProcessors() - 1));
    private static int forkParallelismOverride = Integer.getInteger("nop.ib.forkParallelism", 0);

    public static void setForkParallelism(int n) {
        if (n > 0)
            forkParallelismOverride = n;
    }

    private static JUnitForkExecutor.ForkResult executeForks(JUnitForkExecutor executor,
                                                             IbModule module,
                                                             ClasspathMaterializer.Materialized cp,
                                                             List<String> classes,
                                                             List<String> warnings) {
        List<String> list = classes == null ? List.of() : classes;
        if (list.size() <= 1)
            return executor.execute(module, cp, list);
        int parallelism = forkParallelismOverride > 0 ? forkParallelismOverride : FORK_PARALLELISM;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(parallelism);
        java.util.List<java.util.concurrent.Future<JUnitForkExecutor.ForkResult>> futures = new ArrayList<>();
        for (String cls : list) {
            futures.add(pool.submit(() -> executor.execute(module, cp, List.of(cls))));
        }
        pool.shutdown();
        int failures = 0;
        int tests = 0;
        int skipped = 0;
        boolean setupFailed = false;
        boolean timedOut = false;
        List<String> timedOutClasses = new ArrayList<>();
        List<Map<String, String>> failedDetails = new ArrayList<>();
        List<String> missingClasses = new ArrayList<>();
        try {
            for (int fi = 0; fi < futures.size(); fi++) {
                var f = futures.get(fi);
                JUnitForkExecutor.ForkResult r = f.get();
                for (String w : r.warnings())
                    if (!warnings.contains(w))
                        warnings.add(w);
                if (r.exitCode() == -3)
                    setupFailed = true;
                Object t = r.summary().get("tests");
                Object fl = r.summary().get("failures");
                Object sk = r.summary().get("skipped");
                if (t instanceof Number n)
                    tests += n.intValue();
                if (sk instanceof Number n)
                    skipped += n.intValue();
                if (fl instanceof Number n)
                    failures += n.intValue();
                if (r.summary().get("timedOut") != null) {
                    timedOut = true;
                    timedOutClasses.add(fi < list.size() ? list.get(fi) : "?");
                }
                if (r.summary().get("missingClasses") instanceof Iterable<?> iter) {
                    for (Object o : iter)
                        missingClasses.add(String.valueOf(o));
                }
                if (r.summary().get("failed") instanceof Iterable<?> iter) {
                    for (Object o : iter) {
                        if (o instanceof Map<?, ?> m) {
                            Map<String, String> d = new java.util.LinkedHashMap<>();
                            d.put("class", String.valueOf(m.get("class")));
                            d.put("method", String.valueOf(m.get("method")));
                            d.put("type", String.valueOf(m.get("type")));
                            d.put("message", String.valueOf(m.get("message")));
                            failedDetails.add(d);
                        }
                    }
                }
            }
        } catch (Exception e) {
            warnings.add("fork aggregation error: " + e);
            failures++;
        }
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("tests", tests);
        summary.put("failures", failures);
        summary.put("skipped", skipped);
        summary.put("failed", failedDetails);
        if (!missingClasses.isEmpty())
            summary.put("missingClasses", missingClasses);
        if (timedOut) {
            summary.put("timedOut", true);
            summary.put("timedOutClasses", timedOutClasses);
        }
        int exit = setupFailed ? -3 : (failures > 0 ? 1 : 0);
        return new JUnitForkExecutor.ForkResult(exit, summary, warnings);
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

    /** After each run: persist failing test classes for --rerun-failed (design 05 §九). */
    static void recordLastFailed(IbEngine engine, String moduleId, TestRun run) {
        Path stateFile = engine.getRepoRoot().resolve(".nop-ib").resolve("last-failed.json");
        java.util.List<int[]> summaries = run.summary;
        if (run.exitCode == Commands.EXIT_OK) {
            // success clears the recorded failures for this module
            try {
                Map<String, Object> all = readLastFailedMap(stateFile);
                all.remove(moduleId);
                writeMap(stateFile, all);
            } catch (Exception ignored) {
                // best-effort bookkeeping; never affects the run verdict
            }
            return;
        }
        if (run.exitCode == Commands.EXIT_LOCK_TIMEOUT || summaries.isEmpty())
            return;
        try {
            java.util.List<String> failedClasses = new ArrayList<>();
            IbModule module = engine.model().module(moduleId);
            Path reports = engine.getRepoRoot().resolve(module != null && module.getTestOutputDir() != null
                    ? module.getTestOutputDir().replace("test-classes", "surefire-reports")
                    : "target/surefire-reports");
            if (Files.isDirectory(reports)) {
                try (var stream = Files.list(reports)) {
                    for (Path p : stream.toList()) {
                        String name = p.getFileName().toString();
                        if (!name.startsWith("TEST-") || !name.endsWith(".txt") && !name.endsWith(".xml"))
                            continue;
                        String text = Files.readString(p);
                        if (text.contains("Failures: 0") && text.contains("Errors: 0"))
                            continue;
                        String cls = name.replaceAll("^TEST-|\\.(txt|xml)$", "").replace('-', '.');
                        failedClasses.add(cls);
                    }
                }
            }
            if (failedClasses.isEmpty())
                return;
            Map<String, Object> all = readLastFailedMap(stateFile);
            all.put(moduleId, failedClasses);
            writeMap(stateFile, all);
        } catch (Exception ignored) {
            // best-effort bookkeeping; never affects the run verdict
        }
    }

    private static Map<String, Object> readLastFailedMap(Path stateFile) throws Exception {
        if (!Files.isRegularFile(stateFile))
            return new LinkedHashMap<>();
        Object parsed = io.nop.ib.json.JsonIn.silentParse(Files.readString(stateFile));
        return io.nop.ib.json.JsonIn.asMapOrEmpty(parsed);
    }

    private static void writeMap(Path stateFile, Map<String, Object> all) throws Exception {
        Files.createDirectories(stateFile.getParent());
        Files.writeString(stateFile, io.nop.ib.json.JsonOut.write(all));
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<String>> readLastFailed(IbEngine engine) {
        Path stateFile = engine.getRepoRoot().resolve(".nop-ib").resolve("last-failed.json");
        if (!Files.isRegularFile(stateFile))
            return Map.of();
        try {
            Map<String, Object> all = readLastFailedMap(stateFile);
            Map<String, List<String>> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : all.entrySet()) {
                if (e.getValue() instanceof Iterable<?> iter) {
                    List<String> classes = new ArrayList<>();
                    for (Object o : iter)
                        classes.add(String.valueOf(o));
                    if (!classes.isEmpty())
                        out.put(e.getKey(), classes);
                }
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
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
        if (!run.warnings.isEmpty())
            m.put("warnings", run.warnings);
        m.put("run", total);
        m.put("failed", failed);
        m.put("errors", errors);
        m.put("skipped", skipped);
        if (run.exitCode == Commands.EXIT_LOCK_TIMEOUT)
            m.put("lockTimeout", true);
        out.println(json ? JsonOut.write(m)
                : String.format("[nop-ib] %s (%s) tests: run=%d failed=%d errors=%d skipped=%d%s%s",
                        run.module, run.kind, total, failed, errors, skipped,
                        run.selectedClasses != null ? " (selected=" + run.selectedClasses.size() + ")" : " (full)",
                        run.warnings.isEmpty() ? "" : " warn=" + run.warnings));
    }

    static final class TestRun {
        final String module;
        final List<String> selectedClasses;
        String kind = "surefire";
        int exitCode = Commands.EXIT_OK;
        List<int[]> summary = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
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
