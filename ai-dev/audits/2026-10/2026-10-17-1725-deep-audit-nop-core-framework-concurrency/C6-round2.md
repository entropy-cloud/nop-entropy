# C6 维度第 2 轮深挖报告：资源生命周期与泄漏（nop-core-framework + nop-utils）

- **审计单元**：nop-core-framework 六子模块（nop-ioc / nop-config / nop-plugin / nop-security / nop-log / nop-boot，src/main 口径）+ nop-utils（nop-commons-java21 / nop-diff / nop-fsm / nop-git / nop-image / nop-java-parser / nop-match / nop-router / nop-shell / nop-table-validator，src/main 口径）
- **仓库路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **本轮输入**：第 1 轮报告（同目录 `C6-resource-lifecycle.md`，C6-001~013 + CL-010~013 + 资源获取点配对表 9 项成立面）；任务下发给出的排重边界编号（C1-004/C1-009/C1-010/C4-005/C4-009/CL-001~009/C6-CL-004、C2 轮锁内重活表）仅作 F3 交叉引用与不重复立案边界，内容未从历史文件核实，全部结论以本轮实读 live code 为准。
- **盲审纪律声明**：本轮为盲审第 2 轮。除第 1 轮本维度文件外，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（含基线文件与 C1/C2/C4 各维度报告）；所有调度点/资源获取点枚举均为本轮独立 grep 全扫 + 逐文件实读，未借用基线交叉表内容。
- **本轮目标**（任务下发 ①~⑤，逐条执行完毕）：
  - ① C6-001/002 失效面扩展：loadPlugin 全部失败点资源残留逐一判定 + C6-002 状态机卡死 F3 全枚举；
  - ② 「先调度后加载」族扩展：框架层全部周期任务调度站点穷举；
  - ③ ShellRunner 家族：进程生命周期全路径配对表；
  - ④ destroy 链完整性深化：destroyMethod 反射异常分类 + 部分销毁容器可否安全重启；
  - ⑤ nop-utils 残余资源面 + ImageCompressHelper F3 同族核对。

---

## ① loadPlugin 全失败点资源残留判定矩阵

对 `PluginManagerImpl.loadPlugin` 的双轨全部失败点逐一实读判定（jar 轨 `loadPluginFromJar` L567-596、VFS 轨 `loadPluginFromVfs` L492-500 / `loadVfsDefinition` L507-525 / `reloadPlugin` L638-688）：

| # | 失败点 | 资源状态 | 判定 |
|---|---|---|---|
| 1 | jar 轨 `resolvePluginResource` L570 抛出（坐标校验/下载/SHA） | classloader 尚未创建；`HttpPluginResourceResolver.download` 临时文件有 finally `deleteIfExists`（L194-200）；缓存命中 SHA 不匹配路径 fail-fast 抛出但**不删缓存**（L113-124，注释 L118-119 明示"不静默重下载"——刻意设计非泄漏） | **配对成立**（临时文件面）；缓存残留为设计声明内行为 |
| 2 | jar 轨 `new PluginClassLoader` L571 构造器抛出（`loadPluginConfig` L64-83 缺 plugin.json / parse 失败） | URLClassLoader 已建、`getResource` 已触发 JDK 打开 jar 句柄，异常在 try 外 → 无引用可关 | **C6-001 已立案**（本轮复核确认边界：构造器内 `getResource` 走 URLClassPath 打开的 JarFile 由 classloader `close()` 释放，构造器抛出即无释放入口） |
| 3 | jar 轨 `classLoader.loadPlugin()` L574 抛出（ClassNotFound/ctor 异常） | 同上 | **C6-001 已立案** |
| 4 | jar 轨 `pluginConfigProvider.getPluginConfig` L575 抛出 | classloader + plugin 实例已建，try 外 | **C6-001 已立案**（第 1 轮建议已明示"将 getPluginConfig 一并纳入"） |
| 5 | jar 轨 `plugin.load/start` L577-581 抛出 | try 内，catch+finally `IoHelper.safeCloseObject(classLoader)` L591 | **配对成立**（finally 保证即使 unload/stop 再抛也关 classloader） |
| 6 | VFS 轨 `loadVfsDefinition` L494 抛出（资源不存在/解析错） | 纯解析，无句柄 | **配对成立** |
| 7 | VFS 轨 `plugin.load(emptyMap)` L495 抛出 | `VfsPluginDefinition.load` L358-363 为纯字段赋值（两个 LinkedHashMap 构造），无现实抛出点 | **配对成立**（不可达失败路径） |
| 8 | VFS 轨 `registerNameIndex` L496 / `subscribeGlobalConfig` L497 抛出 | 若 subscribeChange 抛出：holder 未入 `plugins`，但 `namesToPluginIds` 已含 pluginId → 注册表残留 | **判定不成立**：`ChangeSubscriptions.subscribe` L30-50 实读——simple 路径仅 `ConcurrentHashMap.computeIfAbsent` + `addListener`（ArrayList add），无现实抛出点（Guard.notEmpty 对 plugin.xdef 校验过的属性名不可达）；`reloadPlugin` L681-682 同构面同判。按误报排除纪律不入立案 |
| 9 | VFS 轨 `reloadPlugin` deactivate/unload 段 L658-663 抛出 | 状态卡死面 | **C6-002 已立案**（同族 ①②）；load 段失败 L671-675 有注册表回滚（remove + unregister + unsubscribe），**配对成立** |
| 10 | 依赖解析（reconcile：`detectCycles`/`topoSortDefs`/`computeDeactivationGroup`）抛出 | 纯计算无资源；且 `activateIfInactive`/`deactivateIfActive` L394-413 逐定义 catch | **配对成立** |

