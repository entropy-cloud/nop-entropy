# 生成报表 / 单据打印（nop-report）

## 适用场景

- 后台经营统计报表、数据集驱动的报表视图、按模板导出结果文件。
- 业务单据打印：发票、订单、凭证、证明等。**支持三种打印载体**：PDF、XLSX、HTML。
- **套打**：在预印好的表单（扫描图）上对位填数据，屏幕可见底图、打印只出数据。

## AI 决策提示

- 平台已内建 `nop-report`，**不要自建报表引擎或导出框架**。
- 报表模板是 Excel 工作簿（`.xpt.xlsx` 或 `.xpt.xml`），作者用 Excel 思维设计（格子 + 展开 + 公式），不写代码。
- **没有独立 `ReportService` 类**——渲染入口是 `IReportEngine`，应用在 BizModel 里注入并调用（见 `ReportDemoBizModel`）。`nop-report-service` 里的 BizModel 是报表定义/数据集/数据源的 CRUD 管理，不是渲染入口。

## 核心概念

| 概念 | 说明 |
|------|------|
| **XPT 报表模型** | `.xpt.xlsx`（推荐，Excel 设计）或 `.xpt.xml`（XML 序列化），加载成 `ExcelWorkbook`。文件类型常量 `XptConstants.FILE_TYPE_XPT_XLSX`/`FILE_TYPE_XPT_XML` |
| **渲染类型** | `html`（屏幕预览，`ITextTemplateOutput`）/ `xlsx`（OOXML 二进制，`IBinaryTemplateOutput`）/ `pdf`（PDFBox 直接渲染，**非 xlsx 转换**）。常量 `XptConstants.RENDER_TYPE_*` |
| **展开（expand）** | 模板里一个单元格，运行时按数据集展开成多行/多列，支持父子层级嵌套（`rowParent`/`colParent`） |
| **数据集（dataset）** | 一个命名的对象列表（`ReportDataSet`），可用程序构造，也可用 SQL/ORM 标签（`UseJdbcDataSet`/`UseOrmDataSet`/`UseQueryDataSet`）在 `beforeExpand` 里执行 |
| **套打（overlay）** | 工作表上放一张背景图（`ExcelImage`，设 `print=false`），屏幕可见、打印隐藏；动态文字用 `${...}` 对位填到表单空白处 |

## 引擎入口：IReportEngine

`io.nop.report.core.engine.IReportEngine`（impl `ReportEngine`）：

```java
ExcelWorkbook getXptModel(String reportPath);                                    // 从 VFS 加载 .xpt.xlsx/.xpt.xml
ITemplateOutput getRenderer(String reportPath, String renderType);               // = getRendererForXptModel(getXptModel(path), type)
default ITextTemplateOutput getHtmlRenderer(String reportPath);                  // renderType="html"
ExcelWorkbook generateFromXptModel(ExcelWorkbook wb, IEvalScope ctx);            // 展开填充
ExcelWorkbook buildXptModelFromImpModel(String impModelPath);                    // 从导入模板自动生成报表模型
```

## 怎么调用（应用 BizModel）

注入 `IReportEngine`，传参数用 `IEvalScope.setLocalValues(data)`（模板里 `${entity.field}`、`${...表达式}` 从 scope 取值）：

```java
@Inject IReportEngine reportEngine;

// HTML 预览（返回文本）
@BizQuery
public String renderHtml(@Name("reportName") String reportName,
                         @Optional @Name("data") Map<String, Object> data) {
    String path = "/nop/main/report/" + reportName;   // 应用项目报表模板的 VFS 路径
    ITextTemplateOutput output = reportEngine.getHtmlRenderer(path);
    IEvalScope scope = XLang.newEvalScope();
    if (data != null) scope.setLocalValues(data);      // 报表参数通过 scope 传入
    return output.generateText(scope);
}

// 下载（xlsx/pdf 二进制，返回 WebContentBean 触发浏览器下载）
@BizQuery
public WebContentBean download(@Name("reportName") String reportName,
                               @Name("renderType") String renderType) {  // "xlsx" | "pdf" | "html"
    String path = "/nop/main/report/" + reportName;
    ITemplateOutput output = reportEngine.getRenderer(path, renderType);
    IEvalScope scope = XLang.newEvalScope();
    IResource resource = ResourceHelper.getTempResource("rpt");
    output.generateToResource(resource, scope);
    return new WebContentBean("application/octet-stream", resource.toFile(), fileName);
}
```

> 真实参考：`nop-report/nop-report-demo/src/main/java/io/nop/report/demo/biz/ReportDemoBizModel.java`。模板路径用 `StringHelper.isValidVPath(reportName)` 校验后再拼接，防路径注入。

## XPT 模板语法（报表作者必读）

报表模板的单元格行为有**三个配置入口**：

### 1. 单元格文本（`XptModelInitializer`）

