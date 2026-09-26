package io.nop.task.app;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.task.dao.entity.NopTaskInstance;
import io.nop.xlang.xmeta.IObjMeta;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 364 Phase 1 [维度04-01]：nop-task 实体 CRUD 写保护回归。
 *
 * <p>机制（ai-dev/design/crud/nop-task-entity-write-protection-design.md）：xmeta delta
 * updatable/insertable=false 经 ObjMetaBasedValidator 在 biz 写入口过滤引擎独占列；
 * 引擎自身的 dao 直写路径不受影响；copyForNew 因 cloneInstance 会整行克隆引擎状态列而被禁用。
 *
 * <p>Anti-Hollow：update/save 经真实 GraphQL mutation 入口驱动（非直调 Java 方法），
 * 断言读回数据库中的实际行值，排除"过滤逻辑存在但未接线"的空壳风险。
 */
public class TestTaskAppCrudWriteProtection extends JunitBaseTestCase {

    private static final Integer STATUS_RUNNING = 10;
    private static final Integer STATUS_COMPLETED = 40;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    io.nop.biz.api.IBizObjectManager bizObjectManager;

    /**
     * 引擎创建的运行中实例，CRUD update 携带 status=COMPLETED 必须被丢弃；
     * 同一请求中未被保护的 remark 正常生效（证明过滤是字段级而非整体拒绝）。
     */
    @Test
    public void testUpdate_cannotOverwriteEngineStatus() {
        NopTaskInstance instance = insertInstanceViaDao("upd");

        Map<String, Object> data = new HashMap<>();
        data.put("id", instance.getTaskInstanceId());
        data.put("status", STATUS_COMPLETED);
        data.put("remark", "ops-note");
        ApiResponse<?> response = executeMutation("NopTaskInstance__update", Map.of("data", data));
        assertEquals(0, response.getStatus(), "update should succeed with unprotected fields, got: " + response);

        NopTaskInstance reloaded = loadInstance(instance.getTaskInstanceId());
        assertEquals(STATUS_RUNNING, reloaded.getStatus(),
                "engine-owned status must be ignored by CRUD update (04-01)");
        assertEquals("ops-note", reloaded.getRemark(),
                "unprotected remark must still be updatable (field-level, not whole-request rejection)");
    }

    /**
     * 手工经 CRUD 创建实例是伪造终态的入口：status 被 insert 过滤后由 DB NOT NULL 约束响亮失败。
     */
    @Test
    public void testCreate_statusInsertableFalse_failsLoudly() {
        Map<String, Object> data = new HashMap<>();
        data.put("taskInstanceId", "inst-fake-create");
        data.put("taskName", "fake-task");
        data.put("taskVersion", 1L);
        data.put("taskGroup", "fake-group");
        data.put("priority", 100);
        data.put("status", STATUS_COMPLETED);
        ApiResponse<?> response = executeMutation("NopTaskInstance__save", Map.of("data", data));
        assertNotEquals(0, response.getStatus(),
                "hand-create of engine-owned instance rows must fail loudly (status filtered => DB NOT NULL), got: "
                        + response);
    }

    /**
     * 引擎写路径（OrmEntityDao 直写，DaoTaskStateStore 同款）不受 xmeta 过滤影响。
     */
    @Test
    public void testEngineDaoWritePath_unaffected() {
        NopTaskInstance instance = insertInstanceViaDao("eng");
        instance.setStatus(STATUS_COMPLETED);
        daoProvider.daoFor(NopTaskInstance.class).updateEntityDirectly(instance);

        NopTaskInstance reloaded = loadInstance(instance.getTaskInstanceId());
        assertEquals(STATUS_COMPLETED, reloaded.getStatus(),
                "engine dao write path must not be affected by xmeta write protection");
    }

    /**
     * copyForNew 的 cloneInstance 会整行克隆引擎状态列绕过 insert 锁，已通过
     * disabledActions 禁用：GraphQL 操作不再存在，调用必须报错。
     */
    @Test
    public void testCopyForNew_disabled() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", insertInstanceViaDao("cpy").getTaskInstanceId());
        ApiResponse<?> response = executeMutation("NopTaskInstance__copyForNew", Map.of("data", data));
        assertNotEquals(0, response.getStatus(),
                "copyForNew must be disabled for engine-owned entities, got: " + response);
    }

    /**
     * xmeta delta merge 语义直接断言：受保护列 updatable=false（引擎独占），
     * 管理面列（remark/priority）保持 true。
     */
    @Test
    public void testXmetaDeltaMerge_protectedPropsNotUpdatable() {
        IObjMeta objMeta = bizObjectManager.getBizObject("NopTaskInstance").getObjMeta();
        assertTrue(!objMeta.getProp("status").isUpdatable(), "status must be non-updatable via CRUD (04-01)");
        assertTrue(!objMeta.getProp("taskInputs").isUpdatable(), "taskInputs must be non-updatable (resume context injection)");
        assertTrue(!objMeta.getProp("version").isUpdatable(), "optimistic-lock version must be non-updatable");
        assertTrue(objMeta.getProp("remark").isUpdatable(), "admin-maintainable remark stays writable");
        assertTrue(objMeta.getProp("priority").isUpdatable(), "admin-maintainable priority stays writable");
    }

    // ==================== helpers ====================

    private NopTaskInstance insertInstanceViaDao(String idPrefix) {
        long now = System.currentTimeMillis();
        NopTaskInstance e = new NopTaskInstance();
        // H2 文件库跨测试类持久，主键用 UUID 保证幂等
        e.setTaskInstanceId(idPrefix + "-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        e.setTaskName("demo-task");
        e.setTaskVersion(1L);
        e.setTaskGroup("demo-group");
        e.setStatus(STATUS_RUNNING);
        e.setPriority(100);
        e.setVersion(0);
        e.setCreatedBy("engine");
        e.setCreateTime(new Timestamp(now));
        e.setUpdatedBy("engine");
        e.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopTaskInstance.class).saveEntityDirectly(e);
        return e;
    }

    private NopTaskInstance loadInstance(String id) {
        NopTaskInstance loaded = daoProvider.daoFor(NopTaskInstance.class).getEntityById(id);
        assertNotNull(loaded, "instance row must exist: " + id);
        return loaded;
    }

    private ApiResponse<?> executeMutation(String operationName, Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        var context = graphQLEngine.newRpcContext(GraphQLOperationType.mutation, operationName, request);
        return io.nop.api.core.util.FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }
}
