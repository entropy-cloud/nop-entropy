package io.nop.tcc.core.impl;

import io.nop.tcc.api.ITccRecord;
import io.nop.tcc.api.TccStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * C10-007 收口门 tcc 半边（plan 2515 红⑪，C7-005）：
 * 清理任务生产接线（LifeCycleSupport 生命周期接口化 + beans 装配行）+ 耗尽行前置终态化（KILLED）
 * + 索引申报落地。缺陷态：清理 API 零生产调用方（空壳）、耗尽行被恢复扫描与清理双重永久排除、
 * nop_tcc_record 全表零索引、beans 装配无生命周期挂接——全部断言红。
 */
@Timeout(30)
public class TestTccLedgerCleanup_C10_007 {

    /** 测试侧行模型：携带 cleanup 语义所需字段（终态化/清理谓词判定输入） */
    static class CleanupRow implements ITccRecord {
        final String txnId;
        final Timestamp beginTime;
        final Timestamp expireTime;
        final int retryTimes;
        volatile TccStatus status;

        CleanupRow(String txnId, TccStatus status, int retryTimes, long beginTimeOffsetMs, long expireOffsetMs) {
            this.txnId = txnId;
            this.status = status;
            this.retryTimes = retryTimes;
            long now = System.currentTimeMillis();
            this.beginTime = new Timestamp(now + beginTimeOffsetMs);
            this.expireTime = new Timestamp(now + expireOffsetMs);
        }

        @Override
        public String getTxnGroup() {
            return "default";
        }

        @Override
        public String getTxnId() {
            return txnId;
        }

        @Override
        public TccStatus getTccStatus() {
            return status;
        }

        @Override
        public Timestamp getExpireTime() {
            return expireTime;
        }
    }

    /** 清理语义 fake：实现耗尽行终态化与有界清理谓词（与裁定⑧ store 契约同形） */
    static class CleanupFakeStore extends FakeTccRecordStore {
        final List<CleanupRow> rows = new ArrayList<>();
        final AtomicInteger killCalls = new AtomicInteger();
        final AtomicInteger boundedRemoveCalls = new AtomicInteger();
        final AtomicReference<Object> lastKillArgs = new AtomicReference<>();
        final AtomicReference<Object> lastRemoveArgs = new AtomicReference<>();

        // 以下两个方法在修复后的 ITccRecordStore 接口中为契约方法（故不加 @Override，
        // 以保证缺陷态基线同样可编译运行）

        public int killExhaustedRecords(int maxRetryCount, int batchSize) {
            killCalls.incrementAndGet();
            lastKillArgs.set(maxRetryCount + "," + batchSize);
            int killed = 0;
            long now = System.currentTimeMillis();
            for (CleanupRow row : rows) {
                if (row.status != null && !row.status.isFinished()
                        && row.retryTimes >= maxRetryCount && row.expireTime.getTime() < now) {
                    row.status = TccStatus.KILLED;
                    killed++;
                    if (killed >= batchSize)
                        break;
                }
            }
            return killed;
        }

        public int removeCompletedRecords(long retentionTime, boolean onlyCompleted, int batchSize) {
            boundedRemoveCalls.incrementAndGet();
            lastRemoveArgs.set(retentionTime + "," + onlyCompleted + "," + batchSize);
            long minTime = System.currentTimeMillis() - retentionTime;
            int removed = 0;
            List<CleanupRow> keep = new ArrayList<>();
            for (CleanupRow row : rows) {
                boolean oldEnough = row.beginTime.getTime() < minTime;
                boolean deletable = !onlyCompleted || (row.status != null && row.status.isFinished());
                if (oldEnough && deletable && removed < batchSize) {
                    removed++;
                } else {
                    keep.add(row);
                }
            }
            rows.clear();
            rows.addAll(keep);
            return removed;
        }
    }

