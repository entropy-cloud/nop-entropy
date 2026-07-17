# 数据质量与数据探索工具源码对比分析：OpenRefine / Great Expectations / Apache Griffin / dbt / PandasAI vs Nop Platform

> Status: open
> Date: 2026-07-17
> Scope: data-quality, data-transformation, data-profiling, ai-integration
> Conclusion: TBD — 待决策是否将关键设计模式映射到 Nop 平台

## Context

从 OpenRefine 数据清洗任务出发，逐步扩展到对五个开源数据工具进行源码级深度分析。目标是：

1. 理解每个工具的核心架构和设计模式
2. 评估 Nop 平台现有模块（nop-batch / nop-stream / nop-dataset / nop-ai / nop-rule / nop-tablesaw）是否具备同类能力
3. 识别差距，决定是否需要将关键模式映射到 Nop 中

五个工具横跨三个领域：**交互式数据清洗（OpenRefine）**、**数据质量验证（Great Expectations / Apache Griffin）**、**数据转换管线（dbt）**、**AI 驱动的数据探索（PandasAI）**。

## Before You Read

> 本文档中的所有分析基于 sub-agent 对五个开源工具源码的直接阅读。如需要原始 sub-agent 输出，请查看对话日志中五个 deep-dive 任务的返回内容。

---

## 1. OpenRefine — 交互式数据清洗

### 源码结构（Java + JavaScript）

```
main/webapp/modules/core/
├── parsing/          # GREL 解析器
│   ├── Scanner.java  # 手写字符级扫描器
│   ├── Parser.java   # 递归下降解析器 → AST
│   ├── Evaluable.java # AST 节点接口（解释器模式）
│   ├── ExpressionUtils.java
│
├── operations/       # 数据操作命令
│   ├── ReconOperation.java
│   ├── ColumnAddition.java
│   ├── TextTransform.java
│   ├── MassEdit.java
│   └── ...30+ 个 Operation 子类
│
├── expr/             # GREL 函数
│   ├── HasFields.java
│   ├── functions/
│   │   ├── strings/  # GREL 字符串函数
│   │   ├── regex/    # 正则匹配
│   │   ├── date/     # 日期函数
│   │   └── ...
│   └── ControlFunctionRegistry.java  # 函数注册表
│
├── model/            # 数据模型
│   ├── Project.java  # 项目（= 一个数据集 + history）
│   ├── ColumnModel.java
│   ├── Row.java      # 行（cells + flagged + starred）
│   └── Cell.java     # 单元格（value + recon）
│
├── history/          # 历史/撤销模型
│   ├── History.java
│   ├── HistoryEntry.java
│   └── Process.java  # 命令序列
│
└── clustering/       # 聚类去重
    ├── Clusterer.java
    ├── Keyer.java
    ├── NearestPoint.java
    ├── KeyCollision.java
    ├── kNN.java      # k-近邻
    └── ...20+ 个聚类算法文件
```

### 核心设计模式

| 模式 | 实现方式 |
|------|---------|
| **表达式解析** | 手写递归下降解析器，`Scanner` 做字符级 tokenize，`Parser` 构建 AST（`Evaluable`），解释器模式执行 |
| **函数注册** | `ControlFunctionRegistry` 静态注册表，双重映射：名字 → 类 + `inner` → 外层 |
| **操作命令（Command Pattern）** | 每个 Operation 子类实现单一数据清洗动作，独立可序列化 |
| **历史/撤销（Memento Pattern）** | `Process` = 不可变命令队列，每个 `HistoryEntry` 包含 `JSON` 形式的增量变更 |
| **聚类（Strategy Pattern）** | 统一 `Clusterer` 接口，`kNN` / `KeyCollision` / `NearestPoint` 可插拔 |
| **列上下文无副作用** | 变换 = `(row, column, expression) → new cell value`，pure function |

### GREL 示例

```
value.trim().toUpperCase().replace("\\s+", " ").split(";")[0]
```

→ 解析树：`GetField(Value)` → `Call(trim)` → `Call(toUpperCase)` → `Call(replace)` → `Call(split)` → `Index(index=0)`

### 关键代码路径

