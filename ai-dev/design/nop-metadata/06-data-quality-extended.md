# nop-metadata 数据质量扩展设计

> Status: active（基线 7 类规则执行已在 `01-architecture-baseline.md` §2.7.1 落地；数据剖析 §三 已落地最终设计，见 P2-7 plan。§四 Checkpoint 已落地最终设计，见 `01-architecture-baseline.md` §2.7.3（P2-8）。§五 评分已落地最终设计，见 `01-architecture-baseline.md` §2.7.4（P2-9）；本节 §五 下文为设计意图伪码，最终裁定以 §2.7.4 为准）
> Date: 2026-07-16（§三 profiling 最终化，P2-7）
> Scope: nop-metadata 数据质量规则扩展、数据剖析和验证执行
> Goal: 定义数据质量扩展模型，参考 Great Expectations 和 Apache Griffin
> Based on: Great Expectations Expectation Suite、Apache Griffin Measure 模型

---

## 一、设计决策

### 1.1 质量规则扩展（扩展 ruleType，follow-up）

**决策**: 在 `01-architecture-baseline.md` §2.7 基线 7 类规则（not_null/unique/range/regex/freshness/volume/custom_sql）之外，扩展以下 ruleType 作为后续增量：

- `profiling` — 数据剖析（统计分析），独立结果面（新建 MetaProfilingRule/Result 实体，参见 §三）
- `schema` — Schema 一致性检查
- `fingerprint` — 数据指纹检查
- `custom_expectation` — 自定义 Expectation

> 这些扩展 ruleType 不在基线执行引擎（§2.7.1）首版支持范围内，作为独立 follow-up plan。

### 1.2 数据剖析支持（follow-up）

**决策**: 参考 Apache Griffin，支持数据剖析（profiling）能力，独立于质量规则执行引擎。

剖析维度: 值分布分析、统计指标（均值、中位数、标准差等）、异常值检测、数据新鲜度。详见 §三。

### 1.3 验证执行模式（已裁定，见 §2.7.1）

**决策**: 基线质量规则执行采用 **BizModel action + withConnection callback**（不选 nop-batch processor），裁定详见 `01-architecture-baseline.md` §2.7.1（D2）。提供的执行模式：

- 单规则执行（`executeQualityRule`）
- 批量规则执行（`executeQualityRulesForDataSource`）
- 定时执行（follow-up，nop-batch/nop-job 适配，见 `09-gap-analysis-extended.md` §4.4）
- 事件触发执行（follow-up）

CheckPoint 编排（多规则批量编排 + 动作 + 调度，见 §四）为独立结果面，未建模实体，属后续 plan。

---

## 二、质量规则扩展（follow-up 增量）

> 基线质量规则模型与执行语义见 `01-architecture-baseline.md` §2.7 / §2.7.1。本节记录在基线之上的扩展设计契约。

### 2.1 扩展的 ruleType 列表（follow-up）

基线 7 类 ruleType 见 §2.7。扩展 ruleType（profiling/schema/fingerprint/custom_expectation）如下，属后续增量 plan，不在基线执行引擎首版支持：

| ruleType | 适用对象 | 说明 | params 示例 |
|----------|----------|------|-------------|
| **`profiling`** | table | 数据剖析 | `{"columns": ["col1", "col2"], "stats": ["mean", "median", "stddev"]}` |
| **`schema`** | table | Schema 检查 | `{"expectedColumns": [{"name": "id", "type": "integer"}]}` |
| **`fingerprint`** | table | 数据指纹 | `{"algorithm": "md5", "sampleSize": 1000}` |
| **`custom_expectation`** | table | 自定义 Expectation | `{"expectation": "expect_column_values_to_be_in_set", "kwargs": {...}}` |

### 2.2 mostly 容错比例（follow-up，未建模）

**说明**: `mostly` 参数表示允许的容错比例。例如 `mostly=0.99` 表示 99% 的值满足规则即可通过。

**当前状态**: 基线 MetaQualityRule 模型**无 mostly 列**（ORM 未建模）。基线执行引擎首版在 `details` JSON 中记录检测值（如 nullCount/重复值组数），但不实现 mostly 全链路判定。mostly 列扩展属 Protected Area（ORM 模型结构变更，需 plan-first），作为 follow-up。

