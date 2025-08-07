/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.nop.api.core.ApiConstants.FILTER_ATTR_EXCLUDE_MAX;
import static io.nop.api.core.ApiConstants.FILTER_ATTR_EXCLUDE_MIN;
import static io.nop.api.core.ApiConstants.FILTER_ATTR_MAX;
import static io.nop.api.core.ApiConstants.FILTER_ATTR_MIN;
import static io.nop.api.core.ApiConstants.FILTER_ATTR_NAME;
import static io.nop.api.core.ApiConstants.FILTER_ATTR_VALUE;
import static io.nop.api.core.ApiConstants.FILTER_OP_ALWAYS_FALSE;
import static io.nop.api.core.ApiConstants.FILTER_OP_ALWAYS_TRUE;
import static io.nop.api.core.ApiConstants.FILTER_OP_AND;
import static io.nop.api.core.ApiConstants.FILTER_OP_BETWEEN;
import static io.nop.api.core.ApiConstants.FILTER_OP_CONTAINS;
import static io.nop.api.core.ApiConstants.FILTER_OP_ENDS_WITH;
import static io.nop.api.core.ApiConstants.FILTER_OP_EQ;
import static io.nop.api.core.ApiConstants.FILTER_OP_GE;
import static io.nop.api.core.ApiConstants.FILTER_OP_GT;
import static io.nop.api.core.ApiConstants.FILTER_OP_IN;
import static io.nop.api.core.ApiConstants.FILTER_OP_IS_BLANK;
import static io.nop.api.core.ApiConstants.FILTER_OP_IS_EMPTY;
import static io.nop.api.core.ApiConstants.FILTER_OP_IS_NULL;
import static io.nop.api.core.ApiConstants.FILTER_OP_LE;
import static io.nop.api.core.ApiConstants.FILTER_OP_LENGTH;
import static io.nop.api.core.ApiConstants.FILTER_OP_LT;
import static io.nop.api.core.ApiConstants.FILTER_OP_NE;
import static io.nop.api.core.ApiConstants.FILTER_OP_NOT;
import static io.nop.api.core.ApiConstants.FILTER_OP_OR;
import static io.nop.api.core.ApiConstants.FILTER_OP_REGEX;
import static io.nop.api.core.ApiConstants.FILTER_OP_STARTS_WITH;
import static io.nop.api.core.ApiErrors.ERR_FILTER_BEAN_NO_OP;
import static io.nop.api.core.beans.FilterBeanConstants.DUMMY_TAG_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_LABEL;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_DATETIME_BETWEEN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_DATE_BETWEEN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_LENGTH_BETWEEN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_LIKE;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_BLANK;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_EMPTY;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_IN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_NULL;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_SQL;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_UTF8_LENGTH_BETWEEN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_TAG_NAME;

@SuppressWarnings("PMD.TooManyStaticImports")
public class FilterBeans {
    /**
     * 对单个变量进行匹配
     */
    public static TreeBean compareOp(String op, String name, Object value) {
        return new TreeBean(op).attr(FILTER_ATTR_NAME, name).attr(FILTER_ATTR_VALUE, value);
    }

    public static TreeBean propCompareOp(String op, String name, String valueName) {
        return new TreeBean(op).attr(FILTER_ATTR_NAME, name).attr(FILTER_ATTR_VALUE_NAME, valueName);
    }


    public static TreeBean assertOp(String op, String name) {
        return new TreeBean(op).attr(FILTER_ATTR_NAME, name);
    }

    /**
     * 检查两个字段是否匹配
     */
    public static TreeBean relation(String op, String name, String valueName) {
        return new TreeBean(op).attr(FILTER_ATTR_NAME, name).attr(FILTER_ATTR_VALUE_NAME, valueName);
    }

    public static TreeBean in(String name, Collection<?> values) {
        return compareOp(FILTER_OP_IN, name, values);
    }

    public static TreeBean notIn(String name, Collection<?> values) {
        return compareOp(FILTER_OP_NOT_IN, name, values);
    }

