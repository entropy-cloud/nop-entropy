package io.nop.jpath;

import io.nop.jpath.JsonAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Wildcard segment that expands to all elements of an array or all values of an object.
 */
public class WildCardSegment implements Segment {
    public static final WildCardSegment INSTANCE = new WildCardSegment();

    private WildCardSegment() {
    }

    @Override
    public Object eval(JsonAccessor accessor, Object root, Object current) {
        if (current == null)
            return null;
        if (accessor.isArray(current)) {
            int size = accessor.size(current);
            List<Object> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                result.add(accessor.getArrayItem(current, i));
            }
            return result;
        }
        if (accessor.isMap(current)) {
            java.util.Set<String> keys = accessor.getPropertyNames(current);
            List<Object> result = new ArrayList<>(keys.size());
            for (String key : keys) {
                result.add(accessor.getProperty(current, key));
            }
            return result;
        }
        return null;
    }

    @Override
    public String toString() {
        return ".*";
    }
}
