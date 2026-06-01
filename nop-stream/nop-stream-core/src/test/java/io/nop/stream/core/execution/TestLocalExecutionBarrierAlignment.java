package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestLocalExecutionBarrierAlignment {

    @Test
    public void testStrictlyOnceSetsBarrierAlignmentTrue() {
        CheckpointConfig config = CheckpointConfig.builder()
                .processingGuarantee(ProcessingGuarantee.STRICT_EXACTLY_ONCE)
                .build();
        assertTrue(config.getProcessingGuarantee().isBarrierAlignment(),
                "STRICT_EXACTLY_ONCE should have barrierAlignment=true");
    }

    @Test
    public void testAtLeastOnceSetsBarrierAlignmentFalse() {
        CheckpointConfig config = CheckpointConfig.builder()
                .processingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE)
                .build();
        assertFalse(config.getProcessingGuarantee().isBarrierAlignment(),
                "AT_LEAST_ONCE should have barrierAlignment=false");
    }

    @Test
    public void testBestEffortSetsBarrierAlignmentFalse() {
        CheckpointConfig config = CheckpointConfig.builder()
                .processingGuarantee(ProcessingGuarantee.BEST_EFFORT)
                .build();
        assertFalse(config.getProcessingGuarantee().isBarrierAlignment(),
                "BEST_EFFORT should have barrierAlignment=false");
    }

    @Test
    public void testEffectivelyOnceSetsBarrierAlignmentFalse() {
        CheckpointConfig config = CheckpointConfig.builder()
                .processingGuarantee(ProcessingGuarantee.EFFECTIVELY_ONCE)
                .build();
        assertFalse(config.getProcessingGuarantee().isBarrierAlignment(),
                "EFFECTIVELY_ONCE should have barrierAlignment=false");
    }
}
