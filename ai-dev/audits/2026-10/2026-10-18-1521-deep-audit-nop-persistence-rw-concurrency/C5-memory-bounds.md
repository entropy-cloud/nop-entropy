# C5 内存有界性初审报告 — nop-persistence 读写热路径（合并批 1/1）

- **仓库**: /Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- **审计对象**: nop-persistence/nop-orm、nop-orm-eql、nop-orm-model、nop-dao、nop-orm-data（5 源码根，509 files / 84,484 lines，src/main， excl `_gen/`）
- **维度定义（C5）**: 验证在模块声明的数据规模包络内，任何执行路径的内存占用有上界；大数据量不 OOM。缓存容量与淘汰、批量写中间集合、查询结果物化面。
- **盲审纪律声明**: 本轮为盲审轮。未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何文件；仅审计当前 live code。允许阅读的方法论文件（concurrency-resource-audit-prompt.md）已完整阅读。同设施面（C1-001~030/C2-001~020/C3-001~006/C4-001~003、CL-400~504）仅在 F3 字段互引，不做重复判级。
- **判级校准**: 沿审计 prompt 校准（P0 声明包络内必然 OOM；P1 长时运行必然触发的无界增长；P2 有界但边界配置缺陷/可量化浪费；P3 优化候选）。C5 每条结论附规模量级。
- **基线**: `_tmp/cra-baseline-nop-persistence-rw.txt`（C5 节无界候选 grep 计数为 0，为 grep 模式局限，实际无界面以本报告人工盘点为准）。

## 0. 内存容器总账（有界性判定矩阵）

