package io.nop.task.builder;

import io.nop.task.model.GraphTaskStepModel;
import io.nop.task.step.GraphTaskStep;

public class GraphStepBuilder {
    public GraphTaskStep buildGraphStep(GraphTaskStepModel stepModel, ITaskStepBuilder stepBuilder) {
        GraphTaskStep ret = new GraphTaskStep();
        return ret;
    }
}
