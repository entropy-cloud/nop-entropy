# 3. nop-plugin 定位反转 R3：兼容路径与命令路由回归

> Plan Status: active
> Mission: nop-plugin-enhancement
> Work Item: R3（兼容与命令路由回归）
> Last Reviewed: 2026-08-22
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

Status: planned
Targets: `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java`、`nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java`、`nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/PluginManagerConstants.java`

- Item Types: `Fix | Proof`

- [ ] 以 R1 落地后 live repo 复核 Current Baseline：确认 `activate()`/`deactivate()` 已由 R1 在两轨交付、VFS 轨 start/stop 已收敛；若 R1 未完整交付（如 AbstractPlugin.activate() 缺失），本项暴露后在 Phase 内升级为显式实现项（行为规格 = R1 plan Goals 所列 jar 轨契约）
- [ ] `AbstractPlugin` aware 分支 start/stop 语义收敛验证：start = load + activate（门控恒空集、激活回调跳过——01 §二 jar 轨契约）、stop = deactivate + unload；非 aware 分支不动；**start/stop 状态边界为两轨统一规格**（已 LOADED 不重复 load、已 ACTIVATED start 幂等、UNLOADED stop 幂等）
- [ ] `VfsPluginDefinition` start/stop 语义确认（R1 已收敛）+ start/stop 状态边界落地（Goals 所列：已 LOADED 不重复 load、已 ACTIVATED 幂等、UNLOADED 态 stop 幂等）
- [ ] 删除 `DEFAULT_INSTANCE_KEY` 常量及全部引用（若 R1 后仍残留）
- [ ] 更新类级 Javadoc 中旧语义描述（§7.1 引用同步）；原两轨差异基线（VFS start 不含 load / jar aware start = load+UOE）的取代记录写入 daily log

Exit Criteria:

- [ ] focused test（VFS 轨）：start 后 getState()==ACTIVATED（含未先 load 的直接 start——补 load 步的行为差异断言）；stop 后 UNLOADED 且中间经过 deactivate（effect 已回退：quiescence 断言）
- [ ] focused test（jar 轨 aware，TestAbstractPlugin 体系）：start 后 ACTIVATED（无条件激活）、stop 后 UNLOADED；原 load+UOE 行为基线被取代（测试更新记录于 daily log）
- [ ] focused test（状态边界，两轨各测）：已 LOADED 时 start 不重复 load（updateConfig 累积值保留、直接 activate）；已 ACTIVATED 时 start 幂等 no-op；UNLOADED 态 stop 幂等 no-op——三断言齐备
- [ ] focused test（非 aware 旧插件）：start/stop 行为与 W2-W4 基线完全一致（AppConfig.assignConfigValue + doStart 子容器路径），存量第三方插件零回归
- [ ] 门控不满足时 VFS 轨 start 不激活不抛异常（LOADED 态 + 日志）断言
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 绿
- [ ] **无静默跳过**：activate 失败在 start 路径显式传播（FAILED 态 + 异常），不吞
- [ ] owner docs deferred to R4、daily log 已更新

### Phase 2 - invokeCommand 定义级路由回归

Status: planned
Targets: `nop-core-framework/nop-plugin/nop-plugin-support/src/main/java/io/nop/plugin/support/AbstractPlugin.java`、`nop-core-framework/nop-plugin/nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/VfsPluginDefinition.java`

- Item Types: `Fix | Decision | Proof`

- [ ] aware 路由收敛：命令经插件**激活容器**的命令 bean 分发（BEAN_NOP_PLUGIN_COMMAND_PREFIX + command），miss 回退链**复用 getCommandBean 现状**（插件容器 → 宿主回退 → default bean 兜底）——两实现点（VfsPluginDefinition/AbstractPlugin）收敛到同一回退链，不得分叉；无激活抛 ERR_PLUGIN_INACTIVE；激活成功但插件无容器（无 beans 文件）时经既有回退链最终抛明确异常（不静默返回）
- [ ] 非 aware 路由维持现状（插件自有容器优先、宿主回退），仅补钉死测试
- [ ] 删除"实例数=1 才路由/多实例抛异常"规则的全部残留实现与 Javadoc

