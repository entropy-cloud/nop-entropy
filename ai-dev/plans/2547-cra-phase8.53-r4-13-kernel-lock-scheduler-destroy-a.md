# 2547 CRA Phase 8.53 — R4-13 2.1 锁纪律/调度器/destroy 束 a（G27 前半，17 实施 + 1 already-landed）

> Plan Status: completed
> Last Reviewed: 2026-11-09
> Source: roadmap 工作项 8.53（`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` :224，Status=todo；Owner Doc=`docs-for-ai/04-reference/source-anchors.md`；`docs-for-ai/02-core-guides/ioc-and-config.md`；Deps=「batch-proposal.md §二 R4-13（18 项）；硬依赖 8.2/8.3/8.10（均已 done）」）；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 :128 R4-13 行 + §二.5 G27 行「36 | R4-13（18）+ R4-14（17）+ R4-32（C7-004）」；成员发现 = `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/`
> Related: plan 2468（2.1 审计）、plan 2504（R2-01 kernel 批，C1-010/C2-004 邻接先例）；设计文档 = `ai-dev/design/concurrency/kernel-lock-discipline-interrupt-contract-and-executor-destroy-semantics.md`
> **Protected Area 授权**：`nop-kernel/nop-commons` / `nop-core` / `nop-xlang` = AGENTS.md「框架核心引擎」= **plan-first**。授权载体 = 设计文档 + 独立 draft review + §五 回归面清单。**未过 review 不得改码。**

## Purpose

关闭 R4-13 的 nop-kernel P2 工单束。**经 draft review R1 勘正（E-2547-1）：原 18 成员中 C3-002 已在 plan 2496 修复落地（`646673a90e`），本批实际实施 17 项**：复合操作原子性（4）、中断契约米策略（1）、回调锁域（2）、destroy 语义（1）、提交/拒绝显式性（4）、声明/配置面（3，其中 C3-009 为 contract change）、单例槽位（1，9 项站点）、文件锁句柄会话（1）。

**Draft Review 记录**

- R1（fresh session，125 次工具调用，逐条 live 核实）：**REJECT**，7 Blocker + 11 Major + 10 Minor。全部随本版落盘（见下「Draft Review R1 修订清单」）。核心发现：C3-002 已修复（基线陈旧）、C2-007 锚点/传播面错、`computeIfAbsent` 返回值判定语义上不可行、C3-012 cancel-then-drain 逻辑不可能成立、载体表缺失、回归面漏 7 个模块。
- R2（fresh session，收敛轮）：**PASS-with-revisions**——R1 的实质裁定全部正确；剩余为设计 §三/§四 的**旧表述残留**（7 处文本）+ 1 个 B-5 机制缺口（「停止接收位」无承载）。全部已清空（见下「Draft Review R2 修订清单」）。**PA 授权齐备。**

### Draft Review R2 修订清单

| R2 项 | 修订 |
|---|---|
| B-3 残留（设计 §三 C2-007 行 / §四.2 / plan Goals / G6） | 统一为米策略（签名不变、零调用点改造、`LocalFileLock` 不在链内） |
| B-4 残留（设计 §四.1） | 删除「返回值身份判定」与 `LocalCache.computeIfAbsentAsync` 先例；锁定 `boolean[]`/`AtomicBoolean` + `Map<K,CompletableFuture<V>>` + join 解包 |
| M-1 残留（§三 C4-002 行） | 改为「init 前 null 守卫」，锚点订正 `:91-93 → :124`；`ScheduledExecutor.stats` fail-fast 标 already-landed |
| M-5/M-6 残留（§三 C1-009 行） | `ErrorMessageManager:114-116` 单列 this-escape；`VirtualFileSystem.registerInstance:31-34` |
| M-3 残留（§一.5） | `bindCancel(..., future.toCompletableFuture())` |
| **B-5 机制缺口** | 明确「停止接收位」= `AbstractBatchProcessService` 新增 `volatile boolean accepting`（生产方检查）；绕过直调 `getQueue().send()` 的外部生产方显式登记为不覆盖 |
| 计数/文本 | 标题、Item Types（Fix 14 + Proof 3 + 证据 1）、组 1 清单去 C3-002 |

### Draft Review R1 修订清单（逐条已闭环）

