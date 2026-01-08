# AutoTest自动化测试框架使用指南

## 1. 核心概念

### 1.1 数据驱动测试
AutoTest是一个数据驱动的测试框架，通过数据文件而非代码来管理测试输入和预期输出。开发者只需编写测试骨架，提供测试数据文件即可。

### 1.2 录制与验证模式
- **录制模式**：执行测试并生成测试数据文件
- **验证模式**：使用已录制的数据验证测试结果

### 1.3 前缀引导语法
录制的JSON和csv文件中使用前缀引导语法来定义某个字段的匹配规则。

一种灵活的字段值模式匹配语法，支持多种匹配条件：
- `@var:xxx`：变量引用
- `@ge:3`：大于等于3
- `@between:1,5`：在1-5之间
- `@startsWith:a`：以a开头

## 2. 项目结构

### 2.1 nop-autotest模块
```
nop-autotest/
├── nop-autotest-core/      # 核心功能实现
└── nop-autotest-junit/     # JUnit5集成
```

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-autotest-junit</artifactId>
    <version>${nop-entropy.version}</version>
    <scope>test</scope>
</dependency>
```

### 2.2 测试用例目录结构
```
// 与src目录平级会自动生成测试用例目录
_cases/
└── io/nop/auth/service/TestLoginApi/
    └── testLogin/          # 测试方法名作为子目录
        ├── input/              # 测试输入数据
        │   ├── tables/         # 数据库初始化数据，自动录制
        │   │   ├── nop_auth_user.csv
        │   │   └── nop_auth_user_role.csv
        │   └── request.json5   # API请求数据，需要手工编写
        └── output/             # 预期输出数据
            ├── tables/         # 数据库预期变更，自动录制
            │   └── nop_auth_session.csv
            └── response.json5  # API响应预期，自动录制
```

## 3. 快速开始

### 3.1 创建测试类
```java
import io.nop.autotest.junit.JunitAutoTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.EnableSnapshot;

class TestLoginApi extends JunitAutoTestCase {
    @EnableSnapshot
    @Test
    public void testLogin() {
        // 1. 构建被测对象
        LoginApi loginApi = buildLoginApi();

        // 2. 读取输入数据
        ApiRequest<LoginRequest> request = input("request.json5",
            new TypeReference<ApiRequest<LoginRequest>>() {}.getType());

        // 3. 执行测试
        ApiResponse<LoginResult> result = loginApi.login(request);

        // 4. 验证输出结果
        output("response.json5", result);
    }
}
```

* input(fileName)是从`_cases/{testCaseClass}/{testMethod}/input/`目录读取文件
* output(fileName)的行为取决于模式
   - 在录制模式: 将结果写入`_cases/{testCaseClass}/{testMethod}/output/`目录
   - 验证模式: 验证实际结果与output目录中已录制的预期结果是否匹配


### 3.2 录制测试数据
**首次运行测试时**：
1. 执行从JunitAutoTestCase继承的测试用例，此时**尚未增加`@EnableSnapshot` 注解**
2. 自动在 `_cases/` 目录下生成测试数据文件
   - input/tables：录制的数据库读取数据
   - output/：数据库更改记录以及output输出结果数据（作为后续验证的基准）
3. 录制成功后会抛出`nop.err.autotest.snapshot-finished`异常，用于提示开发者后续可以添加`@EnableSnapshot`注解。


### 3.3 验证测试结果
当已有录制的测试数据时：
1. **添加 `@EnableSnapshot` 注解**到测试方法
2. 执行测试用例
3. 框架会：
   - 使用 input/ 目录中的数据作为输入
   - 将实际执行结果与 output/ 目录中的基准结果进行比对
   - 使用前缀引导语法进行灵活匹配

## 4. 核心功能

### 4.1 数据库测试支持
- 自动使用H2内存数据库
- 自动建表和初始化数据
- 验证数据库变更

### 4.2 多步骤测试
```java
@EnableSnapshot
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

