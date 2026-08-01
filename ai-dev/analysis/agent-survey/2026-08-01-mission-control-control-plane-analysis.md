# Mission-Control 控制平面：质量门、收据签名与多 Runtime 适配分析 & Nop AI Agent 治理层

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/mission-control`（Builderz Labs，TS/Next.js，自托管 agent 控制平面，alpha）vs `nop-ai-agent`（guardrail + team 包）
> Conclusion:

## 一、总览

**Mission-Control** 是"引擎之上"的编排/治理层，不接管推理/工具循环，只做治理——谁拥有任务、什么被执行、哪个结果通过评审、钱花在哪。通过 runtime adapter 适配 OpenClaw/Claude Code/Codex/CrewAI/LangGraph/AutoGen 等外部引擎，自身无推理能力。

| 维度 | mission-control | nop-ai-agent |
|------|----------------|--------------|
| 定位 | 控制平面（引擎之上的治理层） | 运行时引擎（含推理/工具/安全） |
| Runtime 适配 | 多 adapter 注册（`agent-runtimes.ts`） | ChatModelProvider 单一抽象 |
| 质量门 | Aegis 质量门 + 三级失败升级 | guardrail-contract |
| 完成证明 | 收据签名（`receipt-signing.ts`） | checkpoint（append-only） |
| 存储 | SQLite（Task/Agent/Operations/Knowledge/Governance 六大面） | AgentSession + checkpoint |
| 暴露面 | Web UI/CLI/MCP/OpenAPI/WebSocket/SSE 六面 | API |

## 二、核心机制详解

### 2.1 多 Runtime Adapter 注册（`src/lib/agent-runtimes.ts`）
- 每个 adapter 把外部 agent 引擎（Claude Code/Codex/CrewAI/LangGraph/AutoGen/OpenClaw）的接口适配为统一的任务派发/状态查询接口。
- 注册模式：runtimes 表声明可用引擎 + 各自的配置模板（认证/端点/能力声明）。

### 2.2 Aegis 质量门 + 三级失败升级（`src/lib/task-dispatch.ts:23`）
- 任务派发时绑定质量门；失败时按三个阈值分级升级：
  - `max_aegis_rejections`：质量门拒绝次数上限（超限标记任务为"质量不达标"）
  - `stale_task_max_retries`：陈旧任务（长时间无进展）的最大重试次数
  - `max_dispatch_retries`：派发本身失败的最大重试次数（网络/引擎不可用等）
- 这三个阈值是**独立的**，分别针对"质量不合格""停滞""基础设施故障"三种失败模式。

### 2.3 完成收据签名（`src/lib/receipt-signing.ts`）
- 任务完成后生成签名收据（含任务 ID/结果摘要/完成时间/签名）。
- 防篡改审计：收据签名保证执行结果不可事后抵赖。

### 2.4 Scheduler + GitHub 双向同步（`src/lib/scheduler.ts`、`src/lib/github-sync-engine.ts`）
- `scheduler.ts`：recurring-tasks 定时调度（cron 表达式驱动周期性 agent 任务）。
- `github-sync-engine.ts`：GitHub issue ↔ task 双向同步（issue 创建→派发给 agent；agent 完成结果→回写 issue）。

## 三、对 nop-ai-agent 的借鉴要点

1. **多 runtime adapter 注册模式**（中价值）——适配 nop 的 provider/middleware 装配；nop 若需对接外部 agent 生态（非自建引擎场景），可参考此 adapter 注册+能力声明模式。
2. **Aegis 质量门 + 三级失败升级**（中价值）——`max_aegis_rejections/stale_task_max_retries/max_dispatch_retries` 三级阈值映射 nop plan 异常分支与 hook 拦截：质量门对应 guardrail-contract 的评审拦截；stale_task 对应 hive 的 stall 检测（`2026-08-01-hive-dual-middleware-analysis.md`）；dispatch_retry 对应 reliability 的重试。
3. **完成收据签名**（高价值）——防篡改审计：checkpoint 增加"收据签名"，保证 plan 执行结果不可抵赖（与 exo 不可变日志 `2026-08-01-exo-self-evolving-analysis.md`、grok-build Journal `2026-08-01-grok-build-deterministic-replay-analysis.md` 方向一致）。落地：checkpoint 的 append-only INSERT 天然适合追加签名收据列。
4. **GitHub 双向同步**（中价值）——team 包若需对接外部 issue tracker（GitHub/Jira），可参考 issue↔task 双向同步引擎设计。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **三级失败升级**（`src/lib/task-dispatch.ts:23`）：`max_aegis_rejections`（质量门拒绝上限）/ `stale_task_max_retries`（停滞任务重试上限）/ `max_dispatch_retries`（派发失败重试上限）——三种失败模式分别重试。
- **recurring-tasks 调度**（`scheduler.ts`）：cron 驱动周期性任务——失败任务下轮自动重试。
- **GitHub 双向同步**（`github-sync-engine.ts`）：issue↔task 同步——外部状态变化可触发任务重试。
- **对 nop 的启示**：三级失败升级（质量/停滞/基础设施分治）是 nop reliability 重试策略的参考；stale_task 对应 hive 的 stall 检测。

## 四、优缺点

### 优点
1. "引擎之上"定位清晰——不重新造引擎，专注于治理/编排/审计。
2. 三级失败升级覆盖三种不同失败模式（质量/停滞/基础设施），比单一重试策略更精细。
3. 收据签名提供防篡改审计，适合企业合规场景。

### 缺点
1. alpha 阶段，schema/API 易变。
2. 自身无推理/工具执行能力，强依赖外部 runtime。
3. TypeScript 单体（Next.js），难以直接迁移到 Java。
4. SQLite 单机存储，不支持分布式部署。

## 五、结论

Mission-Control 的"质量门 + 收据签名 + 三级失败升级"是 nop 治理/审计层的参考。收据签名（防篡改审计）是最高价值借鉴点。局限：alpha 易变、自身无执行能力强依赖外部 runtime、TS 单体难迁移 Java。

## Open Questions
- [ ] nop 的 checkpoint 如何集成收据签名（追加列 vs 独立审计表）？
- [ ] 三级失败升级的阈值由谁配置（AgentModel 静态 vs 运行时动态）？

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D7**（Aegis 质量门+三级失败升级）、**D11**（完成收据签名+6 面暴露）、**D12**（max_aegis_rejections/stale_task/max_dispatch 三级重试）。缺失/薄弱：D1（控制面非引擎）、D2。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **审计**：nop `AuditEvent`/`AuditDecision` + checkpoint append-only 比 mission-control 的收据签名更完整（nop 是结构化事件）。
- **任务管理**：nop team 包（TeamTask/blockedBy/nop-task DAG）比 mission-control 的 SQLite 任务管理更成熟。
- **控制面**：nop 是引擎 + 治理一体，mission-control 是外部控制面（依赖外部 runtime）——nop 无此依赖。

**必要参考的增量（以超越方式吸收）**：
- **三级失败升级**（质量/停滞/基础设施分治）：nop reliability 重试可区分三类失败分别处理——真正增量（对应 hive stall 检测 + nop 熔断的组合）。

**总评**：nop-ai-agent 在审计/任务/架构上**全面超越**；三级失败升级（质量门/停滞/派发分治）一个增量值得吸收，nop 实现优于其 TS 单体。

## References
- `~/ai/mission-control/src/lib/`（agent-runtimes.ts、task-dispatch.ts、receipt-signing.ts、scheduler.ts、github-sync-engine.ts）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`、`guardrail-contract.md`
- `ai-dev/analysis/agent-survey/2026-08-01-exo-self-evolving-analysis.md`、`2026-08-01-grok-build-deterministic-replay-analysis.md`、`2026-08-01-hive-dual-middleware-analysis.md`