| R1 项 | 结论与修订 |
|---|---|
| **B-1** C3-002 已修复（基线陈旧） | 改列 **already-landed 证据项**（引 plan 2496 提交 `646673a90e` + `PeriodicTaskHelper` 现场）；实施工单数 18 → **17** |
| **B-2** `AbstractResourceLock.tryLockWithLease` 不存在 | 改点为 `LocalResourceLockManager.tryLockWithLease:182-184`（throw 源）+ `AbstractResourceLock:57-59,67-69`（声明）+ `SysDaoResourceLockManager:114`（另一实现） |
| **B-3** C2-007 传播面低估 + 漏 nop-sys | **采纳米策略**：`tryLockWithLease` 签名不变（零调用点改造），只在 `lockInterruptibly`/`tryLock(time,unit)` 识别 `cause instanceof InterruptedException` 重抛；移出 `LocalFileLock`（它已合规）；回归面补 `nop-sys` |
| **B-4** `computeIfAbsent` 「返回值身份判定」是假的 | 只用 `boolean[] created`/`AtomicBoolean`；字段类型改 `Map<K, CompletableFuture<V>>` + `join` 解包 `NopException`；删除 `LocalCache.computeIfAbsentAsync` 先例引用（那是 Caffeine 原生异步，非同形态） |
| **B-5** C3-012 cancel-then-drain 逻辑不成立 | 次序改为「置停止接收位 → 消费循环 drain 至空 → cancel → 销毁」；超时用 `drainTo(list)` 取残量逐条记丢弃清单 |
| **B-6** 载体表缺失 | 下节补齐 17+1 条方法级载体表，并逐条 grep 校验 |
| **B-7** 回归面漏 7 模块 | 设计 §五 按 live grep 重写（补 nop-sys / nop-batch-exp / nop-js / nop-ai-shell / nop-ai-agent / nop-graphql-grpc / nop-http-client-jdk / nop-rpc-simple / nop-config / nop-datav / nop-web / nop-graphql-core），并单列 `GlobalExecutors.register` 阻塞风险 |
| **M-1** destroy 后 stats NPE 前提假 | 拆为「`destroy()` 补 init 前 null 守卫」+「`DefaultScheduledExecutor.stats` fail-fast 已由 C1-003 完成（already-landed）」 |
| **M-2** `awaitTerminationSeconds=0` 语义缺失 | 明确「仅 `waitForTasksToCompleteOnShutdown==true && awaitTerminationSeconds>0` 才有界等待；否则保持 `shutdown()` 现语义」 |
| **M-3** C3-018 实参类型不成立 | 改 `bindCancel(state.getPromise(), future.toCompletableFuture())` |
| **M-4/M-5/M-6** C1-009 | 给 11 项显式清单（其中 `RecordMappingManager`/`MarkdownTool` 跨模块 → Deferred，本批做 9 项）；`ErrorMessageManager` 单列 this-escape（`:114-116`）；`VirtualFileSystem` 改点为 `registerInstance:31-34` |
| **M-7** C1-004 lambda 捕获编译不过 | 明确形态：`compute` 内先建 `CompletableFuture` 局部变量，schedule lambda 捕获该 effectively-final 变量 |
| **M-8** C3-008 唯一消费方绕开 execute | 载体表注明边界（`WebSocketSendPump` 自行处理拒绝） |
| **M-9** C3-009 是契约变更 | Phase 2 标 `contract change`，Exit Criteria 要求 javadoc+owner doc 同步 + absent 断言载体 |
| **M-10** C3-013 改动类未界定 | 列明 3 个 `execute` 入口 + `IContextProvider` 用 `default` 方法新增 |
| **M-11** 端到端 Exit Criteria 不可观测 | 改为具体测试 `TestThreadPoolDestroyAwaitProbe`（含 await>0 有界等待、await=0 不等待两腿） |
| m-1..m-10 | 行号订正；`LocalFileLock` 移出传播链；C1-011 补非中断路径中断标志登记；C3-010 补 C3-019 边界；C3-012 队列取数机制；C1-010/C2-004 零调用方写入基线；设计降为锚点级；本修订清单段；C3-002 入 Deferred；18>15 拆分裁定（本批 17 项按 design §六 四组串行，不拆批） |

## Current Baseline