> mostly 的判定语义为：`passRate = satisfyCount / totalCount; pass = (passRate >= mostly)`。具体实现待 mostly 列建模后随执行引擎增量一并落地。

---

## 三、数据剖析（Profiling）— 最终设计（P2-7 已落地）

> 本节为 P2-7（数据剖析）的最终设计状态。建模选型 / 统计范围 / 降级策略 / 执行机制 已裁定（D1/D2/D3 见下），不再含未决项。

### 3.0 设计决策（D1/D2/D3，已裁定）

**D1 建模选型 + 存储形态**：**新建独立实体 `NopMetaProfilingRule` / `NopMetaProfilingResult`**（不复用 MetaQualityRule + profiling ruleType）。理由：剖析结果是**统计值集合**（嵌套 numericStats/stringStats/distribution），与质量结果的 pass/fail + actualValue(double) 形态不同；剖析规则语义（columns[]/stats[]）与质量规则（ruleType/threshold）不同；独立实体避免在 QualityResult 的单 actualValue(double) 列里硬塞统计 JSON。

- `NopMetaProfilingRule`：per-rule 行。列：`profilingRuleId`(PK, seq) / `ruleName` / `displayName` / `tableId`(→NopMetaTable.metaTableId, mandatory) / `columns`(JSON，空=所有列) / `stats`(JSON，要收集的指标列表) / `sampleSize`(nullable) / `extConfig`(json) + 审计列。`columns`/`stats` 用 `domain="json-4000"` + `stdDomain="json"`。
- `NopMetaProfilingResult`：per-execution 时序行。列：`profilingResultId`(PK, seq) / `profilingRuleId`(→NopMetaProfilingRule, mandatory) / `metaTableId`(mandatory) / `snapshotTime`(mandatory) / `tableStats`(JSON，rowCount/sizeBytes/lastModified) / `columnStats`(JSON，列级统计数组) + 审计列。**`tableStats`/`columnStats` 用 `domain="mediumtext"` + `stdDomain="json"`**（列级统计含 percentiles/topValues/distribution 可能超长，不得用 json-4000，对齐 Manifest/Catalog 的 JSON 列决策）。
- to-one 关系：ProfilingResult→ProfilingRule、ProfilingResult→Table；索引 `IX_NOP_META_PROF_RESULT_RULE`(profilingRuleId, snapshotTime) 时序查询 + `IX_NOP_META_PROF_RESULT_TABLE`(metaTableId)。ProfilingRule→Table 为可选 to-one（`tableId` 引用 metaTableId）。

**D2 统计范围 + 可移植性 + 降级（已按 live repo 核查）**：

live repo 核查结论（H2 2.4.240 测试库 + MySQL + PostgreSQL 方言）：
- `COUNT(*)` / `COUNT(DISTINCT col)` / `MIN` / `MAX` / `AVG`：全方言精确（含 H2）。
- `STDDEV_SAMP(col)`：**全方言精确**（H2 / MySQL / PostgreSQL 均支持，已 live 验证 H2 返回正确值）。
- `MIN(LENGTH(col))` / `MAX(LENGTH(col))` / `AVG(LENGTH(col))`：全方言精确。
- `GROUP BY col ORDER BY COUNT(*) DESC LIMIT N`（topValues）：全方言精确。

基于以上，统计范围 + 降级裁定为：

