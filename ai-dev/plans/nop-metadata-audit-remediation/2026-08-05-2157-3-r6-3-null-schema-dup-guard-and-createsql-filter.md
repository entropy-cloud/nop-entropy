# R6-3 NULL-schema 重复行防护 + createSqlTable 重复守卫过滤（AR-07/AR-08）

> Plan Status: completed
> Last Reviewed: 2026-08-05
> Draft Review: R1 `ses_02d972abcffeiHR3WBNv3d0qiW`（2 Blocker：三选一决策结构自相矛盾（A 单独不充分/B 撞 Oracle R4.2 D1 陷阱/C 需跨 flush）——已重写为组合裁定 + R4.2 D1 承接 + 锁跨 flush 约束 + 回退裁定；并发验证空壳化（顺序双插 vacuous）——已改并发双插为唯一判别性验证 + 测试规格（预建 module/latch/稳定失败要求）；4 Major：Phase 2 过滤语义（null 或空串 vs UK 对齐）——已改 isDelta=0 AND metaSchema IS NULL 逐字对齐 + 路径 B 交互测试；B 路径读路径 blast radius/迁移范围/owner-doc 条件化——已入 Baseline 枚举 5 处消费方；测试命令 -am 统一）；R2 `ses_02d8bd24effe5sTI7IBnCw6yFn`（结论：**可以直接执行**，0 Blocker / 0 Major；4 条 Minor 建议已吸收——测试先行顺序 + session 隔离排查 + ≥20 轮稳定失败判定标准入测试规格，arm-index 注记形式执行时裁定）。consensus 达成。
> Mission: nop-metadata-audit-remediation
> Work Item: MR6（R6.3）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR6 段 R6.3 行 + Follow-up Backlog AR-07/AR-08）、`ai-dev/audits/arm-index-nop-metadata.md`（§P2 MR6 裁决记录）
> Related: 执行顺序 `{3}` of 3 — R6.1（`2026-08-05-2157-1`）、R6.2（`2026-08-05-2157-2`）先行；本 plan 文件域（entity/NopMetaDataSourceBizModel + NopMetaTableBizModel）与 R6.1/R6.2 不重叠，但 upsert 竞态修复（Phase 1）可能涉及 ORM 模型（`nop-metadata/model/nop-metadata.orm.xml`）——若触发 Protected Area 按 plan-first 声明（本 plan 即裁决载体）；**内部依赖：Phase 2（createSqlTable 守卫）过滤语义取决于 Phase 1 的路径 B 裁定结果（空串占位 vs 保持 null）**——执行顺序为 Phase 1 → Phase 2 串行；Deps 门禁（R6.0 done）已解除

## Purpose

按 MR6 R6.3 行收口两项 Backlog finding（2026-08-05 两轮审计登记，R6.0 live 复核提级）：

1. **AR-07**：R4.2 后 `UK_NOP_META_TABLE_MODULE_NAME = (metaModuleId, tableName, isDelta, metaSchema)` 含可空 META_SCHEMA；`upsertExternalTable` 的 find-then-insert 非原子——NULL-schema 并发同名表两插皆成功（NULL≠NULL 不参与 UK 冲突判定），DB 层防线移除且 Java 层无法兜底，静默重复行。修复方向（roadmap）：原子 upsert 或空串占位。
2. **AR-08**：`createSqlTable` 重复守卫查询仅按 `(metaModuleId, tableName)` 查重，无 isDelta/schema 过滤——比 4 列 UK 更严，已导入 delta 行/异 schema 行存在时误报 `ERR_SQL_VIEW_TABLE_EXISTS`，DB 本身允许共存。修复方向：守卫查询补过滤。

目标状态：NULL-schema 重复行防线恢复（应用层兜底或 UK 生效），createSqlTable 守卫与 4 列 UK 语义一致，回归测试钉死双侧行为。

## Current Baseline

2026-08-05 live repo 核对（R6.0 裁决记录 + 本次复核）：