| # | 容器 | 位置 | 上界机制 | 判定 |
|---|------|------|---------|------|
| B1 | 一级实体缓存 `OrmSessionEntityCache.idToEntities`（LinkedHashMap per entityName） | `nop-orm/session/OrmSessionEntityCache.java:56` | session 生命周期（`runInNewSession` finally close，`OrmSessionImpl.close` → `cache.clear()`，`OrmSessionImpl.java:778`） | **有界 by session**；session 内无上限（见 C5-001 的叠加面） |
| B2 | 多租户一级缓存 `TenantOrmSessionEntityCache.caches`（per-tenant B1） | `session/TenantOrmSessionEntityCache.java:28` | session 生命周期 × 本 session 触及租户数 | 有界 by session |
| B3 | 无状态会话缓存 `sessionCache = MapCache("orm-session-cache")` | `OrmSessionImpl.java:1302-1307` | session 生命周期 | 有界 by session（session 内无淘汰，与 B1 同口径） |
| B4 | 二级全局实体缓存 `LocalCacheProvider("orm-global-cache")` per entityName 一 cache | `factory/OrmSessionFactoryBean.java:113-118`、`OrmConfigs.java:39-44` | Caffeine maximumSize=10000 **per cache（per entity）** + expireAfterWrite=10min；热刷新 `refreshConfig()` 已修（详见 nop-orm 源码注释） | **有界（有淘汰）**；总量 = 实体模型数 × 10000 条（见 C5-003 乘数口径） |
| B5 | 查询计划缓存 `queryPlanCache` | `OrmSessionFactoryBean.java:106-107` | Caffeine maximumSize = `nop.orm.query-plan-cache-size`=1000 | 有界（有淘汰） |
| B6 | 租户计划子缓存 `TenantCachedQueryPlan.tenantCache` | `factory/TenantCachedQueryPlan.java:17` | Caffeine maximumSize=100 per 计划键 | 有界（有淘汰）；租户数>100 时编译抖动（CL-602） |
| B7 | SqlExprMetaCache `entityMetas` | `nop-orm-eql/meta/SqlExprMetaCache.java:23` | ConcurrentHashMap，键=entityName（模型键空间，启动后封闭） | 有界（键空间封闭） |
| B8 | 批量写动作队列 `BatchActionQueueImpl.actionMap/collectionActions` | `persister/BatchActionQueueImpl.java:38-43` | flushAsync finally 全部清空（`:232-238`）；队列寿命 = 一次 flush | 有界 by 事务脏集（见 C5-004 分配放大） |
| B9 | 批量装载队列 `OrmBatchLoadQueueImpl.LoadQueue` | `loader/OrmBatchLoadQueueImpl.java:46-67` | flush 置 `loadQueue=null`（`:584-600`）；JDBC 分块 `maxBatchLoadSize`=1000（`EntityPersisterImpl._batchLoad:202-215`） | 有界 by session 两次 flush 之间的代理量 |
| B10 | JDBC 批处理 `JdbcBatcher.commands` | `nop-dao/jdbc/JdbcBatcher.java:58,143-146` | `CFG_DAO_JDBC_MAX_BATCH_UPDATE_SIZE` 到量即 flush | 有界 |
| B11 | CascadeFlusher 中间集合（waitDeletes/changedDuringFlush/flushedEntities/flushVisitingEntities） | `session/CascadeFlusher.java:40-54` | CascadeFlusher 为每 flush 新建、flush 后丢弃（`OrmSessionImpl.java:208-219`） | 有界 by flush |
| B12 | OrmEntitySet.removedEntities/initialEntities | `support/OrmEntitySet.java:90-92` | `orm_clearDirty`/`orm_beginLoad` 置 null（`:655-682`） | 有界 by session |
| B13 | 查询结果缓存 `JdbcTemplateImpl.cacheMap`（name→ICache） | `nop-dao/jdbc/impl/JdbcTemplateImpl.java:92` | 条目数由 cacheProvider 配置；**单条目体积无行数上限**（C5-002）；name 键空间封闭 | 半有界（C5-002） |
| B14 | 事务监听器列表 `AbstractTransaction.listeners` | `nop-dao/txn/impl/AbstractTransaction.java:56` | `invokeCloseListener→clearListeners`（`:423-435`），事务 close 必经 | 有界 by 事务（增长速率问题归 C9-001） |
| B15 | `JdbcTemplateImpl.clockMap`、`DefaultTransactionManager.transactionFactoryMap`、`JdbcDialectProvider.querySpaceToDialectMap` | `JdbcTemplateImpl.java:90` 等 | 键=querySpace，必须预注册数据源方可使用（`DefaultTransactionManager.getTransactionFactory:185-193` 未知即抛） | 有界（键空间封闭） |
| B16 | `TenantAwareOrmModelProvider.tenantCaches` | `factory/TenantAwareOrmModelProvider.java:24-27` | Caffeine maximumSize=`nop.orm.orm-model-cache.tenant-cache-container-size`=100 + destroyOnRemove | 有界（有淘汰+销毁钩子） |
| B17 | 上下文注册表（OrmSessionRegistry / TransactionRegistry） | `impl/OrmSessionRegistry.java`、`txn/impl/TransactionRegistry.java` | 存放于 IContext attribute，随 context 生命周期 | 有界 by context |

**总账结论**: 全部常驻缓存均有显式容量上限与淘汰（B4/B5/B6/B16 Caffeine）或封闭键空间（B7/B15）；全部中间集合均绑定 session/flush/事务生命周期。真正的无界增长面集中在**查询结果物化路径**（C5-001、C5-002）与**有界缓存的乘数/放大口径**（C5-003、C5-004）。

---

## 发现条目

### [nop-persistence-rw-C5-001] findAllByExample/findAll 全量物化且与一级缓存双重驻留，结果集无行数上限

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java:353-365`；`nop-persistence/nop-dao/src/main/java/io/nop/dao/api/ISqlExecutor.java:180-182`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/OrmAssembly.java:155-165`
- **证据片段**:
  ```java
  // ISqlExecutor.java:180 — findAll 无 range，RowMapperAllExtractor 吸收全部行
  default <T> List<T> findAll(final SQL sql, IRowMapper<T> rowMapper) {
      return executeQuery(sql, null, new RowMapperAllExtractor<>(rowMapper));
  }
  // JdbcEntityPersistDriver.java:360 — findAllByExample 直接走 findAll
  return jdbc().findAll(sql, (rs, rowNumber, colMapper) -> {
      IOrmEntity entity = OrmAssembly.readEntity(rs, entityModel, binders,
              entityModel.getEagerLoadProps(), session);
      return (T) entity;
  });
  // OrmAssembly.readEntity:159-162 — 每行经 session.internalLoad 进入一级缓存
  IOrmEntity entity = session.internalLoad(entityModel.getName(), id);
  session.internalAssemble(entity, values, propIds);
  ```
