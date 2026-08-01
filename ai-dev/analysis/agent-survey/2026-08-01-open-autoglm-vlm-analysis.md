# Open-AutoGLM VLM 动作分发与敏感操作接管分析 & Nop AI Agent 工具分发

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/open-autoglm`（Python，手机 Agent VLM，40 文件，低活跃）vs `nop-ai-agent`（工具分发）
> Conclusion:

## 一、总览与机制
Open-AutoGLM 是基于 AutoGLM 的手机智能助手（Android/HarmonyOS/iOS），极轻量（~40 文件）。核心：**VLM 驱动循环**（截图→视觉语言模型→解析动作→ADB/HDC/xCTest 执行，`phone_agent/agent.py:84`）；**流式 token 标记解析**（模型输出用 finish(message=/do(action= 标记，边流式边解析，`model/client.py:84`）；**动作分发表**（按 action_name 映射 handler：Tap/Type/Swipe/Launch…，`actions/handler.py:90`）含屏幕坐标归一化；**敏感操作 confirmation_callback + 登录/验证码 takeover_callback**（`handler.py:42`）；device_factory 抽象多端。

## 二、对 nop-ai-agent 的借鉴要点（中相关）
1. **类型化动作分发表**（中价值）——按 action_name 映射 handler 的模式，对应 nop 工具分发（ToolDispatcher 的 type-based 路由）。
2. **敏感操作确认/接管回调**（中价值）——confirmation_callback（确认）+ takeover_callback（人工接管），对应 nop 审批流（`2026-08-01-agent-governance-toolkit-analysis.md`）的两种粒度。
3. **三层（model/action/device）清晰分层**（低价值）——DSL executor 分层参考。

## 三、结论
Open-AutoGLM 的动作分发表 + 敏感操作确认/接管是 nop 工具分发的轻度参考。局限：体量小、低活跃、强绑移动端 ADB、循环简陋（无规划/压缩）。

## References
- `~/ai/open-autoglm/phone_agent/agent.py`、`model/client.py`、`actions/handler.py`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-engine.md`
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`
