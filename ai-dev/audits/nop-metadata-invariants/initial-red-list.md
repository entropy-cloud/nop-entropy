# nop-metadata Invariant Initial Red List（棘轮零点快照）

> 产出方：plan `2026-08-13-1930-2`（Cycle 1 / I1 — 首批不变式沉淀为门禁，Phase C）
> 实测日期：2026-08-13（live repo，门禁首次运行）
> 上游：`invariant-catalog.md`（4 条不变式）、`audit-target-set.md`（目标集）
> 下游消费者：I2（按不变式审计全集）、I3（裁决 red list）、I5（零命中收口对比基准）

## 目的

本文件记录 **4 条门禁首次运行的全部命中**，作为：
- **I2/I3 的输入**：I2 跑门禁产出 red list 时以此为对比基线；I3 逐条裁决（P0/P1/defer）。
- **I5"零命中"的棘轮零点**：I4 每修复一项，本快照对应条目标记 resolved；I5 全清后门禁提升为 hard gate。

**棘轮规则**：已沉淀的不变式只增不减。本快照的命中总数（81）= I5 收口对比基准。

---

## 汇总

| 门禁 | 不变式 | 命中数 | 退出码 |
|------|--------|--------|--------|
| `check-silent-swallow.mjs` | INV-SILENT-SWALLOW | **80** | 1 |
| `check-orm-unique-key-constraint.mjs` | INV-UK | **0** | 0 |
| `check-sensitive-literal-leak.mjs` | INV-SENSITIVE | **0** | 0 |
| `TestLimitNegativeValueInvariant` | INV-LIMIT | **1**（FAIL） | 1（非零） |
| **合计** | | **81** | — |

> **说明**：INV-UK 与 INV-SENSITIVE 当前命中 = 0（历史违规已在 R3.19 / R6.2 / R8.2 / R8.4b 修复）。这两条门禁当前为**防回退门禁**，保护已修复的违规不再复发。INV-SILENT-SWALLOW 的 80 命中为**I2 对抗探查的主要审计面**（130 catch 块中 80 个不满足传播 ErrorCode 的信号——I3 将逐条裁决是否为有意裁定）。INV-LIMIT 的 1 命中（queryTableData）为 **MA7.4-03 已裁定的静默封顶语义**，I3 将裁决是否保持或改为显式拒绝。

---

## 门禁运行命令清单

以下 4 条命令可一键复跑，结果确定可复现：

```bash
# 1. INV-SILENT-SWALLOW: 静默吞异常扫描（130 catch 块）
node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata

# 2. INV-UK: unique-key constraint 完备性扫描（37 unique-key）
node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata

# 3. INV-SENSITIVE: 敏感字面量脱敏扫描
node ai-dev/tools/check-sensitive-literal-leak.mjs --module nop-metadata

# 4. INV-LIMIT: limit 负值穷举测试（默认从 surefire 排除，单独调用）
./mvnw test -pl nop-metadata-service -Dtest=TestLimitNegativeValueInvariant -Dsurefire.failIfNoSpecifiedTests=false
```

自验证 fixture（证明门禁非空壳）：

```bash
node ai-dev/tools/check-silent-swallow.mjs --fixture           # PASS
node ai-dev/tools/check-orm-unique-key-constraint.mjs --fixture # PASS
node ai-dev/tools/check-sensitive-literal-leak.mjs --fixture    # PASS
```

---

## §1 INV-SILENT-SWALLOW 命中清单（80 项）

> 扫描目标集：130 catch 块 / 46 文件（`nop-metadata-service/src/main/java`，I0 audit-target-set §1.3）
>
> 检测规则：catch 块花括号跨度内不出现以下任一信号 → 命中：`throw`、`NopMetadataException(`、`.errorCode(`、`ErrorCode.`、`BizException`、`Biz.fatal(`
>
> 对应不变式：INV-SILENT-SWALLOW（`invariant-catalog.md`）
> 历史同族 finding-ID：P2-06/07/09（R6.4）、P2-01/02/04（R6.5）、AR-21（R8.4a）

