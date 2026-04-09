# 编写测试

## 适用场景

- 需要测试 BizModel、Processor、GraphQL 或其他容器内逻辑。
- 需要录制与校验快照。

## AI 决策提示

- 需要快照录制时，优先 `JunitAutoTestCase`。
- 不需要快照时，优先 `JunitBaseTestCase`。
- 需要容器、数据库、配置、`_vfs` 时，使用 `@NopTestConfig`。

## 最小闭环

### 1. 选择基类

| 场景 | 基类 |
|------|------|
| 快照录制 / 校验 | `JunitAutoTestCase` |
| 普通进程内测试 | `JunitBaseTestCase` |

### 2. 添加 `@NopTestConfig`

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

### 4. 数据目录

| 场景 | 位置 |
|------|------|
| 快照测试 | `_cases/...` |
| 普通资源文件 | `src/test/resources/...` |

## 常见坑

1. `JunitAutoTestCase` 忘记 `@NopTestConfig`。
2. `@Inject private` 字段。
3. 快照数据放错目录。
4. 对快照测试手写大量重复断言。

## 相关文档

- `../02-core-guides/testing.md`
- `../04-reference/source-anchors.md`
