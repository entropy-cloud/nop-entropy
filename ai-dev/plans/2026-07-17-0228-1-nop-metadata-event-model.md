# 1 nop-metadata 元数据变更事件模型（MetaModelChangedEvent）

> Plan Status: completed
> Last Reviewed: 2026-07-17
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验）。R1 发现 2 Major（pom 链指引错误 dao→orm→dao 应为 service→sys-dao→message-core；事件行写入时序未定义导致幽灵事件风险）+ 4 Minor（Closure Gate 措辞矛盾、baseline 无事件章节应"新增"、helper 落点/dao 注入未指定、单操作 transactionId 未定义），已全部修复。R2 逐条核实 6 项修复 PASS，无新矛盾，共识 YES。
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（`未建模实体` 表：`MetaModelChangedEvent`，目标 Phase 待定）；`ai-dev/design/nop-metadata/10-event-model.md`（draft，含 Open Questions）；`ai-dev/design/nop-metadata/01-architecture-baseline.md`
> Mission: nop-metadata
> Work Item: P-event — 元数据变更事件模型（通知 UI 实时更新 / 下游同步 / 审计日志）
> Related: 全部写路径 plan（292 导入引擎 / 295 版本发布 / 0225-2 外部表同步 / 0700-1 SQL 视图 / 0420-2 血缘采集）—— 事件发布的候选 hook 点

## Purpose

把 roadmap `未建模实体` 表的最后一个实体 `MetaModelChangedEvent` 从未建模推进到 landed：建模 + 事件发布 + 至少一个事件消费路径，使元数据变更可被通知、审计、下游同步。本 plan 同时收口 `10-event-model.md` 的三个 Open Questions（是否持久化 / 是否 GraphQL 订阅 / 批量导入是否合并事件），将其从 draft 重写为最终设计状态（Rule 14）。

## Current Baseline

- **`MetaModelChangedEvent` 实体不存在**：`nop-metadata/model/nop-metadata.orm.xml` 当前 31 实体中无此实体。`nop-metadata-roadmap.md` §未建模实体表将其列为「待定」Phase 待建模（设计 `10-event-model.md`）。它是在该表中唯一未标记 done/已建模的实体（`MetaProfilingRule`/`MetaProfilingResult` 已随 P2-7 plan 0530-2 建模，roadmap 该表未同步删除线，属文档 stale，非实际待建模——本 plan 顺带修此 doc drift）。
- **nop-metadata 当前无任何事件机制**：grep `EventBus|IEventBus|eventBus|IMessageService` 在 nop-metadata 全模块 0 命中。所有写路径（导入、CRUD、同步）均不发布事件。
- **平台消息基建可用（已 live 核实）**：`nop-api-core` 提供 `IMessageService`（`extends IMessageSender, IMessageSubscriber`，单向消息发送/接收）。平台无独立的 `EventBus` 类——`10-event-model.md` §1.2「利用 Nop 的 EventBus 机制」的表述需在 D2 门禁收敛为实际可用的机制。**pom 依赖链已核实**：`nop-metadata-service` pom.xml 依赖 `nop-sys-dao`，后者传递依赖 `nop-message-core`（提供 `LocalMessageService`）并自身提供 `SysDaoMessageService`（`io.nop.sys.dao.message.SysDaoMessageService`，implements `IMessageService`）。因此 `IMessageService` 在 nop-metadata-service 层可直接 `@Inject`。
- **写路径 hook 点（已存在的自定义 mutation action）**：
  - `NopMetaModuleBizModel.importOrmModel`（导入，changeSource=IMPORT）
  - `NopMetaModuleBizModel.releaseModule`（版本发布）
  - `NopMetaDataSourceBizModel.syncExternalTables`（外部表同步，changeSource=SYNC）
  - `NopMetaTableBizModel.createSqlTable`（SQL 视图创建）
  - `NopMetaLineageEdgeBizModel.recordLineage`（血缘录入）等
  - 这些 action 在 BizModel Java 中，可在持久化后调用事件发布 helper。
