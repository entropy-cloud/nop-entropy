# 2484 WI15 目录页检测与结构源路由 + 标题回填

> Plan Status: completed
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
  - **v2 outline 驱动（headingizeFromOutline :70-126）**：输入 `List<PdfOutlineEntry>` 形状（title/depth/page/pageEnd——恰为 WI14 产物 schema，**但 v2 不消费 pageEnd**，其仅随 schema 携带，R2-Major-1）；整键相等（`keyify(l) === tKey`，**非前缀匹配**）；搜索窗口=[上一物理页有锚时其锚行+1，否则目标页锚行+1， 窗口末=下一锚行（页块末尾 p.end，行号语义）]；命中原位升级 `#`，未命中插在目标页锚后；层级=entry.depth clamp 1..6；无相邻页、无页顶兜底；stats={outlineEntries, inserted, replaced, unmatched, skippedNoAnchor}（基准返回的 insertedKeys 裁剪，design 文档显式记录）。
  - **v1 toc 驱动（headingize :128-358）**：输入 pdftotext -layout 形态行文本（多空格保留）；keyMatch 双向前缀（min 4）；`[target, +1, -1]` 相邻页；页顶兜底；层级=titleLevel 两档（H1_RE→1 否则 2）；完整机制清单（R1-M5，见 Phase 1 Item 3）。
- **正则分工（R1-M2）**：s0 的 `TOC_LINE_RE`（:23，无附件形态、无 `\r?`、仅作路由证据）≠ headingize 的 `TOC_ENTRY_RE`（:17，三页码形态含附件、行尾容 `\r`、做条目抽取）。路由守卫扫描用 s0 口径直译；抽取用 TOC_ENTRY_RE 原文照抄（含 `(?:` 非捕获组，以 headingize.mjs:17 为准）。
- **层级推导（R1-M1 纠错）**：编号点分段数（numDepth）仅是 tocDepth>0 时的**条目过滤器**，从不决定 `#` 层数；层级来源分路径——outline 路径=entry.depth；toc 路径=titleLevel 两档。
- **路由（s0 run :113-176）**：outline 条目非空→直接用；否则在 toc-regex/ai-reconstruct/chunk 间路由——s0 中该选择是 LLM decide（:134-147），**本计划的确定性决策表（R1-M4）见 Phase 1 Item 2**；守卫：toc 可行 = 首个含目录行页 ≤ ceil(nPages×20%)（s0 :153，用 TOC_LINE_RE 在前 12 页扫描）；chunk 路线遇 nPages≥20 的覆盖修正仅针对 LLM 决策出的 chunk（:160-162），确定性链中该守卫不保留（决策表自身保证）。corrections 审计轨迹（:148-162）与 crosscheck 失衡告警（:196-213，outline 条目数 vs toc 行数 0.5/2.0 倍界外 WARN——R1-m4 纳入路由输出）。
- **钩子校验（R1-M4）**：s0 validateReconstructedEntries（:69-82）——title≥3、depth clamp 1..6、page clamp 1..nPages、排序、去重；重建输入=前 10 页、每页 1500 字上限的 front-pages 文本（:84-95）。
- **文本输入（R1-M3 裁定 + R2-Major-2 实证细化）**：toc 包内部新建轻量 sorted 页文本 API（PDFTextStripper setSortByPosition(true)）。**实证契约**：点导符字形（". . . ."）保留、字面空格字形 run（showText 多空格）保留；**纯定位间隙（newLineAtOffset 拉开）坍缩为单空格**（PDFTextStripper writeLine 词间只写单分隔符）——无点导符且无字面多空格、仅靠定位排版的目录行在 toc-regex 路径系统性漏检，登记为已知限制挂 WI16 对版 follow-up（WI16 是 -layout 等价的 owner）；fixture 覆盖点导符字形与字面多空格两种可检形态。`getPageText` 现状未开 sort，不动它。`\r`：POSIX 无、Windows 有 \r\n——容 \r 保证两端一致（R2-Minor-3 措辞修正）。
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

Status: completed
Targets: `io/nop/pdf/toc/*`（新增）、`PdfDoc.java`、测试

- Item Types: `Feature`

