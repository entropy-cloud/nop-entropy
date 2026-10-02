# nop-stream CDC 生产化 Cookbook

> 定位：Debezium CDC source（`DebeziumCdcSourceFunction`，`nop-stream-connector-debezium`）与 CDC 落库（`jdbc-2pc` dmlMode）的生产操作手册。
> 设计基线：Debezium 3.7 嵌入式引擎、两层单机并行（A 层引擎内 chunk 快照并行 + B 层 subtask 表路由）、connectorType 白名单 mysql/postgres、schema history 无静默默认、offset 真相源钉定；连接器能力矩阵见 `03-modules/nop-stream-connectors.md`；checkpoint 机制总览见 owner doc `03-modules/nop-stream.md`。
> 本文关键声称带 live 行为核对锚点（测试名），均可通过模块测试复现；真实数据库行为由 gated 集成测试钉定（`TestDebeziumRealMysqlCdc` / `TestDebeziumRealPostgresCdc`，docker 不可达时显式 SKIP）。

## 机制总览（先读）

- 引擎面：Debezium **3.7.x.Final** 嵌入式引擎（`AsyncEmbeddedEngine` 默认实现）；事件处理并发被钉定为 `record.processing.threads=1`（保护 source 单写者不变量），调高 = 类型化拒绝（`TestDebeziumEngineProperties.testProcessingThreadsPinned`）。
- **connectorType 白名单 = mysql / postgres**：其余值在引擎配置构建期 fail-fast，错误参数携带支持清单（`TestDebeziumEngineProperties.testConnectorWhitelist`）。
- CDC source 声明语义 `REPLAYABLE`：Debezium engine 的 offset 经 **`NopStreamOffsetBackingStore`** 桥接进 nop-stream checkpoint 协议——nop checkpoint 是 offset 的**唯一恢复真相源**；`extraProperties` 覆盖 `offset.storage` = 类型化拒绝（`testOffsetStorageOverrideRejected`）。
- offset 存储：`snapshotState(checkpointId)` 把 offset map（base64 TreeMap）写入 operator state，key = **`cdc-offsets`**；恢复时 `initializeState` 读出并预填充 backing store，engine 从位点续读。
- engine 侧接线：Debezium engine builder 无 `using(OffsetBackingStore)` 入口（3.7 仍未暴露），`NopStreamOffsetBackingStore` 经 `offset.storage` 配置反射实例化，并以 **static registry（connectorName → 共享 map）** 与 source-function 侧实例互通（`TestDebeziumCdcCheckpoint.testOffsetStoreInjectionChain` 钉定三层接线）。
- **connector name 必填**：`DebeziumConfig.name` 缺失即 fail-fast（`ERR_STREAM_CONFIG_ERROR`）——未命名连接器会共享 `_default_` offset 桶造成位点污染。

## 一、首次启动：snapshot → 增量切换

1. **配置**（`DebeziumConfig`）：`name`（必填）、数据库连接、`snapshotMode`（默认 `initial`——无 offset 时先全量快照，有 offset 时直接增量续读）。
2. **A 层并行快照（单表 chunk 并行）**：`snapshotMaxThreads`（默认 1 = 串行）>1 时透传 Debezium `snapshot.max.threads`，大表 initial snapshot 按主键 chunk 多线程读取（`TestDebeziumEngineProperties.testSnapshotMaxThreadsPassthrough`）。
   - 边界：无主键表回退表级并行；同表行序在 chunk 间无全局保证——**快照期写入必须走幂等 upsert**（见第五节 dmlMode）。
3. **B 层并行（subtask 表路由）**：source `parallelism = N > 1` 时按 `floorMod(表名.hashCode(), N)` 确定性分片，每实例独立引擎与实例名 `{name}-{subtaskIndex}`（offset registry、失败监听、Debezium `name` 全部按实例隔离）；部署期校验：`tableIncludeList` 必填、条目数 ≥ N、hash 空分片 fail-fast；单表并行请用 A 层（`TestDebeziumCdcSharding` 全链钉定）。能力描述符已声明 `PARALLEL`。
4. **首次启动判定**：`initializeState(state)` 中 `state == null` 或无 `cdc-offsets` 条目 → fresh start，主动清除 static registry 中同名（实例名）连接器的**陈旧 offset**（`TestDebeziumCdcCheckpoint.testFirstRunStateNullDoesNotResumeFromStaleOffsets`、`testFirstRunRestartStartsFromBeginning`）。
5. **schema history（必配，无静默默认）**：`schemaHistoryStore` 默认 `jdbc`（`JdbcSchemaHistory`，要求 `schemaHistoryJdbcUrl`，用户/密码支持 `credential:{id}#{field}` 引用并引擎侧瞬态解密）；`file` 模式走 `schemaHistoryPath`（单机回退）。两者皆缺 = 配置错误——静默内存 schema history 已废除（`TestDebeziumEngineProperties.testSchemaHistory*`）。
6. **增量信号表（预留）**：`signalDataCollection` 透传 `signal.data.collection`，为信号驱动的增量快照预留。
7. **schema change 事件**：`includeSchemaChanges` 仅映射 MySQL 连接器（Postgres 经 schema history 承载 DDL 上下文）；默认不外发。

