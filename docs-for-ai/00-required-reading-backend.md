# 后端服务开发必读索引

> **用途：** 计划阶段和执行阶段的 `Required Pre-Reading` 入口。后端任务只需引用本文件，执行时按实际涉及的内容项选择阅读。

## 全局必读（任何后端任务都要读）

| 文档 | 为什么必读 |
|------|-----------|
| `00-start-here/application-project-defaults.md` | 决策顺序（Model→Delta→Java）、反模式表、自检清单 |
| `02-core-guides/service-layer.md` | CrudBizModel 模式、@BizQuery/@BizMutation、safe API |
| `02-core-guides/error-handling.md` | NopException + ErrorCode 规则、两层级策略 |
| `04-reference/safe-api-reference.md` | requireEntity / doFindList / save 等 safe API 速查 |

## 按场景选读

### 实体建模与代码生成

| 文档 | 场景 |
|------|------|
| `02-core-guides/orm-model-design.md` | **模型设计规范核心**：stdDataType/stdSqlType 分离、主键策略、字段设计、关系设计、字典设计 |
| `02-core-guides/model-first-development.md` | 新建/修改实体、ORM 模型、代码生成 |
| `03-runbooks/create-new-entity.md` | 新建实体完整步骤 |
| `03-runbooks/add-field-and-validation.md` | 新增字段或校验 |
| `03-runbooks/change-model-and-regenerate.md` | 模型变更后重新生成 |

### BizModel 方法与服务逻辑

| 文档 | 场景 |
|------|------|
| `03-runbooks/write-bizmodel-method.md` | 写 BizModel 方法的注解、参数、返回值规则 |
| `03-runbooks/extend-crud-with-hooks.md` | defaultPrepareSave/Update、afterEntityChange 等 hook |
| `03-runbooks/implement-complex-business-flow.md` | 多步编排流程 |
| `03-runbooks/choose-entity-bizmodel-processor.md` | 何时拆 Processor |
| `03-runbooks/custom-query-with-querybean.md` | QueryBean 自定义查询 |
| `03-runbooks/add-cross-module-biz-interface.md` | 跨模块调用 I*Biz 接口 |
| `03-runbooks/create-request-response-dto.md` | Request/Response DTO 写法 |

### Delta 定制

| 文档 | 场景 |
|------|------|
| `02-core-guides/delta-customization.md` | Delta 机制原理、合并规则、调试方法 |
| `03-runbooks/prefer-delta-over-direct-modification.md` | 何时用 Delta、常见操作、常见坑 |

### 认证与权限

| 文档 | 场景 |
|------|------|
| `02-core-guides/auth-and-permissions.md` | 认证机制、publicPaths/authPaths、角色与数据权限 |

### 事务与并发

| 文档 | 场景 |
|------|------|
| `02-core-guides/concurrency-and-transactions.md` | 事务边界、乐观锁、并发控制 |
| `03-runbooks/transaction-boundaries.md` | 事务边界处理 runbook |

### 测试

> 单元/集成测试有独立必读入口：**`00-required-reading-testing.md`**。E2E 测试入口：**`00-required-reading-e2e-testing.md`**。以下仅列出最常用的快速跳转。

| 文档 | 场景 |
|------|------|
| `02-core-guides/testing.md` | 测试模式与约定（快速参考） |
| `03-runbooks/write-tests.md` | 写测试用例 |
| `03-runbooks/write-integration-test-with-noptestconfig.md` | JunitAutoTestCase + @NopTestConfig 集成测试 |

### 错误码与异常

| 文档 | 场景 |
|------|------|
| `03-runbooks/error-codes-and-nop-exception.md` | 错误码定义与异常写法 runbook |

### 速查

| 文档 | 场景 |
|------|------|
| `04-reference/common-java-helpers.md` | CoreMetrics、StringHelper、JsonTool 等 helper 速查 |

> 计划阶段只引用本索引确定路径；执行阶段才实际阅读具体文档。流程由 plan guide 定义。
