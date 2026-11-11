# 2523 docx roadmap WI8：图表/二维码/条码占位替换

> Plan Status: completed
> Last Reviewed: 2026-11-11
> Source: ai-dev/backlog/nop-docx-document-model-roadmap.md（WI8）+ echarts-server-render-roadmap.md（WI0–WI10 全部完成，EChartsRenderer/IChartImageProvider 就绪）+ docs-for-ai/02-core-guides/word-document-model.md
> Related: ai-dev/plans/2522（展开器）

## Purpose

图片占位 + 绑定定义（image.model 内嵌）+ 运行时替换：占位图片在模型展开期替换为图表（echarts 静态渲染）或二维码/条码图（zxing），替换产物走既有 docx 生成（WI5）与 PDF 渲染（WI6）出口。

## Current Baseline

- echarts 静态渲染就绪：`io.nop.chart.echarts.render.EChartsRenderer.renderPng(optionJson, width, height)` → byte[]（nop-format/nop-chart-echarts，GraalJS 引擎较重，opt-in 依赖）。
- 二维码：`io.nop.integration.api.qrcode.IQrcodeService.createQrcodeBytes(QrcodeOptions)`（nop-integration-api 接口 + nop-integration-zxing 实现 + BeanContainer 获取；nop-report-ext 的 `ReportExtFunctions.QRCODE` 即此路径先例）。QrcodeOptions 含 content/width/height/margin。
- 一维条码：仓库无独立服务；nop-dependencies BOM 管理 zxing **javase** 3.5.3（core 为其传递依赖）（plan 2444 WI10）。
- 模型：OfficeImageModel{assetRef,width,height} + OfficeImageTemplateModel{testExpr,dataExpr,beforeRender,afterRender}——**无图表/码绑定字段**（设计文档 §4 的 WordChartBinding/WordCodeBinding 未实现）。
- 图片链路：doc.assets（ByteString）→ docx 生成（保留解析路径）与 PDF 渲染（PdfImageHelper byte[]）均已支持；展开器（WI7）已具块级循环/物化机制。
- 既有先例：`XptWordTableRenderer` 表后插图（ofcPkg scope + ExcelImage）；`ReportExtFunctions.QRCODE` 读 cell model ext props 配置。

## Goals

- image.model 内嵌绑定定义（xdef 扩展）：`<chartBinding optionExpr width height/>` 与 `<codeBinding codeExpr codeType width height/>`；precompile 重生成。
- `DocImageBinder`（nop-report-docx）：遍历图片块，按绑定定义渲染（chart → EChartsRenderer；code → IQrcodeService qr / zxing Code128 一维条码）→ 产物注入 doc.assets → assetRef 指向新 asset；与 Expander 串接（展开后绑定，或 expander 内置后处理——裁定见 Phase 1）。
- opt-in 依赖边界：EChartsRenderer 经 optional 运行时检测（类存在才走 echarts；绑定存在但依赖缺失 → 具名报错，非静默）；zxing/qrcode 服务经 BeanContainer/直接依赖。
- 测试：chart（真实 echarts 渲染 bar option → PNG 非空 + 出口链）、qr（产物可解码回读原文）、code128（像素/尺寸断言）、缺依赖/缺绑定字段 fail-fast、两条出口链。

## Non-Goals

