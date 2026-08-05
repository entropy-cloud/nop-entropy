# MR4 跨维度裁决（R4.1：Successor: MR4 deferred 项终局裁决 + 跨维度 P1 冲突核对）

> Plan Status: completed
> Last Reviewed: 2026-08-05
> Draft Review: 2 轮独立子 agent 对抗性审查通过（第 1 轮 0 Blocker + 1 Major + 3 Minor 全部修复；第 2 轮复审 4 项修复全部 PASS + 2 Minor 已按复审建议修复，0 Blocker / 0 Major 残留，裁定可执行）。Session: ses_02f71f0ccffefEHTbv7Q2T6gvL / ses_02f6b7012ffecVI9793XbQ6al5。
> Mission: nop-metadata-audit-remediation
> Work Item: MR4（R4.1 跨维度 P1 裁决与冲突修复）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR4 里程碑 + R2.7/R2.9/R2.14/R2.15/R3.20 行）、`ai-dev/audits/arm-index-nop-metadata.md`（§P1/§P2 追踪）、MR1（2026-08-04-1004-3）/MR2（2026-08-04-1543-3）/MR3（2026-08-05-0746-2）completed 计划
> Related: 执行顺序 `{1}` of 3 — 启动门禁：MR1+MR2+MR3 全部 completed（roadmap R4.1 Deps）、roadmap MR1/MR2/MR3 段全部行 done；本 plan 为**裁决文档计划**（roadmap MR4 里程碑产出 = 裁决文档，无冲突时直接 done），如裁决提升任何项为修复则含代码变更（roadmap 规则 3 例外路径），为 MV（V.1-V.3）提供输入。

## Purpose

承接 MR1+MR2+MR3 全部收口后的 MR4 跨维度裁决（R4.1）：(a) 对全部 Successor: MR4 的 deferred 项做**终局裁决**——逐项 live 复核后裁定为终局 deferred（归类 watch-only residual / optimization candidate / out-of-scope improvement + Why Not Blocking Closure）或提升为修复（新证据显示 live defect / 激活条件满足）；(b) 跨维度 P1 冲突核对（全部 P0/P1 finding 终态一致、追踪链无断裂）；(c) 裁决记录写入 roadmap R4.1 段 + arm-index，R4.1 → done，为 MV 全量验证提供闭环输入。

> 裁决对象共 8 项（枚举见 Current Baseline，含 P2-MA3-03 一项由 R2.15 与 R3.20 两处 Successor 声明共同指向，去重后计 1 项）

## Current Baseline

经 2026-08-05 live repo 核对（finding 终态以 arm-index §P1/§P2 与 roadmap R 行为准；**MR4 不重新审计，只做终局裁决与冲突核对**）：

- roadmap MR4 行（R4.1）状态 `todo`；MV 行（V.1-V.3）全部 `todo`（V.1 依赖 MR4 done）；**Deps 满足：MR1+MR2+MR3 plan 全部 completed，roadmap MR1/MR2/MR3 段全部行 done**（MR3 收口：P0-MA7.1-01 + P1×4 + P2-MA5-401 must-fix + 13 项 P2 in-scope 全部 landed；deferred 5 项登记 R3.20）
- **Successor: MR4 的 deferred 项共 8 项（本 plan 裁决对象，来源 = 各裁决行 Successor 声明）**：
  - **R2.7（`2026-07-23-0714#维度07-004`）**：queryAggregation 返回 `List<Map<String,Object>>` 未类型化——AggregationRowDTO 死 DTO 已在 R3.8（P2-MA6.1-001）移除；实际行结构为扁平 alias-key Map（measure/dimension 别名→值）；强类型化需先定义行结构契约（改变 GraphQL items 输出形状 = 破坏性变更）；当时 0 消费方强转
  - **R2.9（`2026-07-21-2039#维度07-03`）**：queryAggregation 11 参数签名未用 @RequestBean——`INopMetaTableBiz.queryAggregation`（`nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/INopMetaTableBiz.java:75`，11 参）被契约测试钉死（`TestNopMetaBizInterfaceCompleteness` 中 queryAggregation 11 参断言在 **line 47**；line 54 为 INopMetaModuleBiz.importOrmModel 断言，R2.9 旧引用不准确）；改造涉及三件套（接口签名/契约测试/GraphQL rpc 参数结构）同步 = 破坏性公开契约变更；11 参签名功能完整、无 live defect
  - **R2.14（P2-MA3-02）**：entity 路径数据查询绕过 data-auth 过滤合并（queryTableData/queryJoinData/queryAggregation 裸 DAO/EQL，对比 CrudBizModel:381 合并点）——合并 appendFilter 需跨 3 个 executor 查询管线设计；enable-data-auth 双开关默认 false + app 未配置无实际暴露（MA3.3 复核实证）
  - **R2.15 + R3.20（P2-MA3-03）**：upsertExternalTable 的 metaSchema 维度未进 DB UK（多 schema 同名表必然 UK 冲突）——需先裁定 metaSchema **null 语义**（可空列 MySQL/Oracle unique 允许多个 NULL 无法阻止重复；NOT NULL/默认 '' 属列契约变更 + 存量迁移），属 ORM 变更 Protected Area
  - **R3.20（P2-MA5-301）**：dataSourceId/datasourceType 双拼写（`nop-metadata/model/nop-metadata.orm.xml:383/:392`）——统一拼写 = GraphQL 公开字段名破坏性变更 + 契约测试 + 消费方同步；命名不一致非 live defect（沿 07-003/R2.9 先例）
  - **R3.20（P2-MA7.2-02）**：entity 自定义查询 data-auth 绕过（与 P2-MA3-02 同族）——enable-data-auth 双开关默认 false + app 未配置无实际暴露（沿 R2.14 先例）
  - **R3.20（P2-MA7.5-04）**：调度器事务回滚副作用（checkpoint 扫描与质量规则执行同事务）——MA7.5-01 修复（cron job 业务错误转 LOG.error + 存活返回）后放大器消除
  - **R3.20（P2-MA7.5-05）**：质量检查点执行无幂等键——需 ORM 变更 + 分布式锁设计（Protected Area + 设计工作）
- **live 复核事实（2026-08-05 实测，MR4 Phase 2 执行时须重新核对）**：
  - `nop-metadata/nop-metadata-app/src/main/resources/application.yaml:17` 已配置 `data-auth-config-path: /nop/metadata/auth/app.data-auth.xml`（文件存在于 `nop-metadata-app/src/main/resources/_vfs/nop/metadata/auth/`），但 **未设置 `nop.auth.use-data-auth-table`（CFG_AUTH_USE_DATA_AUTH_TABLE）为 true**——data-auth 实际激活状态须在 Phase 2 重新核对（P2-MA3-02/P2-MA7.2-02 裁决的关键依赖事实，不得沿用旧结论）
  - orm.xml:383/:392 双拼写仍存在（dataSourceId / datasourceType）
  - INopMetaTableBiz.queryAggregation 11 参签名仍在（:75）
