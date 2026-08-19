# nop-core-framework 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-core-framework
- 文件数: 234（`*/src/main/java` 下主代码，实测计数；任务描述中的 315 与实际不符。分布：nop-ioc 135、nop-config 40、nop-plugin 31、nop-security 12、nop-log 11、nop-boot 5）
- 覆盖范围声明:
  - **深读全文**（逐行验证）: nop-ioc 的 BeanContainerImpl / BeanDefinition / ProducedBeanInstance / BeanCreationContext / BeanTopologySorter / BeanProperty / BeanScopeImpl / BeanScopeContextImpl / BeanScopeContext / BeanFinder / DelegateInvocationHandler / DefaultBeanClassIntrospection / AppBeanContainerLoader / BeanContainerBuilder / BeanConditionEvaluator / BeanDefinitionBuilder / BeansDefinition / BeanParentResolver / EmbeddedBeanCollector / AopBeanProcessor / ConfigExpressionProcessor / SpringBeanSupport / ConfigValueResolver / InjectRefValueResolver / BeanContainerValueResolver / IocCoreInitializer / BeanContainerVariableScope / IocConfigs；nop-plugin-manager 全部核心类（PluginManagerImpl / PluginInstanceImpl / VfsPluginDefinition / PluginScopeImpl / ServiceProxy / InstanceConfigProvider / HttpPluginResourceResolver / PluginClassLoader / PluginConfig / CoeffectConfigHelper）及 nop-plugin-api 的 IPluginInstance / IPluginScope；nop-config 的 DefaultConfigProvider / ChangeSubscriptions / ChangeSubscription / ConfigStarter / ConfigChangeApplier / SingleThreadConfigExecutor / JdbcConfigSource / DynamicConfigSource / AbstractFileConfigSource / KeyFileConfigSource / PropsFileConfigSource / DefaultConfigBeanLoader / CompositeConfigSource / ProfileConfigSource；nop-boot 的 NopApplication；nop-security 的 SecurityHelper / DefaultKeyManager；nop-log 的 LogbackConfigurator。
  - **grep 全量扫描 + 命中点回读**: 空 catch、`new RuntimeException`、`printStackTrace`、synchronized、双检锁、MessageDigest/SHA256、非 final static、可变 static 集合。
  - **未逐行阅读**: nop-ioc 的 model 包（Bean*Model 系列，多为 _gen 生成代码与简单委托）、剩余简单 resolver（List/Map/Set/Props/Concat/Constant/Null/Fixed/Xpl/Expression/ConfigMap/InjectType/BeanType）、BeanContainerDumper、nop-log java/log4j2 配置器、nop-security 的 RsaHelper/BcHelper/KeyBean/KeySetBean、nop-config 的 router/enhancer/model 剩余类、NopBanner/StartupInfoLogger。这些为低风险样板/委托代码，且全部经过 grep 扫描（无空 catch、无 bare RuntimeException、无可变 static 集合命中）。
  - 测试代码不在范围。依赖的 nop-core/nop-commons 行为（如反射 setter 语义）仅按接口推断，未跨模块深查。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 4 |
| P2 | 5 |
| P3 | 6 |

## 发现列表

