# C10 并发正确性的测试保护力 — nop-search-runner-autotest（WI 4.5）

- **审计员**: C10 独立审计员（WI 4.5 合并批 C1+C4+C6 复核完成后执行）
- **日期**: 2026-10-21
- **输入**: 本目录三维度最终保留项（C1×9 / C4×7 / C6×8，共 24 条，P1×1 + P2×14 + P3×9）+ 全部 round2 文件 + 现行 live 测试源码。盲审纪律：未读其他单元审计目录详情。
- **口径**: 按 `ai-dev/skills/concurrency-resource-audit-prompt.md` C10 正文执行——对每条保留发现做变异推演（"补上正确实现后，现有哪个测试会红？"）；变异结论三态：**会红**（修复实现错误或回退时现有测试必失败）、**假保护**（现有测试触达缺陷站点/路径但变异不可见，给出"已有覆盖"错觉）、**无测试**（缺陷路径在测试树零触达或被禁用）。

## 一、既有测试盘点（并发相关子集）

模块测试总量：43 个测试 Java 文件（nop-search 7 / nop-runner 22 / nop-autotest 14，含 dbtool 4 个纯工具测试）。

**真并发测试数 = 0。** 全测试树 grep 证实：`CountDownLatch` / `Awaitility` / 断言异步结果的并发编排 / 自建线程测试均为 0 命中；唯一 `Thread.sleep` 位于 `@Disabled` 的手工索引测试（TestLuceneSearchEngine.java:130）；无 `junit-platform.properties`（JUnit 并行默认关闭，与 C1-005/C1-007 的降档前提一致）。

并发相关测试清单及实际保护面：

| 测试文件 | 触达面 | 对 24 条缺陷的实际保护 |
|---|---|---|
| TestLuceneSearchEngine（nop-search-lucene） | addDoc/search/removeTopic 功能往返；@Disabled indexAll 含 sleep | 单线程功能往返；对 C1-001/C1-002/C6-002/C6-003 并发面零保护 |
| TestSearchFixesDiscriminative（S-02..06 判别腿） | rollback 自愈（S-04）、topic 隔离、fail-closed 授权 | **S-04 是 C1-001/C6-002 修复的正向行为约束**（见反向钉死节）；并发面零保护 |
| TestVectorSearch / TestLuceneFilterBeanTransformer | 向量/文本/混合检索功能 | 与 24 条缺陷无交集 |
| TestSearchEngineBizModel / TestSearchBizModelAccessControl（nop-search-core） | stub 引擎 wiring、searchAsync 委托、访问控制 | `testSearchAsyncDelegatesToEngine`(:169-181) 钉委托不钉线程语义（CL-007 面）；对 C1-008 回写零触达（测试部署无 ITextEmbedding bean，:1043 分支不进入） |
| TestBuildIndexTool | 录制 stub 的过滤/批量/enhancer | 对 CL-002（全量读入）零断言（无大小上界断言） |
| TestCliWatchZipCommand（nop-cli-core） | **仅参数校验与默认值**（3 个测试全部不启动 watch）；:68-69 明文承认回避 | 对 C6-001/C1-004/C6-008 主功能面零覆盖；plain 实例化使 @PostConstruct 双重 init 路径结构性不可达 |
| TestCliDiffAndWatchFixes | CliDiff 功能 + `watchCommandBuildsWatcherWithoutContainerInjection`(:97-105) | 仅钉**本地路径**（factory==null）locallyOwned+stop 契约；注入路径（C4-007/C6-007 缺陷所在）零触达；:103-104 注释自证知晓非守护线程 |
| TestCliCallToolsCommand | 工具调用 happy path | 对 C4-004 挂死路径零保护 |
| CliRunCommand / CliWatchCommand | **零测试引用**（全仓 grep 证实） | C4-001（唯一 P1）、C4-002 完全裸奔 |
| MockScheduledExecutor / MockTask | **全仓零测试引用** | C1-003/C4-003/C4-006/C6-004 四条全部无保护 |
| TestM01SpikeRecordingSession（nop-autotest-core） | EntityRow/AutoTestOrmHook 录制往返（含 getChangedData 断言 :71-72,:89-90） | 对 C1-006 读侧不对称零保护（单线程，defensive-copy 修复后仍绿） |
| TestNopJunitExtensionConfigRestore | afterAll 配置恢复（顺序语义） | 对 C1-007 并行翻动零保护；为 C1-007 修复的正向约束 |
| TestTestClockAnchoredSim | TestClock 锚点契约 | C1 锚点判定为干净，无需保护 |
| TestJdbcMetaDiscovery 等 dbtool 4 件 | **@Disabled**（:25）或纯 differ 工具 | 禁用测试不构成保护（C6-006 面） |
| TestSourceRefactor（nop-tool） | refactorDir 含 test.orm.xlsx（:39 扩展名白名单含 xlsx） | refactorOfficeFile happy path 被触达 → 坏文件泄漏路径（C6-005）零断言 |
| TestSourceCounter / 其余 | 单线程纯函数/一次性命令 | 与 24 条缺陷无交集 |

