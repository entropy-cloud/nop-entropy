/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.util.ApiStringHelper;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import static io.nop.api.core.beans.FilterBeanConstants.DUMMY_TAG_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_ATTR_VALUE_NAME;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_DATETIME_BETWEEN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_DATE_BETWEEN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_LENGTH_BETWEEN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_BLANK;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_EMPTY;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_IN;
import static io.nop.api.core.beans.FilterBeanConstants.FILTER_OP_NOT_NULL;
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

    public static TreeBean between(String name, Object min, Object max, boolean excludeMin, boolean excludeMax) {
        return between(name, min, max).attr(FILTER_ATTR_EXCLUDE_MIN, excludeMin).attr(FILTER_ATTR_EXCLUDE_MAX, excludeMax);
    }

    public static TreeBean dateBetween(String name, LocalDate min, LocalDate max) {
        return new TreeBean(FILTER_OP_DATE_BETWEEN).attr(FILTER_ATTR_NAME,name).attr(FILTER_ATTR_MIN, min).attr(FILTER_ATTR_MAX, max);
    }

    public static TreeBean dateBetween(String name, LocalDate min, LocalDate max, boolean excludeMin,
                                       boolean excludeMax) {
        return dateBetween(name, min, max).attr(FILTER_ATTR_EXCLUDE_MIN, excludeMin).attr(FILTER_ATTR_EXCLUDE_MAX,
                excludeMax);
    }

    public static TreeBean dateTimeBetween(String name, LocalDateTime min, LocalDateTime max) {
        return new TreeBean(FILTER_OP_DATETIME_BETWEEN).attr(FILTER_ATTR_NAME,name).attr(FILTER_ATTR_MIN, min).attr(FILTER_ATTR_MAX, max);
    }

    public static TreeBean dateTimeBetween(String name, LocalDateTime min, LocalDateTime max, boolean excludeMin,
                                           boolean excludeMax) {
        return dateTimeBetween(name, min, max).attr(FILTER_ATTR_EXCLUDE_MIN, excludeMin).attr(FILTER_ATTR_EXCLUDE_MAX,
                excludeMax);
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
            if(filter.getChildCount() == 0)
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
}