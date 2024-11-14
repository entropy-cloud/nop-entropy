package io.nop.record.resource;

import io.nop.api.core.exceptions.NopException;
import io.nop.dataset.record.IRecordInput;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.reader.IDataReaderBase;
import io.nop.record.serialization.IModelBasedRecordDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AbstractModelBasedRecordInput<Input extends IDataReaderBase, T> implements IRecordInput<T> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordOutput.class);

    private final Input baseIn;
    private long readCount;
    private final RecordFileMeta fileMeta;

    protected final IModelBasedRecordDeserializer<Input> deserializer;
    protected final IFieldCodecContext context;

    private Map<String, Object> headerMeta;

    public AbstractModelBasedRecordInput(Input baseIn, RecordFileMeta fileMeta,
                                         IFieldCodecContext context, IModelBasedRecordDeserializer<Input> deserializer) {
        this.baseIn = baseIn;
        this.fileMeta = fileMeta;
        this.deserializer = deserializer;
        this.context = context;
        readHeader();
    }

    void readHeader() {
        if (fileMeta.getHeader() != null) {
            headerMeta = new HashMap<>();
            try {
                deserializer.readObject(baseIn, fileMeta.getHeader(), null, headerMeta, context);
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public Map<String, Object> getHeaderMeta() {
        return headerMeta;
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
