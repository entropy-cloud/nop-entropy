# C3 异步链路正确性 — nop-persistence-peri（WI 2.5）

- **审计批次**: 2026-10-19 单批次盲审初审（CRA WI 2.5，C3 维度）
- **审计方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` C3 正文 + F1–F11 缺陷族画像 + 附录 A 格式
- **基线**: `_tmp/cra-baseline-nop-persistence-peri.txt`「C3: async chains」节（L489–783）+ C4a 节（L461–475）圈定重点，并按 grep 全源码根人工补扫（`CompletableFuture|CompletionStage|then*|new Thread|ExecutorService|Scheduled*|Timer|ForkJoinPool|parallel*|CountDownLatch` 等关键字全集）

## 审计范围与口径

单批次覆盖全部 10 个源码根。各根审计面与结论如下：

| 源码根 | 异步面 | C3 结论 |
|---|---|---|
| `nop-dbtool/nop-dbtool-core`（7f/1,832） | **零异步入口**（关键字全集 grep 零命中；纯同步启动期代码） | 无站点，显式登记免审 |
| `nop-db-migration`（72f/3,877 手写） | **零异步入口**（同上） | 无站点，显式登记免审 |
| `nop-orm-migration`（20f/3,462） | 裸线程长驻流作业（IncrementalJobManager L176 `new Thread` + `env.execute`）+ 状态机编排 API | **重点审计**（运行期面，不适用降档），产出 C3-001/002/003 |
| `nop-cdc/nop-cdc-core`（1f/11） | `CdcConstants.java` 为空接口占位符（11 行，无成员）；`nop-cdc-debezium` 为纯 pom 包装（无 `src/` 目录） | **零审计面，显式登记** |
| `nop-nosql/nop-nosql-core`（19f/1,083） | 纯 CompletableFuture 接口声明（INosqlQueue/SessionStore/Counter/List/RateLimiter/ZSet/Ranking/Set/Lock），无实现逻辑 | 作为契约参照（用于判定 lettuce 实现偏差），无独立发现 |
| `nop-nosql/nop-nosql-lettuce`（18f/2,377） | 全量 `*Async` 实现走 Lettuce 异步命令（IO 线程回调）；PubSub 消息投递回调；DCL 单例 | **重点审计**（运行期面，不适用降档），产出 C3-004/005/006/007 |
| `nop-orm-drivers/nop-orm-tdengine`（6f/726） | `loadAsync/batchLoadAsync/batchExecuteAsync` 经 `FutureHelper.futureCall` 在**调用线程同步执行**后包装（FutureHelper.java:122–129：`task.call()` 直接调用，无池化）；TdMessageService 为显式 typed-rejection 占位（空能力集 + UnsupportedOperationException） | 伪异步，无回调线程面；登记为 CL-017（C4 契约线索），无 C3 发现 |
| `nop-orm-geo`（12f/700） | **零异步入口**（关键字全集 grep 零命中） | 无站点，显式登记免审 |
| `nop-orm-pdm`（9f/1,654） | **零异步入口**（同上） | 无站点，显式登记免审 |
| `nop-orm-rpc`（1f/913） | RpcEntityPersistDriver：3 个真异步契约方法（loadAsync/batchLoadAsync/batchExecuteAsync）+ 7 个 async-to-sync 桥接方法（`new CompletableFuture` + `whenComplete` + `FutureHelper.syncGet`） | 桥接模式 7 处同构（findFirst L632 / findList L705 / findAll L751 / count L811 / exists L839 / delete L867 / update L896），逐处核对 `completeExceptionally` 覆盖 err 分支与回调 try/catch——**全部正确**；`FutureHelper.syncGet` 无超时为平台约定（超时归 IRpcServiceInvoker，平台设施不重审）。会话状态在 RPC 客户端线程回调中变更、调用方经 future completion happens-before 可见——合规。无 C3 发现 |

**降档口径声明**：dbtool-core / db-migration / orm-geo / orm-pdm 四根不是「仅启动期单线程触达可条件化降档」，而是**零异步站点**（无任何 CompletableFuture/线程/池化构造，静态扫描与人工复核双重确认），无站点可降档。orm-migration（长驻流作业 + 编排状态机）与 nosql-lettuce（Redis 异步客户端）为运行期异步面，**不适用降档**，全量审计。

**异步入口清单**：
1. `IncrementalJobManager.startChain`：裸 Thread 长驻 `env.execute` 流作业（每目标分片一条），失败写入 `AtomicReference<Throwable> failure`
2. `OrmMigrationOrchestrator`：runIncremental/runVerify/confirmSwitch/completeSwitch/abort 编排 API（同步门禁 + 异步作业生命周期管理）
3. `RpcEntityPersistDriver`：`invokeRpc(...).thenAccept/whenComplete` 链 ×10（3 异步 + 7 桥接）
4. `TdEntityPersistDriver`：`FutureHelper.futureCall` 伪异步 ×3
5. `LettuceMessageService/LettuceHashOperations/LettuceSessionStore/LettuceQueue/LettuceListOperations/LettuceSetOperations/LettuceZSetOperations/LettuceCounter/LettuceRanking/LettuceRateLimiter/LettuceLock`：Lettuce 异步命令 compose/thenApply 链（约 90 处，单命令链为主，多命令链见发现）
6. `LettucePubSubService`：subscribe/unsubscribe + `RedisPubSubAdapter.message` IO 线程投递回调
7. `LettuceMessageService.getMessageService`：DCL 单例（volatile + synchronized，双检形式正确，非发现）
8. `MigrationJobStateStore.ensureTable`：DCL 建表（volatile + synchronized，形式正确，非发现）

**口径纪律第 10 条**：数量结论均由人工复现（grep + 逐文件精读），非仅引用基线行数。整维度存在 P1 发现（4 条），**不写终止信号**。

## 发现

### [C3-001] startFallbackChains 部分启动后异常路径无清理，重试产生不可停止的孤儿链作业

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:121-141, 146-188`；`OrmMigrationOrchestrator.java:185-204, 151-170`
- **证据片段**:
  ```java
  // IncrementalJobManager.java:134-139, 174-186
  for (int i = 0; i < targets.size(); i++) {
      PhysicalTable target = targets.get(i);
      String connectorName = targets.size() == 1 ? baseConnector : baseConnector + "-" + i;
      DebeziumConfig cdcConfig = buildCdcConfig(jobId, config, connectorName, tableIncludeList);
      started.add(startChain(jobId, cdcConfig, target, positionP0));
  }
  ...
  running.thread = thread;
  runningJobs.put(jobName, running);   // jobName = "...-" + jobId + "-" + tableName
  ```
