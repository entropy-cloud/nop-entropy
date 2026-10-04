# 2321 full-code-review 2.3 nop-dao + nop-db-migration + nop-dbtool 深审

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 2.3（MA2）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 04/09/14 + 共享前缀）
> Related: plan 2314（1.7 已审 nop-persistence 分层边界）；plan 2318（2.1 orm 会话域，dim14-04 已登记 JdbcBatcher:185-189 成功路径回调无兜底 P3——本项 JdbcBatcher 复核的撞车对照）；plan 2319/2320（2.2 orm-eql，check2 orm-eql P0 already-fixed 先例）
> Skill 适配注记：三模块为 JDBC 执行与迁移基础设施——维度 04 → 方言模型与发现（DialectImpl/DialectModel 消费面/meta discovery）；维度 09 → 错误处理（DbMigrationErrors 错误码契约、NopSQLException/NopDaoException 两档策略、check2 :207 错误码错位线索域）；维度 14 → 事务与批量（txn 包/JdbcBatcher/JdbcTemplate 事务边界）。check2 点名复核：db-migration P0×2（:19 17 种 change 中 9 种解析即 CCE、:51 `<column>` 解析为 DynamicObject 遍历 CCE——**P0 即时通道候选，须实测**）+ nop-dao P1×2（:19 JdbcBatcher 负计数伪造为 1、:49 stopOnError=false 残留命令混合——roadmap 点名「负计数问题修复确认」）。nop-dbtool 嵌套组（core 7 main/1 test + web 0 文件空壳）小面并入迁移子 agent。
> 只读审计（P0 走异步注入）；check2/nop-dao.md 与 check2/db-migration.md 为 roadmap 点名的例外输入。
> Draft Review: agent_553dad60 首轮 4 Major（executor 实为 18 文件 16 执行器+executeMark 缺执行器、验证锚点漏 dbtool-core 且「无测试面」不实、dbtool-web 1.8 裁定引用失实（实指 nop-autotest-dbtool）、2.1 dim14 撞车对照不全）+ 5 Minor 全修；审查实测佐证 check2 四个点名条目的修复疑似已在 worktree 落地（xdef key-attr/InsertColumnModel/failRemainingCommands/isSupportBatchUpdateCount）——「以 live code 为准标定」兜住。

## Purpose

执行 roadmap 工作项 2.3：深审 nop-dao（JDBC 执行/方言/事务）+ nop-db-migration（迁移引擎与 17 种 change 执行器）+ nop-dbtool（core 小面 + web 空壳定位），产出报告 `2.3-dao-migration-dbtool-deep-audit.md`；复核 check2 点名条目（migration P0×2、JdbcBatcher P1×2）的 live 状态。**只读审计**（P0 走异步注入）。

## Current Baseline

- `nop-persistence/nop-dao`：main 116 / test 34。核心：JdbcTemplateImpl 857、DialectImpl 780（dialect/model 下 _gen 生成物豁免）、JdbcDataSet 606、AbstractTransaction 468、TransactionTemplateImpl 438；包：api/coderule/dialect/exceptions/initialize/jdbc/metrics/seq/shard/txn/utils。
- `nop-persistence/nop-db-migration`：main 103 / test 9。核心：MigrationEngine 415；executor/ 18 文件（16 个 change 执行器 + DdlSyntax + IChangeExecutor）；xdef（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/db-migration/migration.xdef`，只读输入）声明 17 种 change——**executeMark 无执行器**（check2「新发现缺陷」已记录未修，撞车对照）；model/_gen 豁免。
- `nop-persistence/nop-dbtool`：嵌套组——nop-dbtool-core（7 main/1 test）+ nop-dbtool-web（0 文件空壳；**无既有裁定**——1.8 P2-1.8-004「空壳漂移物化」实指 nop-autotest-dbtool 而非 dbtool-web，本项标定现状并给出归属建议）。
- check2 点名输入：`check2/db-migration.md` 16 条（P0×2：19/51；P1×4：82/104/148/179；P2×7；P3×3）；`check2/nop-dao.md` 18 条（P1×2：19/49 JdbcBatcher；其余 P2/P3 不进范围）。点名复核=两个 migration P0 + JdbcBatcher P1×2；migration P1×4/P2×7/P3×3 留 R1.0 裁决或 watch-only。check2 db-migration.md 末尾「新发现缺陷（仅记录，未修）」4 条（executeMark 无执行器、H2 USERS 系统表冲突、precondition INFORMATION_SCHEMA Oracle 不兼容、mssql remark 静默丢弃）均在审计面上——撞见时按「check2 已记录、标定重复关系」处理，不重复立案。
- 2.1 撞车对照：2.1 dim14 共 12 条中至少 4 条落在 nop-dao 文件——P3-2.1-024（JdbcBatcher :185-189 行号为本次对码，2.1 报告只登记 finding ID）、P3-2.1-029（TransactionTemplateImpl runWithoutTransaction）、P3-2.1-030（TransactionRegistry get-then-put）、P3-2.1-031（markRollbackOnly 零调用，ITransaction/AbstractTransaction）——本项复核 nop-dao/txn 时逐条对照，不重复立案（撞车以 finding ID 为主锚点）。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS；2.1/2.2 定向测试均绿（nop-orm 213 含 nop-dao 上游）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.3-dao-migration-dbtool-deep-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-2.3-<序号>`）+ 驳回台账（正文）+ 裁决。
- JDBC 执行面（维度 04+09）：JdbcTemplateImpl 执行链（SQL 渲染/参数绑定/结果集映射/异常转换）、DialectImpl 方言能力矩阵与发现 SQL、JdbcDataSet 游标/资源管理、seq（序列生成）、shard（分表路由）、coderule。
- 事务与批量面（维度 14）：txn 包事务编排（与 2.1 dim14 的 nop-orm 事务视图互补——本项审 nop-dao 侧实现）、JdbcBatcher 批量语义（分批/失败处置/计数——含 check2 P1×2 复核）、metrics 慢查询。
- 迁移面：MigrationEngine 执行链（发现/排序/执行/历史记录）；16 个 change executor + executeMark 缺执行器现状 逐个可达性与正确性（**含 check2 P0-1 实测**：构造 9 种 change 的 XML 走解析→执行器装配，确认 CCE 是否仍在）；insert/update/delete `<column>` 解析（**check2 P0-2 实测**）；历史表方言（check2 P1 :82 线索域——不预判，撞见按新发现）；DdlSyntax 方言硬编码域。
- dbtool 面：dbtool-core 7 文件职责与测试覆盖；dbtool-web 空壳现状标定与归属建议（无既有前案）。
- index 登记 + 独立 closure audit + roadmap 2.3 翻 done。