- **严重程度**: P2
- **并发场景**: 单 HTTP worker / 批处理线程内的单次查询；并发请求数 × 各自 session 一级缓存叠加。
- **触发时序**: 非竞态问题，为规模触发：T1 业务调用 `findAllByExample`（无 orderBy/limit 注入）→ SQL 无 LIMIT → `JdbcTemplateImpl.executeQuery`（range=null）逐行流式读取 → 每行 `readEntity` 同时 (a) 加入返回 List、(b) 经 `session.internalLoad→makeProxy→cache.add` 进入 L1 `LinkedHashMap` → 结果集 N 行时堆内同时驻留 2N 个实体图，直至 session 关闭。
- **现状**: findAll 是声明 API（平台提供 findPage 分页替代），但 ORM 层在普通 JDBC 语义之外**额外叠加 L1 缓存驻留**，使实际内存≈2×结果集，且生命周期延长到 session 结束。
- **风险**: 规模量级——每行 = OrmEntity 对象(~48B) + `Object[cols]` 属性数组(10 列≈120B) + L1 LinkedHashMap 条目(~48B) + List 条目(~8B) ≈ **0.25–0.5KB/行**；单次 100 万行查询 ≈ **250–500MB**；并发 10 个此类请求 ≈ **2.5–5GB**，堆预算内可触发 Full GC 雪崩乃至 OOM。会话级实体还会被 flush 脏检查全量扫描（`OrmSessionEntityCache.forEachDirty`）。
- **建议**: (1) 对 `findAllByExample`/`deleteByExample(entityMode)` 增加可配置行数上限（如 `nop.orm.find-all-max-rows`，超限抛 `ERR_ORM_FIND_ALL_ROW_LIMIT_EXCEEDED`，对齐 `ShardFanoutEngine` 的 `fanout-max-rows` 先例）；(2) 只读遍历场景文档声明引导 `findPage`；(3) 大结果导出场景引导 stateless session（`OrmTemplateImpl.forStateless`，逐实体 flush 后即弃）。
- **失效面枚举（F3）**: ① `JdbcEntityPersistDriver.findAllByExample:353-365` —— 本体，无上限；② `EntityPersisterImpl.deleteByExample:692-698` entityMode 分支（findAllByExample→逐个 session.delete）—— 同族无上限；③ `EntityPersisterImpl.updateByExample:739-747` entityMode 分支 —— 同族无上限；④ `JdbcEntityPersistDriver.findPageByExample:338-350` —— 有 limit 下界，**安全**；⑤ `findFirstByExample/countByExample` —— 单行/聚合，**安全**；⑥ `ShardFanoutEngine`+`FanoutRowMerger` —— 受 `nop.orm.shard.fanout-max-rows` 逐分片增量计数兜底，**安全**；⑦ `DaoEntityBlockingSource.loadItems` —— 受 maxElements 限制，**安全**；⑧ `JdbcDataSet.readAll` —— 调用方契约，登记为等价族（仓库内调用点未逐一遍历，超出 5 根范围外的不判级）。
- **信心水平**: 确定
- **误报排除**: 非"有界声明存在的缓存"——findAll 无任何上限声明；也非调用方责任可以豁免：ORM 在 JDBC 流式语义上叠加了 L1 双重驻留，这是框架层放大，非单纯 API 误用。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。findAll 无 range + L1 双重驻留机制亲验成立；量级估算链合理

---

