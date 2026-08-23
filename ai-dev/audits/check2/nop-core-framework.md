# nop-core-framework 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-core-framework（boot/config/ioc/log/plugin/security）
- 文件数: 194（src/main/java，剔除 target/、`_` 前缀生成文件与 `_gen/`）
- 覆盖范围声明: 深读约 100 个文件（ioc 全部核心: BeanContainerImpl/BeanDefinition/ProducedBeanInstance/BeanScopeImpl/BeanCreationContext/BeanTopologySorter/BeanConditionEvaluator/BeanParentResolver/BeanDefinitionBuilder/AopBeanProcessor/BeanContainerBuilder/AppBeanContainerLoader/ConfigExpressionProcessor/全部 17 个 resolver；config: ConfigStarter/DefaultConfigProvider/ConfigChangeApplier/ChangeSubscriptions/JdbcConfigSource/AbstractFileConfigSource/KeyFileConfigSource/DefaultConfigValueEnhancer/ConfigExpressionResolver/ConfigSourceHelper/ProfileConfigSource/RouterConfigSource/SysServiceLoader 等；plugin: PluginManagerImpl/VfsPluginDefinition/PluginScopeImpl/ServiceProxy/PluginClassLoader/HttpPluginResourceResolver/DefinitionConfigProvider/AbstractPlugin；security: RsaHelper/SecurityHelper/DefaultKeyManager/CompositeKeyManager/KeySetHelper/BcCertHelper；boot: NopApplication/StartupInfoLogger/NopBanner；log: Logback/Log4j2Configurator/LoggerConfigurator）。其余约 94 个文件为接口、常量/错误码类、model 数据类与 log 初始化器，经模式扫描覆盖（@Inject private / Spring @Value / bare RuntimeException / catch 吞异常 / SimpleDateFormat 等 grep 全量扫描，未命中违例）。未覆盖区域: nop-ioc model/ 下简单数据类的逐行阅读（仅抽查 BeanValue/BeanConditionModel）、nop-log 的 Initializer 细节、nop-plugin-api 接口注释。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 4 |
| P2 | 10 |
| P3 | 9 |

## 发现列表

### [P1] BeanParentResolver 循环 parent 检测为死代码，环引用触发 StackOverflowError

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanParentResolver.java:56-62`
- **维度**: D1
- **证据**:
```java
if (parentBean.getStatus() == BeanDefinition.STATUS_UNRESOLVED) {
    resolveParent(parentBean);
} else if (parentBean.getStatus() == BeanDefinition.STATUS_RESOLVING) {
    throw new NopException(ERR_IOC_PARENT_REF_CONTAINS_LOOP).source(bean).param(ARG_BEAN_NAME, bean.getId())
            .param(ARG_PARENT, parent).param(ARG_TRACE, bean.getTrace())
            .param(ARG_LOOP_REF, parentBean.getId());
}
```
- **现状**: 代码检查 `STATUS_RESOLVING` 以抛出 `ERR_IOC_PARENT_REF_CONTAINS_LOOP`，但 `resolveParent()` 在递归前从未调用 `setStatus(STATUS_RESOLVING)`（全模块 grep 确认：`setStatus` 仅在 `BeanParentResolver.java:49` 设置 `STATUS_RESOLVED`，`STATUS_RESOLVING` 无任何写入点）。
- **风险**: A(parent=B) 且 B(parent=A) 时，A、B 状态始终为 UNRESOLVED，`resolveParent` 无限递归直到 StackOverflowError，本应得到的清晰循环错误永远不会抛出；SOE 堆栈巨大且可能污染启动日志/掩盖真实错误。
- **建议**: 递归前置 `bean.setStatus(STATUS_RESOLVING)`，`mergeWithParent` 完成后再置 `STATUS_RESOLVED`（注意异常路径也要复位状态）。
- **误报排除**: 已 grep 整个 nop-ioc 模块确认没有其他地方写入 `STATUS_RESOLVING`（仅常量定义处出现）；也确认 `BeanContainerBuilder.build()` 调用链中 `BeanParentResolver.resolve()` 是 parent 合并唯一入口，不会先由别处标记状态。

### [P1] ProducedBeanInstance.setHandler 将 JDK Proxy 强转为 DelegateInvocationHandler，ioc:proxy + bean-method 组合必抛 CCE

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/ProducedBeanInstance.java:208-210`（触发点 `BeanDefinition.java:641-645`、构造点 `BeanDefinition.java:546-551`）
- **维度**: D1
- **证据**:
```java
// BeanDefinition.createInstance: bean 字段被设为 JDK 动态代理对象
producedBeanInstance.setBean(createProxy(new DelegateInvocationHandler()));

// BeanDefinition.initBean: beanMethod 返回 InvocationHandler 后回填 handler
beanInstance.setHandler(((InvocationHandler) instance));

// ProducedBeanInstance.setHandler: 对 bean 字段（代理对象）做强转
public synchronized void setHandler(InvocationHandler handler) {
    ((DelegateInvocationHandler) bean).setHandler(handler);
}
```
- **现状**: `createProxy` 经 `ReflectionManager.newProxyInstance`（已核实为 `Proxy.newProxyInstance`）返回仅实现 `ioc:type` 接口的代理对象，代理对象不是 `DelegateInvocationHandler` 的实例。当 bean 同时配置 `ioc:proxy="true"` 与 `ioc:bean-method`（`beanMethod != null` 分支）时，init 阶段 `setHandler` 必然抛 ClassCastException，容器启动失败。
- **风险**: 文档化特性（beans.xdef:107 明确描述 ioc:proxy 语义）在该组合下 100% 崩溃；当前仓库 XML 未使用 ioc:proxy（全仓 grep 无命中），属潜伏缺陷，一旦业务启用即触发。
- **建议**: 在 `ProducedBeanInstance` 中保存 `DelegateInvocationHandler` 引用（或用 `Proxy.getInvocationHandler(bean)` 反查）再调用 `setHandler`。
- **误报排除**: 已读 `ReflectionManager.newProxyInstance`（nop-kernel/nop-core）确认返回 JDK Proxy；已核对 `AopBeanProcessor.checkProxy`（只校验类实现 InvocationHandler，救不了该强转）；已确认 `createInstance` 在 `beanMethod != null && isIocProxy()` 分支先 `setBean(createProxy(...))`，随后 initBean 才调 `setHandler`，时序上字段必然是代理对象。

