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

✅ 模板文本 i18n（`@i18n:key|默认` 平台约定，`xptRt.i18n/i18nVar`，缺失回退原文本；字典翻译本就 locale 感知）；csv/fods/rtf/png 四种扩展 renderType（csv=RFC4180 首表、fods=Flat ODF LibreOffice 事实兼容、rtf=基础表格、png=逐页 PNG zip；透视还原 residual），单元格/交叉展开（含父子层级嵌套）、套打三端一致、PDF 分页与重复表头/关键列（`nop.report.pdf.*` 配置）、xlsx/html/shtml/pdf 四种 renderType、图表动态绑定、公式函数（`ReportFunctions`）、导入模板自动生成、嵌入式 API（`IReportEngine`）
- 🟡 受限：条件格式规则集（✅ 表达式谓词/数值区间 + 样式，首中生效、可覆盖 styleIdExpr；色阶/数据条/图标集 ❌ residual，roadmap WI17 承载）、交互（html 输出已带 linkExpr 超链接/editorId/viewerId 属性/列排序/层级折叠渐进增强——消费契约为文档级解析，innerHTML 注入不执行交互脚本；钻取目标由模板 linkExpr 表达）、子报表（✅ cell 级 `<subreportPath>`/`<subreportParams>` 引用另一模板并传参——html 嵌套渲染、xlsx/pdf 文本回退、深度上限 fail-fast；钻取跳转由 `linkExpr` 参数化承载）、大数据（`JdbcDataSet` 流式取数；全局行数上限 `nop.report.max-expand-rows` fail-fast 防 OOM；迭代式 xlsx 导出 `getRendererForExcelData` 游标分页——完整惰性展开不做，见 AI 开发记录区 large-dataset 契约）、条码/二维码（QRCODE 函数 + `qr:*` ext props：barcodeFormat 支持全部 Zxing 码制——CODE_128/EAN_13/PDF_417 等、errorCorrection 整数 0-3（PDF_417 为 0-8 级）、尺寸/边距/编码；未知码制报错）、Word（仅表格路径，未注册 renderType）
- ❌ 缺失：导出格式广度进阶（色阶/数据条/透视还原等 residual）、模板文件 locale 变体加载（文本 i18n 已生效）、Web 设计器、OLAP 数据源、模板版本管理

**参数模型（已生效）**：模板 `XptWorkbookModel` 内嵌 `<params>` 声明（name/type/label/required/defaultValue/controlType/multiple/dependsOn + 候选来源静态 options/`dictName`/`optionsSql`(+`querySpace`)），引擎展开前统一校验（required/类型转换/默认值）并注入 scope 的 `param` 变量，模板内 `${param.xxx}` 取值；错误 `nop.err.report.param.*`。接口：`NopReportForm__getReportFormSchema(reportPath)`（声明 → AMIS form schema；amis 渲染已 deprecated，见 `../04-reference/amis-legacy-deprecation.md`）、`NopReportForm__getReportParamOptions(reportPath, paramName, dependsValues)`（候选 `List<DictOptionBean>` label/value，均先过报表级授权）。controlType→AMIS 映射（规范性）：string→input-text；int/float→input-number；boolean→switch；date/datetime→input-date/input-datetime；有候选来源→select（multiple 展示 multiple）；dependsOn→表单级联声明。参数以解析后的规范化值参与结果缓存键（WI6 消费）。未声明的 `${param.x}` 引用运行时取 null；候选集动态、不做枚举闭合校验（design 契约裁定）。

**结果文件缓存（已生效）**：`NopReportCache__download(reportPath, renderType, params, bizDate)` 按「报表+参数摘要+数据版本」键控（`NopReportResultFile`），命中直接返回文件、未命中渲染落盘落库；`generateAsync`/`getResultStatus` 异步生成与轮询、`invalidate(rptId)` 手动失效；TTL/开关/目录配置 `nop.report.result-cache.*`。**授权先行于缓存命中**（未授权不返回缓存）。已知限制：数据版本仅 DATE 型、过期记录不自动清理。设计见 AI 开发记录区 result-cache 契约。

