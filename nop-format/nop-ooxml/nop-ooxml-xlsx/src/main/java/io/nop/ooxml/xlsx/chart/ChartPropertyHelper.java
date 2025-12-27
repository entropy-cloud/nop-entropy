/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import java.util.concurrent.ThreadLocalRandom;

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

    public static void setChildVal(XNode node, String childTagName, String value) {
        XNode childNode = node.makeChild(childTagName);
        childNode.setAttr("val", value);
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

    /**
     * 生成符合OOXML XLSX chart标准的轴ID
     * 
     * 轴ID在OOXML中是一个唯一的数字标识符，用于标识图表中的坐标轴。axId 只需在单个图表内唯一
     * 根据OOXML标准和实际Excel文件分析，轴ID通常是8-9位的正整数。
     * 
     * @return 生成的轴ID字符串，范围在10000000到999999999之间
     */
    public static String generateAxisId() {
        // 生成8-9位的随机数，确保与Excel生成的轴ID格式一致
        // 范围：10,000,000 到 999,999,999
        int axisId = ThreadLocalRandom.current().nextInt(10_000_000, 1_000_000_000);
        return String.valueOf(axisId);
    }

    /**
     * 生成一对相关联的轴ID（通常用于X轴和Y轴）
     * 
     * 在图表中，通常需要成对的轴ID来表示主轴和交叉轴的关系。
     * 此方法确保生成的两个ID不相同。
     * 
     * @return 包含两个不同轴ID的字符串数组，[0]为第一个轴ID，[1]为第二个轴ID
     */
    public static String[] generateAxisIdPair() {
        String firstId = generateAxisId();
        String secondId;
        
        // 确保两个ID不相同
        do {
            secondId = generateAxisId();
        } while (firstId.equals(secondId));
        
        return new String[]{firstId, secondId};
    }
}