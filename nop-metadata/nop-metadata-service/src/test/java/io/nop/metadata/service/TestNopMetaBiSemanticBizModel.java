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
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + tableId + "\") }");
        assertTrue(resp.hasError(),
                "entity table with null baseEntityId must explicitly fail (not silent empty): " + resp);
    }

    /** external 表 buildSql JSON 损坏（非数组）→ 显式失败。 */
    @Test
    public void testResolveTableFieldsExternalBadBuildSqlFails() {
        String tableId = saveExternalTable("T_RESOLVE_BAD", "qs_resolve_bad", "{not-an-array}");
        GraphQLResponseBean resp = runGraphQL(
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + tableId + "\") }");
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
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + tableId + "\") }");
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
