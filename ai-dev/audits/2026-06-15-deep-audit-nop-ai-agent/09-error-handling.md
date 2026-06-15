# 维度09：错误处理与错误码

## 范围与基准事实

- **审计范围**：`nop-ai/nop-ai-agent/src/main/java/**`（排除 `_gen/`）
- **基准文档**：`docs-for-ai/02-core-guides/error-handling.md` 的两档策略
- **关键事实（已核实）**：
  1. `nop-api-core`（含 `NopException` + `ErrorCode`）已在 classpath（经 `nop-ai-core`/`nop-ai-toolkit` 传递）。"`extends RuntimeException`"不是受迫约束。
  2. 同级模块 `nop-ai-api` 已正确实现：`nop-ai-api/.../NopAiException.java` `extends NopException` 且提供 `(String)` 和 `(ErrorCode)` 构造器——error-handling.md 引用的模板。`nop-ai-agent` 与之不一致。
  3. error-handling.md 第 163 行明确将"自定义异常类不继承 `NopException`"列为反模式。
- **干净项**：无 `printStackTrace`/`System.out`/`System.err`；核实的 catch-rethrow 均保留异常链；无非英文 throw 消息；降级 catch 均带 WARN/ERROR 日志。

## 第 1 轮（初审）

### [维度09-01] 模块异常类 `NopAiAgentException extends RuntimeException` 违反两档策略反模式
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/NopAiAgentException.java:1-12`
- **证据片段**:
  ```java
  package io.nop.ai.agent.engine;

  public class NopAiAgentException extends RuntimeException {
      public NopAiAgentException(String message) {
          super(message);
      }
      public NopAiAgentException(String message, Throwable cause) {
          super(message, cause);
      }
  }
  ```
- **严重程度**: P1
- **现状**: 模块唯一的异常类直接继承 `RuntimeException`，既未继承 `NopException`，也无 `(ErrorCode)` 构造器。
- **风险**: error-handling.md 第 163 行将"`extends RuntimeException`"显式列为反模式。后果：模块内 ~80 处 `throw new NopAiAgentException(...)` 全部绕过框架异常体系——丢失 `.param(...)` 链式上下文、`ErrorCode` 稳定错误码、i18n 翻译、上游对 `NopException` 的统一 catch/转换。`IAgentEngine` 多处 Javadoc 已承诺"fails fast with a NopAiAgentException"，但该异常实际无法被框架错误机制识别。
- **建议**: 改为 `extends NopException`，并对照同级 `NopAiException` 增加 `(ErrorCode)` / `(ErrorCode, Throwable)` 构造器。`nop-api-core` 已在 classpath，零新依赖；改动集中在单文件。
- **信心水平**: 确定
- **误报排除**: 已核实 `NopException` 经 `nop-ai-core`/`nop-ai-toolkit` 传递可用；已核实同级 `nop-ai-api/NopAiException` 正确继承；该类确为模块业务异常（非编程错误专用）。
- **复核状态**: 未复核

### [维度09-02] 大量裸 `IllegalArgumentException` 用于前置条件校验，与模块自身的 `NopAiAgentException` 用法内部不一致
- **文件**: 涉及 ~30 个 throw 站点，分布于 `skill/CuratorConfig.java:76,79,82,86`、`skill/LLMCurator.java:55`、`completion/LlmJudgeConfig.java:67,70,73,77`、`completion/LlmCompletionJudge.java:50`、`completion/CompletionRuleConfig.java:28,31`、`completion/RuleBasedCompletionJudge.java:36`、`engine/CalibratedTokenEstimator.java:41`、`message/AgentMessageEnvelope.java:35`、`message/AgentMessageTopics.java:77,84`、`message/DBMessageService.java:94,101,194`、`message/LocalAgentMessenger.java:72,76,80,85,117`、`reliability/Checkpoint.java:96,99,102,106`、`reliability/CheckpointSnapshot.java:57,60,63`、`reliability/CompactionAwareTruncation.java:69`、`security/DenialRecord.java:56`、`security/DenialRecordOutcome.java:35`、`security/DenialResult.java:64,67`、`security/ParentConstrainedPathAccessChecker.java:71,75`、`security/ParentConstrainedToolAccessChecker.java:40,44`、`security/ParentPermissionConstraint.java:112`、`security/RuleBasedPathAccessChecker.java:78,82`、`security/DBDenialLedger.java:84`
- **证据片段**（`completion/LlmJudgeConfig.java:66-79`，构造器前置校验）:
  ```java
  if (chatService == null) {
      throw new IllegalArgumentException("chatService must not be null");
  }
  if (maxTokens != null && maxTokens <= 0) {
      throw new IllegalArgumentException("maxTokens must be > 0: " + maxTokens);
  }
  if (temperature != null && (Float.isNaN(temperature) || temperature < 0.0f || temperature > 2.0f)) {
      throw new IllegalArgumentException(
              "temperature must be in [0.0, 2.0] range, got: " + temperature);
  }
  ```
- **严重程度**: P3
- **现状**: AGENTS.md 与 error-handling.md 均规定"Never bare `IllegalArgumentException`"，但模块内大量构造器/配置校验仍直接抛裸 `IllegalArgumentException`；同时模块其他位置（如 `SkillResolver:42`、`SessionFileWriter:65,68`、`SessionIds:62`）对同类 null 校验却使用 `NopAiAgentException`，存在内部不一致。
- **风险**: 上层无法用统一异常基类拦截该模块的前置条件失败；同一类语义（null/非法参数）抛两种异常类型，增加 catch 与排查成本。注意：构造器 null 校验是 IAE 最可辩护的场景，因此定 P3。
- **建议**: 统一替换为 `throw new NopAiAgentException("...")`（依赖 09-01 修复后改为继承 NopException）；范围大但机械化、低风险。
- **信心水平**: 很可能
- **误报排除**: 这些并非 enum-switch-default 豁免场景，多数是面向调用方的配置/参数契约校验。
- **复核状态**: 未复核

### [维度09-03] `Layer2TurnPruningStrategy` 用裸 `IllegalStateException` 报告上下文完整性破坏
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/Layer2TurnPruningStrategy.java:198-202`
- **证据片段**:
  ```java
  if (!calledIds.equals(respondedIds)) {
      throw new IllegalStateException(
              "Layer 2 boundary integrity violated: tool_call ids " + calledIds
                      + " do not match tool_response ids " + respondedIds);
  }
  ```
