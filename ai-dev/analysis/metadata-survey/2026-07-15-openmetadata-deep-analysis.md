# OpenMetadata 深度分析报告

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 OpenMetadata 的架构设计，为 nop-metadata 提供设计参考
> Last Updated: 2026-07-15 (源码深度分析)

## Context

OpenMetadata 是一个专注于 AI 上下文层的元数据平台，强调语义搜索、知识图谱和组织记忆。本文档基于源码深度分析，详细拆解其 JSON Schema 模型、EntityRepository 模板方法、知识图谱、血缘模型、MCP Server、向量搜索和组织记忆系统，为 nop-metadata 的设计提供参考。

## 核心架构

### 1. 组件架构

```
┌─────────────────────────────────────────────────────┐
│                 OpenMetadata UI                      │
│                (React Frontend)                       │
└──────────────────────┬──────────────────────────────┘
                       │ REST API (JAX-RS)
┌──────────────────────▼──────────────────────────────┐
│  ┌─────────────────┐  ┌──────────────────────────┐  │
│  │ TableResource   │  │ McpServer                │  │
│  │ DomainResource  │  │ SearchRepository         │  │
│  │ LineageResource │  │ VectorIndexService       │  │
│  └────────┬────────┘  └────────────┬─────────────┘  │
│           │                        │                 │
│  ┌────────▼────────────────────────▼─────────────┐  │
│  │     EntityRepository<T> (13,256 行模板方法)     │  │
│  │  ┌──────────────┐  ┌──────────────────────┐   │  │
│  │  │ EntityDAO<T> │  │ EntityRelationship   │   │  │
│  │  │ (JDBI)       │  │ Repository           │   │  │
│  │  └──────────────┘  └──────────────────────┘   │  │
│  └────────┬────────────────────────┬─────────────┘  │
└───────────┼────────────────────────┼────────────────┘
            │                        │
┌───────────▼──────────┐  ┌─────────▼───────────────┐
│  MySQL / PostgreSQL  │  │    Elasticsearch         │
│  (JSON 文档 + 边表)   │  │  (搜索 + 向量索引)       │
└──────────────────────┘  └─────────────────────────┘
```

### 2. 存储策略

- **实体属性**: JSON 文档存储在每实体表（`table_entity`、`database_entity` 等）
- **实体关系**: 独立 `entity_relationship` 边表，`(fromEntity, toEntity, fromId, toId, relation)`
- **搜索索引**: Elasticsearch 反规范化文档
- **向量嵌入**: Elasticsearch dense_vector 字段

## 核心设计模式

### 模式 1：JSON Schema 作为唯一真实来源 (SSoT)

**关键文件:**
- `openmetadata-spec/src/main/resources/json/schema/entity/data/table.json`
- `openmetadata-spec/src/main/resources/json/schema/entity/type.json`

**设计结构:**

每个实体遵循一致的 JSON Schema (draft-07) 模式：

```json
{
  "$id": "https://open-metadata.org/schema/entity/data/table.json",
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Table",
  "$comment": "@om-entity-type",          // 代码生成注解
  "javaType": "org.openmetadata.schema.entity.data.Table",
  "javaInterfaces": [
    "org.openmetadata.schema.EntityInterface",         // 所有实体通用接口
    "org.openmetadata.schema.ColumnsEntityInterface"   // 可选混入接口
  ]
}
```

**通用实体属性 (EntityInterface 契约):**

| 属性 | 类型 | 用途 |
|------|------|------|
| `id` | UUID | 唯一标识符 |
| `name` | entityName | 系统名称 |
| `fullyQualifiedName` | fqn | 层级点分隔路径 |
| `displayName` | string | 人类可读名称 |
| `description` | markdown | 富文本描述 |
| `version` | entityVersion | 单调递增版本 |
| `updatedAt` / `updatedBy` | timestamp/string | 审计跟踪 |
| `changeDescription` | changeDescription | 与上一版本的差异 |
| `deleted` | boolean | 软删除标志 |
| `owners`, `tags`, `domains` | EntityReferenceList | 横切关系字段 |
| `extension` | map | 自定义属性（可扩展 Schema） |

