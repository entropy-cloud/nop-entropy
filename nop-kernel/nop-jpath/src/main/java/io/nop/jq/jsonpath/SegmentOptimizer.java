package io.nop.jq.jsonpath;

import java.util.ArrayList;
import java.util.List;

/**
 * Optimizes a Segment pipeline by fusing consecutive PropertySegments
 * into a single FusedPropertySegment, reducing per-segment overhead.
 */
public class SegmentOptimizer {

    /**
     * Optimize the segment pipeline by fusing consecutive PropertySegments.
     */
    public static List<Segment> optimize(List<Segment> segments) {
        List<Segment> result = new ArrayList<>(segments.size());
        List<PropertySegment> pendingProps = new ArrayList<>();

        for (Segment seg : segments) {
            if (seg instanceof PropertySegment) {
                pendingProps.add((PropertySegment) seg);
            } else {
                if (!pendingProps.isEmpty()) {
                    result.add(flushPendingProps(pendingProps));
                    pendingProps.clear();
                }
                result.add(seg);
            }
        }
        if (!pendingProps.isEmpty()) {
            result.add(flushPendingProps(pendingProps));
        }
        return result;
    }

    private static Segment flushPendingProps(List<PropertySegment> props) {
        if (props.size() == 1) {
            return props.get(0);
        }
        return new FusedPropertySegment(props);
    }
}