| 文本 | 含义 |
|------|------|
| `*=fieldName` | 引用当前行对象的字段（如 `*=金额`、`*=fldA`） |
| `*=^ds!field` | 绑定数据集 `ds`，**向下展开（多行）**，显示 `field`。`^` → `expandType=r` |
| `*=>ds!field` | **向右展开（多列）**。`>` → `expandType=c` |
| `*=field@expandExpr` | `@` 分隔绑定字段与自定义展开表达式 |
| `${...}` | XPL 表达式（如 `${SUM(C4)}`、`${cz.where('ID',xptRt.field('ID')).sum('金额')}`） |
| `=SUM(D3)` | Excel 公式，自动 `exportFormula=true` |

### 2. 单元格批注（comment，主要作者面）

`ExcelToXptModelTransformer` 把单元格批注读成多行 `key=value` 配置，映射到 `XptCellModel`。例（单元格 A2 批注）：
```
expandType=r
expandExpr=orders
valueExpr=null
```

### 3. 工作簿/工作表配置页

工作簿可有名为 `XptWorkbookModel` 的 sheet，每个数据 sheet 可有 `<sheetName>-XptSheetModel`，承载 `loopVarName`、`beforeExpand`、`afterExpand`（XPL 脚本）、`sheetNameExpr`、`ext:namedStyles` 等。

### XptCellModel 指令集（批注 key）

| 指令 | 取值 | 用途 |
|------|------|------|
| `expandType` | `r` \| `c` | 展开方向：`r`=向下/多行，`c`=向右/多列 |
| `expandExpr` | 表达式 | 返回展开遍历的列表（如 `orders`、`ds.group('dept')`） |
| `field` | 字符串 | 每行的字段名（分组/取值） |
| `ds` | 字符串 | 绑定的数据集名 |
| `rowParent` / `colParent` | 单元格坐标（如 `A2`） | **父子层级坐标**，实现嵌套分组展开 |
| `valueExpr` | 表达式 | 展开后计算值，可引用层级坐标 / `SUM(...)` 公式 |
| `formatExpr` | 表达式 | 格式化显示文本 |
| `expandMaxCount` / `expandMinCount` | 整数 | 限制行数 / 补空行 |
| `expandInplaceCount` | 整数 | 模板预留格数（避免插行） |
| `expandOrderBy` | 排序字段 | 排序展开列表 |
| `keepExpandEmpty` | 布尔(默认true) | 展开集为空时是否保留 |
| `rowExtendForSibling` / `colExtendForSibling` | 布尔(默认true) | 兄弟格展开时本格是否拉伸 |
| `rowTestExpr` / `colTestExpr` | 断言 | 断言假则丢弃该行/列 |
| `exportFormula` | 布尔 | 导出时保留 Excel 公式 |
| `styleIdExpr` / `linkExpr` | 表达式 | 动态样式 / 超链接 |
| `dict` / `domain` | 字符串 | 字典/域（显示翻译值） |

> 指令权威定义：`nop-format/nop-excel/.../model/_gen/_XptCellModel.java`（`xml name:` 注释即配置 key）。

## 数据集（dataset）

数据集 = scope 里一个命名的对象列表。两种构造方式：

**程序构造**：`ReportDataSet` 提供 `group(field)`、`where(...)`、`filter(...)`、`sort(...)`、`sum(field)`、`avg`、`countIf`、`select(field)`。在 `beforeExpand` 或工作簿脚本里 `xptRt.makeDs(dsName, data)` 构造，单元格 `expandExpr` 引用。

**SQL/ORM 标签**（xlib `/nop/report/xlib/xpt-rt.xlib`，在 `beforeExpand` 里用）：

```xml
<!-- beforeExpand 单元格内容 -->
<xpt-rt:UseJdbcDataSet xpl:lib="/nop/report/xlib/xpt-rt.xlib">
  <source>
    select sid, fldA, fldB from my_table;
  </source>
</xpt-rt:UseJdbcDataSet>
<!-- 数据单元格：*=^ds1!sid  绑定 ds1 向下展开 sid -->
```

四个标签：`UseJdbcDataSet`（原生 SQL，默认 ds1）、`JdbcDataSet`（流式，保持连接）、`UseOrmDataSet`（走 ORM，含 `disableLogicalDelete`）、`UseQueryDataSet`（query-bean）。

> 运行时管理的报表：`nop_report_dataset`/`nop_report_datasource`/`nop_report_sub_dataset`/`nop_report_dataset_ref` 表存数据集定义（`dsName`/`dsType`/`dsText` 查询文本），由 CRUD BizModel 管理。

## 套打（overlay / 预印表单打印）

**机制**：工作表放一张背景图（`ExcelImage`）覆盖打印区，设 `print=false`——屏幕/HTML 预览可见底图，**打印/导出时隐藏**，只把动态文字（`${entity.field}`）打到真实表单的空白处。

**三个渲染器如何处理 `print=false`**：
- **HTML**：图片作为 CSS `background-image`（屏幕），`print=false` 的图被排除出 `@media print`，浏览器打印时消失。
- **PDF**：`PdfSheetRenderer.renderImages` 按 `print` 标志排除背景图，PDF 只含数据。
- **XLSX**：`print=false`（对应 OOXML `fPrintsWithSheet="0"`）原样保留到导出的 xlsx，Excel 里同样不打印。

