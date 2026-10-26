package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.CollectionHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.metadata.dao.entity.NopMetaDict;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityMethod;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M3（plan 2026-10-26-2047-1 Phase 3）：方法执行器端到端 + SQL 沙箱 + roles + 错误面脱敏。
 *
 * <p>覆盖：PHYSICAL/EXTERNAL 实体 invokeMethod 端到端（GraphQL 入口 → 校验链 → SQL
 * 执行 → DTO）；save-time 与 query-time 同源沙箱（save 拒掉的 SQL DAO 直写行 query 时
 * 同样被拒——接线验证）；沙箱拒绝族逐类一条；roles 拒绝与无登录态放行；错误响应不含
 * SQL 全文。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaEntityMethodInvoke extends JunitBaseTestCase {

    private static final String ERR_PREFIX = "nop.err.metadata.";

    private static final String EXT_QUERY_SPACE = "qs_method_invoke";
    private static final String EXT_DB_URL = "jdbc:h2:mem:" + EXT_QUERY_SPACE + ";DB_CLOSE_DELAY=-1";

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private final Timestamp now = new Timestamp(System.currentTimeMillis());

    private String newHostEntity(String id) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity entity = dao.newEntity();
        entity.setMetaEntityId(id);
        entity.setOrmModelId("orm-m3-test");
        entity.setIsDelta((byte) 0);
        entity.setEntityName("io.nop.metadata.dao.entity.NopMetaDict");
        entity.setTableName("nop_meta_dict");
        entity.setEntityKind("PHYSICAL");
        entity.setMetaModuleId("mod-m3-test");
        entity.setVersion(1L);
        entity.setCreatedBy("autotest");
        entity.setUpdatedBy("autotest");
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        dao.saveEntity(entity);
        return id;
    }

    private void newDict(String dictId, String dictName) {
        IEntityDao<NopMetaDict> dao = daoProvider.daoFor(NopMetaDict.class);
        NopMetaDict dict = dao.newEntity();
        dict.setMetaDictId(dictId);
        dict.setOrmModelId("orm-m3-test");
        dict.setIsDelta((byte) 0);
        dict.setDictName(dictName);
        dict.setVersion(1L);
        dict.setCreatedBy("autotest");
        dict.setUpdatedBy("autotest");
        dict.setCreateTime(now);
        dict.setUpdateTime(now);
        dao.saveEntity(dict);
    }

    /** DAO 直写方法行（绕过 BizModel save 守卫，用于 query-time 兜底与夹具构造）。 */
    private void daoSaveMethod(String methodId, String hostEntityId, String methodName, String implSource,
                               String roles, Byte disabled) {
        IEntityDao<NopMetaEntityMethod> dao = daoProvider.daoFor(NopMetaEntityMethod.class);
        NopMetaEntityMethod m = dao.newEntity();
        m.setMethodId(methodId);
        m.setMetaEntityId(hostEntityId);
        m.setIsDelta((byte) 0);
        m.setMethodName(methodName);
        m.setMethodKind("loader");
        m.setSourceKind("custom");
        m.setImplKind("sql");
        m.setImplSource(implSource);
        m.setReturnType("List<java.lang.String>");
        if (roles != null) m.setRoles(roles);
        if (disabled != null) m.setDisabled(disabled);
        m.setVersion(1L);
        m.setCreatedBy("autotest");
        m.setUpdatedBy("autotest");
        m.setCreateTime(now);
        m.setUpdateTime(now);
        dao.saveEntity(m);
    }

    private GraphQLResponseBean saveMethodViaGql(Map<String, Object> data) {
        return executeWithVars("mutation($data:Map) { NopMetaEntityMethod__save(data:$data) "
                + "{ methodId methodName } }", Map.of("data", data));
    }

    private GraphQLResponseBean invokeMethod(String hostEntityId, String methodName, Map<String, Object> args) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("metaEntityId", hostEntityId);
        vars.put("methodName", methodName);
        vars.put("args", args);
        return executeWithVars("query($metaEntityId:String,$methodName:String,$args:Map) { "
                + "NopMetaEntity__invokeMethod(metaEntityId:$metaEntityId, methodName:$methodName, args:$args) "
                + "{ methodId methodName columns items rowCount } }", vars);
    }

    private String firstErrorCode(GraphQLResponseBean response) {
        assertTrue(response.hasError(), "expected error but got: " + response);
        return response.getErrorCode();
    }

    @Test
    public void testInvokeMethodPhysicalEndToEnd() {
        String hostId = newHostEntity("ent-m3-1");
        newDict("dict-m3-alpha", "alpha");
        newDict("dict-m3-beta", "beta");

        // GraphQL save 走守卫 + 沙箱回接
        Map<String, Object> method = new HashMap<>();
        method.put("methodId", "mtd-m3-1");
        method.put("metaEntityId", hostId);
        method.put("methodName", "findByName");
        method.put("methodKind", "loader");
        method.put("implKind", "sql");
        method.put("implSource", "SELECT DICT_NAME AS NAME FROM nop_meta_dict WHERE DICT_NAME = ?");
        method.put("returnType", "List<java.lang.String>");
        method.put("version", 1);
        assertFalse(saveMethodViaGql(method).hasError(), "method must save");

        // 参数子表：argName 按字典序绑定 ?（单参数场景）
        Map<String, Object> arg = new HashMap<>();
        arg.put("argId", "arg-m3-1");
        arg.put("methodId", "mtd-m3-1");
        arg.put("argName", "dictName");
        arg.put("argType", "java.lang.String");
        arg.put("mandatory", 1);
        arg.put("version", 1);
        assertFalse(executeWithVars("mutation($data:Map) { NopMetaMethodArg__save(data:$data) { argId } }",
                Map.of("data", arg)).hasError(), "arg must save");

        GraphQLResponseBean resp = invokeMethod(hostId, "findByName", Map.of("dictName", "alpha"));
        assertFalse(resp.hasError(), "invokeMethod should not error: "
                + (resp.hasError() ? resp.getErrors() : resp.getData()));
        String data = resp.getData().toString();
        assertTrue(data.contains("NAME"), "columns must come from the SQL projection: " + data);
        assertTrue(data.contains("alpha"), "rows must come from the bound query: " + data);
        assertTrue(data.contains("rowCount=1"), "exactly one row expected: " + data);
    }

    @Test
    public void testInvokeMethodExternalEndToEnd() throws Exception {
        // H2 mem 外部库 + datasource + syncExternalTables（沿 AggregationTestHelper 既有基建）
        try (Connection c = DriverManager.getConnection(EXT_DB_URL, "sa", "");
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE METHOD_T (ORDER_ID INT NOT NULL, NOTE VARCHAR(50))");
            st.execute("INSERT INTO METHOD_T VALUES (1, 'm3-note-a')");
            st.execute("INSERT INTO METHOD_T VALUES (2, 'm3-note-b')");
        }
        // datasource 行沿 helper 同款 DAO 直写（connectionConfig 为 JSON 字符串）
        IEntityDao<io.nop.metadata.dao.entity.NopMetaDataSource> dsDao =
                daoProvider.daoFor(io.nop.metadata.dao.entity.NopMetaDataSource.class);
        io.nop.metadata.dao.entity.NopMetaDataSource ds = dsDao.newEntity();
        ds.setDataSourceId("ds-m3-ext");
        ds.setQuerySpace(EXT_QUERY_SPACE);
        ds.setName("ds-m3-ext");
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig("{\"jdbcUrl\":\"" + EXT_DB_URL + "\",\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}");
        ds.setStatus("ACTIVE");
        ds.setVersion(1L);
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dsDao.saveEntity(ds);

        GraphQLResponseBean sync = executeWithVars(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId:\"ds-m3-ext\", "
                        + "schemaPattern:\"PUBLIC\") { syncedTableCount errors { code message } } }", Map.of());
        assertFalse(sync.hasError(), "sync should not error: " + sync);
        IEntityDao<NopMetaEntity> entityDao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity ext = entityDao.findFirstByQuery(new io.nop.api.core.beans.query.QueryBean()
                .addFilter(io.nop.api.core.beans.FilterBeans.eq(
                        NopMetaEntity.PROP_NAME_tableName, "METHOD_T"))
                .addFilter(io.nop.api.core.beans.FilterBeans.eq("entityKind", "EXTERNAL")));
        assertNotNull(ext, "external entity METHOD_T must be synced");

        // DAO 直写方法（EXTERNAL 实体的方法行同样注册）+ 参数
        daoSaveMethod("mtd-m3-ext", ext.getMetaEntityId(), "findNote",
                "SELECT NOTE FROM METHOD_T WHERE ORDER_ID = ?", null, null);
        daoProvider.daoFor(NopMetaEntityMethod.class);

        GraphQLResponseBean resp = invokeMethod(ext.getMetaEntityId(), "findNote", Map.of("ORDER_ID", 1));
        // 未声明参数 → method-arg-unknown（外部方法未注册参数行）
        assertEquals(ERR_PREFIX + "method-arg-unknown", firstErrorCode(resp));

        // 注册参数后走通全链
        Map<String, Object> arg = new HashMap<>();
        arg.put("argId", "arg-m3-ext");
        arg.put("methodId", "mtd-m3-ext");
        arg.put("argName", "orderId");
        arg.put("argType", "java.lang.Integer");
        arg.put("version", 1);
        assertFalse(executeWithVars("mutation($data:Map) { NopMetaMethodArg__save(data:$data) { argId } }",
                Map.of("data", arg)).hasError(), "ext arg must save");
        // 参数顺序（字典序 orderId）与 ? 占位符绑定；值经 args 传入
        GraphQLResponseBean ok = invokeMethod(ext.getMetaEntityId(), "findNote", Map.of("orderId", 2));
        assertFalse(ok.hasError(), "external invoke should not error: " + ok);
        assertTrue(ok.getData().toString().contains("m3-note-b"),
                "external query must execute against the bound datasource: " + ok.getData());
    }

    @Test
    public void testSandboxRejectionFamilyAtSaveTime() {
        String hostId = newHostEntity("ent-m3-2");
        assertSaveRejected(hostId, "mtdDel", "DELETE FROM nop_meta_dict", "DML");
        assertSaveRejected(hostId, "mtdMulti", "SELECT 1; DROP TABLE nop_meta_dict", "multi-statement");
        assertSaveRejected(hostId, "mtdPgSleep", "SELECT PG_SLEEP(1) FROM nop_meta_dict", "blacklist-function");
        assertSaveRejected(hostId, "mtdWith", "WITH t AS (SELECT 1) SELECT * FROM t", "WITH");
        assertSaveRejected(hostId, "mtdInto", "SELECT DICT_NAME INTO new_t FROM nop_meta_dict", "INTO");
        assertSaveRejected(hostId, "mtdExplain", "EXPLAIN SELECT 1 FROM nop_meta_dict", "select-only");
        assertSaveRejected(hostId, "mtdComment", "SELECT 1 /*! FROM nop_meta_dict */", "executable-comment");
    }

    private void assertSaveRejected(String hostId, String methodName, String implSource, String vector) {
        Map<String, Object> data = new HashMap<>();
        data.put("methodId", "mtd-m3-r-" + methodName);
        data.put("metaEntityId", hostId);
        data.put("methodName", methodName);
        data.put("methodKind", "loader");
        data.put("implKind", "sql");
        data.put("implSource", implSource);
        data.put("returnType", "List<java.lang.String>");
        data.put("version", 1);
        GraphQLResponseBean resp = saveMethodViaGql(data);
        assertEquals(ERR_PREFIX + "method-impl-invalid", firstErrorCode(resp),
                "sandbox must reject vector [" + vector + "]: " + resp);
        // 回滚零残留
        assertEquals(null, daoProvider.daoFor(NopMetaEntityMethod.class)
                        .getEntityById("mtd-m3-r-" + methodName),
                "rejected method must not persist: " + vector);
    }

    /** 接线验证：save-time 拒掉的 SQL，DAO 直写行在 query-time 同样被同一校验器拒绝。 */
    @Test
    public void testQueryTimeRevalidationSameValidator() {
        String hostId = newHostEntity("ent-m3-3");
        // DAO 直写绕过 save 守卫（模拟校验器落地前的存量行）
        daoSaveMethod("mtd-m3-q", hostId, "badMethod",
                "DELETE FROM nop_meta_dict WHERE DICT_NAME = ?", null, null);

        GraphQLResponseBean resp = invokeMethod(hostId, "badMethod", Map.of());
        assertEquals(ERR_PREFIX + "method-sql-unsafe", firstErrorCode(resp),
                "query-time must re-validate with the same validator (defense-in-depth)");
    }

    @Test
    public void testNotFoundDisabledAndArgErrors() {
        String hostId = newHostEntity("ent-m3-4");

        assertEquals(ERR_PREFIX + "method-not-found", firstErrorCode(invokeMethod(hostId, "missing", Map.of())));

        daoSaveMethod("mtd-m3-off", hostId, "offMethod", "SELECT 1 FROM nop_meta_dict", null, (byte) 1);
        assertEquals(ERR_PREFIX + "method-disabled", firstErrorCode(invokeMethod(hostId, "offMethod", Map.of())));

        daoSaveMethod("mtd-m3-arg", hostId, "argMethod", "SELECT 1 FROM nop_meta_dict", null, null);
        Map<String, Object> arg = new HashMap<>();
        arg.put("argId", "arg-m3-4");
        arg.put("methodId", "mtd-m3-arg");
        arg.put("argName", "requiredArg");
        arg.put("argType", "java.lang.String");
        arg.put("mandatory", 1);
        arg.put("version", 1);
        assertFalse(executeWithVars("mutation($data:Map) { NopMetaMethodArg__save(data:$data) { argId } }",
                Map.of("data", arg)).hasError());

        // mandatory 缺失
        assertEquals(ERR_PREFIX + "method-arg-missing",
                firstErrorCode(invokeMethod(hostId, "argMethod", Map.of())));
        // 未知参数（mandatory 已供值，仅多传未知键）
        assertEquals(ERR_PREFIX + "method-arg-unknown",
                firstErrorCode(invokeMethod(hostId, "argMethod", Map.of("requiredArg", "v", "wrongArg", "x"))));
    }

    @Test
    public void testRolesEnforcement() {
        String hostId = newHostEntity("ent-m3-5");
        daoSaveMethod("mtd-m3-role", hostId, "roleMethod",
                "SELECT COUNT(*) AS CNT FROM nop_meta_dict", "m3-admin", null);

        // 无登录态（内部调用）放行
        IUserContext.set(null);
        GraphQLResponseBean noLogin = invokeMethod(hostId, "roleMethod", Map.of());
        assertFalse(noLogin.hasError(), "no-login-context invocation must pass: "
                + (noLogin.hasError() ? noLogin.getErrors() : noLogin.getData()));

        // 登录但角色不匹配 → 拒绝
        setUserContext("m3-user", "viewer");
        assertEquals(ERR_PREFIX + "method-role-denied", firstErrorCode(invokeMethod(hostId, "roleMethod", Map.of())));

        // 角色命中 → 放行
        setUserContext("m3-admin", "m3-admin");
        GraphQLResponseBean hit = invokeMethod(hostId, "roleMethod", Map.of());
        assertFalse(hit.hasError(), "role-hit invocation must pass: "
                + (hit.hasError() ? hit.getErrors() : hit.getData()));
        IUserContext.set(null);
    }

    @Test
    public void testErrorSurfaceDoesNotLeakSql() {
        String hostId = newHostEntity("ent-m3-6");
        // 执行期失败的 SQL（表不存在）；SQL 文本含独有标记
        daoSaveMethod("mtd-m3-leak", hostId, "leakMethod",
                "SELECT * FROM m3_table_that_does_not_exist", null, null);
        GraphQLResponseBean resp = invokeMethod(hostId, "leakMethod", Map.of());
        assertEquals(ERR_PREFIX + "method-exec-failed", firstErrorCode(resp));
        String rendered = String.valueOf(resp.getErrors());
        assertFalse(rendered.contains("m3_table_that_does_not_exist"),
                "error surface must not leak SQL/table identifiers: " + rendered);
        assertFalse(rendered.contains("SELECT"), "error surface must not contain SQL text: " + rendered);
    }

    private void setUserContext(String userId, String... roles) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userId);
        userContext.setUserName(userId);
        userContext.setRoles(CollectionHelper.buildImmutableSet(roles));
        IUserContext.set(userContext);
    }

    private GraphQLResponseBean executeWithVars(String query, Map<String, Object> vars) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        request.setVariables(vars);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
