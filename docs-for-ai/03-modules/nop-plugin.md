# nop-plugin — 可插拔组件框架（plugin 定义 + 多实例 + coeffect + HMR）

## 功能概览

nop-plugin 提供完整的插件框架：插件定义（XDSL `*.plugin.xml` / uber jar 双轨来源）、定义级与实例级两层状态机、多实例隔离、条件激活（coeffect）、强类型服务访问、per-instance 命令路由、热重载（HMR）与 artifact 下载完整性校验（SHA256）。

模块结构（`nop-core-framework/nop-plugin/`）：

| 子模块 | 职责 |
|--------|------|
| `nop-plugin-api` | 插件实现者契约（`IPlugin`/`IPluginInstance`/`IPluginScope`/`IPluginActivator`/`Disposable`）+ `plugin.xdef`。**零依赖**（不引用 `io.nop.ioc`/`io.nop.xlang`） |
| `nop-plugin-manager` | 框架实现：双轨加载、实例生命周期、coeffect/reconcile、HMR、`HttpPluginResourceResolver`（artifact 下载 + SHA256 校验） |
| `nop-plugin-support` | `AbstractPlugin` 基类（兼容旧插件的 start/stop 收敛） |

## 核心概念与生命周期

### 两层状态机

插件有**定义级**（`PluginState`）与**实例级**（`InstanceState`）两层状态：

```
定义级:  UNLOADED ──loadPlugin──▶ LOADED ──unloadPlugin(须先 destroy 全部实例)──▶ UNLOADED
实例级:  createInstance ──▶ ACTIVATED ──deactivate──▶ DEACTIVATED ──activate──▶ ACTIVATED
                            └──────────destroy（移除实例）──────────┘
```

- `loadPlugin(pluginId)` 只把定义加载到 **LOADED**（解析 + 校验），**不激活**。
- `createInstance(pluginId, instanceKey, config, parent)` 为 LOADED 定义派生一个 **ACTIVATED** 实例；`destroyInstance` 移除实例（`destroy()` 级联销毁后代实例）。
- `deactivate()` 只回退内部资源（effect LIFO 回退、销毁子容器），**实例对象保留**，可重新 `activate()`。
- `unloadPlugin` 前必须先 destroy 全部实例（有实例抛 `ERR_PLUGIN_INSTANCES_NOT_EMPTY`）。

### 兼容路径（isStateMachineAware 双路径）

`IPlugin.isStateMachineAware()` 默认 `false`——存量第三方插件（只有 `start/stop`）不进入新状态机：

- 非 aware：`loadPlugin` 执行旧 `start` 语义（= load + 激活，无"已加载未激活"态），`unloadPlugin` 执行旧 `stop` 语义。
- aware（`AbstractPlugin` 子类或自实现）：进入定义级状态机；`start(config)` = `load + createInstance(默认 key "default")`，`stop()` = `destroyInstance + unload`。

## 插件定义（VFS 轨）

`*.plugin.xml` 位于 `_vfs` 下，经 `/nop/schema/plugin/plugin.xdef` 校验（`x:schema="/nop/schema/plugin/plugin.xdef"`）：

```xml
<plugin name="agent-tools"
        requires="model-provider"
        if-property="agent.tools.enabled|true"
        activator="agentToolsActivator"
        x:schema="/nop/schema/plugin/plugin.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <beans>
        <bean id="tool.bash" class="io.nop.plugin.test.BashTool" primary="true"/>
    </beans>
</plugin>
```

| 属性 | 含义 |
|------|------|
| `name` | 插件名（定义 id） |
| `requires` | 依赖的插件名集合（csv-set，空格分隔）——coeffect 依赖链 |
| `if-property` | 激活条件：`propName\|expectedValue`（缺省 expectedValue 视为 `true`），如 `agent.tools.enabled\|true` |
| `activator` | 激活器 bean id（见下文 activator 模式） |
| `<beans>` | 插件内部 bean 定义（复用 beans.xdef，`primary` 用于 getService 多候选规则） |

## 多实例（instanceKey / parent 层级 / 实例配置域）

- **instanceKey**：一个 LOADED 定义可派生 N 个独立实例（多租户/多 agent），每个实例持有独立的 scope / effect / 配置域；同 key 重复 `createInstance` 抛 `ERR_PLUGIN_INSTANCE_EXISTS`。
- **parent 层级**（subagent）：`createInstance(pluginId, key, config, parent)` 的 `parent` 为父实例（顶层传 `null`）。子容器 parent = 父实例容器，**服务查找沿链回退**（子 → 父 → … → 顶层；顶层 parentContainer 为 null，不扩展宿主）；destroy 父级联 destroy 子；配置层叠：子覆盖父、父独有键继承进子合并视图；父 DEACTIVATED 时禁止挂靠（`ERR_PLUGIN_PARENT_NOT_ACTIVATED`）。
- **实例配置域**：每个实例有独立 `IConfigProvider`，`getConfig()` 返回合并视图（定义默认 ← 实例配置覆盖，W6 起含父链层叠），**任何态可读**；不写全局 `AppConfig`。

