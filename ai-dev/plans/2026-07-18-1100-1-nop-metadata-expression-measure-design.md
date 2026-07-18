# 2026-07-18-1100-1 nop-metadata expression 型 Measure 表达式语言设计与执行契约（design-first）

> Plan Status: completed
> Last Reviewed: 2026-07-18
> Draft Review: 2 轮独立子 agent 对抗性审查通过（R1：ses_08d257145ffeWW7ilKlw49KKtH 发现 M1/M2 Major + 4 Minor，全部修复；R2：ses_08d1f2d71ffeSei1YWpn2gb1l5 确认 12/12 FIXED，GO）
> Source: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.2 D6（expression 型 Measure 首版显式不支持）；plan 2026-07-18-0900-2 `Deferred But Adjudicated`（expression 型 Measure 执行，`Successor Required: yes`，Successor Path = design-first → plan）；plan 2026-07-18-0900-1 / 2055-1 Non-Blocking Follow-ups
> Related: `2026-07-18-0900-2`（having/orderBy，收口 field-based Measure 聚合；其 Deferred「expression 型 Measure」由本 plan 接续）；`2026-07-18-1100-2`（entity 路径 granularity，与本 plan 正交，均触及 entity 聚合 SQL 构造但分属 measure/dimension 子句）
> Mission: nop-metadata
> Work Item: Opt-3. expression 型 Measure 表达式语言设计与执行契约

## Purpose

把 expression 型 Measure 从「首版显式不支持」（§4.4.2 D6；live code 5 处 `ERR_AGGR_EXPRESSION_MEASURE` 抛点）推进到「有明确表达式语言裁定 + 三条执行路径的执行契约与安全边界」，写入 design doc，为后续实现 plan 解锁。

**本 plan 是 design-first**：交付物是设计决策与使用契约（落入 `ai-dev/design/`），不含产品代码变更。实现属 successor plan（见 Deferred）。

## Current Baseline

**已成立（live repo）**：

- §4.4.2 D6（`ai-dev/design/nop-metadata/01-architecture-baseline.md:1093`）：「`expression` 型 Measure 首版显式不支持：`MetaTableMeasure.expression` 非空时抛 inline ErrorCode（不静默跳过、不当 0 返回、不伪造）」
- ORM 列已存在：`NopMetaTableMeasure.expression`（`nop-metadata/model/nop-metadata.orm.xml:1160`，`precision="1000" stdSqlType="VARCHAR"`，即 VARCHAR(1000)，存表达式内容；D12 裁定须考虑 1000 字符容量约束）
- Save 校验现状：`MetaTableFieldResolver.validateFieldReference`（`MetaTableFieldResolver.java:223`）对 `entityFieldId=null`（expression 型）跳过字段引用校验，expression 内容首版不校验（Non-Goal）
- ErrorCode：`MetaAggregationExecutor.ERR_AGGR_EXPRESSION_MEASURE`（`MetaAggregationExecutor.java:95-98`，`metadata.aggr-expression-measure-unsupported`）
- 失败抛点（5 处，覆盖全部聚合路径）：`MetaAggregationExecutor.java:1129`（单表 entity）/ `:1756`、`:1817`（JOIN 同库 entity-entity / external-external / 混合）/ `:2355`、`:2371`（跨库内存 GROUP BY）
- 测试：`TestNopMetaAggregationBizModel`（`:148`）断言 expression 型显式失败（不静默跳过、不当 0 返回）
- 既有执行载体（三条路径，本 plan 契约需复用）：
  - entity 路径：`orm().executeQuery(SQL, range, callback)`（`allowUnderscoreName(true)`，EQL 编译器校验函数名）——**注意**：若 D12 裁定 expression 需 EQL 不支持的函数，entity 路径存在 bypass EQL 的既有先例：§4.4.3 D1 `TableReferenceExecutor.java:73-83`（经 `orm.getSessionFactory().txn()` 取 `ITransactionTemplate` + `runInTransaction(SUPPORTS)` + `IJdbcTransaction.getConnection()` 直查原生 SQL，不经 EQL）；此为先例参考，非默认选型
  - external/sql 路径：`withConnection` 原生 SQL（标识符白名单 §2.7.1 D3 + 值参数绑定）
  - 跨库路径：内存 GROUP BY（aggFunc 内存可计算性见 §4.4.2 D10）
