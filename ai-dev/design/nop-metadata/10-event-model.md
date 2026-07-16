# nop-metadata 事件模型设计

> Status: final (plan 2026-07-17-0228-1 落地)
> Date: 2026-07-17
> Scope: nop-metadata 元数据变更事件
> Goal: 定义元数据变更事件模型，支撑审计日志、下游拉取与未来实时推送
> Based on: DataHub MCP/MCL、OpenMetadata ChangeEvent
> Implementation: 实体 `NopMetaModelChangedEvent` 已建模（ORM 模型 `nop-metadata.orm.xml`）

---

## 一、设计决策（D1/D2/D3 裁定）

### 1.1 事件模型定位

**决策**：事件模型用于通知其他系统元数据发生变更，支撑审计日志（变更可追溯历史）、下游拉取（变更可被查询消费），并为未来的 UI 实时推送 / 搜索索引更新留出可扩展的接入点。

**理由**：
- 审计日志需可追溯元数据变更历史（谁、何时、改了什么）。
- 下游系统（报表 / BI / 同步任务）需感知元数据变化，可通过 query 拉取事件。
- UI 实时推送（WebSocket/SSE）与搜索索引更新为 follow-up，事件模型先保证「可发布、可持久化、可查询」，不依赖推送基建。

### 1.2 持久化 + 存储形态（D1）

**决策**：事件**持久化到 DB**（新建实体 `NopMetaModelChangedEvent`），非纯内存。

- 理由：审计日志需可追溯历史；纯内存事件在重启后丢失；持久化事件天然支持 GraphQL query 查询（审计/下游拉取），不依赖推送基建。

**收口 Open Question「事件是否需要持久化到数据库？」→ 是。**

**存储形态**：per-event 行（时序追加，不覆盖）。`beforeSnapshot`/`afterSnapshot` 用 `domain="mediumtext"` + `stdDomain="json"`（快照可能超长，对齐 Manifest/Catalog/Profiling 的 JSON 列决策，**不得 json-4000**）。

**实体命名**：按模块 `NopMeta` 前缀约定，命名为 `NopMetaModelChangedEvent`。

### 1.3 发布机制 + 消费路径（D2）

**决策**：

- **发布机制（主路径）**：**直接 DB 写入事件行**——事件发布 helper 在写路径内 `saveEntity` 一条 `NopMetaModelChangedEvent`。
  - 理由：(a) 不依赖 `IMessageService` 的订阅者注册机制（nop-metadata 首版无订阅者）；(b) 直接持久化最简单可测、可被 GraphQL query 暴露；(c) 与「审计日志」目标一致。
  - `IMessageService` 可叠加（non-blocking overlay）：已 live 核实 `nop-metadata-service` pom 依赖链 `nop-metadata-service → nop-sys-dao → nop-message-core` 传递 `SysDaoMessageService`（`implements IMessageService`），可直接 `@Inject`。首版**不强制**叠加，topic 命名规范见 §6.1 留给 follow-up。
- **消费路径（首版至少一条）**：**GraphQL query 查询事件历史**（审计/下游拉取）。`NopMetaModelChangedEvent` CRUD 自动暴露后，`__findPage` 可按 `entityType`/`changeSource`/`changeTime` 过滤查询事件列表。这收口「至少一条消费路径可用」且不需要推送基建。

**收口 Open Question「是否需要支持 GraphQL 订阅？」→ 首版用 query（拉取）非 subscription（推送）；subscription 依赖推送基建，为 follow-up。**

### 1.4 发布范围 + 批量粒度（D3）

**决策**：

- **范围（首版）**：覆盖**关键元数据写路径**：
  - 关键 mutation action（持久化成功后调 helper 写主实体级事件行）：
    - `NopMetaModuleBizModel.importOrmModel`（IMPORT，主实体级）
    - `NopMetaModuleBizModel.releaseModule`（版本发布）
    - `NopMetaDataSourceBizModel.syncExternalTables`（SYNC，主实体级）
    - `NopMetaTableBizModel.createSqlTable`（UI/API）
  - 核心实体（`NopMetaModule` / `NopMetaTable`）通用 CRUD 走 **save override + delete override**：
    - `save` override 覆盖 CREATE+UPDATE；`delete` override 覆盖 DELETE（二者独立，save 不覆盖 delete）。
    - 其余实体作为 follow-up。
- **批量粒度**：批量写操作按**主实体级**记录（不逐子实体、不合并丢失）：
  - `importOrmModel` 记 1 行 Module CREATED 事件（changeSource=IMPORT）。
  - `syncExternalTables` 记 1 行 DataSource UPDATED 事件（changeSource=SYNC，表述「外部表已同步」）。
  - 子实体细粒度事件（per-row Entity/Field/Table）**deferred**（避免一次导入产生数十行事件 + 大量快照 JSON 膨胀）。
  - 同一批操作共享同一 `transactionId`（批量操作的 correlation key），支持未来关联同批的细粒度扩展。
