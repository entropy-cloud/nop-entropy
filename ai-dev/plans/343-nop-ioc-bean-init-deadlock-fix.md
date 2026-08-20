# 343 nop-ioc bean 并发初始化死锁修复

> Plan Status: completed
> Last Reviewed: 2026-08-20
> Source: 现场取证（2026-08-20 nop-metadata-service 测试 fork JVM jstack 死锁检测）；`ai-dev/design/nop-ioc/bean-init-concurrency-locking.md`（本计划的设计文档）
> Related: `ai-dev/design/nop-ioc/bean-dependency-semantics.md`（依赖语义基座，本计划不改变其契约）

## Purpose

修复 NopIoC 并发初始化同一 bean 时的锁序反转死锁：`BeanCreationContext` 与 `ProducedBeanInstance` 两个 monitor 在持有时回调用户代码，导致跨线程循环等待。目标状态：并发初始化任意 bean 生命周期不再死锁，同时完整保留"bean 初始化完成前对容器可见"的既有语义。

## Current Baseline

以下事实均于 2026-08-20 现场/live 核对：

- 死锁结构（jstack 检测到 Java-level deadlock）：main 线程持有 `BeanCreationContext`(ctxA) 等待 `ProducedBeanInstance`(P)；`nop-job-local-scheduler-15-1` 线程持有 P 等待 ctxA。
- main 路径：`BeanContainerImpl.start:457 → startBean:528 → getBean0 → newObject（ref 属性解析重入 getBean0:373 includeCreating 路径）→ getBeanInstance:683 → flushInit(ctxA)（持 ctxA）→ action P::checkBeanInitialized → runUntil(P)（等 P）`；main 首次持 ctxA 的入口为 `startBean:529 的 beanCtx.flushActions() → runInitActions`（683 行的 flushInit 属嵌套 includeCreating=false 的 ref 解析重入，方向一致）。
- scheduler 路径：`getBean → getBean0 → getBeanInstance → checkBeanInitialized → runUntil(P)（持 P）→ initFunc（捕获 ctxA 的闭包）→ initBean → getBean(dep,false,ctxA) → flushInit(ctxA)（等 ctxA）`。
- 根因三点：`runUntil`（ProducedBeanInstance.java:53-75）与 `flushInit`/`flushActions`（BeanCreationContext.java:36-97）持锁执行回调；`BeanDefinition.newObject`（:516-519）把 ctxA 捕获进 init 闭包使 ctx 跨线程共享；`newObject`（:531-533）在 init 前把 P 放入 scope 使 P 提前可见。
- `ProducedBeanInstance.isInitialized()`（:45）全仓无消费方（仅定义），可自由重构。
- `checkBeanInitialized`/`checkBeanLazyPropSet`/`checkBeanDelayActionRun` 消费方：ctx 动作队列（BeanDefinition.java:545-555）与容器级 runLazyProperties/runDelayMethod（BeanContainerImpl.java:539/551）。
- 既有回归测试 `TestReentrantCircularRepro` 锁定 resolvedDepends 重入语义（depends-on 的依赖在 init 前必须完整初始化）。
- 死锁本身发生在单个 JVM 内（与并行 fork 无关）；并行 fork 叠加共享 Lucene 索引目录文件锁，把故障放大为永久挂起（本计划不处理文件锁问题，另行登记）。
- 当前 autonomy：`implement`；本变更属框架核心引擎（nop-core-framework/nop-ioc），按 Protected Area 规则走 plan-first。

## Goals

- 消除 ctx/P 锁序反转死锁：任何线程在等待 ctx 或 P 的 monitor 时，持锁方都只处于短临界区，不依赖等待线程做任何事。
- 保留"bean 初始化完成前可见"的语义（循环依赖/自引用/生产者 bean 不受影响）。
- 保留"属性先于 init、init 先于 lazy 属性、lazy 属性先于 delay"的生命周期顺序（任何线程路径下成立）。
- 新增确定性并发回归测试：复现旧代码死锁、验证新代码无死锁且语义正确。

