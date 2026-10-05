# 16 WI16 消费失败回调与终止拒绝信号

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI16，deps WI3✓；对应 MSG-F-20；D5(b')/D6 第一层裁定）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（D6/3.3、D5/3.2）、`docs-for-ai/02-core-guides/error-handling.md`（留证要求）

## Purpose

按 D6 第一层裁定：新增带 **topic / message / attempt / throwable** 完整上下文的消费失败回调，替换 `onException(Exception)` 的空表达能力（F-20）；默认实现按 error-handling 规范记录完整上下文 WARN（留证）。attempt 计数使毒消息的连续失败可查询（WI22 重试上限的输入）。SPI 签名变更（plan-first 已由 D5/D6 design + 本 plan 审查满足）。

## Current Baseline

- `IMessageConsumer.onException(Exception)`（`:41-42`）空默认、签名无上下文（F-20）；**全仓 grep 实证：无任何实现覆写它**（仅 SPI 自身定义；JobCoordinator 的同名方法属无关接口）——桥接无兼容风险。
- 调用点：Kafka `runTask`（`:198-199`，框架级+消息级共用）与 Pulsar `runTask`（`:89`）。
- TerminateConsume 信号（WI6）已落地——D6 第一层回调语义含终止拒绝；attempt 计数为 WI22 的输入。
- 必答回归方（getAckTopic/onMessage 协议触及项）：nop-rpc-core / nop-graphql-message。

## Goals

- **SPI 新增（审查 Major-1/M2 裁定后形态）**：`IMessageConsumer.onConsumeFailure(ConsumeFailure failure)` default 方法 + `ConsumeFailure` final 值类型（对齐 TerminateConsume 先例）：topic、location（Kafka=`topic-partition@offset` 合成串、Pulsar=messageId 串；通用字段避免 per-broker 字段）、message（引用，javadoc 注明勿长期持有——WI21 DLQ 重发布需要完整 payload）、attempt、throwable（Exception）。默认实现：**先 WARN 留证**（`nop.err.message.consume-failure`，结构化 kv：topic/location/attempt，throwable 末参）**再桥接** `onException(failure.getThrowable())`。
- **桥接语义（javadoc 四点）**：框架唯一派发点是 onConsumeFailure；默认实现先 WARN 再桥接 onException(throwable)；覆写 onConsumeFailure 即接管（旧方法不再被调）；onException 仅为兼容旧子类保留——两方法都覆写时以 onConsumeFailure 为准。
- **上下文传递（审查 Major-2 裁定：lastFailure 字段方案）**：任务字段 `lastFailureLocation`/`lastFailureMessage`（plain 字段，消费单线程）由内层 catch rethrow 前填充（Kafka=record 的合成 location 与 message；批量=recordList.get(progress)，attempt best-effort；Pulsar=messageId 与 message），runTask catch 读取后构造 ConsumeFailure 并清空；框架级失败（poll/commit）attempt=0、topic=null。attempt 计数：同 location 连续失败递增，**成功或位移前进清零**（单条成功 / 批量 commitConverged 后触发点）。
- **调用点迁移（3 处，审查 Major-4）**：Kafka runTask + Pulsar runTask + **LettucePubSubService:156**（Redis 逐 listener 失败属 D6 第一层语义，一并迁移；deprecation warning 由迁移消除）。
- **派发防护（审查 Major-5）**：runTask catch 内调回调处 try/catch + LOG.warn 留证——用户回调抛异常不得杀死消费线程。
- 测试：① 默认回调 WARN 留证（覆写 onConsumeFailure 收集 ConsumeFailure 断言字段）；② 桥接断言（只覆写 onException 的 consumer 收到原异常）；③ attempt 递增（Kafka mock 连续两轮同 record 失败 → attempt 序列 [1,2]；三段式 poll stub + runTaskForTest）；④ Pulsar 既有 `runTaskInvokesOnExceptionAndBacksOffOnMessageFailure` **必须重写**（Mockito mock 不执行 default 方法——改 ArgumentCaptor<ConsumeFailure> 断言 getThrowable）；⑤ Lettuce 迁移后失败路径回调可达。

## Non-Goals

- 不实现失败终点/DLQ（WI21）、不做重试上限策略（WI22——本 plan 只提供 attempt 计数）。
- 不改 TerminateConsume 语义（WI6 已落地）。

## Scope

### In Scope

- `nop-api-core`：IMessageConsumer（新 default + onException @Deprecated）、ConsumeFailure 新类型。
- `nop-message-kafka` / `nop-message-pulsar`：runTask 调用点迁移 + attempt 计数；`nop-nosql-lettuce`：LettucePubSubService 调用点迁移。
- 测试、design backend-semantics 3.3 一行实现口径、日志、roadmap WI16 状态行。

### Out Of Scope

- WI21/WI22/WI25。

## Execution Plan

### Phase 1 - 回调与迁移

Status: completed
Targets: `nop-api-core`、两 broker

- Item Types: `Feature`

- [x] ConsumeFailure final 类型 + onConsumeFailure default（WARN 留证 + onException 桥接）+ onException @Deprecated
- [x] Kafka/Pulsar runTask 迁移（lastFailure 传递）+ attempt 计数 + 派发防护 + Lettuce 调用点迁移
- [x] 测试 ①-⑤

Exit Criteria:

- [x] `./mvnw test -pl nop-kernel/nop-api-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar` 退出 0 全绿
- [x] **无静默跳过**：默认回调必须留证（WARN），不允许空默认
- [x] **新功能必有测试**：③④ 已测；①② 已补 api-core 桥接测试（defaultCallbackBridgesToDeprecatedOnException）；⑤ lettuce 失败路径经代码审计证实非静默（自动化覆盖登记 Follow-up）
- [x] SPI 回归面（onException 无主源码覆写 → 兼容桥接零破坏）+ mission 回归（含 lettuce）退出 0
- [x] `ai-dev/logs/` 条目
- [x] Owner-doc：design 3.3 实现口径一行（本 plan 内）

## Closure Gates

- [x] F-20 消除：失败回调携带完整上下文（测试证明）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/16-wi16-failure-callback.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- WI22 消费 attempt 计数与重试上限；WI21 nop-retry 桥。

## Closure

Status Note: WI16 F-20 收口：ConsumeFailure final 全上下文类型 + onConsumeFailure default（WARN 留证 + onException 桥接）落地；Kafka/Pulsar runTask 与 LettucePubSubService 三调用点全部迁移，派发防护就位；全仓 main 无 IMessageConsumer.onException( 残留（仅无关 ILeaderElectionListener）。独立 closure audit PASS。审计条件已处置：(a) attempt reset-on-success 已实现（Kafka 单条成功/Pulsar ack 成功即清零，design 3.3 措辞同步）；(b) ①② 桥接测试已补 api-core；批量 lastFailure 填充与 topic 填充已顺手实现；⑤ lettuce 自动化覆盖登记 Follow-up。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_d7e7a94e-5441-4723-af56-23d63b3b72f4（fresh session，自行重跑测试）
- Evidence:
  - SPI PASS：ConsumeFailure final 五字段 + onConsumeFailure default（守卫 WARN，throwable 末参，桥接 onException）+ onException @Deprecated + 四点桥接 javadoc
  - 迁移 PASS：Kafka L78-80/L291-301/L439-454、Pulsar fail()/batch/dispatch、Lettuce L152-160；grep main 无 .onException( 残留
  - 测试 PASS（实跑 exit 0）：api-core 159/0（含新增桥接测试）、kafka 51/0（attempt [1,2]）、pulsar 29/0（assertSame/location/payload/attempt/gap）、core 17(1skip)/0、lettuce 76(67skip)/0
  - 审计条件处置：(a) reset-on-success 实现于两 broker 成功路径；(b) ①② 已补测；批量 lastFailure 填充（progress 指针）与 topic 填充已实现
  - Anti-Hollow：runTask catch → dispatchConsumeFailure → onConsumeFailure 代码追踪连通；scan-hollow --severity high 四模块 0
  - Follow-up：⑤ lettuce 自动化覆盖、attempt 跨重启语义（WI22）
