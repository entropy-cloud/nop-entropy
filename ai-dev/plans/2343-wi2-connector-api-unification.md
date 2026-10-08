# 2343 WI2 连接器 API 两代归一迁移（message / debezium-cdc / batch-loader 全量迁 FLIP-27 + B 层例外撤销）

> Plan Status: active
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI2（M1）+ 裁定 §三 R2/R3（`ai-dev/design/nop-stream/multi-engine-gap-adjudication.md` :92-101）
> Related: plan 2330（FileSource FLIP-27 先例）；plan 2335（WI3 kafka legacy source——显式冻结剩余项）；`docs-for-ai/03-modules/nop-stream-connectors.md`（冻结政策节与能力矩阵）
> 审查记录：R1 对抗性审查（fresh session）1 Blocker + 6 Major 全部落本 plan（见下节）；R2 复审（fresh session，task agent_79545e7c-6a13-4761-9c59-2de5138e2cd8）7 项修订全落实、无新 Blocker/Major，**结论：可置 active**——其实施建议已钉入 Phase 0（N-1 stash consume-once + unregister 清理）。

## Purpose

按 WI0 裁定 R3 把连接器面三个 legacy source（message / debezium-cdc / batch-loader）全量迁 FLIP-27 `Source/SplitEnumerator/SourceReader`；按 R2 在迁移完成后撤销 debezium B 层表路由例外（`SubtaskShardedSourceFunction` 在连接器工厂面终结）。交付 = 迁移完成 + 显式剩余冻结清单（kafka）+ 能力矩阵回归。

## 对抗性审查修订（agent_1e35a3c6，1B+6M 全部落 plan）

