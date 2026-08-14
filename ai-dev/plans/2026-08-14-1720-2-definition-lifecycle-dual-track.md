# 2 定义级生命周期 + 双轨来源 + 旧插件兼容（W2）

> Plan Status: completed
> Mission: nop-plugin-enhancement
> Work Item: W2 定义级生命周期 + 双轨来源 + 旧插件兼容
> Last Reviewed: 2026-08-14
> Review Consensus: 2026-08-14（独立 reviewer 三轮审查达成共识：无 Blocker）
> Source: `ai-dev/design/nop-plugin/01-architecture-baseline.md`（§二 双轨来源、§三 定义级状态机、§7.1 兼容语义）；`ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（Stage 2）
> Related: `ai-dev/plans/2026-08-14-1720-1-plugin-xdef-and-api-layer.md`（前置，提供 plugin.xdef + API 契约）、`ai-dev/plans/2026-08-14-1720-3-instance-lifecycle-effect-activator.md`（执行顺序 3，消费本 plan 的定义级状态机）

## Purpose

把"加载即激活"的耦合拆开：`loadPlugin` 只产定义（UNLOADED→LOADED），激活语义从加载中剥离；存量第三方插件（仅 start/stop，非 state-machine-aware）无感走旧路径。双轨来源（本地 VFS `*.plugin.xml` / uber jar）按 id 类型路由到同一套定义级状态机。

## Current Baseline

- `PluginManagerImpl`（`../../nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java`）：`loadPlugin`（:45）＝ resolver→`PluginClassLoader`→`classLoader.loadPlugin()`→`plugin.start(...)`（:54，加载即激活）；`unloadPlugin`（:64）＝ `plugin.stop()` + close classloader。插件 id 目前仅 `ArtifactCoordinates`（`manager/IPluginManager.java:13`）。
- **uber jar 的插件发现机制是 `plugin.json` 而非 ServiceLoader**：`PluginClassLoader.loadPlugin()`（`classloader/PluginClassLoader.java:71-73`）读取 `/nop/plugin.json`（`NopPluginConstants.PLUGIN_CONFIG_FILE`，`nop-plugin-api/.../NopPluginConstants.java:6`）→ `pluginConfig.getPluginClassName()` → `ClassHelper.newInstance`。
- `AbstractPlugin`（`nop-plugin-support/.../AbstractPlugin.java`）：extends `LifeCycleSupport`；`start(...)`（:83）＝ 保存坐标 + **`AppConfig.assignConfigValue` 全局写入 config** + `start()`；`doStart`（:108）＝ CoreInitialization + `loadFromResource(PLUGIN_BEANS_FILE, parent=BeanContainer.instance())` 建子容器；`doStop`（:97）＝ beanContainer.stop + 按需 CoreInitialization.destroy。子容器创建在 start 时立即发生（= 激活耦合）。`invokeCommandAsync`（:139-148）命令 bean 找不到时**回退宿主容器**（`BeanContainer.tryGetBean`）。
- `DefaultPlugin`（extends AbstractPlugin，空实现）在仓内无任何调用方；`GraphQLPluginCommand` 整文件被注释（两者都不构成兼容负担）。
- `IPluginManager` 现有签名 `loadPlugin(ArtifactCoordinates)/unloadPlugin(ArtifactCoordinates)/getLoadedPlugins()`；`plugin.xlib` 的 `InvokeCommand` 标签经 `nopPluginManager.loadPlugin(pluginId)` 调用且 `pluginId` 为 **String**（`nop-plugin-manager/src/main/resources/_vfs/nop/plugin/xlib/plugin.xlib`）——该标签无在仓调用方（dormant framework 文本），且 String 入参天然兼容统一 id 方案。
- W1 产出（本 plan 的前置）：`plugin.xdef`（动态模型模式，可解析 `*.plugin.xml`：name/requires/if-property/activator + `<beans>` 子元素）、`IPlugin` default 方法（`getState()` 默认 LOADED、`load/unload` 默认 UOE、`getInstance(s)` 空、`isStateMachineAware()` 默认 false）。
- 本地 VFS plugin 定义路径尚不存在；无任何 isStateMachineAware 检测；无"已加载未激活"态可观测。

## Goals

- 双轨统一 id 入口定案：`loadPlugin(String)` 为统一入口（Maven 坐标双冒号格式 → uber jar 轨；其余 → VFS 路径轨），保留 `loadPlugin(ArtifactCoordinates)` overload 委托（现有调用方二进制兼容）；`plugin.xlib` 已传 String，零改动。
- `PluginManagerImpl` load/unload 按 plugin.xdef 定义驱动：VFS 轨经 `DslModelParser` + plugin.xdef 解析 `*.plugin.xml` 为静态定义（manager 侧定义持有类实现 IPlugin）；uber jar 轨保留 resolver→classloader→plugin.json 链路；两轨汇入同一定义级状态机（LOADED 可缓存、可重复 get）。
- `isStateMachineAware` 检测：aware → 新状态机（load 到 LOADED，不激活）；非 aware → 兼容路径（保持旧 start/stop 语义，行为与改造前等价，含 start 失败清理语义）。
- `AbstractPlugin` 改造：aware 路径 load/unload 只管理静态定义（不再在 load 时建子容器）；`AppConfig.assignConfigValue` 全局写入仅保留在兼容路径；aware 路径无 ACTIVATED 实例时 `invokeCommand` 抛 INACTIVE（设计 §7.1 要求，W2 即落地，不做宿主容器回退）。
- 非 aware `getState()` 语义裁定：默认返回 LOADED（插件对象仅经 loadPlugin 成功后可见，unload 后从 manager 移除）。
- uber jar 路径经 `IPluginResourceResolver` 接线到新状态机（resolver 契约不变，调用点接入新流程）。

## Non-Goals

- 实例级生命周期（createInstance/destroy/activate/deactivate、PluginInstanceImpl、PluginScopeImpl、activator 调用链、实例配置域）——W3。
- unload 守卫的"有实例抛异常"路径（无实例可创建，守卫正路径 W3 测试；本 plan 仅保证 unload 在定义级状态机上正确执行）。
- coeffect/reconcile（W5）、parent 层级/HMR（W6）、getService 代理（W4）。
- `HttpPluginResourceResolver` SHA256 补齐（W7）。
- 不改 `PluginClassLoader` 类隔离机制（roadmap 明确"本 roadmap 不改动它"）。

## Scope

### In Scope

- `IPluginManager` 签名：新增 `loadPlugin(String)/unloadPlugin(String)/getInstance(s)` 家族入口（§7.3 中本 stage 需要的部分），保留 ArtifactCoordinates overload。
- `PluginManagerImpl`：双轨路由、定义级状态机（LOADED 持有/缓存）、isStateMachineAware 检测、aware 走 `plugin.load(config)`、非 aware 走 `plugin.start(...)`（现状语义 + start 失败清理）。
- VFS 轨定义持有类（manager 侧，实现 IPlugin）：解析 plugin.xdef 模型、持定义级状态与实例 registry 骨架、load/unload 落地——**VFS 轨 config 来源定案：定义级 config = 空 Map**（plugin.xml 无 config 载体；W5 定义级 coeffect 读全局配置），实例配置由 W3 createInstance 传入。
- `AbstractPlugin`（support）：aware 路径 load/unload 静态定义管理 + invokeCommand INACTIVE；兼容路径保留全局 config 写入与旧 start/stop 语义。
- 定义级实例 registry 骨架（getInstances()/getInstance(key) 返回空列表/空——W3 填充，本 plan 保证非空壳语义成立）。
- 测试：兼容路径回归、aware 定义级状态（LOADED/UNLOADED）、双轨路由接线、非 aware getState 裁定验证。

### Out Of Scope

- 实例对象、effect、scope、activator 的一切实现（W3）。
- coeffect spec 求值（W5；本 plan 仅解析进定义模型，不求值）。
- HMR 依赖追踪接线（W6）。
- SHA256 校验（W7）。
- beans.xdef 修改（W1 已明确不做）。

## Execution Plan

### Phase 1 - 双轨 id 统一 + PluginManagerImpl 状态机化

Status: completed
Targets: `../../nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/IPluginManager.java`、`../../nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/PluginManagerImpl.java`（含新 VFS 轨定义持有类）；`plugin.xlib` 零改动（已传 String，兼容新签名）

- Item Types: `Fix | Decision`

- [x] **id 路由定案**（本 plan 内裁定，不留实现期发明）：统一 String id；判别规则 = `ArtifactCoordinates.parse(id)` 成功 → Maven 坐标 → uber jar 轨；**parse 抛错视为非坐标**（`ArtifactCoordinates.parse` 对非法输入抛 `IllegalArgumentException`，判别处 try/catch 吞掉并转 VFS 轨，不传播——注意含 `:` 的 VFS 路径存在误判坐标的风险，属可接受残余，测试覆盖非法输入场景）；否则 → VFS 路径轨（如 `/plugins/agent-tools/plugin.plugin.xml`）；保留 `loadPlugin(ArtifactCoordinates)` overload 委托到 String 入口（现有调用方二进制兼容）；`plugin.xlib` 已传 String 无需改动。
- [x] `PluginManagerImpl.loadPlugin(String)`：id 判别 → 双轨分发；VFS 轨经 `DslModelParser` + plugin.xdef 解析 `*.plugin.xml` 为静态定义，由 **manager 侧新增定义持有类**（实现 IPlugin：持定义级状态、registry 骨架、load/unload 落地）持有；uber jar 轨保留 resolver→`PluginClassLoader`→`plugin.json`→`ClassHelper.newInstance` 链路。
- [x] **定义持有类的剩余抽象方法行为钉死**：`start/stop` → 显式失败（§7.1 语义 start = load + createInstance，createInstance 未落地（W3）前抛带说明的异常，与 AbstractPlugin 同一裁定）；`getPluginGroupId/getPluginArtifactId/getPluginVersion` → null（VFS 轨无坐标）；`getLastChangeTime/getLoadTime` → 真实值（load 时记录）。
- [x] isStateMachineAware 检测：aware → `plugin.load(config)`（到 LOADED，**不调用 start**）；非 aware → 现有 `plugin.start(...)` 语义（加载即激活，行为等价现状）。
- [x] 兼容路径失败语义显式保留（现状 `PluginManagerImpl.java:56-59`）：`start` 抛错 → `plugin.stop()` → rethrow，entry 不写入插件 map（无半加载残留）。
- [x] `PluginManagerImpl.unloadPlugin(id)`：aware → `plugin.unload()`（丢弃定义，registry 清空）；非 aware → `plugin.stop()`（现状）；两路都保留 classloader 关闭（如适用）。
- [x] 定义级实例 registry 骨架：定义持有类 `getInstances()`/`getInstance(key)` 返回真实空列表/空（W3 填充）——不写空壳占位。
- [x] aware 路径 INACTIVE 语义：aware 定义在 LOADED 且 0 实例时 `invokeCommand/invokeCommandAsync` 抛 INACTIVE 错误（设计 §7.1：无 ACTIVATED 实例抛 INACTIVE；W2 阶段 aware 恒 0 实例，此即正确行为；W4 完善实例数=1 路由）。
- [x] `./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] id 路由定案已落地并记录：String 统一入口 + ArtifactCoordinates overload 委托；判别规则 repo-observable（Phase 3 测试覆盖两轨各一例）。
- [x] **接线验证**：VFS 轨真实经 plugin.xdef 解析路径产出定义持有类（非 stub）；uber jar 轨仍经 `IPluginResourceResolver` + `PluginClassLoader.loadPlugin()`（plugin.json）。
- [x] **无静默跳过**：未知/无法解析的 id 抛明确异常（错误码带 id 参数）；aware-LOADED 的 invokeCommand 抛 INACTIVE（不静默回退宿主容器）。
- [x] 编译通过；`TestPluginManager` 空壳在本 phase 不被依赖（Phase 3 替换为真实测试）。
- [x] No owner-doc update required（行为变更记录在 W7 docs 同步；本 plan 无独立 owner doc）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - AbstractPlugin 改造（aware/兼容双路径）+ support 测试

