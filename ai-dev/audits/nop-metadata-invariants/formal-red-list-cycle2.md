# nop-metadata Invariant Cycle 2 正式 Red List（Formal Red List — silent-wrong-result）

> 产出方：plan `2026-08-15-0820-2`（Cycle 2 / I2' — 不变式驱动审计，Phase 1 A1/A3）
> 实测日期：2026-08-15（live repo，门禁 `check-silent-wrong-result.mjs` 正式复跑）
> 上游：`invariant-catalog.md`（Cycle 2 增补 5 条 INV）、`audit-target-set.md` §5（目标集）、`initial-red-list-cycle2.md`（I1' 初始快照 = 漂移核对基线）+ `baseline-cycle2/silent-wrong-result.json`（机器可读层）
> 下游消费者：I3'（逐条裁决，见 `adjudication-table-cycle2.md`）、I4'（P1 修复，plan `2026-08-15-0820-3`）、I5'（终态对比基准）
> 与 Cycle 1 `formal-red-list.md` 并列，不覆盖不追加。

## 目的

记录 gate 5（`check-silent-wrong-result.mjs`，1 扫描器 × 5 规则）在 I2' 正式审计轮次跨全部审计目标（`nop-metadata-service/src/main/java` 全部 main 代码）的**全部命中（67）**，并与 I1' 初始快照做漂移核对。本文件是 I3' 裁决的唯一输入。

---

## §0 门禁运行命令（可一键复跑）

```bash
# 无 baseline 形态（命中即红，退出码 1 = 有命中）
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata

# 模式 b（CI / 聚合入口形态）：baseline 对账，命中集 ⊆ baseline 即绿（退出码 0）
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata \
  --baseline ai-dev/audits/nop-metadata-invariants/baseline-cycle2/silent-wrong-result.json

# 单子族过滤（正式审计逐族复核）
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule locale              # 40
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule narrowing-cast     # 0
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule contains-classify  # 19
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule delim-key          # 6
node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule bigdec-precision   # 2

# 聚合入口（5 门禁全量）
bash ai-dev/tools/run-nop-metadata-invariants.sh   # exit 0（gate 1-4 零命中；gate 5 对账 0 超出）

# 自验证 fixture
node ai-dev/tools/check-silent-wrong-result.mjs --fixture   # 18/18 PASS
```

---

## §汇总

| 子族（规则） | 不变式 | 命中数 | 键数（baseline） | 退出码（无 baseline 形态） | 已知/新发现 |
|------|--------|--------|------------------|--------|------------|
| locale | INV-LOCALE | **40** | 39 | 1 | 已知族（AR-12 类别残留，I1' 快照收录） |
| narrowing-cast | INV-NARROW | **0** | 0 | 0 | 防回退零点 |
| contains-classify | INV-CONTAINS-CLASSIFY | **19** | 14 | 1 | 已知族（AR-05 兄弟 + AR-06 消息线索等，I1' 快照收录） |
| delim-key | INV-DELIM-KEY | **6** | 6 | 1 | 已知族（AR-03 类别残留，I1' 快照收录） |
| bigdec-precision | INV-BIGDEC | **2** | 2 | 1 | 已知族（AR-10 私有拷贝残留，I1' 快照收录） |
| **合计** | | **67** | **61** | 1 | — |

> 对抗探查新增（Phase 1 A2/A3）：**0** 条并入本表（10 方向全部"已探查"，1 项 latent-form 观察不构成 live 命中，详见 `adversarial-probing-notes-cycle2.md`）。

---

## §一致性核对（与 I1' 初始快照 `initial-red-list-cycle2.md` 漂移比对）

| 维度 | I1' 初始快照（2026-08-15 首次运行） | I2' 正式 red list（本文件） | 结论 |
|------|-------------------------------------|------------------------------|------|
| locale | 40（39 键） | 40（39 键） | **一致** |
| narrowing-cast | 0 | 0 | **一致** |
| contains-classify | 19（14 键） | 19（14 键） | **一致** |
| delim-key | 6（6 键） | 6（6 键） | **一致** |
| bigdec-precision | 2（2 键） | 2（2 键） | **一致** |
| **合计** | **67 命中 / 61 键** | **67 命中 / 61 键** | **零漂移** |