## coeffect 条件激活

coeffect = 定义级 + 实例级条件评估，运行时动态激活/去激活：

- **定义级**：`requires`（依赖插件名，须存在 ACTIVATED 实例）+ `if-property`（全局配置，缺省回退 true）。
- **实例级**：`if-property` 按实例合并视图求值（实例配置优先、全局回退）。
- `createInstance` 时定义级条件不满足 → **no-op 返回 null**（门控；重复 key 检查先于门控）。
- `reconcileInstances()` 迭代评估全部定义/实例到不动点（自动级联激活/去激活，静态环检测报告 `unresolvedPluginIds`，激活失败超阈值暂停自动激活）；实现层配置订阅（`subscribeChange`）自动触发 reconcile，API 层只暴露显式 `reconcile()`。
- 条件翻转真实驱动实例状态翻转（destroy 父实例 → 子自动 DEACTIVATED；创建父实例 → 子自动 ACTIVATED）。

## activator 参数传递模式

定义声明 `activator="beanId"`，激活实例时实现层实例化子容器后调用 **`activate(scope, config)`** 双参数（scope + 合并视图 config 作为参数传入，禁止字段注入 scope）：

```java
@FunctionalInterface
public interface IPluginActivator {
    Disposable activate(IPluginScope scope, Map<String, Object> config);
}
```

- `scope.getService(Class)` 激活期取 bean；`scope.effect(Disposable)` 注册可逆操作（实例 deactivate/destroy 时 **LIFO 回退**，回退后可观测 quiescence）。
- **返回值非 null 自动注册为该实例的 effect**（与显式 `scope.effect(...)` 等价，便捷模式 `return () -> cleanup`）。
- 重激活语义：deactivate 后再次 activate 重新执行 activator（scope 已 close，effect 需重新注册）。

## getService 生命周期代理

`instance.getService(Class<T>)` 返回**生命周期绑定代理**（仅支持接口类型，具体类抛 `ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE`）：

- **ACTIVATED**：路由到实现；**deactivate/destroy 后调用快速失败**（抛 `ERR_PLUGIN_INACTIVE`，不悬空、不静默返回 null）。
- **多候选规则**：`primary="true"` 优先 → 无 primary 时按 bean id 与接口匹配的唯一实现 → 多候选且无 primary 抛 `ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES`（不静默返回集合）。
- `getServices(Class)` 返回全部实现的代理集合；`scope.getService`（activator 内用）返回真实 bean。
- 服务查找沿 parent 链回退（见多实例）；全链未命中 → 容器标准错误 `ERR_IOC_UNKNOWN_BEAN_FOR_TYPE`（透传，参数完整）。

## per-instance 命令路由

- `instance.invokeCommand(command, args, fieldSelection, cancelToken)` 路由到**本实例子容器**（多实例下命令隔离由此保证）。
- 定义级 `plugin.invokeCommand(...)` 兼容语义：仅实例数=1 时经该实例路由；多实例抛明确异常（须显式指定实例）；无 ACTIVATED 实例抛 `ERR_PLUGIN_INACTIVE`。

## artifact 加载（uber jar 轨 + SHA256 校验）

`loadPlugin("groupId:artifactId:version")`（Maven 坐标，双冒号格式）走 uber jar 轨：`IPluginResourceResolver.resolvePluginResource(coords)` → 下载到本地缓存 → SHA256 校验 → `PluginClassLoader` 从已校验 jar 加载。

**`HttpPluginResourceResolver` 配置（宿主应用注入）**：

```xml
<bean id="pluginHttpResolver" class="io.nop.plugin.manager.resolver.HttpPluginResourceResolver">
    <property name="cacheDir" value="@cfg:nop.plugin.cache-dir|/nop/plugin"/>
    <property name="pluginServiceUrl" value="@cfg:nop.plugin.service-url"/>
    <property name="skipCacheVerify" value="@cfg:nop.plugin.skip-cache-verify|false"/>
    <property name="httpClient" ref="nopHttpClient"/>
    <property name="expectedHashes">  <!-- 可选：配置预期 hash map -->
        <map>
            <entry key="io.nop.plugin.test:mock-plugin:1.0.0" value="<hex sha256>"/>
        </map>
    </property>
</bean>
```

