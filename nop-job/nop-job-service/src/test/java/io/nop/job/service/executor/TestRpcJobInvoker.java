package io.nop.job.service.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ICancelToken;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.JobFireResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

public class TestRpcJobInvoker {
    private RpcJobInvoker invoker;
    private MockRpcServiceInvoker mockRpc;

    @BeforeEach
    void setUp() {
        invoker = new RpcJobInvoker();
        mockRpc = new MockRpcServiceInvoker();
        invoker.setRpcServiceInvoker(mockRpc);
    }

    @Test
    void testInvokeSuccess() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        IJobExecutionContext ctx = new TestJobContext(Map.of(
                "serviceName", "myService",
                "serviceMethod", "doWork",
                "data", Map.of("key", "value")
        ));

        JobFireResult result = invoker.invokeAsync(ctx).toCompletableFuture().join();
        assertFalse(result.isErrorResult());
        assertFalse(result.isCompleted());
    }

    @Test
    void testInvokeError() {
        ApiResponse<?> errorResponse = new ApiResponse<>();
        errorResponse.setStatus(-1);
        errorResponse.setCode("INTERNAL_ERROR");
        errorResponse.setMsg("Something went wrong");
        mockRpc.setResponse(errorResponse);

        IJobExecutionContext ctx = new TestJobContext(Map.of(
                "serviceName", "myService"
        ));

        JobFireResult result = invoker.invokeAsync(ctx).toCompletableFuture().join();
        assertTrue(result.isErrorResult());
        assertTrue(result.isCompleted());
        assertNotNull(result.getError());
    }

    @Test
    void testCancelSuccess() {
        mockRpc.setResponse(ApiResponse.success(true));
        IJobExecutionContext ctx = new TestJobContext(Map.of(
                "serviceName", "myService"
        ));

        Boolean result = invoker.cancelAsync(ctx).toCompletableFuture().join();
        assertTrue(result);
    }

    @Test
    void testCancelFailure() {
        ApiResponse<?> errorResponse = new ApiResponse<>();
        errorResponse.setStatus(-1);
        errorResponse.setCode("BAD_REQUEST");
        errorResponse.setMsg("Failed");
        mockRpc.setResponse(errorResponse);

        IJobExecutionContext ctx = new TestJobContext(Map.of(
                "serviceName", "myService"
        ));

        Boolean result = invoker.cancelAsync(ctx).toCompletableFuture().join();
        assertFalse(result);
    }

    @Test
    void testMissingServiceNameThrows() {
        IJobExecutionContext ctx = new TestJobContext(Map.of());
        assertThrows(NopException.class, () -> invoker.invokeAsync(ctx).toCompletableFuture().join());
    }

    @Test
    void testCustomHeadersPassed() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        IJobExecutionContext ctx = new TestJobContext(Map.of(
                "serviceName", "myService",
                "headers", Map.of("nop-svc-target-host", "10.0.0.1:8080")
        ));

        invoker.invokeAsync(ctx).toCompletableFuture().join();

        assertNotNull(mockRpc.getLastRequest().getHeaders());
        assertEquals("10.0.0.1:8080", mockRpc.getLastRequest().getHeaders().get("nop-svc-target-host"));
    }

    @Test
    void testAutoBuildDataWhenNotProvided() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        IJobExecutionContext ctx = new TestJobContext(Map.of(
                "serviceName", "myService"
        ));

        invoker.invokeAsync(ctx).toCompletableFuture().join();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) mockRpc.getLastRequest().getData();
        assertNotNull(data);
        assertEquals("testJob", data.get("jobName"));
    }

    // --- Mock and Stub classes ---

    private static class MockRpcServiceInvoker implements IRpcServiceInvoker {
        private ApiResponse<?> response;
        private ApiRequest<?> lastRequest;

        void setResponse(ApiResponse<?> response) {
            this.response = response;
        }

        ApiRequest<?> getLastRequest() {
            return lastRequest;
        }

        @Override
        public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod,
                                                           ApiRequest<?> request, ICancelToken cancelToken) {
            this.lastRequest = request;
            return CompletableFuture.completedFuture(response);
        }
    }

    private static class TestJobContext implements IJobExecutionContext {
        private final Map<String, Object> jobParams;

        TestJobContext(Map<String, Object> jobParams) {
            this.jobParams = jobParams;
        }

        @Override
        public Map<String, Object> getJobParams() {
            return jobParams;
        }

        @Override
        public String getJobDefId() {
            return "testDefId";
        }

        @Override
        public String getJobName() {
            return "testJob";
        }

        @Override
        public long getJobVersion() {
            return 1;
        }

        @Override
        public String getJobGroup() {
            return "testGroup";
        }

        @Override
        public String getInstanceId() {
            return "inst-1";
        }

        @Override
        public long getExecCount() {
            return 1;
        }

        @Override
        public long getScheduledExecTime() {
            return System.currentTimeMillis();
        }

        @Override
        public long getExecBeginTime() {
            return System.currentTimeMillis();
        }

        @Override
        public long getExecEndTime() {
            return 0;
        }

        @Override
        public boolean isOnceTask() {
            return false;
        }

        @Override
        public boolean isManualFire() {
            return false;
        }

        @Override
        public String getFiredBy() {
            return "system";
        }

        @Override
        public long getChangeVersion() {
            return 0;
        }

        @Override
        public long getExecFailCount() {
            return 0;
        }

        @Override
        public int getInstanceStatus() {
            return 0;
        }

        @Override
        public io.nop.api.core.beans.ErrorBean getExecError() {
            return null;
        }

        @Override
        public String getLastInstanceId() {
            return null;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return null;
        }

        @Override
        public void setAttributes(Map<String, Object> attributes) {
        }

        @Override
        public long getMinScheduleTime() {
            return 0;
        }

        @Override
        public long getMaxScheduleTime() {
            return 0;
        }

        @Override
        public long getMaxExecutionCount() {
            return 0;
        }

        @Override
        public long getMaxFailedCount() {
            return 0;
        }

        @Override
        public boolean isJobFinished() {
            return false;
        }

        @Override
        public boolean isInstanceRunning() {
            return true;
        }

        @Override
        public boolean isScheduleEnabled() {
            return true;
        }
    }
}
