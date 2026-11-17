package io.nop.ib.scope;

import io.nop.ib.index.ClassInfo;
import io.nop.ib.index.UnitIndex;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Scope computation over Maven's coarse filter plus the bytecode class graph
 * (design 05 §6.3/§6.4/§6.5). Every fallback signal is explicit and carries a
 * reason; no pruning happens when the model or the index for a module is missing
 * (I6: recall has no upper bound on missing data — full scope, never partial).
 *
 * <p>Wave 1 is read-only prediction: the ABI question for changed sources cannot
 * be answered before compilation, so cross-module candidates are reported as a
 * safe superset ({@code abiPredictionPrecise=false}). The compile executor
 * (Wave 2) recomputes precise ABI diffusion after recompiling.
 */
public final class ScopePlanner {
    /** beyond this many selected test classes a plan is flagged wideImpact (report-only) */
    private static final int WIDE_IMPACT_TEST_THRESHOLD = 2000;

    private final IbModel model;
    private final ModuleGraph graph;
    private final Function<String, UnitIndex> indexLoader;
    /** wave 12 (design 08 IB-T5): per-module declared task set; null in legacy direct construction */
    private final Function<String, java.util.List<io.nop.ib.tasks.TaskConfig>> taskLoader;

    public ScopePlanner(IbModel model, ModuleGraph graph, Function<String, UnitIndex> indexLoader) {
        this(model, graph, indexLoader, null);
    }

    public ScopePlanner(IbModel model, ModuleGraph graph, Function<String, UnitIndex> indexLoader,
                        Function<String, java.util.List<io.nop.ib.tasks.TaskConfig>> taskLoader) {
        this.model = model;
        this.graph = graph;
        this.indexLoader = indexLoader;
        this.taskLoader = taskLoader;
    }

    /** the module declares a before-compile task (wave 12 fallback suppression gate) */
    private boolean ownsBeforeCompileTask(String moduleId) {
        if (taskLoader == null)
            return false;
        for (io.nop.ib.tasks.TaskConfig t : taskLoader.apply(moduleId))
            if ("before-compile".equals(t.trigger))
                return true;
        return false;
    }

    private boolean ownsDeclaredTasks(IbModule module) {
        if (taskLoader == null || module == null)
            return false;
        return !taskLoader.apply(module.getArtifactId()).isEmpty();
    }

    public ModuleChanges classifyAll(List<String> changedPaths) {
        ModuleChanges changes = new ModuleChanges();
        for (String relPath : changedPaths) {
            IbModule module = model.moduleForPath(relPath);
            ChangeClassifier.Kind kind;
            if (module == null) {
                kind = ChangeClassifier.Kind.OTHER;
            } else {
                kind = classifyWithin(relPath, module);
            }
            changes.add(module != null ? module.getArtifactId() : null, kind, relPath);
        }
        return changes;
    }

    private ChangeClassifier.Kind classifyWithin(String relPath, IbModule module) {
        boolean inTestSrc = module.getTestSourceDir() != null && relPath.startsWith(module.getTestSourceDir() + "/");
        boolean inMainSrc = module.getSourceDir() != null && relPath.startsWith(module.getSourceDir() + "/");
        boolean inTestRes = inAny(relPath, module.getTestResourceDirs());
        boolean inMainRes = inAny(relPath, module.getResourceDirs());
        return new ChangeClassifier().classify(relPath, inMainSrc, inTestSrc, inMainRes, inTestRes);
    }

    private boolean inAny(String relPath, List<String> dirs) {
        if (dirs == null)
            return false;
        for (String dir : dirs) {
            if (relPath.startsWith(dir + "/"))
                return true;
        }
        return false;
    }