### [P1] ioc:condition 的 unless-property 分支读取错误的模型对象（NPE / 错误求值）

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanConditionEvaluator.java:246-252`
- **维度**: D1（另涉 D8：与 beans.xdef 声明的条件语义不符）
- **证据**:
```java
if (conditionModel.getUnlessProperty() != null) {
    BeanIfPropertyCondition ifProperty = conditionModel.getIfProperty();  // 应为 getUnlessProperty()
    if (checkProperty(ifProperty.getName(), ifProperty.getValue(),
            ifProperty.isEnableIfMissing(), ifProperty.isEnableIfDebug())) {
        return false;
    }
}
```
- **现状**: unless 分支取的是 `getIfProperty()` 而非 `getUnlessProperty()`。同文件 `dumpDisabled`（439-440 行）用的是正确的 `unlessProperty.getName()/getValue()`，证明此处是笔误而非有意设计。
- **风险**:
  1. 只配置 `unless-property`（无 if-property，schema 允许，见 `_dump/nop/app/nop/schema/beans.xdef:136`）时 `getIfProperty()` 返回 null → `ifProperty.getName()` NPE，容器构建直接崩溃，异常无 bean 定位信息。
  2. 同时配置 if+unless 时，unless 条件实际重复求值 if 条件：if 通过即判 false → bean 被错误禁用。
- **建议**: 改为 `BeanUnlessPropertyCondition unlessProperty = conditionModel.getUnlessProperty();` 并用其字段求值；补一个 only-unless 与 if+unless 的单测。
- **误报排除**: 已核对 `_BeanConditionModel` 生成代码：`getIfProperty()`/`getUnlessProperty()` 是两个独立字段，分别由 `<if-property>`/`<unless-property>` 元素填充，不存在共享实现的可能；仓库内当前无 beans.xml 使用 unless-property（无内置触发方），但该元素是 beans.xdef 公开 schema 能力。

### [P1] 通配 pattern 的配置变更订阅永远不触发（注册到错误的 map）

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/impl/ChangeSubscriptions.java:46-50`
- **维度**: D1 / D8
- **证据**:
```java
private Runnable subscribePattern(String pattern, IConfigChangeListener listener) {
    ChangeSubscription sub = simpleSubscriptions.computeIfAbsent(pattern, ChangeSubscription::new); // 写错 map
    sub.addListener(listener);
    return () -> sub.removeListener(listener);
}
```
- **现状**: 带 `*` 的 pattern 订阅被放进 `simpleSubscriptions`，而 `trigger()` 只从 `patternSubscriptions` 读取 pattern 订阅（64-70 行），后者永远是空 map。`trigger()` 的 simple 查找是精确名匹配，pattern key 不可能命中。
- **风险**: `IConfigProvider.subscribeChange` 的公开契约（`nop-api-core/.../IConfigProvider.java:45` javadoc："最后一个部分可以是\*，表示模糊匹配"）不成立——使用 `a.b.*` 订阅的监听器静默地永远不会被回调（订阅成功、无任何报错），典型难以排查的静默失效。
- **建议**: `subscribePattern` 改用 `patternSubscriptions.computeIfAbsent(...)`；补一个通配订阅触发的单测。另建议 `trigger()` 对 simple map 中意外出现的 pattern key 无需处理（修复后不会发生）。
- **误报排除**: 已确认 `trigger()` 全文只有这两处 map 读取；当前仓库主代码无通配 pattern 调用方（潜在型缺陷，不影响存量行为），但契约明确承诺该语义。

### [P1] 构造器注入 + 循环依赖时静默创建重复的单例实例（singleton 语义被破坏）

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:481-510`（getConstructorArgs/newInstance）与 `BeanContainerImpl.java:367-397`（getBean0）、`BeanDefinition.java:512-533`（newObject 的 scope.add 时机）
- **维度**: D1
- **证据**:
```java
// BeanDefinition.getConstructorArgs: 构造器参数解析时 beanCtx 传 null
Object value = constructorArgs.get(i).resolveValue(container, scope, null);

// InjectRefValueResolver.resolveValue → container.getBean(ref, true, null)
// BeanContainerImpl.getBean: beanCtx==null → 每次新建 BeanCreationContext

