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

import static io.nop.record.RecordConstants.VAR_LAST_RECORD;
import static io.nop.record.RecordConstants.VAR_NEXT_RECORD;

public class AbstractModelBasedRecordInput<Input extends IDataReaderBase, T> implements IRecordInput<T> {
    //static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordOutput.class);

    private final Input baseIn;
    private long readCount;
    private final RecordFileMeta fileMeta;

    protected final IModelBasedRecordDeserializer<Input> deserializer;
    protected final IFieldCodecContext context;

    private Map<String, Object> headerMeta;
    private T nextRecord;
    private T lastRecord;
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
            if (fileMeta.getBeforeRead() != null)
                fileMeta.getBeforeRead().call2(null, baseIn, context, context.getEvalScope());

            if (fileMeta.getResolvedHeaderType() != null && !baseIn.isEof()) {
                headerMeta = new HashMap<>();
                deserializer.readObject(baseIn, fileMeta.getResolvedHeaderType(), headerMeta, context);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    void readRepeatCount() {
        if (repeatKind == FieldRepeatKind.expr) {
            if (bodyMeta.getRepeatCountExpr() != null) {
                this.totalCount = ConvertHelper.toPrimitiveLong(bodyMeta.getRepeatCountExpr().call2(null, baseIn, null, context.getEvalScope()), NopException::new);
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
        lastRecord = ret;
        context.setValue(VAR_LAST_RECORD, lastRecord);

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
                    if (bodyMeta.getRepeatUntil() != null) {
                        boolean ret = ConvertHelper.toTruthy(bodyMeta.getRepeatUntil().call2(null, baseIn, null, context.getEvalScope()), NopException::new);
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
        if (deserializer.readObject(this.baseIn, resolvedBody, record, context)) {
            this.nextRecord = record;
            context.setValue(VAR_NEXT_RECORD, record);
        } else {
            context.setValue(VAR_NEXT_RECORD, null);
            if (fileMeta.getAfterRead() != null)
                fileMeta.getAfterRead().call2(null, baseIn, context, context.getEvalScope());
        }
    }
}
