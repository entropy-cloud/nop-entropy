# nop-metadata Reconciliation 设计

> Status: draft
> Date: 2026-07-15
> Scope: nop-metadata 实体对账、外部知识库集成
> Goal: 定义 Reconciliation 对账模型，参考 OpenRefine Reconciliation 模式
> Based on: OpenRefine Reconciliation、Wikidata Reconciliation API

---

## 一、设计决策

### 1.1 Reconciliation 定位

**决策**: Reconciliation 是 nop-metadata 的核心能力之一，用于实体与外部知识库的匹配和对齐。

**理由**:
- 主数据标准化需要与外部知识库对齐
- 数据清洗需要识别相同实体的不同表述
- 数据增强需要基于匹配结果扩展属性

### 1.2 Reconciliation 服务

**决策**: 支持可插拔的 Reconciliation 服务，兼容标准 Reconciliation API。

**标准**:
- 遵循 Wikidata Reconciliation API 规范
- 支持 `suggest/entity`、`suggest/type`、`reconcile` 端点
- 支持批量查询和自动匹配

### 1.3 对账粒度

**决策**: 支持表级和列级对账。

**粒度**:
- **表级对账**: 整个表与外部知识库对齐
- **列级对账**: 特定列与外部实体类型对齐

---

## 二、核心模型

### 2.1 Reconciliation 配置

```
MetaReconciliationConfig         — 对账配置
  ├── configName                 — 配置名称
  ├── displayName                — 显示名称
  ├── moduleId                   → MetaModule（所属模块）
  │
  ├── serviceUrl                 — Reconciliation 服务 URL
  ├── identifierSpace            — 标识符空间（如 Wikidata URI）
  ├── schemaSpace                — Schema 空间
  │
  ├── targetEntityType           — 目标实体类型（可选）
  ├── autoMatch                  — 是否自动匹配
  ├── autoMatchThreshold         — 自动匹配阈值（0.0~1.0）
  │
  ├── columns[]                  — 要对账的列配置
  │   └── ReconColumnConfig
  │       ├── columnName         — 列名
  │       ├── entityType         — 实体类型
  │       ├── matchStrategy      — 匹配策略
  │       └── expandProperties   — 要扩展的属性列表
  │
  ├── schedule                   — 执行计划（可选）
  └── extConfig
```

### 2.2 Reconciliation 结果

```
MetaReconciliationResult         — 对账结果
  ├── configId                   → MetaReconciliationConfig
  ├── executeTime                — 执行时间
  ├── tableId                    → MetaTable
  │
  ├── statistics                 — 统计信息
  │   ├── totalRows              — 总行数
  │   ├── matchedRows            — 匹配行数
  │   ├── unmatchedRows          — 未匹配行数
  │   ├── multipleMatches        — 多候选行数
  │   └── matchRate              — 匹配率
  │
  └── details[]                  — 详细结果
      └── ReconRowResult
          ├── rowIndex           — 行索引
          ├── originalValue      — 原始值
          ├── status             — "matched" | "unmatched" | "multiple" | "manual"
          ├── candidates[]       — 候选匹配列表
          │   └── ReconCandidate
          │       ├── entityId   — 实体 ID
          │       ├── entityName — 实体名称
          │       ├── entityType — 实体类型
          │       ├── score      — 匹配置信度
          │       └── properties — 实体属性
          ├── selectedId         — 选中的实体 ID（人工确认后）
          └── expandedData       — 扩展的数据（可选）
```

### 2.3 Reconciliation 实体

```
MetaReconciliationEntity         — 对账实体（缓存外部实体）
  ├── entityId                   — 外部实体 ID
  ├── entityName                 — 实体名称
  ├── entityType                 — 实体类型
  ├── identifierSpace            — 标识符空间
  │
  ├── properties[]               — 实体属性
  │   └── ReconEntityProperty
  │       ├── propertyId         — 属性 ID
  │       ├── propertyName       — 属性名称
  │       └── propertyValue      — 属性值
  │
  ├── lastSyncedAt               — 最后同步时间
  └── extConfig
```

---

## 三、Reconciliation 流程

### 3.1 标准 Reconciliation API

```
POST /reconcile
{
  "query": "Microsoft",
  "type": "Q4830453",
  "limit": 5
}

Response:
{
  "result": [
    {
      "id": "Q2283",
      "name": "Microsoft",
      "type": ["Q4830453"],
      "score": 0.95,
      "properties": [
        {"pid": "P17", "label": "country", "value": {"id": "Q30", "name": "United States"}},
        {"pid": "P112", "label": "founded by", "value": {"id": "Q5284", "name": "Bill Gates"}}
      ]
    }
  ]
}
```

### 3.2 对账执行流程

