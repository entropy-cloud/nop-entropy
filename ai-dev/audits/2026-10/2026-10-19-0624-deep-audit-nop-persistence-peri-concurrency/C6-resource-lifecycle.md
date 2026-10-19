# C6 资源生命周期与泄漏 — nop-persistence-peri（WI 2.5）

- 审计维度：C6（资源生命周期与泄漏）
- 批次：单批次覆盖全部 10 个源码根
- 审计日期：2026-10-19（盲审初审，未读 ai-dev/audits、plans、bugs、lessons、backlog 历史记录；未查 git 修复历史）
- 方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md`（F1–F11 缺陷族 + C6 维度正文 + 附录 A 格式）

## 审计范围与口径

单批次全源码根覆盖声明（10/10）：

| 源码根 | 文件数/行数 | C6 结论 |
|---|---|---|
| `nop-persistence/nop-dbtool/nop-dbtool-core` | 7f/1,832 | 干净（JdbcMetaDiscovery 全部 ResultSet 走 try-with-resources，dataSource 路径 Connection 在 finally 归还；`forConnection` 路径不 close 是 javadoc 声明的所有权契约） |
| `nop-persistence/nop-db-migration` | 72f/3,877（`_` 前缀跳过） | 干净（执行器全部经平台 JdbcTemplate 执行；直接 JDBC 面 `MigrationHistoryManager.tableExists`/`IndexExistsChecker.indexExists` 的 ResultSet 均 TWR；`MigrationContext` 不持有裸连接） |
| `nop-persistence/nop-orm-migration` | 20f/3,462 | **C6-001/C6-002/C6-003**（裸线程 + CDC 链资源生命周期；`MigrationJobStateStore.initializeTable` 的 `openConnection` 已 TWR 正确配对） |
| `nop-persistence/nop-cdc/nop-cdc-core` | 1f/11 | **零审计面登记**：仅 `CdcConstants` 空接口，无任何资源获取点 |
| `nop-persistence/nop-nosql/nop-nosql-core` | 19f/1,083 | 干净（接口 + 配置 + Lua 脚本文本，无资源获取点） |
| `nop-persistence/nop-nosql/nop-nosql-lettuce` | 18f/2,377 | **C6-004/C6-005**（PubSub 订阅 entry 取消配对、共享连接 resize 收缩） |
| `nop-persistence/nop-orm-drivers/nop-orm-tdengine` | 6f/726 | 干净（全部 JDBC 经 jdbcTemplate；无直接连接/流获取点） |
| `nop-persistence/nop-orm-geo` | 12f/700 | 干净（`GeometryTypeHandler.toByteArray` 的 Blob 流 finally 关闭；Oracle handler 借用 native connection 不关闭，所有权正确；H2GIS init 经 runWithConnection） |
| `nop-persistence/nop-orm-pdm` | 9f/1,654 | 干净（XML 解析经平台 XNodeParser，无裸流） |
| `nop-persistence/nop-orm-rpc` | 1f/913 | 干净（RPC 经平台客户端；CompletableFuture 聚合均 completeExceptionally，无吞异常/无自持资源） |

降档口径执行：dbtool/db-migration 的启动期单线程降档**仅适用于竞态/可见性形态**；本维度全部发现为泄漏与残留形态，**一律未降档**。基线 C6a/C6b 节命中稀疏（grep 面窄），本次以人工补扫为准；基线 C4a 证实全部 10 根中裸线程创建点唯一（`IncrementalJobManager.java:176`），与人工扫描一致。

资源获取点配对表（C6 步骤 1/2 输出，仅列直接获取点；平台 JdbcTemplate/runWithConnection/XNodeParser 管理的面按口径不重审）：

| 资源 | 获取点 | 释放点 | 判定 |
|---|---|---|---|
| JDBC Connection（dbtool 发现链） | `JdbcMetaDiscovery.getConnection:197` | `closeConnection:203-207`（finally） | 配对 |
| ResultSet（JDBC metadata） | dbtool/db-migration 全部 6 处 | 全部 TWR | 配对 |
| Connection（状态行建表） | `MigrationJobStateStore.initializeTable`（openConnection） | TWR | 配对 |
| PreparedStatement（全量 upsert） | `ShardedUpsertConsumerProvider.upsertGroup:103` | TWR | 配对 |
| Blob 流（geo） | `GeometryTypeHandler.toByteArray:161` | finally safeCloseObject | 配对 |
| Lettuce 共享连接池 | `LettuceRedisConnectionProvider.doStart:115-123` | `doStop` 经 client.shutdown | 生命周期配对；**运行期收缩无配对**（C6-005） |
| PubSub 连接 + 订阅 entry | `LettucePubSubService.getOrCreateConnection:96-105` / `subscribe:72-94` | `doStop:112-117` / `PubSubSubscription.cancel:190-200` | 生命周期配对；**逐 topic 取消配对有竞态**（C6-004） |
| 裸线程 + Debezium 引擎 + 2PC sink | `IncrementalJobManager.startChain:176-186` | 仅 `stopAll:201-206` ← `abort` 唯一调用点 | **成功路径不配对**（C6-002）；**部分启动不配对**（C6-001）；**终止不可验证**（C6-003） |
| 临时表/中间产物 | db-migration 执行器无临时对象；orm-migration 中间产物仅 2PC ledger（释放归 C7/F4） | — | 本维度无发现 |

**终止信号：本维度存在 P0/P1 发现，不输出整维度无 P0/P1 的终止信号。**

## 发现

### [C6-001] startFallbackChains 部分启动孤儿链：后续分片 startChain 抛错时已启动链不停机

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:121-188`；`nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:196-203`
- **证据片段**:
  ```java
  // IncrementalJobManager.startFallbackChains
  for (int i = 0; i < targets.size(); i++) {
      PhysicalTable target = targets.get(i);
      String connectorName = targets.size() == 1 ? baseConnector : baseConnector + "-" + i;
      DebeziumConfig cdcConfig = buildCdcConfig(jobId, config, connectorName, tableIncludeList);
      started.add(startChain(jobId, cdcConfig, target, positionP0));   // 第 i 条失败时，0..i-1 条已在线运行
  }
  // OrmMigrationOrchestrator.runIncremental：先翻相位、后启动、无 try/finally
  state.setPhase(MigrationPhase.INCREMENTAL);
  store().update(state);
  ...
  List<...RunningChainJob> jobs = manager.startFallbackChains(jobId, config,
          SourcePosition.fromJson(state.getPositionP0()));
  ```
