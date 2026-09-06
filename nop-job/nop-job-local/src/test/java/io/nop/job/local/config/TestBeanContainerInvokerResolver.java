package io.nop.job.local.config;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.JobFireResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;

import static io.nop.job.core.JobCoreErrors.ERR_JOB_INVOKER_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 [P3-10]: {@link BeanContainerInvokerResolver} 对 bean 类型不符的处理。
 * 此前直接强转，配错 bean 类型时报无上下文的 ClassCastException；
 * 现对齐 DefaultJobInvokerResolver：抛带 beanName 上下文的 ERR_JOB_INVOKER_NOT_FOUND，
 * bean 不存在仍返回 null（由 LocalJobScheduler.addJob 处理，保持既有契约）。
 */
public class TestBeanContainerInvokerResolver {

    private IBeanContainer originalContainer;
    private BeanContainerInvokerResolver resolver;

    static class WrongTypedBean {
    }

    static class StubInvoker implements IJobInvoker {
        @Override
        public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
            return null;
        }

        @Override
        public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        originalContainer = BeanContainer.isInitialized() ? BeanContainer.instance() : null;
        resolver = new BeanContainerInvokerResolver();
    }

    @AfterEach
    void tearDown() {
        if (originalContainer != null) {
            BeanContainer.registerInstance(originalContainer);
        }
    }

    private void setupContainer(Object bean) {
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("nopJobInvoker_myJob", bean);
        BeanContainer.registerInstance(container);
    }

    @Test
    void testWrongTypedBeanThrowsSemanticNopException() {
        setupContainer(new WrongTypedBean());

        NopException e = assertThrows(NopException.class, () -> resolver.apply("myJob"));
        assertEquals(ERR_JOB_INVOKER_NOT_FOUND.getErrorCode(), e.getErrorCode());
        assertEquals("nopJobInvoker_myJob", e.getParam("beanName"));
        assertEquals(WrongTypedBean.class.getName(), e.getParam("actualType"));
    }

    @Test
    void testCorrectTypedBeanReturned() {
        StubInvoker invoker = new StubInvoker();
        setupContainer(invoker);
        assertEquals(invoker, resolver.apply("myJob"));
    }

    @Test
    void testMissingBeanStillReturnsNull() {
        setupContainer(new WrongTypedBean());
        assertNull(resolver.apply("noSuchJob"),
                "bean 不存在时保持返回 null 的既有契约（由 LocalJobScheduler.addJob 抛 ERR_JOB_BEAN_NOT_FOUND）");
        assertTrue(BeanContainer.isInitialized());
        assertNotNull(BeanContainer.instance());
    }
}
