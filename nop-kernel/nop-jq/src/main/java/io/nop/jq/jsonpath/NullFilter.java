package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

/**
 * Filter that checks if a property value is null.
 */
public class NullFilter implements Filter {
    private final String propertyName;
    private final boolean isNull;

    public NullFilter(String propertyName, boolean isNull) {
        this.propertyName = propertyName;
        this.isNull = isNull;
    }

    @Override
    public boolean apply(JsonAccessor accessor, Object root, Object item) {
        Object actual = propertyName == null ? item : accessor.getProperty(item, propertyName);
        boolean nullCheck = actual == null;
        return isNull == nullCheck;
    }

    @Override
    public String toString() {
        return isNull ? propertyName + " == null" : propertyName + " != null";
    }
}
