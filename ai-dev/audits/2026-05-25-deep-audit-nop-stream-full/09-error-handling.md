# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] 核心数据路径广泛使用裸 RuntimeException 而非模块定义的 StreamException

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingOutput.java:35-86`
- **证据片段**:
  ```java
  } catch (Exception e) {
      throw new RuntimeException("Error forwarding element to next operator", e);
  }
  ```
  核心数据路径（ChainingOutput 共 5 处、RecordWriter 共 4 处、OperatorChain 共 4 处、SubtaskTask 2 处等）全部使用裸 `throw new RuntimeException(...)`。
- **严重程度**: P2
- **现状**: nop-stream-core 定义了 `StreamRuntimeException` 及其子类 `StreamException` 作为流框架的专属异常类型，但核心数据路径约 25+ 处全部使用裸 `RuntimeException`。模块自身定义的 `StreamException`/`StreamRuntimeException` 实际上只被少数文件使用。
- **风险**: 调用方无法通过 `catch (StreamRuntimeException e)` 统一捕获流框架异常。异常层次定义成为死代码。生产环境监控和告警无法基于类型区分错误。
- **建议**: 将核心执行路径中的 `throw new RuntimeException(...)` 统一改为 `throw new StreamRuntimeException(...)` 或 `throw new StreamException(...)`。
- **误报排除**: 模块明确定义了异常层次却不使用，是结构性不一致。
- **复核状态**: 未复核

---

### [维度09-02] GraphModelCheckpointExecutor 同类错误使用不同异常类型

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:510-516, 577-581, 430`
- **证据片段**:
  ```java
  // 行 510: 用 RuntimeException
  throw new RuntimeException("Task failed", task.getError());
  // 行 577: 用 StreamException
  throw new StreamException("No exact task state found...");
  // 行 430: 用 IllegalStateException
  throw new IllegalStateException("No TaskLocation found for vertex: " + vertexId);
  ```
- **严重程度**: P2
- **现状**: 同一个文件中，对性质相似的框架内部错误分别使用了 `RuntimeException`、`StreamException` 和 `IllegalStateException` 三种不同异常类型。
- **风险**: 调用方无法通过单一 catch 块统一处理。
- **建议**: 统一为 `StreamException` 或 `StreamRuntimeException`。
- **误报排除**: 同一文件内对同类错误使用三种异常类型，影响调用方的 catch 策略。
- **复核状态**: 未复核

---

### [维度09-03] RecordWriter 中 InterruptedException 被包装为 RuntimeException 丢失中断语义

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/execution/RecordWriter.java:134-141, 149-157`
- **证据片段**:
  ```java
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while writing record", e);
  }
  ```
- **严重程度**: P2
- **现状**: RecordWriter 的 4 个写方法在捕获 `InterruptedException` 后正确恢复了中断标志，但随后抛出裸 `RuntimeException` 而非 `StreamRuntimeException`。
- **风险**: 调用方如果仅捕获 `StreamRuntimeException` 将无法处理写中断，中断信号可能被上层框架的通用 `catch (Exception e)` 吞掉。
- **建议**: 将 `throw new RuntimeException("Interrupted while ...")` 改为 `throw new StreamRuntimeException("Interrupted while ...", e)`。
- **误报排除**: 中断处理涉及并发语义，异常类型决定了上层框架能否正确识别并响应关闭信号。
- **复核状态**: 未复核

---

### [维度09-04] NopCepErrors 错误码描述硬编码中文消息

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/NopCepErrors.java:19-28`
- **证据片段**:
  ```java
  ErrorCode ERR_CEP_UNKNOWN_PATTERN_PART =
          define("nop.err.cep.unknown-pattern-part", "未定义的子模式:{partName}", ARG_PART_NAME);
  ```
- **严重程度**: P3
- **现状**: NopCepErrors 定义的 3 个 ErrorCode 全部使用中文作为默认描述消息。
- **风险**: 在未配置 i18n 的环境中，异常消息直接展示中文，对非中文使用者不友好。
- **建议**: 将默认描述改为英文，中文消息放到 i18n 资源文件中。
- **误报排除**: ErrorCode 的默认描述在无 i18n 配置时直接作为错误消息返回。
- **复核状态**: 未复核

---

