# Metadata 平台深度对比分析

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 比较主流 metadata 平台的源码级设计模式，为 nop-metadata 提供设计建议
> Last Updated: 2026-07-15 (源码深度分析)

## Context

本文档基于对 DataHub、OpenMetadata、Apache Atlas、Amundsen、Marquez 五个平台的源码深度分析，对比其核心设计模式，为 nop-metadata 的设计提供参考。

## 架构对比总览

| 维度 | DataHub | OpenMetadata | Apache Atlas | Marquez |
|------|---------|-------------|--------------|---------|
| **存储策略** | MySQL + ES 三合一 | JSON 文档 + 边表 | HBase 图存储 | PostgreSQL 递归 CTE |
| **模型定义** | PDL + YAML 注册表 | JSON Schema SSoT | Java 类型系统 | Java POJO |
| **关系存储** | ES 边文档 | 独立边表 (整数序号) | JanusGraph 边 | SQL 数组交集 |
| **血缘模型** | 关系遍历 | DAG + 列级边载荷 | Process 中心 | 递归 CTE BFS |
| **搜索索引** | 注解驱动生成 | Mixin 接口组合 | Solr 手写映射 | ES 基础搜索 |
| **事件模型** | MCP → MCL | ChangeEvent | Hook 通知 | OpenLineage 双写 |
| **类型系统** | Entity + Aspect | JSON Schema + EntityInterface | 三阶段解析类型注册 | 四实体精简模型 |
| **授权** | DNF 策略组 | RBAC + 策略评估 | Ranger 集成 | 无 |
| **AI/MCP** | ✅ MCP 工具 | ✅ 28 个 MCP 工具 | ❌ | ❌ |
| **向量搜索** | ❌ | ✅ 多块嵌入 | ❌ | ❌ |
| **数据契约** | ❌ | ✅ ODCS 3.1 | ❌ | ❌ |
| **组织记忆** | ❌ | ✅ ContextMemory | ❌ | ❌ |

## 核心设计模式对比

### 1. 元数据模型定义方式

#### DataHub: PDL + YAML 注册表
```yaml
# entity-registry.yml - 声明式实体定义
entities:
  - name: dataset
    keyAspect: datasetKey
    aspects: [schemaMetadata, ownership, globalTags, ...]
```
```pdl
// PDL 文件 - 带注解的 Aspect 定义
@Aspect = { "name": "globalTags" }
@Searchable = { "/*/tag": { "fieldName": "tags", "fieldType": "URN" } }
@Relationship = { "/*/tag": { "name": "TaggedWith", "entityTypes": ["tag"] } }
record GlobalTags { tags: array[TagAssociation] }
```

#### OpenMetadata: JSON Schema SSoT + 代码生成
```json
{
  "$comment": "@om-entity-type",
  "javaInterfaces": ["org.openmetadata.schema.EntityInterface"],
  "properties": { "columns": { "$ref": "#/definitions/column" } }
}
```

#### Atlas: Java 类型系统 + 三阶段解析
```java
public class AtlasEntityDef extends AtlasStructDef {
    private Set<String> superTypes;
    private Set<String> subTypes;  // 只读，自动计算
}
// 三阶段: resolveReferences → resolveReferencesPhase2 → resolveReferencesPhase3
```

#### Marquez: Java POJO + Lombok
```java
@JsonSubTypes({@JsonSubTypes.Type(value = DbTable.class, name = "DB_TABLE")})
public abstract class Dataset {
    ImmutableMap<String, Object> facets;  // 可扩展元数据
}
```

**对比结论:** DataHub 的注解驱动最优雅（模型定义即索引/关系定义），OpenMetadata 的 JSON Schema 最标准化。

---

### 2. 关系存储策略

#### DataHub: Elasticsearch 边文档
```
graph_service_v1 索引:
{ source: {urn, entityType}, destination: {urn, entityType}, relationshipType: "DownstreamOf" }
```
- 无需图数据库，边作为 ES 文档
- 自由形式关系类型字符串

#### OpenMetadata: 独立关系边表
```sql
entity_relationship (fromEntity, toEntity, fromId, toId, relation INTEGER)
```
- 关系序号存整数（枚举序号），非字符串
- JSON 文档永不包含关系

