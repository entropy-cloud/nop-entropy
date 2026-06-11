# 测试默认模式

> **受众**：基于 Nop 平台构建业务应用的开发者和 AI。本页描述的测试类（`JunitAutoTestCase`、`JunitBaseTestCase`、`@NopTestConfig`）通过 Maven 依赖引入，适用于任何 Nop 项目。源码路径列仅为 nop-entropy 仓库内部参考，业务项目中不存在这些路径。

本页只保留当前仓库里最适合 AI 使用的测试结论。

## 默认原则

1. 具体用法和 import 参见 `../05-examples/test-examples.java`，本文只补充规则和约束。
2. 需要容器、数据库、配置、`_vfs` 时，优先 `@NopTestConfig`。
3. 需要快照录制/校验时，优先 `JunitAutoTestCase`。
4. 普通进程内集成测试，优先 `JunitBaseTestCase`。
5. 纯逻辑测试（无 DB 无 IoC），用 `BaseTestCase` + `CoreInitialization.initialize()/destroy()`。
6. `@Inject` 字段不能是 `private`。
7. 外部依赖优先使用测试专用 bean、stub 或 fake，而不是默认从 HTTP E2E 开始。

## 何时选哪种基类

| 场景 | 默认基类 |
|------|---------|
| 纯逻辑，无 DB 无 IoC | `BaseTestCase` + `CoreInitialization` |
| 需要容器+DB，不需要快照 | `JunitBaseTestCase` |
| 需要录制和校验 `_cases/` 快照 | `JunitAutoTestCase` |

## `@NopTestConfig` 的关键点

边界先记住：

1. `JunitAutoTestCase` 需要类级别 `@NopTestConfig`。
2. `JunitBaseTestCase` 不强制要求 `@NopTestConfig`；仓库里也存在不加该注解的普通测试。
3. 只有需要本地库、测试配置、测试 beans 或快照相关能力时再加。

## `@NopTestConfig` 的关键点

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `localDb` | `true` | 强制使用 H2 内存/文件数据库。CHECKING 模式下始终为 true。 |
| `initDatabaseSchema` | `NOT_SET` | 不从注解控制；依赖 `application.yaml` 中的 `nop.orm.init-database-schema` 或其他配置源。**RECORDING 模式也不会自动触发 schema 初始化。** |
| `snapshotTest` | `CHECKING` | 校验模式。首次录制时设为 `RECORDING`。 |
| `forceSaveOutput` | `false` | 仅更新输出不校验。 |

**重要：** `initDatabaseSchema` 默认是 `NOT_SET`（不是 `true`）。从空 H2 文件库首次录制时，必须显式设为 `OptionalBoolean.TRUE`。已有的 H2 文件库（`db/test.mv.db`）如果 schema 已存在则不需要此设置。

**分场景推荐配置：**

| 场景 | 配置 | 说明 |
|------|------|------|
| **首次录制**（从空库） | `@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE, snapshotTest = SnapshotTest.RECORDING)` | schema 初始化 + 录制快照 |
| **首次录制**（已有库） | `@NopTestConfig(localDb = true, snapshotTest = SnapshotTest.RECORDING)` | 不设 `initDatabaseSchema`，schema 已存在 |
| **后续校验**（日常 CI） | 裸 `@NopTestConfig` | CHECKING 模式自动从 `_cases/` 加载快照数据 |
| **更新输出** | `@NopTestConfig(forceSaveOutput = true)` | 不校验，仅更新输出文件。更新后改回默认 |

> **为什么裸 `@NopTestConfig` 在日常校验时也能工作？** CHECKING 模式下，框架从 `_cases/` 加载录制时的数据库快照（CSV 文件）恢复到 H2 内存库，不需要 schema 初始化。

## `@EnableSnapshot` 方法级快照控制

`@EnableSnapshot` 是方法级注解（`@Target(ElementType.METHOD)`），用于在 CHECKING 模式下对单个测试方法进行细粒度快照控制。