- **AR-07（confirmed，竞态退化）**：`NopMetaDataSourceBizModel.upsertExternalTable`（`entity/NopMetaDataSourceBizModel.java:428-468`）：先按 `(metaModuleId, tableName)` 拉候选集（EQL 规避 SCHEMA reserved keyword 的既有设计，:423-426），Java 层按 schema 精确匹配（`normalizeSchemaForMatch` :470-476，null/空串→null）找到则 update（:461-467）、找不到则 newEntity + `saveEntity`（:449-460，isDelta=0）。R4.2（plan-2026-08-05-1625-1）将 UK 扩展为含可空 `metaSchema` 的 4 列后，**NULL-schema 并发插入时两行 metaSchema 均 NULL，UK 不拦截**（NULL≠NULL）；Java 层 find-then-insert 无锁无重试——静默重复行。既有测试 `TestNopMetaTableMultiSchemaUpsert.java`（R4.2 回归，S1/S2 断言 :76-79，distinct 计数 + re-sync :80-86）验证两 schema 共存 + 单 schema 重复同步为 update，未覆盖并发/NULL-schema 双插场景。**R4.2 D1 先例（必须承接）**：plan-2026-08-05-1625-1 已裁定并文档化三类陷阱——(1) 主流值（entity/SQL 表全部 NULL）改造成哨兵值 = GraphQL 可观测变更；(2) 全量迁移成本；(3) Oracle `''`≡NULL：INSERT 显式 `''` 报 ORA-01400、`SET META_SCHEMA=''` 静默 no-op、ALTER NOT NULL 报 ORA-02296。**IEntityDao 无 DB 级 UK upsert 抽象**（仅 saveEntity/updateEntity/saveOrUpdateEntity:112，后者按主键语义；ISqlExecutor/IDialect 层无 upsert/merge/on-conflict 抽象）。**flush 位置**：`syncExternalTables`（:186）循环内调用 `upsertExternalTable`，返回后 :187 `orm().flushSession()`——**flush 在 upsertExternalTable 之外**，锁若只包住 upsertExternalTable 则 T1 行未 flush、T2 同键 find 查不到，竞态保留。
- **AR-08（confirmed，误报面）**：`NopMetaTableBizModel.createSqlTable`（`entity/NopMetaTableBizModel.java:164-173`）：守卫查询 `dupQuery` 仅 `eq(metaModuleId)` + `eq(tableName)`（:166-169），命中即抛 `ERR_SQL_VIEW_TABLE_EXISTS`（:170-173）——无 `isDelta` 过滤（createSqlTable 恒建 isDelta=0 行，:176）、无 `metaSchema` 过滤（createSqlTable 恒 null-schema）。若同模块同表名存在 isDelta=1（delta 行）或异 schema 行，守卫误报；而 4 列 UK（含 isDelta + metaSchema）实际允许共存。**守卫正确语义 = 逐字对齐 4 列 UK**：`isDelta=0 AND metaSchema IS NULL`（`FilterBeans.eq(prop, null)` 或 `isNull`，执行时按 FilterBeans API 裁定）——不是"null 或空串归一"（`''` 与 NULL 在 UK 中是两个不同键，DB 允许共存；若 Phase 1 走空串占位，外部表 NULL-schema 行落库为 `""`，守卫若按空串归一会把 `""` 外部行误判为重复——AR-08 误报面复发）。
- **读路径消费方（Phase 1 路径 B 时需评估，已枚举）**：`getMetaSchema` 消费方 5 处——`NopMetaProfilingRuleBizModel:149`、`NopMetaQualityRuleBizModel:308`、`NopMetaTableBizModel:345`、`NopMetaDataSourceBizModel:363`（resolveDefaultSchema → profile/catalog/quality 执行链）、`QualityAlertWorkflowProcessor:125`。空串占位后 `""` 流入 `effectiveSchema` → JDBC 元数据查询 schemaPattern——H2 `getTables("",...)` 匹配空 schema 名（空结果）而 null 表示不过滤：null-schema 外部表 profile/catalog 可能从"全量"变"零结果"的功能回归。**迁移范围**：`UPDATE ... SET meta_schema='' WHERE meta_schema IS NULL` 会波及 entity 表行（OrmModelImporter 恒 null）与 SQL 表行（createSqlTable 恒 null）——路径 B 必须裁定这些行是否同步转 `""` 及 createSqlTable 写路径是否同步改。
- **ORM 模型现状**：`nop-metadata/model/nop-metadata.orm.xml` NopMetaTable UK（4 列，含 constraint 属性，R3.19/R4.2 已落地）；NopMetaTable 列含 IS_DELTA（domain=boolFlag, mandatory）、META_SCHEMA（可空）。deploy/sql 含 oracle/ 目录（Oracle 为一等方言，R4.2 全程覆盖）。
- **既有回归测试**：`TestNopMetaTableMultiSchemaUpsert.java`（R4.2 多 schema 行为回归，`NopTestConfig(localDb=true, initDatabaseSchema=true)`）；`TestNopMetaTableBizModel.java`（createSqlTable 正/负路径 :343-406）。
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` → nop-metadata 子树 **895 tests / 0 failures / 0 errors / 0 skipped**（service 894 + web 1，R6.0 收口口径）。

## Goals

- upsertExternalTable 的 NULL-schema 重复行防线恢复：并发双插（含 NULL-schema）不再产生静默重复行（组合裁定，见 Phase 1 Decision——路径 C per-key 锁跨 flush 为推荐，路径 B 空串占位为备选，路径 A 原子 upsert 单独不充分）
- createSqlTable 守卫查询与 4 列 UK 语义一致：delta 行 / 异 schema 行存在时不误报 `ERR_SQL_VIEW_TABLE_EXISTS`；真重复（同模块同表名同 isDelta=0 同 null-schema）仍 fail-fast
- 回归测试钉死双侧行为（含并发场景，若可行）
- arm-index §P2 对应行终态（fixed）+ roadmap R6.3 行 → done

## Non-Goals

- 不改变 R4.2 已裁定的 UK 列维度（metaSchema 保持可空，路径 A 语义不反转）
- 不做分布式锁（R4.3 已裁定单实例 baseline，分布式锁不在 scope；并发防护以单实例进程内手段为上限）
- 不扩展至 querySpace 维度（upsert 幂等的跨数据源覆盖语义，R4.2 注释 :420-421 已声明 follow-up）
- 不触碰 R6.1/R6.2 文件域（quality/field、connection）
- 不重建 deploy/sql DDL（除非 Phase 1 裁定要求列/UK 变更——此时按 model-first + 三方言再生成，见 Phase 1 Decision）

## Scope

### In Scope

- `NopMetaDataSourceBizModel.upsertExternalTable` NULL-schema 重复行防护（Decision + Fix）
- `NopMetaTableBizModel.createSqlTable` 守卫查询 isDelta/schema 过滤（Fix）
- `TestNopMetaTableMultiSchemaUpsert` / `TestNopMetaTableBizModel` 对应回归测试（Fix）
- arm-index §P2 + roadmap R6.3 行终态更新（Fix）
- `ai-dev/logs/2026/08-05.md`（或执行当日日志）更新（Follow-up）

### Out Of Scope

- P2-10/P2-13（R6.1）、P2-11/P2-12（R6.2）、P2-06/07/09（R6.4）、P2-01/02/04（R6.5）、R6.6 批量
- 分布式锁 / 多实例并发防护
- querySpace 维度 upsert 语义（R4.2 已声明 follow-up）

## Execution Plan

### Phase 1 - upsertExternalTable NULL-schema 重复行防护

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java` + `TestNopMetaTableMultiSchemaUpsert.java`（或新建并发测试文件）

