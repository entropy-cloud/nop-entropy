# nop-metadata AI 集成设计

> Status: draft
> Date: 2026-07-15
> Scope: nop-metadata 与 AI 的集成（GraphQL 自动暴露、元数据驱动）
> Goal: 定义 AI 与元数据的集成模式，通过 GraphQL 自动暴露接口

---

## 一、设计决策

### 1.1 接口暴露方式

**决策**: 所有服务通过 GraphQL 自动暴露，**不需要额外的 tool 定义**。

**理由**:
- Nop 平台已有 GraphQL 自动生成能力（CrudBizModel + nop-graphql）
- GraphQL schema 自动描述所有可用查询和变更
- AI 可以通过 GraphQL schema 自动发现和使用功能
- **GraphQL 本身就是统一接口**，不需要 MCP 或其他 tool 层

### 1.2 AI 学习机制

**决策**: AI 通过 GraphQL schema 自动学习功能使用。

**机制**:
1. GraphQL schema 描述所有类型和操作
2. AI 解析 schema 理解可用功能
3. AI 根据用户问题生成 GraphQL 查询
4. 执行查询并返回结果

---

## 二、GraphQL 自动暴露

### 2.1 自动生成机制

```
ORM 模型 (MetaEntity)
       ↓
CrudBizModel (自动 CRUD)
       ↓
GraphQL Schema (自动生成)
       ↓
GraphQL API (自动暴露)
```

### 2.2 自动生成的接口示例

```graphql
# 自动生成的 Query
type Query {
    metaEntity(id: ID!): MetaEntity
    metaEntities(
        filter: MetaEntityFilter
        orderBy: [MetaEntityOrderBy]
        limit: Int
        offset: Int
    ): MetaEntityConnection!
    
    metaTable(id: ID!): MetaTable
    metaTables(
        filter: MetaTableFilter
        limit: Int
        offset: Int
    ): MetaTableConnection!
    
    # 血缘查询
    getUpstream(tableId: ID!, depth: Int): [MetaLineageEdge!]!
    getDownstream(tableId: ID!, depth: Int): [MetaLineageEdge!]!
    
    # 质量查询
    getQualityRules(entityType: String, entityId: ID): [MetaQualityRule!]!
    getQualityResults(ruleId: ID!): [MetaQualityResult!]!
    
    # 搜索
    searchMetadata(query: String!, limit: Int): [SearchResult!]!
    
    # AI 上下文
    getMetadataContext(question: String!, tableName: String): MetadataContext!
}

# 自动生成的 Mutation
type Mutation {
    createMetaEntity(input: CreateMetaEntityInput!): MetaEntity!
    updateMetaEntity(id: ID!, input: UpdateMetaEntityInput!): MetaEntity!
    deleteMetaEntity(id: ID!): Boolean!
    
    importModule(input: ImportModuleInput!): MetaModule!
    executeQualityCheckpoint(configId: ID!): CheckpointResult!
}
```

---

## 三、AI 学习流程

```
用户问题
    ↓
AI 解析 GraphQL schema（理解可用功能）
    ↓
AI 构建元数据上下文（从 GraphQL 查询）
    ↓
AI 生成 GraphQL 查询
    ↓
执行 GraphQL 查询
    ↓
AI 解释查询结果
    ↓
返回给用户
```

### 3.1 Schema 驱动学习

AI 通过 GraphQL schema 自动学习：

```java
public class AiMetadataLearner {
    
    private final GraphQLSchema schema;
    private final LLMService llmService;
    
    public AiCapability learnFromSchema() {
        // 1. 导出 GraphQL schema
        String schemaSdl = schemaToString(schema);
        
        // 2. AI 解析 schema 理解功能
        String prompt = """
        分析以下 GraphQL schema，理解可用的功能：
        
        %s
        
        返回 JSON 格式的功能列表。
        """, schemaSdl;
        
        return llmService.generate(prompt, AiCapability.class);
    }
}
```

### 3.2 自然语言到 GraphQL

```java
public class NaturalLanguageToGraphQL {
    
    private final LLMService llmService;
    private final GraphQLSchema schema;
    
    public GraphQLQuery convert(String question, MetadataContext context) {
        String prompt = """
        根据用户问题和元数据上下文，生成 GraphQL 查询。
        
        用户问题: %s
        
        元数据上下文: %s
        
        可用的 GraphQL 查询:
        - metaEntities: 查询实体列表
        - metaTables: 查询表列表
        - getUpstream: 获取上游血缘
        - getDownstream: 获取下游血缘
        - getQualityRules: 获取质量规则
        - searchMetadata: 搜索元数据
        
        请生成 GraphQL 查询。
        """, question, formatContext(context);
        
        return llmService.generate(prompt, GraphQLQuery.class);
    }
}
```

---

## 四、与 metadata-survey 的对比

| 能力 | PandasAI | OpenMetadata MCP | nop-metadata |
|------|---------|-----------------|-------------|
| 接口方式 | Python API | MCP Server | **GraphQL 自动暴露** |
| Schema 描述 | DataFrame 元数据 | MCP 工具定义 | **GraphQL Schema** |
| AI 学习 | 代码生成 | 工具调用 | **Schema 驱动** |
| 查询生成 | Python 代码 | MCP 请求 | **GraphQL 查询** |

### nop-metadata 的独特优势

1. **零维护接口**: GraphQL schema 自动生成，无需额外维护
2. **自描述 API**: GraphQL schema 自动描述所有可用功能
3. **AI 自动学习**: AI 通过 schema 自动理解功能
4. **类型安全**: GraphQL 类型系统保证查询正确性
5. **不需要额外 tool 层**: GraphQL 本身就是统一接口

---

## Open Questions

- [ ] GraphQL schema 是否需要版本化？
- [ ] AI 生成的查询是否需要缓存？