- **判级**：18 项全 P2（merge-adjudication 单元内裁定）；**实际实施 17 项 + 1 项 already-landed（C3-002，见 E-2547-1）**。
- **依赖**：8.2（R1-01 绝缘层，done）、8.3（R1-02 池设施，done）、8.10（R2-01 kernel 退役代际，done）。
- **PA 判定**：全部站点在 nop-kernel（框架核心）→ plan-first。**本批是本 campaign 首个全 PA 批次**，须走完整设计 → review → 实施 → 独立 closure audit。
- **live 基线事实**（2026-11-09 亲读 + 设计文档 §三 逐条锚定）：
  - `RateLimitExecutorImpl.debounce` :50-67 `get`-`cancel`-`schedule`-`put` 非原子 + `holder[0]` 提交后写（`throttle` :29-47 已有 `compute` 正确先例）。
  - `ResourceComponentManager` :334-344 `computeIfAbsent` 内 `transform`；`XLangSemantics` :1094-1098 `CLASS_MODELS.computeIfAbsent` 内 `ClassHelper.forName`。
  - `AbstractResourceLock.tryLockWithLease` :50-69 把中断转 `NopException`；`lockInterruptibly()` 无 `throws`。
  - `CycleSynchronizer` :36-46/:66-102 持 `this` 锁跑回调、无 generation。
  - `LocalFileLock` :31-37/:45-90 句柄为实例字段无同步；`tryLock(0)` 失败路径泄漏。
  - `ErrorMessageManager` :42-45/:172-17x 构造器 this-escape；多处 `_instance` 非 volatile。
  - `SequentialTaskExecutor.execute` :79-83 忽略 offer 返回值（**零生产调用方**，唯一消费方 `WebSocketSendPump` 自行处理）；`BlockingSourceConsumeTask` :48-76 循环可静默死亡；`AbstractBatchProcessService.destroy` :71-78 丢弃队列；`DefaultTaskExecutionQueue` :238 `bindCancel(future, promise)` 参数反接（签名 `(CompletionStage, Future)`）。
  - `BindScheduledExecutor` :95-107 **已委托 `PeriodicTaskHelper`**（plan 2496 修复），C3-002 为 already-landed。
  - `CycleSynchronizer` / `AbstractBatchProcessService` 之外的 C1-010/C2-004 **零生产调用方**（潜伏面）。
  - `ErrorMessageManager` 构造器 this-escape 在 `:114-116`（非 :42-45）。`VirtualFileSystem` 错序在 `registerInstance:31-34`（非 unregisterInstance）。
  - `IAsyncCache`/`LocalCache`/`CacheConfig` 语义分叉与 executor 固化。
  - `BaseThreadPoolConfig` :17-35 的 `waitForTasksToCompleteOnShutdown`/`awaitTerminationSeconds` 为死值；`destroy()` 不等待且缺 `init` 前 null 守卫（destroy 后 `stats()` 本身不 NPE）。
- **基线测试签名**（起草期）：`./mvnw test -pl nop-kernel` 全绿。

## Goals

- **17 工单**收口（裁定 A–H 八组）+ C3-002 证据核对。
- 每条工单 ≥1 可被回归捕获的载体；**PA 批次额外要求**：每条都标明消费方回归面（设计 §五）。
- C2-007 的接口签名变更须**同 diff 完成全仓调用点改造**（否则编译断）。
- owner doc 落账（`source-anchors.md` + `ioc-and-config.md`）。

## Non-Goals

- **不拆批**：batch-proposal :128 提示「本批 18 成员 > 15 可拆两次子交付」；经 draft review 后有效工单为 17 项且按 design §六 分四组串行实施（组 3 的 C2-007 采米策略后不再有跨模块编译破坏），故**不拆批**（拆分理由不成立）。
- 不做 R4-14（8.54）的 17 个成员；不做 R4-32（8.72）的 C7-004 墙钟批。
- 不改 `nop-xdefs` schema；不改 orm 模型。
- **不做 C3-009 的行为重写**（改 async 加载语义会破坏既有契约）→ 仅声明面。
- **不做 C3-010 的缺省 executor 更换**（改 globalWorker 有死锁风险）→ 仅给配置出口。

## Scope

### In Scope

`nop-kernel/nop-commons`（`RateLimitExecutorImpl`、`CycleSynchronizer`、`LocalFileLock`、`AbstractResourceLock`/`ResourceLock`/`ResourceMultiLock`、`LocalResourceLockManager`、`SequentialTaskExecutor`、`BindScheduledExecutor`、`BlockingSourceConsumeTask`、`AbstractBatchProcessService`、`ExecutorHelper`、`DefaultThreadPoolExecutor`、`DefaultScheduledExecutor`、`BaseThreadPoolConfig`、`LocalCache`、`CacheConfig`、`IAsyncCache`）
`nop-kernel/nop-core`（`ResourceComponentManager`、`ModuleManager`、`ErrorMessageManager`、`VirtualFileSystem`、`DefaultTaskExecutionQueue`）
`nop-kernel/nop-xlang`（`XLangSemantics`）
测试树 + owner doc。

### Out Of Scope

