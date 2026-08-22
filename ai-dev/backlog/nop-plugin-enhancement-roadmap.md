# nop-plugin 增强落地 Roadmap（W1-W7 已完成 + 定位反转重构 R1-R4）

> Status: active
> Last updated: 2026-08-22
> **2026-08-22 定位反转**：W1-W7 落地后，用户裁决删除多实例、依赖收敛到插件级（单层状态机）。反转论证与裁决记录见 `ai-dev/discussions/2026-08/2026-08-14-nop-plugin-design-revision.md` 第 17 轮与 `ai-dev/design/nop-plugin/00-vision.md` §〇。R1-R4 为反转重构 work items；下方 W1-W7 及其 Stage details 为**历史交付记录**，其中被废止机制的描述以反转版设计文档为准。
> Sources（实施前必读）：
> - `ai-dev/design/nop-plugin/00-vision.md`（定位反转裁决 §〇：plugin 框架与 IoC 解耦 + 单层状态机 + 插件级依赖——**权威来源**）
> - `ai-dev/design/nop-plugin/01-architecture-baseline.md`（架构基线 2026-08-22 版：单层六态状态机、接口契约、插件级 coeffect、HMR——**权威来源**）
> - `ai-dev/design/nop-plugin/02-dsh-usage-coverage.md`（dsh 用法覆盖评估：E/I/H 改判 agent 层职责）
> - `ai-dev/design/nop-plugin/03-coeffect-and-agent-example.md`（coeffect + agent 单激活流示例）
> - `ai-dev/design/nop-plugin/04-interface-comparison.md`（与 dsh 接口逐项对比：fiber 有意拒绝论证）
> - `ai-dev/design/nop-plugin/05-artifact-loading-design.md`（artifact 加载 + SHA256 补齐；§七链路终点待 R1 更新）
> - `ai-dev/discussions/2026-08/2026-08-14-nop-plugin-design-revision.md`（17 轮修正过程与纠正记录，第 17 轮 = 定位反转）

**Why**：DeepSeek Harness（dsh/Cordis）调研驱动 W1-W7 增强落地后，2026-08-22 用户按认识论边界裁决定位反转：Nop 已有编译期 Delta 细粒度定制且服务端无状态，plugin 收敛为"粗粒度引入 + 激活门控"，多实例与服务级依赖删除。本 roadmap 的 R1-R4 驱动反转落地。mission 启动命令：`./ai-dev/tools/mission-driver.sh run nop-plugin-enhancement`。

## Work Items

> **这是唯一动态状态块。状态只在这里更新。**
> 人工设定条目与顺序；AI 取第一个 `todo`，起草/执行计划，closure audit 通过后标 `done`。

