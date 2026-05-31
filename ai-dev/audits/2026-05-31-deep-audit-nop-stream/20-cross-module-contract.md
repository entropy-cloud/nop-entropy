# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] CheckpointStorage 实现向调用方泄漏未包装的 NopException

- **文件**: `nop-stream-runtime/.../storage/JdbcCheckpointStorage.java` (13处) + `LocalFileCheckpointStorage.java` (12处)
- **证据片段**:
  ```java
  } catch (NopException e) {
      throw e;  // 泄漏未包装的 NopException
  } catch (Exception e) {
      throw new CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e)
              .param(ARG_DETAIL, "storeCheckPoint failed");
  }
  ```
- **严重程度**: P1
- **现状**: ICheckpointStorage 接口签名仅声明 throws CheckpointStorageException，但实现通过 catch(NopException e){throw e;} 透传框架异常。
- **风险**: 调用方仅 catch CheckpointStorageException 时，NopException 被意外传播。
- **建议**: 将 NopException 包装进 CheckpointStorageException。
- **信心水平**: 确定
- **误报排除**: 已确认接口签名不含 NopException。
- **复核状态**: 未复核

### [维度20-02] optional 依赖 nop-batch-core 实现类被直接实例化

- **文件**: `nop-stream-connector/.../BatchConsumerSinkFunction.java:53-54` + `BatchLoaderSourceFunction.java:56-57`
- **严重程度**: P1
- **现状**: nop-batch-core 声明为 optional，但代码直接实例化 BatchTaskContextImpl（实现类而非接口）。classpath 缺失时抛 NoClassDefFoundError 而非友好提示。
- **建议**: 在 StreamConnectors 工厂方法中增加 classpath 检查。
- **信心水平**: 确定
- **误报排除**: NoClassDefFoundError 不友好。
- **复核状态**: 未复核

### [维度20-03] optional 依赖 nop-message-debezium 实现类被直接引用

- **文件**: `nop-stream-connector/.../DebeziumCdcSourceFunction.java:14-16`
- **严重程度**: P2
- **现状**: 同 20-02 模式，但 CDC 连接器是明确可选功能，风险较低。
- **建议**: 在文档中标注依赖要求。
- **信心水平**: 确定
- **误报排除**: CDC 连接器使用者通常知道自己需要 Debezium。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 20-01 | P1 | JdbcCheckpointStorage.java | 泄漏未包装 NopException |
| 20-02 | P1 | BatchConsumerSinkFunction.java | optional 依赖直接实例化 |
| 20-03 | P2 | DebeziumCdcSourceFunction.java | optional 依赖直接引用 |