- 绿色基线：**857 tests / 0 failures / 0 errors / 0 skipped**（MR3 收口实测 2026-08-05，范围 `-pl nop-metadata`（无 -am，见 08-05 日志 surefire 口径）；本 plan 执行时以重新实测记录为准）
- **MR4 边界**：roadmap MR4 里程碑定义 = "裁决文档（无冲突时直接 done）"；MR4 不是新审计轮次，不重新审计已闭包 finding（arm-index P1/P2 追踪为唯一事实源）

## Goals

- 对 8 项 Successor: MR4 deferred 项逐项终局裁决：每项经 live 复核后落到 (a) 终局 deferred（归类 watch-only residual / optimization candidate / out-of-scope improvement + Why Not Blocking Closure + Successor Required）或 (b) 提升为修复（带测试落地，Test-Mandated Feature Rule；涉 ORM 走 model-first + plan-first 声明）
- 跨维度 P1 冲突核对：arm-index §P1 全部 finding 终态一致（fixed 或 deferred + 可追溯），无跨维度矛盾（如双拼写 vs 契约测试、metaSchema UK vs 多 schema 功能、data-auth 项之间的一致性）
- 裁决记录写入 roadmap R4.1 段（含每项终局归类与依据）+ arm-index §P2 更新，R4.1 → done
- 为 MV 提供闭环输入（V.2 追踪矩阵的 MR4 裁决段）

## Non-Goals

- 不执行 MV（V.1-V.3 全量 build/test + closure audit，由 plan-2026-08-05-1408-2 承接）
- 不执行 MG（G.1-G.3 知识沉淀，由 plan-2026-08-05-1408-3 承接）
- 不进行任何新审计（MA1-MA7 已 done；MR4 只消费其产物）
- 不处理 P3（roadmap 规则 1：只处理 P0/P1；P3 维持 deferred）
- 不重做 MR1/MR2/MR3 已修复项（只在核对中发现回归时上报并记录）
- 不修改平台模板（nop-persistence/nop-orm 等 Protected Area；DDL/模型类变更仅限 nop-metadata 自身模型 model-first）

## Scope

### In Scope

- 启动门禁核查（MR1+MR2+MR3 completed + roadmap 行 done）
- 8 项 Successor: MR4 deferred 项逐项 live 复核与终局裁决（R2.7/R2.9/R2.14/R2.15→R3.20/R3.20×5）
- 跨维度 P1 冲突核对（arm-index §P1 追踪矩阵一致性）
- 裁决提升为修复的项落地（代码 + 测试，Test-Mandated Feature Rule；涉 ORM model-first + plan-first 本 plan 即裁决载体）
- roadmap R4.1 段裁决记录 + arm-index 更新 + R4.1 → done
- 独立子 agent closure audit

### Out Of Scope

- MV/MG（后续 plan-2026-08-05-1408-2 / -3）
- 新审计维度、P3 处置、平台模板变更

## Execution Plan

### Phase 1 - 门禁 + 输入归集 + 跨维度 P1 冲突核对

Status: completed
Targets: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR4 段）+ `ai-dev/audits/arm-index-nop-metadata.md`（§P1/§P2）

- Item Types: `Decision | Proof`

- [x] **启动门禁核查（Proof）**：确认 MR1（2026-08-04-1004-3）/MR2（2026-08-04-1543-3）/MR3（2026-08-05-0746-2）plan 全部 completed（check-plan-checklist --strict exit 0 抽查）且 roadmap MR1/MR2/MR3 段全部行 done；**任一不满足则不启动并上报**——执行结果记录：`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 对三份 plan 均 exit 0（MR1/MR2/MR3 各 1 passed / 0 failed）；三份 plan `Plan Status: completed` 且各 Phase `Status: completed`（MR1 3 Phase / MR2 4 Phase / MR3 4 Phase）；roadmap MR1/MR2/MR3 段全部 R*.x 行 `done`（R2.6/R2.7/R2.9/R2.14/R2.15/R2.16/R2.17/R2.18/R2.19 为 done（裁决）行，R3.0-R3.20 全 done）。门禁全部满足，启动 MR4
- [x] **Successor: MR4 项归集（Decision）**：从 roadmap R2.7/R2.9/R2.14/R2.15/R3.20 行 + MR1/MR2/MR3 plan Deferred 段 + arm-index §P2 汇总全部 Successor: MR4 项，核对与 arm-index 登记一致（8 项清单 + 每项现有 Why Not Blocking Closure + 现有归类），确认无遗漏（防 Successor 声明与登记断裂）——8 项清单核对结果记录：`rg "Successor.*MR4" roadmap + arm-index + 三份 MR plan` 全仓归集 = 8 项（R2.7〔`2026-07-23-0714#维度07-004`〕/R2.9〔`2026-07-21-2039#维度07-03`〕/R2.14〔P2-MA3-02〕/R2.15+R3.20〔P2-MA3-03，R2.15 与 R3.20 两处 Successor 声明共同指向，去重计 1 项〕/R3.20〔P2-MA5-301〕/R3.20〔P2-MA7.2-02〕/R3.20〔P2-MA7.5-04〕/R3.20〔P2-MA7.5-05〕）；与 arm-index §P2 各行登记逐一对应（07-004 行/R2.9 行/P2-MA3-02 行/P2-MA3-03 行/P2-MA5-301 行/P2-MA7.2-02 行/P2-MA7.5-04 行/P2-MA7.5-05 行），无遗漏无重复
- [x] **跨维度 P1 冲突核对（Proof）**：逐项检查 arm-index §P1 全部 P0/P1 finding 终态（fixed 或 deferred + 可追溯），确认无：同一契约点两个 finding 终态矛盾（如 P2-MA5-301 双拼写 vs GraphQL 契约测试钉死）、模型变更类 finding 与已落地 model-first 产物冲突（如 P2-MA3-03 与 R3.19 UK 修复的相互作用）、data-auth 类 finding（P2-MA3-02/P2-MA7.2-02）与当前激活状态矛盾——冲突清单记录（预期：无冲突，注记 "无跨维度冲突"）：核对结论 = **无跨维度冲突**。逐项证据：(a) P2-MA5-301 双拼写 vs 契约测试：`TestNopMetaBizInterfaceCompleteness` 仅断言接口方法签名（queryAggregation 11 参 :47、importOrmModel :54），不含 NopMetaDataSource 字段断言——双拼写不构成与契约测试的矛盾，命名不一致非 live defect；(b) P2-MA3-03 vs R3.19 UK 修复：R3.19 为 36 个 UK 补 constraint 属性 + UK_NOP_META_TABLE_MODULE_NAME 补 isDelta 维度（orm.xml:1316-1317 实证），metaSchema 仍不在 UK 内（orm.xml:1310 可空列）——R3.19 改善 UK 物化，P2-MA3-03 语义（schema ∉ UK）不变，无相互作用冲突；(c) data-auth 族（P2-MA3-02/P2-MA7.2-02）vs 激活状态：`nop.auth.use-data-auth-table` 默认 false（NopAuthConfigs.java:69-70 `varRef(..., "nop.auth.use-data-auth-table", Boolean.class, false)`）+ `nop.auth.enable-data-auth`（enableDataAuth）默认 false（biz-defaults.beans.xml:16 `@cfg:nop.auth.enable-data-auth|false`）+ application.yaml:17 仅配 data-auth-config-path 未开启双开关——两 finding 的 deferred 终态与当前激活状态一致；(d) P0/P1 全部终态可追溯：P0 表 MA7.1-01 fixed（R3.1）、AR-01/AR-02/11-01 done；P1 表 9 项全部 fixed（MR1/MR2/MR3）+ 2 项 watch-only（MISSING-AUTH/16-01）——终态一致
- [x] 向 roadmap MR4 段追加裁决记录骨架（R4.1 行状态 → in progress），登记 8 项清单与裁决时限——执行结果记录：roadmap MR4 段已追加「R4.1 裁决记录」骨架块（8 项清单 + 与 arm-index 一致性 + 裁决时限 2026-08-05），R4.1 行 todo → in progress

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 门禁核查通过（MR1/MR2/MR3 completed + roadmap 行 done），未满足则已上报
- [x] 8 项 Successor: MR4 清单与 arm-index/roadmap 登记一致，无遗漏无重复
- [x] 跨维度 P1 冲突核对完成，结果（无冲突 / 冲突清单）repo-observable 记录
- [x] roadmap R4.1 行 → in progress，裁决记录骨架已追加
- [x] 文档变化：roadmap + arm-index 更新；`No owner-doc update required`（docs-for-ai 不变，裁决提升项若有契约变化在 Phase 3 记录）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 8 项 Successor: MR4 deferred 项逐项终局裁决

