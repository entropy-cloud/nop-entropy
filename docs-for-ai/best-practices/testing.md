# Nop Platform Testing Best Practices

## 概述

本文档提供Nop Platform测试开发的最佳实践，帮助开发者构建可靠、可维护的测试体系。测试包括单元测试、集成测试、端到端测试等多个层次。

## 测试类型

### 1. 单元测试

测试单个类或方法的功能：

```java
@ExtendWith(JunitExtension.class)
public class UserServiceTest {

    @Inject
    private IUserService userService;

    @Test
    public void testGetUserById() {
        // Given
        String userId = "test-user-001";

        // When
        User user = userService.getUser(userId);

        // Then
        assertNotNull(user);
        assertEquals(userId, user.getUserId());
    }
}
```

### 2. 集成测试

测试多个组件之间的交互：

```java
@ExtendWith(JunitExtension.class)
public class OrderIntegrationTest {

    @Inject
    private IOrderBizModel orderBizModel;

    @Inject
    private IInventoryBizModel inventoryBizModel;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void testCreateOrderWithInventoryUpdate() {
        // Given
        Order order = createTestOrder();

        // When
        Order created = orderBizModel.createOrder(order);

        // Then
        assertNotNull(created);
        assertNotNull(created.getOrderId());

        // 验证库存更新
        Inventory inventory = inventoryBizModel.getInventory(order.getProductId());
        assertEquals(order.getQuantity(), inventory.getReservedQty());
    }
}
```

### 3. 端到端测试

测试完整的业务流程：

```java
@ExtendWith(JunitExtension.class)
public class OrderE2ETest {

    @Inject
    private IOrderBizModel orderBizModel;

    @Test
    public void testCompleteOrderFlow() {
        // 1. 用户登录
        LoginResult login = authService.login("test-user", "password");

        // 2. 创建订单
        Order order = createTestOrder();
        Order created = orderBizModel.createOrder(order);

        // 3. 支付订单
        PaymentResult payment = paymentService.pay(created.getOrderId());
        assertTrue(payment.isSuccess());

        // 4. 发货
        ShippingResult shipping = shippingService.ship(created.getOrderId());
        assertTrue(shipping.isSuccess());

        // 5. 完成订单
        Order completed = orderBizModel.completeOrder(created.getOrderId());
        assertEquals("COMPLETED", completed.getStatus());
    }
}
```

## 测试工具

### 1. Junit框架

Nop Platform使用JUnit 5：

```java
@ExtendWith(JunitExtension.class)
public class MyTest {

    @Test
    public void testMethod() {
        // 测试代码
    }

    @Test
    @DisplayName("测试用户创建")
    public void testCreateUser() {
        // 测试代码
    }
}
```

### 2. 自动测试框架

使用nop-autotest进行自动化测试：

```java
@ExtendWith(JunitExtension.class)
public class AutoTestExample {

    @BizQuery
    public User getUser(String userId) {
        return dao().getEntityById(userId);
    }

    @Test
    public void testGetUser(@Name("userId") String userId) {
        // 第一次运行：录制结果
        User user = getUser(userId);

        // 第二次运行：自动比较
        assertDeepEquals(user, getUser(userId));
    }
}
```

### 3. Mock工具

使用Mock框架隔离测试：

```java
@ExtendWith(MockitoExtension.class)
public class UserServiceWithMockTest {

    @Mock
    private IUserDao userDao;

    @InjectMocks
    private UserService userService;

    @Test
    public void testGetUser() {
        // Given
        String userId = "test-001";
        User expectedUser = new User();
        expectedUser.setUserId(userId);
        when(userDao.getEntityById(userId)).thenReturn(expectedUser);

        // When
        User actualUser = userService.getUser(userId);

        // Then
        assertEquals(expectedUser, actualUser);
        verify(userDao).getEntityById(userId);
    }
}
```

## 测试模式

### 1. Given-When-Then模式

清晰的测试结构：

```java
@Test
public void testUpdateUserName() {
    // Given - 准备测试数据
    User user = new User();
    user.setUserId("test-001");
    user.setUserName("Old Name");
    when(userDao.saveEntity(any(User.class))).thenReturn(user);

    // When - 执行测试操作
    User updated = userService.updateUserName("test-001", "New Name");

    // Then - 验证结果
    assertEquals("New Name", updated.getUserName());
    verify(userDao).saveEntity(any(User.class));
}
```

### 2. AAA模式（Arrange-Act-Assert）

