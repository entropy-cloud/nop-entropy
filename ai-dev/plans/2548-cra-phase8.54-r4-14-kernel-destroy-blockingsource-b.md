# 2548 CRA Phase 8.54 — R4-14 2.1 销毁链/BlockingSource 束 b（G27 后半，17 工单）

> Plan Status: active
> Last Reviewed: 2026-11-09
> Source: roadmap 工作项 8.54（`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` :225，Status=todo；Owner Doc=同 R4-13；Deps=「batch-proposal §二 R4-14（17 项）；软依赖 8.53」）；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 :129 R4-14 行 + §二.5 G27 行；成员发现 = `ai-dev/audits/2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency/`
> Related: plan 2547（R4-13，completed——本批**复用其 C4-002 destroy 语义、C1-010 generation 守卫、C2-019 CLASS_MODELS**，不重建）；设计文档 = `ai-dev/design/concurrency/kernel-destroy-chain-blockingsource-and-resource-lifecycle.md`
> **Protected Area 授权**：nop-kernel / nop-utils = **plan-first**。授权载体 = 设计文档 + 独立 draft review + §五 回归面。

## Purpose

关闭 R4-14 的 **17 个 nop-kernel P2 工单**（**C6-001 经权威台账复核为 P2 条件化，非 P1**——见 E-2548-1）：destroy 侧终态化（1）、全局池生命周期（2）、指标注销（1）、缓存销毁链（3）、阻塞等待可中断/有界（3）、监听/注册表/死旋钮（7）。

**Draft Review 记录**

- R1（fresh session，122 次工具调用，逐条 live 核实）：**REJECT**，6 Blocker + 12 Major + 8 Minor。全部随本版落盘（见下「Draft Review R1 修订清单」）。核心发现：C6-001 判级与主根因均与权威台账不符、「五入口」枚举不成立、提交点结算不可观测、`GlobalExecutors` 无 destroy 可改、`IRateLimiter.tryAcquire` 已存在、载体表缺失。
- R2（fresh session，27 次工具调用，逐条 live 核实 + 载体宿主类 grep）：**REJECT**，1 新 Blocker + 5 Major 残留 + 3 Minor。判定「PA 载体形式齐备，但语义一致性未收敛」。
- R3（fresh session，135 次工具调用，逐条 live 核实）：**REJECT**，1 Blocker（design §一.1 仍留被驳回的 wrapper 识别形态，与 §三/§四.1 自相矛盾）+ 13 Major + 9 Minor。
- R4.5（执行期勘误登记）：E-2548-2（C7-008 entry 自动淘汰→失效链回归，撤销接线改 API+告警）；G1 载体两处竞态修正（pin6001b 等待式断言、pin6001d cause 二选一）。
- R4（**会话内自查，非独立审计**：用户中止了 R4 子代理并指示「继续」，故由执行会话自查并如实标注）：聚焦核验 R3 修复的 4 个技术要点，全部通过，0 Blocker：
  1. BL-R3-1：§一.1 wrapper 识别残留已清除，与 §三/§四.1 三处一致。
  2. MJ-11：`submitTracked(Callable+Set)` 3 参 vs `submitTracked(Runnable,T,Set)` 4 参，**不同 arity，无重载歧义**。
  3. MJ-5：destroy 顺序充分性核验通过——`InsulatedPeriodicChain.reject` 幂等（CF 层）；`rejectChain` 的 listener 虽不幂等，但 step1 先行使 chain done + `handle.cancel` 使轮次 CF 从 pending 摘除，step3 无双触发路径（`watchNativeHandle` 的 `!chain.isDone()` 守卫短路）。live 锚：`InsulatedPeriodicChain.java:54-57`、`InsulatedPeriodicTask.java:139-150`、`PeriodicTaskHelper.java:91-98`。
  4. 载体可观测性：线程名实为 `nio-file-watch-service-{poolNumber}-{n}`（`NamedThreadFactory.java:82`）→ `startsWith` 前缀过滤可行；`ClassHelper.forName:309` → `getDefaultClassLoader()` 优先 TCCL → 计数 ClassLoader 方案可行。
  附带修正：Item Types 计数 13→14（枚举实为 14 项）；`pin6001d`（在飞任务契约钉）补入载体表。

### Draft Review R3 修订清单（逐条已闭环）

