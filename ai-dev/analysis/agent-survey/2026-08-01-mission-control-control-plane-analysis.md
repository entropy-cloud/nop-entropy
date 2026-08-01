# Mission-Control 控制平面与质量门收据签名分析 & Nop AI Agent 治理层

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/mission-control`（Builderz Labs，TS/Next.js，自托管 agent 控制平面，alpha）vs `nop-ai-agent`
> Conclusion:

## 一、总览与机制
Mission-Control 是"引擎之上"的编排/治理层，不接管推理/工具循环。通过 **runtime adapter** 适配 OpenClaw/Claude Code/Codex/CrewAI/LangGraph/AutoGen（`src/lib/agent-runtimes.ts`）；任务派发带 **Aegis 质量门 + 完成收据签名**（`task-dispatch.ts:23` 失败时按 max_aegis_rejections/stale_task_max_retries/max_dispatch_retries 分级升级，`receipt-signing.ts` 收据签名）；SQLite 存本地状态，6 大面（Task/Agent/Operations/Knowledge/Governance）；scheduler + recurring-tasks + github-sync 双向同步。

## 二、对 nop-ai-agent 的借鉴要点
1. **多 runtime adapter 注册模式**——适配 nop 的 provider/middleware 装配（`agent-runtimes.ts`）；可作为 nop 对接外部 agent 生态的参考。
2. **Aegis 质量门 + 失败分级升级**（中价值）——`max_aegis_rejections/stale_task_max_retries/max_dispatch_retries` 三级阈值映射 nop plan 异常分支与 hook 拦截。
3. **完成收据签名**（高价值）——防篡改审计：checkpoint 增加"收据签名"，保证 plan 执行结果不可抵赖（与 exo 不可变日志 `2026-08-01-exo-self-evolving-analysis.md`、grok-build Journal `2026-08-01-grok-build-deterministic-replay-analysis.md` 方向一致）。

## 三、结论
Mission-Control 的"质量门 + 收据签名"是 nop 治理/审计层的参考。局限：alpha 易变、自身无执行能力强依赖外部 runtime、TS 单体难迁移 Java。

## References
- `~/ai/mission-control/src/lib/`（agent-runtimes.ts、task-dispatch.ts、receipt-signing.ts、scheduler.ts）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-team.md`、`nop-ai-agent-guardrail.md`
- `ai-dev/analysis/agent-survey/2026-08-01-exo-self-evolving-analysis.md`、`2026-08-01-grok-build-deterministic-replay-analysis.md`
