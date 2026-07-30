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
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;

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
    public Map<String, Object> resolveTableFields(String tableId) {
        GraphQLResponseBean resp = runGraphQL(
                "query { NopMetaTable__resolveTableFields(metaTableId: \"" + tableId + "\") { tableType fields { name sourceType type } } }");
        assertFalse(resp.hasError(), "resolveTableFields should succeed: " + resp);
        return (Map<String, Object>) ((Map<String, Object>) resp.getData())
                .get("NopMetaTable__resolveTableFields");
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

    public String saveJoin(String metaTableId, String joinType, String leftEntityId, String rightEntityId,
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

    public String saveTableJoin(String metaTableId, String joinType, String leftTableId, String rightTableId,
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

    public String saveTableEntityJoin(String metaTableId, String joinType, String leftTableId, String rightEntityId,
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

    public String saveExternalTable(String tableName, String querySpace, String buildSqlJson) {
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

    public String saveSqlTable(String moduleId, String tableName, String sourceSql) {
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

    public NopMetaTable getTable(String tableId) {
        return daoProvider.daoFor(NopMetaTable.class).getEntityById(tableId);
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
