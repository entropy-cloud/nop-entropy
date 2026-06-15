# 维度 01：依赖图与模块边界（nop-ai-agent）

## 第 1 轮（初审）

### [维度01-1] nop-dao 声明为 compile scope 但 main 代码零引用（仅 test 在用）

- **文件**: `nop-ai/nop-ai-agent/pom.xml:31-34`；对照 `src/main/java/`（全量 grep 结果为空）vs `src/test/java/`（10 个文件）
- **证据片段**:
  ```xml
  <!-- pom.xml:31-34, 无 scope 声明 = compile -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-dao</artifactId>
  </dependency>
  ```
  ```
  $ grep -rn "io\.nop\.dao" src/main/java/   =>  (无输出，0 匹配)
  $ grep -rl "io\.nop\.dao" src/test/java/   =>  10 个测试文件
  # 测试中只用到一个类:
  import io.nop.dao.jdbc.datasource.SimpleDataSource;
  ```
  ```
  # DBSessionStore.java:7-12 显式说明设计为 raw JDBC，绕过 nop-dao:
  import javax.sql.DataSource;        // JDK 标准
  import java.sql.Connection;
  import java.sql.PreparedStatement;
  # 类注释: "Persistence scheme: raw JDBC (DataSource + PreparedStatement),
  #          consistent with DBDenialLedger and DBMessageService"
  ```
- **严重程度**: P2
- **现状**: nop-dao 是 compile 依赖，但 main 代码（含 4 个 DB* 类 DBSessionStore/DBDenialLedger/DBMessageService/DBCheckpointManager）全部使用 JDK 原生 `javax.sql.DataSource` + `java.sql.*`，不引用任何 `io.nop.dao.*`；仅测试代码通过 `SimpleDataSource` 使用 nop-dao。
- **风险**: (1) 下游消费方编译/运行 classpath 被迫引入 nop-dao 及其传递依赖（nop-dao-api 等），属误导性耦合；(2) pom 声明与实际使用不符，维护者会误以为 main 用了 nop-dao 的 API，做重构时容易看错依赖关系；(3) `app.orm.xml` 实际只作为 schema 文档参考（手写 `AiAgent*Table.java` 持有运行期 DDL），并不走 nop-dao/nop-orm 实体生成。
- **建议**: 将 `nop-dao` 的 scope 改为 `test`（与 `nop-autotest-junit`/`nop-record-mapping` 一致）；若未来 main 真要用 nop-dao 的 `IEntityDao`/`ISqlConnection` 等抽象，再回调 compile。
- **信心水平**: 确定（dependency:tree + main 全量 grep + test grep + 源码注释四重印证）
- **误报排除**: 不是"传递依赖碰巧可见"类误报——nop-dao 是**显式声明**的直连依赖，且 main 编译期确实可见其类；问题是"可见但零使用"，属真实 scope 过宽。已排除"app.orm.xml 隐含需要 nop-dao"的可能：模块内无任何 ORM 实体生成类（`find _gen entity` 为空）。
- **复核状态**: 未复核

### [维度01-2] nop-message-core 声明为 compile scope 但 main 代码零引用（仅 test 在用）

- **文件**: `nop-ai/nop-ai-agent/pom.xml:27-30`；对照 `src/main/java/`（0 匹配）vs `src/test/java/`（`LocalMessageService` 单类引用）
- **证据片段**:
  ```xml
  <!-- pom.xml:27-30, 无 scope = compile -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-core</artifactId>
  </dependency>
  ```
  ```
  $ grep -rn "^import io\.nop\.message" src/main/java/   =>  (0 匹配)
  $ grep -rh "^import io\.nop\.message" src/test/java/
  import io.nop.message.core.local.LocalMessageService;   # 唯一一个类
  ```
  ```
  # 本模块自建的消息包，与 nop-message-core 平行:
  src/main/java/io/nop/ai/agent/message/
    ├── LocalAgentMessenger.java     # 自研 messenger，不基于 nop-message-core
    ├── IAgentMessenger.java
    └── DBMessageService.java        # raw JDBC，不走 nop-message-core
  ```
