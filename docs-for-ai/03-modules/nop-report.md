# nop-report — 报表引擎

## 功能概览

全功能报表引擎，支持类 Excel 报表定义、多格式输出。

- 报表模板是 Excel 工作簿（`.xpt.xlsx`/`.xpt.xml`），用 Excel 思维设计（格子 + 展开 + 公式），不写代码
- **三种输出载体**：XLSX、PDF（PDFBox 直接渲染）、HTML（屏幕预览）—— 均可作打印载体
- **套打**：背景图（`ExcelImage.print=false`）屏幕可见、打印隐藏，动态文字对位填表单
- 数据集支持程序构造 / 原生 SQL / ORM / query-bean（`xpt-rt.xlib` 标签）
- 可从导入模板（Excel + `imp.xml`）自动生成报表模型

## 当前能力面与已知限制（canonical owner）

> 本节是 nop-report 当前能力面与已知限制的权威来源；跨引擎对照矩阵（Jasper/FineReport/UReport 等）属 AI 开发记录区的时效性调研证据，本节不复制。

**能力现状**：

- ✅ 完整：单元格/交叉展开（含父子层级嵌套）、套打三端一致、PDF 分页与重复表头/关键列（`nop.report.pdf.*` 配置）、xlsx/html/shtml/pdf 四种 renderType、图表动态绑定、公式函数（`ReportFunctions`）、导入模板自动生成、嵌入式 API（`IReportEngine`）
- 🟡 受限：条件格式（仅 `styleIdExpr`，无规则集/色阶/数据条/图标集）、交互（`linkExpr`/`editorId`/`viewerId` 仅模型层，渲染层无输出）、子报表（仅父子层级展开，无跨模板引用）、大数据（`JdbcDataSet` 流式取数但渲染侧非增量）、条码（仅 QRCODE）、Word（仅表格路径，未注册 renderType）
- ❌ 缺失：数据级行/列权限、HTML 分页打印、结果缓存接线、模板 i18n、Web 设计器、OLAP 数据源、模板版本管理

**参数模型（已生效）**：模板 `XptWorkbookModel` 内嵌 `<params>` 声明（name/type/label/required/defaultValue/controlType/multiple/dependsOn + 候选来源静态 options/`dictName`/`optionsSql`(+`querySpace`)），引擎展开前统一校验（required/类型转换/默认值）并注入 scope 的 `param` 变量，模板内 `${param.xxx}` 取值；错误 `nop.err.report.param.*`。接口：`NopReportForm__getReportFormSchema(reportPath)`（声明 → AMIS form schema）、`NopReportForm__getReportParamOptions(reportPath, paramName, dependsValues)`（候选 [{label,value}]，均先过报表级授权）。controlType→AMIS 映射（规范性）：string→input-text；int/float→input-number；boolean→switch；date/datetime→input-date/input-datetime；有候选来源→select（multiple 展示 multiple）；dependsOn→表单级联声明。参数以解析后的规范化值参与结果缓存键（WI6 消费）。未声明的 `${param.x}` 引用运行时取 null；候选集动态、不做枚举闭合校验（design 契约裁定）。

**权限接线（已生效）**：渲染入口（`IReportEngine.getXptModel`）与 JDBC 取数标签（`xpt-rt.xlib` 的 `JdbcDataSet`/`UseJdbcDataSet`）强制授权检查，未授权抛 `nop.err.report.auth.*`：

- 消费 `NopReportDefinitionAuth`（按角色）与 `NopReportDatasourceAuth`（按角色），用户角色取自 nop-auth 会话上下文
- 报表模板路径与 `NopReportDefinition.rptText` 精确匹配——**rptText 约定存模板 VFS 全路径**（该字段原注释「文件路径、SQL语句等」的歧义由此收窄：平台授权语境下 rptText = 模板路径）
- 授权语义：定义/数据源未配置 auth 记录 → 不限制；配置了 → 用户角色须命中任一；无用户上下文且配置了记录 → fail-closed 拒绝（调度等系统调用方须显式建立用户上下文）；豁免角色由配置项 `nop.report.auth.exempt-roles`（逗号分隔，默认空）指定
- `getRendererForXptModel`/`getRendererForExcel` 为程序化内部 API（模型已在内存、无平台报表身份），不做单独检查；公共渲染入口走 `getRenderer(path, renderType)`
- 引擎作嵌入式库使用（无 `nopReportAuthChecker` bean）时不启用检查

