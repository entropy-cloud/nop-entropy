/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

/**
 * Helper class for safely setting properties on chart model objects.
 * Provides type conversion and error handling for chart model property setting.
 */
public class ChartPropertyHelper {

    public static Boolean convertToBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;

        String str = value.toString().toLowerCase().trim();
        switch (str) {
            case "1":
            case "true":
            case "yes":
            case "on":
                return Boolean.TRUE;
            case "0":
            case "false":
            case "no":
            case "off":
                return Boolean.FALSE;
            default:
                return null;
        }
    }
}