| # | 文件:行 | catch 块 (起止) | 对应历史 finding-ID（如可回溯） |
|---|---------|-----------------|-------------------------------|
| 1 | `connection/MetaDataSourceConnectionProcessor.java:99` | L99-L101 | — |
| 2 | `connection/MetaDataSourceConnectionProcessor.java:156` | L156-L161 | P2-12（R6.2，同文件敏感字面量已修） |
| 3 | `connection/MetaDataSourceConnectionProcessor.java:362` | L362-L364 | — |
| 4 | `connection/MetaDataSourceConnectionProcessor.java:385` | L385-L387 | — |
| 5 | `contract/MetaContractChecker.java:335` | L335-L337 | — |
| 6 | `entity/AutoClassificationProcessor.java:105` | L105-L109 | AR-21（R8.4a 同文件已修） |
| 7 | `entity/AutoClassificationProcessor.java:141` | L141-L148 | P2-02 / AR-21（R6.5/R8.4a 同文件已修） |
| 8 | `entity/LineageTagPropagationProcessor.java:158` | L158-L165 | AR-21（R8.4a 同文件已修） |
| 9 | `entity/NopMetaDataSourceBizModel.java:201` | L201-L208 | — |
| 10 | `entity/NopMetaDataSourceBizModel.java:259` | L259-L261 | — |
| 11 | `entity/NopMetaDataSourceBizModel.java:325` | L325-L332 | — |
| 12 | `entity/NopMetaDataSourceBizModel.java:456` | L456-L459 | — |
| 13 | `entity/NopMetaEntityBizModel.java:70` | L70-L74 | — |
| 14 | `entity/NopMetaGlossaryTermBizModel.java:111` | L111-L114 | — |
| 15 | `entity/NopMetaLineageEdgeQueryAction.java:165` | L165-L169 | — |
| 16 | `entity/NopMetaLineageEdgeQueryAction.java:221` | L221-L225 | — |
| 17 | `entity/NopMetaLineageEdgeQueryAction.java:338` | L338-L346 | — |
| 18 | `entity/NopMetaModuleBizModel.java:320` | L320-L322 | — |
| 19 | `entity/NopMetaModuleBizModel.java:394` | L394-L397 | — |
| 20 | `entity/NopMetaModuleBizModel.java:484` | L484-L493 | P2-07（R6.4 同文件已修） |
| 21 | `entity/NopMetaProfilingRuleBizModel.java:193` | L193-L196 | — |
| 22 | `entity/NopMetaQualityCheckpointBizModel.java:296` | L296-L299 | — |
| 23 | `entity/NopMetaQualityCheckpointBizModel.java:308` | L308-L313 | — |
| 24 | `entity/NopMetaQualityCheckpointBizModel.java:361` | L361-L370 | — |
| 25 | `entity/NopMetaQualityCheckpointBizModel.java:390` | L390-L395 | — |
| 26 | `entity/NopMetaQualityCheckpointBizModel.java:445` | L445-L448 | — |
| 27 | `entity/NopMetaQualityRuleBizModel.java:164` | L164-L166 | — |
| 28 | `entity/NopMetaQualityRuleBizModel.java:259` | L259-L265 | — |
| 29 | `entity/NopMetaQualityRuleBizModel.java:397` | L397-L400 | — |
| 30 | `entity/NopMetaTableQueryAction.java:240` | L240-L243 | — |
| 31 | `entity/NopMetaTagLabelBizModel.java:119` | L119-L123 | P2-09（R6.4 同文件已修） |
| 32 | `entity/NopMetaTagLabelBizModel.java:176` | L176-L178 | — |
| 33 | `profiling/MetaTableProfiler.java:136` | L136-L139 | — |
| 34 | `profiling/MetaTableProfiler.java:184` | L184-L189 | — |
| 35 | `profiling/MetaTableProfiler.java:218` | L218-L220 | — |
| 36 | `profiling/MetaTableProfiler.java:436` | L436-L439 | — |
| 37 | `profiling/MetaTableProfiler.java:455` | L455-L457 | — |
| 38 | `profiling/MetaTableProfiler.java:480` | L480-L482 | — |
| 39 | `quality/CheckpointActionDispatcher.java:155` | L155-L158 | — |
| 40 | `quality/CheckpointActionDispatcher.java:188` | L188-L191 | — |
| 41 | `quality/CheckpointActionDispatcher.java:392` | L392-L394 | — |
| 42 | `quality/MetaQualityCheckpointExecutor.java:156` | L156-L183 | P2-04（R6.5 同文件已修） |
| 43 | `quality/MetaQualityCheckpointExecutor.java:178` | L178-L182 | — |
| 44 | `quality/MetaQualityCheckpointExecutor.java:353` | L353-L358 | P2-04（R6.5 同文件已修） |
| 45 | `quality/MetaQualityCheckpointExecutor.java:396` | L396-L401 | — |
| 46 | `quality/MetaQualityCheckpointScheduler.java:149` | L149-L152 | — |
| 47 | `quality/MetaQualityCheckpointScheduler.java:174` | L174-L176 | — |
| 48 | `quality/MetaQualityCheckpointScheduler.java:213` | L213-L228 | — |
| 49 | `quality/MetaQualityCheckpointScheduler.java:259` | L259-L264 | — |
| 50 | `quality/MetaQualityCheckpointScheduler.java:294` | L294-L306 | — |
| 51 | `quality/MetaQualityCheckpointScheduler.java:302` | L302-L304 | — |
| 52 | `quality/MetaQualityCheckpointScheduler.java:324` | L324-L328 | — |
| 53 | `quality/MetaQualityRuleExecutor.java:305` | L305-L310 | AR-16（R8.2 同文件敏感字面量已修） |
| 54 | `quality/MetaQualityRuleExecutor.java:528` | L528-L533 | — |
| 55 | `quality/MetaQualityRuleExecutor.java:571` | L571-L584 | — |
| 56 | `quality/MetaQualityRuleExecutor.java:702` | L702-L704 | — |
| 57 | `quality/MetaQualityRuleExecutor.java:709` | L709-L711 | — |
| 58 | `quality/MetaQualityRuleExecutor.java:842` | L842-L844 | — |
| 59 | `quality/MetaQualityScorer.java:265` | L265-L270 | — |
| 60 | `quality/QualityAlertWorkflowProcessor.java:148` | L148-L152 | — |
| 61 | `query/AggregationHelper.java:472` | L472-L475 | P2-06（R6.4 同文件已修） |
| 62 | `query/CrossDbFieldResolver.java:225` | L225-L228 | — |
| 63 | `query/JoinMixedSideResolver.java:120` | L120-L123 | — |
| 64 | `reconciliation/LocalReconciliationProcessor.java:190` | L190-L195 | — |
| 65 | `search/NopMetaIndexBuilder.java:73` | L73-L77 | — |
| 66 | `search/NopMetaIndexBuilder.java:114` | L114-L120 | — |
| 67 | `search/NopMetaIndexBuilder.java:137` | L137-L143 | — |
| 68 | `search/NopMetaIndexBuilder.java:147` | L147-L153 | — |
| 69 | `search/NopMetaIndexBuilder.java:192` | L192-L196 | — |
| 70 | `search/NopMetaIndexBuilder.java:215` | L215-L218 | — |
| 71 | `search/NopMetaIndexBuilder.java:239` | L239-L242 | — |
| 72 | `search/NopMetaIndexBuilder.java:263` | L263-L266 | — |
| 73 | `search/NopMetaIndexBuilder.java:286` | L286-L289 | — |
| 74 | `search/NopMetaIndexBuilder.java:310` | L310-L313 | — |
| 75 | `search/NopMetaIndexBuilder.java:334` | L334-L337 | — |
| 76 | `security/HostSecurityUtil.java:141` | L141-L143 | — |
| 77 | `security/HostSecurityUtil.java:268` | L268-L270 | — |
| 78 | `security/HostSecurityUtil.java:281` | L281-L284 | — |
| 79 | `sqlview/SqlViewFieldTypeInferrer.java:199` | L199-L203 | — |
| 80 | `tableref/TableReferenceExecutor.java:141` | L141-L144 | — |

