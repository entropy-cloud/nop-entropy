# AutoTest 自动化测试框架使用指南

## 1. 选择基类

### 1.1 需要快照录制支持

从 `JunitAutoTestCase` 继承：

```java
@NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestLoginApi extends JunitAutoTestCase {
    // 支持快照录制和验证
}
```

### 1.2 不需要快照录制支持

从 `JunitBaseTestCase` 继承（相当于 `snapshotTest=NOT_USE`）：

```java
public class TestLoginApi extends JunitBaseTestCase {
    // 不支持快照机制，适合简单测试
}
```

### 1.3 Maven 依赖

AutoTest 框架基于 **JUnit 5**，需要添加以下 Maven 依赖：

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-autotest-junit</artifactId>
    <version>${nop-entropy.version}</version>
    <scope>test</scope>
</dependency>
```

## 2. 快速开始

### 2.1 使用 @Inject 注入 Bean

从 `JunitBaseTestCase` 或 `JunitAutoTestCase` 继承后，可以使用 `@Inject` 注入 bean：

```java
import jakarta.inject.Inject;

public class TestLoginApi extends JunitBaseTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;  // 不能是 private

    @Inject
    protected IAuthService authService;  // protected 或 package-private 都可以
}
```

**重要**：注入的字段不能是 `private`，使用 `protected` 或 package-private。

### 2.2 使用 Attachment 读取测试数据

`JunitBaseTestCase` 和 `JunitAutoTestCase` 从 `BaseTestCase` 继承，提供了 `attachmentXXX` 方法，可以读取与测试类在同一包路径下的文件（在 classpath 中）：

```java
public class TestLoginApi extends JunitBaseTestCase {
    @Test
    public void testLogin() {
        // 读取测试类所在目录下的文本文件
        String text = attachmentText("request.txt");

        // 读取 JSON 文件并反序列化为对象
        ApiRequest<LoginRequest> request = attachmentBean("request.json5",
            new TypeReference<ApiRequest<LoginRequest>>() {}.getType());

        // 读取 XML 文件
        XNode xmlNode = attachmentXml("config.xml");
    }
}
```

**目录结构**：
```
src/test/java/io/nop/auth/service/
├── TestLoginApi.java
├── request.json5
└── config.xml
```

### 2.3 使用 Target 目录

```java
public class TestLoginApi extends JunitBaseTestCase {
    @Test
    public void testGenerateOutput() {
        // 获取 target 目录下的文件
        File outputFile = getTargetFile("generated/output.json");

        // 写入文件
        FileHelper.writeFile(outputFile, "content");
    }
}
```

### 2.4 创建使用快照的测试类

```java
@NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE
)
public class TestLoginApi extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;  // 不能是 private

    @Test
    public void testLogin() {
        // 1. 读取输入数据
        ApiRequest<LoginRequest> request = input("request.json5",
            new TypeReference<ApiRequest<LoginRequest>>() {}.getType());

        // 2. 执行测试
        ApiResponse<LoginResult> result = loginApi.login(request);

        // 3. 输出结果（根据模式自动录制或验证）
        output("response.json5", result);
    }
}
```

**核心要点**：
- `input(fileName)` 从 `_cases/{testCaseClass}/{testMethod}/input/` 目录读取文件
- `output(fileName)` 根据模式自动录制或验证结果

### 2.5 录制测试数据

```java
@NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE,
    snapshotTest = SnapshotTest.RECORDING  // 录制模式
)
public class TestLoginApi extends JunitAutoTestCase {
    // 执行测试后自动生成 _cases/ 目录下的数据文件
}
```

**重要**：录制模式运行完成后，会抛出异常码 `nop.err.autotest.snapshot-finished`，提示 "录制快照过程正常结束. 现在可以通过@NopTestConfig的snapshotTest属性来控制录制/校验快照数据"。这是正常流程，表示录制完成，并非错误。

### 2.6 验证测试结果

```java
@NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE,
    snapshotTest = SnapshotTest.CHECKING  // 验证模式，也可不设置（默认值）
)
public class TestLoginApi extends JunitAutoTestCase {
    // 验证实际结果与录制数据是否匹配
}
```

## 3. 测试用例目录结构

```
_cases/
└── io/nop/auth/service/TestLoginApi/
    └── testLogin/          # 测试方法名作为子目录
        ├── input/              # 测试输入数据
        │   ├── tables/         # 数据库初始化数据（手工编写）
        │   └── request.json5   # API请求数据（手工编写）
        └── output/             # 预期输出数据（自动录制）
            ├── tables/         # 数据库预期变更（自动录制）
            └── response.json5  # API响应预期（自动录制）
