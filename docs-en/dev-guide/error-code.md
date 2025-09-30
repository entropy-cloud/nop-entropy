# Unified Response Format

The Nop Platform’s REST and RPC services uniformly return result data using the ApiResponse<T> form. In synchronous mode, ApiResponse is returned directly; in asynchronous mode, CompletionStage<ApiResponse<T>> is returned.

The basic structure of ApiResponse is as follows:

```java
class ApiResponse<T>{
    Map<String,Object> headers;
    int status;
    String code;
    String msg;
    T data;
}
```

* On success, status==0, and the result data is returned via the data property.
* On failure, status is not 0; the error code is returned via code, and the detailed error message is returned via msg.

status serves as a coarse-grained indicator of success or failure, and it uses an integer type to align with command-line programs’ exitCode. Service implementations on the Nop Platform are not only used as HTTP services; they can also be directly exposed as command-line services. In that case, the command-line program’s exitCode uses the status here.

code is the error code, and it uses a string format to facilitate extension to various usage scenarios. On the Nop Platform, built-in system error codes can be mapped via error-code mapping to error codes that meet external specification requirements.

## Error Code Definition

1. Define ErrorCode in a constants class, for example in NopAuthErrors.java

```
    ErrorCode ERR_AUTH_UNKNOWN_SITE = define("nop.err.auth.unknown-site", "未知的站点：{siteId}", ARG_SITE_ID);
```

It contains the key of the error code and a default error message.

2. System-wide, use the NopException exception class

```
        if (siteMap == null)
            throw new NopException(ERR_AUTH_UNKNOWN_SITE).param(ARG_SITE_ID, siteId);
```

You can add arbitrary parameters to the exception object via the param function.
NopException has properties such as errorCode, description, params, and an optional status property.

3. The system globally catches all exceptions and converts them into an ApiResponse object

```java
ApiResponse<?> res = ErrorMessageManager.instance().buildErrorResponse(request, error);
```

The structure of ApiResponse is similar to ResponseDTO in SmartAdmin. response = headers + status + msg + data

During the conversion to ApiResponse, i18n message mapping is performed to map the error code’s key to a localized message.

4. Error code mapping
   During ErrorMessageManager’s construction of ApiResponse, internal error codes are mapped to external error codes. For example, a customer might have uniform specification requirements for returned error codes. For instance, map nop.err.auth.unknown-site to 10010, etc.

5. Parameterized message mapping
   Sometimes the underlying system might throw a unified error code whose parameters differ. If we want to return different error messages to the client based on different parameters, we can configure parameterized message mapping. For example

```
"nop.err.my-error?myParam=xx" : "Error message A"
```

6. Error message customization
The error message corresponding to an error code can be customized via i18n files. For example, specify the corresponding error message for each error code in the /i18n/zh-CN/error.i18n.yaml file, which will replace the default message used when defining the error code.
I18nMessageManager automatically reads all i18n.yaml files under the _vfs/i18n/ directory whose names do not start with an underscore. The Nop Platform does not mandate customizing error messages in error.i18n.yaml; the name is up to you.

## Error Code Mapping

### 1. Overview

The Nop Platform allows you to map internal error codes (e.g., nop.err.auth.login-check-fail) to the error response format of external APIs via an error code mapping mechanism. This enables you to unify API error code conventions, customize HTTP status codes and return messages, without changing the underlying business code.

### 2. Configuration Files

Error code mapping rules are configured in the app.errors.yaml file.

* Module configuration: Each module can have its own configuration file at /{moduleId}/conf/app.errors.yaml.
* Global configuration: The main application can provide a global configuration file at /main/conf/app.errors.yaml.
* Load order and precedence: The system loads all module configurations first, then the global configuration. If there are mappings for the same error code, the global configuration overrides the module configuration.

### 3. Configuration Item Description

The keys in the YAML file are the string IDs of internal errors (e.g., nop.err.auth.invalid-login-request). The value is an object containing the mapping rules, with commonly used properties as follows:

* mapToCode: (Core) Map the internal error code to a new, external-facing error code string.
* httpStatus: (Common) Specify the HTTP status code to return for this error, e.g., 401 (Unauthorized), 404 (Not Found).
* messageKey: Override the default i18n message key to return a different error description.
* includeCause: Whether to include the root cause (cause) exception information in the error description. It is recommended to disable (false) in production to avoid leaking internal implementation details.
* status: Business status code, defaults to -1
* Other properties such as returnParams, bizFatal, mapToParams, etc., can be used for finer-grained control.

### 4. Configuration Example

Based on the error code definitions in the NopAuthErrors interface, the following is a configuration example of app.errors.yaml.

File path: /main/conf/app.errors.yaml

```yaml
# The key is the string ID of the error code, not the Java constant name

# Login failed: username or password does not match
nop.err.auth.login-check-fail:
  # Map to the external error code AUTH_FAILURE
  mapToCode: "AUTH_FAILURE"
  # Return 401 Unauthorized HTTP status
  httpStatus: 401
  # Use a custom i18n message
  messageKey: "err.api.login-failed"

# Logged-in user does not exist
nop.err.auth.login-with-unknown-user:
  # Map to the external error code USER_NOT_FOUND
  mapToCode: "USER_NOT_FOUND"
  # Return 400 Bad Request, as this is a client input error
  httpStatus: 400
  status: -401
```

Effect:
When the system throws the NopAuthErrors.ERR_AUTH_LOGIN_CHECK_FAIL exception internally, with this mapping, the final external API response:
* The HTTP status code will be 401.
* The value of the code field in the response body will be AUTH_FAILURE.
<!-- SOURCE_MD5:6002ec5ca7e294adfe39fe180c609b96-->
