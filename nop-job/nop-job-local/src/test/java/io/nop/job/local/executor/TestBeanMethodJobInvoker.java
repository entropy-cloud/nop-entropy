package io.nop.job.local.executor;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.JobFireResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

public class TestBeanMethodJobInvoker {

    private IBeanContainer originalContainer;
    private BeanMethodJobInvoker invoker;
    private TestService testService;

    static class TestService {
        private Map<String, Object> lastParams;
        private Map<String, Object> lastCancelParams;
        private boolean noArgCalled;
        private boolean cancelNoArgCalled;
        private boolean customCancelCalled;

        public void execute(Map<String, Object> params) {
            this.lastParams = params;
        }

        public void execute() {
            this.noArgCalled = true;
        }

        public void alwaysFail() {
            throw new RuntimeException("intentional failure");
        }

        public void cancel() {
            this.cancelNoArgCalled = true;
        }

        public void cancel(Map<String, Object> params) {
            this.lastCancelParams = params;
        }

        public boolean customCancel() {
            this.customCancelCalled = true;
            return true;
        }

        public void cancelFails() {
            throw new RuntimeException("intentional cancel failure");
        }

        public CompletionStage<Boolean> cancelAsync() {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        Map<String, Object> getLastParams() {
            return lastParams;
        }

        Map<String, Object> getLastCancelParams() {
            return lastCancelParams;
        }

        boolean isNoArgCalled() {
            return noArgCalled;
        }

        boolean isCancelNoArgCalled() {
            return cancelNoArgCalled;
        }

        boolean isCustomCancelCalled() {
            return customCancelCalled;
        }
    }

    @BeforeEach
    void setUp() {
        originalContainer = BeanContainer.isInitialized() ? BeanContainer.instance() : null;
        testService = new TestService();
        invoker = new BeanMethodJobInvoker();
    }

    @AfterEach
    void tearDown() {
        if (originalContainer != null) {
            BeanContainer.registerInstance(originalContainer);
        }
    }

    private void setupContainer() {
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("testService", testService);
        BeanContainer.registerInstance(container);
    }

    @Test
    void testInvokeWithMapParam() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");
        jobParams.put("methodName", "execute");
        jobParams.put("customKey", "customValue");

        CompletionStage<JobFireResult> result = invoker.invokeAsync(new TestJobContext(jobParams));
        JobFireResult fireResult = result.toCompletableFuture().join();

        assertFalse(fireResult.isErrorResult());
        assertEquals("customValue", testService.getLastParams().get("customKey"));
        assertNull(testService.getLastParams().get("beanName"));
        assertNull(testService.getLastParams().get("methodName"));
    }

    @Test
    void testInvokeWithNoArgMethod() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");
        jobParams.put("methodName", "execute");

        CompletionStage<JobFireResult> result = invoker.invokeAsync(new TestJobContext(jobParams));
        JobFireResult fireResult = result.toCompletableFuture().join();