资源泄漏检测钩子（关闭后断言池空/句柄数/注册表空）：**全测试树 0 处**。大规模边界测试（topic 上限、队列满、maxParallelism 边界）：**0 处**。

## 二、保护力矩阵（24 行，缺陷 × 相邻测试 × 变异结论）

| # | 发现 | 严重度 | 缺陷一句话 | 相邻测试（触达证据） | 变异结论 | 反向钉死 | 缺保护登记 |
|---|---|---|---|---|---|---|---|
| 1 | C1-001 | P2 | removeTopic/rollback 与在途读写竞态 | TestSearchFixesDiscriminative S-04（rollback 自愈）、TestLuceneSearchEngine 往返 | **假保护**（单线程功能覆盖；并发协调修复后无任何测试变化） | 无（S-04 为正向约束） | C10-001 |
| 2 | C1-002 | P2 | addDocs delete+add+commit 非原子 | 同上 happy-path addDoc/addDocs | **假保护**（同 topic 并发写从不发生） | 无 | C10-001 |
| 3 | C1-003 | P2 | Mock 替身时间/周期/TimeUnit 契约三重破坏 | 零测试引用（全仓 grep） | **无测试** | 无 | C10-003 |
| 4 | C1-004 | P2 | debounce 重叠执行 + zip 并发写 | TestCliWatchZipCommand 仅参数校验 | **无测试**（watch 行为零驱动） | 无 | C10-005 |
| 5 | C1-005 | P3 | AutoTestVars 三件套 clear 竞态/ThreadLocal 残留 | 全部 JunitBaseTestCase 隐式穿其生命周期 | **假保护**（全覆盖、变异不可见——并行/直用场景无检测） | 无 | C10-006 |
| 6 | C1-006 | P3 | EntityRow 读侧裸读 + 集合逃逸 | TestM01SpikeRecordingSession（getChangedData 断言） | **假保护**（defensive-copy 修复后断言仍绿） | 无 | C10-008 |
| 7 | C1-007 | P3 | autotest 全局静态集群无并行守卫 | TestNopJunitExtensionConfigRestore（afterAll 顺序恢复） | **假保护**（顺序语义钉死、并行翻动不可达） | 无（该测试为修复正向约束） | C10-006 |
| 8 | C1-008 | P3 | buildDocument 回写调用方 doc 契约未声明 | 无（测试部署无 ITextEmbedding bean，回写分支不进入） | **无测试** | 无 | C10-009 |
| 9 | C1-009 | P3 | initDao t_context 方法级残留 | 全部 JunitBaseTestCase 隐式穿 initDao/complete | **假保护**（残留无任何断言） | 无 | C10-006 |
| 10 | C4-001 | **P1** | 周期任务异常静默停摆（Future 丢弃） | 零测试引用（CliRunCommand 全仓无测试） | **无测试** | 无 | **C10-002** |
| 11 | C4-002 | P2 | watch 脚本异常静默不可见 | TestCliDiffAndWatchFixes 仅建 watcher | **无测试**（processEvents 零驱动） | 无 | C10-004 |
| 12 | C4-003 | P2 | Mock destroy 生命周期无效 | 零测试引用 | **无测试** | 无 | C10-003 |
| 13 | C4-004 | P3 | join() 无超时挂死 | TestCliCallToolsCommand happy path | **假保护**（挂死工具注入场景零覆盖） | 无 | C10-008 |
| 14 | C4-005 | P3 | MergeScheduler 随 topic 基数放大 | 无 topic 数量/容量测试 | **无测试** | 无 | C10-001 |
| 15 | C4-006 | P2 | Mock 取消语义缺失（派生 stage cancel 不回传） | 零测试引用 | **无测试** | 无 | C10-003 |
| 16 | C4-007 | P2 | 注入路径命令返回后 watch 仍活跃 | TestCliDiffAndWatchFixes 仅本地路径 | **假保护**（正确一半被钉死、缺陷一半不可表达；见修复陷阱） | 无（但为 footgun 修复变体陷阱面） | C10-004 |
| 17 | C6-001 | P2 | watch-zip 双重 init 泄漏（非守护线程放大） | TestCliWatchZipCommand（plain 实例化 + tearDown destroy） | **假保护**（@PostConstruct 路径结构性不可达；init/destroy 表观配对） | 无 | C10-005 |
| 18 | C6-002 | P2 | topic 资源只增不逐出 | happy-path 测试均以 removeTopic 收尾（卫生假象） | **假保护**（无逐出/上限断言） | 无 | C10-001 |
| 19 | C6-003 | P2 | destroy 与在途 computeIfAbsent 交错逃逸 | 无停机在途流量测试 | **无测试** | 无 | C10-001 |
| 20 | C6-004 | P2 | Mock destroy 不排空、promise 永挂 | 零测试引用 | **无测试** | 无 | C10-003 |
| 21 | C6-005 | P3 | SourceRefactor 获取-守卫错位 | TestSourceRefactor（xlsx happy path，:39） | **假保护**（坏文件注入零覆盖） | 无 | C10-008 |
| 22 | C6-006 | P3 | SimpleDataSource 配置后未关闭 | TestJdbcMetaDiscovery @Disabled（:25） | **无测试**（禁用测试非保护） | 无 | C10-008 |
| 23 | C6-007 | P2 | ICancellable 丢弃、注册不注销 | TestCliDiffAndWatchFixes 仅本地路径 | **假保护**（同 C4-007） | 无 | C10-004 |
| 24 | C6-008 | P2 | zip 产物就地覆盖写非原子 | zipDir 在测试树零调用 | **无测试** | 无 | C10-005 |