#### Atlas: JanusGraph 图原生
```
[Dataset] --dataset_process_inputs--> [Process] --process_dataset_outputs--> [Dataset]
```
- 原生图遍历（Gremlin）
- Process 中心血缘

#### Marquez: SQL 数组交集
```sql
WHERE array_cat(io.inputs, io.outputs) && array_cat(l.inputs, l.outputs)
```
- 递归 CTE 替代图遍历
- 无需图数据库

**对比结论:** Marquez 的递归 CTE 方案最轻量（仅需 PostgreSQL），Atlas 的图原生最强大但依赖重。

---

### 3. 血缘模型

| 维度 | DataHub | OpenMetadata | Atlas | Marquez |
|------|---------|-------------|-------|---------|
| **列级血缘** | ✅ 关系遍历 | ✅ 边 JSON 载荷 | ✅ Process 属性 | ✅ ColumnLineage |
| **血缘存储** | ES 边文档 | entity_relationship 边 | JanusGraph 边 | job_versions_io_mapping |
| **血缘查询** | ES 嵌套查询 | DAG 遍历 | Gremlin 遍历 | 递归 CTE BFS |
| **血缘来源** | Hook 事件 | 多来源枚举 | Hook 通知 | OpenLineage 事件 |
| **临时表处理** | ❌ | ✅ tempLineageTables | ❌ | ✅ symlink |

**OpenMetadata 列级血缘最丰富:**
```json
"lineageDetails": {
  "sqlQuery": "SELECT CONCAT(first, last) AS full_name FROM users",
  "columnsLineage": [{
    "fromColumns": ["users.first_name"],
    "toColumn": "users_full_name.full_name",
    "function": "CONCAT(first, last)"
  }],
  "source": "QueryLineage|PipelineLineage|DbtLineage|OpenLineage|..."
}
```

---

### 4. 搜索索引生成

#### DataHub: 注解驱动生成
```pdl
@Searchable = {
  "fieldName": "tags", "fieldType": "URN", "boostScore": 0.5,
  "addToFilters": true, "filterNameOverride": "Tagged With"
}
```
- PDL 注解 → MappingsBuilder 自动生成 ES 映射

#### OpenMetadata: Mixin 接口组合
```java
public interface DataAssetIndex extends TaggableIndex, ServiceBackedIndex, LineageIndex {}
public record TableIndex(Table table) implements ColumnIndex, DataAssetIndex, AIContextIndex {}
```
- 实现接口 → 自动应用搜索增强

#### Atlas: Solr 手写映射
- 手写 Schema 映射文件

**对比结论:** DataHub 注解驱动和 OpenMetadata Mixin 组合都很优雅，Atlas 手写映射维护成本高。

---

### 5. 事件模型

#### DataHub: Proposal → Log 两阶段
```
MCP (写入请求) → GMS 验证+持久化 → MCL (已提交日志)
                                    ↓
                              ES 索引更新 / 图索引更新 / 通知
```

#### OpenMetadata: ChangeEvent
```
Entity 变更 → ChangeEvent (ENTITY_CREATED/UPDATED/DELETED)
            → SearchRepository 索引更新
```

#### Atlas: Hook 通知
```
Hive Operation → HiveHook → BaseHiveEvent → HookNotification → Atlas Server
```

#### Marquez: 双路径异步
```
OpenLineage Event → CompletableFuture.allOf(
    存储原始事件 (审计),
    更新规范化模型 (查询) + 通知监听器
)
```

**对比结论:** DataHub 的 MCP/MCL 最严谨（支持验证和审计），Marquez 的双路径异步最实用。

---

### 6. 扩展机制

| 平台 | 扩展点 | 扩展方式 |
|------|--------|----------|
| DataHub | Aspect, 关系, 搜索字段 | PDL 注解 + YAML 注册 |
| OpenMetadata | 实体, 搜索, MCP 工具 | JSON Schema + Mixin 接口 |
| Atlas | 类型, 分类, 业务元数据 | Java 类型系统 + REST API |
| Marquez | Dataset 子类, Facet | Jackson 多态 + @JsonAnySetter |

