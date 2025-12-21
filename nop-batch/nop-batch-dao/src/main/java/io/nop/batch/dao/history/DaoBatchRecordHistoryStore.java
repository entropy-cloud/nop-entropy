package io.nop.batch.dao.history;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchRecordHistoryStore;
import io.nop.batch.core.history.IBatchHistoryStoreModel;
import io.nop.batch.dao.entity.NopBatchRecordResult;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.dao.api.IEntityDao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DaoBatchRecordHistoryStore<S> implements IBatchRecordHistoryStore<S> {
    private final String DEFAULT_KEY = "default";

    protected final IEntityDao<NopBatchRecordResult> dao;
    protected final IBatchHistoryStoreModel model;

    public DaoBatchRecordHistoryStore(IEntityDao<NopBatchRecordResult> dao, IBatchHistoryStoreModel model) {
        this.dao = Guard.notNull(dao, "dao");
        this.model = Guard.notNull(model, "model");
    }

    @Override
    public Collection<S> filterProcessed(Collection<S> records, IBatchChunkContext context) {
        String taskId = context.getTaskId();
        Map<String, S> recordMap = buildRecordMap(records, context);

        // 查找状态为0的成功处理记录，跳过这些记录
        QueryBean query = new QueryBean();
        TreeBean filter = FilterBeans.and(FilterBeans.eq(NopBatchRecordResult.PROP_NAME_batchTaskId, taskId),
                FilterBeans.in(NopBatchRecordResult.PROP_NAME_recordKey, recordMap.keySet()),
                FilterBeans.eq(NopBatchRecordResult.PROP_NAME_resultStatus, 0));
        query.addFilter(filter);

        List<NopBatchRecordResult> results = dao.findAllByQuery(query);
        List<S> history = new ArrayList<>(results.size());
        for (NopBatchRecordResult result : results) {
            history.add(recordMap.remove(result.getRecordKey()));
        }

        List<S> ret = new ArrayList<>(records);
        ret.removeAll(history);
        return ret;
    }

    private Map<String, S> buildRecordMap(Collection<S> records, IBatchChunkContext context) {
        IEvalFunction fn = model.getRecordKeyExpr();
        IEvalScope scope = context.getEvalScope();

        Map<String, S> map = new LinkedHashMap<>();

        for (S record : records) {
            String recordKey = getRecordKey(record, fn, scope);
            map.put(recordKey, record);
        }
        return map;
    }

    protected String getRecordKey(S record, IEvalFunction fn, IEvalScope scope) {
        String key = StringHelper.toString(fn.call1(null, record, scope), null);
        if (StringHelper.isEmpty(key))
            key = DEFAULT_KEY;
        return key;
    }

    @Override
    public void saveProcessed(Collection<S> filtered, Throwable exception, IBatchChunkContext context) {

    }
}
