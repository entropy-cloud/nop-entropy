/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.orm.fetcher;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.graphql.GraphQLConnection;
import io.nop.api.core.beans.graphql.GraphQLConnectionInput;
import io.nop.api.core.beans.graphql.GraphQLEdgeBean;
import io.nop.api.core.beans.graphql.GraphQLNode;
import io.nop.api.core.beans.graphql.GraphQLPageInfo;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.biz.GraphQLQueryMethod;
import io.nop.graphql.core.biz.IBizObjectQueryProcessor;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import io.nop.orm.utils.OrmQueryHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_MAX_PAGE_SIZE;
import static io.nop.orm.utils.OrmQueryHelper.resolveRef;

public class OrmEntityPropConnectionFetcher implements IDataFetcher {
    private final IBizObjectQueryProcessor<?> queryProcessor;
    private final String authObjName;
    private final int maxFetchSize;
    private final GraphQLQueryMethod queryMethod;
    private final TreeBean filter;
    private final List<OrderFieldBean> orderBy;

    public OrmEntityPropConnectionFetcher(IBizObjectQueryProcessor<?> queryProcessor, String authObjName, int maxFetchSize,
                                          GraphQLQueryMethod queryMethod, TreeBean filter, List<OrderFieldBean> orderBy) {
        this.authObjName = authObjName;
        this.maxFetchSize = maxFetchSize;
        this.queryMethod = queryMethod;
        this.filter = Guard.notNull(filter, "filter");
        this.orderBy = orderBy;
        this.queryProcessor = queryProcessor;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IServiceContext context = env.getServiceContext();
        Object source = env.getSource();

        FieldSelectionBean selection = env.getSelectionBean();

        GraphQLConnectionInput input = BeanTool.castBeanToType(env.getArgs(), GraphQLConnectionInput.class);
        QueryBean query = input.getQuery();
        if (query == null)
            query = new QueryBean();

        query.setOffset(input.getOffset());
        query.setLimit(input.getLimit());

        if (input.getFirst() > 0) {
            query.setLimit(input.getFirst());
            query.setCursor(input.getAfter());
            input.setLast(0);
            query.setOffset(0);
        } else if (input.getLast() > 0) {
            input.setFirst(0);
            query.setOffset(0);
            query.setLimit(input.getLast());
            query.setCursor(input.getBefore());
        }

        if (queryMethod != GraphQLQueryMethod.findFirst) {
            if (maxFetchSize > 0) {
                if (query.getLimit() > maxFetchSize) {
                    query.setLimit(maxFetchSize);
                }
            }
            if (query.getLimit() <= 0) {
                query.setLimit(10);
            } else if (query.getLimit() > CFG_GRAPHQL_MAX_PAGE_SIZE.get()) {
                query.setLimit(CFG_GRAPHQL_MAX_PAGE_SIZE.get());
            }
        }

        if (input.getFilter() != null) {
            query.addFilter(input.getFilter());
        }

        if (input.getOrderBy() != null) {
            query.addOrderBy(input.getOrderBy());
        }

        BiConsumer<QueryBean, IServiceContext> prepareQuery = buildPrepareQuery(source, input);

        if (queryMethod == GraphQLQueryMethod.findFirst) {
            // 可以利用connection的支持只查出满足条件的唯一一条数据
            return queryProcessor.doFindFirst0(query, authObjName, prepareQuery, selection, context);
        } else if (queryMethod == GraphQLQueryMethod.findCount) {
            return queryProcessor.doFindCount0(query, authObjName, prepareQuery, context);
        } else if (queryMethod == GraphQLQueryMethod.findList) {
            return queryProcessor.doFindList0(query, authObjName, prepareQuery, selection, context);
        } else if (queryMethod == GraphQLQueryMethod.findPage) {
            return queryProcessor.doFindPage0(query, authObjName, prepareQuery, selection, context);
        } else {
            GraphQLConnection<Object> conn = new GraphQLConnection<>();

            fetchItems(conn, query, input, queryBean -> {
                PageBean<Object> pageBean = (PageBean<Object>) queryProcessor.doFindPage0(queryBean,
                        authObjName, prepareQuery, selection, context);
                conn.setTotal(pageBean.getTotal());
                conn.setItems(pageBean.getItems());
                return pageBean.getItems();
            });

            if (selection.hasSourceField(GraphQLConstants.FIELD_EDGES)) {
                List<Object> items = conn.getItems();
                if (items != null) {
                    List<GraphQLEdgeBean> edges = new ArrayList<>(items.size());
                    for (Object item : items) {
                        GraphQLEdgeBean edge = new GraphQLEdgeBean();
                        GraphQLNode node = new GraphQLNode();
                        node.setId(ConvertHelper.toString(BeanTool.getProperty(item, OrmConstants.PROP_ID)));
                        edge.setNode(node);
                        edge.setCursor(getCursor(item));
                        edges.add(edge);
                    }
                    conn.setEdges(edges);
                }
            }
            return conn;
        }
    }

