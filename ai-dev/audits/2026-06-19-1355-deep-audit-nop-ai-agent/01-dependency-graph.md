# 维度 01：依赖图与模块边界 — nop-ai-agent

## 第 1 轮（初审）

### [维度01-1] `nop-dao` 声明为 compile scope，但仅被测试代码使用（35 个测试文件，主代码 0 引用）

- **文件**: `nop-ai/nop-ai-agent/pom.xml:31-34`（声明处）；对照 `src/main/java/**`（0 个引用）与 `src/test/java/**`（35 个引用，例如 `src/test/java/io/nop/ai/agent/session/TestDBSessionStore.java:13`）
- **证据片段**:
  ```xml
  <!-- pom.xml:31-34 — compile scope（默认） -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-dao</artifactId>
  </dependency>
  ```
  ```java
  // src/test/java/io/nop/ai/agent/session/TestDBSessionStore.java:13（仅测试代码使用）
  import io.nop.dao.jdbc.datasource.SimpleDataSource;
  ```
  ```java
  // src/main/java/io/nop/ai/agent/session/DBSessionStore.java:3-15（主代码的持久化实现走 raw JDBC，不依赖 nop-dao）
  import io.nop.ai.agent.engine.NopAiAgentException;
  import io.nop.ai.agent.security.ITenantResolver;
  import io.nop.ai.agent.security.NullTenantResolver;
  import io.nop.ai.agent.security.TenantSql;
  import javax.sql.DataSource;
  import java.sql.Connection;
  import java.sql.PreparedStatement;
  import java.sql.ResultSet;
  ```
- **严重程度**: P2
- **现状**: `nop-dao` 在 pom.xml 中以 compile scope 声明，但主代码（`src/main/java`）的 0 个文件 import 了 `io.nop.dao.*` 任何类。`DBSessionStore` / `DBMessageService` / `DBDenialLedger` / `DbSessionTakeoverLock` / `DBCheckpointManager` / `DbUsageRecorder` 等持久化类全部使用 JDK 原生 `javax.sql.DataSource` + `java.sql.PreparedStatement`（raw JDBC），与 nop-dao 抽象无关。`nop-dao` 的唯一实际消费者是测试代码中的 `io.nop.dao.jdbc.datasource.SimpleDataSource`（35 个测试文件用于 DB-backed 集成测试）。
- **风险**: (1) `nop-dao` 通过传递依赖把 `HikariCP`（已在 nop-dao/pom.xml 确认）等连接池相关 jar 泄漏到所有下游消费者的 **compile** classpath，即使消费者只使用 `InMemorySessionStore` 默认实现。(2) 依赖声明误导后续维护者以为主代码需要 nop-dao 抽象，可能误向 `io.nop.dao.*` 演进（破坏 raw-JDBC 设计选择）。(3) 与同 pom 中 `nop-autotest-junit` / `nop-record-mapping` 显式标注 `<scope>test</scope>` 的做法不一致——这两个测试专用依赖正确标注了 scope，唯独 `nop-dao` / `nop-message-core`（见 [维度01-2]）遗漏。
- **建议**: 将 `nop-dao` 的 scope 改为 `test`（与 `nop-autotest-junit` / `nop-record-mapping` 一致）。`SimpleDataSource` 仅测试需要。如果未来主代码确需 nop-dao 抽象（如切换到 IOrmSession），再恢复 compile scope 并补充对应主代码引用。
- **信心水平**: 确定（主代码 import 全量聚合已用 `grep -rh | sed | sort | uniq -c` 验证，`io.nop.dao` 前缀计数为 0；`grep -rl` 对 `src/main/java` 计数为 0，对 `src/test/java` 计数为 35）
- **误报排除**: 这不是"多模块 pom.xml 中必要的传递依赖显式声明（Maven 最佳实践）"误报。该豁免口径适用于"主代码确实通过传递链稳定使用、显式声明以锁版本"的场景。此处主代码完全未使用 nop-dao 任何类，显式声明并无版本锁定作用（无 `<version>` 标签），且与同 pom 中已正确标 test scope 的两个测试依赖形成鲜明对比，是真实的 scope 标注遗漏。
- **复核状态**: 未复核

---

### [维度01-2] `nop-message-core` 声明为 compile scope，但仅被测试代码使用（14 个测试文件，主代码 0 引用）

- **文件**: `nop-ai/nop-ai-agent/pom.xml:27-30`（声明处）；对照 `src/main/java/**`（0 个引用）与 `src/test/java/**`（14 个引用，例如 `src/test/java/io/nop/ai/agent/message/TestLocalAgentMessenger.java:7`）
- **证据片段**:
  ```xml
  <!-- pom.xml:27-30 — compile scope（默认） -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-message-core</artifactId>
  </dependency>
  ```
  ```java
  // src/test/java/io/nop/ai/agent/message/TestLocalAgentMessenger.java:7（仅测试代码使用 nop-message-core 的实现类）
  import io.nop.message.core.local.LocalMessageService;
  ```
  ```java
  // src/main/java/io/nop/ai/agent/message/DBMessageService.java:7-13（主代码只用 IMessageService 接口，来自 nop-api-core）
  import io.nop.api.core.message.ConsumeLater;
  import io.nop.api.core.message.IMessageConsumeContext;
  import io.nop.api.core.message.IMessageConsumer;
  import io.nop.api.core.message.IMessageService;          // ← 接口来自 nop-api-core（io.nop.api.core.message）
  import io.nop.api.core.message.IMessageSubscription;
  import io.nop.api.core.message.MessageSendOptions;
  import io.nop.api.core.message.MessageSubscribeOptions;
  ```