**矩阵结论**：loadPlugin 失败面除 C6-001 已立案的 2 个前置点外，无未立案资源残留。

### C6-002 状态机卡死 F3 全枚举（第 1 轮 4 站点之外）

对 `VfsPluginDefinition` / `AbstractPlugin` 六态状态机全部转移逐一核验：

| 转移 | 失败回滚 | 判定 |
|---|---|---|
| VFS `load`（→LOADED） | 纯赋值不可失败 | 无卡死面 |
| VFS/Abstract `unload`（→UNLOADED） | 守卫抛出发生在赋值前，赋值不可失败 | 无卡死面 |
| VFS/Abstract `activate`（→ACTIVATING→ACTIVATED） | rollbackActivation 内 `c.stop()` 抛出 → `state=FAILED` 被跳过 | **C6-002 ②④ 已立案**（本轮复核确认现行代码仍卡 ACTIVATING） |
| VFS/Abstract `deactivate`（→DEACTIVATING→LOADED） | `s.close()`/`c.stop()` 抛出 → `state=LOADED` 不可达；引用已先置 null | **C6-002 ①③ 已立案** |
| Abstract 非 aware `stop`（doStop L350-358） | `LifeCycleSupport.stop` 实读（nop-kernel L77-93）：doStop 异常 **LOG.error 后吞掉**，finally 中状态落 `CREATED`（allowRestart=true 缺省）——状态可复位，无卡死 | 第 1 轮判定维持，非同缺陷 |
| VFS `start`/`stop`（load+activate / deactivate+unload 组合） | 复合路径，卡死面即上述 activate/deactivate 面 | 已被 C6-002 覆盖 |
| `updateConfig` | 无状态转移 | 不适用 |

**新增候选排除记录（重要）**：本轮曾立候选缺陷"双轨插件 activate 路径 `container.start()` 失败时 `beanContainer`/`scope`/`container` 字段尚未赋值（VFS L481-483、Abstract L230 均在 start 之后），rollbackActivation 扑空 → 半启动容器泄漏"。实读 `BeanContainerImpl.start` L519-523 后**排除**：`start()` 自带失败自愈——`catch (Exception e) { handleStartError(e); throw e; }`，`handleStartError` L526-533 调 `stop()` 销毁已建 singleton bean（其内部 stop 失败也被 L530-531 捕获记录），异步路径（concurrentStart）经 `whenComplete` 回调走同一 `handleStartError`。故 `container.start()` 抛出时容器已自行 stop，rollback 扑空无害；`builder.build`（BeanContainerBuilder L319-357 实读）只组装定义不创建 bean 实例，`new PluginScopeImpl` L39-41 构造器为纯字段赋值不可抛。**该候选不成立，登记为复核排除面。**

---

## ② 「先调度后加载」族全枚举

