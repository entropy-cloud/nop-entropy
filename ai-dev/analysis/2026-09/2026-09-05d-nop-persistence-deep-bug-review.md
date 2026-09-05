# nop-persistence 模块深度缺陷审查

> Status: resolved
> Date: 2026-09-05
> Scope: `nop-persistence/` 全部 code-bearing 模块：nop-orm、nop-orm-eql、nop-dao、nop-orm-model、nop-db-migration、nop-nosql（core+lettuce）、nop-dbtool-core、nop-orm-rpc、nop-orm-tdengine、nop-orm-data、nop-cdc-core、nop-orm-geo、nop-orm-pdm
> Conclusion: 主通道精读 + 两轮独立子 agent 串行复核达成共识，确认 22 项真实缺陷（P1 级 3 项、P2 级 6 项、P3 级 13 项）。修复处置（2026-09-05/06，红测试先行）：20 项已修复（其中 18 项附新增回归测试，DAO-04 日志降级与 MISC-02 目录清理无独立测试），SQL-03 经复核证伪回退，EQL-01 已修复（含 EQL 编译器 prop-join 参数收集扩展）。全量 nop-persistence 聚合构建测试通过。

## Context

- 目标：找出会在真实场景产生错误行为的缺陷，而非风格问题。此前 `ai-dev/audits/` 覆盖过 nop-code/nop-stream/nop-job/nop-metadata/nop-ai/nop-auth，**从未深查过 nop-persistence**（仅 nop-metadata 审计间接触及 orm 模型层）。
- 审查方法：按"公共契约 → 状态机 → 资源管理 → 并发 → SQL 生成正确性"五类线索逐文件核对；每项疑似缺陷均在源码中亲自复核（本报告的"已复核推翻"小节记录了 4 项被主通道自己推翻的疑似项）。
- 复核方法：两轮独立子 agent 串行对抗复核（一次一个，不并行）。第 1 轮逐项验证 + 补查浅层区，全部 CONFIRM 并新增 3 项；第 2 轮验证新增项 + 复核修订表述 + 补查剩余区域，新增 1 项、修正 2 处表述后达成共识。
- 模块规模：13 个 code-bearing 模块约 757 个主代码 Java 文件，核心是 nop-orm(152)/nop-orm-eql(221)/nop-dao(116)/nop-db-migration(103)/nop-orm-model(91)。

## 缺陷总览

| 级别 | 数量 | 说明 |
|------|------|------|
| P1（特定场景错误行为/数据丢失/机制失效） | 3 | flush 期间修改丢失、修订实体 update/delete 关闭记录失效、findLatest SQL 恒不匹配 |
| P2（真实缺陷，影响正确性或性能，窗口较窄） | 6 | EQL prop-path 关联缺租户过滤、集合缓存更新不汇入 future、批量加载冗余查询、批处理回调计数 NPE、flushImmediately dirty 回滚等 |
| P3（边界/契约/卫生问题） | 13 | 死代码、日志噪声、边缘 NPE、几何字面量 SRID 拼接错误、阻塞源抢占竞争、驱动返回值契约偏离等 |

> 修订记录：第 1 轮独立复核（2026-09-05）对全部 18 项裁定 CONFIRM、无推翻；按复核意见精化 ORM-01/02/03、DAO-04、SQL-02 的表述，并新增 EQL-01、GEO-01、TD-01 三项。第 2 轮独立复核确认三项新增全部成立，修正 EQL-01 的对照范围（所有 prop-path join 均缺租户过滤，缺陷面扩大）、补充 GEO-01 影响方言面，新增 DATA-01；两轮共同确认 P1 三项证据链完整，**达成共识：本报告为共识版本**。

---

## A. nop-orm（会话/持久化/加载）

