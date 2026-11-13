# C6 维度初审报告：资源生命周期与泄漏（nop-core-framework + nop-utils）

- **审计单元**：nop-core-framework 六子模块（nop-ioc / nop-config / nop-plugin / nop-security / nop-log / nop-boot，主源码 229 文件 / 25,867 行）+ nop-utils（nop-commons-java21 / nop-diff / nop-fsm / nop-git / nop-image / nop-java-parser / nop-match / nop-router / nop-shell / nop-table-validator，主源码约 109 文件 / 12,483 行）
- **仓库路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度定义（C6）**：destroy/init 生命周期完整性、关闭链顺序与异常传播、注册/注销配对、连接/流/句柄/类加载器泄漏、线程池关闭责任。一切分配的资源在成功/失败/取消/中断/超时全部路径上被正确释放（对照缺陷族 F8 清理不对称、F9 静默吞异常、F2 半个成功）。
- **盲审纪律声明**：本轮为盲审第 1 轮。审计过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅阅读了方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md` 与当前 live code。任务下发中给出的已知立案编号（C1-004/C1-009/C1-010/C4-005、CL-001~009、C6-CL-004）仅用作"不重复立案"的排重边界，其内容未从历史文件核实，全部结论以本轮实读代码为准。
- **判级口径**：框架层被全平台使用；按任务要求，"仅启动期单线程触达"站点维持条件化降档（本报告中 C6-003 虽触发于启动期，但泄漏对象是常驻运行期任务/连接，泄漏窗口延伸至全生命周期，不适用启动期降档）。

## 检查范围说明

逐文件审读了生命周期核心链：`BeanContainerImpl`（start/stop/destroyBean）、`BeanScopeImpl`（close/add/remove）、`BeanScopeContextImpl`（bind/unbind/onContainerStop）、`BeanDefinition`（destroyBean/subscribeConfigChange）、`IocCoreInitializer`、`AppBeanContainerLoader`、`CoreInitialization` + `NopApplication` + `ShutdownHook`（关闭链，后者仅审链路不重复立案）、`PluginManagerImpl` / `VfsPluginDefinition` / `AbstractPlugin` / `PluginScopeImpl` / `PluginClassLoader` / `HttpPluginResourceResolver` / `IPluginManager`、`JdbcConfigSource` / `JdbcConfig` / `AbstractFileConfigSource` 及两个子类 / `ConfigStarter` / `ConfigInitializer` / `DefaultConfigProvider` / `ChangeSubscriptions` / `ChangeSubscription` / `ConfigChangeApplier` / `SingleThreadConfigExecutor`、`DefaultKeyManager` / `CompositeKeyManager`、`Log4j2Configurator` / `LoggerConfigurator`、`NopApplication` / `NopBanner` / `NopBootConfigs`、`ShellRunner` / `DefaultShellOutputCollector`、`VirtualThreadTaskExecutor` / `Jdk21CommonsCoreInitializer`、`GitRepositoryImpl` / `GitRepositoryManagerImpl`、`ImageCompressHelper`；对其余模块文件做了资源获取点 grep 全扫（Process/Executor/Stream/URLClassLoader/acquire/close/destroy 形态）。

**范围裁定**：`DefaultTaskExecutionQueue` 位于 `nop-kernel/nop-core/execution`，不在 nop-ioc，按任务下发的条件性归属（"若在 ioc"）不进入本单元，移交 nop-kernel 单元。`ShutdownHook` 本体、`DefaultThreadPoolExecutor.destroy()`、`GlobalExecutors`、`Cancellable` 位于 nop-kernel/nop-commons，只作为使用方站点的证据链引用，不重复立案。

### 资源获取点配对表

| 资源 | 获取点 | 释放点 | 配对判定 |
|---|---|---|---|
| PluginClassLoader（URLClassLoader） | `PluginManagerImpl.loadPluginFromJar` L571 | `unloadPlugin` L162、load 失败 finally L591 | 失败：构造器/loadPlugin 两点在 try 外 → **C6-001** |
| 插件子容器 IBeanContainer | `AbstractPlugin.buildBeanContainer` L225-230、`VfsPluginDefinition.activateInternal` L475-477 | doDeactivate/rollbackActivation/doStop 的 `c.stop()` | 失败：字段先置 null、stop 抛错状态卡死 → **C6-002** |
| globalTimer 定时刷新任务 | `JdbcConfigSource` 构造器 L54、`AbstractFileConfigSource` 构造器 L45 | `close()` cancel | 失败：构造器 loadConfig 抛错后无引用可关 → **C6-003** |
| JDBC Connection | `JdbcConfigSource.loadConfig` L111-113 | `closeConnection`（refreshConfig catch / close） | close 竞态已立案（C1-009，不重复）；构造失败路径并入 C6-003 |
| 子进程 Process | `ShellRunner.newProcess` L83 | `safeDestroy`（仅超时路径触达）；正常路径 waitFor 自终 | 非 timeout 异常/中断路径无释放 → **C6-004** |
| 刷新任务生命周期 | `scheduleWithFixedDelay`（jdbc/file） | close cancel | 监听器异常使任务永久静默死亡 → **C6-005** |
| singleton scope 条目 | `BeanScopeImpl.add` | `close()`→`remove`（先摘条目再 destroy） | destroy 顺序任意 → **C6-006**；异常传播 → **C6-007** |
| 自定义 scope 注册条目 | `BeanScopeContextImpl.bind` | `unbind`（runWithScope 配对）/ `onContainerStop` | onContainerStop 关闭不摘条目 → **C6-009** |
| VirtualThread ExecutorService | `VirtualThreadTaskExecutor` L17 | `destroy()` shutdown（无 await） | 初始化器无 destroy、不注销 → **C6-010**（C6-CL-004 深化） |
| 全局配置订阅（plugin） | `PluginManagerImpl.subscribeGlobalConfig` | `unsubscribeGlobalConfig`（unload/reload 成败两径） | 成功；无聚合关闭入口 → **C6-011** |
| JGit Repository | `GitRepositoryImpl` 构造器 L81 | `close()` L393 | 所有权完全在调用方、manager 不追踪 → **C6-012** |
| javax.imageio ImageWriter | `ImageCompressHelper` L156 | `dispose`+`close`（仅成功路径） | 异常路径缺 → **C6-013** |
| KeyStore/口令 char[] | `DefaultKeyManager.init` | `@PreDestroy destroy`（nop-ioc `DefaultBeanClassIntrospection` L70 确认识别 PreDestroy） | 配对成立 |
| 下载临时 JAR | `HttpPluginResourceResolver.download` L161 | finally `deleteIfExists` L194-200 | 配对成立（move 成功后 sha 写失败回删 jar 亦有） |
| 插件 effect/Disposable | `PluginScopeImpl.effect` | `close()` LIFO、单 effect 异常记录继续 | 配对成立 |
| statement/resultSet（jdbc） | `JdbcConfigSource.loadConfig` L121-122 | finally safeClose L133-136 | 配对成立 |
| 目录流（Files.walk） | `AbstractFileConfigSource.loadConfig` L86 | try-with-resources | 配对成立 |
| stdin 流（子进程） | `ShellRunner` L95 | finally safeClose L99 | 配对成立 |

---

## 发现清单

### [nop-core-framework-C6-001] jar 轨插件加载失败路径泄漏 PluginClassLoader 与 JAR 文件句柄

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java:567-596`
- **证据片段**:
  ```java
  private IPlugin loadPluginFromJar(ArtifactCoordinates coords) {
      String id = coords.toString();
      return plugins.computeIfAbsent(id, k -> {
          List<URL> urls = resourceResolver.resolvePluginResource(coords);
          PluginClassLoader classLoader = new PluginClassLoader(urls.toArray(new URL[0]),
                  this.getClass().getClassLoader());          // L571：构造器可抛

          IPlugin plugin = classLoader.loadPlugin();           // L574：可抛
          Map<String, Object> config = pluginConfigProvider.getPluginConfig(coords);
          try {                                                // L576：try 从这里才开始
              ...
          } catch (RuntimeException e) {
              try { ... } finally {
                  IoHelper.safeCloseObject(classLoader);       // L591：仅 try 内失败才关闭
              }
              throw e;
          }
      }).plugin;
  }
  ```
- **严重程度**: P1
- **并发场景**: 调用方线程（管理操作/reconcile 触发的 load）；与 `plugins`（ConcurrentHashMap）并发无竞争关系，属单线程内失败路径资源泄漏。
- **触发时序**: T1 = 部署一个缺 `plugin.json`（或 pluginClassName 为空/损坏）的 uber jar，宿主调 `loadPlugin("g:a:v")`：
  1. L570 resolver 命中缓存 jar 并返回 URL（jar 已在磁盘）；
  2. L571 `new PluginClassLoader(...)` 构造成功，父类 URLClassLoader 就绪；
  3. 构造器内 `loadPluginConfig()` L64-83 经 `getResource` 触发 JDK 打开该 jar 的 JarFile 句柄后抛 `ERR_PLUGIN_NO_PLUGIN_CLASS_NAME`（或 L574 `loadPlugin()` 抛 ClassNotFoundException/ctor 异常）；
  4. 异常发生在 try 块（L576）之外 → `IoHelper.safeCloseObject(classLoader)` 不执行 → URLClassLoader 及其打开的 jar 句柄永不关闭；
  5. 异常使 computeIfAbsent 不建立映射 → 调用方重试/换坐标再试时 lambda 重新执行，再 new 一个全新 PluginClassLoader → 每次失败新增一个未关闭 classloader + 一份 jar 句柄，线性累积。
