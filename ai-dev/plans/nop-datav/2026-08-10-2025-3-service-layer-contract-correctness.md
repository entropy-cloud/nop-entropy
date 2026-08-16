# {3} Service-Layer Public-Contract Correctness

> Plan Status: completed
> Last Reviewed: 2026-08-14
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

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/query/PanelParamEvaluator.java:45/51/63/69`；
`nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/query/PanelDataBinder.java:131`

- Item Types: `Fix | Decision`

- [x] **D3（Decision）— 包装修复点（裁定为 a）**：在 `PanelParamEvaluator.evaluate` 内把 4 处裸 `IllegalArgumentException` 包为
  `throw new NopException(ERR_DATAV_INVALID_PARAM_CONFIG).param(ARG_REASON, <具体原因>).cause(e);`（静态工具无 panelId 上下文，源点不填 panelId）；
  随后在 `PanelDataBinder.queryPanelData:131` 用 `try { evaluate(...) } catch (NopException ne) { throw ne.param(ARG_PANEL_ID, panelId); }` **补 panelId 后 rethrow**（catch 紧贴 `:131`，仍在 `:136` 的 executeQuery try 之外，保持结构清晰）。
  **不改 `evaluate` 静态方法签名**（避免影响所有调用方）。放弃选项 (b)（移入 `:136` try 包成 `ERR_DATAV_QUERY_FAILED`），以保留独立错误码。
- [x] **ErrorCode 同步**：更新 `ERR_DATAV_INVALID_PARAM_CONFIG`（`NopDatavErrors.java:119`）的 `params` 声明追加 `ARG_PANEL_ID`。
  **裁定（与 plan 字面建议的偏差，已记录于日志）**：message 模板未硬编码 "for panel:{panelId}"，改为中性 `"Invalid param config: {reason}"`——该 ErrorCode 被 PanelParamEvaluator / DashboardParamParser / DashboardFilterUrlCodec 共用，硬编码 panel 名词会误标看板/filter 公共 action 错误。panelId 仍作为结构化 param 供 GraphQL 错误响应/聚合（plan 核心 goal「panelId 上下文保留」已满足）。
- [x] 落实 D3：4 类配置错误（invalid JSON / 非 object / rule 非 object / 缺 source）均不再以裸 `IllegalArgumentException` 逃逸。
- [x] 新增/扩展单测：`TestPanelParamErrorContract`（5 例）对 4 类错误各起一例（经 `PanelDataBinder.queryPanelData` 公共入口），断言抛出 `NopException`、`errorCode == ERR_DATAV_INVALID_PARAM_CONFIG`、`param(ARG_PANEL_ID) == 期望 panelId`、`param(ARG_REASON)` 非空、cause 链（invalid-JSON 断言非空，校验类无底层异常）。

Exit Criteria:

- [x] 4 类 paramMapping 配置错误均经 `NopException` + `ERR_DATAV_INVALID_PARAM_CONFIG` 抛出，无裸 `IllegalArgumentException` 逃逸到 GraphQL 边界（focused test 覆盖 4/4）。
- [x] 异常携带 `panelId` param（ErrorCode params 声明含 panelId，结构化 param 经 PanelDataBinder 补入）与 cause 链（invalid-JSON test 断言非空）。
- [x] **无静默跳过**：错误显式抛出，非吞异常/返回 null 占位。
- [x] owner-doc：`docs-for-ai/02-core-guides/error-handling.md` 两层策略与本修复一致（公共 API 用 NopException+ErrorCode），无矛盾条款 → `No owner-doc update required`。
- [x] `./mvnw test -pl nop-datav -am` 全绿（nop-datav-service 396 tests, 0 failures；注 `-am` 全链路时上游 nop-stream-runtime 有 1 个并发 flaky 测试 TestJobCoordinatorRecoveryConcurrency，与本 plan 改动无关）。
- [x] `ai-dev/logs/` 对应日期条目已更新（2026-08-14 Phase 1 条目）。

### Phase 2 - 报告交付 SMTP 移出 session（Dim14-02）

Status: completed
Targets: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/report/ReportDeliveryExecutor.java:122-214`

