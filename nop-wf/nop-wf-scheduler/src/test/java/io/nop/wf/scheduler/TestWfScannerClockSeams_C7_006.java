package io.nop.wf.scheduler;

import io.nop.core.context.IServiceContext;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.model.IWorkflowModel;
import io.nop.wf.core.model.IWorkflowStepModel;
import io.nop.wf.core.service.WorkflowServiceSpi;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.store.beans.WorkflowStepRecordBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4-32（1.5-C7-006，plan 2566 [R1-M4]）WfTaskScanner 双缝载体钉：
 * <ol>
 *   <li>单调缝（nowNanos=预算 4 站点）：stub 子类覆写 nowNanos 注入受控步进时钟——
 *       扫描预算耗尽由单调钟决定；变异（预算判定回退缝外直连 System.currentTimeMillis）
 *       下 stub 失效 → 全批消费完（scanned=6≠4）→ RED。</li>
 *   <li>墙钟缝（nowMillis=leaseUntil/dueTime/remindTime 绝对域）：覆写 nowMillis 到已知
 *       epoch 值，退避写值断言 = 已知值 + backoff（绝对域保留 epoch 语义）。</li>
 * </ol>
 */
public class TestWfScannerClockSeams_C7_006 extends BaseTestCase {

    /**
     * 测钉子类：覆写双缝注入受控时钟（站点级 stub 优先，零全局 registerClock）
     */
    static class SteppedScanner extends WfTaskScanner {
        final AtomicInteger nanoCalls = new AtomicInteger();
        volatile long fixedNowMillis = -1L;

        // 每次调用推进 STEP_NANOS（模拟单调流逝）
        @Override
        protected long nowNanos() {
            return nanoCalls.getAndIncrement() * STEP_NANOS;
        }

        @Override
        protected long nowMillis() {
            return fixedNowMillis >= 0 ? fixedNowMillis : super.nowMillis();
        }

        @Override
        int getScanBatchSize() {
            return 2;
        }

        @Override
        long getScanTimeBudgetMs() {
            return 100;
        }
    }

    // 100ms 预算：步进 40ms/次 → deadline=t0+100；check 序列 t0+40(继续)/t0+80(继续)/t0+120(耗尽)
    static final long STEP_NANOS = 40_000_000L;

    static WorkflowStepRecordBean record(String wfId, String stepId, long dueTime) {
        WorkflowStepRecordBean record = new WorkflowStepRecordBean();
        record.setWfId(wfId);
        record.setStepId(stepId);
        record.setStepName("review");
        record.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        record.setDueTime(new Timestamp(dueTime));
        return record;
    }

    @Test
    public void testBudgetExhaustionFollowsMonotonicSeam() {
        // 6 条到期记录，分批返回：page1=2、page2=2、page3=1（< batchSize 自然截断）
        List<IWorkflowStepRecord> all = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            all.add(record("wf-budget", "step-" + i, 1000L + i));
        }

        AtomicInteger findCalls = new AtomicInteger();
        AtomicInteger claimCalls = new AtomicInteger();
        List<IWorkflowStepRecord> dueList = List.of();
        SteppedScanner scanner = new SteppedScanner();
        scanner.setWorkflowStore(storeProxy((proxy, method, args) -> {
            String name = method.getName();
            // 拦截分支先于 isDefault（否则 default 方法走产品实现，计数缝失效）
            if ("findDueActivatedSteps".equals(name)) {
                int page = findCalls.getAndIncrement();
                if (page == 0)
                    return all.subList(0, 2);
                if (page == 1)
                    return all.subList(2, 4);
                return all.subList(4, 5);
            }
            if ("tryClaimScanLease".equals(name)) {
                claimCalls.incrementAndGet();
                return Boolean.TRUE;
            }
            if (method.isDefault())
                return InvocationHandler.invokeDefault(proxy, method, args);
            return defaultValue(method.getReturnType());
        }, dueList));
        scanner.setWorkflowManager(workflowManagerProxy(
                workflowProxy(stepProxy(all.get(0)), workflowModelProxy(stepModelProxy(null)))));
        scanner.setWorkflowService(inertWorkflowService());
        scanner.setOrmTemplate(ormTemplateProxy());

