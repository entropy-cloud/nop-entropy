# 2553 CRA Phase 8.59 — R4-19 2.4 ORM 切片束 b（G45 后半，15 工单）

> Plan Status: active
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
- [ ] 独立 draft review

### Phase 2 - 15 工单实现 + 回归测试

Status: planned
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
| C2-007 | `TestModelProviderDelayedClose`（clearCache 后旧代 decRef 归零关闭断言） | nop-orm | 行为钉 |
| **无静默跳过**（rule 24） | | | |

## Exit Criteria

- [ ] 15 工单全部实施
- [ ] 载体表逐条存在且断言目标行为
- [ ] 关键项先红后绿或变异论证（≥3：C2-016 状态机、C2-009 swap、C3-003 遮蔽）
- [ ] **C2-016 前置义务**：testMultipleCommit 改写落盘且语义断言可观察
- [ ] **C1-016 零弱化义务**：TestLazyLoadOrmModel 断言零改动且复绿（裁定 E 触同文件）
- [ ] **N4 真大 H2 义务**：C5 族载体用真 H2 非全 mock
- [ ] E1 全量编译门禁 BUILD SUCCESS
- [ ] E3：`./mvnw -o test -pl nop-persistence -am` 归因绿（四模块逐模块绿承载 + 上游 flake 族群 solo 归因）
- [ ] **不回退 2517/2552**：TestCra2517* 全族 + TestDaoSliceA + TestOrmModelSliceA + TestLazyLoadOrmModel 全绿
- [ ] owner doc 落账：`model-first-development.md` + `source-anchors.md`（ORM-A 行更新）
- [ ] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--severity high` 逐叶子模块 EXIT=0
- [ ] `ai-dev/logs/` 已更新

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

### C2-009 真 H2 E2E 钉
- Classification: `watch-only residual`
- Why Not Blocking Closure: 单线程 swap-then-capture 行为钉承载核心面。
- Successor Required: `no`。

## Review 记录

（待 draft review）

## Closure

Status Note: （收口时填写）
Completed: （YYYY-MM-DD）
