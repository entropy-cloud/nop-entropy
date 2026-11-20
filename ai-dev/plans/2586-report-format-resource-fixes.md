# 2586 报表缓存与格式模块资源/一致性修复（report/pdf/chart/debug-web）

> Plan Status: draft
> Last Reviewed: 2026-11-21
> Source: `ai-dev/analysis/2026-11/2026-11-21-monthly-new-code-deep-audit.md`（格式/报表分区 Major/Minor 群）
> Related: `docs-for-ai/`（nop-report / nop-format owner docs）

## Purpose

修复报表结果缓存落盘与并发缺陷、PdfDoc 页导出跨文档共享、echarts 全局超时污染及资源残留面。

## Current Baseline

- `NopReportResultCache.java:77-96`：缓存开关关闭仍向缓存目录写文件且记录不入库 → 永久孤儿；`:157-179` 同步生成无并发去重，并发同 key 下载各自渲染并对同一路径并发写 → 可能损坏文件 + 重复记录。
- `PdfDoc.java:306-330,427`：exportPages 直接把源文档 PDPage 加进新 PDDocument，无深拷贝；源关闭后派生文档不可用、后续盖章污染已导出页；异常路径 ret 不关闭。
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

Status: planned
Targets: `NopReportResultCache.java`、`PdfDoc.java`

- Item Types: `Fix`

- [ ] 缓存开关关闭走临时目录/内存流，不落缓存目录
- [ ] 同 (rptId,params,fileType) 并发生成去重（进程内锁或生成中占位 + 原子改名落盘）
- [ ] exportPages 改深拷贝语义（importPage/独立页对象），异常路径 try/finally 关闭 ret

Exit Criteria:

- [ ] 钉：开关关闭后缓存目录无新文件；并发同 key 下载只产生一份完整文件 + 一条记录
- [ ] 钉：exportPages 后关闭源文档，派生文档仍可保存/渲染；变异探针红绿
- [ ] `ib test -pl :nop-report-service,:nop-pdf` 全绿
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - echarts 与 Minor 群修

Status: planned
Targets: `EchartsEngine.java`、`ChartExporter.java`、`EchartsBridge.java`、`NopReportFormBizModel.java`、`NopReportDataSecurityProvider.java`、`ImageCensus.java`、`PageTextExtractor.java`、`PdfImageRenderer.java`、`ReplSourceValidator.java`、`DebugWebSocketHandler.java`

- Item Types: `Fix`

- [ ] 超时改 render 调用参数，移除全局可变面
- [ ] Context 注册表 + shutdown() 关闭钩子；resizeCanvas 旧画布释放
- [ ] jsonAsJsExpression `ch < 0x20` 统一 \uXXXX 兜底
- [ ] FormBizModel null-safe 匹配 + checker 缺失 fail-closed；SecurityProvider 规则加载期校验必填字段
- [ ] ImageCensus catch 分支 LOG.warn + 流式 digest；PageTextExtractor IO 故障与 scanned 判定解耦；PdfImageRenderer 提供按页回调形态或页数上限
- [ ] ReplSourceValidator 改 token/标识符层判定；DebugWebSocketHandler 传带用户的 SecurityContext；ChartExporter 画布乘积上限守卫

Exit Criteria:

- [ ] 每项 focused 钉（超时隔离、shutdown 后无 Context 残留、规则缺字段 400 带索引、控制字符渲染成功等）
- [ ] `ib test -pl :nop-chart-echarts,:nop-chart-export,:nop-report-service,:nop-pdf,:nop-xlang-debug-web` 全绿
- [ ] owner docs 同步或显式 No owner-doc update required
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] 3 个 Major 修复并有回归钉
- [ ] Minor 群全部落地或移入 Deferred But Adjudicated
- [ ] focused tests 全绿
- [ ] 独立子 agent closure audit 完成，evidence 写入本文件
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-report-service --severity high` 退出码 0

## Deferred But Adjudicated

### OfficeDocModelParser/DocLayoutEngine 深审

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本轮抽样未发现异常信号，审计覆盖面问题而非 live defect
- Successor Required: `yes`
- Successor Path: `ai-dev/analysis/`（下轮审计分区）

## Non-Blocking Follow-ups

- ExcelFormatHelper/RandomAccessFileBinaryDataReader 变更复核

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
