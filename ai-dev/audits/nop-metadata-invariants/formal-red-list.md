# nop-metadata Invariant 正式 Red List（Formal Red List）

> 产出方：plan `2026-08-13-1930-3`（Cycle 1 / I2 — 不变式驱动审计，Phase 1）
> 实测日期：2026-08-13（live repo，4 条门禁正式复跑）
> 上游：`invariant-catalog.md`（4 条不变式）、`audit-target-set.md`（目标集）、`initial-red-list.md`（I1 初始快照）
> 下游消费者：I3（逐条裁决，见 `adjudication-table.md`）、I4（P0/P1 修复）、I5（零命中收口对比基准）

## 目的

本文件记录 **4 条门禁在 I2 正式审计轮次跨全部目标集的全部命中**，是 I3 裁决的唯一输入。每条命中含：门禁名 / `文件:行` / 不变式编号 / 族归属 / 已知或新发现 / 可复跑证据。

**与 I1 初始快照的关系**：I1 快照是"门禁首次运行"的记录；本文件是"I2 正式审计轮次"的记录。两者比对用于确认门禁确定性（见 §一致性核对）。

---

## §0 门禁运行命令（可一键复跑）

```bash
# 1. INV-SILENT-SWALLOW: 静默吞异常扫描（130 catch 块）
node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata        # 退出码 1 = 有命中

# 2. INV-UK: unique-key constraint 完备性扫描（35 unique-key；2026-08-16 P2-29 删 2 个冗余 per-scope FQN UK 前为 37，见 §INV-UK 当前命中注记）
node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata   # 退出码 0 = 零命中

# 3. INV-SENSITIVE: 敏感字面量脱敏扫描
node ai-dev/tools/check-sensitive-literal-leak.mjs --module nop-metadata      # 退出码 0 = 零命中

# 4. INV-LIMIT: limit 负值穷举测试（默认从 surefire 排除，单独调用）
./mvnw test -pl nop-metadata/nop-metadata-service -Dtest=TestLimitNegativeValueInvariant -Dsurefire.failIfNoSpecifiedTests=false
#   4 tests, 1 FAIL(queryTableData) = limit red list
```

---

## §汇总

| 门禁 | 不变式 | 命中数 | 退出码 | 族归属 | 已知/新发现 |
|------|--------|--------|--------|--------|------------|
| `check-silent-swallow.mjs` | INV-SILENT-SWALLOW | **80** | 1 | silent-swallow | 已知族（7 兄弟先例已修；本次为更广子模式面） |
| `check-orm-unique-key-constraint.mjs` | INV-UK | **0** | 0 | unique-key | 防回退门禁（零命中） |
| `check-sensitive-literal-leak.mjs` | INV-SENSITIVE | **0** | 0 | sensitive-literal | 防回退门禁（零命中） |
| `TestLimitNegativeValueInvariant` | INV-LIMIT | **1**（FAIL） | 1（非零） | limit 负值 | 已知（MA7.4-03 裁定复核点） |
| **合计** | | **81** | — | — | — |

> 对抗探查新增（Phase 2）：**0** 条并入本表。对抗探查的 3 个方向（catch 跨子模块、ORM index/DDL 漂移、门禁子模式）均结论为"无新族命中"，详见 `adversarial-probing-notes.md`。

---

## §一致性核对（与 I1 初始快照比对）

| 维度 | I1 初始快照 | I2 正式 red list（本文件） | 结论 |
|------|-------------|----------------------------|------|
| INV-SILENT-SWALLOW | 80 | 80 | **一致** |
| INV-UK | 0 | 0 | **一致** |
| INV-SENSITIVE | 0 | 0 | **一致** |
| INV-LIMIT | 1（queryTableData FAIL） | 1（queryTableData FAIL） | **一致** |
| **合计** | **81** | **81** | **一致** |

