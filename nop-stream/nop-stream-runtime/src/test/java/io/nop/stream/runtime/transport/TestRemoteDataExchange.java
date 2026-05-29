package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.RecordWriter;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests data exchange through IMessageService using LocalMessageService
 * for in-process simulation.
 */
class TestRemoteDataExchange {

    private LocalMessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new LocalMessageService();
    }

    @AfterEach
    void tearDown() {
        messageService.clearConsumers();
    }

    @Test
    void testTopicNaming() {
        String topic = StreamTopicNaming.buildTopic("job-1", "src->tgt", 0, 1);
        assertEquals("nop-stream.job-1.src->tgt.0.1", topic);
    }

    @Test
    void testRemoteResultPartitionSendsEnvelope() throws Exception {
        TypeRegistry typeRegistry = new TypeRegistry();
        typeRegistry.register("edge-1", String.class.getName());

        String topic = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 0);
        RemoteResultPartition partition = new RemoteResultPartition(
                messageService, topic, typeRegistry, "edge-1", "token-1", 1L);

        // Subscribe a listener to capture messages
        List<Object> received = new ArrayList<>();
        messageService.subscribe(topic, (t, msg, ctx) -> {
            received.add(msg);
            return null;
        });

        // Write a record
        partition.write(new StreamRecord<>("hello"));

        assertEquals(1, received.size());
        assertInstanceOf(StreamMessageEnvelope.class, received.get(0));
        StreamMessageEnvelope env = (StreamMessageEnvelope) received.get(0);
        assertEquals(StreamMessageEnvelope.TYPE_STREAM_RECORD, env.getType());
        assertEquals("token-1", env.getFencingToken());
        assertEquals(1L, env.getEpochId());
    }

    @Test
    void testRemoteResultPartitionClose() throws Exception {
        String topic = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 0);
        RemoteResultPartition partition = new RemoteResultPartition(
                messageService, topic, null, "edge-1", "token-1", 1L);

        List<Object> received = new ArrayList<>();
        messageService.subscribe(topic, (t, msg, ctx) -> {
            received.add(msg);
            return null;
        });

        partition.close();
        assertTrue(partition.isFinished());

        // Verify END_OF_STREAM was sent
        assertEquals(1, received.size());
        StreamMessageEnvelope env = (StreamMessageEnvelope) received.get(0);
        assertEquals(StreamMessageEnvelope.TYPE_CONTROL, env.getType());
        assertEquals("END_OF_STREAM", env.getPayload());
    }

    @Test
    void testRemoteResultPartitionRejectsWriteAfterClose() {
        String topic = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 0);
        RemoteResultPartition partition = new RemoteResultPartition(
                messageService, topic, null, "edge-1", "token-1", 1L);
        partition.close();

        assertThrows(StreamException.class, () ->
                partition.write(new StreamRecord<>("fail")));
    }

    @Test
    void testRemoteInputChannelReceivesRecords() throws Exception {
        String topic = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 0);
        TypeRegistry typeRegistry = new TypeRegistry();
        typeRegistry.register("edge-1", String.class.getName());

        // Create producer and consumer
        RemoteResultPartition producer = new RemoteResultPartition(
                messageService, topic, typeRegistry, "edge-1", "token-1", 1L);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, "token-1", 1L);

        try {
            // Send records
            producer.write(new StreamRecord<>("record-1"));
            producer.write(new StreamRecord<>("record-2"));

            // Read with timeout to allow message propagation
            StreamElement elem1 = consumer.read(2, TimeUnit.SECONDS);
            assertNotNull(elem1);
            assertTrue(elem1.isRecord());
            assertEquals("record-1", elem1.asRecord().getValue());

            StreamElement elem2 = consumer.read(2, TimeUnit.SECONDS);
            assertNotNull(elem2);
            assertTrue(elem2.isRecord());
            assertEquals("record-2", elem2.asRecord().getValue());
        } finally {
            consumer.close();
        }
    }

    @Test
    void testRemoteInputChannelEndOfStream() throws Exception {
        String topic = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 0);

        RemoteResultPartition producer = new RemoteResultPartition(
                messageService, topic, null, "edge-1", "token-1", 1L);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, "token-1", 1L);

        try {
            producer.write(new StreamRecord<>("last"));
            producer.close();

            // Read the record first
            StreamElement elem = consumer.read(2, TimeUnit.SECONDS);
            assertNotNull(elem);
            assertEquals("last", elem.asRecord().getValue());

            // Next read should return null (end-of-stream)
            StreamElement eos = consumer.read(2, TimeUnit.SECONDS);
            assertNull(eos);
            assertTrue(consumer.isFinished());
        } finally {
            consumer.close();
        }
    }

    @Test
    void testRemoteInputChannelFencingRejectsStaleMessages() throws Exception {
        String topic = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 0);

        // Consumer expects token-2, epoch 2
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, "token-2", 2L);

        // Producer sends with wrong fencing token
        TypeRegistry typeRegistry = new TypeRegistry();
        typeRegistry.register("edge-1", String.class.getName());
        RemoteResultPartition staleProducer = new RemoteResultPartition(
                messageService, topic, typeRegistry, "edge-1", "token-1", 1L);

        try {
            staleProducer.write(new StreamRecord<>("stale"));

            // Give a moment for delivery
            Thread.sleep(100);

            // Consumer should not have received the stale message
            StreamElement elem = consumer.read(200, TimeUnit.MILLISECONDS);
            assertNull(elem); // timeout, no valid messages
            assertEquals(0, consumer.queueSize());
        } finally {
            consumer.close();
        }
    }

    @Test
    void testRemoteBarrierExchange() throws Exception {
        String topic = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 0);

        RemoteResultPartition producer = new RemoteResultPartition(
                messageService, topic, null, "edge-1", "token-1", 1L);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, "token-1", 1L);

        try {
            // Send a checkpoint barrier
            CheckpointBarrier barrier = new CheckpointBarrier(1L, 100L, CheckpointType.CHECKPOINT);
            producer.write(barrier);

            StreamElement elem = consumer.read(2, TimeUnit.SECONDS);
            assertNotNull(elem);
            assertTrue(elem.isCheckpointBarrier());
            assertEquals(1L, elem.asCheckpointBarrier().getId());
            assertEquals(100L, elem.asCheckpointBarrier().getTimestamp());
        } finally {
            consumer.close();
        }
    }

    @Test
    void testRemoteWatermarkExchange() throws Exception {
        String topic = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 0);

        RemoteResultPartition producer = new RemoteResultPartition(
                messageService, topic, null, "edge-1", "token-1", 1L);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, "token-1", 1L);

        try {
            producer.write(new Watermark(42L));

            StreamElement elem = consumer.read(2, TimeUnit.SECONDS);
            assertNotNull(elem);
            assertTrue(elem.isWatermark());
            assertEquals(42L, elem.asWatermark().getTimestamp());
        } finally {
            consumer.close();
        }
    }

    @Test
    void testRecordWriterWithRemotePartitions() throws Exception {
        // Simulate: source with parallelism=1, target with parallelism=2
        TypeRegistry typeRegistry = new TypeRegistry();
        typeRegistry.register("edge-1", String.class.getName());

        String topic0 = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 0);
        String topic1 = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 1);

        RemoteResultPartition p0 = new RemoteResultPartition(
                messageService, topic0, typeRegistry, "edge-1", "token-1", 1L);
        RemoteResultPartition p1 = new RemoteResultPartition(
                messageService, topic1, typeRegistry, "edge-1", "token-1", 1L);

        RemoteInputChannel c0 = new RemoteInputChannel(
                messageService, topic0, "token-1", 1L);
        RemoteInputChannel c1 = new RemoteInputChannel(
                messageService, topic1, "token-1", 1L);

        try {
            // RecordWriter with 2 remote partitions, using forward partitioner
            RecordWriter<Object> writer = new RecordWriter<>(
                    new ResultPartition[]{p0, p1}, null);

            // Emit records (forward without partitioner goes to partition 0)
            writer.emit(new StreamRecord<>("to-p0"));
            writer.emit(new StreamRecord<>("to-p0-again"));

            // Close (broadcasts to all partitions)
            writer.close();

            // Consumer on partition 0 should get both records + EOS
            StreamElement e1 = c0.read(2, TimeUnit.SECONDS);
            assertNotNull(e1);
            assertEquals("to-p0", e1.asRecord().getValue());

            StreamElement e2 = c0.read(2, TimeUnit.SECONDS);
            assertNotNull(e2);
            assertEquals("to-p0-again", e2.asRecord().getValue());

            // EOS
            StreamElement eos = c0.read(2, TimeUnit.SECONDS);
            assertNull(eos);
            assertTrue(c0.isFinished());

            // Consumer on partition 1 should get EOS only (no records routed to it)
            StreamElement eos1 = c1.read(2, TimeUnit.SECONDS);
            assertNull(eos1);
            assertTrue(c1.isFinished());
        } finally {
            c0.close();
            c1.close();
        }
    }

    @Test
    void testInputChannelCompatibility() throws Exception {
        // Verify RemoteInputChannel works as InputChannel
        String topic = StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 0);
        RemoteInputChannel remote = new RemoteInputChannel(
                messageService, topic, "t1", 1L);

        // Can use as InputChannel (polymorphism)
        InputChannel channel = remote;

        try {
            // No data yet - should timeout
            StreamElement elem = channel.read(100, TimeUnit.MILLISECONDS);
            assertNull(elem);
            assertFalse(channel.isFinished());
        } finally {
            remote.close();
        }
    }
}