**枚举模式:**
```json
"tableType": {
  "javaType": "org.openmetadata.schema.type.TableType",
  "type": "string",
  "enum": ["Regular", "External", "Dynamic", "View", ...],
  "javaEnums": [{ "name": "Regular" }, { "name": "External" }, ...]
}
```

**设计优势:**
- **单一真实来源**: 实体形状定义一次，Java POJO + 代码生成
- **`$comment: "@om-entity-type"`**: 触发构建管道生成 DAO 接口和 CRUD 样板代码
- **混入接口**: `javaInterfaces` 支持可选能力混入（如 `ColumnsEntityInterface`）
- **枚举序数存储**: 枚举序数存入数据库，新枚举只能追加不能删除

---

### 模式 2：EntityRepository 模板方法 (13,256 行)

**关键文件:**
- `openmetadata-service/src/main/java/org/openmetadata/service/jdbi3/EntityRepository.java`

**设计结构:**

```java
@Repository()
public abstract class EntityRepository<T extends EntityInterface> {
    // 子类必须实现的模板方法钩子:
    protected abstract void setFields(T entity, Fields fields, RelationIncludes relationIncludes);
    protected abstract void clearFields(T entity, Fields fields);
    protected abstract void prepare(T entity, boolean update);          // 实体特定验证
    protected abstract void storeEntity(T entity, boolean update);      // 存储实体
    protected abstract void storeRelationships(T entity);               // 存储关系
}
```

**构造器自动发现:**
```java
// 通过反射检查实体的 allowedFields，动态配置特性支持
this.supportsTags = allowedFields.contains(FIELD_TAGS);
this.supportsOwners = allowedFields.contains(FIELD_OWNERS);
this.supportsDomains = allowedFields.contains(FIELD_DOMAINS);
// 每个自动注册批量字段获取器
fieldFetchers.put(FIELD_TAGS, this::fetchAndSetTags);
fieldFetchers.put(FIELD_OWNERS, this::fetchAndSetOwners);
```

**核心模板方法 `prepareInternal`:**
```java
public final void prepareInternal(T entity, boolean update) {
    validateTags(entity);
    prepare(entity, update);           // 子类钩子: 实体特定验证
    setFullyQualifiedName(entity);
    validateExtension(entity, update);
    setDefaultStatus(entity, update);
}

public final void storeRelationshipsInternal(T entity) {
    storeOwners(entity, entity.getOwners());
    applyTags(entity);
    storeDomains(entity, entity.getDomains());
    storeDataProducts(entity, entity.getDataProducts());
    storeReviewers(entity, entity.getReviewers());
    applyCertification(entity);
    storeRelationships(entity);       // 子类钩子: 实体特定关系
}
```

**设计优势:**
- **`final` 修饰符**: 防止子类破坏不变量顺序
- **横切关注点自动处理**: 所有实体自动获得 tags、owners、domains、certification 等
- **字段门控**: API 消费者指定要水合的字段，避免 N+1 查询
- **缓存竞争检测**: 使用 `WRITE_EPOCH` 计数器检测加载器与写入者的竞争

---

### 模式 3：实体注册表 + 类型到仓库映射

**关键文件:**
- `openmetadata-service/src/main/java/org/openmetadata/service/Entity.java`

```java
public final class Entity {
    private static final Map<String, EntityRepository<? extends EntityInterface>>
        ENTITY_REPOSITORY_MAP = new HashMap<>();

    public static void registerEntity(Class<?> entityClass, String entityType,
                                      EntityRepository<?> entityRepository) {
        ENTITY_REPOSITORY_MAP.put(entityType, entityRepository);
    }
}
```

每个 `EntityRepository` 子类在构造器中自注册：
```java
public DataProductRepository() {
    super(...);
    Entity.registerEntity(DataProduct.class, Entity.DATA_PRODUCT, this);
}
```

**设计优势:**
- **跨实体遍历**: `Entity.getEntity()`、`Entity.getEntityReferenceById()` 使用类型到仓库映射
- **自注册模式**: 每个仓库自注册，无需集中配置

