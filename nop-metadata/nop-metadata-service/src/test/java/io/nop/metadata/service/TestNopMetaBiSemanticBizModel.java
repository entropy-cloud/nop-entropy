package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 BI 语义层字段引用校验 + 条件结构校验 + 跨表类型字段解析（plan 0700-2，架构基线 §2.5.2）：
 *
 * <ul>
 *   <li><b>resolveTableFields 跨类型</b>：entity（NopMetaEntityField 集合）/ external（buildSql JSON columnName）/
 *       sql（SELECT 解析字段名）三类分派均返回正确字段；失败路径（entity baseEntityId null / external 损坏
 *       buildSql / sql 非 SELECT）显式失败不静默空集。</li>
 *   <li><b>Measure save 校验</b>：合法 entityFieldId 通过；非法引用（指向不存在字段）显式失败；
 *       expression 型指标（entityFieldId null）跳过字段校验通过。</li>
 *   <li><b>Dimension save 校验</b>：合法通过、非法显式失败；时间维度 granularity 自由 string 不受 dict 约束。</li>
 *   <li><b>Join save 校验</b>：合法实体+字段通过；实体不存在/字段不属于实体显式失败。</li>
 *   <li><b>Filter save 校验</b>：合法 TreeBean 结构通过；非法 JSON 显式失败；isDefault 唯一性强制。</li>
 * </ul>
 *
 * <p>Anti-Hollow：Measure/Dimension/Join 的非法引用被显式拒绝证明 save 校验运行时确实调用了
 * {@code MetaTableFieldResolver}（接线验证）；resolveTableFields 三类返回真实字段集（端到端验证）。
 *
 * <p>跨表校验（plan 0228-3，架构基线 §2.5.2 D3）：entity 表 Measure/Dimension 引用 join 右实体字段（直连可达）
 * 合法通过；悬空跨表引用（metaEntityId 不在 baseEntity ∪ join.rightEntity 集合）显式失败。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaBiSemanticBizModel extends JunitBaseTestCase {

    public TestNopMetaBiSemanticBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    // ============================================================
    // resolveTableFields 跨表类型分派（item 1.2b）
    // ============================================================

    /** entity 表：resolveTableFields 返回 NopMetaEntityField 集合的字段名。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testResolveTableFieldsEntity() {
        String moduleId = ensureModule("mod-resolve-ent");
        String entityId = saveEntity(moduleId, "ResolveEntity", "fld_a", "fld_b");
        String tableId = saveEntityTable(moduleId, "T_RESOLVE_ENT", entityId);

        Map<String, Object> result = resolveTableFields(tableId);
        assertEquals("entity", result.get("tableType"), "tableType must be entity");
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        assertNotNull(fields, "fields must not be null");
        assertEquals(2, fields.size(), "entity table must resolve 2 fields");
        assertEquals("fld_a", fields.get(0).get("name"));
        assertEquals("fld_b", fields.get(1).get("name"));
        assertEquals("entity", fields.get(0).get("sourceType"));
    }

    /** external 表：resolveTableFields 返回 buildSql JSON 的 columnName 集合。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testResolveTableFieldsExternal() {
        String tableId = saveExternalTable("T_RESOLVE_EXT", "qs_resolve_ext",
                "[{\"columnName\":\"amount\",\"dataType\":\"DOUBLE\",\"nullable\":true},"
                        + "{\"columnName\":\"name\",\"dataType\":\"VARCHAR\",\"nullable\":false}]");

        Map<String, Object> result = resolveTableFields(tableId);
        assertEquals("external", result.get("tableType"));
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        assertEquals(2, fields.size());
        assertEquals("amount", fields.get(0).get("name"));
        assertEquals("name", fields.get(1).get("name"));
        assertEquals("external", fields.get(0).get("sourceType"));
    }

    /** sql 表：resolveTableFields 返回 SELECT 解析字段名（复用 P3-1 解析器）。 */
    @Test
    @SuppressWarnings("unchecked")
    public void testResolveTableFieldsSql() {
        String moduleId = ensureModule("mod-resolve-sql");
        String tableId = saveSqlTable(moduleId, "T_RESOLVE_SQL",
                "SELECT order_id, customer_id AS cid FROM orders");

        Map<String, Object> result = resolveTableFields(tableId);
        assertEquals("sql", result.get("tableType"));
        List<Map<String, Object>> fields = (List<Map<String, Object>>) result.get("fields");
        assertEquals(2, fields.size());
        assertEquals("order_id", fields.get(0).get("name"));
        assertEquals("cid", fields.get(1).get("name"));
        assertEquals("sql", fields.get(0).get("sourceType"));
    }

    /** entity 表 baseEntityId 为 null → 显式失败（不静默空集、不静默跳过）。 */
    @Test
    public void testResolveTableFieldsEntityBaseEntityIdNullFails() {
        String moduleId = ensureModule("mod-resolve-null");
        String tableId = saveEntityTable(moduleId, "T_RESOLVE_NULL", null);

        GraphQLResponseBean resp = runGraphQL(
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + tableId + "\") { tableType fields { name sourceType type } } }");
        assertTrue(resp.hasError(),
                "entity table with null baseEntityId must explicitly fail (not silent empty): " + resp);
    }

    /** external 表 buildSql JSON 损坏（非数组）→ 显式失败。 */
    @Test
    public void testResolveTableFieldsExternalBadBuildSqlFails() {
        String tableId = saveExternalTable("T_RESOLVE_BAD", "qs_resolve_bad", "{not-an-array}");
        GraphQLResponseBean resp = runGraphQL(
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + tableId + "\") { tableType fields { name sourceType type } } }");
        assertTrue(resp.hasError(), "corrupted buildSql JSON must explicitly fail: " + resp);
    }

    // ============================================================
    // Measure save 校验（item 1.3）
    // ============================================================

    /** Measure 合法：entityFieldId 指向实体字段 PK → 通过。 */
    @Test
    public void testMeasureSaveValidEntityField() {
        String moduleId = ensureModule("mod-measure-ok");
        String entityId = saveEntity(moduleId, "MeasureEnt", "amount");
        String fieldId = findEntityFieldId(entityId, "amount");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_OK", entityId);

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + tableId + "\", measureName: \"m1\", "
                        + "aggFunc: \"sum\", entityFieldId: \"" + fieldId + "\"}) { measureId } }");
        assertFalse(resp.hasError(), "valid measure save must succeed: " + resp);
    }

    /** Measure 非法：entityFieldId 指向不属于该表实体的字段 → 显式失败（不静默存入悬空引用）。 */
    @Test
    public void testMeasureSaveInvalidEntityFieldFails() {
        String moduleId = ensureModule("mod-measure-bad");
        String entityIdA = saveEntity(moduleId, "MeasureEntA", "amount");
        String entityIdB = saveEntity(moduleId, "MeasureEntB", "other");
        String fieldIdB = findEntityFieldId(entityIdB, "other"); // 属于实体 B
        String tableId = saveEntityTable(moduleId, "T_MEASURE_BAD", entityIdA); // 表关联实体 A

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + tableId + "\", measureName: \"m_bad\", "
                        + "aggFunc: \"sum\", entityFieldId: \"" + fieldIdB + "\"}) { measureId } }");
        assertTrue(resp.hasError(),
                "measure with field ref not belonging to table's entity must be rejected: " + resp);
    }

    /** Measure expression 型：entityFieldId 为 null → 跳过字段校验通过（expression 内容首版不校验）。 */
    @Test
    public void testMeasureSaveExpressionSkipsFieldCheck() {
        String moduleId = ensureModule("mod-measure-expr");
        String entityId = saveEntity(moduleId, "MeasureExprEnt", "amount");
        String tableId = saveEntityTable(moduleId, "T_MEASURE_EXPR", entityId);

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + tableId + "\", measureName: \"m_expr\", "
                        + "aggFunc: \"count\", expression: \"amount * 2\"}) { measureId } }");
        assertFalse(resp.hasError(), "expression measure (null entityFieldId) must skip field check: " + resp);
    }

    /** Measure external 表：entityFieldId 为字段名（columnName）→ 合法通过。 */
    @Test
    public void testMeasureSaveExternalFieldName() {
        String tableId = saveExternalTable("T_MEASURE_EXT", "qs_measure_ext",
                "[{\"columnName\":\"amount\",\"dataType\":\"DOUBLE\"}]");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + tableId + "\", measureName: \"m_ext\", "
                        + "aggFunc: \"sum\", entityFieldId: \"amount\"}) { measureId } }");
        assertFalse(resp.hasError(), "valid external field-name measure must succeed: " + resp);
    }

    /** Measure external 表：entityFieldId 指向不存在的列名 → 显式失败。 */
    @Test
    public void testMeasureSaveExternalUnknownFieldFails() {
        String tableId = saveExternalTable("T_MEASURE_EXT2", "qs_measure_ext2",
                "[{\"columnName\":\"amount\",\"dataType\":\"DOUBLE\"}]");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + tableId + "\", measureName: \"m_ext_bad\", "
                        + "aggFunc: \"sum\", entityFieldId: \"nonexistent_col\"}) { measureId } }");
        assertTrue(resp.hasError(), "unknown external column must be rejected: " + resp);
    }

    // ============================================================
    // Dimension save 校验（item 1.3）
    // ============================================================

    /** Dimension 合法 + 时间维度 granularity 自由 string（不受 dict 约束）。 */
    @Test
    public void testDimensionSaveValidTemporal() {
        String moduleId = ensureModule("mod-dim-ok");
        String entityId = saveEntity(moduleId, "DimEnt", "order_date");
        String fieldId = findEntityFieldId(entityId, "order_date");
        String tableId = saveEntityTable(moduleId, "T_DIM_OK", entityId);

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableDimension__save(data: {"
                        + "metaTableId: \"" + tableId + "\", dimensionName: \"d_time\", "
                        + "dimensionType: \"temporal\", granularity: \"month\", "
                        + "entityFieldId: \"" + fieldId + "\"}) { dimensionId } }");
        assertFalse(resp.hasError(), "valid temporal dimension must succeed: " + resp);
    }

    /** Dimension 非法：entityFieldId 指向不存在字段 → 显式失败。 */
    @Test
    public void testDimensionSaveInvalidFieldFails() {
        String moduleId = ensureModule("mod-dim-bad");
        String entityIdA = saveEntity(moduleId, "DimEntA", "region");
        String entityIdB = saveEntity(moduleId, "DimEntB", "other");
        String fieldIdB = findEntityFieldId(entityIdB, "other");
        String tableId = saveEntityTable(moduleId, "T_DIM_BAD", entityIdA);

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableDimension__save(data: {"
                        + "metaTableId: \"" + tableId + "\", dimensionName: \"d_bad\", "
                        + "dimensionType: \"categorical\", entityFieldId: \"" + fieldIdB + "\"}) { dimensionId } }");
        assertTrue(resp.hasError(), "dimension with invalid field ref must be rejected: " + resp);
    }

    // ============================================================
    // 跨表 Measure/Dimension 字段引用校验（plan 0228-3，§2.5.2 D3 entity-entity）
    // ============================================================

    /**
     * 跨表 Measure 合法：entity 表的 Measure 引用 join 右实体的字段（直连可达 rightEntityId）
     * → save 通过（不再仅限主表 baseEntity 字段）。
     *
     * <p>接线验证（Anti-Hollow）：本测试通过需要 save override 运行时确实调用
     * {@code resolveAllowedEntityIds} 加载 join 右实体集合并接受引用，证明跨表校验非空壳。
     */
    @Test
    public void testMeasureSaveCrossTableJoinRightEntityValid() {
        String moduleId = ensureModule("mod-xmeasure-ok");
        String leftEntityId = saveEntity(moduleId, "XMeasureLeft", "order_id");
        String rightEntityId = saveEntity(moduleId, "XMeasureRight", "amount");
        String rightFieldId = findEntityFieldId(rightEntityId, "amount"); // 属于右实体
        String tableId = saveEntityTable(moduleId, "T_XMEASURE_OK", leftEntityId);
        saveJoin(tableId, "inner", leftEntityId, rightEntityId, "order_id", "order_id");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + tableId + "\", measureName: \"m_xtab\", "
                        + "aggFunc: \"sum\", entityFieldId: \"" + rightFieldId + "\"}) { measureId } }");
        assertFalse(resp.hasError(),
                "cross-table measure referencing join rightEntity field must succeed: " + resp);
    }

    /**
     * 跨表 Measure 悬空：entity 表 Measure 引用既非主表 baseEntity、也无 join 直连可达 rightEntity 的字段
     * → 显式失败（不静默存入悬空引用）。entity-only 行为已由 testMeasureSaveInvalidEntityFieldFails 覆盖；
     * 此处额外验证「即使表有 join，但 join 不指向该字段所属实体」仍失败。
     */
    @Test
    public void testMeasureSaveCrossTableDanglingFails() {
        String moduleId = ensureModule("mod-xmeasure-dangling");
        String leftEntityId = saveEntity(moduleId, "XDangleLeft", "order_id");
        String rightEntityId = saveEntity(moduleId, "XDangleRight", "amount");
        // 第三个实体，既非主表也未被 join 引用
        String orphanEntityId = saveEntity(moduleId, "XDangleOrphan", "ghost");
        String orphanFieldId = findEntityFieldId(orphanEntityId, "ghost");
        String tableId = saveEntityTable(moduleId, "T_XMEASURE_DANGLE", leftEntityId);
        // join 只指向 rightEntityId，不指向 orphanEntityId
        saveJoin(tableId, "inner", leftEntityId, rightEntityId, "order_id", "order_id");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + tableId + "\", measureName: \"m_dangle\", "
                        + "aggFunc: \"sum\", entityFieldId: \"" + orphanFieldId + "\"}) { measureId } }");
        assertTrue(resp.hasError(),
                "dangling cross-table measure (field entity not in baseEntity ∪ join.rightEntity) must be rejected: "
                        + resp);
    }

    /**
     * 跨表 Measure 无 join 但引用非主表字段 → 仍显式失败（既有 entity-only 行为不退化，回归）。
     * 等价于 allowedEntityIds 退化为 {baseEntityId} 单元素集合。
     */
    @Test
    public void testMeasureSaveNoJoinNonBaseFieldFails() {
        String moduleId = ensureModule("mod-xmeasure-nojoin");
        String baseEntityId = saveEntity(moduleId, "XNoJoinBase", "amt");
        String otherEntityId = saveEntity(moduleId, "XNoJoinOther", "x");
        String otherFieldId = findEntityFieldId(otherEntityId, "x");
        String tableId = saveEntityTable(moduleId, "T_XMEASURE_NOJOIN", baseEntityId);
        // 无 join

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + tableId + "\", measureName: \"m_nojoin\", "
                        + "aggFunc: \"sum\", entityFieldId: \"" + otherFieldId + "\"}) { measureId } }");
        assertTrue(resp.hasError(),
                "entity-only table (no join) with non-base field ref must still be rejected: " + resp);
    }

    /**
     * 跨表 Dimension 合法：entity 表 Dimension 引用 join 右实体字段 → save 通过。
     * Dimension 与 Measure 共享 validateFieldReference，跨表扩展一并生效。
     */
    @Test
    public void testDimensionSaveCrossTableJoinRightEntityValid() {
        String moduleId = ensureModule("mod-xdim-ok");
        String leftEntityId = saveEntity(moduleId, "XDimLeft", "order_id");
        String rightEntityId = saveEntity(moduleId, "XDimRight", "region");
        String rightFieldId = findEntityFieldId(rightEntityId, "region");
        String tableId = saveEntityTable(moduleId, "T_XDIM_OK", leftEntityId);
        saveJoin(tableId, "left", leftEntityId, rightEntityId, "order_id", "order_id");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableDimension__save(data: {"
                        + "metaTableId: \"" + tableId + "\", dimensionName: \"d_xtab\", "
                        + "dimensionType: \"categorical\", entityFieldId: \"" + rightFieldId + "\"}) { dimensionId } }");
        assertFalse(resp.hasError(),
                "cross-table dimension referencing join rightEntity field must succeed: " + resp);
    }

    /**
     * 跨表 Dimension 悬空：引用既非主表、也无 join 可达 rightEntity 的字段 → 显式失败。
     */
    @Test
    public void testDimensionSaveCrossTableDanglingFails() {
        String moduleId = ensureModule("mod-xdim-dangle");
        String leftEntityId = saveEntity(moduleId, "XDimDangleLeft", "order_id");
        String rightEntityId = saveEntity(moduleId, "XDimDangleRight", "region");
        String orphanEntityId = saveEntity(moduleId, "XDimDangleOrphan", "ghost");
        String orphanFieldId = findEntityFieldId(orphanEntityId, "ghost");
        String tableId = saveEntityTable(moduleId, "T_XDIM_DANGLE", leftEntityId);
        saveJoin(tableId, "inner", leftEntityId, rightEntityId, "order_id", "order_id");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableDimension__save(data: {"
                        + "metaTableId: \"" + tableId + "\", dimensionName: \"d_dangle\", "
                        + "dimensionType: \"categorical\", entityFieldId: \"" + orphanFieldId + "\"}) { dimensionId } }");
        assertTrue(resp.hasError(),
                "dangling cross-table dimension must be rejected: " + resp);
    }

    /**
     * 跨表校验不影响 external/sql 表 Measure（回归）：external 表 Measure 仍按字段名集合校验，
     * 无 join 可达概念（external 表不可能有 entity join）。合法字段名通过。
     */
    @Test
    public void testMeasureSaveExternalUnaffectedByCrossTable() {
        String tableId = saveExternalTable("T_XMEASURE_EXT", "qs_xmeasure_ext",
                "[{\"columnName\":\"amount\",\"dataType\":\"DOUBLE\"}]");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + tableId + "\", measureName: \"m_ext_xtab\", "
                        + "aggFunc: \"sum\", entityFieldId: \"amount\"}) { measureId } }");
        assertFalse(resp.hasError(),
                "external table measure must still succeed (unaffected by cross-table entity logic): " + resp);
    }

    /**
     * resolver.resolveAllowedEntityIds 直接调用验证（接线证明 helper 非空壳）：
     * entity 表含 baseEntity + join rightEntity → 返回集合含两者。
     */
    @Test
    public void testResolverResolveAllowedEntityIds() {
        io.nop.metadata.service.field.MetaTableFieldResolver resolver =
                new io.nop.metadata.service.field.MetaTableFieldResolver();
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        IEntityDao<NopMetaTableJoin> joinDao = daoProvider.daoFor(NopMetaTableJoin.class);

        String moduleId = ensureModule("mod-resolver-allowed");
        String leftEntityId = saveEntity(moduleId, "AllowedLeft", "k");
        String rightEntityId = saveEntity(moduleId, "AllowedRight", "v");
        String tableId = saveEntityTable(moduleId, "T_ALLOWED", leftEntityId);
        saveJoin(tableId, "inner", leftEntityId, rightEntityId, "k", "k");

        NopMetaTable table = getTable(tableId);
        java.util.Set<String> allowed = resolver.resolveAllowedEntityIds(table, joinDao);
        assertTrue(allowed.contains(leftEntityId),
                "allowedEntityIds must contain baseEntityId: " + allowed);
        assertTrue(allowed.contains(rightEntityId),
                "allowedEntityIds must contain join rightEntityId: " + allowed);
        assertEquals(2, allowed.size(), "exactly base + one join right entity: " + allowed);
    }

    /**
     * resolver.resolveAllowedEntityIds 对无 join 的表 → 仅含 baseEntityId（entity-only 退化）。
     */
    @Test
    public void testResolverResolveAllowedEntityIdsNoJoin() {
        io.nop.metadata.service.field.MetaTableFieldResolver resolver =
                new io.nop.metadata.service.field.MetaTableFieldResolver();
        IEntityDao<NopMetaTableJoin> joinDao = daoProvider.daoFor(NopMetaTableJoin.class);

        String moduleId = ensureModule("mod-resolver-nojoin");
        String baseEntityId = saveEntity(moduleId, "AllowedNoJoin", "k");
        String tableId = saveEntityTable(moduleId, "T_ALLOWED_NOJOIN", baseEntityId);

        NopMetaTable table = getTable(tableId);
        java.util.Set<String> allowed = resolver.resolveAllowedEntityIds(table, joinDao);
        assertEquals(1, allowed.size(), "entity-only table allowed set must be just baseEntity: " + allowed);
        assertTrue(allowed.contains(baseEntityId));
    }

    /**
     * resolver.resolveAllowedEntityIds 对 baseEntityId null → 显式抛异常（不静默空集，对齐降级铁律）。
     */
    @Test
    public void testResolverResolveAllowedEntityIdsBaseNullThrows() {
        io.nop.metadata.service.field.MetaTableFieldResolver resolver =
                new io.nop.metadata.service.field.MetaTableFieldResolver();
        IEntityDao<NopMetaTableJoin> joinDao = daoProvider.daoFor(NopMetaTableJoin.class);
        String moduleId = ensureModule("mod-resolver-allnull");
        String tableId = saveEntityTable(moduleId, "T_ALLOWED_NULL", null);

        NopMetaTable table = getTable(tableId);
        Executable call = () -> resolver.resolveAllowedEntityIds(table, joinDao);
        assertThrows(io.nop.api.core.exceptions.NopException.class, call,
                "entity table with null baseEntityId must throw (not silent empty)");
    }

    // ============================================================
    // Join save 校验（item 1.4）
    // ============================================================

    /** Join 合法：left/right 实体存在 + 字段属于对应实体 → 通过。 */
    @Test
    public void testJoinSaveValid() {
        String moduleId = ensureModule("mod-join-ok");
        String leftEntityId = saveEntity(moduleId, "JoinLeft", "order_id");
        String rightEntityId = saveEntity(moduleId, "JoinRight", "order_id");
        String tableId = saveEntityTable(moduleId, "T_JOIN_OK", leftEntityId);

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + tableId + "\", joinType: \"inner\", "
                        + "leftEntityId: \"" + leftEntityId + "\", rightEntityId: \"" + rightEntityId + "\", "
                        + "leftField: \"order_id\", rightField: \"order_id\"}) { joinId } }");
        assertFalse(resp.hasError(), "valid join must succeed: " + resp);
    }

    /** Join 非法：rightEntityId 不存在 → 显式失败。 */
    @Test
    public void testJoinSaveNonExistentEntityFails() {
        String moduleId = ensureModule("mod-join-nent");
        String leftEntityId = saveEntity(moduleId, "JoinLeftN", "order_id");
        String tableId = saveEntityTable(moduleId, "T_JOIN_NENT", leftEntityId);

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + tableId + "\", joinType: \"left\", "
                        + "leftEntityId: \"" + leftEntityId + "\", rightEntityId: \"__nope_entity__\", "
                        + "leftField: \"order_id\", rightField: \"order_id\"}) { joinId } }");
        assertTrue(resp.hasError(), "join with non-existent entity must be rejected: " + resp);
    }

    /** Join 非法：rightField 不属于 rightEntity → 显式失败。 */
    @Test
    public void testJoinSaveFieldNotInEntityFails() {
        String moduleId = ensureModule("mod-join-nfld");
        String leftEntityId = saveEntity(moduleId, "JoinLeftF", "order_id");
        String rightEntityId = saveEntity(moduleId, "JoinRightF", "order_id");
        String tableId = saveEntityTable(moduleId, "T_JOIN_NFLD", leftEntityId);

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + tableId + "\", joinType: \"inner\", "
                        + "leftEntityId: \"" + leftEntityId + "\", rightEntityId: \"" + rightEntityId + "\", "
                        + "leftField: \"order_id\", rightField: \"nonexistent_field\"}) { joinId } }");
        assertTrue(resp.hasError(), "join with field not in entity must be rejected: " + resp);
    }

    // ============================================================
    // Filter save 校验（item 1.5）
    // ============================================================

    /** Filter 合法：definition 为合法 TreeBean filter 树 → 通过。 */
    @Test
    public void testFilterSaveValidTreeBean() {
        String moduleId = ensureModule("mod-filter-ok");
        String tableId = saveEntityTable(moduleId, "T_FILTER_OK", null);
        String def = escapeGraphQL(JsonTool.stringify(
                FilterBeans.eq("status", "active")));

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_active\", "
                        + "definition: \"" + def + "\"}) { filterId } }");
        assertFalse(resp.hasError(), "valid TreeBean filter must succeed: " + resp);
    }

    /** Filter 合法：组合条件（and + eq + gt）→ 通过。 */
    @Test
    public void testFilterSaveValidComposite() {
        String moduleId = ensureModule("mod-filter-comp");
        String tableId = saveEntityTable(moduleId, "T_FILTER_COMP", null);
        String def = escapeGraphQL(JsonTool.stringify(
                FilterBeans.and(FilterBeans.eq("status", "active"), FilterBeans.gt("amount", 100))));

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_comp\", "
                        + "definition: \"" + def + "\"}) { filterId } }");
        assertFalse(resp.hasError(), "valid composite filter must succeed: " + resp);
    }

    /** Filter 非法：definition 不是合法 JSON（无法反序列化为 TreeBean）→ 显式失败。 */
    @Test
    public void testFilterSaveInvalidJsonFails() {
        String moduleId = ensureModule("mod-filter-bad");
        String tableId = saveEntityTable(moduleId, "T_FILTER_BAD", null);

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_bad\", "
                        + "definition: \"this is not json {{{\"}) { filterId } }");
        assertTrue(resp.hasError(), "invalid JSON filter must be rejected: " + resp);
    }

    /** Filter 非法：definition 为空 → 显式失败。 */
    @Test
    public void testFilterSaveEmptyDefinitionFails() {
        String moduleId = ensureModule("mod-filter-empty");
        String tableId = saveEntityTable(moduleId, "T_FILTER_EMPTY", null);

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_empty\", "
                        + "definition: \"   \"}) { filterId } }");
        assertTrue(resp.hasError(), "empty definition must be rejected: " + resp);
    }

    /** Filter isDefault 唯一性：同表第二个 isDefault=true → 显式失败。 */
    @Test
    public void testFilterIsDefaultUniquenessFails() {
        String moduleId = ensureModule("mod-filter-default");
        String tableId = saveEntityTable(moduleId, "T_FILTER_DEF", null);
        // 第一个默认过滤器
        String def = escapeGraphQL(JsonTool.stringify(FilterBeans.eq("a", "b")));
        GraphQLResponseBean first = runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_default1\", "
                        + "definition: \"" + def + "\", isDefault: true}) { filterId } }");
        assertFalse(first.hasError(), "first default filter must succeed: " + first);

        // 第二个默认过滤器 → 违反唯一性
        GraphQLResponseBean second = runGraphQL(
                "mutation { NopMetaTableFilter__save(data: {"
                        + "metaTableId: \"" + tableId + "\", filterName: \"f_default2\", "
                        + "definition: \"" + def + "\", isDefault: true}) { filterId } }");
        assertTrue(second.hasError(),
                "second default filter (isDefault=true) for same table must be rejected: " + second);
    }

    // ============================================================
    // MetaTableFieldResolver 直接调用（接线验证：解析器非空壳）
    // ============================================================

    /** 直接调用 resolver 验证 entity/external/sql 分派与返回字段集（不经过 GraphQL，证明解析器自身可用）。 */
    @Test
    public void testResolverDirectDispatch() {
        io.nop.metadata.service.field.MetaTableFieldResolver resolver =
                new io.nop.metadata.service.field.MetaTableFieldResolver();
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);

        String moduleId = ensureModule("mod-resolver-direct");
        // entity
        String entityId = saveEntity(moduleId, "ResolverEnt", "f1");
        NopMetaTable entityTable = getTable(saveEntityTable(moduleId, "T_RES_ENT", entityId));
        List<io.nop.metadata.service.field.ResolvedTableField> entityFields =
                resolver.resolve(entityTable, fieldDao);
        assertEquals(1, entityFields.size());
        assertEquals("f1", entityFields.get(0).getName());

        // external
        NopMetaTable extTable = getTable(saveExternalTable("T_RES_EXT", "qs_res_ext",
                "[{\"columnName\":\"c1\"}]"));
        List<io.nop.metadata.service.field.ResolvedTableField> extFields =
                resolver.resolve(extTable, fieldDao);
        assertEquals(1, extFields.size());
        assertEquals("c1", extFields.get(0).getName());

        // sql
        NopMetaTable sqlTable = getTable(saveSqlTable(moduleId, "T_RES_SQL", "SELECT x, y FROM t"));
        List<io.nop.metadata.service.field.ResolvedTableField> sqlFields =
                resolver.resolve(sqlTable, fieldDao);
        assertEquals(2, sqlFields.size());
    }

    /** resolver 对 entity 表 baseEntityId null 显式抛异常（不静默空集）。 */
    @Test
    public void testResolverEntityBaseEntityIdNullThrows() {
        io.nop.metadata.service.field.MetaTableFieldResolver resolver =
                new io.nop.metadata.service.field.MetaTableFieldResolver();
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        String moduleId = ensureModule("mod-resolver-null");
        NopMetaTable table = getTable(saveEntityTable(moduleId, "T_RES_NULL", null));

        Executable call = () -> resolver.resolve(table, fieldDao);
        assertThrows(io.nop.api.core.exceptions.NopException.class, call,
                "entity table with null baseEntityId must throw (not silent empty)");
    }

    // ============================================================
    // Join save 校验扩展（plan 0700-1：sql/external 表作为 join 端点 + entity/table 互斥 + ERR_JOIN_ENTITY_ID_NULL 放宽）
    // ============================================================

    /**
     * sql 表作为 join 端点：rightTableId 指向 sql 表，合法 rightField 属于该 sql 表 SELECT 解析列集合 → save 通过。
     *
     * <p>接线验证（Anti-Hollow）：通过需要 save override 运行时确实调用 table 端点字段校验（resolveFieldNames 解析 sql SELECT）。
     */
    @Test
    public void testJoinSaveSqlTableEndpointValid() {
        String moduleId = ensureModule("mod-join-sql-endpoint");
        String leftTableId = saveSqlTable(moduleId, "T_JOIN_SQL_LEFT", "SELECT order_id, amt FROM orders");
        String rightTableId = saveSqlTable(moduleId, "T_JOIN_SQL_RIGHT", "SELECT order_id, region FROM regions");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + leftTableId + "\", joinType: \"inner\", "
                        + "leftTableId: \"" + leftTableId + "\", leftField: \"order_id\", "
                        + "rightTableId: \"" + rightTableId + "\", rightField: \"order_id\"}) { joinId } }");
        assertFalse(resp.hasError(), "valid sql-table-endpoint join must succeed: " + resp);
    }

    /** external 表作为 join 端点：rightTableId 指向 external 表，合法 rightField 属于其 buildSql JSON 列集合 → save 通过。 */
    @Test
    public void testJoinSaveExternalTableEndpointValid() {
        String moduleId = ensureModule("mod-join-ext-endpoint");
        String leftTableId = saveSqlTable(moduleId, "T_JOIN_EXT_LEFT", "SELECT order_id FROM orders");
        String rightTableId = saveExternalTable("T_JOIN_EXT_RIGHT", "qs_join_ext_right",
                "[{\"columnName\":\"order_id\",\"dataType\":\"VARCHAR\"}]");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + leftTableId + "\", joinType: \"left\", "
                        + "leftTableId: \"" + leftTableId + "\", leftField: \"order_id\", "
                        + "rightTableId: \"" + rightTableId + "\", rightField: \"order_id\"}) { joinId } }");
        assertFalse(resp.hasError(), "valid external-table-endpoint join must succeed: " + resp);
    }

    /** sql 表端点的 rightField 不属于该表 SELECT 解析列集合 → 显式失败（不静默存入悬空字段引用）。 */
    @Test
    public void testJoinSaveTableEndpointFieldNotInTableFails() {
        String moduleId = ensureModule("mod-join-sql-bad");
        String leftTableId = saveSqlTable(moduleId, "T_JOIN_SQL_BAD_L", "SELECT order_id FROM orders");
        String rightTableId = saveSqlTable(moduleId, "T_JOIN_SQL_BAD_R", "SELECT order_id FROM regions");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + leftTableId + "\", joinType: \"inner\", "
                        + "leftTableId: \"" + leftTableId + "\", leftField: \"order_id\", "
                        + "rightTableId: \"" + rightTableId + "\", rightField: \"nonexistent_col\"}) { joinId } }");
        assertTrue(resp.hasError(),
                "join table-endpoint with field not in table column set must be rejected: " + resp);
    }

    /** entity/table 互斥违反：同一端点同时设置 leftEntityId 和 leftTableId → 显式失败。 */
    @Test
    public void testJoinSaveBothEndpointsSetFails() {
        String moduleId = ensureModule("mod-join-mutex");
        String entityId = saveEntity(moduleId, "MutexEnt", "order_id");
        String tableId = saveSqlTable(moduleId, "T_JOIN_MUTEX", "SELECT order_id FROM orders");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + tableId + "\", joinType: \"inner\", "
                        + "leftEntityId: \"" + entityId + "\", leftTableId: \"" + tableId + "\", leftField: \"order_id\", "
                        + "rightTableId: \"" + tableId + "\", rightField: \"order_id\"}) { joinId } }");
        assertTrue(resp.hasError(),
                "join side with both entityId and tableId set (mutex violation) must be rejected: " + resp);
    }

    /** table 端点指向 entity-type NopMetaTable → 显式失败（entity-type 表应走 entityId 路径）。 */
    @Test
    public void testJoinSaveTableEndpointEntityTypeFails() {
        String moduleId = ensureModule("mod-join-enttype");
        String entityId = saveEntity(moduleId, "EntTypeEnt", "order_id");
        String entityTableId = saveEntityTable(moduleId, "T_JOIN_ENTTYPE", entityId); // entity-type 逻辑表
        String sqlTableId = saveSqlTable(moduleId, "T_JOIN_ENTTYPE_SQL", "SELECT order_id FROM orders");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + sqlTableId + "\", joinType: \"inner\", "
                        + "leftTableId: \"" + sqlTableId + "\", leftField: \"order_id\", "
                        + "rightTableId: \"" + entityTableId + "\", rightField: \"order_id\"}) { joinId } }");
        assertTrue(resp.hasError(),
                "join table-endpoint referencing entity-type NopMetaTable must be rejected: " + resp);
    }

    /**
     * ERR_JOIN_ENTITY_ID_NULL 放宽：table 端点合法时（tableId 非空、entityId 为 null）→ 不再因 entityId==null 报错，save 通过。
     * 回归保护：既有 entity 路径不受影响（testJoinSaveValid 覆盖 entity 端点）。
     */
    @Test
    public void testJoinSaveTableEndpointRelaxesEntityIdNull() {
        String moduleId = ensureModule("mod-join-relax");
        String leftTableId = saveSqlTable(moduleId, "T_JOIN_RELAX_L", "SELECT order_id FROM orders");
        String rightTableId = saveSqlTable(moduleId, "T_JOIN_RELAX_R", "SELECT order_id FROM regions");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + leftTableId + "\", joinType: \"inner\", "
                        // left/right 仅设 tableId，entityId 均为 null——放宽后合法
                        + "leftTableId: \"" + leftTableId + "\", leftField: \"order_id\", "
                        + "rightTableId: \"" + rightTableId + "\", rightField: \"order_id\"}) { joinId } }");
        assertFalse(resp.hasError(),
                "table-endpoint join with null entityId must succeed (ERR_JOIN_ENTITY_ID_NULL relaxed): " + resp);
    }

    /** 端点 mandatory：left/right 两端都既无 entityId 也无 tableId → 显式失败（不静默存入无端点关联）。 */
    @Test
    public void testJoinSaveNoEndpointFails() {
        String moduleId = ensureModule("mod-join-noendpoint");
        String tableId = saveSqlTable(moduleId, "T_JOIN_NOENDPOINT", "SELECT order_id FROM orders");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + tableId + "\", joinType: \"inner\", "
                        + "leftField: \"order_id\", rightField: \"order_id\"}) { joinId } }");
        assertTrue(resp.hasError(),
                "join with neither entityId nor tableId on a side must be rejected: " + resp);
    }

    // ============================================================
    // sql/external Measure/Dimension 跨表字段引用校验（plan 0700-1 D4：name-based 可达列名集合）
    // ============================================================

    /**
     * sql 表 Measure 跨表（table 端点）合法：sql 表 T 定义 NopMetaTableJoin 指向另一 external 表端点，
     * 其 Measure 引用该 external 表的字段名（直连可达）→ save 通过（name-based 可达集包含端点表列名）。
     *
     * <p>端到端验证 + 接线验证：从「建 sql 表 → 建 table 端点 join → save Measure 引用 join 可达字段」完整跑通，
     * 证明 resolveAllowedFieldNames 运行时被调用且并入端点表列名（非空壳）。
     */
    @Test
    public void testSqlTableMeasureCrossTableViaTableEndpointValid() {
        String moduleId = ensureModule("mod-sqlmeasure-xtab");
        String sqlTableId = saveSqlTable(moduleId, "T_SQLMEASURE_XTAB", "SELECT base_col FROM base");
        String extTableId = saveExternalTable("T_SQLMEASURE_EXT", "qs_sqlmeasure_ext",
                "[{\"columnName\":\"amount\",\"dataType\":\"DOUBLE\"}]");
        // join 属于 sql 表，right 端点为 external 表
        saveTableJoin(sqlTableId, "inner", sqlTableId, extTableId, "base_col", "amount");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + sqlTableId + "\", measureName: \"m_xtab\", "
                        + "aggFunc: \"sum\", entityFieldId: \"amount\"}) { measureId } }");
        assertFalse(resp.hasError(),
                "sql-table measure referencing join-reachable external field must succeed (name-based): " + resp);
    }

    /**
     * external 表 Measure 跨表（entity 端点）合法：external 表 T 定义 join 指向 entity 端点，
     * 其 Measure 引用该 entity 的字段名（直连可达）→ save 通过（name-based 可达集包含 entity 端点字段名）。
     */
    @Test
    public void testExternalTableMeasureCrossTableViaEntityEndpointValid() {
        String moduleId = ensureModule("mod-extmeasure-xtab");
        String entityId = saveEntity(moduleId, "ExtXtabEnt", "region");
        String extTableId = saveExternalTable("T_EXTMEASURE_XTAB", "qs_extmeasure_xtab",
                "[{\"columnName\":\"base_col\",\"dataType\":\"VARCHAR\"}]");
        // join 属于 external 表，right 端点为 entity（leftTableId 为 external 表自身）
        saveTableEntityJoin(extTableId, "left", extTableId, entityId, "base_col", "region");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + extTableId + "\", measureName: \"m_ext_xtab\", "
                        + "aggFunc: \"count\", entityFieldId: \"region\"}) { measureId } }");
        assertFalse(resp.hasError(),
                "external-table measure referencing join-reachable entity field must succeed (name-based): " + resp);
    }

    /** sql 表 Measure 引用不可达字段（既非自身列、也无 join 端点可达）→ save 显式失败（不静默存入悬空引用）。 */
    @Test
    public void testSqlTableMeasureCrossTableDanglingFails() {
        String moduleId = ensureModule("mod-sqlmeasure-dangle");
        String sqlTableId = saveSqlTable(moduleId, "T_SQLMEASURE_DANGLE", "SELECT base_col FROM base");
        String extTableId = saveExternalTable("T_SQLMEASURE_DANGLE_EXT", "qs_sqlmeasure_dangle",
                "[{\"columnName\":\"amount\",\"dataType\":\"DOUBLE\"}]");
        saveTableJoin(sqlTableId, "inner", sqlTableId, extTableId, "base_col", "amount");

        // 引用既不在 sql 表自身列、也不在 external 端点表列集合的悬空字段
        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + sqlTableId + "\", measureName: \"m_dangle\", "
                        + "aggFunc: \"sum\", entityFieldId: \"ghost_field\"}) { measureId } }");
        assertTrue(resp.hasError(),
                "sql-table measure with dangling field (not self nor join-reachable) must be rejected: " + resp);
    }

    /** sql 表自身列引用仍合法（可达集包含自身列，回归）。 */
    @Test
    public void testSqlTableMeasureSelfFieldValid() {
        String moduleId = ensureModule("mod-sqlmeasure-self");
        String sqlTableId = saveSqlTable(moduleId, "T_SQLMEASURE_SELF", "SELECT base_col FROM base");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + sqlTableId + "\", measureName: \"m_self\", "
                        + "aggFunc: \"sum\", entityFieldId: \"base_col\"}) { measureId } }");
        assertFalse(resp.hasError(), "sql-table measure referencing its own column must succeed: " + resp);
    }

    /**
     * resolver.resolveAllowedFieldNames 直接调用验证（接线证明 helper 非空壳）：
     * sql 表自身列 + join table 端点 external 表列 + join entity 端点字段名 → 可达集包含三者。
     */
    @Test
    public void testResolverResolveAllowedFieldNames() {
        io.nop.metadata.service.field.MetaTableFieldResolver resolver =
                new io.nop.metadata.service.field.MetaTableFieldResolver();
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        IEntityDao<NopMetaTableJoin> joinDao = daoProvider.daoFor(NopMetaTableJoin.class);
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);

        String moduleId = ensureModule("mod-resolver-allowed-fn");
        String entityId = saveEntity(moduleId, "AllowedFnEnt", "region");
        String sqlTableId = saveSqlTable(moduleId, "T_ALLOWED_FN", "SELECT self_col FROM t");
        String extTableId = saveExternalTable("T_ALLOWED_FN_EXT", "qs_allowed_fn",
                "[{\"columnName\":\"amount\",\"dataType\":\"DOUBLE\"}]");
        // 一个 join：left table 端点（external），right entity 端点
        saveTableEntityJoin(sqlTableId, "inner", extTableId, entityId, "amount", "region");

        NopMetaTable table = getTable(sqlTableId);
        java.util.Set<String> allowed = resolver.resolveAllowedFieldNames(table, fieldDao, joinDao, tableDao);
        assertTrue(allowed.contains("self_col"), "reachable set must contain sql table's own column: " + allowed);
        assertTrue(allowed.contains("amount"), "reachable set must contain external table-endpoint column: " + allowed);
        assertTrue(allowed.contains("region"), "reachable set must contain entity-endpoint field name: " + allowed);
    }

    /** resolver.resolveAllowedFieldNames 对无 join 的表 → 仅含自身列名（退化）。 */
    @Test
    public void testResolverResolveAllowedFieldNamesNoJoin() {
        io.nop.metadata.service.field.MetaTableFieldResolver resolver =
                new io.nop.metadata.service.field.MetaTableFieldResolver();
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        IEntityDao<NopMetaTableJoin> joinDao = daoProvider.daoFor(NopMetaTableJoin.class);
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);

        String moduleId = ensureModule("mod-resolver-allowed-fn-nojoin");
        String sqlTableId = saveSqlTable(moduleId, "T_ALLOWED_FN_NOJOIN", "SELECT only_col FROM t");

        NopMetaTable table = getTable(sqlTableId);
        java.util.Set<String> allowed = resolver.resolveAllowedFieldNames(table, fieldDao, joinDao, tableDao);
        assertEquals(1, allowed.size(), "no-join sql table reachable set must be just own column: " + allowed);
        assertTrue(allowed.contains("only_col"));
    }

    // ============================================================
    // helpers
    // ============================================================

    private GraphQLResponseBean runGraphQL(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveTableFields(String tableId) {
        GraphQLResponseBean resp = runGraphQL(
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + tableId + "\") { tableType fields { name sourceType type } } }");
        assertFalse(resp.hasError(), "resolveTableFields should succeed: " + resp);
        return (Map<String, Object>) ((Map<String, Object>) resp.getData())
                .get("NopMetaTable__resolveTableFields");
    }

    private String ensureModule(String moduleName) {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleName, moduleName));
        NopMetaModule module = dao.findFirstByQuery(q);
        if (module != null) {
            return module.getMetaModuleId();
        }
        module = dao.newEntity();
        module.setModuleId("nop/" + moduleName);
        module.setModuleName(moduleName);
        module.setDisplayName(moduleName);
        module.setModuleVersion(1L);
        module.setStatus(_NopMetadataCoreConstants.MODULE_STATUS_RELEASED);
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(module);
        dao.flushSession();
        return module.getMetaModuleId();
    }

    /**
     * 建一个 NopMetaOrmModel + NopMetaEntity + 若干 NopMetaEntityField，返回 entityId。
     * OrmModel 是 NopMetaEntity.ormModelId 的 mandatory 引用，必须先建。
     */
    private String saveEntity(String moduleId, String entityName, String... fieldNames) {
        IEntityDao<NopMetaOrmModel> ormDao = daoProvider.daoFor(NopMetaOrmModel.class);
        NopMetaOrmModel ormModel = ormDao.newEntity();
        ormModel.setMetaModuleId(moduleId);
        ormModel.setModelName(entityName + "_model");
        ormModel.setIsDelta((byte) 0);
        ormDao.saveEntity(ormModel);
        String ormModelId = ormModel.getOrmModelId();

        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity entity = dao.newEntity();
        entity.setOrmModelId(ormModelId);
        entity.setEntityName(entityName);
        entity.setTableName("tbl_" + entityName);
        entity.setDisplayName(entityName);
        entity.setClassName("io.test." + entityName);
        dao.saveEntity(entity);
        String entityId = entity.getMetaEntityId();

        IEntityDao<NopMetaEntityField> fdao = daoProvider.daoFor(NopMetaEntityField.class);
        int propId = 1;
        for (String fn : fieldNames) {
            NopMetaEntityField f = fdao.newEntity();
            f.setMetaEntityId(entityId);
            f.setFieldName(fn);
            f.setColumnCode(fn.toUpperCase());
            f.setPropId(propId++);
            fdao.saveEntity(f);
        }
        dao.flushSession();
        return entityId;
    }

    private String findEntityFieldId(String entityId, String fieldName) {
        IEntityDao<NopMetaEntityField> dao = daoProvider.daoFor(NopMetaEntityField.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_fieldName, fieldName));
        NopMetaEntityField f = dao.findFirstByQuery(q);
        assertNotNull(f, "entity field must exist: " + fieldName);
        return f.getEntityFieldId();
    }

    private String saveEntityTable(String moduleId, String tableName, String baseEntityId) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_ENTITY);
        if (baseEntityId != null) {
            t.setBaseEntityId(baseEntityId);
        }
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }

    /** 保存一条 NopMetaTableJoin（entity-entity 直连关联），用于跨表 Measure/Dimension 校验测试。 */
    @SuppressWarnings("UnusedReturnValue")
    private String saveJoin(String metaTableId, String joinType, String leftEntityId, String rightEntityId,
                            String leftField, String rightField) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin j = dao.newEntity();
        j.setMetaTableId(metaTableId);
        j.setJoinType(joinType);
        j.setLeftEntityId(leftEntityId);
        j.setRightEntityId(rightEntityId);
        j.setLeftField(leftField);
        j.setRightField(rightField);
        dao.saveEntity(j);
        dao.flushSession();
        return j.getJoinId();
    }

    /** 保存一条 table-table 端点的 NopMetaTableJoin（leftTableId/rightTableId 均为 external/sql 表端点）。 */
    @SuppressWarnings("UnusedReturnValue")
    private String saveTableJoin(String metaTableId, String joinType, String leftTableId, String rightTableId,
                                 String leftField, String rightField) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin j = dao.newEntity();
        j.setMetaTableId(metaTableId);
        j.setJoinType(joinType);
        j.setLeftTableId(leftTableId);
        j.setRightTableId(rightTableId);
        j.setLeftField(leftField);
        j.setRightField(rightField);
        dao.saveEntity(j);
        dao.flushSession();
        return j.getJoinId();
    }

    /** 保存一条 table-entity 混合端点的 NopMetaTableJoin（leftTableId 表端点 + rightEntityId entity 端点）。 */
    @SuppressWarnings("UnusedReturnValue")
    private String saveTableEntityJoin(String metaTableId, String joinType, String leftTableId, String rightEntityId,
                                       String leftField, String rightField) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin j = dao.newEntity();
        j.setMetaTableId(metaTableId);
        j.setJoinType(joinType);
        j.setLeftTableId(leftTableId);
        j.setRightEntityId(rightEntityId);
        j.setLeftField(leftField);
        j.setRightField(rightField);
        dao.saveEntity(j);
        dao.flushSession();
        return j.getJoinId();
    }

    private String saveExternalTable(String tableName, String querySpace, String buildSqlJson) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureExternalSystemModuleId());
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL);
        t.setQuerySpace(querySpace);
        t.setBuildSql(buildSqlJson);
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }

    private String saveSqlTable(String moduleId, String tableName, String sourceSql) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_SQL);
        t.setSourceSql(sourceSql);
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }

    private NopMetaTable getTable(String tableId) {
        return daoProvider.daoFor(NopMetaTable.class).getEntityById(tableId);
    }

    private String ensureExternalSystemModuleId() {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, "nop/meta-external"));
        NopMetaModule module = dao.findFirstByQuery(q);
        if (module != null) {
            return module.getMetaModuleId();
        }
        module = dao.newEntity();
        module.setModuleId("nop/meta-external");
        module.setModuleName("meta-external");
        module.setDisplayName("外部表系统模块");
        module.setModuleVersion(1L);
        module.setStatus("RELEASED");
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(module);
        dao.flushSession();
        return module.getMetaModuleId();
    }

    private static String escapeGraphQL(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
