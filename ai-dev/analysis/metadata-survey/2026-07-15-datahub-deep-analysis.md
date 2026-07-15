# DataHub 深度分析报告

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 DataHub 的架构设计，为 nop-metadata 提供设计参考
> Last Updated: 2026-07-15 (源码深度分析)

## Context

DataHub 是 LinkedIn 开源的数据目录平台，采用流式架构和 schema-first 方法管理元数据。本文档基于源码深度分析，详细拆解其声明式实体注册表、PDL 注解驱动的索引生成、MCP/MCL 事件模型、Elasticsearch 图存储、时序方面和 GraphQL DataLoader 模式，为 nop-metadata 的设计提供参考。

## 核心架构

### 1. 组件架构

```
┌─────────────────────────────────────────────────────┐
│                    DataHub UI                        │
│                  (React Frontend)                     │
└──────────────────────┬──────────────────────────────┘
                       │ GraphQL API (4100+ 行 GmsGraphQLEngine)
┌──────────────────────▼──────────────────────────────┐
│              Metadata Service (GMS)                  │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ EntityClient │  │ GraphService │  │ Search    │ │
│  │              │  │ (ES-backed)  │  │ Service   │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         └─────────────────┼────────────────┘       │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        EntityRegistry (YAML 驱动)               │ │
│  │  ~45 实体类型, 每个带 aspects 白名单             │ │
│  └────────────────────────┬───────────────────────┘ │
└───────────────────────────┼─────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
┌───────▼──────┐   ┌───────▼──────┐   ┌───────▼──────┐
│    MySQL      │   │   Kafka      │   │Elasticsearch  │
│ (Aspect 存储) │   │ (MCP/MCL)    │   │ (搜索+图+时序)│
└──────────────┘   └──────────────┘   └──────────────┘
```

### 2. 存储层

- **MySQL**: 存储实体 Aspect 的版本化快照
- **Elasticsearch**: 三合一 — 搜索索引 + 图边存储 + 时序索引
- **Kafka**: MCP/MCL 事件总线

## 核心设计模式

### 模式 1：声明式实体注册表 (entity-registry.yml)

**关键文件:**
- `metadata-models/src/main/resources/entity-registry.yml` (874 行)

**设计结构:**

```yaml
entities:
  - name: dataset          # 实体名称
    category: core         # core | internal
    keyAspect: datasetKey  # 唯一标识实体的 Aspect
    searchGroup: primary   # primary | timeseries | schemaField | query
    aspects:               # 允许的 Aspect 名称白名单
      - schemaMetadata
      - ownership
      - globalTags
      - datasetProfile
      - datasetUsageStatistics
      - ...
```

**插件系统 (lines 846-874):**
```yaml
aspectPayloadValidators:   # Aspect 载荷验证器
mcpSideEffects:           # MCP 事件副作用
mcpObservers:             # MCP 事件观察者
mutationHooks:            # 变更钩子
```

**设计优势:**
- **声明式模型定义**: 所有元数据在单一 YAML 文件中声明，系统自描述
- **搜索分组**: 实体按 `searchGroup` 分区，决定 Elasticsearch 索引策略
- **~45 实体类型**: dataset, dashboard, chart, dataFlow, dataJob, mlModel, corpuser, corpGroup, domain, container, tag, glossaryTerm, dataProduct, incident, query, metric, semanticModel 等

---

### 模式 2：PDL 注解驱动的索引/关系生成

**关键文件:**
- `metadata-models/src/main/pegasus/com/linkedin/common/Status.pdl`
- `metadata-models/src/main/pegasus/com/linkedin/common/GlobalTags.pdl`
- `metadata-models/src/main/pegasus/com/linkedin/common/Deprecation.pdl`

**PDL 注解示例:**

```pdl
// Status.pdl - 方面定义
@Aspect = { "name": "status" }
record Status {
  @Searchable = { "fieldType": "BOOLEAN" }
  removed: boolean = false

  @Searchable = { "fieldType": "KEYWORD", "queryByDefault": false }
  lifecycleStage: optional Urn
}
```

