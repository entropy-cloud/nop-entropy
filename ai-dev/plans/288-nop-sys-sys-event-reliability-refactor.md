# 288 nop-sys sys-event 可靠广播与分区队列重构

> Plan Status: completed
> Last Reviewed: 2026-07-08
> Source: user request `现在编写重构计划`; `ai-dev/design/nop-sys/sys-event-architecture.md`; live baseline in `nop-sys/model/nop-sys.orm.xml` and `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java`

## Purpose

将 `nop-sys` 当前基于单表 `NopSysEvent` 的混合事件实现，收口为两条语义清晰的本地链路：

- 广播事件：append-only event stream + subscriber cursor + lease
- 普通事件：shared queue + partition ownership + per-partition ordering

本计划的目标是把现有“广播不可靠、普通事件分区契约未闭合、随机分区破坏同键顺序”的 live gap 收敛为可验证的本地基线，同时保持“业务数据与事件在同一数据库事务内发布”的既有价值。

## Current Baseline

- `SysDaoMessageService.sendAsync()` / `sendMultiAsync()` 当前统一写 `NopSysEvent`，并默认用 `MathHelper.random().nextInt(Short.MAX_VALUE)` 填充 `partitionIndex`。
- `processBroadcastEvent()` 当前依赖进程内 `lastBroadcastEvent` 指针增量扫描广播事件；重启或实例漂移后没有持久化 cursor 作为恢复真源。
- `processNonBroadcastEvent()` 当前从 `NopSysEvent` 轮询 `WAITING` 事件，先批量更新 `scheduleTime/processTime`，再逐条执行；没有 `leaseOwner/leaseExpireTime` 或显式 partition ownership 模型。
- `nop-sys/model/nop-sys.orm.xml` 当前只有 `NopSysEvent` 这一套事件实体；未建模广播事件流表和广播订阅游标表。
- `docs-for-ai/03-modules/nop-sys.md` 仍把事件能力描述为“基于 `nop_sys_event` 表的进程内事件队列，支持分区扫描 / 延迟事件 / 状态追踪”，尚未表达广播 / 普通事件分离。
- `ai-dev/design/nop-sys/sys-event-architecture.md` 已给出目标设计基线，但当前仍标记为草案，且 live code 尚未对齐。
- 当前未发现覆盖 `SysDaoMessageService` 发送、广播恢复、分区串行、lease 恢复的 focused tests。

## Goals

- 为 `sys-event` 建立与当前设计文档一致的持久化模型：普通事件队列表、广播事件流表、广播订阅游标表。
- 普通事件发布端停止随机默认分区；相同业务顺序键稳定落到同一 `partitionIndex`。
- 广播消费端不再依赖 JVM 内存游标；重启后可从持久化 cursor 恢复，并保持 subscriber 级顺序推进。
- 普通事件消费端具备显式 partition ownership / lease 模型，保证分区内串行、分区间并行。
- 在不把 `sys-event` 建模成 `nop-batch` task 的前提下，允许引入 windowed fetch、bounded session reuse 等 chunk-like 优化，并以 focused tests 验证正确性边界不被破坏。
- 同步 owner docs，使 `docs-for-ai/03-modules/nop-sys.md` 与实现后的 live baseline 一致。

## Non-Goals

- 不引入 Kafka、RocketMQ、Pulsar 等外部 MQ。
- 不追求 exactly-once；本计划的 supported baseline 仍是 at-least-once + listener 幂等。
- 不实现“每个实例都收到一次”的 instance-scoped broadcast 新语义。
- 不把 `sys-event` 改造成完整 `nop-batch` task，或复用 batch task state / completedIndex 作为事件正确性边界。
- 不把 `nop-sys` 其它能力（sequence、code-rule、lock、notice template）混入本计划。
- 不手改任何 `_gen/`、`_*.xml`、`_*.java` 生成物。

## Scope

### In Scope

- `nop-sys/model/nop-sys.orm.xml`
- `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/` 下的发送、扫描、消费实现
- `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/entity/` 下与事件模型对应的保留层类
- `nop-sys/nop-sys-dao/src/test/java/` 和/或 `nop-sys/nop-sys-service/src/test/java/` 下的 focused regression / integration tests
- `ai-dev/design/nop-sys/sys-event-architecture.md`
- `docs-for-ai/03-modules/nop-sys.md`
- `docs-for-ai/INDEX.md`（如模块路由描述需要同步）
- `docs-for-ai/04-reference/source-anchors.md`（如事件实现锚点发生变化）
- `ai-dev/logs/2026/07-07.md`

