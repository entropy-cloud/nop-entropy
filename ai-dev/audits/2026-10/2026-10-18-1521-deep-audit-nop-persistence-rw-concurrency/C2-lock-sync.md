# C2 锁与同步 —— nop-persistence-rw 深度审计（第 1 轮初审）

- **批次**：批次 1/4（预拆制）。本文件只覆盖以下 2 个源码根，其余源码根由批次 2-4 立案：
  - `nop-persistence/nop-orm/src/main/java`（154 files / 25,517 lines）
  - `nop-persistence/nop-orm-data/src/main/java`（1 file / 293 lines）
- **维度**：C2 锁与同步（锁序与死锁环、锁粒度与锁内 I/O、Condition/wait 丢失唤醒、synchronized 范围、CAS 复合操作、锁选错对象）
- **审计方式**：盲审第 1 轮。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（含本目录兄弟文件 `C1-shared-mutable-state.md`）。仅审计当前 live code（分支 `concurrency-audit-remediation`）。
- **判级口径代入**：ORM 层被全平台数据访问调用；session 为 IContext 绑定（OrmSessionRegistry→IContext.attributes）；`IContext.executeBlocking(ordered=false)` 允许同上下文并发（`BaseContext.executeWithContext` 对同上下文并发仅打 warn 不拒绝，见 CL-402）；`CFG_ORM_SESSION_CHECK_CONTEXT` 默认 true 但其守卫不构成互斥（见 C2-001）。
- **互引纪律**：C1 批次 1 已从可见性角度立案 OrmSessionRegistry/ormModel（C1-001~030）。本文件不重复可见性面立案，仅在 F3 失效面枚举处互引；api-core（FutureHelper/IContext/ContextTaskQueue）只作对照，不越界立案。

---

## 0. 锁载体盘点（本批次 2 源码根全量）

对全部 154+1 个文件执行 `synchronized|ReentrantLock|ReadWriteLock|Collections.synchronized|StampedLock|Condition|.lock(|.tryLock|.wait(|.notify` 全量 grep，结果：

| 载体 | 数量 | 位置 | 判定 |
|---|---|---|---|
| `synchronized` 方法/块 | **0** | — | 本模块没有任何 JVM 级监视器锁/显式锁 |
| `ReentrantLock`/`ReadWriteLock`/`Condition` | **0** | — | 同上 |
| `Collections.synchronizedXxx` | **0** | — | 无 |
| `volatile` 单槽 SQL 缓存 | 5 | `JdbcEntityPersistDriver` loadSql/lockSql/batchLoadSqlPart/lastUpdateSql（:62-73） | 见下方专项判定：良性成立 |
| 悲观实体锁（`driver.lock` = SELECT FOR UPDATE） | 1 条路径 | `JdbcEntityPersistDriver.lock`(:150-185) | DB 级锁，见锁序图 |
| DB 级条件 UPDATE CAS | 1 条路径 | `DaoEntityBlockingSource.tryClaim`(:243-261) | 见 C2-005（谓词空退化） |
| `computeIfAbsent`/`putIfAbsent` 复合操作 | 12 处 | 基线 C2 节逐点核对 | 热路径仅 3 处（OrmTemplateImpl:528、OrmDaoProvider:54、BatchActionQueueImpl:102），其余为启动/构建期冷路径；sessionCache 为 `new MapCache<>(..., false)`=裸 HashMap（OrmSessionImpl:1305），原子性由"session 单线程"契约承担，见 C2-001 |
| 静默跳过型重入守卫（boolean flag，无锁） | 3 处 | `OrmSessionImpl.flush`(:182-186 flusher!=null)、`OrmBatchLoadQueueImpl.flush`(:578-580 flushing)、`CascadeFlusher.flushing`(:54) | 见 C2-001/C2-009 |
| api-core 对照：`ContextTaskQueue`（ReentrantLock+Condition） | 1 | nop-kernel（不在本批次范围） | 单锁无嵌套；`syncGet` 在锁外执行任务（:141 注释），无锁序环；但其"等待即排空任务队列"语义是 C2-001/009 的可达性通道（CL-401） |

**volatile 单槽 SQL 缓存专项判定**（基线注释"并发回写为良性竞争"是否属实）：`EntitySQL` 为 final 字段 + `ImmutableIntArray`（EntitySQL.java:17-30），`useParamsFromEntity` 走 `GenSqlHelper.transform` 新建 SqlBuilder 不改写共享 `SQL`（GenSqlHelper.java:641）；`loadAsync`/`lock`/`batchLoadAsync`/`buildUpdateSql` 四处读槽后校验 `propIds.equals` 不匹配即重新生成并只在默认方言下回写。两线程交错时各自使用本地变量、槽内容永远自洽，最坏后果是异构 propIds 反复互相踢槽导致重复生成 SQL（C9 量化议题，非正确性）。**判定：注释属实，不立案**。

**锁内 I/O 判定**：本模块无 JVM 锁，因此不存在字面意义的"锁内 I/O"。等价形态是 `computeIfAbsent` 内做 I/O 与"DB 锁内做重活"：`OrmTemplateImpl.cacheGet`(:526-529) 的 loader 在 sessionCache（裸 HashMap）computeIfAbsent 内执行任意用户加载逻辑（含 DB 查询的递归 load），session 单线程下无互斥代价，登记为 CL-403（重入契约缺口）；悲观锁路径（SELECT FOR UPDATE）从加锁到事务提交/回滚之间由业务代码持有 DB 行锁，属应用层责任，ORM 侧无加长持有期的框架代码。

## 0.1 锁序图（文本）

JVM 锁层：本模块**无锁**，唯一相关锁是 api-core `ContextTaskQueue.lock`（单锁，任务执行在锁外，无嵌套边）→ **无 JVM 锁序环**。

DB 锁层（行锁/间隙锁）边：
```
E1: flush 批写路径  ──按 topoAsc 相序、update/delete 按实体ID升序(TreeMap, BatchActionHolder:53注释"避免更新时发生锁冲突")──▶ DB行锁
E2: session.lock() ──SELECT ... FOR UPDATE（单实体单行）──▶ DB行锁
E3: DaoEntityBlockingSource.tryClaim ──UPDATE...WHERE id──▶ DB行锁（REQUIRES_NEW 事务内）
E4: flushCollectionChange/flushChanged 延迟任务 ──flush批内追加──▶ DB行锁
```
- E1 已经做了 ID 排序 + 拓扑相序（phase1 升序 save/update、phase2 逆序 delete，BatchActionQueueImpl:160-202），模块内无交叉锁定顺序，无 DB 死锁环立案。
- E2/E3 的死锁风险取决于调用方对多实体的加锁顺序，ORM 无法约束（DB 死锁可检测并抛错），不立案。
- E3 特殊：REQUIRES_NEW 事务横跨整个轮询等待窗口（CL-400），行锁持有期被拉长到 maxWait——txn 维度线索，不按 C2 立案。

**结构性结论**：本模块的并发纪律不依赖锁，而依赖"session 线程封闭"契约 + 若干无锁 boolean 守卫。C2 的全部实质发现都源于：**该契约在平台并发语义（executeBlocking(ordered=false)、executeWithContext 并发进入、异步完成线程）下不成立，而模块没有任何互斥或 fail-fast 守卫兜底**（F10 并发守卫缺失族）。

---

## 发现清单

