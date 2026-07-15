# OpenRefine 深度分析报告

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计参考
> Goal: 分析 OpenRefine 的数据清洗和转换模式，为 nop-metadata 提供设计参考
> Last Updated: 2026-07-15 (源码深度分析)

## Context

OpenRefine（原 Google Refine）是一个强大的数据清洗和转换工具，专注于非结构化和脏数据的处理。它提供基于操作的数据转换、数据对齐和数据扩展能力，适合数据预处理场景。

## 核心架构

### 1. 组件架构

```
┌─────────────────────────────────────────────────────┐
│                OpenRefine Web UI                     │
│              (Java Servlet + GWT)                    │
└──────────────────────┬──────────────────────────────┘
                       │ REST API
┌──────────────────────▼──────────────────────────────┐
│              OpenRefine Core                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ Project      │  │ Operations  │  │ History   │ │
│  │ (数据项目)   │  │ (操作)       │  │ (历史)    │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         └─────────────────┼────────────────┘       │
│  ┌────────────────────────▼───────────────────────┐ │
│  │        Transforms (转换引擎)                     │ │
│  │  Text, Numeric, Date, Facet, Cluster          │ │
│  └────────────────────────┬───────────────────────┘ │
└───────────────────────────┼─────────────────────────┘
                            │
            ┌───────────────┼───────────────┐
            │               │               │
┌───────────▼──────┐ ┌─────▼─────┐ ┌───────▼──────┐
│   Data Sources   │ │  Project  │ │  Extensions  │
│  (CSV/JSON/      │ │  Store    │ │  (Reconcile) │
│   Excel/...)     │ │           │ │              │
└──────────────────┘ └───────────┘ └──────────────┘
```

### 2. 核心概念

- **Project**: 数据项目，包含数据和操作历史
- **Operation**: 数据转换操作（如去重、合并、拆分）
- **History**: 操作历史，支持撤销和重做
- **Facet**: 数据切面，用于过滤和分组

## 核心设计模式

### 模式 1：Project 数据模型

**关键文件:**
- `main/src/main/java/com/google/refine/model/Project.java`

**Project 结构:**

```java
public class Project {
    private long id;
    private String name;
    private Metadata metadata;
    
    // 数据
    private List<Column> columns;
    private List<Row> rows;
    
    // 操作历史
    private History history;
    
    // 索引
    private Map<String, ColumnModel> columnModelMap;
}

public class Row {
    private long id;
    private List<Cell> cells;
    
    public Cell getCell(int columnIndex) {
        return cells.get(columnIndex);
    }
}

public class Cell {
    private Object value;      // 单元格值
    private Object error;      // 错误信息
    private ReconResult recon; // 对账结果（可选）
}
```

**设计优势:**
- **内存数据模型**: 数据存储在内存中
- **行级粒度**: 每行独立管理
- **可扩展单元格**: 支持错误和对账信息

---

### 模式 2：Operation 操作模型

**关键文件:**
- `main/src/main/java/com/google/refine/operations/`

**Operation 类型:**

```java
// 文本操作
public class TextTransformOperation extends AbstractOperation {
    private String columnName;
    private Expression expression;
    private boolean onErrorKeepOriginal;
}

// 数值操作
public class NumberTransformOperation extends AbstractOperation {
    private String columnName;
    private String expression;
}

// 日期操作
public class DateTimeTransformOperation extends AbstractOperation {
    private String columnName;
    private String expression;
    private String outputFormat;
}

// 行操作
public class RowRetainOperation extends AbstractOperation {
    private Expression expression;  // 保留满足条件的行
}

public class RowReorderOperation extends AbstractOperation {
    private String sortColumn;
    private boolean reverse;
}

// 列操作
public class ColumnSplitOperation extends AbstractOperation {
    private String columnName;
    private String separator;
    private int numberOfFields;
    private boolean removeOriginalColumn;
}

public class ColumnMergeOperation extends AbstractOperation {
    private List<String> columnNames;
    private String separator;
    private String newColumnName;
}
```

**Operation 执行:**

```java
public abstract class AbstractOperation {
    
    public ProcessResult createProcess(GContext context) {
        // 创建处理过程
        return new Process(this, context);
    }
    
    public void validate() {
        // 验证操作参数
    }
}

public class Process {
    private Operation operation;
    private ProcessResult result;
    
    public void execute(Project project) {
        // 1. 记录操作前状态
        project.getHistory().push(operation);
        
        // 2. 执行操作
        result = operation.apply(project);
        
        // 3. 更新项目
        project.update(result);
    }
    
    public void undo(Project project) {
        // 撤销操作
        project.getHistory().pop();
        project.restore();
    }
}
```

**设计优势:**
- **操作抽象**: 统一的操作接口
- **可撤销**: 支持操作历史和撤销
- **组合操作**: 操作可以组合成工作流