Status: completed
Targets: `ai-dev/audits/arm-index-nop-metadata.md`（§P2）+ 各对应代码位置（live 复核用）

- Item Types: `Decision | Proof`

- [x] **裁决框架先定（Decision）**：按 Minimum Rule #16 与 Allowed Deferred Classifications 定义裁决规则——每项复核后必须落到：终局 deferred（仅限 watch-only residual / optimization candidate / out-of-scope improvement 三类之一，附 Why Not Blocking Closure + Successor Required）或提升为修复（新证据显示 live defect / 激活条件满足 / 新消费者出现，此时无 deferred 选项）；**已确认 live defect 不得降级为 deferred**；裁决依据写入 roadmap R4.1 段 + arm-index 对应行——裁决框架记录：规则 = ①每项以 2026-08-05 live 复核（配置扫描 + 代码路径追踪 + 消费方扫描）为准，不沿用旧文；②终局 deferred 仅限三类归类，每项附 live 依据的 Why Not Blocking Closure + Successor Required（yes 时必须引用已存在载体：已注册 plan 或 roadmap 登记行）；③新证据显示 live defect / 激活条件满足 / 新消费者出现 → 提升修复（Test-Mandated Feature Rule + 涉 ORM 走 model-first）；④8 项裁决全部完成后统一写入 roadmap R4.1 段 + arm-index §P2 对应行
- [x] **P2-MA3-02 + P2-MA7.2-02 合并裁决（Decision，data-auth 族）**：live 复核 data-auth 实际激活状态——`nop.auth.use-data-auth-table`（CFG_AUTH_USE_DATA_AUTH_TABLE）当前值 + application.yaml data-auth-config-path 指向文件是否被加载 + AuthHelper.appendFilter 在当前配置下是否实际执行（证据手段：配置扫描 + 读 DefaultDataAuthChecker 判定链 + 容器内运行探针或现有 data-auth 测试）；同时复核 queryTableData/queryJoinData/queryAggregation 裸 DAO/EQL 路径现状（CrudBizModel:381 合并点仍不适用？）；裁决 = 维持终局 deferred（激活开关仍 false，归类 watch-only residual，Successor Required: no）或提升修复（开关已激活 → 跨 3 executor 管线设计 + 修复，plan-first 声明，移 Phase 3）——裁决记录：**终局 deferred（watch-only residual）**。live 复核证据：(a) 双开关激活状态——`CFG_AUTH_USE_DATA_AUTH_TABLE`（`nop.auth.use-data-auth-table`）默认 **false**（nop-auth-service NopAuthConfigs.java:69-70 `varRef(s_loc, "nop.auth.use-data-auth-table", Boolean.class, false)`），application.yaml:17 仅配置 `data-auth-config-path: /nop/metadata/auth/app.data-auth.xml`（文件存在：`nop-metadata-app/src/main/resources/_vfs/nop/metadata/auth/app.data-auth.xml` + app.action-auth.xml），**未设置 use-data-auth-table = true**；`enableDataAuth` 开关（`nop.auth.enable-data-auth`，biz-defaults.beans.xml:16 `<property name="enableDataAuth" value="@cfg:nop.auth.enable-data-auth|false"/>`）同样默认 false 未开启——双开关仍全关，data-auth 过滤未被激活（DefaultDataAuthChecker.java:80-84 isUseTenant 判定链 `ResourceTenantManager.isEnableTenantResource() || (CFG_AUTH_USE_DATA_AUTH_TABLE.get() && dao.isUseTenant())`，两路均 false）。(b) 裸 DAO/EQL 路径现状——NopMetaTableBizModel.queryTableData(:208)/queryJoinData(:234)/queryAggregation(:254) 均无 AuthHelper.appendFilter 调用（rg 实证：三个方法体零 DataAuth 引用，走 queryAction/joinExecutor/aggregation processors 裸 EQL/JDBC），CrudBizModel:381 的 appendFilter 合并点仍仅覆盖 ORM CRUD 路径，对裸 SQL 执行路径不适用（AuthHelper.java:27 appendFilter 签名 `(IDataAuthChecker, QueryBean, bizObjName, action, context)`，QueryBean 过滤无法挂到裸 JDBC 查询）。(c) 结论：激活条件未满足（双开关 false），无实际数据暴露面，维持终局 deferred，Successor Required: **no**
- [x] **P2-MA5-301 裁决（Decision）**：live 复核 orm.xml:383/:392 双拼写现状 + GraphQL 暴露面（`rg -n "datasourceType|dataSourceId" nop-metadata -g "*.xmeta" -g "*.xbiz" -g "*.java" -g "!**/target/**" -g "!**/_gen/**"`）+ 消费方扫描（外部模块引用）+ 契约测试影响面（TestNopMetaBizInterfaceCompleteness 是否含该字段）——裁决 = 终局 deferred（破坏性契约变更，归类 out-of-scope improvement，Successor Required: no，命名规范非 live defect）或提升修复（新消费者依赖不一致 → 统一拼写 model-first + 迁移）——裁决记录：**终局 deferred（out-of-scope improvement）**。live 复核证据：(a) 双拼写仍在——orm.xml:383 `<column code="DATA_SOURCE_ID" ... name="dataSourceId" ...>` / :392 `<column code="DATASOURCE_TYPE" ... name="datasourceType" ...>`；(b) GraphQL 暴露面——生成产物 `_NopMetaDataSource.xmeta:9,27`（dataSourceId）/ `:43`（datasourceType prop），`_NopMetaDataSource.view.xml:22/:34` 页面列引用两字段；(c) 消费方——Java getter/setter 面（测试 TestAggregationHelper:183 `ds.setDatasourceType` 等）+ 服务层参数命名（MetaDataSourceConnectionProcessor.withConnection(String datasourceType, ...)，IMetaDataSourceConnectionProcessor 公共接口）；契约测试 TestNopMetaBizInterfaceCompleteness 仅断言 I*Biz 方法签名（queryAggregation 11 参 :47），**不含 dataSourceId/datasourceType 字段断言**——统一拼写 = 实体 prop 改名（datasourceType→dataSourceType）= GraphQL 公开字段名破坏性变更 + Java getter 改名全模块同步 + 页面/契约测试同步，破坏性公开契约变更；命名不一致非 live defect（沿 07-003/R2.9 破坏性契约变更先例）；Successor Required: **no**
- [x] **P2-MA3-03 裁决（Decision）**：live 复核 upsertExternalTable 当前行为（外部表数据落 NopMetaTable 实体，`NopMetaDataSourceBizModel.upsertExternalTable`）+ metaSchema 列声明（orm.xml:1310）+ R3.19 UK 修复后的 UK 集现状（metaSchema 是否已入任何 UK；相关 UK 为 `UK_NOP_META_TABLE_MODULE_NAME`（orm.xml:1317，注意：**不存在名为 UK_NOP_META_EXTERNAL_TABLE_* 的 UK**，外部表复用 NopMetaTable 的 UK））——裁决 = 终局 deferred（metaSchema null 语义需列契约变更 + 存量迁移，归类 out-of-scope improvement，Successor Required: yes → 多 schema 专项 plan 或登记终局）或提升修复（若 null 语义已有业务裁定）——裁决记录：**终局 deferred（out-of-scope improvement）**。live 复核证据：(a) upsertExternalTable（NopMetaDataSourceBizModel.java:428-462）——按 (metaModuleId, tableName) 查候选集后在 **Java 层** 做 metaSchema 归一化匹配（normalizeSchemaForMatch null/空串/纯空白→null），命中则 update、未命中则 insert；(b) metaSchema 列声明——orm.xml:1310 `<column code="META_SCHEMA" ... name="metaSchema" precision="100" propId="18">` 可空列（无 mandatory）；(c) UK 现状——R3.19 修复后 `UK_NOP_META_TABLE_MODULE_NAME`（orm.xml:1316-1317）= (metaModuleId, tableName, isDelta)，constraint 属性已补，**metaSchema 未入任何 UK**，且全模型不存在 UK_NOP_META_EXTERNAL_TABLE_*（外部表复用 NopMetaTable UK）——多 schema 同名表必然撞 UK（insert 抛 duplicate-key）；修复需先裁定 metaSchema null 语义（可空列 MySQL/Oracle unique 允许多个 NULL 无法阻止重复 → NOT NULL/默认 '' 属列契约变更 + 存量迁移），涉 ORM 变更 Protected Area；当前无多 schema 部署场景（MA7.3/MA3.4 记录单 schema）；Successor Required: **yes → 多 schema 支持专项**（roadmap 已登记 successor 行，见 MR4 段 R4.2）
- [x] **R2.7（07-004）裁决（Decision）**：live 复核 AggregationRowDTO 移除后（R3.8）queryAggregation 返回路径现状 + 消费方扫描（`rg -n "AggregationResultDTO|getItems" nop-metadata -g "*.java" -g "!**/target/**" -g "!**/_gen/**"` + web 页面/frontend 消费）——裁决 = 终局 deferred（0 消费方强转确认，归类 watch-only residual，Successor Required: no）或提升修复（新消费方出现 → 定义行结构契约 DTO，破坏性变更评估）——裁决记录：**终局 deferred（watch-only residual）**。live 复核证据：(a) AggregationRowDTO 全仓 0 命中（rg 实证，R3.8 已移除）；(b) 返回路径——`AggregationResultDTO.getItems()`（api/dto/AggregationResultDTO.java:22）返回 `List<Map<String,Object>>` 扁平 alias-key Map（measure/dimension 别名→值）；(c) 消费方扫描——测试消费方全部按 Map 迭代（TestNopMetaJoinBizModel:72/101/182/242/287 `List<Map<String,Object>> items = result.getItems()`、TestNopMetaDtoResults:70-74、TestAggregationEntityJoinAndComplex 等），**0 消费方强转**；nop-metadata-web 无 frontend/页面直接消费 queryAggregation 产物（页面资源仅 CRUD view，rg 实证）；强类型化需先定义行结构契约 = 改变 GraphQL items 输出形状破坏性变更；无新消费方出现；Successor Required: **no**
- [x] **R2.9（07-03）裁决（Decision）**：live 复核 INopMetaTableBiz.queryAggregation 11 参签名现状 + TestNopMetaBizInterfaceCompleteness 契约测试仍钉死（queryAggregation 断言在 :47）+ GraphQL rpc 参数结构——裁决 = 终局 deferred（有意契约，归类 out-of-scope improvement，Successor Required: no）或提升修复（新需求出现 → DTO 化三件套同步）——裁决记录：**终局 deferred（out-of-scope improvement）**。live 复核证据：(a) INopMetaTableBiz.java:75 11 参签名仍在（`AggregationResultDTO queryAggregation(@Name("metaTableId") String metaTableId, ...`）；(b) 契约测试钉死——TestNopMetaBizInterfaceCompleteness.java:47 `assertDeclaresMethod(INopMetaTableBiz.class, "queryAggregation", 11)`（:54 为 importOrmModel 断言，R2.9 旧引用 :54 不准确，plan baseline 已勘误）；(c) 镜像实现 NopMetaTableBizModel.queryAggregation(:254) 同 11 参签名 + GraphQL rpc 参数结构一致——11 参签名为契约测试钉死的有意契约，功能完整无 live defect；DTO 化需接口签名/契约测试/GraphQL rpc 参数结构三件套同步 = 破坏性公开契约变更；无新需求出现；Successor Required: **no**
- [x] **P2-MA7.5-04 裁决（Decision）**：live 复核 MA7.5-01 修复后调度器事务边界现状（MetaQualityCheckpointScheduler.executeScheduledCheckpoint 错误存活返回 + 事务回滚路径），确认放大器已消除且无残余放大路径（读代码追踪 checkpoint 扫描→质量规则执行→提交/回滚调用链）——裁决 = 终局 deferred（watch-only residual，Successor Required: no）或提升修复（残余放大路径确认）——裁决记录：**终局 deferred（watch-only residual）**。live 复核证据：(a) 放大器消除——executeScheduledCheckpoint（MetaQualityCheckpointScheduler.java:201-221）try/catch 包 executeCheckpoint，checkpoint 级业务错误（NOT_FOUND/NOT_ACTIVE/NO_RULES 等）→ LOG.error + buildErrorResult 正常返回，**job 存活**（修复前异常经 invoker→JobFireResult.ERROR→LocalJobScheduler FAILED 永久死亡）；(b) 事务边界残余路径——NopMetaQualityCheckpointBizModel.save(:218-221) 在事务内 register、delete override(:229) 在事务内 unregister（:228-231）：save 回滚 → job 指向不存在 checkpoint → 下次 cron 触发 executeCheckpoint 抛 CHECKPOINT_NOT_FOUND → 被 (a) 捕获存活（仅 ERROR 日志噪音，checkpoint 重新保存时 registerCheckpoint 幂等自愈）；delete 回滚 → job 已移除但 checkpoint 仍在 → 调度静默丢失 **至重启自愈**（init() @PostConstruct :129-130 启动 scanner 重注册全部 ACTIVE checkpoint）；register/unregister 失败已有日志留证——NopMetaQualityCheckpointBizModel.notifySchedulerRegister:242 / notifySchedulerUnregister:253 LOG.warn，MetaQualityCheckpointScheduler 侧 register-failed :151/:175、remove-stale-job-failed :285 为 LOG.error；(c) 调用链追踪：cron 触发→executeScheduledCheckpoint→executeCheckpoint（质量规则执行）→异常→LOG.error+存活返回，无残余放大路径（无永久死亡、无静默丢数据、无外部副作用）；低概率（事务回滚）+ 可自愈（重启/重存），watch-only residual；Successor Required: **no**
- [x] **P2-MA7.5-05 裁决（Decision）**：live 复核质量检查点执行幂等现状（是否有任何幂等保护；重复触发后果）——裁决 = 终局 deferred（需 ORM 变更 + 分布式锁设计，Protected Area，归类 out-of-scope improvement，Successor Required: yes → 专门调度可靠性 plan 或登记终局）或提升修复（确认 live defect 路径）——裁决记录：**终局 deferred（out-of-scope improvement）**。live 复核证据：(a) 幂等现状——NopMetaQualityResult 无 checkpointId/runId 列无业务 UK（MA7.5-05 审计 orm.xml:2034-2094 实证），QualityResultWriter.append 恒新增行，检查点执行无运行标记/锁；(b) 平台层单 JVM 守卫在位——LocalJobScheduler cron 自触发 WAITING 态门（RUNNING 跳过本 tick）+ fireNow running 检查，单实例自重叠被抑制；(c) 重复触发后果——手动 GraphQL 双击同一检查点无守卫 → 重复结果行 + 重复 QualityScore 行 + 重复 webhook/notify 投递（真实外部副作用）；多实例部署无 DB 分布式锁 → 每实例每 tick 各执行一次；(d) 修复 = ORM 变更（runId/checkpointId 列 + 复合 UK）+ 执行入口运行标记（乐观锁/进程内锁），涉 ORM 变更 Protected Area + 分布式锁设计工作；当前单实例部署 + 平台守卫覆盖主路径，重复投递面需配置触发（手动双击/多实例扩展），非当前 supported baseline 的活跃缺陷路径；Successor Required: **yes → 调度可靠性专项**（roadmap 已登记 successor 行，见 MR4 段 R4.3）
- [x] 每项裁决后同步更新 arm-index §P2 对应行（终局归类 + Why Not Blocking Closure + Successor Required）——执行结果记录：arm-index §P2 8 行全部追加 MR4 终局裁决注记（07-004 行/R2.9 行/P2-MA3-02 行/P2-MA3-03 行/P2-MA5-301 行/P2-MA7.2-02 行/P2-MA7.5-04 行/P2-MA7.5-05 行），roadmap MR4 段「MR4 终局裁决记录」8 项完整登记（终局归类 + Why Not Blocking Closure + Successor Required + successor 行引用 R4.2/R4.3）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 8 项全部有终局裁决结论，每项归类符合 Allowed Deferred Classifications（或提升修复），Why Not Blocking Closure 具体且基于 live 复核（非复制旧文）
- [x] data-auth 族裁决基于**本次 live 复核**的激活状态（application.yaml + CFG 默认值 + 判定链），未沿用旧结论
- [x] 无已确认 live defect 被降级为 deferred（裁决记录逐项声明）
- [x] 裁决记录 repo-observable（roadmap R4.1 段 + arm-index §P2 对应行）
- [x] `No new test required`: 纯裁决文档工作（Phase 2 无代码变更；提升修复项由 Phase 3 补测试）——除非裁决确认 live defect，则违反时立即转 Phase 3 并补测试
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 裁决提升项修复（条件执行）

