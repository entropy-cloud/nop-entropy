# 2484 WI15 目录页检测与结构源路由 + 标题回填

> Plan Status: active
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI15，deps WI14 已完成）
> Related: card-kb 语义基准 `~/workspace/card-knowledge-base/tools/lib/headingize.mjs`（v1 toc 驱动全路径 + headingizeFromOutline v2 outline 驱动）、`~/workspace/card-knowledge-base/tools/analyzed-pipeline/src/core/stages/s0-structure-source.mjs`（路由/守卫/TOC_LINE_RE/validateReconstructedEntries）
> Review: R1 对抗性审查完成（1 Blocker + 5 Major + 4 Minor 全部吸收——双路径拆分/层级纠错/双正则分工/输入契约/决策表/v1 机制清单；见修订记录）

## Purpose

无 outline 或需交叉验证时，从目录页恢复结构：目录行检测（三页码形态）、确定性结构源路由（outline > toc-regex > AI 重建钩子 > chunk，含守卫与决策审计轨迹）、按结构在页锚处回填 markdown 标题。**两条回填路径直译自 headingize.mjs 的两套互斥算法**（v2 outline 驱动 / v1 toc 驱动），不混合。

## Current Baseline

2026-10-19 核对（R1 审查逐条核实）：

- nop-pdf 仅有 `SimpleTocDetector`（extract 包，面向 ResourceDocument 行块管线的最简 dot-leader 检测）；无路由、无回填、无三页码形态。**裁定（R1-m1）**：extract 包保持不动（面向行块管线），`io.nop.pdf.toc` 面向纯文本行独立演进。
- **headingize.mjs 含两套互斥回填算法（R1-B1）**：
  - **v2 outline 驱动（headingizeFromOutline :70-126）**：输入 `List<PdfOutlineEntry>` 形状（title/depth/page/pageEnd——恰为 WI14 产物 schema）；整键相等（`keyify(l) === tKey`，**非前缀匹配**）；搜索窗口=上一条命中页起点+1 到本条 pageEnd；命中原位升级 `#`，未命中插在目标页锚后；层级=entry.depth clamp 1..6；无相邻页、无页顶兜底；stats={outlineEntries, inserted, replaced, unmatched, skippedNoAnchor}。
  - **v1 toc 驱动（headingize :128-358）**：输入 pdftotext -layout 形态行文本（多空格保留）；keyMatch 双向前缀（min 4）；`[target, +1, -1]` 相邻页；页顶兜底；层级=titleLevel 两档（H1_RE→1 否则 2）；完整机制清单（R1-M5，见 Phase 1 Item 3）。
- **正则分工（R1-M2）**：s0 的 `TOC_LINE_RE`（:23，无附件形态、无 `\r?`、仅作路由证据）≠ headingize 的 `TOC_ENTRY_RE`（:17，三页码形态含附件、行尾容 `\r`、做条目抽取）。路由守卫扫描用 s0 口径直译；抽取用 TOC_ENTRY_RE 原文照抄（含 `(?:` 非捕获组，以 headingize.mjs:17 为准）。
- **层级推导（R1-M1 纠错）**：编号点分段数（numDepth）仅是 tocDepth>0 时的**条目过滤器**，从不决定 `#` 层数；层级来源分路径——outline 路径=entry.depth；toc 路径=titleLevel 两档。
- **路由（s0 run :113-176）**：outline 条目非空→直接用；否则在 toc-regex/ai-reconstruct/chunk 间路由——s0 中该选择是 LLM decide（:134-147），**本计划的确定性决策表（R1-M4）见 Phase 1 Item 2**；守卫：toc 可行 = 首个含目录行页 ≤ ceil(nPages×20%)（s0 :153，用 TOC_LINE_RE 在前 12 页扫描）；chunk 路线遇 nPages≥20 的覆盖修正仅针对 LLM 决策出的 chunk（:160-162），确定性链中该守卫不保留（决策表自身保证）。corrections 审计轨迹（:148-162）与 crosscheck 失衡告警（:196-213，outline 条目数 vs toc 行数 0.5/2.0 倍界外 WARN——R1-m4 纳入路由输出）。
- **钩子校验（R1-M4）**：s0 validateReconstructedEntries（:69-82）——title≥3、depth clamp 1..6、page clamp 1..nPages、排序、去重；重建输入=前 10 页、每页 1500 字上限的 front-pages 文本（:84-95）。
- **文本输入（R1-M3 裁定）**：toc 包内部新建轻量 sorted 页文本 API（PDFTextStripper setSortByPosition(true)，**保留多空格**——v1 的 `≥2 空格` leader 依赖 -layout 形态；不做多空格坍缩）；`getPageText` 现状未开 sort，不动它。`PdfDoc.getPageText`（:188-200）默认 stripper 乱序风险由此规避。`\r` 在 PDFBox 输出中不实际出现（容 \r 为基准兼容，测试造 \r 用例）。
- **锚语义（R1-B1）**：页锚 `<a id="pdf-page-N"></a>` 为**消费**语义——输入文本中已有锚（由使用方/WI16 产出）；无锚时回填器原样返回 + 空 stats（headingize:142 parity）。nop-pdf 不生成锚。
- pdfjs/正则 Java 语义（R1-m2）：`\s` 不启用 UNICODE_CHARACTER_CLASS（全角/不可断空格由 keyify NFKC 兜底，正则不偏离基准）；lowercase 用 Locale.ROOT；TOC 行匹配为逐行 full match。
- WI14 已落地：`PdfDoc.extractOutline()` → `PdfOutline{entries: List<PdfOutlineEntry>, stats}`。

