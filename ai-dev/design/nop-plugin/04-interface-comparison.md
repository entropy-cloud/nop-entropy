# nop-plugin 与 dsh 接口设计详细对比

**日期**：2026-08-14；**2026-08-22 定位反转修订**（多实例/fiber 对应关系改判为"有意拒绝"）
**范围**：dsh/Cordis 核心接口 vs nop-plugin 设计接口，逐项对比（接口名、方法、语义、对应关系、差异）
**状态**：active
**关联**：`01-architecture-baseline.md`（§七 接口契约）、`02-dsh-usage-coverage.md`（用法评估）、`03-coeffect-and-agent-example.md`（场景对照）

---

## 一、接口总览

| 概念 | dsh（TypeScript/Cordis） | nop-plugin（Java） |
|---|---|---|
| 插件定义 | 函数 `(ctx, config)` / 类 / 对象 `apply(ctx, config)` + `inject`/`Config` 元数据 | `IPlugin`（load/unload/activate/deactivate）+ `IPluginActivator`（激活入口） |
| 激活入口 | `apply(ctx, config)` —— ctx + config **参数传递** | `IPluginActivator.activate(IPluginScope scope, Map<String,Object> config)` —— scope + config **参数传递**（同构） |
| 生命周期单元 | `Fiber`（每次 `ctx.plugin()` 一个，可多实例） | **IPlugin 自身**（单层状态机，一个定义至多一个激活；无独立实例对象——R 裁决） |
| 可逆副作用 | `ctx.effect(execute, label)` → disposer | `IPluginScope.effect(Disposable)` |
| 服务访问 | `ctx.<key>`（弱类型 proxy） | `getService(Class<T>)`（强类型激活态代理） |
| 配置 | `fiber.config`（校验后） | 定义级配置域（loadPlugin 初始 + updateConfig 合并视图） |
| 依赖声明 | `inject`（服务名级，PENDING 直到就绪） | coeffect spec（仅插件级：requires + if-property） |
| 作用域 | `createScope(ctx, key)` / `agent.ctx` | **不建模**（agent 层 session/contribution 职责——R 裁决） |
| 生命周期入口 | `fiber.dispose()/restart()/update()` | `plugin.deactivate()/reloadPlugin/updateConfig` |
| 事件 | `ctx.on/emit/waterfall/parallel/serial` | **独立基础设施**（事件总线，不属 plugin 范围） |
| 服务注册原语 | `ctx.provide/accessor/set` + registry.register | beans.xml 声明式 bean + `x:override`（Delta） |

---

## 二、逐项详细对比

### 2.1 插件定义形态：`apply(ctx, config)` vs `IPluginActivator.activate(scope, config)`

| | dsh | nop-plugin |
|---|---|---|
| 接口 | 函数 `(ctx, config) => any`；类 `new(ctx, config)`；对象 `apply(ctx, config)` | `IPluginActivator.activate(IPluginScope scope, Map<String,Object> config)` |
| 参数 | ctx（fiber context）+ config | scope（激活期作用域）+ config（定义级配置合并视图）——**双参数对齐** |
| 注册行为 | apply 内 `ctx.effect()`/`ctx.tools.register()` 等 | activate 内 `scope.effect()`/`scope.getService()` 等 |
| 返回值 | `Effect<T>`（**返回值自动注册为 effect**） | `Disposable`（可空——**返回值自动注册为 effect**，同构） |
| 重复调用 | 每次 `ctx.plugin()` 一个新 fiber | 同 plugin 已 ACTIVATED 幂等返回；deactivate→activate 重跑 activator；并发重复经 in-flight 单飞收敛 |
| 声明式外壳 | 无（纯代码） | plugin.plugin.xml 声明（`activator="beanId"`）+ Delta 定制 |
| 依赖声明 | `inject` 元数据（服务名级） | coeffect spec（插件级，plugin.xdef 原生属性） |

**对齐点**：参数传递模式完全同构。**差异**：dsh 的 plugin 是纯函数式定义（代码即定义）；Nop 的 plugin 定义在结构层（声明式），activator 只是激活行为——Nop 因此拥有节点级 Delta 定制能力。

### 2.2 服务访问：`ctx.<key>` vs `getService(Class<T>)`

| | dsh | nop-plugin |
|---|---|---|
| 接口 | `ctx.tools`/`ctx.llm` 等（proxy 属性读取） | `getService(Class<T>)` / `getServices(Class<T>)` |
| 类型 | **弱类型**（字符串 key，编译期经 declaration merging 补类型） | **强类型**（Class 参数） |
| 失效语义 | 属性弱读经 ctx.get 返回 undefined；traceable proxy 方法调用抛 INACTIVE（两路径并存，R4 对照 cordis 源码终核——见 `03` §5.3 统一注解） | 激活态绑定代理：deactivate 后调用**抛 INACTIVE**（快速失败），重激活后恢复 |
| 多候选 | 由 registry 语义决定 | primary 优先 → 唯一实现 → 多候选抛异常（明确） |
| 作用域选择 | realm/scope 视图解析（同名多实现按上下文选择） | 单一解析语义（realm/scope 不建模——agent 层职责） |
| 暴露内部 | ctx 是公开服务仓库 | 内部子容器**隐藏** |

