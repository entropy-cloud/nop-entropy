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
 * JavaScript Number 全局对象兼容：XScript 中裸名 {@code Number.parseInt(s)} 映射到本类静态方法。
 */
public class JsNumber {

    public static final Long MAX_SAFE_INTEGER = MathHelper.MAX_JS_LONG;
    public static final Long MIN_SAFE_INTEGER = -MathHelper.MAX_JS_LONG;
    public static final Double MAX_VALUE = MathHelper.MAX_DOUBLE_VALUE;
    public static final Double MIN_VALUE = MathHelper.MIN_DOUBLE_VALUE;
    public static final Double NaN = MathHelper.NaN;
    public static final Double POSITIVE_INFINITY = Double.POSITIVE_INFINITY;
    public static final Double NEGATIVE_INFINITY = Double.NEGATIVE_INFINITY;
    public static final Double EPSILON = 2.220446049250313e-16;

    public static Integer parseInt(String s) {
        return parseInt(s, 10);
    }

    public static Integer parseInt(String s, int radix) {
        return Integer.parseInt(s, radix);
    }

    public static Double parseFloat(String s) {
        return Double.parseDouble(s);
    }

    public static boolean isNaN(Object value) {
        if (value instanceof Number) {
            double v = ((Number) value).doubleValue();
            return Double.isNaN(v);
        }
        return false;
    }

    public static boolean isFinite(Object value) {
        if (value instanceof Number) {
            double v = ((Number) value).doubleValue();
            return Double.isFinite(v);
        }
        return false;
    }

    public static boolean isInteger(Object value) {
        if (value instanceof Long || value instanceof Integer)
            return true;
        if (value instanceof Number) {
            double v = ((Number) value).doubleValue();
            return v == Math.floor(v) && !Double.isInfinite(v);
        }
        return false;
    }
}