### 4.3 数据变体
```java
@ParameterizedTest
@EnableVariants
@EnableSnapshot
public void testVariants(String variant) {
    // 自动加载/variants/{variant}/下的数据
    ApiRequest<LoginRequest> request = input("request.json5",
        new TypeReference<ApiRequest<LoginRequest>>() {}.getType());

    ApiResponse<LoginResult> result = loginApi.login(request);
    output("response.json5", result);
}
```

### 4.4 异常测试
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

- `error(fileName, supplier)`函数会捕获异常，并录制到output目录下的fileName文件中

## 5. 关键注解

### 5.1 @EnableSnapshot
**作用**：启用快照验证功能，对执行结果进行验证。

**重要说明**：
- 不添加此注解：仅执行测试，不验证结果（但会录制新数据）
- 添加此注解：执行测试并验证结果是否与录制的快照匹配

```java
@EnableSnapshot(
    localDb = true,          // 使用本地H2数据库，录制的初始化数据会初始化到这个数据库中
    saveOutput = false,      // 当saveOutput=true时，会更新执行快照，而不是比对快照和本次执行结果
)
```

1. **一般使用**：添加 `@EnableSnapshot` 注解即可，不用指定 `saveOutput` 等属性
2. **更新快照**：当业务逻辑变化导致测试结果变化，但希望复用原有的输入数据（特别是数据库初始化数据）时，可以使用 `@EnableSnapshot(saveOutput = true)` 来更新输出快照

### 5.2 @NopTestConfig
配置测试环境：
```java
@NopTestConfig(
    localDb = false,          // 如果设置为true，则录制阶段也是使用本地数据库。否则使用application.yaml配置
    initDatabaseSchema = false, // 如果设置为true，则录制阶段会自动在数据库中根据orm模型建表
    disableSnapshot = false,  // 忽略@EnableSnapshot注解，不启用快照验证功能
    testConfigFile = ""  // 在application.yaml配置之上叠加的测试专用配置文件
)
```

### 5.3 @NopTestProperty
直接设置测试专用属性：
```java
@NopTestProperty(name = "my.property", value = "test-value")
```

## 6. 最佳实践

### 6.1 测试数据管理
- 使用json5格式，支持注释
- 合理组织数据目录结构
- 利用数据变体测试边缘场景

### 6.2 测试代码编写
- 保持测试方法简洁
- 每个测试方法只测试一个功能
- 利用自动生成减少重复代码

### 6.3 测试执行
- 先录制后验证
- 定期更新测试数据
- 结合CI/CD自动执行

## 7. 常见问题

### 7.1 代码修改后不想重新录制初始化数据，只想更新执行快照
```java
@EnableSnapshot(saveOutput = true)
@Test
public void testLogin() {
    // 这会更新output目录下的文件，但保持input目录不变
    // 适用于业务逻辑变化但测试场景不变的场景
}
```

### 7.2 集成测试配置
```java
@EnableSnapshot(localDb = false)  // 使用外部数据库
@Test
public void integrationTest() {
    // 集成测试
}
```

## 8. 全局配置开关

以下开关用于整体更新测试数据。

- `nop.autotest.force-save-output`: 强制设置saveOutput为true，即所有测试用例都更新执行快照，而不是校验执行结果符合预期。
- `nop.autotest.disable-snapshot`: 全局禁用快照功能，所有测试用例都重新录制

## 9. 总结

AutoTest是一个强大的数据驱动测试框架，通过录制-验证模式和灵活的匹配语法，大大简化了自动化测试的编写和维护。它特别适合API测试和数据库测试，能够自动处理测试数据的生成、验证和管理，提高测试效率和质量。


对于AI开发者来说，AutoTest框架的优势在于：
- **减少代码编写**：专注于测试逻辑而非数据准备
- **数据与代码分离**：便于维护和更新测试用例
- **支持多种测试场景**：从单元测试到集成测试无缝切换
- **清晰的测试结果反馈**：通过前缀引导语法提供灵活的验证机制

**核心使用流程总结**：
1. **首次运行**：执行测试 → 自动录制数据 → 获得基准快照
2. **日常验证**：添加 `@EnableSnapshot` → 执行验证 → 确保代码变更不影响现有功能
3. **更新快照**：业务逻辑变化时使用 `@EnableSnapshot(saveOutput = true)` → 更新基准数据