// BeanDefinition.newObject: 先 newInstance（触发依赖创建），之后才 scope.add 暴露早期引用
Object bean = newInstance(scope, container, beanCtx);
...
if (scope != null) { scope.add(getId(), producedBeanInstance); }  // 晚于构造器参数解析
```
- **现状**: 属性注入的循环依赖靠 "先 scope.add 再设属性" 的早期暴露机制正确解决；但构造器参数解析发生在 `scope.add` 之前，且不存在 "正在创建中" 标记。当 A 的构造器参数引用 B、B（或其依赖链）又引用 A 时：A 尚不在 scope → 递归进入 `A.newObject` 第二次执行 → 产生 A 的第二个实例 A2；`scope.add` 是 `beans.put`（覆盖），最终容器注册的是 A1，而 B 持有 A2。`CFG_IOC_BEAN_DEPENDS_GRAPH_ALLOW_CYCLE` 默认 **true**（IocConfigs.java:65-66），拓扑排序不报错，启动静默通过。是否触发取决于拓扑排序把 A 还是 B 排前（排前顺序由图迭代决定，非语义控制）。
- **风险**: 单例 bean 出现两个实例且互相持有不一致引用；A 的 init-method、`subscribeConfigChange`（配置订阅）在两个实例上各执行一次，行为不可预期。对比 Spring 会对构造器循环给出 `BeanCurrentlyInCreationException` 明确失败。
- **建议**: 引入 "in-creation" 集合（或复用 scope + 状态标记），`newObject` 重入同一 bean 定义时抛 `ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE` 类明确异常；至少在构造器参数解析前先注册占位实例或记录创建栈。
- **误报排除**: 逐步核对了 `getBean0` 的 fast path（scope 命中才走早期返回）、`synchronized(beanDef)` 同线程可重入、`scope.add` 使用覆盖式 `put`，以及默认 allow-cycle=true 下拓扑排序器不抛错；未发现任何其他机制（如 in-creation 标记）会阻断递归 newObject。

### [P1] 文件型配置源一次刷新异常即永久停止热刷新（scheduleWithFixedDelay 任务被静默取消）

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/source/file/AbstractFileConfigSource.java:53-60, 62-84`
- **维度**: D1 / D4
- **证据**:
```java
private void refreshConfig() {                 // 无任何 try-catch
    Map<String, ValueWithLocation> vars = loadConfig();
    ...
}
// loadConfig 的 catch 中: LOG.error(...); throw NopException.adapt(e);  // 异常继续上抛
```
- **现状**: `refreshConfig` 作为 `GlobalExecutors.globalTimer().scheduleWithFixedDelay` 的任务（已核实 `DefaultScheduledExecutor` 是对 JDK `ScheduledThreadPoolExecutor` 的直接透传，无异常包装）。按 JDK 契约，任务一次未捕获异常后后续执行全部被抑制。而 `loadConfig` 故意 `LOG.error` 后再 `throw`——一次瞬时错误（如刷新瞬间文件被写一半导致 `PropsFileConfigSource` 的 `JsonTool.parseBeanFromResource` 解析失败、或 `KeyFileConfigSource.readText` IO 错误）就永久、静默地终止该配置源的热刷新。
- **风险**: 启用了 `nop.config.key-file.paths` / `nop.config.props-file.paths` 的应用，一次瞬时文件读取/解析错误后配置热更新失效且无任何后续日志（错误处理意图明显是想记录后继续——对比 `JdbcConfigSource.refreshConfig` 内部 catch 并重试的实现，两处行为不一致）。
- **建议**: `refreshConfig` 内整体 try-catch，记录错误并返回（保留下轮刷新）；或 `loadConfig` 的定时刷新路径不重抛。补 "解析失败后下一轮仍刷新" 的单测。
- **误报排除**: 已核实 `DefaultScheduledExecutor.scheduleWithFixedDelay`（nop-commons）直接委托 JDK executor，无包装；子类 `loadConfigFromPath` 确实会因 IO/解析抛出（未捕获）。

### [P2] loadPluginFromJar 失败路径泄漏 PluginClassLoader（jar 句柄/metaspace）

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java:984-1013`
- **维度**: D2
- **证据**:
```java
PluginClassLoader classLoader = new PluginClassLoader(urls.toArray(new URL[0]),
        this.getClass().getClassLoader());
