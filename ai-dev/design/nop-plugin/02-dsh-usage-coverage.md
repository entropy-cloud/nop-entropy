# dsh plugin 用法在 nop-plugin 中的支持度评估

**日期**：2026-08-14
**范围**：对照 DeepSeek Harness（dsh/Cordis）对 plugin 的用法，评估 `nop-plugin` 设计（`00-vision` + `01-architecture-baseline`）的覆盖度
**状态**：active

---

## 一、评估范围界定

**plugin 核心职责（本评估范围）**：生命周期（加载/激活/卸载/多实例）、可逆副作用（effect）、配置层叠（Delta）、服务获取与隔离。

**不属于 plugin 评估范围**（独立基础设施 / agent 框架层，不误算为 plugin 差距）：
- **事件总线**（emit/waterfall/parallel/serial）——独立基础设施，与 IoC 同级。dsh plugin 用它通信，但事件总线本身不是 plugin 职责。Nop 若需要，是独立设计。
- **session log / turn / step / agent.inject / goals / fork**——agent 框架层（`nop-ai-agent`）。

## 二、dsh plugin 用法清单（源自 dsh 架构文档）

| # | dsh 用法 | dsh 机制（源） |
|---|---|---|
| A | 一切皆插件，无特权核心 | "everything is a plugin... no privileged core to patch"（architecture.md）；model adapter / tool registry / session log / agent loop 都是 plugin |
| B | 注册即副作用，卸载 unwind | "registrations are effects that unwind when their plugin unloads"；`ctx.effect()`/`ctx.on()` 返回 disposer（AGENTS.md） |
| C | 层叠配置组合 | profile → bundle → `cordis.patch.yml` → `--patch` 有序叠加（architecture.md / app-boot README） |
| D | 能力接缝（三角色） | capability seam = Service Definition + Provider + Consumer，一个 provider swap 改变整个产品（glossary.md） |
| E | per-agent 作用域 + shadowing | scope：per-agent 注册；shadowing：most-specific-wins 同名覆盖（glossary.md） |
| F | 声明依赖、等服务可用 | `inject`：命名所需服务，等服务存在才加载，加载顺序由服务需求决定（cordis-primer.md） |
| G | 热更新 | `watchUserPatches`：监听 patch 文件变化，事务性重组（app-boot README） |
| H | 隔离域 | `isolate` realm：provider+consumer 共享一个 realm；realm table 让两个 fiber 解析同 key 到不同值（architecture.md / glossary.md） |
| I | 多实例（fiber） | 一个 component 多次实例化，各携带独立生命周期（论文 §4.1） |

## 三、逐项评估

### A. 一切皆插件，无特权核心 —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | 每个 part 是 plugin，挂载到 shared context，无特权核心 | plugin 定义（beans.xml）+ 实例（IPluginInstance）承载任意 bean；通过 `createInstance` 挂载 |

**如何支持**：dsh 的 "model adapter / tool registry / agent loop 都是 plugin" 在 Nop 里就是"这些组件都是 plugin 子容器中的 bean"。plugin 定义声明了哪些 bean（服务），激活后实例化。没有特权核心——宿主只是一个 parent 容器，plugin 子容器可覆盖（IoC 子容器查找回退到父，子可覆盖父）。

### B. 注册即副作用，卸载 unwind —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `ctx.effect()`/`ctx.on()` 注册，返回 disposer，卸载 unwind | `IPluginScope.effect(disposable)` 注册；实例 deactivate/destroy → 子容器 stop → `singletonScope.close()` 遍历 destroyBean + subscription cancel + LIFO 回退 effect |

**如何支持**：dsh 的 "registrations are effects that unwind" 直接对应 `IPluginScope`（§四 architecture-baseline）。三源 effect（bean destroy / subscription cancel / 手动 effect）统一聚合，LIFO 回退，`effects()` 可观测、可断言 quiescence。

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
| 机制 | Service Definition（抽象类/registry）+ Provider（实现）+ Consumer（注入） | bean 接口（Definition）+ 多 bean 实现（Provider）+ `@Inject`/`getService`（Consumer） |

**如何支持**：dsh 的 capability seam 在 Nop 里就是标准的 IoC 接口 + 多实现 + 注入。"一个 provider swap 改变整个产品" = 替换子容器中某个接口的 bean 实现（Delta 覆盖）。`<ioc:collect-beans by-type>` 还支持按接口收集全部实现。

### E. per-agent 作用域 + shadowing —— △ 部分（shadowing 待明确）

| | dsh | nop-plugin |
|---|---|---|
| scope（per-agent 注册） | agent.ctx，per-agent 注册 | ✅ IPluginInstance（per-agent/per-tenant 实例，独立子容器+scope） |
| shadowing（同名覆盖） | most-specific-wins：scoped tool/section 替换全局同名 | △ 子容器可覆盖父 bean（IoC 天然），但"同名 tool/prompt shadowing"的语义未在 plugin 层显式建模 |

**差距**：dsh 的 shadowing 是"per-agent scope 内的同名注册覆盖全局"。Nop 的子容器天然支持子覆盖父 bean（IoC 查找回退），机制上可达。但若要精确语义（如 tool schema 的 shadowing、prompt section 的 shadowing），属于**消费侧（tool registry / prompt assembly）**的职责，不是 plugin 通用层——plugin 只提供"独立子容器"这个隔离基座，shadowing 的具体规则由在子容器里注册的服务（tool registry 等）定义。

