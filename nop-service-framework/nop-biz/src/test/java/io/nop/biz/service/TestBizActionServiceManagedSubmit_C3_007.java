package io.nop.biz.service;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.context.BaseContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.biz.api.IBizActionModel;
import io.nop.biz.api.IBizModel;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.biz.BizErrors.ERR_BIZ_ASYNC_INVOCATION_NO_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 2.3-C3-007（plan 2499 Phase 3，裁定③快照+executeWithContext 形态）：
 * BizActionService.callActionAsync 受管提交改造 + BizActionInvocation.proceedAsync fail-fast。
 * <p>
 * 缺陷：裸 executor.execute 绕过平台「任务 finally detachContext」约定——调用方 IContext
 * （tenant/locale/trace/callExpireTime）不传播到 worker 线程；任务体内 ServiceContextImpl
 * 字段初始化（getOrCreateContext）在池线程永久附着新建/残留 context，跨任务串号。
 * 注记：beanContainer 取出的是 Executor（非 ExecutorService），ExecutorHelper.submit 受管
 * 入口不能直接承载（裁定③被拒形态）——本修复为提交时快照 + IContext.executeWithContext。
 * <p>
 * 判别口径：单线程池载体（真实线程错位）下，biz 方法体观测的 currentContext 即调用方
 * context（assertSame + tenant 断言）；任务完成后 worker 线程无 context 残留（串号面）。
 * proceedAsync 无线附着时以 ERR_BIZ_ASYNC_INVOCATION_NO_CONTEXT 语义化拒绝（修复前 NPE）。
 */
public class TestBizActionServiceManagedSubmit_C3_007 {

    /** biz 方法体（worker 线程）观测的 [IContext, tenantId] */
    static final AtomicReference<IContext> OBSERVED_CONTEXT = new AtomicReference<>();
    static final AtomicReference<String> OBSERVED_TENANT = new AtomicReference<>();

    @BeforeEach
    public void setUp() {
        BaseContextProvider.clear();
    }

    @AfterEach
    public void tearDown() {
        BaseContextProvider.clear();
        OBSERVED_CONTEXT.set(null);
        OBSERVED_TENANT.set(null);
    }

