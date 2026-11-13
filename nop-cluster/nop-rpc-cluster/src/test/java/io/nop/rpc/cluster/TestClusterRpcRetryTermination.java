package io.nop.rpc.cluster;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopConnectException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.ICancelToken;
import io.nop.cluster.chooser.IServerChooser;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.lb.impl.ServiceLoadBalanceAdapter;
import io.nop.commons.concurrent.executor.DefaultScheduledExecutor;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static io.nop.cluster.ClusterErrors.ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE;
import static io.nop.http.api.HttpApiErrors.ERR_HTTP_CONNECT_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C3-001 (plan 2521): every synchronous exception on the retry path — inside accept()
 * bodies AND inside the completion-callback err branch — must terminate the returned
 * future (completeExceptionally). The pre-fix code drops the stage returned by
 * whenComplete, so those exceptions vanish and the caller hangs forever.
 */
@Timeout(60)
public class TestClusterRpcRetryTermination {

    private IScheduledExecutor timer;

    @BeforeEach
    public void setUp() {
        timer = DefaultScheduledExecutor.newSingleThreadTimer("test-cluster-rpc-termination");
    }

    @AfterEach
    public void tearDown() {
        timer.destroy();
    }

    private static ServiceInstance instance(String id) {
        ServiceInstance instance = new ServiceInstance();
        instance.setInstanceId(id);
        instance.setAddr("10.0.0.1");
        instance.setPort(8080);
        instance.setServiceName("svc");
        return instance;
    }

    private static ApiRequest<?> request() {
        return ApiRequest.build("test");
    }

    static class FailingRpcService implements IRpcService {
        @Override
        public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                         ICancelToken cancelToken) {
            return CompletableFuture.failedFuture(new NopConnectException(ERR_HTTP_CONNECT_FAIL));
        }

