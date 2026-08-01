# Agent Harness 机制参考框架（联网调研综合）

> Status: open
> Date: 2026-08-01
> Scope: 综合 LangChain《Anatomy of an Agent Harness》、Martin Fowler《Harness Engineering》、Microsoft Agent Framework《Agent Harnesses》、Augment《Harness Engineering for AI Coding Agents》等权威来源，建立 Agent Harness 的完整机制维度框架，作为本目录 46 份项目分析报告的对照基准
> Conclusion: Harness = Model + Harness（除模型外的一切：代码、配置、执行逻辑）。完整 harness 包含 12 大机制维度：Agent 循环、文件系统+工具、记忆+上下文、状态持久化、规划+执行模式、审批+安全、反馈环、前馈约束、质量门、上下文转交、可观测性、可靠性（Retry/Replan/Resume）。本框架用于审计各项目报告的机制覆盖完整性。

---

## 1. Harness 定义

**Agent = Model + Harness**（LangChain 定义，行业共识）。

> "If you're not the model, you're the harness." —— 除模型外的一切都是 harness：
> - System Prompts
> - Tools、Skills、MCP 及其描述
> - 捆绑基础设施（文件系统、沙箱、浏览器）
> - 编排逻辑（子 agent 生成、转交、模型路由）
> - Hooks/Middleware（压缩、继续、lint 检查）

模型本身做不到的（持久状态、执行代码、实时知识、环境搭建）都是 harness 层特性。

### 1.1 三层 harness（Martin Fowler 的 bounded context）

```
┌─────────────────────────────┐
│  用户 harness（最外层）      │ ← 用户为用例构建：rules/skills/sensors
├─────────────────────────────┤
│  构建者 harness（中间层）    │ ← agent 产品内置：system prompt/检索/编排
├─────────────────────────────┤
│  模型（核心）                │ ← 被 harness 的对象
└─────────────────────────────┘
```

### 1.2 两种控制方向（cybernetic governor）

| 方向 | 机制 | 作用 | 示例 |
|------|------|------|------|
| **前馈（Feedforward）** | Guides | 行动前引导，提高首次正确率 | rules 文件、skills、架构约束、AGENTS.md |
| **反馈（Feedback）** | Sensors | 行动后观察，自我纠正 | linters、测试、LLM judge、浏览器 |

只有反馈（agent 重复犯错）或只有前馈（规则永不验证）都不行——必须组合。

### 1.3 两种执行类型

| 类型 | 速度 | 确定性 | 示例 |
|------|------|--------|------|
| **Computational（计算型）** | 毫秒-秒 | 确定性 | 测试、linter、类型检查、结构分析 |
| **Inferential（推理型）** | 慢/贵 | 非确定性 | 语义分析、AI code review、LLM as judge |

**关键设计**：linter 错误信息本身就是 prompt（"use X instead of Y"）——错误消息面向 LLM 优化，形成自纠正信号。

---

## 2. 十二大 Harness 机制维度

### D1. Agent 循环（Orchestration Loop）
- ReAct 循环：reason → act（tool call）→ observe → repeat。
- **迭代上限（iteration limit）**：防无限循环。
- **Looping**（Azure）：completion evaluator 判定"完成"——`CompletionMarkerLoopEvaluator("DONE")` / `todos_remaining()` 谓词。
- **Ralph Loop**（LangChain）：hook 拦截模型退出尝试 → 干净上下文窗口重注入原始 prompt → 强制继续直到完成目标。每轮新上下文但读磁盘状态。

### D2. 文件系统 + 工具执行
- 文件系统是最基础的 harness 原语：工作区、增量落盘、跨会话持久、**自然协作面**（多 agent/人通过共享文件协调）。
- **git 版本化**：跟踪工作、回滚错误、分支实验。
- **Bash + 代码执行**：通用工具——agent 自行设计工具，不限于预配置。
- **Sandbox**：安全隔离执行环境（allow-list 命令、网络隔离、按需创建/扇出/销毁）。
- **自验证循环**：写代码 → 跑测试 → 看日志 → 修复。