## 二、offset 恢复与重放

1. **恢复路径**：作业重启 → 从最近 durable checkpoint 恢复 → `initializeState` 从 `cdc-offsets` 条目恢复 offset 到 backing store → engine 续读。
   - 核对锚点（offset round-trip）：`TestDebeziumCdcCheckpoint.testSnapshotStateStoresOffsetsFromStore` / `testInitializeStateRestoresOffsetsToStore` / `testSnapshotRestoreRoundTrip`；真实 MySQL 全链路（snapshot→stream→kill→restore→续传不重不丢）由 gated `TestDebeziumRealMysqlCdc.testSnapshotStreamKillRestoreResume` 钉定。
2. **重放语义**：恢复后从 checkpoint 位点重读——checkpoint 之后、崩溃之前已发的事件会**重发**（source 边界 at-least-once）；配合 2PC sink 端到端 exactly-once。**键控 sink（UPSERT/UPSERT_DELETE）使重放结构性安全**（第五节）。
3. **B 层并行的恢复**：operator state 本就 per-subtask——每实例恢复自己的 offset，路由函数是纯函数（表名 + N 决定分片），restore 后分片一致。
4. **升级语义**：Debezium 大版本升级（如 2.x → 3.x）存在 offset 格式破坏性变更，**不支持跨大版本位点恢复**——升级 = 重新 initial snapshot，安全性由键控 upsert 幂等保证（cdc-design.md §3.4）。
5. **重放到任意历史位点**：无 offset 手动改写工具——用「全新重跑」（第四节）+ 上游可重放存储配合。

## 三、schema 演进边界

**支持面**（状态下线后的状态 schema 迁移，`StateMigrationRegistry` 机制）：

1. 注册迁移函数：`StreamComponents.registerStateMigrationFunction(stateName, StateMigrationFunction)`——按 source/target 的 `schemaChecksum` 匹配；迁移在恢复后**首次 `getState()` 时同步执行**，幂等。
   - 核对锚点：`TestStateMigrationEndToEnd.*`（runtime 模块，两后端全链）。
2. 未注册迁移 + 指纹不匹配 → **fail-fast**（`ERR_STREAM_STATE_SCHEMA_MISMATCH`），不静默丢状态。

**不支持面（显式声明，defer 裁定见 cdc-design.md §3.8）**：

- **源表 DDL 变更不自动适配**：无 schema evolution DDL 协调/执行。上游 DDL 的行为边界 = schema change 事件透传（`includeSchemaChanges`）+ 告警 + 人工重同步手册；键控 sink 对新增列会在批内以缺列 fail-fast（列集钉定），不会静默错写。
- 无自动 schema diff 工具 / 无跨格式自动迁移：迁移函数必须用户手写注册。

## 四、故障排查与全新重跑

### 全新重跑（reset-state）

```bash
<java> io.nop.stream.runtime.maintain.StreamMaintenanceMain reset-state \
    jobId=<id> checkpointBaseDir=<dir> sourceReplayable=true
```

- 语义：删除 `<base>/<jobId>/` 全部 durable checkpoint + epoch manifest + 其携带的 source 位点——同 jobId 重新拉起即从**起点**全量重跑。
- 拒绝语义（无静默清空）：`sourceReplayable=false` 显式报错；注册表存在活跃 coordinator 显式报错；状态目录不存在显式报错。
- 锚点：`TestStreamStateResetTool.*`（runtime 模块）。

### 常见故障表