---

### 模式 4：边表知识图谱

**关键文件:**
- `openmetadata-spec/src/main/resources/json/schema/type/entityRelationship.json`

**关系类型 (27 种):**
```
contains, createdBy, repliedTo, isAbout, addressedTo, mentionedIn,
testedBy, uses, owns, parentOf, has, follows, joinedWith, upstream,
appliedTo, relatedTo, reviews, reactedTo, voted, expert, editedBy,
defaultsTo, relatesTo, inputPort, outputPort, assignedTo, derivedFrom
```

**存储模型:**
```java
// 属性存储为 JSON 文档（不包含关系）
// 关系存储在独立边表中
CollectionDAO.EntityRelationshipObject.builder()
    .fromId(fromId.toString())
    .toId(toId.toString())
    .fromEntity(fromEntity)
    .toEntity(toEntity)
    .relation(relationship.ordinal())  // 整数序号，非字符串
    .build();
```

**设计优势:**
- **关系永不过时**: 关系通过 UUID 键而非 FQN 或名称，重命名不影响关系
- **类型化有向多重图**: `(fromEntity:UUID --[Relationship]--> toEntity:UUID)` 带可选 JSON 载荷
- **批量加载**: 按实体类型分组，每类型一次批量查询而非 N 次单独查询

---

### 模式 5：列级血缘模型

**关键文件:**
- `openmetadata-spec/src/main/resources/json/schema/type/entityLineage.json`

**血缘 DAG 模型:**
```json
{
  "entity": { /* EntityReference - 根实体 */ },
  "nodes": [ /* EntityReference[] - 所有连接实体 */ ],
  "upstreamEdges": [ /* Edge[] - 从上游到根 */ ],
  "downstreamEdges": [ /* Edge[] - 从根到下游 */ ]
}
```

**列级血缘:**
```json
"columnLineage": {
  "fromColumns": ["db.schema.table.column1", ...],   // 源列 (FQN)
  "toColumn": "db.schema.table.column2",              // 目标列 (FQN)
  "function": "CONCAT(col1, col2)"                    // 转换函数
}

"lineageDetails": {
  "sqlQuery": "SELECT CONCAT(first, last) AS full_name FROM users",
  "columnsLineage": [ /* columnLineage[] */ ],
  "pipeline": { /* EntityReference to pipeline */ },
  "source": "Manual|QueryLineage|PipelineLineage|DbtLineage|OpenLineage|...",
  "tempLineageTables": [ {"fromEntity": "...", "toEntity": "..."} ]
}
```

**血缘仓库关键模式:**
- **域级血缘裁剪**: 返回前按用户域访问过滤节点
- **扩展血缘**: 自动创建服务级、域级、数据产品级血缘边
- **Elasticsearch 血缘图**: 直接从反规范化搜索文档构建血缘图，避免 N+1 查询

**设计优势:**
- **列级血缘**: 存储为边表上的 JSON 载荷，而非独立表
- **血缘来源追踪**: `source` 枚举追踪血缘出处（手动、SQL 解析、Dbt、OpenLineage 等）
- **临时表跳转**: `tempLineageTables` 建模多步 ETL 中间/临时表

---

### 模式 6：域 + 数据产品 (Data Mesh)

**关键文件:**
- `openmetadata-spec/src/main/resources/json/schema/entity/domains/domain.json`
- `openmetadata-spec/src/main/resources/json/schema/entity/domains/dataProduct.json`

**域模型:**
```json
{
  "parent": "EntityRef",           // 自引用树
  "children": ["EntityRef"],
  "domainType": "Source-aligned|Consumer-aligned|Aggregate",  // DAMA 分类
  "experts": ["EntityRef"]         // 域专家
}
```

