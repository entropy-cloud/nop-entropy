# 19 WI19 线程模型与有界关闭

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI19，deps M2✓；对应 MSG-F-24 与 MSG-F-54）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（本计划新增 3.8 节）、`io.nop.commons.service.LifeCycleSupport`、`io.nop.commons.concurrent.thread.NamedThreadFactory`
> Draft Review: 两轮独立子代理对抗审查通过（Round 1：agent_06593f48，4 Major 已修；Round 2：agent_79d77acf，结论可直接执行）

## Purpose

消除两 broker 后端的线程模型缺陷（F-24：Pulsar 消费线程非 daemon 且未命名、Kafka fan-out 线程重名）与 Pulsar 停止不可达缺陷（F-54：无消息时 `stop()` 只翻转标志，消费线程阻塞在无超时 `receive()` 上无法退出），并把两后端生命周期统一到平台 `LifeCycleSupport` 状态机上。完成判定（roadmap 原文）：消费线程统一 daemon 且命名且 fan-out 可区分，且 `stop` 在无新消息时仍能有界退出；关闭路径有界不依赖 `close` 隐式解阻；两后端统一采用 `LifeCycleSupport`；新增关闭时序测试。

## Current Baseline

- **F-24 Kafka 侧**：`KafkaMessageService.doSubscribe`（`:349-353`）用内联 ThreadFactory 创建 daemon 线程，名 `kafka-consume-<groupId>`；`subscribe` concurrency=N 时 N 个 doSubscribe 共用同一 groupId → N 条线程重名，无法区分。
- **F-24 Pulsar 侧**：`PulsarMessageService.doSubscribe`（`:294`）`Executors.newSingleThreadExecutor()` 无 ThreadFactory → 线程非 daemon、默认池命名 `pool-N-thread-1`。
- **F-54**：`PulsarConsumeTask.consume()`（`:181`）用无超时 `receive()`；`stop()`（`:100-102`）只翻转 `active`。当前退出依赖 cancel 路径 `consumer.close()` 使 `receive()` 抛 AlreadyClosedException——即「close 隐式解阻」。`batchConsume()`（`:109`）的无超时 `batchReceive()` 同理（Pulsar 2.8 API 无 `batchReceive(timeout)` 重载，`javap` 实测仅有 `batchReceive()` / `batchReceiveAsync()`）。
- **Kafka 侧已有界**：`poll(Duration.ofMillis(pollTimeoutMs))` 有界 + `stop()` 调 `consumer.wakeup()`（`KafkaConsumeTask.java:208-211`），不依赖 close 解阻；本计划不改 Kafka 消费循环。
- **生命周期现状**：两 service 是普通对象，`init()`/`destroy()` 由 beans.xml `init-method="init"` `destroy-method="destroy"` 驱动；平台先例 `SysDaoMessageService extends LifeCycleSupport`（doStart/doStop，bean 侧 @PostConstruct 生效于 `DefaultBeanClassIntrospection.getInitMethod`）。`LifeCycleSupport` 的 start/stop 有 CAS 守卫，天然幂等。
- **依赖可达**：`nop-message-kafka`/`-pulsar` → `nop-core` → `nop-commons`（compile 传递，`mvnw dependency:tree` 实测）→ `LifeCycleSupport` 与 `NamedThreadFactory`（`<prefix>-<poolNumber>-<threadNumber>` 命名 + daemon 开关）无需 pom 变更。
- **测试影响面**：`TestPulsarConsumeTask` 有 5 处 mock `receive()` 无参版本（`:201`、`:293`、`:358`、`:413`、`:444`）——`consume()` 改为 `receive(timeout, unit)` 后这些 stub 必须同步迁移，否则测试静默失效（Mockito 对未 stub 的 `receive(int,TimeUnit)` 默认返回 null，消费循环空转而非报错；其中 `:293`/`:358`/`:413` 会响亮失败）。**另有第 6 处**：`:109` 的 `when(consumer.batchReceive()).thenReturn(messages, null)` + `:135` 直调 `task.batchConsume()`——retention 模式改走 `batchReceiveAsync()` 后该 stub 必然失效（未 stub 默认返回 null → `pendingBatch.get()` NPE）。Pulsar 2.8 的 `receive(int, TimeUnit)` 首参是 **int**（javap 实测），mock 匹配器必须用 `anyInt()` 而非 `anyLong()`。
- **边界（不在本计划）**：Pulsar destroy 顺序（F-27）、cancel/Cancellable 原子幂等（F-25）、跨线程 close/poll 竞态（F-26）、destroy 后 getProducer NPE 与重入（F-30）→ WI20。

