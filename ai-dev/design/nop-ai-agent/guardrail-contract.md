# Guardrail & NoOp Baseline Contract

> Status: active
> Plan: 2056 (Guardrail Safety & NoOp Baseline)
> Last Updated: 2026-07-31

## Purpose

Document the minimum guard configuration required for production safety, and
classify every SPI interface in `nop-ai-agent` by implementation maturity.

## Minimum Production Safety Guards

The following guards **must** be non-NoOp for minimum production safety.
Constructing an engine with any of these in insecure-default mode emits a
**WARN** via `DefaultAgentEngine.warnIfInsecureDefaults()`.

| Guard | Secure Default | Insecure (WARN) | Why |
|-------|---------------|-----------------|-----|
| `IToolAccessChecker` | `DefaultToolAccessChecker` | `AllowAllToolAccessChecker` | Blocks dangerous tools (bash, write-file, etc.) |
| `IPathAccessChecker` | `DefaultPathAccessChecker` | `AllowAllPathAccessChecker` | Blocks sensitive paths (~/.ssh/, /etc/, .env) |
| `IAuditLogger` | `Slf4jAuditLogger` | `NoOpAuditLogger` | Records tool decisions; silent = no audit trail |
| `IDenialLedger` | `DefaultDenialLedger` | `NoOpDenialLedger` | Counts denials and pauses sessions on threshold |
| `IPostDenialGuard` | `DefaultPostDenialGuard` | `PassThroughPostDenialGuard` | Blocks blind retries of denied operations |
| `ISecurityLevelResolver` | `DefaultSecurityLevelResolver` | `NoOpSecurityLevelResolver` | Classifies operations by security level |
| `IPermissionMatrix` | `DefaultPermissionMatrix` | `PassThroughPermissionMatrix` | Enforces channel × level permissions |
| `IApprovalGate` | `DefaultApprovalGate` | `AutoApproveGate` | Defense-in-depth deny for RESTRICTED operations |

## SPI Interface Classification

Every SPI interface in `nop-ai-agent` is classified into one of:

- **production-ready**: production-grade implementation shipped
- **partial**: functional but non-persistent (in-memory) implementation
- **partial-with-implementation**: basic implementation that works but is not production-grade
- **no-production**: only NoOp or test-only implementation

### Production-Ready

| Interface | Shipped Implementation | Notes |
|-----------|----------------------|-------|
| `IRetryPolicy` | `StandardRetryPolicy` | Configurable retry with backoff |
| `ICheckpointManager` | `FileBackedCheckpointManager` / `DBCheckpointManager` | Persistent checkpoint recording |
| `ICircuitBreaker` | `ThresholdBreaker` | Configurable failure threshold |
| `ISustainer` | `SisypheanSustainer` | Configurable sustain rounds |

### Partial (In-Memory Only)

| Interface | Shipped Implementation | Limitation |
|-----------|----------------------|------------|
| `IWriteIntentRegistry` | `InMemoryWriteIntentRegistry` | Cross-process detection is a future successor |
| `IFencingTokenService` | `DefaultFencingTokenService` | Single-JVM; cross-process fencing is a successor |
| `IContributionRegistry` | `InMemoryContributionRegistry` | Contributions lost on JVM restart |

### Partial-With-Implementation

| Interface | Shipped Implementation | Limitation |
|-----------|----------------------|------------|
| `ISkillProvider` | `FileSystemSkillProvider` | Basic filesystem provider; no DB support |
| `ISkillCurator` | `LLMCurator` | Basic LLM-based curator; no rule-based mode |
| `IGoalTracker` | `SessionGoalTracker` | In-memory per-session; no persistent tracking |

### No-Production (NoOp Only)

| Interface | Shipped Implementation | Notes |
|-----------|----------------------|-------|
| `IContentGuardrail` | `NoOpContentGuardrail` | No production alternative exists. INFO-level awareness at construction. |
| `IBudgetProvider` | `NoOpBudgetProvider` | No production alternative exists. INFO-level awareness at construction. |

## NoOp Awareness Design

Two INFO-level checks fire at construction when NoOp-only interfaces are detected:

1. **NoOpContentGuardrail**: "No production implementation available for
   IContentGuardrail — content safety is not enforced."
