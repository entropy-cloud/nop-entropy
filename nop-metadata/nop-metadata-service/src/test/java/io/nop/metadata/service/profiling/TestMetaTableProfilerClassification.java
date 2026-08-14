package io.nop.metadata.service.profiling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-05 回归测试：profiler 列类型分类正确性。
 *
 * <p>旧实现用 {@code upper.contains(kw)} substring 匹配，{@code NUMERIC_KEYWORDS} 含 {@code "INT"}/
 * {@code "BOOLEAN"}，导致：
 * <ul>
 *   <li>{@code POINT}（几何类型）含子串 {@code INT} → 误判数值 → {@code SUM(POINT)} 产非法 SQL → 列落入
 *       per-column 错误路径，无任何 stats（而非正确回退 string stats）</li>
 *   <li>{@code BOOLEAN} 被显式列入数值集合 → 所有布尔列误判数值</li>
 * </ul>
 *
 * <p>修复后 {@code isNumericType} 改用显式数值类型名集合做 exact-match，几何/布尔/位域列正确回退
 * string stats 或 probeNumeric 运行时探测路径。
 *
 * <p>本测试置于 {@code io.nop.metadata.service.profiling} 包以直接访问 package-private 的
 * {@code isNumericType} / {@code isStringType}（无需反射）。
 */
public class TestMetaTableProfilerClassification {

    // ===== AR-05：误分类修正（POINT / BOOLEAN / BIT 不再误归数值）=====

    /** POINT（几何类型）含子串 "INT"，旧 contains 匹配误判数值 → 修复后 exact-match 返回 false。 */
    @Test
    public void testPointNotNumeric() {
        assertFalse(MetaTableProfiler.isNumericType("POINT"),
                "POINT (geometry) must not be classified numeric — old substring match on 'INT' was a bug");
    }

    /** BOOLEAN 已移出数值集合（布尔列不是数值）。 */
    @Test
    public void testBooleanNotNumeric() {
        assertFalse(MetaTableProfiler.isNumericType("BOOLEAN"),
                "BOOLEAN must not be classified numeric (removed from numeric set)");
    }

    /** BIT 裁定为非数值（BIT(1)=boolean / BIT(n>1)=bitfield，保守回退 string stats，不产非法 SUM）。 */
    @Test
    public void testBitNotNumeric() {
        assertFalse(MetaTableProfiler.isNumericType("BIT"),
                "BIT must not be classified numeric (conservative fallback to string stats)");
    }

    /** 其他含 "INT" 子串的非数值类型（如 POINTER/PRINT）也不被误匹配。 */
    @Test
    public void otherIntSubstringTypesNotNumeric() {
        assertFalse(MetaTableProfiler.isNumericType("POINTER"));
        assertFalse(MetaTableProfiler.isNumericType("PRINT"));
        assertFalse(MetaTableProfiler.isNumericType("PAINT"));
    }

    /** null / 空串 / 空白：返回 false（不抛异常）。 */
    @Test
    public void testNullAndBlankNotNumeric() {
        assertFalse(MetaTableProfiler.isNumericType(null));
        assertFalse(MetaTableProfiler.isNumericType(""));
        assertFalse(MetaTableProfiler.isNumericType("   "));
    }

    // ===== 合法数值类型名仍返回 true（防过度收缩）=====

    @Test
    public void testKnownNumericTypesClassified() {
        assertTrue(MetaTableProfiler.isNumericType("INT"));
        assertTrue(MetaTableProfiler.isNumericType("INTEGER"));
        assertTrue(MetaTableProfiler.isNumericType("TINYINT"));
        assertTrue(MetaTableProfiler.isNumericType("SMALLINT"));
        assertTrue(MetaTableProfiler.isNumericType("MEDIUMINT"));
        assertTrue(MetaTableProfiler.isNumericType("BIGINT"));
        assertTrue(MetaTableProfiler.isNumericType("DECIMAL"));
        assertTrue(MetaTableProfiler.isNumericType("NUMERIC"));
        assertTrue(MetaTableProfiler.isNumericType("NUMBER"));
        assertTrue(MetaTableProfiler.isNumericType("DOUBLE"));
        assertTrue(MetaTableProfiler.isNumericType("DOUBLE PRECISION"),
                "PG 'DOUBLE PRECISION' (space-composite name) must match as a whole term");
        assertTrue(MetaTableProfiler.isNumericType("FLOAT"));
        assertTrue(MetaTableProfiler.isNumericType("REAL"));
    }

    /** 大小写/前后空白不敏感（toUpperCase + trim）。 */
    @Test
    public void testCaseAndWhitespaceInsensitive() {
        assertTrue(MetaTableProfiler.isNumericType("int"));
        assertTrue(MetaTableProfiler.isNumericType("  Integer  "));
        assertTrue(MetaTableProfiler.isNumericType("double precision"));
        assertFalse(MetaTableProfiler.isNumericType("  point  "));
    }

    /** POINT 仍被识别为字符串可剖析类型之外的类型（不会进入 collectStringStats，会走 probeNumeric）。 */
    @Test
    public void testPointNotStringEither() {
        assertFalse(MetaTableProfiler.isStringType("POINT"));
        assertFalse(MetaTableProfiler.isStringType("BOOLEAN"));
    }

    /** 字符串类型名仍被 isStringType 正确识别（防回归）。 */
    @Test
    public void testKnownStringTypesClassified() {
        assertTrue(MetaTableProfiler.isStringType("VARCHAR"));
        assertTrue(MetaTableProfiler.isStringType("CHAR"));
        assertTrue(MetaTableProfiler.isStringType("TEXT"));
        assertTrue(MetaTableProfiler.isStringType("CLOB"));
    }
}
