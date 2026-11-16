package io.nop.ib.cli;

import io.nop.ib.NopIbException;
import io.nop.ib.engine.GitChanges;
import io.nop.ib.engine.IbEngine;
import io.nop.ib.index.UnitIndexer;
import io.nop.ib.json.JsonOut;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import io.nop.ib.model.ModelRefresher;
import io.nop.ib.model.ModelStore;
import io.nop.ib.scope.PlanResult;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave 1 read-only command surface (design 05 §九): model refresh/show, status,
 * plan, explain, cache size. `model refresh` writes .nop-ib state only — never
 * build products — so it does not take the build lock.
 */
public final class Commands {
    private Commands() {
    }

    public static final int EXIT_OK = 0;
    public static final int EXIT_FAILED = 1;
    public static final int EXIT_FALLBACK_REJECTED = 2;
    public static final int EXIT_USAGE = 3;
    public static final int EXIT_PARTIAL = 6;
    public static final int EXIT_LOCK_TIMEOUT = 75;

    public static int model(IbEngine engine, List<String> args, PrintStream out, boolean json) {
        String sub = args.isEmpty() ? "show" : args.get(0);
        switch (sub) {
            case "index": {
                IbModel model = engine.model();
                int indexed = refreshUnitIndexes(engine, model);
                out.println("unit indexes rebuilt: " + indexed + " modules");
                return EXIT_OK;
            }
            case "refresh": {
                IbModel model = new ModelRefresher(engine.getRepoRoot()).refresh();
                engine.getModelStore().save(model);
                refreshUnitIndexes(engine, model);
                if (json) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ok", true);
                    m.put("modules", model.getModules().size());
                    m.put("mavenVersion", model.getMavenVersion());
                    m.put("dumpSha256", model.getDumpSha256());
                    out.println(JsonOut.write(m));
                } else {
                    out.println("model refreshed: " + model.getModules().size() + " modules, maven "
                            + model.getMavenVersion() + ", dump " + model.getDumpSha256());
                }
                return EXIT_OK;
            }
            case "show": {
                IbModel model = engine.model();
                if (json) {
                    out.println(JsonOut.write(model.toMap()));
                } else {
                    out.println("modules: " + model.getModules().size());
                    out.println("maven: " + model.getMavenVersion() + "  jdk: " + model.getJdkVersion());
                    out.println("localRepo: " + model.getLocalRepo());
                    String stale = engine.stalenessReason();
                    out.println("stale: " + (stale == null ? "no" : "yes (" + stale + ")"));
                }
                return EXIT_OK;
            }
            default:
                throw new NopIbException("usage error: unknown 'model' subcommand '" + sub
                        + "' (refresh|show)");
        }
    }

    /** (Re)build unit indexes for every module whose products exist. */
    public static int refreshUnitIndexes(IbEngine engine, IbModel model) {
        UnitIndexer indexer = new UnitIndexer();
        int indexed = 0;
        for (IbModule module : model.getModules()) {
            List<Path> outputs = new ArrayList<>();
            Path main = module.getOutputDir() != null
                    ? engine.getRepoRoot().resolve(module.getOutputDir()) : null;
            Path testOut = module.getTestOutputDir() != null
                    ? engine.getRepoRoot().resolve(module.getTestOutputDir()) : null;
            if (testOut != null && Files.isDirectory(testOut))
                outputs.add(testOut);
            if (main != null && Files.isDirectory(main))
                outputs.add(main);
            // modules with neither main nor test products (never built) have no index —
            // the planner reports indexMissing and widens, never prunes
            if (outputs.isEmpty())
                continue;
            engine.getUnitStore().save(indexer.scan(module.getArtifactId(), outputs,
                    engine.getRepoRoot().resolve(module.getBasedir())));
            indexed++;
        }
        return indexed;
    }

    public static int status(IbEngine engine, PrintStream out, boolean json) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean hasModel = engine.getModelStore().exists();
        m.put("ok", true);
        m.put("repoRoot", engine.getRepoRoot().toString());
        String stale = hasModel ? engine.stalenessReason() : "model missing";
        Map<String, Object> modelInfo = new LinkedHashMap<>();
        modelInfo.put("present", hasModel);
        modelInfo.put("stale", stale != null);
        modelInfo.put("staleReason", stale);
        m.put("model", modelInfo);
        m.put("changed", GitChanges.changedPaths(engine.getRepoRoot()));
        if (json) {
            out.println(JsonOut.write(m));
        } else {
            out.println("repo: " + engine.getRepoRoot());
            out.println("model: " + (hasModel ? (stale == null ? "ok" : "STALE: " + stale) : "missing (run 'nop-ib model refresh')"));
            out.println("changed files: " + ((List<?>) m.get("changed")).size());
        }
        return EXIT_OK;
    }

    public static int plan(IbEngine engine, List<String> paths, boolean allowPartial,
                           PrintStream out, boolean json) {
        ModelStore store = engine.getModelStore();
        if (!store.exists()) {
            out.println(json ? JsonOut.write(Map.of("ok", false,
                    "fallback", Map.of("required", true, "reason", "model missing: run 'nop-ib model refresh'")))
                    : "model missing: run 'nop-ib model refresh'");
            return EXIT_FALLBACK_REJECTED;
        }
        List<String> changed = paths != null && !paths.isEmpty() ? paths : GitChanges.changedPaths(engine.getRepoRoot());
        PlanResult plan = engine.planner().plan(changed, engine.stalenessReason(), allowPartial);
        if (json) {
            out.println(JsonOut.write(plan.toMap()));
        } else {
            out.println("changed: " + plan.getChanged().size() + " file(s)");
            out.println("compile modules: " + plan.getCompileModules());
            out.println("downstream candidates: " + plan.getDownstreamCandidates()
                    + (plan.isAbiPredictionPrecise() ? "" : " (abi prediction: superset)"));
            out.println("full-test modules: " + plan.getFullTestModules());
            out.println("selected tests: " + plan.getSelectedTests());
            if (!plan.getWarnings().isEmpty())
                plan.getWarnings().forEach(w -> out.println("warning: " + w));
            if (plan.requiresMavenFallback())
                out.println("fallback: " + plan.getFallbackReason());
        }
        if (plan.requiresMavenFallback() && !allowPartial)
            return EXIT_FALLBACK_REJECTED;
        if (plan.isPartial())
            return EXIT_PARTIAL;
        return EXIT_OK;
    }

    public static int explain(IbEngine engine, String target, PrintStream out, boolean json) {
        IbModel model = engine.model();
        IbModule module = model.moduleForPath(target);
        if (module == null)
            module = model.module(target);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("target", target);
        if (module == null) {
            m.put("ok", false);
            m.put("error", "target is neither a file inside a reactor module nor a module artifactId");
            out.println(json ? JsonOut.write(m) : m.get("error").toString());
            return EXIT_USAGE;
        }
        m.put("ok", true);
        m.put("module", module.getArtifactId());
        m.put("basedir", module.getBasedir());
        m.put("capability", engine.planner().capabilityLevel(module));
        var index = engine.index(module.getArtifactId());
        if (index != null) {
            java.util.Set<String> classes = target.endsWith(".java")
                    ? index.classesOfSource(moduleRelative(engine, module, target))
                    : java.util.Set.of();
            m.put("declaredClasses", classes);
            List<String> dependents = new ArrayList<>();
            for (var e : index.dependents().entrySet()) {
                if (classes.contains(e.getKey()))
                    dependents.addAll(e.getValue());
            }
            m.put("moduleDependents", dependents);
        } else {
            m.put("indexMissing", true);
        }
        out.println(json ? JsonOut.write(m) : JsonOut.write(m));
        return EXIT_OK;
    }

    private static String moduleRelative(IbEngine engine, IbModule module, String target) {
        String basedir = module.getBasedir() + "/";
        return target.startsWith(basedir) ? target.substring(basedir.length()) : target;
    }

    public static int cache(IbEngine engine, List<String> args, PrintStream out, boolean json) {
        String sub = args.isEmpty() ? "size" : args.get(0);
        switch (sub) {
            case "size": {
                long bytes = engine.getUnitStore().cacheSizeBytes();
                if (json)
                    out.println(JsonOut.write(Map.of("ok", true, "bytes", bytes)));
                else
                    out.println("unit index cache: " + bytes + " bytes");
                return EXIT_OK;
            }
            default:
                throw new NopIbException("usage error: unknown 'cache' subcommand '" + sub
                        + "' (size|verify|prune — verify/prune land with Wave 2 对拍)");
        }
    }
}

