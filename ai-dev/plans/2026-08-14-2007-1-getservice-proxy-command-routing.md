# 4 getService 生命周期代理 + per-instance 命令路由（W4）

> Plan Status: completed
> Mission: nop-plugin-enhancement
> Work Item: W4 getService 生命周期代理 + per-instance 命令路由
> Last Reviewed: 2026-08-14
> Review Consensus: 2026-08-14（独立 reviewer 多轮对抗性审查达成共识：无 Blocker，advisory 已全部并入）
> Source: `ai-dev/design/nop-plugin/01-architecture-baseline.md`（§7.1 invokeCommand 兼容规则、§7.2 getService 代理语义）；`ai-dev/design/nop-plugin/03-coeffect-and-agent-example.md`（§2.6 代理语义）；`ai-dev/design/nop-plugin/04-interface-comparison.md`（失效语义对比）；`ai-dev/backlog/nop-plugin-enhancement-roadmap.md`（Stage 4）
> Related: `ai-dev/plans/2026-08-14-1720-3-instance-lifecycle-effect-activator.md`（前置，W3 closure follow-up 明确"唯一实现分支测试补全记入 W4 测试矩阵"）；`2026-08-14-2007-2-coeffect-reconcile.md`（执行顺序 2，消费 W4 的 getService 代理语义）

## Purpose

把消费者的强类型服务访问收口到"生命周期绑定代理"语义：`IPluginInstance.getService/getServices` 返回的引用在实例 deactivate/destroy 后调用必须快速失败（抛 INACTIVE），不悬空；同时把 `IPlugin.invokeCommand` 的 §7.1 兼容规则（实例数=1 路由 / 多实例抛异常 / 无 ACTIVATED 实例抛 INACTIVE）落地为真实行为——**覆盖 VFS 轨定义持有类与 AbstractPlugin 两个实现点**；补齐 W3 移交的测试缺口（唯一实现分支、多实例命令隔离），并评估 W3 遗留的 uber jar 轨实例化 successor 项。

## Current Baseline

- W3 产出：`PluginScopeImpl.getService/getServices` 已实现多候选规则（primary→唯一→多候选抛 `ERR_PLUGIN_MULTIPLE_SERVICE_CANDIDATES`，`PluginScopeImpl.java:90-102`），**返回真实 bean**（无代理包装）；`PluginInstanceImpl.getService/getServices` 委托 scope 并做 ACTIVATED 态检查（`PluginInstanceImpl.java:126-143`——这只是**调用点**检查，不是返回给消费者的生命周期绑定代理）。
- W3 产出：per-instance `invokeCommand/invokeCommandAsync` 已路由到本实例子容器命令 bean（`PluginInstanceImpl.java:156-176`，`nopPluginCommand_{command}` → fallback `nopPluginCommand_default`），已有测试 `testInvokeCommandRoutesToInstanceContainer`。
- `VfsPluginDefinition.invokeCommand/Async`（`VfsPluginDefinition.java:191-203`）无条件抛 INACTIVE（LOADED 且 0 实例语义），§7.1 的"实例数=1 路由 / 多实例抛异常"未实现。
- **`AbstractPlugin.invokeCommand/Async`（nop-plugin-support）aware 路径是 W4 显式 stub**（`AbstractPlugin.java:236-253`）：0 实例抛 INACTIVE（pluginId 参数用 `getPluginDefinitionPath()`，`:242`）；有实例抛 `UnsupportedOperationException("not yet implemented: per-instance command routing (W4)")`——本 plan 必须收口该 stub（in-scope，不可静默搁置）。**错误码放置约束**：support 模块不依赖 manager，`ERR_PLUGIN_INACTIVE` 因此位于 api 模块（`PluginApiErrors.java:18-19`）——本 plan 新错误码 `ERR_PLUGIN_MULTIPLE_INSTANCES` 同样必须放 `PluginApiErrors`（被 AbstractPlugin 使用）。
- **AbstractPlugin 不 override `getInstances()`**（IPlugin default 返回空列表，`IPlugin.java:114-119`）——aware 路径的"1 实例路由"分支在**现有生产路径不可达**（jar 轨 aware start 已显式失败，无实例机制），测试需子类 override + stub IPluginInstance（见 Phase 2 测试技术项）。
- `IPlugin.invokeCommand` javadoc（W1 产出）已写明 §7.1 态语义（`IPlugin.java:29-48`）——本 plan 使其成为真实行为。
- 测试夹具已有：`agent-instance.plugin.xml`（primary bean `tool.bash` + 非 primary `tool.search` + `nopPluginCommand_hello`）、`multi-candidate.plugin.xml`（两个无 primary 的候选）、`TestPluginInstanceLifecycle`（15 用例）。
- 平台代理基础设施：`ReflectionManager.instance().newProxyInstance(...)`（nop-core，`ReflectionManager.java:458`，GraalVM 原生镜像注册代理类——仓库惯例，优先于裸 `java.lang.reflect.Proxy`）。
- W3 closure 移交：唯一实现分支（无 primary 且仅一个实现）无独立测试用例；多实例命令隔离测试未做；W3 Non-Goals 声明 per-instance 命令路由已随 W3 落地，本 plan 只做语义复核与隔离测试。
- W3 deferred：uber jar 轨 createInstance 路径为显式 successor 项（"W4/W7 评估"），当前 `PluginManagerImpl.createInstance` 对 jar 轨定义抛 `ERR_PLUGIN_INSTANCE_NOT_SUPPORTED`。