Non-Goals 所列。

## Execution Plan

### Phase 1 - 设计裁定

Status: completed
Targets: `ai-dev/design/concurrency/kernel-lock-discipline-interrupt-contract-and-executor-destroy-semantics.md`

- Item Types: `Decision`

- [x] 设计文档落盘（八条裁定 A–H + 18 成员逐条唯一形态 + 25 条拒绝方案 + 回归面清单）
- [x] 独立 draft review R1（REJECT 7B+11M+10m）+ R2（PASS-with-revisions，残留已清空）
- [x] review 发现的 Blocker/Major 全部闭环
- [x] PA 授权证据齐备（设计 + 两轮 review + §五 回归面 17 成员全覆盖）
- [x] `ai-dev/logs/` 已更新

Exit Criteria:

- [x] 设计文档在案且 17 成员各有唯一形态（+ C3-002 证据项）
- [x] 独立 draft review R2 PASS-with-revisions 且修订全闭环
- [x] 回归面清单覆盖全部成员（含 12 个消费模块）
- [x] `ai-dev/logs/` 已更新

### Phase 2 - 17 工单实现 + 回归测试（C3-002 为证据项）

Status: completed
Targets: nop-commons / nop-core / nop-xlang + 测试树

- Item Types: `Fix`（14）、`Proof`（3 声明面 + C3-002 证据）

**实施顺序（设计 §六，依赖驱动）**
- 组 1（无兼容性破坏）：C1-004、C1-009、C1-011、C2-003、C2-008、C2-019、C3-008、C3-011、C3-012、C3-018
- 组 2（语义变化）：C1-010 + C2-004（同 diff）、C3-013、C4-002
- 组 3（中断语义，最后）：C2-007（米策略，无签名变更）
- 组 4（声明/配置）：C3-009、C3-010

- [x] G1 C1-004：`debounce` 改 `promiseMap.compute`
- [x] G2 C1-009：单例槽位 `static volatile` + `ErrorMessageManager` this-escape 消除
- [x] G3 C1-011：`LocalFileLock` 句柄会话化
- [x] G4 C1-010 + C2-004（同 diff）：`CycleSynchronizer` generation + 回调出锁域 + 逐回调隔离
- [x] G5 C2-003：`ResourceComponentManager` 占位模式
- [x] G6 C2-007：中断语义识别重抛（米策略，`LocalResourceLockManager` + `AbstractResourceLock` + unlock 纪律）
- [x] G7 C2-008：`LocalResourceLockManager` 过期判定入 latch 锁
- [x] G8 C2-019：`XLangSemantics` 占位模式
- [x] G10 C3-008：`SequentialTaskExecutor.execute` 拒绝显式化
- [x] G11 C3-009：`IAsyncCache` 语义声明
- [x] G12 C3-010：`CacheConfig` executor 出口
- [x] G13 C3-011：`BlockingSourceConsumeTask` 循环韧性
- [x] G14 C3-012：`AbstractBatchProcessService.destroy` 有界等待 + 丢弃清单
- [x] G15 C3-013：`execute` 路径上下文卫生
- [x] G16 C3-018：`DefaultTaskExecutionQueue` bindCancel 与计数
- [x] G17 C4-002：`destroy()` init 前 null 守卫 + 配置项生效（有界等待，缺省语义不变）
- [x] **C3-002（already-landed）**：closure 证据核对（不写码）
- [x] 回归测试：nop-kernel 全量 + 消费方 focused（设计 §五 逐条）
- [x] **无静默跳过**（rule 24）：拒绝/丢弃/回调异常全部显式，无空 catch

**工单 × 载体表（draft review R1 B-6）**

> **宿主类状态（2026-11-09 grep 实测）**：
> - **已存在，直接扩展**：`TestLocalFileLock`（nop-commons/lock）、`TestRateLimitExecutor`（nop-commons/executor）、`TestSequentialTaskExecutor`（nop-commons/executor）、`TestDefaultTaskExecutionQueue`（nop-core/execution）
> - **需新建**：`TestCycleSynchronizer`、`TestLocalResourceLockManager`、`TestResourceComponentTransform`、`TestXlangSemanticsClassModels`、`TestSingletonSlots`、`TestBlockingSourceConsumeTask`、`TestAbstractBatchProcessService`、`TestExecutorContextHygiene`、`TestLocalCacheAsyncSemantics`、`TestLocalCacheExecutorConfig`、`TestThreadPoolDestroyAwaitProbe`
> - **实施纪律（2546 教训）**：每建一个载体类后立即 `grep -c "class 类名"` 校验，并在收口前对整表做一次全量存在性扫描。

