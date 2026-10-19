# C8 背压、流控与过载行为 — nop-persistence-peri（WI 2.5）

- 审计维度：C8（背压、流控与过载行为）
- 审计日期：2026-10-19
- 审计员：C8 维度盲审初审员（单批次，Phase 2 收官维）
- 方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md`（高频缺陷族 F1–F11 + C8 正文 + 附录 A）
- 基线：`_tmp/cra-baseline-nop-persistence-peri.txt` C8a/C8b 节
- 状态：初审完成（未复核）

## 审计范围与口径

**单批次全源码根覆盖声明**：本批次一次性覆盖以下全部 10 个源码根（按基线 C8 节圈定 + 人工补扫，全部 root 均已实际读取/定向扫描，无抽查跳过）：

| ROOT | 文件数/行数 | C8 审计面结论 |
|---|---|---|
| `nop-persistence/nop-dbtool/nop-dbtool-core/src/main/java` | 7f/1,832 | 无生产-消费边界：schema 发现/diff 为启动/工具路径，物化规模受库 schema 元数据约束（表×列），无队列/扇出面。非降档豁免，是"无过载面" |
| `nop-persistence/nop-db-migration/src/main/java` | 72f/3,877（`_` 前缀跳过） | 无过载面：DDL/data-change executor 按迁移模型（配置有界）构造语句执行，无批收集、无事件扇出、无队列；history 读取按迁移版本数有界 |
| `nop-persistence/nop-orm-migration/src/main/java` | 20f/3,462 | 有流控面，已逐点核验（见下） |
| `nop-persistence/nop-cdc/nop-cdc-core/src/main/java` | 1f/11 | **零审计面登记**：唯一文件 `CdcConstants.java` 为空标记接口，无行为可审 |
| `nop-persistence/nop-nosql/nop-nosql-core/src/main/java` | 19f/1,083 | 接口/配置层；`INosqlQueue` 无容量旋钮面（C8-006） |
| `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java` | 18f/2,377 | **过载面重点**：PubSub 消费链（C8-001）、forEachItem 拉全量（C8-005）、Queue（C8-006）；RateLimiter 为 tryAcquire-only 令牌桶（无 permit 泄漏面） |
| `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java` | 6f/726 | 写出批上界缺失（C8-003） |
| `nop-persistence/nop-orm-geo/src/main/java` | 12f/700 | 无过载面：方言 type handler/启动初始化，无批面 |
| `nop-persistence/nop-orm-pdm/src/main/java` | 9f/1,654 | 无过载面：PDM 模型解析器（`normalizeRefInfo` do-while 每轮至少 remove 一个元素，可终止），启动期模型有界 |
| `nop-persistence/nop-orm-rpc/src/main/java` | 1f/913 | 批量加载/写出 chunk 面（C8-004） |

**orm-migration 流控面逐点核验（基线 C8b 线索人工复现）**：

- `FullCopyBatchJob.copyTable`（`FullCopyBatchJob.java:86-96`）将 `throttleBatchSize`（默认 1000）/`throttleConcurrency`（默认 1）/`throttleRateLimit`（默认 0 = 不限速）真实接线到 nop-batch `BatchTaskBuilder` 的 `batchSize/concurrency/rateLimit`（L89-92，rateLimit>0 才设置）。**限流旋钮委托闭环在代码层面成立**，与 javadoc 设计 §4.7"不新造限流器"一致；rate-limit 缺省 0 为配置语义（运营显式选择不限速），不报缺陷。
- 读源为 `JdbcPageBatchLoaderProvider` offset 分页（查询成功后推进游标），单批物化 ≤ batchSize 行，有界。
- `ShardedUpsertConsumerProvider.consume` 按 chunk（≤ batchSize）分组 JDBC batch upsert，有界。
- 增量回退拓扑"每目标分片一个链作业、每个作业全订阅源表、M 倍源事件消费"为 **javadoc 已声明的 v1 边界**（`IncrementalJobManager.java:33-37`，引用设计 §4.4，"设计明示 v1 接受"）——按口径纪律第 6 条（已声明边界非缺陷）不计入发现；其线程治理面另登记 overlay CL-023。
- `ConsistencyVerifier` 校验拉全量面 → C8-002。

**降档口径应用**：dbtool/db-migration/geo/pdm 为启动期单线程路径，但本维度对这些 root 的结论是"不存在生产-消费边界因而无过载面"（行为性判断），并非以"单线程"豁免任何无背压缺陷； nosql-lettuce/tdengine/orm-rpc/orm-migration 四个 root 的运行期过载面全部按行为性缺陷评估，不受单线程降档影响。

**基线对照**：基线 C8a（queues and buffers）静态扫描为空；C8b 各线索人工复现结果——`JdbcMetaDiscovery.java:457`（PK 列 collect，主键列数有界，不成面）、`OrmModelDiffer.java:130`/`DataBaseUpgrader.java:135`（diff DDL 列表，模型有界，不成面）、`FullCopyBatchJob.java:89`/`MigrationJobConfig.java:415`（限流旋钮，复现确认闭环）、`RateLimiterConfig`/`LettuceRateLimiter`（tryAcquire-only，无泄漏）、`TdEntityPersistDriver.java:152`/`RpcEntityPersistDriver.java:330,373`（复现并升级为 C8-003/C8-004）。

**Phase 2 收官说明**：C8 为本模块 Phase 2 收官维。按盲审纪律未读取同项目其他维度报告，Phase 2 全维度累计发现数由主 agent 汇总；本维度贡献 6 条发现（P0×0 / P1×1 / P2×4 / P3×1）+ 2 条 CL 线索。**本维度存在 P1 发现，不适用整维度终止信号**。

## 发现

### [C8-001] PubSub 消费回调在共享 Redis I/O event-loop 线程上同步执行：无有界交接、无慢消费者策略、多 topic 队头阻塞

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:145-161`（辅证 `:72-94`、`:96-105`、`LettuceRedisConnectionProvider.java:140-146`）
- **证据片段**:
  ```java
  // SubscriptionEntry.message() —— RedisPubSubAdapter 回调
  @Override
  public void message(String channel, Object message) {
      if (!topic.equals(channel))
          return;
      IMessageConsumeContext context = new PubSubConsumeContext();
      for (IMessageConsumer consumer : listeners.keySet()) {
          try {
              consumer.onMessage(channel, message, context);   // 在 Lettuce event-loop 线程内联执行；返回值被丢弃
          } catch (Exception e) {
              consumer.onConsumeFailure(new io.nop.api.core.message.ConsumeFailure(
                      channel, channel, message, 0, e));
          }
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: Lettuce netty event-loop 线程（所有 topic、所有 listener 共享一条 `connectPubSub` 专用连接）与业务消费者线程；发布方经 `sendAsync`（另一条池化连接）。
- **触发时序**: T1 = 订阅者 A 注册的 `IMessageConsumer.onMessage` 执行阻塞 I/O（DB 写、或对同一 Redis 的 `syncGet` 调用），耗时 T_slow；T2 = 发布方对**任意**共享该 pub/sub 连接的 topic 突发 M 条消息。Lettuce `PubSubEndpoint.notifyListeners` 在 event-loop 线程内联迭代 listener（已对 lettuce-core 反编译核验：notifyListeners 直接 `Iterator` 调用 `listener.message`，无 executor 卸载）→ T_slow 期间 event-loop 完全停转：同连接上**所有 topic** 的投递停摆（队头阻塞），消息积压从 socket → 内核收包缓冲 → Redis 服务端该 client 的 output buffer 持续增长 → 超过 Redis `client-output-buffer-limit`（pubsub 类默认 hard limit 32MB）→ **Redis 强制断开该连接** → 重连窗口内已发布消息静默丢失（类 javadoc 声明 fire-and-forget），且重连前所有共享 topic 的消费全部停摆。交叠后果：过载下系统不是有界降级，而是"无界停摆 → 服务端强制断链 → 静默丢消息"。
- **现状**: 消费回调无任何有界交接队列/工作线程池交接，慢消费者行为未声明、无积压深度/丢弃指标；且 `IMessageConsumer.onMessage` 契约的 `CompletionStage`/`ConsumeLater` 返回值被整体忽略（`:154` 未接收返回值），异步消费者无 ack 追踪。
- **风险**: 慢消费者 + 负载上升必然触发跨 topic 停摆；Redis 端断链后进入"丢消息 + 反复重连"循环。作为平台级 `IMessageService` 实现，宿主所有经该服务的 pub/sub 通信共享此故障面。
- **建议**: 在 `SubscriptionEntry.message()` 内将消息交由有界 executor 处理（复用平台 `io.nop.commons.concurrent.executor.IThreadPoolExecutor` + `QueueOverflowPolicy` 声明满时策略），或最小化：为每 topic 配置消费线程 + 有界队列；积压深度/拒绝数暴露指标；`onMessage` 返回 `CompletionStage` 时至少记录未完成即继续投递的语义（warn-once），与类头"绝不静默"声明一致。
- **失效面枚举**（F3 同族清扫）: ① `SubscriptionEntry.message`——本条主体；② `SubscriptionEntry` 每个 topic 一个实例、共享单条 pub/sub 连接（`getOrCreateConnection` L96-105）——队头阻塞面，同一缺陷；③ `IMessageConsumer.onMessage` 返回值丢弃（L154）——同一入口，并入本条；④ 各 topic listener 互不隔离（O(#topics) 次回调/条消息）——并入本条；⑤ `sendAsync` 发布路径（L65-69）有超时配置面（soTimeout=2000 默认）且 publish 为短命令，无同类阻塞面，**不报**；⑥ `LettuceLock`/`LettuceRateLimiter` 无消费者回调，**不适用**。
- **信心水平**: 确定（阻塞机制经 lettuce-core 7.5.2 `PubSubEndpoint.notifyListeners` 字节码核验；Redis 端断链为 `client-output-buffer-limit` 标准行为，具体阈值随服务端配置）
- **误报排除**: 非"原语使用不是问题"类误报——缺陷不在 `addListener` 原语本身，而在业务包装层把无界的用户回调内联进 I/O 线程且未声明慢消费者语义；亦非已声明边界：类 javadoc 仅声明 fire-and-forget 丢消息语义，未声明"消费停摆/服务端断链"这一过载行为。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（同案：本单元 C3-005(P2)/C7-010(P2)/C1-CL-011 同站点投递模型面多维度登记，严重度分歧 P1/P2/P2 留 7.1——本复核判定加入统一裁定记录：**C8 侧意见建议统一 P1**，四点理由见主文件「C8-001 严重度统一裁定意见」节）。复核：字节码级独立反汇编 lettuce-core 7.5.2.RELEASE `PubSubEndpoint.notifyListeners`——List.iterator 直迭代并内联 invokeinterface listener.message，无 executor 卸载，初审机制主张成立；单 pub/sub 连接承载全部 topic（L96-105 + L147 注释自证）成立；`IMessageConsumer.onMessage` 的 CompletionStage 语义为 nop-api-core javadoc 明文契约（IMessageConsumer.java:21-24）而本类 L154 丢弃返回值——该子面无条件成立，任何异步消费者 ack 追踪静默失效；「仓库内未发现生产接线」普查部分成立（无直接 beans 装配，经 `LettuceMessageService.getMessageService`:344-348 惰性工厂暴露，nop-stream-runtime `RemoteInputChannel:254` 为平台 messageService.subscribe 设计消费方）——属部署成熟度条件，非机制反驳）

### [C8-002] ConsistencyVerifier full-checksum 双侧全量 pk→hash 驻内存 + O(N²) 深分页放大（F7 同族）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/ConsistencyVerifier.java:131-151`（`scanHashes` :157-181，`findPage` :183-189，常量 :53-55）
- **证据片段**:
  ```java
  private void verifyChecksum(int denominator, VerifyReport report) {
      Map<String, String> sourceHashes = scanHashes(mapping.getSourceTables(), denominator, report);
      Map<String, String> targetHashes = scanHashes(mapping.getTargetTables(), denominator, report);
      TreeSet<String> allKeys = new TreeSet<>();
      allKeys.addAll(sourceHashes.keySet());
      allKeys.addAll(targetHashes.keySet());
      ...
  ```
