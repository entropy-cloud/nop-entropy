# CDC 子系统设计——Debezium 升级、单机并行与 DB 内置同步

**日期**：2026-10-02
**范围**：`nop-message/nop-message-debezium`、`nop-stream/nop-stream-connector-debezium`、`nop-stream/nop-stream-connector-jdbc`、`nop-stream-core` 连接器注册契约（`connector-design.md` §5.4/§8 修订）
**状态**：active（核心决策已于 2026-10-02 由用户裁定；实施未启动，逐项落 `ai-dev/plans/` 后执行）
**裁定来源**：用户 2026-10-02 设计裁定——升级 Debezium 至最新；并行处理采用**单机多线程**（不做分布式）；外部连接器只考虑 **MySQL + PostgreSQL**；nop 整体定位为**围绕数据库系统提供内置解决方案，其余留给外围扩展**；并在此范围内取代 SeaTunnel 的数据同步职能。

---

## 一、设计结论

1. **升级**：Debezium `2.4.0.Final` → `3.7.x.Final`（当前 latest，2026-09-29 发布），版本属性单点管理；`AsyncEmbeddedEngine` 成为引擎实现（3.2 起 `EmbeddedEngine` 已移除）。
2. **连接器范围**：一等支持清单 = **MySQL、PostgreSQL**（版本兼容矩阵、集成测试、文档三件承诺）；其余数据库不进入内置范围，经连接器 SPI + Delta 机制留给外围扩展。
3. **并行 = 两层单机多线程**：A 层透传 Debezium 3.5+ 的 `snapshot.max.threads`（引擎内 chunk 级并行 initial snapshot）；B 层在 nop-stream 侧做 **subtask 级确定性表路由**（表名 hash → subtask，每实例独立 offset 与 connector name）。不做分布式 chunk 分发。
4. **offset 真相源不变**：CDC 位点仍由 `NopStreamOffsetBackingStore` 进 nop checkpoint operator state；`offset.storage` 属性注入 + connector-name registry 桥接**保留**（3.7 的 `DebeziumEngine.Builder` 仍不暴露 `using(OffsetBackingStore)`）。
5. **schema history 落库**：引入 `debezium-storage-jdbc`，`JdbcSchemaHistory` 成为多节点场景默认推荐（文件保留为零依赖回退）；**offset 存储不切换**到 `JdbcOffsetBackingStore`（nop checkpoint 是 offset 真相源，见 §3.6）。
6. **升级语义**：不承诺跨 Debezium 大版本的位点恢复（3.0 起 offset 格式有破坏性变更）；升级 = 重新 initial snapshot，安全性由目标端幂等 upsert 保证。
7. **CDC 目标端**：`jdbc-2pc` sink 增加 DML 语义（UPSERT / DELETE），按 nop 自有 `ChangeEvent.operation`（c/u/d/r）驱动，MySQL/PostgreSQL 方言生成对应 upsert 语句；epoch 2PC + ledger 幂等机制不变（不引入 XA）。
8. **取代 SeaTunnel 的范围**：DB→DB CDC 同步、批量初始同步、DB→文件/消息导出、流式窗口/CEP 加工为内置目标；多连接器生态、分布式集群、schema evolution DDL 协调、UI 为外围/延迟项（矩阵见 §六）。

## 二、背景与动机