**数据产品模型 (ODPS):**
```json
{
  "lifecycleStage": "IDEATION|DESIGN|DEVELOPMENT|TESTING|PRODUCTION|DEPRECATED|RETIRED",
  "dataProductType": "RAW_DATA|DERIVED_DATA|DATASET|REPORTS|ALGORITHM|...",  // 13 种
  "visibility": "PRIVATE|INVITATION|ORGANISATION|DATASPACE|PUBLIC",
  "dataProductPort": [             // 输入/输出端口
    { "portType": "input|output", "protocol": "REST|JDBC|Kafka|S3", "format": "JSON|CSV|Parquet" }
  ],
  "assets": ["EntityRef"]          // 组成数据产品的资产
}
```

**域仓库关键模式:**
- **层次哈希**: 使用 FQN 哈希前缀高效计算后代数量
- **级联硬删除**: `DomainHardDeleteContext` 跟踪子树删除状态
- **继承字段搜索**: 域支持向下继承搜索字段（owner、domain、tags）

---

### 模式 7：MCP Server 工具分派

**关键文件:**
- `openmetadata-mcp/src/main/java/org/openmetadata/mcp/McpServer.java`
- `openmetadata-mcp/src/main/java/org/openmetadata/mcp/tools/McpTool.java`

**工具接口:**
```java
public interface McpTool {
    Map<String, Object> execute(
        Authorizer authorizer, CatalogSecurityContext securityContext,
        Map<String, Object> params) throws IOException;
}
```

**28 个 MCP 工具:**
| 工具名 | 实现类 | 用途 |
|--------|--------|------|
| `search_metadata` | `SearchMetadataTool` | 全文搜索 |
| `semantic_search` | `SemanticSearchTool` | 向量/语义搜索 |
| `get_entity_details` | `GetEntityTool` | 按 FQN 获取实体 |
| `get_lineage` | `GetLineageTool` | 血缘图遍历 |
| `create_context_memory` | `CreateContextMemoryTool` | 存储 AI 记忆 |
| `create_test_case` | `CreateTestCaseTool` | 创建数据质量测试 |
| `root_cause_analysis` | `RootCauseAnalysisTool` | 调查测试失败 |
| `patch_entity` | `PatchEntityTool` | 更新实体字段 |

**设计优势:**
- **工具定义从 JSON 加载**: 非硬编码，可扩展
- **RBAC 集成**: 每个工具接收 `Authorizer` 和 `CatalogSecurityContext`
- **速率限制**: `Limits` 参数支持按用户限流

---

### 模式 8：Mixin 搜索索引组合

**关键文件:**
- `openmetadata-service/src/main/java/org/openmetadata/service/search/indexes/SearchIndex.java`
- `openmetadata-service/src/main/java/org/openmetadata/service/search/indexes/TableIndex.java`

**搜索索引构建:**
```java
public interface SearchIndex {
    default Map<String, Object> buildSearchIndexDoc(DocBuildContext ctx) {
        Map<String, Object> esDoc = JsonUtils.getMap(getEntity());

        // Phase 1: 通用实体字段
        populateCommonFields(esDoc, entity, getEntityTypeName());

        // Phase 2: 基于接口自动应用 Mixin
        if (this instanceof TaggableIndex ti)      ti.applyTagFields(esDoc);
        if (this instanceof ServiceBackedIndex sbi) sbi.applyServiceFields(esDoc, ctx);
        if (this instanceof LineageIndex li)        li.applyLineageFields(esDoc, ctx);
        if (this instanceof AIContextIndex ai)      ai.applyAIContextFields(esDoc);

        // Phase 3: 实体特定字段
        esDoc = this.buildSearchIndexDocInternal(esDoc);
        return esDoc;
    }
}

// Mixin 组合示例
public interface DataAssetIndex extends TaggableIndex, ServiceBackedIndex, LineageIndex {}
public record TableIndex(Table table) implements ColumnIndex, DataAssetIndex, AIContextIndex {}
```

**设计优势:**
- **Mixin 接口组合**: 添加 tag 支持只需实现 `TaggableIndex` 接口
- **78 个索引类**: `SearchIndexFactory` 使用 switch 映射实体类型到索引实现
- **列扁平化**: Table 索引将嵌套列扁平化为可搜索字段

---

### 模式 9：向量搜索 + 语义搜索