**制作套打报表步骤**：
1. 把扫描好的表单图作为图片插入工作表，锚定覆盖打印区（用 Excel 绘图锚点）。
2. 设置该图片 `print=false`（等价 Excel "对象不打印" / OOXML `fPrintsWithSheet="0"`）。图片级指令（`testExpr`/`dataExpr`）可在图片描述里 `----` 后配置。
3. 用合并单元格 + `${entity.field}` 表达式对位填到表单空白处，调行高列宽匹配表单几何。

> 真实范例：`nop-report-demo/.../_vfs/nop/report/demo/base/09-套打.xpt.xlsx`（实习证明表单：`image1.png` 作背景 `fPrintsWithSheet="0"`，单元格 `${entity.zy}`/`${entity.xm}`/`${entity.fromDate}` 等对位填空）。测试入口 `TestReportDemoBizModel.testFormPrinting()`。

## 从导入模板自动生成报表（buildXptModelFromImpModel）

不手写单元格批注，用一个**普通 Excel 文件 + `imp.xml` 导入模型**自动生成 XPT 报表：

```java
// imp.xml 描述字段/列表区域（带 xpt:* 扩展属性），自动合成 XptCellModel 指令
ExcelWorkbook wb = reportEngine.buildXptModelFromImpModel("/nop/main/imp/my-export.imp.xml");
ITemplateOutput output = reportEngine.getRendererForXptModel(wb, "xlsx");
```

`ExcelTemplateToXptModelTransformer` 按 `imp.xml` 的列表区/字段标记自动合成 `expandType=r`/`field`/`valueExpr`/`formatExpr`/`dict` 等指令。便捷导出 Java bean 到 Excel/HTML/PDF 的封装：`ExcelReportHelper.saveXpxObject(reportEngine, impModelPath, resource, entity, renderType, scope)`。

## 报表模板放哪里（VFS 路径）

- 应用项目：`src/main/resources/_vfs/<group>/<module>/.../*.xpt.xlsx`，运行时合并进 VFS。
- 约定路径示例：`/nop/main/report/<reportName>.xpt.xlsx`（BizModel 拼路径前校验 `StringHelper.isValidVPath`）。
- 加载：`ReportEngine.getXptModel(path)` 校验文件类型 ∈ `ALLOWED_XPT_FILE_TYPES=[xpt.xml, xpt.xlsx]`，经 `XptModelLoader` → `ExcelToXptModelTransformer`（批注→模型）+ `XptModelInitializer`（文本→表达式），结果缓存。
- 范例：`nop-report-demo/.../_vfs/nop/report/demo/base/`（18 个编号报表，含 `01-档案式报表`、`09-套打`、`18-兄弟节点不自动展开` 等）。

## 反模式

- ❌ 自建报表/导出框架——用 `nop-report`。
- ❌ 直接调 `nop-report-service` 的 CRUD BizModel 当渲染入口——渲染走 `IReportEngine`，CRUD BizModel 只管报表定义/数据集/数据源的增删改查。
- ❌ 把 PDF 当"xlsx 转 PDF"实现理解——PDF 是 PDFBox 直接从展开后的工作簿模型渲染，三种输出共享同一展开逻辑。
- ❌ 套打用前端 HTML 浮层定位——平台 `ExcelImage.print=false` 已支持，且 HTML/PDF/XLSX 三端一致。

## 仓库里的真实参考

- 引擎：`nop-report/nop-report-core/src/main/java/io/nop/report/core/engine/IReportEngine.java` + `ReportEngine.java` + `ExpandedSheetGenerator.java`
- 调用范例：`nop-report/nop-report-demo/src/main/java/io/nop/report/demo/biz/ReportDemoBizModel.java` + 测试 `TestReportDemoBizModel.java`
- 模板范例：`nop-report/nop-report-demo/.../_vfs/nop/report/demo/base/*.xpt.xlsx`（套打见 `09-套打.xpt.xlsx`）
- 数据集标签：`nop-report/nop-report-core/src/main/resources/_vfs/nop/report/xlib/xpt-rt.xlib`
- 单元格模型：`nop-format/nop-excel/.../model/_gen/_XptCellModel.java`（指令权威）+ `ExcelImage.java`（套打 `print` 标志）
- 渲染器：xlsx `nop-report-core/.../engine/renderer/XlsxReportRendererFactory.java`；html `nop-format/nop-excel/.../renderer/HtmlReportRendererFactory.java`；pdf `nop-report-pdf/.../renderer/PdfReportRendererFactory.java`

## 相关文档

- `../02-core-guides/reporting-and-notification-integration.md` — 报表/通知集成的默认路线与边界
- `../03-modules/nop-report.md` — nop-report 模块概览（实体表/子模块）
- `../04-reference/source-anchors.md`（`REPORT-001`~`REPORT-003`）
