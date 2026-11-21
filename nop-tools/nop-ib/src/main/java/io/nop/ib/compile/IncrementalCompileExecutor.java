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
            return m;
        }
    }
}
