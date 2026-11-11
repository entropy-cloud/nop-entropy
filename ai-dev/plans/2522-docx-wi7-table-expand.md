# 2522 docx roadmap WI7：动态表格与数据绑定展开

> Plan Status: completed
> Last Reviewed: 2026-11-11
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI7）+ docs-for-ai/02-core-guides/word-document-model.md §7
> Related: ai-dev/plans/2519（模板标注）、2520（生成）、2521（PDF）

## Purpose

模板表格按数据集展开（纵向/横向/分组）+ doc/page 级循环，展开产物为 OfficeDocModel（可继续走 docx 生成 WI5 或 PDF 渲染 WI6），复用 `nop-report-docx`/NopReport 既有展开引擎。

## Current Baseline

- 既有 xpt 展开管线完整（live code）：`XptWordTableParser.compileTable` 流水线 = WordTable → `WordTableToExcelTableTransformer` → ExcelTable → `ExcelToXptModelTransformer`（cell 文本 `*=^ds!field@expr` 标记：`^`纵向/`>`横向/@分组聚合）→ `XptModelInitializer`（编译表达式）→ `XptWordTableRenderer`（`ExpandedSheetGenerator`+`TableExpander` 引擎展开 → ExpandedTable → w:tbl 回写）。展开引擎含父子格依赖与 `DynamicReportDataSet` 分组交集。
- WI4 已落地：OfficeDocModelParser 识别 xpt:table 批注 → `table.model.renderType="xpt"`；块级 c:for/c:if → 各层 model（testExpr/beginLoop/loopVarName/loopIndexName/loopItemsName 编译为 IEvalAction/IEvalPredicate）。
- 模型字段与 xpt 语义对应：WordTableCellTemplateModel 有 name/field/ds/valueExpr/formatExpr；表格单元格文本即 xpt 标记载体。
- 既有入口（`XptWordTemplateParser`/`WordTemplateParser`）输出是 docx 文件（XPL 生成期回写 XML），不产出 OfficeDocModel；DocxCorpusBuilder 可合成含 xpt:table 批注的表格语料（WI4 已实现 templateAnnotationDoc）。
- 依赖方向：nop-report-docx → nop-ooxml-docx → doc-model；nop-report-docx → report-core。展开器落 nop-report-docx 零新依赖。

## Goals

- `OfficeDocModelExpander`（nop-report-docx 新类）：入口 `expand(OfficeDocModel doc, IEvalScope scope)` → 展开后的 OfficeDocModel：
  - 表格：renderType="xpt" 的 WordTable 复用既有管线展开（cell 文本 xpt 标记），展开结果回填为新 WordTable 块；
  - 表格级 testExpr 过滤；表格内 col/row 的 hiddenExpr/testExpr；
  - doc/page 级 loop（loopVarName/loopItemsName/beginLoop）与块级 testExpr：循环复制块序列、条件过滤；
  - 页面级 testExpr 过滤整页。
- 展开后的模型可直连 WI5 docx 生成与 WI6 PDF 渲染（验收即走这两条出口）。
- 端到端测试：语料（xpt 表格 + c:for 列表/段落）+ scope 数据 → expand → 断言展开模型结构 → 生成 docx → 再解析断言；PDF 链路冒烟。

## Non-Goals

- 不改既有 `XptWordTemplateParser`/`WordTemplateParser`/`XptWordTableRenderer`/展开引擎（`TableExpander`/`ExpandedSheetGenerator`/`DynamicReportDataSet`）的行为。
- 图表/二维码占位替换（WI8）；统一出口与模板编译（WI9）；springreport 对齐（WI10）。
- 前端设计器；xpt 标记语言之外的第三种标记语法。

## Scope

### In Scope

- `nop-report/nop-report-docx`：新增展开器类（复用既有管线类，不改其行为）+ 测试（本模块自造含 xpt 标记的模型样本——DocxCorpusBuilder 在 nop-ooxml-docx test 不可跨模块）。pom 增 **test** 依赖 `nop-report-pdf`（Phase 3 PDF 出口验收用；compile 依赖面保持零新增）。
- owner doc / 设计文档决策 / roadmap / 日志。

### Out Of Scope

- 见 Non-Goals；另：nop-ooxml-docx 与 doc-model 零改动；既有渲染/生成/解析行为零变化。

## Execution Plan

### Phase 1 - 展开层决策收敛

Status: completed
Targets: `ai-dev/design/word-editor/online-word-model-design.md`

- Item Types: `Decision`