- **现状**: 只有 L576 之后的失败（`plugin.load/start/unload/stop` 阶段）才有 finally 兜底关闭；构造器抛错与 `loadPlugin()` 抛错两个前置失败点完全无释放路径。
- **风险**: 每次失败泄漏一个 URLClassLoader + jar 文件句柄 + 已加载的部分类（Metaspace）。持续失败的坏包被周期性重试时（如运维脚本轮询 loadPlugin）句柄数与 Metaspace 单调增长；Windows 上打开的 jar 句柄还会锁住缓存文件（该文件同目录的 `PluginClassLoader.parsePluginConfig` 注释 L87-89 正是为此问题写过规避，说明句柄占用是实际已知痛点）。错误路径资源泄漏，按判级校准 P1。
- **建议**: 把 L571-575 全部纳入 try（在 lambda 内先声明 `PluginClassLoader classLoader = null;`，try 内赋值，catch/finally 中 `IoHelper.safeCloseObject(classLoader)` 后再抛）；同时将 `getPluginConfig` 一并纳入。复用 `io.nop.commons.util.IoHelper.safeCloseObject`，与 L591 现有兜底同构。
- **失效面枚举**（F3 同族清扫）: ① `loadPluginFromJar`（本条，未修复）；② `loadPluginFromVfs` L492-500——VFS 轨 holder.classLoader 恒为 null，无此类资源，不适用；③ `unloadPlugin` L145-165——失败路径保留 classLoader 是有意可重试设计（注释 L154-158 明示），不算缺陷；④ `reloadPlugin`——仅 VFS 轨，无 classloader，不适用；⑤ `PluginClassLoader` 构造器自身——抛错即泄漏（本条覆盖）；⑥ `HttpPluginResourceResolver.download`——临时文件有 finally 清理，配对成立。
- **信心水平**: 确定
- **误报排除**: 不是"低频路径粗粒度同步"类豁免：泄漏对象（classloader/jar 句柄）随失败次数无界累积，且失败原因（坏包）在真实部署中常见且持续；也非有界缓存声明内的行为。
- **复核状态**（维度复核 2026-10-17）: **保留 P1**。全部证据亲核成立：L571 构造器（`PluginClassLoader.loadPluginConfig` L60 经 getResource 触发 jar 打开后可抛 `ERR_PLUGIN_NO_PLUGIN_CLASS_NAME`，PluginClassLoader.java L64-81 亲读）、L574 `loadPlugin()`、L575 `getPluginConfig` 三点均在 try（L576）之外；computeIfAbsent 失败不建映射 → 重试线性累积成立。生产可达性亲核：HttpPluginResourceResolver 为远程插件仓库部署形态设计（`nop.plugin.service-url` + SHA256 三源校验），属设计内生产形态。两点补注（不改判级）：① 本仓内 PluginManagerImpl 无 beans.xml 装配点（全仓仅测试实例化），与 C6-011 条件化口径一致，宿主装配为预期用法；② 泄漏对象不可达后经 GC/cleaner 最终可回收（JDK JarFile 自带 cleaner），「永不关闭」精确表述为「无确定性释放入口、泄漏窗口不确定且重试压力下线性累积」。现状：新发现未修复。

---

### [nop-core-framework-C6-002] 插件 deactivate/activate 失败路径状态机永久卡死，半停容器引用置空不可达

- **文件**:
  `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java:194-201, 236-242, 256-274`；
  `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java:408-422, 507-522, 434-458`
- **证据片段**（VfsPluginDefinition.doDeactivate；AbstractPlugin 同构）:
  ```java
  private void doDeactivate() {
      synchronized (lifecycleLock) {
          if (state != PluginState.ACTIVATED) { return; }
          this.state = PluginState.DEACTIVATING;      // 先进中间态
          PluginScopeImpl s = scope;
          IBeanContainer c = container;
          scope = null; container = null; configProvider = null;  // 引用先置空
          try {
              if (s != null) { s.close(); }           // effect 抛错 → 传播
          } finally {
              if (c != null) { c.stop(); }            // 容器 stop 抛错 → 传播
          }
          this.state = PluginState.LOADED;            // 任何异常都到不了这里
      }
  }
  ```
  activate 侧同构：`catch (RuntimeException | Error e) { rollbackActivation(); this.state = PluginState.FAILED; ... }` —— `rollbackActivation()` 内 `c.stop()` 抛错时，`state=FAILED` 与 `lastActivationError` 赋值被跳过，状态停在 ACTIVATING。
- **严重程度**: P1
- **并发场景**: deactivate 可从 config 执行器线程（reconcile 的 `deactivateIfActive`）、管理调用线程进入；错误源是任意 bean destroy-method / effect dispose 抛出的 RuntimeException（经 `BeanContainerImpl.stop` L604-616 原样上抛、`PluginScopeImpl.close` 单 effect 仅记录不抛、但 `BeanScopeImpl.close` 末尾会上抛最后一个 destroy 异常）。
- **触发时序**: T1 管理线程调 `deactivatePlugin(p)` → 定义进入 DEACTIVATING、`container/scope` 字段置 null → 子容器内某 bean 的 destroy-method 抛 RuntimeException → `BeanContainerImpl.stop()` 将其重新抛出 → `doDeactivate` 的 `this.state = LOADED`（L456）永不可达，异常经 `FutureHelper.futureCall` 装入返回 future。此后该插件进入不可恢复态：`activate()` 见 DEACTIVATING 直接抛 ERR_PLUGIN_NOT_DEACTIVATED（L386-391）；`unload()` 守卫同样拒绝 DEACTIVATING（L369-374）；`PluginManagerImpl.unloadPlugin` 调 `plugin.unload()` 抛错 → holder 留在 map；`reloadPlugin` 第 2 步 `oldDef.unload()` 同样被守卫拒绝。任何公开 API 均无法把状态推离 DEACTIVATING。
- **现状**: 中间态（ACTIVATING/DEACTIVATING）没有任何失败落定路径；同时 `scope/container/configProvider` 引用在清理**之前**已被置 null，半停的容器对象在字段侧不可达，只剩异常栈与 ThreadLocal 之类的临时引用。
- **风险**: 一次 destroy-method 抛错即把该插件生命周期永久锁死（重启进程前无法激活/卸载/重载）；泄漏面 = 半停容器中未完成 destroy 的 bean 持有的线程池/连接 + 保持 ACTIVATED 消费者依赖的级联误判（`hasActivatedPluginByName` 读 state）。属"错误路径资源泄漏 + 无恢复语义的半个成功"（F2/F8），P1。
- **建议**: 用 try/finally 将中间态收敛为终态：deactivate 失败落 `FAILED`（或回退 ACTIVATED 语义需先定义），activate 的 rollback 也应以 finally 保证 `state=FAILED` 落定；清理完成后再置空字段（或保留字段直到终态落定，供诊断/强制清理路径使用）；为 DEACTIVATING/ACTIVATING 提供显式 `forceUnload` 恢复入口。
- **失效面枚举**（F3 同族清扫）: ① `VfsPluginDefinition.doDeactivate` L434-458（本条，s.close 异常即使 c.stop 成功也使状态卡死）；② `VfsPluginDefinition.activate→rollbackActivation` L408-422/L507-522（卡 ACTIVATING）；③ `AbstractPlugin.doDeactivate` L256-274（卡 DEACTIVATING，且 `autoInit→CoreInitialization.destroy()` L268-271 在异常时同样被跳过，宿主初始化残留）；④ `AbstractPlugin.activate→rollbackActivation` L194-201/L236-242（卡 ACTIVATING）；⑤ `AbstractPlugin.doStop` L350-358（非 aware 兼容路径：beanContainer.stop 抛错有 finally 保 CoreInitialization.destroy，但 state 语义为 LOADED 常量，不卡状态机，不算同缺陷）；⑥ `PluginManagerImpl.deactivatePlugin` L177-185——future 异常上抛给调用方，是可观测面而非缺陷点。四处中间态卡死同根：中间态无失败落定，统一修复。
- **信心水平**: 确定
- **误报排除**: 非并发误报——即使完全单线程，一次 destroy 抛错即复现；也不是"插件作者 destroy 抛错是作者责任"：框架状态机对"清理失败"这一可预期事件无落定语义，属框架生命周期设计缺陷。
- **复核状态**（维度复核 2026-10-17）: **保留 P1**。四处站点行号逐一比对成立：VfsPluginDefinition.activate catch L408-422（rollbackActivation L411 内 `c.stop()` L519 抛错则 L412 `state=FAILED` 被跳过）、doDeactivate L434-458（L440 置 DEACTIVATING、L443-445 引用先置空、L456 LOADED 不可达）、AbstractPlugin.activate catch L194-201、doDeactivate L256-274（含 autoInit→CoreInitialization.destroy L268-271 异常时跳过）。异常传播链亲核：`BeanScopeImpl.close` 末尾上抛最后一个 destroy 异常（L116-117）→ `BeanContainerImpl.stop` L616 原样上抛 → 中间态无落定；unload 守卫（VfsPluginDefinition L369-374 / AbstractPlugin L154-161）、activate 守卫（L386-391 / L177-181）、reloadPlugin 经 `oldDef.unload()`（L663）三面均拒绝中间态，无恢复 API 成立。触发现实性：单线程一次 destroy-method 抛错即确定性复现；reconcile 路径 `deactivateIfActive` catch 后状态仍永久卡死（PluginManagerImpl L402-413 仅吞异常不复位状态）。现状：新发现未修复。

---

### [nop-core-framework-C6-003] 配置源构造器"先调度后加载"：初始加载失败泄漏永久定时刷新任务（jdbc 源附带连接重建循环）

- **文件**:
  `nop-core-framework/nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java:49-65`；
  `nop-core-framework/nop-config/src/main/java/io/nop/config/source/file/AbstractFileConfigSource.java:42-51`
- **证据片段**（JdbcConfigSource）:
  ```java
  public JdbcConfigSource(JdbcConfig jdbcConfig) {
      this.jdbcConfig = jdbcConfig;
      if (jdbcConfig.getRefreshInterval() != null) {
          long seconds = jdbcConfig.getRefreshInterval().getSeconds();
          this.refreshTaskFuture = GlobalExecutors.globalTimer().scheduleWithFixedDelay(
              this::refreshConfig, seconds, seconds, TimeUnit.SECONDS);   // L54：先调度
      } else { this.refreshTaskFuture = null; }
      try {
          this.vars = loadConfig();                                        // L61：后加载
      } catch (SQLException e) {
          throw NopException.adapt(e);                                     // 构造失败，任务无人可关
      }
  }
  ```
  `AbstractFileConfigSource` L44-50 完全同构（schedule L45 在 `this.vars = loadConfig()` L50 之前）。