    private static IBizObject probeActor() {
        IBizModel bizModel = (IBizModel) Proxy.newProxyInstance(
                TestBizActionServiceManagedSubmit_C3_007.class.getClassLoader(),
                new Class[]{IBizModel.class},
                (p, m, a) -> {
                    if ("getAction".equals(m.getName()) && "doProbe".equals(a[0]))
                        return actionModel("doProbe");
                    return defaultValue(m.getReturnType());
                });
        return (IBizObject) Proxy.newProxyInstance(
                TestBizActionServiceManagedSubmit_C3_007.class.getClassLoader(),
                new Class[]{IBizObject.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getBizObjName":
                            return "ProbeObj";
                        case "getBizModel":
                            return bizModel;
                        case "invoke":
                            // worker 线程上执行：观测线程上下文
                            OBSERVED_CONTEXT.set(ContextProvider.currentContext());
                            OBSERVED_TENANT.set(ContextProvider.currentTenantId());
                            return "probe-ok";
                        default:
                            return defaultValue(m.getReturnType());
                    }
                });
    }

    private static IBizActionModel actionModel(String name) {
        return (IBizActionModel) Proxy.newProxyInstance(
                TestBizActionServiceManagedSubmit_C3_007.class.getClassLoader(),
                new Class[]{IBizActionModel.class},
                (p, m, a) -> {
                    if ("getName".equals(m.getName()))
                        return name;
                    if ("isBizSequential".equals(m.getName()))
                        return false;
                    return defaultValue(m.getReturnType());
                });
    }

    private static BizActionService newService(IBizObject actor, Executor executor) {
        BizActionService service = new BizActionService();
        service.setBizObjManager((IBizObjectManager) Proxy.newProxyInstance(
                TestBizActionServiceManagedSubmit_C3_007.class.getClassLoader(),
                new Class[]{IBizObjectManager.class},
                (p, m, a) -> "getBizObject".equals(m.getName()) ? actor
                        : defaultValue(m.getReturnType())));
        service.setBeanContainer((IBeanContainer) Proxy.newProxyInstance(
                TestBizActionServiceManagedSubmit_C3_007.class.getClassLoader(),
                new Class[]{IBeanContainer.class},
                (p, m, a) -> "getBean".equals(m.getName()) ? executor
                        : defaultValue(m.getReturnType())));
        return service;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive())
            return null;
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        return null;
    }

    /**
     * 判别用例（先红后绿）：调用方 context 快照经 executeWithContext 传播到 worker 线程。
     * 修复前裸提交，worker 线程观测 currentContext()==null（红）；修复后观测 == 调用方
     * context 且 tenant 传播（绿）。
     */
    @Test
    public void testCallerContextSnapshotPropagatesToWorkerThread() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            BizActionService service = newService(probeActor(), worker);

            IContext callerContext = ContextProvider.newContext(true);
            callerContext.setTenantId("tenant-c3-007");
            try {
                ApiRequest<Object> request = new ApiRequest<>();
                request.setData(Map.of("k", "v"));
                request.setHeader("nop-tenant", "tenant-c3-007");

                ApiResponse<?> res = service.callActionAsync("ProbeObj", "doProbe", request)
                        .toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertEquals("probe-ok", res.getData());

                assertSame(callerContext, OBSERVED_CONTEXT.get(),
                        "biz method body must run with the caller's context attached "
                                + "(snapshot + executeWithContext managed submit)");
                assertEquals("tenant-c3-007", OBSERVED_TENANT.get(),
                        "tenant must propagate to the worker thread");
            } finally {
                callerContext.close();
            }
        } finally {
            worker.shutdown();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /**
     * 串号面（C3-007 第二缺陷要素）：受管提交后 worker 线程无 context 残留——下一任务在
     * 同一池线程上观测无线附着（修复前任务体内 ServiceContextImpl 构造会永久附着新建
     * context，残留可观测，红）。
     */
    @Test
    public void testWorkerThreadHasNoContextResidueAfterManagedSubmit() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            BizActionService service = newService(probeActor(), worker);

            IContext callerContext = ContextProvider.newContext(true);
            try {
                ApiRequest<Object> request = new ApiRequest<>();
                request.setData(Map.of("k", "v"));
                service.callActionAsync("ProbeObj", "doProbe", request)
                        .toCompletableFuture().get(5, TimeUnit.SECONDS);

                AtomicReference<IContext> residue = new AtomicReference<>();
                CompletableFuture.runAsync(
                        () -> residue.set(ContextProvider.currentContext()), worker)
                        .get(5, TimeUnit.SECONDS);

                assertNull(residue.get(),
                        "worker thread must not retain context residue after the managed task "
                                + "(executeWithContext finally restores prior binding)");
            } finally {
                callerContext.close();
            }
        } finally {
            worker.shutdown();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /**
     * fail-fast（rule 24）：无线附着线程上 proceedAsync 以语义化错误拒绝（ERR_BIZ_
     * ASYNC_INVOCATION_NO_CONTEXT），修复前为无上下文 NPE。
     */
    @Test
    public void testProceedAsyncFailsFastWithoutBoundContext() throws Exception {
        assertNull(ContextProvider.currentContext(), "test precondition: no bound context");

        IBizObject actor = probeActor();
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(Map.of("k", "v"));

        BizActionInvocation inv = new BizActionInvocation(actor, "doProbe", request, null);
        CompletionStage<ApiResponse<?>> future = inv.proceedAsync();

        java.util.concurrent.ExecutionException ex =
                assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> future.toCompletableFuture().get(2, TimeUnit.SECONDS));
        Throwable err = ex.getCause();

        assertTrue(err instanceof NopException,
                "fail-fast must reject with a semantic error, got: " + err);
        assertEquals(ERR_BIZ_ASYNC_INVOCATION_NO_CONTEXT.getErrorCode(),
                ((NopException) err).getErrorCode());
    }
}