Status: completed
Targets: 按 Phase 2 裁决结果确定（预期无提升项或 ≤2 项；涉 ORM 时 `nop-metadata/model/nop-metadata.orm.xml` + model-first 重新生成）

- Item Types: `Fix | Proof`

- [x] 若 Phase 2 无提升项：记录 "无提升项，纯裁决计划"（Phase 3 标记 completed，不走代码变更路径）——执行结果记录：**无提升项，纯裁决计划**——Phase 2 8 项裁决全部为终局 deferred，0 项提升修复；归类口径：watch-only residual ×4（P2-MA3-02、P2-MA7.2-02、R2.7〔07-004〕、P2-MA7.5-04）+ out-of-scope improvement ×4（P2-MA5-301、P2-MA3-03、R2.9〔07-03〕、P2-MA7.5-05），全部附 live 复核 Why Not Blocking Closure + Successor Required（no ×6 / yes ×2，yes 项已挂 roadmap R4.2/R4.3 successor 行）；无 live defect 被降级（裁决记录逐项声明）；Phase 3 不走代码变更路径，标记 completed
- [x] 若存在提升项（按 Phase 2 裁决清单）：对每项先复现再修复（记录 pre-fix 基线）——执行结果记录：N/A（Phase 2 无提升项）
- [x] 提升项修复落地：涉 ORM（P2-MA3-03/P2-MA5-301 可能）走 model-first（改 orm.xml → `./mvnw clean install -DskipTests` 重新生成 → 核对 `_gen/` 与 deploy/sql DDL，禁止手编生成产物，AGENTS.md Hard Stop）；data-auth 族（P2-MA3-02/P2-MA7.2-02）按跨 executor 管线设计落地——执行结果记录：N/A（Phase 2 无提升项；无代码变更，无 _gen/ 产物变更）
- [x] 每个提升项修复补行为回归测试（Test-Mandated Feature Rule），区分性断言（验证修复新增语义）——执行结果记录：N/A（Phase 2 无提升项，`No new test required`: 纯裁决文档计划）
- [x] 运行 `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` 后运行 `./mvnw test -pl nop-metadata -am -T 1C`，绿色基线保持（nop-metadata 子树 0 failures；计数与 MR3 收口口径对比时注意 857 为 `-pl nop-metadata` 无 -am 口径，-am 范围按重新实测记录并归因）——执行结果记录：本 plan 零代码变更（git diff 仅 ai-dev/ 文档），构建验证实测记录如下——**(a) nop-metadata 子树（MR3 收口口径 `-pl nop-metadata -T 1C`）**：BUILD SUCCESS，**858 tests / 0 failures / 0 errors / 0 skipped**（service 857 + web 1；MR3 收口 857 为 service 口径——NopMetadataWebPagesTest 在 MR3 时点受 xview 回归影响未计入，82dbd170c 修复后计入，0/0/0 保持）；**(b) `-pl nop-metadata -am -T 1C` 全 reactor 实测（clean HEAD 归因，3 次运行）**：nop-metadata 8 子模块全部 SUCCESS，上游依赖模块存在 3 项 pre-existing 失败（均非本 plan 引入——本 plan 零代码变更，git status 实证）：① `nop-xlang` TestFeatureConditionEvaluator.testVirtualNode（07-26 日志已文档化 pre-existing，clean HEAD 隔离复现，expected 3 vs 1）；② `nop-wf-service` RefactorWf.refactorName（**MR4 执行期新发现 pre-existing 破坏性测试**——clean HEAD 隔离复现：一次性迁移测试 NPE 于 approval-form/v1.xwf（无 `<start>` 节点），且执行副作用改写 11 个 tracked fixture 文件，已 `git checkout --` 恢复，工作树归零；本 plan 未修，登记为 pre-existing 归 MV/后续 wf 域处置）；③ `nop-stream-rocksdb` TestRocksDBIncrementalRestoreAndBenchmark（08-05 日志已文档化性能基准 flaky，ratio 阈值 1.045 vs <1.0，单跑 PASS）；`./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` 编译链验证：nop-metadata 8 子模块 SUCCESS（-am 上游模块 test 阶段失败不影响本 plan 零代码事实，MR3 同口径先例）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 无提升项时：明确记录 "纯裁决计划，Phase 3 无代码变更"，`No new test required`: 纯裁决文档计划——或
- [x] 有提升项时：全部修复 landed + 行为回归测试 + build/test 全绿（0 failures；pre-existing 失败按 MR3 惯例归因记录）
- [x] 接线验证（如适用）：提升项修复的运行时调用链连通已验证（数据鉴权过滤实际生效 / model-first 生成物一致）
- [x] 无静默跳过：新增/修改代码无空方法体/吞异常/静默返回
- [x] 文档变化：若提升项修复改变 public contract（如 GraphQL 字段名），同步 `docs-for-ai/` 对应 owner doc；否则 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口（roadmap R4.1 → done + arm-index 终态）

