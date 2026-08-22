# nop-plugin — 可插拔组件框架（插件定义 + 单层六态状态机 + 插件级 coeffect + HMR）

## 功能概览

nop-plugin 提供完整的插件框架：插件定义（XDSL `*.plugin.xml` / uber jar 双轨来源）、单层六态状态机（一个定义至多一个激活）、插件级条件激活（coeffect）、强类型服务访问（激活态绑定代理）、定义级命令路由、热重载（HMR）与 artifact 下载完整性校验（SHA256）。

> 2026-08-22 定位反转（R1-R4 重构）：plugin 收敛为"粗粒度引入 + 激活门控"，运行时派生机制（实例身份、实例级配置视图、父子层级级联）已删除。定位声明：凡进入 plugin 层的内容均须加载期可静态声明，运行时变化收敛为激活态迁移 + effect 登记的可逆资源。

模块结构（`nop-core-framework/nop-plugin/`）：

| 子模块 | 职责 |
|--------|------|
| `nop-plugin-api` | 插件实现者契约（`IPlugin`/`IPluginScope`/`IPluginActivator`/`Disposable`/`PluginState`）+ `plugin.xdef`。**零依赖**（不引用 `io.nop.ioc`/`io.nop.xlang`） |
| `nop-plugin-manager` | 框架实现：双轨加载、单激活生命周期编排、coeffect/reconcile、HMR、`HttpPluginResourceResolver`（artifact 下载 + SHA256 校验） |
| `nop-plugin-support` | `AbstractPlugin` 基类（jar 轨 aware/兼容双路径实现） |

## 核心概念与生命周期

### 单层六态状态机

插件生命周期是**一条单层状态机**：定义（load/unload）与激活（activate/deactivate）在同一 `IPlugin` 对象上演进，**一个定义至多一个激活**：

```
UNLOADED ──load──▶ LOADED ──activate(门控满足)──▶ ACTIVATING ──▶ ACTIVATED
   ▲                  │  ▲                            │              │
   │                  │  └── activate 失败 ──▶ FAILED │              │ deactivate
   └── unload ────────┘        FAILED ──(可重试 activate)──▶ …      ▼
                     └── unload ◀── LOADED ◀── DEACTIVATING ◀───────┘
```

| 状态 | 静态定义 | 内部子容器 | bean 实例 | effect |
|------|---------|-----------|----------|--------|
| UNLOADED | ✗ | ✗ | ✗ | ✗ |
| LOADED | ✓（可缓存，loader 被动失效） | ✗ | ✗ | ✗ |
| ACTIVATING | ✓ | build 中 | 部分 | 注册中 |
| ACTIVATED | ✓ | ✓（started） | ✓ | ✓（已注册） |
| DEACTIVATING | ✓ | stop 中 | destroy 中 | 回退中 |
| FAILED | ✓ | ✗（已清理） | ✗ | ✗（已回退） |

关键语义：

- `loadPlugin(pluginId)` 只把定义加载到 **LOADED**（解析 + 校验），**不激活**；reconcile 按门控决定是否自动激活。
- `activate()`：门控满足时建子容器 + 执行 activator + 注册 effect，返回 `true`；门控未满足 **no-op 返回 `false`**（不抛异常）；已 ACTIVATED **幂等返回 `true`**（并发重复经 in-flight 单飞收敛，不重跑）。同步返回 boolean 是有意设计（激活资源建立为同步操作，展开窗口短）。
- `deactivate()` 返回 `CompletionStage<Void>`：先 `scope.close()`（LIFO 回退全部 effect）再子容器 stop，回到 **LOADED（定义保留）**；异步 effect 回退故为异步返回。
- **unload 守卫**：ACTIVATED/中间态（ACTIVATING/DEACTIVATING）时 `unload()`/`unloadPlugin()` 抛 `ERR_PLUGIN_NOT_DEACTIVATED`（须先 deactivate）。
- **FAILED 可重试**：激活失败置 FAILED（资源已回退清理、`lastActivationError` 可读、原始异常保留为 cause），显式 `activatePlugin` 可恢复尝试；FAILED 态允许 unload。
- **失败阈值暂停**：连续自动激活失败超阈值（5 次）后 reconcile 暂停该插件的自动激活（显式 activatePlugin 仍可恢复）。

