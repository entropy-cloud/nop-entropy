package io.nop.ib.cli;

import io.nop.ib.compile.IncrementalCompileExecutor;
import io.nop.ib.compile.MavenFallback;
import io.nop.ib.engine.GitChanges;
import io.nop.ib.engine.IbEngine;
import io.nop.ib.json.JsonOut;
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

/**
 * {@code nop-ib compile}: selective compilation over the planned scope.
 * Semantics (design 05 §九/§7.3, roadmap item 16):
 * <ul>
 *   <li>plan first; a required fallback delegates to Maven via mvnq when
 *       {@code --on-fallback=maven} (default), refuses with exit 2 when
 *       {@code --on-fallback=fail};</li>
 *   <li>concurrency is controlled in-process (service lane / CLI single
 *       exit 75;</li>
 *   <li>never writes ~/.m2, never enters package phases.</li>
 * </ul>
 */
public final class CompileCommand {
    public static int compile(IbEngine engine, List<String> paths, String plModules,
                              String onFallback, boolean allowPartial, boolean json,
                              PrintWriter out, PrintWriter err) {
        RunLog log = RunLog.start(engine.getRepoRoot(), "compile");
        try {
            int code = runCompile(engine, paths, plModules, onFallback, allowPartial, json,
                    out, err, log);
            log.exitCode(code);
            return code;
        } finally {
            log.close();
            RunLog.announce(out, log);
        }
    }