```
UI click "Transform" → POST /command/core/apply-operations
  → ProcessManager.queueProcess(Process(operations))
  → for each HistoryEntry: entry.apply(project)
  → project.history.addEntry(entry)
  → ProjectManager.save(project)  // JSON 序列化到磁盘
```

### 差距分析 vs Nop

| 维度 | OpenRefine | Nop 平台 | 差距 |
|------|-----------|----------|------|
| GREL 表达式 | 单元格级 pipe-chain 函数式语言 | XLang 是通用表达式，但缺少 OpenRefine 的按列 apply 模式 | **存在**：XLang 是通用表达式，但无 `value.xxx()` 的单元格隐式上下文，无 `transform(col, expr)` 的按列推理 |
| 操作命令模型 | 30+ 种序列化 Operation | `TableFlowFunctions` 实现了 11 个函数，但无命令序列化/undo | **部分已填补**：功能接近，缺少 undo 历史 |
| 历史/撤销 | 每个变更记录 JSON 增量，可回退到任意点 | 无对应机制 | **存在** |
| 聚类去重 | 20+ 聚类算法，可扩展 | 仅有 `clusterKeyCollision` / `clusterNearestNeighbor` 两个 | **部分已填补**：基本够用，缺少 Soundex / Metaphone / colognePhonetic 等 |
| 面（facet） | 交互式筛选 + 分布分析 | `facetValue` / `facetNumeric` 已实现 | **已填补** |
| 导出 | TSV / CSV / Excel / Wikidata | 通过 Tablesaw API | **已填补** |

### 可借鉴的设计

1. **Operation 序列化** — 将数据清洗动作建模为序列化命令（JSON），可被 AI 生成、回放、审计
2. **Process + HistoryEntry 增量模式** — 每个 transform 记录 diff 而非全量数据，支持 time-travel
3. **按列隐式上下文** — `transform(col, expr)` 中 expr 隐式引用当前单元格值 `value`

---

## 2. Great Expectations — 数据质量验证

### 源码结构（Python）

```
great_expectations/
├── expectations/        # Expectation 定义（~120 个）
│   ├── expectation_configuration.py
│   ├── expectation_validation_result.py
│   └── metrics/         # 度量计算
│
├── core/                # 核心模型
│   ├── expectation_configuration.py
│   ├── expectation_suite.py
│   └── run_identifier.py
│
├── render/              # Data Docs 渲染
│   ├── renderer/
│   ├── view/
│   └── DefaultJinjaPageView.py
│
├── profile/             # Profiler
│   ├── BasicSuiteBuilderProfiler.py
│   ├── UserConfigurableProfiler.py
│   └── columns/
│       └── ColumnProfilerCardinality.py
│
├── validator/           # 验证引擎
│   └── validator.py
│
├── rule_based_profiler/ # Rule-Based Profiler
│   └── ...（较新特性）
│
├── datasource/          # 数据源
│   ├── new_datasource/
│   └── ...
│
└── checkpoint/          # Checkpoint 运行器
```

### 核心设计模式

| 模式 | 实现方式 |
|------|---------|
| **Expectation 声明式验证** | `expect_column_to_not_be_null("age")` → 自动转成 SQL/Pandas/Spark 查询 |
| **三后端** | `ExecutionEngine` 抽象：Pandas / Spark / SQLAlchemy，同一 Expectation 跨引擎执行 |
| **Profiler → Suite** | `BasicSuiteBuilderProfiler` → 自动检测数据类型（int/str/date）→ 生成 batch ExpectationSuite |
| **ExpectationSuite** | `ExpectationConfiguration[]` + `meta` + `ge_cloud_id`，可序列化为 JSON |
| **Checkpoint** | 运行配置：datasource + suite + batch request，输出 `CheckpointResult` |
| **Data Docs** | Jinja 模板渲染 HTML 报告，覆盖验证结果 |
| **Rule-Based Profiler** | 用 YAML 规则定义 profiling 逻辑（较新），比 BasicProfiler 更灵活 |

### 关键代码路径

```
checkpoint.run() →
  Validator.graph_validate(expectations) →
    for each expectation: resolve_metric(metric) →
      ExecutionEngine.resolve_metric() → SQL/Pandas/Spark query →
    ExpectationValidationResult(status=PASS/FAIL/ERROR, result={...}) →
  CheckpointResult(success=True/False, run_results={...})
```