- 现状痛点（代码核实）：CDC initial snapshot 串行（大表初始化慢）；schema history 仅文件路径（多节点/容器不可靠）；`jdbc-2pc` 只会生成 `INSERT INTO`（无法正确应用 UPDATE/DELETE 变更）；Debezium 2.4.0 老旧（2 年前版本，无并行快照、无 JDBC 存储、无 AsyncEmbeddedEngine）。
- 外部能力基线（Debezium 3.x 官方发布信息）：3.5.0.Final GA 单表 chunk 级多线程并行快照（`snapshot.max.threads`，默认 1 关闭；2.3 起旧机制为多表并行，3.5 起被 chunk 方案取代）；3.2 起默认引擎为 `AsyncEmbeddedEngine`（`record.processing.threads` 支持事件处理并发，`record.processing.order=ORDERED` 保持有序）；Java 17 baseline（平台编译目标 release 17，兼容）；`debezium-storage-jdbc` 稳定提供 `JdbcSchemaHistory`（`schema.history.internal=io.debezium.storage.jdbc.history.JdbcSchemaHistory` + `schema.history.internal.jdbc.*` 属性族）；MySQL/PostgreSQL 连接器为单 task 连接器（流式阶段本质单线程，任何框架相同）。
- 定位驱动：nop 围绕数据库系统提供内置解决方案——CDC 与数据同步是数据库场景的高频内置需求； SeaTunnel 在该场景的核心手段（嵌入式 CDC、并行快照、exactly-once 落库）在本设计范围内可用「Debezium 升级 + 单机并行 + CDC sink」三件收敛，无需引入外部集成引擎。

## 三、核心设计

### 3.1 版本升级与兼容面（D1）

| 维度 | 2.4.0 现状 | 3.7 目标 | 行动 |
|---|---|---|---|
| 版本管理 | `nop-message/nop-message-debezium/pom.xml` 内联属性 | 平台 BOM/属性单点（`debezium.version`），`debezium-bom` 导入 | 属性提升，禁止散落版本号 |
| 引擎实现 | `EmbeddedEngine` | `AsyncEmbeddedEngine`（默认） | 验证 offset store 反射注入链、`CompletionCallback`/`ConnectorCallback`、failure listener 在 Async 引擎下语义不变；异常则显式选配兼容实现 |
| 事件消费 | 简单 `Consumer<ChangeEvent>`（`notifying(...)`） | 不变（不迁移 `ChangeConsumer` API） | 迁移 `ChangeConsumer` + `ChangeEventWithMetadata` 维持 connector-design §7-8 的 successor 地位，本设计不携带 |
| offset 格式 | 2.x 格式 | 3.0 起格式变更 | §3.4 升级语义 |
| 存储 | file offset store（自定义桥接）+ file schema history | 桥接保留 + `JdbcSchemaHistory` | §3.6 |
| 连接器 | mysql/postgres/sqlserver 声明 | mysql/postgres 一等；sqlserver 降级透传 | §3.2 |
| Java baseline | Java 11 | Java 17+ | 平台 release 17，无行动 |

**升级验收口径**：既有 42 个 debezium 测试全部通过（mock 语义不变）+ 新增真实 MySQL/PostgreSQL 集成测试（Testcontainers）覆盖 snapshot→stream→cancel→restore→续传闭环——这是本设计对「测试全 mock」缺口的最小补课，随升级 plan 交付。

### 3.2 连接器范围裁剪（D2）

- **一等支持**（承诺兼容矩阵 + 集成测试 + 文档）：`mysql`、`postgres`。
- **降级透传**：`sqlserver` 从一等清单移除——`DebeziumConfig.connectorType` 映射与 `extraProperties` 通道保留（实现成本为零），文档标注「未验证，自行探索」；不承诺行为。
- **内置不扩展**：其余数据库（oracle/db2/mongodb/tidb 等）不做映射、不进能力矩阵。外围扩展机制 = `connector-design.md` §8 SPI 注册（新连接器 = 新代码 + beans.xml 工厂）+ §8.8 Delta 定制（既有连接器装配差异）。

**拒绝的替代方案**：预置 9+ 数据库映射但不做测试——「能编译」不等于「能承诺」，无验证的声明矩阵违反产品化纪律（与 §8.7 OLAP defer 裁定同口径）。

### 3.3 单机并行模型（D3，两层）

**A 层——引擎内 chunk 并行（吞吐杠杆，零新协议）**

