package io.nop.metadata.service.lineage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * check2 P2-04（2026-08-23 审计）回归：sql_parse 血缘重复抽取不清理陈旧边。
 *
 * <p>缺陷机制：{@code extractColumnLineageFromSql}/{@code extractLineageFromSql} 只按存量做
 * 增量插入/更新（存在性跳过），不删除本次解析已不存在的旧边——同一 metaTableId 修改 sourceSql
 * 后重抽取，旧 SQL 产生的 sql_parse 边（列不再被引用/源表已移出）永久残留，血缘图（getUpstream/
 * getImpactAnalysis 全量加载）累积过期边、影响分析失真。对照 measure 路径的先清后建
 * （deleteMeasureParseEdges）。
 *
 * <p>修复：对齐 measure 路径——重抽取前按 targetTableId 删除本通道（lineageSource=sql_parse）
 * 旧边再插入；表级通道（sourceColumn IS NULL）与列级通道（sourceColumn 非空）独立清理，
 * 两通道互不误删。
 *
 * <p>mutate-fail：若回退为“只增不删”，更新 sourceSql 后旧源表的列级/表级边仍在 → 两个断言
 * countSqlParseEdges/countTableLevelSqlParseEdges(旧源) == 0 确定性失败。
 */
public class TestSqlParseLineageStaleEdgeCleanup extends LineageTestBase {

    /** 列级：sourceSql 从引用 src1 改为只引用 src2 后，src1 的列级边必须被删除、src2 新边就位。 */
    @Test
    public void testColumnLevelStaleEdgesRemovedAfterSourceSqlChange() {
        String moduleId = ensureModule("stale_col_cleanup");
        String src1 = saveTable(moduleId, "stc_src1");
        String src2 = saveTable(moduleId, "stc_src2");
        String targetId = saveSqlTable(moduleId, "stc_view_col",
                "SELECT a.f1 AS out1, a.f2 AS out2 FROM stc_src1 a");

        // 第一次抽取：src1 → target 两条列级边
        lineageBiz.extractColumnLineageFromSql(targetId, svcCtx);
        assertEquals(2, countColumnSqlParseEdges(src1, targetId),
                "first extract must create 2 column edges from src1");

        // sourceSql 变更：不再引用 src1，只引用 src2 的 f3
        reSaveSourceSql(targetId, "SELECT b.f3 AS out3 FROM stc_src2 b");
        lineageBiz.extractColumnLineageFromSql(targetId, svcCtx);

        assertEquals(0, countColumnSqlParseEdges(src1, targetId),
                "stale column edges from src1 (no longer referenced) must be deleted on re-extract");
        assertEquals(1, countColumnSqlParseEdges(src2, targetId),
                "new column edge from src2 must be present after re-extract");
        assertNotNull(findColumnEdge(src2, targetId, "f3", "out3"),
                "new edge must map src2.f3 -> target.out3");
        assertNull(findColumnEdge(src1, targetId, "f1", "out1"),
                "old edge src1.f1 -> target.out1 must not survive re-extract");
    }

    /** 表级：sourceSql 源表从 src1 换为 src2 后，src1 的表级边必须被删除。 */
    @Test
    public void testTableLevelStaleEdgesRemovedAfterSourceSqlChange() {
        String moduleId = ensureModule("stale_tbl_cleanup");
        String src1 = saveTable(moduleId, "stt_src1");
        String src2 = saveTable(moduleId, "stt_src2");
        String targetId = saveSqlTable(moduleId, "stt_view", "SELECT a.f1 AS o1 FROM stt_src1 a");

        lineageBiz.extractLineageFromSql(targetId, svcCtx);
        assertEquals(1, countTableLevelSqlParseEdges(src1, targetId),
                "first extract must create 1 table-level edge from src1");

        reSaveSourceSql(targetId, "SELECT b.f2 AS o2 FROM stt_src2 b");
        lineageBiz.extractLineageFromSql(targetId, svcCtx);

        assertEquals(0, countTableLevelSqlParseEdges(src1, targetId),
                "stale table-level edge from src1 must be deleted on re-extract");
        assertEquals(1, countTableLevelSqlParseEdges(src2, targetId),
                "new table-level edge from src2 must be present");
    }

    /** 两通道独立：列级重抽取不得误删表级边，反之亦然。 */
    @Test
    public void testChannelIsolationBetweenTableAndColumnLevel() {
        String moduleId = ensureModule("stale_channel_iso");
        String src1 = saveTable(moduleId, "sti_src1");
        String targetId = saveSqlTable(moduleId, "sti_view", "SELECT a.f1 AS o1 FROM sti_src1 a");

        // 两通道都先抽取
        lineageBiz.extractLineageFromSql(targetId, svcCtx);
        lineageBiz.extractColumnLineageFromSql(targetId, svcCtx);
        assertEquals(1, countTableLevelSqlParseEdges(src1, targetId));
        assertEquals(1, countColumnSqlParseEdges(src1, targetId));

        // 只重抽列级：表级边必须保留
        lineageBiz.extractColumnLineageFromSql(targetId, svcCtx);
        assertEquals(1, countTableLevelSqlParseEdges(src1, targetId),
                "column-level re-extract must not delete table-level edges");
        assertEquals(1, countColumnSqlParseEdges(src1, targetId));

        // 只重抽表级：列级边必须保留
        lineageBiz.extractLineageFromSql(targetId, svcCtx);
        assertEquals(1, countColumnSqlParseEdges(src1, targetId),
                "table-level re-extract must not delete column-level edges");
        assertEquals(1, countTableLevelSqlParseEdges(src1, targetId));
    }

    /** 更新 sql 视图表的 sourceSql（直更 + evict，沿 updateMeasureExpression 先例）。 */
    private String reSaveSourceSql(String tableId, String newSourceSql) {
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("update NOP_META_TABLE set SOURCE_SQL=? where META_TABLE_ID=?", newSourceSql, tableId)
                .end();
        ormTemplate.executeUpdate(upd);
        ormTemplate.evictAll(io.nop.metadata.dao.entity.NopMetaTable.class.getName());
        return tableId;
    }
}