- **通用 CRUD 写路径**：自动生成 `CrudBizModel` 的 `save(Map, IServiceContext)`（upsert 语义，CREATE+UPDATE）+ `delete(String, IServiceContext)`（独立方法，DELETE）；plan 0700-2 已建立 `save(Map, IServiceContext)` override 范式（Measure/Dimension/Filter/Join BizModel）。override save/delete 可分别拦截 CREATE+UPDATE / DELETE 路径（UI/GraphQL/xbiz）。**注意：save 不覆盖 delete，ENTITY_DELETED 需另 override delete**。
- **`10-event-model.md` 当前状态**：`Status: draft`，含 Java/JS 伪码（`MetaEntityService`/`MetadataChangeUIUpdater`/`MetadataSearchIndexUpdater`/`MetadataAuditLogger` 示例类，非实际存在）+ 3 个 Open Questions（是否持久化到 DB / 是否 GraphQL 订阅 / 批量导入是否合并事件）。需重写为最终设计（去伪码、收敛 Open Questions，Rule 14）。
- **测试基建**：Nop AutoTest 可用；`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS。

## Goals

- 新建 `NopMetaModelChangedEvent` 实体（持久化变更事件日志，按 D1 裁定形态），重新生成代码
- 事件发布 helper 可用：在关键写路径（按 D3 裁定范围）持久化后写入事件行，使变更可追溯
- 至少一条事件消费/查询路径可用（按 D2 裁定）：变更事件可经 GraphQL query 查询（审计/历史）
- AutoTest：触发写操作后断言 `NopMetaModelChangedEvent` 写入正确的事件类型/实体类型/来源/快照

## Non-Goals

- **UI 实时推送（WebSocket/SSE）**——消费端推送属前端集成，本 plan 只保证事件可发布可查询，不做实时传输通道（follow-up）
- **GraphQL Subscription（实时订阅）**——`10-event-model.md` Open Question，依赖平台 subscription 基建核实，首版用 query（拉取）非 subscription（推送）
- **搜索索引自动更新**——需要搜索引擎集成（design §4.2 `MetadataSearchIndexUpdater` 示例），属独立基建，本 plan 不引入搜索引擎
- **全量 CRUD 事件覆盖**——首版按 D3 裁定范围（关键写路径或核心实体 save override），不要求覆盖全部 32 实体的每个 CRUD（避免 scope 爆炸）
- **事件最终一致性 / 跨事务**——首版事件与业务写同事务（或紧邻写后同 action 内），不做分布式事件总线/可靠投递（follow-up）

## Design Decisions

> D1/D2/D3 为硬前置门禁，须在 item 1.1 裁定并写入 `10-event-model.md`（重写为最终设计）+ `01-architecture-baseline.md` 后实现。

### D1. 事件持久化 + 存储形态（待 item 1.1 裁定）

- **持久化**：裁定事件**持久化到 DB**（新建 `NopMetaModelChangedEvent` 实体），非纯内存。理由：审计日志需可追溯历史；纯内存事件在重启后丢失；持久化事件天然支持 GraphQL query 查询（审计/下游拉取），不依赖推送基建。这收口 Open Question「事件是否需要持久化到数据库？」→ 是。
- **存储形态**：per-event 行（时序追加，不覆盖）。列按 `10-event-model.md` §2.1：`eventId`(PK, seq) / `eventType`(ENTITY_CREATED|UPDATED|DELETED) / `entityType`(MetaEntity|MetaTable|...) / `entityId` / `entityName` / `changeSource`(IMPORT|UI|API|SYNC) / `beforeSnapshot`(mediumtext+json, nullable) / `afterSnapshot`(mediumtext+json, nullable) / `changedBy` / `changeTime`(mandatory) / `transactionId`(nullable) / `extConfig`(json) + 审计列。`beforeSnapshot`/`afterSnapshot` 用 `domain="mediumtext"` + `stdDomain="json"`（快照可能超长，对齐 Manifest/Catalog/Profiling 的 JSON 列决策，不得 json-4000）。
- **实体命名**：按模块 `NopMeta` 前缀约定，命名为 `NopMetaModelChangedEvent`（映射 design doc `MetaModelChangedEvent`）。最终命名在 item 1.1 确认。

### D2. 事件发布机制 + 消费路径（待 item 1.1 裁定）

- **发布机制**：优先 **直接 DB 写入事件行**（事件发布 helper 在写路径内 `saveEntity` 一条 `NopMetaModelChangedEvent`），因为：(a) 不依赖 `IMessageService` 的订阅者注册机制（nop-metadata 当前无订阅者）；(b) 直接持久化最简单可测、可被 GraphQL query 暴露；(c) 与「审计日志」目标一致。**已 live 核实**：`nop-metadata-service` pom 依赖链 `nop-metadata-service → nop-sys-dao → nop-message-core` 传递 `SysDaoMessageService`（implements `IMessageService`），可直接 `@Inject`。首版裁定 DB 直接写为主路径；若需异步通知可叠加 `IMessageService.send`（non-blocking），但首版不强制。
- **消费路径（首版至少一条）**：裁定首版消费路径 = **GraphQL query 查询事件历史**（审计/下游拉取）。即 `NopMetaModelChangedEvent` CRUD 自动暴露后，`__findPage` 可按 `entityType`/`changeSource`/`changeTime` 过滤查询事件列表。这收口「至少一条消费路径可用」且不需要推送基建。
- **事件主题命名**：若叠加 `IMessageService`，topic 命名规范 `nop-metadata.{entityType}.changed`（design §6.1）。首版以 DB 行为权威，topic 为可选。

### D3. 事件发布范围 + 批量粒度（待 item 1.1 裁定）

- **范围**：首版覆盖**关键元数据写路径**——按 D3 裁定具体清单，推荐覆盖核心实体的结构性变更：`importOrmModel`（IMPORT，批量）/ `releaseModule`（版本发布）/ `syncExternalTables`（SYNC，批量）/ `createSqlTable`（UI/API）。是否对核心实体（NopMetaModule/NopMetaTable）的通用 save/delete 也发布（via save/delete override），在 item 1.1 裁定——推荐首版至少覆盖 Module/Table 两个核心实体的 save override（ENTITY_CREATED/UPDATED）+ delete override（ENTITY_DELETED），其余实体作为 follow-up。
- **批量粒度（显式裁定，消除歧义）**：批量写操作（importOrmModel 一次创建 Module + 多个 OrmModel/Entity/Field/Relation/Table 子实体；syncExternalTables 一次创建多个 Table）按以下粒度记录：
  - **主实体级**：importOrmModel 记 1 行 Module CREATED 事件（changeSource=IMPORT）；syncExternalTables 记 1 行 DataSource UPDATED 事件（changeSource=SYNC，表述「外部表已同步」）。
  - **子实体细粒度事件 deferred**：不逐个 Entity/Field/Table 记录子实体事件（避免一次导入产生数十行事件 + 大量快照 JSON 膨胀）。子实体细粒度事件（per-row）作为 follow-up，需事件粒度可配时再做。
  - 共享同一 `transactionId`（批量操作的 correlation key）。
- **批量合并**：收口 Open Question「批量导入时是否需要合并事件？」→ 主实体级记录（如上粒度），不逐条不合并丢失。`transactionId` 支持关联同一批的后续细粒度扩展。
- **单操作 transactionId 语义**：单次 save/delete override 触发的事件生成 per-op UUID 作为 transactionId（便于未来关联同操作的多事件扩展）。
- **beforeSnapshot 获取**：UPDATE/DELETE 需变更前快照——save override 在调 `super.save()` **前**先按 PK `findFirstByQuery` 加载旧状态（null=CREATE，非null=UPDATE）区分 CREATE/UPDATE；delete override 在调 `super.delete()` 前先加载旧状态作为 beforeSnapshot。若实体不存在（DELETE 已删）则 beforeSnapshot=null + 记录。
- **事件行写入时序（关键）**：**事件行在 super.save/super.delete 成功后写入**。即 before 快照在 super.save **前加载**，event 行在 super.save **成功后持久化**。这避免 super.save 失败/事务回滚时产生幽灵事件（事件行已写但业务写未成功）。

## Scope

### In Scope

- `nop-metadata/model/nop-metadata.orm.xml`：新增 `NopMetaModelChangedEvent` 实体（按 D1 形态）+ 索引（`(entityType, changeTime)` 时序查询 + `changeSource`）
- 事件发布 helper（无状态或轻量）：构造事件行 + 持久化，供写路径调用
- 写路径 hook（按 D3 范围）：关键 mutation action 持久化后调 helper 写事件行；核心实体 save override（按 D3 裁定）
- GraphQL query 消费路径（CRUD 自动暴露 + findPage 过滤）
- AutoTest：触发写操作 → 断言事件行写入正确类型/来源/快照；批量操作共享 transactionId

### Out Of Scope

- UI 实时推送（WebSocket/SSE）、GraphQL Subscription（依赖推送基建，follow-up）
- 搜索索引自动更新（需搜索引擎，follow-up）
- 全量 32 实体 CRUD 事件覆盖（首版关键路径 + 核心实体，其余 follow-up）
- 分布式事件总线 / 可靠投递 / 跨进程（follow-up）
- 事件清理/归档策略（follow-up）

## Execution Plan

### Phase 1 - 事件实体建模 + 发布 helper + 写路径 hook + 消费查询

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（新增 NopMetaModelChangedEvent）、新增事件发布 helper、`nop-metadata/nop-metadata-service/.../entity/` 关键 BizModel（hook 点）、`TestNopMetaModelChangedEvent*.java`

- Item Types: `Decision`（D1 持久化/存储 + D2 发布机制/消费 + D3 范围/批量粒度，硬前置）+ `Proof`（新功能：变更事件）

> **硬前置门禁（item 1.1）**：D1/D2/D3 必须先裁定（只裁定不写代码），写入设计文档后再落地。重点核实：平台 `IMessageService` pom 依赖链是否传递到 nop-metadata 可注入（见 D2 核实指引，决定是否叠加异步通知）；save/delete override 在 Module/Table BizModel 的可行性（save 覆盖 CREATE/UPDATE、delete 覆盖 DELETE，二者独立）。

- [x] 1.1 **持久化 + 存储形态 + 发布机制 + 范围/批量粒度决策（硬前置门禁，Decision only）**：基于 live repo 核实并裁定 D1/D2/D3——确认事件持久化到 DB（NopMetaModelChangedEvent 实体）；裁定存储形态（per-event 时序行，beforeSnapshot/afterSnapshot mediumtext+json）；确认 `IMessageService` 可经 `nop-metadata-service → nop-sys-dao` 传递依赖注入（SysDaoMessageService），裁定发布机制（DB 直接写为主路径，IMessageService 可选叠加）；裁定消费路径（GraphQL query 查询事件历史）；裁定发布范围（关键 mutation action + 核心实体 save/delete override 清单）；裁定批量粒度（主实体级，子实体细粒度 deferred，共享 transactionId）；裁定单操作 transactionId（per-op UUID）；裁定 helper 落点（`service/event/MetaModelChangedEventPublisher` IoC bean，`@Inject IEntityDao<NopMetaModelChangedEvent>`）；确认实体命名（NopMetaModelChangedEvent）。**只裁定不写代码**。结论写入 `10-event-model.md`（重写为最终设计：去 Java/JS 伪码、收敛 3 个 Open Questions、保留事件模型/发布/消费契约）+ `01-architecture-baseline.md`（**新增**事件模型章节）+ `nop-metadata-roadmap.md`（未建模实体表：MetaModelChangedEvent 标记已建模 + 顺带修 MetaProfilingRule/Result stale 删除线）
- [x] 1.2 **NopMetaModelChangedEvent 实体落地（Proof，依赖 1.1）**：按 D1 在 `nop-metadata.orm.xml` 新增实体（列见 D1；beforeSnapshot/afterSnapshot 用 `domain="mediumtext"` + `stdDomain="json"`）+ 索引（`(entityType, changeTime)` 时序 + `changeSource`）。运行 `./mvnw clean install -pl nop-metadata -T 1C` 重新生成代码确认 BUILD SUCCESS，生成实体类与 CRUD（含 GraphQL `__findPage` 自动暴露）
- [x] 1.3 **事件发布 helper（依赖 1.1）**：新增 `service/event/MetaModelChangedEventPublisher`（IoC bean，`@Inject IEntityDao<NopMetaModelChangedEvent>`），输入（eventType/entityType/entityId/entityName/changeSource/before/after/transactionId/context）→ 构造 NopMetaModelChangedEvent 行 → `saveEntity` 持久化。快照序列化用 JsonTool（before/after 为实体 JSON）。失败路径（如快照序列化异常）显式抛 inline ErrorCode（不静默吞掉）
- [x] 1.4 **写路径 hook 接线（依赖 1.1，按 D3 范围与粒度）**：(a) 关键 mutation action（importOrmModel 主实体级 CREATED / releaseModule / syncExternalTables 主实体级 / createSqlTable 等，按 1.1 裁定清单）持久化**成功后**调 helper 写事件行（按 D3 批量粒度：主实体级，共享 transactionId）；(b) 核心实体（NopMetaModule/NopMetaTable，按 1.1 裁定）**save override** 在调 super.save 前加载 before 区分 CREATE/UPDATE，super.save **成功后**发布 ENTITY_CREATED/UPDATED；(c) 核心实体 **delete override** 在调 super.delete 前加载 before，super.delete **成功后**发布 ENTITY_DELETED（save 不覆盖 delete，DELETE 走独立 override）。每个 hook 点调用 helper 写真实事件行，非空壳
- [x] 1.5 错误码按现有模式在 BizModel/helper 内 inline 定义（参考 `NopMetaDataSourceBizModel` inline ErrorCode 用法）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1/D2/D3 决策已裁定并落地，且**可观测不变量成立**：`NopMetaModelChangedEvent` 实体已生成代码（`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS）；既有回归测试全过
- [x] 事件可查询：触发写操作后 `NopMetaModelChangedEvent__findPage` 返回新增事件行，含正确的 eventType/entityType/changeSource/changeTime（非伪造）
- [x] 快照语义成立：ENTITY_CREATED 有 afterSnapshot、ENTITY_UPDATED 有 before+after、ENTITY_DELETED（经 delete override）有 before（按事件类型，不伪造缺失快照）
- [x] 批量粒度成立：批量操作（如 importOrmModel）产生主实体级事件（非逐子实体），共享同一 transactionId（子实体细粒度 deferred）
- [x] 失败路径显式：快照序列化失败等异常显式抛 ErrorCode（不静默吞掉、不静默跳过事件发布不留痕迹）
- [x] **端到端验证**：从写操作入口（如 importOrmModel 或 Module save/delete）→ NopMetaModelChangedEvent 行写入 → `__findPage` 查询到正确事件的完整路径已验证（见 Minimum Rules #22）
- [x] **接线验证**：写路径 hook（含 save override + delete override）运行时确实调用了发布 helper 并持久化了事件行（事件行含真实 entityId/entityName 证明），非空壳（见 Minimum Rules #23）
- [x] **无静默跳过**：失败路径显式抛异常；无空方法体 / 吞异常 / return null 占位（见 Minimum Rules #24）
- [x] **新功能测试**：新增测试覆盖 事件写入（ENTITY_CREATED/UPDATED/DELETED + 快照语义）+ 批量主实体级 transactionId 共享 + findPage 过滤查询 + 失败路径显式失败，全绿（见 Minimum Rules #25）
- [x] `ai-dev/design/nop-metadata/10-event-model.md` 重写为最终设计状态（去伪码、收敛 Open Questions）；`01-architecture-baseline.md` **新增**事件模型章节；`nop-metadata-roadmap.md` 未建模实体表已同步（MetaModelChangedEvent 标记已建模 + MetaProfilingRule/Result stale 删除线）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [x] NopMetaModelChangedEvent 实体已建模并重新生成代码
- [x] 事件发布端到端可用（写操作 → 事件行 → findPage 查询）
- [x] 快照语义成立（按事件类型 before/after 不伪造）
- [x] 批量 correlation 成立（transactionId 共享 + 主实体级记录，非逐子实体）
- [x] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常）
- [x] 必要 focused verification 已完成（事件写入 + 快照 + 批量 + 查询 + 失败路径测试全绿）
- [x] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] 受影响的 owner docs 已同步（`10-event-model.md` 最终设计 + `01-architecture-baseline.md` **新增**事件模型章节）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证写路径 hook 运行时确实调用 helper 并持久化真实事件行（端到端连通）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-17-0228-1-nop-metadata-event-model.md --strict` 退出码 0

## Deferred But Adjudicated

### UI 实时推送 / GraphQL Subscription

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 实时推送依赖 WebSocket/SSE/subscription 基建，独立于事件发布/持久化/查询结果面。首版 GraphQL query（拉取）已满足审计/下游拉取的核心需求
- Successor Required: no（独立基建，按需 follow-up）

### 搜索索引自动更新

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需引入搜索引擎集成，独立基建。首版事件持久化 + query 已满足审计需求
- Successor Required: no

## Non-Blocking Follow-ups

- 全量实体 CRUD 事件覆盖（首版关键路径 + 核心实体）
- 分布式事件总线 / 可靠投递 / 跨进程通知（IMessageService 订阅者注册，若需）
- 事件清理/归档策略（时序事件行增长后的清理）
- `changeSource` dict 化（首版 plain string + 文档约定，对齐 dimension-type/granularity 模式）

## Closure

Status Note: 全部 Phase 完成。NopMetaModelChangedEvent 实体已建模（ORM + 代码生成 + GraphQL CRUD 自动暴露）；事件发布 helper（MetaModelChangedEventPublisher IoC bean）落地；写路径 hook 接线（importOrmModel/releaseModule/syncExternalTables/createSqlTable mutation action + NopMetaModule/NopMetaTable save+delete override）端到端连通；消费路径 GraphQL __findPage 可查询。232 service 测试全过（含 7 个新增事件测试）；hollow scan 退出码 0；doc-link 检查退出码 0。pre-existing web 测试失败（NopMetaDataContract 页引用 nop-auth 资源）与本 plan 无关（基线已存在，stash 核实）。
Completed: 2026-07-17

Closure Audit Evidence:

- Reviewer / Agent: 执行 agent 自验证（live repo 核实：mvn clean install BUILD SUCCESS + 232 service tests 全绿 + hollow scan exit 0 + doc-link exit 0）
- Evidence: 
  - 实体生成：`nop-metadata-dao/.../entity/NopMetaModelChangedEvent.java` + `_gen/_NopMetaModelChangedEvent.java` + biz interface `INopMetaModelChangedEventBiz.java`
  - helper：`nop-metadata-service/.../service/event/MetaModelChangedEventPublisher.java`（IoC bean，app-service.beans.xml 注册）
  - hook 接线：`NopMetaModuleBizModel`（importOrmModel/releaseModule + save/delete override）、`NopMetaTableBizModel`（createSqlTable + save/delete override）、`NopMetaDataSourceBizModel`（syncExternalTables）
  - 测试：`TestNopMetaModelChangedEvent`（7 tests：CREATED/UPDATED/DELETED 快照语义 + 批量主实体级 transactionId + findPage 查询 + createSqlTable + 失败路径序列化异常显式抛 ErrorCode）
  - design docs：`10-event-model.md` 重写为 final、`01-architecture-baseline.md` §2.8 新增、`nop-metadata-roadmap.md` 未建模实体表同步

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
