/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;

import java.util.*;

public class CheckpointPlanBuilder {

    public static CheckpointPlan build(GraphExecutionPlan executionPlan, String jobId, String pipelineId) {
        if (executionPlan == null) {
            throw new IllegalArgumentException("executionPlan must not be null");
        }
        if (jobId == null || jobId.isEmpty()) {
            throw new IllegalArgumentException("jobId must not be null or empty");
        }
        if (pipelineId == null || pipelineId.isEmpty()) {
            throw new IllegalArgumentException("pipelineId must not be null or empty");
        }

        List<TaskLocation> allTasks = new ArrayList<>();
        List<TaskLocation> sourceTasks = new ArrayList<>();
        Map<TaskLocation, List<OperatorStateMapping>> stateMappings = new LinkedHashMap<>();

        for (String vertexId : executionPlan.getSortedVertexIds()) {
            JobVertex vertex = executionPlan.getExecutionVertices().get(vertexId);
            if (vertex == null) {
                throw new IllegalStateException("Vertex not found in execution plan: " + vertexId);
            }

            TaskLocation taskLocation = new TaskLocation(jobId, pipelineId, vertexId, 0);
            allTasks.add(taskLocation);

            List<OperatorChain> chains = vertex.getOperatorChains();
            if (chains == null || chains.isEmpty()) {
                throw new IllegalStateException("Vertex has no operator chains: " + vertexId);
            }

            boolean isSource = false;
            List<OperatorStateMapping> mappings = new ArrayList<>();
            int operatorGlobalIndex = 0;

            for (OperatorChain chain : chains) {
                List<StreamOperator<?>> operators = chain.getOperators();
                for (int i = 0; i < operators.size(); i++) {
                    StreamOperator<?> op = operators.get(i);
                    int opIndex = operatorGlobalIndex++;

                    String opStateKey = "operator-" + opIndex;
                    String keyedKey = null;
                    boolean is2PC = false;

                    if (op instanceof AbstractStreamOperator) {
                        AbstractStreamOperator<?> abstractOp = (AbstractStreamOperator<?>) op;
                        if (abstractOp.getKeyedStateBackend() != null) {
                            keyedKey = "operator-" + opIndex + "-keyed";
                        }
                    }

                    if (op instanceof AbstractUdfStreamOperator) {
                        Object udf = ((AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
                        if (udf instanceof TwoPhaseCommitSinkFunction) {
                            is2PC = true;
                        }
                    }

                    if (op instanceof StreamSourceOperator) {
                        isSource = true;
                    }

                    mappings.add(new OperatorStateMapping(opIndex, opStateKey, keyedKey, is2PC));
                }
            }

            if (isSource) {
                sourceTasks.add(taskLocation);
            }

            stateMappings.put(taskLocation, mappings);
        }

        return new CheckpointPlan(jobId, pipelineId, allTasks, sourceTasks, stateMappings);
    }
}
