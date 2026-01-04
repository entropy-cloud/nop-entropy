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

        String str = value.toString();
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

    public static void setChildBoolVal(XNode node, String childTagName, Boolean value) {
        setChildVal(node, childTagName, Boolean.TRUE.equals(value) ? "1" : "0");
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
     * <p>
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
     * <p>
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

    // ==================== 角度转换帮助函数 ====================

    /**
     * OOXML角度转换常量
     * OOXML中角度值使用1/60000度为单位
     */
    private static final double OOXML_ANGLE_UNIT = 60000.0;

    /**
     * 将度转换为OOXML角度单位（1/60000度）
     *
     * @param degrees 角度值（度）
     * @return OOXML角度值（1/60000度单位）
     */
    public static long degreesToOoxmlAngle(double degrees) {
        return Math.round(degrees * OOXML_ANGLE_UNIT);
    }

    /**
     * 将OOXML角度单位转换为度
     *
     * @param ooxmlAngle OOXML角度值（1/60000度单位）
     * @return 角度值（度）
     */
    public static double ooxmlAngleToDegrees(long ooxmlAngle) {
        return ooxmlAngle / OOXML_ANGLE_UNIT;
    }

    /**
     * 将度转换为OOXML角度字符串（处理null值）
     *
     * @param degrees 角度值（度），可以为null
     * @return OOXML角度字符串，如果输入为null则返回null
     */
    public static String degreesToOoxmlAngleString(Double degrees) {
        if (degrees == null) {
            return null;
        }
        return String.valueOf(degreesToOoxmlAngle(degrees));
    }

    /**
     * 将OOXML角度字符串转换为度（处理null值和解析错误）
     *
     * @param ooxmlAngleStr OOXML角度字符串，可以为null或空
     * @return 角度值（度），如果输入无效则返回null
     */
    public static Double ooxmlAngleStringToDegrees(String ooxmlAngleStr) {
        if (ooxmlAngleStr == null || ooxmlAngleStr.trim().isEmpty()) {
            return null;
        }

        long ooxmlAngle = Long.parseLong(ooxmlAngleStr.trim());
        return ooxmlAngleToDegrees(ooxmlAngle);
    }

    /**
     * 获取子节点的角度值并转换为度
     *
     * @param node         父节点
     * @param childTagName 子节点标签名
     * @return 角度值（度），如果节点不存在或解析失败则返回null
     */
    public static Double getChildAngleVal(XNode node, String childTagName) {
        String angleStr = getChildVal(node, childTagName);
        return ooxmlAngleStringToDegrees(angleStr);
    }

    /**
     * 设置子节点的角度值（从度转换为OOXML单位）
     *
     * @param node         父节点
     * @param childTagName 子节点标签名
     * @param degrees      角度值（度）
     */
    public static void setChildAngleVal(XNode node, String childTagName, Double degrees) {
        if (degrees != null) {
            String ooxmlAngleStr = degreesToOoxmlAngleString(degrees);
            setChildVal(node, childTagName, ooxmlAngleStr);
        }
    }

    public static Double getThousandthAttr(XNode node, String attrName) {
        Integer value = node.attrInt(attrName);
        if (value == null)
            return null;
        return value.doubleValue() / 1000.0;
    }

    public static void setThousandthAttr(XNode node, String attrName, Double value) {
        if (value != null) {
            int v = (int) (value * 1000);
            node.setAttr(attrName, v);
        }
    }

    /**
     * 将OOXML的alpha值转换为opacity(0-1)
     *
     * @param alpha OOXML alpha值 (0-100000)
     * @return opacity值 (0.0-1.0)
     */
    public static double alphaToOpacity(int alpha) {
        // 确保在有效范围内
        if (alpha < 0) return 0.0;
        if (alpha > 100000) return 1.0;
        return alpha / 100000.0;
    }

    /**
     * 将opacity(0-1)转换为OOXML的alpha值
     *
     * @param opacity 透明度值 (0.0-1.0)
     * @return OOXML alpha值 (0-100000)
     */
    public static int opacityToAlpha(double opacity) {
        // 确保在有效范围内
        if (opacity <= 0.0) return 0;
        if (opacity >= 1.0) return 100000;
        return (int) Math.round(opacity * 100000);
    }

    public static Double getChildAlphaVal(XNode node, String childTagName) {
        Integer value = getChildIntVal(node, childTagName);
        if (value == null)
            return null;
        return alphaToOpacity(value);
    }

    public static void setChildAlphaVal(XNode node, String childTagName, Double opacity) {
        if (opacity != null) {
            int alpha = opacityToAlpha(opacity);
            setChildVal(node, childTagName, String.valueOf(alpha));
        }
    }
}