## Goals

- **F-24 线程模型**：两后端消费线程统一改用 `NamedThreadFactory(prefix, true)`（daemon）。Kafka：每个 doSubscribe 一个 factory，prefix `nop-msg-kafka-consume-<groupId>`；Pulsar：prefix `nop-msg-pulsar-consume-<identity>`（identity = subscribeName，缺失时 fallback topic 名——subscribeName 缺失本身的缺陷归 WI29/F-29）。`NamedThreadFactory` 自带的 `-<poolNumber>-<threadNumber>` 后缀保证 fan-out 与多订阅均不重名。
- **F-54 Pulsar 有界等待**：
  - 单条路径：`consume()` 改 `receive((int) RECEIVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)`，超时返回 null → 循环重查 `active`。常量 `DEFAULT_RECEIVE_TIMEOUT_MS = 1000`（对齐 Kafka `DEFAULT_POLL_TIMEOUT_MS`；不新增配置字段，避免「声明了但从不读」）。
  - 批量路径（2.8 无超时重载）：retention 模式——字段持有 `pendingBatch`（`batchReceiveAsync()` 的 future），循环 `pendingBatch.get(RECEIVE_TIMEOUT_MS)`；`TimeoutException` 时若 `!active` 则退出、否则**继续等同一个 future**（不重复发起新请求，杜绝 pending future 无界累积）；取到消息后置空 `pendingBatch`。**future 异常完成（`ExecutionException`，如 close 后 AlreadyClosed、broker 瞬断）必须清空 `pendingBatch` 并上抛**，交 runTask 的退避路径接管——禁止保留已异常完成的 future（否则永久错误循环、消息流停摆）。退出时若有未完成的 pendingBatch，消息由 Pulsar 的 close/重连未确认重投语义兜底（生产 stop 仅发生于 cancel 路径，其后必跟 `consumer.close()`）——javadoc 显式声明该依据。
  - `stop()` javadoc 声明：有界退出上界为 RECEIVE_TIMEOUT_MS + 单批处理耗时，不依赖 `consumer.close()`。
- **LifeCycleSupport 统一**：两 service `extends LifeCycleSupport`；现 `init()` 体迁 `doStart()`、`destroy()` 体迁 `doStop()`；公开 `init()`/`destroy()` 保留为一行委托（`start()`/`stop()`），beans.xml 不动（显式 init-method 优先于 @PostConstruct 回退，CAS 守卫使双调用无害）。Kafka `initialized` 标志与 `getProducer` 校验保持现状（F-30 重入语义归 WI20，本计划不改行为）；Pulsar doStop 保持「producer 先于订阅」现状顺序并加注释指向 WI20（顺序修复是 WI20 的 exit criterion，本计划不抢）。**受检异常裁定**：`PulsarMessageService.init()` 现声明 `throws PulsarClientException`——`LifeCycleSupport.doStart()` 无 throws，迁移后 `PulsarClientException` 由 `NopException.adapt` 包装（公开异常类型变化：broker 不可达时 init 抛 NopWrapException 而非 PulsarClientException；grep 证实全仓无调用方 catch 该受检类型、无测试断言之），记入 design 3.8；`init()` 签名去掉 throws。
- **测试可注入**：Pulsar 新增 `createClient()` protected 工厂 seam（对齐 Kafka `createConsumer` 先例），生命周期测试用子类注入 mock client，不依赖真实 broker。
- **关闭时序测试**（见 Exit Criteria 逐条）。

## Non-Goals

- 不修 F-25/F-26/F-27/F-30（WI20）；不做 DLQ/重试（WI21/WI22）；不动 `batchReceiveCount` 接线（WI25）。
- 不改 Kafka 消费循环与 wakeup 机制（已有界）。
- 不引入新的可配置项（接收超时用常量；可配置化属后续优化候选，无实证需求）。
- 不改 `LocalMessageService` / `SysDaoMessageService` 等非 broker 后端线程模型（roadmap WI19 限定两 broker 后端）。

## Scope

### In Scope