```pdl
// GlobalTags.pdl - 关系 + 搜索注解
@Aspect = { "name": "globalTags" }
record GlobalTags {
  @Relationship = { "/*/tag": { "name": "TaggedWith", "entityTypes": ["tag"] } }
  @Searchable = {
    "/*/tag": {
      "fieldName": "tags", "fieldType": "URN", "boostScore": 0.5,
      "addToFilters": true, "hasValuesFieldName": "hasTags",
      "filterNameOverride": "Tagged With", "searchTier": 2
    }
  }
  tags: array[TagAssociation]
}
```

**注解类型:**

| 注解 | 用途 |
|------|------|
| `@Aspect` | 声明记录为元数据 Aspect |
| `@Searchable` | 驱动 Elasticsearch 索引映射生成 |
| `@Relationship` | 声明图边（关系） |
| `@TimeseriesField` | 标记时序字段 |
| `@TimeseriesFieldCollection` | 标记时序集合字段 |

**Searchable fieldType:**
`BOOLEAN`, `KEYWORD`, `URN`, `COUNT`, `DATETIME`, `TEXT`, `MAP_ARRAY`

**设计优势:**
- **注解即索引**: 在 PDL 文件中添加 `@Searchable` 注解，下次重建索引自动拾取新搜索字段
- **注解即关系**: `@Relationship` 注解自动创建图边，无需单独的关系管理代码
- **权重控制**: `boostScore`、`searchTier` 控制搜索排名
- **过滤器生成**: `addToFilters` + `filterNameOverride` 自动生成 UI 过滤器

---

### 模式 3：Proposal-Log 事件模型 (MCP → MCL)

**关键文件:**
- `metadata-models/src/main/pegasus/com/linkedin/mxe/MetadataChangeProposal.pdl`
- `metadata-models/src/main/pegasus/com/linkedin/mxe/MetadataChangeLog.pdl`

**事件流:**
```
MetadataChangeProposal (MCP)  ──Kafka──>  GMS 写入  ──>  MetadataChangeLog (MCL)
     (写入请求)                            (验证+持久化)       (已提交的变更日志)
```

**MCP 结构:**
```pdl
record MetadataChangeProposal {
  entityType: string
  entityUrn: optional Urn
  entityKeyAspect: optional GenericAspect
  changeType: ChangeType           // UPSERT, CREATE, DELETE
  aspectName: optional string
  aspect: optional GenericAspect
  systemMetadata: optional SystemMetadata
}
```

**MCL 结构:**
```pdl
record MetadataChangeLog includes MetadataChangeProposal {
  previousAspectValue: optional GenericAspect    // 前一值（审计）
  previousSystemMetadata: optional SystemMetadata
  created: optional AuditStamp
}
```

**GenericAspect 信封:**
```pdl
record GenericAspect {
  contentType: string    // e.g., "application/json"
  value: bytes           // 序列化的 Aspect 载荷
}
```

**SystemMetadata:**
```pdl
record SystemMetadata {
  runId: optional string
  pipelineName: optional string
  registryName: optional string
  registryVersion: optional string
  version: optional long
  schemaVersion: optional long
  aspectCreated: optional long
  aspectModified: optional long
}
```

**设计优势:**
- **两阶段事件**: MCP 是写入请求（可验证），MCL 是已提交日志（仅已提交变更）
- **GenericAspect 信封**: Aspect 载荷以 bytes + content type 序列化，支持 Schema 演进
- **SystemMetadata 溯源**: 追踪元数据来源（runId、pipelineName、registryVersion）
- **失败事件**: `FailedMetadataChangeProposal` 记录验证失败的 MCP

---

### 模式 4：Elasticsearch 图存储（无图数据库）

**关键文件:**
- `metadata-io/src/main/java/com/linkedin/metadata/graph/elastic/ElasticSearchGraphService.java`

**边文档模型:**
```json
{
  "source": { "urn": "urn:li:dataset:...", "entityType": "dataset" },
  "destination": { "urn": "urn:li:dashboard:...", "entityType": "dashboard" },
  "relationshipType": "DownstreamOf",
  "createdOn": "...",
  "createdActor": "...",
  "updatedOn": "...",
  "properties": {}
}
```

**PDL 边定义:**
```pdl
record EntityRelationship {
  created: optional AuditStamp
  entity: Urn
  type: string     // 自由形式: DownstreamOf, OwnedBy, TaggedWith, IsPartOf
}
```

