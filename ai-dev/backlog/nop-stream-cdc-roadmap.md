# nop-stream CDC Roadmap——Debezium 升级、单机并行与 DB 内置同步

> Last updated: 2026-10-02（v1 初版。设计决策全部由用户裁定，见 ai-dev/design/nop-stream/cdc-design.md v2：Debezium 升级 3.7.0.Final、单机两层并行、mysql+postgres 白名单、offset 真相源钉定、JdbcSchemaHistory 默认、jdbc-2pc dmlMode、取代 SeaTunnel 范围矩阵。关键前提：此前 CDC 实现从未发布，无兼容性包袱，契约以设计文档为准）
> 位置：按仓库 roadmap 惯例存放于 ai-dev/backlog/。书写约定：未来交付物路径用普通文本书写、不加反引号；已存在的文档路径用反引号，持续受 check-doc-links 保护。
> mission-driver 兼容：本块采用 `## Work Item Status` 与 `- WI<n> 名称: \`status\`` 形态，状态集为 `todo` / `ready` / `planned` / `done`。状态行尾部括注必须单层非嵌套——只允许一组全角括号且其内不得再出现括号或半角右括号。改任何状态行后必须重跑解析核对条目数。里程碑行写作 `★ **里程碑：M<n> …**（解锁条件 …）：\`status\``。
> mission 状态：进行中。missions/nop-stream-cdc.json 已建，plansDir 为 ai-dev/plans/nop-stream-cdc，auditsDir 为 ai-dev/audits/nop-stream-cdc。
> 数据口径：设计事实来自 Debezium 官方发布信息与 nop 源码实测锚点，汇总于设计文档；外部真实数据库行为以 WI2/WI8 的 gated Testcontainers 测试为准。

> Sources:
> - `ai-dev/design/nop-stream/cdc-design.md`（本 roadmap 的全部设计裁定，D1–D8）
> - `ai-dev/design/nop-stream/connector-design.md`（§5.4 CDC offset 集成基线、§8 SPI 注册与能力矩阵）
> - `docs-for-ai/03-modules/nop-stream-cdc-cookbook.md`（用户侧 CDC 使用文档，WI8 同步对象）
> - Debezium 官方发布信息（3.7 Final / 3.5 并行快照 / engine 文档 / storage 配置文档）

## Purpose

把「用 nop-stream + nop-debezium 在单机多线程范围内取代 SeaTunnel 的 DB→DB 数据同步职能」落成可执行、可验收的工作项。核心命题不是补功能清单，而是三个结构性收敛：

1. **引擎换代**：Debezium 2.4.0.Final → 3.7.0.Final，拿到并行快照、JDBC 存储、AsyncEmbeddedEngine 三个上游能力，且连接器白名单收敛到 mysql/postgres。
2. **并行入模**：两层单机多线程并行——A 层引擎内 chunk 并行快照（配置透传）、B 层 nop-stream subtask 级确定性表路由（新 core 契约 + 描述符 PARALLEL）。
3. **落库闭环**：jdbc-2pc 获得 DML 语义（UPSERT/UPSERT_DELETE），使 CDC 镜像与快照重放结构性安全，配合既有 epoch 2PC + ledger 完成 exactly-once 闭环。

非目标（设计文档 §四 已裁定）：分布式 chunk 分发、XA sink、schema evolution DDL 协调、Flink CDC / SeaTunnel 引入。

## Work Item Status

- WI1 引擎升级 3.7.0.Final: `done`
- WI2 真实数据库集成测试基建: `todo`
- WI3 A 层并行快照透传与配置契约: `done`
- WI4 B 层 subtask 表路由与描述符 PARALLEL: `done`
- WI5 jdbc-2pc dmlMode CDC 落库: `done`
- WI6 schema history 换轨 JdbcSchemaHistory: `done`
- WI7 引擎单写者钉定与配置守卫: `done`
- WI8 reference 验收场景与文档同步: `todo`

★ **里程碑：M1 引擎换代完成**（WI1 通过全部既有与新增单元测试）：`done`
★ **里程碑：M2 单机并行可用**（WI3 + WI4 落地，debezium-cdc 声明 PARALLEL）：`done`
★ **里程碑：M3 CDC 镜像闭环**（WI5 + WI6 + WI8 验收通过）：`todo`

## Phase 1——引擎换代（门：M1）

### WI1 引擎升级 3.7.0.Final

- nop-message-debezium 的 debezium.version 属性升至 3.7.0.Final；sqlserver 连接器依赖删除；mysql/postgres 连接器保持 provided+optional。
- DebeziumEngineConfig 兼容 AsyncEmbeddedEngine：offset.storage 属性注入链（NopStreamOffsetBackingStore 反射实例化 + connector-name registry）在 3.7 下验证不变；engine 回调（CompletionCallback / ConnectorCallback）与 failure listener 语义不变。
- connectorType 白名单收敛 mysql/postgres：getConnectorClass 与 per-connector 属性段只留两支，未知名抛 ERR_DEBEZIUM_UNSUPPORTED_CONNECTOR_TYPE 且错误参数含支持清单。
- 属性面清理：`database.server.name` 在 Debezium 2.x 已由 topic.prefix 取代，按 3.7 连接器实际契约生成属性并验证；未知属性在 3.7 下为 fail 行为，逐属性核对。
- 验证：nop-message-debezium 与 nop-stream-connector-debezium 全部测试通过；快照/恢复 mock 语义测试随新契约修订。

