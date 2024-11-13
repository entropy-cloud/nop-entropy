package io.nop.record.resource;

import io.nop.dataset.record.IRecordInput;
import io.nop.record.codec.FieldCodecRegistry;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.IDataReaderBase;
import io.nop.record.serialization.IModelBasedRecordDeserializer;
import io.nop.record.serialization.IModelBasedRecordSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class AbstractModelBasedRecordInput<Input extends IDataReaderBase, T> implements IRecordInput<T> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordOutput.class);

    private final Input baseIn;
    private long readCount;
    private final RecordFileMeta fileMeta;

    protected final IModelBasedRecordDeserializer<Input> deserializer;
    protected final IFieldCodecContext context;

    public AbstractModelBasedRecordInput(Input baseIn, RecordFileMeta fileMeta,
                                         IFieldCodecContext context, IModelBasedRecordDeserializer<Input> deserializer) {
        this.baseIn = baseIn;
        this.fileMeta = fileMeta;
        this.deserializer = deserializer;
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