**HTML 交互与响应式（已生效）**：html 输出内嵌渐进增强脚本（幂等安装）：th 点击列排序（数值优先/文本 fallback）、展开层级折叠组切换（`data-expand-group`/`aria-expanded`，从 rowParent 派生）；单元格输出 `data-editor-id`/`data-viewer-id` 前端挂接点；容器 `overflow-x:auto` + 窄屏媒体查询。排序语义与消费契约见 `nop-format/nop-excel/src/test/html-acceptance/`。

**HTML 分页打印（已生效）**：html 渲染类型在 sheet 配置了 pageSetup 时输出分页打印结构——`@page`（纸型/页边距取模板打印设置）、按行高装箱的 `.xpt-page` 分页节（每节复制表头行进 thead）、页脚 `第 &P / &N 页`（&P/&N 语义对齐 PDF）。已知限制：忽略 fit/scale；横向分页与关键列重复未做；验收走 headless 浏览器截图 golden（`nop-format/nop-excel/src/test/html-acceptance/`）。

**数据级安全（已生效）**：行级过滤 + 列级脱敏（`IReportDataSecurityProvider`，规则在 VFS `/nop/report/data-security.yaml`）：

- 行级过滤：三个 JDBC/ORM 取数标签在取数前把原 SQL 包装为 `select * from ( 原SQL ) _rpt_sec where <条件>`，条件中的 `{userId}/{tenantId}/{deptId}` 占位符从当前用户上下文解析并以 `?` 参数绑定；无用户上下文且规则引用占位符 → fail-closed `nop.err.report.data-sec.*`
- 列级脱敏：单元格字段取值时按规则掩码（phone/name/idCard/full）；数据集与聚合不受影响，但**被公式引用的字段不得配置 mask**（公式对掩码文本求值行为未定义）
- `UseQueryDataSet`（query bean 路径）不在行过滤范围（走平台 ORM 数据权限 `IDataAuthChecker`）

**权限接线（已生效，入口边界模式）**：授权在**系统入口**（对外 BizModel action：渲染/下载/表单等）显式调用 `NopReportAuthChecker`（bean `nopReportAuthChecker`），**内部引擎（`IReportEngine`/xpt-rt 标签）不感知授权**，保持可嵌入式复用：

- 消费 `NopReportDefinitionAuth`（按角色）与 `NopReportDatasourceAuth`（按角色，入口触发数据源访问时判定），用户角色取自 nop-auth 会话上下文
- 报表模板路径与 `NopReportDefinition.rptText` 精确匹配——**rptText 约定存模板 VFS 全路径**
- 授权语义：定义/数据源未配置 auth 记录 → 不限制；配置了 → 用户角色须命中任一；无用户上下文且配置了记录 → fail-closed 拒绝（调度等系统调用方须显式建立用户上下文）；豁免角色由配置项 `nop.report.auth.exempt-roles`（逗号分隔，默认空）指定
- 权限记录经 ORM 关联加载（`reportAuths`/`datasourceAuths`）；入口 action 建议标注 `@SingleSession`
- 引擎作嵌入式库使用（无 checker bean）时不启用检查；子报表由主模板授权覆盖，不逐层重复检查

**已知限制勘误**：

- ~~报表/数据源权限预留未接线~~（已消除：渲染入口与取数标签现强制授权，见下「权限接线」）

**补强方向与契约**：缺失能力的目标契约已在 AI 开发记录区的设计目录（nop-report 子目录）裁定为草案，落地以报表能力 roadmap 编排为准：

| 契约 | 摘要 |
|------|------|
| 参数模型 | 声明内嵌 XPT、`${param.*}` 统一取值、查询表单 schema、参与缓存键 |
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
| NopReportResultFile | `nop_report_result_file` | 缓存的报表结果文件（已接线——`NopReportCache` 接口消费，见上「结果文件缓存」） |

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-report-core` | 报表核心引擎 |
| `nop-report-pdf` | PDF 输出支持 |
| `nop-report-docx` | DOCX 输出支持 |
| `nop-report-api` | API DTO |
| `nop-report-dao` | ORM 实体与 DAO |
| `nop-report-service` | 业务逻辑 |
| `nop-report-web` | Web 层与管理页面 |
| `nop-report-ext` | 扩展功能 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-report/model/nop-report.orm.xml` |

## 相关文档

- `../02-core-guides/reporting-and-notification-integration.md`
- `../reusable-modules-overview.md`
