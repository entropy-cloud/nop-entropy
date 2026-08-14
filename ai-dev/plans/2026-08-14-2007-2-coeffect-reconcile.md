# 5 coeffect + reconcile（W5）

> Plan Status: active
> Mission: nop-plugin-enhancement
> Work Item: W5 coeffect + reconcile
> Last Reviewed: 2026-08-14
> Review Consensus: 2026-08-14（独立 reviewer 多轮对抗性审查达成共识：无 Blocker，advisory 已全部并入）
> Source: `ai-dev/design/nop-plugin/01-architecture-baseline.md`（§五 Reactive Coeffect、§7.3 reconcileInstances、§7.6 IPluginContext）；`ai-dev/design/nop-plugin/03-coeffect-and-agent-example.md`（§1.2-§1.4 载体与依赖链示例）；`ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（Stage 5 + Cross-cutting 待裁决点 P2-C 实例侧已在 W3 裁定）
> Related: `ai-dev/plans/2026-08-14-1720-3-instance-lifecycle-effect-activator.md`（前置：实例 registry 唯一持有者 = 定义持有类；`IPluginContext` 实现推迟到本 plan）、`2026-08-14-2007-1-getservice-proxy-command-routing.md`（执行顺序 1）、`2026-08-14-2007-3-parent-hierarchy-hmr.md`（执行顺序 3，依赖本 plan 的 reconcile 引擎，跨 plan 契约见 Phase 2 Decision）

## Purpose

把"运行时条件激活/去激活"落地：解析 plugin.xdef 的 coeffect spec（`requires`/`if-property`），实现 `IPluginContext.reconcile()`（定义级门控 + **定义级×实例级级联评估**，迭代收敛 + 环检测报告），`createInstance` 在定义级条件不满足时 no-op 返回 null，实现层配置订阅自动触发 reconcile。这是 W6（parent/HMR）的前置。

## Current Baseline

- W1 产出：plugin.xdef 原生属性 `requires="csv-set"`（依赖 plugin id 集合）、`if-property="string"`（格式 `propName|expectedValue`，缺省 expectedValue 视为 true）；`IPluginContext` 接口已定义（`getInstance/allInstances/reconcile`，javadoc 写明只做 registry + coeffect reconcile、不暴露宿主容器）。
- W2 产出：VFS 轨定义经 `DslModelParser` 解析为 `DynamicObject`；`VfsPluginDefinition` 目前只提取 `beans` 与 `activator`（`VfsPluginDefinition.java:59-64`），**`requires`/`if-property` 尚未解析为类型化字段**。
- W3 产出：per-definition 实例 registry（`VfsPluginDefinition.instances`，唯一持有者；`VfsPluginDefinition.java:36-37` javadoc 写明"W5 的 IPluginContextImpl 以同一 registry 为数据源"——本 plan 实现后需同步该 javadoc）；`createInstance` 立即激活（无条件）；`PluginInstanceImpl` 生命周期 + 实例配置域 + `getConfig()` 任何态可读（实例级 coeffect 数据源已就绪）；P2-C 定义级 updateConfig 热应用/缓存已落地。
- `PluginManagerImpl` 持有全部定义（`plugins` map，`PluginManagerImpl.java:47`），是唯一能遍历"全部 LOADED 定义 + 各自实例"的位置——`IPluginContext` 实现的天然宿主。
- 测试夹具已声明 coeffect：`agent-instance.plugin.xml` / `agent-tools.plugin.xml` 均有 `requires="model-provider" if-property="agent.tools.enabled|true"`；**仓库中无 `model-provider` 夹具**（依赖未满足）。`TestPluginInstanceLifecycle`（15 用例）**仅用 `agent-instance` 夹具**直接 createInstance 并断言非 null（`agent-tools` 由 `TestPluginManager` 的 start 路径测试与 `TestPluginXdef` 解析测试消费）——**本 plan 门控落地后这些用例会拿到 null，必须显式适配**（见 Phase 3，适配范围含 `TestPluginManager`）。
- **`TestPluginManager.testVfsTrackAwareDefinitionState`（:158-166）的 start 断言与门控冲突**：该测试断言 `agent-tools.start(...)` 抛 `ERR_PLUGIN_ACTIVATOR_NOT_FOUND`（activator 未声明于 beans）；门控落地后（model-provider 未加载）start → createInstance → 返回 null 不抛异常——该断言必挂，适配范围必须覆盖（见 Phase 3）。
- **数据流缝（cross-class seam）**：门控评估需要 manager 级状态（其他定义的实例、全局配置订阅），而 `VfsPluginDefinition` 无 manager 引用（`PluginManagerImpl.loadPluginFromVfs` 直接 `new VfsPluginDefinition(id, definition)`，`PluginManagerImpl.java:175`）——接线方式必须钉死（见 Phase 2/3 执行项：manager 在 load 时注入评估器回引）。
- `IConfigProvider.subscribeChange(pattern, listener)`（nop-api-core，`IConfigProvider.java:42`）已有；`InstanceConfigProvider`（W3）本地监听表 + 全局委托。
- `IPluginManager` 无 `reconcileInstances()`（设计 §7.3 列出）；`IPluginContext` 无实现类。
- 既有错误码：`ERR_PLUGIN_INACTIVE`（PluginApiErrors）、`ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES`（PluginManagerErrors）等；本 plan 可能新增 coeffect 相关错误码。

## Goals

- coeffect spec 解析：`VfsPluginDefinition` 提取 `requires`（Set<String>）与 `if-property`（propName + expectedValue）。
- `IPluginContext` 实现（宿主 = `PluginManagerImpl`，registry 单一数据源不另起第二份）：`getInstance/allInstances/reconcile`。
- `reconcile()`：**定义级×实例级级联评估**（详见 Phase 2 Decision——现有实例同时受定义级与实例级条件约束，依赖链/配置变化可驱动实例自动 activate/deactivate）；迭代收敛 + 静态环检测报告；激活失败置回 DEACTIVATED 并重试，失败超阈值暂停并报告。
- `createInstance` 定义级 coeffect 不满足 → **no-op 返回 null**（设计 §五明确语义；W3 无条件创建行为按此收敛，**W3 测试显式适配**）。
- 实现层配置订阅：全局配置变更（定义级 if-property 键）经 `subscribeChange` 自动触发 reconcile；生命周期操作（load/unload/create/destroy）与定义级 updateConfig 后自动 reconcile；API 层只暴露显式 `reconcile()`（零依赖不变式保持）。
- `IPluginManager.reconcileInstances()`（§7.3）落地（委托 reconcile，供 manager 使用者直接调用）。

## Non-Goals

- getService 代理/命令路由——W4（独立计划，执行顺序 1）。
- parent 层级 + HMR——W6（本 plan 的 reconcile 引擎为 W6 提供入口；parent 挂靠、级联、父链抑制在 W6，见 Phase 2 Decision 跨 plan 契约）。
- SHA256 补齐 + docs-for-ai 同步——W7。
- coeffect 完整类型系统（设计 §九 拒绝项 7：仅取条件性激活/去激活工程语义）。
- `if-property` 多条件/或/与组合的 schema 扩展（W1 已裁定 watch-only residual：单条件满足本 plan 需求，扩展时在同一 plugin.xdef 文件内进行）。
- jar 轨定义参与 reconcile（jar 轨无实例机制，`reconcile` 遍历时跳过非 VFS 定义——见 Phase 2 执行项）。

## Scope

### In Scope

- coeffect spec 解析（VfsPluginDefinition 类型化字段 + 非法格式显式失败）。
- `IPluginContext` 实现 + `IPluginManager.reconcileInstances()`。
- reconcile 引擎：定义级×实例级级联评估、迭代收敛、静态环检测报告、失败重试阈值。
- `createInstance` 定义级 no-op（返回 null）语义收敛 + W3 测试适配。
- 配置订阅 + 生命周期事件自动触发（实现层）。
- 测试：依赖链自动激活/去激活、实例级配置驱动、环检测报告、createInstance 门控、自动触发。
- 设计文档注解（03 §1.4 与 01 §五 的语义裁定落注）。

### Out Of Scope

- W6 parent/HMR、W7 SHA256/docs、W4 代理。
- `updateConfig` 与 reconcile 的交互再设计（P2-C 已裁定；本 plan 只消费合并视图）。
- 自发性的"load 即自动创建实例"（本 plan 裁定 reconcile 不自动创建新实例——见 Phase 2 Decision；W6 reload 快照恢复是恢复语义，非自发创建）。

## Execution Plan

### Phase 1 - coeffect spec 解析

Status: planned
Targets: `VfsPluginDefinition.java`

- Item Types: `Fix`

- [ ] `VfsPluginDefinition` 提取类型化字段：`getRequires()`（`requires` csv-set → Set<String>，空集合语义 = 无依赖）、`getIfPropertyName()/getIfPropertyExpected()`（`if-property` 按 `|` 拆分：`propName` + `expectedValue`，无 `|` 时 expected 视为布尔 true；格式见 03 §1.2）。
- [ ] 解析契约钉死：DynamicObject 属性取值键为 camelCase（`definition.prop_get("requires")` / `definition.prop_get("ifProperty")`——W1 解析测试已确认 `TestPluginXdef:48-49` 的 camelCase 行为）；空值/null 处理显式（无 requires = 无条件；无 if-property = 无配置条件）；格式非法（如 `|` 分隔后 propName 为空）**在 load 解析时（构造即校验）**抛明确异常（新错误码，如 `ERR_PLUGIN_INVALID_COEFFECT_SPEC`，带 pluginId+属性名参数）而非静默忽略（No Silent No-Op）。
- [ ] 单元测试：合法 spec 解析（含无 `|` 形式、空值形式）、非法格式抛异常（断言错误码/消息）。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `getRequires`/`getIfProperty*` 行为与 03 §1.2 格式逐条对应（测试断言具体值：requires 集合元素、propName、expectedValue 默认 true）。
- [ ] **无静默跳过**：非法格式显式抛异常（测试断言）。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - IPluginContext 实现 + reconcile 引擎

Status: planned
Targets: `PluginManagerImpl.java`、`IPluginManager.java`（新增 `reconcileInstances()`）、`PluginManagerErrors.java`（如新增错误码）

- Item Types: `Decision | Fix`

- [ ] **Decision：实现形态**——`PluginManagerImpl` 实现 `IPluginContext` 接口（getInstance/allInstances/reconcile 直接基于 `plugins` map 与各定义 registry，单一数据源）；不新建第二份 registry（W3 deferred 裁决），不引入独立 IPluginContextImpl 类（避免与 manager 共享内部状态时的双份来源；`VfsPluginDefinition.java:36-37` javadoc 同步为"IPluginContext（由 PluginManagerImpl 实现）以同一 registry 为数据源"；**`IPluginContext` 接口 javadoc（:27"环检测 + 最大迭代"）同步为"静态环检测 + 防御性迭代上限"**）；`reconcileInstances()` 为 IPluginManager 新增抽象方法（manager 层接口，仓内唯一实现 PluginManagerImpl，同步实现）。**数据流缝钉死**：定义级评估器以回引形式注入——`PluginManagerImpl.loadPluginFromVfs` 在创建 `VfsPluginDefinition` 后调用 `plugin.setCoeffectEvaluator(...)`（包级接口，manager 实现：查其他定义实例状态 + 读全局配置），门控与级联评估统一经该评估器；`updateConfig` 后的 reconcile 触发经 manager 在 load 时注入的 `onConfigChanged` 回调（定义持有类持有 Runnable 引用，manager 传入 `this::reconcile`）。
- [ ] **Decision：定义级×实例级级联评估（本 plan 的核心裁定，也是与 03 §1.4 示例冲突的裁决点）**——(1) 定义级评估：requires 满足 = 每个依赖 pluginId 存在且**至少一个实例 `getState()==ACTIVATED`**（依赖身份解析见下一条 Decision）；if-property（定义级）满足 = 全局配置中 propName 对应值等于 expectedValue（缺省 expected 时 = true）。(2) **现有实例的激活状态同时受定义级与实例级条件约束**：reconcile 对每个实例求值——定义级满足 **且** 实例级满足 → ACTIVATED（DEACTIVATED 则 activate）；任一不满足 → DEACTIVATED（ACTIVATED 则 deactivate）；否则 neutral 不动。**实例级 if-property 求值语义（防 W3 测试全灭的关键裁定）**：实例合并视图优先、未命中**回退全局配置**（与 `InstanceConfigProvider.getConfigValue` 的合并视图语义一致——实例配置域本身就是"全局+实例覆盖"视图，`InstanceConfigProvider.java:258-271`）；该语义下 W3 测试实例配置（不含 `agent.tools.enabled`）仍从全局读到 true（门控/级联不误杀），而 agent-1/agent-2 各自配置携带该键时仍可产生隔离差异（sandbox 开/关场景）。**裁决理由**：仅配置域驱动的实例级评估使依赖链永不可达（父 deactivate 后子仍 ACTIVATED、全局配置变化无法驱动任何实例），03 §1.4 依赖链示例（"父激活 → 子自动激活；配置变化 → 自动 activate/deactivate"）依赖该级联语义；01 §五伪代码的"允许 createInstance"门控语义是创建门控，与本裁定不冲突（门控管创建，级联管已有实例的激活状态）。裁决记录于 plan Closure 并向 03 §1.4 落注解（见 Phase 3 设计注解项）。
- [ ] **Decision：requires 依赖身份解析（钉死，防实现期发明）**——fixtures 声明 `requires="model-provider"`（plugin.xdef `@name`），而 manager 的 `plugins` map 键是 VFS 路径（如 `/nop/plugin/test/model-provider.plugin.xml`）：依赖匹配按定义 **`@name` 属性**——manager 维护 name→definitions 索引（loadPlugin 时注册、unloadPlugin 时移除，**unload 失败路径（`ERR_PLUGIN_INSTANCES_NOT_EMPTY`）不得清除索引**）；**同名多个定义合法**（不同路径可声明同名），requires 匹配任一名下定义有 ACTIVATED 实例即满足，加载时记录日志；`@name` 为 xdef 必填（`name="!string"`），"无 name 时以 id 匹配"仅为防御性兜底（不可达，注明）。该裁定是 W6 级联与 reload 的公共契约。
- [ ] **Decision：定义级"激活"语义（不自动创建）**——reconcile **不自动为无实例的定义创建实例**：定义级评估只产出门控 + 对已有实例的级联状态；实例由 `createInstance`（显式调用）或 W6 reload 快照恢复创建。03 §1.4 "loadPlugin → 自动 activate" 的达成路径 = 显式 createInstance + reconcile 自动维持激活状态（loadPlugin 后自动 reconcile 只负责状态收敛，不创建）。裁决记录。
- [ ] **Decision：环处理**——静态环检测：reconcile 开始时对全部 LOADED VFS 定义构建 requires 依赖图，DFS 检测环；环成员定义**强制门控关闭**（即使其他条件满足也不允许派生实例/激活其已有实例的激活条件视为不满足——级联语义下环成员实例被 deactivate）并**报告**（**报告暴露面钉死**：`PluginManagerImpl` 具体类暴露 `Set<String> getUnresolvedPluginIds()`（非接口方法，不进 IPluginManager/IPluginContext——保持接口最小）+ 日志）。迭代引擎另设最大迭代数 = LOADED 定义数作为防御性终止保护（级联收敛通常 ≤ 2 轮；测试断言终止性而非报告）。**裁决理由**：纯门控语义下依赖环天然收敛（谁也不激活），"最大迭代后 unresolved"不可观测——静态环检测使报告确定且可测。
- [ ] **reconcile 重入防护（防丢通知）**：reconcile 执行中置 in-flight 标记（volatile/锁）；subscribeChange 回调等重入调用**置 dirty 标记**（不直接丢弃——配置轮询只在变更时触发，丢弃通知可能永久丢失该轮），当前 reconcile 结束后若有 dirty 标记再跑一轮；生命周期操作触发的 reconcile 经同一入口串行化。
- [ ] 实例级评估数据源核对：`instance.getConfig()` 为合并视图快照（`PluginInstanceImpl.java:151-154`）；expectedValue 比较语义裁定（字符串与布尔/数字的宽松比较：`"true"` 与 `Boolean.TRUE` 等价；数值字符串与数字等价——裁定并实现统一比较器，两端（定义级全局、实例级合并视图）共用）。
- [ ] **父链扩展点预留（跨 plan 契约，W6 消费）**：实例级评估判定函数留扩展点——W6 将把"父链健康"（父实例存在且 ACTIVATED）纳入实例激活条件（P2-D reconcile 路径：父 DEACTIVATED 时子实例不激活；父实例去激活的执行顺序为**先子后父**——W6 Phase 4 裁定）；本 plan 实现时以包级评估器接口（Phase 2 注入的回引）承载，不将父链逻辑写死进引擎。
- [ ] reconcile 执行语义：遍历全部 LOADED **VFS 轨**定义（jar 轨跳过——无实例机制，`instanceof VfsPluginDefinition` 判别）；对每个实例的 activate/deactivate **同步等待完成**（W3 的 `FutureHelper.futureCall` 实为同步执行，syncGet 即可——失败捕获记录，实例已由 W3 回退 DEACTIVATED）；失败计数：同一实例连续失败 ≥ 阈值（常量，如 5 次）→ 暂停该实例的自动激活（仅显式 `instance.activate()` 可恢复），报告日志。**暂停可观测性钉死（测试断言路径）**：经 FailActivator 的激活尝试计数器断言——阈值后 reconcile 不再尝试（计数不变），显式 activate() 恢复（计数 +1）。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **接线验证**：`reconcile()` 真实遍历 manager 的全部定义与实例（测试：2 定义 + 3 实例拓扑，reconcile 后状态符合级联评估预期）。
- [ ] 级联激活/去激活真实发生（依赖链测试：父实例激活后 reconcile → 子实例自动 activate；父 deactivate 后 reconcile → 子实例自动 deactivate）。
- [ ] 静态环检测：A↔B 依赖环 → reconcile 正常终止（无死循环）、环成员不激活、unresolved 报告可观测（测试断言报告内容）。
- [ ] **无静默跳过**：unresolved 报告、失败阈值暂停、jar 轨跳过均为真实实现（测试断言日志/状态/返回）。
- [ ] Decision 三项（级联语义、不自动创建、静态环检测）已记录于 plan Closure。
- [ ] No owner-doc update required（设计注解项在 Phase 3 统一落）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - createInstance 门控 + 自动触发接线 + 设计注解

Status: planned
Targets: `PluginManagerImpl.java`、`VfsPluginDefinition.java`、`IPluginManager.java` javadoc、`ai-dev/design/nop-plugin/03-coeffect-and-agent-example.md`（注解）

- Item Types: `Fix | Decision`

- [ ] `createInstance` 定义级门控（**位置钉死**：`VfsPluginDefinition.createInstance` 入口，顺序 = checkLoaded → **重复 key 检查（抛 `ERR_PLUGIN_INSTANCE_EXISTS`）→ 门控（返回 null）→ 创建**——重复 key 先于门控：门控关闭但实例已存在（先开后关场景）时仍抛重复 key 异常，不静默返回 null）：定义级 spec 不满足 → **返回 null**（no-op，设计 §五；javadoc 同步"返回 null = 定义级 coeffect 不满足"）；满足 → 既有行为（创建 + 立即激活）。门控评估经 Phase 2 注入的 coeffect evaluator。
- [ ] **W3/W4 测试适配（显式执行项，范围 = `TestPluginInstanceLifecycle` + `TestPluginManager`，执行时扫描 W4 新增测试一并纳入）**：(a) `TestPluginInstanceLifecycle`——新增 `model-provider.plugin.xml` 夹具（**形状钉死：无 `activator` 属性、无或平凡 beans、不引用 `AgentInstanceRecorder`**——激活必须成功，否则门控永不打开）+ 测试 setUp 依次 `loadPlugin(model-provider)`、`createInstance(model-provider, ...)`（产生 ≥1 ACTIVATED 实例，使 requires 腿打开）+ 全局配置 `agent.tools.enabled=true`（setUp 赋值——JVM 全局键，注意与其他测试类的共享基线），既有断言语义不变（非 null、activatedCount、**`@AfterEach` 统一清理（钉死，防实现期发散）：destroy 本用例实例 → destroy model-provider 实例 → 恢复全局配置键原值（捕获赋值前值而非硬编码恢复）**，保持测试隔离）；门控关闭路径用新夹具（requires 引用不存在的 plugin id）单独测试。**不得**删除 W3 夹具的 requires/if-property 属性规避。(b) `TestPluginManager.testVfsTrackAwareDefinitionState`（:158-166）——该类无 `@BeforeEach`、各测试自行构造 manager（`TestPluginManager.java:66-75`）：在该测试内联 `loadPlugin(model-provider)` + `createInstance(model-provider, ...)` **并显式赋值全局 `agent.tools.enabled=true`（钉死：捕获原值并恢复——finally/tearDown 恢复为捕获的前值，防跨测试类污染）**——agent-tools 的门控有两条腿（requires + 定义级 if-property 读**全局**配置），只开 requires 腿门控仍关闭、start 不创建实例、`ERR_PLUGIN_ACTIVATOR_NOT_FOUND` 断言必挂（JUnit 类执行顺序不保证 TestPluginInstanceLifecycle 先跑，不能依赖其 setUp）；若内联改动破坏该测试的其他断言，改为显式断言"门控关闭时 start 不创建实例"（二选一，优先保留原断言语义）。(c) **`testUpdateConfigHotApplyAndDeactivatedCaching`（:296-322）适配（W5 语义下的必然变化，必须先钉死）**：该测试现为"deactivate（条件满足）→ updateConfig → 断言缓存 prod（仍 DEACTIVATED）"——W5 的 updateConfig 自动 reconcile（Phase 3(c)）会在条件满足时立即重新激活实例，缓存断言必挂。**P2-C 缓存契约在 W5 语义下的可观测窗口 = 条件不满足时**：适配为"**显式** `inst.deactivate()`（钉死为显式路径，不用 reconcile 去激活路径——触发次数/断言点单一化）→ **关闭条件**（如全局 `agent.tools.enabled=false`，实例级与定义级同时不满足）→ updateConfig → 断言缓存值（仍 DEACTIVATED）→ **恢复条件后显式 `manager.reconcileInstances()` 触发激活**（钉死：测试环境 `SimpleConfigProvider` 的 `assignConfigValue` 不触发订阅回调，自动订阅路径由 Phase 4(d) 可控 provider 测试覆盖——本适配的激活必须显式触发）→ 断言新值生效"——缓存契约与 reactive 语义同时可观测。
- [ ] 门控对 `start()` 兼容路径的影响裁定：`start` = load + createInstance(默认 key)（W3 收敛语义）——定义级不满足时 start 不创建实例并记录日志（与 createInstance null 语义一致，不抛异常；§7.1 start 是旧语义封装，行为记录于 javadoc）。
- [ ] 自动触发接线（触发点全集钉死，消除实现期发明）：(a) 全局 `IConfigProvider.subscribeChange` 订阅各 LOADED 定义的 if-property 键（load 时订阅、unload 时取消订阅；重复键订阅无害——reconcile 幂等可重入）→ 变更回调调 `reconcile()`；**provider 获取缝钉死**：manager 新增 setter **`setGlobalConfigProvider(IConfigProvider)`**（**名称必须区别于既有 `setPluginConfigProvider(IPluginConfigProvider)`**——jar 轨配置，`PluginManagerImpl.java:67-70`；缺省 `AppConfig.getConfigProvider()`——注意 `SimpleConfigProvider.subscribeChange` 返回 null、`assignConfigValue` 不触发订阅回调，**生产语义 = 配置源轮询（`DefaultConfigProvider.applyChange`）驱动的变更事件**）；自动触发测试注入可控 provider（记录订阅 + 手动触发 listener）验证接线（接线验证 ≠ 依赖生产 provider 的真实变更）。(b) 生命周期操作成功路径：loadPlugin/unloadPlugin/createInstance/destroyInstance 后自动调 `reconcile()`；(c) 定义级 `updateConfig`（P2-C 热应用路径）后经 Phase 2 注入的 `onConfigChanged` 回调自动调 `reconcile()`（实例级条件的唯一变化源——实例配置在 createInstance 后无独立变更 API，**实例级变化经 (b)(c) 覆盖，不接 InstanceConfigProvider 回调**，避免无注册机制的第二订阅链）；(d) 显式 `reconcile()`/`reconcileInstances()`；**触发点全集边界注明**：直接调用 `IPluginInstance.activate/deactivate/destroy`（W3 测试常见路径）**不触发**自动 reconcile（显式实例操作由调用方自行负责显式 `reconcile()`——"全集"诚实性）。**评估器注入空安全钉死**：`VfsPluginDefinition` 的 coeffect 评估器缺省未注入时（非 manager 构造的防御场景）门控评估返回"满足"并记录警告日志（保守放行，manager 路径总是注入，不影响生产语义）。
- [ ] 设计注解（owner doc 同步项）：**03 §1.4 与 01 §五 两处**落裁决注解——03 §1.4："定义级激活语义以 01 §五 + W5 裁决为准：reconcile 不自动创建实例，依赖链经级联评估 + 显式 createInstance 达成"；01 §五："实例激活状态受定义级×实例级联合约束（W5 裁决）；环处理为静态依赖图检测 + 防御性迭代上限"——消除未来读者再提冲突。
- [ ] API 层零依赖复核：所有订阅/触发逻辑在 manager/support 实现层，`nop-plugin-api` 不出现 IConfigProvider/订阅类型。
- [ ] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `createInstance` 门控：定义级不满足 → null（测试断言）；满足 → 实例创建成功（回归断言）；重复 key 检查先于门控（`testDuplicateKeyThrowsExplicitError` 在门控满足下仍抛异常；门控关闭但实例已存在的边界用例有断言）。
- [ ] **接线验证**：全局配置变更（改 `agent.tools.enabled`）→ subscribeChange 回调 → reconcile → 实例自动 activate/deactivate 全链路有测试覆盖（03 §1.4 场景复现）。
- [ ] W3 适配（`TestPluginInstanceLifecycle` + `TestPluginManager`）后全部回归通过（`./mvnw test` 同 gate）。
- [ ] 设计注解已写入 03 §1.4 与 01 §五（owner doc 同步项，非 W7 移交）。
- [ ] api 模块 grep 无 `io.nop.ioc`/`io.nop.xlang`/`IConfigProvider` import（零依赖不变式）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 测试补全

Status: planned
Targets: `nop-plugin-manager/src/test/`

- Item Types: `Proof`

- [ ] **夹具清单钉死（防实现期发明；requires 一律按 `@name` 声明）**：`model-provider.plugin.xml`（name="model-provider"，无 activator、无/平凡 beans）、`cycle-a.plugin.xml`（name="cycle-a" requires="cycle-b"）+ `cycle-b.plugin.xml`（name="cycle-b" requires="cycle-a"）、`gate-closed.plugin.xml`（name="gate-closed" requires="missing-dep"）、既有 `agent-instance.plugin.xml`（name="agent-instance" requires="model-provider" if-property="agent.tools.enabled|true"）与 `agent-tools.plugin.xml`（保持激活失败语义，`TestPluginManager` 断言）；"2 定义 + 3 实例"拓扑 = model-provider 1 实例 + agent-instance 2 实例（agent-1/agent-2）；**FailActivator 夹具改造项**：为其增加激活尝试计数器（现有 FailActivator 无计数器，Phase 2 的暂停可观测性断言依赖它——列为显式夹具修改项）。
- [ ] 依赖链测试：model-provider（无条件）→ **agent-instance**（requires model-provider + if-property，**链上子定义必须是激活可成功的定义——agent-tools 的 activator 未声明于 beans，激活必失败，不能作为链子**）→ 实例级 if-property 开关（实例配置域 + 全局回退）：父实例 deactivate → 子实例自动 deactivate；父恢复 → 子自动激活；实例级条件变化（updateConfig → 自动 reconcile）驱动 activate/deactivate 往返；实例配置携带 `agent.tools.enabled=false` 的实例被级联 deactivate（与全局 true 的实例差异——隔离差异断言）。
- [ ] createInstance 门控测试：requires 未满足 → null（gate-closed 夹具）；满足 → 实例（回归）；start 在门控关闭时不创建实例（日志断言或状态断言）。
- [ ] 静态环检测测试：A↔B 互依赖 → reconcile 终止、unresolved 报告、A/B 均不激活。
- [ ] 自动触发测试：全局配置订阅触发（(a)，注入可控 provider：记录订阅 + 手动触发 listener → reconcile 被调用 + 实例状态变化断言）、生命周期操作触发（(b)）、updateConfig 触发（(c)）。
- [ ] **全局配置基线钉死（Phase 4 新增测试类）**：`agent.tools.enabled` 等全局键为 JVM 共享（`SimpleConfigProvider` 静态）——Phase 4 各测试类在 setUp 赋值（true）、tearDown 恢复/清理（含翻转 false 的用例），避免跨测试类污染。
- [ ] 失败阈值暂停测试：activate 持续失败（FailActivator 夹具，**改造后含尝试计数器**）→ 达到阈值后 reconcile 不再自动重试（计数器不变断言）；显式激活可恢复（计数器 +1 断言）。
- [ ] expectedValue 宽松比较测试：`"true"` vs `Boolean.TRUE`、数值字符串 vs 数字，定义级与实例级两端。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 上表每类测试存在且断言真实行为（激活状态、返回值、日志/报告可观测）。
- [ ] **端到端验证**：从 `loadPlugin`（依赖链：model-provider → agent-instance）→ 自动 reconcile → createInstance → 全局配置变化 → 自动 deactivate → 配置恢复 → 自动 activate 完整链路有测试覆盖（03 §1.4 场景）。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 退出码 0。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] coeffect spec 解析 + 定义级×实例级级联评估成立（测试证明）。
- [ ] `IPluginContext.reconcile()` + `IPluginManager.reconcileInstances()` 成立：迭代收敛、静态环检测报告、失败阈值（测试证明）。
- [ ] `createInstance` 定义级门控（不满足返回 null）成立 + W3 测试适配完成（回归全绿）。
- [ ] 自动触发成立（全局订阅 / 生命周期 / updateConfig 三链路，测试证明）。
- [ ] 设计注解已写入 03 §1.4 与 01 §五；03/01 与实现之间的语义差已裁决记录。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [ ] 零依赖不变式：api 模块 grep 0 命中（`io.nop.ioc`/`io.nop.xlang`/`IConfigProvider`）。
- [ ] No owner-doc update required（使用文档 W7 统一同步）。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）reconcile 调用链（loadPlugin/配置变更 → reconcile → 实例 activate/deactivate）运行时连通（端到端测试），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs 2026-08-14-2007-2-coeffect-reconcile.md --strict` 退出码 0（closure 时执行）。
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-plugin-manager --severity high` 退出码 0。
- [ ] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [ ] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [ ] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### `if-property` 多条件 / 或与组合

- Classification: `watch-only residual`
- Why Not Blocking Closure: W1 已裁定单条件格式（`propName|expectedValue`）满足本 plan 需求；多条件需扩展 plugin.xdef 属性，属结构层扩展（同一新文件内），不影响当前 supported baseline 成立。
- Successor Required: `no`
- Successor Path: （需要时在同一 plugin.xdef 内扩展 + 新计划）

### 顶层实例的宿主容器回退（与 W5 关系）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 定义级 if-property 读全局配置（本 plan 实现），实例级读实例合并视图；"实例级 getService 可解析宿主 bean"的宿主回退属 W6 裁定范围（W6 已裁决不扩展），本 plan 不依赖该语义。
- Successor Required: `no`
- Successor Path: W6

## Non-Blocking Follow-ups

- reconcile 自动触发点的触发频率优化（如批量事件合并）——当前按事件触发，规模场景可优化为定时批处理。
- 失败阈值参数（次数、窗口）配置化——当前实现层常量即可，配置化属优化项。

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

- `createInstance` 语义变更（无条件 → 门控 null）：W3 测试显式适配（Phase 3 执行项）；javadoc 同步。若裁定门控导致破坏面不可接受，回退为仅 reconcile 级联、不门控 createInstance（但设计 §五明确要求门控，回退需用户确认）。
- 级联评估的激活抖动：配置频繁变更可能反复激活/去激活——失败阈值与幂等设计缓解；极端抖动由 W7 测试矩阵覆盖。
- reconcile 与 W4 代理的交互：级联 deactivate 触发代理 INACTIVE（W4 语义），无冲突；W6 的父链抑制是本引擎的扩展点（Phase 2 Decision 已记录跨 plan 契约）。