框架层 + nop-utils（src/main 全量 grep：`globalTimer()`、`scheduleWithFixedDelay`、`scheduleAtFixedRate`、`.schedule(`、`newScheduledThreadPool`、`newSingleThreadScheduledExecutor`、`new Timer`、自循环 submit、`while(true)` 后台循环、`new Thread`）：

| 站点 | 形态 | 判定 |
|---|---|---|
| `JdbcConfigSource` 构造器 L54 | 周期任务，先调度后加载 | **C6-003 已立案** |
| `AbstractFileConfigSource` 构造器 L45 | 同上 | **C6-003 已立案**（含 KeyFile/PropsFile 子类直通面） |
| `ShellRunner.scheduleTimeout` L226 | 一次性超时 timer（非周期） | 无滞留面：finally L122-123 恒达 cancel；唯 E/F 窗口（见 ③/C6-014）cancel 不达，但该 timer 恰是兜底销毁执行者，滞留无害 |
| `BeanContainerImpl.start` L542 `TaskExecutionGraph(globalWorker)` | 启动期一次性并行执行图，非周期、随 start 完成 | 不适用 |
| `ConfigChangeApplier.requestUpdate` L43 / `SingleThreadConfigExecutor` L29 | 事件驱动单发 execute，非周期注册 | 不适用（REE 拒绝后果已由 C6-005 证据链覆盖） |
| nop-log / nop-boot / nop-security | grep 零命中 | 不适用 |

**枚举结论**：本单元范围内"构造/启动期调度周期任务，初始执行失败后任务滞留"族**无第 3 个站点**——C6-003 的 2 个站点即全族。注：按盲审纪律未读取 C4 基线节与 C2 锁内重活表，本枚举为独立 grep 全扫结果，交叉待汇总轮统一。

---

## ③ ShellRunner 进程资源配对表（全生命周期路径）

`ShellRunner.run` L73-143 逐路径实读：

| # | 路径 | 进程销毁 | 管道 fd | stderr 读取线程 | timer | 判定 |
|---|---|---|---|---|---|---|
| 1 | 成功：readOutput EOF + waitFor 返回 | 自然退出 | 随进程退出由 JDK ProcessImpl 回收 | EOF 退出 | cancel（finally） | **配对成立** |
| 2 | stdin 写断管（L95 IOException） | **无销毁** | 泄漏 | 子进程不退则永阻塞 | cancel | **C6-004 时序 A 已立案** |
| 3 | 中断（waitFor/readLine 阻塞点） | **无销毁** | 泄漏 | 同上 | cancel | **C6-004 时序 B 已立案** |
| 4 | collector 抛 RuntimeException | **无销毁** | 泄漏 | 同上 | cancel | **C6-004 时序 C 已立案** |
| 5 | waitFor(diff) 超时子路径（L112 false → L116 stopped） | cancel 先于 timer 触发 → **无销毁** | 泄漏 | 同上 | 被 cancel | **C6-004 时序 D 已立案** |
| 6 | timer 主路径（safeDestroy L259-271） | destroy（SIGTERM） | safeClose 三管 | stopped → IOException 退出 | 到期执行 | **配对成立**（但无 `destroyForcibly()` 升级——忽略 SIGTERM 的子进程存活，C6-004 建议已含该次级面） |
| 7 | timeout=0 阻塞 | 无 timer，waitFor 无限阻塞 | — | — | — | **C4-005 已立案**（交叉引用，不重复） |
| 8 | **E：`scheduleTimeout` L226 抛 RejectedExecutionException（globalTimer 已 shutdown）** | **无销毁、无 timer** | 泄漏 | 读取线程已提交或未提交 | 未调度 | **本轮新立案 → C6-014** |
| 9 | **F：`collectError` L153 `cachedThreadPool().submit` 抛 RejectedExecutionException（池已 shutdown）** | timeout>0 时已调度 timer 在截止点兜底销毁（延迟、有界，且该 timer 不再被 cancel——停机后仍触发一次 safeDestroy，为兜底性质）；**timeout==0 时永久泄漏** | 泄漏 | 未提交 | 无法 cancel | **本轮新立案 → C6-014** |
| 10 | `newProcess` L83 抛 IOException | 进程未创建 | — | — | 未调度 | **配对成立** |

