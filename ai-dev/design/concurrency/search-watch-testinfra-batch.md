# WI 4.5 Lucene/watch/测试基建束（G71，R4-29）设计裁定

> Status: draft
> Last Reviewed: 2026-11-12
> Source: `ai-dev/audits/2026-10/2026-10-21-1444-deep-audit-nop-search-runner-autotest-concurrency/`（merge-adjudication §二 MA-01~04 + §三 I-C4-002；live 取证 fresh-session @ 2026-11-12，分支 concurrency-audit-remediation）
> 批次: roadmap 8.69 行（R4-29，G71 五工单：4.5-MA-01、MA-02、MA-03、MA-04、I-C4-002）；批次约束 `ai-dev/audits/cra-index.md:456`（P2-G71 行）

## 〇、Live 取证（2026-11-12 逐条核对，全部在位）

### MA-01 三 CHM 无协调（在位）

载体：`nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java`

| 面 | 锚点 | 现状 |
|---|---|---|
| 三 CHM 字段 | `LuceneSearchEngine.java:134-136` | `indexDirs`/`indexWriters`/`searcherManagers` 三个 `ConcurrentHashMap`，零协调设施 |
| 获取路径 | `:264`（getDirectory）、`:277`（getSearcherManager）、`:289`（getIndexWriter） | 均 `computeIfAbsent` 畅通无守卫 |
| removeTopic | `:1118-1150` | searcherManager→writer→dir 三段移除+关闭，与在途引用无协调 |
| rollback | `:1158-1173` | S-04 已钉形态在位：先 `searcherManagers.remove`+close（:1159-1166）→ `indexWriters.remove`（:1167）→ `writer.rollback()`（:1169）；**indexDirs 条目不移除不关闭（dir 有意保留）** |
| destroy | `:179-222` | 按 searcherManagers→writers→dirs 顺序 forEach close + clear，与在途 computeIfAbsent 无守卫 |

S-04 正向约束在位：`nop-search/nop-search-lucene/src/test/java/io/nop/search/TestSearchFixesDiscriminative.java:114-138`（testTopicRecoversAfterFailedBatch：失败 batch 后 topic 自愈、断言 total==2）。

### MA-02 ICancellable 丢弃 + 注入路径跳过清理（在位）

载体：`nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchCommand.java`

- `:124-126`：`watcher.watch(...)` 返回的 `ICancellable`（FileWatcher.java:49-87，cancel → unwatch → watchKeyMap 移除）**未接住**，直接丢弃——唯一注销通道断。
- `:94-105`（call）：finally 仅 `if (handle.locallyOwned) watcher.stop()`——注入路径（locallyOwned=false，`:85-87`）**跳过一切清理**。
- `:84-91`（newFileWatcher）：factory 注入 → `new WatcherHandle(fileWatcherFactory.newFileWatcher(), false)`。
- 底层注册表：`nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/NioFileWatchService.java:32`（`watchKeyMap` 私有 CHM）、`:244-247`（unwatch 移除 entry）——watchKeyMap 为断言 seam（跨模块需反射）。

### MA-03 watch-zip 产物并发损坏（在位）

载体：`nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchZipCommand.java`

