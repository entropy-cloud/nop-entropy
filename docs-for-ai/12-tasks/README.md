# 任务型开发手册（Runbook）

本目录是 AI 在 Nop 平台上执行具体开发任务时的首选入口。

默认顺序：

1. 先看 `docs-for-ai/INDEX.md`
2. 再进入本目录命中的任务手册
3. 如果需要理解默认规范，再跳到 `03-development-guide/` / `04-core-components/`

---

## 使用规则

1. **先模型/生成**：能通过 ORM、XMeta、xbiz、页面模型解决的，不先写 Java。
2. **再 Delta**：能通过 `_delta` 覆盖/扩展的，不改基础实现。
3. **最后才写 Java**：普通实体型服务优先复用 `CrudBizModel` 默认能力。
4. **优先安全 API**：普通 BizModel 默认使用 `requireEntity()` / `doFindList()` / `doFindPage()`。

---

## 当前任务手册

### 建模与生成

| 文档 | 任务场景 |
|------|---------|
| [create-new-entity.md](./create-new-entity.md) | 创建新实体（ORM → 生成 → BizModel） |
| [add-field-and-validation.md](./add-field-and-validation.md) | 新增字段与校验 |
| [change-model-and-regenerate.md](./change-model-and-regenerate.md) | 修改模型后重新生成 |
| [debug-codegen-and-generated-files.md](./debug-codegen-and-generated-files.md) | 调试代码生成与生成文件 |

### 业务逻辑

| 文档 | 任务场景 |
|------|---------|
| [write-bizmodel-method.md](./write-bizmodel-method.md) | 编写 BizModel 方法（`@BizQuery` / `@BizMutation`） |
| [extend-crud-with-hooks.md](./extend-crud-with-hooks.md) | 扩展 CRUD 钩子 |
| [custom-query-with-querybean.md](./custom-query-with-querybean.md) | 使用 QueryBean 写自定义查询 |
| [choose-entity-vs-bizmodel-vs-processor.md](./choose-entity-vs-bizmodel-vs-processor.md) | 选择逻辑应该放在哪一层 |
| [add-cross-module-biz-interface.md](./add-cross-module-biz-interface.md) | 新增跨模块 Biz 接口 |
| [create-request-response-dto.md](./create-request-response-dto.md) | 创建 Request / Response DTO |
| [add-dict-and-constants.md](./add-dict-and-constants.md) | 新增字典和常量 |

### 扩展与定制

| 文档 | 任务场景 |
|------|---------|
| [extend-api-with-delta-bizloader.md](./extend-api-with-delta-bizloader.md) | 通过 Delta + BizLoader 扩展返回字段 |
| [prefer-delta-over-direct-modification.md](./prefer-delta-over-direct-modification.md) | 优先使用 Delta，而不是直接修改基础实现 |
| [add-bizloader-field.md](./add-bizloader-field.md) | 新增 BizLoader 字段 |

### 基础设施

| 文档 | 任务场景 |
|------|---------|
| [transaction-boundaries.md](./transaction-boundaries.md) | 事务边界与 afterCommit |
| [error-codes-and-nop-exception.md](./error-codes-and-nop-exception.md) | 错误码与 NopException |
| [write-unit-test.md](./write-unit-test.md) | 编写单元/集成测试 |
| [write-integration-test-with-noptestconfig.md](./write-integration-test-with-noptestconfig.md) | 使用 `@NopTestConfig` 编写集成测试 |
| [add-test-mock-bean.md](./add-test-mock-bean.md) | 在测试中补充 mock bean |

### 迁移

| 文档 | 任务场景 |
|------|---------|
| [ai-core-api-migration-guide.md](./ai-core-api-migration-guide.md) | AI Core API 迁移 |

---

## 相关文档

- `../INDEX.md`
- `../03-development-guide/bizmodel-guide.md`
- `../03-development-guide/project-structure.md`
- `../13-reference/source-anchors.md`
