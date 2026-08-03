# 2249 Checkpoint 决策链场景化测试框架

> Plan Status: draft
> Last Reviewed: 2026-08-03
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md` §14.5；`ai-dev/analysis/agent-survey/2026-08-03-arbiteros-governance-kernel-deep-analysis.md` §3.9
> Related: `ai-dev/design/nop-ai-agent/guardrail-contract.md`（增量 1 GuardrailTestSuite = 内容层测试范式参考）
> Revision: 2026-08-03 v2 — Round 1 对抗性审查发现 3 个 Blocker（chain 不可从 engine 提取、evaluate() 不返回 layer、prior 与 checkpoint 无关），本版按实际架构重写。

## Purpose

nop 已有**内容层** guardrail 测试（`GuardrailTestSuite`，测"这段字符串是否被 `IContentGuardrail.check()` 拦截"）和**组件级**单元测试（`TestSecurityCheckpointChain`/`TestDefaultToolAccessChecker` 等，测单个 checker 的孤立行为）。但**没有数据驱动的全链场景测试**——即"给定一个 tool call + 安全上下文配置（channel/principal/workDir），跑完 7 个 checkpoint 的完整 `SecurityCheckpointChain`，断言最终 ALLOW/DENY 及命中检查点"。

本计划吸收 ArbiterOS redteam 的**场景化对抗测试方法论**（非 `prior`/`current` 范式——Round 1 审查确认 nop checkpoint 不消费消息历史），为 nop 补上这一层：用 YAML 声明的 case（tool call + 安全上下文 + 期望决策），对 `AgentSecurityConsultation.buildCheckpointChain()` 构建的**完整 7-checkpoint 链**做数据驱动验收。

## Current Baseline

- **已有（内容层测试）**：`io.nop.ai.agent.guardrail.test.GuardrailTestSuite` + `AttackCase` + `GuardrailGrader` + 10+ corpus YAML（`_vfs/nop/ai/agent/guardrail-test/corpus/`）。测的是 `IContentGuardrail.check(direction, payload, ctx)` 对单条字符串的拦截——**纯函数测试**。
- **已有（组件级单元测试）**：`TestSecurityCheckpointChain`（测 chain 短路逻辑）、`TestDefaultToolAccessChecker`/`TestDefaultPathAccessChecker`/`TestDefaultPermissionMatrix`/`TestDefaultSecurityLevelResolver`/`TestDefaultApprovalGate` 等（各 checker 单元测试，孤立验证）。**这些测的是单个组件，不是完整链。**
- **已有（chain 构建入口）**：`AgentSecurityConsultation.buildCheckpointChain()`（`engine/AgentSecurityConsultation.java:124`）是 **public** 方法，构建含 7 个 checkpoint 的 `SecurityCheckpointChain`：postDenial → toolAccess → permission → pathAccess → layer2(securityLevel+matrix) → layer3(approvalGate) → conflict。依赖 `AgentSecurityConsultation` 构造器的 13 个组件（见 Phase 2）。
- **已有（审计侧信道）**：`AuditEvent`（`security/AuditEvent.java`）含 `matchedRule` 字段——每个 checkpoint 的 deny 路径写审计事件时带 `matchedRule`（如 `"layer1_tool_access"`、`"layer2_permission_matrix"`、`"layer3_approval_gate"`、`"layer3_post_denial_guard"`、`"layer1_permission_provider"`、`"layer1_path_access"`、`"write_intent_conflict"`）。**`SecurityCheckpointChain.evaluate()` 只返回 `Decision`（ALLOW/DENY/DENY_AND_BREAK）枚举，不返回 layer/rule——但命中的 layer 可经 `IAuditLogger` 侧信道捕获。**
- **缺失**：数据驱动的全链测试。现有单元测试是代码写死的断言，不是可批量执行的 YAML 语料 + 度量 report。

### 关键架构约束（Round 1 审查发现，决定本计划形态）

1. **`SecurityCheckpointChain` 不可从 `DefaultAgentEngine` 提取**——它是 `ReActAgentExecutor` 的 `private final` 字段（每次执行时构建），engine 无 getter。**harness 必须直接构造 `AgentSecurityConsultation`**（用 `Default*` 组件装配），而非"从 engine 提取"。
2. **`evaluate()` 不是纯函数**——checkpoint deny 路径有副作用：写 `AuditEvent`、调 `hookInvoker.publishEvent()`、突变 `AgentExecutionContext`（addMessage/setStatus）、记 `denialLedger`/`postDenialGuard`。**harness 必须每 case 构造全新 context + 用 collecting/no-op 变体隔离副作用。**
3. **checkpoint 不消费消息历史**——`SecurityCheckpoint.CheckContext` 无 message 字段；7 个 checkpoint 全部针对**单个 tool call + 静态配置**评估。**ArbiterOS 的 `prior`/`current` 范式不适用**——nop 的 case 是"单 call + 安全上下文"，不是"多轮历史 + 待判定"。

## Goals

- nop 具备**数据驱动的全 checkpoint 链场景测试**：YAML case 声明 tool call + 安全上下文 + 期望决策 → harness 构造完整 7-checkpoint 链 → 跑 `evaluate()` + 捕获 `AuditEvent.matchedRule` → 断言 ALLOW/DENY + 命中检查点。
- 交付 nop-native 场景语料（适配 nop checkpoint 实际能测的决策点）。
- 与既有 `GuardrailTestSuite`（内容层）+ 组件级单元测试**并存**——三者测不同层次。

## Non-Goals

- **不改 checkpoint 引擎代码**——`AgentSecurityConsultation`/`SecurityCheckpointChain`/7 个 checkpoint 的实现不动。harness 只**调用**既有 public API。
- **不做 `prior`/`current` 多轮上下文测试**——Round 1 审查确认 checkpoint 不消费消息历史。多轮场景测试（如 post-denial-guard 跨 call 的 fingerprint 累积）留 successor。
- **不做 observe/dry-run mode**（§14.2 ③）——独立低优先增强。
- **不测 LLM 推理质量**——测的是 checkpoint 决策（确定性程序逻辑）。

## Scope

### In Scope

- `CheckpointTestCase` 值类型（tool call：toolName + args；安全上下文：channelKind + principal + workDir + sessionId；期望：decision + 可选 matchedRule）
- `CheckpointTestHarness`（直接构造 `AgentSecurityConsultation` + `Default*` 组件 → `buildCheckpointChain()` → 构造 `CheckContext` → `evaluate()` + `CollectingAuditLogger` 捕获命中规则）
- nop-native 场景语料 YAML（覆盖 7 个 checkpoint 的 deny 路径）
- runner + report

### Out Of Scope

- 多轮 prior/current 范式（checkpoint 不支持，留 successor）
- call-agent 委派权限继承测试（发生在 `CallAgentExecutor` 内部，不在 checkpoint 链——Round 1 审查 M2 确认）
- LLM-as-judge、CI 硬门禁阈值

## Execution Plan

### Phase 1 - Case 模型 + Harness 契约设计

Status: planned
Targets: `ai-dev/design/nop-ai-agent/guardrail-contract.md`（新增增量 4）

- Item Types: `Decision`

- [ ] 设计 `CheckpointTestCase` 数据模型：
  - `toolCall`：`toolName`（String）+ `args`（Map<String,Object>，含 path 等路径参数）
  - `securityContext`：`channelKind`（可选，WEBUI/API/DM/GROUP/null）+ `principal`（可选，role/userId）+ `workDir`（可选，影响 pathAccess 检查）+ `sessionId`（String）
  - `expected`：`decision`（ALLOW / DENY / DENY_AND_BREAK）+ `expectedMatchedRule`（可选 String，对应 `AuditEvent.matchedRule` 值）
  - `category`（String，场景分类）+ `description`
- [ ] 设计 7-checkpoint 到 `matchedRule` 值的映射表（**从 `AgentSecurityConsultation.java` 实际 AuditEvent 构造行提取**，Round 2 审查发现初版猜测有误）：
  - postDenial → `"layer3_post_denial_guard"`（`:139,151`）
  - toolAccess → `"hardcoded_deny_list"`（来自 `DefaultToolAccessChecker.java:23` `ToolAccessResult.denyByRule("hardcoded_deny_list", ...)`）
  - permission → 动态值（`rule.getId()` from matched permission rule，或 null 当 AllowAll）
  - pathAccess → `"path_access_checker"`（`:221`）+ 子规则来自 `DefaultPathAccessChecker`（`"sensitive_path_prefix"`/`"sensitive_path_env_file"`/`"sensitive_path_filename"`/`"path_traversal_defense"`/`"sensitive_path_symlink"`）
  - layer2 → `"layer2_permission_matrix"`（`:240,457`）
  - layer3 → `"layer3_approval_gate"`（`:260,501`）
  - conflict → `"layer2_conflict_strategy"`（`:279,608`）
  - （附属）denialLedger 阈值触发 → `"layer3_denial_ledger"`（`:354`）
- [ ] 在 `guardrail-contract.md` 新增"增量 4：决策链场景测试（CheckpointTestHarness）"章节，固化 case 模型 + harness I/O 契约 + matchedRule 映射表

Exit Criteria:

- [ ] `guardrail-contract.md` 新增章节，含 `CheckpointTestCase` 字段表 + harness I/O 契约 + **7-checkpoint matchedRule 映射表**（从 `AgentSecurityConsultation.java` 实际代码提取，非猜测）
- [ ] 章节明确：harness **直接构造 `AgentSecurityConsultation`**（非从 engine 提取）；case 模型**无 prior 字段**（checkpoint 不消费消息历史）
- [ ] No owner-doc update required（`guardrail-contract.md` 即 owner doc）

### Phase 2 - Harness 实现（含 AgentSecurityConsultation 装配助手）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/test/`（或新建 `checkpoint/` 子包）