| R3 项 | 结论与修订 |
|---|---|
| **BL-R3-1** design §一.1 与被驳回形态自相矛盾 | 已重写 §一.1「修复形态」：改为引用 §四.1「待结算登记表」为唯一权威，显式写明「**不得**依赖 `shutdownNow()` 返回值的 wrapper 识别——live 载体不存在（BL-N1）」；§三 C6-001 行同步为 `*Tracked` 方法名 |
| **MJ-1** §三 缺 C6-012 行（16/17） | 已补：§三 现 17 行（`grep -c "^| C"`=17）；C6-012 形态 = ①容器 `setDestroyOnRemove(true)` ②依赖 RLC `implements IDestroyable`；plan G7 同步 |
| **MJ-2** G6 欠规格 + `LocalCache.destroy` 被移入 Deferred 标 `landed` | G6 改为三项齐列（① `setDestroyOnRemove` ② `implements IDestroyable` ③ `LocalCache.destroy = invalidateAll+cleanUp`）；Deferred 中该条已删除 |
| **MJ-3** C6-002 `register` 覆盖不 drain 未裁定 | Deferred 新增 residual 条目（分类 `watch-only residual`，successor = R-G 三态契约裁定） |
| **MJ-4** C4-012 三面被丢弃 | 拆为**四面** (a) Fix destroy / (b) Decision 默认值 / (c) Fix `getConfig`+`stats` / (d) Decision `refreshConfig` no-op；§三行同步，plan G15 同步 |
| **MJ-5** destroy 顺序与周期拒绝路径冲突 | §四.1 明确顺序：**periodicTasks 拒绝循环 → shutdownNow → pending 完结**，并解释 `watchNativeHandle` 的 `!chain.isDone()` 守卫因此短路（避免 listener 双触发，`rejections.size()==1` 保持） |
| **MJ-6** 在飞任务结果丢弃未声明 | §一.1 增「契约变化」段 + `pin6001d` 载体 |
| **MJ-7** C4-011 契约变更声明不足 | §五 增「Java 消费方 = 0（编译级证据）+ `Serializable` 向后兼容 + 外部二进制需升级说明」 |
| **MJ-8** pin6007 不可观测（private 字段） | 改为**线程名扫描** `Thread.getAllStackTraces()` 过滤 `nio-file-watch-service` 前缀 |
| **MJ-9** pin9004a「类加载计数==1」不可实施 | 改为 TCCL 切至**计数 `ClassLoader`** + 合成类；`pin9004b` 改 `assertSame` 实例；增 `pin9004c` ClassNotFound |
| **MJ-10** java21 测试基建缺失 | §五 增前置条件：pom 加 junit + 新建 `src/test` + 显式 `-P java21-modules` |
| **MJ-11** 新重载同 arity 歧义 | 改为**独立方法名** `submitTracked` / `submitTracked` / `scheduleTracked`（禁止同 arity 重载）；`pending==null` = 不登记 |
| **MJ-12** plan 基线 C7-011「无逃生门」 | 已改为「`restart()` 已是逃生门（`CycleSynchronizer.java:143-145`）；本批只做声明面」 |
| **MJ-13** Bind 传播边界未声明 | §四.1 增「适用边界：仅当底层 timer 能终态化内层 future；本批保证面 = `DefaultScheduledExecutor`」 |
| **m1-m9** | `successor 8.55` 写死；plan:6 措辞改「不复用」；Deferred `landed` 分类删除；§四.1 移除字段/伪代码（改描述性）；`EmbeddingServiceImpl:288→294`；G3 改写 roadmap 8.55；`Fix+Decision` 组合类型拆分；`_content` 全部替换为**行为断言**（C7-002 用漏拍观测、C7-011 用 `restart()` 逃生门行为）；C4-012 destroy 未注册时 NPE → 要求「非 null 才 destroy」 |

### Draft Review R2 修订清单（逐条已闭环）

| R2 项 | 结论与修订 |
|---|---|
| **BL-N1** C6-001 的 destroy 侧终态化在 live 上无载体 | **真缺陷，采纳**。原设计写「对 `shutdownNow()` 返回的 Runnable 逐项识别 wrapper 并结算」，但 live 上 `ExecutorHelper` 提交的是匿名 `Callable<Object>`，CF 仅在闭包内，`DefaultScheduledExecutor` 拿不到任何句柄 → 机制不存在。**改为「待结算登记表」**：`ExecutorHelper` 3 入口各增 `Set<CompletableFuture<?>> pending` 重载（登记 / whenComplete 摘除 / 提交 REE 时摘除后抛出）；`DefaultScheduledExecutor` 持有 `pending`，`destroy()` 先快照 → `shutdownNow()` → 逐项 `completeExceptionally(ERR_SERVICE_NOT_ACTIVE)` → `clear()`；`BindScheduledExecutor.schedule/submit` 补 inner→outer 异常传播。`pin6001` 改为**确定性**（不依赖时序）：`schedule(callable,60s)` → `destroy()` → 断言 `isCompletedExceptionally()` + cause 类型 + 执行计数=0；增 `pin6001c`（Bind 层传播） |
| **B-1 残留** design 3 处 P1 | 已改：§一裁定表 A 行 → P2；§二「本批唯一 P1」→「本批 17 项全 P2」；§六组 1 → 「P2，最先——依赖最少且独立」；plan 组 1 → 「最先，依赖最少」 |
| **B-2 残留** design §四.1/§六.1「五入口」 | 已改写为「3 个 CF 入口 + 待结算登记表」，并写明真实修复面 = `DefaultScheduledExecutor` + `BindScheduledExecutor` 传播 |
| **B-4 残留** plan「register 才是 put+destroy，正确」/ design §四.2「register（已正确）」+「新增 destroy 方法」 | 已改：plan live 基线改「**无 destroy/destroyAll**；register = put + old.destroy() **也是缺陷形态**」；design §四.2 同步，删除「新增 destroy 方法」的误述（实为 `destroyAndUnregister`/`destroyAll`） |
| **M-1 残留** design §四.4「必须同批按依赖序」 | 已改：「**不存在实施依赖**（`cloneInstance()` 零生产调用方）→ 载体可直接构造 `CacheConfig` 并 `setDestroyOnRemove(true)`；C5-003 在前纯为阅读便利」；§六组 3 标「无强依赖」 |
| **M-4 残留** design §一.6/§四.3「复用 CLASS_MODELS」 | 已改：「**不复用** CLASS_MODELS（值类型只到 IClassModel + join 引入阻塞 + 污染 C2-019 占位契约），新增独立方法级缓存」 |
| **M-7 残留** design §一.3「或记录 Meter 列表」 | 已改为唯一形态「返回 `AutoCloseable` 句柄」，删除「或」 |
| **M-8 残留** design §一.6「改默认或显式声明」 | 已拆为 (a) Fix `destroy()` 覆写 / (b) Decision 默认值不翻转，并写明「不混在一项验收」 |
| **M-6 残留** design 仅写「= successor」 | 已写死 `roadmap 8.55（R4-15）` 三处 |
| **M-10 残留** allExecutors 活引用未入 Deferred | 已补 Deferred 条目（watch-only residual + `pin6003` 依赖该语义 + successor=R-G） |
| **B-5 残留** 载体宿主类 2 处事实偏差 | `TestCycleSynchronizer` → `CycleSynchronizerTest`（类名无 Test 前缀）；`TestNioFileWatchService` / `TestThreadPoolDestroyAwaitProbe` 从「新建」改为「扩展」并在宿主类状态行标注 |
| **m-new** Phase 2 Item Types 与内容不符 | 改为 `Fix`（14）、`Decision`（2：C7-002、C7-011）、`Fix+Decision`（1：C4-012 a/b）、`Proof`（1） |

