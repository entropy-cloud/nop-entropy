/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * ChartBuilderValidator - 图表构建器验证工具
 * 负责验证解析器和构建器系统的一致性
 * 确保相同的IChartStyleProvider接口使用和默认值一致性
 */
public class ChartBuilderValidator {
    private static final Logger LOG = LoggerFactory.getLogger(ChartBuilderValidator.class);

    public static final ChartBuilderValidator INSTANCE = new ChartBuilderValidator();

    /**
     * 验证解析器和构建器的一致性
     * 
     * @param originalXml 原始XML
     * @param chartModel 解析后的图表模型
     * @param rebuiltXml 重新构建的XML
     * @return 验证结果
     */
    public ValidationResult validateConsistency(XNode originalXml, ChartModel chartModel, XNode rebuiltXml) {
        ValidationResult result = new ValidationResult();
        
        try {
            // 验证样式提供者一致性
            validateStyleProviderConsistency(result, originalXml, rebuiltXml);
            
            // 验证默认值一致性
            validateDefaultValueConsistency(result, chartModel);
            
            // 验证图表类型映射一致性
            validateChartTypeMappingConsistency(result, originalXml, rebuiltXml);
            
            // 验证命名空间一致性
            validateNamespaceConsistency(result, originalXml, rebuiltXml);
            
            // 验证元素层次结构一致性
            validateElementHierarchyConsistency(result, originalXml, rebuiltXml);
            
        } catch (Exception e) {
            result.addError("Validation failed with exception: " + e.getMessage());
            LOG.warn("Validation failed", e);
        }
        
        return result;
    }

    /**
     * 验证样式提供者一致性
     */
    private void validateStyleProviderConsistency(ValidationResult result, XNode originalXml, XNode rebuiltXml) {
        try {
            // 检查颜色解析和主题应用的一致性
            validateColorConsistency(result, originalXml, rebuiltXml);
            
            // 检查样式元素的一致性
            validateStyleElementConsistency(result, originalXml, rebuiltXml);
            
        } catch (Exception e) {
            result.addWarning("Style provider consistency validation failed: " + e.getMessage());
        }
    }

    /**
     * 验证颜色一致性
     */
    private void validateColorConsistency(ValidationResult result, XNode originalXml, XNode rebuiltXml) {
        // 检查主题颜色引用
        List<XNode> originalThemeColors = findElementsByPath(originalXml, "//a:schemeClr");
        List<XNode> rebuiltThemeColors = findElementsByPath(rebuiltXml, "//a:schemeClr");
        
        if (originalThemeColors.size() != rebuiltThemeColors.size()) {
            result.addWarning("Theme color count mismatch: original=" + originalThemeColors.size() + 
                            ", rebuilt=" + rebuiltThemeColors.size());
        }
        
        // 检查RGB颜色值
        List<XNode> originalRgbColors = findElementsByPath(originalXml, "//a:srgbClr");
        List<XNode> rebuiltRgbColors = findElementsByPath(rebuiltXml, "//a:srgbClr");
        
        if (originalRgbColors.size() != rebuiltRgbColors.size()) {
            result.addWarning("RGB color count mismatch: original=" + originalRgbColors.size() + 
                            ", rebuilt=" + rebuiltRgbColors.size());
        }
    }

    /**
     * 验证样式元素一致性
     */
    private void validateStyleElementConsistency(ValidationResult result, XNode originalXml, XNode rebuiltXml) {
        // 检查形状属性元素
        List<XNode> originalSpPr = findElementsByPath(originalXml, "//c:spPr");
        List<XNode> rebuiltSpPr = findElementsByPath(rebuiltXml, "//c:spPr");
        
        if (originalSpPr.size() != rebuiltSpPr.size()) {
            result.addInfo("Shape properties count difference: original=" + originalSpPr.size() + 
                          ", rebuilt=" + rebuiltSpPr.size());
        }
        
        // 检查文本属性元素
        List<XNode> originalTxPr = findElementsByPath(originalXml, "//c:txPr");
        List<XNode> rebuiltTxPr = findElementsByPath(rebuiltXml, "//c:txPr");
        
        if (originalTxPr.size() != rebuiltTxPr.size()) {
            result.addInfo("Text properties count difference: original=" + originalTxPr.size() + 
                          ", rebuilt=" + rebuiltTxPr.size());
        }
    }

