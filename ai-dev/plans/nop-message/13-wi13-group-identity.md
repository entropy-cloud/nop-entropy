# 13 WI13 resolveGroupId 身份模型修正

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI13，deps WI4✓；对应 MSG-F-12 与 MSG-F-28）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（D7 裁定）

## Purpose

按 D7 裁定修正 Kafka 消费组身份模型：全局 `groupId` 从「静默遮蔽 subscribeName」降级为「命名空间前缀」，生效身份 = `${groupId}.${subscribeName}`；`subscribeName` 缺失时类型化拒绝（默认配置下 subscribeName 即身份）。同时修复 F-28：`extraProps` 不得覆盖 `group.id` / `bootstrap.servers` 等显式管理键。

## Current Baseline

- `KafkaMessageService.resolveGroupId`（实测 `:344-353`）：全局 `defaultConsumerConfig.getGroupId()` 非空即返回，静默遮蔽 `options.getSubscribeName()` → 同组异构订阅永久分区饥饿（F-12）。`buildConsumerProps`（`:162-187`）/`buildProducerProps`（`:140-160`）：extraProps 最后 putAll（F-28）。`ERR_GROUP_ID_NOT_CONFIGURED` 兜底 `:279-283`。
- **破坏性影响面（审查 Major-1，grep 实证）**：D7 后无 subscribeName 的调用将 fail-fast——① `MessageRpcServer.doStart`（nop-rpc-core `:71`，2-arg subscribe、无 subscribeName 注入途径；装配于 stream-control-rpc.beans.xml）；② `GraphQLMessageSubscriptionRegistrar:78`（options 可 null）；③ `ReflectionMessageSubscriptionRegistrar:64-86`（@MessageListener 无 subscribeOptionsBean 时 null）；④ `DataPlaneMessageServiceAdapter:80-92`（仅 null options 合成 subscribeName，options 非空无名字仍拒）。配置面 grep `groupId` 全仓 0 命中——全局回退路径的仓内配置用户为零；control-rpc 默认 bean 为 Local、data-plane 默认 bean 为 SysDaoMessageService（均非 Kafka），仓内测试回归面为零。
- **空档声明**：消费方代码迁移（控制面/rpc-core 注入 subscribeName）无 WI 承接（WI34 为纯文档项）——登记于 Non-Blocking Follow-ups。

## Goals

- **D7 落地**：`resolveGroupId` 重写——`subscribeName` 缺失 → 类型化拒绝（无论全局配置是否存在）；有全局 → `${global}.${subscribeName}`；否则 → `subscribeName`。`doSubscribe` 既有兜底守卫保留（防御式）。
- **F-28 修复**：`buildConsumerProps` / `buildProducerProps` 改为先 putAll `extraProps`、后写显式管理键（bootstrapServers / clientId / group.id / 全部调优键）——显式键不可被 extraProps 覆盖，javadoc 声明该契约（extraProps 仅对未显式管理的键生效，如 SASL/SSL）。
- 测试：① 全局+subscribe → 前缀身份；② 仅 subscribe → subscribe（默认配置生效断言）；③ 缺 subscribe → 类型化拒绝（即使有全局）；④ F-28——extraProps 含 group.id/bootstrap.servers/max.poll.records 时显式值胜出；⑤ 多 topic 隔离——不同 subscribeName 产生不同 group.id（createConsumer 接缝捕获 props），不同订阅不共享分配域。

## Non-Goals

- 不改 Pulsar 侧 subscribeName 处置（其随机订阅名消除归 WI29/F-29）。
- 不动 ①-④ 调用方代码（迁移无 WI 承接的空档登记于 Follow-ups；仓内默认 bean 均为 Local，无回归风险）。

## Scope

### In Scope

- `nop-message-kafka`：KafkaMessageService（resolveGroupId + 两个 buildProps 顺序）、TestKafkaConsumeTask 或新测试文件、日志、roadmap WI13 状态行。

### Out Of Scope

- WI17/WI22/WI29/WI34。

## Execution Plan

### Phase 1 - 身份模型与 props 顺序

Status: completed
Targets: `nop-message/nop-message-kafka`

- Item Types: `Fix`

- [x] resolveGroupId 按 D7 重写 + javadoc（破坏性变更声明）
- [x] buildConsumerProps/buildProducerProps 顺序反转 + javadoc 契约（「被代码写入的键为管理键、不可被 extraProps 覆盖」；同步修正 KafkaClientConfig 「unless overridden」与 warnIfAutoCommit 两处失效 javadoc）
- [x] 测试 ①-⑤

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-kafka` 退出 0 全绿
- [x] **无静默跳过**：缺 subscribeName 必显式失败（含全局配置场景）
- [x] **新功能必有测试**：①-⑤ 各有断言
- [x] mission 回归（kafka + core + rpc-core）退出 0
- [x] `ai-dev/logs/` 条目
- [x] Owner-doc：design backend-semantics 3.4（D7）已含裁定，补充「Kafka 实现口径」一行（前缀分隔符与缺失拒绝）

## Closure Gates

- [x] F-12/F-28 消除（测试证明）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-kafka -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/13-wi13-group-identity.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- **空档登记**：消费方代码迁移（MessageRpcServer / GraphQLMessageSubscriptionRegistrar / ReflectionMessageSubscriptionRegistrar / DataPlaneMessageServiceAdapter 注入 subscribeName）无 WI 承接（WI34 仅文档）——按 roadmap Rules 应以 Follow-up Backlog 取号或随 WI34 owner doc 给出迁移模板；不阻塞本 plan（D7 裁定的破坏性变更 + 仓内默认 bean 均为 Local）。

## Closure

Status Note: WI13 经独立子代理 closure audit 七项全 PASS：D7 身份模型与 F-28 管理键契约落地且有 5 条真实测试（47/47）、三模块回归实跑全绿、影响面清单与空档登记齐备。audit 的 Minor（幽灵 Follow-up 已删、默认 bean 表述已修、Phase 状态已翻转）均在收口时处置。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_24582d8e-9f8e-40ac-b543-e333e32f9833（fresh session，自行重跑测试）
- Evidence:
  - D7 PASS：resolveGroupId :357-369 三支路 + 守卫 description 重写
  - F-28 PASS：extraProps 先合并 + 三处 javadoc 修正
  - 测试 PASS：kafka 47/47（新增 5 条）、core 17/0fail/1skip、rpc-core 30/0
  - design PASS：backend-semantics 3.4 Kafka 实现口径一行
  - 影响面 PASS：4 调用点 grep 实证（data-plane 默认 bean 表述已修正为 SysDao）
  - git 范围 PASS
  - Deferred 分类检查：无新增 deferred；消费方迁移空档留 Follow-ups
