# nop-plugin 增强设计：愿景

**日期**：2026-08-14（经独立审查第一轮修订）；**2026-08-22 定位反转修订**（讨论记录第 17 轮，用户裁决）
**范围**：`nop-core-framework/nop-plugin`（api / manager / support）；plugin 专属 XDef（`/nop/schema/plugin/plugin.xdef`）——plugin 有自己的结构层 schema，**不改 beans.xdef**
**状态**：定位反转已定稿（plan-first，重构实施待 plan audit）
**灵感来源**：Cordis _A Programming Paradigm for Spatiotemporal Composability_（temporal/spatial composability、revertible effects、reactive coeffects）；可逆计算理论对编译期/运行时两个结构空间的划分（见 `ai-dev/articles/dsh-architecture-from-reversible-computation.md` §4.6 认识论边界）

---

## 〇、定位反转（2026-08-22，本设计的根本裁决）

**plugin 只做粗粒度引入与激活门控；细粒度定制归编译期结构空间，请求级变化归上下文数据。**

Nop 平台已经通过模型加载器（DeltaLoader + x-extends）提供了编译期结构空间的完整可逆计算支持——定制可以在任意 DSL 模型的字段级发生，且 loader 被动失效追踪天然覆盖"结构变了自动重算"。同时 Nop 是服务端平台，服务无状态、请求级差异以 `IContext`（tenantId/userId/sessionId）数据流经稳定结构。

据此按认识论边界重新划定 plugin 职责（文章 §4.6："凡是在结构空间中可确定的内容，就提到结构空间；凡是在运行期才可确定的内容，才留在运行时并用可逆机制管理"）。**定位声明（带两个限定）**：

1. **凡进入 plugin 层坐标的内容均须加载期可静态声明**——这是本层的准入定义，不是理论推论。运行期才知道内容的注册（如 MCP 式激活后远端发现工具）不由 plugin 层承载：由领域层的外部注册表管理（plugin 的 effect 只保证资源回退，不提供坐标级差量管理）；租户级模型差异由 nop-dyn 租户 delta 层在模型加载时解析。
2. **"加载时可知"指 per-load 而非 per-boot**——每次模型加载时可知即可（loader 被动失效重算），不要求进程启动时全部就绪。

在此定位下，插件层的注册拓扑在加载期固定；运行时变化收敛为两类：激活态迁移（门控布尔量）+ effect 登记的可逆资源（定时器/监听器等仍是结构性变更，由 scope 账本管理）。

| 变化类型 | 归属层 | plugin 是否参与 |
|---|---|---|
| 字段级定制（改某 bean 属性、删某工具） | 编译期 Delta（x:extends/x:override），已有 | 否 |
| 租户/会话差异（不同租户不同配置、不同 agent 不同工具视图） | `IContext` 数据 / 领域层 session 模型（nop-ai-agent tag 可见性系统、ContributionRegistry 等） | 否 |
| **粗粒度能力引入**（远程 jar 分发、类隔离、整组 bean 贡献） | **plugin** | **是** |
| **激活门控**（依赖其他 plugin 已激活、配置开关） | **plugin（coeffect 定义级条件）** | **是** |
| 运行期才知道内容的注册 | 领域层外部注册表（effect 保资源回退，不做坐标化差量） | 否 |

推论（推翻 2026-08-14 版目标中的多实例部分）：**多实例派生（fiber 式 IPluginInstance）从设计中移除**——服务端平台上进入 plugin 层的内容全部加载期可静态声明，不存在需要 N 实例派生的注册内容；**依赖只声明在插件级**——服务级装配本属 IoC/结构空间既有语义（ref/collect-beans），插件依赖的存在理由只有激活门控与顺序。

dsh 需要 fiber/scope/realm 是因为它是客户端交互式 harness：每个 agent 会话的注册内容依赖"这个 agent 是谁"这一运行期信息。Nop 无此需求；agent 场景的 per-session 可变性由 nop-ai-agent 自己的 session/contribution 机制承担（W9 已立项该层）。

## 一、核心原则：plugin 框架与 IoC 解耦

**plugin 框架是独立概念**（生命周期、状态、effect、服务抽象），**IoC 只是内部的一种实现机制**——与 plugin 框架本身没有任何 API 关系：

```
plugin 框架（独立，API 层零依赖）
   │  IPlugin / IPluginScope / IPluginActivator / IPluginManager / IPluginContext
   │  （不引用 IoC 任何类型）
   │
   └─ 内部实现（可替换）
        可用 Nop IoC 子容器组装 bean（当前实现路径）
        也可用其他机制（未来可替换，不承诺依赖 IoC）
```

推论：
- `nop-plugin-api` 模块**零依赖**（不引用 `BeansModel`/`IBeanContainer` 等 IoC 类型），保持"最小化插件接口、不要求插件使用 Nop 平台实现"的既有定位。
- 子容器、bean destroy 都是**实现细节**，不是 API 契约；`IPluginScope` 的 effect 是 plugin 框架自己的机制。
- "改不改 nop-ioc"不是本设计的核心问题——plugin 框架不依赖 IoC 公开 API，内部实现选型不影响框架本身。

## 二、增强目标（收敛为三项）

现有 `nop-plugin` 已实现 load/unload + plugin=子容器模式。本次增强吸收 Cordis 思想中与 Nop 定位匹配的部分，保留三项能力：