> 文件路径相对于 `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/`。
> 每条命中可被独立复跑定位：`rg -n 'catch' <file> | grep <line>`。

---

## §2 INV-UK 命中清单（0 项）

> 扫描目标集：37 unique-key / 39 entity（`nop-metadata/model/nop-metadata.orm.xml`，I0 audit-target-set §2.2）
>
> 检测规则：`<unique-key>` 元素缺 `constraint=` 或 `columns=` 属性 → 命中
>
> 对应不变式：INV-UK（`invariant-catalog.md`）
> 历史同族 finding-ID：MA7.3-01 / P2-MA6.6-001 / R3.19（已修复，Lesson 09）
>
> **当前命中 = 0**（live 实测 37/37 全带 `constraint=` 与 `columns=`，DDL 已物化——见 audit-target-set §2.3）。本门禁为防回退门禁。

---

## §3 INV-SENSITIVE 命中清单（0 项）

> 扫描目标集：error/log message 构造点（`.param(...)` / `LOG.*(...)` 字面量入参，I0 audit-target-set §1.5）
>
> 检测规则：同一行同时出现 logger/error-builder token 与匹配字面量（JDBC-URL / ≥2 个 SQL 关键字）→ 命中
>
> 对应不变式：INV-SENSITIVE（`invariant-catalog.md`）
> 历史同族 finding-ID：P2-12（R6.2 fixed）、AR-16（R8.2 fixed）、AR-23⑩（R8.4b fixed）
>
> **当前命中 = 0**。历史命中点（MetaDataSourceConnectionProcessor ARG_RAW_JDBC_URL、MetaQualityRuleExecutor SQL 字面量、MetaModelChangedEventPublisher Map 分支）均已修复为脱敏形式。本门禁为防回退门禁。