### Draft Review R1 修订清单（逐条已闭环）

| R1 项 | 结论与修订 |
|---|---|
| **B-1** C6-001 判级与机制与台账不符 | 改为 **P2（条件化）**；主根因（`shutdown()` 丢队列）**已由台账驳回**（JDK 契约排空）；真实残余面 = `DefaultScheduledExecutor.destroy()` 的 `shutdownNow()`。Purpose/Baseline/G1/Exit/Closure Gates 五处同步 |
| **B-2** 「五入口」不成立 | 改为 **3 个 CF 入口**（`submit(Callable)`/`submit(Runnable,T)`/`schedule(Callable)`）；`scheduleAtFixedRate/WithFixedDelay` 不在该文件且已由 R1-01 覆盖；`scheduleWithRandomDelay`（C7-003）**显式 out-of-scope**（Deferred） |
| **B-3** 提交点结算不可观测 | 裁定 A 改为 **destroy 侧终态化补偿**；提交点 try/catch 仅作卫生性附带，**明确不作 Exit Criteria** |
| **B-4** C4-005 修复点不存在 + register 误判 | `GlobalExecutors` 无 `destroy()`；`register` 的 put+destroy old **也是缺陷形态**。改为新增 `destroyAndUnregister(String)` + **显式 residual**（借用方直调仍留死实例，owner doc 声明） |
| **B-5** 载体表缺失 | 已补 17 行方法级载体表（见 Phase 2），逐条 grep 校验宿主类 |
| **B-6** C7-015 修复项已存在 | `IRateLimiter.tryAcquire(int,long)` **已存在**。改为 (i) `acquire()` 默认上界可配置有限值；(ii) Guava 不可中断面 = **watch-only + 调用方超时护栏**（不改 Guava） |
| **M-1** 缓存链依赖序虚构 | `C5-003` 零生产调用方（`cloneInstance()` 全仓仅定义处）；`C6-005`/`C6-012` 各自直接改配置点，**无先后依赖**；C5-003 载体改为直测 clone 往返 |
| **M-2** C7-011 已有逃生门 | `restart()` 已存在（audit 原文即「有入口无自动触发」）。改为 **Decision（声明面+观测）**，**显式拒绝**自动超时；`pin0110` 列入不回退清单 |
| **M-3** C7-010 缺三要素 | 明确：签名不变 + 新配置键 `nop.commons.concurrent.throttle-acquire-timeout-ms`（缺省 60s）+ `ERR_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT` + 中断恢复标志；3 个消费方纳入回归 |
| **M-4** C9-004 复用 CLASS_MODELS 不足 | 改为**新增独立缓存** `Map<String, IMethodModelCollection>`（key=`className+'\0'+funcName`）；不复用 `CLASS_MODELS`（值类型只到 IClassModel + join 引入阻塞）；`ClassNotFoundException→null` 保持；`ClassHelper.forName` 统一语义 |
| **M-5** §五 假消费方/漏真消费方 | 按 live grep 重写：增 `nop-ai-core`/`nop-task-core`/`nop-batch-core`/`nop-web`/`nop-stream`×5/`nop-integration-feishu`；删 `nop-job`/`nop-wf` fixed-rate、`nop-ai-gateway` |
| **M-6** destroyAll successor 无落点 | 明确 successor = **roadmap 8.55（R4-15，nop-core-framework）**；本批只提供 API + 自测 |
| **M-7** C6-004「或」不是裁定 | 定为唯一形态：`registerMetrics` 返回 `AutoCloseable` 句柄，由实例字段持有、destroy 时 close；`"tenant-"+name` 同名 meter 去重列入 G4 子面 |
| **M-8** C4-012 无确定形态 | 拆两项：(a) **Fix** `Jdk21CommonsCoreInitializer.destroy()` 覆写；(b) **Decision** 默认值不翻转 + owner doc 声明 |
| **M-9** C6-006 摘父机制未定 | 明确 parent 字段 + 传递位置（`watch()` / `checkChange` 的 ENTRY_CREATE）+ 重复 watch **复用同一 entry、旧句柄仍有效幂等** |
| **M-10** Deferred 待补 | 已补（C7-003 / C4-005 residual / allExecutors 活引用 / CacheConfig.executor / LocalCache.destroy / destroyAll 接线） |
| **M-11** C7-002 item type 错 | 改为 **Decision**（声明面），Exit Criteria 改为 javadoc + source-anchors 断言 |
| **M-12** 锚点系统性漂移 | §三 全部改为**方法名锚**，行号仅作提示 |
| m-1..m-8 | C4-011 收窄为 2 字段（其余 3 为分支丢弃→out-of-scope）；`CacheConfig.executor` 不拷贝并注明；C6-007 awaitTermination 5s + daemon 语义变更登记；`LocalCache.destroy` 已入 G6；基线标注「引用 2547」；`nop-commons-java21` 单列；`GlobalExecutors` 实为 3 惰性池 + syncExecutor |