- **严重程度**: P3
- **现状**: 检测到 `tool_call` id 集合与 `tool_response` id 集合不一致时抛裸 `IllegalStateException`，绕过模块异常体系。
- **风险**: 这是上下文/数据完整性异常（消息历史损坏或上游 LLM 输出错配），属于有业务语义、需排查的错误；用裸 ISE 使上层无法用模块异常统一处理，且丢失结构化参数。
- **建议**: 改为 `throw new NopAiAgentException("Layer 2 boundary integrity violated: ...")`。
- **信心水平**: 很可能
- **误报排除**: 已确认这不是 switch-default 式不可达分支，而是对运行时消息集合的真实完整性校验。
- **复核状态**: 未复核

### [维度09-04] `IAiMemoryStore` 接口默认方法将 "Phase 2" 内部路线图术语泄露给 SPI 调用方
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/memory/IAiMemoryStore.java:15-29`
- **证据片段**:
  ```java
  default List<AiMemoryItem> readBudgeted(int maxTokens, Map<String, Object> context) {
      throw new UnsupportedOperationException("readBudgeted requires Phase 2");
  }
  default void update(String key, AiMemoryItem item) {
      throw new UnsupportedOperationException("update requires Phase 2");
  }
  default void remove(String key) {
      throw new UnsupportedOperationException("remove requires Phase 2");
  }
  default void batchAdd(List<AiMemoryItem> items) {
      throw new UnsupportedOperationException("batchAdd requires Phase 2");
  }
  ```
- **严重程度**: P2
- **现状**: 公共 SPI 接口的 4 个默认方法把"Phase 2"这一内部开发路线图阶段写进抛给调用方的异常消息。
- **风险**: 内部术语外泄污染公共 API 契约；若路线图调整（Phase 2 改名/合并），消息立即过时误导调用方；对集成方而言"Phase 2"无任何可操作含义。
- **建议**: 将消息改为面向调用方的功能性表述，例如 `"readBudgeted is not supported by this memory store implementation"`。
- **信心水平**: 确定
- **误报排除**: 已确认 `IAiMemoryStore` 是 `public interface`，且这些是 `default` 方法，属真正的对外 SPI 表面。
- **复核状态**: 未复核

### [维度09-05] `DefaultAgentEngine` 实现类将 "not yet implemented" 路线图状态泄露给运行时调用方
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:1162-1168`
- **证据片段**:
  ```java
  if ("single-turn".equals(mode)) {
      return new SingleTurnExecutor(chatService, eventPublisher);
  }
  if ("plan".equals(mode)) {
      throw new UnsupportedOperationException("Plan execution mode is not yet implemented: mode=plan");
  }
  throw new NopAiAgentException("Unknown agent execution mode: " + mode);
  ```
