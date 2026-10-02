# CDC 子系统设计——Debezium 升级、单机并行与 DB 内置同步

**日期**：2026-10-02（v2，同日重写）
**范围**：`nop-message/nop-message-debezium`、`nop-stream/nop-stream-connector-debezium`、`nop-stream/nop-stream-connector-jdbc`、`nop-stream-core` 连接器注册契约（`connector-design.md` §5.4/§8 修订）
**状态**：active（核心决策已于 2026-10-02 由用户裁定；同日裁定：**此前 CDC 实现从未发布，无任何兼容性包袱**，一切契约以本设计为准）
**裁定来源**：用户 2026-10-02 设计裁定——升级 Debezium 至最新；并行处理采用**单机多线程**（不做分布式）；外部连接器只考虑 **MySQL + PostgreSQL**；nop 整体定位为**围绕数据库系统提供内置解决方案，其余留给外围扩展**；在此范围内取代 SeaTunnel 的数据同步职能；**无需兼容旧实现**。

---

## 一、设计结论

1. **版本**：Debezium `3.7.0.Final`（当前 latest，2026-09-29 发布），版本属性在 `nop-message-debezium` 单点管理；引擎实现为 3.2+ 的默认 `AsyncEmbeddedEngine`（旧 `EmbeddedEngine` 已从上游移除）。
2. **连接器白名单**：`connectorType` 仅接受 **`mysql` / `postgres`**，其余值 = 类型化错误（含支持清单提示）。不保留 sqlserver 或其他映射——无发布用户，无过渡期需要。
3. **并行 = 两层单机多线程**：A 层 `snapshotMaxThreads` 配置透传 Debezium 3.5+ 的 `snapshot.max.threads`（引擎内 chunk 级并行 initial snapshot，默认 1）；B 层 **subtask 级确定性表路由**——source `parallelism = N` 时按 `hash(表名) mod N` 把表分片到各 subtask，每实例独立 connector name 与 offset，复用既有 checkpoint 机制（零新协议）。明确不做跨节点 chunk 分发。
4. **offset 真相源**：CDC 位点唯一由 `NopStreamOffsetBackingStore` 进 nop checkpoint operator state；该存储经 `offset.storage` 属性注入 + connector-name registry 桥接（3.7 的 `DebeziumEngine.Builder` 仍不暴露 `using(OffsetBackingStore)`，官方 engine 文档确认属性注入是唯一路径），且**契约钉定禁止换轨**——外部配置试图覆盖 `offset.storage` = 类型化错误。
5. **schema history**：默认 `JdbcSchemaHistory`（`debezium-storage-jdbc`，官方稳定存储），连接参数走 `credential:{id}#{field}` 凭证引用；`file` 模式保留为单机零依赖回退。不再使用内存 schema history 作为静默默认（静默丢 schema 上下文是错误行为，无历史时 fail-fast 或显式选择）。
6. **CDC 目标端**：`jdbc-2pc` sink 的 `dmlMode` 契约——`INSERT`（追加）/ `UPSERT`（快照+增量，r/c/u 全部覆盖写）/ `UPSERT_DELETE`（d 事件按 key 删除）；方言 upsert 语句由 sink 内建的 MySQL/PG 生成器产出（不动 `nop-dao` 的 `IDialect` 公共面）；epoch 2PC + ledger 幂等机制全复用，不引入 XA。
7. **单写者钉定**：`AsyncEmbeddedEngine` 的事件处理并发被钉定为单线程投递（`record.processing.threads=1`），保护 `00-vision.md` 不变量 #4（Barrier 由 source 读取线程注入）；A 层并行快照的并发发生在 Debezium 连接器内部，对 nop 仍是单点事件流入口。
8. **取代 SeaTunnel 的范围**：DB→DB CDC 同步、批量初始同步、DB→文件/消息导出、流式窗口/CEP 加工为内置目标（矩阵见 §六）；连接器生态、分布式集群、schema evolution DDL 协调、UI 为外围/延迟项。

## 二、背景与动机

