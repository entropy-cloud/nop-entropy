# 20 WI20 destroy 顺序与并发幂等

> Plan Status: completed
> Draft Review: 两轮独立子代理对抗审查通过（R1：agent_fa5cd818，4 Major 已修——状态重断言方案/F-25 证明链改写/下游回归命令/残余窗口裁定；R2：agent_09ce547b，第四模块归因修正后判定可执行）
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI20，deps WI19✓；对应 MSG-F-25/F-26/F-27/F-30）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（3.8 节，本计划增补 3.8.1）、`io.nop.commons.lang.impl.Cancellable`（nop-commons）

## Purpose

修复两后端关闭路径的四个并发/顺序缺陷：F-27（Pulsar destroy 顺序错误——关闭期在途发送打已关闭 producer）、F-25（`cancel` 与 `Cancellable.cancel` 均为非原子 check-then-act，并发 cancel 双跑清理体）、F-26（从调用线程直接 `consumer.close()` 与消费线程 poll 竞态，pause/resume 同）、F-30（Pulsar destroy 后 `getProducer` NPE、init/destroy 不可重入）。完成判定（roadmap 原文）：两后端 destroy 顺序统一为订阅先于 producer；`cancel` 与 `Cancellable` 共同改为原子幂等；消除跨线程 `consumer.close` 与 `poll` 竞态；新增并发 cancel 与关闭期在途发送测试。

## Current Baseline

- **F-27**：`PulsarMessageService.doStop`（WI19 迁移后 `:143-165` 附近）先关 producers 再 cancel subscriptions；Kafka 顺序正确（订阅先于 producer）。
- **F-25**：`KafkaMessageSubscription.cancel()`（`KafkaMessageService.java:461-483`）`if (cancelled) return; cancelled = true;` 非原子——**全清理体（stop/shutdown/close）可双跑**。`Cancellable.cancel(String)`（`Cancellable.java:40-46`）的置位非原子，但 `tryRunCallbacks`（`:102-119`）已在锁内一次性消费回调列表——**Pulsar 订阅的 doOnCancel 不会双跑（既有保证）**，真实缺陷是 `cancelReason` 双写竞争（并发 cancel 时回调拿到的 reason 归因不确定）+ 冗余重复消费；修复为防御性原子收口。
- **F-26**：两后端 cancel 路径都从调用线程直接 `consumer.close()`，此刻消费线程可能正阻塞在 poll/receive/处理批次——Kafka `KafkaConsumer` 非线程安全（仅 `wakeup()` 文档化线程安全）；Pulsar 客户端整体线程安全但 close 与在途处理的语义交错仍是缺陷形态。Kafka `suspend()/resume()` 也从调用线程直调 `consumer.pause/resume`（同族竞态）。
- **F-30**：Pulsar `getProducer`（`:182-191`）在 destroy 后 `client == null` 仍走 `computeIfAbsent → buildProducer → client.newProducer` → NPE；`init/destroy` 重入已由 WI19 的 LifeCycleSupport 解决（`TestPulsarServiceLifecycle.initDestroyRunOnLifecycleStateMachineAndSupportRestart` 已绿），剩 destroy 后发送的 NPE 面与 `ERR_PULSAR_SERVICE_NOT_ACTIVE` 缺位。Kafka 侧 `getProducer` 已有 `initialized` 校验（类型化错误）。
- **WI19 后的新事实**：任务停止已有界（`stop()` 翻标志 + 有界等待轮）；`executor.awaitTermination` 已在 cancel 路径存在但先 close 后 await——close 时机在 await 之前是竞态根源。`PulsarConsumeTask.stop()` 语义（仅翻标志）被 WI19 测试 `verify(consumer, never()).close()` 钉定——本计划把 close 移到「任务终止之后」，该钉定测试保持不动且继续有效。
- **Cancellable 回归面**：`nop-commons` 的 `Cancellable` 被 15+ 主源码类使用（ExecutionContext、BeanContainer、RPC cluster client 等）——改为原子 check-and-set 是单线程行为保持的纯并发修正，回归面为 nop-commons 自身测试 + 本计划的并发测试。

## Goals

