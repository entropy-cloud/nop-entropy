> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-stream-invariant-loop
> Processed: 2026-08-13 — P1-21-01/P1-09-01 → plan `ai-dev/plans/2026-08-13-1930-1-nop-stream-restore-path-serde-channel-fixes.md`；P1-18-02 → plan `ai-dev/plans/2026-08-13-1930-3-nop-stream-component-roadmap-c5-sync.md`；P2 ×15 → roadmap `## Follow-up Backlog`（`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`）

# 2026-08-13-1930 nop-stream 多维审计报告

## 基本信息

- **审核模块**: `nop-stream/` 全模块组（core / runtime / cep / flow / rocksdb / connector / connector-batch / connector-jdbc / connector-debezium / fraud-example，10 子模块，620 main + 490 test Java 文件）
- **审核日期**: 2026-08-13
- **审核范围**: 代码、配置（beans.xml / xdef / XDSL 实例）、测试、公共契约（导出面、API 表面、BOM）、架构文档一致性（ai-dev/design/nop-stream/*、nop-stream/README.md、source-anchors.md）
- **执行维度**: 01 依赖图与模块边界 / 03 API 表面积与契约 / 08 IoC 与 Bean 配置 / 09 错误处理与错误码 / 10 XDSL 与 XLang / 11 模型对齐 / 15 类型安全 / 16+21 测试覆盖与有效性 / 18 文档-代码一致性 / 20 跨模块契约
- **方法**: 6 个并行审计子 agent（3 波第一轮 + 3 个空响应重派），P0 候选与全部 P1 候选经主 agent 独立复核（JsonTool 探针实测 + 代码路径逐行验证 + 与 roadmap backlog 去重核对）

## 执行统计

| 波次 | 维度 | 初审发现 | 主 agent 复核 | 复核结论 |
|------|------|---------|--------------|---------|
| W1 | 01+20 依赖与跨模块契约 | 7 | 01-01/01-02/01-04/20-01 | 全部已在 backlog 登记（P2-01-02/P1-01-01/P2-01-03），不重复报告 |
| W1 | 03+08 API 表面与 IoC | 11 | 03-6、08-1 | 03-6 部分驳回（loadSavepoint 有生产消费者）；08-1 已在 backlog（P2-03-01/P2-11） |
| W1 | 09+15 错误处理与类型安全 | 15 | 09-1 | 09-1 保留 P1（StreamElementCodec 无 LOG 已实证） |
| W1 | 10+11 XDSL 与模型 | 9 | 10-2、11-1 | 10-2 保留（新实例）；11-1 已在 backlog（P2-03-02/XDSL-1~4） |
| W2 | 16+21 测试覆盖与有效性 | 6 | **21-01（P0 候选）** | **21-01 降级 P0→P1 并修正归因**（WindowOperator 路径生产不可达；CEP 路径为真实生产面，探针实测） |
| W2 | 18 文档-代码一致性 | 14 | 18-01/18-02/18-03 | 18-01 已在 backlog（P2-DOC-04~11）；18-02 保留 P1（C5 表三行实测矛盾）；18-03 保留 P2 |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | —（21-01 经复核降级，见 [P1-21-01]） |
| P1 | 3 | CEP 事件队列 JSON 恢复类型丢失、ChannelState 恢复静默丢记录、component-roadmap C5 状态表过期 |
| P2 | 22 | 死代码/死类型、@Internal 漏标、DSL 属性零消费新实例、测试弱断言、文档行号漂移等 |

---

## P1 发现

### [P1-21-01] MapState 容器值（List<Event>）经 JSON checkpoint 恢复后内层元素类型丢失 → CEP 恢复即 CCE；同缺陷 WindowOperator 路径生产不可达（P0 降级）

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:274-276`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:551-583, 226-229, 887-901`
- **证据片段**:
  ```java
  // CepOperator.open() —— 生产无条件创建（非测试路径）
  elementQueueState = keyedStateStore.getMapState(
          new MapStateDescriptor<>(EVENT_QUEUE_STATE_NAME, Long.class, (Class) List.class));
  // MemoryStateSerDe.snapshotMapState —— value 无自定义 serializer 时原样入 JSON
  pair.add(serializeWithSerializer(me.getValue(), valueSer));   // valueSer=null → List<Event> 原样
  // restoreMapState —— List.class.isInstance(ArrayList) 短路，内层元素不重物化
  Object mv = deserializeValue(me.get(1), valueClass);          // valueClass=List.class
  ```
  **主 agent 探针实测**（JsonTool.serialize → parseMap 存储层 round-trip，与 AR-01/AR-22 同法）：
  ```
  [List<Event>] json: {"value":[{"id":1,"name":"a"}]}     // @DataBean 序列化无 @type
    parsed value class: java.util.ArrayList first elem: java.util.LinkedHashMap
  ```
- **严重程度**: P1 — 契约违约：恢复后 CEP 事件队列元素为 LinkedHashMap，`sort()/processEvent()` 首次触碰即 CCE，或条件求值静默错误。
- **现状**: 子 agent 原报 P0（WindowOperator `elementTimestampsState` 路径，探针复现 CCE）经主 agent 复核**部分驳回**：`elementTimestampsState` 仅在 `windowStateDescriptor == null`（legacy 构造）分支创建，生产 builder（WindowOperatorFactoryImpl→Builder）四入口全部传非空 descriptor 且 Memory/RocksDB 后端均实现 `IInternalStateBackend` → 生产路径该状态为 null（对应 backlog P2-05「带 evictor 的 descriptor 路径不建 elementTimestampsState」）。**但同一 serde 缺陷经 `CepOperator.elementQueueState`（MapState<Long, List<Event>>，open() 无条件创建）在生产可达**：storageType=local（默认）→ LocalFileCheckpointStorage → CheckpointSerDe JSON round-trip → `restoreMapState` 的 `List.class.isInstance` 短路返回原样容器，内层元素类型丢失。全仓无任何测试对 CEP 事件队列做 JSON 存储层 round-trip（TestCepCheckpointRestoreE2E 全部直传内存 OperatorSnapshotResult）。
- **风险**: event-time CEP 作业（事件在 watermark 前进前缓冲于队列）+ 默认 local 存储 + checkpoint 恢复 → 恢复后队列元素为 LinkedHashMap → onEventTime 处理 CCE → 作业恢复即失败；或条件求值拿到错误字段静默错配。与 AR-01/AR-22（均为 P0 修复）同机制同族，修复范围仅覆盖了 key 与 timer key，容器 value 未覆盖。
- **建议**: ① `deserializeValue`（或 restoreMapState）对容器值（List/Map）按元素类型递归重物化（对齐 RocksDBValueSerDe.deserializeList/deserializeMap 的逐元素处理）；② 补回归测试：CEP event-time + bufferEvent 缓冲 + JSON 存储层 round-trip + 恢复后断言事件类型正确且匹配成立。
- **信心水平**: 确定（探针实测序列化行为 + 恢复代码路径逐行核对）
- **误报排除**: 非"已收敛"——AR-01 修复的是 entry key、AR-22 修复的是 timer key，容器 value 的内层元素重物化从未覆盖；RocksDB 后端免疫（deserializeList 逐元素重物化）恰证两后端行为不对称。

### [P1-09-01] ChannelState 恢复路径静默丢弃不可解码的 in-flight 记录，且注释声称的"codec 路径日志"不存在

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/ChannelState.java:156-194`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/transport/StreamElementCodec.java:91-146`
- **证据片段**:
  ```java
  // ChannelState.java:162-192（fromSerializableForm，恢复主路径调用）
  try {
      channelIndex = Integer.parseInt(e.getKey());
  } catch (NumberFormatException nfe) { continue; }            // 畸形索引 → 整通道静默丢弃
  ...
  try {
      StreamElement element = StreamElementCodec.decode(env);
      elements.add(element);
  } catch (Exception ex) {
      // "A single undecodable in-flight record must not abort the whole
      //  restore; skip it (best-effort). This is observable via logging
      //  in the codec path."   ← 注释承诺的日志不存在
  }
  ```
  **实证**：`StreamElementCodec.decode`（StreamElementCodec.java:91-146）仅抛 `StreamException`，**零 LOG 调用**（主 agent grep 全文件 0 命中）；ChannelState 三条旁路（:166-168 非数字、:171-173 非 List、:176-178 非 Map）同样静默无日志。
- **严重程度**: P1 — 违约行为：in-flight 记录是 exactly-once 语义的唯一载体，恢复时 decode 失败即被静默丢弃，exactly-once 静默降级为 at-least-once，且无任何诊断线索。
- **现状**: unaligned checkpoint 的 channel state 恢复采用"best-effort 跳过"，与 state 侧同类版本漂移 fail-fast（`ERR_STREAM_STATE_SCHEMA_MISMATCH`）不对称；注释虚假承诺日志存在（violates error-handling.md per-element 隔离规则：必须 `LOG.warn(..., e)` 且 throwable 作末参数）。
- **风险**: 跨版本 restore（用户类被移除 → ClassNotFoundException，codec:110-111）或 payload 漂移时记录静默丢失 = 无声数据丢失；排障时无从知晓丢了多少条、丢了哪条。
- **建议**: 至少 `LOG.warn`（含 throwable）记录被丢弃的 channel/record 与原因；或按 No-Silent-No-Op 惯例 fail-fast 让恢复机制显式处理；补两条负面测试（畸形索引 / 损坏 payload）钉住 skip 契约。
- **信心水平**: 确定（decode 无日志逐行核实）
- **误报排除**: 非已收敛——AR-01/P0-6/P0-7 修复的是其他静默路径（key 重物化/fencing/savepoint vertex），此 catch 块 live 存在且被恢复主路径调用。

### [P1-18-02] component-roadmap C5 容错缺口表三行状态过期，与 live code 及 checkpoint-design 直接矛盾

- **文件**: `ai-dev/design/nop-stream/component-roadmap.md:186-193`；代码证据：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1551`、`RpcDistributedExecutor.java:250`、`CheckpointCoordinator.java:370`
- **证据片段**:
  ```
  roadmap:186 "abort 接线 …distributed 路径 abort 接线（cancelTask RPC + JobCoordinator listener）仍为 Deferred（lease failover 兜底）"
  roadmap:191 "并发能力一致 ✅ 已修复：…effectiveMaxConcurrent = Math.min(1, config) 强制 max=1"
  roadmap:193 "背压逃生（unaligned）✅ 已修复（Stage 43 Phase 2–3）…"；roadmap:308 "unaligned checkpoint 未实现（Stage 43 Phase 2–4 进行中）"
  ```
  live code：`JobCoordinator.java:1551 registerDistributedAbortHandler()` + `RpcDistributedExecutor.java:250 coordinator.registerDistributedAbortHandler()`（**主 agent 实测两处存在**）；`CheckpointCoordinator.java:370 int effectiveMaxConcurrent = config.getMaxConcurrentCheckpoints();`（**无 Math.min(1,...) clamp**）；unaligned 全链路实现且有 `TestChannelStateRescaleE2E`/`TestInputGateSingleChannelRemoteLiveness`。
- **严重程度**: P1 — 文档契约漂移：状态表声称的行为（distributed abort 未接线 / maxConcurrent 被 clamp 到 1 / unaligned 未实现）均与 live code 不符；且同文档 §3 C5（已修复）与 §5 技术债表（未实现）自相矛盾。
- **现状**: 三行状态未随 Stage 39/43/45 落地更新；与同目录权威文档 checkpoint-design §13.2/§8.7 直接矛盾。
- **风险**: 读者按此表评估容错能力会误判 distributed abort 缺失、并发 checkpoint 被降级、unaligned 不可用，得出错误的投产/恢复决策。
- **建议**: 按 checkpoint-design 状态重写三行（distributed abort ✅ Stage 39 Phase 3 / 并发能力一致 ✅ Stage 45 / unaligned ✅ Stage 43 含 E2E），删除 §5 中矛盾行。
- **信心水平**: 确定（三处 live code 直接证据 + 同目录权威文档交叉印证）
- **误报排除**: roadmap 有"动态规划"免责声明（§4），但 §3 C5 是明确标注"当前实现状态"的状态表，且该表其他行（对齐超时/心跳）均与代码一致，证明本应同步而这三行遗漏。

---

## P2 发现

### [P2-10-02] DSL 节点级 parallelism（StreamTransformModel 基础属性）声明但零消费、无 fail-fast（新实例）
- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:104`；`StreamModelDslBuilder.java:149-161`
- **证据**: `StreamTransformModel.getParallelism()` 在 flow main 代码零引用（grep 仅 `_gen/` 与测试命中）；builder 仅消费模型级 `model.getParallelism()`（xdef:24）；全部 9 个测试实例与 fraud 示例声明节点级 `parallelism="1"/"2"`，探针实测构建后 env 并行度为全局值，节点级声明无效。backlog AR-11 只覆盖 source/sink 的 maxParallelism/consistencyCapability，节点级 `parallelism` 是新实例。
- **风险**: 算子级并行度是核心部署契约，静默退化为全局并行度且无报错。
- **建议**: 消费（transform 构建后 setParallelism）或非默认值 fail-fast（带 transform id）。
- **信心水平**: 确定

### [P2-09-02c] MemoryStateSerDe.serializeWithSerializer 静默降级：catch 无日志无 rethrow
- **文件**: `MemoryStateSerDe.java:870-879`
- **证据**: 自定义 IStreamSerializer（descriptor.setSerializer）序列化失败时静默返回原始对象 → 快照 JSON 路径格式被绕过、失败推迟到写出阶段、restore 侧因非 byte[] 走 JSON 分支格式不对称。
- **建议**: `LOG.warn` 或 fail-fast。默认 JsonToolSerializer 不受影响。
- **信心水平**: 中高

### [P2-15-01b] MapState 内层 key 在无 mapKeyType 记录的兼容路径不重物化（AR-01 修复范围外）
- **文件**: `MemoryStateSerDe.java:203-231`；`MemoryMapState.java:86-142`
- **证据**: 顶层 key 已由 AR-01 `deserializeKey` 修复；MapState 内层 key 在 `keyTypeName == null`（legacy 快照）或 mapKeyClass==Object 时保持 JSON 原生形态（Integer/LinkedHashMap）→ `containsKey(123L)` 对 Integer(123) 失配 → 状态静默"消失"；RocksDB 侧按 keyClass 重物化，两后端不对称。
- **建议**: restoreMapState 对 null mapKeyClass 按 valueClass 推断或 fail-fast。
- **信心水平**: 中高

### [P2-15-02] schema checksum 不覆盖自定义 TypeSerializer，更换序列化器后恢复静默损坏
- **文件**: `StateSchemaResolver.java:128-148`；`MemoryStateSerDe.java:892-895`
- **证据**: canonical 串 = stateType;valueType;mapKeyType;accumulatorType;aggregateFunctionType，不含 serializer 类名；restore 用当前 descriptor 的 serializer 解码旧 byte[] → 类型未变仅换 serializer 时 checksum 不变 → 垃圾值或延迟 CCE。
- **建议**: serializer 类名纳入 canonical 串。
- **信心水平**: 中

### [P2-03-02b] API 面死类型与死实现（批量，全部主 agent grep 0 消费验证）
- **文件**: `SourceEnumeratorState.java:21-32`（死 @DataBean，已被 SourceEnumeratorSerializedState 取代）；`TaskAssignmentMessage.java:23-31`（死 DataBean，已被 RPC deployTask 取代）；`DeploymentPlanProviderImpl.java:22-36`（死实现，与 core DefaultDeploymentPlanProvider 双轨并存仅一套生效）；`NopCepConstants.java:10-13`（死常量 VAR_EVENT/VAR_CTX 零消费）
- **建议**: 删除或补 `@Deprecated`；DeploymentPlanProviderImpl 二选一（删除或注册为 bean）。
- **信心水平**: 高

### [P2-03-06] ICheckpointStorage savepoint 写侧半接线：storeSavepoint 零生产消费者
- **文件**: `ICheckpointStorage.java:45-49`；`GraphModelCheckpointExecutor.java:1139-1142`
- **证据**: 复核修正子 agent 原报——`loadSavepoint` 有生产消费者（GraphModelCheckpointExecutor:1142 restoreFromSavepointPath），但 `storeSavepoint` 仅测试消费（SUSPEND/EXPORT_SAVEPOINT 走通用 storeCheckPoint，从不调 storeSavepoint）。
- **建议**: 删除 storeSavepoint 族或接入 SUSPEND 路径。
- **信心水平**: 中高

### [P2-03-07/08] @Internal 同族漏标（core 3 个 + runtime 5 个）
- **文件**: `InternalTimerService.java:31`、`InternalAppendingState.java:33`、`InternalListState.java:31`（core，同包 InternalTimer/StateMigrationFunction 均已标注）；`InternalWindowFunction.java:35` + 4 个 Internal* 适配器（runtime，同包 WindowOperator 已标注）
- **建议**: 补 @Internal。
- **信心水平**: 中高

### [P2-08-03] 测试声称的"模块发现→beans/ 遍历→物化"路径在真实 app 容器中不存在
- **文件**: `TestStreamModuleDiscovery.java:88-107`；`nop-ioc/.../AppBeanContainerLoader.java:275-284`
- **证据**: 两 beans 文件名（stream-control-rpc/stream-data-plane）不在 `app-*` 匹配集，AppBeanContainerLoader.isAppBeans 只认 app 前缀；测试实为手工 addResource 单文件加载，与注释宣称的"模块发现路径"不符。
- **建议**: 改名 app-* 或修改测试注释明确"仅验证手工加载路径"。
- **信心水平**: 高

### [P2-09-02d] NopCepErrors 错误码声明 ARG 无占位符（cep 侧 1 处，core 侧 2 处已在 backlog P2-09-06）
- **文件**: `NopCepErrors.java:27-29`（ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP 声明 ARG_PART_NAME/ARG_FOLLOW_KIND 但描述无占位符）
- **建议**: 补占位符或删 ARG。
- **信心水平**: 高

### [P2-10-05] edge `<name>` 子元素零消费且无 fail-fast
- **文件**: `stream.xdef:199-208`；`StreamModelDslBuilder.java:349-391`
- **证据**: `StreamEdgeModel.getName()` flow main 零消费（grep 仅 _gen）；edge 其余 7 属性均已消费或 fail-fast（validateEdgeDeclarations，计划 1243-2 修复），唯独 `<name>` 静默丢弃，与 builder 注释"edge 属性 never silently downgraded"矛盾。
- **建议**: fail-fast 或消费，或在 xdef 注明元数据待遇。
- **信心水平**: 高

### [P2-20-03] connector pom 注释与模块实际内容不符
- **文件**: `nop-stream/nop-stream-connector/pom.xml:18-27`
- **证据**: 注释称"仅保留 MessageSourceFunction/MessageSinkFunction"，实际模块含 FileSource 家族 + FileTwoPhaseCommitSink（9 个 main 类）；与 runtime pom 注释（Stage 49 表述正确）互相矛盾。
- **建议**: 更新注释。
- **信心水平**: 确定

### [P2-21-03/04/05] 测试弱断言与盲区（批量）
- **文件**: `TestWindowOperatorEvictorTimestamps.java:79-93`（testSnapshotDeepClonesAccumulators 名实不符，仅 assertNotNull，命中 P-5/P-6）；`TestCepCheckpointRestoreE2E.java:238-241`（条件断言 `if (!restoredOutput.isEmpty())`，坏实现"timer 未恢复"照样通过，命中 P-3 变体）；`ChannelState.fromSerializableForm` 两条解码失败路径零负面测试（命中 P-3）
- **建议**: 无条件断言 / 补负面测试 / 改真断言。
- **信心水平**: 高

### [P2-16-01] TestTaskEpochSnapshot 低价值容器测试
- **文件**: `TestTaskEpochSnapshot.java:13-56`
- **证据**: 5 个测试全部是 HashMap 支撑 DTO 的 set→get 往返与 identity/instanceof 断言（命中 P-1/P-2）；真实序列化保护在 runtime 的 CheckpointSerDe 测试。
- **建议**: 删除或并入真实 round-trip。
- **信心水平**: 高

### [P2-21-02] Session 窗口合并丢弃被合并窗口的 element timestamps（legacy 路径 + 生产 path 的双重问题）
- **文件**: `WindowOperator.java:1518-1662`（mergeWindowContents 三分支均不合并 timestamps）、`:990-994`（fallback 到 currentWatermark）
- **证据**: 合并路径只合并 contents 不合并 timestamps；clearWindowContents 会删被合并窗口的 timestamp 条目；emit 对缺失 timestamp 静默 fallback 到当前水位。生产路径（elementTimestampsState==null，见 P1-21-01 复核）本就全 fallback（对应 backlog P2-05）；legacy 路径下合并丢时间戳 → TimeEvictor 驱逐错误。
- **建议**: merge 路径对称合并 timestamps；补 session+evictor+merge 组合测试。
- **信心水平**: 中高

### [P2-18-03/08/13/14] 文档补充漂移（新实例，未在 P2-DOC-04~11 批量内）
- **文件**: `component-roadmap.md:298`（"4 个空壳模块（api/checkpoint/flink/flow）保留占位"——仓库仅 10 模块，api/checkpoint/flink 已不存在、flow 已实现）；`cep-design.md:281-286`（§6.2 CepWindowOperator 组件全仓 0 命中且未标 spec-only）；`core-design.md:360-369`（侧输出发射点行号部分过期）；`01-architecture-baseline.md:127` + `00-vision.md:132`（RuntimeTopology 列为正式管线末级，代码 0 命中，README 已有 caveat 缓解）
- **建议**: 删除/标注/加概念阶段注记。
- **信心水平**: 高

---

## 已复核确认在 backlog 登记、不重复报告的项（live 复认）

| 条目 | 代码现状 | backlog 登记 |
|------|---------|-------------|
| BOM 幽灵条目 nop-stream-api/checkpoint + 缺 4 真实模块 + tests/pom 引用不存在 artifact | live（nop-bom/pom.xml:1184-1188,1256-1262） | P2-01-02 |
| rocksdb 类驻留 io.nop.stream.core 命名空间 split-package | live（17+ 类） | P1-01-01（successor，待人工批准） |
| core→runtime Class.forName 反向软耦合（WindowedStreamImpl:162-165） | live | P2-01-03 |
| 两 beans.xml 重复 id=streamMessageService（ioc:default 不消解） | live（实证 ERR_IOC_DUPLICATE_BEAN_DEFINITION） | P2-03-01/P2-11 |
| rocksdbjni 版本内联硬编码 | live | P2-01-05/06/07 |
| StreamConstants/Configuration 空占位类 | live | P2-03-03 |
| feature 命名空间零元素 + "feature-gated"注释不符 | live | P2-03-05 |
| 裸异常 89 处（flow 已清零，core 69 处为主） | live | P2-02 |
| 死错误码 10 个 + SstFileChecksum 裸异常重复错误码消息 | live | P2-09-03/05/08 |
| ProcessingTimeCallbackException extends RuntimeException | live | P2-15-01/02 |
| fraud-detection.stream.xml 破损死文件（x:extends→xdef、悬空 bean、必 fail-fast 窗口属性） | live（实证 ERR_XDSL_NO_SCHEMA） | P2-03-02/XDSL-1~4 |
| source/sink 的 outputType/inputType/params/maxParallelism/consistencyCapability 零消费 | live | AR-11 + P2-XDSL-8~14 |
| 节点级 watermarkInterval 零消费 | live | P2-XDSL-8~14 |
| README.md:35 XDSL 主入口"规划中" | live | P2-DOC-04~11 |
| checkpoint-design/cep-design/window-design/README 模块表行号与计数漂移 | live | P2-DOC-04~11 |
| TriggerAccumulators 清理后 FIRE 路径不对称等窗口族 P2（backlog P2-04~08） | 部分修复（P1-INV-1 已闭环） | backlog |

---

## 总评

nop-stream 模块组整体工程质量仍保持高位：错误处理骨架健全（471 处 ErrorCode 式抛点、英文消息、双构造器异常类），依赖图健康（core 唯一根、无环、connector 族干净），测试质量优秀（有效行为测试约 78%，关键恢复路径断言正确结果而非无异常），计划 1243-1/2/3 与 1615-1 的修复（triggerAccumulators 清理、InputGate 入口 elapsed 检查、edge/checkpoint/窗口属性消费或 fail-fast、flow 错误码体系 58→0）经本次审计确认全部落地且有高质量回归测试钉住。

**P0 = 0**。最接近 P0 的是 [P1-21-01]：子 agent 原报 P0（WindowOperator element-timestamps CCE）经主 agent 探针实测 + 路径复核**降级为 P1 并修正归因**——生产 builder 从不创建 elementTimestampsState（该部分与 backlog P2-05 重合），但同一 serde 缺陷（容器 value 的 JSON round-trip 内层元素类型丢失）经 `CepOperator.elementQueueState`（生产 open() 无条件创建）真实可达，且与已按 P0 修复的 AR-01/AR-22 同机制同族——这是本审计最有价值的发现：**修复族的最新变体落在容器 value 上，全仓无测试覆盖 JSON 存储层 CEP 恢复**。

**次重要缺口**：[P1-09-01] unaligned 恢复路径静默丢记录且注释虚假承诺日志（exactly-once 载体无诊断降级）；[P1-18-02] component-roadmap C5 状态表三行与 live code 矛盾（与已修复的 P1-DOC-01/02/03 同病，plan 1243-3 未覆盖该表）。

**系统性结论**：本审计 6 个维度发现的绝大多数（26/28）已准确落在 roadmap backlog 中（前一周期审计+计划机制工作良好），新发现集中在三处：(a) 容器值序列化（serde 族新变体）、(b) unaligned 恢复静默路径、(c) 计划收口时未同步的状态表文档。均不需要结构性重构，可在既有 I4/I5 类别清扫或新计划中承载。

## 优先修复建议

1. **P1-21-01**：`deserializeValue` 对容器值递归重物化（对齐 RocksDB deserializeList/deserializeMap）+ CEP JSON round-trip 回归测试（先红后绿，机制同 AR-22）
2. **P1-09-01**：ChannelState 恢复 catch 补 LOG.warn（含 throwable）+ 两条负面测试钉住 skip 契约
3. **P1-18-02**：component-roadmap C5 三行 + §5 技术债行按 checkpoint-design 回写
4. **P2 批次**：节点级 parallelism fail-fast、serializeWithSerializer 日志、MapState 内层 key 兼容路径重物化、死类型清理、@Internal 补齐、beans 测试注释修正

## 本次审核盲区自评

- 未运行完整 `./mvnw test -pl nop-stream -am`（只读审计；构建验证由 mission 的 I5 阶段负责；上一周期 I5 full-green 2895 tests 在案）
- [P1-21-01] 的 CEP 恢复 CCE 未做端到端 E2E 实测（探针在 serde 存储层确认类型丢失 + 恢复代码路径逐行确认；E2E 复现建议留待修复计划先红测试）
- 类型安全维度抽查为主（1235 处 cast 无法全量逐查）
- 并发竞态类发现依赖静态分析，未做压力/并发实测
- nop-stream-connector-batch（nop-batch 桥接）与 fraud-example 的语义细节未深挖（非核心路径）

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
