package io.nop.record.resource;

import io.nop.commons.aggregator.IAggregatorProvider;
import io.nop.dataset.record.IRecordOutput;
import io.nop.record.RecordConstants;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordPaginationMeta;
import io.nop.record.serialization.IModelBasedRecordSerializer;
import io.nop.record.writer.IDataWriterBase;

import java.io.IOException;
import java.util.Map;

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

        if (fileMeta.getResolvedHeaderType() != null) {
            writeObject(baseOut, fileMeta.getResolvedHeaderType(), context, RecordConstants.HEADER_NAME);
        }
    }

    @Override
    public void endWrite(Map<String, Object> trailerMeta) throws IOException {
        if (trailerMeta != null)
            context.getEvalScope().setLocalValues(trailerMeta);

        if (fileMeta.getResolvedTrailerType() != null) {
            context.getEvalScope().setLocalValues(aggregateState.getResults());
            writeObject(baseOut, fileMeta.getResolvedTrailerType(), context, RecordConstants.TRAILER_NAME);
        }
    }

    @Override
    public void write(T record) throws IOException {
        beforeWriteRecord(record);
        writeObject(baseOut, fileMeta.getResolvedBodyType(), record, RecordConstants.BODY_NAME);
        afterWriteRecord(record);
    }

    protected void beforeWriteRecord(T record) throws IOException {
        aggregateState.onWriteRecord(record);

        if (fileMeta.getPagination() != null) {
            if (aggregateState.isPageBegin()) {
                RecordPaginationMeta pagination = fileMeta.getPagination();
                if (pagination.getPageHeader() != null) {
                    writeObject(baseOut, pagination.getPageHeader(), context, RecordConstants.PAGE_HEADER_NAME);
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
                    writeObject(baseOut, pagination.getPageFooter(), context, RecordConstants.PAGE_FOOTER_NAME);
                }
                aggregateState.resetPage();
            }
        }
    }

    public void writeObject(Output out, RecordObjectMeta recordMeta, Object record, String name) throws IOException {
        getSerializer().writeObject(out, recordMeta, name, record, context);
    }

    protected IModelBasedRecordSerializer<Output> getSerializer() {
        return serializer;
    }
}
