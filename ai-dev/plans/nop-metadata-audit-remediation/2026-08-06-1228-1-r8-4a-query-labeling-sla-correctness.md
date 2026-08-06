# R8.4a 查询/标注/契约正确性组修复（AR-20, AR-21, AR-22）

> Plan Status: completed
> Last Reviewed: 2026-08-06
> Mission: nop-metadata-audit-remediation
> Work Item: MR8（R8.4a 查询/标注/契约正确性组）
> Source: `ai-dev/audits/2026-08-05-2157-open-audit-nop-metadata-audit-remediation.md`（AR-20、AR-21、AR-22）、`ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR8 段 R8.4 行 + R8.0 裁决记录）、`ai-dev/audits/arm-index-nop-metadata.md`（R8.0 裁决记录表）
> Related: 执行顺序 `{1}` of 2（R8.4a 与 R8.4b 无共享代码文件——两份 plan 的收口 Phase 均更新 roadmap R8.4 行 / arm-index §P2，顺序执行下无冲突）；启动门禁：R8.0 done（已满足）。

## Purpose

修复查询/标注/契约域的 3 个已确认正确性缺陷（AR-20 方言无关 NULLS FIRST/LAST + 跨库 join 键精确类比较、AR-21 自动化标注 fail-loud 被 catch-all 吞掉 + 全局回退分类、AR-22 SLA 未知时间单位静默按毫秒解析，R8.0 全部提级为 P1 修复）：外部 JDBC 聚合路径在 MySQL 目标数据源上不再产出 NULLS FIRST/LAST 语法错误、跨库 join 整型键数值等值不再误拒、标注失败显式失败而非静默吞掉、SLA 配置笔误显式报错。产出 = 代码修复 + 判别性回归测试 + docs 复核 + arm-index/roadmap 终态。

## Current Baseline

经 2026-08-06 live repo 核对（finding 描述以审计报告为准；行号以 live 复核为准）：

- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` **1016/0**（R8.3 收口口径）
- **AR-20**（`AggregationHelper.java` + `CrossDbJoinMerger.java`）：
  - `AggregationHelper.java:314-348` `buildOrderByClause`：`:342-345` 当 `nullsFirst != null` 时无条件拼接 `" NULLS FIRST"` / `" NULLS LAST"`，无方言参数。**真实调用点 6 处**（live 复核）：
    1. `AggregationHelper.java:585`——`buildExternalAggregationSql`（:541-591）内部调用，**该函数已有 `dialect` 参数**（:548），方言在外部处理器（ExternalAggregationProcessor:80-81 等）经 `safeProductName(metaData)` 获得
    2. `MixedSameDbJoinAggregationProcessor.java:108`——**在 `withConnection` lambda 之前**调用（方言 :125 在 lambda 内才计算）
    3. `ExternalExternalJoinAggregationProcessor.java:83`——同上，**在 lambda 之前**（方言 :90 在 lambda 内）
    4. `EntityAggregationProcessor.java:138`（via-EQL 路径）——无方言变量（走 `orm().executeQuery`，IOrmTemplate 无方言 API）
    5. `EntityAggregationProcessor.java:228`（bypass-EQL 路径）——有 `productName`
    6. `EntityEntityJoinAggregationProcessor.java:123`——内部 ORM 路径，无方言变量
  - **方言集合**：`AggregationContext.java:23-24` / `GranularityBucketing.java:26` `SUPPORTED_DIALECTS = {H2, MySQL, PostgreSQL}`。MySQL 不支持 NULLS FIRST/LAST 语法（H2/PostgreSQL 支持）
  - MySQL 排序事实：ASC 默认 NULLs 在前、DESC 默认 NULLs 在后——`nullsFirst=true+ASC` / `nullsFirst=false+DESC` 与 MySQL 默认一致（省略子句语义不变）；`nullsFirst=false+ASC`（NULLS LAST in ASC）与 `nullsFirst=true+DESC`（NULLS FIRST in DESC）MySQL 无法表达
  - `CrossDbJoinMerger.java:122-136` `verifyCrossDbKeyTypeConsistency` 用 `Class.equals` 精确类比较（`leftType.equals(rightType)`），`:143-159` `firstNonNullKeyType` 要求单列内全部非 null 值同类；**:226-228 `stringKey` = `String.valueOf(v)`**——merge 匹配走 stringKey
  - **既有测试冲突（live 复核，必须 re-adjudicate）**：`TestMetaJoinCrossDbMergeNullSemantics.testTypeMismatchIntegerVsLongThrowsExplicitly`（:82-93）显式断言 Integer vs Long 必须抛 `ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH`（javadoc: "Integer vs Long key must explicitly fail (silent String coercion is forbidden)"）——与本次修复方向相反
