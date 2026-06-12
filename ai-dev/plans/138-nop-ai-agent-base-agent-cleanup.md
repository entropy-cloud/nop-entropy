> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L1-14
> **Last Reviewed**: 2026-06-12
> **Source**: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 L1-14, `ai-dev/design/nop-ai-agent/00-vision.md` §三 约束表第 2 行
> **Related**: 134-nop-ai-agent-engine-actor-entry.md (L1-1 ✅), 129-nop-ai-agent-enum-unification.md (L0-2 ✅)

# 138 BaseAgent 删除

## Purpose

删除 `BaseAgent` 空壳类。`BaseAgent`（`io.nop.ai.agent.agents.BaseAgent`）是一个 7 行空壳类，仅持有 `IAiMemoryStore memory` 字段，无执行逻辑，未被任何工作项追踪，也未被 `DefaultAgentEngine` 或任何其他组件引用。设计原则（`00-vision.md` §三 约束表第 2 行："Agent 即配置，Engine 即执行"）明确 Agent 是配置对象，执行由 `IAgentEngine` + `IAgentExecutor` 负责。`BaseAgent` 与此原则矛盾——它暗示 Agent 应该是持有状态的执行体。因此裁定：**删除**。

### 决策理由

1. **与设计原则矛盾**：`00-vision.md` §三 约束表第 2 行 "Agent 即配置，Engine 即执行" 明确 Agent 不持有执行逻辑和状态。`BaseAgent` 试图作为 Agent 的运行时基类，违反此约束。
2. **无引用**：grep 搜索确认，`nop-ai-agent` 模块内无任何 Java 代码 import 或继承 `BaseAgent`。`nop-kernel/nop-core/src/test/java/io/nop/core/lang/json/TestJsonTool.java:150` 中出现 `"BaseAgent"` 字符串，但这仅是 JSON 测试数据中的字面量，不是代码引用。
3. **无设计角色**：`BaseAgent` 不出现在任何设计文档的架构描述或工作项的职责范围内。`01-architecture-baseline.md` 完全未提及 `BaseAgent`。
4. **已由 `AgentModel` + `DefaultAgentEngine` 替代**：Agent 配置由 `AgentModel`（从 `agent.xdef` 加载）承载，执行由 `DefaultAgentEngine` + `IAgentExecutor` 负责。`BaseAgent` 无存在必要。

## Current Baseline

- `BaseAgent` 曾存在于 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/agents/BaseAgent.java（已删除）`，7 行代码，仅声明 `IAiMemoryStore memory` 字段
- `DefaultAgentEngine`（L1-1 ✅）是 Agent 引擎的核心入口，不使用 `BaseAgent`
- `AgentModel` 是从 `agent.xdef` 加载的配置对象，不继承 `BaseAgent`
- 执行逻辑由 `IAgentEngine` + `IAgentExecutor` 负责（Actor 模型），`BaseAgent` 不参与执行链路
- `IAiMemoryStore` 接口存在，但 `BaseAgent` 中的 `memory` 字段未被使用
- 设计原则（`00-vision.md` §三 约束表第 2 行）明确：Agent 是配置对象而非执行体
- `BaseAgent` 不在任何设计文档或工作项的职责范围内
- `01-architecture-baseline.md` 未提及 `BaseAgent`
- grep 搜索确认：`nop-ai-agent` 模块内无任何 Java 代码 import 或继承 `BaseAgent`（`nop-core/TestJsonTool.java:150` 中的 `"BaseAgent"` 是 JSON 测试字面量，非代码引用）

## Goals

- 删除 `BaseAgent.java`
- 确认删除后构建和测试通过
- 更新 roadmap 中 L1-14 的状态为 ✅
- 更新技术债清单（§5）中关于 `BaseAgent` 的条目

## Non-Goals

- 不设计新的 Agent 基类或抽象层（如果未来需要 Agent 运行时对象，应通过新的设计文档 + 计划处理，不应复用空壳）
- 不改变 `AgentModel` 的结构
- 不引入新的执行模型变更

## Scope

### In Scope

- 删除 `BaseAgent.java`
- 确认无引用断裂
- 更新 roadmap 和相关文档
- 确认构建和测试通过

### Out Of Scope

- Agent 执行引擎的进一步设计
- 新的抽象类或接口设计
- 如果未来需要 Agent 运行时对象，属于新的设计+计划工作

## Execution Plan

### Phase 1 - 引用验证与删除

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/agents/BaseAgent.java（已删除）`

- Item Types: `Fix`