- **严重程度**: P1
- **并发场景**: 编排 API 调用线程与先前已启动的 M-1 条链作业后台线程（`startChain:176-185` 的裸 daemon 线程，各自持有 Debezium binlog 连接与 jdbc-2pc sink 连接）。
- **触发时序**: T1 编排线程执行 `runIncremental`，目标分片 M>1；i=0 链已完成 `thread.start()` 并注册进 `runningJobs`（L186）→ T1 继续迭代，i=1 的 `startChain` 内 `source.initializeState(null)` 抛错（L160-166 包装为 ERR_MIG_P0_NOT_PINNED）或 `sinkFactory.createSink` 内 `initializeLedgerTable()` DDL 失败 → 异常沿 `startFallbackChains` 向上传播，`runIncremental` 无任何清理路径 → T2 运维恢复：`resume()` 对 INCREMENTAL 相位仅原样返回 jobId（OrmMigrationOrchestrator L160-166），重试 `runIncremental` 被 `checkTransition` 拒绝（`MigrationPhase.ALLOWED` 中 INCREMENTAL 无自环）→ i=0 链成为孤儿：持续消费源库 binlog、向目标分片表 upsert、写 epoch ledger，直到人工显式 `abort()`（唯一的 stopAll 调用点）。交叠后果：连接/线程/订阅滞留 + 未经编排调度的目标端持续写入；若运维随后以新 jobId 重启同实体搬迁作业，新旧两代链并发写同一批目标物理表。
- **现状**: `startFallbackChains` 循环无"失败即回收已启动链"的补偿路径；`runIncremental` 先翻相位后启动且无 try/finally 回滚。
- **风险**: 取消/错误路径资源滞留（P1 判级依据）；孤儿链与新作业链双写目标表造成 2PC ledger 混入外部 epoch，干扰后续 lag=0 判定（`IncrementalLagProbe` 对 ledger 全表计数，不区分 jobId）。
- **建议**: `startFallbackChains` 内 catch 后对 `started`（以及 `runningJobs` 中本次 jobId 的条目）逐一 `stop()` 再重抛；`runIncremental` 将 `setPhase` 移到 `startFallbackChains` 成功之后，或补 try/finally 失败即 `abort` 相位回退。复用平台设施：无新轮子需求，仅补齐配对。
- **失效面枚举**（F3 同族清扫）: ① `startChain` 单独被外部直接调用（public API）失败时发生在 thread.start 之前，无泄漏——已核；② `startFallbackChains` 循环体——本发现主体，未配对；③ `OrmMigrationOrchestrator.abort:301-305` 是全模块唯一 stopAll 调用点（grep 证实），部分启动异常路径未经过它——未配对；④ db-migration 的 `MigrationEngine.migrate` 逐条迁移失败路径——走 history 失败记录 + fail-fast，无线程/连接滞留——已核干净。
- **信心水平**: 确定
- **误报排除**: 非平台基础设施内部问题（裸线程与 lifecycle 全部在本审计根内手写）；非低频启动路径粗锁类优雅性问题——泄漏发生在运行期 API 错误路径且有明确触发链。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（同案：本单元 C3-001 同根同站点已保留 P1——startFallbackChains 无补偿 + runIncremental 相位先翻，两维度面合并计一次工单，主登记随 C3-001 含 round4 relocate 同名扩展面）。复核回码全部成立：循环 L134-139 无补偿；runIncremental L196-197 先翻相位、无 try/finally；resume L160-166 对 INCREMENTAL 原样返回；INCREMENTAL→INCREMENTAL 非法迁移（MigrationPhase ALLOWED 无自环，重试被拒）；abort:304 唯一 stopAll 调用点；新 jobId 重启时 jobName 异构（L174 拼 jobId）双代并存双写目标表。时序描述与 C3-001 复核修正后口径一致，无事实修正。

