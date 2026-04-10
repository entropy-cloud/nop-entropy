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

录制模式保存输出后会抛出一个表示快照录制完成的专用异常，这是框架预期行为。

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
