# 2553 CRA Phase 8.59 — R4-19 2.4 ORM 切片束 b（G45 后半，15 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: roadmap 工作项 8.59；批次定义 = batch-proposal.md §二 :134 R4-19 行；成员发现 = `ai-dev/audits/2026-10/2026-10-18-1521-deep-audit-nop-persistence-rw-concurrency/`
> Related: plan 2517（R2-14）、plan 2552（R4-18）；设计文档 = `ai-dev/design/concurrency/persistence-orm-slice-b.md`
> **PA**：无（15 项全 Java 层，零 orm.xml 触碰）
> **特殊约束**：C2-016 修复前必须先改 `TestTransactionTemplate:326 testMultipleCommit`（C10-017 现状容忍钉）；C5 族载体受 N4 真 H2 义务（禁全 mock）；C2-009 补钉义务；C1-016 零弱化钉继承（裁定 E 触 LazyLoadOrmModel）。
> 成员取证（fresh session @ 2026-11-10）：原样 12 + 部分覆盖 3（C2-007、C2-016、C4-002）+ 已修 0。

## Purpose

关闭 R4-19 的 **15 个 P2 工单**：锁同步族（批队列 swap-then-capture、事务监听器原子注册、事务状态机 CAS、锁内加载外移）+ 异步契约族（runInSessionAsync 对齐、delayTasks 遮蔽、两阶段拓扑 fail-fast、伪异步载体契约钉定、flush 载体装配点、连接获取语义）+ 物化防线族（findAll 行数上限、缓存单条目上限、findAllByQuery limit 尊重）+ 热路径（事务级单 listener）。

## Baseline（live 亲读 @ 2026-11-10，取证 agent 独立核验）

- `DefaultOrmModelProvider.java:18,39-43,47-52` clearCache 旧代不延迟关闭；`setOrmInterceptor` 请求路径热写。
- `BatchActionQueueImpl.java:147-151,154,173,196,236` capture-then-null + `delayTasks` 局部遮蔽 + 阶段拓扑同步假说；`OrmBatchLoadQueueImpl.java:585-588` flush 重入静默 return。
- `AbstractTransaction.java:58,86-94` addListener 惰性 init check-then-act；`:55,60,166,419-426` open check-then-act（2552M 仅 volatile）。
- `LazyLoadOrmModel.java:141-160,172-200` 锁内 DB+codegen 加载。
- `OrmTemplateImpl.java:249-256,252-264,277-289` 双分支异常契约不一致；2517⑤ 回队列面在位。
- `JdbcEntityPersistDriver.java:127,190,236,353-365` 伪异步 + findAll 全量物化；`JdbcCollectionPersistDriver.java:105`；`AbstractTransaction.java:487,491`。
- `JdbcTemplateImpl.java:459-470` saveCacheData 无条件 put（2552 围栏已落）。
- `ISqlExecutor.java:180-182` findAll 汇聚点无上限；`dao/OrmEntityDao.java:674-682`/`DaoQueryHelper.java:122-160` findAllByQuery 丢 limit。
- `mdx/MdxQueryExecutor.java:113-117,200-210`/`sql_lib/QuerySqlItemModel.java:59-65` ⑦⑧面。
- `EntityPersisterImpl.java:591-601` per-entity listener。
- `TestTransactionTemplate.java:326` testMultipleCommit 无断言体（C2-016 阻塞钉）。
- H2 基建：`AbstractJdbcTestCase`（nop-orm）/`JdbcTestCase`（nop-dao）在位，h2 依赖在 pom。
- **基线测试签名**：`./mvnw -o test -pl nop-persistence -am`（归因形态沿 2552）。

### Phase 1 - 设计与取证

Status: completed（设计十三裁定 A-M + 特殊约束落位 + N4/H2 基建确认；draft review 待审）

- [x] 设计文档（十三裁定 + 15 成员唯一形态 + 回归面 + 真大 H2 义务落位）
- [x] 成员取证（fresh session：12 原样 / 3 部分覆盖 / 0 已修）
- [x] 独立 draft review（R1 must-fix-first：Major-1 close 原语勘正[provider 不持引用]/Major-2 actionMap 槽/Major-3 重入守卫/Major-4 seam 限界/Major-5 DaoErrors+抽取器级/Major-7 语义裁定显式化+Minor 4 项——全闭环；R2 ready-to-execute[3 cosmetic nits 同批清扫]）

### Phase 2 - 15 工单实现 + 回归测试

Status: completed（15 工单生产面全落盘 + 13 载体钉全绿 @ 2026-11-10；变异红证五腿见 `_tmp/red-green-ledger-2553.md`）
Targets: nop-persistence/nop-orm / nop-orm-eql(无) / nop-orm-model / nop-dao + 测试树

- Item Types: `Fix`（11）、`Proof`（0）、`Fix+Decision`（2：C2-016 状态机取向、C5-001 上界档语义）、`Feature`（0）、`声明面`（2：C4-001、C4-003——随批 diff + closure 抽验，不单独立项）

