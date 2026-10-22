# nop-report 功能分析与外部报表引擎对照补强建议

> Status: converged（当前能力面与已知限制的 canonical owner 已移交 `docs-for-ai/03-modules/nop-report.md`「当前能力面与已知限制」节；本文档保留 25 维跨引擎对照矩阵与 P0/P1/P2 建议作为调研证据，矩阵数字不再随实现演进更新）
> Date: 2026-10-17
> Scope: `nop-report/*`（core / pdf / docx / ext / service / web / api / dao），对照 `~/sources/report-engines` 下载的商业与开源报表引擎
> Conclusion: nop-report 的「Excel 单元格 + 展开」模型在中文式网格/套打/多源报表上优于传统 band 模型，工程架构（同一展开逻辑驱动多渲染器、可逆计算）也领先；主要缺口集中在 **参数化查询表单、权限接线、HTML 分页打印保真、交互式分析、导出格式广度、填报/数据回写** 六类。建议按 P0/P1/P2 分级补强，而非改换 band 模型。
> Reference library: `~/sources/report-engines/`（clones 与 doc mirrors，清单见该目录 `README.md`）

---

## Context

- 要回答：nop-report 已有哪些能力？相对主流商业/开源报表引擎还缺什么？哪些补强最值得做？
- 涉及模块：`nop-report/nop-report-core`（引擎/展开/数据集/公式）、`nop-report-pdf`、`nop-report-docx`、`nop-report-ext`、`nop-report-service|dao|api|web`（运行时 CRUD）；相邻的 `nop-datav`（看板/BI/D5 定时报告/D6 AI）作为边界参考。
- 约束：保持 Excel 单元格 + 展开 + 可逆计算模型；不引入 band 模型；不引 GPL/AGPL 运行时依赖。

## Analysis

### 1. nop-report 现状（基于源码）

#### 1.1 架构分层

| 子模块 | 职责 | 关键类 |
|--------|------|--------|
| `nop-report-core` | 模板加载、展开引擎、xlsx/html 渲染、数据集、公式 | `IReportEngine`/`ReportEngine`、`ExpandedSheetGenerator`、`ReportDataSet`、`ExcelFormulaParser` |
| `nop-report-pdf` | PDFBox 直接渲染、字体、分页、套打隐藏 | `PdfReportRendererFactory`、`PdfSheetRenderer`、`FontManager` |
| `nop-report-docx` | Word 表格生成（xlib `GenXptTable` 路径） | `XptWordTableRenderer`、`XptWordTableParser` |
| `nop-report-ext` | 二维码/条形码等扩展函数 | `ReportExtFunctions.QRCODE` |
| `nop-report-service` / `dao` / `api` / `web` | 运行时实体 CRUD + AMIS 页面 | `NopReportDefinitionBizModel` 等 |

引擎入口固定为 `IReportEngine`（`ReportEngine`）：

```java
ExcelWorkbook getXptModel(String path);                          // 加载 .xpt.xlsx / .xpt.xml
ITemplateOutput getRenderer(String path, String renderType);     // html/xlsx/shtml/pdf + docx(独立路径)
ExcelWorkbook generateFromXptModel(ExcelWorkbook, IEvalContext); // 展开填充
ExcelWorkbook buildXptModelFromImpModel(String impModelPath);    // 从导入模板自动生成
```

> `IReportEngine` 按 `renderers` map 分发渲染器；`report-defaults.beans.xml` 注册 `xlsx`/`html`/`shtml`，`nop-report-pdf` 注册 `pdf`。`XptConstants.RENDER_TYPE_WORD` 存在但**未注册为 renderType**——Word 输出走 `nop-report-docx` 的 `docx-gen.xlib`（docx 模板内嵌 `GenXptTable`），只支持「把一个 sheet 渲染成 Word 表格」，不支持自由段落/页眉页脚。

#### 1.2 模板模型（XPT）

模板是 Excel 工作簿（`.xpt.xlsx` 推荐，`.xpt.xml` 为序列化形式），三个配置入口：

1. **单元格文本**（`XptModelInitializer`）：`*=field`、`*=^ds!field`（向下展开）、`*=>ds!field`（向右展开）、`*=field@expandExpr`、`${XPL 表达式}`、`=SUM(D3)`。
2. **单元格批注**（`ExcelToXptModelTransformer`）：多行 `key=value` → `XptCellModel`。
3. **配置 sheet**：`XptWorkbookModel` / `<sheetName>-XptSheetModel` 承载 `beforeExpand`/`afterExpand`/`sheetNameExpr`/`loopVarName`/`ext:namedStyles`。