- **严重程度**: P2
- **并发场景**: 编排器调用线程单线程执行（`OrmMigrationOrchestrator.runVerify` :210-251），过载面为内存与源库压力，非竞态。
- **触发时序**: 非竞态缺陷，时序为规模推演：`verify.full-checksum=true`（denominator=1，全采样）或大表配高 `sample-ratio` 时，`scanHashes` 将**源侧与目标侧全部行的 pk 串 + 64 字符 SHA-256 hex** 物化为两个 HashMap（百万行级 ≈ 数百 MB，亿行级直接 OOM）；同时 `findPage` 用 `ORDER BY pk` + `offset` 深分页（每页重扫 offset 行），第 k 页代价 O(k×2000)，全表扫描代价 O(N²/2000)——大表上源库 CPU/IO 被校验作业放大数千倍。两侧本已按主键序输出，存在 O(1) 内存的归并流式比对面却未使用。
- **现状**: 全量 checksum 模式一次性物化双侧映射，无分片/溢写/归并流式路径；深分页无游标（keyset pagination）替代。
- **风险**: full-checksum 为显式 opt-in（默认关），故非默认路径 P1；但该旋钮一旦在大表上启用，校验阶段必然 OOM 或拖垮源库，且 VERIFY 处于写门关闭的业务冻结窗口内，校验时长直接放大停写时长。
- **建议**: 两侧均按主键序扫描改为归并流式比对（边扫边比，O(1) 内存）；深分页改 keyset（`WHERE pk > :last`）；或至少按 pk 区间分片循环比对。
- **失效面枚举**（F3 同族清扫）: ① `verifyChecksum`/`scanHashes`（本条主体）；② `sumCounts`（COUNT(*)，无物化，不报）；③ `FullCopyBatchJob` 的 nop-batch 分页读（有界 chunk，不报）；④ `IncrementalLagProbe.offsetsFingerprint`（offset map 为 partition 级有界，不报）；⑤ dbtool `JdbcMetaDiscovery.discover(null,null,"%")` 全库 schema 物化（元数据规模有界，不报）。db-migration/dbtool/geo/pdm 各 root 无同族兄弟（无逐行扫描面），已逐一排除。
- **信心水平**: 确定（内存放大）；很可能（O(N²) 分页在特定方言/优化器下的实际代价）
- **误报排除**: 非"有界声明存在"误报——full-checksum 下物化量无任何上限声明；规模判断按"配置启用即触发"，非超出声明包络的臆测负载。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（同案：本单元 C7-007 同站点同证据同修复——C7 复核已裁定同案合并计一次工单，C8 侧按 C7 复核裁定登记、不另立工单）。复核：verifyChecksum/scanHashes/findPage 双侧全量 Map + TreeSet + offset 深分页逐行核实成立（:131-151/:157-181/:183-189）；opt-in 门核实成立——full-checksum 默认关（Builder :383 + fromMap :159 需显式 true）、sample-ratio 默认 0（:382）、verifyCount 默认 true 但仅 COUNT(*) 无物化，P2 计价依据是配置门而非路径冷热，口径正当）