- **F-27**：`PulsarMessageService.doStop` 重排为 **subscriptions → producers → client**（对齐 Kafka），注释从「WI20 将重排」改为最终裁定表述。
- **F-25**：
  - `Cancellable.cancel(String)`：check-and-set 收进 `synchronized(this)`（与 `appendOnCancel`/`tryRunCallbacks` 既有锁一致），双线程并发 cancel 仅一个执行置位——`cancelReason` 归因确定（单一 winner）；回调列表由 `tryRunCallbacks` 的既有锁语义保证只消费一次（该保证本已成立，本修复使其与置位原子性一致）。回调仍在锁外执行（结构不变，无新死锁面）。
  - `KafkaMessageSubscription.cancelled` 改 `AtomicBoolean`，`cancel()` 用 `compareAndSet` 幂等守卫——**这是真正双跑清理体的缺陷面**；`isCancelled()` 读 `get()`。Pulsar 订阅经 Cancellable 修复获得确定性 reason 归因。
- **F-26（close-after-termination 模式）**：两后端订阅 cancel 路径统一为 **stop → shutdown → awaitTermination(5s)（超时则 shutdownNow + 再等 1s）→ 然后**才 `consumer.close()`——close 发生在消费线程终止（或被强制中断退出）之后，常规路径结构性消除 close/poll 并发。**残余窗口裁定**：若消费线程卡死在阻塞式 consumer 调用（慢 poll/commit，如 broker 不可达的长超时）超过 6s，二次等待超时后仍执行兜底 close 并打 WARN（best-effort）——该尾窗从 WI20 前的「常态」收窄为「病态尾窗」，3.8.1 显式声明。Pulsar/`KafkaConsumeTask` 的 `stop()` 语义不变（仅翻标志），任务自身永不 close consumer（WI19 钉定测试不破坏）。注意：5s await 沿用现状，1s 二次等待为本计划新增。
  - **F-26 pause/resume 同族（状态重断言方案）**：`KafkaMessageSubscription.suspended` 语义改为消费线程上的**每轮状态重断言**——`KafkaConsumeTask` 持 `volatile boolean suspended`（期望态）+ 消费线程私有 `suspendedApplied`（已施态）：runTask 每轮循环头 `suspended != suspendedApplied` 时对全量 `assignment()` 执行 pause/resume 并更新已施态；`onPartitionsAssigned` 中若挂起态生效则对新分配分区重新 pause（覆盖首分配前挂起与 rebalance 后挂起两种序列）。`suspend()/resume()` 仅置位 volatile——与 poll 串行化到消费线程，无单槽令牌丢失问题（suspend→resume 紧邻也正确）。Pulsar 客户端 API 本身线程安全（官方契约），`pause()/resume()` 直调保持不动。语义注记：flag 同步置位，实际 pause/resume 在下一轮循环前（或分区分配时）生效（at-least-once 下多投一轮可容忍）；任务已停止时不施状态（flag 仍可置位，无害）。
- **F-30**：`PulsarMessageService.getProducer` 在 `client == null`（未 init 或已 destroy）时抛新增类型化错误 `PulsarErrors.ERR_PULSAR_SERVICE_NOT_ACTIVE`（英文描述；前缀沿用现风格，WI26 统一迁移；destroy 中途 producers 已 clear 而 client 未置空的窄窗内会抛 PulsarClientException 适配错误——3.8.1 注明）。重入语义由 LifeCycleSupport 承担（已有测试），本计划补 destroy-then-send 回归。
- **测试**（见 Exit Criteria ①-⑥）。

## Non-Goals

- 不改 `PulsarConsumeTask`/`KafkaConsumeTask` 既有 poll/commit/停止语义（WI19 已收口并有钉定测试；suspend 施态为本计划新增机制，见 Goals）。
- 关闭等待常量：5s 沿用现状；1s 二次等待为本计划新增的兜底组成，不做可配置化（无实证需求）。
- 不触碰 `IMessageSubscription` SPI 签名；不改 beans.xml。
- Local/SysDao 等非 broker 后端不在本计划。

## Scope

### In Scope

- `nop-kernel/nop-commons`：`Cancellable.cancel` 原子化 + 新增 `TestCancellable`（含并发测试）。
- `nop-message-kafka`：`KafkaMessageSubscription`（AtomicBoolean cancel、close-after-termination、suspend/resume 置位）、`KafkaConsumeTask`（suspended 期望态 + 循环头重断言 + rebalance 重 pause）、destroy 顺序 InOrder 钉定测试。
- `nop-message-pulsar`：`doStop` 重排（F-27）、`getProducer` 未激活守卫 + 新错误码、destroy 顺序/并发 cancel/destroy 后发送测试。
- `TestKafkaSubscription` 既有 suspend/resume 断言迁移（同步 pause 断言改为置位 + 施态断言）。
- design 3.8.1 小节、日志、roadmap WI20 状态行。

