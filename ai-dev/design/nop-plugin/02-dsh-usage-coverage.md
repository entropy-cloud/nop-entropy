# dsh plugin 用法在 nop-plugin 中的支持度评估

**日期**：2026-08-14（经独立审查第一轮修订）；**2026-08-22 定位反转修订**
**范围**：对照 DeepSeek Harness（dsh/Cordis）对 plugin 的用法，评估 `nop-plugin` 设计（`00-vision` + `01-architecture-baseline`）的覆盖度
**状态**：active

> **R 裁决注解（2026-08-22，定位反转）**：本评估的基线从"吸收 Cordis 全部思想"改为"按认识论边界取舍"（`00-vision.md` §〇）。dsh 是客户端交互式 harness——每个 agent 会话的注册内容依赖运行期信息，因此需要 fiber/scope/realm；Nop 是服务端无状态平台——插件层注册内容全部加载时可知。据此：E/I/H 三项（per-agent scope、多实例、isolate）从"plugin 层支持"改判为"**非 plugin 层职责**"，由 nop-ai-agent 的 session/contribution 机制承担（与 W9 立项方向一致但更进一步：不再以 instanceKey/parent 为基座）。F（依赖）收敛为纯插件级。

---

## 一、评估范围界定

**plugin 核心职责（本评估范围）**：粗粒度引入（artifact 加载/类隔离/声明式结构贡献）、激活门控（两态 + 插件级 coeffect）、可逆生命周期（effect）、服务获取。

**不属于 plugin 评估范围**（独立基础设施 / agent 框架层，不误算为 plugin 差距）：
- **事件总线**（emit/waterfall/parallel/serial）——独立基础设施，与 IoC 同级。
- **session log / turn / step / agent.inject / goals / fork**——agent 框架层（`nop-ai-agent`）。
- **per-agent/per-session 注册可见性与隔离**（scope/realm/shadowing）——agent 框架层（R 裁决后明确归入此类）。

## 二、dsh plugin 用法清单（源自 dsh 架构文档）

| # | dsh 用法 | dsh 机制（源） |
|---|---|---|
| A | 一切皆插件，无特权核心 | "everything is a plugin... no privileged core to patch"（architecture.md） |
| B | 注册即副作用，卸载 unwind | "registrations are effects that unwind when their plugin unloads"；`ctx.effect()`/`ctx.on()` 返回 disposer |
| C | 层叠配置组合 | profile → bundle → `cordis.patch.yml` → `--patch` 有序叠加 |
| D | 能力接缝（三角色） | capability seam = Service Definition + Provider + Consumer（glossary.md） |
| E | per-agent 作用域 + shadowing | scope：per-agent 注册；shadowing：most-specific-wins 同名覆盖 |
| F | 声明依赖、等服务可用 | `inject`：命名所需服务，等服务存在才加载；服务变更则卸载重跑 |
| G | 热更新 | `watchUserPatches`：监听 patch 文件变化，事务性重组 patch 列表 |
| H | 隔离域 | `ctx.isolate`：服务名级隔离（reads/writes of the service name resolve against the new label） |
| I | 多实例（fiber） | 一个 component 多次实例化，各携带独立生命周期（论文 §4.1） |
| J | 注册原语全集（服务 4 种 + 事件 5 种） | 服务：`Service` 子类自注册 / `ctx.provide(name, value)` / `ctx.accessor(name,{get,set})` / `ctx.set(name, value)`；事件：`ctx.on/emit/waterfall/parallel/serial` |
| K | **apply(ctx, config) 参数传递**（主通道） | plugin 是函数/类/对象，ctx **作为参数传入** `apply(ctx, config)` |

## 三、逐项评估

### A. 一切皆插件，无特权核心 —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | 每个 part 是 plugin，挂载到 shared context，无特权核心 | plugin 定义（plugin.xml + beans）+ 激活态承载任意 bean |

**如何支持**：dsh 的 "model adapter / tool registry / agent loop 都是 plugin" 在 Nop 里就是"这些组件都是某个 plugin 激活容器中的 bean"。没有特权核心——宿主只提供定义加载、门控与激活编排。

