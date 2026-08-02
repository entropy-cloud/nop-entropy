package io.nop.stream.core.checkpoint;

import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 43, Phase 2: channel-state capture + persistence unit tests.
 *
 * <p>Verifies {@link InputChannel#captureInFlightData(boolean)} per-channel
 * semantics, {@link ChannelState} JSON round-trip, and {@link TaskEpochSnapshot}
 * backward compatibility (existing aligned snapshots without channel state must
 * still deserialize).
 */
class TestChannelStateCapture {

    /**
     * Capture drains all currently buffered records from a non-aligned channel
     * (barrierReceived=false): the records are MOVED out of the buffer.
     */
    @Test
    void testCaptureDrainsAllBufferedForNonAlignedChannel() throws Exception {
        ResultPartition partition = new ResultPartition();
        partition.write(new StreamRecord<>("a"));
        partition.write(new StreamRecord<>("b"));
        InputChannel channel = new InputChannel(partition);

        List<StreamElement> captured = channel.captureInFlightData(false);

        assertEquals(2, captured.size(), "All buffered records should be drained");
        assertEquals("a", captured.get(0).asRecord().getValue());
        assertEquals("b", captured.get(1).asRecord().getValue());
        // Drain: buffer is now empty.
        assertTrue(partition.drainBufferedElements().isEmpty(),
                "capture must move records out of the buffer (drain, not copy)");
    }

    /**
     * For an aligned channel (barrierReceived=true), the barrier has already been
     * consumed by InputGate, so the remaining buffered records ARE the post-barrier
     * records. Mechanically capture drains them too.
     */
    @Test
    void testCaptureDrainsPostBarrierRecordsForAlignedChannel() throws Exception {
        ResultPartition partition = new ResultPartition();
        // Simulate post-barrier records (barrier already read out by InputGate).
        partition.write(new StreamRecord<>("post1"));
        partition.write(new StreamRecord<>("post2"));
        InputChannel channel = new InputChannel(partition);

        List<StreamElement> captured = channel.captureInFlightData(true);

        assertEquals(2, captured.size());
        assertEquals("post1", captured.get(0).asRecord().getValue());
    }

    /**
     * Capture excludes the end-of-stream sentinel (it is a terminal signal, not data).
     */
    @Test
    void testCaptureExcludesEndOfStreamSentinel() throws Exception {
        ResultPartition partition = new ResultPartition();
        partition.write(new StreamRecord<>("x"));
        partition.close(); // places EOS sentinel
        InputChannel channel = new InputChannel(partition);

        List<StreamElement> captured = channel.captureInFlightData(false);
        assertEquals(1, captured.size(), "EOS sentinel must NOT be captured as data");
        assertEquals("x", captured.get(0).asRecord().getValue());
    }

    /**
     * ChannelState round-trips through its serializable form: records (with type
     * info), barriers, and watermarks survive encode → JSON-like map → decode.
     */
    @Test
    void testChannelStateSerializableRoundTrip() {
        ChannelState original = new ChannelState();
        original.putRecords(0, Arrays.asList(
                new StreamRecord<>("hello"),
                new Watermark(42L)
        ));
        original.putRecords(2, Arrays.asList(
                new StreamRecord<>(99)
        ));

        Map<String, Object> form = original.toSerializableForm();
        assertNotNull(form);
        ChannelState restored = ChannelState.fromSerializableForm(form);

        assertEquals(3, restored.getTotalRecordCount());
        List<StreamElement> ch0 = restored.getRecords(0);
        assertEquals(2, ch0.size());
        assertTrue(ch0.get(0).isRecord());
        assertEquals("hello", ch0.get(0).asRecord().getValue());
        assertTrue(ch0.get(1).isWatermark());
        assertEquals(42L, ch0.get(1).asWatermark().getTimestamp());

        List<StreamElement> ch2 = restored.getRecords(2);
        assertEquals(1, ch2.size());
        assertEquals(99, ch2.get(0).asRecord().getValue());
    }

    /**
     * Empty channel state yields null serializable form (compact for aligned
     * checkpoints). fromSerializableForm(null) yields an empty state.
     */
    @Test
    void testEmptyChannelStateSerializableIsNullAndBackwardCompatible() {
        ChannelState empty = new ChannelState();
        assertNull(empty.toSerializableForm(), "Empty state should serialize to null");
        assertTrue(ChannelState.fromSerializableForm(null).isEmpty());
        assertTrue(ChannelState.fromSerializableForm(new java.util.HashMap<>()).isEmpty());
    }

    /**
     * TaskEpochSnapshot carries channel state, and a snapshot WITH channel state
     * plus one WITHOUT are both constructible — backward compatible.
     */
    @Test
    void testTaskEpochSnapshotCarriesChannelState() {
        TaskLocation loc = new TaskLocation("job", "pipe", "v", 0);

        // Without channel state (aligned checkpoint).
        TaskEpochSnapshot withoutCs = new TaskEpochSnapshot(loc, 1L);
        assertNull(withoutCs.getChannelState(), "Aligned snapshot has null channel state");

        // With channel state (unaligned checkpoint).
        ChannelState cs = new ChannelState();
        cs.putRecords(1, Arrays.asList(new StreamRecord<>("in-flight")));
        TaskEpochSnapshot withCs = new TaskEpochSnapshot(loc, 1L);
        withCs.setChannelState(cs);

        assertNotNull(withCs.getChannelState());
        assertEquals(1, withCs.getChannelState().getRecords(1).size());
        assertNotSame(withoutCs, withCs);
    }
}