### [nop-persistence-rw-C5-002] cacheRef 查询结果缓存将整个结果集物化为单条缓存条目，条目体积无行数上限

- **文件**: `nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:437-456`；`nop-kernel/nop-dataset/src/main/java/io/nop/dataset/impl/DataSetCacheHelper.java:23-33`
- **证据片段**:
  ```java
  // JdbcTemplateImpl.saveCacheData — 命中 cacheRef 的查询在回调消费前先全量物化
  private IDataSet saveCacheData(IDataSet ds, SQL sql, LongRangeBean range) {
      ...
      DataSetCacheData cacheData = DataSetCacheHelper.toCacheData(ds); // 全量读取
      cacheData.setSql(sql.getText());
      cacheData.setRange(range);
      cache.put(cacheRef.getCacheKey(), cacheData);   // 整个结果集作为一条
      return DataSetCacheHelper.toDataSet(cacheData, true); // 再构造第二份行对象视图
  }
  // DataSetCacheHelper.toCacheData — List<Object[]> 吸收全部行
  List<Object[]> objects = dataSet.stream().map(IDataRow::getFieldValues).collect(Collectors.toList());
  ```
- **严重程度**: P2
- **并发场景**: 多 worker 并发执行携带同一 cacheRef 的查询；缓存条目常驻跨请求。
- **触发时序**: 非竞态，规模触发：T1 查询携带 `cacheRef` 且 SQL 无 LIMIT → `executeQuery` 内 `saveCacheData` 在回调前把 ResultSet 全部行读为 `List<Object[]>` → 单条 cache entry = N×cols×(8B 引用+对象头) → put 进命名缓存常驻（TTL/容量由 cacheProvider 配置）→ 同时 `toDataSet` 再物化一份 `BaseDataRow` 列表供回调，**峰值≈2×结果集**。
- **现状**: 条目**数量**有界（缓存容量配置），但条目**体积**无任何行数上限；且即使调用方只需首行（findFirst），也先全量物化。
- **风险**: 规模量级——100 万行 × 10 列 entry ≈ **300–600MB** 单条常驻；该查询每次未命中都重复物化（写前无去重/互斥，并发同 key 未命中会重复物化+相互覆盖，浪费面×并发度）。缓存命名空间为 sql-lib 开发者定义（键空间封闭，无用户可控键注入面）。
- **建议**: (1) 对 `saveCacheData` 增加单条目行数/字节上限（超限记日志并放弃缓存，仅流式返回）；(2) 未命中路径加 per-key in-flight 合并（平台 `ICache.get(k, loader)` 已具备单飞语义，可复用）；(3) 文档声明 cacheRef 仅适用于有界结果集（字典/配置类查询）。
- **失效面枚举（F3）**: ① `JdbcTemplateImpl.saveCacheData:437-456` —— 本体；② `JdbcTemplateImpl.getCacheData:394-427` —— 读路径仅反序列化已有条目，安全；③ `clearQueryCache/clearQueryCacheFor/evictQueryCache:235-255` —— 清理入口齐全（配对完整）；④ `noCacheProviderWarned` 分支 —— 未配置 provider 时已有告警，安全。
- **信心水平**: 确定
- **误报排除**: 非"有界声明存在"——容量上限约束的是条目数而非条目体积；声明包络内（普通业务查询被标 cacheRef）单条目即可达数百 MB，与"大数据量不 OOM"目标矛盾。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**

---

