# 编写测试

## 适用场景

- 需要测试 BizModel、Processor、GraphQL 或其他容器内逻辑。
- 需要录制与校验快照。

## AI 决策提示

- 需要快照录制时，优先 `JunitAutoTestCase`。
- 不需要快照时，优先 `JunitBaseTestCase`。
- 需要容器、数据库、配置、`_vfs` 时，使用 `@NopTestConfig`。
- `JunitAutoTestCase` 必须带类级别 `@NopTestConfig`；`JunitBaseTestCase` 可按需添加。

## 最小闭环

### 1. 选择基类

| 场景 | 基类 |
|------|------|
| 快照录制 / 校验 | `JunitAutoTestCase` |
| 普通进程内测试 | `JunitBaseTestCase` |

### 2. 按需添加 `@NopTestConfig`

`JunitBaseTestCase` 并不是每次都要配 `@NopTestConfig`。只有在需要本地库、测试配置、测试 beans 或快照能力时再加。

```java
@NopTestConfig(localDb = true)
public class OrderBizModelTest extends JunitBaseTestCase {
    @Inject
    protected OrderBizModel orderBizModel;
}
```

### 3. 快照测试工作流

1. 首次录制：`snapshotTest = SnapshotTest.RECORDING`
2. 日常验证：默认 `CHECKING`
3. 只更新输出：`forceSaveOutput = true`

常用 helper：

1. `input(...)`
2. `request(...)`
3. `output(...)`
4. `outputText(...)`

录制模式保存输出后会抛出一个错误码为 `nop.err.autotest.snapshot-finished` 的专用异常表示录制完成，这是**预期行为**不是失败。Maven 会报告 `Errors: X`，切换到 CHECKING 模式后 Errors 归零。

### 4. 数据目录

| 场景 | 位置 |
|------|------|
| 快照测试 | `_cases/...` |
| 普通资源文件 | `src/test/resources/...` |

## 使用 `IGraphQLEngine` 测试 BizModel 方法

`@BizQuery`/`@BizMutation` 方法必须通过 `IGraphQLEngine` 测试（不能只写实体级纯逻辑测试）。

### 注入引擎 + 设置用户上下文

```java
@Inject
IGraphQLEngine graphQLEngine;

void setUser(String userId, String userName) {
    ContextProvider.getOrCreateContext().setUserId(userId);
    ContextProvider.getOrCreateContext().setUserName(userName);
}
```

### 构造请求

`IGraphQLEngine.newRpcContext()` 接收完整的 `ApiRequest<?>`。请求通过 `request()` 或 `input()` 从 `_cases/.../input/` 目录的 JSON5 文件中读取。

```java
// 推荐：从 input 目录读取 ApiRequest（文件包含完整结构）
ApiResponse<?> result = executeRpc(
    request("save_request.json5", Map.class));    // ← bodyType = Map.class

// 等价的内联写法（不使用文件）：
ApiResponse<?> result = executeRpc(
    ApiRequest.build(Map.of("data", addressData)));
```

**`request(fileName, bodyType)` 的行为**：
- 从 `_cases/.../<testMethod>/input/<fileName>` 读取 JSON5
- 返回 `ApiRequest<T>`，其中 `T = bodyType`
- `bodyType` 指定了 `ApiRequest.data` 的 Java 类型

`ApiRequest.data` 的内容格式取决于目标方法的参数注解：

| 方法签名 | `request()` 读取的 JSON5 文件结构 | 说明 |
|---------|----------------------------------|------|
| `save(@Name("data") Map<String,Object> data, ...)` | `{ data: { data: { name: "张三", ... } } }` | `@Name("data")`：请求 data 内嵌套一层 `data` |
| `setDefault(@Name("id") String id, ...)` | `{ data: { id: "...", ... } }` | 扁平参数：请求 data 内直接放参数名 → 值 |
| `getMyList(...)` | `{ data: {} }` | 无业务参数，空 map |

**阅读 `request()` 的文件时，框架会自动解析 `@var:` 引用**（见下文多步测试模式）。

#### `request()` 与 `input()` 的选择