    /**
     * 验证默认值一致性
     */
    private void validateDefaultValueConsistency(ValidationResult result, ChartModel chartModel) {
        try {
            // 验证图表级别的默认值
            if (chartModel.getType() == null) {
                result.addWarning("Chart type is null, should have a default value");
            }
            
            // 验证绘图区域的默认值
            if (chartModel.getPlotArea() != null) {
                validatePlotAreaDefaults(result, chartModel);
            }
            
            // 验证标题的默认值
            if (chartModel.getTitle() != null) {
                validateTitleDefaults(result, chartModel);
            }
            
            // 验证图例的默认值
            if (chartModel.getLegend() != null) {
                validateLegendDefaults(result, chartModel);
            }
            
        } catch (Exception e) {
            result.addWarning("Default value consistency validation failed: " + e.getMessage());
        }
    }

    /**
     * 验证绘图区域默认值
     */
    private void validatePlotAreaDefaults(ValidationResult result, ChartModel chartModel) {
        // 检查系列的默认值
        if (chartModel.getPlotArea().getSeriesList() != null) {
            chartModel.getPlotArea().getSeriesList().forEach(series -> {
                // 由于visible已设置为非空，不需要检查null值
                // series.isVisible() 总是返回boolean值
            });
        }
        
        // 检查坐标轴的默认值
        if (chartModel.getPlotArea().getAxes() != null) {
            chartModel.getPlotArea().getAxes().forEach(axis -> {
                if (axis.isVisible()) {
                    result.addInfo("Axis visibility should have default value");
                }
            });
        }
    }

    /**
     * 验证标题默认值
     */
    private void validateTitleDefaults(ValidationResult result, ChartModel chartModel) {
        // 由于visible已设置为非空，不需要检查null值
        // chartModel.getTitle().isVisible() 总是返回boolean值
    }

    /**
     * 验证图例默认值
     */
    private void validateLegendDefaults(ValidationResult result, ChartModel chartModel) {
        // 由于visible已设置为非空，不需要检查null值
        // chartModel.getLegend().isVisible() 总是返回boolean值
        
        if (chartModel.getLegend().getPosition() == null) {
            result.addInfo("Legend position should have default value");
        }
    }

    /**
     * 验证图表类型映射一致性
     */
    private void validateChartTypeMappingConsistency(ValidationResult result, XNode originalXml, XNode rebuiltXml) {
        try {
            // 检查图表类型元素
            String[] chartTypes = {"c:barChart", "c:pieChart", "c:lineChart", "c:areaChart", 
                                 "c:scatterChart", "c:radarChart", "c:bubbleChart"};
            
            for (String chartType : chartTypes) {
                List<XNode> originalCharts = findElementsByPath(originalXml, "//" + chartType);
                List<XNode> rebuiltCharts = findElementsByPath(rebuiltXml, "//" + chartType);
                
                if (originalCharts.size() != rebuiltCharts.size()) {
                    result.addWarning("Chart type " + chartType + " count mismatch: original=" + 
                                    originalCharts.size() + ", rebuilt=" + rebuiltCharts.size());
                }
            }
            
        } catch (Exception e) {
            result.addWarning("Chart type mapping consistency validation failed: " + e.getMessage());
        }
    }

