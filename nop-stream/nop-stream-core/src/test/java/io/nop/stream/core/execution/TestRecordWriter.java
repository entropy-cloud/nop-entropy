package io.nop.stream.core.execution;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestRecordWriter {

    @Test
    public void testSinglePartitionWrite() throws Exception {
        ResultPartition partition = new ResultPartition();
        RecordWriter<String> writer = new RecordWriter<>(partition);

        writer.emit(new StreamRecord<>("test-value"));

        StreamElement element = partition.read();
        assertNotNull(element);
        assertTrue(element.isRecord());
        assertEquals("test-value", element.asRecord().getValue());

        writer.close();
    }

    @Test
    public void testWatermarkBroadcast() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        RecordWriter<String> writer = new RecordWriter<>(
                new ResultPartition[]{p0, p1, p2}, null);

        Watermark wm = new Watermark(500L);
        writer.emitWatermark(wm);

        for (int i = 0; i < 3; i++) {
            ResultPartition p = new ResultPartition[]{p0, p1, p2}[i];
            StreamElement element = p.read();
            assertNotNull(element, "Partition " + i + " should receive the watermark");
            assertTrue(element.isWatermark(),
                    "Partition " + i + " should receive a watermark element");
            assertEquals(500L, element.asWatermark().getTimestamp());
        }

        writer.close();
    }

    @Test
    public void testBarrierBroadcast() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        RecordWriter<String> writer = new RecordWriter<>(
                new ResultPartition[]{p0, p1, p2}, null);

        CheckpointBarrier barrier = new CheckpointBarrier(5L, 1000L, CheckpointType.CHECKPOINT);
        writer.emitBarrier(barrier);

        for (int i = 0; i < 3; i++) {
            ResultPartition p = new ResultPartition[]{p0, p1, p2}[i];
            StreamElement element = p.read();
            assertNotNull(element, "Partition " + i + " should receive the barrier");
            assertTrue(element.isCheckpointBarrier(),
                    "Partition " + i + " should receive a checkpoint barrier");
            assertEquals(5L, element.asCheckpointBarrier().getId());
        }

        writer.close();
    }

    @Test
    public void testMultiPartitionWithPartitioner() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();

        IPartitioner<String> partitioner = (key, numPartitions) -> {
            return Math.abs(key.hashCode()) % numPartitions;
        };

        RecordWriter<String> writer = new RecordWriter<>(
                new ResultPartition[]{p0, p1, p2}, partitioner);

        writer.emit(new StreamRecord<>("alpha"));
        writer.emit(new StreamRecord<>("beta"));
        writer.emit(new StreamRecord<>("gamma"));

        int totalRecords = p0.size() + p1.size() + p2.size();
        assertEquals(3, totalRecords,
                "All 3 records should be distributed across partitions");

        assertTrue(p0.size() > 0 || p1.size() > 0 || p2.size() > 0);

        writer.close();
    }

    @Test
    public void testCloseSignalsEndOfStream() throws Exception {
        ResultPartition partition = new ResultPartition();
        RecordWriter<String> writer = new RecordWriter<>(partition);

        writer.emit(new StreamRecord<>("last-record"));
        writer.close();

        StreamElement record = partition.read();
        assertNotNull(record);
        assertTrue(record.isRecord());
        assertEquals("last-record", record.asRecord().getValue());

        StreamElement end = partition.read();
        assertNull(end, "After close, read should return null (END_OF_STREAM)");
        assertTrue(partition.isFinished());
    }

    @Test
    public void testGetNumberOfPartitions() {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();

        RecordWriter<String> single = new RecordWriter<>(p0);
        assertEquals(1, single.getNumberOfPartitions());

        RecordWriter<String> multi = new RecordWriter<>(
                new ResultPartition[]{p1, p2}, null);
        assertEquals(2, multi.getNumberOfPartitions());
    }
}
