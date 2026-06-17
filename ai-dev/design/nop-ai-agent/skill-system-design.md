# Nop AI Skill 系统设计

## 1. 目标

本篇定义 `nop-ai-agent` 的 Skill 系统设计，以 **SSL (Scheduling-Structural-Logical)** 三层表示为外部参照，给出 Skill 在 DSL、引擎、运行时三层的表示模型和匹配机制。

外部参照：[COOLPKU/SSL](https://github.com/COOLPKU/SSL) — 北大 COOLPKU 实验室的 agent skill 表示规范，源自 6,184 条标注的 `SKILL.md` 语料库和两个 Benchmark（SSL-SkillDiscovery、SSL-RiskAssessment）。

---

## 2. 当前状态

### 2.1 Skill 与 Talent 的关系

| 概念 | 定位 | 层级 | 关注点 |
|------|------|------|--------|
| **Skill** | 结构化能力定义（含匹配签名、场景阶段、资源声明） | DSL + 引擎层 | "这个能力是什么、怎么发现、怎么匹配" |
| **ITalent** | 运行时动态准入（基于上下文开关工具集） | Layer 2 执行扩展 | "此时此刻该激活什么工具集" |

Skill 是能力的**结构化描述**，ITalent 是能力的**运行时激活机制**。一个 Skill 可以通过 ITalent 接口实现动态准入。二者互补而非重叠：Skill 定义"能力是什么"，Talent 决定"现在是否启用"。

#### 2.1.1 代码级对比（`ITalent` vs `SkillModel`）

| 维度 | ITalent | Skill（`SkillModel`） |
|------|---------|----------------------|
| **定义形式** | Java SPI 接口（`io.nop.ai.agent.talent.ITalent`，代码实现） | 声明式数据模型（`*.skill.yaml` → POJO `SkillModel`） |
| **匹配/准入机制** | `isSupported(ctx)` 程序化判断（任意运行时逻辑：关键词 / 上下文 / 环境检测） | 声明式匹配：`availableSkills`∩registry → (Phase 2) `topPattern` 粗筛 → (Phase 3) `intentSignature` 精确匹配 |
| **携带内容** | instruction + tool names + `onAttach` 副作用 | `goal` + `dependencies`(tool names) + `resourceScope`（+ 未来 `scenes`） |
| **注册方式** | 无注册中心；`List<ITalent>` 经 `DefaultAgentEngine.setTalents` → Builder 直接注入 executor | `ISkillProvider` 注册中心（`FileSystemSkillProvider` 扫描 `nop-ai-agent/skills/*.skill.yaml`） |
| **失败语义** | 准入不过静默跳过；工具找不到 → WARN 后 skip | `requiredSkills` 未匹配 → fail-fast；`availableSkills` 未匹配 → 不报错 |
| **来源** | Solon AI "Talent" 模式（见 `nop-ai-agent-llm-layer.md` §5） | 自研（参考北大 SSL 三层表示规范） |

#### 2.1.2 装配顺序与优先级语义

`ReActAgentExecutor.execute()` 在执行 setup 阶段（首轮 LLM 调用前）按**固定顺序**一次性咨询三类能力源（`ReActAgentExecutor.java:997-1000`）：

```
1. buildToolDefinitions(agentModel)          // agent.xdef 自身声明的工具
2. consultTalents(ctx, toolDefs)             // ITalent 动态准入
3. consultSkills(ctx, agentModel, toolDefs)  // Skill 声明式匹配
4. consultPromptContributions(ctx)           // IContributionRegistry 的 PROMPT 贡献
```

**优先级语义（三者不同，重要区分）**：

| 能力源 | 是否有显式 `priority` | 排序键 |
|--------|----------------------|--------|
| **Talent** | ❌ 无 | 注册序（`List<ITalent>` 迭代序，即 Builder 传入的不可变 list 顺序） |
| **Skill** | ❌ 无 | 解析序（`SkillResolver` 按 `availableSkills`/`requiredSkills` 声明序） |
| **PROMPT 贡献** | ✅ 有（int 升序） | `contributionRegistry.getContributions(PROMPT)` 返回 priority 升序稳定排序，相同 priority 按注册序（见 `nop-ai-agent-hook-skill-engine.md` §8.2） |

**共同语义（三者一致）**：

- 都是 **additive**：instruction 片段用 `\n\n` 拼接；工具名经 `IToolManager.loadTool()` 解析后 union 到 `toolDefs`（找不到的工具 WARN 后 skip，非静默）。
- 都经 `injectSystemInstruction` 插入为**单条** `ChatSystemMessage`，位置在已有 system 消息之后、历史/用户消息之前，**additive 不替换** agent 原 prompt。
- 都在执行 setup 一次性咨询，不每轮重评估。

**设计裁定**：Talent/Skill 不引入 `priority` 字段。理由：它们是"能力开关"（激活与否是布尔决策），不是"竞争性指令排序"。多个 Talent/Skill 同时激活时，其 instruction 是并列补充而非互斥替代，拼接顺序不影响语义正确性。PROMPT 贡献引入 `priority`，是因为插件场景下 prompt 片段存在"基础提示 vs 增强提示"的层次需求，需要确定性顺序。

### 2.2 已有设计

- `nop-ai-agent-hook-skill-engine.md` — 定义 Skill 引擎层对象（ISkillProvider、SkillResolver、SkillActivationPolicy）
- `agent.xdef` — `availableSkills` / `requiredSkills` 作为 Agent 级声明
- `.opencode/skills/` — 8 个手写 `SKILL.md` 技能描述

### 2.3 当前缺失

| 缺失项 | 后果 |
|--------|------|
| Skill 内部没有结构化表示 | `SKILL.md` 是自由文本，无法被运行时解析 |
| 无匹配/路由机制 | `availableSkills` 仅做集合声明，无条件激活逻辑 |
| 无受控词表 | `description` 字段语义不可被程序解释 |
| 无跨 Agent 共享的 Skill 注册中心 | 每个 Agent 独立声明，无法发现 |
| 无 Skill 版本/兼容性契约 | 升级时无法判断是否破坏调用者 |

---

## 3. SSL 三层表示及其对 Nop 的启示

### 3.1 三层结构

```
Scheduling Layer  ──  skill 级调度信息（能力匹配面）
     │
Structural Layer  ──  场景级执行阶段（阶段结构面）
     │
Logical Layer     ──  原子逻辑步骤（动作/资源证据面）
```

**拒绝了**：扁平化的单层 Skill 表示。理由：匹配、编排、执行三个阶段需要不同粒度的信息。匹配只需要 Scheduling 层，编排需要 Structural 层，执行追溯需要 Logical 层。压成一层会导致匹配阶段的噪声过多、执行阶段的语义不足。

**决策**：Nop Skill 系统采用三层表示，但 DSL 层对外只暴露两层（Scheduling + Structural），Logical 层作为引擎内部推导产物而非人工编写目标。

### 3.2 关键可采纳点

| SSL 概念 | Nop 采纳程度 | 理由 |
|----------|-------------|------|
| `intent_signature` | 采纳，改造 | 作为 Skill 匹配的向量化签名，但扩展为多语义签名（精确匹配 + 语义匹配） |
| `top_pattern` | 采纳 | 顶级行为模式枚举（如 "code_generation", "data_analysis"），用于快速过滤 |
| `scene_type` 七种类型 | 采纳受限子集 | `PREPARE`、`ACT`、`VERIFY` 最稳定；`RECOVER` 由引擎层 Hook 覆盖，不放入 Skill 自身 |
| `act_type` 十二种 | 不直接采纳 | 过于细粒度，Nop 以 Tool 为原子单位，`act_type` 应由 Tool 元数据描述而非 Skill 描述 |
| `resource_scope` 枚举 | 采纳 | 作为 Skill 的安全声明，与 `permissions` 系统对接 |
| `control_flow_features` | 暂不采纳 | 当前 Agent 引擎只有 ReAct，不支持复杂流控声明 |
| 三层跨层引用 | 采纳简化版 | 只保留 Scheduling→Structural 的单向引用，不作为运行时图结构 |

**拒绝了**：直接将 SSL 全量采纳。理由：
1. SSL 的 Logical 层假设一个 NL2JSON 可以精准标注原子操作，但实际 `SKILL.md` 中多数操作描述模糊
2. Nop 以 Tool 为执行原子，Logical 层的 `act_type` 和 Tool 概念重合
3. SSL 标注是为了**检索和风险评估**，Nop Skill 是为了**运行时激活和注入**

---

## 4. Nop Skill DSL 设计

### 4.1 顶层结构

Skill 在 DSL 中是独立的一等公民，不与 Agent 定义耦合：

```yaml
# skill.yaml / skill.xdef
skill:
  # === Scheduling Layer ===
  name: str
  goal: str
  intentSignature: str | str[]  # 多语义签名
  topPattern: enum              # PREPARE | ACT | VERIFY | MANAGE | RETRIEVE | TRANSFORM
  expectedInputs: SchemaRef
  expectedOutputs: SchemaRef
  dependencies: str[]           # 依赖的其他 skill / tool 名称
  tags: csv-set
  resourceScope: enum[]         # MEMORY | LOCAL_FS | CODEBASE | NETWORK | CREDENTIALS

  # === Structural Layer ===
  scenes:
    - id: str
      name: str
      sceneType: enum           # PREPARE | ACT | VERIFY
      goal: str
      input: SchemaRef
      output: SchemaRef
      entryConditions: str
      exitConditions: str
      nextSceneRules: str       # 场景间转场规则
      tools: str[]              # 此场景涉及的 tool 名称集合
```

**语义补充**：

- `intentSignature` — 用于匹配的签名。可以是精确字符串（如 skill 名），也可以是语义向量 ID。支持多条签名表示同一 skill 的不同使用意图。
- `topPattern` — 顶级行为模式，用于 Agent 在 `availableSkills` 集合中做第一轮粗筛。
- `resourceScope` — Skill 声明的资源边界，运行时与 Agent 的权限策略叠加判断。不叠加的 scope 不生效。
- `scenes` — 可选。当 Skill 有明确的阶段结构时使用。简单 Skill 可以只有 Scheduling 层。
- `tools` — 工具名集合，而非完整工具定义。工具定义仍由 `tool.xdef` 管理。

### 4.2 与 `agent.xdef` 的关系

```
Agent DSL:
  availableSkills: [skill-a, skill-b]   ← 引用 skill 名称，不是内嵌定义
  requiredSkills: [skill-c]             ← 同上

Skill DSL (独立):
  nop-ai-agent/skills/skill-a.skill.yaml  ← .skill.yaml 扩展名
```

**决策**：Skill 是独立文件，不是 Agent 内嵌节点。理由：
1. Skill 可以被多个 Agent 复用
2. Skill 可以独立版本化和测试
3. Agent DSL 保持简洁，只做引用声明

**拒绝了**：将 Skill 定义嵌入 `agent.xdef`。理由：会导致跨 Agent 共享困难、DSL 膨胀。

### 4.3 与现有 `.opencode/skills/` 的关系

- `.opencode/skills/` 维持不变，作为 AI 编写 `SKILL.md` 的源目录
- 工具（validate/build）读取 `SKILL.md`，输出 `.skill.yaml` 到 `nop-ai-agent/skills/`
- 人工运行时注册仍然编辑 `.skill.yaml` 作为 source of truth

---

## 5. Skill 引擎设计

### 5.1 职责

Skill 引擎负责：

1. **发现** — 从注册中心加载所有可用 Skill
2. **匹配** — 根据当前请求的 `intentSignature` 和 Agent 的 `availableSkills` / `requiredSkills` 筛选生效的 Skill
3. **装配** — 将生效 Skill 的 Scheduling 信息注入 Agent 的 prompt / tools / hooks
4. **监督** — 运行时跟踪 Skill 声明的 `resourceScope` 是否被违反

### 5.2 匹配机制

匹配分三阶段：

```
Phase 1 - 声明过滤:
  Agent.availableSkills ∩ GlobalSkillRegistry → candidate set

Phase 2 - topPattern 粗筛:
  request.topPattern matches candidate.topPattern → narrowed set

Phase 3 - intentSignature 精确匹配:
  request.intentSignature == candidate.intentSignature → activated set

回退: Phase 3 无匹配时，回退到 Phase 2 结果
      Phase 2 无匹配时，不注入任何 Skill（空集）
```

**决策**：采用递减式多阶段匹配。理由：
1. 声明过滤保证安全性（Agent 只感知它声明了的 Skill）
2. `topPattern` 粗筛性能开销极低
3. `intentSignature` 精确匹配提供最准确的匹配
4. 回退到空集而非全体——符合 00-vision.md 约束 4（不引入任何外部假定的最简行为）。匹配失败时注入全体 Skill 会违反"最小运行时"原则，导致工具集非预期膨胀

**拒绝了**：单一向量相似度匹配。理由：在无可靠 Embedding 模型时，精确匹配优于语义模糊匹配；精确匹配失败时可以优雅回退。

### 5.3 Skill 激活策略

| 声明方式 | 匹配结果 | 行为 |
|----------|----------|------|
| `requiredSkills` | 未找到 | 启动错误，Agent 不执行 |
| `requiredSkills` | 匹配成功 | 强制激活，注入 prompt/tools/hooks |
| `availableSkills` | 匹配成功 | 激活，注入 |
| `availableSkills` | 未匹配 | 不激活，不报错 |

### 5.4 装配结果

Skill 装配结果包括三部分注入到 Agent 执行上下文：

1. **Prompt 注入** — `skill_goal` 和场景描述追加到系统 prompt
2. **Tool 注册** — Skill 依赖的 tool 注册到 Agent 的 tool 集合中（需要权限叠加检查）
3. **ResourceScope 声明** — 与 Agent 的 `permissions` 叠加，取交集

### 5.5 Skill 策展 / Curation

Skill 策展（curation）是技能质量评估层——一个**建议性、非变更性**的分析工具。它读取已注册的 Skill 定义，评估其清晰度、完整性、覆盖范围，并产出策展建议（质量评级、改进推荐），但**绝不修改** Skill 定义。

**与 ISkillProvider 的关系**：策展器只消费 skill 注册表集合（`Collection<SkillModel>`），不直接接触 `ISkillProvider`。从 provider 获取 skills 是引擎的职责——引擎调用 provider，将结果集合传给策展器。

**质量评级分类法（三级）**：

| 评级 | 含义 |
|------|------|
| `WELL_DEFINED` | 目标、签名、依赖、资源范围清晰完整，且与其他 skill 不冗余 |
| `NEEDS_IMPROVEMENT` | 可用但有缺陷：目标模糊、缺少依赖、签名不明确、资源范围不充分 |
| `REDUNDANT` | 与注册表中其他 skill 大量重叠，建议合并或移除 |

**调用模型**：策展器是**按需分析工具**，不是 ReAct 循环内的组件。它注册在 `DefaultAgentEngine` 上（setter，默认 `NoOpSkillCurator`），按需调用，不在 `ReActAgentExecutor.execute()` 中被调用。策展是元层关注点（skill 质量评估），不是每次执行的关注点（skill 激活）。

**成功/失败标记语义**：策展结果携带明确的成功/失败标记。"成功且零评估"（空注册表的合法结果）与"策展失败"（LLM 错误、解析失败）必须可区分。两者都是显式的，绝非吞掉异常的产物。

**LLM 策展的输出格式契约**（`LLMCurator`）：策展器与其默认系统 prompt 必须约定单一的结构化输出格式。LLM 响应必须按 skill 携带名称引用和评估字段（质量评级、推荐、理由），且可被机器解析为评估对象。具体格式选择（JSON，per-skill 对象数组）和解析契约（期望字段、缺失/格式错误条目的错误处理）是实现决策，记录在此处，以确保解析器与默认 prompt 一致。解析器从 LLM 响应中提取 JSON（处理 markdown 代码围栏），使用 `JsonTool` 解析；缺失或格式错误的条目被跳过（不导致整批失败，除非整个响应不可解析）。当整个批次响应不可解析时，该批次产出失败标记。批处理部分失败语义：成功的批次贡献其评估，失败的批次贡献失败标记；如果任何批次失败，合并结果的总体标记为"失败"，但仍保留成功批次的评估。

**拒绝了**：

- **变更性策展器**（自动应用推荐）——回写语义需要独立的安全设计，且与静态加载设计（§7.3）冲突。策展器只推荐，不修改。
- **基于执行轨迹的策展**——需要尚不存在的 session-history/telemetry 基础设施。定义质量策展（评估 skill 定义的清晰度/完整性/覆盖）是基础能力，无需执行轨迹即可交付。
- **ReAct 循环后生命周期钩子调用**——会使每次执行都承担策展开销，混淆两个不同关注点。
- **定期/后台策展调度**——策展器按需调用；调度是平台关注点（Actor Runtime, L4-8）。

---

## 6. 与现有设计的接合点

### 6.1 与 Hook 引擎的关系

- Skill **提供能力集合**（tool + prompt + resource scope）
- Hook **提供生命周期挂接点**（before/after reasoning、on error 等）
- 一个 Skill 可能通过 Hook 效应器生效。**Phase-1 选择**：Skill 本身不声明 Hook（职责分层）。注意这是**设计选择，不是技术限制**——Skill 声明式携带 Hook 在技术上完全可行，见 §6.4 辨析。

### 6.2 与 Session 模型的关系

- Skill 装配结果跟随 `AgentExecutionContext` 生命周期，不跨 session 共享
- Session 恢复时重新执行 Skill 匹配（Skill 注册中心可能已更新）

### 6.3 与权限系统的关系

- Skill 声明的 `resourceScope` 在装配时与 Agent 的 `permissions` 叠加
- 叠加规则：`ACTIVE_SCOPE = SKILL_SCOPE ∩ AGENT_PERMISSIONS`
- 运行时超过 `ACTIVE_SCOPE` 的操作应该被拒绝

### 6.4 Skill 携带 Hook 的声明式可行性（设计辨析）

> **常见疑问**：「给 Skill 加 Hook 能力，不就动态注入了吗？Skill 会降级为命令式注入器吗？」

**结论：用 xpl 表达的 Hook 不会降级。**「降级为命令式注入器」只发生在塞入 Java `IAgentLifecycleHook` 实例时，不发生在用 xpl 声明 Hook body 时。

#### 6.4.1 xpl 的声明式—命令式二象性

xpl 的 `xpl-fn:(args)=>T` domain type（`XplStdDomainHandlers.java`）在**模型加载期**把 XML body 编译成一个 `IEvalFunction` Java 对象，作为 POJO 字段持有。容器仍是声明式数据模型（可序列化、可 Delta 合并、可 xdef 校验），payload 是一个函数值。命令式逻辑（if/for/side-effect/环境探测）以声明式 XML 形态暴露。

**这是 Nop 的主导模式，不是例外**。已落地的 1:1 先例：

| DSL | 声明式节点 | xpl payload | 证据 |
|-----|-----------|-------------|------|
| `agent.xdef` | `<hooks><on event="...">` | `xpl-fn:(event,agentRt)=>void` → `AgentHookModel.body: IEvalFunction` | `agent.xdef:43`；`DefaultHookRegistry` 经 `EvalFunctionHookAdapter` 适配 |
| `biz/state-machine.xdef` | `<state>` | `<on-entry>xpl</on-entry>` / `<on-exit>` / `<handle-error>xpl-fn:>` | `state-machine.xdef:73-81` |
| `task/batch.xdef` | step | 12 种 listener（`onTaskBegin`/`onChunkEnd`...）全 `xpl-fn:` | `batch.xdef:55-70` |
| `task/task.xdef` | step | `<catch>`/`<finally>`/`<when>`/`<while>` | `task.xdef:84-92` |

因此若让 Skill 携带 Hook，Skill POJO 只会多出 `IEvalFunction`-typed 字段（和 `AgentHookModel` 完全一样），仍是声明式模型。

#### 6.4.2 命令式 vs 声明式的真正边界

| 形态 | 机制 | Skill 是否保持声明式 |
|------|------|---------------------|
| **A. xpl Hook body** | `xpl-fn:` 编译为 `IEvalFunction` POJO 字段 | ✅ 保持声明式（agent.xdef 先例） |
| **B. Java SPI 注入** | Skill 内塞 `IAgentLifecycleHook` 实例 | ❌ 降级为命令式注入器 |
| **C. runtime 贡献** | 经 `IContributionRegistry` 注册 `ContributionType.HOOK` | ❌ 命令式（payload 是 Java `HookPayload`） |

当前 phase-1 的「Skill 不声明 Hook」是**规避形态 B/C 的降级风险**。但形态 A 不会降级——它和 agent.xdef 用的是同一套机制。`IContributionRegistry`（形态 C）已作为 runtime 增量装配通道独立存在（`nop-ai-agent-hook-skill-engine.md` §8），与 Skill 声明式携带 Hook（形态 A）是两个正交关注点。

#### 6.4.3 行业先例（源码级调研，非二手分析）

> 详细源码级数据（6 框架的 frontmatter 解析器 / hook 注册接口 / plugin manifest schema + file:line 证据 + 三个洞见 + 对设计启示）见 `ai-dev/analysis/agent-survey/2026-06-17-skill-hook-relationship-source-survey.md`。本节为摘要。

直接读源码调研 6 个代表性框架（`~/ai/{opencode,claude-code,hermes-agent,pi,codex,soloncode}`）+ 14+ 个框架的二手分析（`ai-dev/analysis/agent-survey/`）。**核心发现高度一致，且源码证据更强**：

**结论：全部 6 个框架的 skill/command/agent/talent 都不能声明自己的 hook**（soloncode 的 Talent 仅有一次性 `onAttach` 初始化回调，非完整生命周期 hook）。Hook 恒居于更高层（plugin/extension/harness/global-config），方向永远是 hook→skill（hook 触发/加载 skill），从不 skill→hook。

| 框架 | skill 能声明 hook？ | hook 形态 | 源码证据 |
|------|---------------------|-----------|----------|
| **opencode** | ❌ | **命令式 TS**（plugin 返回 `Hooks`，21 V1 hook） | skill frontmatter 仅 `name/description/slash`（`packages/core/src/skill.ts:60-64`）；plugin 是 TS 模块（`packages/plugin/src/index.ts:74`） |
| **claude-code**（claw） | ❌ | **声明式 JSON**（settings.json，3 事件 shell 命令） | skill 解析器只取 `name`/`description`，其余静默丢弃（`commands/src/lib.rs:4217-4246`）；**Plugin manifest 可携带 hooks**（`plugins/src/lib.rs:116-132`）但全局合并，不 scope 到 skill |
| **hermes-agent** | ❌ | **命令式 Python**（`ctx.register_hook`，19 hook） | skill 校验器只要求 `name`/`description`（`tools/skill_manager_tool.py:217-253`）；**plugin.yaml 的 `hooks:` 块纯装饰——加载器从不读**（`plugins.py:1283`）；`provides_hooks` 解析但零消费者 |
| **pi-agent** | ❌ | **命令式**（loop config / harness.on / extension pi.on） | `Skill` 仅 5 字段（`packages/agent/src/harness/types.ts:46-57`）；**Tool 无 per-tool hook**（`packages/agent/src/types.ts:361-384`） |
| **codex** | ❌ | **双系统**：声明式 JSON（ClaudeHooks，10 事件 Command/Prompt/Agent）+ 命令式 Rust trait | skill frontmatter 仅 `name`/`description`/`metadata.short-description`（`core-skills/src/loader.rs:39-53`），grep hook 零匹配；**Plugin manifest 平级携带 `skills`∥`hooks`**（`core-plugins/src/manifest.rs:47-59`，支持 Inline hook 定义），但流入不相交子系统 |
| **soloncode** | ❌（skill 是纯 MD） | **命令式 Java**（Builder.add）；**声明式 hooks 字段预留但未实现** | `Talent` 仅 `onAttach` 一次性回调（`Talent.java:78`）；**38 Talent 与 5 ReActInterceptor 实现零交集**；`AgentDefinition.java:211-212` 显式 `// Hooks 配置（暂不解析，保留字段）`；**HarnessExtension** 平级暴露 `defaultTalentAdd`∥`defaultToolAdd`∥`defaultInterceptorAdd`（`HarnessExtension.java:32`） |

**三个关键洞见（源码揭示，二手分析未点透）：**

1. **「声明式 hook 配置」存在，但恒在更高层，不嵌套进 skill**。claude-code/codex 的 hook 是声明式 JSON（shell 命令），hermes 的 plugin.yaml 有 `hooks:` 块——但**前者在 settings.json/plugin manifest 层，后者是装饰性**（加载器忽略）。**没有任何框架把 hook 声明放进 skill 数据模型。**

2. **「bundle 携带 hook」的成熟形态是平级 contribution**。codex 的 `PluginManifest`（`skills`∥`hooks` 平级 + Inline hook，第三个独立例证）、claude-code 的 `PluginManifest`（`hooks`∥`commands`∥`tools` 平级）、PilotDeck 的 plugin manifest（`hooks?`∥`skills?` 平级 + 5 种 Hook 执行器）、soloncode 的 `HarnessExtension`（三平级方法）都是 bundle 层携带 hook。**Nop 的 `IContributionRegistry`（HOOK 是 7 种 contribution 之一）已是此模式**（`nop-ai-agent-hook-skill-engine.md` §8）。

3. **源码级「静默丢弃 / 预留未实现」是刻意 schema 收敛**：claude-code/codex/hermes/opencode/pi 的 skill frontmatter 解析器都**显式只读有限键，忽略其余**；**soloncode 更进一步——`AgentDefinition.java:211` 显式预留 `hooks` 字段但标注「暂不解析」**，证明这是设计选择而非遗漏。

> **对设计的启示**：源码证据**不否定** §6.4.1/§6.4.2 的技术结论（xpl 让 skill 声明式携带 hook 在 Nop 内可行，form A 不降级）。它证明的是：**业界一致选择「skill=纯数据 + hook=更高层平级 contribution」的分层**，而非 skill 内嵌 hook。Nop 若走 skill 内嵌 xpl hook，是无先例的原创设计——可行但有举证责任（需证明比平级 contribution 模式更好）。

#### 6.4.4 对 Talent 定位的影响

xpl 抹平了「声明式 vs 命令式」的对立——任何运行时程序化逻辑（包括 Talent 的 `isSupported(ctx)` 任意准入判断、`getInstruction`/`getTools` 动态注入）都能以 xpl 声明式形态放进 Skill（`xpl-predicate` 表达准入、`xpl-fn`/`xpl` 表达动态产出）。

这意味着 long-term：**Skill（声明式容器 + xpl 可执行 payload）+ 增强的匹配引擎可以覆盖 Talent 的大部分用例**。Talent 的独立价值收敛为「纯 Java SPI 的轻量程序化准入外壳」——适合不想写 xpl、直接用代码做环境探测的场景（如 LSP 是否连着、数据库连接是否存在）。

Phase-1 保留 Talent 作为已交付的 Java SPI 扩展点；phase-2+ 评估「Skill 声明式携带 xpl hook + xpl 准入表达式」作为更通用的声明式路径，Talent 退为其程序化快捷外壳。

---

## 7. 拒绝了什么

### 7.1 拒绝：全量采用 SSL 三层标注

SSL 的 Logical 层（`logic_step`）不适合 Nop：
- Nop 以 Tool 为原子执行单位，Logical 层的 `act_type`（12 种）与 Tool 概念高度重合
- Logical 层的跨层图结构在运行时维护成本高
- SSL 的标注目标是**检索和风险评估**，Nop 的目标是**运行时激活**

**替代方案**：Nop 的 Logical 层由 Tool 元数据和执行日志替代，不单独建模。

### 7.2 拒绝：Skill 内嵌在 agent.xdef 中

理由见 §4.2。

### 7.3 拒绝：运行时动态注册 Skill

- 当前阶段 Skill 注册中心在 Agent 引擎启动时静态加载
- 运行时动态注册会增加安全审查和版本冲突风险
- 动态注册作为 Phase 2 选项

### 7.4 拒绝：Skill 内包含完整的 tool 定义

- Skill 只声明 tool 名称引用，不携带 tool 的 schema/description
- Tool 定义统一由 `tool.xdef` 管理
- 避免同一 tool 在多个 Skill 中存在不一致定义

---

## 8. 发布约束

### 8.1 第一阶段

- Skill DSL 只实现 Scheduling 层（无 `scenes` 结构）
- Agent DSL 支持 `availableSkills` / `requiredSkills` 引用
- 匹配只做声明过滤（`requiredSkills` fail-fast + `availableSkills` 激活）；`topPattern` 粗筛推迟到第二阶段，因为请求侧 `topPattern` 机制尚不存在
- Skill 注册中心：文件系统扫描 `nop-ai-agent/skills/*.skill.yaml`

### 8.2 第二阶段

- Skill DSL 增加 `scenes` 结构层
- 匹配增加 `intentSignature` 精确匹配
- Skill 与 Tool 的依赖检查（装配时验证 tool 是否存在）
- `resourceScope` 与权限系统的叠加检查

### 8.3 第三阶段评估项

- **Skill 声明式携带 xpl Hook**（§6.4 形态 A）：Skill 增 `<hooks>` 节点 + `xpl-fn:(event,skillRt)=>void` body，引擎在装配期把 Skill 的 Hook 经 `IHookRegistry.register` 注册（复用 agent.xdef 的 `EvalFunctionHookAdapter` 桥）。这是**声明式增强**，非降级。评估点：与 `IContributionRegistry` 平级 contribution 模式（§6.4.3 PilotDeck 先例）的取舍。
- **Skill 用 xpl 表达动态准入/产出**：`xpl-predicate` 表达 `isSupported`、`xpl-fn`/`xpl` 表达动态 instruction/tools，收敛 Talent 用例（§6.4.4）。
- Skill 注册中心支持 Delta 覆盖（与 Nop 平台 Delta 机制一致）
- Skill 版本化
- `intentSignature` 支持语义向量匹配
