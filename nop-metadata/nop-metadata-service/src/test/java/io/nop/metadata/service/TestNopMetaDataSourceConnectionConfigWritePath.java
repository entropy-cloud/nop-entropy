package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-1 回归测试（plan 2026-08-15-1913-2 Phase 1）：connectionConfig 受控写路径恢复。
 *
 * <p>契约语义：
 * <ul>
 *   <li>写路径：{@code NopMetaDataSource__save} / {@code __update} 可写入 connectionConfig
 *       （xmeta insertable/updatable 恢复为 true，经 ObjMetaBasedValidator isInsertable/isUpdatable 过滤器）</li>
 *   <li>读脱敏不回退：published=false 保持，GraphQL 查询输出类型（get/findPage）不含 connectionConfig 字段</li>
 *   <li>update 语义：提交体不含 connectionConfig key 时不触碰已存配置（OrmEntityCopier 只拷贝存在 key）；
 *       显式提交该 key 才更新（凭据轮换路径）</li>
 *   <li>端到端可达（Anti-Hollow）：save 写入的 connectionConfig 被 testConnection 真实消费（H2 连通）</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaDataSourceConnectionConfigWritePath extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testSaveWritesConnectionConfigAndTestConnectionConsumesIt() {
        String config = h2Config("meta_write_e2e");
        GraphQLResponseBean saveResp = execute("mutation { NopMetaDataSource__save(data: {"
                + " dataSourceId: \"ds-write-e2e\", querySpace: \"qs_write\", name: \"ds-write-e2e\","
                + " displayName: \"Write Path E2E\", datasourceType: \"jdbc\", status: \"ACTIVE\","
                + " connectionConfig: " + gqlString(config)
                + " }) { dataSourceId } }");
        assertFalse(saveResp.hasError(), "save must succeed: " + saveResp);

        // 写路径被证明：经公开 API save 的数据源携带 connectionConfig（修复前被 insertable=false 静默丢弃）
        NopMetaDataSource ds = loadDataSource("ds-write-e2e");
        assertEquals(config, ds.getConnectionConfig(),
                "connectionConfig passed to __save must be persisted (was silently dropped before P1-1)");

        // 端到端：testConnection 消费 save 写入的配置（真实 H2 建连）
        GraphQLResponseBean testResp = execute(
                "mutation { NopMetaDataSource__testConnection(dataSourceId: \"ds-write-e2e\")"
                        + " { connected databaseProductName } }");
        assertFalse(testResp.hasError(), "testConnection on save product must not error: " + testResp);
        String data = String.valueOf(testResp.getData());
        assertTrue(data.contains("connected=true"),
                "testConnection must consume the config written by save: " + data);
        assertTrue(data.contains("databaseProductName=H2"),
                "real H2 metadata expected (end-to-end path): " + data);
    }

    @Test
    public void testUpdateRotatesConnectionConfigWhenKeyPresent() {
        saveViaDao("ds-rotate", h2Config("meta_rotate_a"));

        String rotated = h2Config("meta_rotate_b");
        GraphQLResponseBean resp = execute("mutation { NopMetaDataSource__update(data: {"
                + " id: \"ds-rotate\", displayName: \"Rotated\","
                + " connectionConfig: " + gqlString(rotated)
                + " }) { dataSourceId } }");
        assertFalse(resp.hasError(), "update with connectionConfig key must succeed (credential rotation): " + resp);

        NopMetaDataSource ds = loadDataSource("ds-rotate");
        assertEquals(rotated, ds.getConnectionConfig(),
                "explicitly submitted connectionConfig key must rotate stored config (updatable=true)");
        assertEquals("Rotated", ds.getDisplayName());
    }

    @Test
    public void testUpdateWithoutConfigKeyKeepsStoredConfig() {
        String config = h2Config("meta_keep_cfg");
        saveViaDao("ds-keep-cfg", config);

        GraphQLResponseBean resp = execute("mutation { NopMetaDataSource__update(data: {"
                + " id: \"ds-keep-cfg\", displayName: \"Kept\""
                + " }) { dataSourceId } }");
        assertFalse(resp.hasError(), "update without connectionConfig key must succeed: " + resp);

        NopMetaDataSource ds = loadDataSource("ds-keep-cfg");
        assertEquals(config, ds.getConnectionConfig(),
                "update whose submitted data lacks the key must not clear stored config "
                        + "(OrmEntityCopier copies only present keys; edit form never submits the field)");
        assertEquals("Kept", ds.getDisplayName());
    }

    /**
     * 读脱敏不回退：published=false 把 connectionConfig 从 GraphQL 查询输出类型整体移除
     * （ObjMetaToGraphQLDefinition 跳过 published=false prop）——findPage/get 输出均不可见。
     */
    @Test
    public void testGraphQlQueryOutputExcludesConnectionConfig() {
        GraphQLFieldDefinition getOp = graphQLEngine.getOperationDefinition(
                GraphQLOperationType.query, "NopMetaDataSource__get");
        assertNotNull(getOp, "NopMetaDataSource__get must exist in schema");
        assertTrue(getOp.getType() instanceof GraphQLNamedType, "get result must be named type");

        String typeName = ((GraphQLNamedType) getOp.getType()).getName();
        GraphQLTypeDefinition typeDef = graphQLEngine.getTypeDefinition(typeName);
        assertNotNull(typeDef, "entity output type must be defined: " + typeName);
        assertTrue(typeDef instanceof GraphQLObjectDefinition, "entity output type must be object type");

        GraphQLFieldDefinition field = findField((GraphQLObjectDefinition) typeDef, "connectionConfig");
        assertNull(field, "connectionConfig must NOT appear in GraphQL query output type (read masking, published=false)");
        assertNotNull(findField((GraphQLObjectDefinition) typeDef, "dataSourceId"),
                "sanity: normal field dataSourceId is present");

        GraphQLFieldDefinition pageOp = graphQLEngine.getOperationDefinition(
                GraphQLOperationType.query, "NopMetaDataSource__findPage");
        assertNotNull(pageOp, "NopMetaDataSource__findPage must exist in schema");
    }

    private static GraphQLFieldDefinition findField(GraphQLObjectDefinition objDef, String name) {
        if (objDef.getFields() != null) {
            for (GraphQLFieldDefinition field : objDef.getFields()) {
                if (name.equals(field.getName())) {
                    return field;
                }
            }
        }
        return null;
    }

    private NopMetaDataSource loadDataSource(String id) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        return dao.getEntityById(id);
    }

    private void saveViaDao(String id, String connectionConfig) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace("qs_write");
        ds.setName(id);
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig(connectionConfig);
        ds.setStatus("ACTIVE");
        ds.setVersion(1L);
        ds.setCreatedBy("autotest");
        ds.setUpdatedBy("autotest");
        java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    private static String h2Config(String memDbName) {
        return "{\"jdbcUrl\":\"jdbc:h2:mem:" + memDbName + ";DB_CLOSE_DELAY=-1\","
                + "\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}";
    }

    /** GraphQL 字符串字面量转义（JSON 配置内嵌双引号）。 */
    private static String gqlString(String json) {
        return "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