### Out Of Scope

- 任意 `_gen/`、`_*.xml`、`_*.java` 生成物
- 外部 MQ 接入、跨进程消息桥接、云消息服务适配
- 全新的事件管理后台、游标人工运维 UI、可视化监控页面
- 旧历史事件数据的离线迁移工具；如需要数据迁移，单列 successor plan
- 非 `sys-event` 的 `nop-sys` 子系统重构

## Execution Plan

### Phase 1 - Contract Sync And Source Model Baseline

Status: completed
Targets: `ai-dev/design/nop-sys/sys-event-architecture.md`, `nop-sys/model/nop-sys.orm.xml`, `docs-for-ai/03-modules/nop-sys.md`

- Item Types: `Decision`, `Fix`, `Proof`

- [x] 将 `sys-event` 设计文档从草案收敛为当前实施基线：明确本轮支持的广播表/游标表/普通事件表边界，以及普通事件 lease / partition ownership 的最小契约。
- [x] 在 source ORM model 中加入本轮所需的持久化对象与字段，确保普通事件、广播事件流、广播订阅游标都有明确模型承载。
- [x] 裁定现有 `NopSysEvent` 在重构后的角色：是否保留为普通事件表保留名，或作为过渡命名存在，但 owner-doc 必须清楚表达其语义边界。
- [x] 裁定普通事件的顺序键来源与缺省策略，避免实现阶段再回到随机默认分区。
- [x] 更新 `docs-for-ai/03-modules/nop-sys.md` 的事件队列描述，使 owner doc 不再宣称“广播与普通事件共用单表”这一旧 baseline。

Exit Criteria:

- [x] `ai-dev/design/nop-sys/sys-event-architecture.md` 已明确本轮 live baseline，不再保留会误导实现的草案性模糊表述。
- [x] `nop-sys/model/nop-sys.orm.xml` 已表达普通事件、广播事件流、广播订阅游标所需 source model；未手改生成物。
- [x] `docs-for-ai/03-modules/nop-sys.md` 已同步最小 owner-facing 行为：广播 / 普通事件分离、at-least-once、分区内顺序。
- [x] `docs-for-ai/INDEX.md` 与 `docs-for-ai/04-reference/source-anchors.md` 已按需要更新；若无需更新，需在执行时明确记为 `No change required`。
- [x] No new production test required in this phase if only source model + docs baseline land; if any runtime behavior also lands here, corresponding tests must be added in the same phase.
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - Publish Path Split And Persistence Routing

Status: completed
Targets: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/`, focused tests

- Item Types: `Fix`, `Proof`

- [x] 让发送路径按广播 / 普通事件语义分别写入对应持久化对象，而不是继续把所有消息都落到同一条单表语义上。
- [x] 普通事件发布端按稳定顺序键计算 `partitionIndex`；不再使用随机默认值破坏同键顺序。
- [x] 保持“业务数据更新与事件发布在同一数据库事务中提交”的现有 outbox 价值，不把发送链路改造成事务外补偿写入。
- [x] 对没有顺序键的普通事件保留明确的降级规则，但必须在文档和测试中说明其不保证同键顺序。
- [x] 补 focused tests，覆盖广播写流、普通事件分区键稳定性、发送路径持久化路由。

Exit Criteria:

- [x] focused test：广播消息发布后进入广播事件流持久化对象，而不是普通事件队列表。
- [x] focused test：带相同业务顺序键的普通事件生成相同 `partitionIndex`；不同键可稳定分散到 short-range。
- [x] focused test：没有顺序键的普通事件走已裁定的缺省分区策略，且测试显式说明其语义边界。
- [x] **接线验证**：`sendAsync()` / `sendMultiAsync()` 入口已实际调用新的持久化路由逻辑，而不是只新增 helper 未接通。
- [x] **无静默跳过**：无法判定消息语义或必需字段缺失时，新增分支显式失败或走已记录的降级策略，不允许悄悄回退到旧随机分区逻辑。
- [x] 新增发送路径测试已覆盖本 Phase 新行为；不能只依赖旧测试。
- [x] No owner-doc update required beyond Phase 1 contract sync.
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - Broadcast Cursor, Lease, And Recovery Semantics

Status: completed
Targets: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/`, focused tests

- Item Types: `Fix`, `Proof`