### ORM-01 [P1] flush 过程中对已托管实体的修改会被静默丢弃

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:577-585`、`session/CascadeFlusher.java:41-43,89-103`、`OrmSessionImpl.java:205-224`
- **证据片段**:
  ```java
  // OrmSessionImpl.internalMarkDirty —— flusher 注册调用被注释掉
  public void internalMarkDirty(IOrmEntity entity) {
      if (entity.orm_enhancer() != this) return;
      markDirty();
      cache.markDirty(entity.orm_entityName());
      // if (flusher != null)
      //     flusher.addChangeDuringFlush(entity);
  }
  ```
  ```java
  // OrmSessionImpl.flush —— execute() 返回后无条件清除 dirty
  this.flusher = new CascadeFlusher(this, cache);
  this.flusher.execute();
  this.dirty = false;
  this.cache.clearDirty();
  ```
- **严重程度**: P1
- **现状**: `CascadeFlusher` 通过 `changedDuringFlush` 列表重放 flush 期间产生的变化，但只有 `internalSave`（新增实体）和 `internalDelete` 会注册；对**已 MANAGED 实体**的修改（`internalMarkDirty` 路径，以及 `internalUpdate` 中同样被注释的注册，OrmSessionImpl.java:570-573）不会注册。若拦截器（`preUpdate`/`preSave`/`postLoad` 回调或 XplOrmInterceptor 脚本）在实体 A 的 flush 过程中修改了**已被 forEachDirty 循环访问过**的实体 B：B 的**实体级**脏标记（oldValues）仍在，但 flush 结束时 `session.dirty=false` + `cache.clearDirty()`（cache 级 dirty）被清除 → 常规"事务提交时仅 flush 一次"流程下 B 的修改不会被发送 SQL；只有当后续其他修改再次把该实体类型的 cache 重新标脏时 B 才有补写机会。是否丢失取决于 forEachDirty 的遍历顺序（LinkedHashMap 插入序），同一代码在不同插入顺序下结果不同。（若 B 尚未被循环访问，则第二循环中的 `internalFlush(B)` 会正常补写，不丢。）
- **风险**: 数据静默丢失且无任何日志；回调代码"有时生效有时不生效"的幽灵问题。
- **建议**: 恢复 `internalMarkDirty` 中被注释的 `flusher.addChangeDuringFlush(entity)` 注册（与 internalSave/internalDelete 对齐），或 flush 结束时不无条件清除 session.dirty 而是重新校验。
- **信心水平**: 确定（代码路径完整可循；触发前提是 flush 回调中修改其他已托管实体）。

### ORM-02 [P1] 修订实体（useRevision）update/delete 不关闭旧修订记录，且清空用户修改的脏标记

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/OrmRevisionHelper.java:46-66,92-126`
- **证据片段**:
  ```java
  // newRevEntity 末尾 —— clearDirty 之后用 orm_internalSet（不标脏）
  entity.orm_clearDirty();
  int endVerPropId = entityModel.getNopRevEndVarPropId();
  if (endVerPropId > 0) {
      entity.orm_internalSet(endVerPropId, ver);          // 不产生 dirty
      revEntity.orm_internalSet(endVerPropId, OrmConstants.NOP_VER_MAX_VALUE);
  }
  // onRevUpdate 随后：
  persister.queueUpdate(entity, session);   // entity.orm_dirtyPropIds() == 空
  ```
  已验证生成的 `orm_internalSet` 实现（`nop-orm/src/test/java/test/entity/_gen/_TestCompositeTable.java`）只做 `onInitProp` + 赋值，**不调用 markPropDirty**。
- **严重程度**: P1
- **现状**: `onRevUpdate`/`onRevDelete` 依赖 `queueUpdate(entity)` 生成"关闭旧修订"的 UPDATE，但 `newRevEntity` 先 `orm_clearDirty()` 抹掉了用户修改的脏标记、再用不标脏的 `orm_internalSet` 设置 revEnd → 批执行时 `orm_dirtyPropIds()` 为空，`buildUpdateSql` 返回 null（`JdbcEntityPersistDriver.buildUpdateSql:271-283`）。分两种情形：(a) 实体配置了自动更新时间戳时，`queueUpdate` 中的 `OrmTimestampHelper.onUpdate` 会用 `orm_propValue` 标脏，UPDATE 只更新时间戳字段，仍不含 revEnd 与用户字段；(b) **无时间戳属性时 UPDATE 动作在 `batchExecuteCommand` 中被整体跳过，连成功回调链（checkUpdateResult/incOptimisticLockVersion/persisterPostUpdate）都不执行**。
- **风险**: 旧修订记录 revEnd 永远保持 MAX_VALUE，同一实体出现两条"当前版本"记录；若 PK 未含 beginVer，则新修订 INSERT 直接主键冲突；修订历史整体损坏。对比 `onRevSave` 路径用的是 `oldEntity.orm_propValue(endVerPropId, ver)`（`orm_propValue` 会标脏）→ save 路径正常，update/delete 路径失效，两路径不对称佐证这是缺陷而非设计。
- **建议**: `newRevEntity` 中改用 `entity.orm_propValue(endVerPropId, ver)`（标脏），且不要在 queueUpdate 之前 `orm_clearDirty()` 抹掉用户修改。
- **信心水平**: 确定（静态链路完整）。现有测试只覆盖 SQL 文本生成（`TestGenSqlHelper.testRevisionFilter`），无持久化流测试。

