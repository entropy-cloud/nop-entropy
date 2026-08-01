# nop-ai-agent Harness Evolution Roadmap

> Status: active
> 来源：46 份外部项目调研（`ai-dev/analysis/agent-survey/2026-08-01-*`）的 10 类增量共识。nop-ai 全面超越外部实现，增量全部以 nop 原生方式吸收（XDEF 声明式 + nop-task 复用 + Java 结构化），不引入外部依赖。

## Work Items

### W1. Plan 运行时门控（最高优先）
- [x] W1-1 plan-dsl：AgentPlanPhase 增加 Gate 门控（on-fail retry/block/escalate + max-retries + require-explicit-verdict）
- [x] W1-2 plan-dsl：任务依赖增加 Trigger Rule（all_success/one_success/none_failed_min_one_success/all_done）
- [x] W1-3 plan-dsl：AgentPlanTaskModel 增加 dependsOn（DAG 依赖，nop-task GraphStepAnalyzer 环检测）
- [x] W1-4 plan 运行时：PlanReplanner（停滞检测 → 阶段回退/任务拆分/失败升级，幂等决策）
  - **收口（2026-08-01，plan `2026-08-01-1905-1`）**：W1-4 全部落地。决策空间（CONTINUE/ESCALATE/ROLLBACK_PHASE/SPLIT_TASK）全部可达 + 全部有真实 enactment；决策载荷结果对象 `ReplanDecisionResult`；构造期 `ReplanPolicy`（避免 Protected Area 模型变更）；executor 可恢复重入（ROLLBACK 不终止 + phase 回跳 + cycle-safety bound）；SPLIT 集成面（scheduler 3-arg 结构来源 = 冻结 ∪ overlay + executor phase 过滤含运行时子节点，子任务非死节点）；冻结模板永不突变；零回归。ABORT 留 successor（enactment 抛 UOE）。design §14.4.2/§14.4.3 已回写。前置 partial-landing（plan `2026-08-01-1505-2`：停滞检测 + ESCALATE/CONTINUE）已被本 plan 收口。
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
- [x] W2-1 checkpoint 增加 wait_for 条件 JSONB（WAIT_FOR 长等待原语：挂起不占线程 → 条件满足唤醒恢复）
- [x] W2-2 checkpoint 增加 idempotency_key 列 + 唯一约束（hash(toolName+callId+输入指纹)，restore 时发散检测）（plan `2026-08-01-1905-2` 落地：Checkpoint 字段 + ORM 列 + unique index + sha256 hash + 全序列化同步 + restore 发散检测拒绝+降级 session 重放，零回归）
- [x] W2-3 三级失败升级（质量失败 max_aegis_rejections / 停滞失败 stale_task_max_retries / 基础设施失败 max_dispatch_retries）（plan `2026-08-01-1437-2` 落地：plan 层层归属裁定 A + `FailureType` 枚举 + `TaskOutcome` 携带 typed failure + `FailureEscalationPolicy` 构造期阈值（disabled 默认零回归）+ `PlanExecutionState` per-task typed 计数器 + `PlanExecutor` 三级升级动作（task failed）+ Contribute 聚合模型（既有 recordError→REPEATED_ERRORS 管道，无双计）+ 30 测试）
- [x] W2-4 ProviderFailoverQueue（跨 provider 有序故障转移 P1→P2→P3 + failover_switch 去重）（plan `2026-08-01-1905-3` 落地：新 manifest xdef `llm-failover.xdef` + codegen `LlmFailoverConfig`/`LlmFailoverProviderModel` + `ProviderFailoverQueue`（per-provider 冷却去重 + 可注入 `LongSupplier` 时钟）+ `ProviderFailoverChain`（向前游走）+ `LlmCallCoordinator` 第三通道集成（账号链耗尽升级→切下一 provider，重置 accountChain/circuit key/attempt，全部耗尽 fail-loud）+ 16 测试。零回归：无 provider 链配置时账号链耗尽仍 fail-loud；shipped NoOp queue 去重维度 opt-in，切换维度 config-driven）
- 参考：`2026-08-01-hatchet-durable-execution-analysis.md`、`2026-08-01-grok-build-deterministic-replay-analysis.md`、`2026-08-01-mission-control-control-plane-analysis.md`、`2026-08-01-cc-switch-provider-circuit-breaker-analysis.md`

