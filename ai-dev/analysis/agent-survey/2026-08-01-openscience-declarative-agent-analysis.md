# OpenScience 声明式 Agent 与权限 Glob 规则集深度分析 & Nop AI Agent DSL/权限

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/openscience`（TS/Bun，科研 AI 工作台）vs `nop-ai-agent`（AgentModel DSL + security PermissionMatrix）
> Conclusion:

## 一、总览

**OpenScience** 是开源科研 AI 工作台。核心：**声明式 agent 定义**（zod schema：name/mode=subagent|primary|all/permission ruleset/model/steps）、**工具注册表**（tool.ts 参数+execute + 同名 .txt 描述配对）、**glob 级 per-tool 权限规则集**（allow/ask/deny 可合并）、**provenance 追踪**。

| 维度 | openscience | nop-ai-agent |
|------|-------------|--------------|
| Agent 定义 | zod schema（mode/permission/model 绑定） | AgentModel（XDEF DSL） |
| 权限 | per-tool glob allow/ask/deny 可合并 | PermissionMatrix |
| 工具描述 | .ts 参数 + .txt 描述配对 | DSL 声明 |
| subagent | primary vs subagent 模式委派 | team 包 |

## 二、核心机制

### 2.1 声明式 agent（`backend/cli/src/agent/agent.ts:26`）
- zod schema：name/mode(subagent|primary|all)/permission ruleset/model/steps；支持 `generate()` 用 LLM 动态生成 agent。

### 2.2 工具注册表（`tool/registry.ts`）
- tool(.ts 参数+execute) + 同名 .txt(描述) 配对；插件可加载自定义工具。

### 2.3 权限规则集（`permission/next`）
- per-tool glob allow/ask/deny，可合并（如 `*.env`→ask）。

## 三、对 nop-ai-agent 的借鉴要点

1. **声明式 agent schema（mode/permission/model 绑定）**（最高价值，天然契合 DSL-first）——nop AgentModel 可参考其 mode（subagent/primary/all）与 permission ruleset 的绑定方式，让权限成为 agent 定义的一等字段（而非外挂 security 链）。
2. **glob 级 per-tool 权限规则集**（高价值）——nop PermissionMatrix 可引入 glob 匹配（`*.env`→ask/deny）+ 规则合并语义，比当前矩阵更灵活。`ask`（需确认）三态比 allow/deny 二态更实用（对应 AGT 审批流 `2026-08-01-agent-governance-toolkit-analysis.md`）。
3. **tool = 参数schema+描述(.txt)+execute 模式**（中价值）——工具描述与实现分离（.txt 描述给 LLM，.ts 执行），对应 nop 的工具元数据设计。
4. **provenance 追踪**（中价值）——与 trustgraph 的来源元数据（`2026-08-01-trustgraph-context-graph-analysis.md`）呼应。

## 四、结论

openscience 的声明式 agent + glob 权限规则集最贴"DSL-first"理念，是 nop AgentModel/PermissionMatrix 的直接参考。局限：科研域重、大 monorepo、Bun/TS 专有、浏览器工作台耦合。

## References
- `~/ai/openscience/backend/cli/src/agent/agent.ts`、`tool/registry.ts`、`permission/`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-dsl.md`、`nop-ai-agent-security.md`
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`、`2026-08-01-trustgraph-context-graph-analysis.md`
