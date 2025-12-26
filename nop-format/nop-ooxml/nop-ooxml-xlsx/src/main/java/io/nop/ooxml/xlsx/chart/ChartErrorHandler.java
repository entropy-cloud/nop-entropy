package io.nop.ooxml.xlsx.chart;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.ooxml.xlsx.XlsxErrors.*;

/**
 * ChartErrorHandler - 图表解析错误处理器
 * 提供统一的错误分类、处理和恢复策略
 * 实现LOG.warn用于次要问题，异常用于关键错误的策略
 */
public class ChartErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ChartErrorHandler.class);
    
    /**
     * 错误严重程度枚举
     */
    public enum ErrorSeverity {
        MINOR,      // 次要错误，使用LOG.warn，继续解析
        MAJOR,      // 主要错误，抛出异常，停止解析
        CRITICAL    // 关键错误，抛出异常，停止整个处理流程
    }
    
    /**
     * 处理解析错误
     * @param severity 错误严重程度
     * @param parserName 解析器名称
     * @param elementName 元素名称
     * @param error 错误信息
     * @param cause 原始异常（可选）
     * @return 是否应该继续解析
     */
    public static boolean handleParseError(ErrorSeverity severity, String parserName, String elementName, 
                                         String error, Throwable cause) {
        switch (severity) {
            case MINOR:
                if (cause != null) {
                    LOG.warn("Minor parsing issue in {}.{}: {}, cause: {}", 
                            parserName, elementName, error, cause.getMessage());
                } else {
                    LOG.warn("Minor parsing issue in {}.{}: {}", parserName, elementName, error);
                }
                return true; // 继续解析
                
            case MAJOR:
                if (cause != null) {
                    LOG.error("Major parsing error in {}.{}: {}", parserName, elementName, error, cause);
                    throw new NopException(ERR_XLSX_CHART_STYLE_PARSE_FAIL)
                            .param(ARG_PARSER_NAME, parserName)
                            .param(ARG_ELEMENT_NAME, elementName)
                            .cause(cause);
                } else {
                    LOG.error("Major parsing error in {}.{}: {}", parserName, elementName, error);
                    throw new NopException(ERR_XLSX_CHART_STYLE_PARSE_FAIL)
                            .param(ARG_PARSER_NAME, parserName)
                            .param(ARG_ELEMENT_NAME, elementName);
                }
                
            case CRITICAL:
                if (cause != null) {
                    LOG.error("Critical parsing error in {}.{}: {}", parserName, elementName, error, cause);
                    throw new NopException(ERR_XLSX_CHART_PARSE_FAIL)
                            .param(ARG_CHART_ID, elementName)
                            .param(ARG_PART_NAME, parserName)
                            .cause(cause);
                } else {
                    LOG.error("Critical parsing error in {}.{}: {}", parserName, elementName, error);
                    throw new NopException(ERR_XLSX_CHART_PARSE_FAIL)
                            .param(ARG_CHART_ID, elementName)
                            .param(ARG_PART_NAME, parserName);
                }
                
            default:
                return true;
        }
    }
    
    /**
     * 处理颜色解析错误
     * @param colorName 颜色名称
     * @param error 错误信息
     * @param cause 原始异常（可选）
     * @return 默认颜色值
     */
    public static String handleColorError(String colorName, String error, Throwable cause) {
        if (cause != null) {
            LOG.warn("Color resolution failed for '{}': {}, cause: {}, using default black", 
                    colorName, error, cause.getMessage());
        } else {
            LOG.warn("Color resolution failed for '{}': {}, using default black", colorName, error);
        }
        return "#000000"; // 返回默认黑色
    }
    
    /**
     * 处理主题加载错误
     * @param partName 部件名称
     * @param error 错误信息
     * @param cause 原始异常（可选）
     */
    public static void handleThemeLoadError(String partName, String error, Throwable cause) {
        if (cause != null) {
            LOG.warn("Theme loading failed for '{}': {}, cause: {}, using default theme", 
                    partName, error, cause.getMessage());
        } else {
            LOG.warn("Theme loading failed for '{}': {}, using default theme", partName, error);
        }
    }
    
    /**
     * 处理XML节点为空的情况
     * @param parserName 解析器名称
     * @param nodeName 节点名称
     * @return 是否应该继续解析
     */
    public static boolean handleNullNode(String parserName, String nodeName) {
        LOG.warn("{}: {} node is null, skipping parsing", parserName, nodeName);
        return false; // 跳过解析
    }
    
    /**
     * 处理缺失的必需属性
     * @param parserName 解析器名称
     * @param elementName 元素名称
     * @param attributeName 属性名称
     * @return 是否应该继续解析
     */
    public static boolean handleMissingAttribute(String parserName, String elementName, String attributeName) {
        LOG.warn("{}: Missing required attribute '{}' in element '{}', using default value", 
                parserName, attributeName, elementName);
        return true; // 继续解析，使用默认值
    }
    
    /**
     * 处理无效的属性值
     * @param parserName 解析器名称
     * @param elementName 元素名称
     * @param attributeName 属性名称
     * @param value 无效值
     * @return 是否应该继续解析
     */
    public static boolean handleInvalidAttributeValue(String parserName, String elementName, 
                                                    String attributeName, String value) {
        LOG.warn("{}: Invalid value '{}' for attribute '{}' in element '{}', using default value", 
                parserName, value, attributeName, elementName);
        return true; // 继续解析，使用默认值
    }
    
    /**
     * 处理不支持的元素
     * @param parserName 解析器名称
     * @param elementName 元素名称
     * @return 是否应该继续解析
     */
    public static boolean handleUnsupportedElement(String parserName, String elementName) {
        LOG.warn("{}: Unsupported element '{}', skipping", parserName, elementName);
        return true; // 继续解析，跳过不支持的元素
    }
    
    /**
     * 处理数据格式错误
     * @param parserName 解析器名称
     * @param dataType 数据类型
     * @param value 错误值
     * @param cause 原始异常
     * @return 默认值（根据数据类型）
     */
    public static Object handleDataFormatError(String parserName, String dataType, String value, Throwable cause) {
        LOG.warn("{}: Invalid {} format '{}', cause: {}, using default value", 
                parserName, dataType, value, cause != null ? cause.getMessage() : "unknown");
        
        // 根据数据类型返回默认值
        switch (dataType.toLowerCase()) {
            case "double":
            case "number":
                return 0.0;
            case "int":
            case "integer":
                return 0;
            case "boolean":
                return false;
            case "string":
                return "";
            default:
                return null;
        }
    }
    
    /**
     * 创建优雅降级的对象
     * @param objectType 对象类型
     * @param parserName 解析器名称
     * @param <T> 对象类型
     * @return 基本对象实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T createGracefulFallback(Class<T> objectType, String parserName) {
        LOG.warn("{}: Creating graceful fallback for {}", parserName, objectType.getSimpleName());
        
        try {
            // 尝试创建默认实例
            return objectType.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            LOG.warn("{}: Failed to create fallback instance for {}, returning null", 
                    parserName, objectType.getSimpleName());
            return null;
        }
    }
    
    /**
     * 验证XML节点是否有效
     * @param node XML节点
     * @param parserName 解析器名称
     * @param nodeName 节点名称
     * @return 是否有效
     */
    public static boolean validateNode(XNode node, String parserName, String nodeName) {
        if (node == null) {
            return handleNullNode(parserName, nodeName);
        }
        return true;
    }
    
    /**
     * 安全获取属性值
     * @param node XML节点
     * @param attributeName 属性名称
     * @param parserName 解析器名称
     * @param elementName 元素名称
     * @param required 是否必需
     * @return 属性值，如果不存在且必需则记录警告
     */
    public static String safeGetAttribute(XNode node, String attributeName, String parserName, 
                                        String elementName, boolean required) {
        if (node == null) {
            if (required) {
                handleNullNode(parserName, elementName);
            }
            return null;
        }
        
        String value = node.attrText(attributeName);
        if (value == null && required) {
            handleMissingAttribute(parserName, elementName, attributeName);
        }
        
        return value;
    }
    
    /**
     * 安全解析双精度数值
     * @param value 字符串值
     * @param parserName 解析器名称
     * @param defaultValue 默认值
     * @return 解析后的数值
     */
    public static Double safeParseDouble(String value, String parserName, Double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            handleDataFormatError(parserName, "double", value, e);
            return defaultValue;
        }
    }
    
    /**
     * 安全解析整数
     * @param value 字符串值
     * @param parserName 解析器名称
     * @param defaultValue 默认值
     * @return 解析后的整数
     */
    public static Integer safeParseInteger(String value, String parserName, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            handleDataFormatError(parserName, "integer", value, e);
            return defaultValue;
        }
    }
    
    /**
     * 安全解析布尔值
     * @param value 字符串值
     * @param parserName 解析器名称
     * @param defaultValue 默认值
     * @return 解析后的布尔值
     */
    public static Boolean safeParseBoolean(String value, String parserName, Boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return "1".equals(value) || "true".equalsIgnoreCase(value);
        } catch (Exception e) {
            handleDataFormatError(parserName, "boolean", value, e);
            return defaultValue;
        }
    }
}