- **严重程度**: P2
- **现状**: nop-message-core 为 compile 依赖，但 main 代码的 `io.nop.ai.agent.message` 子包是完全自研的消息机制（`LocalAgentMessenger`/`DBMessageService`），不使用 `io.nop.message.*`；测试中仅用 `LocalMessageService` 一个类做互操作性验证。
- **风险**: 与 [维度01-1] 同类——下游消费方被迫引入 nop-message-core（→ nop-core，虽然 nop-core 本就通过其他链路可达，但语义上是冗余声明）；pom 声明误导维护者以为 main 依赖 nop-message-core 的消息抽象。
- **建议**: 将 `nop-message-core` 的 scope 改为 `test`。
- **信心水平**: 确定（main 全量 grep 0 命中 + test 仅 1 类 + 自研 message 包平行存在）
- **误报排除**: 不是把自研包 `io.nop.ai.agent.message` 与 `io.nop.message` 混淆——已用 `grep "^import io\.nop\.message\."`（带尾点）精确区分；test 引用的是 `io.nop.message.core.local.LocalMessageService`，确属 nop-message-core。
- **复核状态**: 未复核

### [维度01-3] nop-record-mapping 为 test scope，但 main 资源（register-model loader + record-mappings.xml）依赖其类与 schema

- **文件**: `src/main/resources/_vfs/nop/core/registry/agent-plan.register-model.xml:6-8`；`src/main/resources/_vfs/nop/record/mapping/agent-plan.record-mappings.xml:1-6`；`pom.xml:41-45`（test scope）
- **证据片段**:
  ```xml
  <!-- pom.xml:41-45 -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-record-mapping</artifactId>
      <scope>test</scope>
  </dependency>
  ```
  ```xml
  <!-- register-model.xml:6-8 (main 资源) -->
  <loader fileType="agent-plan.md" mappingName="agent-plan.Markdown_to_AgentPlanModel"
          optional="true"
          class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>
  ```
  ```xml
  <!-- agent-plan.record-mappings.xml:1-6 (main 资源) -->
  <definitions x:schema="/nop/schema/record/record-mappings.xdef" ...>
      <x:post-extends>
          <c:import from="/nop/record/xlib/record-mapping-gen.xlib"/>
          <record-mapping-gen:GenReverseMappings/>
      </x:post-extends>
  ```
  ```
  # RegisterModelDiscovery.java:248-261 (平台运行期处理逻辑):
  try {
      IResourceObjectLoader<Object> loaderBean = newLoader(className, config, loader);
  } catch (NoClassDefFoundError | NopException e) {
      if (!optional) { throw NopException.adapt(e); }
      else { LOG.warn("nop.register-model.ignore-invalid-loader:..."); }
  }
  ```
- **严重程度**: P2
- **现状**: main 资源 `register-model.xml` 通过 `class` 属性引用 `io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory`，且 `record-mappings.xml` 引用 `/nop/schema/record/record-mappings.xdef` 与 `record-mapping-gen.xlib`——三者均来自 nop-record-mapping。但 nop-record-mapping 被声明为 `test` scope。`optional="true"` 使平台在运行期捕获 `NoClassDefFoundError` 后仅打 warn 日志降级。
- **风险**: (1) 下游消费方运行期 classpath 没有 nop-record-mapping（test scope 不传递），`*.agent-plan.md` 文件的加载会被静默禁用，仅留一条 warn 日志——这是一个对生产环境隐形的"功能降级"，无文档提示消费方需自行引入 nop-record-mapping；(2) main 资源 `record-mappings.xml` 在缺少该库时也成为死代码（其 schema/parser 都在 test 库里）；(3) 若有人误把 `optional="true"` 改为 false，启动会直接抛 `NoClassDefFoundError`。
- **建议**: 二选一——(A) 若 markdown 计划加载是核心特性：将 nop-record-mapping 提升为 compile scope；(B) 若仅是可选特性：保持 test scope，但在 owner 文档显式声明"下游启用 markdown plan 需自引 nop-record-mapping"，或把该 loader+mappings 移到 test 资源。当前是"资源在 main、依赖在 test"的不一致态。
- **信心水平**: 很可能（class/schema 引用方向确定无疑；唯一不确定的是团队"可选降级"是否为刻意设计，但即便刻意，scope-资源不一致仍属真实维护隐患）
- **误报排除**: 不是 main **Java** 代码泄漏——已确认 `src/main/java` 中 0 处 `record_mapping`/`autotest` 引用；问题专指 **main 资源**对 test 库的引用，属资源级耦合，与步骤 4 检查的"main java import test 库"是不同切面。已通过 `RegisterModelDiscovery` 源码确认 `optional` 的降级语义确实存在（不是无作用的装饰属性）。
- **复核状态**: 未复核

### [维度01-4] model 与 security 子包之间存在双向 import 循环（PathRuleModel ↔ PathAccessDecision）

