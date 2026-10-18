# C1 共享可变状态与可见性 — nop-persistence-rw 深度审计（第 1 轮初审）

- **审计单元**: `nop-persistence`（rw 预拆批次 1/4）
- **批次**: 批次 1/4（本批次发现编号从 `nop-persistence-rw-C1-001` 起，001~099 段归批次 1；后续批次由主 agent 接续编号）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度定义**: C1——共享可变状态与可见性。多线程共享的字段/集合/缓存/注册表的线程安全与可见性；check-then-act 竞态；非同步惰性初始化；static 可变状态；ThreadLocal 误用；发布安全。
- **盲审纪律声明**: 本轮为盲审。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅阅读了方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、owner doc `docs-for-ai/02-core-guides/model-first-development.md` 与当前 live code。所有发现均为本轮独立判级，「现状」字段一律填新发现（盲审轮不标已知/已修复）。
- **检查范围**: 仅以下 2 个源码根（src/main，不含 target/）：
  - `nop-persistence/nop-orm/src/main/java`（154f/25,517——IOrmSession/OrmSessionImpl、IOrmTemplate/OrmTemplateImpl、IOrmSessionEntityCache 三实现、SessionFactoryImpl/OrmSessionFactoryBean/DefaultOrmModelProvider/TenantAwareOrmModelProvider/LoadedOrmModel、EntityPersisterImpl/CollectionPersisterImpl/BatchActionQueueImpl/SessionBatchActionQueue、JdbcEntityPersistDriver/JdbcCollectionPersistDriver、OrmBatchLoadQueueImpl、ShardFanoutEngine、SqlLibManager、enc/resource/initialize/mdx 等）
  - `nop-persistence/nop-orm-data/src/main/java`（1f/293——DaoEntityBlockingSource）
  - 跨子模块疑点（orm-eql/orm-model/dao）只作线索提及，不立案（后续批次覆盖）。
- **会话生命周期模型核实（判级前置）**: ORM session 为**上下文绑定**而非线程绑定：`OrmSessionRegistry` 将 session 存入 `IContext.getAttrs()`（`BaseContext.attributes` 为 `ConcurrentHashMap`，`OrmSessionRegistry.java:19-27`）；`nop.orm.session-check-context` 默认 `true`（`OrmConfigs.java:92-94`），session 的读路径经 `checkContext()` 强制在绑定上下文上执行。因此 session 内部字段（一级缓存、batchActionQueue、flusher 等）在**声明用法下是上下文内单线程封闭**，不按默认跨线程共享判级；但平台 `IContext.executeBlocking(task, ordered=false)` 的 javadoc 明示「否则只是选择一个 worker 线程，把 context 设置为上下文对象来执行」（`BaseContext.java:296-308`），且 `executeWithContext` 对同上下文并发执行仅告警不拒绝（`BaseContext.java:254-256`）——**同一上下文被多线程并发执行是平台公开 API 可达的形态**，C1-001/C1-002 以此为触发前提。session 若仅按每事务/每请求串行使用，其内部字段不构成本轮立案对象（已逐一核对，见共享状态清单）。

---

## 共享可变状态清单（字段 × 写者 × 读者 × 同步机制 × 判定）

### A. 引擎级单例状态（跨所有请求/线程共享）

| 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `DefaultOrmModelProvider.ormModel`（:17，非 final 非 volatile） | `clearCache()`（reloadModel/管理操作） | `getOrmModel()`（每次开 session/compileSql，热路径） | 无 | **C1-003 立案** |
| `TenantAwareOrmModelProvider.ormModel`（:16，同上） | `clearCache()` | `getOrmModel()` | 无 | **C1-003 立案（同族）** |
| `LoadedOrmModel.ormInterceptor`（:33，非 volatile） | `DefaultOrmModelProvider.getOrmModel()` 每次调用 `setOrmInterceptor` | `getOrmInterceptor()`（每次 save/update/load 回调） | 无（数据竞争，值收敛） | 并入 **C1-003** |
| `LoadedOrmModel.refCount/needClose`（:35-36） | `OrmSessionImpl` 构造/close | `close()`/`decRef()` | AtomicInteger + volatile（原语正确） | 复合时序 **C1-004 立案** |
| `LoadedOrmModel.entityPersisters/collectionPersisters/sqlExprMetaCache`（:30-32，final） | 构造器内 PersistEnvBuilder | 全部会话 | final 字段冻结语义 | 安全（内容单线程构建后只读） |
| `SessionFactoryImpl.queryPlanCache` 内容（ICache） | `LoadedOrmModel.compileSql` | 同 | LocalCache（平台设施）+ check-then-act | **C1-008 立案（复合操作层）** |
| `SessionFactoryImpl.interceptors`（:86） | `addInterceptor/removeInterceptor`（COW 替换） | `openSession()`（热路径） | 无 volatile/锁 | **C1-005 立案** |
| `SessionFactoryImpl.daoListeners`（:88） | `addDaoListener`（check-then-act + MultiOrmDaoListener 内部 COW） | `getDaoListener()` | 外层字段无同步 | **C1-005 立案（同族）** |
| `SessionFactoryImpl` 其余注入字段（jdbcTemplate/globalCache/queryExecutors/dialectProvider 等） | IoC init 单线程 | 全部 | IoC 启动 happens-before（实践） | 安全（启动后只读） |
| `EntityPersisterImpl`/`CollectionPersisterImpl`/`JdbcEntityPersistDriver`/`JdbcCollectionPersistDriver` 实例字段 | `init()` 单线程（模型加载期） | 全部会话 | init 后只读 + driver 的 SQL 单槽缓存为 `volatile`（`JdbcEntityPersistDriver.java:62-73`，注释声明良性竞争） | 安全（设计良好，非误报项） |
| `OrmTimestampHelper._instance`（:21，static 非 volatile） | `registerInstance`（:27） | `queueSave/queueUpdate` 每次（热路径） | 无 | **C1-006 立案** |
| `EncAssistedHelper._instance`（:48，static final） | 无（不可变引用） | `queueSave/queueUpdate` | final | 安全 |
| `OrmDaoProvider.daoMap/daoByTableMap`（ConcurrentHashMap） | `dao()/daoForTable()` | 同 | CHM + computeIfAbsent（:54）/get-then-put（:69-73，良性重复 put） | 安全（dup 创建良性） |
| `GlobalCacheRegistry` 注册（`OrmSessionFactoryBean.init/destroy` :135-138,190-195） | init/destroy | 平台 | 平台设施，配对对称 | 安全 |
| `DaoProvider.registerInstance(this)`（`OrmDaoProvider.java:46`） | bean init | nop-dao 静态门面 | 静态可变单例，字段在 nop-dao | 跨批次线索（dao 批次覆盖） |

### B. 上下文/会话级状态（上下文内封闭，并发同上下文时变共享）

| 载体 | 归属 | 封闭机制 | 判定 |
|---|---|---|---|
| `OrmSessionRegistry`（per-context attribute） | 每 context 一份 | context 属性 + `checkContext()`（默认开，仅覆盖读路径） | **C1-001 立案** |
| `OrmSessionImpl.flusher/dirty/closed/readOnly/sessionRevVersion/batchActionQueue/batchLoadQueue/sessionCache/onClose` | 每 session | 上下文封闭（声明用法）；写路径 `save/update/delete/lock/flushImmediately/saveDirectly/updateDirectly/deleteDirectly` 未过 `checkContext()`（守卫不对称） | **C1-002 立案（守卫缺失面）** |
| `OrmSessionEntityCache`（HashMap + visiting/temp 快照机制）、`TenantOrmSessionEntityCache`（HashMap + 快照迭代） | 每 session | 上下文封闭；visiting 检查-然后-置位非原子 | 封闭下安全；并发同上下文时归入 C1-002 触发面 |
| `BatchActionQueueImpl.actionMap/flushing/collectionActions/delayTasks`、`SessionBatchActionQueue.queues` | 每 session | 上下文封闭 | 封闭下安全；并发同上下文时归入 C1-002 |
| `OrmBatchLoadQueueImpl.loadQueue（非 volatile）/flushing（volatile）/afterFlushCallbacks` | 每 session | 上下文封闭；但 `thenRun` 回调（:652-661,685-701）会再入队——默认 JDBC 驱动同步完成故单线程，自定义异步驱动即破坏 | **C1-007 立案** |
| `sessionCache`（`new MapCache<>("orm-session-cache", false)` → 普通 HashMap，`OrmSessionImpl.java:1305`） | 每 session | 上下文封闭 | 封闭下安全（注意 threadSafe=false） |
| `DaoEntityResource.lastCheckTime/lastModified` | 每资源实例 | handler 每次 `new`（`DaoResourceNamespaceHandler.java:52`）；若被上游组件缓存跨线程共享则裸奔 | **C1-011 立案（P3）** |
| `DaoEntityBlockingSource` 全部字段（nop-orm-data） | 单例 bean | 启动期写、运行期只读；轮询状态为方法局部变量（`drainTo` :194-213），无共享轮询字段 | 安全（无发现） |

### C. static 可变状态全枚举（基线 C1 第 1 节，本范围内非 import/static 方法的全部命中）

| 位置 | 状态 | 判定 |
|---|---|---|
| `OrmTimestampHelper._instance`（:21）+ `registerInstance`（:27） | static 非 final 单例槽 | **C1-006** |
| `DataBaseSchemaInitializer.specifyQuerySpaces`（:41，`public static String[]`，实例 setter 写静态） | static 可变数组 | **C1-009** |
| `OrmSessionRegistry.CONTEXT_KEY`/各 LOG/`NOT_INITED`（JsonOrmComponent:30，哨兵常量）/`MAX_LOOP_COUNT` 等常量 | 常量 | 安全 |
| `OrmSessionImpl.s_seq`、`BaseContext.s_seq` | AtomicLong | 安全 |
| ThreadLocal | 本范围 3 处命中全在 nop-dao（`ReadWriteHint/DynamicDataSource/QuerySpaceEnv`），dao 批次覆盖 | 范围外 |
| computeIfAbsent 站点（基线 C2 节交叉）：`OrmTemplateImpl:528`（session 级 MapCache）、`OrmDaoProvider:54`（CHM）、`BatchActionQueueImpl:102`（session 级 HashMap）、`MdxQuerySplitter:187`/`AddTenantColInitializer:90`/`DataBaseSchemaInitializer:127`/`XplOrmInterceptorLoader:47-48`/`EncAssistedBackfiller:187`（局部集合） | — | 除 C1-008（compileSql 的 get-then-put 在 LoadedOrmModel，非 computeIfAbsent 形态）外均为局部集合或 CHM，安全 |

---

## 发现条目

### [nop-persistence-rw-C1-001] OrmSessionRegistry 单槽会话注册：check-then-act 创建 + 同上下文并发下会话槽被踩（会话泄漏/僵尸会话/一级缓存分裂）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmSessionRegistry.java:19-27`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java:204-231,244-270,273-280`；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/BaseContext.java:296-308`
- **证据片段**:
  ```java
  // OrmSessionRegistry.java:19-27 —— check-then-act，无同步
  public static OrmSessionRegistry instance() {
      IContext context = ContextProvider.getOrCreateContext();
      OrmSessionRegistry registry = (OrmSessionRegistry) context.getAttribute(CONTEXT_KEY);
      if (registry == null) {
          registry = new OrmSessionRegistry();
          context.setAttribute(CONTEXT_KEY, registry);
      }
      return registry;
  }
  // OrmTemplateImpl.java:214-231 —— 单槽 put/恢复，旧会话恢复依赖 s == session 判定
  IOrmSession oldSession = registry.put(sessionFactory, session);
  try { T ret = callback.apply(session); session.flush(); return ret; }
  finally {
      IOrmSession s = registry.put(sessionFactory, oldSession);
      if (s == session) { session.close(); }
      else { LOG.info("nop.orm.skip-session-close-when-not-current-session"); }
  }
  ```
- **严重程度**: P1
- **并发场景**: 同一 `IContext` 被两个线程并发执行 ORM 访问（平台公开 API 可达：`context.executeBlocking(task, false)` javadoc 明示 unordered 时「选择一个 worker 线程，把 context 设置为上下文对象来执行」；`executeWithContext` 对同上下文并发仅 `LOG.warn` 不拒绝）。HTTP worker 与 worker 线程池线程、或两个 worker 线程。
- **触发时序**（两个独立可具体化交错）:
  - **(a) 注册表双实例**：context 生命周期内首次 ORM 访问即为并发——T1 进入 `instance()` 读 `getAttribute` 为 null（:21）；T2 同时读到 null；T2 `setAttribute(registryB)` 后返回 registryB；T1 继续 `setAttribute(registryA)` 覆盖 T2（:24），返回 registryA。后果：T2 经 `runInNewSession` 把 session 放进 registryB（局部引用），而 context 属性指向 registryA——T2 后续流程中任何 `currentSession()`（:176，重新走 `instance()`）取不到该 session，再开新 session：同一逻辑请求内出现两个独立一级缓存，实体身份不一致、一边的修改对另一边不可见；registryB 中注册的 session 因不再被属性引用而在其帧结束后无人再复用。
  - **(b) 单槽互踩**：T1 `registry.put(sf, session1)`（old=null）→ T2 `registry.put(sf, session2)`（old=session1）→ T1 完成回调后 finally：`put(sf, null)` 移除，返回 session2 ≠ session1 → **session1 跳过 close()**（`OrmSessionImpl.close()` 中的 `loadedOrmModel.decRef()` 不执行 → `LoadedOrmModel.refCount` 永不归零 → 工厂 close 时 persisters 永久延迟销毁）；T2 finally：`put(sf, oldSession=session1)` 返回 null ≠ session2 → **session2 也跳过 close**，且把已被 T1 flush 完、已废弃的 session1 重新注册为「当前会话」→ 后续同上下文 `runInSession` 复用僵尸 session1。
- **现状**: 新发现。代码注释（:224「如果已经unregister，则s为null，这里将不会关闭session」）表明作者考虑过 unregister 情形，但未考虑并发 put 交错下的跳过-关闭分支。
- **风险**: 会话泄漏（每漏一个 session 即一次 `refCount` 计数失衡，`LoadedOrmModel` 关闭路径永久失效）；僵尸会话被后续请求复用导致跨请求实体状态污染；同请求双一级缓存导致丢失更新（乐观锁仅部分兜底）；`skip-session-close` 日志在长时运行中作为唯一症状难以归因。
- **建议**: (1) `OrmSessionRegistry.instance()` 改为 `context.computeAttributeIfAbsent` 语义（用 `attributes.compute` 或先 put 一个 final 空注册表于 context 创建期）；(2) 会话槽 put/restore 改为 `ConcurrentHashMap.remove(key, value)` 两参形式：恢复时用 `registry.remove(sessionFactory, session)` + 比对，跳过-关闭分支改为「registry 中已不是本 session 时仍必须 close 自己持有的 session」（close 幂等，:769-770 有 `if (closed) return` 守卫），绝不因槽位被踩而漏 close；(3) 或最小修复：finally 中无论 `s == session` 与否都调用 `session.close()`（close 幂等且 flush 已在 try 内完成）。
- **失效面枚举**（F3 同族清扫）: 使用该单槽的 7 个入口逐一核对——`currentSession()`(:176，只读，受 a/b 影响)、`runInSession`(:204，走 runInNewSession)、`runInNewSession`(:214，**b 的主现场**)、`runInSessionAsync`(:234)、`runInNewSessionAsync`(:244，**同构 b**：whenComplete 在完成线程上 `registry.put` 恢复 + `s == session` 判定，交错后果相同)、`unregisterSession`(:273)、`registerSession`(:278)。无一有同步保护；`ContextualizedRegistry`（nop-commons，map 为 CHM）原语本身安全，问题全部在复合操作层。
- **信心水平**: 确定（交错序列与后果均可从代码直读推出；触发前提「同上下文并发」为平台公开 API 声明的执行形态）
- **误报排除**: 不属于「无状态单例被并发调用属设计意图」——该槽位是可变会话注册表；不属于「CHM 原语使用非问题」——缺陷在 get/put 复合操作与 finally 恢复协议，非容器原语；`checkContext()`（默认开）不能防御：同上下文两线程的 `isRunningOnContext()` 均为 true。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。instance() 的 get→new→setAttribute 三步无同步逐行亲验成立；两个交错（注册表双实例/单槽互踩）均无需硬件假设；与 C1-023 同族合并终裁**确认**（同一缺陷、单一平台级修复项，台账双锚点保留）

### [nop-persistence-rw-C1-002] Session 写路径缺 `checkContext()` 守卫 + flush/惰性初始化非原子守卫族（同上下文并发下双重 flush、队列丢失）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:176-227（flush）,493-503（save）,534-563（updateDirectly/deleteDirectly）,604-630（lock）,1013-1031（flushImmediately）,272-277（getBatchLoadQueue）,1302-1307（getSessionCache）`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/SessionBatchActionQueue.java:27-43`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionEntityCache.java:42,114,253-284`
- **证据片段**:
  ```java
  // OrmSessionImpl.java:183-186 —— flush 重入门为非原子 check-then-act
  // 如果正在执行flush, 则直接返回
  if (this.flusher != null) {
      LOG.debug("orm.exit-flush-when-another-flush-is-running");
      return;
  }
  ...
  this.flusher = new CascadeFlusher(this, cache);   // 两线程可同时越过 null 检查
  // 对比：写路径入口无 checkContext —— save() 只做 checkValid()+checkNotReadOnly()
  public Object save(IOrmEntity entity) { checkValid(); checkNotReadOnly(); internalSave(entity); ... }
  ```
- **严重程度**: P2（写不出独立于 C1-001 触发前提之外的更宽入口；一旦触发，后果为双写/队列丢失，接近 P1）
- **并发场景**: 与 C1-001 同前提——同上下文两线程并发操作同一 session（unordered executeBlocking / 共享 context 的两个 worker）。
- **触发时序**: T1 进入 `flush()` 读 `this.flusher == null` 通过；T2 同时读 `this.flusher == null` 也通过 → 两个 `CascadeFlusher` 同时执行：各自 `forEachDirty` 遍历同一 `OrmSessionEntityCache`（普通 HashMap + `visiting` 布尔门，两线程互踩 visiting/temp 快照协议，`ConcurrentModificationException` 或静默漏遍历）→ 各自把同一批 `BatchActionHolder` 的 action 列表提交 `driver.batchExecuteAsync`（两线程共享同一 List 引用）→ 同一实体 INSERT 被提交两次（PK 冲突）或回调 `persisterPostSave` 双次执行。同族：`SessionBatchActionQueue.getBatchActionQueue`（:33-41，两线程同时 `queues == null`/`queue == null` → 一个新建 queue 被 `put` 覆盖丢弃，其已入队 DML 动作静默丢失）；`getSessionCache`（:1304-1305，两 MapCache 实例，其一丢弃）、`getBatchLoadQueue`（:274-275）、`getSessionRevVersion`（:158-159，双份 revVersion）。
- **现状**: 新发现。守卫体系不对称：`checkContext()` 全类 22 处调用，但集中在读路径（get/load/internalLoad*/getSessionCache/getBatchLoadQueue/execute*）与 flush()；写路径 `save/update/delete/lock/saveDirectly/updateDirectly/deleteDirectly/flushImmediately/flushSave/flushUpdate/flushDelete` 均未检查——跨线程误用 session 在写路径上无任何 fail-fast。
- **风险**: 声明用法下无影响（上下文封闭）；一旦业务以共享 context + worker 线程形态使用（平台 API 允许），DML 重复提交或静默丢失，且无异常指纹（部分丢失仅表现为偶发懒加载 NPE/唯一键冲突）。
- **建议**: (1) `flusher` 改 `volatile` + `AtomicReference<CascadeFlusher>.compareAndSet(null, f)` 原子占位（复用平台 `AtomicReference`；失败方直接 return 保持现语义）；(2) 为全部写路径入口补 `checkContext()`（对齐读路径），使跨线程误用在最早点响亮失败（F10 运行时守卫）；(3) `SessionBatchActionQueue.queues` 惰性创建改 `computeIfAbsent`（TreeMap→ConcurrentSkipListMap 或保留 TreeMap+同步）。
- **失效面枚举**（F3 同族清扫）: 非原子 check-then-act 守卫全清单——`OrmSessionImpl.flush`（flusher!=null）、`OrmSessionImpl.flushImmediately`（:1017 `this.flusher == null` 同型）、`OrmSessionEntityCache.visiting`（:255,289,321 置位/复位）、`SessionBatchActionQueue.queues`（:33）、`OrmSessionImpl.getBatchLoadQueue`（:274）、`getSessionCache`（:1304）、`getSessionRevVersion`（:158）、`OrmBatchLoadQueueImpl.makeQueue`（:212，另案 C1-007）、`OrmSessionEntityCache.makeEntityCache`（:189）。封闭用法下均安全，但无一具备运行时守卫，全部依赖调用方自觉。
- **信心水平**: 确定（交错后果可从代码直读；触发前提与 C1-001 相同）
- **误报排除**: 不属于「synchronized/CHM 原语使用非问题」——此处恰恰是原语缺失；不属于「低频路径粗锁合理」——这是主路径（每事务 flush）的守卫缺失；单独立案是因为即使 C1-001 修复（注册表原子化），session 内部这些守卫仍独立可破。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。flusher 静默跳过守卫与写路径无 checkContext 的守卫不对称亲验成立；触发前提与 C1-001 相同（同上下文并发），session 内原子守卫为独立修复面

