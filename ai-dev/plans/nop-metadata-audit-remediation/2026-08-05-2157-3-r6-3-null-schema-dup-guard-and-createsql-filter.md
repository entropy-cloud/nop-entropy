# R6-3 NULL-schema 重复行防护 + createSqlTable 重复守卫过滤（AR-07/AR-08）

> Plan Status: active
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

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java` + `TestNopMetaTableMultiSchemaUpsert.java`（或新建并发测试文件）

- Item Types: `Decision | Fix | Proof`

- [ ] **防护方案裁定（Decision，组合裁定而非三选一）**：AR-07 的 NULL-schema 场景决定**任一独立选项均不充分**，必须按组合裁定：
  - **路径 A（DB 原子 upsert）单独不充分**：NULL-schema 下 `metaSchema` 为 NULL，UK 不参与冲突判定（NULL≠NULL），DB 原生 upsert（ON DUPLICATE KEY/MERGE）对 NULL 键同样无法收敛双插（方言差异 + 平台无抽象，已核实）——A 只能作为非 NULL-schema 场景的加固，不能独立收口 AR-07。**不选 A 作为主方案**（成本高收益低），执行时可记录为 watch-only 加固候选。
  - **路径 B（空串占位）必须承接 R4.2 D1 陷阱分析**：R4.2 已裁定并文档化 (1) GraphQL 可观测变更（主流值 NULL→哨兵）、(2) 全量迁移、(3) Oracle `''`≡NULL（INSERT ORA-01400 / SET 静默 no-op / ALTER ORA-02296）三类陷阱——本 plan 若选 B 必须逐条回应：Oracle 方言升级 SQL 变体、INSERT 运行时语义、读路径 5 处消费方回归（枚举见 Baseline）、迁移范围（entity/SQL 行 + createSqlTable 写路径同步）。**B 的落库语义改变属公开契约变更，不允许"No owner-doc update required"默认成立**——需显式声明变更面并同步 owner doc（R4.2 D1 裁定原文要求）。
  - **路径 C（应用层并发防护）必须覆盖 flush**：`syncExternalTables` 的 flush 在 `upsertExternalTable` 之外（:186-187）——锁若只包住 upsertExternalTable 则 T1 行未 flush、T2 同键 find 查不到，竞态保留。**锁必须跨 upsert+flush**（锁放 `syncExternalTables` 层按 `(metaModuleId, tableName, normalizedSchema)` 键持锁，覆盖 flush；或锁内 flush）。单实例 baseline（R4.3 已裁定）下 per-key 锁 + 重试闭环可收口。
  - **推荐组合（执行时可调整但需记录理由）**：路径 C（per-key 锁跨 flush）+ 失败路径显式错误/重试；路径 B 作为备选（须先完成 R4.2 D1 陷阱回应 + 读路径回归）。**回退裁定**：选定路径在端到端验证失败时，按"记录失败原因 → 切换 B/C 组合"流程，不得以削弱验证（如改顺序双插）代替修复。
  - 裁定约束：不允许"维持现状（无任何防护）"的模糊态；裁定结果写入本 plan + arm-index §P2；若涉及 ORM 模型列/UK 变更，按 Protected Area plan-first 声明（本 plan 即裁决载体）+ model-first 再生成 + 三方言 DDL 同步（沿 R3.19/R4.2/R4.3 先例）
- [ ] 按裁定落地（Fix）：`upsertExternalTable` 防护实现（路径 C：锁跨 flush；或路径 B：空串占位 + 读路径归一 + 迁移），错误路径显式抛错或重试收敛，不静默吞
- [ ] **回归测试（Fix，判别性验证，无替代）**：**并发双插测试为 AR-07 修复的唯一判别性验证**——双线程同时 `syncExternalTables` 同一 NULL-schema 外部表（或直插路径），断言仅 1 行落盘（无重复行）+ 并发失败方按裁定语义可见（错误进 errors[] 或重试收敛为 update）。测试规格：预创建外部表所属 module（`ensureExternalSystemModule` :482-499 本身是 find-then-insert，双线程会竞态 NopMetaModule UK——测试须先建 module 隔离该竞态）；用 CountDownLatch/CyclicBarrier 对齐线程起点；**测试先行**——先写测试，在未修复代码上必须观察到失败（或按 fallback 记录组合证明）；若测试在未修复代码上通过，先排查 session 隔离/共享事务（两线程共享 session 时 flush-before-query 会让 buggy 代码假通过），而非直接认定修复有效。**稳定失败判定标准**：无插桩前提下 find→flush 窗口无法真正确定性放大，现实手段 = latch 对齐起点 + 多轮累积（≥20 轮中 ≥1 轮失败即判 buggy 代码下可稳定失败，记录轮数）；若无法复现，记录"窗口极小 + 断言 1 行 + 代码审查证据"的组合证明。**"顺序双插断言单行"不作为验证项**（未修复代码上顺序两次 sync 也是 1 行，属 vacuous 测试）；既有两 schema 共存 + 单 schema update 回归用例不回归（:73-86，S1/S2 断言 :76-79）
- [ ] 若路径 B：存量 NULL-schema 行迁移处置（升级 SQL 三方言含 Oracle 变体或 Java 一次性归一化）+ 读路径 5 处消费方回归测试（profile/catalog/quality 链）+ createSqlTable 写路径同步裁定，写入 plan 执行项 + deploy/sql 或迁移文档

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 防护方案裁定已记录（组合裁定 + 回退裁定，无"维持现状"状态）
- [ ] **端到端验证**：syncExternalTables 入口 → upsertExternalTable → DB 落盘，NULL-schema **并发**双插场景仅 1 行（判别性测试断言；顺序双插不作为验证项）
- [ ] **接线验证**：防护机制（per-key 锁跨 flush / 空串占位归一）在 `upsertExternalTable` 运行时确实生效（测试经公开入口断言，非直接测工具）
- [ ] **无静默跳过**：无 catch-and-continue 引入；失败路径显式抛错或按裁定记录降级理由（并发失败方 errors[] 可见或重试收敛，与裁定一致）
- [ ] 既有 `TestNopMetaTableMultiSchemaUpsert` 全部用例不回归；`./mvnw test -pl nop-metadata -am -T 1C` 相关测试类全绿
- [ ] 若涉及模型/DDL 变更：`./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` 再生成通过 + deploy/sql 三方言（含 oracle）/upgrade SQL 同步（沿先例）
- [ ] 若走路径 B：`No owner-doc update required` **不成立**——需显式声明 metaSchema 存储语义变更面（GraphQL 可观测）并同步 owner doc（R4.2 D1 要求）；路径 C 下 `No owner-doc update required` 成立
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - createSqlTable 重复守卫过滤

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java` + `TestNopMetaTableBizModel.java`（或 TestNopMetaTableMultiSchemaUpsert）