        @Override
        public ApiResponse<?> call(String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }
    }

    /** Chooser that throws a synchronous exception after the first hop (retry body face). */
    static class ThrowingChooser implements IServerChooser<ApiRequest<?>> {
        final List<ServiceInstance> candidates;
        int hops;

        ThrowingChooser(List<ServiceInstance> candidates) {
            this.candidates = candidates;
        }

        @Override
        public CompletionStage<List<ServiceInstance>> getServersAsync(String serviceName,
                                                                      ApiRequest<?> request) {
            return CompletableFuture.completedStage(candidates);
        }

        @Override
        public List<ServiceInstance> getServers(String serviceName, ApiRequest<?> request) {
            return candidates;
        }

        @Override
        public ServiceInstance chooseFromCandidates(List<ServiceInstance> candidates,
                                                    ApiRequest<?> request) {
            if (hops++ >= 1)
                throw new IllegalStateException("chooser blew up on the retry hop");
            return candidates.get(0);
        }
    }

    /** Chooser over an immutable candidate list — the err-branch remove() throws UOE. */
    static class ImmutableCandidatesChooser implements IServerChooser<ApiRequest<?>> {
        final List<ServiceInstance> candidates;

        ImmutableCandidatesChooser(List<ServiceInstance> candidates) {
            this.candidates = candidates;
        }

        @Override
        public CompletionStage<List<ServiceInstance>> getServersAsync(String serviceName,
                                                                      ApiRequest<?> request) {
            return CompletableFuture.completedStage(candidates);
        }

        @Override
        public List<ServiceInstance> getServers(String serviceName, ApiRequest<?> request) {
            return candidates;
        }

        @Override
        public ServiceInstance chooseFromCandidates(List<ServiceInstance> candidates,
                                                    ApiRequest<?> request) {
            return candidates.get(0);
        }
    }

    static class NullChooser implements IServerChooser<ApiRequest<?>> {
        @Override
        public CompletionStage<List<ServiceInstance>> getServersAsync(String serviceName,
                                                                      ApiRequest<?> request) {
            return CompletableFuture.completedStage(Arrays.asList(instance("i1")));
        }

        @Override
        public List<ServiceInstance> getServers(String serviceName, ApiRequest<?> request) {
            return Arrays.asList(instance("i1"));
        }

        @Override
        public ServiceInstance chooseFromCandidates(List<ServiceInstance> candidates,
                                                    ApiRequest<?> request) {
            return null;
        }
    }

    static class RecordingProvider implements IRpcClientInstanceProvider {
        @Override
        public IRpcService getRpcClientInstance(ServiceInstance instance) {
            // mirrors HttpRpcClientInstanceProvider :35 — first dereference of the instance
            assertNotNull(instance, "provider must not receive a null instance");
            return new FailingRpcService();
        }
    }

    /**
     * 红④ main pin: the retry hop's synchronous exception must terminate the future.
     * Failure mode in the defect state is a HANG — get() times out (bounded, and the
     * timeout semantics are asserted via the try/catch below).
     */
    @Test
    public void retryBodySyncThrowTerminatesFuture() {
        ThrowingChooser chooser = new ThrowingChooser(Arrays.asList(instance("i1"), instance("i2")));
        ClusterRpcClient client = new ClusterRpcClient("svc", chooser, new RecordingProvider(), timer);
        client.setRetryCount(3);

        CompletableFuture<ApiResponse<?>> future = client
                .callAsync("/rpc/test", request(), null)
                .toCompletableFuture();

        // the defect leaves the future unconsummated forever; the fix terminates it
        Throwable err = assertThrows(Exception.class,
                () -> future.get(5, TimeUnit.SECONDS),
                "C3-001: retry-body sync exception must completeExceptionally the returned future");
        assertTrue(future.isCompletedExceptionally(), "future must reach a terminal state");
    }

    /**
     * M2 face (plan 2521 review): the err-branch callback body itself throws — an
     * immutable candidates list makes instances.remove(instance) throw
     * UnsupportedOperationException inside the completion callback, where no accept()
     * frame can catch it. Must still terminate the future.
     */
    @Test
    public void errBranchBodyThrowTerminatesFuture() {
        ImmutableCandidatesChooser chooser = new ImmutableCandidatesChooser(
                Arrays.asList(instance("i1"), instance("i2")));
        ClusterRpcClient client = new ClusterRpcClient("svc", chooser, new RecordingProvider(), timer);
        client.setRetryCount(3);

        CompletableFuture<ApiResponse<?>> future = client
                .callAsync("/rpc/test", request(), null)
                .toCompletableFuture();

        assertThrows(Exception.class,
                () -> future.get(5, TimeUnit.SECONDS),
                "C3-001: err-branch sync exception (immutable remove) must terminate the future");
        assertTrue(future.isCompletedExceptionally());
    }

    /**
     * 伴生 null 钉 (adjudication ④): chooser returning null must fail with the typed
     * no-available-server error, not an untyped NPE from the provider dereference.
     * Both states terminate (first hop reaches the caller via the outer stage) — the
     * assertion pins the error code.
     */
    @Test
    public void nullChosenInstanceFailsTyped() {
        NullChooser chooser = new NullChooser();
        ClusterRpcClient client = new ClusterRpcClient("svc", chooser, new RecordingProvider(), timer);

        CompletableFuture<ApiResponse<?>> future = client
                .callAsync("/rpc/test", request(), null)
                .toCompletableFuture();
        ExecutionException err = assertThrows(ExecutionException.class,
                () -> future.get(5, TimeUnit.SECONDS),
                "null chosen instance must fail the call (first hop reaches the caller)");
        Throwable cause = err.getCause();
        assertTrue(cause instanceof NopException,
                "C3-001: null consumption point must be typed at the consumer side, got: " + cause);
        assertEquals(ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE.getErrorCode(),
                ((NopException) cause).getErrorCode(),
                "null chosen instance must produce the typed no-available-server error");
    }

    /**
     * 4.2-MA-09（plan 2559 裁定 D）：同步重试循环的防御性 null 守卫（与 async 半边同型）——
     * chooser 返回 null 必须抛类型化 no-available-server 错误，不得以 provider 解引用 NPE 形态浮出。
     */
    @Test
    public void syncNullChosenInstanceFailsTyped() {
        NullChooser chooser = new NullChooser();
        ClusterRpcClient client = new ClusterRpcClient("svc", chooser, new RecordingProvider(), timer);

        NopException err = assertThrows(NopException.class,
                () -> client.call("/rpc/test", request(), null),
                "sync path: null chosen instance must fail typed");
        assertEquals(ERR_CLUSTER_NO_AVAILABLE_SERVER_INSTANCE.getErrorCode(), err.getErrorCode(),
                "sync path null guard must produce the typed no-available-server error");
    }

    /**
     * 4.2-C8-001（plan 2559 裁定 G/R25-2）接线验证载体（rule 23）：真实调用路径上活跃计数
     * 持有 1 → 调用终结后回落 0（sync 路径）。
     */
    @Test
    public void activeCountWiringSyncCallFallsBackToZero() {
        ServiceInstance inst = instance("i1");
        ServiceLoadBalanceAdapter adapter = new ServiceLoadBalanceAdapter();

        SingleSuccessRpcService rpc = new SingleSuccessRpcService(adapter, inst);
        RecordingSingleProvider provider = new RecordingSingleProvider(rpc);

        ClusterRpcClient client = new ClusterRpcClient("svc",
                new FixedInstanceChooser(Arrays.asList(inst)), provider, timer);
        client.setLoadBalanceAdapter(adapter);

        assertEquals(0, adapter.getActiveCount(inst), "pre-call active count must be 0");
        ApiResponse<?> ret = client.call("/rpc/test", request(), null);
        assertNotNull(ret);
        assertEquals(1, rpc.calls, "rpc service must have been invoked on the real call path");
        assertEquals(0, adapter.getActiveCount(inst),
                "C8-001: active count must fall back 1->0 after the sync call completes");
    }

    /**
     * 4.2-C8-001（plan 2559 裁定 G/R25-2）接线验证载体（rule 23）：async 路径 whenComplete
     * 终结后活跃计数回落 0。
     */
    @Test
    public void activeCountWiringAsyncCallFallsBackToZero() throws Exception {
        ServiceInstance inst = instance("i1");
        ServiceLoadBalanceAdapter adapter = new ServiceLoadBalanceAdapter();

        SingleSuccessRpcService rpc = new SingleSuccessRpcService(adapter, inst);
        RecordingSingleProvider provider = new RecordingSingleProvider(rpc);

        ClusterRpcClient client = new ClusterRpcClient("svc",
                new FixedInstanceChooser(Arrays.asList(inst)), provider, timer);
        client.setLoadBalanceAdapter(adapter);

        ApiResponse<?> ret = client.callAsync("/rpc/test", request(), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertNotNull(ret);
        assertEquals(1, rpc.calls);
        assertEquals(0, adapter.getActiveCount(inst),
                "C8-001: active count must fall back 1->0 after the async stage completes");
    }

    /** Chooser that always returns the fixed candidate list (real non-null choice). */
    static class FixedInstanceChooser implements IServerChooser<ApiRequest<?>> {
        final List<ServiceInstance> candidates;

        FixedInstanceChooser(List<ServiceInstance> candidates) {
            this.candidates = candidates;
        }

        @Override
        public CompletionStage<List<ServiceInstance>> getServersAsync(String serviceName,
                                                                      ApiRequest<?> request) {
            return CompletableFuture.completedStage(candidates);
        }

        @Override
        public List<ServiceInstance> getServers(String serviceName, ApiRequest<?> request) {
            return candidates;
        }

        @Override
        public ServiceInstance chooseFromCandidates(List<ServiceInstance> candidates,
                                                    ApiRequest<?> request) {
            return candidates.get(0);
        }
    }

    /** Provider returning a fixed IRpcService. */
    static class RecordingSingleProvider implements IRpcClientInstanceProvider {
        final IRpcService service;

        RecordingSingleProvider(IRpcService service) {
            this.service = service;
        }

        @Override
        public IRpcService getRpcClientInstance(ServiceInstance instance) {
            return service;
        }
    }

    /** Real call-path rpc service: asserts the in-flight active count is 1, then succeeds. */
    static class SingleSuccessRpcService implements IRpcService {
        final ServiceLoadBalanceAdapter adapter;
        final ServiceInstance instance;
        int calls;

        SingleSuccessRpcService(ServiceLoadBalanceAdapter adapter, ServiceInstance instance) {
            this.adapter = adapter;
            this.instance = instance;
        }

        @Override
        public CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, ApiRequest<?> request,
                                                         ICancelToken cancelToken) {
            calls++;
            assertEquals(1, adapter.getActiveCount(instance),
                    "C8-001 wiring: in-flight call must hold active count == 1");
            return CompletableFuture.completedFuture(ApiResponse.buildSuccess("ok"));
        }

        @Override
        public ApiResponse<?> call(String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken) {
            calls++;
            assertEquals(1, adapter.getActiveCount(instance),
                    "C8-001 wiring: in-flight call must hold active count == 1");
            return ApiResponse.buildSuccess("ok");
        }
    }
}
