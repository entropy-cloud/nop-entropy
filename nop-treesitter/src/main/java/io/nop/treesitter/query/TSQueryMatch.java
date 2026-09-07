package io.nop.treesitter.query;

import io.nop.treesitter.TSNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A query match: the matched pattern's index plus the capture set collected
 * while matching — one entry per capture slot in pattern order (the same
 * capture name may appear more than once when several pattern positions carry
 * it, mirroring the upstream {@code ts_query_cursor_next_capture} semantics).
 */
public record TSQueryMatch(int patternIndex, List<Capture> captures) {

    public TSQueryMatch {
        captures = List.copyOf(captures);
    }

    /**
     * First captured node under {@code name}, or null when the match has no
     * such capture.
     */
    public TSNode node(String captureName) {
        for (Capture capture : captures) {
            if (capture.name().equals(captureName)) {
                return capture.node();
            }
        }
        return null;
    }

    /**
     * All captured nodes under {@code name} in capture order.
     */
    public List<TSNode> nodes(String captureName) {
        return captures.stream()
                .filter(c -> c.name().equals(captureName))
                .map(Capture::node)
                .toList();
    }

    /**
     * The capture set as an insertion-ordered name to node map (first capture
     * wins per name).
     */
    public Map<String, TSNode> captureMap() {
        Map<String, TSNode> map = new LinkedHashMap<>();
        for (Capture capture : captures) {
            map.putIfAbsent(capture.name(), capture.node());
        }
        return map;
    }

    /**
     * One captured node: the interned capture id, its name and the node.
     */
    public record Capture(int captureId, String name, TSNode node) {
    }
}