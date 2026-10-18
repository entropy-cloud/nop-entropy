# 2463 WI0 PDF 视觉验收工具链（参考实现 + golden/diff 工具 + 验收口径）

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI0）
> Related: `ai-dev/plans/2260-nop-report-pdf-quality.md`（已完成，TestPdfExportAudit 雏形来源）

## Purpose

落实 roadmap WI0：建立"实际渲染截图 + golden/图像 diff"验收能力，作为本 roadmap 全部渲染类 WI（WI1–WI11、WI18）的完成门。同时收口参考实现下载状态，并产出验收口径裁定文档。

## Current Baseline

2026-10-19 逐项核对 live repo：

- PDFBox 引擎基线已是 **3.0.8**：`nop-kernel/nop-dependencies/pom.xml:107` `<pdfbox.version>3.0.8</pdfbox.version>`，nop-pdf/nop-report-pdf 均经 dependencyManagement 解析，无模块级 pin。roadmap WI0 的"引擎基线升级"子项已完成。
- 参考实现：`~/sources/pdfbox`、`~/sources/OpenPDF`、`~/sources/jasperreports` 已克隆成功；`~/sources/print/` 有 8 个打印/报表设计器。x-easypdf：roadmap 所记 URL `dromara/x-easypdf` 已失效（勘误核实 404；`~/sources/x-easypdf.clone.log` 记录对旧 URL 的 60 次网络重试全失败）；2026-10-19 核实实际仓库为 `xsx344646090/x-easypdf` 并已用重试脚本克隆成功（`~/sources/x-easypdf`）。
- 仓库现无任何像素 diff / golden 快照工具（roadmap 已声明，本计划执行时以 grep 复核）。
- 视觉验收雏形：`nop-report/nop-report-demo/src/test/java/io/nop/report/demo/TestPdfExportAudit.java` 用 `PDFRenderer.renderImageWithDPI(i, 110)` 逐页转 PNG 落 `_tmp/report-pdf-audit/`，仅人工查看，无 golden/diff 断言。
- nop-format/nop-pdf 现有包：`core`/`extract`/`tabula`/`utils`（main），`core`/`extract`/`render`/`tabula`（test）；无 verify/视觉比对类。
- 错误处理惯例：nop-pdf 现有代码用 `NopException.adapt(e)` 包装 PDFBox IOException（如 `PdfDoc.java:40`），无模块级 ErrorCode 注册表。
- 依赖关系：nop-report-pdf 生产依赖不含 nop-pdf（两者平行依赖 pdfbox）；nop-report-demo → nop-report-pdf。`.gitignore:123` 忽略 `_tmp/`。
- plan 编号：当前最大 2462，本计划取 2463。

## Goals

- nop-pdf 提供 PDF 视觉验收工具包（`io.nop.pdf.verify`）：固定 DPI 渲染 PDF→PNG、golden 快照保存/比对、图像 diff（像素容差 + 差异像素占比 + 灰度窗口 SSIM）、指定区域颜色/线宽采样断言。
- 工具自身有 JUnit 5 单测覆盖（程序合成 PDF fixture，不依赖外部样本或系统 CJK 字体）。
- 验收口径裁定文档落 `ai-dev/analysis/2026-10/`：DPI 口径、抗锯齿容差、diff 阈值语义、金标准更新流程、区域采样用法、参考实现索引与 x-easypdf 裁定。
- 参考实现状态收口：pdfbox/OpenPDF/jasperreports/print 就位核对；x-easypdf（URL 勘误后已克隆）就位核对。

## Non-Goals

- 任何渲染器功能改动（WI1–WI11 范围）。
- golden PNG 快照入库（字体光栅化随环境变化，跨机器像素不可移植；golden 只落 `_tmp/`，可移植门是区域结构断言——理由见 Phase 3 裁定文档）。
- 引入外部图像比较库（OpenCV/imgdiff 等）；SSIM 用自带灰度窗口实现。
- 对 nop-report-pdf 或 nop-report-demo 的任何生产代码改动；nop-report 侧接入本工具发生在后续 WI 各自 plan 中（以 test-scope 依赖引入）。
- OCR、内容理解、PDF 结构抽取（WI14–WI17）。