    /**
     * 验证命名空间一致性
     */
    private void validateNamespaceConsistency(ValidationResult result, XNode originalXml, XNode rebuiltXml) {
        try {
            // 检查必需的命名空间
            String[] requiredNamespaces = {"c", "a", "r"};
            
            for (String ns : requiredNamespaces) {
                String originalNs = originalXml.attrText("xmlns:" + ns);
                String rebuiltNs = rebuiltXml.attrText("xmlns:" + ns);
                
                if (originalNs != null && rebuiltNs == null) {
                    result.addError("Missing namespace in rebuilt XML: " + ns);
                } else if (originalNs != null && !originalNs.equals(rebuiltNs)) {
                    result.addWarning("Namespace mismatch for " + ns + ": original=" + 
                                    originalNs + ", rebuilt=" + rebuiltNs);
                }
            }
            
        } catch (Exception e) {
            result.addWarning("Namespace consistency validation failed: " + e.getMessage());
        }
    }

    /**
     * 验证元素层次结构一致性
     */
    private void validateElementHierarchyConsistency(ValidationResult result, XNode originalXml, XNode rebuiltXml) {
        try {
            // 检查基本结构
            validateBasicStructure(result, originalXml, rebuiltXml);
            
            // 检查图表元素结构
            validateChartStructure(result, originalXml, rebuiltXml);
            
        } catch (Exception e) {
            result.addWarning("Element hierarchy consistency validation failed: " + e.getMessage());
        }
    }

    /**
     * 验证基本结构
     */
    private void validateBasicStructure(ValidationResult result, XNode originalXml, XNode rebuiltXml) {
        // 检查根元素
        if (!originalXml.getTagName().equals(rebuiltXml.getTagName())) {
            result.addError("Root element mismatch: original=" + originalXml.getTagName() + 
                          ", rebuilt=" + rebuiltXml.getTagName());
        }
        
        // 检查chart元素
        XNode originalChart = originalXml.childByTag("c:chart");
        XNode rebuiltChart = rebuiltXml.childByTag("c:chart");
        
        if (originalChart != null && rebuiltChart == null) {
            result.addError("Missing c:chart element in rebuilt XML");
        } else if (originalChart == null && rebuiltChart != null) {
            result.addInfo("Added c:chart element in rebuilt XML");
        }
    }

    /**
     * 验证图表结构
     */
    private void validateChartStructure(ValidationResult result, XNode originalXml, XNode rebuiltXml) {
        XNode originalChart = originalXml.childByTag("c:chart");
        XNode rebuiltChart = rebuiltXml.childByTag("c:chart");
        
        if (originalChart != null && rebuiltChart != null) {
            // 检查plotArea
            XNode originalPlotArea = originalChart.childByTag("c:plotArea");
            XNode rebuiltPlotArea = rebuiltChart.childByTag("c:plotArea");
            
            if (originalPlotArea != null && rebuiltPlotArea == null) {
                result.addError("Missing c:plotArea element in rebuilt XML");
            }
        }
    }

    /**
     * 根据路径查找元素
     */
    private List<XNode> findElementsByPath(XNode root, String path) {
        List<XNode> elements = new ArrayList<>();
        try {
            // 简化的路径查找实现
            if (path.startsWith("//")) {
                String tagName = path.substring(2);
                findElementsByTagName(root, tagName, elements);
            }
        } catch (Exception e) {
            LOG.warn("Failed to find elements by path: " + path, e);
        }
        return elements;
    }

    /**
     * 递归查找指定标签名的元素
     */
    private void findElementsByTagName(XNode node, String tagName, List<XNode> result) {
        if (node.getTagName().equals(tagName)) {
            result.add(node);
        }
        
        for (XNode child : node.getChildren()) {
            findElementsByTagName(child, tagName, result);
        }
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> info = new ArrayList<>();

        public void addError(String message) {
            errors.add(message);
            LOG.error("Validation error: {}", message);
        }

        public void addWarning(String message) {
            warnings.add(message);
            LOG.warn("Validation warning: {}", message);
        }

        public void addInfo(String message) {
            info.add(message);
            LOG.info("Validation info: {}", message);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public List<String> getInfo() {
            return info;
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult{");
            sb.append("errors=").append(errors.size());
            sb.append(", warnings=").append(warnings.size());
            sb.append(", info=").append(info.size());
            sb.append("}");
            return sb.toString();
        }
    }
}