- Item Types: `Proof`

- [ ] 实现 `CheckpointTestCase` 不可变值类型
- [ ] 实现 `CheckpointTestHarness`：
  - **装配助手** `buildDefaultConsultation()`：用 `AllowAllPermissionProvider`（**注意：不是 `DefaultPermissionProvider`——后者无规则时 deny-all，会使所有 case 命中 permission checkpoint 而非目标层**；`AllowAllPermissionProvider` 是 `DefaultAgentEngine.java:143` 的实际 engine 默认）+ 其余 `Default*` 组件（DefaultToolAccessChecker/DefaultPathAccessChecker/DefaultSecurityLevelResolver/DefaultPermissionMatrix/DefaultApprovalGate/DefaultDenialLedger/DefaultPostDenialGuard/InMemoryWriteIntentRegistry/FailFastStrategy）+ 新建 `CollectingAuditLogger`（提取为 `guardrail/test/CollectingAuditLogger.java` production 类——当前只在 5 个测试文件中以 inner class 复制粘贴存在）+ `AgentHookInvoker`（用 `new AgentHookInvoker(emptyRegistry, null)`——null publisher 可接受，`publishEvent` 对 null 有防御）+ `AgentToolPlanResolver`（需 `IToolManager`——用 stub 模式，参考 `TestConflictDetectionDispatchPath.java:137-161` 的 `stubToolManager()`）构造 `AgentSecurityConsultation` → 调 `buildCheckpointChain()`
  - **CheckContext 构造**：`SecurityCheckpoint.CheckContext.create()` 需要 `agentName` + `AgentModel`（含 workDir）——harness 用最小 `AgentModel`（仅设 workDir）+ case 提供的 agentName 构造
  - **执行** `runCase(CheckpointTestCase)`：从 case 构造 `CheckContext` → 调 `chain.evaluate(ctx)` → 从 `CollectingAuditLogger` 提取 `AuditEvent.matchedRule` → 产 `CheckpointTestResult`
  - **隔离**：每 case 构造全新 `AgentExecutionContext`（无跨 case 状态泄漏）+ 全新 consultation（guard/ledger 重置）
