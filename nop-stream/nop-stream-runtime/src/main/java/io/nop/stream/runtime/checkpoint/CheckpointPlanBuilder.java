/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import java.util.*;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.Subtask;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

public class CheckpointPlanBuilder {

    /**
     * Build a CheckpointPlan from the execution plan without StreamComponents.
     * Participants list will be auto-detected from TwoPhaseCommitSinkFunction operators.
     * Uses STRICT_EXACTLY_ONCE as default processing guarantee.
     */
    public static CheckpointPlan build(GraphExecutionPlan executionPlan, String jobId, String pipelineId) {
        return build(executionPlan, jobId, pipelineId, null, null);
    }

    /**
     * Build a CheckpointPlan from the execution plan, extracting checkpoint participants
     * from StreamComponents if provided.
     */
    public static CheckpointPlan build(GraphExecutionPlan executionPlan, String jobId, String pipelineId,
                                       StreamComponents streamComponents) {
        return build(executionPlan, jobId, pipelineId, streamComponents, null);
    }

    /**
     * Build a CheckpointPlan from the execution plan with full configuration.
     *
     * <p>Supports parallelism > 1: generates a {@link TaskLocation} for each subtask
     * of each vertex. Source tasks are identified by checking if the head operator
     * in the chain is a {@link StreamSourceOperator}.
     *
     * @param executionPlan    the execution plan
     * @param jobId            job identifier
     * @param pipelineId       pipeline identifier
     * @param streamComponents optional stream components for participant detection
     * @param checkpointConfig optional checkpoint config providing processing guarantee
     */
    public static CheckpointPlan build(GraphExecutionPlan executionPlan, String jobId, String pipelineId,
                                       StreamComponents streamComponents, CheckpointConfig checkpointConfig) {
        if (executionPlan == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "executionPlan");
        }
        if (jobId == null || jobId.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "jobId");
        }
        if (pipelineId == null || pipelineId.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "pipelineId");
        }

        List<TaskLocation> allTasks = new ArrayList<>();
        List<TaskLocation> sourceTasks = new ArrayList<>();
        Map<TaskLocation, List<OperatorStateMapping>> stateMappings = new LinkedHashMap<>();

        for (String vertexId : executionPlan.getSortedVertexIds()) {
            JobVertex vertex = executionPlan.getExecutionVertices().get(vertexId);
            if (vertex == null) {
                throw new StreamException(ERR_STREAM_INVALID_STATE)
                        .param(ARG_DETAIL, "Vertex not found in execution plan: " + vertexId);
            }

            List<Subtask> subtaskList = executionPlan.getSubtasks(vertexId);
            if (subtaskList.isEmpty()) {
                // Fallback: legacy single-task mode (parallelism=1)
                subtaskList = null;
            }

            List<OperatorChain> chains = vertex.getOperatorChains();
            if (chains == null || chains.isEmpty()) {
                throw new StreamException(ERR_STREAM_INVALID_STATE)
                        .param(ARG_DETAIL, "Vertex has no operator chains: " + vertexId);
            }

            if (subtaskList != null) {
                // Multi-subtask mode: iterate over all subtasks
                for (Subtask subtask : subtaskList) {
                    TaskLocation taskLocation = new TaskLocation(
                            jobId, pipelineId, vertexId, subtask.getTaskIndex());
                    allTasks.add(taskLocation);

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
            } else {
                // Legacy single-task mode
                TaskLocation taskLocation = new TaskLocation(jobId, pipelineId, vertexId, 0);
                allTasks.add(taskLocation);

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
        }

        // Collect checkpoint participant IDs from StreamComponents or auto-detect
        List<String> participantIds = new ArrayList<>();
        if (streamComponents != null && !streamComponents.getCheckpointParticipants().isEmpty()) {
            participantIds.addAll(streamComponents.getCheckpointParticipants());
        } else {
            // Auto-detect: find operators that contain TwoPhaseCommitSinkFunction
            for (String vertexId : executionPlan.getSortedVertexIds()) {
                JobVertex vertex = executionPlan.getExecutionVertices().get(vertexId);
                List<Subtask> subtaskList2 = executionPlan.getSubtasks(vertexId);
                boolean found2PC = false;
                outerChain:
                for (OperatorChain chain : vertex.getOperatorChains()) {
                    for (StreamOperator<?> op : chain.getOperators()) {
                        if (op instanceof AbstractUdfStreamOperator) {
                            Object udf = ((AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
                            if (udf instanceof TwoPhaseCommitSinkFunction) {
                                found2PC = true;
                                break outerChain;
                            }
                        }
                    }
                }
                if (found2PC) {
                    if (subtaskList2 != null && !subtaskList2.isEmpty()) {
                        for (Subtask subtask : subtaskList2) {
                            participantIds.add(vertexId + "-" + subtask.getTaskIndex());
                        }
                    } else {
                        participantIds.add(vertexId + "-0");
                    }
                }
            }
        }

        ProcessingGuarantee guarantee = (checkpointConfig != null && checkpointConfig.getProcessingGuarantee() != null)
                ? checkpointConfig.getProcessingGuarantee()
                : ProcessingGuarantee.STRICT_EXACTLY_ONCE;

        return new CheckpointPlan(2, jobId, pipelineId, allTasks, sourceTasks, stateMappings,
                participantIds, guarantee);
    }
}
