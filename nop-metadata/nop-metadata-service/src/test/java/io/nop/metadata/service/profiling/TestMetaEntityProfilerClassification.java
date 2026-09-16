package io.nop.metadata.service.profiling;

import org.junit.jupiter.api.Test;

import java.util.Locale;

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
public class TestMetaEntityProfilerClassification {

    // ===== AR-05：误分类修正（POINT / BOOLEAN / BIT 不再误归数值）=====

    /** POINT（几何类型）含子串 "INT"，旧 contains 匹配误判数值 → 修复后 exact-match 返回 false。 */
    @Test
    public void testPointNotNumeric() {
        assertFalse(MetaEntityProfiler.isNumericType("POINT"),
                "POINT (geometry) must not be classified numeric — old substring match on 'INT' was a bug");
    }

    /** BOOLEAN 已移出数值集合（布尔列不是数值）。 */
    @Test
    public void testBooleanNotNumeric() {
        assertFalse(MetaEntityProfiler.isNumericType("BOOLEAN"),
                "BOOLEAN must not be classified numeric (removed from numeric set)");
    }

    /** BIT 裁定为非数值（BIT(1)=boolean / BIT(n>1)=bitfield，保守回退 string stats，不产非法 SUM）。 */
    @Test
    public void testBitNotNumeric() {
        assertFalse(MetaEntityProfiler.isNumericType("BIT"),
                "BIT must not be classified numeric (conservative fallback to string stats)");
    }

    /** 其他含 "INT" 子串的非数值类型（如 POINTER/PRINT）也不被误匹配。 */
    @Test
    public void otherIntSubstringTypesNotNumeric() {
        assertFalse(MetaEntityProfiler.isNumericType("POINTER"));
        assertFalse(MetaEntityProfiler.isNumericType("PRINT"));
        assertFalse(MetaEntityProfiler.isNumericType("PAINT"));
    }

    /** null / 空串 / 空白：返回 false（不抛异常）。 */
    @Test
    public void testNullAndBlankNotNumeric() {
        assertFalse(MetaEntityProfiler.isNumericType(null));
        assertFalse(MetaEntityProfiler.isNumericType(""));
        assertFalse(MetaEntityProfiler.isNumericType("   "));
    }

    // ===== 合法数值类型名仍返回 true（防过度收缩）=====

    @Test
    public void testKnownNumericTypesClassified() {
        assertTrue(MetaEntityProfiler.isNumericType("INT"));
        assertTrue(MetaEntityProfiler.isNumericType("INTEGER"));
        assertTrue(MetaEntityProfiler.isNumericType("TINYINT"));
        assertTrue(MetaEntityProfiler.isNumericType("SMALLINT"));
        assertTrue(MetaEntityProfiler.isNumericType("MEDIUMINT"));
        assertTrue(MetaEntityProfiler.isNumericType("BIGINT"));
        assertTrue(MetaEntityProfiler.isNumericType("DECIMAL"));
        assertTrue(MetaEntityProfiler.isNumericType("NUMERIC"));
        assertTrue(MetaEntityProfiler.isNumericType("NUMBER"));
        assertTrue(MetaEntityProfiler.isNumericType("DOUBLE"));
        assertTrue(MetaEntityProfiler.isNumericType("DOUBLE PRECISION"),
                "PG 'DOUBLE PRECISION' (space-composite name) must match as a whole term");
        assertTrue(MetaEntityProfiler.isNumericType("FLOAT"));
        assertTrue(MetaEntityProfiler.isNumericType("REAL"));
    }

    /** 大小写/前后空白不敏感（toUpperCase + trim）。 */
    @Test
    public void testCaseAndWhitespaceInsensitive() {
        assertTrue(MetaEntityProfiler.isNumericType("int"));
        assertTrue(MetaEntityProfiler.isNumericType("  Integer  "));
        assertTrue(MetaEntityProfiler.isNumericType("double precision"));
        assertFalse(MetaEntityProfiler.isNumericType("  point  "));
    }

    /** POINT 仍被识别为字符串可剖析类型之外的类型（不会进入 collectStringStats，会走 probeNumeric）。 */
    @Test
    public void testPointNotStringEither() {
        assertFalse(MetaEntityProfiler.isStringType("POINT"));
        assertFalse(MetaEntityProfiler.isStringType("BOOLEAN"));
    }

    /** 字符串类型名仍被 isStringType 正确识别（防回归）。 */
    @Test
    public void testKnownStringTypesClassified() {
        assertTrue(MetaEntityProfiler.isStringType("VARCHAR"));
        assertTrue(MetaEntityProfiler.isStringType("CHAR"));
        assertTrue(MetaEntityProfiler.isStringType("TEXT"));
        assertTrue(MetaEntityProfiler.isStringType("CLOB"));
    }

