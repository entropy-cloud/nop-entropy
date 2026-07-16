# 0800-2 nop-metadata 联邦查询 — 跨表 JOIN + 指标维度聚合（P4-2 + P4-3）

> Plan Status: active
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P4-2/P4-3；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5 + §2.5.2 D1/D2 + §设计结论 #9 + §七 + §八；plan 0700-2 Deferred But Adjudicated（跨表 Measure / sql-external 表 Join 归 P4）
> Related: `2026-07-16-0800-1-nop-metadata-federated-single-table-query.md`（predecessor，提供单表查询基础）；`2026-07-16-0700-2-...`（P3-2~P3-5，Measure/Dimension/Filter/Join 模型与校验）
> Draft Review: 经两轮独立子 agent 对抗性审查（含想象性分析 + live repo 核验），Blocker/Major 已全部收敛，consensus 达成（无 Blocker）。本 plan 依赖未执行的 predecessor 0800-1，执行顺序：0800-1 closure 后再启动。

## Purpose

把 P4-2（跨表 JOIN 执行）+ P4-3（指标/维度聚合查询）从 `todo` 推进到 `done`：在 P4-1 单表查询基础上，完成联邦查询的「分析查询」能力——按 `MetaTableJoin` 关联多表（同库 SQL JOIN / 跨库应用层拼接），并按 `MetaTableMeasure` + `MetaTableDimension` 自动生成聚合查询。这两项共同收口"P4 联邦查询可执行分析"这一结果面。

## Current Baseline

- **依赖 predecessor plan 0800-1（状态 `draft`，尚未执行）**：本 plan 的跨库拼接与接线验证依赖 0800-1 落地的统一单表查询 action。**执行顺序**：0800-1 closure 后再启动 0800-2（或将 0800-2 标 `blocked`）。0800-1 锁定的消费契约：单表查询 action 返回行数据为 `List<Map<String,Object>>`（列名按 tableType 取自字段解析），filter 参数为 TreeBean。若 0800-1 最终 action 签名与此不同，本 plan 启动时须先对齐。
- 依赖 predecessor plan 0800-1 已落地的能力：querySpace→数据源解析（external/sql 表定位 NopMetaDataSource）。
- `MetaTableJoin`（§2.5）已建模：`joinType`(inner/left/right)、`leftEntityId`/`rightEntityId`(→MetaEntity)、`leftField`/`rightField`、`alias`。**首版校验仅 entity 实体关联**（§2.5.2 D2）；sql/external 表 join 语义为 follow-up（plan 0700-2 Deferred But Adjudicated，归 P4）。注意：`leftField`/`rightField` 是**字段名字符串**（属对应实体字段集合），而 `MetaTableMeasure/Dimension.entityFieldId` 对 entity 表存的是 `NopMetaEntityField.metaEntityFieldId` 主键——执行时需把 entityFieldId 解析回 ORM 属性名（§2.5.2 D2）。
- **entity 表 querySpace 在 `MetaEntity.querySpace`**（非 `MetaTable.querySpace`，后者 entity 表可为 null；import 时 `OrmModelImporter.buildEntityTable` 写入 `table.setQuerySpace(em.getQuerySpace())`）。判定同/跨库 JOIN 时须用 `MetaEntity.querySpace` 解析 entity 表的 querySpace。
- ORM 查询模型已有 JOIN/GROUP BY/聚合原语：`QueryBean`（`innerJoin`/`leftJoin`/`rightJoin`，但注意 live `QueryBean.rightJoin` 实际映射为 LEFT_JOIN）、`GroupFieldBean`（仅 `owner`+`name`，**无法表达 DATE_TRUNC 等变换**）、`QueryAggregateFieldBean`（aggFunc）。`MetaQualityRuleExecutor.IDENTIFIER_PATTERN`（§2.7.1 D3）提供原生 SQL 标识符白名单 + 参数绑定先例。
- `MetaTableMeasure`（§2.5）：`aggFunc`(sum/count/avg/min/max/countDistinct)、`entityFieldId`(语义按 tableType 重载)、`expression`。`MetaTableDimension`：`dimensionType`(categorical/temporal/geographical)、`granularity`(自由 string，文档约定 year/quarter/month/week/day/hour，**P4 执行时翻译为 SQL 分桶函数**)。
- `MetaTableFilter`（§2.5.2 D1）：`definition` 为 TreeBean filter 树；`isDefault` 唯一性首版代码强制（无 DB 唯一索引，仅 `IX_NOP_META_FILTER_TABLE` 非唯一），**默认过滤器的运行时自动应用在 P4 查询执行**（0700-2 Non-Blocking Follow-up，归 P4）。
- 测试数据库为 H2。`DATE_TRUNC` 在 PostgreSQL/H2(2.x) 可用，**MySQL 无 DATE_TRUNC**（用 `DATE_FORMAT`）；"week"/"quarter" 跨方言/区域有差异。
- §八 待定问题（与本 plan 直接相关）：「MetaTableJoin 跨表关联时，左右表所属数据源不同（如 ORM 的 MySQL 表和 SQL 定义的 ClickHouse 表），查询执行如何路由？」——本 plan 必须裁定。
- 架构硬约束（§设计结论 #9 + §七）：不引入额外 Driver/QuerySpace 抽象；同库走 SQL JOIN，跨库走应用层拼接（roadmap P4-2 原文）。

