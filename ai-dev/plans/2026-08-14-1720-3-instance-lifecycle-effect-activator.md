# 3 实例级生命周期 + effect + activator（W3）

> Plan Status: active
> Mission: nop-plugin-enhancement
> Work Item: W3 实例级生命周期 + effect + activator
> Last Reviewed: 2026-08-14
> Review Consensus: 2026-08-14（独立 reviewer 审查达成共识：无 Blocker，advisory 已全部并入）
> Source: `ai-dev/design/nop-plugin/01-architecture-baseline.md`（§三 实例级状态机、§四 IPluginScope、§7.2/§7.4/§7.5）；`ai-dev/design/nop-plugin/03-coeffect-and-agent-example.md`（agent 组装示例）；`ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（Stage 3 + Cross-cutting 待裁决点 P2-C）
> Related: `ai-dev/plans/2026-08-14-1720-2-definition-lifecycle-dual-track.md`（前置，提供定义级状态机）

## Purpose

把多实例能力落地：一个 LOADED 定义可派生 N 个独立实例（createInstance/destroy/activate/deactivate 全链路），每实例独立 scope/effect/配置域；activator 以参数传递方式激活（`activate(scope, config)`），返回值自动注册为 effect；unload 守卫（有实例不可 unload）生效。这是 W4/W5/W6 的全部前置。

## Current Baseline

- W1 产出：`IPluginInstance`/`IPluginScope`/`IPluginActivator`/`IPluginContext`/`Disposable`/`InstanceState` API 契约（javadoc 语义即设计 §7.2/§7.4/§7.5/§7.6）。
- W2 产出：定义级状态机（LOADED 持有静态定义）、双轨来源路由、`isStateMachineAware` 双路径、`AbstractPlugin` 兼容路径保留旧 start/stop、aware 路径 load 不建子容器（子容器创建移入本 plan）。
- 平台已有实现载体：`BeanContainerImpl.setConfigProvider`（per-instance 独立 `IConfigProvider` 注入）、`AppBeanContainerLoader.loadFromResource(id, resource, parent)`/`BeanContainerImpl.buildNewInstance`、`@PostConstruct`/`ILifeCycle`/`<ioc:destroy>` 清理链（roadmap "Framework / platform reuse" 表）。
- `IPluginManager` 无 createInstance/destroyInstance；`AbstractPlugin.doStart` 的容器创建是 start 时一次性（W2 已剥离到本 plan 接管）。
- 待裁决点 P2-C（updateConfig × 实例合并视图刷新）在 W3/W5 裁定，本 plan 处理实例侧部分。

## Goals

- `PluginInstanceImpl`：固定实例对象（deactivate 后实例对象保留、可再 activate）；destroy 只销毁内部；同 key 重复 createInstance 抛明确异常；per-instance 生命周期 in-flight 单飞串行化（activate/deactivate 不并发）。
- 实例配置域：per-instance `IConfigProvider`（全局 AppConfig + 实例配置的合并视图，子覆盖父语义用于 W6 父级，本 plan 为全局+实例两层）；注入实例子容器；`instance.getConfig()` 任何态可读；不写全局配置（不调 `AppConfig.assignConfigValue`）。
- `PluginScopeImpl`：`effect`/`effects`/`close`（LIFO、幂等、close 后注册抛异常、quiescence 可断言）+ `getService`/`getServices`（多候选规则：primary→唯一→多候选抛异常，真实返回 bean）。
- activator 调用链：实例化子容器 → 定位 activator bean → `activate(scope, config)` 双参数 → 返回值非 null 自动 `scope.effect(returned)`；deactivate→activate 重跑 activator；deactivate 顺序 = 先 `scope.close()` 再子容器 stop。
- unload 守卫：unload 定义前须 destroy 全部实例（有实例抛异常）——本 plan 落地并测试正路径。
- P2-C 裁决落地（实例侧）：定义级 updateConfig 刷新实例合并视图（ACTIVATED 热应用 / DEACTIVATED 缓存待激活应用）。

## Non-Goals

- `instance.getService` 生命周期绑定代理（INACTIVE 快速失败代理）——W4（本 plan 只实现多候选规则解析，返回真实 bean，代理包装由 W4 接管）。
- coeffect spec 求值 + `IPluginContext.reconcile()`——W5（本 plan 只落地实例 registry；`IPluginContext` 的 reconcile 语义在 W5 实现前不提供空壳实现，详见 Deferred 节）。
- parent 层级实例化（createInstance(parent) 的服务查找沿链回退/级联 destroy/配置层叠）——W6。
- HMR reloadPlugin——W6。
- per-instance invokeCommand 路由之外**不存在其他命令路由语义变更**（路由到本实例子容器并复用现有命令 bean 分发是本 plan 的 in-scope 实现，非 W4 移交项——roadmap Stage 4 的 W4 交付物以 getService 代理为主，W4 plan 不再重复实现 per-instance 命令路由）。
- `PluginClassLoader` 类隔离改动。

## Scope

### In Scope

- 实例对象与生命周期：`PluginInstanceImpl`（activate/deactivate/destroy）、in-flight 串行化、重复 key 异常。
- 实例配置域：per-instance `IConfigProvider` 合并视图、getConfig 任何态可读、P2-C 裁决。
- `PluginScopeImpl`：effect 机制 + getService/getServices 多候选规则。
- activator 调用链与返回值自动 effect 注册。
- manager 实例管理入口：createInstance/destroyInstance/getInstance/getInstances（§7.3）。
- unload 守卫（有实例抛异常）及其正路径测试。
- 测试：quiescence 断言、多实例隔离、重复 key、unload 守卫、activator 参数传递、配置域合并视图。

### Out Of Scope

- getService 生命周期代理（W4）、coeffect/reconcile（W5）、parent/HMR（W6）、SHA256（W7）。
- 实例级 coeffect spec 求值（W5；本 plan 的 activate 直接执行，不做条件判定）。

## Execution Plan

### Phase 1 - 实例配置域 + P2-C 裁决

Status: planned
Targets: `nop-plugin-manager`（实现层）

- Item Types: `Fix | Decision`

- [ ] 实现实例配置域：per-instance `IConfigProvider`（全局配置 + 实例配置的合并视图）；实例子容器经 `BeanContainerImpl.setConfigProvider` 注入该 provider；全程不调用 `AppConfig.assignConfigValue`（兼容路径 W2 已隔离）。
- [ ] **容器接线方式钉死**（避免 provider 静默丢失）：实例子容器经 `AppBeanContainerLoader.loadFromResource(...)` 创建（返回可转 `BeanContainerImpl` 的实例），**转 `BeanContainerImpl` 后先 `setConfigProvider(instanceProvider)` 再 `start()`**；**禁止用 `buildNewInstance` 派生实例容器**（`BeanContainerImpl.buildNewInstance` 不传播自定义 provider，会静默回落到全局 `AppConfig.getConfigProvider()`——配置域空洞陷阱；该 API 仓内零调用方）。
- [ ] 实例 provider 形态钉死：**委托包装器**（读路径 + `subscribeChange` 委托全局 provider，实例键覆盖全局值），不得直接用 `SimpleConfigProvider`/`AbstractConfigProvider` 子类（其 `subscribeChange` 返回 null，实例容器内 `@r-cfg` 式响应式 bean 装配会触发空订阅清理）。
- [ ] `instance.getConfig()`：返回实例合并视图快照（或等价可读形式），ACTIVATED/DEACTIVATED 均可读（实例级 coeffect 依赖此语义，W5 消费）。
- [ ] P2-C 裁决：定义级 `updateConfig(config)` 语义定案——(a) DEACTIVATED 实例缓存配置、下次 activate 应用；(b) ACTIVATED 实例热应用（刷新合并视图定义默认部分，且经委托 provider 触发变更通知，非仅改快照）；裁决结果记录于 plan 的 Closure 或 daily log。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 实例子容器持有独立 config provider（测试断言：实例内配置读取命中合并视图值；全局 `AppConfig` 未被写入——配置值不出现在全局 config）。
- [ ] `getConfig()` 在 DEACTIVATED 态仍可读（Phase 5 测试断言；本 phase 至少完成代码路径）。
- [ ] P2-C 裁决已记录；ACTIVATED 热应用路径经委托 provider 真实触发变更（非仅改快照）。
- [ ] **无静默跳过**：updateConfig 的 ACTIVATED 热应用 / DEACTIVATED 缓存路径均有真实实现（无空分支）；实例容器 provider 为委托包装器（subscribeChange 非 null）。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - PluginScopeImpl（effect 机制 + 服务解析）

Status: planned
Targets: `nop-plugin-manager`（实现层）

- Item Types: `Fix`

- [ ] `IPluginScope` 实现：`effect(Disposable)` 注册（返回可移除句柄）、`effects()` 可观测列表、`close()` 按 LIFO 回退（单个 disposer 抛错记录并继续）、close 幂等（重复 close 直接返回）、close 后 `effect()` 注册抛明确异常。
- [ ] `getService(Class)`/`getServices(Class)`：多候选规则——primary 优先；无 primary 时按 bean id 与接口匹配的唯一实现；多候选且无 primary 抛明确异常（不静默返回集合）；返回真实 bean（W4 前无生命周期代理包装）。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **无静默跳过**：close 后注册抛异常、多候选抛异常均真实实现（测试断言）。
- [ ] LIFO 顺序可观测（Phase 5 测试：注册 d1→d2，close 后回退顺序 d2→d1）。
- [ ] `effects()` 清空可达（quiescence 断言基础）。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - PluginInstanceImpl 生命周期 + manager 实例管理

Status: planned
Targets: `nop-plugin-manager`（实现层）、`../../nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/IPluginManager.java`（§7.3 方法扩展）

- Item Types: `Fix`

- [ ] `IPluginInstance` 实现：`getInstanceKey`/`getState`/`getScope`（仅 ACTIVATED 返回、否则 null）/`activate`/`deactivate`/`destroy`/`invokeCommand`(Async)（路由到本实例子容器命令 bean，复用现有分发）。
- [ ] 生命周期语义：activate = 实例化子容器 + 注册 effect；deactivate = **先 `scope.close()`（LIFO 回退）再子容器 stop**，实例对象保留；destroy = 回退 + 从定义移除；重复 createInstance 同 key 抛明确异常；per-instance in-flight 单飞串行化（同实例 activate/deactivate 不并发；并发重复 activate 幂等返回既有实例）。
- [ ] `IPluginManager` 扩展：`createInstance(pluginId, instanceKey, config, parent)`（本 plan parent 传 null 亦可）、`destroyInstance(pluginId, instanceKey)`、`getInstance(pluginId, instanceKey)`、`getInstances(pluginId)`（§7.3）；**实例 registry 的唯一持有者 = 定义持有类**（W2 已在其上建 registry 骨架；W5 的 `IPluginContextImpl` 以同一 registry 为数据源，不许另起第二份 registry）。
- [ ] **W2 移交处置**：W2 阶段定义持有类的 `start/stop` 为显式失败（createInstance 未落地）；本 plan 落地 createInstance 后，定义持有类与 `AbstractPlugin` 的 `start/stop` 收敛为 §7.1 语义（start = load + createInstance(默认 key)；stop = destroyInstance + unload），不再抛临时失败异常——测试覆盖 start 的默认 key 实例化。
- [ ] unload 守卫：unload 定义前检查 `getInstances()` 非空 → 抛明确异常（含实例 key 参数）；registry 空 → 正常 unload（W2 行为保留）。
- [ ] **uber jar 轨 createInstance 的边界裁定**：本 plan 的实例链路（子容器/activator/scope）以 VFS 轨（plugin.xdef 定义，含 activator 载体）为测试覆盖对象；uber jar 轨（plugin.json 定义，无 plugin.xdef/activator 载体）的实例化路径记为显式 successor 项（W4/W7 评估），不在本 plan 静默承诺——`createInstance` 对 jar 轨定义若被调用而路径未就绪，抛明确异常（No Silent No-Op），不得返回半成品实例。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] deactivate 顺序真实实现：scope.close 先于子容器 stop（代码路径或测试断言：effect 在 bean destroy 前回退）。
- [ ] destroy 后实例从 registry 移除（getInstance 返回 null）。
- [ ] 重复 key createInstance 抛异常、unload 守卫抛异常（Phase 5 测试断言）。
- [ ] **接线验证**：manager 的 createInstance → 定义级 LOADED 校验（未加载的定义 createInstance 明确失败）→ 实例 registry 注册 → 返回实例，调用链连通。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - activator 调用链

Status: planned
Targets: `nop-plugin-manager`（实现层）

- Item Types: `Fix`

- [ ] activate 流程：实例化子容器 → 按定义声明的 `activator`（bean-name）定位 activator bean → `activator.activate(scope, config)`（scope + 合并视图 config 双参数传入，禁止字段注入 scope）→ 返回值非 null 自动 `scope.effect(returned)`。
- [ ] **scope 生命周期定案**：每次 activate 使用**全新的 scope 实例**（close 后 `effect()` 抛异常，旧 scope 不可复用——设计 §7.4 "激活期作用域句柄"支持 per-activation 新实例）；deactivate 关闭该次激活的 scope。
- [ ] 重激活语义：deactivate 后 activate 重新执行 activator（scope 全新，effect 需重新注册）；并发重复 activate 幂等（in-flight 单飞，不重跑）。
- [ ] 激活失败处理：实例化/activator 抛错 → 实例回退到 DEACTIVATED 并记录错误（不残留半激活实例）；错误带实例 key 参数。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **接线验证**：activator bean 的 `activate(scope, config)` 确实被实例激活流程调用（测试断言：activator 内用 scope.getService 取 bean 成功、config 为合并视图）。
- [ ] 返回值自动注册生效（Phase 5 测试：activate 返回的 disposer 在 deactivate 时被回退）。
- [ ] **无静默跳过**：激活失败路径明确置回 DEACTIVATED 并抛/记录错误，无空 catch。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 5 - 测试补全

Status: planned
Targets: `nop-plugin-manager/src/test/`、`nop-plugin-support/src/test/`（如新建）

- Item Types: `Proof`

- [ ] quiescence 测试：注册多个 effect（含 activator 返回值），deactivate/destroy 后 `effects()` 清空（LIFO 顺序断言）。
- [ ] 多实例隔离测试：同定义派生 agent-1/agent-2 两实例，各自独立 scope/effect/配置域（一个实例的 effect/配置变更不影响另一个）；实例级 getConfig 差异化。
- [ ] 生命周期状态测试：ACTIVATED→DEACTIVATED→ACTIVATED 往返（deactivate 后实例对象保留、activate 重跑 activator）；destroy 从 registry 移除。
- [ ] 重复 key / unload 守卫 / 未加载定义 createInstance 的异常测试（断言错误码/消息含 key 参数）。
- [ ] activator 参数传递测试：scope + config 合并视图正确传入；返回值 disposer 自动注册。
- [ ] 配置域合并视图测试：实例配置覆盖全局（合并视图值断言，含**仅实例有而全局无的键**——证明 provider 非全局回落）；`AppConfig` 全局无污染断言。
- [ ] 并发重复 activate 幂等测试（in-flight 单飞）。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 上表每类测试存在且断言真实行为（非仅"不抛异常"）。
- [ ] **端到端验证**：从 manager `createInstance` 入口 → 子容器创建 → activator 调用 → scope 取服务 → deactivate → quiescence → destroy 移除，完整链路有测试覆盖。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 退出码 0。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 多实例全链路成立：一个定义 N 个独立实例，各自 scope/effect/配置域隔离（测试证明）。
- [ ] effect 机制成立：LIFO 回退、close 幂等、close 后注册抛异常、quiescence 可断言。
- [ ] activator 参数传递 + 返回值自动注册成立（测试证明）。
- [ ] 实例配置域成立：per-instance provider、getConfig 任何态可读、不写全局 AppConfig。
- [ ] unload 守卫成立：有实例时 unload 抛异常（测试证明）。
- [ ] P2-C 裁决已记录并落地。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 项。
- [ ] No owner-doc update required（使用文档 W7 统一同步）。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）createInstance → 子容器 → activator → scope 调用链在运行时连通（端到端测试），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [ ] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### IPluginContext 实现整体推迟到 W5

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §7.6 的 `IPluginContext` = 实例 registry + coeffect reconcile 两件事；W3 落地 registry（由 manager/定义持有），reconcile（含环检测/配置订阅）是 W5 交付物。W3 不提供空壳 `IPluginContextImpl`（避免 No Silent No-Op）；W5 实现时以 manager 的 registry 为数据源。
- Successor Required: `yes`
- Successor Path: W5 coeffect + reconcile（roadmap Stage 5）

### getService 生命周期绑定代理

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §7.2 的 INACTIVE 快速失败代理是 W4 交付物；W3 的 `getService`/`getServices` 已实现多候选规则并返回真实 bean，W3 阶段实例只在其生命周期内被服务（activator/命令分发），代理缺失不构成 contract gap——W4 在既有解析之上加包装。
- Successor Required: `yes`
- Successor Path: W4 getService 生命周期代理 + per-instance 命令路由（roadmap Stage 4）

## Non-Blocking Follow-ups

- per-instance 命令路由（invokeCommand 到本实例子容器）随 W4 的 getService 代理一起做一次语义复核（多实例命令隔离测试）。
- `IPlugin.invokeCommand` 兼容规则（仅实例数=1 路由、多实例抛异常）属 W4，本 plan 不提前实现。

## Closure

Status Note: （完成时填写）
Completed: （完成时填写）

Closure Audit Evidence:

- Reviewer / Agent: （完成时填写）
- Evidence: （完成时填写）

Follow-up:

- no remaining plan-owned work（完成时确认）

## Optional Sections

## Risks And Rollback

- 子容器创建从 AbstractPlugin 移入实例激活：若存量 AbstractPlugin 子类依赖"start 时容器已存在"的行为，W2 兼容路径已保留旧语义；aware 路径新代码不受影响。
- per-instance IConfigProvider 若与全局 bean 条件装配（`<ioc:condition>` 走全局）语义混淆：设计已明确定义级条件读全局、实例级条件读实例配置域，W5 消费时复核。
- in-flight 串行化若与异步 activate/deactivate（CompletionStage）交互出竞争：以单飞队列（per-instance 串行 executor/锁）实现，测试覆盖并发重复 activate。
