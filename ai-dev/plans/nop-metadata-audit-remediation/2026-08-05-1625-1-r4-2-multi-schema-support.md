# R4.2 多 schema 支持专项（P2-MA3-03 终局 successor：metaSchema null 语义裁定 → UK 列维度扩展 + 存量迁移 + 写入路径同步）

> Plan Status: active
> Last Reviewed: 2026-08-05
> Draft Review: 3 轮独立子 agent 对抗性审查通过——R1 `ses_02ef3abffffeXeh8YKus2aQEmd`（2 Blocker：Oracle ''=NULL 方言语义 + _add_tenant tenant UK 变体遗漏；5 Major：TestNopMetaUniqueKeysEnforced 列数断言 / createSqlTable 第三写点 / Non-Goal 冲突 / 存量 NULL 主流 / RACE 残余面）；R2 `ses_02ee60d20ffeNAukr5GQ2kNQy6`（2 Major：createSqlTable fail-fast 回归 + 非租户存量升级路径；3 Minor）；R3 `ses_02edf79bbffeZB6N4E6qQ2d9Fb`（0 Blocker，共识达成；2 Major 文本级更正——_add_tenant 实为 xgen 生成产物非手写脚本 + Baseline ORA-30657 残留，已修复；3 Minor 已修复）。全部 Blocker/Major 清零，裁定可执行。
> Mission: nop-metadata-audit-remediation
> Work Item: R4.2（roadmap MR4 段多 schema 支持专项，Deps: R4.1 done）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（R4.2 行 + MR4 终局裁决记录）、`ai-dev/audits/arm-index-nop-metadata.md`（P2-MA3-03 行）、MR4 plan（2026-08-05-1408-1，终局 deferred + Successor: yes → R4.2）
> Related: 执行顺序 `{1}` of 2 — 启动门禁：R4.1 done（roadmap MR4 段行状态）+ 本计划与前序 MR1-MR4/MV/MG plan 无未关依赖；R4.3（2026-08-05-1625-2）独立于本 plan（不同实体），可先后执行

## Purpose

承接 P2-MA3-03 的 MR4 终局 successor（out-of-scope improvement，Successor Required: yes）：**裁定 `NopMetaTable.metaSchema` 的 null 语义**，并将 **schema 维度纳入表唯一性约束**（UK 列维度扩展或 schema 维度列契约变更，二者择一，先裁定后实施），使**多 schema 同名外部表可共存**（当前 `(metaModuleId, tableName, isDelta)` UK 必然冲突）。全程 **model-first**（改 `nop-metadata/model/nop-metadata.orm.xml` → 重新生成 → DDL 三方言再生成），**ORM Protected Area，plan-first——本 plan 即裁决载体**（沿 R3.19 先例）。

## Current Baseline

2026-08-05 live repo 核对（与 MR4 Phase 2 裁决依据一致，执行时以重新实测为准）：

- `nop-metadata/model/nop-metadata.orm.xml:1310-1311`：`NopMetaTable.metaSchema`（`META_SCHEMA VARCHAR(100)`）为**可空列**（无 `mandatory` 属性）
- `nop-metadata/model/nop-metadata.orm.xml:1316-1317`：`UK_NOP_META_TABLE_MODULE_NAME` = `(metaModuleId, tableName, isDelta)`，**不含 metaSchema**；`constraint` 属性已补（R3.19）；全模型**不存在** `UK_NOP_META_EXTERNAL_TABLE_*`——外部表复用 NopMetaTable 的 UK
- 写入路径：`NopMetaDataSourceBizModel.upsertExternalTable`（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:428-468`）——先按 `(metaModuleId, tableName)` 拉候选集，再 **Java 层**按 `normalizeSchemaForMatch`（null/空串/纯空白 → null，:471-476）做 schema 精确匹配，命中 update / 未命中 insert；**多 schema 同名表 insert 必然撞 UK（duplicate key）**（MR4 live 复核：MA7.3/MA3.4 单 schema 部署，当前无实际触发）
- 其他 `NopMetaTable` 写路径（**共 3 个**）：
  - `OrmModelImporter.buildEntityTable`（`nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/dao/model/OrmModelImporter.java:176-185`）——entity 表**不设置 metaSchema**（保持 null）
  - `NopMetaTableBizModel.createSqlTable`（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTableBizModel.java:161-169`）——tableType=SQL 表**不设置 metaSchema**（保持 null）