- **AR-21**（`AutoClassificationProcessor.java` + `LineageTagPropagationProcessor.java`）：
  - `AutoClassificationProcessor.java:260-271` `doCreateAutomatedLabel`：`catch (Exception e) { LOG.error; return null; }` catch-all——R6.4（P2-09 `ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED` fail-loud 提审失败）在自动化标注路径被吞掉，标签 Suggested 落库但审批流静默不建，调用方零感知
  - `LineageTagPropagationProcessor.java:177-188` `doCreatePropagatedLabel`：同型 catch-all（LOG.error + return null）
  - **外层吞错层（live 复核，plan 必须一并处理）**：`AutoClassificationProcessor.java:174-184` `suggestTags` 循环内还有一层 per-item catch（:181-183）；`LineageTagPropagationProcessor.java:136-157` `propagateEdge` 循环内一层 per-item catch（:154-156）——只改内层 catch-all，异常上抛后仍被外层吞掉
  - `AutoClassificationProcessor.java:219-226`：无绑定分类时 `allEnabled.sort(Comparator.comparing(classificationId)).get(0)` 全局 lexicographically-first 回退
  - **既有测试冲突**：`TestMetadataPropagationUnit.testPropagationPerEdgeIsolation`（:126-145）显式断言"边缘失败 → 静默返回空结果、无异常"
  - 调用链：`NopMetaTagLabelBizModel.suggestTags`（GraphQL @BizMutation，:58-61）→ AutoClassificationProcessor；抛异常经 BizMutation 事务包装 → 请求整体回滚（沿 R6.4 先例）；`TestMetadataPropagationUnit` 为纯 Mockito 结构（mock IBizObjectManager + invoke 可抛）
- **AR-22**（`MetaContractChecker.java`）：
  - `:336-365` `toDurationMillis` switch：`default` 分支（:362-364）→ `TimeUnit.MILLISECONDS`——未知单位（如 `{"interval":1,"unit":"week"}`）静默按 1ms 解析 → 恒 stale；`week`/`w` 不在现有映射（ms/s/m/h/d 全覆盖）
  - **既有错误码**：`ERR_CONTRACT_SLA_INVALID`（MiscErrors.java:75，MetaContractChecker:81/:383 在用，审计 AR-22 原文建议复用该码）——**复用，不新建**
- 测试基建：`TestEntityAggregationProcessor.java:170-201` 有 4 个 6 参直接调用 `buildOrderByClause`（签名变更后需同步更新）；`TestAggregationExternalJoinAndPagination.java:742` 有双 H2 数据源 e2e 先例（`testCrossDbMemoryHavingMatchesSameDbSqlPath`，AR-19 测试，**键为 INT vs INT——无现成 INT vs BIGINT 跨库 e2e，需新建**）；跨库 join 键类型经 `verifyCrossDbKeyTypeConsistency` 守卫（H2 INT→Integer / BIGINT→Long，修复前抛错 → red 可行）

## Goals

