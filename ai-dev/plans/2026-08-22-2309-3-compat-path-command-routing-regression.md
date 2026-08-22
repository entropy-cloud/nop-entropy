# 3. nop-plugin 定位反转 R3：兼容路径与命令路由回归

> Plan Status: completed
> Mission: nop-plugin-enhancement
> Work Item: R3（兼容与命令路由回归）
> Last Reviewed: 2026-08-23
> Source: `ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（R3 条目）；`ai-dev/design/nop-plugin/01-architecture-baseline.md`（§三 兼容语义、§7.1 invokeCommand/start/stop、§7.6 属性集冻结）
> Related: 前置 `2026-08-22-2309-1`（R1：API 收缩 + activate()/deactivate() 双轨落地 + VFS 轨 start/stop 直接收敛）；`2026-08-22-2309-2`（R2，可并行——本 plan 仅依赖 R1；实例错误码常量退役属 R2，与本 plan 执行顺序无关）；后续 R4（测试+docs）

## Purpose

把存量第三方插件兼容语义（start/stop 双轨）与命令路由收敛到反转后契约——`start = load + activate`、`stop = deactivate + unload`、`invokeCommand` 回归定义级路由（单容器）——并按两轨**真实行为差异**写回归基线测试，最终冻结 plugin.xdef 属性集。

## Current Baseline

（2026-08-22 依据 live repo 核验的 **R1 前**两轨现状差异（回归基线的差异素材）；R1 落地后 VFS 轨 start/stop 与 AbstractPlugin 的 activate()/deactivate() 已按 R1 plan 收敛——**执行本 plan 前必须以 R1 落地后的 live repo 复核并修正本节**）

- **`VfsPluginDefinition.start`（VFS 轨，523 行文件内，R1 前现状）**：`checkLoaded()` + `createInstance(DEFAULT_INSTANCE_KEY, config, null)`——**不含 load 步**（要求已 LOADED），门控不满足时记 warn 日志不创建实例；`stop` = destroy 默认 key 实例 + unload。R1 plan 已裁定 start/stop 直接收敛为 start = load + activate / stop = deactivate + unload（编译强制）并消除 `PluginManagerConstants.DEFAULT_INSTANCE_KEY` 的 main 引用——常量本体删除归属本 plan（roadmap R3 交付物；R2 plan Out Of Scope 已互认）
- **`AbstractPlugin.start` aware 分支（jar 轨，281 行文件内 164-173 行，R1 前现状）**：`load(config)` 后抛 `UnsupportedOperationException`；非 aware 分支 = 旧语义（AppConfig.assignConfigValue 全局写入 + doStart 子容器）。R1 plan 已裁定落地 `activate()`/`deactivate()` 最小实现（容器构建复用 doStart 路径、六态迁移、门控恒空集无条件激活、激活回调跳过——01 §二 jar 轨契约原文语义）并将 aware start/stop 收敛为 load + activate / deactivate + unload——本 plan 验证收敛结果并补回归基线
- **`invokeCommand` 定义级兼容规则（W4 落地，R1 前现状）**：aware = 实例数=1 经该实例路由 / >1 抛 ERR_PLUGIN_MULTIPLE_INSTANCES / =0 抛 INACTIVE（`VfsPluginDefinition` 与 `AbstractPlugin` 两实现点）；非 aware = **插件自有容器优先、宿主容器回退**的命令 bean 分发（`getCommandBean`：this.beanContainer → BeanContainer.tryGetBean → 宿主 getBean；BEAN_NOP_PLUGIN_COMMAND_PREFIX + command，default bean 兜底）。R1 删 IPluginInstance 后该规则的实例路由部分已失效——本 plan 钉死**定义级单容器路由**：aware = 命令 bean 分发于本插件容器（复用 getCommandBean 既有回退链：插件容器 → 宿主回退 → default bean 兜底），未激活抛 INACTIVE；非 aware = 现状不变
- `plugin.xdef` 属性集 = name/requires/if-property/activator + `<beans>`（W1 冻结形态）+ xdef 标准属性；无 requires-service
- 兼容路径回归测试现状：W2-W4 建立的 isStateMachineAware 双路径测试在 R1 测试收缩后部分存留，start/stop 新语义与定义级路由无系统化回归基线
- 消费者：`nop-quarkus-demo/DemoPluginCommand`（IPluginCommand 实现，非 aware 命令分发路径的真实下游）

## Goals

- 验证并收敛 `AbstractPlugin` aware 分支 start/stop 语义：`start(gav, config)` = `load(config) + activate()`、`stop()` = `deactivate() + unload()`（activate()/deactivate() 本体由 R1 交付——若 R1 未交付，本 plan Phase 1 第一项复核将暴露并升级为显式实现项）；非 aware 分支保持旧语义不变（存量第三方插件零感知）
- 验证并收敛 `VfsPluginDefinition` start/stop 语义（R1 已收敛）：start = load + activate、stop = deactivate + unload；确认 `DEFAULT_INSTANCE_KEY` 全仓零引用
- start/stop 状态边界规格：已 LOADED 时 start 不重复 load（保留 updateConfig 累积值）直接 activate；已 ACTIVATED 时 start 幂等 no-op；UNLOADED 态 stop 幂等 no-op；ACTIVATED 态 stop = deactivate + unload
- `invokeCommand/invokeCommandAsync` 定义级路由钉死：aware = 本插件容器命令 bean 分发（复用 getCommandBean 既有回退链：插件容器 → 宿主回退 → default bean 兜底），无激活抛 INACTIVE；非 aware = 现状不变；删除"实例数=1 才路由"规则残留
- 按两轨真实行为差异建立回归基线测试（本 plan Current Baseline 所列 R1 前差异即基线素材：VFS 轨 start 原不含 load、jar 轨 aware start 原 load+UOE——收敛后统一为新语义，测试断言新语义 + 兼容路径不变，原基线取代记录于 daily log）
- plugin.xdef 属性集冻结确认（Decision）：requires/if-property/activator + `<beans>`，不新增 requires-service（01 §五终裁），确认后作为 R4 docs 的冻结基线（结论记录于 daily log，供 R4 起草引用）

## Non-Goals

- reconcile/HMR/updateConfig 语义（R2）
- API 签名变更（R1 已冻结）
- 测试矩阵全面改写与 docs-for-ai 同步（R4；本 plan 的回归测试即 R4 基线的组成部分）
- `getCommandBean` 宿主回退（`BeanContainer.tryGetBean`）行为变更——非 aware 现状保持，仅测试钉死

## Scope

### In Scope

- `nop-plugin-support/.../AbstractPlugin.java`：start/stop aware 语义收敛、invokeCommand aware 定义级路由
- `nop-plugin-manager/.../impl/VfsPluginDefinition.java`：start 补 load 步、stop 语义收敛、invokeCommand 定义级路由、删 DEFAULT_INSTANCE_KEY
- `PluginManagerConstants.java`：默认实例 key 常量删除
- `plugin.xdef` 冻结确认（无代码变更，Decision + 记录）
- 兼容路径与命令路由回归测试（aware VFS 轨、aware jar 轨、非 aware 旧插件三线）

### Out Of Scope

- 同 Non-Goals

## Execution Plan

### Phase 1 - start/stop 兼容语义收敛（双轨）

Status: completed
Targets: `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java`、`nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java`、`nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/PluginManagerConstants.java`

- Item Types: `Fix | Proof`

- [x] 以 R1 落地后 live repo 复核 Current Baseline：确认 `activate()`/`deactivate()` 已由 R1 在两轨交付、VFS 轨 start/stop 已收敛。**复核发现一处 R1 残留缺口并升级为显式实现项（已修复）**：`AbstractPlugin.start` aware 分支无条件 `load(config)`——已 ACTIVATED 时 start 会把状态重置为 LOADED 再重建容器（旧容器泄漏）；已 LOADED 时重复 load。修复 = 状态边界规格落地：仅 UNLOADED 补 load 步，其余状态直接 activate（ACTIVATED 时 activate 幂等 no-op，不重建容器）
- [x] `AbstractPlugin` aware 分支 start/stop 语义收敛验证：start = load + activate（门控恒空集、激活回调跳过——01 §二 jar 轨契约）、stop = deactivate + unload；非 aware 分支不动；**start/stop 状态边界为两轨统一规格**（已 LOADED 不重复 load、已 ACTIVATED start 幂等、UNLOADED stop 幂等）——boundary 修复落地（AbstractPlugin.start 状态判断），VfsPluginDefinition.start R1 已实现同规格（复核确认）
- [x] `VfsPluginDefinition` start/stop 语义确认（R1 已收敛）+ start/stop 状态边界落地（Goals 所列：已 LOADED 不重复 load、已 ACTIVATED 幂等、UNLOADED 态 stop 幂等）——R1 代码已实现，本 plan 补齐三断言 focused tests（TestCommandRoutingRegression.testVfsStartStopStateBoundaries）
- [x] 删除 `DEFAULT_INSTANCE_KEY` 常量及全部引用（R1 已删除常量本体；本 plan 复核全仓零引用——grep 排除 ai-dev/docs 历史记录后 0 命中）
- [x] 更新类级 Javadoc 中旧语义描述（§7.1 引用同步）——AbstractPlugin/VfsPluginDefinition 类级 Javadoc 补状态边界与回退链一致表述；原两轨差异基线（VFS start 不含 load / jar aware start = load+UOE）的取代记录写入 daily log（`ai-dev/logs/2026/08-23.md` R3 段）

Exit Criteria:

- [x] focused test（VFS 轨）：start 后 getState()==ACTIVATED（含未先 load 的直接 start——补 load 步的行为差异断言）；stop 后 UNLOADED 且中间经过 deactivate（effect 已回退：quiescence 断言）——`TestCommandRoutingRegression.testVfsDirectStartWithoutPriorLoadIncludesLoadStep`（直接构造定义 UNLOADED → start → ACTIVATED；stop → UNLOADED + return-disposed/bean-destroyed 事件 + scope 回退）
- [x] focused test（jar 轨 aware，TestAbstractPlugin 体系）：start 后 ACTIVATED（无条件激活）、stop 后 UNLOADED；原 load+UOE 行为基线被取代（测试更新记录于 daily log）——`testStartStopConvergeToLoadActivateDeactivateUnload`（+start 后命令命中/stop 后 INACTIVE 断言）
- [x] focused test（状态边界，两轨各测）：已 LOADED 时 start 不重复 load（updateConfig 累积值保留、直接 activate）；已 ACTIVATED 时 start 幂等 no-op；UNLOADED 态 stop 幂等 no-op——三断言齐备（jar 轨 `testStartStopStateBoundariesJarTrack`：load 计数 + 容器身份；VFS 轨 `testVfsStartStopStateBoundaries`：definitionConfig 累积值保留（load 会重置配置域——保留即证明不重复 load）+ scope 身份 + activator 不重跑）
- [x] focused test（非 aware 旧插件）：start/stop 行为与 W2-W4 基线完全一致（AppConfig.assignConfigValue + doStart 子容器路径），存量第三方插件零回归——`testCompatPathKeepsStartStopSemantics` + `testCompatPathW2W4BaselineConfigAndContainer`
- [x] 门控不满足时 VFS 轨 start 不激活不抛异常（LOADED 态 + 日志）断言——`TestPluginLifecycle.testStartStopConvergeToLoadActivate`（R1 已建，复核在位）
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 绿
- [x] **无静默跳过**：activate 失败在 start 路径显式传播（FAILED 态 + 异常），不吞——jar 轨 `testStartPropagatesActivationFailureExplicitly`（FAILED + lastActivationError + 异常传播）；VFS 轨 `testVfsStartPropagatesActivationFailureExplicitly`（ERR_PLUGIN_ACTIVATION_FAILED 上抛 + FAILED）
- [x] owner docs deferred to R4、daily log 已更新

### Phase 2 - invokeCommand 定义级路由回归

Status: completed
Targets: `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java`、`nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java`

- Item Types: `Fix | Decision | Proof`

- [x] aware 路由收敛：命令经插件**激活容器**的命令 bean 分发（BEAN_NOP_PLUGIN_COMMAND_PREFIX + command），miss 回退链**复用 getCommandBean 现状**（插件容器 → 宿主回退 → default bean 兜底）——两实现点（VfsPluginDefinition/AbstractPlugin）收敛到同一回退链，不得分叉（**修复一处分叉**：AbstractPlugin.getCommandBean 宿主回退分支原为裸 cast（宿主同名非 IPluginCommand bean 会 CCE），与 VfsPluginDefinition 的 instanceof 判定对齐）；无激活抛 ERR_PLUGIN_INACTIVE；激活成功但插件无容器（无 beans 文件）时经既有回退链最终抛明确异常（不静默返回）——`testAwareNoContainerCommandResolvesViaFallbackChain` 钉死
- [x] 非 aware 路由维持现状（插件自有容器优先、宿主回退），仅补钉死测试——`testCompatPathW2W4BaselineConfigAndContainer` 钉死链现状全部分支（链上可见名经插件容器 getBean 显式 container-not-started——兼容容器 build 不 start 为改造前等价行为；未知名 → 静态宿主回退 miss → default 兜底 getBean 显式抛异常）。**执行裁定（非 aware"宿主回退命中"不可达）**：containsBean 经 parent 链已覆盖宿主 bean 名空间（插件容器 parent = 宿主容器），静态 tryGetBean 宿主回退仅在链上无此名时可达（此时必 miss）——非 aware 宿主回退"命中"分支结构性不可达，非缺陷，链结构（插件容器优先 → 宿主 → default）不变即 W2-W4 基线保持
- [x] 删除"实例数=1 才路由/多实例抛异常"规则的全部残留实现与 Javadoc——R1/R2 已删净，本 plan grep 复核 manager/support main 零命中（`instances.size() > 1` / `ERR_PLUGIN_MULTIPLE_INSTANCES`）

Exit Criteria:

- [x] focused test（VFS 轨 aware）：ACTIVATED 态 invokeCommand 命中插件容器命令 bean；未激活抛 INACTIVE；deactivate 后再调用抛 INACTIVE（定义级状态检查）——`testVfsCommandInactiveBeforeAndAfterDeactivate`
- [x] focused test（jar 轨 aware）：start（=load+activate）后 invokeCommand 命中容器命令 bean；stop 后抛 INACTIVE——`testStartStopConvergeToLoadActivateDeactivateUnload`（start 后命中 "hello:world" + stop 后 ERR_PLUGIN_INACTIVE）
- [x] focused test（非 aware）：invokeCommand 经 getCommandBean 回退链命中（含宿主回退与 default bean 兜底分支）——W2-W4 基线保持——`testCompatPathW2W4BaselineConfigAndContainer`（default 兜底分支显式失败断言 + 链上可见名行为钉死）；宿主回退分支"命中"两轨 aware 各测（见下条，标记 "host:{who}" 一致）；非 aware 静态回退不可达裁定已记录于上一条
- [x] focused test（回退链裁定）：aware 插件容器未命中指定命令 bean 时按裁定回退链行为（宿主回退/default 兜底）可观察，两轨行为一致——jar 轨 `testAwareCommandHostFallbackObservable`（"host" → "host:world"）+ VFS 轨 `testVfsCommandFallbackChainHostAndDefault`（"host" → "host:world" 同标记 = 两轨一致；"no-such-command" → default 兜底 "default:{command}"）。宿主 fixture：support `/main/beans/app-host-commands.beans.xml`（仅 host，保留链终点显式异常可观测性）+ manager 同路径（host + default，兜底命中可观测性）
- [x] `grep -rn "instances.size() > 1\|ERR_PLUGIN_MULTIPLE_INSTANCES" nop-core-framework/nop-plugin/nop-plugin-manager/src/main nop-core-framework/nop-plugin/nop-plugin-support/src/main` 零命中，且 api 模块 `PluginApiErrors` 中该常量无 manager/support 用法残留（常量本体退役归 R2，与本 plan 顺序无关）——grep exit 1（零命中）；api 模块已无常量本体（R2 已退役，复核零残留）
- [x] **端到端验证**：loadPlugin → activatePlugin → invokeCommand → 命令 bean 返回真实结果 → deactivatePlugin → invokeCommand 抛 INACTIVE，全链走通——`testEndToEndLoadActivateInvokeDeactivate`（含接线断言：AgentInstanceRecorder 收到 "command:hello"）
- [x] `./mvnw test` 三模块绿；owner docs deferred to R4；daily log 已更新

### Phase 3 - plugin.xdef 属性集冻结确认

Status: completed
Targets: `nop-core-framework/nop-plugin/nop-plugin-api/src/main/resources/_vfs/nop/schema/plugin/plugin.xdef`

- Item Types: `Decision | Proof`

- [x] 核对 plugin.xdef 属性集 = name/requires/if-property/activator + `<beans>` 子元素 + xdef 标准属性；确认无 requires-service 等服务级条件属性（01 §五终裁：扩展点已关闭）——逐项 diff 为空（name 必填 `!string`、requires `csv-set`、if-property `string`、activator `bean-name`、唯一子元素 `<beans xdef:ref>`）；并落地机器可检守护测试 `TestPluginXdef.testAttributeSetFrozen`（经 XDefinitionParser 解析 xdef 本体断言属性集与子元素集，任何偏差即测试失败——冻结基线长期受保护）
- [x] 冻结结论记录于 daily log（作为 R4 docs 重写的冻结基线引用点）——`ai-dev/logs/2026/08-23.md` R3 段

Exit Criteria:

- [x] plugin.xdef 与冻结清单逐项一致（属性 diff 为空）；若发现偏差，修复或在本 plan 内显式裁决后更新冻结清单——diff 为空，无偏差
- [x] 既有 plugin.xdef 解析测试（TestPluginXdef/TestPluginCoeffectSpec）全绿——TestPluginXdef 3/3（含新增守护测试）、TestPluginCoeffectSpec 4/4
- [x] daily log 已记录冻结决策

## Closure Gates

- [x] 两轨 start/stop 语义统一为 start = load + activate / stop = deactivate + unload，且两轨回归测试均存在（AbstractPlugin.start 边界修复 + VfsPluginDefinition R1 实现复核；jar 轨 TestAbstractPlugin 15 @Test + VFS 轨 TestCommandRoutingRegression 6 @Test）
- [x] 非 aware 旧插件路径（start/stop/invokeCommand）与 W2-W4 基线零差异（兼容不破坏）——非 aware 分支代码零改动（audit 核验 AbstractPlugin.java:326-335 旧语义保持 + 链现状钉死测试）
- [x] invokeCommand 定义级路由落地，多实例路由规则零残留（grep 零命中）
- [x] DEFAULT_INSTANCE_KEY 全仓零引用（grep 排除 ai-dev/docs 后零命中，audit 独立复核）
- [x] plugin.xdef 冻结结论已记录（daily log 08-23 R3 段 + TestPluginXdef.testAttributeSetFrozen 守护测试）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect（发现的两个 live defect——AbstractPlugin.start 边界泄漏、getCommandBean 回退链分叉——均已当场修复，非延期；owner docs 归属 R4 为 roadmap 指派）
- [x] owner docs 同步按 roadmap 归属 R4，plan 内显式记录（Non-Goals + 各 Phase exit criteria）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（fresh session `ses_fd4f56f78ffeDkEB0zxyv8d6Lw`，read-only explore 子 agent，见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 已验证（a）start→load→activate→容器构建→invokeCommand→命令 bean 调用链运行时连通（audit 逐行追踪 AbstractPlugin:132-234/382-410、VfsPluginDefinition:454-499/612-621 + e2e 测试实跑），（b）无空方法体/静默跳过/no-op（scan-hollow 0 findings，miss 端点均显式抛异常）
- [x] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am` 通过（exit 0）
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过（api 6 + manager 69 + support 15 = 90/90，audit 独立复跑 exit 0）
- [x] checkstyle / 代码规范检查通过（repo config `checkstyle.xml`，exit 0）

