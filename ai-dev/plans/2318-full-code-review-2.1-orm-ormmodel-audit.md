# 2318 full-code-review 2.1 nop-orm + nop-orm-model 深审

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 2.1（MA2）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 04/10/14/15 + 共享前缀）
> Related: plan 2314（1.7 已审 nop-persistence 分层边界——orm-model 91 文件全量扫描零泄漏正结论）；owner doc `docs-for-ai/02-core-guides/model-first-development.md`
> Skill 适配注记：两模块为 ORM 引擎本体（非业务实体模块），维度按引擎域适配——维度 04 → 会话状态机与实体生命周期（OrmSessionImpl 状态机/级联 flush/脏检查/集合语义，替代业务 orm.xml 扫描——后者归 2.5）；维度 10 → orm.xdef 与 nop-orm-model 模型装载正确性（OrmModelInitializer/OrmEntityModelInitializer 对 xdef 的消费面）；维度 14 → 事务与异步（flush/事务边界/OrmBatchLoadQueueImpl 异步装载/BatchActionQueueImpl）；维度 15 → session/dao 公开 API 泛型契约精度。check2 P1 复核为点名任务：复合主键 getIdText 非法 SQL、游标分页忽略 orderBy、SqlLibDictLoader.existsDict 前缀错（check2/nop-orm.md:19/43/66）——以 live code 为准逐条标定 still-live/already-fixed/false-positive。
> 只读审计（P0 走异步注入）；不读 `ai-dev/audits/`/`ai-dev/plans/` 等历史记录，check2/nop-orm.md 为 roadmap 点名的唯一例外输入。
> Draft Review: agent_2f7881d4 一次 PASS（3 Major：OrmCollectionHolder 幽灵类引用、check2 计数 19→20 勘正、orm.xdef 缺 canonical 路径；5 Minor 全修）

## Purpose

执行 roadmap 工作项 2.1：深审 nop-orm（ORM 会话/持久化引擎）+ nop-orm-model（ORM 元模型），产出报告 `2.1-orm-ormmodel-deep-audit.md`；复核 check2 已报 3 个 P1 的 live 状态。**只读审计**（P0 走异步注入）。

## Current Baseline

- `nop-persistence/nop-orm`：main 152 / test 137。核心类：OrmSessionImpl 1465 行、OrmEntityDao 812、OrmEntity 781、EntityPersisterImpl 760、OrmEntitySet 748、OrmBatchLoadQueueImpl 705、OrmTemplateImpl 555、SessionFactoryImpl 458、CascadeFlusher 449、DaoQueryHelper 450。
- `nop-persistence/nop-orm-model`：main 91 / test 6。核心类：_gen/_OrmEntityModel 1601（生成物豁免）、OrmEntityModelInitializer 730、_gen/_OrmColumnModel 729（生成物豁免）、OrmModelInitializer 521。
- 1.7（plan 2314）已建立分层边界正结论：orm-model 91 文件全量扫描零泄漏；dao→rpc-api 边不成立（勘误）；本项不重复维度 01 边界审计，聚焦行为语义。
- check2（`ai-dev/audits/check2/nop-orm.md`）20 条：P1×3 + P2×8 + P3×9，未修复跟踪；本项点名复核 P1×3，P2/P3 不进本项范围（异常项可上报 R1.0 裁决通道）。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS（含本两模块——两者均为基线前入库的老模块）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.1-orm-ormmodel-deep-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-2.1-<序号>`）+ 驳回台账（正文）+ 裁决。
- 会话状态机面（维度 04 适配）：OrmSessionImpl 实体生命周期（transient→managed→detached→removed）状态转移完备性与非法转移防护；实体缓存族（OrmSessionEntityCache/TenantOrmSessionEntityCache/StatelessOrmSessionEntityCache 一级缓存——持久化上下文核心）；级联 flush（CascadeFlusher）递归/并发修改窗口；脏检查（dirty checking）正确性与 flush 异常中断后的状态残留（check2 P2 线索：orm_flushVisiting 残留、orm_reset 别名共享——不预判，以复核口径重查）；集合语义（OrmEntitySet/CollectionPersisterImpl/JdbcCollectionPersistDriver 懒加载与 detach）。
- 模型装载面（维度 10）：orm.xdef（canonical：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm.xdef`，只读输入；注意仓内另有 4 个同名干扰文件——nop-kernel-cli demo delta、nop-ai-coder、nop-ai-dsl-orm、.opencode/skills）关键约束（id/pk/引用/级联声明）在 OrmModelInitializer/OrmEntityModelInitializer 的校验完备性——非法模型（环引用、无效 ref、重复列）响亮失败 vs 静默接受；LoadedOrmModel 派生结构（getIdText 等公开方法）一致性；SqlLibDictLoader.existsDict 字典装载路径（check2 P1-2 复核）。
- 事务与异步面（维度 14）：OrmTemplateImpl 事务边界与 afterCommit 语义；OrmBatchLoadQueueImpl 异步装载竞态（check2 P3 线索：flush 捕获 loadQueue 滞留）；BatchActionQueueImpl.flushAsync 失败路径；persister 层批量写一致性。
- 泛型与 API 面（维度 15）：IOrmSession/IOrmEntityDao/OrmEntity<T> 泛型契约精度；unchecked 转换热点；内部 DynamicObject 边界的类型安全。
- check2 P1×3 复核：逐条以 live code 标定 still-live / already-fixed / false-positive / duplicated；still-live 的进 R1.0 输入。
- index 登记 + 独立 closure audit + roadmap 2.1 翻 done。

