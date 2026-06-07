package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.jobgraph.JobGraph;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestConcurrencySafety {

    @Test
    void testDefaultCheckpointExecutorFactoryVolatileVisibility() throws Exception {
        AtomicReference<ICheckpointExecutorFactory> captured = new AtomicReference<>();
        ICheckpointExecutorFactory factory = new ICheckpointExecutorFactory() {
            @Override
            public StreamExecutionResult executeWithCheckpoint(
                    JobGraph jobGraph, String jobName, CheckpointConfig checkpointConfig) {
                return null;
            }

            @Override
            public String triggerSavepoint(JobGraph jobGraph, CheckpointConfig checkpointConfig,
                                           String targetPath) {
                return null;
            }

            @Override
            public StreamExecutionResult executeWithSavepoint(JobGraph jobGraph, String jobName,
                                                              CheckpointConfig checkpointConfig,
                                                              String savepointPath) {
                return null;
            }
        };

        StreamExecutionEnvironment.setCheckpointExecutorFactory(factory);

        Thread t = new Thread(() -> {
            StreamExecutionEnvironment env = new StreamExecutionEnvironment();
            captured.set(env.getCheckpointExecutorFactory());
        });
        t.start();
        t.join(5000);

        assertSame(factory, captured.get());

        StreamExecutionEnvironment.setCheckpointExecutorFactory(null);
    }

    @Test
    void testRecordReaderPropagatesInterruptedException() {
        InputChannel channel = new InputChannel(new ResultPartition(1)) {
            @Override
            public io.nop.stream.core.streamrecord.StreamElement read() throws InterruptedException {
                throw new InterruptedException("test interrupt");
            }
        };

        RecordReader<Void> reader = new RecordReader<>(channel);
        assertThrows(io.nop.stream.core.exceptions.StreamException.class, () -> reader.read());
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }
}
