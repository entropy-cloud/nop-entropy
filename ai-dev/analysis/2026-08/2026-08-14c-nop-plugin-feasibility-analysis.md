# Nop Plugin 系统可行性分析：改 IoC / 用 IoC / 新入口？

> Status: open
> Date: 2026-08-14
> Scope: 评估在 Nop 可逆计算基础上实现 Cordis 式 plugin 系统的三条实现路径
> Conclusion: **不需要改 IoC**。现有 `BeanContainerImpl` 已具备 plugin 系统所需的全部运行时机制（父子容器 + `stop()`→自动 destroy 全部 bean）。**路径 B 已由 `nop-plugin` 模块实现**（`IPluginManager`/`AbstractPlugin`/`IPluginCancelToken`），采用"plugin = 子容器、卸载 = stop()"模式——本文初稿"需新建编排层"的判断已修正。真正差距是**增强**级别：effect 跟踪系统化（当前 `appendOnCancel` 是手动回调，非 Cordis 式 `ctx.effect()` 自动）+ HMR（`getLastChangeTime` 字段已有但无自动 reload）。

## Context

- 目标：在 Nop 可逆计算基础上叠加 plugin 系统，获得类似 Cordis 的运行时可逆副作用（load/unload/effect/disposer），同时保持结构层 Delta 的细粒度优势。
- 用户问题：**是要修改 IoC 吗？IoC 能解决这个问题吗？还是说要另外引入一个新的入口？**
- 判断依据：`nop-ioc` 源码（`BeanContainerImpl.java`、`BeanScopeImpl.java`、`IBeanScopeContext.java`、`beans.xdef`、`_BeansModel.java`）；Cordis 论文与 dsh 架构（见 `2026-08-14` / `2026-08-14b` 分析）。

## IoC 现状关键事实（调研确认）

| 事实 | 证据 | 对 plugin 的意义 |
|---|---|---|
| beans.xml 是完整 XDSL（节点级 Delta） | `_BeansModel extends AbstractDslModel`（`_BeansModel.java:17`）；`BeanContainerBuilder.java:103` 经 `DslModelParser` 解析 | plugin 定义可 Delta 定制，**节点级细粒度**（远优于 Cordis 配置行级） |
| 父子容器层级（全方法父容器回退） | `BeanContainerImpl.java:65` `parentContainer`；`:113` `buildNewInstance(parent)` | **plugin = 子容器**天然隔离 + 共享宿主 bean |
| `stop()` 自动 destroy 所有 singleton bean | `BeanContainerImpl.java:558-580` stop → `:570` `singletonScope.close()` → `BeanScopeImpl.java:92-99` 遍历 → `:77-80` `remove()` → `container.destroyBean()` → `:588-593` `beanDef.destroyBean()` | **卸载 plugin = stop() 子容器，自动回退全部 bean 副作用** |
| destroy 触发多种逆操作 | `BeanDefinition.java:641-655`：destroyMethod + `<ioc:destroy>` + `subscriptionChecks.cancel()` | destroy = disposer 等效物 |
| 自定义 scope | `IBeanScopeContext`；`BeanContainerImpl.java:399-409` 非 singleton/prototype 委托给 `BeanScopeContext` | 可建 per-plugin / per-task scope |
| **不支持运行时动态增删 bean** | `enabledBeans` 是 `final`（`:62`），无 register/unregister 公开 API | **不是缺陷**——保持容器不可变，符合可逆计算静态结构哲学 |

## Nop 已有 nop-plugin 模块（对本文"需新建"判断的修正）

> **重要修正**：本文初稿假设需"新建编排层"，实际 Nop **已有** `nop-core-framework/nop-plugin`（api / manager / support 三子模块），且已完整实现下文路径 B 的"plugin = 子容器"模式。本文的路径判断（不改 IoC、子容器模式）仍然正确，只是"需新建 IPluginManager"是错的。

现有实现已验证本文核心论点：

| 能力 | 现有实现 | 证据 |
|---|---|---|
| load / unload | `IPluginManager.loadPlugin/unloadPlugin` | `PluginManagerImpl.java:45/64` |
| **plugin = 子容器** | `AbstractPlugin.doStart()` 调 `AppBeanContainerLoader.loadFromResource(id, resource, BeanContainer.instance())`——第三参为**父容器**（宿主） | `AbstractPlugin.java:118` |
| **卸载 = stop → destroy** | `doStop()` → `beanContainer.stop()` | `AbstractPlugin.java:97-105` |
| 类隔离 | `PluginClassLoader`（独立 ClassLoader） | `PluginManagerImpl.java:48` |
| 远程加载 | `HttpPluginResourceResolver`（下载 uber jar） | resolver 包 |
| cancel / disposer 雏形 | `IPluginCancelToken.appendOnCancel(callback)` | `IPluginCancelToken.java:16` |
| 结构层 Delta | plugin 的 beans.xml 是 XDSL（节点级） | `AbstractPlugin.java:116` + `DslModelParser` |
| 命令分发 | `invokeCommand` → `IPluginCommand` bean | `AbstractPlugin.java:138-155` |