- **严重程度**: P1
- **并发场景**: 启动期 ConfigStarter 单线程构造（按判级口径本应条件化降档）；但泄漏对象是 `GlobalExecutors.globalTimer()` 上的常驻周期任务，泄漏窗口覆盖全运行期且持续消耗资源，不适用启动期降档。
- **触发时序**: T1 启动线程：`ConfigStarter.doStart` → `loadJdbcSource`（L373-380）→ `new JdbcConfigSource(config)` → L54 调度成功 → L61 `loadConfig()` 因启动时 DB 不可达/凭证错误/selectAllQuery 语法错抛 SQLException → 构造器抛出 → `loadJdbcSource` 未返回 → `closeableSources.add(jdbcSource)`（ConfigStarter L154）从未执行 → `LifeCycleSupport.start` 失败回调 `doStop`（ConfigStarter L526-551）关闭的是 `closeableSources` 列表，其中没有本源 → 已调度的 refreshTaskFuture 无任何引用可 cancel，此后每 `seconds` 秒在 globalTimer 上执行一次：DB 恢复前反复 `createConnection()` 连接尝试（errorCount 限流日志）；DB 恢复后 `loadConfig` 成功 → `connection` 字段被赋值并永久持有（对象已死，无人调 close）→ 泄漏一条持久连接 + 一个永久周期任务。文件源变体：k8s key-file/props 路径在构造期 `loadConfigFromPath` 抛错（文件半写/解析错）→ 同样泄漏每 refreshInterval 遍历目录的永久任务（其 refreshConfig 全程 try/catch，永不自杀）。
- **现状**: 两处构造器均为"先注册周期任务、后执行可失败初始化"，失败时对象半构造、任务不可达。
- **风险**: 每次启动失败残留一个常驻任务；嵌入场景反复 destroy→initialize 时逐次累积；jdbc 变体额外持有数据库连接并制造周期性连接风暴（DB 故障期间放大故障源）。P1（错误路径泄漏 + 长时运行资源持续消耗）。
- **建议**: 调整顺序——先 `this.vars = loadConfig()` 成功后再调度；或构造器 catch 中在抛出前 `if (refreshTaskFuture != null) refreshTaskFuture.cancel(false)`。最小改动是后者（一处 try/catch）。
- **失效面枚举**（F3 同族清扫）: ① `JdbcConfigSource` 构造器（本条）；② `AbstractFileConfigSource` 构造器（本条）；③ 其具体子类 `KeyFileConfigSource` L23 / `PropsFileConfigSource` L26——均直通 super 构造器，随 ② 一并修复；④ `ConfigStarter.loadJdbcSource/loadKeyFileConfigSource/loadPropsFileConfigSource`——调用方无失败补偿路径（源未入 closeableSources），为泄漏成立的前提面；⑤ 仓库内其他 `scheduleWithFixedDelay` 构造点——grep 范围内 config 模块外无同形态（DynamicTextConfigSourceLoader 不自建调度）。
- **信心水平**: 确定
- **误报排除**: 非误报校准中的"低频启动路径合理设计"——泄漏的是运行期常驻任务而非启动期临时对象；也非"有界声明"豁免（无任何上限/TTL 声明）。
- **复核状态**（维度复核 2026-10-17）: **保留 P1**。「先调度后加载」两构造器逐行核实成立（JdbcConfigSource L54→L61、AbstractFileConfigSource L45→L50，KeyFile L23/PropsFile L26 直通 super）；调用方无失败补偿亲核成立（ConfigStarter L152-154 失败时不入 closeableSources、doStop L547 仅关列表）；jdbc 变体连接滞留成立（构造失败时 L111-113 已赋值的 `connection` 字段无人关闭；泄漏任务在 DB 恢复后重建连接并永久持有，closed 恒 false）。**启动期降档不适用论证复核：成立**——泄漏任务挂于 JVM 静态 `GlobalExecutors.globalTimer()`（非 CoreInitialization 生命周期管辖，本复核亲证 GlobalExecutors 无任何 initializer destroy 路径），泄漏窗口覆盖全运行期，降档规则的前提（后果限于启动期）不满足。一点补注（不改判级）：单体应用启动失败通常以进程退出收场，此时泄漏随 JVM 消亡；现实风险面集中于嵌入宿主存活续跑、初始化失败重试、destroy→initialize 循环三类场景。现状：新发现未修复。

---

### [nop-core-framework-C6-004] ShellRunner 非 timeout 异常/中断路径不销毁已创建子进程，进程与管道读取线程泄漏

- **文件**: `nop-utils/nop-shell/src/main/java/io/nop/shell/ShellRunner.java:73-143, 259-271`
- **证据片段**:
  ```java
  final Process process = this.newProcess(command);            // L83：进程已创建
  final Future<?> timerFuture = scheduleTimeout(command, () -> { safeDestroy(...); ... }); // 仅 timeout>0
  final Future<?> errorFuture = collectError(command, process, stopped, collector);
  try {
      if (command.getInputBytes() != null) {
          try { IoHelper.write(process.getOutputStream(), command.getInputBytes(), null); }
          finally { IoHelper.safeClose(process.getOutputStream()); }   // 写 stdin 失败 → 直接上抛
      }
      readOutput(process, command, collector, stopped);
      ...
  } finally {
      if (timerFuture != null) timerFuture.cancel(false);      // L122-126：finally 只取消任务
      if (errorFuture != null) errorFuture.cancel(false);      // 不 safeDestroy(process)
  }
  ```
- **严重程度**: P1
- **并发场景**: 调用线程 + `GlobalExecutors.cachedThreadPool()` 上的 stderr 读取线程（collectError，redirectErrorStream=false 时存在）；超时清理在 globalTimer 线程执行。
- **触发时序**: 时序 A（stdin 断管）：T1 调用线程 L95 向已提前退出的子进程写大块 inputBytes → IOException → 外层 catch L131 捕获 → `stopped.get()==false` → 包装 `ERR_SHELL_EXEC_COMMAND_FAIL` 上抛 → finally 仅 cancel 两个 future → **process.destroy() 从未被调用**，子进程（若仍存活）与三根管道句柄泄漏；cachedThreadPool 的 stderr 读取线程因 `stopped` 永不为 true 且子进程不退出而永久阻塞在 readLine 上，pin 住一个池线程。时序 B（中断）：调用线程在 `process.waitFor`/readLine 阻塞点被 interrupt → L128 catch → rethrow → 同样不销毁进程。时序 C（读输出期间 collector 抛 RuntimeException）：同路径泄漏。时序 D（waitFor 超时子路径本身也漏杀）：进程关 stdout 但不退出 → readOutput 正常返回 → L112 `waitFor(diff, NANOS)` 超时返回 false → L116 `stopped.set(true)` → finally `timerFuture.cancel(false)`——timer 截止点（进程创建后 +timeout）晚于 beginTime 起算的 diff 截止点，cancel 时 timer 任务通常尚未执行 → `safeDestroy` 永不运行 → 连"超时路径"自身也在该子路径上泄漏进程（最终 L141 抛 TIMEOUT，调用方以为已终止）。唯一确定执行 `safeDestroy` 的是 readOutput 阻塞期间被 timer 打断的主路径（L85-88）。
- **现状**: 进程销毁仅挂在超时路径上；成功路径靠进程自然退出，异常/中断路径完全没有 destroy 兜底。
- **风险**: 每次泄漏一个可能仍存活的 OS 进程 + 管道 fd + 一个被 pin 死的 cachedThreadPool 线程（无界池，泄漏可线性放大）；长驻宿主反复调用外部命令（如代码生成、图像工具链）时进程表与线程数单调增长。错误路径资源泄漏，P1。
- **建议**: 将 `safeDestroy` 纳入 finally 的兜底：成功路径 waitFor 已返回、destroy 幂等无害；失败/中断路径强制销毁；对 timeout 场景在 destroy 后追加 `destroyForcibly()` 升级（当前仅 SIGTERM，忽略 SIGTERM 的子进程会在 stopped=true 下被上报为已超时终止但实际存活——同站点次级面）。
- **失效面枚举**（F3 同族清扫）: ① `run(...)`（本条，唯一进程执行入口）；② `runCommand` L47-70 静态便捷入口——经 run()，随 ① 修复；③ `collectError` L150-170——读取线程退出完全依赖 `stopped` 位或 EOF，是泄漏放大器非独立站点；④ `safeDestroy` L259-271——无 destroyForcibly 升级（本条建议覆盖）；⑤ C4-005 已立案 timeout=0 挂起（scheduleTimeout 对 timeout<=0 返回 null 且 waitFor 无限阻塞）——本条不覆盖该已立案面，属同文件不同路径；⑥ nop-utils 其余 Process 使用点——grep 全扫仅 ShellRunner 一处。
- **信心水平**: 确定
- **误报排除**: 非测试代码简化模型（生产工具类）；也非"进程会自然退出所以无害"——写入 stdin 断管与中断都是常规错误路径，且 stderr 读取线程 pin 是确定性后果。
- **复核状态**（维度复核 2026-10-17）: **保留 P1**。四时序逐行亲核全部成立：A（L95 stdin 写断管 IOException→外层 catch L131 包装上抛，finally L121-127 仅 cancel）、B（中断 rethrow L128-130）、C（collector 抛 RuntimeException 同路径）、D（L112 `waitFor(diff)` false → L116 `stopped.set(true)` → finally cancel timer（timer 截止点晚于 beginTime 起算的 diff 截止点，通常尚未触发）→ L141 抛 TIMEOUT 而 `safeDestroy` 从未执行——「超时路径自身漏杀」属实）。修复约束关系核实：外层 try（L80）无 finally，C6-014 的「兜底必须置于外层 finally」约束成立。stderr 线程 pin 面核实：collectError L153-166 退出仅依赖 `stopped` 位或 EOF，进程不销毁则 `cancel(false)`（不中断）后线程永久阻塞 readLine，pin 住无界 cachedThreadPool 线程。两点补注（不改判级）：① 时序 C 需自定义 IShellOutputCollector 抛异常（内置 Default/Log 收集器不抛）；② 本仓内唯一生产调用点 MavenProject L22 用 redirectErrorStream(true)+无 inputBytes+timeout=0（暴露面为时序 B + C4-005），但 nop-shell 为公开工具库、宿主直接使用为设计内场景。现状：新发现未修复。

---

