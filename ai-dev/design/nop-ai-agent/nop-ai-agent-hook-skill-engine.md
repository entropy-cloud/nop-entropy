# Nop AI Hook And Skill Engine

## 1. 目标

本篇定义 Hook 与 Skill 在 Java 引擎层中的组织方式。

## 2. Hook 引擎

Hook 引擎负责：

- 加载 `agent.xdef` 中的 Hook 配置
- 按规则筛选、排序和触发 Hook
- 处理 before/after/on_error 的失败传播语义

推荐对象：

- `HookRegistry`
- `HookMatcher`
- `HookInvoker`
- `HookExecutionPolicy`

**排序语义**：同一 Hook 点上的多个 Hook 按 `priority` 升序执行（数值小的先执行）。无 `priority` 声明时默认 `0`。相同 `priority` 按注册序执行。

**超时策略**：每个 Hook 执行有 `hookTimeoutSeconds` 限制（默认 30s），超时后记录 `HookTimeout` 警告并继续（不中断 ReAct 循环）。`before_*` 和 `after_*` Hook 超时不阻断执行；`on_error` Hook 超时使用 fallback 默认错误处理。

**失败传播**：`before_*` Hook 失败 → 阻止当前操作（返回 Hook veto）；`after_*` Hook 失败 → 记录但不影响已完成操作；`on_error` Hook 失败 → 使用引擎默认错误处理。

## 3. Skill 引擎

Skill 引擎负责：

- 发现可用 Skill
- 判断当前请求哪些 Skill 生效
- 汇总 instruction、tools、hooks

推荐对象：

- `ISkillProvider`
- `SkillResolver`
- `SkillActivationPolicy`
- `SkillAssemblyResult`

## 4. Hook 与 Skill 的关系

- Skill 负责注入能力集合
- Hook 负责挂接生命周期点

很多 Skill 最终会通过 Hook 生效，但二者仍应分层实现。

## 5. 与 ReAct 引擎的关系

ReAct 引擎负责调用：

- Hook 引擎
- Skill 装配结果

但 ReAct 引擎本身不负责：

- 发现 Skill
- 解析 Skill 激活条件
- 管理 Hook 优先级规则

## 5a. Hook 类型扩展（MiMoCode 吸收）

除了 §4 定义的 before/after/on_error 事件 Hook，新增两个**允许 ReAct 重入**的 Hook 点：

| Hook 点 | 触发时机 | 允许重入 | 典型用途 |
|---------|---------|---------|---------|
| `before_tool_result_processed` | 工具执行完成、结果写入消息历史之前 | ✅ 可返回 `ReenterResult` | 工具结果修复、结果拦截重试 |
| `after_tool_result_processed` | 工具结果已写入消息历史、下一轮推理之前 | ✅ 可返回 `ReenterResult` | 结果评审、触发子任务 |

**重入语义**：
- Hook 返回 `ReenterResult{message}` → 引擎注入该消息，跳过当前轮剩余步骤，进入下一轮 ReAct
- Hook 返回 `PassResult` → 继续正常流程
- Hook 超时 → 默认 Pass
- 重入次数限制：单个 Hook 点连续重入 N 次（默认 3 次）→ 强制 Pass，防止死循环

**设计理由**：MiMoCode 的 PreStop/PostStop ReAct 重入钩子被证明能有效处理工具结果异常（JSON 截断修复、结果拦截重试）和后处理（结果评审、知识提取）。Nop 现有 Hook 只有事件通知，没有"改变执行流"的能力。两个新 Hook 点给引擎增加了"执行流修正"能力，而不需要拆解 ReAct 循环本身。

## 8. 插件贡献注册表（IContributionRegistry）

引擎通过 `IContributionRegistry` 让插件在运行时注册 7 种类型的贡献（plan 217 / L4-6）。注册表是独立原语，不替代 `agent.xdef` 的静态声明（`<tools>` / `<hooks>` / `<permissions>` / `<prompt>`），而是在静态声明之外提供一条运行时增量装配的通道。

### 8.1 贡献类型 → 既有扩展点映射

| `ContributionType` | 装配期解析目标 | 本版是否解析 |
|--------------------|---------------|-------------|
| `HOOK` | `IHookRegistry.register(point, hook)` | ✅ 解析（L2-12 直接依赖） |
| `PROMPT` | `injectSystemInstruction` 注入 system prompt（additive） | ✅ 解析 |
| `TOOL` | `IToolManager` 工具集 | ❌ successor（独立配置面 + executor 依赖） |
| `PERMISSION_RULE` | 权限 checker 合并 | ❌ successor（独立配置面） |
| `ROUTER` | `IModelRouter` 选择 | ❌ successor（单实例配置，非列表式） |
| `COMMAND` | 命令面（无既有运行时 surface） | ❌ successor（需新建 surface） |
| `MCP_SERVER` | MCP 服务面（无既有运行时 surface） | ❌ successor（需新建 surface） |

