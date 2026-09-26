package io.nop.task.step;

import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;

/**
 * plan 364 [维度02-01]：`<simple bean="X">` 步骤的 per-step 包装。
 *
 * <p>缺陷（修复前）：{@code TaskStepBuilder.buildSimpleStep} 返回 IoC 容器的共享单例，
 * {@code initAbstractStep} 随后将每个步骤模型的 location/inputs/outputs/concurrent/persistVars
 * 原地覆写到该共享对象上——多个步骤引用同一 bean 时后构建覆盖先构建，运行期
 * persistVars/concurrent 串扰直接引入跨步骤数据竞争。
 *
 * <p>修复：容器 bean 只作为执行体被本包装委托（execute 转发），模型侧配置落在包装自身
 * （本类继承 AbstractTaskStep 的字段），由 {@code initAbstractStep} 按各步骤模型独立写入。
 * 共享 bean 自身不再被改写。
 */
public class SimpleBeanTaskStep extends AbstractTaskStep {
    private final AbstractTaskStep bean;

    public SimpleBeanTaskStep(AbstractTaskStep bean) {
        this.bean = bean;
    }

    public AbstractTaskStep getBean() {
        return bean;
    }

    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        return bean.execute(stepRt);
    }
}
