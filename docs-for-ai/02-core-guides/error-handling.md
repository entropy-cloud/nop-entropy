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
2. 用 `.param(...)` 附带上下文参数。**判断原则：只看消息文字能否定位问题？** 能则不需要参数（如"购物车为空"）；不能则需要参数（如"订单无法取消"——哪个订单？什么状态？）。
3. 保留原始异常链。

需要参数的场景：实体不存在（传 ID）、状态不匹配（传业务编号 + 当前状态）、库存不足（传 SKU + 请求数量）、唯一性冲突（传字段名 + 冲突值）。不需要参数的场景：语义已自足的错误（购物车为空、商品已下架）。

敏感信息（手机号、身份证等）不要直接传入参数；如果业务必须包含，先掩码：`StringHelper.maskMiddle(mobile, 3, 4)`。

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
| 在 `@BizQuery`/`@BizMutation` 返回 bean 中内嵌 `success`/`errorCode`/`errorMsg` 等字段来表达失败 | 绕过框架统一的 ApiResponse 错误机制。BizModel 方法的返回值只承载成功场景的数据（ApiResponse.data），失败应直接抛 `NopException`，由框架自动转为 `{status, code, msg}` 标准错误响应。调用方不需要检查返回 bean 中的成功标志 |

## API 层的表现

BizModel 抛出的 `NopException`（包括子类）会被框架转换为结构化错误响应。对业务代码来说，最重要的是：

1. 错误码/消息清晰。
2. 参数完整。
3. 异常链保留。

## 从 ErrorCode 获取错误消息

### ErrorCode 本身（无 i18n，无参数解析）

```java
ErrorCode ec = OrderErrors.ERR_ORDER_NOT_FOUND;
String raw = ec.getDescription(); // "订单不存在:{orderId}"，不做任何翻译或替换
```

### 查 i18n 翻译

i18n 消息文件在 `_vfs/nop/locale/{locale}.i18n.yaml`，key 为 errorCode 字符串：

```yaml
# zh-CN.i18n.yaml
nop.err.order.not-found: "订单未找到:{orderId}"
```

通过 `ErrorMessageManager` 查找：

```java
// 仅查 i18n 翻译，不解析 {param}
String localized = ErrorMessageManager.instance()
    .getLocalizedDescription("zh-CN", "nop.err.order.not-found");

// 查 i18n + 解析 {param} 占位符
Map<String,Object> params = Map.of("orderId", "ORD-001");
String msg = ErrorMessageManager.instance()
    .getErrorDescription("zh-CN", "nop.err.order.not-found", params);
// → "订单未找到:ORD-001"
```

### 从 NopException 获取

| 方法 | 返回 | 是否 i18n | 是否解析 param |
|------|------|-----------|---------------|
| `e.getDescription()` | ErrorCode 的描述（含 `{param}` 占位符） | 是（构造时已查 i18n） | 否 |
| `e.getMessage()` | 完整日志文本（含 errorCode、params、resolved desc、stack） | 是 | 是 |
| `e.getParams()` | `.param(...)` 传入的参数 Map | — | — |

```java
NopException e = new NopException(OrderErrors.ERR_ORDER_NOT_FOUND)
    .param(OrderErrors.ARG_ORDER_ID, "ORD-001");

e.getDescription();   // "订单未找到:{orderId}"（未解析 param）
e.getMessage();       // "NopException[seq=1,errorCode=nop.err.order.not-found,params={orderId:ORD-001},desc=订单未找到:ORD-001]"
```

### 从 Throwable / ErrorBean 统一构建

```java
// 构建 ErrorBean（含完整的 i18n + mapping + 参数解析）
ErrorBean bean = ErrorMessageManager.instance()
    .buildErrorMessage(locale, exception, false, true, true);
bean.getDescription(); // "订单未找到:ORD-001"

// 构建 ApiResponse（GraphQL/HTTP 响应）
ApiResponse<?> resp = ErrorMessageManager.instance()
    .buildResponseForException(locale, exception);
```

## 国际化(i18n)消息机制

### i18n 文件结构与加载

i18n 消息文件放在 VFS 的 `/i18n/{locale}/` 下：