### Profiler 设计

`BasicSuiteBuilderProfiler` 工作流：

```
profiler.profile(batch) →
  columns = batch.columns()
  for col in columns:
    type = infer_column_type(col)  # card based on value count
    if numeric: add expect_column_mean_to_be_between, etc.
    if string: add expect_column_value_lengths_to_be_between, etc.
    if categorical (card < threshold):
      add expect_column_values_to_be_in_set(known_values)
```

### 差距分析 vs Nop

| 维度 | Great Expectations | Nop 平台 | 差距 |
|------|-------------------|----------|------|
| **声明式断言** | `expect_column_not_to_be_null` = 可读性极好 | `RuleModel` 可表达 null 检查、range 验证，但无标准断言库 | **部分不存在**：nop-rule 可以表达验证逻辑，缺少标准化的断言命名和目录 |
| **跨引擎验证** | 同一 Expectation 在 Pandas/Spark/SQLAlchemy 上运行 | Nop 的 `IDataSet` 是统一的记录抽象，但无验证引擎 | **存在** |
| **Profiler 自动生成** | 自动检测列类型 → 生成 ExpectationSuite | 无对应机制 | **存在** |
| **验证结果报告** | Data Docs HTML 报告 | 无标准验证报告格式 | **存在** |
| **Checkpoint 编排** | 可配置的验证运行器 | `BatchTaskBuilder` 可编排，但无专门的验证 checkpoint | **可复用**：batch 管线可包装为验证引擎 |
| **Rule-Based Profiler** | YAML 驱动的 profiling 规则 | nop-rule 本身就是 YAML 规则引擎，天然适合此模式 | **直接匹配**：nop-rule 的决策表/决策树可直接映射 |

### 可借鉴的设计

1. **Expectation 命名惯例** — `expect_<column>_to_<condition>` 的断言 API 比裸规则定义更易理解
2. **Profiler 自动发现** — 自动检测列语义 → 生成验证规则 = 解决 "用户不想手写规则" 的问题
3. **三引擎抽象** — `ExecutionEngine` 接口使验证逻辑与计算引擎解耦（Nop 已有 `IDataSet` 但缺少验证层）

---

## 3. Apache Griffin — 数据质量监控

### 源码结构（Scala + Spark）

```
griffin/
├── measure/              # 质量度量
│   ├── Measure.scala     # 核心特质
│   ├── config/           # 配置解析
│   ├── AccuracyMeasure.scala
│   ├── CompletenessMeasure.scala
│   ├── DuplicationMeasure.scala
│   ├── ProfilingMeasure.scala
│   ├── SparkSQLMeasure.scala
│   ├── SchemaConformanceMeasure.scala
│   ├── DistinctnessMeasure.scala
│   └── ...
│
├── job/                  # 批/流作业
│   ├── BatchGriffin.scala     # 批处理主入口
│   ├── StreamingGriffin.scala # 流处理主入口
│   └── ...
│
├── context/              # 运行上下文（SparkSession 封装）
│
├── sink/                 # 输出
│   ├── ElasticSearchSink.scala
│   ├── HdfsSink.scala
│   └── ...
│
├── measure/transformations/  # 数据预处理
│
├── info/                 # 指标收集
│
└── configuration/        # 配置管理
    ├── env/
    └── dq/
```

### 核心设计模式

| 模式 | 实现方式 |
|------|---------|
| **Measure 特质 + 七种子类** | 所有度量继承 `Measure` trait，返回 `(metricsDF, recordsDF)` 元组 |
| **批/流双模式** | `BatchGriffin` / `StreamingGriffin` 共享 Measure，仅在作业调度层面区分 |
| **Spark SQL 作为度量引擎** | `SparkSQLMeasure` 直接执行用户输入的 Spark SQL，最灵活 |
| **Sink 抽象** | 度量结果→Elasticsearch/HDFS/自定义，`Sink` 接口 |
| **双输出** | 每个 Measure 同时输出 `metricsDF`（数字指标）+ `recordsDF`（标记异常行） |
| **配置驱动** | 所有 job/measure 配置在 JSON 中声明，运行时解析 |