- 前置事实（代码核实）：旧 CDC 集成（Debezium 2.4.0.Final）initial snapshot 串行；schema history 仅文件/内存两种且默认内存（静默）；`jdbc-2pc` 只生成 `INSERT INTO`（无法应用 UPDATE/DELETE）；`DebeziumCdcSourceFunction` 经 `runEntered` CAS 实际只能单实例运行（并行 >1 时第二个 subtask 静默 EOS）。
- Debezium 3.x 能力基线（官方发布信息）：3.5.0.Final GA 单表 chunk 级多线程并行快照（`snapshot.max.threads` + `snapshot.max.threads.multiplier`，默认 1）；3.2 起 `AsyncEmbeddedEngine` 默认（`record.processing.threads` 可并发处理事件）；Java 17 baseline（平台编译目标 release 17，兼容）；`debezium-storage-jdbc` 稳定（`JdbcSchemaHistory`：`schema.history.internal=io.debezium.storage.jdbc.history.JdbcSchemaHistory` + `schema.history.internal.jdbc.*` 属性族）；MySQL/PostgreSQL 连接器为单 task 连接器（流式阶段本质单线程）。
- 定位驱动：nop 围绕数据库系统提供内置解决方案——CDC 与数据同步是数据库场景的高频内置需求。SeaTunnel 在该场景的核心手段（嵌入式 CDC、并行快照、exactly-once 落库）由「Debezium 升级 + 两层并行 + CDC sink」三件收敛，无需引入外部集成引擎。

## 三、核心设计

### 3.1 版本与依赖（D1）

| 依赖 | 版本/说明 |
|---|---|
| `debezium-bom` / `debezium.version` | `3.7.0.Final`，属性单点，各 artifact 不再散落版本号 |
| `debezium-api` + `debezium-embedded` | 引擎面（`compile`） |
| `debezium-connector-mysql` / `debezium-connector-postgres` | `provided` + `optional`（运行时按 connectorType 装配，编译期仅类型引用） |
| `debezium-storage-jdbc` | `compile`（`JdbcSchemaHistory` 为默认 schema history；自身依赖轻量 JDBC，无 Kafka Connect 面） |
| sqlserver 连接器依赖 | **删除** |

验收口径：既有 debezium 单元测试全部通过（mock 语义随新契约修订）+ 新增真实 MySQL/PostgreSQL 集成测试（Testcontainers）覆盖 snapshot→stream→cancel→restore→续传闭环；单元测试全 mock 的历史缺口随本设计关闭。

### 3.2 连接器白名单（D2）

`DebeziumEngineConfig` 仅映射两个 connectorType；未知名 = `ERR_DEBEZIUM_UNSUPPORTED_CONNECTOR_TYPE`（错误参数含受支持清单）。`DebeziumConfig.connectorType` javadoc 同步收敛。

### 3.3 单机并行模型（D3，两层）

**A 层——引擎内 chunk 并行（吞吐杠杆）**

`DebeziumConfig.snapshotMaxThreads`（默认 1）→ `snapshot.max.threads`；`extraProperties` 透传 `snapshot.max.threads.multiplier`（全局/按表）。边界：无主键表与 select override 表回退表级并行；同表行序在 chunk 间无全局保证——快照期写入必须走幂等 upsert（§3.5），这是 A 层的正确性前提。

**B 层——subtask 级表路由（并行度杠杆）**

新增 core 契约 `SubtaskShardedSourceFunction`（`functions/source/`，与 `ParallelismCheckable` 同族）：

```
SubtaskShardedSourceFunction<T> extends SourceFunction<T>, ParallelismCheckable
  + copyForSubtask(int subtaskIndex, int totalParallelism): SubtaskShardedSourceFunction<T>
```

