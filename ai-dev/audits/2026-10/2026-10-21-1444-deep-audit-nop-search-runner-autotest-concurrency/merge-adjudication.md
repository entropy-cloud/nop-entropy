# 合并裁定 — nop-search-runner-autotest（WI 4.5，合并批 C1+C4+C6 + C10）

- **裁定员**: 独立合并裁定员（与三维度审计员、复核员、C10 审计员均不同 task）
- **日期**: 2026-10-21
- **输入**: 本目录三维度文件（`## 维度复核结论` + `## 最终保留项` + 各 round2 `## 复核结论`）+ `ai-dev/audits/cra-index.md`（P0 追踪 + P1 台账 WI 1.1–4.4）+ C10 审计（`C10-test-protection.md`）。未读其他单元目录详情；跨单元事实以 cra-index 台账行文为准。

## 一、裁定口径

1. **三态判定**：24 条复核保留发现逐条归入**缺陷族**（同根因 + 同失效层，单一工单单一修复批次）/ **独立**（独立根因，各自工单）/ **watch-only**（登记观察不立项），逐条落位无遗漏（§六权威表 24 行全映射）。
2. **复核已登记分组作为输入**：C4-001 唯一 P1（周期任务异常静默停摆）；Lucene 三资源族；Mock 替身族；C4-007 watch 模式面。三处分组与证据存在出入，本轮裁定修正如下（**冲突裁定 ×3**）：
   - **裁定 1**：输入写作"Lucene 三资源族（C6-001/002/003）"——C6-001 实为 CliWatchZipCommand 双重 init 泄漏（watch 域，非 Lucene 站点）。裁定 Lucene 资源族成员为 **C6-002/C6-003 + 同站点同根的 C1-001/C1-002**（三维度文件交叉引用："C6-003 与 C1-001 同域不同面"）；C6-001 归独立工单。
   - **裁定 2**：输入写作"Mock 替身族（C6-004/C4-006/C1-003）"——漏列 C4-003（Mock destroy 死旗标）。C4 文件明文"与 C1-003（时间/周期语义）、C6-004（队列残留/promise 挂起）同根不同面"——裁定并入，族 **4 成员**。
   - **裁定 3**：输入以"C4-007 watch 模式面"单列——C6-round2 明文"C4-007/C6-007 同根不同面（合并裁定不重复计数）"。裁定 **MA-02 双成员**覆盖该面，C4-007 不单列、C6-007 不重复计数。
3. **C10 缺保护登记**（9 条 P2）：不单开测试工单，**映射随本体工单**（§九）。
4. **CL×12 逐条处置**（§八）：并入本单元工单 / 独立线索 / 关闭确认 / 纯移交或并入跨单元。
5. **跨单元**：cra-index P1 台账（WI 1.1–4.4 全部行）+ P0 追踪逐行比对——本单元 24 条 + 12 CL **零同站点重复声明**；家族关联单列（§七）。
6. **统计口径**：24 条 → 15 张工单（5 族 + 9 独立 + 1 watch）；工单判级取成员最高严重度；P0 显式空执行。

## 二、缺陷族工单（MA-01~05）

### MA-01 Lucene topic 生命周期与并发协调族（P2，4 成员：C1-001、C1-002、C6-002、C6-003；随案 CL-010；watch 关联 C4-005）

- **根因**: LuceneSearchEngine 三 CHM（indexDirs/indexWriters/searcherManagers）条目生命周期无协调设施——获取路径 computeIfAbsent 畅通无守卫，变更路径（removeTopic/rollback/destroy）与在途引用、重建路径互不感知。
- **失效层**: 引擎条目管理（并发协调面 C1-001/C1-002、容量面 C6-002、停机面 C6-003）。
- **单修复批**: per-topic 读写协调（或 running 旗标 + computeIfAbsent 后二次校验）+ topic 上限/LRU 逐出（复用平台 ICache+CacheConfig 形态，逐出复用 removeTopic 三段关闭序列）+ ISearchEngine javadoc 线程契约声明。四个成员同设施同 diff 批次，**不得拆分**（C6-003 的协调设施与 C1-001 为同一设施，先修任一半都会留下另一半的逃逸窗口）。
- **修复约束**: 必须保持 S-04 已钉行为——rollback"先移出缓存再关闭"、dir 有意保留（TestSearchFixesDiscriminative.testTopicRecoversAfterFailedBatch 为正向约束，见 C10 §三）。
- **测试义务**: C10-001（并发协调三腿 + 容量腿）+ C10-007 交叠部分（三 CHM 关闭后尺寸断言）。
- **watch 关联**: C4-005（MergeScheduler 每 topic 线程/fd 放大）随本族治理观察，不单独立项（§四）。