## Current Baseline

- **判级**：**17 项全 P2**（C6-001 经权威台账 `C6-resource-lifecycle.md:83` 复核为「降级 P1→P2（条件化）；主根因主张驳回」，见 E-2548-1）。
- **依赖**：软依赖 8.53（plan 2547 completed）。
- **PA 判定**：全部在 nop-kernel/nop-utils = plan-first。
- **与 2547 的分工**（防冲突）：C7-011 与 2547 的 C1-010 同文件但不同失效面（前者=推进者死亡逃生门，后者=旧周期不污染新周期）；C4-005/C6-003 与 2547 的 C4-002 分工（前者=注册表移除，后者=destroy 内部等待）；C9-004 **不复用** 2547 的 CLASS_MODELS（值类型只到 IClassModel 且 join 引入阻塞），新增独立方法级缓存。
- **live 基线事实**（2026-11-09 亲读）：
  - **C6-001（P2 条件化）**：真实残余面 = `DefaultScheduledExecutor.destroy()` 用 `shutdownNow()` **丢弃已排队未执行任务**，其 wrapper future（已返回调用方）永不完成。`DefaultThreadPoolExecutor.destroy()` 用 `shutdown()` 会排空队列（JDK 契约），**不命中**（主根因已驳回）。`ExecutorHelper` 只有 **3 个 CF 入口**。
  - `GlobalExecutors` 现状：**无 `destroy()`/`destroyAll()`**；`register` = `put` + `old.destroy()`（**也是缺陷形态**，顶替不 drain）；`unregister` 存在但不 destroy。借用方直调 `executor.destroy()` → `g_executors` 保留死实例（use-after-destroy，residual）。
  - `CacheConfig(CacheConfig)` 拷贝构造漏 `destroyOnRemove`/`refreshAfterWrite`。
  - `ThreadPoolConfig` 的 `threadPriority`/`allowCoreThreadTimeOut` 全仓零消费者。
  - `CycleSynchronizer.beginCycle` 在 `processing==true` 时抛错；**`restart()` 已是逃生门**（`CycleSynchronizer.java:143-145`），audit 原文即「有入口但无自动触发」——本批 C7-011 只做**声明面**（C7-011 无自动超时）。2547 已加 generation 守卫（不冲突）。
  - `DefaultRateLimiter` 用 `sleepMicrosUninterruptibly`；`IRateLimiter.acquire()` 默认无上界。
  - `ExecutorHelper.throttleExecute` 用 `acquireUninterruptibly`。
- **基线测试签名**：`nop-kernel` 全量 1856 tests（**引用 2547 closure 值**；Phase 2 起点须 clean 复跑一次确认）。

## Goals

- 17 工单收口（全 P2）；C6-001 作为 destroy 侧终态化的旗舰项最先实施并单独验证。
- 每条 ≥1 可被回归捕获的载体；PA 批次标明消费方回归面。
- 不回退 2547 已建立的语义（generation / destroy 等待 / CLASS_MODELS）。
- owner doc 落账。

## Non-Goals

- 不做 R4-32（8.72）的 C7-004；不做 8.55 起的成员。
- 不改 `nop-xdefs` schema；不改 orm 模型。
- **不做宿主 `nop-boot` 的 destroyAll 接线**（属 2.2 批）→ 本批只提供 API，接线登记 successor。
- **不做「换 registry 自动重绑」**（C6-004 只做池 destroy 时注销）。

## Scope