**归因**：I1→I2 期间 `nop-metadata-service/src/main/java` **零 commit**（`git log --since=2026-08-13 -- nop-metadata/nop-metadata-service/src/main/java` 为空）。命中集完全一致属预期——源码未变，门禁确定性确认。无 commit 漂移、无门禁非确定性缺陷需要深查。

> 目标集稳定性旁证（复跑 I0 计数口径）：`@BizModel`=40、`@BizQuery`=13、`@BizMutation`=30、catch 块=130 / 46 文件、limit-taking 入口=4 —— 全部与 I0 `audit-target-set.md` 一致，无 I0 漏扫、无新增变更型方法脱离门禁覆盖。

---

## §1 INV-SILENT-SWALLOW 命中清单（80 项）

> **扫描目标集**：130 catch 块 / 46 文件（`nop-metadata/nop-metadata-service/src/main/java`，I0 audit-target-set §1.3）—— 实测覆盖完整。
>
> **检测规则**：catch 块花括号跨度内不出现以下任一信号 → 命中：`throw`、`NopMetadataException(`、`.errorCode(`、`ErrorCode.`、`BizException`、`Biz.fatal(`。
>
> **族归属**：silent-swallow（fail-loud）。**对应不变式**：INV-SILENT-SWALLOW。
>
> **历史同族 finding-ID**（先例链）：P2-06/07/09（R6.4 fixed）、P2-01/02/04（R6.5 fixed）、AR-21（R8.4a fixed）—— 这 7 个"空 catch / getMessage-only"子模式**已修复**；本族 80 命中为该族的**更广子模式面**（"LOG + 降级 / LOG + 继续未传播 ErrorCode"），是 I3 裁决的主要面。
>
> **语义子类**（I3 裁决与 I4 类别清扫的工作形状参考，非最终裁决）：
> - **S1 类型探测回退**（`SQLException ignore` / `NumberFormatException` 用于列类型判别，回退到备选读法）：本面典型 — `MetaTableProfiler`、`MetaQualityRuleExecutor` 的 `ignore` 系列。
> - **S2 批量失败隔离**（catch 在循环内，捕获到 errors 列表 + errorCount + 结果行，但不 rethrow）：`MetaQualityCheckpointExecutor`、`MetaQualityCheckpointScheduler`、`CheckpointActionDispatcher`。
> - **S3 优雅降级返回空/错误结果**（捕获异常入结构化 errors，返回降级结果）：`NopMetaLineageEdgeQueryAction`、`NopMetaIndexBuilder`（11 处索引构建降级）。
> - **S4 良性 best-effort / 可选配置**（SecurityException / NumberFormatException 等 JVM 或解析环境拒绝，属可选调优）：`MetaDataSourceConnectionProcessor`、`HostSecurityUtil`。
> - **S5 通用 Exception catch + LOG 未传播 ErrorCode**（无结构化错误捕获的泛 catch）：`NopMetaDataSourceBizModel`、`NopMetaModuleBizModel` 等。