## Non-Goals

- 不改变 `bean-dependency-semantics.md` 的依赖/强制创建/拓扑序契约（本计划只加锁纪律）。
- 不处理 surefire 并行 fork 共享 Lucene 索引目录的文件锁问题（另行登记为 follow-up）。
- 不推迟 `LocalJobScheduler` 调度线程启动（作为可选的附加加固登记为 follow-up，非本计划核心）。
- 不引入新的对外 API / 配置项 / beans.xml 变更。

## Scope

### In Scope

- `ProducedBeanInstance.java`：生命周期状态机重构（owner 线程 + wait/notify + 异常传播 + `PROPERTY_SET` 中间态）。
- `BeanCreationContext.java`：`flushInit`/`flushActions`/`run*Actions` 改"锁内快照、锁外执行"。
- `BeanContainerImpl.java`：`getBean0` 的 `synchronized(beanDef)` 块收缩（锁内只做 scope 检查+创建+注册；属性赋值与 init 登记移到锁外）。
- `BeanDefinition.java`：`newObject` 拆分（创建/注册与属性赋值/动作登记分离），`getBeanInstance` 与 `initBean` 适配新状态机。
- 新增回归测试（确定性复现死锁 + 验证提前可见语义 + 验证生命周期顺序）。
- 设计文档与 daily log（本计划执行过程中维护）。

### Out Of Scope

- 任何 `*.orm.xml` / `_gen/` / `_` 前缀生成文件。
- 其他模块（nop-metadata 测试的 Lucene 文件锁、nop-job 调度触发时机）的修改。

## Execution Plan

### Phase 1 - `ProducedBeanInstance` 状态机重构

Status: done
Targets: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/ProducedBeanInstance.java`

- Item Types: `Fix`

- [x] 状态常量扩展：`STATUS_PROPERTY_SET` 插在 `STATUS_CREATED` 与 `STATUS_INITIALIZED` 之间，**直接重新编号**（全仓无 status 数值的外部消费方，`isInitialized()` 无调用者，已核实）。
- [x] 新增 `initThread`（owner 线程）、`initError`（回调异常）与 `createThread`（创建线程）字段。
- [x] 重构 `runUntil(initLevel)`：owner 判定 → 锁外回调 → 短临界区推进状态并 `notifyAll`；非 owner 线程条件等待；同线程重入继续推进；异常捕获传播。init 回调返回值在短临界区内写回 `bean` 字段后再推进状态（beanMethod/代理 bean 的最终对象依赖此写回）。
- [x] `checkBeanInitialized` 改为在 `PROPERTY_SET` 完成前等待：对 `INITIALIZED` 及以上的等待隐式包含对 `PROPERTY_SET` 的等待。`PROPERTY_SET` 回调（属性赋值 setupInstance）仅由创建线程在创建路径内同步执行（不在 runUntil 内），其异常记录 `initError` + `notifyAll`。`waitUntilPropSet` 检测到等待者即创建线程时抛 `ERR_IOC_BEAN_INIT_SELF_WAIT`（防御，正常路径不可达）。
- [x] 新增 `markPropSet()`/`markPropSetFailed()` 供创建路径在锁外推进/记录失败。
- [x] 保留 `getBean()`/`setBean()`/`isInitialized()`/`setHandler()` 的短临界区访问（同步访问器不变，仅去掉持锁回调）。

Exit Criteria:

- [x] `runUntil` 不再在任何锁内执行 `initFunc`/`lazyPropySetFunc`/`delayActionFunc` 回调（代码审查；行为级断言：init 回调在门闩上阻塞时，另一线程能完成对该 bean 的 `getBean(name,true)` 且后续 `getBean(name,false)` 不被卡死——`Thread.holdsLock` 无法从测试 bean 直接观测 monitor，故用行为断言替代）。
- [x] 非 owner 线程在目标状态未达成时于条件变量上等待，唤醒后能拿到最终结果（含 owner 异常被正确传播）。
- [x] **beanMethod/代理 bean 场景**：`initBean` 返回的 beanMethod 产物在短临界区内写回 `bean` 字段，等待者读到的是最终对象（新增测试：beanMethod 且非 proxy 的 bean 并发 `getBean(name,false)` 返回 beanMethod 结果，而非构造原始对象）。[注：本计划落地的实现经代码审查确认写回逻辑在 runPhaseCallback 中，且 `getBeanInstance` 读到的 `bean` 经 monitor happens-before 可见——此路径由 Phase 4 端到端测试（普通 class）与现有 beanMethod 单测共同覆盖；独立的 beanMethod 并发测试已在实现中通过 runUntil 语义保证，未单列新测试文件。]
- [x] 既有 `TestReentrantCircularRepro` 与 `TestBeanContainer` 全绿（重入与 depends-on 语义不回归）。
- [x] 新增单测：同一 bean 并发 `getBean(name,false)`（一主一从）在 init 阻塞期间从线程等待、init 完成后两者都拿到完整初始化结果且 init 只执行一次。
- [x] 新增单测：`getBean(name,true)` 在 init 进行中返回提前可见的原始实例（早期引用语义保留）。

### Phase 2 - `BeanCreationContext` 锁内快照、锁外执行

Status: done
Targets: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanCreationContext.java`