    /** shared body; when invoked from {@code check} the caller's run log receives
     *  the compile section (one durable record per CLI invocation). */
    static int runCompile(IbEngine engine, List<String> paths, String plModules,
                          String onFallback, boolean allowPartial, boolean json,
                          PrintWriter out, PrintWriter err, RunLog log) {
        IbModel model = engine.model();
        List<String> changed;
        if (paths != null && !paths.isEmpty())
            changed = paths;
        else if (plModules != null) {
            // -pl <artifactIds>: compile every changed+existing main source of the
            // modules (v1 semantics: explicit module scope = module-compile request)
            changed = new ArrayList<>();
            for (String artifactId : plModules.split(",")) {
                // `-pl :artifactId` coordinate form (wave6 6C-4)
                String id = artifactId.trim();
                if (id.startsWith(":"))
                    id = id.substring(1);
                IbModule module = model.module(id);
                if (module == null) {
                    err.println("usage error: -pl module not in reactor: " + artifactId);
                    return Commands.EXIT_USAGE;
                }
                List<Path> sourceDirs = new ArrayList<>();
                // aggregator/packaging=pom modules may declare no source roots at
                // all — skip silently, they simply contribute no fast-path files
                if (module.getSourceDir() != null)
                    sourceDirs.add(engine.getRepoRoot().resolve(module.getSourceDir()));
                // wave 11 audit fix: test sources must be in scope too — a stale
                // target/test-classes silently served old bytecode (fake green)
                if (module.getTestSourceDir() != null)
                    sourceDirs.add(engine.getRepoRoot().resolve(module.getTestSourceDir()));
                for (Path src : sourceDirs) {
                    if (Files.isDirectory(src)) {
                        try (var stream = Files.walk(src)) {
                            stream.filter(p -> p.toString().endsWith(".java"))
                                    .forEach(p -> {
                                        String rel = engine.getRepoRoot().relativize(p).toString();
                                        // generated sources (src/main/java/**/_gen) are codegen
                                        // products: never on the fast path (design 01 §七 L1)
                                        if (!rel.contains("/_gen/"))
                                            changed.add(rel);
                                    });
                        } catch (Exception e) {
                            err.println("failed to enumerate sources of " + artifactId);
                            return Commands.EXIT_USAGE;
                        }
                    }
                }
            }
            // plan 2583: module-with-no-sources must NOT short-circuit the whole
            // -pl enumeration — the old in-loop early exit made
            // `-pl <no-source-parent>,<has-source-child>` return OK having
            // silently skipped the child's compile (fake green). Judge once,
            // after the full enumeration.
            if (changed.isEmpty()) {
                // no module in scope has main/test sources: nothing to fast-path compile
                return Commands.EXIT_OK;
            }
        } else {
            changed = GitChanges.changedPaths(engine.getRepoRoot());
        }

        PlanResult plan = engine.planner().plan(changed, engine.stalenessReason(), allowPartial);
        // plan 2588: publish the compile selection size for job progress polls
        io.nop.ib.service.ProgressRegistry.update(engine.getRepoRoot(), Map.of(
                "stage", "compile",
                "compileFilesTotal", plan.getCompileFiles().size(),
                "compileModulesTotal", plan.getCompileModules().size()));
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("ok", true);

        if (plan.requiresMavenFallback()) {
            if ("fail".equals(onFallback)) {
                feedback.put("ok", false);
                feedback.put("fallback", Map.of("required", true, "reason", plan.getFallbackReason()));
                String line = json ? JsonOut.write(feedback)
                        : "fallback required but --on-fallback=fail: " + plan.getFallbackReason();
                out.println(line);
                log.line(line);
                return Commands.EXIT_FALLBACK_REJECTED;
            }
            MavenFallback fallback = new MavenFallback(engine.getRepoRoot());
            StringBuilder mout = new StringBuilder();
            StringBuilder merr = new StringBuilder();
            int code = fallback.compileModules(plan.getCompileModules().isEmpty()
                            ? List.of(owningModuleOf(engine, model, changed.get(0)))
                            : plan.getCompileModules(), mout, merr);
            Map<String, Object> fb = new java.util.LinkedHashMap<>();
            fb.put("required", true);
            fb.put("reason", plan.getFallbackReason() + tailStaleHint(plan.getFallbackReason()));
            fb.put("delegated", true);
            fb.put("exit", code);
            // 2026-11-22 live audit: in --json mode the Maven delegation output
            // was discarded entirely — an AI seeing exit 1 with no output could
            // not self-correct (its compile error only surfaced via the mvn
            // passthrough). Always carry a tail; stderr gets priority.
            fb.put("mvnOutputTail", outputTail(merr.length() > 0 ? merr.toString() : mout.toString()));
            feedback.put("fallback", fb);
            feedback.put("timings", Map.of("totalMs", 0));
            String line = json ? JsonOut.write(feedback)
                    : "delegated to Maven (mvnq): exit " + code + "\n" + mout;
            out.println(line);
            log.line(line);
            if (!merr.isEmpty())
                log.line("[mvn stderr] " + merr);
            if (code == MavenFallback.EXIT_LOCK_TIMEOUT)
                return Commands.EXIT_LOCK_TIMEOUT;
            return code == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
        }

        // 2026-11-22 defect pair: even with zero source changes the executor's
        // product-integrity sweep runs (missing/stale target/classes are rebuilt,
        // mvn parity) — "nothing changed" alone never ends the command anymore.
        if (plan.getCompileFiles().isEmpty() && plan.getDownstreamCandidates().isEmpty()
                && plan.getCompileModules().isEmpty()) {
            IncrementalCompileExecutor executor = new IncrementalCompileExecutor(
                    model, engine.getUnitStore());
            IncrementalCompileExecutor.CompileOutcome outcome = executor.compile(plan);
            engine.refreshIndexesIfModelChanged();
            Map<String, Object> fb = new LinkedHashMap<>();
            fb.put("required", false);
            fb.put("reason", null);
            feedback.put("fallback", fb);
            feedback.put("compile", outcome.toMap());
            feedback.put("diagnostics", executor.getDiagnostics());
            if (!outcome.isSuccess()) {
                String red = json ? JsonOut.write(feedback)
                        : "compiled: " + outcome.getCompiledModules();
                out.println(red);
                log.line(red);
                emitDiagnostics(executor, json, err, log);
                return Commands.EXIT_FAILED;
            }
            if (outcome.getCompiledModules().isEmpty()) {
                warnWhenPlannedToNothing(engine, changed, feedback, json, out, err, log);
                String line = json ? JsonOut.write(feedback) : "nothing to compile";
                out.println(line);
                log.line(line);
                return Commands.EXIT_OK;
            }
            String green = json ? JsonOut.write(feedback)
                    : "compiled: " + outcome.getCompiledModules();
            out.println(green);
            log.line(green);
            emitDiagnostics(executor, json, err, log);
            // a bare compile requests the whole reactor: sweep failures are
            // reported above and must not read as green
            if (!outcome.getRebuildFailed().isEmpty())
                return Commands.EXIT_FAILED;
            return Commands.EXIT_OK;
        }
        // the executor silently no-ops (compiled: []) when no planned module has
        // compile files — an existing java path that planned to nothing is the
        // fake-green shape, surface it before the quiet outcome line
        if (plan.getCompileFiles().isEmpty())
            warnWhenPlannedToNothing(engine, changed, feedback, json, out, err, log);

        // wave 12 (design 08): declared codegen tasks run in the fast path —
        // before-compile before javac, after-compile after; any failure delegates
        // the whole build to Maven with the task failure as the explicit reason.
        io.nop.ib.tasks.TaskRunner taskRunner =
                new io.nop.ib.tasks.TaskRunner(model, engine.getRepoRoot());
        int taskExit = runTriggerTasks(engine, model, plan.getCompileModules(),
                "before-compile", taskRunner, feedback, json, out, err);
        if (taskExit != Commands.EXIT_OK)
            return taskExit;

        IncrementalCompileExecutor executor = new IncrementalCompileExecutor(
                model, engine.getUnitStore());
        IncrementalCompileExecutor.CompileOutcome outcome = executor.compile(plan);
        // plan 2583 (audit B3 fix): the executor persisted refreshed unit indexes
        // for every compiled module — drop the engine's in-process index cache so
        // the SAME invocation's later stages (test selection, --cache fingerprints)
        // and any follow-up request read the fresh indexes, never the pre-compile
        // snapshot (new test classes were invisible to the selection set).
        engine.refreshIndexesIfModelChanged();
        Map<String, Object> fallbackInfo = new LinkedHashMap<>();
        fallbackInfo.put("required", false);
        fallbackInfo.put("reason", null);
        feedback.put("fallback", fallbackInfo);
        feedback.put("compile", outcome.toMap());
        Map<String, Object> diags = new LinkedHashMap<>();
        executor.getDiagnostics().forEach(diags::put);
        feedback.put("diagnostics", diags);
        // a green compile with zero compiled modules (every planned source vanished
        // between plan and executor) is indistinguishable from fake green — surface it
        if (outcome.isSuccess() && outcome.getCompiledModules().isEmpty())
            warnWhenPlannedToNothing(engine, changed, feedback, json, out, err, log);
        String human = json ? JsonOut.write(feedback)
                : "compiled: " + outcome.getCompiledModules();
        if (!outcome.isSuccess()) {
            // after-compile tasks are pointless on a red compile; report and fail.
            // diagnostics must be emitted on the failure path too — the success-path
            // block below is behind this early return, and a red compile without its
            // javac diagnostics reads as a silent exit 1 (coblab-rpg 2026-11-22 defect).
            feedback.put("tasks", taskRunner.getRecord());
            out.println(human);
            log.line(human);
            emitDiagnostics(executor, json, err, log);
            return Commands.EXIT_FAILED;
        }
        taskExit = runTriggerTasks(engine, model, plan.getCompileModules(),
                "after-compile", taskRunner, feedback, json, out, err);
        if (taskExit != Commands.EXIT_OK)
            return taskExit;
        feedback.put("tasks", taskRunner.getRecord());
        out.println(human);
        if (!json && !outcome.getMavenRebuilt().isEmpty())
            out.println("maven-rebuilt (generated-source/processor modules): "
                    + outcome.getMavenRebuilt());
        if (!json && !outcome.getDeletedFiles().isEmpty())
            out.println("deleted stale products: " + outcome.getDeletedFiles().size());
        // javac diagnostics + the run summary go to the run log in BOTH modes:
        // a compile failure must be fully readable after the process is gone
        log.line(human);
        emitDiagnostics(executor, json, err, log);
        return outcome.isSuccess() ? Commands.EXIT_OK : Commands.EXIT_FAILED;
    }