---

### 模式 3：History 操作历史

**关键文件:**
- `main/src/main/java/com/google/refine/history/History.java`

**History 结构:**

```java
public class History {
    private List<Process> processes;
    private int currentIndex;
    
    public void push(Operation operation) {
        // 添加新操作
        Process process = new Process(operation);
        processes.add(process);
        currentIndex = processes.size() - 1;
    }
    
    public Process pop() {
        // 移除最后一个操作
        Process process = processes.remove(currentIndex);
        currentIndex--;
        return process;
    }
    
    public void undo() {
        // 撤销当前操作
        if (currentIndex >= 0) {
            Process process = processes.get(currentIndex);
            process.undo();
            currentIndex--;
        }
    }
    
    public void redo() {
        // 重做下一个操作
        if (currentIndex < processes.size() - 1) {
            currentIndex++;
            Process process = processes.get(currentIndex);
            process.redo();
        }
    }
    
    public List<Operation> getOperations() {
        // 获取所有操作列表
        return processes.stream()
            .map(Process::getOperation)
            .collect(Collectors.toList());
    }
}
```

**History 序列化:**

```json
{
  "history": [
    {
      "operation": {
        "op": "core/text-transform",
        "description": "文本转换",
        "engineConfig": "cross",
        "columnName": "amount",
        "expression": "value.replace('$', '')",
        "onError": "keep-original"
      }
    },
    {
      "operation": {
        "op": "core/number-transform",
        "description": "数值转换",
        "columnName": "amount",
        "expression": "toNumber(value)"
      }
    }
  ],
  "currentIndex": 1
}
```

**设计优势:**
- **完整历史**: 记录所有操作
- **撤销/重做**: 支持操作回退
- **序列化**: 操作历史可保存和恢复

---

### 模式 4：Facet 数据切面

**关键文件:**
- `main/src/main/java/com/google/refine/facets/`

**Facet 类型:**

```java
// 文本切面
public class ListFacet extends AbstractFacet {
    private String columnName;
    private Map<String, Integer> counts;  // 值 → 计数
    private Set<String> selected;         // 选中的值
}

// 数值切面
public class RangeFacet extends AbstractFacet {
    private String columnName;
    private double min;
    private double max;
    private List<Range> ranges;
}

// 时间切面
public class TimelineFacet extends AbstractFacet {
    private String columnName;
    private Instant start;
    private Instant end;
    private List<TimeRange> ranges;
}
```

**Facet 查询:**

```json
// Facet 定义
{
  "facets": [
    {
      "type": "list",
      "name": "status",
      "columnName": "status",
      "expression": "value",
      "selection": ["completed", "pending"]
    },
    {
      "type": "range",
      "name": "amount",
      "columnName": "amount",
      "expression": "value",
      "select": {
        "min": 100,
        "max": 10000
      }
    }
  ]
}

// Facet 结果
{
  "facets": [
    {
      "name": "status",
      "choices": [
        {"v": {"v": "completed", "l": "completed"}, "c": 5000},
        {"v": {"v": "pending", "l": "pending"}, "c": 2000},
        {"v": {"v": "cancelled", "l": "cancelled"}, "c": 300}
      ]
    }
  ]
}
```

**设计优势:**
- **交互式过滤**: 支持多维度过滤
- **实时计算**: 切面实时更新
- **可视化**: 支持切面可视化

---

### 模式 5：Reconciliation 对账（核心能力）

**关键文件:**
- `modules/core/src/main/java/com/google/refine/model/recon/`
- `extensions/wikibase/`

**Reconciliation 是 OpenRefine 的核心能力**，用于将数据与外部知识库（如 Wikidata、VIAF、Library of Congress）进行匹配和对齐。

**Reconciliation 模型:**

```java
public class Recon {
    private String id;                    // 匹配的实体 ID
    private String name;                  // 实体名称
    private double score;                 // 匹配置信度
    private ReconJudgment judgment;       // 判断结果
    private ReconType type;               // 实体类型
    private List<ReconCandidate> candidates;  // 候选匹配
}

public enum ReconJudgment {
    MATCHED,       // 自动匹配（置信度高）
    UNMATCHED,     // 未匹配
    MANUAL,        // 人工确认
    SKIPPED        // 跳过
}

public class ReconCandidate {
    private String id;        // 候选实体 ID
    private String name;      // 候选实体名称
    private double score;     // 候选置信度
    private ReconType type;   // 候选实体类型
    private Map<String, Object> properties;  // 候选实体属性
}
```

**Reconciliation 配置:**

