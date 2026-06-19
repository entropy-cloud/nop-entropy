# 维度 10：XDSL 与 XLang 正确性 — nop-ai-agent

## 第 1 轮（初审）

### [维度10-01] agent-plan.register-model.xml 的 `mappingName="agent-plan.Markdown_to_AgentPlanModel"` 含连字符，被 `isValidClassName` 拒绝，导致 markdown plan 加载器在 dev/test 环境下 100% 抛 IllegalArgumentException（被测试绕过掩盖）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent-plan.register-model.xml:7-8`
- **证据片段**:
  ```xml
  <loader fileType="agent-plan.md" mappingName="agent-plan.Markdown_to_AgentPlanModel" optional="true"
          class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>
  ```
  对比平台标准用法（`nop-orm-model/.../orm.register-model.xml:8-9`，无连字符）：
  ```xml
  <loader fileType="orm.md" mappingName="orm.Md_to_OrmModel" optional="true"
          class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>
  ```
  加载链触发点（`RecordMappingManagerImpl.java:42-48`）：
  ```java
  String getMappingPath(String mappingName) {
      Guard.checkArgument(StringHelper.isValidClassName(mappingName));
      int pos = mappingName.lastIndexOf('.');
      String prefix = mappingName.substring(0, pos).replace('.', '/');
      return "resolve-record-mappings:" + prefix;
  }
  ```
- **严重程度**: P1
- **现状**: `mappingName` 的前缀段 `agent-plan` 含连字符 `-`。当任何调用方（dev/test 环境，`nop-record-mapping` 在 classpath 时）通过 `ResourceComponentManager.loadComponentModel("xxx.agent-plan.md")` 触发加载链时，`isValidClassName` 在第 5 个字符 `-` 处返回 `false`，`Guard.checkArgument(false)` 抛出 `IllegalArgumentException("Invalid:")`。markdown plan 加载器**永远无法成功加载任何 `.agent-plan.md` 文件**。
- **风险**: 文档化/注册的 "agent-plan markdown DSL" 特性在 dev/test 环境 100% 失效。`optional="true"` **不能**保护此路径——它只在注册阶段捕获 `NoClassDefFoundError`/`NopException`，而此 `IllegalArgumentException` 发生在加载阶段、且异常类型不在捕获清单内。现有测试 `TestAgentPlanRecordMapping` 完全绕过 `RecordMappingManager`（直接 `loadComponentModel` + `defs.getMapping`），因此该 bug 被测试掩盖。一旦有开发者按文档实际使用 `.agent-plan.md`，会得到一条与连字符毫无关联线索的 `IllegalArgumentException: Invalid:`。
- **建议**: 将 record-mappings 文件名与 `mappingName` 前缀同步改为无连字符形式（如 `agentPlan.record-mappings.xml` + `mappingName="agentPlan.Markdown_to_AgentPlanModel"`），或使用下划线 `agent_plan`。修复后应补一个真正走 `ResourceComponentManager.loadComponentModel("xxx.agent-plan.md")` 全链路的端到端测试。
- **信心水平**: 确定（已逐行追踪 `isValidClassName`/`Guard.checkArgument`/`RecordMappingManagerImpl.getMappingPath`，并在平台 `orm` 模块对照确认无连字符是事实约定）
- **误报排除**: 这不是"测试 scope"误报。先前审计只覆盖了**类缺失**（`NoClassDefFoundError`，由 `optional=true` 降级），**没有**覆盖"类存在时 mappingName 连字符触发 `isValidClassName` 失败"这条独立路径。本发现是即使 `nop-record-mapping` 在 classpath 上也成立的、更根本的阻断。
- **复核状态**: 未复核

---

