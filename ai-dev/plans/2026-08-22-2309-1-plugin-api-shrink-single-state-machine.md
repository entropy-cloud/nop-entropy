# 1. nop-plugin 定位反转 R1：API 收缩——单层六态状态机

> Plan Status: completed
> Mission: nop-plugin-enhancement
> Work Item: R1（定位反转重构：API 收缩）
> Last Reviewed: 2026-08-22
> Source: `ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（R1 条目）；`ai-dev/design/nop-plugin/01-architecture-baseline.md`（2026-08-22 版，权威契约：§三 状态模型、§7.1-§7.7 接口契约、源码锚点表）；`ai-dev/design/nop-plugin/00-vision.md` §〇（定位反转裁决）
> Related: `2026-08-14-1720-1-plugin-xdef-and-api-layer.md`（W1，历史交付者）；后续 `2026-08-22-2309-2`（R2）、`2026-08-22-2309-3`（R3）

## Purpose

将 `nop-plugin-api` 公共契约从"定义级 + 实例级"两层状态机收缩为**单层六态状态机**（一个定义至多一个激活），删除全部实例机制 API 面（IPluginInstance/InstanceState/createInstance/instanceKey/parent 层级），`IPlugin` 直接承载 activate/deactivate/getService。同时完成实现侧为保持编译与测试绿所必需的最小收缩（完整编排清理归 R2，兼容语义归 R3，全面测试改造与 docs 归 R4）。

## Current Baseline

（2026-08-22 依据 live repo 核验；行数为主线代码行数）

- `nop-plugin-api`（模块 `nop-core-framework/nop-plugin/nop-plugin-api`）现有：
  - `IPluginInstance.java`（101 行）：实例级状态机接口（getInstanceKey/activate/deactivate/getScope/getService(s)/getParent/getConfig/invokeCommand(s)/destroy）——**本 plan 删除**
  - `PluginState.java`（仅 UNLOADED/LOADED）与 `InstanceState.java`（仅 ACTIVATED/DEACTIVATED）双枚举——**本 plan 合并为单组六态**
  - `IPlugin.java`（137 行）：default `getInstance(instanceKey)`/`getInstances()`/`load`/`unload`/`isStateMachineAware`/`getState`；**无 activate/deactivate/getService**；第 11 行 Javadoc 仍称"利用ServiceLoader机制注册插件实现类"（实际发现机制 = jar 内 plugin.json 指定实现类 + 反射，见 01 §二）
  - `IPluginContext.java`（33 行）：getInstance(pluginId, key)/allInstances()/reconcile()——**本 plan 重写**
  - api 模块零依赖不变式当前成立（不 import `io.nop.ioc`/`io.nop.xlang`）
- `nop-plugin-manager` 现有：
  - `IPluginManager.java`（101 行）：`createInstance`/`destroyInstance`/`getInstance`/`getInstances` 为**抽象方法（非 default）**——删除即编译阻塞 `PluginManagerImpl`，本 plan 必须同步适配
  - `PluginManagerImpl.java`（1070 行）：实现 IPluginManager + IPluginContext；持 `parentToChildren` 映射、级联销毁、HMR 快照（ReloadSnapshot/pendingRebuilds）、实例方法实现——实例方法删除归 R1，**其余编排清理归 R2**（R1 仅删到编译通过且语义不空洞）
  - `PluginInstanceImpl.java`（592 行）：实例生命周期实现（子容器、activator 调用链、effect、in-flight 单飞）——**本 plan 删除**，生命周期并入 `VfsPluginDefinition`（或提取包内支持类，见锚点表 R1 行）
  - `ServiceProxy.java`（85 行）：代理 handler 持 `PluginInstanceImpl`、经 `instance.getScope()` 判活——**本 plan 改绑插件激活态**，多候选规则不变
  - `VfsPluginDefinition.java`（523 行）：持 `Map<String, IPluginInstance> instances` registry、createInstance/destroyInstance、start（不含 load 步，先 checkLoaded 再建默认 key 实例）、stop（destroy 默认 key + unload）
  - `AbstractPlugin`（support 模块，281 行）：aware 分支 `start` = `load(config)` + 抛 UOE（jar 轨实例化为 W4 裁决的 successor 项）；非 aware 分支 = 旧语义（AppConfig.assignConfigValue + doStart 子容器）
- 测试面（三模块）：
  - manager 10 个测试类（约 2900 行）：`TestPluginInstanceLifecycle`(634)/`TestCoeffectReconcile`(470)/`TestReloadPlugin`(355)/`TestParentHierarchy`(354)/`TestPluginManager`(237)/`TestChangeDetection`(242)/`TestPluginCoeffectSpec`(93)/`TestHttpPluginResourceResolver`(448)/`TestPluginXdef`(69)/`impl/TestCoeffectConfigHelper`(55)——多数经 createInstance 驱动，随 API 删除必须收缩
  - api：`TestPluginDefaultMethods`（引用 getInstance/getInstances default 行为）——随 default 方法删除必须收缩
  - support：`TestAbstractPlugin`（大量 IPluginInstance/InstanceState/StubInstance 引用）——随实例面删除必须收缩
- 外部消费者核验：仓库内 `nop-plugin` 之外仅 `nop-demo/nop-quarkus-demo/.../DemoPluginCommand.java` import `io.nop.plugin.api.{IPluginCancelToken,IPluginCommand}`（不在被删面上）；无任何代码调用 `IPluginManager.createInstance/destroyInstance/getInstances` 或引用 `IPluginInstance`——**"当前无外部消费者"成立，破坏性收缩合规**（AGENTS.md 跨模块公共 API plan-first 由本 mission 流程满足）
- `plugin.xdef`（`nop-plugin-api/src/main/resources/_vfs/nop/schema/plugin/plugin.xdef`）：requires/if-property/activator + `<beans>` 已就位，本 plan 不改其属性集

## Goals

- `PluginState` 收敛为单组六态枚举：`UNLOADED/LOADED/ACTIVATING/ACTIVATED/DEACTIVATING/FAILED`（01 §三）；`InstanceState`、`IPluginInstance` 从 api 模块删除
- `IPlugin` 按 01 §7.1 承载：`getState()`（六态）、`activate()` 返回 boolean（门控不满足 no-op false；已 ACTIVATED 幂等 true）、`deactivate()` 返回 `CompletionStage<Void>`、`getService(Class)`/`getServices(Class)`（激活态绑定代理，多候选规则不变）、`invokeCommand` 契约回归定义级路由的 API 面收窄（删"实例数=1 才路由"相关 default 语义描述）；`updateConfig` 语义 = LOADED 缓存 / ACTIVATED 热应用（定义级配置域）；`start/stop`/`isStateMachineAware` 保留为兼容入口（语义收敛的**实现**归 R3）
- `IPluginContext` 重写为 `getPlugin(id)`/`allPlugins()`/`reconcile()`（01 §7.6）
- `IPluginManager` 删除 `createInstance/destroyInstance/getInstance(key)/getInstances` 抽象方法，收敛到 01 §7.3 形状（`loadPlugin`/`unloadPlugin`/`activatePlugin`/`deactivatePlugin`/`getPlugin`/`getLoadedPlugins`（保留）/`reloadPlugin`/`reconcilePlugins`）
- 实现侧最小落地：`VfsPluginDefinition`（或其提取的支持类）承载单激活六态生命周期（子容器 + activator + effect + in-flight 单飞 + unload 须先 deactivate 守卫）；`ServiceProxy` 绑定对象从实例改插件激活态（INACTIVE 快速失败语义不变）；`PluginManagerImpl` 实例方法删除并新增 activatePlugin/deactivatePlugin/getPlugin/reconcilePlugins（reconcile 引擎的**语义简化**归 R2，本 plan 过渡为仅定义级评估且不加拓扑序）
- jar 轨契约落地（AbstractPlugin 轨道，01 §二裁决原文语义）：`AbstractPlugin.activate()` 最小实现——容器构建复用现有 doStart 路径逻辑、状态迁移到六态；**门控恒为空集（无条件激活）、激活回调统一跳过**（子容器启动即 ACTIVATED；无论 VFS 约定路径是否存在 xdef 载体——有载体时仍解析持有但仅作元数据，不驱动 jar 轨门控/activator，未来需要时须为其定义载体立项）；`load()` 相应容忍 xdef 缺失（无载体时空定义加载成功）
- 修正 `IPlugin.java` Javadoc：ServiceLoader 描述改为 plugin.json 指定实现类 + 反射实例化（与 `PluginClassLoader` 实际机制一致）
- api 模块零依赖不变式复验通过

### Ownership Deviation（R1/R2 边界，draft review 裁定）

roadmap R2 文字把 `parentToChildren`/级联销毁/P2-A 快照/pending/`ICoeffectEvaluator.isInstanceConfigMatched` 记在 R2 名下，但它们全部以 `IPluginInstance`/`PluginInstanceImpl` 为类型或消费者——R1 删除这些类型后**编译即断裂**。裁定：**R1 因编译依赖一并移除全部 IPluginInstance 型结构与方法签名**（含上述五项 + `PluginInstanceImpl` 内的失败阈值暂停机制迁移到定义级 + `hasActivatedInstanceByName` 的定义级判定改造）；**R2 = 残留引用审计（grep 清零确认）+ 错误码族退役 + reconcile 拓扑序/语义收口**。R2 plan 的 Current Baseline 已按此以"验证无残留而非重复删除"表述；本 Deviation 在 R1 执行时记录于 daily log，R4 docs 同步时回读修正（修正对象含 roadmap R2 条目文字与 01-architecture-baseline.md 源码锚点表 R2 行措辞）。

## Non-Goals

- manager 编排实现清理：`InstanceConfigProvider`/`CoeffectConfigHelper` 类删除（若 R1 后仍存在）、实例错误码族退役（ERR_PLUGIN_INSTANCE_* / INSTANCES_NOT_EMPTY / MULTIPLE_INSTANCES / PARENT_*，含 api 模块常量）、实例配置域求值语义的残留审计、reconcile 跨插件去激活拓扑序、HMR 快照/pending 机制的残留清理审计——**R2**（`ICoeffectEvaluator.isInstanceConfigMatched` 的方法签名因参数类型为 IPluginInstance，**随 R1 编译依赖删除**，见 Ownership Deviation；R2 仅审计其语义残留）
- `AbstractPlugin`/`VfsPluginDefinition` 的 start=load+activate、stop=deactivate+unload 兼容语义收敛及其按两轨真实行为差异写的回归基线、invokeCommand 定义级路由回归测试、plugin.xdef 属性集冻结确认——**R3**
- 多实例隔离/coeffect 实例级测试的全面改写、`hasActivatedInstanceByName` 语义迁移断言、dsh 终核结论回填 03/04、`docs-for-ai/03-modules/nop-plugin.md` 重写与 INDEX/source-anchors 同步——**R4**
- `plugin.xdef` 属性集变更（冻结，不新增属性）
- `PluginClassLoader` 类隔离、`HttpPluginResourceResolver` SHA256 链路（W7 交付，不动）

## Scope

### In Scope

- `nop-plugin-api`：PluginState 六态化、删 InstanceState/IPluginInstance、IPlugin/IPluginContext 重写、Javadoc 修正
- `nop-plugin-manager`：IPluginManager 收缩、PluginManagerImpl 适配（删实例面 + 新增 activate 面）、删 PluginInstanceImpl、VfsPluginDefinition 单激活化、ServiceProxy 重绑、过渡期 HMR（deactivate→unload→load 重放 updateConfig 累积值→reconcile——实例快照随实例删除自然消失，残留清理审计归 R2）
- `nop-plugin-support`：`AbstractPlugin` 落地 `activate()`/`deactivate()` 最小实现（jar 轨契约，见 Goals）+ 删除 IPluginInstance/InstanceState 引用的最小适配；start/stop 兼容语义收敛与回归基线归 R3
- 测试面最小收敛：删除/收缩仅依赖已删机制的测试用例（含 api `TestPluginDefaultMethods`、support `TestAbstractPlugin`）；为六态状态机、幂等激活、unload 守卫、getService 代理重绑、jar 轨无条件激活新增 focused tests
- api 零依赖不变式验证（grep + 编译）

### Out Of Scope

- 同 Non-Goals（R2/R3/R4 归属项）
- nop-quarkus-demo 依赖收窄（R4 可选项）

## Execution Plan

### Phase 1 - api 模块契约收缩

Status: completed
Targets: `nop-plugin-api/src/main/java/io/nop/plugin/api/`（PluginState.java、InstanceState.java、IPluginInstance.java、IPlugin.java、IPluginContext.java、IPluginScope.java、PluginApiErrors.java 的 Javadoc/消息文案）

- Item Types: `Fix | Decision | Proof`

- [x] `PluginState` 改为六态（UNLOADED/LOADED/ACTIVATING/ACTIVATED/DEACTIVATING/FAILED），Javadoc 按 01 §三状态表描述各态的静态定义/子容器/bean/effect 占有
- [x] 删除 `InstanceState.java`、`IPluginInstance.java`
- [x] `IPlugin` 按 01 §7.1 重写：getState/load/unload/activate/deactivate/getService(s)/invokeCommand(s)/updateConfig/start/stop/isStateMachineAware；删除 getInstance(instanceKey)/getInstances default 方法；activate 同步返回 boolean、deactivate 返回 CompletionStage<Void> 的不对称为有意设计（Javadoc 引用 01 §7.1）
- [x] `IPluginContext` 重写为 getPlugin/allPlugins/reconcile（删 getInstance/allInstances）
- [x] 修正 IPlugin Javadoc 的 ServiceLoader 描述为 plugin.json + 反射；清除全部对 IPluginInstance/InstanceState 的 Javadoc 引用（含 `IPluginScope.java` Javadoc、`PluginApiErrors.java` Javadoc 与错误消息文案中提及 IPluginInstance 的字符串——**改文案 ≠ 退役错误码**，常量本体保留至 R2 退役）
- [x] api 模块内零 `io.nop.ioc`/`io.nop.xlang` import（不变式保持）

Exit Criteria:

- [x] `nop-plugin-api` 源码树中 `IPluginInstance`/`InstanceState` 文件不存在；`grep -rn "IPluginInstance\|InstanceState" nop-core-framework/nop-plugin/nop-plugin-api/src/main` 零命中
- [x] `IPlugin` 方法签名与 01 §7.1 表逐项一致（activate→boolean、deactivate→CompletionStage<Void>、getService/getServices 存在）
- [x] 新增/更新单元测试：六态枚举可引用、IPlugin 非 aware default 行为显式（activate 无状态机支撑时抛 UnsupportedOperationException 或按 §7.1 兼容语义定义，不静默 no-op）
- [x] **无静默跳过**：新增 default 方法在未实现路径显式抛异常（对齐 W1/W2 先例）
- [x] api 模块独立编译通过（`./mvnw compile -pl :nop-plugin-api -am`）
- [x] **若该 Phase 改变 live baseline**：本 Phase 只动 api 层，owner docs 同步归 R4；Phase 内明确记录 `docs 同步 deferred to R4（roadmap 归属）` 于 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - manager/support 实现侧最小收缩（编译面收敛）

Status: completed
Targets: `nop-plugin-manager/src/main/java/io/nop/plugin/manager/`（IPluginManager.java、impl/PluginManagerImpl.java、impl/PluginInstanceImpl.java、impl/VfsPluginDefinition.java、impl/ServiceProxy.java、impl/PluginScopeImpl.java、impl/ICoeffectEvaluator.java）、`nop-plugin-support/.../AbstractPlugin.java`

- Item Types: `Fix | Decision | Proof`

- [x] `IPluginManager` 删 createInstance/destroyInstance/getInstance/getInstances；reconcileInstances 更名/收敛为 reconcilePlugins；新增 activatePlugin(String)→boolean、deactivatePlugin(String)→CompletionStage<Void>、getPlugin(String)→IPlugin（01 §7.3，显式保留项见下：`loadPlugin(String)` 签名不变（不新增 config 形参）、`getLoadedPlugins` 保留为 §7.3 表之外的查询方法）
- [x] 删除 `PluginInstanceImpl.java`；单激活生命周期（子容器构建、activator.activate(scope, config)、返回值自动 scope.effect、scope.close LIFO 先于容器 stop、in-flight 单飞、ACTIVATED 幂等、FAILED 置态可重试、**失败计数超阈值暂停自动激活**——阈值机制自 PluginInstanceImpl 迁移到定义级，语义不变）落到 `VfsPluginDefinition` 或包内新支持类（实现选型自由，锚点表允许）
- [x] `VfsPluginDefinition`：instances registry 删除；load/unload 守卫改为"ACTIVATED/中间态 unload 抛明确异常"（01 §三不变量，错误码可沿用现有定义级错误或新增，实例错误码族退役归 R2）；getState 返回六态；**start/stop 直接收敛为目标语义**（start = load + activate、stop = deactivate + unload——编译强制所致，R3 仅补两轨回归基线）；定义级配置送达子容器的通道落地（语义等价迁移 PluginInstanceImpl 现有"先 setConfigProvider 再 start、禁止静默回落全局配置"的钉死约束，改为定义级配置域视图）
- [x] `ServiceProxy` handler 绑定对象从 PluginInstanceImpl 改为插件激活态判定（激活容器的 scope 句柄；DEACTIVATING 完成后调用抛 INACTIVE）；多候选规则（primary 优先→唯一实现→多候选抛异常）不变
- [x] `PluginManagerImpl`：删四个实例方法实现与 IPluginContext 旧实现，新增 getPlugin/allPlugins/reconcilePlugins/activatePlugin/deactivatePlugin；HMR reloadPlugin 过渡语义 = 若激活态先 deactivate → unload → load（重放 updateConfig 累积值）→ reconcile（快照/pending 数据结构随实例删除移除，**遗留引用清理审计归 R2**）
- [x] reconcile 过渡语义：仅定义级 requires/if-property 评估（不动点迭代 + 环检测保留）；**跨插件拓扑序增强归 R2**，本 Phase 保持现有迭代收敛
- [x] `AbstractPlugin`（support）：落地 `activate()`/`deactivate()` 最小实现（Goals 所列 jar 轨契约：容器构建复用 doStart 路径逻辑、六态迁移、门控恒空集无条件激活、激活回调跳过；`load()` 容忍 xdef 缺失）；删除对 IPluginInstance/InstanceState 的引用；aware 分支 invokeCommand 的"实例数=1 路由"逻辑删除，过渡为定义级路由（命令 bean 分发于本插件容器，与 R3 目标语义一致；未接通路径显式抛异常，禁止占位 null/空体）；aware start 分支收敛为 load + activate（stop 对应 deactivate + unload）——R3 补两轨回归基线

Exit Criteria:

- [x] `grep -rn "IPluginInstance\|InstanceState\|createInstance\|destroyInstance\|isInstanceConfigMatched" nop-core-framework/nop-plugin/nop-plugin-manager/src/main nop-core-framework/nop-plugin/nop-plugin-support/src/main` 零命中（getPluginId 等无关命中排除；`ICoeffectEvaluator.isInstanceConfigMatched` 签名随本 plan 删除，见 Ownership Deviation）
- [x] `PluginInstanceImpl.java` 文件不存在
- [x] **端到端验证**：一条测试从 `loadPlugin(vfsPath)` → `activatePlugin` → `getService(SomeService.class)` 调用真实 bean 方法 → `deactivatePlugin` → 再调用 getService 代理抛 INACTIVE → `unloadPlugin` 成功，完整走通（单激活流主干）
- [x] **接线验证**：activator 声明的插件激活时 `IPluginActivator.activate(scope, config)` 确实被调用（计数器/标志位断言），返回的 Disposable 在 deactivate 时确实被回退（quiescence：scope.effects() 清空）
- [x] 六态转换断言：LOADED→ACTIVATING→ACTIVATED→DEACTIVATING→LOADED 全链可观测；ACTIVATED 重复 activate 幂等 true 不重跑；ACTIVATED 时 unload 抛明确异常；激活失败置 FAILED 且错误可读；失败超阈值暂停自动激活（迁移语义保持，focused test 断言）
- [x] jar 轨契约 focused test：无 xdef 载体 → 空定义加载成功、门控空集放行、无激活回调仍达 ACTIVATED；有 xdef 载体 → 仍解析持有但门控不受其驱动（无条件激活）、激活回调跳过
- [x] 测试套件收敛后全绿：`./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C`；被删机制专属测试（多实例隔离、parent 层级、实例级 coeffect、快照重建）已删除或收缩为单激活流等价断言（**全面改写归 R4，本处仅编译面+主干行为收敛**，裁定记录于 daily log）
- [x] 下游消费者编译：`./mvnw compile -pl nop-demo/nop-quarkus-demo -am -DskipTests` 通过
- [x] **无静默跳过**：过渡期保留的 reconcile/HMR 路径无新增空方法体/吞异常/占位返回 null 当正常值
- [x] owner docs：`docs 同步 deferred to R4（roadmap 归属）` 显式记录于 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 零依赖不变式与收口验证

Status: completed
Targets: `nop-plugin-api`、`ai-dev/logs/`

- Item Types: `Proof`

- [x] api 零依赖复验：`grep -rn "io.nop.ioc\|io.nop.xlang" nop-core-framework/nop-plugin/nop-plugin-api/src/main` 零命中 + api 模块独立编译
- [x] checkstyle 通过：`./mvnw checkstyle:check -Dcheckstyle.config.location=file://<repo-root>/checkstyle.xml -pl nop-core-framework/nop-plugin/nop-plugin-api,nop-core-framework/nop-plugin/nop-plugin-manager,nop-core-framework/nop-plugin/nop-plugin-support -am`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs nop-core-framework/nop-plugin/nop-plugin-api/src/main nop-core-framework/nop-plugin/nop-plugin-manager/src/main nop-core-framework/nop-plugin/nop-plugin-support/src/main --severity high` 退出码 0（positional paths 形式，模块不在仓库根）

