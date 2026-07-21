package io.nop.metadata.service;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.orm.IOrmTemplate;
import org.junit.jupiter.api.Assertions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TestAggregationHelper {

    protected final IGraphQLEngine graphQLEngine;
    protected final IDaoProvider daoProvider;
    protected final io.nop.metadata.service.entity.NopMetaTableBizModel nopMetaTableBizModel;
    protected final io.nop.metadata.service.entity.NopMetaTableMeasureBizModel nopMetaTableMeasureBizModel;
    protected final IOrmTemplate ormTemplate;

    public TestAggregationHelper(IGraphQLEngine graphQLEngine, IDaoProvider daoProvider,
                                  io.nop.metadata.service.entity.NopMetaTableBizModel nopMetaTableBizModel,
                                  io.nop.metadata.service.entity.NopMetaTableMeasureBizModel nopMetaTableMeasureBizModel,
                                  IOrmTemplate ormTemplate) {
        this.graphQLEngine = graphQLEngine;
        this.daoProvider = daoProvider;
        this.nopMetaTableBizModel = nopMetaTableBizModel;
        this.nopMetaTableMeasureBizModel = nopMetaTableMeasureBizModel;
        this.ormTemplate = ormTemplate;
    }

    // ===== seed helpers =====

    public void seedAggTable(String dbUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE ext_agg (category VARCHAR(20), amount INT, created_at TIMESTAMP)");
            st.execute("INSERT INTO ext_agg VALUES ('A', 10, '2024-01-15 10:00:00')");
            st.execute("INSERT INTO ext_agg VALUES ('A', 20, '2024-01-20 10:00:00')");
            st.execute("INSERT INTO ext_agg VALUES ('B', 30, '2024-02-10 10:00:00')");
        }
    }

    public void seedFactDimTables(String dbUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE ext_fact (cat_id INT, amount INT)");
            st.execute("CREATE TABLE ext_dim (cat_id INT, cat_name VARCHAR(20))");
            st.execute("INSERT INTO ext_fact VALUES (1, 10)");
            st.execute("INSERT INTO ext_fact VALUES (1, 20)");
            st.execute("INSERT INTO ext_fact VALUES (2, 30)");
            st.execute("INSERT INTO ext_dim VALUES (1, 'A')");
            st.execute("INSERT INTO ext_dim VALUES (2, 'B')");
        }
    }

    public void seedArithTable(String dbUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE ext_arith (category VARCHAR(20), val_a INT, val_b INT)");
            st.execute("INSERT INTO ext_arith VALUES ('A', 10, 1)");
            st.execute("INSERT INTO ext_arith VALUES ('A', 20, 5)");
            st.execute("INSERT INTO ext_arith VALUES ('B', 30, 1)");
            st.execute("INSERT INTO ext_arith VALUES ('C', 5, 1)");
        }
    }

    public void seedMixedSameDbTables(String dbUrl) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE NOP_META_MODULE (STATUS VARCHAR(20))");
            st.execute("INSERT INTO NOP_META_MODULE VALUES ('A')");
            st.execute("INSERT INTO NOP_META_MODULE VALUES ('A')");
            st.execute("INSERT INTO NOP_META_MODULE VALUES ('B')");
            st.execute("CREATE TABLE MIXED_DIM (STATUS_VAL VARCHAR(20), CAT_NAME VARCHAR(50))");
            st.execute("INSERT INTO MIXED_DIM VALUES ('A', 'Category A')");
            st.execute("INSERT INTO MIXED_DIM VALUES ('B', 'Category B')");
        }
    }

    public static void seedH2(String dbUrl, String... statements) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    // ===== entity/table helpers =====

    public void importModel() {
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaModule__importOrmModel(path: \"/nop/metadata/orm/app.orm.xml\") { metaModuleId } }");
        graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
    }

    public String findEntityTableId(String tableName) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "entity"));
        NopMetaTable t = dao.findFirstByQuery(q);
        Assertions.assertNotNull(t, "entity table " + tableName + " must exist after import");
        return t.getMetaTableId();
    }

    public String findEntityFieldId(String tableName, String fieldName,
                                     IEntityDao<NopMetaEntityField> fieldDao) {
        IEntityDao<NopMetaEntity> entityDao = daoProvider.daoFor(NopMetaEntity.class);
        QueryBean eq = new QueryBean();
        eq.addFilter(FilterBeans.eq(NopMetaEntity.PROP_NAME_tableName, tableName));
        NopMetaEntity ent = entityDao.findFirstByQuery(eq);
        Assertions.assertNotNull(ent, "entity " + tableName + " must exist");
        QueryBean fq = new QueryBean();
        fq.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, ent.getMetaEntityId()));
        fq.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_fieldName, fieldName));
        NopMetaEntityField f = fieldDao.findFirstByQuery(fq);
        Assertions.assertNotNull(f, "field " + fieldName + " must exist on " + tableName);
        return f.getEntityFieldId();
    }

    public String findEntityFieldId(String tableName, String fieldName) {
        IEntityDao<NopMetaEntityField> fieldDao = daoProvider.daoFor(NopMetaEntityField.class);
        return findEntityFieldId(tableName, fieldName, fieldDao);
    }

    public String prepareExternalTable(String dbUrl, String querySpace, String expectedTable) {
        saveDataSource("ds-" + querySpace, querySpace, "jdbc", "ACTIVE", dbUrl);
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-" + querySpace
                + "\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
        Assertions.assertFalse(resp.hasError(), "sync should not error: " + resp);
        return tableId(expectedTable);
    }

    public String tableId(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        NopMetaTable t = tableDao.findFirstByQuery(q);
        Assertions.assertNotNull(t, "external table " + tableName + " must be synced");
        return t.getMetaTableId();
    }

    public String externalTableId(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        NopMetaTable t = tableDao.findFirstByQuery(q);
        Assertions.assertNotNull(t, "external table " + tableName + " must be synced");
        return t.getMetaTableId();
    }

    // ===== data source helpers =====

    public void saveDataSource(String id, String querySpace, String datasourceType, String status, String dbUrl) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType(datasourceType);
        ds.setConnectionConfig("{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}");
        ds.setStatus(status);
        ds.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    public void saveDataSource(String id, String querySpace, String dbUrl) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig("{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}");
        ds.setStatus("ACTIVE");
        ds.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    public void syncExternalTables(String dataSourceId) {
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"" + dataSourceId
                + "\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message detail } } }");
        io.nop.api.core.beans.graphql.GraphQLResponseBean resp =
                graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
        Assertions.assertFalse(resp.hasError(), "sync should not error: " + resp);
    }

    // ===== measure/dimension/filter helpers =====

    public void createMeasure(String tableId, String name, String entityFieldId, String aggFunc, String expression) {
        IEntityDao<NopMetaTableMeasure> dao = daoProvider.daoFor(NopMetaTableMeasure.class);
        NopMetaTableMeasure m = dao.newEntity();
        m.setMetaTableId(tableId);
        m.setMeasureName(name);
        m.setEntityFieldId(entityFieldId);
        m.setAggFunc(aggFunc);
        if (expression != null) {
            m.setExpression(expression);
        }
        m.setVersion(1L);
        dao.saveEntity(m);
    }

    public void createDimension(String tableId, String name, String entityFieldId, String dimensionType,
                                 String granularity) {
        IEntityDao<NopMetaTableDimension> dao = daoProvider.daoFor(NopMetaTableDimension.class);
        NopMetaTableDimension d = dao.newEntity();
        d.setMetaTableId(tableId);
        d.setDimensionName(name);
        d.setEntityFieldId(entityFieldId);
        d.setDimensionType(dimensionType);
        if (granularity != null) {
            d.setGranularity(granularity);
        }
        d.setVersion(1L);
        dao.saveEntity(d);
    }

    public void createDefaultFilter(String tableId, String name, TreeBean definition) {
        IEntityDao<NopMetaTableFilter> dao = daoProvider.daoFor(NopMetaTableFilter.class);
        NopMetaTableFilter f = dao.newEntity();
        f.setMetaTableId(tableId);
        f.setFilterName(name);
        f.setDefinition(JsonTool.stringify(definition));
        f.setIsDefault((byte) 1);
        f.setVersion(1L);
        dao.saveEntity(f);
    }

    public void createMeasureWithSide(String tableId, String name, String entityFieldId, String aggFunc, String side) {
        IEntityDao<NopMetaTableMeasure> dao = daoProvider.daoFor(NopMetaTableMeasure.class);
        NopMetaTableMeasure m = dao.newEntity();
        m.setMetaTableId(tableId);
        m.setMeasureName(name);
        m.setEntityFieldId(entityFieldId);
        m.setAggFunc(aggFunc);
        if (side != null) {
            m.setSide(side);
        }
        m.setVersion(1L);
        dao.saveEntity(m);
    }

    public void createDimensionWithSide(String tableId, String name, String entityFieldId, String dimensionType,
                                         String granularity, String side) {
        IEntityDao<NopMetaTableDimension> dao = daoProvider.daoFor(NopMetaTableDimension.class);
        NopMetaTableDimension d = dao.newEntity();
        d.setMetaTableId(tableId);
        d.setDimensionName(name);
        d.setEntityFieldId(entityFieldId);
        d.setDimensionType(dimensionType);
        if (granularity != null) {
            d.setGranularity(granularity);
        }
        if (side != null) {
            d.setSide(side);
        }
        d.setVersion(1L);
        dao.saveEntity(d);
    }

    public void createMeasureWithSideAndExpression(String tableId, String name, String entityFieldId,
                                                     String aggFunc, String side, String expression) {
        IEntityDao<NopMetaTableMeasure> dao = daoProvider.daoFor(NopMetaTableMeasure.class);
        NopMetaTableMeasure m = dao.newEntity();
        m.setMetaTableId(tableId);
        m.setMeasureName(name);
        m.setEntityFieldId(entityFieldId);
        m.setAggFunc(aggFunc);
        if (side != null) {
            m.setSide(side);
        }
        if (expression != null) {
            m.setExpression(expression);
        }
        m.setVersion(1L);
        dao.saveEntity(m);
    }

    // ===== join helpers =====

    public NopMetaEntity findMetaEntityByTable(String tableName) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntity.PROP_NAME_tableName, tableName));
        NopMetaEntity e = dao.findFirstByQuery(q);
        Assertions.assertNotNull(e, "NopMetaEntity for table " + tableName + " must exist after import");
        return e;
    }

    public String createJoin(String metaTableId, String joinType, String leftEntityId, String rightEntityId,
                              String leftField, String rightField, String alias) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = dao.newEntity();
        join.setMetaTableId(metaTableId);
        join.setJoinType(joinType);
        join.setLeftEntityId(leftEntityId);
        join.setRightEntityId(rightEntityId);
        join.setLeftField(leftField);
        join.setRightField(rightField);
        join.setAlias(alias);
        join.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        join.setCreatedBy("autotest");
        join.setCreateTime(now);
        dao.saveEntity(join);
        return join.getJoinId();
    }

    public String createMixedJoin(String metaTableId, String joinType, String leftEntityId, String rightTableId,
                                   String leftField, String rightField, String alias) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = dao.newEntity();
        join.setMetaTableId(metaTableId);
        join.setJoinType(joinType);
        join.setLeftEntityId(leftEntityId);
        join.setRightTableId(rightTableId);
        join.setLeftField(leftField);
        join.setRightField(rightField);
        join.setAlias(alias);
        join.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        join.setCreatedBy("autotest");
        join.setCreateTime(now);
        dao.saveEntity(join);
        return join.getJoinId();
    }

    public String createTableTableJoin(String metaTableId, String joinType, String leftTableId, String rightTableId,
                                        String leftField, String rightField, String alias) {
        IEntityDao<NopMetaTableJoin> dao = daoProvider.daoFor(NopMetaTableJoin.class);
        NopMetaTableJoin join = dao.newEntity();
        join.setMetaTableId(metaTableId);
        join.setJoinType(joinType);
        join.setLeftTableId(leftTableId);
        join.setRightTableId(rightTableId);
        join.setLeftField(leftField);
        join.setRightField(rightField);
        join.setAlias(alias);
        join.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        join.setCreatedBy("autotest");
        join.setCreateTime(now);
        dao.saveEntity(join);
        return join.getJoinId();
    }

    // ===== query helpers =====

    public boolean queryAggregationHasError(String tableId, List<String> measures, List<String> dims) {
        try {
            nopMetaTableBizModel.queryAggregation(tableId, measures, dims, null, null, null, null, null, null, null, null);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public boolean queryAggregationJoinHasError(String tableId, String joinId) {
        return queryAggregationJoinHasError(tableId, "__any_measure__", "__any_dim__", joinId);
    }

    public boolean queryAggregationJoinHasError(String tableId, String measureName, String dimName, String joinId) {
        try {
            nopMetaTableBizModel.queryAggregation(tableId,
                    java.util.Arrays.asList(measureName), java.util.Arrays.asList(dimName),
                    null, joinId, null, null, null, null, null, null);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    public long countRows(String sql) {
        io.nop.core.lang.sql.SQL q = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true).sql(sql).end();
        return ormTemplate.executeQuery(q, null, ds -> {
            for (io.nop.dataset.IDataRow row : ds) {
                return ((Number) row.getObject(0)).longValue();
            }
            return 0L;
        });
    }

    public List<String> queryDistinctColumnValues(String sql, String columnName) {
        io.nop.core.lang.sql.SQL q = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true).sql(sql).end();
        return ormTemplate.executeQuery(q, null, ds -> {
            List<String> values = new ArrayList<>();
            for (io.nop.dataset.IDataRow row : ds) {
                Object v = row.getObject(0);
                if (v != null) {
                    values.add(String.valueOf(v));
                }
            }
            return values;
        });
    }

    // ===== entity time spread helpers =====

    public void spreadEntityCreateTimeAcrossTwoMonths() {
        Timestamp jan = Timestamp.valueOf("2024-01-15 10:00:00");
        Timestamp feb = Timestamp.valueOf("2024-02-15 10:00:00");
        List<String> allIds = queryAllEntityIds();
        Assertions.assertNotNull(allIds);
        Assertions.assertFalse(allIds.isEmpty(), "must have at least 1 entity row for time spread");
        int mid = allIds.size() / 2;
        for (int i = 0; i < allIds.size(); i++) {
            String id = allIds.get(i);
            Timestamp ts = (i < mid) ? jan : feb;
            io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                    .sql("update NOP_META_ENTITY set CREATE_TIME=? where META_ENTITY_ID=?", ts, id).end();
            ormTemplate.executeUpdate(upd);
        }
        ormTemplate.evictAll(NopMetaEntity.class.getName());
    }

    public List<String> queryAllEntityIds() {
        io.nop.core.lang.sql.SQL q = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("select META_ENTITY_ID from NOP_META_ENTITY").end();
        return ormTemplate.executeQuery(q, null, ds -> {
            List<String> ids = new ArrayList<>();
            for (io.nop.dataset.IDataRow row : ds) {
                ids.add(String.valueOf(row.getObject(0)));
            }
            return ids;
        });
    }

    public void resetEntityCreateTime() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("update NOP_META_ENTITY set CREATE_TIME=?", now).end();
        ormTemplate.executeUpdate(upd);
        ormTemplate.evictAll(NopMetaEntity.class.getName());
    }

    public List<Object[]> queryEntityCreateTimeRows() {
        io.nop.core.lang.sql.SQL q = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("select ENTITY_NAME, CREATE_TIME from NOP_META_ENTITY").end();
        return ormTemplate.executeQuery(q, null, ds -> {
            List<Object[]> rows = new ArrayList<>();
            for (io.nop.dataset.IDataRow row : ds) {
                rows.add(new Object[]{row.getObject(0), row.getObject(1)});
            }
            return rows;
        });
    }

    // ===== misc helpers =====

    public void updateQuerySpaceSql(String metaEntityId, String querySpace) {
        io.nop.core.lang.sql.SQL upd = io.nop.core.lang.sql.SQL.begin().allowUnderscoreName(true)
                .sql("update NOP_META_ENTITY set QUERY_SPACE=? where META_ENTITY_ID=?",
                        querySpace == null ? "" : querySpace, metaEntityId)
                .end();
        ormTemplate.executeUpdate(upd);
        ormTemplate.evictAll(NopMetaEntity.class.getName());
    }

    public String saveSqlTableManual(String sourceSql, String querySpace) {
        IEntityDao<NopMetaTable> dao = daoProvider.daoFor(NopMetaTable.class);
        NopMetaTable t = dao.newEntity();
        t.setMetaModuleId(ensureTestModuleId());
        t.setTableName("SQL_AGG_" + System.nanoTime());
        t.setDisplayName("sql-agg-endpoint");
        t.setTableType("sql");
        t.setQuerySpace(querySpace);
        t.setSourceSql(sourceSql);
        t.setVersion(1L);
        dao.saveEntity(t);
        return t.getMetaTableId();
    }

    public String ensureTestModuleId() {
        IEntityDao<NopMetaModule> dao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = dao.newEntity();
        m.setModuleId("nop/test-agg-" + System.nanoTime());
        m.setModuleName("test-agg");
        m.setDisplayName("test-agg");
        m.setModuleVersion(1L);
        m.setStatus("RELEASED");
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        dao.saveEntity(m);
        return m.getMetaModuleId();
    }

    public io.nop.api.core.beans.graphql.GraphQLResponseBean execSaveMutation(String tableId,
                                                                                String measureName,
                                                                                String expression) {
        String exprJson = JsonTool.stringify(expression);
        String dataJson = "{metaTableId:\"" + tableId + "\",measureName:\"" + measureName
                + "\",aggFunc:\"sum\",entityFieldId:null,expression:" + exprJson + ",version:1}";
        io.nop.api.core.beans.graphql.GraphQLRequestBean request = new io.nop.api.core.beans.graphql.GraphQLRequestBean();
        request.setQuery("mutation { NopMetaTableMeasure__save(data: " + dataJson + ") { measureName expression } }");
        return graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(request));
    }

    // ===== static helpers =====

    public static Map<String, Object> findRow(List<Map<String, Object>> items, String key, String val) {
        for (Map<String, Object> row : items) {
            Object v = getIgnoreCase(row, key);
            if (v != null && val.equals(String.valueOf(v))) {
                return row;
            }
        }
        return null;
    }

    public static Object getIgnoreCase(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static int toInt(Object v) {
        if (v == null) {
            return 0;
        }
        return ((Number) v).intValue();
    }

    public static long toLong(Object v) {
        if (v == null) {
            return 0;
        }
        return ((Number) v).longValue();
    }

    public static TreeBean havingArithmeticLeaf(String op, String expr, Object value) {
        TreeBean leaf = new TreeBean(op);
        leaf.setAttr(io.nop.metadata.service.query.MetaAggregationExecutor.HAVING_EXPR_ATTR, expr);
        leaf.setAttr(io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE, value);
        return leaf;
    }

    public static String normalizeBucketKey(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 19 ? s.substring(0, 19) : s;
    }

    public static Map<String, Long> bucketsToCountMap(List<Map<String, Object>> items, String dimKey, String cntKey) {
        Map<String, Long> map = new TreeMap<>();
        for (Map<String, Object> row : items) {
            Object bucket = getIgnoreCase(row, dimKey);
            Object cnt = getIgnoreCase(row, cntKey);
            Assertions.assertNotNull(bucket, "bucket column " + dimKey + " must be present: " + row);
            Assertions.assertNotNull(cnt, "count column " + cntKey + " must be present: " + row);
            String key = normalizeBucketKey(String.valueOf(bucket));
            map.merge(key, toLong(cnt), Long::sum);
        }
        return map;
    }
}