### MA-02 watch 注册/注销与返回后残留族（P2，2 成员：C4-007、C6-007）

- **根因**: CliWatchCommand 丢弃 `watcher.watch()` 返回的 ICancellable（唯一注销通道），注入路径 finally 因 locallyOwned=false 跳过一切清理——WatchKey/handler 在共享单例注册表永久滞留，命令返回后脚本继续执行。
- **失效层**: 使用方生命周期编排（C6 执行步骤 5 注册表对称性 + C4-007 执行面）。
- **单修复批**: doWatch 接住 ICancellable + finally 无条件 `cancel()`（unwatch 只摘本调用 key，双路径安全幂等）；本地路径 watcher.stop() 保留为服务级兜底。
- **修复陷阱（C10 §三.2）**: 禁止以"无条件 watcher.stop()"统一两路径——触发 CL-011 共享服务被杀 footgun；修复 diff 必须同时补 C10-004 两腿测试（含"未调用共享服务 stop"断言）钉死修复形态。
- **测试义务**: C10-004。

### MA-03 watch-zip 产物并发损坏族（P2，2 成员：C1-004、C6-008）

- **根因**: CliWatchZipCommand 的 zip 产物更新无串行化与原子性——debounce 对在跑任务仅 cancel 不等待（平台 CL-005 根因侧）+ 写-写无 in-flight 守卫（C1-004）；平台 zipDirTo 就地覆盖写无 temp+rename（C6-008，写-读）。
- **失效层**: 同一命令同一产物的两个互补施害方向（C6-round2 明文"写-写 vs 写-读互补面，非重复"）。
- **单修复批**: in-flight 串行化守卫（AtomicBoolean + dirty 补偿执行）+ tmp 写入 + `Files.move(ATOMIC_MOVE)`；使用方守卫**不依赖** CL-005/CL-012 平台侧回流（回流为增强非前置）。
- **测试义务**: C10-005（三腿：幂等/原子/重叠）。

### MA-04 MockScheduledExecutor 替身契约族（P2，4 成员：C1-003、C4-003、C4-006、C6-004；冲突裁定 2 修正：并入 C4-003）

- **根因**: 测试替身相对 IScheduledExecutor 真实契约的系统性缺失——时间语义（DelayQueue 恒不过期/TimeUnit 丢弃）、生命周期语义（started 死旗标、destroy 不拒绝不排空）、取消语义（派生 stage cancel 不回传、清理回调挂错分支）。
- **失效层**: 替身契约（测试基建本体）。
- **单修复批**: 替身整体重写为单一 diff——入队锚定 nanoTime + started 守卫 + cancel 级联到源 promise（清理回调挂 whenComplete）+ destroy 排空并 completeExceptionally；或声明"仅支持 delay=0 手动驱动"边界并删死代码（两案择一留实现评审，不开放为 7.1 裁定项——修复方向两维度建议已一致指向修复优先）。
- **测试义务**: C10-003（替身契约五腿对称断言）。

### MA-05 autotest 全局状态清理对称族（P3，2 成员：C1-005、C1-009）

- **根因**: AutoTestCase 生命周期在方法边界的清理不对称——`complete()` 清理 t_vars/g_vars 路径存在但对 t_context 无对称清理（C1-009 为 C1-005 的 F3 同族补漏，同一 initDao 路径第四个 ThreadLocal）；全局池对并行/直用场景零防护。
- **失效层**: 测试期全局状态边界（条件化 P3：JUnit 并行默认关闭、类边界 beginTest/endTest 兜底已核实存在）。
- **单修复批**: `complete()` finally 增加 `BaseContextProvider.clear()`（对齐 ：187 IUserContext.set(null) 对称形态）+ "不支持并行测试类"边界 javadoc（与 C1-007 的边界声明义务合并落地）+ 可选 VarsScope 直用形态。
- **测试义务**: C10-006（三腿）。

## 三、独立工单（9）

