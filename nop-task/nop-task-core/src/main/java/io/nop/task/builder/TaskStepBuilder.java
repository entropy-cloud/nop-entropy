package io.nop.task.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.IEnhancedTaskStep;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepChooseDecider;
import io.nop.task.ITaskStepResultAggregator;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.model.CallStepTaskStepModel;
import io.nop.task.model.CallTaskStepModel;
import io.nop.task.model.ChooseTaskStepModel;
import io.nop.task.model.DelayTaskStepModel;
import io.nop.task.model.EndTaskStepModel;
import io.nop.task.model.ExitTaskStepModel;
import io.nop.task.model.ForkNTaskStepModel;
import io.nop.task.model.ForkTaskStepModel;
import io.nop.task.model.GraphTaskStepModel;
import io.nop.task.model.IGraphTaskStepModel;
import io.nop.task.model.InvokeTaskStepModel;
import io.nop.task.model.LoopNTaskStepModel;
import io.nop.task.model.LoopTaskStepModel;
import io.nop.task.model.ParallelTaskStepModel;
import io.nop.task.model.ScriptTaskStepModel;
import io.nop.task.model.SequentialTaskStepModel;
import io.nop.task.model.SimpleTaskStepModel;
import io.nop.task.model.SleepTaskStepModel;
import io.nop.task.model.SuspendTaskStepModel;
import io.nop.task.model.TaskBeanModel;
import io.nop.task.model.TaskChooseCaseModel;
import io.nop.task.model.TaskDeciderModel;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.model.TaskInputModel;
import io.nop.task.model.TaskOutputModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.model.TaskStepsModel;
import io.nop.task.model.XplTaskStepModel;
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.step.BeanTaskStepResultAggregator;
import io.nop.task.step.CallStepTaskStep;
import io.nop.task.step.CallTaskStep;
import io.nop.task.step.ChooseTaskStep;
import io.nop.task.step.DelayTaskStep;
import io.nop.task.step.EndTaskStep;
import io.nop.task.step.EnhancedTaskStep;
import io.nop.task.step.EvalTaskStep;
import io.nop.task.step.EvalTaskStepResultAggregator;
import io.nop.task.step.ExitTaskStep;
import io.nop.task.step.ForkNTaskStep;
import io.nop.task.step.ForkTaskStep;
import io.nop.task.step.GraphTaskStep;
import io.nop.task.step.InvokeTaskStep;
import io.nop.task.step.LoopNTaskStep;
import io.nop.task.step.LoopTaskStep;
import io.nop.task.step.ParallelTaskStep;
import io.nop.task.step.SequentialTaskStep;
import io.nop.task.step.SleepTaskStep;
import io.nop.task.step.SuspendTaskStep;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.task.TaskErrors.ARG_STEP_NAME;
import static io.nop.task.TaskErrors.ARG_STEP_TYPE;
import static io.nop.task.TaskErrors.ERR_TASK_UNSUPPORTED_STEP_TYPE;
import static io.nop.task.utils.TaskStepHelper.notNull;

public class TaskStepBuilder implements ITaskStepBuilder {
    private final XLangCompileTool compileTool;

    private final ITaskStepEnhancer stepEnhancer;

    public TaskStepBuilder() {
        this.compileTool = XLang.newCompileTool();
        this.stepEnhancer = new TaskStepEnhancer();
    }

    public ITaskStep buildMainStep(TaskFlowModel taskFlowModel) {
        return buildDecoratedStep(taskFlowModel);
    }

