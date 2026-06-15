# 维度01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] `nop-message-core` 声明为 compile scope，但 main 源码未引用任何 `io.nop.message.core.*` 类
- **文件**: `nop-ai/nop-ai-agent/pom.xml:27-30`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-core</artifactId>
  </dependency>
  ```
- **严重程度**: P2
- **现状**: `nop-message-core` 在 pom 中是默认 compile scope（无 `<scope>` 标签），但对 `src/main/java/**` 241 个 Java 文件做 `grep -E "^import io\.nop\.message\."` 结果为零。所有 message 相关 main 代码（`LocalAgentMessenger`、`DBMessageService`、`NoOpAgentMessenger` 等）只引用 `io.nop.api.core.message.*` 接口（`IMessageService`/`IMessageConsumer`/`IMessageSubscription`），这些来自 `nop-api-core`，而非 `nop-message-core`。`nop-message-core` 中具体的 `LocalMessageService` 等类仅出现在 main 源码的 Javadoc 文本里（如 `LocalAgentMessenger.java:19`、`DBMessageService.java:36`），不是 import。仅在 test 源码里被实际 import（如 `TestLocalAgentMessenger.java:7` `import io.nop.message.core.local.LocalMessageService;`）。
- **风险**: compile scope 会让该依赖通过传递闭包进入所有消费方。任何仅使用 `NoOpAgentMessenger` 或 `DBMessageService`（后者用裸 JDBC，不需要 `nop-message-core`）的消费方仍会无谓拉入 `nop-message-core`，造成模块边界膨胀。同时 pom 隐式暗示 main 代码直接依赖 `nop-message-core`，但实际并非如此，形成"pom 撒谎"的维护陷阱——未来有人重构 `nop-message-core` 时无法从 `nop-ai-agent/pom.xml` 准确判断影响面。
- **建议**: 将 `<scope>test</scope>` 显式加到该 dependency；同时在 `LocalAgentMessenger` 的类级 Javadoc 增加"运行时需消费方自行提供 `IMessageService` 实现（如 `nop-message-core` 的 `LocalMessageService`）"的契约说明，把运行时契约从 pom 隐式承诺迁移到文档显式承诺。
- **信心水平**: 确定
- **误报排除**: 这不属于审计指令中"无需显式声明的平台核心包"豁免清单（豁免清单是 `nop-api-core`/`nop-commons`/`nop-core`/`nop-xlang`/`nop-markdown`）；`nop-message-core` 是业务/集成模块，且 main 源码确实零引用其类。
- **复核状态**: 未复核

### [维度01-02] `nop-dao` 声明为 compile scope 且重复声明，但 main 源码未引用任何 `io.nop.dao.*` 类
- **文件**: `nop-ai/nop-ai-agent/pom.xml:31-34`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-dao</artifactId>
  </dependency>
  ```
- **严重程度**: P2
- **现状**: 对 `src/main/java/**` 执行 `grep -E "^import io\.nop\.dao" -r` 结果为空。所有 DB 相关 main 类（`DBSessionStore`、`DBCheckpointManager`、`DBDenialLedger`、`DBMessageService`）只用 JDK 自带的 `java.sql.*` + `javax.sql.DataSource`（裸 JDBC），完全绕开 `nop-dao` 的 `IEntityDao`/`SqlLib`/`IEntityManager`/`ITransactionTemplate` 等抽象。`io.nop.dao.*` 类只在 test 源码里被引用（如 `TestDBSessionStoreEngineWiring.java` 等 10 个文件用 `io.nop.dao.jdbc.datasource.SimpleDataSource` 作为测试用内存数据源）。同时 `nop-ai-core/pom.xml:26-28` 已经声明 `nop-dao` 为 compile，所以本模块即使删除该声明，传递依赖也会让 `nop-dao` 仍然可用。
- **风险**: 三重问题：(1) scope 不正确——实际只在 test 用，应为 test scope；(2) 显式声明与 `nop-ai-core` 的传递声明重复，违反"每个依赖在最小可表达的 pom 里声明一次"原则；(3) 对消费方而言，本模块 pom 暗示"我用了 nop-dao 的 API"，但实际没用，未来 `nop-dao` 重构（如拆分 jdbc 包）将无法准确定位受影响模块。
- **建议**: 两种修复方向二选一：(A) 改为 `<scope>test</scope>` 并保留声明（清晰表达测试用途）；(B) 直接从 pom 删除该依赖（依赖 `nop-ai-core` 传递，与豁免清单"通过传递链稳定获取"的思路一致）。推荐方向 (A)。
- **信心水平**: 确定
- **误报排除**: `nop-dao` 不在豁免清单中（豁免清单只有 5 个平台核心包）；这是直接 import 检查 + 调用图验证后确认零引用，不是误报。
- **复核状态**: 未复核

### [维度01-03] `app.orm.xml` 声明了 4 个不存在的 Java 实体类，造成持久化层契约漂移
- **文件**: `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/agent/orm/app.orm.xml:27-146`
- **证据片段**:
  ```xml
  <entities>
      <entity name="io.nop.ai.agent.message.AiAgentMessage" tableName="ai_agent_message" ...>
      ...
      <entity name="io.nop.ai.agent.security.AiAgentDenial" tableName="ai_agent_denial" ...>
      ...
      <entity name="io.nop.ai.agent.session.AiAgentSession" tableName="ai_agent_session" ...>
      ...
      <entity name="io.nop.ai.agent.reliability.AiAgentCheckpoint" tableName="ai_agent_checkpoint" ...>
  </entities>
  ```
- **严重程度**: P2
- **现状**: ORM 文件声明 4 个实体类（`io.nop.ai.agent.message.AiAgentMessage` 等），但全模块 `glob` 验证这些类**不存在**。源码里只有同包下的 `AiAgentMessageTable`、`AiAgentSessionTable`、`AiAgentDenialTable`、`AiAgentCheckpointTable`——它们是仅含 SQL DDL 字符串常量的 final 工具类（如 `AiAgentSessionTable.java:35-48` 硬编码 `CREATE TABLE IF NOT EXISTS ai_agent_session (...)`）。实际所有 DB 读写代码（`DBSessionStore.java:207-214` 的 `MERGE into` 语句、`DBCheckpointManager.java:110-122` 的 `INSERT INTO` 语句）都直接拼接 SQL 字符串，与 ORM 实体声明完全脱钩。更糟的是 `AiAgentSessionTable.java:9-11` 的 Javadoc 写着 "The table schema is defined in the ORM model at `_vfs/nop/ai/agent/orm/app.orm.xml`"——把 ORM 文件标注为权威 schema 来源，但实际权威来源是 DDL 字符串常量。
- **风险**: 当消费方应用启动时，Nop ORM 聚合器会把 `_vfs/nop/ai/agent/orm/app.orm.xml` 合并进 app 级 `_app.orm.xml`，框架的元数据注册表会期望这 4 个实体类存在。如果消费方尝试通过 `IEntityDao<AiAgentSession>` 访问这些表（自然的 Nop 模式），会在反射加载时 `ClassNotFoundException`。即便不触发，schema 也有两份真相：DDL 字符串常量（实际生效）vs ORM XML（文档+元数据注册），将来给一张表加列时极易只改一边，造成漂移。
- **建议**: 两种修复方向二选一：(A) 如果设计就是"裸 JDBC + 自管 DDL"，应删除 `app.orm.xml` 或把它重命名为不被 ORM 聚合器扫描的形式，并在 `*Table` 类 Javadoc 中去掉"schema 定义在 ORM 模型里"的误导性描述；(B) 如果设计是希望未来走 ORM，应通过 codegen 从 `app.orm.xml` 生成实体类，并把 `DB*Store` 改为基于 `IEntityDao` 的实现。当前两个方向都未做，是悬空状态。
- **信心水平**: 确定
- **误报排除**: `app.orm.xml` 不是 `_` 前缀的生成物（不是 `_app.orm.xml`），是手写源模型文件，因此不属于"代码生成产物豁免"范围；该文件由人维护、参与运行时 ORM 聚合，是直接审计对象。
- **复核状态**: 未复核

### [维度01-04] `nop-record-mapping` 声明为 test scope，但 main 资源文件引用其类 `io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory`
- **文件**: `nop-ai/nop-ai-agent/pom.xml:41-45` 配合 `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent-plan.register-model.xml:7-8`
- **证据片段**:
  ```xml
  <!-- pom.xml: test scope -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-record-mapping</artifactId>
      <scope>test</scope>
  </dependency>
  ```
  ```xml
  <!-- agent-plan.register-model.xml:7-8 (main resources, 打包进 jar) -->
  <loader fileType="agent-plan.md" mappingName="agent-plan.Markdown_to_AgentPlanModel" optional="true"
          class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>
  ```
- **严重程度**: P2
- **现状**: 该 register-model XML 在 main 资源中（`src/main/resources/_vfs/...`），打包后随 jar 分发给消费方。它声明 `agent-plan.md` 文件类型由 `MarkdownDslResourceLoaderFactory`（`nop-record-mapping` 的 main 类）加载。但 `nop-ai-agent/pom.xml` 把 `nop-record-mapping` 声明为 `<scope>test</scope>`——这意味着该依赖**不会**传递给消费方。对 `src/main/java/**` 做 `grep "io\.nop\.record"` 也确认 main 源码不直接 import 这个类，但 main 资源文件通过字符串名引用了它（框架用反射 `Class.forName` 加载）。`nop-record-mapping` 既不在豁免清单中，也不被 `nop-ai-core` 或 `nop-ai-toolkit` 传递引入——所以消费方 classpath 里没有这个类。
- **风险**: 当消费方应用调用框架加载任何 `agent-plan.md` 文件时，框架会尝试反射实例化 `MarkdownDslResourceLoaderFactory`，触发 `ClassNotFoundException` 或 `NoClassDefFoundError`。`optional="true"` 标志可能让框架优雅降级（跳过该 loader），但即使如此，文档化的"支持 markdown plan"功能在消费方静默失效，需要消费方手动添加 `nop-record-mapping` 依赖——这是隐式契约。本模块自己的测试能跑通只是因为 test scope 提供了类。
- **建议**: 两种修复方向二选一：(A) 把 `nop-record-mapping` 的 scope 改为 compile（默认）或 `<optional>true</optional>` + 在模块 README / 类级 Javadoc 明确说明"使用 markdown plan 功能需要消费方自行引入 `nop-record-mapping`"；(B) 如果该 markdown loader 不是核心特性，把 register-model.xml 中那段 `<loader fileType="agent-plan.md" .../>` 整段删除，让该功能由下游模块按需注册。当前 main 资源声明 + test scope 依赖的组合是错的。
- **信心水平**: 很可能（`optional="true"` 让框架行为不确定，需要看 nop-core 的 register-model 实现，但 scope 不一致本身已是事实）
- **误报排除**: 这不是"`@Inject` 在 protected 字段"等已知误报；`nop-record-mapping` 不在豁免清单；main 资源文件（非 `_` 前缀生成物）的字符串类引用属于真实运行时契约。
- **复核状态**: 未复核

## 完整依赖图

### 模块级 Maven 依赖图（仅显示 compile + test，省略平台核心包）

```
                          [root pom: nop-entropy]
                                  |
                          [nop-ai (parent pom)]
                                  |
                  +---------------+---------------+
                  |                               |
        ┌──[nop-ai-api]──┐                ┌──[nop-xlang]──┐
        │                │                │               │
   [nop-ai-core]    [nop-ai-toolkit]      │               │
        │                │                │               │
        └────────┬───────┘                │               │
                 │                        │               │
            [nop-ai-agent] ◄──────────────┘               │
                 │                                        │
   ┌─────────────┼──────────────────┐                     │
   │ (compile)   │ (compile)        │ (compile)           │
   ▼             ▼                  ▼                     ▼
[nop-ai-    [nop-ai-          [nop-message-core]    [nop-dao]
 toolkit]    core]              ❌ main零引用          ❌ main零引用
                                  (仅 test 引用)        (仅 test 引用)

   ┌─────────────┬──────────────────┐
   │ (test)      │ (test)           │
   ▼             ▼                  ▼
[nop-autotest- [nop-record-mapping]
 junit]         ❌ 与 main 资源引用其类冲突
```

无 P0、P1、P3 级别发现。模块依赖是 DAG、无业务模块横向耦合、无应用层框架耦合。

## 维度复核结论

待复核。

## 子项复核结论

待复核。

## 最终保留项

待复核后填写。
