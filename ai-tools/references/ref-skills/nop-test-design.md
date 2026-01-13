# nop-test-design Skill

## Skill 概述

**名称**: nop-test-design（自动化测试设计）

**定位**: 基于所有前序阶段的产物，设计全面的自动化测试用例，包括单元测试、集成测试和端到端测试

**输入**:
1. 所有前序阶段的产物（数据库模型、服务层定义、前端页面等）
2. 测试需求描述（覆盖率要求、性能要求等）
3. BizModel方法列表
4. XMeta配置

**输出**:
1. 单元测试用例（JUnit 5）
2. 集成测试用例（Nop AutoTest）
3. 端到端测试用例（Playwright）
4. 测试配置（`test-config.xml`）
5. 测试覆盖率报告（`test-coverage-report.md`）

**能力**:
- 根据BizModel方法生成单元测试
- 根据服务层设计集成测试
- 根据用户场景设计端到端测试
- 配置测试覆盖率要求（> 80%）
- 设计Mock和测试数据
- 设计性能测试用例

**依赖**:
- Nop平台AutoTest文档（docs-for-ai/getting-started/testing/）
- JUnit 5文档（https://junit.org/junit5/docs/current/user-guide/）
- Playwright文档（https://playwright.dev/）

## 核心原则

### 1. 测试金字塔
- **单元测试**：快速、孤立、覆盖核心逻辑（70%）
- **集成测试**：测试服务层集成（20%）
- **端到端测试**：测试完整用户流程（10%）

### 2. 测试覆盖率
- **目标覆盖率**：> 80%
- **分支覆盖率**：> 70%
- **关键路径覆盖率**：100%

### 3. 测试隔离
- **单元测试**：使用Mock隔离依赖
- **集成测试**：使用测试数据库
- **端到端测试**：使用测试环境

### 4. 快速反馈
- **单元测试**：< 5秒
- **集成测试**：< 1分钟
- **端到端测试**：< 5分钟

## 工作流程

### 阶段1：测试策略设计

**步骤1.1：理解测试需求**
```
分析测试需求描述，理解：
- 测试覆盖率要求（> 80%）
- 性能要求（响应时间、吞吐量）
- 测试环境要求（测试数据库、Mock服务）
- 测试数据要求（测试数据集）
```

**步骤1.2：识别测试范围**
```
识别需要测试的功能点：
- 核心业务逻辑（订单创建、支付、状态转换等）
- 边界条件（空值、null值、非法值）
- 异常场景（并发修改、数据冲突、网络错误）
- 性能场景（大数据量、高并发）
```

**步骤1.3：生成测试用例清单**
```
生成测试用例清单：
- 单元测试用例
- 集成测试用例
- 端到端测试用例
- 性能测试用例
```

### 阶段2：单元测试设计

**步骤2.1：分析BizModel方法**
```
分析BizModel，提取：
- 查询方法（@BizQuery）
- 变更方法（@BizMutation）
- 动作方法（@BizAction）
- 扩展点（defaultPrepareSave、afterSave等）
```

**步骤2.2：设计单元测试用例**
```
为每个BizModel方法设计测试用例：
- 正常场景（Happy Path）
- 边界条件（Boundary Conditions）
- 异常场景（Error Cases）
```