- **验证**：`validateParallelism(N)` 在执行计划构建期调用（既有 `GraphExecutionPlan` 钩子）；`N > 1` 时要求 `tableIncludeList` 非空且条目数 ≥ N 的可行性检查——单表 + N>1 = 配置错误 fail-fast（单表并行用 A 层线程）。
- **路由**：`route(表名) = unsignedMod(表名.hashCode(), N)`（`String.hashCode` 由 JLS 规定跨 JVM 稳定，路由确定性成立；表名分布在小 N 下足够均匀）。路由粒度 = `tableIncludeList` 的逗号分隔条目。
- **实例身份**：实例 connector name = `{name}-{subtaskIndex}`（offset registry 与 failure listener 键、Debezium `name` 属性全部按实例名；首跑 `clearConnector` 生命周期按实例名适用）。原始 `config` 对象保持不变（可序列化、凭证引用驻留），每 subtask 副本在引擎侧构造**有效配置**：name 与 `table.include.list` 替换为本实例子集。
- **恢复**：每 subtask 独立 operator state（`cdc-offsets` 键下按实例隔离——operator state 本就 per-subtask，无新协议）；副本经序列化往返创建（函数可序列化，与 `effectiveEngineConfig` 的既有技术一致）。
- **接线**：`StreamSourceOperator.copyForSubtask(int)` 覆写——函数实现 `SubtaskShardedSourceFunction` 时创建携带身份的函数副本（现状：函数跨 subtask 共享）；未实现该接口的函数维持共享语义零回归。

**能力描述符**：`debezium-cdc` 并行度 `SINGLE_INSTANCE` → **`PARALLEL`**；恢复语义 `OFFSET_CHECKPOINT`、交付语义 `REPLAYABLE` 不变（§8.4 矩阵行与注册发现测试同步）。

**明确不做**：跨节点分布式 chunk 分发——用户裁定单机多线程足够；与 `00-vision.md` Non-Goal「大规模并行」一致。单 JVM 内吞吐上限 = 单机资源，是接受的能力边界。

### 3.4 状态存储分工（D6）

```
┌─ nop checkpoint（真相源，恢复语义归属）─────────────────┐
│  CDC offsets（operator state "cdc-offsets"）            │
│  ← snapshotState/initializeState 读写，restore 后       │
│    pre-populate 到 NopStreamOffsetBackingStore registry │
└──────────────┬──────────────────────────────────────────┘
               │ 引擎运行态 flush 载体（契约钉定，禁止换轨）
┌──────────────┴──────────────────────────────────────────┐
│  NopStreamOffsetBackingStore（offset.storage 属性注入）  │
└─────────────────────────────────────────────────────────┘
┌─ Debezium storage 侧（引擎内运行态）────────────────────┐
│  schema.history.internal = JdbcSchemaHistory（默认）     │
│                           或 FileSchemaHistory（回退）    │
└─────────────────────────────────────────────────────────┘
```

- **offset 不用 `JdbcOffsetBackingStore`**：nop checkpoint 是恢复真相源；若 offset.storage 换轨外部表，restore 后 pre-populate 的位点与引擎从外部表读回的位点两主并存，语义必错。`DebeziumEngineConfig.buildProperties` 恒定注入 `NopStreamOffsetBackingStore`，用户 `extraProperties` 携带 `offset.storage` = 类型化错误。
- **schema history 默认落库**：`DebeziumConfig.schemaHistoryStore: jdbc|file`，默认 `jdbc`；JDBC 属性族（url/user/password/table）从 `DebeziumConfig` 字段生成，凭证字段支持 `credential:` 引用（引擎侧瞬态解密，与既有 user/password 机制同型）。`file` 模式 = `schemaHistoryPath`（单机回退）。未配置任何 schema history = 配置错误（不再静默内存）。

### 3.5 CDC 目标端 sink：DML 语义（D5）

`JdbcTwoPhaseCommitSink` 增加 `dmlMode` 契约：

| dmlMode | 行为 | 用途 |
|---|---|---|
| `INSERT`（默认） | 追加插入 | 纯流式落表（现状语义） |
| `UPSERT` | 按 key 覆盖写；`d` 事件 = 类型化错误（UPSERT 不承诺删除） | 快照 + 增量（r/c/u） |
| `UPSERT_DELETE` | upsert + `d` 事件按 key 删除 | 完整 CDC 镜像 |