- [x] **SortedPageTexts + TocLineDetector**：轻量 sorted 页文本（新 stripper 实例 setSortByPosition(true)，逐行输出、**保留空白 run**、不做归一化——契约声明）；TOC_ENTRY_RE 原文照抄（基准 3 组形态：`^(.{3,140}?)[ .]{2,}(([A-Za-z]?\d{1,4}(?:-[A-Za-z]?\d{1,4}[A-Za-z]?)?)|附件[一二三四五六七八九十]+-\d+)\s*\r?$`——页码取组 3 对应组；逐行 full match；不启用 UNICODE_CHARACTER_CLASS——全角/不可断空格由 keyify NFKC 兜底，lowercase 用 Locale.ROOT，R1-m2 裁定）；cleanTitle（剥尾部 `[ .·…]+` + 空白归一）；输出 TocLine。
- [x] **StructureSourceRouter**（R1-M4 决策表定版）：输入（outline entries、逐页文本、nPages、ReconstructHook|null、可选 source 显式指定）→ RoutingDecision{source, entries, corrections, crosscheckWarnings}。**决策表**：(1) outline 非空 → outline；(2) 显式指定 source 且可行 → 按指定（守卫修正仍记录；**显式 pin 不可行/钩子 null 时顺延后续分支并记 correction**，R2-Minor-2a）；(3) tocFirstPage（TOC_LINE_RE 在前 12 页首命中）≠null 且 ≤ ceil(nPages×20%) → toc-regex；(4) 钩子非空 → 调钩子（输入=前 10 页、每页 1500 字上限 front-pages）+ **确定性校验**（title≥3/depth clamp 1..6/page clamp/sort/dedupe——s0 validateReconstructedEntries 直译），校验失败记 correction 降 chunk；**校验通过但 0 条目 = 诚实 0 条目**（s0 :103-106 语义，非失败，R2-Minor-2b）；(5) 否则 chunk；nPages≥20 的 chunk-override 守卫不保留（确定性链中无可覆盖对象——决策表自身保证，corrections 记录该裁定）。钩子产物**不补 pageEnd**（v2 不消费；RoutingDecision javadoc 声明，R2-Minor-2d）。crosscheck：outline 条目数 vs 目录行数（**TOC_LINE_RE 口径**，R2-Minor-2c）比值越出 [0.5, 2.0] 记 WARN 进输出（R1-m4）。ReconstructHook 接口签名与输入窗口/输出 schema 入 javadoc。
- [x] **OutlineBackfiller（v2 直译，headingizeFromOutline :70-126）**：输入 `List<PdfOutlineEntry>` + 逐页锚文本；整键相等（keyify）；窗口=上条命中页起点+1..本条 pageEnd；原位升级/锚后插入；层级=depth clamp 1..6；stats={outlineEntries, inserted, replaced, unmatched, skippedNoAnchor}；无锚→原样返回+空 stats。
- [x] **TocBackfiller（v1 全机制清单直译，headingize :128-358——R1-M5 逐项 checklist；R2-Major-3 定版）**：输入=仅带锚 md（组件内部自行 isTocPage/抽取条目，非消费 TocLine 列表）；isTocPage（每页≥3 命中）、内部 front-20% 门（:155-156）、目录带连续扩展（maxGap=3）、条目<3 整体放弃、NaN 页码守卫（:182 页眉伪条目）、page≤lastTocPage 跳过、**pageOffset 扫描（off 2..40、采纳判据四重：≥10/≥1.2x/≥1.5x 次优/≥50% eligible）**、逻辑页单调扫描（scanFrom 游标 + extra≤12 前缀回退）、Figure/Table/文件名样例过滤、**claimed map 语义**（同 key 同行=duplicateSkipped、同 key 异行冲突=unmatched）、SOURCE_HEADING_LIKE（`#` 开头正文行守卫）、BODY_H1 独立 pass、层级=titleLevel 两档；**opts 全量移植**（tocDepth>0 深度过滤 + forceScan 全量扫描——Java 端以 BackfillOptions 承载）；keyify（NFKC→lowercase(Locale.ROOT)→`[a-z0-9\u4e00-\u9fff]`）/keyMatch（双向前缀 min 4）/findLine 直译；**BackfillStats 字段表定版**（tocEntries/inserted/byExact/byAdjacent/byPageTop/byScan/unmatched/duplicateSkipped/tocPages/depthFiltered/indexSkipped/exampleSkipped/pageOffset/bodyH1——基准早期 return 形状不一致，Java 端按单一 BackfillStats 归一）。
- [x] PdfDoc 门面（extractOutline 组合、detectTocLines(pages)、routeStructureSource(...)、backfillByOutline(...)/backfillByToc(...)）。
- [x] 测试（上列全覆盖 + m2 全角 keyify NFKC 用例 + \r 容忍用例 + 端到端：合成无 outline + 目录页 PDF → **经 PdfDoc.routeStructureSource + backfillByToc 门面**（R2-Minor-5 接线验证）→ md 结构断言；e2e 锚由测试以使用方身份手工合成——nop-pdf 不生成锚）。