**矩阵统计：会红 0 / 假保护 12 / 无测试 12（24 行全覆盖）。** 缺陷严重度与保护力倒挂：唯一 P1（C4-001）与 Mock 四条 P2 处于零测试象限。

## 三、反向钉死检查（钉死缺陷行为、修复即红的测试）

**硬钉命中：0 处。** 本单元不存在"把缺陷行为断言为正确"的测试——全部 43 个测试与 24 条缺陷的缺陷行为无正向断言交集。逐项排除说明：

1. `TestSearchFixesDiscriminative.testTopicRecoversAfterFailedBatch`（:114-138）：钉的是 **P1-4.4-S-04 修复后的正确行为**（失败 batch 后 topic 自愈、dir 有意保留）。C1-001/C6-002 的任何修复实现**必须保持**该行为——登记为**正向修复约束**（非钉死）：per-topic 协调设施的引入不得破坏 rollback 的"先移出缓存再关闭"序列与 dir 保留语义。
2. `TestCliDiffAndWatchFixes.watchCommandBuildsWatcherWithoutContainerInjection`（:97-105）：钉本地路径 locallyOwned 契约，与 C4-007/C6-007 修复兼容。但登记一个**修复陷阱无守卫面**：若修复者以"finally 无条件 `watcher.stop()`"统一两条路径，该测试**不会红**，而 stop() 经注入实例会杀死共享 watchService（CL-011 footgun）——正确修复形态（接住 ICancellable、只 cancel 不 stop）无测试护栏，错误修复形态亦无测试拦截。
3. `TestSearchEngineBizModel` 的 `assertSame(doc, engine.addedDocs.get(0))`（:196）与 `testSearchAsyncDelegatesToEngine`（:169-181）：钉单 doc 批次同一性与委托关系；C1-008 修复（移除回写）与 CL-007 契约声明（同步/异步语义）均不触碰这些断言。`future.get(5s)` 恒绿——caller 线程同步实现下立即返回，真异步实现下亦通过——**线程语义不可判别**（归入 C10-009）。
4. `TestVectorSearch.testEmbeddingField`（:182-198）：`assertArrayEquals` 按内容比较，防御性拷贝类修复不破坏。
5. `TestCliWatchZipCommand.testParameterDefaults`（:81-84）：钉 debounceWait=100/recursive=true 默认值；C1-004/C6-008 修复不改默认值。
6. `TestNopJunitExtensionConfigRestore`：钉 afterAll 配置恢复；C1-007 fail-fast 守卫（并行检测）不影响顺序路径。

## 四、时序敏感测试审查（C10 步骤 3）

- **Thread.sleep 同步**：仅 1 处（TestLuceneSearchEngine.java:130），位于 `@Disabled indexAll` 手工测试内——无平台性脆弱风险，登记卫生项。
- **未等待异步结果即断言**：0 处。唯一的异步断言（testSearchAsyncDelegatesToEngine :179）有 `get(5, SECONDS)` 超时等待——形态正确但因 caller 线程同步语义（CL-007）实际恒同步完成，不构成对异步行为的检验（见 C10-009）。
- **测试自己 catch 掉竞争（superficially-pass）**：0 处（无 try/catch 包裹断言形态）。
- **禁用测试**：2 处（TestJdbcMetaDiscovery :25 @Disabled、TestLuceneSearchEngine indexAll :95 @Disabled）——恰是本单元唯一触及真实资源契约（JDBC 数据源、全量索引）的两个测试，双双禁用，C6-006 由此落入"无测试"象限。

## 五、资源泄漏测试与大规模测试（C10 步骤 4/5）

- **泄漏检测钩子**：0 处。无"关闭后断言池空/句柄数/注册表空"形态。P1/P2 级资源缺陷（C6-001/C6-002/C6-003/C6-006）的建议测试形态随 C10-007 登记（fd 计数、watchKeyMap 尺寸、三 CHM 尺寸断言——对照 campaign 先例 plan 2479 的 TestFileDownloadSinkClose fd 计数形态）。
- **大规模边界**：0 处。topic 基数上限/逐出（C6-002）、DelayQueue 满（Mock 无界）、debounce 满速冲击（C1-004）均无小规模参数化模拟。C4-005 的建议形态（多 topic 参数化 + 线程/fd 计数）随 C10-001 登记。

## 六、缺保护登记（C10-001 起，均 P2，与缺陷本体分开计数）

