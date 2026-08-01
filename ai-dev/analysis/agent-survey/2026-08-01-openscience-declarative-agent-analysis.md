# OpenScience 声明式 Agent 与权限 Glob 规则集深度分析 & Nop AI Agent DSL/权限

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/openscience`（TS/Bun，科研 AI 工作台，~1181 文件）vs `nop-ai-agent`（AgentModel DSL + security PermissionMatrix）
> Conclusion:

## 一、总览

**OpenScience** 是开源科研 AI 工作台（文献→代码→实验→撰写）。核心：**声明式 agent 定义**（zod schema）、**工具注册表**（.ts 参数+execute + .txt 描述配对）、**glob 级 per-tool 权限规则集**（allow/ask/deny）、**provenance 追踪**。

| 维度 | openscience | nop-ai-agent |
|------|-------------|--------------|
| Agent 定义 | zod schema（mode/permission/model 绑定） | AgentModel（XDEF DSL） |
| 权限 | per-tool glob allow/ask/deny 可合并 | PermissionMatrix |
| 工具描述 | .ts 参数 + .txt 描述配对 | DSL 声明 |
| subagent | primary vs subagent 模式委派 | team 包（multi-agent） |
| 生态 | 42+ 科研数据库连接器 + 290+ skill | — |

## 二、核心机制详解

### 2.1 声明式 agent（`backend/cli/src/agent/agent.ts:26`）
- zod schema：`name` / `mode`（**subagent | primary | all**）/ `permission ruleset` / `model` / `steps`。
- 支持 **`generate()`** 用 LLM 动态生成 agent——运行时按需创建。
- 内置 research/biology/physics/ml agent + critique/literature-review 子 agent + plan 模式。

### 2.2 工具注册表（`tool/registry.ts`）
- tool = `.ts`（参数 schema + execute）+ 同名 `.txt`（描述给 LLM）配对。
- 插件可加载自定义工具。
- biology/science 工具集。

### 2.3 权限规则集（`permission/`）
- **per-tool glob 匹配**：`allow` / `ask`（需确认）/ `deny`。
- 可合并（如 `*.env` → ask）。
- glob 级粒度比简单 allow/deny 更灵活。

### 2.4 provenance 追踪 + 科研生态
- provenance：追踪每个事实/结果的来源。
- 42+ 科研数据库连接器。
- kernel 管理、MCP/LSP/worktree、compute（Modal/Tinker）。

## 三、对 nop-ai-agent 的借鉴要点

1. **声明式 agent schema（mode/permission/model 绑定）**（最高价值，天然契合 DSL-first）——nop AgentModel 可参考其 mode（subagent/primary/all）与 permission ruleset 的绑定方式，让权限成为 agent 定义的一等字段（而非外挂 security 链）。
2. **glob 级 per-tool 权限规则集**（高价值）——nop PermissionMatrix 可引入 glob 匹配（`*.env`→ask/deny）+ 规则合并语义。**`ask`（需确认）三态**比 allow/deny 二态更实用（对应 AGT 审批流 `2026-08-01-agent-governance-toolkit-analysis.md`）。
3. **tool = 参数schema+描述(.txt)+execute 模式**（中价值）——工具描述与实现分离（.txt 描述给 LLM，.ts 执行），对应 nop 的工具元数据设计。
4. **provenance 追踪**（中价值）——与 trustgraph 的来源元数据（`2026-08-01-trustgraph-context-graph-analysis.md`，nop 已有 ContentOrigin）呼应。
5. **`generate()` 动态生成 agent**（中价值）——运行时用 LLM 按需创建 agent，对应 nop team 包的动态 agent 配置。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **permission glob 拒绝 → 重试路径**：`*.env→ask` 等权限规则——被拒后 agent 走 ask 分支（**权限级 replan**）。
- **subagent 委派重试**：primary 委派 subagent 失败 → 可换 subagent 重试（`mode: subagent|primary`）。
- **generate() 动态生成 agent**：运行时按需生成——**agent 级 replan**（失败时生成新 agent 替代）。
- **对 nop 的启示**：权限 ask 三态（allow/ask/deny）是 nop PermissionMatrix 的 replan 路径参考；动态生成 agent 对应 nop team 包的 auto-spawn。

## 四、结论

openscience 的声明式 agent + glob 权限规则集最贴"DSL-first"理念，是 nop AgentModel/PermissionMatrix 的直接参考。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D8**（声明式 agent zod schema+permission ruleset）、**D6**（glob 权限 allow/ask/deny）、**D5**（subagent/primary 模式）、**D12**（generate() 动态生成 agent replan）。缺失/薄弱：D9。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **agent 定义**：nop `AgentModel`（XDEF DSL）比其 zod schema 更成熟（类型化 + Delta + 校验）。
- **安全**：nop PermissionMatrix + security 6 层比其 glob 权限更系统化。
- **team**：nop team 包比其 subagent/primary 模式更完整。

**必要参考的增量（以超越方式吸收）**：
- **mode 绑定**（subagent/primary/all 声明式绑定）：nop AgentModel 可增加 agent 角色模式声明——增强（与 nop team 包呼应）。
- **glob 级权限**（per-tool glob allow/ask/deny 可合并）：nop PermissionMatrix 可增加 glob 匹配 + ask 三态——真正增量。

**总评**：nop-ai-agent **全面超越** openscience（AgentModel/安全/team 更成熟）；mode 绑定 + glob 权限两个增量吸收。

## References
- `~/ai/openscience/backend/cli/src/agent/agent.ts:26`、`tool/registry.ts`、`permission/`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-dsl.md`、`nop-ai-agent-security-and-permissions.md`
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`、`2026-08-01-trustgraph-context-graph-analysis.md`