## Goals

- `IPluginInstance.getService(Class)` 返回**生命周期绑定代理**：ACTIVATED 时调用路由到真实 bean；deactivate/destroy 后调用抛 INACTIVE（快速失败，不悬空）；重新 activate 后代理恢复可用（**代理按调用时当前 scope 重新解析目标**，绑定实例对象而非一次性引用）。
- `getServices(Class)` 返回代理集合（同语义，按 bean id 保持候选身份）。
- `IPlugin.invokeCommand/invokeCommandAsync` 落地 §7.1 兼容规则（**VfsPluginDefinition 与 AbstractPlugin 两个实现点**）：实例数=1 → 经该实例路由；多实例 → 抛明确异常（要求经 `IPluginInstance.invokeCommand` 显式指定实例）；无 ACTIVATED 实例 → INACTIVE。
- 测试补全：唯一实现分支、多实例命令隔离（per-instance 可观测）、代理生命周期矩阵、AbstractPlugin aware 路径路由。
- uber jar 轨实例化 successor 项评估并裁定（本 plan 做出裁决记录，不静默拖延）。

## Non-Goals

- coeffect spec 求值与 `reconcile()`——W5（实例级配置变化驱动的自动去激活不属本 plan；本 plan 代理的 INACTIVE 只由显式 deactivate/destroy 触发）。
- parent 层级实例化与级联销毁——W6。
- reloadPlugin/HMR——W6。
- SHA256 补齐与 docs-for-ai 同步——W7。
- `IPluginScope.getService` 的代理化（scope 是激活期句柄，activator 参数传递使用，保持返回真实 bean——见 Phase 1 Decision 项）。
- 多候选解析规则的任何改动（W3 已落地）。

## Scope

### In Scope

- getService/getServices 生命周期绑定代理（代理生成 + 状态检查 + 按调用重新解析委托）。
- `VfsPluginDefinition` + `AbstractPlugin` 的 invokeCommand/invokeCommandAsync §7.1 路由规则 + 新错误码（PluginApiErrors）。
- 测试补全（唯一实现分支、多实例命令隔离、代理生命周期矩阵、AbstractPlugin 路由）。
- uber jar 轨实例化 successor 项评估与裁决记录。

### Out Of Scope

- W5/W6/W7 全部交付物（见 Non-Goals）。
- 命令分发机制本身（bean 名约定与 fallback 已有，不改）。

