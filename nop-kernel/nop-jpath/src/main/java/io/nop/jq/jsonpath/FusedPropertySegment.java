package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

import java.util.List;

/**
 * Optimized segment that fuses multiple consecutive PropertySegments into a single
 * evaluation step, avoiding per-segment overhead.
 */
public class FusedPropertySegment implements Segment {
    private final String[] propertyNames;

    public FusedPropertySegment(List<PropertySegment> segments) {
        this.propertyNames = new String[segments.size()];
        for (int i = 0; i < segments.size(); i++) {
            this.propertyNames[i] = segments.get(i).getPropertyName();
        }
    }

    public String[] getPropertyNames() {
        return propertyNames;
    }

    @Override
    public Object eval(JsonAccessor accessor, Object root, Object current) {
        for (String name : propertyNames) {
            if (current == null)
                return null;
            current = accessor.getProperty(current, name);
        }
        return current;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(".");
        for (int i = 0; i < propertyNames.length; i++) {
            if (i > 0) sb.append(".");
            sb.append(propertyNames[i]);
        }
        return sb.toString();
    }
}
