package io.nop.job.retry.adapter;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.job.api.retry.JobFireFailedEvent;
import io.nop.retry.api.IRetryEngine;
import io.nop.retry.api.IRetryTask;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestNopRetryJobRetryBridge {

    private NopRetryJobRetryBridge bridge;
    private MockRetryEngine retryEngine;

    @BeforeEach
    void setUp() {
        bridge = new NopRetryJobRetryBridge();
        retryEngine = new MockRetryEngine();
        bridge.setRetryEngine(retryEngine);
    }

    @Test
    void testOnFireFailed_submitsRetryTask() throws InterruptedException {
        JobFireFailedEvent event = new JobFireFailedEvent(
                "fire-1", "schedule-1", "policy-1",
                "ns-1", "group-1", "testJob", "test",
                "ERR_TIMEOUT", "Task timed out");

        String result = bridge.onFireFailed(event);

        assertNull(result, "onFireFailed should return null, not the jobFireId");

        retryEngine.awaitTask(2, TimeUnit.SECONDS);

        assertEquals(1, retryEngine.getSubmittedTasks().size());
        MockRetryTask task = retryEngine.getSubmittedTasks().get(0);
        assertEquals("NopJobService", task.serviceName);
        assertEquals("fireJob", task.serviceMethod);
        assertEquals("policy-1", task.policyId);
        assertEquals("fire-1", task.idempotentId);
        assertEquals("ns-1", task.namespaceId);
        assertEquals("group-1", task.groupId);
    }

    @Test
    void testOnFireFailed_noRetryEngine_returnsNull() {
        bridge.setRetryEngine(null);

        JobFireFailedEvent event = new JobFireFailedEvent(
                "fire-1", "schedule-1", null,
                null, null, "testJob", "test",
                "ERR", "msg");

        String result = bridge.onFireFailed(event);
        assertNull(result);
    }

    @Test
    void testOnFireFailed_includesErrorInfo() throws InterruptedException {
        JobFireFailedEvent event = new JobFireFailedEvent(
                "fire-2", "schedule-2", null,
                null, null, "myJob", "rpc",
                "ERR_503", "Service unavailable");

        bridge.onFireFailed(event);

        retryEngine.awaitTask(2, TimeUnit.SECONDS);

        assertEquals(1, retryEngine.getSubmittedTasks().size());
        MockRetryTask task = retryEngine.getSubmittedTasks().get(0);
        assertNotNull(task.lastRequest);
        assertNotNull(task.lastRequest.getData());
    }

    static class MockRetryEngine implements IRetryEngine {
        private final List<MockRetryTask> submittedTasks = new ArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(1);

        List<MockRetryTask> getSubmittedTasks() {
            return submittedTasks;
        }

        void awaitTask(long timeout, TimeUnit unit) throws InterruptedException {
            latch.await(timeout, unit);
        }

        @Override
        public IRetryTask newRetryTask(String serviceName, String serviceMethod) {
            MockRetryTask task = new MockRetryTask(serviceName, serviceMethod, this);
            return task;
        }

        void onTaskSubmitted(MockRetryTask task) {
            submittedTasks.add(task);
            latch.countDown();
        }

        @Override
        public CompletionStage<ApiResponse<?>> retryFromDeadLetter(String deadLetterId, ICancelToken cancelToken) {
            return CompletableFuture.completedStage(ApiResponse.success(null));
        }

        @Override
        public void pause(String recordId) {}

        @Override
        public void resume(String recordId) {}
    }

    static class MockRetryTask implements IRetryTask {
        final String serviceName;
        final String serviceMethod;
        final MockRetryEngine engine;

        String executorId;
        String policyId;
        String idempotentId;
        String callbackService;
        String callbackMethod;
        String namespaceId;
        String groupId;
        ApiRequest<?> lastRequest;

        MockRetryTask(String serviceName, String serviceMethod, MockRetryEngine engine) {
            this.serviceName = serviceName;
            this.serviceMethod = serviceMethod;
            this.engine = engine;
        }

        @Override public String getServiceName() { return serviceName; }
        @Override public String getServiceMethod() { return serviceMethod; }
        @Override public String getExecutorId() { return executorId; }
        @Override public IRetryTask withExecutorId(String executorId) { this.executorId = executorId; return this; }
        @Override public String getPolicyId() { return policyId; }
        @Override public IRetryTask withPolicyId(String policyId) { this.policyId = policyId; return this; }
        @Override public String getIdempotentId() { return idempotentId; }
        @Override public IRetryTask withIdempotentId(String idempotentId) { this.idempotentId = idempotentId; return this; }
        @Override public String getCallbackService() { return callbackService; }
        @Override public String getCallbackMethod() { return callbackMethod; }
        @Override public IRetryTask withCallback(String callbackService, String callbackMethod) { this.callbackService = callbackService; this.callbackMethod = callbackMethod; return this; }
        @Override public String getNamespaceId() { return namespaceId; }
        @Override public IRetryTask withNamespaceId(String namespaceId) { this.namespaceId = namespaceId; return this; }
        @Override public String getGroupId() { return groupId; }
        @Override public IRetryTask withGroupId(String groupId) { this.groupId = groupId; return this; }

        @Override
        public CompletionStage<ApiResponse<?>> callAsync(ApiRequest<?> request, ICancelToken cancelToken) {
            this.lastRequest = request;
            engine.onTaskSubmitted(this);
            return CompletableFuture.completedStage(ApiResponse.success(null));
        }
    }
}