### [维度10-02] agent-plan.record-mappings.xml 的字段名与当前 agent-plan.xdef 结构全面漂移

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/record/mapping/agent-plan.record-mappings.xml:9-81`
- **证据片段**:
  ```xml
  <mapping name="Markdown_to_AgentPlanModel" md:titleField="title">
      <fields>
          <field name="path" from="存储路径"> ... </field>
          <field name="title" from="计划标题" mandatory="true"> ... </field>
          <field name="overview" from="计划概述"> ... </field>
          <field name="planStatus" from="计划状态"> ... </field>
          <field name="tasks" from="任务" keyProp="taskNo" itemMapping="Markdown_to_AgentPlanTaskModel"> ... </field>
      </fields>
  </mapping>
  ```
  当前 `agent-plan.xdef`（`nop-xdefs/.../schema/ai/agent-plan.xdef:12-17, 41-109`）根 `<plan>` 的实际结构：
  ```xml
  <plan xdef:name="AgentPlan" xdef:bean-package="io.nop.ai.agent.plan.model"
        title="string" currentPhase="string" currentTaskNo="string"
        createdAt="datetime" updatedAt="datetime" reviewedAt="date"
        status="enum:io.nop.ai.agent.model.AgentExecStatus">
      <purpose>string</purpose>
      <goal>string</goal>
      ...
      <phases xdef:body-type="list" xdef:key-attr="name">
          <phase ...><tasks xdef:body-type="list" xdef:key-attr="taskNo">...</tasks></phase>
      </phases>
  ```
- **严重程度**: P2
- **现状**: mapping 的 5 个顶层字段中，仅 `title` 命中 xdef 属性；`path`、`overview`、`planStatus`（xdef 是 `status`，不是 `planStatus`）、`tasks`（xdef 是 `phases` 内嵌的 `tasks`，非顶层）均**不在当前 `agent-plan.xdef` 根 `<plan>` 的允许结构内**。不匹配的字段要么被 xdef 校验拒绝，要么静默丢失，最终得到的 `AgentPlan` 缺失 `purpose`/`goal`/`phases` 等核心节点。任务子映射 `Markdown_to_AgentPlanTaskModel` 的 `overview`/`notes` 字段同样不在 xdef 的 `AgentPlanTaskModel`（其字段为 instructions/resultMessage/checks/subTasks）内。
- **风险**: 即便修复 10-01 的连字符问题，markdown plan 的双向转换（md↔xml）仍无法产出合规的 `AgentPlan` 模型。该 mapping 实际对应的是一个**更早版本的 agent-plan.xdef**（其生成物 `io.nop.ai.agent.model._gen._AgentPlanModel` 含 `decisions`/`keyQuestions`/`notes`/`status`，与当前 xdef 生成物 `io.nop.ai.agent.plan.model._gen._AgentPlan` 含 `closure`/`purpose`/`phases` 不同），说明 mapping 与 xdef 在某次重构后未同步更新。
- **建议**: 以当前 `agent-plan.xdef` 为准重写 `agent-plan.record-mappings.xml` 的字段映射（`status` 而非 `planStatus`、`phases` 而非 `tasks`、移除 `path`/`overview` 或映射到 xdef 实际存在的节点），并补 `.agent-plan.md` ↔ `.agent-plan.xml` 往返测试。与 10-01 一并修复。
- **信心水平**: 确定（已逐字段对照 `agent-plan.xdef` 与 `_AgentPlan.java`/`_AgentPlanModel.java` 两套生成物）
- **误报排除**: 这不是对 02-3 的重复——本条独立从 XDSL 合规性角度确认，并定位到 mapping 对应的是旧 xdef 生成物。
- **复核状态**: 未复核

---

### [维度10-03] ai-agent-tools.beans.xml 缺失 `x:schema` 且 `xmlns:ioc` URI 带空格、偏离全平台约定（从 XDSL 合规角度独立确认 08-2）

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/beans/ai-agent-tools.beans.xml:1-3`
- **证据片段**:
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <beans xmlns:x="/nop/schema/xdsl.xdef"
         xmlns:ioc="urn: nop-ioc:1.0">
  ```
  对比同 AI 模块族的所有兄弟 beans.xml（均声明 x:schema 且 ioc URI 为 `ioc`），例如 `nop-ai-core/.../ai-defaults.beans.xml:2`：
  ```xml
  <beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc">
  ```
- **严重程度**: P3
- **现状**: 两个偏离：(1) **缺失 `x:schema="/nop/schema/beans.xdef"`**——全仓库 90+ 个 beans.xml 均声明该 schema，唯 `ai-agent-tools.beans.xml` 与 `nop-ai-toolkit` 的两个文件未声明。(2) **`xmlns:ioc="urn: nop-ioc:1.0"`（`urn:` 后有空格）**——全平台唯一规范的 ioc 命名空间 URI 是字面量 `ioc`。
- **风险**: 当前**功能上无害**，原因：(a) Nop 的 `XDslValidator.checkAttrs` 通过 `StringHelper.getNamespace(name)` 按**前缀**而非 URI 匹配，所以 URI 写成任意串对校验等价；(b) 本文件 body 未使用任何 `ioc:` 前缀的标签/属性，故 `xmlns:ioc` 声明根本未被解引用。但一旦未来在此文件新增 `ioc:collect-beans`/`ioc:inject` 等标签，缺失 `x:schema` 会导致这些扩展标签不被 `beans.xdef` 校验。
- **建议**: 补 `x:schema="/nop/schema/beans.xdef"`；将 `xmlns:ioc="urn: nop-ioc:1.0"` 改为 `xmlns:ioc="ioc"` 以对齐全平台约定。两处均为纯声明层改动。
- **信心水平**: 确定（已验证 Nop 按前缀匹配 ns、本文件未用 ioc: 标签、全平台 90+ 文件用 `xmlns:ioc="ioc"`）
- **误报排除**: 维度 08-2 从 IoC 加载角度记录了 x:schema 缺失；本条独立从 XDSL 合规性角度确认，并补充了"当前为何功能无害"的机制证据。
- **复核状态**: 未复核

---

## 已检查且合规的项（无发现）

**1. app.orm.xml（XDSL 合规）** — `x:schema="/nop/schema/orm/orm.xdef"` 引用正确（已验证 xdef 存在）。无 `x:extends`/`x:override`（正确，源 ORM 不应有 Delta 标记）。**合规**。

**2. agent.register-model.xml（schemaPath）** — `x:schema="/nop/schema/register-model.xdef"` 引用正确。`xdsl-loader schemaPath="/nop/schema/ai/agent.xdef"` 指向的 xdef 存在。`fileType="agent.xml"` 与测试 fixture 命名一致。**合规**。

**3. agent-plan.register-model.xml（xdsl-loader 部分）** — 两个 `xdsl-loader`（`agent-plan.xml`、`agent-plan.yaml`）的 `schemaPath` 均指向已存在的 xdef。`optional="true"` 的语义已查证。mappingName 后缀段与 record-mappings.xml 内 `<mapping name>` 一致。**合规**（mappingName 引用一致性合规，问题仅在连字符，见 10-01）。

**4. precompile/gen-agent-xdsl.xgen** — 脚本正确调用 `codeGenerator.renderModel(...)` 两次，两个 xdef 路径均已验证存在，与 register-model.xml 的 schemaPath 引用一致。pom.xml 通过 `exec-maven-plugin` 在 build 的 generate-sources 阶段触发 precompile。**合规**。

**5. 28 个测试 *.agent.xml 的 x:schema 与 x:extends/x:override** — 全部使用 `x:schema="/nop/schema/ai/agent.xdef"`。唯一使用 `x:extends`/`x:override` 的文件是 `_delta/default/test-team-delta-base.agent.xml`，正确使用 `x:extends="super"` + `x:override="replace"`。Delta 路径与原文件路径一致。**合规**。

**6. 自定义 xdef 检查（应无）** — glob 返回空：本模块无自定义 xdef，符合共享上下文。模块消费的自定义（非平台核心）DSL 仅有 record-mappings。**合规**。

## 补充观察（超出维度 10 严格范围）

**观察 A（codegen 清洁度，P3）**：`io.nop.ai.agent.model._gen` 下存在 7 个 `_AgentPlan*` 生成类，对应**更早版本的 agent-plan.xdef**。当前 agent-plan.xdef 的生成物在 `io.nop.ai.agent.plan.model._gen`。两套并行模型类共存，且手写代码仍 `import io.nop.ai.agent.model.AgentPlanModel`（旧类）。与 10-02 的 mapping 漂移同源。因属生成文件 + 维度 02 模型一致性范畴，本维度仅记录线索。

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。