## Non-Goals

- nop-orm-eql（EQL 编译器，2.2）、nop-dao/nop-db-migration/nop-dbtool（2.3）、nop-nosql/nop-cdc（2.4）。
- 全仓 orm.xml 业务模型机械扫描（2.5）；nop-orm-* 其他兄弟模块（orm-data/geo/pdm/rpc/drivers——1.7 已做结构边界审计，行为深审不在本项）。
- SQL 方言层完备性证明（drivers 面）；性能基准测试。
- check2 P2×8/P3×9 的逐条复核（仅当深挖独立撞见同域问题时按新发现处理，注明与 check2 的重复关系）。

## Scope

### In Scope

- `nop-persistence/nop-orm/**`
- `nop-persistence/nop-orm-model/**`
- 只读输入：`ai-dev/audits/check2/nop-orm.md`（仅 P1×3 点名条目）；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm.xdef`（schema 消费面核对）

### Out Of Scope

- nop-persistence 其余兄弟模块；EQL 编译与 SQL 生成语法层（2.2 域）；数据库 drivers 内部。

## Execution Plan

### Phase 1 - 深挖（deep-audit 迭代轮次，子 agent 并行）

Status: completed
Targets: nop-orm + nop-orm-model

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 机械基线：两模块 main/test 行数榜、测试类↔被测类映射表、@Disabled 清单、session/persister/loader 包结构清单
- [x] 会话状态机子 agent（维度 04 适配）：OrmSessionImpl 全状态机与 CascadeFlusher/EntityPersisterImpl 联动；实体缓存族一级缓存；脏检查；集合 detach（OrmEntitySet/CollectionPersisterImpl/JdbcCollectionPersistDriver）；flush 中断残留
- [x] 模型装载子 agent（维度 10）：nop-orm-model initializer 校验完备性 + orm.xdef 消费面 + LoadedOrmModel 派生方法一致性（含 check2 P1-1 getIdText、P1-2 SqlLibDictLoader.existsDict 复核——两处均在模型/装载路径）
- [x] 事务异步子 agent（维度 14）：OrmTemplateImpl/BatchActionQueueImpl/OrmBatchLoadQueueImpl/EntityPersisterImpl 批量写（含 check2 P1-3 游标分页复核——缺陷落点 DaoQueryHelper/OrmEntityDao）
- [x] 泛型 API 子 agent（维度 15）：session/dao/entity 公开契约 + unchecked 热点
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [x] 产物 `_tmp/fcr-2.1-{session,model,txn,types}.md`

Exit Criteria:

- [x] 四份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] check2 P1×3 逐条标定结论落盘（still-live/already-fixed/false-positive/duplicated + live code 证据）
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 每维度派独立复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [x] 统一 severity 判级；与 2.2（EQL/SQL 生成）/2.5（orm.xml 机械扫描）边界协调；check2 重复发现的归属裁定
- [x] 会话状态机正结论收敛（状态转移完备性/级联 flush 一条总评）

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-2.1-{session,model,txn,types}-review.md`；保留发现判级理由 + 证据可复现
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `2.1-orm-ormmodel-deep-audit.md`；index 登记 2.1 行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [x] 定向验证锚点：`./mvnw test -pl nop-orm,nop-orm-model -am`（无条件执行；预期锚点：Phase 1 机械基线实测 @Test 计数；如遇失败，归因预存失败并登记——全仓 test 基线未建立，与 V.1 同口径，不阻断只读审计 closure）
- [x] 独立子 agent closure audit；通过后 roadmap 2.1 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS，或失败已归因为预存失败并在报告/log 登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且两模块面全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 2.1 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；视发现而定。check2 P2×8/P3×9 不在 6.4–6.9 的 check 系列分批语料内，若未被本项独立撞见，留 R1.0 裁决通道或 watch-only 归档处理。）