| # | 文件:行（相对 `nop-metadata-service/.../service/`） | catch 块 | 语义子类 | 对应历史 finding-ID |
|---|------------------------------------------------------|----------|----------|---------------------|
| 1 | `connection/MetaDataSourceConnectionProcessor.java:99` | L99-L101 | S4 | — |
| 2 | `connection/MetaDataSourceConnectionProcessor.java:156` | L156-L161 | S5 | P2-12 同文件（敏感字面量已修） |
| 3 | `connection/MetaDataSourceConnectionProcessor.java:362` | L362-L364 | S4 | — |
| 4 | `connection/MetaDataSourceConnectionProcessor.java:385` | L385-L387 | S4 | — |
| 5 | `contract/MetaContractChecker.java:335` | L335-L337 | S1 | — |
| 6 | `entity/AutoClassificationProcessor.java:105` | L105-L109 | S2/S5 | AR-21 同文件已修 |
| 7 | `entity/AutoClassificationProcessor.java:141` | L141-L148 | S2/S5 | P2-02 / AR-21 同文件已修 |
| 8 | `entity/LineageTagPropagationProcessor.java:158` | L158-L165 | S2/S5 | AR-21 同文件已修 |
| 9 | `entity/NopMetaDataSourceBizModel.java:201` | L201-L208 | S5 | — |
| 10 | `entity/NopMetaDataSourceBizModel.java:259` | L259-L261 | S5 | — |
| 11 | `entity/NopMetaDataSourceBizModel.java:325` | L325-L332 | S5 | — |
| 12 | `entity/NopMetaDataSourceBizModel.java:456` | L456-L459 | S5 | — |
| 13 | `entity/NopMetaEntityBizModel.java:70` | L70-L74 | S5 | — |
| 14 | `entity/NopMetaGlossaryTermBizModel.java:111` | L111-L114 | S5 | — |
| 15 | `entity/NopMetaLineageEdgeQueryAction.java:165` | L165-L169 | S3 | — |
| 16 | `entity/NopMetaLineageEdgeQueryAction.java:221` | L221-L225 | S3 | — |
| 17 | `entity/NopMetaLineageEdgeQueryAction.java:338` | L338-L346 | S3 | — |
| 18 | `entity/NopMetaModuleBizModel.java:320` | L320-L322 | S5 | — |
| 19 | `entity/NopMetaModuleBizModel.java:394` | L394-L397 | S5 | — |
| 20 | `entity/NopMetaModuleBizModel.java:484` | L484-L493 | S2/S5 | P2-07 同文件已修 |
| 21 | `entity/NopMetaProfilingRuleBizModel.java:193` | L193-L196 | S1 | — |
| 22 | `entity/NopMetaQualityCheckpointBizModel.java:296` | L296-L299 | S5 | — |
| 23 | `entity/NopMetaQualityCheckpointBizModel.java:308` | L308-L313 | S5 | — |
| 24 | `entity/NopMetaQualityCheckpointBizModel.java:361` | L361-L370 | S2/S5 | — |
| 25 | `entity/NopMetaQualityCheckpointBizModel.java:390` | L390-L395 | S5 | — |
| 26 | `entity/NopMetaQualityCheckpointBizModel.java:445` | L445-L448 | S5 | — |
| 27 | `entity/NopMetaQualityRuleBizModel.java:164` | L164-L166 | S5 | — |
| 28 | `entity/NopMetaQualityRuleBizModel.java:259` | L259-L265 | S5 | — |
| 29 | `entity/NopMetaQualityRuleBizModel.java:397` | L397-L400 | S1 | — |
| 30 | `entity/NopMetaTableQueryAction.java:240` | L240-L243 | S1 | — |
| 31 | `entity/NopMetaTagLabelBizModel.java:119` | L119-L123 | S5 | P2-09 同文件已修 |
| 32 | `entity/NopMetaTagLabelBizModel.java:176` | L176-L178 | S5 | — |
| 33 | `profiling/MetaTableProfiler.java:136` | L136-L139 | S1 | — |
| 34 | `profiling/MetaTableProfiler.java:184` | L184-L189 | S1 | — |
| 35 | `profiling/MetaTableProfiler.java:218` | L218-L220 | S1 | — |
| 36 | `profiling/MetaTableProfiler.java:436` | L436-L439 | S1 | — |
| 37 | `profiling/MetaTableProfiler.java:455` | L455-L457 | S1 | — |
| 38 | `profiling/MetaTableProfiler.java:480` | L480-L482 | S1 | — |
| 39 | `quality/CheckpointActionDispatcher.java:155` | L155-L158 | S2 | — |
| 40 | `quality/CheckpointActionDispatcher.java:188` | L188-L191 | S2 | — |
| 41 | `quality/CheckpointActionDispatcher.java:392` | L392-L394 | S4 | — |
| 42 | `quality/MetaQualityCheckpointExecutor.java:156` | L156-L183 | S2 | P2-04 同文件已修 |
| 43 | `quality/MetaQualityCheckpointExecutor.java:178` | L178-L182 | S2 | — |
| 44 | `quality/MetaQualityCheckpointExecutor.java:353` | L353-L358 | S2 | P2-04 同文件已修 |
| 45 | `quality/MetaQualityCheckpointExecutor.java:396` | L396-L401 | S2 | — |
| 46 | `quality/MetaQualityCheckpointScheduler.java:149` | L149-L152 | S2 | — |
| 47 | `quality/MetaQualityCheckpointScheduler.java:174` | L174-L176 | S2 | — |
| 48 | `quality/MetaQualityCheckpointScheduler.java:213` | L213-L228 | S2 | — |
| 49 | `quality/MetaQualityCheckpointScheduler.java:259` | L259-L264 | S2 | — |
| 50 | `quality/MetaQualityCheckpointScheduler.java:294` | L294-L306 | S2 | — |
| 51 | `quality/MetaQualityCheckpointScheduler.java:302` | L302-L304 | S2 | — |
| 52 | `quality/MetaQualityCheckpointScheduler.java:324` | L324-L328 | S2 | — |
| 53 | `quality/MetaQualityRuleExecutor.java:305` | L305-L310 | S1 | AR-16 同文件（敏感字面量已修） |
| 54 | `quality/MetaQualityRuleExecutor.java:528` | L528-L533 | S1 | — |
| 55 | `quality/MetaQualityRuleExecutor.java:571` | L571-L584 | S1/S2 | — |
| 56 | `quality/MetaQualityRuleExecutor.java:702` | L702-L704 | S1 | — |
| 57 | `quality/MetaQualityRuleExecutor.java:709` | L709-L711 | S1 | — |
| 58 | `quality/MetaQualityRuleExecutor.java:842` | L842-L844 | S1 | — |
| 59 | `quality/MetaQualityScorer.java:265` | L265-L270 | S2 | — |
| 60 | `quality/QualityAlertWorkflowProcessor.java:148` | L148-L152 | S2 | — |
| 61 | `query/AggregationHelper.java:472` | L472-L475 | S1 | P2-06 同文件已修 |
| 62 | `query/CrossDbFieldResolver.java:225` | L225-L228 | S5 | — |
| 63 | `query/JoinMixedSideResolver.java:120` | L120-L123 | S5 | — |
| 64 | `reconciliation/LocalReconciliationProcessor.java:190` | L190-L195 | S2/S5 | — |
| 65 | `search/NopMetaIndexBuilder.java:73` | L73-L77 | S3 | — |
| 66 | `search/NopMetaIndexBuilder.java:114` | L114-L120 | S3 | — |
| 67 | `search/NopMetaIndexBuilder.java:137` | L137-L143 | S3 | — |
| 68 | `search/NopMetaIndexBuilder.java:147` | L147-L153 | S3 | — |
| 69 | `search/NopMetaIndexBuilder.java:192` | L192-L196 | S3 | — |
| 70 | `search/NopMetaIndexBuilder.java:215` | L215-L218 | S3 | — |
| 71 | `search/NopMetaIndexBuilder.java:239` | L239-L242 | S3 | — |
| 72 | `search/NopMetaIndexBuilder.java:263` | L263-L266 | S3 | — |
| 73 | `search/NopMetaIndexBuilder.java:286` | L286-L289 | S3 | — |
| 74 | `search/NopMetaIndexBuilder.java:310` | L310-L313 | S3 | — |
| 75 | `search/NopMetaIndexBuilder.java:334` | L334-L337 | S3 | — |
| 76 | `security/HostSecurityUtil.java:141` | L141-L143 | S4 | — |
| 77 | `security/HostSecurityUtil.java:268` | L268-L270 | S4 | — |
| 78 | `security/HostSecurityUtil.java:281` | L281-L284 | S4 | — |
| 79 | `sqlview/SqlViewFieldTypeInferrer.java:199` | L199-L203 | S1 | — |
| 80 | `tableref/TableReferenceExecutor.java:141` | L141-L144 | S1 | — |