### Out Of Scope

- WI21-WI23；SPI 签名变更；`MessageRpcClient`/`GraphQL` 侧（未触及 onMessage 协议与 ack topic，无必答回归方——本计划不改返回值协议与 getAckTopic 派生）。

## Execution Plan

### Phase 1 - 关闭顺序、幂等取消与竞态消除

Status: completed
Targets: `nop-kernel/nop-commons`、`nop-message/nop-message-kafka`、`nop-message/nop-message-pulsar`

- Item Types: `Fix`（F-25/F-26/F-27/F-30 均为已确认缺陷）；新增测试归类 `Proof`

- [x] `Cancellable.cancel` check-and-set 原子化（nop-commons）
- [x] Kafka：`KafkaMessageSubscription` AtomicBoolean + close-after-termination + suspend/resume 状态置位；`KafkaConsumeTask` 增 `volatile suspended` 期望态 + `suspendedApplied` 循环头重断言 + `onPartitionsAssigned` 挂起重 pause
- [x] Pulsar：`doStop` 重排订阅先于 producer；`buildProducer` 未激活守卫 + `ERR_PULSAR_SERVICE_NOT_ACTIVE`；`doOnCancel` 改 close-after-termination
- [x] 测试① `TestCancellable`：单次 cancel 单回调、重复 cancel 幂等、4 线程并发 cancel 回调恰一次且 `getCancelReason()` 为提交 reason 之一（归因确定）
- [x] 测试② Kafka 并发 cancel：4 线程并发 cancel 同一订阅 → `task.stop` 与 `consumer.close` 各恰一次；close-after-termination 顺序断言（真实 KafkaConsumeTask + poll 阻塞在 latch，poll answer 捕获消费线程引用；close answer 断言该线程已 TERMINATED，禁止时序脆弱断言）
- [x] 测试③ Kafka destroy 顺序 InOrder 钉定（consumer.close 先于 producer.close）；suspend/resume 状态重断言单测（置位后下一轮循环头对 assignment 施 pause；resume 后施 resume；挂起态下 rebalance 新分区被 pause；pause/resume 在消费线程执行）
- [x] 测试④ Pulsar 并发 cancel：4 线程并发 cancel → consumer.close 恰一次（新测试用 WI19 的有界 receive stub + `setReceiveTimeoutMs`，避免 5s await 拖慢）
- [x] 测试⑤ Pulsar destroy 顺序 InOrder 钉定（consumer.close 先于 producer.close）+ 关闭期在途发送：sendAsync future 未完成时 destroy，producer.close 晚于订阅取消，随后 complete future 断言发送成功
- [x] 测试⑥ Pulsar destroy 后 sendAsync → `NopException(ERR_PULSAR_SERVICE_NOT_ACTIVE)` 而非 NPE
- [x] design 3.8.1 落档（close-after-termination 契约与残余窗口裁定、cancel 幂等语义与 reason 归因、suspend/resume 状态重断言语义含 rebalance 面、Pulsar destroy 顺序最终裁定、getProducer 未激活守卫与 destroy 中途窄窗）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-kernel/nop-commons,nop-message/nop-message-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-debezium -am -T 1C` 退出 0 全绿（BUILD SUCCESS：nop-commons 355/0F、kafka 64/0F、pulsar 40/0F、core/debezium 绿，_tmp/wi20-regression.log）
- [x] **新功能必有测试**：①-⑥ 全部存在且通过；② 是 Kafka F-25 双跑清理体的直接证明；① 是 Cancellable 原子化（reason 归因确定 + 单次消费）的回归证明；④ 是 Pulsar 面并发 cancel 的回归证明；②③⑤ 直接证明 close-after-termination 与 destroy 顺序；⑥ 是 F-30 的直接证明
- [x] **无静默跳过**：cancel 超时分支显式 shutdownNow + 再等待 + 兜底 close（带 WARN 日志，残余窗口在 3.8.1 声明），不留吞异常空分支；suspend/resume 置位后未施态（任务已停）属显式语义（javadoc 声明），非静默
- [x] **接线验证**：close-after-termination 由 ②③ 的事件顺序断言运行时证明（close answer 断言消费线程 State==TERMINATED）；Cancellable 原子性由 ① 并发测试证明；suspend 状态重断言由 ③ 的循环头 pause→resume→rebalance 重放单测证明
- [x] F-25/F-26/F-27/F-30 验收：两订阅 + Cancellable 均原子守卫；Pulsar doStop 顺序与 Kafka 一致；`buildProducer` 空客户端走 `ERR_PULSAR_SERVICE_NOT_ACTIVE`（守卫位置从 getProducer 下沉至 buildProducer——getProducer 位置破坏测试 override 注入形态，实测修正，design 3.8.1 已记）
- [x] WI19 钉定测试不回退：`TestPulsarConsumeTask` 的 stop 有界/never close 断言原样保留且通过（20/20 绿）
- [x] Owner-doc：design 3.8.1 已落档
- [x] `ai-dev/logs/2026/` 当日条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0

## Closure Gates

- [x] MSG-F-25/F-26/F-27/F-30 已修复且有测试证明（①-⑥）
- [x] Cancellable 变更回归面收口：`./mvnw test -pl nop-kernel/nop-core,nop-core-framework/nop-ioc,nop-network/nop-rpc/nop-rpc-core,nop-task/nop-task-core -am -T 1C` 退出 0（BUILD SUCCESS，_tmp/wi20-downstream.log）
- [x] 独立子 agent closure audit 已完成并记录证据（agent_46b4eaa4，结论 PASS）
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --module <m>` 对 nop-message-core / nop-message-kafka / nop-message-pulsar / nop-message-debezium 四模块逐一退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/20-wi20-destroy-order-cancel-idempotency.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 关闭超时（5s/1s）可配置化（optimization candidate：常量沿用现状，无实证需求）。

