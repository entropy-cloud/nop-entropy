# 前端页面开发必读索引

> **用途：** 计划阶段和执行阶段的 `Required Pre-Reading` 入口。前端/页面任务只需引用本文件，执行时按实际涉及的内容项选择阅读。

## 全局必读（任何前端/页面任务都要读）

| 文档 | 为什么必读 |
|------|-----------|
| `00-start-here/application-project-defaults.md` | 决策顺序（Model→Delta→Java）、view.xml 三层模型 |
| `02-core-guides/view-and-page-customization.md` | view.xml 三层模型、bounded-merge、x:prototype 模式 |

## 按场景选读

### 后台管理页面

| 文档 | 场景 |
|------|------|
| `03-runbooks/admin-page-development-roadmap.md` | 后台页面开发路线图（必读入口） |
| `03-runbooks/make-field-reach-page.md` | 字段从模型落到页面的完整路径 |
| `03-runbooks/add-child-table-editor-to-page.md` | 子表编辑 |
| `03-runbooks/add-page-business-action.md` | 业务动作按钮 |
| `03-runbooks/add-export-or-batch-operations.md` | 导出/批量操作 |
| `03-runbooks/replace-field-with-complex-control.md` | 复杂自定义控件 |
| `03-runbooks/build-tree-crud-page.md` | 树形 CRUD 页面 |
| `03-runbooks/build-related-drawer-page.md` | 关联子表 Drawer 页面 |
| `03-runbooks/build-tabs-workspace-page.md` | Tabs 工作台页面 |
| `03-runbooks/build-admin-workspace-page.md` | 完整后台工作台页面 |
| `03-runbooks/build-designer-or-special-page.md` | 设计器/专用编辑器页面 |

### Delta 覆盖平台页面

| 文档 | 场景 |
|------|------|
| `02-core-guides/delta-customization.md` | Delta 机制原理 |
| `03-runbooks/override-platform-page-with-delta.md` | Delta 覆盖平台页面 |
| `03-runbooks/prefer-delta-over-direct-modification.md` | Delta vs 直接修改 |

### 认证与权限（页面可见性）

| 文档 | 场景 |
|------|------|
| `02-core-guides/auth-and-permissions.md` | action-auth.xml 结构、菜单资源生成、操作权限、数据权限、HTTP 认证 |

### 高级页面模式

| 文档 | 场景 |
|------|------|
| `02-core-guides/page-dsl-pattern-catalog.md` | 复杂页面 DSL 配置模式目录 |
| `03-runbooks/add-runtime-or-related-page.md` | 运行态或关联页 |
| `03-runbooks/add-bizloader-field.md` | BizLoader 字段 |
| `03-runbooks/extend-api-with-delta-bizloader.md` | Delta 扩展 API 返回字段 |

### index.html 扩展

| 文档 | 场景 |
|------|------|
| `02-core-guides/index-html-extensions.md` | 定制 index.html 扩展注入 |

> 计划阶段只引用本索引确定路径；执行阶段才实际阅读具体文档。流程由 plan guide 定义。
