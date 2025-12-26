package io.nop.ooxml.xlsx.chart;

import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * ChartStyleIntegrationValidator - 图表样式集成验证器
 * 验证所有子解析器与共享样式提供者的集成工作
 * 测试颜色处理在所有图表元素中的正确性
 */
public class ChartStyleIntegrationValidator {
    private static final Logger LOG = LoggerFactory.getLogger(ChartStyleIntegrationValidator.class);
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private boolean success;
        private List<String> errors;
        private List<String> warnings;
        
        public ValidationResult() {
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
            this.success = true;
        }
        
        public void addError(String error) {
            this.errors.add(error);
            this.success = false;
        }
        
        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }
    
    /**
     * 执行完整的集成验证
     * @param styleProvider 样式提供者
     * @return 验证结果
     */
    public static ValidationResult validateIntegration(IChartStyleProvider styleProvider) {
        ValidationResult result = new ValidationResult();
        
        LOG.info("Starting chart style integration validation");
        
        try {
            // 1. 验证样式提供者基本功能
            validateStyleProvider(styleProvider, result);
            
            // 2. 验证颜色处理功能
            validateColorProcessing(styleProvider, result);
            
            // 3. 验证解析器集成
            validateParserIntegration(styleProvider, result);
            
            // 4. 验证主题文件支持
            validateThemeFileSupport(styleProvider, result);
            
            // 5. 验证错误处理
            validateErrorHandling(styleProvider, result);
            
            if (result.isSuccess()) {
                LOG.info("Chart style integration validation completed successfully");
            } else {
                LOG.warn("Chart style integration validation completed with {} errors", result.getErrors().size());
            }
            
        } catch (Exception e) {
            result.addError("Integration validation failed with exception: " + e.getMessage());
            LOG.error("Integration validation failed", e);
        }
        
        return result;
    }
    
    /**
     * 验证样式提供者基本功能
     */
    private static void validateStyleProvider(IChartStyleProvider styleProvider, ValidationResult result) {
        try {
            // 测试主题颜色获取
            String accent1 = styleProvider.getThemeColor("accent1");
            if (accent1 == null) {
                result.addError("Theme color 'accent1' should not be null");
            } else if (!accent1.startsWith("#")) {
                result.addError("Theme color should be in hex format, got: " + accent1);
            }
            
            // 测试样本中关键的颜色
            String tx1 = styleProvider.getThemeColor("tx1");
            if (tx1 == null) {
                result.addError("Theme color 'tx1' should not be null (critical for samples)");
            }
            
            String bg1 = styleProvider.getThemeColor("bg1");
            if (bg1 == null) {
                result.addError("Theme color 'bg1' should not be null (critical for samples)");
            }
            
            // 测试颜色解析
            String resolvedColor = styleProvider.resolveColor("#FF0000");
            if (!"#FF0000".equals(resolvedColor)) {
                result.addError("Color resolution failed for direct hex color");
            }
            
            // 测试默认样式获取
            ChartShapeStyleModel titleStyle = styleProvider.getDefaultStyle("title");
            if (titleStyle == null) {
                result.addError("Default title style should not be null");
            }
            
            LOG.debug("Style provider basic functionality validation completed");
            
        } catch (Exception e) {
            result.addError("Style provider validation failed: " + e.getMessage());
        }
    }
    
    /**
     * 验证颜色处理功能
     */
    private static void validateColorProcessing(IChartStyleProvider styleProvider, ValidationResult result) {
        try {
            // 创建测试用的颜色修改XML节点
            XNode testColorNode = createTestColorNode();
            
            // 测试颜色修改应用
            String baseColor = "#4472C4"; // accent1 默认颜色
            String modifiedColor = styleProvider.applyColorModifications(baseColor, testColorNode);
            
            if (modifiedColor == null) {
                result.addError("Color modification should not return null");
            } else if (modifiedColor.equals(baseColor)) {
                result.addWarning("Color modification did not change the base color (may be expected)");
            }
            
            // 测试空节点处理
            String nullNodeResult = styleProvider.applyColorModifications(baseColor, null);
            if (!baseColor.equals(nullNodeResult)) {
                result.addError("Color modification with null node should return original color");
            }
            
            // 测试主题颜色修改
            String themeColor = styleProvider.getThemeColor("accent2");
            String modifiedThemeColor = styleProvider.applyColorModifications(themeColor, testColorNode);
            if (modifiedThemeColor == null) {
                result.addError("Theme color modification should not return null");
            }
            
            LOG.debug("Color processing validation completed");
            
        } catch (Exception e) {
            result.addError("Color processing validation failed: " + e.getMessage());
        }
    }
    
    /**
     * 验证解析器集成
     */
    private static void validateParserIntegration(IChartStyleProvider styleProvider, ValidationResult result) {
        try {
            // 测试形状样式解析器
            XNode testShapeNode = createTestShapeStyleNode();
            ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(testShapeNode, styleProvider);
            
            if (shapeStyle == null) {
                result.addError("Shape style parser should not return null for valid input");
            } else {
                if (shapeStyle.getFill() == null) {
                    result.addWarning("Shape style fill is null (may be expected for some cases)");
                }
            }
            
            // 测试文本样式解析器
            XNode testTextNode = createTestTextStyleNode();
            ChartTextStyleModel textStyle = ChartTextStyleParser.INSTANCE.parseTextStyle(testTextNode, styleProvider);
            
            if (textStyle == null) {
                result.addError("Text style parser should not return null for valid input");
            } else {
                if (textStyle.getFont() == null) {
                    result.addWarning("Text style font is null (may be expected for some cases)");
                }
            }
            
            // 测试空输入处理
            ChartShapeStyleModel nullShapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(null, styleProvider);
            if (nullShapeStyle != null) {
                result.addWarning("Shape style parser returned non-null for null input (graceful handling)");
            }
            
            ChartTextStyleModel nullTextStyle = ChartTextStyleParser.INSTANCE.parseTextStyle(null, styleProvider);
            if (nullTextStyle != null) {
                result.addWarning("Text style parser returned non-null for null input (graceful handling)");
            }
            
            LOG.debug("Parser integration validation completed");
            
        } catch (Exception e) {
            result.addError("Parser integration validation failed: " + e.getMessage());
        }
    }
    
    /**
     * 验证主题文件支持
     */
    private static void validateThemeFileSupport(IChartStyleProvider styleProvider, ValidationResult result) {
        try {
            if (styleProvider instanceof DefaultChartStyleProvider) {
                DefaultChartStyleProvider defaultProvider = (DefaultChartStyleProvider) styleProvider;
                
                // 测试主题文件加载（使用空节点）
                defaultProvider.loadThemeFiles(null, null);
                
                // 验证加载后颜色仍然可用
                String colorAfterLoad = defaultProvider.getThemeColor("accent1");
                if (colorAfterLoad == null) {
                    result.addError("Theme colors should be available after theme file loading");
                }
                
                // 测试重复加载
                defaultProvider.loadThemeFiles(null, null);
                String colorAfterReload = defaultProvider.getThemeColor("accent1");
                if (!colorAfterLoad.equals(colorAfterReload)) {
                    result.addError("Theme colors should be consistent after repeated loading");
                }
                
                LOG.debug("Theme file support validation completed");
            } else {
                result.addWarning("Style provider is not DefaultChartStyleProvider, skipping theme file tests");
            }
            
        } catch (Exception e) {
            result.addError("Theme file support validation failed: " + e.getMessage());
        }
    }
    
    /**
     * 验证错误处理
     */
    private static void validateErrorHandling(IChartStyleProvider styleProvider, ValidationResult result) {
        try {
            // 测试无效颜色名称
            String invalidColor = styleProvider.getThemeColor("invalid_color_name");
            if (invalidColor == null) {
                result.addError("Invalid theme color should return default color, not null");
            }
            
            // 测试无效颜色修改
            String errorColor = styleProvider.applyColorModifications("invalid_color", null);
            if (errorColor == null) {
                result.addError("Color modification with invalid input should return fallback, not null");
            }
            
            // 测试空字符串处理
            String emptyColor = styleProvider.getThemeColor("");
            if (emptyColor == null) {
                result.addError("Empty theme color name should return default color, not null");
            }
            
            String nullColor = styleProvider.getThemeColor(null);
            if (nullColor != null) {
                result.addWarning("Null theme color name returned non-null result: " + nullColor);
            }
            
            LOG.debug("Error handling validation completed");
            
        } catch (Exception e) {
            result.addError("Error handling validation failed: " + e.getMessage());
        }
    }
    
    /**
     * 创建测试用的颜色节点
     */
    private static XNode createTestColorNode() {
        // 创建一个简单的测试节点，模拟OOXML颜色修改结构
        XNode colorNode = XNode.make("a:schemeClr");
        colorNode.setAttr("val", "accent1");
        
        // 添加亮度调制（样本中最常见的修改）
        XNode lumModNode = XNode.make("a:lumMod");
        lumModNode.setAttr("val", "65000"); // 65%
        colorNode.appendChild(lumModNode);
        
        XNode lumOffNode = XNode.make("a:lumOff");
        lumOffNode.setAttr("val", "35000"); // 35%
        colorNode.appendChild(lumOffNode);
        
        return colorNode;
    }
    
    /**
     * 创建测试用的形状样式节点
     */
    private static XNode createTestShapeStyleNode() {
        XNode spPrNode = XNode.make("c:spPr");
        
        // 添加纯色填充
        XNode solidFillNode = XNode.make("a:solidFill");
        XNode schemeClrNode = XNode.make("a:schemeClr");
        schemeClrNode.setAttr("val", "accent1");
        solidFillNode.appendChild(schemeClrNode);
        spPrNode.appendChild(solidFillNode);
        
        return spPrNode;
    }
    
    /**
     * 创建测试用的文本样式节点
     */
    private static XNode createTestTextStyleNode() {
        XNode txPrNode = XNode.make("c:txPr");
        
        // 添加段落属性
        XNode pPrNode = XNode.make("a:pPr");
        pPrNode.setAttr("algn", "ctr");
        txPrNode.appendChild(pPrNode);
        
        // 添加段落和运行属性
        XNode pNode = XNode.make("a:p");
        XNode rPrNode = XNode.make("a:rPr");
        rPrNode.setAttr("sz", "1200"); // 12pt
        rPrNode.setAttr("b", "1"); // bold
        
        // 添加字体颜色
        XNode solidFillNode = XNode.make("a:solidFill");
        XNode schemeClrNode = XNode.make("a:schemeClr");
        schemeClrNode.setAttr("val", "tx1");
        solidFillNode.appendChild(schemeClrNode);
        rPrNode.appendChild(solidFillNode);
        
        pNode.appendChild(rPrNode);
        txPrNode.appendChild(pNode);
        
        return txPrNode;
    }
    
    /**
     * 运行快速验证（用于开发时测试）
     * @return 验证是否成功
     */
    public static boolean quickValidation() {
        try {
            DefaultChartStyleProvider provider = new DefaultChartStyleProvider();
            ValidationResult result = validateIntegration(provider);
            
            if (!result.isSuccess()) {
                LOG.error("Quick validation failed with {} errors:", result.getErrors().size());
                for (String error : result.getErrors()) {
                    LOG.error("  - {}", error);
                }
                return false;
            }
            
            if (!result.getWarnings().isEmpty()) {
                LOG.warn("Quick validation completed with {} warnings:", result.getWarnings().size());
                for (String warning : result.getWarnings()) {
                    LOG.warn("  - {}", warning);
                }
            }
            
            return true;
        } catch (Exception e) {
            LOG.error("Quick validation failed with exception", e);
            return false;
        }
    }
}