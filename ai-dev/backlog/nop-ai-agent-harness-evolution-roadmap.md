# nop-ai-agent Harness Evolution Roadmap

> Status: active
> 来源：46 份外部项目调研（`ai-dev/analysis/agent-survey/2026-08-01-*`）的 10 类增量共识。nop-ai 全面超越外部实现，增量全部以 nop 原生方式吸收（XDEF 声明式 + nop-task 复用 + Java 结构化），不引入外部依赖。

## Work Items

### W1. Plan 运行时门控（最高优先）
- [ ] W1-1 plan-dsl：AgentPlanPhase 增加 Gate 门控（on-fail retry/block/escalate + max-retries + require-explicit-verdict）
- [ ] W1-2 plan-dsl：任务依赖增加 Trigger Rule（all_success/one_success/none_failed_min_one_success/all_done）
- [ ] W1-3 plan-dsl：AgentPlanTaskModel 增加 dependsOn（DAG 依赖，nop-task GraphStepAnalyzer 环检测）
- [ ] W1-4 plan 运行时：PlanReplanner（停滞检测 → 阶段回退/任务拆分/失败升级，幂等决策）
- 参考：`2026-08-01-codewhale-workflow-ir-gate-analysis.md`、`2026-08-01-archon-yaml-dag-workflow-analysis.md`、`2026-08-01-jcode-dag-first-agent-analysis.md`、`2026-08-01-spec-kit-workflow-engine-analysis.md`

### W2. Reliability 增量（高优先）
- [ ] W2-1 checkpoint 增加 wait_for 条件 JSONB（WAIT_FOR 长等待原语：挂起不占线程 → 条件满足唤醒恢复）
- [ ] W2-2 checkpoint 增加 idempotency_key 列 + 唯一约束（hash(toolName+callId+输入指纹)，restore 时发散检测）
- [ ] W2-3 三级失败升级（质量失败 max_aegis_rejections / 停滞失败 stale_task_max_retries / 基础设施失败 max_dispatch_retries）
- [ ] W2-4 ProviderFailoverQueue（跨 provider 有序故障转移 P1→P2→P3 + failover_switch 去重）
- 参考：`2026-08-01-hatchet-durable-execution-analysis.md`、`2026-08-01-grok-build-deterministic-replay-analysis.md`、`2026-08-01-mission-control-control-plane-analysis.md`、`2026-08-01-cc-switch-provider-circuit-breaker-analysis.md`

### W3. Middleware 增量（高优先）
- [ ] W3-1 双层中间件：执行级（每次工具/模型尝试，retry 时重新评估安全检查）
- [ ] W3-2 声明式 filter chain：DSL 声明有序 filter ID 列表 + input/output 双链分离
- 参考：`2026-08-01-hive-dual-middleware-analysis.md`、`2026-08-01-plano-declarative-filter-chain-analysis.md`

### W4. 上下文工程增量（中优先）
- [ ] W4-1 引用式压缩双轨（shortRef{type,path,range,hash} + readRef 工具，按内容类型分流）
- [ ] W4-2 压缩前 snapshot 归档 + 压缩比记录（originalSize/compactedSize，失败保留原文）
- 参考：`2026-08-01-context-mode-compaction-analysis.md`、`2026-08-01-beads-versioned-graph-memory-analysis.md`

### W5. Guardrail 验收闭环（中优先）
- [ ] W5-1 GuardrailTestSuite（AttackPlugin 生成 + Grader rubric 打分分离，60+ 攻击类型语料）
- [ ] W5-2 Guideline 依赖/排除关系图（规则关系建模，靠结构收敛）
- [ ] W5-3 BAIL 中断语义（middleware 第三态：中断并丢弃响应）
- 参考：`2026-08-01-promptfoo-redteam-eval-analysis.md`、`2026-08-01-parlant-conversation-control-analysis.md`

### W6. DSL 组合层（低优先）
- [ ] W6-1 Recipe 配方组合层（recipe.xdef：prompt 模板 + 工具集 + 模型配置 + hooks 快照，可叠加）
- 参考：`2026-08-01-goose-provider-hook-recipe-analysis.md`

## 完成定义

- W1-W6 全部 todo → planned（有 execution plan）→ done（closure audit 通过）
- 每项增量以 nop 原生方式实现（XDEF 声明式 / nop-task 复用 / Java 结构化）
- 不引入任何外部开源依赖（仅吸收语义增量）
- 更新 `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` Phase Status（追加 L6. Harness Evolution 层）