**示例**：
```java
@ExtendWith(NopTestExtension.class)
class NopAuthUserServiceModelTest {

    @Inject
    private NopAuthUserServiceModel service;

    @Inject
    private IEntityDao<NopAuthUser> userDao;

    @Test
    void testFindActiveUsers() {
        // Given: 准备测试数据
        NopAuthUser activeUser = createTestUser(1);
        NopAuthUser inactiveUser = createTestUser(0);

        // When: 执行查询
        List<NopAuthUser> users = service.findActiveUsers();

        // Then: 验证结果
        Assertions.assertNotNull(users);
        Assertions.assertTrue(users.size() > 0);
        Assertions.assertTrue(users.stream().allMatch(u -> u.getStatus() == 1));
    }

    @Test
    void testFindUserByOpenId() {
        // Given: 准备测试数据
        String openId = "test_open_id_001";
        NopAuthUser user = createTestUser(openId);

        // When: 执行查询
        NopAuthUser found = service.getUserByOpenId(openId);

        // Then: 验证结果
        Assertions.assertNotNull(found);
        Assertions.assertEquals(openId, found.getOpenId());
    }

    @Test
    void testResetUserPassword() {
        // Given: 准备测试数据
        NopAuthUser user = createTestUser();
        String newPassword = "new_password_123";

        // When: 执行密码重置
        service.resetUserPassword(user.getId(), newPassword);

        // Then: 验证密码已更新
        NopAuthUser updated = userDao.requireEntityById(user.getId());
        Assertions.assertNotNull(updated);
        Assertions.assertTrue(passwordEncoder.matches(
            newPassword, updated.getPassword()));
    }

    @Test
    void testResetUserPassword_UserNotFound() {
        // Given: 不存在的用户ID
        String userId = "non_existent_user_id";

        // When & Then: 执行密码重置，应该抛出异常
        Assertions.assertThrows(NopException.class, () -> {
            service.resetUserPassword(userId, "new_password");
        });
    }

    @Test
    void testCreateUser() {
        // Given: 准备测试数据
        NopAuthUser user = new NopAuthUser()
            .setName("Test User")
            .setEmail("test@example.com")
            .setPassword("password123");

        // When: 创建用户
        NopAuthUser created = service.save(
            Collections.singletonMap("entity", user));

        // Then: 验证用户已创建
        Assertions.assertNotNull(created);
        Assertions.assertNotNull(created.getId());
        Assertions.assertEquals("Test User", created.getName());
        Assertions.assertEquals("test@example.com", created.getEmail());
    }

    // 辅助方法
    private NopAuthUser createTestUser(int status) {
        NopAuthUser user = new NopAuthUser()
            .setName("Test User")
            .setEmail("test@example.com")
            .setPassword(passwordEncoder.encode("password123"))
            .setStatus(status);
        userDao.saveEntity(user);
        return user;
    }

    private NopAuthUser createTestUser(String openId) {
        NopAuthUser user = new NopAuthUser()
            .setOpenId(openId)
            .setName("Test User")
            .setEmail("test@example.com")
            .setPassword(passwordEncoder.encode("password123"))
            .setStatus(1);
        userDao.saveEntity(user);
        return user;
    }
}
```

### 阶段3：集成测试设计

**步骤3.1：分析服务层集成**
```
分析服务层，提取：
- 服务间调用（BizModel之间）
- 数据库操作（DAO调用）
- 外部服务调用（API、消息队列等）
```

**步骤3.2：设计集成测试用例**
```
设计集成测试用例：
- 测试服务间调用
- 测试数据库事务
- 测试数据权限
- 测试并发场景
```

**示例**：
```java
@ExtendWith(NopTestExtension.class)
@NopIntegrationTest
class OrderServiceIntegrationTest {

    @Inject
    private OrderBizModel orderBizModel;

    @Inject
    private ProductBizModel productBizModel;

    @Inject
    private IUserDao userDao;

    @Inject
    private IOrderDao orderDao;

    @Test
    @Transactional
    void testCreateOrderWithItems() {
        // Given: 准备测试数据
        User user = createTestUser();
        Product product1 = createTestProduct("Product 1", 100.00);
        Product product2 = createTestProduct("Product 2", 200.00);

        List<OrderItem> items = Arrays.asList(
            new OrderItem()
                .setProductId(product1.getId())
                .setProductPrice(product1.getPrice())
                .setQuantity(2),
            new OrderItem()
                .setProductId(product2.getId())
                .setProductPrice(product2.getPrice())
                .setQuantity(3)
        );

        Order order = new Order()
            .setUserId(user.getId())
            .setOrderStatus(OrderStatus.PENDING.name());

        // When: 创建订单
        Order created = orderBizModel.createOrder(order, items);

        // Then: 验证订单和订单项已创建
        Assertions.assertNotNull(created);
        Assertions.assertNotNull(created.getId());
        Assertions.assertEquals(user.getId(), created.getUserId());

        // 验证订单项
        List<OrderItem> createdItems = orderBizModel.findOrderItemsByOrderId(created.getId());
        Assertions.assertNotNull(createdItems);
        Assertions.assertEquals(2, createdItems.size());

        // 验证总金额计算
        BigDecimal expectedTotal = BigDecimal.valueOf(2 * 100.00 + 3 * 200.00);
        Assertions.assertEquals(0, created.getTotalAmount().compareTo(expectedTotal));
    }

    @Test
    void testOrderStatusTransition() {
        // Given: 创建待支付订单
        Order order = createTestOrder(OrderStatus.PENDING);

        // When: 支付订单
        orderBizModel.payOrder(order.getId());

        // Then: 验证订单状态已更新
        Order paidOrder = orderDao.requireEntityById(order.getId());
        Assertions.assertEquals(OrderStatus.PAID.name(), paidOrder.getOrderStatus());
    }

    @Test
    void testConcurrentOrderUpdate() throws InterruptedException {
        // Given: 创建订单
        Order order = createTestOrder(OrderStatus.PENDING);

        // When: 并发更新订单
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Exception> exception1 = new AtomicReference<>();
        AtomicReference<Exception> exception2 = new AtomicReference<>();

        Thread thread1 = new Thread(() -> {
            try {
                orderBizModel.payOrder(order.getId());
            } catch (Exception e) {
                exception1.set(e);
            } finally {
                latch.countDown();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                orderBizModel.cancelOrder(order.getId());
            } catch (Exception e) {
                exception2.set(e);
            } finally {
                latch.countDown();
            }
        });

        thread1.start();
        thread2.start();
        latch.await();

        // Then: 验证只有一个操作成功
        Order updated = orderDao.requireEntityById(order.getId());
        Assertions.assertTrue(
            updated.getOrderStatus().equals(OrderStatus.PAID.name()) ||
            updated.getOrderStatus().equals(OrderStatus.CANCELLED.name())
        );
    }
}
```