```java
public class ReconciliationExecutor {
    
    private final ReconciliationServiceFactory serviceFactory;
    private final MetaReconciliationResultRepository resultRepository;
    private final MetaReconciliationEntityRepository entityRepository;
    
    /**
     * 执行对账
     */
    public MetaReconciliationResult execute(MetaReconciliationConfig config) {
        // 1. 加载表数据
        DataTable table = loadTable(config.getTableId());
        
        // 2. 获取 Reconciliation 服务
        ReconciliationService service = serviceFactory.getService(config.getServiceUrl());
        
        // 3. 批量查询
        List<ReconRowResult> details = new ArrayList<>();
        
        for (int rowIndex = 0; rowIndex < table.getRowCount(); rowIndex++) {
            String value = table.getValue(rowIndex, config.getColumnName());
            
            // 查询候选匹配
            List<ReconCandidate> candidates = service.reconcile(
                value,
                config.getTargetEntityType(),
                10  // limit
            );
            
            // 判断匹配状态
            ReconRowResult rowResult = evaluateMatch(candidates, config);
            rowResult.setRowIndex(rowIndex);
            rowResult.setOriginalValue(value);
            
            details.add(rowResult);
        }
        
        // 4. 计算统计信息
        ReconStatistics statistics = calculateStatistics(details);
        
        // 5. 存储结果
        MetaReconciliationResult result = new MetaReconciliationResult();
        result.setConfigId(config.getId());
        result.setExecuteTime(Instant.now());
        result.setStatistics(statistics);
        result.setDetails(details);
        
        resultRepository.save(result);
        
        return result;
    }
    
    /**
     * 评估匹配结果
     */
    private ReconRowResult evaluateMatch(List<ReconCandidate> candidates, 
                                         MetaReconciliationConfig config) {
        ReconRowResult result = new ReconRowResult();
        
        if (candidates.isEmpty()) {
            result.setStatus("unmatched");
            result.setCandidates(Collections.emptyList());
        } else if (candidates.size() == 1) {
            ReconCandidate best = candidates.get(0);
            if (best.getScore() >= config.getAutoMatchThreshold()) {
                result.setStatus("matched");
                result.setSelectedId(best.getEntityId());
            } else {
                result.setStatus("multiple");
                result.setCandidates(candidates);
            }
        } else {
            ReconCandidate best = candidates.get(0);
            if (best.getScore() >= config.getAutoMatchThreshold()) {
                result.setStatus("matched");
                result.setSelectedId(best.getEntityId());
            } else {
                result.setStatus("multiple");
                result.setCandidates(candidates);
            }
        }
        
        return result;
    }
}
```

### 3.3 人工确认流程

```java
public class ReconciliationManualConfirm {
    
    /**
     * 人工确认匹配
     */
    public void confirmMatch(Long resultId, int rowIndex, String selectedEntityId) {
        // 1. 加载结果
        MetaReconciliationResult result = resultRepository.findById(resultId);
        ReconRowResult rowResult = result.getDetails().get(rowIndex);
        
        // 2. 更新状态
        rowResult.setStatus("manual");
        rowResult.setSelectedId(selectedEntityId);
        
        // 3. 保存
        resultRepository.save(result);
        
        // 4. 缓存实体（可选）
        cacheEntity(selectedEntityId);
    }
    
    /**
     * 批量确认
     */
    public void batchConfirm(Long resultId, Map<Integer, String> selections) {
        MetaReconciliationResult result = resultRepository.findById(resultId);
        
        for (Map.Entry<Integer, String> entry : selections.entrySet()) {
            int rowIndex = entry.getKey();
            String entityId = entry.getValue();
            
            ReconRowResult rowResult = result.getDetails().get(rowIndex);
            rowResult.setStatus("manual");
            rowResult.setSelectedId(entityId);
        }
        
        resultRepository.save(result);
    }
}
```

---

## 四、属性扩展

### 4.1 扩展配置

```json
{
  "columnName": "company_name",
  "entityType": "Q4830453",
  "expandProperties": [
    {"propertyId": "P17", "propertyName": "country", "targetColumn": "country"},
    {"propertyId": "P112", "propertyName": "founded by", "targetColumn": "founder"},
    {"propertyId": "P159", "propertyName": "headquarters", "targetColumn": "headquarters"}
  ]
}
```

### 4.2 扩展执行

```java
public class PropertyExpander {
    
    /**
     * 扩展实体属性
     */
    public void expandProperties(MetaReconciliationResult result, 
                                  MetaReconciliationConfig config) {
        for (ReconRowResult rowResult : result.getDetails()) {
            if ("matched".equals(rowResult.getStatus()) || 
                "manual".equals(rowResult.getStatus())) {
                
                // 获取实体详情
                ReconEntity entity = fetchEntity(rowResult.getSelectedId());
                
                // 扩展属性
                Map<String, String> expandedData = new HashMap<>();
                for (ReconColumnConfig columnConfig : config.getColumns()) {
                    for (ReconPropertyConfig propertyConfig : columnConfig.getExpandProperties()) {
                        String value = entity.getPropertyValue(propertyConfig.getPropertyId());
                        if (value != null) {
                            expandedData.put(propertyConfig.getTargetColumn(), value);
                        }
                    }
                }
                
                rowResult.setExpandedData(expandedData);
            }
        }
    }
}
```

