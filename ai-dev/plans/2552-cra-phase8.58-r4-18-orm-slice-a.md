# 2552 CRA Phase 8.58 — R4-18 2.4 ORM 切片束 a（G45 前半，15 工单）

> Plan Status: active
> Last Reviewed: 2026-11-10
> Source: roadmap 工作项 8.58；批次定义 = batch-proposal.md §二 :133 R4-18 行；成员发现 = `ai-dev/audits/2026-10/2026-10-18-1521-deep-audit-nop-persistence-rw-concurrency/`
> Related: plan 2517（R2-14，done——裁定④⑥⑨ 为 SSOT 消费）、plan 2518（R2-15，done）；设计文档 = `ai-dev/design/concurrency/persistence-orm-slice-a.md`
> **PA**：无（15 项全纯 Java 修复面，零 `model/*.orm.xml` 触碰——零 orm 触碰声明；batch 行 ORM plan-first 条款不触发）
> **C1-016 零弱化义务继承**：`nop-orm-model/src/test/java/io/nop/orm/model/lazy/TestLazyLoadOrmModel.java:61 testConcurrentAddAndTopoRebuildKeepsAllEntities` 断言不得削弱，复绿为 Exit Criterion。
> 成员取证（fresh session @ 2026-11-10）：原样 12 + 部分覆盖 2（C1-018/99abdf8f28、C1-025/2517 裁定⑨）+ 已修 1（C1-002/2517 裁定④ Proof 登记）。

## Purpose

关闭 R4-18 的 **15 个 P2 工单**：共享可变状态族（session TOCTOU、工厂可见性、静态槽、批量装载队列、事务状态、JWT 外热路径可见性）+ 发布安全（CompiledSql 冻结）+ 模型运行期（C1-016 HB、冻结守卫族、半注册回滚、init 原子化）+ 缓存面（reloadModel 清查询计划、查询缓存世代围栏）+ C10-011/012 随修补钉。

## Baseline（live 亲读 @ 2026-11-10，取证 agent 独立核验）

- `LoadedOrmModel.java:159-166` getLoadedOrmModel→incRef 与 close→doClose TOCTOU 原样。
- `SessionFactoryImpl.java:90,92,254-264,278-280` interceptors COW 无 volatile / daoListeners check-then-act；`:464-467` reloadModel 仅清 ormModelHolder。
- `OrmTimestampHelper.java:21,24,28` / `DaoProvider.java:11-17` 静态槽非 volatile。
- `OrmBatchLoadQueueImpl.java:69,205,212-214,584-599,636-641` 半 volatile。
- `EqlParseTreeParser.java:13-16` static final _instance + instance()（无生产调用方）。
- `CompiledSql.java:24-45,52-88` 全可变 POJO 公开 setter。
- `OrmModelTopEntryBuilder.java:35` 写点 / `OrmEntityModel.java:99,194-196` plain boolean / 读点 persist 驱动（C1-016；钉 :61 零弱化义务）。
- `OrmEntityModel.java:426-443` addProp 无守卫先污染；`:108,381,418` inited 非 volatile。
- `LazyLoadOrmModel.java:171-193` 短名冲突抛点 :180-182 先于 :193 置位（半注册残留）。
- `AbstractTransaction.java:53,58,59` opened/rollbackOnly/error plain（:110-134 subTransactions 面已修）。
- `JdbcTemplateImpl.java:92,236,245,251,407,437-453` saveCacheData :446-450 无条件 put。
- **基线测试签名**：`./mvnw -o test -pl nop-persistence -am`（-am 反应堆含四模块全量）。

### Phase 1 - 设计与取证

Status: completed（设计十四裁定 A-N + C10 前置落位 + C1-016 零弱化义务声明；draft review 待审）

- [x] 设计文档（十四裁定 + 15 成员唯一形态 + 回归面 + PA 零 orm 触碰声明）
- [x] 成员取证（fresh session：12 原样 / 2 部分覆盖 / 1 已修）
- [ ] 独立 draft review

### Phase 2 - 15 工单实现 + 回归测试

Status: planned
Targets: nop-persistence/nop-orm / nop-orm-eql / nop-orm-model / nop-dao + 测试树

- Item Types: `Fix`（13）、`Proof`（1：C1-002）、`Fix+Decision`（1：C1-027 含世代围栏消费决策）

**实施顺序**
- 组 1（工厂/静态槽）：C1-002（Proof）→ C1-004 → C1-005 → C1-006+C1-024（同 diff 切片）→ C1-007
- 组 2（模型运行期）：C1-016 → C1-017 → C1-018 残余 → C1-019
- 组 3（发布/缓存面）：C1-008 → C1-012 → C1-013 → C1-025 残余 → C1-027