    // ===== Cycle 2 / C8（adjudication-table-cycle2 §4 C8）：isStringType exact-match（沿 AR-05 形态）=====

    /**
     * isStringType 改 exact-match 后，标准字符串类型族全量收录（防过度收缩）：
     * 修复前 substring contains 命中的合法名称（VARCHAR/TINYTEXT 等含 CHAR/TEXT 子串）修复后必须仍命中。
     */
    @Test
    public void testStringTypeExactMatchStandardFamily() {
        assertTrue(MetaEntityProfiler.isStringType("CHAR"));
        assertTrue(MetaEntityProfiler.isStringType("NCHAR"));
        assertTrue(MetaEntityProfiler.isStringType("CHARACTER"));
        assertTrue(MetaEntityProfiler.isStringType("VARCHAR"));
        assertTrue(MetaEntityProfiler.isStringType("NVARCHAR"));
        assertTrue(MetaEntityProfiler.isStringType("VARCHAR2"));
        assertTrue(MetaEntityProfiler.isStringType("NVARCHAR2"));
        assertTrue(MetaEntityProfiler.isStringType("LONGVARCHAR"));
        assertTrue(MetaEntityProfiler.isStringType("TEXT"));
        assertTrue(MetaEntityProfiler.isStringType("TINYTEXT"));
        assertTrue(MetaEntityProfiler.isStringType("MEDIUMTEXT"));
        assertTrue(MetaEntityProfiler.isStringType("LONGTEXT"));
        assertTrue(MetaEntityProfiler.isStringType("CLOB"));
        assertTrue(MetaEntityProfiler.isStringType("NCLOB"));
        assertTrue(MetaEntityProfiler.isStringType("STRING"));
    }

    /**
     * exact-match 消除子串误分类：含 CHAR/TEXT/STRING 子串的非标准词条不再被误归字符串列
     * （对齐 AR-05 isNumericType 的 POINT⊃INT 修复语义——未知复合类型正确回退 probeNumeric 运行时探测，
     * 而非子串误路由）。旧 substring 实现下这些词条会被误判 true。
     */
    @Test
    public void testStringTypeSubstringCompositesNotClassified() {
        assertFalse(MetaEntityProfiler.isStringType("XCHARTHING"),
                "unknown composite containing 'CHAR' must not be classified string (exact-match)");
        assertFalse(MetaEntityProfiler.isStringType("CONTEXT_ID_TYPE"),
                "unknown composite containing 'TEXT' must not be classified string (exact-match)");
        assertFalse(MetaEntityProfiler.isStringType("SUPERSTRINGIFIER"),
                "unknown composite containing 'STRING' must not be classified string (exact-match)");
        assertFalse(MetaEntityProfiler.isStringType(null));
        assertFalse(MetaEntityProfiler.isStringType(""));
        assertFalse(MetaEntityProfiler.isStringType("   "));
    }

    /** isStringType 大小写/前后空白不敏感（与 isNumericType 对齐）。 */
    @Test
    public void testStringTypeCaseAndWhitespaceInsensitive() {
        assertTrue(MetaEntityProfiler.isStringType("varchar"));
        assertTrue(MetaEntityProfiler.isStringType("  Clob  "));
        assertTrue(MetaEntityProfiler.isStringType("longText"));
        assertFalse(MetaEntityProfiler.isStringType("  xcharthing  "));
    }

    // ===== Cycle 2 / P1-B（adjudication-table-cycle2 §2 #28/#29）：分类归一化 tr-TR 回归 =====

    /**
     * tr-TR 默认 locale 下小写类型名归一化必须仍正确分类：
     * 修复前默认 locale {@code toUpperCase()} 把小写 {@code i} 映射为带点 {@code İ}——
     * {@code "tinyint"} → {@code "TİNYINT"} ≠ {@code TINYINT}、{@code "varchar"} →
     * {@code "VARCHAR"}（无 i 恰好不受影响），{@code "int"} → {@code "İNT"} ≠ {@code INT}。
     */
    @Test
    public void turkishLocaleLowercaseTypeNamesStillClassified() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertTrue(MetaEntityProfiler.isNumericType("tinyint"),
                    "lowercase 'tinyint' must classify numeric under tr-TR (old default-locale uppercase mapped i to İ)");
            assertTrue(MetaEntityProfiler.isNumericType("int"));
            assertTrue(MetaEntityProfiler.isNumericType("bigint"));
            assertTrue(MetaEntityProfiler.isNumericType("decimal"));
            assertTrue(MetaEntityProfiler.isStringType("varchar"),
                    "lowercase 'varchar' must classify string under tr-TR");
            assertTrue(MetaEntityProfiler.isStringType("character varying"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