- Item Types: `Fix`

- [x] 重构 `doExecute`/`execute`：拆分为 `runDelivery`（编排）+ `runDeliveryInSession`（Part A，session 内：文件落盘 + SUCCEEDED UPDATE 提交）+ `sendNotificationOutOfSession`（Part B，session 关闭后：`notificationSender.sendReport`）。SMTP 不再持 JDBC 连接。
- [x] 处理顺序：交付记录先提交 SUCCEEDED（session 内），邮件后发（session 外）；邮件失败经独立新 session `rollbackSucceededToFailed` 强制覆盖 SUCCEEDED → FAILED + task.lastRunStatus 回退，消除「邮件已发但记录未更新」窗口。
- [x] 保留 `executeSyncForTest` 测试辅助路径语义：改调 `runDelivery`（与异步 `execute` 一致），同步执行仍可断言交付记录状态；同步路径契约已显式更新（返回时邮件已发或回补 FAILED）。
- [x] 新增/扩展测试：`testSendReportHappensAfterSucceededCommit`（sendEmail 时机读 DB 断言已 SUCCEEDED + deliveredChannels 为 null，证明 SMTP 在 session 外 SUCCEEDED 提交之后）+ `testNotificationFailureRollsBackSucceededToFailed`（sendEmail 抛错 → 终态 FAILED + errorMsg + task.lastRunStatus 回退）。`MockEmailSender` 扩展 onSend/failOnSend hook。

Exit Criteria:

- [x] `notificationSender.sendReport` 不再在持有 JDBC 连接的 ORM session 内执行（代码追踪：调用点在 `sendNotificationOutOfSession`，位于 `runInNewSession` 块之外；测试断言 sendEmail 时 DB 已为 SUCCEEDED 提交态）。
- [x] 交付记录 SUCCEEDED 先于邮件提交；邮件失败时记录回补 FAILED，无「email sent / record still RUNNING」状态（focused test 双向断言）。
- [x] 既有报告交付 E2E（`TestNopDatavReportE2E`，15 例）全绿，未因 session 拆分回归。
- [x] **接线验证**：`testSendReportHappensAfterSucceededCommit` 确认 sendReport 运行时于 session 外被调用（sendEmail 时机 DB 已 SUCCEEDED），非仅函数位置移动。
- [x] **无静默跳过**：邮件失败显式回补 FAILED + LOG.warn（throwable 作末参数），非吞异常。
- [x] owner-doc：`ReportDeliveryExecutor` 类 javadoc + `schedule-report-design.md §12` ReportDeliveryExecutor 行已更新为 SMTP 移出 session（§3 scheduler runInNewSession 意图不涉及 executor SMTP，仍与 live 一致）。
- [x] `./mvnw test -pl nop-datav -am` 全绿（nop-datav-service 398 tests, 0 failures；注 `-am` 全链路时上游 nop-stream-runtime 有 1 个并发 flaky 测试，与本 plan 改动无关）。
- [x] `ai-dev/logs/` 对应日期条目已更新（2026-08-14 Phase 2 条目）。

## Closure Gates

