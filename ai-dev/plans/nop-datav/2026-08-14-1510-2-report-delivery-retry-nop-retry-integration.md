# 2 报告交付失败重试 — 接入 nop-retry（DEFERRED）

> Plan Status: deferred
> Last Reviewed: 2026-08-14
> Source: D5-1 plan `2026-08-10-1230-1` Non-Blocking Follow-ups「报告交付失败重试（接 nop-retry retryPolicyId）」+ D5-2 plan `2026-08-10-1230-2` Non-Blocking Follow-ups「告警评估失败重试（接 nop-retry）」+ throwable-sweep plan `2026-08-14-1452-1` Non-Blocking Follow-ups「Retry integration with nop-retry for report delivery and alert evaluation」
> Related: `ai-dev/plans/nop-datav/2026-08-10-1230-1-scheduled-report-generation-and-delivery.md`、`ai-dev/plans/nop-datav/2026-08-14-1452-1-executor-throwable-exception-classification-sweep.md`
> Mission: nop-datav
> Work Item: D5-1 deferred follow-up — 报告交付失败重试

## Why Deferred（本轮独立调研结论）

本计划在 2026-08-14 DRAFT_PLANS 轮次起草为 draft，经独立子 agent 对抗性审查 + 平台 RPC 基础设施可行性调研后，确认 **当前不具备 clean 落地条件**，裁定为 `deferred`。重新触发需先解除以下平台级阻塞。

### 阻塞 1（Blocker）：平台无本地 in-process `IRpcServiceInvoker`

- `nop-retry` 引擎 `RetryEngineImpl` 严格执行路径**唯一出口**为 `doExecute`（`RetryEngineImpl.java:363-374`）→ `rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, cancelToken)`；`IRetryTask extends IRpcCall`，无 callback/本地分发/直调替代路径（RPC-only）。
- 平台仅有两个**生产** `IRpcServiceInvoker` 实现：`ClusterRpcServiceInvoker`、`HttpRpcServiceInvoker`（均在 `nop-rpc-cluster`），二者**始终跨网络**（HTTP/cluster 服务发现），不能派发到同进程 GraphQL biz action。
- 平台**无** `LocalRpcServiceInvoker` / `GraphQLRpcServiceInvoker`。`nop-graphql-core` 的 `RpcServiceOnGraphQL`（`RpcServiceOnGraphQL.java:31`）能路由到本地 biz action，但它实现的是 `IRpcService`（单服务），**非** `IRpcServiceInvoker`（多服务派发器），且 serviceName 在构造期固定。
- `nop-datav-service` 作为库模块，`nop-rpc-cluster` 为 `<scope>test</scope>`（`pom.xml:100-103`），符合平台「生产 RPC 由宿主 app 提供」约定——模块自身不提供生产 invoker。
- **结论**：要使 retry 在同进程生效，必须**新建**一个本地 `IRpcServiceInvoker` 适配 bean（包裹 `IGraphQLEngine`/`RpcServiceOnGraphQL`，约 15 行），但平台当前无此 bean，且这是跨切面平台级基础设施，不宜在 BI 域模块内私造。

### 阻塞 2（Blocker）：唯一「参考范式」nop-job 自身未端到端验证（断裂）

- `NopRetryJobRetryBridge.java:19-20` 硬编码 `SERVICE_NAME="NopJobService"`、`SERVICE_METHOD="fireJob"`，但全仓**不存在** `class NopJobService` 或 `@BizMutation("fireJob")`（nop-job BizModel 为 `NopJobFireBizModel`，bizObj 名 `NopJobFire`，仅暴露 `cancelFire`/`rerunFire`）。
- 三份 nop-job 审计（`ai-dev/audits/nop-datav/...` 等）将其标记为 P2 断裂「重试任务提交后无法路由到正确的服务方法，nop-retry-engine 实际调用时将因找不到 `NopJobService.fireJob` 而失败」「当前重试机制在生产环境中实际无法工作」，且 finding 至今未 action。
- **结论**：本计划原拟「镜像 nop-job 范式」基于一个**未验证/断裂**的先例，不能作为可信基线。

### 阻塞 3（Major）：系统级 Auth-bypass 机制无先例

- retry 引擎重触发 biz action 时无用户上下文。需一个系统级、RPC 可达、绕过用户态 `@Auth` 的 biz action。平台无此类「系统级无用户调用」biz action 的先例可循（cron 路径走 `beanMethod` job invoker 直调 bean 方法，不经 GraphQL Auth；retry 路径必须经 `IRpcServiceInvoker` → GraphQL）。该 Auth 机制需独立设计裁定。

## Re-trigger Conditions（任一解除即可重新起草为 draft）

1. **平台补齐本地 `IRpcServiceInvoker`**（平台级，在 `nop-graphql-core` 或 `nop-rpc-core` 提供 `GraphQLLocalRpcServiceInvoker implements IRpcServiceInvoker` bean），**且** nop-job retry 断裂（`NopJobService.fireJob`）已修复并端到端验证；或
2. **nop-datav 决定私造模块内本地 invoker 适配 bean**（接受跨切面基础设施落在域模块的权衡），并配套解决系统级 Auth-bypass 机制；或
3. 业务出现「瞬时交付失败必须自动重试」的硬需求，使成本/收益反转。

## 原始设计意图（保留供 re-trigger 时参考）

- **目标**：报告任务配置 `retryPolicyId` 后，交付失败（取数/落盘/通知任一路径）经 nop-retry 自动重试同一交付记录；重试耗尽 → 死信；未配置策略 → 向后兼容。
- **告警评估重试明确排除**：`NopDatavAlertScheduler` 每个 cron tick 已重新 `evaluate`，状态跨失败保留，通知失败经 rearm 自愈——nop-retry 冗余。
- **涉及的 ORM 变更（Protected Area）**：`NopDatavReportTask` 新增 `retryPolicyId`（+ 视死信追溯需求定 `retryRecordId`）。
- **涉及代码**：`ReportDeliveryExecutor.java:229-241`（session 内 FAILED）、`:252-259`（session 外 SMTP FAILED）、`:111-123`（每次触发插新交付、不重试失败记录）。

## Deferred But Adjudicated

### 报告交付失败重试整体

- Classification: `out-of-scope improvement`（当前 blocked 于平台 RPC 基础设施）
- Why Not Blocking Closure: D5 已有重启恢复（`NopDatavReportDeliveryRecovery`）+ 周期 stuck 扫描（plan `2026-08-14-1510-1`）作为交付可靠性的兜底；retry 是增强项非正确性 gap。当前阻塞为平台级（无本地 invoker + nop-job 先例断裂），非 nop-datav scope 内可独立解除。
- Successor Required: `yes`（见 Re-trigger Conditions）
- Successor Path: 待平台补齐本地 `IRpcServiceInvoker` 或 nop-job retry 断裂修复后，重新起草本 plan

## Non-Blocking Follow-ups

- 跟踪 nop-job `NopJobService.fireJob` 断裂修复进度（3 份审计 finding 仍 open）
- 跟踪平台是否引入本地 in-process `IRpcServiceInvoker`

## Closure

Status Note: 本 plan 于 2026-08-14 DRAFT_PLANS 轮起草，经独立审查 + 平台 RPC 可行性调研确认 blocked 于平台级基础设施（无本地 `IRpcServiceInvoker`、nop-job retry 先例断裂、系统级 Auth-bypass 无先例），裁定 `deferred`。Re-trigger 条件见上。nop-datav 交付可靠性由重启恢复 + 周期 stuck 扫描（`2026-08-14-1510-1`）兜底。