1. **加载/激活两态分离 + 插件级依赖门控**：plugin 状态机 `UNLOADED → LOADED ⇄ ACTIVATED`。加载只产出静态定义（解析 plugin.xml/jar、不建子容器）；激活才实例化子容器并执行 activator。plugin 声明激活条件（`requires` 依赖其他 plugin 已激活、`if-property` 配置项为 true）——条件变化时 reconcile 评估，自动激活/去激活，无需重新加载。**一个定义至多一个激活实例。**
2. **Revertible effects 系统化**：`IPluginScope` 是 plugin 框架自己的 effect 机制——`effect(disposable)` 注册可逆操作、`effects()` 可观测、`close()` LIFO 回退。去激活时回退全部 effect、达 quiescence。scope 绑定**每次激活**而非实例。**不承诺观测 IoC 内部**（子容器 destroy 是实现细节）。
3. **HMR**：plugin 定义变更 → loader 依赖追踪 → 自动 reload（去激活 → unload → load → 重新门控激活），无需重启宿主。

**已删除的目标**（2026-08-22 反转）：~~目标 5 多实例（fiber）~~、~~目标 3 Reactive coeffect 中的实例级条件部分~~——见 §〇 与 non-goals 6/7。原目标 4（HMR）保留为新目标 3；原目标 3 的定义级部分并入新目标 1。

## 三、不可违反的约束

1. **plugin API 与 IoC 解耦**：`nop-plugin-api` 零依赖 IoC 类型；公开接口不引用 `BeansModel`/`IBeanContainer`。IoC 仅存在于实现层（可替换）。
2. **结构层保持节点级 Delta 优势**：plugin 定义继续走 XDSL（plugin.xdef + `<beans>` 复用 bean 定义模型），不退回到配置行级。这是相对 Cordis 的核心优势，不可放弃。
3. **loader 被动模式**：加载（loader 输出静态定义）与激活（实例化）保持两段分离，loader 不主动驱动运行时——结构变更自动失效重算，运行时是独立消费者。
4. **观测等价而非完全恢复**：unload/deactivate 承认外部副作用（已发请求、已写文件、已 spawn 子进程）不可逆，追求 quiescence（静默等价）。与 Cordis 论文 §3.3.2 的观测等价立场一致。
5. **插件粒度唯一性（2026-08-22 新增）**：一个 plugin 定义至多一个激活（无 N 实例派生、无 instanceKey、无 parent 实例层级）；依赖只声明在插件级（`requires` 引用 plugin id），不提供服务级依赖声明。若未来出现真实的容器级多作用域需求，应在需求所在领域层（如 agent session）立项，不在 plugin 通用层预置机制。

## 四、Non-goals

1. **不实现 Cordis 式形式化演算/元理论**（Preservation/Confluence 定理证明）：吸收工程思想，不照搬 PL 理论形式化。
2. **不引入新的差量空间**：结构层沿用 XDSL/XDef，不为 plugin 另造差量坐标系。
3. **不替代 `<ioc:condition>` / `<ioc:collect-beans>`**：build 时条件装配保留，coeffect 条件激活是运行时补充，二者并存。
4. **不做跨进程 plugin 编排**：限单 JVM 内的 plugin 生命周期。远程 plugin 仅复用 `HttpPluginResourceResolver`（下载 uber jar + SHA256 校验），不涉及跨进程协调。
5. **realm 共享数据层不做**：不引入 `resolveKey` 式共享数据机制。
6. **不做多实例/fiber 派生（2026-08-22 新增）**：删除 `createInstance(pluginId, instanceKey, config, parent)` 及 instanceKey/parent 层级/实例配置域/实例级 coeffect/级联销毁。理由见 §〇：服务端无状态下，租户差异是数据（IContext）、会话差异是领域层状态对象、字段定制是编译期 Delta——没有一类变化要求"同一定义派生 N 个独立容器"。dsh 的 fiber 对应物在 Nop 定位下是空集（对照记录见 `04-interface-comparison.md` §2.4）。
7. **不做服务级依赖声明（2026-08-22 新增）**：关闭此前 W8 注解预留的 `requires-service="IModelAdapter"` 扩展点。服务装配属 IoC/结构空间（bean ref、`<ioc:collect-beans>`、宿主容器可见性，加载期静态解析）；插件内服务变更经定义重载（HMR）重新门控即可，不需要服务级运行时 reconcile。dsh 的 inject 是服务名级，是因为服务坐标就是它的组合坐标系；Nop 的组合坐标是 VFS 路径 + DSL 节点，已由 DeltaLoader 被动追踪覆盖。

## 五、成功标准

1. plugin 可处于"已加载未激活"态；定义级 coeffect 条件（requires/if-property）变化时运行时激活/去激活，无需 reload。
2. `IPluginScope` 管理的 effect 列表可观测；deactivate/unload 后列表清空、达 quiescence，可被测试断言。
3. plugin 定义变更后自动 reload，宿主与其他 plugin 不受影响。
4. `nop-plugin-api` 模块零依赖 IoC（编译期可验证：api 包不 import 任何 `io.nop.ioc`/`io.nop.xlang` 类型）。
5. API 表面不含任何实例派生概念（无 createInstance/instanceKey/parent 实例层级）；依赖声明仅存在插件级形态（plugin.xdef 的 requires/if-property）。

## 六、设计收敛路径

`00-vision`（本文）→ `01-architecture-baseline`（架构决策与接口契约）→ `ai-dev/plans/`（拆分重构实施计划）→ 实施 → 关键结论同步到 `docs-for-ai/`。

> **反转的实施影响**：2026-08-14 版设计已按 W1-W7 落地（含多实例）。本次反转产生一轮重构（收缩公共 API、合并两层状态机为单层），work items 见 `ai-dev/backlog/nop-plugin-enhancement-roadmap.md` R1-R4；`nop-plugin-api` 为跨模块公共 API（Protected Area plan-first），重构计划须经独立 audit 后实施。
