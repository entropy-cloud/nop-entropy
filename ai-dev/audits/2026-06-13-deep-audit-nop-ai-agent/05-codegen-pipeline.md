# 维度 05：生成管线完整性 — nop-ai-agent

**审计目标模块**: `nop-ai/nop-ai-agent`
**审计基线**: live code（不以历史日志/计划为准）
**生成管线形态**: 本模块不是标准 dao/meta/service/web 业务模块。其生成管线是 XDsl model→codegen 路径：`precompile/gen-agent-xdsl.xgen` 通过 `codeGenerator.renderModel(...)` 将 `/nop/schema/ai/agent.xdef` 与 `/nop/schema/ai/agent-plan.xdef` 渲染到各自 `xdef:bean-package` 声明的 `_gen/` 目录；exec-maven-plugin 在 root pom 的 `precompile` execution（`generate-sources` 阶段，mainClass=`io.nop.codegen.task.CodeGenTask`）中被本模块 pom 显式激活。

## 第 1 轮（初审）

**管线入口已校验通过**:
- precompile 脚本路径、xdef 路径、模板路径（`/nop/templates/xdsl`）均有效。
- exec-maven-plugin 配置（root pom 325-345 行 + 模块 pom 42-46 行）正确绑定到 `precompile` 目录。
- 两个 register-model.xml 的 `xdsl-loader` fileType/schemaPath 正确。
- 测试资源 test-*.agent.xml 经抽查符合 agent.xdef（除 05-04）。

### [维度05-01] XDsl 生成管线无剪枝机制：`mvn clean install` 无法收敛历史漂移，孤儿 `_` 类在两个 `_gen/` 目录长期堆积并被手写代码耦合