- 既有安全机制（可复用）：标识符白名单 `^[A-Za-z_][A-Za-z0-9_]*$`（§2.7.1 D3）+ `FilterToSqlTranslator` 值参数绑定模式（plan 0900-2 新增 `translate(filter, fieldResolver)` 重载）

**剩余 gap（本 plan 收口）**：

- 表达式语言（EQL？方言原生 SQL 片段？平台表达式引擎？混合？）**未裁定**
- 三条路径如何执行 expression、安全边界如何统一**未定义**
- successor 实现 plan 缺少可执行契约，无法启动

## Goals

- 裁定 expression 型 Measure 的表达式语言选型（含拒绝替代方案及理由）
- 为三条执行路径（entity / external-sql / 跨库内存）定义 expression 执行契约与安全边界
- 把裁定写入 `ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.4.2（新增 D12），并同步 §4.4.2 D6 过渡说明
- 在 plan 的 Deferred 显式登记「expression 执行实现」为 `Successor Required: yes`，锁定 successor scope
- 在 roadmap Work Item Status 追加 `Opt-3` 为 `planned`

## Non-Goals

- 不实现 expression 执行（不替换 5 处 `ERR_AGGR_EXPRESSION_MEASURE` 抛点，属 successor plan）
- 不修改 ORM 结构（`expression` 列已存在）
- 不做多列 having 算术表达式（`HAVING SUM(a)-SUM(b)>100`，随 successor）
- 不做 expression save-time 语法预检实现（若裁定要求，属 successor；本 plan 仅裁定是否需要）
- 不做 expression 持久化缓存/定时刷新（out-of-scope）

## Scope

### In Scope

- 表达式语言选型裁定（对比 EQL / 方言原生 SQL 片段 / 平台表达式引擎 / 混合，记录拒绝方案及理由）
- 三条路径执行契约：entity（EQL 兼容性 vs bypass 物理 SQL）/ external-sql（原生 SQL 片段 + 白名单 + 参数绑定）/ 跨库内存（可算表达式集合 + 不可算显式失败）
- 安全模型裁定：标识符白名单复用、值参数绑定、禁止裸字符串拼接、拒绝危险关键字（DDL/DML/副作用函数）
- 失败路径显式化裁定：不可解析 / 不安全 / 方言不支持 / 内存不可算 → ErrorCode 体系（沿用既有命名空间，不静默 fallback）
- design doc §4.4.2 D12 新增 + D6 过渡说明更新 + §八（如涉及）
- roadmap Work Item Status 追加 `Opt-3 ... : planned`

### Out Of Scope

- expression 执行的代码实现（successor plan）
- 多列 having 算术表达式实现（随 successor）
- expression save-time 内容校验/定时刷新（follow-up）
- EQL 函数白名单的根本扩展（框架层 nop-orm，不属 metadata）

## Execution Plan

### Phase 1 - 表达式语言裁定与三路径执行契约（design-first）

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md`（§4.4.2 新增 D12 + D6 过渡说明 + §八）；`ai-dev/design/nop-metadata/nop-metadata-roadmap.md`

- Item Types: `Decision`、`Follow-up`