完整的 load/unload 链路（已验证）：
```
loadPlugin:  resolveResource(远程下载) → PluginClassLoader(类隔离) → ServiceLoader → plugin.start()
               → AbstractPlugin.doStart() → loadFromResource(parent=宿主) → 子容器.start()
unloadPlugin: plugin.stop() → doStop() → beanContainer.stop() → singletonScope.close() → destroyBean(全部)
               → safeCloseObject(classLoader)
```

## 三条路径评估

### 路径 A：修改 IoC（加动态 register/unregister + effect 跟踪）

- 做法：给 `BeanContainerImpl` 增加运行时 `registerBean`/`unregisterBean`，把 `enabledBeans` 改为可变。
- **不推荐**：
  1. `nop-ioc` 是框架核心（Protected Area，plan-first），改动风险高。
  2. 破坏现有依赖解析（单例预解析、循环依赖检测、`<ioc:collect-beans>` 都在 build 时完成）；运行时动态增删会使这些不变量失效。
  3. **与可逆计算的静态结构哲学冲突**：容器变成运行时可变状态，难以 Delta 化、难以"结构/运行时分离"。可逆计算的 loader 输出的是**静态** BeansModel，容器应是它的实例化产物，而非运行时可变对象。

### 路径 B：不改 IoC + 新建轻量编排层（推荐）✅

- 做法：plugin = 子容器。`load` = 加载 plugin 的 beans.xml（Delta 合并）→ `buildNewInstance(parent)` 创建子容器 → `start()` 实例化；`unload` = `stop()`（自动 destroy 全部 bean）。
- **IoC 不改一行**，完全复用现有 `buildNewInstance` / `stop()` / 父子回退 / destroy。
- 新建一个**编排入口**（新模块 `nop-plugin` 或在 `nop-core` 加 `IPluginManager`），只做生命周期编排 + effect 聚合 + HMR。
- 粒度匹配：plugin 本身就是**粗粒度边界**（用户已指出），子容器级管理粒度正好匹配。

### 路径 C：完全独立新入口（不用 IoC 子容器）

- 做法：plugin 系统自建一套 scope/effect（类似 Cordis context），不依赖 IoC 子容器。
- **不推荐**：重复造轮子（IoC 已有 scope/destroy/父子容器），与 Nop IoC 割裂，两套生命周期难以协调。

## 推荐方案：路径 B 的核心机制

```
load(pluginPath):
  beans.xml(Delta 合并, 节点级)  ──loader 被动模式──▸  BeansModel(静态, 可缓存)
                                                         │ buildNewInstance(parent)
                                                         ▼
                                              子容器.start() → 实例化(注册 destroy/subscription)

unload(pluginId):
  子容器.stop() → singletonScope.close() → 遍历 bean → destroyBean()
       → @PreDestroy / <ioc:destroy> / subscription.cancel()  (自动 LIFO 回退)
       → quiescence (承认外部副作用不可逆, 等价非完全恢复)
```

**关键证据链（已验证）**：
- `BeanContainerImpl.stop()`（`:558`）→ `singletonScope.close()`（`:570`）
- `BeanScopeImpl.close()`（`:92-99`）遍历所有 `ProducedBeanInstance` → `remove()`（`:77-80`）
- `remove()` → `container.destroyBean()`（`:588-593`）→ `beanDef.destroyBean()`（`BeanDefinition.java:641-655`）
- 触发 destroyMethod + `<ioc:destroy>` XPL + `subscriptionChecks.cancel()`

**这正好是 Cordis revertible effects 的"运行时层"实现**——每个 bean 的 destroy/subscription-cancel 就是它的 disposer，`stop()` 聚合回退。区别是 Nop 的结构层（beans.xml Delta）是节点级细粒度，远优于 Cordis 的配置行级。

## 缺口分析（已有 vs 需增强）

