# nop-persistence ORM 切片束 a（R4-18 G45 前半，15 成员）

> Status: draft
> Last Reviewed: 2026-11-10
> Source: plan 2552（`ai-dev/plans/2552-cra-phase8.58-r4-18-orm-slice-a.md`）Phase 1；批次定义 = batch-proposal.md §二 :133 R4-18 行；成员发现 = `ai-dev/audits/2026-10/2026-10-18-1521-deep-audit-nop-persistence-rw-concurrency/`
> PA：**无**（全部 15 项纯 Java 修复面 nop-orm/nop-orm-eql/nop-orm-model/nop-dao，零 `model/*.orm.xml` 触碰——零 orm 触碰声明）
> 前置：R2-14（plan 2517）done——世代号语义（裁定⑥）为 C1-008/C1-027 的 SSOT 消费不重建；`TestLazyLoadOrmModel.java:61 testConcurrentAddAndTopoRebuildKeepsAllEntities` **零弱化义务继承**（C1-016 回归钉）。
> 成员取证（fresh session @ 2026-11-10）：原样 12 + 部分覆盖 2（C1-018 机制锁面/99abdf8f28、C1-025 subTransactions 面/2517 裁定⑨）+ 已修 1（C1-002/2517 裁定④，Proof 登记）。

## 一、设计结论（十四条裁定）

1. **裁定 A（Proof 登记）**：C1-002 已修（2517 裁定④ session 全生命周期锁 + `TestCra2517SessionMutex`）。验收 = 该钉复绿，零新动作。
2. **裁定 B（TOCTOU 闭合，R1-Major-1 锁域修订）**：C1-004。close 侧 `needClose=true` 写与 `refCount<=0→doClose()` 判定、decRef 零判定、inc 侧 `tryIncRefIfOpen()`（synchronized 内查 needClose 再递增）**全部收进同一监视器**（三处均低频，无争用成本）——仅锁 inc 侧不闭合 TOCTOU（close 写 needClose 后锁外读 refCount 的交错仍存活）。`tryIncRefIfOpen` 加到 `ILoadedOrmModel` 接口（单实现、模块内）；`OrmSessionImpl` 构造器（SessionFactoryImpl.openSession :333-334 唯一构造型）改用之。拒绝方案：构造后二次校验（窗口仍在）。
3. **裁定 C（工厂可见性，R1-Low-5 消费方核验义务）**：C1-005。`SessionFactoryImpl.interceptors`/`daoListeners` 加 volatile；`daoListeners` check-then-act 改常驻 `MultiOrmDaoListener` 容器（`io/nop/orm/impl/MultiOrmDaoListener.java` 已存在；创建后只做内部列表变更，读侧恒非 null 单引用）。**实施步骤义务**：grep `getDaoListener()` 消费方逐一确认空集语义从 null→空容器不改变热路径行为。
4. **裁定 D（静态槽收紧切片）**：C1-006 + C1-024。`OrmTimestampHelper._instance` 与 `DaoProvider._instance` 加 volatile（同一「静态槽收紧」切片同 diff 落地）。
5. **裁定 E（批量装载队列可见性，R1-Low-6 锁界补钉）**：C1-007。`OrmBatchLoadQueueImpl.loadQueue`/`afterFlushCallbacks` 加 volatile；捕获-清空收进同一锁域（`synchronized(this)` 覆盖 makeQueue + flush 捕获清空段 :582-607 + afterFlush 注册段 :635-644）——**锁内禁止执行回调任务体**（afterFlush 的 task.run 与 invokeCallback 必须锁外执行，防监视器跨用户代码持有）。
6. **裁定 F（reloadModel 清查询计划缓存）**：C1-008(c) 面。`SessionFactoryImpl.reloadModel` 增加 `queryPlanCache.clear()`（消费 2517 裁定⑥世代号语义 SSOT——计划缓存全清为最小正确形态；世代校验细粒度方案 watch-only 登记）。(a)(b) 良性双编译面无动作。
7. **裁定 G（有状态解析单例契约钉定）**：C1-012。`EqlParseTreeParser` 移除 public `instance()` 静态入口，调用方（如有）改直接构造（grep 全仓生产调用方为零——本模块无生产调用方，纯 API 面收紧 + 文档钉定单线程契约）。若 grep 发现调用方则改为 per-call 构造。
8. **裁定 H（CompiledSql 发布前冻结，R1-Medium-4 锚点勘正）**：C1-013。`CompiledSql` 增 `freeze()`（`frozen` 标记）——全部 19 个 setter 前置 `checkFrozen()`（frozen 后抛 NopException，ErrorCode 新增 `OrmEqlErrors.ERR_EQL_FROZEN`）；**冻结点 = `EqlCompiler.compile`（:111-176 唯一装配点）return 前最后语句**（无 SqlCompiler 装配点；缓存插入在 nop-orm `LoadedOrmModel.compileSql` 包装层，冻结须在 return 前完成）。
9. **裁定 I（C1-016 方向一最小修 + 零弱化义务）**：`OrmEntityModel.dependByOtherEntity` 加 volatile（写点 `OrmModelTopEntryBuilder:35` / 读点 persist 驱动 HB 闭合）。**`TestLazyLoadOrmModel.java:61 testConcurrentAddAndTopoRebuildKeepsAllEntities` 零弱化义务**：本批任何 diff 不得削弱其断言，且该钉复绿为 Exit Criterion。方向二（伴生视图根治）watch-only 登记。
10. **裁定 J（冻结守卫族，R1-Medium-3 豁免修订）**：C1-017。`OrmEntityModel.addProp` 守卫提前到首行 + 先污染后守卫顺序修正；其余手写 mutator（:426-443 族）逐项补 `checkAllowChange()`（grep 全部公开 mutator 逐一登记——F3 失效面枚举义务）。**`setDependByOtherEntity` 显式豁免**（C1-016 方向一 sanctioned 运行期写点——加 volatile per 裁定 I，豁免注释 per 审计 C1-017 建议；加守卫会打断动态模型 topo 重建并触发 :61 零弱化钉红）。
11. **裁定 K（半注册回滚，R1-Info-8 范围声明）**：C1-018 残余。`LazyLoadOrmModel.putEntityModel`：先只读预检短名冲突再写入（预检-写入间隙由既有容器锁覆盖——机制锁面已由 99abdf8f28 落地）；失败抛点后不产生半注册（`topoEntryInited` 置位移到成功路径末尾，抛点消除后可达）。**同名重复注册静默覆盖 name 键的第二形态为范围外**（closure 注记确认）。
12. **裁定 L（init 原子化，R1-Low-7 形态补钉）**：C1-019。`OrmEntityModel.inited` 加 volatile；`OrmModel` 自身补 volatile 守卫 + init 同步块双检（原 init :133-146 无守卫标志，裸同步块不给 lock-free 读侧 HB——对齐 `OrmModelValidatorRegistry` DCL 形态）。
13. **裁定 M（事务状态可见性）**：C1-025 残余。`AbstractTransaction.opened`/`rollbackOnly` 改 volatile；`error` 写后同监视器发布（读写入 `synchronized(this)` 域，与 2517 裁定⑨已修面同锁）。
14. **裁定 N（查询缓存世代围栏，R1-Major-2 机制钉定）**：C1-027。**2517 裁定⑥的 GenerationFence 为 nop-orm 包私有类，nop-dao 依赖方向反向不可达**——本批钉定自包含形态：`JdbcTemplateImpl` 内每 cacheName 一把 `AtomicLong` 局部世代（epoch），`clearQueryCache`/`clearQueryCacheFor`/`evictQueryCache`（:235/:244/:250）时 bump；**getCacheData miss 时点捕获世代**（DB 读前，非 saveCacheData 内——彼时已在 executeQuery 之后），线程携带至 `saveCacheData` 回填前比对：世代不一致（读期间发生失效）放弃 `cache.put`。与 R4-19 C2-004 共享设计语义（世代围栏），机制独立（本批局部 epoch，跨层围栏=watch-only）。