    public static TreeBean between(String name, Object min, Object max) {
        return new TreeBean(FILTER_OP_BETWEEN).attr(FILTER_ATTR_NAME, name).attr(FILTER_ATTR_MIN, min).attr(FILTER_ATTR_MAX, max);
    }

    public static TreeBean betweenOp(String op, String name, Object min, Object max) {
        return new TreeBean(op).attr(FILTER_ATTR_NAME, name).attr(FILTER_ATTR_MIN, min).attr(FILTER_ATTR_MAX, max);
    }

    public static TreeBean betweenOp(String op, String name, Object min, Object max, boolean excludeMin, boolean excludeMax) {
        TreeBean filter = betweenOp(op, name, min, max);
        return addExclude(filter, excludeMin, excludeMax);
    }

    public static TreeBean between(String name, Object min, Object max, boolean excludeMin, boolean excludeMax) {
        TreeBean filter = between(name, min, max);
        return addExclude(filter, excludeMin, excludeMax);
    }

    private static TreeBean addExclude(TreeBean filter, boolean excludeMin, boolean excludeMax) {
        if (excludeMin)
            filter.attr(FILTER_ATTR_EXCLUDE_MIN, true);
        if (excludeMax)
            filter.attr(FILTER_ATTR_EXCLUDE_MAX, true);
        return filter;
    }

    public static TreeBean dateBetween(String name, LocalDate min, LocalDate max) {
        return new TreeBean(FILTER_OP_DATE_BETWEEN)
                .attr(FILTER_ATTR_NAME, name)
                .attr(FILTER_ATTR_MIN, min)
                .attr(FILTER_ATTR_MAX, max);
    }

    public static TreeBean dateBetween(String name, LocalDate min, LocalDate max, boolean excludeMin, boolean excludeMax) {
        TreeBean filter = dateBetween(name, min, max);
        return addExclude(filter, excludeMin, excludeMax);
    }

    public static TreeBean dateTimeBetween(String name, LocalDateTime min, LocalDateTime max) {
        return new TreeBean(FILTER_OP_DATETIME_BETWEEN)
                .attr(FILTER_ATTR_NAME, name)
                .attr(FILTER_ATTR_MIN, min)
                .attr(FILTER_ATTR_MAX, max);
    }

    public static TreeBean dateTimeBetween(String name, LocalDateTime min, LocalDateTime max, boolean excludeMin, boolean excludeMax) {
        TreeBean filter = dateTimeBetween(name, min, max);
        return addExclude(filter, excludeMin, excludeMax);
    }

    public static TreeBean inRanges(String name, IntRangeSet ranges) {
        if (ranges == null || ranges.isEmpty())
            return alwaysFalse();
        if (ranges.size() == 1) {
            IntRangeBean range = ranges.getRange(0);
            return between(name, range.getStart(), range.getLast());
        }
        List<TreeBean> filters = new ArrayList<>();
        for (IntRangeBean range : ranges.getRanges()) {
            filters.add(between(name, range.getStart(), range.getLast()));
        }
        return FilterBeans.or(filters);
    }

    public static TreeBean length(String name, int value) {
        return compareOp(FILTER_OP_LENGTH, name, value);
    }

    public static TreeBean lengthBetween(String name, Integer min, Integer max) {
        TreeBean bean = new TreeBean(FILTER_OP_LENGTH_BETWEEN).attr(FILTER_ATTR_NAME, name);
        bean.attrIgnoreNull(FILTER_ATTR_MIN, min).attrIgnoreNull(FILTER_ATTR_MAX, max);
        return bean;
    }

    public static TreeBean utf8LengthBetween(String name, Integer min, Integer max) {
        TreeBean bean = new TreeBean(FILTER_OP_UTF8_LENGTH_BETWEEN).attr(FILTER_ATTR_NAME, name);
        bean.attrIgnoreNull(FILTER_ATTR_MIN, min).attrIgnoreNull(FILTER_ATTR_MAX, max);
        return bean;
    }

    public static TreeBean propEq(String name, String valueName) {
        return propCompareOp(FILTER_OP_EQ, name, valueName);
    }

    public static TreeBean propNe(String name, String valueName) {
        return propCompareOp(FILTER_OP_NE, name, valueName);
    }