> **复跑定位**：文件路径相对于 `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/`。每条可独立复跑：`node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata` 输出与本表 1:1 对应（含 catch 起止行 + 首行 snippet）。
>
> **语义子类分布（I3 裁决参考）**：S1 类型探测回退 ≈ 17（#5,21,29,30,33-38,53,54,56,57,58,61,79,80）；S2 批量失败隔离 ≈ 21（#6,7,8,20,24,39,40,42-52,55,59,60,64）；S3 优雅降级 ≈ 14（#15,16,17,65-75）；S4 良性 best-effort ≈ 8（#1,3,4,41,76,77,78）；S5 通用 catch+LOG ≈ 20（#2,9-14,18,19,22,23,25-28,31,32,62,63）。子类为裁决工作形状参考，**不预设裁决结论**（裁决归 Phase 3 / `adjudication-table.md`）。

---

## §2 INV-UK 命中清单（0 项）

> **扫描目标集**：37 unique-key / 39 entity（`nop-metadata/model/nop-metadata.orm.xml`，I0 audit-target-set §2.2）。
>
> **检测规则**：`<unique-key>` 元素缺 `constraint=` 或 `columns=` → 命中。
>
> **族归属**：unique-key constraint 完备性。**对应不变式**：INV-UK。
>
> **历史同族 finding-ID**：MA7.3-01 / P2-MA6.6-001 / R3.19（已修复，Lesson 09）。
>
> **当前命中 = 0**（live 实测全带 `constraint=` + `columns=`——I0 轮口径 37/37，2026-08-16 plan-2026-08-16-0920-1 P2-29 删 2 个冗余 per-scope FQN UK 后为 35/35；DDL 三方言已物化 UNIQUE —— 见 `audit-target-set.md` §2.3）。本门禁为**防回退门禁**，本族 red list 分量 = 0，无 I3 裁决条目。