Status: completed
Targets: `nop-plugin-support/.../AbstractPlugin.java`、`nop-plugin-support/src/test/`（如新建）

- Item Types: `Fix`

- [x] aware 路径：`load(config)` 解析 plugin.xdef 定义并持有（LOADED）；`unload()` 丢弃定义（UNLOADED）；**不再在 load 时创建子容器**（子容器创建移入 W3 createInstance——load 路径不得出现 AppBeanContainerLoader 调用）；`isStateMachineAware()` 返回 true（覆盖 default；DefaultPlugin 继承链随之 aware，仓内无调用方，风险可控）。
- [x] **aware 定义来源定案**（jar 轨 aware 插件同路径）：`load(config)` 从 VFS 约定路径读取定义文件（沿用 `NopPluginConstants.PLUGIN_BEANS_FILE` 的 `/nop/plugin.plugin.xml` 同类约定，实现时在 `NopPluginConstants` 新增对应常量）；**定义文件缺失 → load() 显式失败**（抛明确异常，不得带着 null 定义进入 LOADED——No Silent No-Op）。
- [x] **INACTIVE 错误码位置定案**：aware 无 ACTIVATED 实例的 INACTIVE 错误码/错误常量定义在 **`nop-plugin-api`**（manager 与 support 两模块都要抛出，而 `nop-plugin-support` 不依赖 `nop-plugin-manager`，放 `PluginManagerErrors` 会不可见）。
- [x] 兼容路径：`start(...)`/`stop()` 保留现状语义（含 doStart 子容器创建与 assignConfigValue 全局写入）；`AppConfig.assignConfigValue` 全局写入仅存在于此路径。
- [x] aware 路径 `invokeCommandAsync`：无 ACTIVATED 实例时抛 INACTIVE（覆盖现状的宿主容器回退）；兼容路径保持现状回退行为不变。
- [x] start/stop 与 load/unload 的关系收敛到设计 §7.1 语义（start = load + createInstance(默认 key)；stop = destroyInstance + unload）——W2 落地 load/unload 半边；start() 在 aware 下直接调用时，因 createInstance 未落地（W3）而**显式失败**（抛带说明的异常，不静默忽略）；createInstance/destroyInstance 半边由 W3 接续。
- [x] 新增 support 模块测试（Rule 25；需在 `../../nop-core-framework/nop-plugin/nop-plugin-support/pom.xml` 加 `junit-jupiter` test-scope 依赖，先例 `nop-plugin-manager/pom.xml:35-37`）：AbstractPlugin 子类——`load(config)` 后 `getState()==LOADED` 且**未创建子容器**（断言无 bean 实例化副作用）；定义缺失时 `load()` 显式抛异常；`unload()` 后 `getState()==UNLOADED`；aware `invokeCommand` 抛 INACTIVE；兼容路径（非 aware 语义）start/stop 行为不变。
- [x] `./mvnw compile -pl :nop-plugin-support -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `AbstractPlugin` aware 路径 load 不触发子容器创建（代码审查 + support 测试断言）。
- [x] `AppConfig.assignConfigValue` 仅存于兼容路径（grep 定位确认）。
- [x] support 测试存在且断言真实行为（LOADED/UNLOADED/INACTIVE/兼容路径四类）。
- [x] **无静默跳过**：aware start() 在 createInstance 未落地（W3）时显式失败，无空方法体。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 测试补全（兼容回归 + 双轨路由 + 定义级状态）

Status: completed
Targets: `nop-plugin-manager/src/test/`、`nop-plugin-support/src/test/`

- Item Types: `Proof | Fix`

- [x] 替换空壳 `TestPluginManager`：兼容路径回归测试（模拟非 aware IPlugin：loadPlugin 调 start、unloadPlugin 调 stop、start 失败 → stop → rethrow 且 entry 不入 map、行为与改造前等价）。
- [x] 非 aware `getState()` 裁定验证：非 aware 插件经 loadPlugin 后 `getState()==LOADED`（默认方法保守值），unloadPlugin 后从 `getLoadedPlugins()` 移除。
- [x] aware 定义级状态测试：VFS 轨 loadPlugin 后 `getState()==LOADED`、`getInstances()` 为空、未创建任何实例；unloadPlugin 后回到 UNLOADED；重复 loadPlugin 幂等（返回同一定义）；**定义持有类钉死行为回归**——`start(...)` 抛带说明异常、`getPluginGroupId()==null`、`getLoadTime()!=null`（load 后）。
- [x] 双轨路由测试：VFS 路径 id 加载测试资源 `*.plugin.xml` 成功——**必须走真实的 manager 侧定义持有类 + 真实 `DslModelParser` 解析路径**（非测试内建 mock；测试资源放 manager 测试类路径）；Maven 坐标 id 走 uber jar 轨（resolver stub 提供本地 jar，jar 内含 `/nop/plugin.json` 声明 pluginClassName——**注意不是 META-INF/services**）。
- [x] 非法 id/无法解析路径抛明确异常（错误码带 id）。
- [x] 零依赖不变式回归（W1 已建检查，本 plan 预期零命中）。
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上述五类测试存在且断言真实行为（非仅"不抛异常"）。
- [x] 兼容路径测试证明旧语义保留（start/stop 调用序列、失败清理、getState 保守值）。
- [x] **端到端验证**（如适用）：从 `loadPlugin(id)` 入口经双轨加载链路到定义级 LOADED 状态的完整路径有测试覆盖（VFS 轨与 uber jar 轨各一）；unloadPlugin 反向到 UNLOADED/移除有测试覆盖。
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 退出码 0。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 加载与激活解耦成立：aware plugin 可处于 LOADED 且零实例/零子容器状态（测试断言）。
- [x] 双轨来源按 id 类型路由成立（VFS/uber jar 各走正确链路，测试覆盖；uber jar 轨经 plugin.json 发现）。
- [x] 存量非 aware 插件兼容：start/stop 语义与改造前等价（含失败清理），getState 保守值裁定落地（回归测试覆盖）。
- [x] `AppConfig.assignConfigValue` 全局写入仅存于兼容路径；aware-LOADED invokeCommand 抛 INACTIVE。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项。
- [x] No owner-doc update required（使用文档 W7 统一同步）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）aware 路径 load 与 unload 真的被 PluginManagerImpl 调用（从 loadPlugin 入口到 plugin.load 的调用链连通；VFS 轨定义持有类真的解析 plugin.xdef），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### 定义级 coeffect 求值（requires/if-property 生效）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §五 明确 coeffect 是运行时条件激活（W5 实现 reconcile）；W2 仅需把属性解析进定义模型（W1 schema 已支持），求值/订阅/自动触发全部归属 W5，W2 定义级状态机不依赖求值结果。
- Successor Required: `yes`
- Successor Path: W5 coeffect + reconcile（roadmap Stage 5）

### unload 守卫"有实例抛异常"正路径测试

- Classification: `watch-only residual`
- Why Not Blocking Closure: W2 阶段任何定义都无法派生实例（createInstance 属 W3），守卫逻辑的"有实例"分支不可能被触发；守卫代码与正路径测试随 W3 实例落地时实现，W2 定义级 unload 语义（registry 空时正常 unload）已由 Phase 3 测试覆盖。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/2026-08-14-1720-3-instance-lifecycle-effect-activator.md`