- [ ] 实现 `CheckpointTestResult`（actual decision + actual matchedRule + pass/fail 判定）
- [ ] 实现 `CheckpointTestReport`（通过率 / deny 率 / per-category / per-checkpoint-layer 度量）

Exit Criteria:

- [ ] `CheckpointTestCase`、`CheckpointTestHarness`、`CheckpointTestResult`、`CheckpointTestReport` 四个类存在
- [ ] **装配助手成功构造 `AgentSecurityConsultation` 并调 `buildCheckpointChain()` 返回非 null chain**（验证 13 个依赖全部可装配）
- [ ] **接线验证**：harness 调用真实 `chain.evaluate()`（非 mock），且 `CollectingAuditLogger` 能捕获 deny 路径的 `AuditEvent.matchedRule`——smoke test：一个已知 DENY case 产生 DENY + 非空 matchedRule
- [ ] **无静默跳过**：harness 在 `evaluate()` 抛异常或 chain 为 null 时显式失败
- [ ] **副作用隔离验证**：连续跑两个 case，第二个 case 的 `postDenialGuard`/`denialLedger` 状态不被第一个污染（验证全新 consultation per case）
- [ ] 新增测试 `TestCheckpointTestHarness`（≥ 2 case：已知 ALLOW + 已知 DENY，验证 result 正确 + matchedRule 捕获）
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过

