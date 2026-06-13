# 维度 04：ORM 模型与实体设计 — nop-ai-agent

## 第 1 轮（初审）

### 检查范围

该模块无 ORM 模型文件（无 `model/*.orm.xml`），符合预期。该模块是纯 Java 框架库，不使用 ORM/DAO/service/web 分层结构。

**已读关键文件：**

**XDef 模型定义：**
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent-plan.xdef`

**注册与映射文件：**
- `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent.register-model.xml`
- `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent-plan.register-model.xml`
- `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/record/mapping/agent-plan.record-mappings.xml`

**Precompile 脚本：**
- `nop-ai/nop-ai-agent/precompile/gen-agent-xdsl.xgen`

**`io.nop.ai.agent.model` 包手写模型类（全部读过）:**
- `AgentModel.java`, `AgentPlanModel.java`, `AgentExecStatus.java`, `AgentPermissionModel.java`, `AgentConstraintsModel.java`, `AgentHookModel.java`, `AgentPlanPhaseModel.java`, `AgentPlanTaskModel.java`, `AgentPlanError.java`, `AgentPlanDecision.java`, `AgentPlanNote.java`, `AgentPlanQuestion.java`

**`io.nop.ai.agent.model._gen` 生成基类（全部读过）**
**`io.nop.ai.agent.plan.model` 包手写模型类（全部读过）**
**`io.nop.ai.agent.plan.model._gen` 生成基类（关键文件）**

**非模型类 DTO/值对象（全部读过）:**
- engine/*, session/*, security/*, guardrail/*, memory/*, router/*, hook/*, compact/*

---

### [维度04-01] `io.nop.ai.agent.model._gen` 中存在过时的 plan 相关生成类

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/_gen/_AgentPlanModel.java:1-12`（及其 6 个同目录 plan 相关文件）
- **证据片段**:
  ```java
  // _AgentPlanModel.java 第 1-12 行
  package io.nop.ai.agent.model._gen;
  /**
   * generate from /nop/schema/ai/agent-plan.xdef <p>
   * Agent Plan Metamodel Definition
  ```
  当前 `agent-plan.xdef` 指定的 `xdef:bean-package` 为 `io.nop.ai.agent.plan.model`（非 `io.nop.ai.agent.model`）。
- **严重程度**: P2
- **现状**: 7 个 plan 相关 `_gen` 文件位于 `io.nop.ai.agent.model._gen` 包，声称由 `agent-plan.xdef` 生成。但当前 `agent-plan.xdef` 的 `xdef:bean-package` 已改为 `io.nop.ai.agent.plan.model`，这些文件在当前 schema 配置下不会被重新生成。
- **风险**: `./mvnw clean install` 后可能不会被重新生成，导致编译失败（`AgentExecutionContext` 引用了 `io.nop.ai.agent.model.AgentPlanModel`）。且字段结构与当前 `agent-plan.xdef` 定义的 `AgentPlan` 不一致，属 schema 版本残留。
- **建议**: 将 `AgentExecutionContext` 中的 plan 字段类型迁移到 `io.nop.ai.agent.plan.model.AgentPlan`，然后删除 `io.nop.ai.agent.model._gen` 中这 7 个过时的 plan 相关生成文件。
- **信心水平**: 很可能 (95%)
- **误报排除**: 不是同类误报。从 precompile 脚本和两个 xdef 文件的当前配置来看，这些文件不会被重新生成。
- **复核状态**: 未复核

---

### [维度04-02] 两个包中存在同名模型类，命名重叠导致混淆

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/_gen/_AgentPlanModel.java:21` vs `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/model/_gen/_AgentPlanModel.java:19`
- **证据片段**:
  ```java
  // io.nop.ai.agent.model._gen._AgentPlanModel 第 21 行
  public abstract class _AgentPlanModel extends AbstractComponentModel {

  // io.nop.ai.agent.plan.model._gen._AgentPlanModel 第 19 行
  public abstract class _AgentPlanModel extends AbstractComponentModel {
  ```
  6 个同名类：AgentPlanModel, AgentPlanTaskModel, AgentPlanError, AgentPlanDecision, AgentPlanNote, AgentPlanQuestion
- **严重程度**: P2
- **现状**: 两个包中存在 6 个同名 plan 模型类（`model` 和 `plan.model`），字段结构不同。极容易在 import 时引入错误。
- **风险**: 开发者在使用 plan 模型时容易 import 错误包中的同名类；新增功能时不确定应使用哪个包。
- **建议**: 如果 `model` 包中的 plan 相关类是遗留产物（见 04-01），应迁移或删除。若需保留，应重命名以区分（如 `AgentRuntimePlanModel`）。
- **信心水平**: 很可能 (95%)
- **误报排除**: 不是同类误报。两套模型的字段和职责确有不同，但命名相同造成真实混淆风险。
- **复核状态**: 未复核

---

### [维度04-03] `io.nop.ai.agent.model` 包中 5 个 plan 子模型为死代码

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/AgentPlanError.java`（及 AgentPlanDecision, AgentPlanNote, AgentPlanQuestion, AgentPlanPhaseModel）
- **证据片段**:
  ```java
  // _AgentPlanModel.java 第 49 行
  private KeyedList<io.nop.ai.agent.model.AgentPlanError> _errors = KeyedList.emptyList();
  ```
  全局 grep 搜索 `import io.nop.ai.agent.model.AgentPlan(Error|Decision|Note|Question|PhaseModel)` 仅在各自 `_gen` 基类中出现，无任何外部消费者。
- **严重程度**: P3
- **现状**: 5 个类仅被 `io.nop.ai.agent.model._gen._AgentPlanModel` 通过生成代码引用，从未被外部代码直接实例化或操作。
- **风险**: 增加代码库理解成本和维护负担，不影响运行时正确性。
- **建议**: 与 04-01 一并处理，迁移 `AgentExecutionContext` 的 plan 字段后清理这些类。
- **信心水平**: 很可能 (95%)
- **误报排除**: 不是同类误报。注册模型文件确认 `agent-plan` DSL 使用 `io.nop.ai.agent.plan.model.AgentPlan` 作为根模型类，非 `io.nop.ai.agent.model.AgentPlanModel`。
- **复核状态**: 未复核

---

### [维度04-04] 时间戳表示方式不一致

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/model/_gen/_AgentPlan.java:35` vs `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/AgentSession.java:19`
- **证据片段**:
  ```java
  // _AgentPlan.java 第 35 行 (XDSL 生成)
  private java.time.LocalDateTime _createdAt;

  // AgentSession.java 第 19 行 (手写)
  private final long createdAt;
  ```
- **严重程度**: P3
- **现状**: XDSL 生成模型统一使用 `java.time` 类型，手写运行时类使用 `long`（epoch 毫秒）。两套约定各自一致，但跨模型边界需显式转换。
- **风险**: 未来需跨模型关联时间数据时需做 `long <-> LocalDateTime` 转换。
- **建议**: 可能是 Nop 平台惯例（XDSL 模型用 `java.time`，轻量值对象用 `long`）。可接受现状，若需跨模型时间关联可提供工具方法。
- **信心水平**: 很可能 (90%)
- **误报排除**: 这可能是平台惯例而非问题。
- **复核状态**: 未复核