```
_i18n/                                # VFS 根
  zh-CN/                             # 语言目录
    nop-auth.i18n.yaml               # 主文件（含 Delta 继承）
    _nop-auth.i18n.yaml              # Delta 基文件（codegen 产出，不要手改）
  en/
    nop-core.i18n.yaml
```

装载顺序（`I18nMessagesLoader`）：

1. 扫描 VFS 下的 `/i18n/{locale}/` 目录，加载所有 `*.i18n.yaml` 文件（跳过 `_` 前缀的 Delta 基文件）
2. 加载 `/main/i18n/{locale}.i18n.yaml`（如果有），允许覆盖模块级消息
3. Delta 基文件（`_nop-auth.i18n.yaml`）由 `*-meta/postcompile/gen-i18n.xgen` 从 ORM/meta 自动生成，主文件通过 `x:extends` 继承基文件然后增量覆盖

**格式**（YAML，继承 Delta JSON 机制）：

```yaml
# _nop-auth.i18n.yaml（codegen 自动生成，不要手改）
dict.label:
  auth/user-status: 用户状态

# nop-auth.i18n.yaml（手工维护，覆盖基文件）
"x:extends": _nop-auth.i18n.yaml
dict.label:
  auth/user-status: 用户状态（定制版）
```

### 配置与启用

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.application.locale` | `zh-CN` | 应用程序默认语言 |
| `nop.default-locale` | `zh-CN` | 配置文件的默认语言 |
| `nop.core.i18n.enabled-locales` | `zh-CN,en` | 启用的语言列表，会触发对应 `/i18n/{locale}` 目录的扫描加载 |

启用额外语言只需在 `application.yaml` 中添加：

```yaml
nop:
  core:
    i18n:
      enabled-locales: zh-CN,en,ja,ko
```

### 前端指定 locale 的链路

```
HTTP Header: nop-locale: zh-CN
    ↓
AuthHttpServerFilter.initUserContext()    # AuthHttpServerFilter.java:491-505
    ↓
IContext.setLocale(locale)                # 线程级上下文
    ↓
ContextProvider.currentLocale()           # 后端代码读取当前 locale
    ↓
I18nMessageManager.getMessage(locale, key, default)
```

具体：

- 前端在 HTTP 请求头中传 `nop-locale`（`ApiConstants.HEADER_LOCALE = "nop-locale"`）
- 登录时也可在 `LoginRequest.locale` 中传递，存入 `UserContext`，之后每次请求自动使用
- `AuthHttpServerFilter.initUserContext()` 优先读请求头，其次读 `userContext.getLocale()`
- 后端代码通过 `ContextProvider.currentLocale()` 获取当前 locale，无需手动传递

### `@i18n:` 前缀语法（XMeta / XView / XJson）

在 XMeta、XView、页面 JSON 等文件中使用 `@i18n:` 前缀引用 i18n key：

```yaml
# XMeta 中
label: "@i18n:auth.login.label|登录"

# 或使用多个 key 自动回退
label: "@i18n:auth.login.label,common.login|登录"

# 嵌入在文本中
title: "前缀 {@i18n:auth.field.name} 后缀"
```

| 语法 | 含义 |
|------|------|
| `@i18n:key\|默认值` | 查 i18n key，找不到用默认值 |
| `@i18n:key1,key2\|默认值` | 依次尝试 key1、key2，都找不到用默认值 |
| `@i18n:key` | 查到返回，查不到返回 key 本身 |
| `prefix {@i18n:key} suffix` | 内嵌在文本中，只替换 `{}` 部分 |

解析引擎：

- `I18nTextResolver` — 编译时注册的 `@i18n:` 值解析器，运行时按 `ContextProvider.currentLocale()` 查询
- `I18nMessageManager.resolveI18nVar(locale, text)` — 对包含 `@i18n:` 的文本做整体替换

### 消息查询层次（`I18nMessageManager.getMessage`）

1. `registeredMessages.get(locale)` — 通过 `registerMessages()` 编程注册的消息
2. `getLocaleMessages(locale)` — VFS 中 `*.i18n.yaml` 文件加载的消息
3. 未找到时尝试 parent locale（如 `zh-CN` → `zh` → default locale）
4. 仍未找到返回 `defaultValue`

## 相关文档

- `./service-layer.md`
- `../03-runbooks/error-codes-and-nop-exception.md`
- `../03-runbooks/transaction-boundaries.md`