IPlugin plugin = classLoader.loadPlugin();                       // try 块之外
Map<String, Object> config = pluginConfigProvider.getPluginConfig(coords); // try 块之外
try {
    ...
} catch (RuntimeException e) {          // 不含 Error
    try { plugin.unload()/stop(); } finally { IoHelper.safeCloseObject(classLoader); }
    throw e;
}
```
- **现状**: `classLoader.loadPlugin()`（插件类缺失/实例化失败，坏插件包的常见失败）与 `getPluginConfig()` 抛异常时 classloader 不会 close；`plugin.load/start` 抛 `Error`（如解析插件类时的 `NoClassDefFoundError`/`OutOfMemoryError` 前者对坏插件很现实）同样绕过 catch。`computeIfAbsent` 失败不落 map，重试会再建新 classloader，旧的永不释放。
- **风险**: 反复加载坏插件（监控自动重试场景）持续泄漏 URLClassLoader（打开的 jar 文件描述符 + 加载类的 metaspace），最终 fd/metaspace 耗尽。
- **建议**: 将 `loadPlugin()`/`getPluginConfig()` 纳入 try，catch 改为 `RuntimeException | Error`，finally 中统一 `safeCloseObject(classLoader)`（成功路径保持现有赋值后再关的语义不变）。
- **误报排除**: 已核对 `PluginClassLoader extends URLClassLoader`（close 释放 jar 句柄）与 `IoHelper.safeCloseObject` 行为；确认 loadPlugin/getPluginConfig 两行确在 try 之外。

### [P2] getBean0 的 synchronized(beanDef) 经递归创建形成嵌套锁，多线程懒初始化可死锁

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:383-394`
- **维度**: D3
- **证据**:
```java
synchronized (beanDef) { //NOSONAR
    beanInstance = beanScope.get(beanDef.getId());
    if (beanInstance == null) {
        beanInstance = beanDef.newObject(beanScope, this, beanCtx);  // 递归触发依赖 bean 的 getBean0 → synchronized(依赖beanDef)
    }
}
```
- **现状**: 创建 A 期间（持 A 锁）解析构造器参数/属性会进入依赖 bean 的 `synchronized(depDef)`，形成 A→B 的锁序。两个线程同时懒初始化互为（构造器）依赖的 A、B 时：T1 持 A 等 B，T2 持 B 等 A（B 的属性引用 A 且 A 尚未 scope.add 时走不到 fast path，必须拿 A 的锁）→ 互等死锁。属性型环 + 单线程（启动期）不会触发；需要运行期多线程并发首次触发同一环上的 bean。
- **风险**: 容器永久挂死（所有等待这两个 bean 的请求阻塞）。条件较苛刻（环 + 构造器边 + 并发懒初始化），但一旦发生是静默死锁而非异常。
- **建议**: 与 P1-构造器环问题一并处理：检测到重入同一 bean 定义即抛异常，可同时消除该死锁路径；或将每容器的创建锁统一为单一粗粒度锁（仅保护 newObject 的首入检查），代价是并发懒初始化串行化。
- **误报排除**: 逐路径核对 fast path 条件（`includeCreating && beanScope != null && bean in scope`）——A 在构造器解析阶段不在 scope，B 侧无法走 fast path，必须阻塞拿 A 锁；确认无其他解锁/超时机制。