- **严重程度**: P2
- **现状**: 当 `mode=plan` 时抛出 `UnsupportedOperationException` 并附 "not yet implemented"，把内部实现进度状态暴露给引擎调用方；与紧随其后的"未知模式"分支（用 `NopAiAgentException`）处理风格也不一致。
- **风险**: "not yet implemented" 是开发内部状态，对调用方无可操作含义，且会随实现完成而过时；同时同一方法对"不支持的模式"用了两种异常类型，契约不一致。
- **建议**: 统一用 `throw new NopAiAgentException("Unsupported agent execution mode: " + mode + " (supported: react, single-turn)")`，去掉路线图术语。
- **信心水平**: 确定
- **误报排除**: 已核实该分支位于 `resolveExecutor` 实现方法内（非接口默认方法、非不可达 switch），是真实运行时路径。
- **复核状态**: 未复核

### [维度09-06] `ISessionStore` 默认方法错误消息引用不存在的实现类 `VfsSessionStore`
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/ISessionStore.java:124-142`
- **证据片段**:
  ```java
  default String forkSession(...) {
      throw new UnsupportedOperationException("forkSession requires VfsSessionStore");
  }
  default long appendEvent(String sessionId, VfsEvent event) {
      throw new UnsupportedOperationException("appendEvent requires VfsSessionStore");
  }
  default CompactionResult compact(...) {
      throw new UnsupportedOperationException("compact requires VfsSessionStore");
  }
  default SessionSnapshot loadSnapshot(...) {
      throw new UnsupportedOperationException("loadSnapshot requires VfsSessionStore");
  }
  default void setPlanRef(...) {
      throw new UnsupportedOperationException("setPlanRef requires VfsSessionStore");
  }
  ```
- **严重程度**: P3
- **现状**: 5 个接口默认方法的错误消息都指向 `VfsSessionStore`，但该类**在模块中不存在**（`session/` 包下仅有 `FileBackedSessionStore`、`InMemorySessionStore`、`DBSessionStore`，唯一含 "Vfs" 的是 `VfsEvent.java`）。实际持久化实现是 `FileBackedSessionStore`。
- **风险**: 调用方按错误消息去寻找 `VfsSessionStore` 会扑空，被误导排查方向；消息既泄露了（错误的）实现类名，又因类名不存在而事实性错误，双重缺陷。
- **建议**: 改为面向能力的描述（如 `"forkSession requires a persistent/vfs-backed session store (e.g. FileBackedSessionStore)"`），与同文件 `save`/`listAllSessions` 的表述风格统一。
- **信心水平**: 确定
- **误报排除**: 已用 glob 核实 `session/` 目录下无 `VfsSessionStore` 类；同文件 `save` 方法已使用正确的 `FileBackedSessionStore`。
- **复核状态**: 未复核

### [维度09-07] `ArgumentStructureRepairStage` 静默吞异常（`catch (Exception ignored)` 无日志）
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/repair/ArgumentStructureRepairStage.java:56-65`
- **证据片段**:
  ```java
  String json = (String) rawArgs;
  try {
      Object parsed = JSON.parse(json);
      if (parsed instanceof Map) {
          return new LinkedHashMap<>((Map<String, Object>) parsed);
      }
  } catch (Exception ignored) {
      // Malformed JSON — fall through to empty map
  }
  return new LinkedHashMap<>();
  ```
- **严重程度**: P3
- **现状**: 解析 LLM 产出的 JSON 参数时捕获 `Exception`，变量名直接命名为 `ignored` 且无任何日志，静默回退到空 map。
- **风险**: 完全无 debug 级日志会让"为什么参数被丢弃成空 map"在生产中无法追溯。模块其他降级路径均带 WARN 日志，此处风格不一致。
- **建议**: 至少加一行 `LOG.debug("normalizeArguments: malformed JSON, falling back to empty map", ignored)`。
- **信心水平**: 很可能
- **误报排除**: 这是真实的 `catch (...) {}` 空体模式（仅有注释、无日志、无重新抛出）。
- **复核状态**: 未复核

## 维度复核结论

待复核。

## 子项复核结论

待复核。

## 最终保留项

待复核后填写。
