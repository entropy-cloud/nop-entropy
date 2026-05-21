/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.*;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.execution.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.*;
import io.nop.stream.core.operators.*;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestSavepointEndToEnd {

    private static final TaskLocation LOC_0 = new TaskLocation("1", "1", "v0", 0);

    @TempDir
    Path tempDir;

    @Test
    void testGraphModelSavepointTriggerWritesFile() throws Exception {
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                for (int i = 1; i <= 3; i++) {
                    ctx.collect("item-" + i);
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(results::add);

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, sinkOp);
        OperatorChain chain = new OperatorChain(operators);
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);

        List<OperatorChain> chains = Collections.singletonList(chain);
        JobVertex vertex = new JobVertex("v1", "test-vertex", 1, chains, invokable);

        JobGraph jobGraph = new JobGraph("savepoint-trigger-test");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000L);
        config.setStorageProperty("path", tempDir.toString());

        String savepointPath = GraphModelCheckpointExecutor.triggerSavepoint(jobGraph, config, tempDir.toString());
        assertNotNull(savepointPath);

        assertTrue(Files.exists(Path.of(savepointPath)));
        assertEquals(Arrays.asList("item-1", "item-2", "item-3"), results);
    }

    @Test
    void testGraphModelExecuteWithSavepointRestoresState() throws Exception {
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());

        TaskStateSnapshot taskState = TaskStateSnapshot.builder(LOC_0)
                .checkpointId(1L)
                .putOperatorState("operator-1", "restored-data".getBytes())
                .build();

        Map<TaskLocation, TaskStateSnapshot> taskStates = new HashMap<>();
        taskStates.put(LOC_0, taskState);

        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId("1")
                .pipelineId("1")
                .checkpointId(1L)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.SAVEPOINT)
                .taskStates(taskStates)
                .build();

        String savepointPath = storage.storeCheckPoint(checkpoint);
        assertNotNull(savepointPath);

        AtomicInteger restoredCount = new AtomicInteger(0);
        AtomicReference<byte[]> restoredState = new AtomicReference<>();

        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) {
                ctx.collect("post-restore");
            }

            @Override
            public void cancel() {
            }
        };

        AbstractStreamOperator<String> restoredOp = new AbstractStreamOperator<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void restoreState(OperatorSnapshotResult snapshotResult) throws Exception {
                super.restoreState(snapshotResult);
                if (snapshotResult != null && !snapshotResult.getOperatorStates().isEmpty()) {
                    restoredCount.incrementAndGet();
                    restoredState.set(snapshotResult.getOperatorStates().values().iterator().next());
                }
            }
        };

        StreamSourceOperator<String> sourceOp = new StreamSourceOperator<>(source);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>((value) -> {});

        List<StreamOperator<?>> operators = Arrays.asList(sourceOp, restoredOp, sinkOp);
        OperatorChain chain = new OperatorChain(operators);
        StreamTaskInvokable invokable = new StreamTaskInvokable(chain);

        List<OperatorChain> chains = Collections.singletonList(chain);
        JobVertex vertex = new JobVertex("v1", "test-vertex", 1, chains, invokable);

        JobGraph jobGraph = new JobGraph("savepoint-recovery-test");
        jobGraph.addVertex(vertex);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId("1");
        config.setPipelineId("1");
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(60000L);
        config.setStorageProperty("path", tempDir.toString());

        io.nop.stream.core.environment.StreamExecutionResult result =
                GraphModelCheckpointExecutor.executeWithSavepoint(
                        jobGraph, "Savepoint Recovery Test", config, tempDir.toString());
        assertNotNull(result);

        assertEquals(1, restoredCount.get());
        assertArrayEquals("restored-data".getBytes(), restoredState.get());

        storage.deleteAllCheckpoints("1");
    }
}
