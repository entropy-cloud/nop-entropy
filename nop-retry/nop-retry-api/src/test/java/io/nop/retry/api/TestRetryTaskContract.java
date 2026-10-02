package io.nop.retry.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcCall;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 结构性验证 IRetryTask 流式构造契约：with* 方法返回同一实例且 getter 读回一致，
 * 任务可经 IRpcCall 接口发起调用；IRetryEngine 的 pause/resume 契约可被实现方落位。
 */
public class TestRetryTaskContract {

    static class TaskImpl implements IRetryTask {
        String serviceName;
        String serviceMethod;
        String executorName;
        String policyId;
        String idempotentId;
        String namespaceId;
        String groupId;

        @Override
        public String getServiceName() {
            return serviceName;
        }

        @Override
        public String getServiceMethod() {
            return serviceMethod;
        }

        @Override
        public String getExecutorName() {
            return executorName;
        }

        @Override
        public IRetryTask withExecutorName(String executorName) {
            this.executorName = executorName;
            return this;
        }

        @Override
        public String getPolicyId() {
            return policyId;
        }

        @Override
        public IRetryTask withPolicyId(String policyId) {
            this.policyId = policyId;
            return this;
        }

        @Override
        public String getIdempotentId() {
            return idempotentId;
        }

        @Override
        public IRetryTask withIdempotentId(String idempotentId) {
            this.idempotentId = idempotentId;
            return this;
        }

        @Override
        public String getNamespaceId() {
            return namespaceId;
        }

        @Override
        public IRetryTask withNamespaceId(String namespaceId) {
            this.namespaceId = namespaceId;
            return this;
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public IRetryTask withGroupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        @Override
        public CompletionStage<ApiResponse<?>> callAsync(ApiRequest<?> request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(ApiResponse.success("ok"));
        }
    }

    @Test
    public void testFluentWithersReturnSameInstance() {
        TaskImpl task = new TaskImpl();

        assertSame(task, task.withExecutorName("exec-1"));
        assertSame(task, task.withPolicyId("policy-1"));
        assertSame(task, task.withIdempotentId("idem-1"));
        assertSame(task, task.withNamespaceId("ns-1"));
        assertSame(task, task.withGroupId("grp-1"));

        assertEquals("exec-1", task.getExecutorName());
        assertEquals("policy-1", task.getPolicyId());
        assertEquals("idem-1", task.getIdempotentId());
        assertEquals("ns-1", task.getNamespaceId());
        assertEquals("grp-1", task.getGroupId());
    }

    @Test
    public void testTaskTargetUnchangedByWithers() {
        TaskImpl task = new TaskImpl();
        task.serviceName = "svc";
        task.serviceMethod = "doIt";

        task.withPolicyId("policy-9");
        assertEquals("svc", task.getServiceName(), "with* must not mutate the rpc target");
        assertEquals("doIt", task.getServiceMethod());
    }

    @Test
    public void testTaskCallableThroughIRpcCall() throws Exception {
        IRetryTask task = new TaskImpl();
        ApiResponse<?> response = ((IRpcCall) task).callAsync(new ApiRequest<>(), null)
                .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, response.getStatus(), "IRetryTask must stay callable as IRpcCall");
    }

    static class EngineStub implements IRetryEngine {
        String lastPaused;
        String lastResumed;

        @Override
        public IRetryTask newRetryTask(String serviceName, String serviceMethod) {
            return null;
        }

        @Override
        public CompletionStage<ApiResponse<?>> retryFromDeadLetter(String deadLetterId, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(ApiResponse.success(deadLetterId));
        }

        @Override
        public void pause(String recordId) {
            lastPaused = recordId;
        }

        @Override
        public void resume(String recordId) {
            lastResumed = recordId;
        }
    }

    @Test
    public void testEnginePauseResumeContract() throws Exception {
        EngineStub engine = new EngineStub();
        engine.pause("record-1");
        engine.resume("record-1");
        assertEquals("record-1", engine.lastPaused);
        assertEquals("record-1", engine.lastResumed);

        ApiResponse<?> ret = engine.retryFromDeadLetter("dead-1", null)
                .toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("dead-1", ret.getData(), "retryFromDeadLetter must echo the dead letter id");
    }
}