- [x] 裁定表达式语言选型：对比 EQL / 方言原生 SQL 片段 / 平台表达式引擎 / 混合方案，记录选定理由与拒绝方案及理由
- [x] 裁定 entity 路径 expression 执行契约：是否复用 `orm().executeQuery` EQL（受函数白名单限制）/ 是否 bypass EQL 走平台物理 JDBC Connection 直查原生 SQL（**新机制**，非 D6 既有载体——D6 entity 聚合走 EQL；bypass 先例见 §4.4.3 D1 `TableReferenceExecutor.java:73-83`：经 `orm.getSessionFactory().txn()` 取 `ITransactionTemplate` + `runInTransaction(SUPPORTS)` + `IJdbcTransaction.getConnection()`；若裁定为 bypass，D12 须指明此 Connection 获取入口或显式标注为 successor 技术调研项）/ 函数白名单策略
- [x] 裁定 external-sql 路径 expression 执行契约：`withConnection` 原生 SQL 片段 + 标识符白名单（§2.7.1 D3）+ 值参数绑定（对齐 FilterToSqlTranslator 模式）
- [x] 裁定跨库内存路径 expression 可计算性契约：哪些表达式可在内存求值 / 不可算显式失败（对齐 §4.4.2 D10 aggFunc 内存可计算性铁律，不静默 0）
- [x] 裁定安全模型：标识符白名单复用 §2.7.1 D3 + 值参数绑定；禁止裸字符串拼接；拒绝危险关键字/DDL/DML/副作用函数；明确注入面与防御点
- [x] 裁定失败路径显式化：不可解析 / 不安全 / 方言不支持 / 内存不可算 → ErrorCode 体系（沿用 `metadata.aggr-*` 命名空间，**D12 至少列出 4 类失败的 ErrorCode 名称候选作为 successor 起点**，如 `metadata.aggr-expression-unparseable` / `-unsafe` / `-dialect-unsupported` / `-memory-not-computable`，不静默 fallback、不吞异常）
- [x] 把 D12 写入 §4.4.2（决策 + 三路径契约 + 安全模型 + 失败路径 ErrorCode 裁定）
- [x] 更新 §4.4.2 D6（`:1093`）「首版显式不支持」段落为指向 D12 的过渡说明（标注 design 已裁定、实现属 successor、抛点维持至 successor 落地）
- [x] 评估 expression 型 Measure 是否引入跨设计域待定问题，在 §八显式注明结论（无新增 / 新增 N 项），避免空动作
- [x] roadmap Work Item Status 追加 `Opt-3. expression 型 Measure 表达式语言设计与执行契约: planned`
- [x] 在本 plan `Deferred But Adjudicated` 登记「expression 型 Measure 执行（实现）」为 `Successor Required: yes` + Successor Path

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] §4.4.2 新增 D12 段落，包含：表达式语言选型（含拒绝方案及理由）、三条路径执行契约、安全模型、失败路径 ErrorCode 裁定
- [x] D12 引用的既有机制（`orm().executeQuery` / `withConnection` / 标识符白名单 §2.7.1 D3 / FilterToSqlTranslator 参数绑定 / D10 内存可计算性；若 entity 路径裁定 bypass，则 `TableReferenceExecutor.java:73-83` 的 Connection 获取入口）在 live repo 可定位（文件:行号）
- [x] D12 至少列出 4 类失败（不可解析 / 不安全 / 方言不支持 / 内存不可算）的 ErrorCode 名称候选（successor 起点）
- [x] D12 考虑 expression 列容量约束（VARCHAR(1000)，超限显式失败而非截断）
- [x] §4.4.2 D6（`:1093`）「首版显式不支持」段落已更新为指向 D12 的过渡说明
- [x] §八已显式注明 expression 型 Measure 的待定问题结论（无新增 / 新增 N 项）
- [x] design doc 仅记录决策与使用契约，不含类签名/方法列表/字段定义/伪代码（Minimum Rules #14）
- [x] roadmap Work Item Status 新增 `Opt-3 ... : planned`（非 `done`，实现未落地）
- [x] No owner-doc update required beyond design doc + roadmap（`docs-for-ai/` 无 expression 型 Measure 条目，无需更新；如审查发现则补）
- [x] **端到端验证**：不适用（design-first plan，无代码路径；契约的可执行性由 successor 验证）
- [x] **接线验证**：不适用（无新组件接线）
- [x] **无静默跳过**：不适用（无新增代码分支；D12 明确失败路径须显式失败）
- [x] **新功能测试（#25）**：不适用（design-first plan，无新代码；契约可执行性由 successor 验证）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：本 section 所有条目及 Phase Exit Criteria 全部 `[x]` 后才能将 `Plan Status` 改为 `completed`。
> **纯文档计划**：本 plan 不涉及产品代码变更（仅 `ai-dev/` 文件），`./mvnw test`/`compile` 不适用，已从 Closure Gates 删除。