- 图表渲染引擎本身（echarts roadmap 已收口）；IExcelSheet 级 provider 路径（nop-chart-report 已覆盖 Excel 报表场景，Word 文档走直接渲染器）。
- 图片在表格单元格内的占位替换（首版仅 body/header/footer 顶层图片块；表格内图片模型未承载——登记）。
- 旧链路（XptWordTableRenderer 表后插图）改动。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`：doc.xdef image.model 绑定子节点；`nop-office-doc-model` 重生成。
- `nop-report/nop-report-docx`：DocImageBinder + pom optional/test 依赖裁定 + 测试；与 WI7 Expander 的串接点。
- owner doc / 设计文档决策 / roadmap / 日志。

### Out Of Scope

- 见 Non-Goals；另：nop-ooxml-docx、doc-model 手写层逻辑、既有渲染行为零改动（_gen 重生成除外）。

## Execution Plan

### Phase 1 - 绑定层决策收敛

Status: completed
Targets: `ai-dev/design/word-editor/online-word-model-design.md`

- Item Types: `Decision`

- [x] 决策记录（设计文档「图片绑定层决策」节，§20）：
  - B1 绑定定义内嵌 image.model：`<chartBinding optionExpr="xpl" width="!double" height="!double"/>` 与 `<codeBinding codeExpr="xpl" codeType="!string" width="!double" height="!double"/>`（codeType: qr|code128）；两者互斥共存语义（同时定义报错）。拒绝：独立 WordChartBinding/WordCodeBinding 顶层对象（占位与绑定分离会丢「哪张图替换哪个占位」的就近性，且与内嵌原则冲突）。
  - B2 替换时机与落位：DocImageBinder 作为 Expander 的后处理阶段（`expand` 尾部自动执行绑定；也提供独立 `bind(OfficeDocModel, IEvalScope)`）；绑定产物注入 doc.assets（名 `__gen-img-<n>`，`__` 前缀沿用保留约定语义——生成物命名空间）并改写 assetRef。**assets 副本策略：expand 结果当前与输入 doc 共享 assets List（WI7 实测），binder 注入前必须先物化 assets 副本（新建 KeyedList 拷贝既有 asset 引用），保证 E4 不变量（输入模板不被污染、重复 expand 幂等）——该修复属 WI7 遗留缺口，随本计划一并落地并在 WI7 计划补登记**。
  - B3 opt-in 依赖：nop-chart-echarts 为 **optional 运行时检测**（Class.forName 探测 EChartsRenderer；实例化仅 `new EChartsRenderer()`——其缺省 engine 即 EchartsEngine 单例，**EchartsEngine 构造器 private，不可反射 new**；类加载即构建 Graal Engine，成本已知；绑定存在且类缺失 → 具名报错指明缺依赖）——避免 Word→PDF 使用方被强制拖入 GraalJS 全链；nop-integration-api（IQrcodeService）与 zxing 为 compile 依赖（轻量）。拒绝：compile 依赖 nop-chart-echarts（重依赖强制传递）；provider 不在 classpath 静默跳过（违反 no-silent-no-op——绑定显式声明了意图）。
  - B4 一维条码：仓库无条码服务，绑定器内置 zxing `Code128Writer`（core 3.5.3，经 javase 传递）生成位阵 → ImageIO PNG（宽高按绑定定义，模块内私有实现，不上提为公共服务——需求扩散再上提）。依赖声明：nop-report-docx 显式声明 zxing **javase**（BOM 管理版本，core 传递可得）。
  - B5 width/height 缺省：绑定未给尺寸时 chart 800×400、qr 取 QrcodeOptions 缺省方形、code128 按内容自适应——具名口径。
- [x] check-doc-links --strict 退出码 0。

Exit Criteria:

- [x] 设计文档含 B1–B5 决策及拒绝理由。
- [x] check-doc-links --strict 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 绑定定义与替换器实现

Status: completed
Targets: `nop-kernel/nop-xdefs/.../office/doc.xdef`, `nop-format/nop-office-doc-model`, `nop-report/nop-report-docx`

- Item Types: `Feature`

- [x] doc.xdef：image.model 绑定子节点（B1）；install xdefs + generate-sources 重生成。
- [x] DocImageBinder：遍历 pages 块序列 image 块（chartBinding/codeBinding 互斥校验、表达式求值、渲染、assets 注入、assetRef 改写）；与 Expander 串接（expand 尾部自动绑定 + 独立 bind 入口）；opt-in 检测（B3）；缺依赖/缺表达式/双绑定/渲染失败 fail-fast。
- [x] zxing Code128 位阵→PNG 私有实现（B4）。
- [x] 错误处理两档惯例；不静默跳过任何已声明的绑定。

Exit Criteria:

- [x] `./mvnw test -pl nop-report/nop-report-docx -am` 全绿（既有用例零回归——Expander 既有 15 用例不受后处理影响）。
- [x] `_gen` 生成物由 precompile 产出，无手改。
- [x] 无静默跳过：缺依赖/缺表达式/双绑定各有一条 fail-fast 测试。
- [x] 实现期裁定落设计文档节 + 实现报告。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 绑定验收测试

Status: completed
Targets: `nop-report/nop-report-docx/src/test/java/io/nop/report/docx/`

- Item Types: `Proof`

- [x] `TestDocImageBinding`：
  - chart：真实 EChartsRenderer（test 依赖 nop-chart-echarts）渲染 bar option → 替换后 asset 数据为合法 PNG（魔数/尺寸断言）→ docx 出口链（生成→再解析→assets 数据一致）→ PDF 出口链（文本无关，像素探针：图表区域非白像素）；
  - qr：codeExpr 求值 → 替换产物经 zxing 解码回读原文一致（QR 服务测试装配：`BeanContainer.registerInstance` detached container 注册 ZxingQrcodeService——先例 TestDocxChartRender:48）；
  - code128：产物 PNG 尺寸与条形结构断言（黑白列分布）；
  - 宽高缺省口径（B5）断言；
  - fail-fast：echarts 类缺失模拟（可构造绑定后以探测开关注入——实现期裁定可测方式）、双绑定、缺表达式。
- [x] Expander 集成：expand + 绑定一条龙（xpt 表格 + 图片绑定同模型）。
- [x] 既有用例零回归。

Exit Criteria:

- [x] chart/qr/code128 三类绑定各有产物真实性断言（qr 解码回读为强断言）。
- [x] **端到端验证**：绑定 → docx/PDF 两出口链路通过。
- [x] `./mvnw test -pl nop-report/nop-report-docx -am` 全绿。
- [x] owner doc 图片绑定契约更新；`ai-dev/logs/` 已更新。

## Closure Gates

- [x] roadmap `Work Item Status` WI8 勾选 + M4 milestone（Word 模板动态展开可用，WI7–WI8）+ Last updated 同步。
- [x] 所有 Phase Exit Criteria 已勾选。
- [x] `./mvnw test -pl nop-report/nop-report-docx -am` 绿；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` 绿。
- [x] 生成物纪律：git diff 无手改 `_gen`/`_` 前缀文件。
- [x] check-doc-links --strict 退出码 0。
- [x] scan-hollow-implementations --module nop-report/nop-report-docx --severity high 退出码 0。
- [x] check-plan-checklist --strict 退出码 0。
- [x] 独立子 agent closure audit 完成且 evidence 写入 Closure 段。
- [x] 完成后 git commit（只暂存本计划相关文件）。

