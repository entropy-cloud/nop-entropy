# 2500 WI12 条码/二维码码制扩展验证（nop-report-capability-roadmap）

> Plan Status: completed
> Last Reviewed: 2026-10-22
> Source: `ai-dev/backlog/nop-report-capability-roadmap.md` WI12

## Purpose

码制扩展（Code128/EAN-13/PDF417 等）与尺寸/纠错级配置的链路已存在（qr:* ext props → QrcodeOptions → Zxing MultiFormatWriter 支持全部 BarcodeFormat 按名解析）——本计划补验证测试与文档收口，并暴露真实缺口（如有）。

## Current Baseline

- `qr:*` cell ext props（qr:barcodeFormat/qr:margin/qr:imgType/qr:width/qr:height/qr:encoding/qr:errorCorrection）在 workbook xdef 已声明，`ReportExtFunctions.QRCODE` 经 `readExtProps` 映射 QrcodeOptions。
- `ZxingQrcodeService.getFormat` 支持全部 `com.google.zxing.BarcodeFormat` 按名忽略大小写解析（未知名抛 IllegalArgumentException——错误码规范化 watch-only）；errorCorrection 配置为**整数 0/1/2/3**（对应 zxing forBits：M=0/L=1/H=2/Q=3）。
- 报表端测试落点：`nop-report-demo` 已有全链路基座（`TestReportDemoBizModel.testQrcode` 渲染 `11-打印条码和二维码.xpt.xlsx`，IQrcodeService 经 zxing-defaults.beans 注册）；nop-report-ext 无 test 基座。
- 无非 QR 码制测试；docs 无码制清单。

## Goals

- 集成测试（nop-integration-zxing）：CODE_128/EAN_13（13 位数字）/PDF_417 生成成功且字节非空、未知码制报错、errorCorrection 四级可配置。
- 报表端测试（nop-report-ext 或 demo）：qr:barcodeFormat=CODE_128 的 cell 渲染产出条码图片（非空 dataBytes）。
- docs：码制清单 + qr:* 配置说明落 owner doc/design；roadmap WI12 勾选；logs。

## Non-Goals

- GS1 AI 结构校验（GS1-128 即 Code128 + AI 内容，码制本身已支持）；新码制实现（Zxing 全量已内置）。

## Scope

### In Scope

- `nop-integration/nop-integration-zxing`（测试）、`nop-report/nop-report-ext`（测试）、docs、roadmap、logs

### Out Of Scope

- 新码制算法；GS1 校验；渲染视觉 golden。

## Execution Plan

### Phase 1 - 验证测试与文档

Status: done
Targets: nop-integration-zxing、nop-report-ext、docs

- Item Types: `Proof + Decision`

- [x] zxing 测试：CODE_128/EAN_13（12 或 13 位数字）/PDF_417（尺寸 ≥ 最小约束）生成、errorCorrection 整数 0/1/2/3、未知码制 IllegalArgumentException
- [x] 报表端测试（demo 模块）：barcodeFormat=CODE_128 渲染条码图片非空（沿 testQrcode 基座）
- [x] docs：码制清单（Zxing 支持格式）+ qr:* 配置表落 owner doc/技术文档；roadmap WI12 勾选；logs 收口

Exit Criteria:

- [x] `./mvnw test -pl nop-integration/nop-integration-zxing` 与 `-pl nop-report/nop-report-demo` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 码制扩展验证测试全绿
- [x] owner doc 码制清单与 qr:* 配置说明
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-report/nop-report-service -am` 与 `-pl nop-report/nop-report-demo` 绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退 0
- [x] `ai-dev/logs` 收口记录
- [x] `check-plan-checklist.mjs --strict` 收口运行

## Deferred But Adjudicated

### GS1 AI 结构校验

- Classification: `watch-only residual`
- Why Not Blocking Closure: GS1-128 = Code128 + AI 内容语法，码制链路已支持；AI 校验属业务校验层
- Successor Required: no
- Successor Path: —

## Non-Blocking Follow-ups

- DataMatrix/Aztec 码制文档补充（Zxing 亦支持）

## Closure

Status Note:
码制扩展链路验证收口：zxing 单元测试（TestBarcodeFormats 5 用例）+ demo 全链路渲染测试（TestBarcode128Render）全绿；真 bug 修复 PDF_417 整数 hint；owner doc 码制清单 + qr:* 配置（整数纠错级）已落 docs-for-ai/03-modules/nop-report.md；roadmap WI12 与 Milestone「导出与内容能力扩展完成」已勾选；logs 2026-10-22 已记录。residual：GS1 AI 结构校验（watch-only，见 Deferred）；DataMatrix/Aztec 文档补充（Non-Blocking Follow-ups）。

Completed:
- TestBarcodeFormats：CODE_128/EAN_13/PDF_417 生成非空、errorCorrection 0/1/2/3、未知码制抛异常（5/5 绿）
- ZxingQrcodeService PDF_417 修复：ERROR_CORRECTION hint 按码制分支（PDF_417 整数 0-8 级，其余 ErrorCorrectionLevel.forBits）
- TestBarcode128Render：qr:barcodeFormat=CODE_128 模板全链路 html 渲染，图片嵌入非空
- docs owner doc 码制清单与 qr:* 配置说明、roadmap WI12 + Milestone 勾选、logs 收口

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor subagent（新会话，GLM，2026-10-22）
- Evidence:
  1. `./mvnw test -pl nop-integration/nop-integration-zxing`：Tests run 7, Failures 0, Errors 0（含 TestBarcodeFormats 5 用例：testCode128/testEan13/testPdf417/testErrorCorrectionLevels/testUnknownFormatThrows），BUILD SUCCESS。
  2. `./mvnw test -pl nop-report/nop-report-demo -Dtest=TestBarcode128Render`：Tests run 1, Failures 0，BUILD SUCCESS；模板 `11-打印条码和二维码.xpt.xlsx`（xl/comments1.xml）含 `qr:barcodeFormat=CODE_128`，渲染输出 drawing 含条码图片，断言 html 含 data:image。
  3. 文档：docs-for-ai/03-modules/nop-report.md「受限」条目更新为条码/二维码（全部 Zxing 码制 + errorCorrection 整数 0-3、PDF_417 0-8 级）；roadmap WI12 与 Milestone「导出与内容能力扩展完成」勾选（Last updated 注明 WI0–WI12 完成）；ai-dev/logs/2026/10-22.md WI12 条目在顶部。
  4. `node ai-dev/tools/check-doc-links.mjs --strict`：4193 files / 49596 refs / 0 errors，EXIT=0。
  5. 文本一致性：Phase 1 Status=done、Exit Criteria 全勾、Closure Gates 全勾、Plan Status=completed，与 live repo 事实一致；`check-plan-checklist.mjs --strict` 收口通过（EXIT=0）。
  6. Anti-Hollow：PDF_417 为真 bug——反编译 zxing 3.3.1 PDF417Writer 确认 ERROR_CORRECTION hint 走 `Integer.parseInt(hint.toString())`，旧代码传入 ErrorCorrectionLevel（"L"/"M"等）必抛 NumberFormatException；修复后 testPdf417 绿（git diff HEAD 可见旧→新实现差异）。
