package io.nop.task.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepExecution;
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
import io.nop.task.model.IfTaskStepModel;
import io.nop.task.model.InvokeStaticTaskStepModel;
import io.nop.task.model.InvokeTaskStepModel;
import io.nop.task.model.LoopNTaskStepModel;
import io.nop.task.model.LoopTaskStepModel;
import io.nop.task.model.ParallelTaskStepModel;
import io.nop.task.model.ScriptTaskStepModel;
import io.nop.task.model.SelectorTaskStepModel;
import io.nop.task.model.SequentialTaskStepModel;
import io.nop.task.model.SimpleTaskStepModel;
import io.nop.task.model.SleepTaskStepModel;
import io.nop.task.model.SuspendTaskStepModel;
import io.nop.task.model.TaskChooseCaseModel;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.model.TaskInputModel;
import io.nop.task.model.TaskOutputModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.model.TaskStepsModel;
import io.nop.task.model.XplTaskStepModel;
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.step.CallStepTaskStep;
import io.nop.task.step.CallTaskStep;
import io.nop.task.step.ChooseTaskStep;
import io.nop.task.step.DelayTaskStep;
import io.nop.task.step.EndTaskStep;
import io.nop.task.step.EvalTaskStep;
import io.nop.task.step.ExitTaskStep;
import io.nop.task.step.ForkNTaskStep;
import io.nop.task.step.ForkTaskStep;
import io.nop.task.step.GraphTaskStep;
import io.nop.task.step.IfTaskStep;
import io.nop.task.step.InvokeStaticTaskStep;
import io.nop.task.step.InvokeTaskStep;
import io.nop.task.step.LoopNTaskStep;
import io.nop.task.step.LoopTaskStep;
import io.nop.task.step.ParallelTaskStep;
import io.nop.task.step.SelectorTaskStep;
import io.nop.task.step.SequentialTaskStep;
import io.nop.task.step.SleepTaskStep;
import io.nop.task.step.SuspendTaskStep;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            case TaskConstants.STEP_TYPE_IF:
                step = buildIfStep((IfTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_SEQUENTIAL:
                step = buildSequentialStep((SequentialTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_TYPE_SELECTOR:
                step = buildSelectorStep((SelectorTaskStepModel) stepModel);
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
            case TaskConstants.STEP_TYPE_INVOKE_STATIC:
                step = buildInvokeStaticStep((InvokeStaticTaskStepModel) stepModel);
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

        step.setStepType(stepModel.getFullStepType());
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
        IEvalAction action = compileTool.compileScriptAction(stepModel.getLocation(), stepModel.getLang(),
                stepModel.getSource(), stepModel.getInputsAsArgModels(), stepModel.getReturnType());

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
        ret.setDecider(stepModel.getDecider());
        Map<String, ITaskStepExecution> caseSteps = new HashMap<>();
        for (TaskChooseCaseModel caseModel : stepModel.getCases()) {
            caseSteps.put(caseModel.getMatch(), buildStepExecution(caseModel));
        }
        ret.setCaseSteps(caseSteps);
        if (stepModel.getOtherwise() != null)
            ret.setDefaultStep(buildStepExecution(stepModel.getOtherwise()));
        return ret;
    }

    private AbstractTaskStep buildIfStep(IfTaskStepModel stepModel) {
        IfTaskStep ret = new IfTaskStep();
        ret.setCondition(stepModel.getCondition());
        if (stepModel.getThen() != null)
            ret.setThen(buildStepExecution(stepModel.getThen()));
        if (stepModel.getElse() != null)
            ret.setElseStep(buildStepExecution(stepModel.getElse()));
        return ret;
    }

    private SequentialTaskStep buildSequentialStep(TaskStepsModel stepModel) {
        List<ITaskStepExecution> steps = new ArrayList<>(stepModel.getSteps().size());
        for (TaskStepModel subStepModel : stepModel.getSteps()) {
            if (subStepModel.isDisabled())
                continue;
            steps.add(buildStepExecution(subStepModel));
        }
        SequentialTaskStep ret = new SequentialTaskStep();
        ret.setSteps(steps);
        return ret;
    }

    private SequentialTaskStep buildSequentialBody(TaskStepsModel stepModel) {
        return buildSequentialStep(stepModel);
    }

    private SelectorTaskStep buildSelectorStep(TaskStepsModel stepModel) {
        List<ITaskStepExecution> steps = new ArrayList<>(stepModel.getSteps().size());
        for (TaskStepModel subStepModel : stepModel.getSteps()) {
            if (subStepModel.isDisabled())
                continue;
            steps.add(buildStepExecution(subStepModel));
        }
        SelectorTaskStep ret = new SelectorTaskStep();
        ret.setSteps(steps);
        return ret;
    }

    private ParallelTaskStep buildParallelStep(ParallelTaskStepModel stepModel) {
        List<ITaskStepExecution> steps = new ArrayList<>(stepModel.getSteps().size());
        for (TaskStepModel subStepModel : stepModel.getSteps()) {
            if (subStepModel.isDisabled())
                continue;
            steps.add(buildStepExecution(subStepModel));
        }
        ParallelTaskStep ret = new ParallelTaskStep();
        ret.setSteps(steps);
        ret.setStepJoinType(stepModel.getJoinType());
        ret.setAggregateVarName(stepModel.getAggregateVarName());
        ret.setAutoCancelUnfinished(stepModel.isAutoCancelUnfinished());
        ret.setAggregator(stepModel.getAggregator());
        return ret;
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
        ret.setStepName(stepModel.getName());
        ret.setIndexName(stepModel.getIndexName());
        ret.setAutoCancelUnfinished(stepModel.isAutoCancelUnfinished());
        ret.setStepJoinType(stepModel.getJoinType());
        ret.setAggregateVarName(stepModel.getAggregateVarName());
        ret.setAggregator(stepModel.getAggregator());
        ret.setCountExpr(stepModel.getCountExpr());
        ret.setStep(buildForkBody(stepModel));
        return ret;
    }

    private ForkTaskStep buildForkStep(ForkTaskStepModel stepModel) {
        ForkTaskStep ret = new ForkTaskStep();
        ret.setIndexName(stepModel.getIndexName());
        ret.setVarName(stepModel.getVarName());
        ret.setStepName(stepModel.getName());
        ret.setAutoCancelUnfinished(stepModel.isAutoCancelUnfinished());
        ret.setStepJoinType(stepModel.getJoinType());
        ret.setAggregateVarName(stepModel.getAggregateVarName());
        ret.setAggregator(stepModel.getAggregator());
        ret.setProducer(stepModel.getProducer());
        ret.setStep(buildForkBody(stepModel));
        return ret;
    }

    private ITaskStep buildForkBody(TaskStepsModel stepModel) {
        return buildSequentialBody(stepModel);
    }

    private InvokeTaskStep buildInvokeStep(InvokeTaskStepModel stepModel) {
        InvokeTaskStep ret = new InvokeTaskStep();
        ret.setArgNames(stepModel.getInputs().stream().map(TaskInputModel::getName).collect(Collectors.toList()));
        ret.setBeanName(stepModel.getBean());
        ret.setMethodName(stepModel.getMethod());
        ret.setReturnAs(stepModel.getReturnAs());
        return ret;
    }

    private InvokeStaticTaskStep buildInvokeStaticStep(InvokeStaticTaskStepModel stepModel) {
        InvokeStaticTaskStep ret = new InvokeStaticTaskStep();
        ret.setArgNames(stepModel.getInputs().stream().map(TaskInputModel::getName).collect(Collectors.toList()));
        IEvalFunction method = stepModel.getResolvedMethod();
        ret.setMethod(method);
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
    public ITaskStepExecution buildStepExecution(TaskStepModel stepModel) {
        return stepEnhancer.buildExecution(stepModel, this);
    }

    @Override
    public ITaskStep buildDecoratedStep(TaskStepModel stepModel) {
        return stepEnhancer.buildDecorated(stepModel, this);
    }

    private void initAbstractStep(TaskStepModel stepModel, AbstractTaskStep step) {
        step.setLocation(stepModel.getLocation());
        step.setInputs(stepModel.getInputs());
        step.setOutputs(stepModel.getOutputs());
        step.setConcurrent(stepModel.isConcurrent());
        step.setReturnAs(stepModel.getReturnAs());
    }
}
