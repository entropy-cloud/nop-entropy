package io.nop.ib.engine;

import io.nop.ib.index.UnitIndex;
import io.nop.ib.index.UnitIndexStore;
import io.nop.ib.model.IbModel;
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
    static Path discoverRepoRoot(Path start) {
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
        if (firstPom != null)
            return firstPom;
        throw new IllegalArgumentException(
                "not inside a Maven project (no mvnw/.mvn or pom.xml): " + start);
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
        return new ScopePlanner(model(), graph(), this::index);
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
