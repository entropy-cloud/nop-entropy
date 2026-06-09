# 模型设计必读索引

> **用途：** 计划阶段和执行阶段的 `Required Pre-Reading` 入口。模型设计任务只需引用本文件，执行时按实际涉及的内容项选择阅读。

## 全局必读（任何模型设计任务都要读）

| 文档 | 为什么必读 |
|------|-----------|
| `02-core-guides/orm-model-design.md` | stdDataType/stdSqlType 分离、主键策略、字段设计、关系设计、字典设计 |
| `02-core-guides/model-first-development.md` | 模型优先开发流程、代码生成链路、VARCHAR precision 自动选择 |

## 按场景选读

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

> 计划阶段只引用本索引确定路径；执行阶段才实际阅读具体文档。流程由 plan guide 定义。
