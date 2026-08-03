/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph.region;

import java.io.Serializable;
import java.util.Objects;

/**
 * A typed, immutable identifier for a pipelined region within a
 * {@link io.nop.stream.core.jobgraph.JobGraph}.
 *
 * <p>A region is a maximal set of vertices connected by non-materialization
 * (pipelined, in-flight) edges. Region IDs are assigned by
 * {@link RegionDecomposer} during decomposition and propagated through the
 * execution plan so that every {@link io.nop.stream.core.execution.Subtask}
 * can report which region it belongs to.
 *
 * <p>Region IDs are stable within a single decomposition run and are formatted
 * as {@code "region-<n>"} where {@code <n>} is the zero-based index of the
 * region in the decomposition order (topological order of first appearance).
 *
 * <p>This is the successor plan 2 deliverable that successor plan 3
 * (supervision loop) depends on: the supervision loop needs to know which
 * region a failing task belongs to so it can restart only that region's tasks
 * rather than the entire job.
 *
 * @see Region
 * @see RegionDecomposition
 * @see RegionDecomposer
 */
public final class RegionId implements Serializable, Comparable<RegionId> {

    private static final long serialVersionUID = 1L;

    private final String id;

    /**
     * Constructs a region ID from its string form.
     *
     * @param id the string identifier (must not be null)
     */
    public RegionId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("RegionId id must not be null");
        }
        this.id = id;
    }

    /**
     * Returns the string form of this region ID.
     *
     * @return the identifier string (never null)
     */
    public String getId() {
        return id;
    }

    @Override
    public int compareTo(RegionId other) {
        return this.id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegionId)) return false;
        return id.equals(((RegionId) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
