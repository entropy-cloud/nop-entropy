package io.nop.wf.scheduler;

import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2514 MA-05（裁定④）红④ 收口门 C10-008 + MA-23（裁定⑥）红⑥ 收口门 C10-011。
 *
 * C10-008: 毒步骤（due action 恒失败）退避推进 dueTime 退出本轮扫描集（收敛性）+
 * 分批游标推进（批次形状）；缺陷态毒步骤每轮重入扫描集永不收敛（红）。
 * C10-011: 双 scanner 竞速——due action 触发次数恒==1（条件 UPDATE 抢占单飞）+
 * remindCount 终值恒==轮数（DB 原子自增）；缺陷态双跑/丢失更新（红）。
 *
 * store 替身以 ConcurrentHashMap/Atomic 语义模拟 DB 条件 UPDATE 与原子自增行为。
 */
public class TestWfTaskScannerFencing extends BaseTestCase {

    /**
     * 模拟 DB 的扫描 store：findDueActivatedSteps 按 dueTime<=now(且>cursor) 有界返回；
     * tryClaimScanLease 以 putIfAbsent 语义模拟条件 UPDATE 单飞；
     * incrementRemindCount 以原子自增模拟 COALESCE(remindCount,0)+1。
     */
    static class FencingStoreSim {
        final Map<String, WorkflowStepRecordBean> steps = new ConcurrentHashMap<>();
        final Map<String, Timestamp> dueTimes = new ConcurrentHashMap<>();
        final Set<String> claims = ConcurrentHashMap.newKeySet();
        final AtomicInteger remindCountDb = new AtomicInteger();
        final AtomicInteger dueInvocations = new AtomicInteger();
        final AtomicInteger fetchCalls = new AtomicInteger();
        final List<Timestamp> cursorsReceived = new ArrayList<>();
        volatile boolean failDueAction = false;
        /**
         * 模拟 DB 租约自然存续（租约到期前他节点抢占必败）：C10-011 双 scanner 竞速用，
         * release 不清除 claim 使竞速判据确定化
         */
        volatile boolean retainClaims = false;