### [nop-persistence-rw-C1-003] 模型提供器 `ormModel` 字段非 volatile：reloadModel 后旧模型可被无限期读取 + 每次 getOrmModel 对共享模型做非同步写（setOrmInterceptor）+ clearCache 两步非原子

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:17,36-49`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/TenantAwareOrmModelProvider.java:16,31-43`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:33,58-60`
- **证据片段**:
  ```java
  // DefaultOrmModelProvider.java:17,36-42,45-49
  private LoadedOrmModel ormModel;                      // 非 final、非 volatile
  public ILoadedOrmModel getOrmModel(IPersistEnv env) { // 热路径：每次开 session/compileSql
      IOrmInterceptor interceptor = interceptorCache.getObject(...);
      if (interceptor != null)
          ormModel.setOrmInterceptor(interceptor);      // 多线程对共享对象的无同步写
      return ormModel;
  }
  public void clearCache() {
      OrmModel ormModel = new OrmModelLoader().loadOrmModel(false);
      this.ormModel = new LoadedOrmModel(env, ormModel); // 步骤1
      this.interceptorCache.clear();                     // 步骤2 —— 两步间读者可见混合态
  }
  // TenantAwareOrmModelProvider.java:16,40-43 —— 同族：this.ormModel = loader.apply(null); this.tenantCaches.clear();
  ```
- **严重程度**: P1
- **并发场景**: 全体 HTTP/worker 线程（`getOrmModel` 为每请求热路径）与管理操作线程（`IOrmTemplate.reloadModel()` → `SessionFactoryImpl.reloadModel()`(:460-463) → `ormModelHolder.clearCache()`；`nop.orm.model-cache-check-change` 默认 true 的动态模型刷新体系）。
- **触发时序**: T1 管理线程执行 `clearCache()`：完成 `this.ormModel = newModel`（普通写，无 volatile/锁）；T2 业务线程此后任意时刻调用 `getOrmModel()`——JMM 下 T2 无 happens-before 边，**允许无限期读到旧 LoadedOrmModel**（实践中何时可见取决于偶然的同步副作用）；后果链一：新模型新增/删除列后，T2 开出的 session 仍按旧实体模型生成 SQL（列不匹配 → SQL 异常，或旧列继续写入 → 与新模型不一致的数据形态）；后果链二（混合态）：T2 在步骤1后步骤2前读取——拿到新 ormModel 但 `interceptorCache` 尚未清，`getOrmModel` 把旧 interceptor `setOrmInterceptor` 到新模型上（写本身又是无同步写，与 T3 的写互相竞争）；后果链三：`ormModel.setOrmInterceptor` 每请求对共享 `LoadedOrmModel.ormInterceptor`（:33，非 volatile）读-改-写，两线程各持新旧 XplOrmInterceptor 交替写，读者可见任意一个。
- **现状**: 新发现。`StaticOrmModelProvider`（final 字段，:8-26）为同族中的正确实现，反证 dynamic 两个 provider 的字段 volatile 缺失非有意设计。
- **风险**: 热刷新模型（reloadModel 是公开运维 API）后集群内各线程模型版本不一致且无自愈时限（无 HB 边即无保证可见）；排障困难——新旧模型症状混杂（部分请求报列不存在、部分正常）。判级依据：引擎级「每请求必经」单例、触发链明确（reloadModel + 持续流量），属「罕见但可具体化的竞态」上限。
- **建议**: (1) 两 provider 的 `ormModel` 字段加 `volatile`（一处关键字修复可见性）；(2) `clearCache` 中先构建后赋值已正确（LoadedOrmModel 构造器内完成 PersistEnvBuilder，final 字段冻结保证内容安全发布），补 `interceptorCache.clear()` 先于赋值或整体放入同步块消除混合态；(3) `getOrmModel` 中 `setOrmInterceptor` 改为仅在 interceptor 实例引用变化时写入（`if (ormModel.getOrmInterceptor() != interceptor)`），把每请求写降为偶发写。
- **失效面枚举**（F3 同族清扫）: IOrmModelProvider 三实现——`DefaultOrmModelProvider`（**缺陷**）、`TenantAwareOrmModelProvider`（**缺陷，同族**）、`StaticOrmModelProvider`（安全，final）。持有方：`SessionFactoryImpl.ormModelHolder`（:103，init 期赋值后只读，安全）、`OrmSessionFactoryBean.ormModelHolder`（:48，同）。下游 `LoadedOrmModel.ormInterceptor`（:33，非 volatile，被 C1-003 连带）。dao 层 `DaoProvider.registerInstance`（nop-dao，跨批次线索，同「静态/共享槽无 volatile」家族）。
- **信心水平**: 确定（数据竞争存在性确定；「无限期不可见」是 JMM 允许而非必然，但混合态交错确定可发生）
- **误报排除**: 不属于「启动后只读单例安全」——该字段有运行期写路径（reloadModel 公开 API + 管理端 clearCache）；不属于「final 字段冻结已解决」——冻结只保证 LoadedOrmModel 内容安全发布，不解决 provider 字段引用本身的可见性与两步 clear 的混合态。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。ormModel 非 volatile、getOrmModel 每请求写 setOrmInterceptor、clearCache 两步混合态三项证据亲验成立（DefaultOrmModelProvider.java:15/:38-48；TenantAwareOrmModelProvider.java:16/:42-45）；reloadModel 为公开运维 API，触发链具体，P1 判级成立

### [nop-persistence-rw-C1-004] LoadedOrmModel 引用计数与 close 的窗口竞态：工厂关闭时新开 session 可挂到已 doClose 的 persisters 上

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:157-184`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:125-135,768-791`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:447-449`
- **证据片段**:
  ```java
  // OrmSessionImpl.java 构造器 :127-128 —— get 与 incRef 非原子
  this.loadedOrmModel = env.getLoadedOrmModel();
  this.loadedOrmModel.incRef();
  // LoadedOrmModel.java:157-174
  public void close() { this.needClose = true; if (refCount.get() <= 0) doClose(); }  // doClose 关闭全部 persister→driver
  public void incRef() { refCount.incrementAndGet(); }
  ```
- **严重程度**: P2
- **并发场景**: 停机/模型热刷新线程（`SessionFactoryImpl.close()` → `ormModelHolder.close()`；`OrmSessionFactoryBean.destroy` :190-202）与业务线程（`openSession` → `new OrmSessionImpl`）。
- **触发时序**: T1 业务线程执行 `env.getLoadedOrmModel()` 拿到引用 M（此刻 M.refCount=0，无在途 session）；T2 管理线程 `M.close()` → needClose=true 且 refCount==0 → `doClose()` 遍历关闭全部 EntityPersisterImpl/CollectionPersisterImpl（及其 JDBC driver）；T1 继续 `M.incRef()`（refCount=1）并完成 session 构造 → 该 session 持有已关闭的 persisters → 首次 load/save 即在已关闭 driver 上失败（或产生未定义行为）。
- **现状**: 新发现。原语（AtomicInteger/volatile needClose）正确，缺陷在 `getLoadedOrmModel`→`incRef` 两步非原子，无「引用检查+递增」原子协议。
- **风险**: 停机窗口内的在途请求报错（可接受度较高），以及 reloadModel 路径下旧模型 close 与新请求并发时偶发失败；无持久化损坏。
- **建议**: 平台惯例可在 provider 侧持锁串行化 `close` 与 `getOrmModel+incRef`；或给 LoadedOrmModel 增加 `boolean tryIncRefIfOpen()`（`synchronized` 内检查 needClose 并递增），`OrmSessionImpl` 构造器改用之，失败抛「模型已关闭」错误码。
- **失效面枚举**（F3 同族清扫）: `incRef` 调用点唯一（`OrmSessionImpl` 构造器 :128）；`decRef` 调用点唯一（`close()` :776）；`close()` 触发方 3 处——`SessionFactoryImpl.close`(:448)、`OrmSessionFactoryBean.destroy`(:197,201)、`DefaultOrmModelProvider/TenantAwareOrmModelProvider.close`——三处同受本窗口影响；`IOrmEntityFileStore` 等会话外资源不在本族。
- **信心水平**: 确定（窗口极窄但交错确定存在；后果为错误而非数据损坏）
- **误报排除**: 不属于「停机路径粗同步合理」——本条不是要求优雅停机顺序，而是指出原子性缺失使 incRef 与 doClose 顺序不受约束；非 C6 泄漏问题（资源最终会关闭），是时序正确性问题。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。incRef 与 doClose 的 TOCTOU 窗口亲验成立（LoadedOrmModel.close/incRef/decRef 代码核对）；后果为错误而非数据损坏，P2 恰当

### [nop-persistence-rw-C1-005] SessionFactoryImpl.interceptors COW 无 volatile / daoListeners check-then-act：运行期增删拦截器与监听器不保证可见、可丢失

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:86,88,240-261,268-291,329-331`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/MultiOrmDaoListener.java:17-25`
- **证据片段**:
  ```java
  private List<IOrmInterceptor> interceptors = Collections.emptyList();
  @Override public void addInterceptor(IOrmInterceptor interceptor) {
      List<IOrmInterceptor> list = new ArrayList<>(interceptors.size() + 1);
      list.addAll(interceptors); list.add(interceptor);
      this.interceptors = list;                      // COW 替换，但字段非 volatile
  }
  private MultiOrmDaoListener daoListeners = null;
  @Override public void addDaoListener(IOrmDaoListener daoListener) {
      if (daoListeners == null) daoListeners = new MultiOrmDaoListener();  // check-then-act
      daoListeners.addDaoListener(daoListener);
  }
  ```
- **严重程度**: P2（当前仓库内未发现启动后运行期调用方，故降档；但这是公开 API `IPersistEnv` 的运行时可变面）
- **并发场景**: 运行期管理线程调用 `addInterceptor/removeInterceptor/addDaoListener/removeDaoListener`（均为 `IPersistEnv`/`IOrmSessionFactory` 公开方法）与全体业务线程 `openSession()`（`SessionFactoryImpl.java:330` 每次 flush 时读取 `getInterceptors()`）及 `env.getDaoListener()`（`EntityPersisterImpl` 每次读/写路径调用）。
- **触发时序**: (a) T1 `addInterceptor` 完成 `this.interceptors = list`（普通写）；T2 并发 `openSession()` 读 `interceptors`——无 volatile，JMM 允许 T2 无限期读到旧列表（新拦截器对后续会话不生效，无告警）。(b) 两管理线程并发首次 `addDaoListener`：同时读到 null → 各自 new 一个 `MultiOrmDaoListener` 并向其中注册 → 后写者覆盖前者 → 先注册的 listener 永久丢失（内层 CopyOnWriteArrayList 只保护了单实例内的并发，未保护外层槽位）。
- **现状**: 新发现。当前 grep 全仓未发现启动后的运行期调用方（`setDaoListeners`/拦截器装配集中在 `OrmSessionFactoryBean.init` 单线程期），故实际暴露面目前为 0；风险在 API 契约层。
- **风险**: 第三方/后续代码按公开 API 做运行期插件注册时，症状为「注册成功但不生效」或「偶发丢失」，属最难排查的静默失效族。
- **建议**: `interceptors`/`daoListeners` 字段加 `volatile`；`addDaoListener` 惰性初始化改 `volatile` 字段 + `synchronized` 或初始化为 `new MultiOrmDaoListener()` 常驻实例（内层已是 COW，常驻无成本）。
- **失效面枚举**（F3 同族清扫）: `SessionFactoryImpl` 全部运行期可变字段清点——`interceptors`（get/add/remove，**缺陷**）、`daoListeners`（get/add/remove/set，**缺陷**）、`queryExecutors`（仅 setter 整体替换，启动期，安全）、`defaultAstTransformer`（仅 setter，安全）、其余为 init 期注入只读。同族正确实现参照：`MultiOrmDaoListener` 内层 CopyOnWriteArrayList、`JdbcEntityPersistDriver` 单槽缓存 volatile。
- **信心水平**: 确定（竞态存在确定）；当前触发概率低（无运行期调用方）
- **误报排除**: 不属于「启动期装配安全」——这两个字段是公开运行期 API 的写入口，契约上允许运行期调用；不能因「现在没人调」豁免 API 级并发契约（F10 守卫缺失口径）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。COW 无 volatile 与 daoListeners check-then-act 形态确认；当前无运行期调用方，按公开 API 契约缺口（F10）口径保留 P2

### [nop-persistence-rw-C1-006] OrmTimestampHelper 静态单例槽非 volatile：热路径每笔 save/update 读取、registerInstance 写入无 happens-before

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/OrmTimestampHelper.java:21-29`；调用方 `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:457,474`
- **证据片段**:
  ```java
  static OrmTimestampHelper _instance = new OrmTimestampHelper();   // 非 final 非 volatile
  public static OrmTimestampHelper instance() { return _instance; }
  public static void registerInstance(OrmTimestampHelper helper) { _instance = helper; }
  // EntityPersisterImpl.queueSave:457 —— 每笔实体保存必经
  OrmTimestampHelper.instance().onCreate(entityModel, entity);
  ```
- **严重程度**: P2
- **并发场景**: 全体业务线程（queueSave/queueUpdate 每笔写必经）与调用 `registerInstance` 的定制线程（当前仓库内无生产调用方，属框架预留的替换点；测试代码常用于替换时间戳行为——测试代码不是生产守卫）。
- **触发时序**: T1 定制线程 `registerInstance(customHelper)`（普通写，customHelper 若带状态字段则构造亦未安全发布）；T2 业务线程持续 `instance()` 读取——无同步边，T2 允许继续读到默认实例（自定义时间戳策略静默不生效），或在首次可见时读到未完全构造的 customHelper（字段非 final 时 JMM 允许观察默认值）。
- **现状**: 新发现。
- **风险**: 替换点被使用时表现为「注册后仍走默认时间戳行为」的静默失效；不产生数据损坏（两个实现行为同构）。
- **建议**: 字段加 `volatile`（一处关键字）；或改为 `AtomicReference<OrmTimestampHelper>`。同族参照 `EncAssistedHelper._instance`（static final，不可变引用）为正确形态。
- **失效面枚举**（F3 同族清扫）: 本源码根 static 单例槽全清单——`OrmTimestampHelper._instance`（**缺陷**）、`EncAssistedHelper._instance`（final，安全）、`OrmSessionRegistry.instance()`（另行立案 C1-001）、`DaoProvider.registerInstance` 调用点在 `OrmDaoProvider:46`（被写目标在 nop-dao，跨批次线索登记）；nop-orm-data 无 static 可变单例。
- **信心水平**: 确定（数据竞争确定存在；当前生产触发概率低因无 registerInstance 调用方）
- **误报排除**: 不属于「static final 引用安全」——该字段非 final；不属于「低频路径粗同步合理」——读侧是每笔写事务热路径。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。static 非 final 非 volatile 槽亲验（OrmTimestampHelper.java:21/:28）；归入「静态槽收紧」切片（与 C1-024 同一 PR）

### [nop-persistence-rw-C1-007] OrmBatchLoadQueueImpl「半 volatile」设计：flushing 为 volatile 而 loadQueue/afterFlushCallbacks 为普通字段，且 thenRun 回调会跨线程再入队

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/OrmBatchLoadQueueImpl.java:69,203-205,211-215,576-607,635-644,652-661,685-701`
- **证据片段**:
  ```java
  private LoadQueue loadQueue = null;            // :69 非 volatile
  private volatile boolean flushing = false;     // :203 volatile —— 半程承认跨线程
  // :596-599 注释明言回调时序问题："thenRun回调在future完成时才执行，若在syncGet之前捕获，
  //  异步驱动下回调新入队的装载项会滞留在loadQueue中导致循环提前退出"
  // :652-661 集合装载 future 的 thenRun 回调 → enqueueSelection → makeQueue() 可能跨线程重建 loadQueue
  ```
- **严重程度**: P2
- **并发场景**: flush 主线程与 future 完成线程。默认 `JdbcEntityPersistDriver` 经 `FutureHelper.futureCall` **同步执行**（`FutureHelper.java:122-129`，调用线程上完成任务），回调单线程——当前默认形态安全；一旦换用任何真异步 `IEntityPersistDriver`（接口明确支持异步返回，`driver/batchLoadAsync` 返回 `CompletionStage`）或异步 `IOrmEntityFileStore.batchLoadResourceAsync`，完成线程即参与 `loadQueue` 的读-改-写。
- **触发时序**（异步驱动下）：T1 flush 线程完成 `_flushEntity` 提交后进入 `syncGet(waitAll)`；驱动线程 T2 完成装载并执行 `thenRun` → `enqueueSelection` → `makeQueue()`：读 `loadQueue == null` 成立 → `this.loadQueue = new LoadQueue()`；T1 从 syncGet 返回后同时执行 `queue = this.loadQueue; this.loadQueue = null;`（:598-599）——两线程对非 volatile 字段无同步交错：T1 的置 null 可覆盖 T2 刚放入的新队列（丢失更新），或 T1 读到半发布状态 → T2 入队的懒加载项静默丢失（实体停留 proxy 态）或 flush 循环提前退出。`afterFlush(task)`（:635-643）的 `loadQueue == null` 检查与 `afterFlushCallbacks.add` 同为非原子且字段非 volatile。
- **现状**: 新发现。`flushing` 加了 volatile 而同状态族字段未加，且 :596-599 注释证明作者已知「异步驱动下回调并发」场景存在——守卫做了一半。
- **风险**: 当前默认 JDBC 驱动下无影响；切换异步驱动（该接口存在的意义）后出现静默丢失懒加载/回调丢失，且仅在真异步时序下复现，极难定位。
- **建议**: `loadQueue`、`afterFlushCallbacks` 一并 `volatile`；`makeQueue` 与 flush 的捕获-清空改在同一锁/`synchronized(this)` 内，或队列改为 `ConcurrentLinkedQueue` 型结构、flush 循环用 drain-to-local 语义消除「捕获-置空」两步。
- **失效面枚举**（F3 同族清扫）: 本类三字段（loadQueue **缺陷**、afterFlushCallbacks **缺陷**、flushing 已 volatile 正确）；同族会话级惰性字段已在 C1-002 清扫；`OrmSessionImpl.flushBatchLoadQueue`（:280-284）与 `getBatchLoadQueue`（:272-277）为同队列的入口对，随本条一并修复。
- **信心水平**: 确定（数据竞争结构确定；默认驱动下不可达已在触发时序中注明前提）
- **误报排除**: 不属于「理论风险未具体化」——注释与 volatile 的存在证明跨线程完成路径是作者认知内的设计约束，交错序列可具体写出（前提为接口允许的异步驱动）；非「CHM 原语误用」类。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。半 volatile 设计与 :596-599 注释证据成立；默认 JDBC 驱动下不可达已如实标注，前提（异步驱动）为接口声明面