**漂移核对证据（三层）**：

1. **baseline 对账（模式 b）**：`check-silent-wrong-result.mjs --baseline baseline-cycle2/silent-wrong-result.json` → `Keys within baseline: 61, Keys outside baseline: 0 — all hits within baseline (current <= baseline per key)`，退出码 **0**（逐键 current = baseline 计数，无超计数、无新键、无收缩）。
2. **git 溯源**：`git log --since=2026-08-15 --oneline -- nop-metadata/nop-metadata-service/src/main/java` 为**空**（I1' 快照 → I2' 正式轮期间 nop-metadata main 代码零 commit）；工作区 `git status --porcelain` 对 nop-metadata 为空。源码未变，命中集完全一致属预期。
3. **确定性双跑**：`--format json` 两次运行 hits 数组逐字段一致（67/67；唯一差异为报告 `generated` 时间戳字段，非命中数据）。

---

## §1 INV-LOCALE 命中清单（40 项 / 39 键 / 11 文件）

> 检测规则：注释/字符串剥离后无参 `.toLowerCase()` / `.toUpperCase()` 调用点（`Locale.` 参数形态天然不命中）。
> 历史同族 finding-ID：AR-12（已修 1 处 = `LocalReconciliationProcessor`；下列 40 处为类别残留）。
> 逐条 `文件:行` 与命中行文本与 `initial-red-list-cycle2.md` §1 **完全一致**（零漂移，直接复引；复跑命令见 §0 `--rule locale`）。

| # | 文件:行（相对 `nop-metadata-service/.../service/`） | 机器比较语义归类（终判归 I3'） |
|---|---------|--------|
| 1 | `catalog/MetaCatalogCollector.java:75` | extras 结构化 token（`tableType`） |
| 2 | `connection/MetaDataSourceConnectionProcessor.java:70` | 安全 blocklist 常量 token |
| 3 | `connection/MetaDataSourceConnectionProcessor.java:128` | allowed-hosts 集合归一化 |
| 4 | `connection/MetaDataSourceConnectionProcessor.java:231` | URL 归一化（危险 token 扫描输入） |
| 5 | `connection/MetaDataSourceConnectionProcessor.java:265` | host 归一化（内网白名单比对） |
| 6 | `connection/MetaDataSourceConnectionProcessor.java:495` | host 归一化 |
| 7 | `contract/MetaContractChecker.java:352` | SLA 单位 token 归一化 |
| 8 | `entity/NopMetaLineageEdgeQueryAction.java:176` | registry 键归一化 |
| 9 | `entity/NopMetaLineageEdgeQueryAction.java:239` | registry 键归一化 |
| 10 | `entity/NopMetaLineageEdgeQueryAction.java:296` | 字段名集合归一化 |
| 11 | `entity/NopMetaLineageEdgeQueryAction.java:326` | 字段名集合比对 |
| 12 | `entity/NopMetaLineageEdgeQueryAction.java:438` | map 键归一化 |
| 13 | `lineage/SqlColumnLineageExtractor.java:157` | CTE registry 键 |
| 14 | `lineage/SqlColumnLineageExtractor.java:318` | 输出列键 |
| 15 | `lineage/SqlColumnLineageExtractor.java:345` | owner 键（与 :523 同键文本，count 2） |
| 16 | `lineage/SqlColumnLineageExtractor.java:354` | CTE registry 查询键 |
| 17 | `lineage/SqlColumnLineageExtractor.java:386` | 输出映射查询键 |
| 18 | `lineage/SqlColumnLineageExtractor.java:443` | alias map 键 |
| 19 | `lineage/SqlColumnLineageExtractor.java:457` | derived alias 键 |
| 20 | `lineage/SqlColumnLineageExtractor.java:523` | owner 键（与 :345 同键文本） |
| 21 | `lineage/SqlColumnLineageExtractor.java:535` | CTE registry 查询键 |
| 22 | `lineage/SqlColumnLineageExtractor.java:572` | 输出映射查询键 |
| 23 | `lineage/SqlSourceTableExtractor.java:89` | CTE 名集合比对 |
| 24 | `lineage/SqlSourceTableExtractor.java:118` | CTE 名集合归一化 |
| 25 | `profiling/MetaTableProfiler.java:118` | extras 结构化 token（`tableType`） |
| 26 | `profiling/MetaTableProfiler.java:275` | 异常消息线索匹配输入（AR-06 机制） |
| 27 | `profiling/MetaTableProfiler.java:461` | 列名 filter 比对 |
| 28 | `profiling/MetaTableProfiler.java:557` | 类型分类（isNumericType，AR-05 已修方法的同文件邻位） |
| 29 | `profiling/MetaTableProfiler.java:565` | 类型分类（isStringType） |
| 30 | `profiling/MetaTableProfiler.java:605` | 列名 filter 比对 |
| 31 | `profiling/MetaTableProfiler.java:632` | 类型名集合归一化 |
| 32 | `quality/CheckpointActionDispatcher.java:127` | webhook allowed-hosts 集合归一化 |
| 33 | `quality/CheckpointActionDispatcher.java:232` | HTTP method 白名单 token |
| 34 | `quality/CheckpointActionDispatcher.java:284` | webhook URL 协议前缀比对输入 |
| 35 | `quality/CheckpointActionDispatcher.java:300` | host 归一化（白名单比对） |
| 36 | `quality/MetaQualityRuleExecutor.java:163` | details 结构化 token（`tableType`） |
| 37 | `quality/MetaQualityRuleExecutor.java:387` | SQL 归一化（sandbox 关键字扫描输入） |
| 38 | `quality/MetaQualityRuleExecutor.java:750` | expectPassWhen 配置 token |
| 39 | `quality/MetaQualityRuleExecutor.java:808` | 异常消息签名匹配输入 |
| 40 | `sync/ExternalTableStructureReader.java:147` | DB 产品名归一化（方言路由输入） |

