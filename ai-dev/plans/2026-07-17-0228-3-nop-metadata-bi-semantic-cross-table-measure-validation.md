# 3 nop-metadata BI 语义层跨表 Measure 校验（entity-entity）

> Plan Status: active
> Last Reviewed: 2026-07-17
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验）。R1 发现 2 Major（Dimension 共享 validateFieldReference 但 scope 未裁定 → 裁定纳入 In Scope 与 Measure 一致；D1 Approach A entityId 集合 vs item 1.2 Approach B 字段 PK 集合矛盾 → 统一为 Approach A）+ 3 Minor（PK 名 metaEntityFieldId→entityFieldId、leftTableId 命中范围限定 orm.xml、leftEntityId/baseEntityId 一致性裁定），已全部修复。R2 逐条核实 5 项修复 PASS + 3 处 cosmetic 残留修复，无新矛盾，共识 YES。
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md`（P3 BI 语义层 deferred）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5.2 D2（Measure/Dimension 字段引用校验范围，行 352-366）；plan `2026-07-16-0700-2` Deferred But Adjudicated「跨表 Measure 表达式」Successor Required: yes（P4 前增量）
> Mission: nop-metadata
> Work Item: P3+ — BI 语义层跨表 Measure 字段引用校验（entity-entity，P4 联邦查询 entity-entity JOIN 已 done 后触发）
> Related: `2026-07-16-0700-2-nop-metadata-bi-semantic-validation-and-field-resolution.md`（P3-2~P3-5 字段引用校验，已 done——Measure/Dimension/Filter/Join save override 校验 + MetaTableFieldResolver 三类分派，本 plan 扩展 Measure 跨表范围）、`2026-07-16-0800-2-nop-metadata-cross-table-join-and-aggregation.md`（P4-2 跨表 JOIN 执行，已 done——entity-entity JOIN 执行已落地，本 plan 补齐 entity 跨表 Measure 字段引用的写入时校验）

## Purpose

把 deferred 的「跨表 Measure 字段引用校验」从 follow-up 推进到 landed：扩展 BI 语义层 Measure 的字段引用校验范围，使 Measure 的 `entityFieldId` 引用可校验**通过 NopMetaTableJoin 可达的右实体字段**（跨表指标），而非仅限主表 `baseEntityId` 实体字段。P4-2 联邦查询（entity-entity JOIN 执行）已 done，跨表字段引用在查询时已被消费（JOIN 用 `leftEntityId`/`rightEntityId` + `leftField`/`rightField`），本 plan 补齐这些引用的**写入时校验**（Measure save override），收口"entity 类型表的 Measure 字段引用可校验主表 + join 可达右实体"这一结果面。

## Current Baseline

- **BI 语义层字段引用校验已落地（P3-2~P3-5，plan 0700-2 done）**：
  - `MetaTableFieldResolver`（`.../field/MetaTableFieldResolver.java`）按 tableType 分派返回可用字段集合：entity→NopMetaEntityField 手动 query（按 `baseEntityId` 作 `metaEntityId` 查）；external→buildSql JSON columnName；sql→SqlSelectFieldExtractor。
  - Measure/Dimension/Filter/Join BizModel 重写 `CrudBizModel.save(Map, IServiceContext)`，持久化前校验字段引用（`validateFieldReference`）。
  - **当前 Measure 校验范围**：entity 表的 `entityFieldId`（存 `NopMetaEntityField.entityFieldId` 主键，已 live 核实 PK 属性名为 `entityFieldId` 非 `metaEntityFieldId`）校验其属于 `baseEntityId` 实体的字段集合（PK 加载 + 归属判定 `field.getMetaEntityId() == table.getBaseEntityId()`）。**跨表引用（measure 引用 join 右实体字段）未校验**——首版只查主表 baseEntity 字段集合。
- **跨表 Measure 校验 deferred（本 plan 目标）**：plan 0700-2 Deferred「跨表 Measure 表达式」Classification=optimization candidate，Successor Required: yes（P4 前增量）。当前 Measure 引用主表字段；跨表指标（measure 引用 join 右实体字段）的字段引用校验未实现——首版只校验 baseEntity 字段集合。
- **Dimension 共享 validateFieldReference（关键事实，影响 scope 裁定）**：`NopMetaTableDimensionBizModel` 与 `NopMetaTableMeasureBizModel` 共享同一个 `MetaTableFieldResolver.validateFieldReference` 方法（已 live 核实）。若本 plan 扩展该方法（加 join 加载），Dimension 将被动获得跨表校验。**scope 裁定**（item 1.1）：Dimension 与 Measure 共享 PK 归属语义 + 共享校验方法，一并扩展到跨表（entity 表的 Dimension entityFieldId 同样校验 baseEntity ∪ join.rightEntity），而非为 Measure 单建路径。Dimension 跨表校验在 item 1.1 显式裁定纳入 In Scope（与 Measure 一致）。
- **NopMetaTableJoin 模型（关键事实，决定本 plan 范围）**：`nop-metadata.orm.xml` NopMetaTableJoin 列为 `joinId / metaTableId / joinType / leftEntityId / rightEntityId / leftField / rightField / alias` + 审计列。**Join 只引用 NopMetaEntity（leftEntityId/rightEntityId），orm.xml 中无 leftTableId/rightTableId 列**（已 live 核实：orm.xml 中 `rg leftTableId|rightTableId` 0 命中；测试代码局部变量有命中但非 ORM 列）。因此本 plan 的「跨表」= **entity-entity 跨实体**（Measure 引用 join 的 rightEntityId 实体字段），不含 sql/external 表参与 Join（后者需 schema 变更，见 Non-Goals）。
- **P4-2 联邦查询已 done（触发条件满足，entity-entity）**：plan 0800-2（跨表 JOIN 执行 + 聚合）done——**entity-entity JOIN** 经 ORM 查询模型（QueryBean.innerJoin/leftJoin + IOrmTemplate）执行，JOIN 字段直接用 `MetaTableJoin.leftField`/`rightField`。跨表字段引用在查询时已被消费，写入时校验是补齐项（前置数据已被使用，校验缺失不影响运行但影响元数据质量/悬空引用防护）。**注意**：P4-2 的 Non-Goals/Out Of Scope 明确「sql/external 表作为 JOIN 右表为 follow-up」，执行层首版聚焦 entity-entity——故本 plan 也只做 entity-entity 跨表 Measure。
- **save override 范式已建立（直接复用）**：Measure BizModel 已有 save override + `validateFieldReference`；`MetaTableFieldResolver` 已有 entity 分派（按 baseEntityId 查 NopMetaEntityField）。本 plan 扩展其范围（合并 join 可达右实体字段集合）。
- **设计 §2.5.2 D2 降级铁律**：字段集合解析失败（entity baseEntityId null）→ 显式失败抛 inline ErrorCode（不静默跳过校验、不静默存入悬空引用）。
- **测试基建**：Nop AutoTest 可用，0700-2 已证明 save override + 字段引用校验 + 失败路径可测。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS。

## Goals

- 跨表 Measure/Dimension 字段引用校验（entity-entity）：Measure/Dimension 的 `entityFieldId` 引用通过 NopMetaTableJoin 可达的 rightEntityId 实体字段时，save override 校验该字段属于「主表 baseEntity ∪ join 直连可达 rightEntity」的 entityId 集合
- 合并语义明确（PK 归属）：entity 表 entityFieldId 为 PK，跨表校验仍用 PK 归属判定（field.metaEntityId ∈ {baseEntity ∪ 各 join.rightEntity}），不降级为名称集合判定（保持既有 entity-only PK 归属校验语义）
- 显式失败（不静默通过）：跨表字段引用解析失败 / 引用字段不属于任何可达实体 → 抛 inline ErrorCode（不静默存入悬空引用），对齐 §2.5.2 D2 降级铁律
- AutoTest：跨表 Measure/Dimension save（引用 join 右实体字段合法）+ 悬空跨表引用（不属于主表也无 join 可达）失败路径全绿

## Non-Goals

- **sql/external 表参与 Join + 其 Measure 跨表校验**——`NopMetaTableJoin` 当前**无 leftTableId/rightTableId 列**（仅 leftEntityId/rightEntityId），sql/external 表无 entity 无法作为 join 端点。支持 sql/external Join 需 ORM schema 变更（新增 table 引用列，属 Protected Area，需独立 plan-first plan + 迁移评估），本 plan 明确不触及 schema。
- **Measure expression 内容校验**——expression 型 measure（entityFieldId=null）的表达式内容首版不校验（沿用 §2.5.2 D2 Non-Goal）；本 plan 只校验**字段引用**（entityFieldId 主键）
- **CTE/子查询 SQL 字段类型推断**——属字段解析深度（独立于跨表校验）
- **Filter 跨表引用**——Filter.definition（TreeBean）的字段引用首版仍限主表（TreeBean 内字段名解析 + 跨表可达集合合并复杂度更高），按 D2 裁定是否纳入首版
- **Join 可达性递归（A→B→C 间接可达）**——首版只直连 Join 可达，递归 deferred
- **Join 自身校验扩展**——Join save override 已校验 leftEntityId/rightEntityId + leftField/rightField 属于对应实体字段集合（0700-2 done），本 plan 不改 Join 校验

## Design Decisions

> D1/D2 为硬前置门禁，须在 item 1.1 裁定并写入 `01-architecture-baseline.md` §2.5.2 后实现。

### D1. 跨表 Measure 字段引用校验范围 + 合并语义（待 item 1.1 裁定）

- **可达字段集合（PK 归属语义，Approach A：entityId 集合归属）**：裁定 entity 表 Measure save 校验时，**不构建字段 PK 集合**，而是构建**可达 entityId 集合** = {baseEntityId ∪ 该表所有 NopMetaTableJoin 的 rightEntityId}。Measure 的 entityFieldId 引用的 field（按 PK 加载），其 `metaEntityId` 须 ∈ 该 entityId 集合。这是对现有 `validateFieldReference` 的最小扩展——现有逻辑 `field.getMetaEntityId() == table.getBaseEntityId()` 扩展为 `allowedEntityIds.contains(field.getMetaEntityId())`，无需新建字段 PK 集合查询路径。
- **PK 归属不降级为名称集合**：entity 表沿用既有 PK 归属判定（`field.getMetaEntityId() == table.getBaseEntityId()` 扩展为 `∈ {baseEntity ∪ join.rightEntity}`），不改为列名字符串集合判定（保持 entity-only 校验语义不退化）。external/sql 表的 Measure 不在本 plan 范围（其 entityFieldId 存列名字符串，无 join 可达——见 Non-Goals）。
- **Join 不存在/未定义时**：Measure 引用的字段 PK 归属 baseEntity 则通过（既有行为不变）；若引用字段 PK 不属于 baseEntity 且无 Join 可达 → 显式失败（悬空跨表引用）。
- **加载 Join 的范围**：仅加载该 metaTableId（Measure 所属表）的 Join。不递归 join 图（A→B→C 间接可达，首版只直连，递归 deferred）。

### D2. Filter 跨表裁定 + external/sql 表 Measure 排除（待 item 1.1 裁定）

- **Filter 跨表裁定**：Filter.definition 的字段引用首版是否扩展到跨表，在 item 1.1 裁定——推荐首版**仍限主表**（TreeBean 内字段名解析 + 跨表可达集合合并复杂度更高），Filter 跨表为 follow-up。
- **external/sql 表 Measure 范围裁定**：明确 external/sql 类型表的 Measure 不做跨表校验（其 entityFieldId 存列名字符串、且 NopMetaTableJoin 不支持 sql/external 端点）。本 plan 校验仅对 entity 类型表（baseEntityId 非空）的 Measure 生效；external/sql 表 Measure 沿用既有名称集合校验（无 join 可达，不扩展）。
- **显式失败**：Measure 引用字段 PK 不属于任何可达实体（baseEntity ∪ join.rightEntity）→ 抛 inline ErrorCode（不静默存入悬空引用）。

## Scope

### In Scope

- 扩展 Measure + Dimension save override 校验（entity 表，共享 `validateFieldReference`）：可用 entityId 集合 = baseEntity ∪ join 直连可达 rightEntity（按 D1，Approach A PK 归属语义）
- MetaTableFieldResolver 扩展：提供「给定 entity 类型 NopMetaTable，返回 baseEntity ∪ join 直连可达 rightEntity 的 entityId 集合」helper
- AutoTest：跨表 Measure + Dimension save（合法 join 右实体字段 + 悬空跨表引用失败）+ 既有 entity-only Measure/Dimension 回归不退化

### Out Of Scope

- sql/external 表参与 Join + 其 Measure 跨表校验（需 ORM schema 变更，独立 plan-first plan）
- Measure expression 内容校验（expression 型，沿用 Non-Goal）
- Filter 跨表字段引用（按 D2 裁定，推荐 follow-up）
- CTE/子查询 SQL 字段类型推断
- Join 可达性递归（A→B→C 间接可达，首版直连）
- Join save override 扩展（已 done，不改）

## Execution Plan

### Phase 1 - 跨表 Measure 校验（entity-entity）

Status: planned
Targets: `nop-metadata/nop-metadata-service/.../entity/NopMetaTableMeasureBizModel.java`（save override 扩展）、`.../field/MetaTableFieldResolver.java`（跨表可达 PK 集合 helper，如需）、`TestNopMetaBiSemantic*.java`

- Item Types: `Decision`（D1 跨表 Measure 范围/合并语义 + D2 Filter 裁定/external-sql 排除，硬前置）+ `Proof`（扩展功能：entity 跨表字段引用校验）

> **硬前置门禁（item 1.1）**：D1/D2 必须先裁定（只裁定不写代码），写入 `01-architecture-baseline.md` §2.5.2（跨表 Measure 校验范围子节）后实现。重点核实：NopMetaTableJoin 按 metaTableId 加载 Join 列表的查询可行性 + rightEntityId 按 metaEntityId 查 NopMetaEntityField 集合的复用性（既有 MetaTableFieldResolver entity 分派已有此查询）。

- [ ] 1.1 **跨表 Measure/Dimension 范围 + 合并语义 + Filter/external-sql 裁定决策（硬前置门禁，Decision only）**：基于 live repo 核实并裁定 D1/D2——裁定 entity 表 Measure + Dimension 可用 entityId 集合（baseEntity ∪ join 直连可达 rightEntity，Approach A 不构建字段 PK 集合）；裁定 PK 归属语义不降级为名称集合（保持 entity-only 语义）；裁定 Dimension 纳入跨表校验（与 Measure 共享 `validateFieldReference`，一并扩展）；裁定悬空跨表引用显式失败；裁定 Filter 跨表（推荐限主表，follow-up）；裁定 external/sql 表 Measure/Dimension 排除（无 join 可达，沿用既有名称校验）；裁定 Join 加载范围（仅直连，递归 deferred）；裁定 Join leftEntityId 不要求 == baseEntityId（任意 join 的 rightEntity 均视为可达，宽松语义）。**只裁定不写代码**。结论写入 `01-architecture-baseline.md` §2.5.2（跨表 Measure/Dimension 校验范围子节）
- [ ] 1.2 **可达 entityId 集合 helper（Proof，依赖 1.1）**：在 MetaTableFieldResolver 新增「给定 entity 类型 NopMetaTable，返回 `Set<String> allowedEntityIds`（baseEntityId ∪ join 直连可达 rightEntityId）」helper（加载该表 Join 列表 → 对每个 Join 收集 rightEntityId → ∪ baseEntityId）。**不构建字段 PK 集合**（Approach A），校验方式为 `allowedEntityIds.contains(field.getMetaEntityId())`，对现有 `validateFieldReference` 单一归属判定做最小扩展。字段集合解析失败路径（baseEntityId null / 实体不存在）显式抛 ErrorCode（对齐 §2.5.2 降级铁律）
- [ ] 1.3 **Measure/Dimension save override 跨表校验扩展（依赖 1.1）**：在 `MetaTableFieldResolver.validateFieldReference` 扩展校验（Measure + Dimension 共享此方法，一并扩展）——entity 表的 entityFieldId 引用的 field，其 metaEntityId 须 ∈ allowedEntityIds（baseEntity ∪ join 直连可达 rightEntity，按 D1 Approach A PK 归属）。合法通过 → super.save；悬空跨表引用（metaEntityId 不在集合）→ 显式失败抛 inline ErrorCode。expression 型（entityFieldId=null）仍跳过（Non-Goal 不变）；external/sql 表 Measure/Dimension 沿用既有名称集合校验（不扩展）
- [ ] 1.4 错误码按现有模式 inline 定义（参考 NopMetaTableMeasureBizModel inline ErrorCode 用法）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] D1/D2 决策已裁定并落地，且**可观测不变量成立**：既有 Measure/Dimension/Filter/Join save 校验回归测试全过（不破坏既有 entity-only 校验）
- [ ] 跨表 Measure/Dimension 校验成立：entity 表 Measure/Dimension 引用 join rightEntity 字段（rightEntityId 实体的 NopMetaEntityField）save 通过；引用字段 metaEntityId 不在 allowedEntityIds 且无 join 可达 → 显式失败（不静默存入悬空引用）
- [ ] PK 归属语义不退化：entity 表 Measure 沿用 PK 归属判定（field.metaEntityId ∈ {baseEntity ∪ join.rightEntity}），既有 entity-only 校验行为不破坏（既有回归测试全过）
- [ ] external/sql 表 Measure/Dimension 不受影响：external/sql 表 Measure/Dimension 沿用既有名称集合校验，无 join 可达（不扩展、不误判）
- [ ] 显式失败（不静默通过）：悬空跨表引用 / 字段集合解析失败（baseEntityId null / 实体不存在）→ 抛 inline ErrorCode（对齐 §2.5.2 降级铁律）
- [ ] **接线验证**：Measure/Dimension save override 运行时确实调用了跨表可达 entityId 集合解析（合法/失败测试断言证明），非空壳（见 Minimum Rules #23）
- [ ] **无静默跳过**：失败路径显式抛 ErrorCode；无空方法体 / 吞异常 / 静默放行悬空引用（见 Minimum Rules #24）
- [ ] **新功能测试**：新增测试覆盖 跨表 Measure + Dimension 合法（join 右实体字段）+ 悬空跨表引用失败 + entity-only 回归（Measure + Dimension）+ external/sql Measure/Dimension 回归，全绿（见 Minimum Rules #25）
- [ ] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5.2 跨表 Measure/Dimension 校验范围已更新
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [ ] 跨表 Measure/Dimension 字段引用校验可用（合法通过 + 悬空失败）
- [ ] PK 归属语义不退化（既有 entity-only 回归全过）
- [ ] external/sql 表 Measure/Dimension 不受影响
- [ ] 显式失败成立（不静默存入悬空引用 / 不静默通过解析失败）
- [ ] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常）
- [ ] 必要 focused verification 已完成（跨表 Measure + entity 回归 + external/sql 回归 + 失败路径测试全绿）
- [ ] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] 受影响的 owner docs 已同步（`01-architecture-baseline.md` §2.5.2）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 save override 运行时确实执行跨表 entityId 集合解析并拒绝悬空引用（端到端连通）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-17-0228-3-nop-metadata-bi-semantic-cross-table-measure-validation.md --strict` 退出码 0

