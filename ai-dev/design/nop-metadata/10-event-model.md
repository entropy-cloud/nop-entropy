# nop-metadata 事件模型设计

> Status: draft
> Date: 2026-07-15
> Scope: nop-metadata 元数据变更事件
> Goal: 定义元数据变更事件模型，支撑 UI 实时更新和下游同步
> Based on: DataHub MCP/MCL、OpenMetadata ChangeEvent
> Implementation Phase: 未定 (尚未建模: MetaModelChangedEvent)

---

## 一、设计决策

### 1.1 事件模型定位

**决策**: 事件模型用于通知其他系统元数据发生变更，支撑 UI 实时更新和下游同步。

**理由**:
- UI 需要实时更新元数据变更
- 下游系统（如报表、BI）需要感知元数据变化
- 审计日志需要记录元数据变更历史

### 1.2 事件实现方式

**决策**: 利用 Nop 的 EventBus 机制，在元数据写入后发布事件。

**理由**:
- 复用 Nop 平台现有的 EventBus 基础设施
- 异步发布，不阻塞主流程
- 支持多个监听器

---

## 二、事件模型

### 2.1 事件定义

```
MetaModelChangedEvent            — 元数据变更事件
  ├── eventId                    — 事件 ID（UUID）
  ├── eventType                  — 事件类型
  │   └── "ENTITY_CREATED" | "ENTITY_UPDATED" | "ENTITY_DELETED"
  ├── entityType                 — 实体类型
  │   └── "MetaEntity" | "MetaTable" | "MetaOrmModel" | "MetaModule" | ...
  ├── entityId                   — 变更实体 ID
  ├── entityName                 — 变更实体名称（便于日志）
  ├── changeSource               — 变更来源
  │   └── "IMPORT" | "UI" | "API" | "SYNC"
  ├── beforeSnapshot             — 变更前快照（JSON，仅 UPDATE/DELETE 时有值）
  ├── afterSnapshot              — 变更后快照（JSON，仅 CREATE/UPDATE 时有值）
  ├── changedBy                  — 操作人
  ├── changeTime                 — 变更时间
  ├── transactionId              — 事务 ID（可选，用于关联多个变更）
  └── extConfig                  — 扩展属性
```

### 2.2 事件类型

| 事件类型 | 说明 | beforeSnapshot | afterSnapshot |
|---------|------|---------------|--------------|
| `ENTITY_CREATED` | 实体创建 | null | ✅ |
| `ENTITY_UPDATED` | 实体更新 | ✅ | ✅ |
| `ENTITY_DELETED` | 实体删除 | ✅ | null |

### 2.3 变更来源

| 来源 | 说明 |
|------|------|
| `IMPORT` | ORM 模型导入 |
| `UI` | 用户通过界面操作 |
| `API` | 通过 GraphQL API 操作 |
| `SYNC` | 数据源同步（如 JdbcModelDiscoverer） |

---

## 三、事件发布

### 3.1 发布时机

```java
public class MetaEntityService {
    
    @Inject
    private EventBus eventBus;
    
    public MetaEntity createEntity(MetaEntity entity) {
        // 1. 保存实体
        MetaEntity saved = entityRepository.save(entity);
        
        // 2. 发布事件
        MetaModelChangedEvent event = MetaModelChangedEvent.builder()
            .eventType("ENTITY_CREATED")
            .entityType("MetaEntity")
            .entityId(saved.getId())
            .entityName(saved.getEntityName())
            .changeSource("API")
            .afterSnapshot(toJson(saved))
            .changedBy(getCurrentUser())
            .changeTime(Instant.now())
            .build();
        
        eventBus.publish("nop-metadata.entity.changed", event);
        
        return saved;
    }
    
    public MetaEntity updateEntity(MetaEntity entity) {
        // 1. 获取变更前状态
        MetaEntity before = entityRepository.findById(entity.getId());
        
        // 2. 保存实体
        MetaEntity saved = entityRepository.save(entity);
        
        // 3. 发布事件
        MetaModelChangedEvent event = MetaModelChangedEvent.builder()
            .eventType("ENTITY_UPDATED")
            .entityType("MetaEntity")
            .entityId(saved.getId())
            .entityName(saved.getEntityName())
            .changeSource("API")
            .beforeSnapshot(toJson(before))
            .afterSnapshot(toJson(saved))
            .changedBy(getCurrentUser())
            .changeTime(Instant.now())
            .build();
        
        eventBus.publish("nop-metadata.entity.changed", event);
        
        return saved;
    }
}
```