补充核查：临时目录——`ShellRunner`/`ShellCommand`/`ShellCommands` 实读均不创建临时目录（`CFG_SHELL_TASK_ROOT_DIR` 为脚本任务约定目录，无生命周期管理对象），该检查项不适用；stdout/stderr 读取线程退出条件（EOF/`stopped`/IOException）在路径 1/6 成立、在路径 2-5/8-9 为 C6-004/C6-014 的放大器面，不独立立案。

---

## ④ destroy 链完整性深化（分析交付，无新缺陷）

### 4.1 destroyMethod 反射调用异常分类

调用链实读：`BeanDefinition.destroyMethod` 类型为 `IFunctionModel`（L118），实现为 `FunctionModel`，invoker 构建链 `MethodModelBuilder.buildMethodInvoker` L186-188 → `MethodInvoker.invoke` L30-32 → `ClassHelper.invoke(Method,...)`（nop-kernel L748-760）。分类结论：

| destroy 方法抛出物 | 传播形态 |
|---|---|
| RuntimeException | 原样传播（`NopException.adapt` L225-227 直接返回） |
| Error | `ClassHelper.invoke` 只 catch `Exception`，Error 原样穿透，且 `NopException.adapt` L222-223 对 Error 是 rethrow |
| 受检异常（如 `close() throws IOException`） | `InvocationTargetException` 被**解包**：`NopException.adapt(e.getTargetException())` → 非 RT 异常包装为 `NopWrapException`（cause 保留） |
| ITE 本体 | 不会泄漏到调用方（解包后即抛 target） |

**对已立案 C6-007 的边界确认**：无论何种异常类型，`destroyMethod.call0`（BeanDefinition L714-715）都会抛出 → `runXpl(iocDestroy)` 被跳过的结论对**全部异常分类成立**，无异常形态能绕过该缺陷面。次级观察：`ClassHelper.invoke` L752-753 将业务异常以 **INFO 级**记录（`invoke-method-fail`），随后 BeanScopeImpl.close L109 以 ERROR 再记——同一失败双记且首记降级，排障时可能误导，属卫生问题不立案。

### 4.2 bean 销毁失败后的 scope 状态与部分销毁容器可否安全重启

实读链条：`BeanScopeImpl.close` L96-118 → `remove` L81-87 → `BeanContainerImpl.destroyBean` L624-631 → `BeanDefinition.destroyBean` L704-718；`BeanContainerImpl.stop` L595-617；`restart` L452-455。

1. **注册表语义**：`remove` **先摘 CHM 条目再调 destroy**——destroy 抛错时条目已移除不回滚。故部分销毁失败后 `beans` 必空，`close` L113-115 的 `!beans.isEmpty()` 抛出为防御性死路径（另注：该路径抛的是 `ERR_IOC_BEAN_SCOPE_ALREADY_CLOSED`，错误码语义与实际条件不符，卫生观察项）。
2. **stop 失败后可否重启**：`stop()` 中 singletonScope 异常被 L608-611 捕获存储，`onContainerStop` L613 在 try 外（异常遮蔽面 = C6-007 ⑤ 已立案）；无论 stop 是否抛出，`running/started` 已在 L597-598 置 false、scope 条目已清空、`singletonScope` 字段未置 null 但 `restart→start` L484 直接以新实例覆盖。**对已停止容器再次调用 stop 幂等**（close 对空 map 静默返回），故 stop 失败后的 `restart()` 首次调用会在 stop 段上抛中止（start 未执行），**重试一次 restart 即可恢复**——容器生命周期本身可恢复，无永久卡死。
3. **泄漏残留**：销毁半途失败的 bean 实例已不可达，其 destroy 未完成部分持有的资源（线程池/连接/临时文件）泄漏——即 C6-007 已立案后果，非新面。
4. **与 C4-009 restart 代际竞态交叉**：scope 条目先摘后毁的语义保证旧代际条目不跨入新 singletonScope（L484 全新实例），C6 侧无代际残留贡献面；竞态本体维持 C4 已立案口径。
5. **Error 穿透面**：`BeanScopeImpl.close` L108 只 catch `Exception`——destroy 抛 Error 时立即冲出 close 循环（剩余 bean 未销毁）→ `stop()` L608 只 catch RuntimeException → Error 冲出 stop → `onContainerStop` 被跳过。与 C6-007 的"异常聚合缺位"同根（聚合方案应含 Error/Throwable 口径），随 C6-007 修复一并覆盖，不独立立案。

---

## ⑤ nop-utils 残余资源面