- DDL 现状（**两份物化，缺一不可**）：
  - 生成产物：`deploy/sql/mysql/_create_nop-metadata.sql:222` `META_SCHEMA VARCHAR(100) NULL`；`:224` `constraint UK_NOP_META_TABLE_MODULE_NAME unique (META_MODULE_ID,TABLE_NAME,IS_DELTA)`（oracle/postgresql 同构，:224）
  - **同为生成产物（xgen 模板）**：`deploy/sql/{mysql,oracle,postgresql}/_add_tenant_nop-metadata.sql:227-228` —— `drop constraint UK_NOP_META_TABLE_MODULE_NAME` 后重建为 **tenant 变体 `(NOP_TENANT_ID, META_MODULE_ID, TABLE_NAME, IS_DELTA)`（不含 metaSchema）**——模板为 `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm/deploy/sql/{dialect}/_add_tenant_{appName}.sql.xgen`（经 `DdlSqlCreator.addTenantIdForTables` + ddl.xlib `AddTenantIdForTables` 从模型 UK 派生，`uniqueKey.constraint` 为发射 gate）；**模型 UK 变更后重新生成会自动产出含 metaSchema 的 tenant 变体——禁止手编，走再生成 + git diff 核对**（Blocker-2；Round-3 更正：非手写脚本）
- DDL 断言测试：`TestNopMetaDdlUniqueKeyEmission`（`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaDdlUniqueKeyEmission.java`）——`testCreateTableEmitsUniqueKeyForAllThreeDialects`（:37-51）断言三方言 UK 发射 + `testDualStorageUniqueKeysIncludeIsDeltaDimension`（:64-76）断言 UK 含 IS_DELTA
- **UK 列数精确断言测试（需同步更新）**：`TestNopMetaUniqueKeysEnforced.testNopMetaTableHasNaturalUniqueKey`（`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaUniqueKeysEnforced.java:77-83`）——helper `hasUniqueKeyWithColumns` 用 `cols.size() != propNames.length`（:118）做**列数精确**匹配，UK 加 metaSchema 后必然失败（Major-1）
- **方言语义事实（裁定关键约束）**：Oracle 中 `''` 即 NULL——「mandatory + 默认 ''」路径在 Oracle 下 `DEFAULT ''` 即 `DEFAULT NULL`，INSERT 显式写 `''` 报 ORA-01400、ALTER 加 NOT NULL 报 ORA-02296、存量迁移 `SET META_SCHEMA=''` 为静默 no-op（Blocker-1）
- 绿色基线：`./mvnw test -pl nop-metadata -T 1C` → **858 tests / 0 failures / 0 errors / 0 skipped**（MV V.1 收口，2026-08-05）；`-am` 全 reactor 存在 3 项 pre-existing 失败（nop-xlang TestFeatureConditionEvaluator / nop-wf RefactorWf / nop-stream-rocksdb benchmark flaky，MR4 plan Phase 3 已文档化归因，与本 plan 无关）
- 工作树：git status 干净（HEAD `2d20b6d1a` MG 收口提交）

## Goals

- 裁定 `metaSchema` null 语义（纳入三方言 unique 语义 + Oracle `''`=NULL 事实），repo-observable（路径 A 保持可空 + UK 扩展，或路径 B 非空哨兵值 + UK 扩展）
- 以 model-first 方式落地 UK 列维度扩展（或 + 列契约变更），三方言 DDL 再生成 + **手写租户脚本（`_add_tenant_*.sql`）同步**，`_gen/` 产物经 `mvn clean install -DskipTests` 重新生成（禁止手编生成产物）
- 同步全部 3 个 `NopMetaTable` 写路径（`upsertExternalTable` / `OrmModelImporter.buildEntityTable` / `NopMetaTableBizModel.createSqlTable`），使 schema 维度写入与 UK 语义一致（存储值归一化规则对齐）
- 存量数据迁移方案落地或明确裁定（NULL 行为主流非例外，路径 A 无需迁移 / 路径 B 迁移 SQL 含重复行处置 + Oracle 适配）
- 行为回归测试 + DDL 断言测试更新（`TestNopMetaDdlUniqueKeyEmission` + `TestNopMetaUniqueKeysEnforced`），独立子 agent closure audit 通过，roadmap R4.2 → done

## Non-Goals

- 不处理跨数据源 querySpace 维度纳入去重键（plan 0852-3 已裁定 follow-up，非本 plan scope）
- 不做多租户 schema 隔离 / 多 schema 权限模型（无相关 finding 支撑）
- **单 schema 部署的现有行为语义保持**（注意：若裁定走「列契约变更」路径，`metaSchema` 存储值 null→非空哨兵值属 GraphQL 可观测变更，必须显式声明该变更面并同步 owner doc——非"零变更"承诺；裁定须正面回答，见 Phase 1）
- 不改平台模板（nop-persistence/nop-orm 等 Protected Area）
- 不执行 R4.3（调度可靠性专项，独立 plan 2026-08-05-1625-2）

## Scope

### In Scope