- **B-1 core 恢复回路死代码（Blocker）**：`SourceReaderOperator.restoreState`（:401-414）被 runtime 恢复链（`RescaleStateAssembler.restoreOperatorsFromState` :509-524，deploy 期）在 `open()`（createReader）**之前**调用——`reader == null` 守卫把 restored `source-reader-splits` 静默丢弃；enumerator 侧 `LocalSourceCoordinator.startEnumerator(restoredState)`（:91-99）生产唯一调用点 `registerReader:148` 传 null——`EpochManifest.sourceEnumeratorSnapshots` 只写不读。FileSource 的「restore 先例」实为 reader 级单测（TestFileSourceCheckpointRestore 直调 reader），runtime 端到端恢复从未行使。**修订**：Phase 0 新增 core 恢复回路工作项（operator 恢复态缓冲 + open() 回放 + enumerator 快照恢复接线 + E2E），三个 source 的恢复断言都依赖它。
- **M-1 debezium hash 路由 vs enumerator 分配矛盾**：内嵌 legacy 函数无「任意表集」API——`effectiveEngineConfig` :685-699 硬编码 hash 路由 + 实例化；`totalParallelism=1` 又丢 `name-<subtask>` 实例命名（AR-03/JMX 卡死）。**修订**：`DebeziumSourceSupport` 不是纯提取——新增「实例命名 + 任意 include-list 覆盖」组合路径（`buildInstanceConfig(base, instanceName, includeListOverride)`），legacy hash 路径原样保活；「零重写」措辞改为「语义逻辑复用 + 新组合路径」。
- **M-2 offset 载荷归因规则缺失**：`cdc-offsets` 是 connector 实例级一张 base64 map（`NopStreamOffsetBackingStore` :211-239），按表拆分需解析 Kafka Connect partition key（各 connector 形状不同）——拒绝。**修订**：DebeziumTableSplit 载荷 =（表条目 + **实例级 offset map 副本**（同 reader 各 split 等值复制）），restore 取首个 split 的 map 合成单个 `TaskStateSnapshot{cdc-offsets}` 喂 `initializeState`——实例粒度如实声明，不做表级归因。
- **M-3 FILL_HISTORY 基线失实**：`TestRecalculateBackfillE2E:424-427` 用 `new BatchLoaderSourceFunction` 程序化构造（非工厂路径），迁移后天然全绿、零信号。**修订**：基线更正；Phase 1 新增 factory→Source 组合 E2E（`factory.createSource → env.addSource(Source) → EOS`）作真实承载；作业图 bean 面走 `StreamModelDslBuilder:1113` SourceFunction bean 解析不经工厂——如实记录（新源消费面 = 程序化 env.addSource + 工具面）。
- **M-4 UNBOUNDED reader idle/cancel 契约**：operator 空 poll 自旋 `Thread.yield`（:302-336）。**修订**：message reader `pollNext` = 有界阻塞 `queue.poll(100ms)` → 空返回（自旋频率 10Hz 可忽略）；checkpoint/cancel mail 延迟上界 = 100ms（钉定）；关停次序 = reader.close() → 内嵌 fn.cancel() → 队列毒丸 → daemon join(5s 超时 typed 失败)；队列满时订阅线程阻塞 put（与 legacy 同步 collect 的背压语义等价）。
- **M-5 测试计数与适配清单**：debezium 实为 **14** 个测试类；message 相关 8 个。工厂切换必适配：`TestDebeziumConnectorFactory:37`（assertInstanceOf→DebeziumCdcSource）、`TestDebeziumPreSubmitConnectivityProbe`、`TestStreamConnectorRegistryDiscovery`（3 componentClass + 3 instanceAligned）、`TestFileMessageConnectorFactories:120`、`TestBatchConnectorFactories:48-60` 与 `TestConnectorConsistencyCapability:45`（`getSourceConsistency()` 实例断言——split source 无此方法，**编译级**）。**修订**：口径改为「legacy 语义测试零翻转 + 工厂/探测测试显式适配清单（上列）」。
- **M-6 descriptor componentClass 矛盾**：descriptor 描述工厂产物，「不变」即对产物说谎。**修订**：componentClass 更新为新 Source 类名；discovery 测试断言翻转集 = 3 componentClass + 3 instanceAligned（message/debezium/batch 的 instanceAligned 期望 true→false，file 行 :249 为先例）；connectors.md「实例一致性 = 端点 getSourceConsistency()」不变式对 split 行改写为 descriptor 钉定口径。
- **Minor**：m1 `StreamDataSampler.construct`（:81-104）已双形态分派，新 Source 不实现 SampleableSource → 采样面零改动（从工作项删除，仅保留发现测试回归）；m2 门禁插入点 = `GraphExecutionPlan:538-560`（source 分支 :544-548）与 `RemoteGraphExecutionPlanBuilder:357-379`（:362-369）；新 Source 的 `validateParallelism` 必须无副作用幂等（legacy 版写模板态 :515，不做）；m3 三新源运行面 = LOCAL（`LocalSourceCoordinator` LOCAL-mode hardwired，FileSource 既有边界沿用，非新引入）；m4 D-4 迟到分配实现判据 = `SourceReaderOperator.open()` 的 `flushPreOpenSplits`（:243）先于 `reader.start()`——reader 以「start 后 addSplits = typed 拒绝」实现。

## Current Baseline（2026-10-08 实测，经审查修正）