- [x] 用 subscriber cursor + lease 替换广播消费端的 JVM 内存 `lastBroadcastEvent` 基线。
- [x] 明确广播订阅者身份、lease owner、cursor 单调推进和失败阻塞规则。
- [x] 保持广播顺序语义以 subscriber 为单位串行推进，不把并发吞吐优化放在顺序正确性之前。
- [x] 补 focused tests，覆盖 cursor 推进、重启恢复、失败后不越过、lease 竞争或接管。

Exit Criteria:

- [x] focused test：广播 subscriber 成功处理事件后，cursor 只按成功前缀推进。
- [x] focused test：subscriber 处理中途失败时，后续事件不会越过失败点推进 cursor。
- [x] focused test：重启或重新创建消费服务实例后，广播消费从持久化 cursor 恢复，而不是依赖旧 JVM 内存状态。
- [x] focused test：同一 `subscriberId + topic` 在任一时刻只允许一个有效 lease owner 执行。
- [x] **端到端验证**：至少一个广播场景从 `sendAsync()`/等价发布入口到 listener 执行再到 cursor 持久化完整跑通。
- [x] **接线验证**：广播扫描入口实际读取 cursor/lease 持久化对象并驱动 listener，不存在“新表已建但运行时仍走旧 `lastBroadcastEvent` 路径”的空壳接线。
- [x] **无静默跳过**：lease 获取失败、cursor 非法回退、subscriber 标识缺失等异常路径显式失败或记录错误，不静默吞掉。
- [x] No owner-doc update required beyond Phase 1 contract sync.
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - Simple Queue Partition Ownership And Bounded Execution Optimizations

Status: completed
Targets: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/`, focused tests

- Item Types: `Fix`, `Proof`

- [x] 为普通事件消费补齐 partition ownership / lease 语义，使 worker 对分区范围的负责关系成为显式运行时契约。
- [x] 保证普通事件分区内串行、分区间并行；失败事件阻塞所在分区头部，不允许后续事件越过。
- [x] 在不把 chunk completion 当作 ack 边界的前提下，引入或复用 windowed fetch、bounded session reuse、批量时间窗口扫描等执行优化。
- [x] 明确普通事件 worker 的 claim / retry / reschedule / fail 语义，并补 focused tests。

Exit Criteria:

- [x] focused test：同一分区内事件按 event order 串行执行，头部失败时后续事件不会被确认。
- [x] focused test：不同分区可并行推进，且不互相阻塞。
- [x] focused test：worker 失去 lease 或超时后，分区可由其他 worker 接管；接管后仍保持至少一次与分区内顺序约束。
- [x] focused test：windowed fetch 或 bounded session reuse 生效时，ack 边界仍以单 event / 连续成功前缀为准，而不是整批 chunk 一次确认。
- [x] **端到端验证**：至少一个普通事件场景从发布入口到分区 worker 处理、状态迁移、重试/失败阻塞完整跑通。
- [x] **接线验证**：分区 ownership / lease 运行时调用链已实际连通到事件扫描与处理入口，而不是只存在独立 helper 或未被调用的新组件。
- [x] **无静默跳过**：未实现的 lease / partition 分支不得用空方法体、`continue`、或吞异常绕过。
- [x] No owner-doc update required beyond Phase 1 contract sync.
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 5 - Verification, Owner-Doc Closure, And Audit

Status: completed
Targets: `nop-sys`, `docs-for-ai/03-modules/nop-sys.md`, this plan

- Item Types: `Proof`, `Decision`

- [x] 运行 `nop-sys` 受影响模块测试，至少覆盖 `nop-sys-dao` / `nop-sys-service` 与依赖。
- [x] 运行文档链接检查。
- [x] 启动独立 closure audit 子 agent，逐条核对本计划的 exit criteria、调用链和 deferred 分类。
- [x] 根据 closure audit 结果修复剩余问题，或将不阻塞项移入 `Deferred But Adjudicated`。
- [x] 写入 closure evidence 后运行 strict plan checklist 与 hollow scan。

Exit Criteria:

- [x] `./mvnw test -pl nop-sys -am` 通过；如受仓库无关上游问题阻塞，需记录阻塞点并补充最小可证明的受影响模块测试命令。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] 独立 closure audit evidence 已写入 `Closure` 段落。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/288-nop-sys-sys-event-reliability-refactor.md --strict` 退出码为 0（关闭前执行）。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-sys --severity high` 退出码为 0。
- [x] 受影响 owner docs 与 live repo 一致，且 `docs-for-ai/03-modules/nop-sys.md` 不再描述旧单表广播/普通事件混用基线。
- [x] `ai-dev/logs/` 对应日期收口条目已更新。

## Closure Gates

- [x] 广播 / 普通事件分离后的持久化模型已落地到 source model，且未手改生成物。
- [x] 普通事件发布端不再使用随机默认分区破坏同键顺序。
- [x] 广播消费端不再依赖 JVM 内存 `lastBroadcastEvent` 作为恢复真源。
- [x] 普通事件已具备显式 partition ownership / lease 语义，并有 focused proof。
- [x] chunk-like 执行优化若已引入，仍以单 event / 连续成功前缀作为 ack 正确性边界。
- [x] 广播与普通事件的端到端验证、接线验证、无静默跳过验证均已完成。
- [x] 受影响 design/owner docs 与 live repo 一致。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift。
- [x] 独立子 agent closure audit 已完成并记录 evidence。
- [x] **Anti-Hollow Check**：closure audit 已验证从发布入口到持久化路由、再到广播/普通事件消费出口的运行时调用链连通，且不存在旧路径残留为实际执行主线的空壳实现。
- [x] `./mvnw test -pl nop-sys -am` 通过，或有经裁定的最小替代验证证据。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/288-nop-sys-sys-event-reliability-refactor.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-sys --severity high` 退出码为 0。