## Goals

- 实现 MetaTableJoin 关联的多表查询：同库（同 querySpace）翻译为 SQL JOIN（entity 走 ORM QueryBean），跨库（不同 querySpace）走应用层拼接（明确合并契约）。
- 实现 Measure + Dimension 聚合查询：按维度分组 + 指标 aggFunc 聚合，时间维度按 `granularity` 分桶（按方言翻译）。
- 默认过滤器（`isDefault=true`）在查询执行时自动应用。
- 把跨库 JOIN 路由（D3/D4/D5）、聚合执行（D6）、granularity 分桶翻译表（D7）写入 `01-architecture-baseline.md`「§4.4 查询执行」节（0800-1 创建该节，本 plan 追加 JOIN/聚合子节，不另起编号）。
- 同库 JOIN、跨库 JOIN、聚合查询、时间分桶各端到端验证。

## Non-Goals

- 单表数据查询（P4-1，predecessor plan 0800-1）。
- sql/external 表作为 JOIN 右表的关联（继承自 0700-2，首版 join 校验仍 entity-only；执行层首版聚焦 entity-entity 同/跨库）。
- 数据契约 MetaDataContract（P4-4）、Reconciliation（P4-5）。
- nop-report 迁移（P5）。
- 自定义聚合表达式（Measure.expression 内容首版不校验/不执行，继承 0700-2 Non-Goal）。

## Scope

### In Scope

- D3 裁定：跨库 JOIN 路由策略（同 querySpace→SQL JOIN；不同 querySpace→应用层拼接：各自单表查询后在内存按 join key 合并）。
- MetaTableJoin 驱动的多表查询执行（同库 SQL JOIN 优先）。
- Measure + Dimension 聚合查询：维度分组 + aggFunc 聚合 + 时间维度 granularity→SQL 分桶函数。
- 默认过滤器运行时自动应用。
- 同库 JOIN、聚合查询、时间分桶的端到端测试。

### Out Of Scope

- sql/external 表参与 JOIN（entity-entity 优先）。
- 自定义 Measure.expression 执行。
- 写操作。

## Execution Plan

### Phase 1 - 跨表 JOIN 执行（P4-2）

Status: planned
Targets: `nop-metadata-service`（JOIN 执行组件）；`ai-dev/design/nop-metadata/01-architecture-baseline.md`「§4.4 查询执行」节（0800-1 创建该节，本 plan 追加 JOIN 子节，不另起编号）

- Item Types: `Decision | Fix | Proof`

- [ ] **D3 裁定并写入设计文档「§4.4」（追加 JOIN 子节）**：跨表 JOIN 路由——按左右表 querySpace 是否相同分派。querySpace 解析：entity 表用 `MetaEntity.querySpace`，external/sql 表用表自身 querySpace。
  - **同库（同 querySpace）**：在单库连接内执行 JOIN（机制见 D4）。
  - **跨库（不同 querySpace）**：应用层拼接（契约见 D5）。
  - 裁定写入 §4.4 并关闭 §八「MetaTableJoin 跨表关联路由」待定问题。
