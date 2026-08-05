package io.nop.metadata.service;

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
import io.nop.metadata.dao.entity.NopMetaDataSource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataSourceBizModel extends JunitBaseTestCase {

    public TestNopMetaDataSourceBizModel() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testConnectionSuccessRealConnect() {
        saveDataSource("ds-h2-ok", "qs_h2_ok", "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"jdbc:h2:mem:meta_test_ok;DB_CLOSE_DELAY=-1\","
                        + "\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-h2-ok\") { connected databaseProductName databaseProductVersion error } }");
        assertFalse(response.hasError(), "success path should not error: " + response);

        String data = String.valueOf(response.getData());
        assertTrue(data.contains("connected=true"),
                "should report connected=true: " + data);
        assertTrue(data.contains("databaseProductName=H2"),
                "should contain real databaseProductName=H2 from live metadata: " + data);
        assertTrue(data.contains("databaseProductVersion="),
                "should contain databaseProductVersion: " + data);
    }

    @Test
    public void testConnectionNotFound() {
        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"__not_exist__\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "non-existent dataSourceId must error (no NPE): " + response);
    }

    @Test
    public void testConnectionDisabledRejected() {
        saveDataSource("ds-disabled", "qs_disabled", "jdbc", "DISABLED",
                "{\"jdbcUrl\":\"jdbc:h2:mem:meta_test_disabled;DB_CLOSE_DELAY=-1\","
                        + "\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-disabled\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "DISABLED datasource must be rejected (no silent pass): " + response);
    }

    @Test
    public void testConnectionNonJdbcThrows() {
        saveDataSource("ds-http", "qs_http", "http", "ACTIVE", "{}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-http\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "non-jdbc datasource must error (NopException ERR_DATASOURCE_TYPE_NOT_SUPPORTED): " + response);
    }

    @Test
    public void testConnectionConfigMissingFastFail() {
        saveDataSource("ds-bad-cfg", "qs_bad_cfg", "jdbc", "ACTIVE",
                "{\"username\":\"sa\",\"password\":\"\"}");

        GraphQLResponseBean response = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-bad-cfg\") { connected databaseProductName error } }");
        assertTrue(response.hasError(),
                "missing required jdbc field (jdbcUrl) must fast-fail: " + response);
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private void saveDataSource(String id, String querySpace, String datasourceType,
                                String status, String connectionConfig) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType(datasourceType);
        ds.setConnectionConfig(connectionConfig);
        ds.setStatus(status);
        ds.setVersion(1L);
        ds.setCreatedBy("autotest");
        ds.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }
}
