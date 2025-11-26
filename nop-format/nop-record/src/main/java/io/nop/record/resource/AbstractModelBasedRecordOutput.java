package io.nop.record.resource;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopConnectException;
import io.nop.commons.aggregator.IAggregatorProvider;
import io.nop.dataset.record.IRecordOutput;
import io.nop.record.RecordConstants;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.FieldRepeatKind;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordPaginationMeta;
import io.nop.record.serialization.IModelBasedRecordSerializer;
import io.nop.record.writer.IDataWriterBase;

import java.io.IOException;
import java.util.Map;

import static io.nop.record.RecordConstants.VAR_TOTAL_COUNT;

public abstract class AbstractModelBasedRecordOutput<Output extends IDataWriterBase, T> implements IRecordOutput<T> {
    //static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordOutput.class);

    private final Output baseOut;
    private final RecordFileMeta fileMeta;

    protected final IModelBasedRecordSerializer<Output> serializer;
    protected final IFieldCodecContext context;

    private final RecordAggregateState aggregateState;

    public AbstractModelBasedRecordOutput(Output out, RecordFileMeta fileMeta,
                                          IFieldCodecContext context, IModelBasedRecordSerializer<Output> serializer,
                                          IAggregatorProvider aggregatorProvider) {
        this.baseOut = out;
        this.fileMeta = fileMeta;
        this.context = context;
        this.serializer = serializer;
        this.aggregateState = new RecordAggregateState(fileMeta, aggregatorProvider, context);

        this.context.getEvalScope().setLocalValue(RecordConstants.VAR_AGG_STATE, aggregateState);
    }


    @Override
    public void flush() throws IOException {
        baseOut.flush();
    }

    @Override
    public void close() throws IOException {
        baseOut.close();
    }

    @Override
    public long getWriteCount() {
        return aggregateState.getWriteCount();
    }

    @Override
    public void beginWrite(Map<String, Object> attributes) throws IOException {
        if (attributes != null)
            context.getEvalScope().setLocalValues(attributes);

        if (fileMeta.getBeforeWrite() != null)
            fileMeta.getBeforeWrite().call2(null, baseOut, context, context.getEvalScope());


        if (fileMeta.getResolvedHeaderType() != null) {
            writeObject(baseOut, fileMeta.getResolvedHeaderType(), context);
        }

        FieldRepeatKind repeatKind = fileMeta.getBody().getRepeatKind();
        if (repeatKind == FieldRepeatKind.expr) {
            if (fileMeta.getBody().getRepeatCountField() != null) {
                Object value = context.getValue(VAR_TOTAL_COUNT);
                value = ConvertHelper.toPrimitiveInt(value, NopConnectException::new);
                serializer.writeSimpleField(baseOut, fileMeta.getBody().getRepeatCountField(), null, value, context);
            }
        }
    }

    @Override
    public void endWrite(Map<String, Object> trailerMeta) throws IOException {
        if (trailerMeta != null)
            context.getEvalScope().setLocalValues(trailerMeta);

        if (fileMeta.getResolvedTrailerType() != null) {
            context.getEvalScope().setLocalValues(aggregateState.getResults());
            writeObject(baseOut, fileMeta.getResolvedTrailerType(), context);
        }

        if (fileMeta.getAfterWrite() != null)
            fileMeta.getAfterWrite().call2(null, baseOut, context, context.getEvalScope());
    }

    @Override
    public void write(T record) throws IOException {
        beforeWriteRecord(record);
        writeObject(baseOut, fileMeta.getResolvedBodyType(), record);
        afterWriteRecord(record);
    }

    protected void beforeWriteRecord(T record) throws IOException {
        aggregateState.onWriteRecord(record);

        if (fileMeta.getPagination() != null) {
            if (aggregateState.isPageBegin()) {
                RecordPaginationMeta pagination = fileMeta.getPagination();
                if (pagination.getPageHeader() != null) {
                    writeObject(baseOut, pagination.getPageHeader(), context);
                }
            }
        }
    }

    protected void afterWriteRecord(T record) throws IOException {
        if (fileMeta.getPagination() != null) {
            if (aggregateState.isPageEnd()) {
                RecordPaginationMeta pagination = fileMeta.getPagination();
                if (pagination.getPageFooter() != null) {
                    context.getEvalScope().setLocalValues(aggregateState.getPageResults());
                    writeObject(baseOut, pagination.getPageFooter(), context);
                }
                aggregateState.newPage();
            }
        }
    }

    public void writeObject(Output out, RecordObjectMeta recordMeta, Object record) throws IOException {
        getSerializer().writeObject(out, recordMeta, record, context);
    }

    protected IModelBasedRecordSerializer<Output> getSerializer() {
        return serializer;
    }
}
