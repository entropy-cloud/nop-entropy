# C4 线程池与执行器治理 — 第 2 轮深挖（WI 4.5 R2）

- **审计员**: 独立深挖审计员（第 2 轮，C1/C4/C6 合并批）
- **日期**: 2026-10-21
- **盲审纪律**: 未阅读 `ai-dev/audits/**` 中除第 1 轮三文件与本文件外的一切内容；未阅读 plans/bugs/lessons/backlog；未执行 git log。契约以代码与 javadoc 为准。
- **深挖任务边界**（承接第 1 轮 C4-001~C4-005，不重复登记）：(a) MockScheduledExecutor 周期任务语义——DelayQueue 恒不过期的替代面；(b) junit 集成测试生命周期线程；(c) cli-core NioFileWatchService 其余面。

## 深挖覆盖声明

- **MockScheduledExecutor/MockTask 全方法逐行复核**：第 1 轮三面（started 死旗标 C1-003/C4-003、DelayQueue 恒不过期 C1-003、destroy 不排空 C6-004）之外，本轮新增**取消语义缺失**（C4-006）与**清理回调挂错分支**判定；其余面（compareTo 以 initialDelay 裸值对比、submit→schedule(0) 是唯一可触发路径、scheduleWithFixedDelay 未 synchronized 的风格不一致）经复核或与第 1 轮结论一致或无害，不另立条目。
- **junit 集成生命周期线程深挖结论**：`NopJunitExtension.beforeAll/afterAll` → `CoreInitialization.initialize/destroy` 每类循环。经核实 `GlobalExecutors` 的池注册表 `g_executors`（GlobalExecutors.java:32）为进程级 static CHM，`CoreInitialization.destroy` 不触碰它（nop-core initialize 包无任何 GlobalExecutors 引用）——即每类 destroy 不会关闭 globalTimer，池为 JVM 单例、daemon 线程：**无每类线程churn、无池泄漏链**；该语义同时意味着测试类中调度到 globalTimer 的未取消任务会跨类存活（本批 8 根内无此使用形态——CliWatch 族为 CLI 进程域，autotest 无 globalTimer 使用点，逐一核实）。`NopTestConfigProcessor:31-33` TestClock 注册/`afterAll:77` 恢复配对干净（全局翻动面已并入 C1-round2 补充）。junit 7 文件无线程创建点（基线 C4a 一致）。**无新立条目**。
- **NioFileWatchService 其余面（使用方视角）**：注入路径（FileWatcherFactory @Singleton 共享单例）与本地路径（CliWatchCommand:88-90 自建）的 stop 语义差异是本轮 C4-007/C6-007 的根；`newSingleThreadExecutor("nio-file-watch-service", 1, false)`（ExecutorHelper.java:49, NioFileWatchService.java:58）第三参 daemon=false——**watch 线程为非守护线程**，作为 C6-001 泄漏后果的放大因子登记（见下方补充节）；`doStop`（NioFileWatchService.java:196-211）`future.cancel(true)+shutdown()` 无 awaitTermination，属平台类本体，移交口径不变。

## 发现

### [C4-006] MockScheduledExecutor 返回 Future 的取消语义缺失：cancel 不移除已入队任务，清理回调挂在永不携带清理需求的分支上

- **文件**: `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/mock/MockScheduledExecutor.java:25-33,47-57`；`nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/mock/MockTask.java:40-50`
- **证据片段**:
  ```java
  public synchronized <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      CompletableFuture<V> promise = new CompletableFuture<>();
      MockTask task = new MockTask(callable, delay, 0, MockTaskKind.ONCE, promise);
      tasks.add(task);
      return promise.exceptionally(err -> {
          tasks.remove(task);          // 清理回调挂在 exceptionally 上
          throw NopException.adapt(err);
      });
  ```