### WI2 真实数据库集成测试基建

- 引入 Testcontainers 的 mysql 与 postgres 集成测试：initial snapshot → 增量流 → cancel → checkpoint restore → 续传无重不丢闭环；pg 侧覆盖 pgoutput 插件与 UPSERT_DELETE 路径。
- docker 不可达环境以 assume 跳过并显式标注，不伪造通过；gated 运行口径写入测试类注释。
- 验证：docker 可用时全绿；不可用时明确 SKIP 且统计可见。

## Phase 2——单机并行（门：M2）

### WI3 A 层并行快照透传与配置契约

- DebeziumConfig 新增 snapshotMaxThreads（默认 1），映射 snapshot.max.threads；multiplier 经 extraProperties 透传并在文档标注边界（无主键表与 select override 表回退表级并行；同表行序 chunk 间无全局保证，快照期写入必须 UPSERT）。
- 验证：属性映射单元测试；A 层开启时 mock 引擎收到正确属性。

### WI4 B 层 subtask 表路由与描述符 PARALLEL

- 新增 nop-stream-core 契约 SubtaskShardedSourceFunction（extends SourceFunction + ParallelismCheckable，含 copyForSubtask(int, int)）。
- StreamSourceOperator.copyForSubtask(int) 覆写：函数实现该接口时创建携带身份的函数副本，未实现维持共享语义零回归。
- DebeziumCdcSourceFunction 实现路由：validateParallelism 校验 tableIncludeList 非空、单表加并行大于 1 fail-fast；route 为表名 hashCode 的确定性无符号模；实例名 = name-subtaskIndex；每实例有效配置替换 name 与 table.include.list；原始 config 不变、凭证引用驻留语义不变；operator state 本就 per-subtask 故恢复零新协议。
- 能力描述符 debezium-cdc 行 SINGLE_INSTANCE 改 PARALLEL，注册发现测试同步。
- 验证：路由确定性、实例名隔离、fail-fast 契约、双 subtask 恢复各自续传的单元测试。

## Phase 3——CDC 落库（门：M3 组成部分）

### WI5 jdbc-2pc dmlMode

- JdbcTwoPhaseCommitSink 新增 dmlMode：INSERT（默认，现状语义）/ UPSERT / UPSERT_DELETE；UPSERT/UPSERT_DELETE 必需 keyColumns。
- 输入契约：原生 ChangeEvent 直连（operation c/u/d/r + key + after）或 recordMapper + 操作码双函数；INSERT 模式收到非插入语义的 ChangeEvent 事件 fail-fast。
- 方言 upsert 生成器内建于 connector-jdbc（MySQL INSERT ... ON DUPLICATE KEY UPDATE、PG INSERT ... ON CONFLICT (key) DO UPDATE SET = EXCLUDED，参照 JdbcCheckpointStorage UpsertDialect 先例），delete 为参数化 DELETE；批内同 key 保序折叠 last-write-wins；epoch 2PC 与 ledger 幂等全复用。
- 验证：dmlMode 三态、方言 SQL 断言、批内折叠、d 事件删除、ledger 复用回归的单元测试。

## Phase 4——存储与守卫

### WI6 schema history 换轨 JdbcSchemaHistory

- 引入 debezium-storage-jdbc 依赖；DebeziumConfig.schemaHistoryStore 默认 jdbc，JDBC 属性族从配置生成并支持 credential 引用（引擎侧瞬态解密）；file 模式走既有 schemaHistoryPath；两者皆未配置 = 配置错误，静默内存默认删除。
- 验证：两种模式的属性生成单元测试；凭证引用解析测试。

### WI7 引擎单写者钉定与配置守卫

- DebeziumEngineConfig 钉定 record.processing.threads=1；extraProperties 携带 offset.storage 或调高该线程数 = 类型化错误（保护 SourceContext 单写者不变量）。
- 快照进度与事件速率指标挂 cdc 指标族（observability-design 分层命名）。
- 验证：守卫类型化错误单元测试；指标注册测试。

## Phase 5——验收与收口

### WI8 reference 验收场景与文档同步

- 场景 1：mysql→mysql CDC 镜像——并行快照、增量、任意点 kill、checkpoint 恢复续传，目标与源最终一致且无重复提交（gated Testcontainers）。
- 场景 2：pg→mysql 初始化加增量——DELETE 传播（UPSERT_DELETE）与 JdbcSchemaHistory 落库验证（gated）。
- 文档同步：docs-for-ai 的 nop-stream-cdc-cookbook.md 与 nop-stream-connectors.md 能力矩阵随实际落地更新；connector-design.md §5.4/§8.4 修订在案。
- 验证：两场景 gated 测试 + check-doc-links 严格通过。

## 裁定记录摘要

全部设计裁定（D1–D8）与拒绝方案留档于 `ai-dev/design/nop-stream/cdc-design.md`，本 roadmap 不复制决策正文。补充两条执行级口径：

- E1 测试基建 gated 语义：docker 不可达 = 显式 SKIP 并可见，不伪造通过；凡依赖真实数据库行为的完成判定以 gated 运行为准。
- E2 描述符单一事实源：debezium-cdc 的 PARALLEL 声明落地前，注册发现测试与 docs-for-ai 能力矩阵同步修改，禁止文档先行或代码先行单边漂移。
