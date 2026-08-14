# nop-plugin 增强设计：愿景

**日期**：2026-08-14（经独立审查第一轮修订）
**范围**：`nop-core-framework/nop-plugin`（api / manager / support）；新增 plugin 专属 XDef（`/nop/schema/plugin/plugin.xdef`）——plugin 有自己的结构层 schema，**不改 beans.xdef**（决策记录见 `01-architecture-baseline.md` §7.6）
**状态**：草案（plan-first，待 plan audit 后实施）
**灵感来源**：Cordis _A Programming Paradigm for Spatiotemporal Composability_（temporal/spatial composability、revertible effects、reactive coeffects）

---

## 一、核心原则：plugin 框架与 IoC 解耦

**plugin 框架是独立概念**（生命周期、状态、effect、服务抽象、隔离），**IoC 只是内部的一种实现机制**——与 plugin 框架本身没有任何 API 关系：

```
plugin 框架（独立，API 层零依赖）
   │  IPlugin / IPluginInstance / IPluginScope / IPluginManager / IPluginContext
   │  （不引用 IoC 任何类型）
   │
   └─ 内部实现（可替换）
        可用 Nop IoC 子容器组装 bean（当前实现路径）
        也可用其他机制（未来可替换，不承诺依赖 IoC）
```

推论：
- `nop-plugin-api` 模块**零依赖**（不引用 `BeansModel`/`IBeanContainer` 等 IoC 类型），保持"最小化插件接口、不要求插件使用 Nop 平台实现"的既有定位。
- 子容器、bean destroy、subscription cancel 都是**实现细节**，不是 API 契约；`IPluginScope` 的 effect 是 plugin 框架自己的机制，不承诺观测 IoC 内部。
- "改不改 nop-ioc"不是本设计的核心问题——plugin 框架不依赖 IoC 公开 API，内部实现选型不影响框架本身。

## 二、增强目标

现有 `nop-plugin` 已实现 load/unload + plugin=子容器模式。本次增强吸收 Cordis 思想，补齐五项能力：

1. **加载/激活两态分离**：plugin 引入 LOADED / ACTIVATED / DEACTIVATED 状态。加载只产出静态定义，激活才实例化——条件满足时激活、不满足时去激活，无需重新加载。
2. **Revertible effects 系统化**：`IPluginScope` 是 plugin 框架自己的 effect 机制——`effect(disposable)` 注册可逆操作、`effects()` 可观测、`close()` LIFO 回退。实例销毁时回退全部 effect、达 quiescence。**不承诺观测 IoC 内部**（子容器 destroy 是实现细节）。
3. **Reactive coeffect 条件激活**：plugin 声明激活条件（依赖其他 plugin 已激活、配置项为 true）——**定义级**决定 plugin 能否派生实例，**实例级**决定该实例是否激活（不同实例不同条件）；context 变化时 reconcile 评估，activating / deactivating / neutral。
4. **HMR**：plugin 定义变更 → loader 依赖追踪 → 自动 reload，无需重启宿主。
5. **多实例（fiber）**：一个 plugin 定义可派生 N 个独立激活实例（多租户/多 agent），每实例独立 scope/effect/配置域，支持 `instanceKey` 与 `parent` 层级实例化（subagent）。

## 三、不可违反的约束

1. **plugin API 与 IoC 解耦**：`nop-plugin-api` 零依赖 IoC 类型；`IPlugin`/`IPluginInstance`/`IPluginScope` 等公开接口不引用 `BeansModel`/`IBeanContainer`。IoC 仅存在于实现层（可替换）。
2. **结构层保持节点级 Delta 优势**：plugin 定义继续用 beans.xml（完整 XDSL，`_BeansModel extends AbstractDslModel`），不退回到配置行级。这是相对 Cordis 的核心优势，不可放弃。
3. **loader 被动模式**：加载（loader 输出静态定义）与激活（实例化）保持两段分离，loader 不主动驱动运行时——结构变更自动失效重算，运行时是独立消费者。
4. **观测等价而非完全恢复**：unload/deactivate 承认外部副作用（已发请求、已写文件、已 spawn 子进程）不可逆，追求 quiescence（静默等价），不奢求信息守恒的精确复原。与 Cordis 论文 §3.3.2 的观测等价立场一致。

## 四、Non-goals

1. **不实现 Cordis 式形式化演算/元理论**（Preservation/Confluence 定理证明）：本设计吸收 Cordis 的工程思想，不照搬其 PL 理论形式化。
2. **不引入新的差量空间**：结构层沿用 XDSL/XDef，不为 plugin 另造差量坐标系。
3. **不替代 `<ioc:condition>` / `<ioc:collect-beans>`**：build 时条件装配保留，coeffect 条件激活是运行时补充，二者并存。
4. **不做跨进程 plugin 编排**：本设计限单 JVM 内的 plugin 生命周期。远程 plugin 仅复用现有 `HttpPluginResourceResolver`（下载 uber jar），不涉及跨进程协调。
5. **realm 共享数据层不做**：不引入 `resolveKey` 式共享数据机制。Cordis 的 `ctx.isolate` 是服务名级隔离（服务实现选择），不是共享数据 key 解析，不映射为本设计的 API。

## 五、成功标准

1. plugin 可处于"已加载未激活"态，定义级 coeffect 条件变化时运行时激活/去激活，无需 reload。
2. `IPluginScope` 管理的 effect 列表可观测；unload/deactivate 后列表清空、达 quiescence，可被测试断言。
3. plugin 定义变更后自动 reload，宿主与其他 plugin 不受影响。
4. `nop-plugin-api` 模块零依赖 IoC（编译期可验证：api 包不 import 任何 `io.nop.ioc`/`io.nop.xlang` 类型）。
5. 一个 plugin 定义可派生 N 个独立激活实例，各自独立 scope/effect/配置域；实例级 coeffect 条件可差异化激活/去激活；subagent 经 parent 层级实例化。

## 六、设计收敛路径

`00-vision`（本文）→ `01-architecture-baseline`（架构决策与接口契约）→ `ai-dev/plans/`（拆分实施计划）→ 实施 → 关键结论同步到 `docs-for-ai/`。
