# Agent-Browser 守护进程与破坏性动作门禁分析 & Nop AI Agent 工具/会话

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/agent-browser`（Vercel Labs，Rust，AI agent 浏览器自动化工具，287 文件）vs `nop-ai-agent`（工具/会话）
> Conclusion:

## 一、总览与机制
Agent-Browser 是原生 Rust CLI，定位为 AI agent 的浏览器自动化"工具"（非 agent 本身）。核心：**守护进程架构**（持久 Chrome 会话经 CDP 管理，CLI 通过 Unix domain socket 通信，`cli/src/native/daemon.rs`）；**动作策略门禁**（敏感动作 PendingConfirmation 需确认，`actions.rs:64`、`policy.rs`）；多后端（CDP/WebDriver Appium iOS Safari/Vercel sandbox）+ React 内省；**会话状态加密持久化**（AES-256-GCM，`state.rs`）+ restore 校验（URL/文本/JS 三重检查）；HAR 导出/网络追踪/a11y 快照；namespace 隔离 socket。

## 二、对 nop-ai-agent 的借鉴要点
1. **破坏性动作的确认门禁策略**（中价值）——对应 nop 安全层的"危险工具需确认"（policy.rs 的 PendingConfirmation 模式，与 openscience glob ask `2026-08-01-openscience-declarative-agent-analysis.md` 呼应）。
2. **会话持久化 + restore 校验**（中价值）——加密持久化 + 三重校验（URL/文本/JS），对应 nop AgentSession 恢复的一致性校验（与 grok-build 多域 checkpoint `2026-08-01-grok-build-deterministic-replay-analysis.md` 方向一致）。
3. **namespace 隔离的守护进程架构**（低价值）——多会话隔离参考。

## 三、结论
Agent-Browser 的破坏性动作门禁 + 会话 restore 校验是 nop 工具安全/会话恢复的轻度参考。局限：仅"工具"非 agent、无循环/规划、actions.rs 14511 行巨型文件、浏览器域强绑。

## References
- `~/ai/agent-browser/cli/src/native/`（daemon.rs、actions.rs）、`policy.rs`、`state.rs`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security.md`、`nop-ai-agent-session.md`
- `ai-dev/analysis/agent-survey/2026-08-01-openscience-declarative-agent-analysis.md`、`2026-08-01-grok-build-deterministic-replay-analysis.md`