- `metaSchema` null 语义裁定（Decision，含 live 证据 + 三方言 unique 语义 + Oracle `''`=NULL 约束）
- `nop-metadata/model/nop-metadata.orm.xml` 的 `NopMetaTable` UK 变更（+ 可能的列契约变更），model-first
- `deploy/sql/` 三方言 DDL 再生成（`_create_nop-metadata.sql`）**+ 手写租户脚本同步（`_add_tenant_nop-metadata.sql` 三方言 UK 变体）**
- `_gen/` 生成产物重新生成 + 核对（不手编）
- 3 个 `NopMetaTable` 写路径同步：`NopMetaDataSourceBizModel.upsertExternalTable` + `OrmModelImporter.buildEntityTable` + `NopMetaTableBizModel.createSqlTable`
- 存量 NULL metaSchema 数据迁移方案（SQL 或文档化迁移说明；**NULL 行为主流非例外**——entity/SQL 表全部为 NULL）
- `TestNopMetaDdlUniqueKeyEmission` + `TestNopMetaUniqueKeysEnforced` 更新 + 多 schema upsert 行为回归测试
- arm-index P2-MA3-03 行终态更新 + roadmap R4.2 → done

### Out Of Scope

- 跨数据源 querySpace 去重（0852-3 follow-up）
- 多租户 / 多 schema 权限与隔离设计
- R4.3 调度幂等（独立 plan）
- 非 `nop-metadata` 模块的任何变更

## Execution Plan

### Phase 1 - metaSchema null 语义裁定 + UK 变更设计

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（NopMetaTable）+ `NopMetaDataSourceBizModel.java` + `OrmModelImporter.java` + `NopMetaTableBizModel.java` + `deploy/sql/*/_add_tenant_*.sql`（证据读取）

- Item Types: `Decision | Proof`