### [C10-001] Lucene 引擎并发协调面零并发测试——MA-01 族四条 P2 与 C4-005 无一可被回归捕获

- **文件**: `nop-search/nop-search-lucene/src/test/java/io/nop/search/TestLuceneSearchEngine.java:43-93`；`nop-search/nop-search-lucene/src/test/java/io/nop/search/TestSearchFixesDiscriminative.java:114-138`
- **证据片段**:
  ```java
  // 全部测试均为单线程顺序往返：addDoc → search → removeTopic
  searchEngine.addDoc("test", doc);
  SearchResponse ret = searchEngine.search(request);
  assertEquals(1, ret.getTotal());
  searchEngine.removeTopic("test");
  // 全测试树：零 CountDownLatch、零并发 addDocs/search 交叠、零 destroy-在途流量场景
  ```
- **严重程度**: P2（按 C10 口径"并发测试缺失导致 P1 级缺陷无保护力"校准；本体最高 P2）
- **并发场景**: 缺陷本体涉及 GraphQL worker × 管理操作 × 停机线程的交叠（C1-001/C1-002/C6-002/C6-003），测试树从不构造任何第二线程。
- **触发时序**（变异推演）: 为 C1-001 补上 per-topic RW 锁（或为 C6-003 补 running 旗标、为 C6-002 补 LRU 逐出）→ 全部现有测试单线程顺序执行，锁/旗标/逐出对它们不可见 → 全绿；随后任何回退（删除协调）亦全绿——修复的存在性无任何测试证据。
- **现状**: 引擎测试仅覆盖单线程功能往返与 S-04 rollback 自愈；并发协调、容量上限、停机窗口三类行为零断言。
- **风险**: MA-01 修复后无回归护栏；后续重构（如更换缓存结构）静默移除协调逻辑的概率高（F3 同族复发路径）。
- **建议**: 补三类小规模参数化测试：(1) 双线程 addDocs 同 topic 同 docId × N 次 → 断言终态唯一（C1-002）；(2) removeTopic 与在途 search/addDocs 交叠（CountDownLatch 栅栏）→ 断言无 AlreadyClosedException 外泄（C1-001）；(3) destroy 后 computeIfAbsent 不入 map / 二次校验关闭（C6-003）+ topic 上限逐出断言（C6-002）；线程/fd 计数断言覆盖 C4-005。
- **失效面枚举（F3）**: 本登记覆盖本体 C1-001、C1-002、C6-002、C6-003、C4-005 五条（矩阵行 1/2/14/18/19）；随 MA-01 工单执行。
- **信心水平**: 确定
- **误报排除**: 非要求真实大规模压测——C10 口径明确"以小规模参数化模拟为主"；非"有功能测试即有保护"的表观判定——变异推演三态判为假保护/无测试。
- **复核状态**: 未复核（C10 单轮终止，随合并裁定移交）