### [nop-persistence-rw-C5-003] 全局实体缓存容量为 per-entity-name 乘数口径，总量上限 = 模型实体数 × 10000 条，且 LocalCacheProvider 名称注册表只增不减

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCacheProvider.java:39-45`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmConfigs.java:38-44`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:94-95,571-586`
- **证据片段**:
  ```java
  // LocalCacheProvider.getCache — 每个 cacheName 一个独立 LocalCache，注册表无移除 API
  public <K, V> ICache<K, V> getCache(String name) {
      return (ICache<K, V>) caches.computeIfAbsent(name, this::buildCache);
  }
  // EntityPersisterImpl.init:94-95 — 每个启用全局缓存的实体独占一个命名缓存
  if (useGlobalCache)
      globalCache = env.getGlobalCache(entityModel.getName());
  // EntityPersisterImpl.getCacheKey:585 — 租户前缀键：tenantId + ":" + id
  return tenantId + ":" + entity.orm_id();
  ```
- **严重程度**: P3
- **并发场景**: 全部 worker 线程共享的全局缓存面。
- **触发时序**: 非竞态，容量验算：配置 `nop.orm.entity-global-cache.size`=10000 的语义是**每个实体一个 10000 条缓存**（`@Description`"针对每个实体的全局缓存的大小"已声明），总驻留 = 启用 useGlobalCache 的实体数 × 10000 × 每条 `Object[allCols]`；键含租户前缀，条目基数 = 租户数 × 实体数。300 实体全开、200 租户、条目 200B 时理论上限 ≈ 300×10000×200B = **600MB**（受 TTL 10min 与访问局部性稀释，实际远低，但上限口径必须登记）。另：`caches` 注册表条目（name→LocalCache 空壳约 KB 级）只增不减，键空间=实体名/sql-lib 缓存名，封闭。
- **现状**: 有界 + 有淘汰（Caffeine maximumSize+expireAfterWrite，`OrmSessionFactoryBean.refreshConfig` 支持热调），非缺陷；登记的是**容量规划口径**——单实体 10000 的配置在多实体多租户部署下的乘数效应无汇总指标。
- **风险**: 无 direct OOM 风险（上限存在）；风险是运维按"全局 10000"理解导致容量误估，以及租户基数大时单实体缓存被租户键挤占（命中率下降，非性能正确性问题）。
- **建议**: (1) 在 `OrmSessionFactoryCacheManagement.stats()`（当前返回 null，`OrmSessionFactoryBean.java:159-162`）补 per-cache 容量/命中率统计，暴露真实乘数；(2) 文档声明乘数口径。
- **失效面枚举（F3）**: ① ORM 全局缓存（per-entityName）—— 本体；② 查询结果缓存 `JdbcTemplateImpl.cacheMap`（per cacheName，见 C5-002）—— 同族乘数；③ `TenantAwareOrmModelProvider.tenantCaches`（上限 100 全局共享，无乘数）—— 口径相反，安全；④ `TenantCachedQueryPlan.tenantCache`（per-plan 100）—— 乘数=计划键数×100，见 CL-602；⑤ `queryPlanCache`（全局单缓存 1000，无乘数）—— 安全。
- **信心水平**: 确定（口径）/ 很可能（量级估算）
- **误报排除**: 按"误报校准"本应豁免有界缓存；立项为 P3 仅因 (a) stats() 返回 null 使上界不可观测，(b) 乘数口径未被任何 owner 文档汇总——属于"边界配置缺陷"的低危形态而非无界面。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

---

### [nop-persistence-rw-C5-004] 批量写路径中间集合分配放大：每 DML 一个匿名监听器 + TreeMap<String,·> 键装箱，上界为事务脏集（伴随 C9-001 的 O(n²)）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java:51-93`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:456-506,553-569`
- **证据片段**:
  ```java
  // BatchActionQueueImpl — update/delete 按 String 键入 TreeMap
  public void addUpdateAction(IBatchAction.EntityUpdateAction action) {
      if (updateActions == null)
          updateActions = new TreeMap<>();
      updateActions.put(String.valueOf(action.getEntityId()), action); // 每动作一次 long→String
  }
  // EntityPersisterImpl.queueUpdate:483-492 — 每实体写一个动作对象+一个回调闭包
  IBatchAction.EntityUpdateAction action = new IBatchAction.EntityUpdateAction(entity, shard, (ret, err) -> {...});
  // EntityPersisterImpl.evictGlobalCache:560 — 每实体 DML 再 new 一个匿名 ITransactionListener
  this.env.txn().addTransactionListener(querySpace, new ITransactionListener() { ... });
  ```
- **严重程度**: P3
- **并发场景**: 单事务写线程；内存面（时间复杂度面归 C9-001，互引不重复判级）。
- **触发时序**: 非竞态，分配验算：一次 flush 写 N 个实体 → N×(EntitySave/Update/DeleteAction + 回调闭包 + String键) + N×匿名 ITransactionListener（捕获 shard+entity 引用，额外延长实体可达性至事务结束）≈ N×(6–10 个分配对象)；N=1 万的批量导入事务 ≈ **10 万级中间对象**，全部随 flushAsync finally 清空 + 事务 close 清 listeners（`AbstractTransaction.invokeCloseListener:423-435`），**生命周期有界**。
- **现状**: 无泄漏、有上界（=事务脏实体数，与 Hibernate flush 队列同构）；登记的是单位分配放大——单槽 TreeMap 仅用于"按 id 排序避免锁冲突"，却付出每动作一次字符串装箱。
- **风险**: 批量导入场景 Young GC 频率上升（可量化：1 万实体事务多产生 ~80–160MB 短命对象——含 C9-001 的 COW 拷贝；不含则 ~5–10MB）；无正确性影响。
- **建议**: (1) `evictGlobalCache` 改为事务级单监听器 + per-txn Set<cacheKey> 汇总（同时消解 C9-001 的 O(n²)，一处修复两案）；(2) `updateActions/deleteActions` 键改用 `Long` 比较器或按 entityId 预排序数组，消除 String 装箱。
- **失效面枚举（F3）**: ① `queueSave:456-471`/`queueUpdate:473-493`/`queueDelete:495-506` —— 三个 enqueue 站点同构（动作对象+闭包，必有）；② `evictGlobalCache:553-569` —— 监听器站点（save/update/delete 三路共用）；③ `JdbcEntityPersistDriver.lock:173-178` —— 同族第四站点（lock 路径，同 C9-001 枚举）；④ `CollectionPersisterImpl` 的集合动作入队 —— 走 `enqueueCollection` 列表，无 String 装箱，安全。
- **信心水平**: 确定
- **误报排除**: 中间集合本身随 flush 清空、有界，不是 F4/F7 无界账本；立项点是可量化的分配放大，属 C5×C9 交叉的低危浪费面。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P3**

---

## 维持性结论（有界，不立项）

1. **一级缓存（B1/B2/B3）**: per-session 有界，`runInNewSession`/`runInNewSessionAsync` 在 finally/whenComplete 中 close（`OrmTemplateImpl.java:214-231,244-270`），close 幂等并清 `cache`+`sessionCache`（`OrmSessionImpl.java:768-791`）。session 内无上限是 Hibernate 语义的声明边界；叠加面已并入 C5-001。
2. **批量装载队列（B9）**: `OrmBatchLoadQueueImpl` 的 entityLoadMap/collectionLoadMap 以 entityName 为键（键空间=模型），`entities` Set 的基数=两次 flush 间代理量；JDBC 侧 `maxBatchLoadSize`=1000 分块 IN 查询（`EntityPersisterImpl._batchLoad:202-215`），单条 SQL 文本长度上界 ≈ 1000×id 长度 ≈ 20–30KB，安全。
3. **EQL 编译缓存（B5/B6/B7）**: 计划缓存 1000 + 每键租户缓存 100 + SqlExprMetaCache 封闭键空间，全部有界有淘汰。编译期中间对象（AST）仅存在于未命中编译窗口。
4. **ShardFanoutEngine**: fanout 归并受 `nop.orm.shard.fanout-max-rows` 达限即断（`ShardFanoutEngine` 设计注释与 `ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED`），是本仓库 C5 防线的正面样板。
5. **DaoEntityBlockingSource（nop-orm-data）**: `drainTo` 结果集合受 maxElements 约束（内存面有界）；其**事务/连接持有面**无界，归 C6-001（维度互引，不在 C5 判级）。

## CL 线索（本轮新增，CL-600 段）

- **CL-602**: `TenantCachedQueryPlan.tenantCache` 单计划键租户上限 100：租户数 >100 时最热计划之外发生编译抖动（吞吐损失，非正确性）。建议纳入容量规划口径并加命中率指标。
- **CL-604**: `DefaultOrmModelProvider.clearCache`（`factory/DefaultOrmModelProvider.java:43-47`）以新 `LoadedOrmModel` 直接替换字段、旧实例未经 `close()`：当前 `IEntityPersistDriver` 非 AutoCloseable、`EntityPersisterImpl.close→IoHelper.safeClose(driver)` 为 no-op，旧 model 可 GC，**现状无资源泄漏**；但若未来 driver 持有资源，reloadModel 路径将漏关。防御性登记（C6 报告同步互引）。

---

**总计**: C5 发现 4 条（P2×2：C5-001、C5-002；P3×2：C5-003、C5-004），有界性维持结论 5 项，CL 线索 2 条（CL-602、CL-604）。**末位编号 C5-004**。每条含规模量级；P0/P1 为零——未发现声明包络内必然 OOM 或长时运行必然无界增长路径。

## 维度复核结论（独立复核合并批，2026-10-18）

- **复核方式**: 与初审/深挖代理独立的第 2 轮合并复核批。本维度 5 条发现（含 round2 新增 C5-005）证据逐条亲验；历史对照面已解除盲审。
- **裁定统计**: 5 条全部**保留**（降级 0、驳回 0）——P2×3 维持 / P3×2 维持；**F3 勘误 1 处**（C5-005 内部一致性修正，见下）。
- **C5-005 复核与勘误**: limit 丢弃链亲验成立（DaoQueryHelper.queryToSelectFieldsSql 全函数无 getLimit 注入；OrmEntityDao.findAllByQuery:674-682 直调 orm().findAll(sql) range=null；MdxQueryExecutor:113-117 主查询分支与 :210 子表恒 findAll 亲验）。**勘误**：C5-005 自身 F3 表 ⑥「DaoEntityBlockingSource.loadItems——query.setLimit(maxCount) 有界安全」与其发现的 limit 丢弃事实**自相矛盾**——loadItems 正是经 dao.findAllByQuery 查询，setLimit(maxCount) 同样被静默丢弃。⑥ 改判为同缺陷受累面（后果：每 tick 物化全部积压行、drainTo 实际返回数可超 maxElements、攒批契约破坏、且放大 C2-005 抢占批与 C3-006/C6-001 每 tick DB 负载）；修复面并入 C5-005 单点守卫（ISqlExecutor.findAll 汇聚点行数上限即覆盖 ⑥）。此勘误强化而非削弱 C5-005（P2 维持；因仍需大表/大积压规模前提，不升 P1）。
- **有界性总账复核**: 17 容器总账抽验（B4/B5/B6 Caffeine 上界、B13 单条目体积无上限、B17 上下文注册表）维持；「P0=0/无长时运行必然无界增长」结论维持。
- **CL 处置（2 条确认）**: CL-602（租户计划子缓存 100 上限编译抖动）确认 watch；**CL-604 与 C6-003 同一事实**——终裁并入 C6-003（C6-003 为主条目、CL-604 互引），两处登记不重复计数。无驳回。

## 最终保留项

| ID | 初判 | 终裁 | 条目 |
|---|---|---|---|
| C5-001 | P2 | P2 | findAll 全量物化 + L1 双重驻留 |
| C5-002 | P2 | P2 | cacheRef 单条目整结果集物化 |
| C5-003 | P3 | P3 | 全局缓存 per-entity 乘数口径 |
| C5-004 | P3 | P3 | 批量写中间集合分配放大 |
| C5-005 | P2 | **P2（附 F3 勘误：⑥ loadItems 改判同缺陷受累面）** | findAll 族 ⑦-⑩ 无上限 + findAllByQuery limit 静默丢弃 |

**维度终局**: P2×3 / P3×2，F3 勘误 1 处（并入 C5-005 修复面），无驳回、无撤案。