- **FLIP-27 目标面**：`Source`（core `source/Source.java:37`）、`SourceReader`（`SourceReader.java:32`）、`SplitEnumerator`（`SplitEnumerator.java:34`）；桥接 = `SourceReaderOperator`（pull 循环 :294-344：空 poll + `isFinished` 退出，空转 `Thread.yield`；split 快照 key `source-reader-splits` :69/:381-397）；coordinator = `LocalSourceCoordinator`（LOCAL-mode）经 `wireCoordinatorChannel` 注册；enumerator 快照入 `EpochManifest.sourceEnumeratorSnapshots`（`CheckpointCoordinator:1685-1712` + `CheckpointSerDe:321/:663-680`）——**但恢复回路断裂（B-1，Phase 0 修复）**。
- **先例边界**：FileSource 族（`io.nop.stream.connector.file`）证明 fresh-run 全链（snapshot 侧 manifest 接线有 `TestSourceEnumeratorManifestWiring`）；其 restore 只有 reader 级单测，runtime 端到端恢复未行使——本 plan Phase 0 补齐。
- **三 legacy source**（行号经审查核实）：
  - `MessageSourceFunction`（connector :45）：裸 `SourceFunction`；AT_LEAST_ONCE（:219-222）；P1-9 pendingError（:141-176）、R5-CON-04 退订（:196-205）、restart 复位（:121-133）。工厂 `MessageSourceConnectorFactory`（类 :31，DESCRIPTOR :35-48；`createSourceFunction` 不接分片——并行语义由后端消费者组承载）。
  - `DebeziumCdcSourceFunction`（connector-debezium :59）：四接口；REPLAYABLE（:347）；offset 经 `NopStreamOffsetBackingStore`（base64）进 operator state `cdc-offsets`（:376-391/:395-433，AR-03 :440-444，WI9 种子 :453-459）；B 层路由（:487/:524/:537/:555/:584-586/:685-726）；凭证 D4（:631-701）；失败监听（:248-256）。工厂 descriptor REPLAYABLE/PARALLEL/OFFSET_CHECKPOINT/PRODUCTION。
  - `BatchLoaderSourceFunction`（connector-batch :63）：`ReplayableSourceFunction`+`ConnectivityCheckable`+`ParallelismCheckable`；offset next-index（:201/:212）、skip+shortfall（:112-128）、单写入者（:189-199）。工厂 descriptor AT_LEAST_ONCE/SINGLE_INSTANCE/OFFSET_CHECKPOINT/PRODUCTION。
- **并行度校验缺口**：`ParallelismCheckable` 只在 legacy 路径检查（`GraphExecutionPlan:538-560` source 分支 :544-548 只认 `StreamSourceOperator`；`RemoteGraphExecutionPlanBuilder:357-379` :362-369 同）；`SourceReaderOperator` 无 source accessor（:72 private）。
- **消费面（审查 grep 结论）**：`IStreamSourceFunctionFactory` 四消费点（StreamConnectorCatalog construct/probeSource、StreamDataSampler、StreamConfValidator:264、StreamConnectorRegistry:128）全部 instanceof 双形态分派——工厂切换零编译破坏；`StreamDataSampler` 已双形态（采样面零改动）；三个 legacy 类 main 其余消费点仅 fraud-example 的 `ReplayableCdcSourceFunction extends DebeziumCdcSourceFunction` bean（`TestConfValidatePreSubmitE2E:182-187` 走 bean 不经工厂）；作业图 bean 面走 `StreamModelDslBuilder:1113` 不经工厂。
- **测试基线**：connector message 相关 8 测试类；debezium **14** 测试类（gated 真 DB 三个 `Assumptions.assumeTrue(DOCKER_AVAILABLE)` SKIP 可见机制在位，`TestDebeziumRealMysqlCdc:71`）；batch 5 测试类；runtime discovery/manifest-wiring/backfill E2E。
- **DRAIN**：coordinator 层实现（`JobCoordinator:2311-2354`），`truncateForDrain` 生产零调用点——非迁移基线。

## Goals

