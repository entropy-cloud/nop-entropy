package io.nop.batch.core;

import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;

import java.io.IOException;

public class DebugResourceRecordIO implements IResourceRecordIO<String> {
    private final long maxCount;

    private long readCount;

    private long writeCount;

    public DebugResourceRecordIO(long maxCount) {
        this.maxCount = maxCount;
    }

    public long getMaxCount() {
        return maxCount;
    }

    public long getReadCount() {
        return readCount;
    }

    public long getWriteCount() {
        return writeCount;
    }

    @Override
    public IRecordInput<String> openInput(IResource resource, String encoding) {
        return new RecordInput();
    }

    class RecordInput implements IRecordInput<String> {
        @Override
        public long getReadCount() {
            return readCount;
        }

        @Override
        public boolean hasNext() {
            return readCount < maxCount;
        }

        @Override
        public String next() {
            readCount++;
            return String.valueOf(readCount);
        }

        @Override
        public void close() {
        }
    }

    @Override
    public IRecordOutput<String> openOutput(IResource resource, String encoding) {
        return new RecordOutput();
    }

    class RecordOutput implements IRecordOutput<String> {
        long writeCount;

        @Override
        public void write(String record) {
            writeCount++;
        }

        @Override
        public long getWriteCount() {
            return writeCount;
        }

        @Override
        public void flush() throws IOException {

        }

        @Override
        public void close() {
        }
    }
}
