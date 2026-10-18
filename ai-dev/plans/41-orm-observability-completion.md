# 41 nop-orm 可观测性补全（N+1 / 慢查询 / SQL 审计 / OTel）

> Plan Status: draft
> Last Reviewed: 2026-10-18
> Source: `ai-dev/analysis/2026-10/2026-10-18-nop-orm-vs-oss-orms-design-and-extension-analysis.md` §1.5 / §4.2 R3；用户要求补全可观测性缺口
> Related: `docs-for-ai/02-core-guides/observability-defaults.md`、`docs-for-ai/02-core-guides/debugging-and-diagnostics.md`

## Purpose

把 ORM/DAO 可观测性从「已有基础指标 + SQL 日志」补全为「能定位 N+1、慢查询、SQL 审计、分布式 trace」的闭环。复用 `GlobalStatManager`、`IOrmMetrics`、`IDaoMetrics`、`GlobalMeterRegistry`，不新建第二套统计；OTel 遵守平台既有的 Q4 默认（http/protobuf 4318、10% sampling）。

本计划为 draft：进入 active 前必须按 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 走独立子 agent 对抗性审查（含想象性分析）。

## Current Baseline

- `GlobalStatManager`（`nop-kernel/nop-core/src/main/java/io/nop/core/stat/GlobalStatManager.java`）是 JVM 级单例：按 SQL 文本聚合 `JdbcSqlStat`，提供执行次数/成功/失败、平均/最大耗时、并发、batch、fetch/update 行数、直方图、`lastSlowParameters` 字段；`getAllJdbcSqlStat(orderByAvgTime)` / `clear()`；SQL 缓存容量由 `nop.core.stat.sql-cache-max-size`（默认 1000）控制。
- `DaoMetricsImpl`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/metrics/DaoMetricsImpl.java`）同时写 Micrometer（`GlobalMeterRegistry`）与 `GlobalStatManager`；`IDaoMetrics` 覆盖连接获取、查询/更新/批处理计时与行数；`ITransactionMetrics` 覆盖事务 open/success/failure。
- `IOrmMetrics`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/metrics/IOrmMetrics.java`）提供 session open/close/flush 与实体 load/save/update/delete 计数；`OrmSessionFactoryBean` 装配 `OrmMetricsImpl` / `EmptyOrmMetricsImpl`。
- SQL 日志：`JdbcHelper.SQL_LOG`（logger `io.nop.dao.sql`）输出 `nop.jdbc.run:usedTime=...` 等；`debugging-and-diagnostics.md` 有使用说明。
- `docs-for-ai/02-core-guides/observability-defaults.md`：Micrometer v1 已就位；全仓无 `opentelemetry` 依赖；Q4 默认 OTLP `http/protobuf` 4318 + 10% sampling 已裁定。
- **真实缺口**：①无 N+1 检测（无 per-session/per-request 的重复 SQL/懒加载统计）；②慢查询捕获未接线（`JdbcSqlStat.lastSlowParameters` 无生产 setter）；③无 SQL 审计（只有日志与 metrics，无审计记录/SPI）；④无 ORM/JDBC OTel span。
- 可复用的接缝：`IDaoMetrics.begin/endQuery`、`begin/endExecuteUpdate`、`begin/endBatchUpdate`；`IOrmMetrics` 的 session/实体计数器；`IOrmSession.getBatchLoadQueue()` / `OrmBatchLoadQueueImpl.flush()`；`IOrmDaoListener`。
- 其他既有统计面（Phase 1 审计，不默认纳入 ORM 指标）：`IWithStats<T>`（`nop-commons` 通用 stats 接口）、`CacheStats`、`ThreadPoolStats`、`PoolStats`、`TaskQueueStats`（`nop-core`）、task-flow 的 `resetGlobalStats`（限流器/信号量统计重置，`nop-task`）。这些是「已有统计面」，不得另建同类 registry。

## Goals

- 定义并落地 ORM/DAO 可观测的统一指标契约：指标名、标签、基数上限、与 `GlobalStatManager`/Micrometer 的关系。
- 提供 N+1 信号：per-session/per-request 的重复 SQL、懒加载/批量装载行为、超阈值告警（日志 + 指标），不自动改写 SQL。
- 提供慢查询捕获：阈值配置、SQL 文本（脱敏）、参数摘要、耗时、querySpace、实体、调用点；写入日志/指标/有界环形缓冲。
- 提供 SQL 审计：可插拔 sink（默认 no-op）、记录谁/何时/租户/querySpace/SQL 摘要/行数/耗时/结果；异步有界队列，失败不静默。
- 提供 OTel tracing：opt-in，覆盖 JDBC query/update/batch 与 ORM session flush/load；属性脱敏、低基数；无 OTel 依赖时 No-Op。
- 文档与验证同步：`observability-defaults.md`、`debugging-and-diagnostics.md`、`source-anchors.md` 更新；每个 Phase 有 focused tests + 端到端验证。

