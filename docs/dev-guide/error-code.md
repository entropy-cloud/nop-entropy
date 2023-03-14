
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
````
"nop.err.my-error?myParam=xx" : "异常消息A"
````