### 七种 Measure

| Measure | 功能 |
|---------|------|
| **Accuracy** | 源 vs 目标表精确匹配（主键 join + 逐字段对比） |
| **Completeness** | 非 null 记录比例（可指定列） |
| **Duplication** | 重复记录计数 + 示例 |
| **Profiling** | 列的 min/max/avg/stddev/distinct count |
| **SparkSQL** | 用户自定义 Spark SQL 度量 |
| **SchemaConformance** | 检查列数据类型是否符合预期 |
| **Distinctness** | 组合列的唯一性度量 |

### 关键数据流

```
BatchGriffin.main() →
  Load config JSON (env + dq) →
  SparkSession.builder() →
  for each measure in dq.measures:
    measure.run(dataSource) →
    (metricsDF, recordsDF) →
    metricsDF.write → ElasticSearchSink (time-series dashboard)
    recordsDF.write → HdfsSink (异常行快照)
```

### 差距分析 vs Nop

| 维度 | Apache Griffin | Nop 平台 | 差距 |
|------|---------------|----------|------|
| **度量模型** | Measure trait + 7 种预定义 | nop-stream/nop-batch 有管线但无标准化度量 | **存在** |
| **双输出模式** | metrics + records 同时输出 | batch processor 可模拟（processor → dual consumer），但无标准模式 | **易填补**：用 `CompositeBatchConsumer` |
| **Spark 集成** | 直接构建在 Spark 上 | nop-stream 有类似 Flink 的窗口抽象，但无 Spark 后端 | **不同引擎选择**：Nop 不是 Spark 平台 |
| **流/批统一** | 同一 Measure 同时支持批和流 | nop-batch / nop-stream 各自独立，但共享 Measure 模式可以复用 | **可借鉴**：将度量逻辑从执行模式解耦 |
| **Sink 输出** | 可配置 ES/HDFS | nop-batch Consumer 链同样可配置 | **已覆盖** |
| **Accuracy 源-目标比对** | 专业 cross-dataset 比对 | 无原生支持，需手动实现 | **存在** |

### 可借鉴的设计

1. **Measure 特质 + 双输出模式** — `run() → (metricsDF, recordsDF)` 是数据质量度量的标准输出格式
2. **度量与执行引擎解耦** — 同一 Measure 可在批/流/交互三种模式下运行
3. **SparkSQLMeasure 的灵活性** — 允许用户输入任意 SQL 作为度量，类似 nop-rule 的自定义规则

---

## 4. dbt — 数据转换管线

### 源码结构（Python）

```
core/dbt/
├── adapter/           # 数据库适配器（Postgres/Redshift/BigQuery/Snowflake/DuckDB）
│   ├── base/
│   │   ├── connections.py
│   │   ├── relation.py
│   │   └── implementations/
│   ├── postgres/
│   ├── snowflake/
│   └── ...
│
├── parser/            # SQL/Jinja 解析
│   ├── models.py
│   ├── manifest.py    # Manifest 构建
│   └── schema_yaml_readers.py  # YAML 源定义解析
│
├── compilation/       # SQL 编译
│   ├── JinjaExt.py    # dbt 自定义 Jinja 扩展
│   └── ...
│
├── graph/             # DAG 图管理
│   ├── graph.py
│   ├── queue.py       # GraphQueue 拓扑排序
│   ├── build_node_edges.py
│   └── ...
│
├── runner/            # 运行器
│   ├── model.py       # ModelRunner
│   ├── seed.py
│   ├── snapshot.py
│   └── ...
│
├── task/              # CLI 任务
│   ├── run.py
│   ├── compile.py
│   ├── test.py
│   └── ...
│
├── contracts/         # 数据契约
│   ├── graph/
│   ├── manifest.py
│   └── project.py
│
├── contexts/          # Jinja 上下文
│   └── ...
│
└── semantic_layer/    # MetricFlow 语义层
```

### 核心设计模式