    /**
     * Full plan. Enforces the fallback matrix (§6.5) before any narrowing, and the
     * "no model = no pruning" rule via {@code requireModelFresh} (null = fresh).
     */
    public PlanResult plan(List<String> changedPaths, String staleModelReason, boolean allowPartial) {
        PlanResult plan = new PlanResult();
        plan.setChanged(changedPaths);

        if (staleModelReason != null) {
            plan.setFallbackReason("model-stale: " + staleModelReason);
            plan.warn("模型过期，全部快路径结论无效（硬约束 2：模型过期即全量）");
            return plan;
        }

        ModuleChanges changes = classifyAll(changedPaths);
        // wave6 audit F5 (revised): a source change OUTSIDE any reactor module
        // means the model cannot see that code (brand-new module, or non-reactor
        // tree). Warn-only let a new module's tests silently drop out of the
        // selection — the exact fake-green shape. Java/aspect sources force the
        // Maven fallback (mvn's reactor is the only authority for unseen code);
        // non-source files (docs, scripts) stay warn-only.
        for (String outside : changes.outsideModules()) {
            if (outside.endsWith(".java")) {
                plan.setFallbackReason("source change outside reactor modules: " + outside
                        + " (possible new module — model has no facts for it)");
                plan.warn("change outside reactor modules: " + outside);
            }
        }

        Set<String> changedModuleIds = new TreeSet<>();
        for (Map.Entry<String, List<String>> e : changes.all().entrySet()) {
            String moduleId = e.getKey().substring(0, e.getKey().indexOf('#'));
            changedModuleIds.add(moduleId);
        }

        // ---- fallback matrix (§6.5), evaluated per changed module
        boolean anyPomChange = false;
        for (String moduleId : changedModuleIds) {
            IbModule module = model.module(moduleId);
            if (module == null)
                continue;
            String level = capabilityLevel(module);

            if (changes.hasAny(moduleId, ChangeClassifier.Kind.POM)) {
                anyPomChange = true;
                continue;
            }
            if (changes.hasAny(moduleId, ChangeClassifier.Kind.GEN_OR_MODEL)
                    && !"L0".equals(level)
                    && !ownsBeforeCompileTask(moduleId)) {
                // L1 generated-product/model change, L2/L3 any model change -> Maven.
                // wave 12 (design 08): a module that declares a before-compile task
                // owns its generation — the change is handled by the task execution
                // path in compile, NOT by a whole-build Maven fallback.
                plan.setFallbackReason("fallback-maven module " + moduleId
                        + ": xlang model/template change in " + level + " module (design 01 §七)");
            }
            if (changes.hasAny(moduleId, ChangeClassifier.Kind.MAIN_JAVA)) {
                switch (level) {
                    case "L2" -> plan.setFallbackReason("fallback-maven module " + moduleId
                            + ": L2 annotation processor module (§6.5)");
                    case "L3" -> plan.setFallbackReason("fallback-maven module " + moduleId
                            + ": L3 AOP/special module (§6.5)");
                    default -> {
                        // L0/L1 handwritten source: fast path eligible
                    }
                }
            }
        }

        // ---- coarse module sets (§6.1)
        List<String> compileModules;
        List<String> testModules;
        if (anyPomChange) {
            compileModules = graph.pomPropagation(changedModuleIds);
            testModules = new ArrayList<>(compileModules);
            if (plan.getFallbackReason() == null)
                plan.setFallbackReason("pom-change: cold path re-export + affected modules delegated to Maven");
        } else {
            compileModules = graph.reverseClosure(changedModuleIds);
            testModules = new ArrayList<>(compileModules);
        }
        plan.setCompileModules(compileModules);

        // ---- wave 11 item 8: modules with unresolved dependency facts never
        // take the fast path (fail-safe: their dependency closure is not
        // trustworthy) — Maven fallback with a reason naming each such module.
        // A changed degraded module is in its own closure, so this covers both
        // "changed degraded module" and "degraded module pulled into scope".
        for (String moduleId : compileModules) {
            IbModule module = model.module(moduleId);
            if (module != null && module.isDepsUnresolved())
                plan.setFallbackReason("fallback-maven module " + moduleId
                        + ": dependency facts unresolved at refresh (depsUnresolved)"
                        + " — dependency closure not trustworthy, Maven fallback");
        }

        // ---- per-module compile files (changed handwritten sources)
        for (String moduleId : changedModuleIds) {
            IbModule module = model.module(moduleId);
            if (module == null)
                continue;
            List<String> files = new ArrayList<>(changes.pathsOf(moduleId, ChangeClassifier.Kind.MAIN_JAVA));
            files.addAll(changes.pathsOf(moduleId, ChangeClassifier.Kind.TEST_JAVA));
            if (!files.isEmpty())
                plan.getCompileFiles().put(moduleId, files);
        }

        // ---- cross-module downstream candidates via class graph (superset)
        Set<String> changedClasses = new HashSet<>();
        for (Map.Entry<String, List<String>> e : plan.getCompileFiles().entrySet()) {
            UnitIndex index = indexLoader.apply(e.getKey());
            if (index == null) {
                plan.warn("indexMissing module " + e.getKey()
                        + " — downstream prediction degraded to coarse closure");
                continue;
            }
            for (String source : e.getValue()) {
                changedClasses.addAll(index.classesOfSource(moduleRelativeSource(e.getKey(), source)));
            }
        }
        Set<String> downstream = new TreeSet<>();
        if (!changedClasses.isEmpty()) {
            for (String moduleId : compileModules) {
                if (changedModuleIds.contains(moduleId))
                    continue;
                UnitIndex index = indexLoader.apply(moduleId);
                if (index == null)
                    continue;
                for (ClassInfo entry : index.getClasses().values()) {
                    for (String ref : entry.getRefs()) {
                        if (changedClasses.contains(ref)) {
                            downstream.add(moduleId);
                            break;
                        }
                    }
                }
            }
            plan.setDownstreamCandidates(new ArrayList<>(downstream));
        }

        // ---- test selection (§6.4) + widening (§6.5)
        for (String moduleId : testModules) {
            IbModule module = model.module(moduleId);
            if (module == null)
                continue;
            if (isWidenedModule(moduleId, changes, changedModuleIds, testModules)) {
                plan.addFullTestModule(moduleId);
                continue;
            }
            UnitIndex index = indexLoader.apply(moduleId);
            if (index == null) {
                plan.addFullTestModule(moduleId);
                plan.warn("indexMissing module " + moduleId + " — full module tests (I6)");
                continue;
            }
            // surefire include-pattern parity (defect 14): the selection seed is
            // the dispatch surface — classes Maven would not execute must not
            // enter the selected set (they fail standalone and poison modules)
            List<String> selected = io.nop.ib.junit.SurefireClassFilter.filter(
                    selectTests(index, changedClasses), module);
            if (!selected.isEmpty())
                plan.addSelectedTests(moduleId, selected);
        }

        int totalSelected = 0;
        for (List<String> l : plan.getSelectedTests().values())
            totalSelected += l.size();
        if (totalSelected > WIDE_IMPACT_TEST_THRESHOLD) {
            plan.setWideImpact(true);
            plan.warn("wideImpact: " + totalSelected + " test classes selected (report-only)");
        }
        plan.setPartial(allowPartial && plan.requiresMavenFallback());
        if (plan.requiresMavenFallback() && !allowPartial) {
            plan.warn("fallback required; default policy is full Maven delegation "
                    + "(--allow-partial overrides, exit code 6)");
        }
        return plan;
    }

