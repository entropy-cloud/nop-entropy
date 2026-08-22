# 2. nop-plugin 定位反转 R2：manager 编排简化——插件级 coeffect + HMR 简化 + 拓扑序

> Plan Status: completed
> Mission: nop-plugin-enhancement
> Work Item: R2（manager 编排简化）
> Last Reviewed: 2026-08-23
> Source: `ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（R2 条目）；`ai-dev/design/nop-plugin/01-architecture-baseline.md`（权威契约：§五 coeffect/reconcile、§六 HMR、§三 跨插件去活性拓扑序不变量）
> Related: 前置 `2026-08-22-2309-1`（R1：API 收缩）；后续 `2026-08-22-2309-3`（R3）、R4（测试+docs，后续轮次起草）

## Purpose

R1 完成公共 API 收缩后，把 `PluginManagerImpl` 的编排实现收敛到反转后语义：coeffect 仅插件级（requires/if-property）、HMR 去实例快照、reconcile 增加跨插件去激活拓扑序，并清除全部实例机制残留实现与实例错误码族。本 plan 结束时 `nop-plugin-manager` 主线代码不再存在任何指向已废止机制的引用。

## Current Baseline

（依据 live repo 2026-08-22 核验的 R1 前状态；**执行本 plan 前必须以 R1 落地后的 live repo 复核并修正本节**。R1 Ownership Deviation 裁定：parentToChildren/级联销毁/快照/pending/isInstanceConfigMatched 签名等 IPluginInstance 型结构随 R1 编译依赖一并删除——本 plan 对这些项为**残留审计**而非重复删除）

- `PluginManagerImpl.java`（1070 行）：持 `parentToChildren`（Map<IPluginInstance,Set<IPluginInstance>>）、级联销毁逻辑、`ReloadSnapshot`/`pendingRebuilds`/`retryPendingRebuilds` 快照重建、`reconcileLock` 串行化——R1 为编译会移除其中直接依赖 IPluginInstance 的部分；**本 plan 负责确认残留清零与语义对齐**
- `ICoeffectEvaluator.java`（32 行）：`isRequiresSatisfied(Set)`（按"依赖定义存在且至少一个实例 ACTIVATED"求值——反转后应改为"依赖定义本身 ACTIVATED"）、`isGlobalConfigMatched`（保留；其实现经 `CoeffectConfigHelper.matches` 做宽松比较，见下条）、`isInstanceConfigMatched(IPluginInstance,...)`（签名随 R1 编译依赖删除，本 plan 审计语义残留）
- `InstanceConfigProvider.java`（280 行）：实例配置域载体，主线消费者 = PluginInstanceImpl（:100 字段、:462 创建并经 `setConfigProvider` 接入子容器、:399 热应用传播；:467-469 注释钉死"先 setConfigProvider 再 start；禁止 buildNewInstance（不传播自定义 provider，会静默回落全局 AppConfig）"）——R1 删 PluginInstanceImpl 重建单激活生命周期时须落地**定义级配置通道**（语义等价迁移该钉死约束：子容器经 provider 接收定义级配置视图，不静默回落全局）；本 plan 删除实例配置域载体并验证定义级通道无回归
- `CoeffectConfigHelper.java`（65 行）：宽松比较逻辑（Boolean/Number/String 等价比较，W5 裁定语义）。**注意**：其消费者不止实例路径——`CoeffectEvaluatorImpl.isGlobalConfigMatched`（PluginManagerImpl.java:1034-1036，定义级，R1 保留 reconcile 定义级评估后依然存活）。本 plan 删除该类时须将宽松比较逻辑**迁移**到保留的实现位置（如 CoeffectEvaluatorImpl 内部或等价位置），不得丢弃语义
- 实例错误码族（`PluginManagerErrors`/`PluginApiErrors`）：ERR_PLUGIN_INSTANCE_EXISTS/NOT_FOUND/INSTANCES_NOT_EMPTY/NOT_SUPPORTED、ERR_PLUGIN_MULTIPLE_INSTANCES、ERR_PLUGIN_PARENT_NOT_ACTIVATED/PARENT_CHAIN_CYCLE、**ERR_PLUGIN_ACTIVE_CHILDREN_EXIST**（:163，parent 级联守卫码）及 ARG_INSTANCE_KEY/ARG_INSTANCE_KEYS（两模块各一份：PluginManagerErrors:10-11 与 PluginApiErrors:14）。**注意**：`ERR_PLUGIN_ACTIVATION_FAILED`（:89-91）与 `ERR_PLUGIN_ACTIVATOR_NOT_FOUND`（:96-98）在 define() 中引用 ARG_INSTANCE_KEY 但本身在插件级世界仍有效——删 ARG 前须先改这两个 define 去掉该参数（消息文案变更，记录裁定）
- `TestCoeffectConfigHelper`（55 行）等实例域测试——随类删除；其中宽松比较用例（数值/字符串等价）迁移覆盖保留路径
- reconcile 现语义：定义级（能否派生实例）+ 实例级双层评估、不动点迭代、DFS 环检测、失败阈值暂停、自动触发链路三条（全局订阅/生命周期/**updateConfig 回调**——第三条在插件级门控下语义惰性化，保留与否需裁定，见 Goals）
- HMR 现语义（W6 P2-A）：destroy 全部实例（先子后父）→ unload → load → 按快照重建（父先子后）→ pending 重试 → reconcile——反转后 = deactivate→unload→load（重放 updateConfig 累积值）→reconcile，无快照无 pending
- 05 设计文档 §七 链路终点表述的更正注解落款为 2026-08-22 反转审计（非 W7）；01 §三/§五 的拓扑序与时间静止语义为实现目标

## Goals

- `ICoeffectEvaluator` 收缩为插件级：`isInstanceConfigMatched` 随 R1 编译依赖已删（本 plan 审计残留清零）；`isRequiresSatisfied` 目标语值语义 = "所列 plugin 定义本身处于 ACTIVATED 态"（01 §五：requires="model-provider tool-core" → 所列 plugin 均 ACTIVATED）——R1 删实例后 `hasActivatedInstanceByName` 编译断裂、R1 实际完成定义级判定改造，本 plan 验证钉死（同名多定义沿用 ANY 语义：任一同名定义 ACTIVATED 即满足，沿用现状 `namesToPluginIds` 的合法多定义假设）
- 删除 `InstanceConfigProvider.java` 的实例配置域语义（前提核对：R1 已落地定义级配置通道——子容器经 provider 接收定义级配置视图、updateConfig 热应用经合并视图重算 + provider 变更传播（01 §7.1）；若 R1 未落地，本项升级为显式实现项）；删除 `CoeffectConfigHelper.java` 但**宽松比较逻辑迁移**到 `isGlobalConfigMatched` 的实现位置（语义等价，W5 裁定的 Boolean/Number/String 宽松比较不丢弃），宽松比较用例（布尔/数值/字符串）迁移钉死保留路径
- 退役实例错误码族（先 grep 引用清零再删常量，含顺序内依赖处理）：ERR_PLUGIN_INSTANCE_EXISTS/NOT_FOUND/INSTANCES_NOT_EMPTY/NOT_SUPPORTED、ERR_PLUGIN_MULTIPLE_INSTANCES、ERR_PLUGIN_PARENT_NOT_ACTIVATED/PARENT_CHAIN_CYCLE、**ERR_PLUGIN_ACTIVE_CHILDREN_EXIST**；ARG_INSTANCE_KEY/ARG_INSTANCE_KEYS（manager 侧）——删除前先修改仍引用 ARG_INSTANCE_KEY 的 `ERR_PLUGIN_ACTIVATION_FAILED`/`ERR_PLUGIN_ACTIVATOR_NOT_FOUND` define（去参数、消息文案变更，裁定记录于 daily log）；api 侧例外见 Out Of Scope
- `reconcilePlugins()`：仅插件级 requires/if-property 评估；不动点迭代 + DFS 环检测保留；**新增跨插件拓扑序**——批量激活按依赖图正拓扑序、批量去激活按逆拓扑序（消费者先于提供者退出，01 §三不变量）；同一 pass 内混合批次裁定：**先处理去激活组（逆拓扑序）再处理激活组（正拓扑序）**，经不动点迭代收敛；门控不满足且已激活 → 去激活；激活失败置 FAILED、超阈值暂停自动激活（R1 迁移到定义级的机制，本 plan 验证钉死）
- `reloadPlugin`：deactivate（若激活）→ unload → load（重放 updateConfig 累积值）→ reconcile；P2-A 快照/pending 机制确认零残留；jar 轨维持 `ERR_PLUGIN_RELOAD_NOT_SUPPORTED` 显式失败
- 激活窗口时间静止语义保留并有测试钉死——**两个窗口**：activate 展开期间条件失效 → 完成后收敛去激活（不中途打断）；deactivate 展开期间条件恢复 → 回退完成后重激活
- `updateConfig` 语义对齐：LOADED 缓存待激活应用 / ACTIVATED 热应用（定义级配置域，合并视图重算）；updateConfig→reconcile 触发链路**保留**（01 §五已知限制 1 的既有实现：未来插件级配置依赖可经该触发点生效；当前对门控惰性但无害）

## Non-Goals

- start/stop 兼容语义收敛与命令路由回归（R3）
- 测试矩阵全面改写与 docs-for-ai 同步（R4；本 plan 仅为其语义变更新增 focused tests）
- `IPlugin`/`IPluginContext`/`IPluginManager` 签名变更（R1 已冻结 API 面）
- loader 依赖追踪/变更检测机制本体（W6 已交付，lastModified 比对与 ResourceComponentManager 接线不动）；**例外**：`checkChangedAndReload` 的 LOADED-only 状态过滤器须随本 plan 修正（见 Phase 3——R1 单层六态后 ACTIVATED 定义被过滤器跳过，HMR 主场景静默失效，属语义修正而非机制重设计）

## Scope

### In Scope

- `nop-plugin-manager/impl/`：ICoeffectEvaluator、InstanceConfigProvider、CoeffectConfigHelper、PluginManagerImpl（reconcile/reload/错误码）、VfsPluginDefinition（requires 求值语义）
- `PluginManagerErrors`/`PluginApiErrors`：实例错误码族退役
- 上述语义变更的 focused tests（拓扑序、时间静止、HMR 重放、updateConfig 两态、requires 定义级求值）

### Out Of Scope

- 同 Non-Goals
- api 模块例外（仅限实例机制死常量清理）：删除 `PluginApiErrors.ERR_PLUGIN_MULTIPLE_INSTANCES` 与 `PluginApiErrors.ARG_INSTANCE_KEYS`（PluginApiErrors.java:14）两处 api 侧实例机制常量——二者在 R1 删除实例路由后于 api 内无消费者；**除这两处常量外 api 模块不动**（签名变更 R1 已冻结）
- `DEFAULT_INSTANCE_KEY` 常量删除归属 R3（roadmap R3 交付物；R1 收敛 start/stop 后 main 引用已消失，R3 收尾删除——若执行 R2 时发现仍残留于 manager main，仅记录不越权删除）

## Execution Plan

### Phase 1 - 实例机制残留清零

Status: completed
Targets: `nop-plugin-manager/src/main/java/io/nop/plugin/manager/impl/`（InstanceConfigProvider.java、CoeffectConfigHelper.java、ICoeffectEvaluator.java、PluginManagerImpl.java）、`PluginManagerErrors.java`、`PluginApiErrors.java`、相关测试

- Item Types: `Fix | Proof`

- [x] 以 R1 落地后 live repo 复核 Current Baseline，把 R1 已删除项标记为"验证无残留"而非重复删除（含 `isInstanceConfigMatched`——R1 Ownership Deviation 已裁定随编译依赖删除）
- [x] 删 `InstanceConfigProvider.java`/`CoeffectConfigHelper.java`/`TestCoeffectConfigHelper.java`（若 R1 后仍存在）；前提核对：定义级配置通道已由 R1 落地且热应用传播测试可观察（Goals 所列），否则本项升级为显式实现项；宽松比较逻辑先迁移到 `isGlobalConfigMatched` 实现位置再删 Helper（语义等价）
- [x] 退役实例错误码族（顺序：先改 `ERR_PLUGIN_ACTIVATION_FAILED`/`ERR_PLUGIN_ACTIVATOR_NOT_FOUND` 的 define 去 ARG_INSTANCE_KEY 参数 → grep 引用清零 → 删常量）：含 ERR_PLUGIN_ACTIVE_CHILDREN_EXIST 与 ARG_INSTANCE_KEY/ARG_INSTANCE_KEYS（manager 侧；api 侧两常量见 Out Of Scope）
- [x] `grep -rn -i "InstanceConfigProvider\|CoeffectConfigHelper\|isInstanceConfigMatched\|ERR_PLUGIN_INSTANCE_\|ERR_PLUGIN_MULTIPLE_INSTANCES\|ERR_PLUGIN_PARENT_\|ERR_PLUGIN_ACTIVE_CHILDREN\|ARG_INSTANCE_KEY\|parentToChildren\|pendingRebuild\|SnapshotInstance\|ReloadSnapshot\|hasActivatedInstanceByName" nop-core-framework/nop-plugin/nop-plugin-api/src nop-core-framework/nop-plugin/nop-plugin-manager/src nop-core-framework/nop-plugin/nop-plugin-support/src` 零命中（`hasActivatedInstanceByName` 若以定义级语义更名后仍保留方法，允许新名存在但旧名零命中，裁定记录于 daily log）

Exit Criteria:

- [x] 上述 grep 零命中且记录于 daily log
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 绿
- [x] **无静默跳过**：删除路径不得以空实现替代（直接删方法/类，不留 stub）
- [x] owner docs：`docs 同步 deferred to R4`（roadmap 归属）记录于 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - reconcile 插件级语义 + 拓扑序

Status: completed
Targets: `PluginManagerImpl.java`（reconcile 引擎）、`ICoeffectEvaluator.java`、`VfsPluginDefinition.java`

- Item Types: `Fix | Decision | Proof`

- [x] `isRequiresSatisfied` 语义改为定义级 ACTIVATED 判定（依赖 plugin 的 getState() == ACTIVATED）
- [x] reconcile 仅插件级评估；环检测（DFS + 强制门控关闭报告）与不动点迭代保留；同一 pass 混合批次裁定落地：先去激活组（逆拓扑序）后激活组（正拓扑序），不动点迭代收敛
- [x] 批量激活正拓扑序 / 批量去激活逆拓扑序落地（消费者先退出）；拓扑序基于 requires 依赖图
- [x] 失败处理对齐 01 §三：激活失败置 FAILED 记录错误、失败计数超阈值暂停该 plugin 自动激活并报告（验证 R1 迁移后的定义级机制）

Exit Criteria:

- [x] focused test：依赖链 A←B（B requires A）批量激活时 A 先于 B ACTIVATED（正拓扑序）；条件整体失效时 B 先于 A 退出（逆拓扑序）——用激活/去激活顺序断言（计数序列或状态观测序列）
- [x] focused test：混合批次——同一 reconcile pass 中 A←B 去激活组与独立插件 C 激活组并存时，先处理去激活组（逆拓扑序）再处理激活组（正拓扑序），顺序断言可观察
- [x] focused test：requires 求值 = 依赖定义 ACTIVATED（依赖仅 LOADED 时门控不满足，activatePlugin 返回 false / reconcile 不激活）
- [x] focused test：环成员强制门控关闭并报告 unresolved（保留 W5 行为基线）
- [x] focused test：时间静止两窗口——(a) activate 展开期间置失效条件，本次激活完成后被 reconcile 去激活（最终态 LOADED），无中间打断；(b) deactivate 展开期间条件恢复，回退完成后经 reconcile 重激活（最终态 ACTIVATED）
- [x] focused test：宽松比较语义保留——if-property 数值/字符串宽松等价比较（迁移自 TestCoeffectConfigHelper 保留用例）在 `isGlobalConfigMatched` 路径仍成立
- [x] `IPluginContext.reconcile()`/`reconcilePlugins()` 端到端：loadPlugin 两个依赖插件 → reconcile → 级联激活 → 修改 if-property 全局配置 → 自动触发订阅 → 级联去激活，全链走通
- [x] **接线验证**：配置订阅自动触发 reconcile 仍在运行时连通（变更全局配置后无需显式调用即收敛，测试断言）
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 绿
- [x] **无静默跳过**、owner docs deferred to R4、daily log 已更新

### Phase 3 - HMR 简化与 updateConfig 语义钉死

Status: completed
Targets: `PluginManagerImpl.java`（reloadPlugin）、`VfsPluginDefinition.java`（updateConfig）

- Item Types: `Fix | Proof`

- [x] reloadPlugin 语义 = deactivate（若激活态）→ unload → load（重放 updateConfig 累积值）→ reconcile；确认无快照/pending 残留
- [x] `checkChangedAndReload` 状态过滤器修正：允许 ACTIVATED/中间态定义进入变更检测（现状 `if (getState() != LOADED) continue` 会跳过 ACTIVATED 定义——R1 单层六态后 HMR 主场景静默失效）
- [x] jar 轨 reloadPlugin 维持 ERR_PLUGIN_RELOAD_NOT_SUPPORTED 显式失败
- [x] updateConfig：LOADED 缓存 / ACTIVATED 热应用语义测试钉死（含重激活后缓存值被应用）

Exit Criteria:

- [x] focused test：ACTIVATED 插件 reload 后定义重解析、updateConfig 累积值重放（新激活的配置视图含历史 updateConfig 值）、reconcile 重新门控（条件仍满足则自动回 ACTIVATED）
- [x] focused test（HMR 自动链端到端）：ACTIVATED 插件的定义文件变更 → `checkChangedAndReload` → 完成 reload（deactivate→unload→load→reconcile），插件最终回 ACTIVATED——修复后 ACTIVATED 定义不被过滤器跳过
- [x] focused test：jar 轨 reload 显式失败（保留 W6 行为基线）
- [x] focused test：LOADED 态 updateConfig 缓存 → activate 后应用；ACTIVATED 态 updateConfig 热应用断言包含**可观察传播**（子容器内 bean 经 getService 反映新配置值，或定义级 provider 变更传播被订阅方观察）——不允许仅内部 map 重算的弱分支通过
- [x] focused test：R1 的失败阈值暂停 focused test 保持绿（验证方式 = 该测试存在且通过；如 R1 未交付则补）
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 绿
- [x] owner docs deferred to R4、daily log 已更新

## Closure Gates

- [x] 实例机制残留 grep（Phase 1 清单，全路径）零命中
- [x] reconcile/HMR/updateConfig 行为与 01 §五/§六/§三 语义逐项一致（含时间静止两窗口）且有 focused tests
- [x] 拓扑序不变量（消费者先退出）有顺序断言测试
- [x] 实例错误码族全仓 *.java 代码引用零命中后删除（含 api 模块两处例外常量：ERR_PLUGIN_MULTIPLE_INSTANCES 与 ARG_INSTANCE_KEYS；含 ERR_PLUGIN_ACTIVATION_FAILED/ACTIVATOR_NOT_FOUND 的 define 参数变更裁定记录；docs/ai-dev 历史文档中的引用归属 R4 重写）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect
- [x] owner docs 同步按 roadmap 归属 R4，plan 内显式记录
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）reconcile→activate/deactivate 编排链与拓扑序在运行时连通（端到端测试），（b）无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am` 通过
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### reconcile 触发频率优化（批量事件合并）与失败阈值参数配置化

