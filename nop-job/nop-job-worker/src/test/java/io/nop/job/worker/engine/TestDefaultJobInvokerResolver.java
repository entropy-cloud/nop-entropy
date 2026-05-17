package io.nop.job.worker.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import io.nop.job.dao.entity.NopJobFire;
import io.nop.job.dao.entity.NopJobSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

public class TestDefaultJobInvokerResolver {
    private IBeanContainer originalContainer;

    @BeforeEach
    void saveContainer() {
        originalContainer = BeanContainer.isInitialized() ? BeanContainer.instance() : null;
    }

    @AfterEach
    void restoreContainer() {
        if (originalContainer != null) {
            BeanContainer.registerInstance(originalContainer);
        }
    }

    private void setupContainer(String beanName, Object bean) {
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean(beanName, bean);
        BeanContainer.registerInstance(container);
    }

    private NopJobSchedule buildSchedule(String executorKind) {
        NopJobSchedule schedule = new NopJobSchedule();
        schedule.setJobName("testJob");
        schedule.setGroupId("testGroup");
        schedule.setExecutorKind(executorKind);
        return schedule;
    }

    private NopJobFire buildFire(String executorKind) {
        NopJobFire fire = new NopJobFire();
        if (executorKind != null) {
            fire.setExecutorKind(executorKind);
        }
        return fire;
    }

    @Test
    void testResolveWithExecutorKind() {
        setupContainer("nopJobInvoker_test", new StubJobInvoker());
        DefaultJobInvokerResolver resolver = new DefaultJobInvokerResolver();
        NopJobSchedule schedule = buildSchedule("test");
        NopJobFire fire = buildFire(null);

        IJobInvoker result = resolver.resolveInvoker(schedule, fire);
        assertNotNull(result);
    }

    @Test
    void testResolveFromFireExecutorKind() {
        setupContainer("nopJobInvoker_myRpc", new StubJobInvoker());
        DefaultJobInvokerResolver resolver = new DefaultJobInvokerResolver();
        NopJobSchedule schedule = buildSchedule("test");
        NopJobFire fire = buildFire("myRpc");

        IJobInvoker result = resolver.resolveInvoker(schedule, fire);
        assertNotNull(result);
    }

    @Test
    void testEmptyExecutorKindThrows() {
        setupContainer("nopJobInvoker_test", new StubJobInvoker());
        DefaultJobInvokerResolver resolver = new DefaultJobInvokerResolver();
        NopJobSchedule schedule = buildSchedule("");
        NopJobFire fire = buildFire(null);

        assertThrows(NopException.class, () -> resolver.resolveInvoker(schedule, fire));
    }

    @Test
    void testNullExecutorKindThrows() {
        setupContainer("nopJobInvoker_test", new StubJobInvoker());
        DefaultJobInvokerResolver resolver = new DefaultJobInvokerResolver();
        NopJobSchedule schedule = buildSchedule(null);
        NopJobFire fire = buildFire(null);

        assertThrows(NopException.class, () -> resolver.resolveInvoker(schedule, fire));
    }

    @Test
    void testNotFoundThrows() {
        setupContainer("nopJobInvoker_test", new StubJobInvoker());
        DefaultJobInvokerResolver resolver = new DefaultJobInvokerResolver();
        NopJobSchedule schedule = buildSchedule("nonexistent");
        NopJobFire fire = buildFire(null);

        NopException ex = assertThrows(NopException.class, () -> resolver.resolveInvoker(schedule, fire));
        assertEquals("JOB_INVOKER_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void testBeanIsNotInvokerThrows() {
        setupContainer("nopJobInvoker_wrong", "not an invoker");
        DefaultJobInvokerResolver resolver = new DefaultJobInvokerResolver();
        NopJobSchedule schedule = buildSchedule("wrong");
        NopJobFire fire = buildFire(null);

        NopException ex = assertThrows(NopException.class, () -> resolver.resolveInvoker(schedule, fire));
        assertEquals("JOB_INVOKER_NOT_FOUND", ex.getErrorCode());
    }

    private static class StubJobInvoker implements IJobInvoker {
        @Override
        public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
            return CompletableFuture.completedFuture(JobFireResult.CONTINUE(0L));
        }

        @Override
        public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }
    }
}