### [nop-persistence-rw-C1-008] 查询计划缓存复合操作：check-then-act 双编译良性但 TenantCachedQueryPlan 发布后可变字段无同步；reloadModel 不清查询计划缓存 → 旧模型编译的计划继续命中

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:93-134（compileSql）`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/TenantCachedQueryPlan.java:11-30`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:460-463（reloadModel）`
- **证据片段**:
  ```java
  // LoadedOrmModel.compileSql —— get-then-compile-then-put，非原子
  IOrmCachedQueryPlan result = env.getQueryPlanCache().get(key);
  if (result == null || result.getCompiledSql() == null) {
      ... compile ...
      tenantPlan.addCompiledSql(compiledSql);   // 对缓存内共享对象的原地变异
      env.getQueryPlanCache().put(key, result);
  }
  // TenantCachedQueryPlan —— 发布进共享缓存后 compiledSql 仍可被写
  private ICompiledSql compiledSql;             // 非 volatile
  public void addCompiledSql(ICompiledSql compiledSql) { ... this.compiledSql = compiledSql; ... }
  // SessionFactoryImpl.reloadModel —— 只清模型，不清 queryPlanCache
  public ILoadedOrmModel reloadModel() { ormModelHolder.clearCache(); return ormModelHolder.getOrmModel(this); }
  ```
- **严重程度**: P2
- **并发场景**: (a) 冷启动多线程并发首次执行同一 EQL；(b) 多租户并发填充同一 `TenantCachedQueryPlan`；(c) reloadModel 管理操作与持续查询流量。
- **触发时序**: (a) T1/T2 同 key 同时 miss → 双编译、双 put，后写覆盖——返回值各自正确，**良性重复编译**（浪费，非错误）。(b) T1（租户 A）先 put 了 plan P（其 `compiledSql` 槽为 null）；T3（无租户上下文）命中 P → `getCompiledSql()` 返回 null → 重编译并 `P.compiledSql = sql`（无同步写）；并发 T3' 同样写 P——值恒等收敛，读者最坏重编译一次，**可见性缺陷但后果良性**。(c) T1 `reloadModel()` 仅 `ormModelHolder.clearCache()`（`DefaultOrmModelProvider.clearCache` 亦不清 `queryPlanCache`，`TenantAwareOrmModelProvider.clearCache` 同）；T2 随后 `compileSql` 在 **factory 级** `queryPlanCache`（键 `QueryPlanCacheKey` 不含模型版本）命中旧 `LoadedOrmModel` 时期编译的 `ICompiledSql`——计划绑定旧实体模型/旧 SqlExprMetaCache，后续会话虽为新模型，执行的 SQL 仍按旧列集生成（新增列不写入/已删列继续写入）。唯一全量清理路径是 `OrmSessionFactoryBean.clearCache`(:165-179，管理端显式调用)，`IOrmTemplate.reloadModel()` 公开路径不含。
- **现状**: 新发现。(a)(b) 为可证明良性的可见性/重复工作缺陷；(c) 为 reloadModel 语义缺口（缓存一致性），其中「计划是否真正绑定旧模型」涉及 `ICompiledSql` 对模型的捕获深度（orm-eql 子模块，批次 2 范围），本批次按本模块内的缓存键/清理缺口立案并留交叉验证标注。
- **风险**: (c) 热刷新模型后部分查询持续按旧模型执行，症状与 C1-003 混杂；(a)(b) 冷启动惊群浪费（量级：并发线程数 × 单次编译成本）。
- **建议**: (c) `SessionFactoryImpl.reloadModel()` 增加 `queryPlanCache.clear()`（与 `OrmSessionFactoryBean.clearCache` 对齐），或在 `QueryPlanCacheKey`/计划上携带模型版本校验；(a)(b) 维持现状可接受（如需消除惊群可改 `computeIfAbsent` 形态，但需评估持锁编译的重逻辑顾虑——本仓库口径倾向保留 get-then-put + 接受重复编译）。
- **失效面枚举**（F3 同族清扫）: factory 级缓存三件套——`queryPlanCache`（**缺陷 c**）、globalCache（`clearCache` 路径已清，reload 路径未清——与 (c) 同根，随修一并处理）、jdbc 查询结果缓存（`clearQueryCache` 由 `OrmSessionFactoryBean.clearCache` 覆盖，reload 路径同缺，跨 dao 批次线索）；`TenantCachedQueryPlan.tenantCache`（容量 100 有界，clear 不随 reload，随 (c) 一并）；compileSql 的 get-then-put 在 `LoadedOrmModel` 每实例各有一份（旧实例的 put 与新实例的 put 落同一 factory 级缓存——即 (c) 的机制本体）。
- **信心水平**: (a)(b) 确定；(c) 很可能（缓存键与清理链路证据确定，「计划绑定旧模型」的深度待 orm-eql 批次最终确认）
- **误报排除**: (a) 不按「并发测试缺失」或惊群 P1 报——本仓库口径良性竞争降 P2/P3；(c) 不属于「已声明边界」——`reloadModel` javadoc/API 语义是「重载模型」，未声明「计划缓存将保持旧模型」。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。(c) 面（reloadModel 不清 queryPlanCache）亲验成立（SessionFactoryImpl.reloadModel:460-463 仅触达 ormModelHolder.clearCache），与 C2-008 同位点双登记合并终裁**确认**（一个缺陷一个修复项，双锚点互引，remediation 计数去重）；(a)(b) 良性面维持；CL-410（多租户条目互踢）为同缓存相邻独立缺陷，不被合并吞并

### [nop-persistence-rw-C1-009] DataBaseSchemaInitializer 公有 static 可变数组 specifyQuerySpaces：实例 setter 写静态字段

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/initialize/DataBaseSchemaInitializer.java:41-46,112-130`
- **证据片段**:
  ```java
  public static String[] specifyQuerySpaces;                    // public static 可变
  @InjectValue("@cfg:nop.orm.db-differ.auto-upgrade-database-specify-query-spaces|")
  public void setSpecifyQuerySpaces(String[] specifyQuerySpaces) {
      this.specifyQuerySpaces = specifyQuerySpaces;             // 实例方法写静态字段
  }
  // :116 静态方法 splitByQuerySpace 直接读取该静态数组（含代码内自述的 null 防御注释）
  ```
- **严重程度**: P3
- **并发场景**: IoC 单线程初始化（写）与外部模块（dbtool 等）任意线程调用静态方法 `splitByQuerySpace`（读）。
- **触发时序**: dbtool 线程在 bean 初始化完成前调用 `splitByQuerySpace` → 读到 null（已有防御，:115 注释）；若 bean 在其他线程初始化后立即被读，无 HB 边保证可见（实践上启动同步掩盖）。理论风险，未具体化生产交错——按口径降 P3 并标注。
- **现状**: 新发现。
- **风险**: 多 bean 实例/测试并行时互相覆盖；可见性无保证；数组内容本身可变（外部可改写元素）。
- **建议**: 改为实例字段 + 静态方法改实例方法；或至少 `private static volatile String[]` 并防御性克隆。
- **失效面枚举**（F3 同族清扫）: 本源码根 public static 可变字段全清单仅此一处（其余 static 字段为常量/Logger/AtomicLong，见清单 C 节）；读取方 1 处（:116）+ dbtool 外部调用（跨模块，随本条修复自然收敛）。
- **信心水平**: 确定（形态确定）；后果为理论级
- **误报排除**: 不属于「低频配置路径安全」豁免——问题形态是「实例 setter 写静态」的结构缺陷，与调用频度无关；按实际后果降 P3 而非豁免。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

### [nop-persistence-rw-C1-010] 全局实体缓存值对象共享引用无防御性拷贝：同一 Object[] 与可变值（Timestamp 等）跨会话/线程共享

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:526-545,626-635`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/CollectionPersisterImpl.java:186-205,306-315`
- **证据片段**:
  ```java
  // EntityPersisterImpl —— 缓存命中时把缓存数组原样交给会话装配
  protected boolean loadFromGlobalCache(IOrmEntity entity, IOrmSessionImplementor session) {
      Object values = globalCache.get(getCacheKey(entity));
      if (values != null) {
          Object[] cacheValues = convertCacheValues(values);   // Object[] 分支直接返回原数组引用
          session.internalAssemble(entity, cacheValues, entityModel.getAllPropIds());
  ```
- **严重程度**: P3
- **并发场景**: 启用 `useGlobalCache` 的实体：写会话 `updateGlobalCache`（`OrmAssembly.getPropValues` 新建数组，元素为属性值引用）与任意多读会话（同一数组/同一元素引用被装配进各会话实体）。
- **触发时序**: 框架自身不原地变更元素（审计核对 `internalAssemble`/`OrmAssembly` 均为引用放置，无 setXxx 类突变），因此**当前框架代码内写不出具体损坏交错**——按口径标注「理论风险，未具体化」降 P3。前提性风险：`java.sql.Timestamp/Date` 等可变值入缓存后，任何一方（用户代码在实体上持有并 setTime、或未来 binder 优化原地转换）即跨会话传染。
- **现状**: 新发现（防御性缺口，非现行缺陷）。
- **风险**: 与 C9 binder 演进叠加时变为跨请求数值污染；排查难度极高（无所有权边界）。
- **建议**: 缓存写入侧对可变类型做不可变化处理（`Timestamp.getTime()` 存 long 或克隆），读出侧按需重建；至少在 owner doc 声明「全局缓存值为共享不可变契约」。
- **失效面枚举**（F3 同族清扫）: 缓存值出口 4 处——`EntityPersisterImpl.loadFromGlobalCache`(:527)、`getToLoad`(:615-619)、`CollectionPersisterImpl.loadFromGlobalCache`(:186，存的是 id 数组，不可变风险低)、`getToLoad`(:289)；`convertCacheValues` 两份实现（Entity/Collection :626/:306）同型。元素可变类型入口为 `OrmAssembly.getPropValues`(:78-96) 与 binder 反序列化结果（dao 批次范围）。
- **信心水平**: 有趣的猜测（结构性缺口确定，损害路径未具体化）
- **误报排除**: 不按 P1/P2 报正因写不出具体交错（口径第 4 条）；不豁免是因为「共享可变对象跨线程发布」属 C1 第 5 步明文检查项。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**。注意：与本单元无同号冲突的 WI 2.3 C1-010（NopAuthSession 无 version 列，P2）为不同条目（同名异单元）；本条为全局缓存值对象共享引用的防御缺口，理论风险定性正确

### [nop-persistence-rw-C1-011] DaoEntityResource.lastCheckTime/lastModified 非 volatile 检查-然后-行动：实例是否跨线程共享取决于上游缓存

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/resource/DaoEntityResource.java:40-54,90-125`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/resource/DaoResourceNamespaceHandler.java:42-53`
- **证据片段**:
  ```java
  private long lastCheckTime = -1;      // 非 volatile
  private long lastModified;            // 非 volatile
  private void checkLoad() {
      long now = CoreMetrics.currentTimeMillis();
      if (checkInterval > 0 && lastCheckTime > 0 && now - lastCheckTime < checkInterval) return;
      loadEntity();                     // check-then-act + 双字段非同步写
  }
  ```
- **严重程度**: P3
- **并发场景**: `dao:` 资源被多线程访问。handler 每次 `getResource` 均 `new DaoEntityResource`（:52），本模块内不共享；若上游（ResourceComponentManager 等组件缓存，nop-core 范围）按路径缓存该 IResource 实例，则实例跨线程共享。
- **触发时序**: 理论风险，未具体化（依赖上游缓存行为）：T1/T2 同时通过 checkInterval 检查 → 双次 DB 加载（惊群，良性）；`lastCheckTime/lastModified` 双写非原子/不可见 → 过期窗口内读到陈旧 lastModified（5 秒 checkInterval 级陈旧，业务影响小）。
- **现状**: 新发现（条件化缺陷）。
- **风险**: 限于陈旧性窗口与重复查询，无正确性损害。
- **建议**: 若上游确认缓存实例，将两字段 `volatile` 化即可（无需加锁，允许重复加载）。
- **失效面枚举**（F3 同族清扫）: 本模块 resource 包仅此一个带可变状态的类（`DaoResourceNamespaceHandler` 无状态）；同族「资源缓存检查时间戳」模式在 nop-core 的资源体系中（跨模块，不在本批次展开）。
- **信心水平**: 确定（形态确定）/ 触发条件未在本模块闭合
- **误报排除**: 不属于「池化 ThreadLocal 未清理」类；按条件化降档口径（仅特定 API 内）处理。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

---

## 维度集外线索（不立案展开）

### [nop-persistence-rw-CL-001]（维度集外线索，不立案展开）evictGlobalCache 按「每个实体动作一个事务监听器」注册：大批量 flush 时监听器数量随写集线性膨胀

- 位置: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:553-569`、`CollectionPersisterImpl.java:207-223`
- 形态: 每次 save/update/delete 入队成功回调中，若事务已开则 `env.txn().addTransactionListener(querySpace, new ITransactionListener(){...})`——万级批次 flush 时单事务挂万级监听器对象（各捕获 entity），after-commit 串行 `removeAsync` 逐键失效。属 C7（长时运行/大事务内存与提交后延迟）/C9（热路径分配放大）形态，非 C1 正确性缺陷（监听器事务级生命周期、无泄漏）。建议后续批次按 C5/C9 口径量化（每监听器 ~2 对象 + 事务提交后串行失效时延）。

### [nop-persistence-rw-CL-002]（维度集外线索，不立案展开）OrmSessionImpl.flush 重入时静默返回（flushing 期间嵌套 flush 直接跳过）

- 位置: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:183-186`
- 形态: 「如果正在执行flush, 则直接返回」为同线程重入保护的设计选择，但语义是**静默跳过持久化**而非排队/报错（F9 形态：吞掉请求不产生任何对外可见信号）； interceptor 在 flush 中触发二次 flush 的数据是否落库取决于时序。属 C3/C8 语义审计口径，C1-002 已覆盖其并发侧，此处仅登记语义线索。

---

## 结论摘要

- **发现总计**: 11 条（P1×2：C1-001、C1-003；P2×6：C1-002/004/005/006/007/008；P3×3：C1-009/010/011）+ 维度集外线索 2 条（CL-001/002，不立案）。
- **判级主线**: 会话生命周期为上下文绑定 + `checkContext` 默认开启，故 session 内部状态在声明用法下不判共享缺陷；全部 P1/P2 的触发前提收敛于两点——(1) 平台公开 API 允许的同上下文并发执行（C1-001/002），(2) 引擎级单例的运行期写路径无可见性保障（C1-003/004/005/006/007/008）。
- **未发现问题的重点区**: `JdbcEntityPersistDriver/JdbcCollectionPersistDriver`（volatile 单槽缓存 + 注释声明良性竞争，实现规范）、`OrmDaoProvider`（CHM+computeIfAbsent）、`DaoEntityBlockingSource`（nop-orm-data，无共享轮询状态，占用抢占靠条件 UPDATE 原子完成）、`EncAssistedHelper`、`MultiOrmDaoListener` 内层、persister/persisters 映射的构建发布链（PersistEnvBuilder 单线程构建 → LoadedOrmModel final 冻结）。
- **末位发现编号**: `nop-persistence-rw-C1-011`；维度集外线索末位编号 `nop-persistence-rw-CL-002`（批次 1 独占 001~099 段）。

---

## 批次 2/4（nop-orm-eql）

- **批次**：2/4（预拆制）。发现编号自 **C1-012** 起（批次 1 已用 C1-001~011）；维度集外线索自 **CL-100** 起（批次 2 独占 100~199 段）。
- **盲审纪律声明**：本轮为盲审第 1 轮，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何历史文件；仅阅读方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、owner doc 可读清单及当前 live code。本分节由 `cat >>` 追加生成，未改动批次 1 任何内容。
- **检查范围**：`nop-persistence/nop-orm-eql/src/main/java`（165 文件 / 39,510 行；其中 `parse/antlr/` 下 ANTLR 生成物按审计纪律第 2 条豁免，不作发现对象）。审计对象为 EQL 语言编译与 SQL 生成链：EqlCompiler、CompiledSql/ICompiledSql、SqlExprMetaCache、EntityTableMeta 及 meta 包、AstToSqlGenerator/AstToEqlGenerator/SeqAliasGenerator、EqlTransformVisitor/SqlTableScope/CollectionOperatorTransformer、ShardMarkerInjector、OracleEqlAstTransformer、param 包（含 TenantParamBuilder）、parse 包（EqlASTParser/EqlParseTreeParser/EqlExprASTParser）。
- **判级口径**：EQL 编译/SQL 生成是全平台数据访问「每查询必经」路径（nop-orm `OrmTemplateImpl`→`SessionFactoryImpl.getQueryPlanCache`→`EqlCompiler.compile`），引擎级缓存缺陷门槛从低；「仅特定 EQL 语法内」条件化的按条件降档。

### 共享可变状态清单表（批次 2 范围内全枚举）

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|---|
| 1 | `static final Map s_meta`（HashMap） | `SingleColumnExprMeta.java:61` | 仅 static 初始化块 | 全线程 `valueExprMeta()` | JLS 类初始化锁；初始化后只读 | 安全（误报排除：静态初始化后无写点，全仓 grep 确认） |
| 2 | `entityMetas`（ConcurrentHashMap） | `SqlExprMetaCache.java:24` | 各 worker 线程首编 put | 每次编译 get | CHM + computeIfAbsent | 正确；锁内重活登记 CL-100 |
| 3 | `EqlParseTreeParser._instance`（有状态基类单例） | `EqlParseTreeParser.java:14` | 潜在：`instance()` 调用方 | 同左 | 无 | **C1-012**（本模块无生产调用方，潜在暴露） |
| 4 | `CompiledSql` 全部实例字段（非 final/volatile） | `CompiledSql.java:24-45` | `EqlCompiler.compile` 单线程填充（setter 仅此一处调用，全仓 grep 证实） | 缓存命中后所有执行线程 | 编译期单线程写 + 平台 ICache put/get happens-before | 发布安全；条目可变性=契约脆弱，**C1-013** |
| 5 | `EntityTableMeta.propExprMetas`（LinkedHashMap）/`valueExprMetas`（HashMap） | `EntityTableMeta.java:44,46` | 构造器单线程 | 各线程经缓存读 | 随 CHM put 安全发布；构造后无写点 | 安全；getter 泄漏活 Map 登记为 **C1-014** |
| 6 | 编译期对象群（AstToSqlGenerator、EqlTransformVisitor、SqlTableScope、SeqAliasGenerator、CollectionOperatorTransformer、SqlParamTypeResolver、ExprTypeResolver、ShardMarkerInjector 局部容器） | 各文件 | 每次 compile 新建实例 | 同一 compile 线程 | 线程封闭（`EqlCompiler.java:57,88,99` 每次 new；`nop-orm EqlCompileContext.getAliasGenerator()` 每次 new SeqAliasGenerator） | 安全 |
| 7 | `OracleEqlAstTransformer`（beans.xml 单例 bean） | `OracleEqlAstTransformer.java:65` | 无实例可变字段（仅 static final ORACLE_FAMILY） | — | 无状态 | 安全 |
| 8 | `TenantParamBuilder.INSTANCE` 公共静态单例 | `TenantParamBuilder.java:19` | 无（无实例字段） | 各执行线程 buildParams | 读 ThreadLocal（ContextProvider）+ 写局部 list | 安全 |
| 9 | ANTLR `EqlLexer/EqlParser` public static String[] | `parse/antlr/` | 生成物 | 生成物 | — | 豁免（生成文件纪律） |

静态可变状态全枚举结论：本源码根内**无非 final static 可变字段**（grep 基线 C1 三节 orm-eql 分区复核，命中项均为 import static / static 方法 / static 初始化块）；无 ThreadLocal、无 volatile 需求点、无 synchronized/锁载体；唯一 computeIfAbsent 站点为 `SqlExprMetaCache.java:40`。

