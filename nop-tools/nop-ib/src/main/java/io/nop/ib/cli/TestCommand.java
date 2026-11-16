package io.nop.ib.cli;

import io.nop.ib.compile.ClasspathMaterializer;
import io.nop.ib.compile.MavenFallback;
import io.nop.ib.NopIbException;
import io.nop.ib.junit.JUnitForkExecutor;
import io.nop.ib.junit.SurefireClassFilter;
import io.nop.ib.engine.GitChanges;
import io.nop.ib.engine.IbEngine;
import io.nop.ib.json.JsonIn;
import io.nop.ib.json.JsonOut;
import io.nop.ib.lock.BuildLock;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import io.nop.ib.scope.PlanResult;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code nop-ib test} / {@code nop-ib check}: test selection + Surefire delegation
 * (design 05 §7.2 phase 1 / IB-D8). The Maven fallback delegates to the {@code test}
 * lifecycle phase (defect 20) so phase bindings and the default-test execution's
 * configuration stay faithful; the selection set is nop-ib's.
 *
 * <p>Lock protocol (wave6 6A-5 audit B-1 adjudication): the junit-fork path
 * TAKES NO BUILD LOCK — it only reads target/classes and writes per-process
 * temp result files (verified non-overlapping), so it is not a shared-resource
 * writer; acquiring the exclusive lock here would serialize the parallel module
 * sweep (-pl a,b, design 06 WR-D6) against itself. All WRITE paths share
 * .mvn/build.lock: fast-path compile (CompileCommand) and Maven delegations
 * (mvnq takes the same lock itself, so the fallback must not nest it — the
 * outer lock would self-deadlock the child). A concurrent compile-vs-test race
 * on target/classes can only produce a false RED (missing class file), never a
 * false green — fail-safe direction (hard constraint 4 rationale).
 *
 * <p>Selection re-runs after a Maven fallback compile (design 01 §13.3: a fallback
 * compile invalidates the pre-fallback selection).
 */
public final class TestCommand {
    /**
     * Total worker budget across concurrently running modules. The reference
     * baseline is {@code mvn test -T 1C} with surefire {@code forkCount=4}:
     * ~cores concurrent modules x 4 forks each — module-serial 1-worker sweeps
     * (the old cores-1 total) left ~4x parallelism on the table and lost the
     * full-repo wall clock. Tests are far from pure-CPU (H2 init, resource
     * loading, parser warmup), so matching mvn's oversubscription profile is
     * the empirically safe setting; override with nop.ib.workerBudget.
     */
    static final int WORKER_BUDGET = Math.max(4, Integer.getInteger("nop.ib.workerBudget",
            4 * Runtime.getRuntime().availableProcessors()));

    /**
     * Wave 10 service seam (design 07 §2.3): the service layer compresses the
     * effective per-job worker budget to {@code max(2, permits / activeJobs)}
     * while other projects are running. CLI runs never set the override, so
     * their behavior is byte-for-byte unchanged. Volatile: set/cleared around
     * each service job on the job's executor thread; read on the module pool.
     */
    private static volatile int workerBudgetOverride = 0;

    /** wave 10: public seam for the service layer (io.nop.ib.service). */
    public static void setWorkerBudgetOverride(int value) {
        workerBudgetOverride = value;
    }

    /** Effective budget: service override (if active) else the CLI constant. */
    static int workerBudget() {
        int override = workerBudgetOverride;
        return override > 0 ? Math.max(2, override) : WORKER_BUDGET;
    }

    /**
     * Module slots mirror mvn -T 1C: ~cores modules under test at once. Worker
     * permits are divided into fair shares: each module takes
     * min(classCount, budget / slots) workers, so a full sweep gives every
     * module ~4 (surefire forkCount parity — a slow small module running 4.5s
     * tests on a single worker used to own the tail), and a small -pl slice
     * gives each module budget/slots (the G-B behavior that beat mvn 9.6s vs
     * 11.9s). Greedy grab-everything starved sibling modules (22s slice).
     */
    static final int MODULE_SLOTS = moduleSlots();

    /** wave7 E4 experiment knob: independent module-slot override (serial = 1). */
    static int moduleSlots() {
        return Math.max(1, Integer.getInteger("nop.ib.moduleSlots",
                Math.max(4, WORKER_BUDGET / 4)));
    }

    private static final java.util.concurrent.ExecutorService moduleTestPool =
            java.util.concurrent.Executors.newFixedThreadPool(
                    MODULE_SLOTS,
                    r -> {
                        Thread t = new Thread(r, "nop-ib-module-test");
                        t.setDaemon(true);
                        return t;
                    });

    private static final Pattern SUREFIRE_SUMMARY =
            Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");

    /** junit-fork failure detail line: {class=..., method=..., type=..., ...} */
    private static final Pattern FAILED_CLASS_LINE =
            Pattern.compile("\\{class=([^,}]+),");

