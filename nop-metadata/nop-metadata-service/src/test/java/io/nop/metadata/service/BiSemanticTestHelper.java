package io.nop.metadata.service;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
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
import io.nop.metadata.dao.entity.NopMetaEntityJoin;

import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BiSemanticTestHelper {

    private final IGraphQLEngine graphQLEngine;
    private final IDaoProvider daoProvider;

    public BiSemanticTestHelper(IGraphQLEngine graphQLEngine, IDaoProvider daoProvider) {
        this.graphQLEngine = graphQLEngine;
        this.daoProvider = daoProvider;
    }

    public IGraphQLEngine graphQLEngine() {
        return graphQLEngine;
    }

    public IDaoProvider daoProvider() {
        return daoProvider;
    }

    public GraphQLResponseBean runGraphQL(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveEntityFields(String tableId) {
        GraphQLResponseBean resp = runGraphQL(
                "query { NopMetaEntity__resolveEntityFields(metaEntityId: \"" + tableId + "\") { entityKind fields { name sourceType type } } }");
        assertFalse(resp.hasError(), "resolveEntityFields should succeed: " + resp);
        return (Map<String, Object>) ((Map<String, Object>) resp.getData())
                .get("NopMetaEntity__resolveEntityFields");
    }

    public String ensureModule(String moduleName) {
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

    public String saveEntity(String moduleId, String entityName, String... fieldNames) {
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

    public String findEntityFieldId(String entityId, String fieldName) {
        IEntityDao<NopMetaEntityField> dao = daoProvider.daoFor(NopMetaEntityField.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_fieldName, fieldName));
        NopMetaEntityField f = dao.findFirstByQuery(q);
        assertNotNull(f, "entity field must exist: " + fieldName);
        return f.getEntityFieldId();
    }

    public String saveEntityTable(String moduleId, String tableName, String baseEntityId) {
        // plan 2261 概念缩减：实体行自身即宿主（原“表行→baseEntityId”间接配对已删除），
        // 字段/Measure 均挂在实体行上，因此有宿主实体时直接复用该实体行。
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

    public String saveJoin(String metaEntityId, String joinType, String leftEntityId, String rightEntityId,
                           String leftField, String rightField) {
        IEntityDao<NopMetaEntityJoin> dao = daoProvider.daoFor(NopMetaEntityJoin.class);
        NopMetaEntityJoin j = dao.newEntity();
        j.setMetaEntityId(metaEntityId);
        j.setJoinType(joinType);
        j.setLeftEntityId(leftEntityId);
        j.setRightEntityId(rightEntityId);
        j.setLeftField(leftField);
        j.setRightField(rightField);
        dao.saveEntity(j);
        dao.flushSession();
        return j.getJoinId();
    }

    public String saveTableJoin(String metaEntityId, String joinType, String leftTableId, String rightTableId,
                                String leftField, String rightField) {
        IEntityDao<NopMetaEntityJoin> dao = daoProvider.daoFor(NopMetaEntityJoin.class);
        NopMetaEntityJoin j = dao.newEntity();
        j.setMetaEntityId(metaEntityId);
        j.setJoinType(joinType);
        j.setLeftEntityId(leftTableId);
        j.setRightEntityId(rightTableId);
        j.setLeftField(leftField);
        j.setRightField(rightField);
        dao.saveEntity(j);
        dao.flushSession();
        return j.getJoinId();
    }

    public String saveTableEntityJoin(String metaEntityId, String joinType, String leftTableId, String rightEntityId,
                                      String leftField, String rightField) {
        IEntityDao<NopMetaEntityJoin> dao = daoProvider.daoFor(NopMetaEntityJoin.class);
        NopMetaEntityJoin j = dao.newEntity();
        j.setMetaEntityId(metaEntityId);
        j.setJoinType(joinType);
        j.setLeftEntityId(leftTableId);
        j.setRightEntityId(rightEntityId);
        j.setLeftField(leftField);
        j.setRightField(rightField);
        dao.saveEntity(j);
        dao.flushSession();
        return j.getJoinId();
    }

    public String saveExternalTable(String tableName, String querySpace, String buildSqlJson) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity t = dao.newEntity();
        t.setMetaModuleId(ensureExternalSystemModuleId());
        t.setOrmModelId("orm_" + tableName);
        t.setIsDelta((byte) 0);
        t.setEntityName(tableName);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setEntityKind(_NopMetadataCoreConstants.ENTITY_KIND_EXTERNAL);
        t.setQuerySpace(querySpace);
        t.setExternalColumns(buildSqlJson);
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaEntityId();
    }

    public String saveSqlTable(String moduleId, String tableName, String sourceSql) {
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

    public NopMetaEntity getTable(String tableId) {
        return daoProvider.daoFor(NopMetaEntity.class).getEntityById(tableId);
    }

    public String ensureExternalSystemModuleId() {
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

    public static String escapeGraphQL(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