        assertFalse(fireResult.isErrorResult());
        assertTrue(testService.isNoArgCalled());
    }

    @Test
    void testBeanNotFound() {
        StaticBeanContainer container = new StaticBeanContainer();
        BeanContainer.registerInstance(container);

        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "nonExistentBean");
        jobParams.put("methodName", "execute");

        assertThrows(NopException.class, () -> invoker.invokeAsync(new TestJobContext(jobParams)));
    }

    @Test
    void testMethodNotFound() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");
        jobParams.put("methodName", "nonExistentMethod");

        assertThrows(NopException.class, () -> invoker.invokeAsync(new TestJobContext(jobParams)));
    }

    @Test
    void testMissingBeanName() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("methodName", "execute");

        assertThrows(NopException.class, () -> invoker.invokeAsync(new TestJobContext(jobParams)));
    }

    @Test
    void testMissingMethodNameDefaultsToExecute() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");

        CompletionStage<JobFireResult> result = invoker.invokeAsync(new TestJobContext(jobParams));
        JobFireResult fireResult = result.toCompletableFuture().join();

        assertFalse(fireResult.isErrorResult());
        assertTrue(testService.isNoArgCalled());
    }

    @Test
    void testMethodThrowsException() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");
        jobParams.put("methodName", "alwaysFail");

        CompletionStage<JobFireResult> result = invoker.invokeAsync(new TestJobContext(jobParams));
        JobFireResult fireResult = result.toCompletableFuture().join();

        assertTrue(fireResult.isErrorResult());
        assertTrue(fireResult.isCompleted());
    }

    @Test
    void testCancelAsync_emptyParams_returnsTrue() {
        setupContainer();
        CompletionStage<Boolean> result = invoker.cancelAsync(new TestJobContext(Map.of()));
        assertTrue(result.toCompletableFuture().join());
    }

    @Test
    void testCancelAsync_beanMissing_returnsTrue() {
        StaticBeanContainer container = new StaticBeanContainer();
        BeanContainer.registerInstance(container);

        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "nonExistentBean");

        CompletionStage<Boolean> result = invoker.cancelAsync(new TestJobContext(jobParams));
        assertTrue(result.toCompletableFuture().join());
    }

    @Test
    void testCancelAsync_noCancelMethod_returnsTrue() {
        // TestService2 has execute() but no cancel method
        StaticBeanContainer container = new StaticBeanContainer();
        Object service = new Object(); // java.lang.Object has no execute/cancel
        container.registerBean("plainBean", service);
        BeanContainer.registerInstance(container);

        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "plainBean");

        CompletionStage<Boolean> result = invoker.cancelAsync(new TestJobContext(jobParams));
        assertTrue(result.toCompletableFuture().join());
    }

    @Test
    void testCancelAsync_callsNoArgCancelMethod() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");

        CompletionStage<Boolean> result = invoker.cancelAsync(new TestJobContext(jobParams));
        assertTrue(result.toCompletableFuture().join());
        assertTrue(testService.isCancelNoArgCalled());
    }

    @Test
    void testCancelAsync_callsMapArgCancelMethodWhenParamsPresent() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");
        jobParams.put("customCancelKey", "customCancelValue");

        CompletionStage<Boolean> result = invoker.cancelAsync(new TestJobContext(jobParams));
        assertTrue(result.toCompletableFuture().join());
        assertNotNull(testService.getLastCancelParams());
        assertEquals("customCancelValue", testService.getLastCancelParams().get("customCancelKey"));
        assertNull(testService.getLastCancelParams().get("beanName"));
    }

    @Test
    void testCancelAsync_customCancelMethodName() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");
        jobParams.put("cancelMethodName", "customCancel");

        CompletionStage<Boolean> result = invoker.cancelAsync(new TestJobContext(jobParams));
        assertTrue(result.toCompletableFuture().join());
        assertTrue(testService.isCustomCancelCalled());
    }

    @Test
    void testCancelAsync_methodThrows_returnsTrue() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");
        jobParams.put("cancelMethodName", "cancelFails");

        CompletionStage<Boolean> result = invoker.cancelAsync(new TestJobContext(jobParams));
        assertTrue(result.toCompletableFuture().join());
    }

    @Test
    void testCancelAsync_asyncResultUnwrappedToBoolean() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");
        jobParams.put("cancelMethodName", "cancelAsync");

        CompletionStage<Boolean> result = invoker.cancelAsync(new TestJobContext(jobParams));
        assertTrue(result.toCompletableFuture().join());
    }

    @Test
    void testExtractMethodParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("beanName", "myBean");
        params.put("methodName", "myMethod");
        params.put("key1", "value1");
        params.put("key2", 42);

        Map<String, Object> extracted = invoker.extractMethodParams(params);
        assertNull(extracted.get("beanName"));
        assertNull(extracted.get("methodName"));
        assertEquals("value1", extracted.get("key1"));
        assertEquals(42, extracted.get("key2"));
    }

    @Test
    void testNullJobParams() {
        setupContainer();
        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put("beanName", "testService");
        jobParams.put("methodName", "execute");

        TestJobContext ctx = new TestJobContext(jobParams) {
            @Override
            public Map<String, Object> getJobParams() {
                return null;
            }
        };

        assertThrows(NopException.class, () -> invoker.invokeAsync(ctx));
    }

    static class TestJobContext implements IJobExecutionContext {
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