- W1. plugin.xdef + API 接口层（plugin 专属 schema + nop-plugin-api 新接口含 IPluginContext，零依赖可编译验证）：`done`
- W2. 定义级生命周期 + 双轨来源 + 旧插件兼容（loadPlugin/unloadPlugin 分离、isStateMachineAware 双路径、AbstractPlugin 改造）：`done`
- W3. 实例级生命周期 + effect + activator（createInstance/destroy/activate/deactivate、实例配置域独立 IConfigProvider、IPluginScope 实现、activate 返回值自动注册）：`done`
- W4. getService 生命周期代理 + per-instance 命令路由（强类型代理、INACTIVE 快速失败、primary 多候选规则、invokeCommand 路由）：`done`
- W5. coeffect + reconcile（spec 解析、定义级+实例级评估、环检测、配置订阅自动触发）：`done`
- W6. parent 层级 + HMR（服务查找沿链回退、级联销毁、配置层叠；reloadPlugin 配置快照重建）：`done`
- W7. artifact SHA256 校验补齐 + 测试补全 + docs-for-ai 同步（HttpPluginResourceResolver 补校验、quiescence/多实例隔离测试、使用文档）：`done`
- ★ **Milestone: nop-plugin 增强落地**（W1-W7 全部 done）：`done`
- R1. 定位反转重构：API 收缩——删除 IPluginInstance/createInstance/instanceKey/parent 层级；**全部公共接口签名收缩归 R1**（IPlugin 承载单层六态状态机 activate/deactivate/getService；IPluginContext 重写为 getPlugin/allPlugins/reconcile；IPluginManager 删 createInstance/destroyInstance/getInstance(key)/getInstances 抽象方法）；ServiceProxy 绑定对象改插件激活态；修正 IPlugin Javadoc 过时的 ServiceLoader 描述（实际为 plugin.json+反射）；jar 轨契约显式裁决（无 xdef 载体→无条件激活、activator 缺省跳过）：`done`（plan：`ai-dev/plans/2026-08-22-2309-1-plugin-api-shrink-single-state-machine.md`；Ownership Deviation：IPluginInstance 型结构因编译依赖随 R1 一并移除，R2 为残留审计——见 plan 内裁定；closure audit 见 plan Closure 段 + `ai-dev/logs/2026/08-23.md`）
- R2. manager 编排简化——coeffect 仅插件级（删 ICoeffectEvaluator.isInstanceConfigMatched）、删 parentToChildren/级联销毁/P2-A 快照重建与 pending 机制、删 InstanceConfigProvider/CoeffectConfigHelper、退役实例错误码族（ERR_PLUGIN_INSTANCE_*/INSTANCES_NOT_EMPTY 等）；HMR 去快照重建（deactivate→unload→load 重放 updateConfig 累积值→reconcile）；reconcile 增加跨插件去激活拓扑序（消费者先于提供者退出，01 §三不变量）：`done`（plan：`ai-dev/plans/2026-08-22-2309-2-manager-orchestration-simplification.md`；执行裁定：InstanceConfigProvider 按定义级语义重命名为 DefinitionConfigProvider（R1 已落地定义级配置通道）、宽松比较迁移到 PluginManagerImpl.configValueMatches、checkChangedAndReload 过滤器修正确认 R1 已落地仅跳 UNLOADED、reconcile 拓扑编排 = 级联闭包 + 同 pass 先去激活组（逆拓扑序）后激活组（正拓扑序）+ 时间静止两窗口 focused tests；closure audit 见 plan Closure 段 + `ai-dev/logs/2026/08-23.md`）
- R3. 兼容与命令路由回归——AbstractPlugin/VfsPluginDefinition 兼容语义改 start=load+activate / stop=deactivate+unload（注意两轨现状行为差异：VfsPluginDefinition.start 不含 load 步、AbstractPlugin.start aware 分支为 load+UOE——回归测试按真实行为写基线），invokeCommand 回归定义级路由，plugin.xdef 属性集冻结确认：`done`（plan：`ai-dev/plans/2026-08-22-2309-3-compat-path-command-routing-regression.md`；执行裁定：R1 已落地两轨 start/stop 收敛主体，本 plan 修复 AbstractPlugin.start 状态边界缺口（已 ACTIVATED 重复 start 泄漏容器）与 getCommandBean 宿主回退分叉（裸 cast 对齐 instanceof）、非 aware 静态宿主回退"命中"结构性不可达（containsBean parent 链覆盖宿主名空间，链结构不变即 W2-W4 基线保持）、冻结确认落地机器守护测试 TestPluginXdef.testAttributeSetFrozen；closure audit 见 plan Closure 段 + `ai-dev/logs/2026/08-23.md`）
- R4. 测试改造 + docs-for-ai 同步——多实例隔离/coeffect 实例级测试改写为单激活流断言（含 hasActivatedInstanceByName→定义级 ACTIVATED 的 requires 语义迁移断言、dsh 失效语义终核结论回填 03/04）；docs-for-ai/03-modules/nop-plugin.md 与 INDEX/source-anchors 按新契约重写；（可选）nop-quarkus-demo 依赖收窄至 nop-plugin-api：`done`（plan：`ai-dev/plans/2026-08-23-0405-1-plugin-r4-test-and-docs-sync.md`；执行裁定：R1-R3 已完成测试主体改造，本 plan = 终态对齐审计（死 fixture 清理 IsolatedCommand + 5 文件陈旧/失实注释修正 + 覆盖对照表缺口=0）+ docs 四处同步（nop-plugin.md 全文重写/INDEX/source-anchors PLG 终态化/module-groups）+ dsh 失效语义终核（结论：两路径并存+边界更正——弱读 undefined 仅显式探测 API ctx.reflect.get、属性访问为抛错路径、traceable 代理方法调用无失效检查；回填 03/04 五处）+ Ownership Deviation 回读修正（01 锚点表 R2 行 + :336）+ demo 依赖收窄落地；closure audit 见 plan Closure 段 + `ai-dev/logs/2026/08-23.md`）
- ★ **Milestone: nop-plugin 定位反转重构落地**（R1-R4 全部 done）：`done`