        scanner.scanDueTasks();

        // 确定性断言：单调预算在 3rd check（t0+120ms ≥ t0+100ms deadline）耗尽 → 仅消费前 4 条
        // （check 先于 find：find=2 次）；变异（预算判定回退墙钟直连）下 stub 失效 → 预算永不耗尽
        // → page3（1 条 < batchSize）截断，find=3/claims=6 → RED
        assertEquals(2, findCalls.get(), "paged fetch must stop at budget exhaustion");
        assertEquals(4, claimCalls.get(),
                "scan must consume exactly 4 items before monotonic budget (100ms, step 40ms/check) exhausts; "
                        + "if the budget seam was bypassed to wall clock all 6 items would be consumed");
    }

    @Test
    public void testAbsoluteBackoffWriteFollowsWallSeam() {
        // remind listener 失败 → remindTime = nowMillis() + backoff（已知 epoch 值，绝对域墙钟缝）
        WorkflowStepRecordBean record = record("wf-seam", "step-seam", 1000L);
        record.setRemindTime(new Timestamp(1_700_000_000_000L));

        final long fixedEpoch = 1_700_000_123_456L;
        SteppedScanner scanner = new SteppedScanner();
        scanner.fixedNowMillis = fixedEpoch;
        List<Timestamp> postponed = new ArrayList<>();
        scanner.setWorkflowStore(storeProxy((proxy, method, args) -> {
            if ("findRemindActivatedSteps".equals(method.getName()))
                return List.of(record);
            // 拦截分支先于 isDefault（postponeStepRemindTime 为 default 方法，捕获退避写值）
            if ("postponeStepRemindTime".equals(method.getName())) {
                postponed.add((Timestamp) args[1]);
                return null;
            }
            if (method.isDefault())
                return InvocationHandler.invokeDefault(proxy, method, args);
            return defaultValue(method.getReturnType());
        }, List.of()));
        scanner.setWorkflowManager(workflowManagerProxy(
                workflowProxy(stepProxy(record), workflowModelProxy(stepModelProxy(null)))));
        scanner.setWorkflowService(inertWorkflowService());
        scanner.setReminderListeners(List.of(step -> {
            // 非 NopException 的运行时异常探针（绝缘层钉）：lint 门禁禁裸 RuntimeException 字面量，用子类保语义
            throw new IllegalStateException("probe failure");
        }));
        scanner.setOrmTemplate(ormTemplateProxy());

        scanner.scanRemindTasks();

        assertEquals(1, postponed.size(), "failed remind must be postponed exactly once");
        // 首次失败退避 = nowMillis + 60s（BACKOFF_BASE_MS）：绝对域写值必须经墙钟缝取已知 epoch
        assertEquals(new Timestamp(fixedEpoch + 60_000L), postponed.get(0),
                "remindTime write must use the wall-clock seam (absolute domain keeps epoch semantics)");
        assertTrue(postponed.get(0).getTime() > fixedEpoch,
                "postponed remindTime must be in the future relative to the seam clock");
    }

    // ==================== proxy harness（沿 TestWfTaskScanner/TestCraR408 自包含形态） ====================

    private WorkflowServiceSpi inertWorkflowService() {
        return new WorkflowServiceSpi() {
            @Override
            public CompletableFuture<Object> invokeActionAsync(io.nop.wf.api.beans.WfActionRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<io.nop.wf.api.beans.WfStartResponseBean> startWorkflowAsync(
                    io.nop.wf.api.beans.WfStartRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<Void> notifySubFlowEndAsync(io.nop.wf.api.beans.WfSubFlowEndRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<Void> killWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<Void> suspendWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<Void> resumeWorkflowAsync(io.nop.wf.api.beans.WfCommandRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<Void> signalWfAsync(io.nop.wf.api.beans.WfSignalRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<io.nop.wf.api.beans.WfTransferResultBean> transferActorsAsync(
                    io.nop.wf.api.beans.WfTransferActorsRequestBean request,
                    io.nop.api.core.beans.FieldSelectionBean selection, IServiceContext ctx) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private io.nop.wf.core.store.IWorkflowStore storeProxy(InvocationHandler handler,
                                                           List<IWorkflowStepRecord> remindStepsIgnored) {
        // findRemindActivatedSteps 分支由 handler 自行处理（本钉两条用例各自注入）
        return (io.nop.wf.core.store.IWorkflowStore) Proxy.newProxyInstance(
                io.nop.wf.core.store.IWorkflowStore.class.getClassLoader(),
                new Class[]{io.nop.wf.core.store.IWorkflowStore.class}, handler);
    }

    private IWorkflowManager workflowManagerProxy(IWorkflow workflow) {
        return proxy(IWorkflowManager.class, (p, method, args) -> {
            if (method.getName().equals("getWorkflow"))
                return workflow;
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflow workflowProxy(IWorkflowStep step, IWorkflowModel model) {
        return proxy(IWorkflow.class, (p, method, args) -> {
            String name = method.getName();
            if ("getStepById".equals(name))
                return Objects.equals(step.getStepId(), args[0]) ? step : null;
            if ("getModel".equals(name))
                return model;
            if ("getWfId".equals(name))
                return step.getWfId();
            if ("getWfName".equals(name))
                return "test/scheduler";
            if ("getWfVersion".equals(name))
                return 1L;
            if ("getRecord".equals(name))
                return null;
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowStep stepProxy(IWorkflowStepRecord record) {
        return proxy(IWorkflowStep.class, (p, method, args) -> {
            String name = method.getName();
            if ("getRecord".equals(name))
                return record;
            if ("getStepId".equals(name))
                return record.getStepId();
            if ("getStepName".equals(name))
                return record.getStepName();
            if ("getWfId".equals(name))
                return record.getWfId();
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowModel workflowModelProxy(IWorkflowStepModel stepModel) {
        return proxy(IWorkflowModel.class, (p, method, args) -> {
            if (method.getName().equals("getStep"))
                return Objects.equals(stepModel.getName(), args[0]) ? stepModel : null;
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowStepModel stepModelProxy(String dueAction) {
        return proxy(IWorkflowStepModel.class, (p, method, args) -> {
            String name = method.getName();
            if ("getName".equals(name))
                return "review";
            if ("getDueAction".equals(name))
                return dueAction;
            if ("getActions".equals(name) || "getTransitionFromSteps".equals(name)
                    || "getTransitionToSteps".equals(name))
                return List.of();
            if ("getTransitionFromStepNames".equals(name) || "getTransitionToStepNames".equals(name)
                    || "getWaitStepNames".equals(name) || "getWaitSignals".equals(name))
                return java.util.Set.of();
            return defaultValue(method.getReturnType());
        });
    }

    private io.nop.orm.IOrmTemplate ormTemplateProxy() {
        return proxy(io.nop.orm.IOrmTemplate.class, (p, method, args) -> {
            if (method.getName().equals("runInNewSession")) {
                Function<?, ?> fn = (Function<?, ?>) args[0];
                return fn.apply(null);
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (p, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName().equals("toString"))
                            return type.getSimpleName() + "@c7006-proxy";
                        if (method.getName().equals("hashCode"))
                            return System.identityHashCode(p);
                        if (method.getName().equals("equals"))
                            return p == args[0];
                        return null;
                    }
                    return handler.invoke(p, method, args);
                });
    }

    static Object defaultValue(Class<?> returnType) {
        if (returnType == null || returnType == Void.TYPE)
            return null;
        if (!returnType.isPrimitive()) {
            if (List.class.isAssignableFrom(returnType))
                return new ArrayList<>();
            if (Map.class.isAssignableFrom(returnType))
                return Map.of();
            return null;
        }
        if (returnType == boolean.class)
            return false;
        if (returnType == int.class)
            return 0;
        if (returnType == long.class)
            return 0L;
        if (returnType == double.class)
            return 0D;
        if (returnType == float.class)
            return 0F;
        if (returnType == short.class)
            return (short) 0;
        if (returnType == byte.class)
            return (byte) 0;
        if (returnType == char.class)
            return (char) 0;
        return null;
    }
}