- `nop.plugin.service-url` 模板变量：`{pluginGroupId}` / `{pluginArtifactId}` / `{pluginVersion}`（URI path 编码）。
- **SHA256 hash 来源优先级**：响应 header `X-Checksum-Sha256` → `{url}.sha256` 请求（响应体 trim + 首个空白分隔 token）→ `expectedHashes` map（key = `groupId:artifactId:version`）。
- **校验时机**：下载完成、move 之前（临时文件上重算 SHA256，大小写不敏感比对）；**通过才 move + 落盘 `{jar}.sha256`**（小写 hex）。
- **失败 fail-fast**：校验不匹配 → 删临时文件 + 抛 `ERR_PLUGIN_SHA256_MISMATCH`（含期望/实际 hash 参数），**绝不使用未通过校验的 jar**。
- **无 hash 来源 → 显式失败** `ERR_PLUGIN_CHECKSUM_NOT_AVAILABLE`（Javadoc 已声明完整性保证，不允许静默降级为无校验下载）——**部署方须提供 hash 源**（header / `.sha256` 文件 / 配置 map 之一）。
- **缓存语义**：命中后重算 hash 与 `.sha256` 比对（篡改 → fail-fast `ERR_PLUGIN_SHA256_MISMATCH`）；缓存 jar 但 `.sha256` 缺失（pre-W7 遗留）→ 重新下载并校验（不静默使用未校验缓存）。
- `nop.plugin.skip-cache-verify=true`：同时跳过缓存重验与遗留重下载（启动优化逃生门，声明式显式行为）。

## 变更检测接线（HMR）

框架核心**不起轮询线程**——提供显式检查入口 `PluginManagerImpl.checkChangedAndReload()`：遍历 VFS 轨 LOADED 定义，用 `ResourceComponentManager.checkChanged`（资源真实 lastModified 严格比对）检测 → 有变更调 `reloadPlugin`（快照采集 → destroy 全部 → unload → load 重解析 → 按快照重建 → reconcile）。宿主应用**定时调用**接线方式：

```java
// 宿主调度（如 nop-job / 定时线程）：
ScheduledExecutorService executor = ...;
executor.scheduleWithFixedDelay(manager::checkChangedAndReload, 0, 5, TimeUnit.SECONDS);
```

注意：`checkChangedAndReload` 是 `PluginManagerImpl` 的**实现层方法**（非 `IPluginManager` 接口方法）；jar 轨（uber jar 不可编辑）`reloadPlugin` 显式抛 `ERR_PLUGIN_RELOAD_NOT_SUPPORTED`。

## 源码锚点

| 组件 | 路径 |
|------|------|
| `IPlugin`（定义级状态机 + 兼容 default 方法） | `nop-core-framework/nop-plugin/nop-plugin-api/src/main/java/io/nop/plugin/api/IPlugin.java` |
| `IPluginInstance`（实例级状态机 + getService/命令路由） | `nop-core-framework/nop-plugin/nop-plugin-api/src/main/java/io/nop/plugin/api/IPluginInstance.java` |
| `IPluginScope`（effect/effects/close + 激活期 getService） | `nop-core-framework/nop-plugin/nop-plugin-api/src/main/java/io/nop/plugin/api/IPluginScope.java` |
| `IPluginActivator`（`activate(scope, config)` 双参数） | `nop-core-framework/nop-plugin/nop-plugin-api/src/main/java/io/nop/plugin/api/IPluginActivator.java` |
| `IPluginManager`（loadPlugin/createInstance/reconcile/reloadPlugin） | `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/IPluginManager.java` |
| `PluginManagerImpl`（双轨路由 + reconcile + HMR + `checkChangedAndReload`） | `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java` |
| `HttpPluginResourceResolver`（SHA256 校验 + expected-hash map） | `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/resolver/HttpPluginResourceResolver.java` |
| `AbstractPlugin`（兼容基类） | `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java` |
| `plugin.xdef`（定义 schema：requires/if-property/activator/beans） | `nop-core-framework/nop-plugin/nop-plugin-api/src/main/resources/_vfs/nop/schema/plugin/plugin.xdef` |

## 相关文档

- 实现锚点：`../04-reference/source-anchors.md`（`PLG-001` ~ `PLG-009`）
- 模块分组：`../01-repo-map/module-groups.md`（核心框架分组）
- 权威设计来源：仓库 ai-dev/design/nop-plugin/ 目录下文档（两态状态机/接口契约 + artifact 加载缓存语义与 SHA256，平台内部文档）——按 docs-for-ai 边界规则不直接链接，需精确定位时从 `04-reference/source-anchors.md` 出发