### [nop-core-framework-C6-005] JdbcConfigSource.refreshConfig 的变更分发在保护块之外：监听器异常永久杀死刷新任务且全程无日志（与文件源保护不对称）

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/source/jdbc/JdbcConfigSource.java:71-108`
- **证据片段**:
  ```java
  private synchronized void refreshConfig() {
      if (closed) return;
      Map<String, ValueWithLocation> vars = null;
      try {
          vars = loadConfig(); ...
      } catch (Exception e) { ...closeConnection/重试/限流日志... }
      if (vars != null)
          tryTriggerChange(vars);          // L96-97：try/catch 之外
  }
  ```
  对照 `AbstractFileConfigSource.refreshConfig` L53-70：load + `tasks.run()` 全部包在 try/catch 内，注释明言"scheduleWithFixedDelay 任务一次未捕获异常会永久取消后续执行"。
- **严重程度**: P2（触达链成立时后果达 P1 级：静默永久停摆）
- **并发场景**: globalTimer 调度线程执行 refreshConfig；`tryTriggerChange` 同步执行任务链 `changeApplier::requestUpdate`（ConfigStarter L486 经 CompositeConfigSource L54-58 转发注册到 jdbc 源）及后续任务。
- **触发时序**: T1 刷新线程检测到配置变化 → `tryTriggerChange` → 任务链中 `changeApplier.requestUpdate()` → `configExecutor.execute(...)`。两种可具体化交叠：(a) 停机窗口——ConfigStarter.doStop L533-535 已 `configExecutor.stop()` 而源在 L547 才关闭，此间刷新触发 → 池已 shutdown → RejectedExecutionException；(b) 运行期——config 执行器（1 线程/队列 100，默认 AbortPolicy）积压满 100 → execute 抛拒绝异常。异常从 tryTriggerChange 传播出 refreshConfig → `scheduleWithFixedDelay` 的任务被 JDK 静默永久取消（无任何日志）→ 该 jdbc 配置源从此不再刷新，`closed` 恒为 false、连接持有，表面一切正常。
- **现状**: 同一模块内两个定时刷新实现一个全保护、一个半保护（F8 清理/保护不对称的直接实例）；且异常最终被调度器吞掉，违反 F9 可观测性。
- **风险**: 长时运行中任意一次配置执行器拒绝（停机窗口必然存在）即永久停掉 jdbc 配置热更新，运维完全无感知；后续所有配置变更依赖失效。
- **建议**: 对齐 `AbstractFileConfigSource`：将 `tryTriggerChange` 纳入 try/catch 并限流记录；更优解是把任务链分发改为异步（经 changeApplier 已有的 executor + shouldUpdate 去重），同步链上仅做快照回写。同时在 `refreshConfig` 外层加兜底 catch，彻底免疫调度器静默取消语义。
- **失效面枚举**（F3 同族清扫）: ① `JdbcConfigSource.refreshConfig`（本条，未保护）；② `AbstractFileConfigSource.refreshConfig` L53-70（已全保护，参照实现）；③ `ChangeSubscriptions.trigger` L78-87（DefaultConfigProvider 侧监听器分发有逐监听器 try/catch，已保护）——即同族三处分发点仅 jdbc 源裸奔；④ `tryTriggerChange` 内任务循环本身（L104-106）也无逐任务 try/catch，前一任务异常会跳过剩余任务（本条修复时应一并覆盖）。
- **信心水平**: 确定（交叠 a 为停机期必然存在的窗口；交叠 b 需队列积压，条件性）
- **误报排除**: 不是"异常最终有人接"——ScheduledThreadPoolExecutor 对任务抛错的语义是静默取消后续执行，无人可观测；非低频豁免——停机窗口交叠在每次带 jdbc 源的优雅停机中都存在。
- **复核状态**（维度复核 2026-10-17）: **保留缺陷、转同站点并入 C1-021（P1）合并处置**。结构性事实亲核成立：`tryTriggerChange`（L96-97）确在 try 之外，任务循环 L104-106 无逐任务隔离，与 AbstractFileConfigSource L53-70 全保护实现不对称。**但本条给出的两条具体触发时序经复核均不成立**：(a) 停机窗口 REE——doStop 实测顺序为 `changeApplier.deactivate()`（L530）**先于** `configExecutor.stop()`（L534），requestUpdate 的 `active` 门（ConfigChangeApplier L41）短路，REE 不可达（即便微观 TOCTOU 撞上，后果也被随即的 L547 close 吞没，C1-round3 同判「近似空转」）；(b) 队满 REE——`shouldUpdate` CAS 去重使单 applier 队列深度 ≤1，默认布线不可达。剩余真实触发面 = 可插拔 IConfigExecutor（SysServiceLoader）+ 公开 `IConfigSource.addOnChange` SPI 回调抛异常——**本单元 C1 维度已就同一站点以该触发面终裁升格 P1 立案 C1-021**（C1-round3 §二/§五，含与本复核一致的触发面修正）。处置：C6-005 登记 cross-ref 归 C1-021 修复载体（trigger 独立 try-catch + 限流日志，per-task 隔离为更优形态），本条 F3④ 已被 C1-021 修复建议覆盖，不重复出修复项；判级随 C1-021。现状：新发现未修复（同站点 C1-021 同状态）。

---

### [nop-core-framework-C6-006] singleton scope 关闭销毁顺序为 ConcurrentHashMap 哈希序，非逆依赖序

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeImpl.java:96-118`
- **证据片段**:
  ```java
  public void close() {
      synchronized (beans) { closed = true; }
      Exception e = null;
      for (Map.Entry<String, ProducedBeanInstance> entry : beans.entrySet()) {  // CHM 哈希序
          String beanName = entry.getKey();
          try { remove(beanName, entry.getValue()); }
          catch (Exception ex) { LOG.error(...); e = ex; }
      }
      ...
  }
  ```
- **严重程度**: P2
- **并发场景**: 停机线程（CoreInitialization.destroy 链或插件 deactivate）；销毁期间其他线程的 bean 访问已被 closed 标志 fail-fast（非本条问题）。
- **触发时序**: 容器按 `orderedBeans` 拓扑序创建 bean：A（如连接池）依赖 B（如 datasource）。停机时 `BeanContainerImpl.stop` → `singletonScope.close()` → 遍历 `beans`（ConcurrentHashMap）——若哈希序使 B 先被 destroy（B 的 destroy-method 关闭底层连接）→ 随后 A.destroy 调用其依赖（刷盘/等待在飞任务）→ 抛错 → A 的 destroy 半途中断 → A 持有资源未完全释放，仅记日志。同一容器每次停机的销毁顺序还随哈希布局变化，问题呈间歇性。
- **现状**: 创建有拓扑排序（`BeanTopologySorter`），销毁完全无序——开与关不对称。
- **风险**: 停机路径上依赖后置的 bean 销毁失败概率随 bean 数量上升；销毁失败意味着其 destroy-method 中后半段资源释放被跳过（关联 C6-007 的异常传播面）；插件子容器热 deactivate 场景放大为反复出现的销毁错误。
- **建议**: 在 `BeanScopeImpl` 记录 bean 注册序（`add` 时附加 topoIndex，`ProducedBeanInstance` 或容器 `BeanDefinition.getBeanTopoIndex()` 已有该信息），`close()` 按注册序逆序销毁；或由容器把 `orderedBeans` 的逆序传给 scope 排序后销毁。
- **失效面枚举**（F3 同族清扫）: ① `BeanScopeImpl.close`（singleton，本条）；② `BeanScopeContextImpl.onContainerStop` L87-94（自定义 scope 集合间也任意序关闭，且容器间无依赖序——同族，修复时可一并定序）；③ `BeanContainerImpl.stop` L605-613（singletonScope 先于自定义 scope 关闭，顺序固定但语义上未与依赖方向对齐——低风险观察项）。
- **信心水平**: 确定（顺序无序是事实；具体损害取决于应用 bean 的 destroy 依赖关系，框架层无法保证不发生）
- **误报排除**: 不是"低频停机路径粗粒度行为"豁免——本条是确定性设计缺口（Spring 等同位实现均按逆注册序销毁），且插件热卸载使其进入可反复触发的路径。
- **复核状态**（维度复核 2026-10-17）: **保留缺陷、转同站点并入 C4-010（P2）合并处置**。代码事实亲核一致：`beans` 为 ConcurrentHashMap（BeanScopeImpl L27），close 按 entrySet() 哈希序销毁；创建序有拓扑契约（BeanContainerImpl L98 `BeanTopologySorter.INSTANCE.sort`）而销毁无序，开与关不对称成立。**本单元 C4 维度第 2 轮已就同一根位点独立立案 C4-010**（C4-round2 L179-199，含更完整触发时序、`beanTopoIndex` 降序修复建议与 F3 全清单）。处置：C6-006 登记 cross-ref 归 C4-010 修复载体（按 topoIndex 逆序销毁），本条 F3②（BeanScopeContextImpl.onContainerStop 集合间任意序）作为次级面随同修复覆盖；判级随 C4-010（P2）。现状：新发现未修复（同站点 C4-010 同状态）。

---

### [nop-core-framework-C6-007] destroy 链异常传播缺陷：destroyMethod 抛错跳过同 bean 的 ioc:destroy 清理；多 bean 失败仅保留最后一个异常

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:704-718`；`BeanScopeImpl.java:103-117`；`BeanScopeContextImpl.java:85-97`；`BeanContainerImpl.java:604-616`
- **证据片段**:
  ```java
  void destroyBean(Object bean, IBeanScope beanScope, IBeanContainerImplementor container) {
      if (bean != null) {
          ...
          if (subscriptionCleanup != null) { subscriptionCleanup.cancel(); subscriptionCleanup = null; }
          if (destroyMethod != null)
              destroyMethod.call0(bean, DisabledEvalScope.INSTANCE);   // 抛错 → 下面一行不执行
          runXpl(beanModel.getIocDestroy(), bean, container, beanScope); // ioc:destroy 清理被跳过
      }
  }
  ```
  `BeanScopeImpl.close`：`catch (Exception ex) { LOG.error(...); e = ex; }` —— `e` 被后续失败覆盖，仅最后一个异常上抛。
- **严重程度**: P2
- **并发场景**: 停机线程串行销毁；无竞争，纯异常传播路径问题（F2 半个成功 / F9 家族）。
- **触发时序**: T1 停机销毁 bean X：X 声明了 destroy-method 且该 method 抛出（如关闭超时）→ `runXpl(iocDestroy)` 被跳过——而 `ioc:destroy` XPL 往往承载平台侧声明式清理（解绑/删临时文件/通知）→ X 完成的是"半个销毁"。容器层面：scope 内 N 个 bean 销毁失败时，`BeanScopeImpl.close` 只把最后一个异常经 `NopException.adapt(e)` 上抛，前面 N-1 个仅存日志；`BeanContainerImpl.stop` 中 singletonScope 异常存入 `ex` 后，`onContainerStop`（L613）不在 try 内，其异常直接传播并**遮蔽** `ex`。
- **现状**: 单 bean 粒度：destroy 步骤非容错；多 bean/多 scope 粒度：无异常聚合，只有"最后一败"对外可见。
- **风险**: 声明式清理被静默跳过导致资源滞留（线程池、临时文件、注册项）；排障时只见最后一个异常，误判销毁失败范围；插件 deactivate 场景叠加 C6-002 后果放大。
- **建议**: `destroyBean` 内对 `destroyMethod.call0` try/catch（记录后继续执行 `runXpl(iocDestroy)`），两步异常聚合为 NopException.suppressed；`BeanScopeImpl.close`/`onContainerStop`/`BeanContainerImpl.stop` 改为异常列表聚合（addSuppressed）后统一上抛。
- **失效面枚举**（F3 同族清扫）: ① `BeanDefinition.destroyBean`（destroyMethod→iocDestroy 顺序，本条）；② 同方法 `subscriptionCleanup.cancel()` L710-712 在 destroyMethod 之前——cancel 内部回调经 `Cancellable.tryRunCallbacks` 已逐个 try/catch（nop-kernel Cancellable L115-123），不受本缺陷影响；③ `BeanScopeImpl.close`（末异常覆盖）；④ `BeanScopeContextImpl.onContainerStop`（末异常覆盖）；⑤ `BeanContainerImpl.stop`（onContainerStop 异常遮蔽 singletonScope 异常）；⑥ `LifeCycleSupport.stop`（nop-kernel，doStop 异常仅记录吞掉——已在通配 catch 层，不算同缺陷但属同一异常聚合缺位的背景）。
- **信心水平**: 确定
- **误报排除**: 非通用错误处理维度越界——本条主张的是"清理步骤被跳过导致资源生命周期不完整"，落在 C6 定义（关闭链顺序与异常传播）内。
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。三面代码事实逐一亲核成立：① destroyBean L704-718（destroyMethod L714 抛错 → L715 `runXpl(iocDestroy)` 跳过，顺序缺陷对全部异常形态成立——第 2 轮 §4.1 反射异常分类复核无矛盾：RuntimeException 原样/Error 穿透/受检异常解包包装）；② BeanScopeImpl.close L106-110 `e = ex` 末异常覆盖；③ BeanContainerImpl.stop 的 `onContainerStop`（L613）在 singletonScope 异常捕获 try 之外，异常遮蔽成立。F3 ②（subscriptionCleanup.cancel 先行且经 tryRunCallbacks 逐个保护）维持不适用判定。第 2 轮 §4.2 补充的 Error 穿透面（close L108 只 catch Exception）归入本条聚合修复口径，判定合理。维持 P2；修复建议（两步异常聚合 addSuppressed + 三层异常列表聚合）成立。现状：新发现未修复。

---

### [nop-core-framework-C6-008] 停机期间容器内 bean 解析双重失效：stop() 先灭 started/running，IocCoreInitializer.destroy 先切 BeanContainer 指针

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:594-622`；`nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/initialize/IocCoreInitializer.java:54-62`
- **证据片段**:
  ```java
  public void stop() {
      LOG.info(...);
      running = false; started = false;          // 先灭访问位
      ...
      if (singletonScope != null) {
          try { singletonScope.close(); } ...     // 销毁期间 destroy-method 在跑
      }
      BeanScopeContext.instance().onContainerStop(this);
  }
  void checkStarted() { if (!started && !running) throw new NopException(ERR_IOC_CONTAINER_NOT_STARTED)...; }

  // IocCoreInitializer.destroy
  BeanContainer.registerInstance(parentContainer);   // 先切全局指针
  container.stop();                                  // 再销毁
  ```