- [x] 决策记录（设计文档「模板展开层决策」节，位于 PDF 渲染层决策之后）：
  - E1 展开路线：表格走既有 xpt 管线（复用 `WordTableToExcelTableTransformer` + `ExcelToXptModelTransformer` + `XptModelInitializer` + `ExpandedSheetGenerator`/`TableExpander`，**不**走 `XptWordTableRenderer` 的 w:tbl XML 回写后半，改为 ExpandedTable → 新 OfficeDocModel WordTable 回填）；标记语法沿用 cell 文本 `*=^ds!field@expr`（与旧链路 100% 同语义）。**样式面裁定：模型层回填保 Model 可承载的面——行列结构/合并（mergeAcross/mergeDown）/单元格文本/列宽/行高/styleId；旧链路经 xpt:node XNode 恢复的 XML 形态样式（trPr/tblHeader/cantSplit/run rPr）不在 doc-model 承载范围，天然不回填（非降级，是模型边界）**。拒绝：模型加 expandType 字段（模型变更 + 双语法并存）。
  - E2 scope 与编译上下文：数据放 IEvalScope 变量（如 `data`/`order`），`XptRuntime(scope)` 经既有 `makeDs` 包装取数（scope 只需放数据变量）；doc/page loop 的 items 经 loopItemsName 从 scope 取。**编译上下文裁定：展开期新建 `XLangCompileTool`（同 `XptWordTableParser.fromCompileScope` 的构造方式——`new XLangCompileTool(compileScope)`），`XptModelInitializer(cp)` 在展开期编译 xpt 单元格表达式；WI4 编译的块级 model 表达式（testExpr/loop 等）已是 IEvalAction/IEvalPredicate 直接求值，无需重复编译**——两条编译路径并存是既有管线形态。
  - E3 循环语义：page.model 的 loop 复制**该页 body 块序列**（页眉页脚不在 body 内，天然完整）；doc.model 的 loop 复制**全部 pages**；块级 c:for 在块序列层展开（与 WI4 列表语义一致：重复整个块）。与 `deleteAllAfterConfigTable` 的交互裁定：展开先执行 loop 复制与块展开，doc.model 的 deleteAllAfterConfigTable 语义属生成期（WI5 writer 未实现该字段），展开器不处理——组合行为登记为 watch-only。
  - E4 展开时机：expand 是模型→模型的纯变换（无副作用）；renderType="xpt" 且展开后表格保留 model（含 renderType）供后续链路识别。
  - E5 容错：xpt 表格展开失败（表达式编译错/数据集缺失）fail-fast 报错；普通表格（非 renderType="xpt"）不展开原样保留。
- [x] check-doc-links --strict 退出码 0。

Exit Criteria:

- [x] 设计文档含 E1–E5 决策及拒绝理由。
- [x] check-doc-links --strict 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 展开器实现

Status: completed
Targets: `nop-report/nop-report-docx/src/main/java/io/nop/report/docx/expand/`

- Item Types: `Feature`

