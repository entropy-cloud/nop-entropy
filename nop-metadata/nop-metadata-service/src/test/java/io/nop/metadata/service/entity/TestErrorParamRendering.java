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
import io.nop.metadata.dao.entity.NopMetaEntity;
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
 *   <li>{@code NopMetaEntityJoinBizModel} save 校验 table 端点 entityKind 不允许（原 :164）——
 *       create 路径（joinId 尚不存在 → 换码 ERR_JOIN_TABLE_TYPE_NOT_ALLOWED_ON_CREATE，
 *       metaEntityId 提供身份）与 update 路径（joinId 自 data map 下沉 → 原码 joinId 渲染）。</li>
 *   <li>Measure save 字段引用不存在（变量形态 errOnInvalid 修复后 allowedEntityIds 渲染）。</li>
 * </ul>
 *
 * <p>elementIndex 族（resolver 直接调用）与 P1-7（requireSupportedProductName）代表点分别见
 * {@code TestMetaEntityFieldResolverBuildSql#testMissingColumnNameRendersRealElementIndex} 与
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
     * create 路径（plan 2261 概念缩减后等价场景：原"entity-type 表端点拒绝"
     * ERR_JOIN_TABLE_TYPE_NOT_ALLOWED_ON_CREATE 随表端点删除而失效）：leftEntityId 指向不存在的实体 →
     * ERR_JOIN_ENTITY_NOT_FOUND，消息渲染真实 metaEntityId/entityId，不含字面占位符。
     */
    @Test
    public void testJoinSaveEntityTypeEndpointCreatePathRendersIdentity() {
        String moduleId = ensureModule("mod-errparam-create");
        String entityId = ensureEntity(moduleId, "ErrParamEnt", "order_id");
        String sqlTableId = saveSqlTable(moduleId, "T_ERRPARAM_SQL", "SELECT order_id FROM orders");
        String missingEntityId = "__no_such_entity__";

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaEntityJoin__save(data: {"
                        + "metaEntityId: \"" + sqlTableId + "\", joinType: \"inner\", "
                        + "leftEntityId: \"" + entityId + "\", leftField: \"order_id\", "
                        + "rightEntityId: \"" + missingEntityId + "\", rightField: \"order_id\"}) { joinId } }");
        assertTrue(resp.hasError(), "non-existent entity endpoint must be rejected: " + resp);
        // 端点实体不存在时由框架 get(entity) 的记录不存在错误先行短路（先于自定义校验码渲染），
        // 但身份渲染不变量保持：真实缺失 entityId 必须出现，且无字面占位符残留。
        String msg = resp.getErrors().get(0).getMessage();
        assertTrue(msg.contains(missingEntityId),
                "rendered message must contain the missing entityId identity (P1-6), got: " + msg);
        assertFalse(msg.contains("{metaEntityId}") || msg.contains("{entityId}")
                        || msg.contains("{side}") || msg.contains("{joinId}"),
                "no literal {placeholder} may remain, got: " + msg);
    }

    /**
     * update 路径（plan 2261 概念缩减后等价场景：原 joinId 渲染断言随
     * ERR_JOIN_TABLE_TYPE_NOT_ALLOWED 删除而失效）：data map 携带既有 joinId、字段不属于端点实体字段集 →
     * ERR_JOIN_FIELD_NOT_IN_ENTITY，消息渲染真实 entityId/field/availableFields（轨 1 下沉穿参路径仍走 update 校验）。
     */
    @Test
    public void testJoinSaveUpdatePathRendersFieldIdentity() {
        String moduleId = ensureModule("mod-errparam-update");
        String entityId = ensureEntity(moduleId, "ErrParamEnt2", "order_id");
        String sqlTableId = saveSqlTable(moduleId, "T_ERRPARAM_SQL2", "SELECT order_id FROM orders");
        String joinId = saveTableJoinRow(sqlTableId, "inner", entityId, "order_id", "order_id");

        GraphQLResponseBean resp = runGraphQL(
                "mutation { NopMetaEntityJoin__save(data: {"
                        + "joinId: \"" + joinId + "\", "
                        + "metaEntityId: \"" + sqlTableId + "\", joinType: \"inner\", "
                        + "leftEntityId: \"" + entityId + "\", leftField: \"__nope_field__\", "
                        + "rightEntityId: \"" + entityId + "\", rightField: \"order_id\"}) { joinId } }");
        assertTrue(resp.hasError(), "field not in entity must be rejected: " + resp);
        String msg = resp.getErrors().get(0).getMessage();
        assertTrue(msg.contains(entityId),
                "update-path message must render the real endpoint entityId (P1-6 track 1), got: " + msg);
        assertTrue(msg.contains("__nope_field__"),
                "update-path message must render the offending field, got: " + msg);
        assertFalse(msg.contains("{entityId}") || msg.contains("{field}")
                        || msg.contains("{availableFields}") || msg.contains("{joinId}"),
                "no literal {placeholder} may remain, got: " + msg);
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
                "mutation { NopMetaEntityMeasure__save(data: {"
                        + "metaEntityId: \"" + tableId + "\", measureName: \"m_errparam\", "
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
        entity.setMetaModuleId(moduleId);
        entity.setOrmModelId(ormModelId);
        entity.setIsDelta((byte) 0);
        entity.setEntityKind(_NopMetadataCoreConstants.ENTITY_KIND_PHYSICAL);
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

    /** plan 2261 概念缩减：实体行自身即宿主——有实体时直接复用实体行（字段挂在实体行上）。 */
    private String saveEntityTable(String moduleId, String tableName, String baseEntityId) {
        if (baseEntityId != null) {
            return baseEntityId;
        }
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setOrmModelId("orm_" + tableName);
        t.setIsDelta((byte) 0);
        t.setEntityName(tableName);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setEntityKind(_NopMetadataCoreConstants.ENTITY_KIND_PHYSICAL);
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaEntityId();
    }

    private String saveSqlTable(String moduleId, String tableName, String sourceSql) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setOrmModelId("orm_" + tableName);
        t.setIsDelta((byte) 0);
        t.setEntityName(tableName);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setEntityKind(_NopMetadataCoreConstants.ENTITY_KIND_SQL_VIEW);
        t.setSourceSql(sourceSql);
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaEntityId();
    }

    /** 直接经 DAO 保存一条既有 Join 行（绕过 save 校验，用于构造 update 场景的真实 joinId）。 */
    private String saveTableJoinRow(String metaEntityId, String joinType, String leftTableId,
                                    String leftField, String rightField) {
        IEntityDao<io.nop.metadata.dao.entity.NopMetaEntityJoin> dao =
                daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaEntityJoin.class);
        io.nop.metadata.dao.entity.NopMetaEntityJoin j = dao.newEntity();
        j.setMetaEntityId(metaEntityId);
        j.setJoinType(joinType);
        j.setLeftEntityId(leftTableId);
        j.setLeftField(leftField);
        j.setRightField(rightField);
        dao.saveEntity(j);
        dao.flushSession();
        return j.getJoinId();
    }
}