### [C8-003] TdEntityPersistDriver 批写/批载无 chunk 上界：整批物化为单条字面量内联 SQL

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/driver/TdEntityPersistDriver.java:128-160`（批写）、`:173-206`（批载）；辅证 `model/TdSqlHelper.java:64-71,159-199`
- **证据片段**:
  ```java
  // batchExecuteAsync：整个 flush 批的 saveActions → splitSubTables → 单条 SQL 全量拼接
  Map<String, List<IOrmEntity>> map = splitSubTables(saveActions);
  SQL.SqlBuilder sb = SQL.begin().insertInfo();
  for (List<IOrmEntity> list : map.values()) {
      TdSqlHelper.genBatchInsertSubTableSql(sb, tableMeta, list);   // 每子表一行 VALUES 追加，无上限
  }
  ```
  ```java
  // TdSqlHelper.java:159 —— 驱动不支持参数绑定，谓词/值全部字面量内联
  // TDengine 驱动不支持参数绑定，谓词值全部以转义字面量内联，因此这里没有 binder 参数
  ```
- **严重程度**: P2
- **并发场景**: ORM 会话 flush 批写路径与批加载路径（调用方为 nop-orm `BatchActionQueueImpl.flushAsync`，其按实体聚合后**整批**下传、无批大小拆分）；驱动层单线程执行。
- **触发时序**: 非竞态，规模推演：T1 = 应用在一个 ORM 会话 flush 周期内写/加载 N 个实体（N 由应用行为决定，会话层无上限）；T2 = 驱动将 N 行×全列以**转义字面量**拼进单条 SQL 文本（批载则拼 N 个 OR 的 pk 谓词链，`appendBatchLoadEq` L180-199）→ SQL 文本随 N 线性膨胀，超过 TDengine 单语句长度上限（3.x 默认 maxSQLLength ~1MB）后整批 flush 直接失败；N 未达上限前，单条巨型 SQL 的构建/传输内存与解析开销同样线性放大。
- **现状**: 驱动层无任何批大小上限/chunk 拆分旋钮；上界完全依赖调用方（会话层同样未拆分，见失效面 ④）。
- **风险**: 大批量 flush（批量导入、大事务）在 TDengine 后端必然触顶失败（fail-loud，非静默丢数据），且失败发生在 flush 事务中段，放大"半个成功"恢复面（F2 关联）。
- **建议**: 在 `batchExecuteAsync`/`batchLoadAsync` 内按行数或估算 SQL 字节数 chunk 化（如每 500 行或 512KB 一段），对齐 TDengine maxSQLLength 并暴露配置项；批载 OR 链改 IN 列表或分段 WHERE。
- **失效面枚举**（F3 同族清扫）: ① `TdEntityPersistDriver.batchExecuteAsync` save 路径（本条主体）；② 同方法 delete 路径（L149-157，`genBatchDelete` 字面量内联，同族同修）；③ `TdEntityPersistDriver.batchLoadAsync`（L173-206，OR 链同族）；④ 会话层 `BatchActionQueueImpl.flushAsync`（nop-orm core，不在本模块审计范围，仅登记：整批下传无拆分，是本条上界缺失的放大器）；⑤ `JdbcEntityPersistDriver.batchExecuteAsync`（nop-orm core 兄弟，参数绑定 + JDBC batch，SQL 文本定长——**机制不同不成同族缺陷**，仅登记对照）；⑥ `RpcEntityPersistDriver`（C8-004，同族"整批单请求"面，独立成条）。
- **信心水平**: 确定（单条 SQL 无上界构造）；很可能（maxSQLLength 触顶的具体阈值随 TDengine 版本）
- **误报排除**: 非"驱动差异审美"误报——字面量内联 + 无 chunk 是可触发的行为性过载面（SQL 长度是协议硬上界），不是风格问题；亦非已声明边界：`ERR_TDENGINE_UPDATE_NOT_SUPPORTED` 声明了 update 不可用，但未声明批大小上界语义。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（**复核修正批载面**：触发时序「写/加载 N 个实体、N 由应用行为决定、会话层无上限」的**加载半边不成立**——会话层 `EntityPersisterImpl._batchLoad`（nop-orm :202-215）按 maxBatchLoadSize（entityModel 覆盖值或平台默认 `nop.orm.default-entity-batch-load-size`=1000，OrmConfigs:32-33）splitChunk 分块后才调驱动，集合路径同款分块（CollectionPersisterImpl:103/143），WI 2.4 B9 已核同事实；批载面修正为「≤1000/批但块内字节数仍无界（字面量内联×全列）」。**写路径（本条主对象 batchExecuteAsync）无界维持**：BatchActionQueueImpl.flushAsync :160-199 整 holder 传递 + EntityPersisterImpl :638-653 直传驱动，全链无拆分复核成立。P2 维持依据=写面无界 fail-loud + 载面「有界但边界配置缺陷」。round2 扩展（tag 声明放大 + tag 分歧取首行）经 TdSqlHelper:64-71/73-94/:66 逐行核实成立，随本条工单。同族不并案：WI 2.4 C5-004（分配放大，缺陷不同）、WI 1.7 C8-001/002（nop-batch 参数护栏族，站点不同））

### [C8-004] RpcEntityPersistDriver 批写/批载整批单请求：ids/data 载荷无 chunk 上界

- **文件**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:368-411`（批载）、`:310-337`（批写）、`:339-359`（buildData）
- **证据片段**:
  ```java
  public CompletionStage<Void> batchLoadAsync(ShardSelection shard, Collection<IOrmEntity> entities, ...) {
      String operationName = newEntityAction("batchGet");
      FieldSelectionBean selection = newSelection(propIds, subSelection);
      List<String> ids = entities.stream().map(IOrmEntity::orm_idString).collect(Collectors.toList());
      Map<String, Object> data = new HashMap<>();
      data.put("ids", ids);                      // N 个 id 一次性进单个 RPC 请求，无 chunk
      return invokeRpc(operationName, data, selection).thenAccept(response -> { ... });
  ```