        io.nop.wf.core.store.IWorkflowStore asStore() {
            return (io.nop.wf.core.store.IWorkflowStore) Proxy.newProxyInstance(
                    io.nop.wf.core.store.IWorkflowStore.class.getClassLoader(),
                    new Class[]{io.nop.wf.core.store.IWorkflowStore.class},
                    (proxy, method, args) -> {
                        int argCount = args == null ? 0 : args.length;
                        switch (method.getName() + "/" + argCount) {
                            case "findDueActivatedSteps/3": {
                                fetchCalls.incrementAndGet();
                                Timestamp cursorTime = (Timestamp) args[1];
                                String cursorStepId = (String) args[2];
                                if (cursorTime != null)
                                    cursorsReceived.add(cursorTime);
                                Timestamp now = new Timestamp(System.currentTimeMillis());
                                List<WorkflowStepRecordBean> ret = new ArrayList<>();
                                for (WorkflowStepRecordBean step : steps.values()) {
                                    Timestamp due = dueTimes.get(step.getStepId());
                                    if (due == null || due.after(now))
                                        continue;
                                    // plan 2585 二元组游标谓词模拟：((eq due and gt stepId) or gt due)
                                    if (cursorTime != null
                                            && (due.before(cursorTime)
                                            || (due.equals(cursorTime)
                                            && (cursorStepId == null
                                            || step.getStepId().compareTo(cursorStepId) <= 0))))
                                        continue;
                                    ret.add(step);
                                }
                                int batchSize = (Integer) args[0];
                                ret.sort(Comparator.comparing(WorkflowStepRecordBean::getDueTime)
                                        .thenComparing(WorkflowStepRecordBean::getStepId));
                                if (ret.size() > batchSize)
                                    return ret.subList(0, batchSize);
                                return ret;
                            }
                            case "findDueActivatedSteps/0":
                            case "findRemindActivatedSteps/0":
                                // 批量变体未被 scanner 消费；保持缺省路由
                                if (method.isDefault())
                                    return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                                return new ArrayList<>(steps.values());
                            case "findRemindActivatedSteps/3": {
                                fetchCalls.incrementAndGet();
                                Timestamp cursorTime = (Timestamp) args[1];
                                String cursorStepId = (String) args[2];
                                List<WorkflowStepRecordBean> ret = new ArrayList<>();
                                for (WorkflowStepRecordBean step : steps.values()) {
                                    Timestamp remind = step.getRemindTime();
                                    if (remind == null)
                                        continue;
                                    // plan 2585 二元组游标谓词模拟（与 due 半区同构）
                                    if (cursorTime != null
                                            && (remind.before(cursorTime)
                                            || (remind.equals(cursorTime)
                                            && (cursorStepId == null
                                            || step.getStepId().compareTo(cursorStepId) <= 0))))
                                        continue;
                                    ret.add(step);
                                }
                                int batchSize = (Integer) args[0];
                                ret.sort(Comparator.comparing(WorkflowStepRecordBean::getRemindTime)
                                        .thenComparing(WorkflowStepRecordBean::getStepId));
                                if (ret.size() > batchSize)
                                    return ret.subList(0, batchSize);
                                return ret;
                            }
                            case "tryClaimScanLease/3": {
                                IWorkflowStepRecord record = (IWorkflowStepRecord) args[0];
                                // 条件 UPDATE 单飞模拟：同 stepId 只有一个持有者成功
                                return claims.add(record.getStepId());
                            }
                            case "releaseScanLease/2": {
                                IWorkflowStepRecord record = (IWorkflowStepRecord) args[0];
                                if (!retainClaims)
                                    claims.remove(record.getStepId());
                                return null;
                            }
                            case "incrementRemindCount/1": {
                                // COALESCE(remindCount,0)+1 原子自增模拟
                                remindCountDb.incrementAndGet();
                                return null;
                            }
                            case "postponeStepDueTime/2": {
                                IWorkflowStepRecord record = (IWorkflowStepRecord) args[0];
                                dueTimes.put(record.getStepId(), (Timestamp) args[1]);
                                return null;
                            }
                            case "saveStepRecord/1":
                                return null;
                            default:
                                if (method.isDefault())
                                    return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                                return defaultValue(method.getReturnType());
                        }
                    });
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == null || returnType == Void.TYPE)
            return null;
        if (returnType == boolean.class)
            return false;
        if (returnType == int.class)
            return 0;
        if (returnType == long.class)
            return 0L;
        return null;
    }