    static Object invoke(Object target, String methodName, Class<?>[] argTypes, Object... args) throws Exception {
        Method method = findMethod(target.getClass(), methodName, argTypes);
        if (method == null)
            throw new NoSuchMethodException(target.getClass().getName() + "." + methodName);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw (e.getCause() instanceof Exception ? (Exception) e.getCause() : e);
        }
    }

    static Method findMethod(Class<?> clazz, String name, Class<?>[] argTypes) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, argTypes);
            } catch (NoSuchMethodException e) {
                // continue up the hierarchy
            }
        }
        return null;
    }

    /** 红⑪接线面 1：beans 装配行必须为 nopTccEngine 挂接生命周期钩子（init-method/destroy-method） */
    @Test
    public void C7_005_beansWiring_nopTccEngineMustHaveLifecycleHooks() throws Exception {
        String xml = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(getClass().getResource("/_vfs/nop/tcc/beans/tcc-core-defaults.beans.xml").toURI())));
        int beanIdx = xml.indexOf("id=\"nopTccEngine\"");
        assertTrue(beanIdx >= 0, "nopTccEngine bean must exist in tcc-core-defaults.beans.xml");
        int beanEnd = xml.indexOf("/>", beanIdx);
        String beanElement = xml.substring(beanIdx, beanEnd);
        assertTrue(beanElement.contains("init-method=\"start\""),
                "nopTccEngine bean must be wired with init-method=\"start\" (production cleanup chain hook), got: "
                        + beanElement);
        assertTrue(beanElement.contains("destroy-method=\"stop\""),
                "nopTccEngine bean must be wired with destroy-method=\"stop\"");
    }

    /** 红⑪接线面 2：清理周期链经 LifeCycleSupport 生命周期接口化（doStart 启动链/doStop 取消） */
    @Test
    public void C7_005_engineLifecycle_lifecycleInterfaceWithCleanupChain() throws Exception {
        Class<?> engineClass = Class.forName("io.nop.tcc.core.impl.TccEngine");
        assertEquals("LifeCycleSupport", engineClass.getSuperclass().getSimpleName(),
                "TccEngine must extend LifeCycleSupport (lifecycle-hooked cleanup chain)");
        // 清理体方法必须存在（生产可达；protected 与 RetryEngineImpl 清理体同构）
        assertNotNull(findMethod(engineClass, "runLedgerCleanup", new Class<?>[0]),
                "cleanup body must be reachable");
        // 配置键族 nop.tcc.ledger.* 必须成文（零键名二源，裁定⑧ §3.8）
        Class<?> configsClass = Class.forName("io.nop.tcc.core.TccEngineConfigs");
        configsClass.getField("CFG_TCC_LEDGER_RETENTION_DAYS");
        configsClass.getField("CFG_TCC_LEDGER_CLEANUP_INTERVAL");
        configsClass.getField("CFG_TCC_LEDGER_DELETE_BATCH_SIZE");
        configsClass.getField("TCC_LEDGER_MIN_CLEANUP_INTERVAL_MILLIS");
    }

    /** 红⑪行为面：耗尽行前置终态化（KILLED）后入既有 finished 谓词，有界清理 */
    @Test
    public void C7_005_cleanupBody_killExhaustedRowsFirst_thenBoundedRemove() throws Exception {
        CleanupFakeStore store = new CleanupFakeStore();
        TccEngine engine = new TccEngine();
        engine.setTccRecordStore(store);

        // 耗尽行：retryTimes 超限 + 已过期 + 非终态 → 必须先被终态化为 KILLED
        CleanupRow exhausted = new CleanupRow("txn-exhausted", TccStatus.TRYING, 200, -40 * 24 * 3600_000L, -1000);
        // 未耗尽行：重试次数未超限 → 不被终态化、不被清理（仍可恢复）
        CleanupRow live = new CleanupRow("txn-live", TccStatus.TRYING, 5, -40 * 24 * 3600_000L, -1000);
        store.rows.add(exhausted);
        store.rows.add(live);

        invoke(engine, "runLedgerCleanup", new Class<?>[0]);

        assertEquals(1, store.killCalls.get(), "cleanup must run the exhausted-row terminalization pre-step");
        assertEquals(TccStatus.KILLED, exhausted.status,
                "exhausted row must be terminalized to KILLED before entering the cleanup predicate");
        assertEquals(TccStatus.TRYING, live.status, "non-exhausted row must not be terminalized");

        assertEquals(1, store.boundedRemoveCalls.get(), "cleanup must run the bounded completed-rows removal");
        assertEquals(1, store.rows.size(), "only the terminalized old row may be removed");
        assertEquals("txn-live", store.rows.get(0).txnId,
                "non-terminal old row must be kept for recovery");
    }

    /** 红⑪接线面 3：start() 生命周期钩子必须真实启动清理周期链（调度器真实调用清理体） */
    @Test
    public void C7_005_startHook_schedulerActuallyInvokesCleanupBody() throws Exception {
        CleanupFakeStore store = new CleanupFakeStore();
        store.rows.add(new CleanupRow("txn-x", TccStatus.TRYING, 200, -40 * 24 * 3600_000L, -1000));
        TccEngine engine = new TccEngine();
        engine.setTccRecordStore(store);

        // 立即执行型调度器桩：scheduleWithFixedDelay 收到清理体即内联执行，证明链真实接通
        AtomicReference<Object> scheduledBody = new AtomicReference<>();
        Object executorProxy = Proxy.newProxyInstance(
                io.nop.commons.concurrent.executor.IScheduledExecutor.class.getClassLoader(),
                new Class<?>[]{io.nop.commons.concurrent.executor.IScheduledExecutor.class},
                (proxy, method, args) -> {
                    if ("scheduleWithFixedDelay".equals(method.getName()) && args.length >= 1
                            && args[0] instanceof Runnable) {
                        scheduledBody.set(args[0]);
                        ((Runnable) args[0]).run();
                        return CompletableFuture.completedFuture(null);
                    }
                    if (Object.class.equals(method.getDeclaringClass()))
                        return method.invoke(proxy, args);
                    return null;
                });
        invoke(engine, "setExecutor",
                new Class<?>[]{io.nop.commons.concurrent.executor.IScheduledExecutor.class}, executorProxy);

        invoke(engine, "start", new Class<?>[0]);
        try {
            assertTrue(scheduledBody.get() != null, "doStart must schedule the ledger cleanup chain");
            assertEquals(1, store.killCalls.get(),
                    "cleanup body must actually run through the lifecycle-started scheduler chain");
        } finally {
            invoke(engine, "stop", new Class<?>[0]);
        }
    }
}
