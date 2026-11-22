package io.nop.ib.compile;

import io.nop.ib.NopIbException;
import io.nop.ib.index.ClassInfo;
import io.nop.ib.index.UnitIndex;
import io.nop.ib.index.UnitIndexer;
import io.nop.ib.index.UnitIndexStore;
import io.nop.ib.javac.JavaCompilerFacade;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import io.nop.ib.scope.PlanResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Compiles the planned source sets with nop-javac file compilation against
 * materialized reactor-directory classpaths (design 05 §7.1, IB-D5/IB-D7), then
 * recomputes precise ABI diffusion: modules whose classes reference ABI-changed
 * classes get their referencing sources recompiled. Deleted sources are handled
 * from the old unit index (I4: never from the current snapshot alone).
 *
 * <p>Caller responsibilities: build lock held (product writes), model fresh,
 * plan without a Maven fallback requirement.
 */
public final class IncrementalCompileExecutor {
    private final IbModel model;
    private final UnitIndexStore unitStore;
    private final UnitIndexer indexer = new UnitIndexer();
    private final Map<String, UnitIndex> loadedIndexes = new HashMap<>();
    private final Map<String, List<String>> diagnostics = new TreeMap<>();
    /** modules whose products were (re)built during this run — cycle/重复 build guard */
    private final Set<String> builtThisRun = new HashSet<>();

    public IncrementalCompileExecutor(IbModel model, UnitIndexStore unitStore) {
        this.model = model;
        this.unitStore = unitStore;
    }

    public Map<String, List<String>> getDiagnostics() {
        return diagnostics;
    }

    public CompileOutcome compile(PlanResult plan) {
        CompileOutcome outcome = new CompileOutcome();
        // class binary names whose ABI changed during this run (drives diffusion)
        Map<String, Set<String>> abiChangedByModule = new HashMap<>();

        // mvn parity (2026-11-22 defect pair): before any planned compile, rebuild
        // every module whose products are missing/stale on disk (clean worktree,
        // externally deleted target/classes). Per-module upstream freshness is
        // additionally enforced in compileModule via ensureReactorDeps.
        buildMissingProducts(outcome);
        if (!outcome.isSuccess())
            return outcome;

        // pass 1: changed sources per module (reactor order from the plan)
        for (String moduleId : plan.getCompileModules()) {
            IbModule module = model.module(moduleId);
            if (module == null || module.getOutputDir() == null)
                continue;
            List<String> sources = plan.getCompileFiles().get(moduleId);
            if (sources == null || sources.isEmpty())
                continue;
            compileModule(module, sources, outcome, abiChangedByModule);
            if (!outcome.isSuccess())
                return outcome;
        }

        // pass 2: precise ABI diffusion into downstream candidate modules
        if (!plan.getDownstreamCandidates().isEmpty()) {
            Set<String> upstreamAbiChanged = new HashSet<>();
            for (Set<String> s : abiChangedByModule.values())
                upstreamAbiChanged.addAll(s);
            if (!upstreamAbiChanged.isEmpty()) {
                for (String moduleId : plan.getDownstreamCandidates()) {
                    IbModule module = model.module(moduleId);
                    if (module == null)
                        continue;
                    UnitIndex index = loadIndex(moduleId);
                    if (index == null)
                        continue; // candidate without index was flagged at plan time
                    List<String> recompileSources = sourcesReferencing(index, upstreamAbiChanged);
                    if (recompileSources.isEmpty())
                        continue;
                    // index source facts are module-relative; compileModule consumes
                    // repo-relative paths
                    List<String> repoRelSources = new ArrayList<>();
                    for (String moduleRel : recompileSources) {
                        // empty basedir (repo-root module): no prefix, else the
                        // path gains a leading slash and stops being repo-relative
                        String basedir = module.getBasedir();
                        repoRelSources.add(basedir == null || basedir.isEmpty()
                                ? moduleRel : basedir + "/" + moduleRel);
                    }
                    compileModule(module, repoRelSources, outcome, abiChangedByModule);
                    if (!outcome.isSuccess())
                        return outcome;
                }
            }
        }
        return outcome;
    }