### D3. 记忆与上下文管理
- **Compaction（压缩）**：上下文窗口将满时智能卸载/总结。
- **Tool call offloading**：工具输出保头保尾，全文卸载到文件系统。
- **Skills（渐进披露 progressive disclosure）**：避免启动时加载过多工具/MCP 导致上下文腐烂。
- **AGENTS.md 注入**：记忆文件标准，会话开始注入，agent 编辑后下次注入更新版——跨会话持续学习。
- **Memory providers**（Azure）：文件记忆、会话记忆。
- **Web Search / MCP**：超越知识截止日期。

### D4. 状态持久化
- **Per-service-call history persistence**（Azure）：每次模型调用后持久化聊天历史——崩溃恢复 + 运行中检查。
- 会话/任务状态跨进程持久。

### D5. 规划与执行模式
- **Todo provider**（Azure）：持久 todo 列表跟踪多步计划。
- **Plan/Execute 模式**：Plan（交互式，问澄清问题、草拟 todo、获批后执行）→ Execute（自主完成 todos）。
- **PEV 循环**（Augment）：Plan-Execute-Verify 三阶段，**phase gates 在每次转换强制**。
  - 与 generate-and-check 的区别：规划显式、执行有界（harness gates 每次工具调用触发）、验证四时机（执行前+运行时+执行后+计划对齐）。
  - **Plan alignment gate**：agent 是否用了现有 auth middleware / 遵守响应格式约定——测试套件看不到的架构问题。
- **Agent mode provider**：plan/execute/custom 模式跟踪。

### D6. 审批与安全
- **Tool approval**（Azure）："Don't ask again" 常设审批规则 + 启发式自动审批（安全无人值守执行）。
- **Pre-execution gates**（Augment）：每次工具调用前——已知工具？参数有效？需用户审批？路径在 workspace 内？
- **Approval policies**：动作前应用审批与安全策略。
- **Tier 边界模式**（GitHub 2500+ 仓库分析）：Always / Ask First / Never 三层。
- **Inline-disable 禁用**：`// eslint-disable-next-line` 应禁用，防 agent 压制违规而非修复。

### D7. 反馈环（Feedback Sensors）
- **Computational sensors**：linter/test/typecheck——便宜快，每次变更都跑。
- **Inferential sensors**：LLM judge / code review agent——贵，但语义判断。
- **失败 → 反馈注入**：验证失败的错误消息循环回 agent 推理（结构化上下文纠正，而非静默丢弃）。
- 连续漂移传感器：死代码检测、测试覆盖质量、依赖扫描（lifecycle 外持续运行）。

