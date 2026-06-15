# 维度 09：错误处理与错误码（nop-ai-agent）

## 第 1 轮（初审）

### [维度09-1] NopAiAgentException 继承 RuntimeException 而非 NopException — 模块异常类脱离框架异常体系

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/NopAiAgentException.java:3-12`
- **证据片段**:
  ```java
  package io.nop.ai.agent.engine;

  public class NopAiAgentException extends RuntimeException {   // ← 应为 extends NopException

      public NopAiAgentException(String message) {
          super(message);
      }

      public NopAiAgentException(String message, Throwable cause) {
          super(message, cause);
      }
  }
  ```
- **严重程度**: P1
- **现状**: 该类是模块内**唯一**的自定义异常类，被 118 处抛出引用。但它继承 `RuntimeException` 而非 `NopException`。这与平台规范 `docs-for-ai/02-core-guides/error-handling.md` 明确点名的反模式完全一致："自定义异常类不继承 NopException（如 extends RuntimeException）| 绕过框架异常体系，丢失 ErrorCode、i18n、结构化错误响应等能力"。本类：① 没有 `(ErrorCode)` 构造器；② 没有 `serialVersionUID`；③ 不支持 `.param(...)` 链式上下文。
- **风险**: 一旦本模块被上层（如未来的 GraphQL/BizModel/服务编排）消费，这些异常**不会**被框架的统一 `ApiResponse` 错误响应机制识别，只会作为普通 `RuntimeException` 冒泡，丢失结构化错误码、参数、i18n 能力。这是核心契约漂移。`NopException` 已在 classpath 上（经 nop-ai-core → nop-api-core 传递依赖），技术上可立即修正。
- **建议**: 改为 `extends NopException`，补齐 `serialVersionUID`，同时提供 `(String)`、`(String, Throwable)`、`(ErrorCode)`、`(ErrorCode, Throwable)` 四构造器。
- **信心水平**: 高
- **误报排除**: 已确认 `NopException` 在依赖链上；已确认本类无任何 `.param(...)` 调用。
- **复核状态**: 未复核

### [维度09-2] 49 处 IllegalArgumentException 散布于 24 个文件，与模块自身异常类不一致

- **文件**: 涉及 24 文件，典型代表 `reliability/Checkpoint.java:95-108`、`security/DBDenialLedger.java:83-86`、`security/RuleBasedPathAccessChecker.java:76-84`
- **证据片段**（Checkpoint.java:95-108，可靠性关键路径）:
  ```java
  if (watermark == null) {
      throw new IllegalArgumentException("Checkpoint.watermark must not be null");
  }
  if (type == null) {
      throw new IllegalArgumentException("Checkpoint.type must not be null");
  }
  if (seq < 0) {
      throw new IllegalArgumentException(
              "Checkpoint.seq must not be negative, got: " + seq);
  }
  ```
  （DBDenialLedger.java:81-88，安全关键路径）:
  ```java
  public DBDenialLedger(DataSource dataSource, int denialThreshold) {
      this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
      if (denialThreshold <= 0) {
          throw new IllegalArgumentException(
                  "denialThreshold must be positive, got: " + denialThreshold);
      }
      this.denialThreshold = denialThreshold;
      initSchema();
  }
  ```
- **严重程度**: P2
- **现状**: 模块已定义 `NopAiAgentException` 作为统一异常类（其余 118 处都用它），但校验型异常却改用 `IllegalArgumentException`，出现"同一模块两套异常风格"的内部分裂。分布集中在安全包（9 处）、reliability 包（7 处）、message 包（9 处）、completion/skill 配置类（13 处）。
- **风险**: 一致性成本——上层 catch 若想统一捕获模块异常，会漏掉这些 `IllegalArgumentException`。在安全/可靠性关键路径上，这种"校验异常游离于模块异常体系之外"会增大错误处理分支的覆盖难度。
- **建议**: 将这些 `IllegalArgumentException` 统一改为 `NopAiAgentException`（在发现 09-1 修正为 extends NopException 之后）。
- **信心水平**: 中
- **误报排除**: 已逐一核对这些类均为模块内部具体类，非 -api 模块的跨模块接口契约。
- **复核状态**: 未复核

### [维度09-3] Layer2TurnPruningStrategy 用 IllegalStateException 表达不变量破坏

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/Layer2TurnPruningStrategy.java:198-202`
- **证据片段**:
  ```java
  if (!calledIds.equals(respondedIds)) {
      throw new IllegalStateException(
              "Layer 2 boundary integrity violated: tool_call ids " + calledIds
                      + " do not match tool_response ids " + respondedIds);
  }
  ```