| 需求 | nop-plugin 现状 | 需增强 |
|---|---|---|
| plugin 生命周期编排（load/unload） | ✅ `IPluginManager`/`PluginManagerImpl` | reload（HMR）尚缺 |
| effect 聚合为可观测列表 | △ `IPluginCancelToken.appendOnCancel`（手动回调） | 系统化：把 bean destroy + subscription cancel + appendOnCancel 统一为可观测 effect 列表（类似 Cordis `ctx.effect()`），便于 quiescence 验证 |
| HMR（beans.xml 修改 → 重载） | △ `getLastChangeTime` 字段已有（暗示设计意图），但 `PluginManagerImpl` 无自动 reload | 检测变更 → unload+load（利用 loader 依赖失效重算 BeansModel） |
| reactive coeffects | ❌ 无 | `<ioc:condition>` + `<ioc:collect-beans>` 已覆盖大部分，暂不需 |

**结论修正**：不是"新建"，是"**增强现有 nop-plugin**"。核心增强是 effect 系统化（自动跟踪）+ HMR（自动 reload）。

## 与 Cordis 对照（路径 B 实现后）

| 维度 | Cordis | Nop（路径 B） |
|---|---|---|
| 结构层粒度 | 配置行级（entry id，粗） | **节点级**（XDef，细） |
| 结构层逆元 | 无 remove/deep-merge | `x:override=remove/merge` |
| 运行时逆操作 | disposer（per-effect） | destroy + subscription cancel（per-bean，stop 聚合） |
| plugin 边界 | component/plugin | **子容器**（隔离 + 父容器回退共享） |
| 结构/运行时分离 | 耦合（boot 加载即激活） | **分离**（loader 输出静态 BeansModel，实例化独立） |
| 动态增删 | ✅ mount/unmount | 子容器 create/stop（plugin 粒度，非单 bean） |

## 风险

1. **子容器粒度较粗**：卸载是整个子容器 stop，无法只卸载单个 bean（但 plugin 本身是粗粒度边界，可接受）。
2. **HMR 代价**：子容器 stop+重建比 Cordis 的 effect 级 unmount 重（要重新实例化所有 bean）；但因结构层独立（BeansModel 已缓存重算），比 dsh 的"整树重激活"轻。
3. **bean 间共享状态**：子容器 bean 若修改了父容器共享对象，stop 不会回退这些修改（观测等价问题，与 Cordis 相同）。

## Conclusion

- **不需要改 IoC**。现有 `buildNewInstance(parent)` + `stop()`→`close()`→`destroyBean()` 链路已覆盖 plugin 所需的运行时可逆机制。
- **IoC 能解决**：通过"plugin = 子容器、卸载 = stop()"模式，自动回退全部 bean 副作用。
- **nop-plugin 模块已实现路径 B**（`IPluginManager`/`AbstractPlugin`/`IPluginCancelToken`），不改 IoC。差距在**增强**：effect 系统化（当前 `appendOnCancel` 手动，非 Cordis `ctx.effect()` 自动）+ HMR（字段已有、逻辑尚缺）。
- Nop plugin 已同时拥有 Cordis 的运行时可逆（子容器 stop）**和**可逆计算的节点级结构 Delta（beans.xml XDSL）——双层都可逆，且结构层比 Cordis 细。
- 后续：若推进增强，写 `ai-dev/design/nop-plugin/`（effect 系统化方案、HMR 流程），再拆 plan。

## Open Questions

- [ ] `IPluginManager` 放新模块 `nop-plugin` 还是挂 `nop-core`？是否与现有 `_module` 模块发现机制整合？
- [ ] effect 聚合是显式 `scope.effect(disposer)` 还是自动从 bean destroy 推导？显式更可控，自动更省事。
- [ ] HMR 是否需要"增量实例化"（只重建变化的 bean），还是接受子容器整体重建？前者复杂、后者简单。
- [ ] 是否需要 Cordis 式 reactive coeffects（依赖声明/响应式解析），还是 `<ioc:condition>` + `<ioc:collect-beans>` 已够？

## References

- `nop-ioc/.../impl/BeanContainerImpl.java`（`:62` enabledBeans final、`:113` buildNewInstance、`:399-409` getBeanScope、`:558-580` stop、`:588-593` destroyBean）
- `nop-ioc/.../impl/BeanScopeImpl.java`（`:77-80` remove→destroyBean、`:92-99` close 遍历）
- `nop-ioc/.../impl/BeanDefinition.java`（`:641-655` destroyBean 触发 destroyMethod + ioc:destroy + subscription cancel）
- `nop-ioc/.../loader/BeanContainerBuilder.java`（`:103` DslModelParser 解析 beans.xml）
- `nop-xdefs/.../_vfs/nop/schema/beans.xdef`（XDSL 定义，支持 x:extends/ioc:condition/allow-override）
- 关联分析：`ai-dev/analysis/2026-08/2026-08-14-deepseek-harness-vs-reversible-comparison.md`、`2026-08-14b-cordis-paper-analysis.md`