| 统计 | 计算方式 | 可移植性 | 不可用时策略 |
|------|---------|---------|------------|
| totalCount | `COUNT(*)` | 全方言精确 | — |
| distinctCount | `COUNT(DISTINCT col)` | 全方言精确 | — |
| nullCount | `COUNT(*) - COUNT(col)` | 全方言精确 | — |
| emptyCount | `COUNT(*) WHERE col=''` | 全方言精确 | — |
| min / max (numeric & string) | `MIN(col)` / `MAX(col)` | 全方言精确 | — |
| mean (numeric) | `AVG(col)` | 全方言精确 | — |
| stddev (numeric) | `STDDEV_SAMP(col)` | 全方言精确（已 live 验证） | — |
| minLength / maxLength (string) | `MIN(LENGTH(col))` / `MAX(LENGTH(col))` | 全方言精确 | — |
| avgLength (string) | `AVG(LENGTH(col))` | 全方言精确 | — |
| topValues (string) | `GROUP BY col ORDER BY COUNT(*) DESC LIMIT N` | 全方言精确 | — |
| **median / percentiles (numeric)** | **in-app 排序计算**（`SELECT col ORDER BY col` 拉取非空值排序，Java 取中位/百分位） | 全方言精确（仅依赖可移植 ORDER BY） | — |
| **distribution (numeric, 分桶)** | **in-app 分桶**（从排序值按桶边界计数） | 全方言精确 | — |
| **tableStats.sizeBytes** | 方言特定，首版不实现 | 不可移植（MySQL `information_schema`/PG `pg_total_relation_size`/H2 无统一） | **null + `tableStats.unavailable` 显式列出** |
| **tableStats.lastModified** | 方言特定，首版不实现 | 不可移植 | **null + `tableStats.unavailable` 显式列出** |

> **降级铁律（禁止静默跳过/伪造）**：不可用统计显式记 null + `unavailable=[...]` 标记，不静默跳过整列/整表、不伪造 0/空值。`tableStats.sizeBytes`/`lastModified` 首版记 unavailable（对齐 Catalog §2.3.2 降级模式），作为不可用标记机制的验证点。

> **为何 median/percentile/distribution 用 in-app 而非 SQL 函数**：H2/PostgreSQL 虽支持 `PERCENTILE_CONT WITHIN GROUP`，但 MySQL 无原生 percentile 函数，方言差异大；in-app 排序计算（仅依赖可移植 `SELECT col FROM t WHERE col IS NOT NULL ORDER BY col`）全方言精确，避免方言分支。

**列类型适配（D2）**：运行时通过 `DatabaseMetaData.getColumns()` 获取每列的 JDBC 类型，分类收集：
- 数值列（INTEGER/BIGINT/DOUBLE/DECIMAL/NUMERIC 等）：收集 numericStats（min/max/mean/stddev/median/percentiles/distribution）。
- 字符串列（VARCHAR/CHAR/CLOB 等）：收集 stringStats（minLength/maxLength/avgLength/topValues）。
- totalCount/distinctCount/nullCount/emptyCount/min/max 对所有类型收集。
- 类型不适用的统计（如数值列的 stringStats）不收集（在 columnStats 中省略对应字段，不伪造）；类型分类失败（未知类型）按字符串处理并记 details 标记。

**采样（sampleSize）**：首版 `sampleSize` 仅作记录（首版全表聚合，不实现 TABLESAMPLE；TABLESAMPLE 后续 follow-up）。

**列名解析（D1，收口 external 表无字段实体）**：剖析器在 withConnection callback 内通过运行时 `DatabaseMetaData.getColumns(null, schema, tableName, null)` 获取物理列名 + 类型（不依赖 buildSql JSON 是否同步），可选 `columns` 参数（逗号分隔）过滤要剖析的列，空=所有列。

**D3 执行机制 + action 契约（已裁定）**：

- **执行机制**：复用 P2-1/P2-4/P2-6 范式 —— BizModel action + `withConnection` callback + 无状态剖析器（`MetaTableProfiler`，参考 `MetaCatalogCollector` + `MetaQualityRuleExecutor`）。剖析器输入 Connection + DatabaseMetaData + schemaPattern + tableName + columns/stats → 对每列跑聚合 SQL / in-app 排序 → 返回结构化 `ProfilingSnapshot`。不自建连接。
- **action 落点 + 契约**：
  - 主入口 `@BizMutation profileTable(@Name("metaTableId") String id, @Optional @Name("schemaPattern") String schemaPattern, @Optional @Name("columns") String columns, IServiceContext context)`，落点 **`NopMetaTableBizModel`**（入口键是 metaTableId，操作对象是表；与 collectCatalog 入口风格一致）。返回 `Map{profilingResultId, columnCount, unavailable:[...], errors:[...]}`。
  - 辅助入口 `@BizMutation executeProfilingRule(@Name("profilingRuleId") String id, @Optional @Name("schemaPattern") String schemaPattern, IServiceContext context)`，落点 **`NopMetaProfilingRuleBizModel`**（按规则定义的 columns/stats 执行，内部委托同一剖析路径），使 ProfilingRule 实体可运行、非空壳。两个入口均写入 NopMetaProfilingResult 时序行。