复现（扫描器口径 40，权威）：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule locale`
辅助复核（rg 原始 41 = 40 + 1 javadoc 伪站点 `LocalReconciliationProcessor.java:124`，不剥离注释）：`rg '\.(toLowerCase|toUpperCase)\(\)' nop-metadata --glob '*.java' -g '!*_gen/*' -g '!*Test*'`

## §2 INV-NARROW 命中清单（0 项，防回退零点）

> 检测规则：`(long|int|short)` 强转 + 未加括号的浮点声明标识符操作数 + 紧随算术运算符。live 仅存 `(long)` 站点为 AR-01 修复后正确形态（`MetaContractChecker.java:384/:395`，操作数加括号）。**0 命中与 I1' 快照一致**；对抗探查方向 D5（catalog 声明的 watch 边界：方法调用返回浮点操作数）另核查 11 处候选形态全部为 widening / Math.floor-ceil 整值 / 括号正确形态，0 违规（见 `adversarial-probing-notes-cycle2.md`）。

复现：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule narrowing-cast`（exit 0）

## §3 INV-CONTAINS-CLASSIFY 命中清单（19 项 / 14 键 / 6 文件）

> 检测规则：String-receiver `.contains(`（receiver 同文件 String 声明或 String 变换链尾；集合 receiver 不命中）。
> 历史同族 finding-ID：AR-05（`isNumericType` 已修 exact-match；#8 为兄弟方法 `isStringType` 残留）。逐条与 `initial-red-list-cycle2.md` §3 完全一致（零漂移）。

