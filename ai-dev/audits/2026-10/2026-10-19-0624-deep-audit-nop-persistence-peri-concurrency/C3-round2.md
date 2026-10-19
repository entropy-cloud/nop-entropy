# C3 深挖第 2 轮 — nop-persistence-peri（WI 2.5）

## 深挖范围与方法

第 1 轮 7 条（C3-001..007）集中在 orm-migration 裸线程链与 PubSub。本轮按任务书五条未扫面深挖：

| 深挖线 | 触达文件 | 结论 |
|---|---|---|
| ① FullCopyBatchJob 异步面（nop-batch 委托边界） | `FullCopyBatchJob.java`（全文）、`ShardedUpsertConsumerProvider.java`（全文） | **阴性**：`task.execute(context)`（`:104`）为同步阻塞调用，委托边界内无自建异步；`LongAdder copied` 为调用线程内计数；consumer/loader 均为 per-call 实例。多 worker（`throttleConcurrency>1`）下的并发面归 nop-batch 平台（不重审）。cancel/中止面已由第 1 轮 C7-006 立案，无新失效面 |
| ② ConsistencyVerifier / MultiSinkTopologyProbe / IncrementalLagProbe 内部并发 | `ConsistencyVerifier.java`（全文）、`MultiSinkTopologyProbe.java`（全文）、`IncrementalLagProbe.java`（全文） | **探针无内部线程/定时器**（全部在调用线程内联执行，`IncrementalLagProbe.sleep` 的 `Thread.sleep` 循环可中断且中断语义正确 `:59-69`）；probe 读 offset 经 `NopStreamOffsetBackingStore` 的 CHM 快照（`getOffsets:211-218` 弱一致迭代，无撕裂面）。但发现 **probe 的 connector 名与链注册名不一致**（C3-008，主发现） |
| ③ CdcOffsetSeedBuilder | `CdcOffsetSeedBuilder.java`（全文） | **阴性**：纯静态工具、无状态、无异步；`buildSerializedSeed` 在 startChain 调用线程执行，经 `setInitialOffsets` → `initializeState` → `Thread.start()` 的 happens-before 边传递给链线程，发布安全 |
| ④ geo/pdm/tdengine 隐藏异步站点 | 五根关键字全集复扫（`new Thread|ExecutorService|CompletableFuture|CompletionStage|Scheduled|parallelStream|synchronized|volatile`） | **阴性**：geo/pdm/dbtool/db-migration 零命中；tdengine 仅 `FutureHelper.futureCall` 伪异步（第 1 轮 CL-017 已立案）；orm-rpc 桥接模式 7 处第 1 轮已逐一核对正确 |
| ⑤ offset backing store 供读面复核 | `NopStreamOffsetBackingStore.java`（全文，nop-message-debezium，作为 orm-migration 探针的使用面证据锚点） | CHM 存储 + 快照读，可见性合格；`forConnector` 的 computeIfAbsent 建空条目语义成为 C3-008 后果面之一（每次 runVerify 对不存在的 base 名建空 registry 条目） |

## 新增发现

### [C3-008] IncrementalLagProbe 探测的 connector 名与增量链实际注册名不一致：多分片（rescale 主场景）下 m6 门禁的 offset 观测量退化为常量，lag=0 判定对任何滞后/死链平凡通过