- [x] **live 复核写入路径与存量数据面（Proof）**：逐一读取 **3 个写路径**——`upsertExternalTable`（NopMetaDataSourceBizModel.java:428-468）、`OrmModelImporter.buildEntityTable`（:176-185）、`NopMetaTableBizModel.createSqlTable`（:161-169），确认哪些写 metaSchema、哪些保持 null；核对 `deploy/sql` 三方言**两份物化**——`_create_nop-metadata.sql`（:222/:224 等）与 `_add_tenant_nop-metadata.sql`（:227-228，tenant UK 变体 `(NOP_TENANT_ID, META_MODULE_ID, TABLE_NAME, IS_DELTA)` 不含 metaSchema，**同为 xgen 生成产物，模型 UK 变更后重新生成即自动同步**，禁止手编）
- [x] **null 语义裁定（Decision，三方言约束内）**：对「路径 A：保持可空 + UK 扩展」「路径 B：列契约变更（mandatory + 非空哨兵值）+ UK 扩展」「路径 C：partial/function-based 唯一索引（null-schema 族保留 3 列 UK + 非 null schema 用扩展 UK，三方言可行性须裁定）」三条路径做裁定——**裁定必须纳入方言语义事实**：(a) 可空列 MySQL/Oracle/PostgreSQL unique 均允许多 NULL → 路径 A 下 null-schema 族无法被 DB 层唯一性兜底（只靠 Java 层归一化匹配）；(b) **Oracle `''` 即 NULL**——路径 B 若用默认 `''` 则 Oracle 下 `DEFAULT '' NOT NULL` 的默认值即 NULL，插入显式 `''` 报 ORA-01400、存量迁移 `SET META_SCHEMA=''` 是静默 no-op（ALTER 加 NOT NULL 报 ORA-02296）——路径 B 必须改用非空哨兵值并显式处理 Oracle 语义（DDL 断言测试只查文本无法暴露，属 lesson 09 同族陷阱）；裁定结论 + Why 记录 repo-observable（本 plan + arm-index 对应行）
- [x] **存量数据面裁定（Decision，Major-4）**：存量 NULL 行为**主流而非例外**——全部 entity 表（buildEntityTable）与 SQL 表（createSqlTable）的 metaSchema 均为 NULL，**「无存量 NULL 数据面」裁定不可用**；pre-R3.19 部署（DDL 零 UK 时代）可能存在 `(metaModuleId, tableName, isDelta)` 重复行，迁移须覆盖 dedupe 或 fail-fast；Oracle 迁移语义（`SET ''` 无效）纳入；迁移方案（路径 A：无数据迁移；路径 B：NULL→哨兵值 + 重复行处置 + Oracle 适配）写入 plan 记录
- [x] **存量非租户部署升级路径裁定（Decision，Round-2 Major-2）**：`_create_` 只覆盖新装、`_add_tenant_` 只覆盖租户化——**已存在的非租户库（3 列 UK）升级路径空白**：不处理则多 schema 同名表在存量部署上继续撞 UK（新能力静默不可用）。裁定产出二选一（不得沉默）：(a) 产出非租户存量库 ALTER SQL（drop/add UK，三方言）；或 (b) 显式裁定 out-of-scope + Why Not Blocking Closure 记录到 Deferred。R3.19 先例（isDelta 维度）确无迁移脚本，但那是既有功能正确性修复，本 plan 是新能力——须显式声明
- [x] **RACE 残余面重新记录（Decision，Major-5）**：引用 `2026-07-20-1554#RACE` 裁决史——MA6.6 终局 watch-only 前提是「UK 有效阻止并发重复」；路径 A 下 null-schema 族并发 upsert 可产生重复行（UK 不拦 NULL）→ 该前提对 null-schema 族不再成立，须显式记录为 watch-only residual + Why（或改判）；不静默跳过
- [x] **createSqlTable fail-fast 回归处置（Decision，Round-2 Major-1）**：`createSqlTable`（NopMetaTableBizModel.java:161-169）是**纯创建**（无 find-first 去重），当前 3 列 UK 提供 fail-fast（重复创建抛 duplicate key）；路径 A 下 UK 扩展为含可空 META_SCHEMA 的 4 列后，同模块同名 SQL 表第二次创建**静默成功**（NULL 不拦）——顺序路径回归（非 RACE 并发残余）。裁定处置二选一：(a) Phase 3 增加 createSqlTable 重复守卫（find-or-fail，保持显式失败语义）；或 (b) 走路径 C（partial UK）保住 DB 层 fail-fast；或 (c) 显式裁定接受静默重复 + Why（不推荐，违反 fail-fast 语义）。**注意**：Deferred 段「SQL 表恒 null-schema」条目当前以「归一规则保证唯一性」为理由——该归一规则只存在于 `upsertExternalTable` 匹配逻辑，createSqlTable 没有，理由事实错误，须同步更正
- [x] **UK 变更设计定稿（Decision）**：按裁定结论确定 UK 列清单（路径 A：`(metaModuleId, tableName, isDelta, metaSchema)` 保持可空；路径 B：+ 列契约变更；路径 C：partial/function-based 唯一索引——**orm.xml `<unique-key>` 与 DDL 生成管线均无 partial 索引表达能力，若裁定路径 C 需脱离生成管线手写索引 SQL 或改平台模板（与 Non-Goal 冲突），C 不可行时 fallback 为 A/B**）与 tenant 变体同步方案（`_add_tenant_*.sql` 为 xgen 生成产物——**模型 UK 变更后重新生成即自动同步，仅需 git diff 核对**含 metaSchema 维度，否则租户部署修复被静默撤销——Blocker-2）；核对与 R3.19 双重存储裁决（isDelta 维度）无相互作用冲突
- [x] 存量迁移方案设计（按裁定：路径 A 无需迁移 / 路径 B 迁移 SQL + 重复行处置 + Oracle 适配 / 路径 C 无需迁移但需索引变更 SQL）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] null 语义裁定完成，结论 + Why 基于 live 复核（非复制旧文）且**显式覆盖三方言 unique 语义与 Oracle `''`=NULL 约束**，repo-observable 记录
- [x] 3 个写路径（upsertExternalTable / buildEntityTable / createSqlTable）全部核对并纳入裁定
- [x] 存量数据面裁定诚实（NULL 行为主流非例外），pre-R3.19 重复行与 Oracle 迁移语义已处置
- [x] **存量非租户部署升级路径已显式裁定**（ALTER SQL 产出 或 out-of-scope + Why），未沉默
- [x] **createSqlTable fail-fast 回归已显式处置**（Java 守卫 / 路径 C / 显式接受 + Why），未静默
- [x] RACE 残余面对 null-schema 族的影响已显式记录（watch-only residual 或改判），未静默跳过
- [x] UK 变更设计定稿（列清单 + 列契约变更与否 + **tenant 变体同步方案** + 与 R3.19 兼容性核对），无悬置决策
- [x] `No owner-doc update required`（Phase 1 纯裁定，无代码变更；docs-for-ai 不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - model-first ORM 变更 + 生成管线 + DDL 再生成

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml` + `deploy/sql/**`（`_create_` 再生成 + `_add_tenant_` 手写同步）+ `_gen/`（生成产物）

- Item Types: `Fix | Proof`

- [x] 按 Phase 1 裁定修改 `NopMetaTable` 的 `<unique-key name="UK_NOP_META_TABLE_MODULE_NAME">`（列清单含 metaSchema）+（如裁定）metaSchema 列契约变更（mandatory / 默认哨兵值）——**只改源模型，禁止手编 `_gen/` 与 `_*.xml`**（AGENTS.md Hard Stop）
- [x] `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` 重新生成 `_gen/` 产物 + `deploy/sql/*/_create_nop-metadata.sql` **+ `_add_tenant_*.sql`（xgen 模板自动同步 tenant UK 变体，禁止手编——Round-3 更正）** 三方言 DDL（codegen 管线 `orm/deploy/sql` xgen，R3.19 提交 9b769490e 已实证该链路），核对 `_NopMetaTable.java` 等生成文件与模型一致；**全文件 git diff 复核**（除目标 UK/列变更外零无关漂移）
- [x] **tenant UK 变体验证（Proof，Blocker-2）**：git diff 核对 `deploy/sql/{mysql,oracle,postgresql}/_add_tenant_nop-metadata.sql:227-228` 的 tenant UK 变体已含 metaSchema 维度（`(NOP_TENANT_ID, META_MODULE_ID, TABLE_NAME, IS_DELTA, META_SCHEMA)` 或按裁定方案），确认租户部署下修复不被静默撤销；**如再生成未自动同步（模板缺陷），上报而非手编**
- [x] 存量迁移 SQL（如 Phase 1 裁定路径 B）：生成/编写到 `deploy/sql/` 下对应迁移文件（NULL→哨兵值 + 重复行 dedupe/fail-fast + Oracle 适配；Oracle 迁移不可用 `SET ''`）；路径 A 则记录「无需数据迁移」

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] orm.xml UK 变更 + 列契约变更（如有）落地，模型语义与 Phase 1 裁定一致
- [x] `_gen/` 重新生成，无手编生成产物（git diff 核对生成文件均源自模型）
- [x] 三方言 DDL 再生成，UK 含 metaSchema 维度，DDL 与 orm.xml 一致（`TestNopMetaDdlUniqueKeyEmission` 三方言断言核对）
- [x] **`_add_tenant_*.sql` 三方言 tenant UK 变体经再生成自动同步完成**（git diff 核对含 metaSchema 维度，禁止手编），无静默撤销路径
- [x] 存量迁移方案落地或按裁定记录（路径 A 无需迁移须写明理由；路径 B 迁移 SQL 含重复行处置 + Oracle 适配）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 写入路径同步 + 行为回归测试

Status: completed
Targets: `NopMetaDataSourceBizModel.java` + `OrmModelImporter.java` + `NopMetaTableBizModel.java`（如需）+ 测试文件

- Item Types: `Fix | Proof`

- [x] **接线同步（Fix）**：`upsertExternalTable` 的 schema 归一化写入与 UK 语义对齐（路径 A：**存储值归一化**——写入侧 `setMetaSchema(info.getSchema())` 与匹配侧 `normalizeSchemaForMatch` 归一规则一致（null/''/空白→null），避免 DB 层 NULL 与 `''` 两个键值并存产生 UK 拦不住的重复行；路径 B：写入统一哨兵值）——不改变 Java 层匹配语义，仅对齐存储契约
- [x] **entity 路径核对（Proof）**：`OrmModelImporter.buildEntityTable`（metaSchema=null）在列契约变更（如 mandatory + 默认哨兵值）下是否仍可落盘——如不可，按裁定同步（如显式设哨兵值或依赖 ORM defaultValue 自动填充——`EntityPersisterImpl.checkColumnValueWhenSave` 对 mandatory+defaultValue 自动填充已实证），确保模型导入链路不回归
- [x] **createSqlTable 重复守卫（Fix，Round-2 Major-1）**：按 Phase 1 裁定处置——若裁定路径 A（可空 4 列 UK 不拦 NULL），`NopMetaTableBizModel.createSqlTable`（:161-169）补 find-or-fail 守卫（同模块同表名已存在 → 显式错误码，保持 fail-fast 语义），SQL 表「恒 null-schema」语义在裁定中显式记录（Major-2）；若裁定路径 B/C 则按裁定核对无需守卫
- [x] **存量非租户部署升级（Fix，Round-2 Major-2）**：按 Phase 1 裁定产出非租户存量库 ALTER SQL（drop/add UK，三方言）或记录 out-of-scope 裁定——与 `_add_tenant_` 租户路径区分，两份升级产物均须与目标 UK 形态一致
- [x] **行为回归测试（Fix，Test-Mandated Feature Rule）**：新增多 schema 同名表 upsert 测试——同模块下两个不同 schema 的同名外部表均成功落盘且互不覆盖（e2e：`upsertExternalTable` → DB 两行）；单 schema 场景与修复前行为一致（回归）
- [x] **DDL 断言测试更新（Fix）**：`TestNopMetaDdlUniqueKeyEmission` 增加 UK 含 META_SCHEMA 维度断言（`hasUniqueKeyColumn(table, "UK_NOP_META_TABLE_MODULE_NAME", "META_SCHEMA")`）；如列契约变更，断言三方言 DDL 列定义（NOT NULL/默认值）；**`TestNopMetaUniqueKeysEnforced.testNopMetaTableHasNaturalUniqueKey`（:77-83）更新为含 metaSchema 的 4 列断言**（helper 为列数精确匹配，必改，Major-1）
- [x] 全量回归：`./mvnw test -pl nop-metadata -T 1C`（0 failures；pre-existing 失败按 MR3/MR4 惯例归因记录）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**：多 schema 同名外部表从 `upsertExternalTable` 入口到 DB 落盘的完整路径已验证（两 schema 两行，互不覆盖）——见 Minimum Rules #22
- [x] **接线验证**：3 个写路径（upsertExternalTable / buildEntityTable / createSqlTable）与 UK 语义对齐的运行时连通性已验证（修复后的写入确实满足新 UK；createSqlTable 重复创建按裁定 fail-fast 或显式接受）——见 Minimum Rules #23
- [x] **无静默跳过**：新增/修改代码无空方法体/吞异常/静默返回；null 语义裁定不是用「继续插入」绕过冲突（修复前撞 UK 是异常路径，修复后应正常落盘或显式失败）——见 Minimum Rules #24
- [x] 新增行为有明确测试覆盖（多 schema 落盘 + 单 schema 回归 + DDL 断言），区分性断言
- [x] `./mvnw test -pl nop-metadata -T 1C` 全绿（0 failures）
- [x] 文档变化：若列契约变更影响公开契约（GraphQL 字段/API），同步 `docs-for-ai/03-modules/nop-metadata.md` + `ai-dev/design/nop-metadata/` 对应文档；否则 `No owner-doc update required`（UK/列契约变更属模型内部契约，预期不影响 GraphQL 字段面，需在 phase 内核实）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口（roadmap R4.2 → done + arm-index 终态 + closure audit）

Status: planned
Targets: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md` + `ai-dev/audits/arm-index-nop-metadata.md`

