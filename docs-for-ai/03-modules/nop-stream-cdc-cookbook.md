# nop-stream CDC 生产化 Cookbook

> 定位：Debezium CDC source（`DebeziumCdcSourceFunction`，`nop-stream-connector-debezium`）的生产操作手册——四类操作场景各一节。
> 连接器能力矩阵见 `03-modules/nop-stream-connectors.md`；checkpoint/状态恢复机制总览见 owner doc `03-modules/nop-stream.md`。
> 本文关键声称带 live 行为核对锚点（测试名），均已存在并可通过模块测试复现。

## 机制总览（先读）

- CDC source 声明语义 `REPLAYABLE`：Debezium engine 的 offset（每个分区下次要读的位点）经 **`NopStreamOffsetBackingStore`** 桥接进 nop-stream checkpoint 协议。
- offset 存储：`snapshotState(checkpointId)` 把 offset map（base64 TreeMap）写入 operator state，key = **`cdc-offsets`**；恢复时 `initializeState` 读出并预填充 backing store，engine 从位点续读。
- engine 侧接线：Debezium 2.4 engine builder 无 `using(OffsetBackingStore)` 入口，`NopStreamOffsetBackingStore` 经 `offset.storage` 配置反射实例化，并以 **static registry（connectorName → 共享 map）** 与 source-function 侧实例互通（`TestDebeziumCdcCheckpoint.testOffsetStoreInjectionChain` 钉定三层接线）。
- **connector name 必填**：`DebeziumConfig.name` 缺失即 fail-fast（`ERR_STREAM_CONFIG_ERROR`）——未命名连接器会共享 `_default_` offset 桶造成位点污染。

## 一、首次启动：snapshot → 增量切换

1. **配置**（`DebeziumConfig`）：`name`（必填）、数据库连接、`snapshotMode`（默认 `initial`——无 offset 时先全量快照，有 offset 时直接增量续读；切换行为由 Debezium engine 依据 offset 存在性决定，`snapshot.mode` 透传）。
2. **首次启动判定**：`initializeState(state)` 中 `state == null` 或无 `cdc-offsets` 条目 → fresh start，主动清除 static registry 中同名连接器的**陈旧 offset**（防止同 JVM 内前一次崩溃运行的位点被误继承）。
   - 核对锚点：`TestDebeziumCdcCheckpoint.testFirstRunStateNullDoesNotResumeFromStaleOffsets`、`testFirstRunRestartStartsFromBeginning`（connector-debezium 模块）。
3. **snapshot 期间**：engine 全量读取现有表内容（`c`/`u` 变更事件流），offset 持续推进并随 checkpoint 持久化。
4. **切换到增量**：snapshot 完成后 engine 自动进入 binlog/增量模式——无需人工干预；checkpoint 中的 offset 保证切换点前后连续。
5. **DDL/schema change 事件**：默认 `includeSchemaChanges=false`、`includeDdl=false`（不外发 schema 变更记录）；转换层对无 `source` 块的事件返回 null 丢弃——schema 演进的处理边界见第三节。

## 二、offset 恢复与重放

1. **恢复路径**：作业重启 → TM 从最近 durable checkpoint 恢复 → `StreamSourceOperator.restoreState` 重建 `TaskStateSnapshot` → `DebeziumCdcSourceFunction.initializeState` 从 `cdc-offsets` 条目恢复 offset 到 backing store → engine 续读。
   - 核对锚点（offset round-trip，三项）：`TestDebeziumCdcCheckpoint.testSnapshotStateStoresOffsetsFromStore`（快照写入）、`testInitializeStateRestoresOffsetsToStore`（恢复写回）、`testSnapshotRestoreRoundTrip`（完整 round-trip：快照 → Java 序列化 → 新实例 initializeState → offset 等值）。
2. **重放语义**：恢复后从 checkpoint 位点重读——checkpoint 之后、崩溃之前已发的事件会**重发**（source 边界 at-least-once）；配合 2PC sink（如 S1 的 `JdbcTwoPhaseCommitSink`）端到端 exactly-once。
   - 核对锚点：`TestDebeziumCdcCheckpoint.testCdcCheckpointKillRecoverNoDuplicates`（kill-recover E2E：跨运行记录 key 零重复）。
3. **S1 生产 offset 路径参考**：fraud-example 的 `ReplayableCdcSourceFunction`（`nop-stream-fraud-example/.../scenario/`）继承 `DebeziumCdcSourceFunction`，只替换消息 engine 为测试替身，**offset/快照/恢复/checkpoint 全走生产代码路径**——它是「CDC offset 语义如何在真实管线中验证」的参考实现（`TestS1CdcRecoveryE2E.s1RestoreReplaysFromCheckpointAndCommitsExactlyOnce`：run 1 停止 → durable epoch manifest 断言 → run 2 从 checkpoint 位点重放，最终结果集完整无重复）。
4. **重放到更早位点（手动回拨）**：当前无 offset 手动改写工具——需要重放到任意历史位点时，用「全新重跑」（第四节）+ 上游可重放存储配合；这是边界而非缺陷（无位点编辑承诺）。