### [C1-012] EqlParseTreeParser 暴露有状态解析单例（基类 baseLocation/source 非同步写读），同族在 nop-xlang 已被并发路径活跃共享

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlParseTreeParser.java:14-19`（锚点在本批次）；状态载体在 `nop-kernel/nop-antlr4/nop-antlr4-common/src/main/java/io/nop/antlr4/common/AbstractParseTreeParser.java:45-46,56-61,73-82`
- **证据片段**:
  ```java
  // EqlParseTreeParser.java
  static final EqlParseTreeParser _instance = new EqlParseTreeParser();
  public static final EqlParseTreeParser instance() { return _instance; }
  // AbstractParseTreeParser.java（基类，实例字段非 volatile、无锁）
  protected SourceLocation baseLocation;
  protected String source;
  public ParseTreeResult parseFromText(SourceLocation loc, String text) {
      ...
      this.baseLocation = loc;      // :59 写
      this.source = text;           // :60 写
      ...
      return doParse(stream);       // :61 → config()/twoPhaseParse() 读 :45-46 字段
  ```
- **严重程度**: P2（理论风险，未具体化——本模块内 `instance()` 当前无生产调用方；`EqlCompiler.java:57` 与 `EqlExprASTParser.newParser()` 均为每次 `new`，线程安全）
- **并发场景**: 若任何调用方经 `EqlParseTreeParser.instance()` 并发解析（如未来将 `EqlASTParser.newParser()` 改为复用单例、或外部模块直接取单例），即多 worker 线程共享同一有状态解析器实例。
- **触发时序**: 理论风险，未具体化（本模块无现实调用方，写不出生产交错）。机制上确定性成立：T1 在基类 ：59-60 写 `baseLocation=L1,source=S1` → T2 交错写 `baseLocation=L2,source=S2` → T1 继续执行 `config(lexer)`（:73-78 读到 L2/S2）→ T1 产出的 AST token 行号按 L2 偏移、错误信息引用 S2 片段。后果为诊断元数据污染（location 错位），非 SQL 语义损坏（`source` 仅喂 `AbstractAntlrLexer` 错误展示路径，:84-87）。
- **现状**: `EqlParseTreeParser` 以 `public static instance()` 形式暴露一个继承自 `AbstractParseTreeParser` 的**有状态**对象；状态字段可见性无任何保护（非 volatile、无同步、check-then-act 跨方法）。单例的线程安全完全依赖「无人使用单例」这一偶然事实。
- **风险**: 该 API 是全平台 antlr4 解析器家族的公共模式；同族 `XLangParseTreeParser.instance()` 已被 `nop-xlang XLangExprParser.parseFullExpr`（`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/XLangExprParser.java:69-73`）在**每条 XLang 表达式/XPL 模板编译**的热路径上共享调用——EQL 侧锚点一旦被效仿（或 EQL 侧 `EqlASTParser` 被改造为单例复用以省对象分配），即可具体化。
- **建议**: (1) 立即项：将 `_instance`/`instance()` 收窄为 private 或删除（本模块无调用方），或在类 javadoc 钉死「非线程安全、禁止共享使用」；(2) 根治项（归框架核心批次）：`AbstractParseTreeParser.parseFromText` 改为将 `baseLocation/source` 降为 `doParse` 的局部传参（方法参数化），消除实例状态，家族一次性修复；(3) 平台已有 `NamedThreadLocal` 等设施不适用此场景，参数化是唯一正确形态。
- **失效面枚举**（F3 同族全清点）: `EqlParseTreeParser._instance`（本文件：14，无生产调用方=潜在）；`EqlExprParseTreeParser`（继承 EqlParseTreeParser，自身无单例，`EqlExprASTParser:31` 每次 new=安全）；`XLangParseTreeParser.instance()`（`nop-kernel/nop-xlang/.../parse/XLangParseTreeParser.java:15`，**活跃共享**，被 `XLangExprParser.java:70` 每次表达式编译调用——本批不立案，供 nop-xlang/框架核心批次按本条时序跟进）；`MermaidParseTreeParser.instance()`（`nop-format/nop-mermaid/.../parse/MermaidParseTreeParser.java:15`，grep 全仓无生产调用方=潜在）；`AbstractParseTreeParser` 其余子类逐一核对仅有上述四个 `instance()` 暴露点。
- **信心水平**: 确定（状态机制与无保护现状确定；本模块内不可达亦确定）。
- **误报排除**: 不属于「synchronized/单例本身不是问题」的误报校准——被报的不是单例模式，而是**有状态对象经公共静态句柄暴露**且基类方法间存在跨调用实例字段写读；也不属于「平台基础设施不重审」——锚点文件（EqlParseTreeParser）在本批次源码根内。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。理论风险定性（本模块 instance() 无生产调用方）与证据一致；nop-xlang 活跃共享面的移交标注维持

### [C1-013] 全局缓存查询计划 CompiledSql 为全可变 POJO：非 volatile 字段 + 公开 setter + getter 泄漏活引用列表，发布安全仅靠约定维持

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/CompiledSql.java:24-45（字段）,52-223（全部公开 setter）,110-117,128-134（getSql/getParamBuilders）`；填充点 `compile/EqlCompiler.java:111-119`
- **证据片段**:
  ```java
  public class CompiledSql implements ICompiledSql {
      private String name;                    // 非 final、非 volatile
      private List<IDataParameterBinder> columnBinders;
      private List<ISqlExprMeta> fieldMetas;  // getter 返回活 ArrayList
      private List<ISqlParamBuilder> paramBuilders = Collections.emptyList();
      ...
      public void setSql(SQL sql) { this.sql = sql; }          // setter 终身公开
      public List<ISqlExprMeta> getFieldMetas() { return fieldMetas; }  // 活引用逸出
  ```
- **严重程度**: P2（理论风险，未具体化——当前全仓无缓存后写点，写不出生产交错）
- **并发场景**: `CompiledSql` 经 nop-orm `SessionFactoryImpl.queryPlanCache`（`ICache<QueryPlanCacheKey, IOrmCachedQueryPlan>`）跨执行、跨线程共享；HTTP worker / 定时任务 / 分片 fanout 多线程并发命中同一查询计划。
- **触发时序**: 理论风险，未具体化。现状链路核验为安全：`EqlCompiler.compile`（单线程）完成全部 setter 后才返回 → `SimpleCachedQueryPlan` 包装 → 平台 `ICache.put`（并发容器 put 与后续 get 建 happens-before）→ 消费方 `JdbcQueryExecutor.transformRow:198-199`、`GenSqlTransformer.transform`（新建 SqlBuilder，仅读 `sql.getMarkers()`）全部只读。任一未来改动（执行期缓存 `setXxx`、消费者对 `getFieldMetas()` 返回列表排序/追加）即具体化为跨线程可见性竞态：T1 执行线程调 setter/改列表 → T2 执行线程 get 同一实例读到旧值或撕裂列表。
- **现状**: 「每查询必经」的引擎级缓存条目是不可变契约下的**全可变实现**：19 个非 final 字段、20 个公开 setter、4 个返回活可变列表的 getter（`fieldMetas`/`columnBinders`/`paramBuilders`/`readEntityModels`，其中仅 `orderByOrdinals` 做了 `unmodifiableList` 包装，:216-223）。无 final 化、无防御性拷贝、无「缓存后禁改」运行时守卫。
- **风险**: 查询计划缓存是全平台数据访问共享面；一次看似无害的「顺手改一下缓存计划」（如执行期向 `columnBinders` 追加方言 binder、给 `readEntityModels` 增量登记实体）就会变成无锁数据竞争，且症状（偶发脏读/不一致列数）极难归因。判级从低的「每查询必经」口径下，该契约脆弱性值得在修复轮统一收紧。
- **建议**: 编译完成后即不可变：`CompiledSql` 改为全 final 字段 + 建造器（或提供 `toImmutableView()` 后入缓存）；getter 对列表返回 `Collections.unmodifiableList` 或构造期即定容；与批次 1 对 `SimpleCachedQueryPlan`/`TenantCachedQueryPlan`/`QueryPlanCacheKey` 的审查结论合并处置。
- **失效面枚举**（F3 同族全清点）: `CompiledSql` 4 个活引用 getter（fieldMetas/columnBinders/paramBuilders/readEntityModels，逐一列于 :48-134；orderByOrdinals 已包装=安全）；setXxx 共 20 个（:52-223，当前唯一调用方 `EqlCompiler.java:112-119,126-176`，grep `compiledSql.set`/`CompiledSql.*\.set` 全仓无第二写点=当前安全）；缓存包装层 `SimpleCachedQueryPlan.java:10-21`（final 字段=安全）、`TenantCachedQueryPlan`（同目录，持 ICompiledSql 引用，包装本身 final=安全）；同构可变缓存条目在 orm-eql 范围内无其他实例。
- **信心水平**: 确定（现状无竞态确定；脆弱性确定）。
- **误报排除**: 不属于「无状态单例被并发调用是设计意图」豁免——该对象**非无状态**（19 个可变字段）且 setter/getter 泄漏是可枚举的事实；不属于「并发容器 put 本身」误报——被报的是条目自身的可变性契约，不是容器用法。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。全可变 POJO 契约脆弱性登记成立；当前无缓存后写点亲验维持

### [C1-014] EntityTableMeta/SelectResultTableMeta 的 getFieldExprMetas() 泄漏内部可变 Map，接口契约未钉死只读语义

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/EntityTableMeta.java:44,270-272`；`meta/SelectResultTableMeta.java:22,42-45`；接口 `meta/ISqlSelectionMeta.java:25`；消费点 `compile/EqlTransformVisitor.java:1471,1819`
- **证据片段**:
  ```java
  // EntityTableMeta.java
  private final Map<String, ISqlExprMeta> propExprMetas = new LinkedHashMap<>();
  ...
  public Map<String, ISqlExprMeta> getFieldExprMetas() {
      return propExprMetas;              // 内部活 Map 直接逸出
  }
  // EqlTransformVisitor.java:1471（消费点，当前只迭代）
  Map<String, ISqlExprMeta> fieldMetas = tableMeta.getFieldExprMetas();
  ```
- **严重程度**: P3（结构/防御性问题；当前无写点、Map 构造后无 mutation，不构成现实竞态）
- **并发场景**: `EntityTableMeta` 经 `SqlExprMetaCache`（CHM）被全部编译线程共享；泄漏的 LinkedHashMap 随之跨线程共享。当前所有消费方（grep 全仓仅 `EqlTransformVisitor:1471/1819` 两处）只做只读迭代，共享只读 + 安全发布 = 无害。
- **触发时序**: 无（当前无可写交错；登记为契约缺陷而非竞态）。若未来调用方对返回 Map 做 `put/remove`，则是对共享缓存元数据的无锁并发写，将直接污染全部后续编译的字段解析。
- **现状**: `ISqlSelectionMeta.getFieldExprMetas()` 接口层未表达「返回只读视图」；两个实现的内部可变 Map（LinkedHashMap/HashMap）原样逸出。同文件的 `valueExprMetas`（:46,89）未暴露、仅有 `getValueExprMeta(name)` 点读——同族内已有正确形态对照。
- **风险**: 元数据缓存是「每查询必经」共享面；一次调用方侧的「便利性修改」即升级为 C1-013 同型竞态，且写的是全局共享元数据，影响面为所有后续 EQL 编译。
- **建议**: 接口 javadoc 钉死只读契约 + 实现返回 `Collections.unmodifiableMap(propExprMetas)`（构造完成后包装一次，零热路径开销）；或对齐 `valueExprMetas` 的点读形态。
- **失效面枚举**（F3 同族全清点）: `ISqlSelectionMeta` 实现共 2 个：`EntityTableMeta.getFieldExprMetas`（泄漏 LinkedHashMap）、`SelectResultTableMeta.getFieldExprMetas`（泄漏构造入参 Map，:42-45，同样未防护）；接口第三方法 `requireFieldExprMeta` 两实现均点读=安全；`EntityTableMeta.getValueExprMeta`（:241-243）点读=安全；`SqlExprMetaCache` 未暴露内部 CHM=安全。
- **信心水平**: 确定。
- **误报排除**: 不属于「只读集合共享无害」豁免对象——本条并非报告竞态，而是 C1 执行步骤 1 第 5 载体（getter 泄漏内部集合）的登记项，判级按「无现实时序」落在 P3 而非虚报 P1/P2。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

### [nop-persistence-rw-CL-100]（维度集外线索，不立案展开）SqlExprMetaCache.computeIfAbsent 映射函数内执行重型构造，持 CHM bin 锁跨实体串行化

- **位置**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/meta/SqlExprMetaCache.java:33-41,52-55`
- **形态**: C2（锁内重操作）/C9（热路径争用），非 C1——C1 视角下 `get()`→`computeIfAbsent` 复合操作原子、同键并发首编正确去重、条目构造后不可变，**正确性无虞**。
- **线索描述**: `computeIfAbsent(entityName, k -> createEntityTableMeta(entityModel))` 的映射函数内完成 `dialectProvider.getDialectForQuerySpace` 方言解析 + `new EntityTableMeta(...)`（构造器内 `OrmBinderHelper.buildBinders` 全列 binder 数组构建 + id/component/relation 全量 meta 构造，`EntityTableMeta.java:50-96`），全程持 ConcurrentHashMap bin 锁。窗口为「每实体首次编译」一次（命中后 get 无锁），负载上升时多实体首编若落入同 bin 会瞬时串行。EQL 键空间为模型实体名（有限、非用户可控），无容量/淘汰问题。
- **移交建议**: 若 C2/C9 批次复核认为首编窗口值得处理，方向为「锁外构造 + 短锁装入」或 `ConcurrentHashMap<Object, Future<EntityTableMeta>>` 模式；当前规模下倾向 watch-only。
- **登记状态**: 维度集外线索，本批次不展开、不计入 C1 发现计数。

### 批次 2 小结

- **发现清单**：C1-012（P2，理论风险）、C1-013（P2，理论风险）、C1-014（P3）。
- **CL 线索**：CL-100（维度集外，C2/C9 形态）。
- **总计**：3 条发现（0×P0、0×P1、2×P2、1×P3）+ 1 条维度集外线索。
- **末位发现编号**：C1-014；末位 CL 编号：CL-100。
- **总体判定**：nop-orm-eql 的 C1 纪律整体较高——编译期对象全部线程封闭（每次 compile 全新实例链）、引擎级元数据缓存（SqlExprMetaCache）并发用法正确、静态可变状态仅一处（静态初始化后只读）。两条 P2 均为「无现实触发时序」的契约脆弱性登记，按「每查询必经」口径从低门槛登记以供修复轮统一收紧；唯一同族**已具体化**风险点（XLangParseTreeParser 单例被 XLangExprParser 热路径共享）位于 nop-xlang，已在 C1-012 失效面枚举中移交框架核心批次。

## 批次 3/4（nop-orm-model）

- **审计范围**：`nop-persistence/nop-orm-model/src/main/java`（67 个手写文件 / 5,729 行，`_gen/` 生成类仅查字段与守卫形态、不作为发现对象）。ORM 元模型（EntityModel/RelationModel/ComponentModel/解析产物 init 管线 + LazyLoadOrmModel 动态装载 + OrmModelValidatorRegistry）。IoEntityPersistProvider 位于 nop-orm，归批次 1 分区，本批不审。
- **判级口径**：ORM 元模型被全平台数据访问路径共享调用（每个 session 创建、每次实体加载、每次 flush 排序都触达），模型解析产物的缓存/注册表缺陷从低门槛登记；纯解析期（`OrmModelLoader.loadOrmModel` → `init()` → `freeze(true)`，启动单线程）触达的站点条件化降档。
- **路径上下文**（跨模块事实核查，仅作触发链证据）：动态模型装载经 `nop-dyn` 的 `DynOrmModelProvider.loadDynamicOrmModel`（每个租户模型缓存条目 `new LazyLoadOrmModel(...)`）+ `DynCodeGen.getDynamicEntityModel`（持锁 DB 查询 + 模块代码生成）；`dependByOtherEntity` 读侧在 nop-orm 的 flush 排序；`getColumnPropIdMap/Ids` 读侧在 nop-orm 实体绑定/批量加载热路径。

### 共享可变状态清单（载体 × 写者 × 读者 × 同步机制 × 判定）

| 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `OrmModelValidatorRegistry.VALIDATORS`（static COW list） | `register/unregister/loadServiceLoaders` | `getValidators/validate` | COW + volatile + class 锁 DCL | 正确（唯一直接安全的静态站点） |
| `ExprOrmDataType.s_map`（static final EnumMap） | static 初始化器 | `fromSqlType` | 类初始化 happens-before | 正确 |
| `OrmModel` 6 个索引字段（:29-41） | `init()`（解析期单线程） | 全平台 | 无（依赖外部缓存安全发布） | 见 C1-019 |
| `OrmEntityModel` ~30 个 init 派生字段 + `props`/`colsByCode`/`colsByPropId` | `init()`、`addProp`（解析期） | 全平台 | 无（同上） | 见 C1-017/019 |
| `OrmEntityModel.dependByOtherEntity`（:99） | `OrmModelTopEntryBuilder.build`（**运行期**，经 LazyLoadOrmModel 重建） | flush 排序（nop-orm） | 无锁写 / 无锁读 | 缺陷，见 C1-016 |
| `OrmComponentModel.colPropIds`/`propIds`（:26-27） | 首触 getter（**运行期**请求线程） | 组件绑定/批量加载热路径 | 无（check-then-act + 先发布后填充） | 缺陷，见 C1-015 |
| `OrmReferenceModel.joinOnNonPkColumn`（:35） | `isJoinOnNonPkColumn` 读路径懒写（运行期） | `OrmSessionImpl:1242` | 无 | 良性竞态，见 C1-020 |
| `LazyLoadOrmModel` 4 个 CHM + volatile topo 字段 | `putEntityModel/checkTopoEntryReady`（运行期） | 动态模型全路径 | entityModelMap 监视器 + volatile | 大体正确；失败路径缺陷见 C1-018 |
| `LazyLoadOrmModel.anyEntityUseTenant`（:39） | 构造器一次 | `OrmSessionImpl:130` 会话缓存选型 | volatile | 派生值不随动态注册更新，见 C1-021 |
| static 非 final 字段 / static final 集合 / ThreadLocal（基线 C1 三节 orm-model 分区） | — | — | — | 仅 serviceLoaderLoaded 一处 static 非 final（volatile+DCL 正确）；无 static 集合、无 ThreadLocal |
| `_gen/` 生成类全部 setter/add 方法 | 解析期 | — | `checkAllowChange()` 冻结守卫全覆盖 | 正确（守卫缺口全部在手写类，见 C1-017） |

## 发现条目

### [nop-persistence-rw-C1-015] OrmComponentModel 双字段非同步惰性初始化：getColumnPropIdMap 先发布后填充 + getColumnPropIds 非安全发布，运行期首触热路径竞态

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmComponentModel.java:26-27,43-51,106-118`
- **证据片段**:
  ```java
  // :26-27
  private Map<String, Integer> colPropIds;
  private int[] propIds;
  // :43-51  字段赋值在填充之前
  public Map<String, Integer> getColumnPropIdMap() {
      if (colPropIds == null) {
          colPropIds = new HashMap<>();
          for (OrmComponentPropModel propModel : this.getProps()) {
              colPropIds.put(propModel.getName(), propModel.getColumnPropId());
          }
      }
      return colPropIds;
  }
  // :106-118  propIds 同为非 volatile 惰性初始化
  ```
- **严重程度**: P1
- **并发场景**: 动态/租户模型（`DynOrmModelProvider` 每缓存条目新建 `LazyLoadOrmModel`，组件模型实例随模块模型缓存被全部请求线程共享）下，多个 HTTP worker 线程并发首次触达同一组件模型；读侧为 nop-orm 热路径——`DynamicOrmEntity.java:246`（实体组件绑定 `compModel.getColumnPropIdMap()`）、`OrmBatchLoadQueueImpl.java:140,475`、`OrmEntityHelper.java:101,197`。
- **触发时序**: T1 进入 `getColumnPropIdMap()`，:45 执行 `colPropIds = new HashMap<>()`（引用已发布、条目为零）→ T1 被调度切出 → T2 进入同一方法，:44 读到 `colPropIds != null` 直接返回**部分填充甚至空 Map** → T2 的 `DynamicOrmEntity.bindToEntity(component, map)` 对缺失 prop 拿到 null 映射 → 组件属性未绑定/错绑。`propIds`（int[]）路径：T1 完成 :115 `this.propIds = propIds` 引用写，JMM 下无 final/volatile 屏障，T2 可见引用但元素写未传播 → `getColumnPropId()` 返回默认值 0 → 按 propId 0 定位列槽位 → 实体状态数组错位写入。两段交错均无需特殊硬件假设（第一段纯逻辑交错）。
- **现状**: 两个字段均为 plain 字段的 check-then-act 惰性初始化，无 volatile、无锁、无构造期预计算；`getColumnPropIdMap` 更是把「发布引用」放在「填充条目」之前。
- **风险**: 每次模型（重）装载后的首批并发组件访问都会进入窗口；命中后组件属性列映射错误，轻则属性丢失/NPE，重则实体状态按错误 propId 读写（数据错位）。模型缓存刷新（租户模型 reload、动态模块重新发布）即重置窗口，长时运行反复暴露。
- **建议**: 首选把两个派生结构的计算移入解析期 `OrmEntityModelInitializer`/`initProps()` 完成后一次性赋值（与 `colsByPropId` 同法），运行期只读；若保留惰性，至少 `private volatile` + 「先局部填充、最后赋引用」（消除先发布后填充），或对齐平台 `computeIfAbsent` 单飞形态。
- **失效面枚举**（F3 同族全清点）: 本类内 `colPropIds`（先发布后填充，最重）与 `propIds`（后赋引用，JMM 可见性）两处同根因。同族模式全模块 grep：`OrmColumnModel.getJavaClass`（:96-103，只读不写=安全）、`OrmReferenceModel.isJoinOnNonPkColumn`（懒写但值恒 false，另立 C1-020）、`OrmEntityModel.init/inited`（守卫式，另立 C1-019）、`LazyLoadOrmModel`（锁内惰性=正确）、`OrmModelValidatorRegistry`（volatile+DCL=正确）、`SchemaToOrmModel.getOrmDefaults`（:24-30，形态相同但实例为 CLI 每次新建、无共享=不立案）、`ExprOrmDataType`（static final=正确）。
- **信心水平**: 确定（数据竞态与先发布后填充形态确定存在；最终数据错位后果依赖下游对 null/0 映射的处理，为很可能）。
- **误报排除**: 不属于「低频路径粗锁」或「无状态只读共享」豁免——两个 getter 是实体加载/绑定的每记录热路径且在运行期做共享可变状态的首次写；触发时序已具体化到行级交错，非「看到共享字段就报」。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。colPropIds 先发布后填充逐行亲验（:45 引用赋值先于填充循环，T2 可返回空/部分 Map 的交错为纯逻辑交错）；propIds 非 volatile 可见性面确认；读侧热路径（DynamicOrmEntity/OrmBatchLoadQueueImpl:475/OrmEntityHelper）确认存在；P1 判级成立

### [nop-persistence-rw-C1-016] LazyLoadOrmModel 运行期 topo 重建对已冻结共享实体模型做无同步写（setDependByOtherEntity），flush 排序读侧无 happens-before

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/lazy/LazyLoadOrmModel.java:54-76` + `init/OrmModelTopEntryBuilder.java:30-37` + `OrmEntityModel.java:194-196`
- **证据片段**:
  ```java
  // LazyLoadOrmModel.checkTopoEntryReady（运行期首触，持有 entityModelMap 锁）
  new OrmModelTopEntryBuilder().build(entityModels, topoEntryMap);
  // OrmModelTopEntryBuilder.build :33-37 —— 对传入的全部实体模型（含 baseModel 冻结实体）写字段
  for (IEntityModel entityModel : graph.vertexSet()) {
      if (graph.getOutwardDegree(entityModel) != 0) {
          entityModel.setDependByOtherEntity(true);
      }
  }
  // OrmEntityModel :194-196 —— 无 checkAllowChange、plain boolean
  public void setDependByOtherEntity(boolean dependByOtherEntity) {
      this.dependByOtherEntity = dependByOtherEntity;
  }
  ```
- **严重程度**: P2（若生产确认为「动态实体依赖此前无被依赖者的静态实体」的高频场景，应升 P1）
- **并发场景**: 租户/动态模型下，请求线程 T1 首触 `checkTopoEntryReady` 触发重建（写者）；全部请求线程在 flush 路径无锁读 `isDependByOtherEntity()`（读者）：`nop-orm` 的 `JdbcEntityPersistDriver.java:240,254`（batchExecuteAsync 决定 delete 归属正/逆序阶段）与 `BatchActionQueueImpl.java:253`（notifyUnsubmittedFailure 失败通知范围）。
- **触发时序**: T1 在 `synchronized(entityModelMap)` 内执行重建，对静态实体 S 写 `dependByOtherEntity=true`（因新加载的动态实体 D 依赖 S，S 静态期为 false）→ T2（另一请求线程 flush）不获取该锁，直接读 S 的 `dependByOtherEntity`，JMM 下无任何 happens-before 边，可无限期读到 stale `false` → T2 的 batchExecuteAsync(topoAsc=true) 把本应逆序阶段执行的 S.delete 放进正序阶段 → 先删被依赖方后删依赖方 → FK 违约报错；同理 notifyUnsubmittedFailure 对 S 漏发失败通知（静默丢通知，F9 形态）。另：每个 LazyLoadOrmModel 实例首触都会对同一批共享冻结实体重复执行该写（DynOrmModelProvider 每租户缓存条目新建实例），形成运行期高频无锁写共享模型的稳定形态。
- **现状**: 静态模型路径该标志在 `OrmModel.init()`（freeze 前）单线程计算完毕，安全；但动态路径把计算推迟到运行期，且写的是 `freeze(true)` 之后的共享冻结模型（setter 无冻结守卫，F10 守卫缺失），读侧（nop-orm flush）与写侧（本模块 topo 锁）无共同锁。
- **风险**: 删除路径 flush 顺序错误（表现为 FK 约束错误，属错误路径行为而非静默数据损坏）+ 批量失败通知遗漏；触发依赖「动态实体改变静态依赖图」这一条件，故 P2 登记并注明升级条件。
- **建议**: 方向一（最小）：`dependByOtherEntity` 声明为 `volatile`（单写多读、写频率低，开销可忽略）并让 `setDependByOtherEntity` 补 `checkAllowChange` 豁免或显式标注运行期合法；方向二（根治）：`LazyLoadOrmModel` 重建 topo 时不改写 baseModel 实体，改为在本实例内维护「有效 dependByOtherEntity」视图（重建结果只入 topoEntryMap 伴生结构）；方向三：租户模型构建时（loadDynamicOrmModel）同步完成重建，把写收敛回发布前。
- **失效面枚举**（F3 同族全清点）: `OrmModelTopEntryBuilder.build` 的调用点共 2 处——`OrmModelInitializer.initTopoMap`（:524-526，解析期 freeze 前=安全）与 `LazyLoadOrmModel.checkTopoEntryReady`（:67，运行期=本条）；`setDependByOtherEntity` 写点全仓仅 TopEntryBuilder 一处；读点 nop-orm 两处（:240/:254、:253）均为无锁读。同族「运行期写冻结模型」兄弟：C1-020（isJoinOnNonPkColumn 懒写）、C1-015（组件惰性初始化）。
- **信心水平**: 很可能（无 happens-before 的读写对确定存在；stale 读的持续时长与实际翻转场景依赖部署形态）。
- **误报排除**: 非「启动期一次性计算」误报——写点在 LazyLoadOrmModel 的运行期惰性路径（类注释自证 putEntityModel 与重建需同锁约束，但读侧并不在该锁内）；亦非把 freeze 后「约定只读」当缺陷——setter 本身就是公开可变入口且被本模块运行期调用。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**（升级条件标注保留：动态实体改变静态依赖图的高频形态确认后可升 P1，留修复轮评估）

### [nop-persistence-rw-C1-017] 冻结守卫族不全：手写 mutator 绕过 checkAllowChange 共 20 处，其中 addProp 先污染 props 再触发守卫（半成功形态）

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmEntityModel.java:426-443`（主证据）+ 下列失效面清单各文件
- **证据片段**:
  ```java
  // OrmEntityModel.addProp —— 无 checkAllowChange；props.put 先于有守卫的委托执行
  public void addProp(IEntityPropModel prop) {
      if (props == null || props.isEmpty()) {
          props = new HashMap<>();
      }
      props.put(prop.getName(), prop);          // ← frozen 后仍会执行
      if (prop.isRelationModel()) {
          addRelation((OrmReferenceModel) prop); // ← 生成类守卫在此才抛
  ```
- **严重程度**: P2
- **并发场景**: 元模型是全平台共享只读面，`freeze(true)`（`OrmModelLoader.java:67`）是其唯一「此后不可变」契约；任何 post-freeze 调用方（含第三方 IOrmModelValidator、动态模型扩展代码）都可经本清单入口无守卫地写共享模型——单线程即可造成状态不一致，多线程下则升级为对共享 HashMap 的无锁写（C1-013 同型）。
- **触发时序**: （守卫缺失型，无并发交错即成立）模型 freeze 后调用 `entityModel.addProp(x)` → :430 `props.put` 成功（props 为手写字段、无守卫）→ :433 委托 `addRelation` 触发生成类 `checkAllowChange()` 抛异常 → 模型处于「props 含 x 但 relations 不含」的半注册不一致态；后续 `getProp(x)` 命中而 `getRelations()` 不含，查询编译与实体加载对同一属性给出分裂视图。若并发发生在 flush 读 `props` 的同时，则为对共享 plain HashMap 的竞态写。
- **现状**: 生成类全部 setter/add 均有 `checkAllowChange()`；缺口集中且仅存在于手写方法（完整清单见失效面枚举），其中 `addProp` 的守卫位置错误造成「守卫抛出前已写一半」，`setDependByOtherEntity`/`setJoinOnNonPkColumn` 则是被运行期路径实际使用的无守卫写点（见 C1-016/C1-020）。当前仓内 `addProp` 唯一调用点为 `OrmModelInitializer.java:287`（freeze 前），故属「可绕过守卫 + 半成功」的防御缺口而非已触发缺陷。
- **风险**: 守卫是模型不可变契约的唯一运行时强制；缺口意味着「冻结后修改」可静默通过，排查期表现为与 C1-015/016 同型的、来源不明的共享模型状态漂移；addProp 的半成功态还会让失败看起来「没改过」而实际改了一半（F2/F9）。
- **建议**: 逐一补 `checkAllowChange()`；`addProp` 将守卫调用提到方法首行（先守卫后写）；`setDependByOtherEntity` 若按 C1-016 方案保留运行期写权，则显式豁免并注释，其余入口一律补齐；可在 `AbstractComponentModel` 层加「冻结态批量自检」测试（freeze 后反射枚举全部 public mutator 逐一调用断言抛错）防同族复发。
- **失效面枚举**（F3 同族全清点，手写无守卫 mutator 全清单）: `OrmEntityModel`——setTenantModel(:124)、setDependByOtherEntity(:194)、setRevLatestKeyColumns(:265)、addTag(:285)、addProp(:426)；`OrmReferenceModel`——setDynamicRelation(:52)、addJoinOn(:70)、setJoinOnNonPkColumn(:133)、setRefPropIds(:148)、setSingleColumnJoin(:188)；`OrmColumnModel`——setOwnerEntityModel(:38)、setColumnRefs(:91)、setJavaClass(:105)、setSqlType(:113)；`OrmComponentModel`——setOwnerEntityModel(:38)；`OrmToManyReferenceModel`——setIgnoreDepends(:26)、setRefSet(:34)、setCollectionName(:48)；`OrmUniqueKeyModel`——addColumn(:23)、setColumnModels(:55)；`OrmModel`——setMerged(:51)；`OrmComputePropModel`——setOwnerEntityModel(:117)。对照：生成类 `_gen/*.java` 全部 setter/add 已带守卫=安全；有守卫手写对照 `OrmEntityModel.setPkColumns`(:184)、`OrmReferenceModel.setColumns`(:85)、`OrmEntityFilterModel`(:32)。
- **信心水平**: 确定（守卫缺口与 addProp 半成功形态为代码事实；post-freeze 调用在当前仓内无触发点，属防御缺口）。
- **误报排除**: 不属于「解析期单线程触达可豁免」——本条不主张存在现实竞态，而是按 F10 口径登记「声明的不可变契约缺运行时守卫/守卫位置错误」；亦非对生成产物的审计（生成侧恰好是正确的对照面）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。addProp 守卫位置后置（先污染 props 再触发守卫）抽验成立

### [nop-persistence-rw-C1-018] LazyLoadOrmModel.putEntityModel 短名冲突失败路径残留半注册状态且 topo 不再重建（getEntityModel 与拓扑视图永久分裂）

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/lazy/LazyLoadOrmModel.java:171-195`
- **证据片段**:
  ```java
  void putEntityModel(IEntityModel entityModel) {
      synchronized (this.entityModelMap) {
          entityModelByTableMap.put(normalizeTableName(...), entityModel);  // 已写入
          entityModelMap.put(entityModel.getName(), entityModel);           // 已写入
          if (entityModel.isRegisterShortName()) {
              IEntityModel oldModel = entityModelMap.put(entityModel.getShortName(), entityModel);
              if (oldModel != null && oldModel != entityModel)
                  throw new NopException(ERR_ORM_MODEL_DUPLICATE_ENTITY_SHORT_NAME)...  // ← 此处抛出
              ...
          }
          this.topoEntryInited = false;   // ← 抛出后不可达
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 动态模型装载路径：请求线程经 `LazyLoadOrmModel.getEntityModel`（:147-157，锁内）调用 `putEntityModel` 注册失败实体；其他请求线程同时在 `getEntityModels()/getEntityNames()/getEntityModelInTopoOrder()`（:84,127,133）与 `getEntityModel(name)`（:137-145 快路径）上读。
- **触发时序**: T1 注册动态实体 X（短名与既有实体冲突）→ :173-175 已写入 entityModelByTableMap 与 entityModelMap（name 键）→ :179 抛 NopException → :193 `topoEntryInited = false` 未执行，若此前 topo 已建（topoEntryInited==true）则保持 true → 此后所有 `checkTopoEntryReady`（:55-56）走快路径直接返回，X 永不进入 topoEntryMap/sortedEntityModels；T2 随后 `getEntityModel("X")` 经 :143 CHM 命中返回 X，而 `getEntityModels()`/`getEntityNames()`/`getEntityModelInTopoOrder("X")` 对 X 报 ERR_ORM_UNKNOWN_ENTITY_NAME 或不含 X → 同一模型实例内两套视图永久分裂（且 `getEntityModelInTopoOrder` 被 flush 排序使用，对 X 排序会抛「未知实体」）。
- **现状**: 失败路径无回滚：冲突抛出前两个 CHM 已写入且无补偿删除；类注释（:166-170）只论证了 put 与重建的锁纪律，未处理「put 中途失败」。
- **风险**: 一次坏模型注册污染共享模型实例的整个生命周期（直至模型缓存刷新）；因异常发生在调用方（DynCodeGen 装载链），上层看到的是「该实体加载失败」，不易联想到注册表已半污染；长时运行下表现为偶发、无法复现的「实体存在但拓扑排序报未知」。
- **建议**: 先校验后写入——把短名冲突检查提为 put 前的独立预检（`entityModelMap.get(shortName)` 只读判断），全部校验通过后再按序写入并置 `topoEntryInited=false`；或 catch 冲突异常后回滚 :173-175 的两处 put 再 rethrow。对照同族：静态路径 `OrmModelInitializer.initEntities`（:129-135）同样先 put 后 throw，但静态模型失败即启动失败、无残留使用，风险仅限动态路径。
- **失效面枚举**（F3 同族全清点）: `putEntityModel` 写点共 4 个容器——entityModelByTableMap(:173)、entityModelMap name 键(:175)、entityModelMap shortName 键(:177，冲突抛点)、snakeCaseNameMap(:190-191)；冲突检查仅覆盖 shortName 键，name 键冲突（同 name 重复注册）会静默覆盖旧值（同族第二形态，一并登记）；静态路径同构代码 `OrmModelInitializer.initEntities`(:126-145) 因失败即终止进程不立案；`addEntityModel`(:162-164) 公开入口直接转发 putEntityModel，继承同一缺陷面。
- **信心水平**: 确定（交错与残留状态均为代码可直接推演的事实）。
- **误报排除**: 非把「注册失败」本身当缺陷——缺陷在于失败后的共享状态不回滚且失效了后续重建触发器（F2 半个成功形态）；亦非低频路径豁免对象：动态实体注册由用户请求数据（NopDynEntityMeta）驱动，坏数据可达。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。失败路径半注册 + topoEntryInited 不可达推演成立

### [nop-persistence-rw-C1-019] 模型 init/发布契约依赖外部纪律：OrmEntityModel.inited 非 volatile check-then-act、OrmModel.init 多字段非原子，类内无发布屏障

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmEntityModel.java:106-108,380-419,426-430` + `OrmModel.java:29-41,133-146`
- **证据片段**:
  ```java
  // OrmEntityModel
  private boolean inited;                          // :108 非 volatile
  public void init() {
      if (inited) return;                          // :381-382 check-then-act
      ... 30 余个字段逐一赋值 ...
      inited = true;                               // :418
  }
  // addProp :427-429 对共享 props 的检查-然后-替换
  if (props == null || props.isEmpty()) { props = new HashMap<>(); }
  // OrmModel.init :133-146 —— 6 个索引字段分步赋值，无同步
  ```
- **严重程度**: P2（理论风险，未具体化——按判级口径从解析期单线程触达条件化降档）
- **并发场景**: 解析/装载路径（`OrmModelLoader.loadOrmModel`、`DynCodeGen` 模块模型缓存构建、`LazyLoadOrmModel.getEntityModel` 锁内装载）对同一实体模型的并发/重入 `init()`；以及任何「init 完成前共享」的误用路径上的读者（`getProp/getColumnByPropId/getEntityModels` 等）。
- **触发时序**: 理论风险，未具体化。可成立的交错形态：T1、T2 同时通过 :381 的 `inited` 检查 → 各自跑一遍 `OrmEntityModelInitializer`（字段被等值重写两次，idempotent 但浪费；若未来某字段非幂等则状态撕裂）；发布侧——当前所有仓内路径经 CHM/cache put（`LazyLoadOrmModel.putEntityModel:175`、`DynOrmModelProvider` 模型缓存）安全发布，未见仓内 unsafe publication 点，故无法给出必然触发的时序，按规则降 P2。
- **现状**: `inited` 守卫无同步（F10 守卫形态）；`OrmModel.init` 的 6 个索引字段非原子发布，`getEntityModels`（:97-99）的 null 回退分支表明「未 init 的模型可被读」是类契约的一部分；类自身不提供任何 happens-before，正确性完全依赖「解析期单线程 + 完成后经并发容器发布」的外部纪律。
- **风险**: 契约无强制——后续任何「先共享后 init」或「并发 init」的调用方（如新的动态装载实现）不会得到编译期或运行期提示；`addProp` 的检查-然后-替换在并发下还会互相覆盖 `props` 引用（与 C1-017 守卫缺口叠加）。
- **建议**: `inited` 加 `volatile` 并用同步块包裹 init 体（或对齐 `OrmModelValidatorRegistry` 的 DCL 形态）；`OrmModel.init` 末尾一次性发布（局部变量构建完 → 赋值到 final/volatile 字段或直接构造不可变索引对象）；javadoc 显式钉定「init 完成并经并发容器发布后只读」契约；`addProp` 的 props 初始化并入构造器。
- **失效面枚举**（F3 同族全清点）: 同一「init 守卫/发布」族：`OrmEntityModel.init/inited`（本条）、`OrmModel.init`（:133-146，无守卫可重入）、`LazyLoadOrmModel.checkTopoEntryReady/topoEntryInited`（:41,54-76，volatile+锁=正确对照）、`OrmModelValidatorRegistry.loadServiceLoaders`（:58-70，volatile+DCL=正确对照）、`ExprOrmDataType`（static 初始化=正确对照）；`addProp` 的 props 替换与 C1-017 共用失效面。
- **信心水平**: 很可能（形态确定；现实触发依赖未来误用，当前仓内无触发链）。
- **误报排除**: 按「本领域误报校准」本可豁免「启动期粗粒度初始化」，但本条登记的不是启动开销而是**契约无守卫**（F10 校准项：声明单线程契约的方法是否有 fail-fast 守卫），并已按写不出具体时序的规则降 P2。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**（理论风险，F10 契约无守卫口径）

### [nop-persistence-rw-C1-020] OrmReferenceModel.isJoinOnNonPkColumn 读路径懒写共享字段：请求线程对冻结模型的数据竞争（值良性）

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmReferenceModel.java:124-135`
- **证据片段**:
  ```java
  public final boolean isJoinOnNonPkColumn() {
      if (joinOnNonPkColumn != null)
          return joinOnNonPkColumn;
      joinOnNonPkColumn = false;   // ← getter 内对共享可变字段写
      return false;
  }
  ```
- **严重程度**: P3
- **并发场景**: 冻结后的关系模型被全部请求线程共享；任一请求线程首次调用该 getter（消费点 `OrmSessionImpl.java:1242`）即对 plain 字段 `joinOnNonPkColumn` 做无同步写，与解析期 `OrmModelInitializer.checkRefPrimary:462` 的 `setJoinOnNonPkColumn(true)` 及其他线程的同名懒写构成数据竞争。
- **触发时序**: 竞态本身确定存在（T1 懒写 false 与 T2 懒写 false 并发、或与解析期写 true 交错），但全部现实交错的结果值均为 false（写 true 的唯一站点在 freeze 前的单线程解析期，发布后不可达），无语义后果；无更坏的交错可构造，按规则判 P3。
- **现状**: getter 副作用写字段是「懒缓存默认值」形态：冻结契约下请求线程写共享模型（与 C1-016 同类的运行期写冻结模型形态），只是值收敛无害。
- **风险**: 当前无行为风险；隐患在于它建立了「getter 可以写共享模型」的先例——若后续有人把懒写默认值改为非平凡计算（如缓存派生结构），即升级为 C1-015 同型缺陷。
- **建议**: 将默认值收敛移入解析期（`OrmModelInitializer` 对所有 ref 显式 `setJoinOnNonPkColumn(false)` 归一），getter 改纯读；或最低成本把字段声明为 `volatile` 并注释单写多读契约。
- **失效面枚举**（F3 同族全清点）: 全模块「getter 懒写共享字段」grep 收敛为本类一处；同族相近形态——`OrmComponentModel.getColumnPropIdMap/Ids`（C1-015，值非良性已立案）、`OrmColumnModel.getJavaClass`（:96-103 纯读不写=安全）、`OrmEntityModel.isAllColumnPrimary`（:131-141 纯读=安全）。
- **信心水平**: 确定。
- **误报排除**: 不按「原语使用非问题」豁免——这里登记的是 getter 副作用这一结构形态及其对冻结契约的破坏，判级已按良性后果落在 P3，未虚报为竞态缺陷。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**（值良性）

### [nop-persistence-rw-C1-021] LazyLoadOrmModel.anyEntityUseTenant 派生标志不随动态实体注册更新：租户会话缓存选型基于过期快照

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/lazy/LazyLoadOrmModel.java:39,45,50-52,171-195`
- **证据片段**:
  ```java
  private volatile boolean anyEntityUseTenant;
  public LazyLoadOrmModel(IOrmModel baseModel, IDynamicEntityModelProvider entityModelLoader) {
      this.anyEntityUseTenant = baseModel != null && baseModel.isAnyEntityUseTenant();  // 仅构造时求值一次
  ...
  // putEntityModel 注册新实体（含 useTenant 实体）时不更新该标志
  ```
- **严重程度**: P2（若确认存在「跨租户复用会话 + 动态租户实体」的部署形态，应升 P1——潜在跨租户缓存串读）
- **并发场景/时序**: 非竞态而是确定性过期：T1 构造 LazyLoadOrmModel（baseModel 无租户实体 → 标志 false）→ T2 经 `addEntityModel/putEntityModel` 注册声明 `useTenant` 的动态实体 → 标志仍 false → nop-orm `OrmSessionImpl.java:130` 依据 `loadedOrmModel.isAnyEntityUseTenant()` 选用 `OrmSessionEntityCache`（不带租户键）而非 `TenantOrmSessionEntityCache` → 该实体的会话缓存条目不按租户隔离；在可跨租户操作的会话（平台管理/批处理遍历租户）中，前一批租户写入的实体状态可被后一批读取命中。
- **现状**: 派生布尔只映照 baseModel 静态快照；`putEntityModel` 维护了 4 个索引容器却独漏该派生值（与 C1-018 同处一个方法，一并为「注册时派生状态维护不全」）。
- **风险**: 动态租户实体的会话级缓存隔离失效；因静态实体占比高、跨租户会话形态窄，现实触发被压缩，但后果面向数据隔离。
- **建议**: `putEntityModel` 内同步维护 `if (entityModel.isUseTenant()) anyEntityUseTenant = true;`（volatile 写，单调置 true 无丢更新问题）；或改为每次求值/在 OrmSessionImpl 取值点穿透到实体级 `isUseTenant` 判定。
- **失效面枚举**（F3 同族全清点）: `LazyLoadOrmModel` 构造期派生值仅此一处；静态对照 `OrmModel.init:144` 在 init 时全量求值且静态模型注册封闭=安全；`putEntityModel` 维护清单：entityModelByTableMap/entityModelMap/snakeCaseNameMap（有维护）、topoEntryInited（有维护，见 C1-018 的失败分支缺口）、anyEntityUseTenant（无维护=本条）。
- **信心水平**: 很可能（代码事实确定；跨租户会话是否实际存在取决于 nop-orm 会话生命周期配置，超出本批次分区）。
- **误报排除**: 非「动态实体不声明 useTenant」的臆测——接口契约（`IEntityModel.isUseTenant`）对动态实体开放该声明，未有任何守卫或文档禁止；也不属于纯功能 bug 移交：它正是 C1 执行步骤 1 的「派生共享可变状态与写者集不一致」盘点项。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**（升级条件标注保留：跨租户复用会话部署形态确认后可升 P1）

### [nop-persistence-rw-C1-022] 共享注册/模型对象的活引用泄漏：getValidators 返回可变 COW list、getEntityNames 返回活 keySet

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmModelValidatorRegistry.java:33-36` + `lazy/LazyLoadOrmModel.java:130-134`
- **证据片段**:
  ```java
  public static List<IOrmModelValidator> getValidators() {
      loadServiceLoaders();
      return VALIDATORS;            // 内部 CopyOnWriteArrayList 原样逸出
  }
  // LazyLoadOrmModel
  public Set<String> getEntityNames() {
      checkTopoEntryReady();
      return topoEntryMap.keySet(); // ConcurrentHashMap 活视图
  }
  ```
- **严重程度**: P3
- **并发场景**: 全局静态注册表与动态模型被全平台共享；外部调用方持有返回引用即可在任意线程对共享注册表做无同步写（VALIDATORS.add/remove 直接改全局）、或对活 keySet 的遍历语义产生「读时弱一致」的误解。
- **触发时序**: 无现实交错（当前仓内消费方均只读），登记为契约缺陷而非竞态；若未来调用方 `getValidators().add(x)`，将绕过 `register` 的 loadServiceLoaders 纪律并直接改写全局校验器集合。
- **现状**: 与批次 2 C1-014（`getFieldExprMetas` 泄漏内部 Map）同族：接口未表达只读契约，实现原样逸出内部容器。`OrmModel.getEntityNames`（:108-110）返回的是不可变 TreeMap 的 keySet（init 后无写者）风险更低，一并纳入同族清单。
- **风险**: 全局注册表的完整性依赖调用方自觉；任何一处「便利性修改」即成为对全平台校验器集合的并发写。
- **建议**: `getValidators()` 返回 `Collections.unmodifiableList(VALIDATORS)`（或构建期快照）；`getEntityNames()` 返回 `Collections.unmodifiableSet` 包装或拷贝；接口 javadoc 钉定只读契约。
- **失效面枚举**（F3 同族全清点）: 本模块共享对象 getter 逸出面全查——`OrmModelValidatorRegistry.getValidators`（可变逸出）、`LazyLoadOrmModel.getEntityNames`（活视图）、`OrmModel.getEntityNames`（:108-110，init 后只读、低危）、`OrmModel.getEntities/getEntityModels`（生成类返回内部 KeyedList，属生成类契约、冻结守卫覆盖写路径，登记为 watch-only）；同族前案：批次 2 C1-014（nop-orm-eql）。
- **信心水平**: 确定。
- **误报排除**: 不属于「无状态只读共享无害」误报豁免的对立面——本条不主张存在竞态，而是 C1 执行步骤 1 第 5 载体（内部集合逃逸）的登记项，判级 P3 与 C1-014 口径一致。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

## 维度集外线索（批次 3）

### [nop-persistence-rw-CL-200]（维度集外线索，不立案展开）LazyLoadOrmModel.getEntityModel 持 entityModelMap 监视器执行 DB 查询 + 模块代码生成：全部 miss 串行化于单锁

- **位置**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/lazy/LazyLoadOrmModel.java:147-157`（C1 视角：锁内装载保证了单飞与安全发布，**正确性无虞**）
- **形态**: C2（锁内重操作）/C9（热路径争用），非 C1。
- **线索描述**: `synchronized (entityModelMap) { ... entityModel = this.loadEntityModel(entityName); ... }` 中 `loadEntityModel` 委托 `DynCodeGen.getDynamicEntityModel`——含 `ormTemplate.runInSession`（完整 ORM 会话 + `requireFirstByExample` DB 查询）与模块级代码生成（`initTenantCache` 声明含「全模块 DB 查询与逐模块代码生成」）。期间该模型实例上所有其他实体装载 miss（乃至 `checkTopoEntryReady` 重建）都在同一监视器上排队；不同实体无法并行装载，首触风暴（租户模型刷新后大批实体并发访问）退化为串行。synchronized 可重入性使 loader 回调 `getEntityModel` 不致死锁（同线程重入），当前亦未发现跨线程锁序环，无 C1 立案面。
- **移交建议**: C2/C9 批次若立案，方向为「锁外装载 + 锁内 put 的 Future/Promise 单飞」或按实体名分桶锁；注意保持 putEntityModel 注释（:166-170）声明的「与重建同锁」纪律不破坏。
- **登记状态**: 维度集外线索，本批次不展开、不计入 C1 发现计数。

### 批次 3 小结

- **发现清单**：C1-015（P1）、C1-016（P2）、C1-017（P2）、C1-018（P2）、C1-019（P2，理论风险）、C1-020（P3）、C1-021（P2）、C1-022（P3）。
- **CL 线索**：CL-200（维度集外，C2/C9 形态）。
- **总计**：8 条发现（0×P0、1×P1、5×P2、2×P3）+ 1 条维度集外线索。
- **末位发现编号**：C1-022；末位 CL 编号：CL-200。
- **总体判定**：nop-orm-model 的 C1 风险重心不在静态状态（基线三节仅有 `serviceLoaderLoaded` 一处且实现正确）而在**「冻结模型 = 只读」契约在动态装载路径上的三处破口**：运行期惰性初始化（C1-015，已具体化 P1）、运行期 topo 重建写派生标志（C1-016）、守卫族缺口（C1-017）。静态路径（解析期单线程 + freeze + 缓存安全发布）纪律良好；`LazyLoadOrmModel` 的锁纪律大体正确，但失败路径派生状态维护不全（C1-018/021）。修复轮建议优先收敛 C1-015/016（均可在解析期预计算根治），C1-017 按失效面清单逐项补守卫并以冻结态 mutator 自检测试防复发。

## 批次 4/4（nop-dao）

- **审计单元**: `nop-persistence/nop-dao/src/main/java`（131f/17,595，基线口径 122f/13,435——本分支差异为读写分离/分片/upsert 新增面；含 `_gen/` 生成物 7f，按纪律不作为发现对象）
- **批次**: 批次 4/4（末批，预拆制）。发现编号从 `nop-persistence-rw-C1-023` 起、维度集外线索从 `nop-persistence-rw-CL-300` 起。
- **盲审纪律声明**: 未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅阅读方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md` 与当前 live code。「现状」一律按盲审口径填新发现。
- **生命周期模型核实（判级前置）**: (1) 事务对象（`AbstractTransaction` 族）经 `TransactionRegistry` 存入 **IContext 属性**（`BaseContext.attributes` 为 ConcurrentHashMap，`BaseContext.java:46`）；`IContext extends Executor`，`ContextProvider.thenOnContext/completeAsyncOnContext` 把**同一 context 实例**附加到 executor 线程执行异步续段，且 `AbstractTransaction.java:54-55` 注释自证「commitAsync/rollbackAsync 路径的 listener 回调发生在 executor 线程，与注册线程并发」——**事务对象被设计为跨线程可达**，其非同步字段按此口径判级。(2) `JdbcTemplateImpl`/`DefaultTransactionManager`/`DialectManager` 等为 IoC 单例，setter 注入发生在启动期，运行期只读字段不判缺陷。

### 共享可变状态清单（批次 4 范围内全枚举：字段 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|---------|------|
| 1 | `DaoProvider._instance`（static，:11） | `OrmDaoProvider.register()`（启动期）+ 测试 | 全平台 DAO 入口（非测试 10+ 调用点） | 无（非 volatile） | **缺陷 C1-024** |
| 2 | `DialectManager._instance`（static，:34） | 类初始化器 | 全部方言读取 | 类初始化 happens-before | 正确 |
| 3 | `DialectManager.dialectSelectors`（:36） | loadDialectSelectors/addDialectSelector | getDialectName | CHM + COW + synchronized(list) | 正确（getDialectSelectors 泄漏活 map，观察级） |
| 4 | `TransactionRegistry`（IContext 属性） | instance() 首触创建 | DefaultTransactionManager register/unregister/get | get+set 组合**非原子** | **缺陷 C1-023** |
| 5 | `AbstractTransaction.opened/rollbackOnly/error/subTransactions`（:52-60） | open/commit/rollback/markRollbackOnly/addSubTransaction | isTransactionOpened/isRollbackOnly/getSubTransaction | 无（plain 字段） | **缺陷 C1-025** |
| 6 | `AbstractTransaction.listeners`（:56，COW） | addListener 懒创建 | invokeListener 快照迭代 | COW（懒创建非原子，窗口极窄） | 基本正确（并入 C1-025 失效面） |
| 7 | `JdbcTransaction.connection`（:35） | getConnection 惰性获取 | doCommit/doRollback/releaseConnection | 无 check-then-act | **缺陷 C1-026** |
| 8 | `JdbcTemplateImpl.clockMap`（:90） | computeIfAbsent | getDbEstimatedClock | CHM，键=querySpace 可枚举 | 正确 |
| 9 | `JdbcTemplateImpl.cacheMap` + 缓存条目内容（:92） | computeIfAbsent / saveCacheData put | getCacheData / 失效入口 | CHM 容器原子；**条目内容 vs 失效非原子** | **缺陷 C1-027** |
| 10 | `JdbcTemplateImpl.noCacheProviderWarned`（:94） | CAS | warn 抑制 | AtomicBoolean | 正确 |
| 11 | `ReadWriteHint.s_hint`（:44）/ `QuerySpaceEnv.s_querySpace`（:15）/ `DynamicDataSource.s_dataSource`（:23） | enter/leave、runWith* 成对恢复（null→remove） | 同线程 | ThreadLocal 线程封闭 | 正确（DynamicDataSource.switchDataSource 公开裸 set API，仅 javadoc 警告无守卫、无生产调用点，watch-only） |
| 12 | `DefaultTransactionManager.transactionFactoryMap/txnGroupMap`（:42-43） | 启动期 setter + addQuerySpace | 运行期 get | CHM | 正确 |
| 13 | `DefaultTransactionManager.querySpaceToDialectMap`（:45） | 惰性初始化（:221-226） | getDialectForQuerySpace 每查询 | 无（非 volatile） | **缺陷 C1-028**（良性，P3） |
| 14 | `JdbcDialectProvider.querySpaceToDialectMap`（:36） | 运行期 memoize put | getDialectForQuerySpace | CHM（check-then-act 并发首查重复嗅探） | 良性（CL-302） |
| 15 | `ReplicaChannel.consecutiveFailures/cooldownUntil/lastErrorMessage`（:22-24） | recordSuccess/recordFailure（synchronized）+ setLastErrorMessage（无锁） | isCooling/isHalfOpen/describe | synchronized + volatile 混合 | **缺陷 C1-030**（P3） |
| 16 | `ReadWriteRoutingDataSource.replicas`（:45） | addReplica（启动装配） | getReplicaConnection0 迭代 | bean 启动发布 | 正常模式 |
| 17 | `AbstractSqlExecutor.FIND_FIRST_RANGE`（:19，static 非 final protected） | 无（当前无赋值点） | exists/findFirst/buildPagedSql 身份比较 | 引用不可变对象但槽位可变 | **缺陷 C1-029**（P3 防御性） |
| 18 | `DialectSQLExceptionTranslator.predefined_errors`（:66，static final HashMap） | 仅 static 块 register（全仓无其他调用者，已 grep） | buildErrorCode | 类初始化 happens-before | 正确 |
| 19 | `DialectImpl.functions/sqlDataTypeMapping/reservedWords/renameMap`（:80-83） | 构造器/initFunctions/initDataTypes | 运行期只读 | 构造后不可变 + 平台模型缓存发布 | 正确 |
| 20 | `DbEstimatedClock.timeData/fetching/TimeData.elapsedTime`（:23-25） | fetch/_fetchFromDb（volatile+sync+recheck） | isExpired | volatile + synchronized 双检 + `timeData==data` 复核 | 正确（isExpired 锁外单调写 elapsedTime 为良性最后写胜出） |
| 21 | `SnowflakeSequenceGenerator.sequence/lastTimestamp`（:55-57） | generateLong | 同方法 | synchronized 方法 | 正确（误报校准：原语使用非问题） |
| 22 | `DaoMetricsImpl` 全部字段（:22-36） | 构造器 | end*/begin* | final + micrometer/原子化 JdbcSqlStat | 正确 |
| 23 | `ShardStrategy` 全字段 / `ConfigShardSelector.strategies/entityBindings` / `ShardAlgorithms` 各算法 | init()/构造器（启动期） | 运行期只读 | final + unmodifiableMap | 正确 |
| 24 | `ReadWriteLoadBalancers`（AtomicLong counter / ThreadLocalRandom / 局部拷贝排序） | order() | 同方法 | AtomicLong + 线程封闭 | 正确 |

## 发现条目

### [nop-persistence-rw-C1-023] TransactionRegistry.instance() 对上下文属性的 check-then-act 创建：迟到 setAttribute 覆盖并发分支已注册的事务表（事务孤立 / 同组重复开事务 / 清理期假性 ERR_TXN_NOT_REGISTERED）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionRegistry.java:22-30`
- **证据片段**:
  ```java
  public static TransactionRegistry instance() {
      IContext context = ContextProvider.getOrCreateContext();
      TransactionRegistry registry = (TransactionRegistry) context.getAttribute(CONTEXT_KEY);
      if (registry == null) {
          registry = new TransactionRegistry();
          context.setAttribute(CONTEXT_KEY, registry);
      }
      return registry;
  }
  ```
- **严重程度**: P1
- **并发场景**: 同一 IContext 的两个异步分支（`IContext extends Executor`；`ContextProvider.thenOnContext/completeAsyncOnContext` 将同一 context 实例附加到 executor 线程；`BaseContext.attributes` 为 ConcurrentHashMap，单操作线程安全但 **get→new→set 三步组合非原子**）。典型入口：GraphQL 并行 datafetcher、`runInTransactionAsync` 任务返回的 CompletionStage 内 `FutureHelper.waitAll` 并行分支、网关异步链——在同一请求上下文中并发发起**该上下文的首次**事务性 DAO 访问。
- **触发时序**: 全新请求上下文 C（此前无任何线程触碰过 TransactionRegistry）。T1 执行到 :26 `new TransactionRegistry()`（registry1，尚未 setAttribute）被调度让出 → T2 完整走完 `instance()`：读到 null → 创建 registry2 → :27 `setAttribute(registry2)` → T2 继续在 registry2 中注册并打开事务 txn2、执行 SQL → T1 恢复，:27 `setAttribute(registry1)` **盲目覆盖 registry2（内含 T2 已注册的 txn2）** → 后果链：(a) T2 清理期 `cleanupTransaction`（TransactionTemplateImpl.java:406-438）→ `unregisterTransaction` → `instance()` 取到 registry1 → `remove(txnG, txn2)` 返回 false → 抛 `ERR_TXN_NOT_REGISTERED`（事务对象仍会 close、连接仍释放，但请求以假性事务错误失败）；(b) 此后该上下文生命周期内所有 `getRegisteredTransaction` 读 registry1，txn2 永久不可见——同 txnGroup 的后续 REQUIRED 进入全部判定「无事务」各自 `newlyOpen`，**同一逻辑事务组裂为多个独立提交作用域**，原子性语义失效。
- **现状**: `getAttribute` 与 `setAttribute` 之间无任何互斥或 CAS；`new TransactionRegistry()` 每次创建新实例，迟到写直接覆盖。
- **风险**: 罕见但可具体化（触发窗口=首触并发 + 一次调度延迟，GraphQL 并行取数场景下可达）；后果含事务分组破裂与请求可见的假性异常，且 (b) 项在上下文剩余生命周期内持续。
- **建议**: 用上下文属性 map 的原子 get-or-create：`context.getAttrs().computeIfAbsent(CONTEXT_KEY, k -> new TransactionRegistry())`（`BaseContext.getAttrs()` 已暴露 ConcurrentHashMap）；或 `synchronized (TransactionRegistry.class)` 包裹三步。平台同类先例见批次 1 C1-001（OrmSessionRegistry 同形态）。
- **失效面枚举（F3）**: `DefaultTransactionManager.getRegisteredTransaction/registerTransaction/unregisterTransaction`（DefaultTransactionManager.java:158-182）三个入口全部经 `TransactionRegistry.instance()`，共享同一根因，修复一处即全覆盖；同族兄弟 `OrmSessionRegistry`（nop-orm，IContext 属性 get-or-create 同形态）已在批次 1 C1-001 立案——本条与之为**同族跨模块复发**，修复轮应统一收敛为平台级「上下文属性原子 get-or-create」工具方法；`QuerySpaceEnv/ReadWriteHint` 为 enter/leave 成对模式，不属同族。
- **信心水平**: 确定（竞态窗口与后果链均可由代码直接推演；未复现仅为未执行并发测试）
- **误报排除**: 非平台设施内部问题——`ContextualizedRegistry`（基类）与 `BaseContext.attributes` 自身线程安全，缺陷在 nop-dao 对它们的**复合调用**；非「低频启动路径粗锁」豁免——instance() 在每查询/每事务路径热调用。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。与 C1-001 逐字节同构亲验成立（TransactionRegistry.java:24-29）；迟到 setAttribute 覆盖后果链具体；同族合并终裁**确认**（同 C1-001）

### [nop-persistence-rw-C1-024] DaoProvider 静态单例槽非 volatile、无发布屏障、无守卫：引擎级「每 DAO 访问必经」入口的不安全发布

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/api/DaoProvider.java:10-20`
- **证据片段**:
  ```java
  public class DaoProvider {
      private static IDaoProvider _instance;
      public static IDaoProvider instance() {
          return _instance;
      }
      public static void registerInstance(IDaoProvider daoProvider) {
          _instance = daoProvider;
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: IoC 容器线程（构造 `OrmDaoProvider`、setter 注入、调 `register()`）写；全部 HTTP/graphql/异步 worker 线程经 `DaoProvider.instance().dao(...)` 读（生产调用点 10+：nop-orm `OrmDaoHelper`/`DaoEntityResource`/`DaoResourceNamespaceHandler`、nop-dyn、nop-sys、业务 BizModel）。`DaoProvider.instance()` 是平台数据访问的全局静态入口，「每查询/每事务必经」面。
- **触发时序**: T1（IoC 线程）：`new OrmDaoProvider()`（daoMap 为 final 字段安全）→ `setOrmTemplate(ormTemplate)`（**非 final 实例字段** OrmDaoProvider.java:23，普通写）→ `register()` → `_instance = provider`（普通 static 写，无 volatile/锁）→ T2（早启 worker 线程）读 `_instance` 数据竞争地观察到非 null 引用但**未见 ormTemplate 写入**（无 happens-before）→ `dao()` → `ormTemplate.getFullEntityName(...)` NPE；JMM 下 T2 也可无限期观察到 `_instance == null`（同族先例批次 1 C1-006 OrmTimestampHelper 同形态）。
- **现状**: 静态槽位非 volatile、读写无同步；`registerInstance` 无重复注册守卫（可被静默覆盖，测试代码确实以 `registerInstance(null)` 复位）。
- **风险**: 实际概率低（容器启动通常先于流量放行），但这是平台级契约缺陷：任何「容器未完全启动即发请求」的嵌入形态（测试、热加载、程序化启动）都会以 NPE/null 形态失败；引擎级单例按本审计判级口径门槛从低。
- **建议**: `_instance` 加 `volatile`（一行修复，发布语义即完备）；`registerInstance` 对重复注册记 WARN（对齐可观测性）；长期方向与 C1-023 一致——平台提供统一的「全局单例安全发布槽」工具。
- **失效面枚举（F3）**: nop-dao 内 static 持有者全查：`DaoProvider._instance`（本条，非安全发布）、`DialectManager._instance`（DialectManager.java:34，**静态初始化器发布，安全**——同族对照）、`DialectSQLExceptionTranslator.predefined_errors`（静态块写后只读，安全）、`AbstractSqlExecutor.FIND_FIRST_RANGE`（另案 C1-029）；跨模块同族：`OrmTimestampHelper`（nop-orm，批次 1 C1-006 已立案）、`BeanContainer` 持有者（nop-kernel，平台设施不重审）。nop-dao 范围内本条是唯一不安全发布的静态单例槽。
- **信心水平**: 确定（JMM 层面成立）；实际触发概率低
- **误报排除**: 不适用「无状态单例被并发调用是设计意图」豁免——问题不在无状态，而在**发布顺序**；不适用「启动路径粗锁合理」豁免——读路径是每查询热路径且完全无同步。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。DaoProvider._instance 非 volatile 亲验（:11）；静态槽收紧切片成员确认

### [nop-persistence-rw-C1-025] AbstractTransaction 状态族（opened/rollbackOnly/error/subTransactions）无同步跨线程读写 + open() 非原子守卫：路由判定读到陈旧 opened 违反「活动事务内一律主库」契约

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:52-60,105-111,390-399`
- **证据片段**:
  ```java
  private boolean opened;                       // :52 plain 字段
  private boolean rollbackOnly;                 // :57
  private Throwable error;                      // :58
  private Map<String, ITransaction> subTransactions;  // :60 LinkedHashMap
  ...
  public void open() {                          // :390
      if (opened) throw newError(ERR_TXN_ALREADY_STARTED);   // check-then-act 非原子
      invokeOpenListener(); doOpen(); openSubTransactions();
      opened = true;
  }
  ```
- **严重程度**: P2
- **并发场景**: 类注释（:54-55）自证 listener 回调发生在 executor 线程与注册线程并发——事务对象设计上跨线程可达。读点：`isTransactionOpened()`（:70，被 `JdbcTemplateImpl.withTxnForSql`:208-209 用作**主库/从库路由判定**）、`isRollbackOnly()`（:143，commit 前守卫）、`getSubTransaction`（:121，经 `TransactionTemplateImpl.getRegisteredTransaction`:74 跨线程组事务解析）；写点：open/close/markRollbackOnly/addSubTransaction。
- **触发时序**（可见性）: 异步事务内两分支共享上下文。T1 分支执行 `open()`→`opened = true`（:398，普通写）；T2 分支在另一 executor 线程执行 DAO → `withTxnForSql` 读 `txn.isTransactionOpened()` 普通读——两读写实线程间**无 happens-before**（分支启动时的 executor 提交屏障只保证 fork 前写入可见，不保证 T1 运行中写入）→ T2 陈旧读到 false → 建立 REPLICA 作用域 → 只读语句路由从库，而同组写事务活动——**违反 ReadWriteRoutingDataSource javadoc 明示的一致性契约「活动事务内一律主库」**（ReadWriteRoutingDataSource.java:36-37），读到从库旧值。
- **触发时序**（守卫）: T1/T2 同并发对同一未打开事务各走 `withTxn(REQUIRED)` → 双双通过 `if (opened)` → 双 `doOpen()`（同连接重复 setAutoCommit(false)）+ `openSubTransactions()` 第二次对 subTxn 调 open() 抛 `ERR_TXN_ALREADY_STARTED` → 双方 TxnState 均标 newlyOpen → 清理期双 commit/双 close 竞争。
- **现状**: 四个状态字段全部 plain；open() 守卫、markRollbackOnly→isRollbackOnly、addSubTransaction→getSubTransaction 均无互斥/内存屏障。
- **风险**: 可见性后果为语义违约（读己之写失效）而非数据损坏；守卫后果为并发同组分支下的错误级联。均为平台公开异步 API 可达形态。
- **建议**: `opened`/`rollbackOnly` 加 volatile（读多写少，代价可忽略）；`open()` 用 `synchronized` 或 AtomicBoolean compareAndSet 实现原子守卫；`subTransactions` 换 ConcurrentHashMap（键=txnGroup 有限）。 listeners 已是 COW（正确），一并纳入同一修复切片。
- **失效面枚举（F3）**: 字段族全查：`opened`（本条）、`rollbackOnly`（:57，markRollbackOnly 写/isRollbackOnly 读跨线程）、`error`（:58，markRollbackOnly 写/beforeRollback 读）、`subTransactions`（:60，LinkedHashMap 跨线程读写，CHM 化前并发 put/get 有撕裂读风险）、`listeners`（:56，COW 已正确、仅懒创建丢失窗口，归入本切片）；子类：`JdbcTransaction.connection` 另案 C1-026、`GroupTransaction.txnId`（:11，构造后只读，正确）。读侧消费点全查：`JdbcTemplateImpl.withTxnForSql`:209（本条主后果）、`TransactionTemplateImpl.openTransaction` 各 propagation 分支的 `isOpened()`（:281/:302）、`beforeCommit` 的 `isRollbackOnly()`（:288）。
- **信心水平**: 很可能（JMM 层面成立，后果链完整；实际触发依赖异步分支时序）
- **误报排除**: 不适用「平台 nop-kernel 设施不重审」——AbstractTransaction 是 nop-dao 自有类；不适用「synchronized/COW 原语使用非问题」——本条恰是**该用原语而未用**。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。状态族可见性面与 open() 守卫面（C2-016 互引）分离判定成立

### [nop-persistence-rw-C1-026] JdbcTransaction.getConnection 惰性取连接 check-then-act：异步事务并行分支双连接获取 → 一条连接永久泄漏 + 事务原子性破裂

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/txn/JdbcTransaction.java:35,53-75`
- **证据片段**:
  ```java
  private Connection connection;                // :35 plain 字段
  ...
  public Connection getConnection() {           // :61
      if (connection == null) {                 // check-then-act
          LOG.trace("nop.dao.txn.fetch-connection:conn={}", this);
          connection = dataSource.get();        // 从池获取
          ...
      }
      return connection;
  }
  ```
- **严重程度**: P1
- **并发场景**: `runInTransactionAsync`（TransactionTemplateImpl.java:185-225）任务返回 CompletionStage，用户在任务内以 `FutureHelper.waitAll`/`allOf` 展开并行分支（平台公开且被鼓励的异步形态），各分支在不同 executor 线程对**同一注册事务对象**执行 DAO（经 `getRegisteredTransaction` 汇聚到同一 `JdbcTransaction`）。
- **触发时序**: T1 分支执行到 :62 `if (connection == null)` 判定 true（被调度让出）→ T2 分支同样判定 true → T1 `connection = dataSource.get()` 得 conn1 → T2 `connection = dataSource.get()` 得 conn2 **覆盖 conn1** → T1 在 conn1 上执行 SQL（局部引用仍可达，写已发生）→ 事务提交 `doCommit()` 只 commit `this.connection`=conn2 → **conn1 永不 commit/rollback、永不 close（releaseConnection 只清 this.connection）→ 池连接泄漏 + conn1 上的写处于未决事务，语义半个成功**。
- **现状**: 惰性获取无同步、无 CAS 守卫；同类模式 `getConnection0()`（:53-57）同缺陷；`releaseConnection()`/`doCommit()`/`doRollback()` 对 `connection` 的读写同样无同步。
- **风险**: 触发链明确（异步并行分支 + 同组汇聚）；后果为资源泄漏（池耗尽方向）与事务原子性破裂双重，属「罕见但可具体化的竞态」P1 档；池连接泄漏在负载上升时放大。
- **建议**: `getConnection()` 加 `synchronized`（连接获取每事务至多一次，锁开销可忽略）；或惰性获取改为事务 open 时急切获取（`doOpen` 已调 `getConnection0`，REQUIRED 路径本已打开——竞态面收窄到 SUPPORTS 穿透路径）；同时在 `runInTransactionAsync` javadoc 声明「同一事务对象非线程安全」契约（F10：声明 + 守卫双管）。
- **失效面枚举（F3）**: 惰性单值获取族全查：`getConnection`（:61-75，本条）、`getConnection0`（:53-57，被 doOpen 调用——open 与分支 getConnection 之间同样存在 check-then-act 竞争）、`releaseConnection`（:113-120，check-then-act 置 null，与并发 getConnection 交叉时可能 close 后复用）、`restoreAutoCommit`（:102-111，读 connection 无同步）；兄弟类：`GroupTransaction`（无连接状态，不受影响）；同族上游：`TransactionTemplateImpl.TxnState` 为方法本地（安全）。连接泄漏镜像问题（close 与 getConnection 交叉）同属本切片回归面。
- **信心水平**: 确定（竞态窗口大：池获取 + 建连为毫秒~十毫秒级，远大于检查窗口）
- **误报排除**: 非测试代码简化模型——`runInTransactionAsync` 是生产 API；非「池借还有平台 pool 负责」——此连接由 JdbcTransaction 自管生命周期，泄漏真实。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。getConnection check-then-act + 双取覆盖泄漏 + doCommit null 早退逐行亲验（JdbcTransaction.java:59-75/:92-100）；异步并行分支共享事务对象为平台公开语义（runInTransactionAsync），触发链具体，P1 成立

### [nop-persistence-rw-C1-027] JDBC 查询缓存回填与失效的竞态：miss→慢查询→put 与 clear/evict 竞赛，陈旧结果集在活缓存中驻留至下次失效

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:341-385,394-456,234-255`
- **证据片段**:
  ```java
  // 回填（executeQuery 内）
  rs = st.executeQuery();                       // :362 慢 I/O，读的是 T1 提交前快照
  ds = saveCacheData(ds, sql, range);           // :365 → cache.put(cacheKey, cacheData)  :453
  // 失效
  public void clearQueryCacheFor(String cacheName) {   // :244
      ICache<Object, DataSetCacheData> cache = cacheMap.get(cacheName);
      if (cache != null) cache.clear();
  }
  ```
- **严重程度**: P2
- **并发场景**: 携带 `cacheRef` 的查询（每查询热路径）与写事务后的缓存失效（`clearQueryCache/clearQueryCacheFor/evictQueryCache`）并发；请求线程 × 写事务线程。
- **触发时序**: T2 查询 `getCacheData` miss（:410）→ 执行 DB 查询读到**旧数据**（:362，查询进行中）→ T1 写事务提交并触发 `clearQueryCacheFor(k)`（:247 cache.clear()）→ T2 `saveCacheData` 执行 `cache.put`（:453）**落在 clear 之后** → 陈旧结果集以合法键驻留活缓存；后续查询 `getCacheData` 命中（range/SQL 文本校验 :415-425 均通过——它们校验的是形状不是新鲜度）→ 持续返回旧数据，直到下一次失效或缓存 TTL。
- **现状**: 「查询→回填」与「失效→清空」两个复合序列无任何顺序保证；`clearQueryCache()`（:234-241）因同时 `it.remove()` 移除映射，竞态后果退化为孤儿缓存（无害），但 `clearQueryCacheFor`/`evictQueryCache`（:243-255）**不移除映射**，与回填直接竞赛。
- **风险**: 显式失效语义被击穿；若缓存配置无 TTL 则陈旧窗口不限。判定 P2：需要「查询与失效并发」这一常见但非必然的时序，后果为脏读级（非损坏）。
- **建议**: 回填前二次校验版本/世代（缓存条目携带数据版本，put 用 `cache.compute` 对齐失效计数）；或将回填收敛为「失效世代号」模式（clear 递增世代，put 携带世代，读时世代不符即弃）；最小修复为 `saveCacheData` 处对照失效计数器（`AtomicLong invalidationEpoch`）。
- **失效面枚举（F3）**: 失效入口全查：`clearQueryCache`（:235-241，remove 模式良性）、`clearQueryCacheFor`（:243-248，**竞赛实锤**）、`evictQueryCache`（:250-255，同上，单键粒度）；回填点全查：`saveCacheData` 唯一（:437-456）；读点：`getCacheData`（:394-427）。同族跨模块：nop-orm 全局实体缓存失效（批次 1 C1-010 邻接，失效侧不同机制）——修复轮统一裁定缓存失效竞态的平台级口径。
- **信心水平**: 确定（时序交错为教科书式 invalidation-vs-repopulate，窗口=整条查询时长）
- **误报排除**: 不适用「有界缓存已配置上限」豁免——问题是新鲜度不是容量；:415-425 的 range/SQL 校验只防串键，不防陈旧。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。invalidation-vs-repopulate 竞态成立；与 C2-004 同族相邻终裁**确认**（不同缓存载体、同一竞态形状：修复共享「失效世代号」设计、两个 work item 不合并执行）

### [nop-persistence-rw-C1-028] 方言映射记忆化双实现缺陷族：DefaultTransactionManager 非同步惰性初始化（非 volatile + 重复构建 + 不安全发布）与 JdbcDialectProvider 并发首查重复嗅探

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/DefaultTransactionManager.java:45,221-226`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcDialectProvider.java:36-52`
- **证据片段**:
  ```java
  // DefaultTransactionManager
  private Map<String, String> querySpaceToDialectMap;      // :45 非 volatile
  protected Map<String, String> getQuerySpaceToDialectMap() {   // :221
      if (this.querySpaceToDialectMap == null) {
          this.querySpaceToDialectMap = DaoHelper.getQuerySpaceToDialectConfig();
      }
      return querySpaceToDialectMap;
  }
  ```
- **严重程度**: P3
- **并发场景**: `getDialectForQuerySpace`（每查询热路径，DefaultTransactionManager.java:232-241）在读侧；仅当配置 setter `setQuerySpaceToDialectMap` 未被调用（可选项）时运行期首触才进入惰性分支。并发首查两线程。
- **触发时序**: T1/T2 同时首查 → 双见 null → 双调 `DaoHelper.getQuerySpaceToDialectConfig()` 重复构建 → 普通写发布 HashMap → 对方线程可能读到**非 null 引用但未完全填充**的 map → `getDialectNameForQuerySpace` 返回 null → 走 `getTransactionFactory(querySpace).getDialectForQuerySpace` 兜底路径，方言解析结果仍正确。
- **现状**: 经典 benign-race 惰性初始化；重复构建廉价、错读兜底正确，故 P3。
- **风险**: 理论上不安全发布可暴露 HashMap 内部不一致（极端 ClassCastException），实用后果限于多余构建与兜底路径分散。
- **建议**: 字段加 volatile；或启动期在 `@PostConstruct`/构造器无条件初始化（消灭运行期惰性分支）。JdbcDialectProvider 同族点（CHM memoize）改为 `computeIfAbsent` 消除并发首查重复开事务嗅探（见 CL-302）。
- **失效面枚举（F3）**: 方言记忆化族全查：`DefaultTransactionManager.querySpaceToDialectMap`（:45，本条主体）、`DefaultTransactionManager.getDialectNameForQuerySpace`（:228-230，读侧）、`JdbcDialectProvider.querySpaceToDialectMap`（:36，CHM 容器安全、复合 check-then-act 归 CL-302）、`JdbcTransactionFactory` 构造期方言解析（:26-30，启动期单线程，安全）、`DialectManager.getDialect`（经 ResourceComponentManager 平台缓存，安全）。dao 模块内无第三处方言缓存。
- **信心水平**: 确定（模式识别）；后果轻微
- **误报排除**: 不按 P2 报——写不出造成错误行为的时序（兜底路径保证正确性），按口径归 P3 防御性收紧。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**。与 C2-020 为同一字段族双立案确认（可见性面 vs check-then-act 面）：合并为一个修复项（volatile 化）、台账互引、remediation 计数去重

### [nop-persistence-rw-C1-029] AbstractSqlExecutor.FIND_FIRST_RANGE：protected static 非 final 可变槽位 + buildPagedSql 依赖引用身份比较，子类重赋值即静默改变 exists/findFirst 语义

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/api/AbstractSqlExecutor.java:19`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:387-392`
- **证据片段**:
  ```java
  protected static LongRangeBean FIND_FIRST_RANGE = LongRangeBean.of(0, 1);   // 非 final
  ...
  private SQL buildPagedSql(SQL sql, LongRangeBean range, IDialect dialect) { // JdbcTemplateImpl:387
      if (range == null || range == FIND_FIRST_RANGE)   // 引用身份比较
          return sql;
  ```
- **严重程度**: P3
- **并发场景**: 当前无写入点（全仓 grep 仅 AbstractSqlExecutor:19 定义、exists:25/findFirst:29 读取、buildPagedSql:388 比较），referent `LongRangeBean` 为 final 字段不可变对象，静态初始化发布——**现状行为正确**。风险在契约层：槽位可被子类（`JdbcTemplateImpl`、`OrmTemplateImpl` 两个子类共享同一 static）重赋值。
- **触发时序**（假设性，未具体化为现实缺陷）: 若任一子类运行期重赋值 `FIND_FIRST_RANGE = LongRangeBean.of(0, N)`：T1 在 `findFirst` 读取旧引用作为 range 传入 → T2 重赋值 → `buildPagedSql` 以**新引用**做身份比较 → 不相等 → findFirst 被意外施加分页语法（语义静默改变）；非 volatile 槽位的跨线程写读另无可见性保证。属「写不出现实触发时序」的理论契约风险，按口径降 P3 登记。
- **现状**: 非 final、protected（对子类开放写权限）、身份比较三重脆弱性叠加。
- **风险**: 防御性缺口而非现行缺陷；一旦下游 delta 定制/子类重赋值，故障形态是静默语义变化而非显式报错。
- **建议**: 改 `private static final`（或将身份比较改为 `range.getOffset()==0 && range.getLimit()==1` 的值比较），一处改动消除整族风险。
- **失效面枚举（F3）**: static 可变引用族全查（nop-dao static 非 final 全集已枚举于清单表 #1/#2/#17）：`DaoProvider._instance`（C1-024）、`DialectManager._instance`（静态初始化安全）、`FIND_FIRST_RANGE`（本条）。读取点全查：`AbstractSqlExecutor.exists`:25、`findFirst`:29、`JdbcTemplateImpl.buildPagedSql`:388；子类全查：`JdbcTemplateImpl`:81、`OrmTemplateImpl`（nop-orm:52，仅经继承 exists/findFirst 读取）。
- **信心水平**: 确定（契约缺口存在）；无现实触发
- **误报排除**: 不按「原语使用非问题」豁免——本条登记的是 static 可变槽位**应 final 而 non-final** 的 C1 第 1 节枚举义务，非指控现行行为错误。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

### [nop-persistence-rw-C1-030] ReplicaChannel 状态机锁纪律不齐：lastErrorMessage 经无锁 setter 写入，绕开 synchronized 状态机互斥，可残留与状态矛盾的陈旧错误消息

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/datasource/ReplicaChannel.java:22-24,53-78`
- **证据片段**:
  ```java
  private int consecutiveFailures;              // 仅 synchronized 块访问
  private volatile long cooldownUntil;          // volatile + synchronized 写
  private volatile String lastErrorMessage;     // volatile，但 setter 无锁
  ...
  public void setLastErrorMessage(String message) {   // :76 无 synchronized
      this.lastErrorMessage = message;
  }
  ```
- **严重程度**: P3
- **并发场景**: 多请求线程并发路由（`ReadWriteRoutingDataSource.getReplicaConnection0`:142-151 对同一从库通道记录成功/失败）；`setLastErrorMessage`（:149）在 `recordFailure`（:150）**之外**单独调用，两步之间可插入其他线程的 `recordSuccess`。
- **触发时序**: T1 从库 X 获取连接失败 → :149 `setLastErrorMessage(err)` → T2 并发对 X 获取成功 → `recordSuccess()`（synchronized）置 `lastErrorMessage=null`、清零冷却 → T1 恢复执行 `recordFailure`（synchronized）计数 +1（未达阈值）→ 终态：通道 healthy 但 `describe()`（:83-96）仍可能在 T1 写入顺序颠倒时展示陈旧 lastError——观测口径与真实状态矛盾（纯可观测性缺陷，计数与冷却判定因 synchronized 互斥保持正确）。
- **现状**: 三字段中仅 lastErrorMessage 存在锁外写路径；读侧均 volatile 可见，无撕裂。
- **风险**: 故障摘要（`noReplicaAvailable` 的 ARG_SUMMARY）可能误导排障；无正确性后果。
- **建议**: `setLastErrorMessage` 并入 `recordFailure`（签名加 message 参数），全部可变状态收敛到 synchronized 状态机内；或 setter 加 synchronized。
- **失效面枚举（F3）**: 调用点全查：`setLastErrorMessage` 生产调用唯一（ReadWriteRoutingDataSource.java:149）；状态机方法全查：`recordSuccess`（:53，synchronized，正确）、`recordFailure`（:62，synchronized，正确）、`isCooling/isHalfOpen/describe`（volatile 读，正确）。兄弟通道状态：`ReadWriteRoutingDataSource` 自身字段均为启动期只读（正确）。
- **信心水平**: 确定（交错可具体化，后果限观测）
- **误报排除**: 不报「volatile 字段无锁访问」本身——volatile 读写在 JMM 下合法；立案点是**同一状态机两套访问纪律不一致**导致的顺序矛盾。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

## 维度集外线索（批次 4）

### [nop-persistence-rw-CL-300]（维度集外线索，不立案展开）JdbcBatcher 成功回调异常路径残留 commands 队列且 this.sql 被置 null：实例复用时旧命令混入异语义新批次（数据错乱形态）

- **位置**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:148-277,136-146`
- **形态**: F2/F9（异常路径状态不一致 + 回调重入），单线程状态机缺陷，非跨线程 C1——生产构造点（JdbcInsertBatchConsumer/JdbcUpdateBatchConsumer/JdbcEntityPersistDriver/EncAssistedBackfiller）均为每操作新实例、线程封闭。
- **线索描述**: flush() 成功路径在 :186-189 **回调遍历期间**（commands.clear() 在 :191）若回调抛 RuntimeException，异常穿透（内层 try/catch 仅覆盖 BatchUpdateException 分支 :220-241），`commands.clear()` 被跳过而 finally（:267-276）仍置 `this.sql = null` → 复用实例的下一次 `addCommand`（:137）因 `this.sql == null` 跳过 flush，**已执行且已回调的旧命令与新 SQL 命令混入同一 deque**；下次 flush 以 `commands.getFirst().sql`（:154）为PreparedStatement 文本，旧参数被塞入语义不同语句（:243-245 注释自证此形态为数据错乱）。另有重入形态：回调中同步调 `addCommand` 的命令在 :191 被 `commands.clear()` 静默丢弃（回调恰已收到，上层无感知）。
- **移交建议**: C6/C2 轮或修复轮处理：成功回调改「先快照 clear 再回调」；flush 失败路径 finally 中 `commands.clear()` + failRemainingCommands 兜底对齐。
- **登记状态**: 维度集外线索，不计入 C1 发现计数。

### [nop-persistence-rw-CL-301]（维度集外线索，不立案展开）DaoMetricsImpl 以动态 SQL 全文为 statManager key：SQL 文本基数不受控时 JdbcSqlStat 注册表单调增长（C7 账本形态）

- **位置**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/metrics/DaoMetricsImpl.java:100,127,153`
- **形态**: C7（指标标签/账本基数爆炸），非 C1。
- **线索描述**: `statManager.getJdbcSqlStat(sql.getText())` 以**完整 SQL 文本**为键；marker 绑定参数的 SQL 文本稳定（有限形状），但任何把值内联进文本的路径（如 exists 模板渲染 :599-617 已限表名/列名集合，风险低；自定义 ad-hoc SQL 若内联字面量）都会逐文本驻留一个 JdbcSqlStat；注册表归属平台 `GlobalStatManager`（nop-core），其保留/清理策略不在本模块审计面。
- **移交建议**: C7 维度核实 GlobalStatManager 的条目上限/TTL；若缺失，nop-dao 侧缓解为对 stat 键做形状归一化（模板名/指纹替代全文）。
- **登记状态**: 维度集外线索，不计入 C1 发现计数。

### [nop-persistence-rw-CL-302]（维度集外线索，不立案展开）JdbcDialectProvider 并发首查方言嗅探击穿：N 并发首查各自开 SUPPORTS 事务 + 各占一条池连接做元数据探测（C2/C9 形态）

- **位置**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcDialectProvider.java:42-52`
- **形态**: C2（check-then-act 并发击穿）/C9（热路径浪费），非 C1 正确性问题——并发结果收敛（各方 put 相同值），无状态损坏。
- **线索描述**: `get(querySpace)==null` → `getDialectFromTxn`（**开启 SUPPORTS 事务 + 从池取连接 + 读 DatabaseMetaData**）→ put 记忆化。并发首查 N 线程 → N 次事务+连接嗅探（结果幂等）；querySpace 键空间由配置/分片模式（ds_{shard}）决定、有限，无容量问题。
- **移交建议**: 改 `querySpaceToDialectMap.computeIfAbsent(...)` 需评估映射函数内开事务是否持 bin 锁跨查询串行（对齐批次 2 CL-100 口径）；更优为启动期预热（ReadWriteDataSourceInitializer 先例）。
- **登记状态**: 维度集外线索，不计入 C1 发现计数。

### 批次 4 小结

- **发现清单**：C1-023（P1）、C1-024（P2）、C1-025（P2）、C1-026（P1）、C1-027（P2）、C1-028（P3）、C1-029（P3）、C1-030（P3）。
- **CL 线索**：CL-300（维度集外，F2/F9 形态）、CL-301（维度集外，C7 形态）、CL-302（维度集外，C2/C9 形态）。
- **总计**：8 条发现（0×P0、2×P1、3×P2、3×P3）+ 3 条维度集外线索。
- **末位发现编号**：C1-030；末位 CL 编号：CL-302。
- **总体判定**：nop-dao 的 C1 风险重心在**「事务对象设计上跨线程可达，但状态族按线程封闭假设编写」**——C1-023/025/026 三条同根：`IContext` 异步续段把同一上下文（及其注册的事务/注册表）带到 executor 线程，而 `TransactionRegistry` 创建、`AbstractTransaction` 状态族、`JdbcTransaction` 连接惰性获取全部无同步/无原子守卫。静态状态纪律整体好于 nop-orm：static 槽位仅 `DaoProvider._instance` 一处不安全发布（C1-024），`DialectManager`/`predefined_errors` 均以类初始化安全发布；`DbEstimatedClock`（volatile+sync+复核）、`SnowflakeSequenceGenerator`（方法级 synchronized）、`ReadWriteHint/QuerySpaceEnv`（enter/leave 成对恢复）为正确实现范本。三条 P3 均为防御性收紧（惰性初始化 volatile 化、static 槽 final 化、状态机锁纪律归一），修复轮可与 P1/P2 切片合并执行。读写分离新面（ReadWriteRoutingDataSource/ReplicaChannel/负载均衡）除 C1-030 观测瑕疵外线程纪律良好；分片（shard）与 upsert 面未发现 C1 缺陷。

## 批次汇总（4 批次全量）

| 批次 | 范围 | 发现数 | P0 | P1 | P2 | P3 | CL 线索 | 末位编号 |
|------|------|--------|----|----|----|----|---------|----------|
| 1/4 | nop-orm + nop-orm-data | 11 | 0 | 2 | 6 | 3 | 2（CL-001/002） | C1-011 / CL-002 |
| 2/4 | nop-orm-eql | 3 | 0 | 0 | 2 | 1 | 1（CL-100） | C1-014 / CL-100 |
| 3/4 | nop-orm-model | 8 | 0 | 1 | 5 | 2 | 1（CL-200） | C1-022 / CL-200 |
| 4/4 | nop-dao | 8 | 0 | 2 | 3 | 3 | 3（CL-300/301/302） | C1-030 / CL-302 |
| **合计** | 5 源码根 | **30** | **0** | **5** | **16** | **9** | **7** | — |

- **全量末位发现编号**: `nop-persistence-rw-C1-030`；全量末位维度集外线索编号: `nop-persistence-rw-CL-302`。
- **P1 清单（跨批次）**: C1-001（OrmSessionRegistry 会话槽竞态）、C1-003（ormModel 非 volatile + 非同步写）、C1-015（OrmComponentModel 双字段惰性初始化）、C1-023（TransactionRegistry check-then-act）、C1-026（JdbcTransaction 双连接获取/泄漏）。
- **跨批次同族收敛点（供合并裁定）**: (1) 「IContext 属性 get-or-create 竞态」——C1-001（OrmSessionRegistry）与 C1-023（TransactionRegistry），建议平台级原子 get-or-create 工具统一修复；(2) 「静态单例槽发布」——C1-006（OrmTimestampHelper）与 C1-024（DaoProvider），volatile 化统一收敛；(3) 「引擎级缓存 invalidation-vs-repopulate」——C1-008（查询计划缓存，重载不清）与 C1-027（查询结果缓存，回填竞态），失效语义统一裁定。

## 维度复核结论（独立复核合并批，2026-10-18）

- **复核方式**: 与初审/深挖代理独立的第 2 轮合并复核批。本维度 30 条发现的证据逐条打开 file:line 亲验；P1×5 逐条子项复核（证据/时序/判级/F3 四要素）；历史对照面已解除盲审（cra-index WI 1.1–2.3、plan 2468–2470）。round2 三项穷举（IContext get-or-create 站点、先发布后填充家族、静态单例槽）经独立 grep 复核**确认闭合**（5 根内 setAttribute 仅 OrmSessionRegistry/TransactionRegistry 2 处；静态槽 C1-006/009/024/029 全集确认）。
- **裁定统计**: 30 条全部**保留**（降级 0、驳回 0）——P1×5 维持 / P2×16 维持 / P3×9 维持。
- **P1 终裁**: C1-001/003/015/023/026 全部维持。四条均有行级亲验证据与具体触发时序；C1-003 触发链（公开 reloadModel API + 每请求热路径读）与 C1-015 触发链（纯逻辑交错、无需硬件假设）判级复核通过。
- **同族合并终裁**（对初审跨批次收敛点 3 组的终裁）:
  1. **C1-001+C1-023（IContext get-or-create 竞态）——合并裁定确认**。两者逐字节同构亲验成立；round2 穷举确认 5 根内仅此 2 站点；终裁为同一缺陷、单一平台级修复项（ContextAttrs.getOrCreate 原子工具），台账双锚点保留、remediation 计数去重。
  2. **静态单例槽族（C1-006+C1-024，邻接 C1-009+C1-029）——同族相邻独立工单、归并一个「静态槽收紧」切片**：裁定确认（同形态、独立后果、不合并台账）。
  3. **缓存 invalidation-vs-repopulate（C1-008 与 C1-027）**：C1-008(c)+C2-008 同位点双登记合并**确认**（reloadModel 未触达 queryPlanCache 亲验）；C1-027 与 C2-004 为同族相邻（不同缓存载体）——共享「失效世代号」修复设计、两个 work item 不合并执行，**确认**。
- **CL 线索处置（批量裁定，7 条全部确认登记）**: CL-001（监听器膨胀，已由 C2-010/C5-004/C9-001 承接量化）、CL-002（flush 重入静默跳过语义，C2-001 已覆盖并发侧）、CL-100（SqlExprMetaCache bin 锁，C2-012 完整化成立）、CL-200（LazyLoadOrmModel 锁内装载，C2-018 立案成立）、CL-300/301/302（JdbcBatcher 异常路径残留 / DaoMetricsImpl SQL 全文键 / JdbcDialectProvider 并发嗅探）均确认登记，无驳回。
- **勘误/说明**: (1) 本维度台账 30 条（P1×5/P2×16/P3×9）与文件内条目逐一对应，无编号空缺；(2) C1-010 与 WI 2.3 单元 C1-010（NopAuthSession 无 version 列）同号异单元，互不混淆——WI 2.3 该条确认 NopAuthSession ORM 实体无 version/optimistic-lock 列，与本单元 C1-001 所注「乐观锁仅部分兜底」的丢更新后果互补（无 version 列实体上丢失更新无任何 DB 层兜底），两单元修复轮应互通该前提。

## 最终保留项

| ID | 初判 | 终裁 | 条目 |
|---|---|---|---|
| C1-001 | P1 | **P1**（与 C1-023 合并修复） | OrmSessionRegistry 会话槽 check-then-act |
| C1-002 | P2 | P2 | session 写路径无 checkContext + flush 非原子守卫族 |
| C1-003 | P1 | **P1** | ormModel 非 volatile + 每请求写 interceptor + clearCache 混合态 |
| C1-004 | P2 | P2 | LoadedOrmModel incRef/close TOCTOU |
| C1-005 | P2 | P2 | interceptors/daoListeners 运行期可变面无同步 |
| C1-006 | P2 | P2（静态槽切片） | OrmTimestampHelper 非 volatile |
| C1-007 | P2 | P2 | OrmBatchLoadQueueImpl 半 volatile |
| C1-008 | P2 | P2（(c) 与 C2-008 合并修复） | 查询计划缓存跨代陈旧 + 双编译良性 |
| C1-009 | P3 | P3 | specifyQuerySpaces 实例写静态 |
| C1-010 | P3 | P3 | 全局缓存值对象共享引用无防御拷贝 |
| C1-011 | P3 | P3 | DaoEntityResource 非 volatile 检查 |
| C1-012 | P2 | P2 | EqlParseTreeParser 有状态单例暴露 |
| C1-013 | P2 | P2 | CompiledSql 全可变 POJO |
| C1-014 | P3 | P3 | getFieldExprMetas 活 Map 逸出 |
| C1-015 | P1 | **P1** | OrmComponentModel 先发布后填充 |
| C1-016 | P2 | P2（升 P1 条件标注） | 运行期 topo 重建写冻结模型 |
| C1-017 | P2 | P2 | 冻结守卫族 20 处缺口 |
| C1-018 | P2 | P2 | putEntityModel 失败路径半注册 |
| C1-019 | P2 | P2 | init/发布契约无守卫 |
| C1-020 | P3 | P3 | isJoinOnNonPkColumn 懒写（值良性） |
| C1-021 | P2 | P2（升 P1 条件标注） | anyEntityUseTenant 过期快照 |
| C1-022 | P3 | P3 | getValidators/getEntityNames 活引用逸出 |
| C1-023 | P1 | **P1**（与 C1-001 合并修复） | TransactionRegistry check-then-act |
| C1-024 | P2 | P2（静态槽切片） | DaoProvider 非 volatile 单例槽 |
| C1-025 | P2 | P2 | AbstractTransaction 状态族可见性 |
| C1-026 | P1 | **P1** | JdbcTransaction 双取连接泄漏 |
| C1-027 | P2 | P2 | JDBC 查询缓存回填 vs 失效竞态 |
| C1-028 | P3 | P3（与 C2-020 合并修复） | 方言映射记忆化惰性 init |
| C1-029 | P3 | P3（静态槽切片） | FIND_FIRST_RANGE 非 final |
| C1-030 | P3 | P3 | ReplicaChannel 锁纪律不齐 |

**维度终局**: P1×5 / P2×16 / P3×9，无驳回、无撤案。
