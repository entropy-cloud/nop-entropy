package io.nop.task.builder;

import io.nop.task.ITaskStepExecution;
import io.nop.task.model.IGraphTaskStepModel;
import io.nop.task.step.GraphTaskStep;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GraphStepBuilder {


    public GraphTaskStep buildGraphStep(IGraphTaskStepModel stepModel, ITaskStepBuilder stepBuilder) {
        GraphTaskStep ret = new GraphTaskStep();

        List<ITaskStepExecution> enterSteps = new ArrayList<>(stepModel.getEnterSteps().size());

        List<GraphTaskStep.GraphStepNode> subSteps = stepModel.getSteps().stream()
                .map(subStep -> {
                    ITaskStepExecution step = stepBuilder.buildStepExecution(subStep);
                    if (stepModel.getEnterSteps().contains(subStep.getName())) {
                        enterSteps.add(step);
                    }
                    boolean end = stepModel.getExitSteps().contains(subStep.getName());
                    return new GraphTaskStep.GraphStepNode(subStep.getWaitSteps(), step, end);
                }).collect(Collectors.toList());

        ret.setNodes(subSteps);
        ret.setEnterSteps(enterSteps);

        return ret;
    }


}
