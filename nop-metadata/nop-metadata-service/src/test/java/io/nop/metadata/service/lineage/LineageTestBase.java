package io.nop.metadata.service.lineage;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.biz.INopMetaLineageEdgeBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaLineageEdge;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaOrmModel;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public abstract class LineageTestBase extends JunitBaseTestCase {

    public LineageTestBase() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    protected IGraphQLEngine graphQLEngine;

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected INopMetaLineageEdgeBiz lineageBiz;

    @Inject
    protected io.nop.orm.IOrmTemplate ormTemplate;

    protected IServiceContext svcCtx = new ServiceContextImpl();

    protected String ensureModule(String moduleName) {
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

    protected String saveTable(String moduleId, String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(moduleId);
        t.setTableName(tableName);
        t.setDisplayName(tableName);
        t.setTableType(_NopMetadataCoreConstants.TABLE_TYPE_ENTITY);
        dao.saveEntity(t);
        dao.flushSession();
        return t.getMetaTableId();
    }

    protected String saveSqlTable(String moduleId, String tableName, String sourceSql) {
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

    protected String saveEntityTable(String moduleId, String tableName, String baseEntityId) {
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

    protected String saveEntity(String moduleId, String entityName, String... fieldNames) {
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

    @SuppressWarnings("UnusedReturnValue")
    protected String saveMeasure(String tableId, String measureName, String expression, String aggFunc) {
        IEntityDao<NopMetaTableMeasure> dao = daoProvider.daoFor(NopMetaTableMeasure.class);
        NopMetaTableMeasure m = dao.newEntity();
        m.setMetaTableId(tableId);
        m.setMeasureName(measureName);
        m.setExpression(expression);
        if (aggFunc != null) {
            m.setAggFunc(aggFunc);
        }
        dao.saveEntity(m);
        dao.flushSession();
        return m.getMeasureId();
    }

    @SuppressWarnings("unchecked")
    protected void updateMeasureExpression(String tableId, String measureName, String newExpression) {
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("update NOP_META_TABLE_MEASURE set EXPRESSION=? where META_TABLE_ID=? and MEASURE_NAME=?",
                        newExpression, tableId, measureName)
                .end();
        ormTemplate.executeUpdate(upd);
        ormTemplate.evictAll(NopMetaTableMeasure.class.getName());
    }

    protected long countMeasureParseEdges(String tableId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, tableId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE));
        return dao.countByQuery(q);
    }

    protected String findTableId(String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        NopMetaTable t = dao.findFirstByQuery(q);
        assertNotNull(t, "table " + tableName + " must exist");
        return t.getMetaTableId();
    }

    protected long countEdges(String sourceId, String targetId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        return dao.countByQuery(q);
    }

    protected long countEdgesByTarget(String targetId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        return dao.countByQuery(q);
    }

    protected long countSqlParseEdges(String sourceId, String targetId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        return dao.countByQuery(q);
    }

    protected long countColumnSqlParseEdges(String sourceId, String targetId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        q.addFilter(FilterBeans.notNull(NopMetaLineageEdge.PROP_NAME_sourceColumn));
        return dao.countByQuery(q);
    }

    protected long countTableLevelSqlParseEdges(String sourceId, String targetId) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_lineageSource,
                _NopMetadataCoreConstants.LINEAGE_SOURCE_SQL_PARSE));
        q.addFilter(FilterBeans.isNull(NopMetaLineageEdge.PROP_NAME_sourceColumn));
        return dao.countByQuery(q);
    }

    protected NopMetaLineageEdge findColumnEdge(String sourceId, String targetId, String sourceCol, String targetCol) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, sourceCol));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetColumn, targetCol));
        return dao.findFirstByQuery(q);
    }

    protected NopMetaLineageEdge findEdge(String sourceId, String targetId, String sourceCol, String targetCol) {
        IEntityDao<NopMetaLineageEdge> dao = daoProvider.daoFor(NopMetaLineageEdge.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceTableId, sourceId));
        q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetTableId, targetId));
        if (sourceCol != null) {
            q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_sourceColumn, sourceCol));
        }
        if (targetCol != null) {
            q.addFilter(FilterBeans.eq(NopMetaLineageEdge.PROP_NAME_targetColumn, targetCol));
        }
        return dao.findFirstByQuery(q);
    }

    protected GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