## Deferred But Adjudicated

### jar 轨门控载体（若未来 jar 轨需要 requires/if-property 门控）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 01 §二 jar 轨契约裁决——jar 轨门控恒为空集（无条件激活）、激活回调跳过（无论 VFS 约定路径是否存在 xdef 载体，载体仅作元数据不驱动门控）为当前 supported baseline；若未来需要门控须先为其定义载体立项（W4 裁决延续，R1 plan 已按此落地）。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 实现产物提交交由 mission-driver 统一 commit（先例）。
- R4 docs 携带项（closure audit 观察记录，非遗留缺陷）：jar 轨 aware `start(gav, config)` 在已 LOADED/ACTIVATED 时忽略 config（VFS 轨经 updateConfig 并入定义级配置域）——jar 轨 aware 无定义级配置域（`load(config)` 不消费 config），为 01 §二 jar 轨契约下的两轨固有差异，R4 重写 docs-for-ai/03-modules/nop-plugin.md 时作为 documented behavior 写明。

## Closure

Status Note: R3 三 Phase 全部完成并经独立 closure audit（CLOSURE APPROVED）。执行中发现并当场修复两个 live defect（AbstractPlugin.start aware 分支无条件重复 load 导致已 ACTIVATED 时容器泄漏；AbstractPlugin.getCommandBean 宿主回退裸 cast 与 VfsPluginDefinition 分叉），VfsPluginDefinition 侧为 R1 已落地语义的复核确认 + focused tests 补齐；plugin.xdef 属性集冻结确认（diff 为空 + 机器守护测试）。兼容路径（非 aware）零改动零回归，两轨回归基线与端到端链路全部建立（90/90 测试绿）。
Completed: 2026-08-23

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，read-only explore），task id `ses_fd4f56f78ffeDkEB0zxyv8d6Lw`
- Evidence:
  - Phase 1 全部 exit criteria PASS（live code 逐行核验：AbstractPlugin.java:310-324 start 边界 / :337-347 stop / :326-335 非 aware 旧语义 / :194-200 失败显式传播；VfsPluginDefinition.java:543-564 start/stop 边界 + 门控不满足 warn 不抛；focused tests 断言实文核验含 load 计数、容器身份、definitionConfig 累积值保留、quiescence 事件）
  - Phase 2 全部 exit criteria PASS（两实现点回退链逐行比对无分叉——AbstractPlugin.java:382-392 与 VfsPluginDefinition.java:612-621 均 instanceof 判定；fixtures 存在性与双 JVM 分工核验；`instances.size()>1|ERR_PLUGIN_MULTIPLE_INSTANCES` grep 零命中；端到端 `testEndToEndLoadActivateInvokeDeactivate` 全链 + 接线断言 `command:hello` 真实记录于 HelloCommand.java:19）
  - Phase 3 全部 exit criteria PASS（plugin.xdef 属性集 = name/requires/if-property/activator + `<beans>` 子元素，无 requires-service；TestPluginXdef 3/3 + TestPluginCoeffectSpec 4/4 绿）
  - Anti-Hollow PASS：start→load→activate→容器构建→invokeCommand→命令 bean 调用链运行时追踪连通；miss 端点全部显式失败（INACTIVE / getBean throw）；失败路径 rollback + FAILED + lastActivationError + rethrow 无吞；`scan-hollow-implementations.mjs --severity high` Critical/High/Medium/Low 全 0
  - 独立命令复跑：`./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` BUILD SUCCESS（api 6 + manager 69 + support 15，0 failures/0 errors）；hollow scan exit 0
  - Deferred 项分类检查：owner docs 归 R4（roadmap 指派）；jar 轨门控载体 out-of-scope improvement（01 §二裁决延续）；无 in-scope live defect 被降级
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（见下方收口记录）
- 审计 Minor 观察（非阻塞，已记录为 R4 docs 携带项）：jar 轨 aware `start(config)` 在已 LOADED/ACTIVATED 时忽略 config——两轨固有差异（jar 轨 aware 无定义级配置域），R4 docs 写明。

Follow-up:

- 实现产物提交由 mission-driver 统一执行（先例）。
- R4：测试矩阵改写 + docs-for-ai 同步（含本 plan 的兼容语义/回退链/冻结基线引用 + 上述 jar 轨 config 差异 documented behavior）。
