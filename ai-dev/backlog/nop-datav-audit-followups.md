# nop-datav Audit Follow-up Backlog (P2)

> Status: backlog（P2-only items，不发起独立 remediation plan）
> Sources: `ai-dev/audits/nop-datav/2026-08-10-1516-multi-audit-nop-datav.md`（multi，P2×10）、`ai-dev/audits/nop-datav/2026-08-10-1516-open-audit-nop-datav.md`（open，P2×7 + nits）
> Rules: 每条标注 source audit 路径与 finding ID 以保持可追溯；P2 非降解项，但优先级低于 P0/P1（已进 plan）。
> Resolved: #1 ✅ #2 ✅ #3 ✅ #5 ✅ #6 ✅ #9 ✅ #10 ✅ #13 ✅ #14 ✅ #15 ✅ #16 ✅（plan `ai-dev/plans/nop-datav/2026-08-14-0937-3-chatbi-error-surface-cleanup.md` 已 completed 2026-08-14）

## From `2026-08-10-1516-multi-audit-nop-datav.md`

| # | Finding | Source | Note |
|---|---------|--------|------|
| 1 | ✅ Dim14-03 — `AlertEvaluator` 持久化 TRIGGERED+lastNotifiedTime 在 `sendAlertNotification` 之前；SMTP 失败时告警被记为「已通知无错」且无通知发出（drift vs design §269-273） | `ai-dev/audits/nop-datav/2026-08-10-1516-multi-audit-nop-datav.md` §Dim14-03 | **已修复** plan `2026-08-14-0937-2` Phase 1：通知成功后再设 lastNotifiedTime；失败留 lastNotifiedTime=null 立即重试 |
| 2 | ✅ Dim14-04 — 调度器 `catch (Throwable)` 含 OOM/Error，违背 design §3/§9「基础设施错误应传播」 | `…multi-audit…` §Dim14-04 | **已修复** plan `2026-08-14-0937-2` Phase 2：`catch (Exception)` 让 `Error` 传播 |
| 3 | ✅ Dim09-02 — `AlertThresholdComparator`/`AlertAggregator` 配置错误用裸 `IllegalArgumentException` 到达公共 `evaluateAlertNow` | `…multi-audit…` §Dim09-02 | **已修复** plan `2026-08-14-0937-2` Phase 3：换 `NopException(ERR_DATAV_ALERT_VALUE_REQUIRED/UNSUPPORTED_OPERATOR/UNSUPPORTED_AGGREGATION)` |
| 4 | Dim09-03 — `createExportTask` 空 sourceType/sourceId 用 `ERR_DATAV_EXPORT_TASK_NOT_FOUND` + ARG mismatch | `…multi-audit…` §Dim09-03 | 定义 `ERR_DATAV_EXPORT_MISSING_SOURCE`；并把空源检查前移到 `validateFormat` 之前 |
| 5 | ✅ Dim09-04 — `ERR_DATAV_EXPORT_FAILED` / `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` 定义但无引用 | `…multi-audit…` §Dim09-04 | **已修复** 导出侧由 Plan {2}（Dim07-01）退役 `ERR_DATAV_EXPORT_FAILED`；chatbi 侧由 plan `2026-08-14-0937-3` Phase 1 接线 `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED`（`.join()` try/catch 抛出） |
| 6 | ✅ Dim09-05 — 调度器缺 job 参数抛裸 `IllegalArgumentException`（被外层 catch 吞） | `…multi-audit…` §Dim09-05 | **已修复** plan `2026-08-14-0937-2` Phase 2：换 `NopException(ERR_DATAV_ALERT_RULE_NOT_FOUND).param(ARG_ALERT_RULE_ID, "(absent from job params)")` |
| 7 | Dim04-01 — `NopDatavScreenSnapshot.snapshotContent` 缺 `mandatory="true"`（screen-design §1.3） | `…multi-audit…` §Dim04-01 | ORM 加 `mandatory="true"` |
| 8 | Dim04-02 — `NopDatavScreenSnapshot` UK 名 `SCR_VER` 与设计 `SCREEN_VER` 不符（screen-design §84） | `…multi-audit…` §Dim04-02 | 重命名 UK 匹配设计契约 |
| 9 | ✅ Dim16-02 — 告警「panel 存在但 queryPanelData 抛错」分支无独立测试 | `…multi-audit…` §Dim16-02 | **已修复** plan `2026-08-14-0937-2` Phase 4：testPanelQueryFailureRecordsErrorAndPreservesState |
| 10 | ✅ Dim16-03 — 报告交付「导出中途失败」路径无测试 | `…multi-audit…` §Dim16-03 | **已修复** plan `2026-08-14-0937-2` Phase 4：testReportDeliveryExportFailureRecordsError（异步轮询） |

