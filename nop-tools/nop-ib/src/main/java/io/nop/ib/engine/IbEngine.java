package io.nop.ib.engine;

import io.nop.ib.index.UnitIndex;
import io.nop.ib.index.UnitIndexStore;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.ModelStore;
import io.nop.ib.scope.ModuleGraph;
import io.nop.ib.scope.ScopePlanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
    private final Map<String, UnitIndex> indexCache = new HashMap<>();

    public IbEngine(Path startDir) {
        this.repoRoot = discoverRepoRoot(startDir);
        this.modelStore = new ModelStore(repoRoot);
        this.unitStore = new UnitIndexStore(repoRoot);
    }

    static Path discoverRepoRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("mvnw"))
                    && Files.isDirectory(current.resolve(".mvn")))
                return current;
            current = current.getParent();
        }
        throw new IllegalArgumentException("not inside a nop-entropy worktree (no mvnw/.mvn): " + start);
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
