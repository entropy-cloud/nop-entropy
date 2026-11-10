# 2516 docx roadmap WI1：通用文档结构模型定稿

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI1）+ docs-for-ai/02-core-guides/word-document-model.md + ai-dev/design/word-editor/online-word-model-design.md §5/§10
> Related: ai-dev/plans/2515-docx-wi0-baseline-spec-visual-acceptance.md

## Purpose

把通用文档结构模型补齐到覆盖 Word 常见编辑能力：块级 title/list/image/hyperlink/separator/pageBreak 独立类型、行内 tab/br/hyperlink run 类型、页面级页面设置（宽高/方向/页边距）、doc 级图片资产、`makeXxx()`/clone/freeze 与 `ExcelSheet/ExcelCell` 体验对齐。模型定稿后由 WI3（解析）/WI5（生成）接线。

## Current Baseline

- 模型现状（live code 核对）：块级仅 `OfficeParagraphModel`（type=p）+ `WordTable` 实现 `OfficeBlock` 标记接口；行内仅 `OfficeRunModel`（t/style/model），runs 为 `KeyedList<OfficeRunModel>`；`OfficeDocModel` 只有 width/height/model/pages；`OfficeDocPageModel` 只有 body/footer/header/model/name/orientation，无页边距、无页面级宽高；无 assets。
- 元模型 `doc.xdef`：body/header/footer 仅 `<p>`/`<table>` 两种子标签；`<r>` 单标签直挂 `xdef:bean-prop="runs"`；precompile 经 `gen-office-doc-xdsl.xgen` 渲染 `/nop/templates/xdsl` 生成 `_gen` 基类。
- 手写子类约定：`OfficeXxx extends _OfficeXxx`，手写部分只放 `makeModel()`（位置继承 + 懒建）；`freeze/cloneInstance` 由生成基类提供。
- 解析/生成端（nop-ooxml-docx）在 WI1 零功能改动（runs 类型化的机械类型拓宽豁免，见 Scope > Out Of Scope）。
- 样式四类模型已在 `nop-office-model`（WI2 范畴，本计划不触碰）。

## Goals

- doc.xdef 扩展：块级新标签（title/list/image/hyperlink/separator/pageBreak）+ 行内新标签（tab/br/link）+ page 级页面设置 + doc 级 assets；precompile 重生成全部 `_gen` 基类。
- 新模型手写子类（实现 OfficeBlock/OfficeRun 标记接口 + makeModel()）与现有体验一致。
- 模型单测：结构构建/clone/freeze/makeModel/新类型分派。
- owner doc 能力矩阵同步（模型层状态变化）。

## Non-Goals