**关键文件:**
- `openmetadata-service/src/main/java/org/openmetadata/service/search/vector/VectorIndexService.java`
- `openmetadata-service/src/main/java/org/openmetadata/service/search/vector/VectorDocBuilder.java`

**向量搜索接口:**
```java
public interface VectorIndexService {
    String VECTOR_EMBEDDING_ALIAS = "dataAssetEmbeddings";

    Map<String, Object> generateEmbeddingFields(EntityInterface entity);
    void updateEntityEmbedding(EntityInterface entity, String entityIndexName);
    void updateEntityEmbeddingChunks(EntityInterface entity);   // 多块嵌入
    VectorSearchResponse search(String query, Map<String, List<String>> filters,
                                  int size, int from, int k, double threshold);
}
```

**VectorDocBuilder 核心模式:**
- **可插拔正文提取**: 实体类型注册自定义 `BodyTextExtractor`
  - Table: 提取 `description` + 列名
  - ContextMemory: 提取 `title + question + answer + summary`
- **多块嵌入**: 长文档分块，每块独立嵌入，每块携带 `embedding` + `textToEmbed` + `chunkIndex`
- **Schema 版本化**: `CHUNK_DOC_VERSION = 1` 支持增量映射迁移
- **语义子节点**: 容器实体追加子实体名称到正文

---

### 模式 10：组织记忆系统

**关键文件:**
- `openmetadata-spec/src/main/resources/json/schema/entity/context/contextMemory.json`

**Context Memory 实体:**
```json
{
  "memoryType": "Preference|UseCase|Note|Runbook|Faq",
  "memoryScope": "UserGlobal|EntityScoped",
  "status": "Draft|Active|Archived",
  "sourceType": "Manual|ChatPromotion|RememberRequest|FileExtraction|PageExtraction",
  "shareConfig": {
    "visibility": "Private|Entity|Shared",
    "sharedWith": [{"principal": EntityRef, "role": "Viewer|Editor"}]
  },
  "machineRepresentation": {
    "format": "...", "version": "...", "content": "...",  // 压缩供 AI 使用
    "status": "Pending|Ready|Stale|Failed"
  },
  "memoryStats": {
    "status": "Queued|Processing|Processed|Failed",
    "sourceHash": "...",          // 变更检测
    "derivedTermCount": 0,        // Memory Agent 推导的词汇表术语
    "derivedMetricCount": 0,      // Memory Agent 推导的指标
    "reusedCount": 0              // 复用的现有术语
  },
  "primaryEntity": EntityRef,     // 挂载实体
  "rootMemory": EntityRef,        // 线程根（追加式）
  "parentMemory": EntityRef,      // 记忆线程中的父节点
  "question": "...",              // 规范问题
  "answer": "..."                 // 规范答案
}
```

**设计优势:**
- **记忆是一等公民实体**: 具有版本、所有权、标签、域分配和共享能力
- **线程化对话**: `rootMemory`/`parentMemory` 形成链表
- **机器可读表示**: `machineRepresentation` 支持高效 AI 提示打包
- **Memory Agent 统计**: 追踪推导的术语和指标数量

---

### 模式 11：数据契约 (ODCS)

**关键文件:**
- `openmetadata-spec/src/main/resources/json/schema/entity/data/dataContract.json`

**数据契约模型:**
```json
{
  "entityStatus": "Draft|Active|Deprecated",
  "entity": EntityRef,                     // 数据资产
  "testSuite": EntityRef,                  // 关联测试套件
  "schema": [Column],                      // 模式定义
  "semantics": [SemanticsRule],            // 语义规则
  "security": {
    "dataClassification": "Confidential|PII|...",
    "policies": [{"accessPolicy": "...", "identities": [...], "rowFilters": [...]}]
  },
  "sla": {
    "refreshFrequency": {"interval": 1, "unit": "day"},
    "maxLatency": {"value": 4, "unit": "hour"},
    "retention": {"period": 90, "unit": "day"}
  },
  "qualityExpectations": [EntityRef],      // 引用测试用例
  "latestResult": {
    "timestamp": timestamp,
    "status": ContractExecutionStatus,
    "message": "..."
  }
}
```