- Item Types: `Decision | Fix | Proof`

- [x] **防护方案裁定（Decision，组合裁定而非三选一）**：AR-07 的 NULL-schema 场景决定**任一独立选项均不充分**，必须按组合裁定：
  - **路径 A（DB 原子 upsert）单独不充分**：NULL-schema 下 `metaSchema` 为 NULL，UK 不参与冲突判定（NULL≠NULL），DB 原生 upsert（ON DUPLICATE KEY/MERGE）对 NULL 键同样无法收敛双插（方言差异 + 平台无抽象，已核实）——A 只能作为非 NULL-schema 场景的加固，不能独立收口 AR-07。**不选 A 作为主方案**（成本高收益低），执行时可记录为 watch-only 加固候选。
  - **路径 B（空串占位）必须承接 R4.2 D1 陷阱分析**：R4.2 已裁定并文档化 (1) GraphQL 可观测变更（主流值 NULL→哨兵）、(2) 全量迁移、(3) Oracle `''`≡NULL（INSERT ORA-01400 / SET 静默 no-op / ALTER ORA-02296）三类陷阱——本 plan 若选 B 必须逐条回应：Oracle 方言升级 SQL 变体、INSERT 运行时语义、读路径 5 处消费方回归（枚举见 Baseline）、迁移范围（entity/SQL 行 + createSqlTable 写路径同步）。**B 的落库语义改变属公开契约变更，不允许"No owner-doc update required"默认成立**——需显式声明变更面并同步 owner doc（R4.2 D1 裁定原文要求）。
  - **路径 C（应用层并发防护）必须覆盖 flush**：`syncExternalTables` 的 flush 在 `upsertExternalTable` 之外（:186-187）——锁若只包住 upsertExternalTable 则 T1 行未 flush、T2 同键 find 查不到，竞态保留。**锁必须跨 upsert+flush**（锁放 `syncExternalTables` 层按 `(metaModuleId, tableName, normalizedSchema)` 键持锁，覆盖 flush；或锁内 flush）。单实例 baseline（R4.3 已裁定）下 per-key 锁 + 重试闭环可收口。
  - **推荐组合（执行时可调整但需记录理由）**：路径 C（per-key 锁跨 flush）+ 失败路径显式错误/重试；路径 B 作为备选（须先完成 R4.2 D1 陷阱回应 + 读路径回归）。**回退裁定**：选定路径在端到端验证失败时，按"记录失败原因 → 切换 B/C 组合"流程，不得以削弱验证（如改顺序双插）代替修复。
  - 裁定约束：不允许"维持现状（无任何防护）"的模糊态；裁定结果写入本 plan + arm-index §P2；若涉及 ORM 模型列/UK 变更，按 Protected Area plan-first 声明（本 plan 即裁决载体）+ model-first 再生成 + 三方言 DDL 同步（沿 R3.19/R4.2/R4.3 先例）
  - **执行期裁定调整（2026-08-06，路径 C → C'）**：先写判别性测试（未修复代码 20/20 轮复现 errors=0 + 2 行 NULL-schema 静默重复）后，按路径 C 原样（锁覆盖 find→insert→flush，不跨 commit）实现发现**竞态保留**——每线程 mutation 的整体事务由框架在 BizModel 返回后提交（commit 在锁外），后到线程在独立会话（READ_COMMITTED）的 find 无法看见先到线程未提交的行，仍会双插。故裁定调整为 **路径 C'：per-key 锁 + 每表 `REQUIRES_NEW` 独立事务提交（锁跨 find→insert→flush→commit）**——锁内独立事务的 commit 使后到线程 find 可见先到行并收敛为 update（并发失败方不报错、不追加、不静默跳过）；锁 key 按 (metaModuleId, tableName, normalizedSchema)。B 路径不选（无哨兵落库 → 无读路径 blast radius、无 Oracle 陷阱、无迁移）；`No owner-doc update required` 成立（metaSchema 存储语义不变）。
