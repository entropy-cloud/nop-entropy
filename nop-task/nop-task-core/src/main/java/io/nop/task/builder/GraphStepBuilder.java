package io.nop.task.builder;

import io.nop.task.ITaskStepExecution;
import io.nop.task.model.IGraphTaskStepModel;
import io.nop.task.step.GraphTaskStep;

import java.util.List;
import java.util.stream.Collectors;

public class GraphStepBuilder {


    public GraphTaskStep buildGraphStep(IGraphTaskStepModel stepModel, ITaskStepBuilder stepBuilder) {
        GraphTaskStep ret = new GraphTaskStep();
        List<GraphTaskStep.GraphStepNode> subSteps = stepModel.getSteps().stream()
                .map(subStep -> {
                    ITaskStepExecution step = stepBuilder.buildStepExecution(subStep);
                    boolean enter = stepModel.getEnterSteps().contains(subStep.getName());
                    boolean end = stepModel.getExitSteps().contains(subStep.getName());
                    return new GraphTaskStep.GraphStepNode(subStep.getWaitSteps(), subStep.getWaitErrorSteps(),
                            step, enter, end);
                }).collect(Collectors.toList());

        ret.setNodes(subSteps);

        return ret;
    }


}
