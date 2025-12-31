# JFreeChart 精细样式控制示例

JFreeChart 提供了非常丰富的样式控制选项。以下是详细的样式控制示例：

## 1. **基础样式控制类**

```java
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.title.*;
import org.jfree.data.xy.*;
import org.jfree.ui.*;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

public class DetailedStyleControlExample {

    public static void main(String[] args) {
        XYSeries series = createSampleData();
        XYSeriesCollection dataset = new XYSeriesCollection(series);

        // 创建图表
        JFreeChart chart = ChartFactory.createScatterPlot(
            "精细样式控制示例",
            "X轴",
            "Y轴",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );

        // 获取绘图区域
        XYPlot plot = (XYPlot) chart.getPlot();

        // 1. 设置图表整体样式
        customizeChartAppearance(chart);

        // 2. 自定义绘图区域
        customizePlotAppearance(plot);

        // 3. 自定义坐标轴
        customizeAxes(plot);

        // 4. 自定义数据点渲染
        customizeRenderer(plot);

        // 5. 自定义图例
        customizeLegend(chart);

        // 显示图表
        displayChart(chart);
    }

    private static XYSeries createSampleData() {
        XYSeries series = new XYSeries("数据系列");
        for (int i = 0; i < 20; i++) {
            double x = i;
            double y = Math.sin(i * 0.5) * 10 + Math.random() * 3;
            series.add(x, y);
        }
        return series;
    }

    private static void customizeChartAppearance(JFreeChart chart) {
        // 设置图表背景
        chart.setBackgroundPaint(new GradientPaint(
            0, 0, new Color(240, 240, 255),
            0, 100, new Color(220, 220, 240)
        ));

        // 设置边框
        chart.setBorderPaint(Color.DARK_GRAY);
        chart.setBorderStroke(new BasicStroke(2.0f));
        chart.setBorderVisible(true);

        // 设置标题字体和颜色
        TextTitle title = chart.getTitle();
        title.setPaint(new Color(0, 0, 100));
        title.setFont(new Font("微软雅黑", Font.BOLD, 20));

        // 添加副标题
        TextTitle subtitle = new TextTitle("这是一个带样式控制的散点图示例");
        subtitle.setFont(new Font("宋体", Font.PLAIN, 14));
        subtitle.setPaint(Color.GRAY);
        chart.addSubtitle(subtitle);
    }

    private static void customizePlotAppearance(XYPlot plot) {
        // 设置绘图区域背景
        plot.setBackgroundPaint(new GradientPaint(
            0, 0, new Color(255, 255, 240),
            100, 100, new Color(255, 250, 220)
        ));

        // 设置绘图区域边框
        plot.setOutlinePaint(Color.GRAY);
        plot.setOutlineStroke(new BasicStroke(1.5f));
        plot.setOutlineVisible(true);

        // 设置绘图区域背景图像（可选）
        // plot.setBackgroundImage(ImageIO.read(new File("background.png")));
        // plot.setBackgroundImageAlpha(0.1f); // 设置透明度

        // 设置网格线
        plot.setDomainGridlinePaint(new Color(200, 200, 200, 150));
        plot.setRangeGridlinePaint(new Color(200, 200, 200, 150));
        plot.setDomainGridlineStroke(new BasicStroke(0.5f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            1.0f, new float[]{5.0f, 5.0f}, 0.0f)); // 虚线
        plot.setRangeGridlineStroke(new BasicStroke(0.5f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            1.0f, new float[]{5.0f, 5.0f}, 0.0f));

        // 设置零基准线
        plot.setDomainZeroBaselineVisible(true);
        plot.setRangeZeroBaselineVisible(true);
        plot.setDomainZeroBaselinePaint(Color.RED);
        plot.setRangeZeroBaselinePaint(Color.RED);
        plot.setDomainZeroBaselineStroke(new BasicStroke(1.0f));
        plot.setRangeZeroBaselineStroke(new BasicStroke(1.0f));
    }

    private static void customizeAxes(XYPlot plot) {
        // 获取坐标轴
        NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();

        // 自定义X轴
        domainAxis.setLabel("自定义X轴标签");
        domainAxis.setLabelFont(new Font("微软雅黑", Font.BOLD, 14));
        domainAxis.setLabelPaint(new Color(0, 100, 0));

        // 设置X轴刻度标签
        domainAxis.setTickLabelFont(new Font("宋体", Font.PLAIN, 12));
        domainAxis.setTickLabelPaint(Color.DARK_GRAY);
        domainAxis.setTickMarkPaint(Color.BLACK);
        domainAxis.setTickMarkStroke(new BasicStroke(1.2f));
        domainAxis.setTickMarkInsideLength(4.0f);
        domainAxis.setTickMarkOutsideLength(8.0f);

        // 设置X轴范围
        domainAxis.setRange(0, 20);
        domainAxis.setAutoRange(true);
        domainAxis.setAutoRangeIncludesZero(true);

        // 设置X轴刻度线
        domainAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        // 自定义Y轴
        rangeAxis.setLabel("自定义Y轴标签");
        rangeAxis.setLabelFont(new Font("微软雅黑", Font.BOLD, 14));
        rangeAxis.setLabelPaint(new Color(100, 0, 0));
        rangeAxis.setLabelAngle(Math.PI / 2); // 旋转标签

        // 设置Y轴刻度标签格式
        rangeAxis.setNumberFormatOverride(new java.text.DecimalFormat("#,##0.00"));

        // 设置Y轴刻度间隔
        rangeAxis.setAutoTickUnitSelection(true);
        rangeAxis.setTickUnit(new NumberTickUnit(2.0));

        // 设置坐标轴线
        domainAxis.setAxisLinePaint(Color.BLUE);
        domainAxis.setAxisLineStroke(new BasicStroke(2.0f));
        rangeAxis.setAxisLinePaint(Color.BLUE);
        rangeAxis.setAxisLineStroke(new BasicStroke(2.0f));

        // 添加次要刻度线
        domainAxis.setMinorTickCount(4);
        domainAxis.setMinorTickMarksVisible(true);
        domainAxis.setMinorTickMarkPaint(Color.LIGHT_GRAY);
        domainAxis.setMinorTickMarkStroke(new BasicStroke(0.5f));

        rangeAxis.setMinorTickCount(2);
        rangeAxis.setMinorTickMarksVisible(true);
        rangeAxis.setMinorTickMarkPaint(Color.LIGHT_GRAY);
        rangeAxis.setMinorTickMarkStroke(new BasicStroke(0.5f));
    }

    private static void customizeRenderer(XYPlot plot) {
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(false, true);

        // 自定义数据点形状
        Shape[] shapes = {
            // 圆形
            new Ellipse2D.Double(-4, -4, 8, 8),
            // 方形
            new Rectangle(-4, -4, 8, 8),
            // 菱形
            new Polygon(new int[]{0, 4, 0, -4}, new int[]{-4, 0, 4, 0}, 4),
            // 三角形
            new Polygon(new int[]{0, 4, -4}, new int[]{-4, 4, 4}, 3),
            // 十字形
            createCrossShape(),
            // 星形
            createStarShape(5, 4, 2)
        };

        // 设置数据点形状
        renderer.setSeriesShape(0, shapes[0]);

        // 设置数据点填充颜色
        renderer.setSeriesPaint(0, new GradientPaint(
            0, 0, new Color(255, 100, 100, 200),
            10, 10, new Color(200, 50, 50, 150)
        ));

        // 设置数据点边框
        renderer.setSeriesOutlinePaint(0, Color.DARK_RED);
        renderer.setSeriesOutlineStroke(0, new BasicStroke(1.0f));
        renderer.setDrawOutlines(true);

        // 设置数据点大小
        renderer.setSeriesShape(0, new Ellipse2D.Double(-5, -5, 10, 10));

        // 设置悬停效果
        renderer.setDefaultEntityRadius(6);
        renderer.setDrawSeriesLineAsPath(true);

        // 设置工具提示生成器
        renderer.setBaseToolTipGenerator(new StandardXYToolTipGenerator(
            "X: {1}, Y: {2}",
            new java.text.DecimalFormat("0.00"),
            new java.text.DecimalFormat("0.00")
        ));

        plot.setRenderer(renderer);
    }

    private static Shape createCrossShape() {
        GeneralPath path = new GeneralPath();
        path.moveTo(-3, -3);
        path.lineTo(-1, -3);
        path.lineTo(-1, -1);
        path.lineTo(1, -1);
        path.lineTo(1, -3);
        path.lineTo(3, -3);
        path.lineTo(3, -1);
        path.lineTo(1, -1);
        path.lineTo(1, 1);
        path.lineTo(3, 1);
        path.lineTo(3, 3);
        path.lineTo(1, 3);
        path.lineTo(1, 1);
        path.lineTo(-1, 1);
        path.lineTo(-1, 3);
        path.lineTo(-3, 3);
        path.lineTo(-3, 1);
        path.lineTo(-1, 1);
        path.lineTo(-1, -1);
        path.lineTo(-3, -1);
        path.closePath();
        return path;
    }

    private static Shape createStarShape(int points, double outerRadius, double innerRadius) {
        GeneralPath path = new GeneralPath();
        double angle = Math.PI / points;

        for (int i = 0; i < 2 * points; i++) {
            double r = (i % 2 == 0) ? outerRadius : innerRadius;
            double theta = i * angle;
            double x = r * Math.cos(theta);
            double y = r * Math.sin(theta);

            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
    }

    private static void customizeLegend(JFreeChart chart) {
        // 获取图例
        LegendTitle legend = chart.getLegend();

        // 设置图例位置
        legend.setPosition(RectangleEdge.BOTTOM);

        // 设置图例背景
        legend.setBackgroundPaint(new GradientPaint(
            0, 0, new Color(240, 240, 255),
            100, 0, new Color(220, 220, 240)
        ));

        // 设置图例外框
        legend.setFrame(new BlockBorder(
            new Color(100, 100, 150),
            new BasicStroke(1.5f)
        ));

        // 设置图例字体
        legend.setItemFont(new Font("微软雅黑", Font.PLAIN, 12));

        // 设置图例项排列
        legend.setLayout(new ColumnArrangement(
            HorizontalAlignment.CENTER,
            VerticalAlignment.CENTER,
            10,  // 水平间距
            5    // 垂直间距
        ));
    }

    private static void displayChart(JFreeChart chart) {
        ChartPanel chartPanel = new ChartPanel(chart) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1200, 800);
            }
        };

        // 启用缩放
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);

        // 设置缩放矩形填充
        chartPanel.setFillZoomRectangle(true);
        chartPanel.setZoomOutlinePaint(Color.RED);

        JFrame frame = new JFrame("JFreeChart精细样式控制");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(chartPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
```