- **G0 core 恢复回路打通（B-1）**：`SourceReaderOperator` 恢复态缓冲（reader 未建时缓存 restored splits，`open()` createReader 后回放 `reader.restoreState`）；enumerator 快照恢复接线（runtime 恢复路径取 `EpochManifest.sourceEnumeratorSnapshots` → `SourceCoordinatorRegistry`/coordinator `startEnumerator(restored)`——具体接线以实现时 manifest 结构为准，exit 以恢复 E2E 断言 enumerator 与 reader 双侧续读为准）；FileSource 作为首个消费者回归。
- **G1 batch-loader 迁移**：`BatchLoaderSource`（`Source<S, BatchLoaderSplit, BatchLoaderEnumeratorState>`，BOUNDED，单 token split `batch-0`）；reader 增量遍历 loader（每次 `pollNext` 至多一条，barrier 粒度保持）、offset=next-index、client-side skip + shortfall typed 拒绝、loader AutoCloseable 关闭、`checkConnection` 保留；单写入者经 G5 部署期门禁 + reader `start()` 侧 `context.getTotalParallelism()>1` typed 拒绝双保险。
- **G2 message 迁移**：`MessageSource`（`Source<T, MessageTopicSplit, MessageSourceEnumeratorState>`，UNBOUNDED）；reader 内嵌 `MessageSourceFunction`（daemon 线程驱动 `run(ctx)`，ctx.collect 入有界队列（容量 256，满则订阅线程阻塞 put——与 legacy 同步 collect 背压等价）；`pollNext` = `queue.poll(100ms)`，M-4 契约）；AT_LEAST_ONCE 声明不变；类型不符/消费失败经 pollNext typed 失败；close 退订；无 offset checkpoint。
- **G3 debezium 迁移（wrapper 形态，D-1 修订版）**：`DebeziumCdcSource`（`Source<ChangeEvent, DebeziumTableSplit, DebeziumCdcEnumeratorState>`，UNBOUNDED）——表 = split（一表一 split，enumerator 初始一次性全量 round-robin）；reader 内嵌 `DebeziumCdcSourceFunction` 作引擎宿主，经 `DebeziumSourceSupport.buildInstanceConfig(base, "name-<subtask>", assignedTables)` 组合路径装配（M-1）；offset = 实例级 map 副本随 split state 走 FLIP-27 检查点面（M-2，restore 合成 `TaskStateSnapshot{cdc-offsets}` 喂 `initializeState`）；AR-03/WI9 种子/D4 凭证/失败监听原样复用；实例级 offset 隔离等价成立。
- **G4 B 层例外撤销（R2）**：debezium 工厂面切 split source 后 connectors.md 冻结政策节注记改判撤销；能力矩阵代际标注更新；`SubtaskShardedSourceFunction` 工厂 create 路径清零（发现测试加钉）。
- **G5 部署期并行度门禁（FLIP-27 面）**：`SourceReaderOperator` 增 `getSource()` 访问器；两计划构建器对 `Source instanceof ParallelismCheckable` 增检查（与 legacy 对称）；新 Source 的 `validateParallelism` 无副作用幂等（m2）。三新源运行面 = LOCAL（m3 既有边界沿用）。
- **G6 显式剩余冻结清单**：`KafkaSourceFunction` 为连接器面唯一剩余 legacy source（冻结登记 + successor 说明）；core 内核 `SourceFunction` 接口与程序化/测试 source 不在范围（R3 明示）。
- **G7 能力矩阵回归（M-5/M-6 适配清单）**：`TestStreamConnectorRegistryDiscovery`（3 componentClass + 3 instanceAligned + 工厂路径清零钉）、`TestDebeziumConnectorFactory`、`TestDebeziumPreSubmitConnectivityProbe`、`TestFileMessageConnectorFactories`、`TestBatchConnectorFactories`、`TestConnectorConsistencyCapability`（编译级两处）适配；fraud-example `TestConfValidatePreSubmitE2E` 回归（bean 路径零翻转）；legacy 语义测试零翻转。

## Non-Goals

- KafkaSourceFunction 迁移；core 内核 legacy 接口/桥接算子删除；sink 侧迁移；debezium 引擎内核改动；FLIP-27 DrainableSource 等价物；WI9 SKIP_GAP 对新源扩展；分布式（remote）FLIP-27 coordinator（LOCAL 边界沿用 FileSource）。

## 设计裁定（执行前钉定，经审查修订）

- **D-1 wrapper 形态迁移**：debezium/message reader 内嵌 legacy 函数作引擎宿主；语义逻辑经 `DebeziumSourceSupport` 提取复用，**新增**「实例命名 + 任意 include-list 覆盖」组合路径（M-1）；替代方案（全 pull 式引擎适配层）被拒——等于重做 cdc roadmap WI1-8 语义，风险收益不成比例。
- **D-2 split 语义**：message/batch split = 赋值 token（数据所有权由后端/loader 承载，splitId 唯一）；debezium split = 真数据分片（表 → 引擎 include-list 子集）。**offset 载荷 = 实例级 map 副本**（M-2），不做表级归因。
- **D-3 restore 真相源**：debezium restore 后表集 = restored splits（checkpoint truth）；config include-list 变更跨恢复 = 下次全新运行生效；restore 重建顺序 = 取 splits → `DebeziumSourceSupport.buildInstanceConfig` → 合成 `TaskStateSnapshot{cdc-offsets}`（split[0] 的 map）→ `initializeState` → start。batch restore offset 单调（seek 负值 typed 拒绝沿用）。
- **D-4 迟到 split 分配**：reader 以「`start()` 后 `addSplits` = typed 拒绝」实现（预 open 分配经 `flushPreOpenSplits` :243 在 start 前接受——m4）。