## Scope

### In Scope

- 新增 `nop-format/nop-pdf/src/main/java/io/nop/pdf/verify/`：渲染、golden、diff、区域采样四类支撑类。
- 新增 `nop-format/nop-pdf/src/test/java/io/nop/pdf/verify/`：合成 PDF fixture + 工具单测。
- 新增 `ai-dev/analysis/2026-10/2026-10-19-pdf-visual-acceptance-toolchain.md`。
- 更新 `ai-dev/backlog/nop-pdf-report-export-roadmap.md` 的 WI0 勾选与 Milestone「视觉验收工具链就绪」。
- 更新当日 `ai-dev/logs/2026/10-19.md`。
- x-easypdf URL 勘误记录（dromara→xsx344646090）。

### Out Of Scope

- `~/sources/` 下参考仓库的内容阅读与对版分析（后续 WI 按需进行）。
- CI 门禁改造（本工具是测试支撑能力，不改动任何 CI 配置）。

## Execution Plan

### Phase 1 - 参考实现状态收口

Status: completed
Targets: `~/sources/`（仓库外）、本 plan、Phase 3 裁定文档

- Item Types: `Decision | Proof`

- [x] 核对 `~/sources/pdfbox`、`~/sources/OpenPDF`、`~/sources/jasperreports`、`~/sources/x-easypdf`（URL 勘误后已克隆）为有效 git 克隆（非空、含 pom/构建入口），`~/sources/print/` 8 个设计器目录非空；结果记入裁定文档"参考实现索引"节（含 dromara→xsx344646090 URL 勘误记录）。

Exit Criteria:

- [x] 裁定文档含参考实现索引表（仓库名、用途、状态），x-easypdf 有明确状态与裁定
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 视觉验收工具包（nop-pdf verify 包）

Status: completed
Targets: `nop-format/nop-pdf/src/main/java/io/nop/pdf/verify/`、`nop-format/nop-pdf/src/test/java/io/nop/pdf/verify/`

- Item Types: `Feature | Decision`

- [x] **Decision（包落位裁定）**：roadmap 原文为"落到 nop-format/nop-pdf 测试支撑包"；本计划裁定为 `src/main/java/io/nop/pdf/verify/`（生产 jar），理由：(a) 后续 WI（nop-report-pdf/nop-report-demo 侧）用普通 test-scope 依赖即可消费，免 test-jar 双端配置；(b) 该包定位是"PDF 视觉验收支撑工具"（render/compare/probe 原语），属 nop-pdf 通用工具箱能力面而非领域 API；(c) 断言辅助抛 `AssertionError` 的契约随之成为该包定位的一部分（供测试代码调用的验收原语，不作为业务 API 错误通道），与 `NopException.adapt`（IO/运行时错误）分工——三分裁定全部记入 Phase 3 裁定文档。
- [x] `PdfImageRenderer`：`PDDocument` → 逐页 `BufferedImage`，DPI 参数化，类常量固定默认 DPI=110（与 TestPdfExportAudit 口径一致，理由记裁定文档）。
- [x] `ImageDiff`：给定两张同尺寸图，输出 diff 结果对象——逐像素通道容差（默认每通道 ≤8/255 记为相同，抗锯齿归一化口径）、差异像素占比、灰度 8×8 窗口 SSIM；尺寸不一致显式报错不静默。结果对象携带建议阈值常量（SSIM ≥0.99、差异占比 ≤0.1%），**属占位默认值，以 Phase 3 裁定文档最终口径为准**。
- [x] `PdfGoldenManager`：以名称 + 页码管理 golden PNG；golden 根目录解析裁定：优先显式传入目录；静态便捷入口从 `user.dir` 向上查找 `.git` 定位仓库根，落 `<repo-root>/_tmp/pdf-golden/<name>/`（与 `.gitignore:123` 的 `_tmp/` 忽略一致，避免 TestPdfExportAudit 式硬编码相对路径）；比对模式：golden 缺失时生成并明确报告"已创建基线"（返回可区分的结果状态，不伪装成 pass）；更新模式：系统属性显式开启时覆盖 golden。
- [x] `RegionProbe`：矩形区域平均色采样；扫描线线宽估计（对指定水平/垂直线，统计与背景色差超容差的连续像素游程）。
- [x] 断言辅助：diff/采样结果对象提供 `assertClose(...)` 风格方法，失败抛 `AssertionError`（英文消息，含实测/期望/阈值），使 JUnit 自然呈现；IO 异常沿用 `NopException.adapt` 惯例。
- [x] 单测：程序合成 PDF（`PDPageContentStream` 画已知颜色矩形、已知宽度直线、多页）→ 渲染 → golden 首建/二次比对 pass/扰动（改颜色/线宽）后 diff 必红 → 区域颜色/线宽采样值正确 → 尺寸不一致必报错。