    BiConsumer<QueryBean, IServiceContext> buildPrepareQuery(Object source, GraphQLConnectionInput input) {
        return (query, ctx) -> {
            TreeBean filter = this.filter.cloneInstance().toTreeBean();
            resolveRef(filter, source);
            query.addFilter(filter);

            if (orderBy != null) {
                query.addOrderBy(orderBy);
            }

            if (input.getLast() > 0) {
                query.setOrderBy(OrmQueryHelper.reverseOrderBy(query.getOrderBy()));
            }
        };
    }

    private void fetchItems(GraphQLConnection<Object> conn, QueryBean query, GraphQLConnectionInput input,
                            Function<QueryBean, List<Object>> fetcher) {
        if (input.getLast() > 0) {
            query.setLimit(input.getLast() + 1);
            List<Object> data = fetcher.apply(query);
            if (data != null) {
                GraphQLPageInfo pageInfo = new GraphQLPageInfo();
                pageInfo.setHasNextPage(!StringHelper.isEmpty(input.getBefore()));
                if (data.size() > input.getLast()) {
                    data = data.subList(0, input.getLast());
                    data = CollectionHelper.reverseList(data);
                    pageInfo.setHasPreviousPage(true);
                } else {
                    data = CollectionHelper.reverseList(data);
                    pageInfo.setHasPreviousPage(false);
                }

                if (!data.isEmpty()) {
                    pageInfo.setStartCursor(getCursor(data.get(0)));
                    pageInfo.setEndCursor(getCursor(data.get(data.size() - 1)));
                }
                conn.setPageInfo(pageInfo);
            }
            conn.setItems(data);
        } else if (input.getFirst() > 0) {
            query.setLimit(input.getFirst() + 1);
            List<Object> data = fetcher.apply(query);
            if (data != null) {
                GraphQLPageInfo pageInfo = new GraphQLPageInfo();
                pageInfo.setHasPreviousPage(!StringHelper.isEmpty(input.getAfter()));
                if (data.size() > input.getFirst()) {
                    data = data.subList(0, input.getFirst());
                    pageInfo.setHasNextPage(true);
                } else {
                    pageInfo.setHasNextPage(false);
                }

                if (!data.isEmpty()) {
                    pageInfo.setStartCursor(getCursor(data.get(0)));
                    pageInfo.setEndCursor(getCursor(data.get(data.size() - 1)));
                }
                conn.setPageInfo(pageInfo);
            }
            conn.setItems(data);
        } else {
            List<Object> data = fetcher.apply(query);
            if (data != null) {
                GraphQLPageInfo pageInfo = new GraphQLPageInfo();
                pageInfo.setHasPreviousPage(query.getOffset() > 0);
                if (data.size() < input.getLimit()) {
                    pageInfo.setHasNextPage(false);
                } else {
                    pageInfo.setHasNextPage(true);
                }

                if (!data.isEmpty()) {
                    pageInfo.setStartCursor(getCursor(data.get(0)));
                    pageInfo.setEndCursor(getCursor(data.get(data.size() - 1)));
                }
                conn.setPageInfo(pageInfo);
            }
            conn.setItems(data);
        }
    }

    protected String getCursor(Object obj) {
        if (obj instanceof IOrmEntity)
            return ((IOrmEntity) obj).orm_idString();
        return ConvertHelper.toString(BeanTool.getProperty(obj, OrmConstants.PROP_ID));
    }

}
