package io.nop.metadata.service.lineage;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.metadata.api.dto.LineageExtractResultDTO;
import io.nop.metadata.core._NopMetadataCoreConstants;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-3 回归测试（plan 2026-08-15-1913-2 Phase 2）：lineage sourceTables 公开契约语义。
 *
 * <p>契约（owner doc `docs-for-ai/03-modules/nop-metadata.md`）：
 * <ul>
 *   <li>{@code sourceTables} = **已解析**源表 metaTable ID 集（表级/列级）；指标级 = 宿主表自身
 *       {@code [metaTableId]}（自环边语义，仅当产出 ≥1 条边，否则空列表）。</li>
 *   <li>修复前缺陷：表级误植 {@code r.unresolved}（解析失败名单混入源表集）；列级/指标级从不填充（恒空）——
 *       按官方示例调用 {@code extractColumnLineageFromSql { sourceTables }} 永远得到空列表。</li>
 *   <li>{@code unresolved} 保持原语义（表级=完整表名；列级/指标级=诊断串），与 sourceTables 异质并存、互不串入。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestLineageSourceTablesContract extends LineageTestBase {

    /**
     * 表级：2 个已解析源表 + 1 个 ghost 引用 → sourceTables 精确等于已解析 ID 集；
     * ghost 完整名只出现在 unresolved，不串入 sourceTables（修复前 sourceTables === unresolved 误植）。
     */
    @Test
    public void testTableLevelSourceTablesEqualsResolvedSet() {
        String moduleId = ensureModule("mod-p13-table");
        saveTable(moduleId, "P13_ORDERS");
        saveTable(moduleId, "P13_CUSTOMERS");
        String sqlViewId = saveSqlTable(moduleId, "V_P13_TABLE",
                "SELECT o.id, c.name FROM P13_ORDERS o JOIN P13_CUSTOMERS c ON o.cust_id = c.id "
                        + "JOIN P13_GHOST g ON o.id = g.id");

        LineageExtractResultDTO dto = lineageBiz.extractLineageFromSql(sqlViewId, svcCtx);

        String ordersId = findTableId("P13_ORDERS");
        String customersId = findTableId("P13_CUSTOMERS");
        Set<String> expected = Set.of(ordersId, customersId);
        assertEquals(expected, new HashSet<>(dto.getSourceTables()),
                "sourceTables must equal the resolved source table ID set (was unresolved mis-plant before P1-3): "
                        + dto.getSourceTables());
        assertEquals(2, dto.getSourceTables().size(), "exactly 2 resolved sources");
        assertEquals(1, dto.getUnresolved().size(), "ghost ref must go to unresolved only");
        assertTrue(dto.getUnresolved().get(0).contains("P13_GHOST"),
                "unresolved keeps full-name semantics: " + dto.getUnresolved());
        // 互不串入：sourceTables 无完整名项，unresolved 无 ID 项
        for (String s : dto.getSourceTables()) {
            assertFalse(s.contains("P13_GHOST"), "unresolved name must not leak into sourceTables: " + s);
        }
        for (String u : dto.getUnresolved()) {
            assertFalse(u.equals(ordersId) || u.equals(customersId),
                    "resolved ID must not leak into unresolved: " + u);
        }
    }

    /**
     * 列级（CTE + 未解析引用混合，区分两列表）：CTE 穿透源表 A + 直接引用源表 B + ghost 表引用
     * → sourceTables = {A, B}；ghost 诊断串只在 unresolved。修复前该路径 sourceTables 恒空。
     */
    @Test
    public void testColumnLevelSourceTablesEqualsResolvedSetWithMixedSql() {
        String moduleId = ensureModule("mod-p13-col");
        saveTable(moduleId, "P13_COL_SRC_A");
        saveTable(moduleId, "P13_COL_SRC_B");
        String sqlViewId = saveSqlTable(moduleId, "V_P13_COL",
                "WITH cte AS (SELECT t.x FROM P13_COL_SRC_A t) "
                        + "SELECT c.x AS x, b.y AS y, g.k AS gk FROM cte c "
                        + "JOIN P13_COL_SRC_B b ON c.x = b.k "
                        + "JOIN P13_COL_GHOST g ON b.y = g.k");

        LineageExtractResultDTO dto = lineageBiz.extractColumnLineageFromSql(sqlViewId, svcCtx);

        String srcA = findTableId("P13_COL_SRC_A");
        String srcB = findTableId("P13_COL_SRC_B");
        Set<String> expected = Set.of(srcA, srcB);
        assertEquals(expected, new HashSet<>(dto.getSourceTables()),
                "column-level sourceTables must be the resolved source ID set (was always empty before P1-3): "
                        + dto.getSourceTables());
        assertFalse(dto.getUnresolved().isEmpty(),
                "ghost ref must be collected in unresolved: " + dto.getUnresolved());
        assertTrue(dto.getUnresolved().stream().anyMatch(u -> u.contains("P13_COL_GHOST")),
                "unresolved diagnostic must name the ghost table: " + dto.getUnresolved());
        for (String s : dto.getSourceTables()) {
            assertFalse(s.contains("GHOST"), "unresolved must not leak into sourceTables: " + s);
        }
        assertTrue(dto.getEdgeCount() >= 2, "at least x<-A.x and y<-B.y edges: " + dto.getEdgeCount());
    }

    /**
     * 指标级（裁定语义）：measure 边全部为自环（sourceTableId=targetId）→ sourceTables = [宿主表自身]。
     */
    @Test
    public void testMeasureLevelSourceTablesIsHostTable() {
        String moduleId = ensureModule("mod-p13-measure");
        String entityId = saveEntity(moduleId, "P13MeasureEnt", "A", "B");
        String tableId = saveEntityTable(moduleId, "T_P13_MEASURE", entityId);
        saveMeasure(tableId, "M_P13", "A + B", _NopMetadataCoreConstants.AGG_FUNC_SUM);

        LineageExtractResultDTO dto = lineageBiz.extractMeasureLineage(tableId, svcCtx);

        assertEquals(2, dto.getEdgeCount(), "A->M, B->M self-loop edges");
        assertEquals(java.util.List.of(tableId), dto.getSourceTables(),
                "measure-level sourceTables = host table itself (self-loop edge semantics, "
                        + "adjudicated in P1-3): " + dto.getSourceTables());
    }

    /** 指标级边界：0 条边（表达式引用不存在字段）→ sourceTables 为空列表（不伪造宿主为源）。 */
    @Test
    public void testMeasureLevelZeroEdgesYieldsEmptySourceTables() {
        String moduleId = ensureModule("mod-p13-measure-empty");
        String entityId = saveEntity(moduleId, "P13MeasureEmptyEnt", "A");
        String tableId = saveEntityTable(moduleId, "T_P13_MEASURE_EMPTY", entityId);
        saveMeasure(tableId, "M_P13_BAD", "NO_SUCH_COL", null);

        LineageExtractResultDTO dto = lineageBiz.extractMeasureLineage(tableId, svcCtx);

        assertEquals(0, dto.getEdgeCount(), "unknown field produces no edge");
        assertTrue(dto.getSourceTables().isEmpty(),
                "0 edges -> no source table may be fabricated: " + dto.getSourceTables());
        assertFalse(dto.getUnresolved().isEmpty(),
                "unknown field must be explicitly unresolved (not silent): " + dto.getUnresolved());
    }
}