### 兼容路径（isStateMachineAware 双路径）

`IPlugin.isStateMachineAware()` 默认 `false`——存量第三方插件（只有 `start/stop`）零感知、不进入新状态机：

- **非 aware**：`loadPlugin` 执行旧 `start` 语义（无"已加载未激活"态），`unloadPlugin` 执行旧 `stop` 语义；状态机新方法（load/activate/getService 等）的 default 实现显式抛 `ERR_PLUGIN_LIFECYCLE_NOT_SUPPORTED`（实现层对非 aware 插件永不调用，No Silent No-Op）。
- **aware**（`AbstractPlugin` 子类或 `VfsPluginDefinition`）：`start` = `load + activate`、`stop` = `deactivate + unload`，状态边界两轨统一规格：仅 UNLOADED 补 load 步（已 LOADED/FAILED 不重复 load、updateConfig 累积值保留）；已 ACTIVATED 时 start 幂等 no-op（不重跑 activator、scope/容器不重建）；UNLOADED 态 stop 幂等 no-op。

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
| `name` | 插件名（必填 `!string`，定义 id） |
| `requires` | 依赖的插件名集合（csv-set，空格分隔）——所列插件均已 ACTIVATED 才开门控 |
| `if-property` | 激活条件：`propName\|expectedValue`（缺省 expectedValue 视为 `true`），如 `agent.tools.enabled\|true` |
| `activator` | 激活器 bean id（bean-name，见下文 activator 模式） |
| `<beans>` | 唯一子元素。插件内部 bean 定义（复用 beans.xdef，`primary` 用于 getService 多候选规则） |

**属性集冻结（R3 裁决）**：不新增 `requires-service` 等服务级条件属性（扩展点已关闭）；属性集由机器守护测试 `TestPluginXdef#testAttributeSetFrozen`（manager 测试树）锁定，偏差即测试失败。

## 插件级 coeffect 条件激活

coeffect = **插件级**条件评估，运行时动态激活/去激活（`<ioc:condition>` 是 build 时一次性决定，coeffect 是运行时可反复）：

- `requires`：所列插件名的**定义已 ACTIVATED**（依赖仅 LOADED 不满足——`TestReconcileTopologicalOrder#testRequiresEvaluatesDependencyActivationNotLoaded` 断言语义）。
- `if-property`：全局配置项匹配，**宽松比较**（数值宽松等价 Integer 10/Double 10.0/"10" 互通；字符串精确；布尔 Boolean.TRUE/"true" 互通）。
- 显式 `activatePlugin` 门控不满足 → no-op 返回 `false`（不抛异常）。
- `reconcilePlugins()` 迭代评估全部 LOADED 插件到不动点，编排规则：
  - **批量激活按正拓扑序**（提供者先激活——同 pass 先激活的提供者即满足消费者 requires 腿）；
  - **批量去激活按逆拓扑序**（消费者先于提供者退出，含级联闭包：提供者失效 → 消费者随之入组）；
  - **同 pass 混合批次先去激活组后激活组**；
  - 激活窗口**时间静止**：activate 展开期间条件失效不打断本次激活（完成后收敛去激活）；deactivate 展开期间条件恢复（回退完成后重激活）；
  - 静态依赖图 DFS 环检测（环成员强制门控关闭并报告 unresolved）；失败阈值暂停自动激活。
- reconcile **不自动 load**（未加载定义保持 UNLOADED，load 是显式调用）；实现层订阅配置变更（`subscribeChange`）自动触发 reconcile，API 层只暴露显式 `reconcile()`。

## 定义级配置域与 activator 参数传递模式

