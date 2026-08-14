# 6 parent 层级 + HMR（W6）

> Plan Status: active
> Mission: nop-plugin-enhancement
> Work Item: W6 parent 层级 + HMR
> Last Reviewed: 2026-08-14
> Review Consensus: 2026-08-14（独立 reviewer 多轮对抗性审查达成共识：无 Blocker，advisory 已全部并入）
> Source: `ai-dev/design/nop-plugin/01-architecture-baseline.md`（§三 parent 层级继承语义、§六 HMR、§7.3 reloadPlugin）；`ai-dev/design/nop-plugin/03-coeffect-and-agent-example.md`（§2.5 subagent 层级实例化示例）；`ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（Stage 6 + 待裁决点 P2-A HMR 配置快照、P2-D deactivate 父实例语义）
> Related: `2026-08-14-2007-2-coeffect-reconcile.md`（前置：reconcile 引擎 + createInstance 门控——**尚未落地，处于 draft**，本 plan 依赖其三项裁定：门控 null、不自动创建、loadPlugin 后自动 reconcile）、`2026-08-14-1720-3-instance-lifecycle-effect-activator.md`（前置：实例生命周期）

## Purpose

把 subagent 层级实例化（createInstance(parent)：服务查找沿链回退、级联销毁、配置层叠）与定义变更热重载（reloadPlugin：快照重建）落地；裁定 P2-A（HMR 配置快照归属）与 P2-D（deactivate 父实例语义）两个待裁决点。这是 W1-W7 的最后一个实现 stage。

## Current Baseline

- **W5 尚未落地（draft 计划）**：本 plan 依赖 W5 的 reconcile 引擎与 createInstance 门控（null 返回）；执行本 plan 前必须确认 W5 已 completed，并复核其三项裁定（门控 null、reconcile 不自动创建、loadPlugin 后自动 reconcile）与 W6 的交互（见 Phase 2 与 Phase 4 的执行项）。W5 若实现偏差，本 plan 的 reload 重建与 P2-D 语义需相应调整。
- W3 产出：`createInstance(pluginId, instanceKey, config, parent)` 接受 parent 参数但**仅存储**（`PluginInstanceImpl.getParent()` 返回；`PluginInstanceImpl.java:63,146-148`）；子容器创建用 `new BeanContainerBuilder(classLoader, null)`（**parent container = null**，`PluginInstanceImpl.java:249`）；合并视图 = 定义默认 + 实例配置（`newMergedView()`，`PluginInstanceImpl.java:205-209`，无父配置层叠）。
- `BeanContainerImpl` 原生支持 parentContainer：`getBeanByType` 未命中时回退父容器（`BeanContainerImpl.java:310-315`），`getBeansOfType`/`containsBean` 同款回退；`BeanContainerBuilder(classLoader, parentContainer)` 构造存在（`BeanContainerBuilder.java:78-80`）；`setConfigProvider` 是 BeanContainerImpl 的独立可变字段——**parent 链与实例 provider 可共存**（reviewer 已核对）。
- `VfsPluginDefinition` 实例 registry 是扁平 map（pluginId + instanceKey，`VfsPluginDefinition.java:52`），无父子关系索引；无级联销毁逻辑；`IPluginManager` 无 `reloadPlugin`；无配置快照机制；无资源变更检测接线。
- `VfsPluginDefinition.getLastChangeTime()` 当前 = load 时的**时钟时间**（`VfsPluginDefinition.java:110`）——变更检测不能直接复用它（需记录资源真实 lastModified，见 Phase 3）。
- 平台已有变更检测基础设施：`ResourceComponentManager.checkChanged(resourcePath, lastModified)`（nop-core，`ResourceComponentManager.java:157-158`），并有 `setChangeChecker` 注入点（`:147`，测试可注入 fake checker）。
- 测试夹具：`agent-instance.plugin.xml` 等 VFS 轨定义；无 parent 链测试、无 HMR 测试；测试资源位于 classpath（`src/test/resources/_vfs/`），**不可写**——HMR 变更检测测试需可写资源（见 Phase 3 测试机制裁定）。

## Goals

- parent 层级实例化：`createInstance(parent)` 子实例子容器 parent = 父实例容器（服务查找沿链回退）；配置层叠 = 父合并视图 + 定义默认 + 子配置（子覆盖父）；destroy 父实例**级联 destroy** 全部后代（递归）；父子索引放 **manager 级全局映射**（跨定义 parent 链可见，见 Phase 1 Decision）。
- P2-D 裁决：deactivate 父实例语义（**已裁定，见 Phase 4**：显式路径守卫式——存在 ACTIVATED 子实例时父 deactivate 抛异常；reconcile 路径级联式——先子后父；与 W5 引擎的跨 plan 契约已定）。
- `reloadPlugin(pluginId)`：快照采集（级联闭包）→ destroy 全部实例 → unload → load → 按快照重建 → reconcile；快照由 manager 持有（P2-A 裁决）。
- loader 依赖追踪接线：VFS 轨 `*.plugin.xml` 资源变更检测（真实 lastModified）→ reload 编排（显式检查入口形态）。
- 测试：parent 链服务回退、配置层叠、级联 destroy、P2-D 语义、reload 快照重建、变更检测触发。
- 设计文档修订（01 §三(a) 宿主回退裁决、01 §六 HMR 流程更新）——owner doc 同步属本 plan in-scope。

## Non-Goals

- 多租户配置命名空间（roadmap Stage 6 明确 out of scope；实例配置域已覆盖基础）。
- jar 轨的 HMR（设计 §六：远程 uber jar 不可编辑，HMR 面向本地/开发场景）；jar 轨 reloadPlugin 显式失败。
- coeffect spec 扩展（W5 已裁定 watch-only residual）。
- SHA256 补齐 + docs-for-ai 同步——W7。
- 顶层实例的宿主容器回退（"→ 宿主"语义不扩展，见 Phase 1 Decision——设计 01 §三(a) 需同步修订）。

## Scope

### In Scope

- parent 层级：子容器 parent 链、配置层叠（含父热应用传播）、级联 destroy（manager 级全局父子映射）、getParent 语义完整化。
- P2-D 裁决落地。
- reloadPlugin + P2-A 快照（级联闭包）。
- VFS 轨变更检测接线（真实 lastModified + 可写资源测试机制）。
- 设计文档修订（01 两处注解）。
- 测试矩阵。

### Out Of Scope

- jar 轨 reload / 实例化（沿用 W4 裁决：显式失败）。
- W7 全部交付物。

## Execution Plan

### Phase 1 - parent 层级实例化

Status: planned
Targets: `PluginInstanceImpl.java`、`PluginManagerImpl.java`（全局父子映射）、`VfsPluginDefinition.java`

- Item Types: `Fix | Decision`

- [ ] **Decision：子容器 parent 链接线**——子实例容器创建改为 `new BeanContainerBuilder(classLoader, parentContainer)`，parentContainer = 父实例的 ACTIVATED 容器；父 DEACTIVATED 时 createInstance(parent) 抛明确异常（禁止挂到已停容器）。先验证 builder 路径下 `setConfigProvider`（实例 provider，W3 注入顺序：build → cast BeanContainerImpl → setConfigProvider → start）与 parent 链可同时成立（reviewer 已核对字段独立，实现时以测试证实）；若验证失败，回退方案 = 子容器不设 parent、服务回退由 `PluginScopeImpl` 手工沿 `getParent()` 链查找（API 语义不变）。
- [ ] **Decision：父子索引归属**——级联关系放 **manager 级全局映射**（`PluginManagerImpl` 持有 instance → children 集合，createInstance(parent) 时登记、destroy 时移除；**实例亦持 children 引用**——父热应用传播（见下）与 P2-D 守卫需要实例级访问）：跨定义 parent 链（`IPluginManager.createInstance` 的 parent 可为任意定义实例，设计 01 §三(a) 不限定同定义）必须可观测，定义持有类级索引看不见其他定义的实例（会静默降级级联）。级联 destroy：递归收集后代（沿全局映射），**先子后父**；destroy 时从映射移除。
- [ ] **Decision：顶层实例的宿主回退**——顶层实例容器 parent 保持 null（W3 现状不变，实例服务解析不扩大到宿主容器）；设计 01 §三(a) "子容器 parent = 父实例容器 → 宿主"的"→ 宿主"部分**不扩展**（避免实例级 getService 可解析宿主 bean 导致隔离面扩大），01 文档同步修订（Phase 2 设计修订项）。parent 链成环防护：createInstance(parent) 时沿 `getParent()` 链回溯校验 parent 不是自身/后代（抛明确异常）。
- [ ] 配置层叠：`newMergedView()` 改为 = 父实例 `getConfig()`（父合并视图）+ 定义默认 + 子实例配置（子覆盖父）；**父配置热应用对子传播（传播路径钉死，防空心接线）**：父实例 `onDefinitionConfigChanged`（P2-C 路径，`PluginInstanceImpl.java:191-203`）更新自身合并视图后，**经其 children 集合递归调用子实例的同名重算方法**（子重算合并视图 + 触发各自 provider 变更）；该链路的末端验收见 Exit Criteria（不得只改父而子持陈旧快照）。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **接线验证**：子实例 `getService(T)` 未命中本容器时回退到父实例容器并返回父实例服务（测试断言：子容器无 bean T，父容器有，子 getService 命中；含**跨定义 parent 链**用例）。
- [ ] 配置层叠：子覆盖父键 + 父独有键继承（测试断言 `getConfig()` 合并视图值）；父 `updateConfig` 热应用后子 `getConfig()` 反映新值（测试断言，验收"父热应用传播"）。
- [ ] 级联 destroy：destroy 父 → 子（及孙，跨定义）实例从 registry 移除（测试断言）；父 DEACTIVATED 时 createInstance(parent) 抛明确异常、parent 环创建抛明确异常（测试断言）。
- [ ] **无静默跳过**：跨定义级联真实发生（全局映射驱动，非定义持有类索引）；所有异常路径显式抛出。
- [ ] No owner-doc update required（设计文档修订集中在 Phase 2 执行项）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - reloadPlugin + P2-A 快照（含设计文档修订）

Status: planned
Targets: `IPluginManager.java`（新增 reloadPlugin）、`PluginManagerImpl.java`、`ai-dev/design/nop-plugin/01-architecture-baseline.md`（修订）

- Item Types: `Decision | Fix`

- [ ] **P2-A 裁决：HMR 配置快照归属**——快照由 manager 持有：reloadPlugin 前采集 (a) 定义级 definitionConfig（updateConfig 累积值）+ (b) **级联闭包内全部实例**的 (instanceKey, config, **parent 引用以 (pluginId, instanceKey) 对形式记录**——父实例对象在 destroy 后失效，重建时必须按 id 对解析到新实例) 列表——闭包 = 本次将被级联 destroy 的全部实例（含**跨定义后代**，经 Phase 1 全局映射收集）；快照生命周期：reload 流程结束后失效（下次 reload 重新采集），不长期保留。
- [ ] `reloadPlugin(pluginId)` 编排（VFS 轨）：快照采集 → destroy 全部（级联，Phase 1 链路）→ unload → load（重解析 plugin.xml，新定义对象）→ 按快照重建（**父先建子后建**，重建的 parent = 新父实例对象；跨定义后代仅当其定义仍 LOADED 时重建，否则记录显式错误——不静默丢失）→ reconcile（W5 引擎）。
- [ ] **重建 × W5 门控交互**：重建时 `createInstance` 返回 null（定义级条件不满足，W5 语义）不是错误——记录"条件未满足暂缓重建"并**保留该实例的快照项为 pending**（manager 挂 pending 列表，reconcile 触发点重试重建；这是"快照恢复语义"，与 W5"reconcile 不自动创建"裁决不冲突）；null 以外的异常按失败处理。
- [ ] **失败路径钉死（No Silent No-Op）**：destroy/unload 段失败 → 定义保留 LOADED（部分实例可能已销毁，状态可观测），**快照不丢弃**（调用方可重试 reload）；load 段失败 → 定义 UNLOADED，快照保留；重建段失败 → 已建实例保留（可观测），pending 项可重试。所有失败路径显式报告（日志/异常），不吞。
- [ ] jar 轨 reloadPlugin：抛明确异常（复用或新增错误码，语义"jar 轨不支持 HMR"），不静默 no-op。
- [ ] **设计文档修订（owner doc，本 plan in-scope）**：(a) 01 §三(a) 追加"宿主回退不扩展"裁决注解；(b) 01 §六 HMR 伪代码（单实例 deactivate→unload→load→activate）更新为多实例快照重建流程（P2-A 语义）。修订以裁决记录形式写入 01（设计文档是权威来源，与实现冲突时必须同步）。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**：loadPlugin → createInstance（带实例配置 + 父子链）→ 修改定义（更新 plugin.xml 内容）→ reloadPlugin → 新实例存在、快照配置保留（getConfig 断言）、父子链重建（getParent 断言）、reconcile 后状态正确。
- [ ] 跨定义后代在 reload 后不静默丢失：定义仍 LOADED 则重建（断言）；定义未 LOADED 则显式错误记录（断言日志/状态）。
- [ ] 门控 null（W5 语义）→ pending 重建路径有测试（不误判为失败）。
- [ ] jar 轨 reload 显式失败（测试断言）。
- [ ] 01 §三(a) / §六 修订已落地（git diff 断言）；P2-A 裁决已记录。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - loader 依赖追踪接线（变更检测）

Status: planned
Targets: `PluginManagerImpl.java`、`VfsPluginDefinition.java`（lastModified 记录）、`ResourceComponentManager` 调用点

- Item Types: `Decision | Fix`

- [ ] **Decision：变更检测形态**——框架核心不主动起轮询线程（避免隐式生命周期与测试耦合）；提供显式检查入口（如 `checkChangedAndReload()`：遍历 VFS 轨 LOADED 定义，用 `ResourceComponentManager.checkChanged(resourcePath, lastModified)` 检测 → 有变更调 `reloadPlugin`），宿主应用可定时调用（W7 docs 记录接线方式）。若执行期发现平台有事件式通知机制可复用，以事件式为准并记录。
- [ ] lastModified 来源钉死：`VfsPluginDefinition` load 时记录**资源真实 `IResource.lastModified()`**（不得复用现有 `getLastChangeTime` 时钟语义——`DefaultResourceChangeChecker.checkChanged` 是 `lastModified != resource.lastModified()` 严格比对，时钟值必然误报变更）；检查入口读取当前资源 lastModified 比对。
- [ ] **测试机制裁定**：classpath 测试资源不可写（意图上）——HMR 测试优先用 **`file:` 前缀 id 的可写临时目录**（`DefaultVirtualFileSystem` 已默认注册 `file:` 命名空间，`DefaultVirtualFileSystem.java:47`，无需自定义 VFS 注册；临时目录放项目 `_tmp/` 下），保持真实 lastModified 比对的端到端真实性；备选 `ResourceComponentManager.setChangeChecker` 注入 fake checker（`:147` setter，注意全局状态、finally 恢复）。执行时二选一（优先 file: 可写资源）。
- [ ] 依赖方收敛验证：被 reload 的 plugin 的 requires 依赖方（其定义级条件基于实例存在性）在 reload 后经 reconcile 收敛（W5 级联评估覆盖——不新增失效表）；接线验证见 Exit Criteria。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **接线验证**：修改可写资源 plugin.xml（内容变化）→ 显式检查入口 → `reloadPlugin` 被真实触发（测试断言：reload 后新定义生效，如 bean 变化可观测）。
- [ ] 无变更时检查入口 no-op（不触发 reload，断言——lastModified 比对正确性证明）。
- [ ] 依赖方收敛：requires 链上被 reload 定义的依赖方经 reconcile 收敛到正确状态（测试断言）。
- [ ] **无静默跳过**：检查失败路径显式报告（日志），不吞异常。
- [ ] No owner-doc update required（W7 docs 记录接线方式）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - P2-D 裁决 + 测试补全

Status: planned
Targets: `nop-plugin-manager/src/test/`

- Item Types: `Decision | Proof`

- [ ] **P2-D 裁决：deactivate 父实例语义（显式路径 + reconcile 路径，含跨 plan 契约）**——(1) **显式 deactivate 路径 = 守卫式**：父实例存在 ACTIVATED 子实例（经全局映射检查）时 `deactivate()` 抛明确异常（带子实例 key 参数；与 unload 守卫同构——先处理子再处理父）；destroy 仍级联（设计 §三(b) 明确）。(2) **reconcile 自动路径 = 级联式**（W5 引擎驱动，无法抛异常）：reconcile 决定 deactivate 某父实例时，**先级联 deactivate 其 ACTIVATED 子实例（保留实例对象），再 deactivate 父**——否则子实例容器 parent 指向已停容器，服务回退调用抛 `ERR_IOC_CONTAINER_NOT_STARTED`（`BeanContainerImpl.java:311,582-585`）硬失败，"服务沿链回退"契约破裂。(3) **跨 plan 契约**：此级联语义需要 W5 引擎感知父子关系——W6 在 reconcile 的实例评估前挂父链检查（父 DEACTIVATED 或链上祖先 DEACTIVATED → 子实例不激活、父实例去激活推迟到子先处理），W5 Phase 2 已预留扩展点（"W6 将把父链健康纳入激活条件"）；若 W5 已落地但未预留，W6 在 manager 的 reconcile 入口包一层父链处理（不侵入 W5 引擎）。**裁决理由**：级联 deactivate 与 W5 reconcile 自动重新激活冲突（子条件仍满足时父停即子停、reconcile 又激活）——守卫式（显式路径）与"父链抑制激活"（reconcile 路径）共同消除该冲突；裁决记录于 plan Closure。
- [ ] **createInstance 检查顺序裁定**：父状态检查（父存在且 ACTIVATED，失败抛明确异常）**先于** W5 定义级门控（不满足返回 null）——错误优先于条件（父 DEACTIVATED 是调用错误，显式抛出；门控是定义级条件，返回 null）；**父状态检查与 W5 重复 key 检查的相对顺序钉死**：checkLoaded → 重复 key（抛 `ERR_PLUGIN_INSTANCE_EXISTS`）→ 父状态（抛父异常）→ 门控（返回 null）→ 创建（重复 key + DEACTIVATED 父的边界 = 重复 key 异常优先）；两顺序均需测试覆盖。
- [ ] parent 链测试：两层（父→子）与三层（父→子→孙，可跨定义）服务回退、配置层叠（含父热应用传播）、级联 destroy（子先于父，递归）、父 DEACTIVATED 拒绝、环防护、P2-D 显式守卫（有 ACTIVATED 子实例时父 deactivate 抛异常）、**P2-D reconcile 路径（W5 引擎驱动父去激活 → 先级联子 deactivate 再父，子服务回退不触发 `ERR_IOC_CONTAINER_NOT_STARTED`）**、createInstance 检查顺序（父状态检查先于门控）。
- [ ] reload 测试：快照重建（配置保留）、父子链重建、跨定义后代重建/显式错误、门控 null → pending 重试、reload 后 reconcile 收敛、jar 轨失败、失败路径（load 抛错 → 定义 UNLOADED 快照保留可重试）。
- [ ] 变更检测测试：显式检查入口触发 reload（可写资源）；无变更 no-op；依赖方收敛。
- [ ] **W5 落地后复核项**：确认 W5 已 completed 后，复核其三项裁定与本 plan 交互（reload 触发点调用 reconcile、pending 重建挂接 reconcile 重试点、P2-D 守卫不触发 reconcile 冲突）；若 W5 实现有偏差，本 plan 相应调整并记录。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 上表每类测试存在且断言真实行为（实例状态、配置值、registry 内容、异常参数）。
- [ ] **端到端验证**：从 loadPlugin → createInstance(parent 链) → 服务回退 → 修改定义 → reloadPlugin → 快照重建 + 级联关系保持 → destroy 级联清理，完整链路有测试覆盖。
- [ ] P2-D 裁决已记录（含 Why，无 reconcile 冲突）；W5 复核项已执行。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 退出码 0。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] parent 层级成立：服务沿链回退（含跨定义）、配置层叠（含父热应用传播）、级联 destroy（递归、跨定义）（测试证明）。
- [ ] P2-A / P2-D 已裁决并落地（裁决记录 + 测试证明）。
- [ ] reloadPlugin 成立：级联闭包快照重建 + pending 恢复 + reconcile 收敛（端到端测试证明）。
- [ ] VFS 轨变更检测接线成立（真实 lastModified + 显式检查入口测试证明）。
- [ ] 设计文档修订完成（01 §三(a)、§六；git diff 证明）。
- [ ] W5 复核项已执行（W5 completed 后校验交互）。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [ ] 零依赖不变式：api 模块 grep 0 命中（`io.nop.ioc`/`io.nop.xlang`）。
- [ ] No owner-doc update required（docs-for-ai 使用文档 W7 统一同步）。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）reloadPlugin 调用链（变更检测 → 快照 → destroy → load → 重建 → reconcile）运行时连通（端到端测试），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs 2026-08-14-2007-3-parent-hierarchy-hmr.md --strict` 退出码 0（closure 时执行）。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-plugin-manager --severity high` 退出码 0。
- [ ] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### jar 轨 reloadPlugin / 实例化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §六 明确 HMR 面向本地/开发场景（远程 uber jar 不可编辑）；jar 轨实例化在 W4 已裁决为显式失败（`ERR_PLUGIN_INSTANCE_NOT_SUPPORTED`），本 plan reloadPlugin 对 jar 轨同样显式失败——当前 supported baseline 不承诺 jar 轨运行时重建。
- Successor Required: `no`
- Successor Path: （有发布场景需求时新计划）

### 顶层实例宿主容器回退（"→ 宿主"）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 设计 01 §三(a) 的 "→ 宿主" 在本 plan 裁定不扩展（顶层实例容器 parent 保持 null，隔离面不扩大），01 已同步修订注解；子链回退完整覆盖 subagent 场景（父实例链终点即顶层实例容器）。
- Successor Required: `no`
- Successor Path: （需要实例级 getService 解析宿主 bean 时新计划）

### 父链上的 IoC 错误码透传（ERR_IOC_UNKNOWN_BEAN_FOR_TYPE 等）

- Classification: `watch-only residual`
- Why Not Blocking Closure: W3 既有行为（`PluginScopeImpl.getService` 只翻译多候选错误），parent 链放大了穿透面；非本 plan 引入，不构成新 contract gap；如需翻译属独立治理项。
- Successor Required: `no`
- Successor Path: （W7 测试矩阵或独立计划）

## Non-Blocking Follow-ups

- HMR 轮询/文件监听由宿主应用集成（框架提供显式检查入口）——W7 docs 记录接线方式。
- 快照结构（definitionConfig + 实例列表）未来可持久化（跨重启热恢复）——当前内存态即可。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<按 guide 的 Closure Audit Evidence 清单填写>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>

## Optional Sections

## Risks And Rollback

- 子容器 parent 链若与实例 provider 注入冲突（builder 路径不传播 provider 的历史教训）：Phase 1 先验证再实现（reviewer 已核对字段独立）；回退方案 = 子实例容器不设 parent、服务回退由 PluginScopeImpl 手工链式查找（API 语义不变）。
- reload 快照重建若与 W5 自动 reconcile 并发：reconcile 幂等可重入（W5 设计），reload 编排在 manager 侧串行化（锁）；pending 重建挂接 reconcile 重试点。
- 级联 destroy 若与 unload 守卫交互：父 destroy 级联后实例数为 0 才可 unload——语义一致，测试覆盖。
- W5 未落地风险：本 plan 依赖 W5 三项裁定；若 W5 执行出现偏差，Phase 4 复核项强制校正（reload 触发点、pending 重试、P2-D 守卫）。