`DebeziumConfig` 新增 `snapshotMaxThreads`（默认 1 = 行为不变），映射 Debezium `snapshot.max.threads`；`extraProperties` 继续透传 `snapshot.max.threads.multiplier`（全局/按表）。边界：无主键表与使用 select override 的表回退表级并行；同表行序在 chunk 间无全局顺序保证——因此快照期写入**必须**走幂等 upsert（§3.5），这是 A 层并行的正确性前提。

**B 层——nop-stream subtask 级表路由（并行度杠杆）**

source `parallelism = N` 时，`DebeziumCdcSourceFunction` 按确定性路由把表分片到各 subtask，每实例一个独立 Debezium 引擎：

```
route(table) = subtaskIndex = hash(全限定表名) mod N     // 确定性，新表发现时路由稳定
实例名     = connectorName + "-" + subtaskIndex          // registry/AR-03 生命周期按实例名适用
offsets    = 每 subtask 独立 operator state（机制已存在，零新协议）
```

- 单表 + parallelism > 1 = **配置错误 fail-fast**（单表并行请用 A 层线程），错误信息含路由模式与表数。
- 新增表在运行中被路由到某 subtask：属增量流事件，按事件路由（流式阶段全表事件本就经同一连接器）——B 层路由只约束 **snapshot 分片**与 **offset 归属**。
- 恢复语义不变：`OFFSET_CHECKPOINT` + `REPLAYABLE` 保持；并行度声明 `SINGLE_INSTANCE` → **`PARALLEL`**（能力描述符单一事实源同步，§8.4 矩阵行修订）。

**明确不做**：跨节点分布式 chunk 分发（SeaTunnel/Flink CDC 式 chunk-as-split）——用户裁定单机多线程足够；与 `00-vision.md` Non-Goal「大规模并行（PB 级吞吐）」一致。B 层在单 JVM 内仍受 Amdahl 约束，吞吐上限 = 单机资源，这是接受的能力边界而非缺陷。

### 3.4 升级语义：位点不跨大版本（D4）

Debezium 3.0 起 connector offset 存储格式有破坏性变更。**裁定：不支持 2.4 → 3.7 原位恢复。** 升级 = 重新 initial snapshot；安全性由目标端幂等 upsert（§3.5）保证（重放覆盖写，不产生重复行）。nop checkpoint 协议本身无感知——它持久化的是 Debezium 序列化后的 offset 字节，同版本内 kill/restore 语义不变。

**拒绝的替代方案**：位点迁移工具——一次性消费、格式私有（Kafka Connect 内部序列化信封）、迁移错误 = 静默丢变更，成本与风险均高于重新快照。文档须给出升级操作顺序：停作业 → 升级 → 清 offset（`clearConnector` 生命周期已有）→ 重新提交（initial snapshot）。

### 3.5 CDC 目标端 sink：DML 语义（D5）

`JdbcTwoPhaseCommitSink` 增加 `dmlMode` 契约：

| dmlMode | 行为 | 用途 |
|---|---|---|
| `INSERT`（默认，现状） | 追加插入 | 纯流式落表（现状兼容） |
| `UPSERT` | 按 key 覆盖写 | 快照 + 增量（r/c/u 全部 upsert） |
| `UPSERT_DELETE` | upsert + d 事件按 key 删除 | 完整 CDC 镜像 |

- 驱动信号 = nop 自有 `ChangeEvent.operation`（c/u/d/r，`nop-message-debezium` 信封已有）+ 主键（`ChangeEvent.key` 或配置声明）；dmlMode=INSERT 时若记录携带 operation≠insert 语义则 fail-fast（防止静默错写）。
- 方言落点：经 `IDialect` 生成 upsert——MySQL `INSERT ... ON DUPLICATE KEY UPDATE`、PostgreSQL `INSERT ... ON CONFLICT DO UPDATE`；delete 为参数化 `DELETE ... WHERE pk=?`。批内同 key 多事件**保序折叠**（last-write-wins），跨 epoch 保序由 2PC epoch 序列保证。
- 2PC 与 ledger 幂等机制**完全复用**（每 epoch 一事务、ledger 查重跳过）——upsert 的幂等性使「重放安全」从 sink 声明变为结构性成立，同时是 §3.4 升级语义与 §3.3 A 层并行的安全前提。
- **拒绝 XA**：SeaTunnel JDBC exactly-once 用 XA（需 DB XA 支持 + 悬挂事务恢复复杂度）；nop 的 epoch ledger 模型已在案并被测试钉定（connector-design §5.3.2 D1），无二选一议题。