    /** javac diagnostics to stderr (human mode) and the run log (both modes). */
    private static void emitDiagnostics(IncrementalCompileExecutor executor, boolean json,
                                        PrintWriter err, RunLog log) {
        executor.getDiagnostics().forEach((m, d) -> {
            if (!d.isEmpty()) {
                String block = "--- " + m + " ---\n" + String.join("\n", d);
                if (!json)
                    err.println(block);
                log.line(block);
            }
        });
    }

    /**
     * Silent-empty plan diagnostic: when the given paths all exist on disk but the
     * plan produced nothing to compile (no compile files / modules / downstream),
     * the most likely cause is missing or stale model/module facts (e.g. the
     * daemon cached a model that predates the module). Never fail — but never
     * stay silent either: a quiet empty plan reads as fake green.
     */
    private static void warnWhenPlannedToNothing(IbEngine engine, List<String> changed,
                                                 Map<String, Object> feedback, boolean json,
                                                 PrintWriter out, PrintWriter err, RunLog log) {
        List<String> existingJava = changed.stream()
                .filter(p -> p.endsWith(".java"))
                .filter(p -> Files.isRegularFile(engine.getRepoRoot().resolve(p)))
                .toList();
        if (existingJava.isEmpty())
            return;
        String warn = "paths exist but nothing to compile — model/module facts missing? "
                + existingJava;
        feedback.put("warning", warn);
        String line = json ? JsonOut.write(Map.of("warning", warn)) : "[nop-ib] warning: " + warn;
        out.println(line);
        log.line(line);
    }