| 工单 | 载体（测试类 FQN / 方法 / 断言目标） | 类型 |
|---|---|---|
| C1-004 | `nop-commons/.../TestRateLimitExecutor#pin0104a`（同一 key 并发两次 debounce → 旧任务被取消、只执行 1 次；缺陷态=两次都执行）/ `pin0104b`（`delay=0` 下 `promiseMap` 无陈旧条目） | 行为钉（并发） |
| C1-009 | `nop-core/.../TestSingletonSlots#pin0109`（结构钉：9 个槽位声明均为 `static volatile`，反射读 modifier）/ `pin0109b`（`ErrorMessageManager` 构造不再调用 `registerErrorMessageManager(this)`——用源码契约或可观测注册时机断言） | 结构钉 + 行为钉 |
| C1-010 | `nop-commons/.../TestCycleSynchronizer#pin0110`（旧周期 finish 与新周期 begin 交错 → 新周期 processing 状态不被清） | 行为钉（forced interleave） |
| C1-011 | `TestLocalFileLock#pin0111a`（`tryLock(0)` 失败后句柄已释放——文件可被另一实例立即锁定）/ `pin0111b`（同一实例被两线程使用时句柄不串） | 行为钉（fd/共享实例） |
| C2-003 | `TestResourceComponentTransform#pin0203a`（并发 16 线程同 key 只构建 1 次）/ `pin0203b`（构建失败传播异常而非挂起） | **结构钉**（验证占位模式形态，非端到端装配链；与生产代码三段同构性已在钉内声明） |
| C2-004 | `TestCycleSynchronizer#pin0204a`（回调内 `await` 本周期 promise 不死锁——超时即失败）/ `pin0204b`（某回调抛异常不阻断后续回调、promise 仍正常完成） | 行为钉 |
| C2-007 | `TestLocalResourceLockManager#pin0207a`（等待中被中断 → `lockInterruptibly` 抛 `InterruptedException` 而非 `NopException`；缺陷态=包装掉）/ `pin0207b`（中断标志被恢复） | 行为钉（中断传播） |
| C2-008 | `TestLocalResourceLockManager#pin0208`（过期判定+删除在 latch 监视器内——结构钉：与 `checkTimeout` 同锁；或用 STW 交错证明无法观察到半删态） | 结构钉 |
| C2-019 | 与 C2-003 同一占位模式（同形态约束），由 `TestResourceComponentTransform` 的两腿覆盖形态正确性；C2-019 站点与 C2-003 的**唯一差异**是构建体为类加载 | **结构钉**（共用 C2-003 载体；不重复建类） |
| C3-002 | **already-landed 证据**：`PeriodicTaskHelper` 的 `scheduleRound`/`dispatchRound` 已有 `try/catch(Throwable) → rejectChain`；现有 `TestPeriodicTaskInsulation` 全绿 | 证据（不改码） |
| C3-008 | `TestSequentialTaskExecutor#pin0308`（队列满时 `execute` 不静默丢——抛 REE 或按 policy + 计数增；缺陷态=静默） | 行为钉（单元；注：零生产调用方） |
| C3-009 | `TestLocalCacheAsyncSemantics#pin0309`（async 分支未命中**不加载**——Caffeine 对 `k->null` 抛 `NullMapCompletionException`，为「不加载」的等价事实证据；`pin0309b` 已缓存条目仍返回）+ javadoc 契约段 | 契约钉（**contract change**） |
| C3-010 | `TestLocalCacheExecutorConfig#pin0310`（`CacheConfig` 设 executor 后 `LocalCache` 实际使用该 executor——桩 executor 计数） | 行为钉 |
| C3-011 | `TestBlockingSourceConsumeTask#pin0311`（消费体抛 `Error` 后循环仍存活并继续处理后续条目；缺陷态=永久死亡；**变异红证**）。**第二腿（submit future 可观测）由 `TestAbstractBatchProcessService#pin0312` 的 drain 断言间接承载**——本批未新增 `Future` 字段（M-7 如实登记，非宣称独立覆盖） | 行为钉 |
| C3-012 | `TestAbstractBatchProcessService#pin0312`（destroy 前已入队条目被 drain 完（`isAllProcessed` 可达）；超时残量逐条记录）/ `pin0312b`（destroy 后有界返回） | 行为钉 |
| C3-013 | `TestExecutorContextHygiene#pin0313`（`execute` 路径任务结束后池线程上下文已 detach——租户残留断言） | 行为钉 |
| C3-018 | `TestDefaultTaskExecutionQueue#pin0318a`（取消 promise → 任务 future 被取消；缺陷态=未接线）/ `pin0318b`（被取消任务计入 cancelled 而非 completed） | 行为钉 |
| C4-002 | `TestThreadPoolDestroyAwaitProbe#pin0402a`（`waitForTasksToCompleteOnShutdown=true` + `awaitTerminationSeconds=2`：提交阻塞 200ms 任务，destroy 返回后任务已完成）/ `pin0402b`（`awaitTerminationSeconds=0` → 不等，保持现语义）/ `pin0402c`（`destroy()` 在 `init()` 前调用抛类型化异常而非 NPE） | 行为钉（端到端） |