### [P1] ConfigExpressionProcessor.parseSpringExpr 共享累积 configVars 列表，多 `${}` 占位表达式解析为错误值

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/ConfigExpressionProcessor.java:66-103`
- **维度**: D1
- **证据**:
```java
public IBeanPropValueResolver parseSpringExpr(...) {
    List<String> configVars = new ArrayList<>();              // 整个表达式共享一个 list
    List<IBeanPropValueResolver> resolvers = parseExpr(sc, "${", "}", ...,
            s -> parseSpringExpr0(sc, configVars));            // 每个 ${} 都往同一 list 追加

IBeanPropValueResolver parseSpringExpr0(TextScanner sc, List<String> configVars) {
    String configVar = sc.nextConfigVar();
    configVars.add(configVar);                                  // 累积，不清除
    ...
    return new ConfigValueResolver(sc.location(), false, configVars, true, null); // 传引用
```
- **现状**: `${a}:${b}` 解析后，第二个占位符的 resolver 持有 `[a, b]`（而非 `[b]`）。`ConfigValueResolver.resolveValue`（已核实，70-84 行）按序返回第一个非空值——若 `a` 已配置，第二个占位符也解析为 `a` 的值，注入结果为 `aVal:aVal`。同时 `collectConfigVars` 会为第二个 resolver 多登记 `a` 的订阅。
- **风险**: 一旦某属性值写两个及以上 `${}` 占位（Spring 风格移植配置很常见），得到静默的错误注入值。当前仓库 beans.xml 无此写法（grep 0 命中），属潜伏的正确性炸弹；单占位 + 嵌套默认值 `${a:${b}}` 的内层 resolver 同样被外层变量污染。
- **建议**: `parseSpringExpr0` 中为每个占位符构造独立的单元素列表（`Collections.singletonList(configVar)`），仅 `@cfg:a,b` 语法保留多元素语义。
- **误报排除**: 已读 `ConfigValueResolver.resolveValue` 确认按序取第一个非空；已读 `parseExpr` 确认同一 exprParser 闭包对表达式内每个 `${}` 各调用一次；已确认 `@cfg:a,b` 的多变量 fallback 语义来自 `parsePrefixExpr`（构造时拆分），与本 bug 无关。

### [P1] ConfigStarter.getProfiles 用 CFG_PROFILE.get()（变量值）当变量名查询，application.yaml 中的 nop.profile 被静默忽略

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/starter/ConfigStarter.java:283-285`
- **维度**: D1
- **证据**:
```java
String profile = appSource.getConfigValue(CFG_PROFILE.get(), null);   // get() 返回当前值（如 "dev"/null）
if (StringHelper.isEmpty(profile))
    profile = baseSource.getConfigValue(CFG_PROFILE.getName(), "");   // 下一行却正确使用 getName()
...
Set<String> profileParent = ConvertHelper.toCsvSet(appSource.getConfigValue(CFG_PROFILE_PARENT.getName())); // 同方法内同类调用也用 getName()
```
- **现状**: `CFG_PROFILE` 是 `IConfigReference<String>`（已核实 ApiConfigs.java:24），`.get()` 返回 "nop.profile" 的**当前值**（来自 env/props/bootstrap），不是变量名。该值被当作变量名在 appSource（application.yaml）中查找：为 null 时 `HashMap.get(null)` 安全返回 null；为 "dev" 时查找名为 "dev" 的配置几乎必为 null——随后走 baseSource 回退。净效果：**application.yaml 中定义的 `nop.profile` 永远不生效**，且若恰好存在与 profile 值同名的配置项会被误用。
- **风险**: 按 Spring 习惯把 profile 写进 application.yaml 的用户会得到无 profile 的静默降级（`%dev.xxx` 配置不激活、application-dev.yaml 不加载），产生错误配置生效等数据级后果。
- **建议**: 改为 `appSource.getConfigValue(CFG_PROFILE.getName(), null)`。
- **误报排除**: 已核实 `IConfigReference.get()` 语义（`LogConfigs.CFG_LOG_LEVEL.get()` 在同文件 490 行取级别值）；已核实 `IConfigSource.getConfigValue(null, null)` 对 HashMap 实现 safety（CompositeConfigSource/StaticConfigSource 均为 HashMap，无 NPE，故是语义错误而非崩溃）；同方法 287 行 `CFG_PROFILE_PARENT.getName()` 的正确用法证明 283 行是笔误而非设计。

### [P2] BeanConditionEvaluator.dumpDisabled 复制粘贴错误：missing-class 分支遍历 getOnClass()，debug 模式下 NPE

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanConditionEvaluator.java:455-461`
- **维度**: D1
- **证据**:
```java
if (conditionModel.getMissingClass() != null) {
    for (String className : conditionModel.getOnClass()) {   // 应为 getMissingClass()
        if (!isMissingClass(className)) {
            sb.append("\n    check-missing-class-fail:").append(className);
            break;
        }
    }
}
```
- **现状**: 当 bean 仅配置 `ioc:condition` 的 `missing-class`（无 `on-class`）且被禁用、且 `LOG.isDebugEnabled()` 时，`getOnClass()` 返回 null，for 循环 NPE，`evaluate()` 失败导致整个容器构建中断。
- **风险**: 开发环境（nop 默认 debug 居多）下使用 missing-class 条件的模块无法启动，且 NPE 无配置上下文，排障成本高。
- **建议**: 遍历 `conditionModel.getMissingClass()`。
- **误报排除**: 已通读 `dumpConditional/dumpDisabled` 全部调用点：`dumpConditional` 有 `isDebugEnabled` 守卫，但一旦进入即执行该分支；已确认 `BeanConditionModel`（model/_gen 之外的手写类无额外填充逻辑）中 onClass 与 missingClass 是独立可空集合。

### [P2] `<set>` 未指定 set-class 时默认 ArrayList，SetValueResolver 强转 Set 必抛 CCE

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanDefinitionBuilder.java:811` 与 `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/resolvers/SetValueResolver.java:47`
- **维度**: D1（错误默认值）
- **证据**:
```java
// BeanDefinitionBuilder.buildResolver:
Class<?> type = model.getSetClass() == null ? ArrayList.class      // Set 语义默认给 List 实现
        : loadBeanClass(bean, model.getLocation(), model.getSetClass());
return new SetValueResolver(type, items, model.isIocExcludeNull());

// SetValueResolver.resolveValue:
Set<Object> ret = (Set<Object>) ClassHelper.newInstance(type);     // ArrayList instanceof Set == false
```
- **现状**: `ClassHelper.newInstance` 返回 Object（已核实签名），`(Set<Object>)` 强转在运行时执行 checkcast，ArrayList 不是 Set，`<set>` / `util:set` 未写 set-class 时第一次解析即 ClassCastException。
- **风险**: beans.xdef 的 set-class 无默认值（已核实 xdef 第 31 行无 default），当前仓库无 `<set>` 用例（grep 0 命中），属潜伏缺陷；一旦使用且省略 set-class 即崩溃。
- **建议**: 默认值改为 `LinkedHashSet.class`。
- **误报排除**: 已核实 `ClassHelper.newInstance(Class<?>)` 返回 Object（nop-commons ClassHelper.java:273）；已核实 javac 对该赋值生成 checkcast（返回类型 Object 到 Set 的向下转型）；对比同文件 List/Map 分支默认值（ArrayList/LinkedHashMap）语义正确，仅 set 分支错。

### [P2] AppBeanContainerLoader.getAppBeansFilter 多 include pattern 为 AND 语义，与同文件 auto-config 过滤器（OR）相反，可静默丢弃 beans 文件

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/AppBeanContainerLoader.java:235-241`（对照 `187-218`）
- **维度**: D1
- **证据**:
```java
// getAppBeansFilter（本方法）:
for (String pattern : patterns) {
    if (!StringHelper.matchSimplePattern(path, pattern))   // 任一 pattern 不匹配即 false => AND
        return false;
}

// getAutoConfigFilter（同文件姊妹方法）:
for (String pattern : patterns) {
    if (StringHelper.matchSimplePattern(name, pattern))
        return true;                                        // 任一匹配即 true => OR
}
return false;
```
- **现状**: 配置 `nop.ioc.app-beans-file.pattern=a*,b*` 时，只有同时匹配两个模式的资源才保留——通常一个都不剩，模块 beans 文件被静默跳过（每资源仅一条 LOG.info）。
- **风险**: 多模式配置下应用以空容器/缺 bean 状态启动，故障隐蔽。
- **建议**: 与 auto-config 过滤器对齐为 OR（任一 include 命中即保留）。
- **误报排除**: 已并排阅读两个过滤器全文确认语义分歧；已核实 `ConvertHelper.toCsvSet` 返回的多模式集合会完整进入循环；已核实 skip-pattern 逻辑两者一致，仅 include 分支相反。

### [P2] AbstractFileConfigSource 使用 Files.walk 未关闭流，定时刷新周期性泄漏目录句柄

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/source/file/AbstractFileConfigSource.java:80-88`
- **维度**: D2
- **证据**:
```java
}).flatMap(path -> {
    try {
        if (Files.isRegularFile(path))
            return Stream.of(path);
        return Files.walk(path).filter(Files::isRegularFile);   // Stream 未 close
    } catch (Exception e) {
        return Stream.empty();
    }
}).sorted(Path::compareTo).collect(Collectors.toList());
```
- **现状**: `Files.walk` 打开的目录流需要显式 close（JDK 文档要求 try-with-resources）；此处经 flatMap 消费后从不关闭，仅能等 GC 回收。KeyFileConfigSource/PropsFileConfigSource 按 refreshInterval 定时调用 `refreshConfig → loadConfig`，每个刷新周期泄漏一批目录句柄。
- **风险**: 长运行进程 FD 缓慢增长（Linux fd 耗尽 / Windows 目录锁），配置热刷新失效连带 `ERR_CONFIG_*` 更新中断。
- **建议**: `try (Stream<Path> s = Files.walk(path)) { return s.filter(Files::isRegularFile).collect(toList()).stream(); }` 或在 flatMap 内收集后返回普通流。
- **误报排除**: 已确认外层流来自 `List.stream()`（关闭无效果、不会级联关闭内部流）；已确认 collect 消费不会自动 close；已确认 refreshConfig 的 catch 只兜住异常不兜资源。

### [P2] ChangeSubscriptions.trigger 单个监听器异常中断其余监听器与后续配置传播

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/impl/ChangeSubscriptions.java:57-66`
- **维度**: D4
- **证据**:
```java
Set<IConfigChangeListener> triggered = new HashSet<>();
...
triggered.forEach(listener -> listener.onConfigChange(provider, oldValues)); // 无逐个 try/catch
```
- **现状**: 监听器来自 `BeanDefinition.subscribeConfigChange`（属性重绑定，内部 `prop.assignToObject` 可因类型转换抛异常）。任一监听器抛错，同轮其余 bean 的属性更新、refreshConfig 级联全部跳过；异常沿 `DefaultConfigProvider.applyChange → ConfigChangeApplier.applyChange` 落到 config executor 线程被吞，且 `shouldUpdate` 已复位，本轮变更永久丢失。
- **风险**: 配置热更新部分生效、部分静默丢失，系统进入不一致状态。
- **建议**: 逐监听器 try/catch 记录错误后继续（同仓 `DefinitionConfigProvider.notifyListeners`（nop-plugin）已是该写法，可直接对齐）。
- **误报排除**: 已读 `ConfigChangeApplier`（执行前先置 shouldUpdate=false，异常无重试路径）；已读 `DefinitionConfigProvider.notifyListeners` 对照确认平台其余位置均有隔离；已确认 `HashSet.forEach` 遇异常立即终止迭代。

### [P2] PluginManagerImpl.unloadPlugin 失败路径先关闭 classLoader 却保留 holder，插件残留为不可用状态

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java:145-164`
- **维度**: D2/D1
- **证据**:
```java
try {
    if (holder.plugin.isStateMachineAware()) {
        holder.plugin.unload();      // ACTIVATED 态抛 ERR_PLUGIN_NOT_DEACTIVATED
    } else {
        holder.plugin.stop();        // 非 aware jar 轨 stop 失败
    }
    plugins.remove(pluginId);        // 失败时未执行
    ...
} finally {
    IoHelper.safeCloseObject(holder.classLoader);   // 无论成败都关闭
}
```
- **现状**: unload/stop 抛错时，`plugins` map 中仍保留该 holder，但其 `PluginClassLoader` 已在 finally 中关闭。后续 `getPlugin` 返回的插件任何类加载操作（命令解析、服务调用）都会失败，且无法再次 unload（classloader 二次关闭无意义）或 loadPlugin（computeIfAbsent 命中旧 holder）。
- **风险**: jar 轨插件进入"僵尸"状态（注册表中存在但不可用），Windows 上还伴随句柄已释放导致的奇怪行为；恢复只能重启。
- **建议**: classLoader 关闭与 holder 移除保持同侧——失败路径不关闭 classLoader（保留可重试性），或失败时同样从 map 移除。
- **误报排除**: 已核实 `VfsPluginDefinition.unload()` 在 ACTIVATED 态抛 ERR_PLUGIN_NOT_DEACTIVATED（该异常路径现实存在）；已核实 VFS 轨 classLoader 为 null（safeCloseObject 对 null 安全，故主要影响 jar 轨）；已确认 `loadPluginFromJar` 用 `computeIfAbsent`，残留 holder 会阻断重新加载。

### [P2] KeySetHelper 硬编码 RSA 公钥指数 "AQAB" 且模数编码可能带前导零字节，生成不合规 JWKS

- **文件**: `nop-core-framework/nop-security/src/main/java/io/nop/security/key/KeySetHelper.java:34-37`
- **维度**: D1/D8
- **证据**:
```java
String n = Base64.getUrlEncoder().encodeToString(rsaKey.getModulus().toByteArray());
key.setOtherClaim(SecurityConstants.RSA_PROP_MODULUS, n);
key.setOtherClaim(SecurityConstants.RSA_PROP_EXPONENT, "AQAB"); // 对于RSA公钥，"e"字段通常是固定的
```
- **现状**: ① 指数未取 `rsaKey.getPublicExponent()`，非 65537 指数的证书（合法存在）发布的 JWKS 指数错误，验签方构造出错误公钥；② `BigInteger.toByteArray()` 对正数可能带前导零字节（2048 位模数常见 257 字节），RFC 7518 §6.3.1.1 要求 minimum octets 编码，严格 JWKS 客户端会拒绝或得到错误 key。
- **风险**: 对外发布的公钥集与真实密钥不一致，外部系统验签失败（或被合规校验拒绝），属跨系统契约漂移。
- **建议**: 指数用 `Base64.getUrlEncoder().encodeToString(rsaKey.getPublicExponent().toByteArray())`；模数用最小字节编码（剥离前导零）。
- **误报排除**: 已核实 `RSAPublicKey` 接口提供 `getPublicExponent()`；已核实内部回环 `SecurityHelper.toRSAPublicKey` 用 `new BigInteger(1, bytes)` 容忍前导零，故仅外部严格消费者受影响——但该方法本身就是对外 JWKS 发布通道。

### [P2] Log4j2Configurator.changeLogLevel 依赖 getLoggerConfig 最近祖先回退，可能改掉父级/根 logger 级别

- **文件**: `nop-core-framework/nop-log/nop-log-log4j2/src/main/java/io/nop/log/log4j2/Log4j2Configurator.java:49-60`
- **维度**: D1/D8
- **证据**:
```java
LoggerConfig logger = loggerContext.getConfiguration().getLoggerConfig(loggerName);
if (logger == null) { ... root 兜底 ... }
if (logger != null) {
    logger.setLevel(level);
} else {
    loggerContext.getConfiguration().addLogger(loggerName, new NopLoggerConfig(loggerName, level, true));
}
```
- **现状**: log4j2 `Configuration.getLoggerConfig(name)` 对无显式配置的 name 返回**最近祖先**的 LoggerConfig（最终回退 root），几乎永不返回 null。因此对无独立配置的 logger 调整级别时，实际修改的是其父级/root 的配置——`logger == null → addLogger` 分支基本不可达。
- **风险**: 运行期动态调整某 logger 级别（如运维接口）会放大/收紧整棵子树的日志级别，日志量失控或关键日志被吞。
- **建议**: 先 `getConfiguration().getLoggers().containsKey(loggerName)` 判断，未命中即走 `addLogger` 新建，不改祖先。
- **误报排除**: 已读全方法与 NopLoggerConfig 定义；确认前置 `getLogLevel(loggerName) == logLevel` 短路用的是 slf4j 有效级别（经由祖先继承），无法阻止错误下钻；对照 LogbackConfigurator（直接 `context.getLogger(name).setLevel`，logback 会自动建独立 logger）无此问题。

### [P2] prototype 作用域 bean 配置 ioc:init / ioc:destroy xpl 时 runXpl 对 null scope NPE

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanDefinition.java:490-499`（调用点 `636`、`703`）
- **维度**: D1
- **证据**:
```java
void runXpl(IEvalAction xpl, Object bean, IBeanContainer container, IBeanScope beanScope) {
    if (xpl != null) {
        IEvalScope scope = beanScope.getEvalScope().newChildScope();  // prototype 时 beanScope == null
        ...
    }
}
```
- **现状**: prototype bean 经 `getBean0` 得到 `beanScope == null`（`isPrototype()` 分支），`createInstance` 捕获该 null 后，initBean→`runXpl(getIocInit(), ...)` 与 `destroyBean`→`runXpl(getIocDestroy(), ...)` 在配置了 xpl 时直接 NPE（init 时抛出；destroy 时被 BeanScopeImpl/容器路径记录）。
- **风险**: prototype + ioc:init 组合（合法 DSL 配置）运行期崩溃；错误信息为裸 NPE，无配置定位。
- **建议**: `runXpl` 对 null scope 降级使用 `DisabledEvalScope.INSTANCE.newChildScope()` 或显式抛出带 bean 定位的配置错误。
- **误报排除**: 已核实 `BeanContainerImpl.getBeanScope` 对 prototype 返回 null 且 `getBean0` 以 null scope 调 `createInstance`；已核实 `BeanScopeImpl.close/destroyBean` 链路同样把 null 传入 `destroyBean`；`initProps`（injectTo 路径）传 null scope 但其 runXpl 同样受影响（外部注入 bean + ioc:init 也会 NPE）。

### [P2] ServiceProxy 不解包 InvocationTargetException，业务异常语义丢失；equals/hashCode 未特判

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/ServiceProxy.java:63-71`
- **维度**: D4/D8
- **证据**:
```java
@Override
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    PluginScopeImpl scope = (PluginScopeImpl) plugin.getScope();
    if (scope == null) { throw new NopException(ERR_PLUGIN_INACTIVE)...; }
    Object target = resolveTarget(scope);
    return method.invoke(target, args);   // 抛 InvocationTargetException 而非原始业务异常
}
```
- **现状**: 目标方法抛出的业务异常（如带 ErrorCode 的 NopException）被包成 `InvocationTargetException` 返回给调用方；上层若按异常类型/错误码处理（Nop 平台错误处理惯例）全部失效。另外 `equals/hashCode/toString` 走同一路径：`proxy.equals(x)` 实际执行 `target.equals(x)`（target 非 proxy 恒 false），与 JDK 代理惯例不符。
- **风险**: 插件服务调用方的异常分支、重试逻辑、错误码映射失效；代理对象放入集合时 equals 行为异常。
- **建议**: catch InvocationTargetException 后 `throw e.getTargetException()`；Object 方法（equals/hashCode/toString）特判处理。
- **误报排除**: 已读 Handler 全文确认无 unwrap；已对比平台内 `DelegateInvocationHandler`（直接委托无反射，无此问题）；已确认注释声明 equals/hashCode 与业务方法同规则属有意为之，但反射语义差异（target vs proxy）使其实际行为偏离声明。

### [P3] BeanContainerImpl.getClassIntrospection 懒初始化存在良性竞态（重复构建）

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:131-140`
- **维度**: D3
- **证据**:
```java
IBeanClassIntrospection introspection = classIntrospection;
if (introspection == null) {
    introspection = new DefaultBeanClassIntrospection(classLoader);
    classIntrospection = introspection;   // 无锁，并发首调可能覆盖彼此
}
```
- **现状**: 派生容器（buildNewInstance 未 setClassIntrospection 的路径）并发首次调用时可能各建一个实例并相互覆盖。`DefaultBeanClassIntrospection` 无状态（仅持 classLoader + SpringBeanSupport），覆盖无功能危害，代码注释已自知。
- **风险**: 极低——浪费少量对象；行为无差异。
- **建议**: 保持现状或改为局部变量+不回写字段（每次 new）。
- **误报排除**: 已读 `DefaultBeanClassIntrospection` 确认无可变共享状态；已确认主容器构建路径 `BeanContainerBuilder.build` 总是 setClassIntrospection，竞态仅存在于 `buildNewInstance` 后未设置的派生容器。

### [P3] PluginClassLoader 缺失 plugin.json 时默认 pluginClassName 指向自身，loadPlugin 必抛 CCE

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/classloader/PluginClassLoader.java:82-86`
- **维度**: D1/D4
- **证据**:
```java
if (url == null) {
    PluginConfig config = new PluginConfig();
    config.setPluginClassName(getClass().getName());   // = "...PluginClassLoader"
    return config;
}
...
public IPlugin loadPlugin() {
    return (IPlugin) ClassHelper.newInstance(pluginConfig.getPluginClassName(), this);  // CCE
}
```
- **现状**: uber jar 无 plugin.json 时以 PluginClassLoader 自身作为插件类，`loadPlugin` 的 `(IPlugin)` 强转抛 ClassCastException，错误信息不指向"缺少 plugin.json"。
- **风险**: 排障体验差（裸 CCE）；无数据危害。
- **建议**: 缺失 plugin.json 时抛 `ERR_PLUGIN_NO_PLUGIN_CLASS_NAME`。
- **误报排除**: 已读 loadPluginConfig/loadPlugin 全文；已确认 `loadPluginFromJar` 直接调用 `loadPlugin()` 无预校验。

### [P3] StartupInfoLogger debug 模式全量打印 env/properties，掩码规则仅覆盖 password 类名称

- **文件**: `nop-core-framework/nop-boot/src/main/java/io/nop/boot/StartupInfoLogger.java:124-132`
- **维度**: D5
- **证据**:
```java
protected String encodeValue(String name, String value) {
    String lower = name.toLowerCase();
    if (lower.contains(".secret.") || lower.contains("userpass") || lower.contains("password")) {
        value = "***";
    } else if (value.startsWith(CommonConstants.SEC_VALUE_PREFIX)) { ... }
    return name + '=' + value;
}
```
- **现状**: `getEnvInfo` 仅在 `AppConfig.isDebugMode()` 时输出，但 TOKEN/SECRET/ACCESS_KEY/PRIVATE_KEY 等命名（不含 "password"）的凭据会明文进入日志。
- **风险**: 开发/联调环境日志泄露第三方 API 凭据。
- **建议**: 扩充掩码关键词（token、secret、key、credential 等）或采用白名单。
- **误报排除**: 已读 `logStarting`/`getEnvInfo` 确认 debug 守卫与全量遍历；已确认 `StringHelper.maskSecretVar`（config 模块）有更完整规则可复用，此处的规则是独立较弱的副本。

### [P3] ConfigModelLoader.merge 沿用错误码管理的日志文案（复制粘贴）

- **文件**: `nop-core-framework/nop-config/src/main/java/io/nop/config/model/ConfigModelLoader.java:63-67`
- **维度**: D3（可维护性）/D1 轻微
- **证据**:
```java
void merge(Map<String, ConfigVarModel> merged, String key, ConfigVarModel vl) {
    ConfigVarModel old = merged.put(key, vl);
    if (old != null) {
        LOG.info("nop.core.exceptions.override-error-code-mapping:key={},loc={},oldLoc={}", ...); // 文案为错误码覆盖
    }
}
```
- **现状**: config vars 合并冲突打出的日志标签是 "override-error-code-mapping"，误导排障。
- **风险**: 仅日志语义错误。
- **建议**: 改为 `nop.config.override-config-var`。
- **误报排除**: 已读该类全部 merge 重载，确认文案与功能（配置变量模型合并）不匹配。

### [P3] AppBeanContainerLoader.loadBeansFile 的 LOG.error 缺异常参数（仅占位符吻合，无堆栈）

- **文件**: `nop-core-framework/nop-config/../nop-ioc/src/main/java/io/nop/ioc/loader/AppBeanContainerLoader.java:120`
- **维度**: D4
- **证据**:
```java
} catch (Exception e) {
    LOG.error("nop.ioc.process-auto-config-fail:source={}", resource);  // e 未传入
    throw NopException.adapt(e);
}
```
- **现状**: 记录错误时不带异常对象（无堆栈）；随后已 rethrow 保留 cause，故信息未丢失，仅日志缺上下文。
- **风险**: 低。
- **建议**: `LOG.error("...:source={}", resource, e)`。
- **误报排除**: 已确认 catch 后立即 adapt 重抛，不属吞异常。

### [P3] GraphQLPluginCommand 整个文件被注释（死代码文件）

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/GraphQLPluginCommand.java:1-35`
- **维度**: D7（可维护性）
- **证据**:
```java
//package io.nop.plugin.support;
//
//import io.nop.api.core.beans.ApiRequest;
//... 全文件均为注释
```
- **现状**: 文件内全部代码被行注释，类名仍出现在类路径与文档语义中。
- **风险**: 误导检索；无运行时危害。
- **建议**: 删除该文件（git 历史可追溯）。
- **误报排除**: 已读文件全文确认无有效代码。

### [P3] BeanConditionEvaluator 条件求值不动点迭代硬编码 5 轮上限，深链条件被静默禁用

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanConditionEvaluator.java:92-96`
- **维度**: D1（边界条件）
- **证据**:
```java
for (int i = 0; i < 5; i++) {
    if (!processCandidates(this::checkBeanCondition) && !this.processAlias()) {
        break;
    }
}
if (!candidateBeans.isEmpty()) {
    ... LOG.warn("nop.ioc.candidate-check-fail:..."); bean.getCondition().setDisabled(true);
}
```
- **现状**: on-bean/missing-bean 条件链超过 5 轮传播时剩余候选被直接禁用，仅一条 warn 日志。
- **风险**: 大型模块组合下合法 bean 意外不装配，且只有日志可循。
- **建议**: 上限提高到与 bean 数相关，或对未收敛候选抛出明确配置错误（至少 warn 中带上轮次信息）。
- **误报排除**: 已读 `processCandidates/checkBeanCondition` 确认每轮最多推进一层依赖；已确认最终兜底是 setDisabled 而非报错。

### [P3] HttpPluginResourceResolver 以 Maven 坐标拼缓存路径，坐标分段未做字符白名单（纵深防御缺口）

- **文件**: `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/resolver/HttpPluginResourceResolver.java:102-103`
- **维度**: D5
- **证据**:
```java
String jarFilePath = coordinates.getJarFilePath();          // groupId.replace('.','/')+"/"+artifactId+"/"+version+"/..."
File jarFile = new File(cacheDir, jarFilePath);
```
- **现状**: `ArtifactCoordinates` 的 artifactId/version 分段无字符校验（`ArtifactCoordinates.getJarFilePath` 已核实为直接拼接）。当前唯一入口 `PluginManagerImpl.tryParseCoordinates` 已排除含 `/`、`\` 的 id，且带 `..` 的分段经路径合并后仍在 cacheDir 边缘内，故本单元内无可达的越界写；但 resolver 作为公共组件被其他调用方直接传入坐标时无防护。
- **风险**: 纵深防御层面存在把下载内容写到 cacheDir 外的潜在路径（需要绕过 manager 入口的前置条件）。
- **建议**: resolve 前校验分段 `^[A-Za-z0-9._-]+$`。
- **误报排除**: 已核实 `tryParseCoordinates` 的 `/`、`\` 过滤与 `ArtifactCoordinates.parse` 的分割规则，确认当前调用链不可达越界；评级因此为 P3 而非 P2。

### [P3] prototype 构造器自引用无环检测，无限递归至 StackOverflowError

- **文件**: `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/impl/BeanContainerImpl.java:391-393`
- **维度**: D1（边界条件）
- **证据**:
```java
if (beanScope == null) {                      // prototype：不做 markInCreation 环检测
    beanInstance = beanDef.createInstance(null, this, beanCtx);
    created = true;
}
```
- **现状**: `inCreationThread` 环检测仅在 singleton 的 `synchronized(beanDef)` 路径生效；prototype 构造参数注入自身（或 prototype 互引环）时每次注入都新建实例，无限递归至 SOE，而非 `ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE`。
- **风险**: 配置错误场景得到崩溃而非清晰错误；无数据危害。
- **建议**: prototype 路径同样使用 ThreadLocal 创建栈做环检测。
- **误报排除**: 已核实 `markInCreation` 仅在 singleton 分支调用；已确认拓扑排序的静态 depends 检测不覆盖构造器 ref（collectDepends 只影响排序，allow-cycle 默认 true）。

## 附注（非缺陷，审计过程中的正面确认）

- D7 平台规范：范围内全部 `@Inject` 均为 setter 注入（PluginManagerImpl/HttpPluginResourceResolver），无私有字段注入；Spring `@Value` 仅出现在 `SpringBeanSupport` 兼容桥（按设计识别 Spring 注解，非违规使用）。
- D4 基线：范围内无 bare `new RuntimeException`、无空 catch 块；错误码 + NopException + `.param(...)` 惯例执行良好。
- `BeanContainerImpl`/`ProducedBeanInstance` 的并发纪律（容器锁外执行用户回调、runUntil 单 owner 推进、markPropSetFailed 防等待者挂死、BeanScopeImpl add/close 互斥）经逐路径复核未发现可触发的不变量破坏。
- `HttpPluginResourceResolver` 的 SHA256 校验链（下载后校验、缓存重验、无 hash 显式失败）实现完整，信任模型在 Javadoc 中如实声明。
- `PluginManagerImpl` reconcile（不动点 + 拓扑批量 + 环检测 + 失败阈值熔断）与 `VfsPluginDefinition` 六态状态机经状态迁移逐条核对，未见非法迁移。
