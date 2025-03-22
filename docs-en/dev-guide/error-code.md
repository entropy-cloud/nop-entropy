# Uniform Response Format

Nop platform's REST and RPC services uniformly use the `ApiResponse<T>` form to return result data. In synchronous mode, it directly returns `ApiResponse<T>`, while in asynchronous mode, it returns `CompletionStage<ApiResponse<T>>`.

The basic structure of `ApiResponse<T>` is as follows:

```java
class ApiResponse<T> {
    Map<String, Object> headers;
    int status;
    String code;
    String msg;
    T data;
}
```

- **Normal return**: When `status == 0`, the result data is returned through the `data` property.
- **Failure case**: When `status != 0`, the error code is returned through the `code` property, and the detailed error message is provided via the `msg` property.

The `status` variable acts as a coarse-grained indicator for success or failure. It uses an integer type to align with the command-line program's `exitCode`. Nop platform's services are not only used as HTTP services but can also be directly published as command-line services. In this case, the `exitCode` of the command-line program is mapped using the `status` variable.

The `code` property represents an error code, which uses a string format to facilitate extension across various scenarios. Within the Nop platform, error codes can be mapped to meet external requirements via error code mapping.

## Error Code Definitions

1. **Error Codes Defined in Constant Classes**

   For example, in `NopAuthErrors.java`:

   ```java
   ErrorCode ERR_AUTH_UNKNOWN_SITE = define("nop.err.auth.unknown-site", "Unknown site: {siteId}", ARG_SITE_ID);
   ```

   This defines an error code with a key and a default message.

2. **Unified Use of Exception Class**

   Within the system, the `NopException` exception class is used uniformly.

   ```java
   if (siteMap == null) {
       throw new NopException(ERR_AUTH_UNKNOWN_SITE).param(ARG_SITE_ID, siteId);
   ```
   
   The `param` method can be used to add any parameters to the exception object.

3. **Global Exception Handling**

   All exceptions are globally caught and converted into `ApiResponse` objects.

   ```java
   ApiResponse<?> res = ErrorMessageManager.instance().buildErrorResponse(request, error);
   ```

   The structure of `ApiResponse` is similar to `SmartAdmin`'s `ResponseDTO`, consisting of headers, status, code, msg, and data.

4. **Error Code Mapping**

   Within the `ErrorMessageManager`, error codes are mapped from internal codes to external ones. For example, mapping `nop.err.auth.unknown-site` to `10010`.

5. **Parameterized Message Mapping**

   Sometimes, the system may throw a single unified exception code but with different parameters. If you need to return different error messages based on these parameters, you can use parameterized message mapping.

   Example:

   ```java
   "nop.err.my-error?myParam=xx" : "Exception message A"
   ```

6. **Customized Error Messages**

   Error codes' corresponding error messages can be customized via `i18n` files. For example, in `/i18n/zh-CN/error.i18n.yaml`, specific error codes can be mapped to their respective messages. The `I18nMessageManager` automatically reads from the `_vfs/i18n/` directory for all non-prefix files. Nop platform does not require that all error messages must be defined in `error.i18n.yaml`; they can be customized as needed.
