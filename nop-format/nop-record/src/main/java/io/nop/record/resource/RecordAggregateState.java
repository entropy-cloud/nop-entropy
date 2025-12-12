package io.nop.record.resource;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.commons.aggregator.AggregateState;
import io.nop.commons.aggregator.IAggregatorProvider;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.record.codec.IFieldCodecContext;
import io.nop.record.model.RecordAggregateFieldMeta;
import io.nop.record.model.RecordFileMeta;
import io.nop.record.model.RecordPaginationMeta;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.record.RecordErrors.ARG_FIELD_NAME;
import static io.nop.record.RecordErrors.ERR_RECORD_AGGREGATE_FIELD_NO_AGG_FUNC;

public class RecordAggregateState {
    private final RecordFileMeta fileMeta;
    private final RecordPaginationMeta paginationMeta;
    private final IFieldCodecContext context;

    private AggregateState aggregateState;

    private AggregateState pageAggregateState;
    private int pageIndex;
    private int indexInPage;
    private int pageSize;
    private long writeCount;

    static final String DEFAULT_GROUP_VALUE = "default";

    private String groupValue;

    public RecordAggregateState(RecordFileMeta fileMeta,
                                IAggregatorProvider aggregatorProvider, IFieldCodecContext context) {
        this.fileMeta = fileMeta;
        this.paginationMeta = fileMeta.getPagination();
        this.context = context;

        if (fileMeta.getAggregates() != null) {
            this.aggregateState = newAggregateState(fileMeta.getAggregates(), aggregatorProvider);
        }

        if (fileMeta.getPagination() != null) {
            List<RecordAggregateFieldMeta> aggregates = fileMeta.getPagination().getAggregates();
            this.pageAggregateState = newAggregateState(aggregates, aggregatorProvider);
            this.pageSize = fileMeta.getPagination().getPageSize();
        }
        this.indexInPage = 0;
        this.pageIndex = 1;
    }

    private AggregateState newAggregateState(List<RecordAggregateFieldMeta> aggFields, IAggregatorProvider aggregatorProvider) {
        AggregateState state = new AggregateState();
        state.setAggregatorProvider(aggregatorProvider);
        if (aggFields != null) {
            for (RecordAggregateFieldMeta aggField : aggFields) {
                if (aggField.getAggregator() != null) {
                    state.setAggregator(aggField.getName(), new EvalFunctionAggregator(aggField.getAggregator(), context));
                } else if (aggField.getAggFunc() != null) {
                    state.initAggregator(aggField.getName(), aggField.getAggFunc());
                } else {
                    throw new NopException(ERR_RECORD_AGGREGATE_FIELD_NO_AGG_FUNC)
                            .source(aggField).param(ARG_FIELD_NAME, aggField.getName());
                }
            }
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

    public boolean checkPageChanged(Object record) {
        if(pageAggregateState == null)
            return false;

        if (fileMeta.getPagination().getGroupByExpr() != null) {
            String groupValue = StringHelper.toString(fileMeta.getPagination().getGroupByExpr().call2(null, record, context, context.getEvalScope()), DEFAULT_GROUP_VALUE);
            if (this.groupValue == null) {
                this.groupValue = groupValue;
                return false;
            }

            if (this.groupValue.equals(groupValue))
                return false;

            this.groupValue = groupValue;
            return true;
        }

        if (pageSize > 0 && indexInPage >= this.pageSize - 1) {
            return true;
        }
        return false;
    }

    public String getGroupValue() {
        return groupValue;
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

    public int getPageIndex() {
        return pageIndex;
    }

    public boolean isPageBegin() {
        return pageAggregateState != null && indexInPage == 1;
    }

    public void newPage() {
        indexInPage = 0;
        pageAggregateState.reset();
        pageIndex++;
    }
}