Exit Criteria:

- [x] 17 工单全部 landed + C3-002 证据核对
- [x] 载体表逐条**已 grep 校验宿主类存在**且断言目标行为（非"不抛异常"）
- [x] 关键项先红后绿或变异论证（≥4 条：C1-004 重复执行、C2-004 自死锁、C2-007 中断传播、C4-002 destroy 等待）
- [x] `./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` **BUILD SUCCESS**（排除形态：该两模块依赖仓库无源目录的 `nop-ai-translate`，已知面⑥，沿 2539/2545 先例）
- [x] `./mvnw test -pl nop-kernel` BUILD SUCCESS
- [x] 设计 §五 回归面**可执行面逐条执行且登记**（12/12 可执行模块 + `nop-xdefs` 仅编译 + `nop-js`/`nop-graphql-grpc` 编译不可得登记——见 E-2547-5）
- [x] **端到端验证**：`TestThreadPoolDestroyAwaitProbe` 三腿（await>0 有界等待完成 / await=0 不等 / init 前 destroy 抛类型化异常）
- [x] **接线验证**：C2-007 的 `throws InterruptedException` 在全部调用点被编译验证（全仓编译即证据）
- [x] `ai-dev/logs/` 已更新

### Phase 3 - 全量验证 + owner doc 落账

Status: completed
Targets: nop-kernel 全量 + owner doc

- Item Types: `Proof`

- [x] E3 分解通道：`./mvnw test -pl nop-kernel -am` BUILD SUCCESS + 逐模块签名
- [x] owner doc 落账：`source-anchors.md`（锁/调度/destroy 锚点）+ `ioc-and-config.md`（池配置项生效语义）
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--module nop-kernel --severity high` EXIT=0
- [x] `ai-dev/logs/` 已更新

Exit Criteria:

- [x] E3 验收且逐条登记
- [x] owner doc 落账核对
- [x] 双门禁 EXIT=0 登记
- [x] `ai-dev/logs/` 已更新

### Phase 4 - closure audit + 收口

Status: completed
Targets: 本 plan、roadmap 8.53 行、cra-index P2-G27 束行

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session）：逐条核对 Exit Criteria / Closure Gates / Anti-Hollow / PA 回归面完整性
- [x] cra-index 台账回填：P2-G27 束行（R4-13 半边收敛注）+ roadmap 8.53 → done
- [x] 双门禁 + Closure Evidence 写入本 plan

Exit Criteria:

- [x] closure audit 证据写入 Closure 段
- [x] cra-index + roadmap 在案
- [x] 双门禁 EXIT=0

## Closure Gates

- [x] 17 工单全部收口 + C3-002 already-landed 证据在案
- [x] 每工单 ≥1 可被回归捕获的载体
- [x] `./mvnw test -pl nop-kernel -am` BUILD SUCCESS
- [x] 设计 §五 回归面**可执行面**逐条执行（PA 硬门禁；`nop-js`/`nop-graphql-grpc` 编译不可得见 E-2547-5）
- [x] owner doc 落账完成
- [x] scan-hollow nop-kernel `--severity high` EXIT=0
- [x] 不存在被静默降级的 in-scope live defect
- [x] 独立子 agent closure-audit 完成并记录证据
- [x] **Anti-Hollow Check**
- [x] `check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0
- [x] cra-index P2-G27 收敛注 + roadmap 8.53 → done

## 执行期勘误（Phase 2 实测发现）