Status: completed
Targets: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR4 段）+ `ai-dev/audits/arm-index-nop-metadata.md`

- Item Types: `Decision | Proof`

- [x] 汇总 8 项终局裁决 + 提升项修复结果，写入 roadmap MR4 段裁决记录（每项：终局归类 / Why Not Blocking Closure / Successor Required / 对应 arm-index 行）；**Successor Required: yes 的项必须引用已存在的 successor 载体（已注册 plan 或 roadmap 登记行），无载体则改判 Successor Required: no（登记终局）——不允许悬挂 successor**——执行结果记录：roadmap MR4 段已写入「MR4 终局裁决记录」完整表（8 项：终局归类 / live 复核 Why Not Blocking Closure / Successor Required / 对应 arm-index 行）+ 跨维度冲突核对结论 + 无降级声明 + 提升项 0；**Successor Required: yes 共 2 项均有已登记载体**——P2-MA3-03 → roadmap R4.2（多 schema 支持专项，todo，Deps R4.1）、P2-MA7.5-05 → roadmap R4.3（调度可靠性专项，todo，Deps R4.1），无悬挂 successor
- [x] arm-index §P1/§P2 终态一致性核对（全部 P0/P1 finding 可追溯至 fixed 或 deferred 终态；MR4 裁决段登记）——执行结果记录：arm-index 已更新（顶部新增「MR4 终局裁决记录」块 + §P2 8 行追加 MR4 终局注记）；核对结论：P0 表 4 项全部 done/fixed（MA7.1-01 fixed 于 R3.1）；P1 表 9 项全部 fixed + 2 项 watch-only（MISSING-AUTH/16-01，历史裁定），P1 未闭包数 0；P2 表 8 项 Successor: MR4 项全部终局 deferred 且可追溯（MR4 裁决段 + roadmap R4.1 段双登记），无终态矛盾
- [x] roadmap R4.1 行 → done（注明 "MR4 跨维度裁决完成：8 项 Successor: MR4 项终局裁决 + 跨维度 P1 无冲突核对"，或按实际裁决结果注明）——执行结果记录：roadmap MR4 段 R4.1 行 → done（注明「MR4 跨维度裁决完成——8 项 Successor: MR4 项终局裁决（全部终局 deferred，0 提升）+ 跨维度 P1 无冲突核对」）
- [x] 独立子 agent closure audit（fresh session，closure-audit-prompt.md）：逐项核对本 plan 全部 Phase Exit Criteria + Closure Gates，证据写入本 plan Closure 段——执行结果记录：独立子 agent（fresh session，不同 task_id）按 `ai-dev/skills/closure-audit-prompt.md` 执行 closure audit，结果 PASS，证据见本 plan Closure 段

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap R4.1 → done，裁决记录完整可追溯（8 项终局 + 冲突核对结论）
- [x] arm-index §P1/§P2 与 roadmap 一致（终态无矛盾）
- [x] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [x] `./mvnw test -pl nop-metadata -am -T 1C`（若 Phase 3 有代码变更；纯裁决计划则记录 "无代码变更，MV V.1 承接全量验证"）——执行结果记录：Phase 3 无代码变更（纯裁决计划）；`./mvnw test -pl nop-metadata -T 1C`（MR3 收口口径）实测 BUILD SUCCESS，**858 tests / 0 failures / 0 errors / 0 skipped**（service 857 + web 1；MR3 收口 857 为 service 口径）；`-pl nop-metadata -am -T 1C` 全 reactor 实测 pre-existing 失败 3 项（nop-xlang TestFeatureConditionEvaluator / nop-wf-service RefactorWf / nop-stream-rocksdb benchmark flaky，均 clean HEAD 归因，本 plan 零代码变更无关，详见 Phase 3 验证记录）——按 plan Closure Gates「无代码变更，构建验证由 MV V.1 承接」语义，MV V.1 将执行全量验证；本 plan 已实测记录绿基线（nop-metadata 子树）
- [x] 文档变化：roadmap + arm-index 更新；docs-for-ai 不变（`No owner-doc update required`，除非 Phase 3 提升项改变契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯文档/裁决计划**：本 plan 为裁决文档计划（Phase 3 条件执行）。若 Phase 3 无代码变更，构建验证条目以 MV V.1 全量验证为收口（V.1 依赖 MR4 done，紧接本 plan 执行），本 plan 内以 `./mvnw test -pl nop-metadata -am -T 1C` 或明确 "无代码变更，无构建要求" 记录。

