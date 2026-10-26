package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaDict;
import io.nop.metadata.dao.entity.NopMetaEntity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M4（plan 2026-10-26-2047-1 Phase 4）：标准 CRUD 标签守卫 + 目录合成只读入口端到端。
 *
 * <p>覆盖：标签/entityKind 匹配守卫（std-crud 仅 PHYSICAL、std-crud-ro 仅 SQL_VIEW/EXTERNAL，
 * 显式拒绝不静默降级）；EXTERNAL 实体 findEntityPage 端到端（分页/过滤/排序 + total）；
 * 无标签实体显式拒绝（entity-std-crud-not-declared）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaEntityStdCrudTag extends JunitBaseTestCase {

    private static final String ERR_PREFIX = "nop.err.metadata.";

    private static final String EXT_QUERY_SPACE = "qs_std_crud";
    private static final String EXT_DB_URL = "jdbc:h2:mem:" + EXT_QUERY_SPACE + ";DB_CLOSE_DELAY=-1";

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private final Timestamp now = new Timestamp(System.currentTimeMillis());

    private GraphQLResponseBean executeWithVars(String query, Map<String, Object> vars) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        request.setVariables(vars);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private String firstErrorCode(GraphQLResponseBean response) {
        assertTrue(response.hasError(), "expected error but got: " + response);
        return response.getErrorCode();
    }

    /** GraphQL __save 走关系一致性校验：ormModelId/metaModuleId 必须指向真实父行（DAO 直写不触发）。 */
    private void ensureParents() {
        IEntityDao<io.nop.metadata.dao.entity.NopMetaModule> moduleDao =
                daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaModule.class);
        if (moduleDao.getEntityById("mod-m4-test") == null) {
            io.nop.metadata.dao.entity.NopMetaModule module = moduleDao.newEntity();
            module.setMetaModuleId("mod-m4-test");
            module.setModuleId("mod-m4-test");
            module.setModuleName("m4-test-module");
            module.setModuleVersion(1L);
            module.setStatus("drafting");
            module.setVersion(1L);
            module.setCreatedBy("autotest");
            module.setUpdatedBy("autotest");
            module.setCreateTime(now);
            module.setUpdateTime(now);
            moduleDao.saveEntity(module);
        }
        IEntityDao<io.nop.metadata.dao.entity.NopMetaOrmModel> ormDao =
                daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaOrmModel.class);
        if (ormDao.getEntityById("orm-m4-test") == null) {
            io.nop.metadata.dao.entity.NopMetaOrmModel orm = ormDao.newEntity();
            orm.setOrmModelId("orm-m4-test");
            orm.setMetaModuleId("mod-m4-test");
            orm.setIsDelta((byte) 0);
            orm.setModelName("m4-test-model");
            orm.setVersion(1L);
            orm.setCreatedBy("autotest");
            orm.setUpdatedBy("autotest");
            orm.setCreateTime(now);
            orm.setUpdateTime(now);
            ormDao.saveEntity(orm);
        }
    }

    private GraphQLResponseBean saveEntity(String id, String entityKind, String tableName, String tagSet) {
        ensureParents();
        String data = "{metaEntityId:\"" + id + "\", ormModelId:\"orm-m4-test\", metaModuleId:\"mod-m4-test\", "
                + "entityName:\"" + id + "_name\", tableName:\"" + tableName + "\", entityKind:\"" + entityKind + "\""
                + (tagSet != null ? ", tagSet:\"" + tagSet + "\"" : "")
                + ", version:1}";
        GraphQLResponseBean resp = executeWithVars("mutation { NopMetaEntity__save(data:" + data + ") "
                + "{ metaEntityId entityKind tagSet } }", Map.of());
        if (resp.hasError()) {
            System.out.println("[StdCrudTagTest] save " + id + " error: " + resp.getErrors());
        }
        return resp;
    }

    @Test
    public void testTagGuardFamily() {
        // PHYSICAL + std-crud → 合法
        assertFalse(saveEntity("ent-m4-phys", "PHYSICAL", "nop_meta_dict", "std-crud").hasError(),
                "PHYSICAL with std-crud must save");
        // PHYSICAL + std-crud-ro → 显式拒绝
        assertEquals(ERR_PREFIX + "entity-tag-std-crud-ro-entity-kind",
                firstErrorCode(saveEntity("ent-m4-p-ro", "PHYSICAL", "nop_meta_dict", "std-crud-ro")));
        assertNull(daoProvider.daoFor(NopMetaEntity.class).getEntityById("ent-m4-p-ro"),
                "rejected entity must not persist");

        // EXTERNAL + std-crud-ro → 合法（无需真实外部连接，仅登记实体行）
        assertFalse(saveEntity("ent-m4-ext", "EXTERNAL", "m4_t", "std-crud-ro").hasError(),
                "EXTERNAL with std-crud-ro must save");
        // EXTERNAL + std-crud → 显式拒绝
        assertEquals(ERR_PREFIX + "entity-tag-std-crud-physical-only",
                firstErrorCode(saveEntity("ent-m4-e-crud", "EXTERNAL", "m4_t2", "std-crud")));

        // update 写入面对称：PHYSICAL 改 tagSet 为 std-crud-ro → 拒绝，原值回滚
        GraphQLResponseBean upd = executeWithVars(
                "mutation { NopMetaEntity__update(data:{id:\"ent-m4-phys\", tagSet:\"std-crud-ro\", version:1}) "
                        + "{ metaEntityId tagSet } }", Map.of());
        assertEquals(ERR_PREFIX + "entity-tag-std-crud-ro-entity-kind", firstErrorCode(upd));
        assertEquals("std-crud", daoProvider.daoFor(NopMetaEntity.class).getEntityById("ent-m4-phys").getTagSet(),
                "rejected tag change must roll back");
    }

    @Test
    public void testFindEntityPageExternalEndToEnd() throws Exception {
        // 外部库 3 行种子表 + datasource + sync
        try (Connection c = DriverManager.getConnection(EXT_DB_URL, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE STD_CRUD_T (ROW_ID INT NOT NULL, NOTE VARCHAR(50))");
            st.execute("INSERT INTO STD_CRUD_T VALUES (1, 'note-a')");
            st.execute("INSERT INTO STD_CRUD_T VALUES (2, 'note-b')");
            st.execute("INSERT INTO STD_CRUD_T VALUES (3, 'note-c')");
        }
        IEntityDao<NopMetaDataSource> dsDao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dsDao.newEntity();
        ds.setDataSourceId("ds-m4-ext");
        ds.setQuerySpace(EXT_QUERY_SPACE);
        ds.setName("ds-m4-ext");
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig("{\"jdbcUrl\":\"" + EXT_DB_URL + "\",\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}");
        ds.setStatus("ACTIVE");
        ds.setVersion(1L);
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dsDao.saveEntity(ds);
        assertFalse(executeWithVars("mutation { NopMetaDataSource__syncExternalTables(dataSourceId:\"ds-m4-ext\", "
                + "schemaPattern:\"PUBLIC\") { syncedTableCount } }", Map.of()).hasError(), "sync must succeed");

        IEntityDao<NopMetaEntity> entityDao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity ext = entityDao.findFirstByQuery(new io.nop.api.core.beans.query.QueryBean()
                .addFilter(io.nop.api.core.beans.FilterBeans.eq(NopMetaEntity.PROP_NAME_tableName, "STD_CRUD_T"))
                .addFilter(io.nop.api.core.beans.FilterBeans.eq("entityKind", "EXTERNAL")));
        assertNotNull(ext, "external entity must be synced");

        // 声明 std-crud-ro（update 面，守卫放行 EXTERNAL + std-crud-ro）
        assertFalse(executeWithVars("mutation { NopMetaEntity__update(data:{id:\"" + ext.getMetaEntityId()
                + "\", tagSet:\"std-crud-ro\", version:1}) { metaEntityId tagSet } }", Map.of()).hasError(),
                "declaring std-crud-ro on EXTERNAL must pass");

        // findEntityPage：过滤 + 排序 + 分页 + total（RPC 通道，Java 构造 QueryBean/TreeBean）
        io.nop.api.core.beans.TreeBean gt = new io.nop.api.core.beans.TreeBean();
        gt.setTagName("gt");
        gt.attr("name", "ROW_ID");
        gt.attr("value", 1);
        io.nop.api.core.beans.query.QueryBean q = new io.nop.api.core.beans.query.QueryBean();
        q.setLimit(2);
        q.setOffset(0L);
        q.setFilter(gt);
        q.setOrderBy(java.util.Arrays.asList(io.nop.api.core.beans.query.OrderFieldBean.desc("ROW_ID")));

        io.nop.api.core.beans.ApiRequest<?> pageReq = io.nop.api.core.beans.ApiRequest.build(
                Map.of("metaEntityId", ext.getMetaEntityId(), "query", q));
        io.nop.api.core.beans.ApiResponse<?> pageResp = executeRpc("NopMetaEntity__findEntityPage", pageReq);
        assertTrue(pageResp.isOk(), "findEntityPage should be ok: " + pageResp);
        @SuppressWarnings("unchecked")
        Map<String, Object> pageData = (Map<String, Object>) pageResp.getData();
        assertEquals(2L, ((Number) pageData.get("total")).longValue(),
                "total must count filtered rows (ROW_ID>1): " + pageData);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) pageData.get("items");
        assertEquals(2, rows.size(), "limit=2 must cap the page");
        // 排序生效：ROW_ID 降序 → 第一行是 3
        assertEquals(3, ((Number) rows.get(0).get("ROW_ID")).intValue(), "orderBy desc must hold");
        assertEquals(2, ((Number) rows.get(1).get("ROW_ID")).intValue(), "second row must be ROW_ID=2");

        // findEntityList（升序全量）
        io.nop.api.core.beans.query.QueryBean listQ = new io.nop.api.core.beans.query.QueryBean();
        listQ.setOrderBy(java.util.Arrays.asList(io.nop.api.core.beans.query.OrderFieldBean.asc("ROW_ID")));
        io.nop.api.core.beans.ApiResponse<?> listResp = executeRpc("NopMetaEntity__findEntityList",
                io.nop.api.core.beans.ApiRequest.build(Map.of("metaEntityId", ext.getMetaEntityId(), "query", listQ)));
        assertTrue(listResp.isOk(), "findEntityList should be ok: " + listResp);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listRows = (List<Map<String, Object>>) ((Map<String, Object>) listResp.getData()).get("items");
        assertEquals(3, listRows.size(), "findEntityList must return all rows");
        assertEquals(1, ((Number) listRows.get(0).get("ROW_ID")).intValue(), "asc order must hold");

        // getEntity：过滤取单行
        io.nop.api.core.beans.TreeBean eq = new io.nop.api.core.beans.TreeBean();
        eq.setTagName("eq");
        eq.attr("name", "ROW_ID");
        eq.attr("value", 2);
        io.nop.api.core.beans.ApiResponse<?> oneResp = executeRpc("NopMetaEntity__getEntity",
                io.nop.api.core.beans.ApiRequest.build(Map.of("metaEntityId", ext.getMetaEntityId(), "filter", eq)));
        assertTrue(oneResp.isOk(), "getEntity should be ok: " + oneResp);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> oneRows = (List<Map<String, Object>>) ((Map<String, Object>) oneResp.getData()).get("items");
        assertEquals(1, oneRows.size(), "getEntity must return exactly the matching row");
        assertEquals(2, ((Number) oneRows.get(0).get("ROW_ID")).intValue());
    }

    private io.nop.api.core.beans.ApiResponse<?> executeRpc(String action, io.nop.api.core.beans.ApiRequest<?> request) {
        io.nop.graphql.core.engine.IGraphQLEngine engine = graphQLEngine;
        io.nop.graphql.core.IGraphQLExecutionContext ctx =
                engine.newRpcContext(io.nop.graphql.core.ast.GraphQLOperationType.query, action, request);
        @SuppressWarnings("unchecked")
        io.nop.api.core.beans.ApiResponse<?> resp = (io.nop.api.core.beans.ApiResponse<?>) engine.executeRpc(ctx);
        return resp;
    }

    @Test
    public void testNoTagRejected() {
        // PHYSICAL 无标签 → 合成入口显式拒绝（标签是启用开关，不是文档性声明）
        assertFalse(saveEntity("ent-m4-notag", "PHYSICAL", "nop_meta_dict", null).hasError(),
                "entity without tag must save (guard only checks tag/kind mismatch)");
        GraphQLResponseBean resp = executeWithVars(
                "query { NopMetaEntity__findEntityPage(metaEntityId:\"ent-m4-notag\") "
                        + "{ entityKind items } }", Map.of());
        assertEquals(ERR_PREFIX + "entity-std-crud-not-declared", firstErrorCode(resp),
                "composed entries must reject entities without the reserved tag");
        assertEquals(ERR_PREFIX + "entity-std-crud-not-declared",
                firstErrorCode(executeWithVars(
                        "query { NopMetaEntity__getEntity(metaEntityId:\"ent-m4-notag\") { items } }", Map.of())));
    }

    @Test
    public void testFindEntityPagePhysicalEndToEnd() {
        // PHYSICAL 实体（NopMetaDict）带 std-crud → QueryBean 原生路径
        IEntityDao<NopMetaDict> dictDao = daoProvider.daoFor(NopMetaDict.class);
        for (String name : new String[]{"m4x", "m4y", "m4z"}) {
            NopMetaDict dict = dictDao.newEntity();
            dict.setMetaDictId("dict-m4-" + name);
            dict.setOrmModelId("orm-m4-test");
            dict.setIsDelta((byte) 0);
            dict.setDictName(name);
            dict.setVersion(1L);
            dict.setCreatedBy("autotest");
            dict.setUpdatedBy("autotest");
            dict.setCreateTime(now);
            dict.setUpdateTime(now);
            dictDao.saveEntity(dict);
        }
        // PHYSICAL 取数路径校验 entityName 必须为运行时注册实体：用真实 NopMetaDict
        ensureParents();
        String physData = "{metaEntityId:\"ent-m4-phys-page\", ormModelId:\"orm-m4-test\", "
                + "metaModuleId:\"mod-m4-test\", entityName:\"io.nop.metadata.dao.entity.NopMetaDict\", "
                + "tableName:\"nop_meta_dict\", entityKind:\"PHYSICAL\", tagSet:\"std-crud\", version:1}";
        assertFalse(executeWithVars("mutation { NopMetaEntity__save(data:" + physData + ") "
                + "{ metaEntityId tagSet } }", Map.of()).hasError(), "physical page entity must save");

        GraphQLResponseBean page = executeWithVars(
                "query { NopMetaEntity__findEntityPage(metaEntityId:\"ent-m4-phys-page\", "
                        + "query:{ limit: 2, orderBy: [{name: \"dictName\", desc: false}] }) "
                        + "{ entityKind total items } }", Map.of());
        assertFalse(page.hasError(), "physical findEntityPage should not error: "
                + (page.hasError() ? page.getErrors() : page.getData()));
        String data = page.getData().toString();
        assertTrue(data.contains("dictName=m4x") && data.contains("dictName=m4y"),
                "physical page must return rows via native QueryBean path: " + data);
    }
}