- **严重程度**: P2
- **并发场景**: 单停机线程内（非竞态）；受影响的是 destroy-method / `ioc:destroy` XPL / onContainerStop 过程中发生的任何容器查找。
- **触发时序**: T1 停机：`IocCoreInitializer.destroy` L59 先把 `BeanContainer` 全局指针切到 parent → L60 `container.stop()` → singletonScope.close() 逐个调 destroy-method。此时 bean X 的 destroy 逻辑（或 ioc:destroy XPL）通过 `BeanContainer.instance().getBean("dep")` 解析兄弟 bean：(a) 指针已指向 parent——查到的是错误容器（parent 为空静态容器时报 unknown-bean；嵌套容器场景下可能在 parent 中**新建** singleton，该实例无任何销毁归宿，容器停了它还在）；(b) 即便绕过指针直接引用本容器——`running/started` 已在 L597-598 置 false，`checkStarted` 直接抛 ERR_IOC_CONTAINER_NOT_STARTED。
- **现状**: 停机顺序使得"销毁过程中解析协作 bean"这一合理需求在两条路径上都被结构性禁止，destroy-method 只能依赖注入引用工作。
- **风险**: 所有依赖运行期容器查找的 destroy 清理（可选依赖、按名查找的解绑、XPL 中 @bean 引用）在停机时必然失败或落到错误容器；嵌套/插件容器场景泄漏"销毁期新建的 parent singleton"。
- **建议**: IocCoreInitializer.destroy 调整顺序为先 `container.stop()` 再 `registerInstance(parent)`（stop 期间保留指针但用关闭中的 scope fail-fast 拦截新建）；`checkStarted` 对"容器正在停机"态放行只读查找或提供 `tryGetBeanForDestroy` 通道；XPL ioc:destroy 执行时临时恢复容器绑定。
- **失效面枚举**（F3 同族清扫）: ① `BeanContainerImpl.stop` 访问位先灭（本条面 1）；② `IocCoreInitializer.destroy` 指针先切（本条面 2）；③ `AbstractPlugin.doDeactivate`/`VfsPluginDefinition.doDeactivate`——插件子容器 stop 期间指针未切（插件容器不 registerInstance），仅面 1 适用；④ `BeanContainerImpl.destroyBean` L624-631——外部单 bean 销毁入口不检查 started，不受面 1 影响（但受 CL-006 二次销毁面，非本轮立案）；⑤ `restart()` L452-455——stop→start 间窗口同面 1，属同一修复面。
- **信心水平**: 很可能（顺序事实确定；"destroy-method 运行期容器查找"在真实应用中的普及度未逐仓统计，后果链每一步均经代码验证）
- **误报排除**: 不是 C1/C2 维度越界——无竞态主张，是关闭链顺序设计问题；也不是"fail-fast 是合理设计"——被禁止的是容器自身发起的销毁期合法查找。
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。双重失效代码事实逐一亲核成立：面 1——`stop()` L597-598 先置 `running/started=false`，`checkStarted` L619-622 对销毁期查找抛 `ERR_IOC_CONTAINER_NOT_STARTED`；面 2——`IocCoreInitializer.destroy` L57-61 先 `BeanContainer.registerInstance(parentContainer)` 再 `container.stop()`，销毁期经全局指针的解析落向 parent 容器。嵌套容器场景「parent 中新建 singleton 无销毁归宿」的推演链成立。F3 ③（插件子容器 stop 期间指针未切，仅面 1 适用）与 ⑤（restart 窗口同面）核实正确。信心「很可能」合理（destroy-method 内容器查找的真实普及度未统计，但后果链每步代码可证）。维持 P2；修复建议顺序（先 stop 后切指针 + 只读通道）可行。现状：新发现未修复。

---

### [nop-core-framework-C6-009] onContainerStop 关闭 scope 但不摘除注册条目：容器重启后自定义 scope 永久 ALREADY_CLOSED，且上下文属性滞留已死容器强引用

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeContextImpl.java:83-98`；`BeanContainerImpl.java:613`
- **证据片段**:
  ```java
  public void onContainerStop(IBeanContainerImplementor container) {
      BeanContainerScope containerScope = getContainerScope(container);
      RuntimeException ex = null;
      if (containerScope != null) {
          for (IBeanScope scope : containerScope.scopes.values()) {
              try { scope.close(); }            // 只关闭
              catch (RuntimeException e2) { ...; ex = e2; }
          }
      }
      if (ex != null) throw ex;                 // 不 scopes.remove(...)，不清 containerScopes
  }
  ```
- **严重程度**: P2
- **并发场景**: 停机/重启线程 vs 绑定在该 IContext 上的请求处理线程（getScope 读取）；CL-007 已登记此处弱一致迭代的面，本条审注册/注销配对面。
- **触发时序**: T1 请求上下文 C 内通过 `BeanScopeContext.runWithScope` 之外的方式绑定了自定义 scope（或更常见：容器支持 restart）：`container.stop()` → onContainerStop 把 C 的 containerScope 中每个 scope close（closed=true）但不 remove → 容器 `restart()`（BeanContainerImpl.restart = stop+start，公开 API）重建 singletonScope，但 C 的 containerScope 里仍是已关闭的旧 scope 实例 → C 内后续对非 singleton/prototype bean 的访问 `getScope(container, name)` 命中旧实例 → `checkClosed` 抛 ERR_IOC_BEAN_SCOPE_ALREADY_CLOSED，直到 IContext 本身销毁。同时 `containerScopes`（按容器对象为 key 的强引用 map，存于 IContext 属性）在容器死亡后仍持有容器与 scope，滞留至上下文消亡。
- **现状**: 关闭语义完整（配对 C6-007），但"关闭"没有联动"注销"——与 `LifeCycleSupport`/`runWithScope` 的 unbind+close 成对模式不对称（F8）。
- **风险**: restart/热重建场景下自定义 scope 的 bean 访问永久报错（同一上下文内不可自愈）；长生命周期上下文（如会话级/根级 context）中滞留死容器图。
- **建议**: `onContainerStop` 中 close 成功后 `containerScope.scopes.remove(scopeName, scope)`；全部关闭后 `containerScopes.remove(container)`；无条目时清掉 IContext 属性。
- **失效面枚举**（F3 同族清扫）: ① `onContainerStop`（本条）；② `bind`/`unbind`——runWithScope 路径成对，无缺陷；③ `BeanScopeContextImpl.makeContainerScope` L41-48——向 IContext 属性写入的 map 本身无失效清理钩子（随本条修复一并覆盖）；④ `BeanContainerImpl.stop` 对 `singletonScope` 字段不置 null——restart 时 L484 直接重建新实例覆盖，无残留，不适用。
- **信心水平**: 很可能（代码事实确定；restart + 自定义 scope 的组合触发面依赖宿主用法，框架内无该用法的直接调用者）
- **误报排除**: 非已立案 CL-006（destroyBean 不摘 scope 条目→二次 destroy）与 CL-007（弱一致迭代）——本条是"关闭后注册条目滞留"的配对面，三者同文件不同缺陷形态。
- **复核状态**（维度复核 2026-10-17）: **保留 P2**。代码事实亲核成立：`onContainerStop`（BeanScopeContextImpl L83-98）close 后无 `scopes.remove`、无 `containerScopes.remove`；`containerScopes` 以容器对象为强 key 存于 IContext 属性（L35-48），死容器滞留至上下文消亡。**触发面精度补注（不改判级）**：`bind` 全仓唯一调用方为 `runWithScope`（BeanScopeContext.api L25-27，finally unbind）——正常路径条目在 run 结束即摘除，容器 restart 本身不产生跨 run 残留条目；ALREADY_CLOSED 永久面需「bind 不 unbind 的宿主用法」或「restart 与在途 runWithScope 并发」二者之一，比条目「或更常见：容器支持 restart」的表述更窄。强引用滞留面（containerScopes 键不摘）为无条件成立面。原信心「很可能」维持；修复建议（close 后摘条目 + 全关后清 containerScopes）可行且低风险。与 CL-006/CL-007 同文件不同缺陷形态的切割成立。现状：新发现未修复。

---

### [nop-core-framework-C6-010] VirtualThreadTaskExecutor 生命周期三面残留：初始化器无 destroy、注销缺失、旧 executor 关闭无等待（C6-CL-004 深化）

- **文件**: `nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/VirtualThreadTaskExecutor.java:16-67`；`nop-utils/nop-commons-java21/src/main/java/io/nop/commons/jdk21/Jdk21CommonsCoreInitializer.java:7-14`；注册契约见 `nop-kernel/nop-commons/.../GlobalExecutors.java:102-109`
- **证据片段**:
  ```java
  public class VirtualThreadTaskExecutor implements IThreadPoolExecutor {
      private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
      public static void registerGlobalWorker() {
          GlobalExecutors.register(new VirtualThreadTaskExecutor());   // 注册即覆盖旧实例
      }
      @Override
      public void destroy() { executor.shutdown(); }                   // 无 awaitTermination
  }
  public class Jdk21CommonsCoreInitializer implements ICoreInitializer {
      @Override public void initialize() {
          if (CFG_ENABLE_VIRTUAL_THREAD_POOL.get())
              VirtualThreadTaskExecutor.registerGlobalWorker();        // 只注册，无 destroy 覆写
      }                                                                // ICoreInitializer.destroy 默认 no-op
  }
  ```
- **严重程度**: P2
- **并发场景**: CoreInitialization.destroy/reinitialize 与经 `GlobalExecutors.requireExecutor("nop-virtual-thread")` 提交任务的任意业务线程（graphql/异步链等按名取池的调用方）。
- **触发时序**: 面 1（平台销毁不关池）：`CFG_ENABLE_VIRTUAL_THREAD_POOL=true` → initialize 注册 → CoreInitialization.destroy() → Jdk21CommonsCoreInitializer 无 destroy 覆写（默认 no-op）→ executor 既不 shutdown 也**不从 `g_executors` 注销** → 销毁后任何按名取池的提交仍然成功并在虚拟线程上执行，平台"已销毁"语义失效。面 2（重初始化孤儿任务）：destroy→initialize 二轮 → `register()` 用新实例覆盖并 `old.destroy()`——旧 executor 上在飞任务无等待、无桥接，跨生命周期继续运行，访问已销毁的旧容器/旧配置（僵尸任务完成态无人消费）。面 3（即 C6-CL-004 已登记面）：`destroy()` 仅 `shutdown()`，不 `awaitTermination`，调用方无从得知在飞任务何时结束。
- **现状**: 三个面同根——executor 的生命周期与 CoreInitialization 完全脱钩：注册有入口、注销/关闭无归属。
- **风险**: destroy→reinit 循环（测试宿主、嵌入场景热重载）中前一轮任务污染后一轮状态；平台销毁后的幽灵执行面使"全部资源已释放"的关闭承诺不可验证。
- **建议**: Jdk21CommonsCoreInitializer 覆写 `destroy()`：`((IThreadPoolExecutor)GlobalExecutors.unregister(GlobalExecutors.NOP_VIRTUAL_THREAD))` 判空后 destroy + 限时 `awaitTermination`（复用 ExecutorHelper 的关闭工具）；`GlobalExecutors.register` 覆盖旧实例时同样先注销再限时关闭。
- **失效面枚举**（F3 同族清扫——"destroy 无 awaitTermination/关闭责任缺失"族）: ① `VirtualThreadTaskExecutor.destroy`（C6-CL-004 已登记 + 本条面 3）；② `Jdk21CommonsCoreInitializer`（无 destroy，本条面 1/2 新立案）；③ `DefaultThreadPoolExecutor.destroy`（nop-kernel，仅 shutdown 无等待——同族兄弟，非本单元，移交 nop-kernel 单元对齐）；④ `SingleThreadConfigExecutor.stop`（nop-config，经 ③ 关闭 config 执行器，停机窗口下在飞 applyChange 可越过 destroy 存活——本单元使用面，随 ③ 对齐修复时覆盖）；⑤ `GlobalExecutors.register` 的 `old.destroy()`（覆盖式关闭无等待，本条面 2 的机制面）。
- **信心水平**: 确定（三个面的代码事实均直接可证）
- **误报排除**: 非已立案 C6-CL-004 的重复——该条登记的是 destroy 方法无 awaitTermination 本身；本条深化立案"初始化器无 destroy + 注册不注销 + 重初始化孤儿任务"两个未登记面，并给出同族完整清单。
- **复核状态**（维度复核 2026-10-17）: **保留 P2，标注同面多重登记待合并裁定**。三面代码事实全部亲核成立：destroy 仅 shutdown 无 await（L60-62）、Jdk21CommonsCoreInitializer 无 destroy 覆写且 `ICoreInitializer.destroy` 为 default no-op（接口 L24 亲读）、`GlobalExecutors.register` 覆盖时 `old.destroy()`（GlobalExecutors L102-109）。**跨维度/跨单元重复登记核对（本复核新查明）**：① 同单元 C4 维度已立案 C4-004（C4-thread-pools，含「初始化器 destroy 不对称」同面）；② WI 2.1 nop-kernel 审计已登记 GlobalExecutors 机制面（kernel C6-002 register 覆盖即 destroy/unregister 无 destroy、kernel C6-003 全局执行器无平台级 destroy 生命周期）及其 C6-round2 明确将「Jdk21CommonsCoreInitializer 未覆写 destroy 僵尸窗口」归入 kernel C6-003 F3 扩展——即本条三面在设施本体单元均已登记。处置建议：缺陷成立维持 P2，合并裁定时本条转 cross-ref 归 WI 2.1 C6-002/C6-003（设施本体）+ 本单元 C4-004（使用侧载体），C6-010 独立增量为 F3 全族清单（⑥ GlobalExecutors.register 覆盖式关闭无等待面）。现状：新发现未修复（同面 C4-004 / WI 2.1 条目同状态）。

---

### [nop-core-framework-C6-011] IPluginManager 无聚合关闭路径：插件子容器/配置订阅/类加载器无宿主可用的 bulk destroy 入口

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/IPluginManager.java:20-85`；`PluginManagerImpl.java:67-91, 548-565`
- **证据片段**:
  ```java
  public class PluginManagerImpl implements IPluginManager, IPluginContext {
      private final Map<String, PluginHolder> plugins = new ConcurrentHashMap<>();
      private final Map<String, Set<String>> namesToPluginIds = new ConcurrentHashMap<>();
      private final Map<String, Runnable> configSubscriptions = new ConcurrentHashMap<>();
      // 全类无 stop/destroy/close；IPluginManager 接口亦无任何生命周期终止方法
  ```