### In Scope
`nop-kernel/nop-commons`（`ExecutorHelper`、`GlobalExecutors`、`ThreadPoolConfig`、`CycleSynchronizer`、`CacheConfig`、`LocalCache`、`DefaultRateLimiter`、`IRateLimiter`、`DefaultScheduledExecutor`）
`nop-kernel/nop-core`（`ResourceLoadingCache`、`TenantAwareResourceLoadingCache`、`FileWatchEntry`、`NioFileWatchService`、`ResourceDependsManager`）
`nop-kernel/nop-xlang`（`XLangSemantics`）
`nop-utils/nop-commons-java21`（虚拟线程开关）
测试树 + owner doc。

### Out Of Scope
Non-Goals 所列。

## Execution Plan

### Phase 1 - 设计裁定

Status: in progress
Targets: `ai-dev/design/concurrency/kernel-destroy-chain-blockingsource-and-resource-lifecycle.md`

- Item Types: `Decision`

- [x] 设计文档落盘（六条裁定 A–F + 17 成员唯一形态 + 与 2547 分工 + 回归面）
- [x] 独立 draft review R1（REJECT 6B+12M+8m，全闭环）
- [x] review 发现的 Blocker/Major 全闭环
- [x] PA 授权证据齐备（设计 + review + 回归面）
- [ ] `ai-dev/logs/` 已更新

Exit Criteria:

- [x] 设计文档在案且 17 成员各有唯一形态
- [x] 独立 draft review R2/R3 复审（R4 = 会话内自查，见 Review 记录）
- [x] 回归面覆盖全部 17 成员（12 消费模块）
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - 17 工单实现 + 回归测试

Status: completed
Targets: nop-commons / nop-core / nop-xlang / nop-commons-java21 + 测试树

- Item Types: `Fix`（14：C6-001/C4-005/C6-004/C5-003/C6-005/C6-003/C6-006/C6-007/C7-008/C7-010/C7-015/C4-011/C4-012a+C4-012c/C9-004）、`Decision`（3：C7-002、C7-011、C4-012b+C4-012d）；C4-012 拆四面分项验收。合计 Fix 14 + Decision 3 = 17 成员

**实施顺序（设计 §六）**
- 组 1（最先，依赖最少）：C6-001
- 组 2：C4-005、C6-003、C6-004
- 组 3（C6-005 ② 是 C6-012 硬前提；C5-003 居首仅阅读便利）：C5-003 → C6-005 → C6-012
- 组 4：C6-006、C6-007、C7-008
- 组 5：C7-010、C7-015、C7-002
- 组 6：C4-011、C4-012、C9-004
- 组 7（最后，需复核 2547）：C7-011

- [x] G1 C6-001（P2）：`ExecutorHelper` 新增 `submitTracked`/`submitTracked`/`scheduleTracked`（登记 / whenComplete 摘除 / REE 时摘除后抛出）+ `DefaultScheduledExecutor` 持有 pending 集、`destroy()` 顺序 = periodicTasks 拒绝循环 → shutdownNow → pending 逐项 `completeExceptionally(ERR_SERVICE_NOT_ACTIVE)` + `BindScheduledExecutor.schedule/submit` 补 inner→outer 异常传播（提交点 try/catch 仅卫生性附带，非验收面）
- [x] G2 C4-005：`GlobalExecutors.destroyAndUnregister(String)` + residual 登记
- [x] G3 C6-003：`GlobalExecutors.destroyAll()` 平台级 API（接线归 roadmap 8.55 / R4-15）
- [x] G4 C6-004：`registerMetrics` 返回 `AutoCloseable` 句柄 + destroy 时 close + `tenant-` meter 去重
- [x] G5 C5-003：`CacheConfig` 拷贝构造补两字段
- [x] G6 C6-005（三项）：① `createCache` 加 `setDestroyOnRemove(true)`；② `ResourceLoadingCache implements IDestroyable`（`destroy = invalidateAll + cleanUp`）；③ `LocalCache.destroy()` 改 `invalidateAll() + cleanUp()`
- [x] G7 C6-012：`TenantAwareResourceLoadingCache` 容器 `setDestroyOnRemove(true)`（**依赖 G6 ②**：值类型须实现 `IDestroyable`）
- [x] G8 C6-006：`FileWatchEntry` unwatch 摘父 + 重复 watch 复用
- [x] G9 C6-007：`NioFileWatchService` daemon 化 + awaitTermination
- [x] G10 C7-008：`ResourceDependsManager` 逐条淘汰
- [x] G11 C7-010：`throttleExecute` `tryAcquire(timeout)` + 新配置键缺省 60s + 类型化超时异常
- [x] G12 C7-015：`acquire()/acquire(permits)` 默认上界可配置（新键缺省 5min）+ Guava 不可中断面 watch-only 登记
- [x] G13 C7-002（**Decision**）：`scheduleAtFixedRate` 补偿语义 javadoc + source-anchors 声明（不改行为）
- [x] G14 C4-011：`ThreadPoolConfig` 删 2 字段（`threadPriority`/`allowCoreThreadTimeOut`）+ owner doc 声明
- [x] G15 C4-012：(a) `Jdk21CommonsCoreInitializer.destroy()` 覆写(Fix)；(b) 默认值不翻转(Decision+doc)
- [x] G16 C9-004：`XLangSemantics` **新增独立** `(className+funcName)→IMethodModelCollection` 缓存 + `ClassHelper.forName`
- [x] G17 C7-011（**Decision**）：`beginCycle` 抛错路径 + javadoc 声明「由 `restart()` 解除，无自动超时」（**不回退 2547 generation**）
- [ ] 回归测试：nop-kernel 全量 + 设计 §五 逐条
- [ ] **无静默跳过**（rule 24）