- **文件**: `nop-ai/nop-ai-agent/precompile/gen-agent-xdsl.xgen:1-4`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/_gen/_AgentPlanModel.java:1-21`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/model/_gen/_AgentPlanModel.java:1-19`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutionContext.java:6`
- **证据片段**:
  ```xml
  <!-- gen-agent-xdsl.xgen:1-4 -->
  <c:script>
      codeGenerator.renderModel('/nop/schema/ai/agent-plan.xdef','/nop/templates/xdsl', '/',$scope);
      codeGenerator.renderModel('/nop/schema/ai/agent.xdef','/nop/templates/xdsl', '/',$scope);
  </c:script>
  ```
  ```java
  // model/_gen/_AgentPlanModel.java:1,12,21  —— 声称生成自 agent-plan.xdef，却落在 model 包
  package io.nop.ai.agent.model._gen;
  ...
   * generate from /nop/schema/ai/agent-plan.xdef <p>
  public abstract class _AgentPlanModel extends io.nop.core.resource.component.AbstractComponentModel {
  ```
  ```java
  // AgentExecutionContext.java:6  —— 手写引擎代码 import 了 model.AgentPlanModel
  import io.nop.ai.agent.model.AgentPlanModel;
  ```
- **严重程度**: P1
- **现状**: Nop 的 `codeGenerator.renderModel` 只会为 xdef 当前定义的模型元素创建/覆盖 `_<ClassName>.java`，不会删除已被移除或改名的模型元素对应的旧 `_` 文件，也不会清理 `_gen/` 目录。本模块的 agent-plan 元模型经历过两次重构（bean-package 从 `io.nop.ai.agent.model` 迁移到 `io.nop.ai.agent.plan.model`；根模型从 `AgentPlanModel` 改名为 `AgentPlan` 并重写全部字段），每次重构都在 `_gen/` 留下了完整的孤儿产物：
  - `io.nop.ai.agent.model._gen` 残留 7 个 plan 类（`_AgentPlanModel/_AgentPlanDecision/_AgentPlanError/_AgentPlanNote/_AgentPlanPhaseModel/_AgentPlanQuestion/_AgentPlanTaskModel`），其 `package` 与当前 `agent-plan.xdef` 的 bean-package（`io.nop.ai.agent.plan.model`）矛盾。
  - `io.nop.ai.agent.plan.model._gen` 残留 5 个孤儿类（`_AgentPlanModel/_AgentPlanDecision/_AgentPlanNote/_AgentPlanNoteModel/_AgentPlanQuestion`），其字段集（`notes/overview/path/planStatus/tasks/title`）与当前 xdef 根 `_AgentPlan` 的 21 个字段（`closure/phases/scope/sources/...`）完全无交集（见 05-02）。
- **风险**: 这是一次"被冻结的回归"。`mvn clean install` 只会重新生成 LIVE 类，孤儿 `_` 文件原地不动，构建照常编译通过——因为两套手写 retention 子类都还 `extends` 着这些孤儿基类。结果：(1) 新开发者无法通过重建消除 staleness，必须手工删除；(2) 引擎 `AgentExecutionContext` 通过 `import io.nop.ai.agent.model.AgentPlanModel` 绑定到错误包/错误形状的 plan 模型，且这一绑定被编译器"保护"起来不会被发现；(3) 每个孤儿类的 Javadoc 都写"generate from /nop/schema/ai/agent-plan.xdef"，构成对生成来源的虚假声明，污染公开面。
- **建议**: (1) 删除两个 `_gen/` 目录中所有当前 xdef 不再定义的孤儿类，并同步删除其手写 retention 子类。(2) 将 `AgentExecutionContext.plan` 字段类型从 `io.nop.ai.agent.model.AgentPlanModel` 迁移到 live 的 `io.nop.ai.agent.plan.model.AgentPlan`（或在引擎层定义独立 DTO，避免直接依赖生成模型）。(3) 在 precompile 脚本或 CI 中加入 `_gen/` 目录与 xdef 定义的一致性校验（差集非空则失败），防止未来再次漂移。
- **信心水平**: 高
- **误报排除**: 本发现不是对单个 `_` 文件内容的纠错（共享规则 4 已排除该口径）。它是针对"生成管线缺少剪枝 → 重建无法收敛 + 手写代码耦合到不再被生成的类名/位置"这一结构性管线缺陷。与 dim04 的区别：dim04 关注数据模型正确性，本条关注管线机制与可重建性。
- **复核状态**: 未复核

### [维度05-02] Markdown 与 XML/YAML 两条 plan 加载管线目标模型形状不一致：record-mapping 锁定在已废弃的 `AgentPlanModel` 形状，而 xdef 根模型已是 `AgentPlan`

- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent-plan.register-model.xml:4-9`、`nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/record/mapping/agent-plan.record-mappings.xml:58-81`、`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent-plan.xdef:12-23`
- **证据片段**:
  ```xml
  <!-- agent-plan.register-model.xml:4-9 -->
  <loaders>
      <xdsl-loader fileType="agent-plan.xml" schemaPath="/nop/schema/ai/agent-plan.xdef"/>
      <xdsl-loader fileType="agent-plan.yaml" schemaPath="/nop/schema/ai/agent-plan.xdef"/>
      <loader fileType="agent-plan.md" mappingName="agent-plan.Markdown_to_AgentPlanModel" optional="true"
              class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>
  </loaders>
  ```
  ```xml
  <!-- agent-plan.record-mappings.xml:58-81 —— Markdown_to_AgentPlanModel 的字段 -->
  <mapping name="Markdown_to_AgentPlanModel" md:titleField="title">
      <fields>
          <field name="path" from="存储路径"> ... </field>
          <field name="title" from="计划标题" mandatory="true"> ... </field>
          <field name="overview" from="计划概述"> ... </field>
          <field name="planStatus" from="计划状态">  <!-- 注意：planStatus，非 status -->
          <field name="tasks" from="任务" keyProp="taskNo" itemMapping="Markdown_to_AgentPlanTaskModel"> ...
  ```
  ```xml
  <!-- agent-plan.xdef:12-23 —— 当前 xdef 根模型 AgentPlan 的字段（status，非 planStatus；phases，非 tasks） -->
  <plan ... xdef:bean-package="io.nop.ai.agent.plan.model"
        title="string" currentPhase="string" currentTaskNo="string"
        createdAt="datetime" updatedAt="datetime" reviewedAt="date"
        status="enum:io.nop.ai.agent.model.AgentExecStatus">
      <purpose>string</purpose>
      <goal>string</goal>
      <currentBaseline>string</currentBaseline>
  ```
- **严重程度**: P2
- **现状**: 对同一逻辑制品（agent plan），register-model 声明了三种 FileType，但它们的"模型形状"分属两个不同世代：
  - `agent-plan.xml`/`agent-plan.yaml` 经 xdsl-loader 绑定到当前 xdef，根模型为 `AgentPlan`，字段集经实测为 `{closure, currentBaseline, currentPhase, currentTaskNo, errors, goal, nonGoals, phases, purpose, readFiles, relatedPlans, reviewedAt, scope, sources, status, successCriteria, title, updatedAt, validationChecklist, writtenFiles, createdAt}`（21 字段）。
  - `agent-plan.md` 经 `mappingName="agent-plan.Markdown_to_AgentPlanModel"` 绑定到 record-mapping，字段集为 `{path, title, overview, planStatus, tasks, notes}`（6 字段），且字段名 `planStatus`/`tasks` 与 xdef 根的 `status`/`phases` 不同名。
  - record-mapping 的 6 字段形状与孤儿类 `_AgentPlanModel`（`plan/model/_gen`，见 05-01）逐字段一致，证明 markdown 管线锁定在已废弃的中间世代模型上。
- **风险**: 任何通过 `agent-plan.md` 加载的 plan 与通过 `agent-plan.xml`/`yaml` 加载的 plan 在运行时不可互换：字段名不同（`planStatus` vs `status`）、结构不同（`tasks` 扁平列表 vs `phases→tasks` 嵌套）、缺失字段不同。下游消费者必须同时兼容两套形状，且 md 侧永远拿不到 xdef 新增的阶段/范围/收尾信息。结合 05-03，md 管线实质上既废弃又不可运行。
- **建议**: (1) 决定 markdown plan 的产品定位：若保留，则按当前 `AgentPlan` xdef 重写 mapping（重命名为 `Markdown_to_AgentPlan`，字段对齐）；若废弃，则从 register-model 删除 `agent-plan.md` loader 并把 record-mappings.xml 迁到 test 资源。(2) 统一 plan 的"形状唯一来源"为 `agent-plan.xdef`。
- **信心水平**: 高
- **误报排除**: 不是对 record-mapping 文案风格的挑剔。证据是可量化的字段集差集（6 vs 21，且字段名实质不同）。mapping 在 `TestAgentPlanRecordMapping` 中以 `forceUseMap=true` 跑通，恰好掩盖了"形状不一致"——因为测试只断言 Map 字面值，从不把 Map 绑定到 `AgentPlan` 类，所以形状漂移不会被测试捕获。这正是结构性漏检。
- **复核状态**: 未复核

### [维度05-03] `agent-plan.md` 生产装载器引用的类 `io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory` 仅在 test classpath 可用（`nop-record-mapping` 声明为 `<scope>test</scope>`）

- **文件**: `nop-ai/nop-ai-agent/pom.xml:33-37`、`nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent-plan.register-model.xml:7-8`
- **证据片段**:
  ```xml
  <!-- pom.xml:33-37 -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-record-mapping</artifactId>
      <scope>test</scope>
  </dependency>
  ```
  ```xml
  <!-- agent-plan.register-model.xml:7-8 —— 主资源里的装载器类来自 test-scope 依赖 -->
  <loader fileType="agent-plan.md" mappingName="agent-plan.Markdown_to_AgentPlanModel" optional="true"
          class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>
  ```
- **严重程度**: P2
- **现状**: register-model.xml 与 record-mappings.xml 都打包进主 JAR（`src/main/resources`），但它们功能依赖的 `nop-record-mapping` 被声明为 test scope。`optional="true"` 只会让 register-model 在注册阶段不抛错（找不到/失败时跳过），并不能让 `MarkdownDslResourceLoaderFactory` 在生产 classpath 凭空出现。`record-mappings.xml` 的 `x:post-extends` 还引用 `/nop/record/xlib/record-mapping-gen.xlib`（同样来自 nop-record-mapping），该组件模型一旦在生产被 `loadComponentModel` 加载也会失败。
- **风险**: 生产环境任何对 `/nop/record/mapping/agent-plan.record-mappings.xml` 或任意 `*.agent-plan.md` 资源的加载都会因 `ClassNotFoundException`（装载器类）或 xlib 找不到而失败。当前主代码确实没有触发这些加载（`TestAgentPlanRecordMapping` 是唯一消费者，且在 test scope），所以是"潜伏"缺陷；但这正是一种"主资源宣称的能力在生产环境不可用"的发布契约违约——下游集成者按 register-model 的声明使用 `agent-plan.md` 时会被坑。同时，它掩盖了 05-02 的形状漂移。
- **建议**: 二选一保持一致：(A) 若 `agent-plan.md` 是产品能力，把 `nop-record-mapping` 改为 compile scope；(B) 若 markdown→plan 仅是测试/工具场景，则把 `agent-plan.md` loader 从 register-model 移除，并把 record-mappings.xml 从 `src/main/resources` 挪到 `src/test/resources`，同步保留 pom 的 test scope。不要保留"主资源 + test 依赖"的当前组合。
- **信心水平**: 高
- **误报排除**: 不是"建议加显式依赖"的机械误报。共享规则明确：未显式声明平台核心包不算问题；但 `nop-record-mapping` 既不在该白名单内，又出现"主资源引用其类 + 依赖声明为 test scope"的实质矛盾，且经全模块 grep 确认主代码对该库有间接依赖、无任何 main-scope 声明，属于真实的 scope 错配。
- **复核状态**: 未复核

### [维度05-04] 测试资源 `test-unknown-mode-agent.agent.xml` 声明 `mode="unknown"`，违反 `agent.xdef` 的枚举约束，且无任何测试加载它

- **文件**: `nop-ai/nop-ai-agent/src/test/resources/_vfs/test-unknown-mode-agent.agent.xml:1-8`、`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef:7-8`、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestModeDispatch.java:166-179`
- **证据片段**:
  ```xml
  <!-- test-unknown-mode-agent.agent.xml:1-2 -->
  <agent x:schema="/nop/schema/ai/agent.xdef" xmlns:x="/nop/schema/xdsl.xdef"
         name="test-unknown-mode-agent" mode="unknown">
  ```
  ```xml
  <!-- agent.xdef:7-8 —— mode 仅允许 react/plan/single-turn -->
         xmlns:xdef="/nop/schema/xdef.xdef"
         name="!string" tagSet="csv-set"
         mode="enum:react,plan,single-turn|react">
  ```
  ```java
  // TestModeDispatch.java:166-179 —— 实际的 unknown-mode 测试用 new AgentModel() 构造，从不加载该 xml
  void testUnknownModeThrowsNopAiAgentException() {
      DefaultAgentEngine engine = createEngine();
      AgentModel model = new AgentModel();
      model.setName("test");
      model.setMode("unknown");           // 走 setter，绕过 xdef 枚举校验
      NopAiAgentException ex = assertThrows(NopAiAgentException.class,
              () -> engine.resolveExecutor(model));
  ```
- **严重程度**: P3
- **现状**: `test-unknown-mode-agent.agent.xml` 自带 `x:schema="/nop/schema/ai/agent.xdef"` 声明，但 `mode="unknown"` 不在 `enum:react,plan,single-turn` 范围内。一旦该文件被 `ResourceComponentManager.loadComponentModel(...)` 加载，xdsl-loader 会在 xdef 校验阶段直接抛错。经 grep 确认：没有任何测试加载它；真正的 unknown-mode 测试用 `new AgentModel()` + `setMode("unknown")`，完全绕过 xdef。
- **风险**: 这是一个"既不合规又无用"的死资源。它不会让当前 CI 失败（因为没人加载它），但会误导后续开发者：以为"unknown mode"可通过 `.agent.xml` 配置传入测试；若有人将来写"加载全部测试 agent.xml"的批量用例，该文件会让用例在校验阶段意外崩溃。
- **建议**: 删除 `test-unknown-mode-agent.agent.xml`（其语义已被 `TestModeDispatch.testUnknownModeThrowsNopAiAgentException` 用编程方式覆盖）。
- **信心水平**: 高
- **误报排除**: 不是"测试用例写得不够"的泛化抱怨。证据可证伪：(1) xdef 枚举不含 `unknown`；(2) grep 全测试目录无任何 `loadComponentModel` 引用该文件；(3) 唯一相关测试用 `new AgentModel()` 构造，不读 XML。
- **复核状态**: 未复核

## 复核结论表

| 编号 | 标题 | 严重 | 信心 | 复核状态 |
|------|------|------|------|----------|
| 维度05-01 | 生成管线无剪枝 → 孤儿 `_` 类堆积，`mvn clean install` 无法收敛，手写代码耦合错误类 | P1 | 高 | 未复核 |
| 维度05-02 | md 与 xml/yaml plan 管线目标模型形状不一致（`AgentPlanModel` 6 字段 vs `AgentPlan` 21 字段） | P2 | 高 | 未复核 |
| 维度05-03 | `agent-plan.md` 生产装载器类仅 test classpath 可用（`nop-record-mapping` test scope） | P2 | 高 | 未复核 |
| 维度05-04 | `test-unknown-mode-agent.agent.xml` 违反 `mode` 枚举且无测试加载（死资源） | P3 | 高 | 未复核 |

**未执行的检查（受限于审计口径，非遗漏）**: 未实际运行 `./mvnw clean install` 验证重建行为；结论"codegen 不剪枝"是基于 Nop 平台已知行为 + 当前源码树中孤儿类已存在的经验证据。建议复核阶段实际执行一次 clean 重建以最终确证 05-01 的"无法自愈"判定。