### [C6-002] 搬迁成功路径永不停止增量链作业：DONE 后全部链线程、binlog/JDBC 连接永久滞留并继续写目标表

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:41-42,201-206`；`nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:210-290`（runVerify/confirmSwitch/completeSwitch 均无 stopAll）
- **证据片段**:
  ```java
  // IncrementalJobManager 类 javadoc（契约声明）：
  // "链作业通过 env.execute 在独立线程长驻运行；ABORT/阶段推进由 {@link #stopAll}
  //  停止（cancel source → run 循环退出）"
  // OrmMigrationOrchestrator.completeSwitch（成功终点）：
  OrmMigrationWriteGate.setClosed(false);
  state.setPhase(MigrationPhase.DONE);
  store().update(state);
  LOG.info("nop.orm.migration.done:jobId={}", jobId);   // 无 incrementalManager.stopAll()
  ```
- **严重程度**: P0
- **并发场景**: 编排 API 调用线程 vs M 条长驻链作业线程（每条持有 Debezium embedded engine 的源库 binlog 连接、jdbc-2pc sink 的目标库连接、checkpoint 定时提交）。
- **触发时序**: T1 编排线程走完 VERIFY→SWITCH→DONE（`runVerify`、`confirmSwitch`、`completeSwitch` 全程无 stopAll——grep 证实全模块 stopAll 调用点仅 `abort:304` 一处）→ T2 写门重开后，任何未被写门覆盖的源库写入（`OrmMigrationWriteGate` 仅拦截本 JVM ORM DML 提交入口，源库上的遗留实例/直连/第三方写入不受控）被仍在运行的链捕获 → 链 sink 以 keyed upsert 写入已晋级的线上目标表 → 迁移前旧值覆盖业务新值；同时 M 个 daemon 线程、M 条 binlog 连接、M 组 sink 连接在正常成功路径上永久滞留（长驻服务进程内无任何回收点），源库下线后 Debezium 断连重试风暴持续打日志。交叠后果：主路径资源泄漏 + 旧事件无限期回放到已切换的目标库。
- **现状**: javadoc 声称"阶段推进由 stopAll 停止"，实现中 VERIFY/SWITCH/DONE 三个推进点均未调用；`runningJobs` 句柄在 DONE 后永久保留但无人消费。
- **风险**: 正常成功路径即泄漏（P0 判级依据）；binlog 连接与 2PC ledger 写入无限期持续；未受写门管控的源端写入形成静默双向分叉通道，直接侵蚀 SWITCH 的数据正确性承诺。
- **建议**: 在 VERIFY 门禁通过后（lag=0 已达成、链已无事可做）或 confirmSwitch 内显式 `incrementalManager.stopAll()` 并 join（配合 C6-003 的可验证终止）；`completeSwitch` 增加"runningJobs 必须为空"的 fail-fast 门禁。复用 `RunningChainJob.join(millis)` 既有设施。
- **失效面枚举**（F3 同族清扫）: ① `abort:296-313`——唯一调用 stopAll 的路径，已配对；② `runVerify:210-251`——未配对（本发现）；③ `confirmSwitch:257-274`——未配对（本发现）；④ `completeSwitch:279-290`——未配对（本发现）；⑤ `resume:151-170` 对 INCREMENTAL/VERIFY/SWITCH 相位不做任何链生命周期动作（进程重启后链已随进程消亡，句柄丢失属既定边界，已核）；⑥ 同族线程创建点全仓唯一（基线 C4a：`IncrementalJobManager.java:176`），无其他裸线程兄弟需清扫。
- **信心水平**: 确定（调用面 grep 全量核实；唯一不确定项是"源库在 DONE 后是否仍可能收到写门外写入"——就管道自身而言不设防，属未定义边界）
- **误报排除**: 不是"已声明的边界"——类 javadoc 明文声明阶段推进会停止链作业，实现与契约不符（守卫缺失而非文档化限制）；也不是 pubsub fire-and-forget 类已声明的能力边界。
- **复核状态**: 未复核
- 复核状态: 保留(P0)｜新发现（同案：本单元 C3-003 同根合并为单一工单，本条为主登记按 P0 即时通道处置，裁定见文末「维度复核结论」）。P0 四环穷举核验全部成立：① stopAll 全仓 grep（main+test）唯一调用点=abort:304，runVerify/confirmSwitch/completeSwitch 逐方法通读无任何链生命周期动作，resume 对 INCREMENTAL/VERIFY/SWITCH 仅 return jobId，无 beans.xml 装配点/destroy 钩子/thread.interrupt；② javadoc 契约 L41-42 + L198-199 原文核对，契约明文覆盖「阶段推进」与「SWITCH 收敛」两时机，实现均缺；③ 写门 OrmMigrationWriteGate 自述「原子生效于本 JVM 所有 DML 提交入口」，本 JVM ORM DML 之外不受控；④ 链线程 daemon=true（L183）持 Debezium 嵌入式 engine 与源库 binlog 连接，cancel 纯协作式。补强证据（初审未提）：MigrationPhase.ALLOWED 中 SWITCH→{DONE} 唯一出边，confirmSwitch 之后 abort 被 checkTransition 拒绝——成功路径自 SWITCH 起链作业在进程内彻底没有任何停止手段。

### [C6-003] stop 机制终止不可验证：cancel 后不 join/不中断/无超时，stopAll 清空句柄销毁失败证据，abort 立即开门

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:176-243`；`nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:296-313`
- **证据片段**:
  ```java
  public void stopAll() {
      for (RunningChainJob job : runningJobs.values()) {
          job.stop();          // 仅 source.cancel()，协作式标志位
      }
      runningJobs.clear();     // 句柄与 failure 证据一并丢弃
  }
  ...
  public void stop() { source.cancel(); }
  public void join(long millis) throws InterruptedException { ... }   // 全模块无调用点（grep 证实）
  // OrmMigrationOrchestrator.abort：
  if (manager != null) manager.stopAll();
  if (OrmMigrationWriteGate.isClosed()) OrmMigrationWriteGate.setClosed(false);  // 未验证终止即开门
  ```