定义级配置域 = `loadPlugin(id, config)` 传入的初始 config + `updateConfig(config)` 的累积合并视图（R1 反转后唯一配置域；不写全局 `AppConfig`、全局无污染）：

- `updateConfig`：LOADED 时缓存待下次激活应用；ACTIVATED 时热应用（合并视图重算 + 经 provider 变更通知传播，bean 属性真实重绑定）。
- bean 属性 `${var}` 占位符从插件的 `DefinitionConfigProvider` 解析：定义级合并视图命中 / 无定义级值的键回落全局配置。

定义声明 `activator="beanId"`，激活时实现层实例化子容器后调用 **`activate(scope, config)`** 双参数（scope + 定义级配置视图作为参数传入，禁止字段注入 scope）：

```java
@FunctionalInterface
public interface IPluginActivator {
    Disposable activate(IPluginScope scope, Map<String, Object> config);
}
```

- `scope.getService(Class)` 激活期取 bean（返回真实 bean 非代理）；`scope.effect(Disposable)` 注册可逆操作（deactivate 时 **LIFO 回退**，`effects()` 清空 = quiescence 可断言）。
- **返回值非 null 自动注册为本次插件激活的 effect**（便捷模式 `return () -> cleanup`）。
- 重激活语义：deactivate 后再次 activate 重新执行 activator（scope 已 close，effect 需重新注册；close 后再注册抛异常、重复 close 幂等）。

## getService 激活态绑定代理

`plugin.getService(Class<T>)` 返回**激活态绑定代理**（仅支持接口类型，具体类抛 `ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE`）：

- **ACTIVATED**：路由到实现；**deactivate 完成后调用快速失败**（抛 `ERR_PLUGIN_INACTIVE`，不悬空、不静默返回 null）；**重新激活后同一代理引用恢复可用**（绑定对象 = 插件激活态，按调用重新解析）。
- **多候选规则**：`primary="true"` 优先 → 无 primary 时按 bean id 与接口匹配的唯一实现 → 多候选且无 primary 抛 `ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES`（不静默返回集合）。
- `getServices(Class)` 返回全部实现的代理集合（重激活后按 bean id 恢复可用）。

## 定义级命令路由

`plugin.invokeCommand(command, args, fieldSelection, cancelToken)` 分发于**本插件激活容器**（单容器，无实例路由）：

- 未激活（非 ACTIVATED）调用抛 `ERR_PLUGIN_INACTIVE`（定义级状态检查）。
- **命令 bean 回退链**：插件容器命令 bean（`nopPluginCommand_{command}`）→ 宿主容器回退（宿主同名命令 bean，与 jar 轨行为一致）→ default 兜底 bean（`nopPluginCommand_default`，宿主注册，未知名命令不静默失败）——链终点无命中时显式抛错。

## jar 轨契约（uber jar）

jar 轨（Maven 坐标 id，双冒号格式）无 plugin.xdef/VFS 定义载体（发现机制 = uber jar 内 plugin.json 指定实现类 + 反射实例化，非 ServiceLoader）：

- **门控恒为空集 → 无条件激活**（requires/if-property 不评估）；activator 未声明则**跳过激活回调**（子容器启动即完成激活）。
- `AbstractPlugin.load()` 容忍 xdef 载体缺失（约定路径无定义文件时空定义加载成功；有载体时解析持有仅作元数据，不驱动门控）。
- **documented behavior（两轨固有差异）**：aware `start(gav, config)` 在插件已 LOADED/ACTIVATED 时**忽略 config 参数**——start 仅在 UNLOADED 态补 load 步（config 经 `load(config)` 进入配置域），已 LOADED/ACTIVATED 时不重复 load、config 不被消费（jar 轨 aware 无定义级配置域语义，activate 不接受 config）。
- jar 轨 `reloadPlugin` 显式抛 `ERR_PLUGIN_RELOAD_NOT_SUPPORTED`（HMR 面向本地/开发场景，uber jar 不可编辑）。

## artifact 加载（uber jar 轨 + SHA256 校验）