**结论**：隔离基座（IPluginInstance 子容器）✅ 支持；shadowing 的精确语义是消费侧职责，plugin 不需要内置。

### F. 声明依赖、等服务可用 —— △ 部分（运行时动态依赖待增强）

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `inject`：命名所需服务，**等服务存在才加载**；加载顺序由服务需求决定（非手工编排） | `<ioc:condition on-bean>`（build 时条件）+ coeffect 条件激活（运行时） |

**差距**：dsh 的 `inject` 是**加载时动态等待**——plugin 声明依赖，loader 等服务可用才激活该 plugin。Nop 的 `<ioc:condition on-bean>` 是 build 时（一次性），coeffect 条件激活是运行时（activating/deactivating）。

**如何弥补**：coeffect 条件激活（§五 architecture-baseline）覆盖了"运行时依赖满足才激活"——plugin 声明依赖的其他 plugin 已 ACTIVATED 作为 coeffect spec，`reconcile` 时按依赖图批量激活。这等价于 dsh 的"等服务可用才加载"，只是时机从"加载时"移到"激活时"（加载/激活两态分离后的自然结果）。

**结论**：支持，通过 coeffect 条件激活实现"依赖满足才激活"。

### G. 热更新 —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `watchUserPatches`：监听 patch 变化，事务性重组全树 | `reloadPlugin`：beans.xml 变更 → loader 依赖失效 → 重算 BeansModel → unload+load+重建实例 |

**如何支持**：`reloadPlugin`（§六 architecture-baseline）覆盖 dsh 的 HMR。

**Nop 优势**：dsh 的 HMR 重组须重新激活整棵插件树（活资源 teardown）；Nop 因加载/激活分离 + 实例独立，reload 只重建该 plugin 的实例，其他 plugin 不受影响（子容器隔离）。

### H. 隔离域（realm/isolate） —— △ 部分

| | dsh | nop-plugin |
|---|---|---|
| 机制 | isolate realm：provider+consumer 共享 realm；realm table 让两 fiber 解析同 key 到不同值 | 子容器隔离（每实例独立容器）+ `resolveKey(key, realm)` |

**如何支持**：dsh 的 realm 是"同一 key 在不同 fiber 解析到不同值"。Nop 的多实例（IPluginInstance）天然隔离（各自子容器），但跨实例的"按 realm 解析共享 key"需要 `IPluginContext.resolveKey(key, realm)`（§七 architecture-baseline）。

**差距**：`resolveKey` 的具体 realm 语义（realm 表如何组织、key 如何按 realm 隔离）尚未细化。但这是共享数据层的设计，可在实现时按需定义。

### I. 多实例（fiber） —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | 一个 component 多次实例化，各独立生命周期、identity、parent | IPlugin 定义 + IPluginInstance 实例（=fiber）；`createInstance` ×N；instanceKey + parent |

**如何支持**：IPluginInstance（§七 architecture-baseline）显式建模多实例，对应 Cordis fiber。支持 instanceKey（tenant/agent/session）+ parent（层级实例化）。

## 四、差距总结

| 差距项 | 严重度 | 说明 |
|---|---|---|
| shadowing 精确语义 | 低 | 隔离基座（子容器）已支持；精确 shadowing 规则是消费侧（tool registry 等）职责，非 plugin 通用层 |
| realm 共享数据语义 | 中 | `resolveKey(key,realm)` 已设计接口；realm 表组织/隔离规则待实现时细化 |
| 加载时动态等待依赖 | 低 | 已由 coeffect 条件激活覆盖（激活时等依赖满足）；语义等价 |

**无 P0 级差距**：dsh 对 plugin 的核心用法（插件化/可逆/层叠/接缝/多实例/HMR/隔离）在 nop-plugin 设计中均有对应机制。

## 五、Nop 相对 dsh 的优势

1. **结构层节点级 Delta**：beans.xml 是完整 XDSL，`x:override=merge/remove` 节点级定制 + 结构化逆元；dsh patch 是配置行级、无 deep-merge/remove。
2. **结构层 + 运行时层双可逆**：结构层 Delta 逆元（`x:override`）+ 运行时层 effect 逆元（IPluginScope）；dsh 只有运行时层 effect 逆元。
3. **加载/激活分离 + loader 被动模式**：结构变更自动失效重算静态 model，运行时独立；dsh 加载与激活耦合，HMR 须重激活整树。
4. **不改 IoC**：plugin 可逆性通过子容器 create/stop 实现，IoC 核心不可变；dsh 的可逆绑在运行时 context 生命周期。

## 六、结论

dsh 对 plugin 的核心用法在 nop-plugin 设计中**均可支持**，无 P0 差距。对应关系清晰：

- 插件化/可替换 → plugin 子容器 bean + IoC 父子回退
- 可逆副作用 → IPluginScope + 子容器 stop
- 层叠配置 → beans.xml Delta（更细）
- 能力接缝 → IoC 接口+多实现+注入
- per-agent 作用域 → IPluginInstance 多实例
- 依赖声明 → coeffect 条件激活
- HMR → reloadPlugin
- 隔离 → 子容器 + resolveKey(realm)
- 多实例 → IPluginInstance = fiber

事件总线、session/turn/inject 等 dsh 能力是**独立基础设施或 agent 框架层**，不属于 plugin 评估范围，不构成 plugin 差距。
