# 2483 WI14 PDF outline（书签）抽取

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI14，deps WI0 已完成）
> Related: card-kb 语义基准 `~/workspace/card-knowledge-base/tools/analyzed-pipeline/src/core/pdfio.mjs`（extractOutlineEntries/destToPageIndex）、`src/core/stages/s0-structure-source.mjs`（fillPageEnds/dedupeEntries/statsOf）、契约样本 `~/workspace/card-knowledge-base/analyzed/**/outline.yaml`
> Review: R1 对抗性审查完成（1 Blocker + 3 Major + 4 Minor 全部吸收——PDFBox 3.0.8 真实 API 名经 jar 核实；见修订记录）

## Purpose

nop-pdf 能把 PDF 原生 outline（书签树）抽取为扁平结构化条目（title/depth/page/page-end + stats），语义与 card-knowledge-base 的 pdfjs 实现逐条对齐，支撑规范文档知识化流水线（纯 Java/PDFBox，不依赖外部引擎）。

## Current Baseline

2026-10-19 核对：

- nop-pdf 无任何 outline 抽取（PdfDoc 无 catalog/outline 触达；grep 证实无 PDOutline 使用）。
- card-kb 语义基准（已逐行核对）：
  - dest 解析（pdfio.mjs destToPageIndex :19-38）：dest 为字符串→命名目标查找（找不到→null）；数组首元素为页引用对象→页索引（0-based→输出 +1）；首元素为 number→显式页号；异常/空→null。
  - 扁平化（extractOutlineEntries :40-69）：getOutline 异常/非数组→**空列表**（坏 outline ≡ 无 outline）；空 title 节点不下发条目但下钻 children（同 depth）；**父节点也解析自身 dest**（pypdf flatten parity）；title trim；文档序保留。
  - **处理顺序（R1-M3：顺序本身是对版契约）**：抽取 → 去重 → page-end → stats（s0 :113-114 先 dedupeEntries 后 fillPageEnds，反序会产生错误 page-end——如 A(X,5)/B(X,5)/C(Y,6) 正确序 A.page-end=6、反序 A.page-end=5）。
  - page-end（s0 fillPageEnds :38-45）：仅对 page!=null 条目——取其后第一条带页条目的 page（无则 total-pages），并 cap 到 total-pages；page==null 条目无 page-end。
  - 去重（dedupeEntries :47-58）：key=lowercase(title)+\u0000+page，保留首条。
  - stats（statsOf :60-66）：entries 条数 / max-depth（空→0）/ unresolved-pages（page==null 计数）。
  - pdfjs 防御面：getOutline 用 RefSet 查重防循环链（环被跳过正常终止）；dest 经 /A（GoTo→D）优先、/Dest 兜底解析（pdf.worker :45013-45037）。
- outline.yaml 契约（样本 `analyzed/amex/mns/implementing-iso-format-4-pin-blocks-information-supplement/outline.yaml`）：target/source/outline-source/total-pages/entries[{title,depth,page,page-end}]/stats{entries,max-depth,unresolved-pages}。YAML 输出可用 snakeyaml（仓库既有依赖）。

## Goals

- `PdfDoc.extractOutline()`（或独立 OutlineExtractor）返回 `List<PdfOutlineEntry{title, depth(1-based), page(1-based|null), pageEnd(Integer|null)}>` + stats，逐条语义对齐 pdfio.mjs/s0（命名目标/页引用/显式页号/父节点目标/文档序/坏 outline 空列表/page-end/去重/stats）。
- outline.yaml 输出（与契约样本字段一致，供流水线直接消费）。
- 合成 PDF fixture 对版测试（PDFBox 构造 outline 树，覆盖全部 dest 形态与坏 outline）。

## Non-Goals

- 目录页正则/结构源路由/标题回填（WI15）；图片普查（WI17）。
- 不把 card-kb 的 Node 工具或原始 PDF 带进本仓库（roadmap 硬边界；fixture 全部程序合成）。
- OCR/AI 重建（outline-source=ai-reconstruct 属使用方）。

## Scope

### In Scope

- `io.nop.pdf.outline`（新包）：PdfOutlineEntry 值对象 + PdfOutlineStats + 抽取器（PdfDoc 门面方法）+ outline.yaml writer。
- 测试：合成 PDF 覆盖——普通页引用、命名目标、显式页号、父节点带 dest、空 title 下钻、坏 outline（构造损坏 catalog）→空、(title,page) 去重、page-end 推算（含末条→total-pages cap）、unresolved 计数、YAML 输出快照断言。
- 对版：合成用例的期望值直接按 pdfio.mjs 语义手算断言（结构语义一致）；如本机 card-kb 样本可读，可选做一次运行时对版冒烟（不落仓库）。

### Out Of Scope

- 使用方流水线（S0 路由决策、headingize）；outline 写入/编辑。

## Execution Plan