```

## 4. 常用功能

### 4.1 多步骤测试

```java
@Test
public void testLoginLogout() {
    // 1. 登录获取token
    ApiResponse<LoginResult> loginResult = loginApi.login(loginRequest);
    output("1_login_response.json5", loginResult);

    // 2. 使用token获取用户信息
    ApiRequest<AccessTokenRequest> userRequest = input("2_user_request.json5", AccessTokenRequest.class);
    ApiResponse<LoginUserInfo> userResponse = loginApi.getLoginUserInfo(userRequest);
    output("2_user_response.json5", userResponse);

    // 3. 使用token登出
    ApiRequest<LogoutRequest> logoutRequest = input("3_logout_request.json5", LogoutRequest.class);
    ApiResponse<Void> logoutResponse = loginApi.logout(logoutRequest);
    output("3_logout_response.json5", logoutResponse);
}
```

### 4.2 数据变体测试

```java
@ParameterizedTest
@EnableVariants
public void testVariants(String variant) {
    // 自动加载 {caseDir}/input/variants/{variant}/ 下的数据
    ApiRequest<LoginRequest> request = input("request.json5",
        new TypeReference<ApiRequest<LoginRequest>>() {}.getType());

    ApiResponse<LoginResult> result = loginApi.login(request);
    output("response.json5", result);
}
```

### 4.3 异常测试

```java
@Test
public void testLoginError() {
    // 验证预期异常
    error("response-error.json5", () -> {
        LoginApi loginApi = buildLoginApi();
        ApiRequest<LoginRequest> request = input("invalid-request.json5",
            new TypeReference<ApiRequest<LoginRequest>>() {}.getType());
        return loginApi.login(request);
    });
}
```

## 5. @NopTestConfig 常用配置

```java
@NopTestConfig(
    localDb = true,                      // 使用本地 H2 数据库
    initDatabaseSchema = OptionalBoolean.TRUE,  // 自动初始化数据库表结构
    snapshotTest = SnapshotTest.RECORDING,     // RECORDING(录制) 或 CHECKING(验证)
    forceSaveOutput = true,              // 使用录制的输入数据，只更新输出数据
    enableIoc = OptionalBoolean.TRUE,   // 启用 NopIoC 容器，支持 @Inject 注入
    enableConfig = OptionalBoolean.TRUE,  // 启用 nop-config 模块的 Config 管理
    testBeansFile = "",                  // 测试专用的 beans 配置文件
    testConfigFile = ""                  // 测试专用的 config 配置文件
)
```

**两种更新快照方式的区别**：

1. **`forceSaveOutput = true`**
   - 使用录制的 input 数据（包括 table 数据）
   - 只更新 output 数据
   - 适用于：业务逻辑变化，但测试输入场景不变的情况

2. **`snapshotTest = SnapshotTest.RECORDING`**
   - 重新录制读取的 table 数据以及所有输出数据
   - 适用于：输入数据也需要更新的情况

### @NopTestProperty
直接设置测试专用属性：
```java
@NopTestProperty(name = "my.property", value = "test-value")
```


## 6. 前缀引导语法

录制的 JSON 和 csv 文件中使用前缀引导语法来定义字段的匹配规则：

- `@var:xxx`：变量引用
- `@ge:3`：大于等于3
- `@between:1,5`：在1-5之间
- `@startsWith:a`：以a开头

## 7. 典型工作流程

1. **首次开发**：
    ```java
    @NopTestConfig(snapshotTest = SnapshotTest.RECORDING)
    ```
    - 编写测试代码和 input 数据
    - 执行测试自动录制 output 数据（包括 table 数据）

2. **日常开发**：
    ```java
    @NopTestConfig(snapshotTest = SnapshotTest.CHECKING)  // 或不设置
    ```
    - 修改业务逻辑
    - 执行测试验证结果

3. **更新快照 - 方式一：只更新输出数据**
    ```java
    @NopTestConfig(forceSaveOutput = true)
    ```
    - **使用录制的 input 数据，只更新 output 数据**
    - 适用于：业务逻辑变化，但测试输入场景不变的情况

4. **更新快照 - 方式二：重新录制所有数据**
    ```java
    @NopTestConfig(snapshotTest = SnapshotTest.RECORDING)
    ```
    - **重新录制读取的 table 数据以及所有输出数据**
    - 适用于：输入数据也需要更新的情况
    - 改完后切回 CHECKING