### 阶段4：端到端测试设计

**步骤4.1：分析用户场景**
```
分析用户场景，提取：
- 核心用户流程（注册、登录、下单、支付等）
- 关键业务流程（订单创建、状态转换、支付流程等）
```

**步骤4.2：设计端到端测试用例**
```
设计端到端测试用例：
- 测试完整用户流程
- 测试页面跳转
- 测试表单提交
- 测试页面元素显示
```

**示例**：
```java
import com.microsoft.playwright.*;

@ExtendWith(NopTestExtension.class)
class OrderEndToEndTest {

    private Playwright playwright;
    private Browser browser;
    private Page page;

    @BeforeEach
    void setUp() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        page = browser.newPage();
    }

    @AfterEach
    void tearDown() {
        browser.close();
        playwright.close();
    }

    @Test
    void testCreateOrder() {
        // Given: 用户登录
        page.navigate("http://localhost:8080/login");
        page.fill("input[name='username']", "testuser");
        page.fill("input[name='password']", "password123");
        page.click("button[type='submit']");

        // Then: 验证登录成功
        Assertions.assertEquals("首页", page.title());

        // When: 进入商品列表
        page.click("a[href='/products']");
        Assertions.assertEquals("商品列表", page.title());

        // When: 选择商品
        page.click("button:has-text('添加到购物车'):first");
        page.click("button:has-text('添加到购物车')");

        // Then: 验证购物车有商品
        page.click("a[href='/cart']");
        Assertions.assertTrue(page.locator(".cart-item").count() > 0);

        // When: 提交订单
        page.click("button:has-text('提交订单')");

        // Then: 验证订单创建成功
        Assertions.assertTrue(page.url().contains("/orders/"));
        Assertions.assertTrue(page.textContent().contains("订单创建成功"));
    }

    @Test
    void testOrderPayment() {
        // Given: 创建订单
        String orderId = createTestOrder(page);

        // When: 进入订单详情
        page.navigate("http://localhost:8080/orders/" + orderId);

        // When: 支付订单
        page.click("button:has-text('支付')");
        page.fill("input[name='cardNumber']", "4111111111111111");
        page.fill("input[name='expiryDate']", "12/25");
        page.fill("input[name='cvv']", "123");
        page.click("button:has-text('确认支付')");

        // Then: 验证支付成功
        Assertions.assertTrue(page.textContent().contains("支付成功"));
        Assertions.assertTrue(page.textContent().contains("订单状态：已支付"));
    }

    private String createTestOrder(Page page) {
        // 创建订单的逻辑
        // ...
        return "order_id_001";
    }
}
```

### 阶段5：测试配置生成

