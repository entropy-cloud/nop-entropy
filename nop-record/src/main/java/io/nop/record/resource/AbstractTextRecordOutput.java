package io.nop.record.resource;

import io.nop.api.core.exceptions.NopException;
import io.nop.dataset.record.IRecordOutput;
import io.nop.record.output.IRecordTextOutput;
import io.nop.record.output.SimpleTextOutput;

import java.io.IOException;

public abstract class AbstractTextRecordOutput<T> implements IRecordOutput<T> {
    protected final IRecordTextOutput out;

    protected long writeCount;

    public AbstractTextRecordOutput(Appendable out) {
        this(new SimpleTextOutput(out));
    }

    public AbstractTextRecordOutput(IRecordTextOutput out) {
        this.out = out;
    }

    @Override
    public long getWriteCount() {
        return writeCount;
    }

    @Override
    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