| 模式 | 实现方式 |
|------|---------|
| **模型即代码** | SQL + Jinja 模板 = 可复用数据转换，存放在 `models/` 目录 |
| **Jinja 编译管线** | Jinja 模板 → 编译为纯 SQL → 执行 → 表物化 |
| **物化策略** | `table` / `view` / `incremental` / `ephemeral` = 四种持久化模式 |
| **DAG 依赖解析** | `ref()` 函数解析模型间依赖 → `build_node_edges()` → `GraphQueue` 拓扑排序 |
| **Manifest 协议** | 编译后的全项目元数据 JSON 文件，包含 nodes / sources / macros / metrics |
| **适配器模式** | `BaseAdapter` 接口 → 每个数据库实现连接/SQL 方言/取消查询 |
| **Source Freshness** | `source` 定义 freshness 阈值 → 数据源时效性检查 |
| **数据测试** | `tests/` 下 SQL 或 `generic_test` 定义验证规则 |
| **语义层** | MetricFlow: `metrics.yml` → SQL 生成引擎 |

### 关键数据流

```
dbt run →
  parser.parse_models() → Manifest(nodes, sources, macros)
  → GraphQueue.from_manifest(manifest) 拓扑排序
  → for each model in topological order:
      compiler.compile_node(model.raw_sql) → materialized_sql
      runner.materialize(model, materialized_sql)
        → if table: CREATE TABLE AS SELECT ...
        → if incremental: MERGE / INSERT ... WHERE ...
        → if view: CREATE VIEW AS ...
```

### 差距分析 vs Nop

| 维度 | dbt | Nop 平台 | 差距 |
|------|-----|----------|------|
| **模型即代码** | SQL 文件 + `ref()` 依赖 | nop-batch 的 processor chain 可用类似模式 | **部分存在**：Nop 用 Java/XBiz 而不是 SQL+Jinja |
| **编译 → 物化** | Jinja → SQL → 执行 | 无 SQL 生成管线 | **架构不同**：Nop 不目标是 SQL 转换 |
| **DAG 依赖解析** | `GraphQueue` 拓扑排序 | nop-task / nop-wf 有 DAG 编排 | **已覆盖**：Nop 有更通用的任务编排 |
| **物化策略** | 四模式 | nop-batch 有 loader→processor→consumer 但非 SQL 物化 | **不同范式** |
| **Manifest** | 编译后的全项目元数据 | nop-xdef 有类似的模型注册表 | **可映射**：XDEF 模型注册表可以起类似作用 |
| **Source Freshness** | 数据时效性检查 | 无对应概念 | **存在** |
| **数据测试** | 自定义 SQL 测试 | nop-rule 决策表可做同类验证，但无 dbt 的 `tests/` 目录约定 | **部分存在** |
| **适配器层** | 多数据库适配器 | nop-dao 的 `IDialect` 支持多方言 | **已覆盖** |
| **增量模型（incremental）** | `is_incremental()` + `if not should_full_refresh` | nop-batch 的 `IBatchRecordHistoryStore` 可以去重实现增量 | **已覆盖**（不同实现） |
| **语义层** | MetricFlow | nop-dataset + Tablesaw 可以做度量计算，无语义层概念 | **存在** |

### 可借鉴的设计

1. **ref() 依赖声明** — 模型的依赖在 SQL 内通过 `ref('model_name')` 声明，隐式构建 DAG
2. **物化策略声明式切换** — developer 声明 `materialized='incremental'`，框架自动处理增量逻辑
3. **Manifest 作为编译产物的元数据** — 编译后生成全项目元数据供下游使用
4. **Source Freshness** — 数据源时效性检查是一个常见需求

---

## 5. PandasAI — AI 驱动的数据探索

### 源码结构（Python）

```
pandasai/
├── __init__.py
│
├── agent/              # Agent 实现
│   ├── agent.py        # Agent 基类
│   └── ...
│
├── smart_dataframe/    # 核心类
│   ├── smart_dataframe.py
│   └── ...
│
├── smart_datalake/     # 多 DataFrame 版本
│   └── smart_datalake.py
│
├── llm/                # LLM 适配器
│   ├── base.py
│   ├── OpenAI.py
│   ├── Anthropic.py
│   └── ...
│
├── connectors/         # 数据源连接器
│   ├── base.py
│   ├── pandas.py
│   ├── snowflake.py
│   └── ...
│
├── prompts/            # Prompt 模板
│   ├── base.py
│   └── ...
│
├── skills/             # 自定义技能
│   ├── base.py
│   └── ...
│
├── helpers/            # 工具函数
│   ├── cache.py
│   └── ...
│
└── shared/
    └── ...（包含 dataframe 序列化模板）
```

