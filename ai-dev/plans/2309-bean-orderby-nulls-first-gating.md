# 2309 bean 路径与聚合路径 ORDER BY 空序方言门控（F1 + F3 合并）

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: `ai-dev/plans/2308-eql-orderby-nulls-first-multi-dialect.md` 的 `Deferred But Adjudicated`（F1 / F3 两条「moved to explicit successor ownership」）
> Related: `ai-dev/design/nop-orm-scope-and-non-goals.md` §3.8、`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`

## Purpose

把 **EQL 之外**的两条 ORDER BY 空序产出路径接到与 EQL 相同的 `supportNullsFirst` 能力位上，
消除当前**已确认的 live defect**：在不接受 `NULLS FIRST/LAST` 语法的数据库上产出语法错误的 SQL。

## Current Baseline

EQL 通道已由 plan 2308 收口：空序只依赖 `IDialect.isSupportNullsFirst()`，
true → 原生输出，false → `CASE WHEN` 改写，契约见 design §3.8。

**F1 bean 路径无方言门控**（`nop-persistence/nop-orm` + `nop-kernel/nop-core`）：

- `nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java:224` —
  `sb.orderField(defaultOwner, orderField)`，把 `OrderFieldBean`（含 `nullsFirst`）交给 `SQL.SqlBuilder`。
- `nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SQL.java` — **全文件 0 处 dialect 引用**，
  即 `SQL.SqlBuilder` 根本无法知道目标方言，空序修饰只能无条件拼接。
- 后果：经 `QueryBean.orderBy(...)` 的查询在 MySQL / MariaDB / DM / tdengine 上会产出
  `ORDER BY col ASC NULLS FIRST` 这类**语法错误** SQL。

**F3 `nop-metadata` 聚合路径硬编码分支**（`nop-metadata/nop-metadata-service`）：

- `.../query/AggregationHelper.java:331` — `buildOrderByClause(..., String dialect)`，
  `dialect` 是 **JDBC `databaseProductName` 字符串**，不是 `IDialect`。
- `.../AggregationHelper.java:362` — 仅 `"MySQL".equalsIgnoreCase(dialect)` 走分支；
  **其余所有方言（含 `dialect == null`）在 `:376` 无条件 `sb.append(" NULLS FIRST"/" NULLS LAST")`**。
- 已确认缺陷：`mariadb` / `dm` / `tdengine` / `db2` 均无 `NULLS` 子句语法，走 else 分支即产出错误 SQL。
- `:365` 的 `matchesMySqlDefault = (nullsFirst && !desc) || (!nullsFirst && desc)`
  正是 design §3.8 明确否决的**单布尔推导缺省**反模式（DuckDB `ASC`/`DESC` 都不翻转即为反例）。
- 两条 ORM 路径直接传 `null`（见 `EntityAggregationProcessor` / `EntityEntityJoinAggregationProcessor` 裁定），
  连字符串门控都用不上。

## Goals

- F1：`SQL.SqlBuilder` 的空序输出按目标方言能力位决定，与 EQL 通道**共用同一契约**。
- F3：`AggregationHelper.buildOrderByClause` 不再硬编码 `MySQL` 字符串比较；
  `databaseProductName → IDialect` 的映射有唯一入口。
- 两条路径都**不做**「请求 == 方言缺省则省略子句」的优化（design §3.8 已裁定）。
- 补齐方言取值守护测试，避免重演 F3 这种「以为只有 MySQL 特殊」的盲区。

## Non-Goals

- 不做 `ORDER BY ... COLLATE`（2308 已裁定 out-of-scope，需跨库 collation 名字映射表）。
- 不改 `QueryBean` / `OrderFieldBean` 的**公开 API 签名**（用户可见契约），
  除非为注入方言能力位所必需——若必须改，走 Protected Area 流程（`ask-first`）。
- 不合并 EQL 与 bean 两条路径的产出实现；它们是两条独立通道，只共享**契约**。

## Scope

### In Scope

- `SQL.SqlBuilder` 空序输出的方言感知。
- `AggregationHelper.buildOrderByClause` 的方言判定改造。
- `databaseProductName → IDialect` 映射入口。
- 对应测试（含取值守护与至少一库真实执行验证）。

### Out Of Scope

- `nop-core` 中除 `SQL.SqlBuilder` 空序片段以外的重构。
- 聚合查询的分页 / 指标语义变更。

## Execution Plan

### Phase 1 - 前置核实与裁定