Exit Criteria:

- [ ] focused test（VFS 轨 aware）：ACTIVATED 态 invokeCommand 命中插件容器命令 bean；未激活抛 INACTIVE；deactivate 后再调用抛 INACTIVE（定义级状态检查）
- [ ] focused test（jar 轨 aware）：start（=load+activate）后 invokeCommand 命中容器命令 bean；stop 后抛 INACTIVE
- [ ] focused test（非 aware）：invokeCommand 经 getCommandBean 回退链命中（含宿主回退与 default bean 兜底分支）——W2-W4 基线保持
- [ ] focused test（回退链裁定）：aware 插件容器未命中指定命令 bean 时按裁定回退链行为（宿主回退/default 兜底）可观察，两轨行为一致
- [ ] `grep -rn "instances.size() > 1\|ERR_PLUGIN_MULTIPLE_INSTANCES" nop-core-framework/nop-plugin/nop-plugin-manager/src/main nop-core-framework/nop-plugin/nop-plugin-support/src/main` 零命中，且 api 模块 `PluginApiErrors` 中该常量无 manager/support 用法残留（常量本体退役归 R2，与本 plan 顺序无关）
- [ ] **端到端验证**：loadPlugin → activatePlugin → invokeCommand → 命令 bean 返回真实结果 → deactivatePlugin → invokeCommand 抛 INACTIVE，全链走通
- [ ] `./mvnw test` 三模块绿；owner docs deferred to R4；daily log 已更新

### Phase 3 - plugin.xdef 属性集冻结确认

Status: planned
Targets: `nop-core-framework/nop-plugin/nop-plugin-api/src/main/resources/_vfs/nop/schema/plugin/plugin.xdef`

- Item Types: `Decision | Proof`

- [ ] 核对 plugin.xdef 属性集 = name/requires/if-property/activator + `<beans>` 子元素 + xdef 标准属性；确认无 requires-service 等服务级条件属性（01 §五终裁：扩展点已关闭）
- [ ] 冻结结论记录于 daily log（作为 R4 docs 重写的冻结基线引用点）

Exit Criteria:

- [ ] plugin.xdef 与冻结清单逐项一致（属性 diff 为空）；若发现偏差，修复或在本 plan 内显式裁决后更新冻结清单
- [ ] 既有 plugin.xdef 解析测试（TestPluginXdef/TestPluginCoeffectSpec）全绿
- [ ] daily log 已记录冻结决策

## Closure Gates

- [ ] 两轨 start/stop 语义统一为 start = load + activate / stop = deactivate + unload，且两轨回归测试均存在
- [ ] 非 aware 旧插件路径（start/stop/invokeCommand）与 W2-W4 基线零差异（兼容不破坏）
- [ ] invokeCommand 定义级路由落地，多实例路由规则零残留
- [ ] DEFAULT_INSTANCE_KEY 全仓零引用
- [ ] plugin.xdef 冻结结论已记录
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect
- [ ] owner docs 同步按 roadmap 归属 R4，plan 内显式记录
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）start→load→activate→命令 bean 调用链运行时连通，（b）无空方法体/静默跳过/no-op
- [ ] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am` 通过
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### jar 轨门控载体（若未来 jar 轨需要 requires/if-property 门控）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 01 §二 jar 轨契约裁决——jar 轨门控恒为空集（无条件激活）、激活回调跳过（无论 VFS 约定路径是否存在 xdef 载体，载体仅作元数据不驱动门控）为当前 supported baseline；若未来需要门控须先为其定义载体立项（W4 裁决延续，R1 plan 已按此落地）。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 实现产物提交交由 mission-driver 统一 commit（先例）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