> Milestone 状态是派生的：R1-R4 全部 done 时自动 done。

## Status values

| Status | Meaning |
| --- | --- |
| `todo` | 未开始，无计划 |
| `planned` | 有计划，通过独立 draft review |
| `done` | 完成，通过独立 closure audit |

> Milestone 状态是派生的：W1-W7 全部 done 时自动 done（已完成）；R1-R4 全部 done 时反转重构里程碑自动 done。

## Framework / platform reuse

| Capability | Provider | Notes |
| --- | --- | --- |
| 子容器装配 | `nop-ioc` `AppBeanContainerLoader.loadFromResource(id, resource, parent)` / `BeanContainerImpl.buildNewInstance` | 实例化实现层复用（API 层不引用）；stop→singletonScope.close→destroyBean 链路已有 |
| ~~实例配置域载体~~ | `BeanContainerImpl.setConfigProvider`（可注入 `IConfigProvider`） | 2026-08-22 反转后废止：per-instance provider 随 R2 删除，配置收敛为定义级配置域 |
| XDSL 管线 | `DslModelParser` + XDef（`AbstractDslModel`） | plugin.xdef 走标准 Delta/校验管线；loader 依赖追踪失效已有（ResourceComponentManager） |
| artifact 下载 | `HttpPluginResourceResolver` + `IHttpClient`（nop-http-api） | IHttpClient 注入 + cacheDir + URL 模板 + tmp/move；SHA256 已由 W7 补齐 |
| 类隔离 | `PluginClassLoader`（super parent=JDK CL；经 `shouldImportClass` 模式匹配将平台类路由到 importClassLoader，plugin 类从 jar） | 现状已支持"plugin 用平台全部类、自己类从 jar"；本 roadmap 不改动它 |
| bean 定位 | `BeanContainerImpl.getBeanByType`（primary 语义已有：`BeanModel.primary`） | getService 多候选规则复用 primary |
| 生命周期回调 | `@PostConstruct`/`ILifeCycle`/`<ioc:destroy>` | 子容器内 bean 清理（实现细节，不进 API） |
| 配置订阅 | `DefaultConfigProvider.subscribeChange` | 实现层 reconcile 自动触发（API 层零依赖保持） |

## Current baseline

> **注（2026-08-22）**：本节为 W1-W7 启动前的历史基线记录，保留供追溯；反转后的真实基线 = W1-W7 交付物减去 R1-R4 待删项。

**Already shipped (pre-W1):**
- `IPluginManager.loadPlugin/unloadPlugin`（加载即激活——已由 W2 改造）
- `AbstractPlugin`：plugin=子容器（`loadFromResource(parent=宿主)`）、卸载=stop→自动 destroy
- `PluginClassLoader` 类隔离 + `HttpPluginResourceResolver`（IHttpClient 下载，**无校验**——已由 W7 补齐）
- `IPlugin.invokeCommand`（命令分发经 bean name）
- beans.xml 完整 XDSL（节点级 Delta）——结构层基础已有

**Main gaps (pre-W1，历史记录):**
- 加载与激活耦合（无 LOADED 未激活态）——W2 已解决
- 无 IPluginScope（effect 不可观测、无 quiescence 断言）——W3 已解决
- 无 coeffect（`<ioc:condition>` 仅 build 时）——W5 已解决
- 无 HMR 编排——W6 已解决
- ~~无多实例~~——W6 曾补齐，2026-08-22 反转后为 non-goal（R1 移除）
- API 依赖现状：`IPlugin` 等接口已零依赖 IoC（保持并固化为约束）
- ~~SHA256 未实现~~——W7 已补齐