## Closure

Status Note: 只读审计计划收口。报告 `2.1-orm-ormmodel-deep-audit.md` 落盘（P0=0/P1=2/P2=15/P3=41，裁决通过附 P1×2——detach 一缓契约混杂 + DELETING 装配重置，均进 R1.0）；check2 点名的 3 条 P1 全部 already-fixed（live 修复+回归测试双向实证）；定向测试全绿（nop-orm 210/skip 6、nop-orm-model 34，MVN_EXIT=0，2026-10-04 实跑）；closure audit 一次 PASS（agent_ca1c4b0a，13 处锚点实测全命中、三方计数勾稽无缺陷；2 条 advisory 文书级已随手落实——check2 台账补模块前缀、日志分桶漂移注记于本 Status Note）。日志条目按本会话在途惯例记录于 10-03.md（实际执行跨 10-03/10-04，报告与工件日期为 10-04）。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_ca1c4b0a-b113-4c58-9305-7f8ca6ff569c（fresh session，只读 closure auditor）
- Evidence:
  - 计数一致性：报告 P0=0/P1=2/P2=15/P3=41（总 58）↔ 报告内清单实点（P1 表 2 + P2 表 15 + P3 13+10+8+10=41）↔ index 2.1 行 0/2/15/41，三方逐位勾稽一致；四份复核文件权威合计 20+15+12+11=58 勾稽
  - 复核对账：dim04 抽验 8 条+13 条 P3 别名、dim10 全量 15 条与 _tmp/*-review.md 修订后口径零错位；dim04-17 降级落位 P2-2.1-004；P1 恰为 dim04-01/18
  - check2 台账：3 条 already-fixed 与 model-review（2 条）/txn-review（P1-3）独立验证一致
  - 条目质量：抽 9 条均 ≥9 行完整格式；file:line 锚点 13 处实测全命中
  - 定向验证：surefire 实测 nop-orm tests=210 failures=0 skipped=6、nop-orm-model tests=34（mtime 2026-10-04 17:55-17:56），与报告声称一致
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2318-full-code-review-2.1-orm-ormmodel-audit.md --strict` 退出码 0
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以两模块定向 test 全绿 + dim04-17 探针工件（_tmp/dim04-probe/）为锚
  - Deferred 项分类检查：无 deferred；P1×2 经 index「P1 发现汇总」进入 R1.0，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（P1×2/P2×15/P3×41 修复归属 R1.0/R1.x；check2 P2×8/P3×9 留 R1.0 裁决或 watch-only 归档；G.2 文档移交两项已登记报告「移交注记」）