---

## §4 INV-LIMIT 命中清单（1 项 FAIL）

> 扫描目标集：4 个接受 `@Name("limit")` 入参的 public 方法（I0 audit-target-set §1.4）
>
> 检测规则：`limit = -1` 必须抛带 ErrorCode 的异常（reject），不抛 = FAIL = red list 条目
>
> 对应不变式：INV-LIMIT（`invariant-catalog.md`）
> 历史同族 finding-ID：AR-09（R7.3 fixed）、AR-23④（R8.2 fixed）

| # | 方法 | limit 处理 | 结果 | 说明 |
|---|------|-----------|------|------|
| 1 | `NopMetaTableBizModel#queryTableData` | `normalizeQueryLimit(-1)` | **FAIL** | `limit <= 0` → 缺省值（静默封顶，不抛异常）。MA7.4-03 已裁定为"静默封顶语义"，但 INV-LIMIT 陈述"负值必须显式失败"。I3 将裁决：保持 MA7.4-03 裁定（标记 intentional / defer），还是改为显式拒绝。 |
| 2 | `NopMetaTableBizModel#queryJoinData` | `normalizeJoinQueryLimit(-1)` | PASS | `limit < 0` → 抛 `ERR_PAGINATION_LIMIT_INVALID`（AR-09 已修） |
| 3 | `NopMetaTableBizModel#queryAggregation` | `normalizeJoinQueryLimit(-1)` | PASS | `limit < 0` → 抛 `ERR_PAGINATION_LIMIT_INVALID`（AR-09 已修） |
| 4 | `NopMetaSearchBizModel#searchMetadata` | 内联检查 `limit < 0` | PASS | `limit < 0` → 抛 `ERR_SEARCH_LIMIT_INVALID`（AR-23④ 已修） |

> 表完备性自检（`TestLimitTargetSetCompleteness`）在默认 surefire 集合中 PASS，确认方法表 = 4 = I0 目标集 limit-taking 方法数。

---

## 棘轮零点声明

> **本快照命中总数 = 81（80 silent-swallow + 0 UK + 0 sensitive + 1 limit）= I5"零命中"收口的对比基准。**
>
> 后续规则：
> - I3 裁决每条命中为 P0/P1/defer。P0/P1 派 I4 修复。
> - I4 每修复一项，本快照对应条目标记 `resolved`，门禁复跑命中数递减。
> - I5 全清（4 条门禁零命中）后，门禁提升为 hard gate（零命中才绿）。
> - **已沉淀的不变式只增不减**（棘轮规则，`invariant-loop-audit-prompt.md` 关键机制 #2）：弱化/删除/豁免需人工确认 + 留痕 + committed 回归测试同步。

---

## 引用

- `invariant-catalog.md`（4 条不变式陈述 + 检测方法）
- `audit-target-set.md`（方法全集 + ORM 全集 + 覆盖率基准）
- `ai-dev/audits/arm-index-nop-metadata.md`（全部 finding-ID 可定位）
- `ai-dev/skills/invariant-loop-audit-prompt.md`（方法论 + 棘轮规则）
- `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I0–I6 路线图）