> **粒度注解（2026-08-22）**："一切皆插件"在 Nop 有一个有意的不完全对应：**细粒度功能不经过 plugin 引入**。Nop 的等价物是 Delta 文件系统对任意 DSL 的差量合并——biz action、workflow step、bean 属性级覆盖都在编译期结构空间完成，不需要打包成插件。plugin 只承载**粗粒度能力单元**（远程分发 + 整组 bean 贡献 + 类隔离）。这是定位差异而非覆盖缺口（00 §〇）。

### B. 注册即副作用，卸载 unwind —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `ctx.effect()`/`ctx.on()` 注册，返回 disposer，卸载 unwind | `IPluginScope.effect(disposable)` 注册；deactivate → `close()` LIFO 回退全部 |

**如何支持**：直接对应 `IPluginScope`（01 §四）。scope 绑定每次激活（原为每实例），语义不变。

> **回退顺序补注（W9，2026-08-21，机制核验，保留）**：cordis 单 effect 内部严格 LIFO、顶层并发清理；Nop 选全量全局 LIFO 是更严格的超集，天然给出确定性回退序。

**Nop 优势**：结构层（beans.xml 的 `x:override=remove`）+ 运行时层（effect）双可逆；dsh 的编译期对应物较弱（配置树整段替换、无节点级合并），运行时 effect 是其唯一完整逆元。

### C. 层叠配置组合 —— ✅ 支持（且更细）

| | dsh | nop-plugin |
|---|---|---|
| 机制 | profile→bundle→patch 有序叠加，patch 按 entry id 替换整行/插入 | plugin.xml 是完整 XDSL，`x:extends`/`x:override` 节点级 Delta 合并 |

**如何支持**：dsh 的层叠是配置行级（整行替换、不深合并）；Nop 是节点级——可精确改某个 bean 的某个属性，`x:override=merge/remove` 提供结构化逆元。

**结论**：支持，且粒度更细、逆元更完整。Nop 相对 dsh 的核心优势不变。

### D. 能力接缝（三角色） —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | Service Definition + Provider + Consumer | bean 接口（Definition）+ 多 bean 实现（Provider）+ `getService`/`getServices`（Consumer） |

**如何支持**：标准接口 + 多实现 + 强类型服务获取。"一个 provider swap 改变整个产品" = Delta 覆盖定义中某接口的 bean 实现（结构层操作，激活时生效）。

### E. per-agent 作用域 —— ➡ 改判：非 plugin 层职责（agent 层立项）

| | dsh | nop-plugin（反转后） |
|---|---|---|
| 机制 | agent.ctx，per-agent 注册 + shadowing | **plugin 层不建模**；由 nop-ai-agent session/contribution 机制承担 |

**改判理由（R 裁决，2026-08-22）**：原设计以 IPluginInstance 多实例派生对应 per-agent scope（每个 agent 一个实例）。反转后该对应物删除。认识论依据：dsh 需要 per-agent scope 是因为**注册内容依赖"这个 agent 是谁"这一运行期信息**（文章 §4.6 点名的唯一必须留在运行时的一类）；Nop 平台上这类信息属于 agent 领域层——不需要也不应该通过为每个会话派生 IoC 子容器来表达。plugin 层提供的是粗粒度基座（工具/技能以 bean 形式被贡献），**视图合成是 agent 层消费侧职责**。

**agent 层承接现状（2026-08-22 审计核实，取代初稿的 ContributionRegistry 表述）**：已落地的 per-session 工具可见性机制是 **tag 系统**（`activeTags`/`denyTags`/`denyTools` 交集过滤 + `AgentSession.activeTags` 会话级 override + `set-active-tags` 元工具，见 [nop-ai-agent-tool-tag-system](../nop-ai-agent/nop-ai-agent-tool-tag-system.md)）；`IContributionRegistry` 是全局注册表（装配期一次解析、不按 session 热生效），其 TOOL 类贡献的引擎深度解析为显式 successor。dsh scope/realm 的另外两维——同名服务按作用域解析（isolate 槽位）、近遮蔽远分层合成（ScopedLayers）——目前 agent 层**无对应立项**（open handoff，接收方立项前不得假设已有机制）。