`XptCellModel` 指令集（权威定义 `nop-format/nop-excel/.../_gen/_XptCellModel.java`）：

| 类别 | 指令 |
|------|------|
| 展开 | `expandType`(r/c)、`expandExpr`、`field`、`ds`、`rowParent`/`colParent`（父子层级嵌套）、`expandInplaceCount`、`expandMaxCount`/`expandMinCount`、`expandOrderBy`、`keepExpandEmpty`、`rowExtendForSibling`/`colExtendForSibling` |
| 过滤/条件 | `rowTestExpr`、`colTestExpr`、`processExpr` |
| 取值/显示 | `valueExpr`、`formatExpr`、`dict`、`domain`、`exportFormattedValue`、`exportFormula` |
| 交互/样式 | `styleIdExpr`、`linkExpr`、`editorId`、`viewerId` |

#### 1.3 展开引擎

`ExpandedSheetGenerator` + `CellRowExpander`/`CellColExpander`/`CellNullExpander`/`TableExpander`：行列双向展开，父子层级嵌套分组，兄弟格拉伸控制，动态行列，动态 sheet（`sheetNameExpr` + loop）。**同一展开结果驱动所有渲染器**。

#### 1.4 数据集

- **程序构造**：`ReportDataSet` 提供 `group/groupBy/groupAndSortBy/where/filter/countIf/sort/sortBy/first/last/field/select/map/sum/sumBy/avg/avgBy/max/maxBy/min/minBy`；`DynamicReportDataSet`、`KeyedReportDataSet` 支持分组键。
- **声明式标签**（`xpt-rt.xlib`，在 `beforeExpand` 使用）：`UseJdbcDataSet`、`JdbcDataSet`（流式，保持连接）、`UseOrmDataSet`（含 `disableLogicalDelete`）、`UseQueryDataSet`（query-bean）。

#### 1.5 公式与函数

- `ExcelFormulaParser` 解析 `=SUM(D3)`；`CellRangeExecutable`/`CellLayerCoordinateExecutable`/`FilterCellSetExecutable` 执行区域；`exportFormula` 决定 xlsx 是否保留公式。
- 函数（`ReportFunctions`，约 20 个）：`SUM/PRODUCT/SUMPRODUCT/COUNT/COUNTA/AVERAGE/MIN/MAX/NVL/PROPORTION/RANK/ACCSUM/STDEV/VAR/COUNTIF/SUMIF/CONCAT/IF/MEDIAN`。

#### 1.6 图表

`ExpandedSheetChartGenerator`：图表动态绑定（`chartTestExpr`、系列 `dataCellRef/nameCellRef/catCellRef` 表达式、轴 `dataCellRef`、标题表达式）——图表引用展开后的单元格区域。

#### 1.7 渲染与输出

| 输出 | 实现 | 关键能力 |
|------|------|----------|
| xlsx | `XlsxReportRendererFactory` | 保留样式/合并/图片/公式；`print=false` 保留 |
| html | `HtmlReportRendererFactory` | 表格 HTML + scoped CSS；套打背景图作 CSS background，`@media print` 排除 |
| shtml | `SimpleHtmlReportRendererFactory` | 简化 HTML |
| pdf | `PdfReportRendererFactory`（PDFBox 直接画） | 字体回退、行列双向分页、重复表头/关键列、默认页码、图片裁剪 |
| word 表格 | `nop-report-docx` `GenXptTable` | 仅表格 |

- **PDF 字体**：base-14 → VFS → 字体目录（`nop.report.pdf.font-dirs` + 系统目录，支持 `.ttc`）→ `/fonts/default.ttf`；按字符回退 CJK（`nop.report.pdf.fallback-font`）。
- **PDF 分页**（`ai-dev/design/nop-report/pdf-pagination.md`）：`repeat-header-rows`（默认 1）、`repeat-key-columns`（默认 1）、`default-page-footer`（默认 true）。

#### 1.8 套打（Overlay）

背景图 `ExcelImage.print=false`：HTML `@media print` 排除；PDF 按 `print` 标志排除；xlsx 保留 `fPrintsWithSheet="0"`。动态文字 `${entity.field}` 对位。

#### 1.9 导入模板自动生成

`ExcelTemplateToXptModelTransformer` 按 `imp.xml` 自动合成 `XptCellModel` 指令；`ExcelReportHelper.saveXlsxObject/saveXpxObject/downloadXlsx/getHtmlForXlsxObject` 支持 bean 一键导出。