### 核心设计模式

| 模式 | 实现方式 |
|------|---------|
| **SmartDataFrame 子类化** | `SmartDataFrame` 继承 `DataFrame`，追加 `chat()` 方法 |
| **代码生成** | 非 tool-call。LLM 生成 Python 代码，`exec()` 执行 |
| **前 5 行 CSV 上下文** | 将 `df.head(5).to_csv()` 序列化后塞入 prompt，让 LLM 了解数据结构 |
| **Prompt 模板** | Jinja 模板 `shared/dataframe/defaults.jinja`，含列信息 + 前 5 行 + 用户问题 |
| **Security 层** | `allow_unsafe=False` 时用白名单允许的安全代码执行 |
| **Multiple LLM Dialects** | OpenAI / Anthropic / Ollama / ... → 统一 `LLM` 基类 |
| **Connectors** | 将不同数据源统一暴露为 pandas DataFrame |

### 关键数据流

```
SmartDataFrame.chat("show me the top 10 rows") →
  prompt = build_prompt(df.head(5).to_csv(), user_query)
  llm_response = llm.chat(prompt)  // 返回 Python 代码
  result = security.execute(llm_response)  // exec() with safety check
  → "Here are the top 10 rows: \n" + str(result)
```

### 差距分析 vs Nop

| 维度 | PandasAI | Nop 平台 | 差距 |
|------|---------|----------|------|
| **AI 数据探索** | SmartDataFrame.chat() | `IAiChatService` + `IAiChatFunctionTool` 可实现类似功能 | **部分已覆盖**：Nop 的 AI agent + tool 系统可作为 PandasAI 的替代 |
| **代码生成模式** | LLM 生成 Python，`exec()` 执行 | Nop 的 AI tool 模式是 function-calling（更安全） | **不同范式**：Tool-calling 比代码生成更可控 |
| **前 5 行上下文** | `head().to_csv()` 暴露给 LLM | Nop 的 `TableFlowFunctions.preview()` 可提供 | **已覆盖** |
| **DataFrame 子类化** | 直接继承，增加 chat() | Nop 的 Tablesaw + AI agent 集成更有优势（不依赖特定 class） | **Nop 更灵活** |
| **Connector 模式** | 统一连接器 → DataFrame | Nop 的 `IDataSet` + `DataSetToTableTransformer` 可做同样事 | **已覆盖** |
| **安全执行** | exec() with whitelist | Nop 的 tool-calling 没有代码执行安全问题 | **Nop 更安全** |

### 可借鉴的设计

1. **数据集前 N 行作为 LLM 上下文** — 将数据预览 + schema 信息格式化后注入 AI prompt
2. **安全沙箱** — 如果采用代码生成模式，需要白名单安全层

---

## 6. 综合对比

### 设计模式映射矩阵

每个工具提取的核心设计模式及其在 Nop 平台中的覆盖状态：