- **严重程度**: P1
- **并发场景**: 编排调用线程与已启动链作业的裸线程（`env.execute` 长驻，含 Debezium 嵌入式 connector 与源库 binlog 连接）交错；startChain 内 `source.initializeState(null)`（L160-166）与 `sinkFactory.createSink` → `sink.initializeLedgerTable()`（JDBC DDL，OrmMigrationOrchestrator.java:509-521）均为可抛阻塞点。
- **触发时序**: T1：`runIncremental` 先持久化 phase=INCREMENTAL（Orchestrator L196-197），随后 startFallbackChains 循环启动链 0 成功——链 0 的 daemon 线程已开始消费源库 binlog 并向目标表写 2PC sink。T2：链 1 的 startChain 在 initializeState 或 initializeLedgerTable 抛出（如目标库 DDL 权限失败），异常传播出 startFallbackChains，`started` 清单丢失，调用方只拿到异常。交叠后果：链 0 继续以无人持句柄的方式运行；`resume()` 对 INCREMENTAL 相位是 no-op（Orchestrator L160-166 仅 return jobId），唯一的停止入口是显式调用 `abort()`——但没有任何代码路径自动触发。若运维以同一 jobId 重试 runIncremental，链 0-新 与链 0-旧 的 jobName 相同（`jobId + tableName`），`runningJobs.put`（L186）覆盖旧句柄——此后 `stopAll()` 只能遍历 map 中**新**句柄，旧链永久不可停止，双写者（各自 2PC epoch ledger）并存写同一目标表。
- **现状**: 多目标分片启动循环是「半个成功」形态：前 i 条已启动的后台作业在后续启动失败时不回滚、不停机、不向调用方移交句柄。
- **风险**: 孤儿链持续占用源库 binlog 连接并向目标表重放事件；重试后新旧链并存导致 2PC sink 语义（单一写者假设）被破坏，且 stopAll 失去对旧链的控制力（F2 + F8 复合）。
- **建议**: startFallbackChains 捕获循环内异常时对 `started`（以及 startChain 成功但尚未加入返回值的最后一条）逐个 `stop()` + `join` 后再抛出；jobName 加入启动序号或启动时间戳避免重试覆盖 `runningJobs` 条目；runIncremental 异常路径同步落状态行告警标记。
- **失效面枚举**: `startFallbackChains` 循环体（本条主站点，唯一调用 startChain 的生产路径）；`stopAll`（L201-206，依赖 map 完整性，被 put 覆盖削弱）；`OrmMigrationOrchestrator.abort`（L301-304，经 stopAll 间接受影响）；`RunningChainJob.stop/join`（句柄丢失后不可达）；`MultiSinkTopologyProbe.probe` 路径（fan-out 形态未启用，同构风险留给启用时——逐一登记，不豁免）。
- **信心水平**: 确定
- **误报排除**: 非低频路径粗锁类误报：这是错误路径资源/写者泄漏，且重试覆盖句柄的具体交错可逐步复现；也不是「已声明边界」——javadoc（L198-200）声明 stopAll 由编排器在 ABORT/SWITCH 收敛后调用，恰证明清理责任在本组件而非调用方。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（同案：本单元 C1-005 并发双启同名覆盖面 P1、C1 批次 CL-007 阶段卡死线索、round4 扩展 C3-001 relocate 确定性同名面——同根修复合并计一次工单）。**复核修正**：原文「同一 jobId 重试 runIncremental 覆盖句柄」的顺序重试触发被相位机阻断（`MigrationPhase.ALLOWED` 实证 INCREMENTAL→INCREMENTAL 非法迁移，重试即抛 ERR_MIG_PHASE_TRANSITION_INVALID）；「链 0 无人持句柄」表述过强——已启动链在 `runningJobs` 仍留句柄、abort()→stopAll() 可达。P1 维持依据是 round4 扩展面的确定性触发（relocate 多源 qs 单次成功启动内 jobName 同名相撞：`IncrementalJobManager:174` 丢 querySpace + `TargetShardMapping.relocate:166` 产出同名异 qs 目标集，put 覆盖后旧链不可停——代码级复核成立）+ 部分启动失败后无自动清理且 runVerify 可带缺链集合继续推进（INCREMENTAL→VERIFY 合法迁移）。