## Non-Goals

- 不做 SQL 优化器/CBO/join 重排/索引选择（前作已裁定不属于 ORM 职责）。
- 不做 SQL 重写、租户/权限注入、无 where 写阻断（属 R2 native SQL 安全计划，不在本计划）。
- 不新建指标 registry，不复制 `GlobalStatManager` 的 SQL 统计；不把 cache/pool/task 统计并入 ORM 指标（各自 owner 保留）。
- 不做审计 UI、告警平台、APM 厂商集成；不做审计数据长期归档/合规保留策略。
- 不对每条内部方法打 span；不引入高基数标签（sessionId/实体 id/SQL 全文不进标签）。
- 不改变任何 SQL 语义、事务边界、查询结果。

## Scope

### In Scope

- `nop-kernel/nop-core` 的 `GlobalStatManager`/`JdbcSqlStat` 复用与必要扩展。
- `nop-persistence/nop-dao` 的 metrics/JdbcTemplate 接缝、慢查询捕获、审计 SPI。
- `nop-persistence/nop-orm` 的 session/batch-load 指标、N+1 信号、ORM span。
- `docs-for-ai/` 三个 owner doc + 测试。

### Out Of Scope

- `nop-metadata`、`nop-stream`、`nop-job` 等消费方的专属看板。
- 外部 APM/告警系统接入。

## Execution Plan

### Phase 1 - 基线核定与契约设计

Status: planned
Targets: 新建 ORM 可观测性设计文档（`ai-dev/design/` 下）、`docs-for-ai/02-core-guides/observability-defaults.md`、`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Decision | Proof`

- [ ] 核对 live baseline：`GlobalStatManager`/`JdbcSqlStat`/`DaoMetricsImpl`/`IOrmMetrics`/`IDaoMetrics`/`ITransactionMetrics` 的实际字段、调用点、装配路径，产出带 `path:line` 的底账。
- [ ] 盘点其他既有统计面（`IWithStats`/`CacheStats`/`ThreadPoolStats`/`PoolStats`/`TaskQueueStats`/task-flow `resetGlobalStats`），逐项裁定「复用 / 不纳入 ORM 指标 / 后续接入」，避免新建第二套统计。
- [ ] 定义指标契约：指标名、类型（Counter/Timer/Gauge）、标签（entity/querySpace/statementKind/result 等低基数字段）、基数上限、与 `GlobalStatManager` 的对应关系；禁止把 SQL 全文/参数值作为标签。
- [ ] 定义 N+1 判定契约：什么算 N+1（同一 session 内同一 SQL 重复超过阈值、集合加载后逐条懒加载、批量装载队列未命中/退化），误报边界与关闭开关。
- [ ] 定义慢查询契约：阈值配置键、采样/捕获上限、参数脱敏规则、环形缓冲容量、输出目的地（日志/指标/可选回调）。
- [ ] 定义 SQL 审计契约：记录字段、sink SPI 语义（默认 no-op）、异步队列容量/丢弃策略、失败是否显式、敏感字段处理。
- [ ] 定义 OTel span 契约：span 名、父子关系、属性白名单、sampling 配置、无依赖时 No-Op 行为。
- [ ] 设计文档通过独立 review（至少一轮想象性分析）。

Exit Criteria:

- [ ] 设计文档写入 `ai-dev/design/`（按 00-design-writing-guide 命名），含指标/慢查询/审计/OTel 四类契约与拒绝项。
- [ ] 当前 baseline 每条断言有 `path:line` 证据；未确认项标 `UNVERIFIED` 并给出验证方式。
- [ ] 指标/配置命名与 `observability-defaults.md` 不冲突；不新增第二套 SQL 统计。
- [ ] N+1 判定有可执行定义（含误报边界），可直接转测试断言。
- [ ] 文档更新：`observability-defaults.md` §ORM/DAO 观测现状与缺口、`source-anchors.md` 增补锚点。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - N+1 信号与 ORM/DAO 指标补全