**已知限制勘误**：

- 报表结果文件缓存（`NopReportResultFile`）：实体存在但**引擎链路当前未接线**，勿依赖
- ~~报表/数据源权限预留未接线~~（已消除：渲染入口与取数标签现强制授权，见下「权限接线」）

**补强方向与契约**：缺失能力的目标契约已在 AI 开发记录区的设计目录（nop-report 子目录）裁定为草案，落地以报表能力 roadmap 编排为准：

| 契约 | 摘要 |
|------|------|
| 参数模型 | 声明内嵌 XPT、`${param.*}` 统一取值、AMIS 查询表单、参与缓存键 |
| 权限接线（已落地） | 渲染入口 + 取数标签双重授权检查、`nop.err.report.auth.*` 快速失败、exempt-roles 豁免 |
| HTML 分页打印 | 浏览器打印语境的 `@page`/重复表头/页脚占位符，语义对齐 PDF 配置 |
| HTML 验收基线 | 浏览器截图 golden + DOM 断言的验收口径 |

**范围边界**：PDF 高级能力归 PDF 能力 roadmap（已完成）；图表静态渲染 provider 归 echarts 渲染 roadmap；Word 通用文档模型归 docx 文档模型 roadmap；定时报送/告警/大屏/AI 问数归 nop-datav roadmap——均不在 nop-report 内重复建设（roadmap 文件均在 AI 开发记录区 backlog 目录）。

## 主要能力入口

| 能力 | 怎么做 | 入口 |
|------|--------|------|
| **生成报表 / 单据打印（含套打）** | 注入 `IReportEngine`，`getRenderer(path, renderType)` 渲染 xlsx/pdf/html | **`../03-runbooks/generate-report.md`**（XPT 单元格语法 / 数据集 / 套打 / 调用范例 / imp 模板自动生成） |

## 核心实体

> 运行时管理的报表（通过 CRUD 页面定义数据集/数据源）：实体表见下。**渲染入口不是这些 CRUD BizModel，而是 `IReportEngine`**（见上方 runbook）。

| 实体 | 表名 | 用途 |
|------|------|------|
| NopReportDefinition | `nop_report_definition` | 报表定义 |
| NopReportDefinitionAuth | `nop_report_definition_auth` | 报表访问权限（按角色；已接线——渲染入口强制校验，见上「权限接线」） |
| NopReportDataset | `nop_report_dataset` | 数据集定义 |
| NopReportSubDataset | `nop_report_sub_dataset` | 子数据集关联 |
| NopReportDatasetRef | `nop_report_dataset_ref` | 报表-数据集引用 |
| NopReportDatasource | `nop_report_datasource` | 数据源定义 |
| NopReportDatasourceAuth | `nop_report_datasource_auth` | 数据源权限（按角色；已接线——JDBC 取数标签强制校验，见上「权限接线」） |
| NopReportResultFile | `nop_report_result_file` | 缓存的报表结果文件（**实体存在、引擎链路未接线**） |

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-report-core` | 报表核心引擎 |
| `nop-report-pdf` | PDF 输出支持 |
| `nop-report-docx` | DOCX 输出支持 |
| `nop-report-api` | API DTO |
| `nop-report-dao` | ORM 实体与 DAO |
| `nop-report-service` | 业务逻辑 |
| `nop-report-web` | Web 层与 AMIS 页面 |
| `nop-report-ext` | 扩展功能 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-report/model/nop-report.orm.xml` |

## 相关文档

- `../02-core-guides/reporting-and-notification-integration.md`
- `../reusable-modules-overview.md`
