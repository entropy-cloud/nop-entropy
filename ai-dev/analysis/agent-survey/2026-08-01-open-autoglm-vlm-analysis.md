# Open-AutoGLM VLM 动作分发与敏感操作接管分析 & Nop AI Agent 工具分发

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/open-autoglm`（Python，手机 Agent VLM，~40 文件，低活跃 2026-03-06）vs `nop-ai-agent`（工具分发）
> Conclusion:

## 一、总览

**Open-AutoGLM** 是基于 AutoGLM 的手机智能助手（Android/HarmonyOS/iOS），极轻量（~40 文件）。三层分离：ModelClient/ActionHandler/Device。

| 维度 | open-autoglm | nop-ai-agent |
|------|-------------|--------------|
| 循环 | VLM 截图→模型→动作→执行 | ReAct |
| 动作分发 | action_name → handler 表 | ToolDispatcher type-based |
| 安全 | confirmation_callback + takeover_callback | security + ContentOrigin |
| 设备抽象 | device_factory（ADB/HDC/xCTest） | — |

## 二、核心机制详解

### 2.1 VLM 驱动循环（`phone_agent/agent.py:84`）
- 截图 → 视觉语言模型 → 解析动作 → ADB/HDC/xCTest 执行。
- 三层分离：**ModelClient**（模型交互）/ **ActionHandler**（动作解析执行）/ **Device**（设备抽象）。

### 2.2 流式 token 标记解析（`model/client.py:84`）
- 模型输出用标记驱动：`finish(message=` / `do(action=`。
- **边流式边解析**——不等完整响应就开始解析动作（低延迟）。
- `action_markers` 定义标记模式。

### 2.3 动作分发表（`actions/handler.py:90`）
- 按 `action_name` 映射到 handler：Tap / Type / Swipe / Launch / …
- 含**屏幕坐标归一化**（不同分辨率统一）。
- `_get_handler`（:90）分派入口。

### 2.4 敏感操作回调（`actions/handler.py:42`）
- **`confirmation_callback`**：敏感操作需用户确认（支付/删除等）。
- **`takeover_callback`**：登录/验证码场景的人工接管（agent 暂停，人类接管输入）。
- 两种粒度：确认（短暂暂停等待 yes/no）vs 接管（完全交给人类）。

### 2.5 Device Factory
- 抽象多端：Android（ADB）/ HarmonyOS（HDC）/ iOS（xCTest）。

## 三、对 nop-ai-agent 的借鉴要点（中相关）

1. **类型化动作分发表**（中价值）——按 action_name 映射 handler 的模式，对应 nop 工具分发（ToolDispatcher 的 type-based 路由）。
2. **敏感操作确认/接管回调**（中价值）——`confirmation_callback`（确认）+ `takeover_callback`（人工接管），对应 nop 审批流（`2026-08-01-agent-governance-toolkit-analysis.md`）的两种粒度：确认是"是/否"决策，接管是"完全交给人类"。
3. **三层（model/action/device）清晰分层**（低价值）——DSL executor 分层参考（模型交互/动作解析/执行环境分离）。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **动作分发失败重试**：action handler 失败 → 重新截图重试（VLM 循环天然重试）。
- **敏感操作确认/接管**：confirmation_callback / takeover_callback——被拒后人工接管（非自动重试）。
- **对 nop 的启示**：敏感操作"接管而非重试"（人工介入）是 nop 审批流的参考（升级语义）。

## 四、结论

Open-AutoGLM 的动作分发表 + 敏感操作确认/接管是 nop 工具分发的轻度参考。局限：体量小、低活跃、强绑移动端 ADB、循环简陋（无规划/压缩）。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D1**（VLM 驱动循环）、**D2**（动作分发表+设备抽象）、**D6**（confirmation/takeover 回调）、**D12**（动作失败重试）。缺失/薄弱：D5、D9。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **工具分发**：nop `AgentToolDispatcher` type-based 路由比其 action 分发表更工程化。
- **审批**：nop `ApprovalGate`/`AutoApproveGate` 比其 confirmation/takeover 回调更系统化。

**必要参考的增量**：
- 无实质增量（手机 VLM 域，nop 是通用引擎）。敏感操作"接管而非重试"（人工介入升级）思想可参考 nop 审批流升级语义。

**总评**：nop-ai-agent **全面超越** open-autoglm（工具分发/审批更工程化，通用 vs 手机专用）；无必要参考。

## References
- `~/ai/open-autoglm/phone_agent/agent.py:84`、`model/client.py:84`、`actions/handler.py:42,90`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`