### F. 声明依赖、等服务可用 —— ✅ 支持（仅插件级）

| | dsh | nop-plugin |
|---|---|---|
| 机制 | `inject`：命名所需**服务**，等服务存在才加载；服务变更则卸载重跑 | `requires`：命名所依赖的**其他 plugin**（须 ACTIVATED）+ `if-property` 配置条件；reconcile 运行时评估 |

**说明**：语义骨架相同（依赖满足才激活、条件变化自动激活/去激活），**依赖坐标不同**：dsh 以服务名为坐标，Nop 以 plugin id 为坐标。R 终裁（01 §五）：不做服务级依赖——服务装配属 IoC/结构空间既有语义；dsh 服务级 inject 的存在理由是它的组合坐标系由 provide/inject 涌现生成，而 Nop 的组合坐标系（VFS 路径 + DSL 节点）已由 DeltaLoader 被动追踪覆盖。"等待中"的可诊断性由 LOADED（未激活）状态 + reconcile 报告承担。

### G. 热更新 —— ✅ 支持

| | dsh | nop-plugin |
|---|---|---|
| 机制 | patch 文件监听 → 事务性重组 → entry refresh / fiber.restart | `reloadPlugin`：定义变更 → loader 失效 → deactivate → unload → load → reconcile |

**如何支持**：`reloadPlugin`（01 §六）覆盖 dsh 的 HMR。反转后流程简化（无实例快照重建），语义不变。

> **HMR 粒度精确化（W9 注解保留）**：cordis 行级 HMR 是同 fiber 重激活、整树重组只发生在 profile 层；Nop 统一走 destroy+重建编排。热更语义取舍记录见 01 §六。

### H. 隔离域（isolate） —— ➡ 改判：非 plugin 层职责

| | dsh | nop-plugin（反转后） |
|---|---|---|
| 机制 | `ctx.isolate(name)`：服务名级隔离（同一名字不同 realm 解析到不同实现） | **plugin 层不建模** |

**改判理由**："同一逻辑名在不同作用域解析到不同实现"是领域级需求（每会话 shell、每租户 provider）。原设计以实例隔离近似；反转后明确：租户差异是 `IContext` 数据、会话差异是 agent 层 realm/scope 立项范围（W9 方向确认）。plugin 层的服务获取保持"一坐标一实现"的简单语义（primary/唯一/多候选报错）。

### I. 多实例（fiber） —— ➡ 改判：拒绝于 plugin 层（non-goal）

| | dsh | nop-plugin（反转后） |
|---|---|---|
| 机制 | 一个 component 多次实例化（fiber），各独立生命周期 | **不支持**：一个定义至多一个激活（00 non-goals 6） |

**改判理由（完整论证见 00 §〇）**：dsh 的多实例服务于其客户端场景（每个 agent 会话一个 scope fiber）。Nop 服务端无状态下：(1) 租户差异 = 数据不是容器拓扑；(2) 会话差异 = 领域层状态对象；(3) 字段定制 = 编译期 Delta。没有任何一类变化要求"同一定义派生 N 个独立容器"。保留多实例会让 API 承载无消费者的机制复杂度（原 W5/W6/W8/P2-A~D 裁决链即是证据）。若未来出现真实需求，在需求所在领域层立项（01 §九 第 8 条）。

### J. 注册原语全集 —— ✅ 支持（映射为 plugin 层能力）

| dsh 原语 | Nop 对应 |
|---|---|
| `Service` 子类 `super(ctx,name)` 自注册 | beans.xml bean 定义，激活时创建（声明式） |
| `ctx.provide(name, value)` 注册服务 | 裁决保留：scope 不提供动态注册（静态装配下无法被同容器消费）；经 beans.xml 静态声明或外部注册表 |
| `ctx.accessor(name, {get,set})` 计算属性 | bean 属性（声明式） |
| `ctx.set(name, value)` 覆盖已提供服务 | Delta 覆盖（`x:override`）（语义分层注保留：cordis set 是运行时单写者改值，x:override 是结构层覆盖，仅在意图上对应） |
| `ctx.on/emit/waterfall/parallel/serial` 事件 | **独立基础设施**（事件总线），不属 plugin 范围（§一） |

