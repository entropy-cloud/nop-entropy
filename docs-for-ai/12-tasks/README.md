# 任务型开发手册（Runbook）

本目录提供 **按任务组织** 的最短路径指南，面向 AI/开发者在 Nop 平台上快速生成/修改实现。

## 使用规则（AI优先级）

1. **先模型/生成**：能通过 xmeta/xbiz/xview 等模型解决的，不写 Java。
2. **再 Delta**：能通过 `_delta` 覆盖/扩展的，不改原实现。
3. **最后才写 Java**：且优先复用 `CrudBizModel` 内置能力与扩展点。

## 目录

### 新建与配置

| 文档 | 任务场景 |
|------|---------|
| [create-new-entity.md](./create-new-entity.md) | 创建新实体（ORM → 生成 → BizModel） |
| [add-field-and-validation.md](./add-field-and-validation.md) | 新增字段与校验 |

### 业务逻辑

| 文档 | 任务场景 |
|------|---------|
| [write-bizmodel-method.md](./write-bizmodel-method.md) | 编写 BizModel 方法（@BizQuery/@BizMutation） |
| [extend-crud-with-hooks.md](./extend-crud-with-hooks.md) | 扩展 CRUD 钩子 |
| [custom-query-with-querybean.md](./custom-query-with-querybean.md) | 用 QueryBean 写自定义查询 |

### 扩展与定制

| 文档 | 任务场景 |
|------|---------|
| [extend-api-with-delta-bizloader.md](./extend-api-with-delta-bizloader.md) | 通过 Delta + BizLoader 扩展返回字段 |

### 基础设施

| 文档 | 任务场景 |
|------|---------|
| [transaction-boundaries.md](./transaction-boundaries.md) | 事务边界与回调 |
| [error-codes-and-nop-exception.md](./error-codes-and-nop-exception.md) | 错误码与 NopException |
| [write-unit-test.md](./write-unit-test.md) | 编写单元测试（nop-autotest） |

### 迁移指南

| 文档 | 任务场景 |
|------|---------|
| [ai-core-api-migration-guide.md](./ai-core-api-migration-guide.md) | AI Core API 迁移 |