未解析的 5 类在注册表中可注册、可按 type typed 查询（消费者经 `getContributions(type)` 取出后自行消费），仅缺引擎自动装配——每类自动装配是独立 successor（独立 plan，因各自有独立配置面或需要新建运行时 surface）。

### 8.2 身份与来源作用域

- 每个 `Contribution` 带 `source`（贡献来源标识，如插件 id）。
- `type + id` 是跨来源唯一键：不同 source 注册相同 `type + id` → fail-fast `NopAiAgentException`；同 source 重注册相同 `type + id` → 替换（idempotent re-register）。
- `unregisterSource(source)` 一次性卸载某来源的全部贡献，支持插件干净卸载/重装。
- `priority`（int，升序，默认 0）是 per-type 内排序键——与 Hook 引擎的 priority 语义一致（§2：数值小的先执行，相同 priority 按注册序）。

### 8.3 装配期解析时机

贡献在 `ReActAgentExecutor` 执行 setup（首次 LLM 调用前，与 `consultSkills` 同阶段）解析一次，不在运行中 session 热生效：

- **HOOK**：`DefaultAgentEngine.resolveExecutor` 在装配 `IHookRegistry` 后（先 `DefaultHookRegistry.fromAgentModel(model)`，再注册 HOOK 贡献），把 `ContributionType.HOOK` 贡献的 payload（`HookPayload` = `AgentLifecyclePoint` + `IAgentLifecycleHook`）注册到 Hook 注册表。
- **PROMPT**：`ReActAgentExecutor.execute()` setup 阶段，把 `ContributionType.PROMPT` 贡献的 String 片段按 priority 升序拼接，经 `injectSystemInstruction` 注入 system prompt 上下文（additive，与 skill instruction 同模式）。

注册表对运行中 session 的后续变更不生效（符合模块既有装配期模型）。这使注册表与 session 生命周期解耦，避免运行时一致性复杂度。运行时热加载是 L4-8 Actor Runtime 的语义裁定。

### 8.4 Shipped 默认与三件套惯例

遵循模块的「接口 + NoOp/pass-through 默认 + 可选功能实现」三件套惯例（与 `IHookRegistry`/`NoOpHookRegistry`、`IMailbox`/`NoOpMailbox`、`IUsageRecorder`/`NoOpUsageRecorder` 同构）：

- **`IContributionRegistry`**：接口契约（`register` / `unregisterSource` / 两个 typed `getContributions` / `getSources`）。
- **`NoOpContributionRegistry`**：shipped singleton。`register` 显式返回 `false`（非静默成功），查询返回空集，`getSources` 返回空。引擎默认使用它，零行为回归。
- **`InMemoryContributionRegistry`**：功能实现。`ConcurrentHashMap` per-type 存储，复合操作（重复检测/替换/卸载）同步保护，查询按 priority 升序稳定排序。集成商经 `engine.setContributionRegistry(new InMemoryContributionRegistry())` 并 `register(...)` 后启用。

贡献缺失不构成安全风险（贡献是增量能力，NoOp 下系统正常），因此不扩展 `warnIfInsecureDefaults`（与 `IMailbox` / `IUsageRecorder` 裁定一致）。

### 8.5 HOOK 贡献 payload 契约

`ContributionType.HOOK` 的 payload 须是 `HookPayload`（携带目标 `AgentLifecyclePoint` + `IAgentLifecycleHook` 实例）。装配期解析跳过 payload 类型不匹配的 HOOK 贡献并 log WARN（fail-visible，非静默忽略），但单个坏贡献不阻断整批解析——其余 HOOK 贡献照常注册。`Contribution.forHook(...)` 工厂方法确保 payload 包装正确。

## 6. 内部 Agent 化

### 6.1 概念

部分引擎内部能力可以用 Agent 来实现——接口的提供者是引擎，接口的实现者可以是硬编码逻辑，也可以是一个 Agent。

详见 `nop-ai-agent-context-model.md` §6。

### 6.2 与 Hook/Skill 的关系

内部 Agent 化的能力可以通过 Hook 挂接到引擎生命周期上：

- 上下文压缩 → 挂接在 `POST_REASONING` 或独立触发
- 错误修复 → 挂接在 `ON_ERROR`
- 结果评审 → 挂接在 `POST_CALL`

Hook 负责触发时机，内部 Agent 负责具体逻辑。

### 6.3 阶段归属

Phase 1：所有内部能力用硬编码逻辑实现。
Phase 2：逐步将硬编码逻辑替换为 Agent 实现，接口不变。

## 7. 本篇结论

Hook 和 Skill 都属于 Java 引擎扩展层。