### D8. 前馈约束（Feedforward Guides）
- **Rules files**（持久、仓库作用域、自动注入、层级组合）：AGENTS.md（Git root→CWD 层级）/ CLAUDE.md / .cursor/rules/*.mdc。
- **三类型约束架构**（Augment Rules）：always_apply（每次自动）/ agent_requested（agent 判定相关时加载）/ manual（显式调用）——选择性加载保上下文。
- **Taste invariants**（OpenAI）：少量编码团队标准的规则，硬 CI 失败非警告。
- 约束分层：从零开始按序——constraint harnesses → feedback loops → quality gates。
- **过约束是失败模式**：复杂度限制过低会误标合法重构。

### D9. 质量门（Quality Gates）
- **CI gates**：硬失败（error 非 warn），非建议。
- **Staleness gates**：依赖选择与代码库当前策略不匹配。
- **失败类型分类响应**（Cosmos）：spec violation → block merge + 注入失败上下文到 retry loop；integration regression → block deployment；infrastructure failure → pause gates。

### D10. 上下文转交（Handoff）
- **Subagent spawning / background agents**（Azure）：并行委派子任务（web search agent / code agent）。
- **Structured handoff artifacts**：跨上下文窗口的结构化交接产物（agentic SDLC 关键）。
- **上下文重置 + 阶段门**：使多上下文窗口的连贯目标导向工作成为可能。
- 共享代码库多 agent 并行（LangChain 开放问题）。

### D11. 可观测性（Observability）
- **OpenTelemetry**（Azure 内置）：遵循生成式 AI 语义约定。
- Trace 贯穿 tool calls / agent runs / handoffs。

### D12. 可靠性（Retry/Replan/Resume）——本目录报告已覆盖维度
- **Retry**：定时重试队列（retry_after）、指数退避、熔断（Closed/Open/HalfOpen）、幂等（idempotency_key）、分级失败重试、租约重试、预算闸。
- **Replan**：停滞检测→重规划（replan nudge）、阶段级回退、策略/权限 replan、上下文充分性 replan、失败升级（Retry/Block/Escalate）、软中断重规划。
- **Resume**：磁盘断点恢复、会话级恢复、多域恢复、幂等恢复（三级重放）、半完成态处理（reset running）、动作级重放。

---

## 3. Harness 机制覆盖审计表（本目录 46 份报告对照）

> 用于逐份报告标注其 harness 机制覆盖。每份报告应能回答："该项目 harness 覆盖了哪些维度？关键设计是什么？"

| 维度 | 代表项目（本目录） | 关键设计 |
|------|-------------------|----------|
| D1 Agent 循环 | browser-use（sense-plan-act 4166 行）、go-micro（12 层中间件）、wuwe（4 推理模式+22 事件） | ReAct/迭代上限/Looping/Ralph Loop |
| D2 文件系统+工具 | planning-with-files（3-File Pattern）、agent-browser（守护进程+CDP）、opensandbox（6 surface） | IToolFileSystem（nop）、bash、sandbox、自验证 |
| D3 记忆+上下文 | trustgraph（provenance）、context-mode（引用式压缩）、beads（版本化图谱）、minecontext（类型感知合并）、txtai（Factory+融合） | compaction、offloading、skills、AGENTS.md、memory providers |
| D4 状态持久化 | hatchet（durable execution）、grok-build（多域 checkpoint）、exo（不可变日志）、rivet（saveState） | 每次调用持久化、崩溃恢复 |
| D5 规划+执行模式 | spec-kit（四阶段+gate）、jcode（DAG+门节点）、conductor（Decider）、codewhale（Workflow IR+Gate）、archon（Trigger Rule） | todo、plan/execute、PEV、plan alignment gate |
| D6 审批+安全 | AGT（策略引擎+审批流）、mcp-gateway（Session 三元组）、opensandbox（Deny-by-default）、openscience（glob 权限）、parlant（BAIL） | tool approval、pre-execution gates、Always/Ask/Never |
| D7 反馈环 | promptfoo（Plugin+Grader）、desloppify（活计划+integrity）、mission-control（Aegis 质量门）、gstack（分层注入防御） | computational/inferential sensors、失败→反馈注入 |
| D8 前馈约束 | goose（Recipe）、openscience（声明式 agent）、gstack（23 技能）、autoresearch（不可变契约） | rules files、skills、taste invariants、约束分层 |
| D9 质量门 | spec-kit（gate）、codewhale（require_explicit_verdict）、planning-with-files（check-complete.sh）、plan-validator | CI gates、staleness gates、失败分类响应 |
| D10 上下文转交 | call-agent（nop）、rivet（Actor 消息）、orca（federation）、exo（canonical state）、dapr-agents（decision hook） | subagent、handoff artifacts、Ralph Loop、上下文重置 |
| D11 可观测性 | litellm（22+ hook）、helicone（网关+双库）、mission-control（收据签名）、grok-build（15 hook 事件） | OTel、trace、events.jsonl |
| D12 可靠性 | hatchet/jcode/rivet/go-micro/beads/wuwe（详见 §3.5/§4.5） | Retry/Replan/Resume 全谱系 |

---

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

本参考框架本身：十二大机制维度完整定义（D1-D12）。

## References

- LangChain《The Anatomy of an Agent Harness》(2026-03)：https://www.langchain.com/blog/the-anatomy-of-an-agent-harness
- Martin Fowler《Harness engineering for coding agent users》(2026-04)：https://martinfowler.com/articles/harness-engineering.html
- Microsoft Learn《Agent Harnesses》(2026-07)：https://learn.microsoft.com/en-us/agent-framework/agents/harness
- Augment《Harness Engineering for AI Coding Agents》(2026-04)：https://www.augmentcode.com/guides/harness-engineering-ai-coding-agents
- Arize《Context management in agent harnesses》：https://arize.com/blog/context-management-in-agent-harnesses/
- GitHub《AGENTS.md 三层边界模式》（Always/Ask First/Never）
- OpenAI《Harness Engineering》（Ryan Lopopolo, 2026-02）