## Goals

- `io.nop.pdf.toc` 新包：
  - **TocLineDetector**（TOC_ENTRY_RE 直译，三页码形态，输出 TocLine{title, pageLabel, pageNo|null}）+ **SortedPageTexts** 轻量页文本 API（sort stripper、保留空白）。
  - **StructureSourceRouter**（确定性决策表 + 守卫 + corrections 审计 + crosscheck 告警 + ReconstructHook 钩子接口与确定性校验）。
  - **OutlineBackfiller**（v2 直译）与 **TocBackfiller**（v1 全机制清单直译），各自 stats 字段表。
- PdfDoc 门面方法组合（extractOutline/detectTocLines/routeStructureSource/backfill 系列）。
- 全合成 fixture 测试 + 端到端。

## Non-Goals

- AI 重建的实现（钩子由使用方注入）；OCR；pdf 转图片版面分析；full.md 磁盘写回。
- 与 WI16 的归一化对齐（登记 follow-up：WI16 落地后页文本契约对版）。

## Scope

### In Scope

- 上述 toc 包全部组件 + PdfDoc 门面；pom 无新依赖。
- 测试：三页码形态正则（含 \r 用例与组号断言）、v1 机制逐项（isTocPage≥3/带扩展 maxGap=3/条目<3 放弃/page≤lastTocPage 跳过/pageOffset 扫描与采纳判据/逻辑页单调扫描 extra≤12/Figure-Table-example 过滤/usedLines 去重/SOURCE_HEADING_LIKE 守卫/BODY_H1 pass）、v2 路径（整键相等/搜索窗口/原位升级 vs 锚后插入/depth clamp）、路由决策表全分支 + corrections/crosscheck 断言、无锚原样返回、端到端（合成无 outline + 目录页 PDF → 路由 toc-regex → TocBackfiller → md 结构断言）。

### Out Of Scope

- AI 重建默认实现；pageOffset 扫描的裁剪（不裁剪，全清单直译）；与 extract 包 SimpleTocDetector 的整合。

## Execution Plan

### Phase 1 - 检测/路由/回填

Status: planned
Targets: `io/nop/pdf/toc/*`（新增）、`PdfDoc.java`、测试

- Item Types: `Feature`

- [ ] **SortedPageTexts + TocLineDetector**：轻量 sorted 页文本（新 stripper 实例 setSortByPosition(true)，逐行输出、**保留空白 run**、不做归一化——契约声明）；TOC_ENTRY_RE 原文直译（`^(.{3,140}?)[ .]{2,}((?:[A-Za-z]?\d{1,4}(?:-[A-Za-z]?\d{1,4}[A-Za-z]?)?)|附件[一二三四五六七八九十]+-\d+)\s*\r?$`——组号与原文一致，页码取 m[2] 对应组；逐行 full match；不启用 UNICODE_CHARACTER_CLASS）；cleanTitle（剥尾部 `[ .·…]+` + 空白归一）；输出 TocLine。
- [ ] **StructureSourceRouter**（R1-M4 决策表定版）：输入（outline entries、逐页文本、nPages、ReconstructHook|null、可选 source 显式指定）→ RoutingDecision{source, entries, corrections, crosscheckWarnings}。**决策表**：(1) outline 非空 → outline；(2) 显式指定 source 且可行 → 按指定（守卫修正仍记录）；(3) tocFirstPage（TOC_LINE_RE 在前 12 页首命中）≠null 且 ≤ ceil(nPages×20%) → toc-regex；(4) 钩子非空 → 调钩子（输入=前 10 页、每页 1500 字上限 front-pages）+ **确定性校验**（title≥3/depth clamp 1..6/page clamp/sort/dedupe——s0 validateReconstructedEntries 直译），校验失败记 correction 降 chunk；(5) 否则 chunk；nPages≥20 的 chunk-override 守卫不保留（确定性链中无可覆盖对象——决策表自身保证，corrections 记录该裁定）。crosscheck：outline 条目数 vs 目录行数比值越出 [0.5, 2.0] 记 WARN 进输出（R1-m4）。ReconstructHook 接口签名与输入窗口/输出 schema 入 javadoc。
- [ ] **OutlineBackfiller（v2 直译，headingizeFromOutline :70-126）**：输入 `List<PdfOutlineEntry>` + 逐页锚文本；整键相等（keyify）；窗口=上条命中页起点+1..本条 pageEnd；原位升级/锚后插入；层级=depth clamp 1..6；stats={outlineEntries, inserted, replaced, unmatched, skippedNoAnchor}；无锚→原样返回+空 stats。
- [ ] **TocBackfiller（v1 全机制清单直译，headingize :128-358——R1-M5 逐项 checklist）**：isTocPage（每页≥3 命中）、目录带连续扩展（maxGap=3）、条目<3 整体放弃、page≤lastTocPage 跳过、**pageOffset 扫描（off 2..40、采纳判据四重）**、逻辑页单调扫描（scanFrom 游标 + extra≤12 前缀回退）、Figure/Table/文件名样例过滤、usedLines 去重、SOURCE_HEADING_LIKE（`#` 开头正文行守卫）、BODY_H1 独立 pass、层级=titleLevel 两档；keyify（NFKC→lowercase(Locale.ROOT)→`[a-z0-9\u4e00-\u9fff]`）/keyMatch（双向前缀 min 4）/findLine 直译；**BackfillStats 字段表按 v1 实际定版**（tocEntries/inserted/byExact/byAdjacent/byPageTop/unmatched/tocPages/depthFiltered/indexSkipped/exampleSkipped/pageOffset 等，R1-m3）。
- [ ] PdfDoc 门面（extractOutline 组合、detectTocLines(pages)、routeStructureSource(...)、backfillByOutline(...)/backfillByToc(...)）。
- [ ] 测试（上列全覆盖 + m2 全角 keyify NFKC 用例 + \r 容忍用例 + 端到端：合成无 outline + 目录页 PDF → 路由 toc-regex → TocBackfiller → md 结构断言）。