- **严重程度**: P2（条件性：宿主将 PluginManagerImpl 注册为 singleton bean 时成立；当前仓库内尚无 beans.xml 装配点，故不升 P1）
- **并发场景**: 宿主停机线程（app 容器 stop 的 destroy-method 阶段）；插件子容器内自建线程池/定时任务与 config 执行器并发。
- **触发时序**: 宿主按框架惯例把 PluginManagerImpl 注册为 singleton（无任何 destroy-method 声明可用，接口也无关闭方法）→ 宿主容器 stop → 该 bean 无 destroy 被调用 → 全部插件保持 ACTIVATED：子容器不 stop（其中 bean 的线程池/连接不释放）、`configSubscriptions` 挂在全局配置 provider 上不退订、jar 轨 PluginClassLoader 不关闭 → 平台 destroy→reinit 循环时旧插件容器整体残留，与新循环中的同名插件并存。
- **现状**: 单插件粒度的 unload/deactivate 资源释放是完整的（unloadPlugin 成功路径五步链），但聚合粒度零覆盖；`checkChangedAndReload` 的 javadoc 明言"宿主应用可定时调用"，说明宿主集成是预期用法，却未提供对等的"宿主停机调用"入口。
- **风险**: 优雅停机退化为"JVM 退出杀一切"；嵌入宿主重启平台时插件资源全量泄漏一轮。
- **建议**: 在 IPluginManager 增加 `destroy()`（逆序 deactivate+unload 全部插件、退订 configSubscriptions、关闭残留 classloader），并在 PluginManagerImpl 的 beans 声明处标注 destroy-method；同时考虑注册到 CoreInitialization cleanup 链。
- **失效面枚举**（F3 同族清扫）: ① `PluginManagerImpl`（本条）；② `plugins`/`namesToPluginIds` 两个注册表——unload 路径成对清理（L158-161），单插件面成立；③ `configSubscriptions`——unload/reload 成败路径均退订，单插件面成立，仅 bulk 面缺失；④ `CoeffectEvaluatorImpl`/`unresolvedPluginIds`——纯内存附属，随实例回收，不适用；⑤ `AbstractPlugin.autoInit→CoreInitialization.destroy()`——插件自动初始化宿主平台的反向销毁面（插件 deactivate 拆掉全局初始化），与本条方向相反，登记为设计观察项不立案。
- **信心水平**: 确定（接口缺失是事实；后果成立依赖宿主装配，已作条件化标注）
- **误报排除**: 非"模块未被使用所以无风险"——该类是插件机制唯一指定入口（javadoc 自述统一编排职责），框架层 API 缺口按 C6"线程池关闭责任"口径立案。
- **复核状态**（维度复核 2026-10-17）: **保留 P2（条件性）**。接口缺口亲核成立：IPluginManager 全方法枚举（load/unload/activate/deactivate/get/getLoaded/reconcile/reload + 两个坐标重载）无任何生命周期终止方法；PluginManagerImpl 全文无 stop/destroy/close；仓内 beans.xml 装配点 grep 零命中——条件化标注（宿主注册为 singleton bean 时成立）正确。unload 成功路径五步链与 configSubscriptions 成败两径退订的单插件面成立，bulk 面缺失为真缺口。维持 P2（条件性，不升 P1 口径正确：装配点缺失）。修复建议（接口 destroy + 逆序 deactivate/unload + 退订）可行。现状：新发现未修复。

---

### [nop-core-framework-C6-012] GitRepositoryImpl 每次获取新建 JGit Repository，close 责任完全外置且无配对保障

- **文件**: `nop-utils/nop-git/src/main/java/io/nop/git/impl/GitRepositoryManagerImpl.java:43-50`；`GitRepositoryImpl.java:77-84, 391-394`
- **证据片段**:
  ```java
  @Override
  public IGitRepository getGitRepository(String path) {
      ...
      return new GitRepositoryImpl(config.getRemotePath(), localDir, credentialsProvider);
      // 每次调用 new 一个；构造器内 newRepository() 打开 JGit Repository（.git 文件句柄）
  }
  public void close() { repository.close(); }   // 释放完全依赖调用方自觉
  ```
- **严重程度**: P2（泄漏窗口依赖调用方行为，本轮无法在框架内给出具体触发调用链——理论风险标注）
- **并发场景**: 任意业务线程并发获取仓库对象；无框架侧共享/追踪。
- **触发时序**: T1 调用方 `manager.getGitRepository("x")` → 构造器 `new FileRepositoryBuilder().setGitDir(...).build()` 打开仓库（持有 `.git` 下文件快照与句柄）→ 调用方完成若干操作后**不调 close**（接口虽 extends AutoCloseable，但 manager 不缓存、不追踪、try-with-resources 之外的任何遗漏无补偿路径）→ Repository 对象等待 GC；文件句柄释放延迟至 GC finalization（JGit 部分句柄经由 WindowCache 有界，但 refs/config 的即时句柄依赖对象回收）→ Windows 上阻塞同目录后续写操作。
- **现状**: 仓库对象工厂方法化 + 释放责任完全外置，是典型的"注册无注销保障"不对称；仓库内无任何生产调用方（仅 manager 自身），无法实证具体泄漏链，按口径降档并标注。
- **风险**: 下游模块接入后，长驻服务反复获取仓库 → 句柄/GC 滞留压力；Windows 文件锁造成 git 操作间歇性失败。
- **建议**: manager 维护 `Map<path, GitRepositoryImpl>` 引用计数缓存（同一 rootDir 复用），或返回时登记到弱引用追踪集并在 manager destroy 时统一 close；最低限度在 IGitRepository javadoc 声明显式 close 契约。
- **失效面枚举**（F3 同族清扫）: ① `getGitRepository`（唯一分发点，本条）；② `GitRepositoryImpl.cloneRepository` L128-132——clone 出的 Git 对象已有 safeCloseObject，成立；③ `push/pull/add/commit/hasUncommittedChanges/applyDiff` 中 `new Git(repository)` 未 close——JGit 语义下非 owner 包装不持有独立资源（代码注释 L129-130 亦确认该细节），不立案；④ `existsRemoteRepository`——LsRemoteCommand 无持久句柄，不适用。
- **信心水平**: 很可能（接口契约缺陷确定；实际泄漏链取决于下游使用）
- **误报排除**: 不按 P1 报是因为写不出框架内的具体触发时序——已按口径标注"理论风险"并降 P2；非误报校准中的有界缓存豁免（这里根本没有缓存与上限）。
- **复核状态**（维度复核 2026-10-17）: **保留 P2（理论风险标注）**。代码事实亲核成立：`getGitRepository` 每调用 new 一个 GitRepositoryImpl（构造器内 `newRepository()` 打开 JGit Repository，GitRepositoryImpl L77-84），close 完全依赖调用方（L391-394），manager 不缓存不追踪；仓内 IGitRepositoryManager 生产调用方 grep 零命中——「理论风险」标注诚实。P2 维持口径：nop-git 为已发布公共工具模块（宿主接入为设计内场景），「工厂方法 + 释放责任外置」的 API 契约缺口按 campaign 对「零调用方休眠但公开契约矛盾」维持 P2 的先例（WI 2.1 reinitialize 系裁定）不降 P3；若合并裁定按更严口径统一降档，可随批量裁量 P3（本复核不主动降级）。F3 ②③④（clone safeClose / new Git 非 owner / LsRemote 无持久句柄）维持成立。现状：新发现未修复。