- [x] 8 项 Successor: MR4 deferred 项全部终局裁决（终局 deferred 归类合规或提升修复落地），无悬置（Successor Required: yes 项均有已存在载体或已改判 no）
- [x] 跨维度 P1 冲突核对完成，无未记录冲突
- [x] 无已确认 live defect 或 contract drift 被静默降级到 deferred（裁决记录逐项声明 + closure audit 核查）
- [x] 提升项（如有）全部 landed 带行为测试，无空壳/静默跳过
- [x] roadmap R4.1 → done + arm-index 同步（MV 输入就绪）
- [x] 独立子 agent closure-audit 已完成并记录证据（写入本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证裁决依据为 live 复核（非复制旧文）；（如有提升项）调用链运行时连通；（如无提升项）明确 "纯裁决计划，无代码"
- [x] `./mvnw test -pl nop-metadata -am -T 1C`（或记录 "无代码变更，构建验证由 MV V.1 承接"；若有代码变更，`-am` 范围 pre-existing 失败按 MR3 惯例归因记录）
- [x] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时；若 Phase 3 有代码变更必跑；纯裁决计划记录 "无新增代码"）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（修改 docs-for-ai/ 后必跑；本 plan 不预期修改 docs-for-ai，若 Phase 3 修改则必跑）

