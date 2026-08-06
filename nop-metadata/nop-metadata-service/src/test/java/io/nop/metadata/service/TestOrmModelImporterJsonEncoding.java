package io.nop.metadata.service;

import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.dao.entity.NopMetaEntityIndex;
import io.nop.metadata.dao.entity.NopMetaEntityRelation;
import io.nop.metadata.dao.model.OrmModelImporter;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.model.OrmIndexColumnModel;
import io.nop.orm.model.OrmIndexModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AR-18a（plan 2026-08-06-0914-3 Phase 1）：导入内容 JSON 构造结构化序列化——join 条件与索引列
 * 名含特殊字符（`"` / `\`）时产物必须是合法 JSON（可重新解析且值完整保留）。
 *
 * <p>判别性：修复前 buildJoinConditionsJson / buildIndexColumnsJson 手拼字符串（值未转义），
 * 含 `"` 或 `\` 的值产出非法 JSON（JsonTool.parse 抛异常 / 值错位）——red 实测。
 */
public class TestOrmModelImporterJsonEncoding {

    private final OrmModelImporter importer = new OrmModelImporter();

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseList(String json) {
        Object parsed = JsonTool.parse(json);
        assertNotNull(parsed, "join/index JSON must be parseable: " + json);
        return (List<Map<String, Object>>) parsed;
    }

    @Test
    public void testJoinConditionsWithSpecialCharsProduceValidJson() {
        // left prop 含双引号、right 走 value 分支含反斜杠——修复前手拼 JSON 非法
        IEntityJoinConditionModel join1 = mock(IEntityJoinConditionModel.class);
        when(join1.getLeftProp()).thenReturn("left\"col");
        when(join1.getRightProp()).thenReturn(null);
        when(join1.getRightValue()).thenReturn("val\\ue");
        IEntityJoinConditionModel join2 = mock(IEntityJoinConditionModel.class);
        when(join2.getLeftProp()).thenReturn(null);
        when(join2.getLeftValue()).thenReturn("v\"1");
        when(join2.getRightProp()).thenReturn("plain");
        IEntityRelationModel rel = mock(IEntityRelationModel.class);
        org.mockito.Mockito.doReturn(Arrays.asList(join1, join2)).when(rel).getJoin();

        NopMetaEntityRelation relation = importer.buildRelation(rel, false);
        List<Map<String, Object>> parsed = parseList(relation.getJoinConditions());

        assertEquals(2, parsed.size(), "join condition count must be preserved: " + relation.getJoinConditions());
        assertEquals("left\"col", parsed.get(0).get("leftProp"), "left prop must round-trip: "
                + relation.getJoinConditions());
        assertEquals("val\\ue", parsed.get(0).get("rightProp"), "right value must round-trip: "
                + relation.getJoinConditions());
        assertEquals("v\"1", parsed.get(1).get("leftProp"), "left value must round-trip: "
                + relation.getJoinConditions());
        assertEquals("plain", parsed.get(1).get("rightProp"), "right prop must round-trip: "
                + relation.getJoinConditions());
    }

    @Test
    public void testIndexColumnsWithSpecialCharsProduceValidJson() {
        OrmIndexColumnModel col1 = new OrmIndexColumnModel();
        col1.setName("col\"x");
        col1.setDesc(Boolean.TRUE);
        OrmIndexColumnModel col2 = new OrmIndexColumnModel();
        col2.setName("col\\y");
        col2.setDesc(Boolean.FALSE);
        OrmIndexModel idx = new OrmIndexModel();
        idx.setName("idx_1");
        idx.setColumns(Arrays.asList(col1, col2));

        NopMetaEntityIndex index = importer.buildIndex(idx, false);
        List<Map<String, Object>> parsed = parseList(index.getIndexColumns());

        assertEquals(2, parsed.size(), "index column count must be preserved: " + index.getIndexColumns());
        assertEquals("col\"x", parsed.get(0).get("fieldName"), "fieldName must round-trip: "
                + index.getIndexColumns());
        assertEquals(Boolean.TRUE, parsed.get(0).get("desc"), "desc must round-trip: " + index.getIndexColumns());
        assertEquals("col\\y", parsed.get(1).get("fieldName"), "fieldName with backslash must round-trip: "
                + index.getIndexColumns());
        assertEquals(Boolean.FALSE, parsed.get(1).get("desc"), "desc=false must round-trip: " + index.getIndexColumns());
    }

    @Test
    public void testJoinConditionsNoSpecialCharsFormatCompatible() {
        // 持久化格式兼容：普通值下产物与手拼格式同构（JSON 数组、同 key）
        IEntityJoinConditionModel join = mock(IEntityJoinConditionModel.class);
        when(join.getLeftProp()).thenReturn("a");
        when(join.getRightProp()).thenReturn("b");
        IEntityRelationModel rel = mock(IEntityRelationModel.class);
        org.mockito.Mockito.doReturn(Arrays.asList(join)).when(rel).getJoin();

        NopMetaEntityRelation relation = importer.buildRelation(rel, false);
        List<Map<String, Object>> parsed = parseList(relation.getJoinConditions());
        assertEquals(1, parsed.size());
        assertEquals("a", parsed.get(0).get("leftProp"));
        assertEquals("b", parsed.get(0).get("rightProp"));
    }

    @Test
    public void testIndexColumnsNoSpecialCharsFormatCompatible() {
        OrmIndexColumnModel col = new OrmIndexColumnModel();
        col.setName("f1");
        col.setDesc(null);
        OrmIndexModel idx = new OrmIndexModel();
        idx.setName("idx_2");
        idx.setColumns(Arrays.asList(col));

        NopMetaEntityIndex index = importer.buildIndex(idx, false);
        List<Map<String, Object>> parsed = parseList(index.getIndexColumns());
        assertEquals(1, parsed.size());
        assertEquals("f1", parsed.get(0).get("fieldName"));
        assertEquals(Boolean.FALSE, parsed.get(0).get("desc"), "null desc must serialize as false (descs=null fallback)");
    }

    @Test
    public void testIndexColumnsNullColumnsListProducesValidEmptyJson() {
        OrmIndexModel idx = new OrmIndexModel();
        idx.setName("idx_3");
        idx.setColumns(null);
        NopMetaEntityIndex index = importer.buildIndex(idx, false);
        List<Map<String, Object>> parsed = parseList(index.getIndexColumns());
        assertEquals(0, parsed.size(), "null columns must produce an empty JSON array: " + index.getIndexColumns());
    }

}