## Non-Goals

- nop-orm 会话/EQL 域（2.1/2.2 已审）；nop-orm-eql（含其 DML 渲染缺口簇——归 R1.0 簇D）。
- migration 的 DDL 方言库完备性证明（drivers 方言库属 nop-orm-drivers，1.7 已做结构边界）。
- check2 db-migration P1×4（:82/:104/:148/:179）与 P2/P3 的逐条复核（深挖撞见同域问题按新发现处理并注明与 check2 的重复关系）；check2 nop-dao 其余 16 条。
- nop-dbtool-web 空壳的处置裁定（无既有前案，本项只标定现状+建议）。

## Scope

### In Scope

- `nop-persistence/nop-dao/**`
- `nop-persistence/nop-db-migration/**`
- `nop-persistence/nop-dbtool/**`
- 只读输入：`ai-dev/audits/check2/db-migration.md`（P0×2 点名 + 标题与新发现缺陷撞车对照）、`ai-dev/audits/check2/nop-dao.md`（P1×2 点名 + 标题撞车对照）、2.1 报告发现清单（dim14 撞车对照）、`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/db-migration/migration.xdef`（17 种 change 口径锚）

### Out Of Scope

- nop-persistence 其余兄弟模块；数据库 drivers 内部；check2 其余语料的逐条标定（check2 不在 6.4–6.9 check 系列语料内，留 R1.0 裁决或 watch-only 归档）。

## Execution Plan

### Phase 1 - 深挖（deep-audit 迭代轮次，子 agent 并行）

Status: planned
Targets: nop-dao + nop-db-migration + nop-dbtool

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：三模块 main/test 行数榜（豁免清单：dialect/model/_gen）、JdbcTemplate 公开方法清单、17 种 change executor↔xdef 声明对照表、@Disabled 清单
- [ ] JDBC 执行子 agent（维度 04+09）：JdbcTemplateImpl/JdbcDataSet/DialectImpl/seq/shard/coderule + 错误处理两档策略（含 DbMigrationErrors 错误码契约域撞见即报）
- [ ] 事务批量子 agent（维度 14）：txn 包 + JdbcBatcher（check2 P1×2 复核——负计数伪造/残留命令混合，实测或逐帧推演；与 2.1 dim14-04 撞车对照）+ metrics
- [ ] 迁移子 agent（维度 09+14）：MigrationEngine 执行链 + 17 种 executor 逐个可达性（**check2 P0-1 实测**：构造 9 种 change XML 走解析→装配，确认 CCE live 状态）+ insert/update/delete `<column>`（**check2 P0-2 实测**）+ nop-dbtool core 7 文件 + web 空壳现状
- [ ] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-2.3-{jdbc,txn,migration}.md`

Exit Criteria:

- [ ] 三份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] check2 点名条目（P0×2 + P1×2）逐条标定结论落盘（still-live/already-fixed/false-positive/duplicated + 实测证据）
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 每维度派独立复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [ ] 统一 severity 判级；与 2.1（JdbcBatcher dim14-04 撞车裁定）/2.2（DML 簇边界）协调；check2 重复发现归属
- [ ] 迁移引擎正结论收敛（执行链连通性一条总评）

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-2.3-{jdbc,txn,migration}-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `2.3-dao-migration-dbtool-deep-audit.md`；index 登记 2.3 行
- [ ] 如有 P0（含 check2 P0 复核若 still-live）：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl nop-persistence/nop-dao,nop-persistence/nop-db-migration,nop-persistence/nop-dbtool/nop-dbtool-core -am`（无条件执行；dbtool-core 有活跃测试 TestJdbcMetaDiscovery 以其通过为准；如遇失败归因预存并登记）
- [ ] 独立子 agent closure audit；通过后 roadmap 2.3 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 定向测试 PASS，或失败已归因为预存失败并在报告/log 登记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [ ] 报告落盘且三模块面全覆盖（jdbc/txn/migration/dbtool）
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 2.3 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；视发现而定。check2 db-migration P1×4/P2×7/P3×3 与 nop-dao 其余 16 条留 R1.0 裁决通道或 watch-only 归档处理。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
