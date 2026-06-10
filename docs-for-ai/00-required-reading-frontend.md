# 前端页面开发必读索引

> **用途：** 本文件是前端/页面任务的必读文档路由入口。Plan 中 `Required Pre-Reading` 引用本文件时，agent 必须逐个打开并通读下表中列出的每一个文档，不能用"已读本索引"替代阅读子文档。

## 全局必读（写任何前端/页面代码之前必须全部读完）

> **本索引文件不包含任何规则内容。** 下表的"为什么必读"列只是说明用途，不是规则摘要。不读原文就会破坏生成物或违反平台规范。
>
> **阅读顺序：** 按表格从上到下逐个打开、通读全文。读完一篇才能读下一篇。

| 文档 | 为什么必读 | 不读会怎样 |
|------|-----------|-----------|
| `00-start-here/application-project-defaults.md` | 决策顺序（Model→Delta→Java）、view.xml 三层模型 | 优先级搞反，手改生成物 |
| `02-core-guides/view-and-page-customization.md` | view.xml 三层模型、bounded-merge、x:prototype 模式、生成物与定制物关系 | **编辑生成物（_gen/ 目录）→下次重新生成被覆盖**；merge 策略选错→定制丢失或冲突 |

## 按场景选读

> 以下文档按需阅读。判断依据：你接下来要写的代码涉及表格中描述的场景时，必须先读对应的文档再写代码。

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