## Execution Plan

### Phase 1 - getService 生命周期绑定代理

Status: completed
Targets: `nop-plugin-manager/impl/PluginInstanceImpl.java`、`PluginManagerErrors.java`

- Item Types: `Decision | Fix`

- [x] **Decision：代理机制与语义裁定**——(a) 代理实现 = `ReflectionManager.instance().newProxyInstance(...)`（GraalVM 注册，仓库惯例），仅支持接口类型：`serviceType.isInterface()` 为 false 时抛明确异常（`ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE`，beanType 参数），**`getServices(具体类)` 同规则抛错**（具体类无法代理，禁止静默返回裸引用/裸集合）；(b) `IPluginScope.getService` 保持返回真实 bean（激活期句柄，activator 在激活流程内调用，无失效语义需求；instance 级才是代理边界——设计 01 §7.4"激活期等价 instance 的 getService"措辞需注解澄清"等价指调用语义，scope 返回真实 bean 非代理"，见 Exit Criteria 设计注解项）；(c) **按调用重新解析**：代理 handler 每次调用从实例**当前** `scope` 重新解析目标 bean（不捕获一次性引用）；`scope == null`（deactivate 窗口内已置空）按 INACTIVE 处理，不 NPE；**包装时立即解析一次**（getService 调用点经 scope 解析、解析错误即时抛出——多候选错误在 getService 调用点抛出，`TestPluginInstanceLifecycle.java:358/367`（`testGetServiceMultipleCandidatesThrowsExplicitError`）回归保持）；(d) `getServices` 集合代理以 `getBeansOfType` 的 bean id 为候选键，重激活后按 id 重新解析；(e) `equals/hashCode/toString` 与业务方法同规则（需 ACTIVATED，deactivated 抛 INACTIVE——一致且无悬空；实现时若需要调试便利可单独裁定 toString）。**这些契约裁定需同步写入设计文档 01 §7.2/§7.4 注解**（guide Rule 14：使用契约归 design，不只在 closure/log 记录）。
- [x] **W3 测试调用点迁移（Phase 1 的前置适配）**：既有测试以**具体类**调用 `getService(ConfigReaderBean.class)`（`TestPluginInstanceLifecycle.java:174,175,187,284,302,318` 共 6 处，ConfigReaderBean 是具体类）——非接口代理规则落地后这些调用必抛异常。迁移方案：新增测试夹具接口 `IConfigReader`（ConfigReaderBean 实现它，暴露现有访问器），6 处调用点改为 `getService(IConfigReader.class)`；夹具接口放 `io.nop.plugin.test`。**不得**把 ConfigReaderBean 变成接口（它是有状态的 bean，接口化破坏 bean 定义）。
- [x] 新增错误码 `ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE`（`PluginManagerErrors`，manager 层专用；带 beanType 参数）。
- [x] 实现代理生成：`PluginInstanceImpl.getService` 在 ACTIVATED 态经 scope 解析真实 bean 后用生命周期代理包装返回；代理每次方法调用检查 `state == ACTIVATED` 且 `scope != null`，否则抛 `ERR_PLUGIN_INACTIVE`（带 pluginId+instanceKey 参数，复用既有错误码）；`getServices` 对每个候选包装同语义代理。
- [x] 接线验证前置：代理包装必须在 `PluginInstanceImpl.getService/getServices`（instance 入口），不得绕过 scope 解析规则（复用 W3 多候选逻辑）。
- [x] 测试：代理基础行为——`Proxy.isProxyClass` 断言；ACTIVATED 时调用行为与真实 bean 等价；非接口类型抛 `ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE`（复用 `BashTool.class` 具体类）；deactivate 后调用抛 INACTIVE。
- [x] 编译验证：`./mvnw compile -pl :nop-plugin-manager -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `getService(Class)` 返回对象为代理（`Proxy.isProxyClass` 断言），ACTIVATED 时调用行为与真实 bean 等价（本 phase 测试断言）。
- [x] **无静默跳过**：非接口类型明确抛异常（测试断言）；deactivate 后调用代理抛 INACTIVE（测试断言，非返回 null/忽略）。
- [x] W3 具体类调用点已迁移（6 处 `getService(ConfigReaderBean.class)` → `getService(IConfigReader.class)`），`TestPluginInstanceLifecycle` 既有用例回归绿。
- [x] 设计文档 01 §7.2/§7.4 注解已写入（代理机制裁定：仅接口（含 getServices）、按调用重新解析 + 包装时立即解析、equals/hashCode 同规则、scope 返回真实 bean 及 §7.4 措辞澄清）；Decision 记录亦已写入 plan Closure 或 daily log。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - IPlugin.invokeCommand §7.1 兼容规则（VfsPluginDefinition + AbstractPlugin）

Status: completed
Targets: `VfsPluginDefinition.java`、`nop-plugin-support/AbstractPlugin.java`、`nop-plugin-api/PluginApiErrors.java`

- Item Types: `Fix`

- [x] 新增错误码 `ERR_PLUGIN_MULTIPLE_INSTANCES` 于 **`PluginApiErrors`**（api 模块——AbstractPlugin 在 support 模块，不依赖 manager；语义"插件存在多个实例，invokeCommand 须经 IPluginInstance 显式路由"，带 pluginId 参数，可带 instanceKeys 参数便于诊断）。
- [x] `VfsPluginDefinition.invokeCommand/Async` 改造：`getInstances()` 数为 1 → 委托该实例的 `invokeCommand(Async)`（路由到实例子容器命令 bean）；数 >1 → 抛 `ERR_PLUGIN_MULTIPLE_INSTANCES`；数 =0（LOADED 无实例）→ 保留抛 INACTIVE（现有行为，javadoc 同步）。
- [x] `AbstractPlugin.invokeCommand/Async` aware 路径收口（替换 `UnsupportedOperationException` stub，`AbstractPlugin.java:236-253`）：`getInstances()` 数为 1 → 委托该实例；>1 → 抛 `ERR_PLUGIN_MULTIPLE_INSTANCES`；0 → 抛 INACTIVE（现有分支保留）；新错误的 pluginId 参数约定与既有 INACTIVE 一致（`getPluginDefinitionPath()`）。
- [x] 态语义核对：路由委托必须经 `IPluginInstance.invokeCommand`（复用 W3 已落地的 per-instance 分发），不新起一条分发链。
- [x] **路由优先级钉死（有序 if-else，防实现期发明）**：实例数 =0 → 抛 INACTIVE；=1 → 委托该实例（实例级检查决定：DEACTIVATED 时抛 INACTIVE）；**>1 → 抛 `ERR_PLUGIN_MULTIPLE_INSTANCES`（无论实例激活状态——含 2×DEACTIVATED 边界）**；与 `IPlugin.java:33-46` javadoc 的 §7.1 语义一致。
- [x] **AbstractPlugin 测试技术（钉死，避免实现期发现分支不可达）**：AbstractPlugin 不 override `getInstances()`（IPlugin default 返回空列表）——测试需 (a) 测试子类 override `getInstances()` 返回可控列表；(b) stub `IPluginInstance`（`invokeCommand` 记录调用并返回可断言结果）；三态（0/1/多实例）各一用例。该分支在现有生产路径仅 jar 轨可达而 jar 轨无实例机制——**测试可达即契约完备性**（为未来子类预留），不是空壳分支（有明确实现与测试）。
- [x] 测试：三态路由——(a) 单实例 → 命令执行结果返回（VfsPluginDefinition + AbstractPlugin 各一）；(b) 多实例 → 抛 `ERR_PLUGIN_MULTIPLE_INSTANCES`；(c) 0 实例 → 抛 INACTIVE（回归；AbstractPlugin 的 INACTIVE 路径用既有测试覆盖）；(d) **1 个 DEACTIVATED 实例** → 委托到实例级 INACTIVE（§7.1"无 ACTIVATED 实例抛 INACTIVE"的边界用例）。
- [x] 编译验证：`./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **接线验证**：定义级 `plugin.invokeCommand` → 唯一实例 → 实例子容器命令 bean 的执行结果返回（端到端断言，命令执行可观测副作用；VfsPluginDefinition 与 AbstractPlugin 两路径各一）。
- [x] 多实例时抛 `ERR_PLUGIN_MULTIPLE_INSTANCES`、0 实例时抛 INACTIVE（本 phase 测试断言，三态全覆盖）。
- [x] **无静默跳过**：AbstractPlugin 的 `UnsupportedOperationException("(W4)")` stub 已被真实实现替换（grep 确认无残留 marker）；三态均有真实实现与测试，无空分支。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 测试补全 + uber jar 轨评估

