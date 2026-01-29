# Nop 平台测试入口（NopAutoTest）

`docs-for-ai` 不重复记录通用测试最佳实践；这里只保留 Nop 平台测试体系的“差异点”。

请以以下文档为准：

- NopAutoTest / `@NopTestConfig` / 测试容器启动方式：
  - `../getting-started/test/autotest-guide.md`
- IoC 注入规则（尤其是 `@Inject` 不支持 private 字段、值注入用 `@InjectValue`）：
  - `../getting-started/core/ioc-guide.md`

### 1. Mock外部依赖

隔离测试外部系统调用：

Nop 平台不内置/不强制某个 Mock 框架。更稳妥的做法是：

1. 依赖抽象成接口（例如 `IMailSender`）
2. 单元测试里用手写 stub/fake（可读、无额外注解）
3. 只在确实需要时引入第三方 Mock 框架，并避免把它当成 Nop 的标准用法写进文档

### 2. 使用测试数据库

使用测试数据库：

在 Nop AutoTest/JUnit 集成中，一般通过 `@NopTestConfig(localDb = true, initDatabaseSchema = true)` 启用本地 H2 以及 schema 初始化，避免在文档中硬编码某个 `application-test.yaml` 结构。

### 3. 测试数据管理

使用fixture管理测试数据：

```java
public class TestDataFixtures {

    public void loadFixtures(String... fixtureNames) {
        for (String name : fixtureNames) {
            String fixturePath = "classpath:/fixtures/" + name + ".json";
            String json = ResourceHelper.readText(fixturePath);
            List entities = JsonTool.parseList(json, Object.class);
            for (Object entity : entities) {
                dao().saveEntity(entity);
            }
        }
    }

    public void clearFixtures() {
        dao().deleteAll();
    }
}
```

## 测试最佳实践

### 1. 测试独立性

每个测试应该独立运行：

```java
// ✅ 推荐：使用@BeforeEach
public class UserServiceTest extends JunitBaseTestCase {

    @BeforeEach
    public void setUp() {
        // 准备测试数据
        testData = createTestData();
    }

    @AfterEach
    public void tearDown() {
        // 清理测试数据
        cleanupTestData();
    }

    @Test
    public void testGetUser() {
        // 测试代码
    }
}

// ❌ 不推荐：依赖测试顺序
public class UserServiceTest extends JunitBaseTestCase {
    @Test
    public void test01_CreateUser() {
        // ...
    }

    @Test
    public void test02_UpdateUser() {
        // 依赖test01的结果
        User user = getCreatedUserInTest01();
        // ...
    }
}
```

### 2. 测试命名

使用描述性的测试名称：

```java
// ✅ 推荐：描述性命名
@Test
public void testCreateUser_WhenValidData_ShouldReturnCreatedUser() {
}

@Test
public void testCreateUser_WhenEmailEmpty_ShouldThrowException() {
}

// ❌ 不推荐：模糊命名
@Test
public void test1() {
}

@Test
public void testUser() {
}
```

### 3. 测试数据

使用有意义的测试数据：

```java
@Test
public void testCalculatePrice() {
    // ✅ 推荐：有意义的测试数据
    Order order = new Order();
    order.setPrice(new BigDecimal("100.00"));
    order.setQuantity(5);

    BigDecimal expected = new BigDecimal("500.00");
    BigDecimal actual = orderService.calculatePrice(order);
    assertEquals(expected, actual);

    // ❌ 不推荐：随意数据
    Order order = new Order();
    order.setPrice(new BigDecimal("123.456"));
    order.setQuantity(789);
}
```

### 4. 异常测试

正确测试异常情况：

```java
@Test
public void testGetUser_WhenUserNotFound_ShouldThrowException() {
    // Given
    String userId = "non-existent-id";

    // When & Then
    NopException exception = assertThrows(NopException.class, () -> {
        userService.getUser(userId);
    });

    assertEquals(ERR_USER_NOT_FOUND, exception.getErrorCode());
}

@Test
public void testDeleteUser_WhenUserHasOrders_ShouldThrowException() {
    // Given
    String userId = "user-with-orders";

    // When & Then
    NopException exception = assertThrows(NopException.class, () -> {
        userService.deleteUser(userId);
    });

    assertEquals(ERR_USER_HAS_ORDERS, exception.getErrorCode());
}
```

### 5. 异步测试

测试异步方法：

```java
@Test
@Timeout(5)  // 5秒超时
public void testAsyncMethod() throws Exception {
    // Given
    String userId = "test-001";

    // When
    CompletableFuture<User> future = userService.getUserAsync(userId);

    // Then
    User user = future.get();  // 阻塞等待
    assertNotNull(user);
}
```

## 单元测试 vs 集成测试

### 1. 简单单元测试（不使用 @NopTestConfig）

对于**最简单的单元测试**，如果测试对象不需要：
- IoC 容器注入（如 `@Inject`）
- 数据库访问（如 `dao()`）
- 配置管理（如 `@InjectValue`）
- 文件系统访问（如 `_vfs`）