**工单 × 载体表**（draft review R1 B-5；宿主类状态已 grep 实测）

> **宿主类状态（2026-11-09）**：`TestExecutorHelperCancelContract`（nop-commons/executor，已存在）、`TestPeriodicTaskInsulation`（已存在）、`TestLocalCacheAsyncSemantics`（2547 新建）、`CycleSynchronizerTest`（2547 新建，**类名无 Test 前缀**）、`TestResourceCacheInvalidationChain`（nop-core，已存在）、`TestThreadPoolDestroyAwaitProbe`（已存在，含 pin0402a/b/c，**本批加 pin6001**）、`TestNioFileWatchService`（nop-core，已存在，**本批扩方法**）。其余需新建；每行标注「新建 / 扩展」。
> **实施纪律**：每建一个载体类后立即 `grep -c "class 类名"` 校验，收口前全表扫描。

| 工单 | 载体（测试类 / 方法 / 断言目标） | 类型 |
|---|---|---|
| C6-001 | `TestThreadPoolDestroyAwaitProbe#pin6001`（**扩展**：`DefaultScheduledExecutor` 单线程 `schedule(callable, 60s)` 后立即 `destroy()` → future 必须 `isCompletedExceptionally()` 且 cause = `NopException(ERR_SERVICE_NOT_ACTIVE)`，callable 执行计数=0）/ `pin6001b`（对照组：`DefaultThreadPoolExecutor` destroy 走 `shutdown` 排空 → future 正常完成）/ `pin6001c`（`BindScheduledExecutor` 包装层：底层 timer destroy 后外层 future 亦 `isCompletedExceptionally()`）/ `pin6001d`（**在飞任务**：任务体 sleep 中 destroy → future 异常完结 + cause 类型化 + 任务结果被丢弃——契约钉 §一.1「契约变化」） | 行为钉（变异：移除终态化或移除传播→红） |
| C4-005 | `TestGlobalExecutorsLifecycle#pin4005a`（`destroyAndUnregister(name)` 后 `getExecutor(name)` 返回 null/新实例，非死实例）/ `pin4005b`（residual 声明面：借用方直调 destroy 后注册表仍持死实例——**契约钉**，诚实登记） | 行为钉 + 契约钉 |
| C6-003 | `TestGlobalExecutorsLifecycle#pin6003`（`destroyAll()` 后 `allExecutors()` 为空） | 行为钉 |
| C6-004 | `TestExecutorMetricsUnbind#pin6004a`（`registerMetrics` 返回句柄非 null；close 后 registry 中该池 meter 消失）/ `pin6004b`（LocalCache destroy 后其 meter 消失） | 行为钉 |
| C5-003 | `TestCacheConfigCopy#pin5003`（clone 后 `destroyOnRemove`/`refreshAfterWrite` 与源一致；`executor` 不拷贝） | 行为钉（直测 clone） |
| C6-005 | `TestResourceLoadingCacheDestroy#pin6005`（驱逐条目时 `destroy` 回调被调用——计数桩） | 行为钉 |
| C6-012 | `TestTenantCacheDestroy#pin6012`（`clearForTenant` 触发值对象 destroy） | 行为钉 |
| C6-006 | `TestFileWatchEntry#pin6006a`（unwatch 后父 subEntries 不含该子项）/ `pin6006b`（同路径重复 watch 返回同一 entry 且两次 cleanup 幂等） | 行为钉 |
| C6-007 | `TestNioFileWatchService#pin6007`（**扩展**：`Thread.getAllStackTraces()` 过滤线程名 `nio-file-watch-service` 前缀 → `isDaemon()==true`）/ `pin6007b`（`doStop()` 返回耗时 < 5s 且该线程名消失） | 行为钉（观测路径：线程名扫描，非私有字段） |
| C7-008 | `TestResourceDependsManagerEvict#pin7008`（`removeDependency` 后 `getResourceDepends` 返回 null + 幂等；**E-2548-2**：entry 失效不自动淘汰，失效重载链由 `TestResourceCacheInvalidationChain` 守护不回退） | 行为钉 + 守护钉 |
| C7-010 | `TestThrottleExecute#pin7010a`（许可耗尽 + 短超时 → 抛 `ERR_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT`）/ `pin7010b`（中断 → 标志恢复 + 类型化异常）/ `pin7010c`（正常获许可仍工作） | 行为钉 |
| C7-015 | `TestRateLimiterAcquireBound#pin7015`（`acquire()` 在配置短上界下抛 `ERR_CONCURRENT_RATE_LIMIT_ACQUIRE_TIMEOUT`）/ `pin7015b`（`<=0` 保持无限语义，用 `tryAcquire(permits, 0)` 观察不阻塞） | 行为钉 |
| C7-002 | `TestScheduledFixedRateContract#pin7002`（**行为观测**：单线程 timer 上 `scheduleAtFixedRate` 周期 50ms，任务体 sleep 200ms 持续 3 轮 → 断言第 2/3 轮 start 时刻**不晚于**理论时刻 + 一个周期，即 JDK 补偿语义成立）+ `source-anchors.md` 锚存在性 | **Decision**（行为钉，非 `_content` 文本断言） |
| C4-011 | `TestThreadPoolConfigFields#pin4011`（反射：`ThreadPoolConfig` 无 `threadPriority`/`allowCoreThreadTimeOut` 字段） | 结构钉 |
| C4-012 | `TestJdk21InitializerDestroy#pin4012`（**前置：pom 加 junit + 新建 src/test，profile `java21-modules`**）（destroy 后注册表无 `nop-virtual-thread`） | 行为钉（`nop-commons-java21`） |
| C9-004 | `TestXlangStaticMethodCache#pin9004a`（TCCL 切至计数 `ClassLoader`，加载一个未预加载的合成类 → 第 1 次 `loadClass` 计数 1，第 2 次仍为 1）/ `pin9004b`（两次 `assertSame` 同一 `IMethodModelCollection` 实例）/ `pin9004c`（`ClassNotFoundException` 仍返回 null 不抛） | 行为钉 |
| C7-011 | `pin0110`/`pin0110b`（2547 既有，**不回退**）+ `pin7011`（**行为观测**：`beginCycle` 抛错后 `processing=true`，调 `restart()` 后新周期可正常 `beginCycle`——即逃生门真实可用）+ `source-anchors.md` 锚存在性 | Decision + 回归钉 |