| 症状 | 根因 | 处置 |
|---|---|---|
| 启动即 `ERR_STREAM_CONFIG_ERROR`（connector name） | `DebeziumConfig.name` 未设置 | 为每个 CDC 作业设置唯一 connector name（B 层并行时为实例名前缀） |
| `ERR_DEBEZIUM_CONFIG_INVALID`（schemaHistoryJdbcUrl） | `schemaHistoryStore` 默认 jdbc 但未配 URL | 配 `schemaHistoryJdbcUrl`，或 `schemaHistoryStore=file` + `schemaHistoryPath`（单机回退） |
| `ERR_DEBEZIUM_CONFIG_INVALID`（offset.storage） | extraProperties 试图覆盖 offset 存储 | offset 存储由 nop checkpoint 协议管理，删除该覆盖项 |
| `ERR_DEBEZIUM_UNSUPPORTED_CONNECTOR_TYPE` | connectorType 不在白名单 | 仅支持 mysql / postgres；其余数据库走外围连接器扩展 |
| 部署期 `ERR_STREAM_CONFIG_ERROR`（tableIncludeList / zero tables） | B 层并行配置问题：无表清单 / 条目数 < parallelism / hash 空分片 | 补齐表清单或调低并行度；单表并行改用 `snapshotMaxThreads` |
| 恢复后从头全量重读（意外 snapshot） | `cdc-offsets` 状态未随 checkpoint 持久化 | 确认 checkpoint enabled 且运行期有 durable checkpoint |
| 恢复后下游出现重复 | 下游 sink 非 2PC 或非键控 | REPLAYABLE source + TWO_PHASE_COMMIT sink 组合 + 键控 dmlMode（重放安全结构性成立） |
| `ERR_STREAM_STATE_SCHEMA_MISMATCH` | 状态指纹不符且未注册迁移 | 注册 `StateMigrationFunction` 或 reset-state |
| 想重放到任意历史位点 | 无 offset 手动改写工具 | 全新重跑 + 上游可重放存储 |
| MySQL initial snapshot 期间 jdbc schema history 写入被阻塞 ~30s 后连接被杀（processlist 显示 `Waiting for global read lock`） | MySQL 快照默认持全局读锁（FTWRL），而 JDBC schema history 从第二条连接写历史——结构性互斥 | `extraProperties` 设 `snapshot.locking.mode=none`（binlog + REPEATABLE READ 保证一致快照）；或快照期用 file schema history。gated `TestDebeziumRealMysqlCdc` 钉定 |
| JdbcSchemaHistory 建表失败：`Column length too big for column 'history_data'` | Debezium 默认 DDL 为 `history_data VARCHAR(65000)`，超 MySQL utf8mb4 行宽（16383 字节） | 按其运行时列结构预建表（`history_data` 用 MEDIUMTEXT；列 = id/history_data/history_data_seq/record_insert_ts/record_insert_seq，PK(id, history_data_seq)），存储检测到表存在即跳过 DDL。gated `TestDebeziumRealMysqlCdc` 钉定 |
| 容器化 MySQL 上引擎连接在初始化窗口被 EOF | MySQL 官方镜像有「临时 server → SHUTDOWN → 正式 server」自重启，容器就绪探测可能落在临时 server | 引擎启动前等待 server 稳定（测试内 `awaitStableMySql` 模式） |

## 五、CDC 落库（jdbc-2pc dmlMode）

CDC 变更正确落库用 `JdbcTwoPhaseCommitSink` 的 `dmlMode`（cdc-design.md §3.5）：

| dmlMode | 行为 | 用途 |
|---|---|---|
| `INSERT`（默认） | 追加插入；收到 u/d 语义的 ChangeEvent fail-fast | 纯流式落表 |
| `UPSERT` | c/r/u 按 keyColumns 覆盖写；d 事件 fail-fast | 快照 + 增量（表镜像不含删除） |
| `UPSERT_DELETE` | upsert + d 按 key 删除 | 完整 CDC 镜像 |

- 输入两条路径：**原生 ChangeEvent 直连**（不配 `recordMapper`，operation/after/key 从信封读取）或 **recordMapper + `opMapper` + `deleteKeyMapper`**（泛型记录）。
- 键控模式必配 `keyColumns`；方言 upsert：MySQL `ON DUPLICATE KEY UPDATE`、PostgreSQL `ON CONFLICT DO UPDATE`（其余方言键控模式类型化拒绝）。
- 批内同 key **last-write-wins 折叠**；跨 epoch 保序由 2PC epoch 序列保证；upsert 幂等 + ledger 查重使「恢复重放」结构性安全。
- 核对锚点：`TestJdbcTwoPhaseCommitSinkDml`（往返/折叠/守卫/ledger 复用，H2 真库）；真实 MySQL/PostgreSQL 方言由 gated 集成测试覆盖。
