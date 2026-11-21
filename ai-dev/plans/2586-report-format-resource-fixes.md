# 2586 报表缓存与格式模块资源/一致性修复（report/pdf/chart/debug-web）

> Plan Status: completed
> Last Reviewed: 2026-11-21
> Adversarial Review: 2026-11-21 独立子代理 1 轮通过（1 Blocker + 3 Major 先修已吸收：PDFMergerUtility 深拷、download 联动、单 JVM 边界、Baseline 修正）
> Source: `ai-dev/analysis/2026-11/2026-11-21-monthly-new-code-deep-audit.md`（格式/报表分区 Major/Minor 群）
> Related: `docs-for-ai/`（nop-report / nop-format owner docs）

## Purpose

修复报表结果缓存落盘与并发缺陷、PdfDoc 页导出跨文档共享、echarts 全局超时污染及资源残留面。

## Current Baseline

- `NopReportResultCache.java:77-96`：缓存开关关闭仍向缓存目录写文件且记录不入库 → 永久孤儿；`:157-179` 同步生成无并发去重，并发同 key 下载各自渲染并对同一路径并发写 → 可能损坏文件 + 重复记录。
- `PdfDoc.java:306-341`：selectPages/selectPageRange/selectPage 直接把源文档 PDPage 加进新 PDDocument，无深拷贝；源关闭后派生文档不可用、后续盖章污染已导出页；selectPages 中间异常路径 `new PDDocument()` 不关（exportPages 外层已有 range fail-loud + try/finally，无需重复修）。
- `EchartsEngine.java:53-66,237-244` + `ChartExporter.java:136-140`：请求级超时写全局单例静态字段，并发导出互相覆盖；ThreadLocal<Context> 无 shutdown 关闭钩子。
- Minor：EchartsBridge resizeCanvas 画布累积、jsonAsJsExpression 控制字符转义不全、NopReportFormBizModel 参数匹配 NPE + auth checker 缺失静默跳过授权、NopReportDataSecurityProvider 规则缺字段 NPE、ImageCensus 空吞 IOException + 大图整流 MD5、PageTextExtractor IO 故障误标扫描页、PdfImageRenderer.renderAll 全页光栅 OOM、ReplSourceValidator 全文正则误拒、DebugWebSocketHandler null SecurityContext、ChartExporter 无画布乘积上限。

## Goals

- 缓存目录不再产生孤儿文件；同 key 并发生成去重且原子落盘。
- exportPages 语义与 merge 的深拷贝契约一致。
- 超时配置请求隔离；Context 生命周期可回收。

## Non-Goals

- 不改报表缓存对外 API/表结构（仅实现修正）；不动 xlang-debug 协议设计；不做 OfficeDocModel 大改深审（follow-up）。

## Scope

### In Scope

- nop-report/nop-report-service（cache/form/security）、nop-format/nop-pdf（PdfDoc/ImageCensus/PageTextExtractor/PdfImageRenderer）、nop-chart-echarts + nop-chart-export、nop-xlang-debug-web（两处）

### Out Of Scope

- OfficeDocModelParser/OfficeDocXmlBuilder 深审、PdfSheetRenderer/DocLayoutEngine（抽样未深，另立 follow-up）。

## Execution Plan

### Phase 1 - 报表缓存与 PdfDoc（3 Major）

Status: done
Targets: `NopReportResultCache.java`、`PdfDoc.java`

- Item Types: `Fix`

- [x] 缓存开关关闭走临时目录，不落缓存目录；必须联动 `NopReportCacheBizModel.download`（:50-53 按 cache dir 拼路径取文件）按记录实际路径来源分支取文件（内部实现变更，对外 API 签名不变）——`doGenerate` 走 `targetDir()`（开关关闭=系统临时目录/nop-report-result），记录携带绝对路径，`resolveRecordFile` 供 download/invalidate 分支
- [x] 同 (rptId,params,fileType) 并发生成去重（进程内锁 + 临时文件原子改名落盘）；边界明示：单 JVM 语义，多实例去重需 DB 约束（Non-Goal 不改表结构）→ 移入 Non-Blocking Follow-ups——`keyLocks` computeIfAbsent + synchronized 内二次查重；`renameFile` ATOMIC_MOVE 回退 REPLACE_EXISTING
- [x] selectPages/selectPageRange/selectPage 改深拷贝语义：用 `PDFMergerUtility.appendDocument`（项目已在 merge():392-412 验证的 COS 深拷路径，同 PdfDoc.java:624-629 appendDocument 用法）逐页/逐段深拷；不用 importPage（PDFBox 3.x 仅浅拷页字典，不满足钉子）；异常路径关闭新建文档——统一收敛到 `deepCopySelected(IntPredicate)` 整体拷贝后移除未选中页

Exit Criteria:

- [x] 钉：开关关闭后缓存目录无新文件；并发同 key 下载只产生一份完整文件 + 一条记录（`testDisabledCacheWritesTempDirNotCacheDir`、`testConcurrentGenerateDedup`：6 线程同 key 仅渲染 1 次/1 条记录）
- [x] 钉：selectPages/exportPages 后关闭源文档，派生文档仍可保存/渲染；变异探针红绿（`TestPdfDocSelectDeepCopy` 3 例；变异探针由独立 closure audit 承担，见 Closure Gates）
- [x] `ib test -pl :nop-report-service,:nop-pdf` 全绿（nop-report-service 33 / nop-pdf 171，0 failures）
- [x] `ai-dev/logs/` 已更新

