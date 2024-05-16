package io.nop.graphql.core.biz;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.context.IServiceContext;

import java.util.List;
import java.util.function.BiConsumer;

public interface IBizObjectQueryProcessor<T> {

    long doFindCount0(QueryBean query,
                      String authObjName,
                      BiConsumer<QueryBean, IServiceContext> prepareQuery,
                      IServiceContext context);

    PageBean<T> doFindPage0(QueryBean query,
                            String authObjName,
                            BiConsumer<QueryBean, IServiceContext> prepareQuery,
                            FieldSelectionBean selection,
                            IServiceContext context);

    List<T> doFindList0(QueryBean query,
                        String authObjName,
                        BiConsumer<QueryBean, IServiceContext> prepareQuery,
                        FieldSelectionBean selection,
                        IServiceContext context);

    T doFindFirst0(QueryBean query,
                   String authObjName,
                   BiConsumer<QueryBean, IServiceContext> prepareQuery,
                   FieldSelectionBean selection,
                   IServiceContext context);
}