### [P2] 插件实例 destroy 与 activate/reconcile 的竞态窗口可产生"僵尸"已激活容器（永不停止）

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginInstanceImpl.java:293-321`；`PluginManagerImpl.java:236-262`（destroyInstance 不持 reconcileLock）
- **维度**: D3
- **证据**:
```java
void destroySelf() {
    try {
        doDeactivate();                    // synchronized(lifecycleLock)，退出锁后…
    } finally {
        definition.removeInstance(instanceKey);   // …到 remove 之间无锁窗口
        ...
    }
}
// reconcile()/activateIfInactive() 在窗口内可看到 DEACTIVATED 实例并调用 activate()
```
- **现状**: `destroySelf` 的 "deactivate（持 lifecycleLock）" 与 "removeInstance（无锁）" 之间存在窗口：并发的 reconcile（配置订阅回调触发）或显式 `activate()` 在窗口内重新激活实例（doActivate 拿锁时 state 已是 DEACTIVATED → 正常激活），随后 destroySelf 继续 `removeInstance` —— 注册表丢失该实例，但其子容器已 start、effect 已注册，此后无人引用、永不 stop。同理 `destroyInstance`/`createInstance` 与 `reloadPlugin`（持 reconcileLock）之间未互斥，destroy 收集闭包后新 create 的实例可逃过 reload 的快照销毁。
- **风险**: HMR/配置驱动的自动 reconcile 与手工生命周期操作并发时，泄漏已激活的 BeanContainer（bean 不销毁、订阅不取消）；实例从 registry 消失后不可观测、不可恢复。
- **建议**: destroySelf 将 "deactivate + removeInstance" 全程置于 lifecycleLock 内，并在 doActivate 入口检查 `definition.getInstance(instanceKey) == this`（已移除则拒绝激活）；manager 侧将 destroyInstance/createInstance 的实例变更段纳入 reconcileLock。
- **误报排除**: 核对 `doActivate` 仅以 `state == ACTIVATED` 作幂等闸门，不校验注册表存在性；`reconcile` 的 `activateIfInactive` 遍历 `def.getInstances()` 快照时实例尚在注册表（remove 未发生），激活合法通过——窗口真实存在。属窄窗口竞态，非必现。

### [P2] SecurityHelper.toRSAPublicKey 抛 bare RuntimeException，违背平台错误处理两档策略

- **文件**: `nop-core-framework/nop-security/src/main/java/io/nop/security/utils/SecurityHelper.java:70-75`
- **维度**: D4 / D7
- **证据**:
```java
private static ... toRSAPublicKey(KeyBean keyBean) {
    ...
    try {
        ...
    } catch (Exception e) {
        throw new RuntimeException(e);   // 同类其余方法均为 NopException.adapt(e)
    }
}
```
- **现状**: nop-security 属框架核心公共 API（AGENTS.md 约定：框架核心用 `NopException` + ErrorCode / `NopException.adapt`，禁止 bare `RuntimeException`）。同文件其他 4 个方法全部用 `NopException.adapt(e)`，唯独此处不一致。
- **风险**: 公钥配置错误时调用方收到无错误码、无定位信息的 RuntimeException，破坏统一的异常处理/错误码体系。
- **建议**: 改为 `throw NopException.adapt(e);`。
- **误报排除**: grep 确认这是本模块主代码中唯一的 bare `RuntimeException`（另两处 printStackTrace 均在测试代码）。

### [P2] 插件下载 SHA256 校验的完整性声明与实现存在信任模型缺口

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/resolver/HttpPluginResourceResolver.java:94-124, 174-193`
- **维度**: D5
- **证据**:
```java
// 缓存命中路径: 与本地 sidecar 文件自比对
String expected = readChecksumText(shaFile);      // {jar}.sha256 与 jar 同目录、同信任域
String actual = calculateSha256(jarFile);
if (!checksumMatches(expected, actual)) throw ...

// 下载路径 hash 来源优先级: 响应 header → {url}.sha256（同一服务器）→ 配置 hash map
String expected = getChecksumHeader(response);
if (!StringHelper.isEmpty(expected)) return normalize(expected);
```
- **现状**: 类 javadoc 声明 "下载文件的完整性有SHA256校验码保证"。但：(1) 缓存命中校验比对的 expected 来自与 jar 同目录的 `.sha256` sidecar——能篡改缓存 jar 的攻击者同样能重写 sidecar，该校验只能防意外损坏，不提供完整性保证；(2) 下载路径三个 hash 来源中前两个（响应 header、`{url}.sha256`）都来自同一服务端，仅第三优先级的配置 `expectedHashes` 是独立信任源，却排在最后（header 命中时配置 hash 根本不参与）；HTTP（非 HTTPS）部署下 MITM 可同时替换 jar 与 hash。
- **风险**: 安全声明（W7 注释）与实际保证不符；防御者可能据 javadoc 误判威胁模型。属声明/实现漂移而非可直达漏洞（HTTPS + 可信源场景下安全）。
- **建议**: 至少：文档如实标注缓存校验仅防损坏；提供 "严格模式" 配置——启用后仅信任配置 hash map（或 header 命中时仍与配置 map 比对），`skip-cache-verify` 之外增加 `require-pinned-hash` 类开关。
- **误报排除**: 已通读全文件确认无其他独立校验来源；`expectedHashes` 注入与大小写归一处理本身正确。此条为安全设计强度问题，非可利用漏洞证明。