- **严重程度**: P2
- **现状**: 这是模块内唯一的 `IllegalStateException`，位于内存压缩策略的边界完整性自检。
- **风险**: 上层无法用单一 `catch (NopAiAgentException)` 或 `catch (NopException)` 捕获该类失败，调试时以非模块异常冒泡。
- **建议**: 改为 `throw new NopAiAgentException("Layer 2 boundary integrity violated: ...")`。
- **信心水平**: 中
- **误报排除**: 已确认这是内部不变量自检（私有调用链），非公共契约。
- **复核状态**: 未复核

### [维度09-4] CheckpointJournalReader 使用 java.util.logging，与全模块 SLF4J 风格不一致

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/CheckpointJournalReader.java:13-14, 40`
- **证据片段**:
  ```java
  import java.util.logging.Level;
  import java.util.logging.Logger;
  ...
  private static final Logger LOG = Logger.getLogger(CheckpointJournalReader.class.getName());
  ```
- **严重程度**: P3
- **现状**: 全模块 23 个文件使用 `org.slf4j.Logger`，唯独 `CheckpointJournalReader` 使用 JDK 内置 `java.util.logging`（JUL）。JUL 的日志级别与 SLF4J 的 API 不同。
- **风险**: 同一进程内两套日志后端，运维无法用统一的 SLF4J 配置统一控制日志级别/输出。
- **建议**: 改用 SLF4J（`LoggerFactory.getLogger(...)` + `LOG.warn(...)`）。
- **信心水平**: 高
- **误报排除**: 已确认全模块仅此一处 JUL；其余含日志的文件均为 SLF4J。该类同目录的 Writer/Reader 也用 SLF4J，属同侪不一致。
- **复核状态**: 未复核

## 反模式 throw 统计

| 异常类型 | 数量 | 性质 |
|---|---|---|
| `NopAiAgentException` | 118 | 合规（但继承错误，见 09-1） |
| `IllegalArgumentException` | 49 | 反模式（见 09-2） |
| `UnsupportedOperationException` | 20 | 合规（接口 default 方法的"可选操作不支持"语义） |
| `SQLException` | 1 | 合规（私有 JDBC helper） |
| `IllegalStateException` | 1 | 反模式（见 09-3） |

ErrorCode 定义数：0（模块定位为内部 AI Agent 库，无 ErrorCode 本身合理；但与 09-1 耦合）。

排除项（已核查非问题）：20 处 UnsupportedOperationException 是接口 default 方法的合法"可选操作不支持"语义；1 处 SQLException 是 JDBC 边界翻译；无 catch 吞异常；无 System.out/err；无中文错误消息；异常链保留完整。

## 维度复核结论

| 发现 | 复核结论 | 理由 |
|------|---------|------|
| [维度09-1] NopAiAgentException extends RuntimeException | **保留 P1** | 源码确证 + 平台规范明文点名此反模式。影响 118 处抛出 + 解锁 ErrorCode 能力。 |
| [维度09-2] 49 处 IllegalArgumentException | **保留 P2** | grep 统计可证实，集中安全/可靠性关键路径。 |
| [维度09-3] IllegalStateException | **保留 P2** | 模块内唯一一处，与 09-1/09-2 同类。 |
| [维度09-4] JUL 日志 | **保留 P3** | import 确证，全模块唯一一处 JUL。 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 09-1 | P1 | engine/NopAiAgentException.java | 异常类继承 RuntimeException 而非 NopException，脱离框架异常体系 |
| 09-2 | P2 | 24 个文件（reliability/security/message/skill/completion） | 49 处 IllegalArgumentException 与模块异常类不一致 |
| 09-3 | P2 | compact/Layer2TurnPruningStrategy.java:198 | 用 IllegalStateException 表达不变量破坏 |
| 09-4 | P3 | reliability/CheckpointJournalReader.java | 使用 java.util.logging 与全模块 SLF4J 不一致 |