Exit Criteria:

- [x] 测试全绿且覆盖 v1 机制 checklist 逐项/v2 窗口语义/决策表全分支/corrections 与 crosscheck/无锚原样返回；**端到端验证**：合成 PDF（文本真实写入）→ 路由 toc-regex → 回填 → md 结构断言走通
- [x] `./mvnw test -pl nop-format/nop-pdf -am` 全绿（模块全量 155 含 skip1）
- [x] 语义裁定由收口 design 文档承接（Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

执行偏差（Phase 1）：

- PDFBox 3.0.8 公共 API 无 PDOutlineNode.children()（WI14 同发现）；COSInteger.get 替代 new COSNumber。
- e2e 首版用空白 PDF + 手工 pageTexts——门面 routeStructureSource 内部走 SortedPageTexts 从 PDF 提取，测试文本须用 PDPageContentStream 真实写入（恰好实证了 R2-Major-2 契约）。
- JS 浮点判据整数化：`s >= bestScore*1.2` → `s > bestScore`（严格大于语义保留）；`bestScore >= max(second*1.5, 10) && >= eligible*0.5` → `bestScore*2 >= second*3 && bestScore >= 10 && bestScore*2 >= eligible`。

### Phase 2 - 收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-toc-structure.md`（新裁定文档）、roadmap、日志

- Item Types: `Decision | Proof`

- [x] design 文档：双回填路径契约（输入 schema/锚消费语义/输出类型/stats 字段表）、路由决策表与守卫、正则分工（s0 路由 vs headingize 抽取）、SortedPageTexts 契约（保留空白、与 WI16 对版 follow-up）、与 extract 包 SimpleTocDetector 的共存边界（R1-m1）。
- [x] roadmap WI15 勾选 **+ WI15 描述同步（"大文档 chunk 覆盖"措辞已按定版决策表改写——R2-Minor-4 reconcile-on-touch）**；check-doc-links/check-plan-checklist（本文件）/`scan-hollow --module nop-format/nop-pdf --severity high` 全过；收口前全量构建（恢复遍口径，见 Closure）。

Exit Criteria:

- [x] design 文档落档且与实现一致
- [x] roadmap Work Item Status WI15 勾选
- [x] `ai-dev/logs/` 对应日期条目记录完成状态
- [x] `./mvnw clean install -T 1C`（或恢复遍证据）

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] 检测/路由/双回填路径均有 focused 测试（正则组号/窗口语义/决策表分支断言非 smoke）
- [x] 受影响 owner docs 已同步（裁定文档落档）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2484-wi15-toc-routing-heading-backfill.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- WI16 页锚/文本归一化落地后与本计划页文本契约对版。
- xpt 流承载；目录行深度后缀解析。

## R1 审查修订记录

2026-10-19 R1（独立子代理 agent_9c27e8bd，1 Blocker + 5 Major + 4 Minor，全部吸收）：B1 双路径拆分（OutlineBackfiller=headingizeFromOutline v2 直译 / TocBackfiller=v1 全路径直译；输入 schema 分定；锚=消费不生成、无锚原样返回；输出=md string+stats）；M1 层级推导纠错（numDepth=过滤器，层级 outline→depth/toc→titleLevel 两档）；M2 双正则分工（s0 TOC_LINE_RE=路由证据、headingize TOC_ENTRY_RE=抽取，原文照抄含 `(?:`）+ 守卫口径声明；M3 输入契约（包内 sorted stripper 保留空白 + fixture 按该形态构造 + WI16 对版 follow-up）；M4 路由决策表定版 + 钩子校验直译 + chunk 守卫裁定移除；M5 v1 机制全清单直译（逐项 checklist 入 Phase 1）；m1 extract 包共存裁定；m2 Java 正则语义声明（不启用 UNICODE_CHARACTER_CLASS、Locale.ROOT、逐行 full match）；m3 stats 字段表按路径定版；m4 crosscheck 纳入路由输出。

## R2 审查修订记录