### [P3] 多处 public static 非 final 可变字段（含状态常量与单例持有者）

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:75-78`、`ProducedBeanInstance.java:24-27`、`BeanTopologySorter.java:45`、`nop-ioc/.../api/BeanScopeContext.java:19`、`nop-config/.../starter/ConfigStarter.java:77`、`nop-log/.../LoggerConfigurator.java:19` 等
- **维度**: D3 / D7
- **证据**:
```java
public static int STATUS_UNRESOLVED = 0;      // BeanDefinition，public 可写
public static BeanTopologySorter INSTANCE = new BeanTopologySorter();
static IBeanScopeContext _instance = new BeanScopeContextImpl();
static ConfigStarter g_instance = new ConfigStarter();
```
- **现状**: `STATUS_*` 等语义为常量却声明为可写 public static；`INSTANCE/_instance/g_instance` 为可变单例持有字段且部分无 volatile（`BeanScopeContext._instance`、`ConfigStarter.g_instance` 被 `registerInstance` 替换时无 happens-before 保证）。
- **风险**: 常量被外部改写属极低概率；单例字段替换的可见性问题在正常启动序列下被类初始化/CHM 操作掩盖。维护性风险为主。
- **建议**: `STATUS_*` 加 final；单例字段尽量 final 或加 volatile + 文档说明替换语义。
- **误报排除**: grep 全模块仅命中上述各处，`_gen` 生成代码中的实例字段不计。

### [P3] BeanContainerImpl.classIntrospection 懒初始化无同步且非 volatile

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:129-134`
- **维度**: D3
- **证据**:
```java
public IBeanClassIntrospection getClassIntrospection() {
    if (classIntrospection == null) {
        classIntrospection = new DefaultBeanClassIntrospection(classLoader);
    }
    return classIntrospection;
}
```
- **现状**: 并发首次调用（多线程懒创建 bean 时属性转换会走到）可能各建一个 introspection 实例并相互覆盖；字段非 volatile，理论上存在发布不完整风险（DefaultBeanClassIntrospection 内含 SpringBeanSupport，构造仅做类加载探测，实际危害低）。
- **风险**: 轻微——多余对象创建 + 理论可见性问题；无状态错误。
- **建议**: 构造时初始化或改为 volatile + 本地变量模式。
- **误报排除**: `BeanContainerBuilder.build` 会主动 setClassIntrospection，主容器路径不触发；仅 `buildNewInstance` 派生容器未设置时才走懒初始化，确认可触达但后果轻。

### [P3] BeanScopeImpl.close() 与并发 add() 的 check-then-act 竞态：close 抛 bare IllegalStateException 且泄漏 bean

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanScopeImpl.java:64-108`
- **维度**: D3 / D4
- **证据**:
```java
public void add(String name, ProducedBeanInstance value) {
    checkClosed();            // (1)
    beans.put(name, value);   // (2) close 可插在 (1)(2) 之间
}
...
public void close() {
    closed = true;
    for (...) { remove(beanName, entry.getValue()); }  // 遍历时该 bean 尚未 put
    Guard.checkState(beans.isEmpty());                 // 失败抛 IllegalStateException（非 NopException）
}
```
- **现状**: 线程 T2 懒创建 bean 通过 checkClosed 后、put 前被 T1 的 stop() 插入：close 遍历看不到该 bean → `beans` 非空 → `Guard.checkState` 抛 IllegalStateException（非平台规范异常）→ `stop()` 失败；该 bean 已入 scope 但永不 destroy。
- **风险**: 窄窗口竞态；后果是容器停止抛裸 IllegalStateException + 单 bean 泄漏。触发需懒初始化与 stop 并发。
- **建议**: close 改为 `beans` 快照循环 + 容忍后续 put（或 add 失败时回滚 put）；终态校验失败时抛 NopException 带上下文。
- **误报排除**: 确认 getBean0 的 add 路径只持 `synchronized(beanDef)`，与 close 无互斥；checkClosed 与 put 非原子。

### [P3] VfsPluginDefinition.state 非 volatile（跨线程生命周期状态可见性）

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java:78`
- **维度**: D3
- **证据**:
```java
private PluginState state = PluginState.UNLOADED;   // 非 volatile；load/unload 写，reconcile/manager 线程读
```
- **现状**: `state` 被 load（主线程）/unload/reload 与 reconcile（config executor 线程）、`checkChangedAndReload` 并发读写，无 volatile。实际多数路径经 `plugins`（ConcurrentHashMap）的 happens-before 掩盖（先 put 后 get），但 `reloadPlugin` 内 `plugins.remove` 之后再读 state 的序列没有屏障保证。
- **风险**: 理论上可见性延迟导致 checkLoaded/门控判断读到旧状态；实际难以观测。
- **建议**: 加 volatile（同类字段 `lastModified`/`definitionConfig` 已是 volatile，风格也不一致）。
- **误报排除**: 确认同类字段已 volatile 而此字段遗漏，属一致性疏漏。