- **严重程度**: P2
- **并发场景**: ORM 批加载/批写出路径调用线程 + 远端 `ICrudBiz.batchGet/batchModify/batchDelete` 服务；RPC 客户端异步派发。
- **触发时序**: 非竞态，规模推演：T1 = 会话批加载 N 个实体（批量关联拉取、大列表渲染）；T2 = 驱动将 N 个 id（批写则为 N 行全字段 `orm_initedValues()`/`orm_dirtyNewValues()`）序列化进**一个** ApiRequest → 请求体/远端反序列化内存/远端 IN 查询规模全部随 N 无界增长；远端响应 N 行数据（含 `subSelection` 递归子表，`:392-404`）一次性物化回调用线程。过载表现 = 巨型请求超时、远端/本地内存尖峰、GC 压力，无降级路径。
- **现状**: 无 chunk 拆分、无最大批量旋钮；`loadAsync`（单实体）也是一次独立 RPC（N+1 面，见 CL-024）。
- **风险**: 大批量操作在 RPC 后端下载荷与时延无界；巨型响应失败后整批加载作废重试，放大对远端的重复压力（重试风暴面）。
- **建议**: 按 id 数（如 500/批）或序列化大小 chunk 化批载/批写，并发度受控（复用平台 `ISemaphore`）；`invokeRpc` 增加批大小上限守卫（超限 fail-fast 并提示配置）。
- **失效面枚举**（F3 同族清扫）: ① `batchLoadAsync`（本条主体）；② `batchExecuteAsync` topoAsc 写路径 `buildData`（L316-323，同族）；③ 同方法 delete 路径 ids 收集（L328-335，同族）；④ `LettuceMessageService.getAll/removeAll`/`putAll`（mget/mset 整批，但为显式键集合 API、键数由调用方持有，且 Redis mget 有协议内分片惯例——降 P3 观察，不独立成条）；⑤ `TdEntityPersistDriver`（C8-003，同族独立成条）；⑥ `JdbcEntityPersistDriver`（参数绑定 JDBC batch，机制不同，不报）。
- **信心水平**: 确定（无上界单请求构造）
- **误报排除**: 非"调用方自担"豁免——`IEntityPersistDriver.batchLoadAsync` 的调用契约（ORM 框架批量拉取）决定了 N 由框架内部行为产生而非应用显式传参，驱动层无上界属于边界配置缺失而非已声明限制。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（**复核修正批载面（本条主对象）**：触发时序「会话批加载 N 个实体 → 驱动将 N 个 id 一次性进单个 RPC 请求」**不成立**——标准调用链 `EntityPersisterImpl._batchLoad`（nop-orm :202-215）以 maxBatchLoadSize（默认 1000）分块后调驱动，N≤1000/请求；原「误报排除」称「N 由框架内部行为产生而非应用显式传参」，而框架内部行为**恰含该分块**，初审遗漏此层（失效面枚举漏列 `_batchLoad`）。修正后口径：批载有会话层默认 1000/请求上界，但驱动层无独立守卫、块内字节数仍无界（行宽×1000 + subSelection 递归 ：401-403）；**批写面（batchExecuteAsync topoAsc/delete 整批下传）无界维持成立**。P2 维持依据=「有界但边界配置缺陷」口径 + 写面无界。WI 2.4 对 RpcEntityPersistDriver 仅 runInSession 同步面范围外交叉引用（其 C2-round2 表 #8），无 chunk 面重复立项；WI 1.x 批量族为家族关联不并案）

