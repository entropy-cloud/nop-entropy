# Apache Atlas 深度分析报告

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 Apache Atlas 的架构设计，为 nop-metadata 提供设计参考
> Last Updated: 2026-07-15 (源码深度分析)

## Context

Apache Atlas 是 Apache Hadoop 生态系统的元数据治理框架，专注于 Hadoop 生态系统的元数据管理和治理。本文档基于源码深度分析，详细拆解其类型系统、图数据库抽象、Hook 机制和血缘模型，为 nop-metadata 的设计提供参考。

## 核心架构

### 1. 组件架构

```
┌─────────────────────────────────────────────────────┐
│                  Atlas UI                            │
│               (Angular Frontend)                     │
└──────────────────────┬──────────────────────────────┘
                       │ REST API (JAX-RS)
┌──────────────────────▼──────────────────────────────┐
│              Atlas Server                            │
│  ┌─────────────────┐  ┌──────────────────────────┐  │
│  │ EntityREST      │  │ TypesREST                │  │
│  │ LineageREST     │  │ ClassificationREST       │  │
│  └────────┬────────┘  └────────────┬─────────────┘  │
│           │                        │                 │
│  ┌────────▼────────────────────────▼─────────────┐  │
│  │          AtlasEntityStore / AtlasTypeDefStore  │  │
│  └────────┬────────────────────────┬─────────────┘  │
│           │                        │                 │
│  ┌────────▼────────────────────────▼─────────────┐  │
│  │        AtlasTypeRegistry (运行时类型注册表)     │  │
│  └────────┬────────────────────────┬─────────────┘  │
│           │                        │                 │
│  ┌────────▼──────┐  ┌──────────────▼────────────┐  │
│  │ Graph Backend  │  │ Index Backend (Solr/ES)   │  │
│  │ (JanusGraph)   │  │                           │  │
│  └───────────────┘  └───────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 2. 存储层

- **HBase/JanusGraph**: 图数据库存储实体和关系
- **Solr/Elasticsearch**: 搜索索引，支持全文搜索
- **Kafka**: 事件总线，实现实时元数据同步

## 核心设计模式

### 模式 1：多阶段引用解析类型系统

**关键文件:**
- `intg/src/main/java/org/apache/atlas/type/AtlasType.java`
- `intg/src/main/java/org/apache/atlas/type/AtlasEntityType.java`
- `intg/src/main/java/org/apache/atlas/type/AtlasTypeRegistry.java`

**设计结构:**

Atlas 使用三阶段引用解析系统处理类型定义之间的循环引用问题：

```java
// AtlasTransientTypeRegistry.java
for (AtlasType type : registryData.allTypes.getAllTypes()) {
    type.resolveReferences(this);       // Phase 1: 构建前向依赖图
}
for (AtlasType type : registryData.allTypes.getAllTypes()) {
    type.resolveReferencesPhase2(this);  // Phase 2: 构建反向依赖图
}
for (AtlasType type : registryData.allTypes.getAllTypes()) {
    type.resolveReferencesPhase3(this);  // Phase 3: 交叉验证
}
```

**各阶段职责:**
- **Phase 1**: 构建所有 `allSuperTypes`、收集所有属性
- **Phase 2**: 构建 `subTypes`、`allSubTypes`（反向链接）
- **Phase 3**: 交叉验证（如验证 RelationshipDef 是否存在），构建派生数据

**设计优势:**
- 解决了类型 A 引用类型 B，类型 B 又引用类型 C 的鸡生蛋问题
- 单遍解析无法处理前向引用
- Phase 3 结束后所有集合包装为 `Collections.unmodifiable*`，确保线程安全

---

### 模式 2：类型注册表 + 乐观锁（快照隔离）

**关键文件:**
- `intg/src/main/java/org/apache/atlas/type/AtlasTypeRegistry.java`

**设计结构:**

```java
public AtlasTransientTypeRegistry lockTypeRegistryForUpdate() {
    // 获取 ReentrantLock，15 秒超时
    // 创建整个注册表的 SNAPSHOT 副本
    return new AtlasTransientTypeRegistry(typeRegistry);
}

