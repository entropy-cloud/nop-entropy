/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.orm.fetcher;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.graphql.GraphQLConnection;
import io.nop.api.core.beans.graphql.GraphQLConnectionInput;
import io.nop.api.core.beans.graphql.GraphQLPageInfo;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.auth.api.utils.AuthHelper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import io.nop.orm.utils.OrmQueryHelper;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_MAX_PAGE_SIZE;

public class OrmEntityPropConnectionFetcher implements IDataFetcher {
    private final IEntityDao entityDao;
    private final String bizObjName;
    private final String fetchAction;
    private final int maxFetchSize;
    private final boolean findFirst;
    private final TreeBean filter;
    private final List<OrderFieldBean> orderBy;
    private final BiConsumer<QueryBean, IDataFetchingEnvironment> queryProcessor;

    public OrmEntityPropConnectionFetcher(IEntityDao entityDao, String bizObjName, String fetchAction, int maxFetchSize,
                                          boolean findFirst, TreeBean filter, List<OrderFieldBean> orderBy,
                                          BiConsumer<QueryBean, IDataFetchingEnvironment> queryProcessor) {
        this.entityDao = entityDao;
        this.bizObjName = bizObjName;
        this.fetchAction = fetchAction;
        this.maxFetchSize = maxFetchSize;
        this.findFirst = findFirst;
        this.filter = Guard.notNull(filter, "filter");
        this.orderBy = orderBy;
        this.queryProcessor = queryProcessor;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IServiceContext context = env.getExecutionContext();
        Object source = env.getSource();

        FieldSelectionBean selection = env.getSelectionBean();

        GraphQLConnectionInput input = BeanTool.castBeanToType(env.getArgs(), GraphQLConnectionInput.class);
        QueryBean query = new QueryBean();
        AuthHelper.appendFilter(context.getDataAuthChecker(), query, bizObjName, fetchAction, context);

        query.setOffset(input.getOffset());

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

        if (!findFirst) {
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

        TreeBean filter = this.filter.toTreeBean();
        resolveRef(filter, source);
        query.addFilter(filter);

        if (orderBy != null) {
            query.addOrderBy(orderBy);
        }

        OrmQueryHelper.appendOrderByPk(query, entityDao.getPkColumnNames(), false);
        if (input.getLast() > 0) {
            query.setOrderBy(OrmQueryHelper.reverseOrderBy(query.getOrderBy()));
        }

        processQuery(query, env);

        if (findFirst) {
            // 可以利用connection的支持只查出满足条件的唯一一条数据
            return entityDao.findFirstByQuery(query);
        } else {
            GraphQLConnection<Object> conn = new GraphQLConnection<>();
            if (selection.hasField(GraphQLConstants.FIELD_TOTAL)) {
                conn.setTotal(entityDao.countByQuery(query));
            }
            if (selection.hasField(GraphQLConstants.FIELD_PAGE_INFO) || selection.hasField(GraphQLConstants.FIELD_ITEMS)) {
                fetchItems(conn, query, input);
            }
            return conn;
        }
    }

    private void fetchItems(GraphQLConnection<Object> conn, QueryBean query, GraphQLConnectionInput input) {
        if (input.getLast() > 0) {
            query.setLimit(input.getLast() + 1);
            List<Object> data = entityDao.findPageByQuery(query);
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
            conn.setItems(data);
        } else {
            query.setLimit(input.getFirst() + 1);
            List<Object> data = entityDao.findPageByQuery(query);
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
            conn.setItems(data);
        }
    }

    protected String getCursor(Object obj) {
        if (obj instanceof IOrmEntity)
            return ((IOrmEntity) obj).orm_idString();
        return ConvertHelper.toString(BeanTool.getProperty(obj, OrmConstants.PROP_ID));
    }

    protected void processQuery(QueryBean query, IDataFetchingEnvironment env) {
        if (queryProcessor != null)
            queryProcessor.accept(query, env);
    }

    void resolveRef(TreeBean filter, Object source) {
        Map<String, Object> attrs = filter.getAttrs();
        if (attrs != null) {
            for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    String filterValue = value.toString();
                    if (filterValue.startsWith(OrmConstants.VALUE_PREFIX_PROP_REF)) {
                        String propName = filterValue.substring(OrmConstants.VALUE_PREFIX_PROP_REF.length());
                        Object refValue = BeanTool.getComplexProperty(source, propName);
                        entry.setValue(refValue);
                    }
                }
            }
        }

        List<TreeBean> children = filter.getChildren();
        if (children != null) {
            for (TreeBean child : children) {
                resolveRef(child, source);
            }
        }
    }
}
