# nop-stream 版本化迁移指南（首版）

> 定位：nop-stream 流作业在版本间行为变更时的迁移指南首版——覆盖 **XDSL 拓扑变更**与**状态格式变更**两条轴 + 版本策略声明。
> CDC 专项见 `03-modules/nop-stream-cdc-cookbook.md`；运维操作（savepoint/reshard/reset-state）权威步骤见 owner doc `03-modules/nop-stream.md`「运维手册」节。

## 版本策略声明（先读）

- nop-stream **尚无已发布版本**，因此本指南是**前瞻性政策**：规定「未来发生不兼容变更时，变更如何被识别、迁移如何执行」。
- 三个独立的版本轴（live 现状，each 均有 fail-fast 校验）：

| 版本轴 | 载体 | 当前值 | 兼容语义 |
|---|---|---|---|
| checkpoint 信封格式 | `CheckpointSerDe` 的 `formatVersion` 字段（checkpoint JSON + epoch manifest JSON 均打标） | `2`（legacy `1`） | 缺失字段 = legacy v1，**向后兼容接受**（debug 日志）；未来格式变更递增该数字 |
| RocksDB key 布局 | `RocksDBKeyEncoder` 的 `keyLayoutVersion`（v2 = `[keyGroupId:int32 BE][nsLen][nsJson][keyLen][keyJson]`，key-group 前缀连续以支持增量恢复） | `2`（legacy `1`） | 增量恢复**要求恰好 v2**（v1/缺失 fail-fast）；全量恢复容忍缺失版本字段（跨后端 Memory 快照存原始用户键） |
| 单状态 schema 指纹 | `SerializerFingerprint.schemaVersion` + `schemaChecksum`（SHA-256 over 类型签名 canonical 串） | `schemaVersion=1`（前瞻预留，恒定） | 恢复时按 `schemaChecksum` 相等校验，不匹配且无迁移函数 → `ERR_STREAM_STATE_SCHEMA_MISMATCH` fail-fast |

- **不承诺未落地机制**：manifest 级 `stateFormatVersion` 与整包 checksum 字段**尚未落地**（设计文档中的前瞻表述不构成本指南承诺）；跨版本升级兼容测试基建已显式 defer（无已发布版本、无测试对象）。
- **revisit 触发点**：首次格式版本递增或首个发布版本出现时，重估跨版本升级测试基建（含旧版本产物 → 新版本恢复的自动化升级路径）。

## 轴一：XDSL 变更（拓扑定义演进）

`.stream.xml` 是声明式模型（xdef 合同 `/nop/schema/stream/stream.xdef`），版本间拓扑迁移的主机制是平台标准 **Delta**：

### 拓扑演进

| 变更 | 迁移机制 | 约束 |
|---|---|---|
| 上游基座升级、本地派生保持 | `x:extends="/path/to/base.stream.xml"` 显式继承，或 `_delta` 分层目录（`x:extends="super"` 自动叠加） | transform 按 `id` 合并；被继承方新增 transform 不破坏派生方 |
| 只调参数不动拓扑 | 仅改 `parallelism` / `<checkpoint>` 配置 | 模型 fingerprint 不变（设计如此——配置派生不产生新拓扑身份） |
| 插入/删除节点 | Delta 中新增 `<transform>` + 重接 `<edge>` | 边按 `from`/`to` 显式声明；被绕开的旧边须显式移除 |

行为由 `TestStreamModelDeltaExtends`（flow 模块）钉定；产品级示例：fraud-example 的 `fraud-s2-file-delta.stream.xml` 在 S2 基座上插入黑名单 filter。

### bean 引用兼容约束

- transform 的 `bean="..."` 引用经 NopIoC `BeanContainer` 解析——**bean id 与签名是跨版本兼容面**：基座模型升级若重命名/删除 bean id，派生模型 build 期 fail-fast（不会静默丢 transform）。
- `<windowingStrategies>` 等组件注册表按稳定 ID 引用：改 ID = 破坏派生方，须保持 ID 稳定或同步迁移派生文件。
- XDSL 面不支持的配置（`asyncSnapshot*`、`unaligned*`、`maxRestartsPerRegion` 等 Java-only 项）不因版本升级而自动进入 XDSL——以 `stream.xdef` 为准。

## 轴二：状态格式变更

### key-group 布局与 reshard

- key→key-group 路由：`stableHash(key) % maxParallelism`（默认 maxParallelism=128，上界 32768）——**maxParallelism 变更会重哈希全部 key 的分组映射**。
- 改 maxParallelism 的唯一支持路径 = **离线 reshard**：

  ```bash
  <java> io.nop.stream.runtime.maintain.StreamMaintenanceMain reshard \
      oldSavepointPath=<path> oldMaxParallelism=<n> newMaxParallelism=<m> outputBaseDir=<dir>
  ```

  语义：读旧 savepoint（只读）→ 全局 key 池按新 maxParallelism 重分布 → 原子写新 savepoint（`.tmp` + rename）+ `reshard-report.json` 审计报告；per-state key 数守恒断言；`old==new` 拒绝；operator（非 keyed）状态按下标 1:1 复制、扩容 subtask 从空起步。锚点：`TestMaxParallelismReshardMigrationE2E`（128→256 / 256→128 / rocksdb 恢复正确性 / key 守恒 / fail-fast 族）、产品级 `TestS2OfflineReshardE2E.s2OfflineReshard128to256ThenRestoreCompletesExactlyOnce`。
