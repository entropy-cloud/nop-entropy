# 2468 WI6 图片嵌入保真

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI6，deps WI0 已完成）
> Related: `ai-dev/plans/2467-wi4-header-footer.md`、`ai-dev/design/nop-report/pdf-pagination.md`

## Purpose

图片在 PDF 中按模板语义正确缩放/旋转/透明/缓存：rotateDegree 旋转实现、DPI/尺寸换算验证钉住、alpha 透明钉住、缓存复用钉住、缺图占位改进、print=false 与页面裁剪回归。

## Current Baseline

2026-10-19 逐项核对 live repo：

- **尺寸换算**：OOXML 锚点偏移 EMU→pt 在解析期完成（`DrawingParser.java:200-203` → `UnitsHelper.emuToPoints`，914400/inch、12700/pt）；`ExcelImage.calcSize`（ExcelImage.java:77-88）按锚点两角 + dx/dy 计算渲染几何（pt）。**换算正确性当前无任何测试钉住**。
- **旋转**：`ExcelImage.rotateDegree`（workbook.xdef 定义，默认 0）在渲染路径**无任何消费**（PdfImageHelper.drawImage 直接 drawImage 无 Matrix 旋转）——旋转图片导出为不旋转。
- **透明**：PDFBox `PDImageXObject` 原生支持 PNG alpha 通道；当前无测试钉住。
- **缓存**：`PdfRenderer.getImage(ByteString)` 按图片字节缓存 PDImageXObject（per-document，同图复用同一实例）；无测试钉住。
- **缺图/损坏图**：`PdfImageHelper.drawImage`——data null/empty → **静默 return（无占位）**；损坏数据 → getImage 抛异常 → catch 绘制浅灰矩形占位。
- **print=false**：renderImages 跳过 `!image.isPrint()`（套打语义，与 XLSX/HTML 渲染器一致）；无 PDF 侧测试钉住。
- **页面裁剪**：drawImage 以页 clipRect 裁剪越界部分（D4）；无专项测试（WI3 超页行测试间接覆盖表格侧）。
- 测试基座：WI1-WI4 编程模板 + verify 工具（图片 fixture 需程序合成 PNG——`ImageIO.write` 带 alpha 通道）。

## Goals

- `PdfImageHelper.drawImage` 支持 rotateDegree（绕图片中心旋转，Excel 顺时针度数语义）。
- 缺图（data null/empty）与损坏图统一绘制占位（浅灰 + LOG），不再静默跳过。
- 换算/透明/缓存/print=false/裁剪全部测试钉住；旋转/透明/DPI golden。

## Non-Goals

- 图片视觉分类/压缩降采样（内存优化仅要求不回归，不做重写）。
- SVG 图片（roadmap 裁定留 WI0 口径，未纳入）。
- 锚点类型扩展（oneCellAnchor/twoCellAnchor 解析已存在，不动）。

## Scope

### In Scope

- `PdfImageHelper`：rotateDegree 旋转、缺图占位统一 + LOG。
- `PdfRenderer.getImage` 缓存复用断言（行为钉住，不改实现）。
- 测试：TestPdfImageRender（旋转/透明/DPI 换算/占位/print=false/裁剪/缓存/golden）。
- `pdf-pagination.md` D8 图片语义裁定；roadmap/日志收口。

### Out Of Scope

- WI18 能力矩阵；HTML/XLSX 渲染器图片语义。

## Execution Plan

### Phase 1 - 旋转与占位

Status: completed
Targets: `nop-report/nop-report-pdf/.../utils/PdfImageHelper.java`

- Item Types: `Feature | Fix`

- [x] rotateDegree：drawImage 以图片中心为轴旋转。**矩阵组合（审查钉死，勿自行推导）**：`drawImage(image, Matrix)` 的 matrix 是图片单位方格 [0,1]² 的完整映射（不再自带 w/h 缩放）——正确组合为 `Matrix m=new Matrix(); m.scale(w,h); m.translate(-0.5f,-0.5f); m.rotate(theta); m.concatenate(Matrix.getTranslateInstance(cx,cy)); drawImage(pdImage,m)`（cx=x+w/2, cy=y+h/2，x/y/w/h 为调用方传入的 PDF 空间矩形，y 已由调用方 `-imageY-h` 翻转、模块内无全局镜像 CTM）。**方向**：PDF y-up 空间正 θ 逆时针，OOXML rot 顺时针为正 → `theta = Math.toRadians(-rotateDegree)`。rotateDegree=0 走原 `drawImage(x,y,w,h)` 路径零开销。**Fix**：data null/empty 由静默 return 改为绘制占位（与损坏图一致的浅灰块）+ WARN 日志（英文，含位置），消除静默跳过。
- [x] 单测占位与旋转逻辑（合成 PNG fixture：`ImageIO.write` TYPE_INT_ARGB；旋转断言：90/270 后非白像素包围盒 w/h 互换且中心不变 + 不对称图（左红右蓝）四象限采样验证方向符号；缺图占位灰块不随 rotateDegree 旋转）。

Exit Criteria:

- [x] rotateDegree=90/180/270 渲染测试通过（旋转后占位几何/像素方向断言）
- [x] 缺图与损坏图均绘制占位且有 WARN
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] `No owner-doc update required`（Phase 3 统一裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 保真钉住与 golden

Status: completed
Targets: `nop-report/nop-report-pdf` 测试