| # | 文件:行 | 命中行（同行多命中已注明次数） | 用途初判（终判归 I3'） |
|---|---------|--------|--------|
| 1 | `connection/MetaDataSourceConnectionProcessor.java:246` | `lower.contains(dangerous)` | 安全 blocklist 扫描 |
| 2 | `entity/NopMetaModuleBizModel.java:358` | `sourceContent.contains("x:extends")` | delta 快路径门 |
| 3 | `field/ExpressionMeasureValidator.java:223` | `ident.contains(".")` | 限定名判定 |
| 4 | `field/ExpressionMeasureValidator.java:237` | `!ident.contains(".")` | 限定名判定 |
| 5-7 | `profiling/MetaTableProfiler.java:276` | `lower.contains("connection")` ×3（同行） | 消息线索（AR-06 机制） |
| 8-9 | `profiling/MetaTableProfiler.java:277` | `lower.contains("closed")` ×2（同行） | 消息线索 |
| 10-11 | `profiling/MetaTableProfiler.java:278` | `lower.contains("communication")` ×2（同行） | 消息线索 |
| 12 | `profiling/MetaTableProfiler.java:567` | `upper.contains(kw)` | **isStringType 子串分类（AR-05 兄弟残留）** |
| 13 | `profiling/MetaTableProfiler.java:595` | `col.startsWith("<") && col.contains("expr")` | 派生列合成名标记（D5） |
| 14 | `quality/MetaQualityRuleExecutor.java:388` | `upper.contains(";")` | sandbox 分隔符探测 |
| 15 | `quality/MetaQualityRuleExecutor.java:391` | `upper.contains("/*!")` | sandbox 注入指令探测 |
| 16 | `quality/MetaQualityRuleExecutor.java:646` | `col.startsWith("<") && col.contains("expr")` | 派生列合成名标记（D5） |
| 17 | `quality/MetaQualityRuleExecutor.java:810` | `lower.contains(signature)` | 消息签名分类（regexp-unsupported SKIP） |
| 18-19 | `sync/ExternalTableStructureReader.java:148` | `p.contains("mysql")` ×2（同行，含 postgresql） | 方言路由 |

复现：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule contains-classify`

## §4 INV-DELIM-KEY 命中清单（6 项 / 6 键 / 3 文件）

> 检测规则：集合 key 位实参跨度内分隔符拼接 + `*Key` 变量分隔符拼接赋值。历史同族 finding-ID：AR-03（group-key 已修结构性键）。逐条与 `initial-red-list-cycle2.md` §4 完全一致（零漂移）。

| # | 文件:行 | 命中行 | 键用途 |
|---|---------|--------|--------|
| 1 | `entity/AutoClassificationProcessor.java:144` | `String warnKey = classificationId + "\|" + pattern;` | warn 去重（pattern 为正则，可含 `\|`） |
| 2 | `entity/LineageTagPropagationProcessor.java:85` | `visited.add(entityType + "#" + entityId);` | 遍历防环 visited |
| 3 | `entity/LineageTagPropagationProcessor.java:136` | `String visitKey = ENTITY_TYPE_NOP_META_TABLE + "#" + targetId;` | 遍历防环 visited |
| 4 | `entity/LineageTagPropagationProcessor.java:152` | `visited.contains(ENTITY_TYPE_NOP_META_TABLE + "#" + ...)` | 遍历防环 visited |
| 5 | `entity/NopMetaLineageEdgeQueryAction.java:258` | `String key = sourceId + "\|" + sourceColumn + "\|" + targetColumn;` | 边去重（seenKeys / existingEdgeMap 查询，SQL 派生标识符分量） |
| 6 | `entity/NopMetaLineageEdgeQueryAction.java:487` | `map.put(e.getSourceTableId() + "\|" + e.getSourceColumn() + "\|" + e.getTargetColumn(), e);` | existing-edge map 键（SQL 派生标识符分量） |

复现（扫描器口径 6，权威）：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule delim-key`

## §5 INV-BIGDEC 命中清单（2 项 / 2 键 / 2 文件）

> 检测规则：`BigDecimal.valueOf(...)` / `new BigDecimal(...)` 实参含 `.doubleValue()` 且所在方法体无 `.longValue()` 整数路由。历史同族 finding-ID：AR-10（`AggregationHelper.toBigDecimal` 已修；下列 2 处为私有拷贝残留）。受保护形态 `AggregationHelper.java:554` 不命中（方法体含路由信号）。逐条与 `initial-red-list-cycle2.md` §5 一致（零漂移）。

| # | 文件:行 | 命中行 |
|---|---------|--------|
| 1 | `query/MemoryFilterEvaluator.java:356` | `return BigDecimal.valueOf(((Number) v).doubleValue());` |
| 2 | `query/MemoryOrderByComparator.java:132` | `return BigDecimal.valueOf(((Number) v).doubleValue());` |

复现（扫描器口径 2，权威）：`node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule bigdec-precision`

---

## §6 对抗探查新增（Phase 1 A2/A3）

