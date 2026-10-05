# 03 WI5 Spring 对照分析落档

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI5，deps 无）、`ai-dev/analysis/2026-10/nop-message-audit-baseline.md`、`ai-dev/analysis/2026-10/nop-message-capability-matrix.md`
> Related: WI3（D5 不引入 AckMode、D6 三层 DLQ 的裁定引用本分析）

## Purpose

产出 `ai-dev/analysis/2026-10/nop-message-spring-comparison.md`（roadmap 硬性指定路径）：以 spring-kafka 与 spring-pulsar 为对照基线，从 ack、错误处理、事务、容器生命周期、可观测性、测试支持六个维度对比 nop-message 的设计取舍。必须显式记录 AckMode、RetryTopic、CommonErrorHandler 三项为**不采纳**并给出理由，防止后续 agent 重新导入。本项不含代码变更。

## Current Baseline

- WI1 基线与 WI2 能力矩阵已落档；roadmap Sources 段已列出 spring-kafka（KafkaTemplate / @KafkaListener / ContainerProperties / AckMode / DefaultErrorHandler + DeadLetterPublishingRecoverer / CommonErrorHandler / KafkaTransactionManager / Micrometer KafkaMetrics）与 spring-pulsar（PulsarTemplate / @PulsarListener / 原生 deadLetterPolicy / transactions）为对照基线。
- roadmap D5 已裁定不引入 AckMode（(b') 返回协议 + 终止拒绝/部分提交信号），D6 已裁定三层 DLQ——本分析的职责是把这些「不采纳」的完整理由落档，供独立 closure audit 与后续 agent 引用，而非重新裁定。
- nop-message 现状：SPI 公分母 + beans.xml 覆盖切换；Spring 两栈是独立构件、两套注解、无共同父类型（roadmap Purpose 已断言，本分析需给出逐点论证）。
- `ai-dev/analysis/2026-10/` 目录存在，尚无 spring-comparison 文档。

## Goals

- 产出六维度对照文档：① ack 语义（AckMode vs onMessage 返回值协议）② 错误处理（DefaultErrorHandler/CommonErrorHandler/DeadLetterPublishingRecoverer vs 两后端现状与 D6 三层方案）③ 事务（KafkaTransactionManager/spring-pulsar transactions vs D11）④ 容器生命周期（ListenerContainer 生命周期模型 vs LifeCycleSupport/WI19）⑤ 可观测性（Micrometer KafkaMetrics vs WI30 平台口径）⑥ 测试支持（@EmbeddedKafka/Testcontainers/PulsarContainer vs WI33 gated E2E）。
- 显式记录三项不采纳：AckMode（D5）、@RetryableTopic（重试机制归 nop-retry，避免第三套重试）、CommonErrorHandler（错误回调已由 D5(b')/D6 的失败回调承载），每项给出理由与被否决的替代方案。
- 每个维度给「Spring 做法 → nop 现状 → 是否采纳/如何映射」三段式结论。
- Spring 侧事实以框架公开机制为准（机制级描述，不写版本号级 API 细节）；与 nop 侧差异逐点给 live 锚点或 roadmap/WI 编号。

## Non-Goals

- 不修改任何产品代码；不重开 D1-D11 已有建议的裁定。
- 不引入任何 Spring 依赖或移植代码。
- 不覆盖 Spring Cloud Stream / Reactor Kafka 等外延框架（超出对照基线）。

## Scope

### In Scope

- `ai-dev/analysis/2026-10/nop-message-spring-comparison.md`（新建）。
- `ai-dev/logs/2026/10-04.md`（追加）。
- roadmap Work Item Status 块 WI5 状态行（todo→planned→done）。

### Out Of Scope

- 全部代码、测试、构建脚本。
- roadmap 除 WI5 状态行外的任何内容。

## Execution Plan

### Phase 1 - 对照分析成文

Status: completed
Targets: `ai-dev/analysis/2026-10/nop-message-spring-comparison.md`

- Item Types: `Proof`

- [x] 按六维度写出对照（Spring 做法 / nop 现状 / 采纳裁定与映射），Spring 机制描述与公开文档口径一致
- [x] 三项不采纳（AckMode / RetryTopic / CommonErrorHandler）各立小节：Spring 机制是什么、为什么不适配 nop 的 SPI 公分母策略、被否决的替代方案、若强行导入的后果
- [x] 每维度结论与 roadmap D 裁定或 WI 编号交叉引用（D5/D6/D11/WI16/WI19/WI21/WI30/WI33）
- [x] 文档结尾给「防重新导入清单」：后续 agent 提案中出现这些机制名时的处理指引

Exit Criteria:

- [x] 六维度各有一节完整对照，无缺维度
- [x] 三项不采纳显式成节且理由指向具体 SPI 结构事实（非泛泛「不适合」）
- [x] nop 侧现状描述抽查 3 处与 WI1/WI2 产出一致
- [x] No owner-doc update required（analysis 交付物；owner doc 属 WI34）
- [x] `ai-dev/logs/2026/10-04.md` 追加条目
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 不新增断链

## Closure Gates

- [x] 文档已产出、六维完整、三项不采纳成节
- [x] nop 侧事实独立 audit 抽查通过
- [x] 无产品代码变更（git 仅含本 plan 声明路径及本 plan 文件）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/03-wi5-spring-comparison.md --strict` 退出码 0
- [x] 纯文档计划：构建验证不适用

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- Spring 侧机制若发现与本次描述有出入的新事实 → 附录登记，不阻塞本 plan。

## Closure

Status Note: WI5 为纯 Proof 项，交付物（Spring 对照分析）已产出并经独立子代理 closure audit：六维完整、三项不采纳成节且理由均落 SPI 结构事实、F-20/F-52/F-46 三处抽查与 WI1 基线及 live 代码一致、Spring 机制描述无误、防重新导入清单 10 个承载 WI 均实有且映射正确。audit 仅 1 Minor（WI5 文件被并行会话的 fix(orm) 提交捎带，提交卫生问题，内容无损）。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_527d4e8e-aff2-4b69-9115-f00a816781f0（fresh session）
- Evidence:
  - 六维完整 PASS：ack/错误处理/事务/容器生命周期/可观测性/测试支持各一节三段式
  - 三项不采纳 PASS：各节理由引用 SPI 结构事实 + D5/D6 编号 + 被否决方案 + 强行导入后果
  - nop 事实抽查 PASS：F-20（IMessageConsumer.java:41-42 空签名）、F-52（PulsarConsumeTask 每消息建事务）、F-46（TestKafkaConsumeTask.java:154-168）与 WI1 基线及 live 一致
  - Spring 机制描述 PASS：AckMode 值集 / CommonErrorHandler 2.8 职责 / @RetryableTopic topic 跳跃 / KafkaTransactionManager exactly-once 组合均与公开机制一致
  - 防重新导入清单 PASS：6 名词 → 10 个承载 WI 全部为 roadmap 实有项且映射正确
  - 无产品代码变更 PASS（按 mission 范围口径：工作区其他脏文件属并行会话任务）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0（本 mission 文件 0 断链）；`check-plan-checklist.mjs --strict` 退出 0
  - Deferred 分类检查：无 deferred