| 编号 | 内容 |
|---|---|
| **E-2547-2** | C3-013 原设计对三个 `execute` 入口（DTE / `DefaultScheduledExecutor` / `BindScheduledExecutor`）统一包装。实测 `TestPeriodicTaskInsulation` 的 `rejections.size()` 变为 2（缺陷态 1）——原因：周期任务命令由 `PeriodicTaskHelper` 绝缘守卫管理，其拒绝回调语义依赖命令身份/时序，外层再包一层会重复触发。**收窄**：只包装 `DefaultThreadPoolExecutor.execute`（`globalWorker`/`cachedThreadPool` 的实际载体）；两个定时器入口保持直通并加注释说明。 |
| **E-2547-3**（**closure audit R1 B-1 + R2 M-6 订正，原结论作废**） | 曾误判为「`cancel(true)` 无法撤下已提交的底层调度」，据此把载体降级为「映射表条目数」。**实测证伪**：`ExecutorHelper.schedule:271` 明确 `FutureHelper.bindCancel(future, f)`，把返回的 CF 与底层 `ScheduledFuture` 绑定——`cancel(true)` **能**撤销底层调度（独立探针：`RAW_RAN=0`；旧实现 `OLD_EXECUTED_COUNT=1`）。首版修复误把**本地新建的 CF** 存进 map（无下层绑定）→ debounce 退化为「两次都执行」（功能回归）。**已重做**：改存 `schedule` 返回的 future；载体 `pin0104a_debounceExecutesOnlyLastTask` 断言「只执行最后一次」，**变异（改回本地 CF）红证成立**。 |
| **E-2547-4** | C3-013 的 finally 中原含 `Thread.interrupted()`（对齐 `ExecutorHelper.submit` 先例）。实测它清掉了周期任务守卫依赖的中断/取消标志，导致 `TestPeriodicTaskInsulation` 失败。**收窄**：`execute` 路径只 `detachContext()`，不清中断标志。 |

## 执行期勘误（补充）

| 编号 | 内容 |
|---|---|
| **E-2547-5**（closure audit R2 Major-1 / R3 Major-1-residual） | `nop-js` / `nop-graphql-grpc` **本批未编译**：二者均为 pom 注释掉的 module（`nop-frontend-support/pom.xml:22`、`nop-service-framework/nop-graphql/pom.xml:21`），且**独立编译亦不可用**（缺 `org.graalvm.polyglot:js` / `protobuf-maven-plugin:0.6.1` 离线不可解析）。二者 `git diff` **零触碰**（`git diff --name-only 7176cd614a..HEAD | grep -cE 'nop-js|nop-graphql-grpc'` = 0），**风险接受**。**不得声称"已由 E1 全量编译覆盖"**（该声明为假）。<br>`nop-xdefs` **无测试树**（`src/test` 下 0 个 `.java`），仅编译面验证。 |

## Deferred But Adjudicated

### C3-002 `BindScheduledExecutor` 重排异常兜底（already-landed）

- Classification: `already-landed`（非 deferred，属证据项）
- 证据：plan 2496（提交 `646673a90e`）引入 `PeriodicTaskHelper` + `InsulatedPeriodicTask`；`BindScheduledExecutor.scheduleWithFixedDelay:95-107` 已委托之，`scheduleRound`/`dispatchRound` 均 `try/catch(Throwable) → guard.rejectChain(e)`。
- Why Not Blocking Closure: 本批不改码；closure 时以「现有 `TestPeriodicTaskInsulation` 全绿 + 代码锚点」作为该成员的收口证据。

### C1-009 中跨模块的两项单例槽位

- Classification: `out-of-scope improvement`
- 内容：`RecordMappingManager`（`nop-record-mapping`）、`MarkdownTool`（`nop-markdown`）——均不在 R4-13 的 nop-kernel Scope 内。
- Why Not Blocking Closure: 二者不属本批模块范围；同族修复形态（`static volatile`）已在本批 9 项内落地，跨模块两项为机械同型改动，风险极低但需各自模块的回归面。
- Successor Required: `yes`；Successor Path: R-G 治理批（roadmap 8.74）统一清扫，或独立小批次。

### C3-010 关联的 C3-019 面（同步 LoadingCache 的 refresh 仍落 commonPool）

- Classification: `watch-only residual`
- Why Not Blocking Closure: C3-019 未在本批成员清单内（审计把 C3-010 裁为本批、C3-019 未列）；本批只给 `CacheConfig` 异步 executor 出口。
- Successor Required: `yes`；Successor Path: 登记至 R-G 或下次 nop-commons 批次。

## Non-Blocking Follow-ups

- C1-011 的 `lock()`/`tryLock()`（非 interruptable 路径）若在内部等待中被中断，当前只会恢复中断标志而不抛出——审计原文同族补遗，本批按「句柄会话化」主面修复，非中断路径语义保持现状（登记 watch）。
- `SequentialTaskExecutor.execute` 当前零生产调用方；`WebSocketSendPump` 自行处理队列拒绝，不受本批修复影响。
- `IContextProvider` 新增的只读方法以 `default` 实现提供，`BaseContextProvider` 与测试 `RawProvider` 均无需改动。

