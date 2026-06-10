# 单元测试 / 集成测试必读索引

> **用途：** 本文件是单元/集成测试任务的必读文档路由入口。Plan 中 `Required Pre-Reading` 引用本文件时，agent 必须逐个打开并通读下表中列出的每一个文档，不能用"已读本索引"替代阅读子文档。

## 全局必读（写任何测试代码之前必须全部读完）

> **本索引文件不包含任何规则内容。** 下表的"为什么必读"列只是说明用途，不是规则摘要。不读原文就会写错测试基类或遗漏录制回放机制。
>
> **阅读顺序：** 按表格从上到下逐个打开、通读全文。读完一篇才能读下一篇。

| 文档 | 为什么必读 | 不读会怎样 |
|------|-----------|-----------|
| `02-core-guides/testing.md` | 基类选择（`JunitAutoTestCase` / `JunitBaseTestCase`）、`@NopTestConfig` 能力矩阵、快照模式、异步防挂起规则 | **选错测试基类→容器不启动或录制回放不生效**；不用 IGraphQLEngine 而用纯 JUnit→BizModel 方法无法通过框架调用验证 |

## 按场景选读

> 以下文档按需阅读。判断依据：你接下来要写的代码涉及表格中描述的场景时，必须先读对应的文档再写代码。

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