### 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `localDb` | `true` | 强制使用 H2 内存数据库 |
| `sqlInput` | `true` | 是否自动执行 input 目录下的 SQL 文件 |
| `sqlInit` | `true` | 是否执行 SQL 初始化脚本 |
| `tableInit` | `true` | 是否将 `input/tables/` 目录下的 CSV 数据插入数据库 |
| `saveOutput` | `false` | 是否保存输出（录制模式开关） |
| `checkOutput` | `true` | 是否校验输出与录制结果匹配 |

### 核心机制（源码：`JunitAutoTestCase.configExecutionMode`）

1. 框架先检查 `disable` 标志：全局配置 `nop.autotest.disable-snapshot=true` 或类级 `snapshotTest == RECORDING` 时 `disable=true`。
2. `disable=false` 且方法标注了 `@EnableSnapshot` → 使用 `@EnableSnapshot` 的参数（方法级控制）。
3. 否则 → 回退到类级 `@NopTestConfig.snapshotTest()` 决定的行为。

**关键约束：类级 RECORDING 模式下 `@EnableSnapshot` 被完全忽略。** 所有方法强制走 RECORDING 逻辑，无法用 `@EnableSnapshot` 让某个方法跳过录制。

### 不加 vs 加

| 情况 | 行为 |
|------|------|
| 不加 `@EnableSnapshot`，类为 CHECKING（默认） | 走 CHECKING 分支：`checkOutput=true, saveOutput=false` — 加载快照并校验 |
| 不加 `@EnableSnapshot`，类为 RECORDING | 走 RECORDING 分支：`saveOutput=true, checkOutput=false` — 所有方法录制 |
| 加裸 `@EnableSnapshot`，类为 CHECKING | 行为与不加相同（默认值一致），仅用于多步测试的惯例标记 |
| 加 `@EnableSnapshot(saveOutput=true)`，类为 CHECKING | **仅此方法重新录制**，执行后抛 `snapshot-finished`，其他方法仍为 CHECKING |

### 全局 vs 单方法录制控制

| 目标 | 做法 |
|------|------|
| 全部重新录制 | `@NopTestConfig(snapshotTest = SnapshotTest.RECORDING)` |
| 全部仅更新输出 | `@NopTestConfig(forceSaveOutput = true)` |
| 单个方法重新录制 | 类保持裸 `@NopTestConfig`，目标方法加 `@EnableSnapshot(saveOutput = true)` |
| 单个方法跳过校验 | `@EnableSnapshot(checkOutput = false)` |
| 全局禁用快照 | 设置 `nop.autotest.disable-snapshot=true` 或 `@NopTestConfig(snapshotTest = SnapshotTest.NOT_USE)` |

### 单方法重新录制示例

```java
@NopTestConfig  // 默认 CHECKING
public class TestOrder extends JunitAutoTestCase {
    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testQuery() {
        // 普通校验模式
    }

    @EnableSnapshot(saveOutput = true)  // 仅此方法重新录制
    @Test
    public void testCreate() {
        ApiResponse<?> result = executeRpc(GraphQLOperationType.mutation,
            "LitemallOrder__createOrder", request("request.json5", Map.class));
        output("response.json5", result);
    }
}
```

录制完成后去掉 `saveOutput = true`（或去掉整个 `@EnableSnapshot`），该方法恢复为 CHECKING 模式。

## 快照测试的默认工作流

1. 首次录制：`snapshotTest = SnapshotTest.RECORDING`
2. 日常验证：默认 `CHECKING`
3. 只更新输出时：`forceSaveOutput = true`
4. 单方法重新录制：`@EnableSnapshot(saveOutput = true)`

实操里最常用的 helper：

1. `input(...)`
2. `request(...)`
3. `output(...)`
4. `outputText(...)`

录制模式下，每个测试方法执行完毕后框架会抛出错误码为 `nop.err.autotest.snapshot-finished` 的异常表示录制完成。这是**预期行为**，不是测试失败。Maven 输出会显示 `Tests run: X, Errors: X`（Errors 数等于录制的方法数），看到此异常和 Errors 计数请忽略。切换到 CHECKING 模式后 Errors 会归零。

## 自动测试变量（`@var:`）机制