    public static TreeBean propGt(String name, String valueName) {
        return propCompareOp(FILTER_OP_GT, name, valueName);
    }

    public static TreeBean propGe(String name, String valueName) {
        return propCompareOp(FILTER_OP_GE, name, valueName);
    }

    public static TreeBean propLt(String name, String valueName) {
        return propCompareOp(FILTER_OP_LT, name, valueName);
    }

    public static TreeBean propLe(String name, String valueName) {
        return propCompareOp(FILTER_OP_LE, name, valueName);
    }

    public static TreeBean propStartsWith(String name, String valueName) {
        return propCompareOp(FILTER_OP_STARTS_WITH, name, valueName);
    }

    public static TreeBean propEndsWith(String name, String valueName) {
        return propCompareOp(FILTER_OP_ENDS_WITH, name, valueName);
    }

    public static TreeBean propContains(String name, String valueName) {
        return propCompareOp(FILTER_OP_CONTAINS, name, valueName);
    }

    public static TreeBean propRegex(String name, String valueName) {
        return propCompareOp(FILTER_OP_REGEX, name, valueName);
    }

    public static TreeBean eq(String name, Object value) {
        return compareOp(FILTER_OP_EQ, name, value);
    }

    public static TreeBean ne(String name, Object value) {
        return compareOp(FILTER_OP_NE, name, value);
    }

    public static TreeBean gt(String name, Object value) {
        return compareOp(FILTER_OP_GT, name, value);
    }

    public static TreeBean ge(String name, Object value) {
        return compareOp(FILTER_OP_GE, name, value);
    }

    public static TreeBean lt(String name, Object value) {
        return compareOp(FILTER_OP_LT, name, value);
    }

    public static TreeBean le(String name, Object value) {
        return compareOp(FILTER_OP_LE, name, value);
    }

    public static TreeBean startsWith(String name, Object value) {
        return compareOp(FILTER_OP_STARTS_WITH, name, value);
    }

    public static TreeBean endsWith(String name, Object value) {
        return compareOp(FILTER_OP_ENDS_WITH, name, value);
    }

    public static TreeBean like(String name, String value) {
        return compareOp(FILTER_OP_LIKE, name, value);
    }

    public static TreeBean contains(String name, Object value) {
        return compareOp(FILTER_OP_CONTAINS, name, value);
    }

    public static TreeBean regex(String name, Object value) {
        return compareOp(FILTER_OP_REGEX, name, value);
    }

    public static TreeBean alwaysTrue() {
        return new TreeBean(FILTER_OP_ALWAYS_TRUE);
    }

    public static TreeBean alwaysFalse() {
        return new TreeBean(FILTER_OP_ALWAYS_FALSE);
    }

    public static TreeBean isNull(String name) {
        return new TreeBean(FILTER_OP_IS_NULL).attr(FILTER_ATTR_NAME, name);
    }

    public static TreeBean isEmpty(String name) {
        return new TreeBean(FILTER_OP_IS_EMPTY).attr(FILTER_ATTR_NAME, name);
    }

    public static TreeBean isBlank(String name) {
        return new TreeBean(FILTER_OP_IS_BLANK).attr(FILTER_ATTR_NAME, name);
    }

    public static TreeBean notNull(String name) {
        return new TreeBean(FILTER_OP_NOT_NULL).attr(FILTER_ATTR_NAME, name);
    }

    public static TreeBean notEmpty(String name) {
        return new TreeBean(FILTER_OP_NOT_EMPTY).attr(FILTER_ATTR_NAME, name);
    }

    public static TreeBean notBlank(String name) {
        return new TreeBean(FILTER_OP_NOT_BLANK).attr(FILTER_ATTR_NAME, name);
    }

    public static TreeBean not(TreeBean filter) {
        TreeBean ret = new TreeBean(FILTER_OP_NOT);
        ret.addChild(filter);
        return ret;
    }