### W3. Middleware 增量（高优先）
- [x] W3-1 双层中间件：执行级（每次工具/模型尝试，retry 时重新评估安全检查）
- [x] W3-2 声明式 filter chain：DSL 声明有序 filter ID 列表 + input/output 双链分离
  - **收口（2026-08-01，plan `2026-08-01-1437-4`）**：W3-2 全部落地。`agent.xdef` 新增 `<filter-chain>`（`<filter-definitions>` + `<input-filters>` + `<output-filters>`，codegen 生成 `AgentFilterChainModel`/`FilterDefModel`/`FilterRefModel`）；D1 方案 B（agent 内 `<filter-definitions>` 自包含 id→impl，复用 `ClassHelper.safeNewInstance`，零 IoC 改动）；D2 默认 PRE_CALL/POST_CALL 单次触发 + `points` 覆盖（避免 PRE_REASONING/PRE_ACTING 的 N+M+K 多触发）；D3 声明式 filter 在前 + 跨机制同 impl class 同点快速失败（`ERR_AGENT_FILTER_DUPLICATE_DECLARATION`）；`FilterChainResolver` + `ResolvedFilterChain`（声明侧 refs 与执行侧 resolved 同步、不可变）；`AgentExecutorResolver` 装配集成（声明式先注册、代码类后注册，重复检测）；24 测试（`TestFilterChainResolver` 15 + `TestAgentFilterChainWiring` 10，含端到端 `resolveExecutor → execute → MiddlewareChain → filter 执行` + 单次触发证明 + 洋葱顺序证明 + DSL parse 验证）。design §5.2 final。零回归（3158 测试 0 failures）。
- 参考：`2026-08-01-hive-dual-middleware-analysis.md`、`2026-08-01-plano-declarative-filter-chain-analysis.md`

### W4. 上下文工程增量（中优先）
- [x] W4-1 引用式压缩双轨（shortRef{type,path,range,hash} + readRef 工具，按内容类型分流）
  - **收口（2026-08-02，plan `2026-08-02-0900-1`）**：W4-1 全部落地。新增引用式（无损指针）压缩路径，与既有摘要式（有损）按内容类型分流共存。toolkit 新增 `ICompactionArchive`/`ICompactionArchiveReader`（归档接口归属 toolkit，裁定 B）+ `ShortRef`/`ShortRefHasher`（`[SHORT_REF type=.. path=.. range=.. hash=sha256:<hex>]` 严格可解析格式 + SHA-256）+ `ReadRefExecutor`（`read-ref` 工具：按 hash 读回 + 重算校验 + 不一致/缺失 fail-loud 显式错误）+ `read-ref.tool.xml`/bean 注册。agent 新增 `InSessionCompactionArchive`（per-session hash 寻址、`putIfAbsent` 去重、null/空 fail-fast）+ `ReferenceCompactionStrategy`（按裁定 A 三信号识别可保真 tool-response：role + 来源工具 `typeForTool` 映射 + 长度阈值；近期 tool result 保留原文）。接线（裁定 G）：`AgentSession` 持 archive 实例（lazy init）→ `AgentCompactionCoordinator.performCompaction` 注入 CompactionContext（写侧）→ `AgentToolExecuteContext.getCompactionArchiveReader()` 覆写（读侧，default UOE 桥处理 22 处实现爆炸半径，裁定 C）。`CompactionResult` 不扩展（裁定 D）。design §8.2 F 修正 escalation 顺序为 Reference→Layer1 micro→Layer2→Layer3（micro 有损会摧毁长内容原文，引用式必须在其前）。29 新测试（`TestInSessionCompactionArchive` 8 + `TestReferenceCompactionStrategy` 10 + `ReadRefExecutorTest` 8 + `TestReferenceCompactionEndToEnd` 3，含端到端 `performCompaction→shortRef→read-ref 读回` Anti-Hollow）。零回归（3179 测试 0 failures）。独立 closure audit PASS（CAN CLOSE）。