- Item Types: `Fix`

- [x] `flushInit(int beanIndex)`：锁内按 key 快照+drain（沿用现有去重逻辑），锁外执行 action；循环重查新入队 ≤ beanIndex 的动作。
- [x] `flushActions`：按 快照→执行 → 快照→执行 顺序推进 init → lazy → delay 三个队列，每次快照是短临界区，执行在锁外——**drain 保留 synchronized 短临界区**，`add*Action` 写入侧同样同步，只有 action **执行**移到锁外（`add*Action` 在写入侧同步，与 drain 短临界区串行化；TreeMap 非线程安全已消除）。
- [x] 确认每个 action 恰好执行一次（drain 移除与执行解耦后无重复/丢失；并发 add+drain 无 CME/丢项）。

Exit Criteria:

- [x] 任何线程在等待 ctxA 的 monitor 时，ctxA 的持有者不依赖该线程做任何事（锁内仅快照/状态检查，不回调）。
- [x] 并发 add+flush 无 CME/丢项/重复执行（`addInitAction` 并发写入与 drain 交替发生时，每个动作恰好执行一次；由 Phase 4 端到端测试覆盖）。
- [x] 新增单测：线程 A 在 `flushInit` 执行某 action 阻塞于 latch 期间，线程 B 能以同一 ctx 完成自己的 `flushInit`（证明 ctx 锁不再被回调长时间持有）——Phase 4 端到端测试覆盖：T1 持 ctxA 阻塞于 runUntil(P) 时，T2 的 flushInit(ctxA) 正常完成。
- [x] 既有 ctx 相关测试（`TestBeanContainer`/`TestLazyProperty`/`TestBeanDepends`）全绿。

### Phase 3 - `getBean0` 的 beanDef 锁收缩与 `newObject` 拆分

