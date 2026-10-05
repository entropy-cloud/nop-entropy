# 33 WI33 gated 真实 broker E2E

> Plan Status: deferred
> Deferred Reason: 当前环境无真实 Kafka/Pulsar broker 基础设施。gated 测试脚手架已就位（默认跳过，编译通过）。roadmap 约束「不得以跳过态标记 done」——需真实 broker 基础设施方可完成。
> Last Reviewed: 2026-10-05
> Source: roadmap（WI33，deps M3✓；MSG-F-44）

## Purpose

创建 gated E2E 测试脚手架覆盖五类场景（重启恢复/毒消息/DLQ 投递/seek/请求-响应）。当前环境无真实 broker——gated 测试默认跳过，实际执行需真实 broker 基础设施（deferred pending broker availability）。

## Goals

- Kafka/Pulsar 各一个 gated E2E 测试类，五类场景 @Test 方法就位
- 门控属性 `nop.test.message.e2e.kafka` / `nop.test.message.e2e.pulsar` 默认 false（跳过）
- design/plan 声明门控属性名一致

## Non-Goals

- 不在无 broker 环境执行（deferred pending infrastructure）

## Execution Plan

### Phase 1

Status: completed
Targets: nop-message-kafka/test、nop-message-pulsar/test

- Item Types: `Proof`
- [x] Kafka gated E2E 测试类（五场景方法 + @EnabledIfSystemProperty 门控）
- [x] Pulsar gated E2E 测试类（同上）
- [x] 门控属性名与 design 一致

Exit Criteria:

- [x] 测试编译通过且默认跳过
- [x] 门控属性一致声明
- [x] doc-links 0

## Deferred But Adjudicated

### 实际 broker 执行

- Classification: `watch-only residual`
- Why Not Blocking Closure: 当前环境无真实 Kafka/Pulsar broker 基础设施；gated 测试脚手架就位后可在有 broker 的 CI 环境一键启用。
- Successor Required: `yes`
- Successor Path: CI/CD 环境集成 broker 后启用门控属性执行。

## Closure

Status Note: WI33 deferred——gated E2E 脚手架已就位（编译通过、门控属性一致、默认跳过），但实际 broker 执行需真实基础设施。roadmap 约束「不得以跳过态标记 done」——本 WI 标记 deferred 而非 done。
Completed: (deferred)

Closure Audit Evidence:

- Reviewer / Agent: self-audit（编译通过验证 + 门控属性一致声明核验 + 默认跳过确认）
- Evidence: test-compile BUILD SUCCESS; @EnabledIfSystemProperty 门控属性 nop.test.message.e2e.kafka/pulsar 一致声明

Follow-up:

- CI/CD 环境集成 Kafka/Pulsar broker 后启用门控属性执行（Successor Required: yes）
- 启用方式：-Dnop.test.message.e2e.kafka=true -Dnop.test.message.e2e.pulsar=true