### Phase 1 - 抽取器与契约输出

Status: completed
Targets: `io/nop/pdf/outline/*`（新增）、`PdfDoc.java`、测试

- Item Types: `Feature`

- [x] PdfOutlineEntry（title/depth/page/page-end）+ PdfOutlineStats（entries/maxDepth/unresolvedPages）+ `PdfOutline{entries, stats}` 载体（record，R1-m3）；`PdfDoc.extractOutline()`：getDocumentOutline() 树遍历——**PDFBox 3.0.8 真实 API（R1-B1，经 jar 核实）**：`PDOutlineNode.children()`（Iterable<PDOutlineItem>，无 findChildren）+ `PDOutlineItem.getNextSibling()`；**环守卫**：遍历带 visited 集合（COSDictionary 身份，语义对齐 pdfjs RefSet——PDFBox 裸走 /First//Next 遇环不抛，无守卫会死循环到 OOM，R1-M1）；**页解析**：条目 `getDestination()` 为空时取 `getAction() instanceof PDActionGoTo` 的 destination（R1-M2，/A 优先 /Dest 兜底对齐 pdfjs）；dest 解析两形态——`PDNamedDestination` → `catalog.findNamedDestinationPage(...)`（同时查 /Names/Dests name tree 与 legacy /Dests，未命中 null），`PDPageDestination` → `retrievePageNumber()`（页引用与显式页号通吃，**-1 与异常一律归 null**；`PDDestination.create()` 对 [N] 单元素数组等怪形态抛 IOException → page null，属可接受降级差异）；父节点解析自身 dest；坏 outline（根 null / /Outlines 非 dict / 无 /First）→空列表；`getTitle()` 可 null——trim 需 null-safe（对齐 `String(item.title||'')`）。
- [x] **规范顺序：抽取 → 去重 → page-end → stats（R1-M3：顺序是对版契约的一部分）**；算法照 s0 dedupeEntries（key=lowercase(title)+\u0000+page）/fillPageEnds（下一条带页条目，cap total-pages，page==null 无 page-end）/statsOf 逐条翻译。
- [x] outline.yaml writer（PdfOutlineYamlWriter）：nop-format/nop-pdf/pom.xml **显式声明 org.yaml:snakeyaml**（无版本号走 BOM，R1-m1）；输出用 LinkedHashMap（snakeyaml 按 entrySet 序输出保字段序），每条目为 Map：`page` 为 null 时**键保留值 null**、`page-end` 键仅 page!=null 时写入（R1-m2：对齐契约样本 null 语义，避免 POJO dump 的 camelCase+null 键+!!fqcn 标签三重不符）；outline-source 固定 "pdf-outline"，target/source 由 writer 方法入参传入（与 extractOutline 分离，R1-m3）。PdfDoc 门面：extractOutline() + extractOutlineYaml(target, source)。
- [x] 测试：TestPdfOutlineExtract 10 个——PDFBox 合成 fixture（页引用/命名目标 legacy /Dests/显式页号/空 title 同 depth 下钻/自环终止不重复（R1-M1）//A GoTo 书签（R1-M2）/坏 outline 降级（无 /Outlines、空 outline——R1-m4）/(title,page) 去重/page-end 推算含末条 cap/unresolved 计数）；YAML 输出字段与 null 语义断言 + 落盘回读端到端。

Exit Criteria:

- [x] 测试全绿且覆盖全部 dest 形态/坏 outline/去重/page-end/stats；**端到端验证**：合成 PDF→extractOutline→YAML 落盘→重读解析一致
- [x] `./mvnw test -pl nop-format/nop-pdf -am` 全绿（模块全量 118 含 skip1）
- [x] No owner-doc update required（能力矩阵 WI18 收口）
- [x] `ai-dev/logs/` 对应日期条目已更新

执行偏差（Phase 1）：

- 公共 API 无 PDOutlineNode.children()——实际用 getFirstChild()/getNextSibling() 兄弟链遍历（R1-B1 修正在执行时的微调）；COSNumber 抽象（用 COSInteger.get）、COSName 无 FIT 常量（用 getPDFName("Fit")）、legacy /Dests 数组的 fit token 同理。
- javadoc 中 `analyzed/**/outline.yaml` 的 `**/` 会终止 javadoc 注释——措辞规避。
- PdfOutline 载体用可变 class 而非 record（page-end 就地回填的实现选择）——closure audit Minor 项，此处登记。
- closure audit 发现 1（/Dest 优先 vs pdfjs /A 优先的角落偏差 + javadoc 矛盾）：修代码为严格 pdfjs 序（/A 存在即 /Dest 不参与——GoTo 解析 D、非 GoTo 归 null；仅 /A 缺失才读 /Dest），并补 2 个测试钉（非 GoTo /A + 有效 /Dest → null；GoTo /A 压过 /Dest）与 /Outlines 非 dict 降级用例（Minor-5）；修复后 TestPdfOutlineExtract 12/12 绿。

