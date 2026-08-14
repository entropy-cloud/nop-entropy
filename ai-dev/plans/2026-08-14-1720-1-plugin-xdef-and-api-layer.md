# 1 plugin.xdef + API 接口层（W1）

> Plan Status: draft
> Mission: nop-plugin-enhancement
> Work Item: W1 plugin.xdef + API 接口层
> Last Reviewed: 2026-08-14
> Source: `ai-dev/design/nop-plugin/01-architecture-baseline.md`（§3.1/§3.4/§3.5/§3.6/§7）；`ai-dev/design/nop-plugin/00-vision.md`（§五 成功标准 4）；`ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（Stage 1）
> Related: `ai-dev/plans/2026-08-14-1720-2-definition-lifecycle-dual-track.md`（执行顺序 2，依赖本 plan 产出）、`ai-dev/plans/2026-08-14-1720-3-instance-lifecycle-effect-activator.md`（执行顺序 3）

## Purpose

把 nop-plugin 增强落地的地基打平：建立 plugin 专属结构层 schema（plugin.xdef，不改 beans.xdef）与零依赖 API 契约（IPluginInstance/IPluginScope/IPluginActivator/IPluginContext/Disposable/PluginState/InstanceState + IPlugin 增强 default 方法）。本 plan 只产出契约与 schema，不产出任何生命周期实现（W2+）。

## Current Baseline

- `nop-core-framework/nop-plugin/` 下三模块：`nop-plugin-api`（pom 零依赖）、`nop-plugin-manager`（依赖 nop-plugin-api + nop-ioc + nop-http-api）、`nop-plugin-support`（依赖 nop-xlang + nop-ioc + nop-plugin-api）。
- `nop-plugin-api` 现有 4 个类型：`IPlugin`（start/stop/updateConfig/invokeCommand 等）、`IPluginCancelToken`、`IPluginCommand`、`NopPluginConstants`（`IPlugin.java:11` 起，无 getState/load/unload/实例概念）。
- `IPluginManager`（`manager/IPluginManager.java`）现有 `loadPlugin(ArtifactCoordinates)/unloadPlugin/getLoadedPlugins`——加载即激活耦合（`PluginManagerImpl.loadPlugin` 直接 `plugin.start(...)`，`impl/PluginManagerImpl.java:45-61`）。
- 不存在 `IPluginInstance`/`IPluginScope`/`IPluginActivator`/`IPluginContext`/`Disposable`/`PluginState`/`InstanceState` 任何类型。
- 不存在 plugin 专属 schema：`nop-plugin-manager/src/main/resources/_vfs/nop/schema/plugin/` 目录不存在；`beans.xdef`（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef`）是全平台共享 schema，`BeansModel` 及其 bean 定义模型（`xdef:define` BeanPropValue/BeanValue 等）是 `<beans>` 子元素复用的对象。
- 已有 `nop-plugin-manager/src/main/resources/_vfs/nop/plugin/xlib/plugin.xlib`（xpl 标签库，与 schema 无冲突）。
- 平台已有 `io.nop.api.core.ioc.BeanContainer`（api-core，非 nop-ioc）——plugin API 零依赖 IoC 类型指不 import `io.nop.ioc.*` 与 `io.nop.xlang.*` 类型（`01-architecture-baseline.md` §7.6 成功标准 4）。

## Goals

- `nop-plugin-api` 新增 7 个类型：`IPluginInstance`、`IPluginScope`、`IPluginActivator`、`IPluginContext`、`Disposable`（自定 `@FunctionalInterface`）、`PluginState`（UNLOADED/LOADED）、`InstanceState`（ACTIVATED/DEACTIVATED），语义严格按 `01-architecture-baseline.md` §7.2/§7.4/§7.5/§7.6。
- `IPlugin` 新增 default 方法：`getState()`、`load(config)`、`unload()`、`getInstance(instanceKey)`、`getInstances()`、`isStateMachineAware()`；既有方法（invokeCommand/updateConfig/start/stop）在 javadoc 中明确新状态机下的态语义（§7.1 表）。
- 新增 `plugin.xdef`（`nop-plugin-manager/src/main/resources/_vfs/nop/schema/plugin/plugin.xdef`）：`requires`/`if-property`/`activator` 为原生属性，`<beans>` 子元素复用 beans.xdef 的 bean 定义模型；不改 `beans.xdef`。
- `nop-plugin-api` 零依赖 IoC 不变式编译期可验证（api 模块源码不 import `io.nop.ioc` / `io.nop.xlang` 任何类型）。