### ORM-03 [P1] genFindLatestSql 的 WHERE 中 revEnd 条件重复追加，findLatest 恒返回空

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java:206-242`
- **证据片段**:
  ```java
  IColumnModel revCol = entityModel.getColumnByPropId(entityModel.getNopRevEndVarPropId(), false);
  appendCol(sb, dialect, null, revCol);
  sb.append('=').append(OrmConstants.NOP_VER_MAX_VALUE);      // rev_end = MAX（字面量）
  ...
  for (IColumnModel col : entityModel.getColumns()) {
      if (col.getPropId() != entityModel.getNopRevBeginVerPropId()) {  // 只排除 beginVer
          sb.and();
          params.add(col.getPropId());
          appendEqMarker(sb, dialect, null, col, binders[col.getPropId()]);  // rev_end = ?（再次）
      }
  }
  ```
- **严重程度**: P1（修订机制在 SQL 层即失效）
- **现状**: 列循环只排除了 `nopRevBeginVer`，未排除 `nopRevEndVer` → 生成 `WHERE rev_end = MAX AND col1 = ? ... AND rev_end = ? AND tenant = ?`。已复核确认：`OrmEntityModelInitializer.addInternalProps` 对 useRevision 实体通过 `addColumn` 添加 revType/revBeginVer/revEndVer（OrmEntityModelInitializer.java:393-403,449-465），三者**必然出现在 `getColumns()`**。`onRevSave` 调用 findLatest 时实体是 transient 新对象，revEnd 未设置 → 绑定 null → `rev_end = NULL` 恒不匹配。此外列循环还包含 `nopRevType`，而 findLatest 在 `orm_internalSet(REV_TYPE_SAVE)` **之前**调用（OrmRevisionHelper.java:25,30）→ revType 绑定 0 与库中 1 也不等，构成第二重失效。最终 `findLatest` 永远返回 null → 旧记录永不关闭、`ERR_ORM_ENTITY_ALREADY_EXISTS` 检测失效。与 ORM-02 独立地使修订机制两端都失效。
- **风险**: 同 ORM-02；且 `onRevSave` 的"已删除实体不可复活"校验失效。
- **建议**: 循环中同时排除 `getNopRevEndVarPropId()`（以及租户列已单独处理可继续保留现状）。
- **信心水平**: 确定（第 1 轮独立复核已核实 revEnd 列必然进入 getColumns()，且发现 nopRevType 第二重失效证据；全仓无 genFindLatestSql 测试锁定该形状）。

### ORM-04 [P2] CollectionPersisterImpl 批量加载后全局缓存更新不汇入返回的 future

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/CollectionPersisterImpl.java:141-156`
- **证据片段**:
  ```java
  CompletionStage<?> future = driver.batchLoadCollectionAsync(shard, colls, propIds, selection, session);
  FutureHelper.collectWaiting(future, futures);
  future.thenRun(() -> {           // 返回值被丢弃
      for (IOrmEntitySet coll : colls) {
          if (useGlobalCache) updateGlobalCache(coll, session);
      }
  });
  ```
- **严重程度**: P2
- **现状**: `thenRun` 结果未加入 futures，调用方 `syncGet(waitAll(futures))` 返回时缓存写入可能尚未完成。同类问题在实体路径已被修复并留有注释：`EntityPersisterImpl.batchLoadAsync:174-182`（"全局缓存的更新也纳入返回的future链，保证调用方syncGet时缓存已完成更新"），集合路径漏改。
- **风险**: `batchLoadCollectionAsync` 返回后立即读全局缓存可能读到旧值/空值；跨线程/异步场景下出现不一致。
- **建议**: 与实体路径对齐，把缓存更新链接进返回的 future。
- **信心水平**: 确定。

### ORM-05 [P2] 批量加载队列的"已加载实体去重"失效，集合预取的实体被冗余二次查询

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/OrmBatchLoadQueueImpl.java:323-333,347-356,644-665`
- **证据片段**:
  ```java
  EntityLoad _enqueueEntity(IOrmEntity entity) {
      if (!entity.orm_proxy()) return null;
      ...
      EntityLoad load = makeEntityLoad(entityName, true);   // eager=true → entityPropLoadMap
  }
  ...
  // _flushCollection：只清理 entityLoadMap，且条件对 lazy-load 队列恒无效果
  EntityLoad entityLoad = queue.entityLoadMap.get(load.entityModel.getName());
  if (entityLoad != null) {
      entityLoad.entities.removeIf(entity -> !entity.orm_proxy());
  }
  ```
- **严重程度**: P2（性能/冗余 SQL）
- **现状**: proxy 实体登记在 `entityPropLoadMap`（`makeEntityLoad(eager=true)` 分支），而 `_flushCollection` 加载完集合元素后只从 `entityLoadMap` 中移除已非 proxy 的实体——`entityLoadMap` 里按当前代码只会存放"已加载实体的延迟属性"加载项（实体本身非 proxy），`removeIf(!proxy)` 永远不会命中 → 清理逻辑完全失效。集合加载已装配过的实体若同时被直接 enqueue，会被再次 batch load（驱动重新执行 SQL，`internalAssemble` 因已 inited 而丢弃结果）。
- **风险**: 每次集合+实体混合加载场景多一轮 SQL；清理代码名存实亡，后续维护者会误以为去重生效。
- **建议**: 清理应针对 `entityPropLoadMap`（或统一两个 map 的语义），并补充回归测试断言"集合预取后不再触发二次加载"。
- **信心水平**: 确定（map 归属与命名/注释互相矛盾，行为以代码为准）。

### ORM-06 [P2] flushImmediately 恢复 dirty 标志，级联期间的新脏标记被回滚清除

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:997-1012`
- **证据片段**:
  ```java
  boolean oldDirty = this.dirty;
  try {
      flusher.execute(entity);
      this.batchActionQueue.flush();
  } finally {
      if (createExecutor) this.flusher = null;
      this.dirty = oldDirty;      // 级联中 internalMarkDirty 置位的 dirty 被还原
  }
  ```
