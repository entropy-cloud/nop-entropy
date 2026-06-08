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
      Phase 2 无匹配时，回退到 Phase 1 结果（全体候选 skill 以低优先级注入）
```

**决策**：采用递减式多阶段匹配。理由：
1. 声明过滤保证安全性（Agent 只感知它声明了的 Skill）
2. `topPattern` 粗筛性能开销极低
3. `intentSignature` 精确匹配提供最准确的匹配

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

---

## 6. 与现有设计的接合点

### 6.1 与 Hook 引擎的关系

- Skill **提供能力集合**（tool + prompt + resource scope）
- Hook **提供生命周期挂接点**（before/after reasoning、on error 等）
- 一个 Skill 可能通过 Hook 效应器生效，但 Skill 本身不声明 Hook

### 6.2 与 Session 模型的关系

- Skill 装配结果跟随 `AgentExecutionContext` 生命周期，不跨 session 共享
- Session 恢复时重新执行 Skill 匹配（Skill 注册中心可能已更新）

### 6.3 与权限系统的关系

- Skill 声明的 `resourceScope` 在装配时与 Agent 的 `permissions` 叠加
- 叠加规则：`ACTIVE_SCOPE = SKILL_SCOPE ∩ AGENT_PERMISSIONS`
- 运行时超过 `ACTIVE_SCOPE` 的操作应该被拒绝

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
- 匹配只做声明过滤 + `topPattern` 粗筛
- Skill 注册中心：文件系统扫描 `nop-ai-agent/skills/*.skill.yaml`

### 8.2 第二阶段

- Skill DSL 增加 `scenes` 结构层
- 匹配增加 `intentSignature` 精确匹配
- Skill 与 Tool 的依赖检查（装配时验证 tool 是否存在）
- `resourceScope` 与权限系统的叠加检查

### 8.3 第三阶段

- Skill 注册中心支持 Delta 覆盖（与 Nop 平台 Delta 机制一致）
- Skill 版本化
- `intentSignature` 支持语义向量匹配