### 3.6 状态存储：schema history 落库，offset 不换轨（D6）

```
┌─ nop checkpoint（真相源，恢复语义归属）─────────────────┐
│  CDC offsets（operator state "cdc-offsets"）            │
│  ← snapshotState/initializeState 读写，restore 后       │
│    pre-populate 到 NopStreamOffsetBackingStore registry │
└──────────────┬──────────────────────────────────────────┘
               │ 引擎运行态 flush 载体（契约钉定不可替换）
┌──────────────┴──────────────────────────────────────────┐
│  NopStreamOffsetBackingStore（offset.storage 属性注入）  │
└─────────────────────────────────────────────────────────┘
┌─ Debezium storage 侧（引擎内运行态，可插拔）────────────┐
│  schema.history.internal = JdbcSchemaHistory（默认推荐） │
│                           或 FileSchemaHistory（回退）    │
└─────────────────────────────────────────────────────────┘
```

- **offset 不切换 `JdbcOffsetBackingStore`**：nop 的恢复真相源是 checkpoint operator state；若 offset.storage 换轨 JDBC 存储，restore 后 pre-populate 的位点会被引擎从外部表读回的位点覆盖语义污染，两主并存必有一错。契约钉定：`DebeziumEngineConfig.buildProperties` 恒定注入 `NopStreamOffsetBackingStore`，外部配置试图覆盖 = typed error。
- **schema history 换轨 JDBC**：`DebeziumConfig` 新增 `schemaHistoryStore: file|jdbc`（默认 `file` 保持零依赖回退；多节点/容器场景文档推荐 `jdbc`），JDBC 连接参数走 `credential:{id}#{field}` 凭证引用（复用平台凭证机制，明文不驻留配置）。表名/连接属性名对齐 Debezium 官方属性族（`schema.history.internal.jdbc.*`）。

### 3.7 引擎线程模型与单写者不变量（D7）

`AsyncEmbeddedEngine` 默认开启事件处理并发的能力（`record.processing.threads`）。**裁定：钉定 `record.processing.threads=1`（或等价的 ORDERED 单线程投递）作为 nop 集成契约**。理由：`00-vision.md` 不变量 #4「Barrier 只能由 source 读取线程注入」依赖 SourceContext 单写者；并发事件回调会把 `ctx.collect` 的加锁语义从「引擎单线程顺序调用」弱化为「依赖库内部排序保证」，超出平台可控面。A 层并行快照的并发发生在 Debezium 连接器内部（快照线程池），对 nop 仍是单点事件流入口，不受此约束。集成测试以「并发压力下 barrier 顺序」断言钉定。

### 3.8 可观测性与配置契约（D8）

- `DebeziumConfig` 新增字段（全部可 Delta 定制）：`snapshotMaxThreads`、`schemaHistoryStore` + JDBC 凭证引用、`signalDataCollection`（透传 Debezium `signal.data.collection`，为增量快照信号表预留）。
- 指标：快照进度（每表 chunk 完成/总数）、引擎事件速率、offset lag——经既有 metrics registry（observability-design 分层命名），来源为 Debezium 连接器 metrics 桥接；引擎终死 failure listener 已有（R5-CON-03），保持。
- 上游 DDL 风险的运行边界（诚实声明）：本设计**不含** schema evolution DDL 协调/执行（defer，§六）；上游 DDL 发生时的行为 = 事件透传（`includeSchemaChanges`）+ 告警 + 人工重同步手册。这是范围内接受的边界，写入用户文档。

