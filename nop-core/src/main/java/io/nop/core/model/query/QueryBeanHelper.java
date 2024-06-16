/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.query;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;

import java.util.List;
import java.util.function.Predicate;

public class QueryBeanHelper {

    public static QueryBean buildQueryBeanFromTreeBean(ITreeBean node) {
        return BeanTool.buildBeanFromTreeBean(node, QueryBean.class);
    }

    public static <T> Predicate<Object> toPredicate(ITreeBean filter, IEvalScope scope) {
        if (filter == null)
            return null;

        return obj -> {
            scope.setLocalValue(null, "obj", obj);
            return evaluateFilter(filter, scope);
        };
    }

    public static boolean evaluateFilter(ITreeBean filter, IVariableScope scope) {
        return Boolean.TRUE.equals(FilterBeanEvaluator.INSTANCE.visit(filter, scope));
    }

    public static String formatFilter(ITreeBean filter) {
        return new FilterBeanFormatter().format(filter);
    }

    public static List<OrderFieldBean> parseOrderBySql(SourceLocation loc, String sql) {
        if (StringHelper.isBlank(sql))
            return null;
        return new OrderBySqlParser().parseFromText(loc, sql);
    }

    public static String buildOrderBySql(List<OrderFieldBean> orderBy) {
        if (orderBy == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (OrderFieldBean orderField : orderBy) {
            if (sb.length() > 0)
                sb.append(',');
            sb.append(orderField.getName());
            sb.append(' ');
            if (orderField.isDesc()) {
                sb.append("desc");
            } else {
                sb.append("asc");
            }
            if (orderField.getNullsFirst() != null) {
                if (orderField.getNullsFirst()) {
                    sb.append(" nulls first");
                } else {
                    sb.append(" nulls last");
                }
            }
        }
        return sb.toString();
    }
}