`loadPlugin("groupId:artifactId:version")` 走 uber jar 轨：`IPluginResourceResolver.resolvePluginResource(coords)` → 下载到本地缓存 → SHA256 校验 → `PluginClassLoader` 从已校验 jar 加载。

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

框架核心**不起轮询线程**——提供显式检查入口 `PluginManagerImpl.checkChangedAndReload()`：遍历 VFS 轨已加载定义（仅跳过 UNLOADED，ACTIVATED/中间态定义进入变更检测），用 `ResourceComponentManager.checkChanged`（资源真实 lastModified 严格比对）检测 → 有变更调 `reloadPlugin`。宿主应用**定时调用**接线方式：

```java
// 宿主调度（如 nop-job / 定时线程）：
ScheduledExecutorService executor = ...;
executor.scheduleWithFixedDelay(manager::checkChangedAndReload, 0, 5, TimeUnit.SECONDS);
```

`reloadPlugin(pluginId)` 编排：若激活态先 `deactivate()`（回退 effect）→ `unload()`（丢弃旧定义）→ `load()`（重解析 plugin.xml，**重放定义级 updateConfig 累积值**）→ `reconcile()`（重新门控激活）。

注意：`checkChangedAndReload` 是 `PluginManagerImpl` 的**实现层方法**（非 `IPluginManager` 接口方法）；jar 轨 `reloadPlugin` 显式抛 `ERR_PLUGIN_RELOAD_NOT_SUPPORTED`。

## 源码锚点

| 组件 | 路径 |
|------|------|
| `IPlugin`（单层六态状态机 + 兼容 default 方法 + 定义级命令路由） | `nop-core-framework/nop-plugin/nop-plugin-api/src/main/java/io/nop/plugin/api/IPlugin.java` |
| `PluginState`（单层六态：UNLOADED/LOADED/ACTIVATING/ACTIVATED/DEACTIVATING/FAILED） | `nop-core-framework/nop-plugin/nop-plugin-api/src/main/java/io/nop/plugin/api/PluginState.java` |
| `IPluginScope`（effect/effects/close + 激活期 getService） | `nop-core-framework/nop-plugin/nop-plugin-api/src/main/java/io/nop/plugin/api/IPluginScope.java` |
| `IPluginActivator`（`activate(scope, config)` 双参数） | `nop-core-framework/nop-plugin/nop-plugin-api/src/main/java/io/nop/plugin/api/IPluginActivator.java` |
| `IPluginManager`（loadPlugin/unloadPlugin/activatePlugin/deactivatePlugin/getPlugin/getLoadedPlugins/reloadPlugin/reconcilePlugins） | `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/IPluginManager.java` |
| `PluginManagerImpl`（双轨路由 + reconcile 拓扑编排 + HMR + `checkChangedAndReload`） | `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java` |
| `VfsPluginDefinition`（VFS 轨单激活生命周期：子容器 + activator + effect + 失败阈值） | `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java` |
| `HttpPluginResourceResolver`（SHA256 校验 + expected-hash map） | `nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/resolver/HttpPluginResourceResolver.java` |
| `AbstractPlugin`（jar 轨 aware/兼容双路径基类） | `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java` |
| `plugin.xdef`（定义 schema：requires/if-property/activator/beans，属性集冻结） | `nop-core-framework/nop-plugin/nop-plugin-api/src/main/resources/_vfs/nop/schema/plugin/plugin.xdef` |

## 相关文档

- 实现锚点：`../04-reference/source-anchors.md`（`PLG-001` ~ `PLG-009`）
- 模块分组：`../01-repo-map/module-groups.md`（核心框架分组）
- 权威设计来源：仓库 ai-dev/design/nop-plugin/ 目录下文档（01-architecture-baseline 架构基线 + 05-artifact-loading-design artifact 加载缓存语义与 SHA256，平台内部文档）——按 docs-for-ai 边界规则不直接链接，需精确定位时从 `04-reference/source-anchors.md` 出发
