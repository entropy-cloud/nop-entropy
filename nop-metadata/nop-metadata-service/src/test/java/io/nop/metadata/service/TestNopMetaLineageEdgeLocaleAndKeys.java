package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.service.lineage.LineageTestBase;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cycle 2 / I4' 端到端验证（plan 2026-08-15-0820-3 Phase 1 Exit Criteria）：
 *
 * <p><b>INV-LOCALE（tr-TR）端到端</b>：从用户可见入口（GraphQL mutation → BizModel →
 * NopMetaLineageEdgeQueryAction → SQL 抽取器 → 目录 registry 匹配）到落库输出，在测试内
 * tr-TR 默认 locale 下走通修复语义——混合大小写表名（含大写 I）与小写 SQL 引用之间的 registry 键
 * 不因 locale 漂移而失配（旧实现下 unresolved、零边）。
 *
 * <p><b>INV-DELIM-KEY（结构性键）端到端</b>（adjudication-table-cycle2 §5 D5/D6）：
 * 列级边去重键 / existing-edge map 键由 {@code sid + "|" + col + "|" + col} 拼接改为结构性键
 * （值级 equals/hashCode）——不依赖"列名不含分隔符 / 解析器归一化保引号"一类格式假设
 * （AR-03 机理：格式假设腐烂正是历史碰撞根因）。端到端钉死含 {@code |} 的引号列名两条边
 * 各自落库 + 重复抽取幂等。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaLineageEdgeLocaleAndKeys extends LineageTestBase {

    /**
     * tr-TR 端到端（P1-B #8/#9/#12 链路）：目录表 {@code BIG_ITEM}（含大写 I）+
     * sourceSql 小写引用 {@code big_item} → extractLineageFromSql 必须解析成功并落表级边。
     *
     * <p>旧实现：buildTableNameIndex 键 {@code "BıG_ITEM".toLowerCase()}（tr：I→ı）与引用侧
     * {@code "big_item"}（普通 i）失配 → unresolved → 零边（静默 lineage 缺失）。
     */
    @Test
    public void turkishLocaleMixedCaseTableNameLineageE2E() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            String moduleId = ensureModule("mod-tr-locale");
            String srcId = saveTable(moduleId, "BIG_ITEM");
            String sqlViewId = saveSqlTable(moduleId, "V_TR_LOCALE",
                    "SELECT t.* FROM big_item t");

            GraphQLResponseBean resp = execute(
                    "mutation { NopMetaLineageEdge__extractLineageFromSql(metaTableId: \"" + sqlViewId
                            + "\") { edgeCount unresolved errors } }");
            assertFalse(resp.hasError(), "extractLineageFromSql should not error under tr-TR: " + resp);
            String data = String.valueOf(resp.getData());
            assertTrue(data.contains("edgeCount=1"),
                    "big_item (lowercase ref) must resolve to BIG_ITEM under tr-TR default locale: " + data);
            assertTrue(data.contains("unresolved=[]") || data.contains("unresolved=]"),
                    "no unresolved tables allowed under tr-TR: " + data);

            // 落库断言：边指向 BIG_ITEM 表 id
            NopMetaLineageEdge e = findEdge(srcId, sqlViewId, null, null);
            assertNotNull(e, "table-level edge BIG_ITEM -> view must exist under tr-TR");
            assertEquals(_NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE, e.getLineageSource());
        } finally {
            Locale.setDefault(original);
        }
    }

    /**
     * tr-TR 列级端到端（P1-B #9 + #13-#24 抽取器链）：混合大小写源表 + 小写引用的列级抽取在
     * tr-TR 下正确归属并落列级边。
     */
    @Test
    public void turkishLocaleColumnLineageE2E() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            String moduleId = ensureModule("mod-tr-locale-col");
            String srcId = saveTable(moduleId, "ITEM_SOURCE");
            String sqlViewId = saveSqlTable(moduleId, "V_TR_COL",
                    "SELECT t.a AS x FROM item_source t");

            GraphQLResponseBean resp = execute(
                    "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId
                            + "\") { edgeCount unresolved errors } }");
            assertFalse(resp.hasError(), "column extract should not error under tr-TR: " + resp);
            String data = String.valueOf(resp.getData());
            assertTrue(data.contains("edgeCount=1"),
                    "column lineage item_source.a -> x must resolve under tr-TR: " + data);

            NopMetaLineageEdge e = findColumnEdge(srcId, sqlViewId, "a", "x");
            assertNotNull(e, "column edge ITEM_SOURCE.a -> view.x must exist under tr-TR");
        } finally {
            Locale.setDefault(original);
        }
    }

    /**
     * INV-DELIM-KEY D5 端到端：含 {@code |} 的引号列名（SQL 派生标识符，带引号方言下域不受控）
     * 经结构性键去重后两条边都必须落库。
     *
     * <p>探查事实（2026-08-15 实测）：本仓库 SQL 解析器对引号标识符做**保引号**归一化
     * （{@code "a|b"} 存储为含引号形态），旧拼接键在该形态下恰好可区分——但该"不碰撞"依赖
     * 解析器归一化保留引号的格式假设（AR-03 同机理的格式假设腐烂面）；结构性键（值级
     * equals/hashCode）不依赖任何格式假设，对含 {@code |} 的列名两两条分正确。本用例钉死：
     * 引号 pipe 列名的两条边各自落库（exact 名断言，含引号形态）。
     */
    @Test
    public void delimKeyCollisionQuotedColumnNamesTwoEdgesE2E() {
        String moduleId = ensureModule("mod-delimey");
        String srcId = saveTable(moduleId, "DELIM_SRC");
        String sqlViewId = saveSqlTable(moduleId, "V_DELIM",
                "SELECT t.\"a|b\" AS c, t.\"a\" AS \"b|c\" FROM DELIM_SRC t");

        GraphQLResponseBean resp = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId
                        + "\") { edgeCount unresolved errors } }");
        assertFalse(resp.hasError(), "delim-key extract should not error: " + resp);
        String data = String.valueOf(resp.getData());
        assertTrue(data.contains("edgeCount=2"),
                "two distinct quoted-column edges must both be extracted (structural key keeps them distinct): " + data);

        // 两条边各自落库（解析器保引号归一化：两列均为含引号形态 "\"a|b\"" / "\"a\""、别名 c / "\"b|c\""）
        assertNotNull(findColumnEdge(srcId, sqlViewId, "\"a|b\"", "c"),
                "edge (source col '\"a|b\"', target 'c') must exist");
        assertNotNull(findColumnEdge(srcId, sqlViewId, "\"a\"", "\"b|c\""),
                "edge (source col '\"a\"', target '\"b|c\"') must exist");
    }

    /**
     * INV-DELIM-KEY D6 端到端：existing-edge map 键结构性化后，重复抽取对含 {@code |} 的两条边
     * 均正确识别为已存在（幂等不追加、不误路由 update）——拼接键的正确性依赖解析器保引号格式假设，
     * 结构性键对任意列名形态成立。
     */
    @Test
    public void delimKeyExistingEdgeMapIdempotentReExtractE2E() {
        String moduleId = ensureModule("mod-delimey-idem");
        String srcId = saveTable(moduleId, "DELIM_SRC2");
        String sqlViewId = saveSqlTable(moduleId, "V_DELIM2",
                "SELECT t.\"a|b\" AS c, t.\"a\" AS \"b|c\" FROM DELIM_SRC2 t");

        execute("mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId
                + "\") { edgeCount unresolved errors } }");
        assertEquals(2L, countColumnSqlParseEdges(srcId, sqlViewId), "2 edges after first extract");

        // 第二次抽取：两条含 | 的边都必须被 existing-edge map 正确识别（幂等，不追加、不丢）
        GraphQLResponseBean r2 = execute(
                "mutation { NopMetaLineageEdge__extractColumnLineageFromSql(metaTableId: \"" + sqlViewId
                        + "\") { edgeCount unresolved errors } }");
        assertFalse(r2.hasError(), "second extract should not error: " + r2);
        assertEquals(2L, countColumnSqlParseEdges(srcId, sqlViewId),
                "idempotent: both pipe-containing edges must stay exactly 2 after re-extract");
        assertNotNull(findColumnEdge(srcId, sqlViewId, "\"a|b\"", "c"),
                "edge (source col '\"a|b\"', target 'c') must survive re-extract");
        assertNotNull(findColumnEdge(srcId, sqlViewId, "\"a\"", "\"b|c\""),
                "edge (source col '\"a\"', target '\"b|c\"') must survive re-extract");
    }
}