- AR-20a：`buildOrderByClause` 方言感知——外部 JDBC 聚合路径（buildExternalAggregationSql / Mixed / ExternalExternal）在 MySQL 上不产出 NULLS FIRST/LAST 语法；与 MySQL 默认排序一致时省略子句（语义不变），MySQL 无法表达的组合显式 fail-fast（复用或新增错误码）；ORM 路径（Entity via-EQL / EntityEntity）方言可得性显式裁定并记录残余
- AR-20b：跨库 join 整型键（Byte/Short/Integer/Long/BigInteger 族）数值等值通过（stringKey 匹配一致性论证），非整型不匹配（含 BigDecimal vs Integer 等）仍报错（避免静默精度失配）；**re-adjudicate 既有 `testTypeMismatchIntegerVsLongThrowsExplicitly`**（记录于 arm-index）
- AR-21：三层 catch（内层 doCreate* + 外层 suggestTags/propagateEdge）按裁定处置——用户触发路径（suggestTags）fail-loud 到请求边界，后台传播路径（propagateEdge）显式记录不静默；无绑定分类不再任意回退首个分类；判别性测试（含既有隔离测试的更新）
- AR-22：`week`/`w` 补入映射 + 未知单位抛既有码 `ERR_CONTRACT_SLA_INVALID`（fail-fast，不再静默按毫秒）；判别性测试 red→green
- 每个修复带判别性回归测试（red 先于修复实测或至少行为断言可捕获回归）；收口更新 roadmap MR8 R8.4 行（R8.4a 子项）与 arm-index §P2

## Non-Goals

- 不处理 R8.4b 组 finding（AR-17、AR-23①②⑨⑩——由 plan 2026-08-06-1228-2 承接）
- 不处理 R8.1/R8.2/R8.3 已收口项（AR-11~16、AR-23③④⑤、AR-18/19）
- 不做 MySQL 上 NULLS FIRST/LAST 的 CASE 表达式模拟（fail-fast 显式拒绝优于隐式改写）
- 不改 `CrossDbJoinMerger` 的 merge 匹配算法（stringKey 语义不动，只放宽类型守卫到整型族）
- 不引入 IOrmTemplate/ORM 方言探测 API 改造（ORM 路径方言可得性在 plan 内裁定，不发明框架 API）

## Scope

### In Scope

- `AggregationHelper.java`（AR-20a 方言感知 ORDER BY + 相关测试）
- 6 处调用点（`buildExternalAggregationSql` 内部 / `MixedSameDbJoinAggregationProcessor` / `ExternalExternalJoinAggregationProcessor` / `EntityAggregationProcessor` ×2 / `EntityEntityJoinAggregationProcessor`）
- `CrossDbJoinMerger.java`（AR-20b 整型键兼容 + 相关测试）
- `AutoClassificationProcessor.java` + `LineageTagPropagationProcessor.java`（AR-21 三层吞错 + 回退裁定 + 相关测试）
- `MetaContractChecker.java`（AR-22 + 相关测试）
- 错误码：AR-22 复用 `ERR_CONTRACT_SLA_INVALID`（MiscErrors）；AR-20a MySQL 无法表达组合——优先复用既有码（如 `ERR_AGGR_*` 族），无合适码再在既有 Errors 文件新增（沿 R8.2 `ERR_SEARCH_LIMIT_INVALID` 在 MiscErrors 的先例，不建新文件）
- `TestEntityAggregationProcessor.java`（buildOrderByClause 签名变更同步）
- `TestMetaJoinCrossDbMergeNullSemantics.java`（re-adjudication 更新）
- `TestMetadataPropagationUnit.java`（隔离测试按裁定更新）
- `docs-for-ai/03-modules/nop-metadata.md`（若复核发现相关表述漂移则同步）
- `ai-dev/audits/arm-index-nop-metadata.md` §P2 + roadmap MR8 段 R8.4 行（R8.4a 子项终态）

### Out Of Scope

- R8.4b 组（AR-17、AR-23①②⑨⑩）
- 聚合查询的其它方言适配（date/granularity 模板——已由 AR-10 收口）
- 自动化标注的算法改进（分类置信度、规则优先级重排）
- SLA 检查器其它逻辑（freshness 已由 AR-15 收口）
- `NopMetaTagLabelBizModel.propagateFromGlossaryTerm`（:176-178）同型吞错面——登记 Non-Blocking Follow-ups（同族残差，本 plan 不扩 scope）

## Execution Plan

### Phase 1 - AR-20a NULLS FIRST/LAST 方言感知

Status: completed
Targets: `AggregationHelper.java` + 6 处调用点 + 相关测试

- Item Types: `Fix | Decision | Proof`

