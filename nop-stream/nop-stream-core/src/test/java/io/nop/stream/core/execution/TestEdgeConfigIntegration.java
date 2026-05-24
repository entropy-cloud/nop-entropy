package io.nop.stream.core.execution;

import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.flow.FlowControlPolicy;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EdgeConfig flow control integration into RecordWriter.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>BLOCKING_QUEUE (default) policy works normally</li>
 *   <li>CREDIT_BASED policy throws UnsupportedOperationException</li>
 *   <li>ACK_WINDOW policy throws UnsupportedOperationException</li>
 *   <li>null EdgeConfig (no config) works normally</li>
 * </ul>
 */
public class TestEdgeConfigIntegration {

    @Test
    public void testBlockingQueuePolicyWorks() throws Exception {
        ResultPartition partition = new ResultPartition();
        EdgeConfig config = new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 512, 256, 2048);

        RecordWriter<String> writer = new RecordWriter<>(partition, config);

        writer.emit(new StreamRecord<>("hello"));
        writer.close();

        StreamElement element = partition.read();
        assertNotNull(element);
        assertTrue(element.isRecord());
        assertEquals("hello", element.asRecord().getValue());

        // After close, should signal end-of-stream
        assertNull(partition.read());
    }

    @Test
    public void testNullEdgeConfigWorks() throws Exception {
        ResultPartition partition = new ResultPartition();

        // null EdgeConfig = default behavior
        RecordWriter<String> writer = new RecordWriter<>(partition, (EdgeConfig) null);

        writer.emit(new StreamRecord<>("default-behavior"));
        writer.close();

        StreamElement element = partition.read();
        assertNotNull(element);
        assertTrue(element.isRecord());
        assertEquals("default-behavior", element.asRecord().getValue());
    }

    @Test
    public void testDefaultConfigWorks() throws Exception {
        ResultPartition partition = new ResultPartition();
        EdgeConfig config = EdgeConfig.defaultConfig();

        RecordWriter<String> writer = new RecordWriter<>(partition, config);

        writer.emit(new StreamRecord<>("default-config"));
        writer.close();

        StreamElement element = partition.read();
        assertNotNull(element);
        assertTrue(element.isRecord());
        assertEquals("default-config", element.asRecord().getValue());
    }

    @Test
    public void testCreditBasedPolicyThrows() {
        ResultPartition partition = new ResultPartition();
        EdgeConfig config = new EdgeConfig(FlowControlPolicy.CREDIT_BASED, 512, 256, 2048);

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> new RecordWriter<>(partition, config));
        assertTrue(ex.getMessage().contains("CREDIT_BASED"),
                "Exception message should mention CREDIT_BASED");
    }

    @Test
    public void testAckWindowPolicyThrows() {
        ResultPartition partition = new ResultPartition();
        EdgeConfig config = new EdgeConfig(FlowControlPolicy.ACK_WINDOW, 512, 256, 2048);

        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> new RecordWriter<>(partition, config));
        assertTrue(ex.getMessage().contains("ACK_WINDOW"),
                "Exception message should mention ACK_WINDOW");
    }

    @Test
    public void testMultiPartitionWithBlockingQueue() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        EdgeConfig config = new EdgeConfig(FlowControlPolicy.BLOCKING_QUEUE, 1024, 1024, 4096);

        RecordWriter<String> writer = new RecordWriter<>(
                new ResultPartition[]{p0, p1, p2}, null, config);

        writer.emit(new StreamRecord<>("record-1"));
        writer.emit(new StreamRecord<>("record-2"));
        writer.emit(new StreamRecord<>("record-3"));
        writer.close();

        int totalRecords = 0;
        for (ResultPartition p : new ResultPartition[]{p0, p1, p2}) {
            while (p.size() > 0) {
                StreamElement elem = p.read();
                if (elem != null && elem.isRecord()) {
                    totalRecords++;
                }
            }
        }
        assertEquals(3, totalRecords, "All 3 records should be written");
    }

    @Test
    public void testMultiPartitionCreditBasedThrows() {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();
        EdgeConfig config = new EdgeConfig(FlowControlPolicy.CREDIT_BASED, 512, 256, 2048);

        assertThrows(UnsupportedOperationException.class,
                () -> new RecordWriter<>(
                        new ResultPartition[]{p0, p1}, null, config));
    }

    @Test
    public void testNullEdgeConfigWithMultiPartitions() throws Exception {
        ResultPartition p0 = new ResultPartition();
        ResultPartition p1 = new ResultPartition();

        // null EdgeConfig on multi-partition constructor
        RecordWriter<String> writer = new RecordWriter<>(
                new ResultPartition[]{p0, p1}, null, null);

        writer.emit(new StreamRecord<>("multi-null"));
        // Without partitioner, emit goes to partition 0 only
        assertEquals(1, p0.size());
        assertEquals(0, p1.size());

        writer.close();
    }
}