## Stages

| # | Stage | Owner plan | Deps | Critical path | Reuse |
| --- | --- | --- | --- | --- | --- |
| 1 | plugin.xdef + API 接口层 | plan W1 | — | **Yes** | XDSL 管线/DslModelParser |
| 2 | 定义级生命周期 + 兼容 | plan W2 | W1 | **Yes** | AppBeanContainerLoader/AbstractPlugin |
| 3 | 实例级生命周期 + effect + activator | plan W3 | W2 | **Yes** | setConfigProvider/LifeCycle |
| 4 | getService 代理 + per-instance 命令 | plan W4 | W3 | Yes | getBeanByType primary |
| 5 | coeffect + reconcile | plan W5 | W3 | **Yes** | subscribeChange |
| 6 | parent 层级 + HMR | plan W6 | W3 + W5 | Yes | loader 依赖追踪 |
| 7 | SHA256 补齐 + 测试 + docs 同步 | plan W7 | W3（可与 W4-W6 并行） | No | — |
| ★ | nop-plugin 增强落地（milestone） | — | W1-W7 done | — | — |
| R1 | 反转重构：API 收缩（删实例机制） | plan R1 | — | **Yes** | ServiceProxy/IPluginScope 复用 |
| R2 | manager 编排简化（插件级 coeffect + HMR 简化） | plan R2 | R1 | **Yes** | reconcile 管线/环检测 |
| R3 | 兼容路径与命令路由回归 | plan R3 | R1 | Yes | AbstractPlugin |
| R4 | 测试改造 + docs-for-ai 同步 | plan R4 | R1-R3 | No | — |
| ★ | nop-plugin 定位反转重构落地（milestone） | — | R1-R4 done | — | — |

### R1. 定位反转重构：API 收缩

**Goal:** `nop-plugin-api` 公共 API 收缩到单层状态机契约（Protected Area plan-first：跨模块公共 API 变更，破坏性收缩——当前无外部消费者，grep 已核验）。

**Deliverables:**
- 删除 `IPluginInstance` 及 `PluginState`/`InstanceState` 双枚举 → 合并为单组六态枚举（UNLOADED/LOADED/ACTIVATING/ACTIVATED/DEACTIVATING/FAILED）
- `IPlugin` 承载 activate/deactivate/getService/getServices（语义见 01 §7.1）；删除 createInstance/getInstance(key)/getInstances 相关 default 方法
- `IPluginContext` 重写为 getPlugin/allPlugins/reconcile（删 getInstance/allInstances）；`IPluginManager` 删 createInstance/destroyInstance/getInstance(key)/getInstances 抽象方法（非 default，编译阻塞面）
- `ServiceProxy` 绑定对象从 instance 改 plugin 激活态；多候选规则不变
- jar 轨契约落地：无 xdef 载体→无条件激活（requires/if-property 空集）、activator 缺省跳过；修正 `IPlugin.java` Javadoc 过时的 ServiceLoader 描述（实际为 plugin.json+反射）
- api 模块零依赖不变式复验（不 import `io.nop.ioc`/`io.nop.xlang`）

**Out of scope:** manager 编排实现清理（R2）、兼容路径（R3）。

### R2. manager 编排简化

**Goal:** coeffect/HMR/reconcile 对齐反转后语义。

**Deliverables:**
- 删除实例级 coeffect 评估（`ICoeffectEvaluator.isInstanceConfigMatched`）、parentToChildren 映射、级联销毁、P2-A 快照/pending 重建逻辑
- 删除实例配置域载体 `InstanceConfigProvider`/`CoeffectConfigHelper`；退役实例错误码族（ERR_PLUGIN_INSTANCE_*/INSTANCES_NOT_EMPTY 等）
- `reconcilePlugins()`：仅插件级 requires/if-property 评估（不动点迭代 + DFS 环检测保留）；新增跨插件去激活拓扑序（消费者先于提供者退出，01 §三不变量）
- `reloadPlugin`：deactivate→unload→load（重放 updateConfig 累积值）→reconcile；删除 P2-A 实例快照流程
- 激活窗口时间静止语义保留（完成再收敛）