- [ ] **D4 裁定同库 JOIN 机制并写入 §4.4**：entity 表（entity-entity 同库）经 ORM 查询模型——`QueryBean.innerJoin/leftJoin` + `IOrmTemplate.findListByQuery`（**不手写 SQL**）；JOIN 字段直接用 `MetaTableJoin.leftField`/`rightField`（已是属性名字符串，无需解析）；执行前须校验**左右两个**实体均注册于运行时 `IOrmSessionFactory`（否则显式失败）。external/sql 表的同库 JOIN 经 `withConnection` 生成原生 JOIN SQL（标识符经 §2.7.1 D3 白名单 + 参数绑定）。注意 live `QueryBean.rightJoin` 实际为 LEFT_JOIN，且 `joinType=right` 首版全局显式不支持（见 D5，同库/跨库一致）。
- [ ] **D5 裁定跨库应用层拼接契约并写入 §4.4**：左/右表各走 0800-1 单表查询路径取数（`List<Map>`）→ 内存按 join key 合并。须明确：
  - **join key 匹配**：按 `leftField`/`rightField` 列值字符串相等匹配（跨库类型差异由调用方建模保证；首版不做隐式类型转换，不匹配即不关联）。
  - **结果 schema**：左表列 + 右表列（右表列名冲突时加 `alias.` 前缀，`alias` 取自 MetaTableJoin.alias）。
  - **分页**：跨库拼接首版**不保证 LIMIT/OFFSET 语义**（内存合并无全局序）——明确文档化为已知限制；调用方可按合并后结果集大小自行截断。
  - **规模上限**：单侧结果集行数上限（如可配置阈值，超限显式失败抛 inline ErrorCode，防 OOM）。
  - **`joinType=right`**：首版**显式不支持**——抛 `UnsupportedOperationException`/inline ErrorCode（不静默降级为 left，不静默返回左表全集）。
- [ ] 实现同库 JOIN 执行（entity 走 QueryBean，external/sql 走原生 SQL，按 D4）。
- [ ] 实现跨库 JOIN 应用层拼接（按 D5 契约；join key 匹配 + 列前缀 + 规模上限 + right 显式失败）。
- [ ] 失败路径显式：无 join 定义/字段不匹配/实体未注册/规模超限/`joinType=right` 显式失败（不静默返回左表全集）。
- [ ] **端到端测试**：同库 JOIN（两 entity 表，断言 JOIN 后真实关联行 + 具体列值）；跨库 JOIN（两不同 querySpace，断言应用层合并结果 + 列前缀）；`joinType=right` 断言显式失败。

Exit Criteria:

- [ ] 同库 JOIN 与跨库 JOIN 两条路径均返回真实关联数据（非空壳，断言具体列值）
- [ ] D3/D4/D5 裁定已写入 §4.4 且关闭 §八 待定问题；§4.4 内无未决 "TBD"；D5 跨库契约（key 匹配/schema/分页限制/规模上限/right 失败）均已写明
- [ ] **端到端验证**（Minimum Rules #22）：从查询入口 → JOIN 执行 → 关联行输出，同库/跨库各完整跑通
- [ ] **接线验证**（Minimum Rules #23）：JOIN 执行确实调用了 0800-1 单表查询/ORM QueryBean（有断言证明）
- [ ] **无静默跳过**（Minimum Rules #24）：无 join / 字段不匹配 / `right` join / 实体未注册 显式失败，无空方法体/吞异常
- [ ] **新增功能测试**（Minimum Rules #25）：列出同库 JOIN + 跨库 JOIN + `right` 失败 + 规模上限的新增测试
- [ ] `./mvnw clean install -pl nop-metadata -am -T 1C` BUILD SUCCESS
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4 已含 D3/D4/D5
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 指标/维度聚合查询（P4-3）

Status: planned
Targets: `nop-metadata-service`（聚合执行组件）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4（追加聚合子节）

- Item Types: `Decision | Fix | Proof`