### K. apply(ctx) 参数传递 —— ✅ 支持（IPluginActivator 同构）

dsh 主通道 `apply(ctx, config)` ↔ Nop `IPluginActivator.activate(scope, config)` 双参数对齐（01 §四）。参数传递 vs 字段注入的架构选择不变（避免 SpringBatch 式成员变量保存 context）。

## 四、差距总结

| 差距项 | 严重度 | 说明 |
|---|---|---|
| per-agent 工具/技能可见性合成（shadowing/限制交集） | 中 | **agent 层职责**（R 裁决后从 plugin 差距表移出）；过滤型可见性已由 tag 系统承接（activeTags/denyTags），plugin 只提供 bean 贡献基座 |
| 同名服务按作用域多实现选择（realm 槽位）+ 分层近遮蔽远（ScopedLayers） | 中 | **agent 层 open handoff（接收方未立项）**：tag 系统只覆盖名字集合的过滤/拒绝，不覆盖 isolate 槽位解析与分层合成；在 nop-ai-agent 立项前，本项为已知能力空缺而非已解决问题 |
| 服务名级响应式依赖（inject 语义） | 低 | **有意不做**（R 终裁，01 §五）：Nop 组合坐标已由 DeltaLoader 追踪，重复建设无消费者 |

> 原 W9 注解中"instanceKey + parent 链正好构成三者的基座"的表述**废止**（2026-08-22）：instanceKey/parent 已随多实例删除；agent 层基座是其自身 session 树与 tag 可见性系统，不依赖 plugin 实例机制。

**无 P0 级差距**：dsh 对 plugin 的核心用法中，凡属"服务端平台合理需求"的部分（A/B/C/D/F/G/J/K）均有对应机制；E/H/I 属客户端 harness 特有需求，明确划归 agent 层或拒绝。

## 五、Nop 相对 dsh 的优势

1. **结构层节点级 Delta**：plugin.xml 是完整 XDSL，节点级定制 + 结构化逆元；dsh patch 是配置行级。
2. **结构层 + 运行时层双可逆**：Delta 逆元（`x:override`）+ effect 逆元（IPluginScope）；dsh 只有后者。
3. **加载/激活分离 + loader 被动模式**：结构变更自动失效重算静态定义；dsh 激活嵌在加载流程（inject 驱动）。
4. **服务获取类型安全**：强类型 + 激活态绑定代理（INACTIVE 快速失败）；dsh 弱类型 ctx key。
5. **定位清晰（新增）**：plugin 只做粗粒度引入与门控，细粒度定制归编译期 Delta——两层机制各司其职，不存在"dsh patch 做不到字段级定制"的能力上限问题（dsh 插件内部是命令式代码没有坐标，Nop 插件定义本身是结构）。

## 六、结论

dsh 对 plugin 的用法在 Nop 定位下的映射：

| dsh 用法 | Nop 对应 | 判定 |
|---|---|---|
| 插件化/可替换 | plugin 定义 + 激活态 | ✅ |
| 可逆副作用 | IPluginScope（绑定激活） | ✅ |
| 层叠配置 | plugin.xml Delta（更细） | ✅ |
| 能力接缝 | 接口 + 多实现 + getService | ✅ |
| 依赖声明 | requires/if-property + reconcile（纯插件级） | ✅（坐标不同，有意为之） |
| HMR | reloadPlugin | ✅ |
| apply(ctx, config) 参数传递 | activate(scope, config) | ✅ |
| per-agent scope / shadowing | nop-ai-agent session/contribution 机制 | ➡ agent 层 |
| isolate/realm | agent 层 / IContext 数据 | ➡ agent 层 |
| 多实例 fiber | 无对应物 | ❌ non-goal（认识论边界） |
| 服务级 inject | 无对应物 | ❌ non-goal（loader 已覆盖） |

事件总线、session/turn/inject 等 dsh 能力仍是独立基础设施或 agent 框架层，不构成 plugin 差距。