### [C3-002] 链作业失败仅写入无人消费的 AtomicReference，lag 门禁对死链盲视

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:174-186, 208-243`；`OrmMigrationOrchestrator.java:210-251`；`IncrementalLagProbe.java:52-76`
- **证据片段**:
  ```java
  // IncrementalJobManager.java:176-183（失败记录）
  Thread thread = new Thread(() -> {
      try {
          env.execute(jobName);
      } catch (Throwable e) {
          running.failure.set(e);      // 唯一观测出口是 getFailure()
      }
  }, jobName);
  // L230-233 javadoc 声明：「fail-fast：编排器轮询到 failure 即阶段失败」
  ```
- **严重程度**: P1
- **并发场景**: 链作业裸线程（写入 failure）与编排调用线程（仅经 IncrementalLagProbe 间接观测）之间；`getFailure()` 与 `join(long)` 在 main 与 test 源码中均为**零调用方**（grep 复现：仅 Orchestrator L200 调 startFallbackChains、L304 调 stopAll）。
- **触发时序**: T1：链线程内 `env.execute` 启动即失败（如 Debezium 凭据错误/连接器白名单拒绝），异常写入 `failure`，daemon 线程退出。T2：编排线程稍后执行 `runVerify` → `probe.awaitLagZero`（IncrementalLagProbe L52-57）——offset 指纹与 ledger 计数在静默期前后**均无变化**（链死了自然静止），快照相等 → lag=0 门禁**通过**。交叠后果：写门冻结、VERIFY 对着一个从未重放过任何增量的死链照常推进；javadoc 声明的「轮询 failure 即阶段失败」机制在编排器中不存在。
- **现状**: 异步链异常被记录但从未被观测（F9 静默吞异常变体：不吞日志、吞**传播路径**）；fail-fast 契约只有声明没有实现。
- **风险**: 死链不可区分于健康空闲链；最终数据安全网只剩 ConsistencyVerifier 的失配比对——若静默期内源库恰无新写，作业可带死链走完 DONE，增量机制的正确性从未被验证；同时死链线程与 connector 资源滞留。
- **建议**: runVerify（或独立守护）先遍历 `jobs` 调 `getFailure()`，非空即 ERR_MIG 增量链失败；`awaitLagZero` 增加链存活前置校验；或在 `RunningChainJob` 内置完成回调直接把失败抛回编排 future。
- **失效面枚举**: failure 写入点唯一（IncrementalJobManager L180）；观测面清单：`getFailure()`（0 调用方）、`join(long)`（0 调用方）、`runVerify` 的 lag 门禁（间接、对死链盲视）、`OrmMigrationJobStatusBizModel`（只读状态行，不含链存活）；同族对照——MultiSinkTopologyProbe 无异步失败面，不适用。
- **信心水平**: 确定（零调用方为静态可证事实；盲视交错为逻辑必然）
- **误报排除**: 不是「错误处理维度越界」——此处报的是**异步失败传播链断裂导致状态机不一致**（C3 范围），非错误码规范；也不是平台设施重审（failure 机制是本模块自建）。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（同案：本单元 C7-001 同缺陷 P1、C1 批次 CL-006 与 C8 批次 CL-023 线索——跨维度重复登记，合并计一次工单）。证据复核：getFailure/join 生产+测试零调用方 grep 复现成立；L230 javadoc fail-fast 契约无实现成立；死链与健康空闲链在 lag 门禁下不可区分的逻辑必然成立。

### [C3-003] completeSwitch 成功路径不停链作业：DONE 后幽灵写者持续重放源事件进已翻转的目标表

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:198-206`（javadoc 契约）；`OrmMigrationOrchestrator.java:279-290`（completeSwitch）、`296-313`（abort 有 stopAll）
- **证据片段**:
  ```java
  // IncrementalJobManager.java:198-200
  /**
   * 停止全部链作业（ABORT/SWITCH 收敛后由编排器调用）。
   */
  public void stopAll() { ... }
  // OrmMigrationOrchestrator.java:279-290 completeSwitch：写门开启 → phase=DONE，无 stopAll 调用
  ```
- **严重程度**: P1
- **并发场景**: 已完成的迁移主流程（编排调用线程）与仍在运行的链作业裸线程（消费源库 binlog → UPSERT 目标表）；写门开启后 ORM 业务写线程与链作业 sink 线程并发写同一物理表。
- **触发时序**: T1：VERIFY 通过 → confirmSwitch 翻转 nop.orm.shard → completeSwitch 开写门、phase=DONE。此时 `runningJobs` 非空，链线程仍持有源库 binlog 连接。T2：残留的旧应用实例（滚动重启收敛前）或旁路写入源表 → CDC 事件被链作业消费 → UPSERT 进已承接生产写流量的目标表。交叠后果：链的重放写与 ORM 写 last-writer-wins，陈旧源值覆盖新值——VERIFY 已过，无任何门禁再拦；线程/连接/嵌入式 connector 按「每次迁移 × 分片数」在长驻 JVM 中累积不释放（仅 abort 路径释放）。
- **现状**: 清理不对称（F8）：stopAll 的 javadoc 明文「ABORT/SWITCH 收敛后由编排器调用」，实现中只有 abort() 调用，成功收敛路径（completeSwitch→DONE）缺失对称调用。
- **风险**: 主成功路径资源泄漏（daemon 线程 + connector + 源库连接持续累积）；切换收敛窗口内的幽灵写者可静默污染生产表——该后果发生在数据面，超出单纯资源泄漏。
- **建议**: completeSwitch 在开写门前先 `stopAll()`（或 confirmSwitch 翻转配置后立即停链）并 join 静默；把「SWITCH 收敛必停链」落为编排器代码路径而非 javadoc 注释。
- **失效面枚举**: 停链入口全清单：`abort()`（已调用 stopAll，正确）；`completeSwitch()`（**缺失**，本条）；`resume()` 对 INCREMENTAL/SWITCH 相位 no-op（不适用）；`OrmMigrationJobStatusBizModel`（只读，无停链职责）；测试面 TestMigrationPipelineE2E L98 调 completeSwitch 后同样不停链（同构缺陷，登记不豁免）。
- **信心水平**: 确定（代码路径可证；幽灵写后果依赖滚动收敛窗口内存在源端残写，属很可能）
- **误报排除**: 非误报校准中的「已声明边界」——边界声明（stopAll 调用时机）与实现相反；也不是平台设施问题：stopAll 责任链完全在本模块两个类之间。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（关联本单元 C7-006 中止面不完整——互补非重复：C7-006 报 abort 面缺口，本条报成功收敛面缺口；关联 C7-004 写门本 JVM 语义）。复核：stopAll 全仓唯一生产调用方=abort（grep 复现）；completeSwitch 无停链、javadoc 与实现相反均成立。小勘误：失效面枚举引用的 TestMigrationPipelineE2E L98 用例未启动增量链，「同构缺陷」表述对测试面不成立（无链可停），生产路径结论不受影响。