2. **NoOpBudgetProvider**: "No production implementation available for
   IBudgetProvider — execution budget is unlimited."

These fire at **INFO** (not WARN) because no production alternative exists to
swap to — WARN would create noise with no remediation path.

## NoOpSessionTakeoverLock Design Rationale

`NoOpSessionTakeoverLock` is **excluded** from INFO/WARN checks. Single-process
deployments rely on the engine's in-process `runningExecutions.putIfAbsent`
guard (plan 197). The takeover lock is incremental capability (NoOp → engine
walks existing path), not a security downgrade. The design decision is recorded
in the `NoOpSessionTakeoverLock` javadoc (lines 338-341).

## Recommended Minimum Guard Configuration for Production

```java
DefaultAgentEngine engine = DefaultAgentEngine.builder(chatService, toolManager)
    .toolAccessChecker(new DefaultToolAccessChecker())
    .pathAccessChecker(new DefaultPathAccessChecker())
    .auditLogger(new Slf4jAuditLogger())  // or custom DB logger
    .approvalGate(new DefaultApprovalGate())
    .securityLevelResolver(new DefaultSecurityLevelResolver())
    .permissionMatrix(new DefaultPermissionMatrix())
    .denialLedger(new DefaultDenialLedger())
    .postDenialGuard(new DefaultPostDenialGuard())
    .build();
```

All defaults above are already the engine's shipped defaults (constructor /
Builder), so the builder call without overrides satisfies the minimum
production guard configuration.

---

## 外部调研驱动的增量设计（2026-08-01：Guardrail 测试闭环 / Guideline 关系图 / BAIL）

> 来源：agent-survey（promptfoo Plugin+Grader / parlant Guideline 关系图+BAIL）。nop 已有运行时 guardrail 执行（security 6 层 + ContentOrigin），本节补"验收"与"关系建模"两个缺失维度。

### 增量 1：Guardrail 测试闭环（Plugin + Grader 分离）

nop 有 guardrail 执行但无系统化测试。增加 `GuardrailTestSuite`（对标 promptfoo 的 Plugin+Grader 分离）：

```
guardrail-test（测试时组件，非运行时）：
  ├── AttackPlugin（生成攻击用例）
  │   ├── SsrfPlugin / SqlInjectionPlugin / PromptExtractionPlugin
  │   ├── HallucinationPlugin / 行业垂直集（financial/medical）
  │   └── 策略层：base64/crescendo 二次变换（绕过简单防御）
  ├── Grader（rubric 打分判定拦截是否正确）
  │   └── Nunjucks 式 rubric 模板（Java: TemplateRenderer）
  └── Report（可度量、可回归：拦截率/漏报率）
```

- 复用 promptfoo 60+ 攻击类型为测试语料库
- 与运行时 guardrail 的关系：Plugin 造攻击 → nop guardrail 拦截 → Grader 判定拦截效果
- 形成"建设 + 验收"闭环（执行已有，补验收）

### 增量 2：Guideline 依赖/排除关系图（规则关系建模）

nop guardrail 当前是线性检查链。增加规则间关系（对标 parlant RelationalResolver）：

```
GuardrailRule（规则定义增加关系）：
  - dependsOn: 命中 A 自动拉入 B（上下文收敛）
  - excludes:  命中 A 排除 C（上下文收窄）
  - 关系图使规则靠结构收敛，而非 LLM 注意力
```

适用：企业合规复杂规则集（多规则冲突时靠结构决策）。

### 增量 3：BAIL 中断语义（硬阻断）

nop 拦截是"改写/拒绝"，增加 BAIL 语义（对标 parlant hooks.py + grok-build GateKind Stop）：

- `BAIL`：中断并丢弃当前响应（agent 循环立即终止该轮）
- 与 nop 现有 middleware 的关系：作为 middleware 链末端的硬阻断决策（`CALL_NEXT/RESOLVE/BAIL` 三态，nop 现有 middleware 返回拦截/放行，增加第三态）

### 与 hook-skill-engine 的边界

- guardrail-contract：规则定义 + 测试闭环 + 关系建模（本篇）
- hook-skill-engine：12 生命周期点 + skill 加载（已存在）
- 关系：BAIL 由 hook 生命周期点触发，规则评估在 security 层