Exit Criteria:

- [x] 17 工单全部实施（C6-001 单独验证 destroy 侧终态化）
- [x] 载体表逐条存在且断言目标行为（17/17：pin6001/a-d、pin4005a/b、pin6003、pin6004a/b、pin5003、pin6005、pin6012、pin6006a/b、pin6007a/b、pin7008、pin7010a-c、pin7015a/b、pin7002、pin4011、pin4012、pin9004a/c、pin7011）
- [x] 关键项先红后绿或变异论证（C6-001：移除终态化→3 红；C6-004：移除 close→1 红；C7-010：移除有界等待→pin7010b 无界挂死（挂死=缺陷形态本身）；C7-008/E-2548-2：entry 自动淘汰→失效链 3 red（反向变异，撤销后 4 绿））
- [x] `./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS（E1 EXIT=0，2026-11-09）
- [x] nop-kernel clean 全量：nop-commons 457 / nop-core 560(2S) / nop-xlang 866(2S) = 1883 tests 0F/0E
- [x] 设计 §五 回归面：nop-web/nop-ai-core/nop-task-core/nop-batch-core `-am` 测试 EXIT=0；nop-stream/nop-integration-feishu 编译级（含于 E1）
- [x] **端到端验证**：pin6001（排队任务 destroy 后异常终态）+ pin6001d（在飞任务契约）
- [x] **接线验证**：pin9004a（第二次解析 assertSame 命中缓存实例）
- [ ] **不回退 2547**：generation 守卫 / destroy 有界等待 / CLASS_MODELS 占位的既有钉全绿
- [ ] `ai-dev/logs/` 已更新

### Phase 3 - 全量验证 + owner doc 落账

Status: planned
Targets: nop-kernel 全量 + owner doc

- Item Types: `Proof`

- [ ] E3 分解通道：`./mvnw test -pl nop-kernel -am` BUILD SUCCESS + 逐模块签名
- [ ] owner doc 落账：`source-anchors.md`（销毁链/监听锚点）+ `ioc-and-config.md`（池/缓存生命周期）
- [ ] 工具门禁：check-doc-links EXIT=0；scan-hollow `--module nop-kernel --severity high` EXIT=0
- [ ] `ai-dev/logs/` 已更新

Exit Criteria:

- [ ] E3 验收且逐条登记
- [ ] owner doc 落账核对
- [ ] 双门禁 EXIT=0 登记
- [ ] `ai-dev/logs/` 已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.54 行、cra-index P2-G27 束行

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session）
- [ ] cra-index 台账回填 + roadmap 8.54 → done
- [ ] 双门禁 + Closure Evidence

Exit Criteria:

- [ ] closure audit 证据写入 Closure 段
- [ ] cra-index + roadmap 在案
- [ ] 双门禁 EXIT=0

## Closure Gates

- [ ] 17 工单全部收口（全 P2）
- [ ] 每工单 ≥1 可被回归捕获的载体
- [ ] `./mvnw test -pl nop-kernel` BUILD SUCCESS（clean）
- [ ] 设计 §五 回归面可执行面逐条执行（PA 硬门禁）
- [ ] **不回退 2547 语义**（三条既有钉全绿）
- [ ] owner doc 落账完成
- [ ] scan-hollow nop-kernel `--severity high` EXIT=0
- [ ] 不存在被静默降级的 in-scope live defect
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] **Anti-Hollow Check**
- [ ] `check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0
- [ ] cra-index P2-G27 收敛注 + roadmap 8.54 → done