- **文件**: `src/main/java/io/nop/ai/agent/model/PathRuleModel.java:4` ↔ `src/main/java/io/nop/ai/agent/security/{ParentPermissionConstraint,DefaultPermissionProvider,ParentConstrainedPathAccessChecker,RuleBasedPathAccessChecker}.java`
- **证据片段**:
  ```java
  // model/PathRuleModel.java:1-18 (手写 retention 类，非生成)
  package io.nop.ai.agent.model;
  import io.nop.ai.agent.model._gen._PathRuleModel;
  import io.nop.ai.agent.security.PathAccessDecision;   // model -> security
  public class PathRuleModel extends _PathRuleModel {
      public PathAccessDecision getAccessDecision() {
          return PathAccessDecision.fromString(getAccess());
      }
  }
  ```
  ```java
  // security 侧多文件反向引用 model:
  // ParentPermissionConstraint.java:3    import io.nop.ai.agent.model.PathRuleModel;
  // DefaultPermissionProvider.java:3     import io.nop.ai.agent.model.AgentPermissionModel;
  // ParentConstrainedPathAccessChecker.java:4  import io.nop.ai.agent.model.PathRuleModel;
  // RuleBasedPathAccessChecker.java:4    import io.nop.ai.agent.model.PathRuleModel;
  ```
- **严重程度**: P3
- **现状**: 数据模型层 `model` 与安全策略层 `security` 互依：`model.PathRuleModel` 为了把 `access` 字符串转成枚举，向上调用 `security.PathAccessDecision.fromString(...)`；而 `security` 多个 checker/provider 又依赖 `model.PathRuleModel` 读取规则。
- **风险**: 层级倒置——`model` 是底层值对象包，却依赖上层 `security` 的概念；这使两个包无法独立演进/单独复用。当前规模小（一个枚举 + 一个便利方法），破坏性有限，但若后续 PathAccessDecision 增多分支或 model 增多类似便利方法，环会扩大。
- **建议**: 把 `PathAccessDecision` 枚举下沉到 `model` 包（它本质是 model 的 `access` 字段的值域），或删除 `PathRuleModel.getAccessDecision()` 便利方法、让 security 侧自行做字符串到枚举的转换。
- **信心水平**: 确定
- **误报排除**: 不是 engine hub-spoke 那种"上下文类型共享"的良性环——engine 环是因为 `AgentExecutionContext` 等共享类型放在 engine 里；本环是业务语义环（数据模型 ↔ 安全决策），属不同性质。已确认 `PathAccessDecision` 是纯枚举、无反向 import，环的入口是 `PathRuleModel` 的便利方法。
- **复核状态**: 未复核

### [维度01-5] engine 子包作为"共享类型+编排"双重职责中心，与 14 个子包形成双向 import

- **文件**: `src/main/java/io/nop/ai/agent/engine/`（23 个文件）vs 全部其他子包；典型证据 `engine/DefaultAgentEngine.java:3-8`、`compact/PipelineCompactor.java:3-5`
- **证据片段**:
  ```
  # engine 包 import 了 14 个其他子包（编排职责）:
  compact, completion, guardrail, hook, memory, message, model,
  reliability, repair, router, security, session, skill, talent
  ```
  ```java
  // engine -> compact (DefaultAgentEngine.java:3-8)
  import io.nop.ai.agent.compact.IContextCompactor;
  import io.nop.ai.agent.compact.Layer2TurnPruningStrategy;
  import io.nop.ai.agent.compact.PipelineCompactor;
  ```
  ```java
  // compact -> engine (反向; PipelineCompactor.java:3-5，甚至引用了具体 executor 类)
  import io.nop.ai.agent.engine.AgentExecutionContext;
  import io.nop.ai.agent.engine.ITokenEstimator;
  import io.nop.ai.agent.engine.ReActAgentExecutor;   // 引用具体实现类，非接口
  ```
- **严重程度**: P3
- **现状**: `engine` 包同时承载两类内容：(a) 全模块共享的上下文/异常/结果类型（`AgentExecutionContext`/`NopAiAgentException`/`AgentExecutionResult` 等），被全部 14 个 SPI 子包反向 import；(b) 编排实现（`DefaultAgentEngine`/`ReActAgentExecutor`），又 import 全部 14 个子包。这使 engine 与 compact/completion/guardrail/hook/memory/message/reliability/repair/router/security/session/skill/talent 两两成环（共 12 个双向边）。
- **风险**: 任何 SPI 子包新增对 engine 的依赖都"看起来正常"，环难以收敛；`compact.PipelineCompactor` 甚至直接 import 了 `ReActAgentExecutor` 具体类（不是接口），是较紧的实现耦合。Java 无包级强制，当前能编译运行，属架构卫生问题而非缺陷。
- **建议**: 抽取一个 `engine.api`（或 `context`/`api`）子包，仅放 `AgentExecutionContext`/`AgentToolExecuteContext`/`NopAiAgentException`/`AgentExecutionResult`/`AgentMessageRequest` 等纯上下文与契约类型；`engine` 保留编排实现。这样 SPI 子包只依赖 `engine.api`，单向无环。
- **信心水平**: 确定（包级 import 图机器生成）
- **误报排除**: 不是"Nop 平台强制的 BizModel 返回实体/@Inject protected 字段"类约定大于配置——本环是模块内自发的包职责划分问题，平台规范未要求把上下文类型与编排实现塞同一包。已与 [维度01-4] 的 model↔security 环区分（性质不同）。
- **复核状态**: 未复核

