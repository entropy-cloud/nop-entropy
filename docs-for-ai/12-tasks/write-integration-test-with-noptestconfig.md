# 使用 @NopTestConfig 编写集成测试

## 适用场景

- 需要注入 bean
- 需要访问数据库、配置或 `_vfs`
- 需要进程内测试 BizModel / GraphQL / 服务逻辑

## AI 决策提示

- ✅ 需要容器时，使用 `@NopTestConfig`
- ✅ 简单场景用 `JunitBaseTestCase`
- ✅ 需要快照录制用 `JunitAutoTestCase`
- ❌ 不要默认从 HTTP E2E 开始

## 最小闭环

```java
@NopTestConfig(localDb = true)
public class OrderBizModelTest extends JunitBaseTestCase {
    @Inject
    protected OrderBizModel orderBizModel;

    @Test
    public void testCancel() {
        Order order = orderBizModel.cancel("order-001", ServiceContextImpl.createTestContext());
        assertNotNull(order);
    }
}
```

## 何时选哪种基类

| 场景 | 基类 |
|------|------|
| 普通集成测试 | `JunitBaseTestCase` |
| 快照录制/校验 | `JunitAutoTestCase` |

## 常见坑

- ❌ 忘记 `@NopTestConfig`
- ❌ `@Inject private` 字段
- ❌ 明明是进程内服务测试，却先去搭 HTTP E2E |

## 相关文档

- `11-test-and-debug/autotest-guide.md`
- `12-tasks/write-unit-test.md`