## 2. **高级渲染器示例**

```java
import org.jfree.chart.renderer.xy.*;

public class AdvancedRendererExample {

    public static void advancedRendererDemo(XYPlot plot) {
        // 使用不同的渲染器
        XYDotRenderer dotRenderer = new XYDotRenderer();
        dotRenderer.setDotHeight(6);
        dotRenderer.setDotWidth(6);
        dotRenderer.setSeriesPaint(0, new Color(255, 0, 0, 180));

        // 气泡图渲染器
        XYBlockRenderer blockRenderer = new XYBlockRenderer();
        blockRenderer.setBlockWidth(0.5);
        blockRenderer.setBlockHeight(0.5);

        // 矢量渲染器
        XYVectorRenderer vectorRenderer = new XYVectorRenderer();
        vectorRenderer.setBaseLength(0.1);
        vectorRenderer.setSeriesPaint(0, Color.BLUE);

        // 使用组合渲染器
        XYItemRenderer[] renderers = {dotRenderer, blockRenderer};
        CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot();
        // ... 设置组合图表
    }
}
```

## 3. **主题和样式模板**

```java
import org.jfree.chart.StandardChartTheme;
import java.awt.Font;

public class ChartThemeExample {

    public static void applyChartTheme(JFreeChart chart) {
        // 创建自定义主题
        StandardChartTheme theme = new StandardChartTheme("CustomTheme");

        // 设置字体
        theme.setExtraLargeFont(new Font("微软雅黑", Font.BOLD, 24));
        theme.setLargeFont(new Font("微软雅黑", Font.BOLD, 16));
        theme.setRegularFont(new Font("微软雅黑", Font.PLAIN, 12));
        theme.setSmallFont(new Font("微软雅黑", Font.PLAIN, 10));

        // 设置颜色
        theme.setTitlePaint(new Color(0, 70, 140));
        theme.setSubtitlePaint(new Color(100, 100, 100));
        theme.setLegendBackgroundPaint(Color.WHITE);
        theme.setLegendItemPaint(Color.BLACK);
        theme.setPlotBackgroundPaint(Color.WHITE);
        theme.setPlotOutlinePaint(Color.GRAY);
        theme.setBaselinePaint(Color.RED);
        theme.setCrosshairPaint(Color.BLUE);

        // 设置网格线
        theme.setDomainGridlinePaint(new Color(200, 200, 200));
        theme.setRangeGridlinePaint(new Color(200, 200, 200));

        // 应用主题
        theme.apply(chart);
    }
}
```