**OpenMetadata 的 ContextMemory 扩展模型:**
```json
{
  "machineRepresentation": { "format": "...", "content": "..." },
  "memoryStats": { "derivedTermCount": 0, "derivedMetricCount": 0 },
  "rootMemory": EntityRef,  // 线程化对话
  "parentMemory": EntityRef
}
```

**Marquez 的 Facet 扩展模型:**
```java
abstract static class BaseFacet {
    URI _producer;
    URI _schemaURL;
    @JsonAnySetter Map<String, Object> additional;  // 自定义 Facet
}
```

---

## 最值得 nop-metadata 借鉴的设计模式

### Tier 1: 必须借鉴

| 模式 | 来源 | nop-metadata 应用 |
|------|------|-------------------|
| **声明式实体注册** | DataHub | XDef 定义实体 + Aspect 白名单 |
| **模板方法 CRUD** | OpenMetadata | EntityRepository 基类 + 子类钩子 |
| **边表关系存储** | OpenMetadata | Nop ORM 关系表，UUID 键 |
| **递归 CTE 血缘** | Marquez | PostgreSQL/MySQL 递归查询 |
| **Propose-Commit 事件** | DataHub | MCP → MCL 两阶段 |

### Tier 2: 建议借鉴

| 模式 | 来源 | nop-metadata 应用 |
|------|------|-------------------|
| **注解驱动索引** | DataHub | XDef 注解 → ES 映射 |
| **向量搜索** | OpenMetadata | 多块嵌入 + 可插拔正文提取 |
| **组织记忆实体** | OpenMetadata | 记忆作为版本化实体 |
| **数据契约 ODCS** | OpenMetadata | 开放标准合规 |
| **DNF 授权** | DataHub | 析取范式策略 |

### Tier 3: 可选借鉴

| 模式 | 来源 | nop-metadata 应用 |
|------|------|-------------------|
| **三阶段类型解析** | Atlas | 循环引用处理 |
| **Copy-on-Write 类型注册** | Atlas | 运行时类型管理 |
| **Process 中心血缘** | Atlas | 血缘中间实体 |
| **四实体精简模型** | Marquez | 轻量血缘核心 |

## nop-metadata 推荐架构

```
┌─────────────────────────────────────────────────────┐
│                 nop-metadata UI                      │
│              (React / amis 配置化)                    │
└──────────────────────┬──────────────────────────────┘
                       │ GraphQL / REST API
┌──────────────────────▼──────────────────────────────┐
│  ┌──────────────────────────────────────────────┐   │
│  │  EntityRepository<T> (模板方法)                │   │
│  │  prepare() → storeEntity() → storeRelations() │   │
│  └──────────────────────┬───────────────────────┘   │
│  ┌──────────────────────▼───────────────────────┐   │
│  │  Nop ORM (实体属性 JSON + 关系边表)            │   │
│  └──────────────────────┬───────────────────────┘   │
│  ┌──────────────────────▼───────────────────────┐   │
│  │  EventBus (MCP → MCL 两阶段)                  │   │
│  └──────────────────────┬───────────────────────┘   │
└─────────────────────────┼───────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
┌───────▼──────┐  ┌───────▼──────┐  ┌──────▼──────┐
│  MySQL/PG    │  │ Elasticsearch│  │  Kafka/RMQ  │
│ (ORM 存储)   │  │ (搜索+向量)   │  │  (事件总线)  │
└──────────────┘  └──────────────┘  └─────────────┘
```

## Open Questions

- [ ] nop-metadata 是否需要 PDL 注解驱动索引，还是用 XDef 注解？
- [ ] 递归 CTE 在 MySQL 上的性能是否满足需求？
- [ ] 向量搜索是否作为 Phase 1 还是 Phase 2 功能？

## References

- [DataHub Deep Analysis](./2026-07-15-datahub-deep-analysis.md)
- [OpenMetadata Deep Analysis](./2026-07-15-openmetadata-deep-analysis.md)
- [Apache Atlas Deep Analysis](./2026-07-15-atlas-deep-analysis.md)
- [Amundsen Deep Analysis](./2026-07-15-amundsen-deep-analysis.md)
- [Marquez Deep Analysis](./2026-07-15-marquez-deep-analysis.md)