- **严重程度**: P2
- **现状**: `flushImmediately`（仅 stateless session 的 save/update/delete 调用）期间级联保存/删除的其他实体通过 `internalMarkDirty`/`internalSave` 置位 `session.dirty=true`，finally 无条件还原为进入前值。stateless 模式下每个操作都会立即 flush，丢失窗口比有状态 session 的 `flush()` 路径小，但 `flusher.execute(entity)` 之后、`batchActionQueue.flush()` 之前新标记的实体（例如 postSave 回调中修改的关联实体）不会由任何后续 flush 兜底。
- **风险**: stateless session（批量导入等场景）下偶发漏写。
- **建议**: 还原 dirty 前先检查 flusher 执行期间是否产生了新的脏标记；或显式记录并处理。
- **信心水平**: 很可能（窗口窄、依赖回调行为）。

### ORM-07 [P3] OrmEntity.markPropDirty 使用 Objects.equals，byte[]/BigDecimal 语义失真

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/OrmEntity.java`（markPropDirty）
- **严重程度**: P3
- **现状**: `byte[]` 属性走 `Object` 引用相等 → 每次赋值都判为"已修改"，实体恒脏、每轮 flush 都发 UPDATE；`BigDecimal`（1.0 vs 1.00 equals 为 false）产生虚假更新。
- **建议**: 对字节数组用 `Arrays.equals`，或模型层禁止裸 byte[] 脏比较。
- **信心水平**: 确定（语义事实）；实际影响取决于模型是否使用这两类字段。

### ORM-08 [P3] OrmEntitySet.IteratorView 误用 remove() 会把 null 塞进 removedEntities

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/OrmEntitySet.java:718-739,566-573`
- **严重程度**: P3
- **现状**: 未调用 `next()` 就调用 `iterator().remove()` 时，`current==null`，`doRemove(null)` 先把 null 加入 `removedEntities`，随后 `it.remove()` 抛 IllegalStateException；null 残留在 removedEntities 中，flush 时 `CascadeFlusher.cascadeCollection → orm_removed()` 遍历对 null 调 `orm_state()` 抛 NPE。
- **建议**: `IteratorView.remove()` 先校验 `current != null`。
- **信心水平**: 确定（触发前提是调用方违反 Iterator 契约）。

## B. nop-orm-rpc

### RPC-01 [P2] batchExecuteAsync 无动作时返回 null、lock() 恒返回 false

- **文件**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:293-315,283-285`
- **严重程度**: P2（契约）/P3（lock）
- **现状**: `CompletionStage<Void>` 方法在 save/update 或 delete 为空时 `return null` 而非 `FutureHelper.voidPromise()`。当前调用方 `BatchActionQueueImpl.flushAsync → FutureHelper.collectWaiting` 对 null 容忍（已复核 `FutureHelper.java:422-426`），暂不崩溃，但这是对接口契约的偏离，任何新的调用方按非 null 假设写代码即 NPE。`lock()` 恒 false 使 `session.lock()` 对 RPC 实体必然抛 `ERR_ORM_LOCK_ENTITY_FAIL`——若属"不支持"应由专门错误码表达。
- **建议**: 返回 voidPromise；lock 明确语义。
- **信心水平**: 确定。

## C. nop-dao（JDBC/事务）

### DAO-01 [P2] JdbcBatcher 批处理部分成功回调的计数语义错误（null 拆箱 NPE / -2 误报多行）

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:206-224,312-318`、`nop-orm/.../EntityPersisterImpl.java:505-521`
- **证据片段**:
  ```java
  } else if (ret[i] == Statement.SUCCESS_NO_INFO) {
      params.onComplete(null, null);        // 计数传 null
  }
  ...
  // EntityPersisterImpl.checkUpdateResult
  protected void checkUpdateResult(int count, IOrmEntity entity) {   // null 自动拆箱 → NPE
  ```
- **严重程度**: P2
- **现状**: `BatchUpdateException` 处理循环中 `SUCCESS_NO_INFO` 命令回调收到 `null` 计数；`EntityUpdateAction` 回调 `checkUpdateResult(ret, entity)` 拆箱 NPE。正常成功路径若驱动返回 -2 且 dialect 配置 `isSupportBatchUpdateCount()==true`，`checkUpdateResult(-2)` 因 `count > 1` 误抛 `ERR_ORM_UPDATE_ENTITY_MULTIPLE_ROWS`（`onSuccess` 的负数归一化只在 `!checkSingleChange` 时生效）。
- **风险**: 驱动返回 SUCCESS_NO_INFO（Oracle/某些连接池包装场景常见）时批处理失败路径二次崩溃或误报。
- **建议**: `onComplete(null,null)` 改传 -2 并在 `onSuccess`/`checkUpdateResult` 统一归一化负值为 1。
- **信心水平**: 确定（代码路径）；触发依赖驱动行为。