- Item Types: `Decision | Proof`

- [ ] arm-index P2-MA3-03 行终态更新（fixed，附 plan 引用 + 修复摘要）
- [ ] roadmap R4.2 行 → done（注明计划引用与修复摘要）
- [ ] 独立子 agent closure audit（fresh session，closure-audit-prompt.md）：逐项核对本 plan 全部 Phase Exit Criteria + Closure Gates，证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] arm-index + roadmap R4.2 终态一致可追溯
- [ ] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [ ] 无静默降级：null 语义裁定与 UK 变更无 live defect 被降级为 deferred
- [ ] 文档变化：roadmap + arm-index 更新；docs-for-ai 按 Phase 3 核实结果处理
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Phase 1 Adjudication Records（2026-08-05 live 复核 + 裁定，repo-observable）

### D1 — metaSchema null 语义裁定：路径 A（保持可空 + UK 扩展为 4 列）

**裁定：路径 A**——`NopMetaTable.metaSchema` 保持可空列，`UK_NOP_META_TABLE_MODULE_NAME` 扩展为 `(metaModuleId, tableName, isDelta, metaSchema)`。

Why（显式覆盖三方言 unique 语义 + Oracle `''`=NULL 约束）：
- (a) 可空列 MySQL/Oracle/PostgreSQL unique 均允许多 NULL → 路径 A 下 null-schema 族（entity/SQL/无 schema 外部表）无 DB 层唯一性兜底；但 Java 层归一化匹配（`normalizeSchemaForMatch`，NopMetaDataSourceBizModel.java:471-476）覆盖全部顺序路径；非 null schema 族获得完整 DB 兜底（对比现状 3 列 UK 全族无兜底，是严格加强）
- (b) Oracle `''` 即 NULL：路径 A 无 NOT NULL / DEFAULT / 哨兵值 / 迁移 SQL → 完全规避 ORA-01400（INSERT 显式 `''`）、ORA-02296（ALTER 加 NOT NULL）、`SET META_SCHEMA=''` 静默 no-op 三类 Oracle 陷阱（Blocker-1）
- NULL 是主流值（entity 表 + SQL 表全部 NULL）——路径 B（mandatory + 非空哨兵值）会把主流值改造成哨兵值 = GraphQL 可观测变更（metaSchema 字段返回值变化）+ 全量迁移 + Oracle ALTER 风险，无 finding 支撑该破坏面（Non-Goal：单 schema 部署行为语义保持）
- 路径 C（partial/function-based 唯一索引）：orm.xml `<unique-key>` 与 DDL 生成管线（ddl.xlib `TableUniqueConstraints`）均无 partial/function-based 索引表达能力，落地需手写索引 SQL（脱离生成管线，违反 model-first）或改平台模板（Non-Goal 冲突）→ 不可行，fallback 为 A
- 与 R3.19 双重存储裁决（isDelta 维度）正交无冲突：isDelta 为非空确定列、metaSchema 为可空语义维度列，两维度语义独立