| 工单 | 发现 | 判级 | 一句话 | 修复方向 | 测试义务 |
|---|---|---|---|---|---|
| I-C4-001 | C4-001 | **P1** | CliRunCommand 周期任务异常静默停摆（Future 丢弃 + listFiles NPE 触发面） | 包裹 runTasks catch+LOG（对照 CliWatchZipCommand:133-135 正确形态）+ 持有 ScheduledFuture 决定重排/非 0 退出 | C10-002（需先抽 stdin 可测 seam） |
| I-C4-002 | C4-002 | P2 | watch 脚本异常静默不可见（三 watch 类处理不对称） | processEvents catch+LOG 对齐兄弟形态 | C10-004 异常腿 |
| I-C6-001 | C6-001 | P2 | watch-zip 双重 init 覆盖泄漏（非守护线程放大为进程挂住） | init 幂等守卫或移除 call() 内手工 init | C10-005 幂等腿 + C10-007 句柄断言 |
| I-C1-006 | C1-006 | P3 | EntityRow 读侧裸读 + 集合逃逸 | 读侧 defensive copy 或不可变快照 + 单线程契约 javadoc | C10-008 异步探针腿 |
| I-C1-007 | C1-007 | P3 | autotest 全局静态集群无并行守卫 | beforeAll AtomicBoolean 并行 fail-fast + 边界声明（与 MA-05 声明义务同批落地） | C10-006 守卫腿 |
| I-C1-008 | C1-008 | P3 | buildDocument 回写调用方 doc 契约未声明（**吸收 CL-007**：async 命名交付同步语义，同一 SPI javadoc 义务单 diff） | 移除回写或 javadoc 声明突变 + SearchableDoc 所有权注记 + async 语义声明 | C10-009 |
| I-C4-004 | C4-004 | P3 | CliCallToolsCommand join() 无超时无取消 | FutureHelper.get(timeout) + 超时路径取消在途任务 | C10-008 挂死注入腿 |
| I-C6-005 | C6-005 | P3 | SourceRefactor loadFromResource 在 try 外 | 获取后首步纳入 try 范围（一行） | C10-008 坏文件腿 |
| I-C6-006 | C6-006 | P3 | SimpleDataSource 配置后未关闭（CL-008 契约联动） | try-with-resources/finally close（close 契约以 nop-dao 侧核对为准） | C10-008 close 计数腿 |

## 四、watch-only（1）

| 条目 | 判级 | 内容 | watch 理由 | 关联 |
|---|---|---|---|---|
| C4-005 | P3 | Lucene 默认 ConcurrentMergeScheduler 线程/fd 随 topic 基数线性放大 | 单 writer merge 线程为 Lucene 设计意图，无独立 OOM/耗尽证据链；维度文件本身定级"观察项"；治理点完全依赖 MA-01 的 topic 逐出落地 | 随 MA-01 治理观察；CL-010 随案并入 MA-01；MA-01 落地后本条关闭 |

## 五、开放裁定（7.1 输入）

1. **C4-001 修复批次归位**：本单元 P1 为使用方防线（catch + Future 持有 + 退出语义）；WI 2.1 C3-001 平台周期任务绝缘层（29 站点）为根因侧。若绝缘层先行落地，本工单降为站点验收面。建议与 2.1 修复批次对齐（同族第 3 处跨单元对齐先例：WI 1.6 C8-001、WI 2.2 C1-021 均并入 2.1 C3-001）。**7.1 终裁归批。**
2. **C6-001 条件化回升/降格条款**：双重 init 仅 CDI 工厂路径可达（@PostConstruct），jdk11 launcher 路径结构性不可达。维持 P2 立项（非守护线程放大后果为"进程挂住"）；若 CLI 打包形态裁定为唯一非 CDI 路径则降 watch-only。**7.1 确认。**
3. **MA-01 修复设施选型**：per-topic RW 锁 / 全局读写锁 / running 旗标+二次校验三案（C1-001 建议设施与 C6-003 建议设施合并为同一设施）。方案裁定留实现评审，**裁定前不得拆分 MA-01 分批修复**（拆分即留下另一半逃逸窗口）。**非缺陷级开放项，登记防拆分。**
4. **C10 测试义务归属**：9 条 C10 P2 随本体工单执行（本裁定）。若 Phase 8 批量修复不接纳测试义务，须在 7.1 显式改判并登记 successor，不得静默豁免（P1 缺陷 C4-001 的测试义务为硬性，对照 campaign 先例 plan 2479"回退实测必红"门）。

## 六、权威汇总表（24 → 15 工单全映射）