- Item Types: `Fix | Proof`

- [ ] 守卫查询（:166-169）补过滤：`eq(isDelta, 0)` + `metaSchema IS NULL`（`FilterBeans.eq(prop, null)` 或 `isNull`，执行时按 FilterBeans API 裁定）——**逐字对齐 4 列 UK `(metaModuleId, tableName, isDelta=0, metaSchema=NULL)` 语义**，不是"null 或空串归一"（`''` 与 NULL 在 UK 中是不同键；空串归一会误伤路径 B 产生的 `""` 外部行，AR-08 误报面复发）
- [ ] **回归测试（Fix）**：补——(a) 同模块同表名 isDelta=1（delta 行）先存在 → createSqlTable 成功（不再误报 `ERR_SQL_VIEW_TABLE_EXISTS`）；(b) 同模块同表名异 schema 行先存在 → createSqlTable 成功（不再误报）；(c) 真重复（同 isDelta=0 + null-schema）→ 仍抛 `ERR_SQL_VIEW_TABLE_EXISTS`（fail-fast 保持）；(d) 若 Phase 1 走路径 B：`""` 外部行与 createSqlTable 交互测试（`""` 外部行存在时 createSqlTable 同表名不误报，因守卫只匹配 IS NULL）——**Phase 1 路径 B 裁定结果决定本项是否必须**，执行时按 Phase 1 产物衔接；(e) 既有正/负路径用例（:343-406）不回归
- [ ] 接线验证：测试经 GraphQL `NopMetaTable__createSqlTable` 或 BizModel 公开入口断言（沿既有测试模式），不直接测守卫私有逻辑

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**：createSqlTable 入口 → 守卫查询 → 成功创建/显式失败，对 delta 行/异 schema 行/真重复三类场景断言成立（Phase 1 走 B 时补 `""` 外部行交互断言）
- [ ] **无静默跳过**：守卫过滤后真重复仍 fail-fast（无放行重复行）；无 catch-and-continue 引入
- [ ] 既有 createSqlTable 用例（:343-406）不回归；`./mvnw test -pl nop-metadata -am -T 1C` 相关测试类全绿
- [ ] `No owner-doc update required`（docs-for-ai 无 createSqlTable 守卫细节章节；行为与 4 列 UK 语义对齐为修正方向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 收口（arm-index 终态 + closure audit）

Status: planned
Targets: `ai-dev/audits/arm-index-nop-metadata.md` + `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`

- Item Types: `Fix | Proof`

- [ ] arm-index §P2 对应行（AR-07/AR-08）终态 = fixed + 本 plan 引用 + 修复摘要 + 测试证据
- [ ] roadmap MR6 R6.3 行 → done（注明 plan 引用 + 测试计数）
- [ ] 独立子 agent closure audit（fresh session）逐项核对 Phase Exit Criteria + Closure Gates，证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（涉及 arm-index/roadmap 变更后）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] arm-index + roadmap 终态一致可追溯（AR-07/AR-08 两行 fixed）
- [ ] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）
- [ ] 无静默降级：两项正确性 finding 为 fixed，无 live defect 被降级
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] AR-07：upsertExternalTable NULL-schema 重复行防线恢复（路径 A/B/C 落地），并发双插不产生静默重复行
- [ ] AR-08：createSqlTable 守卫查询与 4 列 UK 语义一致（isDelta/schema 过滤），真重复仍 fail-fast
- [ ] 必要 focused verification 已完成（双侧回归测试 + 既有用例不回归）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）防护机制在 `upsertExternalTable`/`createSqlTable` 运行时确实生效（非仅存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw test -pl nop-metadata -am -T 1C`
- [ ] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）

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

Status Note: 待执行完成后填写（为什么这个 plan 可以关闭）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent，fresh session>>
- Evidence: <<逐条 Exit Criterion / Closure Gate 验证结果 + 工具退出码 + Anti-Hollow 调用链追踪>>

Follow-up:

- <<执行完成后填写；或明确写 no remaining plan-owned work>>