Status: planned
Targets: `nop-persistence/nop-orm` 的 session/batch-load/metrics；`nop-persistence/nop-dao` 的 metrics；测试

- Item Types: `Feature | Proof`

- [ ] 在 session 生命周期记录 per-session 统计：打开/关闭、flush 次数、实体加载数、按 entity 的 load 次数。
- [ ] 记录批量装载队列行为：flush 次数、每次批大小、按实体/关联的装载数；区分 batch load 与逐条 get/load。
- [ ] 实现 N+1 信号：同一 session/请求内同一 SQL 模板重复超过阈值，或集合加载后出现逐条懒加载时，产生 WARN/指标；提供关闭开关。
- [ ] 指标接入 `GlobalStatManager`/Micrometer：命名、标签、基数符合 Phase 1 契约；不重复统计 `IDaoMetrics` 已有指标。
- [ ] 测试：N+1 正例（循环内逐条 load）、反例（一次 batch load）、关闭开关、基数上限。

Exit Criteria:

- [ ] 新增指标在 live 调用路径上被真实写入（接线验证：断言 counter/timer 被调用）。
- [ ] N+1 正例触发、反例不触发；阈值与开关可配。
- [ ] 批量装载不被误报为 N+1；per-session 统计不跨 session 泄漏。
- [ ] 指标基数有上限测试（实体名/SQL 模板数量受控）。
- [ ] **端到端验证**：从一次 DAO/GraphQL 查询入口到指标可见，完整路径跑通。
- [ ] **无静默跳过**：未配置/未启用时行为明确（默认不采集或 no-op，不抛异常也不静默丢错误）。
- [ ] owner doc 更新（指标清单/开关）；`ai-dev/logs/` 已更新。

### Phase 3 - 慢查询捕获

Status: planned
Targets: `nop-persistence/nop-dao` 的 JdbcTemplate/metrics；`nop-kernel/nop-core` 的 `JdbcSqlStat`；测试

- Item Types: `Feature | Fix`

- [ ] 增加慢查询阈值配置（沿用 `nop.dao.*` 命名；默认关闭或保守阈值，避免生产噪声）。
- [ ] 在 `IDaoMetrics.endQuery/endExecuteUpdate/endBatchUpdate` 接缝捕获超阈值语句：SQL 文本（按既有脱敏规则）、参数摘要、耗时、querySpace、name/实体、行数。
- [ ] 输出：独立 logger（可单控）、慢查询指标、可选有界环形缓冲/回调；不改变原有 SQL 日志行为。
- [ ] 接线 `JdbcSqlStat.lastSlowParameters`（当前无生产 setter）或明确不复用该字段的裁定；避免死字段。
- [ ] 测试：慢查询触发、快查询不触发、参数脱敏、阈值边界、环形缓冲上限、并发安全。

Exit Criteria:

- [ ] 慢查询记录恰好一次（不重复/不遗漏）；快查询零记录。
- [ ] 敏感参数不落日志/指标；SQL 文本脱敏规则有测试。
- [ ] 环形缓冲/队列有界，超限行为显式（丢弃计数或阻塞策略，不能静默）。
- [ ] **端到端验证**：真实 H2 查询超过阈值后，日志/指标/缓冲三处可观察到。
- [ ] 默认关闭或保守阈值；开启方式有 owner doc 说明。
- [ ] owner doc 更新；`ai-dev/logs/` 已更新。

### Phase 4 - SQL 审计

Status: planned
Targets: `nop-persistence/nop-dao` 审计 SPI/实现；`nop-persistence/nop-orm` 事件补充；测试

- Item Types: `Feature | Decision`

- [ ] 定义并实现审计 sink SPI（默认 no-op）、审计记录模型、异步有界队列；记录字段遵循 Phase 1 契约。
- [ ] 接入查询/更新/批处理与 ORM 写路径：尽可能复用 `IDaoMetrics`/`IOrmDaoListener`，不引入 SQL 字符串二次解析。
- [ ] 记录谁/何时/租户/querySpace/SQL 摘要/行数/耗时/结果；不记录明文敏感参数。
- [ ] 队列满/ sink 异常的行为显式（计数/日志/抛错策略），不允许静默吞掉。
- [ ] 测试：读/写审计各一条、默认 no-op、sink 失败、队列满、敏感字段。

Exit Criteria:

