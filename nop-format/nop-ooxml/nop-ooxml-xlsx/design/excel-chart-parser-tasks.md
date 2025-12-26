# Excel Chart Parser 代码生成任务规划

## 项目概述
基于[excel-chart-parser-design.md](excel-chart-parser-design.md)设计文档，逐步生成Excel图表解析器的Java实现代码。

## 代码生成阶段划分

### 阶段1：基础解析器框架（第1-3步）

#### 任务1：创建IChartStyleProvider接口
**文件路径**: `src/main/java/io/nop/ooxml/xlsx/chart/IChartStyleProvider.java`

**功能说明**:
- 定义图表样式提供者接口
- 提供theme支持和外部样式合并功能
- 包含主题颜色映射、样式解析、字体解析等方法

**接口方法**:
- `getThemeColor(String themeColor)` - 获取主题颜色
- `resolveColor(String colorRef)` - 解析颜色引用
- `applyTheme(String componentType, IChartStyleSupportModel model)` - 应用主题

**注意**: 此接口先定义，具体实现可以在后续阶段完成

---

#### 任务2：创建ChartTextParser文本解析器
**文件路径**: `src/main/java/io/nop/ooxml/xlsx/chart/ChartTextParser.java`

**功能说明**:
- 统一处理富文本和简单文本
- 支持RTF格式到纯文本的转换
- 处理文本样式和格式

**核心方法**:
- `extractText(XNode textNode)` - 解析文本节点，提取文本内容
- `extractCellReference(XNode node)` - 提取单元格引用
- `extractRichText(XNode richNode)` - 提取富文本内容

**依赖**: XNode类（已存在）

**参考示例**: chart-sample目录下的chart1.xml中的标题文本配置

---

#### 任务3：创建ChartShapeStyleParser样式解析器
**文件路径**: `src/main/java/io/nop/ooxml/xlsx/chart/ChartShapeStyleParser.java`

**功能说明**:
- 统一处理填充、边框、阴影等样式属性
- 支持theme颜色解析
- 处理渐变填充等复杂样式

**核心方法**:
- `parseShapeStyle(XNode spPrNode, IChartStyleProvider styleProvider)` - 解析形状样式
- `parseFill(ChartShapeStyleModel style, XNode fillNode, IChartStyleProvider styleProvider)` - 解析填充
- `parseSolidFill(ChartFillModel fill, XNode solidFillNode, IChartStyleProvider styleProvider)` - 解析纯色填充
- `parseBorder(ChartShapeStyleModel style, XNode lnNode, IChartStyleProvider styleProvider)` - 解析边框
- `parseShadow(ChartShapeStyleModel style, XNode shadowNode, IChartStyleProvider styleProvider)` - 解析阴影

**依赖**: IChartStyleProvider接口、ChartShapeStyleModel等模型类

**参考示例**: chart-sample目录下的样式配置示例

---

### 阶段2：主解析器实现（第4步）

#### 任务4：创建DrawingChartParser主解析器
**文件路径**: `src/main/java/io/nop/ooxml/xlsx/chart/DrawingChartParser.java`

**功能说明**:
- 采用单例模式：`DrawingChartParser.INSTANCE`
- 仿照DrawingParser设计模式
- 使用childByTag处理简单子节点，使用SELECTOR机制处理复杂嵌套节点
- 复用现有的UnitsHelper、ColorHelper等工具类

**核心方法**:
- `parseChart(XNode chartNode, IChartStyleProvider chartStyleProvider, ChartModel excelChart)` - 主解析入口
- `parseBasicProperties(ChartModel chart, XNode chartNode)` - 解析基础属性
- `parseTitle(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider)` - 解析标题
- `parseLegend(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider)` - 解析图例
- `parsePlotArea(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider)` - 解析绘图区域
- `parseTitleText(ChartTitleModel title, XNode titleNode)` - 解析标题文本
- `parseTitleLayout(ChartTitleModel title, XNode titleNode)` - 解析标题布局
- `parseTitleStyle(ChartTitleModel title, XNode titleNode, IChartStyleProvider styleProvider)` - 解析标题样式
- `parseLegendStyle(ChartLegendModel legend, XNode legendNode, IChartStyleProvider styleProvider)` - 解析图例样式
- `parseAxisTitleText(ChartAxisTitleModel axisTitle, XNode axisTitleNode)` - 解析坐标轴标题文本
- `parseDataSource(ChartDataSourceModel dataSource, XNode dataSourceNode)` - 解析数据源
- `extractDataCellReference(XNode dataSourceNode)` - 提取数据源单元格引用

**依赖**: IChartStyleProvider接口、ChartTextParser、ChartShapeStyleParser、UnitsHelper、ColorHelper

