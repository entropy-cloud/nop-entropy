package io.nop.biz.crud;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.graphql.core.biz.IBizObjectQueryProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.nop.biz.BizConstants.METHOD_DO_FIND_COUNT0;
import static io.nop.biz.BizConstants.METHOD_DO_FIND_FIRST0;
import static io.nop.biz.BizConstants.METHOD_DO_FIND_LIST0;
import static io.nop.biz.BizConstants.METHOD_DO_FIND_PAGE0;
import static io.nop.biz.BizConstants.PARAM_AUTH_OBJ_NAME;
import static io.nop.biz.BizConstants.PARAM_PREPARE_QUERY;
import static io.nop.biz.BizConstants.PARAM_QUERY;

/**
 * BizObject可能相互引用，所以这里只保存bizObjName，使用延迟加载机制。另外每次都按照bizObjName加载也可以实现动态更新，每当对象模型发生变化的时候，
 * 都自动更新加载对象。
 */
@SuppressWarnings("unchecked")
public class BizObjectQueryProcessorAdapter<T> implements IBizObjectQueryProcessor<T> {
    private final IBizObjectManager bizObjectManager;
    private final String bizObjName;

    public BizObjectQueryProcessorAdapter(IBizObjectManager bizObjectManager, String bizObjName) {
        this.bizObjectManager = bizObjectManager;
        this.bizObjName = bizObjName;
    }

    private IBizObject getBizObject() {
        return bizObjectManager.getBizObject(bizObjName);
    }

    @Override
    public long doFindCount0(QueryBean query,
                             String authObjName,
                             BiConsumer<QueryBean, IServiceContext> prepareQuery,
                             IServiceContext context) {
        Map<String, Object> params = buildParams(query, authObjName, prepareQuery);
        return ConvertHelper.toPrimitiveLong(getBizObject().invoke(METHOD_DO_FIND_COUNT0, params, null, context), NopException::new);
    }

    private Map<String, Object> buildParams(QueryBean query, String authObjName,
                                            BiConsumer<QueryBean, IServiceContext> prepareQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put(PARAM_QUERY, query);
        params.put(PARAM_AUTH_OBJ_NAME, authObjName);
        params.put(PARAM_PREPARE_QUERY, prepareQuery);
        return params;
    }

    @Override
    public PageBean<T> doFindPage0(QueryBean query, String authObjName,
                                   BiConsumer<QueryBean, IServiceContext> prepareQuery,
                                   FieldSelectionBean selection, IServiceContext context) {
        Map<String, Object> params = buildParams(query, authObjName, prepareQuery);
        return (PageBean<T>) getBizObject().invoke(METHOD_DO_FIND_PAGE0, params, selection, context);
    }

    @Override
    public List<T> doFindList0(QueryBean query, String authObjName,
                               BiConsumer<QueryBean, IServiceContext> prepareQuery,
                               FieldSelectionBean selection, IServiceContext context) {
        Map<String, Object> params = buildParams(query, authObjName, prepareQuery);
        return (List<T>) getBizObject().invoke(METHOD_DO_FIND_LIST0, params, selection, context);
    }

    @Override
    public T doFindFirst0(QueryBean query, String authObjName,
                          BiConsumer<QueryBean, IServiceContext> prepareQuery,
                          FieldSelectionBean selection, IServiceContext context) {
        Map<String, Object> params = buildParams(query, authObjName, prepareQuery);
        return (T) getBizObject().invoke(METHOD_DO_FIND_FIRST0, params, selection, context);
    }
}