### [C8-005] LettuceListOperations.forEachItemAsync LRANGE 0 -1 一次性物化整个列表（同模块分页先例未复用）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceListOperations.java:124-131`
- **证据片段**:
  ```java
  @Override
  public CompletableFuture<Void> forEachItemAsync(Consumer<Object> consumer) {
      return async().lrange(key, 0, -1).thenAccept(list -> {   // 全量拉回，无分页
          if (list != null) {
              for (Object item : list) {
                  consumer.accept(item);
              }
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程 + Lettuce 异步派发线程；响应物化发生在 Lettuce 命令完成回调中。
- **触发时序**: 非竞态，规模推演：列表被用作积压结构（`INosqlQueue`/list 操作同一 Redis list 语义，生产快于消费时长度无界，见 C8-006）→ 任一 `forEachItem` 调用触发 `LRANGE key 0 -1` → Redis 单响应体内序列化全部元素、客户端一次性反序列化为 List → 消费者内存尖峰 O(列表长度)，过载下与积压深度相互放大。
- **现状**: 全量物化无分页；同模块 `LettuceMessageService.forEachEntry`（`LettuceMessageService.java:132-148`）对同类"全量遍历"语义使用 SCAN 分页（limit 100）——**分页先例存在而未复用**；`getRangeAsync(start, maxCount)`（L71-73）分页原语也已存在。
- **风险**: 列表越长单次遍历成本与内存越大；大列表上还阻塞 Redis 单命令执行（LRANGE O(N) 服务端耗时），影响同实例其他操作。
- **建议**: `forEachItemAsync` 改用 `lrange` 按 100/页循环（对齐 `forEachEntry` 的 SCAN 分页先例），或在接口 javadoc 显式声明"仅限小列表"边界。
- **失效面枚举**（F3 同族清扫）: ① `LettuceListOperations.forEachItemAsync`（本条主体）；② `LettuceSetOperations` smembers 读全集合（L101，集合语义常为键级有界，降观察）；③ `LettuceHashOperations` hgetall（L171/251、`LettuceSessionStore.java:37`，hash 为会话/单键级有界，降观察）；④ `LettuceMessageService.forEachEntry/forEachEntryAsync`（SCAN 分页 100，**正面先例，不报**）；⑤ `LettuceQueue.dequeueBatch(maxCount)`（调用方定上界，不报）。
- **信心水平**: 确定
- **误报排除**: 非"findAll 类 API 契约自担"——forEach 是遍历语义而非用户显式请求全量结果集；同模块已证明遍历语义可以分页实现（forEachEntry），本条属一致性缺失的行为面而非契约选择。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现（同案：nop-persistence-peri-CL-015（C3 批次线索）同位点——C3 复核已裁「线索分账、处置归对应维度」，本条为 list 面维度主登记）。复核：`lrange(key,0,-1)` 全量物化（:125-133）、分页原语 `getRangeAsync` 在位（:71-73）、`forEachEntry` SCAN 分页先例对照（LettuceMessageService:132-148）均核实成立；全仓零生产调用方普查成立（grep main+test，仅接口实现与测试 fake），按本单元零调用方缺陷落点 P2 先例（C1-012/C7-015）维持 P2；与 round2 C8-007 非同案确认（单命令全量物化 vs 迭代总范围无界，修复点互异））

### [C8-006] INosqlQueue 队列抽象无容量/溢出策略旋钮：平台 QueueOverflowPolicy 该接未接

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/INosqlQueue.java:14-30`；`nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceQueue.java:27-47`
- **证据片段**:
  ```java
  public interface INosqlQueue {
      CompletableFuture<Void> enqueueAsync(Object item);
      void enqueue(Object item);
      CompletableFuture<Void> enqueueBatchAsync(Collection<?> items);
      void enqueueBatch(Collection<?> items);
      ...
      // 无 capacity / overflowPolicy / maxSize 任何旋钮
  ```
  ```java
  // LettuceQueue.enqueueBatchAsync：整集合单条 RPUSH，无上限
  return async().rpush(key, items.toArray()).thenApply(Functionals.toVoid()).toCompletableFuture();
  ```
