package io.nop.record.resource;

import io.nop.dataset.record.IRecordInput;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.reader.IDataReaderBase;
import io.nop.record.model.RecordFileMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AbstractModelBaseRecordInput<Input extends IDataReaderBase, T> implements IRecordInput<T> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordOutput.class);

    private final Input baseIn;
    private long readCount;
    private final RecordFileMeta fileMeta;

    protected final FieldCodecRegistry registry;
    protected final IFieldCodecContext context;

    public AbstractModelBaseRecordInput(Input baseIn, RecordFileMeta fileMeta,
                                        FieldCodecRegistry registry, IFieldCodecContext context) {
        this.baseIn = baseIn;
        this.fileMeta = fileMeta;
        this.registry = registry;
        this.context = context;
    }

    @Override
    public long getReadCount() {
        return readCount;
    }

    @Override
    public void close() throws IOException {
        baseIn.close();
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public T next() {
        return null;
    }
}
