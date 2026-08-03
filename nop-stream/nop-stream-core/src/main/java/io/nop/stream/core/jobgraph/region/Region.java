/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph.region;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A single pipelined connected component within a
 * {@link io.nop.stream.core.jobgraph.JobGraph}.
 *
 * <p>A region is a maximal set of vertices that are mutually reachable via
 * non-materialization (in-flight pipelined) edges. Vertices within the same
 * region share a single failure domain: a task failure anywhere in the region
 * requires restarting the entire region (because data flows through in-flight
 * queues that cannot be independently replayed). Vertices separated by a
 * materialization-enabled edge belong to different regions and can be
 * restarted independently (because the materialization point at the boundary
 * holds a replayable copy of the data).
 *
 * <p>This class is immutable after construction.
 *
 * @see RegionId
 * @see RegionDecomposition
 * @see RegionDecomposer
 */
public final class Region implements Serializable {

    private static final long serialVersionUID = 1L;

    private final RegionId id;
    private final Set<String> vertexIds;

    /**
     * Constructs a region with the given ID and member vertex IDs.
     *
     * @param id        the region identifier (must not be null)
     * @param vertexIds the set of vertex IDs belonging to this region (must not be null or empty)
     */
    public Region(RegionId id, Set<String> vertexIds) {
        if (id == null) {
            throw new IllegalArgumentException("Region id must not be null");
        }
        if (vertexIds == null || vertexIds.isEmpty()) {
            throw new IllegalArgumentException("Region vertexIds must not be null or empty");
        }
        this.id = id;
        this.vertexIds = Collections.unmodifiableSet(new LinkedHashSet<>(vertexIds));
    }

    /**
     * Returns the identifier of this region.
     *
     * @return the region ID (never null)
     */
    public RegionId getId() {
        return id;
    }

    /**
     * Returns the set of vertex IDs belonging to this region.
     *
     * @return an unmodifiable, insertion-ordered set of vertex IDs
     */
    public Set<String> getVertexIds() {
        return vertexIds;
    }

    /**
     * Returns the number of vertices in this region.
     *
     * @return the vertex count
     */
    public int getVertexCount() {
        return vertexIds.size();
    }

    /**
     * Checks whether this region contains the specified vertex.
     *
     * @param vertexId the vertex ID to check
     * @return {@code true} if the vertex belongs to this region
     */
    public boolean contains(String vertexId) {
        return vertexIds.contains(vertexId);
    }

    @Override
    public String toString() {
        return "Region{id=" + id + ", vertices=" + vertexIds + '}';
    }
}
