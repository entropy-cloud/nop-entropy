package io.nop.wf.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.wf.api.WfReference;
import io.nop.wf.core.mock.SimpleWfActorResolver;
import io.nop.wf.api.beans.WfTransferActorsRequestBean;
import io.nop.wf.api.beans.WfTransferResultBean;
import io.nop.wf.core.engine.IWorkflowExecutor;
import io.nop.wf.core.impl.IWorkflowImplementor;
import io.nop.wf.core.impl.IWorkflowStepImplementor;
import io.nop.wf.core.store.IWorkflowStepRecord;
import io.nop.wf.core.engine.WorkflowEngineImpl;
import io.nop.wf.core.engine.WfRuntime;
import io.nop.wf.core.model.WfRetryModel;
import io.nop.wf.core.model.WfStepModel;
import io.nop.wf.core.service.impl.WorkflowServiceImpl;
import io.nop.wf.core.store.IWorkflowStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.48 R4-08（plan 2541）wf 域钉族：W-02（1.5-MA-07 裁定 D——批量转办请求级 maxBatch
 * 上界 + 部分成功语义保留）与 W-12（1.5-MA-22 裁定 F——computeRetryDelay 封顶统一两分支 +
 * 活动事务上下文降级不重试）。store 桩以动态代理承载（三参/四参按参数个数分派；hasOpenedTransaction
 * 可控），兼容 pre-fix 与 post-fix 两种服务形态（变异回退红证）。
 */
public class TestCraR408TransferAndRetry extends BaseTestCase {

    private static final ErrorCode TEST_PROBE_ERROR =
            define("nop.err.test.r408.probe", "r408 test probe");


    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ==================== W-02（红②）：批量转办请求级上界 ====================

    private WorkflowServiceImpl newService(IWorkflowStore store) {
        WorkflowServiceImpl service = new WorkflowServiceImpl();
        service.setWorkflowExecutor(executorStub());
        service.setWorkflowStore(store);
        service.setWfActorResolver(new SimpleWfActorResolver());
        return service;
    }

    private WfTransferActorsRequestBean request(String fromUserId) {
        WfTransferActorsRequestBean request = new WfTransferActorsRequestBean();
        request.setFromUserId(fromUserId);
        request.setToUserId("u2");
        return request;
    }

    private IServiceContext ctx() {
        ServiceContextImpl context = new ServiceContextImpl();
        context.getContext().setUserId("tester");
        return context;
    }

    @Test
    public void transferOverMaxBatch_typedRejected() {
        // 红②：N=6 > maxBatch=5 → ERR_WF_TRANSFER_BATCH_EXCEED typed 拒绝（不静默截断）。
        // 修复前全量载入逐条执行成功（successCount=N）→ 红
        AppConfig.getConfigProvider().updateConfigValue(WfConfigs.CFG_WF_TRANSFER_MAX_BATCH, 5);
        List<IWorkflowStepRecord> steps = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            steps.add(newStepRecord("wf-1", "step-" + i));
        }
        WorkflowServiceImpl service = newService(storeStub(steps));

