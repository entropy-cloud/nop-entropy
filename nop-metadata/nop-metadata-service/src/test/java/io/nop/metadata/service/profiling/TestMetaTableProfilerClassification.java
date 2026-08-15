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

    // ===== Cycle 2 / C8（adjudication-table-cycle2 §4 C8）：isStringType exact-match（沿 AR-05 形态）=====

    /**
     * isStringType 改 exact-match 后，标准字符串类型族全量收录（防过度收缩）：
     * 修复前 substring contains 命中的合法名称（VARCHAR/TINYTEXT 等含 CHAR/TEXT 子串）修复后必须仍命中。
     */
    @Test
    public void testStringTypeExactMatchStandardFamily() {
        assertTrue(MetaTableProfiler.isStringType("CHAR"));
        assertTrue(MetaTableProfiler.isStringType("NCHAR"));
        assertTrue(MetaTableProfiler.isStringType("CHARACTER"));
        assertTrue(MetaTableProfiler.isStringType("VARCHAR"));
        assertTrue(MetaTableProfiler.isStringType("NVARCHAR"));
        assertTrue(MetaTableProfiler.isStringType("VARCHAR2"));
        assertTrue(MetaTableProfiler.isStringType("NVARCHAR2"));
        assertTrue(MetaTableProfiler.isStringType("LONGVARCHAR"));
        assertTrue(MetaTableProfiler.isStringType("TEXT"));
        assertTrue(MetaTableProfiler.isStringType("TINYTEXT"));
        assertTrue(MetaTableProfiler.isStringType("MEDIUMTEXT"));
        assertTrue(MetaTableProfiler.isStringType("LONGTEXT"));
        assertTrue(MetaTableProfiler.isStringType("CLOB"));
        assertTrue(MetaTableProfiler.isStringType("NCLOB"));
        assertTrue(MetaTableProfiler.isStringType("STRING"));
    }

    /**
     * exact-match 消除子串误分类：含 CHAR/TEXT/STRING 子串的非标准词条不再被误归字符串列
     * （对齐 AR-05 isNumericType 的 POINT⊃INT 修复语义——未知复合类型正确回退 probeNumeric 运行时探测，
     * 而非子串误路由）。旧 substring 实现下这些词条会被误判 true。
     */
    @Test
    public void testStringTypeSubstringCompositesNotClassified() {
        assertFalse(MetaTableProfiler.isStringType("XCHARTHING"),
                "unknown composite containing 'CHAR' must not be classified string (exact-match)");
        assertFalse(MetaTableProfiler.isStringType("CONTEXT_ID_TYPE"),
                "unknown composite containing 'TEXT' must not be classified string (exact-match)");
        assertFalse(MetaTableProfiler.isStringType("SUPERSTRINGIFIER"),
                "unknown composite containing 'STRING' must not be classified string (exact-match)");
        assertFalse(MetaTableProfiler.isStringType(null));
        assertFalse(MetaTableProfiler.isStringType(""));
        assertFalse(MetaTableProfiler.isStringType("   "));
    }

    /** isStringType 大小写/前后空白不敏感（与 isNumericType 对齐）。 */
    @Test
    public void testStringTypeCaseAndWhitespaceInsensitive() {
        assertTrue(MetaTableProfiler.isStringType("varchar"));
        assertTrue(MetaTableProfiler.isStringType("  Clob  "));
        assertTrue(MetaTableProfiler.isStringType("longText"));
        assertFalse(MetaTableProfiler.isStringType("  xcharthing  "));
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
            assertTrue(MetaTableProfiler.isNumericType("tinyint"),
                    "lowercase 'tinyint' must classify numeric under tr-TR (old default-locale uppercase mapped i to İ)");
            assertTrue(MetaTableProfiler.isNumericType("int"));
            assertTrue(MetaTableProfiler.isNumericType("bigint"));
            assertTrue(MetaTableProfiler.isNumericType("decimal"));
            assertTrue(MetaTableProfiler.isStringType("varchar"),
                    "lowercase 'varchar' must classify string under tr-TR");
            assertTrue(MetaTableProfiler.isStringType("character varying"));
        } finally {
            Locale.setDefault(original);
        }
    }
}