**设计优势:**
- **ODCS 3.1 合规**: 支持开放数据契约标准的导入/导出
- **多维验证**: 模式验证、语义验证、SLA 验证、质量验证
- **状态流转**: proposed -> draft -> active -> deprecated -> retired

---

### 模式 12：数据质量测试框架

**关键文件:**
- `openmetadata-spec/src/main/resources/json/schema/tests/testDefinition.json`
- `openmetadata-spec/src/main/resources/json/schema/tests/testCase.json`

**测试定义 (模板):**
```json
{
  "entityType": "TABLE|COLUMN",
  "testPlatforms": ["OpenMetadata", "GreatExpectations", "dbt", "Deequ", "Soda"],
  "parameterDefinition": [
    { "name": "threshold", "dataType": "NUMBER", "required": true }
  ],
  "dataQualityDimension": "Completeness|Accuracy|Consistency|Validity|Uniqueness|Integrity",
  "sqlExpression": "SELECT ... FROM {table} WHERE {column} ..."
}
```

**测试用例 (实例):**
```json
{
  "testDefinition": EntityRef,
  "entityLink": "Table::fully.qualified.name",
  "parameterValues": [{"name": "threshold", "value": "100"}],
  "testCaseResult": TestCaseResult,
  "testCaseStatus": "Success|Failed|Aborted|Queued|Cancelled|Skipped",
  "dimensionColumns": ["country", "region"],  // 按维度分组结果
  "incidentId": UUID,                         // 关联活跃事件
  "autoCloseIncident": false
}
```

**测试框架架构:**
```
TestDefinition (模板) --[1:N]--> TestCase (实例) --[N:1]--> TestSuite (集合)
                    |                        |
                    v                        v
            validatorClass            testCaseResult (时序)
            sqlExpression              dimensionResult (时序)
```

**设计优势:**
- **多平台执行**: 原生测试、GreatExpectations、dbt、Deequ、Soda 统一产生标准化结果
- **动态断言**: 阈值可适应历史数据模式
- **维度结果**: 按维度组存储结果（如 "country=US 的完整性" vs "country=UK"）
- **事件生命周期**: 测试失败创建事件，成功可自动关闭

## 对 nop-metadata 的启示

### 可借鉴的设计

1. **JSON Schema SSoT + 代码生成**: 一次定义，生成 POJO/DAO/CRUD
2. **EntityRepository 模板方法**: 13K 行通用 CRUD + 子类钩子
3. **横切关注点自动发现**: 通过字段门控动态配置 tags/owners/domains
4. **边表知识图谱**: 关系与属性分离，永不过时
5. **列级血缘**: 存储为边表 JSON 载荷，带来源追踪
6. **Mixin 搜索索引**: 接口组合自动应用搜索增强
7. **向量搜索 + 多块嵌入**: 语义搜索的完整实现
8. **组织记忆一等公民**: 记忆作为版本化实体，支持线程和共享
9. **数据契约 ODCS**: 开放标准合规
10. **MCP 工具分派**: 28 个预定义工具，RBAC 集成

### 需要改进的地方

1. **复杂性**: 功能丰富但架构复杂，13K 行 EntityRepository 难以维护
2. **性能**: 大规模部署需要优化
3. **JSON 文档存储**: 关系查询需要额外边表，可能成为瓶颈

## Open Questions

- [ ] nop-metadata 是否需要 JSON Schema 代码生成？
- [ ] 如何在 Nop ORM 上实现模板方法 CRUD？
- [ ] 是否需要向量搜索支持？

## References

- [OpenMetadata GitHub](https://github.com/open-metadata/OpenMetadata)
- [OpenMetadata Standards](https://openmetadatastandards.org/)
- 源码: `openmetadata-spec/src/main/resources/json/schema/`
- 源码: `openmetadata-service/src/main/java/org/openmetadata/service/jdbi3/EntityRepository.java`
- 源码: `openmetadata-mcp/src/main/java/org/openmetadata/mcp/`