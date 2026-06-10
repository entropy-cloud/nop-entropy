# 后端服务开发必读索引

> **用途：** 本文件是后端任务的必读文档路由入口。Plan 中 `Required Pre-Reading` 引用本文件时，agent 必须逐个打开并通读下表中列出的每一个文档，不能用"已读本索引"替代阅读子文档。

## 全局必读（写任何后端代码之前必须全部读完）

> **本索引文件不包含任何规则内容。** 下表的"为什么必读"列只是说明用途，不是规则摘要。不读原文就会在运行时出错或违反平台规范。
>
> **阅读顺序：** 按表格从上到下逐个打开、通读全文。读完一篇才能读下一篇。

| 文档 | 为什么必读 | 不读会怎样 |
|------|-----------|-----------|
| **`05-examples/README.md`** | **先看示例再写代码。** Entity / IBiz / BizModel / DTO / 错误码 / 测试的精简代码骨架 | 不知道各类文件实际怎么写，凭猜测生成不符合约定的代码 |
| `02-core-guides/service-layer.md` | 示例之后的规则补充：I*Biz 接口契约、注解规则、开发顺序、跨实体访问、safe API、反模式表 | I*Biz 接口方法漏注解→运行时代理无法路由；@Inject 写成 private；用 dao() 而非 requireEntity |
| `02-core-guides/error-handling.md` | NopException + ErrorCode 规则 | throws RuntimeException；错误消息用中文；丢失异常链 |
| `04-reference/safe-api-reference.md` | requireEntity / doFindList / saveEntity / newEntity 等 safe API 速查 | 绕过 CrudBizModel 管道直接操作 dao() |

## 按场景选读

> 以下文档按需阅读。判断依据：你接下来要写的代码涉及表格中描述的场景时，必须先读对应的文档再写代码。

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
| **`03-runbooks/feature-implementation-checklist.md`** | **功能实现总流程：从需求到验证的端到端 checklist，串联所有 runbook** |
| `03-runbooks/write-bizmodel-method.md` | 写 BizModel 方法（含强制实现顺序：接口→实现→自检→测试） |
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
