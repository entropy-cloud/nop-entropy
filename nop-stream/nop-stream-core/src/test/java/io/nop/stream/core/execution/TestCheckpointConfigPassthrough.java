package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.jobgraph.JobGraph;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointConfigPassthrough {

    @Test
    void testFourParamOverloadPassesCheckpointConfig() throws Exception {
        AtomicReference<CheckpointConfig> capturedConfig = new AtomicReference<>();

        ICheckpointExecutorFactory factory = new ICheckpointExecutorFactory() {
            @Override
            public StreamExecutionResult executeWithCheckpoint(JobGraph jobGraph, String jobName, CheckpointConfig config) {
                return null;
            }

            @Override
            public String triggerSavepoint(JobGraph jobGraph, CheckpointConfig config, String targetPath) {
                return targetPath;
            }

            @Override
            public StreamExecutionResult executeWithSavepoint(JobGraph jobGraph, String jobName, CheckpointConfig config, String savepointPath) {
                return null;
            }

            @Override
            public StreamExecutionResult executeWithCheckpoint(
                    StreamModel streamModel,
                    PartitionedPlan partitionedPlan,
                    DeploymentPlan deploymentPlan,
                    CheckpointConfig config) {
                capturedConfig.set(config);
                return new StreamExecutionResult("test", 0);
            }
        };

        CheckpointConfig userConfig = new CheckpointConfig();
        userConfig.setCheckpointEnabled(true);
        userConfig.setCheckpointInterval(5000);
        userConfig.setProcessingGuarantee(ProcessingGuarantee.STRICT_EXACTLY_ONCE);

        StreamExecutionResult result = factory.executeWithCheckpoint(
                (StreamModel) null, null, null, userConfig);

        CheckpointConfig captured = capturedConfig.get();
        assertNotNull(captured, "Config should be passed to 4-param overload");
        assertTrue(captured.isCheckpointEnabled());
        assertEquals(5000, captured.getCheckpointInterval());
        assertEquals(ProcessingGuarantee.STRICT_EXACTLY_ONCE, captured.getProcessingGuarantee());
    }

    @Test
    void testThreeParamOverloadFallsBackToDefault() throws Exception {
        AtomicReference<CheckpointConfig> capturedConfig = new AtomicReference<>();

        ICheckpointExecutorFactory factory = new ICheckpointExecutorFactory() {
            @Override
            public StreamExecutionResult executeWithCheckpoint(JobGraph jobGraph, String jobName, CheckpointConfig config) {
                return null;
            }

            @Override
            public String triggerSavepoint(JobGraph jobGraph, CheckpointConfig config, String targetPath) {
                return targetPath;
            }

            @Override
            public StreamExecutionResult executeWithSavepoint(JobGraph jobGraph, String jobName, CheckpointConfig config, String savepointPath) {
                return null;
            }

            @Override
            public StreamExecutionResult executeWithCheckpoint(
                    StreamModel streamModel,
                    PartitionedPlan partitionedPlan,
                    DeploymentPlan deploymentPlan) {
                return new StreamExecutionResult("test", 0);
            }

            @Override
            public StreamExecutionResult executeWithCheckpoint(
                    StreamModel streamModel,
                    PartitionedPlan partitionedPlan,
                    DeploymentPlan deploymentPlan,
                    CheckpointConfig config) {
                capturedConfig.set(config);
                return new StreamExecutionResult("test", 0);
            }
        };

        CheckpointConfig userConfig = new CheckpointConfig();
        userConfig.setCheckpointEnabled(true);
        userConfig.setCheckpointInterval(3000);

        factory.executeWithCheckpoint((StreamModel) null, null, null, userConfig);
        assertNotNull(capturedConfig.get(), "4-param overload should be used");
        assertEquals(3000, capturedConfig.get().getCheckpointInterval());
    }
}