**工单 × 载体表**

| 工单 | 载体（测试类 / 方法 / 断言目标） | 模块 | 类型 |
|---|---|---|---|
| C1-002 | Proof 登记：`TestCra2517SessionMutex` 复绿即验收 | nop-orm | Proof |
| C1-004 | `TestLoadedOrmModelCloseRace`（tryIncRefIfOpen：close 后 incRef 拒绝；并发 close×getSession 无负计数） | nop-orm | 行为钉 |
| C1-005 | `TestSessionFactoryVisibility`（interceptors/daoListeners volatile 结构钉 + 常驻 MultiOrmDaoListener 读侧非 null） | nop-orm | 行为钉+结构钉 |
| C1-006+C1-024 | `TestStaticSlotVisibility`（OrmTimestampHelper/DaoProvider `_instance` volatile 反射结构钉——承载 C10-011） | nop-orm + nop-dao | 结构钉 |
| C1-007 | `TestBatchLoadQueueVisibility`（loadQueue/afterFlushCallbacks volatile 结构钉 + 捕获-清空同锁域结构断言） | nop-orm | 结构钉 |
| C1-008 | `TestReloadModelClearsQueryPlan`（reloadModel 后 queryPlanCache 空——承载 C10-003 残面补钉） | nop-orm | 行为钉 |
| C1-012 | `TestEqlParserEncapsulation`（instance() 不可达结构钉：public 静态入口移除） | nop-orm-eql | 结构钉 |
| C1-013 | `TestCompiledSqlFreeze`（freeze 后全 setter 抛 NopException；freeze 后入缓存发布——承载 C10-012） | nop-orm-eql | 行为钉 |
| C1-016 | 既有钉 `TestLazyLoadOrmModel.java:61` 零弱化复绿 + `dependByOtherEntity` volatile 结构钉（承载 HB 补钉） | nop-orm-model | 行为钉+结构钉 |
| C1-017 | `TestFrozenModelGuardFamily`（addProp 守卫前置：冻结后 addProp 抛且 props 未污染；mutator 族逐一 checkAllowChange——承载 C10-012） | nop-orm-model | 行为钉 |
| C1-018 | `TestLazyPutEntityModelRollback`（短名冲突失败后无半注册残留；成功路径 topoEntryInited 置位可达） | nop-orm-model | 行为钉 |
| C1-019 | `TestOrmModelInitAtomic`（inited volatile 结构钉 + init 体同步断言） | nop-orm-model | 结构钉 |
| C1-025 | `TestTransactionStateVisibility`（opened/rollbackOnly volatile 反射钉——承载 C10-011） | nop-dao | 结构钉 |
| C1-027 | `TestQueryCacheGenerationFence`（失效世代推进后旧 miss 回填被放弃——承载 C10-005 残面补钉） | nop-dao | 行为钉 |
| **无静默跳过**（rule 24） | | | |

## Exit Criteria

- [ ] 15 工单全部实施（含 C1-002 Proof 登记）
- [ ] 载体表逐条存在且断言目标行为
- [ ] 关键项先红后绿或变异论证（≥3：C1-004 TOCTOU、C1-013 冻结、C1-027 世代围栏）
- [ ] **C1-016 零弱化义务**：`testConcurrentAddAndTopoRebuildKeepsAllEntities` 断言零改动且复绿
- [ ] E1 全量编译门禁 BUILD SUCCESS
- [ ] E3：`./mvnw -o test -pl nop-persistence -am` 四模块全量 0F/0E
- [ ] **不回退 2517/2518**：TestCra2517* 全族 + TestLazyLoadOrmModel 全绿
- [ ] owner doc 落账：`model-first-development.md` + `source-anchors.md`
- [ ] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--severity high` 逐叶子模块 EXIT=0（nop-orm / nop-orm-eql / nop-orm-model / nop-dao）
- [ ] `ai-dev/logs/` 已更新

## Deferred But Adjudicated

### C1-016 方向二（伴生视图根治）
- Classification: `watch-only residual`
- Why Not Blocking Closure: 方向一 volatile 最小修闭合 HB 面；方向二（运行期 topo 重建结果只入伴生视图不改写冻结 baseModel）属模型架构增强，本批钉（:61）已保证并发行为正确。
- Successor Required: `no`。

### C1-008 世代校验细粒度方案
- Classification: `watch-only residual`
- Why Not Blocking Closure: 本批 queryPlanCache 全清为最小正确形态（reloadModel 低频）；细粒度计划级世代校验属优化。
- Successor Required: `no`。

## Review 记录

（待 draft review）

## Closure

Status Note: （收口时填写）
Completed: （YYYY-MM-DD）
