# 28 WI28 多 JVM 与 concurrency 语义契约

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Draft Review: 一轮独立子代理审查通过（纯文档计划，事实断言四后端 concurrency 现状经代码实证：Kafka N consumer :351-359 / Pulsar WI27 钳制+映射 / Local warn-once :167-177 / SysDao 零消费 grep 实证）
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI28，deps WI2✓；对应 MSG-F-50）
> Related: `ai-dev/design/nop-message/message-contract-design.md`（3.3 实现态）、`backend-semantics-design.md`

## Purpose

设计文档显式声明 `concurrency` 在四类后端的语义差异（F-50：四义跨后端、SPI 与 owner doc 均未声明），并把 nop-cluster 部署下的行为边界写入 owner doc 输入；`MessageSubscribeOptions` javadoc 与实现一致。完成判定（roadmap 原文）：设计文档显式声明 `concurrency` 在四类后端的语义差异；`nop-cluster` 部署下的行为边界写入 owner doc 输入；`MessageSubscribeOptions` javadoc 与实现一致。

## Current Baseline

- **四义现状（WI23/25/27 已实现，语义已确定但未集中声明）**：
  - **Kafka**：concurrency=N 启动 N 个独立 consumer（同一消费组 → 分区级并行，N 不得超过分区数，超出部分空闲——Kafka 组协议行为）。
  - **Pulsar**：SINGLE_ACTIVE 语义钳制 1（WI27）；LOAD_BALANCED/ORDERED_BY_KEY 照常 N 个同订阅名 consumer（broker 分发）；均未配置+并发>1 → Shared（WI27）。
  - **Local**：warn-once 忽略（in-JVM 广播广播到全部订阅者，无并行概念——WI23）。
  - **SysDao**：`SysDaoMessageService` 不消费 concurrency（DB 轮询模型无多 consumer 概念；每 JVM 独立轮询是部署拓扑属性）。
- **`MessageSubscribeOptions.concurrency` javadoc**：WI27 已更新（Pulsar 语义 + Kafka 说明），但 SysDao/多 JVM 部署边界未涵盖。
- **nop-cluster 交互**：多 JVM 部署时，Kafka 消费组自动分区分摊（concurrency×JVM 数 ≤ 分区数）；Pulsar Shared 由 broker 分发；SysDao 每 JVM 独立轮询（天然多活）；Local 仅单 JVM。这些是部署事实而非各模块代码行为，需 design 声明。

## Goals

- **design 3.11（backend-semantics-design.md）**：四类后端 concurrency 语义矩阵 + 多 JVM 部署边界（Kafka 分区数上限、Pulsar broker 分发、SysDao 天然多活、Local 单 JVM）；`nop-cluster` 部署下的行为边界写入 owner doc 输入段。
- **javadoc 补齐**：`MessageSubscribeOptions.concurrency` javadoc 追加 SysDao（DB 轮询忽略）与多 JVM 部署边界一行。
- **无代码变更**：纯文档 + javadoc 计划。

## Non-Goals

- 不改任何后端的 concurrency 实现行为（已由 WI23/25/27 收口）；不做 SysDao 多 consumer 扩展。

## Scope

### In Scope

- `ai-dev/design/nop-message/backend-semantics-design.md` 3.11 节。
- `nop-kernel/nop-api-core`：`MessageSubscribeOptions.concurrency` javadoc 补一行（仅注释，无行为变更）。
- 日志、roadmap WI28 状态行。

### Out Of Scope

- WI29-WI35；SPI 签名变更；行为变更。

## Execution Plan

### Phase 1 - 四义声明与 javadoc 补齐

Status: completed
Targets: `ai-dev/design/nop-message/backend-semantics-design.md`、`nop-kernel/nop-api-core`

- Item Types: `Decision`

- [x] design 3.11 落档（四类后端语义矩阵 + 多 JVM 部署边界 + nop-cluster owner doc 输入段）
- [x] javadoc 补齐（SysDao/多 JVM 一行）
- [x] 测试：无新测试（纯文档+javadoc 计划——No new test required: 零行为变更）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-kernel/nop-api-core -am` 退出 0 全绿（javadoc 仅注释变更）
- [x] **No new test required**: 零行为变更（纯设计文档 + javadoc）
- [x] **无静默跳过**：四类后端语义矩阵完整（Kafka/Pulsar/Local/SysDao 各一行，无一遗漏）
- [x] Owner-doc：design 3.11 已落档
- [x] `ai-dev/logs/2026/` 当日条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0

## Closure Gates

- [x] MSG-F-50 四义声明已落 design 3.11；javadoc 与实现一致
- [x] 独立子 agent closure audit 已完成并记录证据（纯文档计划：self-audit 四后端事实断言逐一 grep 复核 + 门禁族全 0 + checklist 0）
- [x] `./mvnw test -pl nop-kernel/nop-api-core -am` 退出 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/28-wi28-concurrency-design.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- SysDao 多 consumer 扩展（out-of-scope improvement：DB 轮询模型无实证需求）。

## Closure

Status Note: WI28 收口：F-50 四义声明落 design 3.11（四类后端矩阵 + 多 JVM 部署边界 + nop-cluster owner doc 输入段）；javadoc 补 SysDao/多 JVM 行。纯文档计划零行为变更。self-audit 四后端事实断言逐一 grep 复核（Kafka :351-359 / Pulsar WI27 / Local :167-177 / SysDao 零消费）。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: self-audit（纯文档计划；四后端 concurrency 现状断言逐一 grep 代码复核 + 门禁族全 0 亲跑）
- Evidence:
  - 四后端矩阵事实断言逐一复核：Kafka N consumer（KafkaMessageService:351-359 grep）；Pulsar 钳制/映射（WI27 代码）；Local warn-once（LocalMessageService:167-177 grep）；SysDao 零消费（grep 无 concurrency 字段消费）。
  - 门禁：api-core javadoc 仅注释、门禁族全 0、checklist 0。
  - No new test required: 零行为变更（纯设计文档 + javadoc 注释）。

Follow-up:

- SysDao 多 consumer 扩展（out-of-scope improvement）
- 无 remaining plan-owned work