## Deferred But Adjudicated

### 8 项 Successor: MR4 项的终局归类（Phase 2 裁决产出）

- Classification: 逐项为 `watch-only residual` / `optimization candidate` / `out-of-scope improvement`（Phase 2 终局裁决后填写）
- Why Not Blocking Closure: 逐项填写（live 复核依据，非复制旧文）
- Successor Required: `yes | no`（逐项；yes 时填 Successor Path）
- Successor Path: 按裁决填写（如专门 data-auth plan / 多 schema 专项 / 专门调度可靠性 plan，或登记终局 no）

**8 项终局归类汇总（Phase 2 终局裁决产出，2026-08-05 live 复核）**：

1. **P2-MA3-02**（R2.14，data-auth 族）— watch-only residual；Why Not Blocking Closure：双开关全关（`nop.auth.use-data-auth-table` 默认 false NopAuthConfigs.java:69-70 + `nop.auth.enable-data-auth` 默认 false biz-defaults.beans.xml:16 + application.yaml:17 仅配 data-auth-config-path），DefaultDataAuthChecker:80-84 判定链两路 false，裸 DAO/EQL 路径（NopMetaTableBizModel:208/:234/:254）零 appendFilter 激活条件未满足；Successor Required: no
2. **P2-MA7.2-02**（R3.20，data-auth 族）— watch-only residual；Why Not Blocking Closure：与 P2-MA3-02 同族同依据（同一批裸 DAO/EQL 执行器 + 双开关全关 live 复核），沿 R2.14 先例；Successor Required: no
3. **P2-MA5-301**（R3.20，双拼写）— out-of-scope improvement；Why Not Blocking Closure：orm.xml:383/:392 双拼写仍在，统一拼写 = 实体 prop 改名 = GraphQL 公开字段破坏性变更 + Java getter 全模块同步 + 页面同步；契约测试无该字段断言，命名不一致非 live defect（沿 07-003/R2.9 先例）；Successor Required: no
4. **P2-MA3-03**（R2.15+R3.20，metaSchema ∉ UK）— out-of-scope improvement；Why Not Blocking Closure：orm.xml:1310 metaSchema 可空列，UK_NOP_META_TABLE_MODULE_NAME（:1316-1317）= (metaModuleId, tableName, isDelta) 不含 metaSchema，无 UK_NOP_META_EXTERNAL_TABLE_*；upsertExternalTable（NopMetaDataSourceBizModel:428）Java 层 schema 匹配，多 schema 同名表必然撞 UK；修复需 null 语义裁定 + 列契约变更 + 迁移 + ORM Protected Area；当前单 schema 部署无影响；Successor Required: yes → roadmap R4.2（多 schema 支持专项）
5. **R2.7**（07-004，未类型化 List\<Map\>）— watch-only residual；Why Not Blocking Closure：AggregationRowDTO 全仓 0 命中（R3.8 移除），AggregationResultDTO.getItems()（api/dto/AggregationResultDTO.java:22）扁平 alias-key Map，消费方全部按 Map 迭代 0 强转，web 无直接消费；强类型化 = items 输出形状破坏性变更；Successor Required: no
6. **R2.9**（07-03，11 参签名）— out-of-scope improvement；Why Not Blocking Closure：INopMetaTableBiz.java:75 11 参签名仍在，契约测试钉死（TestNopMetaBizInterfaceCompleteness.java:47）；DTO 化三件套同步 = 破坏性公开契约变更；11 参功能完整无 live defect；Successor Required: no
7. **P2-MA7.5-04**（R3.20，事务回滚副作用）— watch-only residual；Why Not Blocking Closure：MA7.5-01 修复实证（executeScheduledCheckpoint:201-221 存活返回）放大器消除；save 回滚残留 = 错误日志噪音 + 重存自愈（registerCheckpoint 幂等），delete 回滚残留 = 重启自愈（init():129-130 scanner 重注册）；日志留证（BizModel:242/:253 LOG.warn + Scheduler:151/:175/:285 LOG.error）；低概率可自愈无外部副作用，无残余放大路径；Successor Required: no
8. **P2-MA7.5-05**（R3.20，无幂等键）— out-of-scope improvement；Why Not Blocking Closure：NopMetaQualityResult 无 checkpointId/runId 列无业务 UK（orm.xml:2034-2094），平台 LocalJobScheduler WAITING 门 + fireNow running 检查覆盖单 JVM 自重叠；残余面（手动双击重复结果/重复 webhook + 多实例无分布式锁）需配置触发或多实例扩展，非当前单实例 supported baseline 活跃缺陷路径；修复 = ORM 变更（Protected Area）+ 分布式锁设计；Successor Required: yes → roadmap R4.3（调度可靠性专项）

## Non-Blocking Follow-ups

- P3-MA7.5-06（readAutoScoreConfig 静默 fail-open 补 LOG.warn，MA6.2-005 归口）与 P3-MA7.5-07（parseValidations 静默空集补 LOG.warn）为 MA7 已登记 P3 治理项，非本 plan scope，维持登记（非 live defect 路径）
- MV（plan-2026-08-05-1408-2）与 MG（plan-2026-08-05-1408-3）为后续批次，不在本 plan 执行
- **MR4 执行期新观察（pre-existing，非本 plan 引入，登记供 MV/后续处置）**：`nop-wf-service` RefactorWf.refactorName 为破坏性一次性迁移测试——clean HEAD 隔离复现失败（NPE 于 approval-form/v1.xwf 无 `<start>` 节点）且执行副作用改写 11 个 tracked fixture（已 git restore 恢复）；`nop-xlang` TestFeatureConditionEvaluator.testVirtualNode 为 07-26 已文档化 pre-existing 失败；`nop-stream-rocksdb` TestRocksDBIncrementalRestoreAndBenchmark 为文档化性能基准 flaky——均与本 plan 零代码变更无关，归 MV V.1 全量验证时按 MR3 惯例归因记录