- [x] W4-2 压缩前 snapshot 归档 + 压缩比记录（originalSize/compactedSize，失败保留原文）
  - **收口（2026-08-02，plan `2026-08-02-0900-2`）**：W4-2 全部落地。为压缩管线补上"可逆性 + 可度量性 + 失败安全"。agent/session 新增 `ICompactionSnapshotArchive`（per-compaction-event、`snapshotId` 寻址整段历史，**独立于** W4-1 的 per-content hash 寻址）+ `InSessionCompactionSnapshotArchive`（in-session 内存、会话级释放、null/空 fail-fast、defensive copy 保可回溯）。`AgentSession` 持 snapshotArchive 实例（lazy init，与 W4-1 archive 平行）。`CompactionResult` 扩展 `originalSize`/`compactedSize`（消息条数维度权威度量，裁定 D：`retainedMessageCount` 降为 legacy alias）+ 既有 `snapshotId` 非 null 化；保留 5/6 参构造器（新字段 default = retainedMessageCount）+ 新 8 参构造器；equals/hashCode/toString 同步。接线（裁定 A+E）：`AgentCompactionCoordinator.performCompaction` PRE_COMPACT 后、compact() 前 archive 原文 → snapshotId 经 `CompactionContext`（新增 snapshotId 字段，`rebuildContext` 透传）流入 `PipelineCompactor` 唯一构造点（success/no-reduction 两分支均填 `ctx.getSnapshotId()` + originalSize/compactedSize）。失败语义（裁定 F）：coordinator 层 `compact()` 加 try-catch（不冒泡中断 agent + 保留 archive + LOG.warn 含 snapshotId）+ compactedMessages==null 分支 + 无 token 减分支均补 LOG.warn（含 snapshotId），消除既有静默跳过。Phase 3：COMPACTION checkpoint `compactSummary` 扩展为含 snapshotId + 两维度压缩比（token ratio + message-count ratio）。**显式修正 design §8.3 旧文「归档即 checkpoint」自相矛盾**为"归档 ≠ checkpoint snapshot.json（§5.4 successor）"，并在 reliability §5.4 交叉引用处标注边界。文档化三套 snapshotId 命名空间（CheckpointSnapshot/SessionSnapshot/compaction-archive）。20 新测试（`TestInSessionCompactionSnapshotArchive` 9 + `TestCompactionSnapshotArchive` 11，含端到端 `performCompaction→archive→compact→结果携带 snapshotId→COMPACTION compactSummary 含两维度比→get(snapshotId) 取回原文` Anti-Hollow + 失败路径 + 无 archive 路径 + 接线验证）。零回归（3199 测试 0 failures）。独立 closure audit PASS（CAN CLOSE）。
- 参考：`2026-08-01-context-mode-compaction-analysis.md`、`2026-08-01-beads-versioned-graph-memory-analysis.md`

### W5. Guardrail 验收闭环（中优先）
- [x] W5-1 GuardrailTestSuite（AttackPlugin 生成 + Grader rubric 打分分离，60+ 攻击类型语料）
  - **收口（2026-08-02，plan `2026-08-02-0421-1`）**：W5-1 全部落地。新增 test-time 组件包 `io.nop.ai.agent.guardrail.test`（非运行时，`AgentPromptAssembly` 从不引用）：`AttackPlugin` SPI（`ListAttackPlugin`/`CorpusAttackPlugin`）+ `AttackCase`（不可变，8 字段）+ `AttackTransform` 装饰器（base64/crescendo，expectedBehavior 不变走完整链）+ `GuardrailGrader` SPI（`DefaultGuardrailGrader` 确定性判定矩阵）+ `GradeResult`/`Verdict` + `GuardrailTestReport`（不可变，拦截率/漏报率/误报率/per-category，`build()` 工厂聚合）+ `GuardrailTestSuite` orchestrator（Plugin→`IContentGuardrail.check()`→Grader→Report）+ `CorpusLoader`（`ResourceHelper`+`JsonTool.parseYaml`，与 `FileSystemSkillProvider` 同构）。65 攻击用例（10 类别：prompt_injection/extraction/role_hijack/exfiltration/jailbreak/hallucination/invisible_char/privilege_escalation/industry_financial/medical）+ 12 良性对照，ship 到 `_vfs/nop/ai/agent/guardrail-test/corpus/`。接线（Anti-Hollow）：`CountingGuardrail` 证明 `check()` 运行时逐用例调用真实 `PromptInjectionGuardrail`；null→fail-loud；变换变体走完整链（checkCount==3）。**PromptInjectionGuardrail 基线**：65 atk→44 blk/21 lk（blockRate 0.677），12 benign→fpr 0.0；目标类（prompt_injection/extraction/role_hijack/exfiltration/invisible_char）全 blockRate 1.0；非目标类（jailbreak/hallucination/privilege_escalation）全 leakRate 1.0（recorded capability boundary，非缺陷）。修正 pre-existing doc drift（SPI 表承认 `PromptInjectionGuardrail` + NoOp awareness WARN、`IContentGuardrail.java` @apiNote）。design `guardrail-contract.md` §增量 1 升级 final。52 新测试（含 6 E2E Anti-Hollow）。零回归（3268 测试 0 failures，runtime 执行路径零改动）。独立 closure audit PASS（CAN CLOSE）。
