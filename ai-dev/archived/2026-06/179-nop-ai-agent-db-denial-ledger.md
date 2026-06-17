# 179 nop-ai-agent DBDenialLedger (L3-6 DB Persistence Successor)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L3-6 (DBDenialLedger)

> Last Reviewed: 2026-06-14
> Source: Carry-over from plan 177 (`ai-dev/plans/177-nop-ai-agent-denial-ledger.md`, Deferred "DBDenialLedger（DB 持久化实现 + ORM 模型 + DAO）", Successor Required: yes). Plan 177 delivered the `IDenialLedger` contract surface + `NoOpDenialLedger` default + dispatch-path integration; this plan delivers the DB-backed functional successor that persists per-session denial counts to the database. Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 3 row L3-6 (`IDenialLedger` 接口 + `NoOpDenialLedger` + `DBDenialLedger`); design `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §6.2 (`persistence = DB` — `DBDenialLedger` successor 职责).
> Related: Plan 177 (L3-6 contract + NoOp default + dispatch-path integration — direct predecessor), Plan 171 (DB-backed messenger `DBMessageService` — DB-backed implementation pattern + `app.orm.xml` entity + H2 test pattern this plan follows), Plan 176 (L3-5 `IApprovalGate`), Plan 178 (L3-7 `IPostDenialGuard` — sibling defense-in-depth node that also records denials)

## Purpose

将 `IDenialLedger` 的 DB 持久化功能化实现 `DBDenialLedger` 收口到"已落地"状态，使 per-session 拒绝计数 + 暂停状态在 ledger 实例重建（模拟 session 恢复 / 跨进程）后依然存活——这正是 plan 177 `NoOpDenialLedger` 默认所缺失的核心能力（设计 §6.2 `persistence = DB`）。

Plan 177 交付了契约表面 + dispatch-path 集成 + `NoOpDenialLedger` 默认。dispatch path 的 `handleDenialAndCheckThreshold` 已在全部 5 个 deny 路径调用 `recordDenial`，阈值暂停机制已完整接通。但 `NoOpDenialLedger` 不持久化——一旦 session 恢复（新 ledger 实例），拒绝计数归零，已暂停的 session 可绕过阈值重新执行（审计发现 L3-G5 的核心关切）。`DBDenialLedger` 是契约的第三个实现（与 `NoOpDenialLedger` 同级），通过 `DefaultAgentEngine.setDenialLedger(...)` 显式注册启用，`NoOpDenialLedger` 保持 shipped 默认。

## Current Baseline

- **L3-6 contract surface ✅ landed (plan 177)**: `IDenialLedger`（4 方法：`recordDenial` / `isPaused` / `getDenialCount` / `reset`）+ `DenialRecord`（不可变值对象：sessionId、toolName、layerSource、reason、matchedRule、timestamp）+ `DenialRecordOutcome`（count + thresholdExceeded）+ `DenialLayerSource`（6 枚举值）+ `NoOpDenialLedger`（无状态 pass-through 默认）均位于 `io.nop.ai.agent.security` 包
- **Dispatch-path integration ✅ wired (plan 177)**: `ReActAgentExecutor` 的 `handleDenialAndCheckThreshold` 在全部 5 个 deny 路径（L1 tool access / L1 permission / L1 path access / L2 security policy / L3 approval gate）调用 `ledger.recordDenial(...)`；阈值暂停双重机制已接通（dispatch for-loop break + ReAct 迭代开始 `isPaused` 检查 break reactLoop）；`post-loop bookkeeping` 排除 `paused` 状态（不发布 `EXECUTION_COMPLETED`）。dispatch path 调用的是接口方法，对具体实现透明——`DBDenialLedger` 通过现有 setter 注入即可被 dispatch path 调用，无需引擎代码变更
- **Engine wiring ✅ exists (plan 177)**: `DefaultAgentEngine` 已有 `IDenialLedger` 的 mutable field（声明处初始化为 `NoOpDenialLedger`）+ null-fallback setter（`setDenialLedger`）+ getter；`resolveExecutor` 将 ledger 传递给 `ReActAgentExecutor.Builder`。`DBDenialLedger` 通过 `setDenialLedger(new DBDenialLedger(dataSource))` 注册，无需引擎接线改动
- **`DBDenialLedger` NOT implemented**: grep `DBDenialLedger` in `nop-ai/` → 0 hits（verified via glob `nop-ai/**/DBDenialLedger*.java` → no files found）。这是本计划要收口的 gap
- **nop-dao dependency ✅ present (plan 171)**: `nop-ai/nop-ai-agent/pom.xml` line 33 已含 `nop-dao` 依赖（plan 171 为 `DBMessageService` 添加）
- **DB-backed implementation pattern ✅ established (plan 171)**: `DBMessageService`（`io.nop.ai.agent.message` 包）使用 raw JDBC（`DataSource` + `PreparedStatement`），非 `IOrmSession`。配套 `AiAgentMessageTable` 常量类（DDL + 列常量）+ `app.orm.xml` entity 定义（`_vfs/nop/ai/agent/orm/app.orm.xml`）。测试使用 `SimpleDataSource` + H2 in-memory（`jdbc:h2:mem:...`），无 mock——完整持久化链路端到端验证
- **ORM model location**: `_vfs/nop/ai/agent/orm/app.orm.xml` 是源 ORM 模型（非生成的 `_app.orm.xml`），当前含一个 entity（`AiAgentMessage` → `ai_agent_message` 表）。新增 entity 编辑此文件。ORM 模型结构是 Protected Area（plan-first）——本计划即 plan-first 文档
- **Design §6.2 persistence contract**: `persistence = DB`（DenialLedger 持久化到数据库）；`denialThreshold` 默认 3；`pauseBehavior = sticky`（延期至 successor——当前实现是非 sticky 的）。架构决策 1 明确：持久化是 `DBDenialLedger`（successor）的职责，非接口契约的硬性要求
- **`DenialRecord` 全部字段为简单类型**: String（sessionId / toolName / reason / matchedRule）+ `DenialLayerSource` enum + long（timestamp）。无 opaque payload（与 messenger 的 `AgentMessageEnvelope` 不同，后者 payload 是任意 Object 需 JSON 序列化）——denial 记录适合列存储（每字段一列），便于 SQL COUNT / DELETE by session 查询
- **Thread-safety requirement (IDenialLedger Javadoc)**: 实现必须线程安全——多 session 可能并发访问同一 ledger 实例，per-session 计数必须独立。`NoOpDenialLedger` 无状态天然线程安全；`DBDenialLedger` 通过 DB 操作实现线程安全（每条 SQL 语句原子，per-session INSERT/COUNT/DELETE 互不干扰）

## Goals

- `DBDenialLedger implements IDenialLedger` 位于 `io.nop.ai.agent.security` 包——`IDenialLedger` 的第三个实现（与 `NoOpDenialLedger` 同级），将 per-session 拒绝记录持久化到 `ai_agent_denial` 表，per-session COUNT 达阈值标记暂停
- `ai_agent_denial` 表的 ORM 实体定义（`app.orm.xml` 新增 entity，plan-first per Protected Areas: ORM 模型结构）+ 表常量类（DDL + 列常量，遵循 `AiAgentMessageTable` 模式）
- **持久化跨实例存活**（核心价值）：denial 记录持久化到 DB 后，销毁 ledger 实例并新建（共享同一 DB）→ `getDenialCount` / `isPaused` 返回与销毁前一致的值——拒绝计数与暂停状态不因 ledger 实例重建而丢失
- `DBDenialLedger` 通过现有 `DefaultAgentEngine.setDenialLedger(...)` 注册——引擎接线无变更；dispatch path 的 `handleDenialAndCheckThreshold` 调用 `recordDenial` 时拒绝记录实际落入 DB
- `NoOpDenialLedger` 保持 shipped 默认——不设 ledger 的引擎行为与接线前完全一致（0 spurious 暂停）
- `denialThreshold` 可配置（构造器/参数注入，默认 3 per 设计 §6.2）
- 设计文档 §6.2 更新：`persistence = DB` 从"successor 职责"变为"已落地"
- 单元测试覆盖：四个接口方法的 DB-backed 行为、阈值边界、线程安全、持久化跨实例存活、向后兼容、dispatch-path 接线

## Non-Goals

- **Sticky-pause 恢复协议**: 设计 §6.2 `pauseBehavior = sticky`（暂停后只有人类干预才能恢复）的完整语义——`IDenialLedger.reset` 的调用方和调用时机、人类审批通道集成、恢复工作流——是独立后续工作项（plan 177 deferred it；它依赖 `DBDenialLedger` 但自身是独立功能面）。本计划交付持久化计数 + 暂停状态存活；`reset` 方法本身实现为清空 session 的 DB 记录（契约已要求），但"谁在何时调用 reset"的 sticky 恢复协议不在范围
- **Fingerprint 计算**: `SHA-256(actionKind + argv + cwd + criticalEnv)[:32]` fingerprint 基础设施归属于 L3-7（`IPostDenialGuard`，plan 178 已交付 fingerprint matching）。`DenialRecord` 的 fingerprint 字段扩展在后续增强
- **denial-config XDSL**: 不引入 `denial-config.xml` 或 Delta 配置。threshold 通过构造器注入
- **配置校验**: "denial threshold 配置存在但 ledger 仍为默认时警告"需要 XDSL 配置基础设施，不在范围
- **引擎接线变更**: `DefaultAgentEngine.setDenialLedger` + `ReActAgentExecutor.Builder` 接收 ledger 已由 plan 177 交付，无需修改
- **拒绝记录保留/清理策略**: 已 paused 的 denial 记录在表中累积。保留策略（TTL 删除、定期清理）是维护优化
- **审计查询接口**: 对持久化 denial 记录的 DB 查询 API（按时间范围 / session / layer 统计）是后续增强
- **提升到通用模块**: `DBDenialLedger` 放在 nop-ai-agent（同 `DBMessageService`）；若非 Agent 消费者出现再提升

## Scope

### In Scope

- `ai_agent_denial` 表的 ORM entity（`_vfs/nop/ai/agent/orm/app.orm.xml` 新增）
- 表常量类（DDL + 列常量 + 状态/索引常量，遵循 `AiAgentMessageTable` 模式）
- `DBDenialLedger implements IDenialLedger`（`io.nop.ai.agent.security` 包），四个接口方法全部 DB-backed
- `denialThreshold` 构造器/参数注入（默认 3）
- 线程安全（经 DB 操作保证，per-session 计数独立）
- 持久化跨实例存活（拒绝记录 + 暂停状态不因 ledger 实例重建丢失）
- 单元测试（H2 in-memory，无 mock）：四个接口方法 + 阈值边界 + 线程安全
- 端到端测试：持久化跨实例存活（销毁旧实例 → 建新实例共享 DB → 计数/暂停状态一致）
- 接线验证：`DBDenialLedger` 经 `setDenialLedger` 注入引擎后，dispatch path 的 `recordDenial` 调用使拒绝记录落入 DB
- 向后兼容测试：`NoOpDenialLedger` 默认行为不变，全部现有测试通过
- 设计文档 §6.2 更新（`persistence = DB` 标记为已落地）

### Out Of Scope

- Sticky-pause 恢复协议（独立后续工作项）
- Fingerprint 基础设施（L3-7 领域）
- denial-config XDSL / 配置校验
- 引擎接线变更
- 拒绝记录保留/清理策略
- 审计查询接口
- 提升到通用模块

## Execution Plan

### Phase 1 - ORM 实体 + 表常量类 + Schema 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml` (新增 `ai_agent_denial` entity), `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/` (表常量类), `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/`