全模块资源获取点 grep（Process/Stream/Channel/ZipFile/WatchService/Socket/TempFile/Executor/Thread 形态）+ 逐文件复核：

| 模块 | 获取点 | 判定 |
|---|---|---|
| nop-shell | ShellRunner 全套 | ③/C6-004/C6-014 覆盖 |
| nop-shell | `OsUserHelper.getUserListFromLinux` L78-79 FileInputStream | try-with-resources，**配对成立** |
| nop-shell | `ShellCommands.permitFileExecutable` L133 attribute view | 非 Closeable 资源，不适用 |
| nop-commons-java21 | `VirtualThreadTaskExecutor` | **C6-010 已立案**（本轮复核三面证据不变） |
| nop-git | `GitRepositoryImpl` | **C6-012 已立案**（F3 ②③④ 维持） |
| nop-image | `ImageCompressHelper` | **C6-013 已立案**；F3 同族核对：`readImage` L31-36 finally safeCloseObject 成立；`resizeImage` L131-136 `g2d.dispose()` 无 try/finally（drawImage 抛错不 dispose）——与第 1 轮 F3 ④ 登记的次级面一致，无新站点；`compressImageWithLimit` 降质循环逐轮新建 ImageWriter 放大获取次数——第 1 轮已注明，维持 |
| nop-diff / nop-fsm / nop-match / nop-router / nop-java-parser / nop-table-validator | grep 零命中（纯内存计算，无进程/流/临时资源获取） | 不适用 |
| nop-log / nop-boot / nop-security | `RsaHelper` L34 FileOutputStream try-with-resources；`ConfigStarter.configLogLevel` L499-504 订阅 cleanup 经 `cancellable.appendOnCancelTask` + doStop L527 `cancellable.cancel()` 成对 | **配对成立**（新增成立面 3 项） |

---

## 发现清单（本轮新增 1 条）

### [nop-core-framework-C6-014] ShellRunner 进程创建与内层 try 之间的失败窗口无销毁兜底：调度器/线程池拒绝时子进程永久泄漏（C6-004 修复位置约束面）

- **文件**: `nop-utils/nop-shell/src/main/java/io/nop/shell/ShellRunner.java:83-92, 121-127, 224-230`
- **证据片段**:
  ```java
  final Process process = this.newProcess(command);            // L83：进程已创建
  final Future<?> timerFuture = scheduleTimeout(command, () -> {   // L85：可抛 REE
      safeDestroy(command, process, stopped);
      return null;
  });
  final Future<?> errorFuture = collectError(command, process, stopped, collector); // L90：submit 可抛 REE
  try {                                                        // L92：内层 try 从这里才开始
      ...
  } finally {
      if (timerFuture != null) timerFuture.cancel(false);      // L122-126：仅 cancel future，不 safeDestroy
      if (errorFuture != null) errorFuture.cancel(false);
  }
  ```
  `scheduleTimeout` L225-229：`GlobalExecutors.globalTimer().schedule(...)`，执行器 shutdown 时抛 RejectedExecutionException；`collectError` L153：`GlobalExecutors.cachedThreadPool().submit(...)` 同理。两个调用点均位于内层 try（L92）**之前**，内层 finally（L121-127）与外层 try（L80，无 finally）均不覆盖。
- **严重程度**: P2（条件性：触发需 globalTimer/cachedThreadPool 处于 shutdown 窗口——平台 CoreInitialization.destroy 与在飞 ShellRunner 调用交叠；但后果为 OS 进程 + 管道 fd 跨停机永久泄漏）
- **并发场景**: 调用线程执行外部命令 vs 停机线程执行 CoreInitialization.destroy（GlobalExecutors 关闭窗口）；timeout=0 与 timeout>0 变体后果不同（见触发时序）。
- **触发时序**:
  - 面 E：T1 停机窗口内调用线程已完成 `newProcess`（L83，子进程已起）→ L85 `globalTimer().schedule` 抛 REE → 异常经外层 catch L131 包装上抛 → **无任何代码路径触达 `safeDestroy`**：进程、三根管道 fd 永久泄漏，cachedThreadPool 上的 stderr 读取线程（若 L90 已提交）永久阻塞在 readLine。
  - 面 F（timeout>0）：L85 调度成功 → L90 `cachedThreadPool().submit` 抛 REE → 内层 try 未进入、finally 不执行 → 已调度 timer 无法 cancel，将在截止点执行 `safeDestroy` 兜底销毁进程（**延迟清理、量级有界**）——但该 timer 任务在平台 destroy 之后仍会在 globalTimer 上运行一次（停机后幽灵任务，就本条而言是兜底性质，可接受）。
  - 面 F'（timeout==0）：`scheduleTimeout` 返回 null、无 timer → 与面 E 同为**永久泄漏**，且与 C4-005（timeout=0 无限等待，已立案）叠加：调用线程悬挂 + 进程无主。
