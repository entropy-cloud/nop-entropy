# JFreeChart 代码示例

本文档提供了使用 JFreeChart 库创建多种常见图表的完整 Java 代码示例。每个示例都是一个可独立运行的 Java 类，并包含了详细的注释，解释了如何构建数据、创建图表以及定制样式。

## 目录

1.  [准备工作](#1-准备工作)
2.  [图表示例](#2-图表示例)
    *   [2.1 簇状柱形图 (Clustered Bar Chart)](#21-簇状柱形图-clustered-bar-chart)
    *   [2.2 带间隔的圆环图 (Spaced Donut Chart)](#22-带间隔的圆环图-spaced-donut-chart)
    *   [2.3 填充雷达图 (Filled Radar Chart)](#23-填充雷达图-filled-radar-chart)
    *   [2.4 百分比堆积面积图 (100% Stacked Area Chart)](#24-百分比堆积面积图-100-stacked-area-chart)
    *   [2.5 带数据点的折线图 (Line Chart with Markers)](#25-带数据点的折线图-line-chart-with-markers)
    *   [2.6 气泡图 (Bubble Chart)](#26-气泡图-bubble-chart)
    *   [2.7 高级样式：自定义填充与数据点样式](#27-高级样式自定义填充与数据点样式)
3.  [核心概念总结](#3-核心概念总结)

---

## 1. 准备工作

在运行这些示例之前，请确保您的 Java 项目中已经添加了 JFreeChart 的依赖。

**Maven 依赖 (pom.xml):**

```xml
<dependency>
    <groupId>org.jfree</groupId>
    <artifactId>jfreechart</artifactId>
    <version>1.5.6</version> <!-- 或其他稳定版本 -->
</dependency>
```

所有示例代码都基于标准的 Java Swing `ApplicationFrame`，可以直接运行 `main` 方法查看效果。

---

## 2. 图表示例

### 2.1 簇状柱形图 (Clustered Bar Chart)

**功能解释**:
簇状柱形图用于比较多个系列在同一分类下的数值。通过为同一个 `category` 添加不同 `series` 的数据实现分组效果。

**效果图描述**:
一个垂直的柱形图，X 轴为产品分类，Y 轴为销量。每个产品分类下有多个并排的、不同颜色的柱子，代表不同的销售渠道或时间。

```java
// ClusteredBarChart.java
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.*;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.*;
import org.jfree.ui.*;

import java.awt.*;

public class ClusteredBarChart extends ApplicationFrame {
    public ClusteredBarChart(String title) {
        super(title);
        CategoryDataset dataset = createDataset();
        JFreeChart chart = createChart(dataset);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
        setContentPane(chartPanel);
    }

    private CategoryDataset createDataset() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series1 = "线上", series2 = "线下";
        String cat1 = "Laptop", cat2 = "Desktop", cat3 = "Tablet";
        dataset.addValue(1200, series1, cat1);
        dataset.addValue(1350, series2, cat1);
        dataset.addValue(800, series1, cat2);
        dataset.addValue(780, series2, cat2);
        dataset.addValue(300, series1, cat3);
        dataset.addValue(420, series2, cat3);
        return dataset;
    }

    private JFreeChart createChart(final CategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createBarChart(
                "产品销量对比", "产品", "销量", dataset,
                PlotOrientation.VERTICAL, true, true, false);

        // --- 样式定制 ---
        chart.setBackgroundPaint(Color.WHITE);
        chart.setTitle(new TextTitle("产品销量对比", new Font("宋体", Font.BOLD, 24)));

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.setOutlineVisible(false);

        // 设置 Y 轴范围
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setRange(0.0, 1600.0);

        // 设置渲染器
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        // **关键: 去除柱子渐变效果，使其变为纯色**
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setSeriesPaint(0, new Color(68, 114, 196));
        renderer.setSeriesPaint(1, new Color(237, 125, 49));

        return chart;
    }

    public static void main(String[] args) {
        ClusteredBarChart demo = new ClusteredBarChart("簇状柱形图");
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);
    }
}
```

### 2.2 带间隔的圆环图 (Spaced Donut Chart)

**功能解释**:
圆环图是饼图的变种，中间有一个空洞。通过 `RingPlot` 的 `setSeparatorsVisible(true)` 可以轻松实现各扇区间的白色间隔效果。

**效果图描述**:
一个彩色的圆环，被分成多个扇区，每个扇区代表一类产品的销售占比。扇区之间有明显的白色缝隙。图表底部有图例说明。

```java
// SpacedDonutChart.java
import org.jfree.chart.*;
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.title.*;
import org.jfree.data.general.*;
import org.jfree.ui.*;

import java.awt.*;

public class SpacedDonutChart extends ApplicationFrame {
    public SpacedDonutChart(String title) {
        super(title);
        PieDataset dataset = createDataset();
        JFreeChart chart = createChart(dataset);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
        setContentPane(chartPanel);
    }

    private PieDataset createDataset() {
        DefaultPieDataset dataset = new DefaultPieDataset();
        dataset.setValue("Laptop", 38.0);
        dataset.setValue("Desktop", 17.0);
        dataset.setValue("Tablet", 10.0);
        dataset.setValue("Smartphone", 23.0);
        dataset.setValue("Monitor", 12.0);
        return dataset;
    }

    private JFreeChart createChart(final PieDataset dataset) {
        JFreeChart chart = ChartFactory.createRingChart(
                "产品销售占比", dataset, true, true, false);

        // --- 样式定制 ---
        chart.setBackgroundPaint(Color.WHITE);
        chart.setTitle(new TextTitle("产品销售占比", new Font("宋体", Font.BOLD, 24)));
        chart.getLegend().setPosition(RectangleEdge.BOTTOM); // 图例放底部

        RingPlot plot = (RingPlot) chart.getPlot();
        plot.setBackgroundPaint(null);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelGenerator(null); // 不在扇区上显示标签

        // **关键: 设置分区之间的间隔**
        plot.setSeparatorsVisible(true);
        plot.setSeparatorPaint(Color.WHITE);
        plot.setSeparatorStroke(new BasicStroke(2.0f));

        // 自定义颜色
        plot.setSectionPaint("Laptop", new Color(68, 114, 196));
        plot.setSectionPaint("Desktop", new Color(237, 125, 49));
        plot.setSectionPaint("Tablet", new Color(165, 165, 165));
        plot.setSectionPaint("Smartphone", new Color(255, 192, 0));
        plot.setSectionPaint("Monitor", new Color(91, 155, 213));

        return chart;
    }

    public static void main(String[] args) {
        SpacedDonutChart demo = new SpacedDonutChart("圆环图");
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);
    }
}
```

### 2.3 填充雷达图 (Filled Radar Chart)

**功能解释**:
雷达图（在 JFreeChart 中称 `SpiderWebPlot`）用于展示多个维度上的数值。通过设置 `SeriesPaint` 可以实现数据区域的颜色填充。

**效果图描述**:
一个多边形的蛛网图，每个顶点代表一个评估维度。一个灰色的、不规则的多边形填充区域连接了各个维度上的数据点，直观地展示了综合能力。

```java
// FilledRadarChart.java
import org.jfree.chart.*;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.*;
import org.jfree.ui.*;

import java.awt.*;

public class FilledRadarChart extends ApplicationFrame {
    public FilledRadarChart(String title) {
        super(title);
        DefaultCategoryDataset dataset = createDataset();
        JFreeChart chart = createChart(dataset);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
        setContentPane(chartPanel);
    }

    private DefaultCategoryDataset createDataset() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String series = "综合能力";
        String cat1 = "攻击", cat2 = "防御", cat3 = "速度", cat4 = "技巧", cat5 = "视野";
        dataset.addValue(90.0, series, cat1);
        dataset.addValue(75.0, series, cat2);
        dataset.addValue(85.0, series, cat3);
        dataset.addValue(60.0, series, cat4);
        dataset.addValue(95.0, series, cat5);
        return dataset;
    }

    private JFreeChart createChart(final DefaultCategoryDataset dataset) {
        SpiderWebPlot plot = new SpiderWebPlot(dataset);
		JFreeChart chart = new JFreeChart(
				"能力评估雷达图",
				new Font("宋体", Font.BOLD, 24),
				plot,
				false // 是否显示图例
		);

        // --- 样式定制 ---
        chart.setBackgroundPaint(Color.WHITE);
        chart.setTitle(new TextTitle("能力评估雷达图", new Font("宋体", Font.BOLD, 24)));

        SpiderWebPlot plot = (SpiderWebPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setWebPaint(Color.LIGHT_GRAY); // 网格线颜色
        plot.setMaxValue(100.0); // 设置最大值

        // **关键: 设置数据区域的填充颜色**
        plot.setSeriesPaint(0, new Color(128, 128, 128, 180)); // 半透明灰色填充

        return chart;
    }

    public static void main(String[] args) {
        FilledRadarChart demo = new FilledRadarChart("雷达图");
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);
    }
}
```

### 2.4 百分比堆积面积图 (100% Stacked Area Chart)

**功能解释**:
此图用于展示多个系列在不同分类下的构成比例随时间或分类的变化。通过 `StackedAreaRenderer` 的 `setRenderAsPercentages(true)` 自动计算并渲染百分比。

**效果图描述**:
一个填满整个绘图区的彩色堆积区域图，Y 轴为 0% 到 100%。每个颜色层代表一个系列，其厚度表示该系列在当前分类下所占的百分比。

```java
// StackedPercentageAreaChart.java
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.*;
import org.jfree.ui.*;

import java.awt.*;
import java.text.DecimalFormat;

public class StackedPercentageAreaChart extends ApplicationFrame {
    public StackedPercentageAreaChart(String title) {
        super(title);
        CategoryDataset dataset = createDataset();
        JFreeChart chart = createChart(dataset);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
        setContentPane(chartPanel);
    }

    private CategoryDataset createDataset() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String s1 = "渠道A", s2 = "渠道B", s3 = "渠道C";
        String c1 = "Q1", c2 = "Q2", c3 = "Q3", c4 = "Q4";
        dataset.addValue(30, s1, c1); dataset.addValue(40, s2, c1); dataset.addValue(30, s3, c1);
        dataset.addValue(25, s1, c2); dataset.addValue(45, s2, c2); dataset.addValue(30, s3, c2);
        dataset.addValue(35, s1, c3); dataset.addValue(30, s2, c3); dataset.addValue(35, s3, c3);
        dataset.addValue(40, s1, c4); dataset.addValue(35, s2, c4); dataset.addValue(25, s3, c4);
        return dataset;
    }

    private JFreeChart createChart(final CategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createStackedAreaChart(
                "季度销售渠道占比", "季度", "占比", dataset,
                PlotOrientation.VERTICAL, true, true, false);

        // --- 样式定制 ---
        chart.setBackgroundPaint(Color.WHITE);
        chart.setTitle(new TextTitle("季度销售渠道占比", new Font("宋体", Font.BOLD, 24)));

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // **关键: 设置渲染器为百分比模式**
        StackedAreaRenderer renderer = (StackedAreaRenderer) plot.getRenderer();
        renderer.setRenderAsPercentages(true);
        renderer.setSeriesPaint(0, new Color(68, 114, 196));
        renderer.setSeriesPaint(1, new Color(237, 125, 49));
        renderer.setSeriesPaint(2, new Color(165, 165, 165));

        // **关键: 将 Y 轴标签格式化为百分比**
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setNumberFormatOverride(new DecimalFormat("0%"));
        rangeAxis.setRange(0.0, 1.0); // 百分比模式下，值域是 0.0 到 1.0

        return chart;
    }

    public static void main(String[] args) {
        StackedPercentageAreaChart demo = new StackedPercentageAreaChart("百分比堆积面积图");
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);
    }
}
```

### 2.5 带数据点的折线图 (Line Chart with Markers)

**功能解释**:
折线图用于显示数据随连续变量（如时间或数值）变化的趋势。使用 `XYDataset` 和 `XYLineAndShapeRenderer` 可以同时显示连接线和数据点标记。

**效果图描述**:
一个二维坐标系图表，包含一条或多条折线。每个数据点的位置由一个可见的形状（如圆形或方形）标记。

```java
// LineChartWithMarkers.java
import org.jfree.chart.*;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.*;
import org.jfree.ui.*;

import java.awt.*;

public class LineChartWithMarkers extends ApplicationFrame {
    public LineChartWithMarkers(String title) {
        super(title);
        XYDataset dataset = createDataset();
        JFreeChart chart = createChart(dataset);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
        setContentPane(chartPanel);
    }

    private XYDataset createDataset() {
        XYSeries series1 = new XYSeries("CPU 使用率");
        series1.add(1.0, 45.0); series1.add(2.0, 55.0); series1.add(3.0, 35.0);
        series1.add(4.0, 68.0); series1.add(5.0, 75.0); series1.add(6.0, 50.0);

        XYSeries series2 = new XYSeries("内存使用率");
        series2.add(1.0, 60.0); series2.add(2.0, 65.0); series2.add(3.0, 62.0);
        series2.add(4.0, 70.0); series2.add(5.0, 72.0); series2.add(6.0, 68.0);

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(series1);
        dataset.addSeries(series2);
        return dataset;
    }

    private JFreeChart createChart(final XYDataset dataset) {
        JFreeChart chart = ChartFactory.createXYLineChart(
                "系统资源监控", "时间 (分钟)", "使用率 (%)", dataset,
                PlotOrientation.VERTICAL, true, true, false);

        // --- 样式定制 ---
        chart.setBackgroundPaint(Color.WHITE);
        chart.setTitle(new TextTitle("系统资源监控", new Font("宋体", Font.BOLD, 24)));

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.setDomainGridlinesVisible(false);
        plot.setOutlineVisible(false);

        // **关键: 获取 XYLineAndShapeRenderer**
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        // 默认就会显示线条和形状，这里可以进一步定制
        renderer.setSeriesPaint(0, new Color(68, 114, 196));
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesPaint(1, new Color(237, 125, 49));
        renderer.setSeriesShapesVisible(1, true);

        // 设置 Y 轴范围
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setRange(0.0, 100.0);

        return chart;
    }

    public static void main(String[] args) {
        LineChartWithMarkers demo = new LineChartWithMarkers("带数据点的折线图");
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);
    }
}
```

### 2.6 气泡图 (Bubble Chart)

**功能解释**:
气泡图是一种三维散点图，其中 X、Y 坐标决定了气泡的位置，而第三个维度的值（Z）决定了气泡的大小。使用 `XYZDataset` 和 `XYBubbleRenderer` 实现。

**效果图描述**:
一个散点图，但每个点被一个大小不一的气泡代替。气泡的大小反映了第三个维度的数值，可以直观地比较三个维度的数据。

```java
// BubbleChart.java
import org.jfree.chart.*;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYBubbleRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.xy.*;
import org.jfree.ui.*;

import java.awt.*;

public class BubbleChart extends ApplicationFrame {
    public BubbleChart(String title) {
        super(title);
        XYZDataset dataset = createDataset();
        JFreeChart chart = createChart(dataset);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
        setContentPane(chartPanel);
    }

    private XYZDataset createDataset() {
        DefaultXYZDataset dataset = new DefaultXYZDataset();
        // data: { {x-values}, {y-values}, {z-values} }
        double[][] data = new double[][]{
            {20, 35, 40, 55, 60}, // X: 产品价格
            {1500, 4500, 2200, 6000, 3000}, // Y: 产品销量
            {100, 350, 200, 500, 280}  // Z: 广告投入 (气泡大小)
        };
        dataset.addSeries("产品分析", data);
        return dataset;
    }

    private JFreeChart createChart(final XYZDataset dataset) {
        JFreeChart chart = ChartFactory.createBubbleChart(
                "产品性价比与投入分析", "价格", "销量", dataset,
                PlotOrientation.VERTICAL, true, true, false);

        // --- 样式定制 ---
        chart.setBackgroundPaint(Color.WHITE);
        chart.setTitle(new TextTitle("产品性价比与投入分析", new Font("宋体", Font.BOLD, 24)));

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // **关键: 设置 Bubble Renderer**
        XYBubbleRenderer renderer = (XYBubbleRenderer) plot.getRenderer();
        // 设置气泡填充色（半透明）
        renderer.setSeriesPaint(0, new Color(68, 114, 196, 150));

        return chart;
    }

    public static void main(String[] args) {
        BubbleChart demo = new BubbleChart("气泡图");
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);
    }
}
```

### 2.7 高级样式：自定义填充与数据点样式

**功能解释**:
JFreeChart 允许通过 `java.awt.Paint` 接口实现复杂的填充，如渐变 (`GradientPaint`) 和图案 (`TexturePaint`)。通过继承 `Renderer` 并重写 `getItemPaint()` 方法，可以实现对单个数据点的样式进行独立控制。

**效果图描述**:
一个簇状柱形图，其中一个系列是蓝白渐变色，另一个系列是橙色条纹图案。在渐变色系列中，有一个柱子被特殊标记为纯红色，以突出显示。

```java
// AdvancedStyleChart.java
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.*;
import org.jfree.data.category.*;
import org.jfree.ui.*;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class AdvancedStyleChart extends ApplicationFrame {
    public AdvancedStyleChart(String title) {
        super(title);
        CategoryDataset dataset = createDataset();
        JFreeChart chart = createChart(dataset);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
        setContentPane(chartPanel);
    }

    private CategoryDataset createDataset() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(1200, "渐变系列", "Laptop");
        dataset.addValue(800, "渐变系列", "Desktop");
        dataset.addValue(300, "渐变系列", "Tablet (突出)"); // 特殊数据点
        dataset.addValue(1350, "图案系列", "Laptop");
        dataset.addValue(780, "图案系列", "Desktop");
        dataset.addValue(420, "图案系列", "Tablet (突出)");
        return dataset;
    }

    // 自定义 Renderer
    private static class CustomRenderer extends BarRenderer {
        private final Paint gradientPaint = new GradientPaint(0, 0, new Color(68, 114, 196), 0, 300, Color.WHITE);
        private final Paint patternPaint = createStripePaint(new Color(237, 125, 49), Color.WHITE);

        @Override
        public Paint getItemPaint(int row, int column) {
            CategoryDataset dataset = getPlot().getDataset();
            String seriesKey = (String) dataset.getRowKey(row);
            String categoryKey = (String) dataset.getColumnKey(column);

            // **关键: 对单个数据点进行特殊处理**
            if (Objects.equals(categoryKey, "Tablet (突出)") && Objects.equals(seriesKey, "渐变系列")) {
                return Color.RED;
            }

            // 根据系列名称返回不同的 Paint 对象
            if (Objects.equals(seriesKey, "渐变系列")) {
                return gradientPaint;
            } else if (Objects.equals(seriesKey, "图案系列")) {
                return patternPaint;
            }
            return super.getItemPaint(row, column);
        }

        private Paint createStripePaint(Color c1, Color c2) {
            BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(c2);
            g2.fillRect(0, 0, 10, 10);
            g2.setColor(c1);
            g2.drawLine(0, 10, 10, 0);
            g2.dispose();
            return new TexturePaint(img, new Rectangle2D.Double(0, 0, 10, 10));
        }
    }

    private JFreeChart createChart(final CategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createBarChart("高级样式", null, null, dataset);

        chart.setBackgroundPaint(Color.WHITE);
        chart.getLegend().setVisible(false); // 隐藏图例以突出视觉效果

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // **关键: 应用我们的自定义 Renderer**
        CustomRenderer renderer = new CustomRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        plot.setRenderer(renderer);

        return chart;
    }

    public static void main(String[] args) {
        AdvancedStyleChart demo = new AdvancedStyleChart("高级样式图表");
        demo.pack();
        RefineryUtilities.centerFrameOnScreen(demo);
        demo.setVisible(true);
    }
}
```

---

## 3. 核心概念总结

*   **`JFreeChart`**: 顶层图表对象，包含标题、图例和绘图区。
*   **`Plot`**: 绘图区，是图表的真正核心，负责绘制数据、坐标轴和网格线。主要有 `CategoryPlot`（用于分类数据）和 `XYPlot`（用于数值数据）。
*   **`Dataset`**: 数据集，为图表提供数据。不同图表需要不同类型的数据集，如 `CategoryDataset`, `PieDataset`, `XYDataset`, `XYZDataset`。
*   **`Renderer`**: 渲染器，负责将数据集中的数据显示在 `Plot` 上。通过定制 `Renderer`，可以改变图表的视觉表现，如颜色、形状、填充等。
*   **`Axis`**: 坐标轴，如 `CategoryAxis` 和 `NumberAxis`，负责显示刻度和标签。
*   **`ChartFactory`**: 一个便捷的工厂类，提供了快速创建各种标准图表的方法。

通过组合和定制这些核心组件，JFreeChart 几乎可以实现任何你需要的图表样式。


## 4. 补充信息与实用技巧

本章节提供了一些在使用 JFreeChart 过程中常见的附加问题和实用技巧，帮助您更好地解决实际开发中遇到的细节问题。

### 4.1 中文乱码问题

JFreeChart 默认使用的字体可能不包含中文字符，导致图表中的中文（如标题、坐标轴标签、图例）显示为方框 `□`。

**解决方案**：为所有需要显示中文的元素明确指定支持中文的字体，如“宋体”、“微软雅黑”等。

```java
Font chineseFont = new Font("宋体", Font.PLAIN, 12);

// 设置标题字体
chart.getTitle().setFont(chineseFont);

// 设置图例字体
chart.getLegend().setItemFont(chineseFont);

// 设置坐标轴标签字体
CategoryPlot plot = chart.getCategoryPlot();
plot.getDomainAxis().setLabelFont(chineseFont); // X轴标签
plot.getRangeAxis().setLabelFont(chineseFont);  // Y轴标签

// 设置坐标轴刻度字体
plot.getDomainAxis().setTickLabelFont(chineseFont); // X轴刻度
plot.getRangeAxis().setTickLabelFont(chineseFont);  // Y轴刻度
```

### 4.2 导出图表为图片文件

在 Web 应用或报表生成等非 GUI 场景下，需要将图表保存为图片文件。`ChartUtils` 类（在旧版本中是 `ChartUtilities`）提供了便捷的方法。

**解决方案**: 使用 `ChartUtils.saveChartAsPNG()` 或 `saveChartAsJPEG()`。

```java
import org.jfree.chart.ChartUtils;
import java.io.File;
import java.io.IOException;

// ... (创建 JFreeChart 对象 chart) ...

try {
    // 保存为 PNG 文件
    ChartUtils.saveChartAsPNG(
        new File("my_chart.png"), // 文件路径
        chart,                    // JFreeChart 对象
        800,                      // 宽度
        600                       // 高度
    );
    System.out.println("图表已成功保存为 my_chart.png");
} catch (IOException e) {
    e.printStackTrace();
}
```

### 4.3 去除 3D 效果和阴影

JFreeChart 的某些图表（如饼图、柱状图）默认带有轻微的 3D 阴影效果。为了获得更现代、扁平化的外观，可以禁用它们。

**解决方案**:

*   **饼图/环形图**: `plot.setShadowPaint(null);`
*   **柱状/条形图**: `renderer.setShadowVisible(false);`
*   **柱状/条形图（去除渐变）**: `renderer.setBarPainter(new StandardBarPainter());` (如示例 2.1 所示)

### 4.4 自定义 Tooltip (提示框)

当鼠标悬停在数据点上时，默认的 Tooltip 可能信息不足。您可以自定义其显示内容。

**解决方案**: 为 `Renderer` 设置一个自定义的 `ToolTipGenerator`。

```java
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import java.text.NumberFormat;

// --- 对于 CategoryPlot (如柱状图) ---
// 格式: "{0}"=系列名, "{1}"=分类名, "{2}"=数值
String toolTipFormat = "在 {1} 的 {0} 销量为: {2}";
StandardCategoryToolTipGenerator toolTipGenerator = new StandardCategoryToolTipGenerator(
    toolTipFormat, NumberFormat.getInstance()
);
renderer.setDefaultToolTipGenerator(toolTipGenerator);

// --- 对于 XYPlot (如折线图) ---
// 格式: "{0}"=系列名, "{1}"=X值, "{2}"=Y值
StandardXYToolTipGenerator xyToolTipGenerator = new StandardXYToolTipGenerator(
    StandardXYToolTipGenerator.DEFAULT_TOOL_TIP_FORMAT,
    NumberFormat.getInstance(), // X值的格式化
    NumberFormat.getCurrencyInstance() // Y值的格式化 (例如显示为货币)
);
xyRenderer.setDefaultToolTipGenerator(xyToolTipGenerator);
```

### 4.5 在无头环境 (Headless) 中运行

在没有图形界面的服务器（如 Linux 服务器）上运行 JFreeChart 时，直接创建图表可能会抛出 `HeadlessException`。

**解决方案**: 在启动 Java 程序时，设置 `java.awt.headless` 系统属性为 `true`。

**命令行启动**:

```bash
java -Djava.awt.headless=true -jar your-application.jar
```

**在代码中设置 (不推荐，但有时有用)**:

```java
System.setProperty("java.awt.headless", "true");
// ... 之后的 JFreeChart 代码 ...
```
