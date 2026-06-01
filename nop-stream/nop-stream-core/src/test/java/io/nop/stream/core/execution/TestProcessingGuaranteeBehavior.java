package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestProcessingGuaranteeBehavior {

    @Test
    public void testAlignedVsNonAlignedBarrierBehavior() throws Exception {
        ResultPartition p0_aligned = new ResultPartition();
        ResultPartition p1_aligned = new ResultPartition();
        List<InputChannel> channelsAligned = Arrays.asList(
                new InputChannel(p0_aligned), new InputChannel(p1_aligned));
        InputGate alignedGate = new InputGate(channelsAligned, null, true);

        ResultPartition p0_nonaligned = new ResultPartition();
        ResultPartition p1_nonaligned = new ResultPartition();
        List<InputChannel> channelsNonAligned = Arrays.asList(
                new InputChannel(p0_nonaligned), new InputChannel(p1_nonaligned));
        InputGate nonAlignedGate = new InputGate(channelsNonAligned, null, false);

        long cpId = 100L;

        p0_aligned.write(new CheckpointBarrier(cpId, 0, CheckpointType.CHECKPOINT));
        p0_aligned.write(new StreamRecord<>("after-barrier-aligned"));
        p1_aligned.write(new StreamRecord<>("data-ch1-aligned"));

        p0_nonaligned.write(new CheckpointBarrier(cpId, 0, CheckpointType.CHECKPOINT));
        p0_nonaligned.write(new StreamRecord<>("after-barrier-nonaligned"));
        p1_nonaligned.write(new StreamRecord<>("data-ch1-nonaligned"));

        List<String> alignedRecords = new ArrayList<>();
        Optional<StreamElement> elem;
        while ((elem = alignedGate.read()).isPresent()) {
            if (elem.get().isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) elem.get().asRecord();
                alignedRecords.add(rec.getValue());
            }
        }

        List<String> nonAlignedRecords = new ArrayList<>();
        while ((elem = nonAlignedGate.read()).isPresent()) {
            if (elem.get().isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) elem.get().asRecord();
                nonAlignedRecords.add(rec.getValue());
            }
        }

        assertTrue(nonAlignedRecords.contains("after-barrier-nonaligned"),
                "Non-aligned gate should allow records after barrier on same channel");
        assertFalse(alignedRecords.contains("after-barrier-aligned"),
                "Aligned gate should NOT allow records after barrier on same channel until alignment");

        assertTrue(alignedRecords.contains("data-ch1-aligned"),
                "Aligned gate should still allow records from non-blocked channels");
        assertTrue(nonAlignedRecords.contains("data-ch1-nonaligned"),
                "Non-aligned gate should allow records from all channels");

        p0_aligned.close();
        p1_aligned.close();
        p0_nonaligned.close();
        p1_nonaligned.close();
    }

    @Test
    public void testNonAlignedGuaranteesBehaveIdentically() {
        assertFalse(ProcessingGuarantee.AT_LEAST_ONCE.isBarrierAlignment(),
                "AT_LEAST_ONCE should have barrierAlignment=false");
        assertFalse(ProcessingGuarantee.EFFECTIVELY_ONCE.isBarrierAlignment(),
                "EFFECTIVELY_ONCE should have barrierAlignment=false");
        assertFalse(ProcessingGuarantee.BEST_EFFORT.isBarrierAlignment(),
                "BEST_EFFORT should have barrierAlignment=false");

        assertTrue(ProcessingGuarantee.STRICT_EXACTLY_ONCE.isBarrierAlignment(),
                "STRICT_EXACTLY_ONCE should have barrierAlignment=true");

        boolean allSameAlignment = !ProcessingGuarantee.AT_LEAST_ONCE.isBarrierAlignment()
                && !ProcessingGuarantee.EFFECTIVELY_ONCE.isBarrierAlignment()
                && !ProcessingGuarantee.BEST_EFFORT.isBarrierAlignment();
        assertTrue(allSameAlignment,
                "AT_LEAST_ONCE, EFFECTIVELY_ONCE, and BEST_EFFORT should all have the same barrierAlignment value (false)");
    }
}