- [x] 裁定（Decision）：方言获取策略逐调用点——(1) `buildExternalAggregationSql`：已有 dialect 参数，直接传入 `buildOrderByClause`（新增参数）；(2) `MixedSameDbJoinAggregationProcessor:108` 与 `ExternalExternalJoinAggregationProcessor:83`：`buildOrderByClause` 调用在 withConnection lambda 之前，方言在 lambda 内——**将 orderByClause 构建移入 lambda**（方言 :125/:90 已计算）或 lambda 前预探测（复用 Mixed 处理器 :69 已有连接探测路径）；(3) `EntityAggregationProcessor:138`（via-EQL）：IOrmTemplate 无方言 API——**显式裁定**：走 ORM 路径时若无法获得方言则保持现状（H2 语义继续拼子句），残余登记 Deferred But Adjudicated（ORM-backed MySQL 部署场景）；(4) `EntityAggregationProcessor:228`（bypass-EQL）：用既有 `productName`；(5) `EntityEntityJoinAggregationProcessor:123`：同 (3) 裁定。裁定记录于 plan + arm-index
- [x] 裁定（Decision）：MySQL 下 `buildOrderByClause` 行为——(a) nullsFirst 为 null：不产出子句（现状不变）；(b) nullsFirst 显式且与 MySQL 默认一致（true+ASC / false+DESC）：省略子句（语义与默认一致）；(c) nullsFirst 显式且 MySQL 无法表达（false+ASC / true+DESC）：显式抛错误码（优先复用既有 `ERR_AGGR_*` 族，无合适码则新增，沿 fail-fast 先例）——裁定记录于 plan + arm-index
- [x] `buildOrderByClause` 增加方言参数（`String dialect` 或等效 `supportsNullsFirst` 布尔），`:342-345` 仅当方言支持（H2/PostgreSQL）时拼接 NULLS FIRST/LAST；MySQL 走裁定逻辑 (a)/(b)/(c)
- [x] 按裁定调整 6 处调用点（含 `TestEntityAggregationProcessor.java:170-201` 4 处直接调用同步）
- [x] 判别性测试：(i) `buildExternalAggregationSql` 路径（dialect 参数已存在，字符串级断言）——dialect="MySQL" + nullsFirst=true+ASC → SQL 无 NULLS FIRST 子句；dialect="MySQL" + nullsFirst=false+ASC → 显式错误码（修复前产出非法 SQL 实测 red）；(ii) dialect="H2"/"PostgreSQL" + nullsFirst 显式 → 子句保留（keep-green）；(iii) 既有排序测试（TestNopMetaTableQueryBizModel 等）不回归
- [x] 回归：`./mvnw test -pl nop-metadata -am -T 1C` 相关测试全绿

Exit Criteria:

- [x] MySQL 方言下 ORDER BY SQL 语法合法（判别性测试实证）；无法表达组合显式错误码（fail-fast，无静默跳过——Minimum Rules #24）
- [x] H2/PostgreSQL 路径行为不变（子句保留，既有测试全绿）
- [x] **接线验证**：6 处调用点确实把方言传入 `buildOrderByClause`（代码审查 + 判别性测试覆盖外部与实体各一路径）（Minimum Rules #23）
- [x] ORM 路径方言裁定记录于 plan + arm-index（可追溯，无未裁定静默面）
- [x] 错误码新增（若裁定）：错误码清单 / arm-index 同步
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 2 - AR-20b 跨库 join 整型键兼容（含 re-adjudication）

Status: completed
Targets: `CrossDbJoinMerger.java` + `TestMetaJoinCrossDbMergeNullSemantics.java` + 相关测试

- Item Types: `Fix | Decision | Proof`