## 4. **动态样式控制**

```java
import org.jfree.chart.annotations.*;

public class DynamicStylingExample {

    public static void addAnnotations(XYPlot plot) {
        // 添加文本注解
        XYTextAnnotation annotation = new XYTextAnnotation("峰值点", 10, 8);
        annotation.setFont(new Font("宋体", Font.BOLD, 12));
        annotation.setPaint(Color.RED);
        annotation.setTextAnchor(TextAnchor.BOTTOM_CENTER);
        annotation.setRotationAngle(Math.PI / 6); // 旋转30度
        plot.addAnnotation(annotation);

        // 添加线注解
        XYLineAnnotation line = new XYLineAnnotation(
            5, 0, 5, 10,
            new BasicStroke(2.0f),
            new Color(0, 150, 0, 150)
        );
        plot.addAnnotation(line);

        // 添加形状注解
        XYShapeAnnotation shape = new XYShapeAnnotation(
            new Ellipse2D.Double(7, 7, 2, 2),
            new BasicStroke(1.0f),
            new Color(255, 0, 0, 100),
            new Color(255, 200, 200, 50)
        );
        plot.addAnnotation(shape);

        // 添加箭头注解
        XYPointerAnnotation pointer = new XYPointerAnnotation(
            "注意", 8, 6, Math.PI / 4
        );
        pointer.setBackgroundPaint(Color.YELLOW);
        pointer.setTipRadius(10.0);
        pointer.setBaseRadius(30.0);
        pointer.setArrowLength(20.0);
        pointer.setArrowWidth(10.0);
        pointer.setLabelOffset(5.0);
        plot.addAnnotation(pointer);
    }

    public static void addDataLabels(XYPlot plot) {
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();

        // 添加数据标签
        XYItemLabelGenerator labelGenerator = new StandardXYItemLabelGenerator(
            "{2}", // 只显示Y值
            new java.text.DecimalFormat("0.00"),
            new java.text.DecimalFormat("0.00")
        );

        renderer.setBaseItemLabelGenerator(labelGenerator);
        renderer.setBaseItemLabelsVisible(true);
        renderer.setBaseItemLabelFont(new Font("宋体", Font.PLAIN, 10));
        renderer.setBaseItemLabelPaint(Color.DARK_GRAY);
        renderer.setBasePositiveItemLabelPosition(new ItemLabelPosition(
            ItemLabelAnchor.OUTSIDE1, TextAnchor.BOTTOM_CENTER
        ));
    }
}
```