---

### [nop-core-framework-C6-013] ImageCompressHelper 异常路径 ImageWriter 不 dispose、流不关闭

- **文件**: `nop-utils/nop-image/src/main/java/io/nop/image/utils/ImageCompressHelper.java:149-175`
- **证据片段**:
  ```java
  ImageWriter writer = writers.next();
  ImageWriteParam param = writer.getDefaultWriteParam();
  ...
  MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(baos);
  writer.setOutput(output);
  writer.write(null, new IIOImage(image, null, null), param);   // 抛错 → 以下两行跳过
  writer.dispose();
  output.close();
  ```
- **严重程度**: P3
- **并发场景**: 请求线程图片压缩；`compressImageWithLimit` 的降质循环（L106-120）每轮调用本方法，失败重试放大获取次数。
- **触发时序**: T1 编码线程 `writer.write(...)` 抛 IOException（编码器对非法像素/内存不足的失败）→ `writer.dispose()` 与 `output.close()` 均不执行 → ImageWriter 实例（持编码器内部状态与监听器引用）与流包装等待 GC。仅失败路径，成功路径成对释放。
- **现状**: 缺 try-with-resources/finally；ImageWriter 无 OS 句柄，泄漏量级小但违背清理对称（F8）。
- **风险**: 反复失败的压缩请求（损坏图片）下 ImageWriter 实例滞留至 GC；无功能性后果，属卫生问题。
- **建议**: 改为 try-with-resources（MemoryCacheImageOutputStream 是 Closeable）+ finally 中 `writer.dispose()`。
- **失效面枚举**（F3 同族清扫）: ① `bufferedImageToBytes` jpg 分支（本条）；② 同方法 else 分支走 `ImageIO.write`（内部自管理，不适用）；③ `readImage` L31-36（finally safeCloseObject，成立）；④ `resizeImage`（`g2d.dispose()` 在正常路径，若 drawImage 抛错 g2d 不 dispose——同族次级面，随本条一并 try/finally）。
- **信心水平**: 确定
- **误报排除**: 非"GC 兜底所以无风险"的豁免场景申报——按 P3 卫生问题立案，明确不夸大后果。
- **复核状态**（维度复核 2026-10-17）: **保留 P3**。亲核成立：jpg 分支 `writer.write` 抛 IOException 时 `writer.dispose()` 与 `output.close()` 均跳过（无 try/finally），成功路径成对释放；`compressImageWithLimit` 降质循环逐轮新建 ImageWriter 放大获取次数；F3 ④（resizeImage `g2d.dispose()` 无 try/finally，drawImage 抛错不 dispose）同族次级面成立。ImageWriter 无 OS 句柄、GC 可回收，P3 卫生定级恰当。维持 P3。现状：新发现未修复。

---

## 维度集外线索

### [nop-core-framework-CL-010]（维度集外线索，不立案展开）插件子容器不使用 PluginClassLoader，jar 轨热卸载的类隔离面失效

`AbstractPlugin.buildBeanContainer`（nop-plugin-support/AbstractPlugin.java:214-234）以 `new AppBeanContainerLoader()`（默认 `ClassHelper.getSafeClassLoader()`，即宿主 classloader）装配插件子容器，而非传入该插件的 `PluginClassLoader`。后果：插件 beans.xml 中定义/引用的插件自有类全部由宿主 classloader 解析——要么 ClassNotFound（插件类不可见），要么（若 jar 同时在宿主 classpath）由宿主永久持有类。`unloadPlugin` 关闭 PluginClassLoader 释放的只是 activator 实例侧的类，容器 bean 类不可卸载，热卸载隔离承诺失效。属功能/设计缺陷（非 C6 计量面——不产生句柄泄漏），移交插件设计线核查。
- **复核处置**（维度复核 2026-10-17）: **确认**。亲核成立：AppBeanContainerLoader 无参构造确走 `ClassHelper.getSafeClassLoader()`（AppBeanContainerLoader L63），AbstractPlugin L225 确以无参构造装配子容器；jar 轨类隔离失效的因果链成立。「功能面移交」注明恰当——不产生句柄泄漏，非 C6 计量面，维持移交插件设计线。

### [nop-core-framework-CL-011]（维度集外线索，不立案展开）DefaultShellOutputCollector 无界 StringBuilder 全量收集子进程输出

`nop-shell/DefaultShellOutputCollector.java`：output/error 均为无界 StringBuilder，`runCommand` 全量吸收子进程 stdout/stderr。产生超大量输出的命令（如递归 cat、日志导出）可 OOM。C5 内存有界性形态，移交 C5 维度裁量。
- **复核处置**（维度复核 2026-10-17）: **确认**。亲核成立：output/error 无界 StringBuilder（L12-14 附近字段 + L25-33 append 无上限），无任何容量护栏。移交 C5 成立。

### [nop-core-framework-CL-012]（维度集外线索，不立案展开）ChangeSubscriptions 空订阅条目永不摘除

`nop-config/impl/ChangeSubscriptions.java:44-56`：cleanup 仅 `removeListener`，`ChangeSubscription` 条目在监听器清零后永久留在 `simpleSubscriptions`/`patternSubscriptions`（条目还强持有 pattern）。增长上界 = 生命周期内出现过的不同配置 var/pattern 基数，框架用法下有界（静态配置名），但插件/动态 pattern 场景为慢性增长。C7 长时运行形态，移交裁量。
- **复核处置**（维度复核 2026-10-17）: **确认**。亲核成立：subscribeSimple/subscribePattern 的 cleanup lambda 仅 `sub.removeListener(listener)`（L47/L55），条目（含 pattern 字符串）在监听器清零后不摘除。同族对照：ChangeSubscriptions.trigger L78-87 有逐监听器 try/catch（正面）。增长上界标注准确，移交 C7 成立。

### [nop-core-framework-CL-013]（维度集外线索，不立案展开）CoreInitialization.registerCleanup 全仓无生产调用点，destroy 早退路径会丢弃已注册 cleanup

`nop-kernel/nop-core/initialize/CoreInitialization.java:150-156, 158-163`：`registerCleanup` 在全仓库（grep 全 scope）无任何生产调用点；且 `destroy()` 在 `initializers == null`（从未 initialize）时直接 return，若未来有人注册 cleanup 将被静默丢弃。nop-kernel 属其他审计单元，登记为 F8"配对设施未接线"线索。
- **复核处置**（维度复核 2026-10-17）: **确认移交，且经 WI 2.1 台账核对为未登记新面**。亲核成立：`registerCleanup`（L150-156）全仓 src/main 生产调用点 grep 零命中（唯一同名命中为 nop-stream WindowOperator.registerCleanupTimer，无关）；`destroy()` L160-163 `if (list == null) return;` 早退发生在 cleanups 回收（L181-186）之前，早注册的 cleanup 将被静默丢弃。**WI 2.1 nop-kernel 审计目录（2026-10-16-2316）全文 grep registerCleanup/cleanups 零命中——该面未被设施本体单元登记**，移交 nop-kernel 线时按新登记面入台账（非重复）。

---

## 汇总

**发现清单（13 条）**：

| ID | 标题 | 严重程度 | 信心 |
|---|---|---|---|
| C6-001 | jar 轨插件加载失败路径泄漏 PluginClassLoader/JAR 句柄 | P1 | 确定 |
| C6-002 | 插件 deactivate/activate 失败状态机永久卡死 + 半停容器不可达（4 同族站点） | P1 | 确定 |
| C6-003 | 配置源构造器先调度后加载：初始失败泄漏永久定时任务（jdbc 变体附带连接滞留） | P1 | 确定 |
| C6-004 | ShellRunner 非 timeout 异常/中断路径不销毁子进程（进程+fd+读取线程泄漏） | P1 | 确定 |
| C6-005 | JdbcConfigSource 变更分发在保护块外：监听器异常永久静默杀死刷新任务 | P2 | 确定 |
| C6-006 | singleton scope 销毁顺序为 CHM 哈希序，非逆依赖序 | P2 | 确定 |
| C6-007 | destroy 链异常传播：destroyMethod 抛错跳过 ioc:destroy；多失败仅末个异常上抛 | P2 | 确定 |
| C6-008 | 停机期间容器内 bean 解析双重失效（started/running 先灭 + BeanContainer 指针先切） | P2 | 很可能 |
| C6-009 | onContainerStop 关闭不摘注册条目：restart 残留 ALREADY_CLOSED + 死容器强引用滞留 | P2 | 很可能 |
| C6-010 | VirtualThreadTaskExecutor 三面残留：初始化器无 destroy/注销缺失/关闭无等待（C6-CL-004 深化） | P2 | 确定 |
| C6-011 | IPluginManager 无聚合关闭路径（条件性 P2） | P2 | 确定 |
| C6-012 | GitRepositoryManager 仓库对象 close 责任完全外置（理论风险标注） | P2 | 很可能 |
| C6-013 | ImageCompressHelper 异常路径 ImageWriter 不 dispose | P3 | 确定 |

**CL 线索（4 条，CL-010 起）**：CL-010 插件子容器不经 PluginClassLoader 加载（隔离失效）；CL-011 Shell 输出收集无界（C5）；CL-012 ChangeSubscriptions 空条目不摘（C7）；CL-013 registerCleanup 未接线 + destroy 早退丢弃（nop-kernel，F8 设施）。

