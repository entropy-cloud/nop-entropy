package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Executes a JsonPath pipeline (Segment[]) against a root object.
 * Handles list propagation: when a PropertySegment receives a list,
 * it maps over each element. Other segments receive the list directly.
 * <p>
 * Optimized for common cases:
 * - Single-segment fast path (avoids list allocation)
 * - Pre-sized result lists
 * - Reusable empty list constant
 */
public class JsonPathExecutor {
    private final JsonAccessor accessor;

    public JsonPathExecutor(JsonAccessor accessor) {
        this.accessor = accessor;
    }

    /**
     * Execute the pipeline and return all results.
     */
    public Object execute(List<Segment> segments, Object root) {
        if (segments.isEmpty())
            return root;

        // Fast path for single-segment pipelines
        if (segments.size() == 1) {
            return segments.get(0).eval(accessor, root, root);
        }

        Object current = root;
        for (Segment segment : segments) {
            current = evalSegment(segment, root, current);
            if (current == null)
                return null;
        }
        return current;
    }

    private Object evalSegment(Segment segment, Object root, Object current) {
        if (current instanceof List && segment instanceof PropertySegment) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) current;
            if (list.isEmpty())
                return list;
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                Object val = segment.eval(accessor, root, item);
                if (val instanceof List) {
                    result.addAll((List<Object>) val);
                } else if (val != null) {
                    result.add(val);
                }
            }
            return result;
        }
        return segment.eval(accessor, root, current);
    }

    /**
     * Execute the pipeline and return all results as a list.
     */
    public List<Object> executeAll(List<Segment> segments, Object root) {
        Object result = execute(segments, root);
        if (result == null)
            return Collections.emptyList();
        if (result instanceof List)
            return (List<Object>) result;
        List<Object> list = new ArrayList<>(1);
        list.add(result);
        return list;
    }
}