- **现状**: 进程销毁的唯一入口（timer 回调内的 safeDestroy）与内层 finally 都不在 L83-L92 窗口的异常路径上；C6-004 已立案的修复建议（"将 safeDestroy 纳入 finally 兜底"）若实现为**内层 finally**，本条 E/F/F' 三面不会被覆盖——修复必须置于外层 try（L80）的 finally。
- **风险**: 停机窗口内每次在飞 shell 调用泄漏一个存活子进程 + fd（+1 个被 pin 的池线程）；进程存活跨越平台 destroy，嵌入式宿主反复 destroy→reinit 时累积。判级 P2 而非 P1：触发窗口仅存在于执行器 shutdown 期间，非常规业务失败路径。
- **建议**: 与 C6-004 合并修复：将 `safeDestroy(command, process, stopped)` 置于**外层** try/finally（L80 try 对应的 finally），以 `stopped.get()` 判断幂等（timer 已销毁时 destroy 幂等无害）；`scheduleTimeout`/`collectError` 自身抛出的 REE 不需要特殊处理，外层 finally 即可覆盖。修复后本条与 C6-004 的全部路径（A-D、E、F、F'）由同一兜底收敛。
- **失效面枚举**（F3 同族清扫）: ① `scheduleTimeout` L226 REE（本条面 E，无 timer 永久泄漏）；② `collectError` L153 submit REE + timeout>0（本条面 F，timer 延迟兜底 + 停机后幽灵 timer 一次）；③ 同上 + timeout==0（本条面 F'，永久泄漏，与 C4-005 叠加）；④ `newProcess` L83 抛 IOException——进程未创建，配对成立不适用；⑤ 内层 try 内全部失败路径——C6-004 已立案，本条仅为修复位置约束（外层 finally），不改变其立案内容；⑥ `safeDestroy` 无 destroyForcibly——C6-004 建议已含，不重复。
- **信心水平**: 确定（两个调用点在 L92 之前、外层 try 无 finally 为直接代码事实；REE 语义为 JDK 标准契约）
- **误报排除**: 非已立案 C6-004 的重复——该条证据全部位于内层 try 内（stdin/readOutput/waitFor/interrupt），F3 未含 L85/L90 抛出点；也非"timer 必然兜底"误判——面 E/F' 无 timer 可言。修复位置差异（内层 vs 外层 finally）对修复方案有实质约束，故独立立案而非并入 C6-001 式扩展注记。
- **复核状态**（维度复核 2026-10-17）: **保留 P2（条件性），修正触发归因**。代码事实全部亲核成立：L83 进程已建、L85 `scheduleTimeout`（scheduleTimeout L224-230 经 `globalTimer().schedule` 可抛 REE）、L90 `collectError`（L153 `cachedThreadPool().submit` 同理）均在内层 try（L92）之前；外层 try（L80）无 finally；面 E/F/F' 三分法（E 无 timer 永久泄漏 / F timer 延迟兜底 + 停机后幽灵任务一次 / F' 与 C4-005 叠加永久泄漏）核实正确；「修复必须置于外层 finally」的位置约束成立（内层 finally 不覆盖 L83-L92 窗口）。**归因修正**：条目并发场景称「停机线程执行 CoreInitialization.destroy（GlobalExecutors 关闭窗口）」——经复核，框架内无任何 initializer/destroy 链关闭 globalTimer/cachedThreadPool（全仓 grep：`allExecutors()` 无 destroy 调用方；`GlobalExecutors.register` 唯一调用链 = VirtualThreadTaskExecutor 换名注册，不触及两池），REE 窗口实际仅在宿主/测试显式销毁全局执行器或替换同名注册时存在——条件性 P2 维持（窗口真实但比条目表述更窄），触发归因以本修正为准。与 C6-004 合并修复（同一外层 finally 兜底）的方案成立。现状：新发现未修复。