- **严重程度**: P1
- **并发场景**: abort/编排调用线程 vs 链作业后台线程（可能阻塞在 sink 的 2PC 提交 JDBC 写、Debezium 事件处理或 binlog 网络读上）。
- **触发时序**: T1 链线程正处于 `env.execute` 循环内 sink 两阶段提交的 JDBC 阻塞写（目标库网络抖动，socket 超时前不返回，cancel 标志位无机会被检查）→ T2 编排线程执行 `abort()`：`stopAll()` 置位 cancel 并 `runningJobs.clear()`，随后立即 `setClosed(false)` 开写门、相位落 ABORT → T1 的阻塞写最终完成，sink 提交最后一批事件写入目标表——发生在 ABORT 已落账、写门已重开之后；该次失败（若提交抛错则写入 `failure` AtomicReference）随 `runningJobs.clear()` 永久不可观测。线程、binlog 连接、sink 连接在取消路径上的滞留时长无上界且外部无法检测。
- **现状**: 停止语义是纯协作式标志位 + 订阅 cancel，无 interrupt 兜底、无 join/超时等待（`join` 已实现但无调用点）、句柄map 清空后终止状态与迟到失败均不可观测。
- **风险**: 取消路径资源滞留无上界（P1 判级依据）；abort"开门即回滚无损"的承诺建立在未验证的终止之上；迟到提交污染目标表且无任何留痕。
- **建议**: `stopAll` 改为 cancel → 有界 join（复用既有 `join(millis)`）→ 超时未终止则 `thread.interrupt()` 并保留句柄到独立"已停止（未确认）"登记直至线程退出；`abort` 在 join 完成后再开写门；`failure` 观测随句柄一并保留。
- **失效面枚举**（F3 同族清扫）: ① `RunningChainJob.stop`/`stopAll`——本发现主体；② `RunningChainJob.join`——已实现未接线（全模块 grep 无调用），同属本发现失效面；③ `RunningChainJob.getFailure`——编排器无轮询点（见 CL-018），失败观测面同源缺失；④ db-migration 执行器无后台线程，无同族兄弟；⑤ lettuce 侧 `LettucePubSubService.doStop` 的 `conn.close()` 是同步语义，无同族问题——已核。
- **信心水平**: 确定（机制缺口为静态事实；具体交错依赖一次目标库阻塞，属可具体化触发链）
- **误报排除**: 非平台线程池设施（nop-kernel executor）内部问题——裸线程生命周期完全在本审计根手写代码内；非测试代码简化模型。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（关联：本单元 C3-002/CL-018 失败观测面同源互补不并案、修复同批；跨单元家族关联 WI 1.2 MA-07「Debezium 停止契约无终结保证」——不同模块不同代码，登记家族关联不并案）。复核回码全部成立：stopAll L201-206 cancel 后 clear 丢弃 failure 证据；RunningChainJob.stop 仅 source.cancel()（L235-237）；join(millis) 全模块零调用（grep 复现）；getFailure 零调用；主源码无 thread.interrupt（唯一命中为 IncrementalLagProbe 自我中断恢复）；abort L307-308 stopAll 后未等待终止立即开写门。链线程阻塞于 sink 2PC JDBC 提交时 cancel 标志不可达、迟到提交在 ABORT 落账并开门后写目标表的时序成立。

