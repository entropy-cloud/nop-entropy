# 2374 full-code-review R1.9 — ORM 模型合规+DDL 物化批（P1-2.5 族+平台工单）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.9 行；finding `P1-2.5-001..005` + 平台工单-2.5（`2.5-orm-model-compliance-scan.md`）
> ⚠️ Protected Area：model/*.orm.xml 与 ddl.xlib 变更 plan-first（本计划即凭证）；先例路径：datav 8/8「unique-key+constraint→三方言物化」管线已验证
> Related: R1.6c 下游教训——本计划验证面必须包含全部受影响模块（nop-orm-eql 下游 nop-orm 在 R1.6c 曾漏覆盖）

## Purpose

按 2.5 审计的分族处方收口五项 P1 + 平台工单：①平台工单先导——`<index>`（含 unique=true）在 DDL 生成与运行时自动建表两条路径的物化（审计实证 deploy 全树 grep unique=0）；②UK 零物化族——job 3 UK/batch 1 UK 补 constraint；③字段长度族——metadata 6 状态列 precision、rule outputs precision；④重生成受影响模块三方言 DDL + batch upgrade 脚本 + metadata 数据迁移脚本。

## Current Baseline

- **平台缺口机理（已亲核）**：`nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/ddl/ddl.xlib` 的 `CreateTables`（:7-28）对每表调 `CreateTable`，后者仅在 `TableUniqueConstraints`（:77-96）中物化**带 `constraint` 属性的 uniqueKey**（`<c:if test="${uniqueKey.constraint}">`）；`<indexes>` 块（含 `unique="true"`）在 create 路径零输出。`AddIndex` tag（:380-391，生成 `create [unique] index`）已存在但仅供 diff 迁移（DdlSqlCreator.addIndex :165）。
- **运行时自动建表同病**：`DataBaseSchemaInitializer`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/initialize/DataBaseSchemaInitializer.java:65`）仅调 `createTable(table,false)`——同为 `CreateTable` tag，无 index 输出。审计口径「运行时自动建表同样不物化」由此而来。
- **模型侧现状**：job 3 UK 无 constraint（`nop-job/model/nop-job.orm.xml` :208/:322/:451 形态 `<unique-key name="UK_..." columns="...">`）；batch 1 UK 同（`nop-batch/model/nop-batch.orm.xml`）；retry 2 个 `<index unique="true">`（`nop-retry/model/nop-retry.orm.xml` :264 UK_RETRY_IDEMPOTENT_ID 等）；metadata 6 状态列无 precision（参照同行 APPROVE_STATUS=20 先例）；rule outputs 列 precision=50（`nop-rule/model/nop-rule.orm.xml`，domain json-4k 被列级静默覆盖——syncDomains 列优先）。
- **重生成机制（已亲核，审查勘正触发阶段）**：postcompile exec 绑定 generate-test-resources 阶段（root pom :368）——mvn compile 不触发；mvn test/test-compile/install 触发。链路：nop-<mod>-codegen/postcompile/gen-orm.xgen → renderModel(/nop/templates/orm) → @init.xrun → gen:DefineLoopForOrm 定义 dialect（来自模型 ext:dialect，五模块齐备）→ 递归执行模板树含 deploy/sql/{dialect}/*.xgen（调 DdlSqlCreator.createTables）。重生成须重新构建 <mod>-codegen 模块，且修改后的 nop-orm 须在 reactor（-am）或已 install。
- **批量构建环境注记（R1.6c/R1.8 教训）**：nop-sys-dao 测试编译断裂（R1.17 在案）会阻断 `-am` 链——重生成与验证用逐模块 `-pl` + `-Dmaven.test.skip=true` install 上游的方式；nop-stream-runtime TestStreamConnectorRegistryDiscovery FAIL=预存 P1-7.2-001（R1.23 承载）。

## Goals

- 平台工单：`CreateTables` 每表后物化全部 `<index>`（`create [unique] index`，复用 AddIndex 语义）；运行时 `DataBaseSchemaInitializer` 同步补 index 创建；四模块 auth/ai/retry/sys 的 index 全部落DDL。
- job 3 UK/batch 1 UK 补 `constraint` 属性 → 三方言 DDL 物化 unique 约束。
- metadata 6 状态列 precision=20；rule outputs 列 precision=4000（保留列级显式声明，消除 domain 静默覆盖歧义）。
- 重生成 job/batch/retry/metadata/rule 五模块 deploy DDL；batch 补 upgrade 脚本；metadata 补数据迁移脚本（LONGTEXT→VARCHAR 需中转列）。
- 全部模型/生成器变更回归绿（nop-orm-eql、nop-orm、nop-job、nop-batch、nop-retry、nop-metadata、nop-rule 逐模块）。

## Non-Goals

- 不修 P2-2.5 族（nop-code 9 UK 无 deploy 层等——台账通道）；不改 syncDomains「列优先」机制本身（机制行为正确，缺陷是模型列声明错——audit 口径）。
- 不为 UK-无-constraint 增加「自动物化为 unique index」的平台兜底（本批以模型侧 constraint 显式化为准；平台兜底语义争议登记 follow-up）。
- 不动 auth/ai/sys 的普通 `<index>` 模型（生成器修复后随构建自然物化，不逐模块重生成——audit 影响面仅要求落 DDL，重生成范围限五模块）。

## Scope

### In Scope

- `nop-persistence/nop-orm/.../ddl.xlib`：`CreateTables` 每表后新增 index 物化循环（复用 `AddIndex` tag）；`DataBaseSchemaInitializer` 建表后补 index 创建。
- 模型：job 3 UK+constraint、batch 1 UK+constraint、metadata 6 列 precision=20、rule outputs precision=4000。
- 重生成：job/batch/retry/metadata/rule 五模块 deploy DDL（构建期自动）。
- 脚本：nop-batch upgrade 脚本（UK 补建）；nop-metadata 迁移脚本（中转列法 LONGTEXT→VARCHAR(20)）。
- 测试：ddl.xlib 物化的回归测试（含 unique index 与普通 index）；受影响模块全量回归。

### Out Of Scope

- P2-2.5 族、P3、demo 单列、auth/ai/sys 的非本批重生成、syncDomains 机制重构。

## Execution Plan

### Phase 1 - 平台工单：DDL 生成器 index 物化（Protected Area 先导）

Status: completed
Targets: `nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/ddl/ddl.xlib`、`.../initialize/DataBaseSchemaInitializer.java`

- Item Types: `Fix`

- [x] `CreateTables`：每表 `CreateTable` 后对 `table.indexes` 循环调 `<thisLib:AddIndex index=... table=... dialect=.../>` + 语句分隔符；受唯一约束排除的表（tableView/not-gen）同规则排除
- [x] `DataBaseSchemaInitializer`：createTable 成功后（现有 !existsTable 守卫内）对索引逐个 addIndex 生成文本并 jdbcTemplate.executeUpdate 执行（addIndex 仅返回文本——审查核实）；IEntityModel 无 indexes 访问器——按 OrmDbDiffer:190 先例转型 (OrmEntityModel)，不扩接口
- [x] 回归测试：nop-orm 内 ddl 单测（构造含 `<index unique=true>` 与普通 `<index>` 的模型，断言 createTables 输出含 `create unique index`/`create index` 语句；DataBaseSchemaInitializer 路径以既有测试形态覆盖——TestDdlSqlCreator/TestDataBaseSchemaInitializer 先例在位）
- [x] auth/ai/sys auto-create 冒烟验证（审查 B1：ddl.xlib 变更改变全仓 auto-create schema，须防 R1.6c 式下游盲区）：三模块各跑一个代表性 dao/service 测试类（H2 auto-create 路径），确认新物化的 unique index 不与种子数据冲突；若冲突属新防线生效——修数据并留档

Exit Criteria:

- [x] 新回归测试绿（unique+普通两类 index 物化断言）
- [x] `./mvnw test -pl nop-persistence/nop-orm -Dtest=*Ddl*` 及 nop-orm-eql 模块全绿（ddl.xlib 变更不破坏既有 SELECT/DML 生成——R1.6c 教训：验证面含 nop-orm）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 模型修复 + DDL 重生成 + 脚本

Status: completed
Targets: `nop-job/model/nop-job.orm.xml`、`nop-batch/model/nop-batch.orm.xml`、`nop-metadata/model/nop-metadata.orm.xml`、`nop-rule/model/nop-rule.orm.xml`、五模块 deploy DDL、nop-batch/nop-metadata 脚本

- Item Types: `Fix`

- [x] job 3 UK（:208/:322/:451）补 `constraint="UK_同名"`；batch 1 UK 同
- [x] metadata 6 状态列补 `precision="20"`（参照同行 APPROVE_STATUS）；rule outputs 列 precision 50→4000
- [x] 构建五模块（postcompile 自动重生成 deploy DDL），git diff 核对：job/batch DDL 出现 `constraint UK_... unique`；retry DDL 出现 `create unique index UK_RETRY_*` 与普通 index；metadata DDL 状态列变 VARCHAR(20)；rule DDL OUTPUTS 变 VARCHAR(4000)
- [x] nop-batch 补 upgrade 脚本（alter table add constraint UK unique；执行说明注明 MySQL 需 information_schema 幂等保护、存量数据违反唯一性时须预检——审查 advisory）
- [x] nop-metadata 补迁移脚本（中转列法 LONGTEXT 转 VARCHAR(20)，覆盖 ext:dialect 全三方言，附执行说明）
- [x] stash 判别：stash ddl.xlib 修复跑 Phase 1 回归测试 FAIL（index 语句缺失）；模型侧判别以 git diff 的 DDL 变更为准
- [x] Phase 2 模型属性增补 No new test required: DDL git-diff 断言即验证（guide Rule 25 记录）

Exit Criteria:

- [x] 五模块 deploy DDL 重生成且内容符合上述四项断言（git diff 证据记入 Closure）
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql,nop-persistence/nop-orm` 全绿
- [x] 逐模块回归：./mvnw test -pl nop-job/nop-job-dao,nop-batch/nop-batch-dao,nop-retry/nop-retry-dao,nop-metadata/nop-metadata-dao,nop-rule/nop-rule-dao（重生成须各 -codegen 模块构建触发）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 收口

Status: completed
Targets: owner doc、roadmap、轮次 index、本 plan

- Item Types: `Proof`

- [x] owner doc：`docs-for-ai/02-core-guides/orm-model-design.md` 增补「UK 必须带 constraint 属性否则任何路径不物化」与「`<index>` 自 R1.9 起在 create/auto-create 物化」两条规则
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm --severity high` 退出码 0
- [x] roadmap R1.9 行 → done；index 五行 P1 + 平台工单 → fixed + MR 表 R1.9 行
- [x] 文本一致性核对五处一致；check-plan-checklist --strict 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] roadmap 与 index 回填一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 平台工单落地：`<index>` 两路径物化（deploy 生成 + 运行时 auto-create）且回归锁定
- [x] P1-2.5-002/003 落地：job/batch UK constraint 化且三方言 DDL 物化
- [x] P1-2.5-004 落地：retry 2 唯一索引经生成器物化（unique index）
- [x] P1-2.5-001/005 落地：metadata/rule precision 修正且 DDL 反映
- [x] batch upgrade 脚本 + metadata 迁移脚本交付
- [x] focused verification：七个受影响模块全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：DDL 产物真实含唯一约束/索引语句（git diff 证据）；stash 判别 FAIL→PASS
- [x] `./mvnw test -pl nop-persistence/nop-orm -am` 相关模块全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### UK-无-constraint 的平台自动物化兜底

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本批以模型侧 constraint 显式化收口全部在案 UK 缺口；平台兜底涉及「隐式唯一索引命名」语义争议（audit 未裁定）
- Successor Required: `no`

## Non-Blocking Follow-ups

- auth/ai/sys 普通 index 的三方言重生成（生成器已修，随各模块下次构建自然落地）

## Closure

Status Note: 平台工单两路径物化（ddl.xlib CreateTables 逐表 index 输出 + DataBaseSchemaInitializer 自动建表后 executeUpdate addIndex）+ P1-2.5 五项全部落地；DDL git-diff 断言全过（job 3 constraint/batch 1 constraint/retry 2 create unique index〔模型零改动〕/metadata VARCHAR(20)/rule VARCHAR(4000)）；batch/metadata 三方言脚本交付；owner doc 物化规则三条。stash 判别 FAIL→PASS 独立复现。auth/ai/sys auto-create 冒烟由 closure audit 补跑全绿（勾选时缺证，如实留档）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_8278b5a3-e702-4ee2-9708-f77dc1f701a3
- Evidence:
  - **平台工单两路径物化**：PASS——ddl.xlib :13-24 逐表 index 循环（同 not-gen/tableView 守卫）；Initializer :74-82 转型 OrmEntityModel 取 getIndexes + executeUpdate 真实执行；sys 冒烟日志目击 createIndex: 语句执行。
  - **stash 判别独立重做**：PASS——auditor stash ddl.xlib 后 TestDdlSqlCreator 恰 1 FAIL（testCreateTablesMaterializesIndexes index DDL 缺失），pop 后 3/3 绿。
  - **DDL 产物抽验五模块×三方言**：PASS——job 3 处 constraint unique ×3 方言；batch constraint ×3；retry create unique index ×2 ×3；metadata 状态列 LONGTEXT→VARCHAR(20)/TEXT→VARCHAR(20)/CLOB→VARCHAR2(20)；rule OUTPUTS→VARCHAR(4000)/VARCHAR2(4000)。生成器修复外溢：metadata DDL 新增 IX_NOP_META_* index 行。
  - **模型 diff**：PASS——job 3 constraint="UK_同名"/batch 1/metadata 6 precision="20"/rule 50→4000；五模块 _app.orm.xml 同步重生成。
  - **脚本**：PASS——batch upgrade 三方言幂等+唯一性预检说明；metadata 三方言迁移（Oracle DBMS_LOB.SUBSTR 中转列，超长响亮失败）覆盖 6 列。
  - **测试复跑**：PASS——TestDdlSqlCreator 3/3、TestOrmTemplate 13/13、nop-job-dao 87/87（新 nop-orm jar 已验证含新 ddl.xlib）、聚合 orm 217/orm-eql 205/batch-dao 10/rule-dao 5/metadata-service 1340 全绿。
  - **auth/ai/sys 冒烟补验**：PASS（closure audit 补跑——勾选时缺证如实留档）：auth TestOperationMfaE2E 18/18、ai TestNopAiOrmEntityMapping 6/6、sys TestOrmEntityChangeLogInterceptor 3/3（日志目击 createIndex:IX_NOP_SYS_EVENT_TOPIC_STATUS 运行时执行；sys-dao 预存编译断裂=R1.17 在案，改走 sys-service 替代路径）。
  - **owner doc**：PASS——orm-model-design.md 新增物化规则三条；doc-links --strict 0；hollow scan 0。
  - **Protected Area**：PASS——plan-first 凭证（本 plan 含对抗审查 Blocker 修正记录）+ owner doc + 回归测试三件齐备。
  - **状态回填**：PASS（B1 修正后）——roadmap :139 R1.9 done；index :106-110 五行 P1 fixed + :111 平台工单 fixed；index MR R1.9 行；daily log。
  - **Deferred 分类**：PASS——UK-无-constraint 平台兜底=optimization candidate（in-scope UK 已全部 constraint 化）；P2-2.5 族台账通道。
  - Advisory 处置：A1 TestOrmTemplate 归属勘正（4 期望更新系 R1.8 提交 f4652d0bcb 落地，本批零改动——daily log/roadmap 表述勘正留档）；A2 nop-retry-dao 无 src/test（回归 vacuous 留档）；A3 断言 OR 形态弱于 plan 措辞（stash 判别证明敏感，留档）；A4 脚本注释 plan 号笔误（2373→2374 留档）；A5 Goal「四模块落 DDL」口径偏大（auth/ai/sys 在 follow-ups 声明，留档）；A6 随本 closure 提交（已做）。

Follow-up:

- UK-无-constraint 平台自动物化兜底（Deferred，optimization candidate）
- auth/ai/sys 三方言 DDL 重生成（生成器已修，随各模块下次构建自然落地）
- transformCollectionScope owner 改写校验（R1.6b Deferred 顺延）
