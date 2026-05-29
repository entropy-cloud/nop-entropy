/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

public class TestDataExchange {

    // ========== ResultPartition Tests ==========

    @Test
    public void testResultPartitionWriteAndRead() throws Exception {
        ResultPartition partition = new ResultPartition();
        StreamRecord<String> record = new StreamRecord<>("hello");

        partition.write(record);
        StreamElement read = partition.read();

        assertNotNull(read);
        assertTrue(read.isRecord());
        assertEquals("hello", read.<String>asRecord().getValue());
    }

    @Test
    public void testResultPartitionClose() throws Exception {
        ResultPartition partition = new ResultPartition();
        partition.close();

        assertTrue(partition.isFinished());
        // After close, read should return null (end-of-stream)
        assertNull(partition.read());
    }

    @Test
    public void testResultPartitionReadWithTimeout() throws Exception {
        ResultPartition partition = new ResultPartition();
        partition.close();

        StreamElement element = partition.read(100, TimeUnit.MILLISECONDS);
        assertNull(element);
    }

    @Test
    public void testResultPartitionCannotWriteAfterClose() throws Exception {
        ResultPartition partition = new ResultPartition();
        partition.close();

        assertThrows(io.nop.stream.core.exceptions.StreamException.class, () ->
            partition.write(new StreamRecord<>("data")));
    }

    @Test
    public void testResultPartitionDrain() throws Exception {
        ResultPartition partition = new ResultPartition();
        partition.write(new StreamRecord<>("a"));
        partition.write(new StreamRecord<>("b"));
        partition.close();

        assertEquals("a", partition.read().asRecord().getValue());
        assertEquals("b", partition.read().asRecord().getValue());
        assertNull(partition.read()); // END_OF_STREAM sentinel consumed
    }

    @Test
    public void testResultPartitionInvalidCapacity() {
        assertThrows(StreamException.class, () -> new ResultPartition(0));
        assertThrows(StreamException.class, () -> new ResultPartition(-1));
    }

    // ========== InputChannel Tests ==========

    @Test
    public void testInputChannelRead() throws Exception {
        ResultPartition partition = new ResultPartition();
        InputChannel channel = new InputChannel(partition);

        partition.write(new StreamRecord<>("data"));
        StreamElement element = channel.read();

        assertNotNull(element);
        assertEquals("data", element.asRecord().getValue());
    }

    @Test
    public void testInputChannelIsFinished() {
        ResultPartition partition = new ResultPartition();
        InputChannel channel = new InputChannel(partition);

        assertFalse(channel.isFinished());
        partition.close();
        assertTrue(channel.isFinished());
    }

    @Test
    public void testInputChannelNullPartition() {
        assertThrows(StreamException.class, () -> new InputChannel(null));
    }

    // ========== RecordWriter Tests ==========

    @Test
    public void testRecordWriterSinglePartition() throws Exception {
        ResultPartition partition = new ResultPartition();
        RecordWriter<String> writer = new RecordWriter<>(partition);

        writer.emit(new StreamRecord<>("test"));
        writer.close();

        StreamElement element = partition.read();
        assertNotNull(element);
        assertEquals("test", element.asRecord().getValue());
        assertNull(partition.read());
    }

    @Test
    public void testRecordWriterWatermarkBroadcast() throws Exception {
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        RecordWriter<String> writer = new RecordWriter<>(new ResultPartition[]{p1, p2}, null);

        Watermark wm = new Watermark(100L);
        writer.emitWatermark(wm);

        StreamElement e1 = p1.read();
        StreamElement e2 = p2.read();

        assertTrue(e1.isWatermark());
        assertTrue(e2.isWatermark());
        assertEquals(100L, e1.asWatermark().getTimestamp());
        assertEquals(100L, e2.asWatermark().getTimestamp());

        writer.close();
    }

    @Test
    public void testRecordWriterBarrierBroadcast() throws Exception {
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        RecordWriter<String> writer = new RecordWriter<>(new ResultPartition[]{p1, p2}, null);

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        writer.emitBarrier(barrier);

        assertTrue(p1.read().isCheckpointBarrier());
        assertTrue(p2.read().isCheckpointBarrier());

        writer.close();
    }

    @Test
    public void testRecordWriterWithPartitioner() throws Exception {
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        // Partitioner that routes even values to partition 0, odd to partition 1
        RecordWriter<Integer> writer = new RecordWriter<>(
            new ResultPartition[]{p1, p2},
            (key, numPartitions) -> key % numPartitions
        );

        writer.emit(new StreamRecord<>(0));
        writer.emit(new StreamRecord<>(1));
        writer.emit(new StreamRecord<>(2));

        // 0 -> partition 0, 1 -> partition 1, 2 -> partition 0
        assertEquals(0, p1.read().asRecord().getValue());
        assertEquals(2, p1.read().asRecord().getValue());
        assertEquals(1, p2.read().asRecord().getValue());

        writer.close();
    }

    @Test
    public void testRecordWriterNullPartition() {
        assertThrows(StreamException.class, () -> new RecordWriter<String>((ResultPartition) null));
    }

    // ========== RecordReader Tests ==========

    @Test
    public void testRecordReaderRead() throws Exception {
        ResultPartition partition = new ResultPartition();
        InputChannel channel = new InputChannel(partition);
        RecordReader<String> reader = new RecordReader<>(channel);

        partition.write(new StreamRecord<>("hello"));
        Optional<StreamElement> element = reader.read();

        assertTrue(element.isPresent());
        assertEquals("hello", element.get().asRecord().getValue());
    }