- `nop-message-kafka`：doSubscribe 线程工厂换 `NamedThreadFactory`；service 继承 LifeCycleSupport。
- `nop-message-pulsar`：同上 + `PulsarConsumeTask` 单条/批量两条路径有界化 + `createClient()` seam + 既有 5 处 `receive()` stub 与 1 处 `batchReceive()` stub（`TestPulsarConsumeTask:109`）迁移。
- `ai-dev/design/nop-message/backend-semantics-design.md`：新增 3.8 节（线程模型与有界关闭契约）。
- 测试、`ai-dev/logs/`、roadmap WI19 状态行。

### Out Of Scope

- WI20-WI23、WI29（subscribeName 缺失的随机订阅名缺陷）；`nop-message-core` / 其他后端。

## Execution Plan

### Phase 1 - 线程模型、有界停止与生命周期统一

Status: completed
Targets: `nop-message/nop-message-kafka`、`nop-message/nop-message-pulsar`、`ai-dev/design/nop-message/backend-semantics-design.md`

- Item Types: `Fix`（缺陷修复 F-24/F-54 与 LifeCycleSupport 统一为主）；新增测试项 ①-⑤ 归类 `Proof`

- [x] Pulsar `PulsarConsumeTask`：单条 `receive(timeout)` 有界化 + 批量 retention 模式（TimeoutException 分支 + ExecutionException 清空上抛分支）+ `stop()` javadoc 有界声明
- [x] Pulsar `PulsarMessageService`：`NamedThreadFactory` daemon 线程 + `createClient()` seam + extends LifeCycleSupport（doStart/doStop + init/destroy 委托，PulsarClientException 改 NopException.adapt、init 去 throws）
- [x] Kafka `KafkaMessageService`：`NamedThreadFactory`（fan-out 可区分名）+ extends LifeCycleSupport（doStart/doStop + init/destroy 委托）
- [x] `TestPulsarConsumeTask` 既有 stub 迁移：5 处 `receive()` 无参 stub 改 `receive(anyInt(), any())` 形态（Pulsar 2.8 首参 int）；`:109` 的 `batchReceive()` stub 改 `batchReceiveAsync()` 形态（返回已完成的 future），`:135` 直调 `batchConsume()` 断言保持
- [x] 新增测试（Proof）：① 线程命名/daemon（两后端 factory 单测 + Kafka fan-out 两订阅线程名互异；断言用 `name.startsWith("nop-msg-…-consume-<id>-")` 前缀 + 互异 + isDaemon——poolNumber 是 JVM 级静态计数器，禁止断言完整线程名）；② Pulsar 单条路径 stop 有界退出（mock receive 真实睡满请求的超时后返回 null，stop 后任务在 2×超时内终止，且 `verify(consumer, never()).close()`）；③ Pulsar 批量路径 stop 有界退出（`batchReceiveAsync()` 返回永不完成的 future，同上断言）；④ 生命周期状态机（Kafka：init→isActive、destroy→非 active、destroy 后可重新 init；Pulsar：经 createClient seam 注入 mock client 后同断言 + 未 init 时 destroy 不抛——ServiceStatus CREATED 不允许 stop，beginStop 返回 false）；⑤ 既有全量回归通过
- [x] design 3.8 节落档（线程命名规则、有界上界、retention 模式含异常完成分支与 close 兜底依据、LifeCycleSupport 委托形态、PulsarClientException 异常类型变化）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-message/nop-message-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-debezium -am -T 1C` 退出 0 全绿（BUILD SUCCESS，kafka+pulsar+core+debezium 及上游全过）
- [x] **新功能必有测试**：①②③④ 全部存在且通过——②③ 是 F-54 的直接证明（退出不依赖 close，测试显式 `verify(consumer, never()).close()`）；① 是 F-24 的直接证明（daemon 断言 + fan-out 重名消除断言）
- [x] **无静默跳过**：批量路径 `TimeoutException` 分支是显式控制流（!active 退出 / active 继续等同一 future），`ExecutionException` 分支清空 `pendingBatch` 并上抛，不吞异常不留空分支；单条路径 null 返回走既有 `if (message == null) return` 显式分支
- [x] **接线验证**：两 service 的 `init()`/`destroy()` 确实经由 `LifeCycleSupport.start()/stop()` 状态机（④ 的 isActive 断言即运行时证据，非仅类型存在）
- [x] F-24 验收：全仓 grep 无「newSingleThreadExecutor() 无 ThreadFactory」残留于两后端；Kafka fan-out 线程名含可区分后缀（①测试证明）
- [x] F-54 验收：`PulsarConsumeTask` 中不再存在无参 `receive()` / 无界 `batchReceive()` 调用（grep 证明）；②③ 测试证明 stop 有界且不依赖 close
- [x] Owner-doc：`backend-semantics-design.md` 3.8 节已落档
- [x] `ai-dev/logs/2026/` 当日条目（`10-05.md`）已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0（2026-10-05 实测存量断链已清零；若执行期间新增断链须修复）

## Closure Gates

- [x] MSG-F-24 与 MSG-F-54 已修复且有测试证明（①②③）
- [x] 关闭路径有界性（本计划可证范围）：消费任务循环 + `awaitTermination` 上界有界（②③ 证明不依赖 close 解阻）；consumer/client close 的内部耗时上界归 WI20（F-26 竞态与 F-27 顺序的同域）
- [x] 独立子 agent closure audit 已完成并记录证据（agent_bc07dcd2，结论 PASS，见 Closure Audit Evidence）
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/19-wi19-thread-model-bounded-shutdown.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- 接收超时可配置化（optimization candidate：当前常量 1000ms 对齐 Kafka poll 默认值，无实证需求；若 WI33 gated E2E 暴露时序问题再议）。

## Closure

Status Note: WI19 收口：F-24（两后端消费线程统一 NamedThreadFactory daemon 命名、fan-out 可区分）与 F-54（Pulsar 单条 receive 有界 + 批量 retention 模式 + stop 不依赖 close 解阻）修复并有测试直接证明；两 service 统一 LifeCycleSupport 状态机（init/destroy 委托、isActive 运行时可观测、PulsarClientException 契约变化已裁定并落 design 3.8）。无 SPI 签名变更、beans.xml 零 diff、doStop 顺序未动（WI20 边界保持）。独立 closure audit 全项 PASS。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_bc07dcd2-7f18-4da1-a2e8-bcbfb51fcff5（fresh session，自行重读代码/测试并重跑全部验证命令）
- Evidence:
  - EC1 PASS：四模块测试全绿——kafka 59/0F + pulsar 37/0F（surefire 实测）；core/debezium 补跑 BUILD SUCCESS（WI19 未触碰，git status 证实）
  - EC2 PASS：①consumeThreadFactoryNamesAreDaemonAndFanOutDistinct + doSubscribeRunsOnNamedDaemonThreadsAndCancelTerminatesThem（concurrency=2 两条互异活线程 + cancel 后归零）+ doSubscribeRunsOnNamedDaemonConsumeThread；②stopExitsBoundedlyWithoutCloseWhenNoMessageArrives（never close）；③stopExitsBoundedlyInBatchModeAndRetainsSinglePendingRequest（times(1) 证明 retention）
  - EC3/EC4 PASS：receiveBatchBounded 三分支显式（Timeout 返 null / Execution 清空上抛 / 中断恢复标志）；init/destroy 一行委托、两 service 无 isActive 覆盖（断言穿透基类状态机）
  - EC5/EC6 PASS：grep 0 残留（无参 receive、无名 newSingleThreadExecutor）
  - EC7/EC8/EC9 PASS：design 3.8 与实现逐条一致；logs/2026/10-05.md 有条目；check-doc-links --strict 0
  - Gates：mvnw test 0；scan-hollow 0；check-silent-wrong-result --module 四消息模块全 0；check-plan-checklist --strict 0
  - Anti-Hollow：factory 线程→runTask（Kafka :368-371 → start :136-138；Pulsar :341-345 → :93-96）+ init→start→doStart（基类 :75-79）+ isActive→基类原子 status——三条链均为真实接线，且有 getAllStackTraces 运行时证据
  - 边界核对：beans.xml 零 diff；Pulsar doStop 顺序未改（WI20 注释锚点）；Kafka initialized 保留
  - Deferred 分类检查：无 in-scope live defect 被降级（唯一 Follow-up 为 optimization candidate）

Follow-up:

- 接收超时可配置化（optimization candidate，归 WI33 gated E2E 后按需评估）
- 无 remaining plan-owned work
