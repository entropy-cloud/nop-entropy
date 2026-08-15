# nop-metadata Invariant Cycle 2 Initial Red List（silent-wrong-result 棘轮零点快照）

> 产出方：plan `2026-08-15-0820-1`（Cycle 2 / I1' — silent-wrong-result 不变式沉淀为门禁，Phase 2 P3）
> 实测日期：2026-08-15（live repo，门禁首次运行）
> 上游：`invariant-catalog.md`（Cycle 2 增补 5 条 INV）、`audit-target-set.md` §5（目标集）
> 下游消费者：I2'（正式 red list 漂移核对基线）、I3'（逐条裁决）、I4'（修复）、I5'（终态验证）——plans `2026-08-15-0820-2` / `-0820-3`
> 与 Cycle 1 `initial-red-list.md` 并列，不覆盖不追加。

## 目的

记录新门禁 `check-silent-wrong-result.mjs`（1 扫描器 × 5 规则）**首次运行的全部命中**，作为：

- **I2'/I3' 的输入**：I2' 正式运行以此为漂移对比基线；I3' 逐条裁决（P0/P1/FP/优化候选维持）。
- **I5' 终态的棘轮零点**：I4' 每修复一项 → baseline 对应键收缩；FP/优化候选维持 → 放行注释（`// invariant-ok:`）或驻留 baseline（I3' 逐条裁定方式 a/b）。

**棘轮规则**：门禁集合只增不减；baseline 只能随裁决终态或修复落地收缩，禁止无依据扩张。

---

## 模式归属表（Phase 3 P5b 交付，closure audit 核验依据）

| 门禁 | 不变式 | 接入模式 | 判定依据（确定性规则：快照空→a，非空→b） | baseline 状态 |
|------|--------|----------|------------------------------------------|----------------|
| `check-silent-wrong-result.mjs` | INV-LOCALE + INV-NARROW + INV-CONTAINS-CLASSIFY + INV-DELIM-KEY + INV-BIGDEC | **b（快照对账）** | P3 快照非空（67 命中） | `baseline-cycle2/silent-wrong-result.json`（61 键 / 67 命中，2026-08-15 零点） |

> 聚合入口与 CI 以 `--baseline ai-dev/audits/nop-metadata-invariants/baseline-cycle2/silent-wrong-result.json` 形式调用（模式 b）；对账语义 = 逐键计数 current ≤ baseline（子集，计数版），新增键或超计数即红。narrowing-cast 子族当前零命中，随门禁整体以模式 b 接入（任何未来命中 = baseline 外新键 = 红，防回退语义不受影响）。

---

## 汇总

| 子族（规则） | 不变式 | 命中数 | 键数（baseline） |
|------|--------|--------|------------------|
| locale | INV-LOCALE | **40** | 39 |
| narrowing-cast | INV-NARROW | **0**（防回退零点） | 0 |
| contains-classify | INV-CONTAINS-CLASSIFY | **19** | 14 |
| delim-key | INV-DELIM-KEY | **6** | 6 |
| bigdec-precision | INV-BIGDEC | **2** | 2 |
| **合计** | | **67** | **61** |

> **说明**：67 = 门禁的初始 red list（全部待 I3' 裁决，本计划不修复）。命中数与键数差异来自同键多计数：locale 的 `SqlColumnLineageExtractor.java:345/:523` 同文本行归一化后为同键（count 2）；contains-classify 的 `MetaTableProfiler.java:276`（3 次）/`:277`（2 次）/`:278`（2 次）/`ExternalTableStructureReader.java:148`（2 次）为同行多命中（对账键 = 文件路径+行文本归一化+子族）。历史已修实例（AR-12 的 Locale.ROOT 修复点、AR-05 的 exact-match 修复点、AR-03 的结构性 key 修复点、AR-10 的路由修复点）均不命中——本快照全部是**类别残留**或**修复点同文件兄弟**，正是「修实例不修类别」根因的实证清单。

---

## 门禁运行命令清单

```bash
# 5. INV-SILENT-WRONG-RESULT: silent-wrong-result 五规则扫描（无 baseline 时命中即红）
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata

# 模式 b（CI / 聚合入口形态）：baseline 对账，命中集 ⊆ baseline 即绿
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata \
  --baseline ai-dev/audits/nop-metadata-invariants/baseline-cycle2/silent-wrong-result.json

# 单子族调试
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule locale

# 自验证 fixture（违规/合规双向 + baseline 对账 ⊆绿/超出红 + 放行注释）
node ai-dev/tools/check-silent-wrong-result.mjs --fixture
```