    private String moduleRelPath(Path basedir, String repoRelPath) {
        try {
            return basedir.relativize(Path.of(model.getRepoRoot()).resolve(repoRelPath)).toString();
        } catch (IllegalArgumentException e) {
            return repoRelPath;
        }
    }

    private List<String> sourcesReferencing(UnitIndex index, Set<String> changedClasses) {
        Set<String> sources = new LinkedHashSet<>();
        for (ClassInfo info : index.getClasses().values()) {
            for (String ref : info.getRefs()) {
                if (changedClasses.contains(ref) && info.getSourceFile() != null) {
                    sources.add(info.getSourceFile());
                    break;
                }
            }
        }
        return new ArrayList<>(sources);
    }

    /**
     * 主/测试源根分区（plan 2575 follow-up）。repo 相对路径以 {@code /src/test/} 判定测试源；
     * 其余（含 {@code /src/main/} 与非标准布局）视为主源。供 compileModule 分两路编译——
     * 各路以自己的输出目录与 classpath 编译，杜绝主源写入 test-classes 的影子类。
     */
    static SourceSplit splitBySourceRoot(List<String> repoRelSources) {
        List<String> main = new ArrayList<>();
        List<String> test = new ArrayList<>();
        for (String source : repoRelSources) {
            // "/src/test/" covers nested modules; "src/test/" covers the repo-root
            // module (empty basedir, no leading slash — misrouting its test sources
            // to the main pass compiled them with the main classpath (no junit)
            // into target/classes: shadow classes + spurious errors)
            if (source != null && (source.contains("/src/test/") || source.startsWith("src/test/")))
                test.add(source);
            else
                main.add(source);
        }
        return new SourceSplit(List.copyOf(main), List.copyOf(test));
    }

    /** 主/测试源分区结果（不可变；调用方按 main → test 顺序消费）。 */
    record SourceSplit(List<String> mainSources, List<String> testSources) {
        boolean hasBoth() {
            return !mainSources.isEmpty() && !testSources.isEmpty();
        }

        boolean hasTest() {
            return !testSources.isEmpty();
        }
    }