- [x] **Re-adjudication（Decision）**：既有 `testTypeMismatchIntegerVsLongThrowsExplicitly`（javadoc "silent String coercion is forbidden"）处置——本次修复将整型族（Byte/Short/Integer/Long/BigInteger）数值等值键由拒绝改为接受，理由：`stringKey` 匹配（String.valueOf）下整型等值键必然同串（Long.toString(1)="1"=Integer.toString(1)），类型守卫拒绝是过度防护，且"coercion"担忧仅对非整型成立；非整型（Float/Double/BigDecimal 等）**维持拒绝**（BigDecimal("1.0")="1.0" vs Integer 1="1" 数值等但不同串——放宽会静默失配；Float 0.1f vs Double 0.1 同串但数值不等——放宽会静默错配）；裁定记录于 plan + arm-index，测试更新为：整型等值键通过（断言 merge 成功）+ 整型 vs 非整型（如 Integer 1 vs BigDecimal 1.0 / String "1"）仍抛（keep-red 负例）
- [x] `verifyCrossDbKeyTypeConsistency` / `firstNonNullKeyType`：两侧或单列内非 null 键值均为整型族（Byte/Short/Integer/Long/BigInteger）时视为类型兼容；非整型不匹配仍抛 `ERR_JOIN_CROSS_DB_KEY_TYPE_MISMATCH`（现状保留）
- [x] 判别性测试：(i) left Integer 1 vs right Long 1 → merge 成功不抛（修复前实测 red）；(ii) 单列内 Integer + Long 混合 → 不抛；(iii) Integer 1 vs BigDecimal("1.0") → 仍抛（keep-red 负例，防精度静默失配）；(iv) Integer 1 vs String "1" → 仍抛（keep-red 负例）；(v) 既有 null 语义测试（TestMetaJoinCrossDbMergeNullSemantics 其余用例）不回归；同时更新该类**类级 javadoc :30**（"类型不一致（Integer vs Long vs BigDecimal）显式抛"的过时表述同步修正）
- [x] **新建跨库 INT vs BIGINT 键 e2e**（沿 `TestAggregationExternalJoinAndPagination` 双 H2 数据源基建模式，一侧 join 键 CAT_ID INT、另一侧 BIGINT）——修复前经 `verifyCrossDbKeyTypeConsistency` 抛错实测 red，修复后 join 结果正确
- [x] 回归：`TestCrossDbInMemoryAggregationProcessor` / `TestAggregationExternalJoinAndPagination`（含新建 INT vs BIGINT e2e）/ 跨库聚合 e2e 全绿

Exit Criteria:

- [x] 整型等值键跨库 join 通过（判别性测试实证）；非整型不匹配仍显式错误码
- [x] **端到端验证**：跨库 join（外部↔外部，INT vs BIGINT 键）经真实聚合路径产出正确 join 结果（Minimum Rules #22，TestAggregationExternalJoinAndPagination 基建）
- [x] **Re-adjudication 记录**：既有测试更新 + arm-index 登记（不静默改写既有测试，显式记录旧裁定作废理由）
- [x] 无静默跳过：放宽类型检查不引入静默吞错（非整型不匹配仍 fail-loud）（Minimum Rules #24）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 3 - AR-21 自动化标注 fail-loud（三层吞错）+ 回退裁定

Status: completed
Targets: `AutoClassificationProcessor.java` + `LineageTagPropagationProcessor.java` + 相关测试

- Item Types: `Fix | Decision | Proof`

- [x] 裁定（Decision）：三层吞错逐层处置——(1) 内层 `doCreateAutomatedLabel`（:260-271）/ `doCreatePropagatedLabel`（:177-188）catch-all 改为显式抛 `NopMetadataException`（cause 保留，复用或对齐 `MiscErrors.ERR_TAG_LABEL_SUBMIT_APPROVAL_FAILED` 族，沿 R6.4 先例；**错误码语义注意**：该码消息为 "label saved but never enters approval flow"，用于"标签保存本身失败"语义略有偏差——若复用则在 error param 中说明是 save 失败，或裁定新码），不再 LOG.error + return null；同时处置 invoke 返回非实体时的 `return null` 静默分支（:266/:183——极低概率边缘，裁定为显式 LOG.warn 后返回 null 并登记残余，或与 catch 同路径处理，不静默无日志）；(2) 外层 `suggestTags` 循环 catch（:181-183）——**用户触发路径**（GraphQL @BizMutation）fail-loud：不吞，异常上抛到请求边界（BizMutation 事务包装整体回滚，沿 R6.4 先例）；(3) 外层 `propagateEdge` 循环 catch（:154-156）——**后台传播路径**：保留 per-edge 隔离（单边失败不中断整条血缘传播），但内层不再返回 null 静默（内层抛错 → 外层 LOG.error 含完整上下文后继续），并显式记录"传播失败可观测（LOG.error）但不中断批处理"为裁定语义——裁定记录于 plan + arm-index
- [x] 裁定（Decision）：无绑定分类回退——移除 `allEnabled.sort(lexicographic).get(0)` 任意回退，改为显式「无分类」结果（返回 null + LOG.warn 说明无绑定分类）；默认 null + warn（用户触发路径可见，不任意选择首个分类）——裁定记录于 plan + arm-index
- [x] 实施两项裁定对应代码变更（三层 + 回退）
- [x] 判别性测试：(i) `TestMetadataPropagationUnit`（Mockito 基建，mock invoke 抛错）——suggestTags 标签保存/提审失败 → 显式错误码 + cause 保留 + 异常到达请求边界（修复前静默 return null 实测 red）；(ii) 无绑定分类 → 不分配任意分类（断言无默认分类，修复前 lexicographically-first 实测 red）；(iii) 有绑定分类 → 正常分配（keep-green）；(iv) **`testPropagationPerEdgeIsolation` 按裁定更新**：propagateEdge 单边失败 → 不中断整批（隔离保持）但失败已 LOG.error 可观测（原"静默返回空结果"断言更新，re-adjudication 记录）
- [x] 回归：`TestMetadataPropagationUnit` / `TestMetadataPropagationIntegration` / `TestNopMetaClassificationTagLabelCrud` / `TestNopMetaTagLabelApproval` 全绿

