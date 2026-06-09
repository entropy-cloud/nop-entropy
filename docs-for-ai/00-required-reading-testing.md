# 单元测试 / 集成测试必读索引

> **用途：** 计划阶段和执行阶段的 `Required Pre-Reading` 入口。测试任务只需引用本文件，执行时按实际涉及的内容项选择阅读。

## 全局必读（任何单元/集成测试任务都要读）

| 文档 | 为什么必读 |
|------|-----------|
| `02-core-guides/testing.md` | 基类选择（`JunitAutoTestCase` / `JunitBaseTestCase`）、`@NopTestConfig` 能力矩阵、快照模式、异步防挂起规则 |

## 按场景选读

### 快照测试与集成测试

| 文档 | 场景 |
|------|------|
| `03-runbooks/write-tests.md` | 编写测试用例、录制与校验工作流 |
| `03-runbooks/write-integration-test-with-noptestconfig.md` | `@NopTestConfig` 集成测试、容器内测试配置 |

### Mock 与依赖隔离

| 文档 | 场景 |
|------|------|
| `03-runbooks/add-test-mock-bean.md` | 在测试中补 mock bean |

### 异步与并发测试

| 文档 | 场景 |
|------|------|
| `02-core-guides/testing.md` → 异步与并发测试防挂起规则 | `@Timeout`、`Future.get()` 超时、`BlockingQueue` 替代写法 |

> 计划阶段只引用本索引确定路径；执行阶段才实际阅读具体文档。流程由 plan guide 定义。
