# Nop Platform - API Core Module (nop-api-core)

The nop-api-core module provides the fundamental API definitions, data structures, and interfaces for the Nop Platform. It serves as the foundation for all other modules, defining the common language and contracts used throughout the platform.

## Features

### API Message Structure
- Standardized API request/response models (`ApiRequest<T>`, `ApiResponse<T>`)
- Error handling with structured error beans (`ErrorBean`)
- Message Internationalization support

### Authentication and Authorization
- Security context management (`ISecurityContext`)
- User context interface (`IUserContext`)
- Action-based and data-based authorization checkers
- Role and permission mapping interfaces

### Configuration Management
- Config provider and reference interfaces (`IConfigProvider`, `IConfigReference`)
- Configuration change listeners
- App-level configuration access

### Context Management
- Execution context interface (`IContext`)
- Context propagation utilities
- Tenant-aware proxy context
- Call expire time management

### Data Conversion
- Type converter framework (`ITypeConverter`)
- System converter registry
- Collection and byte array view interfaces

### Exception Handling
- Base exception classes (`NopException`, `IException`)
- Specific exception types (validation, timeout, login, etc.)
- Error code system with message internationalization

### Inversion of Control
- Bean container interfaces (`IBeanContainer`, `IBeanProvider`)
- Bean container start modes

### JSON Processing
- JSON provider interface (`IJsonProvider`)
- JSON schema support
- JSON parse options
- Extensible JSON node model

### Message Queue
- Message sender and receiver interfaces
- Subscription management
- Message consume context
- Acknowledge and retry mechanisms

### Resource Management
- Resource reference interface (`IResourceReference`)

### Time and Calendar
- Clock and system calendar interfaces
- Timeout utilities
- Core metrics integration

### Utility Interfaces
- Cloneable, DeepCloneable interfaces
- Freezable pattern for immutable objects
- Dirty flag support for change tracking
- Variable scope interface
- Ordered interface for sorting

### Validation
- Validation framework interfaces

## Installation

```xml
<dependency>
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-api-core</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Examples

### API Request/Response

```java
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;

// Create a request
ApiRequest<UserCreateBean> request = new ApiRequest<>();
request.setData(new UserCreateBean("username", "password"));

// Process the request
ApiResponse<UserBean> response = userService.createUser(request);

if (response.getStatus() == 0) {
    UserBean user = response.getData();
    // Handle success
} else {
    String errorCode = response.getCode();
    String errorMsg = response.getMsg();
    Map<String, String> errors = response.getErrors();
    // Handle error
}
```

### Security Context

```java
import io.nop.api.core.auth.ISecurityContext;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;

// Access security context
ISecurityContext securityContext = (ISecurityContext) ContextProvider.getContextAttr("securityContext");
IUserContext userContext = securityContext.getUserContext();

// Get user information
String userId = userContext.getUserId();
String username = userContext.getUsername();
```

### Configuration

```java
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.ApiConfigs;

// Get config provider
IConfigProvider configProvider = AppConfig.getConfigProvider();

// Get config value directly
String value = configProvider.getConfigValue("app.name", "default");

// Use predefined config reference (from ApiConfigs)
IConfigReference<String> ref = ApiConfigs.CFG_APPLICATION_NAME;
String currentValue = ref.get();
```

### Exception Handling

```java
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ApiErrors;

// Throw exception
if (user == null) {
    throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
        .param("userId", userId);
}

// Catch and handle exception
try {
    userService.deleteUser(userId);
} catch (NopException e) {
    String errorCode = e.getErrorCode();
    Map<String, Object> params = e.getParams();
    // Handle specific error
}
```

### Filter and Pagination

```java
import io.nop.api.core.beans.FilterBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;

// Create filter
TreeBean filter = FilterBeans.eq("status", "ACTIVE");

// Create pagination
PageBean<UserBean> page = new PageBean<>();
page.setOffset(0);
page.setLimit(10);

// Query with filter and pagination
PageBean<UserBean> result = userService.queryUsers(filter, page);

List<UserBean> users = result.getItems();
long total = result.getTotal();
```

## Directory Structure

```
src/main/java/io/nop/api/core/
├── annotations/       # Various annotations (aop, autotest, biz, cache, etc.)
├── audit/             # Audit logging interfaces
├── auth/              # Authentication and authorization
├── beans/             # API data structures and beans
├── biz/               # Business service interfaces
├── config/            # Configuration management
├── context/           # Execution context
├── convert/           # Data conversion framework
├── exceptions/        # Exception hierarchy
├── graphql/           # GraphQL API annotations
├── ioc/               # Inversion of Control interfaces
├── json/              # JSON processing interfaces
├── message/           # Message queue interfaces
├── resource/          # Resource reference
├── time/              # Time and calendar utilities
├── util/              # Utility interfaces
└── validate/          # Validation framework
```

## Dependencies

- **Jakarta Annotations API**: For standard Java annotations
- **Jakarta RESTful Web Services API**: For REST API definitions
- **Jackson Annotations**: For JSON serialization annotations
- **SLF4J API**: For logging

## Extending the API Core

The nop-api-core module is designed to be extended by other modules. Here's how you can extend it:

1. **Implement Interfaces**: Create implementations of the provided interfaces (e.g., `IConfigProvider`, `ISecurityContext`)
2. **Extend Beans**: Inherit from base beans like `ExtensibleBean` for your data structures
3. **Use Annotations**: Apply the provided annotations to your components
4. **Add Error Codes**: Register custom error codes with the message manager