## 四、拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| 跨节点分布式并行快照（SeaTunnel/Flink CDC 式） | 用户裁定单机多线程足够；vision Non-Goal「大规模并行」；分布式 chunk 调度引入 enumerator 级新协议，收益不覆盖成本 |
| 引入 Flink CDC / SeaTunnel 作为依赖 | 外部集成引擎成为能力链支柱，违反自完备设计（平台级约束）；SeaTunnel 另有 Debezium 1.9.8 版本锁死问题 |
| Debezium 内核复用 + 自研补丁（SeaTunnel 路线） | 并行自由 vs 升级自由的交换——3.5 已内置线程级并行快照，内核补丁路线的护城河价值消失，代价（版本锁死）保留；升级容易一侧收益更高 |
| offset 迁移到 `JdbcOffsetBackingStore` | 两主并存（nop checkpoint vs 外部表）语义污染；nop checkpoint 是唯一恢复真相源（§3.6） |
| 位点跨版本迁移工具 | 一次性、格式私有、迁移错误静默丢数据；重新快照 + upsert 幂等更简单安全（§3.4） |
| XA sink | epoch ledger 模型已钉定并被测试覆盖；XA 增加数据库前提与悬挂事务恢复复杂度（§3.5） |
| schema evolution DDL 协调/执行 | defer——用户裁定「其他暂时不考虑」；触发条件 = 首个真实需求进入 roadmap；范围内以事件透传 + 告警 + 重同步手册兜底（§3.8） |
| 事件消费迁移 `ChangeConsumer` API | connector-design §7-8 successor 保持独立；与本升级解耦，避免一个 plan 承载两个迁移 |

## 五、与已有设计的关系

- **connector-design.md §5.4.2 D1**：`offset.storage` 反射注入 + connector-name registry 桥接**保留**——3.7 官方文档确认 offset 存储仍走属性配置，`Builder.using(OffsetBackingStore)` 未兑现，§7-7「版本升级暴露直接注入 API 时简化桥接」的 successor 条件核实为未触发。
- **connector-design.md §8.4 能力矩阵**：`debezium-cdc` 行修订——并行度 `SINGLE_INSTANCE` → `PARALLEL`（B 层路由落地后）；恢复语义 `OFFSET_CHECKPOINT` 不变；参数规格新增 §3.8 字段。注册发现测试断言同步。
- **00-vision.md**：不变量 #4（单写者）由 §3.7 钉定保护；不变量 #7（语义不降级）不受影响——`REPLAYABLE` + 2PC 组合维持 `STRICT_EXACTLY_ONCE` 资格；Non-Goal 清单新增显式条目「分布式 CDC 快照分片」。
- **checkpoint-design.md**：无协议变更——B 层并行复用既有 per-subtask operator state 快照/恢复；A 层并发在连接器内部，对 checkpoint 协议透明。
- **observability-design.md**：新增指标族按其分层命名规范挂载（cdc.* 前缀归属 job 族）。
- **self-contained-design.md**：本设计为其例外适用点之一，裁定见 §八。

## 六、取代 SeaTunnel：范围与矩阵

**定位裁定（用户）**：nop 内置 = **围绕数据库系统的数据同步与流加工闭环**；连接器生态、分布式集群、湖表格式、UI 等留给外围扩展（SPI + Delta 机制已在案，§3.2/connector-design §8.8）。SeaTunnel 的职能在此边界内逐项裁定：

