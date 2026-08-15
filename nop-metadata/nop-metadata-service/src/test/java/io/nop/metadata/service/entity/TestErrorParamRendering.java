package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
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
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-6 代表点回归（plan 2026-08-15-1913-3 Phase 2）：错误消息识别性参数真实渲染——
 * 最终用户可见消息含真实身份值、不含字面 {@code {placeholder}}（沿
 * {@code TestMetaQualityRuleExecutorErrorParams} 的 "renders real value + no literal placeholder" 模式）。
 *
 * <p>代表点覆盖：
 * <ul>
 *   <li>{@code NopMetaTableJoinBizModel} save 校验 table 端点 tableType 不允许（原 :164）——
 *       create 路径（joinId 尚不存在 → 换码 ERR_JOIN_TABLE_TYPE_NOT_ALLOWED_ON_CREATE，
 *       metaTableId 提供身份）与 update 路径（joinId 自 data map 下沉 → 原码 joinId 渲染）。</li>
 *   <li>Measure save 字段引用不存在（变量形态 errOnInvalid 修复后 allowedEntityIds 渲染）。</li>
 * </ul>
 *
 * <p>elementIndex 族（resolver 直接调用）与 P1-7（requireSupportedProductName）代表点分别见
 * {@code TestMetaTableFieldResolverBuildSql#testMissingColumnNameRendersRealElementIndex} 与
 * {@code TestExternalTableStructureReader#testUnsupportedDialectStillDialectGateError}。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestErrorParamRendering extends JunitBaseTestCase {

    public TestErrorParamRendering() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    /**
     * create 路径（原 :164）：rightTableId 指向 entity-type 逻辑表且无 joinId →
     * ERR_JOIN_TABLE_TYPE_NOT_ALLOWED_ON_CREATE，消息渲染真实 metaTableId/tableId/tableType，
     * 不含字面占位符。
     */
    @Test
    public void testJoinSaveEntityTypeEndpointCreatePathRendersIdentity() {
        String moduleId = ensureModule("mod-errparam-create");
        String entityId = ensureEntity(moduleId, "ErrParamEnt", "order_id");
        String entityTableId = saveEntityTable(moduleId, "T_ERRPARAM_ENT", entityId);
        String sqlTableId = saveSqlTable(moduleId, "T_ERRPARAM_SQL", "SELECT order_id FROM orders");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "metaTableId: \"" + sqlTableId + "\", joinType: \"inner\", "
                        + "leftTableId: \"" + sqlTableId + "\", leftField: \"order_id\", "
                        + "rightTableId: \"" + entityTableId + "\", rightField: \"order_id\"}) { joinId } }");
        assertTrue(resp.hasError(), "entity-type table endpoint must be rejected: " + resp);
        String msg = resp.getErrors().get(0).getMessage();
        assertTrue(msg.contains(sqlTableId),
                "rendered message must contain real metaTableId identity (P1-6), got: " + msg);
        assertTrue(msg.contains(entityTableId),
                "rendered message must contain real tableId identity (P1-6), got: " + msg);
        assertTrue(msg.contains("entity"),
                "rendered message must contain real tableType (P1-6), got: " + msg);
        assertFalse(msg.contains("{metaTableId}") || msg.contains("{tableId}")
                        || msg.contains("{tableType}") || msg.contains("{joinId}") || msg.contains("{side}"),
                "no literal {placeholder} may remain, got: " + msg);
    }

    /**
     * update 路径（原 :164）：data map 携带既有 joinId → 原码 ERR_JOIN_TABLE_TYPE_NOT_ALLOWED，
     * joinId 真实渲染（轨 1 下沉穿参）。
     */
    @Test
    public void testJoinSaveEntityTypeEndpointUpdatePathRendersJoinId() {
        String moduleId = ensureModule("mod-errparam-update");
        String entityId = ensureEntity(moduleId, "ErrParamEnt2", "order_id");
        String entityTableId = saveEntityTable(moduleId, "T_ERRPARAM_ENT2", entityId);
        String sqlTableId = saveSqlTable(moduleId, "T_ERRPARAM_SQL2", "SELECT order_id FROM orders");
        String joinId = saveTableJoinRow(sqlTableId, "inner", sqlTableId, "order_id", "order_id");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableJoin__save(data: {"
                        + "joinId: \"" + joinId + "\", "
                        + "metaTableId: \"" + sqlTableId + "\", joinType: \"inner\", "
                        + "leftTableId: \"" + sqlTableId + "\", leftField: \"order_id\", "
                        + "rightTableId: \"" + entityTableId + "\", rightField: \"order_id\"}) { joinId } }");
        assertTrue(resp.hasError(), "entity-type table endpoint must be rejected: " + resp);
        String msg = resp.getErrors().get(0).getMessage();
        assertTrue(msg.contains(joinId),
                "update-path message must render the real joinId sunk from data map (P1-6 track 1), got: " + msg);
        assertFalse(msg.contains("{joinId}"),
                "no literal {joinId} placeholder may remain, got: " + msg);
    }

    /**
     * 变量形态代表点（原 :214）：Measure 引用不存在的 entityFieldId PK →
     * allowedEntityIds（entity 分支适用集）真实渲染；availableFields 为空集渲染 "[]"（非 null 空壳）。
     */
    @Test
    public void testMeasureSaveNonExistentFieldRendersAllowedEntityIds() {
        String moduleId = ensureModule("mod-errparam-measure");
        String entityId = ensureEntity(moduleId, "ErrParamMeasureEnt", "amount");
        String tableId = saveEntityTable(moduleId, "T_ERRPARAM_MEASURE", entityId);

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaTableMeasure__save(data: {"
                        + "metaTableId: \"" + tableId + "\", measureName: \"m_errparam\", "
                        + "aggFunc: \"sum\", entityFieldId: \"__nope_field_pk__\"}) { measureId } }");
        assertTrue(resp.hasError(), "measure referencing non-existent field PK must be rejected: " + resp);
        String msg = resp.getErrors().get(0).getMessage();
        assertTrue(msg.contains(entityId),
                "rendered message must contain real allowedEntityIds values (P1-6), got: " + msg);
        assertFalse(msg.contains("{allowedEntityIds}") || msg.contains("{availableFields}"),
                "no literal {allowedEntityIds}/{availableFields} placeholders may remain, got: " + msg);
    }

    // ============================================================
    // helpers（沿 TestNopMetaBiSemanticBizModel 模式，最小集）
    // ============================================================

    private GraphQLResponseBean runGraphQL(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
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

    private String ensureEntity(String moduleId, String entityName, String... fieldNames) {
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

    /** 直接经 DAO 保存一条既有 Join 行（绕过 save 校验，用于构造 update 场景的真实 joinId）。 */
    private String saveTableJoinRow(String metaTableId, String joinType, String leftTableId,
                                    String leftField, String rightField) {
        IEntityDao<io.nop.metadata.dao.entity.NopMetaTableJoin> dao =
                daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaTableJoin.class);
        io.nop.metadata.dao.entity.NopMetaTableJoin j = dao.newEntity();
        j.setMetaTableId(metaTableId);
        j.setJoinType(joinType);
        j.setLeftTableId(leftTableId);
        j.setLeftField(leftField);
        j.setRightField(rightField);
        dao.saveEntity(j);
        dao.flushSession();
        return j.getJoinId();
    }
}