    /** repo-root nop-ib.config.json -> test.resetters (reflection-contract class names). */
    static List<String> configuredResetters(IbEngine engine) {
        Path cfg = engine.getRepoRoot().resolve("nop-ib.config.json");
        if (!Files.isRegularFile(cfg))
            return List.of();
        try {
            Map<String, Object> root = JsonIn.asMapOrEmpty(
                    JsonIn.silentParse(Files.readString(cfg, StandardCharsets.UTF_8)));
            Object test = root.get("test");
            if (test instanceof Map<?, ?> tm) {
                Object rs = tm.get("resetters");
                if (rs instanceof Iterable<?> iter) {
                    List<String> out = new ArrayList<>();
                    for (Object o : iter)
                        out.add(String.valueOf(o));
                    return out;
                }
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public static int test(IbEngine engine, List<String> paths, String plModules,
                           List<String> explicitClasses, boolean allowPartial, boolean json,
                           boolean includeCompile, String onFallback, boolean rerunFailed,
                           String runner, boolean useCache, boolean forceRun,
                           PrintWriter out, PrintWriter err) {
        RunLog log = RunLog.start(engine.getRepoRoot(), includeCompile ? "check" : "test");
        TestCacheStore cache = useCache ? new TestCacheStore(engine) : null;
        try {
            int code = runTest(engine, paths, plModules, explicitClasses, allowPartial, json,
                    includeCompile, onFallback, rerunFailed, runner, cache, forceRun,
                    out, err, log);
            if (cache != null)
                cache.save();
            log.exitCode(code);
            return code;
        } finally {
            log.close();
            RunLog.announce(out, log);
        }
    }

    static int runTest(IbEngine engine, List<String> paths, String plModules,
                       List<String> explicitClasses, boolean allowPartial, boolean json,
                       boolean includeCompile, String onFallback, boolean rerunFailed,
                       String runner, TestCacheStore cache, boolean forceRun,
                       PrintWriter out, PrintWriter err, RunLog log) {
        IbModel model = engine.model();
        List<String> changed = paths != null && !paths.isEmpty() ? paths
                : (plModules != null ? List.of() : GitChanges.changedPaths(engine.getRepoRoot()));

        // model-staleness gate for ALL dispatch branches (wave6 audit F3): the
        // plan-scope branch applied it via engine.stalenessReason(); the -pl and
        // --rerun-failed branches consumed model facts (fork classpath, index
        // dispatch) without checking — stale poms must never sharpen a fast-path
        // conclusion (hard constraint 2: model expired = full fallback)
        String staleReason = engine.stalenessReason();
        if (staleReason != null) {
            String notice = "model stale (" + staleReason + "): fast-path dispatch disabled, "
                    + "delegating to Maven (authoritative)";
            out.println(json ? JsonOut.write(Map.of("plan", Map.of("stale", staleReason)))
                    : "[nop-ib] " + notice);
            log.line(notice);
        }

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
                report(engine, run, json, out, log);
                if (run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
            return failures == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
        }
        if (plModules != null && (paths == null || paths.isEmpty()) && explicitClasses.isEmpty()) {
            // -pl module scope, no explicit classes: full suite per module, modules
            // in parallel (design 06 WR-D6 — closes the parallelism gap vs mvn -T 1C)
            List<String> moduleIds = new ArrayList<>();
            for (String artifactId : plModules.split(",")) {
                // `-pl :artifactId` coordinate form (wave6 6C-4): same meaning as
                // the bare artifactId in a single-module reactor
                String id = artifactId.trim();
                if (id.startsWith(":"))
                    id = id.substring(1);
                if (model.module(id) == null) {
                    err.println("usage error: -pl module not in reactor: " + artifactId);
                    return Commands.EXIT_USAGE;
                }
                moduleIds.add(id);
            }
            // fair worker shares (see MODULE_SLOTS): full sweep -> ~4/module like
            // surefire forkCount; small slice -> budget/slots per module
            java.util.concurrent.Semaphore workerPermits = new java.util.concurrent.Semaphore(workerBudget());
            int fairShare = Math.max(1, workerBudget() / Math.min(Math.max(moduleIds.size(), 1), MODULE_SLOTS));
            Map<String, TestRun> runByModule = new java.util.concurrent.ConcurrentHashMap<>();
            Map<String, Integer> classCounts = new LinkedHashMap<>();
            Set<String> modelSkipped = new LinkedHashSet<>();
            for (String moduleId : moduleIds) {
                IbModule m = model.module(moduleId);
                // model facts gate dispatch (same as the plan-scope branch):
                // skipTests=true (e.g. liteflow disables its script testcases in
                // pom) and modules without a test source tree run nothing
                if ("true".equals(m.getSurefireSkipTests())) {
                    modelSkipped.add(moduleId);
                    continue;
                }
                if (m.getTestSourceDir() == null
                        || !Files.isDirectory(engine.getRepoRoot().resolve(m.getTestSourceDir()))) {
                    modelSkipped.add(moduleId);
                    continue;
                }
                var index = engine.index(moduleId);
                int n = index == null ? 1 : index.testClasses().size();
                classCounts.put(moduleId, Math.max(1, n));
            }
            for (String skippedId : modelSkipped) {
                TestRun run = TestRun.skipped(skippedId, "skipTests=true or no test source (model fact)");
                report(engine, run, json, out, log);
                runByModule.put(skippedId, run);
            }
            moduleIds.removeAll(modelSkipped);
            java.util.List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (String moduleId : moduleIds) {
                IbModule module = model.module(moduleId);
                int classCount = classCounts.get(moduleId);
                int want = Math.min(classCount, fairShare);
                futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                    workerPermits.acquireUninterruptibly(want);
                    try {
                        long t0 = System.currentTimeMillis();
                        // stale model (wave6 audit F3): never fork from stale facts —
                        // Maven re-reads the live poms and is the only valid executor
                        TestRun run = staleReason != null
                                ? runMavenModuleTest(engine, module, json, out, err)
                                : runModuleMaybeCached(engine, cache, forceRun, module,
                                        null, runner, json, out, err, want);
                        if (staleReason != null)
                            run.kind = "maven-module(model-stale)";
                        run.durationMs = System.currentTimeMillis() - t0;
                        runByModule.put(moduleId, run);
                        report(engine, run, json, out, log);
                    } finally {
                        workerPermits.release(want);
                    }
                }, moduleTestPool));
            }
            int failures = 0;
            for (var f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    err.println("module test failed: " + e);
                    if (e.getCause() != null)
                        e.getCause().printStackTrace(err);
                    failures++;
                }
            }
            for (String moduleId : moduleIds) {
                TestRun run = runByModule.get(moduleId);
                if (run == null || run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
            return failures == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
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
                TestRun run = runModuleTests(engine, module, List.of(className), runner, json, out, err, 2);
                recordLastFailed(engine, module.getArtifactId(), run);
                report(engine, run, json, out, log);
                if (run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
            return failures == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
        }

        // plan scope: compile (optional) + affected tests
        PlanResult plan = engine.planner().plan(changed, staleReason, allowPartial);

        if (includeCompile) {
            int compileExit = CompileCommand.runCompile(engine, changed, null, onFallback,
                    allowPartial, json, out, err, log);
            if (compileExit != Commands.EXIT_OK)
                return compileExit;
            // re-judge the selection after compilation (unit indexes refreshed)
            plan = engine.planner().plan(changed, staleReason, allowPartial);
        }

        // hard gate 1 (design 05 §十, wave6 audit F4): partial / wideImpact /
        // fallback reasons / plan warnings MUST be visible in the output, not
        // silently dropped with the plan object
        emitPlanSurface(plan, json, out, log);

        if (plan.requiresMavenFallback()) {
            // guard (wave6 audit F7): an empty change set with a fallback
            // requirement used to crash on changed.get(0) — with nothing changed
            // there is nothing to select, so report the honest empty verdict
            if (changed.isEmpty()) {
                out.println(json ? JsonOut.write(Map.of("ok", true, "tests",
                        Map.of("selected", 0, "run", 0, "failed", 0,
                                "note", "fallback required but no changed paths")))
                        : "fallback required but no changed paths — nothing selected");
                return Commands.EXIT_OK;
            }
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
            report(engine, run, json, out, log);
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
                TestRun run = runModuleMaybeCached(engine, cache, forceRun, module, null,
                        runner, json, out, err, Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, workerBudget())));
                recordLastFailed(engine, moduleId, run);
                runs.add(run);
                if (run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
            for (Map.Entry<String, List<String>> e : plan.getSelectedTests().entrySet()) {
                IbModule module = model.module(e.getKey());
                if (module == null)
                    continue;
                TestRun run = runModuleMaybeCached(engine, cache, forceRun, module, e.getValue(),
                        runner, json, out, err, Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, workerBudget())));
                recordLastFailed(engine, e.getKey(), run);
                runs.add(run);
                if (run.exitCode != Commands.EXIT_OK)
                    failures++;
            }
        }
        for (TestRun run : runs)
            report(engine, run, json, out, log);
        return failures == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
    }

    /**
     * Wave 9 opt-in cache wrapper: on a fingerprint hit with a recorded GREEN,
     * returns an explicit {@code cached-up-to-date} run WITHOUT executing JUnit
     * (visible in human output and JSON as cached — never masquerades as an
     * execution). After a real junit-fork GREEN run, records the cache entry.
     * Red runs and non-fork kinds (surefire/maven fallback) are never cached.
     */
    static TestRun runModuleMaybeCached(IbEngine engine, TestCacheStore cache, boolean force,
                                        IbModule module, List<String> classes, String runner,
                                        boolean json, PrintWriter out, PrintWriter err,
                                        int workers) {
        if (cache == null || force)
            return runModuleTests(engine, module, classes, runner, json, out, err, workers);
        String fp = cache.fingerprint(engine, module);
        Map<String, Object> hit = cache.hit(module.getArtifactId(), fp);
        if (hit != null) {
            TestRun run = new TestRun(module.getArtifactId(), null);
            run.kind = "cached-up-to-date";
            run.summary.add(new int[]{numOf(hit.get("tests")), numOf(hit.get("failed")),
                    numOf(hit.get("errors")), numOf(hit.get("skipped"))});
            run.warnings.add("cache hit (--cache): fingerprint unchanged since last GREEN run; "
                    + "use --force to re-execute");
            return run;
        }
        TestRun run = runModuleTests(engine, module, classes, runner, json, out, err, workers);
        if ("junit-fork".equals(run.kind) && run.exitCode == Commands.EXIT_OK) {
            int t = 0, f = 0, e = 0, sk = 0;
            for (int[] s : run.summary) {
                t += s[0];
                f += s[1];
                e += s[2];
                sk += s[3];
            }
            cache.record(module.getArtifactId(), cache.fingerprint(engine, module), 0, t, f, e, sk);
        }
        return run;
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
     * Recovery path for a missing or test-less unit index (flink observation,
     * defect 19): when the module has test sources that were never compiled
     * (project installed with -Dmaven.test.skip, baseline stopped earlier in the
     * reactor, or no prior ib compile), run one Maven test-compile, rescan the
     * products and persist a fresh unit index, then re-enumerate through the
     * surefire include filter. Returns null when no test sources exist or the
     * retry yields nothing — callers keep their existing skip/fallback semantics.
     */
    private static List<String> recoverMissingTestClasses(IbEngine engine, IbModule module,
                                                          List<String> warnings) {
        if (!hasTestSources(engine, module))
            return null;
        io.nop.ib.compile.MavenFallback fallback =
                new io.nop.ib.compile.MavenFallback(engine.getRepoRoot());
        StringBuilder mout = new StringBuilder();
        int code = fallback.delegate("-pl :" + module.getArtifactId()
                + " test-compile -q", mout, new StringBuilder());
        if (code != 0)
            return null;
        io.nop.ib.index.UnitIndexStore unitStore = engine.getUnitStore();
        io.nop.ib.index.UnitIndexer indexer = new io.nop.ib.index.UnitIndexer();
        List<Path> outputs = new ArrayList<>();
        if (module.getOutputDir() != null)
            outputs.add(engine.getRepoRoot().resolve(module.getOutputDir()));
        if (module.getTestOutputDir() != null)
            outputs.add(engine.getRepoRoot().resolve(module.getTestOutputDir()));
        unitStore.save(indexer.scan(module.getArtifactId(), outputs,
                engine.getRepoRoot().resolve(module.getBasedir())));
        engine.refreshIndexesIfModelChanged();
        var fresh = engine.index(module.getArtifactId());
        if (fresh == null)
            return null;
        List<String> recovered = new ArrayList<>();
        for (var info : fresh.testClasses())
            recovered.add(info.getClassName());
        recovered = SurefireClassFilter.filter(recovered, module);
        if (!recovered.isEmpty())
            warnings.add("unit index missing/empty: test sources compiled via Maven "
                    + "retry, " + recovered.size() + " classes discovered");
        return recovered;
    }

    /** true when the module declares a test source dir containing .java sources. */
    private static boolean hasTestSources(IbEngine engine, IbModule module) {
        String dir = module.getTestSourceDir();
        if (dir == null)
            return false;
        Path testSrc = engine.getRepoRoot().resolve(dir);
        if (!java.nio.file.Files.isDirectory(testSrc))
            return false;
        try (java.util.stream.Stream<Path> s = java.nio.file.Files.walk(testSrc)) {
            return s.anyMatch(p -> p.getFileName().toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Fast-path execution: forked JUnit JVM (design 02 contract). Falls back to
     * surefire ONLY when the junit setup cannot be satisfied (no platform artifacts
     * on the classpath / launcher jar unavailable) — the fallback reason is
     * explicit in the run kind. Missing test classes trigger one test-compile
     * retry via Maven (adaptive expansion, design 01 §十).
     */
    static TestRun runModuleTests(IbEngine engine, IbModule module, List<String> classes,
                                  String runner, boolean json, PrintWriter out, PrintWriter err,
                                  int workerCount) {
        List<String> warnings = new ArrayList<>();
        if (!"junit".equals(runner))
            return runSurefire(engine, module, classes, json, out, err);
        // model-facts gate (wave6 6A-2): surefire config dimensions the fork
        // engine cannot faithfully replicate (non-default workingDirectory,
        // additionalClasspathDependencies, repo-internal classpath elements)
        // route to real surefire — zero-fake-green beats speed
        if (module.getSurefireUnsupportedReason() != null) {
            TestRun run = runSurefire(engine, module, classes, json, out, err);
            run.kind = "surefire(model-facts: " + module.getSurefireUnsupportedReason() + ")";
            return run;
        }
        if (classes == null) {
            // full-module fork: enumerate test classes from the unit index
            var index = engine.index(module.getArtifactId());
            if (index == null) {
                // flink observation (defect 19, generalization): a missing index
                // with test sources on disk means the tests were never compiled
                // through ib — recover via one Maven test-compile + rescan instead
                // of dropping to the surefire goal fallback (whose direct-goal
                // invocation ignores phase bindings — candidate defect 20).
                List<String> recovered = recoverMissingTestClasses(engine, module, warnings);
                if (recovered == null) {
                    TestRun run = runSurefire(engine, module, null, json, out, err);
                    run.kind = "surefire(index-missing)";
                    return run;
                }
                classes = recovered;
            } else {
                classes = new ArrayList<>();
                for (var info : index.testClasses())
                    classes.add(info.getClassName());
                // surefire include-pattern parity (defect 14): the index dispatches
                // every JUnit-marked class; Maven only executes the ones matching the
                // module's <includes> (default Test*/*Test/*Tests/*TestCase). Base
                // classes like *ITCase carry @Test methods but rely on a subclass
                // @BeforeAll — running them standalone fails and poisons the module.
                classes = SurefireClassFilter.filter(classes, module);
                if (classes.isEmpty()) {
                    List<String> recovered = recoverMissingTestClasses(engine, module, warnings);
                    if (recovered != null)
                        classes = recovered;
                }
            }
            if (classes.isEmpty()) {
                TestRun run = TestRun.skipped(module.getArtifactId(), "no test classes in unit index");
                return run;
            }
        }
        ClasspathMaterializer materializer = new ClasspathMaterializer(engine.model());
        ClasspathMaterializer.Materialized cp = materializer.testClasspath(module);
        cp.assertNoIntraProjectJars(engine.model());
        // surefire <additionalClasspathElements> parity (wave6 6A-2): repo-external
        // entries are appended verbatim (the parser routes repo-internal ones to
        // the surefire fallback above)
        if (!module.getSurefireAdditionalClasspathElements().isEmpty()) {
            List<String> entries = new ArrayList<>(cp.entries());
            Path repoRootPath = engine.getRepoRoot();
            for (String element : module.getSurefireAdditionalClasspathElements()) {
                Path p = Path.of(element);
                entries.add(p.isAbsolute() ? element : repoRootPath.resolve(element).toString());
            }
            cp = new ClasspathMaterializer.Materialized(entries, cp.missing());
            warnings.add("additionalClasspathElements appended: "
                    + module.getSurefireAdditionalClasspathElements());
        }
        // dispatch-surface ruling (candidate defect 26, zeebe-gateway-grpc): Maven
        // surefire puts test-jar dependencies on the classpath but does NOT discover
        // their test classes — the dispatch surface is the module's OWN test classes
        // only (grpc: 65 mvn classes, all from src/test; the first tests-jar scan
        // attempt over-dispatched 121 classes / 526 tests vs mvn's 462 and was
        // reverted). Test-jar classes stay classpath-only (harness/base classes).
        if (!cp.complete()) {
            TestRun run = runSurefire(engine, module, classes, json, out, err);
            run.kind = "surefire(classpath-incomplete)";
            return run;
        }
        JUnitForkExecutor executor = new JUnitForkExecutor(engine.model());
        List<String> forkWarnings = new ArrayList<>();
        List<String> resetters = configuredResetters(engine);
        io.nop.ib.junit.JUnitWorkerPool pool;
        JUnitForkExecutor.ForkResult fork;
        try {
            pool = new io.nop.ib.junit.JUnitWorkerPool(
                    engine.model(), module, cp, resetters, workerCount,
                    workerMaxClasses(engine));
            fork = pool.run(classes);
        } catch (NopIbException e) {
            // worker setup unsatisfiable (e.g. no JUnit engine on the classpath —
            // JUnit4-only module): explicit surefire fallback, not a doomed fork
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String head = sw.toString().lines().skip(1).findFirst().map(String::trim).orElse("");
            TestRun fb = runSurefire(engine, module, classes, json, out, err);
            fb.kind = "surefire(worker-setup: " + e + " @ " + head + ")";
            return fb;
        }
        forkWarnings.addAll(warnings);
        forkWarnings.addAll(fork.warnings());
        // module-level differential rerun (design 06 WR-D4, v2 semantics): when
        // the worker run fails, rerun the WHOLE module in ONE fresh sequential
        // fork — surefire's own execution model (single JVM, classes in order,
        // cross-class state accumulates). Activiti conformance tests assert on
        // deployment registries accumulated ACROSS test classes: per-class fresh
        // forks reproduce the failure (missing accumulated state) and per-class
        // pollution checks cannot apply, so the only faithful oracle is the
        // surefire-shaped rerun. Fresh result authoritative either way.
        System.err.println("nop-ib debug: rerun-gate [" + module.getArtifactId() + "] exit="
                + fork.exitCode() + " classes=" + (classes == null ? "null" : classes.size())
                + " failures=" + fork.summary().get("failures"));
        if (fork.exitCode() == 1 && classes != null && !classes.isEmpty()) {
            long rerunCap = Long.getLong("nop.ib.moduleRerunTimeoutSeconds", 1800);
            // wave7 E1 experiment switch (default off): failed-only subset rerun.
            // Semantics: rerun ONLY the failed classes fresh; if the subset fails,
            // verdict FAILED as usual. If the subset PASSES, the module verdict
            // STILL comes from a full fresh fork (fail-safe; the Activiti
            // precedent shows order-sensitive failures can vanish in a subset) —
            // but the divergent-pass timing and divergence flag are recorded so
            // the experiment can measure the subset's saving and false-positive
            // rate. With divergence measured 0 across a corpus, the saving is the
            // adoption candidate; the full fork never leaves the default path.
            if ("failed-only".equals(System.getProperty("nop.ib.moduleRerunMode"))
                    && failedClassesOf(fork.summary()) != null) {
                List<String> failedSubset = failedClassesOf(fork.summary());
                System.err.println("nop-ib debug: rerun E1 failed-only subset, classes="
                        + failedSubset.size() + " cap=" + rerunCap);
                long t0 = System.currentTimeMillis();
                JUnitForkExecutor.ForkResult subset = executor.execute(
                        module, cp, failedSubset, java.time.Duration.ofSeconds(rerunCap));
                long subsetMs = System.currentTimeMillis() - t0;
                forkWarnings.add("E1 failed-only rerun: subset=" + failedSubset.size()
                        + " classes, " + subsetMs + "ms, exit=" + subset.exitCode()
                        + " (full fresh fork still governs the verdict)");
                if (subset.exitCode() != 0) {
                    forkWarnings.add("E1: subset reproduces failures -> genuine, full rerun skipped "
                            + "(saving=" + subsetMs + "ms)");
                    forkWarnings.addAll(subset.warnings());
                    fork = new JUnitForkExecutor.ForkResult(1, fork.summary(), fork.warnings());
                } else {
                    System.err.println("nop-ib debug: rerun E1 subset PASSED, executing full fresh fork (fail-safe)");
                    JUnitForkExecutor.ForkResult fresh = executeRerun(
                            executor, module, cp, classes, rerunCap, forkWarnings);
                    forkWarnings.add("E1 divergence probe: subset passed but full fork exit="
                            + fresh.exitCode() + " (divergence=" + (fresh.exitCode() == 0 ? "0" : "1") + ")");
                    forkWarnings.addAll(fresh.warnings());
                    fork = fresh;
                }
            } else {
            System.err.println("nop-ib debug: rerun EXECUTING fresh full-module fork, cap=" + rerunCap);
            JUnitForkExecutor.ForkResult fresh = executeRerun(
                    executor, module, cp, classes, rerunCap, forkWarnings);
            System.err.println("nop-ib debug: rerun DONE freshExit=" + fresh.exitCode());
            // wave6 audit A-1: a rerun-gate TIMEOUT must not overwrite the worker
            // run's collected results (spring-batch-core precedent — the fresh
            // fork's counts-only result replaced 906 executed tests and
            // starved --rerun-failed of the class list). Fail-safe merge: keep the
            // worker results, mark timedOut, verdict stays FAILED.
            if (fresh.summary().get("timedOut") != null) {
                forkWarnings.add("module differential rerun timed out (cap=" + rerunCap
                        + "s): worker-run results retained, module stays FAILED (fail-safe)");
                Map<String, Object> merged = new LinkedHashMap<>(fork.summary());
                merged.put("timedOut", true);
                fork = new JUnitForkExecutor.ForkResult(1, merged, fork.warnings());
            } else if (fresh.exitCode() == 0) {
                forkWarnings.add("module differential rerun: PASSES in fresh single-fork "
                        + "(worker-model pollution/order divergence) -> fresh result authoritative");
                forkWarnings.addAll(fresh.warnings());
                fork = fresh;
            } else {
                forkWarnings.add("module differential rerun: fails identically in fresh "
                        + "single-fork -> genuine failures confirmed");
                forkWarnings.addAll(fresh.warnings());
                fork = fresh;
            }
            }
        }
        if (fork.exitCode() == -3) {
            // junit setup unsatisfiable -> explicit fallback
            TestRun run = runSurefire(engine, module, classes, json, out, err);
            run.kind = "surefire(" + String.join("; ", fork.warnings()) + ")";
            return run;
        }
        TestRun run = new TestRun(module.getArtifactId(), classes);
        run.kind = "junit-fork";
        // forkWarnings carries the recovery warnings (index-missing recovery,
        // ACLE append, differential rerun verdicts) — they were assembled but
        // never surfaced before (wave6 audit F4 family)
        run.warnings.addAll(forkWarnings);
        run.warnings.addAll(fork.warnings());
        Map<String, Object> summary = fork.summary();
        int tests = numOf(summary.get("tests"));
        int failed = numOf(summary.get("failures"));
        int skipped = numOf(summary.get("skipped"));
        run.summary.add(new int[]{tests, failed, 0, skipped});
        if (summary.get("slowest") instanceof Iterable<?> iter) {
            for (Object o : iter)
                run.warnings.add("slow: " + o);
        }
        // fork-crash diagnostics surfacing (wave6 6C-2, flink-agents abort-134
        // precedent): a fork that died without a result document carries its
        // stderr/stdout tails in the summary — they must reach the run warnings,
        // not stay buried in a map the caller never prints
        if (summary.get("forkError") != null)
            run.warnings.add("forkError: " + summary.get("forkError"));
        if (summary.get("forkStdoutTail") != null)
            run.warnings.add("forkStdoutTail: " + summary.get("forkStdoutTail"));
        if (summary.get("crashed") != null)
            run.warnings.add("fork crashed without result document (exit " + fork.exitCode() + ")");
        if (Boolean.TRUE.equals(summary.get("poolAborted")))
            run.warnings.add("worker pool aborted: see earlier pool warning; module verdict FAILED");
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
                Map<String, Object> s2 = fork.summary();
                // wave6 audits F2/A-3: the retry fork's own exit semantics govern —
                // the old `f2 > 0 ? FAILED : OK` reported EXIT_OK for a retry that
                // executed ZERO tests (still-missing classes, crashed fork) — a
                // fake green. exit 2 again = classes still missing -> FAILED with
                // the missing list; -3 = setup unusable -> surefire fallback.
                if (fork.exitCode() == -3 || s2.isEmpty()) {
                    TestRun fb = runSurefire(engine, module, classes, json, out, err);
                    fb.kind = "surefire(missing-class-retry-unusable)";
                    fb.warnings.addAll(warnings);
                    return fb;
                }
                run.summary.clear();
                int t2 = numOf(s2.get("tests"));
                int f2 = numOf(s2.get("failures"));
                int sk2 = numOf(s2.get("skipped"));
                run.summary.add(new int[]{t2, f2, 0, sk2});
                if (fork.exitCode() == 2) {
                    run.warnings.addAll(warnings);
                    run.warnings.add("missing classes persist after Maven test-compile retry: "
                            + (s2.get("missingClasses") != null ? s2.get("missingClasses") : "unknown"));
                    run.exitCode = Commands.EXIT_FAILED;
                    return run;
                }
                run.warnings.addAll(warnings);
                run.exitCode = fork.exitCode() == 0 && t2 > 0
                        ? Commands.EXIT_OK : Commands.EXIT_FAILED;
                return run;
            }
            // compile retry itself failed: the selected classes cannot be built —
            // the run is FAILED, not silently green (wave6 audit A-3)
            run.warnings.addAll(warnings);
            run.warnings.add("missing-class Maven test-compile retry failed (exit " + code + ")");
            run.exitCode = Commands.EXIT_FAILED;
            return run;
        }
        run.exitCode = fork.exitCode() == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
        return run;
    }

    private static int workerMaxClasses(IbEngine engine) {
        Path cfg = engine.getRepoRoot().resolve("nop-ib.config.json");
        if (Files.isRegularFile(cfg)) {
            try {
                Map<String, Object> root = JsonIn.asMapOrEmpty(
                        JsonIn.silentParse(Files.readString(cfg, StandardCharsets.UTF_8)));
                Object test = root.get("test");
                if (test instanceof Map<?, ?> tm && tm.get("workerMaxClasses") instanceof Number n)
                    return n.intValue();
            } catch (Exception ignored) {
                // fall through to default
            }
        }
        return 20;
    }

    private static int numOf(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** failed classes from a worker summary ("failed" entries carry class=), or null when absent. */
    static List<String> failedClassesOf(Map<String, Object> summary) {
        Object failedList = summary.get("failed");
        if (!(failedList instanceof Iterable<?> iter))
            return null;
        List<String> out = new ArrayList<>();
        for (Object o : iter) {
            if (o instanceof Map<?, ?> m && m.get("class") != null)
                out.add(String.valueOf(m.get("class")));
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * wave7 E5 experiment switch (default off): a rerun executed in batches of
     * {@code nop.ib.rerunBatchSize} fresh forks instead of one big fork — the
     * zeebe-engine 8k-tests single-fork crash precedent. Batches run
     * sequentially and merge additively; the merged exit is 0 only when every
     * batch is green (zero-fake-green: any batch failure fails the merge).
     */
    static JUnitForkExecutor.ForkResult executeRerun(JUnitForkExecutor executor,
                                                     IbModule module,
                                                     ClasspathMaterializer.Materialized cp,
                                                     List<String> classes,
                                                     long rerunCap,
                                                     List<String> warnings) {
        int batch = Integer.getInteger("nop.ib.rerunBatchSize", 0);
        java.time.Duration cap = java.time.Duration.ofSeconds(rerunCap);
        if (batch <= 0 || classes.size() <= batch)
            return executor.execute(module, cp, classes, cap);
        long t0 = System.currentTimeMillis();
        List<JUnitForkExecutor.ForkResult> batches = new ArrayList<>();
        for (int from = 0; from < classes.size(); from += batch) {
            List<String> slice = classes.subList(from, Math.min(from + batch, classes.size()));
            JUnitForkExecutor.ForkResult r = executor.execute(module, cp, slice, cap);
            warnings.add("E5 rerun batch " + (from / batch + 1) + "/" + ((classes.size() + batch - 1) / batch)
                    + ": " + slice.size() + " classes, exit=" + r.exitCode());
            batches.add(r);
        }
        warnings.add("E5 batched rerun: " + classes.size() + " classes in "
                + ((classes.size() + batch - 1) / batch) + " forks, "
                + (System.currentTimeMillis() - t0) + "ms total");
        return mergeBatches(batches);
    }

    /**
     * E5 batch merge (zero-fake-green): counts add up; "failed" detail lists
     * concatenate; the merged exit is the FIRST non-zero batch exit — a single
     * red batch makes the whole rerun red.
     */
    static JUnitForkExecutor.ForkResult mergeBatches(List<JUnitForkExecutor.ForkResult> batches) {
        int tests = 0, failures = 0, skipped = 0;
        List<Map<String, String>> failed = new ArrayList<>();
        List<String> batchWarnings = new ArrayList<>();
        int exit = 0;
        for (JUnitForkExecutor.ForkResult r : batches) {
            batchWarnings.addAll(r.warnings());
            Map<String, Object> s = r.summary();
            tests += numOf(s.get("tests"));
            failures += numOf(s.get("failures"));
            skipped += numOf(s.get("skipped"));
            if (s.get("failed") instanceof Iterable<?> iter)
                for (Object o : iter)
                    if (o instanceof Map<?, ?> m) {
                        Map<String, String> d = new LinkedHashMap<>();
                        d.put("class", String.valueOf(m.get("class")));
                        d.put("method", String.valueOf(m.get("method")));
                        d.put("type", String.valueOf(m.get("type")));
                        d.put("message", String.valueOf(m.get("message")));
                        failed.add(d);
                    }
            if (r.exitCode() != 0 && exit == 0)
                exit = r.exitCode();
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("tests", tests);
        merged.put("failures", failures);
        merged.put("skipped", skipped);
        merged.put("failed", failed);
        return new JUnitForkExecutor.ForkResult(exit, merged, batchWarnings);
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

    /**
     * Maven args for the surefire-shaped fallback (defect 20): delegate to the
     * {@code test} LIFECYCLE phase, not the {@code surefire:test} goal. A direct
     * goal invocation bypasses phase bindings and the default-test execution's
     * own configuration (includes/argLine/forkCount/excludedGroups) — on flink
     * it dispatched the integration set the unit execution excludes
     * (fs-tests: 41/7/2 vs the lifecycle's 40/7/1). Flag passthrough keeps the
     * lifecycle reachable on external projects: enforcer version pins (flink
     * locks Maven 3.8.6; the local toolchain is newer) and the build cache must
     * not gate a fallback run.
     */
    static String surefireFallbackArgs(IbModule module, List<String> testClasses) {
        StringBuilder args = new StringBuilder("-pl :" + module.getArtifactId() + " test");
        if (testClasses != null && !testClasses.isEmpty()) {
            args.append(" -Dtest=").append(String.join(",", testClasses));
            args.append(" -DfailIfNoSpecifiedTests=false");
        }
        args.append(" -Denforcer.skip=true -Dmaven.build.cache.skipCache=true");
        return args.toString();
    }

    static TestRun runSurefire(IbEngine engine, IbModule module, List<String> testClasses,
                               boolean json, PrintWriter out, PrintWriter err) {
        TestRun run = new TestRun(module.getArtifactId(), testClasses);
        String args = surefireFallbackArgs(module, testClasses);
        // no outer BuildLock: mvnq takes .mvn/build.lock itself (see runMavenModuleTest)
        MavenFallback fallback = new MavenFallback(engine.getRepoRoot());
        StringBuilder mout = new StringBuilder();
        int code = fallback.delegate(args, mout, new StringBuilder());
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
            if ("junit-fork".equals(run.kind)) {
                // junit-fork runs write no surefire reports: use the run's own
                // failure detail (reading the surefire dir would pick up stale
                // reports from an older mvn run of the module)
                for (String line : run.output.split("\n")) {
                    Matcher fm = FAILED_CLASS_LINE.matcher(line);
                    if (fm.find())
                        failedClasses.add(fm.group(1));
                }
            } else {
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

    /**
     * Module-level surefire summaries only (defect 20 co-finding): surefire's
     * per-class lines ("Tests run: 32, ... -- in org.example.FooTest") match the
     * same pattern as the final module summary — summing both double-counts
     * every test (flink-fs-tests: 80/14/2 reported for a real 40/7/1 run).
     * Per-class lines carry the " -- in <class>" suffix; strip those lines
     * before matching.
     */
    static List<int[]> extractSummaries(String output) {
        List<int[]> out = new ArrayList<>();
        StringBuilder summaries = new StringBuilder();
        for (String line : output.split("\n")) {
            if (line.contains(" -- in "))
                continue;
            summaries.append(line).append('\n');
        }
        Matcher m = SUREFIRE_SUMMARY.matcher(summaries.toString());
        while (m.find()) {
            out.add(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))});
        }
        return out;
    }

    /**
     * Plan-scope visibility (design 05 §十 hard gate 1, wave6 audit F4):
     * partial / wideImpact / fallbackReason / plan warnings must appear in the
     * command output and the run log — a quiet narrowing of the selection set is
     * the fake-green shape this engine exists to prevent.
     */
    private static void emitPlanSurface(PlanResult plan, boolean json, PrintWriter out, RunLog log) {
        boolean hasScope = plan.isPartial() || plan.isWideImpact()
                || plan.getFallbackReason() != null || !plan.getWarnings().isEmpty();
        if (!hasScope)
            return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("partial", plan.isPartial());
        m.put("wideImpact", plan.isWideImpact());
        if (plan.getFallbackReason() != null)
            m.put("fallbackReason", plan.getFallbackReason());
        if (!plan.getWarnings().isEmpty())
            m.put("warnings", plan.getWarnings());
        String line = json ? JsonOut.write(Map.of("plan", m))
                : "[nop-ib] plan: partial=" + plan.isPartial() + " wideImpact=" + plan.isWideImpact()
                        + (plan.getFallbackReason() != null ? " fallback=" + plan.getFallbackReason() : "")
                        + (plan.getWarnings().isEmpty() ? "" : " warnings=" + plan.getWarnings());
        out.println(line);
        log.line(json ? line : JsonOut.write(Map.of("plan", m)));
    }

    private static void report(IbEngine engine, TestRun run, boolean json, PrintWriter out, RunLog log) {
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
        // wave9: explicit cache flag — a cached green must be machine-
        // distinguishable from an execution, not only via the kind string
        m.put("cached", "cached-up-to-date".equals(run.kind));
        m.put("selected", run.selectedClasses);
        if (!run.warnings.isEmpty())
            m.put("warnings", run.warnings);
        m.put("run", total);
        m.put("failed", failed);
        m.put("errors", errors);
        m.put("skipped", skipped);
        if (run.durationMs > 0)
            m.put("durationMs", run.durationMs);
        // failure detail (class/method/type/message) — parity analysis needs the
        // exact failing set, not just counts; skipped-run reason lives in output
        // too but must NOT surface as failedDetail (it is not a failure)
        if (!run.output.isBlank() && !"skipped".equals(run.kind))
            m.put("failedDetail", run.output.trim());
        else if (!run.output.isBlank())
            m.put("skipReason", run.output.trim());
        if (run.exitCode == Commands.EXIT_LOCK_TIMEOUT)
            m.put("lockTimeout", true);
        String human = json ? JsonOut.write(m)
                : (String.format("[nop-ib] %s (%s) tests: run=%d failed=%d errors=%d skipped=%d%s%s",
                        run.module, run.kind, total, failed, errors, skipped,
                        run.selectedClasses != null ? " (selected=" + run.selectedClasses.size() + ")" : " (full)",
                        run.warnings.isEmpty() ? "" : " warn=" + run.warnings));
        out.println(human);
        // durable record: the same line + structured entry into the run log
        log.module(m, human);
    }

    static final class TestRun {
        final String module;
        final List<String> selectedClasses;
        String kind = "surefire";
        int exitCode = Commands.EXIT_OK;
        List<int[]> summary = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        String output = "";
        long durationMs;

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