- Classification: `optimization candidate`
- Why Not Blocking Closure: W5 follow-up 延续项；当前按事件触发 + 实现层常量已满足语义正确性，性能与配置化属优化，不影响 supported baseline。
- Successor Required: `no`

### updateConfig 热应用不触发依赖方重门控（01 §五已知限制 1）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 01 §五终裁注解明文化的设计限制（当前无此需求），非缺陷；若未来出现跨插件配置依赖需求须重新评估并立项。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 实现产物提交交由 mission-driver 统一 commit（先例）。

## Closure

Status Note: R2 三 Phase 全部落地并勾选：实例机制残留全路径 grep 零命中（载体类按定义级语义重命名为 DefinitionConfigProvider、宽松比较迁移到 configValueMatches、实例错误码族 manager 7 码 + 2 ARG 与 api 2 常量退役）；reconcile 收敛为插件级拓扑编排（批量激活正拓扑序/去激活逆拓扑序 + 级联闭包 + 同 pass 混合批次先去激活组裁定 + 时间静止两窗口语义，均有事件序列断言 focused tests）；HMR/updateConfig 语义验证钉死（checkChangedAndReload 过滤器修正确认 R1 已落地、focused tests 复跑绿）。独立 closure audit 17 项全 PASS（CLOSURE APPROVED）。
Completed: 2026-08-23

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，explore 类型，read-only）`ses_fd5111804ffev7YYjeGzoSSgri`
- Audit Session: ses_fd5111804ffev7YYjeGzoSSgri
- Evidence:
  - 每条 Exit Criterion：17/17 PASS（逐条 file:line / test name 证据，见 audit report；Phase 1 grep 零命中、Phase 2 doReconcile 拓扑编排 PluginManagerImpl:261-299 + 6/6 focused tests 断言实文核验、Phase 3 reload/filter/jar/updateConfig 5 项 focused tests 实文核验）
  - 每条 Closure Gate：PASS（grep 零命中；拓扑序不变量顺序断言 TestReconcileTopologicalOrder:120/144/169；错误码全仓零引用；deferred 项与 01 §五已知限制注解逐条比对诚实；anti-hollow 见下）
  - Anti-Hollow 检查：loadPlugin:139 → reconcile:225 → doReconcile:261 → topoSortDefs:268 / computeDeactivationGroup:275 → activateIfInactive:290 / deactivateIfActive:279 → def.activate()/deactivate() 全链运行时连通（无死代码）；无空方法体/静默 no-op（异常均 LOG.error 后按契约处理；`node ai-dev/tools/scan-hollow-implementations.mjs <三模块 src/main> --severity high` 退出码 0，0 findings）
  - 独立测试复跑：`./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` exit 0 全绿（manager 62 = TestReconcileTopologicalOrder 6 + TestCoeffectConfigMatching 3 + TestCoeffectReconcile 7 + TestReloadPlugin 4 + TestChangeDetection 3 + TestPluginLifecycle 14 + 其余；api 6；support 9）
  - checkstyle（repo config）exit 0；`./mvnw compile -pl 三模块 -am` exit 0；下游 nop-quarkus-demo 仅用 api 未触及面，无破坏
  - Deferred 项分类检查：reconcile 触发频率优化（optimization candidate）与 updateConfig 不触发依赖方重门控（watch-only residual，01 §五已知限制 1 明文化）均非 live defect
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（closure 时点复核）

Follow-up:

- docs-for-ai 同步（nop-plugin.md 重写 + INDEX/source-anchors）与测试矩阵全面改写归 R4（roadmap 归属）
- start/stop 兼容语义收敛与命令路由回归归 R3；`DEFAULT_INSTANCE_KEY` 常量删除归 R3（R2 时点 manager main 已无引用，仅记录）