**设计优势:**
- **图存储在 Elasticsearch**: 边作为文档存储在专用 `graph_service_v1` 索引，无需 Neo4j
- **自由形式关系类型**: `type` 字段是自由字符串，无需 Schema 变更即可扩展
- **双向查询**: 嵌套 `source`/`destination` 对象支持双向查询
- **LineageRegistry**: 配置哪些关系类型用于血缘遍历

---

### 模式 5：时序 Aspect（独立索引策略）

**关键文件:**
- `metadata-models/src/main/pegasus/com/linkedin/dataset/DatasetProfile.pdl`
- `metadata-models/src/main/pegasus/com/linkedin/dataset/DatasetUsageStatistics.pdl`

**时序 Aspect 示例:**
```pdl
@Aspect = { "name": "datasetProfile", "type": "timeseries" }
record DatasetProfile includes TimeseriesAspectBase {
  @Searchable = { "fieldType": "COUNT", "hasValuesFieldName": "hasRowCount" }
  rowCount: optional long

  @Searchable = { "fieldType": "COUNT", "hasValuesFieldName": "hasColumnCount" }
  columnCount: optional long

  fieldProfiles: optional array[DatasetFieldProfile]
}
```

```pdl
@Aspect = { "name": "datasetUsageStatistics", "type": "timeseries" }
record DatasetUsageStatistics includes TimeseriesAspectBase {
  @TimeseriesField = {}
  uniqueUserCount: optional int

  @TimeseriesFieldCollection = {"key":"user"}
  userCounts: optional array[DatasetUserUsageCounts]
}
```

**时序索引映射:**
```java
// 固定字段
"urn"             → keyword
"@timestamp"      → date
"timestampMillis" → date
"event"           → object (enabled: false)  // 不索引，仅存储
"isExploded"      → boolean                  // 集合字段是否展开

// Aspect 特定字段（从 @TimeseriesField 自动生成）
"uniqueUserCount"  → integer
"totalSqlQueries"  → integer
```

**设计优势:**
- **独立索引**: 时序 Aspect 有专用 Elasticsearch 索引，与主搜索索引分离
- **时间范围查询**: 优化 `WHERE timestamp BETWEEN X AND Y` 模式
- **聚合查询**: Elasticsearch date histograms、stats 聚合
- **Exploded 视图**: 集合字段（如 userCounts）展开为独立文档，支持按用户/字段聚合
- **专用查询线程池**: 防止时序查询饿死主搜索池

---

### 模式 6：DNF 授权模型

**关键文件:**
- `metadata-auth/auth-api/src/main/java/com/datahub/authorization/ConjunctivePrivilegeGroup.java`
- `metadata-auth/auth-api/src/main/java/com/datahub/authorization/DisjunctivePrivilegeGroup.java`

**设计结构:**
```java
// AND 组: 所有权限都必须满足
public class ConjunctivePrivilegeGroup {
    private final Collection<String> _requiredPrivileges;
}

// OR 组: 任一权限组满足即可
public class DisjunctivePrivilegeGroup {
    private final Collection<ConjunctivePrivilegeGroup> _authorizedPrivilegeGroups;
}
```

**策略表达式:**
```
OR(AND(owner, EDIT), AND(admin_role))
= 允许如果 (是所有者 AND 操作是编辑) OR (有管理员角色)
```

**设计优势:**
- **析取范式 (DNF)**: 标准 RBAC 模式，支持复杂策略组合
- **实体感知**: 策略可引用特定实体类型、字段、甚至字段值
- **字段级访问控制**: `ResolvedEntitySpec` + `FieldResolver` 支持字段级权限
- **插件化**: `AuthorizerContext` 支持不同授权后端（LDAP、OPA、自定义）

---

### 模式 7：GraphQL DataLoader 批量加载

**关键文件:**
- `datahub-graphql-core/src/main/java/com/linkedin/datahub/graphql/resolvers/load/AspectResolver.java`

```java
public class AspectResolver implements DataFetcher<CompletableFuture<Aspect>> {
    public CompletableFuture<Aspect> get(DataFetchingEnvironment environment) {
        DataLoader<VersionedAspectKey, Aspect> loader =
            environment.getDataLoaderRegistry().getDataLoader("Aspect");
        String urn = ((Entity) environment.getSource()).getUrn();
        String fieldName = environment.getField().getName();
        Long version = environment.getArgument("version");
        return loader.load(new VersionedAspectKey(urn, fieldName, version));
    }
}
```