Status: completed
Targets: `TestPluginInstanceLifecycle.java`（或新增 `TestGetServiceProxy.java`）、`PluginManagerImpl.java`（如评估裁定需要）

- Item Types: `Proof | Decision`

- [x] 唯一实现分支测试（**接口钉死 `IConfigReader`**——夹具中唯一"无 primary 且单实现"的接口，复用 Phase 1 迁移创建的夹具）：单 bean 无 primary → `getService` 返回该实现（W3 closure follow-up 移交项）。
- [x] 多实例命令隔离测试：同定义 agent-1/agent-2 两实例，命令结果按实例可区分（**需 per-instance 可观测性**——HelloCommand 无状态且事件记录共享：**夹具落地方式钉死** = 在 `agent-instance.plugin.xml` 追加命令 bean（id `nopPluginCommand_<x>`，经 `${agent.only-instance:none}` 读实例配置域、按实例返回不同值）；agent-1/agent-2 各自实例配置该键为不同值，断言命令返回不同）；定义级 `plugin.invokeCommand` 抛多实例异常（Phase 2 已测，此处复核）。
- [x] 代理生命周期矩阵测试：ACTIVATED 调用正常 → deactivate 后调用抛 INACTIVE → 重新 activate 后**同一代理引用**恢复可用（按调用重新解析语义）→ destroy 后抛 INACTIVE；getServices 集合代理重激活后按 bean id 恢复。
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 全绿。
- [x] **Decision：uber jar 轨实例化 successor 项评估**——评估点：(a) 语义完备性（jar 轨 plugin.json 定义无 plugin.xdef/activator 载体，实例化需新增机制）；(b) 本 roadmap 范围（W7 无 jar 轨实例化交付物）；(c) 现行显式失败（`ERR_PLUGIN_INSTANCE_NOT_SUPPORTED`）是否构成 contract gap。裁定结论二选一：维持显式失败并记录（当前支持基线不承诺 jar 轨实例化）或纳入后续计划；裁决写入 plan Closure。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 上表每类测试存在且断言真实行为（非仅"不抛异常"）。
- [x] **端到端验证**：从 `manager.loadPlugin` → `createInstance` → `instance.getService(接口)`（代理）→ 调用 → `deactivate` → 代理调用抛 INACTIVE → `activate` → 同代理恢复可用 → 完整链路有测试覆盖。
- [x] uber jar 轨评估裁决已记录（含 Why 与 successor 归属）。
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 退出码 0。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] getService/getServices 生命周期绑定代理成立：deactivate/destroy 后调用快速失败抛 INACTIVE，重激活恢复（测试证明）。
- [x] IPlugin.invokeCommand §7.1 兼容规则成立（VfsPluginDefinition + AbstractPlugin 两实现点）：1 实例路由 / 多实例明确异常 / 0 实例 INACTIVE（测试证明）。
- [x] AbstractPlugin 的 "(W4)" stub 已收口（无残留 UnsupportedOperationException marker）。
- [x] W3 移交测试缺口（唯一实现分支、多实例命令隔离）已补全。
- [x] uber jar 轨实例化 successor 项已评估并裁决（不静默搁置）。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [x] 零依赖不变式：api 模块无 `io.nop.ioc` / `io.nop.xlang` import（grep 复核）。
- [x] 设计文档 01 §7.2/§7.4 注解已写入（代理机制裁定，含 §7.4 措辞澄清）；docs-for-ai 使用文档 W7 统一同步。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）代理确实被 getService 返回并实际拦截调用（端到端测试断言 INACTIVE 行为来自代理而非调用点检查），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2026-08-14-2007-1-getservice-proxy-command-routing.md --strict` 退出码 0（closure 时执行）。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-plugin-manager,nop-plugin-support --severity high` 退出码 0。
- [x] `./mvnw compile -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [x] `./mvnw test -pl :nop-plugin-api,:nop-plugin-manager,:nop-plugin-support -am -T 1C` 通过。
- [x] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### uber jar 轨实例化路径（评估结论以 Phase 3 裁决为准）

- Classification: `out-of-scope improvement`（Phase 3 裁定：维持显式失败——jar 轨 plugin.json 定义无 plugin.xdef/activator 载体，实例化需新增机制，当前支持基线不承诺）
- Why Not Blocking Closure: 现行 `ERR_PLUGIN_INSTANCE_NOT_SUPPORTED` 显式失败保证 No Silent No-Op；jar 轨发布场景无 plugin.xdef/activator 载体，实例化需新机制（W7 范围外）。
- Successor Required: `no`
- Successor Path: （有发布场景需求时新计划）

## Non-Blocking Follow-ups

- 代理生成可后续抽取为独立工具类（若多处复用）；当前仅 instance 级一处，不提前抽象。
- 零依赖检查固化 CI 脚本（W1 已记录，跨 plan 治理项，不重复承诺）。

## Closure

Status Note: W4 全部三 Phase 落地并全绿——getService/getServices 生命周期绑定代理、IPlugin.invokeCommand §7.1 三态路由（VfsPluginDefinition + AbstractPlugin 两实现点）、W3 移交测试缺口补全、uber jar 轨实例化裁决（维持显式失败）；独立 closure audit 结论 READY_TO_CLOSE（0 Blocker/Major）。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，research-only）
- Audit Session: ses_fff806802ffeDTh1CUYAycwhDG
- Evidence:
  - Phase 1 Exit Criteria 全部 PASS：代理返回（`PluginInstanceImpl.java:130-157` 代理包装 + `ServiceProxy.java` 实现——`ReflectionManager.instance().newProxyInstance` + Handler 按调用重解析 + scope null → INACTIVE 不 NPE）；`testGetServiceReturnsLifecycleProxy`（Proxy.isProxyClass / ACTIVATED 等价 / `BashTool.class` 具体类 getService+getServices 双路径抛 ERR_PLUGIN_SERVICE_PROXY_ONLY_INTERFACE / deactivate → INACTIVE 带 instanceKey）；6 处 `ConfigReaderBean.class` 调用点迁移（grep 0 残留）；设计 01 §7.2/§7.4 注解已写入。
  - Phase 2 Exit Criteria 全部 PASS：`VfsPluginDefinition.java:199-227` 与 `AbstractPlugin.java:239-265` 三态路由（0 → INACTIVE / 1 → 委托该实例 / >1 → ERR_PLUGIN_MULTIPLE_INSTANCES 带 pluginId+instanceKeys）；"(W4)" UOE stub grep 0 残留（support src/main 仅剩 jar 轨 successor 显式失败 marker：`AbstractPlugin.java:170`）；接线验证（stub.invoked 断言 + hello 命令端到端结果断言）。
  - Phase 3 Exit Criteria 全部 PASS：唯一实现分支（`testGetServiceUniqueImplementationBranch`）、多实例命令隔离（`testMultiInstanceCommandIsolation`——IsolatedCommand 按实例配置域返回不同 tag + 定义级 MULTIPLE_INSTANCES 复核）、代理生命周期矩阵（`testServiceProxyLifecycleMatrix`：ACTIVATED → deactivate 同代理 INACTIVE → 重激活同代理恢复 → destroy INACTIVE；集合代理按 bean id 恢复）。
  - **uber jar 轨裁决**（Phase 3 Decision 落定）：(a) 语义完备性——jar 轨 plugin.json 定义无 plugin.xdef/activator 载体，实例化需新增机制；(b) roadmap 范围——W7 无 jar 轨实例化交付物；(c) contract gap 判定——`ERR_PLUGIN_INSTANCE_NOT_SUPPORTED` 显式失败 + `testCreateInstanceOnJarTrackFailsExplicitly` 钉死，不构成 gap。**裁定：维持显式失败**（当前支持基线不承诺 jar 轨实例化），successor 归属 = 有发布场景需求时新计划（本 roadmap 无 successor 项）。
  - Closure Gates 逐条 PASS：proxy 行为 / §7.1 路由 / stub 收口 / W3 缺口补全 / uber jar 裁决 / 无静默降级（Deferred 分类审计 PASS）/ 零依赖 grep 0 命中 / 设计注解 / 独立 audit（本 evidence）/ Anti-Hollow（调用链追踪 + E2E 代理 INACTIVE 断言）/ check-plan-checklist --strict 退出码 0 / scan-hollow 0 findings exit 0 / compile PASS / test PASS（api 6/6 + manager 29/29 + support 10/10，BUILD SUCCESS）/ checkstyle 项目规则集 0 violation exit 0。
  - Anti-Hollow 检查结果：调用链端到端追踪（manager.loadPlugin → VfsPluginDefinition.createInstance → PluginInstanceImpl.activate（scope 落字段）→ getService 代理包装 → Handler.invoke（`instance.getScope()` null → INACTIVE；否则按调用重解析 scope.getService / getServiceBean(beanId)）→ doDeactivate 置空 scope → 代理抛 INACTIVE）连通；INACTIVE 行为来自代理自身（E2E 测试持代理引用跨 deactivate 断言）；`scan-hollow-implementations.mjs` 0 findings。
  - Deferred 项分类检查：uber jar 轨实例化 = `out-of-scope improvement`（Successor: no）——显式失败保证 No Silent No-Op，无 in-scope live defect 被降级。

Follow-up:

- 代理生成未抽独立工具类（当前仅 instance 级一处复用，不提前抽象——plan Non-Blocking Follow-ups 原样保留）；零依赖 CI 脚本为跨 plan 治理项（W1 已记录）。
- 无 remaining plan-owned work。

## Optional Sections

## Risks And Rollback

- JDK/ReflectionManager 代理只支持接口：若消费者传入具体类（非接口）会抛异常——这是显式行为（Phase 1 Decision），使用文档在 W7 明确"getService 仅支持接口类型"。
- 代理状态检查读 volatile state + scope 判空：deactivate 窗口内 `state==ACTIVATED` 但 `scope` 已置空（`PluginInstanceImpl.doDeactivate`，:315-328）——handler 对 null scope 抛 INACTIVE（测试覆盖），不 NPE。
- `plugin.invokeCommand` 多实例路由变更：存量调用方旧行为就是抛异常（0 实例 INACTIVE 或 AbstractPlugin stub 的 UnsupportedOperationException），新规则为明确异常语义，破坏面为零。
- AbstractPlugin aware 路径若存在"仅 start/stop 但 isStateMachineAware() 返回 true"的存量实现（aware 默认 true，`AbstractPlugin.java:75-77`）：invokeCommand 新路由依赖 getInstances()，无实例时 INACTIVE 与旧 stub 行为等价；本 plan 的 support 测试覆盖 aware 路径正反两态。
