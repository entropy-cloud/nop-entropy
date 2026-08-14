# dsh plugin 用法在 nop-plugin 中的支持度评估

**日期**：2026-08-14（经独立审查第一轮修订）
**范围**：对照 DeepSeek Harness（dsh/Cordis）对 plugin 的用法，评估 `nop-plugin` 设计（`00-vision` + `01-architecture-baseline`）的覆盖度
**状态**：active

---

## 一、评估范围界定

**plugin 核心职责（本评估范围）**：生命周期（加载/激活/卸载）、可逆副作用（effect）、配置层叠（Delta）、服务获取与隔离。

**不属于 plugin 评估范围**（独立基础设施 / agent 框架层，不误算为 plugin 差距）：
- **事件总线**（emit/waterfall/parallel/serial）——独立基础设施，与 IoC 同级。dsh plugin 用它通信，但事件总线本身不是 plugin 职责。Nop 若需要，是独立设计。
- **session log / turn / step / agent.inject / goals / fork**——agent 框架层（`nop-ai-agent`）。

## 二、dsh plugin 用法清单（源自 dsh 架构文档）

| # | dsh 用法 | dsh 机制（源） |
|---|---|---|
| A | 一切皆插件，无特权核心 | "everything is a plugin... no privileged core to patch"（architecture.md） |
| B | 注册即副作用，卸载 unwind | "registrations are effects that unwind when their plugin unloads"；`ctx.effect()`/`ctx.on()` 返回 disposer（AGENTS.md） |
| C | 层叠配置组合 | profile → bundle → `cordis.patch.yml` → `--patch` 有序叠加（architecture.md / app-boot README） |
| D | 能力接缝（三角色） | capability seam = Service Definition + Provider + Consumer（glossary.md） |
| E | per-agent 作用域 + shadowing | scope：per-agent 注册；shadowing：most-specific-wins 同名覆盖（glossary.md） |
| F | 声明依赖、等服务可用 | `inject`：命名所需服务，等服务存在才加载；服务变更则卸载重跑（cordis-primer.md / cordis-api registry） |
| G | 热更新 | `watchUserPatches`：监听 patch 文件变化，事务性重组 patch 列表（app-boot README） |
| H | 隔离域 | `ctx.isolate`：服务名级隔离（reads/writes of the service name resolve against the new label） |
| I | 多实例（fiber） | 一个 component 多次实例化，各携带独立生命周期（论文 §4.1） |
| J | 注册原语全集（服务 4 种 + 事件 5 种） | 服务：`Service` 子类自注册 / `ctx.provide(name, value)` / `ctx.accessor(name,{get,set})` / `ctx.set(name, value)`；事件：`ctx.on/emit/waterfall/parallel/serial`（事件总线，独立基础设施） |
| K | **apply(ctx, config) 参数传递**（主通道） | plugin 是函数/类/对象，ctx **作为参数传入** `apply(ctx, config)`；ctx 是 plugin 的 fiber context（继承父 context） |

## 三、逐项评估

### A. 一切皆插件，无特权核心 —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | 每个 part 是 plugin，挂载到 shared context，无特权核心 | plugin 定义（beans.xml）+ 激活实例（IPluginInstance）承载任意 bean |

**如何支持**：dsh 的 "model adapter / tool registry / agent loop 都是 plugin" 在 Nop 里就是"这些组件都是 plugin 实例中的 bean"。plugin 定义声明了哪些 bean（服务），激活后实例化。没有特权核心——宿主只提供定义加载与实例管理。

### B. 注册即副作用，卸载 unwind —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `ctx.effect()`/`ctx.on()` 注册，返回 disposer，卸载 unwind | `IPluginScope.effect(disposable)` 注册；实例 deactivate → `close()` LIFO 回退全部 |

**如何支持**：dsh 的 "registrations are effects that unwind" 直接对应 `IPluginScope`（`01` §四）。`IPluginScope` 管理 **plugin 框架自己的** effect（手动注册列表），LIFO 回退、`effects()` 可观测、可断言 quiescence。**不承诺观测 IoC 内部**（实现层子容器 stop 触发的 bean destroy 是实现细节，非 API 契约）。

**Nop 优势**：dsh 的 effect 是过程式 disposer；Nop 额外把结构层（beans.xml）的逆操作（`x:override=remove`）也纳入可逆——结构层 + 运行时层双可逆。

### C. 层叠配置组合 —— ✅ 支持（且更细）

