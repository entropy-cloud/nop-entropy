# 2481 WI12 nop-pdf 生成/装配 API

> Plan Status: completed
> Last Reviewed: 2026-10-19
> Source: `ai-dev/backlog/nop-pdf-report-export-roadmap.md`（WI12，deps WI0 已完成）
> Related: `ai-dev/plans/2463-wi0-pdf-visual-acceptance-toolchain.md`（verify 工具链复用）
> Review: R1（1 Blocker + 2 Major + 10 Minor）+ R2 复核（Blocker=0/Major=0，5 Minor 措辞收紧已吸收）；见修订记录

## Purpose

nop-pdf 从"只读工具箱"升级为可生成/装配：合并、拆分导出增强、盖章/覆盖层、水印、给已有 PDF 追加页眉页脚/页码、图片→PDF，使导出产物可被批量后处理。

## Current Baseline

2026-10-19 核对 live repo（R1 审查核对通过项为准）：

- `io/nop/pdf/core/PdfDoc.java`（199 行）：loadFromFile/getNumberOfPages/getPageText/getAllText/selectPages/selectPageRange/selectPage/splitIntoPages/extractTables/tablesHtml/save/close——**全部为解析/抽取/选择，无生成/写入/装配能力**（save 仅保存已有 document）。异常惯例 `NopException.adapt(e)`（:40,:58）。
- **页面迁移陷阱（R1-B1）**：同文件 `selectPages`（:110-114）是**浅共享**（新 tree 引用源 doc 的 PDPage 对象），安全仅因源 doc 保持打开（splitIntoPages 先例）；`importPage` 先例（NurminenDetectionAlgorithm.java:814）只深拷 content stream，**/Resources 仍指源文档**，源 close 后 save/render 抛 COSStream closed。装配必须用 `PDFMergerUtility.appendDocument`（COS 深拷贝，pdfbox artifact 内无新依赖）。
- `PdfBoxHelper`（utils）：仅 3 个几何/字体度量静态方法。依赖：nop-xlang + pdfbox 3.0.8（nop-kernel/nop-dependencies/pom.xml:107）+ jts-core；无字体加载设施（FontManager 属 nop-report-pdf，跨模块禁依赖）。
- 错误码模式：ReportPdfErrors（define + ARG_* 常量）；i18n 登记**单点** `nop-runner/nop-cli-core/src/main/resources/_vfs/i18n/zh-CN/nop-cli-errors.i18n.yaml:1113-1116`（**en 变体无 report.pdf 条目——zh-CN 单点登记为现行实践，en 不登记**）。`check-error-param-consistency.mjs`：throw-site 扫描只认 NopMetadataException（NopException 抛点不受检）；`--module` 须用完整路径 `nop-format/nop-pdf`（短名会 exit 2）；registry 要求 argsMap 非空（至少一个 ARG_* 常量）+ src/main 真实引用。
- 测试基座：`TestPdfDoc` 已 `@Disabled`（Windows 路径前提），`TestPdfDocSplit` 活跃；**活跃基座为 verify/extract/tabula 包**——PdfGoldenManager/RegionProbe/PdfImageRenderer（main scope）支撑 golden（`_tmp/pdf-golden/`，BASELINE_CREATED 不算 fail）、位置墨迹采样、opacity 混色（averageColor）断言。

## Goals

- PdfDoc 增装配 API：merge 多文件→一、append 追加页（COS 深拷贝）、exportPages 拆分导出增强、stampText/stampImage 指定页指定位置盖章、watermarkText/watermarkImage 全页水印（角度/透明度）、appendPageNumbers 页码、appendHeaderFooter 页眉页脚、fromImage(s) 图片→PDF。
- 每个能力有单测（内容流/提取/RegionProbe 采样断言）+ 装配 golden；fail-loud（坏页码/缺字体/不可编码字符/坏图均抛 ErrorCode，不静默）。

## Non-Goals