## Closure

Status Note: WI20 收口：F-27（Pulsar doStop 重排 subscriptions→producers→client）、F-25（Kafka 订阅 AtomicBoolean + Cancellable synchronized 置位，并发 cancel 单一 winner、reason 归因确定）、F-26（close-after-termination 常规路径结构性串行，残余病态尾窗 WARN+3.8.1 声明；Kafka suspend/resume 消费线程状态重断言含 rebalance 重放）、F-30（buildProducer 未激活守卫 + ERR_PULSAR_SERVICE_NOT_ACTIVE）全部落地并有并发/顺序/typed-reject 测试直接证明。Cancellable 下游四模块回归绿。独立 closure audit 全项 PASS。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_46b4eaa4-32c8-49de-bd3d-4799c2e635d8（fresh session，自行重读代码/测试并重跑全部验证命令）
- Evidence:
  - EC1-EC9 全 PASS：common 355/0F + kafka 64/0F + pulsar 40/0F（亲跑）；五模块 reactor BUILD SUCCESS（_tmp/wi20-regression.log）
  - 测试①-⑥ 全部在绿跑中出现：TestCancellable 4/4（含 4 线程并发归因）、Kafka 并发 cancel 恰一次 + close 时消费线程 TERMINATED、destroy InOrder、suspend 施态与 rebalance 重放、Pulsar 并发 cancel 恰一次 / destroy InOrder + 在途发送存活 / typed reject
  - EC6：TestPulsarConsumeTask 工作树零改动（git diff HEAD 空），WI19 钉定断言原样、20/20 绿
  - Gates：下游四模块 BUILD SUCCESS（_tmp/wi20-downstream.log）；scan-hollow 0 findings；四消息模块 check-silent-wrong-result 全 0；check-doc-links 0 errors（4 个存量 warnings 在无关旧 plan）；check-plan-checklist --strict 0
  - Anti-Hollow：subscribe→bindExecutor→start→runTask（KafkaConsumeTask:149）真实执行；两后端 close 仅存在于取消路径且严格 happens-after awaitTermination；Cancellable synchronized 覆盖置位三步（:44-49）；三个并发测试均为 CyclicBarrier 齐放真并发
  - Deferred 分类检查：无 in-scope live defect 被降级（唯一 Follow-up 为关闭超时可配置化 optimization candidate）

Follow-up:

- 关闭超时（5s/1s）可配置化（optimization candidate，无实证需求）
- 无 remaining plan-owned work
