# Trellis 元 Harness 状态注入深度分析 & Nop AI Agent Hook 体系

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/Trellis`（Trellis-org/Trellis，跨平台 agent harness 配置生成器）vs `nop-ai-agent`（hook 15 生命周期点 + AgentModel 静态配置）
> Conclusion:

## 一、总览

**Trellis** 不是 agent 运行时，而是"元 harness"：用一套配置（`trellis.yaml`）为 11+ 个平台（Claude Code/Cursor/Codex/OpenCode/Codeium/…）生成对应的 harness 配置文件（`.claude/`、`.cursor/rules/`、`.codex/` 等），并通过 **shared-hooks（状态注入钩子）** 把工作流状态（`.trellis/workflow.md`）注入到各平台的 agent 会话中。核心机制：**配置模板化生成 + 工作流状态注入 + 4 阶段 workflow 状态机**。

| 维度 | Trellis | Nop AI Agent |
|------|---------|--------------|
| 定位 | 配置生成器（元层） | 运行时引擎（执行层） |
| 状态机 | 4 阶段 workflow（PLAN→IMPLEMENT→VERIFY→REVIEW + 决策态） | 无阶段（ReAct 循环） |
| 注入 | shared-hooks（inject-workflow-state.py 等，hook 配置注入） | PromptAssembly 注入 + hook 生命周期 |
| 模板化 | configurators（per-platform 模板） | XDEF 静态配置（无模板生成概念） |

**核心结论先行**：Trellis 对 nop 的借鉴价值在**方法论层**：①**hook 优先的状态注入**——用 hook 在每次工具调用前注入工作流状态（与 planning-with-files 的 PreToolUse cat 同构，Trellis 把它产品化为 shared-hooks 跨平台复用）；②**配置生成器模式**——nop 的 XDEF 配置是"直接配置"，Trellis 的 configurators 是"模板 → 生成 → 注入"间接模式，对 nop 的多平台适配（CLI/服务/桌面）有参考意义。nop 已有 `2026-06-07-trellis-vs-age-comparison.md`（agent-survey 目录外部，analysis 根目录）对比 Trellis 与 AGE；本报告聚焦状态注入机制与 nop hook 的映射。

## 二、Context（调研背景）

- **为什么需要这个分析**：7 月博客再次以 Trellis 为例展示"状态注入"类 harness 设计；nop 的 hook 体系（15 生命周期点）已实现，需要评估"通过 hook 注入外部状态文件"这一模式的适用性。
- **要回答的问题**：Trellis 的 shared-hooks 注入与 nop 的 PromptAssembly/hook 如何对照？模板生成模式值得借鉴吗？
- **约束**：nop 是 Java 运行时；Trellis 是配置/脚本层（python + yaml）。

## 三、核心机制详解

### 3.1 Configurators（11 平台模板）

- `configurators/` 下每个平台一个适配器：读取统一 trellis.yaml → 生成该平台的配置格式。
- 共享概念（agents/skills/workflow/hooks）在平台间映射（如 Claude 的 hooks ↔ Codex 的 instructions）。

### 3.2 工作流状态机（.trellis/workflow.md）

- 4 阶段 + 1 决策态：PLAN → IMPLEMENT → VERIFY → REVIEW，阶段间有决策点（可回退）。
- 状态文件由 agent 更新（模板指令指导），hook 读取。

### 3.3 Shared-Hooks（核心机制）

- `shared-hooks/inject-workflow-state.py`：把当前 workflow 阶段/状态注入 agent 上下文（每次工具调用前执行，等价 planning-with-files 的 PreToolUse cat）。
- 跨平台复用：hook 脚本一套，配置模板各自适配。

## 四、优缺点

### 优点

1. 一套配置多平台生效，harness 行为一致性高。
2. 状态注入确保"agent 永远知道自己在哪个阶段"（防目标漂移）。
3. 生成式配置可审计（生成物 vs 模板源）。

### 缺点

1. 生成层增加间接性：改一处模板影响所有平台，调试复杂。
2. 状态机（4 阶段）比 spec-kit（四阶段 + gate）简单，无持久化 RunState（依赖文件纪律）。
3. 脚本 + yaml 的方案在 Java 服务端场景水土不服（只适合客户端 harness）。

## 五、对 nop-ai-agent 的借鉴要点（核心价值）

### 5.1 状态注入 hooks（确认/轻度扩展）

- nop 已有 PreTool hook + PromptAssembly；Trellis 的"注入外部状态文件"模式可用 nop 的 hook 机制**零成本实现**（新增一个 FileStateInjector hook：读取指定状态文件 → 附加到 prompt）。
- 价值：nop 计划运行时（spec-kit/jcode 借鉴）落定后，可用该 hook 注入 PlanRun 状态——即把"计划状态注入"做成可配置 hook 而非硬编码。

### 5.2 配置生成器模式（评估）

- nop 是单平台运行时（Java），无多平台配置需求；但"模板 → 生成"间接模式对 nop 的 **multi-agent 场景**（team 包：按团队角色生成 Agent 配置）有参考意义——类似 recipe（`2026-08-01-goose-provider-hook-recipe-analysis.md`）的组合层。

### 5.3 不借鉴的

- yaml/脚本基础设施；nop 全 Java 即可（hook 即 Java 类，配置即 XDEF）。

## 六、结论

- Trellis 状态注入与 nop hook 体系**同构**（PreToolUse ↔ nop PreTool），nop 无需新机制；唯一可选借鉴是"状态文件注入 hook"（把计划状态注入做成可配置 hook）。
- 配置生成器模式仅对 team 包角色配置生成有参考意义（低优先）。
- 后续工作：无重大设计变更；在 hook 设计文档中记录该外部参照。

## Open Questions

- [ ] 状态注入 hook 的状态来源（文件/AgentSession/外部服务）？
- [ ] 注入频率（每次工具调用 vs 每轮模型调用）对 token 成本的影响？

## References

- `~/ai/Trellis/`（configurators/、shared-hooks/、workflows/）
- `nop-ai-agent/src/main/java/io/nop/ai/agent/hook/`、`middleware/`
- `ai-dev/analysis/2026-06-07-trellis-vs-age-comparison.md`（已存在的 Trellis vs AGE 对比）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-hook.md`