| SeaTunnel 场景 | 裁定 | nop 路径 |
|---|---|---|
| DB→DB CDC 同步（mysql/pg 源 → JDBC 目标，exactly-once） | **内置（IN）** | 本设计 D1–D6：升级 + 并行 + UPSERT_DELETE sink |
| 批量初始同步 / 定时全量搬运 | **IN** | `nop-batch` JDBC Loader/Consumer（已有）+ nop-job 调度；bounded 流水或纯 batch 任务 |
| DB → 文件 / 消息导出 | **IN** | file 2PC sink / message sink（已有） |
| 流式窗口 / CEP 加工 | **IN（超集）** | nop-stream 原生能力，SeaTunnel 无此面 |
| 80+ 连接器生态（kafka/es/湖表/OLAP…） | **外围（OUT）** | SPI 注册 + Delta 定制扩展机制；OLAP/湖表 defer/exclude 裁定已在案（connector-design §8.7） |
| 分布式集群 / K8s / HPA | **OUT** | 用户裁定单机；分布式骨架已有（RPC/选主/fencing）但不做编排面 |
| Schema evolution DDL 协调 + sink 执行 DDL | **DEFER** | 触发条件：首个真实需求；现以事件透传 + 告警 + 重同步手册兜底 |
| 作业 REST 提交 / Web UI | **部分 IN / OUT** | ops HTTP + REST 提交已有（observability-design）；Web UI 不做 |

**替代验收口径**：两个 reference scenario 作为 e2e 验收（随最后一个落地 plan 交付）——
1. **mysql→mysql CDC 镜像**：initial snapshot（并行）→ 增量流 → 任意点 kill → checkpoint 恢复续传 → 断言目标库与源库最终一致、无重复提交；
2. **pg→mysql 初始化 + 增量**：含 DELETE 传播（UPSERT_DELETE）与 JdbcSchemaHistory 落库验证。

## 七、落地顺序（plan 拆分建议）

按依赖序逐 plan 立项（每个独立可交付、可审计）：

1. **升级 plan**：D1 + D2 + D4 + D7（版本单点、AsyncEmbeddedEngine 兼容验证、Testcontainers 集成测试、位点不跨版本语义与文档）。
2. **并行 plan**：D3（A 层透传 + B 层表路由 + 描述符 PARALLEL + fail-fast 契约 + 单写者压力测试）。
3. **CDC sink plan**：D5（dmlMode 三态 + 方言 upsert/delete + 批内折叠 + ledger 复用回归）。
4. **存储与观测 plan**：D6 + D8（JdbcSchemaHistory + 凭证引用 + 快照进度指标 + DebeziumConfig 新字段 conf 校验）。
5. **验收 plan**：§六两个 reference scenario e2e + 用户文档（升级操作顺序 / DDL 风险手册 / 容量边界声明）。

## 八、自完备约束裁定

`self-contained-design.md` 要求选型类设计显式回答「为什么不引入外部大型件」。本设计的定性：

- **Debezium = DB 日志协议驱动**。它提供的是 binlog/WAL 读取与解码的协议实现，地位类比 JDBC 驱动（平台从不因引入 JDBC 驱动而违反自完备——驱动是「到达数据的协议桥」，不是能力链引擎）。CDC 能力链的引擎、模型、checkpoint 协议、DSL、并行调度、2PC sink 全部为 nop 自有实现。
- **架构支柱测试**：移除 Debezium → CDC 连接器这一个端点消失，流引擎、checkpoint、窗口/CEP、其余连接器全部不受影响；CDC 通道可经外围连接器（消息队列 CDC、批量轮询）替代到达。判定：非架构支柱，**通过**。
- **收敛性**：Debezium 类型不向业务面扩散——流引擎只消费 nop 自有 `ChangeEvent` 信封（`nop-message-debezium` 内完成转换）；外部属性仅以字符串 map 透传。
- **例外裁定**：`nop-message-debezium` 模块先于本约束存在，且用户于 2026-10-02 明示裁定「升级 Debezium 至最新并深化集成」——本设计记录该裁定为 §五.3 意义上的例外适用。
- **接受的代价**：CDC 协议覆盖 = Debezium 支持矩阵 ∩ 一等清单（mysql/pg）；Debezium 版本演化绑定（含 offset 格式破坏性变更，已以 §3.4 语义兜底）；AsyncEmbeddedEngine 内部行为变更风险（以 §3.7 钉定 + 集成测试护栏对冲）。
