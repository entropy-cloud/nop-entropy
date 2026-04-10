# 后台页面开发路线图

本页不是讲某一种 DSL 语法，而是回答一个更实际的问题：

**当你要改一个后台页面时，应该先看哪篇手册，按哪条路径推进。**

## 最短判断法

先问自己是哪一类任务：

1. 只是让字段出现在页面上
2. 要把字段换成复杂控件
3. 要在父页面里编辑子表
4. 要加业务动作按钮
5. 要加树形页 / drawer 页 / tabs 工作台
6. 要做导出、批量操作、运行态页
7. 要覆盖平台内置页面

对照下面直接跳转。

## 任务到文档的最短路由

| 任务 | 首选手册 | 第二步常看 |
|------|---------|-----------|
| 字段从模型走到页面 | `make-field-reach-page.md` | `add-field-and-validation.md` |
| 字段改成复杂控件 | `replace-field-with-complex-control.md` | `page-dsl-pattern-catalog.md` |
| 父页面编辑子表 | `add-child-table-editor-to-page.md` | `build-related-drawer-page.md` |
| 页面加业务按钮 | `add-page-business-action.md` | `write-bizmodel-method.md` |
| 做树形后台页 | `build-tree-crud-page.md` | `page-dsl-pattern-catalog.md` |
| 做关联 drawer 页 | `build-related-drawer-page.md` | `add-runtime-or-related-page.md` |
| 做 tabs 工作台页 | `build-tabs-workspace-page.md` | `build-admin-workspace-page.md` |
| 做设计器/专用编辑器页 | `build-designer-or-special-page.md` | `xlang-and-xpl-basics.md` |
| 加导出或批量操作 | `add-export-or-batch-operations.md` | `add-page-business-action.md` |
| 加运行态页或关联页 | `add-runtime-or-related-page.md` | `build-tabs-workspace-page.md` |
| 做完整后台工作台 | `build-admin-workspace-page.md` | `page-dsl-pattern-catalog.md` |
| 覆盖平台内置页面 | `override-platform-page-with-delta.md` | `prefer-delta-over-direct-modification.md` |

## 推荐推进顺序

### 1. 普通字段/UI 改动

适用：

1. 新字段显示
2. 表单布局调整
3. 默认控件不够用

推荐顺序：

1. `make-field-reach-page.md`
2. `replace-field-with-complex-control.md`
3. `view-and-page-customization.md`

### 2. 子表与关联编辑

适用：

1. 父表编辑子表
2. 打开关联列表 drawer
3. 固定外键子页

推荐顺序：

1. `add-child-table-editor-to-page.md`
2. `build-related-drawer-page.md`
3. `add-runtime-or-related-page.md`

### 3. 页面动作与后台操作

适用：

1. 新增批准/拒绝/退款/执行按钮
2. 批量操作
3. 导入导出

推荐顺序：

1. `add-page-business-action.md`
2. `add-export-or-batch-operations.md`
3. `api-and-graphql.md`

### 4. 页面结构升级

适用：

1. 树形后台页
2. tabs 工作台
3. 运行态工作台
4. 专用编辑器页面

推荐顺序：

1. `build-tree-crud-page.md`
2. `build-tabs-workspace-page.md`
3. `build-admin-workspace-page.md`
4. `build-designer-or-special-page.md`

### 5. 平台页面覆盖

适用：

1. 改 `nop-auth`、`nop-sys`、`nop-job` 之类内置页面
2. 升级兼容很重要

推荐顺序：

1. `override-platform-page-with-delta.md`
2. `prefer-delta-over-direct-modification.md`
3. `delta-customization.md`

## 如果你还不知道该改哪层

先按这个顺序判断：

1. 字段/关联是否已经在 ORM / XMeta 存在
2. 页面只是缺展示，还是缺业务动作
3. 是普通 CRUD，还是树/关联/workspace/设计器场景
4. 是你自己的业务页，还是平台内置页

## 如果你只想抄真实例子

优先看这几个：

1. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/LitemallGoods.view.xml`
2. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallAftersale/LitemallAftersale.view.xml`
3. `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthResource/NopAuthResource.view.xml`
4. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobSchedule/NopJobSchedule.view.xml`
5. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobFire/NopJobFire.view.xml`
6. `nop-dyn/nop-dyn-web/src/main/resources/_vfs/nop/dyn/pages/NopDynModule/NopDynModule.view.xml`

## 常见误区

1. 还没判断任务类型，就在十几篇 runbook 里乱跳。
2. 页面问题一上来就读大段源码，而不是先走现成文档路线。
3. 明明只是“字段上屏”，却直接开始改复杂 page DSL。
4. 明明是平台页面覆盖，却去改原始产品页面。

## 相关文档

- `../02-core-guides/view-and-page-customization.md`
- `../02-core-guides/page-dsl-pattern-catalog.md`
- `../02-core-guides/external-app-development.md`
- `./README.md`