**Out of scope:** 兼容路径（R3）。

### R3. 兼容路径与命令路由回归

**Goal:** 存量第三方插件兼容语义与新命令路由对齐。

**Deliverables:**
- `AbstractPlugin`/`VfsPluginDefinition`：start = load + activate、stop = deactivate + unload（删除默认实例 key）。注意两轨现状差异基线：`VfsPluginDefinition.start` 不含 load 步、`AbstractPlugin.start` aware 分支为 load+UOE——回归测试按真实行为写
- `invokeCommand/invokeCommandAsync` 回归定义级路由（单容器；删除 per-instance 路由与"实例数=1 才路由"规则）
- plugin.xdef 属性集冻结确认（requires/if-property/activator；不新增 requires-service）

### R4. 测试改造 + docs-for-ai 同步

**Goal:** 测试面与使用者文档对齐新契约。

**Deliverables:**
- 多实例隔离/coeffect 实例级测试改写为单激活流断言（两态转换、门控激活/去激活、quiescence、HMR、INACTIVE 代理失效）；含 `hasActivatedInstanceByName` → 定义级 ACTIVATED 的 requires 语义迁移断言
- dsh 失效语义终核结论回填 `03`/`04` 统一注解
- 删除 parent 层级/级联销毁/实例配置域相关测试
- `docs-for-ai/03-modules/nop-plugin.md` 重写（单层状态机 + 插件级依赖定位）+ INDEX/source-anchors 同步
- （可选）nop-quarkus-demo 依赖收窄至 nop-plugin-api

## Stage details

> **历史记录横幅（2026-08-22）**：以下 Stage 1-7 为 W1-W7 历史交付描述，其中 createInstance/实例配置域/parent 层级/P2-A 快照等机制已被定位反转废止，去向见 Work Items 的 R1-R4。R 系列细节见上方 Work Items 描述（反转后以 [`01-architecture-baseline`](../design/nop-plugin/01-architecture-baseline.md) 2026-08-22 版为权威）。

### 1. plugin.xdef + API 接口层

> Status: see Work Items above

**Goal:** 建立 plugin 专属结构层 schema 与零依赖 API 契约（全部后续工作的地基）。

**Deliverables:**
- plugin.xdef（`_vfs/nop/schema/plugin/plugin.xdef`）：`requires`/`if-property`/`activator` 原生属性 + `<beans>` 子元素（复用 bean 定义模型）；不改 beans.xdef
- `nop-plugin-api` 新接口：`IPluginInstance`/`IPluginScope`/`IPluginActivator`（`activate(scope, config)` 返回 `Disposable`）/`IPluginContext`（实例 registry + reconcile 契约，纯 JDK 类型保持零依赖）/`Disposable`（自定函数式接口）/`PluginState`/`InstanceState` 枚举
- `IPlugin` 增强 default 方法（`load/unload/getState/getInstance/getInstances/isStateMachineAware`）+ 既有方法态语义
- api 模块零依赖编译验证（不 import `io.nop.ioc`/`io.nop.xlang`）

**Out of scope:** 任何实现（W2+）、beans.xdef 修改（明确不做）。

**Module / area:** `nop-plugin-manager/_vfs/...`（xdef）、`nop-plugin-api/src/...`。

### 2. 定义级生命周期 + 双轨来源 + 旧插件兼容

> Status: see Work Items above

**Goal:** loadPlugin 与激活解耦（UNLOADED→LOADED），存量插件无感兼容。

**Deliverables:**
- `PluginManagerImpl`：load/unload 按 plugin.xdef 定义驱动（本地 VFS `*.plugin.xml` / uber jar 双轨，按 id 类型路由）
- `AbstractPlugin` 改造：start/stop 收敛为 load+createInstance / destroy+unload 的 default 封装；`AppConfig.assignConfigValue` 全局写入仅保留在兼容路径
- `isStateMachineAware` 检测 + 非 aware 旧插件回退路径（start 语义=load+activate）
- uber jar 路径经 `IPluginResourceResolver` 接线到新状态机