    public static TreeBean and(TreeBean... filters) {
        if (filters.length == 0)
            return alwaysTrue();

        if (filters.length == 1)
            return filters[0];

        TreeBean ret = new TreeBean(FILTER_OP_AND);
        for (int i = 0, n = filters.length; i < n; i++) {
            TreeBean filter = filters[i];
            if (filter == null)
                continue;
            if (filter.getTagName().equals(FILTER_OP_AND) || DUMMY_TAG_NAME.equals(filter.getTagName())) {
                if (filter.getChildren() != null) {
                    for (TreeBean child : filter.getChildren()) {
                        ret.addChild(child);
                    }
                }
            } else {
                ret.addChild(filter);
            }
        }

        if (!ret.hasChild())
            return alwaysTrue();
        return ret;
    }

    public static TreeBean or(TreeBean... filters) {
        if (filters.length == 0)
            return alwaysTrue();

        if (filters.length == 1)
            return filters[0];

        TreeBean ret = new TreeBean(FILTER_OP_OR);
        for (int i = 0, n = filters.length; i < n; i++) {
            TreeBean filter = filters[i];
            if (filter.getTagName().equals(FILTER_OP_OR)) {
                if (filter.getChildren() != null) {
                    for (TreeBean child : filter.getChildren()) {
                        ret.addChild(child);
                    }
                }
            } else {
                ret.addChild(filter);
            }
        }
        return ret;
    }

    public static TreeBean and(List<TreeBean> filters) {
        if (filters.size() == 0)
            return alwaysTrue();

        if (filters.size() == 1)
            return filters.get(0);

        TreeBean ret = new TreeBean(FILTER_OP_AND);
        ret.setChildren(filters);
        return ret;
    }

    public static TreeBean or(List<TreeBean> filters) {
        if (filters.size() == 0)
            return alwaysTrue();

        if (filters.size() == 1)
            return filters.get(0);

        TreeBean ret = new TreeBean(FILTER_OP_OR);
        ret.setChildren(filters);
        return ret;
    }

    public static TreeBean normalizeFilterBean(ITreeBean filter) {
        if (filter == null)
            return null;
        TreeBean tree = filter.toTreeBean();
        if (tree.getTagName().equals(DUMMY_TAG_NAME) || tree.getTagName().equals(FILTER_TAG_NAME)) {
            if (filter.getChildCount() == 0)
                return null;

            if (tree == filter)
                tree = tree.cloneInstance();
            tree.setTagName(FILTER_OP_AND);
        }
        return tree;
    }

    public static TreeBean propsEq(Map<String, Object> props) {
        if (props == null)
            return null;

        TreeBean ret = new TreeBean();
        ret.setTagName(FILTER_OP_AND);
        props.forEach((name, value) -> {
            if (ApiStringHelper.isEmptyObject(value))
                return;
            ret.addChild(eq(name, value));
        });

        if (!ret.hasChild())
            return null;

        return ret;
    }

    public static TreeBean sql(Object value) {
        TreeBean bean = new TreeBean();
        bean.setTagName(FILTER_OP_SQL);
        bean.setAttr(FILTER_ATTR_VALUE, value);
        return bean;
    }

    public static List<FilterBean> toFilterBeanList(TreeBean filter) {
        List<FilterBean> list = new ArrayList<>();
        toFilterBeansImpl(filter, 0, list);
        return list;
    }

    private static void toFilterBeansImpl(TreeBean node, int level, List<FilterBean> list) {
        if (node == null) return;

        String tag = node.getTagName();

        // 判断是否为逻辑节点
        boolean isLogic = FILTER_OP_AND.equalsIgnoreCase(tag)
                || FILTER_OP_OR.equalsIgnoreCase(tag)
                || FILTER_OP_NOT.equalsIgnoreCase(tag);

        if (isLogic) {
            // 逻辑节点也作为一条FilterBean输出，有利于表格展现清晰结构
            FilterBean logicNode = new FilterBean();
            logicNode.setLevel(level);
            logicNode.setLogic(tag);
            list.add(logicNode);

            if (node.getChildren() != null) {
                for (TreeBean child : node.getChildren()) {
                    toFilterBeansImpl(child, level + 1, list);
                }
            }
        } else {
            // 叶子节点（比较操作，如eq、gt等）
            // tagName就是op
            FilterBean leaf = new FilterBean();
            leaf.setLevel(level);
            leaf.setOp(tag);
            if (node.getAttrs() != null) {
                leaf.setName((String) node.getAttr(FILTER_ATTR_NAME));
                leaf.setValue(node.getAttr(FILTER_ATTR_VALUE));
                leaf.setValueName((String) node.getAttr(FILTER_ATTR_VALUE_NAME));
                leaf.setLabel((String) node.getAttr(FILTER_ATTR_LABEL));
            }
            list.add(leaf);
        }
    }