| 发现 | 判级 | 归属 | 工单判级 | cra-index 查重 | 跨单元关联 |
|---|---|---|---|---|---|
| C1-001 | P2 | **MA-01** | P2 | 零同站点 | — |
| C1-002 | P2 | **MA-01** | P2 | 零同站点 | — |
| C1-003 | P2 | **MA-04** | P2 | 零同站点 | — |
| C1-004 | P2 | **MA-03** | P2 | 零同站点 | CL-005/012 移交轨 |
| C1-005 | P3 | **MA-05** | P3 | 零同站点 | — |
| C1-006 | P3 | **I-C1-006** | P3 | 零同站点 | — |
| C1-007 | P3 | **I-C1-007** | P3 | 零同站点 | — |
| C1-008 | P3 | **I-C1-008**（吸收 CL-007） | P3 | 零同站点 | — |
| C1-009 | P3 | **MA-05** | P3 | 零同站点 | — |
| C4-001 | **P1** | **I-C4-001** | **P1** | 零同站点 | **族关联：WI 2.1 C3-001（+1.6 C8-001 同族）** |
| C4-002 | P2 | **I-C4-002** | P2 | 零同站点 | nop-core FileWatcher 中间层随 CL-001 移交 |
| C4-003 | P2 | **MA-04** | P2 | 零同站点 | — |
| C4-004 | P3 | **I-C4-004** | P3 | 零同站点 | — |
| C4-005 | P3 | **watch**（随 MA-01） | P3-watch | 零同站点 | — |
| C4-006 | P2 | **MA-04** | P2 | 零同站点 | — |
| C4-007 | P2 | **MA-02** | P2 | 零同站点 | CL-011 移交轨 |
| C6-001 | P2 | **I-C6-001** | P2 | 零同站点 | — |
| C6-002 | P2 | **MA-01** | P2 | 零同站点 | **族关联：F4 无界账本家族（1.1 C1-006 / 1.2 MA-05 / 1.4 MA-01 / 1.7 C7-003 / 3.1 MA-03）** |
| C6-003 | P2 | **MA-01** | P2 | 零同站点 | **族关联：WI 2.2 C1-009（destroy-vs-rebuild 逃逸族）** |
| C6-004 | P2 | **MA-04** | P2 | 零同站点 | — |
| C6-005 | P3 | **I-C6-005** | P3 | 零同站点 | — |
| C6-006 | P3 | **I-C6-006** | P3 | 零同站点 | CL-008 移交轨 |
| C6-007 | P2 | **MA-02** | P2 | 零同站点 | CL-011 移交轨 |
| C6-008 | P2 | **MA-03** | P2 | 零同站点 | — |

**合并统计**：24 条 → **15 张工单**（5 缺陷族 MA-01~05 吸收 14 条 + 9 独立 + 1 watch-only；族内吸收 14，独立 9，watch 1）。**工单判级分布：P0×0（显式空执行）/ P1×1 / P2×6 / P3×7 + watch-P3×1。** 本轮无降级、无驳回（三维度复核三态判定已为保留 24/降级 0/驳回 0，本轮维持）。冲突裁定 ×3（见 §一.2）。会红=0 缺保护登记 9 条随本体（§九）。

## 七、跨单元对账

**零同站点重复声明**：24 条发现 + 12 条 CL 对 cra-index P1 台账（WI 1.1–4.4 全部 156 行）与 P0 追踪逐行比对，nop-search / nop-runner / nop-autotest 主源码类在台账零出现；三维度复核员（C1/C4/C6 各自）的"跨单元对照 cra-index 台账零同站点命中"结论经本轮独立抽查维持。**零重复立项。**

**家族关联单列（3 处，关联不立项、不重复计数）**：

1. **周期任务停摆族**：I-C4-001（使用方面）↔ WI 2.1 C3-001（平台绝缘层，29 生产站点，P1 台账在案）↔ WI 1.6 C8-001（扫描循环停摆，同族已互链）↔ WI 2.2 C1-021（已并入 2.1 C3-001）。本单元为该族**消费方站点**；修复批次对齐见 §五.1。
2. **destroy-vs-rebuild 逃逸族**：MA-01/C6-003（引擎三 CHM）↔ WI 2.2 C1-009（JdbcConfigSource.close 停机后在途刷新重建永不关闭连接，P1 台账在案）。同根异站点，修复模式互证（running 旗标/二次校验关闭刚建资源）；两站点各自立项，零重复。
3. **F4 无界账本家族**：MA-01/C6-002（Lucene 三 CHM 用户可控键）为该族新站点，台账在案成员：WI 1.1 C1-006、WI 1.2 MA-05、WI 1.4 MA-01、WI 1.7 C7-003、WI 3.1 MA-03。族级修复纪律（上限+逐出+fail-fast 包络声明）对齐，站点独立。

**移交触点 8 处**（CL-001/003/005/006/008/009/011/012，逐条见 §八）：nop-core 轨 2（CL-001/011）、nop-commons 轨 2（CL-005/012）、nop-orm 轨 1（CL-003）、nop-dao 轨 1（CL-008）、平台生命周期轨 1（CL-009）、WI 2.1 站点核对 1（CL-006）。各接收轨在 cra-index 台账均无同站点条目，移交即首报。