---

## §1 INV-LOCALE 命中清单（40 项）

> 检测规则：无参 `.toLowerCase()` / `.toUpperCase()`（默认 locale case-mapping；机器比较 vs display-only 语义由 I3' 逐条裁定）
> 历史同族 finding-ID：AR-12（已修 1 处 = `LocalReconciliationProcessor`；下列 40 处为类别残留）

| # | 文件:行 | 命中行 |
|---|---------|--------|
| 1 | `catalog/MetaCatalogCollector.java:75` | `stats.getExtras().put("tableType", ref.getKind().name().toLowerCase());` |
| 2 | `connection/MetaDataSourceConnectionProcessor.java:70` | `"allowLoadLocalInfile".toLowerCase(),` |
| 3 | `connection/MetaDataSourceConnectionProcessor.java:128` | `String trimmed = token.trim().toLowerCase();` |
| 4 | `connection/MetaDataSourceConnectionProcessor.java:231` | `String lower = jdbcUrl.toLowerCase();` |
| 5 | `connection/MetaDataSourceConnectionProcessor.java:265` | `&& !resolveAllowedInternalHosts().contains(host.toLowerCase())) {` |
| 6 | `connection/MetaDataSourceConnectionProcessor.java:495` | `String h = host.toLowerCase();` |
| 7 | `contract/MetaContractChecker.java:352` | `String unit = String.valueOf(unitObj).toLowerCase();` |
| 8 | `entity/NopMetaLineageEdgeQueryAction.java:176` | `String sourceId = nameToId.get(ref.getSimpleName().toLowerCase());` |
| 9 | `entity/NopMetaLineageEdgeQueryAction.java:239` | `String sourceId = nameToId.get(c.getSourceTableName().toLowerCase());` |
| 10 | `entity/NopMetaLineageEdgeQueryAction.java:296` | `if (n != null) fieldNamesLower.add(n.toLowerCase());` |
| 11 | `entity/NopMetaLineageEdgeQueryAction.java:326` | `if (!fieldNamesLower.contains(ident.toLowerCase())) {` |
| 12 | `entity/NopMetaLineageEdgeQueryAction.java:438` | `map.putIfAbsent(t.getTableName().toLowerCase(), t.getMetaTableId());` |
| 13 | `lineage/SqlColumnLineageExtractor.java:157` | `String cteNameLower = cte.getName().toLowerCase();` |
| 14 | `lineage/SqlColumnLineageExtractor.java:318` | `String outColLower = outCol.toLowerCase();` |
| 15 | `lineage/SqlColumnLineageExtractor.java:345` | `String ownerLower = owner.toLowerCase();` |
| 16 | `lineage/SqlColumnLineageExtractor.java:354` | `NamedSourceMap cte = cteRegistry.get(simpleTable.toLowerCase());` |
| 17 | `lineage/SqlColumnLineageExtractor.java:386` | `List<SourceRef> refs = namedMap.outputs.get(colName.toLowerCase());` |
| 18 | `lineage/SqlColumnLineageExtractor.java:443` | `aliasMap.putIfAbsent(scopeName.toLowerCase(), simple);` |
| 19 | `lineage/SqlColumnLineageExtractor.java:457` | `derivedAliases.putIfAbsent(alias.toLowerCase(), m);` |
| 20 | `lineage/SqlColumnLineageExtractor.java:523` | `String ownerLower = owner.toLowerCase();` |
| 21 | `lineage/SqlColumnLineageExtractor.java:535` | `NamedSourceMap cte = cteRegistry.get(sourceTable.toLowerCase());` |
| 22 | `lineage/SqlColumnLineageExtractor.java:572` | `List<SourceRef> refs = namedMap.outputs.get(sourceColumn.toLowerCase());` |
| 23 | `lineage/SqlSourceTableExtractor.java:89` | `if (cteNames.contains(simple.toLowerCase())) {` |
| 24 | `lineage/SqlSourceTableExtractor.java:118` | `cteNames.add(cte.getName().toLowerCase());` |
| 25 | `profiling/MetaTableProfiler.java:118` | `snapshot.getTableExtras().put("tableType", ref.getKind().name().toLowerCase());` |
| 26 | `profiling/MetaTableProfiler.java:275` | `String lower = msg.toLowerCase();` |
| 27 | `profiling/MetaTableProfiler.java:461` | `if (filter != null && !filter.isEmpty() && !filter.contains(name.toUpperCase())) {` |
| 28 | `profiling/MetaTableProfiler.java:557` | `String upper = typeName.toUpperCase().trim();` |
| 29 | `profiling/MetaTableProfiler.java:565` | `String upper = typeName.toUpperCase();` |
| 30 | `profiling/MetaTableProfiler.java:605` | `if (filter != null && !filter.isEmpty() && !filter.contains(f.getName().toUpperCase())) {` |
| 31 | `profiling/MetaTableProfiler.java:632` | `set.add(t.toUpperCase());` |
| 32 | `quality/CheckpointActionDispatcher.java:127` | `String trimmed = token.trim().toLowerCase();` |
| 33 | `quality/CheckpointActionDispatcher.java:232` | `? String.valueOf(config.get("method")).trim().toUpperCase()` |
| 34 | `quality/CheckpointActionDispatcher.java:284` | `String lower = url.toLowerCase();` |
| 35 | `quality/CheckpointActionDispatcher.java:300` | `&& !resolveAllowedWebhookHosts().contains(host.toLowerCase())) {` |
| 36 | `quality/MetaQualityRuleExecutor.java:163` | `j.getDetails().put("tableType", ref.getKind().name().toLowerCase());` |
| 37 | `quality/MetaQualityRuleExecutor.java:387` | `String upper = sql.trim().toUpperCase();` |
| 38 | `quality/MetaQualityRuleExecutor.java:750` | `String s = expectPassWhen.trim().toLowerCase();` |
| 39 | `quality/MetaQualityRuleExecutor.java:808` | `String lower = msg.toLowerCase();` |
| 40 | `sync/ExternalTableStructureReader.java:147` | `String p = productName.toLowerCase();` |