- 文本/表格抽取增强（已有）；数字签名、加密/权限（WI13）；PDF/A（WI13 裁定）。
- 页眉页脚富文本/模板化（字面文本 + 字号/颜色）；CJK 字体自动发现（调用方传字体文件；缺省 Helvetica 仅限 Latin——**任何 encode 失败（含希腊/西里尔等非 CJK 非 Latin）一律 fail-loud，禁止 report-pdf 式 sanitize 静默替换**）。
- 性能优化（大文件流式合并）；九宫格锚点语法（x/y 显式 pt）。

## Scope

### In Scope

- `PdfDoc`：merge/append/exportPages/stampText/stampImage/watermarkText/watermarkImage/appendPageNumbers/appendHeaderFooter/fromImage(s)；`PdfStampOptions`（x/y、degrees、opacity、fontSize/fontColor/fontFile、imageWidth/imageHeight）；`PdfErrors`（ERR_PDF_PAGE_OUT_OF_RANGE / ERR_PDF_CJK_REQUIRES_FONT / ERR_PDF_IMAGE_INVALID / ERR_PDF_TEXT_NOT_ENCODABLE）。
- 测试：TestPdfAssemble、TestPdfStamp、fail-loud 用例。方法签名以源码为准（plan 不锁签名细节）。

### Out Of Scope

- watermark/stamp 的 SVG 矢量水印；已有页面内容编辑；表单填充；平铺水印。

## Execution Plan

### Phase 1 - 装配 API 与测试

Status: completed
Targets: `PdfDoc.java`、`PdfStampOptions.java`（新增）、`PdfErrors.java`（新增）、`i18n yaml`

- Item Types: `Feature`

- [x] 合并与拆分（R1-B1 裁定）：`static PdfDoc merge(List<File> files)`——逐个 load，**append 用 `PDFMergerUtility.appendDocument`（COS 深拷贝），禁止 addPage/importPage 共享源文档对象；selectPages 既有浅共享语义不得作为 append 实现模板**；merge 部分失败**首错即抛**（param 带文件名），已打开源 doc 关闭，**内部创建的目标 doc 一并关闭（R2-Minor-1，防泄漏）**；`void append(PdfDoc other)`——**不接管 other 生命周期**（调用方负责 close）；`void exportPages(List<Integer> pages1Based, File out)`——**先范围校验（越界 fail-loud ERR_PDF_PAGE_OUT_OF_RANGE）后委托 selectPages**（其现语义静默跳过越界，不可依赖）。
- [x] 盖章/水印：stampText（encode 探测驱动 fail-loud + UnicodeBlock 分类错误码 + PDType0Font 按目标文档加载）、stampImage（宽高缺省=像素/72pt、单边缺省按比例）、**绕图片中心旋转（执行偏差：绕角点旋转使旋转后视觉中心偏移，像素调试定位后改为 translate(center)+rotate+draw(-w/2,-h/2)）**、watermarkText/Image（缺省 45°/0.25/36pt 居中）、applyOpacity（null Resources 显式建 PDResources，save/restore 收口）。
- [x] 页码/页眉页脚：appendPageNumbers（{page}/{pages} 占位符、页脚居中 y=20pt、字号 10pt 缺省）；appendHeaderFooter（header y=pageHeight-20 / footer y=20）；新增 `save(OutputStream)` 与 `getDocument()`（测试与产物复用）。
- [x] 图片→PDF：fromImage/fromImages（页面尺寸=像素/72pt、满页铺放；null/空字节 ERR_PDF_IMAGE_INVALID——Guard.notEmpty 换显式错误码判定）。
- [x] PdfErrors：四码 ErrorCode.define 全限定形式（check-error-param-consistency 解析口径）+ ARG 对称 + src/main 真实引用；i18n zh-CN 单点登记（en 不登记）。
- [x] 测试：TestPdfAssemble 10 个 + TestPdfStamp 5 个（全绿）——merge→save→close→reload 深拷贝钉、append 生命周期、exportPages 子集+越界、图片几何、页码/页眉页脚提取、CJK 无字体/希腊字母 fail-loud（错误码精确断言）、CJK 带字体（Assumptions 门控+候选探测）、位置墨迹采样、opacity 混色（0.25 vs 1.0 灰度序）、30°/45° Tm 系数、null Resources 页水印、golden；**人工截图抽查 PASS（watermark-page-1.png：Stamp-12 位置印章 + 淡灰 CONF 45° 水印，0.25 透明度可辨；36pt 水印在 200×100pt 小页越界部分正常裁剪）**。