## Deferred But Adjudicated

### 表格单元格内图片占位绑定

- Classification: `watch-only residual`
- Why Not Blocking Closure: doc-model 表格单元格不承载图片对象（文本单元格），占位绑定仅覆盖顶层图片块；单元格内图文混排属模型扩展。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: WI8 三 Phase 完成：doc.xdef image.model 增 chartBinding/codeBinding 互斥子节点（表达式为子元素——live xdef 约定；宽高缺省 800×400/0）；DocImageBinder（echarts 类探测 opt-in 仅 new EChartsRenderer、null-safe tryGetBeanByType QR、内置 Code128Writer 位阵→PNG、assets 物化副本修 WI7 共享引用缺口、Expander 尾部自动绑定+可注入覆盖）；14 新用例（echarts 中文 bar 真实渲染、QR 解码回读、code128 列结构、B5 缺省、fail-fast 矩阵、E4 回归、集成一条龙）。nop-report-docx 34、docx 98 全绿，既有零回归。
Completed: 2026-11-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_38d67725-df20-4cc0-ba65-b7c070acf66e）
- Audit Session: agent_38d67725-df20-4cc0-ba65-b7c070acf66e
- Evidence:
  - Exit Criteria 逐项：Phase 1 PASS（设计文档 §20 四条与实现一致）；Phase 2 PASS（doc.xdef 两绑定子节点 + 4 个 _gen + 2 手写子类零手改；DocImageBinder 互斥校验/echarts 探测仅 new 缺省构造/QR null-safe tryGetBeanByType/code128 位阵→PNG/assets 物化副本修 WI7 共享引用/`__gen-img-<n>`/无绑定 no-op/全 fail-fast 矩阵；Expander 尾部自动绑定 + 注入覆盖，既有 15 用例 no-op 不受影响）；Phase 3 PASS（TestDocImageBinding 14 用例：echarts 中文 bar 真实渲染 PNG 魔数尺寸、QR zxing 解码回读强断言、code128 明暗列、B5 缺省、fail-fast 矩阵含注入探测类名模拟、docx 往返字节一致 + PDF 非白像素探针出口链、xpt+绑定集成）。
  - Closure Gates：`./mvnw test -pl nop-report/nop-report-docx -am` 独立实跑 exit 0（34 用例 0 skipped）；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -am` exit 0（101）；nop-report-pdf 143 重跑全绿（首次出现 1 例偶发失败，重跑稳定通过，已复验）；check-doc-links --strict exit 0；scan-hollow 0 发现；依赖纪律（compile 仅增 nop-integration-api + zxing javase，无 echarts compile）。
  - Anti-Hollow：binder 被真实渲染/解码回读测试驱动；无空方法体/no-op。
  - Audit 建议落实（APPROVED 无必修）：materializedDocs 改 WeakHashMap（长生命周期单例 binder 不 pin 渲染文档），改后 34 用例复验全绿。
  - Deferred 项分类检查：表格内图片占位 watch-only，非 in-scope 缺陷降级。
