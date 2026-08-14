# nop-plugin 与 dsh 接口设计详细对比

**日期**：2026-08-14
**范围**：dsh/Cordis 核心接口 vs nop-plugin 增强设计接口，逐项对比（接口名、方法、语义、对应关系、差异）
**状态**：active
**关联**：`01-architecture-baseline.md`（§七 接口契约）、`02-dsh-usage-coverage.md`（用法评估）、`03-coeffect-and-agent-example.md`（场景对照）

---

## 一、接口总览

| 概念 | dsh（TypeScript/Cordis） | nop-plugin（Java） |
|---|---|---|
| 插件定义 | 函数 `(ctx, config)` / 类 / 对象 `apply(ctx, config)` + `inject`/`Config` 元数据 | `IPlugin`（定义级：load/unload）+ `IPluginActivator`（激活入口） |
| 激活入口 | `apply(ctx, config)` —— ctx + config **参数传递** | `IPluginActivator.activate(IPluginScope scope, Map<String,Object> config)` —— scope + config **参数传递**（同构） |
| 实例（fiber） | `ctx.plugin(plugin, ...config)` → `Fiber` | `IPluginManager.createInstance(...)` → `IPluginInstance` |
| 可逆副作用 | `ctx.effect(execute, label)` → disposer | `IPluginScope.effect(Disposable)` |
| 服务访问 | `ctx.<key>`（弱类型 proxy） | `getService(Class<T>)`（强类型生命周期代理） |
| 配置 | `fiber.config`（校验后） | `instance.getConfig()`（实例配置域，合并视图） |
| 依赖声明 | `inject`（plugin 元数据，PENDING 直到就绪） | coeffect spec（定义级 + 实例级） |
| 作用域 | `createScope(ctx, key)` / `agent.ctx` | 实例隔离（IPluginInstance 独立 scope/effect/配置域） |
| 生命周期 | `fiber.dispose()/restart()/update()` | `instance.destroy()/activate()/deactivate()` |
| 事件 | `ctx.on/emit/waterfall/parallel/serial` | **独立基础设施**（事件总线，不属 plugin 范围） |
| 服务注册原语 | `ctx.provide/accessor/set` + registry.register | beans.xml 声明式 bean + `x:override`（Delta） |

---

## 二、逐项详细对比

### 2.1 插件定义形态：`apply(ctx, config)` vs `IPluginActivator.activate(scope, config)`

| | dsh | nop-plugin |
|---|---|---|
| 接口 | 函数 `(ctx, config) => any`；类 `new(ctx, config)`；对象 `apply(ctx, config)` | `IPluginActivator.activate(IPluginScope scope, Map<String,Object> config)` |
| 参数 | ctx（fiber context，继承父 context）+ config | scope（实例作用域，服务/effect）+ config（实例配置，createInstance 传入的合并视图）——**双参数对齐** |
| 注册行为 | apply 内 `ctx.effect()`/`ctx.tools.register()` 等 | activate 内 `scope.effect()`/`scope.getService()` 等 |
| 返回值 | `Effect<T>`（disposer/promise/iterable——**返回值自动注册为 effect**，便捷模式 `return () => cleanup`） | `Disposable`（可空——**返回值自动注册为 effect**，同构） |
| 重复调用 | 每次 `ctx.plugin()` 一个新 fiber（= 新实例）；同 fiber 依赖变化时 apply 重跑 | `createInstance` 同 key 抛异常；deactivate→activate 重跑 activator；并发重复幂等 |
| 声明式外壳 | 无（纯代码） | plugin.plugin.xml 声明（`activator="beanId"`）+ Delta 定制 |
| 依赖声明 | `inject` 元数据 | coeffect spec（beans.xml 根节点属性） |

**对齐点**：参数传递模式完全同构（ctx → scope）。**差异**：dsh 的 plugin 是纯函数式定义（代码即定义）；Nop 的 plugin 定义在 beans.xml（声明式），activator 只是激活行为——因此 Nop 拥有结构层节点级 Delta 定制能力（dsh 无）。

### 2.2 服务访问：`ctx.<key>` vs `getService(Class<T>)`