Exit Criteria:

- [x] 用户触发路径失败到达请求边界（判别性测试实证）；后台传播路径失败可观测不中断（判别性测试实证）
- [x] **无静默跳过**：三层 catch 无空吞（内层抛、外层或上抛或 LOG.error 留证）（Minimum Rules #24）
- [x] 两项裁定 + 隔离测试 re-adjudication 记录写入 plan + arm-index（可追溯）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 4 - AR-22 SLA 时间单位解析

Status: completed
Targets: `MetaContractChecker.java` + 相关测试

- Item Types: `Fix | Proof`

- [x] `toDurationMillis`（:336-365）：`week` / `w` 补入映射（→ DAYS×7 或等效）；`default` 分支改为显式抛既有码 `ERR_CONTRACT_SLA_INVALID`（MiscErrors.java:75，MetaContractChecker:81/:383 已在用，审计 AR-22 原文建议复用——不新建错误码）；**注意该码消息模板只声明 `{contractId}`/`{error}` 无 `{unit}` 占位**——unit 值拼入 `error` 文本（如 `error="unknown sla time unit: week"`），不扩展错误码模板（避免影响 :81/:383 既有渲染）；不再静默按毫秒解析
- [x] 判别性测试：(i) `{"interval":1,"unit":"week"}` → 7 天毫秒数（修复前 1ms 实测 red）；(ii) `{"interval":2,"unit":"w"}` → 14 天；(iii) 未知单位（如 "fortnight"）→ `ERR_CONTRACT_SLA_INVALID`（修复前静默 1ms 实测 red）；(iv) 既有合法单位（ms/s/m/h/d）不回归
- [x] 回归：`TestNopMetaDataContractBizModel` / `TestNopMetaDataContractBizModelExecution` / 契约检查 e2e 全绿

Exit Criteria:

- [x] week 语义正确 + 未知单位显式错误码（判别性测试实证，无静默跳过——Minimum Rules #24）
- [x] 既有单位路径不回归（keep-green）
- [x] 复用既有错误码（无新码，错误码清单不膨胀）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

### Phase 5 - 收口

Status: completed
Targets: roadmap MR8 段 + arm-index §P2 + 全量验证

- Item Types: `Fix | Proof`