| 设计模式 | 来源工具 | Nop 覆盖状态 | 当前实现 |
|---------|---------|-------------|---------|
| 按列表达式上下文 | OpenRefine | ❌ 不存在 | — |
| 操作命令序列化+撤销 | OpenRefine | ❌ 不存在 | — |
| 聚类算法 | OpenRefine | ✅ 基本覆盖 | `TableFlowFunctions.cluster*` (2种) |
| 面/分布分析 | OpenRefine | ✅ 已覆盖 | `TableFlowFunctions.facet*` + `summary` + `missing` |
| 声明式断言 (Expectation) | Great Expectations | ⚠️ 部分存在 | `nop-rule` 可表达，但无标准断言命名 |
| Profiler 自动生成 | Great Expectations | ❌ 不存在 | — |
| 三引擎验证抽象 | Great Expectations | ⚠️ 部分存在 | `IDataSet` 是统一抽象，但无验证层 |
| 验证结果报告 | Great Expectations | ❌ 不存在 | — |
| Measure + 双输出模式 | Apache Griffin | ⚠️ 可复用 | batch Consumer 链可模拟 |
| 度量与执行引擎解耦 | Apache Griffin | ❌ 不存在 | nop-batch / nop-stream 各自独立 |
| SparkSQL 灵活度量 | Apache Griffin | ⚠️ 可复用 | nop-rule 的 XLang 表达式可做类似功能 |
| 流/批统一度量 | Apache Griffin | ❌ 不存在 | — |
| 模型即代码 (ref+DAG) | dbt | ⚠️ 部分存在 | nop-task DAG 编排但无 `ref()` SQL 依赖 |
| 物化策略声明式切换 | dbt | ❌ 不存在 | — |
| Manifest 编译元数据 | dbt | ⚠️ 部分存在 | nop-xdef 模型注册表功能类似 |
| Source Freshness | dbt | ❌ 不存在 | — |
| AI 代码生成执行 | PandasAI | ❌ 不存在（不同范式） | Nop 用 tool-calling 更安全 |
| 前 N 行作为 AI 上下文 | PandasAI | ✅ 已覆盖 | `TableFlowFunctions.preview()` |
| 多 LLM 方言 | PandasAI | ✅ 已覆盖 | `ILlmDialect` + 5 种实现 |

### 能力四象限

```ascii
                   声明式验证能力
                   ↑
          (Griffin) | ★ Great Expectations
           ★        |
      验证规则丰富   |   ★ Apache Griffin
                   |
───────────────────┼──────────────────────→  AI/NL 交互
                   |       能力
                   |
     dbt ★         |
  SQL 转换管线     |
                   |    ○ PandasAI
                   |       ★ OpenRefine
                   |
```

- **右上（GE + Griffin）**：声明式质量验证，Nop 缺少标准验证层
- **右下（OpenRefine + PandasAI）**：交互式数据探索 + AI，Nop 已开始覆盖（TableFlowFunctions + AI agent）
- **左上（dbt）**：SQL 转换管线，与 Nop 范式不同但管线编排能力有重叠
- Full stack 这四个象限中，**右上（声明式验证）是 Nop 最明显的缺口**

### 架构风格对比

| 维度 | OpenRefine | Great Expectations | Apache Griffin | dbt | PandasAI | Nop Platform |
|------|-----------|-------------------|---------------|-----|---------|------------|
| **语言** | Java + JS | Python | Scala (Spark) | Python | Python | Java 21 |
| **引擎** | In-memory | Pandas/Spark/SQLAlchemy | Spark | SQL (DB) | Pandas | IDataSet + Tablesaw |
| **用户接口** | Web UI | Python API / CLI | Spark Job / UI | CLI / dbt Cloud | Python | Java API / REST |
| **持久化** | JSON 项目文件 | Expectation Suite JSON | HDFS/ES | Schema + 模型文件 | 无状态 | ORM + XLang 模型 |
| **AI 集成** | 无原生 | 无原生 | 无原生 | dbt Copilot（商业） | **核心** | AI Agent + Tool |
| **扩展方式** | GREL + Operation | Expectation + Profiler | Measure + Sink | SQL + Jinja + Adapter | Skill + Connector | Delta + XBiz + XRule |

---

## 7. 可复用设计模式 — 候选列表

以下模式值得考虑映射到 Nop 平台，按优先级排序：

### P0 (高价值，可快速实现)

| 模式 | 来源 | 建议映射位置 | 预期工作量 |
|------|------|-------------|----------|
| **Measure + 双输出 (metrics + records)** | Griffin | nop-batch: 新增 `IBatchMetricsConsumer` 接口 | ~1d |
| **Profiler 自动生成** | GE | nop-tablesaw: 基于 `summary()` + `facet*()` 自动推断列语义 | ~2d |
| **操作命令序列化** | OpenRefine | nop-tablesaw: `TableFlowOperation` 序列化/反序列化 | ~2d |
| **前 N 行 + schema 注入 AI prompt** | PandasAI | nop-ai: 新增 `DataFrameContextBuilder` | ~1d |

### P1 (高价值，但依赖前期设计)