    public static TreeBean fromFilterBeanList(List<FilterBean> beans) {
        if (beans == null || beans.isEmpty()) return null;
        int[] idx = {0};
        return buildTree(beans, idx, 0); // 从最小level开始
    }

    private static TreeBean buildTree(List<FilterBean> beans, int[] idx, int currentLevel) {
        List<TreeBean> children = new ArrayList<>();

        while (idx[0] < beans.size()) {
            FilterBean bean = beans.get(idx[0]);
            int lvl = bean.getLevel();

            if (lvl < currentLevel) {
                // 级别小于当前级别，说明回到上级
                break;
            }

            TreeBean node = buildTreeNode(beans, bean, idx);
            children.add(node);
            // 处理完一个节点后，检查下一个节点是否还在当前层级
            if (idx[0] < beans.size() && beans.get(idx[0]).getLevel() < lvl) {
                break; // 下一个节点级别更小，回到上级
            }
        }

        if (children.size() == 1) {
            return children.get(0);
        } else if (children.size() > 1) {
            TreeBean root = new TreeBean();
            root.setTagName(FILTER_OP_AND);
            root.setChildren(children);
            return root;
        }

        return null;
    }

    static TreeBean buildTreeNode(List<FilterBean> beans, FilterBean bean, int[] idx) {
        int lvl = bean.getLevel();

        if (bean.getLogic() != null || bean.getOp() == null) {
            // 逻辑节点
            String logic = (bean.getLogic() == null || bean.getLogic().isEmpty()) ? FILTER_OP_AND : bean.getLogic();
            TreeBean node = new TreeBean();
            node.setTagName(logic);
            idx[0]++; // 消耗当前节点

            // 递归收集所有子节点（level > lvl的节点）
            List<TreeBean> logicChildren = new ArrayList<>();

            if (containsOp(bean)) {
                TreeBean cond = new TreeBean();
                cond.setTagName(bean.getOp());
                cond.setAttr(FILTER_ATTR_NAME, bean.getName());
                cond.setAttr(FILTER_ATTR_VALUE, bean.getValue());
                cond.setAttr(FILTER_ATTR_VALUE_NAME, bean.getValueName());
                cond.setAttr(FILTER_ATTR_LABEL, bean.getLabel());
                logicChildren.add(cond);
            }

            while (idx[0] < beans.size() && beans.get(idx[0]).getLevel() > lvl) {
                TreeBean child = buildTreeNode(beans, beans.get(idx[0]), idx); // 修改这里，传递lvl+1作为下一级的最小level
                if (child != null) {
                    logicChildren.add(child);
                }
            }

            if (FILTER_OP_AND.equals(node.getTagName()) && logicChildren.size() == 1) {
                node = logicChildren.get(0);
            } else {
                node.setChildren(logicChildren);
            }
            return node;
        } else {
            // 叶子节点处理保持不变
            if (bean.getOp() == null)
                throw new NopException(ERR_FILTER_BEAN_NO_OP);
            TreeBean node = new TreeBean();
            node.setTagName(bean.getOp());
            node.setAttr(FILTER_ATTR_NAME, bean.getName());
            node.setAttr(FILTER_ATTR_VALUE, bean.getValue());
            node.setAttr(FILTER_ATTR_VALUE_NAME, bean.getValueName());
            node.setAttr(FILTER_ATTR_LABEL, bean.getLabel());
            idx[0]++;
            return node;
        }
    }

    static boolean containsOp(FilterBean bean) {
        if (FILTER_OP_ALWAYS_TRUE.equals(bean.getOp()) || FILTER_OP_ALWAYS_FALSE.equals(bean.getOp()))
            return false;
        return bean.getName() != null && bean.getOp() != null;
    }
}