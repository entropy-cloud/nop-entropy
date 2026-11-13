package io.nop.rpc.cluster;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.rpc.IRpcService;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.chooser.IServerChooser;
import io.nop.api.core.json.JSON;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.core.lang.json.JsonTool;
import io.nop.rpc.core.utils.RpcHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.2-C4-003（plan 2498 Phase 6）：ClusterRpc 两个裸 globalTimer 注入点（矩阵 D3/D4 站点）改为
 * globalTimer().executeOn(globalWorker()) 卸载——真实装配链上断言轮询体（tick）执行线程为
 * worker 而非 timer 线程（rule 23 接线验证 + rule 22 端到端）。
 */
@Timeout(60)
public class TestClusterRpcPollOffload_C4_003 {

    static final AtomicReference<String> lastProbeThread = new AtomicReference<>();

    @BeforeAll
    static void initJsonProvider() {
        // PollTask tick 的 LOG.debug 分支使用 JSON.serialize，裸 JUnit 环境需注册解析器
        JSON.registerProvider(JsonTool.instance());
    }

    static ApiResponse<Object> statusResponse(String state) {
        TaskStatusBean status = new TaskStatusBean();
        status.setTaskId("t1");
        status.setTaskState(state);
        // TaskStatusBean.isCompleted 按 taskStatus 判定（>STATUS_RUNNING），仅设 taskState 不构成终态
        status.setTaskStatus("COMPLETED".equals(state) ? TaskStatusBean.STATUS_SUCCESS : TaskStatusBean.STATUS_RUNNING);
        return ApiResponse.success(status);
    }

    static IServerChooser<ApiRequest<?>> singleInstanceChooser() {
        ServiceInstance instance = new ServiceInstance();
        instance.setServiceName("test-svc");
        instance.setAddr("test-host");
        instance.setPort(18080);
        return new IServerChooser<ApiRequest<?>>() {
            @Override
            public ServiceInstance chooseFromCandidates(List<ServiceInstance> candidates, ApiRequest<?> request) {
                return candidates.get(0);
            }

            @Override
            public CompletionStage<List<ServiceInstance>> getServersAsync(String serviceName, ApiRequest<?> request) {
                return CompletableFuture.completedFuture(Collections.singletonList(instance));
            }

            @Override
            public List<ServiceInstance> getServers(String serviceName, ApiRequest<?> request) {
                return Collections.singletonList(instance);
            }
        };
    }

    static IScheduledExecutor timerOf(Object owner, String fieldName) throws Exception {
        Field f = owner.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return (IScheduledExecutor) f.get(owner);
    }

    /**
     * rule 23 接线验证（注入点 1 = ClusterRpcProxyFactoryBean init，矩阵 D4）：
     * 真实 init() 装配链捕获传入 ClusterRpcClient 的 timer，probe 任务体执行线程为 worker
     * （Bind 载体真实生效）；修复前该 timer 为裸 globalTimer，任务体落在 timer 线程（先红）。
     */
    @Test
    public void factoryBeanInjectionPointBindsExecuteOnCarrier_C4_003() throws Exception {
        ClusterRpcProxyFactoryBean factoryBean = new ClusterRpcProxyFactoryBean();
        factoryBean.setServiceName("test-svc");
        factoryBean.setServiceClass(ServiceA.class);
        factoryBean.setServerChooser(singleInstanceChooser());
        factoryBean.setClientProvider(instance -> null);
        factoryBean.init();

        IScheduledExecutor timer = timerOf(factoryBean.getRpcService(), "timer");
        assertNotNull(timer, "ClusterRpcClient must receive the default timer");

        timer.schedule(() -> {
            lastProbeThread.set(Thread.currentThread().getName());
            return null;
        }, 0, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS);

        assertTrue(lastProbeThread.get().startsWith("nop-global-worker"),
                "default timer must be executeOn(globalWorker) bound, got: " + lastProbeThread.get());
    }

    /**
     * rule 23 接线验证（注入点 2 = ClusterRpcServiceInvoker init，矩阵 D3）：同上。
     */
    @Test
    public void serviceInvokerInjectionPointBindsExecuteOnCarrier_C4_003() throws Exception {
        ClusterRpcServiceInvoker invoker = new ClusterRpcServiceInvoker();
        invoker.setServerChooser(singleInstanceChooser());
        invoker.setClientProvider(instance -> null);
        invoker.init();

        IScheduledExecutor timer = timerOf(invoker, "timer");
        assertNotNull(timer, "invoker must bind the default timer after init");

        timer.schedule(() -> {
            lastProbeThread.set(Thread.currentThread().getName());
            return null;
        }, 0, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS);

        assertTrue(lastProbeThread.get().startsWith("nop-global-worker"),
                "default timer must be executeOn(globalWorker) bound, got: " + lastProbeThread.get());
    }

    /**
     * 端到端（rule 22）：真实 ClusterRpcServiceInvoker 装配链发起 polling 型请求，
     * 轮询 tick（PollTask.call → 状态检查调用）可观测为 worker 线程、非 timer 线程。
     */
    @Test
    public void pollingTickRunsOnWorkerNotTimerThread_C4_003() throws Exception {
        Set<String> tickThreads = new ConcurrentSkipListSet<>();
        AtomicInteger statusCalls = new AtomicInteger();
        IRpcService instanceClient = (serviceMethod, request, cancelToken) -> {
            if (serviceMethod.equals("start"))
                return CompletableFuture.completedFuture(statusResponse("RUNNING"));
            // status 检查：记录执行线程（PollTask.call 的运行线程），3 轮后完成终止链
            tickThreads.add(Thread.currentThread().getName());
            if (statusCalls.incrementAndGet() >= 3)
                return CompletableFuture.completedFuture(statusResponse("COMPLETED"));
            return CompletableFuture.completedFuture(statusResponse("RUNNING"));
        };

        ClusterRpcServiceInvoker invoker = new ClusterRpcServiceInvoker();
        invoker.setServerChooser(singleInstanceChooser());
        invoker.setClientProvider(instance -> instanceClient);
        invoker.init();

        ApiRequest<?> request = new ApiRequest<>();
        RpcHelper.setPollingMethod(request, "getStatus");
        RpcHelper.setPollInterval(request, 20);

        ApiResponse<?> ret = invoker.getRpcService("test-svc")
                .callAsync("start", request, null)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertTrue(ret.isBizSuccess(), "polling call must complete normally");

        assertTrue(!tickThreads.isEmpty(), "status ticks must have been observed");
        for (String thread : tickThreads) {
            assertTrue(thread.startsWith("nop-global-worker"),
                    "polling tick must run on globalWorker (offloaded), got: " + thread);
            assertTrue(!thread.contains("nop-global-timer"),
                    "polling tick must not run on globalTimer, got: " + thread);
        }
    }
}