---

## 五、匹配策略

### 5.1 内置匹配策略

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| **exact** | 精确匹配 | ID、编码 |
| **fuzzy** | 模糊匹配 | 名称、描述 |
| **phonetic** | 语音匹配 | 人名、地名 |
| **semantic** | 语义匹配 | 含义相似的文本 |

### 5.2 匹配策略配置

```json
{
  "matchStrategy": "fuzzy",
  "matchParams": {
    "algorithm": "levenshtein",
    "threshold": 0.8,
    "ignoreCase": true,
    "ignoreDiacritics": true
  }
}
```

### 5.3 匹配策略实现

```java
public interface MatchStrategy {
    
    /**
     * 计算两个字符串的匹配分数
     */
    double calculateScore(String value1, String value2, Map<String, Object> params);
}

public class FuzzyMatchStrategy implements MatchStrategy {
    
    @Override
    public double calculateScore(String value1, String value2, Map<String, Object> params) {
        String algorithm = (String) params.getOrDefault("algorithm", "levenshtein");
        boolean ignoreCase = (boolean) params.getOrDefault("ignoreCase", true);
        
        if (ignoreCase) {
            value1 = value1.toLowerCase();
            value2 = value2.toLowerCase();
        }
        
        switch (algorithm) {
            case "levenshtein":
                return levenshteinSimilarity(value1, value2);
            case "jaro":
                return jaroSimilarity(value1, value2);
            case "jaccard":
                return jaccardSimilarity(value1, value2);
            default:
                return levenshteinSimilarity(value1, value2);
        }
    }
}
```

---

## 六、GraphQL API

### 6.1 查询

```graphql
type Query {
  # 对账配置查询
  metaReconciliationConfig(id: ID!): MetaReconciliationConfig
  metaReconciliationConfigs(
    filter: MetaReconciliationConfigFilter
    limit: Int
    offset: Int
  ): MetaReconciliationConfigConnection!
  
  # 对账结果查询
  metaReconciliationResult(id: ID!): MetaReconciliationResult
  metaReconciliationResults(
    configId: ID
    tableId: ID
    limit: Int
    offset: Int
  ): MetaReconciliationResultConnection!
  
  # 外部实体查询
  metaReconciliationEntity(entityId: String!): MetaReconciliationEntity
  searchReconciliationEntities(
    query: String!
    entityType: String
    limit: Int
  ): [MetaReconciliationEntity!]!
  
  # 对账统计
  getReconciliationStatistics(configId: ID!): ReconciliationStatistics!
}
```

### 6.2 变更

```graphql
type Mutation {
  # 对账配置变更
  createReconciliationConfig(input: CreateReconciliationConfigInput!): MetaReconciliationConfig!
  updateReconciliationConfig(id: ID!, input: UpdateReconciliationConfigInput!): MetaReconciliationConfig!
  deleteReconciliationConfig(id: ID!): Boolean!
  
  # 对账执行
  executeReconciliation(configId: ID!): MetaReconciliationResult!
  
  # 人工确认
  confirmReconciliationMatch(resultId: ID!, rowIndex: Int!, entityId: String!): Boolean!
  batchConfirmReconciliationMatches(resultId: ID!, selections: [ReconciliationSelection!]!): Boolean!
  
  # 属性扩展
  expandReconciliationProperties(resultId: ID!): MetaReconciliationResult!
}
```

---

## 七、与 OpenRefine 的对比

| 能力 | OpenRefine | nop-metadata |
|------|-----------|-------------|
| 对账配置 | JSON 配置 | MetaReconciliationConfig |
| 对账结果 | 内存存储 | MetaReconciliationResult |
| 外部实体 | 实时查询 | MetaReconciliationEntity（缓存） |
| 匹配策略 | 内置 | 可扩展 |
| 人工确认 | UI 操作 | GraphQL API |

### nop-metadata 的优势

1. **持久化存储**: 对账结果和配置持久化到数据库
2. **实体缓存**: 外部实体缓存到本地，减少 API 调用
3. **GraphQL 接口**: 统一的查询和变更接口
4. **可扩展策略**: 支持自定义匹配策略

---

## 八、应用场景

### 8.1 主数据标准化

```graphql
mutation {
  executeReconciliation(configId: "company-recon-config") {
    statistics {
      totalRows
      matchedRows
      matchRate
    }
  }
}
```

### 8.2 数据增强

```graphql
mutation {
  expandReconciliationProperties(resultId: "result-123") {
    details {
      originalValue
      selectedId
      expandedData
    }
  }
}
```

### 8.3 去重检测

```graphql
query {
  searchReconciliationEntities(query: "Microsoft", limit: 10) {
    entityId
    entityName
    entityType
    score
  }
}
```

---

## Open Questions

- [ ] Reconciliation 服务是否需要支持认证？
- [ ] 对账结果是否需要支持版本化？
- [ ] 是否需要支持流式对账（大数据量场景）？
- [ ] 外部实体缓存是否需要定时刷新？