        NopException err = assertThrows(NopException.class,
                () -> service.transferActorsAsync(request("u1"), null, ctx()),
                "transfer with steps exceeding nop.wf.transfer.max-batch must be typed-rejected (W-02 MA-07)");
        assertEquals(NopWfCoreErrors.ERR_WF_TRANSFER_BATCH_EXCEED.getErrorCode(), err.getErrorCode());
        assertEquals("u1", err.getParam("ownerId"));
        assertEquals(5, err.getParam("maxBatch"));
    }

    @Test
    public void transferUnderMaxBatch_partialSuccessSemanticsPreserved() {
        // 部分成功语义保留腿（逐字保留——裁定 D 落款）：N=3 <= maxBatch → successCount==3
        AppConfig.getConfigProvider().updateConfigValue(WfConfigs.CFG_WF_TRANSFER_MAX_BATCH, 5);
        List<IWorkflowStepRecord> steps = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            steps.add(newStepRecord("wf-1", "step-" + i));
        }
        WorkflowServiceImpl service = newService(storeStub(steps));

        WfTransferResultBean result = service.transferActorsAsync(request("u1"), null, ctx()).toCompletableFuture().join();
        assertEquals(3, result.getSuccessCount(),
                "partial-success contract (successCount+failedItems) preserved across the paging rework");
        assertTrue(result.getFailedItems().isEmpty());
    }

    @Test
    public void transferInvalidMaxBatchConfig_failsTyped() {
        // 配置 <=0 → typed 配置错误（WfConfigs 校验腿）
        AppConfig.getConfigProvider().updateConfigValue(WfConfigs.CFG_WF_TRANSFER_MAX_BATCH, 0);
        WorkflowServiceImpl service = newService(storeStub(new ArrayList<>()));

        NopException err = assertThrows(NopException.class,
                () -> service.transferActorsAsync(request("u1"), null, ctx()),
                "maxBatch<=0 must fail with the typed transfer-config error");
        assertEquals(NopWfCoreErrors.ERR_WF_INVALID_TRANSFER_CONFIG.getErrorCode(), err.getErrorCode());
    }

    // ==================== W-12（红⑫）：wf retry 封顶 + 事务降级 ====================

    @Test
    public void computeRetryDelay_fixedDelayAppliesMaxRetryDelayCap() throws Exception {
        // 红⑫(a)：固定 delay（非 exponential）同样应用 maxRetryDelay 封顶（对齐 task 侧
        // RetryPolicy 两分支均封顶先例）。修复前固定 delay 不封顶 → 返回 5000（红）；
        // 修复后返回 100。maxRetryDelay=0 维持不封顶（对照腿）
        WorkflowEngineImpl engine = new WorkflowEngineImpl();
        Method m = WorkflowEngineImpl.class.getDeclaredMethod("computeRetryDelay", WfRetryModel.class, int.class);
        m.setAccessible(true);

        WfRetryModel retry = new WfRetryModel();
        retry.setRetryDelay(5000);
        retry.setMaxRetryDelay(100);
        retry.setExponentialDelay(false);

        long delay = (Long) m.invoke(engine, retry, 0);
        assertEquals(100L, delay,
                "fixed retryDelay must be capped by maxRetryDelay on the non-exponential branch too (W-12 MA-22)");

        // 对照腿：exponential 分支封顶不回退（既有行为）
        retry.setExponentialDelay(true);
        long expDelay = (Long) m.invoke(engine, retry, 0);
        assertEquals(100L, expDelay, "exponential branch cap unchanged");

        // 对照腿：maxRetryDelay<=0 维持不封顶（存量长退避零变更——裁定 F 落款）
        WfRetryModel uncapped = new WfRetryModel();
        uncapped.setRetryDelay(5000);
        uncapped.setMaxRetryDelay(0);
        uncapped.setExponentialDelay(false);
        assertEquals(5000L, (Long) m.invoke(engine, uncapped, 0),
                "maxRetryDelay=0 keeps the legacy uncapped semantics");
    }

    @Test
    public void retryInsideOpenTransaction_degradesNoRetry() throws Exception {
        // 红⑫(b)：活动事务上下文（store 桩 hasOpenedTransaction=true）→ 延迟等待降级不重试，
        // 显式失败（execCount==1、立即抛原异常）。修复前 sleep+重试 → execCount==4（红）。
        // 对照腿：无事务上下文（hasOpenedTransaction=false 缺省）→ 重试正常进行（既有语义）
        WorkflowEngineImpl engine = new WorkflowEngineImpl();
        Method m = WorkflowEngineImpl.class.getDeclaredMethod("runSourceWithRetry",
                IWorkflowStepImplementor.class, WfStepModel.class, WfRuntime.class);
        m.setAccessible(true);

        WfStepModel stepModel = new WfStepModel();
        stepModel.setName("r408-retry-step");
        stepModel.setSource(scope -> {
            throw new io.nop.api.core.exceptions.NopException(TEST_PROBE_ERROR);
        });
        WfRetryModel retry = new WfRetryModel();
        retry.setMaxRetryCount(3);
        retry.setRetryDelay(50);
        retry.setMaxRetryDelay(0);
        retry.setExponentialDelay(false);
        stepModel.setRetry(retry);

        // —— 事务打开：降级不重试 ——
        AtomicInteger execCountTx = new AtomicInteger();
        IWorkflowStepImplementor stepTx = stepStub(execCountTx);
        WfRuntime wfRtTx = new WfRuntime(wfImplementorStub(true), new ServiceContextImpl());
        long begin = System.currentTimeMillis();
        try {
            m.invoke(engine, stepTx, stepModel, wfRtTx);
            fail("source failure must propagate (retry exhausted or degraded)");
        } catch (Exception expected) {
            // probe 异常直达
        }
        assertEquals(1, execCountTx.get(),
                "inside an open transaction the delayed retry must degrade to no-retry "
                        + "(W-12(b) MA-22; pre-fix: slept 50ms and retried, execCount=4)");
        assertTrue(System.currentTimeMillis() - begin < 100,
                "degraded path must not sleep the retry delay");

        // —— 无事务：重试既有语义（对照腿，pre/post 同绿）——
        AtomicInteger execCountNoTx = new AtomicInteger();
        IWorkflowStepImplementor stepNoTx = stepStub(execCountNoTx);
        WfRuntime wfRtNoTx = new WfRuntime(wfImplementorStub(false), new ServiceContextImpl());
        try {
            m.invoke(engine, stepNoTx, stepModel, wfRtNoTx);
            fail("source failure must propagate after retries exhausted");
        } catch (Exception expected) {
            // probe 异常直达（重试耗尽后抛原始异常）
        }
        assertEquals(4, execCountNoTx.get(),
                "without an open transaction retry semantics unchanged (maxRetryCount=3 → 4 executions)");
    }

    // ==================== stubs ====================

    private IWorkflowStepRecord newStepRecord(String wfId, String stepId) {
        io.nop.wf.core.store.beans.WorkflowStepRecordBean record =
                new io.nop.wf.core.store.beans.WorkflowStepRecordBean();
        record.setWfId(wfId);
        record.setStepId(stepId);
        record.setStepName("review");
        record.setStatus(NopWfCoreConstants.WF_STEP_STATUS_ACTIVATED);
        return record;
    }

    private IWorkflowStore storeStub(List<IWorkflowStepRecord> steps) {
        return proxy(IWorkflowStore.class, (proxy, method, args) -> {
            String name = method.getName();
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if ("findActivatedStepsByOwner".equals(name)) {
                // 三参（pre-fix 全量形态）与四参（post-fix 分页形态）同集返回——服务层形态
                // 差异由服务侧 maxBatch 判定承载
                return steps;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowExecutor executorStub() {
        return proxy(IWorkflowExecutor.class, (proxy, method, args) -> {
            if ("execute".equals(method.getName())) {
                // 单项执行成功（部分成功语义腿不涉鉴权——task lambda 不消费）
                return CompletableFuture.completedFuture(null);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowStepImplementor stepStub(AtomicInteger execCount) {
        return proxy(IWorkflowStepImplementor.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("getRecord".equals(name)) {
                return proxy(IWorkflowStepRecord.class, (p2, m2, a2) -> {
                    if ("incExecCount".equals(m2.getName())) {
                        execCount.incrementAndGet();
                        return null;
                    }
                    return defaultValue(m2.getReturnType());
                });
            }
            if ("getWfName".equals(name)) {
                return "test/wf";
            }
            if ("getWfId".equals(name)) {
                return "wf-r408";
            }
            if ("getStepName".equals(name)) {
                return "r408-retry-step";
            }
            return defaultValue(method.getReturnType());
        });
    }

    private IWorkflowImplementor wfImplementorStub(boolean transactionOpened) {
        return proxy(IWorkflowImplementor.class, (proxy, method, args) -> {
            String name = method.getName();
            if ("getStore".equals(name)) {
                return proxy(IWorkflowStore.class, (p2, m2, a2) -> {
                    // hasOpenedTransaction 为接口 default 方法——自定义 case 必须先于
                    // isDefault 分支（invokeDefault 会以接口缺省 false 覆盖桩语义）
                    if ("hasOpenedTransaction".equals(m2.getName())) {
                        return transactionOpened;
                    }
                    if (m2.isDefault()) {
                        return InvocationHandler.invokeDefault(p2, m2, a2);
                    }
                    return defaultValue(m2.getReturnType());
                });
            }
            if ("getGlobalVars".equals(name)) {
                return new io.nop.core.utils.MapVarSet();
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName().equals("toString"))
                            return type.getSimpleName() + "@r408-proxy";
                        if (method.getName().equals("hashCode"))
                            return System.identityHashCode(proxy);
                        if (method.getName().equals("equals"))
                            return proxy == args[0];
                        return null;
                    }
                    return handler.invoke(proxy, method, args);
                });
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == null || returnType == Void.TYPE) {
            return null;
        }
        if (!returnType.isPrimitive()) {
            if (List.class.isAssignableFrom(returnType)) {
                return new ArrayList<>();
            }
            if (Map.class.isAssignableFrom(returnType)) {
                return Map.of();
            }
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
