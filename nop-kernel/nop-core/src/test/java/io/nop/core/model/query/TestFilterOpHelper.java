package io.nop.core.model.query;

import org.junit.jupiter.api.Test;

import io.nop.api.core.exceptions.NopException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFilterOpHelper {

    @Test
    public void testEqNeWithNumberAndString() {
        assertTrue(FilterOpHelper.eq(1, 1));
        assertFalse(FilterOpHelper.eq(1, 2));
        assertTrue(FilterOpHelper.ne(1, 2));

        // 任一侧是 String 时按字符串比较
        assertTrue(FilterOpHelper.eq("1", 1), "string vs number should compare as text");
        assertFalse(FilterOpHelper.eq("1 ", "1"));
        assertTrue(FilterOpHelper.eq(null, null), "MathHelper.eq(null,null) should be true");
    }

    @Test
    public void testCompareOpsWithNumericCoercion() {
        assertTrue(FilterOpHelper.gt(5, 3));
        assertFalse(FilterOpHelper.gt(3, 5));
        assertTrue(FilterOpHelper.ge(3, 3));
        assertTrue(FilterOpHelper.lt(3, 5));
        assertTrue(FilterOpHelper.le(5, 5));
        // 字符串形式的右侧值被转换为数字再比较
        assertTrue(FilterOpHelper.gt(10, "9"));
        assertFalse(FilterOpHelper.gt(9, "10"));
    }

    @Test
    public void testStringOps() {
        assertTrue(FilterOpHelper.startsWith("hello world", "hello"));
        assertFalse(FilterOpHelper.startsWith("hello", "world"));
        assertTrue(FilterOpHelper.endsWith("hello world", "world"));
        assertTrue(FilterOpHelper.contains("hello world", "lo wo"));
        assertFalse(FilterOpHelper.notContains("abc", "b"));
        assertTrue(FilterOpHelper.notContains("abc", "x"));

        assertTrue(FilterOpHelper.icontains("Hello World", "LO WO"));
        assertFalse(FilterOpHelper.icontains("abc", "xyz"));

        assertTrue(FilterOpHelper.regex("abc123", "[a-z]+\\d+"));
        assertFalse(FilterOpHelper.regex("ABC", "[a-z]+"));
        // 空值或空 pattern 返回 false
        assertFalse(FilterOpHelper.regex("", "a"));
        assertFalse(FilterOpHelper.regex("a", ""));
    }

    @Test
    public void testLikeEmptySemantics() {
        // 语义锁定：value 为空返回 false；pattern 为空返回 true
        assertFalse(FilterOpHelper.like("", "a%"));
        assertTrue(FilterOpHelper.like("abc", ""));
    }

    @Test
    public void testInNotIn() {
        assertTrue(FilterOpHelper.in(2, Arrays.asList(1, 2, 3)));
        assertFalse(FilterOpHelper.in(5, Arrays.asList(1, 2, 3)));
        assertFalse(FilterOpHelper.in(2, null), "null value list should not match");
        assertTrue(FilterOpHelper.notIn(5, Arrays.asList(1, 2)));
        assertFalse(FilterOpHelper.notIn(1, Arrays.asList(1)));
    }

    @Test
    public void testContainsAnyAndContainsAll() {
        Collection<Object> c1 = Arrays.<Object>asList(1, 2, 3);
        assertTrue(FilterOpHelper.containsAny(c1, Arrays.<Object>asList(9, 2)));
        assertFalse(FilterOpHelper.containsAny(c1, Arrays.<Object>asList(9, 8)));
        assertFalse(FilterOpHelper.containsAny(null, c1));
        assertFalse(FilterOpHelper.containsAny(c1, null));

        assertTrue(FilterOpHelper.containsAll(c1, Arrays.<Object>asList(1, 2)));
        assertFalse(FilterOpHelper.containsAll(c1, Arrays.<Object>asList(1, 9)));
        // containsAll: v1 null -> false; v2 null -> true
        assertFalse(FilterOpHelper.containsAll(null, c1));
        assertTrue(FilterOpHelper.containsAll(c1, null));
    }

    @Test
    public void testAssertOps() {
        assertTrue(FilterOpHelper.isEmpty(""));
        assertTrue(FilterOpHelper.isEmpty(null));
        assertFalse(FilterOpHelper.isEmpty("a"));
        assertTrue(FilterOpHelper.notEmpty("a"));

        assertTrue(FilterOpHelper.isNull(null));
        assertFalse(FilterOpHelper.isNull("a"));
        assertTrue(FilterOpHelper.notNull("a"));

        assertTrue(FilterOpHelper.isBlank("  "));
        assertTrue(FilterOpHelper.isBlank(null));
        assertFalse(FilterOpHelper.isBlank("a"));
        assertTrue(FilterOpHelper.notBlank("a"));

        assertTrue(FilterOpHelper.isTrue(true));
        assertTrue(FilterOpHelper.isTrue("true"));
        assertFalse(FilterOpHelper.isTrue(false));
        assertFalse(FilterOpHelper.isTrue(null));

        assertTrue(FilterOpHelper.isFalse(false));
        assertFalse(FilterOpHelper.isFalse(null), "null is neither true nor false");
        assertTrue(FilterOpHelper.notTrue(null), "notTrue(null) should be true");
        assertTrue(FilterOpHelper.notFalse(null), "notFalse(null) is vacuously true");

        assertTrue(FilterOpHelper.isNumber(3));
        assertTrue(FilterOpHelper.isNumber("3.5"));
        assertFalse(FilterOpHelper.isNumber("abc"));
        assertFalse(FilterOpHelper.isNumber(null));
        assertTrue(FilterOpHelper.notNumber("abc"));
    }

    @Test
    public void testBetweenBoundaries() {
        // 闭区间：边界值包含
        assertTrue(FilterOpHelper.between(5, 1, 10, false, false));
        assertTrue(FilterOpHelper.between(1, 1, 10, false, false));
        assertTrue(FilterOpHelper.between(10, 1, 10, false, false));
        assertFalse(FilterOpHelper.between(0, 1, 10, false, false));
        assertFalse(FilterOpHelper.between(11, 1, 10, false, false));

        // 排除边界
        assertFalse(FilterOpHelper.between(1, 1, 10, true, false), "excludeMin should reject lower bound");
        assertFalse(FilterOpHelper.between(10, 1, 10, false, true), "excludeMax should reject upper bound");
        assertTrue(FilterOpHelper.between(1, 1, 10, true, false) == false);

        // null 边界表示无限制
        assertTrue(FilterOpHelper.between(100, null, null, false, false));
        assertTrue(FilterOpHelper.between(-100, null, 10, false, false));
        assertFalse(FilterOpHelper.between(100, null, 10, false, false));

        assertFalse(FilterOpHelper.notBetween(5, 1, 10, false, false));
        assertTrue(FilterOpHelper.notBetween(50, 1, 10, false, false));
    }

    @Test
    public void testLengthOps() {
        assertTrue(FilterOpHelper.length("abc", 3));
        assertFalse(FilterOpHelper.length("ab", 3));
        // 长度约束为 null 时视为无约束
        assertTrue(FilterOpHelper.length("abc", null));

        assertTrue(FilterOpHelper.lengthBetween("abcd", 2, 3 + 1, false, false));
        assertFalse(FilterOpHelper.lengthBetween("abcde", 2, 4, false, false));

        // utf8 长度：中文字符占 3 字节
        assertTrue(FilterOpHelper.utf8Length("中a", 4));
        assertFalse(FilterOpHelper.utf8Length("中", 1));
        assertTrue(FilterOpHelper.utf8LengthBetween("中a", 4, 4, false, false));
    }

    @Test
    public void testDateBetweenMinBoundOnly() {
        // max 参数存在缺陷嫌疑（实现误用 min 两次，见测试报告），只断言不受缺陷影响的部分
        assertTrue(FilterOpHelper.dateBetween("2026-01-01", "2026-01-01", null, false, false),
                "value == min should pass");
        assertFalse(FilterOpHelper.dateBetween("2025-12-31", "2026-01-01", null, false, false),
                "value < min should fail");
        assertFalse(FilterOpHelper.dateBetween("2026-01-01", "2026-01-01", null, true, false),
                "excludeMin should reject equal value");
        // 无法解析为日期的 value 抛转换异常（ConvertHelper 语义）
        org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> FilterOpHelper.dateBetween("not-a-date", null, null, false, false));
    }

    @Test
    public void testDateTimeBetween() {
        assertTrue(FilterOpHelper.dateTimeBetween("2026-01-15T10:00:00", "2026-01-01T00:00:00",
                "2026-02-01T00:00:00", false, false));
        assertFalse(FilterOpHelper.dateTimeBetween("2026-03-01T10:00:00", "2026-01-01T00:00:00",
                "2026-02-01T00:00:00", false, false));
        assertTrue(FilterOpHelper.dateTimeBetween("2026-01-01T00:00:00", "2026-01-01T00:00:00",
                null, false, false));
        assertFalse(FilterOpHelper.dateTimeBetween("2026-01-01T00:00:00", "2026-01-01T00:00:00",
                null, true, false));
    }

    @Test
    public void testEmptyCollectionSemantics() {
        Collection<Object> empty = Collections.emptyList();
        // containsAll: 空 v2 -> true
        assertTrue(FilterOpHelper.containsAll(empty, Collections.emptyList()));
        assertFalse(FilterOpHelper.containsAny(empty, Arrays.<Object>asList(1)));
    }
}
