/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.utils;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.xlang.xmeta.impl.ObjKeyModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class OrmQueryHelper {
    public static boolean containsAnyKey(List<OrderFieldBean> orderBy, List<ObjKeyModel> keys) {
        if (keys == null || keys.isEmpty())
            return false;

        if (orderBy == null || orderBy.isEmpty())
            return false;

        for (ObjKeyModel key : keys) {
            if (containsKey(orderBy, key))
                return true;
        }
        return false;
    }

    private static boolean containsKey(List<OrderFieldBean> orderBy, ObjKeyModel key) {
        for (String prop : key.getProps()) {
            if (!containsProp(orderBy, prop))
                return false;
        }
        return true;
    }

    public static List<OrderFieldBean> appendOrderByPk(List<OrderFieldBean> orderBy, List<String> colNames,
                                                       boolean desc) {
        if (orderBy == null)
            orderBy = new ArrayList<>();

        for (String colName : colNames) {
            if (!containsProp(orderBy, colName)) {
                orderBy.add(OrderFieldBean.forField(colName, desc));
            }
        }
        return orderBy;
    }

    public static void appendOrderByPk(QueryBean query, List<String> colNames, boolean desc) {
        for (String colName : colNames) {
            query.addOrderField(colName, desc);
        }
    }

    public static List<OrderFieldBean> reverseOrderBy(List<OrderFieldBean> orderBy) {
        if (orderBy == null || orderBy.isEmpty())
            return null;

        List<OrderFieldBean> ret = new ArrayList<>(orderBy.size());
        for (OrderFieldBean order : orderBy) {
            order = order.reverse();
            ret.add(order);
        }
        return ret;
    }

    private static boolean containsProp(List<OrderFieldBean> orderBy, String name) {
        for (OrderFieldBean field : orderBy) {
            if (field.getName().equals(name))
                return true;
        }
        return false;
    }

    public static TreeBean buildRelationFilter(IEntityRelationModel propModel, Function<String, Object> propGetter) {
        if (propModel == null)
            return null;
        List<TreeBean> filters = new ArrayList<>(propModel.getJoin().size());
        for (IEntityJoinConditionModel join : propModel.getJoin()) {
            if (join.getRightPropModel() != null) {
                if (join.getLeftProp() != null) {
                    filters.add(FilterBeans.eq(join.getRightProp(), propGetter.apply(join.getLeftProp())));
                } else {
                    filters.add(FilterBeans.eq(join.getRightProp(), join.getRightValue()));
                }
            }
        }
        return FilterBeans.and(filters);
    }

    public static void resolveRef(TreeBean filter, Object source) {
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