- **物理解析 + schema 限定**：复用 P2-6/D1 —— metaTableId→NopMetaTable(external)→querySpace→NopMetaDataSource→`withConnection`；schemaPattern 限定物理 SQL，null 依赖默认 schema。多 schema 同名表为已知限制（与 Catalog/质量同源 follow-up）。
- **时序语义**：每次剖析追加新行（snapshotTime=now），不覆盖（趋势分析）。
- **失败隔离**：单列失败（SQL 异常）per-column try/catch 收集 errors 不中断整表（对齐 P2-6 per-rule 隔离）。
- **不可执行路径显式失败/SKIP**（不静默通过）：表不存在/非 external（首版）/无注册数据源/DISABLED/非 jdbc → 显式失败抛 inline ErrorCode（继承 collectCatalog/executeQualityRule 模式）。
- **标识符注入防护**：列名/表名/schema 名为 SQL 标识符，拼接前通过 `^[A-Za-z_][A-Za-z0-9_]*$` 白名单校验（对齐 P2-6 D3）；值（如 topValues LIMIT）用 int 参数，不拼接。

### 3.1 剖析规则定义（NopMetaProfilingRule，已落地）

```
NopMetaProfilingRule                — 数据剖析规则
  ├── profilingRuleId               — PK (seq)
  ├── ruleName / displayName
  ├── tableId                       → NopMetaTable.metaTableId (mandatory)
  ├── columns                       — JSON，要剖析的列名数组（空=所有列，运行时由 DatabaseMetaData.getColumns 解析）
  ├── stats                         — JSON，要收集的指标列表（count/distinct_count/null_count/empty_count/min/max/mean/stddev/median/percentiles/distribution/min_length/max_length/avg_length/top_values）
  ├── sampleSize                    — 采样大小（可选，首版仅记录）
  ├── extConfig                     — json
  └── 审计列 (version/createdBy/createTime/updatedBy/updateTime/remark)
```

### 3.2 剖析结果模型（NopMetaProfilingResult，已落地）

```
NopMetaProfilingResult              — 数据剖析结果（per-execution 时序行）
  ├── profilingResultId             — PK (seq)
  ├── profilingRuleId               → NopMetaProfilingRule (mandatory)
  ├── metaTableId                   → NopMetaTable.metaTableId (mandatory)
  ├── snapshotTime                  — 快照时间（mandatory，时序键）
  │
  ├── tableStats                    — JSON (mediumtext+json)
  │   ├── rowCount                  — long，SELECT COUNT(*) 结果
  │   ├── sizeBytes                 — long, nullable（方言特定，首版 unavailable）
  │   ├── lastModified              — timestamp, nullable（方言特定，首版 unavailable）
  │   └── unavailable               — string[]，不可用统计字段名（显式列出，不静默跳过）
  │
  └── columnStats                   — JSON 数组 (mediumtext+json)
      ├── columnName
      ├── dataType                  — JDBC 类型名（运行时 DatabaseMetaData.getColumns）
      ├── totalCount / distinctCount / nullCount / emptyCount
      │
      ├── numericStats              — 数值列统计（可选，类型适配）
      │   ├── minValue / maxValue / meanValue / stddevValue
      │   ├── medianValue
      │   └── percentiles           — {"50":..,"25":..,"75":..}（in-app 排序计算）
      │
      ├── stringStats               — 字符串列统计（可选，类型适配）
      │   ├── minLength / maxLength / avgLength
      │   └── topValues             — [{"value":"..","count":N}]（GROUP BY ... LIMIT N）
      │
      ├── distribution              — 值分布（可选，in-app 分桶）
      │   ├── buckets               — 分桶边界 [0,100,500,1000]
      │   └── counts                — 每桶计数 [1000,2000,3000,500]
      │
      └── unavailable               — string[]，本列不可用统计字段名（显式列出）
```

### 3.3 剖析结果示例（final form，去掉伪码）