- **单操作 transactionId**：单次 save/delete override 触发的事件生成 per-op UUID 作为 transactionId（便于未来关联同操作的多事件扩展）。
- **beforeSnapshot 获取**：
  - save override 在调 `super.save()` **前**先按 PK 加载旧状态（null=CREATE，非 null=UPDATE）区分 CREATE/UPDATE。
  - delete override 在调 `super.delete()` **前**先加载旧状态作为 beforeSnapshot。
  - 若实体不存在（DELETE 已删）则 beforeSnapshot=null + 不发事件（已删除无快照可记）。
- **事件行写入时序（关键，避免幽灵事件）**：**事件行在 super.save/super.delete 成功后写入**。即 before 快照在 super.save **前加载**，event 行在 super.save **成功后持久化**。这避免 super.save 失败/事务回滚时产生幽灵事件（事件行已写但业务写未成功）。

**收口 Open Question「批量导入时是否需要合并事件？」→ 主实体级记录（如上粒度），不逐条不合并丢失。`transactionId` 支持关联同一批的后续细粒度扩展。**

### 1.5 失败路径

- 快照序列化失败等异常**显式抛 inline ErrorCode**（不静默吞掉、不静默跳过事件发布不留痕迹）。
- 失败路径不伪造缺失快照：ENTITY_CREATED 有 afterSnapshot、ENTITY_UPDATED 有 before+after、ENTITY_DELETED 有 before（按事件类型，不伪造）。

---

## 二、事件模型

### 2.1 事件定义（实体 NopMetaModelChangedEvent）

```
NopMetaModelChangedEvent            — 元数据变更事件（时序追加行）
  ├── modelChangedEventId           — PK（seq）
  ├── eventType                     — 事件类型（dict meta/change-event-type：ENTITY_CREATED|ENTITY_UPDATED|ENTITY_DELETED）
  ├── entityType                    — 实体类型（NopMetaModule|NopMetaTable|NopMetaDataSource|...）
  ├── entityId                      — 变更实体 ID
  ├── entityName                    — 变更实体名称（便于日志）
  ├── changeSource                  — 变更来源（IMPORT|UI|API|SYNC，plain string + 文档约定，对齐 dimension-type/granularity 模式）
  ├── beforeSnapshot                — 变更前快照（mediumtext + stdDomain=json，仅 UPDATE/DELETE 时有值）
  ├── afterSnapshot                 — 变更后快照（mediumtext + stdDomain=json，仅 CREATE/UPDATE 时有值）
  ├── changedBy                     — 操作人
  ├── changeTime                    — 变更时间（mandatory）
  ├── transactionId                 — 事务/批次 ID（nullable，用于关联同批多个变更）
  └── extConfig                     — 扩展属性（json-4000）
```

索引：`(entityType, changeTime)` 时序查询 + `changeSource`。

### 2.2 事件类型

| 事件类型 | 说明 | beforeSnapshot | afterSnapshot |
|---------|------|---------------|--------------|
| `ENTITY_CREATED` | 实体创建 | null | ✅ |
| `ENTITY_UPDATED` | 实体更新 | ✅ | ✅ |
| `ENTITY_DELETED` | 实体删除 | ✅ | null |

### 2.3 变更来源

| 来源 | 说明 |
|------|------|
| `IMPORT` | ORM 模型导入（importOrmModel） |
| `UI` | 用户通过界面操作（save override 经 GraphQL UI 入口） |
| `API` | 通过 GraphQL API 操作（save override 经 API 入口） |
| `SYNC` | 数据源同步（syncExternalTables） |

> 首版 `changeSource` 为 plain string + 文档约定，dict 化为 Non-Blocking Follow-up。

---

## 三、事件发布

### 3.1 发布 helper（service 层 IoC bean）

落点：`io.nop.metadata.service.event.MetaModelChangedEventPublisher`（IoC bean，`@Inject IEntityDao<NopMetaModelChangedEvent>`）。

契约：

```
publishEvent(
    eventType,            // ENTITY_CREATED|ENTITY_UPDATED|ENTITY_DELETED
    entityType,           // NopMetaModule / NopMetaTable / ...
    entityId,
    entityName,
    changeSource,         // IMPORT|UI|API|SYNC
    before,               // Object（实体 JSON 快照源），可为 null
    after,                // Object（实体 JSON 快照源），可为 null
    transactionId,        // 批次/单操作 UUID，nullable
    context               // IServiceContext（取 changedBy）
) → 写一行 NopMetaModelChangedEvent（saveEntity 持久化）
```

- 快照序列化用 `JsonTool`（before/after 为实体对象，序列化为 JSON 字符串）。
- 失败路径（如快照序列化异常）**显式抛 inline ErrorCode**（不静默吞掉）。

### 3.2 写路径 hook 接线（按 D3 范围与粒度）

**(a) 关键 mutation action**（持久化**成功后**调 helper 写主实体级事件行，共享 transactionId）：