### 哪些字段会成为变量

快照测试录制过程中，ORM 实体的某些字段值会被自动收集为变量。判断逻辑在 `AutoTestHelper.isVarCol()`：

| 条件 | 对应 ORM 配置 | 变量名示例 |
|------|-------------|-----------|
| 主键的 `tagSet` 包含 `seq` 或 `seq-default` | `<column code="ID" tagSet="seq" .../>` | `LitemallAddress@id` |
| 字段的 `tagSet` 包含 `var` | `<column code="OPEN_ID" tagSet="var" .../>` | `NopAuthUser@openId` |
| 字段的 `tagSet` 包含 `clock` | `<column code="LOGIN_TIME" tagSet="clock" .../>` | `NopAuthSession@loginTime` |
| 字段是实体的 `createTimeProp` | `entity createTimeProp="addTime"` | `LitemallAddress@addTime` |
| 字段是实体的 `updateTimeProp` | `entity updateTimeProp="updateTime"` | `LitemallAddress@updateTime` |

变量名格式固定为：`{实体ShortName}@{字段名}`，例如 `LitemallAddress@id`、`NopAuthUser@openId`。

### 外键变量自动传递

如果 A 表的外键列引用了 B 表的主键，且 B 表主键是变量列（`tagSet="seq"`），则 A 表输出 CSV 中该外键列的值会被替换为 B 表主键的 `@var:` 引用。开发者**无需**在外键列上重复标注 `tagSet`。

示例：`nop_auth_session` 表的 `userId` 列引用 `nop_auth_user` 表的 `userId`（`tagSet="seq"`），则录制后的 CSV 中：
```
_chgType,SESSION_ID,USER_ID,...
A,@var:NopAuthSession@sessionId,@var:NopAuthUser@userId,...
```

此逻辑由 `TagVarCollector.addRefVars()` 实现：遍历实体上的 `to-one` 关系，对每个关联条件，如果右表主键是 var 列，就将左表外键的变量名前缀注册为右表主键的变量名前缀。

### 变量生命周期

**录制阶段**（`saveOutput=true`）：

1. **ORM 拦截器收集**：`AutoTestOrmHook` 拦截 `postSave`/`postUpdate`/`postLoad` 事件，对每个实体的变量列调用 `AutoTestVars.addVar(name, value)`。
2. **同名处理**：若同一变量名出现多次（如连续创建两条地址），`addVar` 自动追加数字后缀：`LitemallAddress@id` → `LitemallAddress@id_1` → `LitemallAddress@id_2`。
3. **输出 JSON 变量替换**：`output("response.json5", result)` 保存响应时，`AutoTestVars.replaceValueByVarName()` 遍历 JSON 中的每个值，如果值匹配某个已知变量，则替换为 `@var:变量名`。
4. **输出 CSV 变量替换**：保存表格数据时，`TagVarCollector.replaceVars()` 对每行每列的值做相同替换。

**校验阶段**（`checkOutput=true`）：

1. **输入变量解析**：`input()`/`request()` 读取文件时，`AutoTestVars.resolveVarName()` 将 `@var:变量名` 替换回录制时的实际值。
2. **输出比对**：`output()` 将当前结果与录制的 JSON 比对，表格数据与 CSV 比对，变量引用会被解析回实际值后进行比较。

### 多步测试模式：无需手动提取 ID

每个 GraphQL 步骤通过 `request()` 读取输入（含 `@var:` 引用），通过 `output()` 保存输出（框架自动注入 `@var:` 引用），步骤之间的数据流由变量机制自动连接：

```java
@EnableSnapshot
@Test
public void testMultiStep() {
    setUser("0", "test");

    // Step 1: 创建实体 —— 响应被录制，ORM 钩子自动注册 @var:LitemallAddress@id
    ApiResponse<?> r1 = executeRpc("LitemallAddress__saveAddress",
            request("1_save_request.json5", ApiRequest.class));
    output("1_save_response.json5", r1);

    // Step 2: 删除实体 —— @var:LitemallAddress@id 由上一步自动提供
    executeRpc("LitemallAddress__deleteAddress",
            request("2_delete_request.json5", ApiRequest.class));

    // Step 3: 查询并录制最终结果
    ApiResponse<?> r3 = executeRpc("LitemallAddress__getMyAddresses",
            request("3_getMyAddresses_request.json5", ApiRequest.class));
    output("response.json5", r3);
}
```

