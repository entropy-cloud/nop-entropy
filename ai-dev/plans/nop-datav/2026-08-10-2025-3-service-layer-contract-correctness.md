# {3} Service-Layer Public-Contract Correctness

> Plan Status: active
> Last Reviewed: 2026-08-11
> Source: `ai-dev/audits/nop-datav/2026-08-10-1516-multi-audit-nop-datav.md` — Dim09-01 [P1], Dim14-02 [P1]
> Related: `AGENTS.md`（两层错误策略）；design `ai-dev/design/nop-datav/schedule-report-design.md`（§3 runInNewSession 意图）

## Purpose

收口两条独立的 P1 公共契约违背：(1) 面板查询公共 action 泄漏裸 `IllegalArgumentException`，破坏两层错误策略与错误码可观测性；
(2) 定时报告交付在 `runInNewSession` 内执行同步 SMTP，跨远程调用持有 JDBC 连接，并发 cron 下可致连接池耗尽。
两者都是 service 层公共路径的契约/资源正确性，互不依赖，作为同一计划的两条独立 slice。

## Current Baseline

（基于 2026-08-10 live code 核对）

- **Dim09-01（public API 错误策略违背）**：`PanelParamEvaluator.evaluate`（`PanelParamEvaluator.java:45/51/63/69`）对 4 类配置错误
  抛裸 `IllegalArgumentException`（invalid JSON / 非 object / rule 非 object / 缺 source）。调用点 `PanelDataBinder.queryPanelData`
  在 `:131` 调用 `evaluate(...)`，该行**结构上位于 `:136` 的 try 块之外**（`:136-150` 的 catch 只把 `executeQuery` 的异常包成
  `ERR_DATAV_QUERY_FAILED`）。故裸 `IllegalArgumentException` 直穿 `NopDatavPanelBizModel.getPanelData`（`@BizQuery` 公共 GraphQL action）
  到达 GraphQL 边界：前端/`GraphQLAuditLogger` 丢失 `errorCode` 字段，cause 链丢失。这是模块内最高频公共查询路径。
  模块已有 `ERR_DATAV_INVALID_PARAM_CONFIG`（`NopDatavErrors.java:119`，参数 `ARG_REASON`）可直接复用。
- **Dim14-02（资源/可用性）**：`ReportDeliveryExecutor.execute` 在 `GlobalExecutors.globalWorker().submit(...)` 内开
  `ormTemplate.runInNewSession(session -> doExecute(...))`（`:122-125`）；`doExecute` 的整个主体（文件落盘 `:187` →
  `notificationSender.sendReport` 同步 SMTP `:192` → 交付记录 UPDATE `:198`）跑在**同一个 ORM session** 内。Nop ORM session
  生命周期内持有 JDBC 连接，故 SMTP（典型超时 30-60s）期间连接被占。多个报告共享同一 cron 分钟时连接池耗尽，级联到无关的
  datav 请求。另：email 在 SUCCEEDED UPDATE 提交**之前**发出（UPDATE `:198` 与 send `:192` 同事务），UPDATE 失败时邮件已发但
  交付记录仍显 RUNNING。对照 `AlertEvaluator`（未包 `runInNewSession`，每个 DAO 调用各自微 session，`sendAlertNotification` 不在 session 内）
  与导出 `executeTask`（单 session 但无远程调用，仅 JDBC + 文件写），确认报告路径是离群点。

## Goals

- 公共 `getPanelData`（及任何经 `PanelParamEvaluator` 的路径）在 paramMapping 配置错误时返回带 `ErrorCode` 的 `NopException`
  （非裸 `IllegalArgumentException`），保留 cause 链与 `panelId` 上下文。
- `ReportDeliveryExecutor` 的 SMTP 发送不再在持有 JDBC 连接的 ORM session 内执行；交付状态 UPDATE 先提交，邮件后发（或经
  `txn().afterCommit(...)`），消除连接池耗尽与 email-before-commit 顺序隐患。

## Non-Goals

- 不重写错误码体系或全局错误策略（仅修这一条公共路径）。
- 不改动报告取数/落盘/grace/skipped 逻辑。
- 不处理 P2 错误码语义项（Dim09-02 告警 bare IAE、Dim09-03 导出空源错码、Dim09-05 调度器 bare IAE、AR-4 dataset 路径 panelId 误用），见 backlog。
- 不处理调度器 `catch (Throwable)`（Dim14-04，P2，backlog）。

## Scope

### In Scope

- `PanelParamEvaluator.evaluate`（`:45/51/63/69`）：4 处裸 `IllegalArgumentException` → 包成 `NopException(ERR_DATAV_INVALID_PARAM_CONFIG)`
  （或等效，使 cause 链与 reason 保留）；并在 `PanelDataBinder.queryPanelData:131` 确保 `panelId` 进入异常 param。
- `ReportDeliveryExecutor.doExecute`（`:141-214`）：把 `notificationSender.sendReport`（`:192`）移出 `runInNewSession` 的 session 持有块；
  交付记录 SUCCEEDED UPDATE 在 session 内提交，邮件在 session 关闭后（或 `afterCommit`）发送；失败回补 FAILED。
- 各自的 focused test。

### Out Of Scope

- 分享/导出 RBAC（→ Plan {1}）；导出状态机（→ Plan {2}）。
- 重构 `AlertEvaluator`（audit 确认其不在 session 内，无需改）。

## Execution Plan

### Phase 1 - 面板查询错误策略（Dim09-01）