### D2 — 存量数据面裁定：无存量 NULL 数据面裁定不可用；路径 A 无需数据迁移

- 存量 NULL 行为**主流而非例外**（Major-4）：全部 entity 表（OrmModelImporter.buildEntityTable，metaSchema 不设保持 null）与 SQL 表（NopMetaTableBizModel.createSqlTable，同样不设）均为 NULL
- 路径 A 下 NULL 与 4 列 UK 天然兼容（可空列允许多 NULL）→ **无需数据迁移**；多 schema 同名表新写入天然带非空 schema，被 4 列 UK 在 DB 层兜底
- pre-R3.19 部署（DDL 零 UK 时代）可能存在的 `(metaModuleId, tableName, isDelta)` 重复行：新装 DDL（`_create_`）不覆盖存量库；存量库升级路径见 D3（ALTER SQL 内注释显式声明前置条件：添加约束前须先处置重复行）

### D3 — 存量非租户部署升级路径裁定：产出三方言 ALTER SQL（landed，Round-2 Major-2）

- `_create_` 只覆盖新装、`_add_tenant_` 只覆盖租户化 → 已存在的非租户库（3 列 UK）若不处理，多 schema 同名表继续撞 3 列 UK（新能力静默不可用）
- **裁定：产出非租户存量库 ALTER SQL（drop 3 列 UK → add 4 列 UK，三方言）**，落地为 `deploy/sql/{mysql,oracle,postgresql}/upgrade-nop-meta-table-uk.sql`（手写保留文件，非 xgen 产物，与 `_add_tenant_` 租户路径区分）
- 文件内注释显式声明前置条件：pre-R3.19 零 UK 部署升级 R3.19 时可能残留重复行，add constraint 前须先 dedupe（fail-fast：add 失败即提示处置）
- Why landed 而非 out-of-scope：R3.19 先例（isDelta 维度）确无迁移脚本，但那是既有功能正确性修复；本 plan 是新能力（多 schema 支持），升级产物成本极低（3 个 SQL 文件）且消除「新能力静默不可用」面

