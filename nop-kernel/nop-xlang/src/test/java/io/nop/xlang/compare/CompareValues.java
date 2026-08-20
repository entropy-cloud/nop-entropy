package io.nop.xlang.compare;

import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.xml.XNode;

/**
 * 对拍断言使用的值相等判定：equals 语义 + 类型一致（避免 1L vs 1 的隐式相等）。
 *
 * <p>XNode / SQL 为可变树/带标记串（无值 equals 实现，Object 同一性），对拍列各自构造实例——
 * 相等判定取序列化内容（I4 覆盖 B 的 Collect 族返回值承载；同树派生 → 内容确定一致）。
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
        if (expected instanceof XNode)
            return ((XNode) expected).xml().equals(((XNode) actual).xml());
        if (expected instanceof SQL)
            return expected.toString().equals(actual.toString());
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
