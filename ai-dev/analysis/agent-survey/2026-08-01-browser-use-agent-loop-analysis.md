# Browser-Use Agent 循环与计划状态机深度分析 & Nop AI Agent 执行引擎

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/browser-use`（Python，浏览器自动化 Agent 框架，v0.13.7）vs `nop-ai-agent`（engine/ReActAgentExecutor + plan 包）
> Conclusion:

## 一、总览

**Browser-Use** 是最成熟的浏览器 Agent 循环（370 文件，极活跃）。核心：**sense-plan-act 循环**（4166 行单文件引擎）、**工具注册表自动生成 schema**（装饰器+函数签名→Pydantic）、**计划状态机**（plan item: done/current/pending/skipped + 停滞重规划）、**消息压缩 + judge + 循环检测 + 步数预算**。

| 维度 | browser-use | nop-ai-agent |
|------|-------------|--------------|
| 循环 | sense-plan-act（Agent.step） | ReAct |
| 工具 schema | 函数签名自动生成 | DSL 声明 |
| 计划状态 | plan item 4 态 + 停滞 replan | AgentPlan 静态（无运行时状态） |
| 压缩 | MessageCompaction 可配触发 | PipelineCompactor 3 层 |

## 二、核心机制

### 2.1 sense-plan-act（`browser_use/agent/service.py:1029`）
- `Agent.step()`：获取浏览器状态→LLM 决策→执行→后处理；单文件 4166 行是引擎核心。

### 2.2 工具注册表自动生成（`tools/registry/service.py:75,149`）
- 装饰器注册 action；从函数签名自动生成 Pydantic 参数模型；特殊参数注入（browser_session/cdp_client 等）。

### 2.3 计划状态机（`service.py:1411,1458`）
- 模型输出 `plan_update`/`current_plan_item`；plan item 带 done/current/pending/skipped；连续失败触发 replan nudge。

## 三、对 nop-ai-agent 的借鉴要点

1. **从方法签名自动生成 action 描述符**（高价值，契合 DSL-first）——nop 可用注解+反射从 Java 方法签名自动生成工具 DSL 描述符（参数 schema），减少手写声明。这是"DSL-first"的进阶：从"手写 DSL"到"代码即 DSL"。
2. **计划状态机 + 停滞重规划**（高价值）——与 planning-with-files（`2026-08-01-planning-with-files-persistent-plan-analysis.md`）/spec-kit（`2026-08-01-spec-kit-workflow-engine-analysis.md`）的 PlanRun 呼应；browser-use 增加 **replan nudge（连续失败触发重规划）**——plan 运行时的重要能力（静态计划无法适应失败）。
3. **特殊参数注入模式**（中价值）——工具执行时由引擎注入上下文对象（session/client），而非工具自取；对应 nop 的 ToolContext 注入。
4. **可配触发的消息压缩**（中价值）——压缩触发条件可配（token 阈值/消息数/显式调用），与 context-mode（`2026-08-01-context-mode-compaction-analysis.md`）的按需压缩呼应。

## 四、结论

browser-use 的工具 schema 自动生成 + 计划状态机 + replan nudge 是 nop 执行引擎的直接参考。局限：Agent 类单体 4166 行耦合重、强绑浏览器域、Python 专有。

## References
- `~/ai/browser-use/browser_use/agent/service.py`、`tools/registry/service.py`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-engine.md`、`nop-ai-agent-plan-dsl.md`
- `ai-dev/analysis/agent-survey/2026-08-01-planning-with-files-persistent-plan-analysis.md`、`2026-08-01-spec-kit-workflow-engine-analysis.md`、`2026-08-01-context-mode-compaction-analysis.md`