**设计优势:**
- **N+1 查询预防**: DataLoader 批量加载多个实体的 Aspect，合并为单次后端调用
- **版本化方面访问**: Aspect 带版本号，支持历史查询（如 "3 个月前的所有权"）
- **异步执行**: 所有 Resolver 返回 `CompletableFuture`

---

### 模式 8：Python 采集框架 + 能力声明

**关键文件:**
- `metadata-ingestion/src/datahub/ingestion/api/source.py`

```python
class SourceCapability(Enum):
    PLATFORM_INSTANCE = "Platform Instance"
    DOMAINS = "Domains"
    DATA_PROFILING = "Data Profiling"
    USAGE_STATS = "Dataset Usage"
    LINEAGE_COARSE = "Table-Level Lineage"
    LINEAGE_FINE = "Column-level Lineage"
    OWNERSHIP = "Extract Ownership"
    SCHEMA_METADATA = "Schema Metadata"
    CONTAINERS = "Asset Containers"
    TEST_CONNECTION = "Test Connection"
```

**MetadataWorkUnit 双事件模型:**
```python
@dataclass
class MetadataWorkUnit(WorkUnit):
    metadata: Union[MetadataChangeEvent, MetadataChangeProposal, MetadataChangeProposalWrapper]

    def generate_workunit_id(item):
        if isinstance(item, MetadataChangeEvent):
            return f"{item.proposedSnapshot.urn}/mce"
        elif isinstance(item, MetadataChangeProposal):
            if item.aspect and item.aspectName in TIMESERIES_ASPECT_MAP:
                ts = getattr(item.aspect, "timestampMillis", None)
                return f"{item.entityUrn}-{item.aspectName}-{ts}"
            return f"{item.entityUrn}-{item.aspectName}"
```

**设计优势:**
- **能力声明**: 每个 Source 通过 `SourceCapability` 声明支持的功能，UI 据此显示/隐藏特性
- **SQLAlchemy 驱动**: SQL 源使用 SQLAlchemy inspector 自动支持多种数据库方言
- **有状态采集**: `StatefulIngestionSourceBase` 跟踪已采集实体，支持增量采集和过期实体检测
- **WorkUnit 处理管线**: 自动处理器（status_aspect、stale_removal、browse_path_v2、incremental_lineage）

## 对 nop-metadata 的启示

### 可借鉴的设计

1. **声明式实体注册表**: YAML 驱动的实体/Aspect 定义，单一真实来源
2. **PDL 注解驱动索引**: 在模型文件中声明搜索字段和关系，自动推导索引
3. **MCP/MCL 两阶段事件**: Proposal-Log 分离，支持验证和审计
4. **GenericAspect 信封**: Schema 可演进的载荷序列化
5. **Elasticsearch 图存储**: 无需图数据库，边作为文档存储
6. **时序 Aspect 独立索引**: 时序数据有专用索引策略
7. **DNF 授权模型**: 析取范式策略表达
8. **GraphQL DataLoader**: 批量加载预防 N+1 查询
9. **Python 采集框架 + 能力声明**: 可扩展的元数据采集

### 需要改进的地方

1. **复杂性**: 架构复杂，部署依赖较多组件
2. **PDL 语言**: 需要学习新的建模语言
3. **4100+ 行 GraphQL 引擎**: 集中式配置可能成为维护负担

## Open Questions

- [ ] nop-metadata 是否需要 PDL 注解驱动索引？
- [ ] 如何在 Nop ORM 上实现 Proposal-Log 事件模型？
- [ ] Elasticsearch 图存储的性能边界在哪里？

## References

- [DataHub GitHub](https://github.com/datahub-project/datahub)
- [DataHub Architecture](https://docs.datahub.com/docs/architecture/architecture/)
- 源码: `metadata-models/src/main/resources/entity-registry.yml`
- 源码: `metadata-models/src/main/pegasus/com/linkedin/`
- 源码: `metadata-io/src/main/java/com/linkedin/metadata/graph/elastic/`
- 源码: `datahub-graphql-core/src/main/java/com/linkedin/datahub/graphql/`