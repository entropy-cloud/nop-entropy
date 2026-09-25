package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

import java.util.regex.Pattern;

/**
 * Comparison filter that compares a property value against a constant using an operator.
 */
public class CompareFilter implements Filter {
    public enum Op {
        EQ, NE, GT, GE, LT, LE
    }

    private final String propertyName;
    private final Op op;
    private final Object expectedValue;

    public CompareFilter(String propertyName, Op op, Object expectedValue) {
        this.propertyName = propertyName;
        this.op = op;
        this.expectedValue = expectedValue;
    }

    @Override
    public boolean apply(JsonAccessor accessor, Object root, Object item) {
        Object actual = propertyName == null ? item : accessor.getProperty(item, propertyName);
        if (actual == null)
            return op == Op.NE && expectedValue != null;
        if (expectedValue == null)
            return op == Op.NE && actual != null;
        int cmp = compareTo(actual, expectedValue);
        return switch (op) {
            case EQ -> cmp == 0;
            case NE -> cmp != 0;
            case GT -> cmp > 0;
            case GE -> cmp >= 0;
            case LT -> cmp < 0;
            case LE -> cmp <= 0;
        };
    }

    @SuppressWarnings("unchecked")
    private int compareTo(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) {
            double da = ((Number) a).doubleValue();
            double db = ((Number) b).doubleValue();
            return Double.compare(da, db);
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        return a.toString().compareTo(b.toString());
    }

    @Override
    public String toString() {
        return propertyName + " " + op + " " + expectedValue;
    }
}