**步骤5.1：生成test-config.xml**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<test-config x:schema="/nop/schema/test-config.xdef"
               xmlns:x="/nop/schema/xdsl.xdef">
    <coverage>
        <minLineCoverage>80</minLineCoverage>
        <minBranchCoverage>70</minBranchCoverage>
        <minMethodCoverage>85</minMethodCoverage>
    </coverage>

    <database>
        <url>jdbc:h2:mem:testdb</url>
        <username>sa</username>
        <password></password>
    </database>

    <mock-services>
        <mock-service name="paymentService">
            <implementation>io.nop.mock.PaymentServiceMock</implementation>
        </mock-service>
    </mock-services>
</test-config>
```

### 阶段6：测试覆盖率报告生成

**步骤6.1：生成测试覆盖率报告**
```markdown
# 测试覆盖率报告

## 总体覆盖率
- 代码覆盖率：85%
- 分支覆盖率：75%
- 方法覆盖率：90%

## 模块覆盖率
| 模块 | 代码覆盖率 | 分支覆盖率 | 方法覆盖率 |
|------|----------|----------|----------|
| {module}-dao | 90% | 80% | 95% |
| {module}-service | 85% | 75% | 90% |
| {module}-api | 80% | 70% | 85% |

## 未覆盖的关键路径
- 订单取消流程：部分边界条件未覆盖
- 并发场景：测试用例较少
- 异常场景：某些异常未覆盖

## 改进建议
1. 增加订单取消流程的测试用例
2. 增加并发场景的测试用例
3. 增加异常场景的测试用例
```

## AI推理策略

### 1. 测试用例生成推理
- **单元测试生成**：
  - 根据BizModel方法生成正常场景、边界条件、异常场景的测试用例
  - 使用Mock隔离依赖

- **集成测试生成**：
  - 根据服务间调用关系设计集成测试
  - 测试事务边界和数据一致性

- **端到端测试生成**：
  - 根据用户场景设计端到端测试
  - 测试完整的用户流程

### 2. 测试覆盖率推理
- **识别未覆盖的代码**：
  - 识别未测试的分支
  - 识别未测试的异常
  - 识别未测试的边界条件

- **设计补充测试用例**：
  - 为未覆盖的分支设计测试用例
  - 为未测试的异常设计测试用例
  - 为未测试的边界条件设计测试用例

## 验证点

### 1. 单元测试验证
- [ ] 测试用例是否覆盖所有BizModel方法
- [ ] 测试用例是否包含正常场景、边界条件、异常场景
- [ ] Mock是否正确

### 2. 集成测试验证
- [ ] 测试用例是否覆盖服务间调用
- [ ] 事务测试是否正确
- [ ] 数据权限测试是否正确

### 3. 端到端测试验证
- [ ] 测试用例是否覆盖关键用户流程
- [ ] 页面元素选择器是否正确
- [ ] 测试数据是否合理

### 4. 测试覆盖率验证
- [ ] 代码覆盖率是否> 80%
- [ ] 分支覆盖率是否> 70%
- [ ] 方法覆盖率是否> 85%

## 输出产物

### 1. 单元测试（JUnit 5）
```java
@ExtendWith(NopTestExtension.class)
class {module}ServiceModelTest {
    // 测试用例...
}
```

### 2. 集成测试（Nop AutoTest）
```java
@ExtendWith(NopTestExtension.class)
@NopIntegrationTest
class {module}ServiceIntegrationTest {
    // 测试用例...
}
```

### 3. 端到端测试（Playwright）
```java
import com.microsoft.playwright.*;

class {module}EndToEndTest {
    // 测试用例...
}
```

### 4. 测试配置（`test-config.xml`）
```xml
<?xml version="1.0" encoding="UTF-8"?>
<test-config x:schema="/nop/schema/test-config.xdef"
               xmlns:x="/nop/schema/xdsl.xdef">
    <coverage>
        <minLineCoverage>80</minLineCoverage>
    </coverage>
</test-config>
```

### 5. 测试覆盖率报告（`test-coverage-report.md`）
```markdown
# 测试覆盖率报告
...
```

## 下一步工作

当前skill完成自动化测试设计，生成以下产物：
1. 单元测试用例（JUnit 5）
2. 集成测试用例（Nop AutoTest）
3. 端到端测试用例（Playwright）
4. 测试配置（`test-config.xml`）
5. 测试覆盖率报告（`test-coverage-report.md`）

这些产物将传递给下一个skill（nop-cicd-design）用于CI/CD设计。