### 3.2 批量导入事件

```java
public class OrmModelImporter {
    
    @Inject
    private EventBus eventBus;
    
    public void importModule(String modulePath) {
        String transactionId = UUID.randomUUID().toString();
        
        // 1. 导入模块
        List<MetaEntity> entities = doImport(modulePath);
        
        // 2. 批量发布事件
        for (MetaEntity entity : entities) {
            MetaModelChangedEvent event = MetaModelChangedEvent.builder()
                .eventType("ENTITY_CREATED")
                .entityType("MetaEntity")
                .entityId(entity.getId())
                .entityName(entity.getEntityName())
                .changeSource("IMPORT")
                .afterSnapshot(toJson(entity))
                .changedBy("system")
                .changeTime(Instant.now())
                .transactionId(transactionId)
                .build();
            
            eventBus.publish("nop-metadata.entity.changed", event);
        }
    }
}
```

---

## 四、事件消费

### 4.1 UI 实时更新

```java
@Component
public class MetadataChangeUIUpdater {
    
    @EventListener(topic = "nop-metadata.entity.changed")
    public void onEntityChanged(MetaModelChangedEvent event) {
        // 通知前端刷新元数据列表
        // 通过 WebSocket 或 Server-Sent Events
    }
}
```

### 4.2 索引更新

```java
@Component
public class MetadataSearchIndexUpdater {
    
    @Inject
    private ISearchEngine searchEngine;
    
    @EventListener(topic = "nop-metadata.entity.changed")
    public void onEntityChanged(MetaModelChangedEvent event) {
        switch (event.getEventType()) {
            case "ENTITY_CREATED":
            case "ENTITY_UPDATED":
                // 更新搜索索引
                SearchableDoc doc = buildSearchableDoc(event.getAfterSnapshot());
                searchEngine.addDoc("MetaEntity", doc);
                break;
            case "ENTITY_DELETED":
                // 删除搜索索引
                searchEngine.removeDoc("MetaEntity", event.getEntityId());
                break;
        }
    }
}
```

### 4.3 审计日志

```java
@Component
public class MetadataAuditLogger {
    
    @EventListener(topic = "nop-metadata.entity.changed")
    public void onEntityChanged(MetaModelChangedEvent event) {
        // 记录审计日志
        auditLog.info("Metadata changed: type={}, id={}, source={}, user={}",
            event.getEntityType(),
            event.getEntityId(),
            event.getChangeSource(),
            event.getChangedBy());
    }
}
```

---

## 五、GraphQL 订阅（可选）

### 5.1 Subscription 定义

```graphql
type Subscription {
  metadataChanged(entityType: String): MetaModelChangedEvent!
}
```

### 5.2 前端订阅

```javascript
// 前端订阅元数据变更
const SUBSCRIPTION = gql`
  subscription OnMetadataChanged($entityType: String) {
    metadataChanged(entityType: $entityType) {
      eventType
      entityType
      entityId
      entityName
      changeTime
    }
  }
`;

// 订阅
const { data } = useSubscription(SUBSCRIPTION, {
  variables: { entityType: "MetaEntity" }
});
```

---

## 六、与 nop-metadata 的集成

### 6.1 事件主题命名规范

```
nop-metadata.{entityType}.changed
```

示例：
- `nop-metadata.MetaEntity.changed`
- `nop-metadata.MetaTable.changed`
- `nop-metadata.MetaModule.changed`

### 6.2 事件存储（可选）

如果需要持久化事件历史：

```
MetaModelChangedEventLog         — 事件日志（可选）
  ├── eventId                    — 事件 ID
  ├── eventType                  — 事件类型
  ├── entityType                 — 实体类型
  ├── entityId                   — 实体 ID
  ├── changeSource               — 变更来源
  ├── changedBy                  — 操作人
  ├── changeTime                 — 变更时间
  ├── beforeSnapshot             — 变更前快照（CLOB）
  ├── afterSnapshot              — 变更后快照（CLOB）
  └── extConfig
```

---

## Open Questions

- [ ] 事件是否需要持久化到数据库？
- [ ] 是否需要支持 GraphQL 订阅？
- [ ] 批量导入时是否需要合并事件？