## 依赖合规性总结

| 依赖 | 声明 scope | main 是否使用 | test 是否使用 | 合规性 |
|------|-----------|--------------|--------------|--------|
| `nop-ai-toolkit` | compile | 是（14 文件） | 是 | 合规 |
| `nop-ai-core` | compile | 是（4 文件） | 是 | 合规 |
| `nop-ai-api`（经 toolkit 传递） | compile (transitive) | 是（大量） | 是 | 合规 |
| `nop-message-core` | compile | **否（0 引用）** | 是 | **不合规**（见 [维度01-2]） |
| `nop-dao` | compile | **否（0 引用）** | 是 | **不合规**（见 [维度01-1]） |
| `nop-autotest-junit` | test | 否 | 是 | 合规 |
| `nop-record-mapping` | test | **main 资源依赖其类与 schema** | 是 | **不合规**（见 [维度01-3]） |

平台核心包（nop-core/nop-commons/nop-api-core/nop-xlang/nop-markdown）经多条传递链稳定获得，不算问题。main 代码**未发现**对 nop-orm/ioc/config/web/biz/graphql/quarkus/spring/auth/job/task/wf/http/search/stream 等的引用，对外包边界干净。

## 隐性耦合清单

1. main 资源 → test-scope 库（nop-record-mapping）：[维度01-3]，P2
2. nop-dao compile 但 main 零用：[维度01-1]，P2
3. nop-message-core compile 但 main 零用：[维度01-2]，P2

未发现的隐性耦合类型（已显式排除）：无 main Java 代码 import test-scope 库；无 main 代码 import 未声明的 nop-orm/ioc/config/web/biz/graphql；无 Spring/Java EE 依赖泄漏。

## 包结构评估

`io.nop.ai.agent.*` 共 17 个子包，15/17 职责清晰。问题项：`engine` 包职责过载（[维度01-5]，P3）；`model`↔`security` 业务语义环（[维度01-4]，P3）。包间依赖基本单向。

## 维度复核结论

逐条复核（主 agent 基于子 agent 已提供的四重印证证据）：

| 发现 | 复核结论 | 理由 |
|------|---------|------|
| [维度01-1] nop-dao scope 过宽 | **保留 P2** | dependency:tree + main grep 0 命中 + DB* 类源码注释明示 raw JDBC，证据确凿。属真实 scope 过宽。 |
| [维度01-2] nop-message-core scope 过宽 | **保留 P2** | 与 01-1 同类，main grep 0 命中 + 自研 message 包并存。 |
| [维度01-3] main 资源引用 test-scope 库 | **保留 P2** | register-model.xml/record-mappings.xml 的 class/schema 引用指向 nop-record-mapping，且 pom 标 test scope。`optional="true"` 降级路径经 RegisterModelDiscovery 源码验证。 |
| [维度01-4] model↔security 环 | **保留 P3** | 双向 import 可证实（PathRuleModel→PathAccessDecision，security→PathRuleModel）。属架构卫生，影响有限。 |
| [维度01-5] engine hub-spoke 环 | **保留 P3** | 12 个双向边可证实。compact→ReActAgentExecutor 具体类引用是较紧耦合。架构卫生项。 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 01-1 | P2 | pom.xml:31-34 | nop-dao compile scope 但 main 代码零引用 |
| 01-2 | P2 | pom.xml:27-30 | nop-message-core compile scope 但 main 代码零引用 |
| 01-3 | P2 | register-model.xml + record-mappings.xml | main 资源引用 test-scope 的 nop-record-mapping，运行期静默降级 |
| 01-4 | P3 | model/PathRuleModel.java ↔ security/* | model 与 security 双向 import 循环 |
| 01-5 | P3 | engine/* ↔ 14 个子包 | engine 包混合共享类型与编排实现，与 14 子包成环 |