### DAO-02 [P3] 事务 commit 失败路径不触发 onAfterCompletion

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:266-340`
- **严重程度**: P3
- **现状**: `commit()` 中 `doCommit()` 抛异常时直接包装抛出，`afterCompletion(UNKNOWN)` 不调用；`commitAsync()` 的 exceptionally 分支同样不通知。后果：(a) `transactionMetrics.onTransactionFailure` 少计；(b) 监听器缺少终态通知（对缓存逐出监听器而言"不通知"恰好安全，因为数据未提交，但对其他监听器是状态泄漏）。事务对象留在 opened 状态，依赖 `TransactionTemplate.cleanupTransaction` 关闭。
- **建议**: commit 失败路径补 `afterCompletion(UNKNOWN)`。
- **信心水平**: 确定（行为事实）；影响以监控/监听器为主。

### DAO-03 [P3] JdbcTransaction 提交后复用连接时 autoCommit 仍为 false，后续写操作不提交

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/txn/JdbcTransaction.java:60-105`
- **严重程度**: P3
- **现状**: `eagerReleaseConnection=false` 时 commit/rollback 后连接保留且 `autoCommit=false`；同一事务对象此后再被 `getConnection()` 取用（例如注册表残留引用）时，写操作运行在无提交的隐式事务中，连接关闭即丢失。
- **建议**: `doCommit/doRollback` 后将连接恢复 `autoCommit=true`（或直接 release）。
- **信心水平**: 很可能（需要"提交后复用"这一非常规使用路径）。

### DAO-04 [P3] 每条 SQL 以 LOG.info 全文输出

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:190,198,293`、`jdbc/impl/JdbcTemplateImpl.java:173`
- **严重程度**: P3
- **现状**: 默认 info 级别记录每条执行过的 SQL 全文与耗时（`runWithConnection`、批处理成功/失败、逐语句执行），生产环境日志量巨大且 SQL 文本可能带敏感字面量。（`executeUpdate`/`executeStatement` 处仅记录 sql.getName()，不算全文。）
- **建议**: 降为 debug 或采样。
- **信心水平**: 确定。

## D. nop-orm SQL 生成（GenSqlHelper）

### SQL-01 [P3] example 查询的租户条件直接绑定 currentTenantId，无租户上下文时静默 0 行

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java:790-809`（appendExampleFilter）
- **严重程度**: P3（行为语义问题）
- **现状**: `findByExample/countByExample/updateByExample/deleteByExample` 的租户过滤用 `ContextProvider.currentTenantId()` 字面量绑定；后台任务/系统上下文无租户时生成 `tenant = NULL` → 静默 0 行，无错误提示。与 `genLoadSql` 路径（按实体属性值绑定）语义不一致。
- **建议**: 无租户上下文时显式报错或按实体租户值绑定，避免静默空结果。
- **信心水平**: 确定（代码事实）；是否算缺陷取决于多租户语义约定，报告供裁定。

### SQL-02 [P3] genUpdateByExample 空 SET 边界会咬坏 SQL 文本

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java:757-788`
- **严重程度**: P3
- **现状**: `updated` 的可更新字段全部不可写且实体无乐观锁版本字段时，SET 段为空，`sb.deleteTail(1)` 咬掉 ` set ` 的尾空格，生成 `update T set where ...` 非法 SQL。上游 `updateByExample` 仅校验 `updated.orm_inited()`（OrmSessionImpl.java:681），挡不住"有 inited 属性但全部不可更新"的组合。
- **建议**: 记录 SET 段起点，空则显式报错。
- **信心水平**: 很可能（边界组合罕见）。

### SQL-03 [P3] orderBy 列名非法时 NPE 而非可读错误

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java:547-563,811-828`
- **严重程度**: P3
- **现状**: `entityModel.getColumn(orderField.getName(), false)` 对非列属性（关联名/拼写错误）返回 null，`col.getCode()`/`appendCol` 直接 NPE。orderBy 来自 API 入参时可被外部触发。
- **建议**: null 时抛 `ERR_ORM_UNKNOWN_COLUMN_NAME` 类错误码。
- **信心水平**: 确定。

## E. 其他模块

### EQL-01 [P2] EQL prop-path 关联（隐式与显式写法均含）的 ON 条件不追加租户条件与实体固定过滤器

