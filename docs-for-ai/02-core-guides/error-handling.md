# 错误处理与错误码

## 两档策略

根据代码的**消费方**选择错误处理方式：

| 层次 | 适用场景 | 规范 |
|------|---------|------|
| **ErrorCode 模式** | 框架核心、跨模块公共 API、GraphQL 接口 | `NopException + ErrorCode + .param(...)` |
| **模块异常类模式** | 模块内部实现、AI 驱动开发的增量代码 | 模块级异常类 + 英文字符串消息 |

**判断依据**：该错误是否会被其他模块程序化消费（如按错误码匹配、国际化、前端展示）？如果是，用 ErrorCode；否则用模块异常类即可。

---

## 模式一：ErrorCode（框架/公共 API）

### 默认规则

1. 统一定义 `ErrorCode`，不要随手抛 `RuntimeException`。
2. 用 `.param(...)` 附带上下文参数。
3. 保留原始异常链。

### 定义错误码

```java
public interface OrderErrors {
    String ARG_ORDER_ID = "orderId";

    ErrorCode ERR_ORDER_NOT_FOUND = ErrorCode.define(
        "nop.err.order.not-found",
        "Order not found: {orderId}",
        ARG_ORDER_ID
    );
}
```

### 抛出业务异常

```java
throw new NopException(OrderErrors.ERR_ORDER_NOT_FOUND)
    .param(OrderErrors.ARG_ORDER_ID, orderId);
```

### 包装底层异常

```java
catch (IOException e) {
    throw new NopException(OrderErrors.ERR_ORDER_SYNC_FAILED, e)
        .param(OrderErrors.ARG_ORDER_ID, orderId);
}
```

### 错误码命名

```text
nop.err.[模块].[子域].[错误]
```

### 参数名

为动态参数定义统一常量，例如：

```java
String ARG_ORDER_ID = "orderId";
```

---

## 模式二：模块异常类（内部实现 / AI 驱动开发）

每个模块定义一个异常类，继承 `NopException`，接受英文字符串消息。错误消息统一使用**英文**。

**消息语言规则**：
- `ErrorCode.define(...)` 中的描述消息使用**中文**，框架会通过 i18n 机制翻译。
- 不走 `ErrorCode` 的异常（如直接 `new NopException("...")` 或模块异常类的字符串构造器）必须使用**英文**，因为这类消息可能被 AI 直接阅读分析。

### 定义模块异常类

```java
// nop-ai-api 模块
package io.nop.ai.api.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

public class NopAiException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopAiException(String message) {
        super(message, null, true, true);
    }

    public NopAiException(String message, Throwable cause) {
        super(message, cause, true, true);
    }

    public NopAiException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopAiException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
```

**要点**：
- 同时提供 `(String)` 和 `(ErrorCode)` 构造器——内部实现用字符串，公共 API 仍可用 ErrorCode
- 调用 `super(message, null, true, true)` 匹配 `NopException` 的字符串构造器签名

### 使用方式

```java
// 内部实现：直接使用英文字符串
throw new NopAiException("LLM response is empty for prompt: " + promptName);

// 包装底层异常
throw new NopAiException("Failed to parse tool call response", e);

// 仍然支持 ErrorCode（当需要稳定错误码时）
throw new NopAiException(AiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR)
    .param(AiCoreErrors.ARG_LLM_NAME, llmName);
```

### 其他模块的参考实现

`StreamException` / `StreamRuntimeException`（nop-stream 模块）采用相同模式：
- `StreamRuntimeException extends NopException`
- `StreamException extends StreamRuntimeException`
- 均提供 `(String)` 和 `(ErrorCode)` 双构造器

---

## 普通 BizModel 中的推荐姿势

1. 先用 `requireEntity()` 获取目标实体。
2. 再做业务规则判断。
3. 规则不满足时根据层次选择异常方式。

## 资源关闭：使用 IoHelper

关闭 `Closeable` / `AutoCloseable` 资源时使用 `io.nop.commons.util.IoHelper`，不要手写 `try { x.close() } catch`。

| 方法 | 用途 |
|------|------|
| `IoHelper.safeClose(obj)` | 安全关闭单个对象 |
| `IoHelper.safeCloseObject(autoCloseable)` | 安全关闭 AutoCloseable |
| `IoHelper.safeCloseAll(collection)` | 批量关闭集合中所有对象 |

---

## 不要这样写

| 反模式 | 问题 |
|--------|------|
| `throw new RuntimeException("some message")` | 绕过框架异常体系，上层无法统一处理 |
| 手写 `try { x.close() } catch` 关闭资源 | 应使用 `IoHelper.safeClose`，见上方说明 |
| 自定义异常类不继承 `NopException`（如 `extends RuntimeException`） | 绕过框架异常体系，丢失 ErrorCode、i18n、结构化错误响应等能力。所有业务异常必须直接或间接继承 `NopException` |
| 错误消息使用中文或非英文 | AI 读取英文消息更准确，避免编码问题；非 ErrorCode 路径的消息可能被 AI 直接阅读，必须使用英文 |
| 丢失原始异常链 | 排查困难 |
| 在普通 BizModel 示例中同时展示底层 DAO + 显式事务 | 容易把边界模式误当默认模板 |

## API 层的表现

BizModel 抛出的 `NopException`（包括子类）会被框架转换为结构化错误响应。对业务代码来说，最重要的是：

1. 错误码/消息清晰。
2. 参数完整。
3. 异常链保留。

## 相关文档

- `./service-layer.md`
- `../03-runbooks/error-codes-and-nop-exception.md`
- `../03-runbooks/transaction-boundaries.md`