### [C6-004] LettucePubSubService 订阅/取消 check-then-act 竞态：陈旧空判unsubscribe 杀死并发新订阅，双重 cancel 杀死重建订阅

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:72-94,190-200,127-139`
- **证据片段**:
  ```java
  public synchronized IMessageSubscription subscribe(String topic, ...) {
      StatefulRedisPubSubConnection<String, Object> conn = getOrCreateConnection();
      SubscriptionEntry entry = subscriptions.get(topic);        // (a) 快照读取
      if (entry == null) { ... conn.sync().subscribe(topic); ... }
      else { entry.addListener(listener); }                      // (b) 不重发 SUBSCRIBE
      return new PubSubSubscription(topic, entry, listener);
  }
  public void cancel() {                                          // 不同步（非 synchronized）
      cancelled = true;
      entry.removeListener(listener);
      if (entry.isEmpty()) {                                     // (c) 陈旧空判
          StatefulRedisPubSubConnection<String, Object> conn = pubSubConnection;
          if (conn != null) conn.sync().unsubscribe(topic);
          subscriptions.remove(topic, entry);
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: 任意业务线程对同一 topic 并发执行 `subscribe`（synchronized 方法）与 `PubSubSubscription.cancel`（非同步）。
- **触发时序**: 交错一（新订阅被静默杀死）：T1 执行 cancel 至 `removeListener`（listeners 空、Lettuce adapter 已移除）→ T2 进入 synchronized `subscribe`，在 (a) 读到尚未移除的旧 entry，随后在 (b) `addListener` 重新挂回 adapter（依赖"Redis 侧仍订阅"的前提，不重发 SUBSCRIBE）→ T1 恢复执行 (c)：`isEmpty()` 在 T2 加入后本应为 false，但若 T1 的 (c)(d)(e) 整体落在 T2 的 (a)(b) 之间（T2 持有旧 entry 引用、map.remove 尚未发生或刚发生），T1 仍按空判执行 `unsubscribe(topic)` 并移除 map 条目 → T2 的订阅返回成功但 Redis 侧已退订：无报错、永无消息。交错二（重建订阅被迟到 cancel 杀死）：T1 对同一 sub 重复/迟到 cancel（`cancelled` 标志不设重入守卫），此时 map 中已是 T3 新建的 entry 并已 `subscribe`——T1 的 `conn.sync().unsubscribe(topic)` 直接退订新 entry 的频道，且两参 `remove(topic, oldEntry)` 不移除新 entry → map 显示在订、Redis 实际退订，消息静默中断。交叠后果：订阅状态三方（ConcurrentMap、Lettuce adapter、Redis SUBSCRIBE 状态）失配，消费方静默丢消息。
- **现状**: `cancel` 无 synchronized/无重入守卫；空判 (c) 与 map 移除 (e)、Redis 退订 (d) 非原子；`subscribe` 对已存在 entry 不重发 SUBSCRIBE，依赖 Redis 侧状态推断。
- **风险**: 动态订阅管理场景（运行期增减消费者）下罕见但可具体化的静默消息丢失（P1 判级依据：有明确触发链的竞态）；无资源泄漏但有状态失配残留。
- **建议**: 将 per-topic 的 entry 创建/订阅/退订/移除收敛为同一把 per-topic 锁（或对 `subscribe`/`cancel` 统一 synchronized 在 service 监视器上）；`cancel` 增加 `cancelled` 重入守卫；entry 移除前以 `listeners.isEmpty()` 在锁内复查。复用平台 `CycleSynchronizer` 无必要，普通锁即可。
- **失效面枚举**（F3 同族清扫）: ① `subscribe:72-94` 首订路径（entry==null 分支在 synchronized 内，自身安全——已核）；② `subscribe` 已存在 entry 分支 (b)——交错一受损面；③ `PubSubSubscription.cancel:190-200`——两处交错主体；④ `suspend:213-218`/`resume:221-226`——suspend 不退订属设计语义（挂起保留频道），resume 仅挂 adapter，若 suspend 期间 entry 被 cancel 路径整体移除，resume 会把 listener 挂进孤儿 entry（与交错一同根，纳入本发现修复面）；⑤ `doStop:112-117`——同步 close，安全；⑥ `SubscriptionEntry.addListener/removeListener:127-139` 内部的 size==1 判定是同一 check-then-act 复合状态的组成部分，随本发现一并修复。
- **信心水平**: 确定（交错序列可在两线程下确定性重放；`subscribe` synchronized 与 `cancel` 非同步的不对称是静态事实）
- **误报排除**: 非"ConcurrentHashMap 原语使用"误报类——问题不在 map 本身而在跨 map/adapter/Redis 三方状态的复合操作无守卫（F10 守卫缺失）；F8 配对（addListener/removeListener、subscribe/unsubscribe）表面成对但异常与并发路径失配。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜已知未修复（同案：`ai-dev/audits/check/nosql-cdc.md` 2026-08-19 [P2]「PubSubSubscription.cancel 与 subscribe 并发竞态可孤立新订阅者」同位点首立，现行代码逐点核对未修复——cancel 无同步 L190-200、subscribe synchronized L72、陈旧空判 L193；check2 2026-08-24 处置轮未覆盖该条，其已修复面为 cancelled 标志共享污染，现行 PubSubSubscription 已改 per-subscription volatile 标志，与本条无冲突）。本轮扩展面为首立：交错二（cancelled 无重入守卫的迟到/重复 cancel 杀死重建订阅，L191 置位后无条件继续 + 两参 remove(topic, oldEntry) 不移除新 entry）与 suspend/resume 孤儿 entry 面。P1 判级依据 CRA 校准「罕见但可具体化的竞态（有明确触发链）」——两交错均可两线程确定性重放、后果为静默丢消息；历史 P2 系旧口径，不沿用。

### [C6-005] refreshConfig 收缩连接池不排空：在用共享连接被立即 close，并发 get() 可取出已关闭连接

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:92-100,61-79`；`nop-kernel`（证据引用）`io/nop/commons/concurrent/RoundRobinSupplier.java`（resize/get）
- **证据片段**:
  ```java
  @Override
  public void refreshConfig() {
      if (connectionSupplier != null) {
          int n = config.getConnectionPoolSize();
          if (n <= 0) n = 1;
          connectionSupplier.resize(n);          // 运行期收缩，无排空/宽限
      }
  }
  // RoundRobinSupplier.resize：缩小分支立即 closeNext(toClose, size) 关闭被裁剪连接
  // get()：读旧数组快照 → 取出对象，与 resize 的 close 之间无任何握手
  ```
- **严重程度**: P2
- **并发场景**: 配置刷新线程（IConfigRefreshable 回调）vs 全部业务线程的 `getAsyncCommands()/getSyncCommands()` 往复取用（连接为固定 N 条共享长连接，RoundRobin 轮转，非借还制）。
- **触发时序**: T1 业务线程调用 `getSyncCommands()`，`RoundRobinSupplier.get()` 读取旧数组（length=5）并取出下标 3 的共享连接，尚未发起命令 → T2 配置刷新线程 `refreshConfig()` 将池从 5 收缩到 2，`resize` 交换数组后立即 `closeNext` 关闭旧数组下标 2..4（含 T1 刚取出的连接）→ T1 在该连接上发起命令：显式 close 的连接不走 autoReconnect（`ClientOptions.autoReconnect(true)` 仅覆盖网络断连，不覆盖用户主动 close）→ 命令报 "Connection closed" 类错误。交叠后果：收缩瞬间约 (旧-新)/旧 比例的取用命中死连接，单次命令失败、随后取用恢复（新数组仅含存活连接）——有界、自限，但对外表现为配置变更瞬间的瞬时错误尖峰。
- **现状**: 收缩路径直接关闭共享连接，无"先摘除、待在用命令归零再关闭"的宽限；`get()` 与 `resize` 的交换窗口无握手。
- **风险**: 配置热更新期间的瞬时命令失败；若上层把该异常当连接池耗尽处理可能放大（重试风暴）。按口径不构成 P1：触发条件罕见（运行期缩容配置）、后果单次命令失败、无持续泄漏。
- **建议**: 收缩时将被裁剪连接先移出轮转数组、延迟异步关闭（如经平台 `IAsyncCache`/GlobalCacheRegistry 式延迟回收，或简单 `CompletableFuture.delayedExecutor` 宽限窗），或收缩前经 `AsyncJoinType` 等待在用命令排空；同时在 `getConnection()` 的 javadoc 显式声明返回共享连接、调用方不得 close。
- **失效面枚举**（F3 同族清扫）: ① `refreshConfig`（唯一 resize 调用点——已核）收缩分支——本发现；② `resize` 扩容分支——只增不改旧连接，安全——已核（扩容中途失败仅关闭本次新建资源，实现注释已自证）；③ `getAsyncCommands:61-68`/`getSyncCommands:72-79`/`getConnection:82-89`/`createPubSubConnection:140-146`——取用侧四入口全部暴露于同一窗口（pubsub 独立建连，不受 resize 影响——已核豁免）；④ `doStop:128-138`——进程停机路径 client.shutdown 统一回收，安全；⑤ `RoundRobinSupplier` 其余使用方（nop-kernel 平台内部）按口径不在本维度重审，仅登记使用方式缺陷。
- **信心水平**: 确定（机制缺口静态成立；发生频率取决于运行期缩容配置操作频率）
- **误报排除**: 非"平台基础设施自身不重审"的越界报告——发现落在使用方 `refreshConfig` 的使用方式（对共享长连接池做无排空热收缩）上，RoundRobinSupplier 仅作证据引用；非有界配置与规模声明矛盾类误报（池有界，问题在关闭时机）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜已知未修复（双登记同案：① `ai-dev/audits/check/nosql-cdc.md` 2026-08-19 [P3]「refreshConfig → RoundRobinSupplier.resize 会立即关闭仍在使用的连接」同位点——其扩容失败子面已由 commit 22129fa9f7 修复（现 RoundRobinSupplier L74-77 注释自证），缩容面未修；② WI 2.1 `nop-kernel-concurrency-C1-001`（P1）设施侧主处置项「resize 收缩路径锁外关闭旧数组尾段：并发 get 取得已关连接（Lettuce 生产链可达）」）。按 campaign「使用方↔设施两面，非重复立项」惯例：设施修复主责随 2.1 P1 工单，本单元保留 P2 作使用方式面登记（触发面=运行期缩容配置、单次命令失败自限、无持续泄漏，P2 口径成立），工单合并至 2.1 主处置项。回码确认：refreshConfig L92-100 → resize 缩容分支 synchronized 内换数组、finally 锁外 closeNext 立即关闭，get() 无握手。

## 维度集外线索（overlay C2/C4/C5/C9）

（编号续接 nop-persistence-peri-CL-018；CL-001…017 已被占用）

### [nop-persistence-peri-CL-018] （C9/F5）链作业死亡不可见：getFailure 无轮询点，死链使 lag=0 门禁平凡通过

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:177-181,230-233`；`OrmMigrationOrchestrator.runVerify:210-251`
- **线索**: `RunningChainJob` 的 run 线程把异常存入 `failure` AtomicReference，其 javadoc 明言"fail-fast：编排器轮询到 failure 即阶段失败"，但全模块（main 源码）grep 无任何 `getFailure()` 调用点；`runVerify` 的两道门禁（写门关闭 + `awaitLagZero` 静默期快照比对）都无法区分"链活着且追平"与"链早已死亡"。链死亡后 offset 不再前进、ledger 不再新增提交，lag=0 平凡成立 → VERIFY/SWITCH 照常推进，未重放的增量事件静默丢失，属 F5"挂死生产者对健康检测不可见"在 C9 的失效面（吞异常 → 状态机不一致）。建议复核时并入 C6-002/C6-003 的停止生命周期重构：相位推进/门禁处轮询 failure 并把"全部链存活"作为 VERIFY 前置。
- **性质**: 维度集外线索（C9 状态一致性/F5 可检测性），不计入 C6 发现数。

### [nop-persistence-peri-CL-019] （C5/F7）ConsistencyVerifier 全量 checksum 将源+目标双侧 pk→hash 全量驻留内存

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/ConsistencyVerifier.java:104-118,131-181`
- **线索**: `verifyChecksum` 先 `scanHashes(源)`、再 `scanHashes(目标)`，各自把「采样行的 pk 串 → 64 字符 hash」全量 accumulate 进内存 HashMap 后才做差集；`isVerifyFullChecksum()` 时 denominator=1 即每行都采样。管道其余面（FullCopyBatchJob 分页流式、findPage 2000 行/页）均为流式，而校验面一次载入双侧全量元数据——每行约 100–200 字节，GB 级表（该管道的声明搬迁对象）在亿行量级即触发数 GB 堆占用，源/目标两份并存叠加。属 F7 OOM 同族（分页缺失/全量物化），与 owner 文档规模包络的比对留待 C5 维度复核；若确认为包络内缺陷应升级为正式发现。
- **性质**: 维度集外线索（C5 内存），不计入 C6 发现数。

## 维度复核结论

- 复核：CRA WI 2.5 C6 维度独立复核员（轮外复核，未参与初审与深挖）
- 复核日期：2026-10-18
- 口径：`ai-dev/skills/concurrency-resource-audit-prompt.md`（严重度判级 + 误报校准 + F1–F11）
- 查重底账：`ai-dev/audits/cra-index.md`（WI 1.1–2.4 台账）+ 历史审计（`check/nosql-cdc.md`、`check2/nosql-cdc.md`、`check/nop-commons.md`、WI 2.1 C1 报告、本单元 C3-async-chains.md 复核节）
- 回码基准：live code（审计工作区与 master 对 nop-orm-migration/nop-nosql diff 核实同源）

### 逐条判定表（C6-001…005）

| 发现 | 初判 | 复核判定 | 最终严重度 | 现状标注 | 同案/关联 |
|---|---|---|---|---|---|
| C6-001 startFallbackChains 部分启动孤儿链 | P1 | 保留 | P1 | 新发现 | 同案并入本单元 C3-001（已保留 P1，工单合并计一次） |
| C6-002 成功路径永不停止链作业 | P0 | **保留（P0 维持）** | P0 | 新发现 | 同案合并本单元 C3-003（本条为主登记，裁定见下） |
| C6-003 stop 终止不可验证 | P1 | 保留 | P1 | 新发现 | 关联 C3-002/CL-018（互补不并案）；家族关联 WI 1.2 MA-07（不同代码不并案） |
| C6-004 PubSub 订阅/取消竞态 | P1 | 保留 | P1 | 已知未修复 | 同位点 check/nosql-cdc.md 2026-08-19 [P2]（未修复） |
| C6-005 refreshConfig 收缩不排空 | P2 | 保留 | P2 | 已知未修复 | check/nosql-cdc.md 2026-08-19 [P3] + WI 2.1 nop-kernel-concurrency-C1-001 [P1]（设施主责） |

### C6-002 P0 穷举核验记录（本单元唯一 P0）

四环 + 一项补强，全部以 live code 核实成立：

1. **stopAll 调用点穷举**（全仓 grep main+test，排除 `_gen`/target；nop-ai-gateway `ChannelConnectorManager.stopAll` 为异类不相关）：nop-orm-migration main 源码唯一调用点 `OrmMigrationOrchestrator.java:304`（abort 内）。`runVerify`（L210-251）、`confirmSwitch`（L257-274）、`completeSwitch`（L279-290）逐方法通读：无 stopAll、无 source.cancel、无任何链生命周期动作；`resume`（L151-170）对 INCREMENTAL/VERIFY/SWITCH 仅 return jobId；无 beans.xml 装配点、无 destroy/shutdown 钩子；主源码无 `thread.interrupt`（grep 唯一命中为 IncrementalLagProbe 自我中断恢复）。
2. **javadoc 契约原文**：`IncrementalJobManager.java:41-42`「链作业通过 env.execute 在独立线程长驻运行；ABORT/阶段推进由 {@link #stopAll} 停止（cancel source → run 循环退出）」+ `:198-199`「停止全部链作业（ABORT/SWITCH 收敛后由编排器调用）」。契约明文覆盖「阶段推进」与「SWITCH 收敛」两个时机，实现两处均缺——契约-实现相反成立。
3. **写门拦截范围**：`nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmMigrationWriteGate.java`（全文）——检查点仅 OrmSessionImpl flush 族 DML 提交入口，javadoc 自述「原子生效于本 JVM 所有 DML 提交入口」；其他 JVM 实例、直连 JDBC、第三方写入均不受控。DONE 重开后源端残写被链 sink 回放进已晋级目标表的通道成立。
4. **链线程 daemon 与连接持有**：`IncrementalJobManager.java:183` `thread.setDaemon(true)`；链线程执行 `DebeziumCdcSourceFunction.run`（L226-319），持有 DebeziumMessageSource/嵌入式 engine（源库 binlog 连接）与 offset store；`cancel()`（L331-344）纯协作式（标志 + latch + source.stop，无中断）。**一处精确化（不影响判级）**：sink 的 JDBC 连接经共享 jdbcTemplate 按操作借还（非专属长连），持续滞留清单为 daemon 线程 + binlog 连接 + 嵌入式 engine + offset store + checkpoint 执行器。
5. **补强（初审未提）**：`MigrationPhase.ALLOWED` 中 SWITCH 唯一出边为 DONE（`SWITCH, Set.of(DONE)`），confirmSwitch 之后 `abort()` 被 `checkTransition` 拒绝（SWITCH→ABORT 非法迁移）——成功路径自 SWITCH 起链作业在进程内**彻底没有任何停止手段**（abort 不可达 + DONE 不停链），泄漏无条件且不可自救。

**P0 维持结论**：校准 P0 项「主路径资源泄漏（正常成功路径即泄漏）」逐字命中——每次成功迁移泄漏 M 条链（线程/binlog 连接/engine/checkpoint 基础设施），进程内零回收点，随迁移次数单调累积；且链非惰性，DONE 后仍消费 binlog 并向已晋级目标表 2PC 提交（条件面后果）。campaign 先例（WI 1.2 唯一 P0：Kafka 2PC producer 按作业终止泄漏 producer+网络线程+缓冲池）支持按作业级泄漏判 P0，本条泄漏发生在成功路径，严于该先例的终止路径。**不降级。**

### C6-002 ↔ C3-003 关系裁定

**同案合并**（同根因、同修复点、同 javadoc 证据），非不同失效面各自成立：

- 共同根因：成功收敛路径（VERIFY→SWITCH→DONE）缺失 stopAll 停链，javadoc 契约与实现相反。
- 共同修复点：confirmSwitch/completeSwitch 停链 + 有界 join（配合 C6-003 终止可验证化同 diff）。
- 面的差异仅是观察视角：C3-003 以幽灵写者为主判级对象（条件面——依赖写门外源端残写，其复核已注明「幽灵写后果依赖收敛窗口内源端残写（条件面），资源滞后面为成功路径无条件面」）；C6-002 以无条件资源滞留为主判级对象，恰落在 C6 校准 P0 项原文上。
- **处置**：合并为单一修复工单，按 **P0 即时通道**处置（发现即修，不入 Phase 8 批量），**C6-002 为主登记**；C3-003 的 C3 维度 P1 复核保留结论作为维度面登记不撤销，工单去重后不重复计数。两轮判级差异系主判级对象选择不同，各自在所属维度口径内成立，合并工单取更严判级 P0。

### 同案命中清单（跨维度/跨报告去重）

| 本轮发现 | 同案条目 | 关系 | 工单处置 |
|---|---|---|---|
| C6-001 | 本单元 C3-001（P1 已保留） | 同根同站点（startFallbackChains 无补偿），C6 泄漏面与 C3 异步面同一修复 | 合并计一次，主登记随 C3-001（含 round4 relocate 同名扩展面） |
| C6-002 | 本单元 C3-003（P1 已保留） | 同根（成功路径不停链），本条 P0 主登记 | 合并单一工单，P0 即时通道 |
| C6-004 | check/nosql-cdc.md 2026-08-19 [P2] | 同位点（cancel/subscribe 竞态）未修复；本轮交错二 + suspend/resume 孤儿 entry 面为首立扩展 | 同工单修复，现状标已知未修复 |
| C6-005 | check/nosql-cdc.md 2026-08-19 [P3]；WI 2.1 nop-kernel-concurrency-C1-001 [P1] | 同缺陷使用面+设施面双前案（扩容子面已修 commit 22129fa9f7，缩容面未修） | 设施主责随 2.1 P1 工单；本单元 P2 使用面登记联动验证 |

与 cra-index P1 台账专项比对结论：nop-stream 单元 C6 面（1.2 C6-004→MA-02、1.3 C6-007）、WI 1.x 资源泄漏族（1.4 C4-001 注册表泄漏、1.6 C6-002/C6-003/C7-001、1.7 C6-001/C6-002/C7-001/C7-003、2.2 C6-001~004、2.3 C3-007、2.4 C6-001/C3-001）与本维度 5 条**均无同案**——nop-orm-migration/nop-nosql-lettuce 为 WI 2.5 首查代码面；唯一跨单元同案为 C6-005 设施面（WI 2.1 C1-001，上表已列）。WI 1.2 MA-07（Debezium 停止契约无终结保证）与 C6-003 同族不同模块（nop-stream connector vs nop-orm-migration 自建链管理），登记家族关联不并案。

### 复核统计

- 判定：5 条全保留（保留 5 / 降级 0 / 驳回 0）。
- 最终严重度分布：**P0×1（C6-002 维持 P0，即时通道）**、P1×3（C6-001/003/004）、P2×1（C6-005）。
- 现状标注分布：新发现 3（C6-001/002/003）、已知未修复 2（C6-004、C6-005）、已修复 0。
- 事实修正：仅 C6-002 证据中「M 组 sink 连接」精确化为共享池按操作借还（不影响判级）；其余 4 条无事实修正。

## 最终保留项

| 保留 ID | 最终严重度 | 一句话 | 修复通道 |
|---|---|---|---|
| C6-002（主登记，合并 C3-003） | P0 | 成功路径永不停止增量链：DONE 后 M 条链（daemon 线程/binlog 连接/engine/checkpoint 执行器）永久滞留并继续回放写目标表；SWITCH 后 abort 不可达，进程内无任何停止手段 | 即时通道（发现即修）：confirmSwitch/completeSwitch 停链 + 有界 join；completeSwitch 增加 runningJobs 空 fail-fast 门禁 |
| C6-003 | P1 | stopAll 纯协作式无 join/无中断/无超时，clear 丢弃 failure 证据，abort 未验证终止即开门 | Phase 8 批量（与 C6-002 修复同 diff：cancel→有界 join→超时 interrupt，句柄保留至线程退出，abort 后置开门） |
| C6-001（合并 C3-001） | P1 | startFallbackChains 部分启动失败无补偿回收：孤儿链滞留 + 新 jobId 双代并存双写目标表 | Phase 8 批量（与 C3-001 工单合并：catch 后回收 started、相位后翻） |
| C6-004 | P1 | PubSub subscribe(synchronized)×cancel(无同步) check-then-act 竞态：陈旧空判退订杀死并发新订阅、迟到 cancel 杀死重建订阅，三方状态失配静默丢消息 | Phase 8 批量（cancel 入同一把锁 + cancelled 重入守卫 + 锁内空判复查；一并覆盖 check/nosql-cdc.md 2026-08-19 [P2] 未修复面） |
| C6-005 | P2 | refreshConfig 缩容经 resize 无排空热关闭共享连接，取用窗口内瞬时命令失败 | Phase 8 裁决（使用方式面登记；设施修复主责随 WI 2.1 nop-kernel-concurrency-C1-001 [P1] 工单，本单元联动验证 Lettuce 链） |

**终止信号**：本维度复核后存在 P0/P1 保留项，维持初审「不输出整维度无 P0/P1 终止信号」口径。
