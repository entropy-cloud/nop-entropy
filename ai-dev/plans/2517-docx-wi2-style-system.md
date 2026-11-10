# 2517 docx roadmap WI2：Word 样式体系

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI2）+ docs-for-ai/02-core-guides/word-document-model.md + ai-dev/design/word-editor/online-word-model-design.md §6/§7
> Related: ai-dev/plans/2516-docx-wi1-structure-model.md

## Purpose

Word 样式体系完善：四类样式（WordRunStyle/WordParagraphStyle/WordCellStyle/WordTableStyle）补齐标题级别与列表样式语义；doc 级命名样式容器（命名样式/默认样式/`basedOn` 基样式继承解析）；样式往返单测。叶子模型继续沉淀在 `nop-office-model`（已就位，本计划不迁移类）。

## Current Baseline

- `OfficeFont`（nop-office-model）已覆盖：bold/italic/strikeout/underlineStyle/verticalAlign/fontFamily/fontName/fontSize/fontColor/charSet——run 级粗斜删下上下标语义齐备。
- `WordRunStyle`：id/name/highlightColor + font（xdef：word-run-style.xdef）。
- `WordParagraphStyle`：align/leftIndent/rightIndent/firstLineIndent/lineSpacing/spaceBefore/spaceAfter——**缺标题级别（titleLevel）与列表样式**。
- `WordCellStyle`：backgroundColor/四向 border/font/verticalAlign。
- `WordTableStyle`：align/cellStyle/id/name/width。
- **doc 级样式容器不存在**：doc.xdef 无 `<styles>`；无命名样式、无默认样式标记、无 basedOn 继承链。
- 样式节点在 doc.xdef 内经 `xdef:ref="word-*-style.xdef"` 内联引用（p/r 下 `<style>`）。
- WI1 已落地：八类块分派 + runs 包装节点；codegen 约定（addXxx 懒建、copyTo 浅拷贝、union 分派需 type 属性）已知。

## Goals

- `WordParagraphStyle` 增 `titleLevel`（Integer 可空：null=正文，0-8=标题级别，语义对齐 OOXML outlineLvl）；新增 `WordListStyle` 叶子模型（numFmt/lvlText/start/level/indent）落 `nop-office-model`，由 `<list>` 块经 `styleId` 引用。
- doc 级命名样式容器 `OfficeDocModel.styles`：五类样式（run/paragraph/cell/table/list）经 `OfficeStyle` 标记接口分派（复用 WI1 包装节点分派模式，key-attr=id，子标签处 `id="!string"` 升级必填——先例 doc.xdef body 对 p 的 id 重声明），每类样式增 `basedOn`/`default` 字段；`OfficeListModel` 增 `styleId` 引用命名 listStyle。
- 基样式继承解析器（hand-written，nop-office-doc-model；模式先例 nop-ooxml-docx `WordStylesPart.resolveInheritance` 的 visited 环检测 + 逐字段 inherit）：沿 basedOn 链合并，标量字段非空覆盖（排除 id/name/basedOn/default），嵌套对象（font/四向 border/cellStyle）整体替换；环检测 fail-fast。
- 样式往返单测 + 继承解析单测 + xdef round-trip。

## Non-Goals

