package io.nop.jpath;

import io.nop.jpath.JsonAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Segment that filters array elements based on a predicate.
 */
public class FilterSegment implements Segment {
    private final Filter filter;

    public FilterSegment(Filter filter) {
        this.filter = filter;
    }

    public Filter getFilter() {
        return filter;
    }

    @Override
    public Object eval(JsonAccessor accessor, Object root, Object current) {
        if (current == null)
            return null;
        if (!accessor.isArray(current))
            return null;
        int size = accessor.size(current);
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Object item = accessor.getArrayItem(current, i);
            if (filter.apply(accessor, root, item)) {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "?(" + filter + ")";
    }
}
