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
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityMethod;
import io.nop.metadata.dao.entity.NopMetaMethodArg;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1（plan 2026-10-26-2047-1 Phase 1）：方法注册表端到端 + save 守卫拒绝族。
 *
 * <p>覆盖：
 * <ul>
 *   <li>GraphQL CRUD 端到端（NopMetaEntityMethod__save/__get/__findPage/__delete +
 *       NopMetaMethodArg__save），含 sourceKind 缺省注入 custom；</li>
 *   <li>接线验证：{@code NopMetaEntity.methods} to-many 经 GraphQL 查询返回已登记方法行；</li>
 *   <li>守卫拒绝族逐一验证错误码：reserved/invalid/kind-unknown/impl-kind-unsupported/
 *       imported-forbidden/impl-invalid/arg-duplicate；</li>
 *   <li>update 写入面对称防护 + 幂等自更新不误伤。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaEntityMethodBizModel extends JunitBaseTestCase {

    private static final String ERR_PREFIX = "nop.err.metadata.";

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    private final Timestamp now = new Timestamp(System.currentTimeMillis());

    private String newHostEntity(String id) {
        IEntityDao<NopMetaEntity> dao = daoProvider.daoFor(NopMetaEntity.class);
        NopMetaEntity entity = dao.newEntity();
        entity.setMetaEntityId(id);
        entity.setOrmModelId("orm-m1-test");
        entity.setIsDelta((byte) 0);
        entity.setEntityName("M1TestEntity" + id);
        entity.setEntityKind("PHYSICAL");
        entity.setMetaModuleId("mod-m1-test");
        entity.setVersion(1L);
        entity.setCreatedBy("autotest");
        entity.setUpdatedBy("autotest");
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        dao.saveEntity(entity);
        return id;
    }

    private Map<String, Object> methodData(String methodId, String hostEntityId, String methodName) {
        Map<String, Object> data = new HashMap<>();
        data.put("methodId", methodId);
        data.put("metaEntityId", hostEntityId);
        data.put("methodName", methodName);
        data.put("methodKind", "loader");
        data.put("implKind", "sql");
        data.put("implSource", "SELECT name FROM nop_auth_user WHERE id = ?");
        data.put("returnType", "List<io.nop.auth.dao.entity.NopAuthUser>");
        data.put("version", 1);
        return data;
    }

    private GraphQLResponseBean saveMethod(Map<String, Object> data) {
        return executeWithVars("mutation($data:Map) { NopMetaEntityMethod__save(data:$data) "
                + "{ methodId methodName methodKind sourceKind implKind } }", Map.of("data", data));
    }

    private GraphQLResponseBean updateMethod(Map<String, Object> data) {
        return executeWithVars("mutation($data:Map) { NopMetaEntityMethod__update(data:$data) "
                + "{ methodId methodName } }", Map.of("data", data));
    }

    private String firstErrorCode(GraphQLResponseBean response) {
        assertTrue(response.hasError(), "expected error but got: " + response);
        return response.getErrorCode();
    }

    @Test
    public void testMethodCrudAndArgsEndToEnd() {
        String hostId = newHostEntity("ent-m1-1");

        // save：sourceKind 缺省注入 custom
        GraphQLResponseBean resp = saveMethod(methodData("mtd-m1-1", hostId, "findSubordinates"));
        assertFalse(resp.hasError(), "save method should succeed: " + resp);
        assertTrue(resp.getData().toString().contains("custom"),
                "sourceKind default injection should yield custom: " + resp.getData());

        // get 读回
        resp = execute("query { NopMetaEntityMethod__get(id: \"mtd-m1-1\") "
                + "{ methodId methodName methodKind sourceKind implKind returnType } }");
        assertFalse(resp.hasError(), "get method should not error: " + resp);
        String data = resp.getData().toString();
        assertTrue(data.contains("findSubordinates"), "methodName should round-trip: " + data);
        assertTrue(data.contains("custom"), "sourceKind should be custom: " + data);

        // findPage 标准分页 action 可见可用
        resp = execute("query { NopMetaEntityMethod__findPage(query: { limit: 10 }) { total "
                + "items { methodId methodName } } }");
        assertFalse(resp.hasError(), "findPage should not error: " + resp);
        assertTrue(resp.getData().toString().contains("findSubordinates"),
                "findPage should return the registered method");

        // 方法参数子表 CRUD
        Map<String, Object> argData = new HashMap<>();
        argData.put("argId", "arg-m1-1");
        argData.put("methodId", "mtd-m1-1");
        argData.put("argName", "userId");
        argData.put("argType", "java.lang.String");
        argData.put("mandatory", 1);
        argData.put("version", 1);
        resp = executeWithVars("mutation($data:Map) { NopMetaMethodArg__save(data:$data) "
                + "{ argId argName argType } }", Map.of("data", argData));
        assertFalse(resp.hasError(), "save arg should succeed: " + resp);

        resp = execute("query { NopMetaMethodArg__get(id: \"arg-m1-1\") "
                + "{ argId argName argType mandatory } }");
        assertFalse(resp.hasError(), "get arg should not error: " + resp);
        assertTrue(resp.getData().toString().contains("userId"), "arg should round-trip");

        // 接线验证：NopMetaEntity.methods to-many 经 GraphQL 返回已登记方法行
        resp = execute("query { NopMetaEntity__get(id: \"ent-m1-1\") "
                + "{ metaEntityId methods { methodName } } }");
        assertFalse(resp.hasError(), "entity get with methods should not error: " + resp);
        assertTrue(resp.getData().toString().contains("findSubordinates"),
                "methods to-many should expose registered method rows: " + resp.getData());
    }

    @Test
    public void testGuardReservedMethodName() {
        String hostId = newHostEntity("ent-m1-2");
        GraphQLResponseBean resp = saveMethod(methodData("mtd-m1-2", hostId, "findPage"));
        assertEquals(ERR_PREFIX + "method-name-reserved", firstErrorCode(resp));
        assertNull(daoProvider.daoFor(NopMetaEntityMethod.class).getEntityById("mtd-m1-2"),
                "rejected save must not persist (transaction rollback)");
    }

    @Test
    public void testGuardNonCamelCaseName() {
        String hostId = newHostEntity("ent-m1-3");
        GraphQLResponseBean resp = saveMethod(methodData("mtd-m1-3", hostId, "Find_Subordinates"));
        assertEquals(ERR_PREFIX + "method-name-invalid", firstErrorCode(resp));
    }

    @Test
    public void testGuardUnknownMethodKind() {
        String hostId = newHostEntity("ent-m1-4");
        Map<String, Object> data = methodData("mtd-m1-4", hostId, "findSubordinates");
        data.put("methodKind", "remote-procedure");
        GraphQLResponseBean resp = saveMethod(data);
        // GraphQL 入口的 kind 非法值由 xmeta dict 校验拒绝（平台统一码）；
        // BizModel 守卫（method-kind-unknown）是 DAO 写入行的 defense-in-depth，
        // 由 testGuardKindDefenseInDepth 单独接线验证
        assertEquals("nop.err.biz.invalid-dict-option", firstErrorCode(resp));
        assertNull(daoProvider.daoFor(NopMetaEntityMethod.class).getEntityById("mtd-m1-4"),
                "rejected save must not persist");
    }

    /**
     * Defense-in-depth 接线：DAO 直写（绕过 xmeta 校验，如未来快照导入通道缺陷）落库的
     * 非法 kind 行，经 GraphQL update 触达 BizModel 守卫时以模块错误码显式失败。
     */
    @Test
    public void testGuardKindDefenseInDepth() {
        String hostId = newHostEntity("ent-m1-4b");
        IEntityDao<NopMetaEntityMethod> dao = daoProvider.daoFor(NopMetaEntityMethod.class);
        NopMetaEntityMethod row = dao.newEntity();
        row.setMethodId("mtd-m1-4b");
        row.setMetaEntityId(hostId);
        row.setIsDelta((byte) 0);
        row.setMethodName("findSubordinates");
        row.setMethodKind("remote-procedure");
        row.setSourceKind("custom");
        row.setImplKind("sql");
        row.setImplSource("SELECT 1 FROM nop_auth_user WHERE id = ?");
        row.setReturnType("List<java.lang.String>");
        row.setVersion(1L);
        row.setCreatedBy("autotest");
        row.setUpdatedBy("autotest");
        row.setCreateTime(now);
        row.setUpdateTime(now);
        dao.saveEntity(row);

        Map<String, Object> patch = new HashMap<>();
        patch.put("id", "mtd-m1-4b");
        patch.put("remark", "touch");
        assertEquals(ERR_PREFIX + "method-kind-unknown", firstErrorCode(updateMethod(patch)),
                "DAO-written invalid methodKind must be caught by the BizModel guard on update");

        Map<String, Object> patch2 = new HashMap<>();
        patch2.put("id", "mtd-m1-4b");
        patch2.put("methodKind", "loader");
        patch2.put("remark", "fix-kind");
        GraphQLResponseBean fix = updateMethod(patch2);
        assertFalse(fix.hasError(), "fixing methodKind to a legal value must pass: " + fix);
    }

    @Test
    public void testGuardUnsupportedImplKind() {
        String hostId = newHostEntity("ent-m1-5");
        Map<String, Object> data = methodData("mtd-m1-5", hostId, "findSubordinates");
        data.put("implKind", "xpl");
        GraphQLResponseBean resp = saveMethod(data);
        // 同上：入口面为平台 dict 校验，守卫错误码走 defense-in-depth 路径
        assertEquals("nop.err.biz.invalid-dict-option", firstErrorCode(resp));
    }

    @Test
    public void testGuardImportedSourceKindForbidden() {
        String hostId = newHostEntity("ent-m1-6");
        Map<String, Object> data = methodData("mtd-m1-6", hostId, "findSubordinates");
        data.put("sourceKind", "imported");
        GraphQLResponseBean resp = saveMethod(data);
        assertEquals(ERR_PREFIX + "method-source-imported-forbidden", firstErrorCode(resp));
    }

    @Test
    public void testGuardEmptyImplSource() {
        String hostId = newHostEntity("ent-m1-7");
        Map<String, Object> data = methodData("mtd-m1-7", hostId, "findSubordinates");
        data.put("implSource", "  ");
        GraphQLResponseBean resp = saveMethod(data);
        assertEquals(ERR_PREFIX + "method-impl-invalid", firstErrorCode(resp));
    }

    @Test
    public void testDuplicateMethodNamePerEntityRejectedByUk() {
        String hostId = newHostEntity("ent-m1-8");
        assertFalse(saveMethod(methodData("mtd-m1-8a", hostId, "findSubordinates")).hasError(),
                "first method must save");
        GraphQLResponseBean dup = saveMethod(methodData("mtd-m1-8b", hostId, "findSubordinates"));
        assertTrue(dup.hasError(), "same methodName under same entity must be rejected by UK");
        // 不同实体下同名合法
        String host2 = newHostEntity("ent-m1-8x");
        assertFalse(saveMethod(methodData("mtd-m1-8c", host2, "findSubordinates")).hasError(),
                "same methodName under different entity must save");
    }

    @Test
    public void testUpdateGuardSymmetricAndSelfUpdateAllowed() {
        String hostId = newHostEntity("ent-m1-9");
        assertFalse(saveMethod(methodData("mtd-m1-9", hostId, "findSubordinates")).hasError(),
                "method must save");

        // update 把 methodName 改成保留名 → 守卫拒绝，原值回滚保留
        Map<String, Object> rename = new HashMap<>();
        rename.put("id", "mtd-m1-9");
        rename.put("methodName", "save");
        GraphQLResponseBean resp = updateMethod(rename);
        assertEquals(ERR_PREFIX + "method-name-reserved", firstErrorCode(resp));
        assertEquals("findSubordinates", daoProvider.daoFor(NopMetaEntityMethod.class)
                .getEntityById("mtd-m1-9").getMethodName(), "rejected rename must roll back");

        // 幂等自更新（名不变）→ 不自误伤
        Map<String, Object> selfData = methodData("mtd-m1-9", hostId, "findSubordinates");
        selfData.put("id", "mtd-m1-9");
        assertFalse(updateMethod(selfData).hasError(), "idempotent self-update must not trip the guard");
    }

    @Test
    public void testDuplicateArgNameRejectedAndSelfExclude() {
        String hostId = newHostEntity("ent-m1-10");
        assertFalse(saveMethod(methodData("mtd-m1-10", hostId, "findSubordinates")).hasError(),
                "method must save");

        Map<String, Object> argA = new HashMap<>();
        argA.put("argId", "arg-m1-10a");
        argA.put("methodId", "mtd-m1-10");
        argA.put("argName", "userId");
        argA.put("argType", "java.lang.String");
        argA.put("version", 1);
        assertFalse(executeWithVars("mutation($data:Map) { NopMetaMethodArg__save(data:$data) "
                + "{ argId } }", Map.of("data", argA)).hasError(), "first arg must save");

        Map<String, Object> argB = new HashMap<>(argA);
        argB.put("argId", "arg-m1-10b");
        GraphQLResponseBean dup = executeWithVars("mutation($data:Map) { NopMetaMethodArg__save(data:$data) "
                + "{ argId } }", Map.of("data", argB));
        // argName 方法内唯一由 DB UK_NOP_META_METHOD_ARG_NAME 承载（无 NULL-distinct 豁免面，
        // P2-28 先例：守卫不接管 UK 已覆盖面），平台转译为 entity-already-exists
        assertEquals("nop.err.biz.entity-already-exists", firstErrorCode(dup),
                "duplicate argName must be rejected by DB UK");
        assertNull(daoProvider.daoFor(NopMetaMethodArg.class).getEntityById("arg-m1-10b"),
                "rejected duplicate arg must not persist");

        // 自排除：同 argId 幂等更新不误伤
        Map<String, Object> selfArg = new HashMap<>(argA);
        selfArg.put("displayName", "用户ID");
        assertFalse(executeWithVars("mutation($data:Map) { NopMetaMethodArg__update(data:$data) "
                + "{ argId } }", Map.of("data", selfArg)).hasError(),
                "idempotent self-update must not trip the duplicate guard");

        // 删除路径：method 删除级联清空 args
        GraphQLResponseBean del = execute("mutation { NopMetaEntityMethod__delete(id: \"mtd-m1-10\") }");
        assertFalse(del.hasError(), "delete method should not error: " + del);
        assertNull(daoProvider.daoFor(NopMetaMethodArg.class).getEntityById("arg-m1-10a"),
                "args must cascade-delete with the method");
    }

    private GraphQLResponseBean execute(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }

    private GraphQLResponseBean executeWithVars(String query, Map<String, Object> vars) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        request.setVariables(vars);
        IGraphQLExecutionContext context = graphQLEngine.newGraphQLContext(request);
        return graphQLEngine.executeGraphQL(context);
    }
}
