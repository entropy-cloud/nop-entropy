package io.nop.ib.engine;

import io.nop.ib.index.UnitIndex;
import io.nop.ib.index.UnitIndexStore;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;
import io.nop.ib.model.ModelStore;
import io.nop.ib.scope.ModuleGraph;
import io.nop.ib.scope.ScopePlanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Shared context for one CLI invocation (and later, one serve process): repo root
 * discovery, model access, unit index cache. Read-only commands never take the
 * build lock (IB-G5).
 */
public final class IbEngine {
    private final Path repoRoot;
    private final ModelStore modelStore;
    private final UnitIndexStore unitStore;
    private IbModel model;
    private final Map<String, UnitIndex> indexCache = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<io.nop.ib.tasks.TaskConfig>> taskCache =
            new ConcurrentHashMap<>();

    public IbEngine(Path startDir) {
        this.repoRoot = discoverRepoRoot(startDir);
        this.modelStore = new ModelStore(repoRoot);
        this.unitStore = new UnitIndexStore(repoRoot);
    }

    /**
     * Repo root discovery is Maven-generic (design 06 generalization): any project
     * with a pom.xml. The mvnw/.mvn markers take precedence when present (they
     * pin the nop-entropy worktree layout and mvnq integration).
     */
    /** wave 10: public — the service layer validates request projectDirs with it. */
    public static Path discoverRepoRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        Path firstPom = null;
        while (current != null) {
            if (Files.isRegularFile(current.resolve("mvnw"))
                    && Files.isDirectory(current.resolve(".mvn")))
                return current;
            if (firstPom == null && Files.isRegularFile(current.resolve("pom.xml")))
                firstPom = current;
            current = current.getParent();
        }
        if (firstPom == null)
            throw new IllegalArgumentException(
                    "not inside a Maven project (no mvnw/.mvn or pom.xml): " + start);
        // wave 11 fix (subdir identity): without the mvnw marker the FIRST pom
        // used to win, so a submission from <root>/moduleA made moduleA itself
        // the "repo root" — a second queue lane and a second .nop-ib state for
        // the SAME worktree, defeating per-project serialization. Climb the
        // contiguous pom chain instead: every ancestor that still has a pom.xml
        // is the same reactor (aggregator tops out where the chain breaks).
        Path top = firstPom;
        Path parent = top.getParent();
        while (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))) {
            top = parent;
            parent = parent.getParent();
        }
        return top;
    }

    public Path getRepoRoot() {
        return repoRoot;
    }

    public ModelStore getModelStore() {
        return modelStore;
    }

    public UnitIndexStore getUnitStore() {
        return unitStore;
    }

    public IbModel model() {
        if (model == null)
            model = modelStore.load();
        return model;
    }

    public String stalenessReason() {
        return modelStore.stalenessReason(model());
    }

    public ModuleGraph graph() {
        return new ModuleGraph(model());
    }

    public ScopePlanner planner() {
        return new ScopePlanner(model(), graph(), this::index, this::tasksFor);
    }

    /**
     * wave 12 (design 08 IB-T1): the merged {@code nop-ib.tasks.json} task set of
     * a module — root-level and module-level files along the root→basedir chain,
     * cached per engine. No config file = empty set (fast path untouched).
     */
    public java.util.List<io.nop.ib.tasks.TaskConfig> tasksFor(String moduleArtifactId) {
        IbModule m = model().module(moduleArtifactId);
        if (m == null)
            return java.util.List.of();
        return taskCache.computeIfAbsent(moduleArtifactId, id -> java.util.List.copyOf(
                io.nop.ib.tasks.TaskConfigLoader.loadForModule(repoRoot,
                        m.getBasedir() == null ? "" : m.getBasedir())));
    }

    /** Loads and caches a module's unit index; null when absent. */
    public UnitIndex index(String moduleArtifactId) {
        return indexCache.computeIfAbsent(moduleArtifactId, id ->
                unitStore.exists(id) ? unitStore.load(id) : null);
    }

    /**
     * serve-mode support: unit indexes written by a previous fast-path compile are
     * visible on disk immediately; drop the in-process cache so the next request
     * re-reads them.
     */
    public void refreshIndexesIfModelChanged() {
        indexCache.clear();
    }
}