## Deferred But Adjudicated

### sql/external 表参与 Join + 其 Measure 跨表校验

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `NopMetaTableJoin` 当前无 leftTableId/rightTableId 列（仅 leftEntityId/rightEntityId → NopMetaEntity），sql/external 表无 entity 无法作为 join 端点（已 live 核实 + P4-2 Non-Goals 明确 follow-up）。支持 sql/external Join 需 ORM schema 变更（Protected Area，需独立 plan-first plan + 迁移评估）。本 plan 仅做 entity-entity 跨表 Measure 校验，已满足「entity 类型表跨表指标字段引用可校验」核心结果面
- Successor Required: yes（sql/external Join 支持需 schema 变更 plan-first plan，其 Measure 跨表校验随之）

### Filter 跨表字段引用

- Classification: `optimization candidate`
- Why Not Blocking Closure: Filter.definition（TreeBean）内字段名解析 + 跨表可达集合合并复杂度高于 Measure（PK 引用）。首版 Measure 跨表校验已满足"BI 语义层跨表字段引用可校验"核心结果面。Filter 跨表为同源增量
- Successor Required: no（按需 follow-up）

### Join 可达性递归（A→B→C 间接可达）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版直连 Join 可达已覆盖绝大多数跨表指标场景。递归 join 图可达性 + 环路检测属查询执行/图算法层 concern，独立复杂度
- Successor Required: no

## Non-Blocking Follow-ups

- sql/external 表参与 Join（需 schema 变更：NopMetaTableJoin 新增 table 引用列，Protected Area plan-first）
- Measure expression 内容校验（expression 型，需表达式 AST 解析）
- Filter 跨表字段引用（TreeBean 内字段名解析 + 跨表集合）
- Join 可达性递归（join 图成环的写入时警告）

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