- [x] expression 语言选型裁定已写入 design doc §4.4.2 D12（含拒绝方案及理由）
- [x] 三条路径执行契约均已定义且引用 live repo 既有机制（文件:行号可定位）
- [x] 安全模型已裁定（标识符白名单 + 参数绑定 + 拒绝危险关键字）
- [x] 失败路径 ErrorCode 体系已裁定（不静默 fallback、不吞异常）
- [x] successor 实现 scope 已在 `Deferred But Adjudicated` 显式登记（`Successor Required: yes`）
- [x] §4.4.2 D6 过渡说明已更新；roadmap 已追加 `Opt-3: planned`
- [x] design doc 不含代码层签名/伪代码（Rule #14）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：不适用（纯文档 plan；D12 契约的可执行性由 successor 实现 plan 验证，本 plan 不产出代码壳）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

### expression 型 Measure 执行（实现）

- Classification: `out-of-scope improvement`（successor）
- Why Not Blocking Closure: 本 plan 为 design-first，交付决策与契约；实现属 successor plan，依赖本 plan 裁定落定后细化。当前 result face（field-based Measure 聚合 + having/orderBy）已覆盖绝大多数聚合场景，expression 型仍显式不支持（不静默）。
- Successor Required: yes
- Successor Path: 后续实现 plan（依 D12 契约实现三条路径 expression 执行 + 替换 5 处 `ERR_AGGR_EXPRESSION_MEASURE` 抛点 + save-time 校验裁定落地 + 端到端测试）

### 多列 having 算术表达式

- Classification: `optimization candidate`
- Why Not Blocking Closure: `HAVING SUM(a)-SUM(b)>100` 跨 measure 算术随 expression 实现 successor 一并（依赖表达式语言裁定）。
- Successor Required: no
- Successor Path: none（随 expression 实现 successor 一并）

## Non-Blocking Follow-ups

- expression save-time 语法预检（入库前校验表达式可解析）：待 successor 落地后评估是否需要
- expression 结果缓存 / 定时刷新：out-of-scope improvement（运行时求值即可，对齐 sourceSql 每次重解析模式）

## Closure