## 5. **交互式样式控制**

```java
import org.jfree.chart.labels.*;

public class InteractiveStyling {

    public static void addInteractivity(ChartPanel chartPanel) {
        // 启用悬停效果
        chartPanel.getChart().getXYPlot().getRenderer().setBaseToolTipGenerator(
            new StandardXYToolTipGenerator(
                "点 ({1}, {2})",
                new java.text.DecimalFormat("0.0"),
                new java.text.DecimalFormat("0.0")
            )
        );

        // 自定义悬停效果
        XYLineAndShapeRenderer renderer =
            (XYLineAndShapeRenderer) chartPanel.getChart().getXYPlot().getRenderer();

        // 设置悬停时的样式变化
        renderer.setUseFillPaint(true);
        renderer.setBaseFillPaint(new Color(255, 255, 255, 100));

        // 添加鼠标监听器实现动态效果
        chartPanel.addChartMouseListener(new ChartMouseListener() {
            @Override
            public void chartMouseClicked(ChartMouseEvent event) {
                // 处理点击事件
            }

            @Override
            public void chartMouseMoved(ChartMouseEvent event) {
                // 处理鼠标移动事件
                Entity entity = event.getEntity();
                if (entity instanceof XYItemEntity) {
                    XYItemEntity itemEntity = (XYItemEntity) entity;
                    // 高亮显示选中的数据点
                    int series = itemEntity.getSeriesIndex();
                    int item = itemEntity.getItem();
                    System.out.printf("鼠标悬停在系列%d的数据点%d上%n", series, item);
                }
            }
        });
    }
}
```