- Item Types: `Decision | Proof`

- [x] **Decision（列存储 vs JSON blob）**: `ai_agent_denial` 表采用列存储（每字段一列），而非 messenger 的 JSON blob 模式。理由：(1) `DenialRecord` 全部字段为简单类型（String + enum + long），无 opaque payload（messenger 的 `AgentMessageEnvelope.payload` 是任意 Object 才需 JSON）；(2) 核心操作是 per-session COUNT + per-session DELETE，列存储使 `SELECT COUNT(*) WHERE SESSION_ID=?` 和 `DELETE WHERE SESSION_ID=?` 成为高效原生 SQL；(3) `DenialLayerSource` 枚举存为 VARCHAR（枚举名），无序列化复杂度
- [x] 在 `_vfs/nop/ai/agent/orm/app.orm.xml` 新增 `ai_agent_denial` entity（遵循现有 `AiAgentMessage` entity 的列定义风格：主键 SID + sessionId + toolName + layerSource + reason + matchedRule + timestamp + createdAt）。表名 / 列名遵循 Nop 数据库设计规范（snake_case 表名，UPPER_CASE 列代码）。可在 `<domains>` 中复用现有 domain 或新增 denial 专用 domain
- [x] 创建表常量类（`io.nop.ai.agent.security` 包），定义：表名常量、列名常量、DDL CREATE TABLE（含 IF NOT EXISTS）、DDL CREATE INDEX（SESSION_ID 索引，支撑 COUNT/DELETE 查询）。遵循 `AiAgentMessageTable` 的常量类模式
- [x] 单元测试：DDL 在 H2 中成功建表 + 建索引（遵循 `TestAiAgentMessageTable` 模式，使用 `SimpleDataSource` + H2 in-memory，无 mock）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `app.orm.xml` 含 `ai_agent_denial` entity 定义，`./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] 表常量类存在于 `io.nop.ai.agent.security` 包，含表名/列名/DDL/索引常量
- [x] **新增功能测试**: DDL 建表 + 建索引测试在 H2 中通过（Minimum Rules #25）
- [x] **无静默跳过**: DDL 执行失败时抛异常（不静默忽略 schema 初始化失败）（Minimum Rules #24）
- [x] No owner-doc update required（设计文档更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DBDenialLedger 实现 + 单元测试

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DBDenialLedger.java`, `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/`

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（持久化方案——raw JDBC，遵循 DBMessageService 模式）**: `DBDenialLedger` 使用 raw JDBC（`DataSource` + `PreparedStatement`），非 `IOrmSession`。理由：(1) 与 plan 171 `DBMessageService` 同级的兄弟实现模式，保持一致；(2) `IDenialLedger` 操作是同步的 record/count/delete，无后台轮询需求（messenger 需要轮询投递，denial ledger 是同步请求-响应）；(3) raw JDBC 对简单 COUNT/INSERT/DELETE 足够且无额外抽象层
- [x] **Decision（thread-safety——经 DB 操作保证）**: `DBDenialLedger` 的线程安全由 DB 操作保证而非内存锁：每个 `recordDenial` 是一次原子 INSERT + 一次 COUNT 查询（计数从 DB 实时读取，非内存累加）；`isPaused` / `getDenialCount` 是 COUNT 查询；`reset` 是 DELETE。多 session 并发访问同一 ledger 实例时，per-session INSERT/COUNT/DELETE 互不干扰（WHERE SESSION_ID=? 隔离）。无需 `ConcurrentHashMap` 内存状态——计数始终从 DB 读取，确保跨实例一致
- [x] 实现 `DBDenialLedger implements IDenialLedger`：
  - 构造器接收 `DataSource`（必填）+ `denialThreshold`（可选，默认 3 per 设计 §6.2）；构造器初始化 schema（建表 + 建索引，遵循 `DBMessageService.start()` 的 `initSchema()` 模式）
  - `recordDenial(DenialRecord)`: INSERT 一条 denial 记录到 `ai_agent_denial` → COUNT 该 session 的累计记录数 → 返回 `DenialRecordOutcome.of(count, count >= threshold)`。匿名 session（sessionId 为 null）的处理需明确（遵循 `IDenialLedger` 契约——null sessionId 时行为需可预测且文档化）
  - `isPaused(sessionId)`: COUNT 该 session 记录数 >= threshold
  - `getDenialCount(sessionId)`: COUNT 该 session 记录数
  - `reset(sessionId)`: DELETE 该 session 的全部 denial 记录（清空计数 + 暂停状态）