```json
{
  "profilingRuleId": "profiling_orders",
  "snapshotTime": "2026-07-16T10:00:00Z",
  "metaTableId": "...",
  "tableStats": {
    "rowCount": 4,
    "unavailable": ["sizeBytes", "lastModified"]
  },
  "columnStats": [
    {
      "columnName": "AMOUNT",
      "dataType": "DOUBLE",
      "totalCount": 4, "distinctCount": 4, "nullCount": 0, "emptyCount": 0,
      "numericStats": {"minValue":10.0,"maxValue":40.0,"meanValue":25.0,"stddevValue":12.9,"medianValue":25.0,
                       "percentiles":{"25":12.5,"50":25.0,"75":37.5}},
      "distribution": {"buckets":[0,20,40],"counts":[2,2]}
    },
    {
      "columnName": "NAME",
      "dataType": "VARCHAR",
      "totalCount": 4, "distinctCount": 4, "nullCount": 0, "emptyCount": 0,
      "stringStats": {"minLength":2,"maxLength":4,"avgLength":3.0,
                      "topValues":[{"value":"aaa","count":1}]}
    }
  ]
}
```

> sizeBytes / lastModified 在 tableStats 中为 null 且列入 `unavailable`（方言特定，首版不实现，不伪造）。

---

## 四、验证执行模式

### 4.1 Checkpoint 模型

参考 Great Expectations 的 Checkpoint 模式：

```
MetaQualityCheckpoint            — 质量验证检查点
  ├── checkpointName / displayName
  ├── moduleId                   → MetaModule
  │
  ├── validations[]              — 验证配置列表
  │   └── ValidationConfig
  │       ├── ruleIds            — 要执行的规则 ID 列表
  │       ├── tableIds           — 要验证的表 ID 列表
  │       └── includeInherited   — 是否包含继承的规则
  │
  ├── schedule                   — 执行计划（可选）
  │   ├── cronExpression         — Cron 表达式
  │   └── timezone               — 时区
  │
  ├── actions[]                  — 执行动作
  │   └── Action
  │       ├── actionType         — "store" | "notify" | "update_docs"
  │       ├── config             — JSON 配置
  │       └── enabled
  │
  └── status                     — "active" | "paused" | "disabled"
```

### 4.2 Checkpoint 执行流程

```python
class QualityCheckpoint:
    """质量验证检查点"""
    
    def execute(self, checkpoint):
        """执行检查点"""
        
        # 1. 加载验证配置
        validations = checkpoint.validations
        
        # 2. 执行验证
        results = []
        for validation in validations:
            rules = self.load_rules(validation.ruleIds)
            tables = self.load_tables(validation.tableIds)
            
            for rule in rules:
                for table in tables:
                    result = self.execute_rule(rule, table)
                    results.append(result)
        
        # 3. 执行动作
        for action in checkpoint.actions:
            if action.enabled:
                self.execute_action(action, results)
        
        # 4. 存储结果
        self.store_results(results)
        
        return results
    
    def execute_rule(self, rule, table):
        """执行单个规则"""
        
        # 获取表数据
        data = self.load_table_data(table)
        
        # 根据规则类型执行
        if rule.ruleType == "not_null":
            return self.validate_not_null(rule, data)
        elif rule.ruleType == "unique":
            return self.validate_unique(rule, data)
        elif rule.ruleType == "range":
            return self.validate_range(rule, data)
        elif rule.ruleType == "profiling":
            return self.execute_profiling(rule, data)
        # ... 其他规则类型
```

### 4.3 执行动作

| 动作类型 | 说明 | 配置示例 | 状态 |
|---------|------|---------|--------|
| `store` | 存储验证结果 | `{"target": "meta_quality_result"}` | ✅ 已落地（隐式，executor 写 QualityResult） |
| `notify` | 发送通知 | `{"channel": "email", "recipients": ["data@company.com"]}` | ✅ 已落地（`IMessageService.send`，plan 0540-2） |
| `update_docs` | 更新数据文档 | `{"format": "html", "outputPath": "/docs/quality"}` | ❌ deferred（依赖文档渲染层，配置 enabled 时显式失败） |
| `webhook` | 调用 Webhook | `{"url": "https://hooks.company.com/quality", "method": "POST"}` | ✅ 已落地（`IHttpClient.fetch`，plan 0540-2） |