- docx styles.xml 解析/生成（WI3/WI5 接线）；文档结构模型变更（WI1 已定稿）；模板语义（WI4）；图表绑定模型。
- 不动 OfficeFont（已齐备）；不引入 springreport 代码。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`：word-paragraph-style.xdef（titleLevel）、新增 word-list-style.xdef、word-run-style.xdef/word-cell-style.xdef/word-table-style.xdef（basedOn/default）、doc.xdef（styles 容器）。Protected Area 生成管线，plan-first 载体即本计划。
- `nop-format/nop-office-model`：WordListStyle 新类 + 四类样式 basedOn/default（xdef 改动经各模块 precompile 重生成）。
- `nop-format/nop-office-model`：OfficeStyle 标记接口（五类样式 implements）；`nop-format/nop-office-doc-model`：OfficeStyleResolver（继承链合并解析）。
- 测试 + owner doc 矩阵更新 + 设计文档决策记录。

### Out Of Scope

- 见 Non-Goals；另：nop-ooxml-docx 生产源码零改动（WI2 不触碰解析/生成）。

## Execution Plan

### Phase 1 - 样式决策收敛

Status: completed
Targets: `ai-dev/design/word-editor/online-word-model-design.md`

- Item Types: `Decision`

- [x] 决策记录（设计文档 §13 追加）：
  - S1 命名样式容器：doc 级 `styles` 列表（`OfficeStyle` 标记接口落 **nop-office-model**——四类样式类在该模块，接口放 doc-model 会造成依赖方向倒置编译必败；runStyle/paragraphStyle/cellStyle/tableStyle/listStyle 五子标签分派，复用 WI1 包装节点模式，key-attr=id，子标签处 `id="!string"` 升级必填）；拒绝单一 style 类 + type 字符串（失去四类样式编译期区分）。
  - S2 继承语义：`basedOn` 沿链合并——标量字段非空覆盖，排除 id/name/basedOn/default 四字段；嵌套对象（font/四向 border/cellStyle）child 非空则整体替换；`default` 每类至多一个缺省样式；环检测与未知 basedOn id fail-fast（错误码两档口径按 error-handling.md 实现期裁定）；解析器 hand-written 落 nop-office-doc-model（模型层不依赖运行时表达式），实现模式先例 = nop-ooxml-docx `WordStylesPart.resolveInheritance`（visited 环检测 + 逐字段 inherit），属平行实现不引用该模块。
  - S3 标题级别：`WordParagraphStyle.titleLevel`（Integer 可空：null=正文，0-8=标题级别，语义对齐 OOXML outlineLvl）。
  - S4 列表样式：`WordListStyle{numFmt, lvlText, start, level, indent}` 独立叶子模型落 nop-office-model；引用点裁决——styles 容器第五子标签 `listStyle`，`OfficeListModel` 增 `styleId` 引用（list.numId 保留为实例级编号绑定）；listStyle 同样支持 basedOn/default 并入 resolver 统一解析。
- [x] check-doc-links --strict 退出码 0。

Exit Criteria:

- [x] 设计文档含 S1–S4 决策及拒绝理由。
- [x] check-doc-links --strict 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - xdef 扩展与模型落地

Status: completed
Targets: `nop-kernel/nop-xdefs/.../office/`, `nop-format/nop-office-model`, `nop-format/nop-office-doc-model`

- Item Types: `Feature`

- [x] xdef：word-paragraph-style.xdef 增 `titleLevel="int"`；五类 style xdef 均增 `basedOn="string" default="!boolean=false"`（含新增的 word-list-style.xdef）；doc.xdef 增 `<styles>` 容器（五子标签分派，子标签处 id 升级 `!string`，引用各 style xdef——先例 doc.xdef body `<table xdef:ref="word-table.xdef">` 的跨文件引用）。
- [x] 编辑 `nop-format/nop-office-model/precompile/gen-office-xdsl.xgen` 渲染清单加入 word-list-style.xdef（该 xgen 为硬编码逐行 renderModel，新 xdef 不会被自动拾取）。
- [x] precompile 重生成：`./mvnw generate-sources -pl nop-format/nop-office-doc-model -am`（覆盖 nop-office-model 与 doc-model 两模块的生成物），确认新基类与字段。
- [x] 手写层：`OfficeStyle` 标记接口（含 getId()）落 **nop-office-model** + 五类样式 implements；doc-model 侧 `OfficeStyleResolver`：resolveRunStyle/resolveParagraphStyle/resolveCellStyle/resolveTableStyle/resolveListStyle——按 id 查 named styles，沿 basedOn 合并（排除字段清单见 S2），default 缺省回退，环检测与未知 id fail-fast（两档口径实现期裁定后落设计文档）；`OfficeListModel` 手写子类无新增（styleId 为生成属性）。
- [x] WordListStyle 手写子类（若 codegen 壳够则免）。

Exit Criteria:

- [x] `./mvnw test -pl nop-format/nop-office-doc-model -am` 绿（含 nop-office-model 重生成编译）。
- [x] `_gen` 生成物全部由 precompile 产出，无手改痕迹。
- [x] 错误处理两档口径按 error-handling.md 裁定并落设计文档。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 样式往返与继承解析单测

Status: completed
Targets: `nop-format/nop-office-doc-model/src/test/java/io/nop/office/doc/model/`, `nop-format/nop-office-model/src/test/java/io/nop/office/model/`

- Item Types: `Proof`

- [x] `TestOfficeStyleResolver`（doc-model）：named styles 五类注册、basedOn 链合并（标量字段子覆盖父逐字段断言 + 排除字段不被合并断言 + 嵌套对象整体替换断言）、default 回退、basedOn 环 fail-fast、未知 basedOn id 显式报错；listStyle 经 `<list styleId>` 引用解析。
- [x] `TestWordStyleRoundTrip`（doc-model）：含命名样式的 doc 模型 → XNode（doc.xdef）→ 反解析类型保持（五类分派/继承字段/default 标记/basedOn 链不丢）；段落 titleLevel、列表 styleId→WordListStyle 字段往返。
- [x] 既有测试保持全绿（WordTableStructure 8、OfficeDocStructureModel 8）。

Exit Criteria:

- [x] 新增测试覆盖继承合并逐字段语义 + default 回退 + 环 fail-fast + xdef 往返。
- [x] **接线验证**：resolver 被样式往返测试真实调用（命名样式经 doc 模型 → resolver 解析 → 合并结果断言）。
- [x] `./mvnw test -pl nop-format/nop-office-doc-model -am` 全绿。
- [x] owner doc 能力矩阵样式行更新（命名/默认/继承「模型已定稿」口径）；`ai-dev/logs/` 已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI2 勾选 + Last updated 同步。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] `./mvnw test -pl nop-format/nop-office-doc-model -am` 绿。
- [x] 生成物纪律：git diff 无手改 `_gen`/`_` 前缀文件。
- [x] check-doc-links --strict 退出码 0。
- [x] scan-hollow-implementations --module nop-office-doc-model --severity high 退出码 0。
- [x] check-plan-checklist --strict 退出码 0。
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- WordTableCell 级横向对齐字段（若 WI3 解析需要时补）。

## Closure

Status Note: WI2 三 Phase 完成：五类 style xdef 增 basedOn/default + titleLevel + 新增 word-list-style.xdef（gen-office-xdsl.xgen 渲染清单同步）；doc.xdef 增 styles 容器五类分派 + OfficeListModel.styleId；OfficeStyle 接口落 nop-office-model（五类 implements）；OfficeStyleResolver（标量非空覆盖 + 排除 id/name/basedOn/default/type/location + 嵌套对象整体替换 + primitive 等于构造缺省视为未设置 + visited 环检测/未知 id 跨类型 fail-fast）落 nop-office-doc-model；7 resolver 测试 + 2 xdef 往返测试全绿（25/25）。独立 closure audit 待记录。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_4b958ce6-1b7f-45b6-b9ce-0bc46c537f1f）
- Audit Session: agent_4b958ce6-1b7f-45b6-b9ce-0bc46c537f1f
- Evidence:
  - Exit Criteria 逐项：Phase 1 PASS（设计文档 §14 恰五条决策含 primitive-unset 补充裁定）；Phase 2 PASS（五 xdef/容器/styleId/xgen 与 16+ 生成物一一对应、无手改生成物；OfficeStyle 接口与五类 implements、resolver 语义与设计一致含六字段排除清单）；Phase 3 PASS（TestOfficeStyleResolver 7 + TestWordStyleRoundTrip 2 实跑全绿，模块 25/25）。
  - Closure Gates：`./mvnw test -pl nop-format/nop-office-doc-model -am` 实跑 exit 0；check-doc-links --strict exit 0；scan-hollow --module nop-office-doc-model --severity high exit 0（0 发现）；roadmap WI2 勾选 + Last updated 同步；日志条目完整；nop-ooxml-docx 生产源码零改动（scope 纪律核对）。
  - Anti-Hollow 检查：resolver 被 9 个测试位置真实调用并逐字段断言；NopOfficeDocModelException 三条 fail-fast 路径（环/未知 id/跨类型）均被 assertThrows 覆盖；scan-hollow 0 命中。
  - Deferred 项分类检查：无 deferred 项；排除字段清单（id/name/basedOn/default/type/location）比设计 S2 初稿四字段多 type/location 两项，已在日志与实现中登记（audit 核对一致）。