- **严重程度**: P3
- **并发场景**: 生产者线程 → Redis list；消费者 `dequeue/dequeueBatch`。
- **触发时序**: 非竞态：生产速率 > 消费速率时队列长度无界增长直至 Redis 实例内存耗尽（或触发 maxmemory 淘汰/拒绝，行为取决于服务端配置，客户端不可见不可控）；无积压深度指标面。
- **现状**: 平台已有 `io.nop.commons.concurrent` 的 `QueueOverflowPolicy`（阻塞/丢弃/异常策略声明）与 `IBlockingQueue` 设施，`INosqlQueue` 抽象未暴露任何对应旋钮；`enqueueBatch` 亦无批上界。
- **风险**: 作为平台队列抽象，调用方无从声明"满时行为"；过载语义完全隐式委托给 Redis 服务端配置。防御性缺口，非现行可触发缺陷（Redis 有界性由部署配置兜底）。
- **建议**: `INosqlQueue` 增加 `maxLen` 选项（LPUSH+LTRIM 或 LLEN 守卫 + `QueueOverflowPolicy` 三态），`enqueueBatch` 增加批大小上限；至少在 javadoc 声明"无界、依赖 Redis maxmemory"边界。
- **失效面枚举**（F3 同族清扫）: ① `INosqlQueue`/`LettuceQueue`（本条主体）；② `INosqlListOperations`/`LettuceListOperations.addAll`（同一无界 list 面，同族并入）；③ `INosqlHashOperations/ZSetOperations` 写路径（键级 TTL/容量由调用方设计，非队列语义，不适用）；④ 平台本地 `IBlockingQueue`（nop-kernel 设施，有界+策略齐备，不报）。
- **信心水平**: 确定（旋钮缺失）；有趣的猜测（实际过载后果取决于部署端 maxmemory 策略）
- **误报排除**: 非"Redis list 本就无界"的语义豁免——本条报的是平台抽象层未接已有 OverflowPolicy 设施且未声明边界，属该接口设计层的防御性缺口，故 P3 而非 P2。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现（复核：INosqlQueue 全接口无 capacity/overflow 旋钮（:14-43）、`enqueueBatchAsync` 整集合单条 RPUSH（LettuceQueue:38-42）核实成立；平台 `QueueOverflowPolicy` 设施在位（nop-kernel/nop-commons concurrent 包）而该抽象未接核实成立；INosqlQueue 零生产使用（全仓仅测试 fake）核实——防御性缺口定性成立，P3 恰当）

## 维度集外线索（overlay C2/C4/C5/C9）

### [nop-persistence-peri-CL-023] 增量链作业每目标分片裸建 Thread，stopAll 不 join、失败轮询无主（C4 线程池与执行器治理）

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/IncrementalJobManager.java:176-186`（`new Thread(...)` 每链一条 daemon 线程）、`:201-206`（`stopAll` 仅 `source.cancel()` 后即 `runningJobs.clear()`）、`:235-243`（`join(millis)` API 存在但主路径无调用方）
- **线索**: 每个目标分片一条裸线程、无池化无命名工厂规范之外的治理；`stopAll` 丢弃句柄前不等待退出，若 `cancel()` 因 sink 阻塞未及时生效，链作业线程在后台残留并继续消费 CDC/写目标库；`getFailure`/`join` 在 main 源码内无调用方（仅 API 面），编排器 `abort` 调 `stopAll` 后不观测线程实际终止。分片数 M 大时线程数 = M×作业数无上界。建议复用平台 `IThreadPoolExecutor` + `CycleSynchronizer`，`stopAll` 改为 cancel + 有界 join + 终止态断言。
- **归属维度**: C4（本维度不展开）

### [nop-persistence-peri-CL-024] RpcEntityPersistDriver 单实体加载即一次阻塞 RPC：N+1 RPC 放大 + syncGet 阻塞调用线程（C9 热路径）

- **文件**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:261-286`（`loadAsync` 对单实体发 `batchGet`、ids=[单个]）、`:623-653`（`findFirst`）、`:741-771`（`findAll`）——全部以 `FutureHelper.syncGet(ret)` 收尾
- **线索**: 每个 lazy-load 未命中实体触发一次独立 RPC 往返且调用线程阻塞等待（sync-over-async）；批量关联拉取场景下 N 个实体 = N 次串行阻塞 RPC，无合并窗口/批量攒批；`findFirst/findList/findAll/count/exists/delete/update` 同族 7 处均为同一 syncGet 模式。热路径下每次 RPC 的序列化 + 网络时延直接叠加进 ORM 装配路径。建议增加按 session 的加载合并（per-key 去重 + 微批），并评估 syncGet 在池化线程上的占用治理。
- **归属维度**: C9（热路径性能；C8-004 已覆盖其载荷上界面，本条覆盖 N+1 与阻塞面）

## 维度复核结论（WI 2.5 C8 独立复核，2026-10-18）

> 复核人：C8 维度独立复核员（轮外复核，未参与初审与两轮深挖）。逐条回到 live code 验证证据 file:line 真实性、触发时序/规模推演可具体化性与严重度校准；现状对照 `ai-dev/audits/cra-index.md`（WI 1.1–2.4 台账逐条比对）、同单元 C1/C3/C6/C7 批次（跨维度同案查重，采信其复核裁定）、WI 2.4 审计目录（批量族查重与会话层分块事实核对）、lettuce-core 7.5.2.RELEASE 字节码（机制面独立验证）。本节不修改原发现条目；round2 条目（C8-007）状态行写在 C8-round2.md，判定表集中于此。

### 逐条判定表（C8-001…007）