### D4 — RACE 残余面重新记录：null-schema 族 watch-only residual（Major-5）

- MA6.6 终局 watch-only 前提是「UK 有效阻止并发重复」；路径 A 下可空 4 列 UK 不拦 NULL → **该前提对 null-schema 族不再成立**，显式记录为 watch-only residual（与计划 Deferred 段登记一致），不静默跳过
- 顺序路径由 Java 层归一化匹配（normalizeSchemaForMatch）覆盖；残余面 = 并发 sync 下 null-schema 同名表重复插入（极低概率，单 schema 部署无暴露）

### D5 — createSqlTable fail-fast 回归处置：Java 层 find-or-fail 守卫（选项 a，Round-2 Major-1）

- 路径 A 下 4 列 UK 含可空 META_SCHEMA → `createSqlTable`（NopMetaTableBizModel.java:161-169，纯创建无 find-first 去重）第二次创建同模块同名 SQL 表**静默成功**（NULL 不拦）= 顺序路径回归（非 RACE 并发残余）
- **裁定：选项 (a)**——`createSqlTable` 入口按 `(metaModuleId, tableName)` 查重（SQL 表恒 null-schema，3 列查询即可覆盖），命中抛显式错误码 `nop.err.metadata.sql-view-table-exists`（新增至 SqlErrors.java，含 metaModuleId/tableName 参数），保持 fail-fast 语义
- **同步更正**：Deferred 段「SQL 表恒 null-schema」条目原理由「归一规则保证唯一性」事实错误——normalizeSchemaForMatch 仅存在于 upsertExternalTable 匹配逻辑，createSqlTable 无此逻辑；守卫落地后理由更新为「重复守卫提供 find-or-fail」

### D6 — UK 变更设计定稿

- **UK_NOP_META_TABLE_MODULE_NAME = (metaModuleId, tableName, isDelta, metaSchema)**，保持可空，无列契约变更（路径 A）
- tenant 变体：`_add_tenant_*.sql` 为 xgen 生成产物（`AddTenantIdToUniqueKey`，ddl.xlib:236-243 从模型 UK 列派生，`uniqueKey.constraint` 为发射 gate——已实证）→ 模型 UK 变更后 `mvn clean install` 再生成即自动同步为 `(NOP_TENANT_ID, META_MODULE_ID, TABLE_NAME, IS_DELTA, META_SCHEMA)`，git diff 核对即可（Blocker-2，禁止手编）
- 与 R3.19 双重存储裁决（isDelta 维度）无相互作用冲突（见 D1 末条）

### D7 — 存量迁移方案：路径 A 无需数据迁移

