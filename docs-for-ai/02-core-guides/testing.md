# 测试默认模式

本页只保留当前仓库里最适合 AI 使用的测试结论。

## 默认原则

1. 需要容器、数据库、配置、`_vfs` 时，优先 `@NopTestConfig`。
2. 需要快照录制/校验时，优先 `JunitAutoTestCase`。
3. 普通进程内集成测试，优先 `JunitBaseTestCase`。
4. `@Inject` 字段不能是 `private`。
5. 外部依赖优先使用测试专用 bean、stub 或 fake，而不是默认从 HTTP E2E 开始。

## 何时选哪种基类

| 场景 | 默认基类 |
|------|---------|
| 需要录制和校验 `_cases/` 快照 | `JunitAutoTestCase` |
| 不需要快照，只需要容器内测试 | `JunitBaseTestCase` |

## `@NopTestConfig` 的关键点

当前仓库里的 `@NopTestConfig` 至少控制这些能力：

- `localDb`
- `initDatabaseSchema`
- `enableConfig`
- `enableIoc`
- `snapshotTest`
- `forceSaveOutput`
- `testBeansFile`
- `testConfigFile`

## 快照测试的默认工作流

1. 首次录制：`snapshotTest = SnapshotTest.RECORDING`
2. 日常验证：默认 `CHECKING`
3. 只更新输出时：`forceSaveOutput = true`

## 测试数据位置

| 测试类型 | 数据位置 |
|---------|---------|
| `JunitAutoTestCase` 快照测试 | `_cases/...` |
| `JunitBaseTestCase` 普通资源 | `src/test/resources/...` |

## 当前仓库里的真实入口

| 能力 | 路径 |
|------|------|
| `JunitAutoTestCase` | `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitAutoTestCase.java` |
| `JunitBaseTestCase` | `nop-autotest/nop-autotest-junit/src/main/java/io/nop/autotest/junit/JunitBaseTestCase.java` |
| `@NopTestConfig` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/autotest/NopTestConfig.java` |

## 常见坑

1. `JunitAutoTestCase` 忘记加 `@NopTestConfig`。
2. `@Inject private` 导致注入失效。
3. 快照测试把数据放错目录。
4. 明明是进程内服务测试，却先去搭 HTTP E2E。

## 相关文档

- `../03-runbooks/write-tests.md`
- `../03-runbooks/write-integration-test-with-noptestconfig.md`
- `../03-runbooks/add-test-mock-bean.md`
- `../04-reference/source-anchors.md`