Exit Criteria:

- [ ] 测试全绿且覆盖 v1 机制 checklist 逐项/v2 窗口语义/决策表全分支/corrections 与 crosscheck/无锚原样返回；**端到端验证**：合成 PDF → 路由 → 回填 → md 结构断言走通
- [ ] `./mvnw test -pl nop-format/nop-pdf -am` 全绿
- [ ] 语义裁定由收口 design 文档承接（Phase 2）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 收口

Status: planned
Targets: `ai-dev/design/nop-report/pdf-toc-structure.md`（新裁定文档）、roadmap、日志

- Item Types: `Decision | Proof`

- [ ] design 文档：双回填路径契约（输入 schema/锚消费语义/输出类型/stats 字段表）、路由决策表与守卫、正则分工（s0 路由 vs headingize 抽取）、SortedPageTexts 契约（保留空白、与 WI16 对版 follow-up）、与 extract 包 SimpleTocDetector 的共存边界（R1-m1）。
- [ ] roadmap WI15 勾选；check-doc-links/check-plan-checklist（本文件）/`scan-hollow --module nop-format/nop-pdf --severity high` 全过；收口前全量构建（恢复遍口径）。

Exit Criteria:

- [ ] design 文档落档且与实现一致
- [ ] roadmap Work Item Status WI15 勾选
- [ ] `ai-dev/logs/` 对应日期条目记录完成状态
- [ ] `./mvnw clean install -T 1C`（或恢复遍证据）

## Closure Gates

- [ ] Phase 1–2 全部 Exit Criteria 勾选
- [ ] 检测/路由/双回填路径均有 focused 测试（正则组号/窗口语义/决策表分支断言非 smoke）
- [ ] 受影响 owner docs 已同步（裁定文档落档）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2484-wi15-toc-routing-heading-backfill.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-pdf --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- WI16 页锚/文本归一化落地后与本计划页文本契约对版。
- xpt 流承载；目录行深度后缀解析。

## R1 审查修订记录

2026-10-19 R1（独立子代理 agent_9c27e8bd，1 Blocker + 5 Major + 4 Minor，全部吸收）：B1 双路径拆分（OutlineBackfiller=headingizeFromOutline v2 直译 / TocBackfiller=v1 全路径直译；输入 schema 分定；锚=消费不生成、无锚原样返回；输出=md string+stats）；M1 层级推导纠错（numDepth=过滤器，层级 outline→depth/toc→titleLevel 两档）；M2 双正则分工（s0 TOC_LINE_RE=路由证据、headingize TOC_ENTRY_RE=抽取，原文照抄含 `(?:`）+ 守卫口径声明；M3 输入契约（包内 sorted stripper 保留空白 + fixture 按该形态构造 + WI16 对版 follow-up）；M4 路由决策表定版 + 钩子校验直译 + chunk 守卫裁定移除；M5 v1 机制全清单直译（逐项 checklist 入 Phase 1）；m1 extract 包共存裁定；m2 Java 正则语义声明（不启用 UNICODE_CHARACTER_CLASS、Locale.ROOT、逐行 full match）；m3 stats 字段表按路径定版；m4 crosscheck 纳入路由输出。