- `:127-137`（scheduleZipUpdate）：`executor.debounce("refresh", debounceWait, ...)` 回调内直接 `ResourceHelper.zipDir(...)`（:131）——无 in-flight 守卫、无原子写。
- debounce 平台侧：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/RateLimitExecutorImpl.java` debounce 段（约 :68-100）——**plan 2547（1.7-C1-004）已修四步非原子**（compute 单原子域 + cancel(true) 绑定底层调度），但**已提交任务 cancel 不等待**语义仍在（CL-005 移交轨维持，MA-03 使用方守卫不依赖其回流——裁定维持）。
- **zipDirTo 归属核实（越界）**：写链为 `CliWatchZipCommand:131` → `ResourceHelper.zipDir`（`nop-kernel/nop-core/.../ResourceHelper.java:589`）→ `IZipTool.zipDirTo`（`nop-kernel/nop-core/src/main/java/io/nop/core/resource/zip/IZipTool.java:75-85`，default 方法，`getZipOutput(zipFile)` 就地打开目标 zip 写入，无 temp+rename）。**归属 nop-core（平台 Protected 面外但越模块）→ 标注越界单行申报**：本批不触碰 nop-core，原子性在使用方层解决（裁定 C）。

### MA-04 MockScheduledExecutor 替身契约缺失（在位）

载体：`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/mock/MockScheduledExecutor.java` + `MockTask.java`

| 缺陷面 | 锚点 | 现状 |
|---|---|---|
| 时间语义 | `MockScheduledExecutor.java:25-33`（schedule）：`unit` 参数**直接丢弃**，`delay` 原值当毫秒传 `MockTask`；`MockTask.java:26-29`（R1-M9 勘正，设计勘正 3 原引 :31-34 为 compareTo）：`getDelay` 恒返回 `initialDelay`（不随时间衰减）→ `DelayQueue.poll()`（`MockScheduledExecutor.java:66-68`）对 delay>0 任务永不返回 | 三重破坏在位 |
| 生命周期 | `MockScheduledExecutor.java:17`（`started` 死旗标：置 true 后无人读）；`:102-104`（destroy 仅置 false，不拒绝不排空）；`:25-57` 三 schedule 方法零 started 检查 | 在位；另发现 `scheduleWithFixedDelay`（:48）**未 synchronized**（schedule/:25 与 scheduleAtFixedRate/:36 均有）——审计未列，随重写一并覆盖 |
| 取消语义 | `:29-32/:41-44/:53-56`：返回值是 `promise.exceptionally(...)` **派生 promise**——cancel 落在派生体不回传源任务；清理回调（tasks.remove）挂在 exceptionally 分支（仅异常完成触发，cancel/正常完成不触发） | 在位 |
| 周期复排 | `:59-64`（triggerNext → `task.call()` 单次）：SCHEDULED_AT_FIXED_RATE / SCHEDULED_WITH_FIXED_DELAY 两种 kind 无任何重排逻辑，`period` 死字段（MockTask:23/:52-54 getter 无人消费） | 在位 |

**两案使用方调查（全仓 grep 2026-11-12）**：`grep -rln MockScheduledExecutor` 全仓命中 = 类本体 + 审计/计划/分析文档 + `docs-for-ai/04-reference/source-anchors.md`；**零测试引用、零生产/测试代码消费方**（含 beans.xml 零引用）。无任何现有测试依赖 delay>0 语义——两案均技术可行。

### I-C4-002 watch 异常处理不对称（在位，第三腿已漂移——勘正见 §五）

| 类 | 锚点 | 处理形态 |
|---|---|---|
| CliWatchCommand | `:122`（首跑直调）、`:126`（watch 回调直抛）、`:138-151`（processEvents，`xpl.invoke(scope)` :150 无 try/catch） | 异常冒泡出 debounce 任务 → FileWatcher.triggerChange（nop-core FileWatcher.java:70）不持有 future → **静默不可见**（循环不死） |
| CliWatchZipCommand | `:128-136` | catch + LOG.error（:133-135）——**正确形态（对照基准）** |
| CliRunCommand | **已修（plan 2496 4.5-C4-001）**：`CliRunCommand.java:105-116` startWatchLoop 持有 future + whenComplete 失败落日志 | 审计枚举的"三 watch 类三种处理"第三腿已不在位 |

### C10 成员面测试义务（审计 C10-test-protection.md §六对应行）

- **C10-001（MA-01 + watch C4-005）**：三腿并发协调 + 容量腿——(1) 双线程 addDocs 同 topic 同 docId × N → 终态唯一；(2) removeTopic 与在途 search/addDocs 交叠（CountDownLatch 栅栏）→ 无 AlreadyClosedException 外泄；(3) destroy 后 computeIfAbsent 不入 map/二次校验关闭 + topic 上限逐出断言；线程/fd 计数覆盖 C4-005。
- **C10-003（MA-04）**：替身契约五腿对称断言（对照真执行器 DefaultScheduledExecutor）：延迟过期 / 周期复排 / destroy 拒绝 / 取消移除 / 排空 completeExceptionally。
- **C10-004（MA-02 + I-C4-002）**：两腿——(1) 注入 factory 后 doWatch + stdin EOF → ICancellable 已 cancel、watchKeyMap 尺寸回落、**未调用共享服务 stop**（钉死修复形态，防 CL-011 footgun 变体）；(2) processEvents 注入抛异常脚本 → 错误被记录且后续事件仍处理。
- **C10-005（MA-03 之腿）**：幂等腿归 I-C6-001（G72 批，不在本批）；本批取原子腿 + 重叠腿——zipDirTo 后并发读只见完整或旧产物；慢 zip 任务 + 新事件无第二次并发进入。
- **C10-007（交叠）**：三 CHM 关闭后尺寸断言（与 C10-001 同 diff，不重复立项）；watchKeyMap 尺寸断言归 C10-004 腿 1。

## 一、设计裁定（五工单 A-E）

1. **裁定 A（MA-01 三 CHM 协调；正向约束不可拆分）**：per-topic 读写协调设施 + topic 上限/LRU 逐出 + `ISearchEngine` javadoc 线程契约声明，**四成员（C1-001/C1-002/C6-002/C6-003）同设施同 diff，禁止拆分**（cra-index G71 约束 + merge-adjudication §二.1：拆分即留另一半逃逸窗口）。设施选型（per-topic RW 锁 / 全局读写锁 / running 旗标 + computeIfAbsent 后二次校验）留实现评审裁定，但**评审必须整族一次裁定**（登记防拆分，merge-adjudication §三开放项 3 维持）。**评审必查项（设计 R1-M3 + R2-M1）**：⓪ running 旗标案与 RW 锁能力不对等——旗标只挡 computeIfAbsent 重建，不排斥在途 search 已 acquire 的 searcher（removeTopic/rollback 关闭 SearcherManager 时在途 acquire 后 search/release 仍 AlreadyClosedException 外泄=C10-001 腿 2 目标面）；旗标案须伴随在途引用保护（引用计数/优雅关闭/ACE 归一 topic-closed 语义）或实现评审显式声明腿 2 通过语义：① rollback（:1158）在 addDocs/removeDocs 的 catch 块内被调用——若 addDocs 置于 per-topic read lock 下而 rollback 再取 write lock，ReentrantReadWriteLock 不允许读升级写=自死锁，必须同锁域内完成或降级 running 旗标方案；② 全部公共读路径站点纳入读锁 span 清单逐站点核对——search 五处 acquire/release（:320-333/:451-474/:510-534/:548-613/:698-748）+ refreshBlocking（:296-301，R2-M2 补入，同窗口）。**硬约束（冻结面）**：rollback "先移出缓存再关闭" 序列（:1158-1173）与 dir 保留语义不得回退——`TestSearchFixesDiscriminative.testTopicRecoversAfterFailedBatch:114-138` 为正向约束载体，MA-01 任何实现必须保持其绿；逐出复用 removeTopic 三段关闭序列。载体测试：`TestLuceneSearchEngineCoordination`（新建，**包 io.nop.search.lucene**[与 engine 同包——既有 TestSearchFixesDiscriminative 在 io.nop.search 不同包，包私有断言要求同包]）。topic 上限配置来源（设计 R1-M4）：`LuceneConfig` 新增 `maxTopics` 字段（缺省值实现评审定，容量腿用 setter 注入小值）；destroy 后 computeIfAbsent 二次校验失败抛 `NopException`（复用现有 ERR_LUCENE_OPEN_INDEX_FAIL，设计 R1-M8）；三 CHM 字段降 package-private 为显式生产代码变更（测试可见性 seam，设计 R1-M7）。护栏 C10-001 三腿 + 容量腿 + C10-007 三 CHM 尺寸断言。
2. **裁定 B（MA-02 ICancellable 接住；禁 stop() 统一）**：`doWatch` 接住 `watcher.watch()` 返回的 ICancellable（:124-126 改赋值），`call()` finally **无条件 `cancel()`（null 守卫：scriptFile 不存在或首跑抛出时 watch() 未执行、ICancellable 为 null——R2-m4）**（unwatch 只摘本调用 key，NioFileWatchService.unwatch 幂等，双路径安全）+ 保留 locallyOwned 路径的 `watcher.stop()` 为服务级兜底。**修复陷阱（cra-index G71 约束 + C10 §三.2）**：禁止以"无条件 watcher.stop()"统一两路径——注入实例 stop 即 CL-011 共享 watchService 被杀 footgun；修复 diff 必须同批补 C10-004 两腿测试，腿 1 显式断言"注入实例 stop 未被调用"，钉死正确修复形态。载体测试：`TestCliWatchCommandLifecycle`（新建，nop-cli-core；注入 fake FileWatcherFactory 返回 spy FileWatcher；watchKeyMap 尺寸经反射断言——跨模块私有字段，登记为测试 seam）。**测试接缝（设计 R1-M6）**：doWatch 为 private 且 call() 直读 System.in（:129）——stdin EOF 腿用 System.setIn(空流)（不改生产签名；**try/finally 恢复原 System.in 防污染同 JVM 其他测试**——R2-m2）；「错误被记录」断言用 logback ListAppender 挂 CliWatchCommand.LOG。护栏 C10-004。
3. **裁定 C（MA-03 使用方层串行化 + 原子写；平台零触碰）**：`scheduleZipUpdate` 回调加 **in-flight 串行化守卫（AtomicBoolean + dirty 补偿执行：在跑时置 dirty，跑完补偿一次）**；**注入接缝（设计 R1-B1，生产代码变更显式声明）**：executor 现于 watchDirAndAutoZip 内硬编码创建（:100-101 GlobalExecutors.globalTimer()+new RateLimitExecutorImpl）、scheduleZipUpdate 为 private（:127）——测试需把替身送进去：增加可覆写的 executor 工厂方法（protected createExecutor()）或 scheduleZipUpdate 改包私有，二选一实现评审定，不触碰 testParameterDefaults 冻结面（debounceWait/recursive 默认值不变）；产物写改 **tmp 文件写入 + `Files.move(tmp, target, ATOMIC_MOVE)`**（同目录 tmp 保证 rename 原子性）——**不触碰 nop-core IZipTool.zipDirTo**（:75-85 就地覆盖写维持现状，登记越界单行申报：平台侧 temp+rename 改造留档 backlog，使用方层已闭合写-读窗口）。使用方守卫**不依赖** CL-005/CL-012 平台侧回流（merge-adjudication §二.3 维持；平台 debounce 原子性已由 plan 2547 闭合，cancel 不等待语义由 in-flight 守卫在使用方消化）。载体测试：`TestCliWatchZipCommand` 扩展（原子腿 + 重叠腿；重叠腿用可注入 IScheduledExecutor 替身驱动——依赖裁定 D 重写后的替身；**包装说明（R2-m3）**：scheduleZipUpdate 收 IRateLimitExecutor，测试以 `new RateLimitExecutorImpl(mock)` 包装替身后传入，不违反冻结面）。护栏 C10-005 之原子/重叠两腿。
4. **裁定 D（MA-04 替身整体重写——两案择一，裁定案 A）**：
   - **调查结论**：全仓零消费方、零测试引用，无任何测试依赖 delay>0 语义——两案均可行，裁定不依赖兼容性。
   - **裁定：案 A（整体重写，单一 diff）**。理由：(a) 案 B（声明 delay=0 边界 + 删死代码）仍须修 destroy 拒绝、cancel 回传、清理回调错挂三腿——其代码量与重写重合，节省有限；(b) 本批自身 C10-005 重叠腿需要"可注入调度替身驱动延迟任务"，delay=0 边界替身无法支撑（自举矛盾）；(c) C10-003 五腿对称断言以真契约为最低成本载体；(d) 零消费方 → 重写回归风险为零；(e) audit 两维度建议一致指向修复优先。
   - **重写形态（保持手动驱动模型，不引入真实线程）**：入队锚定 `System.nanoTime()` + TimeUnit 忠实换算（unit 不得丢弃）；`MockTask.getDelay` 按锚定衰减；`triggerNext`/`pollTask` 只取已过期任务 + 新增 `expireTasks(long millis)` 时间推进驱动 API（**推进内部虚拟时钟偏移**：now = nanoTime 起点已推进偏移，后续入队锚定与 getDelay 衰减均基于虚拟 now——R2-M3 语义钉，禁止读作「仅强制触发 delay≤millis」）（替代对 sleep 的依赖）；`started` 守卫——destroy 后提交 reject（`NopException` + English message）；返回源 promise 本体（删除 exceptionally 派生体，cancel 即回传）+ 清理回调挂 `whenComplete`（cancel/异常/正常完成统一触发 tasks.remove）；`destroy()` 排空队列并逐 promise `completeExceptionally`；周期 kind 显式声明"周期任务仅手动驱动单轮、不承担 IScheduledExecutor 周期绝缘/退避契约"边界（javadoc 声明 + 删除死 period 字段）——**裁定边界声明案**（设计 R1-B2：IScheduledExecutor:30-52 已裁定周期契约含 fixedDelay 指数退避 1s~60s + 异常绝缘，MockScheduledExecutor 无真实线程无法按时间语义履约退避；re-arm 案将违反接口契约或被迫实现退避观测，与轻量替身定位冲突；零消费方使边界声明可接受）。对照腿声明：TestMockScheduledExecutorContract 以 DefaultScheduledExecutor 的**语义为断言基准（fixture 内人工校对）**，不启动真执行器（避免真实线程+sleep flaky）；补 `scheduleWithFixedDelay` 缺失的 synchronized（:48）。
   - 载体测试：`TestMockScheduledExecutorContract`（新建，nop-autotest-core，五腿对称断言对照 DefaultScheduledExecutor 语义）。护栏 C10-003。
5. **裁定 E（I-C4-002 processEvents catch+LOG 对齐）**：`CliWatchCommand.processEvents`（:138-151）`xpl.invoke(scope)` 包 try/catch + `LOG.error`（对齐 `CliWatchZipCommand:133-135` 兄弟正确形态），catch 后返回——保持"循环不死"语义（与 C4-001 不同，失败可见而非功能死亡）。**范围收窄（勘正驱动）**：审计"三 watch 类处理不对称"第三腿 CliRunCommand 已随 plan 2496 修复（whenComplete 失败可见化），本工单只对齐 CliWatchCommand 一站；F9 联动面（脚本半途失败后 globalState 脏态）登记观察不扩 scope（catch 内不清理 state——清理语义属脚本契约，本批不裁）。载体测试：`TestCliDiffAndWatchFixes` 扩展异常腿（C10-004 腿 2：抛异常脚本 → 错误记录断言 + 后续事件仍处理）。**接缝（设计 R1-M6）**：processEvents 为 private（:138）→ 改包私有（测试同包 io.nop.cli.commands）；异常脚本=tempDir 写 .xpl 经 XLang.parseXpl 最小注入形态。护栏 C10-004。

## 二、载体表

| 工单 | 载体 | 模块叶 |
|---|---|---|
| MA-01 | `TestLuceneSearchEngineCoordination`（新建：并发三腿 + 容量逐出腿 + 三 CHM 尺寸断言；S-04 正向约束保持绿） | nop-search/nop-search-lucene |
| MA-02 | `TestCliWatchCommandLifecycle`（新建：注入路径 cancel + watchKeyMap 回落 + 未调 stop 断言） | nop-runner/nop-cli-core |
| MA-03 | `TestCliWatchZipCommand` 扩展（原子腿 + 重叠腿） | nop-runner/nop-cli-core |
| MA-04 | `TestMockScheduledExecutorContract`（新建：五腿对称断言） | nop-autotest/nop-autotest-core |
| I-C4-002 | `TestCliDiffAndWatchFixes` 扩展（异常腿） | nop-runner/nop-cli-core |

## 三、变异红证计划（每工单 ≥3 链，先红后绿验收）

- **MA-01**：链1 删 per-topic 协调锁 → 并发腿（addDocs 交叠）红；链2 去掉 destroy 二次校验 → 停机腿（destroy 后 computeIfAbsent）红；链3 去掉上限/LRU → 容量腿红；链4（防回退）破坏 rollback 先移除序列 → `TestSearchFixesDiscriminative.testTopicRecoversAfterFailedBatch` 红（正向约束反证）。
- **MA-02**：链1 finally 改回"仅 locallyOwned 清理" → 注入路径 cancel 断言红；链2 错误变体（finally 无条件 `watcher.stop()`）→ "未调用共享服务 stop" 断言红（footgun 拦截腿）；链3 删 cancel → watchKeyMap 尺寸回落断言红。
- **MA-03**：链1 去掉 in-flight 守卫 → 重叠腿（并发进入计数）红；链2 回退为直接写目标 zip → 原子腿（读方只见完整/旧产物）红；链3 dirty 补偿删除 → 末次事件丢更新断言红。
- **MA-04**：链1 getDelay 恒定（还原 unit 丢弃）→ 延迟过期腿红；链2 destroy 不排空 → 排空 completeExceptionally 腿红；链3 exceptionally 派生体还原 → cancel 级联腿红；链4 started 检查删除 → destroy 后提交拒绝腿红。
- **I-C4-002**：链1 删 catch → 异常记录断言红；链2 catch 后 rethrow 外泄 → "后续事件仍处理" 断言红。

## 四、接缝与回归面

- 硬接缝：① MA-01 四成员单设施单 diff（禁拆分，评审整族一次裁定）；② MA-02 + I-C4-002 同模块但**不同 diff**（生命周期编排 vs 异常可见性，零耦合，不合并）；③ MA-04 重写先于 MA-03 重叠腿落地（重叠腿依赖重写后替身——批内顺序约束：MA-04 → MA-03）。
- 回归基线：`./mvnw test -pl nop-autotest/nop-autotest-core,nop-search/nop-search-lucene,nop-runner/nop-cli-core -am`。
- 接缝 seam：watchKeyMap（私有，反射断言，登记为测试 seam）；MockScheduledExecutor 手动驱动 API（triggerNext/expireTasks，包私有或 public 留实现评审）；LuceneSearchEngine 三 CHM（包私有/protected 访问，测试同包可断言尺寸）。

## 五、审计与 live 勘正（3 处）

1. **I-C4-002"三 watch 类处理不对称"第三腿已漂移**：CliRunCommand 已随 plan 2496（4.5-C4-001）修复——`CliRunCommand.java:105-116` startWatchLoop 持有 future + whenComplete 失败落日志。现存不对称实为两腿（CliWatchCommand 无 catch vs CliWatchZipCommand catch+LOG）。I-C4-002 修复面收窄为单站（裁定 E），对照基准仍为 CliWatchZipCommand:133-135。
2. **MA-03 平台 debounce 侧描述部分漂移**：审计根因表述"debounce 对在跑任务仅 cancel 不等待"引用的实现已被 plan 2547（1.7-C1-004）重写（RateLimitExecutorImpl debounce 段，compute 单原子域 + cancel(true) 绑定底层调度）；"cancel 不等待已提交任务"的语义仍成立（CL-005 移交轨维持），但四步非原子中间态描述已过时。MA-03 使用方守卫裁定不变。
3. **MA-04 审计证据片段锚点修正**：审计引用 `getDelay` 在 MockScheduledExecutor.java 内——live 实际位于 `MockTask.java:27-29`（R1-M9 追勘：:32-34 为 compareTo；R2-m1 同步）（MockTask/MockTaskKind 独立文件）；"恒不过期"机制 = getDelay 恒返回 initialDelay 不衰减 + `DelayQueue.poll()`（MockScheduledExecutor.java:66-68）仅出队已过期任务。另发现审计未列的两处：`scheduleWithFixedDelay`（MockScheduledExecutor.java:48）缺 synchronized；周期 kind 无复排逻辑（period 死字段）——均随裁定 D 重写覆盖。

## 六、冻结面（不许回退的既有钉）

- `TestSearchFixesDiscriminative.testTopicRecoversAfterFailedBatch:114-138`——S-04 rollback 先移除再关闭 + dir 保留（MA-01 正向约束）。
- `TestCliDiffAndWatchFixes.watchCommandBuildsWatcherWithoutContainerInjection:97-105`——本地路径 locallyOwned 契约（MA-02 兼容面）。
- `TestCliWatchZipCommand.testParameterDefaults:81-84`——debounceWait=100 / recursive=true 默认值（MA-03 不改默认值）。
- `RateLimitExecutorImpl` debounce plan 2547 修复形态（compute 单原子域 + cancel(true) 绑定调度）——MA-03 使用方守卫不触碰该文件。
- NioFileWatchService/FileWatcher 平台层（nop-core）——MA-02 只动使用方，CL-011 footgun 面零触碰。

## 七、owner doc 缺口声明

nop-search 无 03-modules owner doc（roadmap 8.69 行已声明缺口）——沿用缺口声明：本批契约落 `docs-for-ai/04-reference/source-anchors.md` 新锚点行（LuceneSearchEngine 三 CHM / CliWatchCommand WatcherHandle / MockScheduledExecutor 契约），不新建 owner doc。