---

## §3 INV-SENSITIVE 命中清单（0 项）

> **扫描目标集**：error/log message 构造点（`.param(...)` / `LOG.*(...)` 字面量入参，I0 audit-target-set §1.5）。
>
> **检测规则**：同一行同时出现 logger/error-builder token 与匹配字面量（JDBC-URL `jdbc:...//` / ≥2 个 SQL 关键字的内联 SQL literal）→ 命中。
>
> **族归属**：敏感字面量脱敏。**对应不变式**：INV-SENSITIVE。
>
> **历史同族 finding-ID**：P2-12（R6.2 fixed）、AR-16（R8.2 fixed）、AR-23⑩（R8.4b fixed）。
>
> **当前命中 = 0**。历史命中点（`MetaDataSourceConnectionProcessor` ARG_RAW_JDBC_URL、`MetaQualityRuleExecutor` SQL 字面量、`MetaModelChangedEventPublisher` Map 分支）均已修复为脱敏形式（脱敏 jdbcUrl / sqlHash）。本门禁为**防回退门禁**，本族 red list 分量 = 0，无 I3 裁决条目。

---

## §4 INV-LIMIT 命中清单（1 项 FAIL）

> **扫描目标集**：4 个接受 `@Name("limit")` 入参的 public 入口方法（I0 audit-target-set §1.4）—— 复跑仍为 4，无新增。
>
> **检测规则**：`limit = -1` 必须抛带 ErrorCode 的异常（reject），不抛 = FAIL = red list 条目。
>
> **族归属**：limit 负值校验。**对应不变式**：INV-LIMIT。
>
> **历史同族 finding-ID**：AR-09（R7.3 fixed）、AR-23④（R8.2 fixed）。