与Given-When-Then类似：

```java
@Test
public void testDeleteUser() {
    // Arrange - 准备
    String userId = "test-001";
    User user = new User();
    user.setUserId(userId);
    when(userDao.getEntityById(userId)).thenReturn(user);

    // Act - 执行
    userService.deleteUser(userId);

    // Assert - 验证
    verify(userDao).deleteEntity(user);
}
```

### 3. 测试驱动开发（TDD）

先写测试，再实现功能：

```java
// 1. 先写测试
@Test
public void testCalculateOrderTotal() {
    Order order = new Order();
    order.setPrice(new BigDecimal("100"));
    order.setQuantity(2);
    order.setTaxRate(new BigDecimal("0.1"));

    BigDecimal expected = new BigDecimal("220");  // (100 * 2 * 1.1)

    BigDecimal actual = orderService.calculateTotal(order);
    assertEquals(expected, actual);
}

// 2. 再实现功能
public BigDecimal calculateTotal(Order order) {
    return order.getPrice()
        .multiply(new BigDecimal(order.getQuantity()))
        .multiply(order.getTaxRate().add(new BigDecimal("1")));
}
```

## 测试覆盖

### 1. 语句覆盖

每条语句至少被执行一次：

```bash
# 运行测试并生成覆盖报告
mvn clean test jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

### 2. 分支覆盖

每个分支至少被执行一次：

```java
@Test
public void testValidateUser() {
    // 测试正常分支
    User user = createValidUser();
    boolean result = userService.validateUser(user);
    assertTrue(result);

    // 测试异常分支
    user.setEmail("");
    result = userService.validateUser(user);
    assertFalse(result);
}
```

### 3. 覆盖率目标

| 测试类型 | 最低目标 | 推荐目标 |
|---------|---------|----------|
| 语句覆盖 | 70% | 85%+ |
| 分支覆盖 | 60% | 80%+ |
| 方法覆盖 | 80% | 95%+ |

## Mock策略

### 1. Mock外部依赖

隔离测试外部系统调用：

```java
@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Test
    public void testSendEmail() {
        // Given
        String to = "test@example.com";
        String subject = "Test Email";
        String content = "Test Content";

        // When
        emailService.sendEmail(to, subject, content);

        // Then
        verify(mailSender).send(eq(to), eq(subject), eq(content));
    }
}
```

### 2. 使用测试数据库

使用H2内存数据库进行测试：

```yaml
# application-test.yaml
datasource:
  url: jdbc:h2:mem:testdb
  driver: org.h2.Driver
  username: sa
  password:
```

### 3. 测试数据管理

使用fixture管理测试数据：

```java
@Component
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
@ExtendWith(JunitExtension.class)
public class UserServiceTest {

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
@ExtendWith(JunitExtension.class)
public class UserServiceTest {
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

## 集成测试

### 1. 端到端测试

测试完整的应用流程：

```java
@ExtendWith(JunitExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testCompleteOrderProcess() {
        // 1. 登录
        LoginRequest login = new LoginRequest("test-user", "password");
        ResponseEntity<LoginResult> loginResponse = restTemplate.postForEntity(
            "/api/auth/login", login, LoginResult.class
        );
        assertTrue(loginResponse.getStatusCode().is2xxSuccessful());

        // 2. 创建订单
        Order order = createTestOrder();
        ResponseEntity<Order> orderResponse = restTemplate.postForEntity(
            "/api/order/create", order, Order.class
        );
        assertTrue(orderResponse.getStatusCode().is2xxSuccessful());

        // 3. 支付
        Payment payment = new Payment(orderResponse.getBody().getOrderId(), "100.00");
        ResponseEntity<PaymentResult> paymentResponse = restTemplate.postForEntity(
            "/api/payment/pay", payment, PaymentResult.class
        );
        assertTrue(paymentResponse.getStatusCode().is2xxSuccessful());
    }
}
```

### 2. API测试

测试REST API接口：

```java
@ExtendWith(JunitExtension.class)
public class OrderApiTest {

    @Inject
    private IOrderBizModel orderBizModel;

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

```yaml
# application-test.yaml
spring:
  profiles:
    active: test

datasource:
  url: jdbc:h2:mem:testdb
  driver: org.h2.Driver

logging:
  level:
    io.nop: DEBUG
```

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
        run: mvn clean test
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

---

**文档版本**: 1.0
**最后更新**: 2025-01-09
**作者**: AI Assistant (Sisyphus)