    private void compileModule(IbModule module, List<String> moduleRelSources,
                               CompileOutcome outcome, Map<String, Set<String>> abiChangedByModule) {
        // 主/测试源分离（plan 2575 follow-up，影子类缺陷修复）：混合集合曾以
        // anyMatch("/src/test/") 整集合选择 testCompile=true——同一变更同时改主源+测试源时
        // （最常见形态），主源被以 -d target/test-classes 编译出影子类；下游 ABI 扩散同样
        // 会产出混合集合（模块索引含 main+test 源）。测试 fork 的 classpath 以 test-classes
        // 优先，影子类遮蔽 target/classes 的正确字节码（实测造成"改了不生效"与探针假绿）。
        SourceSplit split = splitBySourceRoot(moduleRelSources);
        if (split.hasBoth()) {
            // 先主后测试：测试编译的 classpath（testClasspath 含主输出目录）依赖主产物新鲜
            compileModule(module, split.mainSources(), outcome, abiChangedByModule);
            compileModule(module, split.testSources(), outcome, abiChangedByModule);
            return;
        }
        boolean testCompile = split.hasTest();
        Path basedir = model.resolve(module.getBasedir()).toPath();
        List<File> files = new ArrayList<>();
        Set<String> deletedSources = new HashSet<>();
        for (String source : moduleRelSources) {
            // plan output is repo-relative; module index facts are module-relative
            Path abs = model.getRepoRoot() != null
                    ? Path.of(model.getRepoRoot()).resolve(source)
                    : basedir.resolve(source);
            if (Files.isRegularFile(abs))
                files.add(abs.toFile());
            else
                deletedSources.add(moduleRelPath(basedir, source));
        }
        if (files.isEmpty() && deletedSources.isEmpty())
            return;

        ClasspathMaterializer materializer = new ClasspathMaterializer(model);
        ClasspathMaterializer.Materialized cp = testCompile
                ? materializer.testClasspath(module)
                : materializer.mainClasspath(module);
        cp.assertNoIntraProjectJars(model);
        if (!cp.complete()) {
            throw new NopIbException("classpath incomplete for module " + module.getArtifactId()
                    + "; missing: " + cp.missing());
        }

        // 2026-11-22 defect pair fix: an upstream reactor dir on this classpath
        // must exist on disk AND be fingerprint-fresh BEFORE javac runs — a
        // missing dir used to surface as javac 非法选项/找不到符号 (clean worktree),
        // an empty/partial dir as silent 找不到符号. Build the upstream chain first,
        // topologically, inside this lane — never report invalid-option.
        if (!ensureReactorDeps(module, cp, outcome))
            return;

        UnitIndex oldIndex = loadIndex(module.getArtifactId());

        // self output dir sits on the classpath and is the write target: ensure it
        // exists so javac prevalidation and incremental writes work after a clean
        try {
            Files.createDirectories(model.resolve(module.getOutputDir()).toPath());
            if (module.getTestOutputDir() != null && testCompile)
                Files.createDirectories(model.resolve(module.getTestOutputDir()).toPath());
        } catch (IOException e) {
            throw new NopIbException("cannot create output dir for " + module.getArtifactId(), e);
        }

        // I4 deletion sweep from the OLD index (never the current snapshot);
        // wave9 fix: sweep the tree the compile actually writes (test deletions
        // live in target/test-classes, not target/classes)
        Path sweepDir = testCompile && module.getTestOutputDir() != null
                ? model.resolve(module.getTestOutputDir()).toPath()
                : model.resolve(module.getOutputDir()).toPath();
        for (String deleted : deletedSources) {
            if (oldIndex != null) {
                for (String className : oldIndex.classesOfSource(deleted))
                    deleteClassProducts(sweepDir, className, outcome);
            }
        }

        // wave9 fix (found by the --cache invalidation probe): test sources must
        // compile into target/test-classes — the unconditional main outputDir
        // polluted target/classes and left target/test-classes stale, so the
        // dispatch classpath (test-classes first) kept serving the OLD bytecode.
        Path compileOutputDir = testCompile && module.getTestOutputDir() != null
                ? model.resolve(module.getTestOutputDir()).toPath()
                : model.resolve(module.getOutputDir()).toPath();
        List<File> sourcePath = new ArrayList<>();
        // wave6 6B-1 finding: effective-pom ALWAYS declares the default
        // sourceDirectory/testSourceDirectory even when the directory does not
        // exist on disk; nop-javac validates -sourcepath entries for existence
        // and hard-fails (javac itself ignores missing paths silently, returning
        // success with zero output — validator is the honest guard). Only pass
        // directories that actually exist so modules without a test tree
        // (liteflow-core precedent) compile on the fast path.
        if (module.getSourceDir() != null
                && Files.isDirectory(model.resolve(module.getSourceDir()).toPath()))
            sourcePath.add(model.resolve(module.getSourceDir()));
        if (module.getTestSourceDir() != null
                && Files.isDirectory(model.resolve(module.getTestSourceDir()).toPath()))
            sourcePath.add(model.resolve(module.getTestSourceDir()));

        // reactor-directory classpath entries are repo-root-relative; nop-javac's
        // pre-validation (validateClassPaths) checks File.exists() against the
        // SERVICE process cwd, so relative entries must be absolutized here —
        // otherwise the first test pre-compile after a clean fails validation
        // with invalid-option-value diagnostics (coblab-rpg rm6-WI6 observation).
        Path repoRoot = Path.of(model.getRepoRoot());
        List<String> classpathEntries = new ArrayList<>(cp.entries().size());
        for (String entry : cp.entries()) {
            Path entryPath = Path.of(entry);
            classpathEntries.add(entryPath.isAbsolute() ? entry
                    : repoRoot.resolve(entry).toString());
        }
        JavaCompilerFacade.CompileResult result = JavaCompilerFacade.compile(
                files, compileOutputDir,
                classpathEntries, module.getCompileRelease(), module.getCompileSource(),
                module.getCompileProc(), sourcePath, module.getCompileParameters(),
                module.getCompileAddModules(), module.getCompileEnablePreview());

        diagnostics.put(module.getArtifactId(), result.diagnostics());
        if (!result.success()) {
            outcome.failModule(module.getArtifactId());
            return;
        }

        // javac writes package-info.class only for ANNOTATED package-info sources;
        // Maven (error-prone rounds) also writes the unannotated ones — emit the
        // missing synthetic form ourselves so fast-path products match Maven's
        // set exactly (spring-batch parity observation)
        int release = parseRelease(module.getCompileRelease());
        PackageInfoEmitter.emitMissing(basedir.resolve("src/main/java"),
                model.resolve(module.getOutputDir()).toPath(), release);

        // rescan products, diff ABI, persist the refreshed unit index
        List<Path> outputs = new ArrayList<>();
        outputs.add(model.resolve(module.getOutputDir()).toPath());
        if (module.getTestOutputDir() != null)
            outputs.add(model.resolve(module.getTestOutputDir()).toPath());
        UnitIndex newIndex = indexer.scan(module.getArtifactId(), outputs, basedir);
        Set<String> abiChanged = new HashSet<>();
        if (oldIndex != null) {
            for (ClassInfo info : newIndex.getClasses().values()) {
                ClassInfo old = oldIndex.classInfo(info.getClassName());
                if (old == null || !old.getAbiDigest().equals(info.getAbiDigest())
                        || !old.getInlineConstants().equals(info.getInlineConstants()))
                    abiChanged.add(info.getClassName());
            }
        }
        abiChangedByModule.put(module.getArtifactId(), abiChanged);
        outcome.addCompiledModule(module.getArtifactId(), files.size(), abiChanged.size());
        unitStore.save(newIndex);
        loadedIndexes.put(module.getArtifactId(), newIndex);
    }

