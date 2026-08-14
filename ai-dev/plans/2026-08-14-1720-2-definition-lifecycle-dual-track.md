# 2 定义级生命周期 + 双轨来源 + 旧插件兼容（W2）

> Plan Status: draft
> Mission: nop-plugin-enhancement
> Work Item: W2 定义级生命周期 + 双轨来源 + 旧插件兼容
> Last Reviewed: 2026-08-14
> Source: `ai-dev/design/nop-plugin/01-architecture-baseline.md`（§二 双轨来源、§三 定义级状态机、§7.1 兼容语义）；`ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（Stage 2）
> Related: `ai-dev/plans/2026-08-14-1720-1-plugin-xdef-and-api-layer.md`（前置，提供 plugin.xdef + API 契约）、`ai-dev/plans/2026-08-14-1720-3-instance-lifecycle-effect-activator.md`（执行顺序 3，消费本 plan 的定义级状态机）

## Purpose

把"加载即激活"的耦合拆开：`loadPlugin` 只产定义（UNLOADED→LOADED），激活语义从加载中剥离；存量第三方插件（仅 start/stop，非 state-machine-aware）无感走旧路径。双轨来源（本地 VFS `*.plugin.xml` / uber jar）按 id 类型路由到同一套定义级状态机。

## Current Baseline

- `PluginManagerImpl`（`nop-plugin-manager/.../impl/PluginManagerImpl.java`）：`loadPlugin`（:45）＝ resolver→`PluginClassLoader`→ServiceLoader 发现 IPlugin→`plugin.start(...)`（加载即激活）；`unloadPlugin`（:64）＝ `plugin.stop()` + close classloader。插件 id 目前仅 `ArtifactCoordinates`（`manager/IPluginManager.java:13`）。
- `AbstractPlugin`（`nop-plugin-support/.../AbstractPlugin.java`）：extends `LifeCycleSupport`；`start(...)`（:83）＝ 保存坐标 + **`AppConfig.assignConfigValue` 全局写入 config** + `start()`；`doStart`（:108）＝ CoreInitialization + `loadFromResource(PLUGIN_BEANS_FILE, parent=BeanContainer.instance())` 建子容器；`doStop`（:97）＝ beanContainer.stop + 按需 CoreInitialization.destroy。子容器创建在 start 时立即发生（= 激活耦合）。
- `DefaultPlugin`（support 模块）与 `GraphQLPluginCommand` 基于 AbstractPlugin。
- `IPluginManager` 现有签名 `loadPlugin(ArtifactCoordinates)/unloadPlugin(ArtifactCoordinates)/getLoadedPlugins()`；`plugin.xlib` 的 `InvokeCommand` 标签经 `nopPluginManager.loadPlugin(pluginId)` 调用（`nop-plugin-manager/src/main/resources/_vfs/nop/plugin/xlib/plugin.xlib`）。
- W1 产出（本 plan 的前置）：`plugin.xdef`（可解析 `*.plugin.xml`：requires/if-property/activator + `<beans>` 子元素）、`IPlugin` default 方法（getState/load/unload/getInstance/getInstances/isStateMachineAware）、PluginState 枚举。
- 本地 VFS plugin 定义路径尚不存在；`NopPluginConstants.PLUGIN_BEANS_FILE` 是现有 beans.xml 资源路径约定（AbstractPlugin.doStart 使用）。
- 无任何 isStateMachineAware 检测；无"已加载未激活"态可观测。

## Goals

- `PluginManagerImpl` load/unload 按 plugin.xdef 定义驱动：本地 VFS `*.plugin.xml` 与 uber jar 双轨，按 id 类型路由到同一定义级状态机（LOADED 可缓存、可重复 get）。
- 双轨统一 id 表示裁决并落地（保留现有 uber jar/Maven 坐标场景调用方兼容，含 plugin.xlib）。
- `isStateMachineAware` 检测：aware → 新状态机（load 到 LOADED，不激活）；非 aware → 兼容路径（保持旧 start/stop 语义，行为与现状逐字节等价）。
- `AbstractPlugin` 改造：`AppConfig.assignConfigValue` 全局写入仅保留在兼容路径；子容器创建从定义级剥离（移入 W3 实例激活）；aware 路径 load/unload 只管理静态定义。
- uber jar 路径经 `IPluginResourceResolver` 接线到新状态机（resolver 契约不变，调用点接入新流程）。

## Non-Goals

- 实例级生命周期（createInstance/destroy/activate/deactivate、PluginInstanceImpl、PluginScopeImpl、activator 调用链、实例配置域）——W3。
- unload 守卫的"有实例抛异常"路径（无实例可创建，守卫正路径 W3 测试；本 plan 仅保证 unload 在定义级状态机上正确执行）。
- coeffect/reconcile（W5）、parent 层级/HMR（W6）、getService 代理（W4）。
- `HttpPluginResourceResolver` SHA256 补齐（W7）。
- 不改 `PluginClassLoader` 类隔离机制（roadmap 明确"本 roadmap 不改动它"）。

## Scope

### In Scope

- `IPluginManager` 签名按双轨 id 统一（Decision 后实施），`plugin.xlib` 调用点同步。
- `PluginManagerImpl`：双轨路由、定义级状态机（LOADED 持有/缓存）、isStateMachineAware 检测、aware 走 `plugin.load(config)`、非 aware 走 `plugin.start(...)`（现状语义）。
- `AbstractPlugin`（support）：aware 路径 load/unload 静态定义管理；兼容路径保留全局 config 写入与旧 start 语义。
- 定义级实例 registry 骨架（getInstances()/getInstance(key) 返回空列表/空——W3 填充，本 plan 保证非空壳语义成立）。
- 测试：兼容路径回归、aware 定义级状态（LOADED/UNLOADED）、双轨路由接线。

### Out Of Scope

- 实例对象、effect、scope、activator 的一切实现（W3）。
- coeffect spec 求值（W5；本 plan 仅解析进定义模型，不求值）。
- HMR 依赖追踪接线（W6）。
- SHA256 校验（W7）。
- beans.xdef 修改（W1 已明确不做）。

## Execution Plan

### Phase 1 - 双轨 id 路由决策 + PluginManagerImpl 状态机化

Status: planned
Targets: `nop-plugin-manager/.../manager/IPluginManager.java`、`impl/PluginManagerImpl.java`、`_vfs/nop/plugin/xlib/plugin.xlib`

- Item Types: `Decision | Fix`

- [ ] 裁决并记录双轨 id 统一表示：按 id 类型区分 VFS 路径（本地 `*.plugin.xml`）与 Maven 坐标（uber jar）路由；约束：现有 uber jar 调用方（plugin.xlib InvokeCommand、业务代码 loadPlugin(coordinates)）不破坏；id 解析规则（前缀/类型标志/显式类型参数三选一，实现时依设计 §二 双轨表裁定并记录理由）。
- [ ] `PluginManagerImpl.loadPlugin(id)`：id 解析 → 双轨分发；VFS 轨经 `DslModelParser` + plugin.xdef 解析 `*.plugin.xml` 为静态定义；uber jar 轨保留 resolver→classloader→ServiceLoader 链路。
- [ ] isStateMachineAware 检测：aware → `plugin.load(config)`（到 LOADED，**不调用 start**）；非 aware → 现有 `plugin.start(...)` 语义（加载即激活，行为等价现状）。
- [ ] `PluginManagerImpl.unloadPlugin(id)`：aware → `plugin.unload()`（丢弃定义，注册表清空）；非 aware → `plugin.stop()`（现状）；两路都保留 classloader 关闭（如适用）。
- [ ] 定义级实例 registry 骨架：定义持有 `getInstances()`/`getInstance(key)` 的真实返回（本阶段恒空列表/空，W3 填充）——不写空壳占位。
- [ ] `plugin.xlib` InvokeCommand 调用点与新 id 签名对齐；`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 双轨路由决策已记录（plan 或 daily log）：id 类型判别方式、两轨 config 来源、VFS 轨定义缓存键。
- [ ] **接线验证**：VFS 轨真实经 plugin.xdef 解析路径产出定义（非 stub）；uber jar 轨仍经 `IPluginResourceResolver`。
- [ ] **无静默跳过**：未知/无法解析的 id 抛明确异常（错误码带 id 参数），无吞异常路径。
- [ ] 编译 + 现有 `TestPluginManager` 可运行（该测试目前为空壳，本 phase 不依赖它，Phase 4 替换为真实测试）。
- [ ] No owner-doc update required（行为变更记录在 W7 docs 同步；本 plan 无独立 owner doc）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - AbstractPlugin 改造（aware/兼容双路径）