> 对抗探查 10 方向（含 2 个 watch-only 候选族专属方向 + Current Baseline 全部 6 个候选假设 + 3 条 catalog 声明的 watch 边界 + 跨子模块）全部结论"无 live 新命中"，**0 条并入本表**。1 项 latent-form 观察（`MetaTableProfiler.toLong:548` raw-NFE/null→0 形态，当前调用面 COUNT-only 不可达）不构成 live 命中，登记于 `adversarial-probing-notes-cycle2.md` 方向 6 与 roadmap backlog（watch-only）。逐方向方法/覆盖面/证据见该文件。

---

## §棘轮零点声明（与 I1' 一致）

> **本正式 red list 命中总数 = 67（40 locale + 0 narrowing + 19 contains + 6 delim + 2 bigdec）= I5' 终态收口的对比基准，与 I1' 快照零漂移。**
>
> 后续规则：
> - I3' 裁决每条命中为 P1 / false-positive（附 B1b 标注方式裁定）/ 优化候选维持 / 新族登记（见 `adjudication-table-cycle2.md`）。
> - I4' 修复 P1 项；类别清扫以裁决表为权威分母。
> - I5' 终态 = 命中集 ⊆ 已批准豁免清单（FP 驻留 / 放行条目）或清空。
> - 已沉淀不变式只增不减（棘轮规则）；baseline 只能随裁决终态或修复落地收缩。

## §棘轮终态记录（I5' 收口，plan 2026-08-15-0820-3 Phase 2 V3，2026-08-15）

> **初始 → 终态对比（可复现计数，恒等式可复核）**

| 族 | 初始命中（I1'/I2' 快照） | 修复清零（P1） | 豁免驻留（FP baseline） | 终态命中 | 复现命令 |
|----|------------------------|----------------|------------------------|----------|----------|
| locale（INV-LOCALE） | 40 | **40** | 0 | **0** | `--rule locale` |
| narrowing-cast（INV-NARROW） | 0 | —（防回退零点） | 0 | **0** | `--rule narrowing-cast` |
| contains-classify（INV-CONTAINS-CLASSIFY） | 19 | **1**（C8 isStringType exact-match） | **18**（C1-C7/C9-C14 方式 (a) 驻留） | **18** | `--rule contains-classify` |
| delim-key（INV-DELIM-KEY） | 6 | **3**（D1/D5/D6 结构性键） | **3**（D2-D4 方式 (a) 驻留） | **3** | `--rule delim-key` |
| bigdec-precision（INV-BIGDEC） | 2 | **2**（E1/E2 AR-10 路由形态） | 0 | **0** | `--rule bigdec-precision` |
| **合计** | **67** | **46** | **21** | **21** | 无 baseline 形态 exit 1（21 FP 命中） |

**恒等式复核**：初始 67 = 修复清零 46 + 豁免驻留 21 + successor 拆分 0 ✓（与裁决表 §1 `67 = 46 P1 + 21 FP + 0 维持 + 0 新族` 逐项对齐；优化候选维持 = 0——旧裁定 2 条经 I3' 重裁推翻转 P1 并已修复）。

**终态机制（0820-1 Phase 3 关键约束 B 预声明的两种形态之一）**：baseline 由 61 键（快照零点）**重写为已批准豁免清单**（21 FP 命中 / 16 计数感知键：contains 18 命中 13 键 + delim 3 命中 3 键），**保持模式 b**（豁免驻留非空 → 不升级模式 a）；红线语义不变：任何新增命中键或计数增长即红。聚合入口 `run-nop-metadata-invariants.sh` 终态整体 exit 0（模式 b 豁免驻留下，含 gate 5 对账 16 键内 0 超出）；hard-gate 注入 proof（2026-08-15 实测）：baseline 外注入 `s.toLowerCase()` → scanner exit 1（EXCESS: new key not in baseline）+ 聚合 exit 1（set -e 传播，CI 将红）→ 还原 → exit 0，产品树 `git status --porcelain -- nop-metadata/` 净零。

---

## 引用

- `initial-red-list-cycle2.md` + `baseline-cycle2/silent-wrong-result.json`（I1' 快照，本文件漂移核对基准）
- `invariant-catalog.md`（Cycle 2 增补 5 条不变式陈述 + 检测方法）
- `audit-target-set.md` §5（目标集与权威计数）
- `adversarial-probing-notes-cycle2.md`（Phase 1 A2 盲区探查，证明本表无对抗新增遗漏）
- `adjudication-table-cycle2.md`（I3' 逐条裁决，本文件的下游消费者）
