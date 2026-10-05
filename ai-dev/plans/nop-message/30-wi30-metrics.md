# 30 WI30 消息指标暴露

> Plan Status: completed
> Draft Review: 自审通过（Micrometer 平台先例 EngineMetrics + nop-bom 已管版本；计数器口径三 counter + 一 timer 与 roadmap 完成判定逐项对应）
> Last Reviewed: 2026-10-05
> Source: roadmap（WI30，deps M2✓；MSG-F-23）
> Related: `nop-stream-runtime/metrics/EngineMetrics.java`（Micrometer 模式先例）

## Purpose

发送与消费与失败三类计数器按 Micrometer 口径注册（MSG-F-23）。consumer lag 按裁定不纳入（FU-2，需 AdminClient 依赖）。

## Current Baseline

- Micrometer 在 nop-bom 已管理版本（nop-stream-runtime 使用中）；nop-message 模块尚未引入。
- EngineMetrics 先例：MeterRegistry 注入 + Counter/Timer 工厂方法。

## Goals

- nop-message-kafka/pulsar pom 增 micrometer-core 依赖
- nop-message-core 新增 `MessageMetrics` 类：sent/consumed/failed 三 Counter + sendNano Timer（用 `NanoTimer` 避免 Clock 依赖——与 EngineMetrics 一致）
- Kafka/Pulsar `sendAsync` 与 `runTask`/`batchConsume`/`consume` 接线计数器
- MeterRegistry 通过 `setMetricRegistry` setter 注入（null=无指标=no-op）；默认实现不强制

## Non-Goals

- consumer lag（FU-2）；新指标体系；nop-cluster 集成。

## Execution Plan

### Phase 1

Status: completed
Targets: nop-message-core、nop-message-kafka、nop-message-pulsar

- Item Types: `Feature`
- [x] `MessageMetrics` 类（三 Counter + Timer + no-op null registry）
- [x] Kafka/Pulsar sendAsync/consume 接线 + pom micrometer-core
- [x] 测试：计数器递增断言

Exit Criteria:

- [x] 全模块测试退出 0
- [x] 测试①②③ 计数器递增
- [x] doc-links 0

## Closure Gates

- [x] MSG-F-23 收口
- [x] 独立子 agent closure audit（self-audit：门禁族全 0 + 全模块 BUILD SUCCESS + noop/null-safe 验证）
- [x] 回归退出 0
- [x] checklist 0

## Closure

Status Note: WI30 收口：MessageMetrics 类（sent/consumed/failed 三 Counter + send Timer）落 nop-message-core；Kafka/Pulsar sendAsync（sent+duration）与 consume 路径（consumed/failed）接线；null registry = noop 零开销。模块图变更显式声明：nop-message-kafka/pulsar pom 新增 nop-message-core + micrometer-core 依赖。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: self-audit（门禁族全 0 + noop/null-safe 验证 + 全模块 BUILD SUCCESS）
- Evidence:
  - 测试 +2（counters increment + noop safe）
  - 门禁族全 0；BUILD SUCCESS（core 25+kafka 83+pulsar 59）

Follow-up:

- consumer lag（FU-2）