---

## 五、质量评分

### 5.1 质量评分模型

```
MetaQualityScore                — 质量评分
  ├── entityId                   → MetaTable | MetaEntity
  ├── scoreTime                  — 评分时间
  ├── overallScore               — 总分（0~100）
  │
  ├── dimensionScores            — 维度评分
  │   ├── completeness           — 完整性评分
  │   ├── accuracy               — 准确性评分
  │   ├── consistency            — 一致性评分
  │   ├── timeliness             — 及时性评分
  │   └── uniqueness             — 唯一性评分
  │
  ├── ruleResults                — 规则执行结果汇总
  │   ├── totalRules             — 总规则数
  │   ├── passedRules            — 通过规则数
  │   ├── failedRules            — 失败规则数
  │   └── errorRules             — 错误规则数
  │
  └── trend                      — 趋势数据
      ├── previousScore          — 上次评分
      ├── changeRate             — 变化率
      └── trendDirection         — "improving" | "stable" | "degrading"
```

### 5.2 评分计算

```python
class QualityScorer:
    """质量评分器"""
    
    def calculate_score(self, entity_id):
        """计算质量评分"""
        
        # 1. 获取规则结果
        results = self.get_rule_results(entity_id)
        
        # 2. 计算维度评分
        completeness = self.calculate_completeness(results)
        accuracy = self.calculate_accuracy(results)
        consistency = self.calculate_consistency(results)
        timeliness = self.calculate_timeliness(results)
        uniqueness = self.calculate_uniqueness(results)
        
        # 3. 计算总分
        overall = (
            completeness * 0.3 +
            accuracy * 0.3 +
            consistency * 0.2 +
            timeliness * 0.1 +
            uniqueness * 0.1
        )
        
        # 4. 获取趋势
        previous_score = self.get_previous_score(entity_id)
        trend = self.calculate_trend(overall, previous_score)
        
        return MetaQualityScore(
            entityId=entity_id,
            overallScore=overall,
            dimensionScores={
                "completeness": completeness,
                "accuracy": accuracy,
                "consistency": consistency,
                "timeliness": timeliness,
                "uniqueness": uniqueness
            },
            trend=trend
        )
```

---

## 六、与 metadata-survey 的对比

| 能力 | Great Expectations | Apache Griffin | nop-metadata |
|------|-------------------|---------------|-------------|
| 质量规则 | Expectation Suite | Measure | MetaQualityRule (扩展) |
| 容错比例 | mostly 参数 | - | mostly 参数 |
| 数据剖析 | - | profiling | MetaProfilingRule |
| 验证执行 | Checkpoint | Engine | MetaQualityCheckpoint |
| 质量评分 | - | - | MetaQualityScore |
| 文档生成 | Data Docs | - | follow-up（渲染层待定） |

## Open Questions

> 基线质量规则执行引擎（7 类 ruleType 执行 + BizModel action + withConnection）已在 `01-architecture-baseline.md` §2.7.1 落地。数据剖析 §三 已落地最终设计（P2-7，建模/统计范围/降级/执行机制已裁定）。以下为扩展项（score/checkpoint，未建模实体）与剖析 follow-up 的未决问题：

- [ ] 质量评分的维度权重是否可配置？（score，未建模实体，follow-up）
- [x] ~~数据剖析是否需要支持增量剖析？~~ → **已裁定**：首版全表剖析（in-app 排序 + 便携聚合），增量/流式剖析为 Non-Blocking Follow-up（见 P2-7 plan 的 Non-Goals / Deferred）。§三 profiling 已为最终设计状态。
- [ ] Checkpoint 是否需要支持流式验证？（checkpoint，未建模实体，follow-up）

> **§四 Checkpoint / §五 质量评分**：MetaQualityCheckpoint 已落地（最终设计见 `01-architecture-baseline.md` §2.7.3，P2-8）；MetaQualityScore 已落地（最终设计见 `01-architecture-baseline.md` §2.7.4，P2-9，含 D1-D6 全部裁定）。本节 §四/§五 下文伪码为早期设计意图参考，最终实现状态以 `01-architecture-baseline.md` §2.7.3/§2.7.4 为准。
