# 模型设计必读索引

> **用途：** 本文件是模型设计任务的必读文档路由入口。Plan 中 `Required Pre-Reading` 引用本文件时，agent 必须逐个打开并通读下表中列出的每一个文档，不能用"已读本索引"替代阅读子文档。

## 全局必读（做任何模型设计变更之前必须全部读完）

> **本索引文件不包含任何规则内容。** 下表的"为什么必读"列只是说明用途，不是规则摘要。不读原文就会设计出违反平台规范的模型。
>
> **阅读顺序：** 按表格从上到下逐个打开、通读全文。读完一篇才能读下一篇。

| 文档 | 为什么必读 | 不读会怎样 |
|------|-----------|-----------|
| `02-core-guides/orm-model-design.md` | stdDataType/stdSqlType 分离、主键策略、字段设计、关系设计、字典设计 | **用错数据类型（如用 string 而非 integer 做主键）→代码生成结果与预期不符**；字典设计不规范→前端下拉框无选项 |
| `02-core-guides/model-first-development.md` | 模型优先开发流程、代码生成链路、VARCHAR precision 自动选择 | 跳过模型直接改生成物→下次重新生成被覆盖 |

## 按场景选读

> 以下文档按需阅读。判断依据：你接下来要写的代码涉及表格中描述的场景时，必须先读对应的文档再写代码。

### 命名规范

| 文档 | 场景 |
|------|------|
| `02-core-guides/code-style.md` | ORM 命名规范（表名、列名、实体名格式、dict name 格式） |

### 实体创建与字段管理

| 文档 | 场景 |
|------|------|
| `03-runbooks/create-new-entity.md` | 新建实体完整步骤 |
| `03-runbooks/add-field-and-validation.md` | 新增字段或校验 |
| `03-runbooks/add-dict-and-constants.md` | 新增字典 |
| `03-runbooks/change-model-and-regenerate.md` | 模型变更后重新生成 |

### 架构与领域

| 文档 | 场景 |
|------|------|
| `02-core-guides/domain-logic-and-ddd.md` | 领域逻辑落位、聚合根设计 |
| `02-core-guides/architecture-principles.md` | 跨切面架构原则 |
| `01-repo-map/domain-module-pattern.md` | 模块骨架定位 |

### 页面可见性

| 文档 | 场景 |
|------|------|
| `02-core-guides/view-and-page-customization.md` | 字段如何从模型落到页面（控件匹配链） |
| `03-runbooks/make-field-reach-page.md` | 字段从模型到页面的完整路径 |