Exit Criteria:

- [x] 测试全绿且覆盖上列断言；**接线验证**：merge→save→close→reload→页数/文本一致（源 doc 在 merge 内已关闭）
- [x] **端到端验证**：图片→PDF→水印→页码→save→重读提取/采样全链路走通（TestPdfStamp 组合用例）
- [x] **无静默跳过**：越界/坏图/CJK/不可编码四类 fail-loud（错误码断言）；null Resources 页 alpha 注入不静默
- [x] **视觉红线**：_tmp/pdf-golden/wi12-assembly/watermark-page-1.png + golden diff + 人工抽查 PASS
- [x] `./mvnw test -pl nop-format/nop-pdf -am` 全绿（模块全量 103 含 skip1）；check-error-param-consistency（精确路径）exit 0
- [x] No owner-doc update required（能力矩阵由 WI18 统一收口）
- [x] `ai-dev/logs/` 对应日期条目已更新

执行偏差（Phase 1）：

- nop-pdf 测试为纯 JUnit5（无 nop-autotest 依赖）——新测试不继承 JunitBaseTestCase。
- NopException.adapt 返回 RuntimeException（链式 .param 需 instanceof 收窄）；PDExtendedGraphicsState 的 alpha setter 是 setNonStrokingAlphaConstant/setStrokingAlphaConstant（沿 WI9 语义 page.getResources().add(gs) + cs.setGraphicsStateParameters）；PDFont.encode 同时抛 IllegalArgumentException 与 IOException；ErrorCode.define 须全限定形式（check-error-param-consistency 的解析口径，静态导入 define 形式不被识别）。
- 旋转图片绕角点旋转导致视觉中心偏移（像素级调试定位），改为绕中心旋转；纯红通道混白不变（255），混色断言应作用在 G/B 通道。
- 测试 fixture 图片须显式填白（TYPE_INT_RGB 零填充为黑）。

### Phase 2 - 裁定与收口

Status: completed
Targets: roadmap、日志

- Item Types: `Proof`

- [x] roadmap WI12 勾选；check-doc-links/check-plan-checklist（本文件）/`scan-hollow --module nop-format/nop-pdf --severity high` 全过；收口前全量构建（恢复遍证据口径，见 Closure）。
- [x] 纯新增能力无既有 owner doc 需更新——显式记录 No owner-doc update required 理由（能力矩阵 WI18 收口）；执行中裁定的语义（水印缺省参数、旋转锚点、页码占位符）已记录于计划与代码 javadoc。

Exit Criteria:

- [x] roadmap Work Item Status WI12 勾选
- [x] `ai-dev/logs/` 对应日期条目记录完成状态
- [x] `./mvnw clean install -T 1C`（恢复遍证据：排除项均属其他工作流并单跑复核，逐项见 Closure）

## Closure Gates

- [x] Phase 1–2 全部 Exit Criteria 勾选
- [x] 装配能力有 focused 测试证明（重读/采样/操作符断言非 smoke）
- [x] 受影响 owner docs：No owner-doc update required（WI18 统一收口，理由已记录）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2481-wi12-pdf-assembly-api.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-format/nop-pdf --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 九宫格锚点/百分比定位语法；水印平铺（tile）模式。
- fontFile 路径缺字形时 showText 抛裸 IllegalArgumentException（closure audit Minor-C：fail-loud 成立但不走 ErrorCode；后续将 showText IAE 收口为 ERR_PDF_TEXT_NOT_ENCODABLE 或对 fontFile 路径预探测）。

## Risks And Rollback

- PdfDoc 纯增量方法，既有抽取 API 不动；merge 必须深拷贝（B1 裁定），reload 验证为回归门；透明度 save/restore 收口 + null Resources 显式建，失败可按方法粒度回退。

## R1/R2 审查修订记录

