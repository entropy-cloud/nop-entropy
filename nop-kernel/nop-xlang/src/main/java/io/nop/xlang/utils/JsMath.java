/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.utils;

import io.nop.commons.util.MathHelper;

/**
 * JavaScript Math 全局对象兼容：XScript 中裸名 {@code Math.abs(x)} 映射到本类静态方法。
 * 委托 {@link MathHelper}（复用既有数值语义），补充 JS 命名（trunc/sign/log2/cbrt/tanh 等）。
 */
public class JsMath {

    public static final Double PI = MathHelper.PI;
    public static final Double E = Math.E;
    public static final Double LN2 = Math.log(2);
    public static final Double LN10 = Math.log(10);
    public static final Double LOG2E = 1.0 / Math.log(2);
    public static final Double LOG10E = 1.0 / Math.log(10);
    public static final Double SQRT2 = Math.sqrt(2);
    public static final Double SQRT1_2 = Math.sqrt(0.5);
    public static final Double MAX_VALUE = MathHelper.MAX_DOUBLE_VALUE;
    public static final Double MIN_VALUE = MathHelper.MIN_DOUBLE_VALUE;
    public static final Double NaN = MathHelper.NaN;

    public static Number abs(Object value) {
        return MathHelper.abs(value);
    }

    public static Number ceil(Object value) {
        Number v = MathHelper.ceil(value);
        if (v instanceof Double || v instanceof Float) {
            double d = v.doubleValue();
            if (d == Math.rint(d) && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE)
                return (long) d;
        }
        return v;
    }

    public static Number floor(Object value) {
        Number v = MathHelper.floor(value);
        if (v instanceof Double || v instanceof Float) {
            double d = v.doubleValue();
            if (d == Math.rint(d) && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE)
                return (long) d;
        }
        return v;
    }

    public static Number round(Object value) {
        double d = toDouble(value);
        return (long) Math.round(d);
    }

    public static long trunc(Object value) {
        if (value instanceof Number)
            return ((Number) value).longValue();
        return Long.parseLong(String.valueOf(value));
    }

    public static Object max(Object v1, Object v2) {
        return MathHelper.max(v1, v2);
    }

    public static Object min(Object v1, Object v2) {
        return MathHelper.min(v1, v2);
    }

    public static Number pow(Object value, Object scale) {
        Number v = MathHelper.pow(value, scale);
        if (v instanceof Double || v instanceof Float) {
            double d = v.doubleValue();
            if (d == Math.rint(d) && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE)
                return (long) d;
        }
        return v;
    }

    public static Number sqrt(Object value) {
        Number v = MathHelper.sqrt(value);
        if (v instanceof Double || v instanceof Float) {
            double d = v.doubleValue();
            if (d == Math.rint(d) && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE)
                return (long) d;
        }
        return v;
    }

    public static Number cbrt(Object value) {
        double v = toDouble(value);
        return Math.cbrt(v);
    }

    public static Number log(Object value) {
        return MathHelper.log(value);
    }

    public static Number log2(Object value) {
        double v = toDouble(value);
        return Math.log(v) / Math.log(2);
    }

    public static Number log10(Object value) {
        return MathHelper.log10(value);
    }

    public static Number exp(Object value) {
        return MathHelper.exp(value);
    }

    public static Number sin(Object value) {
        return MathHelper.sin(value);
    }

    public static Number cos(Object value) {
        return MathHelper.cos(value);
    }

    public static Number tan(Object value) {
        double v = toDouble(value);
        return Math.tan(v);
    }

    public static Number asin(Object value) {
        return Math.asin(toDouble(value));
    }

    public static Number acos(Object value) {
        return Math.acos(toDouble(value));
    }

    public static Number atan(Object value) {
        return Math.atan(toDouble(value));
    }

    public static Number atan2(Object y, Object x) {
        return Math.atan2(toDouble(y), toDouble(x));
    }

    public static Number sinh(Object value) {
        return Math.sinh(toDouble(value));
    }

    public static Number cosh(Object value) {
        return Math.cosh(toDouble(value));
    }

    public static Number tanh(Object value) {
        return Math.tanh(toDouble(value));
    }

    public static int sign(Object value) {
        double v = toDouble(value);
        if (v > 0)
            return 1;
        if (v < 0)
            return -1;
        return 0;
    }

    public static double random() {
        return MathHelper.random().nextDouble();
    }

    private static double toDouble(Object value) {
        if (value instanceof Number)
            return ((Number) value).doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }
}