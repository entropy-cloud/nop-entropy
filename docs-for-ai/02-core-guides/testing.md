# 测试默认模式

> **受众**：基于 Nop 平台构建业务应用的开发者和 AI。本页描述的测试类（`JunitAutoTestCase`、`JunitBaseTestCase`、`@NopTestConfig`）通过 Maven 依赖引入，适用于任何 Nop 项目。源码路径列仅为 nop-entropy 仓库内部参考，业务项目中不存在这些路径。

本页只保留当前仓库里最适合 AI 使用的测试结论。

## 默认原则

1. 需要容器、数据库、配置、`_vfs` 时，优先 `@NopTestConfig`。
2. 需要快照录制/校验时，优先 `JunitAutoTestCase`。
3. 普通进程内集成测试，优先 `JunitBaseTestCase`。
4. `@Inject` 字段不能是 `private`。
5. 外部依赖优先使用测试专用 bean、stub 或 fake，而不是默认从 HTTP E2E 开始。
6. 不依赖 Nop 容器的纯逻辑测试，直接用 JUnit 5 `@Test`，无需 `@NopTestConfig`。

## 何时选哪种基类

| 场景 | 默认基类 |
|------|---------|
| 需要录制和校验 `_cases/` 快照 | `JunitAutoTestCase` |
| 不需要快照，只需要容器内测试 | `JunitBaseTestCase` |

## `@NopTestConfig` 的关键点

边界先记住：

1. `JunitAutoTestCase` 需要类级别 `@NopTestConfig`。
2. `JunitBaseTestCase` 不强制要求 `@NopTestConfig`；仓库里也存在不加该注解的普通测试。
3. 只有需要本地库、测试配置、测试 beans 或快照相关能力时再加。

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

实操里最常用的 helper：

1. `input(...)`
2. `request(...)`
3. `output(...)`
4. `outputText(...)`

录制模式下，框架在保存快照后会抛出一个表示“录制完成”的专用异常，这是正常流程，不要误判成普通业务失败。

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

## 异步与并发测试的防挂起规则

涉及 `CompletableFuture`、`BlockingQueue`、线程池、后台任务的测试必须遵守以下规则，防止测试因死锁、无限等待、或线程饥饿而永远不返回：

1. **类级别 `@Timeout`**：所有涉及异步操作的测试类加 `@Timeout(10)`（秒），防止单个测试无限阻塞。
2. **每个 `Future.get()` / `join()` 都必须带超时**：用 `future.get(5, TimeUnit.SECONDS)` 而非裸 `future.get()` 或 `future.join()`。超时后 test framework 可以 kill 线程。
3. **`BlockingQueue.take()` 不出现在测试主线程**：测试主线程调用 `take()` 会无限阻塞。用 `poll(timeout, unit)` 或确保生产者线程先 `close()` 再在主线程收集输出。
4. **Mock 长时间运行的命令用 `Thread.sleep()` + `AtomicBoolean` 检测中断**：不要用 `CountDownLatch.await(30s)`——cancel 后线程未必能走到 `countDown`。用 `Thread.sleep(largeValue)` + `catch InterruptedException` 设置标志位。
5. **等待后台线程启动用自旋 + 短 sleep**：`for (int i = 0; i < 100 && !started.get(); i++) Thread.sleep(10)` 而非 `latch.await(30s)`。
6. **`close()` 必须在 `collectOutput` 之前**：如果生产者写 `BlockingQueueShellOutput`，消费者必须等 `close()`（发送 EOF）后才能 `readAllText()`，否则永远阻塞。正确顺序：先关闭输出 → 再读取。

## 相关文档

- `../00-required-reading-testing.md`
- `../03-runbooks/write-tests.md`
- `../03-runbooks/write-integration-test-with-noptestconfig.md`
- `../03-runbooks/add-test-mock-bean.md`
- `../04-reference/source-anchors.md`
- `e2e-testing.md`（E2E 测试模式）