- [ ] W5-2 Guideline 依赖/排除关系图（规则关系建模，靠结构收敛）
- [x] W5-3 BAIL 中断语义（middleware 第三态：中断并丢弃响应）
  - **收口（2026-08-02，plan `2026-08-02-0900-3`）**：W5-3 全部落地。新增 `HookResult.BailResult`（第四态，`HookResult.java` 内静态嵌套子类，受 package-private 构造约束）+ `isBail()`（向后兼容，既有三态语义不变）。把"丢弃输出+重新提示"从执行器硬编码（`checkOutputGuardrail`，仅 promptAssembly 可触发）**泛化为中间件可返回的标准态**（裁定 A：BAIL 在前共存，BAIL 命中跳过 checkOutputGuardrail）。POST 侧返回值丢弃缺陷修复（BAIL 维度）：`POST_REASONING`/`POST_CALL` 的 `executeWithMiddleware` 返回值现被捕获 + 检查 `isBail()`（裁定 F 零回归：POST 侧既有 hook 均返 Pass，仅消费 isBail 不消费 isVeto/isReenter）。POST_REASONING BAIL → 不作用该轮响应（跳过 tool_calls 执行 + 不视为最终答案）+ 重新提示（`setCurrentIteration(+1)` 与 checkOutputGuardrail 对齐）+ per-request bail cap（`MAX_POST_REASONING_BAILS=3`，超限 fail-loud，裁定 D：不与 checkOutputGuardrail 共享计数）。POST_CALL BAIL → `AgentExecutionContext.bailReason` 经 `AgentExecutionResult.fromContext` 透传到调用方（`result.getBailReason()!=null` 判阻断）；流式已发 chunk 不可撤回（显式接受限制，裁定 E）；`EXECUTION_COMPLETED` payload 加 additive `guardrailBlocked` 标记。BAIL 在非 POST 点 fail-loud（`AgentHookInvoker.validateBailPoint`，覆盖 middleware + hook 两路径；执行级中间件返 BailResult 同样 fail-loud）。design §2.5 回写 BAIL 语义、§5.4 新增 A–F 六条裁定、§三:156 supersede "不修改 HookResult 密封层级"；guardrail-contract 增量 3 标 final。18 新测试（`TestBailInReActLoop` 9 含五条必需路径 + `TestBailEndToEnd` 4 含端到端 + 四方语义区分 + Anti-Hollow wiring proof + `TestHookResult` 5 BailResult 类型）。零回归（3216 测试 0 failures）。独立 closure audit PASS（CAN CLOSE）。
- 参考：`2026-08-01-promptfoo-redteam-eval-analysis.md`、`2026-08-01-parlant-conversation-control-analysis.md`

### W6. DSL 组合层（低优先）
- [ ] W6-1 Recipe 配方组合层（recipe.xdef：prompt 模板 + 工具集 + 模型配置 + hooks 快照，可叠加）
- 参考：`2026-08-01-goose-provider-hook-recipe-analysis.md`

## 完成定义

- W1-W6 全部 todo → planned（有 execution plan）→ done（closure audit 通过）
- 每项增量以 nop 原生方式实现（XDEF 声明式 / nop-task 复用 / Java 结构化）
- 不引入任何外部开源依赖（仅吸收语义增量）
- 更新 `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` Phase Status（追加 L6. Harness Evolution 层）
