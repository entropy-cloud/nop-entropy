# nop-ai-agent Harness Evolution Roadmap

> Status: active
> 来源：46 份外部项目调研（`ai-dev/analysis/agent-survey/2026-08-01-*`）的 10 类增量共识。nop-ai 全面超越外部实现，增量全部以 nop 原生方式吸收（XDEF 声明式 + nop-task 复用 + Java 结构化），不引入外部依赖。

## Work Items

### W1. Plan 运行时门控（最高优先）
- [x] W1-1 plan-dsl：AgentPlanPhase 增加 Gate 门控（on-fail retry/block/escalate + max-retries + require-explicit-verdict）
- [x] W1-2 plan-dsl：任务依赖增加 Trigger Rule（all_success/one_success/none_failed_min_one_success/all_done）
- [x] W1-3 plan-dsl：AgentPlanTaskModel 增加 dependsOn（DAG 依赖，nop-task GraphStepAnalyzer 环检测）
- [ ] W1-4 plan 运行时：PlanReplanner（停滞检测 → 阶段回退/任务拆分/失败升级，幂等决策）
- 参考：`2026-08-01-codewhale-workflow-ir-gate-analysis.md`、`2026-08-01-archon-yaml-dag-workflow-analysis.md`、`2026-08-01-jcode-dag-first-agent-analysis.md`、`2026-08-01-spec-kit-workflow-engine-analysis.md`

### W2e. LLM 错误规范化与配额感知恢复（W2 前置必须项）

> 设计：`ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`；可行性：`ai-dev/analysis/2026-08/2026-08-01-llm-error-mapping-feasibility-analysis.md`
> **为何是 W2（尤其 W2-4 ProviderFailoverQueue）的前置**：L3 的 `IRetryPolicy`/`LlmErrorClassifier` 已落地但只能按 HTTP 状态粗分类，`QUOTA_EXCEEDED`/`AUTH_INVALID` 从未被生产——W2-4 的智能故障转移没有错误分类信号就无法决定"切 provider"还是"等待"。本节先把信号通路打通。
>
> **架构约定**：错误规范化在 `ChatServiceImpl` 内经 `ILlmDialect` 完成（与成功响应 `parseResponse` 同构），结果放 `ChatResponse` 新字段（`errorClassification`/`retryAfterMs`）。**不用装饰器、不用新异常类型**——ChatServiceImpl 职责即经 dialect 规范化 I/O，错误是另一种输出。响应级错误（拿到 HTTP 响应）→ ChatResponse；传输级错误（无响应）→ 仍抛异常。
>
> **已落地**（core 配置/模型层）：`llm.xdef` schema（`<errorResponse>`/`<errorMappings>`/`<classifyError>`）、default/claude/gemini/azure/ollama 五个 provider 的 `<errorMappings>` 配置、codegen 模型 + `TestLlmErrorMapping` 配置加载测试。**W2e-0..3 信号通路前半段 + RATE_LIMITED floor 已落地**（plan `2026-08-01-1440-1`）：`ErrorClassification` 迁到 `io.nop.ai.api.chat`（nop-ai-api 最低层，使 `ChatResponse` 可引用、信号通路全程同一类型）、`ServerEventPublisher` 挂响应头、`ChatResponse` 三字段、`parseErrorResponse`、`ChatServiceImpl` 错误路径返回错误 ChatResponse、`LlmCallCoordinator` `!isSuccess()` 读分类进入 RETRY、`StandardRetryPolicy` RATE_LIMITED floor。

- [x] W2e-0（nop-http 前置）`ServerEventPublisher` 把响应头挂到抛出的异常（流式路径取 `Retry-After` 头的前提；未完成则流式仅支持 body 级 Retry-After，已知缺口）
- [x] W2e-1（nop-ai-api）`ChatResponse` 增加 `errorClassification`/`retryAfterMs`/`httpStatus` 字段（沿用既有 `error`/`errorCode`/`isSuccess()`）
- [x] W2e-2（nop-ai-core）`ILlmDialect` 新增 `parseErrorResponse`（消费 `<errorMappings>` 规则表 first-match，与 `parseResponse` 对称）；`ChatServiceImpl` 非 200（非流式读 body+头）+ 流式 `aggregateStreamToResponse.onError`（读 `ARG_BODY`）都调它 → 返回带 `errorClassification` 的错误 `ChatResponse`（不抛）
- [x] W2e-3（nop-ai-agent）`LlmCallCoordinator` 重试循环改造：`!response.isSuccess()` 从终止路径升级为读 `errorClassification` 进入重试决策（今天 `:179-187` 不重试）；`RetryContext` 增加 `retryAfterMs`
- [x] W2e-4（nop-ai-agent）`StandardRetryPolicy` 行为变更：`QUOTA_EXCEEDED`/`AUTH_INVALID` → FALLBACK（按设计 §3.6 方案 b 路由账号链）；~~`RATE_LIMITED` 用 Retry-After 作 floor（`delay=retryAfterMs+jitter`）~~ ✅ 已落地（plan `2026-08-01-1440-1` Phase 3）；传输异常仍走 `LlmErrorClassifier` 启发式（plan `2026-08-01-1505-1` 落地 QUOTA/AUTH→FALLBACK）
- [x] W2e-5（nop-ai-agent）账号回退链（provider 级配置）+ 重试循环按 `errorClassification` 区分账号链 vs `IModelRouter.getFallback` 模型 tier 链；链耗尽 fail-loud（plan `2026-08-01-1505-1` 落地：`<accounts>` 配置 + `ChatOptions.accountKey` 跨层下沉 + `AccountChain` 游走 + 两通道分流）。**W2-4 ProviderFailoverQueue 消费本项的错误分类信号做跨 provider 故障转移**

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