| | dsh | nop-plugin |
|---|---|---|
| 机制 | profile→bundle→patch 有序叠加，patch 按 entry id 替换整行/插入 | beans.xml 是完整 XDSL，`x:extends`/`x:override` 节点级 Delta 合并 |

**如何支持**：dsh 的层叠是"配置行级"（id-targeted patch replaces whole config, does not deep-merge）。Nop 的 beans.xml Delta 是**节点级**——可精确改某个 bean 的某个属性，且 `x:override=merge/remove` 提供结构化逆元（dsh patch 无 remove/deep-merge）。

**结论**：支持，且粒度更细、逆元更完整。这是 Nop 相对 dsh 的核心优势。

### D. 能力接缝（三角色） —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | Service Definition（抽象类/registry）+ Provider（实现）+ Consumer（注入） | bean 接口（Definition）+ 多 bean 实现（Provider）+ `getService`/`getServices`（Consumer） |

**如何支持**：dsh 的 capability seam 在 Nop 里就是标准的接口 + 多实现 + 服务获取。"一个 provider swap 改变整个产品" = 替换实例中某个接口的 bean 实现（Delta 覆盖）。

### E. per-agent 作用域 —— ✅ 支持（多实例派生）

| | dsh | nop-plugin |
|---|---|---|
| 机制 | agent.ctx，per-agent 注册 | IPluginInstance 多实例派生（`createInstance("agent-1")` / `("agent-2")`，每实例独立 scope/effect/配置域） |

**说明**：dsh 的 per-agent scope 对应 Nop 的"实例隔离"——每个 agent 一个独立实例（独立 scope/effect/配置域），互不干扰。多实例是 plugin 框架接口层面概念，与 IoC 内部实现无关（解耦原则）。

**关于 shadowing**：dsh 的 shadowing 是**运行时注册级**同名覆盖（per-scope 内注册替换全局同名注册）。Nop 侧没有直接对应（子容器覆盖父 bean 是**定义级静态**覆盖，机制不同）。shadowing 的精确语义属于**消费侧**（tool registry / prompt assembly）职责，不是 plugin 通用层——plugin 只提供"独立实例"这个隔离基座。

### F. 声明依赖、等服务可用 —— ✅ 支持（coeffect 定义级条件激活）

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `inject`：命名所需服务，等服务存在才加载；服务变更则卸载重跑 | coeffect spec（依赖其他 plugin 已激活）+ `reconcile()` 运行时评估 |

**说明**：dsh 的 `inject` 是"依赖未就绪则不激活（PENDING 态），依赖变更则重跑"。Nop 的 coeffect 条件激活覆盖同一语义——plugin 声明依赖的其他 plugin 已 ACTIVATED 作为 coeffect spec，`reconcile` 时按依赖图批量激活/去激活。区别：dsh 的激活仍是加载流程的一部分（inject 驱动）；Nop 的激活是独立 `activate` 阶段（加载/激活两态分离）。

### G. 热更新 —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `watchUserPatches`：监听 patch 文件变化，事务性重组 **patch 列表** 并通过 compose 闭包重挂 | `reloadPlugin`：定义变更 → loader 依赖失效 → 重算 → unload+load+条件激活 |

**如何支持**：`reloadPlugin`（`01` §六）覆盖 dsh 的 HMR。

**Nop 优势**：dsh 的 HMR 重挂涉及整棵配置树重组与活资源处理；Nop 因加载/激活分离，reload 只重建该 plugin 的实例，其他 plugin 不受影响（实例隔离）。**注**：远程下载的 uber jar（发布场景）不可编辑，HMR 主要针对本地/开发文件场景。

### H. 隔离域（isolate） —— △ 机制不同

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `ctx.isolate(name)`：**服务名级**隔离——reads/writes of the service name resolve against the new label（服务实现选择） | 实例级隔离（每实例独立 scope/effect） |

**说明**：dsh 的 `ctx.isolate` 隔离的是**服务实现选择**（同一服务名在不同 label 解析到不同实现），不是"共享数据 key 解析"。Nop 的实例隔离（独立 scope）覆盖"不同实例不同实现"的需求；共享数据层（`resolveKey`）**不引入**（`00-vision` non-goals 6）——那是与 Cordis isolate 无关的新发明。