- 输入契约：记录必须是携带 `operation` 语义的 CDC 事件——sink 接受 `ChangeEvent`（`nop-message-debezium` 信封：c/u/d/r + before/after/key），或经 `recordMapper` + `dmlModeMapper` 双函数映射任意记录（value 列 + 操作码）。dmlMode=`INSERT` 时若输入为 `ChangeEvent` 且 operation 语义非插入 = fail-fast（防静默错写）。
- 主键来源：显式 `keyColumns` 配置（必需，UPSERT/UPSERT_DELETE 模式）；不猜主键。
- 方言 upsert 生成器（sink 内建，参照 `JdbcCheckpointStorage` 的 UpsertDialect 先例）：MySQL `INSERT ... ON DUPLICATE KEY UPDATE`；PostgreSQL `INSERT ... ON CONFLICT (key) DO UPDATE SET ... = EXCLUDED.*`；delete = 参数化 `DELETE WHERE pk=?`。批内同 key 多事件**保序折叠**（last-write-wins），跨 epoch 保序由 epoch 序列保证。
- 2PC/ledger 全复用：saveState-first、每 epoch 一事务、ledger 查重跳过。upsert 幂等使重放安全结构性成立。
- **拒绝 XA**：epoch ledger 模型已钉定并被测试覆盖（connector-design §5.3.2 D1）；XA 增加数据库前提与悬挂事务恢复复杂度。

### 3.6 引擎线程模型（D7）

`DebeziumEngineConfig` 钉定 `record.processing.threads=1`；用户 `extraProperties` 试图调高 = 类型化错误（保护 SourceContext 单写者，`00-vision.md` 不变量 #4）。A 层快照线程池在连接器内部，不受此限。

### 3.7 可观测性与配置契约（D8）

- `DebeziumConfig` 新增字段：`snapshotMaxThreads`、`schemaHistoryStore`（jdbc|file）、schema history JDBC 参数（host 侧直接复用平台凭证引用）、`signalDataCollection`（透传 `signal.data.collection`，增量快照信号表预留）。
- 指标：快照进度（每表 chunk 完成/总数）、事件速率、offset lag——挂 observability-design 分层命名的 cdc 指标族；引擎终死 failure listener 已有（R5-CON-03）。
- 上游 DDL 风险运行边界（不含 schema evolution 协调，defer）：事件透传（`includeSchemaChanges`）+ 告警 + 人工重同步手册。

## 四、拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| 跨节点分布式并行快照 | 用户裁定单机多线程足够；vision Non-Goal「大规模并行」；chunk-as-split 需 enumerator 级新协议 |
| Flink CDC / SeaTunnel 作为依赖 | 外部集成引擎成为能力链支柱，违反自完备设计 |
| Debezium 内核复用 + 自研补丁（SeaTunnel 路线） | 3.5 已内置线程级并行快照，内核补丁的护城河价值消失，版本锁死代价保留 |
| offset 换轨 `JdbcOffsetBackingStore` | 两主并存语义污染；nop checkpoint 是唯一恢复真相源（§3.4） |
| XA sink | epoch ledger 模型已钉定；XA 增加数据库前提与悬挂事务恢复复杂度（§3.5） |
| schema evolution DDL 协调/执行 | defer——触发条件：首个真实需求；现以事件透传+告警+重同步手册兜底 |
| 内存 schema history 作为静默默认 | 静默丢 schema 上下文是错误行为；无历史 = fail-fast 或显式选择（§3.4） |
| 调高引擎事件处理并发 | 破坏单写者不变量（§3.6） |
| `String.hashCode` 之外的强哈希路由 | 表名是小基数分布，JLS 规定的稳定 hashCode 已满足确定性；引入 SHA 强哈希无第二消费者 |

## 五、与已有设计的关系

- **connector-design.md §5.4.2 D1**：`offset.storage` 反射注入 + connector-name registry 桥接保留（3.7 官方文档确认属性注入是唯一路径）；AR-03 首跑清理生命周期按实例名适用。
- **connector-design.md §8.4**：`debezium-cdc` 行修订——并行度 `PARALLEL`；参数规格新增 §3.7 字段；注册发现测试断言同步。
- **00-vision.md**：不变量 #4 由 §3.6 钉定保护；不变量 #7 不受影响——`REPLAYABLE` + 2PC 组合维持 `STRICT_EXACTLY_ONCE` 资格；Non-Goal 新增显式条目「分布式 CDC 快照分片」。
- **checkpoint-design.md**：无协议变更——B 层复用 per-subtask operator state；A 层并发在连接器内部，对 checkpoint 协议透明。
- **observability-design.md**：新增 cdc 指标族按其分层命名规范挂载。
- **self-contained-design.md**：本设计为其例外适用点之一，裁定见 §八。

