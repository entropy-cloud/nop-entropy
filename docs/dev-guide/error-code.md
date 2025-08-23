# 统一响应格式

Nop平台的REST服务和RPC服务，统一使用`ApiResponse<T>`形式返回结果数据，同步模式直接返回`ApiResponse`，异步模式返回`CompletionStage<ApiResponse<T>>`。

ApiResponse的基本结构如下：

```java
class ApiResponse<T>{
    Map<String,Object> headers;
    int status;
    String code;
    String msg;
    T data;
}
```

* 正常返回时`status==0`，通过data属性返回结果数据。
* 失败时status不为0，通过code返回错误码，通过msg返回错误的详细消息。

status相当于是提供一个粗粒度的正确或者失败的信息表示，而且它采用整数类型，便于和命令行程序的exitCode对应。Nop平台的服务实现不仅仅是作为Http服务来使用，
它还可以直接发布为命令行服务，此时命令行程序的exitCode就使用这里的status来返回。

code为错误码，它采用字符串格式，便于扩展到各种使用场景。在Nop平台中可以通过错误码映射将系统内置的错误码映射为满足外部规范要求的错误码。

## 错误码定义

1. 在常量类中定义ErrorCode，例如NopAuthErrors.java中

```
    ErrorCode ERR_AUTH_UNKNOWN_SITE = define("nop.err.auth.unknown-site", "未知的站点：{siteId}", ARG_SITE_ID);
```

它包含一个异常码的key，一个缺省的异常消息。

2. 系统内统一使用NopException这个异常类

```
        if (siteMap == null)
            throw new NopException(ERR_AUTH_UNKNOWN_SITE).param(ARG_SITE_ID, siteId);
```

可以通过param函数为异常对象增加任意参数。
NopException具有errorCode, description, params等属性，并具有可选的status属性。

3. 系统全局捕获所有异常，并将其转换为ApiResponse对象

```java
ApiResponse<?> res = ErrorMessageManager.instance().buildErrorResponse(request, error);
```

ApiResponse的结构类似SmartAdmin中ResponseDTO。response = headers + status + msg + data

在转换为ApiResponse的过程中会执行i18n消息映射，根据异常码的key映射得到国际化消息。

4. 异常码映射
   ErrorMessageManager构造ApiResponse的过程中，会将内部异常码映射为外部异常码。比如客户对返回的异常码可能有统一规范要求。例如将 nop.err.auth.unknown-site 映射为 10010等。

5. 参数化消息映射
   有的时候系统底层可能会抛出一个统一的异常码，但是它的参数不同。如果我们希望根据不同的参数向客户返回不同的错误消息，则可以配置参数化消息映射。例如

```
"nop.err.my-error?myParam=xx" : "异常消息A"
```

6. 异常消息定制
异常码对应的异常消息可以通过i18n文件进行定制。例如在 `/i18n/zh-CN/error.i18n.yaml`文件中为每个错误码指定对应的错误消息，它会替代错误码定义时所使用的缺省消息。
I18nMessageManager会自动读取`_vfs/i18n/`目录下所有的不以下划线为前缀的`i18n.yaml`文件。Nop平台并没有约定一定要在`error.i18n.yaml`中定制错误消息，这里的名称是自定义的。

## 错误码映射

### 1. 概述

NopPlatform允许通过错误码映射机制，将系统内部的错误码（例如 `nop.err.auth.login-check-fail`）转换为对外API的错误响应格式。这使得您可以统一API的错误码规范、自定义HTTP状态码和返回消息，而无需修改底层业务代码。

### 2. 配置文件

错误码映射规则在 `app.errors.yaml` 文件中进行配置。

*   **模块配置**: 每个模块可以有自己的配置文件，路径为 `/{moduleId}/conf/app.errors.yaml`。
*   **全局配置**: 主应用可以提供一个全局配置文件，路径为 `/main/conf/app.errors.yaml`。
*   **加载顺序与优先级**: 系统会先加载所有模块的配置，然后加载全局配置。如果存在相同的错误码映射，**全局配置会覆盖模块配置**。

### 3. 配置项说明

YAML文件的**键**是内部错误的**字符串ID** (例如 `nop.err.auth.invalid-login-request`)。**值**是一个包含映射规则的对象，常用属性如下：

*   `mapToCode`: **(核心)** 将内部错误码映射为新的、对外的错误码字符串。
*   `httpStatus`: **(常用)** 为此错误指定返回的HTTP状态码，例如 `401` (未授权)、`404` (未找到)。
*   `messageKey`: 覆盖默认的国际化(i18n)消息键，以返回不同的错误描述。
*   `includeCause`: 是否在错误描述中包含根本原因(cause)的异常信息。**生产环境建议关闭 (`false`)**，以防泄露内部实现细节。
*  `status`: 业务状态码，缺省为-1
*   其他属性如 `returnParams`, `bizFatal`, `mapToParams` 等可用于更精细的控制。

### 4. 配置示例

根据 `NopAuthErrors` 接口中的错误码定义，以下是一个 `app.errors.yaml` 的配置示例。

**文件路径**: `/main/conf/app.errors.yaml`

```yaml
# 键(key)是错误码的字符串ID，而不是Java常量名

# 登录失败：用户名或密码不匹配
nop.err.auth.login-check-fail:
  # 映射为对外的错误码 AUTH_FAILURE
  mapToCode: "AUTH_FAILURE"
  # 返回 401 Unauthorized HTTP状态码
  httpStatus: 401
  # 使用自定义的i18n消息
  messageKey: "err.api.login-failed"

# 登录用户不存在
nop.err.auth.login-with-unknown-user:
  # 映射为对外的错误码 USER_NOT_FOUND
  mapToCode: "USER_NOT_FOUND"
  # 返回 400 Bad Request，因为这是一个客户端输入错误
  httpStatus: 400
  status: -401
```

**效果**:
当系统内部抛出 `NopAuthErrors.ERR_AUTH_LOGIN_CHECK_FAIL` 异常时，通过此映射，最终对外的API响应：
*   HTTP状态码将是 `401`。
*   响应体中的 `code` 字段值为 `AUTH_FAILURE`。
