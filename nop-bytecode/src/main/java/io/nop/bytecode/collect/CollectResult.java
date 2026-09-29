package io.nop.bytecode.collect;

import java.util.List;

/**
 * Result of one collection run: the full artifact set plus the delta against the manifest
 * state the run started from.
 */
public final class CollectResult {
    private final List<ClassArtifact> artifacts;
    private final List<ClassArtifact> added;
    private final List<ClassArtifact> changed;
    private final List<String> removed;
    private final int unchangedCount;

    public CollectResult(List<ClassArtifact> artifacts, List<ClassArtifact> added,
                         List<ClassArtifact> changed, List<String> removed, int unchangedCount) {
        this.artifacts = List.copyOf(artifacts);
        this.added = List.copyOf(added);
        this.changed = List.copyOf(changed);
        this.removed = List.copyOf(removed);
        this.unchangedCount = unchangedCount;
    }

    /** Full artifact set of this run, sorted by relativePath. */
    public List<ClassArtifact> artifacts() {
        return artifacts;
    }

    /** Artifacts not present in the starting manifest. */
    public List<ClassArtifact> added() {
        return added;
    }

    /** Artifacts present in the starting manifest whose sha256 changed. */
    public List<ClassArtifact> changed() {
        return changed;
    }

    /** Manifest paths absent from this run. */
    public List<String> removed() {
        return removed;
    }

    /** Artifacts whose (path, size, mtime, sha256) matched the manifest and were reused without rehashing. */
    public int unchangedCount() {
        return unchangedCount;
    }
}