## Deferred But Adjudicated

### 外部 MQ 集成

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划目标是先把数据库 outbox 基线补齐为可靠广播与分区串行，不涉及外部消息系统替换。
- Successor Required: no

### 旧历史事件数据迁移工具

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划先收口 live code 与 source model；历史数据迁移是否需要取决于部署环境和切换策略，应单列迁移计划。
- Successor Required: yes
- Successor Path: 如实施上线迁移，再创建独立 migration plan。

### 广播 / 游标运维后台与监控页面

- Classification: `optimization candidate`
- Why Not Blocking Closure: 可观测性增强很有价值，但不是本次建立正确性基线的前置条件。
- Successor Required: no

## Non-Blocking Follow-ups

- 如后续需要 instance-scoped broadcast、新的事件路由 SPI、或外部 MQ 对接，基于本计划落地后的 baseline 再单列 design/plan。

## Closure

Status Note: 广播事件流/游标表、普通事件 row lease/分区头处理、稳定分区路由与 focused proof 已全部落地；独立 closure audit 二次复核后确认运行时调用链连通且无剩余 plan-owned blocker，可关闭。
Completed: 2026-07-08

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit subagent `ses_0c100ef0effehDWxIPIWhQyJvs`
- Audit Session: `ses_0c100ef0effehDWxIPIWhQyJvs`
- Evidence:
  - Phase 2 publish-path proof: `nop-sys/nop-sys-dao/src/test/java/io/nop/sys/dao/message/TestSysDaoMessageService.java` covers broadcast routing, stable `partitionIndex`, and no-order-key topic-hash fallback; routing code lives in `SysDaoMessageService.saveMessage()/saveBroadcastEvent()/saveNonBroadcastEvent()`.
  - Phase 3 broadcast proof: `TestSysDaoMessageService` covers cursor success prefix, failure blocking, restart recovery, and single active lease owner; runtime path is `processBroadcastEvent -> claimBroadcastCursor -> processBroadcastCursor -> advanceBroadcastCursor`.
  - Phase 4 queue proof: `TestSysDaoMessageService` covers partition-head-only execution, failure blocking, `ConsumeLater` requeue, cross-partition independent progress, and stale-lease takeover; runtime path is `processNonBroadcastEvent -> fetchNonBroadcastEvents -> claimNonBroadcastEvents -> handleNonBroadcastProcessResult`.
  - Focused verification: `./mvnw -pl nop-sys/nop-sys-dao -Dtest=TestSysDaoMessageService test` passed with 12 tests, 0 failures.
  - Reactor verification: `./mvnw test -pl nop-sys -am` passed.
  - Doc verification: `node ai-dev/tools/check-doc-links.mjs --strict` exited 0 (repo still has historical warnings outside this plan).
  - Hollow scan: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-sys --severity high` exited 0.
  - Checklist verification: `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/288-nop-sys-sys-event-reliability-refactor.md --strict` exited 0.
  - Anti-Hollow result: audit confirmed publish entrypoints route directly into persisted normal/broadcast paths and that no `lastBroadcastEvent` in-memory mainline remains in live code.

Follow-up:

- no remaining plan-owned work
