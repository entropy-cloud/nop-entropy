package io.nop.task.step;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.builder.TaskStepBuilder;
import io.nop.task.model.SimpleTaskStepModel;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 364 Phase 4 [维度02-01]：`<simple bean="X">` 步骤 per-step 包装隔离。
 *
 * <p>缺陷（修复前）：buildSimpleStep 返回容器共享单例，initAbstractStep 把各步骤模型的
 * location/inputs/outputs/concurrent/persistVars 原地覆写到共享对象——后构建覆盖先构建。
 * 修复：容器 bean 被包装为 per-step 的 {@link SimpleBeanTaskStep}，模型配置写在包装上。
 */
public class TestSimpleBeanStepIsolation {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        StaticBeanContainer container = new StaticBeanContainer();
        container.registerBean("probe", new ProbeStep());
        BeanContainer.registerInstance(container);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void sharedBean_perStepConfigIsolated_underlyingUntouched() {
        TaskStepBuilder builder = new TaskStepBuilder();

        SimpleTaskStepModel m1 = new SimpleTaskStepModel();
        m1.setBean("probe");
        m1.setConcurrent(true);
        m1.setPersistVars(Set.of("x"));

        SimpleTaskStepModel m2 = new SimpleTaskStepModel();
        m2.setBean("probe");

        AbstractTaskStep s1 = builder.buildRawStep(m1);
        AbstractTaskStep s2 = builder.buildRawStep(m2);

        assertTrue(s1 instanceof SimpleBeanTaskStep, "simple steps must be wrapped per-step (02-01)");
        assertNotSame(s1, s2, "two <simple> steps sharing a bean must not share the step object");
        assertSame(((SimpleBeanTaskStep) s1).getBean(), ((SimpleBeanTaskStep) s2).getBean(),
                "both wrappers delegate to the same container bean");

        // 各自模型配置独立生效（修复前：s2 的并发/持久化配置被 s1 覆写）
        assertTrue(s1.isConcurrent(), "step1 declares concurrent=true");
        assertFalse(s2.isConcurrent(), "step2 must keep its own concurrent=false (pre-fix: clobbered to true)");
        assertEquals(Set.of("x"), s1.getPersistVars(), "step1 persistVars must apply");
        assertNull(s2.getPersistVars(), "step2 must not inherit step1's persistVars (pre-fix: clobbered)");

        // 共享 bean 自身不再被改写
        ProbeStep probe = (ProbeStep) ((SimpleBeanTaskStep) s1).getBean();
        assertNull(probe.getPersistVars(), "container singleton must not be mutated by initAbstractStep");
        assertFalse(probe.isConcurrent(), "container singleton must not be mutated by initAbstractStep");
    }

    /** 测试用 probe step：读取输入 x 并加一返回。 */
    public static class ProbeStep extends AbstractTaskStep {
        @Nonnull
        @Override
        public TaskStepReturn execute(ITaskStepRuntime stepRt) {
            Object x = stepRt.getValue("x");
            int v = x == null ? 0 : Integer.parseInt(x.toString());
            return TaskStepReturn.of(null, v + 1);
        }
    }
}