- [ ] 审计默认 no-op 时零行为变更；启用后每条匹配语句恰好一条记录。
- [ ] sink 失败/队列满有显式信号（计数或错误），不是静默丢弃。
- [ ] **端到端验证**：从业务 DAO 调用到审计 sink 收到记录完整跑通。
- [ ] owner doc/SQL 审计使用说明；`ai-dev/logs/` 已更新。

### Phase 5 - OpenTelemetry tracing

Status: planned
Targets: `nop-persistence/nop-dao`、`nop-persistence/nop-orm`、依赖/BOM、测试

- Item Types: `Feature | Decision`

- [ ] 引入 opt-in OTel/Micrometer Tracing 依赖与 No-Op 默认；遵守 `observability-defaults.md` 的 Q4 默认（http/protobuf 4318、10% sampling）。
- [ ] 为 JDBC query/update/batch 与 ORM session flush/load 建立 span；父子关系、属性白名单（db.system/db.statement 摘要/nop.querySpace/nop.entity）。
- [ ] 上下文传播：同步 + 既有异步 `CompletionStage` 路径；不破坏无 OTel 部署。
- [ ] 测试：in-memory exporter 断言 span/属性；禁用时零 span/零依赖；高基数属性不出现。

Exit Criteria:

- [ ] 启用后一次 DAO 查询产生完整 span 链；禁用时无 OTel 类加载/无开销路径。
- [ ] SQL 文本/参数按脱敏规则处理，无高基数标签。
- [ ] **端到端验证**：从入口调用到 exporter 收到 span。
- [ ] `observability-defaults.md` OTel 段落更新（若引入依赖/配置变化）。
- [ ] `ai-dev/logs/` 已更新。

### Phase 6 - 文档收口与验证

Status: planned
Targets: `docs-for-ai/`、`ai-dev/logs/`、测试

- Item Types: `Proof | Follow-up`

- [ ] 更新 `observability-defaults.md`、`debugging-and-diagnostics.md`、`source-anchors.md`，写明指标/慢查询/审计/OTel 的启用方式与边界。
- [ ] 运行受影响模块测试（`nop-core`、`nop-dao`、`nop-orm`）与 `scan-hollow-implementations`。
- [ ] 由独立子 agent 做 closure audit（含 anti-hollow：真实调用链、无 no-op 冒充实现）。

Exit Criteria:

- [ ] 三份 owner doc 与 live baseline 一致；新增指标/配置有文档。
- [ ] 受影响模块测试全绿；`scan-hollow-implementations --severity high` 退出 0。
- [ ] 独立 closure audit 证据已写入本计划 `Closure` 段。
- [ ] `ai-dev/logs/` 收口记录已更新。

## Closure Gates

- [ ] 所有 in-scope confirmed live defect / contract drift 已收敛。
- [ ] N+1、慢查询、SQL 审计、OTel 四类行为均有 focused tests + 端到端验证。
- [ ] 未引入第二套 SQL 统计；`GlobalStatManager`/`IOrmMetrics`/`IDaoMetrics` 复用关系有文档。
- [ ] 无高基数标签、无敏感参数落盘、无静默丢弃。
- [ ] 受影响 owner docs 已同步到 live baseline。
- [ ] 独立子 agent closure audit 已完成并记录证据。
- [ ] `./mvnw test -pl nop-kernel/nop-core,nop-persistence/nop-dao,nop-persistence/nop-orm -am` 通过。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm --severity high` 退出 0。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/41-orm-observability-completion.md --strict` 退出 0（关闭时）。

## Deferred But Adjudicated

### N+1 自动修复

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划只要求检测/告警；自动改写查询会改变结果语义与性能特征，需独立设计。
- Successor Required: `yes`
- Successor Path: 待触发时另立 plan。

### 审计数据保留/轮转/合规

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划只提供 sink SPI 与记录模型，保留策略由部署方/审计系统决定。
- Successor Required: `no`
- Successor Path: 无。

### OTel 指标 exemplars / APM 厂商集成

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 非 ORM 层职责，按部署环境选型。
- Successor Required: `no`
- Successor Path: 无。

## Non-Blocking Follow-ups

- 各消费方（nop-metadata/nop-stream/nop-batch）的看板与告警规则。
- 慢查询 SQL 指纹归一化（当前只做文本摘要）可作为后续优化。

## Closure

Status Note: 未关闭（draft）。
Completed: 

Closure Audit Evidence:

- Reviewer / Agent: 待填
- Evidence: 待填

Follow-up:

- 待填