| # | 方法 | limit 处理（live） | 结果 | 语义说明 |
|---|------|-------------------|------|----------|
| L1 | `NopMetaTableBizModel#queryTableData` | `normalizeQueryLimit(-1)` → null/≤0 取缺省值（**静默封顶**） | **FAIL** | INV-LIMIT 陈述"负值必须显式失败"，但 MA7.4-03 此前裁定为"静默封顶语义"。**契约冲突点** —— I3 必须裁决：保持 MA7.4-03（→ 需按棘轮规则人工确认弱化 INV-LIMIT 并登记例外）或改为显式拒绝（→ 行为变更，需人工确认）。 |
| L2 | `NopMetaTableBizModel#queryJoinData` | `normalizeJoinQueryLimit(-1)` → 抛 `ERR_PAGINATION_LIMIT_INVALID` | PASS | AR-09 已修 |
| L3 | `NopMetaTableBizModel#queryAggregation` | `normalizeJoinQueryLimit(-1)` → 抛 `ERR_PAGINATION_LIMIT_INVALID` | PASS | AR-09 已修 |
| L4 | `NopMetaSearchBizModel#searchMetadata` | 内联 `limit < 0` → 抛 `ERR_SEARCH_LIMIT_INVALID` | PASS | AR-23④ 已修 |

> 表完备性自检 `TestLimitTargetSetCompleteness`（默认 surefire 集合）PASS：方法表 = 4 = I0 目标集 limit-taking 方法数。本族 red list 分量 = 1（L1），I3 裁决为契约冲突 → 派 I4 + 人工确认。

---

## §5 对抗探查新增（Phase 2 并入）

> Phase 2 对抗探查 3 个方向均结论"无新族命中"（详见 `adversarial-probing-notes.md`）：
> - **catch 跨子模块**：nop-metadata 其余子模块（web/app/core/api/dao）catch 块 = **0**；门禁 scope 完整，无 I0 漏扫。
> - **ORM index / DDL 漂移**：ORM 63 个 `<index>` 未出现在 `_create` DDL，但经全仓比对（nop-wf/nop-ai/nop-job/nop-retry 同构）确认为**平台级 by-design**（index 由运行时 schema/diff 工具管理，非 `_create` 部署产物），属优化类而非 nop-metadata 正确性缺陷 —— **不构成本轮新族**，不触发 Cycle 2/I1。
> - **门禁子模式覆盖**：INV-SILENT-SWALLOW 为 `java-lint-empty-catch` + `java-lint-getmessage-only` 的语义超集，已覆盖"log.warn 后继续"等非空但未传播 ErrorCode 的子模式（实证：`MetaDataSourceConnectionProcessor:99` 即此模式且被命中）。
>
> **本表无对抗探查新增条目**。所有 81 命中均来自 4 条门禁的确定性扫描/穷举。

---

## §棘轮零点声明（与 I1 一致）

> **本正式 red list 命中总数 = 81（80 silent-swallow + 0 UK + 0 sensitive + 1 limit）= I5"零命中"收口的对比基准。** 与 I1 初始快照完全一致（源码零漂移）。
>
> 后续规则：
> - I3 裁决每条命中为 P0/P1/新族/deferred-with-reason（见 `adjudication-table.md`）。
> - I4 修复 P0/P1；类别清扫强制（修任一 processor 的 catch 必 grep 全部 processor 的 catch）。
> - I5 全清（4 条门禁零命中）后，门禁提升为 hard gate（零命中才绿）。
> - **已沉淀的不变式只增不减**（棘轮规则）：弱化/删除/豁免需人工确认 + 留痕 + committed 回归测试同步。

---

## 引用

- `initial-red-list.md`（I1 初始快照，本文件一致性核对基准）
- `invariant-catalog.md`（4 条不变式陈述 + 检测方法）
- `audit-target-set.md`（方法全集 + ORM 全集 + 覆盖率基准）
- `adversarial-probing-notes.md`（Phase 2 盲区探查，证明本表无新增遗漏）
- `adjudication-table.md`（Phase 3 逐条裁决，本文件的下游消费者）
- `ai-dev/audits/arm-index-nop-metadata.md`（全部 finding-ID 可定位）
