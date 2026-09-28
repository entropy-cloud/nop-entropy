package io.nop.jpath;

import io.nop.jpath.JsonAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep scan segment (..) that recursively searches for values matching a property name or filter.
 */
public class DeepScanSegment implements Segment {
    private final String propertyName;

    public DeepScanSegment(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public Object eval(JsonAccessor accessor, Object root, Object current) {
        List<Object> result = new ArrayList<>();
        deepScan(accessor, current, result);
        return result;
    }

    private void deepScan(JsonAccessor accessor, Object current, List<Object> result) {
        if (current == null)
            return;
        // Only try to get named property from map-like objects, not from lists
        if (propertyName != null && accessor.isMap(current)) {
            Object value = accessor.getProperty(current, propertyName);
            if (value != null) {
                result.add(value);
            }
        }
        if (accessor.isMap(current)) {
            for (String key : accessor.getPropertyNames(current)) {
                Object child = accessor.getProperty(current, key);
                deepScan(accessor, child, result);
            }
        } else if (accessor.isArray(current)) {
            int size = accessor.size(current);
            for (int i = 0; i < size; i++) {
                Object child = accessor.getArrayItem(current, i);
                deepScan(accessor, child, result);
            }
        }
    }

    @Override
    public String toString() {
        return propertyName == null ? ".." : ".." + propertyName;
    }
}