### [维度09-05] JdbcCheckpointStorage 反序列化时静默吞掉 TaskLocation 解析异常

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:641-646`
- **证据片段**:
  ```java
  try {
      taskLocation = stringToTaskLocation(entry.getKey());
  } catch (Exception e) {
      taskLocation = new TaskLocation(jobId, pipelineId, entry.getKey(), 0);
  }
  ```
- **严重程度**: P2
- **现状**: 反序列化 `EpochManifest` 时，如果 `TaskLocation` 的字符串格式解析失败，静默构造 `taskIndex=0` 的 fallback TaskLocation，无任何日志记录。
- **风险**: 状态恢复到错误的 subtask，完全静默无日志，问题极难排查。这是数据正确性风险。
- **建议**: 至少添加 `LOG.warn` 日志。更好的做法是抛出异常。
- **误报排除**: 静默吞掉解析异常并构造可能错误的 fallback 对象，是可量化的数据正确性风险。
- **复核状态**: 未复核

---

### [维度09-06] LocalFileCheckpointStorage.extractIdFromFileName 解析失败静默返回 -1

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/LocalFileCheckpointStorage.java:244-251`
- **证据片段**:
  ```java
  private long extractIdFromFileName(String fileName, String suffix) {
      String name = fileName.replace(suffix, "");
      try {
          return Long.parseLong(name);
      } catch (NumberFormatException e) {
          return -1;
      }
  }
  ```
- **严重程度**: P3
- **现状**: 从文件名提取 checkpoint ID 失败时静默返回 -1，无日志记录。
- **风险**: 残留的临时文件会被作为 ID=-1 的 checkpoint 参与排序和清理逻辑。
- **建议**: 在 catch 分支添加日志并考虑跳过 ID=-1 的文件。
- **误报排除**: 静默返回哨兵值而不记录日志，属于可观测性缺失。
- **复核状态**: 未复核

---

### [维度09-07] TaskManager 心跳失败和 checkpoint ACK 发送失败仅记日志未抛异常

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:160-168, 300-312`
- **证据片段**:
  ```java
  // 心跳失败
  } catch (Exception e) {
      LOG.error("Heartbeat failed for node {}", nodeId, e);
  }
  // checkpoint ACK 发送失败
  } catch (Exception e) {
      LOG.error("Failed to send checkpoint ACK for checkpoint {}", checkpointId, e);
  }
  ```
- **严重程度**: P2
- **现状**: 关键操作失败时仅记录错误日志，不抛出异常也不通知上层。
- **风险**: checkpoint ACK 丢失会导致 checkpoint 永远无法完成，影响 exactly-once 语义。心跳失败可能导致节点 lease 过期但 TaskManager 继续运行。
- **建议**: ACK 发送失败应设置重试机制；心跳失败可设置连续失败计数器。
- **误报排除**: 分布式系统中关键控制消息丢失仅记日志不处理，直接影响 checkpoint 完成率。
- **复核状态**: 未复核

---

### [维度09-08] CepPatternBuilder 使用 NopException.adapt() 包装未知异常丢失上下文

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:146-148`
- **证据片段**:
  ```java
  } catch (Exception e) {
      throw NopException.adapt(e);
  }
  ```
- **严重程度**: P3
- **现状**: 使用 `NopException.adapt(e)` 包装类加载异常，未附带任何 ErrorCode 或上下文参数。
- **风险**: 缺少结构化错误码，降低了可诊断性。
- **建议**: 为类加载失败定义专门的 ErrorCode。
- **误报排除**: NopCepErrors 已有 3 个 ErrorCode 但没有覆盖类加载失败场景。
- **复核状态**: 未复核
深挖第 2 轮追加完成

---

## 维度复核结论

| 编号 | 复核结论 | 理由 |
|------|---------|------|
| 09-01 | **保留 P2** | 证据完全验证。~30处裸RuntimeException，StreamRuntimeException/StreamException仅被4个文件使用。结构性不一致确认。 |
| 09-02 | **保留 P2** | 同一文件三种异常类型确认。调用方无法统一catch。 |
| 09-03 | **降级至 P3** | 根本问题（RuntimeException vs StreamRuntimeException）完全被09-01覆盖。唯一增量是InterruptedException语义，但对单线程执行引擎影响有限。 |
| 09-04 | **保留 P3** | NopCepErrors 3个ErrorCode全部中文确认。 |
| 09-05 | **保留 P2** | 静默fallback确认。复核发现同类模式实际有4处（不仅是1处），问题比审核报告的更系统。 |
| 09-06 | **保留 P3** | -1哨兵值在max比较时自然排到最后，风险较低但可观测性缺失。 |
| 09-07 | **降级至 P3** | 心跳失败仅记日志是分布式系统标准实践。当前为本地执行引擎，不存在ACK丢失导致永远无法完成的场景。P2过高。 |
| 09-08 | **保留 P3** | NopException.adapt保留异常链，诊断性非完全丧失，仅缺结构化错误码。 |