**Out of scope:** 实例生命周期细节（W3）。

**Module / area:** `nop-plugin-manager/impl`、`nop-plugin-support/AbstractPlugin`。

### 3. 实例级生命周期 + effect + activator

> Status: see Work Items above

**Goal:** createInstance/destroy/activate/deactivate 全链路 + effect 自管理 + 参数传递激活。

**Deliverables:**
- `PluginInstanceImpl`：固定实例对象、destroy 只销毁内部（scope.close 先于子容器 stop）、in-flight 单飞串行化、同 key 重复抛异常
- 实例配置域：per-instance `IConfigProvider`（全局+实例配置合并视图）注入子容器；`instance.getConfig()` 任何态可读
- `PluginScopeImpl`：effect/effects(LIFO close)/close 幂等 + getService/getServices（委托实例代理，多候选规则同设计 7.2）；activate 返回值非 null 自动 `scope.effect(returned)`
- activator 调用链：实例化子容器→定位 activator bean→`activate(scope, config)`；deactivate→activate 重跑
- unload 守卫：unload 定义前须 destroy 全部实例（有实例抛异常，设计 01 §三不变量）

**Out of scope:** instance.getService 生命周期代理的生成（W4）、coeffect（W5）。

**Module / area:** `nop-plugin-manager`（或 support）、`PluginClassLoader` 不变。

### 4. getService 生命周期代理 + per-instance 命令路由

> Status: see Work Items above

**Goal:** 强类型服务访问 + 多实例命令隔离。

**Deliverables:**
- getService(Class)/getServices(Class)：primary 优先→唯一实现→多候选抛异常；生命周期绑定代理（deactivate/destroy 后抛 INACTIVE）
- `IPluginInstance.invokeCommand/invokeCommandAsync`（路由到本实例子容器，复用现有命令 bean 分发）
- `IPlugin.invokeCommand` 兼容规则：仅实例数=1 时路由，多实例抛明确异常

**Out of scope:** coeffect（W5）；scope.getService 已随 W3 实现，此处仅测试校验。

**Module / area:** `nop-plugin-manager`（代理生成）。

### 5. coeffect + reconcile

> Status: see Work Items above

**Goal:** 定义级+实例级条件激活，运行时动态。

**Deliverables:**
- coeffect spec 解析（plugin.xdef 的 requires/if-property）
- `IPluginContext.reconcile()`（由 PluginManagerImpl 实现，不另起独立 Impl 类）：定义级（能否派生实例）+ 实例级（基于 instance 配置域，实时合并视图 + 全局回退）；迭代到不动点 + 静态环检测；失败置回并重试（超阈值暂停）
- 实现层配置订阅（subscribeChange）自动触发 reconcile（API 层只暴露显式 reconcile()）
- activatePlugin 对应语义：createInstance 定义级不满足 no-op 返回 null

**Out of scope:** HMR（W6）。

**Module / area:** `nop-plugin-manager`。

### 6. parent 层级 + HMR

> Status: see Work Items above

**Goal:** subagent 层级实例化 + 定义变更热重载。

**Deliverables:**
- createInstance(parent)：服务查找沿链回退（子容器 parent=父实例容器→宿主）、destroy 父级联 destroy 子、配置层叠（子覆盖父）
- reloadPlugin：destroy 全部实例→unload→load→按 createInstance 配置快照重建→reconcile（快照由 manager 持有——第七轮 P2-A 裁决点）
- loader 依赖追踪接线（plugin.plugin.xml 变更→失效→reload 编排）

**Out of scope:** 多租户配置命名空间（实例配置域已覆盖基础）。

**Module / area:** `nop-plugin-manager`。

### 7. SHA256 补齐 + 测试补全 + docs-for-ai 同步

> Status: see Work Items above

**Goal:** 安全底线补齐 + 质量门 + 使用者文档。