    // ------------------------------------------------------------------
    // 2026-11-22 defect pair: upstream product freshness + auto bootstrap
    // ------------------------------------------------------------------

    /**
     * Ensures every intra-project reactor directory on {@code cp} exists and is
     * fresh; missing/stale owners are built first (recursion yields the
     * dependency-topological order). Returns false when an upstream build failed
     * (outcome carries the failure + diagnostics).
     */
    private boolean ensureReactorDeps(IbModule module, ClasspathMaterializer.Materialized cp,
                                      CompileOutcome outcome) {
        Path repoRoot = Path.of(model.getRepoRoot());
        for (String entry : cp.entries()) {
            Path p = Path.of(entry);
            if (!p.isAbsolute())
                p = repoRoot.resolve(entry);
            if (p.toString().endsWith(".jar"))
                continue;
            IbModule owner = moduleForDir(p);
            if (owner == null || owner.getArtifactId().equals(module.getArtifactId()))
                continue;
            if (builtThisRun.contains(owner.getArtifactId()))
                continue;
            if (productsFresh(owner))
                continue;
            // buildWholeModule routes ineligible modules (processors, generated
            // sources) straight to Maven; a genuine failure fails this compile
            if (!buildWholeModule(owner, outcome, true))
                return false;
        }
        return outcome.isSuccess();
    }

    /** module whose main/test output directory equals {@code dir} (normalized), or null. */
    private IbModule moduleForDir(Path dir) {
        Path norm = dir.toAbsolutePath().normalize();
        Path repoRoot = Path.of(model.getRepoRoot()).toAbsolutePath().normalize();
        for (IbModule m : model.getModules()) {
            if (m.getOutputDir() != null
                    && repoRoot.resolve(m.getOutputDir()).toAbsolutePath().normalize().equals(norm))
                return m;
            if (m.getTestOutputDir() != null
                    && repoRoot.resolve(m.getTestOutputDir()).toAbsolutePath().normalize().equals(norm))
                return m;
        }
        return null;
    }