- 理由：NULL 为存量主流值（entity/SQL 表全部 NULL）且可空列 UK 允许多 NULL（三方言一致），4 列 UK 与存量 NULL 数据完全兼容；多 schema 场景为纯新增写入路径（非空 schema 进 4 列 UK 被 DB 兜底）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] metaSchema null 语义已裁定（live 证据 + 三方言 unique 语义 + Oracle `''`=NULL 约束），UK 变更落地（model-first），多 schema 同名表可共存
- [ ] 所有 3 个 in-scope 写路径与 UK 语义一致（upsertExternalTable / buildEntityTable / createSqlTable），无接线断裂
- [ ] **`_add_tenant_*.sql` 三方言 tenant UK 变体已同步**（含 metaSchema 维度），租户部署下修复不被静默撤销
- [ ] 存量迁移方案落地或明确裁定（NULL 行为主流非例外；路径 B 含重复行处置 + Oracle 适配；**非租户存量部署升级路径已显式裁定**）
- [ ] 必要 focused verification 已完成（多 schema upsert e2e + 单 schema 回归 + DDL 断言 + UK 列数断言更新 + **createSqlTable 重复守卫测试**）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（含 RACE null-schema 族残余面显式记录）
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）upsert 写入路径与新 UK 的运行时连通（e2e 两 schema 两行落盘），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C`
- [ ] `./mvnw test -pl nop-metadata -T 1C`（0 failures）
- [ ] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（若修改 docs-for-ai/ 则必跑）

## Deferred But Adjudicated

### 跨数据源 querySpace 维度纳入去重键（plan 0852-3 follow-up）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 跨数据源同名同 schema 覆盖语义迁移需评估破坏面（0852-3 已裁定 follow-up）；本 plan 只处理 schema 维度，不改变 querySpace 去重行为
- Successor Required: `no`
- Successor Path: —

### null-schema 族并发 upsert 重复（RACE 残余面，路径 A/C 裁定后登记）

- Classification: `watch-only residual`（Phase 1 裁定路径 A 或 C 时生效；路径 B 则改写为「DB 层已兜底，无残余」）
- Why Not Blocking Closure: 可空列 unique 允许多 NULL，null-schema 族（entity/SQL/无 schema 外部表）的 DB 层唯一性兜底不成立（路径 C 下 null-schema 族同样保留 3 列 UK，残余相同）；Java 层归一化匹配（normalizeSchemaForMatch）覆盖顺序路径，残余面 = 并发 sync 下 null-schema 同名表重复插入（MA6.6 RACE 终局前提对 null-schema 族不再成立，Phase 1 显式记录）
- Successor Required: `no`
- Successor Path: —

### SQL 表「恒 null-schema」语义（createSqlTable 写路径）

- Classification: `watch-only residual`（Phase 1 裁定确认后登记）
- Why Not Blocking Closure: `NopMetaTableBizModel.createSqlTable`（:161-169）创建的 tableType=SQL 表不设 metaSchema（null）——语义为「SQL 视图无 schema 维度」；**唯一性保障 = Phase 1 裁定的 createSqlTable 重复守卫（find-or-fail，裁定选项 a）或路径 B/C 的 DB 层约束（选项 b）**；若裁定选项 (c)（显式接受静默重复），须在 Phase 1 记录 Why（修正 Round-2 审查指出的错误：原理由「归一规则保证唯一性」不成立——normalizeSchemaForMatch 仅存在于 upsertExternalTable 匹配逻辑，createSqlTable 无此逻辑）
- Successor Required: `no`
- Successor Path: —

### 存量非租户部署 UK 升级（Round-2 Major-2）

- Classification: `out-of-scope improvement`（Phase 1 若裁定此路径）或 `landed`（Phase 1 若裁定产出 ALTER SQL）
- Why Not Blocking Closure: （若裁定 out-of-scope）R3.19 先例（isDelta 维度）亦无迁移脚本；存量非租户部署升级为运维流程问题，不改变代码正确性——但须显式记录，不得沉默
- Successor Required: `no`
- Successor Path: —

### 多租户 schema 隔离 / 权限模型

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 无 finding 支撑，单 schema 部署当前无暴露面
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- （按执行结果补充：如 Phase 3 发现新的 NopMetaTable 写路径未覆盖，登记观察项）

## Closure

Status Note: 待执行后填写
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: 待独立子 agent 填写
- Evidence: 待填写（每条 Exit Criterion 的验证结果 + check-plan-checklist exit 0 + Anti-Hollow 检查结果 + Deferred 项分类检查）

Follow-up:

- 待执行后填写

## Optional Sections

- `## Risks And Rollback`：ORM 模型变更（Protected Area）——本 plan 即裁决载体（plan-first 声明）；回滚 = 还原 orm.xml + 重新生成 + DDL 回退；`_gen/` 与 `deploy/sql` 全程禁止手编，git diff 核对生成物一致性
- `## Outdated Note`：若执行期间发现 schema 语义被其他工作改变（如新增 UK_NOP_META_EXTERNAL_TABLE_* 或其他 UK 变更），立即中止 Phase 2 重新裁定并上报
