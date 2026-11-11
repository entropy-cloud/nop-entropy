# docx roadmap 端到端验收报告（WI11）

> Status: closed（roadmap 收口验收记录；时效性证据）
> Date: 2026-11-11
> Scope: ai-dev/backlog/nop-docx-document-model-roadmap.md WI0–WI11（plan 2515–2526）

## 目标逐项验收（roadmap Purpose 四目标）

### 1. docx → OfficeDocModel（解析）

- WI3 全面解析 + WI4 模板标注：八类块/四类行内/多节/styles 容器/图片资产/`c:if`/`c:for`/`expr:`/`xpl:`/`tpl_expr:`/`xpt:table` 全覆盖。
- 锚点：`TestDocxCorpusParsing` 19 用例（11 语料样本全链路 + 容错）、`TestTemplateAnnotationParsing` 10 用例、`TestOfficeDocModelParser` 19 用例。
- 结论：**PASS**。

### 2. OfficeDocModel → docx（生成）

- WI5 生成端重写：与解析面对偶全覆盖；round-trip 红线验收 `TestDocxRoundTrip` 18 用例（15 语料样本 docx→模型→docx→模型 双向语义等价，类型化比较器逐字段）。
- 结论：**PASS**（已知不保真项显式豁免：水印 VML/块级批注/命名样式字体细节/混排图片）。

### 3. OfficeDocModel → PDF（渲染）

- WI6 渲染器：流式排版（layout/paint、段落按行/表格按行跨页）、run 全装饰、CJK 字体回退、每节页面几何与页眉页脚、超链接 annotation、背景。
- 锚点：`TestOfficeDocPdfRender2a` 16（layout 单测 + 像素探针）、`2b` 14（表格/图片/列表/链接/多节）、`3E2E` 3（writer→docx→parser→render 端到端 + LibreOffice 对照页数/关键文本页 + golden）。
- Cross-Cutting 3（实际 PDF + 逐页 PNG + 线宽/颜色/布局断言）：由文本抽取 + RegionProbe 像素探针满足（入库可复现主体）；golden 为本机辅助。
- 结论：**PASS**。

### 4. 模型驱动模板（动态展开 + 占位替换）

- WI7 展开：`OfficeDocModelExpander` 纵向/横向/分组（xpt 标记 `^/>/@` 复用 NopReport 引擎）+ doc/page/块三级循环与过滤；`TestOfficeDocModelExpander` 15 用例。
- WI8 绑定：`DocImageBinder` chart（echarts opt-in）/qr（解码回读强断言）/code128；`TestDocImageBinding` 14 用例。
- WI9 统一出口：`WordDocTemplates.generate`（动态 docx → 展开生成 docx 端到端）、`WordDocPdfTemplates.renderPdf`（展开模型 → PDF 文本抽取）；`TestWordDocTemplates` 7 + `TestWordDocPdfTemplates` 4。
- 结论：**PASS**。

## 最终全量测试（2026-11-11 实跑）

| 模块 | 用例 | 结果 |
| --- | --- | --- |
| nop-ooxml-docx（-am 含上游 doc-model 25） | 98 | 0 失败 0 skip |
| nop-report-docx（-am 含 report-pdf 147） | 42 | 0 失败 0 skip |
| nop-report-pdf | 147 | 0 失败 0 skip |

## 已知限制与 Deferred 汇总

| 项 | 归属 | successor |
| --- | --- | --- |
| 首页不同页眉页脚（titlePg） | 应进模型，延期 | 需求驱动立项（模型+三面接线） |
| numbering.xml 合成（列表字形） | WI11 前补或按需 | 需求驱动 |
| 水印 VML | 模型不承载 | 渲染目标按需（PDF stamp 已有） |
| 块级批注 | 模型无承载字段 | 需求驱动 |
| 表格内图片绑定 | 单元格不承载图片 | 模型扩展 |
| 单调用 PDF 出口（bytes→PDF） | 依赖环限制 | 方案 c 依赖裁定 |
| 字体/换行通用件下沉 nop-pdf | 治理重构 | 独立治理项 |
| 列表内细粒度 item 循环、嵌套表 xpt、linkExpr、xpt 批注完整语义 | 各 plan Deferred | 按需求 |

## 文档收口核对

- owner doc `02-core-guides/word-document-model.md`：§1–§12 全契约 + 能力矩阵（解析/生成列已翻转）——抽查与 live 一致。
- INDEX.md 快速路由含 word-document-model 条目；source-anchors WDM-001..007 路径全部真实。
- module-groups.md：nop-office-model/nop-office-doc-model/nop-ooxml-docx 登记 + nop-report 行更新（本计划补）。
- 设计决策：`ai-dev/design/word-editor/online-word-model-design.md` §13–§21（结构/样式/解析/图片绑定/统一出口/PDF 渲染/模板展开各层决策）+ `springreport-alignment.md`（WI10 对齐裁定）。

## Milestone 状态

M0 基线与验收基础设施 / M1 模型与样式定稿 / M2 docx 双向转换可用 / M3 PDF 输出可用 / M4 模板动态展开可用 / M5 统一出口与 springreport 能力对齐 / M6 端到端验收通过——**全部达成**。
