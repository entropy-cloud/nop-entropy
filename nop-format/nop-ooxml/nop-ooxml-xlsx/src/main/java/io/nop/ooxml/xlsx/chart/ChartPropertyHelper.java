/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;

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

    public static String getChildVal(XNode node, String childTagName) {
        XNode childNode = node.childByTag(childTagName);
        if (childNode == null)
            return null;
        return childNode.attrText("val");
    }

    public static Boolean getChildBoolVal(XNode node, String childTagName) {
        String val = getChildVal(node, childTagName);
        return convertToBoolean(val);
    }

    public static Double getChildDoubleVal(XNode node, String childTagName) {
        XNode childNode = node.childByTag(childTagName);
        if (childNode == null)
            return null;
        return childNode.attrDouble("val");
    }

    public static Integer getChildIntVal(XNode node, String childTagName) {
        XNode childNode = node.childByTag(childTagName);
        if (childNode == null)
            return null;
        return childNode.attrInt("val");
    }
}