Status: planned
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/query/PanelParamEvaluator.java:45/51/63/69`；
`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/query/PanelDataBinder.java:131`

- Item Types: `Fix | Decision`

- [ ] **D3（Decision）— 包装修复点（裁定为 a）**：在 `PanelParamEvaluator.evaluate` 内把 4 处裸 `IllegalArgumentException` 包为
  `throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG).param(ARG_REASON, <具体原因>).cause(e);`（静态工具无 panelId 上下文，源点不填 panelId）；
  随后在 `PanelDataBinder.queryPanelData:131` 用 `try { evaluate(...) } catch (NopException ne) { throw ne.param(ARG_PANEL_ID, panelId); }` **补 panelId 后 rethrow**（catch 紧贴 `:131`，仍在 `:136` 的 executeQuery try 之外，保持结构清晰）。
  **不改 `evaluate` 静态方法签名**（避免影响所有调用方）。放弃选项 (b)（移入 `:136` try 包成 `ERR_DATAV_QUERY_FAILED`），以保留独立错误码。
- [ ] **ErrorCode 同步**：更新 `ERR_DATAV_INVALID_PARAM_CONFIG`（`NopDatavErrors.java:119`）的 `params` 声明追加 `ARG_PANEL_ID`，message 模板改为含 panelId（如 `"Invalid paramMapping config for panel: {panelId}, reason: {reason}"`，原模板 "for dashboard" 语义不准，一并修正），使格式化消息携带 panelId。
- [ ] 落实 D3：4 类配置错误（invalid JSON / 非 object / rule 非 object / 缺 source）均不再以裸 `IllegalArgumentException` 逃逸。
- [ ] 新增/扩展单测：对 4 类错误各起一例（经 `PanelDataBinder.queryPanelData` 公共入口），断言抛出 `NopException`、`errorCode == ERR_DATAV_INVALID_PARAM_CONFIG`、`param(ARG_PANEL_ID) == 期望 panelId`、`param(ARG_REASON)` 非空、cause 非空。

Exit Criteria:

- [ ] 4 类 paramMapping 配置错误均经 `NopException` + `ERR_DATAV_INVALID_PARAM_CONFIG` 抛出，无裸 `IllegalArgumentException` 逃逸到 GraphQL 边界（focused test 覆盖 4/4）。
- [ ] 异常携带 `panelId` param（ErrorCode params 声明 + message 模板均已含 panelId）与 cause 链（test 断言）。
- [ ] **无静默跳过**：错误显式抛出，非吞异常/返回 null 占位。
- [ ] owner-doc：核对 `docs-for-ai/02-core-guides/error-handling.md` 两层策略与本修复一致；若该文档未具体到 panelId/ErrorCode 细节，注明 `No owner-doc update required`（仅当文档无矛盾条款）。
- [ ] `./mvnw test -pl nop-datav -am` 全绿。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 报告交付 SMTP 移出 session（Dim14-02）

Status: planned
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/report/ReportDeliveryExecutor.java:122-214`

- Item Types: `Fix`

- [ ] 重构 `doExecute`/`execute`：文件落盘 + 交付记录状态 UPDATE（SUCCEEDED）在 `runInNewSession` 内完成并提交；`notificationSender.sendReport`
  在 session 关闭后（或 `txn().afterCommit(...)`）执行，使 SMTP 期间不持 JDBC 连接。
- [ ] 处理顺序：交付记录先提交 SUCCEEDED，邮件后发；若邮件发送抛错，回补交付记录为 FAILED（经独立新 session/`markFailedSafe` 同款模式）并记 errorMsg，确保「邮件已发但记录未更新」窗口消除或最小化。
- [ ] 保留既有 `executeSyncForTest` 测试辅助路径的语义（同步执行仍可断言交付记录状态）；如同步路径因 session 拆分需调整，显式更新其调用契约。
- [ ] 新增/扩展测试：断言 `sendReport` 调用发生在交付记录 SUCCEEDED UPDATE 提交之后（可用计数/mock/标志位 verify sendReport 被调用时机，
  或断言 sendReport 抛错时记录回补 FAILED 且无「email sent but record RUNNING」状态）。

Exit Criteria:

- [ ] `notificationSender.sendReport` 不再在持有 JDBC 连接的 ORM session 内执行（代码追踪确认：调用点在 `runInNewSession` 块之外 或 `afterCommit` 回调内）。
- [ ] 交付记录 SUCCEEDED 先于邮件提交；邮件失败时记录回补 FAILED，无「email sent / record still RUNNING」状态（focused test）。
- [ ] 既有报告交付 E2E（`TestNopDatavReportE2E`）仍全绿，未因 session 拆分回归。
- [ ] **接线验证**：closure audit 确认 sendReport 在运行时确实于 session 外被调用（代码追踪 + 测试），非仅函数位置移动。
- [ ] **无静默跳过**：邮件失败显式回补 FAILED + 日志，非吞异常。
- [ ] owner-doc：`schedule-report-design.md` §3（runInNewSession 意图）与 live 一致。
- [ ] `./mvnw test -pl nop-datav -am` 全绿。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] Dim09-01：面板查询公共路径 4 类配置错误均经 `NopException`+`ErrorCode` 抛出，无裸 `IllegalArgumentException` 逃逸。
- [ ] Dim14-02：SMTP 移出 session；交付记录先提交后发邮件；邮件失败回补 FAILED。
- [ ] 不存在被静默降级到 deferred 的 in-scope contract drift。
- [ ] owner docs（error-handling / schedule-report §3）与 live baseline 一致。
- [ ] 独立子 agent closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）PanelParamEvaluator 4 处在运行时真正抛 NopException（非空壳），（b）sendReport 运行时确于 session 外调用（非仅位置移动）。
- [ ] `./mvnw compile -pl nop-datav -am`
- [ ] `./mvnw test -pl nop-datav -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 其余错误码语义 P2（Dim09-02/03/05、AR-4）见 backlog，可作后续一致性清扫。

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Audit Session:
- Evidence:

Follow-up:

- no remaining plan-owned work（关闭时确认）