- [x] 再次确认 `BaseAgent` 无任何 Java import/extends 引用（`rg "import.*BaseAgent" --type java` 和 `rg "extends BaseAgent" --type java`），以及无 XML/配置文件引用（`rg "BaseAgent" --type xml`），确认 `TestJsonTool.java` 中的字符串引用是安全的
- [x] 删除 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/agents/BaseAgent.java（已删除）`
- [x] 删除空目录 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/agents/（已删除）`（如果删除 BaseAgent 后为空）

Exit Criteria:

- [x] `BaseAgent.java` 已删除，`agents/` 目录已清理（如果为空则删除）
- [x] grep 确认：删除后 `nop-ai/nop-ai-agent/` 内无残留的 `BaseAgent` 引用（`TestJsonTool.java` 在 `nop-core` 中，不受影响）
- [x] No owner-doc update required: 本 Phase 只删除空壳代码，不改变已有公共契约
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 构建验证与文档更新

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`

- Item Types: `Fix`

- [x] 运行 `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 确认编译和测试通过
- [x] 更新 `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 1 L1-14 状态为 ✅
- [x] 更新 `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §5 技术债中 `BaseAgent` 条目（标记已解决）
- [x] 确认 `01-architecture-baseline.md` 无需更新（已确认不提及 BaseAgent）

Exit Criteria:

- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过
- [x] `nop-ai-agent-roadmap.md` §4 L1-14 状态为 ✅
- [x] `nop-ai-agent-roadmap.md` §5 `BaseAgent` 技术债条目标记已解决
- [x] 确认 `01-architecture-baseline.md` 无需更新（不提及 BaseAgent，无改动必要）
- [x] No `docs-for-ai/` update required: 变更限于内部空壳清理，不改变用户可见行为或公共契约
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1-2 所有 Exit Criteria 已勾选 `[x]`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] roadmap L1-14 状态已更新为 ✅
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：本计划是删除操作，anti-hollow 不适用（不引入新组件，只移除空壳）
- [x] checkstyle / 代码规范检查通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Deferred But Adjudicated

None anticipated.

## Non-Blocking Follow-ups

- None — 这是一个简单的清理操作

## Closure

Status Note: Both phases completed. BaseAgent.java deleted, agents/ directory cleaned, tests pass, roadmap L1-14 updated to ✅, tech debt entries updated.

Closure Audit Evidence:

- Reviewer / Agent: 独立审计 sub-agent (fresh session, task_id: ses_1481f871effeU90u2sNoMygIcP)
- Audit Session: 独立 closure-audit pass（fresh session，非实现阶段 self-audit）。独立 re-audit 结论：ALL CRITERIA SATISFIED（17/17 PASS），独立重跑 build = BUILD SUCCESS (70 tests, 0 failures)，check-plan-checklist 退出 0，scan-hollow 退出 0。
- Evidence:
  - Phase 1 Exit Criterion 1 (BaseAgent.java 删除 + agents/ 清理): PASS — `nop-ai/nop-ai-agent/.../agents/（已删除）` 目录不存在，无残留 `BaseAgent.java（已删除）`
  - Phase 1 Exit Criterion 2 (无残留引用): PASS — grep `BaseAgent` 于 `nop-ai/nop-ai-agent/` 返回 0 匹配；`grep "(import.*BaseAgent|extends BaseAgent)" --type java` 全仓 0 匹配
  - Phase 1 Exit Criterion 3 (No owner-doc update): PASS — 纯删除空壳，不改变公共契约
  - Phase 1 Exit Criterion 4 (logs 更新): PASS — `ai-dev/logs/2026/06-12.md` 含 Plan 138 条目
  - Phase 2 Exit Criterion 1 (mvnw test): PASS — `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS，Tests run: 70, Failures: 0, Errors: 0, Skipped: 6
  - Phase 2 Exit Criterion 2 (roadmap L1-14 ✅): PASS — `nop-ai-agent-roadmap.md:136` `| L1-14 | ... | ✅ |`
  - Phase 2 Exit Criterion 3 (§5 技术债已解决): PASS — `nop-ai-agent-roadmap.md:221` `✅ 已解决`
  - Phase 2 Exit Criterion 4 (architecture-baseline 无需更新): PASS — `01-architecture-baseline.md` 中 `BaseAgent` 出现 0 次
  - Phase 2 Exit Criterion 5 (No docs-for-ai update): PASS — 内部空壳清理，不改变用户可见行为
  - Phase 2 Exit Criterion 6 (logs 更新): PASS — `ai-dev/logs/2026/06-12.md`
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查结果：本计划为删除操作（不引入新组件），anti-hollow 不适用；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` Total findings = 0（退出码 0）
  - Deferred 项分类检查：无 deferred 项；Non-Blocking Follow-ups 为 None，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work