**差异**：Nop 强类型 + 显式失效 + 隐藏内部容器；dsh 弱类型、公开、带作用域视图。反转注记：原设计的"per-instance 路由"随实例机制删除，服务获取绑定对象从 instance 改为 plugin 激活态。

### 2.3 可逆副作用：`ctx.effect` vs `IPluginScope.effect`

| | dsh | nop-plugin |
|---|---|---|
| 接口 | `ctx.effect(execute, label?)` | `IPluginScope.effect(Disposable d)` |
| 执行时机 | execute 立即执行，返回 disposer | disposer 注册（回退时执行） |
| 回退顺序 | 单 effect 内 LIFO；顶层并发清理 | **全量全局 LIFO**（更严格的超集） |
| 幂等 | disposed fiber 上抛 INACTIVE_EFFECT | `close()` 幂等；close 后 effect() 抛异常 |
| 可观测 | `getEffects()` 诊断树 | `effects()` 可观测列表（quiescence 断言） |
| 绑定单元 | fiber（可多实例） | 每次激活（一定义至多一激活） |

**对齐点**：LIFO 回退、幂等、可观测、INACTIVE 快速失败——机制同构。

### 2.4 生命周期单元：`Fiber` vs IPlugin 自身（R 裁决：无 fiber 对应物）

> **本节是 2026-08-22 反转的核心改动点。**

| | dsh | nop-plugin（反转后） |
|---|---|---|
| 创建 | `ctx.plugin(plugin, ...config)` → Fiber（每次一个新实例） | `loadPlugin` → LOADED；reconcile/显式 activate → ACTIVATED（唯一） |
| 多实例 | `Runtime.fibers`（一定义多 fiber 是原生语义） | **不支持**（non-goal）：一个定义至多一个激活 |
| identity | `fiber.uid`（registry 唯一序号） | plugin id 本身（无需实例标识） |
| 状态 | PENDING/LOADING/ACTIVE/FAILED/UNLOADING/DISPOSED 六态 | UNLOADED/LOADED/ACTIVATING/ACTIVATED/DEACTIVATING/FAILED 六态（单层承载，无 DISPOSED——unload 即回到 UNLOADED 可再 load） |
| 层级 | fiber ctx 原型继承（parent.extend） | **无 parent 实例层级**（跨 plugin 复用经宿主容器/collect-beans；会话树归 agent 层） |
| 配置 | `fiber.config`（每 fiber 一份） | 定义级配置域（一份；updateConfig 热应用） |
| 生命周期入口 | `dispose()/restart()/update()` | `deactivate()/reloadPlugin(HMR)/updateConfig` |

**为什么 Nop 有意不做 fiber**：fiber 的存在理由是 dsh 客户端场景中"同一段插件代码在不同作用域各活一份"（每个 agent 会话一个 scope fiber）。Nop 服务端无状态下没有这类插件层注册内容（00 §〇认识论边界表）；等价需求各有归属——租户差异是 `IContext` 数据、字段定制是编译期 Delta、会话树是 agent 层 session 模型。原设计曾以 `createInstance(pluginId, instanceKey, config, parent)` 对齐 fiber，该 API 及 instanceKey/parent/实例配置域已整体移除（01 §7.2 去向表）。

### 2.5 依赖声明：`inject` vs coeffect

| | dsh | nop-plugin |
|---|---|---|
| 载体 | plugin 元数据 `inject: ['tools','llm']` | plugin.plugin.xml 属性（`requires`/`if-property`，plugin.xdef 原生） |
| 坐标 | **服务名级**（服务出现/消失触发 notify → 重解析） | **插件 id 级**（其他 plugin 的 ACTIVATED 态 + 全局配置项） |
| 语义 | 依赖未就绪 → PENDING（不激活）；依赖变更 → 卸载重跑 | 条件满足 → activate；条件丧失 → deactivate；变化反复可逆 |
| 时机 | 加载时（inject 驱动，无独立 activate 阶段） | **独立 activate 阶段**（加载/激活两态分离） |
| 动态性 | 服务级响应式（notify/_refresh） | reconcile 迭代评估（环检测 + 最大迭代）；插件内结构变更走 HMR 重载重新门控 |

**差异定性（R 终裁，2026-08-22）**：坐标不同是**有意为之**而非缺口。dsh 以服务名为组合坐标（provide/inject 涌现扩展点）；Nop 的组合坐标是 VFS 路径 + DSL 节点，被动追踪由 DeltaLoader 提供。服务级响应式依赖在 Nop 是重复建设（01 §五 R 注解）。可选依赖（dsh `ctx.get(name)` 探测）不建模——需要弱依赖时用配置条件表达。

