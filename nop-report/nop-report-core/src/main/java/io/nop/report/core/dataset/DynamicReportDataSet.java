/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.dataset;

import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.report.core.XptConstants;
import io.nop.report.core.model.ExpandedCell;

import java.util.ArrayList;
import java.util.List;

import static io.nop.report.core.XptErrors.ARG_DS_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_MISSING_VAR_DS;

/**
 * 根据单元格当前位置动态确定数据集中的内容
 */
public class DynamicReportDataSet extends ReportDataSet {
    public DynamicReportDataSet(String dsName, List<Object> items) {
        super(dsName, items);
    }

    @EvalMethod
    public static DynamicReportDataSet makeDataSet(IEvalScope scope, String dsName) {
        Object value = scope.getValue(dsName);
        if (value == null) {
            throw new NopException(ERR_XPT_MISSING_VAR_DS)
                    .param(ARG_DS_NAME, dsName);
        }

        DynamicReportDataSet ds;
        if (value instanceof DynamicReportDataSet) {
            DynamicReportDataSet rs = (DynamicReportDataSet) value;
            if (rs.getDsName().equals(dsName))
                return rs;
            ds = new DynamicReportDataSet(dsName, rs.getItems());
        } else {
            List<Object> items = CollectionHelper.toList(value);
            ds = new DynamicReportDataSet(dsName, items);
        }
        scope.setLocalValue(null, dsName, ds);
        return ds;
    }

    @Override
    @EvalMethod
    public List<Object> current(IEvalScope scope) {
        ExpandedCell cell = (ExpandedCell) scope.getValue(XptConstants.VAR_CELL);
        if (cell == null)
            return getItems();

        List<Object> items = getDsItems(cell, getDsName());
        if (items == null)
            items = getItems();
        return items;
    }

    public static List<Object> getDsItems(ExpandedCell cell, String dsName) {
        if (dsName == null) {
            dsName = getDsName(cell);
        }

        // 行坐标决定的数据集
        List<Object> rowItems = getRowParentItems(cell, dsName);

        // 列坐标决定的数据集
        List<Object> colItems = getColParentItems(cell, dsName);

        if (rowItems == null && colItems == null) {
            return null;
        }

        if (rowItems == null)
            return colItems;

        if (colItems == null)
            return rowItems;

        // 返回两个集合中的公共部分。这意味着当前数据集满足同时满足行列坐标的要求
        List<Object> listA = rowItems;
        List<Object> listB = colItems;
        if (listA.size() > listB.size()) {
            List<Object> tmp = listB;
            listA = listB;
            listB = tmp;
        }

        List<Object> ret = new ArrayList<>();
        for (Object item : listA) {
            if (CollectionHelper.identityContains(listB, item)) {
                ret.add(item);
            }
        }
        return ret;
    }

    public static String getDsName(ExpandedCell cell) {
        String dsName = cell.getModel().getDs();
        if (dsName != null)
            return dsName;

        if (cell.getRowParent() != null) {
            dsName = cell.getDsName();
            if (dsName != null)
                return dsName;
        }

        if (cell.getColParent() != null) {
            dsName = cell.getDsName();
            if (dsName != null)
                return dsName;
        }

        return dsName;
    }

    private static List<Object> getRowParentItems(ExpandedCell cell, String dsName) {
        ExpandedCell parent = cell.getRowParent();
        if (parent == null)
            return null;

        ReportDataSet ds = parent.getDs();
        if (ds != null && (dsName == null || ds.getDsName().equals(dsName)))
            return ds.getItems();
        return getRowParentItems(parent, dsName);
    }

    private static List<Object> getColParentItems(ExpandedCell cell, String dsName) {
        ExpandedCell parent = cell.getColParent();
        if (parent == null)
            return null;

        ReportDataSet ds = parent.getDs();
        if (ds != null && (dsName == null || ds.getDsName().equals(dsName)))
            return ds.getItems();
        return getColParentItems(parent, dsName);
    }
}