    @Override
    public AbstractTaskStep buildRawStep(TaskStepModel stepModel) throws NopException {
        AbstractTaskStep step;
        String type = stepModel.getType();
        switch (type) {
            case TaskConstants.STEP_TYPE_XPL:
                step = buildXplStep((XplTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_END:
                step = buildEndStep((EndTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_EXIT:
                step = buildExitStep((ExitTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_SUSPEND:
                step = buildSuspendStep((SuspendTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_CALL_STEP:
                step = buildCallStep((CallStepTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_CALL_TASK:
                step = buildCallTask((CallTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_SCRIPT:
                step = buildScriptStep((ScriptTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_DELAY:
                step = buildDelayStep((DelayTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_CHOOSE:
                step = buildChooseStep((ChooseTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_SEQUENTIAL:
                step = buildSequentialStep((SequentialTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_PARALLEL:
                step = buildParallelStep((ParallelTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_LOOP:
                step = buildLoopStep((LoopTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_LOOP_N:
                step = buildLoopNStep((LoopNTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_FORK_N:
                step = buildForkNStep((ForkNTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_FORK:
                step = buildForkStep((ForkTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_INVOKE:
                step = buildInvokeStep((InvokeTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_SIMPLE:
                step = buildSimpleStep((SimpleTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_SLEEP:
                step = buildSleepStep((SleepTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_GRAPH:
                step = buildGraphStep((GraphTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_CASE:
            case TaskConstants.STEP_TYPE_OTHERWISE:
                step = buildSequentialStep((TaskStepsModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_TASK:
                step = buildTaskStep((TaskFlowModel) stepModel);
                break;
            default:
                throw new NopException(ERR_TASK_UNSUPPORTED_STEP_TYPE)
                        .source(stepModel)
                        .param(ARG_STEP_NAME, stepModel.getName())
                        .param(ARG_STEP_TYPE, type);
        }
        step.setStepType(stepModel.getType());
        initAbstractStep(stepModel, step);
        return step;
    }

    private AbstractTaskStep buildTaskStep(TaskFlowModel stepModel) {
        AbstractTaskStep step;
        if (stepModel.isGraphMode()) {
            step = this.buildGraphStep(stepModel);
        } else {
            step = buildSequentialStep(stepModel);
        }
        return step;
    }

    private AbstractTaskStep buildXplStep(XplTaskStepModel stepModel) {
        EvalTaskStep ret = new EvalTaskStep();
        ret.setSource(notNull(stepModel.getSource()));
        return ret;
    }

    private AbstractTaskStep buildEndStep(EndTaskStepModel stepModel) {
        EndTaskStep ret = new EndTaskStep();
        ret.setResult(stepModel.getSource());
        return ret;
    }

    private AbstractTaskStep buildExitStep(ExitTaskStepModel stepModel) {
        ExitTaskStep ret = new ExitTaskStep();
        ret.setResult(stepModel.getSource());
        return ret;
    }

    private AbstractTaskStep buildCallTask(CallTaskStepModel stepModel) {
        CallTaskStep ret = new CallTaskStep();
        ret.setTaskName(stepModel.getTaskName());
        ret.setTaskVersion(stepModel.getTaskVersion() == null ? 0 : stepModel.getTaskVersion());
        ret.setInputNames(stepModel.getInputs().stream().map(TaskInputModel::getName).collect(Collectors.toSet()));
        ret.setOutputNames(stepModel.getOutputs().stream().map(TaskOutputModel::getName).collect(Collectors.toSet()));
        return ret;
    }

    private AbstractTaskStep buildCallStep(CallStepTaskStepModel stepModel) {
        CallStepTaskStep ret = new CallStepTaskStep();
        ret.setLibName(stepModel.getLibName());
        ret.setLibVersion(stepModel.getLibVersion() == null ? 0 : stepModel.getLibVersion());
        ret.setStepName(stepModel.getStepName());
        return ret;
    }

    private AbstractTaskStep buildScriptStep(ScriptTaskStepModel stepModel) {
        IEvalAction action = compileTool.compileScript(stepModel.getLocation(), stepModel.getLang(), stepModel.getSource());

        EvalTaskStep ret = new EvalTaskStep();
        ret.setSource(action);
        return ret;
    }

    private AbstractTaskStep buildDelayStep(DelayTaskStepModel stepModel) {
        DelayTaskStep ret = new DelayTaskStep();
        ret.setDelayMillsExpr(stepModel.getDelayMillisExpr());
        return ret;
    }

    private AbstractTaskStep buildChooseStep(ChooseTaskStepModel stepModel) {
        ChooseTaskStep ret = new ChooseTaskStep();
        ret.setDecider(buildDecider(stepModel.getDecider()));
        Map<String, IEnhancedTaskStep> caseSteps = new HashMap<>();
        for (TaskChooseCaseModel caseModel : stepModel.getCases()) {
            caseSteps.put(caseModel.getMatch(), buildEnhancedStep(caseModel));
        }
        ret.setCaseSteps(caseSteps);
        if (stepModel.getOtherwise() != null)
            ret.setDefaultStep(buildEnhancedStep(stepModel.getOtherwise()));
        return ret;
    }

    private IEvalAction buildDecider(TaskDeciderModel deciderModel) {
        String bean = deciderModel.getBean();
        if (!StringHelper.isEmpty(bean)) {
            return ctx -> {
                ITaskStepChooseDecider decider = (ITaskStepChooseDecider) ctx.getEvalScope()
                        .getBeanProvider().getBean(bean);
                return decider.decide((ITaskStepRuntime) ctx);
            };
        }
        return deciderModel.getSource();
    }

    private SequentialTaskStep buildSequentialStep(TaskStepsModel stepModel) {
        List<IEnhancedTaskStep> steps = new ArrayList<>(stepModel.getSteps().size());
        for (TaskStepModel subStepModel : stepModel.getSteps()) {
            steps.add(buildEnhancedStep(subStepModel));
        }
        SequentialTaskStep ret = new SequentialTaskStep();
        ret.setSteps(steps);
        return ret;
    }

    private SequentialTaskStep buildSequentialBody(TaskStepsModel stepModel) {
        return buildSequentialStep(stepModel);
    }

    private ParallelTaskStep buildParallelStep(ParallelTaskStepModel stepModel) {
        List<IEnhancedTaskStep> steps = new ArrayList<>(stepModel.getSteps().size());
        for (TaskStepModel subStepModel : stepModel.getSteps()) {
            steps.add(buildEnhancedStep(subStepModel));
        }
        ParallelTaskStep ret = new ParallelTaskStep();
        ret.setSteps(steps);
        ret.setStepJoinType(stepModel.getJoinType());
        ret.setAggregateVarName(stepModel.getAggregateVarName());
        ret.setAutoCancelUnfinished(stepModel.isAutoCancelUnfinished());
        ret.setAggregator(buildAggregator(stepModel.getAggregator()));
        return ret;
    }

    private ITaskStepResultAggregator buildAggregator(TaskBeanModel taskBeanModel) {
        if (taskBeanModel == null)
            return null;

        if (!StringHelper.isEmpty(taskBeanModel.getBean()))
            return new BeanTaskStepResultAggregator(taskBeanModel.getBean());

        if (taskBeanModel.getSource() != null)
            return new EvalTaskStepResultAggregator(taskBeanModel.getSource());

        return null;
    }

    private LoopTaskStep buildLoopStep(LoopTaskStepModel stepModel) {
        LoopTaskStep ret = new LoopTaskStep();
        ret.setIndexName(stepModel.getIndexName());
        ret.setVarName(stepModel.getVarName());
        ret.setItemsExpr(stepModel.getItemsExpr());
        ret.setUntilExpr(stepModel.getUntil());
        ret.setBody(buildSequentialBody(stepModel));
        return ret;
    }

    private LoopNTaskStep buildLoopNStep(LoopNTaskStepModel stepModel) {
        LoopNTaskStep ret = new LoopNTaskStep();
        ret.setIndexName(stepModel.getIndexName());
        ret.setVarName(stepModel.getVarName());
        ret.setBeginExpr(stepModel.getBeginExpr());
        ret.setEndExpr(stepModel.getEndExpr());
        ret.setStepExpr(stepModel.getStepExpr());
        ret.setBody(buildSequentialBody(stepModel));
        return ret;
    }

    private ForkNTaskStep buildForkNStep(ForkNTaskStepModel stepModel) {
        ForkNTaskStep ret = new ForkNTaskStep();
        ret.setIndexName(stepModel.getIndexName());
        ret.setAggregator(buildAggregator(stepModel.getAggregator()));
        ret.setCountExpr(stepModel.getCountExpr());
        ret.setStep(buildForkBody(stepModel));
        return ret;
    }

    private ForkTaskStep buildForkStep(ForkTaskStepModel stepModel) {
        ForkTaskStep ret = new ForkTaskStep();
        ret.setIndexName(stepModel.getIndexName());
        ret.setAggregator(buildAggregator(stepModel.getAggregator()));
        ret.setProducer(buildProducer(stepModel.getProducer()));
        ret.setStep(buildForkBody(stepModel));
        return ret;
    }

    private IEnhancedTaskStep buildForkBody(TaskStepsModel stepModel) {
        ITaskStep step = buildSequentialBody(stepModel);

        List<EnhancedTaskStep.InputConfig> inputs = new ArrayList<>(stepModel.getInputs().size());
        for (TaskInputModel inputModel : stepModel.getInputs()) {
            String name = inputModel.getName();
            inputs.add(new EnhancedTaskStep.InputConfig(inputModel.getLocation(), inputModel.getName(),
                    ctx -> ctx.getEvalScope().getLocalValue(name), inputModel.isFromTaskScope()));
        }

        List<EnhancedTaskStep.OutputConfig> outputs = new ArrayList<>();
        Set<String> outputVars = new HashSet<>();
        for (TaskOutputModel outputModel : stepModel.getOutputs()) {
            outputVars.add(outputModel.getName());
            String exportName = outputModel.getExportAs() == null ? outputModel.getName() : outputModel.getExportAs();
            outputs.add(new EnhancedTaskStep.OutputConfig(outputModel.getLocation(), exportName,
                    outputModel.getName(), outputModel.isToTaskScope()));
        }

        return new EnhancedTaskStep(stepModel.getLocation(), stepModel.getName(), inputs, outputs, outputVars,
                null, step,
                null, null, false, null, false);
    }

    private IEvalAction buildProducer(TaskBeanModel beanModel) {
        if (beanModel.getSource() != null)
            return beanModel.getSource();
        return null;
    }

    private InvokeTaskStep buildInvokeStep(InvokeTaskStepModel stepModel) {
        InvokeTaskStep ret = new InvokeTaskStep();
        ret.setArgNames(stepModel.getInputs().stream().map(TaskInputModel::getName).collect(Collectors.toList()));
        ret.setBeanName(stepModel.getBean());
        ret.setMethodName(stepModel.getMethod());
        ret.setReturnAs(stepModel.getReturnAs());
        return ret;
    }


    private AbstractTaskStep buildSimpleStep(SimpleTaskStepModel taskStepModel) {
        return (AbstractTaskStep) BeanContainer.instance().getBean(taskStepModel.getBean());
    }

    private SuspendTaskStep buildSuspendStep(SuspendTaskStepModel stepModel) {
        SuspendTaskStep ret = new SuspendTaskStep();
        ret.setResumeWhen(stepModel.getResumeWhen());
        return ret;
    }

    private SleepTaskStep buildSleepStep(SleepTaskStepModel stepModel) {
        SleepTaskStep ret = new SleepTaskStep();
        ret.setSleepMillisExpr(stepModel.getSleepMillisExpr());
        return ret;
    }

    private GraphTaskStep buildGraphStep(IGraphTaskStepModel stepModel) {
        return new GraphStepBuilder().buildGraphStep(stepModel, this);
    }

    @Override
    public IEnhancedTaskStep buildEnhancedStep(TaskStepModel stepModel) {
        return stepEnhancer.buildEnhanced(stepModel, this);
    }

    @Override
    public ITaskStep buildDecoratedStep(TaskStepModel stepModel) {
        return stepEnhancer.buildDecorated(stepModel, this);
    }

    private void initAbstractStep(TaskStepModel stepModel, AbstractTaskStep step) {
        step.setLocation(stepModel.getLocation());
        step.setInputs(stepModel.getInputs());
        step.setOutputs(stepModel.getOutputs());
    }
}
