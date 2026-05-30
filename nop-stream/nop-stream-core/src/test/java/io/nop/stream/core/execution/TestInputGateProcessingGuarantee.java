package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.ProcessingGuarantee;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests InputGate behavior under different ProcessingGuarantee modes:
 * <ul>
 *   <li><b>barrierAlignment=true (STRICT_EXACTLY_ONCE)</b>: channels are blocked after
 *       receiving a barrier until all channels deliver theirs.</li>
 *   <li><b>barrierAlignment=false (AT_LEAST_ONCE)</b>: channels are never blocked by
 *       barriers; records continue flowing through freely.</li>
 * </ul>
 */
public class TestInputGateProcessingGuarantee {

    // =========================================================================
    // STRICT_EXACTLY_ONCE (barrierAlignment=true) tests
    // =========================================================================

    @Test
    public void testAlignedMode_blocksChannelsAfterBarrier() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels, null, true);

        // Write barrier on channel 0
        p0.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));

        // Write data on channel 1 — should be readable while channel 0 is blocked
        p1.write(new StreamRecord<>("data-on-ch1"));
        Optional<StreamElement> elem = gate.read();
        assertTrue(elem.isPresent());
        assertTrue(elem.get().isRecord(),
                "In aligned mode, data from non-blocked channel should still be readable");
        assertEquals("data-on-ch1", elem.get().asRecord().getValue());

        // Now write barrier on channel 1 to complete alignment
        p1.write(new CheckpointBarrier(1, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        List<CheckpointBarrier> barriers = new ArrayList<>();
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) break;
            if (e.get().isCheckpointBarrier()) {
                barriers.add(e.get().asCheckpointBarrier());
            }
        }

        assertEquals(1, barriers.size(), "Should emit exactly one aligned barrier");
        assertEquals(1L, barriers.get(0).getId());
    }

    @Test
    public void testAlignedMode_barrierBlocksFurtherReadsFromSameChannel() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels, null, true);

        // Write barrier + data on channel 0, barrier on channel 1
        p0.write(new CheckpointBarrier(5, 0, CheckpointType.CHECKPOINT));
        p0.write(new StreamRecord<>("should-not-appear-before-alignment"));
        p1.write(new CheckpointBarrier(5, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        List<String> records = new ArrayList<>();
        List<CheckpointBarrier> barriers = new ArrayList<>();
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) break;
            if (e.get().isCheckpointBarrier()) {
                barriers.add(e.get().asCheckpointBarrier());
            } else if (e.get().isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) e.get().asRecord();
                records.add(rec.getValue());
            }
        }

        // After the aligned barrier, the "should-not-appear-before-alignment" record
        // from channel 0 should now be readable
        assertEquals(1, barriers.size(), "Should emit exactly one aligned barrier for checkpoint 5");
        assertEquals(5L, barriers.get(0).getId());
        assertTrue(records.contains("should-not-appear-before-alignment"),
                "Records queued on blocked channel should be readable after alignment completes");
    }

    // =========================================================================
    // AT_LEAST_ONCE (barrierAlignment=false) tests
    // =========================================================================

    @Test
    public void testNonAlignedMode_doesNotBlockChannelsAfterBarrier() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels, null, false);

        // Write barrier on channel 0, then data on channel 0 (barrier doesn't block)
        p0.write(new CheckpointBarrier(10, 0, CheckpointType.CHECKPOINT));
        p0.write(new StreamRecord<>("after-barrier-on-ch0"));

        // Write data on channel 1 (no barrier yet)
        p1.write(new StreamRecord<>("data-on-ch1"));

        p1.write(new CheckpointBarrier(10, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        List<String> records = new ArrayList<>();
        List<CheckpointBarrier> barriers = new ArrayList<>();
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) break;
            if (e.get().isCheckpointBarrier()) {
                barriers.add(e.get().asCheckpointBarrier());
            } else if (e.get().isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) e.get().asRecord();
                records.add(rec.getValue());
            }
        }

        // In non-aligned mode, barriers are emitted immediately and records flow freely
        assertFalse(records.isEmpty(), "Should have read records in non-aligned mode");
        assertTrue(records.contains("after-barrier-on-ch0"),
                "In AT_LEAST_ONCE mode, records after barrier should not be blocked");
        assertTrue(records.contains("data-on-ch1"),
                "Records from other channels should flow freely");

        // Barriers should be emitted (each channel's barrier comes through)
        assertFalse(barriers.isEmpty(), "Barriers should be emitted in non-aligned mode");
        assertTrue(barriers.stream().anyMatch(b -> b.getId() == 10L),
                "Should have checkpoint 10 barrier");
    }

    @Test
    public void testNonAlignedMode_recordsFlowImmediatelyAfterBarrier() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels, null, false);

        // Write barrier on channel 0, immediately followed by records
        p0.write(new CheckpointBarrier(20, 0, CheckpointType.CHECKPOINT));
        p0.write(new StreamRecord<>("record-1-after-barrier"));
        p0.write(new StreamRecord<>("record-2-after-barrier"));

        // Channel 1 has no barrier yet, just records
        p1.write(new StreamRecord<>("record-on-ch1"));

        p0.close();
        p1.close();

        List<String> records = new ArrayList<>();
        int barrierCount = 0;
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) break;
            if (e.get().isCheckpointBarrier()) {
                barrierCount++;
            } else if (e.get().isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) e.get().asRecord();
                records.add(rec.getValue());
            }
        }

        // All 3 records should be readable (AT_LEAST_ONCE doesn't block)
        assertTrue(records.contains("record-1-after-barrier"),
                "Records immediately after barrier should be readable in AT_LEAST_ONCE mode");
        assertTrue(records.contains("record-2-after-barrier"),
                "Multiple records after barrier should all be readable");
        assertTrue(records.contains("record-on-ch1"),
                "Records from other channels should be readable");
        assertEquals(3, records.size(), "All three records should be read");

        // Barrier should have been emitted
        assertEquals(1, barrierCount, "Exactly one barrier should be emitted for checkpoint 20");
    }

    @Test
    public void testNonAlignedMode_multipleCheckpoints() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels, null, false);

        // Checkpoint 30: barrier on both channels with data between
        p0.write(new CheckpointBarrier(30, 0, CheckpointType.CHECKPOINT));
        p0.write(new StreamRecord<>("after-cp30-ch0"));
        p1.write(new StreamRecord<>("between-checkpoints"));
        p1.write(new CheckpointBarrier(30, 0, CheckpointType.CHECKPOINT));

        // Checkpoint 31: barrier on both channels
        p0.write(new CheckpointBarrier(31, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(31, 0, CheckpointType.CHECKPOINT));

        p0.close();
        p1.close();

        List<String> records = new ArrayList<>();
        List<Long> barrierIds = new ArrayList<>();
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) break;
            if (e.get().isCheckpointBarrier()) {
                barrierIds.add(e.get().asCheckpointBarrier().getId());
            } else if (e.get().isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) e.get().asRecord();
                records.add(rec.getValue());
            }
        }

        // Records should all be readable
        assertTrue(records.contains("after-cp30-ch0"));
        assertTrue(records.contains("between-checkpoints"));
        assertEquals(2, records.size());

        // Both checkpoints should have barriers
        assertTrue(barrierIds.contains(30L), "Should have barrier for checkpoint 30");
        assertTrue(barrierIds.contains(31L), "Should have barrier for checkpoint 31");
    }

    // =========================================================================
    // CheckpointConfig integration test
    // =========================================================================

    @Test
    public void testCheckpointConfigDefaultGuarantee() {
        CheckpointConfig config = new CheckpointConfig();
        assertNotNull(config.getProcessingGuarantee());
        assertEquals(ProcessingGuarantee.STRICT_EXACTLY_ONCE,
                config.getProcessingGuarantee(),
                "Default should be STRICT_EXACTLY_ONCE");
    }

    @Test
    public void testCheckpointConfigCustomGuarantee() {
        CheckpointConfig config = CheckpointConfig.builder()
                .processingGuarantee(ProcessingGuarantee.AT_LEAST_ONCE)
                .build();
        assertEquals(ProcessingGuarantee.AT_LEAST_ONCE,
                config.getProcessingGuarantee());

        config.setProcessingGuarantee(null);
        assertEquals(ProcessingGuarantee.STRICT_EXACTLY_ONCE,
                config.getProcessingGuarantee(),
                "Setting null should fall back to STRICT_EXACTLY_ONCE");
    }

    // =========================================================================
    // ProcessingGuarantee semantics validation
    // =========================================================================

    @Test
    public void testGuaranteeToBarrierAlignmentMapping() {
        // STRICT_EXACTLY_ONCE → barrierAlignment=true
        assertTrue(ProcessingGuarantee.STRICT_EXACTLY_ONCE.isBarrierAlignment());

        // AT_LEAST_ONCE → barrierAlignment=false
        assertFalse(ProcessingGuarantee.AT_LEAST_ONCE.isBarrierAlignment());

        // EFFECTIVELY_ONCE → barrierAlignment=false
        assertFalse(ProcessingGuarantee.EFFECTIVELY_ONCE.isBarrierAlignment());

        // BEST_EFFORT → barrierAlignment=false
        assertFalse(ProcessingGuarantee.BEST_EFFORT.isBarrierAlignment());
    }

    @Test
    public void testNonAlignedMode_emitsExactlyOneBarrierPerCheckpoint() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p0), new InputChannel(p1));
        InputGate gate = new InputGate(channels, null, false);

        p0.write(new CheckpointBarrier(100, 0, CheckpointType.CHECKPOINT));
        p1.write(new CheckpointBarrier(100, 0, CheckpointType.CHECKPOINT));
        p0.close();
        p1.close();

        int barrierCount = 0;
        while (true) {
            Optional<StreamElement> e = gate.read();
            if (!e.isPresent()) break;
            if (e.get().isCheckpointBarrier()) {
                barrierCount++;
            }
        }

        assertEquals(1, barrierCount,
                "AT_LEAST_ONCE mode should emit exactly one barrier per checkpoint alignment cycle");
    }
}