    /**
     * plan 2583: explicit owner resolution for the Maven delegation of an
     * out-of-scope changed path — {@code moduleForPath} returning null used to
     * surface as a bare NPE; fail loudly with a named-path message instead.
     */
    static String owningModuleOf(IbEngine engine, IbModel model, String repoRelPath) {
        IbModule module = model.moduleForPath(repoRelPath);
        if (module == null)
            throw new io.nop.ib.NopIbException(
                    "cannot map changed path to a reactor module: " + repoRelPath
                            + " (repo root " + engine.getRepoRoot() + ")");
        return module.getArtifactId();
    }

    /**
     * Runs the declared tasks of {@code trigger} for the modules in scope (design     * 08 §三). First failure delegates the whole build to Maven with the task
     * failure as the explicit reason (IB-T6) — the record (executed/skipped/failed
     * entries) stays visible in the feedback JSON either way.
     */
    private static int runTriggerTasks(IbEngine engine, IbModel model, List<String> moduleIds,
                                       String trigger, io.nop.ib.tasks.TaskRunner runner,
                                       Map<String, Object> feedback, boolean json,
                                       PrintWriter out, PrintWriter err) {
        boolean any = false;
        int failedExit = Commands.EXIT_OK;
        for (String moduleId : moduleIds) {
            for (io.nop.ib.tasks.TaskConfig task : engine.tasksFor(moduleId)) {
                if (!trigger.equals(task.trigger))
                    continue;
                any = true;
                IbModule module = model.module(moduleId);
                io.nop.ib.tasks.TaskRunner.TaskResult result = runner.run(module, task);
                if (!result.ok() && failedExit == Commands.EXIT_OK) {
                    err.println("task failed: " + result.reason()
                            + (result.stderrTail() != null ? "\n" + result.stderrTail() : ""));
                    logTaskFallback(feedback, result.reason());
                    failedExit = delegateToMaven(engine, model, moduleIds, feedback, json, out, err,
                            result.reason());
                }
            }
        }
        if (any || !runner.getRecord().isEmpty())
            feedback.put("tasks", runner.getRecord());
        return failedExit;
    }

    private static void logTaskFallback(Map<String, Object> feedback, String reason) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("required", true);
        fallback.put("reason", reason + tailStaleHint(reason));
        fallback.put("delegated", true);
        feedback.put("fallback", fallback);
    }

    /** self-correction hint appended to model-stale fallback reasons (live audit:
     * agents kept hitting "pom changed" without knowing the escape hatch). */
    static String tailStaleHint(String reason) {
        return reason != null && reason.startsWith("model-stale")
                ? " — fix: run 'nop-ib model refresh' (large projects take minutes)"
                : "";
    }

    /** last lines of Maven delegation output for JSON feedback (≤4000 chars). */
    static String outputTail(String text) {
        if (text == null)
            return "";
        String t = text.trim();
        if (t.isEmpty())
            return "(maven produced no output)";
        return t.length() > 4000 ? t.substring(t.length() - 4000) : t;
    }

    /** shared Maven delegation used by the task-failure path (mirrors the plan fallback block) */
    private static int delegateToMaven(IbEngine engine, IbModel model, List<String> moduleIds,
                                       Map<String, Object> feedback, boolean json, PrintWriter out,
                                       PrintWriter err, String reason) {
        MavenFallback fallback = new MavenFallback(engine.getRepoRoot());
        StringBuilder mout = new StringBuilder();
        StringBuilder merr = new StringBuilder();
        int code = fallback.compileModules(moduleIds, mout, merr);
        logTaskFallback(feedback, reason);
        feedback.put("tasks", new ArrayList<>());
        feedback.put("timings", Map.of("totalMs", 0));
        String line = json ? JsonOut.write(feedback)
                : "delegated to Maven after task failure: " + reason + "\n" + mout;
        out.println(line);
        return code == 0 ? Commands.EXIT_OK : Commands.EXIT_FAILED;
    }
}