| ID | 标题（缩写） | 原判 | 复核判定 | 现状标注 | 同案指针 |
|---|---|---|---|---|---|
| C8-001 | PubSub 消费回调 IO 线程内联：队头阻塞→停摆/断链丢消息 + onMessage 返回值丢弃 | P1 | **保留 P1**（统一裁定意见见下节） | 新发现 | C3-005(P2)/C7-010(P2)/C1-CL-011（同案，分歧留 7.1） |
| C8-002 | ConsistencyVerifier 双侧全量驻内存 + O(N²) 深分页 | P2 | **保留 P2**（opt-in 配置门核实） | 新发现 | C7-007（同案确认，按 C7 复核裁定合并计一次工单）；CL-025 同模式族 |
| C8-003 | tdengine 批写/批载无 chunk 上界（字面量内联） | P2 | **保留 P2**（批载面修正：会话层默认 1000/chunk；写面无界维持） | 新发现 | WI 2.4 C5-004、WI 1.7 C8-001/002（家族关联不并案）；round2 扩展随本条 |
| C8-004 | rpc 批写/批载整批单请求无 chunk 上界 | P2 | **保留 P2**（批载面修正：会话层默认 1000/请求；写面无界维持） | 新发现 | 同上家族关联；WI 2.4 无 chunk 面重复 |
| C8-005 | forEachItemAsync LRANGE 0 -1 全量物化 | P2 | **保留 P2**（零生产调用方，按本单元先例落点 P2） | 新发现 | nop-persistence-peri-CL-015（同案线索分账，本条主登记）；WI 2.2/2.3 拉全量族（家族） |
| C8-006 | INosqlQueue 无容量/溢出策略旋钮 | P3 | **保留 P3**（防御性缺口定性成立） | 新发现 | 无 |
| C8-007 | forEachEntry SCAN 无 MATCH/无总量上限：全库遍历全值 MGET | P2 | **保留 P2**（与 C8-005 非同案确认） | 新发现 | 无；C7-005 关联（复合前提已被 C7 复核降档 P2） |

### C8-001 严重度统一裁定意见（加入 7.1 统一裁定记录）

C8-001(P1)/C3-005(P2)/C7-010(P2) 同站点三重登记（另 C1-CL-011 线索），C3/C7 复核均已裁「严重度分歧留 7.1」。本复核从 C8 席位给出四点事实与裁定意见：

1. **机制面（本复核独立验证，不依赖初审自证）**：解包 lettuce-core 7.5.2.RELEASE 反汇编 `PubSubEndpoint.notifyListeners`——`List.iterator()` 直迭代并 `invokeinterface RedisPubSubListener.message`，无任何 executor 卸载/线程交接（与初审反编译结论、C7 复核采信一致）；单 pub/sub 连接承载全部 topic 为代码自证（LettucePubSubService.java:96-105、:147 注释）。慢消费者跨 topic 队头阻塞成立。
2. **契约面（无条件子面）**：`IMessageConsumer.onMessage` 返回 CompletionStage 的 ack 语义是 nop-api-core javadoc 明文契约（IMessageConsumer.java:21-24），包装层 :154 丢弃返回值——任何异步消费者的 ack 追踪静默失效，与部署接线无关。
3. **声明边界面（C3-005 P2 理由之一不完整）**：类 javadoc（:29-38）声明的是 fire-and-forget **传输丢消息**语义，未声明「消费停摆/服务端断链/反复重连」过载行为——「已声明边界」抗辩只覆盖丢消息半边，C8-001 误报排除成立。
4. **判级锚点与条件面**：P1 定义「负载上升必然触发的死锁/队列溢出」命中（慢消费者在场 + 负载上升 → event-loop 停摆 → Redis `client-output-buffer-limit` 断链为服务端标准行为）；同族判级先例 = WI 2.3 C8-001（WS 订阅推送同步阻塞钉死共享池，P1 在账）。C3-005 P2 的另一理由「仓库内未发现生产接线」经本复核普查**部分成立**：LettucePubSubService 无直接 beans 装配，经 `LettuceMessageService.getMessageService()`（:344-348）惰性工厂暴露；nop-stream-runtime `RemoteInputChannel:254` 是平台 `messageService.subscribe` 的设计消费方——属部署成熟度条件，非机制反驳。

**C8 侧裁定意见：建议 7.1 对四重登记统一裁 P1**（机制 + 平台 designated 消费路径 + 负载驱动必然 + onMessage 契约违约无条件面）；若 7.1 采信「零在仓生产接线」条件化降档口径（C7-005 P1→P2 先例），则对齐 P2，但裁定记录须保留第 1/2/3 点事实。

### C8-003/004 复核修正（会话层分块事实）

- **批载路径有会话层分块**：标准调用链为 `EntityPersisterImpl._batchLoad`（nop-orm :202-215）——`toLoad.size() > maxBatchLoadSize` 时 `CollectionHelper.splitChunk` 分块后调驱动；maxBatchLoadSize = entityModel 覆盖值或平台默认 `nop.orm.default-entity-batch-load-size`=1000（OrmConfigs.java:32-33）；集合路径同款分块（CollectionPersisterImpl:103/143）。该事实 WI 2.4 C5-memory-bounds.md B9 已独立核实。据此：C8-003 触发时序「写/加载 N 个实体、会话层无上限」的**加载半边**与 C8-004 主对象触发时序「驱动将 N 个 id 一次性进单个请求」**被反驳**；C8-004 失效面枚举漏列 `_batchLoad` 分块层。
- **批写路径无分块维持**：`BatchActionQueueImpl.flushAsync`（:160-199）将整个 holder 的 save/update/deleteActions 传 `persister.batchExecuteAsync`，`EntityPersisterImpl:638-653` 直传驱动——全链无拆分。C8-003/004 的写面无界成立（队列寿命=一次 flush、规模=事务脏集，WI 2.4 B8/C5-004 已核）。
- **修正后两条均维持 P2**：写面无界（fail-loud）+ 载面「有界但边界配置缺陷」（严重度口径原话）——1000 为计数上界非字节感知，TDengine 字面量内联 / RPC 行宽×1000 + subSelection 递归下块内字节量仍可巨大；驱动层无独立守卫。
- **round2 扩展 C8-003 逐行核实成立**：每子表独立 `USING <super> (tagcols) TAGS(…)` 声明（TdSqlHelper:64-71/73-94，TAGS 取 `list.get(0)` :66）；同子表 tag 值分歧静默取首行（TDengine 已存在子表忽略 TAGS 语义为领域知识，采信其「很可能」信心标注）——「仅按行数 chunk 不足以封顶、须按子表声明数×估算字节双维度切分」论证成立，随 C8-003 工单作为修复设计输入。