Status: planned
Targets: `nop-plugin-support/.../AbstractPlugin.java`

- Item Types: `Fix`

- [ ] aware 路径：`load(config)` 解析 plugin.xdef 定义并持有（LOADED）；`unload()` 丢弃定义（UNLOADED）；**不再在 load 时创建子容器**（子容器创建移入 W3 createInstance）；`isStateMachineAware()` 返回 true（覆盖 default）。
- [ ] 兼容路径：`start(...)`/`stop()` 保留现状语义，`AppConfig.assignConfigValue` 全局写入仅存在于此路径；非 aware 第三方实现（只实现 IPlugin start/stop）不受影响。
- [ ] start/stop 与 load/unload 的关系收敛到设计 §7.1 语义（start = load + createInstance(默认 key)；stop = destroyInstance + unload）——W2 落地 load/unload 半边，createInstance/destroyInstance 半边由 W3 补齐，本 phase 禁止出现空壳（createInstance 未实现时以显式失败/未接线状态交付，由 W3 接续）。
- [ ] `./mvnw compile -pl :nop-plugin-support -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `AbstractPlugin` aware 路径 load 不触发子容器创建（代码审查：load 路径无 AppBeanContainerLoader 调用）。
- [ ] `AppConfig.assignConfigValue` 仅存于兼容路径（grep 定位确认）。
- [ ] 默认实现不产生行为回退：DefaultPlugin 继承链编译通过、现有语义不变。
- [ ] **无静默跳过**：aware 路径中 W3 未接续的入口（如 createInstance 语义的 start 封装）显式失败或明确未接线，不得空方法体。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - uber jar 路径接线到新状态机

Status: planned
Targets: `nop-plugin-manager/.../impl/PluginManagerImpl.java`、`classloader/PluginClassLoader.java`（只读）

- Item Types: `Fix | Proof`

- [ ] uber jar 轨加载链路接入新流程：resolver 返回本地 jar URL → `PluginClassLoader`（parent=宿主 classloader，现状）→ ServiceLoader 发现 IPlugin → 按 isStateMachineAware 分发（aware 走 load / 非 aware 走 start）。
- [ ] 确认 `PluginClassLoader` 本身零改动（类隔离机制不变；roadmap 硬约束）。
- [ ] 验证：经 resolver 的 maven 坐标 id 在 aware/非 aware 两分支下都正确到达定义级状态机。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **接线验证**：测试中用 resolver stub 提供本地 jar，ServiceLoader 加载的 plugin 正确进入 aware/非 aware 对应分支（断言分支选择依据 isStateMachineAware 返回值）。
- [ ] `PluginClassLoader.java` 无 diff（git 确认）。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 测试补全

Status: planned
Targets: `nop-plugin-manager/src/test/`、`nop-plugin-support/src/test/`（如新建）

- Item Types: `Proof | Fix`

- [ ] 替换空壳 `TestPluginManager`：兼容路径回归测试（模拟非 aware IPlugin：loadPlugin 调 start、unloadPlugin 调 stop、行为与改造前等价）。
- [ ] aware 定义级状态测试：loadPlugin 后 getState()==LOADED、getInstances() 为空、未创建任何子容器/实例；unloadPlugin 后回到 UNLOADED；重复 loadPlugin 幂等（返回同一定义）。
- [ ] 双轨路由测试：VFS 路径 id 加载本地 `*.plugin.xml`（测试资源）成功；非法 id/无法解析抛明确异常。
- [ ] 零依赖不变式回归（W1 已建检查，本 plan 改动 api 模块时重跑；本 plan 预期零命中）。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 上述四类测试存在且断言真实行为（非仅"不抛异常"）。
- [ ] 兼容路径测试证明旧语义保留（start/stop 调用序列与参数等价）。
- [ ] **端到端验证**（如适用）：从 `loadPlugin(id)` 入口经双轨加载链路到定义级 LOADED 状态的完整路径有测试覆盖；unloadPlugin 反向到 UNLOADED 有测试覆盖。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 退出码 0。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 加载与激活解耦成立：aware plugin 可处于 LOADED 且零实例/零子容器状态（测试断言）。
- [ ] 双轨来源按 id 类型路由成立（VFS/uber jar 各走正确链路，测试覆盖）。
- [ ] 存量非 aware 插件兼容：start/stop 语义与改造前等价（回归测试覆盖）。
- [ ] `AppConfig.assignConfigValue` 全局写入仅存于兼容路径。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 项。
- [ ] No owner-doc update required（使用文档 W7 统一同步）。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）aware 路径 load 与 unload 真的被 PluginManagerImpl 调用（从 loadPlugin 入口到 plugin.load 的调用链连通），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [ ] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### 定义级 coeffect 求值（requires/if-property 生效）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §五 明确 coeffect 是运行时条件激活（W5 实现 reconcile）；W2 仅需把属性解析进定义模型（W1 schema 已支持），求值/订阅/自动触发全部归属 W5，W2 定义级状态机不依赖求值结果。
- Successor Required: `yes`
- Successor Path: W5 coeffect + reconcile（roadmap Stage 5）

### unload 守卫"有实例抛异常"正路径测试

- Classification: `watch-only residual`
- Why Not Blocking Closure: W2 阶段任何定义都无法派生实例（createInstance 属 W3），守卫逻辑的"有实例"分支不可能被触发；守卫代码与正路径测试随 W3 实例落地时实现，W2 定义级 unload 语义（registry 空时正常 unload）已由 Phase 4 测试覆盖。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/2026-08-14-1720-3-instance-lifecycle-effect-activator.md`

## Non-Blocking Follow-ups

- 双轨 id 表示若未来引入第三种来源（如 Maven 仓库直接解析），路由表可扩展——当前不实施。
- `getLoadedPlugins()` 的返回语义随实例概念落地（W3）复核是否需要扩展。

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

- 双轨 id 表示若与现有调用方（plugin.xlib、业务代码）冲突：回退为显式类型参数（loadPluginByPath/loadPlugin(coordinates) 并存），仅影响 manager 层签名，api 层契约不动。
- AbstractPlugin 改造若破坏 DefaultPlugin/GraphQLPluginCommand 继承链：保持 start/stop 兼容路径语义不变即可回滚；aware 路径新代码独立演进。
- 若 ServiceLoader 发现的插件在 aware 分支调用 load 时抛错：按 No Silent No-Op 快速失败并卸载 classloader（与现状 start 失败路径对称），不残留半加载定义。