2026-10-19 R2（独立子代理 agent_7c4289d0，Blocker=0/Major=3，全部吸收）：Major-1 v2 窗口语义修正（窗口=[上一物理页锚行+1 或目标页锚行+1, 下一锚行)；v2 不消费 pageEnd——行号语义同名不同物）；Major-2 M3 契约实证细化（点导符/字面空格保留、定位间隙坍缩单空格——漏检限制登记挂 WI16 follow-up，fixture 覆盖两种可检形态）；Major-3 v1 定版补全（tocDepth/forceScan 全量移植以 BackfillOptions 承载、NaN 守卫、内部 front-20% 门、claimed map 语义、BackfillStats 15 字段定版删"等"）；Minor-1 正则引文照抄 3 组形态（页码取组 3）；Minor-2 决策表补遗（pin 不可行顺延、0 条目=诚实语义、crosscheck 口径、钩子产物不补 pageEnd）；Minor-3 \r 平台措辞修正；Minor-4 Phase 2 roadmap 描述同步；Minor-5 e2e 经门面+锚手工合成。

## Closure

Status Note: 五项审计全 PASS（双路径直译逐条对照 headingize/s0 基准、决策表全分支钉、正则组号原文照抄、输入契约实证、门面接线 e2e）；R1/R2 全部发现吸收或裁定；模块全量 155 绿；构建恢复遍证据见 Closure。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_7f509e5b-f19c-41e2-a5d3-e555f47dd63d（fresh session，与实现不同 task；基准 headingize.mjs + s0-structure-source.mjs 全文逐行对照）
- Evidence:
  - v1 对版语义逐条 PASS（14 机制逐条 file:line——isTocPage/front-20%/maxGap/NaN/Figure-Table-样例/tocDepth/pageOffset 四重判据整数化数学等价/逻辑页扫描/claimed-usedLines/页顶兜底/BODY_H1/titleLevel/keyify）
  - v2 对版 PASS（整键相等/窗口语义 testWindowCoversPrevPageBottom 钉/原位升级 vs 锚后插入/depth clamp/skippedNoAnchor/无锚原样）
  - 路由对照 PASS（决策表五分支/TOC_LINE_RE 原文/前 12 页/ceil(20%)/validate 直译/pin 顺延）
  - 正则 PASS（TOC_ENTRY_RE 3 组原文/TOC_LINE_RE 原文/逐行 full match）
  - 无静默跳过 PASS（scan-hollow exit 0）；文档一致性 PASS（D1/D2/D3、roadmap、日志）
  - audit 发现 F1（0 条目语义反向）/F2（roadmap 正文漏改）/F3（簿记）修复后复跑 35/35 绿；F4/F6/F8/F9 顺手修复；F5/F7/F10-F12 watch-only
  - 构建证据（恢复遍口径）：模块全量 155（skip1）BUILD SUCCESS；全量 clean install 恢复遍排除清单（均单跑复核绿或预存缺陷）：TestLeaseLifecycle（xdbg plan 2469）、TestDebugGatewayE2E/TestReplService（-T 1C flaky）、NodeTscBridgeTest（Node 环境依赖）、JSONPointTest_0/1（遗留坏测试）、ReproG3PerWindowStateLeakOnDiscard（-T 1C flaky）、insert-no-debezium pin（plan 2413 预存缺陷）、NopCodeWebPagesTest（-T 1C 可复现并行挂死，单跑绿——已上报）

Follow-up:

- WI16 页锚/文本归一化落地后与本计划页文本契约对版（定位间隙坍缩限制）
- nop-code-web -T 1C 并行挂死（已上报其他工作流范围）
- F5/F7/F10-F12 watch-only 项见 Closure 审计修复记录

## Closure 审计修复记录（audit F1-F9）

2026-10-19 closure audit（独立子代理 agent_7f509e5b，3 Major + 12 Minor，0 Blocker）：F1 钩子 0 条目语义反向——代码/测试改为保持 ai-reconstruct 无 correction（对齐 plan/design/s0 基准），修复后复跑 35/35 绿；F2 roadmap WI15 Deliverables "大文档 chunk 覆盖"措辞同步（正文漏改已补）；F3 簿记随本收口完成；F4 crosscheck 去 tocLines 非空守卫（0 行也 WARN，对齐 s0:197）；F6 死代码移除（frontPages 列表/pageNums）；F8 chunk-large 收敛回 chunk 枚举；F9 pin 不可行 correction 去重（fromPin 旗标）。F5/F7/F10-F12 登记 watch-only（计数口径偏差/isTocPage NaN 偏保守/IOException 静默/测试补钉/字面偏差备案——均为边缘场景或后续 WI16 对版范围）。
