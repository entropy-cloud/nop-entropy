package io.nop.ib.compile;

import io.nop.ib.NopIbException;
import io.nop.ib.model.IbModel;
import io.nop.ib.model.IbModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Incremental resource sync (mvn resources:resources parity for the fast path).
 * The planner classifies MAIN_RESOURCE/TEST_RESOURCE changes and widens test
 * selection, but nothing used to copy the changed files into target/classes /
 * target/test-classes — downstream consumers (contract tests reading resources
 * from the output dir) kept seeing the OLD file: fake red / fake green.
 *
 * <p>Scope: only files the caller already knows changed (explicit --paths or
 * the git working-tree change set). A full out-of-sync copy is NOT attempted —
 * the Maven fallback path owns full parity. Deletions are mirrored (source gone
 * → stale product deleted), matching mvn's stale-resource purge behavior.
 */
public final class ResourceSync {
    private final IbModel model;
    private final List<String> copied = new ArrayList<>();
    private final List<String> deleted = new ArrayList<>();
    /** per-module summary: artifactId -> [copied, deleted] */
    private final Map<String, int[]> byModule = new TreeMap<>();

    public ResourceSync(IbModel model) {
        this.model = model;
    }

    /**
     * Syncs every changed path that lives inside one of the owning module's
     * declared resource dirs; other paths are ignored (they belong to the
     * compile/test classifiers). A copy/delete failure aborts with a
     * {@link NopIbException} naming the file — never silently out of sync.
     */
    public void sync(Collection<String> changedRepoRelPaths) {
        for (String rel : changedRepoRelPaths) {
            IbModule module = model.moduleForPath(rel);
            if (module == null)
                continue;
            Path repoRoot = Path.of(model.getRepoRoot());
            String outDir = outputDirFor(rel, module);
            if (outDir == null)
                continue;
            Path target = repoRoot.resolve(outDir).resolve(subPath(rel, module, outDir));
            Path source = repoRoot.resolve(rel);
            try {
                if (Files.isRegularFile(source)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    copied.add(rel);
                    count(module, 0);
                } else {
                    Files.deleteIfExists(target);
                    deleted.add(rel);
                    count(module, 1);
                }
            } catch (IOException e) {
                throw new NopIbException("resource sync failed for " + rel + " -> " + target, e);
            }
        }
    }

    public List<String> getCopied() {
        return copied;
    }

    public List<String> getDeleted() {
        return deleted;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("copied", copied.size());
        m.put("deleted", deleted.size());
        if (!copied.isEmpty())
            m.put("copiedFiles", copied);
        if (!deleted.isEmpty())
            m.put("deletedFiles", deleted);
        return m;
    }

    private void count(IbModule module, int deletedFlag) {
        byModule.computeIfAbsent(module.getArtifactId(), k -> new int[2])[deletedFlag]++;
    }

    /** output dir (repo-relative) the resource belongs to, or null when not a declared resource. */
    private String outputDirFor(String rel, IbModule module) {
        if (inAny(rel, module.getResourceDirs()))
            return module.getOutputDir();
        if (inAny(rel, module.getTestResourceDirs()))
            return module.getTestOutputDir() != null ? module.getTestOutputDir() : module.getOutputDir();
        return null;
    }

    /** the resource path relative to its resource dir (keeps the in-jar layout). */
    private String subPath(String rel, IbModule module, String outDir) {
        for (String dir : concat(module.getResourceDirs(), module.getTestResourceDirs())) {
            if (rel.startsWith(dir + "/"))
                return rel.substring(dir.length() + 1);
        }
        // not under any dir (defensive): fall back to the module-relative path
        String basedir = module.getBasedir() == null ? "" : module.getBasedir();
        String moduleRel = !basedir.isEmpty() && rel.startsWith(basedir + "/")
                ? rel.substring(basedir.length() + 1) : rel;
        return moduleRel.contains("/") ? moduleRel.substring(moduleRel.lastIndexOf('/') + 1) : moduleRel;
    }

    private static boolean inAny(String rel, List<String> dirs) {
        if (dirs == null)
            return false;
        for (String dir : dirs)
            if (rel.startsWith(dir + "/"))
                return true;
        return false;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a == null ? List.of() : a);
        if (b != null)
            out.addAll(b);
        return out;
    }
}