- **改 parallelism（不改 maxParallelism）**：无需 reshard——key→group 映射不变，仅 group→subtask 划分重排（跨集群 stop-the-world 重启即可，见 owner doc「恢复操作」）。

### 序列化指纹与状态 schema 迁移

- 每个状态在快照中携带 `SerializerFingerprint`（stateName + schemaVersion + schemaChecksum）；恢复时与当前 `StateDescriptor` 的指纹比对。
- **兼容变更**（指纹不变）：值类型不变的业务逻辑调整——无需任何动作。
- **不兼容变更**（指纹变化，如 `Integer`→`Long`）：必须注册迁移函数，否则恢复 fail-fast：

  ```java
  StateMigrationFunction<Integer, Long> fn = ...;  // migrate / sourceFingerprint / targetFingerprint
  streamComponents.registerStateMigrationFunction("myState", fn);
  ```

  迁移在恢复后首次 `getState()` 同步执行（元素处理前），幂等。锚点：`TestStateMigrationEndToEnd`（memory + rocksdb 双后端全链 round-trip 与 fail-fast 对照）、`TestStateMigrationFunctionRegistration`（注册/查询语义 10 用例）。
- **accumulator 状态注意**：Reducing/Aggregating 状态的 accumulator 是不透明对象——schema 变更时 accumulator 迁移是用户责任（无法自动转换）。
- CEP 状态（NFA computationStates / SharedBuffer）：经 `__java_bytes__` base64 marker 走 Java 序列化——**CEP 内部状态格式随引擎版本演进，不做跨引擎版本迁移承诺**；跨版本恢复 CEP 作业前先验证（或 reset-state 重跑）。

### JDBC 2PC 台账 schema（复合主键，D2 裁定）

`JdbcTwoPhaseCommitSink` 的 epoch 台账表现行 DDL 主键为**复合键 `(epoch_id, subtask_id)`**（per-subtask 提交幂等守卫的载体，DDL 由 `getLedgerTableDDL()` 提供）：

- commit `2de622fb6a` **之前**创建的单列（仅 `epoch_id` 主键）遗留台账表：`CREATE TABLE IF NOT EXISTS` 对旧表是 no-op，随后按复合列的 INSERT/SELECT 在旧表上**响亮失败**（column not found / count mismatch）——不存在静默丢数据路径。处置口径（D2 裁定，`checkpoint-design.md` §6.4.2）= **DROP 旧表后按现行 DDL 重建**（无已发布版本，存量仅测试/演练库，不加代码探测）：

  ```sql
  DROP TABLE stream_epoch_ledger;
  -- 然后任选：调用 JdbcTwoPhaseCommitSink.initializeLedgerTable()，或手工执行
  CREATE TABLE IF NOT EXISTS stream_epoch_ledger (
      epoch_id BIGINT NOT NULL, subtask_id INT NOT NULL, committed_at TIMESTAMP,
      PRIMARY KEY (epoch_id, subtask_id));
  ```

  重建只丢幂等守卫历史（台账行），**不丢业务数据**；旧表上已提交的数据行不受影响。
- 台账表跨 sink 实例共享约束：复合键无 vertex 维度——同库多链 2PC sink 须各用独立 ledger 表（fraud-example S1 先例：4 链 4 ledger 表）。

### 状态重置（不迁移，全新重跑）

`StreamMaintenanceMain reset-state`（拒绝语义与操作步骤见 CDC cookbook 第四节 / owner doc）——当迁移成本高于重放成本时的正当选择。

## 迁移决策速查

| 场景 | 动作 |
|---|---|
| 只改 XDSL 配置/拓扑（状态 schema 不变） | Delta 派生 + 正常重启（自动从 durable checkpoint 恢复） |
| 改并行度（maxParallelism 不变） | stop-the-world 重启（TM 数可变）。**例外**：含 2PC sink 的顶点恢复时并行度须与快照一致——跨并行度恢复被 typed 拒绝（`ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED`，D1 裁定），改 2PC sink 并行度需 reset-state 重跑或全新作业 |
| 改 maxParallelism | 离线 `reshard` → 新目录恢复 |
| 状态值类型/结构变更 | 注册 `StateMigrationFunction`（或 reset-state 重跑） |
| 遗留单列 2PC 台账表（`2de622fb6a` 前创建） | DROP 后按现行复合主键 DDL 重建（见「JDBC 2PC 台账 schema」节） |
| checkpoint 格式不兼容（信封 formatVersion 拒绝） | 无迁移工具——reset-state 重跑（当前 formatVersion=2 向后兼容 legacy v1，此场景仅在远期版本出现） |