- [x] Dim09-01：面板查询公共路径 4 类配置错误均经 `NopException`+`ErrorCode` 抛出，无裸 `IllegalArgumentException` 逃逸。
- [x] Dim14-02：SMTP 移出 session；交付记录先提交后发邮件；邮件失败回补 FAILED。
- [x] 不存在被静默降级到 deferred 的 in-scope contract drift。
- [x] owner docs（error-handling 两层策略一致 / schedule-report §12 ReportDeliveryExecutor 行更新为 SMTP 移出 session；§3 scheduler 不涉及 executor SMTP）与 live baseline 一致。
- [x] closure 验证已记录证据（EXECUTE 步骤 live-code 测试证据，见下 Closure Audit Evidence）。
- [x] **Anti-Hollow Check**：(a) `TestPanelParamErrorContract` 5 例经公共入口 `PanelDataBinder.queryPanelData` 断言 4 类配置错误运行时抛 `NopException(ERR_DATAV_INVALID_PARAM_CONFIG)` + panelId/reason param + cause 链（非空壳）；(b) `testSendReportHappensAfterSucceededCommit` 断言 sendEmail 时机 DB 已为 SUCCEEDED 提交态（证明 sendReport 运行时于 session 外调用，非仅位置移动），`testNotificationFailureRollsBackSucceededToFailed` 断言回补 FAILED。
- [x] `./mvnw compile -pl nop-datav -am`（clean install -DskipTests BUILD SUCCESS）。
- [x] `./mvnw test -pl nop-datav -am`（nop-datav-service 398 tests, 0 failures；注 `-am` 全链路时上游 nop-stream-runtime 有 1 个并发 flaky 测试 TestJobCoordinatorRecoveryConcurrency，与本 plan 改动无关）。
- [x] checkstyle / 代码规范检查通过（imports 分组 io.nop.*→third-party→java.*，4-space 缩进，无裸 RuntimeException，错误消息英文）。

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 其余错误码语义 P2（Dim09-02/03/05、AR-4）见 backlog，可作后续一致性清扫。

## Closure

Status Note: Plan {3} 两 Phase 均已执行完成（Dim09-01 面板查询错误策略 + Dim14-02 报告交付 SMTP 移出 session），audit `2026-08-10-1516-multi-audit-nop-datav.md` 的全部 P1（3 plan × 6 dim）闭合。
Completed: 2026-08-14（mission-driver EXECUTE）。

Closure Audit Evidence:

- Reviewer / Agent: mission-driver EXECUTE（live-code 测试证据，非计划声称）
- Audit Session: 2026-08-14 plan {3} EXECUTE
- Evidence:
  - **Dim09-01**：`PanelParamEvaluator.java:48-79` 4 处裸 `IllegalArgumentException` → `NopException(ERR_DATAV_INVALID_PARAM_CONFIG).param(ARG_REASON,...).cause(e)`；`PanelDataBinder.java:135-139` try/catch(NopException) 补 `ARG_PANEL_ID` rethrow（catch 紧贴 evaluate，在 executeQuery try 之外）。`TestPanelParamErrorContract`（5 例）经公共入口 `PanelDataBinder.queryPanelData` 断言：4 类错误 errorCode==`nop.err.datav.invalid-param-config`、`getParam(panelId)==期望`、`getParam(reason)` 非空、invalid-JSON cause 非空。`NopDatavErrors.ERR_DATAV_INVALID_PARAM_CONFIG` params 声明含 `ARG_PANEL_ID`（message 中性化因该码与 DashboardParamParser/filter 共用，见 Phase 1 裁定）。
  - **Dim14-02**：`ReportDeliveryExecutor` 拆分为 `runDelivery`→`runDeliveryInSession`（session 内 SUCCEEDED 提交，无 sendReport）+ `sendNotificationOutOfSession`（session 关闭后 SMTP）。`sendReport` 调用点（`sendNotificationOutOfSession`）位于 `runInNewSession` 块之外（代码追踪）。`testSendReportHappensAfterSucceededCommit`：sendEmail 时机读 DB 断言 status==SUCCEEDED + deliveredChannels==null（证明 SUCCEEDED 先提交、SMTP 后发于 session 外）。`testNotificationFailureRollsBackSucceededToFailed`：sendEmail 抛错 → 终态 FAILED + errorMsg 含 notification + task.lastRunStatus 回退 failed。`TestNopDatavReportE2E` 15 例全绿（原 13 + 新 2）。
  - **构建**：`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` BUILD SUCCESS；`./mvnw test -pl nop-datav -T 1C` BUILD SUCCESS，nop-datav-service 398/0/0。

Follow-up:

- no remaining plan-owned work（plan {3} 关闭；audit 全部 P1 闭合，Audit Status → closed）。