    private IWorkflowManager managerReturning(WorkflowStepRecordBean record) {
        return (IWorkflowManager) Proxy.newProxyInstance(IWorkflowManager.class.getClassLoader(),
                new Class[]{IWorkflowManager.class}, (proxy, method, args) -> {
                    if ("getWorkflow".equals(method.getName())) {
                        return workflowProxy(record);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private IWorkflow workflowProxy(WorkflowStepRecordBean record) {
        return (IWorkflow) Proxy.newProxyInstance(IWorkflow.class.getClassLoader(),
                new Class[]{IWorkflow.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getStepById":
                            return Objects.equals(record.getStepId(), args[0]) ? stepProxy(record) : null;
                        case "getModel":
                            return modelProxy("approve");
                        case "getWfId":
                            return record.getWfId();
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private IWorkflowStep stepProxy(WorkflowStepRecordBean record) {
        return (IWorkflowStep) Proxy.newProxyInstance(IWorkflowStep.class.getClassLoader(),
                new Class[]{IWorkflowStep.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRecord":
                            return record;
                        case "getStepId":
                            return record.getStepId();
                        case "getStepName":
                            return record.getStepName();
                        case "getWfId":
                            return record.getWfId();
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private io.nop.wf.core.model.IWorkflowModel modelProxy(String dueAction) {
        return (io.nop.wf.core.model.IWorkflowModel) Proxy.newProxyInstance(
                io.nop.wf.core.model.IWorkflowModel.class.getClassLoader(),
                new Class[]{io.nop.wf.core.model.IWorkflowModel.class}, (proxy, method, args) -> {
                    if ("getStep".equals(method.getName())) {
                        return stepModelProxy(dueAction);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private io.nop.wf.core.model.IWorkflowStepModel stepModelProxy(String dueAction) {
        return (io.nop.wf.core.model.IWorkflowStepModel) Proxy.newProxyInstance(
                io.nop.wf.core.model.IWorkflowStepModel.class.getClassLoader(),
                new Class[]{io.nop.wf.core.model.IWorkflowStepModel.class}, (proxy, method, args) -> {
                    if ("getName".equals(method.getName()))
                        return "review";
                    if ("getDueAction".equals(method.getName()))
                        return dueAction;
                    return defaultValue(method.getReturnType());
                });
    }

    private io.nop.wf.core.service.WorkflowServiceSpi serviceSim(FencingStoreSim sim,
                                                                 Function<WorkflowStepRecordBean, Object> invoke) {
        return (io.nop.wf.core.service.WorkflowServiceSpi) Proxy.newProxyInstance(
                io.nop.wf.core.service.WorkflowServiceSpi.class.getClassLoader(),
                new Class[]{io.nop.wf.core.service.WorkflowServiceSpi.class}, (proxy, method, args) -> {
                    if ("invokeActionAsync".equals(method.getName())) {
                        String stepId = ((io.nop.wf.api.beans.WfActionRequestBean) args[0]).getStepId();
                        sim.dueInvocations.incrementAndGet();
                        if (sim.failDueAction) {
                            return java.util.concurrent.CompletableFuture
                                    .failedFuture(new IllegalStateException("due-action-boom"));
                        }
                        WorkflowStepRecordBean target = sim.steps.get(stepId);
                        Object ret = invoke.apply(target);
                        if (ret instanceof Throwable) {
                            return java.util.concurrent.CompletableFuture.failedFuture((Throwable) ret);
                        }
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private io.nop.orm.IOrmTemplate ormTemplateProxy() {
        return (io.nop.orm.IOrmTemplate) Proxy.newProxyInstance(io.nop.orm.IOrmTemplate.class.getClassLoader(),
                new Class[]{io.nop.orm.IOrmTemplate.class}, (proxy, method, args) -> {
                    if ("runInNewSession".equals(method.getName())) {
                        @SuppressWarnings("unchecked")
                        Function<Object, Object> callback = (Function<Object, Object>) args[0];
                        return callback.apply(null);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private WorkflowStepRecordBean dueRecord(String wfId, String stepId, Timestamp dueTime) {
        WorkflowStepRecordBean record = new WorkflowStepRecordBean();
        record.setWfId(wfId);
        record.setStepId(stepId);
        record.setStepName("review");
        record.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        record.setDueTime(dueTime);
        return record;
    }

    private WorkflowStepRecordBean remindRecord(String wfId, String stepId) {
        WorkflowStepRecordBean record = dueRecord(wfId, stepId,
                new Timestamp(System.currentTimeMillis() + 3_600_000L));
        record.setRemindTime(new Timestamp(System.currentTimeMillis() - 1000));
        return record;
    }

    private void runConcurrently(Runnable taskA, Runnable taskB) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Function<Runnable, Runnable> wrap = task -> () -> {
                try {
                    start.await();
                    task.run();
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            };
            Future<?> fa = pool.submit(wrap.apply(taskA));
            Future<?> fb = pool.submit(wrap.apply(taskB));
            start.countDown();
            fa.get(30, TimeUnit.SECONDS);
            fb.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        if (failure.get() != null)
            throw new AssertionError("concurrent scan failed", failure.get());
    }

    /**
     * C10-011 红⑥（MA-23 裁定⑥ due 路径）：双 scanner 竞速同一到期步骤（各自持有 DB
     * 会话快照副本）——修复后条件 UPDATE 抢占单飞，due action 触发次数恒==1
     * （缺陷态两节点各执行一次 → ==2 → 红）。
     */
    @Test
    public void testC10_011_doubleScannerRace_dueActionInvokedExactlyOnce() throws Exception {
        FencingStoreSim sim = new FencingStoreSim();
        Timestamp due = new Timestamp(System.currentTimeMillis() - 1000);
        WorkflowStepRecordBean snapA = dueRecord("wf-due", "step-1", due);
        WorkflowStepRecordBean snapB = dueRecord("wf-due", "step-1", due);
        sim.steps.put(snapA.getStepId(), snapA);
        sim.dueTimes.put(snapA.getStepId(), due);
        // 模拟 DB 租约存续：重叠扫描窗口内第二 scanner 抢占必败（单飞判据确定化）
        sim.retainClaims = true;

        WfTaskScanner scannerA = new WfTaskScanner();
        WfTaskScanner scannerB = new WfTaskScanner();
        scannerA.setWorkflowStore(sim.asStore());
        scannerA.setWorkflowManager(managerReturning(snapA));
        scannerA.setWorkflowService(serviceSim(sim, r -> null));
        scannerA.setOrmTemplate(ormTemplateProxy());
        scannerB.setWorkflowStore(sim.asStore());
        scannerB.setWorkflowManager(managerReturning(snapB));
        scannerB.setWorkflowService(serviceSim(sim, r -> null));
        scannerB.setOrmTemplate(ormTemplateProxy());

        runConcurrently(scannerA::scanDueTasks, scannerB::scanDueTasks);

        // 单飞：触发次数恒==1（缺陷态两节点各执行一次 → ==2 → 红）
        assertEquals(1, sim.dueInvocations.get());
    }

    /**
     * C10-011 红⑥（MA-23 裁定⑥ remind 路径）：双 scanner 竞速同一提醒步骤（各自持有
     * DB 会话快照副本）——remindCount 经 DB 原子自增，处理轮数与计数值恒一致
     * （缺陷态双会话读-改-写：通知翻倍而计数丢失更新 → 红）。
     */
    @Test
    public void testC10_011_doubleScannerRemindCount_atomicIncrements() throws Exception {
        FencingStoreSim sim = new FencingStoreSim();
        WorkflowStepRecordBean snapA = remindRecord("wf-remind", "step-1");
        WorkflowStepRecordBean snapB = remindRecord("wf-remind", "step-1");
        sim.steps.put(snapA.getStepId(), snapA);
        // 模拟 DB 租约存续：重叠扫描窗口内第二 scanner 抢占必败（单飞判据确定化）
        sim.retainClaims = true;

        List<IWorkflowStep> reminded = new ArrayList<>();

        WfTaskScanner scannerA = new WfTaskScanner();
        WfTaskScanner scannerB = new WfTaskScanner();
        scannerA.setWorkflowStore(sim.asStore());
        scannerA.setWorkflowManager(managerReturning(snapA));
        scannerA.setWorkflowService(unsupportedService());
        scannerA.setOrmTemplate(ormTemplateProxy());
        scannerA.setReminderListeners(List.of(reminded::add));
        scannerB.setWorkflowStore(sim.asStore());
        scannerB.setWorkflowManager(managerReturning(snapB));
        scannerB.setWorkflowService(unsupportedService());
        scannerB.setOrmTemplate(ormTemplateProxy());
        scannerB.setReminderListeners(List.of(reminded::add));

        runConcurrently(scannerA::scanRemindTasks, scannerB::scanRemindTasks);

        // fencing 单飞：重叠扫描只产一次处理（缺陷态双跑通知翻倍 → 红）
        assertEquals(1, reminded.size());
        // 原子自增不变量：处理轮数 == remindCount 终值（缺陷态丢失更新 → 不一致 → 红）
        assertEquals(sim.remindCountDb.get(), reminded.size());
    }

    private io.nop.wf.core.service.WorkflowServiceSpi unsupportedService() {
        return (io.nop.wf.core.service.WorkflowServiceSpi) Proxy.newProxyInstance(
                io.nop.wf.core.service.WorkflowServiceSpi.class.getClassLoader(),
                new Class[]{io.nop.wf.core.service.WorkflowServiceSpi.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException();
                });
    }

    /**
     * C10-008 红④（MA-05 裁定④ 毒步骤收敛）：due action 恒失败的毒步骤经退避推进
     * dueTime 退出本轮扫描集，第二轮扫描不再触达（缺陷态每轮重入 → 红）。
     */
    @Test
    public void testC10_008_poisonStepBackoff_leavesScanSet() {
        FencingStoreSim sim = new FencingStoreSim();
        WorkflowStepRecordBean poison = dueRecord("wf-poison", "step-p", new Timestamp(System.currentTimeMillis() - 1000));
        sim.steps.put(poison.getStepId(), poison);
        sim.dueTimes.put(poison.getStepId(), poison.getDueTime());
        sim.failDueAction = true;

        WfTaskScanner scanner = new WfTaskScanner();
        scanner.setWorkflowStore(sim.asStore());
        scanner.setWorkflowManager(managerReturning(poison));
        scanner.setWorkflowService(serviceSim(sim, r -> null));
        scanner.setOrmTemplate(ormTemplateProxy());

        // 第一轮：毒步骤失败 → 退避推进 dueTime（>=60s 基准，退出扫描集）
        scanner.scanDueTasks();
        assertEquals(1, sim.dueInvocations.get());
        Timestamp postponed = sim.dueTimes.get(poison.getStepId());
        assertTrue(postponed.after(new Timestamp(System.currentTimeMillis() + WfTaskScanner.BACKOFF_BASE_MS - 1000)),
                "poison dueTime must be pushed beyond backoff base");

        // 第二轮：毒步骤已退出扫描集，不再触达（收敛判据）
        scanner.scanDueTasks();
        assertEquals(1, sim.dueInvocations.get(), "poison step must leave the scan set after backoff");
    }

    /**
     * C10-008 红④（MA-05 裁定④ 分批游标）：批次上限内游标按上批末条 dueTime 推进，
     * 消费完整且无全量物化。
     */
    @Test
    public void testC10_008_batchCursorAdvancesByLastDueTime() {
        FencingStoreSim sim = new FencingStoreSim();
        List<WorkflowStepRecordBean> batch = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            WorkflowStepRecordBean record = dueRecord("wf-b", "step-" + i,
                    new Timestamp(System.currentTimeMillis() - 10_000L + i));
            sim.steps.put(record.getStepId(), record);
            sim.dueTimes.put(record.getStepId(), record.getDueTime());
            batch.add(record);
        }

        // batchSize=2：3 条候选 → 两次取批，第二次游标==首批末条 dueTime
        WfTaskScanner scanner = new WfTaskScanner() {
            @Override
            int getScanBatchSize() {
                return 2;
            }
        };
        scanner.setWorkflowStore(sim.asStore());
        scanner.setWorkflowManager(managerReturning(batch.get(0)));
        scanner.setWorkflowService(serviceSim(sim, r -> null));
        scanner.setOrmTemplate(ormTemplateProxy());

        scanner.scanDueTasks();

        assertEquals(2, sim.fetchCalls.get());
        assertEquals(1, sim.cursorsReceived.size());
        assertEquals(batch.stream().map(WorkflowStepRecordBean::getDueTime)
                        .sorted().collect(java.util.stream.Collectors.toList()).get(1),
                sim.cursorsReceived.get(0));
        // 全部 3 条候选在预算内被分批消费
        assertEquals(3, sim.dueInvocations.get());
    }

    /**
     * plan 2585 Phase 2 钉（同毫秒游标 tie-breaker）：多条候选共享同一毫秒 dueTime 且超批时，
     * 二元组游标 (dueTime, stepId) 经 stepId tie-breaker 逐批推进，全部候选被消费
     * （缺陷态：单值游标 gt dueTime 使后续同毫秒行整批跳过/死循环 → 红）。
     */
    @Test
    public void testSameMillisecondDueTime_tieBreakerCursorConsumesAll() {
        FencingStoreSim sim = new FencingStoreSim();
        Timestamp sameMs = new Timestamp(System.currentTimeMillis() - 10_000L);
        int total = 5;
        for (int i = 0; i < total; i++) {
            WorkflowStepRecordBean record = dueRecord("wf-same-ms", "step-" + i, sameMs);
            sim.steps.put(record.getStepId(), record);
            sim.dueTimes.put(record.getStepId(), sameMs);
        }

        WfTaskScanner scanner = new WfTaskScanner() {
            @Override
            int getScanBatchSize() {
                return 2;
            }
        };
        scanner.setWorkflowStore(sim.asStore());
        scanner.setWorkflowManager(managerReturning(sim.steps.values().iterator().next()));
        scanner.setWorkflowService(serviceSim(sim, r -> null));
        scanner.setOrmTemplate(ormTemplateProxy());

        scanner.scanDueTasks();

        // 全部同毫秒候选分批消费完毕（缺陷态：旧游标下第 2 批起谓词 gt dueTime
        // 永不命中剩余同毫秒行 → 死循环/跳批 → 红）
        assertEquals(total, sim.dueInvocations.get(), "all same-millisecond due steps must be consumed");
        // 每批游标时间恒为同毫秒值（tie-breaker 由 stepId 承担）
        assertTrue(sim.fetchCalls.get() >= 3, "must advance through multiple batches");
    }
}