| 模式 | 来源 | 建议映射位置 | 预期工作量 |
|------|------|-------------|----------|
| **声明式断言库** | GE | nop-rule: 标准化断言命名 (如 `expect_not_null`, `expect_in_range`) | ~3d |
| **度量与执行模式解耦** | Griffin | nop-batch + nop-stream: `IDataQualityMeasure` 接口 | ~3d |
| **验证结果报告渲染** | GE | nop-report 或 Data Docs 风格 HTML | ~3d |
| **按列隐式表达式上下文** | OpenRefine | nop-tablesaw: 实现 `value` 隐式引用 + pipe-chain | ~3d |

### P2 (长期，依赖产品方向)

| 模式 | 来源 | 建议映射位置 | 预期工作量 |
|------|------|-------------|----------|
| **Source Freshness** | dbt | nop-dao: 新增 `IDataSourceFreshness` 接口 | ~2d |
| **undo 历史** | OpenRefine | nop-tablesaw: DataFrame snapshot diff 模型 | ~3d |
| **Manifest 编译元数据** | dbt | nop-xdef: 增强模型注册表输出格式 | ~2d |
| **三引擎验证抽象** | GE | nop-dataset: 新增 `IValidationEngine` 接口 | ~5d |

---

## 8. 结论

### 核心发现

1. **OpenRefine 的操作模型（GREL + Operation + History）与 Nop 的 XLang + nop-tablesaw 是互补的**。XLang 不能替代 GREL（上下文不同），但 `TableFlowFunctions` 已覆盖语法糖层面的功能。真正的差距在于操作命令序列化和 undo 历史模型。

2. **Great Expectations 的声明式断言 + Profiler 自动生成是整个领域最实用的功能**。Nop 的规则引擎（nop-rule）完全有能力承载断言逻辑，但缺少两层：标准化断言命名和自动化 Profiler。

3. **Apache Griffin 的 Measure + 双输出模式（metrics + records）是数据质量监控的标准接口**。Nop 的 batch/stream 管线可以模拟此模式，但需要显式的接口定义。

4. **dbt 的范式（SQL + Jinja）与 Nop 完全不同**，但其 DAG 依赖解析和物化策略声明式切换值得借鉴到 nop-task 的编排模型。

5. **PandasAI 的设计最简单，Nop 的 AI agent + tool 系统已经具备更强的替代能力**。唯一值得借鉴的是将数据集预览注入 AI prompt 的模式。

### 决策建议

**短期（P0）**：实现 Profiler（基于 summary+facet 自动推断列语义）+ Measure 双输出模式，使 `nop-tablesaw` 和 `nop-batch` 具备基础的数据质量验证能力。

**中期（P1）**：在 nop-rule 中建立标准化断言库 + 按列隐式表达式上下文，让规则引擎可以像 Great Expectations 那样编写 `expect_not_null(col)`。

**长期（P2）**：评估是否需要 undo 历史、Source Freshness 和三引擎验证抽象。

## Open Questions

- [ ] Profiler 的列语义自动推断精度：从 summary + facet 推断列类型（numeric/string/date/categorical）的准确度如何？需要多少启发式规则？
- [ ] Measure 双输出模式是否应该统一 nop-batch 和 nop-stream，还是各自独立实现？
- [ ] 按列隐式表达式上下文是否应该在 nop-tablesaw 层实现（作为 Java API），还是上升到 XLang 层（与 GREL 对标）？
- [ ] Nop 已有 AI agent + tool-calling 模式，是否还需要支持 PandasAI 风格的代码生成？两种模式各有利弊。

## References

- `nop-format/nop-tablesaw/src/main/java/io/nop/tablesaw/dataflow/TableFlowFunctions.java` — 已实现的 OpenRefine 风格函数
- `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/` — Batch 管线
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/` — Stream 管线
- `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/` — 规则引擎
- `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/` — AI 聊天 + 工具系统
- `nop-kernel/nop-dataset/src/main/java/io/nop/dataset/` — IDataSet 抽象
- `ai-dev/analysis/metadata-survey/2026-07-15-openrefine-deep-analysis.md` — 已有的 OpenRefine 高层面分析
