# 128 nop-ai-agent: 创建 agent.register-model.xml 使 .agent.xml DSL 可加载

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L0-1
> Last Reviewed: 2026-06-09
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 L0-1, §5 技术债 P0
> Related: roadmap §2 M4b

## Purpose

创建 `agent.register-model.xml`，使 `.agent.xml` 文件能被 XLang DSL 加载机制正确识别和装载为 `AgentModel` 对象。当前该文件缺失，导致 `agent.xdef` 定义的 DSL schema 虽然存在但无法使用，等同于 DSL 死代码。

## Current Baseline

- `agent.xdef` 已定义在 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`，xdef:name=`AgentModel`，bean-package=`io.nop.ai.agent.model`
- `agent-plan.register-model.xml` 已存在并正常工作，注册了 xml/yaml/md 三种格式的 loader
- `.agent.xml` 的加载完全缺失：没有 `agent.register-model.xml`，无法通过 `ResourceComponentManager` 加载 `.agent.xml` 文件
- `AgentModel` 的 _gen 代码已由代码生成管线产出（`_gen/_AgentModel.java` 等）
- `nop-ai-agent` 模块**没有 `src/test/` 目录和测试依赖**：pom.xml 中未声明 `nop-autotest-junit`，也没有任何测试代码。测试基础设施需从零创建（参照兄弟模块 `nop-ai-toolkit` 的 pom.xml 声明 `nop-autotest-junit` test scope 依赖）
- 编译通过：`./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` 成功

## Goals

- 创建 `agent.register-model.xml`，使 `.agent.xml` 文件可被 XLang 加载
- 编写单元测试验证加载功能端到端可用
- 编写一个示例 `.agent.xml` 文件作为最小验证用例

## Non-Goals

- 不实现任何 Agent 执行逻辑（ReAct 引擎、会话管理等属于 L1+ 工作项）
- 不处理 L0-2 枚举不一致问题（独立工作项）
- 不添加 yaml/md 格式的 agent loader（agent.xdef 不像 agent-plan 那样有 markdown 映射需求，xml 格式足够）

## Scope

### In Scope

- 创建 `agent.register-model.xml` 注册文件
- 编写最小 `.agent.xml` 测试用例文件
- 编写单元测试验证 DSL 加载

### Out Of Scope

- Agent 执行引擎（L1-1 ~ L1-14）
- 枚举统一（L0-2）
- BaseAgent 清理（L1-14）

## Execution Plan

### Phase 1 - 创建 agent.register-model.xml 并验证加载

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent.register-model.xml`, `nop-ai/nop-ai-agent/pom.xml`, `nop-ai/nop-ai-agent/src/test/`

- Item Types: `Fix`, `Proof`

- [x] 在 pom.xml 中添加 `nop-autotest-junit` test scope 依赖（参照 `nop-ai-toolkit/pom.xml`）
- [x] 创建测试目录结构 `src/test/java/io/nop/ai/agent/` 和 `src/test/resources/_vfs/`
- [x] 在 `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/` 创建 `agent.register-model.xml`，参照已有 `agent-plan.register-model.xml` 格式，注册 `agent.xml` fileType，schemaPath 指向 `/nop/schema/ai/agent.xdef`
- [x] 在 `src/test/resources/_vfs/` 下创建测试用 `test-agent.agent.xml` 文件（最小合法 agent 定义：仅含 `<name>` 和 `<prompt>` 为简单文本）
- [x] 编写 JUnit 5 单元测试，验证 `.agent.xml` 可通过 `ResourceComponentManager` 加载为 `AgentModel` 对象，且 `name` 和 `prompt` 字段值正确
- [x] 运行 `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 确认全部通过

Exit Criteria:

- [x] 文件 `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent.register-model.xml` 存在且内容合法（schema 引用 `/nop/schema/register-model.xdef`，loader 的 schemaPath 引用 `/nop/schema/ai/agent.xdef`，fileType 为 `agent.xml`）
- [x] pom.xml 已声明 `nop-autotest-junit` test scope 依赖
- [x] 测试用 `src/test/resources/_vfs/test-agent.agent.xml` 文件存在且符合 `agent.xdef` schema 约束（至少含 name + prompt，prompt 为简单文本）
- [x] 单元测试存在且通过：验证 `.agent.xml` 可加载为 `AgentModel`，断言 name 和 prompt 字段值
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` 成功
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 成功
- [x] **端到端验证**：从 `.agent.xml` 文件加载 → 得到 `AgentModel` Java 对象 → 字段可读取，完整路径已验证
- [x] No owner-doc update required（此工作项不改变公共 API 或行为契约，仅补齐已有 schema 的加载注册）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `agent.register-model.xml` 文件存在且可被 XLang 框架解析
- [x] `.agent.xml` 文件可被加载为 `AgentModel` 对象（有单测证明）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am -T 1C` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 通过

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- L0-2 枚举统一（`AgentExecStatus` vs `AgentTaskStatus`/`AgentPlanStatus`）— 独立工作项
- L1-13 基础单元测试框架搭建 — 当前 Phase 已包含最小测试，完整框架属于独立工作项

## Closure

Status Note: Plan executed successfully. Created `agent.register-model.xml` registering `agent.xml` fileType with schema `/nop/schema/ai/agent.xdef`. Added `nop-autotest-junit` test dependency. Created test `test-agent.agent.xml` and `TestAgentModelLoading` JUnit test verifying end-to-end loading: `.agent.xml` → `AgentModel` Java object with correct name, description, and prompt fields. All tests pass.

Closure Audit Evidence: `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS. Test `TestAgentModelLoading.testLoadAgentModel` passes confirming AgentModel loading with correct field values.

Follow-up:

- L0-2 枚举统一
- L1 层全部工作项
