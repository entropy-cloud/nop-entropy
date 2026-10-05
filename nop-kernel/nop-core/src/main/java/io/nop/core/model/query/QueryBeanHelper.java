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
import java.util.function.Function;
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

    public static String formatFilter(ITreeBean filter, Function<String,String> nameTransformer) {
        return new FilterBeanFormatter(nameTransformer).format(filter);
    }

    public static List<OrderFieldBean> parseOrderBySql(SourceLocation loc, String sql) {
        if (StringHelper.isBlank(sql))
            return null;
        return new OrderBySqlParser().parseFromText(loc, sql);
    }

    /**
     * <b>⚠️ 方言盲，勿用于需要空序的查询。</b>
     *
     * <p>本方法把 {@code OrderFieldBean.nullsFirst} 直接渲染成 {@code nulls first/last} 文本，
     * <b>不询问目标方言是否支持该语法</b>。原因是依赖方向所致：
     * {@code nop-dao} 依赖 {@code nop-core}（单向），本类无法引用 {@code IDialect}。
     * 因此在 MySQL / MariaDB / DM / tdengine / SQL Server 等不支持该语法的库上，
     * 本方法会产出<b>语法错误</b> SQL。
     *
     * <p>正确做法：走 ORM 查询通道（{@code OrmEntityDao} / {@code GenSqlHelper}），
     * 它们按 plan 2309 接入 {@code supportNullsFirst} 能力位，原生 / 改写自动分流。
     *
     * <p>本方法当前<b>零调用方</b>，删除属 {@code nop-core} API 变更，已在
     * {@code ai-dev/plans/2309-bean-orderby-nulls-first-gating.md} 的 Deferred 区登记。
     */
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