- [x] 按裁定落地（Fix）：`upsertExternalTable` 防护实现（路径 C'：`EXTERNAL_TABLE_UPSERT_LOCKS` per-key 锁 + `upsertExternalTableGuarded` 内 `REQUIRES_NEW` 事务包 upsert + flush），错误路径显式抛错（catch → errors[] 收集，不静默吞）
- [x] **回归测试（Fix，判别性验证，无替代）**：**并发双插测试为 AR-07 修复的唯一判别性验证**——新建 `TestNopMetaTableConcurrentNullSchemaUpsert`（20 轮 × 2 线程，CountDownLatch 对齐起点，GraphQL 公开入口 `NopMetaDataSource__syncExternalTables`）：每轮独立物理库 + 独立表名；预创建 `nop/meta-external` 模块隔离 module UK 竞态；TABLE_SCHEM 置 null（JDK 动态代理仅覆写 getTables 结果集该列，其余委托真实连接）使真实 upsert 走 NULL-schema 分支。**测试先行证据**：未修复代码上 20/20 轮均 errors=0 + 2 行（schemas=[null, null]）静默重复 → 测试失败；修复后 20/20 轮 1 行 0 错误 → 测试通过（判别性成立，非 vacuous；顺序双插不作为验证项）。既有两 schema 共存 + 单 schema update 回归用例不回归（TestNopMetaTableMultiSchemaUpsert 2/2 绿）
- [x] 若路径 B：存量 NULL-schema 行迁移处置（升级 SQL 三方言含 Oracle 变体或 Java 一次性归一化）+ 读路径 5 处消费方回归测试（profile/catalog/quality 链）+ createSqlTable 写路径同步裁定，写入 plan 执行项 + deploy/sql 或迁移文档 —— **不适用（路径 C'，不落哨兵值，无迁移面）**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 防护方案裁定已记录（组合裁定 + 回退裁定，无"维持现状"状态）——路径 C' 裁定 + 调整理由记录（见 Item 1）
- [x] **端到端验证**：syncExternalTables 入口 → upsertExternalTable → DB 落盘，NULL-schema **并发**双插场景仅 1 行（判别性测试断言 20/20 轮 1 行 0 错误；顺序双插不作为验证项）
- [x] **接线验证**：防护机制（per-key 锁 + REQUIRES_NEW commit）在 `upsertExternalTable` 运行时确实生效（测试经 GraphQL 公开入口断言，非直接测工具；未修复代码 20/20 轮失败 → 修复后 20/20 轮通过）
- [x] **无静默跳过**：无 catch-and-continue 引入；失败路径显式抛错（errors[] 收集，与既有 per-table 错误隔离语义一致）；并发失败方收敛为 update（不报错不追加）
- [x] 既有 `TestNopMetaTableMultiSchemaUpsert` 全部用例不回归；`./mvnw test -pl nop-metadata -am -T 1C` 相关测试类全绿（909/0 全绿）
- [x] 若涉及模型/DDL 变更：`./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` 再生成通过 + deploy/sql 三方言（含 oracle）/upgrade SQL 同步（沿先例）—— **不适用（路径 C' 无 ORM/DDL 变更）**
- [x] 若走路径 B：`No owner-doc update required` **不成立**——需显式声明 metaSchema 存储语义变更面（GraphQL 可观测）并同步 owner doc（R4.2 D1 要求）；路径 C 下 `No owner-doc update required` 成立 —— **路径 C'：成立（metaSchema 存储语义不变）**
- [x] `ai-dev/logs/` 对应日期条目已更新（2026-08-06 条目）

