package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.TaskConfigs;
import io.nop.task.TaskConstants;
import io.nop.task.dao.entity.NopTaskInstance;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.49 R4-09（plan 2542）裁定 E：红⑫ W-12（1.5-C7-010）。
 *
 * <p>红线：修复前 nop-task 全模块零 SUSPENDED 滞留指标——「等人工」与「被遗忘」两类滞留
 * 不可区分。修复后 {@code nop.task.suspended-tasks} gauge（kind=all|stale）按 scrape 时机
 * 聚合 status=SUSPENDED 行，stale 腿以 {@code nop.task.suspended.stale-ms} 阈值切分。
 *
 * <p>「等人工」声明语义零触碰：本类断言 gauge 注册/取值，不触发任何 resume。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSuspendedStaleGaugeR409 extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private DaoTaskStateStore store;

    @BeforeEach
    public void setUpStore() {
        store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);
    }

    /**
     * 红⑫：SUSPENDED 行注入（旧/新两行）→ kind=all 计数恒为存量 +2，kind=stale 仅计超阈值行。
     */
    @Test
    public void suspendedGauges_splitAllAndStaleByThreshold() {
        setTestConfig(TaskConfigs.CFG_TASK_SUSPENDED_STALE_MS, 3600000);
        try {
            long baselineAll = doubleValue("all");
            long baselineStale = doubleValue("stale");

            ITaskState fresh = store.newTaskState("testW12Fresh", 0, newRuntime());
            ITaskState stale = store.newTaskState("testW12Stale", 0, newRuntime());

            IEntityDao<NopTaskInstance> dao = daoProvider.daoFor(NopTaskInstance.class);
            markSuspended(dao, fresh.getTaskInstanceId(), System.currentTimeMillis());
            markSuspended(dao, stale.getTaskInstanceId(), System.currentTimeMillis() - 7200000L);

            // 触发 gauge 注册（首次 loadTaskState 路径）——注册本身不得改变滞留语义
            store.loadTaskState(fresh.getTaskInstanceId(), newRuntime());

            assertNotNull(gauge("all"), "nop.task.suspended-tasks{kind=all} must be registered (W-12; pre-fix: absent)");
            assertEquals(baselineAll + 2, doubleValue("all"),
                    "kind=all must count every SUSPENDED row (W-12)");
            assertEquals(baselineStale + 1, doubleValue("stale"),
                    "kind=stale must count only rows older than stale-ms (W-12)");

            // 「等人工」语义零触碰：gauge 读取不触发 resume——行仍 SUSPENDED
            assertEquals(TaskConstants.TASK_STATUS_SUSPENDED,
                    dao.getEntityById(stale.getTaskInstanceId()).getStatus(),
                    "gauge is observe-only: SUSPENDED rows must stay SUSPENDED (W-12 no auto-resume)");
        } finally {
            clearTestConfig(TaskConfigs.CFG_TASK_SUSPENDED_STALE_MS.getName());
        }
    }

    /**
     * 红⑫ 阈值边界：stale-ms 放大到超过行龄后 stale 腿不再计数该行（键行为钉）。
     */
    @Test
    public void staleThresholdKeyIsHonored() {
        IEntityDao<NopTaskInstance> dao = daoProvider.daoFor(NopTaskInstance.class);
        // 计数是全局聚合——清空同表残留 SUSPENDED 行后差值断言才成立
        for (NopTaskInstance existing : dao.findAllByQuery(new io.nop.api.core.beans.query.QueryBean()))
            dao.deleteEntityDirectly(existing);

        ITaskState row = store.newTaskState("testW12Boundary", 0, newRuntime());
        markSuspended(dao, row.getTaskInstanceId(), System.currentTimeMillis() - 600000L);
        store.loadTaskState(row.getTaskInstanceId(), newRuntime());
        assertEquals(TaskConstants.TASK_STATUS_SUSPENDED,
                dao.getEntityById(row.getTaskInstanceId()).getStatus(), "row must be SUSPENDED");
        assertTrue(dao.getEntityById(row.getTaskInstanceId()).getUpdateTime().getTime()
                        < System.currentTimeMillis() - 300000L,
                "row updateTime must be backdated (ORM auto-stamp must not override)");

        setTestConfig(TaskConfigs.CFG_TASK_SUSPENDED_STALE_MS, 60000);
        try {
            long strictStale = doubleValue("stale");

            setTestConfig(TaskConfigs.CFG_TASK_SUSPENDED_STALE_MS, 3600000);
            long relaxedStale = doubleValue("stale");
            assertEquals(strictStale - 1, relaxedStale,
                    "raising stale-ms above the row age must drop it from the stale count (W-12 key behavior)");
        } finally {
            clearTestConfig(TaskConfigs.CFG_TASK_SUSPENDED_STALE_MS.getName());
        }
    }

    /**
     * 构造滞留行：status=SUSPENDED 且 updateTime 回填为指定时刻。
     *
     * <p>两道闸口都必须关闭才构造得出来：(a) ORM 缺省对新写入的 updateTime 无条件盖章
     * （{@code nop.orm.use-assigned-user-timestamp=false}），故临时切到 assigned 语义；
     * (b) 实体在会话内仍是 dirty/managed，任何后续 flush 都会再次盖章——故对该实体持续
     * 关闭自动盖章（不再复原：本用例内该行只用于构造滞留场景）。
     */
    private void markSuspended(IEntityDao<NopTaskInstance> dao, String id, long updateTime) {
        setTestConfig(io.nop.orm.OrmConfigs.CFG_ORM_USE_ASSIGNED_USER_TIMESTAMP, true);
        try {
            NopTaskInstance entity = dao.getEntityById(id);
            entity.setStatus(TaskConstants.TASK_STATUS_SUSPENDED);
            entity.setUpdateTime(new Timestamp(updateTime));
            entity.orm_disableAutoStamp(true);
            dao.updateEntityDirectly(entity);
        } finally {
            clearTestConfig(io.nop.orm.OrmConfigs.CFG_ORM_USE_ASSIGNED_USER_TIMESTAMP.getName());
        }
    }

    private static io.micrometer.core.instrument.Gauge gauge(String kind) {
        return io.nop.commons.metrics.GlobalMeterRegistry.instance()
                .find("nop.task.suspended-tasks").tag("kind", kind).gauge();
    }

    private static long doubleValue(String kind) {
        io.micrometer.core.instrument.Gauge g = gauge(kind);
        return g == null ? 0L : Math.round(g.value());
    }

    private ITaskRuntime newRuntime() {
        return new TaskRuntimeImpl(new TaskFlowManagerImpl(), store, null, XLang.newEvalScope(), false);
    }
}