Status: planned
Targets: 上述文件

- Item Types: `Decision`

- [ ] 逐条复核本 plan `Current Baseline` 的 file:line 与实际代码一致（尤其 `:362`/`:376` 的分支覆盖范围）
- [ ] 裁定方言能力如何在 `nop-core` 的 `SQL.SqlBuilder` 中可得：
      静态参数传入 / 调用方前置判定 / 其他——**必须评估对 `nop-core` 公开签名的影响**（Protected Area）
- [ ] 裁定 `databaseProductName → IDialect` 的映射入口位置（`DialectManager` 扩展 or `AggregationHelper` 侧适配）

Exit Criteria:

- [ ] 裁定含被拒方案与理由，不留「后续再说」
- [ ] 若触及 `nop-core` 公开签名，已按 ask-first 取得用户确认并留痕
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - F3 聚合路径门控

Status: planned
Targets: `nop-metadata/nop-metadata-service`

- Item Types: `Fix`

- [ ] `buildOrderByClause` 改为按 `IDialect.isSupportNullsFirst()` 判定，删除 `"MySQL"` 字符串硬编码
- [ ] 删除 `:365` 的单布尔缺省推导（与 design §3.8 冲突）
- [ ] 两条传 `null` 的 ORM 路径改为可解析出真实方言，或显式失败并给出可行动错误码

Exit Criteria:

- [ ] **证明**：在 `mariadb` / `dm` / `tdengine` 任一方言下产出可执行 SQL 或响亮失败，不再无条件拼 `NULLS`
- [ ] 有测试断言不再出现「非 MySQL 方言无条件 append NULLS」的路径
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - F1 bean 路径门控

Status: planned
Targets: `nop-kernel/nop-core`、`nop-persistence/nop-orm`

- Item Types: `Fix`

- [ ] `SQL.SqlBuilder` 空序输出接入方言能力位
- [ ] `DaoQueryHelper:224` 调用链把方言信息传递到位
- [ ] 不支持原生语法的方言走 CASE 改写，或与 EQL 一致地显式失败——裁定并记录

Exit Criteria:

- [ ] **端到端验证**：`QueryBean.orderBy(...)` 一路到真实库执行，断言结果行序（非「SQL 含 NULLS 字样」）
- [ ] 至少一库真实执行（docker 门控，范式对齐 `TestEqlNullsOrderMatrix`）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 取值守护与文档

Status: planned
Targets: `nop-dao/src/test/**`、`docs-for-ai/**`

- Item Types: `Proof`

- [ ] 方言取值守护覆盖本计划涉及的能力位
- [ ] `docs-for-ai` 中 bean 路径 / 聚合路径的空序行为描述与 live baseline 一致

Exit Criteria:

- [ ] **证明**：变异任一取值导致测试失败
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata-service --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 本 plan 自身 0 条 broken link
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] F1 / F3 两条 confirmed live defect 均已修复，无一留在 non-blocking 区
- [ ] 两条路径与 EQL 通道共用同一空序契约（design §3.8）
- [ ] 端到端验证断言结果行序，且经真实数据库执行
- [ ] 方言取值有常驻测试守护 + 变异验证
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service,nop-persistence/nop-orm,nop-kernel/nop-core -am` 通过
- [ ] 跨模块回归（2308 的 8 模块清单 + `nop-metadata-service`）全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2309-bean-orderby-nulls-first-gating.md --strict` 退出码 0
- [ ] 独立子 agent closure audit 已完成，`Closure` 段已写入 evidence

## Deferred But Adjudicated

### `ORDER BY ... COLLATE`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需先有跨库 collation 名字映射表（MySQL `utf8mb4_general_ci` / PG collation 名 / Oracle `NLS_SORT` 各异），
  且 H2 实测在 ORDER BY 表达式上不接受 COLLATE 语法。2308 与本计划均维持不支持
- Successor Required: `yes`
- Successor Path: 独立 plan（需先产出映射表 + 各库实测矩阵）

## Non-Blocking Follow-ups

- `docs-en/dev-guide/orm/` 下的英文 ORM 文档若描述 bean 路径空序行为，需与本计划终态对齐
- 无法实测方言（oracle / dm / tdengine / db2）的空序行为：`supportNullsFirst=false` 走 CASE 改写
  在任何方言上都语义正确，故未实测不构成正确性风险，只影响 SQL 冗长度

## Closure

Status Note: 待填写
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- 待填写