### Phase 3 - nop-native 场景语料

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/checkpoint-test/corpus/`

- Item Types: `Proof`

- [ ] 设计场景目录（**只覆盖 checkpoint 链实际能测的决策点**，排除 Round 1 确认不可测的 call-agent-delegation，排除 Round 2 确认不可触发的 security-level——`DefaultContentTrustEvaluator` 对 AGENT_GENERATED 恒返回 trusted → shell.exec 只到 ELEVATED → DefaultApprovalGate 批准 ELEVATED → 无 DENY）：
  - `tool-deny-list`：deny-list 工具（bash/write-file/delete-file）→ DENY + `"hardcoded_deny_list"`
  - `path-sensitive`：敏感路径（~/.ssh/**/.env）→ DENY + `"path_access_checker"`（子规则 `"sensitive_path_prefix"` 等）
  - `path-traversal`：`..` 目录穿越 → DENY + `"path_access_checker"`（子规则 `"path_traversal_defense"`）
  - `channel-matrix`：channel=group + ELEVATED 操作 → DENY + `"layer2_permission_matrix"`
  - `write-intent-conflict`：预填充 WriteIntentRegistry + 冲突路径 → DENY + `"layer2_conflict_strategy"`
  - `benign`：正常工具 + 安全路径 → ALLOW（对照组）
- [ ] 每场景 ≥ 3 case（含 safe/unsafe 混合），total ≥ 18 case
- [ ] `CheckpointTestCaseLoader`（YAML → `CheckpointTestCase`，复用 `ResourceHelper` + `JsonTool.parseYaml`）

Exit Criteria:

- [ ] ≥ 5 场景目录，total ≥ 18 case
- [ ] 每个 case 的 `toolName`/`args`/`securityContext` 字段完整且可被 harness 消费
- [ ] **场景覆盖验证**：至少有 case 命中 ≥ 4 个不同 `matchedRule` 值（`hardcoded_deny_list` + `path_access_checker` + `layer2_permission_matrix` + `layer2_conflict_strategy`——postDenial 和 security-level 因架构约束不可在单 call 触发，已在 Non-Goals/Deferred 记录）
- [ ] `CheckpointTestCaseLoader` 能加载全部 YAML 无解析错误
- [ ] `TestCheckpointTestCaseLoader` 通过

### Phase 4 - Runner + 端到端集成

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/test/`；`src/test/`

- Item Types: `Proof`

- [ ] 实现 `CheckpointTestRunner`（批量加载 → 逐条跑 harness → 聚合 report）
- [ ] 端到端测试 `TestCheckpointDecisionEndToEnd`：YAML case 加载 → harness（Default* 装配）→ runner → report，断言 safe 场景大多 ALLOW、unsafe 场景大多 DENY、per-checkpoint 度量非空
- [ ] **端到端验证**：从 YAML 到 report 的完整路径跑通

Exit Criteria:

- [ ] `CheckpointTestRunner` 存在且批量执行
- [ ] `TestCheckpointDecisionEndToEnd` 通过；report 含 per-category + per-matchedRule 度量
- [ ] **Anti-Hollow**：case → loader → harness → chain.evaluate() → CollectingAuditLogger → report 全链路在端到端测试中验证连通
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过

### Phase 5 - 基线验收 + 文档同步

Status: planned
Targets: `ai-dev/design/nop-ai-agent/guardrail-contract.md`；`ai-dev/logs/`

- Item Types: `Proof` | `Follow-up`

- [ ] 用 runner 对 Default* checkpoint 跑全量语料，记录基线 report
- [ ] 分析漏报/误报（unsafe 被 ALLOW / safe 被 DENY），逐条标注"设计意图"或"需后续 plan"
- [ ] `guardrail-contract.md` 增量 4 补"基线结果摘要"
- [ ] `ai-dev/logs/` 更新

Exit Criteria:

- [ ] 基线 report 产出并记录
- [ ] 漏报/误报逐条裁定
- [ ] `guardrail-contract.md` 含基线摘要
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] `CheckpointTestHarness` + ≥ 20 nop-native case + runner + 端到端测试全部 landing
- [ ] harness 直接构造 `AgentSecurityConsultation`（不改 engine 代码），7-checkpoint 链全可装配
- [ ] `CollectingAuditLogger` 侧信道成功捕获 matchedRule（覆盖 ≥ 5 个不同 checkpoint）
- [ ] 基线 report 产出，漏报/误报逐条裁定
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow：case → loader → harness → chain → AuditLogger → report 全链路连通
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过

## Deferred But Adjudicated

### 多轮 prior/current 场景测试

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Round 1 审查确认 `SecurityCheckpoint.CheckContext` 无 message 字段——checkpoint 不消费消息历史。多轮测试（如 post-denial-guard 跨 call fingerprint 累积）需 harness 支持"顺序重放多 case 建立 guard/ledger 状态"，是独立增强。
- Successor Required: no

### observe/dry-run mode（§14.2 ③）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: §14.2 ③ 低优先增强。本框架是 observe-mode 的验证基础设施——未来可用本框架对比 enforce vs observe。
- Successor Required: no

### CI 硬门禁阈值

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版可度量记录，门禁策略由消费方裁定。
- Successor Required: no

## Non-Blocking Follow-ups

- call-agent 委派权限继承的场景测试（需在 `CallAgentExecutor` 层测，非 checkpoint 链）
- 语料持续扩展（随实战发现新场景）

## Closure

Status Note: (待 closure)
Completed: (待 closure)

Closure Audit Evidence:
- (待独立子 agent closure-audit 后填写)
