# Browser-Use Agent 循环与计划状态机深度分析 & Nop AI Agent 执行引擎

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/browser-use`（Python，浏览器自动化 Agent 框架，~370 文件，v0.13.7）vs `nop-ai-agent`（engine ReActAgentExecutor + plan 包）
> Conclusion:

## 一、总览

**Browser-Use** 是最成熟的浏览器 Agent 循环。核心：**sense-plan-act 循环**（4166 行单文件引擎）、**工具注册表自动生成 schema**、**计划状态机**（done/current/pending/skipped + replan nudge）、**消息压缩 + judge + 循环检测 + 步数预算**。

| 维度 | browser-use | nop-ai-agent |
|------|-------------|--------------|
| 循环 | sense-plan-act（Agent.step） | ReAct |
| 工具 schema | 函数签名自动生成 Pydantic 模型 | DSL 声明 |
| 计划状态 | plan item 4 态 + 停滞 replan nudge | AgentPlan 静态（AgentExecStatus 9 值） |
| 压缩 | MessageCompaction 可配触发 | PipelineCompactor 3 层 |

## 二、核心机制详解

### 2.1 sense-plan-act 循环（`browser_use/agent/service.py:1029`）
- `Agent.step()`：获取浏览器状态 → LLM 决策 → 执行动作 → 后处理。
- 单文件 4166 行是整个引擎核心。

### 2.2 工具注册表自动生成 schema（`tools/registry/service.py:75,149`）
- 装饰器注册 action。
- 从**函数签名自动生成 Pydantic 参数模型**——无需手写 schema。
- **特殊参数注入**（:57）：`browser_session` / `cdp_client` 等由引擎注入，而非工具自取。

### 2.3 计划状态机（`service.py:1411,1458`）
- 模型输出 `plan_update` / `current_plan_item`。
- plan item 带 **4 态**：done / current / pending / skipped。
- **连续失败触发 replan nudge**（:1458 `_inject_replan_nudge`）——停滞检测 + 自动重规划提示。
- `_update_plan_from_model_output`（:1411）：从模型输出更新计划状态。

### 2.4 消息压缩 + judge + 循环检测
- `MessageCompaction`（`views.py:35`）：可配置触发的消息压缩。
- judge 校验：LLM 判定结果合理性。
- 循环检测：检测重复行为模式。
- 步数预算告警：接近 MaxSteps 时提醒 agent。

### 2.5 内置 MCP server + skill 系统 + EventBus（bubus）
- browser-use 把自身注册为 coding agent 的 skill。
- EventBus（bubus）：事件总线驱动异步通信。

## 三、对 nop-ai-agent 的借鉴要点

1. **从方法签名自动生成 action 描述符**（高价值，契合 DSL-first）——nop 可用注解+反射从 Java 方法签名自动生成工具 DSL 描述符（参数 schema），减少手写声明。这是"DSL-first"的进阶：从"手写 DSL"到"代码即 DSL"。
2. **计划状态机 + 停滞重规划**（高价值）——与 planning-with-files（`2026-08-01-planning-with-files-persistent-plan-analysis.md`）/spec-kit（`2026-08-01-spec-kit-workflow-engine-analysis.md`）的 PlanRun 呼应；browser-use 增加 **replan nudge（连续失败触发重规划）**——plan 运行时的重要能力（静态计划无法适应失败）。nop 的 AgentExecStatus 已有 failed 状态，可对接 replan nudge。
3. **特殊参数注入模式**（中价值）——工具执行时由引擎注入上下文对象（session/client），而非工具自取；对应 nop 的 ToolContext 注入。
4. **可配触发的消息压缩**（中价值）——压缩触发条件可配（token 阈值/消息数/显式调用），与 context-mode（`2026-08-01-context-mode-compaction-analysis.md`）的按需压缩呼应。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **停滞重规划 replan nudge**（`service.py:1411,1458`）：`_inject_replan_nudge`——连续失败触发重规划提示（**停滞检测 → 自动 replan**）。
- **计划状态机 4 态**：plan item 带 done/current/pending/skipped——失败步骤标记后重规划路径。
- **循环检测 + 步数预算**：重复行为检测 + MaxSteps 预算告警——防死循环。
- **对 nop 的启示**：replan nudge（连续失败→重规划）是 nop plan 运行时 replan 触发机制的直接参考；与 planning-with-files/spec-kit 的 PlanRun 呼应。

## 四、结论

browser-use 的工具 schema 自动生成 + 计划状态机 + replan nudge 是 nop 执行引擎的直接参考。局限：Agent 类单体 4166 行耦合重、强绑浏览器域、Python 专有。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D1**（sense-plan-act 循环+步数预算告警）、**D2**（工具注册表自动生成 schema）、**D5**（plan 状态机 4 态+replan nudge）、**D3**（MessageCompaction）、**D12**（停滞检测→重规划）。缺失/薄弱：D6（无显式审批）、D9。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **引擎**：nop `ReActAgentExecutor` + 12 hook 点 + middleware 洋葱链比 browser-use 的 4166 行单体 Agent 类更模块化（nop 是 25 模块拆分）。
- **计划**：nop AgentPlan 模型 + AgentExecStatus 9 态比其 plan item 4 态更丰富。
- **压缩**：nop PipelineCompactor 3 层比其 MessageCompaction 更工程化。

**必要参考的增量（以超越方式吸收）**：
- **工具 schema 自动生成**（从方法签名自动生成 DSL 描述符）：nop 可用注解+反射从 Java 方法签名生成工具 schema——真正增量（减少手写 DSL，DSL-first 的进阶）。
- **replan nudge**（连续失败触发重规划）：nop plan 运行时停滞检测可增加——增强（与 hive stall 检测统一）。

**总评**：nop-ai-agent **全面超越** browser-use（引擎模块化/计划/压缩更优）；工具 schema 自动生成 + replan nudge 两个增量吸收。

## References
- `~/ai/browser-use/browser_use/agent/service.py:1029,1411,1458`（4166 行）、`tools/registry/service.py:57,75,149`、`views.py:35`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`、`nop-ai-agent-plan-dsl.md`
- `ai-dev/analysis/agent-survey/2026-08-01-planning-with-files-persistent-plan-analysis.md`、`2026-08-01-spec-kit-workflow-engine-analysis.md`、`2026-08-01-context-mode-compaction-analysis.md`