**总计**：13 条发现（P1×4、P2×8、P3×1）+ 4 条维度集外线索；另确认配对成立面 9 项（见资源获取点配对表），已立案同位点（C1-004/C1-009/C1-010/C4-005/CL-001~009/C6-CL-004）均未重复立案，其中 C6-CL-004 已按任务要求深化为 C6-010。

---

## 维度复核结论（独立复核子代理，2026-10-17）

- **复核范围**：C6-001~013（本文件）+ C6-014、CL-021（C6-round2.md）共 14 条发现 + 5 条 CL 线索；复核方式 = 全部 file:line 亲自打开核对（与初审相互独立，怀疑姿态），行号漂移以 live code 修正；历史对照面（cra-index、WI 1.1–2.1 审计目录、本单元 C1/C4 各轮报告）按本轮任务解除盲审。
- **总裁定**：**保留 14 / 降级 0 / 驳回 0**。四条 P1 全部维持。其中 3 条（C6-005/006/010）经交叉核对确认为**同站点跨维度重复登记**（分别与本单元 C1-021、C4-010、C4-004 + WI 2.1 kernel C6-002/C6-003 同面），转 cross-ref 合并处置、判级随主条目，不改变缺陷成立事实。**本复核推翻初审具体触发时序 2 处**（C6-005 交叠 a/b）、**修正归因 1 处**（C6-014 的 CoreInitialization.destroy 归因）、**补注精度 4 处**（C6-001 GC/cleaner 回收与装配点缺失、C6-003 单体进程退出收敛、C6-004 时序 C 前置、C6-009 restart 触发表述），均不改判级。
- **判级复核专项结论**：
  - **C6-003 启动期降档不适用论证：成立**。降档规则的前提是"触达仅启动期**且**后果限于启动期"；本条泄漏任务挂于 JVM 静态 `GlobalExecutors.globalTimer()`（本复核亲证：全仓无任何 initializer/destroy 链关闭该定时器），泄漏窗口覆盖全运行期并跨 destroy→reinit 代际，前提不满足。补注：单体应用启动失败以进程退出收场时泄漏随 JVM 消亡，现实风险面为嵌入宿主/初始化重试/生命周期循环场景——此为频率修正非定性修正，P1 维持。
  - **C6-002 触发现实性：成立**。子容器 stop 抛错链逐环亲核（`BeanScopeImpl.close` L116-117 上抛末个 destroy 异常 → `BeanContainerImpl.stop` L616 原样上抛 → `doDeactivate` 的 `state=LOADED` 不可达）；destroy-method 抛错是框架生命周期内的常规可预期事件，单线程即可确定性复现；且 reconcile 路径的 catch（`deactivateIfActive`）只吞异常不复位状态，卡死后果与调用方无关。
  - **C6-001 生产可达性：成立**（附条件标注）。HttpPluginResourceResolver 为远程插件仓库部署形态设计（service-url 模板 + SHA256 三源校验 + 本地缓存），jar 轨是设计内生产形态；但本仓内 PluginManagerImpl 尚无 beans.xml 装配点（仅测试实例化），宿主装配为预期但未落地的用法——与 C6-011 的条件化口径一致，合并裁定时可统一标注。
- **第 2 轮产物复核**：10 项 loadPlugin 失败点矩阵亲核成立（#2/#3/#4 即 C6-001 三前置点；#5 try 内 finally 兜底；#8 ChangeSubscriptions.subscribe 无现实抛出点判定合理——L44-56 仅 computeIfAbsent+addListener；#10 reconcile 逐定义 catch L394-413）。「先调度后加载」族全枚举 = 2 站点经独立 grep 复核成立（本单元范围内无第 3 站点）。进程资源配对表 10 项成立（E/F/F' 即 C6-014）。**「activate 回滚扑空泄漏」候选否证复核：否证正确**（`BeanContainerImpl.start` L539-542 catch → `handleStartError` → `stop()` 自愈，同步/异步双路径亲读）。round-2 配对成立面增补 7 项抽查（calculateSha256 try-with-resources L292、configLogLevel appendOnCancelTask+doStop L527 cancel、LifeCycleSupport doStop 吞错 + allowRestart=true 缺省 L26 等）全部核实成立。
- **CL 处置汇总**：CL-010 确认（功能面移交插件设计线注明成立）；CL-011 确认（移交 C5）；CL-012 确认（移交 C7）；CL-013 确认移交，**且经 WI 2.1 审计目录全文 grep 核对为未登记新面**（registerCleanup/cleanups 零命中），移交 nop-kernel 线时按新面入台账；CL-021 确认（口令入进程参数，日志掩码不护 ps 账面，移交安全线）。
- **现状标注**：14 条发现全部为**新发现（本单元本轮产出、未修复）**——git log 核实审计涉文件无修复提交，live code 缺陷均在；cra-index WI 1.1–2.1 台账无本单元条目（本单元审计尚在进行）。同单元跨维度重复（C1-021/C4-004/C4-010）属本单元 Phase 3 并行产出的同站点立案，非"已知未修复"历史负担。
- **争议点与移交合并裁定事项**：
  1. C6-005 ↔ C1-021 同站点（JdbcConfigSource.tryTriggerChange）：C6 侧两条触发时序被推翻、C1 侧以 SPI/可插拔 executor 触发面终裁 P1——建议合并归 C1-021，C6-005 转 cross-ref；
  2. C6-006 ↔ C4-010 同站点（singleton 销毁哈希序）：建议合并归 C4-010（含修复建议与 F3 全清单），C6-006 转 cross-ref；
  3. C6-010 三重登记（本条 + 本单元 C4-004 + WI 2.1 kernel C6-002/C6-003 及其 round2 F3 扩展）：建议设施本体归 WI 2.1、使用侧载体归 C4-004，C6-010 转 cross-ref 保留 F3 清单增量；
  4. C6-001/C6-011 的「仓内无装配点」口径统一标注（前者 P1 维持、后者条件性 P2，差异理由：前者泄漏随失败次数线性累积且 jar 轨为设计内生产形态，后者后果依赖宿主装配才成立）；
  5. C6-012 若合并裁定采用更严的「仓内零调用方」口径可降 P3，本复核按 campaign 休眠缺陷 P2 先例维持原判。

## 最终保留项（复核后清单）

| ID | 标题 | 复核裁定 | 复核后判级 | 处置 | 现状 |
|---|---|---|---|---|---|
| C6-001 | jar 轨插件加载失败路径泄漏 PluginClassLoader/JAR 句柄 | 保留（补注 GC/cleaner 回收精度 + 装配点缺失标注） | P1 | 独立修复项 | 新发现未修复 |
| C6-002 | 插件 deactivate/activate 失败状态机永久卡死（4 同族站点） | 保留 | P1 | 独立修复项（try/finally 收敛终态 + forceUnload 恢复入口） | 新发现未修复 |
| C6-003 | 配置源构造器先调度后加载泄漏永久定时任务（jdbc 连接滞留） | 保留（启动期降档不适用论证成立；补注单体进程退出收敛） | P1 | 独立修复项（先加载后调度或构造器 catch cancel） | 新发现未修复 |
| C6-004 | ShellRunner 非 timeout 异常/中断路径不销毁子进程 | 保留（补注时序 C 前置与仓内调用点暴露面） | P1 | 独立修复项，**修复位置约束：兜底置于外层 try finally（L80），与 C6-014 同 diff** | 新发现未修复 |
| C6-005 | JdbcConfigSource 变更分发在保护块外 | 保留缺陷；两条触发时序推翻 | 随 C1-021（P1） | **cross-ref 并入 C1-021**（同站点，含 F3④） | 新发现未修复 |
| C6-006 | singleton scope 销毁 CHM 哈希序 | 保留缺陷；同站点确认 | 随 C4-010（P2） | **cross-ref 并入 C4-010**（topoIndex 逆序销毁） | 新发现未修复 |
| C6-007 | destroy 链异常传播（destroyMethod 抛错跳过 ioc:destroy + 末异常覆盖 + onContainerStop 遮蔽） | 保留 | P2 | 独立修复项（两步聚合 + 三层异常聚合，含 Error/Throwable 口径） | 新发现未修复 |
| C6-008 | 停机期 bean 解析双重失效（访问位先灭 + 指针先切） | 保留 | P2 | 独立修复项（先 stop 后切指针 + 只读通道） | 新发现未修复 |
| C6-009 | onContainerStop 关闭不摘注册条目（ALREADY_CLOSED + 死容器滞留） | 保留（触发面精度补注：需 bind-without-unbind 或并发 restart） | P2 | 独立修复项（close 后摘条目） | 新发现未修复 |
| C6-010 | VirtualThreadTaskExecutor 生命周期三面残留 | 保留；**三重登记确认**（C4-004 + WI 2.1 kernel C6-002/C6-003） | P2 | **cross-ref**（设施归 WI 2.1、使用侧归 C4-004）；F3⑥ 为本条独立增量 | 新发现未修复 |
| C6-011 | IPluginManager 无聚合关闭路径 | 保留（条件性标注正确） | P2（条件性） | 独立修复项（接口 destroy + 逆序清理） | 新发现未修复 |
| C6-012 | GitRepositoryImpl close 责任外置 | 保留（理论标注诚实；降 P3 留合并裁量） | P2（理论风险） | 独立修复项（引用计数缓存或弱引用追踪 + javadoc 契约） | 新发现未修复 |
| C6-013 | ImageCompressHelper 异常路径 ImageWriter 不 dispose | 保留 | P3 | 独立修复项（try-with-resources + finally dispose） | 新发现未修复 |
| C6-014 | ShellRunner 进程创建与内层 try 间失败窗口（REE） | 保留（条件性 P2 维持；**触发归因修正**：非 CoreInitialization.destroy，实为宿主/测试显式销毁执行器窗口） | P2（条件性） | **与 C6-004 同 diff**（外层 finally 兜底覆盖 A-D/E/F/F' 全路径） | 新发现未修复 |

**复核后统计**：14 条全部保留（P1×4 维持、P2×9（其中 3 条转 cross-ref 判级随主条目：C6-005→C1-021 P1、C6-006→C4-010 P2、C6-010→WI 2.1/C4-004 P2）、P3×1）；驳回 0、降级 0、升格 0；修复项归并为 9 个独立修复载体（C6-001 / C6-002 / C6-003 / C6-004+C6-014 同 diff / C6-007 / C6-008 / C6-009 / C6-011 / C6-013，C6-012 视合并裁量）+ 3 个 cross-ref。CL 5 条全部确认（CL-010 插件设计线、CL-011→C5、CL-012→C7、CL-013→nop-kernel 新面、CL-021→安全线）。

**维度终止信号复核**：第 2 轮「达到终止信号、不建议第 3 轮」的判定**维持**——本轮复核未产生新立案、未推翻任何缺陷成立性，仅收敛触发面与归因；C6-001/002/004 若修复方案按本复核确认的位置约束（外层 finally）与状态机终态收敛方案实施，无新增失败路径风险。