    /** §6.5 widening: config resources, test data, model/template, test-source changes. */
    private boolean isWidenedModule(String moduleId, ModuleChanges changes,
                                    Set<String> changedModuleIds, List<String> testModules) {
        if (changes.hasAny(moduleId, ChangeClassifier.Kind.CONFIG_RESOURCE)
                || changes.hasAny(moduleId, ChangeClassifier.Kind.TEST_DATA)
                || changes.hasAny(moduleId, ChangeClassifier.Kind.TEST_JAVA)
                || changes.hasAny(moduleId, ChangeClassifier.Kind.MAIN_RESOURCE)
                || changes.hasAny(moduleId, ChangeClassifier.Kind.TEST_RESOURCE))
            return true;
        // cross-module widening: model/test-data change anywhere in the coarse set
        // => every candidate module runs full tests (conservative consumer rule, v1)
        for (String changedModule : changedModuleIds) {
            if (changes.hasAny(changedModule, ChangeClassifier.Kind.GEN_OR_MODEL)
                    || changes.hasAny(changedModule, ChangeClassifier.Kind.TEST_DATA)) {
                return testModules.contains(moduleId) || changedModule.equals(moduleId);
            }
        }
        return false;
    }

    /** forward closure from test classes over the module's class graph. */
    List<String> selectTests(UnitIndex index, Set<String> changedClasses) {
        if (changedClasses.isEmpty())
            return List.of();
        List<String> selected = new ArrayList<>();
        for (ClassInfo test : index.testClasses()) {
            if (forwardClosureHits(index, test, changedClasses))
                selected.add(test.getClassName());
        }
        return selected;
    }