Exit Criteria:

- [x] `io.nop.pdf.verify` 包四类支撑类落地，公共方法均有 Javadoc 说明口径语义
- [x] 新增单测全部通过：golden 生命周期（创建→pass→扰动 fail→更新模式恢复 pass）、颜色/线宽采样、SSIM 对相同图=1、尺寸不一致显式失败
- [x] **无静默跳过**：golden 缺失不伪装 pass（返回"基线已创建"状态）；尺寸不一致抛错而非返回 0 差异
- [x] 无新公共 ErrorCode 注册需求（沿用 `NopException.adapt` + `AssertionError` 两类，理由记裁定文档）；`No owner-doc update required`（nop-pdf 用户能力矩阵在 WI18 统一收口，本 Phase 仅测试支撑能力，不改变现有用户可见行为）
- [x] `./mvnw test -pl nop-format/nop-pdf -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 验收口径裁定文档 + roadmap/日志收口

Status: completed
Targets: `ai-dev/analysis/2026-10/2026-10-19-pdf-visual-acceptance-toolchain.md`、`ai-dev/backlog/nop-pdf-report-export-roadmap.md`、`ai-dev/logs/2026/10-19.md`

- Item Types: `Decision | Proof`

- [x] 裁定文档：DPI=110 口径理由；抗锯齿归一化 = 逐像素通道容差 8/255 + 差异占比阈值语义；SSIM 窗口/灰度口径；golden 存 `_tmp/` 不入库的环境可移植性理由与更新流程（删除即重建、更新模式开关）；**标准 14 字体（Helvetica 等）非内嵌、渲染时按环境字体替换的确定性边界**（单测 fixture 以纯矢量图形规避文本，golden 同机重渲稳定、跨机仅区域结构断言可移植）；区域线宽/颜色采样作为跨环境可移植断言门的用法；verify 包 main-scope 落位与 AssertionError/NopException 分工裁定（Phase 2 Decision 项）；后续渲染类 WI 的验收操作口径（先建 golden→改渲染→diff 必红→修复→diff 转绿）；参考实现索引与 x-easypdf 裁定（Phase 1 产出；含 roadmap 原 URL dromara/x-easypdf 404、实际仓库 xsx344646090/x-easypdf 的勘误）。
- [x] roadmap：WI0 勾选 `[x]`，Milestone「视觉验收工具链就绪」勾选（依赖 WI0 完成，派生勾选）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

- [x] 裁定文档存在且覆盖上述全部裁定点，被 roadmap WI0 条目语义引用的内容一致
- [x] roadmap Work Item Status 中 WI0 已勾选，Milestone 同步
- [x] `ai-dev/logs/2026/10-19.md` 已记录本计划完成状态
- [x] check-doc-links 退出码 0

## Closure Gates

- [x] Phase 1–3 全部 Exit Criteria 勾选
- [x] 工具单测证明 golden/diff/采样三类能力真实可用（扰动必红，非仅 happy-path）
- [x] `./mvnw test -pl nop-format/nop-pdf -am` 全绿
- [x] 受影响 owner docs：`No owner-doc update required`（测试支撑能力，WI18 统一收口能力矩阵）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2463-wi0-pdf-visual-acceptance-toolchain.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。x-easypdf 经 URL 勘误（dromara/x-easypdf 404 → xsx344646090/x-easypdf）后克隆成功，原"克隆缺失"deferred 预案未触发。

## Non-Blocking Follow-ups

- 后续 WI 在 nop-report 侧接入本工具时，统一在各自 plan 中声明 test-scope 依赖（nop-report-demo/nop-report-pdf test → nop-pdf）。

## Closure

Status Note: WI0 三 Phase 全部完成：verify 工具包落地且 16 个新单测证明 golden/diff/采样能力真实可用（扰动必红非 happy-path）；口径裁定文档（D1–D8 + 参考实现索引 + x-easypdf URL 勘误）已落 ai-dev/analysis/2026-10/；roadmap WI0 与「视觉验收工具链就绪」Milestone 已勾选。独立子 agent closure audit PASS（无 Blocker/Major；Minor-1 收口补勾已执行）。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_502861ae-2df5-4ef5-9b6d-0b5f95091fc6）
- Audit Session: agent_502861ae-2df5-4ef5-9b6d-0b5f95091fc6（2026-10-19）
- Evidence:
  - Phase 1 Exit Criteria：PASS（裁定文档:70-78 参考索引表 + 5 仓库实测有效克隆；logs/2026/10-19.md:3-10）
  - Phase 2 Exit Criteria：PASS（7 文件逐个实读，公共方法均有口径 Javadoc；16/16 测试独立重跑绿；无静默跳过——golden 缺失 BASELINE_CREATED 且 assertPass 抛错、扰动 FAIL 带 diff 数据、尺寸不一致 IllegalArgumentException；仅 NopException.adapt + AssertionError，无新 ErrorCode）
  - Phase 3 Exit Criteria：PASS（裁定文档覆盖 plan:103 全部 9 个裁定点 D1–D8+索引+勘误；roadmap:42 WI0 [x]、roadmap:86 Milestone [x]；check-doc-links --strict exit 0 独立重跑）
  - Anti-Hollow 三条行为链：PASS（compare:96-108 + GoldenCompareResult.assertPass:79-88 + TestPdfGoldenManager:50-65 + TestImageDiff:34-49 的 16 像素算术验证——断言真的会红）；scan-hollow-implementations --module nop-format/nop-pdf --severity high exit 0
  - 测试真实性抽查：TestRegionProbe 坐标换算复算吻合（2pt@110dpi=3.06px，区间 2–5）；fixture 纯矢量无文本（D5 边界成立）
  - `./mvnw test -pl nop-format/nop-pdf -am`：88 tests, 0 failures, BUILD SUCCESS
  - Deferred 项分类检查：Deferred=无（诚实）；Follow-ups 与 Non-Goals 一致，无 in-scope live defect 被降级
  - 审计附带 Minor（不阻塞）：averageColor 无边界校验（越界仍 fail-loud 抛 AIOOBE，watch-only）；roadmap"测试支撑包"措辞与 main-scope 落位偏差已由 D7 显式裁定（watch-only，WI18 勘误）

Follow-up:

- nop-report 侧（nop-report-pdf test / nop-report-demo test）接入 verify 工具时声明 test-scope 依赖——归后续渲染类 WI 各自 plan 承载
- 占位阈值 0.1%/0.99 若实战误报/漏报，由首个消费 WI 回写裁定文档 Open Questions