| | dsh | nop-plugin |
|---|---|---|
| 接口 | `ctx.tools`/`ctx.llm` 等（proxy 属性读取） | `getService(Class<T>)` / `getServices(Class<T>)` |
| 类型 | **弱类型**（字符串 key，编译期经 declaration merging 补类型） | **强类型**（Class 参数，编译期保证） |
| 失效语义 | traceable proxy；非活跃时经 `ctx.get` 返回 undefined | 生命周期绑定代理：deactivate/destroy 后调用**抛 INACTIVE**（快速失败） |
| 多候选 | 由 registry 语义决定 | primary 优先 → 唯一实现 → 多候选抛异常（明确） |
| 暴露内部 | ctx 是公开服务仓库 | 内部子容器**隐藏**，只经 getService |

**差异**：Nop 强类型 + 显式失效（抛错 vs undefined）+ 隐藏内部容器。dsh 弱类型、ctx 公开。

### 2.3 可逆副作用：`ctx.effect` vs `IPluginScope.effect`

| | dsh | nop-plugin |
|---|---|---|
| 接口 | `ctx.effect(execute, label?)` | `IPluginScope.effect(Disposable d)` |
| 执行时机 | execute 立即执行，返回 disposer | disposer 注册（回退时执行） |
| 回退顺序 | disposer **逆序**收集（fiber 卸载或 disposer 调用） | **LIFO** 回退（`close()`） |
| 幂等 | 重复调用 no-op；disposed fiber 上抛 `INACTIVE_EFFECT` | `close()` 幂等；close 后 effect() 抛异常 |
| 可观测 | `getEffects()` 诊断树 | `effects()` 可观测列表（quiescence 断言） |
| 与 IoC 关系 | 无 | 不承诺观测 IoC 内部（子容器 destroy 是实现细节） |

**对齐点**：逆序/LIFO 回退、幂等、可观测、INACTIVE 快速失败——机制同构。

### 2.4 实例/fiber：`ctx.plugin` vs `createInstance`

| | dsh | nop-plugin |
|---|---|---|
| 创建 | `ctx.plugin(plugin, ...config)` → Fiber（可 await） | `createInstance(pluginId, instanceKey, config, parent)` → IPluginInstance |
| 多实例 | `Runtime.fibers`（每 `ctx.plugin()` 调用一个 fiber） | `getInstances()`（每 createInstance 一个实例） |
| identity | `fiber.uid`（registry 唯一） | `instanceKey`（用户指定，同定义唯一） |
| 生命周期 | `dispose()/restart()/update()` | `destroy()/activate()/deactivate()`（异步） |
| 状态 | `fiber.state`（PENDING/LOADING/ACTIVE 等）+ `fiber.inertia`（in-flight） | `ACTIVATED/DEACTIVATED` + in-flight 单飞（异步串行化） |
| 配置 | `fiber.config`（校验后） | `instance.getConfig()`（实例配置域合并视图） |
| 层级 | fiber ctx 原型继承（parent.extend） | `parent` 参数 + 服务查找沿链回退/级联销毁/配置层叠 |
| 上下文 | `fiber.ctx`（extends 父 context） | `getScope()`（激活期视图） |

**对齐点**：一定义多实例、独立生命周期、配置随实例、层级继承——结构同构。**差异**：Nop 有显式 `instanceKey`（用户可读标识）；dsh `uid` 是内部序号。

### 2.5 依赖声明：`inject` vs coeffect

| | dsh | nop-plugin |
|---|---|---|
| 载体 | plugin 元数据 `inject: ['tools','llm']` 或对象形式 | plugin.plugin.xml 属性（`requires`/`if-property`，plugin.xdef 原生） |
| 语义 | 服务未就绪 → PENDING（不激活）；服务变更 → 卸载重跑 | 定义级：能否派生实例；实例级：该实例是否激活（基于实例配置域） |
| 时机 | 加载时（inject 驱动，无独立 activate 阶段） | **独立 activate 阶段**（加载/激活两态分离） |
| 可选依赖 | `ctx.get(name)` 探测 | （未建模，经 getService 失败处理） |
| 动态性 | 服务消失/回来 → 自动重载 | reconcile 迭代评估（activating/deactivating/neutral，环检测） |

**差异**：语义等价（"依赖满足才激活"），时机不同（加载时 vs 激活时）。Nop 的加载/激活分离使 coeffect 是独立的运行时评估（可反复激活/去激活），dsh 的 inject 是加载流程的一部分。