则**可以直接使用 JUnit 5**，**无需** `@NopTestConfig` 和 `JunitBaseTestCase`：

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CalculatorTest {

    @Test
    public void testAdd() {
        Calculator calc = new Calculator();
        assertEquals(5, calc.add(2, 3));
    }

    @Test
    public void testSimpleCommandExecution() {
        // 手写 stub/fake，无需 IoC 容器
        SimpleTestRegistry registry = new SimpleTestRegistry();
        CommandExecutor executor = new CommandExecutor(parser, registry, null);
        
        ExecutionResult result = executor.execute("echo hello");
        assertEquals(0, result.exitCode());
    }
}
```

**使用场景**：
- ✅ 纯算法/工具类测试
- ✅ 使用手写 stub/fake 的测试
- ✅ 无需外部依赖的测试
- ❌ 不适用于需要 IoC 注入、数据库、配置的集成测试

### 2. 集成测试（使用 @NopTestConfig）

如果测试需要：
- `@Inject` 注入 bean
- 访问数据库（`dao()`）
- 使用配置（`@InjectValue`）
- 访问 `_vfs` 虚拟文件系统

则需要使用 `@NopTestConfig` 和 `JunitBaseTestCase`：

```java
import io.nop.test.core.junit.JunitBaseTestCase;
import io.nop.test.core.annotation.NopTestConfig;

@NopTestConfig(localDb = true)
public class UserServiceTest extends JunitBaseTestCase {

    @Inject
    protected IUserDao userDao;

    @Inject
    protected IUserService userService;

    @Test
    public void testCreateUser() {
        User user = new User();
        user.setName("test");
        userDao.saveEntity(user);

        assertNotNull(user.getId());
    }
}
```

**选择原则**：
- 优先使用**简单单元测试**（无需 `@NopTestConfig`）
- 需要外部依赖时才使用**集成测试**（`@NopTestConfig`）

## 集成测试

### 1. 端到端测试

端到端测试（E2E）覆盖“进程外”的真实网络边界，取决于你选择的 Web/网关运行时与部署方式。

本文不提供可复制的 HTTP 客户端示例（例如 Spring 的 `TestRestTemplate` / `MockMvc`），避免误导。

建议：

1. 如果你要测 GraphQL/RPC，优先写“进程内集成测试”：在 `JunitBaseTestCase` 中注入 `IGraphQLEngine` 或 BizModel，直接调用执行入口。
2. 如果你要测 HTTP 适配层，把 E2E 放到独立的测试项目/脚本里（例如 Postman/Newman、k6、pytest、你们内部框架），并在 CI/CD 部署后执行。

### 2. API测试

测试REST API接口：

```java
@NopTestConfig(localDb = true)
public class OrderApiTest extends JunitBaseTestCase {

    @Inject
    protected IOrderBizModel orderBizModel;

    @Test
    public void testCreateOrder() {
        // Given
        OrderData order = new OrderData();
        order.setUserId("test-user");
        order.setAmount(new BigDecimal("100"));

        // When
        Order created = orderBizModel.createOrder(order);

        // Then
        assertNotNull(created);
        assertNotNull(created.getOrderId());
    }
}
```

## 测试配置

### 1. 测试环境配置

测试用例通常使用 `@NopTestConfig` 来控制测试环境（是否使用本地 H2、是否初始化数据库 schema、启用哪些 auto-config、引入哪些 test bean 配置等）。

如果你确实需要加载额外的 properties 文件，可以参考项目中 `@NopTestConfig(testConfigFile = "classpath:xxx.properties")` 的用法。

### 2. Maven测试配置

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <includes>
                    <include>**/*Test.java</include>
                    <include>**/*Tests.java</include>
                </includes>
                <excludes>
                    <exclude>**/Abstract*.java</exclude>
                    <exclude>**/*IntegrationTest.java</exclude>
                </excludes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## 持续集成测试

### 1. CI/CD集成

在CI/CD中运行测试：

```yaml
# .github/workflows/test.yml
name: Run Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 17
        uses: actions/setup-java@v2
        with:
          java-version: '17'
      - name: Build with Maven
                run: mvn test
```

### 2. 测试报告

生成和发布测试报告：

```bash
# 生成测试报告
mvn clean test jacoco:report

# 发布到Codecov
bash <(curl -s https://codecov.io/bash) -t ${CODECOV_TOKEN}
```

## 常见测试问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 测试依赖执行顺序 | 共享测试数据 | 使用@BeforeEach和@AfterEach |
| Mock失败 | 不正确的Mock设置 | 检查Mock配置 |
| 测试不稳定 | 随机性或时间依赖 | 隔离外部依赖、固定测试数据 |
| 测试慢 | 数据库操作过多 | 使用测试数据库、Mock外部调用 |
| 覆盖率低 | 未覆盖所有场景 | 添加更多测试用例 |
| 测试难以维护 | 测试代码复杂 | 简化测试、提取公共方法 |

## 测试清单

### 测试前

- [ ] 明确测试目标和范围
- [ ] 设计测试用例覆盖所有场景
- [ ] 准备测试数据和Mock
- [ ] 配置测试环境

### 测试中

- [ ] 编写清晰的测试代码
- [ ] 使用Given-When-Then模式
- [ ] 验证所有预期结果
- [ ] 处理异常情况

### 测试后

- [ ] 运行所有测试
- [ ] 检查测试覆盖率
- [ ] 修复失败的测试
- [ ] 重构测试代码

## 相关文档

- [AutoTest指南](../getting-started/test/autotest-guide.md)
- [服务层开发指南](../getting-started/service/service-layer-development.md)
- [异常处理指南](../getting-started/core/exception-guide.md)
- [测试调试指南](../getting-started/test/nop-debug-and-diagnosis-guide.md)

## 总结

Nop Platform测试是保证代码质量的关键。通过遵循这些最佳实践：

1. **多层次测试**: 单元测试、集成测试、端到端测试
2. **测试独立性**: 每个测试独立运行
3. **清晰的结构**: Given-When-Then模式
4. **合适的Mock**: 隔离外部依赖
5. **高覆盖度**: 追求较高的测试覆盖率
6. **持续集成**: 在CI/CD中自动化测试
7. **快速反馈**: 快速定位和修复问题

通过构建完善的测试体系，可以提高代码质量，减少生产环境问题。

