package io.nop.ooxml.xlsx.chart;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.model.ChartManualLayoutModel;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.excel.chart.model.ChartTextStyleModel;
import io.nop.excel.chart.model.ChartTitleModel;

/**
 * ChartTitleParser - 标题解析器
 * 负责解析Excel图表中的标题配置
 */
public class ChartTitleParser {
    public static final ChartTitleParser INSTANCE = new ChartTitleParser();

    /**
     * 解析标题配置
     *
     * @param titleNode     标题节点
     * @param styleProvider 样式提供者
     * @return 标题模型对象
     */
    public ChartTitleModel parseTitle(XNode titleNode, IChartStyleProvider styleProvider) {
        if (titleNode == null) return null;

        ChartTitleModel title = new ChartTitleModel();

        // 解析可见性
        parseVisibility(title, titleNode);

        // 解析位置
        parsePosition(title, titleNode);

        // 解析覆盖选项
        parseOverlay(title, titleNode);

        // 解析文本内容
        parseTextContent(title, titleNode);

        // 解析手动布局
        ChartManualLayoutModel manualLayout = ChartManualLayoutParser.INSTANCE.parseManualLayout(titleNode);
        if (manualLayout != null) {
            title.setManualLayout(manualLayout);
        }

        // 解析形状样式
        parseShapeStyle(title, titleNode, styleProvider);

        // 解析文本样式
        parseTextStyle(title, titleNode, styleProvider);

        return title;
    }

    /**
     * 解析可见性
     */
    private void parseVisibility(ChartTitleModel title, XNode titleNode) {
        // 在OOXML中，标题可见性通过<c:autoTitleDeleted>元素控制
        // 如果titleNode存在，则标题默认可见
        title.setVisible(true);

        // 检查是否有自动删除设置
        String autoTitleDeleted = ChartPropertyHelper.getChildVal(titleNode.getParent(), "c:autoTitleDeleted");
        if (autoTitleDeleted != null && ChartPropertyHelper.convertToBoolean(autoTitleDeleted)) {
            title.setVisible(false);
        }
    }

    /**
     * 解析位置
     */
    private void parsePosition(ChartTitleModel title, XNode titleNode) {
        // 在OOXML中，标题位置通过手动布局或默认位置控制
        // 没有专门的position属性，暂时保留默认位置设置

        // 如果需要从子元素获取位置设置，可以添加相应的解析逻辑
        // 例如：从<c:layout>或<c:manualLayout>元素获取位置信息
    }

    /**
     * 解析覆盖选项
     */
    private void parseOverlay(ChartTitleModel title, XNode titleNode) {
        // 从子元素<c:overlay>获取覆盖设置
        Boolean overlayValue = ChartPropertyHelper.getChildBoolVal(titleNode, "c:overlay");
        if (overlayValue != null) {
            title.setOverlay(overlayValue);
        }
    }

    /**
     * 解析文本内容
     */
    private void parseTextContent(ChartTitleModel title, XNode titleNode) {
        // 在OOXML中，标题文本内容通过子元素获取，而不是直接属性
        // 解析富文本内容
        XNode txNode = titleNode.childByTag("c:tx");
        if (txNode != null) {
            parseRichTextContent(title, txNode);
        }
    }

    /**
     * 解析富文本内容
     */
    private void parseRichTextContent(ChartTitleModel title, XNode txNode) {
        // 使用ChartTextParser解析富文本 - 修正方法名
        String text = ChartTextParser.INSTANCE.extractText(txNode);
        if (!StringHelper.isEmpty(text)) {
            title.setText(text);
        }

        // 解析字符串引用
        String cellRef = ChartTextParser.INSTANCE.extractCellReferenceFromParent(txNode);
        if (cellRef != null) {
            title.setTextCellRef(cellRef);
        }
    }

    /**
     * 解析形状样式
     */
    private void parseShapeStyle(ChartTitleModel title, XNode titleNode, IChartStyleProvider styleProvider) {
        XNode spPrNode = titleNode.childByTag("c:spPr");
        if (spPrNode != null) {
            ChartShapeStyleModel shapeStyle = ChartShapeStyleParser.INSTANCE.parseShapeStyle(spPrNode, styleProvider);
            title.setShapeStyle(shapeStyle);
        }
    }

    /**
     * 解析文本样式
     */
    private void parseTextStyle(ChartTitleModel title, XNode titleNode, IChartStyleProvider styleProvider) {
        XNode txPrNode = titleNode.childByTag("c:txPr");
        if (txPrNode != null) {
            ChartTextStyleModel textStyle = ChartTextStyleParser.INSTANCE.parseTextStyle(txPrNode, styleProvider);
            title.setTextStyle(textStyle);
        }
    }

    /**
     * 解析标题覆盖选项（用于图例等元素）
     */
    public ChartTitleModel parseTitleOverlay(XNode titleNode, IChartStyleProvider styleProvider) {
        if (titleNode == null) return null;

        ChartTitleModel title = new ChartTitleModel();

        // 只解析覆盖相关的属性
        parseOverlay(title, titleNode);
        parseTextContent(title, titleNode);
        parseShapeStyle(title, titleNode, styleProvider);
        parseTextStyle(title, titleNode, styleProvider);

        return title;
    }
}