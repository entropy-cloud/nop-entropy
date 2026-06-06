# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] NopCodeException 已定义但零使用，缺少 String 构造器

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeException.java`
- **行号**: 1-14（全文）
- **证据片段**:
  ```java
  public class NopCodeException extends NopException {
      public NopCodeException(ErrorCode errorCode) { super(errorCode); }
      public NopCodeException(ErrorCode errorCode, Throwable cause) { super(errorCode, cause); }
      // 缺少 (String) 和 (String, Throwable) 构造器
  }
  ```
- **严重程度**: P3
- **现状**: 全仓库零 import/使用。缺少 String 构造器不符合两档策略。
- **建议**: 删除或补充构造器并统一使用。
- **复核状态**: 未复核

### [维度09-02] JavaFileAnalyzer 静默吞掉异常

- **文件**: `nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java`
- **行号**: 212-219
- **证据片段**:
  ```java
  } catch (Exception e) {
      // ignore parse failure — 无 LOG.debug
  }
  ```
- **严重程度**: P3
- **现状**: 同文件其他 catch 块都记录 DEBUG 日志，此处遗漏。
- **建议**: 添加 LOG.debug 记录。
- **复核状态**: 未复核

## 正面确认

- NopCodeErrors (7) + NopCodeCoreErrors (3) 共10个 ErrorCode 均通过 ErrorCode.define() 定义
- 公共 API throw 均使用 NopException + ErrorCode + .param()
- 所有错误消息为英文，无中文
- 日志全部使用 SLF4J
- InterruptedException 正确恢复中断状态