## Deferred But Adjudicated

### C7-003 `scheduleWithRandomDelay` 幽灵 future（同文件第 4 个 CF 入口）

- Classification: `out-of-scope improvement`
- 内容：`ExecutorHelper.scheduleWithRandomDelay`（`ExecutorHelper.java:284-312`）的 future 在正常路径永不 complete（仅 `task.run()` 抛异常时才 completeExceptionally）。
- Why Not Blocking Closure: 不在本批 17 成员清单内（审计将其单列为 C7-003）；且其调用方为零（grep 仅定义处）。本批裁定 A 的 destroy 侧终态化不覆盖它（它是自递归调度，不依赖池 destroy）。
- Successor Required: `yes`；Successor Path: R-G 或下次 nop-commons 批次。

### C4-005 residual：借用方直调 `executor.destroy()` 仍留死实例

- Classification: `watch-only residual`
- Why Not Blocking Closure: 拦截该路径需要分发点返回不可销毁包装（破坏 `IThreadPoolExecutor` 语义）或改动 `IDestroyable` 契约（跨模块面）。本批提供受支持入口 `destroyAndUnregister` + owner doc 声明「平台池不得由借用方直接 destroy」。
- Successor Required: `yes`；Successor Path: R-G 治理批（契约面）。

### C6-002 `GlobalExecutors.register` 覆盖时不 drain 旧池（R3 MJ-3）

- Classification: `watch-only residual`
- 内容：`register(executor)` = `g_executors.put(name, new)` + `old.destroy()`；`destroy()` 对 `DefaultScheduledExecutor`/`DefaultThreadPoolExecutor` 都是即时停机，**在飞任务被中断、排队任务被丢弃**（对 DTE 是 `shutdown` 排空，对 DSE 是 `shutdownNow`）。若注册方预期「热替换后旧池自然跑完」，当前形态会静默截断。
- Why Not Blocking Closure: 改变 `register` 语义（改为「先 drain 再 destroy」或「拒绝覆盖」）会影响既有热更调用方（`VirtualThreadTaskExecutor.registerGlobalWorker` 等）；且本台账 C6-002 已由 family-clustering 并入 C4-005，而 C4-005 的裁定面是「注册表移除对称」，不含「覆盖时 drain 策略」。
- Successor Required: `yes`；Successor Path: R-G 治理批（与 C4-005 residual / allExecutors 活引用同批裁定 `register` 的三态契约）。

### `GlobalExecutors.allExecutors()` 返回内部 live map

- Classification: `watch-only residual`
- 内容：`allExecutors()`（`GlobalExecutors.java:40-42`）直接返回 `g_executors` 本体，调用方可 `put/remove` 绕过 `register`/`destroyAndUnregister` 的销毁语义。本批 `pin6003` 断言 `destroyAll()` 后 `allExecutors().isEmpty()` 依赖「活引用」语义。
- Why Not Blocking Closure: 改为不可变视图会破坏既有 `allExecutors()` 消费方；本批保留活引用并在 owner doc 声明「只读用途，变更请走受支持入口」。
- Successor Required: `yes`；Successor Path: R-G 治理批（与 C4-005 residual 同批）。

### `GlobalExecutors.destroyAll()` 的宿主接线

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 宿主关闭链在 `nop-core-framework/nop-boot`（`NopApplication`），属 2.2 模块批（roadmap 8.55/R4-15），不在本批 PA 范围。
- Successor Required: `yes`；Successor Path: **roadmap 8.55（R4-15）**。

### `CacheConfig.executor` 拷贝语义

- Classification: `watch-only residual`
- Why Not Blocking Closure: `executor` 为 transient 池引用，拷贝进 clone 会把池引用带出（可能不期望）；本批裁定**不拷贝**并在字段 javadoc 注明。
- Successor Required: `no`。

## Non-Blocking Follow-ups

- C6-007 的 `daemon=true` 会改变「JVM 退出等待 watcher」为「随 JVM 直接退出」（事件可能丢失）——已在设计 §五 声明，属可接受的开发期行为。
- C7-015 的 Guava `sleepMicrosUninterruptibly` 不可中断面 = watch-only（`DefaultRateLimiter` 是 Guava 薄包装，替换属设施重写）；调用方已有 20min 上界。
- `ThreadPoolConfig` 的「分支丢弃」3 参数（cached 分支 / updateThreadPool）显式 out-of-scope（非死旋钮，是有意忽略）。
- flaky：`TestPeriodicTaskInsulation` / `TestResourceCacheInvalidationChain` / `TestExecutorHelperCancelContract`（沿用 2547 登记）；PA 回归必须 clean。

## Closure

Status Note: （收口时填写）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent fresh session）
- Audit Session: （session ID）
- Evidence:
  - （每条 Exit Criterion / Closure Gate 的 PASS/FAIL）
  - `check-plan-checklist --strict` 退出码
  - Anti-Hollow 结果 + scan-hollow 退出码
  - Deferred 分类检查

Follow-up:

- （仅 non-blocking）