public void releaseTypeRegistryForUpdate(AtlasTransientTypeRegistry ttr, boolean commitUpdates) {
    if (commitUpdates) {
        copyIndexNameFromCurrent(ttr.getAllEntityTypes());
        typeRegistry.registryData = ttr.registryData;  // 原子交换
    }
    unlock();
}
```

**设计优势:**
- **Copy-on-Write 模式**: 所有类型变更（增删改）在瞬态副本上执行，不影响在线读取
- **原子提交**: commit 时整个 `registryData` 引用原子交换
- **读写不互斥**: 读者永远不阻塞，只有一个写者
- `RegistryData` 使用 `ConcurrentHashMap` 存储各类型分类的缓存

---

### 模式 3：实体类型继承层次

**关键文件:**
- `intg/src/main/java/org/apache/atlas/type/AtlasEntityType.java`

**类型类层次:**
```
AtlasType (abstract)
  AtlasStructType
    AtlasEntityType         -- 添加 superTypes, subTypes, relationshipAttributes
    AtlasClassificationType -- 添加 entityTypes 限制
    AtlasBusinessMetadataType
  AtlasEnumType
  AtlasArrayType
  AtlasMapType
  AtlasBuiltInTypes.*      -- AtlasStringType, AtlasIntType, ...
```

**继承字段:**
```java
private List<AtlasEntityType>   superTypes;           // 直接父类型
private Set<String>             allSuperTypes;         // 所有祖先（传递闭包）
private Set<String>             subTypes;              // 直接子类型
private Set<String>             allSubTypes;           // 所有后代（传递闭包）
private Set<String>             typeAndAllSubTypes;    // 自身 + 所有后代
private Set<String>             typeAndAllSuperTypes;  // 自身 + 所有祖先
```

**设计优势:**
- **预计算物化视图**: 允许 O(1) 的 `isSuperTypeOf()`、`isSubTypeOf()` 判断
- **双向链接**: Phase 1 构建祖先列表，Phase 2 注册为每个祖先的子类型
- **不可变性**: Phase 3 结束后所有集合不可变，支持并发读取
- **ENTITY_ROOT 单例**: 作为层次结构根节点，定义系统级属性

---

### 模式 4：图数据库抽象层

**关键文件:**
- `graphdb/api/src/main/java/org/apache/atlas/repository/graphdb/AtlasGraph.java`
- `graphdb/api/src/main/java/org/apache/atlas/repository/graphdb/AtlasVertex.java`
- `graphdb/api/src/main/java/org/apache/atlas/repository/graphdb/AtlasEdge.java`

**抽象接口:**
```
AtlasElement (接口)
  AtlasVertex<V,E>  -- getEdges(), addProperty(), query()
  AtlasEdge<V,E>    -- getInVertex(), getOutVertex(), getLabel()

AtlasGraph<V,E> (接口) -- addVertex(), addEdge(), query(), V(), E()
  AtlasGraphManagement     -- 索引创建、Schema 管理
  AtlasGraphQuery<V,E>     -- 属性查询
  AtlasGraphTraversal      -- Gremlin 遍历
  AtlasIndexQuery          -- Elasticsearch 全文查询