- **文件**: `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java:811-821`（`addTableFilterForPropJoinTable` 只把**逻辑删除**过滤加进 join ON 条件）
- **证据对照**: `TenantParamBuilder` 全编译器唯一使用点是 `newTenantExpr`（:347）← `collectDefaultEntityFilter`（:318-337）；后者仅被 `addEntityFilter`（:375-387，主表进 WHERE）与 `addTableFilterForJoinTable`（:521-528，**实体名** join 的 ON）调用。执行时机：`visitSqlQuerySelect` 中 `visitSqlFrom`（:243）后立即 `addTableFilter`（:245），而投影/where/order by 中的隐式 prop join 在 :285/:292/:303 才生成，永远不进过滤路径；`visitSqlFrom` 期间生成的 prop join 虽会出现在 `getEntitySources()`，但被 `filterAlreadyAdded` + 非 mainSource 挡掉（:379-386）。**注意**：显式 prop-path 写法 `left join o.dept d` 经 `visitJoinRight:650 → resolvePropPath → addToOneRelationJoin:795` 走同一条 `addTableFilterForPropJoin` 路径，ON 中同样只有逻辑删除、无租户——真正带全部缺省过滤的只有"显式**实体名** join + 手写 ON"（`left join Department d on ...`）。
- **严重程度**: P2
- **现状**: 所有 prop-path 关联（无论隐式 `o.dept.name` 还是显式 `left join o.dept d`）生成的 `left join Department d on o.deptId = d.id and d.deleted = 0` **均不含** `d.tenant_id = ?` 与 filters；仅实体名 join 带全部缺省过滤。
- **风险**: 多租户表经 prop-path 关联可读取其他租户的同 id 关联数据，租户隔离出现绕过缝隙。
- **建议**: prop join 建表时与实体名 join 对齐，在 ON 条件中追加租户条件与 filters（修复时建议补"prop join ON 条件含租户"回归测试锁定形状）；或由维护者明确裁定该语义为设计约定并记录。
- **信心水平**: 确定（第 2 轮独立复核完整追过两条调用链，并修正了第 1 轮"显式 prop-path join 带全部过滤"的错误对照）。
- **发现来源**: 第 1 轮独立复核新增，第 2 轮扩大缺陷范围。

### GEO-01 [P3] GeometryTypeHandler.toLiteral 用 `.` 拼接 SRID，几何字面量非法

- **文件**: `nop-persistence/nop-orm-geo/src/main/java/io/nop/orm/geo/type/GeometryTypeHandler.java:73-84`
- **现状**: `sb.append('.').append(Math.max(geom.getSRID(), 0))` 生成 `ST_GeomFromText('WKT'.4326)` 形式的非法 SQL（标准应为 `,srid`）；消费点为 `nop-dao` 的 `DialectImpl.getValueLiteral`（:540-565）字面量内联路径（可达方：nop-batch-jdbc 的 `GenInsertSqlRecordIO.encode` 批量导出内联 INSERT 等）。基类 handler 被 MySQL/SQL Server/DB2 方言子类继承且未重写 `toLiteral`/`isLiteralIncludeSRID`，对这些库同样非法；仅 PostGIS（EWKT 内嵌 SRID）与 Oracle（重写用逗号）不受影响。
- **严重程度**: P3（可达路径窄：仅字面量内联场景；常规 ORM 读写走 WKB 绑定不经过 toLiteral）
- **信心水平**: 语法错误确定。
- **发现来源**: 第 1 轮独立复核新增，第 2 轮扩大影响面。

### TD-01 [P3] TdEntityPersistDriver.batchExecuteAsync 所有路径返回 null

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/driver/TdEntityPersistDriver.java:121-151`
- **现状**: 同步执行后 `return null` 而非 `FutureHelper.voidPromise()`，与 RPC-01 同类契约偏离（当前调用方 `collectWaiting` 判空容忍，不崩溃）。第 2 轮复核确认驱动其余方法无更严重缺陷（loadAsync/batchLoadAsync 返回正常 future、topo 两阶段与 BatchActionQueue 吻合、子表名有白名单校验）。附带卫生观察：`TdSqlHelper.appendEq`（:159-164）的 `binder` 参数完全未使用，所有 example/count/delete 值经 `appendValue`（:116-122）内联为字面量（非 Number 一律加引号，Boolean 写成 `'true'`），是否产生运行时错误取决于 TDengine 服务端类型转换。
- **严重程度**: P3
- **信心水平**: 高。
- **发现来源**: 第 1 轮独立复核新增。

### DATA-01 [P3] DaoEntityBlockingSource 抢占非原子，多消费者并发下同一任务可被重复投递

- **文件**: `nop-persistence/nop-orm-data/src/main/java/io/nop/orm/data/source/DaoEntityBlockingSource.java:136-191`
- **现状**: `drainTo`（REQUIRES_NEW）内 `loadItems` 用普通 SELECT 取待处理实体（:172-175），随后仅在内存中修改 acquire 状态字段（:178-188），UPDATE 为"SET host/time/status WHERE pk=?"——不含 `WHERE status=未占用` 守卫，SELECT 也无 FOR UPDATE/skip locked。两个消费者（不同线程/主机，`acquireHostField=AppConfig.hostId()` 表明多主机是设计意图）在彼此提交前读到同一批行并各自"抢占"成功 → 任务重复处理（at-least-once 且无去重守卫）。
- **严重程度**: P3（若平台承诺多消费者安全则为 P2；单消费者部署无影响）
- **信心水平**: 代码事实确定；是否属"接受的 at-least-once 语义"需维护者裁定。
- **发现来源**: 第 2 轮独立复核新增。

### MISC-01 [P3] OrmAssembly.getValuesByIndexes 是带疑似缺陷的死代码

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/OrmAssembly.java:104-113`
- **严重程度**: P3
- **现状**: `binders[i]` 按位置取 binder 而值按 `colIndexes[i]` 取，语义可疑（全库其余调用均为 `binders[propId]` 风格）；全仓无调用方。建议删除。
- **信心水平**: 确定（无调用方）。