- **严重程度**: P2
- **并发场景**: 测试期单线程——调用方对 `schedule/scheduleAtFixedRate/scheduleWithFixedDelay` 返回的 future 调用 `cancel(true)`；替身当前零调用方（第 1 轮已 grep 核实）。
- **触发时序**: T1 被测代码 `Future<?> f = executor.scheduleWithFixedDelay(task, 5_000, 100, MILLISECONDS)`（任务已入 DelayQueue）→ T2 调用方 `f.cancel(true)`——注意返回的是 `promise.exceptionally(...)` 派生 stage 而非源 promise，**对派生 stage 的 cancel 不会传播回源 promise**（CompletableFuture 依赖 stage 取消独立于源头）→ 源 promise 保持未完成 → `tasks.remove(task)` 清理回调（挂在源 promise 的 exceptionally 链上）**永不触发** → MockTask 滞留 DelayQueue → `triggerNext()` 照常取出并执行该"已取消"任务，且执行结果写入源 promise 后流经已取消的派生 stage 被静默丢弃。对照 JDK 契约：`ScheduledFuture.cancel` 应将任务从队列移除并阻止执行。
- **现状**: 替身无任何取消路径；第 1 轮 C6-004 已指出 exceptionally 回调里的 `tasks.remove` 在"执行后失败"分支是 no-op（任务已被 poll 出队）——本轮进一步确认该回调是**唯一**清理点且挂在唯一不需要它的分支上：需要清理的 cancel-before-exec 分支反而触达不了。
- **风险**: 依赖替身的测试无法暴露生产代码"cancel 后任务仍执行"的真缺陷（假保护）；cancel 后任务照常执行还可能污染测试断言。零调用方 + 测试期，条件化 P2。
- **建议**: 覆写/包装返回 future 使 cancel 传播到源 promise（`promise.cancel` 级联），并将 `tasks.remove(task)` 挂到源 promise 的 `whenComplete`（cancel 也触发）而非 exceptionally；或声明替身不支持取消并在 javadoc 标注边界。
- **失效面枚举（F3）**: 提交面全查——`schedule`(:25)/`scheduleAtFixedRate`(:36)/`scheduleWithFixedDelay`(:47)/`submit`×2(:87,:92)/`execute`(:107) 六个入口的返回 future 全部走同一 exceptionally 包装，取消语义同缺（逐一登记，无豁免）；对照面——真执行器 `DefaultScheduledExecutor` 返回 JDK ScheduledFuture（cancel 从队列移除，语义正确），`RateLimitExecutorImpl.debounce`(:52-54) 对旧任务 `old.cancel(true)` 是替身缺陷的既知受害方（第 1 轮 CL-005/C1-004 链）。
- **信心水平**: 确定（CompletableFuture 派生 stage 取消不回传源 promise 为 JDK 契约；代码直读）
- **误报排除**: 非替身简化并发模型豁免——与 C4-003 同判：豁免不覆盖生命周期/取消契约缺失造成的假保护；亦非原语使用报告，而是"返回对象的契约与 JDK 语义不对称"的定向检查项。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

