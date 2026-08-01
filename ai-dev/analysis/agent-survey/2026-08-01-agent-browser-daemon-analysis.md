# Agent-Browser 守护进程与破坏性动作门禁分析 & Nop AI Agent 工具/会话

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/agent-browser`（Vercel Labs，Rust，AI agent 浏览器自动化工具，~287 文件，v0.33.2）vs `nop-ai-agent`（工具安全 + session）
> Conclusion:

## 一、总览

**Agent-Browser** 是原生 Rust CLI，定位为 AI agent 的浏览器自动化"工具"（非 agent 本身）。核心：**守护进程架构**、**动作策略门禁**、**会话加密持久化 + restore 校验**、**namespace 隔离**。

| 维度 | agent-browser | nop-ai-agent |
|------|--------------|--------------|
| 架构 | 守护进程（持久 Chrome 会话 + Unix socket） | — |
| 安全 | PendingConfirmation 门禁（敏感动作需确认） | security + ContentOrigin |
| 持久化 | AES-256-GCM 加密 + restore 三重校验 | checkpoint append-only |
| 隔离 | namespace socket 隔离 | AgentSession |
| 多后端 | CDP/WebDriver(Appium iOS/Safari)/Vercel sandbox | — |

## 二、核心机制详解

### 2.1 守护进程架构（`cli/src/native/daemon.rs`、`actions.rs`）
- 持久 Chrome 会话经 CDP（Chrome DevTools Protocol）管理。
- CLI 通过 **Unix domain socket** 通信——轻量 IPC。
- 运行时零 Node.js/Playwright 依赖（纯 Rust）。

### 2.2 动作策略门禁（`actions.rs:64`、`policy.rs`）
- 敏感动作触发 **`PendingConfirmation`**——需用户确认才执行。
- policy.rs 定义哪些动作需要确认（破坏性动作如删除/提交/导航）。

### 2.3 会话状态加密持久化（`state.rs`）
- **AES-256-GCM** 加密持久化会话状态。
- **restore 校验**（`agent-browser.schema.json:33-44`）：URL/文本/JS **三重检查**——恢复时验证页面状态一致性。
- HAR 导出、网络追踪、a11y 快照。

### 2.4 namespace 隔离
- socket 通过 namespace 隔离——多会话互不干扰。

### 2.5 React 内省（`react/`）
- React 组件树内省——直接操作 React 组件而非 DOM。

## 三、对 nop-ai-agent 的借鉴要点

1. **破坏性动作的确认门禁策略**（中价值）——对应 nop 安全层的"危险工具需确认"（PendingConfirmation 模式，与 openscience glob ask `2026-08-01-openscience-declarative-agent-analysis.md` 呼应）。
2. **会话持久化 + restore 校验**（中价值）——加密持久化 + 三重校验（URL/文本/JS），对应 nop AgentSession 恢复的一致性校验（与 grok-build 多域 checkpoint `2026-08-01-grok-build-deterministic-replay-analysis.md` 方向一致）。
3. **namespace 隔离的守护进程架构**（低价值）——多会话隔离参考（nop 用 AgentSession 独立即可）。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **会话加密持久化 + restore 三重校验**（`state.rs`）：AES-256-GCM 加密 + URL/文本/JS 三重检查——**恢复时校验状态一致性**（不一致则重新加载）。
- **破坏性动作门禁**（`actions.rs:64`）：PendingConfirmation——敏感动作被拒后可调整方案重试。
- **对 nop 的启示**：restore 三重校验（URL/文本/JS）是 nop AgentSession 恢复一致性校验的参考。

## 四、结论

Agent-Browser 的破坏性动作门禁 + 会话 restore 校验是 nop 工具安全/会话恢复的轻度参考。局限：仅工具非 agent、无循环/规划、actions.rs 14511 行巨型文件。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D2**（守护进程+CDP 浏览器工具+namespace 隔离）、**D6**（破坏性动作 PendingConfirmation 门禁）、**D4**（会话加密持久化+restore 三重校验）、**D12**（restore 一致性校验）。缺失/薄弱：D1（非 agent，仅工具）、D5（无规划）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **会话持久化**：nop `DBCheckpointManager` append-only + `AgentSession` 存储比 agent-browser 的 AES 加密快照更完整（nop 有恢复语义）。
- **工具安全**：nop security 6 层 + ApprovalGate 比其 PendingConfirmation 门禁更系统化。

**必要参考的增量（以超越方式吸收）**：
- **restore 一致性三重校验**（URL/文本/JS 检查）：nop AgentSession 恢复可增加一致性校验——增强（非必需）。

**总评**：nop-ai-agent **全面超越** agent-browser（会话/安全更完整）；restore 校验作为可选增强。

## References
- `~/ai/agent-browser/cli/src/native/`（daemon.rs、actions.rs:64）、`policy.rs`、`state.rs`、`agent-browser.schema.json:33-44`、`react/`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`、`nop-ai-agent-session-and-storage.md`
- `ai-dev/analysis/agent-survey/2026-08-01-openscience-declarative-agent-analysis.md`、`2026-08-01-grok-build-deterministic-replay-analysis.md`