## Non-Goals

- 不实现任何生命周期行为（load/unload/activate 的实现、PluginManagerImpl 改造、AbstractPlugin 改造）——属于 W2。
- 不实现实例生命周期（createInstance/destroy/effect 落地）——属于 W3。
- 不修改 `beans.xdef`（全平台共享 schema，Protected Area）。
- 不新增 `IPluginResourceResolver` 之外的新加载器接口（05 设计 §八 拒绝项 5）。
- 不在本 plan 修改 `IPluginManager` 接口签名（W2 按需处理）。

## Scope

### In Scope

- `nop-plugin-api`：7 个新类型 + `IPlugin` default 方法增强（含态语义 javadoc）。
- `nop-plugin-manager` 资源：`_vfs/nop/schema/plugin/plugin.xdef` 新文件。
- 零依赖不变式验证（grep/编译期）。
- plugin.xdef 可解析性验证（示例 `*.plugin.xml` 经 DslModelParser 成功解析、无效输入校验报错）。

### Out Of Scope

- 一切实现层代码（manager/support 的 Java 逻辑改动）。
- beans.xdef 的任何修改。
- 测试基础设施大改（沿用 JUnit 5；新增测试仅用于证明本 plan 产物成立）。

## Execution Plan

### Phase 1 - API 新类型定义

Status: planned
Targets: `nop-core-framework/nop-plugin/nop-plugin-api/src/main/java/io/nop/plugin/api/`

- Item Types: `Fix`

