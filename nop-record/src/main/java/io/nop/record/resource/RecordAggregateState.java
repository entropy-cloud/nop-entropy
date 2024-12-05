package io.nop.record.resource;

import io.nop.api.core.util.IVariableScope;
import io.nop.commons.aggregator.AggregateState;
import io.nop.commons.aggregator.IAggregatorProvider;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.RecordAggregateFieldMeta;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordPaginationMeta;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RecordAggregateState {
    private final RecordFileMeta fileMeta;
    private final RecordPaginationMeta paginationMeta;
    private final IFieldCodecContext context;

    private AggregateState aggregateState;

    private AggregateState pageAggregateState;
    private int indexInPage;
    private int pageSize;
    private long writeCount;

    public RecordAggregateState(RecordFileMeta fileMeta,
                                IAggregatorProvider aggregatorProvider, IFieldCodecContext context) {
        this.fileMeta = fileMeta;
        this.paginationMeta = fileMeta.getPagination();
        this.context = context;

        if (fileMeta.getAggregates() != null) {
            this.aggregateState = newAggregateState(fileMeta.getAggregates(), aggregatorProvider);
        }

        if (fileMeta.getPagination() != null) {
            if (fileMeta.getPagination().getAggregates() != null) {
                this.pageAggregateState = newAggregateState(fileMeta.getPagination().getAggregates(), aggregatorProvider);
                this.pageSize = fileMeta.getPagination().getPageSize();
            }
        }
        this.indexInPage = 0;
    }

    private AggregateState newAggregateState(List<RecordAggregateFieldMeta> aggFields, IAggregatorProvider aggregatorProvider) {
        AggregateState state = new AggregateState();
        state.setAggregatorProvider(aggregatorProvider);
        for (RecordAggregateFieldMeta aggField : aggFields) {
            state.initAggregator(aggField.getName(), aggField.getAggFunc());
        }
        return state;
    }

    public long getWriteCount() {
        return writeCount;
    }

    public int getIndexInPage() {
        return indexInPage;
    }

    public void onWriteRecord(Object record) {
        writeCount++;
        if (pageAggregateState != null) {
            aggregate(pageAggregateState, paginationMeta.getAggregates(), record);
        }

        if (aggregateState != null) {
            aggregate(aggregateState, fileMeta.getAggregates(), record);
        }

        indexInPage++;
    }

    private void aggregate(AggregateState state, List<RecordAggregateFieldMeta> aggFields, Object record) {
        for (RecordAggregateFieldMeta aggField : aggFields) {
            Object value = getProp(record, aggField);
            state.aggregate(aggField.getName(), value);
        }
    }

    Object getProp(Object record, RecordAggregateFieldMeta aggField) {
        if (aggField.getValueExpr() != null)
            return aggField.getValueExpr().call1(null, record, context.getEvalScope());

        if (record == null)
            return null;

        String propName = aggField.getPropOrFieldName();

        if (record instanceof IVariableScope)
            return ((IVariableScope) record).getValueByPropPath(propName);

        return BeanTool.getComplexProperty(record, propName);
    }

    public Map<String, Object> getPageResults() {
        if (pageAggregateState == null)
            return Collections.emptyMap();

        return pageAggregateState.getResults();
    }

    public Map<String, Object> getResults() {
        if (aggregateState == null)
            return Collections.emptyMap();
        return aggregateState.getResults();
    }

    public boolean isPageBegin() {
        return pageAggregateState != null && indexInPage == 1;
    }

    public boolean isPageEnd() {
        return pageAggregateState != null && indexInPage >= pageSize;
    }

    public void resetPage() {
        if (pageAggregateState != null) {
            indexInPage = 0;
            pageAggregateState.reset();
        }
    }
}
