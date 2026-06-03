package io.nop.job.service.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.ICancelToken;
import io.nop.job.api.NopJobApiConstants;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.JobFireResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
    void testFrameworkHeadersInjectJobIdentity() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        IJobExecutionContext ctx = new TestJobContext(Map.of(
                "serviceName", "myService"
        ));

        invoker.invokeAsync(ctx).toCompletableFuture().join();

        Map<String, Object> headers = mockRpc.getLastRequest().getHeaders();
        assertNotNull(headers);
        assertEquals("testJob", headers.get(NopJobApiConstants.HEADER_JOB_NAME));
        assertEquals("testGroup", headers.get(NopJobApiConstants.HEADER_JOB_GROUP));
    }

    @Test
    void testFrameworkHeadersInjectExecutionIds() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("jobFireId", "fire-001");
        attrs.put("jobTaskId", "task-001");
        TestJobContext ctx = new TestJobContext(Map.of("serviceName", "myService"), attrs);

        invoker.invokeAsync(ctx).toCompletableFuture().join();

        Map<String, Object> headers = mockRpc.getLastRequest().getHeaders();
        assertNotNull(headers);
        assertEquals("fire-001", headers.get(NopJobApiConstants.HEADER_JOB_FIRE_ID));
        assertEquals("task-001", headers.get(NopJobApiConstants.HEADER_JOB_TASK_ID));
    }

    @Test
    void testFrameworkHeadersInjectShardingInfo() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("shardingIndex", 2);
        attrs.put("shardingTotal", 5);
        TestJobContext ctx = new TestJobContext(Map.of("serviceName", "myService"), attrs);

        invoker.invokeAsync(ctx).toCompletableFuture().join();

        Map<String, Object> headers = mockRpc.getLastRequest().getHeaders();
        assertNotNull(headers);
        assertEquals(2, headers.get(NopJobApiConstants.HEADER_JOB_SHARDING_INDEX));
        assertEquals(5, headers.get(NopJobApiConstants.HEADER_JOB_SHARDING_TOTAL));
    }

    @Test
    void testUserHeadersOverrideFramework() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        // User provides a custom job name via headers — should override the framework default
        IJobExecutionContext ctx = new TestJobContext(Map.of(
                "serviceName", "myService",
                "headers", Map.of(NopJobApiConstants.HEADER_JOB_NAME, "override-job")
        ));

        invoker.invokeAsync(ctx).toCompletableFuture().join();

        Map<String, Object> headers = mockRpc.getLastRequest().getHeaders();
        assertNotNull(headers);
        assertEquals("override-job", headers.get(NopJobApiConstants.HEADER_JOB_NAME));
    }

    @Test
    void testAutoBuildDataWhenNotProvided() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        IJobExecutionContext ctx = new TestJobContext(Map.of(
                "serviceName", "myService"
        ));

        invoker.invokeAsync(ctx).toCompletableFuture().join();

        // Body should be empty when no data is configured
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) mockRpc.getLastRequest().getData();
        assertNotNull(data);
        assertTrue(data.isEmpty());

        // Job identity moves to headers
        Map<String, Object> headers = mockRpc.getLastRequest().getHeaders();
        assertNotNull(headers);
        assertEquals("testJob", headers.get(NopJobApiConstants.HEADER_JOB_NAME));
        assertEquals("testGroup", headers.get(NopJobApiConstants.HEADER_JOB_GROUP));
    }

    @Test
    void testTimeoutHeaderFromAttributes() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("timeoutSeconds", 30);
        TestJobContext ctx = new TestJobContext(Map.of("serviceName", "myService"), attrs);

        invoker.invokeAsync(ctx).toCompletableFuture().join();

        Map<String, Object> headers = mockRpc.getLastRequest().getHeaders();
        assertNotNull(headers);
        assertEquals(30_000L, headers.get("nop-timeout"));
    }

    @Test
    void testTimeoutHeaderDefaultWhenZero() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("timeoutSeconds", 0);
        TestJobContext ctx = new TestJobContext(Map.of("serviceName", "myService"), attrs);

        invoker.invokeAsync(ctx).toCompletableFuture().join();

        Map<String, Object> headers = mockRpc.getLastRequest().getHeaders();
        assertNotNull(headers);
        assertEquals(60_000L, headers.get("nop-timeout"));
    }

    @Test
    void testTimeoutHeaderDefaultWhenNoAttributes() {
        mockRpc.setResponse(ApiResponse.success("ok"));
        TestJobContext ctx = new TestJobContext(Map.of("serviceName", "myService"));

        invoker.invokeAsync(ctx).toCompletableFuture().join();

        Map<String, Object> headers = mockRpc.getLastRequest().getHeaders();
        assertNotNull(headers);
        assertEquals(60_000L, headers.get("nop-timeout"));
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
        private final Map<String, Object> attributes;

        TestJobContext(Map<String, Object> jobParams) {
            this(jobParams, new HashMap<>());
        }

        TestJobContext(Map<String, Object> jobParams, Map<String, Object> attributes) {
            this.jobParams = jobParams;
            this.attributes = attributes;
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
            return attributes;
        }

        @Override
        public void setAttributes(Map<String, Object> attributes) {
            this.attributes.clear();
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
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