### I. 多实例（fiber） —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | 一个 component 多次实例化，各独立生命周期、identity、parent | IPlugin 定义 + IPluginInstance（=fiber）；`createInstance` 派生 N 实例；`instanceKey` + `parent` 层级 |

**如何支持**：多实例是 plugin 框架接口层面概念——`createInstance(pluginId, instanceKey, config, parent)` 派生独立实例，每实例独立 scope/effect/配置域（`instance.getConfig()`）；实例级 coeffect 基于实例配置域差异化激活；subagent 经 `parent` 层级实例化。与 IoC 内部实现无关（解耦原则）。

### J. 注册原语全集 —— ✅ 支持（映射为 plugin 层能力）

| dsh 原语 | Nop 对应 |
|---|---|
| `Service` 子类 `super(ctx,name)` 自注册 | beans.xml bean 定义，实例激活时注册（声明式） |
| `ctx.provide(name, value)` 注册服务 | 裁决：scope 不提供动态注册（静态装配下无法被同容器消费）；activator 经 `scope.getService()` 取 bean 后做外部注册 |
| `ctx.accessor(name, {get,set})` 计算属性 | bean 属性（声明式） |
| `ctx.set(name, value)` 覆盖已提供服务 | Delta 覆盖（`x:override`） |
| `ctx.on/emit/waterfall/parallel/serial` 事件 | **独立基础设施**（事件总线），不属 plugin 范围（§一） |

### K. apply(ctx) 参数传递 —— ✅ 支持（IPluginActivator 同构）

dsh 的 plugin 主通道是 `apply(ctx, config)`——ctx **作为参数传入**。Nop 的 `IPluginActivator.activate(scope, config)` 完全同构：scope + config 参数传入（01 §四）。这是"参数传递 vs 字段注入"的架构选择：与 Cordis `apply(ctx)`、NopBatch `setup(context)` 一致，**避免 SpringBatch 的成员变量保存 context 坏设计**（`docs/theory/why-springbatch-is-bad.md` §3.1：context 应参数传递而非保存为类成员；Hooks 类比：闭包传参优于 this 指针）。

## 四、差距总结

| 差距项 | 严重度 | 说明 |
|---|---|---|
| shadowing 精确语义 | 低 | 消费侧职责（tool registry 等），非 plugin 通用层 |
| 服务名级隔离（isolate 语义） | 低 | 实例级隔离覆盖主要需求；不引入共享数据层 |

**无 P0 级差距**：dsh 对 plugin 的核心用法（插件化/可逆/层叠/接缝/作用域/HMR/隔离）在 nop-plugin 设计中均有对应机制。

## 五、Nop 相对 dsh 的优势

1. **结构层节点级 Delta**：beans.xml 是完整 XDSL，`x:override=merge/remove` 节点级定制 + 结构化逆元；dsh patch 是配置行级、无 deep-merge/remove。
2. **结构层 + 运行时层双可逆**：结构层 Delta 逆元（`x:override`）+ 运行时层 effect 逆元（IPluginScope）；dsh 只有运行时层 effect 逆元。
3. **加载/激活分离 + loader 被动模式**：结构变更自动失效重算静态定义，运行时独立；dsh 激活仍嵌在加载流程（inject 驱动）。
4. **服务获取类型安全**：`getService(Class<T>)` 强类型 + 生命周期绑定代理（deactivate 后快速失败）；dsh `ctx.<key>` 弱类型。

## 六、结论

dsh 对 plugin 的核心用法在 nop-plugin 设计中**均可支持**，无 P0 差距。对应关系清晰：

- 插件化/可替换 → plugin 定义 + 激活实例
- 可逆副作用 → IPluginScope（plugin 框架自己的 effect）
- 层叠配置 → beans.xml Delta（更细）
- 能力接缝 → 接口 + 多实现 + getService
- per-agent 作用域 → IPluginInstance 多实例派生（createInstance/instanceKey）
- 依赖声明 → coeffect 定义级条件激活
- HMR → reloadPlugin
- 隔离 → 实例级 scope/effect 隔离
- 多实例 → IPluginInstance（createInstance/instanceKey/parent 层级）
- 注册原语全集（provide/accessor/set）→ beans.xml 声明式 bean + activator（声明式对应）
- apply(ctx, config) 参数传递 → IPluginActivator.activate(scope, config)（同构，参数传递非字段注入）

事件总线、session/turn/inject 等 dsh 能力是**独立基础设施或 agent 框架层**，不属于 plugin 评估范围，不构成 plugin 差距。