复现：`rg '\.(toLowerCase|toUpperCase)\(\)' nop-metadata --glob '*.java' -g '!*_gen/*' -g '!*Test*'`（rg 原始 41 = 40 真实 + 1 javadoc 伪站点 `LocalReconciliationProcessor.java:124`；扫描器剥离注释后 40）

## §2 INV-NARROW 命中清单（0 项，防回退零点）

> 检测规则：`(long|int|short)` 强转 + 未加括号的浮点声明标识符操作数 + 紧随算术运算符。live 仅存 `(long)` 站点为 AR-01 修复后正确形态（`MetaContractChecker.java:384/:395`，操作数加括号），widening 场景（`(long) from + limit`）不命中。

复现：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule narrowing-cast`（exit 0）

## §3 INV-CONTAINS-CLASSIFY 命中清单（19 项 / 14 行）

> 检测规则：String-receiver `.contains(`（receiver 同文件 String 声明或 String 变换链尾；集合 receiver 不命中）。
> 历史同族 finding-ID：AR-05（`isNumericType` 已修 exact-match；下列 #10 为其兄弟方法 `isStringType` 残留——AR-05 只修一子的直接实证）。

| # | 文件:行 | 命中行 |
|---|---------|--------|
| 1 | `connection/MetaDataSourceConnectionProcessor.java:246` | `if (lower.contains(dangerous)) {` |
| 2 | `entity/NopMetaModuleBizModel.java:358` | `return sourceContent != null && sourceContent.contains("x:extends");` |
| 3 | `field/ExpressionMeasureValidator.java:223` | `if (ident.contains(".")) {` |
| 4 | `field/ExpressionMeasureValidator.java:237` | `if (!ident.contains(".")) {` |
| 5 | `profiling/MetaTableProfiler.java:276` | `if (lower.contains("connection") \|\| lower.contains("permission") \|\| lower.contains("denied")`（同行 3 命中） |
| 6 | `profiling/MetaTableProfiler.java:277` | `\|\| lower.contains("closed") \|\| lower.contains("timeout")`（同行 2 命中） |
| 7 | `profiling/MetaTableProfiler.java:278` | `\|\| lower.contains("communication") \|\| lower.contains("does not exist")) {`（同行 2 命中） |
| 8 | `profiling/MetaTableProfiler.java:567` | `if (upper.contains(kw)) {`（**AR-05 兄弟：isStringType 子串分类**） |
| 9 | `profiling/MetaTableProfiler.java:595` | `return col != null && col.startsWith("<") && col.contains("expr");` |
| 10 | `quality/MetaQualityRuleExecutor.java:388` | `if (upper.contains(";")) {` |
| 11 | `quality/MetaQualityRuleExecutor.java:391` | `if (upper.contains("/*!")) {` |
| 12 | `quality/MetaQualityRuleExecutor.java:646` | `return col != null && col.startsWith("<") && col.contains("expr");` |
| 13 | `quality/MetaQualityRuleExecutor.java:810` | `if (lower.contains(signature)) {` |
| 14 | `sync/ExternalTableStructureReader.java:148` | `return p.contains("mysql") \|\| p.contains("postgresql") \|\| p.equals("h2");`（同行 2 命中） |

复现：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule contains-classify`

## §4 INV-DELIM-KEY 命中清单（6 项）

> 检测规则：集合 key 位实参跨度内分隔符拼接（`| : ; # @ ~ $ % ^ & ,`）+ `*Key` 变量分隔符拼接赋值。
> 历史同族 finding-ID：AR-03（group-key 已修结构性键；下列 6 处为类别残留）。

| # | 文件:行 | 命中行 |
|---|---------|--------|
| 1 | `entity/AutoClassificationProcessor.java:144` | `String warnKey = classification.getClassificationId() + "\|" + pattern;` |
| 2 | `entity/LineageTagPropagationProcessor.java:85` | `visited.add(entityType + "#" + entityId);` |
| 3 | `entity/LineageTagPropagationProcessor.java:136` | `String visitKey = ENTITY_TYPE_NOP_META_TABLE + "#" + targetId;` |
| 4 | `entity/LineageTagPropagationProcessor.java:152` | `if (visited.contains(ENTITY_TYPE_NOP_META_TABLE + "#" + nextEdge.getTargetTableId())) {` |
| 5 | `entity/NopMetaLineageEdgeQueryAction.java:258` | `String key = sourceId + "\|" + c.getSourceColumn() + "\|" + c.getTargetColumn();` |
| 6 | `entity/NopMetaLineageEdgeQueryAction.java:487` | `map.put(e.getSourceTableId() + "\|" + e.getSourceColumn() + "\|" + e.getTargetColumn(), e);` |

复现：`rg -n '\+ "\\\|" \+' nop-metadata/nop-metadata-service/src/main/java --glob '*.java'`（`|` 分隔族 3 行；`#` 族另见 rg `'\+ "#" \+'`）；权威口径 = 扫描器。

## §5 INV-BIGDEC 命中清单（2 项）

> 检测规则：`BigDecimal.valueOf(...)` / `new BigDecimal(...)` 实参含 `.doubleValue()` 且所在方法体无 `.longValue()` 整数路由。
> 历史同族 finding-ID：AR-10（`AggregationHelper.toBigDecimal` 已修；下列 2 处为私有拷贝残留，含 String 静默跳过同型问题）。受保护形态 `AggregationHelper.java:554` 不命中（方法体含路由信号）。

| # | 文件:行 | 命中行 |
|---|---------|--------|
| 1 | `query/MemoryFilterEvaluator.java:356` | `return BigDecimal.valueOf(((Number) v).doubleValue());` |
| 2 | `query/MemoryOrderByComparator.java:132` | `return BigDecimal.valueOf(((Number) v).doubleValue());` |

复现：`rg -n 'BigDecimal\.valueOf' nop-metadata --glob '*.java' -g '!*_gen/*' -g '!*Test*'`（全量 5 处；扫描器排除受保护形态后 2）

---

## 机器可读层（baseline）

- 路径：`ai-dev/audits/nop-metadata-invariants/baseline-cycle2/silent-wrong-result.json`
- 字段：`file`（仓库相对路径）+ `text`（命中行文本归一化，去首尾空白）+ `family`（子族标签）+ `count`（同键出现次数）
- 对账键 = `file | family | text`；对账语义 = 逐键 `current ≤ baseline`（子集，计数版；少于 baseline 为绿——I4' 渐进修复期无需逐笔收缩）
- 生成命令：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --emit-baseline`
- 快照事实：61 键 / 67 命中（2026-08-15 首次运行，与本文人读层一致）
