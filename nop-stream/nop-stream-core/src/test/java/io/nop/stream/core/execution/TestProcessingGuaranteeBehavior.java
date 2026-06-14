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
        p1_aligned.write(new CheckpointBarrier(cpId, 0, CheckpointType.CHECKPOINT));

        p0_nonaligned.write(new CheckpointBarrier(cpId, 0, CheckpointType.CHECKPOINT));
        p0_nonaligned.write(new StreamRecord<>("after-barrier-nonaligned"));
        p1_nonaligned.write(new StreamRecord<>("data-ch1-nonaligned"));
        p1_nonaligned.write(new CheckpointBarrier(cpId, 0, CheckpointType.CHECKPOINT));

        p0_aligned.close();
        p1_aligned.close();
        p0_nonaligned.close();
        p1_nonaligned.close();

        List<StreamElement> alignedElems = new ArrayList<>();
        Optional<StreamElement> elem;
        while ((elem = alignedGate.read()).isPresent()) {
            alignedElems.add(elem.get());
        }

        List<StreamElement> nonAlignedElems = new ArrayList<>();
        while ((elem = nonAlignedGate.read()).isPresent()) {
            nonAlignedElems.add(elem.get());
        }

        List<String> alignedRecords = collectRecords(alignedElems);
        List<String> nonAlignedRecords = collectRecords(nonAlignedElems);

        assertTrue(nonAlignedRecords.contains("after-barrier-nonaligned"),
                "Non-aligned gate should allow records after barrier on same channel");
        assertTrue(alignedRecords.contains("after-barrier-aligned"),
                "Aligned gate should release buffered records after barrier once alignment completes");
        assertTrue(alignedRecords.contains("data-ch1-aligned"),
                "Aligned gate should still allow records from non-blocked channels");
        assertTrue(nonAlignedRecords.contains("data-ch1-nonaligned"),
                "Non-aligned gate should allow records from all channels");

        assertTrue(firstBarrierIndex(alignedElems) >= 0,
                "Aligned gate should emit an aligned barrier");
        assertTrue(firstBarrierIndex(alignedElems) < firstRecordIndexWithValue(alignedElems, "after-barrier-aligned"),
                "Aligned gate must block records after barrier until alignment completes");
    }

    private static List<String> collectRecords(List<StreamElement> elems) {
        List<String> records = new ArrayList<>();
        for (StreamElement e : elems) {
            if (e.isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) e.asRecord();
                records.add(rec.getValue());
            }
        }
        return records;
    }

    private static int firstBarrierIndex(List<StreamElement> elems) {
        for (int i = 0; i < elems.size(); i++) {
            if (elems.get(i).isCheckpointBarrier()) {
                return i;
            }
        }
        return -1;
    }

    private static int firstRecordIndexWithValue(List<StreamElement> elems, String value) {
        for (int i = 0; i < elems.size(); i++) {
            StreamElement e = elems.get(i);
            if (e.isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) e.asRecord();
                if (value.equals(rec.getValue())) {
                    return i;
                }
            }
        }
        return -1;
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