- [x] `OfficeDocModelExpander`：遍历 pages/blocks；表格 renderType="xpt" → 经 `WordTableToExcelTableTransformer`+`ExcelToXptModelTransformer`+`XptModelInitializer`+`ExpandedSheetGenerator` 展开 → ExpandedTable 回填为 OfficeDocModel WordTable（行列/合并/文本/样式保真映射，裁定细节落实现报告）；块级 c:for/testExpr 展开；doc/page loop。
- [x] 展开会话：scope 构造 helper（数据变量）+ 展开期 `XLangCompileTool`（E2 裁定）；块级 model 表达式（WI4 已编译）直接求值，xpt 单元格表达式展开期编译。
- [x] 便捷重载：`expand(OfficeDocModel, Map<String,Object> dataScope)`。
- [x] 错误处理：两档惯例，fail-fast（E5）；不静默跳过任何模板标注。

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-docx -am` 全绿（既有入口类 git diff 为零即零回归证据形式；既有两个测试类通过）。
- [x] 无静默跳过：展开失败/缺数据集 fail-fast 有测试。
- [x] 实现期裁定（ExpandedTable→模型映射细节、循环复制语义）落设计文档节 + 实现报告。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 展开端到端测试

Status: completed
Targets: `nop-report/nop-report-docx/src/test/java/io/nop/report/docx/expand/`

- Item Types: `Proof`

- [x] 模型直构样本（本模块内构造 OfficeDocModel：renderType="xpt" 表格含 `*=^data!field` 标记、c:for 段落、c:if 段落、doc/page loop）：
  - 纵向展开：N 行数据 → 表格 N 数据行 + 标题行；
  - 横向展开：`>` 标记 → 列扩展；
  - 分组：`@` 聚合表达式 → 分组行；
  - testExpr false 的表格/段落/页面被过滤；
  - doc loop：M 份数据 → 页面块序列重复 M 次；
  - 页面 loop 同理；
  - 列表 c:for（loopItemsName）重复列表块。
- [x] 出口接线：展开模型 → `OfficeDocModelWriter` → docx → 再解析断言展开结果持久化；展开模型 → `OfficeDocPdfRenderer` → PDF 文本抽取含展开数据（两个出口各至少一条，证明展开产物真实可用）。
- [x] 容错：数据集缺失/表达式错 fail-fast 断言；普通表格不被误展开。
- [x] 既有 `TestXptWordTemplate`/`TestXptWordTableRenderer` 零回归。

Exit Criteria:

- [x] 纵向/横向/分组/doc loop/page loop/块循环六类展开断言齐全。
- [x] **端到端验证**：展开 → docx 生成 → 再解析；展开 → PDF → 文本抽取，两条出口链路通过。
- [x] `./mvnw test -pl nop-report/nop-report-docx -am` 全绿。
- [x] owner doc 模板标注/动态展开行更新；`ai-dev/logs/` 已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI7 勾选 + Last updated 同步。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] `./mvnw test -pl nop-report/nop-report-docx -am` 绿；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿（上游零改动确认）。
- [x] check-doc-links --strict 退出码 0。
- [x] scan-hollow-implementations --module nop-report/nop-report-docx --severity high 退出码 0。
- [x] check-plan-checklist --strict 退出码 0。
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

### expand 结果与输入共享 assets 列表

- Classification: `watch-only residual`（本计划实现时未发现；WI8 plan 2523 审查发现——绑定器注入会污染输入模板）
- Why Not Blocking Closure: WI7 的出口链只读不注入 assets；缺口由 successor WI8 的 assets 副本策略修复（B2）。
- Successor Required: `yes`
- Successor Path: ai-dev/plans/2523-docx-wi8-image-binding.md（B2 assets 副本策略）。

### 循环副本的块实例共享

- Classification: `watch-only residual`
- Why Not Blocking Closure: 块循环副本共享同一块实例（模型无深拷贝工具，copyTo 为平台浅拷贝约定）；出口链（docx 生成/PDF 渲染）只读不变形。若未来出现循环后逐副本变异的场景需引入深拷贝。
- Successor Required: `no`
- Successor Path: —

### deleteAllAfterConfigTable 生成期语义

- Classification: `watch-only residual`
- Why Not Blocking Closure: E3 裁定归生成期（WI5 writer 未实现该字段），展开器不处理。
- Successor Required: `no`
- Successor Path: —

### WordTableCellTemplateModel 原生 ds/field 字段直连展开（不经文本标记）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 文本标记与旧链路语义 100% 对齐且零模型变更；原生字段直连需在 XptModelInitializer 之外新写解析映射，属优化。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: WI7 三 Phase 完成：OfficeDocModelExpander（doc/page/块三级 testExpr 过滤与 loop 复制；renderType=xpt 表格走既有管线展开，ExpandedTable 映射回模型 WordTable 三级保真，模板位 cell model 按原坐标对位复制；非 xpt 表格原样保留；缺数据集/非法标记 fail-fast）。14 用例（纵/横/分组/三级过滤/三级循环/两条出口链：展开→docx 生成→再解析、展开→PDF→文本抽取）。附带基线修复：XptWordTableParser 两处 override 签名对齐 WI3 的 WordTableParser 钩子变更（该模块自 WI3 起编译不过，实证裸基线失败）。nop-report-docx 19 + report-pdf 143 + docx 98 全绿，既有入口类 git diff 仅签名对齐。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_7acb76f5-5a88-4225-b23d-91646ceb7556）
- Audit Session: agent_7acb76f5-5a88-4225-b23d-91646ceb7556
- Evidence:
  - Exit Criteria 逐项：Phase 1 PASS（设计文档 §19 五条与实现一致）；Phase 2 PASS（三级过滤/循环、xpt 管线复用调用链 L278-294 与 E1 逐类一致、映射 L304-355、非 xpt 原样、fail-fast）；Phase 3 PASS（14 用例实读：纵向 4 行 + 文本值断言、横向列扩展、分组组值与聚合、出口链 docx 再解析与 PDF 文本抽取均真实、入参零修改双断言、ERR_XPT_MISSING_VAR_DS 错误码）。
  - Closure Gates：`./mvnw test -pl nop-report/nop-report-docx -am` 独立实跑 exit 0（20 用例含 audit 建议新增，0 skipped）；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` exit 0（98）；check-doc-links --strict exit 0；scan-hollow 0 发现；scope（既有 Xpt* 类仅 XptWordTableParser 签名对齐两处，其余 main 零 diff；nop-ooxml-docx/doc-model 零 diff）。
  - Anti-Hollow：展开器被 14+ 用例真实驱动（全链实跑）；无空方法体/no-op。
  - Audit 建议落实（APPROVED 无必修）：① 移除重复 import 与未使用 import；② 新增 testBlockLoopVarVisibleToModelExpressions——loop 变量对块内 run model 表达式可见的直接断言（实现侧补 materializeParagraph：带 valueExpr 的段落循环副本物化求值写回文本，共享原实例不被变异）；附带基线修复核实（XptWordTableParser 两处 override 签名对齐 WI3 钩子变更，旧签名必然 compile error 成立）。
  - Deferred 项分类检查：块实例共享与 deleteAllAfterConfigTable 均 watch-only，非 in-scope 缺陷降级。
