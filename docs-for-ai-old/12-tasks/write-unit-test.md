# 编写单元测试（nop-autotest 录制回放）

## 适用场景

- 需要测试 BizModel 方法
- 需要自动录制输入输出，无需手写断言

## AI 决策提示

- ✅ 继承 `JunitAutoTestCase`，**必须使用 @NopTestConfig 注解**
- ✅ 首次运行用 `SnapshotTest.RECORDING` 录制，后续用 `SnapshotTest.CHECKING` 验证
- ✅ 无需手写 mock 和断言代码
- ✅ 测试数据放在 `_cases/` 目录下

## 必要 Import

```java
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.SnapshotTest;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
```

## 最小闭环

### 1. 创建测试类

```java
@NopTestConfig(
    localDb = true,                        // 使用 H2 内存数据库
    initDatabaseSchema = OptionalBoolean.TRUE  // 自动初始化表结构
)
public class OrderBizModelTest extends JunitAutoTestCase {

    @Inject
    OrderBizModel orderBizModel;  // 不能是 private

    @Test
    public void testCancel() {
        // 从 input/request.json5 读取请求数据
        ApiRequest<?> request = input("request.json5", ApiRequest.class);
        
        // 执行业务方法
        Object result = orderBizModel.cancel(
            request.getData().get("orderId").toString(),
            ServiceContextImpl.createTestContext());
        
        // 输出结果（录制时保存，验证时比对）
        output("response.json5", result);
    }
}
```

> **重要**：JunitAutoTestCase **必须**使用 @NopTestConfig 注解，否则会抛出异常。

### 2. 测试数据目录结构

#### JunitAutoTestCase（快照测试）

使用 `_cases/` 目录存储快照数据：

```
test/
├── java/
│   └── app/mall/service/biz/
│       └── OrderBizModelTest.java
└── _cases/
    └── app/mall/service/biz/OrderBizModelTest/
        └── testCancel/          # 测试方法名作为子目录
            ├── input/           # 输入数据
            │   ├── request.json5
            │   └── tables/      # 数据库初始化数据
            │       └── t_order.csv
            └── output/          # 预期输出（自动录制）
                └── response.json5
```

#### JunitBaseTestCase（普通测试）

可使用 `src/test/resources` 目录，通过 `attachmentXXX` 方法访问：

```
test/
├── java/
│   └── app/mall/service/
│       └── SimpleTest.java
└── resources/
    └── app/mall/service/
        ├── SimpleTest/
        │   └── test-data.json5   # 测试数据
        └── config.xml
```

```java
@NopTestConfig(localDb = true)
public class SimpleTest extends JunitBaseTestCase {
    @Test
    public void testLogic() {
        // attachmentXXX 方法读取测试类同目录下的文件
        String text = attachmentText("test-data.json5");
        XNode xml = attachmentXml("config.xml");
        MyBean bean = attachmentBean("data.json5", MyBean.class);
    }
}
```

### 3. 录制和验证模式

```java
// 录制模式（首次运行）
@NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE,
    snapshotTest = SnapshotTest.RECORDING  // 录制快照
)
public class OrderBizModelTest extends JunitAutoTestCase {
    // 运行后自动生成 _cases/ 目录下的数据
}

// 验证模式（正常运行）
@NopTestConfig(
    localDb = true,
    initDatabaseSchema = OptionalBoolean.TRUE
    // snapshotTest = SnapshotTest.CHECKING 是默认值
)
public class OrderBizModelTest extends JunitAutoTestCase {
    // 自动比对实际输出与录制的快照
}
```

### 4. 运行测试

```bash
# 录制模式：首次运行生成测试数据
mvn test -Dtest=OrderBizModelTest

# 验证模式：后续运行自动比对结果
mvn test -Dtest=OrderBizModelTest
```

## 初始化测试数据

### 方式 1：CSV 文件（数据库初始化）

```csv
# _cases/.../testCancel/input/tables/t_order.csv
ORDER_ID,USER_ID,ORDER_STATUS,ORDER_PRICE
order-001,user-001,101,100.00
```

### 方式 2：JSON5 文件（请求参数）

```json5
// _cases/.../testCancel/input/request.json5
{
  "data": {
    "orderId": "order-001"
  }
}
```

## 高级用法

### 强制更新输出快照

```java
@NopTestConfig(
    localDb = true,
    forceSaveOutput = true  // 使用录制的输入，只更新输出
)
public class OrderBizModelTest extends JunitAutoTestCase {
    // 只更新 output 目录，不重新录制 input
}
```

### 测试异常

```java
@Test
public void testCancelInvalidOrder() {
    // 使用 error() 方法验证预期异常
    error("response-error.json5", () -> {
        orderBizModel.cancel("invalid-id", context);
    });
}
```

## 简单测试使用 JunitBaseTestCase

如果不需要快照机制，可以使用 JunitBaseTestCase：

```java
@NopTestConfig(localDb = true)
public class SimpleTest extends JunitBaseTestCase {
    @Inject
    MyService myService;
    
    @Test
    public void testLogic() {
        // 简单的单元测试逻辑
    }
}
```

### BaseTestCase 提供的帮助方法

JunitBaseTestCase 继承自 `BaseTestCase`，提供以下数据读取方法：

| 方法 | 说明 |
|------|------|
| `attachmentText(name)` | 读取测试类同目录下的文本文件 |
| `attachmentBean(name, class)` | 读取测试类同目录下的 JSON 文件并反序列化 |
| `attachmentXml(name)` | 读取测试类同目录下的 XML 文件 |
| `attachmentBytes(name)` | 读取测试类同目录下的二进制文件 |
| `classpathResource(path)` | 从 classpath 读取资源 |
| `getTargetFile(subPath)` | 获取 target 目录下的文件 |
| `getTestResourceFile(name)` | 获取 src/test/resources 下的文件 |

## 常见坑

- ❌ JunitAutoTestCase 忘记 @NopTestConfig → 会抛出异常
- ❌ `@Inject private` → NopIoC 不支持 private 字段注入
- ❌ 手动断言 → 快照测试应该让 autotest 自动对比
- ❌ JunitAutoTestCase 数据放错目录 → 快照数据应放在 `_cases/` 目录
- ❌ JunitBaseTestCase 用 _cases 目录 → 普通测试应放在 `src/test/resources`，通过 `attachmentXXX` 访问

## 相关文档

- `11-test-and-debug/autotest-guide.md`
- `07-best-practices/testing.md`