对应的输入文件：

`1_save_request.json5`（初始创建，无 `@var:` 引用）：
```json5
{ data: { data: { name: "张三", tel: "13800138000", ... } } }
```

`2_delete_request.json5`（引用步骤 1 生成的变量）：
```json5
{ data: { id: "@var:LitemallAddress@id" } }
```

`3_getMyAddresses_request.json5`（无参数查询）：
```json5
{ data: {} }
```

**关键规则**：
- `request()` 读取输入文件时会自动解析 `@var:` 引用，变量值必须已经被前面的步骤注册过。
- `executeRpc()` 返回的 `ApiResponse<?>` 不经手动提取直接传给 `output()`，框架负责变量替换。
- 不要手动解析响应中的 ID 值并作为 Java 变量传递（如 `extractId(result)`），这会绕过变量机制，导致录制结果不正确。
- 对于只执行但不需要录制响应的步骤（如返回 void 的删除操作），可以省略 `output()` 调用。

## 测试数据位置

| 测试类型 | 数据位置 |
|---------|---------|
| `JunitAutoTestCase` 快照测试 | `_cases/...` |
| `JunitBaseTestCase` 普通资源 | `src/test/resources/...` |

## 常见坑

1. `JunitAutoTestCase` 忘记加 `@NopTestConfig`。
2. `@Inject private` 导致注入失效。
3. 快照测试把数据放错目录。
4. 明明是进程内服务测试，却先去搭 HTTP E2E。
5. **首次录制以为 RECORDING 模式会自动初始化 schema** — 不会。需显式加 `initDatabaseSchema = OptionalBoolean.TRUE`。
6. **把 `saveEntity(entity, actionName, context)` 的 `actionName` 当成 `boolean` 传** — `actionName` 是 `String`，传 `null` 使用默认值即可。

## 异步与并发测试的防挂起规则

涉及 `CompletableFuture`、`BlockingQueue`、线程池、后台任务的测试必须遵守以下规则，防止测试因死锁、无限等待、或线程饥饿而永远不返回：

1. **类级别 `@Timeout`**：所有涉及异步操作的测试类加 `@Timeout(10)`（秒），防止单个测试无限阻塞。
2. **每个 `Future.get()` / `join()` 都必须带超时**：用 `future.get(5, TimeUnit.SECONDS)` 而非裸 `future.get()` 或 `future.join()`。超时后 test framework 可以 kill 线程。
3. **`BlockingQueue.take()` 不出现在测试主线程**：测试主线程调用 `take()` 会无限阻塞。用 `poll(timeout, unit)` 或确保生产者线程先 `close()` 再在主线程收集输出。
4. **Mock 长时间运行的命令用 `Thread.sleep()` + `AtomicBoolean` 检测中断**：不要用 `CountDownLatch.await(30s)`——cancel 后线程未必能走到 `countDown`。用 `Thread.sleep(largeValue)` + `catch InterruptedException` 设置标志位。
5. **等待后台线程启动用自旋 + 短 sleep**：`for (int i = 0; i < 100 && !started.get(); i++) Thread.sleep(10)` 而非 `latch.await(30s)`。
6. **`close()` 必须在 `collectOutput` 之前**：如果生产者写 `BlockingQueueShellOutput`，消费者必须等 `close()`（发送 EOF）后才能 `readAllText()`，否则永远阻塞。正确顺序：先关闭输出 → 再读取。

## 相关文档

- `../05-examples/test-examples.java`（示例代码，先看这个）
- `../00-required-reading-testing.md`
- `../03-runbooks/write-tests.md`
- `../03-runbooks/write-integration-test-with-noptestconfig.md`
- `../03-runbooks/add-test-mock-bean.md`
- `e2e-testing.md`（E2E 测试模式）
