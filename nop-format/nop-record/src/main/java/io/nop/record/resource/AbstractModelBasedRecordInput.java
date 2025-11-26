package io.nop.record.resource;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dataset.record.IRecordInput;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.FieldRepeatKind;
import io.nop.record.model.RecordFileBodyMeta;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.reader.IDataReaderBase;
import io.nop.record.serialization.IModelBasedRecordDeserializer;
import io.nop.record.serialization.StreamingReadResult;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static io.nop.core.CoreErrors.ARG_READ_COUNT;
import static io.nop.record.RecordConstants.VAR_LAST_RECORD;
import static io.nop.record.RecordConstants.VAR_NEXT_RECORD;
import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ARG_FIELD_PATH;
import static io.nop.record.RecordErrors.ARG_REAL_READ_POS;
import static io.nop.record.RecordErrors.ARG_TOTAL_COUNT;
import static io.nop.record.RecordErrors.ERR_RECORD_NO_ENOUGH_ITEMS;

public class AbstractModelBasedRecordInput<Input extends IDataReaderBase, T> implements IRecordInput<T> {
    //static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordOutput.class);

    private final Input baseIn;
    private long readCount;
    private final RecordFileMeta fileMeta;

    protected final IModelBasedRecordDeserializer<Input> deserializer;
    protected final IFieldCodecContext context;

    private Map<String, Object> headerMeta;
    private Map<String, Object> trailerMeta;
    private T nextRecord;
    private T lastRecord;
    private StreamingReadResult nextRecordIt;
    private FieldRepeatKind repeatKind;
    private RecordFileBodyMeta bodyMeta;
    private RecordObjectMeta resolvedBody;
    private long totalCount;
    private int fetchCount;
    private final boolean useStreaming;
    private boolean eof;

    public AbstractModelBasedRecordInput(Input baseIn, RecordFileMeta fileMeta, boolean useStreaming,
                                         IFieldCodecContext context, IModelBasedRecordDeserializer<Input> deserializer) {
        this.baseIn = baseIn;
        this.fileMeta = fileMeta;
        this.deserializer = deserializer;
        this.context = context;
        this.useStreaming = useStreaming;
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

    public boolean isUseStreaming() {
        return useStreaming;
    }

    @Override
    public Map<String, Object> getTrailerMeta() {
        return trailerMeta;
    }

    void readHeader() {
        try {
            if (fileMeta.getBeforeRead() != null)
                fileMeta.getBeforeRead().call2(null, baseIn, context, context.getEvalScope());

            if (fileMeta.getResolvedHeaderType() != null && !baseIn.isEof()) {
                headerMeta = new LinkedHashMap<>();
                deserializer.readObject(baseIn, fileMeta.getResolvedHeaderType(), headerMeta, context);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    void readRepeatCount() {
        if (repeatKind == FieldRepeatKind.expr) {
            Object count = null;
            if (bodyMeta.getRepeatCountFieldName() != null) {
                count = BeanTool.getComplexProperty(headerMeta, bodyMeta.getRepeatCountFieldName());
            } else {
                if (bodyMeta.getRepeatCountExpr() != null) {
                    count = bodyMeta.getRepeatCountExpr().call2(null, baseIn, null, context.getEvalScope());
                } else if (bodyMeta.getRepeatCountField() != null) {
                    try {
                        count = deserializer.readSimpleField(baseIn, bodyMeta.getRepeatCountField(), headerMeta, context);
                    } catch (IOException e) {
                        throw NopException.adapt(e);
                    }
                }
            }

            this.totalCount = ConvertHelper.toPrimitiveInt(count, err -> newError(err, baseIn, context).source(bodyMeta).param(ARG_FIELD_NAME, "body"));
        }
    }

    protected NopException newError(ErrorCode errorCode, Input in, IFieldCodecContext context) {
        return new NopException(errorCode)
                .param(ARG_REAL_READ_POS, in.realPos())
                .param(ARG_FIELD_PATH, context.getFieldPath());
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
        if (eof)
            return false;
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
        this.nextRecord = null;
        if (eof)
            return;

        if (isUseStreaming() && resolvedBody.isAnyFieldSupportStreaming()) {
            if (nextRecordIt != null) {
                if (nextRecordIt.hasNext()) {
                    nextRecord = (T) nextRecordIt.next();
                    context.setValue(VAR_NEXT_RECORD, nextRecord);
                    return;
                }
                nextRecordIt = null;
            }
        }
        try {

            switch (repeatKind) {
                case eos: {
                    if (!baseIn.isEof())
                        readOneRecord();
                    break;
                }
                case expr: {
                    if (fetchCount < totalCount) {
                        readOneRecord();
                        if (this.nextRecord == null)
                            throw new NopException(ERR_RECORD_NO_ENOUGH_ITEMS)
                                    .param(ARG_READ_COUNT, readCount)
                                    .param(ARG_TOTAL_COUNT, totalCount);
                    } else {
                        readTrailer();
                    }
                    break;
                }
                case until: {
                    if (bodyMeta.getRepeatUntil() != null) {
                        boolean ret = ConvertHelper.toTruthy(bodyMeta.getRepeatUntil().call2(null, baseIn, null, context.getEvalScope()), NopException::new);
                        if (!ret) {
                            readOneRecord();
                        } else {
                            readTrailer();
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
        if (isUseStreaming() && resolvedBody.isAnyFieldSupportStreaming()) {
            this.nextRecordIt = deserializer.readObjectStreaming(this.baseIn, resolvedBody, new LinkedHashMap<>(), context);
            if (this.nextRecordIt == null || this.nextRecordIt.isNull()) {
                readTrailer();
                return;
            }

            if (this.nextRecordIt.hasNext()) {
                this.nextRecord = (T) this.nextRecordIt.next();
                this.fetchCount++;
                context.setValue(VAR_NEXT_RECORD, nextRecord);
            }
            return;
        }

        T record = (T) resolvedBody.newBean();
        if (deserializer.readObject(this.baseIn, resolvedBody, record, context)) {
            this.nextRecord = record;
            this.fetchCount++;
            context.setValue(VAR_NEXT_RECORD, record);
        } else {
            readTrailer();
        }
    }

    void readTrailer() {
        eof = true;
        context.setValue(VAR_NEXT_RECORD, null);
        if (fileMeta.getResolvedTrailerType() != null) {
            try {
                if (trailerMeta == null)
                    trailerMeta = new LinkedHashMap<>();
                deserializer.readObject(baseIn, fileMeta.getResolvedTrailerType(), trailerMeta, context);
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
        if (fileMeta.getAfterRead() != null)
            fileMeta.getAfterRead().call2(null, baseIn, context, context.getEvalScope());
    }
}