```json
{
  "reconciliation": {
    "service": "wikidata",
    "name": "Wikidata Reconciliation",
    "identifierSpace": "http://www.wikidata.org/entity/",
    "schemaSpace": "http://www.wikidata.org/prop/direct/",
    "url": "https://tools.wmflabs.org/openrefine-wikidata/en/api",
    "suggest": {
      "entity": {
        "service_url": "https://tools.wmflabs.org/openrefine-wikidata",
        "service_path": "/en/suggest/entity"
      },
      "type": {
        "service_url": "https://tools.wmflabs.org/openrefine-wikidata",
        "service_path": "/en/suggest/type"
      }
    },
    "autoMatch": true,
    "autoMatchThreshold": 0.9
  }
}
```

**Reconciliation 执行流程:**

```java
public class ReconOperation extends AbstractOperation {
    
    public void perform(Project project) {
        // 1. 获取列值
        Column column = project.getColumnByName(columnName);
        
        // 2. 批量发送到 Reconciliation 服务
        List<ReconCandidate> candidates = reconcileBatch(
            project, column, serviceUrl, typeID
        );
        
        // 3. 自动匹配高置信度结果
        for (int rowIndex = 0; rowIndex < project.rows.size(); rowIndex++) {
            Row row = project.rows.get(rowIndex);
            Cell cell = row.getCell(column.getCellIndex());
            
            if (cell != null && candidates.get(rowIndex) != null) {
                ReconCandidate best = candidates.get(rowIndex);
                
                if (best.score >= autoMatchThreshold) {
                    // 自动匹配
                    Recon recon = new Recon();
                    recon.id = best.id;
                    recon.name = best.name;
                    recon.score = best.score;
                    recon.judgment = ReconJudgment.MATCHED;
                    recon.type = best.type;
                    
                    row.setCell(column.getCellIndex(), 
                        new Cell(cell.value, recon));
                }
            }
        }
    }
}
```

**Reconciliation 用途:**

| 用途 | 说明 | 示例 |
|------|------|------|
| **实体对齐** | 将本地数据与外部知识库对齐 | 公司名称 → Wikidata 实体 |
| **属性扩展** | 基于匹配结果扩展属性 | 匹配公司后获取地址、行业等 |
| **数据标准化** | 标准化实体名称和类型 | "US" → "United States" |
| **去重检测** | 识别相同实体的不同表述 | "IBM" vs "International Business Machines" |

**Reconciliation API:**

```
POST /reconcile
{
  "query": "Microsoft",
  "type": "Q4830453",  // 公司类型
  "limit": 5
}

Response:
{
  "result": [
    {
      "id": "Q2283",
      "name": "Microsoft",
      "type": ["Q4830453"],
      "score": 0.95
    }
  ]
}
```

**设计优势:**
- **外部知识库集成**: 支持 Wikidata、VIAF 等标准 Reconciliation API
- **自动匹配**: 高置信度结果自动匹配
- **人工确认**: 低置信度结果支持人工确认
- **属性扩展**: 匹配后可扩展实体属性

---

## 对 nop-metadata 的启示

### 可借鉴的设计

1. **Reconciliation 对账**（核心能力）: 实体与外部知识库的匹配和对齐
2. **Operation 操作模型**: 声明式数据转换操作
3. **History 操作历史**: 完整的操作历史和撤销/重做
4. **Facet 数据切面**: 交互式数据过滤和分组

### 与 nop-metadata 的对比

| 能力 | OpenRefine | nop-metadata |
|------|-----------|-------------|
| **实体对账** | Reconciliation（核心） | MetaReconciliation（新增） |
| 数据转换 | Operation 模型 | 扩展支持 |
| 操作历史 | History | 扩展支持 |
| 数据过滤 | Facet | 查询条件 |

### nop-metadata 可复用的模式

1. **Reconciliation 模式**: 实体与外部知识库的匹配（Wikidata、VIAF 等）
2. **Operation 模式**: 数据转换操作的标准化
3. **History 模式**: 操作历史和版本管理
4. **Facet 模式**: 数据切面和过滤

### Reconciliation 在 nop-metadata 中的应用

| 应用场景 | 说明 | 示例 |
|---------|------|------|
| **主数据对齐** | 将本地主数据与外部标准对齐 | 公司名称 → 工商注册信息 |
| **术语标准化** | 标准化业务术语 | "客户" → "customer" |
| **数据增强** | 基于匹配结果扩展属性 | 匹配产品后获取分类、描述 |
| **去重检测** | 识别相同实体的不同表述 | "IBM" vs "International Business Machines" |

## Open Questions

- [ ] nop-metadata 是否需要支持 Reconciliation 对账？
- [ ] 实体对账是否需要与 MetaEntity 集成？
- [ ] Reconciliation 服务是否需要可插拔？
- [ ] 对账结果是否需要版本化？

## References

- [OpenRefine GitHub](https://github.com/OpenRefine/OpenRefine)
- [OpenRefine 文档](https://openrefine.org/documentation.html)
- 源码: `main/src/main/java/com/google/refine/model/`
- 源码: `main/src/main/java/com/google/refine/operations/`
