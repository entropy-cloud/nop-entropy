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