### [P3] BeanDefinitionBuilder 对未知属性值类型抛裸 IllegalArgumentException + 拼接式伪错误码

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanDefinitionBuilder.java:822`
- **维度**: D4 / D7
- **证据**:
```java
throw new IllegalArgumentException("nop.err.ioc.invalid-prop-value-type:" + value);
```
- **现状**: 框架核心装配器抛裸 IllegalArgumentException，错误信息用字符串拼接伪装错误码（IocErrors 中无此码），无 bean/属性定位参数。
- **风险**: 当前所有 IBeanPropValue 类型都有分支，属内部不变量兜底，正常配置不可达；一旦新增类型漏改，异常信息不符合平台规范且无定位。
- **建议**: 定义 `ERR_IOC_INVALID_PROP_VALUE_TYPE` 错误码，抛 NopException 带 bean/propName 参数。
- **误报排除**: 已核对 buildResolver 的 instanceof 分支覆盖全部 model 类型，确认仅兜底可达。

### [P3] 条件求值定点迭代上限 5 轮，超深 on-bean/missing-bean 链静默禁用 bean

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanConditionEvaluator.java:92-96, 108-114`
- **维度**: D1（边缘）
- **证据**:
```java
for (int i = 0; i < 5; i++) {
    if (!processCandidates(this::checkBeanCondition) && !this.processAlias()) {
        break;
    }
}
if (!candidateBeans.isEmpty()) {
    for (BeanDefinition bean : this.candidateBeans) {
        LOG.warn("nop.ioc.candidate-check-fail:...");   // 仅 warn 后强制禁用
        bean.getCondition().setDisabled(true);
    }
}
```
- **现状**: on-bean/missing-bean 条件依赖链超过 5 轮传播时，未决 candidate 被强制 `setDisabled(true)`，仅 LOG.warn，无异常。
- **风险**: 深链条件（少见但合法）下 bean 被静默禁用，仅能靠日志发现；warn 与异常策略不一致（alias 解析失败会抛 ERR_IOC_UNRESOLVED_ALIAS）。
- **建议**: 迭代上限提高到定义数，或超限时抛明确异常；至少 warn 中给出链路信息便于诊断。
- **误报排除**: 常规 ≤5 轮链路可正常收敛（每轮 processCandidates 至少消解一个 candidate 或终止），仅深链受影响，故 P3。

## 附：已排查未立项的疑点（误报排除记录）

- `DefaultConfigProvider.applyChange()` 无同步：全部生产入口经 `ConfigChangeApplier` → `SingleThreadConfigExecutor`（单线程）串行执行，`changeConfigSource` 仅启动期调用，设计上已串行化，不立项。
- `AbstractFileConfigSource.loadConfig` 中 `Files.walk` 未显式 close：处于 `flatMap` 内，JDK Stream.flatMap 语义会在内联流消费完毕后关闭内层流（Javadoc 明示），无目录句柄泄漏。
- `BeanCreationContext` 的 add* 未同步而 flush* synchronized：context 为单次创建栈内对象，同线程使用（含递归重入），synchronized 仅为重入保护，非缺陷。
- `DefaultConfigProvider`/`ConfigStarter` 的 `traceConfigVars`：配置值输出统一经 `StringHelper.maskSecretVar` 脱敏，敏感信息不泄漏。
- `PluginScopeImpl.effect` 的 IllegalArgumentException/IllegalStateException：`IPluginScope` 接口 Javadoc 明确承诺 IllegalStateException，实现与契约一致。
- `SpringBeanSupport` 识别 Spring `@Value`/`@Autowired`：是与 Spring bean 兼容的显式设计（类注释声明），不违背 Nop 自身代码约定（该约定约束平台自有代码与文档示例）。
- `HttpPluginResourceResolver.download` 的 finally 删临时文件、`.sha256` 落盘失败回滚（删 jar）：正确。