    @Test
    public void testRecordReaderEndOfStream() throws Exception {
        ResultPartition partition = new ResultPartition();
        InputChannel channel = new InputChannel(partition);
        RecordReader<String> reader = new RecordReader<>(channel);

        partition.close();
        Optional<StreamElement> element = reader.read();
        assertFalse(element.isPresent());
    }

    @Test
    public void testRecordReaderNullChannel() {
        assertThrows(StreamException.class, () -> new RecordReader<String>((InputChannel) null));
    }

    // ========== InputGate Tests ==========

    @Test
    public void testInputGateSingleChannel() throws Exception {
        ResultPartition partition = new ResultPartition();
        InputChannel channel = new InputChannel(partition);
        InputGate gate = new InputGate(channel);

        partition.write(new StreamRecord<>("data"));
        Optional<StreamElement> element = gate.read();

        assertTrue(element.isPresent());
        assertEquals("data", element.get().asRecord().getValue());
    }

    @Test
    public void testInputGateSingleChannelEndOfStream() throws Exception {
        ResultPartition partition = new ResultPartition();
        InputChannel channel = new InputChannel(partition);
        InputGate gate = new InputGate(channel);

        partition.close();
        Optional<StreamElement> element = gate.read();
        assertFalse(element.isPresent());
    }

    @Test
    public void testInputGateMultiChannelRead() throws Exception {
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p1), new InputChannel(p2));
        InputGate gate = new InputGate(channels);

        p1.write(new StreamRecord<>("a"));
        p2.write(new StreamRecord<>("b"));
        p1.close();
        p2.close();

        List<String> results = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) {
                break;
            }
            if (element.get().isRecord()) {
                @SuppressWarnings("unchecked")
                StreamRecord<String> rec = (StreamRecord<String>) (StreamRecord<?>) element.get().asRecord();
                results.add(rec.getValue());
            }
        }

        assertEquals(2, results.size());
        assertTrue(results.contains("a"));
        assertTrue(results.contains("b"));
    }

    @Test
    public void testInputGateWatermarkMerging() throws Exception {
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p1), new InputChannel(p2));
        InputGate gate = new InputGate(channels);

        // Write watermark 100 to channel 0
        p1.write(new Watermark(100));
        // Write watermark 200 to channel 1
        p2.write(new Watermark(200));
        p1.close();
        p2.close();

        List<Long> watermarkTimestamps = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) {
                break;
            }
            if (element.get().isWatermark()) {
                watermarkTimestamps.add(element.get().asWatermark().getTimestamp());
            }
        }

        // First emitted watermark should be min(100, 200) = 100
        assertFalse(watermarkTimestamps.isEmpty());
        assertEquals(100L, watermarkTimestamps.get(0));
    }

    @Test
    public void testInputGateBarrierAlignment() throws Exception {
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p1), new InputChannel(p2));
        InputGate gate = new InputGate(channels);

        // Send barrier on channel 0
        p1.write(new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT));
        // Send barrier on channel 1
        p2.write(new CheckpointBarrier(1L, 1000L, CheckpointType.CHECKPOINT));
        p1.close();
        p2.close();

        List<CheckpointBarrier> barriers = new ArrayList<>();
        while (true) {
            Optional<StreamElement> element = gate.read();
            if (!element.isPresent()) {
                break;
            }
            if (element.get().isCheckpointBarrier()) {
                barriers.add(element.get().asCheckpointBarrier());
            }
        }

        // Should emit exactly one aligned barrier
        assertEquals(1, barriers.size());
        assertEquals(1L, barriers.get(0).getId());
    }

    @Test
    public void testInputGateIsAllFinished() {
        ResultPartition p1 = new ResultPartition();
        ResultPartition p2 = new ResultPartition();
        List<InputChannel> channels = Arrays.asList(new InputChannel(p1), new InputChannel(p2));
        InputGate gate = new InputGate(channels);

        assertFalse(gate.isAllFinished());
        p1.close();
        assertFalse(gate.isAllFinished());
        p2.close();
        assertTrue(gate.isAllFinished());
    }

    @Test
    public void testInputGateNullChannels() {
        assertThrows(StreamException.class, () -> new InputGate((List<InputChannel>) null));
        assertThrows(StreamException.class, () -> new InputGate(new ArrayList<>()));
    }

    // ========== Producer-Consumer Concurrency Test ==========

    @Test
    public void testConcurrentProducerConsumer() throws Exception {
        ResultPartition partition = new ResultPartition();
        int itemCount = 1000;

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread producer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < itemCount; i++) {
                    partition.write(new StreamRecord<>(i));
                }
                partition.close();
            } catch (Exception e) {
                error.compareAndSet(null, e);
            }
        });

        List<Integer> consumed = new ArrayList<>();
        Thread consumer = new Thread(() -> {
            try {
                startLatch.await();
                while (true) {
                    StreamElement element = partition.read();
                    if (element == null) {
                        break;
                    }
                    @SuppressWarnings("unchecked")
                    StreamRecord<Integer> rec = (StreamRecord<Integer>) (StreamRecord<?>) element.asRecord();
                    consumed.add(rec.getValue());
                }
            } catch (Exception e) {
                error.compareAndSet(null, e);
            }
        });

        producer.start();
        consumer.start();
        startLatch.countDown();

        producer.join(5000);
        consumer.join(5000);

        assertNull(error.get());
        assertEquals(itemCount, consumed.size());
        for (int i = 0; i < itemCount; i++) {
            assertEquals(i, consumed.get(i));
        }
    }
}