### Phase 2 - createSqlTable 重复守卫过滤

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java` + `TestNopMetaTableBizModel.java`（或 TestNopMetaTableMultiSchemaUpsert）

- Item Types: `Fix | Proof`

- [x] 守卫查询（:166-169）补过滤：`eq(isDelta, 0)` + `metaSchema IS NULL`（执行时裁定用 `FilterBeans.isNull`，语义与 `FilterBeans.eq(prop, null)` 等价且 SQL 表达无歧义）——**逐字对齐 4 列 UK `(metaModuleId, tableName, isDelta=0, metaSchema=NULL)` 语义**，不是"null 或空串归一"（`''` 与 NULL 在 UK 中是不同键；空串归一会误伤路径 B 产生的 `""` 外部行，AR-08 误报面复发）。注释同步（含不归一理由）
- [x] **回归测试（Fix）**：补——(a) 同模块同表名 isDelta=1（delta 行）先存在 → createSqlTable 成功（`testCreateSqlTableDeltaRowDoesNotBlockCreate`，不再误报 `ERR_SQL_VIEW_TABLE_EXISTS`）；(b) 同模块同表名异 schema 行先存在 → createSqlTable 成功（`testCreateSqlTableOtherSchemaRowDoesNotBlockCreate`）；(c) 真重复（同 isDelta=0 + null-schema）→ 仍抛 `ERR_SQL_VIEW_TABLE_EXISTS`（`testCreateSqlTableTrueDuplicateStillFailsFast`，fail-fast 保持；与既有 `TestNopMetaTableMultiSchemaUpsert.testCreateSqlTableDuplicateFailsFast` 双钉）；(d) 若 Phase 1 走路径 B：`""` 外部行与 createSqlTable 交互测试——**不适用（Phase 1 裁定路径 C'，无 `""` 哨兵落库）**；(e) 既有正/负路径用例（:343-406）不回归（26/26 绿）
- [x] 接线验证：测试经 GraphQL `NopMetaTable__createSqlTable` 公开入口断言（沿既有测试模式），不直接测守卫私有逻辑

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**：createSqlTable 入口 → 守卫查询 → 成功创建/显式失败，对 delta 行/异 schema 行/真重复三类场景断言成立（Phase 1 走 C' → `""` 外部行交互断言不适用，已按 Phase 1 产物衔接）
- [x] **无静默跳过**：守卫过滤后真重复仍 fail-fast（无放行重复行）；无 catch-and-continue 引入
- [x] 既有 createSqlTable 用例（:343-406）不回归；`./mvnw test -pl nop-metadata -am -T 1C` 相关测试类全绿（909/0 全绿）
- [x] `No owner-doc update required`（docs-for-ai 无 createSqlTable 守卫细节章节；行为与 4 列 UK 语义对齐为修正方向）
- [x] `ai-dev/logs/` 对应日期条目已更新（2026-08-06 条目）

### Phase 3 - 收口（arm-index 终态 + closure audit）

Status: completed
Targets: `ai-dev/audits/arm-index-nop-metadata.md` + `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`

- Item Types: `Fix | Proof`

- [x] arm-index §P2 对应行（AR-07/AR-08）终态 = fixed + 本 plan 引用 + 修复摘要 + 测试证据
- [x] roadmap MR6 R6.3 行 → done（注明 plan 引用 + 测试计数 909/0）
- [x] 独立子 agent closure audit（fresh session）逐项核对 Phase Exit Criteria + Closure Gates，证据写入本 plan Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（涉及 arm-index/roadmap 变更后）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] arm-index + roadmap 终态一致可追溯（AR-07/AR-08 两行 fixed）
- [x] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）—— 909/0/0/0（service 908 + web 1 口径，`-pl nop-metadata/nop-metadata-service -am`）
- [x] 无静默降级：两项正确性 finding 为 fixed，无 live defect 被降级
- [x] `ai-dev/logs/` 对应日期条目已更新（2026-08-06 条目）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] AR-07：upsertExternalTable NULL-schema 重复行防线恢复（路径 C' 落地：per-key 锁 + REQUIRES_NEW 独立事务提交），并发双插不产生静默重复行（20/20 轮 1 行 0 错误）
- [x] AR-08：createSqlTable 守卫查询与 4 列 UK 语义一致（isDelta/schema 过滤），真重复仍 fail-fast
- [x] 必要 focused verification 已完成（双侧回归测试 + 既有用例不回归——26/2/1 全绿 + 909/0 全量）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred But Adjudicated 段仅 watch-only 残余 + out-of-scope 项）
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required（路径 C'：成立，metaSchema 存储语义不变；arm-index + roadmap 已同步）
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session `ses_02d29d01bffeDAKGsxPLFBlkU5`，PASS，见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）防护机制在 `upsertExternalTable`/`createSqlTable` 运行时确实生效（syncExternalTables:193 → upsertExternalTableGuarded:508-515 调用链 + 守卫 4 filter :172-176 + 判别性测试公开入口断言），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-metadata -am -T 1C` —— `./mvnw test -pl nop-metadata/nop-metadata-service -am -T 1C` → 909 tests / 0 failures / 0 errors / 0 skipped（BUILD SUCCESS；全量 `-pl nop-metadata -am` 的预存在 rocksdb 性能 flaky 单跑复绿，非本 plan 引入）
- [x] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时，0 发现）