### 2.6 作用域：`createScope`/`agent.ctx` vs 实例隔离

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `createScope(ctx, key)` + scope key（活 Agent 即 key）+ `bindScopeParent` | `createInstance("agent-1")` 独立实例（scope/effect/配置域） |
| 可见性 | scope 内注册 = scope-visible AND scope-lifetime | 实例内 effect/服务隔离（getService 代理绑定实例） |
| shadowing | 最近层胜出（per-agent 同名覆盖） | 消费侧职责（tool registry 等），plugin 层不内置 |
| 事件派发 | scopeTarget carrier（thisArg 过滤） | 事件总线独立基础设施 |

**差异**：dsh 的 scope 是"注册可见性"机制（per-agent 的同名注册 shadowing）；Nop 的实例隔离是"生命周期隔离"（每实例独立 scope/effect）。shadowing 语义 Nop 交由消费侧。

### 2.7 服务注册原语：`provide/accessor/set` vs beans.xml 声明式

| | dsh | nop-plugin |
|---|---|---|
| 注册 | `ctx.provide(name, value)` / `ctx.accessor(name, {get,set})` / `ctx.set(name, value)`（运行时） | beans.xml bean 定义（**声明式**）+ `x:override=merge/remove`（Delta） |
| 动态注册 | 支持（运行时 provide） | **裁决**：scope 不提供动态注册（静态装配下无法被同容器消费）；activator 经 getService 取 bean 后外部注册 |
| 定制 | patch 配置行级（无 remove/deep-merge） | **节点级** Delta（可精确改某 bean 某属性、可 remove） |

**差异**：dsh 运行时动态注册 + 配置行级 patch；Nop 声明式静态装配 + 节点级 Delta（更细、有结构化逆元）。这是 Nop 结构层核心优势。

### 2.8 生命周期入口：`fiber.dispose/restart/update` vs `instance.destroy/activate/deactivate`

| | dsh | nop-plugin |
|---|---|---|
| 销毁 | `fiber.dispose()` | `instance.destroy()` / `manager.destroyInstance()` |
| 重启 | `fiber.restart()`（重载当前 config） | `instance.deactivate()+activate()` 或 `reloadPlugin`（HMR） |
| 更新 | `fiber.update(config)`（internal/update waterfall，可否决） | `IPlugin.updateConfig(config)`（定义级；实例级经配置域） |

**差异**：dsh 有显式 `restart/update`（含 waterfall 否决钩子）；Nop 以 deactivate/activate 组合 + HMR 编排覆盖。Nop 的异步生命周期（CompletionStage）+ in-flight 单飞对齐 dsh `fiber.inertia`。

---

## 三、语义差异总结

| 维度 | 对齐 | Nop 更强 | dsh 更强 |
|---|---|---|---|
| 参数传递激活入口 | ✅（apply(ctx, config) ↔ activate(scope, config)） | | |
| 可逆副作用 | ✅（ctx.effect ↔ scope.effect，LIFO/幂等/可观测） | | |
| 失效语义 | ✅（INACTIVE 快速失败） | 强类型抛错 vs undefined | |
| 服务访问类型 | | ✅ 强类型 + 隐藏容器 | |
| 结构层定制粒度 | | ✅ 节点级 Delta + remove | |
| 声明式外壳 | | ✅ beans.xml + Delta | |
| 多实例/fiber | ✅ | | |
| 依赖条件激活 | ✅（语义等价，时机不同） | 独立 activate 阶段 | inject 加载时驱动 |
| 运行时动态注册 | | | ✅ ctx.provide/accessor（Nop 裁决不做） |
| 生命周期入口 | | | ✅ restart/update + waterfall 否决 |

**核心结论**：
1. **激活入口、effect、多实例、依赖激活、失效语义**——五个核心机制同构（参数传递模式使然）。
2. **Nop 结构性优势**：强类型服务访问、节点级 Delta 定制、声明式外壳（beans.xml）、隐藏内部容器。
3. **dsh 优势**：运行时动态服务注册（provide/accessor）、fiber 显式 restart/update（含瀑布否决）。
4. **不属于 plugin 接口**：事件总线（emit/waterfall 等）是独立基础设施，Nop 侧需要时单独设计。

## 四、结论

nop-plugin 接口与 dsh 在**核心机制**上同构（激活入口参数传递、可逆副作用、多实例、依赖激活、失效语义），在**表达方式**上不同（Nop 声明式 + 强类型 + 结构层 Delta，dsh 函数式 + 弱类型 + 运行时注册）。差异源于可逆计算的结构层优势（声明式、节点级、可逆逆元）与 dsh 的运行时灵活性（动态注册、瀑布否决）——两者互补，Nop 以结构层优势换取更强的类型安全与定制粒度。