### 2.6 作用域：`createScope`/`agent.ctx` vs 不建模

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `createScope(ctx, key)` + ScopedLayers（条目分层，近遮蔽远）+ scopeTarget 事件路由 | **不建模** |
| 归属 | dsh 客户端 harness 的核心机制 | Nop 划归 nop-ai-agent：session/contribution 机制做视图合成（02 §四差距表） |

**差异定性**：反转前以"实例隔离"近似 scope/realm 并标注为超集简化；反转后承认这是不同维度的机制，plugin 通用层不再合成二者——领域层的可见性需求由领域层自己的坐标系解决。

### 2.7 服务注册原语：`provide/accessor/set` vs beans.xml 声明式

| | dsh | nop-plugin |
|---|---|---|
| 注册 | `ctx.provide(name, value)` / `ctx.accessor(name,{get,set})` / `ctx.set(name, value)`（运行时） | beans.xml bean 定义（**声明式**）+ `x:override=merge/remove`（Delta） |
| 动态注册 | 支持 | **裁决保留**：scope 不提供动态注册（静态装配下无法被同容器消费）；beans.xml 静态声明或外部注册表 |
| 定制 | patch 配置行级 | **节点级** Delta（可精确改某 bean 某属性、可 remove） |
| 单写者 | 运行时约束（同坐标一个提供者） | 结构层天然满足（bean id 在 Delta 链上静态归属） |

### 2.8 生命周期入口：`fiber.dispose/restart/update` vs `plugin.deactivate/reload/updateConfig`

| | dsh | nop-plugin |
|---|---|---|
| 销毁 | `fiber.dispose()` | `deactivate()`（回 LOADED）/ `unloadPlugin`（回 UNLOADED） |
| 重启 | `fiber.restart()`（同 fiber 重激活，身份延续） | `deactivate()+activate()` 或 `reloadPlugin`（HMR：干净重建，取舍记录见 01 §六） |
| 更新 | `fiber.update(config)`（internal/update waterfall，可否决） | `updateConfig(config)`（热应用/缓存待激活；无 waterfall 否决钩子） |

---

## 三、语义差异总结

| 维度 | 对齐 | Nop 更强 | dsh 更强 |
|---|---|---|---|
| 参数传递激活入口 | ✅（apply ↔ activate 双参数同构） | | |
| 可逆副作用 | ✅（effect ↔ scope.effect） | | |
| 失效语义 | ✅（调用路径均快速失败；dsh 弱读路径更宽松，待 R4 终核） | 强类型 + 全路径显式抛错 | |
| 服务访问类型 | | ✅ 强类型 + 隐藏容器 | |
| 结构层定制粒度 | | ✅ 节点级 Delta + remove | |
| 声明式外壳 | | ✅ plugin.xml + XDSL 管线 | |
| 加载/激活分离 | ✅（语义等价） | ✅ 独立 activate 阶段 | inject 加载时驱动 |
| 依赖坐标 | | 插件级简洁性（有意取舍） | ✅ 服务名级响应式 |
| 多实例/fiber | | 有意不做（认识论边界） | ✅ 客户端场景必需 |
| 运行时动态注册 | | 有意不做 | ✅ provide/accessor/set |
| 作用域/视图合成 | | 不建模（agent 层职责） | ✅ createScope/ScopedLayers/realm |
| 生命周期否决钩子 | | 无 | ✅ waterfall 否决 |

**核心结论（反转后）**：
1. **激活入口、effect、失效语义、加载/激活分离**——四个核心机制同构。
2. **Nop 结构性优势**：强类型服务访问、节点级 Delta、声明式外壳、隐藏内部容器。
3. **dsh 优势集中在客户端 harness 特有机制**：多实例 fiber、服务级响应式 inject、scope/realm 视图合成、waterfall 否决——这些在 Nop 定位下要么有意拒绝（多实例、服务级依赖），要么划归领域层（scope/realm）。这不是能力缺陷，而是定位差异：Nop 用"编译期 Delta + 插件级门控"覆盖同类需求的平台侧子集。

## 四、结论

nop-plugin 与 dsh 在**核心生命周期机制**上同构（参数传递激活、可逆 effect、INACTIVE 失效、两态分离）；分歧集中于**组合坐标与粒度**：dsh 以服务坐标 + 运行时多实例支撑客户端交互式 harness，Nop 以 VFS/DSL 结构坐标 + 编译期 Delta + 插件级门控支撑服务端平台。2026-08-22 反转将原设计中模仿 fiber 的实例机制移除，使接口面收敛到定位所需的最小集（IPlugin/IPluginScope/IPluginActivator/IPluginManager/IPluginContext 五接口，无实例概念）。
