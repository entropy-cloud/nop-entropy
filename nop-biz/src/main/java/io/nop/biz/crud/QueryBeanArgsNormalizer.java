/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.BizConstants;
import io.nop.core.CoreConstants;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.reflection.IGraphQLArgsNormalizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueryBeanArgsNormalizer implements IGraphQLArgsNormalizer {
    @Override
    public Map<String, Object> normalize(Map<String, Object> args, IGraphQLExecutionContext context) {
        if (isNormalized(args))
            return args;

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(QueryBean.class);

        Map<String, Object> ret = new LinkedHashMap<>();

        Map<String, Object> query = (Map<String, Object>) args.get(BizConstants.ARG_QUERY);
        if (query == null) {
            query = new LinkedHashMap<>();
        } else {
            query = new LinkedHashMap<>(query);
        }

        List<Map<String, Object>> filters = new ArrayList<>();

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String name = entry.getKey();
            if (beanModel.getPropertyModel(name) != null) {
                query.put(name, entry.getValue());
            } else if (name.startsWith(BizConstants.FILTER_PREFIX)) {
                Map<String, Object> filter = getFilterMap(name, entry.getValue());
                filters.add(filter);
            }
        }

        if (!filters.isEmpty()) {
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put(CoreConstants.XML_PROP_TYPE, FilterBeanConstants.FILTER_OP_AND);
            filter.put(CoreConstants.XML_PROP_BODY, filters);
            query.put(BizConstants.PROP_FILTER, filter);
        }

        ret.put(BizConstants.ARG_QUERY, query);
        return ret;
    }

    private static Map<String, Object> getFilterMap(String name, Object value) {
        String filterName = name.substring(BizConstants.FILTER_PREFIX.length());
        String op = FilterBeanConstants.FILTER_OP_EQ;
        int pos = filterName.indexOf("__");
        if (pos > 0) {
            op = filterName.substring(pos + 2);
            filterName = filterName.substring(0, pos);
        }
        if (BizConstants.SPECIAL_VALUE_EMPTY.equals(value)) {
            value = "";
        } else if (BizConstants.SPECIAL_VALUE_NULL.equals(value)) {
            value = null;
        }
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put(CoreConstants.XML_PROP_TYPE, op);
        filter.put(CoreConstants.ATTR_NAME, filterName);
        filter.put(CoreConstants.ATTR_VALUE, value);
        return filter;
    }

    private boolean isNormalized(Map<String, Object> args) {
        if (args.size() == 0)
            return true;

        if (args.size() == 1 && args.containsKey(BizConstants.ARG_QUERY))
            return true;

        for (String name : args.keySet()) {
            if (!name.equals(BizConstants.ARG_QUERY) && !name.startsWith(BizConstants.V_PREFIX))
                return false;
        }
        return true;
    }
}