- **文件**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java:220-226`（探测调用）、`IncrementalJobManager.java:125-127,134-138`（链注册名派生）、`IncrementalLagProbe.java:71-90`（指纹构建）；证据锚点 `nop-message/nop-message-debezium/.../NopStreamOffsetBackingStore.java:81-90`（forConnector 对未知名 computeIfAbsent 建空条目）
- **证据片段**:
  ```java
  // IncrementalJobManager.java:125-126,136 —— 链的 connector 名 = base 或 base-i
  String baseConnector = config.getIncrementalConnectorName() != null
          ? config.getIncrementalConnectorName() : "mig-" + jobId;
  ...
  String connectorName = targets.size() == 1 ? baseConnector : baseConnector + "-" + i;
  // OrmMigrationOrchestrator.java:220,223 —— 探测只用裸 base 名，不带分片后缀
  String connectorName = config.isIncrementalEnabled() ? config.getIncrementalConnectorName() : null;
  if (!probe.awaitLagZero(connectorName, config.getQuiesceSeconds())) { ... }
  // IncrementalLagProbe.java:82-84 —— 对从未注册的 base 名取 offset，得到恒空指纹
  Map<ByteBuffer, ByteBuffer> offsets =
          NopStreamOffsetBackingStore.forConnector(connectorName).getOffsets();
  ```
- **严重程度**: P1（主场景下门禁观测面结构性失效，有明确触发链；VERIFY 的 lag=0 承诺对多分片作业完全不成立）
- **并发场景**: 编排 API 调用线程（runVerify → awaitLagZero）与 M 条增量链作业线程（各自以 `base-i` 名写 offset registry）；缺陷不依赖交错时序，而是**命名空间错位**：探测方读取的 registry 键与写入方注册的键在 M>1 时必然不同
- **触发时序**: rescale 场景 M=4：T1：runIncremental → startFallbackChains 启动 `mig-<jobId>-0 .. -3` 四条链，各自以带后缀名向 `NopStreamOffsetBackingStore.REGISTRY` 写 offset（`IncrementalJobManager.java:136`）；T2：写门关闭后 runVerify 执行 `awaitLagZero("mig-<jobId>", ...)`——`forConnector(base)` 在 registry 中无此键，`computeIfAbsent` **新建空 CHM**（`NopStreamOffsetBackingStore.java:85`）→ 指纹恒为空串；交叠后果：(a) 链滞后数万事件时 offset 指纹前后均不变 → `before.equals(after)` 平凡成立，m6 门禁第一观测量形同虚设，VERIFY 在增量未追平的库面上放行 count/checksum 之外的一切判定；(b) 链死亡（C7-001 场景）与链健康空闲同样不可区分——本条把 C7-001 的"死链盲视"从"探测语义弱"升级为"探测键错位、结构性无观测"
- **现状**: `awaitLagZero` 的 offset 观测仅在 `targets.size()==1`（relocate 单表形态）时键名一致；M==1 时裸 base 名与链名重合属巧合性正确。另每次 runVerify 对空名条目的 `computeIfAbsent` 向静态 REGISTRY 泄漏一个永不清理的空条目（CL-010 增长面的新来源）
- **风险**: rescale（模块存在的核心场景：1→M 扩容）下 VERIFY/SWITCH/DONE 全链路的"增量已追平"前提失去观测支撑；唯一剩余闸门是 count/checksum（`ConsistencyVerifier`），静默期内小窗口增量缺失可双双通过——切换后目标分片静默缺行，且无任何错误信号
- **建议**: 探测名与注册名同源派生——把 startFallbackChains 的命名规则（`targets.size()==1 ? base : base+"-"+i`）提取为共享方法，runVerify 对 M>1 逐分片名聚合指纹（任一滞后即 gateFailed），或 `IncrementalJobManager` 暴露 `connectorNames()` 供探测遍历；同时 runVerify 后清理本次误建的空 registry 条目（复用 `clearConnector`）
- **失效面枚举**（F3 同族清扫）: offset 观测全调用链逐点——① `runVerify:220` connector 名派生（本条主对象，唯一 awaitLagZero 生产调用点）；② `IncrementalLagProbe.snapshot:71-76`（执行方，键错位后果面，随 ① 修复闭合）；③ `IncrementalJobManager.startFallbackChains:136`（注册方命名，正确侧，作为修复基准）；④ `IncrementalLagProbe.countLedger/maxEpoch:92-103`（ledger 观测量，与链名无关，其无 namespace 过滤缺陷已由 C7-008 立案，不重复）；⑤ fan-out opt-in 形态（`MultiSinkTopologyProbe` 启用时同构键错位，随 ① 同修）；⑥ `CdcOffsetSeedBuilder.buildSeed:58-64`（seed 的 partition 键用 `cdcConfig.getServerName()`，默认=分片 connector 名，链内自洽，不受本条影响）
- **信心水平**: 确定（键派生两侧行号级对照：探测侧 `:220` 裸 base vs 注册侧 `:136` 带后缀；M>1 时空指纹为 `forConnector` computeIfAbsent 语义的直接代码后果）
- **误报排除**: 非平台设施重审——`NopStreamOffsetBackingStore` 仅作使用面证据锚点，缺陷锚定在 orm-migration 两侧命名派生不一致；非"已声明边界"——设计 §4.3/m6 明文要求 offset 静默为门禁观测量，实现使其在主场景下恒真属实现与契约冲突；非第 1 轮重复——C7-001 报的是"死链对存活探测不可见"（探测语义缺轮询），本条报的是"探测键错位使 Lag 面在健康链上也不工作"，根因不同、修复点不同（补轮询 vs 修键派生）
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现（关联 C7-001/C1-005；与 C3-009/010/011 同模式统一修复，非同案合并）。复核：键派生三层（配置裸名 → manager `base-i` → instanceName 并行度后缀惰性）行级对照成立；**强化证据**——`MigrationJobConfig.fromMap` 强制 `incremental.connector-name` 非空（requireSectionString + isEmpty 校验），生产配置下「null 名 → no-incremental 支路」不可达，本条生产面即 M>1 后缀错位、rescale 主场景必然触发；小勘误——「每次 runVerify 泄漏空条目」应为每 base 名首次泄漏一条（computeIfAbsent 复用既有条目），不改结论。

## 扩展

### 扩展 C7-001（链作业死亡不可见 → 失效面新增结构性证据）

C3-008 证明：在 rescale 主场景（M>1）下，`runVerify` 的 offset 指纹读取的 registry 键恒为空（`forConnector(base)` vs 链注册 `base-i`），因此"健康空闲链"与"死亡链"之外的第三种状态——"健康但滞后链"——同样能通过 lag=0 门禁。第 1 轮 C7-001 的失效面枚举 (a)-(e) 需追加：(f) M>1 时 offset 观测对一切链状态恒真。修复须与 C3-008 的键派生修复联动（先修键、再加存活轮询，顺序颠倒会使存活轮询建立在错位的观测键上）。

## 维度集外线索（overlay）

### nop-persistence-peri-CL-025 FullCopyBatchJob 主复制路径 offset 深分页：亿行表全量复制为 O(N²) 源库 IO（C8/C7 归维）

- **位置**: `nop-persistence/nop-orm-migration/src/main/java/io/nop/orm/migration/FullCopyBatchJob.java:114-135`（`buildSelectSql` 生成 `select * from t order by <pk>`，分页载体为 nop-batch `JdbcPageBatchLoaderProvider` 的 offset 语义）；同族先例 `ConsistencyVerifier.findPage:183-189`（第 1 轮 C7-007/C8-002 已立案的 verify 面）
- **线索**: FULL 阶段主复制路径与校验路径同用 `ORDER BY pk + offset` 深分页——第 k 页需重扫/重排前 k×batchSize 行，N 行表总代价 O(N²/batch)；校验面已在 C7-007 立案，而**主复制路径的同类代价未被任何发现覆盖**（其数据量恰恰是校验面的全部输入）。分页原语在平台 `JdbcPageBatchLoaderProvider`（nop-batch-jdbc，按口径不重审平台本体），故登记为使用面线索而非发现：编排侧未提供 keyset（`WHERE (pk>?) ORDER BY pk`）分页选项，也未在 owner 文档声明该 IO 包络。建议：评估以 keyset SQL 生成器替换 `buildSelectSql`（编排侧一行 SQL 形态变更，平台 loader 的 SQL 由 `sqlGenerator` seam 注入，`FullCopyBatchJob.java:117` 具备替换点）。
- **归属维度**: C8（流控/过载——源库 IO 放大）为主，关联 C7（复制时长与时窗）。

## 继续判定

**需第 3 轮**。依据：本轮新增 1 条 P1（C3-008，主场景门禁结构性失效），按"新增含 P0/P1 → 需第 3 轮"触发。第 3 轮建议范围：以 C3-008 修复后的命名派生为基准复核 lag 门禁全链（含 fan-out opt-in 形态与 `NopStreamOffsetBackingStore` 消费面），并覆盖本轮阴性的 FullCopyBatchJob/Probe 内部并发面在并发配置（`throttleConcurrency>1`、`fanoutEnabled=true`）下的重新过筛。

## 复核结论（WI 2.5 C3 独立复核）

本文件 1 条条目（C3-008）逐条回码复核：**保留 1（P1 维持），驳回 0，待定 0**；现状新发现。键派生两侧行级对照成立，并经 fromMap 强制 connector-name 非空的配置面复核**强化**（生产面收敛为 M>1 后缀错位、必然触发）；扩展节（C7-001 失效面追加）维持。判定表与最终保留清单集中见 C3-async-chains.md「维度复核结论」节。
