package io.nop.record.resource;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.aggregator.IAggregatorProvider;
import io.nop.core.lang.eval.IBeanVariableScope;
import io.nop.dataset.record.IRecordOutput;
import io.nop.record.RecordConstants;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordObjectMeta;
import io.nop.record.model.RecordPaginationMeta;
import io.nop.record.serialization.IModelBasedRecordSerializer;
import io.nop.record.writer.IDataWriterBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

public abstract class AbstractModelBasedRecordOutput<Output extends IDataWriterBase, T> implements IRecordOutput<T> {
    static final Logger LOG = LoggerFactory.getLogger(AbstractModelBasedRecordOutput.class);

    private final Output baseOut;
    private long writeCount;
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
    public void flush() {
        try {
            baseOut.flush();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void close() {
        try {
            baseOut.close();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public long getWriteCount() {
        return writeCount;
    }

    @Override
    public void beginWrite(Map<String, Object> attributes) {
        if (fileMeta.getHeader() != null) {
            IBeanVariableScope scope = name -> getScopeValue(attributes, name);
            try {
                writeObject(baseOut, fileMeta.getHeader(), scope, RecordConstants.HEADER_NAME);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }

    private Object getScopeValue(Map<String, Object> vars, String name) {
        if (name.equals(RecordConstants.VAR_WRITE_COUNT))
            return writeCount;
        if (name.equals(RecordConstants.VAR_INDEX_IN_PAGE))
            return aggregateState.getIndexInPage();
        if (vars != null) {
            Object value = vars.get(name);
            if (value != null)
                return value;
        }
        return context.getEvalScope().getValue(name);
    }

    @Override
    public void endWrite(Map<String, Object> trailerMeta) {
        if (fileMeta.getTrailer() != null) {
            IBeanVariableScope scope = name -> getScopeValue(trailerMeta, name);
            try {
                writeObject(baseOut, fileMeta.getTrailer(), scope, RecordConstants.TRAILER_NAME);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void write(T record) {
        writeCount++;
        try {
            beforeWriteRecord(record);
            writeObject(baseOut, fileMeta.getBody(), record, RecordConstants.BODY_NAME);
            afterWriteRecord(record);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    protected void beforeWriteRecord(T record) throws IOException {
        aggregateState.onWriteRecord(record);

        if (fileMeta.getPagination() != null) {
            if (aggregateState.isPageBegin()) {
                RecordPaginationMeta pagination = fileMeta.getPagination();
                if (pagination.getPageHeader() != null) {
                    IBeanVariableScope scope = name -> getScopeValue(null, name);
                    writeObject(baseOut, pagination.getPageHeader(), scope, RecordConstants.PAGE_FOOTER_NAME);
                }
            }
        }
    }

    protected void afterWriteRecord(T record) throws IOException {
        if (fileMeta.getPagination() != null) {
            if (aggregateState.isPageEnd()) {
                RecordPaginationMeta pagination = fileMeta.getPagination();
                if (pagination.getPageFooter() != null) {
                    IBeanVariableScope scope = name -> getScopeValue(aggregateState.getPageResults(), name);
                    writeObject(baseOut, pagination.getPageFooter(), scope, RecordConstants.PAGE_FOOTER_NAME);
                }
                aggregateState.resetPage();
            }
        }
    }

    public void writeObject(Output out, RecordObjectMeta recordMeta, Object record, String name) throws IOException {
        getSerializer().writeObject(out, recordMeta, record, name, context);
    }

    protected IModelBasedRecordSerializer<Output> getSerializer() {
        return serializer;
    }
}