**Deliverables:**
- `HttpPluginResourceResolver` 补 SHA256：下载后 move 前校验（`.sha256` 文件/响应 header）、fail-fast、缓存重验（05 §五/§六）
- 测试：quiescence 断言（effects 清空）、多实例隔离（agent-1/agent-2）、coeffect 依赖链激活/去激活、HMR reload、兼容路径回归
- `docs-for-ai/` plugin 使用文档（两态/多实例/coeffect/getService/activator）+ INDEX 登记

**Out of scope:** 远程仓库元数据服务（hash 分发方式实现时最小化）。

**Module / area:** `nop-plugin-manager/resolver`、`nop-plugin-*` tests、`docs-for-ai/`。

## Dependency graph

```mermaid
graph TD
    W1["W1. plugin.xdef + API 接口层"]
    W2["W2. 定义级生命周期 + 兼容"]
    W3["W3. 实例级 + effect + activator"]
    W4["W4. getService 代理 + 命令路由"]
    W5["W5. coeffect + reconcile"]
    W6["W6. parent 层级 + HMR"]
    W7["W7. SHA256 + 测试 + docs"]
    M["★ 增强落地（已完成，被反转取代）"]
    R1["R1. 反转：API 收缩"]
    R2["R2. manager 编排简化"]
    R3["R3. 兼容与命令路由回归"]
    R4["R4. 测试改造 + docs 同步"]
    M2["★ 反转重构落地"]
    W1 --> W2 --> W3
    W3 --> W4
    W3 --> W5
    W3 --> W7
    W5 --> W6
    W3 --> W6
    W1 --> M
    W2 --> M
    W3 --> M
    W4 --> M
    W5 --> M
    W6 --> M
    W7 --> M
    R1 --> R2
    R1 --> R3
    R1 --> R4
    R2 --> R4
    R3 --> R4
    R1 --> M2
    R2 --> M2
    R3 --> M2
    R4 --> M2
```

## Cross-cutting concerns

| Concern | Notes |
| --- | --- |
| Verification baseline | 每 stage 后：`./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 绿 |
| API 零依赖不变式 | 每个 plan 的 closure 必须验证 api 模块不 import `io.nop.ioc`/`io.nop.xlang`（00 §五成功标准 4） |
| IoC 只作实现层 | 任何公开接口不得出现 `IBeanContainer`/`BeansModel`；子容器/destroy 是实现细节（01 §一） |
| effect 自管理 | IPluginScope 不观测 IoC 内部；可逆性由 scope 自证（01 §四） |
| 参数传递模式 | activator 必须 `activate(scope, config)` 双参数，禁止字段注入 scope（why-springbatch-is-bad.md §3.1） |
| 兼容不破坏 | 存量第三方 IPlugin 实现（仅 start/stop）必须继续工作（isStateMachineAware 双路径） |
| 待裁决点（第七轮放行条件） | ~~P2-A HMR 配置快照、P2-B 重复 key、P2-C updateConfig×实例合并视图、P2-D deactivate 父实例语义~~（2026-08-22 定位反转后随实例机制废止；HMR 仅重放定义级 updateConfig 累积值） |
| 定位反转基线 | R1-R4 以 [`00-vision.md` §〇](../design/nop-plugin/00-vision.md) + [01-architecture-baseline](../design/nop-plugin/01-architecture-baseline.md)（2026-08-22 版）为权威；与 W 系列裁决冲突时以反转版为准 |
| 设计文档为权威 | 接口语义以 `01-architecture-baseline.md` 为准；plan 与实现冲突时回读设计 |
| Protected Area 程序性标注 | `nop-plugin-api` 公共 API 变更命中 AGENTS.md "跨模块公共 API → plan-first"；mission 流程（plan 起草 + 独立 draft review）即 plan-first 的满足形式。**R1 为破坏性收缩**（删接口删方法，非 default 可兜底）——合规依据 = "当前无外部消费者"事实（2026-08-22 三 agent 审计 grep 复核属实）+ 独立 closure audit 兜底 |

## Rules

- This file is a state index and coarse decomposition, not an execution plan.
- Each `planned` stage is owned by its execution plan.
- Status changes happen only in the Work Items block at the top.
- Milestones are derived: W1-W7 all `done` → 增强落地 milestone done（已完成）；R1-R4 all `done` → 反转重构落地 milestone done。
