package io.nop.jpath;

import io.nop.jpath.JsonAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Array slice segment: [start:end] extracts a sub-range of array elements.
 * Negative start/end are relative to the end of the array.
 */
public class RangeSegment implements Segment {
    private final int start;
    private final int end;

    public RangeSegment(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    @Override
    public Object eval(JsonAccessor accessor, Object root, Object current) {
        if (current == null)
            return null;
        if (!accessor.isArray(current))
            return null;
        int size = accessor.size(current);
        int from = start < 0 ? Math.max(0, size + start) : Math.min(start, size);
        int to = end == Integer.MAX_VALUE ? size : (end < 0 ? Math.max(0, size + end) : Math.min(end, size));
        List<Object> result = new ArrayList<>();
        for (int i = from; i < to; i++) {
            result.add(accessor.getArrayItem(current, i));
        }
        return result;
    }

    @Override
    public String toString() {
        return "[" + start + ":" + end + "]";
    }
}
