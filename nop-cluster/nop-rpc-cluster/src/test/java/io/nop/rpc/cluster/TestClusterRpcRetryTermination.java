package io.nop.rpc.cluster;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopConnectException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.ICancelToken;
import io.nop.cluster.chooser.IServerChooser;
import io.nop.cluster.discovery.ServiceInstance;
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
}
