package io.nop.stream.core.execution;

import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestRecordWriterBroadcastEmit {

    @Test
    void testBroadcastEmitLogsPartitionsOnInterrupt() throws Exception {
        ResultPartition p0 = new ResultPartition(1);
        ResultPartition p1 = new ResultPartition(1) {
            @Override
            public void write(io.nop.stream.core.streamrecord.StreamElement element) throws InterruptedException {
                throw new InterruptedException("test interrupt during broadcast");
            }
        };

        RecordWriter<String> writer = new RecordWriter<>(
                new ResultPartition[]{p0, p1}, null, null,
                new BroadcastPartitionRouter(2));

        assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                () -> writer.emit(new StreamRecord<>("data")));
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }
}