- **严重程度**: P2
- **现状**: `nop-message-core` 在 pom.xml 中以 compile scope 声明，但主代码的 0 个文件 import 了 `io.nop.message.*`（即 nop-message-core 自有包）。主代码使用的 `IMessageService` / `IMessageConsumer` / `IMessageSubscription` / `MessageSendOptions` 等消息抽象接口全部来自 `io.nop.api.core.message.*` 包——该包属于 **nop-api-core**（平台核心包，通过传递链稳定获得）。`nop-message-core` 的唯一实际消费者是测试代码中的 `io.nop.message.core.local.LocalMessageService`（14 个测试文件，用作 `LocalAgentMessenger` 的内存消息总线实现）。
- **风险**: (1) `nop-message-core` 把 nop-core、nop-dataset 等传递依赖泄漏到下游消费者的 compile classpath。(2) 架构设计上，Agent Engine Layer 应只依赖消息**抽象**（IMessageService），具体实现（LocalMessageService）由宿主/测试注入。当前 compile scope 让"主代码不依赖消息实现"这一架构约束在依赖图层面被掩盖——读者无法从 pom.xml 看出主代码与实现解耦。(3) 与 [维度01-1] 同源，scope 标注不一致。
- **建议**: 将 `nop-message-core` 的 scope 改为 `test`。主代码仅依赖 nop-api-core 提供的 `IMessageService` 抽象（已通过 nop-ai-toolkit → nop-ai-api → nop-api-core 传递链获得）。测试需要 LocalMessageService 作为内存实现，test scope 足够。
- **信心水平**: 确定（`io.nop.message` 前缀在主代码聚合计数为 0；`grep -rl` 对主代码计数为 0，对测试代码计数为 14；`io.nop.api.core.message.*` 的 14 处 import 已逐条核对来自 nop-api-core）
- **误报排除**: 这不是"平台核心包通过传递链稳定获得不算隐性耦合"误报的反向情形。此处问题不是"用了但没声明"，而是"声明了 compile 但主代码完全没用"——是 scope 标注错误，会导致不必要的传递依赖泄漏。已确认主代码的消息抽象来自 nop-api-core（`io.nop.api.core.message`），与 nop-message-core（`io.nop.message.core`）是不同模块的不同包。
- **复核状态**: 未复核

---

## 架构红线合规性检查（全部通过，无发现）

| 检查项 | 结果 |
|---|---|
| 3a. 直接依赖 `nop-ai-tools` | 零命中 ✅ |
| 3a. import `io.nop.ai.tools.` | 零命中 ✅ |
| 3b. 直接引用 LLM Provider SDK | 仅 `ApiStyle.openai` 枚举（nop-ai-core 抽象层）✅ |
| 3b. LLM 调用是否走 nop-ai-core | 使用 `ILlmDialect` + `LlmDialectFactory`（nop-ai-core 抽象）✅ |
| 3c. 绕过 toolkit 调用工具 | 68 处全部走 `IToolExecutor`/`IToolManager`/`AiToolModel` 抽象 ✅ |
| 3e. 引擎库出现 nop-ioc/web/graphql/biz | 零命中 ✅ |
| 隐性耦合：使用 nop-orm（运行时） | 零命中 ✅ |
| 隐性耦合：使用 nop-ioc 实现类 | 零命中 ✅ |
| 循环依赖（nop-ai-core 反向引用） | 零命中 ✅ |
| 循环依赖（nop-ai-toolkit 反向引用） | 零命中 ✅ |
| 5. `agent.xdef` 存在性 | 存在于 nop-kernel/nop-xdefs ✅ |
| 5. `agent-plan.xdef` 存在性 | 存在于 nop-kernel/nop-xdefs ✅ |
| 6. 手写 model 正确继承 `_gen._*` | 36 处正确引用 ✅ |

### 关于 record-mapping 的 scope 说明（非发现，仅记录）

`agent-plan.register-model.xml` 引用 `io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory`（来自 `nop-record-mapping`），而 `nop-record-mapping` 在 pom.xml 中是 test scope。这不是问题：loader 显式标注 `optional="true"`，类缺失时框架优雅跳过；XML/YAML 是主加载路径；Markdown 加载是可选 dev/test 便利特性。

---

## 依赖关系小结

```
nop-ai-agent 直接依赖（pom.xml compile scope）
│
├── nop-ai-toolkit  ──┐  工具 DSL 抽象（IToolExecutor / AiToolModel）       ✅ 合规
├── nop-ai-core     ──┤  LLM 抽象（IChatService / ILlmDialect / ApiStyle）  ✅ 合规
├── nop-message-core ─┤  ⚠️ 仅测试用 LocalMessageService（14 测试文件）       ⚠️ [维度01-2]
├── nop-dao         ──┤  ⚠️ 仅测试用 SimpleDataSource（35 测试文件）          ⚠️ [维度01-1]
├── nop-task-core   ──┘  任务流（ITaskStepRuntime / GraphTaskStep）         ✅ 合规
│
├── [test] nop-autotest-junit   ✅ 正确标注 test scope
└── [test] nop-record-mapping   ✅ 正确标注 test scope
```

**架构边界合规性判断**：
- 红线 1（不依赖 nop-ai-tools）：✅ 完全合规
- 红线 2（LLM 走 nop-ai-core 抽象）：✅ 完全合规
- 红线 3（工具走 nop-ai-toolkit 抽象）：✅ 完全合规
- 配置与执行分离：✅ 合规
- 循环依赖：✅ 无

**唯一待改进项**：[维度01-1] 与 [维度01-2] 的 scope 标注遗漏（P2），属依赖卫生问题，非架构边界违约。

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。
