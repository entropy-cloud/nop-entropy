# 272 nop-ai-agent 配置修复——beans 死代码激活 + mapping 名称修复 + orphan gen 清理 + 依赖 scope 修正

> Plan Status: planned
> Module: nop-ai-agent
> Last Reviewed: 2026-06-19
> Source: deep audit 2026-06-19-1355 dimensions 08 (IoC), 10 (XDSL), 01 (dependency), 03 (API surface for orphan gen finding)
> Related: 270-nop-ai-agent-security-hardening

## Purpose

收口 deep audit 发现的配置/codegen 层 P1/P2 问题：`ai-agent-tools.beans.xml` 死代码（10 个 tool bean 从未被加载）、mappingName 连字符导致 markdown plan loader 100% 失败、orphan `_gen` 文件与 xdef 脱节、pom 依赖 scope 泄漏。

## Current Baseline

- `ai-agent-tools.beans.xml` 声称被 `<ioc:collect-beans>` 自动收集，但 nop-ai-agent 模块无 `/nop/autoconfig/*.beans` 入口文件，10 个 tool bean 从未被 IoC 容器加载。设计文档 `01-architecture-baseline.md:85` 同样声称自动收集（false claim）。
- `agent-plan.register-model.xml:7-8` 中 `mappingName="agent-plan.Markdown_to_AgentPlanModel"` 包含连字符，`isValidClassName` 拒绝它，导致 markdown plan loader 100% 失败。测试绕过了此 bug。
- `model/_gen/_AgentPlanModel.java`（475 行）是 frozen orphan，与 xdef 脱节，但被 `AgentExecutionContext` 作为运行时核心数据结构引用。
- `pom.xml` 中 `nop-dao`（compile scope，仅测试用）和 `nop-message-core`（compile scope，仅测试用）泄漏 HikariCP 等传递依赖给下游消费者。

## Goals

- `ai-agent-tools.beans.xml` 被 IoC 容器实际加载，10 个 tool bean 在运行时可用。
- `mappingName` 连字符修复，markdown plan loader 可正常工作。
- orphan `_gen` 文件要么删除（迁移消费者到 `plan.model.AgentPlan`），要么重新接入 xdef 管理。
- `nop-dao` 和 `nop-message-core` 改为 `<scope>test</scope>`。

## Non-Goals

- 不重设计 plan model 的 xdef/codegen 管线。
- 不引入新的 IoC 扫描机制。
- 不改 ORM 模型或数据库 schema。

## Scope

### In Scope

- **Phase 1**: IoC 激活（beans 注册 + mapping 修复）
- **Phase 2**: 代码清理（orphan gen 迁移 + 依赖 scope 修正）

### Out Of Scope

- plan model xdef 完整重设计（当前仅修复 mapping 连字符和 orphan 文件）。
- `nop-record-mapping` test-scope 问题（20-5）：需要单独评估影响范围。

## Execution Plan

### Phase 1 - IoC 激活与 Mapping 修复

Status: planned
Targets: `ai-agent-tools.beans.xml`, `agent-plan.register-model.xml`, `/nop/autoconfig/`

- Item Types: `Fix`

- [ ] 创建 `/nop/autoconfig/nop-ai-agent.beans` 文件，内容引用 `/nop/ai/beans/ai-agent-tools.beans.xml`
- [ ] 修复 `ai-agent-tools.beans.xml` 的 `x:schema` 声明和 `xmlns:ioc` URI（移除空格）
- [ ] 修复 `agent-plan.register-model.xml` 中 `mappingName` 的连字符：`agent-plan.Markdown_to_AgentPlanModel` → `agentPlan.Markdown_to_AgentPlanModel`
- [ ] 修复 `agent-plan.record-mappings.xml` 中字段名漂移（`planStatus` → `status`，`tasks` → `phases`，补齐 `purpose`/`goal`/`phases`）
- [ ] 添加端到端测试：从 `.agent-plan.md` 文件加载到 `AgentPlan` 对象，验证全路径可用
- [ ] 验证 10 个 tool bean 在运行时被 IoC 容器正确加载

Exit Criteria:

- [ ] `ai-agent-tools.beans.xml` 中的 10 个 tool bean 在运行时可通过 IoC 容器获取
- [ ] markdown plan loader 不再因 `mappingName` 连字符失败
- [ ] 端到端 `.agent-plan.md` 加载测试通过
- [ ] **接线验证**：`/nop/autoconfig/nop-ai-agent.beans` 被 IoC 容器实际发现和加载（非仅文件存在），验证方式：测试中 `IoC.getAllBeans()` 断言包含 tool bean 实例
- [ ] **无静默跳过**：tool bean 实例化后可调用（非 lazy proxy / uninitialized），验证方式：测试中调用 bean 的公共方法不抛异常
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 编译通过
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [ ] checkstyle / 代码规范检查通过
- [ ] 所有 in-scope confirmed live defects (08-1, 10-01) 已修复确认
- [ ] 设计文档 `01-architecture-baseline.md` 更新 IoC 收集方式说明
- [ ] `ai-dev/logs/2026/06-19.md` 已更新

### Phase 2 - Orphan 清理与依赖修正

Status: planned
Targets: `model/_gen/`, `model/AgentPlanModel.java`, `pom.xml`

- Item Types: `Fix`

- [ ] 评估 `model/_gen/_AgentPlanModel.java` 的消费者：将所有引用迁移到 `plan.model.AgentPlan`
- [ ] 删除 `model/_gen/_AgentPlanModel.java` 和 `model/AgentPlanModel.java`（orphan）
- [ ] 将 `pom.xml` 中 `nop-dao` 改为 `<scope>test</scope>`
- [ ] 将 `pom.xml` 中 `nop-message-core` 改为 `<scope>test</scope>`
- [ ] 验证下游模块编译不因 scope 变更而失败
- [ ] 清理 `ReActAgentExecutor.java` 中的重复 import 声明

Exit Criteria:

- [ ] `model/_gen/` 下无 orphan 文件，所有 plan model 类型来自 xdef 管理的 `plan.model` 包
- [ ] `nop-dao` 和 `nop-message-core` 为 test scope，不泄漏传递依赖
- [ ] `./mvnw clean install -pl nop-ai/nop-ai-agent -am` 全绿
- [ ] 下游依赖 nop-ai-agent 的模块编译通过（如无下游模块则注明 `No downstream modules depend on nop-ai-agent`）
- [ ] **无静默跳过**：删除 orphan 文件后，所有引用点编译失败时快速报错（非静默忽略）
- [ ] checkstyle / 代码规范检查通过
- [ ] 所有 in-scope confirmed live defects (05-2, 01-1, 01-2) 已修复确认
- [ ] `docs-for-ai/01-repo-map/module-groups.md` 更新依赖关系说明
- [ ] No owner-doc update required for `docs-for-ai/02-core-guides/`：pom scope 变更和 orphan 删除不改变 public contract
- [ ] `ai-dev/logs/2026/06-19.md` 已更新

## Closure Gates

- [ ] Phase 1 Exit Criteria 全部勾选（08-1 beans 激活 + 10-01 mapping 修复）
- [ ] Phase 2 Exit Criteria 全部勾选（05-2 orphan 清理 + 01-1/01-2 依赖 scope）
- [ ] `./mvnw clean install -pl nop-ai/nop-ai-agent -am` 全绿
- [ ] checkstyle / 代码规范检查通过
- [ ] 所有 in-scope confirmed live defects (08-1, 10-01, 05-2, 01-1, 01-2) 已修复确认
- [ ] 无 in-scope 项被静默降级为 deferred
- [ ] 独立 closure audit 完成（Reviewer: ___）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/272-nop-ai-agent-configuration-fixes.md --strict` 退出码 0

## Deferred But Adjudicated

- **20-5** `nop-record-mapping` test-scope 在 pom 中但被 main resource `register-model.xml` 引用
  - Classification: watch-only residual
  - Why Not Blocking Closure: 当前 plan 仅修复 mapping 名称连字符，record-mapping scope 问题需单独评估影响范围
  - Successor Required: yes
  - Successor Path: 独立后续 plan 评估是否将 `nop-record-mapping` 改为 compile scope 或重构 register-model 引用方式

- **05-2** `_gen` 文件完整重接入 xdef 管线
  - Classification: optimization candidate
  - Why Not Blocking Closure: Phase 2 已删除 orphan 文件并迁移消费者，完整 codegen 集成为架构优化
  - Successor Required: no（当前删除方案已满足 baseline）
  - Successor Path: 后续按需重设计 plan model codegen 管线

- **15-1** `Team.members` 应为 `ConcurrentMap` 而非 `Map`
  - Classification: optimization candidate
  - Why Not Blocking Closure: 类型安全优化，当前 `InMemoryTeamManager` 内部已用 `ConcurrentHashMap`，仅接口签名不精确
  - Successor Required: no
  - Successor Path: 后续 cleanup PR 顺带修复