### 跨单元/跨维度查重结论

- **cra-index P1 台账（WI 1.1–2.4）逐条比对：零跨单元同案重复立项**。定向核对三组：
  - **C8-003/004 ↔ WI 2.4 批量族 / WI 1.x 批量无上限族：家族关联非同案**。WI 2.4 的批量面为 C5-004（写路径中间集合分配放大，上界=事务脏集，缺陷不同）与 B8/B9 有界性结论（恰为本复核修正的事实依据）；WI 2.4 对 RpcEntityPersistDriver 仅 runInSession 同步面范围外交叉引用（其 C2-round2 表 #8），无 chunk 面登记；WI 1.7 C8-001/002 为 nop-batch dispatch/Export 参数护栏族，站点不同。
  - **C8-005 ↔ nop-persistence-peri-CL-015：同位点线索分账**（C3 复核已登记「CL-015↔C8-005 处置归对应维度」），本条为维度主登记；WI 2.2/2.3 拉全量族为家族关联不并案。
  - **C8-002 ↔ C7-007：本单元内同案确认**（同站点 verifyChecksum/scanHashes/findPage、同证据、同修复），按 C7 复核裁定合并计一次工单。
- 本单元内跨维度同案汇总（C8 视角）：C8-001=C3-005=C7-010（+C1-CL-011 线索）、C8-002=C7-007、C8-005=CL-015（线索分账）。合并裁定时各计一次工单，C8 侧不撤条，以判定表指针供 7.1 汇总。
- overlay 线索分账：CL-023（IncrementalJobManager 裸线程）已由 C7-001/C3-002 工单吸收分账（C7 复核判定表在案）；CL-024（rpc N+1）归 C9，与 C8-004 载荷面互补不重复。

### 降档口径核对（任务专项）

初审对 dbtool/db-migration/geo/pdm 四根的「无过载面」结论为行为性判断（不存在生产-消费边界），非单线程豁免——复核抽验 db-migration（每 change 单语句、无运行期批累积）与 pdm（normalizeRefInfo do-while 每轮 remove 可终止）成立。四条运行期发现（C8-001/003/004/007）均按行为性缺陷计价，无单线程降档痕迹。C8-002 的 P2 计价依据是 opt-in 配置门（fromMap:157-159 / Builder:381-383 核实）而非路径冷热，口径正当。C8-005/007 的零生产调用方为条件性标注（按本单元 C1-012/C7-015 先例落点 P2），非降档豁免。**未发现不当降档**。反向问题一处：C8-004 批载面为**过度声明**（「无界」实为「会话层 1000/chunk 分块」），已修正不改级。

### 现状标注汇总与同案命中清单

- **判定统计**：保留 7（维持原严重度 7、降级 0、升格 0）/ 驳回 0 / 待定 0；其中 2 条（C8-003/004）附事实修正、1 条（C8-001）附统一裁定意见。
- **最终严重度分布**：P0×0；P1×1（C8-001）；P2×5（C8-002/003/004/005/007）；P3×1（C8-006）。
- **现状标注分布**：新发现×7；已知未修复×0；已修复×0。
- **同案命中清单**：本单元内发现级同案 2 组（C8-001=C3-005/C7-010、C8-002=C7-007）+ 线索分账 1 组（C8-005=CL-015）；跨单元零同案（C8-003/004 与 WI 2.4/WI 1.x 批量族为家族关联）。

## 最终保留项

| ID | 标题 | 最终严重度 | 现状标注 | 同案/修复通道指针 |
|---|---|---|---|---|
| C8-001 | PubSub 消费回调 IO 线程内联：跨 topic 队头阻塞→停摆/断链丢消息 + onMessage 返回值丢弃 | P1 | 新发现 | 与 C3-005/C7-010/C1-CL-011 合并计一次工单；统一裁定意见=建议 P1（见复核结论节） |
| C8-002 | ConsistencyVerifier full-checksum 双侧全量驻内存 + O(N²) 深分页 | P2 | 新发现 | 与 C7-007 合并计一次工单（归并流式 + keyset）；与 CL-025 同模式统一方案 |
| C8-003 | tdengine 批写/批载无 chunk 上界（字面量内联；tag 声明放大 + tag 分歧取首行） | P2 | 新发现 | 写面双维度 chunk（行数×子表声明数×估算字节，见 round2 扩展）；与 C8-004 同批工单 |
| C8-004 | rpc 批写/批载整批单请求无 chunk 上界（批载面已修正为有界） | P2 | 新发现 | 批写面 chunk 化 + 驱动层独立守卫；与 C8-003 同批工单 |
| C8-005 | forEachItemAsync LRANGE 0 -1 全量物化（分页先例未复用） | P2 | 新发现 | 与 CL-015 分账同案；按 100/页循环或 javadoc 声明边界 |
| C8-006 | INosqlQueue 无容量/溢出策略旋钮 | P3 | 新发现 | maxLen 选项 + QueueOverflowPolicy 三态，或 javadoc 声明无界边界 |
| C8-007 | forEachEntry/Async SCAN 无 MATCH/无总量上限：全库遍历全值 MGET | P2 | 新发现 | 匹配约束 + 总量/时限上限 + javadoc 边界；与 C7-005（P2）修断键增长源 |

**复核统计**：判定 7 条——保留 7（维持原严重度 7）、驳回 0、待定 0。最终严重度分布 P1×1 / P2×5 / P3×1。现状标注：新发现×7。同案合并后本维度净新增工单 4 张：C8-003+C8-004（驱动 chunk 同批一张）、C8-005（CL-015 分账）、C8-006、C8-007；C8-001、C8-002 分别并入 PubSub 投递模型工单与 C7-007 工单（去重后独立工单数由 7.1 汇总）。