| 方法 | 返回类型 | 用途 |
|------|---------|------|
| `request(file, bodyType)` | `ApiRequest<T>` | **推荐**。bodyType 是 `ApiRequest.data` 的类型。对 BizModel 方法用 `Map.class`。 |
| `input(file, type)` | `T` | 直接反序列化为指定类型。需要文件本身已经是 `ApiRequest` 完整结构时使用。 |

**常见错误**：`request("file.json5", ApiRequest.class)` 会生成 `ApiRequest<ApiRequest>` —— 这是错误的数据组织方式，永远不要这样用。对 BizModel 方法始终传 `Map.class`：

```java
// ✅ 正确
request("file.json5", Map.class)    // → ApiRequest<Map<String,Object>>

// ❌ 错误
request("file.json5", ApiRequest.class)   // → ApiRequest<ApiRequest>
```

#### 辅助方法参考

```java
ApiResponse<?> executeRpc(GraphQLOperationType opType, String action, ApiRequest<?> request) {
    IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(opType, action, request);
    return graphQLEngine.executeRpc(ctx);
}
```

### 返回值和数据提取

`IGraphQLEngine.executeRpc()` (同步) 和 `executeRpcAsync()` (异步) 都返回 `ApiResponse<?>`。`output("response.json5", result)` 接收完整 `ApiResponse`，框架序列化为 `{data: {...}, status: 0}`。

框架不要求也不建议手动从 `ApiResponse` 中提取字段值作为 Java 变量传递。见下文的多步测试模式。

### 多步测试模式：通过 `@var:` 自动传递数据

多步测试的核心模式：**每步通过 `request()` 读取输入（`bodyType = Map.class`），通过 `output()` 保存输出，步骤间数据流由框架的 `@var:` 变量机制自动连接**，无需手动提取 ID 或 token。

```java
@EnableSnapshot
@Test
public void testMultiStep() {
    setUser("0", "test");

    // Step 1: 创建 —— ORM 钩子自动注册 @var:LitemallAddress@id
    ApiResponse<?> r1 = executeRpc(GraphQLOperationType.mutation, "LitemallAddress__saveAddress",
            request("1_save_request.json5", Map.class));
    output("1_save_response.json5", r1);

    // Step 2: 删除 —— @var:LitemallAddress@id 由上一步自动提供
    executeRpc(GraphQLOperationType.mutation, "LitemallAddress__deleteAddress",
            request("2_delete_request.json5", Map.class));

    // Step 3: 查询并录制最终结果
    ApiResponse<?> r3 = executeRpc(GraphQLOperationType.query, "LitemallAddress__getMyAddresses",
            request("3_getMyAddresses_request.json5", Map.class));
    output("response.json5", r3);
}
```

对应的输入文件：

`1_save_request.json5`（初始创建，无 `@var:`）：
```json5
{ data: { data: { name: "张三", tel: "13800138000", province: "1", ... } } }
```

`2_delete_request.json5`（引用上一步的变量）：
```json5
{ data: { id: "@var:LitemallAddress@id" } }
```

`3_getMyAddresses_request.json5`（无参数查询）：
```json5
{ data: {} }
```

关于变量机制的完整说明（包括哪些 ORM 字段成为变量、外键变量自动传递、同名变量加后缀规则）见 `../02-core-guides/testing.md` 中的"自动测试变量机制"章节。

### 首次录制配置

```java
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
               snapshotTest = SnapshotTest.RECORDING)
```

录制完成后改回裸 `@NopTestConfig` 进入日常校验模式。

## 常见坑

1. `JunitAutoTestCase` 忘记 `@NopTestConfig`。
2. `@Inject private` 字段。
3. 快照数据放错目录。
4. 对快照测试手写大量重复断言。
5. `@Name("data")` 参数的请求误用扁平 `{name:...}` 而非 `{data: {name:...}}`——抛 `unknown-operation-arg`。
6. 首次录制时漏设 `initDatabaseSchema = OptionalBoolean.TRUE`，导致 `runLazyActions` 因空表失败。
7. 多步测试中手动从 `ApiResponse` 提取 ID 并作为 Java 变量传递——应使用 `request()` + `@var:` 机制自动传递，见上文"多步测试模式"和 `../02-core-guides/testing.md` 的"自动测试变量机制"章节。

## 相关文档

- `../02-core-guides/testing.md`
- `../04-reference/source-anchors.md`
