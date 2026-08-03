/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.jobgraph.region;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The result of decomposing a {@link io.nop.stream.core.jobgraph.JobGraph}
 * into pipelined regions.
 *
 * <p>A decomposition contains:
 * <ul>
 *   <li>An ordered list of {@link Region} objects (ordered by the topological
 *       position of the first vertex encountered in each region);</li>
 *   <li>A lookup map from vertex ID to the {@link RegionId} that owns it.</li>
 * </ul>
 *
 * <p>This class is immutable after construction.
 *
 * @see Region
 * @see RegionId
 * @see RegionDecomposer
 */
public final class RegionDecomposition implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<Region> regions;
    private final Map<String, RegionId> vertexToRegion;

    /**
     * Constructs a decomposition from the given regions and vertex→region map.
     *
     * @param regions         the ordered list of regions (must not be null or empty)
     * @param vertexToRegion  the vertex ID → region ID map (must not be null)
     */
    public RegionDecomposition(List<Region> regions, Map<String, RegionId> vertexToRegion) {
        if (regions == null || regions.isEmpty()) {
            throw new IllegalArgumentException("regions must not be null or empty");
        }
        if (vertexToRegion == null) {
            throw new IllegalArgumentException("vertexToRegion must not be null");
        }
        this.regions = Collections.unmodifiableList(new java.util.ArrayList<>(regions));
        this.vertexToRegion = Collections.unmodifiableMap(new LinkedHashMap<>(vertexToRegion));
    }

    /**
     * Returns the ordered list of regions in this decomposition.
     *
     * @return an unmodifiable list of regions
     */
    public List<Region> getRegions() {
        return regions;
    }

    /**
     * Returns the number of regions in this decomposition.
     *
     * @return the region count
     */
    public int getRegionCount() {
        return regions.size();
    }

    /**
     * Returns the unmodifiable vertex ID → region ID map.
     *
     * @return an unmodifiable map
     */
    public Map<String, RegionId> getVertexToRegion() {
        return vertexToRegion;
    }

    /**
     * Returns the region ID of the specified vertex, or {@code null} if the
     * vertex is not part of any region in this decomposition.
     *
     * @param vertexId the vertex ID to look up
     * @return the region ID, or {@code null} if the vertex is unknown
     */
    public RegionId getRegionId(String vertexId) {
        return vertexToRegion.get(vertexId);
    }

    /**
     * Returns the set of all vertex IDs covered by this decomposition.
     *
     * @return an unmodifiable set of vertex IDs
     */
    public Set<String> getVertexIds() {
        return vertexToRegion.keySet();
    }

    @Override
    public String toString() {
        return "RegionDecomposition{regionCount=" + regions.size() + ", vertices=" + vertexToRegion.size() + '}';
    }
}