### Phase 2 - echarts 与 Minor 群修

Status: done
Targets: `EchartsEngine.java`、`ChartExporter.java`、`EchartsBridge.java`、`NopReportFormBizModel.java`、`NopReportDataSecurityProvider.java`、`ImageCensus.java`、`PageTextExtractor.java`、`PdfImageRenderer.java`、`ReplSourceValidator.java`、`DebugWebSocketHandler.java`

- Item Types: `Fix`

- [x] 超时改 render 调用参数，移除全局可变面（签名链：ChartExporter → EChartsRenderer.renderPng → EchartsEngine.renderCanvas/renderSvg → scheduleTimeout，`EChartsRenderer.java` 一并纳入改动；seam 私有不构成阻断）——`configureTimeout` 删除，ChartExporter.render 传 `options.getTimeoutSeconds()*1000`（≤0 引擎默认）
- [x] Context 注册表 + shutdown()，挂载点=JVM shutdown hook（EchartsEngine 是 static 单例非 IoC bean；bean 化待 WI7-8 另议）；resizeCanvas 旧画布释放——`liveContexts` 注册表 + static init 挂 hook；shutdown 后调度线程自愈重建（单例不被永久失效）
- [x] jsonAsJsExpression `ch < 0x20` 统一 \uXXXX 兜底
- [x] FormBizModel null-safe 匹配 + checker 缺失 fail-closed；SecurityProvider 规则加载期校验必填字段——`Objects.equals` 匹配；checker 缺失抛 `nop.err.report.auth-checker-missing`；规则缺 rowCondition/field/type 抛 `nop.err.report.data-sec.rule-field-required`（带 ruleIndex）
- [x] ImageCensus catch 分支 LOG.warn + 流式 digest；PageTextExtractor IO 故障与 scanned 判定解耦；PdfImageRenderer 提供按页回调形态或页数上限——`renderAll(doc,dpi,maxPages)` + `renderEach(doc,dpi,PageImageConsumer)`；IO 故障抛 `nop.err.pdf.page-extract-io`（fail-loud，不误标扫描）
- [x] ReplSourceValidator 改 token/标识符层判定；DebugWebSocketHandler 传带用户的 SecurityContext；ChartExporter 画布乘积上限守卫——token 层 Async/Await/Identifier 判定（字符串/注释不再误拒）；`runtime.authenticateUser` 保留 IUserContext，session open 传 `ReplAccessGuard.Context`；`MAX_CANVAS_PIXELS=16M` 守卫

Exit Criteria:

- [x] 每项 focused 钉（超时隔离 `requestLevelTimeoutIsIsolatedFromGlobal`、`requestLevelTimeoutInterruptsInfiniteLoopOnly`；shutdown 后无 Context 残留 + 自愈 `shutdownClosesAllContextsAndEngineRecovers`；规则缺字段带索引 `TestDataSecRuleValidation`；控制字符渲染成功 `jsonAsJsExpressionEscapesControlChars`；画布乘积上限 `TestChartExporterGuards`）
- [x] `ib test -pl :nop-chart-echarts,:nop-chart-export,:nop-report-service,:nop-pdf,:nop-xlang-debug-web` 全绿（40+34+33+171+62=340 tests, 0 failures, 0 errors, 2 skipped 均为既有 @Disabled/环境跳过）
- [x] owner docs 同步或显式 No owner-doc update required（`docs-for-ai/03-modules/nop-report.md` 结果缓存落盘语义、`docs-for-ai/03-modules/nop-pdf.md` 页选取深拷行、`docs-for-ai/02-core-guides/chart-static-render.md` 超时参数化/上限；debug-web/report-service form+security 细节属实现层，No owner-doc update required）
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] 3 个 Major 修复并有回归钉
- [x] Minor 群全部落地或移入 Deferred But Adjudicated
- [x] focused tests 全绿（5 模块 340 tests / 0 failures / 0 errors，见 Phase 1/2 Exit Criteria）
- [x] 独立子 agent closure audit 完成，evidence 写入本文件（主控负责）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（主控负责）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report-service --severity high` 退出码 0（主控负责）

## Deferred But Adjudicated

### OfficeDocModelParser/DocLayoutEngine 深审

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本轮抽样未发现异常信号，审计覆盖面问题而非 live defect
- Successor Required: `yes`
- Successor Path: `ai-dev/analysis/`（下轮审计分区）

## Non-Blocking Follow-ups

- 报表结果缓存多实例去重（DB 唯一约束，涉及表结构变更，超出本轮 Non-Goal）
- ExcelFormatHelper/RandomAccessFileBinaryDataReader 变更复核

## Closure

Status Note: Status Note: 两 Phase 全部落地；独立审计 PASS（5 模块 340/0 与 plan 逐字一致）。
Completed: 2026-11-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（closure audit 2584+2586，2026-11-21）
- Evidence: 缓存临时目录/resolveRecordFile 分支/keyLocks+ATOMIC_MOVE、PdfDoc deepCopySelected 无 importPage、configureTimeout 全仓 grep 零命中、shutdown hook、16M 守卫、DataSec ruleIndex、Repl token 判定、SecurityContext 携带用户——逐条 live 核实