- [x] roadmap MR8 段 R8.4 行 → 记录 R8.4a 子项终态（AR-20/21/22 → fixed + re-adjudication 记录；R8.4 行整体 done 由 plan 2026-08-06-1228-2 完成）
- [x] arm-index §P2 AR-20 / AR-21 / AR-22 → fixed（含修复 commit 引用 + re-adjudication 登记）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` exit 0
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（记录计数基线）
- [x] 独立子 agent closure audit（fresh session）PASS + Closure 段证据写入

Exit Criteria:

- [x] roadmap MR8 段与 arm-index §P2 双向一致（AR-20/21/22 逐条可追溯）
- [x] 全量测试通过（0 failures/errors/skipped）+ 工具验证 exit 0
- [x] 独立 closure audit READY_TO_CLOSE（含 Anti-Hollow 调用链追踪）
- [x] `ai-dev/logs/2026/08-06.md` 已更新

## Closure Gates

> 关闭条件：本 section 所有条目与每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [x] AR-20 + AR-21 + AR-22 三个已确认 live defect 全部修复（判别性测试 red→green 证据在案）
- [x] 无已确认 live defect / contract drift 被降级到 deferred / follow-up
- [x] docs-for-ai 复核完成（无漂移则显式记录 `No owner-doc update required`，如发现 NULLS FIRST 方言约束 / SLA 单位白名单相关表述漂移则已同步）
- [x] 必要 focused verification 已完成（每项 finding 至少一条判别性测试）
- [x] 独立子 agent / 独立审阅者 closure-audit 完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）方言参数确实传入 `buildOrderByClause` 并在 SQL 构造中被使用、（b）三层 catch 无空吞、（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-metadata -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] checkstyle / 代码规范检查通过（历史惯例：插件仅 -Pqa profile，按仓库惯例）

## Deferred But Adjudicated

### ORM 路径方言探测（AR-20a 残差）

- Classification: `watch-only residual`
- Why Not Blocking Closure: Entity via-EQL（EntityAggregationProcessor:138）与 EntityEntityJoinAggregationProcessor 走 ORM 路径，IOrmTemplate 无方言 API（live 复核）；外部 JDBC 路径（MySQL 暴露面）修复后，ORM 路径保持既有行为（H2 语义拼子句），缺陷仅存在于"ORM 背后接 MySQL"的部署场景——当前模块测试与 supported baseline 均为 H2，无实际暴露路径
- Successor Required: `no`
- Successor Path: 若未来引入 ORM 方言探测 API 再处理

## Non-Blocking Follow-ups

- `NopMetaTagLabelBizModel.propagateFromGlossaryTerm`（:176-178）同型吞错面（Derived 标签路径）——同族残差，随 AR-21 语义推广批次处理
- MySQL 上 NULLS FIRST/LAST 的 CASE 表达式模拟（如未来产品需求要求 MySQL 反向 NULL 排序——当前 fail-fast 显式拒绝已满足正确性，模拟属优化面）
- 自动化标注分类置信度 / 规则优先级重排（算法改进面，无正确性缺陷）

## Closure

Status Note: 已执行完成（2026-08-06；R8.4a 三项 finding 全部 fixed，独立 closure audit READY_TO_CLOSE）
Completed: 2026-08-06（commit `f829c11d5`，20 文件 +845/-50；`./mvnw test -pl nop-metadata -am -T 1C` **1037/0 全绿**（1016 基线 + 21 判别性测试）；check-plan-checklist/check-doc-links/scan-hollow 全 exit 0；roadmap MR8 段 R8.4 行 R8.4a 子项 → fixed + arm-index §P2 AR-20/21/22 → fixed）

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session `ses_02a7333a5ffeUasQKZUI6pwKxq`）
- Evidence: **VERDICT PASS（READY_TO_CLOSE）**——7 项验证点全 PASS：AR-20a 方言参数真实使用（AggregationHelper.java:349/:353-359/:364，6 处调用点全部接线含 2 处 lambda 内构建）/ AR-20b 整型族兼容 + 非整型拒绝 + INT vs BIGINT e2e / AR-21 三层无空吞（内层 wrap cause 保留 + suggestTags 无 catch + propagateEdge LOG.error 留证）/ AR-22 week/w + 复用既有码；Anti-Hollow 全 PASS（接线链 execute → safeProductName → buildOrderByClause 实证；testPropagationPerEdgeIsolation 假绿揭示与重写实证（旧单一 stub 致 sourceLabels 空早退，现 stub getSourceLabels 真实到达边缘循环））；无 live defect 降级（Deferred 仅 ORM 方言残余，plan Phase 1 裁定显式登记）；独立复跑 focused 9 类 **149/0 全绿**

Follow-up:

- 无 plan 内遗留工作（R8.4b：AR-17 + AR-23①②⑨⑩ 由 plan-2026-08-06-1228-2 承接，R8.4 行整体 done 待其收口）
