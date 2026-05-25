package io.nop.code.graph.diff;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class GraphSnapshot {
    private final long timestamp;
    private final Set<String> nodes;
    private final Set<EdgeKey> edges;
    private final Map<String, String> communityMap;

    public GraphSnapshot(Set<String> nodes, Set<EdgeKey> edges, Map<String, String> communityMap) {
        this.timestamp = System.currentTimeMillis();
        this.nodes = Collections.unmodifiableSet(nodes);
        this.edges = Collections.unmodifiableSet(edges);
        this.communityMap = Collections.unmodifiableMap(communityMap);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Set<String> getNodes() {
        return nodes;
    }

    public Set<EdgeKey> getEdges() {
        return edges;
    }

    public Map<String, String> getCommunityMap() {
        return communityMap;
    }

    public static class EdgeKey {
        private final String source;
        private final String target;

        public EdgeKey(String source, String target) {
            this.source = source;
            this.target = target;
        }

        public String getSource() {
            return source;
        }

        public String getTarget() {
            return target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EdgeKey edgeKey = (EdgeKey) o;
            return source.equals(edgeKey.source) && target.equals(edgeKey.target);
        }

        @Override
        public int hashCode() {
            return 31 * source.hashCode() + target.hashCode();
        }

        @Override
        public String toString() {
            return source + " -> " + target;
        }
    }
}
