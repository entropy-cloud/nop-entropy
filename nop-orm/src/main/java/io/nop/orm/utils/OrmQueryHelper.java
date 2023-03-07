/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.utils;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.xlang.xmeta.impl.ObjKeyModel;

import java.util.ArrayList;
import java.util.List;

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

    private static boolean containsProp(List<OrderFieldBean> orderBy, String name) {
        for (OrderFieldBean field : orderBy) {
            if (field.getName().equals(name))
                return true;
        }
        return false;
    }
}
