/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.web.page.condition;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.query.FilterOp;
import io.nop.core.model.query.FilterOpType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实现amis的条件表达式和Nop平台的filterBean之间的双向转换
 */
public class ConditionExprHelper {

    public static Map<String, Object> filterToCondition(Object value) {
        if (value == null)
            return null;

        Map<String, Object> filter = _filterToCondition(XNode.fromValue(value));
        if(filter == null)
            return null;

        if (filter.get("conjunction") == null) {
            Map<String, Object> and = new LinkedHashMap<>();
            and.put("conjunction", "and");
            and.put("children", Arrays.asList(filter));
            return and;
        }
        return filter;
    }

    private static Map<String, Object> _filterToCondition(ITreeBean filterBean) {
        if(filterBean == null)
            return null;
        String filterOp = filterBean.getTagName();
        Map<String, Object> ret = new LinkedHashMap<>();
        if ("and".equals(filterOp) || filterOp.equals("or")) {
            ret.put("conjunction", filterOp);
            ret.put("children", filterToConditions(filterBean.getChildren()));
            return ret;
        } else if ("or".equals(filterOp)) {
            List<Map<String, Object>> list = filterToConditions(filterBean.getChildren());
            if (list == null || list.isEmpty())
                return null;
            if (list.size() == 1) {
                Map<String, Object> map = list.get(0);
                if (map.containsKey("conjunction")) {
                    map.put("not", true);
                    return map;
                }
            }
            ret.put("conjunction", "and");
            ret.put("not", true);
            ret.put("children", list);
            return ret;
        }

        String conditionOp = filterOpToConditionOp(filterOp);
        ret.put("op", conditionOp);
        String name = (String) filterBean.getAttr("name");
        if (name != null) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("type", "field");
            field.put("field", filterBean.getAttr("name"));

            ret.put("left", field);

            if (filterBean.getAttr("min") != null || filterBean.getAttr("max") != null) {
                List<Object> values = new ArrayList<>();
                values.add(filterBean.getAttr("min"));
                values.add(filterBean.getAttr("max"));
                ret.put("right", values);
            } else {
                if ("in".equals(filterOp) || "notIn".equals(filterOp)) {
                    ret.put("right", ConvertHelper.toCsvSet(filterBean.getAttr("value")));
                } else {
                    ret.put("right", filterBean.getAttr("value"));
                }
            }
            return ret;
        } else {
            return null;
        }
    }

    private static List<Map<String, Object>> filterToConditions(List<? extends ITreeBean> children) {
        if (children == null || children.isEmpty())
            return null;

        List<Map<String, Object>> list = new ArrayList<>(children.size());
        for (ITreeBean child : children) {
            Map<String, Object> item = _filterToCondition(child);
            if (item != null)
                list.add(item);
        }
        return list;
    }

    /**
     * 从amis的condition格式转化为Nop平台的filter格式
     */
    public static TreeBean conditionToFilter(Map<String, Object> cond) {
        String conditionOp = (String) cond.get("op");
        if (!StringHelper.isEmpty(conditionOp)) {
            String leftName = getLeftName(cond);
            String filterOp = conditionOpToFilterOp(conditionOp);
            Object right = cond.get("right");
            FilterOp op = FilterOp.fromName(filterOp);
            if (op != null && op.getType() == FilterOpType.BETWEEN_OP) {
                List<Object> list = (List<Object>) right;
                Object min = null;
                Object max = null;
                if (list != null) {
                    min = list.get(0);
                    max = list.get(1);
                }
                return FilterBeans.betweenOp(filterOp, leftName, min, max);
            } else {
                if (op != null) {
                    if (op.getType() == FilterOpType.ASSERT_OP) {
                        return FilterBeans.assertOp(filterOp, leftName);
                    }
                }
                return FilterBeans.compareOp(filterOp, leftName, right);
            }
        } else {
            String conjunction = (String) cond.get("conjunction");
            boolean not = ConvertHelper.toPrimitiveBoolean(cond.get("not"));
            List<Map<String, Object>> children = (List<Map<String, Object>>) cond.get("children");
            List<TreeBean> filters = conditionToFilterList(children);
            if (filters == null || filters.isEmpty())
                return null;

            boolean or = "or".equals(conjunction);
            TreeBean ret = or ? FilterBeans.or(filters) : FilterBeans.and(filters);
            if (not && or) {
                ret = FilterBeans.not(ret);
            }
            return ret;
        }
    }

    private static List<TreeBean> conditionToFilterList(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty())
            return null;

        return list.stream().map(ConditionExprHelper::conditionToFilter).collect(Collectors.toList());
    }

    private static String getLeftName(Map<String, Object> cond) {
        Object left = cond.get("left");
        if (left == null)
            return null;
        if (left instanceof Map) {
            Map<String, Object> leftMap = (Map<String, Object>) left;
            if ("field".equals(leftMap.get("type")))
                return (String) leftMap.get("field");
        }
        return null;
    }

    private static final String[] s_opPair = new String[]{
            "select_equals", "eq",
            "select_not_equals", "ne",
            "select_any_in", "in",
            "select_not_any_in", "notIn",

            "equal", "eq",
            "not_equals", "ne",
            "less", "lt",
            "less_or_equal", "le",
            "greater", "gt",
            "greater_or_equal", "ge",
            "between", "between",
            "not_between", "notBetween",
            "is_empty", "isEmpty",
            "is_not_empty", "notEmpty",
            "like", "contains",
            "not_like", "notContains",
            "starts_with", "startsWith",
            "ends_with", "endsWith",
    };

    private static Map<String, String> s_conditionOpToFilterOp = new HashMap<>();
    private static Map<String, String> s_filterOpToConditionOp = new HashMap<>();

    static {
        for (int i = 0, n = s_opPair.length; i < n; i += 2) {
            String conditionOp = s_opPair[i];
            String filterOp = s_opPair[i + 1];

            s_conditionOpToFilterOp.put(conditionOp, filterOp);
            s_filterOpToConditionOp.put(filterOp, conditionOp);
        }
    }

    public static String conditionOpToFilterOp(String conditionOp) {
        String op = s_conditionOpToFilterOp.get(conditionOp);
        if (op == null)
            op = conditionOp;
        return op;
    }

    public static String filterOpToConditionOp(String filterOp) {
        String op = s_filterOpToConditionOp.get(filterOp);
        if (op == null)
            op = filterOp;
        return op;
    }

}