#### 1.10 运行时管理与权限

实体：`NopReportDefinition`、`NopReportDataset`、`NopReportSubDataset`、`NopReportDatasetRef`、`NopReportDatasource`、`NopReportDefinitionAuth`、`NopReportDatasourceAuth`、`NopReportResultFile`。`nop-report-web` 仅实体 CRUD AMIS 页面——无模板设计器、参数表单、预览页。

> **已知缺口**：角色级报表/数据源权限表可配置但**渲染与取数链路当前不校验**（P1-3.6-002）。

#### 1.11 边界（nop-datav）

`nop-datav` 是报表之上的看板/BI 层：D1 看板运行时、D2 筛选联动、D3 权限分享导出、D4 大屏、D5 定时报告 + 告警 + 邮件/IM、D6 AI/ChatBI。「定时报告/告警/AI 问数」应落在 nop-datav，不重复计入 nop-report 缺口。

### 2. 与外部报表引擎的能力矩阵

图例：✅ 完整 ／ 🟡 部分或受限 ／ ❌ 缺失

| 能力维度 | nop-report | Jasper/BIRT/Pentaho | FineReport/润乾 | UReport2 | JimuReport | SSRS/Crystal/Telerik |
|----------|:----------:|:-------------------:|:---------------:|:--------:|:----------:|:--------------------:|
| 模板范式 | **Excel 单元格+展开** | band | Excel 单元格 | Excel 单元格 | Excel 单元格 | band/自由布局 |
| 单元格/交叉展开 | ✅ | 🟡 | ✅ | ✅ | ✅ | 🟡 |
| 父子层级嵌套展开 | ✅ | 🟡 | ✅ | ✅ | ✅ | 🟡 |
| Web 可视化设计器 | ❌ | 🟡 | ✅ | ✅ | ✅ | ✅ |
| 参数/查询表单（声明式+控件） | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 报表/数据源权限接线 | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 数据级行/列权限 | ❌ | 🟡 | ✅ | 🟡 | 🟡 | ✅ |
| 图表 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 条件格式（规则） | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 钻取/联动/超链接 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 折叠/树/排序交互 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 子报表/主从 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 数据填报/回写 | ❌ | 🟡 | ✅ | ✅ | ✅ | 🟡 |
| 导出格式广度 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ |
| PDF 分页与表头重复 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| PDF 高级（加密/签名/水印/PDF-A/可访问） | ❌ | 🟡 | ✅ | 🟡 | 🟡 | ✅ |
| 套打（预印表单） | ✅ | 🟡 | ✅ | 🟡 | ✅ | ✅ |
| 条码/二维码 | ✅ | 🟡 | ✅ | ✅ | ✅ | ✅ |
| 大数据/流式数据集 | 🟡 | 🟡 | ✅ | 🟡 | 🟡 | ✅ |
| 数据集/结果缓存 | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 定时报送/渠道投递 | ➖ nop-datav D5 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 脚本/表达式 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 多语言/i18n 模板内容 | ❌ | 🟡 | ✅ | ✅ | ✅ | ✅ |
| 模板版本管理 | ❌ | 🟡 | ✅ | 🟡 | 🟡 | ✅ |
| 嵌入式/API/SDK | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| AI/自然语言问数 | ➖ nop-datav D6 | 🟡 | ✅ | ❌ | ✅ | 🟡 |

### 3. 差距分析与改进建议

> 判定原则：保留 Excel 单元格 + 展开 + 可逆计算核心模型，只补「商业引擎有、nop 缺、实际业务高回报」的能力。不引入 band 模型，不重写设计器内核。

#### P0 —— 影响可用性/安全性的硬缺口

1. **接线报表/数据源权限（安全缺陷）**：权限表可配置但渲染/取数链路不校验。在取数与渲染入口统一插入授权检查，复用 `nop-auth` 角色/资源权限模型。参考 FineReport「数据决策系统」、`birt` repo security。
2. **声明式报表参数模型 + 查询表单**：当前参数只能由应用硬塞 `IEvalScope`；建议引入 `ReportParameterModel`（name/type/label/required/default/候选来源 SQL/控件类型）→ 生成 AMIS 查询表单，模板 `${param.xxx}` 统一取值。对应 FineReport「参数界面」、润乾「参数模板」、Jasper「Parameters」、UReport「参数」。
3. **HTML 分页打印保真**：html/shtml 是普通表格流，浏览器打印无页眉页脚/重复表头/分页控制。增加 `@page`/分页 CSS 与分页预览，复用 PDF 的 `repeat-header-rows`/`repeat-key-columns` 语义；可借鉴 `openhtmltopdf`/`flyingsaucer`。
4. **结果/数据集缓存端到端落地**：`NopReportResultFile` 实体存在但引擎链路未见强制使用。把缓存命中插入 `getRenderer`/`generateFromXptModel` 前置，按「报表+参数+数据版本」键控，提供 TTL/失效与异步生成。