Exit Criteria:

- [x] 上述三项验证命令全部通过并记录输出摘要
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 最终绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 公共 API 面与 01 §7.1/§7.3/§7.6 契约一致（接口签名级核对；显式保留偏差：`loadPlugin(String)` 不加 config 形参、`getLoadedPlugins` 为 §7.3 表外保留的查询方法——二者已在 Phase 2 item 记录，非漂移）
- [x] 实例机制 API（IPluginInstance/InstanceState/createInstance/instanceKey/parent）在三个模块 main 源码零残留（遗留错误码常量按 Phase 1 裁定保留至 R2 退役且有 legacy 注记，audit 确认无处 throw）
- [x] api 模块零依赖不变式成立并有验证证据
- [x] 单激活流端到端测试存在且通过（load→activate→getService→deactivate→INACTIVE→unload）
- [x] 过渡期语义（reconcile 无拓扑序、start/stop 未收敛、实例错误码未退役）已在 plan 内显式标注归属 R2/R3/R4，无静默遗留
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect
- [x] owner docs 同步按 roadmap 归属 R4，本 plan 已显式记录该归属裁定
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）activate→子容器→activator→scope 调用链运行时连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am` 通过
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### docs-for-ai 与设计文档使用侧同步

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap R4 显式拥有 docs-for-ai/03-modules/nop-plugin.md 重写与 INDEX/source-anchors 同步；API 收缩本身的可验证性由代码与测试保证，文档滞后一个 work item 不影响 supported baseline 成立（R1-R4 为同一 mission 内连续交付）。
- Successor Required: `yes`
- Successor Path: R4 plan（后续轮次起草）

### reconcile 拓扑序、实例错误码退役、编排残留清理

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap R2 显式拥有；R1 过渡语义（定义级评估 + 现有迭代收敛）功能完整且测试钉死，残留仅为待清理的实现细节与死代码风险，由 R2 closure gate "零残留引用" 兜底。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/2026-08-22-2309-2-manager-orchestration-simplification.md`