### 实例级命令路由（per-instance invokeCommand）

- Classification: `watch-only residual`
- Why Not Blocking Closure: W2 无实例，aware 路径 invokeCommand 抛 INACTIVE 已是设计 §7.1 要求的正确行为；实例数=1 路由与多实例异常规则随 W4 实例落地实现。
- Successor Required: `yes`
- Successor Path: W4 getService 生命周期代理 + per-instance 命令路由（roadmap Stage 4）

## Non-Blocking Follow-ups

- 双轨 id 表示若未来引入第三种来源（如 Maven 仓库直接解析），路由表可扩展——当前不实施。
- `getLoadedPlugins()` 的返回语义随实例概念落地（W3）复核是否需要扩展。

## Closure

Status Note: 三个 Phase 全部落地并验证通过；独立 closure audit（fresh session）READY_TO_CLOSE，零 Blocker/Major（4 项 Minor 均 non-blocking，2 项已顺手处置/记录）。定义级生命周期（UNLOADED→LOADED）、双轨来源（VFS 轨经 plugin.xdef 解析 / uber jar 轨经 plugin.json 发现）、存量非 aware 兼容路径（start/stop 旧语义 + 失败清理 + getState 保守值）全部有测试断言真实行为；Anti-Hollow 检查通过（调用链从 loadPlugin(id) 入口到定义级 LOADED 连通，VFS 轨与 jar 轨各一端到端测试）。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: independent subagent（general，fresh session）
- Audit Session: `ses_000297b3cffe5LyPw5HhRLUod4`
- Evidence:
  - Phase 1 Exit Criteria 全部 PASS：String 统一入口 + ArtifactCoordinates default overload 委托（IPluginManager.java:26/36-45）；判别规则 repo-observable（PluginManagerImpl.tryParseCoordinates:114-120）；VFS 轨真实经 DslModelParser+PLUGIN_XDEF_PATH 产出 VfsPluginDefinition（非 stub，PluginManagerImpl.java:122-139）；jar 轨保留 resolver→PluginClassLoader.loadPlugin()→plugin.json（:141-170）；未知 id 抛 ERR_PLUGIN_DEFINITION_NOT_FOUND 带 pluginId 参数、aware-LOADED invokeCommand 抛 ERR_PLUGIN_INACTIVE（VfsPluginDefinition.java:127-138，零 BeanContainer 引用无宿主回退）。
  - Phase 2 Exit Criteria 全部 PASS：AbstractPlugin.load 无 AppBeanContainerLoader 调用（唯一出现于 doStart:206，aware start() 抛 UOE 不可达），测试断言 getBeanContainer()==null；assignConfigValue 全仓 grep 仅 1 处实现（AbstractPlugin.java:170 兼容分支）；support 测试四类真实断言（LOADED/UNLOADED/INACTIVE/兼容路径）6/6；aware start/stop 显式失败带 W3 说明。
  - Phase 3 Exit Criteria 全部 PASS：TestPluginManager 5 用例（兼容回归 start/stop 序列经 MockPluginRecorder 跨 classloader 断言、失败清理 stop+entry 不入 map、getState 保守值、VFS 轨定义级状态+幂等+unload 反向、非法 id 带参数、单冒号 id 路由残余）+ TestPluginXdef 2/2；双轨端到端（VFS 轨与 uber jar 轨各一，jar 内 /nop/plugin.json 非 META-INF/services）；测试独立复跑 BUILD SUCCESS（api 6/6、manager 7/7、support 6/6）。
  - Closure Gates 全部 PASS（独立 audit 逐条核验）：加载与激活解耦（0 实例 0 子容器测试断言）；双轨按 id 类型路由（plugin.json 发现含前导斜杠回退修复，jar 轨测试实证命中回退分支）；存量非 aware 兼容（含失败清理 + 保守值）；assignConfigValue 仅兼容路径 + INACTIVE；Deferred 三类分类诚实（coeffect→W5 / unload 守卫正路径→W3 / 实例级命令路由→W4，均非 in-scope 项降级）。
  - Anti-Hollow 检查：PASS——调用链追踪（VFS：loadPlugin(String)→loadPluginFromVfs→VfsPluginDefinition.load→LOADED；jar：→loadPluginFromJar→PluginClassLoader.loadPlugin→ClassHelper.newInstance→start/load 分流），运行时日志实证真实解析（begin-parse-resource DslModelParser/plugin.xdef）；无空方法体/静默跳过/no-op（getInstances/getInstance 真实 registry 查询）；`scan-hollow-implementations.mjs --module nop-plugin-api/manager/support --severity high` 退出码 0，0 findings。
  - `node ai-dev/tools/check-plan-checklist.mjs 2026-08-14-1720-2-definition-lifecycle-dual-track.md --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）。
  - checkstyle（项目规则集 checkstyle.xml）exit 0；`./mvnw clean install -DskipTests -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` BUILD SUCCESS。
  - 已知偏差（audit 核实合理）：plugin.xdef 迁移至 nop-plugin-api 资源（support 需解析而 support 不依赖 manager；TestPluginXdef 迁移后仍绿）；PluginClassLoader.loadPluginConfig 前导斜杠回退修复（原 uber jar 发现潜伏缺陷，首次被测试覆盖）。
  - Minor 处置：M1（未提交）→ 按 W1 先例交由 mission-driver 统一 commit；M2（VfsPluginDefinition.unload 未清 instances map——W2 registry 恒空不可观测，unload 守卫随 W3 落地，plan Deferred 节已裁定）；M3（jar 轨 aware load 分支由 TestAbstractPlugin 分层覆盖，W7 测试补全）→ Follow-up；M4（单冒号 id 路由残余）→ 本次已补测试 testVfsPathWithSingleColonRoutesToVfsTrack 钉死。

Follow-up:

- 实现产物未提交，交由 mission-driver 统一 commit（AGENTS.md Git Workflow，W1 先例）。
- jar 轨 aware 插件 load 分支的 jar 内端到端测试由 W7 测试补全覆盖（当前由 support 模块分层覆盖）。
- no remaining plan-owned work。

## Optional Sections

## Risks And Rollback

- 双轨 id 统一若与现有调用方冲突（仓内无 ArtifactCoordinates 调用方，plugin.xlib 传 String）：保留 ArtifactCoordinates overload 已消除二进制风险；行为风险由 Phase 3 双轨测试覆盖。
- AbstractPlugin 升级 aware（isStateMachineAware=true）若影响仓外存量 AbstractPlugin 子类：其 start/stop 兼容语义按 §7.1 收敛（start = load + createInstance），W2 阶段 start() 显式失败属临时缺口，W3 接续后收敛；仓内 DefaultPlugin 无调用方，风险可控。
- 若 ServiceLoader 假设再次出现（任何新代码不得引入）：uber jar 发现一律走 `PluginClassLoader.loadPlugin()` 的 plugin.json 机制（`NopPluginConstants.PLUGIN_CONFIG_FILE`）。