## 三、schema 演进边界

**支持面**（状态下线后的状态 schema 迁移，`StateMigrationRegistry` 机制）：

1. 注册迁移函数：`StreamComponents.registerStateMigrationFunction(stateName, StateMigrationFunction)`——按 source/target 的 `schemaChecksum` 匹配；迁移在恢复后**首次 `getState()` 时同步执行**（任何元素处理之前），幂等（描述子替换后不再重复迁移）。
2. 迁移函数三方法：`migrate(old)->new`、`sourceFingerprint()`、`targetFingerprint()`；各状态种类的「存储值」语义——ValueState=值本身、MapState=逐 value（key 不迁移）、ListState=逐元素、Reducing/Aggregating=不透明 accumulator（accumulator 迁移由用户负责）。
   - 核对锚点：`TestStateMigrationEndToEnd.memoryIntegerToLongMigrationFullRoundTrip` / `rocksdbIntegerToLongMigrationFullRoundTrip`（runtime 模块，两后端全链：getState→快照→JSON 持久化→重载→恢复→迁移）。
3. 未注册迁移 + 指纹不匹配 → **fail-fast**（`ERR_STREAM_STATE_SCHEMA_MISMATCH`，带状态名上下文），不静默丢状态。
   - 核对锚点：`TestStateMigrationEndToEnd.memoryNoMigrationFailsFast` / `rocksdbNoMigrationFailsFast`。

**不支持面（显式声明）**：

- **源表 DDL 变更不自动适配**：Debezium DDL 事件默认不外发（第一节）；新增列/改列类型后，`after` 映像按新 schema 到达，解码层（如 S1 `CdcChangeDecoder`）对 `after` 缺失 fail-fast——**先保证解码层与状态描述子兼容，再上线 DDL**。
- 无自动 schema diff 工具 / 无跨格式自动迁移：迁移函数必须用户手写注册。
- 值序列化走 JSON 快照路径（自定义 `IStreamSerializer` 的状态经 `__java_bytes__` base64 marker 无损 round-trip；CEP NFA/SharedBuffer 状态即此路径）。

## 四、故障排查与全新重跑

### 全新重跑（reset-state）

```bash
<java> io.nop.stream.runtime.maintain.StreamMaintenanceMain reset-state \
    jobId=<id> checkpointBaseDir=<dir> sourceReplayable=true
```

- 语义：删除 `<base>/<jobId>/` 全部 durable checkpoint + epoch manifest + 其携带的 source 位点——同 jobId 重新拉起即从**起点**全量重跑（可重放 source 从头重读）。
- 拒绝语义（无静默清空）：`sourceReplayable=false` 显式报错（不可重放 source 重置即丢数据）；注册表存在活跃 coordinator 显式报错（先 stop）；状态目录不存在显式报错（防拼错路径静默成功）。
- 锚点：`TestStreamStateResetTool.resetThenReplayFromStart` / `refusesNonReplayableSource` / `refusesActiveCoordinator`（runtime 模块）。

### 常见故障表

| 症状 | 根因 | 处置 |
|---|---|---|
| 启动即 `ERR_STREAM_CONFIG_ERROR`（connector name） | `DebeziumConfig.name` 未设置——offset 会共享 `_default_` 桶 | 为每个 CDC 作业设置唯一 connector name |
| 恢复后从头全量重读（意外 snapshot） | `cdc-offsets` 状态未随 checkpoint 持久化（如 checkpoint 未启用/未 durable） | 确认 `<checkpoint enabled="true">` 且作业运行期有 durable checkpoint（owner doc `/jobs/{jobId}/checkpoints` 可查） |
| 恢复后位点了但下游出现重复 | 下游 sink 非 2PC（exactly-once 只在 REPLAYABLE source + TWO_PHASE_COMMIT sink 组合下成立） | 核对连接器矩阵语义组合规则；换 2PC sink 或接受 at-least-once + 下游幂等 |
| `ERR_STREAM_STATE_SCHEMA_MISMATCH` | 恢复的状态指纹与当前描述子不符且未注册迁移函数 | 按第三节注册 `StateMigrationFunction`，或 reset-state 全新重跑 |
| 同 JVM 重跑吃到上一次运行的位点 | （已修复语义）fresh start 主动清 registry 陈旧 offset——若自定义子类绕过 `initializeState` 则失去该保证 | 子类必须沿用生产 `initializeState`/`snapshotState` 路径（`ReplayableCdcSourceFunction` 即此模式） |
| 想重放到任意历史位点 | 无 offset 手动改写工具 | 全新重跑 + 上游可重放存储；或等位点编辑工具（未承诺） |