#### P1 —— 显著提升专业度与竞争力

5. **交互式分析**：`linkExpr` 三端渲染落地 + 钻取（主→明细）+ 参数联动 + 列排序/折叠/条件可见。
6. **条件格式规则引擎**：把 `styleIdExpr` 扩展为可声明规则集（表达式/区间+样式），支持数据条/色阶/图标集。
7. **导出格式扩展**：CSV、图片（PNG）、ODS、RTF、原生 xlsx（图表/透视还原）；PDF 高级项（水印/印章、加密、数字签名、PDF/A、Tagged PDF）。
8. **正式子报表/报表组合模型**：主从、跨模板组合、片段复用与参数传递（当前靠层级展开，无法引用另一模板）。
9. **数据级安全**：按角色/部门/用户注入行级过滤与列级脱敏。
10. **大数据集与流式渲染**：数据集分页/游标 + 渲染侧增量写出；为 `JdbcDataSet` 流式补齐 xlsx/pdf 增量路径。
11. **模板 i18n**：模板文本/格式/字典按 locale 切换。

#### P2 —— 长期与生态

12. **Web 可视化设计器（可选增强）**：基于 `univer`/`luckysheet`（在线表格）+ 单元格属性面板 + 参数表单设计器；定位「Excel 之外的轻量在线编辑」，不替代 Excel 工作流。
13. **直接打印服务**：服务端直连打印（打印机/纸盒/双面/套打偏移）。
14. **OLAP/多维数据集集成**：接入 Calcite/Mondrian 类语义层（本地已有 `bi/calcite`）。
15. **移动端响应式**；16. **Word 输出增强**（段落/页眉页脚/图表混排，`docx4j` 已在本地）；17. **模板版本管理**。

#### 不建议做（防重建）

- ❌ 不改成 band 模型——单元格展开对中文式网格/套打/多源报表更自然。
- ❌ 不自研在线 Excel 网格内核——复用 `univer`/`luckysheet`。
- ❌ 不在 nop-report 重复「定时报送/告警/AI 问数」——属 nop-datav D5/D6。
- ❌ 不引 GPL/AGPL 报表平台（JimuReport/Metabase 等）作为运行时依赖，仅作参考。

## Conclusion

- **优势（保持）**：Excel 原生模板 + 单元格展开 + 可逆计算；一份展开逻辑驱动 xlsx/html/pdf（+word 表格）；套打三端一致；`imp.xml` 自动生成；纯 Java、易嵌入 Nop IoC。
- **最高 ROI 补强顺序**：①权限接线 → ②参数模型+查询表单 → ③HTML 分页打印保真 → ④结果/数据集缓存 → ⑤交互式分析 → ⑥导出格式与 PDF 高级能力。
- **后续工作**：将 P0 拆为 `ai-dev/plans/`；如需先定方案，在 `ai-dev/design/nop-report/` 增设「参数模型」「权限接线」「HTML 分页」设计契约。

## Open Questions

- [ ] `NopReportResultFile` 结果缓存当前是否在任意链路被实际写入/读取？
- [ ] `linkExpr`/`editorId`/`viewerId` 在 HTML 渲染层是否有对应输出，还是仅停留在模型？
- [ ] Word 输出是否规划注册为统一 renderType，还是长期保持 docx 模板 + xlib 路径？
- [ ] D5 定时报告与 nop-report 的渲染职责边界是否已在 nop-datav 设计中完全收口？

## References

- 本地资料：`~/sources/report-engines/README.md`、`~/sources/jasperreports`、`~/sources/bi/*`、`~/sources/esProc`
- `docs-for-ai/03-modules/nop-report.md`、`docs-for-ai/03-runbooks/generate-report.md`、`docs-for-ai/02-core-guides/reporting-and-notification-integration.md`
- `ai-dev/design/nop-report/pdf-pagination.md`、`ai-dev/design/nop-report/pdf-font-strategy.md`、`ai-dev/design/nop-datav/*`
- `docs-for-ai/04-reference/source-anchors.md`（REPORT-001~003、RPT-001）