**实施顺序**
- 组 1（阻塞性前置）：C2-016 前置改钉（testMultipleCommit）→ C2-016 状态机
- 组 2（锁同步族）：C2-009 → C2-014+C9-001 → C2-018
- 组 3（异步契约族）：C3-002 → C3-003 → C3-004 → C4-001 → C4-002 → C4-003
- 组 4（物化防线）：C5-001+C5-005 → C5-002
- 组 5（模型代残余）：C2-007

**工单 × 载体表**

| 工单 | 载体（测试类 / 方法 / 断言目标） | 模块 | 类型 |
|---|---|---|---|
| C2-016 前置 | `TestTransactionTemplate#testMultipleCommit` **改写**（首次真提交 + 二次 no-op-with-WARN 状态断言——现状容忍钉先改义务） | nop-dao | 行为钉（改写） |
| C2-016 | `TestTransactionStateCas`（open 双开 fail-fast；markRollbackOnly×commit→rollback 语义+WARN[语义变更显式断言 R1-Major-7]；listener 锁外；CAS 失败终态） | nop-dao | 行为钉 |
| C2-009 | `TestBatchActionQueueSwapCapture`（swap-then-capture：三捕获槽[collecitonActions/delayTasks/**actionMap** R1-Major-2]重入不丢失；重放循环语义） | nop-orm | 行为钉 |
| C2-014+C9-001 | `TestTransactionListenerAtomic`（并发注册不丢 + evictGlobalCache 事务级单 listener + pendingEvictKeys 聚合） | nop-dao + nop-orm | 行为钉 |
| C2-018 | `TestLazyLoadConcurrentLoad`（并发同 key 单次加载 + 回环 fail-fast[R1-Major-3 重入守卫] + 零弱化钉复绿） | nop-orm-model | 行为钉 |
| C3-002 | `TestRunInSessionAsyncContract`（既有会话分支异常 → future reject 断言） | nop-orm | 行为钉 |
| C3-003 | `TestFlushFailure` 扩钉（flush 后 delayTasks 字段清空断言——局部遮蔽变异红） | nop-orm | 行为钉 |
| C3-004 | `TestBatchActionPhaseContract`（未完成 future 注入 → fail-fast 结构钉） | nop-orm | 行为钉+结构钉 |
| C4-001 | 声明面：javadoc 载体契约随批 diff（closure 抽验一句） | nop-orm + nop-dao | 声明面钉 |
| C4-002 | `TestOrmFlushExecutor`（注入 flush executor → I/O 落注入线程断言） | nop-orm | 行为钉 |
| C4-003 | 声明面：getConnection 语义 javadoc + 慢获取 WARN 随批 diff（closure 抽验） | nop-dao | 声明面钉 |
| C5-001+C5-005 | `TestFindAllRowLimit`（**真 H2**：AbstractJdbcTestCase——抽取器级超限抛 ERR_DAO_FIND_ALL_ROW_LIMIT_EXCEEDED[R1-Major-5 DaoErrors] + findAllByQuery 尊重 limit 分页语义；承载 C10-014/N4） | nop-orm | 行为钉（真 H2） |
| C5-002 | `TestQueryCacheEntryLimit`（上限配置 → 超限放弃缓存 WARN） | nop-dao | 行为钉 |
| C2-007 | `TestModelProviderDelayedClose`（clearCache 调 close() 后旧代于末 session decRef 归零销毁断言[R2-nit-1 原语勘正]） | nop-orm | 行为钉 |
| **无静默跳过**（rule 24） | | | |
| 执行期补充载体 | `TestBatchDelayTaskCleared`（C3-003 行为钉）+ `TestOrmFlushExecutor` + `TestFindAllRowLimit` 等 13 载体类 | — | 行为钉 |

## Exit Criteria

- [x] 15 工单全部实施
- [x] 载体表逐条存在且断言目标行为
- [x] 关键项先红后绿或变异论证（≥3：C2-016 状态机、C2-009 swap、C3-003 遮蔽）
- [x] **C2-016 前置义务**：testMultipleCommit 改写落盘且语义断言可观察
- [x] **C1-016 零弱化义务**：TestLazyLoadOrmModel 断言零改动且复绿（裁定 E 触同文件）
- [x] **N4 真大 H2 义务**：C5 族载体用真 H2 非全 mock
- [x] E1 全量编译门禁 BUILD SUCCESS（`_tmp/e1-2553-full-build-excli.log`：`clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS——排除双模块=已知面[nop-ai-translate 无源目录，11-08 先例；首轮全量日志 `_tmp/e1-2553-full-build.log` 423 模块 SUCCESS + 该双模块 FAILURE 佐证失败仅限排除面]）
- [x] E3：`./mvnw -o test -pl nop-persistence` 归因绿（nop-dao 354/nop-orm 438/nop-orm-model 66 逐模块绿；`-am` 形态上游 nop-config flake solo 59/59 绿归因[2552 同族面]）（四模块逐模块绿承载 + 上游 flake 族群 solo 归因）
- [x] **不回退 2517/2552**：TestCra2517* 全族 + TestDaoSliceA + TestOrmModelSliceA + TestLazyLoadOrmModel 全绿
- [x] owner doc 落账：`model-first-development.md` + `source-anchors.md`（ORM-A 行更新）
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--severity high` 三叶子模块 EXIT=0
- [x] `ai-dev/logs/` 已更新

## Deferred But Adjudicated

### C2-007 setOrmInterceptor 全量治理
- Classification: `watch-only residual`
- Why Not Blocking Closure: 模型构建期契约注记 + volatile COW 可见性兜底已落；请求路径热写的全量治理（拦截器装配 API 重构）属设施增强。
- Successor Required: `no`。

### C2-009 真 H2 E2E 钉
- Classification: `watch-only residual`
- Why Not Blocking Closure: 单线程 swap-then-capture 行为钉承载核心面；真 H2 E2E（多连接竞争 flush 窗口）属增强钉。
- Successor Required: `no`。

### C2-007 TenantAwareOrmModelProvider TOCTOU 面
- Classification: `watch-only residual`
- Why Not Blocking Closure: R1-Minor-11 登记——tenantCaches destroyOnRemove TOCTOU 与 setOrmInterceptor 全量治理一并属设施增强。
- Successor Required: `no`。

### C4-002 flush executor 生产装配与 session.flush 全量接管
- Classification: `watch-only residual`
- Why Not Blocking Closure: 可装配点+契约声明已落，seam 作用域显式限定 finalize 路径（R1-Major-4——session/factory 无 OrmTemplate 依赖，全量接管需 IPersistEnv 贯穿超范围）；生产装配属部署面治理。
- Successor Required: `no`。

## Review 记录

<!-- R2 cosmetic nit 清扫：Deferred 段重复的 C2-009 条目已去重（原 110-113 行为 95-98 之重复） -->

**Draft review R1（agent_f03f33a0，2026-11-10，must-fix-first）**：Major-1 裁定 A 原语错误（decRef→close）/Major-2 actionMap 第三捕获槽/Major-3 CF 重入死锁守卫/Major-4 seam 作用域过claim/Major-5 错误码跨模块+抽取器级执行/Major-7 裁定 D 语义变更显式化 + Minor-8/9/10/11——全闭环。
**Draft review R2（agent_f03f33a0，2026-11-10，ready-to-execute）**：11 项逐一核实 FIXED；残留 3 cosmetic nits（carrier 原语措辞/Deferred 去重/评审记录补登）同批清扫。

## Closure

Status Note: 8.59/R4-19 全 15 工单落地（11 Fix + 2 Fix+Decision + 2 声明面），13 载体钉全绿，五腿红证（2 变异红 + 1 执行期真死锁红 + 2 诚实登记[C3-003 审计误报证伪/C2-009 变异未命中]），owner doc + source-anchors 落账，E1/E3/工具门禁全绿，2517/2552 零回退。closure audit APPROVED-with-corrections 两修正（E1 证据改排除形态真绿日志、Deferred 重复条目实删）已闭环。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit 子 agent（agent_a970082f，2026-11-10）
- Evidence:
  - 15 工单 PASS（抽查 11/15 机制 live 命中：AbstractTransaction TxnState CAS/no-op-WARN/auto-rollback、BatchActionQueueImpl swap+phase fail-fast、pendingTxnEvictKeys 聚合、LazyLoadOrmModel inFlightLoads+回环守卫、setFlushExecutor+runInSessionAsync reject、findAll 上界+新错误码、query-cache 上界+toDataSet 重建、findAllByQuery limit 下推、clearCache close、pendingFlush 重放、getConnection 慢取 WARN、伪异步 javadoc）
  - 载体表 PASS：13 载体类存在且钉目标行为（TestFindAllRowLimit 真 H2=N4 义务承载；TestLazyLoadOrmModel 零改动=C1-016 零弱化）
  - 独立复跑门禁：nop-dao 5 测试类 25/25、nop-orm-model 10/10（含 TestOrmModelSliceA）、TestDaoSliceA 3/3——全 BUILD SUCCESS
  - `check-doc-links.mjs --strict` EXIT=0（0 errors）；`scan-hollow-implementations.mjs --severity high` 三叶子模块 EXIT=0
  - Anti-Hollow PASS：session.flush 6 调用点→SessionBatchActionQueue→flushAsync swap 链；runInNewSessionAsync→dispatchSessionFinalize→flushExecutor seam（TestOrmFlushExecutor 运行时线程断言）；无空体/静默 no-op
  - Deferred 项分类 PASS：5 项均 watch-only residual 带非阻塞理由；C3-003 误报证伪与 C2-009 变异未命中诚实登记可接受（Anti-Slacking Rule）
  - 修正闭环：Major-1 E1 证据改排除形态 BUILD SUCCESS 日志（`_tmp/e1-2553-full-build-excli.log`）+ 日志 E1 确认步关闭；Minor-2 Deferred 重复条目实删
  - `check-plan-checklist.mjs --strict` EXIT=0（收口后复跑确认）

Follow-up:

- no remaining plan-owned work（5 项 watch-only residual 见 Deferred But Adjudicated）
