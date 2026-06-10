# 使用 `@NopTestConfig` 编写集成测试

## 适用场景

- 需要注入 bean。
- 需要访问数据库、配置或 `_vfs`。
- 需要进程内测试 BizModel、GraphQL 或服务逻辑。

## AI 决策提示

- 需要容器时，优先 `@NopTestConfig`。
- 普通集成测试优先 `JunitBaseTestCase`。
- 需要快照录制或校验时，优先 `JunitAutoTestCase`。
- 不要默认从 HTTP E2E 开始。
- `JunitAutoTestCase` 必须带类级别 `@NopTestConfig`；`JunitBaseTestCase` 只有在需要额外测试配置时再加。

## 最小闭环

> import 和代码骨架参见 `../05-examples/test-examples.java`。

### 1. 选择基类

| 场景 | 默认基类 |
|------|---------|
| 普通进程内集成测试 | `JunitBaseTestCase` |
| 快照录制 / 校验 | `JunitAutoTestCase` |

### 2. 添加测试配置

如果只是普通进程内测试，并不一定非要加 `@NopTestConfig`；但只要需要本地库、测试 beans、测试配置文件或快照能力，就应该显式加上。

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

### 3. 需要补外部依赖时再加测试 beans

如果测试启动时缺少外部依赖实现，继续看：

- `add-test-mock-bean.md`

### 4. 执行验证

优先运行对应模块测试，而不是整套 HTTP E2E。

## 常见坑

1. `JunitAutoTestCase` 忘记加 `@NopTestConfig`。
2. `@Inject private` 字段导致注入失败。
3. 明明是进程内服务测试，却先去搭 HTTP E2E。
4. 需要 mock bean 时去改生产 beans。

## 相关文档

- `./write-tests.md`
- `./add-test-mock-bean.md`
- `../02-core-guides/testing.md`
- `../04-reference/source-anchors.md`