## Closure

Status Note: 17 实施工单 + C3-002 already-landed 证据项全部收口；PA 授权（设计 + draft review R1/R2 + §五 回归面）齐备；**五轮独立 closure audit 收敛至 0 Blocker / 0 Major**。
Completed: 2026-11-09

Closure Audit Evidence:

- Reviewer / Agent: 独立 subagent（fresh session，只读；R1→R5 五轮均为 fresh session，无自审）
- Audit Sessions: R1 agent_1b3353e9 / R2 agent_f02f5c5e / R3 agent_d0e14fb5 / R4 agent_592e7004 / R5 agent_631b1f4b
- **五轮审计链（收敛轨迹）**：
  - R1 **REJECTED**（5B+7M）：C3-002 基线陈旧、C2-007 传播面漏 nop-sys、`computeIfAbsent` 返回值判定不可行、C3-012 cancel-then-drain 不成立、载体表缺失、回归面漏 7 模块 → 全闭环
  - R2 **REJECTED**（1B+2M）：C3-009 契约写反 + pin0309 恒真断言、E1 覆盖声明不实、E-2547-3 未订正 → 全闭环
  - R3 **REJECTED**（1B残留+1M+2m）：`@return` 旧句、E-2547-5 未落 plan/design → 全闭环
  - R4 **REJECTED**（1B+2m）：C3-009 被**过度泛化为接口级承诺**（实为 4 种实现/配置分叉）→ 全闭环（javadoc/owner doc/design/测试注释四处同步为分叉表）
  - R5 **APPROVED-with-corrections**（0B/0M/1 文本 Minor）：逐实现核对 3 种行为声明与 live 一致；旧普遍句规范面零命中 → 收口
- **关键变异红证（逐条「变异→载体确定性 FAIL→恢复→diff 为空」）**：
  - C1-004 `debounce`：改回本地 CF → `pin0104a` 红（两次都执行）；`RAW_RAN=0`/`OLD_EXECUTED_COUNT=1` 证明 `ExecutorHelper.schedule:271 bindCancel` 使 cancel 可撤销底层调度
  - C1-010：删 generation 守卫 → `pin0110` 红（旧周期清新周期状态）
  - C3-011：移除 `catch(Throwable)` → `pin0311` 红（循环永久死亡）
  - C3-018：断接 bindCancel → `pin0318a/b` 红
  - C4-002：`waitForCompletion=false` → `pin0402a` 红
  - C3-009：3 条独立变异（走同步 getAll / 逐 key get / 先调 loader）→ `pin0309` 均红（loader 计数判别面成立）
- **承载面**：27 个 pin 全表 grep 存在；测试类 5 个已存在扩展 + 8 个新建
- **门禁**：`nop-commons` 439 / `nop-core` 553(2S) / `nop-xlang` 864(2S) = **1856 tests 0F/0E**（clean 重建）；`check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0；`scan-hollow --module nop-kernel --severity high` EXIT=0（Total=0）；E1 `clean install -DskipTests -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS（400+ 模块）
- **Anti-Hollow**：0 命中；无空 catch、无恒真断言（pin0309 已三面判别）
- **Deferred 分类**：C3-002=already-landed 证据项（plan 2496/`646673a90e`）；C1-009 跨模块 2 项（nop-record-mapping/nop-markdown）=out-of-scope→R-G 8.74；C3-019=watch-only→R-G。均非静默降级
- **E-2547-5 例外**：`nop-js`/`nop-graphql-grpc` 为 pom 注释 module，**本批未编译**（缺 graalvm-js/protobuf-plugin），零 diff 风险接受；`nop-xdefs` 无测试树仅编译

Follow-up:

- （non-blocking）`IAsyncCache` 分叉表「本接口的默认实现」已精确化为 `ISyncCache` 默认实现；plan 历史行「absent 断言载体」已订正为「整体失败断言载体」
- （non-blocking，已登记）C1-011 非中断路径中断标志仅恢复不抛出（watch）；`SequentialTaskExecutor.execute` 零生产调用方
- （non-blocking，测试基建）flaky：`TestPeriodicTaskInsulation` / `TestResourceCacheInvalidationChain` / `TestExecutorHelperCancelContract`（时间敏感，clean 后连跑全绿）→ R-G 候选
- （non-blocking，流程）PA 批次回归必须 `clean`，不信增量编译残留