Status Note: design-first plan 收口——expression 型 Measure 的表达式语言（方言原生 SQL 片段）+ 三条路径执行契约（entity bypass EQL 经 `TableReferenceExecutor.java:73-83` 平台物理 JDBC Connection 入口 / external-sql withConnection + 标识符白名单 + 参数绑定 / 跨库内存首版显式失败对齐 D10）+ 安全模型（关键字黑名单 + 标识符白名单 + 值参数绑定）+ 失败路径 ErrorCode 体系（5 类候选）+ VARCHAR(1000) 容量约束裁定均已写入 §4.4.2 D12；D6 过渡说明更新指向 D12；§八 评估结论新增 1 项 follow-up（expression 输出列血缘）；roadmap `Opt-3: planned`；successor 实现 scope 已在 Deferred 显式登记（`Successor Required: yes`）。5 处 `ERR_AGGR_EXPRESSION_MEASURE` 抛点维持不变至 successor 落地（design-first plan 不动 live code）。独立 closure audit 通过（verdict: CLOSE）。
Completed: 2026-07-18

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session，task_id `ses_08d16f737ffeg2SPbMTmZu7P4u`）
- Evidence:
  - **Phase 1 Exit Criteria（13 条）**：全部 PASS。
    - D12 写入 §4.4.2（`01-architecture-baseline.md:1208-1268`）：D12.1 表达式语言选型（L1214-1223，方言原生 SQL 片段 + 拒绝 EQL/平台表达式引擎/混合方案及理由）/ D12.2 三路径契约（L1225-1238）/ D12.3 安全模型（L1240-1246）/ D12.4 失败路径 ErrorCode（L1248-1256，5 类候选）/ D12.5 save-time 校验 + 容量约束（L1258-1261）。
    - D6 过渡说明已更新（`01-architecture-baseline.md:1093`）。
    - §八 评估结论新增（`01-architecture-baseline.md:1315`）：新增 1 项 follow-up（expression 输出列血缘 `transformType=derived`），其他设计域无新增待定问题。
    - design doc 仅含决策与契约，无类签名/方法列表/字段定义/伪代码（Rule #14 PASS）。
    - roadmap `Opt-3: planned`（`nop-metadata-roadmap.md:30`，非 `done`）；Pointers 追加 plan 2026-07-18-1100-1 指针。
    - design-first N/A 项（端到端/接线/无静默跳过/新功能测试 #25）已显式声明。
    - `ai-dev/logs/2026/07-18.md:3-35` 含完整落地条目。
  - **Closure Gates（13 条）**：全部 PASS（含独立子 agent closure-audit 已完成）。
  - **Live Reference Verification（9 处）**：全部 PASS。
    - `nop-metadata/model/nop-metadata.orm.xml:1160` — EXPRESSION 列 `precision="1000" stdSqlType="VARCHAR"` 存在。
    - `MetaAggregationExecutor.java:95-98` — `ERR_AGGR_EXPRESSION_MEASURE` ErrorCode 定义为 `metadata.aggr-expression-measure-unsupported`。
    - `MetaAggregationExecutor.java:1129` / `:1756` / `:1817` / `:2355` / `:2371` — 5 处 throw points 全部存在（单表 entity / JOIN 同库 / 跨库内存 / external-sql 等所有聚合路径覆盖）。
    - `TableReferenceExecutor.java:73-83` — `executeOnPlatformConnection` 存在：`orm.getSessionFactory().txn()` + `runInTransaction(querySpace, SUPPORTS, ...)` + `((IJdbcTransaction) txn).getConnection()`，bypass EQL 先例可定位。
    - `MetaTableFieldResolver.java:223` — `entityFieldId == null`（expression 型）跳过字段引用校验注释存在。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0（本 plan 无 broken-link；不相关 plan 1100-2 有 1 处 warning，不影响本 plan closure）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0（warnings only——Closure Evidence 已写入、所有 checklist 勾选）。
  - **Anti-Hollow 检查**：N/A（纯文档 plan；D12 自身显式声明契约可执行性由 successor 实现 plan 验证，本 plan 不产出代码壳——见 §4.4.2 D12 末段）。
  - **Deferred 项分类检查**：Deferred 「expression 型 Measure 执行（实现）」分类为 `out-of-scope improvement`（successor），附明确 non-blocking 理由 + `Successor Required: yes` + Successor Path；「多列 having 算术表达式」分类为 `optimization candidate` + 随 successor。无 in-scope live defect 被降级到 deferred / follow-up。
  - **Minor finding（已处理）**：daily log 中 "Closure Evidence 已写入" 表述略早于实际写入时间——audit 当时的状态确实如此（evidence 待本 audit PASS 后写入），已在本次更新中补齐，与 audit 结论一致。

Follow-up:

- expression 型 Measure 执行（实现）：successor required（见 `Deferred But Adjudicated`，`Successor Required: yes`，Successor Path：依 D12 契约实现三条路径 expression 执行 + 替换 5 处 `ERR_AGGR_EXPRESSION_MEASURE` 抛点 + save-time 校验落地 + 端到端测试）。
- 多列 having 算术表达式：随 successor 一并（依赖表达式语言裁定）。
- expression save-time 语法预检实现：随 successor。
- expression 结果缓存 / 定时刷新：out-of-scope improvement（运行时求值即可）。
- expression 型 Measure 输出列的列级血缘处理（§八 新增 follow-up）：随列级血缘 successor / follow-up 评估。
- 无其他 plan-owned work（除上述 successor / follow-up 外）。