- [ ] **D6 裁定聚合执行路径并写入 §4.4**：聚合查询首版对 **external/sql 表（经 withConnection）走原生 SQL**（因 `GroupFieldBean` 仅 `owner`+`name` 无法表达 DATE_TRUNC 等变换，且 granularity 分桶必须下沉到 SQL）。**entity 表原生 SQL 的执行载体为 `IOrmSession/IOrmTemplate.executeQuery(SQL,...)`**（entity 表 querySpace 由 ORM 管理、不经 NopMetaDataSource，故不能用 withConnection）。需把 `entityFieldId`（主键）解析回**物理列名 `columnCode`**（非 ORM 属性名）。**ORM 隐式过滤旁路裁定**：原生 SQL 不应用 ORM 隐式过滤（租户/逻辑删除/版本）；首版策略——对启用了 `useTenant`/`useLogicalDelete` 的 entity 表，聚合 action 须在文档/ErrorCode 中显式提示"原生 SQL 聚合不应用隐式过滤"，并裁定首版是否限制（只允许对未启用隐式过滤的实体执行）或显式 warning——该裁定写入 §4.4，不得静默忽略。
- [ ] **D7 裁定 granularity→SQL 分桶翻译表并写入 §4.4**：按方言（H2/PostgreSQL/MySQL）给出 year/quarter/month/week/day/hour 的分桶表达式表——H2/PostgreSQL 用 `DATE_TRUNC`/`FORMATDATETIME`，MySQL 用 `DATE_FORMAT`。首版以测试库 H2 的可用函数为准（断言 H2 上分桶结果正确）；非约定 granularity 值显式失败（§2.5.2 D1）。方言不在首版支持集时显式失败。
- [ ] 实现聚合查询 action：输入 metaTableId + 选定 Measures + Dimensions（+ 可选 filter/分页）→ 生成聚合 SQL（GROUP BY 维度 + aggFunc 指标）；aggFunc(sum/count/avg/min/max/countDistinct) 翻译为对应 SQL 聚合（countDistinct→`COUNT(DISTINCT col)`）；标识符经 §2.7.1 D3 白名单 + 值参数绑定。
- [ ] 时间维度 granularity 分桶：dimensionType=temporal 时按 D7 翻译表生成分桶表达式；非约定值显式失败。
- [ ] **expression 型 Measure 首版显式不支持**：抛 `UnsupportedOperationException`（不静默跳过、不当 0 返回）。
- [ ] 默认过滤器自动应用：聚合/JOIN 查询执行前，自动注入该表 `isDefault=true` 的 Filter.definition 到 filter 树。**注入落点**：提取为共享 filter-merge helper（与 0800-1 单表查询共用同一 helper，0800-2 不重写 0800-1 的注入逻辑；若 0800-1 已在该 helper 落点则复用，否则本 plan 在 helper 中实现并供 0800-1/0800-2 调用）。
- [ ] **端到端测试**：聚合查询（按维度分组 + sum/count 指标，断言真实聚合值）；时间分桶（temporal 维度 + granularity，断言 H2 上分桶结果）；默认过滤器应用（断言过滤生效）；expression 型 Measure 断言显式失败。

Exit Criteria:

- [ ] 聚合查询对内置 aggFunc（含 countDistinct）返回真实聚合数据；时间维度按 granularity 正确分桶（H2 断言通过）
- [ ] 默认过滤器运行时自动应用已落地（0700-2 Non-Blocking Follow-up 收口）
- [ ] D6/D7 裁定已写入 §4.4（聚合子节含 entity 原生 SQL 执行载体 `executeQuery` + 隐式过滤旁路裁定 + granularity 翻译表）
- [ ] **端到端验证**（Minimum Rules #22）：从聚合查询入口 → GROUP BY/aggFunc → 聚合结果输出完整跑通（H2）
- [ ] **接线验证**（Minimum Rules #23）：聚合生成确实调用了底层数据路径（withConnection/ORM），有断言证明
- [ ] **无静默跳过**（Minimum Rules #24）：非约定 granularity / 不支持方言 / expression 型 Measure 显式失败，不静默跳过
- [ ] **新增功能测试**（Minimum Rules #25）：列出聚合 + countDistinct + 时间分桶 + 默认过滤器 + expression 失败的新增测试
- [ ] `./mvnw clean install -pl nop-metadata -am -T 1C` BUILD SUCCESS
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4 已含聚合语义 + granularity 翻译表
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。**前置**：0800-1 已 closure（单表查询 action 已落地，消费契约对齐）。

- [ ] 同库/跨库 JOIN 执行端到端可用（真实关联数据）
- [ ] Measure+Dimension 聚合查询端到端可用（真实聚合值 + 时间分桶）
- [ ] 默认过滤器运行时自动应用已落地
- [ ] D3/D4/D5 跨库 JOIN 路由裁定 + D6 聚合路径 + D7 granularity 翻译表已写入 §4.4 并关闭 §八 待定问题
- [ ] 不存在空壳实现（无空方法体/静默跳过/吞异常；`joinType=right`/expression Measure 显式失败）
- [ ] 必要 focused verification 已完成
- [ ] 受影响 owner docs 已同步（`01-architecture-baseline.md` §4.4）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 JOIN/聚合执行在运行时确实连通底层数据路径并返回真实计算结果（端到端连通）
- [ ] `./mvnw compile`（`-pl nop-metadata -am`）
- [ ] `./mvnw test`（`-pl nop-metadata -am`）
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0800-2-nop-metadata-cross-table-join-and-aggregation.md --strict` 退出码 0

## Deferred But Adjudicated

（执行中产生的优化项按规则归类记录于此。预判：sql/external 表参与 JOIN 可能作为 optimization candidate deferred。）

## Non-Blocking Follow-ups

- sql/external 表作为 JOIN 右表（继承自 0700-2，首版 entity-entity）。
- 自定义 Measure.expression 执行。
- 复杂 Filter 求值引擎（嵌套 OR/AND/表达式，与 0800-1 共享）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<待 closure audit 填写>>
- Evidence: <<待填写>>

Follow-up:

- <<待 closure 填写；confirmed live defect 不得出现在这里>>
