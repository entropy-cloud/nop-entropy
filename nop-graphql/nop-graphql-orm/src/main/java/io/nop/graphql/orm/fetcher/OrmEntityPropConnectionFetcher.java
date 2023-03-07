/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.orm.fetcher;

import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.graphql.GraphQLConnectionInput;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.Guard;
import io.nop.auth.api.utils.AuthHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.orm.OrmConstants;
import io.nop.orm.utils.OrmQueryHelper;

import java.util.List;
import java.util.Map;

import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_MAX_PAGE_SIZE;

public class OrmEntityPropConnectionFetcher implements IDataFetcher {
    private final IEntityDao entityDao;
    private final String bizObjName;
    private final String fetchAction;
    private final int maxFetchSize;
    private final boolean findFirst;
    private final TreeBean filter;
    private final List<OrderFieldBean> orderBy;

    public OrmEntityPropConnectionFetcher(IEntityDao entityDao, String bizObjName, String fetchAction, int maxFetchSize,
                                          boolean findFirst, TreeBean filter, List<OrderFieldBean> orderBy) {
        this.entityDao = entityDao;
        this.bizObjName = bizObjName;
        this.fetchAction = fetchAction;
        this.maxFetchSize = maxFetchSize;
        this.findFirst = findFirst;
        this.filter = Guard.notNull(filter, "filter");
        this.orderBy = orderBy;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        IServiceContext context = env.getExecutionContext();
        Object source = env.getSource();

        GraphQLConnectionInput input = BeanTool.castBeanToType(env.getArgs(), GraphQLConnectionInput.class);
        QueryBean query = new QueryBean();
        AuthHelper.appendFilter(context.getDataAuthChecker(), query, bizObjName, fetchAction, context.getUserContext());

        query.setOffset(input.getOffset());

        if (input.getFirst() > 0) {
            query.setLimit(input.getFirst());
            query.setCursor(input.getAfter());
        } else if (input.getLast() > 0) {
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

        if (input.getFirst() > 0) {
            OrmQueryHelper.appendOrderByPk(query, entityDao.getPkColumnNames(), false);
        } else {
            OrmQueryHelper.appendOrderByPk(query, entityDao.getPkColumnNames(), true);
        }

        if (findFirst) {
            return entityDao.findFirstByQuery(query);
        } else {
            return entityDao.findPageByQuery(query);
        }
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