    private boolean forwardClosureHits(UnitIndex index, ClassInfo start, Set<String> changedClasses) {
        Set<String> visited = new HashSet<>();
        LinkedHashSet<String> queue = new LinkedHashSet<>();
        queue.add(start.getClassName());
        while (!queue.isEmpty()) {
            var it = queue.iterator();
            String current = it.next();
            it.remove();
            if (!visited.add(current))
                continue;
            ClassInfo info = index.classInfo(current);
            if (info == null)
                continue;
            for (String ref : info.getRefs()) {
                if (changedClasses.contains(ref))
                    return true;
                if (index.classInfo(ref) != null && !visited.contains(ref))
                    queue.add(ref);
            }
        }
        return false;
    }

    /** Evidence-based capability level (Wave 0 adjudication): AOP products and templates. */
    public String capabilityLevel(IbModule module) {
        boolean hasAopArtifacts = module.getOutputDir() != null
                && Files.isDirectory(model.resolve(module.getOutputDir()).toPath())
                && hasAopProducts(model.resolve(module.getOutputDir()).toPath());
        boolean ownsTemplates = module.getBasedir() != null
                && Files.isDirectory(model.resolve(module.getBasedir()).toPath())
                && hasXgenTemplates(model.resolve(module.getBasedir()).toPath())
                // wave 12 (design 08 IB-T5): a declared task set is authoritative
                // codegen ownership — declaration beats the file-scan heuristic
                || ownsDeclaredTasks(module);
        // check-only processors (error-prone et al.) do NOT escalate to L2 anymore:
        // their annotation-processing ROUNDS have a product side effect (javac
        // writes package-info.class for annotated package-info sources only during
        // rounds — spring-batch/@NullMarked), but the fast path now handles that
        // itself via the RoundTriggerProcessor (IncrementalCompileExecutor), no
        // Maven fallback needed
        return module.capabilityLevel(hasAopArtifacts, ownsTemplates);
    }

    private boolean hasAopProducts(Path outputDir) {
        try (var stream = Files.walk(outputDir, 8)) {
            return stream.anyMatch(p -> p.getFileName().toString().endsWith("__aop.class"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasXgenTemplates(Path basedir) {
        try (var stream = Files.walk(basedir, 5)) {
            return stream.anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("gen-") && name.endsWith(".xgen");
            });
        } catch (Exception e) {
            return false;
        }
    }

    private String moduleRelativeSource(String moduleId, String repoRelPath) {
        IbModule module = model.module(moduleId);
        String basedir = module != null ? module.getBasedir() : null;
        if (basedir != null && repoRelPath.startsWith(basedir + "/"))
            return repoRelPath.substring(basedir.length() + 1);
        return repoRelPath;
    }

    private String fileNameOf(String relSourcePath) {
        return relSourcePath.substring(relSourcePath.lastIndexOf('/') + 1);
    }
}