### MISC-02 [P3] 仓库卫生：nop-persistence/nop-orm-graphql 目录残留未跟踪构建产物

- **文件**: `nop-persistence/nop-orm-graphql/target/`（目录内无 pom.xml、无 src，git 未跟踪）
- **严重程度**: P3
- **现状**: 疑似已删除模块的残留 target 目录，容易被误认为活跃模块。建议删除目录。

## 已复核推翻的疑似项（避免后续重复上报）

1. **lock 失败毒化全局缓存**：`JdbcEntityPersistDriver.lock` 查无记录时先 `session.markMissing(entity)` 再返回 false，`EntityPersisterImpl.lock` 中的 `updateGlobalCache` 会走 removeAsync 分支 → 不成立。
2. **JdbcHelper.setParameters 0 基索引**：`DialectImpl.jdbcGet/jdbcSet` 统一 `index + 1`，全链路 0 基约定一致 → 不成立。
3. **RpcEntityPersistDriver 返回 null 导致批处理 NPE**：`FutureHelper.collectWaiting(null, …)` 显式判空 → 不成立（降级为契约瑕疵，见 RPC-01）。
4. **失败迁移记录永久卡死**：`MigrationHistoryManager.getExecutedVersions` 只取 `success = TRUE` 的版本，失败迁移会重试 → 不成立。

## 覆盖度自评

- **精读**：nop-orm 会话/持久化/驱动/SQL 生成全链路（OrmSessionImpl、CascadeFlusher、EntityPersisterImpl、JdbcEntityPersistDriver、GenSqlHelper、EntitySQL、BatchActionQueueImpl、CollectionPersisterImpl、OrmBatchLoadQueueImpl、OrmEntity(Set)、OrmEntityHelper、OrmAssembly、OrmRevisionHelper）、nop-dao 事务与 JDBC 核心（AbstractTransaction、TransactionTemplateImpl、DefaultTransactionManager、JdbcTransaction、JdbcBatcher、JdbcHelper、JdbcTemplateImpl、DialectImpl 关键段）、nop-orm-rpc 全文、LettuceRedisConnectionProvider 全文、MigrationEngine/MigrationHistoryManager 核心。
- **第 2 轮独立复核补查**：GeometryTypeHandler 的方言继承面、TdEntityPersistDriver 全方法、DaoEntityBlockingSource、AstToSqlGenerator/AstToEqlGenerator（转义/union/别名）、eql/binder 目录、nop-nosql-core 全部、nop-dbtool-core（Upgrader/Initializer/JdbcMetaDiscovery/TableSchemaMeta/DataBaseMeta）、nop-orm-geo 各方言/initializers/util、nop-orm-pdm 全文、nop-orm 的 mdx/kv/ddl/component/OrmTimestampHelper/LogicalDeleteHelper、nop-cdc-core——除已收录条目外未再发现新缺陷（第 2 轮另记录两项低危观察不上报：多递归 CTE 重复输出 `recursive` 关键字、NosqlCache 写路径不应用 expireAfterWrite TTL，见下"Open Questions"）。
- **浅层**：nop-orm-eql 的 AstToSqlGenerator/AstToEqlGenerator 细节、binder 目录、nop-orm-model 其余初始化逻辑、nop-dbtool discovery 细节、nop-orm-geo 其余方言、nop-orm-pdm、nop-cdc-core（仅常量接口）。
- **未覆盖**：EQL ANTLR 生成解析器、`_gen` 模型类（按规范跳过）。
- **复核中主动排除的待裁定项**：① AstToSqlGenerator 对 LEFT JOIN 右表在 WHERE 级添加数据权限 filter marker（AstToSqlGenerator.java:236-237,265-269）会使外连接退化为内连接——数据权限 filter 属可选功能、平台语义存争议；② DATA-01 是否属接受的 at-least-once 语义；③ SQL-01 无租户上下文 example 查询行为；④ TdSqlHelper 值内联的类型转换依赖。

## Open Questions

- [x] ORM-01/02/03 已由第 1 轮独立复核确认，第 2 轮复核修订表述一致。
- [x] EQL-01/GEO-01/TD-01 已由第 2 轮独立验证成立（EQL-01 缺陷面扩大至所有 prop-path join）。
- [ ] EQL-01/SQL-01/DATA-01 的语义裁定需要平台维护者参与（prop join 租户过滤、无租户上下文查询行为、阻塞源 at-least-once 语义）。
- [ ] 两轮累计 22 项发现的修复排期未定；修复 EQL-01 时建议补"prop join ON 条件含租户"回归测试，修复 ORM-02/03 时建议补 useRevision 实体的持久化流测试。

