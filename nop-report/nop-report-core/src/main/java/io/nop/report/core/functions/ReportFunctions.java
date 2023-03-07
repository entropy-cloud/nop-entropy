/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.functions;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.commons.lang.IValueWrapper;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

import java.util.Iterator;

@Locale("zh-CN")
public class ReportFunctions {
    @Description("求和。忽略所有非数值类型")
    public static Number SUM(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        Number ret = 0;

        while (it.hasNext()) {
            Object value = it.next();
            if (!(value instanceof Number))
                continue;
            ret = MathHelper.add(ret, value);
        }
        return ret;
    }

    @Description("求所有值的乘积。忽略所有非数值类型")
    public static Number PRODUCT(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        Number ret = 1;

        while (it.hasNext()) {
            Object value = it.next();
            if (!(value instanceof Number))
                continue;
            ret = MathHelper.multiply(ret, value);
        }
        return ret;
    }

    @Description("对数值单元格进行计数")
    public static Number COUNT(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        int count = 0;
        while (it.hasNext()) {
            Object value = it.next();
            // 仅对数值单元格计数，逻辑与Excel相同
            if (!(value instanceof Number))
                continue;
            count++;
        }

        return count;
    }

    @Description("对非空单元格进行计数")
    public static Number COUNTA(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        int count = 0;
        while (it.hasNext()) {
            Object value = it.next();
            if (StringHelper.isEmptyObject(value))
                continue;
            count++;
        }

        return count;
    }

    @Description("求平均值")
    public static Number AVERAGE(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        Number ret = 0;

        int count = 0;
        while (it.hasNext()) {
            Object value = it.next();
            if (!(value instanceof Number))
                continue;
            ret = MathHelper.add(ret, value);
            count++;
        }

        // count=0时Excel会显示除零错误
        return MathHelper.divide(ret, count);
    }

    @Description("求最小值")
    public static Number MIN(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        Object ret = null;

        while (it.hasNext()) {
            Object value = it.next();
            if (!(value instanceof Number))
                continue;
            if (ret == null) {
                ret = value;
            } else {
                ret = MathHelper.min(ret, value);
            }
        }

        return (Number) ret;
    }

    @Description("求最大值。忽略所有非数值类型")
    public static Number MAX(@Name("values") Object values) {
        if (values == null)
            return null;

        Iterator<Object> it = CollectionHelper.toIterator(values, true);
        Object ret = null;

        while (it.hasNext()) {
            Object value = it.next();
            if (!(value instanceof Number))
                continue;
            if (ret == null) {
                ret = value;
            } else {
                ret = MathHelper.max(ret, value);
            }
        }

        return (Number) ret;
    }

    @Description("当第一个参数为null时，使用第二个参数作为返回值")
    public static Object NVL(@Name("value") Object value, @Name("defaultValue") Object defaultValue) {
        value = resolveValue(value);
        if (value == null) {
            value = resolveValue(defaultValue);
        }
        return value;
    }

    private static Object resolveValue(Object value) {
        if (value instanceof IValueWrapper)
            value = ((IValueWrapper) value).getValue();
        return value;
    }
}