---

## 维度集外线索（本轮新增 1 条）

### [nop-core-framework-CL-021]（维度集外线索，不立案展开）OsUserHelper.createMacUser 将口令拼入子进程命令行，进程列表可见

`nop-shell/utils/OsUserHelper.createMacUser` L210-216：`sudo sysadminctl -addUser %s -password %s` 将明文口令作为进程参数——日志侧已做掩码（L215），但 `/bin/ps`/进程账目在命令执行期间对同机其他用户可见该参数（`ps` 进程 args 泄露口令为经典命令行敏感信息面）。Linux/Windows 路径无口令参数不受影响。属安全维度（C3/安全审计口径），移交裁量。
- **复核处置**（维度复核 2026-10-17）: **确认**。亲核成立：`String.format("sudo sysadminctl -addUser %s -password %s", userName, userName)`（口令实为 userName，L211）拼入进程参数，日志掩码（L215）仅护日志面不护进程账面；`runCommand` → ShellRunner 起子进程期间 `/bin/ps` 可见。安全线移交成立。

---

## 本轮配对成立面增补（第 1 轮 9 项之外）

| 资源 | 获取点 | 释放点 | 判定 |
|---|---|---|---|
| 容器启动失败自愈 | `BeanContainerImpl.start` L486-523 | catch → `handleStartError` → `stop()` 销毁已建 bean（同步/异步双路径） | 配对成立（含 stop 失败再捕获） |
| 下载临时文件 | `HttpPluginResourceResolver.download` L161 | finally L194-200（第 1 轮已确认，本轮维持） | 配对成立 |
| SHA 摘要流 | `calculateSha256` L292 | try-with-resources | 配对成立 |
| 日志级别订阅 | `ConfigStarter.configLogLevel` L499-504 | `cancellable.appendOnCancelTask` → doStop L527 cancel | 配对成立 |
| 服务启动失败回滚 | `LifeCycleSupport.start` L66-69 | catch → `stop()`；stop 异常吞掉、状态可复位（L77-93） | 配对成立 |
| /etc/passwd 流 | `OsUserHelper` L78-79 | try-with-resources | 配对成立 |
| reloadPlugin 注册表回滚 | `PluginManagerImpl` L671-675 | remove + unregisterNameIndex + unsubscribeGlobalConfig | 配对成立 |

---

## 汇总与终止信号判定

**本轮新增**：C6-014（P2，条件性）×1；CL-021 ×1；配对成立面增补 7 项；重要复核排除 1 项（"activate 回滚扑空泄漏"候选经 `BeanContainerImpl.start` 自愈链实读否证）。

**①~⑤ 执行情况**：① loadPlugin 全失败点 10 项判定完毕（8 项配对/已立案、2 项 C6-001、1 项误报排除）；② 周期调度族全枚举 = C6-003 两站点即全族，无第 3 站点；③ 进程全路径配对表 10 项，新窗口立案 C6-014，其余归 C6-004/C4-005；④ 反射异常分类完成（ITE 解包/受检异常 NopWrapException 包装/Error 穿透，C6-007 边界对全部异常形态成立）、部分销毁容器 restart 判定"注册表可恢复、半毁实例泄漏属 C6-007 后果、stop 幂等可重试"；⑤ nop-utils 残余面 grep 全扫零未立案获取点，ImageCompressHelper F3 与第 1 轮一致。

**终止信号判定**：本轮新增全部为 P2/P3 量级（P2×1 + CL×1），无新增 P0/P1；五项深挖指令的枚举面均已收敛——调度族、插件失败矩阵、进程生命周期、destroy 链、nop-utils 资源面均达到"逐站点判定完毕、无未判定残留"状态；剩余开放面均为他维已立案项（C4-005/C4-009/C1-009、CL-006/007/008、C6-CL-004）或已明确移交单元（nop-kernel/nop-http-api）。**判定：C6 维度在本审计单元达到终止信号，不建议第 3 轮**——除非复核推翻 C6-014，或 C6-001/002/004 修复方案变更引入新失败路径。

**累计（本单元 C6 两轮）**：发现 14 条（P1×4、P2×9、P3×1）+ CL 5 条（CL-010~013、CL-021）。