- Item Types: `Proof`

- [x] TestPdfImageRender：(a) DPI/尺寸换算——锚点已知 pt 几何的图片渲染后占位区域与锚点几何一致（区域采样）；(b) alpha 透明——带 alpha 的 PNG 叠在色块上，采样混合色正确；(c) 缓存复用——同图两次出现渲染不重复解码（getImage 同实例断言）+ 多图渲染不 OOM（10+ 图轻量回归）；(d) print=false 不出现；(e) 越界图片裁剪（D4 回归）；(f) 缺图/损坏图占位。
- [x] golden：旋转 90、alpha 透明、DPI 几何三用例。

Exit Criteria:

- [x] 测试全绿且覆盖 (a)-(f)
- [x] **视觉优先红线**：golden + 人工抽查记录
- [x] `./mvnw test -pl nop-report/nop-report-pdf -am` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 设计裁定与收口

Status: completed
Targets: `ai-dev/design/nop-report/pdf-pagination.md`、roadmap、日志

- Item Types: `Decision | Proof`

- [x] pdf-pagination.md 新增 D8：图片语义（EMU→pt 解析期换算、rotateDegree 绕心顺时针 θ 取负、noChangeAspect 为 UI 语义渲染以锚点几何为准——**显式覆盖 roadmap"noChangeAspect 等比"措辞**、占位策略、分页/套打跳过保持静默、缓存 per-document、alpha 原生、oneCellAnchor 负宽高限制）。
- [x] roadmap WI6 勾选；check-doc-links/check-plan-checklist/scan-hollow 全过。

Exit Criteria:

- [x] 设计文档与实现一致
- [x] roadmap Work Item Status WI6 勾选
- [x] `ai-dev/logs/2026/10-19.md` 记录完成状态
- [x] 收口前 `./mvnw clean install -T 1C`（或记录 flaky 无关失败恢复遍证据）

## Closure Gates

- [x] Phase 1–3 全部 Exit Criteria 勾选
- [x] 旋转/占位行为有 focused 测试证明
- [x] 受影响 owner docs 已同步（pdf-pagination.md）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2468-wi6-image-fidelity.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report/nop-report-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### oneCellAnchor/absoluteAnchor 负宽高

- Classification: `watch-only residual`
- Why Not Blocking Closure: DrawingParser 对无 `to` 节点的锚点（oneCellAnchor/absoluteAnchor）col2=row2=0 → calcSize 宽高为负（渲染错位）。仓库全部模板/fixture 均为 twoCellAnchor（grep 零命中 oneCellAnchor），无现实消费者；修复需解析 `<ext>` 扩展几何，属独立增强。
- Successor Required: `no`
- Successor Path: 出现 oneCellAnchor 模板需求时在 DrawingParser 解析 ext 并让 calcSize 回退扩展几何；D8 已记录该限制。

## Non-Blocking Follow-ups

- xlsx 写回图片属性；&G 页眉图片（WI5）；大图内存降采样优化（出现需求时立项）。

## Closure

Status Note: WI6 三 Phase 完成：rotateDegree 绕心顺时针旋转（显式合并矩阵，四象限方向探针钉住）、缺图/损坏统一占位（消除静默跳过）、alpha/换算/缓存/print=false/裁剪全测试钉住、golden 三张 + 人工抽查（rotate-90 四象限颜色映射正确）。独立子 agent closure audit 见下方 Evidence。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_0656d79b-8497-4698-9fce-75e4e5d6707b）
- Audit Session: agent_0656d79b-8497-4698-9fce-75e4e5d6707b（2026-10-19）
- Evidence:
  - 旋转矩阵独立重推（行向量合并各项与 PdfImageHelper.java:69-73 逐项一致；θ 取负=顺时针；0 值零开销路径）
  - 四象限方向笔算与 testRotate90Clockwise 断言互证；独立复跑模块 72/72 绿
  - 缺图/损坏占位均非静默（WARN + drawPlaceholder）；alpha/缓存/占位/print=false 测试为真实断言非 smoke
  - D8 七项逐条一致（含 noChangeAspect 覆盖裁定显式记录）；roadmap 勾选；日志完整
  - 工具（审计实跑）：check-doc-links exit 0；scan-hollow nop-report-pdf exit 0；check-plan-checklist exit 0
  - oneCellAnchor deferred 诚实（xlsx fixture 零命中）
- Deferred 项分类检查：watch-only（oneCellAnchor）诚实登记含 successor path；无 in-scope live defect 被降级

Follow-up:

- oneCellAnchor `<ext>` 扩展几何解析（出现真实模板需求时）
- roadmap WI6 行 noChangeAspect 措辞在未来触碰时改指 D8

### 实现期关键偏差记录

- 旋转矩阵首版用 Matrix 实例方法组合（scale/translate/rotate/concatenate），实测不生效（PDFBox 行向量约定下实例方法混合前置/后置，组合序错误）——改为显式构造最终矩阵各项（推导记入代码注释与 D8），四象限采样探针验证方向。
- 测试基座：ExcelClientAnchor 编程 API 为 col1/row1 + colDelta/rowDelta（非 col2/row2）；BaseRow 式 null 占位单元格在图片路径不适用（clip NPE 同 WI3 教训，改用空 ExcelCell）；KeyedList 图片集合要求 name 唯一（多图测试需 setName）。