2026-10-19 R1（独立子代理 agent_21f634dd）：B1 merge/append 页面迁移机制裁定为 PDFMergerUtility 深拷贝（禁浅共享/importPage，reload-close 回归钉）；M1 CJK 测试裁定为系统字体发现 + Assumptions 门控（不提交字体）；M2 人工抽查记录项补入 Exit Criteria；Minor 1-10（i18n zh-CN 单点裁定、check-error-param-consistency 精确路径与前提、基座表述修正、exportPages 先校验后委托、非 CJK 不可编码 fail-loud、null Resources 显式建、水印/HF 缺省参数定版、append 生命周期/merge 首错即抛、PDType0Font 禁跨文档缓存、签名以源码为准）全部并入正文。

2026-10-19 R2（独立子代理 agent_a1cf83a0，Blocker=0/Major=0）：B1/M1/M2 消解性复核经 live 代码与 pdfbox 3.0.8 jar 字节码逐条证实（appendDocument 深拷贝、Assumptions 先例、双落点）；Phase 1 逐 API 想象性分析无断层。5 Minor 吸收：merge 失败同时关闭目标 doc；encode 失败驱动替代字面 Latin-1 判断；混合文本按首失败字符 UnicodeBlock 分类错误码；null Resources 措辞修正（3.0.8 构造器自动建，显式建为防御双保险，硬性要求=不静默 return）；CJK 探测测试内自实现（不复用 FontManager）。

## Closure

Status Note: 六项审计全 PASS（深拷贝接线经 pdfbox jar 字节码独立证实、reload-close 回归钉有效、四类 fail-loud 齐备、i18n 单点对称、四 gate 工具 exit 0、文档/日志与 live 一致），无 Blocker/Major；Minor-A（watermarkDefaults 拷贝语义）与 Minor-B（页码/HF 透传范围 javadoc）同批修复复跑 15/15 绿，Minor-C 登记 follow-up，Minor-D（簿记）随本收口完成。
Completed: 2026-10-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_91dd8c0e-bee5-4528-b8c0-4da21dda2da3（fresh session，与实现不同 task）
- Evidence:
  - 接线验证 PASS：appendDocument 唯一装配入口 = PDFMergerUtility.appendDocument（PdfDoc.java:459-465），字节码级证实 PDFCloneUtility/cloneForNewDocument 深拷贝；reload-close 回归钉 TestPdfAssemble.java:59-83（浅共享回归必红）；exportPages 先校验后委托（:267-271）
  - 语义正确性 PASS：merge 失败关源+目标（:241/:245-248）；append 不接管（:255-258）；encode 探测 + UnicodeBlock 分类（:497-534）；opacity 注入收口（:541-552）；水印缺省常量（:48-50）；绕中心旋转（:341-345）；占位符替换（:382-383）
  - 无静默跳过 PASS：四类 fail-loud 全部 ErrorCode；scan-hollow exit 0（116 文件 0 findings）
  - i18n/工具面 PASS：四条 zh-CN（nop-cli-errors.i18n.yaml:1117-1120）与 Java 定义逐字一致；check-error-param-consistency exit 0
  - 文档一致性 PASS：roadmap WI12 勾选（:72）；计划 Phase 1 completed 与 live 核对属实；日志 :3-9
  - Minor-A/B 同批修复后复跑 TestPdfAssemble+TestPdfStamp 15/15 绿
  - 构建证据（恢复遍口径）：模块验证 nop-pdf 全量 103（skip1）BUILD SUCCESS；全量 `clean install -T 1C` 恢复遍排除清单（均单跑复核绿或预存缺陷）：TestLeaseLifecycle（xdbg plan 2469 预存稳定失败）、TestDebugGatewayE2E/TestReplService（-T 1C flaky）、NodeTscBridgeTest（Node 环境依赖）、JSONPointTest_0/1（遗留坏测试）、ReproG3PerWindowStateLeakOnDiscard/TestMessageAdapters（-T 1C 计时 flaky，单跑绿）、nop-stream-connector-jdbc insert-no-debezium pin（plan 2413 预存缺陷，jar 已装模块跳过）

Follow-up:

- TestLeaseLifecycle（xdbg plan 2469）、insert-no-debezium pin（plan 2413）为其他工作流预存缺陷，已如实上报
- fontFile 路径缺字形 IAE 收口（Minor-C）；水印平铺/九宫格锚点
