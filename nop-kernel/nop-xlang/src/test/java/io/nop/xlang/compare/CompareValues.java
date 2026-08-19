package io.nop.xlang.compare;

/**
 * 对拍断言使用的值相等判定：equals 语义 + 类型一致（避免 1L vs 1 的隐式相等）。
 */
public final class CompareValues {
    private CompareValues() {
    }

    public static boolean typedEquals(Object expected, Object actual) {
        if (expected == actual)
            return true;
        if (expected == null || actual == null)
            return false;
        if (expected.getClass() != actual.getClass())
            return false;
        return expected.equals(actual);
    }

    public static String display(Object value) {
        if (value == null)
            return "null";
        return value.getClass().getSimpleName() + "(" + value + ")";
    }

    public static String describeTypedMismatch(Object expected, Object actual) {
        return "expected=" + display(expected) + " but was=" + display(actual);
    }
}