- 解析/生成端接线（WI3/WI5）；模板语义扩展（WI4）；样式体系（WI2）；PDF（WI6）。
- 不重写 word-table.xdef 表格结构（已满足，行列合并/列宽/行高已建模）；不动 nop-office-model 样式类。
- 不引入 springreport 代码/依赖。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`：`doc.xdef` 扩展（Protected Area 生成管线，plan-first 载体即本计划）。
- `nop-format/nop-office-doc-model`：precompile 重生成 + 新手写子类。
- `nop-format/nop-office-doc-model/src/test`：模型单测。
- `docs-for-ai/02-core-guides/word-document-model.md` 能力矩阵行更新；`ai-dev/design/word-editor/online-word-model-design.md` 决策记录增补。
- `docs-for-ai/04-reference/source-anchors.md` 新增锚点（如有新公共入口）。

### Out Of Scope

- 见 Non-Goals；另：nop-ooxml-docx 生产源码（parser/writer/xlib）零功能改动——**显式豁免**：runs 类型化（`List<OfficeRunModel>` → `List<OfficeRun>`）所需的纯机械类型拓宽（已核实最小面：`OfficeDocXmlBuilder.java` 两处 for-each 变量类型与 `buildRun` 签名）不算功能改动，不触发 R-A1；除此之外的 parser/writer 行为变化一律留 WI3/WI5。

## Execution Plan

### Phase 1 - 结构决策收敛

Status: completed
Targets: `ai-dev/design/word-editor/online-word-model-design.md`

- Item Types: `Decision`

- [x] 决策记录（写入设计文档新章节，含拒绝的替代方案）：
  - D1 块级新类型用独立类实现 `OfficeBlock`（与段落/表格并列、doc.xdef 标签分派），而非复用段落 type 字符串——理由：强类型 + 编辑器语义清晰；代价：类数量增加。
  - D2 行内 run 类型化（具体 xdef 结构已定）：新增 `OfficeRun` 标记接口（手写，模式同 OfficeBlock）；p 下增**包装节点** `<runs>`（先例：task/task.xdef steps 的包装节点 + bean-sub-type-prop 形态，产出 KeyedList 需包装节点 + key-attr），子标签 `r`（text，标签名保留不改名）/`tab`/`br`/`link` 经 `xdef:bean-sub-type-prop="type"` 分派，`link` 内嵌 runs 包装节点（递归嵌套）；p.runs 类型变为 `KeyedList<OfficeRun>`。扁平形态「多个子标签直接共挂一个 bean-prop」全 xdef 语料无先例，不做。降级预案 R-A1 触发条件：(a) 包装节点 + bean-sub-type-prop 分派经 codegen 实测不可用，或 (b) runs 类型化破坏下游编译且机械拓宽不可修复——触发时改为保留 `List<OfficeRunModel>` 单类 + `type` 属性（text/tab/br/link 枚举），决策写入设计文档并同步 owner doc。
  - D3 页面设置：`OfficeDocPageModel` 增 width/height（可空，回退 doc 级）+ `margins:OfficePageMargins`（top/right/bottom/left/header/footer/gutter，twips 口径 double）；doc 级 assets：`OfficeImageAsset`（name/ext/data）。data 字段类型按实现期裁定：全 xdef 语料无 byte[] 先例（审查核实），若 xdef 类型系统不支持则用 String base64 并在设计文档登记裁定结果。
  - D4 list 模型：`OfficeListModel{numId, items:List<OfficeListItemModel>}`，item{level, runs, style}——list 为自包含块（编辑器友好），不把列表语义散进段落。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

- [x] 设计文档含 D1–D4 决策及替代方案拒绝理由；R-A1 是否触发有明确结论。
- [x] check-doc-links --strict 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - xdef 扩展与模型类落地

Status: completed
Targets: `nop-kernel/nop-xdefs/.../office/doc.xdef`, `nop-format/nop-office-doc-model`

- Item Types: `Feature`

- [x] doc.xdef：body/header/footer 增 `<title>/<list>/<image>/<hyperlink>/<separator>/<pageBreak>` 标签（各含 id 与必要属性、必要时内嵌 model 子节点占位语义按现有模板模型模式）；`<p>` 内增包装节点 `<runs>`（bean-sub-type-prop 分派，子标签 r/tab/br/link，r 保留为 text run 标签名）；page 增 width/height/margins；doc 增 assets。
- [x] precompile 重生成：`./mvnw generate-sources -pl nop-format/nop-office-doc-model -am`（nop-xdefs 资源先进 reactor，避免读到本地仓库旧 xdef jar）触发 `gen-office-doc-xdsl.xgen`，确认 `_gen` 新基类生成且无手工编辑生成物。
- [x] 手写子类：OfficeTitleModel/OfficeListModel(+OfficeListItemModel)/OfficeImageModel/OfficeHyperlinkModel/OfficeSeparatorModel/OfficePageBreakModel implements OfficeBlock；OfficeTabModel/OfficeBreakModel/OfficeLinkModel（+手写 OfficeRun 接口）implements OfficeRun；各含 makeModel()（有模板语义的节点）。
- [x] 既有 `OfficeRunModel` 实现新 OfficeRun 接口的兼容核查；p.runs 类型变化时 nop-ooxml-docx 机械类型拓宽（豁免范围内）落地且编译通过（功能不改）。
- [x] byte[]（assets data）xdef 类型支持性实测结论落设计文档（支持则 byte[]，否则 String base64 并登记）。

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-office-doc-model -am` 绿；nop-ooxml-docx 编译兼容由 Closure Gates 的 `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 承担。
- [x] `_gen` 基类全部由 precompile 生成（git diff 中 `_gen` 文件与 xdef 一致、无手改痕迹）；新增公共模型类均含手写子类层。
- [x] 无静默跳过：新模型类的模板 model 节点均经 xdef 定义生成，无手写空壳方法（makeModel 为既有约定模式，不算空壳）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 模型单测

Status: completed
Targets: `nop-format/nop-office-doc-model/src/test/java/io/nop/office/doc/model/`

- Item Types: `Proof`

- [x] 新增 `TestOfficeDocStructureModel`（或分文件）：构建含全部新块类型 + 新 run 类型 + 页面设置 + assets 的完整模型树；断言 makeModel 懒建幂等（两次调用同一实例）；cloneInstance 深拷贝（修改副本不影响原树）；freeze 后 setter 抛异常；xdef 反序列化冒烟（用 XDefParser 从 doc.xdef 加载一个含新标签的最小模型 XML，验证 schema 与代码一致——接线验证：新标签真的被 xdef 引擎接受而非仅 Java 类存在）。
- [x] 既有 `TestWordTableStructure` 8 用例保持全绿。

Exit Criteria:

- [x] 新增测试覆盖全部新块级/行内类型 + makeModel/freeze + xdef 反序列化冒烟；cloneInstance 按平台约定为浅拷贝（copyTo 语义与 ExcelWorkbook 一致，决策已登记设计文档 §13.7），测试按浅拷贝口径断言。
- [x] `./mvnw test -pl nop-format/nop-office-doc-model -am` 全绿。
- [x] **接线验证**：xdef 反序列化测试证明 doc.xdef 新标签 → 生成类实例的运行时分派真实可用。
- [x] owner doc 能力矩阵行更新（块级/行内/页面设置/assets 从「未支持」改为「模型已定稿，解析/生成待 WI3/WI5 接线」口径——矩阵语义按 owner doc 现行约定微调）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI1 勾选 + Last updated 同步。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] `./mvnw test -pl nop-format/nop-office-doc-model -am` 绿；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿（下游编译兼容 + 既有 50 用例回归）。
- [x] 生成物纪律：git diff 无手改 `_gen`/`_` 前缀文件；doc.xdef 变更经 precompile 再生成闭环。
- [x] check-doc-links --strict 退出码 0。
- [x] scan-hollow-implementations --module nop-office-doc-model --severity high 退出码 0。
- [x] check-plan-checklist --strict 退出码 0。
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

（无——R-A1 若触发属显式 scope 内决策，不进 deferred）

## Non-Blocking Follow-ups

- WordTableCell.styleId 关联样式解析（WI2 样式体系承接）。

## Closure

Status Note: WI1 三 Phase 完成：doc.xdef 扩展为八类块分派 + runs 包装节点四类行内分派 + page 级页面设置 + doc 级 assets，precompile 重生成 16 个新 _gen 基类；手写子类 + OfficeRun 接口 + addRun 约定落地；8 个新模型测试全绿（含 xdef 反序列化接线证明与 xdef round-trip）；下游 nop-ooxml-docx 模块 50 测试全绿（机械类型拓宽豁免范围内 + addRun 替换 addR，R-A1 未触发）。独立 closure audit APPROVED-WITH-FIX，两项 Minor（owner doc 矩阵行口径、测试计数勘误）已落实。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_df72431d-54e0-4523-a97e-bf2a6c8ac5b6）
- Audit Session: agent_df72431d-54e0-4523-a97e-bf2a6c8ac5b6
- Evidence:
  - Exit Criteria 逐项：Phase 1 PASS（设计文档 §13 含 8 条决策与拒绝理由）；Phase 2 PASS（doc.xdef 八类块 + runs 包装节点分派 + page 设置 + assets 与 16 个 _gen 基类一一对应，git diff 无手改生成物；手写子类/接口/addRun 完整）；Phase 3 PASS（TestOfficeDocStructureModel 8 用例真实——raw XML xdef 反序列化分派到类型化实例、xdef round-trip 类型保持、makeModel 幂等、同 id 替换、freeze 抛异常；TestWordTableStructure 8 用例全绿）。
  - Closure Gates：`./mvnw test -pl nop-format/nop-office-doc-model -am` 与 `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 实跑 exit 0（50 用例）；check-doc-links --strict exit 0；scan-hollow --module nop-office-doc-model --severity high exit 0（0 发现）；roadmap WI1 勾选 + Last updated 同步；日志 ai-dev/logs/2026/11-10.md 有条目；生成物纪律（_gen diff 全部可由 xdef 解释）。
  - Anti-Hollow 检查：新模型类由 xdef 引擎在反序列化用例中真实分派实例并断言结构（非仅类存在）；buildRun 对未接线 run 类型 fail-fast 抛 UnsupportedOperationException（无静默跳过）；scan-hollow high=0。
  - Deferred 项分类检查：无 deferred 项；R-A1 未触发（包装节点分派一次实测可用），两项 Minor 修复已落实。