## Execution Plan

### Phase 0 - core 恢复回路打通（B-1）

Status: completed
Targets: nop-stream-core（SourceReaderOperator、SourceCoordinatorRegistry/LocalSourceCoordinator）、nop-stream-runtime（恢复接线）、nop-stream-connector（FileSource 回归）

- Item Types: `Fix`（已确认 live defect：恢复链路静默丢状态）

- [x] `SourceReaderOperator`：restoreState 在 reader 未建时缓冲恢复 splits（stash **consume-once**——回放即删，防同 JVM 后续 job 误恢复），`open()` createReader 后回放 `reader.restoreState`（含 open() 异常路径不吞）
- [x] enumerator 快照恢复接线：runtime 恢复路径把 manifest 的 `sourceEnumeratorSnapshots` 喂回对应顶点的 coordinator `startEnumerator(restored)`（接线点以 `CheckpointSerDe:663-680` 结构实测为准；`StreamExecutionEnvironment` unregister 钩子 :602 一并清理 stash）；快照缺失时维持现状（fresh enumerator）
- [x] 恢复 E2E：FileSource（BOUNDED）checkpoint → restore → split 游标续读不重不丢（真实走 SourceReaderOperator + LocalSourceCoordinator 全链，非 reader 级单测）
- [x] **执行发现（B-1'，姊妹缺陷）**：split 持久化双缺陷——(a) 原始 `List<FileSplit>` 直接进 operator state，持久化线程的 JSON only-DataBean 门禁拒绝（`nop.err.core.json.only-data-bean-is-serializable`）→ **FLIP-27 源的 checkpoint 持久化从未成功过**；(b) 即使可写，JSON 解码退化为 Map、restore 侧裸转型必 CCE。修复：snapshotState 经 **source 自身的 `getSplitSerializer()`**（FLIP-27 契约）编码为 JSON 安全信封 `{serializerVersion, base64 encodedSplits}`；restoreState 解码还原类型（裸 List 形态保留为算子级单测的内存通路）。E2E 首红实证（4 次 Failed checkpoint + 恢复后 run2 从 line-1 重读），修复后全绿。
- [x] **执行发现（E2E 前置）**：`env.execute` 走 checkpoint 引擎的前提是 `enableCheckpointing()`（`checkpointingDeclared` 门 :406-408）——仅 set interval 不生效；FILL_HISTORY 测试即未启用（自然 EOS 无需恢复）。E2E 已按 `enableCheckpointing(100L)` 修正。

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-connector,nop-stream/nop-stream-runtime -am` 全绿
- [x] **端到端验证**：恢复 E2E 断言 reader splits 与 enumerator state 双侧真实续读（反 B-1 静默丢弃）——`TestSourceApiRestoreE2E`（run1 注入失败于 line-50 + 同 jobId 重提交 → run2 从恢复游标续读、非 line-1 重启、跨 run 全覆盖）
- [x] **无静默跳过**：恢复态缓冲在 open 失败时 typed 暴露，非吞掉；split 信封解码失败 typed 拒绝
- [x] owner docs：`nop-stream.md` 或 connectors.md 中 FLIP-27 恢复语义一句话校正（如适用；否则显式声明 No owner-doc update required）——FLIP-27 检查点面此前从未可用，属修复而非契约变更；connectors.md 恢复锚点索引行随 Phase 4 终核
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 1 - core 门禁 + batch-loader 迁移

Status: planned
Targets: nop-stream-core（SourceReaderOperator、两计划构建器）、nop-stream-connector-batch、runtime 测试

- Item Types: `Feature`

- [ ] `SourceReaderOperator.getSource()` 访问器；`GraphExecutionPlan:538-560` 与 `RemoteGraphExecutionPlanBuilder:357-379` 对 `Source instanceof ParallelismCheckable` 增部署期校验（对称 legacy :544-548/:362-369）
- [ ] `BatchLoaderSource` + `BatchLoaderSplit` + `BatchLoaderEnumeratorState` + 版本化双 serializer（split 载荷含 offset）+ `BatchLoaderSplitEnumerator`（单 split 赋值）+ `BatchLoaderSourceReader`（增量遍历/每次 poll 一条/skip/shortfall/offset，语义镜像 legacy :97-149；start() 侧 parallelism>1 typed 拒绝）
- [ ] `BatchLoaderSourceConnectorFactory` 切 `IStreamSplitSourceFactory`（componentClass 更新为 BatchLoaderSource）；beans 更新
- [ ] 测试：split/serializer 往返；reader 遍历+skip+shortfall+seek（移植 legacy 关键用例）；parallelism 门禁（两构建器 + reader 侧）；factory→Source 组合 E2E（**新增承载**：`factory.createSource → env.addSource(Source) → loader 空批 EOS`，M-3）；工厂/探测/发现适配（M-5/M-6 清单 batch 部分）

Exit Criteria:

- [ ] `./mvnw test -pl nop-stream/nop-stream-connector-batch,nop-stream/nop-stream-runtime -am` 全绿
- [ ] **端到端验证**：factory→Source 组合 E2E 真实走通（含 checkpoint→restore→offset 续读，依赖 Phase 0 回路）
- [ ] **接线验证**：门禁在两构建器被真实调用（部署期 typed 拒绝测试）
- [ ] **无静默跳过**：并行度>1 部署与 start 双层 typed 拒绝；skip-shortfall typed；seek 负值 typed
- [ ] owner docs：connectors.md batch-loader 行代际更新（行内容本阶段冻结，Phase 4 终核）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - message 迁移

Status: planned
Targets: nop-stream-connector

- Item Types: `Feature`

- [ ] `MessageSource` + `MessageTopicSplit` + `MessageSourceEnumeratorState` + serializer + `MessageTopicSplitEnumerator`（N token 一对一分配）+ `MessageSourceReader`（内嵌 `MessageSourceFunction` daemon 驱动 + 有界队列 256 + `poll(100ms)` + pendingError 经 poll 抛出 + close 次序 M-4）
- [ ] `MessageSourceConnectorFactory` 切 `IStreamSplitSourceFactory`（componentClass 更新）；beans 更新
- [ ] 测试：token 分配唯一性；push→poll 转换与背压（队列满阻塞）；类型不符 typed 失败（P1-9 等价）；close 退订 + daemon join（R5-CON-04 等价）；restart 复位；start 后 addSplits typed 拒绝（D-4）；工厂/发现/探测适配（M-5/M-6 清单 message 部分）

Exit Criteria:

- [ ] `./mvnw test -pl nop-stream/nop-stream-connector,nop-stream/nop-stream-runtime -am` 全绿
- [ ] **端到端验证**：message source → sink E2E（LocalMessageService，discovery 测试先例）真实收发
- [ ] **无静默跳过**：消费异常经 pollNext typed 失败；后端不可达 typed 失败
- [ ] owner docs：connectors.md message 行代际更新（本阶段冻结行内容）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - debezium 迁移 + B 层例外撤销

Status: planned
Targets: nop-stream-connector-debezium、connectors.md

- Item Types: `Feature`

- [ ] `DebeziumSourceSupport`：提取实例命名/schema-history 限定/凭证解析（legacy 行为零变化，14 测试类保持绿）+ **新增** `buildInstanceConfig(base, instanceName, includeListOverride)` 组合路径（M-1）
- [ ] `DebeziumCdcSource` + `DebeziumTableSplit`（表条目 + 实例级 offset map 副本）+ `DebeziumCdcEnumeratorState`（未分配表集 + nextSubtask）+ 版本化双 serializer + `DebeziumTableSplitEnumerator`（初始一次性全量 round-robin、restore、表数<parallelism typed 拒绝 parity legacy :500-515）+ `DebeziumCdcSourceReader`（D-3 重建顺序、M-2 载荷、D-4 守卫、credentialProvider setter 透传）
- [ ] `DebeziumCdcSourceConnectorFactory` 切 `IStreamSplitSourceFactory`（componentClass 更新）；beans 更新
- [ ] 测试：split/serializer 往返；enumerator 分配+restore；reader 语义（装配、offset 经 split state 快照/恢复、种子首跑、迟到分配 typed 拒绝、实例级隔离断言 `name-<subtask>`）；既有 14 测试类零翻转（legacy 语义）；fraud-example bean 路径回归
- [ ] **R2 撤销落地**：connectors.md 冻结政策节改判「已随 WI2 迁移撤销」；能力矩阵 debezium 行代际标注；发现测试加 `SubtaskShardedSourceFunction` 工厂路径清零钉

Exit Criteria:

- [ ] `./mvnw test -pl nop-stream/nop-stream-connector-debezium,nop-stream/nop-stream-runtime -am` 全绿（gated 真 DB 测试 SKIP 可见机制沿用）
- [ ] **端到端验证**：DebeziumCdcSource 经 SourceReaderOperator 真实消费 ChangeEvent（fake 引擎源）+ checkpoint→restore→offset 续读断言（依赖 Phase 0 回路）
- [ ] **接线验证**：enumerator 快照真实进 EpochManifest 且恢复侧真实回放（Phase 0 模式扩展）
- [ ] **无静默跳过**：迟到分配 typed；引擎失败经 pollNext typed；表数<parallelism typed
- [ ] owner docs：connectors.md 三处（冻结政策/能力矩阵/探测表 + 实例一致性不变式改写）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口

Status: planned
Targets: docs、plan/roadmap

- Item Types: `Proof`

- [ ] docs 终核：connectors.md 全量一致性（模块清单/SPI 表/成熟度/采样/探测/能力矩阵/恢复锚点 + kafka 冻结登记 G6 + split 行实例一致性不变式改写）；`nop-stream.md`/user-guide 涉及点核对
- [ ] 独立子 agent closure audit；roadmap WI2 勾选；git commit

Exit Criteria:

- [ ] owner docs 已更新且 `check-doc-links --strict` 退出码 0
- [ ] 独立 closure audit 通过且证据写入本 plan Closure 节
- [ ] roadmap WI2 已勾选、提交完成

## Closure Gates

- [ ] 三 source 工厂面全部产出 FLIP-27 split source（`SubtaskShardedSourceFunction` 工厂路径清零钉通过）
- [ ] R2/R3 语义保全基线成立：message AT_LEAST_ONCE、debezium REPLAYABLE+并行+实例级 offset 隔离、batch seek 续读+shortfall typed 拒绝
- [ ] core 恢复回路真实行使：三源至少各一条 checkpoint→restore→续读证据（FileSource 回归 + 三新源 E2E）
- [ ] `./mvnw test -pl nop-stream/nop-stream-connector,nop-stream/nop-stream-connector-batch,nop-stream/nop-stream-connector-debezium,nop-stream/nop-stream-runtime,nop-stream/nop-stream-core -am` 全绿
- [ ] **Anti-Hollow Check**：三 source 经 SourceReaderOperator/LocalSourceCoordinator/EpochManifest 真实运行与恢复
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --severity high`（connector/batch/debezium 三模块）退出码 0
- [ ] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### KafkaSourceFunction 迁移

- Classification: `watch-only residual`
- Why Not Blocking Closure: WI3 残余窗口语义（sink commitTransaction~durable 窗口）钉定在 legacy 检查点契约上，迁移需重新论证 EO 语义（adjudication R1 B1 链条）；roadmap Deliverable 允许「显式剩余冻结清单」形态收口
- Successor Required: `no`（真实诉求或 WI 波次触发时从 WI20+ 取号另立）

### 分布式（remote）FLIP-27 coordinator

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `LocalSourceCoordinator` LOCAL-mode hardwired 为 FileSource 起即有的引擎边界，非本迁移引入；三新源运行面 = LOCAL 已如实声明（m3）
- Successor Required: `no`

## Non-Blocking Follow-ups

- （closure 时填写）

## Closure

Status Note: （closure 时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:（closure 时填写）

Follow-up:

- （closure 时填写）