## Closure

Status Note: MR4 跨维度裁决完成——8 项 Successor: MR4 deferred 项全部终局裁决（终局 deferred ×8：watch-only residual ×4 + out-of-scope improvement ×4，0 提升），跨维度 P1 冲突核对无冲突，roadmap R4.1 → done + R4.2/R4.3 successor 行登记，arm-index §P1/§P2 终态一致；纯裁决文档计划（Phase 3 无代码变更），nop-metadata 子树测试绿基线实测 858/0/0/0（service 857 + web 1，MR3 收口口径 service 857）；三轮独立子 agent closure audit（初轮/复轮 needs revision 全部 Blocker 修复 → 终局复验 PASS）后关闭。
Completed: 2026-08-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，三轮）
- Audit Session: 初轮 `ses_02f6110aaffeHC1zWTKcVyY3LU`（needs revision：3 Blocker——B1 Closure 证据缺失/12 Gates 未勾选、B2a 配置键误标（原误标键名，实为 `nop.auth.enable-data-auth`）、B2b LOG.warn 行号引用歧义、B3 Phase 3 测试运行声明未落地）；复轮 `ses_02f4cb21fffekDRVyPLzfW7o90`（needs revision：BLOCKER-1 为审计时点 Closure 段尚未写入（复轮审计早于本段写入的时序所致）、BLOCKER-2a plan:81 残余误标键名 1 处、BLOCKER-3 日志先行声明；其余全项 PASS）；终局复验 `ses_02f49a034ffezkv2gnHLnXIAP8`（**passes closure audit**，0 Blocker 0 Major，6 Minor 全部为文本级并在本轮修复：复验 session ID 回填 / Status Note 三轮叙事修正 / 日志同步 / 误标键名仅存于审计历史引述（已注明更正）/ 测试计数 858 勘误 / doc-links 12 条勘误）
- Evidence:
  - 每条 Phase Exit Criteria 的验证结果（PASS/FAIL + 对应 roadmap 行 / arm-index 行）：
    - Phase 1（门禁+归集+冲突核对）：PASS——MR1/MR2/MR3 check-plan-checklist --strict 均 exit 0 + roadmap MR1/MR2/MR3 段全 done；8 项归集与 arm-index §P2 一致；冲突核对无冲突（证据见 Phase 1 记录 + roadmap R4.1 段 + arm-index MR4 块）
    - Phase 2（8 项终局裁决）：PASS——8 项全终局 deferred（归类合规 + live 复核 Why Not Blocking Closure + Successor Required），data-auth 族基于本次 live 复核激活状态（application.yaml:17 + NopAuthConfigs.java:69-70 + biz-defaults.beans.xml:16 + DefaultDataAuthChecker:80-84），无 live defect 降级；裁决记录 repo-observable（roadmap MR4 终局裁决表 + arm-index §P2 8 行 + 本 plan Deferred 段汇总）
    - Phase 3（条件执行）：PASS（NA 路径）——无提升项，纯裁决计划，无代码变更（`No new test required`）；实测记录：`./mvnw test -pl nop-metadata -T 1C` 858/0/0/0 BUILD SUCCESS（service 857 + web 1，MR3 收口口径 service 857 一致）；-am 全 reactor 3 项 pre-existing 失败（nop-xlang TestFeatureConditionEvaluator / nop-wf RefactorWf（fixtures 已恢复）/ nop-stream-rocksdb benchmark flaky）clean HEAD 归因，工作树零代码变更
    - Phase 4（收口）：PASS——roadmap R4.1 done + 裁决记录完整可追溯 + R4.2/R4.3 successor 行（无悬挂）；arm-index §P1/§P2 终态一致；独立 closure audit 三轮完成，evidence 写入本段；测试记录 + 文档变化 + 日志条目齐备
  - 8 项终局裁决逐项证据（live 复核手段 + 归类 + Why Not Blocking Closure）：见本 plan Phase 2 各 item 记录 + Deferred But Adjudicated 段汇总（每项附 file:line 实证，closure audit 复轮抽查 15+ 处 line reference 全部匹配 live 代码）
  - 跨维度 P1 冲突核对结果：无跨维度冲突（契约测试无字段断言 / R3.19 UK 修复与 P2-MA3-03 无相互作用 / data-auth 终态与激活状态一致 / P0+P1 全部终态可追溯）
  - Anti-Hollow 检查结果：纯裁决计划无代码（git status 实证零源码变更）；裁决依据全部为 live 复核（配置扫描 + 代码路径追踪 + 消费方扫描 + 契约测试行号），非复制旧文；`scan-hollow-implementations.mjs --module nop-metadata --severity high` exit 0（0 findings，无新增代码）
  - Deferred 项分类检查：8 项全部符合 Allowed Deferred Classifications（watch-only residual ×4 / out-of-scope improvement ×4），无 in-scope live defect 被降级（closure audit 三轮逐项核查 PASS）
  - `check-plan-checklist.mjs --strict` exit 0（58/58 全勾选 + Closure evidence 已写入）/ `scan-hollow-implementations.mjs` exit 0（无新增代码）/ `check-doc-links.mjs --strict` exit 0（0 errors，12 条 BROKEN_LINK warnings 为 ai-dev 相对路径代码引用仓库级容忍）

Follow-up:

- no remaining plan-owned work——MV（plan-2026-08-05-1408-2，V.1 全量验证）与 MG（plan-2026-08-05-1408-3）承接后续批次；8 项终局 deferred 中 Successor Required: yes 2 项（P2-MA3-03→R4.2 多 schema 专项 / P2-MA7.5-05→R4.3 调度可靠性专项）已登记 roadmap 行
- 非阻塞观察（供 MV/后续处置）：nop-wf RefactorWf 破坏性测试 + nop-xlang TestFeatureConditionEvaluator + nop-stream benchmark flaky（均 pre-existing，零代码变更归因）

## Optional Sections

- `## Risks And Rollback`：裁决提升项若涉 ORM（P2-MA3-03/P2-MA5-301）为 Protected Area（plan-first，本 plan 即裁决载体），落地走 model-first 全量重新生成 + 生成物 git diff 归零核对兜底；data-auth 提升项（P2-MA3-02/P2-MA7.2-02）需先完成跨 3 executor 管线设计（设计文档先行），不允许执行期临场发明
- `## Outdated Note`：本 plan 的 8 项 deferred 终态以 Phase 2 live 复核为准；若执行期间对应项被其他工作改变（如 data-auth 开关被开启），立即中止对应裁决并上报