    /**
     * Fingerprint freshness: products exist on disk AND the unit index covers
     * every handwritten main source (each source maps to at least one indexed
     * class). A missing output dir, a missing index, or a source with no indexed
     * class (partial build, externally emptied dir) means stale — fail-safe
     * direction: we rebuild, never assume fresh.
     */
    boolean productsFresh(IbModule m) {
        if (m.getOutputDir() == null)
            return true;
        Path out = model.resolve(m.getOutputDir()).toPath();
        if (!Files.isDirectory(out))
            return false;
        if (m.getSourceDir() == null)
            return true; // no sources: dir existence is the whole contract
        Path srcRoot = model.resolve(m.getSourceDir()).toPath();
        if (!Files.isDirectory(srcRoot))
            return true;
        UnitIndex idx = loadIndex(m.getArtifactId());
        if (idx == null)
            return false;
        Path basedir = model.resolve(m.getBasedir() == null ? "" : m.getBasedir()).toPath();
        try (Stream<Path> stream = Files.walk(srcRoot)) {
            for (Path f : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel = basedir.toAbsolutePath().normalize()
                        .relativize(f.toAbsolutePath().normalize()).toString()
                        .replace(File.separatorChar, '/');
                if (rel.contains("/_gen/") || rel.startsWith("_gen/"))
                    continue; // codegen products are never on the fast path
                if (idx.classesOfSource(rel).isEmpty())
                    return false;
            }
        } catch (IOException e) {
            return false; // unreadable tree: fail safe, rebuild
        }
        return true;
    }

    /** fast-path whole-module build eligibility (mirrors the planner §6.5 gates). */
    private boolean fastPathEligible(IbModule m) {
        return !m.isProcessorModule()
                && !m.hasUnsupportedCompilerArgs()
                && !m.isDepsUnresolved();
    }

    /**
     * Builds a module's FULL main source set (mvn compile semantics for one
     * module), its own upstream chain guaranteed first via compileModule's
     * ensureReactorDeps hook. {@code builtThisRun} guards cycles/repeats.
     * When {@code critical} is false (integrity sweep) a failure is recorded on
     * the outcome (rebuildFailed + diagnostics) without aborting the caller —
     * only a plan that genuinely needs the module fails via the critical path.
     */
    private boolean buildWholeModule(IbModule dep, CompileOutcome outcome, boolean critical) {
        String id = dep.getArtifactId();
        builtThisRun.add(id);
        List<String> sources = mainSources(dep);
        if (sources.isEmpty()) {
            // no main sources: the contract is just an existing output dir
            try {
                Files.createDirectories(model.resolve(dep.getOutputDir()).toPath());
            } catch (IOException e) {
                throw new NopIbException("cannot create output dir for " + id, e);
            }
            return true;
        }
        if (fastPathEligible(dep)) {
            compileModule(dep, sources, outcome, new HashMap<>());
            if (outcome.isSuccess())
                return true;
        }

        // Fast path cannot own every module: generated-source modules (antlr4,
        // xlang codegen without a declared task) produce incomplete javac sets
        // and fail here. mvn parity requires the products regardless — delegate
        // THIS module (mvn -pl :id -am compile, upstreams included) and refresh
        // its unit index from the real products.
        StringBuilder mout = new StringBuilder();
        StringBuilder merr = new StringBuilder();
        int code = new MavenFallback(Path.of(model.getRepoRoot()))
                .compileModules(List.of(id), mout, merr);
        if (code == 0) {
            diagnostics.remove(id);
            outcome.clearFailure();
            outcome.addMavenRebuilt(id);
            refreshIndexAfterMaven(dep);
            return true;
        }
        String tail = (merr.length() > 0 ? merr.toString() : mout.toString());
        tail = tail.length() > 4000 ? tail.substring(tail.length() - 4000) : tail;
        List<String> diags = new ArrayList<>(diagnostics.getOrDefault(id, List.of()));
        diags.add("fast-path and Maven rebuild both failed (mvn exit " + code + "):\n" + tail);
        diagnostics.put(id, diags);
        if (!critical) {
            outcome.rebuildFailed(id);
            outcome.clearFailure();
            diagnostics.remove(id);
            diagnostics.put(id + " #sweep-failed", diags);
        }
        return outcome.isSuccess();
    }

