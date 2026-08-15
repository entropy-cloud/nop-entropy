package io.nop.metadata.service.lineage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INV-LOCALE（Cycle 2 / P1-B，adjudication-table-cycle2 §2 #13-#24）：SQL 血缘抽取器
 * registry/集合键归一化的 tr-TR 默认 locale 回归。
 *
 * <p>修复前 CTE 名集合与列级抽取器各类 registry 键使用默认 locale {@code toLowerCase()}：
 * tr-TR 下大写 {@code I} 小写化为无点 {@code ı}——大写声明 + 小写引用（或反之）的标识符两侧映射不一致，
 * CTE 被误报为物理源表（AR-08 回归）/ registry 键失配。修复后机器键语义统一 {@code Locale.ROOT}。
 */
public class TestSqlExtractorsLocale {

    private final SqlSourceTableExtractor sourceExtractor = new SqlSourceTableExtractor();
    private final SqlColumnLineageExtractor columnExtractor = new SqlColumnLineageExtractor();

    /**
     * tr-TR 下大写声明 + 小写引用的 CTE 名仍必须被排除（不误报为物理源表）：
     * 旧实现 cteNames 含 {@code ıtem_ınfo}（大写 I→ı）而引用侧 {@code item_info}（普通 i）→ 失配。
     */
    @Test
    public void turkishLocaleCteNameCaseMismatchStillExcluded() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            List<SqlTableReference> refs = sourceExtractor.extract(
                    "WITH ITEM_INFO AS (SELECT a FROM REAL_SRC) SELECT b FROM item_info");
            assertEquals(1, refs.size(), "only REAL_SRC must be reported: " + refs);
            assertEquals("REAL_SRC", refs.get(0).getSimpleName(),
                    "CTE declared ITEM_INFO / referenced item_info must stay excluded under tr-TR");
        } finally {
            Locale.setDefault(original);
        }
    }

    /**
     * tr-TR 下混合大小写表名 + 小写别名的列级抽取归属仍正确：
     * 旧实现 owner 键（{@code alias.toLowerCase()}）与 registry 键在 I/i 两侧形态不一致时失配。
     */
    @Test
    public void turkishLocaleColumnExtractionMixedCaseIdentifiers() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            // 源表 ITEM_SRC（大写 I）经别名 it（小写 i）引用——别名归一化两侧须一致
            List<ColumnLineageCandidate> cs = columnExtractor.extract(
                    "SELECT it.a AS x FROM ITEM_SRC it");
            assertEquals(1, cs.size(), "one resolved candidate expected: " + cs);
            ColumnLineageCandidate r = cs.get(0);
            assertFalse(r.isUnresolvable(), "candidate must resolve under tr-TR: " + r);
            assertEquals("x", r.getTargetColumn());
            assertEquals("ITEM_SRC", r.getSourceTableName());
            assertEquals("a", r.getSourceColumn());
        } finally {
            Locale.setDefault(original);
        }
    }

    /** 默认 locale 回归基线：CTE 排除与列级抽取在默认 locale 下行为不变。 */
    @Test
    public void defaultLocaleCteExclusionBaseline() {
        List<SqlTableReference> refs = sourceExtractor.extract(
                "WITH cte AS (SELECT a FROM REAL_SRC) SELECT b FROM cte");
        assertEquals(1, refs.size());
        assertEquals("REAL_SRC", refs.get(0).getSimpleName());
        assertTrue(columnExtractor.extract("SELECT t.a AS x FROM SRC t").size() == 1);
    }
}