## 八、CL 处置（×12 逐条）

| CL | 内容一句话 | 处置 | 落点 |
|---|---|---|---|
| CL-001 | FileWatcher.processing check-then-act 丢失唤醒（nop-core） | **纯移交** | nop-core 并发轨（WI 2.2 域）；台账无同站点，首报 |
| CL-002 | BuildIndexTool.indexAll 全文件读入无大小上界（C5 面） | **独立线索保留**（本单元） | admin 触达条件化，不立项；登记 §六 台账外线索，随 C10-001 容量腿观察 |
| CL-003 | IOrmSessionFactory add/removeDaoListener 并发契约（C2 面） | **纯移交** | nop-orm 轨（WI 2.4/2.5 域） |
| CL-004 | nop-autotest-dbtool 零主源码核对 | **关闭（确认性登记）** | 非缺陷；事实随本目录归档 |
| CL-005 | RateLimitExecutorImpl.debounce cancel 不 join（nop-commons） | **纯移交** | nop-commons 轨（WI 2.1 域）；MA-03 使用方守卫注明不依赖回流 |
| CL-006 | DefaultScheduledExecutor/globalTimer 本体（登记类） | **并入跨单元站点核对** | WI 2.1 C4-001（globalTimer 死配置，P1 台账在案）核对项；非新缺陷，零重复 |
| CL-007 | ISearchEngine async 命名交付 caller 线程同步语义（C3 面） | **并入本单元工单** | I-C1-008（同一 SPI javadoc 义务单 diff） |
| CL-008 | SimpleDataSource/JdbcMetaDiscovery 连接生命周期契约 | **纯移交** | nop-dao 轨（WI 2.4 域）；I-C6-006 修复以该契约为准（关联单列） |
| CL-009 | CoreInitialization.destroy 幂等契约与三调用点（登记类） | **纯移交** | 平台生命周期轨（WI 2.1/2.2 域核对）；与 2.2 C4-003 停机面家族相邻，零重复 |
| CL-010 | Lucene ConcurrentMergeScheduler 第三方线程模型 | **并入本单元工单** | MA-01（随 C6-002 治理收敛） |
| CL-011 | FileWatcher.stop() 停掉共享单例服务（休眠 footgun，nop-core） | **纯移交** | nop-core 并发轨（与 CL-001 同文件同批）；MA-02 修复注明禁止 stop() 兜底注入路径 |
| CL-012 | RateLimitExecutorImpl.debounce get/put 非原子窗口（CL-005 同类补充面） | **纯移交** | nop-commons 轨（与 CL-005 同类同批，非重复：cancel 语义 vs 竞态窗口） |

**CL 统计**：并入本单元工单 2（CL-007/010）、独立线索保留 1（CL-002）、关闭确认 1（CL-004）、纯移交/并入跨单元 8（CL-001/003/005/006/008/009/011/012）。驳回 0。

## 九、C10 缺保护映射（9 条随本体，不单开工单）

| C10 登记 | 判级 | 覆盖本体 | 随属工单 |
|---|---|---|---|
| C10-001 | P2 | C1-001/C1-002/C6-002/C6-003/C4-005 | MA-01 + watch C4-005 |
| C10-002 | P2 | C4-001（唯一 P1 零保护，最优先） | I-C4-001 |
| C10-003 | P2 | C1-003/C4-003/C4-006/C6-004 | MA-04 |
| C10-004 | P2 | C4-007/C6-007/C4-002 | MA-02 + I-C4-002 |
| C10-005 | P2 | C6-001/C1-004/C6-008 | I-C6-001 + MA-03 |
| C10-006 | P2 | C1-005/C1-009/C1-007 | MA-05 + I-C1-007 |
| C10-007 | P2 | C6 泄漏族回归锚（横切，与 C10-001/005 交叠部分不重复立项） | MA-01 + I-C6-001 + I-C6-005 + I-C6-006 + MA-02 |
| C10-008 | P2 | C1-006/C4-004/C6-005/C6-006 | I-C1-006 + I-C4-004 + I-C6-005 + I-C6-006 |
| C10-009 | P2 | C1-008 + CL-007 契约面 | I-C1-008 |

C10 矩阵统计备查：会红 0 / 假保护 12 / 无测试 12；反向钉死硬钉 0（正向修复约束 1：S-04；修复陷阱无守卫面 1：locallyOwned 测试不拦 footgun 修复变体）。系统性观察 3 条见 `C10-test-protection.md` §七。
