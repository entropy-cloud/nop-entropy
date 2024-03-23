package io.nop.task.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.IEnhancedTaskStep;
import io.nop.task.ITaskManager;
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
import io.nop.task.model.TaskInvokeArgModel;
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
import io.nop.task.step.InvokeTaskStep;
import io.nop.task.step.LoopNTaskStep;
import io.nop.task.step.LoopTaskStep;
import io.nop.task.step.ParallelTaskStep;
import io.nop.task.step.SequentialTaskStep;
import io.nop.task.step.SleepTaskStep;
import io.nop.task.step.SuspendTaskStep;
import io.nop.task.utils.TaskStepHelper;
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

public class TaskStepBuilder implements ITaskStepBuilder {
    private final ITaskManager taskManager;
    private final XLangCompileTool compileTool;

    private final ITaskStepEnhancer stepEnhancer;

    public TaskStepBuilder(ITaskManager taskManager) {
        this.taskManager = taskManager;
        this.compileTool = XLang.newCompileTool();
        this.stepEnhancer = new TaskStepEnhancer();
    }

    public AbstractTaskStep buildStep(TaskStepModel stepModel) throws NopException {
        AbstractTaskStep step;
        String type = stepModel.getType();
        switch (type) {
            case TaskConstants.STEP_TYPE_XPL:
                step = buildXplStep((XplTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_NAME_END:
                step = buildEndStep((EndTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_NAME_EXIT:
                step = buildExitStep((ExitTaskStepModel) stepModel);
                break;
            case TaskConstants.STEP_NAME_SUSPEND:
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

    private AbstractTaskStep buildXplStep(XplTaskStepModel stepModel) {
        EvalTaskStep ret = new EvalTaskStep();
        ret.setSource(TaskStepHelper.notNull(stepModel.getSource()));
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
        ret.setTaskManager(taskManager);
        ret.setTaskName(stepModel.getTaskName());
        ret.setTaskVersion(stepModel.getTaskVersion());
        return ret;
    }

    private AbstractTaskStep buildCallStep(CallStepTaskStepModel stepModel) {
        CallStepTaskStep ret = new CallStepTaskStep();
        ret.setTaskManager(taskManager);
        ret.setLibName(stepModel.getLibName());
        ret.setLibVersion(stepModel.getLibVersion());
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
            caseSteps.put(caseModel.getName(), buildEnhancedStep(caseModel));
        }
        ret.setCaseSteps(caseSteps);
        ret.setDefaultStep(buildEnhancedStep(stepModel.getOtherwise()));
        return ret;
    }

    private IEvalAction buildDecider(TaskDeciderModel deciderModel) {
        String bean = deciderModel.getBean();
        if (StringHelper.isEmpty(bean)) {
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
        List<IEnhancedTaskStep> steps = new ArrayList<>(stepModel.getSteps().size());
        for (TaskStepModel subStepModel : stepModel.getSteps()) {
            steps.add(buildEnhancedStep(subStepModel));
        }

        SequentialTaskStep ret = new SequentialTaskStep();
        ret.setSteps(steps);
        return ret;
    }

    private ParallelTaskStep buildParallelStep(ParallelTaskStepModel stepModel) {
        List<IEnhancedTaskStep> steps = new ArrayList<>(stepModel.getSteps().size());
        for (TaskStepModel subStepModel : stepModel.getSteps()) {
            steps.add(buildEnhancedStep(subStepModel));
        }
        ParallelTaskStep ret = new ParallelTaskStep();
        ret.setSteps(steps);
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
        ret.setItemsExpr(stepModel.getItems());
        ret.setUntilExpr(stepModel.getUntil());
        ret.setBody(buildSequentialBody(stepModel));
        return ret;
    }

    private LoopNTaskStep buildLoopNStep(LoopNTaskStepModel stepModel) {
        LoopNTaskStep ret = new LoopNTaskStep();
        ret.setIndexName(stepModel.getIndexName());
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
        //ret.setStep(buildSequentialBody(stepModel));
        return ret;
    }

    private ForkTaskStep buildForkStep(ForkTaskStepModel stepModel) {
        ForkTaskStep ret = new ForkTaskStep();
        ret.setIndexName(stepModel.getIndexName());
        ret.setAggregator(buildAggregator(stepModel.getAggregator()));
        ret.setProducer(buildProducer(stepModel.getProducer()));
        //ret.setStep(buildSequentialBody(stepModel));
        return ret;
    }

    private IEvalAction buildProducer(TaskBeanModel beanModel) {
        if (beanModel.getSource() != null)
            return beanModel.getSource();
        return null;
    }

    private InvokeTaskStep buildInvokeStep(InvokeTaskStepModel stepModel) {
        InvokeTaskStep ret = new InvokeTaskStep();
        ret.setArgExprs(buildArgExprs(stepModel.getArgs()));
        ret.setBeanName(stepModel.getBean());
        ret.setMethodName(stepModel.getMethod());
        ret.setReturnAs(stepModel.getReturnAs());
        return ret;
    }

    private List<IEvalAction> buildArgExprs(List<TaskInvokeArgModel> argModels) {
        return argModels.stream().map(arg -> TaskStepHelper.notNull(arg.getValueExpr())).collect(Collectors.toList());
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

    private EnhancedTaskStep buildEnhancedStep(TaskStepModel stepModel) {
        return stepEnhancer.buildEnhanced(stepModel, this);
    }

    private void initAbstractStep(TaskStepModel stepModel, AbstractTaskStep step) {
        step.setInputs(stepModel.getInputs());
        step.setOutputs(stepModel.getOutputs());
    }
}