- [ ] 新增 `Disposable`：`@FunctionalInterface`，单方法 `void dispose()`，纯 JDK 类型（§7.4）。
- [ ] 新增 `PluginState` 枚举（UNLOADED/LOADED）与 `InstanceState` 枚举（ACTIVATED/DEACTIVATED）（§三/§7.1/§7.2）。
- [ ] 新增 `IPluginActivator`：`Disposable activate(IPluginScope scope, Map<String,Object> config)` 双参数签名，javadoc 写明"返回值非 null 自动注册为该实例 effect"（§7.5）。
- [ ] 新增 `IPluginInstance`：`getInstanceKey/getState/activate/deactivate/getScope/getService/getServices/getParent/getConfig/invokeCommand/invokeCommandAsync/destroy`，javadoc 写明 §7.2 语义（getConfig 任何态可读、getService 多候选规则、getScope 非 ACTIVATED 返回 null、deactivate 保留实例对象）。
- [ ] 新增 `IPluginScope`：`effect/effects/close/getService/getServices`，javadoc 写明 LIFO 回退、close 幂等、close 后注册抛异常（§7.4）。
- [ ] 新增 `IPluginContext`：`getInstance(pluginId, instanceKey)/allInstances()/reconcile()`，javadoc 写明只做 registry + coeffect reconcile、不暴露宿主容器（§7.6）。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-api -q` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 7 个新类型文件存在于 `nop-plugin-api/src/main/java/io/nop/plugin/api/`，javadoc 语义与 `01-architecture-baseline.md` §7.2/§7.4/§7.5/§7.6 逐条对应（closure audit 时抽查）。
- [ ] 接口中不出现 `IBeanContainer`/`BeansModel` 及任何 `io.nop.ioc.*`/`io.nop.xlang.*` import（grep 验证，见 Phase 3 的零依赖检查项）。
- [ ] `./mvnw compile -pl :nop-plugin-api -q` 退出码 0。
- [ ] 无静默跳过：本 phase 不引入任何空方法体/placeholder 实现（纯接口与枚举，无实现体）。
- [ ] No new test required: 本 phase 产物为纯接口/枚举声明（`Disposable` 为函数式接口、两个枚举为值定义），无可执行行为逻辑；行为的测试覆盖由 Phase 2 的默认方法行为测试与 Phase 3 的 xdef 解析测试承担，closure audit 以编译通过 + 后续 phase 测试验证本 phase 产物。
- [ ] No owner-doc update required（纯新增契约类型，未改变既有行为）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - IPlugin default 方法增强

Status: planned
Targets: `nop-plugin-api/src/main/java/io/nop/plugin/api/IPlugin.java`

- Item Types: `Fix | Decision`

- [ ] 新增 default 方法 `isStateMachineAware()`，默认返回 false（§7.1 兼容机制）。
- [ ] 新增 default 方法并**钉死默认行为**（供 W2 兼容路由消费，避免实现期发明）：
  - `getState()` 默认返回 `LOADED`（保守值——插件对象仅经 `loadPlugin` 成功后可见，unload 后从 manager 移除；非 aware 插件不存在"可见的 UNLOADED"窗口）；
  - `getInstance(String)` 默认返回 null、`getInstances()` 默认返回空列表（非 aware 无实例概念）；
  - `load(Map)`、`unload()` 默认抛 `UnsupportedOperationException`（非 aware 不进入新状态机；PluginManagerImpl 对非 aware 永不调用它们——No Silent No-Op，不写空方法体）；
  - 每个 default 方法 javadoc 写明上述行为与"非 aware 插件不进入新状态机"。
- [ ] 既有方法 javadoc 补充新状态机下的态语义：`invokeCommand`（实例数=1 路由、多实例抛异常、无 ACTIVATED 实例抛 INACTIVE）、`updateConfig`（DEACTIVATED 缓存待激活应用 / ACTIVATED 热应用）、`start/stop`（start = load + createInstance(默认 key)；stop = destroyInstance + unload）（§7.1 表）——仅文档，不改签名。
- [ ] 新增测试（`nop-plugin-api`）：测试内建一个最小 `IPlugin` 实现（不做任何 override），断言 6 个 default 方法的实际行为：`isStateMachineAware()` false、`getState()` LOADED、`getInstance` null、`getInstances` 空、`load`/`unload` 抛 `UnsupportedOperationException`——把 default 行为固化为可回归测试（Guide Rule 25）。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-api -q` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `IPlugin.java` 中 6 个 default 方法存在，默认行为与上表一致（测试断言 + closure audit 读代码确认）。
- [ ] 新增方法均为 `default`，未改变既有抽象方法签名（存量第三方实现不受影响——二进制兼容）。
- [ ] default 行为测试存在且通过（覆盖 isStateMachineAware/getState/getInstance/getInstances/load/unload 六项）。
- [ ] **无静默跳过**：`load`/`unload` default 显式抛 `UnsupportedOperationException`（测试断言），无空方法体；既有空 `updateConfig` default（`IPlugin.java:36-38`，改造前已存在）不在本 plan 范围（其语义由 W2 兼容路径与 W3 实例配置域接管，anti-hollow 声明仅覆盖本 plan 新增方法）。
- [ ] `./mvnw compile -pl :nop-plugin-api -q` 退出码 0。
- [ ] No owner-doc update required（仅契约文档增强；API 变更的正式记录在 W7 docs-for-ai 同步）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - plugin.xdef 新增 + 可解析性验证

Status: planned
Targets: `nop-plugin-manager/src/main/resources/_vfs/nop/schema/plugin/plugin.xdef`

- Item Types: `Fix | Proof`

