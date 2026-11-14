package io.nop.ib.cli;

import io.nop.ib.compile.IncrementalCompileExecutor;
import io.nop.ib.compile.MavenFallback;
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

/**
 * {@code nop-ib compile}: selective compilation over the planned scope.
 * Semantics (design 05 §九/§7.3, roadmap item 16):
 * <ul>
 *   <li>plan first; a required fallback delegates to Maven via mvnq when
 *       {@code --on-fallback=maven} (default), refuses with exit 2 when
 *       {@code --on-fallback=fail};</li>
 *   <li>product writes take the shared {@code .mvn/build.lock}; lock timeout is
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
                IbModule module = model.module(artifactId.trim());
                if (module == null) {
                    err.println("usage error: -pl module not in reactor: " + artifactId);
                    return Commands.EXIT_USAGE;
                }
                Path src = engine.getRepoRoot().resolve(module.getSourceDir());
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
                if (changed.isEmpty()) {
                    // module with no main sources: nothing to fast-path compile
                    return Commands.EXIT_OK;
                }
            }
        } else {
            changed = GitChanges.changedPaths(engine.getRepoRoot());
        }

        PlanResult plan = engine.planner().plan(changed, engine.stalenessReason(), allowPartial);
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
                            ? List.of(model.moduleForPath(changed.get(0)).getArtifactId())
                            : plan.getCompileModules(), mout, merr);
            feedback.put("fallback", Map.of("required", true,
                    "reason", plan.getFallbackReason(), "delegated", true, "exit", code));
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

        if (plan.getCompileFiles().isEmpty() && plan.getDownstreamCandidates().isEmpty()) {
            String line = json ? JsonOut.write(feedback) : "nothing to compile";
            out.println(line);
            log.line(line);
            return Commands.EXIT_OK;
        }

        try (BuildLock lock = BuildLock.acquire(engine.getRepoRoot())) {
            if (lock == null) {
                out.println(json ? JsonOut.write(Map.of("ok", false, "error", "build lock timeout"))
                        : "build lock timeout (exit 75)");
                return Commands.EXIT_LOCK_TIMEOUT;
            }
            IncrementalCompileExecutor executor = new IncrementalCompileExecutor(
                    model, engine.getUnitStore());
            IncrementalCompileExecutor.CompileOutcome outcome = executor.compile(plan);
            Map<String, Object> fallbackInfo = new LinkedHashMap<>();
            fallbackInfo.put("required", false);
            fallbackInfo.put("reason", null);
            feedback.put("fallback", fallbackInfo);
            feedback.put("compile", outcome.toMap());
            Map<String, Object> diags = new LinkedHashMap<>();
            executor.getDiagnostics().forEach(diags::put);
            feedback.put("diagnostics", diags);
            String human = json ? JsonOut.write(feedback)
                    : "compiled: " + outcome.getCompiledModules();
            out.println(human);
            if (!json && !outcome.getDeletedFiles().isEmpty())
                out.println("deleted stale products: " + outcome.getDeletedFiles().size());
            // javac diagnostics + the run summary go to the run log in BOTH modes:
            // a compile failure must be fully readable after the process is gone
            log.line(human);
            executor.getDiagnostics().forEach((m, d) -> {
                if (!d.isEmpty()) {
                    String block = "--- " + m + " ---\n" + String.join("\n", d);
                    if (!json)
                        err.println(block);
                    log.line(block);
                }
            });
            return outcome.isSuccess() ? Commands.EXIT_OK : Commands.EXIT_FAILED;
        }
    }
}