```

**设计优势:**
- **泛型类型参数 `<V, E>`**: JanusGraph、JanusGraph-RDBMS 等后端提供强类型实现
- **懒加载身份**: `getVertex()` 在顶点不存在时返回非 null 对象，调用者检查 `.exists()` 而非 null 检查
- **Gremlin 集成**: `executeGremlinScript()`、类型感知查询生成
- **属性模型**: 单值、多值（Set）、List、JSON 属性统一在单个元素上

---

### 模式 5：Hive Hook 命令分派

**关键文件:**
- `addons/hive-bridge/src/main/java/org/apache/atlas/hive/hook/HiveHook.java`
- `addons/hive-bridge/src/main/java/org/apache/atlas/hive/hook/events/BaseHiveEvent.java`

**设计结构:**

```java
public class HiveHook extends AtlasHook implements ExecuteWithHookContext {
    public void run(HookContext hookContext) {
        HiveOperation oper = OPERATION_MAP.get(hookContext.getOperationName());
        BaseHiveEvent event = null;

        switch (oper) {
            case CREATEDATABASE:        event = new CreateDatabase(context); break;
            case CREATETABLE_AS_SELECT:
            case QUERY:                 event = new CreateHiveProcess(context); break;
            case ALTERTABLE_RENAME:     event = new AlterTableRename(context); break;
            // ... 15+ 种操作
        }

        if (event != null) {
            super.notifyEntities(
                ActiveEntityFilter.apply(event.getNotificationMessages()), ugi);
        }
    }
}
```

**设计优势:**
- **Strategy + Factory 模式**: 每个 `HiveOperation` 映射到具体的 `BaseHiveEvent` 子类
- **Template Method**: 抽象 `getNotificationMessages()` 由各子类实现
- **实体通知管道**: 事件产生 `HookNotification` 对象，经 `ActiveEntityFilter` 过滤后发送到 Atlas
- **缓存层**: `HiveHookObjectNamesCache` 缓存已知数据库和表，减少 Hive metastore 查询
- **模式过滤**: 可配置正则表达式忽略/裁剪特定表

---

### 模式 6：分类/标签挂载模型

**关键文件:**
- `intg/src/main/java/org/apache/atlas/type/AtlasClassificationType.java`
- `webapp/src/main/java/org/apache/atlas/web/rest/EntityREST.java`

**设计结构:**

分类是**独立的类型实体**，挂载到实体类型上并带有约束：

```java
public class AtlasClassificationType extends AtlasStructType {
    private Set<String> entityTypes;  // 此分类可挂载到哪些实体类型
}
```

REST API 端点：
```
GET  /v2/entity/guid/{guid}/classifications           -- 列出所有分类
POST /v2/entity/guid/{guid}/classifications            -- 添加分类
POST /v2/entity/uniqueAttribute/type/{typeName}/classifications  -- 按唯一属性添加
```

**设计优势:**
- **多对多带元数据**: 分类不仅是标签，还携带自己的属性（如 PII 分类可能有 "sensitivity" 属性）
- **实体类型约束带继承**: 分类可限制到特定实体类型，子类型自动继承允许关系
- **时间边界**: 分类支持有效期时段，实现临时标签

---

### 模式 7：Process-Entity 血缘模型

**关键文件:**
- `addons/hive-bridge/src/main/java/org/apache/atlas/hive/hook/events/CreateHiveProcess.java`
- `webapp/src/main/java/org/apache/atlas/web/rest/LineageREST.java`

**血缘关系模型:**

```
[Dataset] --dataset_process_inputs--> [Process] --process_dataset_outputs--> [Dataset]
```

```java
public static final String RELATIONSHIP_DATASET_PROCESS_INPUTS  = "dataset_process_inputs";
public static final String RELATIONSHIP_PROCESS_DATASET_OUTPUTS = "process_dataset_outputs";
```

REST API：
```
GET /v2/lineage/{guid}?direction=BOTH&depth=3
POST /v2/lineage/{guid}  -- 带约束的按需血缘查询
```

**设计优势:**
- **Process 中心血缘**: 每个转换（CTAS、INSERT、JOIN）创建 Process 实体，显式记录输入/输出数据集
- **方向参数**: `BOTH`、`INPUT`、`OUTPUT` 支持定向血缘查询
- **深度限制**: `depth` 参数防止无限循环，控制查询成本
- **按需血缘**: POST 请求可对大型图进行约束过滤

---

### 模式 8：REST API 类型验证 + 性能追踪

**关键文件:**
- `webapp/src/main/java/org/apache/atlas/web/rest/EntityREST.java`

**统一模式:**
```java
@GET
@Path("/guid/{guid}")
@Timed
public AtlasEntityWithExtInfo getById(@PathParam("guid") String guid, ...) throws AtlasBaseException {
    Servlets.validateQueryParamLength("guid", guid);           // 1. 输入验证

    AtlasPerfTracer perf = null;
    try {
        if (AtlasPerfTracer.isPerfTraceEnabled(PERF_LOG)) {   // 2. 性能追踪
            perf = AtlasPerfTracer.getPerfTracer(PERF_LOG, "EntityREST.getById(" + guid + ")");
        }
        return entitiesStore.getById(guid, minExtInfo);         // 3. 委托给 Store
    } finally {
        AtlasPerfTracer.log(perf);                              // 4. 记录性能
    }
}
```

**设计优势:**
- **瘦 REST 层**: REST 资源是纯调度器，验证输入、委托给 Store、处理性能日志，不含业务逻辑
- **@Timed 注解**: Dropwizard metrics 自动请求计时
- **双访问模式**: 实体可通过 GUID 或 类型+唯一属性 访问
- **分类端点深嵌套**: 在实体路径下深层嵌套，反映层级所有权模型

## 类型系统详解

### 类型定义模型

```java
public class AtlasEntityDef extends AtlasStructDef {
    private Set<String> superTypes;                          // 父类型集合
    private Set<String> subTypes;                            // 子类型（只读，自动计算）
    private List<AtlasRelationshipAttributeDef> relationshipAttributeDefs;  // 关系属性
    private Map<String, List<AtlasAttributeDef>> businessAttributeDefs;     // 业务属性
    private List<AtlasAttributeDef> attributeDefOverrides;   // 属性覆盖
}
```

### 属性定义

```java
public class AtlasAttributeDef {
    private String name;
    private String typeName;          // 类型名（可嵌套）
    private boolean isOptional;
    private Cardinality cardinality;  // SINGLE, LIST, SET
    private DefaultValues defaultValue;
    private Constraints constraints;  // minLength, maxLength, minimum, maximum
    private List<AtlasAttributeDef> nestedAttributes;  // 嵌套属性
}
```

## 对 nop-metadata 的启示

### 可借鉴的设计

1. **三阶段类型解析**: 解决循环引用问题，nop 可用类似机制处理 ORM 模型间依赖
2. **Copy-on-Write 类型注册**: 读写不互斥的运行时类型管理
3. **预计算继承闭包**: O(1) 的类型层次查询
4. **图数据库抽象**: 支持多种后端的统一图查询接口
5. **Hook 命令分派**: Strategy + Factory + Template Method 组合
6. **分类带属性的挂载模型**: 标签不只是字符串，可携带结构化元数据
7. **Process 中心血缘**: 血缘通过中间 Process 实体显式建模

### 需要改进的地方

1. **Hadoop 依赖**: 强依赖 Hadoop 生态系统，nop 应解耦
2. **技术栈老旧**: 使用较旧的技术栈
3. **图数据库依赖**: JanusGraph 部署复杂，nop 可用关系型数据库替代

## Open Questions

- [ ] nop 是否需要运行时类型注册表？
- [ ] 如何在 Nop ORM 上实现 Copy-on-Write 类型管理？
- [ ] 是否需要图数据库抽象层？

## References

- [Apache Atlas GitHub](https://github.com/apache/atlas)
- [Atlas Type System](https://atlas.apache.org/docs/1.2.0/TypeSystem.html)
- [Atlas Hooks](https://atlas.apache.org/docs/1.2.0/Hooks.html)
- 源码: `intg/src/main/java/org/apache/atlas/type/`
- 源码: `graphdb/api/src/main/java/org/apache/atlas/repository/graphdb/`
- 源码: `addons/hive-bridge/src/main/java/org/apache/atlas/hive/hook/`