## From `2026-08-10-1516-open-audit-nop-datav.md`

| # | Finding | Source | Note |
|---|---------|--------|------|
| 11 | AR-1 — `nop-datav-core` 孤儿模块（261 行生成常量），service 层手写重复状态常量，single-source-of-truth 已侵蚀 | `ai-dev/audits/nop-datav/2026-08-10-1516-open-audit-nop-datav.md` §AR-1 | 二选一：加入 `<modules>` + service 依赖并替换手写枚举；或删除 core 并在 model-first 文档标定 service 枚举为权威源 |
| 12 | AR-2 — `nop-datav-chart` 空壳模块 + 绕过 nop-bom 的硬编码 `poi:5.4.0` | `…open-audit…` §AR-2 | 从 `<modules>` 删除并删目录；或填充预期代码并把 poi 版本移入 nop-bom |
| 13 | ✅ AR-3 — `DatavGenerateScreenExecutor.isUniqueConstraintViolation` 子串过宽（`constraint`/`uk_`），把任意约束错误误报为重名 | `…open-audit…` §AR-3 | **已修复** plan `2026-08-14-0937-3` Phase 3：收窄为仅 `unique`/`duplicate`，移除 `constraint`/`uk_`（三方言 UK 仍正确命中） |
| 14 | ✅ AR-4 — `DatavQueryDatasetExecutor` 复用 `ERR_DATAV_QUERY_FAILED` 但 param `panelId` 误填 datasetSid（ChatBI 无 panel） | `…open-audit…` §AR-4 | **已修复** plan `2026-08-14-0937-3` Phase 2：新增 `ERR_DATAV_CHATBI_DATASET_QUERY_FAILED`（ARG_DATASET_SID + ARG_REASON），保留 `.cause(e)` |
| 15 | ✅ AR-5 — 两 ChatBI generate-executor 内联 `SYSTEM_OPERATOR="system"` 并附「避免循环依赖」假理由，真常量在同模块 `NopDatavOperatorResolver` | `…open-audit…` §AR-5 | **已修复** plan `2026-08-14-0937-3` Phase 2：两 executor 引用 `NopDatavOperatorResolver.SYSTEM_OPERATOR`，删 `NopOperatorFallback` 内联类 |
| 16 | ✅ AR-6 — `ChatBiToolCallingLoop.callTool().join()` 可泄漏 `CompletionException`；handler 失败双层静默吞；`ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` 未用 | `…open-audit…` §AR-6 | **已修复** plan `2026-08-14-0937-3` Phase 1：`.join()` try/catch（CompletionException+CancellationException）解包后抛结构化 NopException（含 ARG_TOOL_NAME+ARG_REASON）；两处 handler-swallow 加 `LOG.debug`；新增 Logger 字段 |
| 17 | AR-7 — `DatavListDatasetsExecutor.findAll()` 全列（含 dsText/dsMeta CLOB）加载后内存过滤 keyword | `…open-audit…` §AR-7 | 投影所需列 + 把 keyword 下推 SQL `like` |

## Minor nits (open-audit)

- `DatavGenerateDashboardExecutor.generateId(prefix)`（`:392-397`）`prefix` 参数未使用 — 用或删。来源：`…open-audit…` Minor nits。
- `ChatBiToolCallingLoop.QUERY_HANDLER`（`:203-204`）`parseNonStrict` 同三元里调两次 — 解析一次入局部。来源：同上。
- `nop-datav-api` 空模块（`gen-crud-api.xgen` 全注释）— 作为 `nop-datav-dao` 占位依赖合法；若永久弃用 CRUD-API 生成可删依赖。来源：同上。