### [C10-002] 唯一 P1（C4-001 周期任务静默停摆）零测试保护——CliRunCommand 全测试树零引用

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliRunCommand.java:89-99`（缺陷本体）；nop-cli-core 测试树（零 CliRunCommand 引用，全仓 grep 证实）
- **证据片段**:
  ```java
  // CliRunCommand:89-99 —— 返回的 ScheduledFuture 未接，异常静默抑制后续执行
  GlobalExecutors.globalTimer().scheduleWithFixedDelay(() -> {
      runTasks(globalState);
  }, interval, interval, TimeUnit.MILLISECONDS);
  try { System.in.read(); ...
  // 测试树：grep -rln "CliRunCommand" --include="*.java" . | grep "/test/" → 0 命中
  ```
- **严重程度**: P2（缺保护登记；本体 P1——本单元唯一 P1 处于零测试象限，为全部登记中最优先项）
- **并发场景**: globalTimer worker 周期执行 × 主线程 stdin 阻塞；runTasks 任意异常（脚本半编辑态、listFiles 瞬时 IO null → NPE）触发 JDK 周期抑制语义。
- **触发时序**（变异推演）: 修复（catch + LOG + 持有 Future 决定重排/退出）后无测试变红；回退（删除 catch）亦无测试变红——P1 修复的存在性零证据。
- **现状**: 本模块唯一 P1 缺陷所在类没有任何测试文件引用；listFiles NPE 触发面（R2 补充）同样零覆盖。
- **风险**: 修复后回归无护栏；且该缺陷在 WI 2.1 C3-001 平台绝缘层家族中有 29 处同族站点——本站点修复若无测试钉住，同族复发不可检测。
- **建议**: 测试形态——以可注入的 ScheduledExecutor 替身驱动 runTasks 抛异常 → 断言 (1) 异常被记录（LOG 断言或错误计数）；(2) 周期循环继续或进程以非 0 退出（二选一，按修复裁定）；(3) listFiles 返回 null 的目录形态不致 NPE 外泄。CLI stdin 阻塞点需先抽出可测 seam。
- **失效面枚举（F3）**: 本登记覆盖本体 C4-001 全部触发面（脚本异常 :90、listFiles NPE :111-112）；关联 WI 2.1 C3-001 家族测试义务。
- **信心水平**: 确定
- **误报排除**: 非替身简化豁免——登记对象是测试缺失本身；非"CLI 难测"豁免——CliDiff/CliCallTools 等同类命令已有可测形态先例。
- **复核状态**: 未复核（C10 单轮终止，随合并裁定移交）

### [C10-003] MockScheduledExecutor 替身契约零测试——四条 P2（MA-04 族全部成员）无任何保护

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/mock/MockScheduledExecutor.java:17-110`（缺陷本体）；全仓测试树（零 MockScheduledExecutor 引用）
- **证据片段**:
  ```java
  // 全仓 grep -rln "MockScheduledExecutor" --include="*.java" . | grep -v "/main/" → 0 命中
  // 缺陷本体（无任何测试触达）：
  private volatile boolean started = true;   // 死旗标
  public long getDelay(TimeUnit unit) { return unit.convert(initialDelay, MILLISECONDS); } // 恒不过期
  ```
- **严重程度**: P2（替身契约缺陷的假保护风险已被三维度确证，测试缺失使该风险无法被任何回归暴露）
- **并发场景**: 测试期替身被未来测试用作 IScheduledExecutor 注入点时，延迟任务静默不可触发/destroy 后照常入队/cancel 后照常执行——**替身缺陷的直接受害方就是测试本身**（假绿/假挂制造机）。
- **触发时序**（变异推演）: 修复替身（锚定 nanoTime + started 守卫 + cancel 级联 + destroy 排空）后无测试变化；回退亦无——替身契约的正确性完全依赖代码审读。
- **现状**: 唯一"测试基建自身是缺陷载体"的类处于零测试状态；四条缺陷（时间语义/生命周期/取消/排空）无一有行为锚。
- **风险**: 后续任何测试采用该替身即继承全部契约缺陷且不可察觉；修复 MA-04 后无护栏。
- **建议**: 修复 MA-04 时同步补替身契约测试套（延迟过期/周期复排/destroy 拒绝/取消移除/排空 completeExceptionally 五腿——对照真执行器 DefaultScheduledExecutor 行为做对称断言）。
- **失效面枚举（F3）**: 本登记覆盖本体 C1-003、C4-003、C4-006、C6-004 四条（矩阵行 3/12/15/20）；随 MA-04 工单执行。
- **信心水平**: 确定
- **误报排除**: 非"零调用方即无需测试"——替身是测试基建，其契约测试义务不随当前调用方为零而消失（C4-003 已确证其假保护本质）。
- **复核状态**: 未复核（C10 单轮终止，随合并裁定移交）

### [C10-004] watch 命令注入路径生命周期零测试——C4-007/C6-007/C4-002 无保护且修复陷阱无守卫

- **文件**: `nop-runner/nop-cli-core/src/test/java/io/nop/cli/commands/TestCliDiffAndWatchFixes.java:97-105`（唯一 watch 生命周期测试，仅本地路径）；`nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchCommand.java:93-151`（缺陷本体）
- **证据片段**:
  ```java
  // 唯一测试：仅覆盖 factory==null 本地路径
  CliWatchCommand cmd = new CliWatchCommand();
  CliWatchCommand.WatcherHandle handle = cmd.newFileWatcher();
  assertTrue(handle.locallyOwned, "a locally created watcher is owned by the command");
  handle.watcher.stop();   // 本地路径 stop 安全；注入路径 stop = CL-011 footgun
  ```
- **严重程度**: P2
- **并发场景**: 缺陷本体涉及 NIO watch 线程（非守护）× globalTimer debounce 任务在命令返回后继续执行；测试从不构造 CDI 注入路径。
- **触发时序**（变异推演）: 修复（接住 ICancellable + finally cancel）后现有测试全绿（它们不经过 doWatch 的注入分支）；**错误修复变体**（finally 无条件 watcher.stop()）同样全绿——而该变体触发 CL-011 共享服务被杀 footgun。双向无守卫。
- **现状**: 注入路径（打包默认 CDI 路径，NopCliApplication.factory 实证）在测试树不可表达；processEvents 异常可见性（C4-002）零断言。
- **风险**: MA-02 修复后无回归护栏；错误修复变体（stop 注入实例）引入更重缺陷而无测试拦截。
- **建议**: 补两腿测试：(1) 经容器/反射注入 factory 后执行 doWatch + stdin EOF → 断言 ICancellable 已 cancel、watchKeyMap 尺寸回落（C6-007/C4-007）；(2) processEvents 注入抛异常脚本 → 断言错误被记录且后续事件仍处理（C4-002）。测试中显式断言"未调用共享服务 stop"以钉死修复形态。
- **失效面枚举（F3）**: 本登记覆盖本体 C4-007、C6-007、C4-002（矩阵行 11/16/23）；随 MA-02 与 I-02（C4-002 独立工单）执行。
- **信心水平**: 确定
- **误报排除**: 非"本地路径已测即有保护"——缺陷与测试分属两条互斥路径；非平台设施本体重审（FileWatcher 归移交轨）。
- **复核状态**: 未复核（C10 单轮终止，随合并裁定移交）

### [C10-005] watch-zip 主功能面测试性弃权——C6-001/C1-004/C6-008 所在命令仅参数校验

- **文件**: `nop-runner/nop-cli-core/src/test/java/io/nop/cli/commands/TestCliWatchZipCommand.java:53-84`
- **证据片段**:
  ```java
  // :68-69 明文弃权声明
  // We can't easily test the full watch functionality without complex threading,
  // but we can test the parameter setup
  // 三个测试全部止步于参数断言；zipDir / watch 循环 / init 双触发零驱动
  ```
- **严重程度**: P2
- **并发场景**: 缺陷本体涉及 @PostConstruct×call 双重 init（CDI 路径）、debounce 任务重叠写 zip、写读交叠窗口——测试树以 plain 实例化绕开全部三类场景。
- **触发时序**（变异推演）: 修复 init 幂等守卫 / in-flight 串行化 / tmp+ATOMIC_MOVE 后，现有三个参数测试恒绿；回退恒绿。
- **现状**: 测试代码以注释明文承认"复杂线程场景不测"；@PostConstruct 在 plain JUnit 中不触发，双重 init 缺陷路径结构性不可达。
- **风险**: MA-03 与 C6-001 修复后零护栏；watch-zip 是 runner 的实际功能面，回归（如误删幂等守卫）不可检测。
- **建议**: (1) init 幂等腿：反射调用 init() 两次 → 断言 watchService 引用同一实例（C6-001）；(2) 原子写腿：驱动 zipDirTo 后并发读 → 断言读方只见完整或旧产物（C6-008，可在 zip 工具层小规模模拟）；(3) 重叠腿：慢 zip 任务 + 新事件 → 断言无第二次并发进入（C1-004，用可注入 executor 替身）。
- **失效面枚举（F3）**: 本登记覆盖本体 C6-001、C1-004、C6-008（矩阵行 4/17/24）；随 MA-03 与 I-03（C6-001 独立工单）执行。
- **信心水平**: 确定
- **误报排除**: 非"测试性弃权即豁免"——弃权注释本身登记为结构性观察；CLI 可测性已在同模块 CliDiff/CliCallTools 测试建立先例。
- **复核状态**: 未复核（C10 单轮终止，随合并裁定移交）

### [C10-006] autotest 全局状态无并行/残留检测钩子——C1-005/C1-009/C1-007 全覆盖式假保护

- **文件**: `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/NopJunitExtension.java:30-47,75-85`（缺陷本体）；`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/AutoTestCase.java:183-187,227-254`
- **证据片段**:
  ```java
  // 每个测试类的 beforeAll/afterAll 与每个方法的 initDao/complete 均被全部
  // JunitBaseTestCase 测试隐式执行——但无任何测试断言：
  // (1) 并行进入检测（C1-007）；(2) t_vars/t_context 方法边界清理（C1-005/C1-009）
  IContext context = ContextProvider.getOrCreateContext();
  context.setUserId("autotest");   // complete() 无对称清理断言
  ```
- **严重程度**: P2
- **并发场景**: JUnit 并行模式（框架未声明禁止）下的全局翻动与池化线程 ThreadLocal 残留；测试树全部顺序执行，缺陷分支不可达。
- **触发时序**（变异推演）: 修复（并行 fail-fast 守卫 + complete() finally BaseContextProvider.clear()）后全部测试恒绿；回退恒绿——"顺序下配对正确"的既有行为被全部测试钉死，缺陷行为（并行/残留）零覆盖。
- **现状**: 全覆盖式假保护的典型形态——生命周期代码被 100% 执行但保护力为零；框架对"不支持并行"的边界既无文档也无运行时检测。
- **风险**: MA-05 与 C1-007 修复后无护栏；用户开启并行后的伪失败群无前置检测测试。
- **建议**: (1) 并行守卫腿：双线程并发进入 beforeAll 模拟 → 断言 fail-fast 报错（C1-007）；(2) 残留腿：initDao 后直接断言 `BaseContextProvider` 上下文已随 complete() 清理（C1-009）；(3) VarsScope 腿：useGlobalVars=false 直用路径的 try/finally 清理断言（C1-005）。
- **失效面枚举（F3）**: 本登记覆盖本体 C1-005、C1-009、C1-007（矩阵行 5/7/9）；随 MA-05 与 I-05（C1-007 独立工单）执行。
- **信心水平**: 确定
- **误报排除**: 非"框架测试自举悖论"（用该框架测该框架）——守卫腿可用 plain JUnit + 反射构造，不依赖被测框架的顺序假设。
- **复核状态**: 未复核（C10 单轮终止，随合并裁定移交）

### [C10-007] 资源泄漏检测钩子全测试树缺失——C6 泄漏族回归锚为零

- **文件**: nop-search / nop-runner / nop-autotest 全部 43 个测试文件（grep 证实零泄漏断言形态）
- **证据片段**:
  ```java
  // 全测试树不存在以下任一形态：
  // assertEqual(0, watchKeyMap.size())        // 注销后注册表空（C6-007）
  // assertEqual(fdBaseline, fdCount())        // 句柄数不增长（C6-001/C6-006）
  // assertEqual(0, indexDirs.size())          // 逐出/destroy 后三 CHM 空（C6-002/C6-003）
  // assertTrue(promise.isCompletedExceptionally()) // destroy 排空（C6-004，归 C10-003）
  ```
- **严重程度**: P2
- **并发场景**: 泄漏类缺陷（C6-001/C6-002/C6-003/C6-006/C6-005）的共同回归锚缺失：泄漏是"关闭后状态"属性，现有功能往返测试不读取关闭后状态。
- **触发时序**（变异推演）: 任何泄漏修复（幂等 init/LRU 逐出/running 旗标/try 范围上移/close 接线）后无测试变化；回退无变化。
- **现状**: 与 campaign 先例对照——plan 2479 已建立 fd 计数形态（TestFileDownloadSinkClose，40 次下载增量断言，回退实测必红）；本单元零采用。
- **风险**: C6 六条修复（MA-01 内两条 + I-03/I-08/I-09）全部裸奔；泄漏回归在长驻部署前不可见。
- **建议**: 按站点补最小泄漏断言：watchKeyMap 尺寸（watch 命令）、三 CHM 尺寸（引擎 removeTopic/destroy 后）、SimpleDataSource close 接线（mock/包装计数）。全部为小规模单线程可测形态。
- **失效面枚举（F3）**: 本登记与 C10-001/C10-005 交叠覆盖本体 C6-001/C6-002/C6-003/C6-005/C6-006/C6-007——交叠部分随本体工单，不重复立项；本条单列的独有义务是"关闭后状态断言"这一横切测试形态。
- **信心水平**: 确定
- **误报排除**: 非与 C10-001 重复——C10-001 管并发协调行为，本条管泄漏回归锚形态；非要求生产级泄漏检测框架——断言式最小形态即可。
- **复核状态**: 未复核（C10 单轮终止，随合并裁定移交）

### [C10-008] 失败/取消注入面缺失——C1-006/C4-004/C6-005/C6-006 的触发条件在测试树零构造

- **文件**: `nop-runner/nop-tool/src/test/java/io/nop/tool/refactor/TestSourceRefactor.java:32-44`；`nop-autotest/nop-autotest-dbtool/src/test/java/io/nop/dbtool/core/discovery/TestJdbcMetaDiscovery.java:25-26`；`nop-autotest/nop-autotest-core/src/test/java/io/nop/autotest/core/spike/TestM01SpikeRecordingSession.java:64-90`
- **证据片段**:
  ```java
  // TestSourceRefactor：仅喂 test.java/test.orm.xlsx 良好输入，坏文件（损坏 xlsx）路径零构造
  refactor.refactorDir(dir, FileExtFilter.forFileExt("java","xml","json5","json","md","txt","xlsx"), targetDir);
  // TestJdbcMetaDiscovery: @Disabled —— 唯一真实 JDBC 契约测试禁用
  // TestM01SpikeRecordingSession：EntityRow 单线程往返，无异步 ORM 回调注入
  ```
- **严重程度**: P2
- **并发场景**: 各本体缺陷的触发条件（损坏文件、瞬时 IO 失败、挂死工具、异步回调线程）均需失败注入，测试树只构造成功路径。
- **触发时序**（变异推演）: 修复（try 范围上移/close 接线/join 超时/defensive copy）后现有成功路径测试恒绿；回退恒绿。
- **现状**: 成功路径覆盖制造"已测"错觉（矩阵四条假保护的本源）；失败与取消路径零构造。
- **风险**: 四条 P3 本体修复后无护栏；禁用的 TestJdbcMetaDiscovery 若长期存在将钝化对 C6-006 联动面（CL-008）的感知。
- **建议**: (1) 坏 xlsx 注入 → refactorDir 继续跑完且无句柄增长（C6-005）；(2) 挂死工具（sleep stub）→ join 超时路径断言（C4-004）；(3) SimpleDataSource close 计数（C6-006，与 CL-008 契约核对联动）；(4) EntityRow 异步回调注入（C1-006，可用独立线程写 + 主线程读的最小竞态探针）。
- **失效面枚举（F3）**: 本登记覆盖本体 C1-006、C4-004、C6-005、C6-006（矩阵行 6/13/21/22）；随 I-04/I-06/I-08/I-09 独立工单执行。
- **信心水平**: 确定
- **误报排除**: 非"P3 缺陷不配测试"——登记对象是注入能力缺失这一结构性面，与缺陷判级解耦；非要求故障注入框架——手工 stub 即可。
- **复核状态**: 未复核（C10 单轮终止，随合并裁定移交）

### [C10-009] SPI 契约面零钉住——C1-008 回写所有权与 CL-007 async 命名语义均无行为锚

- **文件**: `nop-search/nop-search-api/src/main/java/io/nop/search/api/ISearchEngine.java:37-57`（契约面）；`nop-search/nop-search-core/src/test/java/io/nop/search/core/biz/TestSearchEngineBizModel.java:169-181`（相邻测试）
- **证据片段**:
  ```java
  // 相邻测试钉委托、不钉语义：caller 线程同步实现下 get(5s) 恒绿，
  // 真异步实现下亦绿——线程语义不可判别
  CompletionStage<SearchResponse> future = bizModel.searchAsync(request);
  future.toCompletableFuture().get(5, TimeUnit.SECONDS);
  assertSame(request, engine.lastSearchRequest, "searchAsync forwards the request untouched");
  // 回写行为（doc.setEmbedding）在测试部署下永不触发（无 ITextEmbedding bean）
  ```
- **严重程度**: P2
- **并发场景**: SPI 演进风险面——`addDocsAsync` 未来被真异步实现覆写时，C1-008 的理论升级路径（调用方 doc 跨线程竞争）无任何测试哨兵。
- **触发时序**（变异推演）: 修复（移除回写 + javadoc 声明）后无测试变化；若未来实现引入真异步 + 回写，现有测试依旧全绿——契约违约在测试树不可表达。
- **现状**: testSearchAsyncDelegatesToEngine 为语义面的假保护（矩阵行 8 的直接证据）；回写所有权、async 命名语义两类契约零断言。
- **风险**: 契约漂移（实现替换/升级）无回归拦截；与 C1-008 修复的 javadoc 义务合并执行。
- **建议**: (1) 以注入 stub ITextEmbedding 的 autotest 部署驱动 autoGenerateEmbedding=true 的 addDoc → 断言调用方 doc 的 embedding 是否被回写（按修复裁定钉死"不回写"或"已声明"）；(2) searchAsync 语义腿：断言执行线程为 caller（或按修复裁定钉死池隔离契约）。
- **失效面枚举（F3）**: 本登记覆盖本体 C1-008（矩阵行 8）+ CL-007 契约面；随 I-07（C1-008 独立工单）执行。
- **信心水平**: 确定
- **误报排除**: 非"javadoc 义务不可测"——回写与执行线程均为可断言行为；非与 C10-001 重复——本条管 SPI 契约演进面，不涉并发协调。
- **复核状态**: 未复核（C10 单轮终止，随合并裁定移交）

**登记统计：C10-001~009 共 9 条，全部 P2；覆盖 24 条本体（其中 C6-004 归 C10-003、C6 系泄漏回归锚归 C10-007 交叠单列）。**

## 七、系统性观察

1. **会红=0 全域形态（campaign 级同型第 N 次确认）**：24 条缺陷对全部 43 个测试文件的变异推演无一命中"会红"；真并发测试数为 0（零 CountDownLatch/Awaitility/双线程断言；唯一 Thread.sleep 位于 @Disabled 测试）。测试栈结构性单线程化与 WI 4.2（"全模块 17 测试文件零真并发测试"）、WI 4.3（"测试栈结构性单线程化"）完全同型——缺陷不是漏测，而是现有测试基建**不可表达**并发行为。
2. **测试性弃权的明文化**：TestCliWatchZipCommand:68-69 以注释明文承认回避 watch 主功能（"We can't easily test the full watch functionality without complex threading"）——恰是五条 P2 缺陷（C1-004/C4-002/C4-007/C6-007/C6-008）所在的功能面；叠加 TestJdbcMetaDiscovery/TestLuceneSearchEngine.indexAll 两个 @Disabled（唯一触及真实资源契约的测试），本单元的"复杂即不测"不是隐式倾向而是显式决定。与 WI 4.2"选主测试结构性不可达"同型。
3. **假保护双向形态 + 修复陷阱无守卫**：(a) 全覆盖式假保护——每个 JunitBaseTestCase 都穿 AutoTestVars/initDao/complete 生命周期（覆盖率意义上的 100%），但 C1-005/C1-009/C1-007 的变异全部不可见；(b) 正确一半钉死——TestCliDiffAndWatchFixes 只能测本地路径，缺陷所在的注入路径在测试树不可表达，且该测试**不拦截**"无条件 stop() 注入实例"的错误修复变体（CL-011 footgun）——修复需要护栏，错误修复同样需要拦截，两者目前均为零。正向修复约束仅一处（S-04 rollback 自愈）——修复实现时必须保持。

## 八、单轮终止声明

C10 按 `ai-dev/skills/concurrency-resource-audit-prompt.md` 口径**单轮执行并终止**：保护力矩阵 24 行全覆盖闭合（会红/假保护/无测试三态逐行判定）；反向钉死检查完成（硬钉 0、正向约束 1、修复陷阱面 1）；时序敏感/泄漏钩子/大规模边界三专项审查完成；缺保护登记 C10-001~009 共 9 条（每条 ≥10 行、附录 A 全字段）；无待深挖方向——后续测试补齐义务已逐条映射至合并裁定工单（见 merge-adjudication.md §九），随修复批次执行，不开启 C10 第二轮。