## 六、取代 SeaTunnel：范围与矩阵

**定位裁定（用户）**：nop 内置 = **围绕数据库系统的数据同步与流加工闭环**；连接器生态、分布式集群、湖表格式、UI 留给外围扩展（SPI + Delta 机制在案）。

| SeaTunnel 场景 | 裁定 | nop 路径 |
|---|---|---|
| DB→DB CDC 同步（mysql/pg 源 → JDBC 目标，exactly-once） | **内置（IN）** | 本设计 D1–D6 |
| 批量初始同步 / 定时全量搬运 | **IN** | nop-batch JDBC Loader/Consumer（已有）+ nop-job 调度 |
| DB → 文件 / 消息导出 | **IN** | file 2PC sink / message sink（已有） |
| 流式窗口 / CEP 加工 | **IN（超集）** | nop-stream 原生能力，SeaTunnel 无此面 |
| 80+ 连接器生态 | **外围（OUT）** | SPI 注册 + Delta 定制（connector-design §8.7 defer/exclude 在案） |
| 分布式集群 / K8s / HPA | **OUT** | 用户裁定单机 |
| Schema evolution DDL 协调 + sink 执行 DDL | **DEFER** | 首个真实需求触发；现以透传+告警+重同步手册兜底 |
| 作业 REST 提交 / Web UI | **部分 IN / OUT** | ops HTTP + REST 提交已有；Web UI 不做 |

**替代验收口径**：两个 reference scenario 作为 e2e 验收——
1. **mysql→mysql CDC 镜像**：并行 initial snapshot → 增量流 → 任意点 kill → checkpoint 恢复续传 → 目标库与源库最终一致、无重复提交；
2. **pg→mysql 初始化 + 增量**：含 DELETE 传播（UPSERT_DELETE）与 JdbcSchemaHistory 落库验证。

## 七、落地顺序

roadmap 见 `ai-dev/backlog/nop-stream-cdc-roadmap.md`（mission-driver 兼容，WI1–WI8 + M1–M3）。依赖序：升级 → 并行（A 层 + B 层）→ CDC sink → 存储与观测 → 验收。每个 WI 独立 plan、独立可交付、可审计。

## 八、自完备约束裁定

`self-contained-design.md` 要求选型类设计显式回答「为什么不引入外部大型件」：

- **Debezium = DB 日志协议驱动**。提供 binlog/WAL 读取与解码的协议实现，地位类比 JDBC 驱动（平台从不因引入 JDBC 驱动违反自完备——驱动是「到达数据的协议桥」，不是能力链引擎）。CDC 能力链的引擎、模型、checkpoint 协议、DSL、并行调度、2PC sink 全部为 nop 自有实现。
- **架构支柱测试**：移除 Debezium → CDC 连接器这一个端点消失，流引擎、checkpoint、窗口/CEP、其余连接器全部不受影响；CDC 通道可经外围连接器（消息队列 CDC、批量轮询）替代到达。判定：非架构支柱，**通过**。
- **收敛性**：Debezium 类型不向业务面扩散——流引擎只消费 nop 自有 `ChangeEvent` 信封；外部属性仅以字符串 map 透传。
- **例外裁定**：用户于 2026-10-02 明示裁定升级并深化 Debezium 集成，且此前实现未发布、无兼容包袱。
- **接受的代价**：CDC 协议覆盖 = Debezium 支持矩阵 ∩ 白名单（mysql/pg）；Debezium 版本演化绑定（大版本升级时重新评估引擎行为，集成测试为护栏）；AsyncEmbeddedEngine 内部行为变更风险（以 §3.6 钉定 + 集成测试对冲）。