| Action | eventType | entityType | changeSource | 粒度 |
|--------|-----------|------------|--------------|------|
| `importOrmModel` | ENTITY_CREATED | NopMetaModule | IMPORT | 主实体级（1 行 Module CREATED） |
| `releaseModule` | ENTITY_UPDATED | NopMetaModule | UI/API | 主实体级（1 行 Module UPDATED） |
| `syncExternalTables` | ENTITY_UPDATED | NopMetaDataSource | SYNC | 主实体级（1 行 DataSource UPDATED，「外部表已同步」） |
| `createSqlTable` | ENTITY_CREATED | NopMetaTable | UI/API | 1 行 Table CREATED |

**(b) 核心实体 save override（NopMetaModule / NopMetaTable）**：

- save override 在调 `super.save()` **前**按 PK 加载 before（区分 CREATE/UPDATE），`super.save()` **成功后**调 helper 发布 ENTITY_CREATED/UPDATED。
- 注：save override 覆盖通用 CRUD 路径（UI/GraphQL/xbiz），与关键 mutation action 路径并存；mutation action 自行调 helper（不经 save override），二者独立。

**(c) 核心实体 delete override（NopMetaModule / NopMetaTable）**：

- delete override 在调 `super.delete()` **前**按 PK 加载 before，`super.delete()` **成功后**调 helper 发布 ENTITY_DELETED（save 不覆盖 delete，DELETE 走独立 override）。
- 若实体不存在（DELETE 已删）则不发事件（beforeSnapshot=null + 已删除无快照可记）。

### 3.3 事件行写入时序（避免幽灵事件）

before 快照在 `super.save/super.delete` **前加载**；event 行在 `super.save/super.delete` **成功后持久化**。避免业务写失败/事务回滚时产生幽灵事件（事件行已写但业务写未成功）。

---

## 四、事件消费

### 4.1 GraphQL query（首版消费路径，审计/下游拉取）

`NopMetaModelChangedEvent` CRUD 自动暴露后：

```graphql
query {
  NopMetaModelChangedEvent__findPage(
    query: { offset: 0, limit: 20,
             filter: { $type: "and", children: [
               { $type: "eq", name: "entityType", value: "NopMetaModule" },
               { $type: "ge", name: "changeTime", value: "2026-07-17T00:00:00Z" }
             ]},
             orderBy: [ { field: "changeTime", desc: true } ] }
  ) {
    total
    items { modelChangedEventId eventType entityType entityId entityName
            changeSource changedBy changeTime transactionId }
  }
}
```

可按 `entityType`/`changeSource`/`changeTime`/`transactionId` 过滤，支持审计日志查询与下游按批次拉取（transactionId 关联）。

### 4.2 UI 实时推送 / 搜索索引更新（follow-up）

以下消费路径为首版 **Non-Goals**（依赖独立基建）：

- **UI 实时推送（WebSocket/SSE）**——消费端推送属前端集成，首版只保证事件可发布可查询，不做实时传输通道。
- **搜索索引自动更新**——需要搜索引擎集成，首版不引入搜索引擎。
- **GraphQL Subscription（实时订阅）**——依赖平台 subscription 基建，首版用 query（拉取）非 subscription（推送）。

未来如需推送/索引更新，可在 helper 持久化事件行后叠加 `IMessageService.send`（topic 命名见 §6.1）或独立的订阅者注册，事件模型本身已为其留出扩展点。

---

## 五、Open Questions（全部已收口）

- [x] 事件是否需要持久化到数据库？→ **是**（D1，新建 NopMetaModelChangedEvent 实体，纯内存重启丢失不满足审计需求）
- [x] 是否需要支持 GraphQL 订阅？→ **首版用 query（拉取），subscription 为 follow-up**（D2，依赖推送基建）
- [x] 批量导入时是否需要合并事件？→ **主实体级记录（不逐子实体、不合并丢失），同批共享 transactionId**（D3）

---

## 六、与 nop-metadata 的集成

### 6.1 事件主题命名规范（IMessageService 叠加时，首版可选）

```
nop-metadata.{entityType}.changed
```

示例：
- `nop-metadata.NopMetaModule.changed`
- `nop-metadata.NopMetaTable.changed`
- `nop-metadata.NopMetaDataSource.changed`

首版以 DB 行为权威，topic 为可选 overlay。

### 6.2 事件存储

事件持久化于 `NopMetaModelChangedEvent` 实体（§2.1），per-event 时序追加行。事件清理/归档策略（时序行增长后的清理）为 Non-Blocking Follow-up。

---

## 七、与架构基线（§七 拒绝额外抽象层）的关系

事件模型复用既有 ORM 持久化 + GraphQL CRUD 自动暴露，**不引入**：
- 独立 EventBus 类（平台无独立 EventBus；首版直接 DB 写为主路径，IMessageService 为可选 overlay）
- 独立事件总线/可靠投递/跨进程通知（follow-up）
- 推送基建抽象层（WebSocket/SSE/subscription，follow-up）

事件发布 helper 为无状态 service 层 IoC bean（`@Inject IEntityDao`），不提升 BizModel 私有方法可见性、不自造连接、不复制持久化逻辑。
