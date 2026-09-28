package io.nop.jpath;

import io.nop.jpath.JsonAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Segment that accesses an array element by index.
 */
public class ArrayAccessSegment implements Segment {
    private final int index;

    public ArrayAccessSegment(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public Object eval(JsonAccessor accessor, Object root, Object current) {
        if (current == null)
            return null;
        if (!accessor.isArray(current))
            return null;
        int size = accessor.size(current);
        int actualIndex = index < 0 ? size + index : index;
        if (actualIndex < 0 || actualIndex >= size)
            return null;
        return accessor.getArrayItem(current, actualIndex);
    }

    @Override
    public String toString() {
        return "[" + index + "]";
    }
}