    /** after a Maven rebuild, rescan products so freshness facts match disk. */
    private void refreshIndexAfterMaven(IbModule dep) {
        try {
            List<Path> outputs = new ArrayList<>();
            outputs.add(model.resolve(dep.getOutputDir()).toPath());
            if (dep.getTestOutputDir() != null
                    && Files.isDirectory(model.resolve(dep.getTestOutputDir()).toPath()))
                outputs.add(model.resolve(dep.getTestOutputDir()).toPath());
            UnitIndex idx = indexer.scan(dep.getArtifactId(), outputs,
                    model.resolve(dep.getBasedir() == null ? "" : dep.getBasedir()).toPath());
            unitStore.save(idx);
            loadedIndexes.put(dep.getArtifactId(), idx);
        } catch (Exception e) {
            // index refresh is best-effort: products are on disk either way;
            // a failed scan degrades to indexMissing widening at plan time
            diagnostics.computeIfAbsent(dep.getArtifactId(), k -> new ArrayList<>())
                    .add("warning: post-maven unit index refresh failed: " + e);
        }
    }

    /** all handwritten main sources of a module, repo-relative (mvn-compile set). */
    private List<String> mainSources(IbModule m) {
        if (m.getSourceDir() == null)
            return List.of();
        Path srcRoot = model.resolve(m.getSourceDir()).toPath();
        if (!Files.isDirectory(srcRoot))
            return List.of();
        Path repoRoot = Path.of(model.getRepoRoot()).toAbsolutePath().normalize();
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(srcRoot)) {
            for (Path f : stream.filter(p -> p.toString().endsWith(".java")).toList()) {
                String rel = repoRoot.relativize(f.toAbsolutePath().normalize()).toString()
                        .replace(File.separatorChar, '/');
                if (rel.contains("/_gen/"))
                    continue;
                out.add(rel);
            }
        } catch (IOException e) {
            throw new NopIbException("cannot enumerate sources of " + m.getArtifactId(), e);
        }
        return out;
    }

    /**
     * Reactor-wide product integrity sweep (mvn parity): every module whose
     * output directory is MISSING on disk and that is fast-path eligible gets a
     * full main-source rebuild in declared reactor order (per-module upstream
     * ordering is guaranteed recursively). Sweep failures are reported
     * (rebuildFailed + diagnostics) but do not abort the sweep — an unrelated
     * broken module must not block the rest of the reactor; a plan that really
     * needs a failed module fails later via {@link #ensureReactorDeps}.
     * Ineligible missing modules are reported on the outcome instead of
     * silently ignored.
     */
    public CompileOutcome buildMissingProducts(CompileOutcome outcome) {
        for (IbModule m : model.getModules()) {
            if (m.getOutputDir() == null || m.getSourceDir() == null)
                continue;
            if (!Files.isDirectory(model.resolve(m.getSourceDir()).toPath()))
                continue;
            if (Files.isDirectory(model.resolve(m.getOutputDir()).toPath()))
                continue; // sweep criterion: products missing entirely
            if (builtThisRun.contains(m.getArtifactId()))
                continue;
            // ineligible modules (annotation processors etc.) are handled inside
            // buildWholeModule: they skip javac and delegate to Maven directly
            buildWholeModule(m, outcome, false);
        }
        return outcome;
    }

    private static int parseRelease(String release) {
        if (release != null && !release.isBlank()) {
            try {
                return Integer.parseInt(release.trim());
            } catch (NumberFormatException ignored) {
                // non-numeric release (e.g. a version range): fall through
            }
        }
        return Runtime.version().feature();
    }

    /** Top-level name + prefix sweep: nested Foo$* classes and synthesized products (I4). */
    private void deleteClassProducts(Path outputDir, String className, CompileOutcome outcome) {
        String rel = className.replace('.', '/') + ".class";
        Path classFile = outputDir.resolve(rel);
        try {
            if (Files.deleteIfExists(classFile))
                outcome.addDeleted(classFile.toString());
        } catch (IOException e) {
            throw new NopIbException("failed to delete stale class " + classFile, e);
        }
        Path dir = classFile.getParent();
        if (dir != null && Files.isDirectory(dir)) {
            String base = className.substring(className.lastIndexOf('.') + 1);
            String prefix = base + "$";
            try (Stream<Path> stream = Files.list(dir)) {
                for (Path p : stream.toList()) {
                    String name = p.getFileName().toString();
                    if (name.startsWith(prefix) && name.endsWith(".class")
                            && Files.deleteIfExists(p))
                        outcome.addDeleted(p.toString());
                }
            } catch (IOException e) {
                throw new NopIbException("failed to sweep stale classes under " + dir, e);
            }
        }
    }

    private UnitIndex loadIndex(String moduleId) {
        return loadedIndexes.computeIfAbsent(moduleId, id ->
                unitStore.exists(id) ? unitStore.load(id) : null);
    }

    public static final class CompileOutcome {
        private boolean success = true;
        private String failedModule;
        private final List<String> compiledModules = new ArrayList<>();
        private final Map<String, int[]> stats = new TreeMap<>();
        private final List<String> deletedFiles = new ArrayList<>();
        /** stale modules not rebuilt because not fast-path eligible (report-only) */
        private List<String> staleNotRebuilt = List.of();
        /** integrity-sweep modules whose rebuild FAILED (report-only; non-fatal) */
        private final List<String> rebuildFailed = new ArrayList<>();
        /** modules rebuilt via Maven delegation (fast path cannot own them) */
        private final List<String> mavenRebuilt = new ArrayList<>();

        public boolean isSuccess() {
            return success;
        }

        public java.util.Optional<String> getFailedModule() {
            return java.util.Optional.ofNullable(failedModule);
        }

        public void failModule(String module) {
            this.success = false;
            this.failedModule = module;
        }

        public void addCompiledModule(String module, int files, int abiChanged) {
            compiledModules.add(module);
            stats.put(module, new int[]{files, abiChanged});
        }

        public void addDeleted(String file) {
            deletedFiles.add(file);
        }

        public void markStaleNotRebuilt(List<String> modules) {
            this.staleNotRebuilt = List.copyOf(modules);
        }

        /** records a non-fatal sweep rebuild failure; keeps success untouched. */
        public void rebuildFailed(String module) {
            rebuildFailed.add(module);
        }

        /** clears the failure flag after a non-fatal (sweep) failure was recorded. */
        public void clearFailure() {
            this.success = true;
            this.failedModule = null;
        }

        public List<String> getRebuildFailed() {
            return rebuildFailed;
        }

        public void addMavenRebuilt(String module) {
            mavenRebuilt.add(module);
        }

        public List<String> getMavenRebuilt() {
            return mavenRebuilt;
        }

        public List<String> getCompiledModules() {
            return compiledModules;
        }

        public Map<String, int[]> getStats() {
            return stats;
        }

        public List<String> getDeletedFiles() {
            return deletedFiles;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new TreeMap<>();
            m.put("success", success);
            m.put("failedModule", failedModule);
            m.put("compiledModules", compiledModules);
            Map<String, Object> st = new TreeMap<>();
            stats.forEach((k, v) -> st.put(k, Map.of("files", v[0], "abiChanged", v[1])));
            m.put("stats", st);
            m.put("deleted", deletedFiles);
            if (!staleNotRebuilt.isEmpty())
                m.put("staleNotRebuilt", staleNotRebuilt);
            if (!rebuildFailed.isEmpty())
                m.put("rebuildFailed", rebuildFailed);
            if (!mavenRebuilt.isEmpty())
                m.put("mavenRebuilt", mavenRebuilt);
            return m;
        }
    }
}