**参考示例**: chart-sample目录下的完整图表配置（chart1.xml, chart2.xml, chart3.xml）

---

### 阶段3：扩展解析器（第5-6步）

#### 任务5：创建ChartTextStyleParser文本样式解析器
**文件路径**: `src/main/java/io/nop/ooxml/xlsx/chart/ChartTextStyleParser.java`

**功能说明**:
- 解析文本样式（字体、对齐等）
- 支持theme颜色解析
- 处理字体大小、粗细、颜色等属性

**核心方法**:
- `parseTextStyle(XNode txPrNode, IChartStyleProvider styleProvider)` - 解析文本样式
- `parseFontStyle(XNode rPrNode, IChartStyleProvider styleProvider)` - 解析字体样式
- `parseParagraphStyle(XNode pPrNode, IChartStyleProvider styleProvider)` - 解析段落样式

**依赖**: IChartStyleProvider接口、ChartTextStyleModel等模型类

**参考示例**: chart-sample目录下的字体样式配置

---

#### 任务6：扩展DrawingChartParser支持更多图表元素
**文件路径**: 扩展`src/main/java/io/nop/ooxml/xlsx/chart/DrawingChartParser.java`

**功能说明**:
- 添加坐标轴解析方法
- 添加数据系列解析方法
- 添加图表类型特定的解析方法

**新增方法**:
- `parseAxes(ChartModel chart, XNode chartNode, IChartStyleProvider styleProvider)` - 解析坐标轴
- `parseAxis(ChartAxisModel axis, XNode axisNode, IChartStyleProvider styleProvider)` - 解析单个坐标轴
- `parseSeries(ChartModel chart, XNode plotAreaNode, IChartStyleProvider styleProvider)` - 解析数据系列
- `parseSeries(ChartSeriesModel series, XNode seriesNode, IChartStyleProvider styleProvider)` - 解析单个数据系列

**参考示例**: chart-sample目录下的坐标轴和数据系列配置

---

### 阶段4：样式提供者实现（第7步）

#### 任务7：实现IChartStyleProvider接口
**文件路径**: `src/main/java/io/nop/ooxml/xlsx/chart/DefaultChartStyleProvider.java`

**功能说明**:
- 实现IChartStyleProvider接口
- 提供theme支持和外部样式合并功能
- 加载并解析styles.xml和colors.xml文件

**核心方法实现**:
- `getThemeColor(String themeColor)` - 从colors.xml中获取主题颜色映射
- `resolveColor(String colorRef)` - 解析颜色引用，支持theme颜色和直接颜色
- `applyTheme(String componentType, IChartStyleSupportModel model)` - 应用主题到模型

**依赖**: styles.xml、colors.xml文件、主题颜色映射表

**参考**: OOXML规范中的主题颜色定义

---

## 类生成顺序总结

1. **IChartStyleProvider** - 接口定义（先定义接口，后实现）
2. **ChartTextParser** - 文本解析器（基础工具类）
3. **ChartShapeStyleParser** - 样式解析器（基础工具类）
4. **DrawingChartParser** - 主解析器（核心功能）
5. **ChartTextStyleParser** - 文本样式解析器（扩展功能）
6. **扩展DrawingChartParser** - 支持更多图表元素（功能增强）
7. **DefaultChartStyleProvider** - 样式提供者实现（最后实现）

## 依赖关系

```
IChartStyleProvider (接口)
    ↑
    ├── ChartTextParser (无依赖)
    ├── ChartShapeStyleParser (依赖IChartStyleProvider接口)
    ├── ChartTextStyleParser (依赖IChartStyleProvider接口)
    ├── DrawingChartParser (依赖上述所有解析器和IChartStyleProvider接口)
    └── DefaultChartStyleProvider (实现IChartStyleProvider接口)
```

## 需要复用的现有类

- `UnitsHelper` - 单位转换工具
- `ColorHelper` - 颜色处理工具
- `XNode` - XML节点处理类
- `ChartModel` 及相关模型类 - 基于chart.xdef生成的Java Bean类

## 参考示例文件

所有解析器的实现都应参考以下示例文件：
- `chart-sample/chart1.xml` - 包含标题和字体样式配置
- `chart-sample/chart2.xml` - 包含图例和坐标轴配置
- `chart-sample/chart3.xml` - 包含数据系列和样式配置

## 实现注意事项

1. **单例模式**: 所有解析器都采用单例模式实现
2. **空值处理**: childByTag、attrLong、attrBoolean、attrDouble等函数可能返回null，需要处理空值情况
3. **样式合并**: 正确处理chart.xml与外部样式文件的合并
4. **主题支持**: 实现完整的theme颜色映射机制
5. **代码风格**: 遵循项目现有的代码规范和命名约定