- [ ] 新增 `plugin.xdef`（`nop-plugin-manager/src/main/resources/_vfs/nop/schema/plugin/plugin.xdef`），**采用动态模型模式**（不声明 `xdef:bean-package`/`xdef:name`，根模型即 `DynamicObject`，无需 codegen 生成 `_gen` 模型类；in-repo 先例：`nop-ai/nop-ai-dsl-orm/src/main/resources/_vfs/nop/schema/gpt/orm.xdef` 同样无 bean-package/name 且无生成类）。
- [ ] 根元素属性按 `03-coeffect-and-agent-example.md` §1.2 示例钉死（这是 W2/W5 消费的契约面）：`name="!string"`（plugin 名）、`requires="csv-set"`（依赖的 plugin id 集合，空格分隔）、`if-property="string"`（格式 `propName|expectedValue`，缺省 expectedValue 视为 true，如 `agent.tools.enabled|true`）、`activator="bean-name"`（激活器 bean id）。
- [ ] `<beans>` 子元素以 `xdef:ref="/nop/schema/beans.xdef"` 引用 beans.xdef 根（**in-repo 先例：`nop-kernel/nop-xdefs/.../schema/task/task.xdef:24` `<beans xdef:ref="../beans.xdef"/>`**）——跨文件 ref 仅解析目标文件根节点（XDefRefResolver 语义），故插件 `<beans>` 直接合并 BeansModel 根，bean 定义模型完整复用；`beans.xdef` 本身零修改。
- [ ] 确认 xdef 走标准 XDSL 管线（`DslModelParser` + Delta/校验）——新 schema 文件位于本模块 classpath `_vfs`，属附加式新增，无共享 schema 变更。
- [ ] 新增测试（`nop-plugin-manager`，依赖传递已含 nop-xlang/nop-xdefs/io.nop.ioc.model）：最小合法 `*.plugin.xml` 示例（含 name/requires/if-property/activator + `<beans>` 子元素含 bean）经 `DslModelParser` 成功解析；**断言具体字段**——根为 DynamicObject 且 name/requires/if-property/activator 可读、`<beans>` 子节点解析为 beans 模型（含 bean id/class）；非法输入（未知属性）校验报错——证明 schema 真正被管线消费（非空壳）。
- [ ] 零依赖不变式验证：grep 扫描 `nop-plugin-api/src/main/java` 无 `io.nop.ioc` / `io.nop.xlang` import（可固化为脚本或验证命令记录于 plan）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `plugin.xdef` 文件存在、采用动态模型模式（无 bean-package/name）、`beans.xdef` 未被修改（git diff 确认）。
- [ ] **接线验证**：解析测试真实调用 `DslModelParser` 读取新 schema 并解析示例文件成功，name/requires/if-property/activator 字段与 `<beans>` 子模型字段可读断言通过；非法输入报错（证明 xdef 被运行时消费）。
- [ ] **无静默跳过**：非法输入测试断言抛异常（schema 校验真实生效）。
- [ ] 零依赖检查：grep 结果 0 命中（api 模块无 `io.nop.ioc`/`io.nop.xlang` import）。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 全绿。
- [ ] No owner-doc update required（schema 为新增，未改变既有文档描述的行为；正式使用文档在 W7）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 7 个新 API 类型 + `IPlugin` 6 个 default 方法落地，语义与设计文档一致（无 contract drift）。
- [ ] `plugin.xdef` 新增且 `beans.xdef` 零改动。
- [ ] `nop-plugin-api` 零依赖 IoC 不变式验证通过（grep 0 命中）。
- [ ] 新 schema 可解析性 + 非法输入报错测试通过（非空壳证明）。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 项。
- [ ] No owner-doc update required（文档同步归属 W7）。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）plugin.xdef 确实被 DslModelParser 消费（解析测试真实跑通），（b）无空方法体/静默跳过/no-op 作为正常实现（IPlugin 非 aware default 显式失败）。
- [ ] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### IPlugin 非 aware 默认行为的具体返回值

- Classification: `watch-only residual`
- Why Not Blocking Closure: 设计文档只规定"default 收敛到状态机、不破坏第三方实现"（§7.1）；具体到 `getState()` 对从未进入新状态机的 legacy 实例返回何值，W2 实现 PluginManagerImpl 路由时以实际路径（aware 走 load/非 aware 走 start）为准，W2 plan 中裁定。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/2026-08-14-1720-2-definition-lifecycle-dual-track.md`

## Non-Blocking Follow-ups

- 将零依赖检查固化为 CI 脚本/检查项（当前以 plan 记录 + closure gate 执行）。
- plugin.xdef 的 XDef 代码生成模型类（`_gen`）是否启用，随 W2 需要再定。

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

- plugin.xdef 若在解析测试中发现与 beans.xdef 复用方式不兼容（xdef:ref 引用细节），回退方案：改为在 plugin.xdef 内联声明 bean 属性子集（不引用 beans.xdef 的 define），仅影响新 schema 文件本身，blast radius 为零。
- `IPlugin` 新增 default 方法若与存量第三方实现（如 DefaultPlugin）冲突，default 方法机制保证二进制兼容；行为冲突在 W2 兼容路径测试中暴露并修复。