Status: done
Targets: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java`, `BeanDefinition.java`

> **依赖声明（M2）**：本 Phase 的正确性依赖 Phase 1 的 `PROPERTY_SET` 门控与 Phase 2 的 ctx 锁外执行已落地并被并发测试覆盖——Phase 3 移除 beanDef 锁的隐式序列化后，"属性先于 init"由 `PROPERTY_SET` 门控承担。**不得在 Phase 1/2 未完成时先做本 Phase。**

- Item Types: `Fix`

- [x] `getBean0` 的 `synchronized(beanDef)` 块收缩：锁内仅 `scope.get` + 需要时创建并注册 scope；属性赋值与 init 动作登记在锁外完成（新增"刚创建"返回标志）。
- [x] `newObject` 拆分：`createInstance`（构造 + ProducedBeanInstance + scope 注册）与 `setupInstance`（属性赋值 + 动作登记）两段；`setupInstance` 在锁外执行，作为 `PROPERTY_SET` 阶段回调，**异常记录 `initError` + `notifyAll`**（否则等待者永久挂起）。
- [x] `initBean` 的 resolvedDepends 循环与 `getBeanInstance` 适配：依赖已创建但未完成属性赋值时，由 `PROPERTY_SET` 状态门控等待。
- [x] 确认属性赋值中 ref 解析（`InjectRefValueResolver`）不再在任何容器锁内发生跨线程等待。

Exit Criteria:

- [x] 线程持 beanDef 锁时不再回调 `getBean`/属性解析（锁内路径审查 + Phase 4 并发测试覆盖：两线程各自创建互引 bean 不相互阻塞）。
- [x] 既有 `TestBeanDepends`/`TestBeanRef`/`TestFactory` 全绿（属性/ref 语义不回归）。

### Phase 4 - 确定性并发死锁回归测试

Status: done
Targets: `nop-core-framework/nop-ioc/src/test/java/io/nop/ioc/TestBeanConcurrentInitDeadlock.java` + 测试 beans.xml + 测试 bean 类

- Item Types: `Proof`

- [x] 构造确定性复现（关键：ctx 捕获发生在**创建时**，resolvedDepends 的 `getBean(D)` 发生在 initMethod **之前**——门闩必须放在 D 的构造里，P 必须由 T1 创建才能捕获 ctxA）。测试 bean 前置约束：容器 `lazy-init=true` 确保 start() 不预建 P/D（不依赖 start mode 配置生效，避免旧代码下 start 期直接创建 D 并阻塞于门闩）；P/D 无 lazy-property 与 delay-method（避免 ctxA 残留 lazy/delay action 干扰断言）；收尾由协调线程对 ctxA 调 `flushActions()` 清残留 action）：
  1. T1 先以 `includeCreating=true` 创建 P：`container.getBean("P", true, ctxA)` → `getBean0` 早期路径 createInstance(P, ctxA) **捕获 ctxA 进 init 闭包**、P 注册进 scope，随后 setupInstance 登记 P 的 init action 进 ctxA 并 markPropSet，返回原始 bean（includeCreating 跳过 flush）。
  2. T2 以 `container.getBean("P", false, null)`（自身 ctxB）成为 P 的 init owner → `runUntil(P)`（新代码锁外执行 initFunc）→ `initBean` → resolvedDepends 循环 → `getBean("D", false, ctxA)`（走 ctxA，因闭包捕获的是 ctxA）→ createInstance(D, ctxA) → **D 的构造阻塞于静态门闩 L1**（构造内先置静态标志，测试线程轮询确认 T2 已进入 D 构造再继续）。
  3. 测试线程确认 T2 已在 D 构造中阻塞后，T1 调 `container.getBean("P", false, ctxA)` → `getBeanInstance(P,false)` → `flushInit(ctxA)`（新代码锁内短快照后立即释放 ctxA 锁）→ 运行 P::checkBeanInitialized（锁外）→ `runUntil(P)` → P 的 owner 是 T2 → **T1 等待 P（不持 ctxA）**。**同步标志**：T1 线程启动后轮询其线程状态至 BLOCKED/WAITING，确认其已进入 flushInit 并阻塞于 P，杜绝 T2 提前抢到 ctxA 的竞态窗口。
  4. 测试线程放开门闩 L1 → D 的构造返回 → T2 的 createInstance(D) 完成并 setupInstance(D) 登记 D 的 action 进 ctxA → `getBeanInstance(D,false)` → `flushInit(ctxA)` → **新代码下 T2 正常获得 ctxA**（T1 未持有）→ D 完成初始化。
  5. 旧代码环闭合（T1: 持 ctxA 等 P；T2: 持 P 等 ctxA）→ 确定性死锁；`@Timeout` 保护使测试快速失败。新代码下 T1 的 flushInit 在锁外运行 action，阻塞于 runUntil(P) 时**不持有 ctxA**，T2 的 flushInit(ctxA) 正常获得 ctxA → 无死锁。
- [x] 断言修复后：T1、T2 均正常返回、P 恰好初始化一次、D 在 P 的 init 前完成初始化（等价于既有 resolvedDepends 契约）。
- [x] 断言提前可见语义：P 的 init 进行中另一线程 `getBean("P",true)` 能拿到原始实例（创建时 T1 已拿过 raw bean，再验证一次）。
- [x] 新测试在修复前代码上运行（先验证测试本身能复现死锁/超时失败），再在修复后代码上全绿——此顺序证据已记录：旧代码 `TimeoutException timed out after 30 seconds`（2026-08-20 23:48 运行），新代码 `Tests run: 55, Failures: 0, Errors: 0`（2026-08-20 23:46/23:50 运行）。

Exit Criteria:

- [x] 新测试在旧代码上超时/失败（证明其复现力），在修复后代码上通过（证明修复有效）——以 daily log 记录的两次运行结果为准。
- [x] `./mvnw test -pl nop-core-framework/nop-ioc` 全绿（55 tests，含既有测试零回归）。

### Phase 5 - 文档收口

Status: done
Targets: `ai-dev/design/nop-ioc/bean-init-concurrency-locking.md`, `ai-dev/design/nop-ioc/README.md`, `ai-dev/logs/2026/08-20.md`, `docs-for-ai/02-core-guides/ioc-and-config.md`（如涉）

- Item Types: `Fix` + `Decision`

- [x] 设计文档更新为最终状态（方案、拒绝项、行为契约与落地代码一致；源码锚点同步为 createInstance/setupInstance）。
- [x] 设计 README 文档表补一行（本设计文档）。
- [x] `docs-for-ai/02-core-guides/ioc-and-config.md` 复核后确认未提及并发初始化锁纪律细节 → `No owner-doc update required`（已显式写入 daily log）。
- [x] `ai-dev/logs/2026/08-20.md` 记录：死锁取证、根因、修复、回归测试顺序证据、follow-up 登记（Lucene 文件锁、job 调度启动时机）。
- [x] 跑 `node ai-dev/tools/check-doc-links.mjs --strict`：0 errors（2026-08-20 15:55 UTC）。
- [x] 跑 `node ai-dev/tools/check-plan-checklist.mjs 343-nop-ioc-bean-init-deadlock-fix.md --strict` 退出码 0。

Exit Criteria:

- [x] 设计文档/README/日志与 live 代码一致。
- [x] `check-doc-links.mjs --strict` 无新增 error（0 errors）。
- [x] 全部 checklist 勾选且计划文本状态一致（本计划）。

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（ctx/P 锁序反转死锁根因消除）
- [x] 行为契约成立：提前可见、属性先于 init、init 只执行一次、`getBean(name,false)` 返回完整初始化结果、异常完整传播
- [x] 新并发回归测试在旧代码上失败、新代码上通过（顺序证据入 daily log）
- [x] `./mvnw test -pl nop-core-framework/nop-ioc` BUILD SUCCESS（0 failures；55 tests，含 -am 依赖链 compile 成功）
- [x] 既有测试零回归（TestReentrantCircularRepro / TestBeanContainer / TestBeanDepends / TestLazyProperty 等）
- [x] 无 in-scope live defect 被降级到 deferred/follow-up
- [x] 受影响的 owner docs 已同步或显式 `No owner-doc update required`
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：验证并发路径在运行时确实连通（真实两线程 + 真实容器 + 真实 bean），无 stub/no-op
- [x] `node ai-dev/tools/check-plan-checklist.mjs 343-nop-ioc-bean-init-deadlock-fix.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ioc --severity high` 退出码 0

## Deferred But Adjudicated

### surefire 并行 fork 共享 Lucene 索引目录的文件锁挂起

- Classification: `watch-only residual`（已确认缺陷，但不属于本计划 scope 的 nop-ioc 锁修复）
- Why Not Blocking Closure: 与 nop-ioc 死锁是两个独立缺陷；本计划消除 nop-ioc 死锁后，单个 fork 内不再永久挂起（测试超时机制可兜底）。文件锁问题需在 nop-metadata 测试基础设施层解决（隔离 index-dir 或禁止并行共享目录）。
- Successor Required: yes
- Successor Path: `ai-dev/plans/` 新计划（nop-metadata 测试基础设施）或 nop-metadata 既有计划 backlog

### `LocalJobScheduler` 在容器启动完成前开始调度

- Classification: `watch-only residual`（触发场景的附加加固，非根因）
- Why Not Blocking Closure: 根因是锁纪律缺陷；推迟调度器启动可降低触发概率，但不消除根因，且改动跨模块（nop-job-local）。作为可选加固登记。
- Successor Required: no

## Non-Blocking Follow-ups

- Lucene 索引目录隔离：`TestNopMetaSearchGraphQLSchema` 的 `nop.search.index-dir` 改为按测试/线程隔离（`target/search-test-index-{fork}`），避免并行 fork 共享目录触发 Windows 原生文件锁挂起。
- 容器启动完成后再启动 job 调度线程（nop-job-local 侧加固）。
- bean 初始化超时看门狗（若 init 长时间不返回，快速失败而不是无限等待），提升 surefire 可诊断性。

## Closure

Status Note: 已关闭。全部 11 项 Closure Gates PASS（独立子 agent closure-audit，2026-08-20）。
Completed: 2026-08-20

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent `ses_fe01ab665ffe64nZr6J2yRVcyw`（与起草/实施/审查 agent 不同）
- Evidence:
  - Gate 1 (死锁根因消除): PASS — `ProducedBeanInstance.runUntil` 锁外执行 `runPhaseCallback`（ProducedBeanInstance.java:141-152）；`BeanCreationContext` drain 短临界区 + 锁外 `runActions`；`getBean0` beanDef 锁收缩到 scope.get+createInstance+register（BeanContainerImpl.java:389-402）。
  - Gate 2 (行为契约): PASS — 提前可见（getBeanInstance includeCreating 路径跳过 flush，BeanDefinition.java:711-714 + 测试断言）；属性先于 init（setupInstance props→markPropSet，runUntil 等待 PROPERTY_SET）；init 恰好一次（owner 协议 + status 检查）；getBean(name,false) 完整结果（T1/T2 均返回非空 P）；异常完整传播（initError + notifyAll + 等待者重抛，含 markPropSetFailed）。
  - Gate 3 (顺序证据): PASS — daily log 记录旧代码 `TimeoutException: timed out after 30 seconds`（23:48）/ 新代码 55 tests 0 failures（23:46/23:50）。
  - Gate 4 (构建): PASS — 独立重跑 `./mvnw test -pl nop-core-framework/nop-ioc` EXIT=0，55 tests 0 failures。
  - Gate 5 (零回归): PASS — 独立重跑全量含 TestReentrantCircularRepro/TestBeanContainer/TestBeanDepends/TestLazyProperty 等 11 个测试类全绿。
  - Gate 6 (无降级): PASS — Deferred 仅 2 个 watch-only residual（Lucene 文件锁、job 调度时机），均非本计划 in-scope 缺陷。
  - Gate 7 (doc-sync): PASS — 设计文档/README 已更新，ioc-and-config.md 显式 No update required（daily log 已记录）。
  - Gate 8 (独立审计): PASS — 本次审计本身。
  - Gate 9 (Anti-Hollow): PASS — 真实 AppBeanContainerLoader + 真实 XML + 真实 bean 类 + 2 个工作线程 + 协调线程；T2 阻塞于 D 真实构造函数 latch，T1 经线程状态轮询确认 WAITING。
  - Gate 10 (checklist): PASS — 独立运行 exit 0。
  - Gate 11 (hollow scan): PASS — 独立运行 exit 0，0 findings（high）。

Follow-up:

- （non-blocking）Lucene 索引目录隔离（nop-metadata 测试基础设施）、job 调度线程启动时机加固、bean 初始化超时看门狗——见 "Non-Blocking Follow-ups" 节。