### [C2-001] Session 全生命周期零互斥：flush 重入守卫是"静默跳过"而非锁，同上下文并发下丢刷写/双刷写

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:102-121,176-237`
- **证据片段**:
  ```java
  private boolean readOnly; private boolean closed; private boolean dirty;   // :102-105 全部非 volatile
  private CascadeFlusher flusher;                                            // :108
  ...
  // 如果正在执行flush, 则直接返回                                            // :182
  if (this.flusher != null) {
      LOG.debug("orm.exit-flush-when-another-flush-is-running");
      return;                                                                // :185 静默跳过，不等待不报错
  }
  ...
  void checkContext() {
      if (context != null && !context.isRunningOnContext())
          throw new OrmException(ERR_ORM_SESSION_NOT_RUN_ON_CONTEXT);        // :229-232
  ```
- **严重程度**: P1
- **并发场景**: 同一 IContext 的两个线程：T1 = context 任务队列线程（正在执行 flush），T2 = `executeBlocking(ordered=false)` worker 线程或经 `executeWithContext` 并进入口绑定同 context 的线程（Base/Vertx 实现均放行，`isRunningOnContext()` 对任何绑定了该 context 的线程都返回 true，因此 `checkContext()` 在两个线程上都通过）。会话内全部可变状态（`dirty`/`closed`/`flusher`、`OrmSessionEntityCache` 的裸 HashMap、`SessionBatchActionQueue.queues` 的 TreeMap、`BatchActionQueueImpl.actionMap` 的裸 HashMap :38、`MapCache(false)` 裸 HashMap sessionCache :1305）均无任何互斥。
- **触发时序**: T1 进入 `flush()`，通过 `flusher != null` 检查，执行 `this.flusher = new CascadeFlusher(...)`（:208）开始遍历脏实体；T2 同时进入 `flush()`：(a) 若 T2 读到 `flusher != null`（或因无 happens-before 读到旧值 null），T2 要么静默 return——其调用方（事务提交拦截器/`flushSession()`）认为已刷写而提交事务，T2 在 T1 的 flusher 快照之后入队的动作滞留 `batchActionQueue`，DML 未执行而事务已提交（丢更新）；要么 (b) T2 也构造第二个 CascadeFlusher 与 T1 并发遍历同一 `OrmSessionEntityCache`（裸 HashMap/LinkedHashMap，:33/:56）并对同一实体重复入队 save/update 动作（重复 INSERT/主键冲突/CME）。两条分支都无需罕见时序：`executeBlocking(ordered=false)` 文档语义即允许 worker 与 context 队列并发。
- **现状**: flush 的互斥依赖"同一时刻只有一个线程在跑这个 context"这一平台侧假设；模块内唯一的守卫是一个非原子的 boolean 判断，竞争时静默跳过而不是等待或报错。
- **风险**: 丢更新（T2 的事务在未刷写状态下提交）或重复 DML/集合结构损坏。ORM 是全平台数据访问底座，任何使用 ordered=false 阻塞任务的业务（导入、批量作业）都在触发面上。
- **建议**: 给 `OrmSessionImpl` 增加所有者线程/重入计数守卫：flush 入口用 `ReentrantLock`（可重入以满足 CascadeFlusher 回调重入语义）替代 `flusher != null` 静默跳过；或在 `checkContext()` 中记录 owner-thread 并对异线程访问 fail-fast（复用 `ERR_ORM_SESSION_NOT_RUN_ON_CONTEXT` 错误码族）。平台已有 `io.nop.commons.concurrent.lock.IResourceLockManager` 可参考。
- **失效面枚举**: 同族守卫兄弟全部核对——`OrmSessionImpl.flush`(:182) 本条；`OrmSessionImpl.flushImmediately`(:1013-1030，`flusher==null` 才新建，否则复用当前 flusher——同线程重入正确，异线程同样无守卫)；`OrmBatchLoadQueueImpl.flush`(:578-580) 归入 C2-009；`BatchActionQueueImpl.flushing`(:140-142) 只被读从不作为入口守卫（flushAsync 无重入检查）；`SessionBatchActionQueue.flush/flushAsync`(:45-63) 完全无守卫；`checkValid`(:234-237) 只查 `closed` 不查线程归属；`getSessionRevVersion`(:156-161) check-then-set 非原子（同上下文并发可发出两个 rev 版本，租户缓存可见性异常）。逐一状态：全部无互斥、无 fail-fast。
- **信心水平**: 确定（守卫与字段形态为代码事实；并发入口为判级口径已核实的平台语义）
- **误报排除**: 不属于"无状态单例被并发调用是设计意图"——session 是重状态对象且 `checkContext`/`flusher` 守卫表明作者自认单线程契约；也不属于"低频路径粗锁合理"——flush 是每事务热路径。判级未到 P0 是因为同上下文双线程并发属边缘用法而非缺省主路径。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。flush 静默跳过守卫与全字段零互斥亲验成立；触发前提亲验：BaseContext.executeWithContext 对同上下文并发仅 LOG.warn（:254-256）、executeBlocking(ordered=false) javadoc 明示 worker 线程绑定 context 执行，P1 成立

### [C2-002] runInNewSessionAsync 在异步完成线程上执行 flush/registry 恢复/close，与同上下文在用线程无同步

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java:243-270`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:768-791`
- **证据片段**:
  ```java
  return ContextProvider.thenOnContext(future).whenComplete((ret, err) -> {
      try {
          if (err == null) { session.flush(); }          // :258 在完成回调线程上刷写
      } finally {
          IOrmSession s = registry.put(sessionFactory, oldSession);   // :261 恢复注册表
          if (s == session) { session.close(); }          // :263-264 在该线程上清空/关闭会话
          else { LOG.info("nop.orm.skip-session-close-when-not-current-session"); }
      }
  });
  ```
- **严重程度**: P1
- **并发场景**: `thenOnContext` 的 Javadoc 明示"返回的promise需要立刻被使用，如果传递到其他线程上使用，则不确保这一点"；且 `thenOnContext0` 经 `context.execute()` 投递——`BaseContext.runOnContext` 在无其他处理线程时于**当前调用线程（即异步完成线程）上同步 flush 任务队列**（BaseContext.java:230-241）。因此 whenComplete 里的 `session.flush()` + `registry.put` + `session.close()` 可在任意完成线程执行，而同 context 的另一线程可能仍持有该 session 并在读 cache/入队动作。
- **触发时序**: T1（context 线程）调用 `runInNewSessionAsync(cb)`，cb 返回未完成 future F 后 T1 继续在同一 context 内执行后续业务（session 仍注册在 context.attributes，`runInSession`(:204-211) 会拿到同一个 session），调用 `orm.get(...)` 读 `OrmSessionEntityCache` 并向 `batchActionQueue` 入队；F 在异步线程 W 上完成 → `context.execute` 在 W 上内联执行 whenComplete → W 执行 `session.flush()`（遍历 T1 正在写的裸 HashMap actionMap/cache）并 `session.close()`（`cache.clear()`、`closed=true`）→ T1 随后在已清空的 cache 上继续操作或对 closed session 入队（`checkValid` 在 T1 的读写之间被 W 改写 `closed`，非 volatile，T1 可能永远看不到）。后果：CME、动作丢失、或对已关闭 session 的静默半操作。
- **现状**: 会话生命周期的收尾（flush/close/registry 恢复）被放在"未来某个线程"上执行，没有任何机制保证此刻没有其他线程正在使用该会话，也没有等待/互斥。
- **风险**: 同 C2-001 的数据面后果（丢写/结构损坏），另加生命周期面：close 与在用并发导致对已关闭会话的静默访问。审计口径下属"罕见但可具体化的竞态（有明确触发链）"。
- **建议**: whenComplete 中仅投递"会话收尾"任务到 context 队列并等待该队列排空确认（配合 CL-401 的队列语义），或给 session 增加引用计数/owner-thread 校验后再 flush/close；`skip-session-close-when-not-current-session` 日志说明偏 already 发生，应升级为可观测的告警指标而非 info。
- **失效面枚举**: 同族兄弟——`runInNewSession`(:213-231，同步版，flush/close 都在调用线程，单线程下无此问题)；`runInSessionAsync`(:233-241，不 close 不 flush，仅 thenOnContext 包装，风险低)；`registerSession/unregisterSession`(:272-280，公共 API，同样可在任意线程替换注册表，归 C2-003)；`OrmSessionImpl.close`(:768-791，无 checkContext、`if (closed) return;` 非原子)。逐一状态：仅 runInNewSessionAsync 命中跨线程收尾。
- **信心水平**: 确定（跨线程执行路径由 thenOnContext/BaseContext 代码事实支撑）
- **误报排除**: 非 C3 异步链维度重复——本条立案点是"会话设施的互斥缺失/生命周期线程纪律"（C2），事务边界破坏面单独留给 C3（CL-407）；亦非 C1 可见性重复——此处是操作交错而非内存可见性。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。runInNewSessionAsync 的 whenComplete 在完成线程执行 flush/registry 恢复/close 逐行亲验成立（OrmTemplateImpl.java:257-268）；同 context 在用线程交错后果具体

### [C2-003] OrmSessionRegistry put/restore 用单值模拟栈且非原子：并发/交错收尾时会话身份被交换、会话泄漏

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java:213-231`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmSessionRegistry.java:16-27`
- **证据片段**:
  ```java
  IOrmSession oldSession = registry.put(sessionFactory, session);   // :217 压入新会话，返回旧会话
  try {
      T ret = callback.apply(session);
      session.flush();
      return ret;
  } finally {
      IOrmSession s = registry.put(sessionFactory, oldSession);     // :223 恢复旧会话（覆盖式）
      if (s == session) { session.close(); }
      else { LOG.info("nop.orm.skip-session-close-when-not-current-session"); }  // :228 偏差已发生的自证日志
  }
  ```
- **严重程度**: P1
- **并发场景**: 同一 IContext 内两个线程各自调用 `runInNewSession`/`runInNewSessionAsync`（或一方 runInNewSessionAsync 的 whenComplete 恢复与另一方 runInNewSession 的 put 交错）。registry 是 context.attributes 里的**单值槽**（`ContextualizedRegistry`），put/restore 是"读-改-写"复合操作，无锁无 CAS。
- **触发时序**: T1 put(S1)（old=S0）；T2 put(S2)（old=S1）；T1 先收尾 → put(S0)（覆盖了 S2！）；T2 收尾 → put(S0) 返回值 s==S0≠S2 → 走 else 分支 **S2 永不 close**（泄漏，其未 flush 的动作丢失、metric onSessionClosed 不触发）；且在 T1 恢复 S0 到 T2 收尾之间，任何 `runInSession`/`currentSession` 都会拿到 S0——此时 T2 的 callback 若尚未结束，其后续 ORM 操作落在**别人的会话**上（数据写进错误会话的批队列，随错误会话的 flush/close 提交或丢弃）。`:228` 的 info 日志正是该偏差在现网发生时的自证出口。
- **现状**: 注册表以"单槽+先压后弹"模拟栈语义，栈序在并发或异步交错（C2-002 的 whenComplete 恢复）下不成立，且 put 返回值只用于 close 判定，不校正错位。
- **风险**: 会话身份交换导致跨会话数据混写/丢失；会话泄漏（批动作滞留、监听器滞留）。与 C2-002 叠加时无需人为并发，异步收尾顺序即可触发。
- **建议**: registry 槽改为 per-context 的 Deque（真实栈）或给 put/restore 增加 generation 校验（restore 时仅当当前槽==自己压入的会话才恢复，否则报错/告警）；`skip-session-close` 分支必须补 close 兜底而非跳过。
- **失效面枚举**: 同族入口——`runInNewSession`(:213-231，同线程嵌套时 LIFO 成立，安全)；`runInNewSessionAsync`(:243-270，恢复点在异步线程，与 C2-002 复合)；`registerSession`(:277-280)/`unregisterSession`(:272-275，公共 API 任意线程可单值覆盖)；`OrmSessionRegistry.instance()`(:19-27) get-or-create 的 check-then-set（可见性面归 C1 批次互引，此处不重复）。逐一状态：除同步嵌套外全部存在错位面。
- **信心水平**: 确定（机制层面）；具体生产触发频率：很可能
- **误报排除**: 不按 C1 可见性重复立案——本条是 put/restore 的**原子性与栈序**缺陷（CAS 复合操作），与内存可见性无关；`:228` 日志证明作者已知偏差存在但只降级为日志。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。单槽 put/restore 与 `s == session` 关闭门亲验成立（:219-229）；交错丢 close/会话身份交换/僵尸会话复用成立

### [C2-004] 全局实体/集合缓存 read-repair 复合操作竞态：迟到 put 覆盖并发 evict → 陈旧值无界驻留

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:217-245,537-569`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/CollectionPersisterImpl.java:80-101,141-157,206-229`
- **证据片段**:
  ```java
  // EntityPersisterImpl.loadAsync: miss 后读库，完成回调中回填
  if (useGlobalCache) { if (loadFromGlobalCache(entity, session)) return FutureHelper.voidPromise(); }
  CompletionStage<Void> future = driver.loadAsync(shardSelection, entity, ..., session);
  if (useGlobalCache) {
      future = future.thenApply(r -> { updateGlobalCache(entity, session); return null; });  // :239-242
  }
  ...
  protected void updateGlobalCache(IOrmEntity entity, IOrmSessionImplementor session) {      // :538-545
      if (entity.orm_state().isMissing()) { globalCache.removeAsync(getCacheKey(entity)); }
      else { globalCache.putAsync(getCacheKey(entity), OrmAssembly.getPropValues(...)); }
  }
  ```
- **严重程度**: P1
- **并发场景**: 引擎级跨线程共享缓存（`env.getGlobalCache(name)`，`CFG_ENTITY_GLOBAL_CACHE_ENABLED` 默认 true）。装载线程 T1 与提交更新的事务线程 T2 无任何同步；"查缓存 miss → 读 DB → put"是一条跨多个 await 点的 check-then-act，evict 侧（事务提交后 `removeAsync`，:553-569）与 put 侧没有序号/版本围栏。
- **触发时序**: T1 `loadFromGlobalCache` miss（缓存中无 key）；T1 的 SELECT 在 T2 事务提交**前**执行，读到旧值 v1；T2 提交（DB=v2）→ `evictGlobalCache` 的 onAfterCommit 监听器执行 `removeAsync(key)`；T1 的 future 完成 → `updateGlobalCache` 执行 `putAsync(key, v1)`。终态：缓存=v1，DB=v2，且此后对该实体的读永远命中缓存（`loadFromGlobalCache` 直接装配返回），直到该实体下一次被写。写侧不触发则**永不自愈**。集合路径同构（`CollectionPersisterImpl.loadCollection`:96-100 读库后同步 `globalCache.put`，:229；evict 走 `remove`+监听器 :207-223）。
- **现状**: cache-aside 回填与失效之间没有版本/时间围栏，失效事件可能被更早启动的回填迟到覆盖。
- **风险**: useGlobalCache 实体的读路径返回陈旧数据且无界期驻留（语义违约：同库不同 session 读到不同值）；下游以 ORM 读为准的业务（权限、租户、状态机）出现不可复现的旧值决策。
- **建议**: 回填 put 前二次校验（put 采用带期望版本的 `replace`/版本化 value，miss-回填携带读库时的事务时间戳，低于当前缓存时间戳则放弃）；或 evict 侧在提交后延迟双删；平台 `ICache` 已有原子 `compute` 能力可承载版本化 CAS。
- **失效面枚举**: 实体路径全部回填点——`loadAsync`(:228-243，putAsync，命中本条)、`batchLoadAsync`(:175-183，thenRun 批量 updateGlobalCache，同构竞态)、`lock`(:259-261，锁后回填，持行锁使其 T1 侧与 T2 提交互斥，**相对安全**)、`getToLoad`(:595-624，批量 getAll 后装配，miss 子集仍走 driver 回填，同构)；evict 点——`queueSave/queueUpdate/queueDelete` 回调(:466,487,499)统一走 `evictGlobalCache`(:553-569，removeAsync / onAfterCommit removeAsync，命中本条的被覆盖方)。集合路径——`loadCollection`(:91-100 同步 put，命中)、`_batchLoad`(:148-154 thenRun put，命中)、`flushCollectionChange`(:172-183 evict，命中)、`getToLoad`(:279 起，同构)。逐一状态：除 `lock()` 路径因持有 SELECT FOR UPDATE 行锁天然串行化外，全部回填点与全部 evict 点构成同一竞态。
- **信心水平**: 确定（交错序列完整具体化）
- **误报排除**: 不属于"有界声明缓存合理"——问题是复合操作缺少围栏而非容量；也不是 C1 可见性面（各操作内部内存可见性无碍，是操作间**顺序**竞态）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。read-repair（loadAsync→thenApply→updateGlobalCache→putAsync）与 evict（onAfterCommit→removeAsync）无版本围栏亲验成立（EntityPersisterImpl.java:228-243/:537-569）；现状标注：CFG_ENTITY_GLOBAL_CACHE_ENABLED 默认 true（OrmConfigs.java:47-48）但需实体模型级 useGlobalCache opt-in（EntityPersisterImpl.java:92）——启用该特性的实体上竞态具体可达，P1 维持

### [C2-005] DaoEntityBlockingSource 抢占 CAS 在旧值为 NULL 时退化为无条件 UPDATE：声明的原子抢占失效，任务重复投递

- **文件**: `nop-persistence/nop-orm-data/src/main/java/io/nop/orm/data/source/DaoEntityBlockingSource.java:216-261`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java:904-950`（appendExampleFilter 仅按 example 已 init 属性生成 WHERE）
- **证据片段**:
  ```java
  // 原子抢占：以UPDATE...WHERE 主键 AND 抢占字段保持被选中时的旧值 的方式写占用标记。   // :224-226 声明的契约
  private boolean tryClaim(IEntityDao<T> dao, T item) {
      Map<String, Object> oldValues = currentAcquireValues(item);   // :248 只收集非null旧值
      ...
      for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
          example.orm_propValueByName(entry.getKey(), entry.getValue());   // :255-257 null 旧值不进 WHERE
      }
      return ormTemplate.runInSession(session -> session.updateByExample(example, updated)) == 1L;
  }
  ```
- **严重程度**: P1
- **并发场景**: 多个消费者线程各自在 REQUIRES_NEW 事务中执行 `drainTo`（`takeMulti` 为无限等待入口），对同一批"从未被抢占过"的任务行竞争。CAS 谓词由 `appendExampleFilter` 按 example 的 inited 属性逐列 eq 生成（GenSqlHelper.java:904-950 之后的 appendExampleFilter 体）——`currentAcquireValues`(:263-281) **跳过 null 值**，因此初始状态为 NULL 的抢占字段不进入 WHERE。
- **触发时序**: 任务行 r 的三个抢占字段均为 NULL（新任务常态）。T1、T2 的 `loadItems` 在各自 REQUIRES_NEW 快照中同时读到 r；T1 `tryClaim` → `UPDATE ... WHERE id=?`（无抢占字段条件）匹配 1 行，commit；T2 `tryClaim` → `UPDATE ... WHERE id=?` 在 T1 提交后同样匹配 1 行（id 不变）→ 返回 true。两个消费者都把 r 加入 `claimed` → **同一任务被投递两次**，`:224-226` 注释声明的"只有一个消费者的条件UPDATE能匹配成功"不成立。仅当业务保证抢占字段在创建时即写入非 NULL 值（如 status=0）时 CAS 才有效，代码与文档均未声明该前置条件。
- **现状**: CAS 谓词的构造把"旧值为 NULL"从比较中剔除，最常见的初始态恰好使整个抢占机制失效。
- **风险**: 批处理/任务队列语义下重复执行副作用（重复发消息、重复扣减）；且失败侧无日志（`:233` 的 claim-conflict 日志在退化场景永不出现，静默双投）。
- **建议**: NULL 旧值显式生成 `col IS NULL` 谓词（SqlBuilder 支持 isNull），或将声明契约改为强制业务非 NULL 初始化并在 `init()` fail-fast 校验实体模型 default 值；至少在 `tryClaim` 检测到 `oldValues.isEmpty()` 时拒绝抢占并告警。
- **失效面枚举**: 本文件全部消费入口——`take`(:122-126)、`poll()`(:129-136)、`poll(timeout)`(:139-144)、`drainTo(maxElements)`(:147-154)、`takeMulti`(:157-159)、`drainTo(4参)`(:172-214) 全部汇聚到 `loadItems`→`tryClaim`，同族命中率 100%；兄弟机制——`IOrmEntityDao.updateByExample` 通用路径（GenSqlHelper 生成器本身行为正确，缺陷在 tryClaim 的 example 构造）；`EntityPersisterImpl.updateByExample`(:736-757) bulk 语义不同，不受影响。
- **信心水平**: 确定（SQL 生成与 example 构造逐行核实）
- **误报排除**: 不是"DB 本身保证"——REQUIRES_NEW 两个事务的 UPDATE 仅靠行锁串行化执行，WHERE 条件不排除对方；也不是租户/revision 附加条件可救（它们对两个消费者同样匹配）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。currentAcquireValues 跳过 null 旧值 + example WHERE 不含 NULL 列逐行亲验成立（DaoEntityBlockingSource.java:263-291）；NULL 初始态前置条件如实标注；与 C6-001/C3-006 三面关系终裁：**独立缺陷不并入合并工单**（正确性/重复投递 vs 资源持有），但事务边界内移实现须保证 tryClaim+loadItems 仍同事务（实现约束确认）

### [C2-006] session.flush() 错误路径状态机不一致：dirty/clearDirty 先复位后批执行，批执行失败后重试 flush 永久 no-op

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:205-227`；对照 `flushImmediately:1013-1030`
- **证据片段**:
  ```java
  try {
      this.flusher = new CascadeFlusher(this, cache);
      this.flusher.execute();
      this.dirty = false;              // :211 已复位
      this.cache.clearDirty();         // :212 会话级脏登记已清空
      this.batchActionQueue.flush();   // :214 此处抛 SQL 异常 → 进入 catch
  } catch (Throwable e) { exp = e; }
  ...
  if (exp != null) throw NopException.adapt(exp);
  ```
- **严重程度**: P2
- **并发场景**: 单线程错误路径（事务内部分 DML 失败，如唯一键冲突/死锁回滚标记）。非竞争缺陷，属 flush 状态机的同步纪律错误（守卫状态与真实数据状态失同步），F2"半个成功"族。
- **触发时序**: flush 开始 → `flusher.execute()` 成功（全部动作入队，`persisterPostUpdate` 尚未清每实体脏标记——那些在批回调里才执行）→ `dirty=false`、`cache.clearDirty()` 执行 → `batchActionQueue.flush()` 抛异常（第 N 批 UPDATE 失败，前 N-1 批已写库）→ 异常抛给调用方。若调用方捕获异常并在**同一事务/同一会话**上重试 `flush()`（或修复数据后重提交），`flush()` 在 :189 `if (!dirty)` 直接 return——**失败实体的修改永远不会重试**（每实体脏标记未清，但会话级脏登记已清，flusher 不再遍历它们）。`dirty=false` 的赋值点位于批执行之前，顺序颠倒。
- **现状**: 状态复位时机早于危险操作完成；`flushImmediately`（stateless 路径 :1027-1029）已经用 `this.dirty = oldDirty || this.dirty` 防御同类问题，主 flush 路径没有同等保护。
- **风险**: 捕获-重试模式的调用方（批处理框架常见）静默丢更新；未回滚整体事务的局部重试路径数据不一致。
- **建议**: 将 `dirty=false`/`clearDirty()` 移到 `batchActionQueue.flush()` 成功之后（与 flushImmediately 的 oldDirty 保护对齐）；或 catch 中回滚 `dirty`/cache 脏登记。
- **失效面枚举**: 同族——`flush()`(:205-227，命中)；`flushImmediately`(:1013-1030，已有 oldDirty||dirty 防御，安全)；`updateDirectly`(:534-548，flushActionQueue 失败时实体脏标记未清、无会话级复位问题，安全)；`saveDirectly`(:505-531，同上安全)；`runInNewSession` 的 `session.flush()`(:220，异常直接传播并 close 会话，安全)。逐一状态：仅主 flush 命中。
- **信心水平**: 确定（顺序为代码事实；重试可达性取决于调用方模式，批处理场景常见）
- **误报排除**: 非 C6 资源泄漏、非 F9 吞异常（异常确实抛出）——立案点是"同步状态守卫与危险操作的相对顺序"。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**

### [C2-007] IOrmModelProvider 模型代交换无同步协议：getOrmModel 中对共享 LoadedOrmModel 热写 interceptor + clearCache 非原子换引用 + incRef TOCTOU

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:15-55`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/TenantAwareOrmModelProvider.java:15-56`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:156-174`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:125-135`
- **证据片段**:
  ```java
  // DefaultOrmModelProvider
  public ILoadedOrmModel getOrmModel(IPersistEnv env) {                 // 每次开 session / getOrmModel 都走
      IOrmInterceptor interceptor = interceptorCache.getObject(..., cacheName -> ...loadInterceptor(...));
      if (interceptor != null)
          ormModel.setOrmInterceptor(interceptor);                      // :40 任意请求线程写共享模型字段（非 volatile）
      return ormModel;                                                  // :41 非 volatile 读
  }
  public void clearCache() {
      OrmOrmModel ignored = null; OrmModel m = new OrmModelLoader().loadOrmModel(false);
      this.ormModel = new LoadedOrmModel(env, m);                       // :47 非原子换引用，旧模型不 close
      this.interceptorCache.clear();
  }
  ```
- **严重程度**: P2
- **并发场景**: 请求线程（每 session 打开/每次 `SessionFactoryImpl.getLoadedOrmModel`）vs 管理动作线程（`OrmTemplate.reloadModel()` → `clearCache`；bean destroy → `close()`）。三处复合操作均无锁/无 volatile：(1) `getOrmModel` 每次调用都对共享模型热写 `ormInterceptor`（读端 `OrmSessionImpl.interceptPreSave` 等在 flush 关键区读取）；(2) `clearCache` 换引用期间读端可能拿到旧引用并对其 `setOrmInterceptor`（写到一个已被替换/即将被 GC 的代际）；(3) session 构造器 `env.getLoadedOrmModel()` 与 `incRef()`(:128-129) 之间，`close()`/`clearCacheForTenant`（TenantAware 的 `destroyOnRemove`，TenantAwareOrmModelProvider.java:25,48）可把 refCount==0 的模型 `doClose()`（LoadedOrmModel.java:168-184 关闭全部 persister driver），新 session 随后 `incRef()` 一个已关闭模型。
- **触发时序**: T1 session 构造：读得模型 M_old（此刻最后一个旧 session 已 decRef 到 0）；T2 `reloadModel()`/provider.close() → `M_old.close()` → `refCount.get()<=0` → `doClose()`；T1 `M_old.incRef()`（:128，晚了）→ T1 的 session 持有已关闭 persister 的模型 → 后续 load/save 走已关闭 driver。窗口为两条指令宽，但 reload + 高频建销 session 的高频管理场景可命中。
- **现状**: 模型代际的发布/交换/关闭没有任何同步协议（无锁、无 volatile、refCount 与 close 不构成安全发布），interceptor 是"每请求热写共享字段"。
- **风险**: 热更新模型（reloadModel 是公开运维入口）期间出现跨代际混用（同一事务内部分实体用旧 persister、部分用新 persister）、interceptor 半程替换（preSave 用旧 postSave 用新）、已关闭模型的复活使用。
- **建议**: provider 的 `ormModel` 字段加 volatile 并以"构造新代→原子换引用→旧代延迟关闭（等待 refCount 归零，AtomicInteger 已具备）"三段式发布；`incRef` 移入 provider 的 getOrmModel 原子区（或 getOrmModel 返回已 incRef 的句柄，由 session finally decRef）；`setOrmInterceptor` 移到模型构建期而非每请求。
- **失效面枚举**: 三个实现逐一——`DefaultOrmModelProvider`(:15-55，三处全中)；`TenantAwareOrmModelProvider`(:15-56，`ormModel` 非 volatile 换引用(:41)、`close()` 只关默认模型后 `tenantCaches.clear()`(destroyOnRemove 即关，:52-55) 对在途 session 同样有 incRef TOCTOU、tenantCaches.get 的 loader 在 LocalCache 锁内做**全量模型构建**（重 I/O inside compute，:23-28，冷路径但属锁内重活形态)；`StaticOrmModelProvider`(final 字段，不可变，安全)。写端兄弟——`LoadedOrmModel.setOrmInterceptor`(:58-60，非 volatile 写，被 :40 每请求调用)。逐一状态：Default/TenantAware 命中，Static 安全。
- **信心水平**: 确定（字段/锁形态）；触发需要 reload 或租户缓存淘汰与 session 创建同窗：很可能（管理窗口内）
- **误报排除**: 不重复 C1 的 ormModel 可见性立案——本条立案点是**复合操作协议**（refCount 与 close 的 TOCTOU、换引用三段式缺失、锁内重活），即便所有字段都 volatile，TOCTOU 依旧成立；管理路径低频但 reload 是声明的公开能力，非"启动期一次性"。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。与 C1-003/C1-004 为同位点不同面（协议层 vs 可见性面），三分立案维持、修复归并同一切片

### [C2-008] reloadModel 不失效 queryPlanCache：跨模型代的陈旧编译计划继续被执行；并发首编为双编译+租户计划条目丢失（自愈）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:551-554`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:44-49`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:92-134`
- **证据片段**:
  ```java
  @Override
  public ILoadedOrmModel reloadModel() {
      ormModelHolder.clearCache();      // DefaultOrmModelProvider.clearCache 只换 ormModel + 清 interceptorCache
      return ormModelHolder.getOrmModel(this);   // 不触碰 env.getQueryPlanCache()
  }
  ...
  // LoadedOrmModel.compileSql: 查-编-放
  IOrmCachedQueryPlan result = env.getQueryPlanCache().get(key);      // :109 命中即返回旧代计划
  if (result == null || result.getCompiledSql() == null) { ... compile ... put ... }  // :110-127
  ```
- **严重程度**: P2
- **并发场景**: 管理线程执行 `reloadModel()`（模型热更新）与请求线程执行 EQL 编译/执行共享全局 `queryPlanCache`。cache 的查-编-放本身无锁（双编译良性），缺陷是**失效一致性**：模型代际更新后缓存不参与失效。
- **触发时序**: (a) 陈旧计划：请求线程 T1 在 reload 前编译过 `sqlText` → plan(P_old) 进缓存；T2 reloadModel（新模型含列变更/新增租户列）；T3 执行同一 EQL → `get(key)` 命中 P_old → 以旧模型列集生成的 SQL 直接执行（orm 查询计划与 persister/DB 新结构脱节）→ SQL 报错或按旧列映射错读。(b) 双编译（良性自愈部分）：T1/T3 同时 miss → 都编译 → 都 put，后写覆盖，租户场景下 `TenantCachedQueryPlan` 新建覆盖可能丢掉对方方言/租户条目，但下次 `getCompiledSql()` 返回 null 触发重编（:110 `result.getCompiledSql() == null` 分支），**自愈**，仅浪费编译。
- **现状**: 缓存有手工失效入口（`clearQueryPlanCache` :117-119）但 reloadModel 链路未调用；查-编-放无围栏。
- **风险**: 热更新模型后平台行为依赖"调用方记得手工清计划缓存"这一未声明前提；否则陈旧计划跨代存活直到进程重启（cache 无 TTL 声明）。
- **建议**: `DefaultOrmModelProvider.clearCache()`（两个实现）中同步 `env.getQueryPlanCache().clear()`，把失效内聚进模型代交换；计划条目携带模型代版本号，`compileSql` 命中时校验代版本不匹配即重编。
- **失效面枚举**: 失效链兄弟——`SessionFactoryImpl.reloadModel`(:551-554，命中)；`clearQueryPlanCache`(:353-355 区段 :117-119，存在但未接入)；`DefaultOrmModelProvider.clearCache`(:44-49，未清 plan cache，命中)；`TenantAwareOrmModelProvider.clearCache/clearCacheForTenant`(:40-49，同样未清，命中)；`StaticOrmModelProvider.clearCache`(空操作，模型不可变，安全)。查-编-放双编译丢失条目：自愈，不单独立案。
- **信心水平**: 确定（reload 链路未清缓存为代码事实）
- **误报排除**: 双编译竞态本身按校准属可容忍（自愈、冷路径），本条立案核心是**跨代陈旧缓存**——锁与同步维度下"共享缓存的失效纪律"，与 C1 的检查-然后-放入（发布可见性）分界清楚。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。与 C1-008(c) 同位点双登记合并终裁**确认**（同 C1-008）；CL-410 对本条「自愈」判定的修正确认成立（多租户交叠下永不收敛）

### [C2-009] 批量装载/批动作队列的静默跳过守卫族：flush 期间经 ContextTaskQueue.syncGet 排空重入后，collectionActions 追加到活列表被"补发成功回调"

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/OrmBatchLoadQueueImpl.java:576-607`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java:121-126,144-239`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/SessionBatchActionQueue.java:45-63`
- **证据片段**:
  ```java
  // OrmBatchLoadQueueImpl.flush
  if (this.flushing) return;                                   // :579-580 静默跳过
  ...
  FutureHelper.syncGet(FutureHelper.waitAll(futures));         // :594 等待点=ContextTaskQueue.syncGet
  ...
  // BatchActionQueueImpl
  List<IBatchAction.CollectionBatchAction> collActions = this.collectionActions;   // :151 捕获引用（同一列表对象）
  ...
  this.collectionActions = null;                               // :236 finally 才断开
  ```
- **严重程度**: P2
- **并发场景**: `FutureHelper.syncGet` 对未完成 future 落入 `ContextTaskQueue.syncGet`（api-core，:46-105），该实现在等待期间**于当前线程上执行 context 任务队列中的其他任务**——即 flush 的等待点成为任意同 context 业务代码的重入入口（异步 driver/file store 场景 future 才会未完成；JDBC 内联完成时不触发）。重入任务与外层 flush 共享同一 session 的队列与守卫 flag。
- **触发时序**: 异步装载 future 未完成 → session 线程在 :594 的 syncGet 中开始执行 context 队列任务 T'；T' 触发级联集合修改 → `flushCollectionChange` → `enqueueCollection`(:122-126) 追加到 `collectionActions` 字段（此字段仍被外层 `collActions` 引用，同一 ArrayList，:151）；T' 接着调用 `session.flush()` → 被外层 flushing/flusher 守卫静默跳过 → T' 以为已刷写；外层 flush 收尾时 whenComplete 遍历 `collActions` 对**从未提交 DML 的** T' 集合动作调用 `action.onSuccess(null)`(:221-230) → 全局缓存不 evict、脏标记被清，集合变更静默丢失。另一分支：T' 的 `OrmBatchLoadQueueImpl.flush` 被 :579 静默跳过，惰性属性保持未装载（后续按需直载兜底，后果较轻）。
- **现状**: 重入守卫全部是"发现并发/重入即静默放弃"语义，放弃后调用方无感知；`collectionActions` 在 flush 窗口内仍是活列表，新动作会被旧批次的收尾回调"代领"成功。
- **风险**: 异步 driver/file store 部署下的集合级丢更新与缓存不失效；同步 JDBC 部署下退化为纯理论（future 内联完成，syncGet 不排空）。
- **建议**: flush 窗口内将 `collectionActions` 置为新列表再捕获旧引用（swap-then-capture），使迟到入队落入下一批；重入的 flush 调用改为注册"待主 flush 完成后重放"而非静默返回（复用 `addChangeDuringFlush` 机制思路）。
- **失效面枚举**: 守卫族逐一——`OrmBatchLoadQueueImpl.flush`(:578-580，命中，后果轻)；`OrmSessionImpl.flush`(:182-186，主案在 C2-001)；`BatchActionQueueImpl.flushAsync`(:144-239，自身无重入守卫——重入直接并发操作 actionMap/collectionActions，命中)；`SessionBatchActionQueue.flushAsync`(:49-63，无守卫，queues TreeMap 迭代期间新增 querySpace 条目可 CME，命中)；`afterFlush`(:634-644，`loadQueue==null` 判定与入队非原子，重入窗口内 task 可能既不立即执行也不入回调，命中)。逐一状态：本族 5 处守卫/无守卫点全部存在静默偏差面。
- **信心水平**: 很可能（重入通道依赖 async driver 或 file store 使 future 不内联完成；机制链完整）
- **误报排除**: 非 F10 泛化——每处守卫都给出了具体重入通道与后果；纯 JDBC 同步场景已如实标注降级（理论面），不虚报。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**（默认 JDBC 驱动下理论面已如实标注）

### [C2-010] evictGlobalCache 每实体每事务注册一个独立事务监听器：O(N) 监听器堆积与提交期回调风暴

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:552-569`（调用点 :466,487,499）；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/CollectionPersisterImpl.java:206-223`（调用点 :179）
- **证据片段**:
  ```java
  protected void evictGlobalCache(ShardSelection shard, final IOrmEntity entity) {
      if (!useGlobalCache) return;
      String querySpace = getQuerySpace(shard);
      if (env.txn().isTransactionOpened(querySpace)) {
          this.env.txn().addTransactionListener(querySpace, new ITransactionListener() {   // :560 每实体一个匿名监听器
              @Override public void onAfterCommit(ITransaction txn) {
                  globalCache.removeAsync(getCacheKey(entity));
              }
          });
      } else { globalCache.removeAsync(getCacheKey(entity)); }
  }
  ```
- **严重程度**: P3
- **并发场景**: 单事务批量写 N 个 useGlobalCache 实体（`queueSave/queueUpdate/queueDelete` 的批回调逐实体调用）→ 事务对象累积 N 个监听器；提交线程在 onAfterCommit 阶段串行执行 N 次 removeAsync。同时 flush 线程仍在（错误路径下）注册新监听器与提交线程的监听器列表操作交错（列表线程安全性归 nop-dao 批次核对，CL-404）。
- **触发时序**: 批量导入 1 万实体 → 1 万个 ITransactionListener 挂到同一事务 → commit 后单线程执行 1 万次缓存 remove（每 remove 一次 CAS/锁竞争）；大 N 下提交延迟被拉长，且监听器对象在事务生命周期内不可回收。
- **现状**: 每实体注册 vs 可按 (querySpace, entityName) 聚合的批量失效，粒度选错（锁选错对象的缓存失效版：以实体为粒度挂事务回调而非以事务为粒度聚合 key 集合）。
- **风险**: 大批量写场景提交尾延迟线性膨胀；与 C2-004 的迟到 put 竞态复合时无影响增量。内存上界 = 批大小，有界。
- **建议**: 聚合失效——flush 结束时按 querySpace 注册一个监听器，携带本次全部 cacheKey 集合批量 remove；或复用 `globalCache.removeAll(keys)`。
- **失效面枚举**: 注册点全部清单——`EntityPersisterImpl.queueSave`(:466)、`queueUpdate`(:487)、`queueDelete`(:499) → `evictGlobalCache`(:553-569)；`CollectionPersisterImpl.flushCollectionChange`(:179) → `evictGlobalCache`(:206-223)。4 个注册点行为一致，全部命中；`lock()` 路径不注册（同步 removeAsync 在 :267 后?——实为 ：259-261 走 updateGlobalCache，无监听器），不计入。
- **信心水平**: 确定（堆积机制）；影响量化：P3（有界、单事务内）
- **误报排除**: 非内存无界（F4 不适用，事务结束即释放）；非错误路径泄漏（rollback 时监听器随事务对象丢弃，缓存不失效但 DB 也回滚，一致）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**。与 C5-004/C9-001 同位点不同面（内存分配/复杂度/监听器协议），修复归并「事务级单监听器聚合」单一方案

### [C2-011] SessionFactoryImpl.addDaoListener 的 check-then-act：并发注册时 MultiOrmDaoListener 实例互相覆盖丢失监听器

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:272-278`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/MultiOrmDaoListener.java:16-25`
- **证据片段**:
  ```java
  @Override
  public void addDaoListener(IOrmDaoListener daoListener) {
      if (daoListeners == null)                       // :274 check
          daoListeners = new MultiOrmDaoListener();   // :275 act，两线程可同时进入
      daoListeners.addDaoListener(daoListener);       // :276 可能加到即将被覆盖的实例上
  }
  ```
- **严重程度**: P3
- **并发场景**: 两个线程（如两个 bean 的 @PostConstruct、运行期动态插桩注册）同时对单例 factory 调用 `addDaoListener`。`MultiOrmDaoListener` 内部是 CopyOnWriteArrayList（线程安全），但外层"判空再创建"非原子。
- **触发时序**: T1、T2 同时读到 `daoListeners == null` → 各自 new 一个 MultiOrmDaoListener → T1 写字段，T2 后写字段覆盖 → T1 在 :276 加入的是被覆盖实例的列表 → **T1 的监听器静默丢失**（onRead/onSave 永远不回调它）。
- **现状**: 惰性初始化无同步；`removeDaoListener`(:286-290) 判空读同样非原子但只影响删除时机。
- **风险**: 监听器（审计、指标类回调）静默丢失，无错误无日志。注册多为启动期，运行期动态注册少见，故 P3。
- **建议**: `volatile` 字段 + synchronized 惰性创建，或构造期直接初始化 `new MultiOrmDaoListener()`（空列表开销可忽略），彻底消掉 check-then-act。
- **失效面枚举**: 同族注册点——`addDaoListener`(:273-278，命中)；`setDaoListeners`(:280-284，循环调 addDaoListener，同面)；`addInterceptor`(:249-254，copy-on-write 新列表整体换引用，丢失面不存在，但**非 volatile 发布**——可见性面归 C1 互引)；`removeInterceptor`(:256-262，同 addInterceptor)。逐一状态：仅 daoListener 惰性初始化命中本条。
- **信心水平**: 确定（机制）；触发频率：低
- **误报排除**: 非启动期合理粗锁豁免范畴——这里恰恰是**缺**锁而非粗锁；降 P3 因触发面窄。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

---

## CL 线索（跨维度/跨模块移交）

| ID | 线索 | 建议承接 |
|---|---|---|
| nop-persistence-rw-CL-400 | `DaoEntityBlockingSource.drainTo` 的 `@Transactional(REQUIRES_NEW)` 横跨整个 `FutureHelper.waitUntil` 轮询窗口（:172-214）：REPEATABLE_READ 下快照固定→轮询看不到新行；已 tryClaim 的行以未提交状态持有行锁直到 drainTo 返回（minWait 攒批窗口内其他消费者被行锁阻塞；进程崩溃则抢占回滚）。 | txn/事务语义维度 + C7 |
| nop-persistence-rw-CL-401 | `ContextTaskQueue.syncGet`（api-core :46-105）在等待 future 期间于当前线程排空 context 任务队列——所有 ORM `syncGet` 等待点（SessionBatchActionQueue:46、OrmBatchLoadQueueImpl:594、OrmSessionImpl:874、IBatchActionQueue:43、IOrmEntityFileStore:37）都是隐藏重入通道，任意等待点可内联执行任意同 context 业务代码。 | api-core 批次 + C3 |
| nop-persistence-rw-CL-402 | `BaseContext.executeWithContext`（api-core :249-256）对同上下文并发执行仅 `LOG.warn("nop.warn.context.concurrent-execute-with-same-context")` 后继续——守卫靠日志。这是 C2-001/002/003 可达性的平台级根因。 | api-core 批次 |
| nop-persistence-rw-CL-403 | `OrmTemplateImpl.cacheGet`(:526-529) 在裸 HashMap sessionCache 的 `computeIfAbsent` 内执行用户 loader：loader 递归同 key → HashMap 递归 compute 未定义行为；loader 内 DB I/O 虽无锁代价但属重活内嵌。 | P3 备忘 / API 契约文档 |
| nop-persistence-rw-CL-404 | `evictGlobalCache`/`driver.lock` 依赖 `ITransaction.addTransactionListener`（nop-dao）：监听器列表的线程安全与"flush 注册 vs 提交线程迭代"交错需 nop-dao 批次核对。 | nop-dao 批次（C1/C2） |
| nop-persistence-rw-CL-405 | `EntityPersisterImpl.batchLoadAsync` 的 `thenRun(updateGlobalCache)`(:177-183) 在异步 driver 下由完成线程读取实体状态数组（`OrmAssembly.getPropValues`）——跨线程读 session 绑定实体，可见性面归 C1 互引。 | C1 批次互引 |
| nop-persistence-rw-CL-406 | `SessionFactoryImpl.interceptors`(:86,249-262) copy-on-write 换引用但字段非 volatile；`LoadedOrmModel.ormInterceptor`(:33,58-60) 同为非 volatile 热写字段。可见性面归 C1 互引（C2-007 只立案协议面）。 | C1 批次互引 |
| nop-persistence-rw-CL-407 | `runInNewSessionAsync` 的 `session.flush()`(:258) 在完成线程执行——若调用方事务为线程绑定，flush 的 DML 落在完成线程的事务（或自动提交）上，破坏调用方事务原子性。 | C3 异步链维度 |
| nop-persistence-rw-CL-408 | `EntityPersisterImpl.batchLoadAsync`(:160-173) 分片分支 `if (future == null)` 为死代码（future 初始化为 voidPromise 不可能为 null）——逻辑卫生，非并发。 | P3 备忘 |

## 汇总

- **发现总计**：11 条。P1×5（C2-001 ~ C2-005）、P2×4（C2-006 ~ C2-009）、P3×2（C2-010 ~ C2-011）。
- **CL 线索总计**：9 条（CL-400 ~ CL-408）。
- **末位编号**：发现 `nop-persistence-rw-C2-011`（批次 1 独占段 001~099，已用至 011）；线索 `nop-persistence-rw-CL-408`（段 400~499，已用至 408）。
- **核心结论**：本模块 JVM 级锁为 **0**，并发正确性完全押注在"session 单线程（context 绑定）"契约上；而平台并发语义（executeBlocking(ordered=false)、executeWithContext 并发进入、异步完成线程收尾）已使该契约存在多条可具体化的破口（C2-001/002/003 同根：F10 并发守卫缺失）。引擎级共享缓存的两处复合操作协议缺口（C2-004 read-repair、C2-008 reload 失效）与一处 DB CAS 谓词退化（C2-005）为独立缺陷。`JdbcEntityPersistDriver` 的 volatile 单槽 SQL 缓存经逐点核验为良性（源码注释属实）。
- **测试保护力备注**：本批次未发现任何针对上述并发场景的并发回归测试（nop-orm 测试树以功能测试为主）；按口径"测试代码不是生产守卫"，保护力缺口统一由 C10 批次立项，本文件不重复计数。

---

# 批次 2/4（nop-orm-eql）

- **批次**：批次 2/4（预拆制）。本批次只覆盖 1 个源码根：`nop-persistence/nop-orm-eql/src/main/java`（165 files / 39,510 lines——EQL 语言与 SQL 生成）。批次 1 内容（C2-001~011、CL-400~408）保持原样未改动。
- **审计方式**：盲审第 1 轮。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅审计当前 live code（分支 `concurrency-audit-remediation`）。为追加本节与更新末尾汇总而读取的本文件既有内容，仅用于格式对齐与编号衔接（批次 1 已用编号），未作为本批次发现的证据。
- **判级口径代入**：EQL 编译/SQL 生成被全平台数据访问调用；编译期对象通常每次 compile 全新实例（C1 批次 2 已核实线程封闭），本批次审锁面：编译缓存 miss 时的锁内编译（bin 锁内重活）、SQL 生成器状态共享、宏/函数库注册表的锁纪律。跨批次编号衔接：发现自 C2-012 起、CL 自 CL-410 起。

## 0. 锁载体盘点（nop-orm-eql 源码根全量，165 files）

对全部 main 源文件执行 `synchronized|ReentrantLock|ReadWriteLock|StampedLock|Collections.synchronized|.lock(|LockSupport|volatile|Atomic[A-Z]|ConcurrentHashMap|.wait(|.notify|Condition` 全量 grep，结果：

| 载体 | 数量 | 位置 | 判定 |
|---|---|---|---|
| `synchronized` 方法/块 | **0** | — | 本源码根无任何 JVM 监视器锁/显式锁 |
| `ReentrantLock`/`ReadWriteLock`/`StampedLock`/`Condition`/`LockSupport` | **0** | — | 无 |
| `Collections.synchronizedXxx` | **0** | — | 无 |
| `wait/notify/notifyAll` 等待点 | **0** | — | 无（C2 维度 F5"等待点超时"检查项零命中） |
| `volatile`/`Atomic*` | **0** | — | 无 |
| `ConcurrentHashMap` | 1 | `SqlExprMetaCache.entityMetas`（SqlExprMetaCache.java:24） | 模块**唯一** JVM 锁载体（bin 锁），见 C2-012 |
| `putIfAbsent` | 1 | `SelectResultTableMeta.buildUnderscoreIndex`（:34-40） | 构造期线程封闭：构造器内 fresh HashMap，构造后经 final 字段安全发布（:27,31），无共享可变面，不立案 |
| `static final HashMap`（类初始化后只读） | 1 | `SingleColumnExprMeta.s_meta`（:61-71） | static 块一次填充（:63-71），此后仅 `valueExprMeta` 读（:73-75）；class-init 锁保证安全发布，填充后无任何写入点——**误报排除，不立案** |
| 静态共享可变结构（外部 runtime 持有，经生成类暴露） | 2 | ANTLR 生成 `EqlParser`/`EqlLexer` 的 `_decisionToDFA`/`_sharedContextCache`（antlr4-runtime 4.13.2） | 见 CL-411 |

**宏/函数库注册表锁纪律**：orm-eql 源码根内**不存在**任何函数库/宏注册表——SQL 函数是 AST 节点静态分派（`ast/SqlFunction.java`、`SqlAggregateFunction.java`），枚举 `fromText` 全部为 `values()` 遍历（如 `enums/SqlDateTimeType.java:27-36`，无可变注册表），宏经 `astTransformer`（XLang 层）在编译期注入、注册表不在本源码根。**该检查项零发现，如实记录。**

**SQL 生成器状态共享**：`AstToSqlGenerator` 实例字段仅 `dialect`/`enableFilter`/`filteredSources`/`paramValueOrdinals`（:97-111），每次编译 `new`（EqlCompiler.java:99）；`AstToEqlGenerator` 每次 `new`（EqlHelper.java:30、EqlASTNode.java:27）；`SeqAliasGenerator` 计数器实例字段、`EqlCompileContext.getAliasGenerator()` 每次 `new`（EqlCompileContext.java:71-73）；`EqlTransformVisitor`/`ShardMarkerInjector`/`EqlASTOptimizer` 每次编译新建或全静态方法。`EqlParseTreeParser._instance`（:13）是静态单例但无实例状态，`doParse` 每次 `new EqlLexer`/`new EqlParser`（:20-25）——解析实例状态线程封闭。**该检查项零立案。**

**缓存编译计划的驻留物**（跨线程共享物的实例态核查）：`CompiledSql` 不持有 AST；`paramBuilders` 三个实现 `SimpleParamBuilder`（final 字段）、`TenantParamBuilder`（无状态单例 INSTANCE :19）、`EntityPropParamBuilder`（final 字段）的 `buildParams` 均只读入参、无可变实例态。缓存计划跨线程共享**无实例态泄漏**。**误报排除，不立案。**

## 0.1 锁序图（批次 2，文本）

JVM 锁层唯一载体：`entityMetas`（CHM）bin 锁，仅在 `computeIfAbsent` lambda 执行期间持有（SqlExprMetaCache.java:40）。调用链证据：EQL 编译 → `EqlTransformVisitor.resolveEntityTableMeta`（:786,875,1247,1335,2028 五处）→ `EqlCompileContext:66-68` → `LoadedOrmModel.resolveEntityTableMeta:84` → `SqlExprMetaCache.getEntityTableMeta:33`。

bin 锁内出边（lambda 执行期间）：
```
E1: bin → ormModel.getEntityModel          —— 不成立：getEntityModel 在 computeIfAbsent 之前锁外调用（:37）
E2: bin → IDialectProvider.getDialectForQuerySpace（SqlExprMetaCache.java:53）
      默认装配（nopDialectProvider = TransactionTemplateImpl 别名，dao-defaults.beans.xml:102-108）
        → DefaultTransactionManager.getDialectForQuerySpace(:233-241)
        → 配置映射命中时 → DialectManager.getDialect(:76-79) → ResourceComponentManager.loadComponentModel
          （组件缓存 miss = 文件 I/O + 模型解析；命中 = 查表）
      备选装配 JdbcDialectProvider（:48-58，dao-defaults.beans.xml 中注释态、可显式启用）
        → query space 冷路径 → runInTransaction（连接池许可 + conn.getMetaData() 驱动往返）
E3: bin → OrmBinderHelper.buildBinders(:25-41) → binderEnhancer.enhanceBinder
      （IPersistEnv 扩展点；in-tree DefaultOrmColumnBinderEnhancer:26-32 为纯标签判断）
E4: bin → new EntityTableMeta(:50-96)（O(cols+props) 纯内存构造，逐列/组件/关系建 meta）
```

反向边：
```
R1: 事务/连接持有者（runInSession 内触发的 EQL 编译）→ getEntityTableMeta → bin
```

**环判定**：默认装配下 E2 无阻塞点（组件缓存命中后为查表），bin 临界区为亚毫秒内存构造 → 无可具体化死锁环。备选 JdbcDialectProvider 装配 + query space 冷 + 池紧张时，E2（bin 内等待池许可）× R1（事务内编译线程持连接等 bin）构成理论环——见 C2-012 升级条件。in-tree 无任何路径使 lambda 递归进入 `entityMetas`（nop-orm-model 对 eql.meta/compile 零引用，grep 已核）→ 无 CHM Recursive update 面。

---

## 发现清单（批次 2）

### [C2-012] SqlExprMetaCache 的 computeIfAbsent 把实体元数据构造与方言解析整体放进 CHM bin 锁：默认装配为亚毫秒级 bin 持有（P3），备选 JdbcDialectProvider 装配下冷路径将事务/连接获取拉入 bin 锁（升级 P2 条件）

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/SqlExprMetaCache.java:33-55`（关联证据：`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:32,49-50,84`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/DefaultTransactionManager.java:233-241`、`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcDialectProvider.java:48-58`）
- **证据片段**:
  ```java
  public EntityTableMeta getEntityTableMeta(String entityName, boolean allowUnderscoreName) {
      EntityTableMeta meta = entityMetas.get(entityName);
      if (meta != null) return meta;
      IEntityModel entityModel = getEntityModel(entityName, allowUnderscoreName);   // :37 锁外
      if (entityModel == null) return null;
      return entityMetas.computeIfAbsent(entityName,
              k -> createEntityTableMeta(entityModel));                            // :40 lambda 在 bin 锁内执行
  }
  EntityTableMeta createEntityTableMeta(IEntityModel entityModel) {
      IDialect dialect = dialectProvider.getDialectForQuerySpace(entityModel.getQuerySpace()); // :53 出边 E2
      return new EntityTableMeta(entityModel, binderEnhancer, dialect);            // :54 出边 E3+E4
  }
  ```
- **严重程度**: P3（默认装配）；备选 JdbcDialectProvider 装配 + query space 冷启动窗口下升级 P2（升级条件见触发时序/风险）
- **并发场景**: 多请求线程并发 EQL 首次编译（计划缓存 miss → 每实体首次 `getEntityTableMeta`）。共享载体为 `LoadedOrmModel` 单例持有的 `sqlExprMetaCache`（LoadedOrmModel.java:32,49-50），全平台数据访问面共享。
- **触发时序**: 默认装配（nopDialectProvider=TransactionTemplateImpl）：T1 首编译引用实体 E1 的查询 → `computeIfAbsent` 持 E1 所在 bin → lambda 依次执行 `getDialectForQuerySpace`（该 dialect 首次时 `loadComponentModel` = 文件 I/O+模型解析，之后为查表）→ `buildBinders` O(cols)（逐列 binder 解析+enhancer 判定）→ `new EntityTableMeta` O(cols+props) 逐列/组件/关系构造 → 期间 T2 首编译 E2（hash 落同 bin）在 bin 上阻塞，其编译被 T1 的构造串行化；CHM 的 `get` 不阻塞，受影响面仅"并发首编 + 同 bin"。备选 JdbcDialectProvider 装配（可显式启用，beans 中为注释态）：query space 无配置映射时，T1 在 bin 内 `runInTransaction` → 从池获取连接 → `conn.getMetaData()` 驱动往返（毫秒级，池紧张时为池等待秒级）；若池被 R1 边的事务内编译线程占满（每个都持连接等同一 bin），与 T1 互等——理论环成立。
- **现状**: 模块唯一 JVM 锁载体把"方言解析 + binder 增强 + 全实体元数据构造"整体作为 computeIfAbsent lambda 体；正确性无损（无递归回调、无返回值交叉依赖），代价集中于每实体一次的冷启动首编窗口。
- **风险**: 默认装配：启动期首编突发下同 bin 首编串行化，临界区亚毫秒级，可忽略。备选装配：bin 内 DB 往返/池等待放大首编延迟，极端池耗尽时理论互等（触发窗口=每 query space 一次冷解析，窄）。另有一正确性前置缺口：`binderEnhancer`/`dialectProvider` 均为 `IPersistEnv` 扩展点，接口未禁止回调本 cache——用户实现若在 `enhanceBinder`/方言解析内触发 `getEntityTableMeta` 同键递归，将命中 CHM `Recursive update`（ISE/挂死）；in-tree 实现（DefaultOrmColumnBinderEnhancer:26-32、DefaultTransactionManager:233-241）已核实无回调。
- **建议**: (1) 把 `getDialectForQuerySpace` 提到 `computeIfAbsent` 之外（与 :37 的 `getEntityModel` 同样锁外预解析，lambda 只做 `new EntityTableMeta(model, enhancer, dialect)`）；(2) 或 `LoadedOrmModel` 构建期预热全部 `EntityTableMeta`（模型加载期单线程），运行期 cache 退化为只读；(3) 为 `IOrmColumnBinderEnhancer`/`IDialectProvider` 补"实现不得回调 SqlExprMetaCache"契约注释（防扩展点递归）。本条为 C1 批次 2 已登记的 CL-100（SqlExprMetaCache computeIfAbsent bin 锁内重型构造）的锁面完整化：确认构造链含扩展点与（备选装配下的）事务下钻，默认装配判级 P3、维持 watch。
- **失效面枚举**: orm-eql 源码根内 `computeIfAbsent` 仅此 1 处（全根 grep）。同族跨根逐一——nop-orm `OrmDaoProvider:54`（bin 内 `new OrmEntityDao`，纯字段赋值，安全）；nop-orm `OrmTemplateImpl:528`（session 级裸 HashMap 的 computeIfAbsent 内用户 loader，session 线程封闭契约，批次 1 CL-403 已登记）；nop-dao `JdbcTemplateImpl:407/446`（bin 内 `cacheProvider.getCache` 扩展点，通常轻量，watch）；nop-dao `JdbcTemplateImpl:867`（bin 内 `new DbEstimatedClock`，轻量，安全）；nop-dao `DialectManager:63`（computeIfAbsent + synchronized(list)，仅启动期注册路径，锁序单向无环，安全）；nop-dao `SqlDataTypeMapping:50`（bin 内 `new ArrayList().add`，安全）。逐一状态：仅本条命中"锁内重活 + 可下钻事务"形态。
- **信心水平**: 确定（构造链、装配路径与锁语义逐行核实）
- **误报排除**: 不属于"computeIfAbsent 原语本身使用不是问题"——立案点是 lambda 体内的重量与外部依赖面（C2"锁内含 I/O"检查项的载体即 CHM bin 锁）；不按"低频/启动路径粗锁合理"豁免后报 P1/P2——默认装配临界区亚毫秒、可证明的争用热点不成立，故按校准判 P3 并明示备选装配下的 P2 升级条件，而非虚报。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**（P2 升级条件保留：备选 JdbcDialectProvider 装配 + query space 冷路径）

## CL 线索（批次 2）

### [nop-persistence-rw-CL-410]（维度集外线索，不立案展开）nop-orm 根：LoadedOrmModel.compileSql 查-编-放 + TenantCachedQueryPlan 整实例覆盖 put——多租户并发首编下租户计划条目互相踢出，缓存重编永不收敛（修正批次 1 C2-008 对该面的"自愈"判定）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:106-127`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/TenantCachedQueryPlan.java:16-36`（属批次 1 源码根 nop-orm，本批次不立案，仅登记线索转交）
- **触发时序**: 租户 A、B 线程并发首编同一 EQL（`isUseTenantModel=true`）→ 各自 `new TenantCachedQueryPlan`（A 实例持 `{A→csA}`、B 实例持 `{B→csB}`）→ 两次 `put(key, plan)` 后写者胜，缓存只剩 planB；A 的下一请求 `get(key)` 得 planB → `planB.getCompiledSql()` 对租户 A 返回 null（tenantCache 无 A 条目、`compiledSql` 字段亦 null）→ `:110` 的 `getCompiledSql()==null` 分支成立 → 重编 → `new planC{A}` **整体 put** → 踢掉 B 条目；B 到达同理踢掉 A。≥2 租户交替流量下每次请求都 miss 重编，**无收敛点**。批次 1 C2-008 失效面枚举将该面判为"自愈，仅浪费编译"——正确性面成立（每次重编结果正确），但"自愈"不成立：多租户交叠下永不收敛，属 C7/C9 编译放大（持续 CPU 消耗）。另 `TenantCachedQueryPlan.compiledSql`（:18）非 volatile、get/add 无同步（可见性面归 C1）。
- **建议承接**: nop-orm 批次复核 C2-008 失效面枚举中该行；修复方向：plan 实例驻留不替换（首次创建后仅向其 `tenantCache` 做 per-tenant 注册），或 `putIfAbsent` 语义 + 命中 null 租户条目时向**既有** plan 注册。

### [nop-persistence-rw-CL-411]（维度集外线索，不立案展开）ANTLR 生成解析器静态共享 `_sharedContextCache` 为无同步 HashMap、DFA 缓存"写同步/读不同步"——并发冷解析的暴露面所有权在外部 runtime

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/antlr/EqlParser.java:17-19`（`EqlLexer` 同构）；所有权在 antlr4-runtime 4.13.2（对 `~/.m2` 中该 jar javap 核实：`DFA.states` 为 plain `java.util.HashMap`；`ParserATNSimulator.addDFAEdge/addDFAState` 对 map 做 `monitorenter` 同步写；`PredictionContextCache.add` 全程无同步（plain HashMap 的 get+put）；`PredictionContext.getCachedContext` 无 monitorenter）
- **形态与窗口**: 每次 EQL 解析 `new EqlParser`（EqlParseTreeParser.java:20-25）——实例态线程封闭；跨线程共享的只有静态 `_decisionToDFA`/`_sharedContextCache`，在进程冷启动首批并发 SQL 编译（decision 缓存冷）时被多线程并发写。后果以良性为主（重复构造/条目丢失/瞬时空 miss）；HashMap 并发结构修改的极端后果属 runtime 内部缺陷，非本仓代码。生成文件 + 外部依赖 → 不立案展开。
- **处置建议**: watch-only；根治入口在 `nop-kernel/nop-antlr4` 包装层（评估 per-parse `PredictionContextCache` 或解析级隔离），不改生成文件。

### [nop-persistence-rw-CL-412]（维度集外线索，不立案展开）EntityTableMeta.propExprMetas 可变 LinkedHashMap 经 getFieldExprMetas() 逃逸出跨线程共享缓存对象

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/EntityTableMeta.java:44,270-272`（该实例缓存于 SqlExprMetaCache，全平台共享）
- **现状**: `propExprMetas` 仅构造器（:66-85）写入；`getFieldExprMetas()` 返回内部可变 map 引用。全仓 main 读点已逐一核对（`EqlTransformVisitor.java:1471,1819`）均只读遍历，接口 `ISqlSelectionMeta.getFieldExprMetas` 无其他消费者——**当前无竞态**，纯封装性风险：未来任何调用方对该 map 做写操作即构成跨线程共享可变状态。C1 形态，watch-only；建议返回 `Collections.unmodifiableMap` 视图。

### [nop-persistence-rw-CL-413]（维度集外线索，不立案展开）SqlExprMetaCache 以原始实参为缓存键：canonical 与 snake_case 双键各建一个 EntityTableMeta 实例（C9 形态）

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/SqlExprMetaCache.java:33-48`
- **形态**: `getEntityModel` 走 `getEntityModelBySnakeCaseName` 兜底（:45-47）后，缓存键仍是调用方原始实参（:40）——同一实体先后以 `"my_entity"` 与 `"MyEntity"` 访问会各建一个 `EntityTableMeta` 实例（双倍构造与驻留，上界 2×实体数，有界）。触发无需并发（别名 + 规范名先后访问即可），无正确性影响。建议键归一（以 `entityModel.getName()` 为键）。

## 批次 2/4 汇总（nop-orm-eql）

- **发现总计（批次 2）**：1 条。P3×1（C2-012）。P0/P1/P2：0。
- **CL 线索总计（批次 2）**：4 条（CL-410 ~ CL-413，其中 CL-410 为跨根转交线索且含对批次 1 C2-008"自愈"判定的修正）。
- **累计末位编号（批次 1+2）**：发现 `nop-persistence-rw-C2-012`；线索 `nop-persistence-rw-CL-413`。（批次 3/4 起发现自 C2-013、CL 自 CL-414 续编。）
- **核心结论**：nop-orm-eql 源码根 JVM 锁载体仅 1 处（`SqlExprMetaCache.entityMetas` CHM bin 锁），无 synchronized/显式锁/条件等待/volatile-atomic 复合面；锁序图无环（默认装配）。模块并发纪律依赖"编译对象每次新建、线程封闭"——已逐项核实成立（EqlCompiler/AstToSqlGenerator/AstToEqlGenerator/SeqAliasGenerator/EqlLexer/EqlParser/paramBuilders 均每次编译新建或无状态），缓存计划驻留物无实例态。C2 实质立案 1 条 P3（bin 锁内构造 + 方言解析下钻，深化 C1 批次 2 的 CL-100，备选装配下有明确 P2 升级条件）；本批次最高价值产出为线索 CL-410（nop-orm 根：多租户查询计划缓存条目互相踢出、重编永不收敛——修正批次 1"自愈"判定，转交 nop-orm 批次）。
- **测试保护力备注**：orm-eql 测试树（TestEql* 系列）全部为功能/方言编译测试，未发现任何覆盖 C2-012 缓存竞态面或并发首编场景的并发回归测试；按"测试代码不是生产守卫"口径，保护力缺口统一由 C10 批次立项，本文件不重复计数。

# 批次 3+4/4（nop-orm-model + nop-dao）

> 盲审第 1 轮初审，仅审 live code。范围：`nop-persistence/nop-orm-model/src/main/java`（元模型）+ `nop-persistence/nop-dao/src/main/java`（DAO 层事务面/执行面）。编号自 C2-013、CL-414 起续编。dao 事务面为全平台数据访问的公共底座，判级按"DAO/元模型被全平台调用"口径。
> 与 C1 批次的分工：C1 批次 3/4 已从可见性角度立案 TransactionRegistry.instance() check-then-act（C1-023 P1）、AbstractTransaction 状态族无同步（C1-025 P2）、JdbcTransaction 惰性取连接竞态（C1-026 P1）、orm-model 冻结守卫族（C1-015~018）。本批次对同一批设施从**锁纪律角度**裁定：应该有什么锁、缺失的复合操作原子性、补锁后的锁序与死锁环评估；不重复计数可见性面。

## 0. 锁载体盘点（本批次 2 源码根全量）

| # | 站点 | 载体 | 判定 |
|---|---|---|---|
| M1 | `orm-model/.../OrmModelValidatorRegistry.java:31,61` | volatile flag + class 字面量 DCL + COW 列表 | **安全**：DCL 配 volatile 完整；`getValidators()` 返回活引用但消费方 `validate()` 走 COW 快照迭代，并发注册不影响本轮校验集合 |
| M2 | `orm-model/.../lazy/LazyLoadOrmModel.java:58,147,172` | `synchronized(entityModelMap)` 单锁 + volatile `topoEntryInited`/`sortedEntityModels` DCL | DCL 结构正确（volatile 写收尾）；`putEntityModel` 同锁失效协议有注释声明且自洽。**缺陷在锁内做外部加载 I/O** → C2-018 |
| M3 | `orm-model/.../OrmEntityModel.java:108,381,418` | `inited` 普通 boolean 冻结/初始化守卫 | 解析/冻结过程**无任何自有锁**：`init()` 为 check-then-act，正确性依赖调用方持锁（LazyLoadOrmModel M2）或模型构建期单线程。可见性面 C1-015~018 已立案；锁面裁定：无锁可审、外部锁依赖未在契约中声明，watch，不另立案 |
| M4 | `orm-model/.../init/OrmModelInitializer.java:236` | 局部 `refMap.computeIfAbsent(...).add(...)` | 线程封闭局部变量，安全 |
| D1 | `dao/txn/impl/AbstractTransaction.java:56,80-102` | `CopyOnWriteArrayList listeners`（非 volatile、惰性初始化） | 迭代面安全（快照迭代器，:374-387 有注释）；**惰性 init 与字段发布不安全** → C2-014（CL-404 必答题裁定在此） |
| D2 | `dao/txn/impl/AbstractTransaction.java:60,105-132` | `LinkedHashMap subTransactions`（无锁、惰性初始化） | 结构修改 vs 组提交/回滚迭代无互斥 → C2-015；状态机复合检查 → C2-016 |
| D3 | `dao/txn/impl/TransactionRegistry.java` + `ContextualizedRegistry`（CHM） | CHM 原语 | put/remove(key,value) 原子；**注册协议层（get→new→put、unregister/register 窗口）非原子** → C2-013；`instance()` check-then-act 为 C1-023（本批次 F3 引用） |
| D4 | `dao/txn/impl/DefaultTransactionManager.java:42-50,221-230` | `transactionFactoryMap`/`txnGroupMap` CHM + `querySpaceToDialectMap` 非volatile 惰性 init | CHM 面安全；`getQuerySpaceToDialectMap()` check-then-act → C2-020 |
| D5 | `dao/jdbc/txn/JdbcTransaction.java:35,53-75,113-125` | `connection` 字段（无锁惰性获取/释放） | 连接生命周期无互斥 → C2-017（可见性面 C1-026 已立案） |
| D6 | `dao/jdbc/impl/JdbcTemplateImpl.java:407,446,867` | `cacheMap`/`clockMap` computeIfAbsent（CHM bin 锁） | bin 内 `cacheProvider.getCache` 扩展点（常规为查表）与 `new DbEstimatedClock`（轻构造）：与批次 2 C2-012 失效面枚举裁定一致，watch，无升级依据 |
| D7 | `dao/dialect/DialectManager.java:63-67` | computeIfAbsent + `synchronized(list)` + COW | 仅启动期注册路径；运行期 `getDialectName` 遍历 COW 快照安全；锁序单向无环，安全 |
| D8 | `dao/dialect/impl/SqlDataTypeMapping.java:47-63` | computeIfAbsent + 裸 `ArrayList.add` / 裸 HashMap 写 | 并发 register 对同一 list 并发 add 可丢元素/损坏内部结构；register 仅方言模型加载期（启动 + dev 热加载窗口）→ CL-415（C1 形态线索） |
| D9 | `dao/seq/SnowflakeSequenceGenerator.java:77-120` | `synchronized generateLong` + `wait(timeout)` | 互斥正确；`wait(offset<<1)` 为超时自唤醒设计（无 notify 配对需求），无丢唤醒；时钟回拨处理正确。单实例全实体 ID 生成串行为 C9 形态 → CL-414 |
| D10 | `dao/utils/DbEstimatedClock.java:23-25,88-117` | volatile `timeData`/`fetching` + `synchronized(this)` | 刷新协议自洽（快路径 return 旧值避免堆积）；**锁内 DB 往返**，首取窗口（data==null）全量串行阻塞 → C2-019 |
| D11 | `dao/jdbc/datasource/ReplicaChannel.java:53,62` | `synchronized recordSuccess/recordFailure` + volatile `cooldownUntil` | 写锁内、读 volatile 混合同步但可见性成立；`consecutiveFailures` 仅锁内访问。安全 |
| D12 | `dao/jdbc/datasource/ReadWriteLoadBalancers.java:62` | `AtomicLong` round-robin 计数器 | 原子；`getAndIncrement()` 2^63 次溢出为负 → 负偏移 IndexOutOfBounds 属纯理论（不可达规模），不立案 |
| D13 | `dao/jdbc/datasource/DynamicDataSource.java:23`、`ReadWriteHint.java:44`、`api/QuerySpaceEnv.java:15` | static ThreadLocal 三族 | 归属 C1 基线分区（C1 ThreadLocal 段）；`runWith*` 均为 try/finally 恢复形式且有「线程池警告」契约注释，本批次无 C2 立案点 |
| D14 | `dao/api/DaoProvider.java:11`、`DialectManager.java:34` | static `_instance` 非 volatile | C1 静态可变状态分区，非锁载体，不另立案 |
| D15 | `dao/dialect/impl/DialectSQLExceptionTranslator.java:66` | static final HashMap（static 块写入） | 仅类初始化写入，类初始化有 HB 语义，安全 |

## 0.1 锁序图（批次 3+4，文本）

```text
LazyLoadOrmModel.entityModelMap（本批次唯一 orm-model 锁）
  └─(锁内, C2-018)→ DynCodeGen.getDynamicEntityModel
       ├→ JDBC 连接池内部锁（Hikari）→ DB 网络 I/O
       ├→ DynCodeGen 租户缓存 ref 锁（synchronized(ref)）
       └→ OrmSession / session-cache（请求内）
DbEstimatedClock.this（C2-019）
  └─(锁内)→ JDBC 连接池内部锁 → DB 网络 I/O
SnowflakeSequenceGenerator.this：叶子（D9）
ReplicaChannel.this：叶子（D11）
DialectManager synchronized(list)：叶子（仅启动期，D7）
AbstractTransaction / JdbcTransaction / TransactionRegistry：无 JVM 锁（D1-D3、D5，本批次立案主体）
DefaultTransactionManager：仅 CHM 原语（D4）

已证实环：无（锁内出边均为"model-map → 池/codegen"单向；加载器回调同一 LazyLoadOrmModel 为同线程可重入，无自环）。
补锁裁定（C2-016/C2-017 修复约束）：若为 AbstractTransaction/JdbcTransaction 引入事务 monitor，
(1) listener 回调必须锁外快照执行——持锁调用 ITransactionListener 外部代码会把用户回调
    （afterCommit 里发消息/开新事务）拉入锁序图，制造不可控出边；
(2) 组事务必须 parent→sub 单向取锁（commit 先 parent 后 sub），禁止任何 sub→parent 反向边
    （当前 rollbackSubTransactions 里 subTxn.rollback(error) 若与跨线程 markRollbackOnly(parent)
    同时持锁即成环）；
(3) 连接获取（池等待可达秒级）不得与 registry/CHM 等全局锁同临界区。
```

## 发现清单（批次 3+4）

### [C2-013] 事务注册协议无原子性：registry get→new→put 复合操作 + runWithoutTransaction/NOT_SUPPORTED 的注销窗口 + REQUIRES_NEW 整体 put 覆盖，并发分支间事务组语义崩塌且提交成功后误报 ERR_TXN_NOT_REGISTERED

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:125-167（getTxnState）、170-182（runWithoutTransaction）、291-299（NOT_SUPPORTED）、285-290+325-357（REQUIRES_NEW）、406-438（cleanupTransaction）`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/DefaultTransactionManager.java:157-182`
- **证据片段**:
  ```java
  // TransactionTemplateImpl.getTxnState —— 查、建、注册三步之间无任何互斥
  ITransaction txn = transactionManager.getRegisteredTransaction(txnGroup);   // :157 get
  if (txn == null && allowCreate) {
      txn = transactionManager.newTransaction(txnGroup);                       // :160 new
      state.newlyCreated = true;
      state.prevTxn = transactionManager.registerTransaction(txn);             // :162 put（覆盖式）
  }
  // runWithoutTransaction —— 注销-执行-重挂窗口
  transactionManager.unregisterTransaction(txn);   // :175
  try { return task.get(); } finally { transactionManager.registerTransaction(txn); } // :179
  // cleanupTransaction —— remove(key,value) 语义依赖注册未被并发覆盖
  if (!transactionManager.unregisterTransaction(state.txn)) {                  // :415
      ex = new NopException(ERR_TXN_NOT_REGISTERED).param(ARG_TXN, state.txn); // :416
  }
  ```
- **严重程度**: P1
- **并发场景**: 同一 IContext 的并行异步分支（`CompletableFuture.allOf` + 平台 `thenOnContext`/`completeAsyncOnContext` 上下文传播——两个分支共享同一 context 属性表，即共享同一个 `TransactionRegistry` CHM 实例）。每个分支各自进入 `@Transactional(REQUIRED)`（TransactionalMethodInterceptor）或 `runInTransaction`。生产面已核实：`runWithoutTransaction` 有在树消费方（nop-datav `NopDatavChatBiBizModel.java:175`、nop-metadata `NopMetaQualityCheckpointBizModel.java:513`），注册协议是全平台 `@Transactional` 的公共底座。
- **触发时序**: 上下文 C 内注册表当前为空。T1、T2 并行进入 `runInTransaction(DEFAULT, REQUIRED)`：T1 执行到 :157 `get` 返回 null（状态 S1）→ T2 执行到 :157 同样返回 null → T1 :160 new 出 `JdbcTransaction_A`、T2 :160 new 出 `JdbcTransaction_B` → T1 :162 `put(A)` 后 T2 :162 `put(B)` **覆盖 A 的注册** → 各自 `open()`（各自从池取连接、autocommit=false）、各自执行任务、各自 commit 自己的 state.txn 引用（两次独立 DB 提交，REQUIRED 的"同上下文同事务"组语义丢失）→ T1 进入 cleanupTransaction :415 `remove(DEFAULT, A)`：map 现存 B，按值删除失败 → :416 构造 `ERR_TXN_NOT_REGISTERED`，:436 **在 commit 已成功之后抛出** → T1 的调用方收到异常 → 上游重试 → 同一批数据重复写入（F2"半个成功"：提交成功但对外报失败）。若第三分支 T3 在 T2 put 之后、T1 cleanup 之前以 REQUIRED 加入：T3 拿到已 opened 的 B，T2 cleanup `close(B)` 关闭连接时 T3 的任务仍在 B 上执行 → 查询/写入落在已关闭连接上。
  变体一（runWithoutTransaction 窗口）：T1 在 :175-:179 之间注销 ambient 事务期间，T2 以 REQUIRED 进入 → get 为 null → 新建并注册 B；T1 finally :179 `put(A)` 再覆盖回 A；T2 cleanup `remove(DEFAULT,B)` 失败 → 同样在提交成功后抛 ERR_TXN_NOT_REGISTERED，且 B 的监听器/close 语义被打乱。
  变体二（NOT_SUPPORTED :295 `unregisterTransaction(state.prevTxn)` 返回 false 即抛 `ERR_TXN_NOT_REGISTERED`）：覆盖发生后被误伤，纯只读路径收到伪失败。
- **现状**: 注册协议的所有复合步骤（查-建-放、注销-执行-重挂、cleanup 的按值删除）都依赖"同一 context 内的注册表操作互斥"这一**未声明的单线程假设**；CHM 只保证了单步原子性，没有任何机制保证协议原子性。C1-023 已立案 `TransactionRegistry.instance()` 的 context-attribute check-then-act（同一设施、更靠外一层），本条立案协议层。
- **风险**: 全平台 `@Transactional` 在"一个 context 内并行异步分支"形态下：提交成功却报错（触发上游重试与数据重复）、REQUIRED 组语义静默退化为多个独立事务、伪 `ERR_TXN_NOT_REGISTERED` 失败、已打开事务对象被并发 close。均为数据一致性/可用性级别后果，且错误路径日志指向 cleanup 而非真实竞态点，极难排查。
- **建议**: (1) 注册协议原子化：`getRegisteredTransaction + newTransaction + registerTransaction` 收敛为 registry 上的 `compute(key, ...)` 或 `putIfAbsent` 语义（putIfAbsent 失败方 close 自己的新建事务并采用胜者）；`runWithoutTransaction` 的注销窗口改为在 registry 条目上挂"挂起标记"而非物理 remove，或为 context 引入 per-context 事务门闩。(2) 若引入 per-context 锁：锁只保护注册表结构变更，**不得**覆盖 task 执行与 commit（commit 会执行 listener 外部回调，见锁序图补锁裁定(1)），否则把整个请求串行化并制造锁内 I/O。(3) cleanup 的 remove 失败路径先做身份比对（当前注册者是否仍为 self），身份不符时降级为告警而非抛错，消除"提交成功报失败"的误报面。
- **失效面枚举**: 同族复合操作逐一核对——(a) `getTxnState` 主分支 :157-164（本条主案）；(b) `getTxnState` 组分支 :131-155（`groupTxn.getSubTransaction`→new→`addSubTransaction`，与 C2-015 交界，结构修改面在 C2-015 立案）；(c) `runWithoutTransaction` :175-179（本条变体一）；(d) `NOT_SUPPORTED` :291-299（本条变体二）；(e) `REQUIRES_NEW` :285-290 + `createNewTransaction` :338/:352（put 覆盖 prevTxn，顺序执行下由 prevTxn 重挂恢复，正确；并发下归入本条同族）；(f) `TransactionRegistry.instance()`（C1-023 已立案，可见性面）；(g) `DefaultTransactionManager.getRegisteredTransaction` :158-168（只读 + addListener，注册原子性面并入 C2-014）。逐一状态：协议层原子性缺陷同根，全部由本条覆盖，无遗漏分支。
- **信心水平**: 确定（代码路径逐行核实；并行分支共享 context 为平台 `thenOnContext` 的既定语义）
- **误报排除**: 不属于"CHM 原语使用不是问题"——立案点不是 put/remove 原语，而是跨多步的 check-then-act 协议（C2 复合操作检查项）；不属于"低频路径粗锁合理"——`@Transactional` 是每请求热路径入口；不属于 C1-023 重复计数——那是 registry 单例创建层的可见性竞态，本条是注册协议层的原子性缺失，根因层位不同。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。getTxnState 查-建-放三步协议亲验成立（TransactionTemplateImpl.java:126-168）；「提交成功后误报 ERR_TXN_NOT_REGISTERED」与 REQUIRED 组语义崩塌后果链具体；round2 调用面枚举（11 类入口含每条 auto-commit SQL 的 SUPPORTS 包装）经抽验成立，可达性被放大，P1 维持

### [C2-014] AbstractTransaction.listeners 惰性初始化 check-then-act（非 volatile 字段）：并发首注册丢监听器；CL-404 必答题裁定——COW 快照迭代面成立、注册原子性面不成立

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:56,80-102,374-387,411-435`
- **证据片段**:
  ```java
  // listener数量少、写少读多；…故用CopyOnWriteArrayList   ← 注释声称 COW 解决并发面
  private CopyOnWriteArrayList<ITransactionListener> listeners;      // :56 非 volatile、惰性创建
  @Override
  public void addListener(ITransactionListener listener) {           // :80
      if (listeners == null)                                         // :81 check-then-act，无锁
          listeners = new CopyOnWriteArrayList<>();
      if (!listeners.contains(listener)) {                           // :83 contains+add 复合
          listeners.add(listener);
          if (listeners.size() > 1)
              listeners.sort(ITransactionListener::compareTo);       // :86 COW.sort 原子
      }
  }
  ```
- **严重程度**: P1（触发链具体但前置装配条件窄，见触发时序；复核可酌情降 P2）
- **并发场景**: 同一 ITransaction 对象上的两个注册线程。公共 API 面：`ITransactionTemplate.addTransactionListener/beforeCommit/afterCommit/afterCompletion`（ITransactionTemplate.java:63-100）供业务在事务内注册回调；框架面：`DefaultTransactionManager.getRegisteredTransaction:165`、`EntityPersisterImpl.evictGlobalCache:560`（全局缓存开启时每修改实体注册一个 onAfterCommit 逐出回调）。回调消费面：`invokeListener`（commit/rollback 线程）。
- **触发时序**: 前置条件：装配中 `nopDefaultTransactionListener` bean 缺失且 `transactionMetrics` 未注入（`newTransaction:200-204` 两个条件均不成立时，新建事务的 listeners 保持 null——`@Nullable` 注解与无 @Inject 的 setter 证明该装配合法存在）。此时上下文 C 的活动事务 T 上，两个并行分支分别修改实体 A、B 并各自走到 `evictGlobalCache` → `addTransactionListener` → `txn.addListener(L_A)/（L_B）`：T1 执行到 :81 读 `listeners==null` → T2 执行到 :81 同样读 null → T1 :82 创建 list1、加入 L_A、:56 字段赋 list1 → T2 创建 list2、加入 L_B、字段赋 list2 **覆盖 list1** → commit 线程 `invokeListener` 只遍历 list2：**L_A 的 onAfterCommit 永不执行** → 实体 A 的全局缓存条目在提交后不被逐出 → 后续读命中陈旧缓存，直至 `CFG_ENTITY_GLOBAL_CACHE_TIMEOUT` TTL 过期（有界但真实的陈旧读）。若 L_A 是业务的 `afterCommit(txnGroup, action)`（如发消息/写 outbox），则该动作静默丢失（F2"半个成功"）。
- **现状**: 代码注释（:54-55）与测试（TestAbstractTransactionListeners.testConcurrentAddWhileInvoking）声称 COW 已解决"迭代 vs 并发注册"竞态——该声称**对迭代面成立**（COW 迭代器是快照，:374-387 `invokeListener` 与注册并发安全、:86 sort 原子），CL-404 请求核对的正是这一点，裁定：**迭代/回调交错面无缺陷**。但 COW 只修了容器，没修复合操作：字段惰性初始化（:81-82）与 contains-then-add（:83-85）仍无互斥，字段本身非 volatile（无安全发布）。
- **风险**: 监听器静默丢失且无任何日志/异常：缓存逐出缺失 → TTL 内陈旧读；afterCommit 副作用（消息、补偿、索引刷新）丢失。丢失是永久性的（本轮事务终态通知永不补发），且只在特定装配 + 并发首注册组合下复现，极难归因。
- **建议**: (1) `listeners` 改为构造器创建的 final COW 列表——零锁消除惰性 init，最简修复（ListenableFuture/NetMonkey 式惯例）；或 (2) 字段加 volatile + 惰性 init 用 `synchronized(this)` 双检（与 OrmModelValidatorRegistry 同构）；(3) contains+add 复合若需去重语义用 `addIfAbsent`（原子版 contains+add）；(4) `clearListeners()`（:100-102，close 路径）与并发 add 的交错按"close 后注册视为终态违规"补 warn 日志即可（终态后注册本就无回调时机，非正确性缺陷）。
- **失效面枚举**: 同族逐一——(a) `addListener` :80-88（本案）；(b) `removeListener` :91-94（COW.remove 原子，安全）；(c) `invokeListener` :374-387（快照迭代，安全，CL-404 裁定成立面）；(d) `invokeOpenListener`/`invokeCloseListener` :411-435（先 `new ArrayList<>(listeners)` 快照再回调，安全；`clearListeners` 丢并发注册已定性为终态边界）；(e) `getListeners()` :96-98（返回活引用，发布面随字段修复一并解决）；(f) 子类 JdbcTransaction/GroupTransaction（无自有 listener 逻辑，随基类修复覆盖）；(g) 同设施异根兄弟 `NopSpringTransactionFactory`（nop-spring，包外，Spring TransactionSynchronization 机制，不在本批次源码根，登记为跨根核对项）；(h) DefaultTransactionManager.newTransaction/getRegisteredTransaction 的注册调用点（单线程创建路径，安全）。逐一状态：唯一缺陷点为 (a) 的惰性 init + 字段发布，修复后全族闭合。
- **信心水平**: 确定（竞态本身确定；P1 判级依赖"无默认监听器装配 + 并行分支首注册"前置栈的真实出现频率）
- **误报排除**: 不属于"并发容器原语使用不是问题"——缺陷在字段惰性初始化的复合操作而非 COW 容器；不属于"测试已覆盖"——testConcurrentAddWhileInvoking 是**单线程重入测试**（回调内 addListener），从未构造真正的双线程交错，按"测试代码不是生产守卫"口径不构成保护力；不与 C1-025 重复——那是状态族可见性立案，本条是监听器注册协议的原子性立案，字段与操作均不同。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**降级 P1→P2**。竞态机制本身亲验成立（listeners 惰性 init check-then-act + contains+add 复合 + 字段非 volatile；CL-404 裁定「迭代面安全/注册原子性面缺陷」确认）；但 P1 的装配前置**不成立**：nopDefaultTransactionListener 于 orm-defaults.beans.xml:17 以 ioc:default 常驻装配、transactionMetrics 于 dao-defaults.beans.xml:23 与 JdbcFactory.java:66 双路径强制注入——默认装配下 newTransaction 必加监听器（DefaultTransactionManager.java:200-204），listeners 恒非 null。触发需「非默认装配（双监听器均缺失）或外部事务同步互操作 + 同事务并行分支首注册 + 窗口交错」≥3 条件合取，按判级口径（罕见条件合取降 P2）与 campaign 先例（P1 需部署面证据）降 P2；初审自留「复核可酌情降 P2」选项本轮采纳；修复方案不变（构造期 final COW 列表）

### [C2-015] subTransactions（裸 LinkedHashMap）结构修改与组事务提交/回滚迭代无互斥：组提交迭代期间并发注册的子事务静默不提交，写数据在 close 时回滚丢失

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:60,105-111,194-210,212-221,351-372,437-451`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:129-144`
- **证据片段**:
  ```java
  private Map<String, ITransaction> subTransactions;              // :60 裸 LinkedHashMap，无同步
  @Override
  public void addSubTransaction(ITransaction subTxn) {            // :105
      if (subTransactions == null)
          subTransactions = new LinkedHashMap<>();
      ITransaction oldTxn = subTransactions.put(subTxn.getTxnGroup(), subTxn);  // :108 结构修改
      ...
  }
  protected void commitSubTransactions() {                        // :365
      if (subTransactions != null) {
          for (ITransaction subTxn : subTransactions.values()) { // :367 提交线程迭代
  ```
- **严重程度**: P1
- **并发场景**: 多数据源事务组（`txnGroupMap` 把多个 querySpace 映射到同一 mainGroup；ITransaction 接口注释明示"一个事务分组可能包含多个数据库的子事务"）。同上下文并行异步分支各用**不同 querySpace**：先到者在 `getTxnState` 组分支（TransactionTemplateImpl:133-144）向已打开的 groupTxn `addSubTransaction`，同时另一分支完成业务触发 `groupTxn.commit()`。
- **触发时序**: 组事务 G 已 opened（mainGroup=DEFAULT，子事务 S_1=querySpaceA）。T1（querySpaceB 分支）进入 `getTxnState`：:133 取到 groupTxn=G，:135 `txn==null && allowCreate` → :137 newTransaction(B) → :138 `G.addSubTransaction(B)`（尚未执行）；同一时刻 T2 的业务完成进入 `G.commit()` → beforeCommit :293 `commitSubTransactions()` :367 开始迭代 `subTransactions.values()`（当前仅 S_1）→ T1 :108 `LinkedHashMap.put` 结构修改与迭代并发 → 两种交错后果：**(i)** T1 的 put 触发 CEMT（ConcurrentModificationException）从 commit 内部炸出，G 被上层按失败回滚——S_1 已提交与 G 回滚的组合语义未定义；**(ii)** put 恰在迭代越过 tail 后落地（无 CEMT）：B **不在本轮提交集合中** → T1 的 commitTransaction 因 `state.newlyOpen==false`（组由主路径打开，:385 只提交新建层）不提交 B → G 的 afterCompletion 结束后 B 无人提交 → cleanup `G.close()` → `closeSubTransactions` `IoHelper.safeClose(B)` → **B 的连接关闭即回滚，T1 已执行的全部写数据静默丢失**，T1 无任何异常感知（F2"半个成功"最重形态）。
- **现状**: 监听器列表族（listeners）已为并发面改用 COW（:54-56 注释自证作者认知该事务对象存在跨线程并发），而同一对象上的 subTransactions 仍是裸 LinkedHashMap 惰性初始化——同一状态机的两个集合部件同步纪律不一致，结构修改与值迭代之间无任何 happens-before。
- **风险**: 多数据源请求 + 组内并行分支形态下：写丢失（无感知回滚）、组提交 CEMT 炸穿、组部分提交部分回滚的中间态。多租户/分库部署（每租户独立 querySpace 是 txnGroupMap 的设计场景）随并行度上升触发概率线性上升。
- **建议**: (1) subTransactions 与 listeners 采用同一纪律：若保持"注册期单线程、提交期单线程"假设则必须在类契约中**显式声明**并加运行时守卫（F10：提交开始后 addSubTransaction 抛 fail-fast），当前两者皆无；(2) 最小修复：subTransactions 惰性 init 加 `synchronized(this)`、`commitSubTransactions/rollbackSubTransactions/closeSubTransactions/openSubTransactions` 迭代均改用 `new ArrayList<>(subTransactions.values())` 快照并在持锁内取快照——快照外的迟到子事务按 (1) 的守卫显式拒绝，杜绝静默丢弃。
- **失效面枚举**: subTransactions 全部消费点逐一——(a) `addSubTransaction` :105-111 / `removeSubTransaction` :114-118（结构修改面，本案）；(b) `commitSubTransactions` :365-372（迭代面，本案后果 ii）；(c) `commitSubTransactionsAsync` :351-363（同构迭代，async 路径同后果）；(d) `rollbackSubTransactions` :194-210 / `rollbackSubTransactionsAsync` :212-221（迭代面，回滚丢子事务同构）；(e) `closeSubTransactions` :437-443 / `openSubTransactions` :445-451（迭代面）；(f) `getSubTransaction`/`getSubTransactions` :120-132（读面，CHM 化后自愈）；(g) `isSubTxnRollbackOnly` :146-154（迭代面）。逐一状态：所有迭代点共享同一裸 map，单点修复（快照+守卫）全族闭合；无第五处遗漏消费点（全文件 grep 核实）。
- **信心水平**: 确定（CEMT 后果确定；后果(ii)的静默丢写依赖 put 落在迭代尾部之后的窗口，时序可具体化）
- **误报排除**: 不属于"单线程事务假设下的误报"——并行分支共享 context 是平台上下文传播的既定语义，且 listeners 注释证明该类的设计预期包含跨线程访问；不属于 C2-013 重复——C2-013 是注册表协议（put 覆盖/误报异常），本条是事务对象内部集合的结构-迭代互斥，缺陷点、后果（丢写 vs 误报）均不同。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。subTransactions 裸 LinkedHashMap 与组提交迭代无互斥亲验成立（AbstractTransaction.java:105-111/:365-372）；静默丢子事务（close 即回滚）后果具体；多 querySpace 组事务为 txnGroupMap 设计场景非误用形态，P1 维持

### [C2-016] 事务状态机复合检查无互斥：open 双开、跨线程 markRollbackOnly 与 commit 检查窗口交错——锁纪律裁定（对照 C1-025：应有 txn-monitor/CAS 状态 + parent→sub 单向锁序，listener 回调必须锁外执行）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:52,135-154,269-294,311-349,389-399`
- **证据片段**:
  ```java
  private boolean opened;                 // :52 非volatile、无锁
  private boolean rollbackOnly;           // :57
  @Override
  public void open() {                    // :390
      if (opened)                         // :393 check
          throw newError(ERR_TXN_ALREADY_STARTED);
      invokeOpenListener(); doOpen(); openSubTransactions();
      opened = true;                      // :398 act
  }
  public void beforeCommit() {            // :287
      if (isRollbackOnly())               // :288 检查点
          throw newError(ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT);
      invokeListener(...); commitSubTransactions();
  }
  ```
- **严重程度**: P2（in-tree 无跨线程 markRollbackOnly 生产调用方，交错面经公共 API/Spring 互操作暴露——理论风险已具体化到 API 面，但缺在树触发链，按校准不判 P1）
- **并发场景**: (a) 同上下文并行分支同时对同一未注册事务对象调 open（REQUIRES_NEW 变体下各自 newTransaction 不经过本条；经 C2-013 的注册覆盖后两个分支共用一个 state.txn 引用时可行）；(b) 异步分支失败路径经公共 API `ITransaction.markRollbackOnly`（或 Spring 互操作 `NopSpringTransactionFactory:203` 的同名实现所代表的生态约定）标记回滚，与主线程 commit 并发。
- **触发时序**: (a) T1、T2 同时进入 `open()`：均在 :393 读 `opened==false` → 均通过检查 → 两次 `doOpen()`（JdbcTransaction.doOpen → getConnection0 → **从池取两个连接**，其一在 close 时才释放，事务期间双连接半数时间闲置）→ 两个分支读写各自的 connection，事务隔离语义完全未定义。(b) T1 主线程进入 `beforeCommit()` :288 读 `isRollbackOnly()==false` → T2 异步分支业务失败调用 `markRollbackOnly(err)` :136-137 置 `rollbackOnly=true` → T1 继续 `doCommit()` 提交 → **被标记必须回滚的数据已落库提交**，markRollbackOnly 的语义违约；T2 随后无任何感知（其标记被 commit 路径 :299 `rollbackOnly=false` 复位抹除）。
- **现状**: 状态机的全部复合检查（open 幂等检查、rollbackOnly 检查-提交窗口、close 与 commit 竞态）依赖"事务对象单线程使用"的未声明假设。C1-025 已立案该状态族无同步的**可见性**面（跨线程读写不建立 HB）；本条从锁纪律角度裁定缺失的**互斥**面并给出补锁约束：这正是"这些站点应该有什么锁"的必答项。
- **风险**: (a) 双开双连接造成池容量放大与事务语义混乱；(b) rollback-only 标记被并发提交击穿——补偿型事务（TCC/重试框架对 markRollbackOnly 的典型用法）的失败语义静默失效。触发依赖扩展方对公共 API 的跨线程使用，框架自身当前不产生该交错。
- **建议**: (1) 引入事务 monitor 或 `AtomicReference<State>`（OPENED/COMMITTING/COMMITTED/ROLLING_BACK/CLOSED）CAS 状态机：open/commit/rollback/close 的进入以 CAS 到过渡态为门，重复 open/已提交后 rollback 得到确定性错误而非交错；(2) **锁序硬约束**（本裁定核心）：组事务 parent→sub 单向取锁（C2-015 修复若引入 sub 锁，必须由 parent 锁内获取，禁止反向边——`rollbackSubTransactions` 持 parent 锁调 `subTxn.rollback` 与跨线程 markRollbackOnly(sub 持锁) 标记 parent 的组合即成环）；(3) **listener 回调锁外执行**：`invokeListener` 的外部用户代码不得持事务锁调用（afterCommit 内发消息/开新事务会把不可控出边拉入锁序图；正确形态是锁内快照、锁外回调——现 COW 快照机制恰好已是锁外形态，补锁时不得改变）；(4) rollbackOnly 置位用 AtomicBoolean，使"标记后提交"至少保证线性一致性（标记落在检查后仍提交，但检查读到的不再是无同步的陈旧值）。
- **失效面枚举**: 状态机复合检查逐一——(a) `open` :390-399 双开窗口（本案 a）；(b) `close` :401-409（与并发 commit 交错：close 后 doCommit 落在已释放连接上——JdbcTransaction.doCommit 的 `connection==null` 早退使其降级为**静默 no-op commit**，并入本案族）；(c) `beforeCommit` :288 / `commitAsync` :314 的 rollbackOnly 检查窗口（本案 b）；(d) `markRollbackOnly` :135-139（置位面）；(e) `commitSubTransactions` 内 `subTxn.isTransactionOpened()` :368 检查-提交窗口（子事务在检查后被并发 close → 跳过提交，与 C2-015 同根不同面，修复联动）；(f) `JdbcTransaction` 连接态复合检查归 C2-017 专案。逐一状态：状态字段族（opened/rollbackOnly/error）的互斥缺失同根，统一由 (1)(2) 修复覆盖。
- **信心水平**: 确定（交错时序成立；判级 P2 因在树无自发触发链）
- **误报排除**: 不属于"C1-025 重复计数"——可见性（HB 缺失）与互斥（复合检查原子性）是两个正交缺陷维度，修复手段不同（volatile/原子发布 vs 锁/CAS）；不按"事务对象天然单线程"豁免——listeners 处的 COW 改造注释与 ITransactionTemplate 的公共并发 API 面证明该假设未被声明也未被守卫（F10 守卫缺失族）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。与 C2-021 共用 CAS 状态机修复确认；round2 三约束反证（listener 锁外/parent→sub 单向/池等待不入全局锁）复核通过
### [C2-017] JdbcTransaction 连接生命周期无互斥：并行分支双取池连接（其一泄漏至池超时）+ eagerRelease 提交后重取僵尸连接、其写数据在 close 时静默回滚——锁纪律裁定（对照 C1-026）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/txn/JdbcTransaction.java:35,53-75,77-100,113-125`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/txn/JdbcTransactionFactory.java:62-64`
- **证据片段**:
  ```java
  private Connection connection;                       // :35 无同步
  @Override
  public Connection getConnection() {                  // :61 惰性取连接，check-then-act
      if (connection == null) {
          connection = dataSource.get();               // :64 池获取（可阻塞秒级）
          if (isTransactionOpened()) {
              if (JdbcHelper.getAutoCommit(connection, dialect))
                  JdbcHelper.setAutoCommit(connection, false, dialect);
          }
      }
      return connection;
  }
  protected void doCommit() {                          // :92
      if (connection == null) return;                  // :93 静默 no-op
      JdbcHelper.commit(connection, dialect); restoreAutoCommit();
      if (eagerReleaseConnection) releaseConnection(); // :99 提交后置 null
  }
  ```
- **严重程度**: P1
- **并发场景**: 同一 REQUIRED 事务对象上的并行异步分支（同上下文共享事务为平台既定语义，TransactionTemplateImpl.cleanup :426 注释自证"回调函数中有可能调用getConnection导致实际上创建了非事务性的连接"——未 open 即取连接是框架认可流，因此 getConnection 的并发面真实存在，不限于误用）。
- **触发时序**: **双取泄漏**：事务 T（未 open 的 SUPPORTS/newlyCreated 态或 open 前窗口）上 T1、T2 并行执行 DB 操作：T1 进入 :62 读 `connection==null` → T2 读 `connection==null` → T1 :64 `dataSource.get()` 取连接 C1 → T2 :64 取连接 C2 → 两个引用先后写 `connection` 字段（后写者胜，设为 C2）→ T1 的操作实际持有 C1 局部引用继续执行，**C1 脱离事务对象管辖**：close/commit 只作用于字段中的 C2 → C1 保持 autocommit/借出态直到其上语句结束被池回收（Hikari 默认无 leakThreshold 时最长 idleTimeout 级别），池容量被双倍放大；若 T1 在 C1 上开启了写事务（:67 setAutoCommit(false) 同样竞态）则该写从未提交。
  **僵尸连接丢写**：eagerReleaseConnection=true（JdbcTransactionFactory 默认 :23）时，T1 commit 路径 :99 `releaseConnection()` 置 null → T2 异步续体（同事务内未链入主 stage 的并行分支）此刻调用 getConnection：`connection==null` → :64 重取新连接 C3 → `isTransactionOpened()==true`（close 尚未执行）→ :67 对 C3 setAutoCommit(false) → T2 的全部写进入 C3 上的**新隐式事务** → 无人再提交 C3 → cleanup `doClose` → `releaseConnection` `safeClose(C3)` → **T2 的写静默回滚，T2 无异常感知**。
- **现状**: 可见性面（connection 字段无 HB）已由 C1-026 立案 P1；本条立案**互斥面**并回答"应该有什么锁"：连接获取/提交/回滚/释放需要以事务对象为 monitor 串行化，或在 CAS 状态机（C2-016 建议(1)）中把"取连接"并入状态迁移；当前两者皆无，且 :93 的 null 早退把竞态后果从"报错"降级为"静默 no-op"（F9 形态的吞状态）。
- **风险**: 连接池容量放大与借出泄漏（负载上升时以 pool exhaustion 形式爆发，表象远离根因）；僵尸连接写丢失为最重后果——与 C2-015 的丢写同构，但根因在连接生命周期而非集合迭代。两者叠加（组事务 + 并行分支）时丢写窗口成倍加宽。
- **建议**: (1) 以事务对象为锁串行化 `getConnection/doCommit/doRollback/releaseConnection/doClose` 五点（临界区内含池获取——池等待可达秒级，但同事务操作本应串行，且池内部锁为叶子节点，无环风险；**禁止**在该锁内再取 registry/CHM 等全局锁，遵守锁序图补锁裁定(3)）；(2) 或并入 C2-016 的 CAS 状态机：COMMITTING/CLOSED 态下 getConnection 直接抛 `ERR_TXN_NOT_IN_TRANSACTION` 显式失败（替代 :93 静默 no-op），把僵尸续体从"静默回滚"变为"显式报错"；(3) `doCommit` 的 `connection==null` 早退至少补 warn 日志（当前完全无痕）。
- **失效面枚举**: connection 全部触点逐一——(a) `getConnection0` :53-57（doOpen 内单线程调用，安全）；(b) `getConnection` :61-75（本案双取 + 僵尸重取）；(c) `doRollback` :78-89（`connection==null` 早退 + release，竞态族同 (b)；回滚 no-op 的后果轻于提交 no-op，随同一把锁修复）；(d) `doCommit` :92-100（本案 :93 no-op 面）；(e) `restoreAutoCommit` :102-111（null 检查防抖，锁内安全）；(f) `releaseConnection` :113-120（check-then-close-then-null 三步非原子，与并发 getConnection 交错的窗口即僵尸重取，本案覆盖）；(g) `doClose` :122-125（复用 releaseConnection，随修复覆盖）。逐一状态：全部触点共享单一无同步字段，(1) 的单锁方案一次闭合，无游离触点（全文件 grep 核实）。
- **信心水平**: 确定（两条时序均可具体化；双取泄漏的回收时长依赖池配置，标注为有界泄漏）
- **误报排除**: 不属于 C1-026 重复计数——该条立案字段可见性，本条立案生命周期互斥与"静默 no-op/静默回滚"后果链（C1-026 的 P1 判级不覆盖僵尸连接丢写这条独立后果）；不按"并行分支共享事务是误用"豁免——共享 context 语义与 cleanup 注释 :426 均证明该流为框架认可面。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。getConnection 双取泄漏/eagerRelease 僵尸连接重取/doCommit null 静默 no-op 逐行亲验成立（JdbcTransaction.java:59-100/:113-125）；TransactionTemplateImpl cleanup 注释自证未 open 取连接为框架认可流；P1 维持

### [C2-018] LazyLoadOrmModel.getEntityModel 慢路径在 entityModelMap 锁内执行动态实体加载（DB 查询 + 代码生成模块编译）：全键单锁串行化，动态模型平台冷启动吞吐坍缩为串行

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/lazy/LazyLoadOrmModel.java:140-160,172-200`；锁内负载实证：`nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:178-193`、`DynOrmModelProvider.java:89`
- **证据片段**:
  ```java
  public IEntityModel getEntityModel(String entityName) {   // LazyLoadOrmModel:140
      ... // baseModel 命中快路径；CHM get 命中快路径
      synchronized (entityModelMap) {                        // :147 单一全局锁
          entityModel = entityModelMap.get(entityName);      // :149 二次检
          if (entityModel != null) return entityModel;
          entityModel = this.loadEntityModel(entityName);    // :152 锁内外部加载
          if (entityModel == null) return null;
          putEntityModel(entityModel);                       // :154 同锁失效 topo
      }
      return entityModel;
  }
  // DynCodeGen.getDynamicEntityModel —— 锁内实际负载：
  //   entityDao.requireFirstByExample(...)  → DB 查询（连接获取 + 往返）
  //   codeCache.requireEnabledModule(...)   → 动态模块代码生成/编译
  ```
- **严重程度**: P2
- **并发场景**: 动态元模型平台（nop-dyn `DynOrmModelProvider` 以 `LazyLoadOrmModel` 为全租户共享 ORM 模型，DynCodeGen 为其 IDynamicEntityModelProvider 实现）多请求线程并发首访**不同**动态实体；锁内的 DB 查询路径还会回调同一 ORM 栈（session/persister/连接池）。
- **触发时序**: 租户冷启动（模型缓存清空后首批请求）：T1 `getEntityModel("OrderExt")` 双 miss → :147 持锁 → :152 触发 DynCodeGen：DB 查询 NopDynEntityMeta + 模块代码生成（冷缓存数百 ms 至秒级）→ T2 `getEntityModel("CustomerExt")`（键不同、逻辑上无冲突）双 miss → :147 **阻塞在 entityModelMap 上** → T1 生成完成、T2 获锁、开始自己的秒级生成 → T3 同样排队。N 个不同动态实体的并发首访总延迟 = Σ(单实体生成时长)（纯串行），且期间所有慢路径请求线程被该单锁拴住；另每次 putEntityModel 置 `topoEntryInited=false`，冷启动期穿插的 `getEntityModels` 触发锁内 O(V+E) 拓扑重建，反复失效反复重建，进一步放大持锁窗口。
- **现状**: 双检 + 同锁失效协议本身正确（putEntityModel 的注释明确声明了同锁约束，失效丢失场景已被设计规避）——**正确性无缺陷，缺陷是锁粒度与锁内重负载**：一把锁同时承担"map 结构互斥、单键加载去重、拓扑失效协议"三职责，其中第二职责不需要全键互斥。
- **风险**: 动态平台冷启动/缓存失效后首波请求延迟线性叠加（N 实体 × 单实体生成时长），请求线程池被阻塞线程占满时可级联为全服务不可用（线程池耗尽）；持锁 DB 查询在数据库抖动时把 DB 慢直接放大为模型锁慢，阻塞面从"该实体请求"扩大到"全部动态实体请求"。
- **建议**: (1) 把 `loadEntityModel` 移出同步块：锁外加载、锁内二次检 + put（stampede 时重复生成，可用 per-key `ConcurrentHashMap<String, CompletableFuture<IEntityModel>>` 去重，CompletableFuture 语义天然防重复且不持锁等待）；(2) per-key 锁分段（`synchronized` on interned name 或 Striped<Lock>）须与 `putEntityModel` 的同锁失效协议兼容——topo 失效仍统一走 entityModelMap 锁（失效协议是轻量写，可保留全局锁），加载去重退到 per-key；(3) 锁内拓扑重建（checkTopoEntryReady）保持现状（纯内存 O(V+E)，可接受）。
- **失效面枚举**: LazyLoadOrmModel 锁内重操作逐一——(a) `getEntityModel` 慢路径 :147-157 锁内 loadEntityModel（本案）；(b) `checkTopoEntryReady` :58-75 锁内 `OrmModelTopEntryBuilder.build` + TreeMap 排序（纯内存，O(V+E)，量级可接受，watch 不立案）；(c) `putEntityModel` :172-200 锁内 5 次 map put + shortName 查重（轻量，安全）；(d) `loadEntityModel` :162-164 本身（薄委托，随 (a) 移出）；(e) 唯一实现方 DynCodeGen（DB+codegen，本案锁内负载实证；接口 `IDynamicEntityModelProvider` 为扩展点——**任何实现的自定义加载都继承该锁内执行位**，契约注释应声明"实现耗时不设上界"的现状风险）。逐一状态：单一重载点 (a)，(b)(c) 轻量留 watch。
- **信心水平**: 确定（锁内调用链逐层核实至 DynCodeGen 的 DB 查询与代码生成；串行化后果为确定性推导）
- **误报排除**: 不属于"低频路径粗锁合理"——动态实体首访是元模型平台每实体的常规运行期路径，且锁内含 DB 往返与代码生成两级重 I/O；不属 C9 专案——本条同时覆盖"锁内 I/O 拖长临界区阻塞无关键请求"的正确性相邻面（线程池耗尽级联），按 C2 锁粒度缺陷立案；DCL 结构本身正确部分已明确排除，不虚报。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**

### [C2-019] DbEstimatedClock 首取窗口在 synchronized(this) 内执行 DB 往返：data==null 期全量串行阻塞，DB 故障时阻塞时长按连接超时逐线程叠加

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/utils/DbEstimatedClock.java:23-25,88-117`
- **证据片段**:
  ```java
  private TimeData fetch() {
      TimeData data = timeData;                        // :89 volatile 读
      if (isExpired(data)) {
          if (fetching && data != null)                // :92 快路径：有旧值即返回旧值
              return data;
          synchronized (this) {                        // :95
              if (timeData == data) {                  // :96 二次检
                  fetching = true;
                  try {
                      data = _fetchFromDb();           // :99 锁内 DB 往返
                  } finally { fetching = false; }
              } else { return timeData; }
          }
      }
      return data;
  }
  ```
- **严重程度**: P3
- **并发场景**: per-querySpace 的 `DbEstimatedClock` 实例（JdbcTemplateImpl.clockMap computeIfAbsent 创建），消费方为 `IEntityDao.getDbEstimatedClock`/`IOrmTemplate.getDbEstimatedClock` 的 DB 时间估算需求，非每 SQL 热路径。
- **触发时序**: 进程启动后首个时间估算请求窗口：T1 进入 :95 持锁 → :99 DB 往返（DB 故障时为连接超时，秒级至数十秒）；期间每个到达线程 T_n：读 :89 `data==null`（首次成功前 timeData 恒 null）→ :92 快路径条件 `fetching && data != null` 因 data 为 null 不成立 → 全部阻塞在 :95 → T1 超时异常后 `fetching=false`、timeData 仍 null → 下一线程进入锁重试同一失败往返 → **DB 故障期间估算请求逐个串行承受完整连接超时**；T1 首取成功后 timeData 非 null，:92 快路径永久接管，后续刷新期线程均拿旧值不阻塞。
- **现状**: 刷新协议设计自洽（fetching 标志 + 双检 + 快路径让旧值，注释明示"只有一个线程会真正执行数据库请求"），无正确性缺陷；唯一缺口是**首取窗口的 data==null 期快路径失效**，锁内 I/O 的阻塞面未被覆盖。
- **风险**: 窗口窄（仅首次成功前）但后果集中：DB 启动抖动/网络分区时，所有依赖 DB 时间估算的早期请求线程串行挂起在 monitor 上（每个最长一个连接超时），放大启动期不可用；无超时/中断响应缺失问题（synchronized 不可中断，阻塞不可诊断）。
- **建议**: (1) `_fetchFromDb` 移出同步块：锁外查询、锁内 `timeData` CAS 写（volatile 字段 + `if (timeData == data) timeData = newData` 判定胜者），首取失败不阻塞他人重试（天然 stampede 容忍——多取一次 DB 时间查询代价可忽略）；(2) 或首取失败路径记忆冷却期（冷却期内直接抛显式错误而非重试 DB），把故障期的线程阻塞转换为快速失败。
- **失效面枚举**: 同族"锁内 DB 往返"逐一——(a) DbEstimatedClock.fetch :95-106（本案）；(b) SqlExprMetaCache 备选 JdbcDialectProvider 装配下 bin 内事务下钻（批次 2 C2-012 已立案，不同文件不重复计数）；(c) JdbcTemplateImpl cacheMap/clockMap computeIfAbsent（bin 内无 I/O，D6 判定安全）；(d) LazyLoadOrmModel 锁内 DB 查询（C2-018 专案）。逐一状态：dao 根内余留仅本案一处未案锁内 I/O。
- **信心水平**: 确定（时序确定，量级有界）
- **误报排除**: 不属于"computeIfAbsent/synchronized 原语使用不是问题"——立案点是首取窗口锁内 I/O 的阻塞面；判 P3 而非 P2 因消费方非热路径、窗口为一次性，按校准"可证明的争用热点"不成立，仅为防御性改进候选。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

### [C2-020] DefaultTransactionManager.querySpaceToDialectMap 非volatile 惰性初始化：check-then-act 竞态导致重复计算与理论性不安全发布

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/DefaultTransactionManager.java:45,67-69,221-230`
- **证据片段**:
  ```java
  private Map<String, String> querySpaceToDialectMap;      // :45 非 volatile、IoC setter 或惰性赋值
  protected Map<String, String> getQuerySpaceToDialectMap() {
      if (this.querySpaceToDialectMap == null) {           // :222 check
          this.querySpaceToDialectMap = DaoHelper.getQuerySpaceToDialectConfig();  // :224 act（解析配置）
      }
      return querySpaceToDialectMap;
  }
  ```
- **严重程度**: P3
- **并发场景**: `getDialectNameForQuerySpace`（事务工厂创建路径 :89/:117 `addQuerySpace/addDataSource` 与 `getDialectForQuerySpace` :236）多线程并发调用，且 IoC 未通过 setter 预置该 map 时。
- **触发时序**: T1、T2 并发进入 :222 均读 null → 各自 `DaoHelper.getQuerySpaceToDialectConfig()`（配置解析）→ 两次赋值后写者胜，败者实例丢弃（重复计算，后果轻）；理论面：非 volatile 字段的无同步发布允许读者观察到未完全构造的 HashMap 引用（JMM 重排），读到残缺 map 时方言名查错——需构造器内发布重排才可达，实践中未观察到该失败形态。
- **现状**: 配置通常由 IoC `setQuerySpaceToDialectMap`（:67-69）单线程预置，惰性路径为兜底；无互斥、无 volatile，两缺陷并存但触发前提（未预置 + 并发首调）局限在启动/动态加数据源窗口。
- **风险**: 重复配置解析（轻）；理论性残缺读导致方言路由错误（未具体化到可复现交错，按校准维持理论风险定性）。
- **建议**: 字段加 volatile（一处改动闭合 check-then-act 与安全发布两面）；或构造时 eager 初始化为空 map、惰性路径删除。
- **失效面枚举**: DefaultTransactionManager 可变字段逐一——(a) `querySpaceToDialectMap`（本案）；(b) `transactionFactoryMap`/`txnGroupMap` CHM（原子，安全）；(c) `defaultFactory`/`defaultListener`/`transactionMetrics` 非 final（IoC 启动期写入、运行期读——容器初始化与请求线程间 HB 依赖容器启动语义，属平台级 C1 面，dao 内不另案）；(d) `metricsListener` final 匿名实例（安全）。逐一状态：仅 (a) 存在运行期写路径。
- **信心水平**: 确定（缺陷形态确定；后果以重复计算为主）
- **误报排除**: 不属于"启动路径粗锁合理"豁免的反向滥用——本条未判 P2/P1，恰按"低频路径、后果轻"校准为 P3；本条按 C2 锁纪律（惰性 init 的 check-then-act + 安全发布）口径立案，盲审轮未核对 C1 批次立案清单，如与 C1 可见性面立案重叠，由复核阶段裁定合并。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**。与 C1-028 同一字段族双立案确认（合并一个修复项、台账互引、计数去重，同 C1-028 裁定）

## CL 线索（批次 3+4）

### [nop-persistence-rw-CL-414]（维度集外线索，不立案展开）SnowflakeSequenceGenerator 单实例 synchronized generateLong 为全实体 ID 生成串行点（C9 形态）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/seq/SnowflakeSequenceGenerator.java:77-120`
- **形态与窗口**: `synchronized` 临界区为纯内存运算（纳秒级），互斥正确、`wait(offset<<1)` 超时自唤醒无丢唤醒；但生成器实例被全部实体共享时，单 monitor 成为每条 insert 的串行点。高 TPS 批量插入下为可量化争用候选（无自旋/分段/无 Tb±SDK 级优化）。C9 形态，需 JMH 基准量化后方可立案（对照 nop-benchmark 惯例）。处置：watch-only；若实测争用显著，方向为分段实例（按 seqName hash 分片 monitor）。

### [nop-persistence-rw-CL-415]（维度集外线索，不立案展开）SqlDataTypeMapping.register 并发注册对同一 ArrayList/HashMap 无同步（C1 形态，dev 热加载窗口暴露）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/SqlDataTypeMapping.java:30-63`
- **形态**: `stdToNative.computeIfAbsent(...)` 返回的裸 `ArrayList` 上锁外 `.add`（:50-53）——computeIfAbsent 原子但 add 并发丢失/损坏；`nativeTypes` 裸 HashMap 读（:68）与 register 写无同步。生产启动期为单线程模型加载，安全；暴露面在方言模型 dev 热加载（ResourceComponentManager reload 触发 re-register）与查询并发的窗口。C1 形态，watch-only；建议 register 路径加锁或改并发容器，并在类注释声明"仅加载期可变"契约。

## 批次 3+4/4 汇总（nop-orm-model + nop-dao）

- **发现总计（批次 3+4）**：8 条。P1×4（C2-013/014/015/017）、P2×2（C2-016/018）、P3×2（C2-019/020）。P0：0。
- **CL 线索总计（批次 3+4）**：2 条（CL-414、CL-415）。
- **累计末位编号（批次 1+2+3+4）**：发现 `nop-persistence-rw-C2-020`；线索 `nop-persistence-rw-CL-415`。C2 维度四批次编号已连续闭合。
- **必答题裁定（CL-404 转交项）**：`AbstractTransaction` 监听器列表"并发注册 vs 提交线程迭代"的交错判定——**迭代/回调面安全成立**（COW 快照迭代器 + `invokeOpenListener/invokeCloseListener` 的显式 `new ArrayList<>(listeners)` 快照 + `sort` 的 COW 内部锁，遍历中增删不 CEMT、不影响本轮回调集合），**注册原子性面不成立**（:81-82 惰性初始化 check-then-act 丢首注册、:56 字段非 volatile 无安全发布、:83-85 contains-then-add 复合去重可失效）——立案 C2-014。声称解决并发面的既有测试为单线程重入测试，无生产保护力（归 C10）。
- **dao 事务面锁纪律总裁定**：事务设施整体**零 JVM 锁**，并发纪律依赖"同 context 单线程使用事务对象"的未声明假设；该假设在平台自身的上下文传播语义（同 context 并行异步分支共享注册表与事务对象）下不成立，由此产生注册协议（C2-013 P1）、监听器注册（C2-014 P1）、子事务集合迭代（C2-015 P1）、连接生命周期（C2-017 P1）四处可具体化竞态，状态机复合检查与 markRollbackOnly 跨线程面另立 P2（C2-016）。补锁修复的三条硬约束已写入锁序图：listener 回调锁外快照执行、组事务 parent→sub 单向取锁、连接池等待不入全局锁临界区。
- **orm-model 锁面结论**：解析/冻结过程无自有锁（C1-015~018 已案可见性面，本批次锁面裁定为"依赖外部锁 + 未声明契约"，watch）；`OrmModelValidatorRegistry` DCL 正确；`LazyLoadOrmModel` DCL 与失效协议正确，唯一立案点为锁内动态实体加载 I/O 的全键串行化（C2-018 P2）。
- **测试保护力备注**：dao/orm-model 测试树未见任何双线程交错类并发回归测试（TestAbstractTransactionListeners 为单线程重入测试、TestTransactionTemplate 为顺序传播测试）；按"测试代码不是生产守卫"口径，C2-013~017 的保护力缺口统一由 C10 批次立项，本文件不重复计数。

## 维度复核结论（独立复核合并批，2026-10-18）

- **复核方式**: 与初审/深挖代理独立的第 2 轮合并复核批。本维度 21 条发现（含 round2 新增 C2-021）证据逐条亲验；P1×9 逐条子项复核；历史对照面已解除盲审。
- **裁定统计**: 保留 20 / **降级 1**（C2-014 P1→P2）/ 驳回 0。
- **P1 终裁（8 条维持）**: C2-001/002/003（session 零互斥族×3）、C2-004（read-repair）、C2-005（CAS 抢占退化）、C2-013/015/017（dao 事务设施×3，C2-014 降级后）全部维持——四要素复核通过：证据行级亲验、触发时序具体（同上下文并行/异步分支为平台公开语义且经 round2 调用面枚举放大）、判级符合「罕见但可具体化」口径、F3 闭合。
- **降级裁定（1 条）——C2-014 P1→P2**: 竞态机制成立（CL-404 必答题裁定「迭代面安全/注册原子性面缺陷」确认），但 P1 的装配前置不成立：`nopDefaultTransactionListener`（orm-defaults.beans.xml:17，ioc:default）与 `transactionMetrics`（dao-defaults.beans.xml:23 + JdbcFactory.java:66 双路径）在默认装配下**必然注入**，newTransaction 必加监听器（DefaultTransactionManager.java:200-204），listeners 恒非 null——惰性 init 竞态在默认装配不可达。触发需「非默认装配或外部事务同步互操作 + 同事务并行分支首注册 + 窗口交错」≥3 条件合取，按判级口径与 campaign 先例（P1 需部署面证据，对照 WI 2.3 降级口径）降 P2。初审自留「复核可酌情降 P2」选项，本轮采纳；修复方案不变。
- **三项深挖合并裁定终裁**:
  1. C1-001+C1-023 合并——确认（归 C1 维度终裁，互引）。
  2. **C1-008(c)+C2-008 同位点双登记合并——确认**（reloadModel 未触达 queryPlanCache 亲验；双锚点互引、remediation 计数去重；CL-410 为同缓存相邻独立缺陷不并入）。
  3. C1-027 与 C2-004 同族相邻独立工单（共享失效世代号设计）——确认。
- **round2 新增 C2-021（P3）**: 亲验维持（双读非原子，纳入 C2-016 CAS 修复）。
- **CL 线索处置（批量裁定，15 条全部确认登记）**: CL-400~408、410~415 全部确认；其中 CL-401（syncGet 隐藏重入通道）、CL-402（executeWithContext 仅 warn 平台根因）为 C2-001/002/003 可达性的已验证前提；CL-404 必答题裁定确认；CL-410 对 C2-008「自愈」判定的修正确认成立；CL-411/412/413/414/415 watch-only 定性维持。无驳回。
- **勘误/说明**: dispatch-log 中「C2×21 条（初审）」「P1 立案 11」「72+4=76」三处计数与文件实态不符——本维度初审实为 **20 条**（C2-001~020，批次 1×11 + 批次 2×1 + 批次 3+4×8）、round2 实为 +1（C2-021）共 21 条；全单元初审 71 条 + round2 3 条 = **74 条**；P1 立案实为 **18 条**（C1×5/C2×9/C3×3/C6×1，与 dispatch-log 自己列出的分布一致，其「11」为笔误）。本复核按文件实态计数。

## 最终保留项

| ID | 初判 | 终裁 | 条目 |
|---|---|---|---|
| C2-001 | P1 | **P1** | session 全生命周期零互斥（flush 静默跳过守卫） |
| C2-002 | P1 | **P1** | runInNewSessionAsync 完成线程执行 flush/close 无同步 |
| C2-003 | P1 | **P1** | 会话注册表单槽非原子身份交换 |
| C2-004 | P1 | **P1** | 全局实体缓存 read-repair 迟到 put 覆盖 evict |
| C2-005 | P1 | **P1** | 抢占 CAS 谓词 NULL 退化（独立缺陷，不入 C6-001 合并工单） |
| C2-006 | P2 | P2 | flush 错误路径 dirty 先复位 |
| C2-007 | P2 | P2 | 模型代交换无同步协议 |
| C2-008 | P2 | P2（与 C1-008(c) 合并修复） | reloadModel 不失效 queryPlanCache |
| C2-009 | P2 | P2 | 静默跳过守卫族（重入补发成功回调） |
| C2-010 | P3 | P3 | 每实体事务监听器堆积 |
| C2-011 | P3 | P3 | addDaoListener check-then-act |
| C2-012 | P3 | P3（P2 升级条件） | computeIfAbsent bin 锁内构造链 |
| C2-013 | P1 | **P1** | 事务注册协议无原子性 |
| C2-014 | P1 | **P2（降级）** | listeners 惰性初始化（默认装配前置不成立） |
| C2-015 | P1 | **P1** | subTransactions 裸 Map 组提交丢写 |
| C2-016 | P2 | P2 | 事务状态机复合检查（CAS 状态机修复点） |
| C2-017 | P1 | **P1** | JdbcTransaction 连接生命周期无互斥 |
| C2-018 | P2 | P2 | LazyLoadOrmModel 锁内动态实体加载 |
| C2-019 | P3 | P3 | DbEstimatedClock 首取窗口锁内 DB 往返 |
| C2-020 | P3 | P3（与 C1-028 合并修复） | querySpaceToDialectMap 惰性 init |
| C2-021 | P3 | P3 | REQUIRED 加入路径双读伪 ALREADY_STARTED |

**维度终局**: P1×8 / P2×9（含 C2-014 降入）/ P3×4（含 C2-021），降级 1、驳回 0。
