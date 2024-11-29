package io.nop.record.resource;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.dataset.record.IRecordInput;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.FieldRepeatKind;
import io.nop.record.model.RecordFileBodyMeta;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.reader.IDataReaderBase;
import io.nop.record.serialization.IModelBasedRecordDeserializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class AbstractModelBasedRecordInput<Input extends IDataReaderBase, T> implements IRecordInput<T> {
    //static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordOutput.class);

    private final Input baseIn;
    private long readCount;
    private final RecordFileMeta fileMeta;

    protected final IModelBasedRecordDeserializer<Input> deserializer;
    protected final IFieldCodecContext context;

    private Map<String, Object> headerMeta;
    private T nextRecord;
    private FieldRepeatKind repeatKind;
    private RecordFileBodyMeta bodyMeta;
    private RecordObjectMeta resolvedBody;
    private long totalCount;

    public AbstractModelBasedRecordInput(Input baseIn, RecordFileMeta fileMeta,
                                         IFieldCodecContext context, IModelBasedRecordDeserializer<Input> deserializer) {
        this.baseIn = baseIn;
        this.fileMeta = fileMeta;
        this.deserializer = deserializer;
        this.context = context;
        readHeader();
        this.repeatKind = fileMeta.getBody().getRepeatKind();
        if (repeatKind == null)
            repeatKind = FieldRepeatKind.eos;
        this.bodyMeta = fileMeta.getBody();
        this.resolvedBody = fileMeta.getResolvedBodyType();
        readRepeatCount();
        if (bodyMeta != null)
            fetchNext();
    }

    void readHeader() {
        try {
            if (fileMeta.getResolvedHeaderType() != null && !baseIn.isEof()) {
                headerMeta = new HashMap<>();
                deserializer.readObject(baseIn, fileMeta.getResolvedHeaderType(), null, headerMeta, context);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    void readRepeatCount() {
        if (repeatKind == FieldRepeatKind.expr) {
            if (bodyMeta.getReadRepeatExpr() != null) {
                this.totalCount = ConvertHelper.toPrimitiveLong(bodyMeta.getReadRepeatExpr().call2(null, baseIn, null, context.getEvalScope()), NopException::new);
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
        return nextRecord != null;
    }

    @Override
    public T next() {
        if (nextRecord == null)
            throw new NoSuchElementException();
        readCount++;
        T ret = nextRecord;
        fetchNext();
        return ret;
    }

    private void fetchNext() {
        try {
            this.nextRecord = null;

            switch (repeatKind) {
                case eos: {
                    if (!baseIn.isEof())
                        readOneRecord();
                    break;
                }
                case expr: {
                    if (readCount < totalCount) {
                        readOneRecord();
                    }
                    break;
                }
                case until: {
                    if (bodyMeta.getReadRepeatUntil() != null) {
                        boolean ret = ConvertHelper.toTruthy(bodyMeta.getReadRepeatUntil().call2(null, baseIn, null, context.getEvalScope()), NopException::new);
                        if (!ret) {
                            readOneRecord();
                        }
                    } else {
                        if (!baseIn.isEof())
                            readOneRecord();
                    }
                }
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    void readOneRecord() throws IOException {
        T record = (T) resolvedBody.newBean();
        if (deserializer.readObject(this.baseIn, resolvedBody, "body", record, context)) {
            this.nextRecord = record;
        }
    }
}
