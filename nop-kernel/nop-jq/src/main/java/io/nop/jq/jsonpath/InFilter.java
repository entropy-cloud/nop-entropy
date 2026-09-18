package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

import java.util.Set;

/**
 * Filter that checks if a property value is in a set of values.
 */
public class InFilter implements Filter {
    private final String propertyName;
    private final Set<Object> values;

    public InFilter(String propertyName, Set<Object> values) {
        this.propertyName = propertyName;
        this.values = values;
    }

    @Override
    public boolean apply(JsonAccessor accessor, Object root, Object item) {
        Object actual = propertyName == null ? item : accessor.getProperty(item, propertyName);
        if (actual == null)
            return values.contains(null);
        for (Object v : values) {
            if (v != null && compareEquals(actual, v))
                return true;
        }
        return false;
    }

    private boolean compareEquals(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        return a.equals(b);
    }

    @Override
    public String toString() {
        return propertyName + " in " + values;
    }
}
