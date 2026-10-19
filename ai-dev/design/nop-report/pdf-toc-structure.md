# nop-pdf 目录检测与结构源路由

> Status: active
> Last Reviewed: 2026-10-19
> Source: plan 2484（`ai-dev/backlog/nop-pdf-report-export-roadmap.md` WI15）

## 决策

### D1 双回填路径（headingize 两套互斥算法直译）

- **v2 OutlineBackfiller**（headingizeFromOutline :70-126 直译）：输入 `List<PdfOutlineEntry>`（WI14 产物，v2 只消费 title/page/depth——**pageEnd 不消费**，仅随 schema 携带）。整键相等（`keyify(line) === keyify(title)`，非前缀匹配）；搜索窗口=[上一物理页有锚时其锚行+1，否则目标页锚行+1 → 目标页块末尾（下一锚行）]（章节 opener 常在上一页底部）；命中原位升级 `#`，未命中插在目标页锚行后；层级=depth clamp 1..6；stats={outlineEntries, inserted, replaced, unmatched, skippedNoAnchor}（基准返回的 insertedKeys 集合裁剪）。
- **v1 TocBackfiller**（headingize :128-358 全机制直译）：输入=带锚 md（组件内部自行 isTocPage/抽取，非消费 TocLine 列表）。机制逐项：isTocPage（每页≥3 命中）、内部 front-20% 门、目录带连续扩展（maxGap=3）、条目<3 整体放弃、NaN 伪页码守卫（字母前缀如 "V17" 整条丢弃）、List of Figures/Tables 过滤、示例行过滤（文件后缀/filename:/多下划线 token）、tocDepth 深度过滤（numDepth+缩进分层）、pageOffset 扫描（off 2..40；采纳判据四重：≥10 / ≥1.2×base / ≥1.5×次优 / ≥50% eligible）、逻辑页单调扫描（scanFrom 游标 + extra≤12 前缀回退）、claimed/usedLines 去重（同 key 同行=duplicateSkipped、同 key 异行=unmatched）、SOURCE_HEADING_LIKE（`#` 开头正文行守卫——兜底落点跳过此类行）、BODY_H1 独立 pass（TOC gate 之后）、层级=titleLevel 两档；opts 全量移植（tocDepth/forceScan，Java 端 BackfillOptions 承载）。
- **层级裁定（R1-M1）**：编号点分段数（numDepth）仅是 tocDepth 过滤器，从不决定 `#` 层数；层级来源 outline=entry.depth / toc=titleLevel 两档。

### D2 路由决策表（确定性链）

s0 中 toc-regex/ai-reconstruct/chunk 之选是 LLM decide，本计划改为确定性链（roadmap 裁定"AI 重建属使用方"）：

1. outline 非空 → outline；
2. 显式指定 source 且可行 → 按指定（守卫修正仍记录；pin 不可行/钩子 null 时顺延后续分支并记 correction）；
3. tocFirstPage（s0 TOC_LINE_RE 在前 12 页首命中）≤ ceil(nPages×20%) → toc-regex；
4. 钩子非空 → 前 10 页（每页 1500 字上限）调钩子 + 确定性校验（title≥3/depth clamp 1..6/page clamp/排序/去重）；校验失败降 chunk；**0 条目=诚实 0 条目**（非失败）；
5. 否则 chunk。

nPages≥20 的 chunk-override 守卫在确定性链中不保留（决策表自身保证）。corrections 审计轨迹与 crosscheck 告警（outline 条目数 vs 目录行数 [0.5, 2.0] 界外 WARN，目录行数用 TOC_LINE_RE 口径）进 RoutingDecision。钩子产物不补 pageEnd。

### D3 正则分工与文本契约

- **s0 TOC_LINE_RE**（无附件形态、无 `\r?`）：仅作路由证据扫描（前 12 页）。
- **headingize TOC_ENTRY_RE**（三页码形态含附件、行尾容 `\r`、3 组形态页码取组 3）：条目抽取。逐行 full match；不启用 UNICODE_CHARACTER_CLASS（全角/不可断空格由 keyify NFKC 兜底）；lowercase 用 Locale.ROOT。
- **SortedPageTexts 契约（实证）**：PDFTextStripper setSortByPosition(true)——点导符字形与字面空格 run 保留；**纯定位间隙坍缩为单空格**（writeLine 词间只写单分隔符）——此类目录行在 toc-regex 路径系统性漏检，已知限制挂 WI16 对版 follow-up（WI16 是 -layout 等价的 owner）。`\r`：POSIX 无、Windows 有 `\r\n`——TOC_ENTRY_RE 容 `\r` 保证两端一致。
- **锚语义**：页锚 `<a id="pdf-page-N"></a>` 为消费语义（使用方/WI16 产出，nop-pdf 不生成）；无锚时回填器原样返回 + 全零 stats。
- **与 extract 包共存**：SimpleTocDetector/TocTable 面向 ResourceDocument 行块管线保持不动；io.nop.pdf.toc 面向纯文本行独立演进。

## 涉及实现

- `nop-pdf/.../toc/`（TocLine/TocLineDetector/SortedPageTexts/StructureSourceRouter/ReconstructHook/RoutingDecision/BackfillOptions/BackfillStats/OutlineBackfiller/TocBackfiller/TocKeys）
- `PdfDoc.java` 门面（detectTocLines/routeStructureSource/backfillByOutline/backfillByToc + extractOutline 组合）

## 验证

- `TestTocLineDetector`（7）：三页码形态/\r/空格 leader/非目录行/短 title/批量/点空混合
- `TestTocBackfill`（11）：exact+兜底/条目<3 放弃/pageOffset 采纳/tocDepth 过滤/三过滤器/逻辑页扫描/duplicateSkipped/SOURCE_HEADING_LIKE/BODY_H1/forceScan/相邻页
- `TestStructureSourceRouter`（8）：决策表全分支/钩子校验 clamp/0 条目诚实/pin 不可行顺延/crosscheck/去重
- `TestOutlineBackfiller`（7）：整键替换/锚后插入/depth clamp/无锚 skip/窗口含上页底/无锚原样/短 title
- `TestTocEndToEnd`（2）：门面接线（真实写入文本的 PDF → 路由 → 回填 → md 结构）