### [C3-004] PubSub cancel 与 subscribe 非对称加锁：竞态窗口内注册的订阅者被静默孤儿化，永久丢消息

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:72-94, 134-139, 189-200`
- **证据片段**:
  ```java
  // L72：subscribe 整体 synchronized；L190：cancel 无锁
  public synchronized IMessageSubscription subscribe(String topic, IMessageConsumer listener, ...) {
      ...
      SubscriptionEntry entry = subscriptions.get(topic);   // L76
      if (entry == null) { ... } else {
          entry.addListener(listener);                       // L91：补挂到可能已被移除的 entry
      }
      ...
  }
  public void cancel() {                                      // L190-199：未 synchronized
      cancelled = true;
      entry.removeListener(listener);
      if (entry.isEmpty()) {
          ...
          conn.sync().unsubscribe(topic);                    // L196
          subscriptions.remove(topic, entry);                // L198
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: 调用方线程 X（注册新消费者 subscribe）与调用方线程 Y（取消既有消费者 cancel）并发；共享单条 pub/sub 连接（L96-105 DCL 单连接）。
- **触发时序**: T1：X 进入 synchronized 的 subscribe，L76 读到 topic 现存 entry E（仅含监听者 B），随后暂停。T2：Y 执行 B 的 cancel：removeListener(B) → listeners 空 → `unsubscribe(topic)` + `subscriptions.remove(topic, E)` 完成，Redis 端该 topic 已退订。T1 恢复：走 L90 else 分支，把新监听者 C 挂到**已从 map 摘除且 Redis 已退订**的孤儿 E 上，返回「正常」的 PubSubSubscription。交叠后果：C 的订阅对象状态为 active、无任何报错或日志，但永远收不到消息——静默丢消息。
- **现状**: subscribe 用 `synchronized(this)` 保护，而 cancel/suspend/resume 三个订阅生命周期变更入口全部绕开同一把锁，锁面不对称。
- **风险**: 消息面静默失效（fire-and-forget 语义下无重放补偿）；孤儿 entry 及其监听者滞留内存。
- **建议**: cancel/suspend/resume 与 subscribe 收敛到同一 `synchronized(this)`（低频管理操作，粗锁无争用代价）；或 cancel 的 isEmpty 判定与 unsubscribe/remove 改为对 entry 的互斥（synchronized(entry)）并在 subscribe else 分支二次校验 `subscriptions.get(topic) == entry`。
- **失效面枚举**: 订阅生命周期变更入口逐一登记：`subscribe`（L72-94，已加锁）；`PubSubSubscription.cancel`（L189-200，**未加锁**，本条）；`suspend`（L213-218，未加锁——不触发 unsubscribe，孤儿窗口后果较轻，但与 subscribe 并发时同样绕锁，登记同族）；`resume`（L221-226，未加锁，add-only 后果轻，登记同族）；`removeListener`（L134-139，被上述入口共用的底层变更点）。对照正面样例：subscribe 的 entry 创建路径（L81-89）对 sync().subscribe 失败做了回滚（removeListener + remove），说明本类已有原子性自觉，cancel 路径为漏网兄弟。
- **信心水平**: 很可能（窗口交错逻辑可完整枚举；未做运行时复现）
- **误报排除**: 非原语误用（CHM/synchronized 单点均正确，复合序列失衡才是问题）；非「已声明边界」——IMessageSubscription SPI 与本类 javadoc 均未声明 cancel 与 subscribe 不可并发。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（同案：本单元 C1-011 同站点同根 P1、C7 批次 CL-021 线索——跨维度三重登记，修复合并计一次工单；C1-011 另含 suspend/resume 复活双投递面，合并后以并集为准）。时序复核：subscribe 整体 synchronized（L72）与 cancel/suspend/resume 全部无锁的不对称、L76 读 entry → L91 补挂孤儿 entry 的交错枚举成立；类 javadoc 的 fire-and-forget 声明覆盖传输层丢失、不覆盖「subscribe 正常返回的活订阅永不投递」，误报排除成立。

### [C3-005] PubSub 投递回调在 Lettuce IO 线程同步执行共享单连接，慢/异常消费者阻断全部 topic 投递并可中断扇出

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:145-161, 96-105`
- **证据片段**:
  ```java
  // L145-161：RedisPubSubAdapter 回调，运行在 Lettuce IO event-loop 线程
  public void message(String channel, Object message) {
      if (!topic.equals(channel)) return;
      IMessageConsumeContext context = new PubSubConsumeContext();
      for (IMessageConsumer consumer : listeners.keySet()) {
          try {
              consumer.onMessage(channel, message, context);
          } catch (Exception e) {
              consumer.onConsumeFailure(new ConsumeFailure(channel, channel, message, 0, e));
          }                                       // onConsumeFailure 再抛则逃逸出循环
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: Lettuce IO event-loop 线程（投递回调执行线程）vs 各业务消费方预期线程；单条共享 pub/sub 连接承载全部 topic。
- **触发时序**: T1：topic-T1 消息到达，`message()` 在 IO 线程同步调用消费者 A.onMessage；A 内部做慢调用（如同步 JDBC，耗时 30s）。T2：期间 topic-T2 的消息在连接上排队——同一 IO 线程被 A 阻塞，T2 的投递被队头阻塞。交叠后果（失败面）：A.onMessage 抛异常 → 走 onConsumeFailure（本身合理，WI6 契约）；但若 **onConsumeFailure 也抛出**（用户处理器 bug），异常逃逸 for 循环 → 排在 A 之后的消费者 B 对本条消息**永不投递**（扇出半途而废），异常进入 Lettuce 派发层（由其记日志）。
- **现状**: 回调线程契约未声明也未隔离：消费方代码在 Redis IO 线程上执行，无执行器卸载、无逐消费者异常屏障（catch 只包 onMessage 不包 onConsumeFailure）。
- **风险**: 一个消费者的不当实现退化为全连接级投递停顿（跨 topic 放大）与同 topic 扇出截断；故障面与消费者数量、topic 数量正相关。
- **建议**: message() 内对每个消费者独立 try/catch（onConsumeFailure 调用也纳入，二次失败仅记日志）；中期引入共享执行器（复用 `io.nop.commons.concurrent.executor.IThreadPoolExecutor`）卸载 onMessage，IO 线程只做派发；在类 javadoc 显式声明投递线程契约。
- **失效面枚举**: 投递入口唯一（SubscriptionEntry.message L145-161，全类仅此一处扇出点）；受影响路径：所有 subscribe 的 topic 共享单连接（getOrCreateConnection L96-105）；对照同族——`sendAsync`（L65-69）走 round-robin 普通连接不经过该回调，不受影响，逐一排除。
- **信心水平**: 确定（执行线程模型由 Lettuce pub/sub 派发机制决定；扇出截断为代码结构必然）
- **误报排除**: 不是「回调里抛异常会杀死调度线程」的泛化担忧——Lettuce 会兜底记日志，此处报的具体失效面是**同消息后续消费者被截断**与**单连接队头阻塞**，均为可指认的结构后果，非风格问题。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（同案：本单元 C8-001 P1 / C7-010 P2 同缺陷三重登记——严重度分歧 P1/P2/P2 留 7.1 合并裁定；本维度按 fire-and-forget 已声明语义 + 仓库内未发现生产接线维持 P2）。复核：try 只包 onMessage、onConsumeFailure 再抛逃逸 for 循环截断扇出、单连接共享队头阻塞，代码结构均成立。

### [C3-006] computeIfAbsent/getAndSet 为非原子「读-改-写」compose 链，与同类原子兄弟（HSETNX/Lua）标准不一

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java:62-70, 156-166`；`LettuceHashOperations.java:43-52, 115-118`
- **证据片段**:
  ```java
  // LettuceHashOperations.java:44-51
  return async().hget(key, field).thenCompose(value -> {
      if (value == null) {
          value = mappingFunction.apply(field);
          return async().hset(key, field, value).thenApply(v -> ret);  // HGET→HSET 两步非原子
      }
      return CompletableFuture.completedFuture(value);
  });
  // 对照：同类 putIfAbsentAsync L105-107 用 HSETNX；removeIfMatchAsync L136-140 用 Lua CAS
  ```
- **严重程度**: P2
- **并发场景**: 多个调用线程/回调线程对同一 key+field 并发调用（Redis 命令各自原子，但 HGET 与 HSET 之间无互斥）；round-robin 连接池下两条命令还可能落在不同连接。
- **触发时序**: T1：线程 X computeIfAbsent("k")：HGET 返回 null，开始执行 mappingFunction（耗时）。T2：线程 Y 同 key computeIfAbsent：HGET 亦为 null，执行**不同结果**的 mapping 并 HSET 写入。T1 完成，HSET 覆盖 Y 的值。交叠后果：Redis 最终保留 X 的值，而 X、Y 两个 future 分别返回各自计算值——调用方持有与存储不一致的视图；mapping 非幂等（如计数、发号）时产生重复副作用。getAndSetAsync（L115-118）同构：两次并发调用都返回初值 v0，其中一次覆盖的中间值从返回值面丢失。
- **现状**: 4 处读-改-写链未用原子原语，而同文件同类操作（putIfAbsent=HSETNX、removeIfMatch/putIfAbsentOrMatch=Lua CAS、SessionStore.setField=Lua exists-guard）均刻意原子化——F3 族内标准不齐。
- **风险**: 依赖 ConcurrentMap.computeIfAbsent 惯例语义的调用方拿到偏弱保证；并发同 key 场景下视图分叉与副作用重复。接口 javadoc 未声明原子性，故不定 P1。
- **建议**: hash 版 computeIfAbsentAsync 复用 HASH_PUT_IF_ABSENT_OR_MATCH 的 Lua 形态改造（脚本内 HGET/HSET 原子化）；string 版用 `SET k v NX GET`（Redis 7 setGet，getAndSetExAsync L302-309 已在用 setGet，可对齐）；或在 INosqlService javadoc 显式声明非原子边界。
- **失效面枚举**: 非原子站点全清单：LettuceMessageService.computeIfAbsent（L62-70，sync 版同构竞态）、LettuceMessageService.computeIfAbsentAsync（L156-166）、LettuceHashOperations.computeIfAbsentAsync（L43-52）、LettuceHashOperations.getAndSetAsync（L115-118）。原子兄弟逐一核对：MessageService.putIfAbsentAsync（setnx，原子）、getAndSetAsync（getset，原子）、Hash putIfAbsentAsync（hsetnx，原子）、Hash removeIfMatchAsync（Lua）、putIfAbsentOrMatchExAsync（Lua）、SessionStore.setFieldAsync（Lua SESSION_SET_FIELD）——均无本问题，不豁免也不重复立项。
- **信心水平**: 确定（竞态结构代码可证；实际危害取决于调用方并发度与 mapper 幂等性）
- **误报排除**: 不是「看到共享存储就报线程不安全」——Redis 命令级原子性无误，报告对象是**复合操作**的原子性缺口，且有同类原子化先例作对照基准；接口未声明原子性，已按校准降 P2 而非 P1。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（同案：本单元 C1-012 同缺陷 P2——跨维度重复登记，合并计一次工单；关联 nop-persistence-rw-CL-403 同 API 面相邻、缺陷形态不同）。复核：4 处非原子站点与 6 处原子兄弟（setnx/getset/Lua/psetex/SET PX NX/setGet）逐一对照成立。

### [C3-007] 写入→PEXPIRE 两步 compose 链：第二跳丢失即产生无 TTL 常驻键（half-success 无恢复语义）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceSessionStore.java:62-73`；`LettuceHashOperations.java:194-197, 210-217, 229-237`
- **证据片段**:
  ```java
  // LettuceSessionStore.java:64-70（会话键，高频路径）
  return async().hmset(key, data)
          .thenCompose(v -> {
              if (ttlMs > 0) {
                  return async().pexpire(key, ttlMs);   // 第二跳丢失 → TTL-less hash 永驻
              }
              return CompletableFuture.completedFuture(true);
          })...
  // 对照同文件 setFieldAsync L83-84 注释：「writing a field into a non-existent (e.g. expired)
  // session key would resurrect it as a TTL-less hash」→ 该兄弟已改 Lua 原子化
  ```
- **严重程度**: P2
- **并发场景**: 调用线程与 Lettuce IO 线程之间的两跳命令链；失效发生在 hmset/hset/hsetnx 成功提交后、pexpire 提交前（连接断开、超时、客户端崩溃）。
- **触发时序**: T1：setAsync 执行 HMSET 成功（Redis 端 hash 已落，此时键**无 TTL**）。T2：连接抖动，PEXPIRE 未送达或结果丢失，future 以异常完结——调用方看到失败可能重试，但**服务端残留的无 TTL 键无人清理**。交叠后果：会话存储场景下，每次此类失败永久泄漏一个 session hash（F4 无界账本 Redis 变体）；putIfAbsentExAsync（hsetnx→pexpire）失败窗口则产生「占位成功但永不过期」的键，语义近似分布式锁丢租约。
- **现状**: 4 处两步 TTL 链均为「部分成功无恢复」；同族原子化改造只覆盖了 setFieldAsync（Lua）与 string 键 putExAsync（PSETEX 原子单命令，MessageService L270），hash/会话键两步链被留下。
- **风险**: 长时运行 Redis 中无 TTL 残留键单调累积（触发频率=链路抖动率 × 写 TTL 键频率）；占位型残留会改变 putIfAbsent 后续语义（永远 absent=false）。
- **建议**: 会话键与 hash TTL 写入下沉为 Lua 脚本（HMSET+PEXPIRE 原子化，复用 SESSION_SET_FIELD 的脚本基建与 evalScript 的 NoScript 回退）；至少在失败路径补偿：future 异常时对 key 补发一次 DEL/PEXPIRE 补救尝试。
- **失效面枚举**: 两步链站点全清单：SessionStore.setAsync（L62-73）、Hash putExAsync（L194-197）、Hash putIfAbsentExAsync（L210-217）、Hash getAndSetExAsync（L229-237）；较轻变体：Hash getExAsync（L201-206，仅 TTL 刷新丢失，登记不立项）。原子兄弟逐一核对：MessageService.putExAsync（PSETEX）、SessionStore.setFieldAsync（Lua）、Hash putIfAbsentOrMatchExAsync（Lua）、MessageService.putIfAbsentExAsync（SET PX NX 单命令，L284-292）——无本问题。
- **信心水平**: 确定（失效窗口为 Redis 复制/连接语义下的标准两步写风险）
- **误报排除**: 非误报校准项：这不是「有界声明的缓存/队列」类已声明行为，也不属「flush≠fsync」持久化模型问题（TTL 本身是服务端契约，本条报的是多命令组装打破该契约）；同族已有原子化先例证明该窗口被视为缺陷而非边界。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜已知未修复（session setAsync 面同案：2026-05-20-deep-audit-nop-nosql-post-plan23 D03-02 已登记 HIGH 且未修，live 代码两步形态未变；hash putExAsync/putIfAbsentExAsync/getAndSetExAsync 三入口为本轮新面。同案：本单元 C1-013 全族 P2 / C7-011 session 面 P2——合并计一次工单）。复核：4 处两步链站点、setFieldAsync Lua 兄弟的 resurrect 注释、string 键原子兄弟（PSETEX/SET PX NX）对照均成立。

## 维度集外线索（overlay C2/C4/C5/C9）

### [nop-persistence-peri-CL-015]（C5）lettuce 迭代类操作全量物化，无分页/上限

`LettuceListOperations.forEachItemAsync`（LettuceListOperations.java:124-133）以 `lrange(key, 0, -1)` 一次性拉取整个 list 进内存后逐个回调；`LettuceHashOperations.forEachEntryAsync`（LettuceHashOperations.java:170-176）与 `LettuceSessionStore.getAsync`（LettuceSessionStore.java:34-43，另复制一份 HashMap）以 `hgetall` 全量物化。对照兄弟实现 `LettuceMessageService.forEachEntryAsync`（LettuceMessageService.java:227-261）已用 SCAN limit(100) 分页——同族内分页标准不齐。大 list/hash 下单次调用即可造成 MB 级分配与回调线程长占压（与 C3-005 的 IO 线程问题叠加）。建议按 MessageService 的 scan 链形态统一。未核对本模块 owner doc 是否已声明规模包络，若已声明大集合边界请按口径标注。

### [nop-persistence-peri-CL-016]（C9）LettucePubSubService doStop 后 restart 复用已关闭连接，订阅面静默死亡

`doStop`（LettucePubSubService.java:112-117）关闭 pubSubConnection 但既不置空字段也不清 `subscriptions` map、不摘除 adapter。LifeCycleSupport 支持 start/stop/start 循环：重启后 `getOrCreateConnection`（L96-105）因字段非 null 直接返回**已关闭**连接，首个 subscribe 的 `sync().subscribe` 抛异常（走 L83-89 回滚），后续对该 topic 的 subscribe 走 else 分支静默挂在旧 entry 上（永不投递）。当前 getMessageService 单例形态下 restart 不易触达，但生命周期契约上是缺陷。建议 doStop 中置空 pubSubConnection + 清空 subscriptions + removeListener。

### [nop-persistence-peri-CL-017]（C4）TdEntityPersistDriver 异步契约方法在调用线程同步执行，无池化卸载

`loadAsync/batchLoadAsync`（TdEntityPersistDriver.java:84-95, 178-205）与 batchExecuteAsync（L128-160）经 `FutureHelper.futureCall` 实现，而该原语在调用线程直接 `task.call()` 后包装（nop-api-core FutureHelper.java:122-129）——方法名与 IEntityPersistDriver 异步契约暗示并发卸载，实际是调用线程阻塞 JDBC。正确性无虞（无回调竞态，会话变更留在调用线程），但批量加载大集合时阻塞时长与 Rpc 驱动（真异步）行为不一致，属执行模型声明缺口。建议统一为真异步或在该驱动 javadoc 显式声明同步执行语义。

## 维度复核结论（WI 2.5 C3 独立复核，2026-10-18）

> 复核人：C3 维度独立复核员。逐条回到 live code 验证证据 file:line 真实性、T1/T2 时序可具体化性与严重度校准；现状对照 `ai-dev/audits/cra-index.md`（WI 1.1–2.4 台账查重）、同单元 C1/C6/C7/C8 批次（跨维度同案查重）、历史审计（`2026-05-20-deep-audit-nop-nosql-post-plan23/`、WI 1.2 stream-state-connectors 对 `NopStreamOffsetBackingStore` 载体的边界声明）。本节不修改原发现条目，仅在条目末尾追加复核状态行；round2/3/4 条目的状态行写在各 round 文件内，判定表集中于此。

### 逐条判定表（C3-001…012）

| ID | 标题（缩写） | 原判 | 复核判定 | 现状标注 | 同案指针 |
|---|---|---|---|---|---|
| C3-001 | startFallbackChains 部分启动无清理 + runningJobs 覆盖 → 不可停孤儿链 | P1 | **保留 P1**（触发时序修正：同 jobId 顺序重试被 `MigrationPhase.ALLOWED` 阻断；「无人持句柄」过强——runningJobs 留有句柄、abort 可达；P1 维持依据 = round4 扩展 relocate 多源 qs 确定性同名覆盖面 + 部分启动后 runVerify 可带缺链集合推进） | 新发现 | C1-005(P1)、C1-CL-007、扩展 C3-001 |
| C3-002 | 链失败写入无人消费的 AtomicReference，lag 门禁对死链盲视 | P1 | **保留 P1**（零调用方 grep 复现成立；fail-fast 契约无实现成立） | 新发现 | C7-001(P1)、C1-CL-006、C8-CL-023 |
| C3-003 | completeSwitch 成功路径不停链：DONE 后幽灵写者 | P1 | **保留 P1**（stopAll 唯一调用方=abort 成立；javadoc 与实现相反成立。小勘误：引用的 E2E 用例未启动链，测试面「同构缺陷」不成立，生产结论不受影响） | 新发现 | 关联 C7-006（互补）、C7-004 |
| C3-004 | PubSub cancel 与 subscribe 非对称加锁 → 活订阅静默孤儿化 | P1 | **保留 P1**（交错枚举成立；fire-and-forget 声明不覆盖活订阅失效，误报排除成立） | 新发现 | C1-011(P1)、C7-CL-021 |
| C3-005 | PubSub 投递回调在 IO 线程同步执行：队头阻塞 + 扇出截断 | P2 | **保留 P2**（结构后果成立；同案严重度分歧留 7.1 裁定） | 新发现 | C8-001(P1)、C7-010(P2) |
| C3-006 | computeIfAbsent/getAndSet 非原子读-改-写 | P2 | **保留 P2**（4 站点 + 6 原子兄弟对照成立） | 新发现 | C1-012(P2) |
| C3-007 | 写入→PEXPIRE 两步链 → 无 TTL 常驻键 | P2 | **保留 P2**（4 站点 + 原子兄弟对照成立） | **已知未修复**（session 面 = 历史审计 D03-02，live 未变） | C1-013(P2)、C7-011(P2)、2026-05-20-nosql D03-02 |
| C3-008 | lag 探测 connector 名与链注册名错位：M>1 下 offset 观测退化常量 | P1 | **保留 P1**（键派生三层复核成立；强化证据：fromMap 强制 connector-name 非空，生产面即 M>1 后缀错位、必然触发；与 WI 1.2 C3-006/C3-014 共享载体但缺陷不同，非重复） | 新发现 | 关联 C7-001、C1-005；同族模式见 C3-009/010 |
| C3-009 | relocate 路由 querySpace 硬编码空串：增量 filter 恒 false + FULL 写回源侧 | P1 | **保留 P1**（三处硬编码 + equals 恒 false + verifier 主模板接线 + e2e relocate 零覆盖全部复核成立；补充：多源 qs 下 FULL 后果加重为跨源行合并写同一 default 表） | 新发现 | 同族模式统一修复（C3-008/010/011），非同案 |
| C3-010 | lag 门禁 ledger 观测 qs=状态行空间 ≠ sink 提交 qs | P2 | **保留 P2**（两侧 qs 派生对照成立；loud/silent 两型后果成立；与 C7-008 namespace 面正交、须协同设计） | 新发现 | 关联 C7-008（正交双面） |
| C3-011 | FullCopyBatchJob taskKey 丢 querySpace：批身份键跨表串用 | P2 | **保留 P2**（taskKey 派生 + 平台 (taskName,taskKey) 唯一键/COMPLETED 闸门 + 三条件触发面成立） | 新发现 | 扩展 C3-001（同根统一修复） |
| C3-012 | P0 位点/订阅面/源表全集三方标识无对齐校验 | P2 | **保留 P2**（三方无交叉校验成立；owner doc 边界清单确无单服务器源声明；qs 数组必填成立；条件性计价合理） | 新发现 | 无 |

**C3-009 与 C3-008 非同根论证裁定**：**成立，保留双条**。C3-008 是 offset registry 的 connector **名**维度错位（探测侧裸配置名 vs 注册侧 `base-i`，站点 = runVerify:220 / IncrementalJobManager:136，触发场景 = rescale M>1）；C3-009 是路由/写面的 **querySpace** 维度错位（站点 = route:41 / routeRow:236 / routeShardValue:252，触发场景 = relocate）。二者站点不同、场景不同、修复点不同（键派生同源化 vs 路由改用 mapped qs），互不闭合——任一修复不消除另一条。同属 round4 总结的「以 tableName/裸配置名代替完整 PhysicalTable 身份」**模式族**，修复方案应统一设计（round4 回归面提示表 1-5），但按「根因 + 失效层」口径不并案。

### 跨单元/跨维度查重结论

- 与 WI 1.1–2.4 cra-index P1 台账逐条比对：**零跨单元重复立项**。C3-008 与 WI 1.3 C3-008 同号异单元（region 重启 initializeState REPLACE）、与 WI 1.2 C3-006/C3-014 共享 `NopStreamOffsetBackingStore` 载体——WI 1.2 已显式声明该载体仅作证据锚点，本批缺陷锚定 orm-migration 使用侧，无重复。
- 与本单元 C1/C6/C7/C8 批次比对：**6 组跨维度同案**（见判定表同案列）——C3-001↔C1-005、C3-002↔C7-001、C3-004↔C1-011、C3-005↔C8-001/C7-010、C3-006↔C1-012、C3-007↔C1-013/C7-011。盲审口径下多维度各自登记同站点缺陷属预期；**合并裁定时应各计一次工单**（同案并集为准），C3 侧不撤条，以本表指针供 7.1 汇总。
- 维度集外线索查重：CL-015↔C8-005、CL-016↔C7-009、CL-017 与 C8-003/C8-004 相邻（chunk 上界面）——线索登记与发现条目分账，处置归对应维度，C3 侧不重复计数。

### 高风险条目（P1）子项复核说明

1. **C3-001**：子项①「异常路径不清理」成立但后果修正（链仍注册于 runningJobs、abort 可停；真实缺口 = 无自动清理 + 相位卡死 + runVerify 可带缺链集合推进）；子项②「重试覆盖句柄」顺序重试面证伪（相位机阻断），由 round4 扩展的 relocate 确定性同名面接替（`startChain:174` jobName 丢 qs + `relocate:159-167` 同名异 qs 目标集，代码级复核成立）；子项③「并发双启覆盖」归 C1-005（已 P1 在案）。三条子项同根（jobName 非单射 + 启动循环无回滚），统一修复。
2. **C3-002**：零调用方为静态可证事实（grep 全模块 main+test）；「死链 = 指纹静止 = 门禁通过」在 M==1 键对齐场景下同样成立（与 C3-008 的 M>1 键错位面正交叠加）；javadoc L230 的 fail-fast 承诺无任何编排代码兑现。
3. **C3-003**：stopAll 调用方普查（全仓 grep）= abort 一处；completeSwitch/confirmSwitch/runVerify/resume 均无停链路径；javadoc「ABORT/SWITCH 收敛后由编排器调用」与实现相反成立。幽灵写后果依赖收敛窗口内源端残写（条件面），资源滞后面（daemon 线程 + binlog 连接 + 嵌入式 connector 按迁移次数累积）为成功路径无条件面。
4. **C3-004**：交错序列逐步复核成立（X 持锁读 entry → Y 无锁退订+摘除 → X 补挂孤儿 entry）；后果「active 订阅零投递零报错」为代码结构必然。C1-011 的 suspend/resume 复活面为并集补充。
5. **C3-008**：键派生三层（配置裸名 → manager `base-i` 后缀 → instanceName 并行度后缀惰性）逐一复核；`forConnector` computeIfAbsent 建空条目语义成立（小勘误：空条目每 base 名只泄漏一次，非每次 runVerify）；fromMap 强制 connector-name 非空使「null 名 → no-incremental 支路」仅 Builder/测试路径可达，生产面收敛为 M>1 后缀错位、必然触发。
6. **C3-009**：relocate 分支三处 `""` 硬编码、`PhysicalTable.equals` 全字段等值恒 false、runIncremental filter 消费点、ensureTargetTablesReady/sink/verifier 三处 mapped-qs 契约对照、e2e 仅 rescale+空 qs（AbstractMigrationE2ETestCase rescaleConfig 注释自证）全部复核成立；多源 qs 下 FULL upsert 合并写面为复核新证。

## 最终保留项

| ID | 标题 | 最终严重度 | 现状标注 | 同案指针 |
|---|---|---|---|---|
| C3-001 | startFallbackChains 部分启动无清理 + jobName 非单射覆盖 → 不可停幽灵链 | P1 | 新发现 | C1-005、C1-CL-007、扩展 C3-001（同根合并修复） |
| C3-002 | 链作业失败仅写入无人消费的 AtomicReference（fail-fast 契约无实现） | P1 | 新发现 | C7-001、C1-CL-006、C8-CL-023（同案合并） |
| C3-003 | completeSwitch 成功路径不停链：DONE 后幽灵写者 + 成功路径资源滞留 | P1 | 新发现 | 关联 C7-006/C7-004（互补不并案） |
| C3-004 | PubSub cancel/subscribe 非对称加锁 → 活订阅静默孤儿化 | P1 | 新发现 | C1-011、C7-CL-021（同案合并，并集含 suspend/resume 面） |
| C3-008 | lag 探测 connector 名与链注册名错位：M>1 下 offset 观测结构性失效 | P1 | 新发现 | 关联 C7-001/C1-005；与 C3-009/010/011 同模式统一修复 |
| C3-009 | relocate 路由 querySpace 硬编码空串：增量 100% 丢事件 + FULL 写回源侧 | P1 | 新发现 | 与 C3-008/010/011 同模式统一修复 |
| C3-005 | PubSub 投递回调 IO 线程内联：队头阻塞 + 扇出截断 | P2 | 新发现 | C8-001(P1)/C7-010(P2)（同案，严重度留 7.1 裁定） |
| C3-006 | computeIfAbsent/getAndSet 非原子读-改-写 | P2 | 新发现 | C1-012（同案合并） |
| C3-007 | 写→PEXPIRE 两步链 → 无 TTL 常驻键（half-success） | P2 | 已知未修复（session 面 = D03-02） | C1-013、C7-011、2026-05-20-nosql D03-02（同案合并） |
| C3-010 | lag 门禁 ledger 观测 qs 错位（状态行空间 vs sink 提交空间） | P2 | 新发现 | 关联 C7-008（正交协同设计） |
| C3-011 | 批任务身份键 taskKey 丢 querySpace：跨表串用 + 错误归因失真 | P2 | 新发现 | 扩展 C3-001（同根统一修复） |
| C3-012 | P0 位点/订阅面/源表全集三方标识无对齐校验 | P2 | 新发现 | 无 |

**复核统计**：判定 12 条——保留 12（其中维持原严重度 12、降级 0、升格 0）、驳回 0、待定 0。最终严重度分布 P1×6 / P2×6。现状标注：新发现 11、已知未修复 1（C3-007 session 面）。跨维度同案 6 组（修复时合并计一次工单，去重后本维度独立工单 6 条：C3-003/008/009/010/011/012）。