## 修复处置记录（2026-09-05，红测试先行）

按"先写红测试复现缺陷，再修复转绿"的流程处置，全量 nop-persistence 聚合构建测试通过。提交：`82713ea1fa`、`d150dbcad2`、`d6abedee07`+`b00d3ab52b`、`530a8c0572` 及后续提交。

| 编号 | 处置 | 修复/测试位置 |
|------|------|--------------|
| ORM-01 | ✅已修复+测试 | internalMarkDirty/internalUpdate登记changedDuringFlush；flushedEntities去重防自重复刷新。测试`TestFlushChangeRegistration` |
| ORM-02 | ✅已修复+测试 | newRevEntity用orm_propValue标脏revEnd。测试`TestOrmRevisionCloseUpdate` |
| ORM-03 | ✅已修复+测试 | genFindLatestSql列循环排除revEnd。测试`TestGenFindLatestSql` |
| ORM-04 | ✅已修复+测试 | 集合批量加载缓存更新链接进返回future。测试`TestCollectionBatchLoadCache` |
| ORM-05 | ✅已修复+测试 | 批量加载清理覆盖entityPropLoadMap并移除空条目（同时移除hasLazyColumn门控）。测试`TestBatchLoadQueueCleanup` |
| ORM-06 | ✅已修复+测试 | flushImmediately不回滚flush期间新dirty；execute(entity)补设flushing标志。测试`TestFlushChangeRegistration` |
| ORM-07 | ✅已修复+测试 | markPropDirty字节数组按内容比较。测试`TestOrmEntityMarkPropDirty` |
| ORM-08 | ✅已修复+测试 | IteratorView.remove未next直接抛ISE。测试`TestOrmEntitySetIteratorRemove` |
| RPC-01 | ✅已修复+测试 | batchExecuteAsync返回voidPromise。测试`TestRpcEntityPersistDriverNullFuture` |
| DAO-01 | ✅已修复+测试 | 批处理SUCCESS_NO_INFO计数归一化，消除null拆箱NPE与-2误报。测试`TestJdbcBatcherNoInfo` |
| DAO-02 | ✅已修复+测试 | commit/commitAsync失败路径发送onAfterCompletion(UNKNOWN)。测试`TestAbstractTransactionCommitNotification` |
| DAO-03 | ✅已修复+测试 | JdbcTransaction提交/回滚后恢复autoCommit。测试`TestJdbcTransactionAutoCommitRestore` |
| DAO-04 | ✅已修复（无独立测试） | 逐语句成功日志降为debug |
| SQL-01 | ✅已修复+测试 | example已携带租户条件时不再追加上下文租户条件。测试`TestGenSqlDefects` |
| SQL-02 | ✅已修复+测试 | 空SET段显式抛OrmException。测试`TestGenSqlDefects` |
| SQL-03 | ❌证伪回退 | getColumn(name,false)本身抛ERR_ORM_UNKNOWN_COLUMN，不存在NPE；相关修复代码与新错误码已回退 |
| EQL-01 | ✅已修复+测试 | prop join的ON条件补齐租户条件与实体固定过滤器（隐式/显式prop-path写法均覆盖）。配套扩展EQL编译器：SqlParamTypeResolver按渲染顺序遍历SqlTableSource.propJoins收集参数（explicit跳过——其条件已转移到SqlJoinTableSource按常规AST收集），SqlTableSource克隆保持插入序。测试`TestEqlTenantPropJoin`（隐式join租户过滤+主表不变性）。首次修复曾引发运行期sql-param-count-mismatch（prop-join参数未收集），补齐编译器参数收集后nop-orm 189测试与全量聚合回归通过 |
| GEO-01 | ✅已修复+测试 | SRID以逗号拼接。测试`TestGeometryTypeHandlerLiteral` |
| TD-01 | ✅已修复+测试 | 返回voidPromise。测试`TestTdEntityPersistDriverContract` |
| DATA-01 | ✅已修复+测试 | 抢占改为条件UPDATE原子完成，冲突记录跳过。测试`TestDaoEntityBlockingSourceClaim` |
| MISC-01 | ✅已修复 | 删除getValuesByIndexes死代码 |
| MISC-02 | ✅已修复 | 删除nop-orm-graphql残留目录 |

汇总：22项中20项修复（含2项无独立测试的卫生项）、1项证伪（SQL-03）、1项修复含编译器扩展（EQL-01）。新增回归测试约26个。

## 共识声明

经过主通道精读 + 两轮独立子 agent 串行复核（第 1 轮：逐项验证 18 项 + 补查 EQL/model/rpc/geo/tdengine，全部 CONFIRM 并新增 3 项；第 2 轮：验证新增项 + 复核修订表述 + 补查 nosql/dbtool/pdm/data/mdx/kv/ddl/binder，确认 3 项新增并新增 1 项、修正 2 处表述），两个独立审查通道对全部 22 项发现及其严重程度判级达成一致，无待裁决的代码疑点分歧，本报告即为共识版本。