### [C4-007] CliWatchCommand 注入路径：命令返回后 watch 仍活跃——EOF"停止观看"契约仅本地路径成立，debounce 残留任务在收尾后仍执行一次

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchCommand.java:93-105,124-133`；根因载体 `C6-007`（watch 注册的 ICancellable 被丢弃）
- **证据片段**:
  ```java
  // :124-126 返回的 ICancellable 未被任何变量接住
  watcher.watch(Arrays.asList(watchDirs)..., debounceWait,
          events -> processEvents(xpl, events, state));
  try {
      System.in.read();
  } catch (IOException e) {
      // EOF (e.g. stdin closed) is a normal way to stop watching; log it for diagnosis.
      LOG.debug("nop.cli.watch: input read ended, stopping watch", e);   // :132 声明的契约
  ```
- **严重程度**: P2
- **并发场景**: 主线程阻塞于 stdin → EOF 返回 → NIO watch 线程（非守护，NioFileWatchService.java:58）与 globalTimer debounce 任务线程继续工作；注入路径 watchService 为容器共享单例。
- **触发时序**: T1 CDI 路径（NopCliApplication factory 注入，打包默认路径）执行 doWatch → watch 注册成功（WatchKey 写入共享单例 watchKeyMap，见 C6-007）→ T2 stdin EOF → doWatch 返回 0，finally :100-104 因 `locallyOwned=false` **跳过一切清理** → 主线程已"返回"，但 NIO 线程仍在投递事件、debounce 任务仍被调度 → **:132 日志声明的"EOF 停止观看"契约不成立**：此后任一文件变化仍触发 `processEvents → xpl.invoke`（脚本在命令返回后继续执行），直到容器停机 PreDestroy 停掉共享服务。同 JVM 反复调用形态（嵌入/测试场景）下，前次调用的 watch 永久活跃并与后次调用的 watch 叠加。另 F8 残留面：finally/stop 路径均不取消已调度的 debounce 任务（RateLimitExecutorImpl 无按 key 取消入口），stop 后至多 debounceWait=100ms 内仍执行一次残余 processChangeEvent。
- **现状**: 清理动作三处全缺——watch() 返回的 ICancellable 丢弃（C6-007）、注入路径不 stop（javadoc 声明的所有权设计）、debounce 残留无补偿；本地路径（factory==null）经 finally watcher.stop() 间接 cancel 全部 WatchKey，契约成立——同一命令两路径行为分叉。
- **风险**: 单进程 CLI 的实害窗口为"返回到容器停机"之间（通常亚秒），但事件泵线程为非守护线程，若容器停机路径未达（嵌入宿主不退出），watch 与脚本执行无限期存续；反复调用场景下多个"已返回"的 watch 并发执行同一 state/脚本（与 C1-004 重叠执行面叠加）。实际触达面（打包默认 CDI 路径），不降档。
- **建议**: 接住 `watcher.watch(...)` 返回的 ICancellable，在 finally 中无条件 `cancel()`（注销 WatchKey，对两条路径均安全——unwatch 只摘本调用注册的 key，不触碰共享服务生命周期）；EOF 路径补 LOG.info 与一次性"残余事件排空"；debounce 残留由 C6-007 修复后自然消解（key 注销后 NIO 线程不再触发新 debounce）。
- **失效面枚举（F3）**: watch 返回值消费点全查——`CliWatchCommand.doWatch:124`（丢弃，本缺陷）；`CliWatchZipCommand.watchDirAndAutoZip:103`（同样丢弃返回的 Runnable cleanup，但该类 finally destroy 停掉**自有** watchService → key 随 doStop 全量 cancel，无残留——路径不同结论不同，已逐一区分）；nop-core `FileWatcher.watch:57-87` 为 cleanup 的生产方（平台侧随 CL-011 移交）。globalTimer 使用点交叉核对——`CliRunCommand:90`（C4-001 域，不重复）。
- **信心水平**: 确定（两条路径的清理差异可代码直读；CDI 为打包默认路径有 NopCliApplication.factory @Inject 实证）
- **误报排除**: 非"finally 有条件 stop 即安全"的表观判定——缺陷是注入路径**零清理**与日志明文契约矛盾；非平台设施本体（FileWatcher/NioFileWatchService 本体随 CL-011 移交），本条限使用方编排。
- **复核状态**: 已保留（WI 4.5 R2 独立复核员逐行回码确认，2026-10-21）

## 补充既有发现的新触发面（不新增编号，复核阶段并入裁定）

- **C4-001 新触发面**: `CliRunCommand.runTasks`(:109-122) 目录形态下 `file.listFiles()` 对瞬时 IO 失败返回 null → :112 `Arrays.sort(subFiles)` NPE → 周期任务首次异常即静默停摆（C4-001 机制）。该触发面**无需脚本错误**，文件系统瞬时不可读（如目录被外部工具原子替换的窗口）即可触发，加重 C4-001 的"长时运行必然触发"论证。建议复核阶段并入 C4-001 的触发时序与 F3 枚举。

## 维度集外线索（overlay）

### [nop-search-runner-autotest-CL-012] RateLimitExecutorImpl.debounce 的 get(:52)/put(:66) 非原子窗口（CL-005 同类补充面，平台 nop-commons）
- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/RateLimitExecutorImpl.java:50-67`
- **线索**: debounce 无 throttle 分支的 compute 原子性——`promiseMap.get(key)` 与 `promiseMap.put(key, future)` 之间无同步，两线程并发调用同 key 时双双调度、旧任务取消失效（防抖保证破产）。当前三个使用点（FileWatcher.triggerChange、CliWatchZipCommand.scheduleZipUpdate）均为 NIO watch 单线程串行触发，未触达；与 CL-005 同归 nop-commons 并发审计单元。

## 继续判定

**判定：C4 维度继续（弱信号保留 1 个方向），其余收敛。** 理由：三个深挖方向中，junit 生命周期线程面确认干净（GlobalExecutors 进程级单例语义 + 无使用点）；NioFileWatchService 其余面已收敛为 C4-007/C6-007/CL-011 三条登记；MockScheduledExecutor 剩余唯一未审面是"IScheduledExecutor 未来新增方法（如 close/awaitTermination 类）的替身对称性"——依赖平台接口演进，非现实缺陷源。若第 3 轮开启，唯一方向是 RateLimitExecutorImpl 修复后的使用方回归面（依赖 CL-005/CL-012 的平台侧裁定结果）。本单元 C4 建议收敛（依赖平台侧两项 CL 的回流）。

## 复核结论（任务 B 追加）

- **C4-006**: 逐行回码确认 exceptionally 包装(:29-32,:41-44,:53-56)、派生 stage 语义、六个入口同缺取消语义；"清理回调挂错分支"与 C6-004 的 no-op 判定相互印证且不重复（C6-004 管队列残留，本条管 cancel 契约）。P2 条件化（零调用方）核对无误。**已保留（P2）**。
- **C4-007**: 回码确认 ：124 ICancellable 丢弃、:100-104 条件清理、:131-132 日志契约、NioFileWatchService.java:58 daemon=false 非守护线程、NopCliApplication factory 注入路径。与 C6-007 同根不同面（本条管"返回后仍执行"，C6-007 管"注册不注销"），交叉引用成立，合并裁定不重复计数。P2 判级核对：单进程实害窗口亚秒、嵌入/反复调用场景实害成立，按"实际触达面（打包默认路径）"不降档口径正确。**已保留（P2）**。
- **C4-001 补充触发面（listFiles null NPE）**: 回码确认 ：111 `File[] subFiles = file.listFiles()` 与 ：112 `Arrays.sort(subFiles)` 无 null 防护。触发链成立，并入 C4-001（见主文件维度复核结论的保留项扩展）。
- **同案命中查重**: C4-006/C4-007 与 cra-index 台账 WI 1.1–4.4 无同案命中（Mock 替身 cancel 契约、CliWatchCommand 返回后残留均为首报）；CL-012 与 CL-005 为同平台类补充面（非重复：CL-005 管 cancel 不 join 语义，CL-012 管 check-then-put 竞态窗口）。
- **第 1 轮五条复核结果与最终保留项见 `C4-thread-pools.md` 的「维度复核结论」「最终保留项」两节。**