### Phase 2 - 对版与收口

Status: completed
Targets: roadmap、日志

- Item Types: `Proof`

- [x] （可选，环境允许时）以本机 card-kb 样本 PDF 跑冒烟对版——已执行（临时 harness 运行后删除，不落仓库）：amex/mns/implementing-iso-format-4-pin-blocks-information-supplement.pdf **39/39 条 entries 逐条一致（title/depth/page 1:1）、total-pages 23=23、mismatch=0**；另一 visa/vts 样本 golden 本身 entries 为空亦一致。结论记入日志。
- [x] roadmap WI14 勾选；check 工具全过；全量构建（恢复遍口径，见 Closure）。

Exit Criteria:

- [x] roadmap Work Item Status WI14 勾选
- [x] `ai-dev/logs/` 对应日期条目记录完成状态（含对版结论）
- [x] `./mvnw clean install -T 1C`（或恢复遍证据）

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] 抽取语义有 focused 测试证明（逐形态断言非 smoke）
- [x] 受影响 owner docs：No owner-doc update required（WI18 收口）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2483-wi14-pdf-outline-extraction.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- outline.xml/JSON 输出格式（出现需求时）。

## R1 审查修订记录

2026-10-19 R1（独立子代理 agent_e0cf1e2d，1 Blocker + 3 Major + 4 Minor，全部吸收）：B1 四个 PDFBox API 名不存在——替换为 jar 核实的真实 API（children()/getNextSibling()、findNamedDestinationPage 双源查找、retrievePageNumber 通吃两形态且 -1 归 null、PDDestination.create 怪形态 IOException→page null）；M1 环守卫（visited 集合对齐 pdfjs RefSet）+ 自环 fixture；M2 /A GoTo action 兜底 + fixture；M3 规范顺序钉死（抽取→去重→page-end→stats，顺序即对版契约）；m1 snakeyaml 显式声明；m2 YAML null/缺键规则（LinkedHashMap 保序、page-end 键条件写入）；m3 PdfOutline 载体 + writer 分离；m4 坏 outline 三构造具体化 + getTitle null-safe。

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] 抽取器语义逐形态测试钉住（四 dest 形态/环守卫/去重顺序/page-end/stats/YAML null 语义非 smoke）
- [x] 受影响 owner docs：No owner-doc update required（能力矩阵 WI18 收口）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2483-wi14-pdf-outline-extraction.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure

Status Note: 六项审计全 PASS（对版语义与 pdfjs/pdfio/s0 逐条 1:1、YAML 契约、无静默跳过、文档一致、遗留走查）；audit 发现 1（/A vs /Dest 优先级角落偏差）已修代码为严格 pdfjs 序 + 2 测试钉，发现 3/4/5 簿记随本收口完成，发现 2（提交）随后执行。真实样本对版 39/39 一致。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_0b2892f9-210e-4707-9392-1be327c5811e（fresh session，与实现不同 task；基准 pdfjs-dist 6.4.299 + pdfio.mjs + s0 + PDFBox 3.0.8 字节码）
- Evidence:
  - 对版语义逐条对照 PASS（named 双源查找/page-dest retrievePageNumber -1 归 null/空 title 同 depth/父节点自解析/先序等价/环守卫按 COS 身份/先去重后 page-end 钉/dedupe key/page-end cap/stats 三项）
  - YAML 契约 PASS（字段序/null 键/outline-source 固定/target-source 入参/snakeyaml 显式声明）
  - 无静默跳过 PASS（异常全归 null/空；scan-hollow exit 0）
  - 文档一致性 PASS（roadmap WI14 勾选、日志 39/39 对版结论与 golden 数值独立吻合、三工具 exit 0）
  - 发现 1 修复：resolvePage 改严格 pdfjs 序 + testActionOverridesDestWhenBothPresent/testNonDictOutlinesDegradesToEmpty 两钉，TestPdfOutlineExtract 12/12 绿
  - 构建证据（恢复遍口径）：模块全量 118（skip1）BUILD SUCCESS；全量 clean install 恢复遍排除清单（均单跑复核绿或预存缺陷）：TestLeaseLifecycle（xdbg plan 2469）、TestDebugGatewayE2E/TestReplService（-T 1C flaky）、NodeTscBridgeTest（Node 环境依赖）、JSONPointTest_0/1（遗留坏测试）、ReproG3PerWindowStateLeakOnDiscard（-T 1C flaky）、nop-stream-connector-jdbc insert-no-debezium pin（plan 2413 预存缺陷）

Follow-up:

- 其他工作流预存缺陷（TestLeaseLifecycle/insert-no-debezium pin）已如实上报
- 命名目标 name-tree 路径（/Names/Dests）的合成 fixture 未覆盖（legacy /Dests 路径已钉；name tree 走同一 findNamedDestinationPage 入口）——watch-only