## Deferred But Adjudicated

### 多实例并发残余面（upsert 竞态在多节点部署）

- Classification: `watch-only residual`
- Why Not Blocking Closure: R4.3 已裁定单实例 baseline（分布式锁不做）；本 plan 路径 C（per-key 锁跨 flush）仅覆盖单实例，多实例残余面与 R4.3 裁定一致，非当前 supported baseline 活跃缺陷路径；路径 B 下由 UK 覆盖该面
- Successor Required: `no`
- Successor Path: —

### querySpace 维度 upsert 语义（跨数据源同名同 schema 覆盖）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: R4.2 注释（`NopMetaDataSourceBizModel.java:420-421`）已声明 follow-up：应用层 upsert 仍幂等，迁移需评估跨数据源覆盖语义破坏面；非 AR-07 范围
- Successor Required: `no`
- Successor Path: —

### 路径 A（DB 原子 upsert）作为非 NULL-schema 场景加固

- Classification: `watch-only residual`（若 Phase 1 不选 A）
- Why Not Blocking Closure: NULL-schema 场景下 A 单独不收敛（NULL 不参与 UK 冲突），主方案为 B 或 C；A 对非 NULL-schema 场景的加固收益低（B/C 已覆盖），不作为独立修复项
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- createSqlTable 守卫过滤（Phase 2）与 Phase 1 裁定结果的衔接测试（路径 B 时的 `""` 外部行交互）——由执行时 Phase 1 产物驱动，属本 plan 内工作，不另立计划
- 工作树提交由 mission 流程/用户决定（本 plan 执行不代提交）