## 二、C10 前置落位

| C10 | 状态 | 本批动作 |
|---|---|---|
| C10-003 | 部分兑现（2517 钉 C1-003 面） | C1-004/C1-008 修复同 diff 补钉 |
| C10-005 | 部分兑现（2517 钉 C2-004 面） | C1-027 补钉 |
| C10-008 | 已兑现（2517） | 无动作 |
| C10-011 | 开 | 随 C1-006/018/019/024/025 修复逐项补结构钉（反射 volatile/final 断言 + 半注册钉） |
| C10-012 | 开 | 随 C1-005/012/013/017 补钉 |
| C10-013/014/015/016/017 | 其他批次 | 不随本批 |

## 三、回归面清单

| 改动 | 回归 |
|---|---|
| 全部 | `./mvnw -o test -pl nop-persistence -am`（-am 反应堆含 nop-orm/nop-orm-eql/nop-orm-model/nop-dao 全量） |
| C1-016 | `TestLazyLoadOrmModel` 零弱化复绿（Exit Criterion） |
| C1-002/C10-008 | `TestCra2517SessionMutex`/`TestCra2517TxnProtocol` 复绿 |
| C10-003/005 既有钉 | `TestCra2517ModelVisibility`/`TestCra2517GenerationFence` 复绿 |

**E1**：`./mvnw -o clean install -DskipTests -T 1C -pl '!nop-runner/nop-cli-core,!nop-runner/nop-cli'` BUILD SUCCESS。
**E3**：`./mvnw -o test -pl nop-persistence -am`（含四模块全量）。