- [x] 单元测试：`recordDenial` 后 `getDenialCount` 递增；`DenialRecordOutcome.count` 正确；`thresholdExceeded` 在达到 threshold 时翻转为 true
- [x] 单元测试：`isPaused` 在 count < threshold 时 false，count >= threshold 时 true
- [x] 单元测试：`reset` 后 `getDenialCount` 归零、`isPaused` 恢复 false
- [x] 单元测试：per-session 独立性——session A 的 denial 不影响 session B 的 count / isPaused（遵循 IDenialLedger 契约 "a denial in session A must not affect the denial count of session B"）
- [x] 单元测试：阈值边界——threshold = 1（首次 deny 即暂停）、threshold = 3（默认值，第 3 次 deny 触发）
- [x] 单元测试：线程安全——多线程并发 `recordDenial` 不同 session，各 session count 独立正确（无串扰）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DBDenialLedger` 存在于 `io.nop.ai.agent.security` 包，implements `IDenialLedger`
- [x] `recordDenial` 将记录持久化到 `ai_agent_denial` 表（通过查询 DB 表行数验证，非内存状态）
- [x] `getDenialCount` / `isPaused` 从 DB 实时读取（COUNT 查询），非内存缓存
- [x] `reset` 从 DB 删除 session 记录（通过查询 DB 表行数验证）
- [x] **新增功能测试**: 四个接口方法 + 阈值边界 + per-session 独立性 + 线程安全——各有对应通过的测试（Minimum Rules #25）
- [x] **无静默跳过**: DB 操作失败时抛异常（不吞 SQLException / 不静默返回 count=0）；匿名 session 行为明确文档化（非静默忽略）（Minimum Rules #24）
- [x] **接线验证**: `DBDenialLedger` 经 `DefaultAgentEngine.setDenialLedger(...)` 注入后可被构造（构造器 + setter 可达）——Phase 3 端到端验证完整调用链
- [x] No owner-doc update required（Phase 3）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` Phase 2 新增测试通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 持久化跨实例端到端 + 引擎接线验证 + 设计文档

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/`（端到端测试）, `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §6.2, `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 L3-6 行

- Item Types: `Proof | Follow-up`

- [x] 端到端测试（**持久化跨实例存活——核心价值**）：创建 `DBDenialLedger` 实例 A（threshold = 2，连接 H2 DB）→ `recordDenial` 2 次（session S）→ `isPaused(S)` 返回 true → **销毁实例 A**（不再引用）→ 创建 `DBDenialLedger` 实例 B（共享同一 H2 DB）→ `getDenialCount(S)` 返回 2、`isPaused(S)` 返回 true。**这证明持久化经 DB 而非内存——如果 DBDenialLedger 内部偷偷用了内存状态，新实例 B 的计数会归零。** 关闭一个 DB 连接重建实例是 session 恢复 / 跨进程的核心模拟场景
- [x] 端到端测试（接线验证——dispatch path 实际写入 DB）：构造 `DefaultAgentEngine` + `setDenialLedger(new DBDenialLedger(dataSource, threshold))` → 配置功能化 `IToolAccessChecker` 对 tool call deny → 执行 ReAct 循环 → dispatch path 的 `handleDenialAndCheckThreshold` 调用 `ledger.recordDenial(...)` → **直接查询 `ai_agent_denial` 表验证拒绝记录实际落入 DB**（非仅 ledger 对象接收了调用）。遵循 plan 177 的端到端测试骨架（功能化 checker + LLM mock）
- [x] 端到端测试（向后兼容）：构造 `DefaultAgentEngine` **不**设 ledger → 默认 `NoOpDenialLedger` → ReAct 循环行为与接线前完全一致——全部现有 dispatch-path denial-ledger 测试通过，0 spurious 暂停
- [x] 更新 `nop-ai-agent-security-and-permissions.md` §6.2：(1) 状态行 `DBDenialLedger`（DB 持久化实现）从"deferred successor"改为"已落地"；(2) `persistence = DB` 行注明 `DBDenialLedger` 已交付；(3) 架构决策 1 更新——持久化不再是 successor 职责而是已落地实现；(4) 记录架构决策：列存储方案（vs JSON blob）、raw JDBC（vs IOrmSession，与 DBMessageService 一致）、thread-safety 经 DB 操作（计数实时从 DB 读取而非内存缓存）；(5) `pauseBehavior = sticky` 仍标注为 deferred（sticky 恢复协议是独立后续工作项，依赖 DBDenialLedger 但自身尚未落地）
- [x] 更新 `nop-ai-agent-roadmap.md` §4 L3-6 行：确认 `DBDenialLedger` 已交付（L3-6 行已标 ✅——更新注释或保持，确保不矛盾）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（持久化跨实例存活）：销毁旧 ledger 实例 + 建新实例（共享 DB）→ 计数 / 暂停状态一致——证明持久化经 DB 而非内存（Minimum Rules #22 Anti-Hollow Rule）
- [x] **接线验证**：`DBDenialLedger` 经 `setDenialLedger` 注入引擎后，dispatch path 的 `recordDenial` 调用使拒绝记录**实际落入 `ai_agent_denial` 表**（通过直接查询 DB 表验证，非仅 ledger 对象被传递）（Minimum Rules #23 Wiring Verification Rule）
- [x] **端到端验证**（向后兼容）：`NoOpDenialLedger` 默认行为不变，全部现有测试通过，0 spurious 暂停
- [x] **Anti-Hollow Check**: 跨实例存活测试证明计数确实从 DB 读取（而非内存缓存）——两个独立实例无共享内存状态，仅共享 DB；如果 DBDenialLedger 内部偷偷用内存状态，新实例计数会归零，测试会失败
- [x] **无静默跳过**: DB 操作失败抛异常（非静默返回 count=0 / false）；schema 初始化失败抛异常（非静默忽略）
- [x] **新增功能测试**: 持久化跨实例存活 + dispatch-path 接线（DB 写入验证）+ 向后兼容——各有对应通过的测试（Minimum Rules #25）
- [x] `nop-ai-agent-security-and-permissions.md` §6.2 已更新（DBDenialLedger 标记为已落地 + 架构决策记录）；`nop-ai-agent-roadmap.md` L3-6 行不矛盾
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全部通过（含新增 + 现有测试不受影响）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `DBDenialLedger implements IDenialLedger` 已落地（四个接口方法全部 DB-backed）
- [x] `ai_agent_denial` 表的 ORM entity + 表常量类存在
- [x] per-session 拒绝记录持久化到 DB（COUNT/INSERT/DELETE 经 SQL，非内存状态）
- [x] 持久化跨实例存活已验证（销毁旧实例 + 建新实例共享 DB → 计数/暂停状态一致）
- [x] dispatch path 的 `recordDenial` 调用使拒绝记录实际落入 DB（接线验证，非仅组件传递）
- [x] `NoOpDenialLedger` 默认向后兼容（全部现有测试通过，0 spurious 暂停）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs（设计 §6.2、roadmap L3-6）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**: closure audit 已验证 (a) recordDenial → DB INSERT → COUNT 从 DB 读取调用链在运行时连通（经 DB 表，非自建内存状态），(b) 跨实例存活测试证明计数确实从 DB 读取（两独立实例无共享内存仅共享 DB），(c) 无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/179-nop-ai-agent-db-denial-ledger.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码为 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### Sticky-pause 人类干预恢复协议

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §6.2 规定 `pauseBehavior = sticky`（暂停后只有人类干预才能恢复）。本计划交付 DB 持久化计数 + 暂停状态存活 + `reset` 方法的 DB 实现（清空 session 记录）。"只有人类干预才能恢复"的完整 sticky 语义需要恢复工作流（`reset` 的调用方和调用时机）+ 可能的审批通道集成 + session 恢复后的重新执行协议。这是一个独立功能面，依赖 `DBDenialLedger` 的持久化能力但自身有独立的用户可见行为设计。
- Successor Required: yes
- Successor Path: 独立 governance / sticky-recovery plan（plan 177 也将此项 deferred 到此 successor）

### Fingerprint 基础设施（DenialRecord fingerprint 字段扩展）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `SHA-256(actionKind + argv + cwd + criticalEnv)[:32]` fingerprint 基础设施归属于 L3-7（`IPostDenialGuard`，plan 178 已交付 `ActionFingerprint` 计算 + exact-fingerprint matching）。`DenialRecord` 的 fingerprint 字段扩展在 L3-7 fingerprint 基础设施落地后作为增强——届时 `ai_agent_denial` 表可新增 `FINGERPRINT` 列。
- Successor Required: yes
- Successor Path: L3-7 fingerprint 增强或独立 plan

### 拒绝记录保留/清理策略

- Classification: `optimization candidate`
- Why Not Blocking Closure: 已 paused 的 denial 记录在 `ai_agent_denial` 表中累积。保留策略（TTL 删除、定期清理、reset 后的记录归档）是维护优化，不改变 `DBDenialLedger` 契约。表增长率受 per-session denial 量约束（threshold 通常 = 3，记录量有限）。
- Successor Required: no

### denial-config XDSL + 配置校验

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 不引入 `denial-config.xml`。threshold 通过构造器注入（默认 3）。配置校验（"denial threshold 配置存在但 ledger 仍为默认时警告"）需要 XDSL 配置基础设施，独立于 DB 持久化实现。
- Successor Required: no

## Non-Blocking Follow-ups

- Sticky-pause 恢复协议（`reset` 的调用方/时机 + 人类审批通道集成 + session 恢复后重新执行协议）
- Fingerprint 字段扩展（`DenialRecord` + `ai_agent_denial` 表新增 FINGERPRINT 列，与 L3-7 fingerprint 基础设施对齐）
- denial-config XDSL（§9 Layer 3 渐进式增强）
- 配置校验（threshold 配置与 ledger 实现一致性检查）
- 拒绝记录 TTL 自动清理 + 定期 purge
- 审计查询接口（对持久化 denial 记录的 DB 查询 API：按时间范围 / session / layer 统计）
- 提升到通用模块（当非 Agent 消费者出现时）

## Closure

Status Note: `DBDenialLedger` 已落地——`IDenialLedger` 的 DB 持久化功能化实现，将 per-session 拒绝计数 + 暂停状态持久化到 `ai_agent_denial` 表，经 ledger 实例重建后依然存活。三个 Phase 全部完成，全部 Exit Criteria 与 Closure Gates 由独立子 agent 验证 PASS。`NoOpDenialLedger` 保持 shipped 默认（0 spurious 暂停），向后兼容。剩余工作（sticky-pause 恢复协议、fingerprint 字段扩展、TTL 清理、审计查询接口）已显式移出 scope（Deferred But Adjudicated / Non-Blocking Follow-ups）。
Completed: 2026-06-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session, task_id `ses_13a09fe4fffeYy1Xk32lldWhjh`，subagent_type=general）——非实现者自查
- Audit Session: `ses_13a09fe4fffeYy1Xk32lldWhjh`
- Evidence:
  - **Phase 1 Exit Criteria** — 全部 PASS：
    - `app.orm.xml:50-78` 定义 `ai_agent_denial` entity（8 列：SID/SESSION_ID/TOOL_NAME/LAYER_SOURCE/REASON/MATCHED_RULE/DENIAL_TIMESTAMP/CREATED_AT）
    - `AiAgentDenialTable.java` 含表名/列名/INDEX_SESSION_ID/DDL_CREATE_TABLE(IF NOT EXISTS)/DDL_CREATE_INDEX
    - `TestAiAgentDenialTable`（6 测试）：DDL 建表/建索引/列校验/幂等/insert+COUNT+DELETE 全通过
    - schema 初始化失败抛 `NopAiAgentException`（`DBDenialLedger.initSchema` 包裹 SQLException），非静默跳过
  - **Phase 2 Exit Criteria** — 全部 PASS：
    - `DBDenialLedger.java:50` implements `IDenialLedger`；四方法全 DB-backed（`recordDenial`=INSERT+COUNT, `isPaused`/`getDenialCount`=COUNT, `reset`=DELETE）
    - 实例仅含 `dataSource` + `denialThreshold` 字段，**无 ConcurrentHashMap / 累加器**——计数始终从 DB 读取（`countDenials` SELECT COUNT(*) WHERE SESSION_ID=?）
    - 3 个 catch 块全部 re-throw `NopAiAgentException`（不吞异常）；匿名 session null sessionId → `DenialRecordOutcome.of(0,false)`（文档化的非静默行为）
    - `TestDBDenialLedger`（16 测试）：recordDenial 递增+DB 行数验证、阈值边界(threshold=1/3)、isPaused 翻转、getDenialCount 实时读取、reset 删除、per-session 独立性、匿名 session 不持久化、10×8 并发线程安全、构造器校验、schema 初始化
  - **Phase 3 Exit Criteria** — 全部 PASS：
    - **Anti-Hollow**（持久化跨实例存活）：`TestDBDenialLedgerCrossInstance.persistenceSurvivesLedgerInstanceReconstruction`——实例 A recordDenial 到 paused → **null 实例 A** → 新建实例 B（共享 DB）→ `getDenialCount==2` + `isPaused==true`。若 DBDenialLedger 偷用内存状态，新实例计数归零，测试会失败。额外 3 测试：跨实例累加/reset 可观测/构造器幂等不丢行
    - **Wiring Verification**：`TestDBDenialLedgerEngineWiring.dispatchPathDenialIsPersistedToDbTable`——`engine.setDenialLedger(ledger)` + DenyAllTools deny → **直接 SQL 查询 `ai_agent_denial` 表** `countDenialRows(sessionId)>=1`（非仅 ledger 对象被传递）。`ReActAgentExecutor.java:1002` `denialLedger.recordDenial(record)` 调用链确认
    - **向后兼容**：`defaultNoOpLedgerProducesNoSpuriousPausesAndWritesNoDbRows`——不设 ledger → `getDenialLedger() instanceof NoOpDenialLedger` + result.status != paused
    - `nop-ai-agent-security-and-permissions.md` §6.2：`DBDenialLedger` 标记"已落地"；`persistence=DB` 注明"已交付"；架构决策 7/8/9（列存储/raw JDBC/DB 线程安全）；`pauseBehavior=sticky` 仍延期
    - `nop-ai-agent-roadmap.md` L3-6 行 = ✅（无矛盾）
  - **Closure Gates** — 全部 PASS：见上述 evidence；`NoOpDenialLedger` 保持 shipped 默认（`DefaultAgentEngine.java:93` 字段默认 + line 395 null-fallback），向后兼容；deferred 项均为 out-of-scope improvement / optimization candidate，无 in-scope live defect 被降级
  - **Anti-Hollow 检查结果**：(a) recordDenial→INSERT→COUNT-from-DB 调用链运行时连通（经 DB 表，非自建内存状态）；(b) 跨实例存活测试证明计数确实从 DB 读取（两独立实例无共享内存仅共享 DB）；(c) `DBDenialLedger` 无空方法体/静默跳过/no-op 作为正常实现
  - **checklist 完整性证据**：`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/179-nop-ai-agent-db-denial-ledger.md --strict` exit 0（Passed:1, Failed:0）
  - **automated anti-hollow scan**：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` exit 0（Critical:0, High:0, Medium:0, Low:0）
  - **构建验证**：`./mvnw compile -pl nop-ai/nop-ai-agent -am` exit 0；`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → 1173 tests, 0 failures, 0 errors
  - **Deferred 项分类检查**：sticky-pause 恢复协议 / fingerprint 字段扩展 = out-of-scope improvement（successor required）；拒绝记录保留/清理 = optimization candidate（no successor）；denial-config XDSL + 配置校验 = out-of-scope improvement（no successor）。无 in-scope live defect / contract drift 被降级
  - **Minor follow-up**（非阻塞）：`IDenialLedger.java` persistence Javadoc 已从"successor (deferred)"更新为"implementation"

Follow-up:

- Sticky-pause 恢复协议（`reset` 的调用方/时机 + 人类审批通道集成 + session 恢复后重新执行协议）——独立 governance / sticky-recovery plan
- Fingerprint 字段扩展（`DenialRecord` + `ai_agent_denial` 表新增 FINGERPRINT 列，与 L3-7 fingerprint 基础设施对齐）
- No remaining plan-owned work

## Follow-up handled by 180-nop-ai-agent-sticky-pause-recovery.md

The sticky-pause recovery protocol（`pauseBehavior = sticky` 完整语义——`reset` 的调用方和调用时机 + session 恢复后重新执行协议）carry-over from this plan's `Deferred But Adjudicated`（Sticky-pause 人类干预恢复协议 — `Successor Required: yes, Successor Path: 独立 governance / sticky-recovery plan`）and `Non-Blocking Follow-ups` sections is handled by plan 180 (`ai-dev/plans/180-nop-ai-agent-sticky-pause-recovery.md`). Plan 180 delivers the `IAgentEngine.resumeSession(sessionId, approver, reason)` human-intervention recovery entry point + `AgentEventType.SESSION_RESUMED` event + `DefaultAgentEngine` implementation (validate paused → `denialLedger.reset` → publish `SESSION_RESUMED` → re-execute) + sticky enforcement verification (calling `execute()` on a paused session without resume re-pauses immediately; the only way to unpause is explicit `resumeSession`) + design §6.2 update (`pauseBehavior = sticky` from "deferred" to "landed"). `IApprovalChannel` integration (gated resume) remains deferred to the plan 176 successor — the resume action itself IS the human intervention.