## Closure

Status Note: AR-07/AR-08 两项正确性 finding 已修复并钉死（路径 C' per-key 锁 + REQUIRES_NEW 提交；守卫 4 filter 对齐 UK）；无静默降级、无契约漂移；Owner doc 同步（arm-index + roadmap 终态）；构建全绿。可以关闭。
Completed: 2026-08-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent，fresh session `ses_02d29d01bffeDAKGsxPLFBlkU5`（未复用执行 session，纯只读核查，未改任何文件）
- Evidence: **PASS**——逐项 Exit Criterion / Closure Gate live 核实：
  - AR-07：`NopMetaDataSourceBizModel.java` `upsertExternalTableGuarded`（:508）per-key 锁（`EXTERNAL_TABLE_UPSERT_LOCKS` :501，键 :503-505）+ `synchronized(lock)`（:511）+ `REQUIRES_NEW` 事务包 upsert+flush（:513-515）；`syncExternalTables` 循环接线 guarded 版本（:193），errors 经既有 try/catch 收集（:192-202，无 catch-and-continue）；判别性测试 `TestNopMetaTableConcurrentNullSchemaUpsert`（2 线程 / 20 轮 / latch 对齐 / GraphQL 公开入口 / 预建模块 / TABLE_SCHEM 置 null），surefire 1/0
  - AR-08：`NopMetaTableBizModel.java` 守卫 4 filter（:172-176：eq metaModuleId + eq tableName + eq isDelta 0 + isNull metaSchema），真重复仍 fail-fast（:177-181）；`TestNopMetaTableBizModel` 26/0（+3 新测试）
  - 既有回归：`TestNopMetaTableMultiSchemaUpsert` 2/0 不回归
  - arm-index §P2 AR-07/AR-08 → fixed（:25-26）+ MR6 R6.3 收口注（:14）；roadmap R6.3 → done（:225）+ header v20（:3）
  - 工具退出码：`check-plan-checklist.mjs <plan> --strict` 0（相对路径调用）、`scan-hollow-implementations.mjs --module nop-metadata --severity high` 0（0 发现）、`check-doc-links.mjs --strict` 0 errors（12 warnings 全为无关历史文件）
  - 测试全量：`./mvnw test -pl nop-metadata/nop-metadata-service -am -T 1C` → 909 tests / 0 failures / 0 errors / 0 skipped（BUILD SUCCESS）；全量 `-pl nop-metadata -am` 预存在 rocksdb 性能 flaky（`TestRocksDBIncrementalRestoreAndBenchmark` ratio=1.0499）单跑复绿 2/0，非本 plan 引入
  - Anti-Hollow：调用链 syncExternalTables:193 → upsertExternalTableGuarded:508-515（锁 → REQUIRES_NEW txn → upsertExternalTable find/insert-update :441-477 → flush）在活路径；判别性测试（未修复代码 20/20 轮 2 行静默重复 → 修复后 20/20 轮 1 行 0 错误）反证防护机制运行时生效

Follow-up:

- no remaining plan-owned work（Phase 1 路径 B `""` 交互测试按裁定不适用；多实例并发残余面 / querySpace upsert 语义 / 路径 A 加固均已在 Deferred But Adjudicated 段登记为 watch-only residual / out-of-scope，Successor Required: no）
- 工作树提交由 mission 流程/用户决定（本 plan 执行不代提交）
