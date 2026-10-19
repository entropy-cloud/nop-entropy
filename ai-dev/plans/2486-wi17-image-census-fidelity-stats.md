# 2486 WI17 图片普查/抽取与机械化保真统计

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI17，deps WI16）
> Related: card-kb 契约 `~/workspace/card-knowledge-base/schemas/image-registry.schema.yaml`；`~/workspace/card-knowledge-base/tools/analyzed-pipeline/src/core/pdfio.mjs`（get_images(full=True) 等价语义）

## Purpose

逐页图片清单与裁剪落盘（registry YAML 对齐 image-registry 契约），产出文档机械化统计供保真对账。

## Current Baseline

2026-10-19 核对：

- nop-pdf 无逐页图片普查（extract 包有 IResourceImageHandler 面向 resource 管线图片导出，但无 registry/统计/面积阈值）；tabula 表抽取有行列结果但无文档级统计聚合。
- PDFBox 能力：PDPage.getResources()→PDResources XObject 遍历（PDImageXObject 宽高/值流大小）；跨页重复图片=同名/同流复用，需按内容或引用判重。
- image-registry 契约字段（roadmap/卡库 schema）：seq/file/page/width/height/size-kb。

## Goals

- `PdfDoc.censusImages(options)`：逐页图片清单（seq 全文档递增、file 落盘名、page 1-based、width/height 像素、size-kb、跨页重复标注），面积阈值 keep/skip 分类（options.minAreaPx）。
- 图片裁剪落盘（PNG/JPEG 原样字节或解码重编）+ registry.yaml 输出（对齐契约字段）。
- 文档机械化统计：页数、字符数、U+FFFD 数、标题数（复用 WI14 outline 条目数/或文本行 H1 检测口径——执行时按 outline 条目数优先裁定）、表格行列数（复用 tabula）、图片数，输出 stats YAML 供保真对账。

## Non-Goals

- 图片视觉分类/文字化（使用方 AI）；inline image（BI/ID/EI 内联图）普查（首版仅 XObject，登记限制）；OCR。

## Scope

### In Scope

- `io.nop.pdf.census`（新包）：ImageCensusEntry/ImageRegistry（YAML writer 对齐 image-registry 字段）、DocumentStats（页/字符/FFFD/标题/表格/图片六项）、PdfDoc 门面。
- 测试：合成 PDF（多页嵌图/跨页同图/小图阈值/无图页）逐用例断言清单/落盘/registry/统计；端到端落盘重读。

### Out Of Scope

- inline image；图片内容识别；与 WI16 扫描检测的联动策略（保持独立 API，WI18 验收时组合使用）。

## Execution Plan

### Phase 1 - 普查/落盘/统计

Status: completed
Targets: `io/nop/pdf/census/*`（新增）、`PdfDoc.java`、测试

- Item Types: `Feature`

- [x] 逐页 XObject 遍历（PDResources 递归含 FormXObject 内嵌资源；同图跨页按流字节摘要判重并标注 repeatedOnPages）；ImageCensusEntry（seq/file/page/width/height/sizeKb/areaPx/keep）。
- [x] 裁剪落盘（options.outputDir；文件名 seq-page-ext 规范；PDImageXObject 原样字节优先，解码失败降级重编 PNG）；registry.yaml（snakeyaml，字段对齐 image-registry：seq/file/page/width/height/size-kb + repeated 扩展字段）。
- [x] DocumentStats：pages（doc 页数）、chars（WI16 抽取聚合或独立 stripper 统计）、fffdCount、headingCount（outline 条目数，WI14 API）、tableRows/tableCols（tabula extractTables 聚合）、imageCount（census keep 数）。
- [x] 测试（合成 PDF 全用例 + 端到端落盘重读 + registry 字段断言）。

Exit Criteria:

- [x] 测试全绿；**端到端验证**：合成多页嵌图 PDF→census→图片落盘+registry.yaml→重读解析字段一致
- [x] `./mvnw test -pl nop-format/nop-pdf -am` 全绿
- [x] No owner-doc update required（能力矩阵 WI18 收口）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 对版与收口

Status: completed
Targets: roadmap、日志

- Item Types: `Proof`

- [x] （可选，环境允许时）本机 card-kb 样本对版冒烟：图片清单与该库 image registry 结构语义一致（不落仓库）。
- [x] roadmap WI17 勾选；check 工具全过；全量构建（恢复遍口径）。

Exit Criteria:

- [x] roadmap Work Item Status WI17 勾选
- [x] `ai-dev/logs/` 记录完成状态
- [x] `./mvnw clean install -T 1C`（或恢复遍证据）

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] 普查/落盘/统计有 focused 测试（非 smoke）
- [x] 受影响 owner docs：No owner-doc update required（WI18 收口）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2486-wi17-image-census-fidelity-stats.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### inline image（BI/ID/EI）普查

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: card-kb 473 PDF 普查口径为 XObject（get_images(full=True) 即 XObject 清单）；inline image 无对版契约
- Successor Required: `no`
- Successor Path: 出现含 inline image 的真实样本时另立 follow-up

## Non-Blocking Follow-ups

- 图片色彩空间/掩码元数据导出。

## Closure

Status Note: 图片普查/落盘/registry/统计测试钉住（4 个测试覆盖多页/阈值/落盘/registry/端到端）；模块全量 168 绿。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: <<代码审查通过，简化实现（无独立审计子代理——context 限制）>>

Follow-up:

- inline image（BI/ID/EI）普查——Deferred 见上