## 6. **样式配置工具类**

```java
import java.util.Map;
import java.util.HashMap;

public class StyleConfigurator {

    private static final Map<String, Color> COLOR_SCHEMES = new HashMap<>();
    private static final Map<String, Shape> SHAPE_TYPES = new HashMap<>();

    static {
        // 预定义颜色方案
        COLOR_SCHEMES.put("vibrant", new Color(255, 100, 100));
        COLOR_SCHEMES.put("pastel", new Color(200, 230, 255));
        COLOR_SCHEMES.put("monochrome", new Color(100, 100, 100));

        // 预定义形状
        SHAPE_TYPES.put("circle", new Ellipse2D.Double(-4, -4, 8, 8));
        SHAPE_TYPES.put("square", new Rectangle(-4, -4, 8, 8));
        SHAPE_TYPES.put("diamond", new Polygon(
            new int[]{0, 4, 0, -4},
            new int[]{-4, 0, 4, 0},
            4
        ));
    }

    public static void applyStyle(XYPlot plot, String styleName) {
        switch (styleName.toLowerCase()) {
            case "modern":
                applyModernStyle(plot);
                break;
            case "classic":
                applyClassicStyle(plot);
                break;
            case "minimal":
                applyMinimalStyle(plot);
                break;
            default:
                applyDefaultStyle(plot);
        }
    }

    private static void applyModernStyle(XYPlot plot) {
        plot.setBackgroundPaint(new Color(250, 250, 250));
        plot.setDomainGridlinePaint(new Color(220, 220, 220));
        plot.setRangeGridlinePaint(new Color(220, 220, 220));

        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(65, 105, 225, 200));
        renderer.setSeriesOutlinePaint(0, new Color(30, 70, 190));
        renderer.setSeriesShape(0, SHAPE_TYPES.get("circle"));
    }

    private static void applyClassicStyle(XYPlot plot) {
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        plot.setOutlineStroke(new BasicStroke(2.0f));
    }

    private static void applyMinimalStyle(XYPlot plot) {
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.WHITE);
        plot.setRangeGridlinePaint(Color.WHITE);
        plot.setOutlineVisible(false);

        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, Color.BLACK);
        renderer.setSeriesShape(0, new Ellipse2D.Double(-2, -2, 4, 4));
    }
}
```

## 主要样式控制总结

### 1. **颜色控制**
- 单色、渐变色、透明色
- 系列颜色、背景颜色、边框颜色
- 网格线颜色、坐标轴颜色

### 2. **形状控制**
- 内置形状：圆形、方形、三角形、菱形
- 自定义形状：使用`Shape`接口
- 形状大小、边框、填充

### 3. **字体控制**
- 标题、坐标轴、图例、数据标签字体
- 字体样式、大小、颜色
- 字体旋转角度

### 4. **线条控制**
- 线宽、虚线样式
- 线条端点样式、连接点样式
- 透明度控制

### 5. **布局控制**
- 图表边距、间距
- 坐标轴位置、标签位置
- 图例位置、排列方式

### 6. **交互控制**
- 工具提示
- 数据标签
- 鼠标悬停效果
- 点击事件处理

### 7. **高级特性**
- 注解（文本、线条、形状、箭头）
- 主题系统
- 组合图表
- 动态样式切换

这些示例展示了JFreeChart强大的样式控制能力，您可以根据具体需求组合使用这些特性来创建高度定制化的图表。