## Non-Blocking Follow-ups

- 实现产物提交交由 mission-driver 统一 commit（W1-W7 先例）。

## Closure

Status Note: R1 三 Phase 全部完成且逐条勾选；独立 closure audit 判定 9 个审计区全部 PASS（契约面逐签名一致、实例机制零残留、api 零依赖不变式、端到端/接线/jar 轨契约测试覆盖、Anti-Hollow 全链追踪、Deferred 诚实性、下游无破坏引用）。audit 提出的唯一 Major（R1 执行日志条目缺失，doc-only）已按 audit 指定补救路径当场修复（`ai-dev/logs/2026/08-23.md`），随后翻门禁关闭。
Completed: 2026-08-23

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (task-fresh session `ses_fd52e5ba8ffeLxeAjjvIL6vdSS`)
- Evidence:
  - 契约面（PASS）：`IPlugin.java`（getState 六态 :93、activate()→boolean :129、deactivate()→CompletionStage<Void> :140、getService(s) :154/:164、无 getInstance/getInstances）与 01 §7.1 一致；`PluginState.java:22-48` 恰为六态；`IPluginContext.java:18/23/32` = getPlugin/allPlugins/reconcile（§7.6）；`IPluginManager.java` 无 createInstance/destroyInstance/getInstance(key)/getInstances，activatePlugin(:53)/deactivatePlugin(:59)/getPlugin/getLoadedPlugins(:66 表外保留裁定)/reloadPlugin/reconcilePlugins（§7.3）；`loadPlugin(String)` 无 config 形参为 Phase 2 记录的显式裁定，非漂移
  - 零残留（PASS）：grep `IPluginInstance|InstanceState|createInstance|destroyInstance|isInstanceConfigMatched` 三模块 main 0 命中；`parentToChildren|ReloadSnapshot|pendingRebuild|allInstances|reconcileInstances` 0 命中；三个被删文件确认不存在；遗留错误码常量为唯一实例机制残留、均有 legacy 注记且无处 throw
  - api 零依赖（PASS）：grep `io.nop.ioc|io.nop.xlang` 0 命中
  - 端到端（PASS）：`TestPluginLifecycle` 14 @Test——testEndToEndSingleActivationFlow（load→activate→getService 真实 bean 调用→deactivate→INACTIVE→unload）、testSixStateTransitionsObservable、testIdempotentActivateDoesNotRerun（含并发单飞）、testUnloadGuardThrowsWhenActivated、testActivationFailureSetsFailedWithReadableError、testFailureThresholdPausesAutoActivation
  - 接线（PASS）：testActivatorWiringAndEffectQuiescence 断言 activatedCount==1、effect 自动注册（2 effects）、deactivate 后 scope.effects()==0、事件序 ["return-disposed","effect-disposed","bean-destroyed"] 证 LIFO + scope.close 先于容器 stop
  - jar 轨契约（PASS）：`TestAbstractPlugin` 9 @Test（无 xdef 载体空定义加载 / 有载体仅元数据 / 空门集无条件达 ACTIVATED / 幂等 / unload 守卫 / 命令路由 / start-stop / 非 aware 兼容）
  - Anti-Hollow（PASS）：VfsPluginDefinition.activate(:371-415) 容器构建（setConfigProvider 先于 start）→activator.activate(scope,config)→返回值自动 scope.effect(:490-494)；deactivate(:417-449) scope.close 先于容器 stop；ServiceProxy.Handler 绑定 plugin.getScope() 激活态；PluginManagerImpl 630 行编排非死代码；独立复跑 scan-hollow-implementations.mjs exit 0、0 findings
  - 门禁命令：`./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` exit 0（BUILD SUCCESS，0 failures/errors）；checkstyle:check（repo checkstyle.xml）exit 0；hollow scan exit 0
  - Deferred 分类核查：docs→R4、编排清理→R2 与 roadmap Work Items 归属一致；Non-Blocking Follow-ups 仅 commit 委托，无 live defect 降级
  - audit Major（日志缺口）补救证据：`ai-dev/logs/2026/08-23.md`（执行事实 + Ownership Deviation 生效记录 + docs deferred to R4 + 验证输出）；audit 3 Minor 均为 R2 域内文档陈旧项，已记入该日志随 R2 处理

Follow-up:

- 无 plan-owned 剩余工作；R2/R3/R4 successor 见 Deferred But Adjudicated 与 roadmap
- R2 顺带项（audit Minor，均已记录于 `ai-dev/logs/2026/08-23.md`）：ERR_PLUGIN_ACTIVE_CHILDREN_EXIST 补 legacy 注记、InstanceConfigProvider.java:32 悬空 `{@link PluginInstanceImpl}`、PluginScopeImpl "instance 级" 措辞——随 R2 类删除/残留审计一并消除
