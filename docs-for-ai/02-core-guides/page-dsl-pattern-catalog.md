# 页面 DSL 模式目录

本页不是再讲一遍 `view.xml` / `page.yaml` 基础，而是给出：

**当你已经知道要改页面 DSL，但需要找“当前仓库里真实存在的复杂配置模式”时，应该优先抄哪一类结构。**

## 先记住

1. 普通 CRUD 改动先看 `view-and-page-customization.md`。
2. 需要复杂页面拼装时，再来本页找现成模式。
3. 优先复用已有 DSL 模式，不要一上来就手搓大段 AMIS JSON。

## 高价值模式总表

| 模式 | 解决什么问题 | 最佳参考 |
|------|-------------|---------|
| `bounded-merge` keep-only override | 只保留你显式列出的继承子节点 | `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/LitemallGoods.view.xml` |
| `x:prototype` 复用 | 基于一个 grid / form / page 派生多个变体 | `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallAftersale/LitemallAftersale.view.xml` |
| tabs 组装多个子页 | 把多个 CRUD / simple 页面组装成一个工作台 | `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobSchedule/NopJobSchedule.view.xml` |
| 外部子表 view / page 片段复用 | 在父表单内嵌子表编辑，而不复制子表 DSL | `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/LitemallGoods.view.xml` |
| 薄 `page.yaml` wrapper | 从已有 view/page 快速生成独立入口页 | `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/add.page.yaml` |
| 树形 CRUD + add-child | 树表、子节点创建、层级回填 | `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthResource/NopAuthResource.view.xml` |
| `feature:on` 条件布局切换 | 同一页面按 feature flag 切两套布局 | `C:/can/nop/nop-app-mall/app-mall-delta/src/main/resources/_vfs/_delta/default/nop/auth/pages/NopAuthUser/NopAuthUser.view.xml` |
| 动作直连 `@query` / `@mutation` | 在表格动作上直接挂业务 API | `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobSchedule/NopJobSchedule.view.xml` |
| `gen-control` 自定义控件 | 缺省控件不够用时，服务端生成控件 | `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthResource/NopAuthResource.view.xml` |
| `initApi` / `api` / `gql:selection` / `withFormData` | 统一 CRUD 页面取数、初始化、提交 wiring | `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallRegion/_gen/_LitemallRegion.view.xml` |
| `fixedProps` 关联子表页 | 把某个外键固定为上层传入值 | `nop-rule/nop-rule-web/src/main/resources/_vfs/nop/rule/pages/NopRuleNode/ref-ruleDefinition.page.yaml` |
| 混合生成页 + 大块手写 page | 专用设计器、编辑器一类页面 | `nop-wf/nop-wf-web/src/main/resources/_vfs/nop/wf/designer/designer.page.yaml` |
| `custom="true"` 非实体字段 | 表单字段不在 objMeta 中时跳过校验 | `nop-auth/.../NopAuthUser.view.xml`（`__password2`）、`nop-rule/.../NopRuleDefinition.view.xml`（`__useImportFile`） |

## 1. `bounded-merge`

适合：

1. 继承了一个很大的 grid / actions 配置，但你只想保留其中一小部分。
2. 不想复制整份父配置。

关键点：

1. `x:override="bounded-merge"` 常用于 `cols`、`rowActions`。
2. `x:prototype-override="bounded-merge"` 常用于基于 prototype 派生出的 page/action 变体。
3. 没有显式重写的继承子节点会被删掉。

参考：

1. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/LitemallGoods.view.xml`
2. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallAftersale/LitemallAftersale.view.xml`

## 2. `x:prototype` 复用

适合：

1. 一个基础 grid / form / crud 页面要派生多个变体。
2. 变体之间只有 filter、action、layout 的局部差异。

当前仓库里的典型写法：

1. `grid id="wait-approve-list" x:prototype="list"`
2. `crud name="wait-approve" x:prototype="main"`
3. `form id="add" x:prototype="edit"`

参考：

1. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallAftersale/LitemallAftersale.view.xml`
2. `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthResource/NopAuthResource.view.xml`
3. `nop-rule/nop-rule-web/src/main/resources/_vfs/nop/rule/pages/NopRuleNode/NopRuleNode.view.xml`

## 3. tabs 组装多个子页

适合：

1. 一个对象需要“概览 + 关联列表 + 运行态”的组合工作台。
2. 一个后台页要按状态拆成多个列表，但不想拆成多个独立菜单。

参考：

1. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallAftersale/LitemallAftersale.view.xml`
2. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobSchedule/NopJobSchedule.view.xml`

前者是“多状态 CRUD tabs”，后者是“summary + related CRUD tabs”。

## 4. 嵌套子表编辑与外部片段复用

适合：

1. 父对象编辑页里要编辑多个子表。
2. 不想把子表 DSL 全部内联到当前页面。

当前仓库里有两种高频写法：

1. 引外部子表 view：`<view path="...Xxx.view.xml" grid="ref-edit"/>`
2. 引外部 page 片段：`<view path="...attributes.page.yaml"/>`

参考：

1. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/LitemallGoods.view.xml`

## 5. 树形 CRUD / add-child

适合：

1. 菜单、分类、区域、规则树一类层级对象。
2. 需要“查看树 + 选树 + 新增子节点”。

关键点：

1. `selection>children @TreeChildren(max:5)`
2. `loadDataOnce="true"`
3. `simple name="add-child"` 里预填父节点字段

参考：

1. `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthResource/NopAuthResource.view.xml`
2. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallRegion/_gen/_LitemallRegion.view.xml`

补充：

`nop-rule/nop-rule-web/src/main/resources/_vfs/nop/rule/pages/NopRuleNode/NopRuleNode.view.xml` 还展示了一个高价值细节：一旦 `<data>` 被显式设置，就不会自动继承外部上下文，因此需要手工把 `parentId`、`ruleId` 等上下文重新传进去。

## 6. 动作直连业务 API

适合：

1. 在 row action / list action 上直接调用 BizModel 方法。
2. 不想额外写前端 controller 或中间转发层。

常见组合：

1. `@mutation:*` + `<data>`
2. `confirmText`
3. `visibleOn`
4. `batch="true"`
5. `actionType="drawer"` / `dialog`

参考：

1. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobSchedule/NopJobSchedule.view.xml`
2. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallAftersale/LitemallAftersale.view.xml`
3. `nop-sys/nop-sys-web/src/main/resources/_vfs/nop/sys/pages/NopSysDict/NopSysDict.view.xml`

## 7. `gen-control` 自定义控件

适合：

1. 默认 domain 推导出来的控件不够用。
2. 控件结构需要依赖服务端上下文、prop meta 或复杂 schema。

典型例子：

1. `button-group-select`
2. `tree-select`
3. `vue-form-item`
4. `editor`
5. `input-kv`
6. `condition-builder`

参考：

1. `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthResource/NopAuthResource.view.xml`
2. `nop-rule/nop-rule-web/src/main/resources/_vfs/nop/rule/pages/NopRuleNode/NopRuleNode.view.xml`
3. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/LitemallGoods.view.xml`

## 8. 薄 `page.yaml` wrapper 与 `fixedProps`

适合：

1. 已有 `view.xml` 很完整，只缺一个页面入口。
2. 要把某个 CRUD 变成“关联子表页面”，固定外键。

参考：

1. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/add.page.yaml`
2. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallBrand/main.page.yaml`
3. `nop-rule/nop-rule-web/src/main/resources/_vfs/nop/rule/pages/NopRuleNode/ref-ruleDefinition.page.yaml`

其中 `ref-ruleDefinition.page.yaml` 明确展示了：`fixedProps="ruleId"` 会把关联字段固定为上层传入值，并把该字段的编辑控件转为只读上下文字段。

## 9. 条件布局与 feature 开关

适合：

1. 外部应用要覆盖平台页面。
2. 同一个页面要按 feature flag 切不同布局，而不想拆成两份菜单。

参考：

1. `C:/can/nop/nop-app-mall/app-mall-delta/src/main/resources/_vfs/_delta/default/nop/auth/pages/NopAuthUser/NopAuthUser.view.xml`

这个例子用两份同 id 的 form 配合 `feature:on` 切换布局。

## 10. 混合生成页 + 大块手写 page

适合：

1. 工作流设计器、流程图编辑器、大型专用页面。
2. 页面主体不是普通 CRUD，而是高度定制组件。

参考：

1. `nop-wf/nop-wf-web/src/main/resources/_vfs/nop/wf/designer/designer.page.yaml`

这个例子同时展示了：

1. `x:gen-extends`
2. 手写 toolbar actions
3. 大块内嵌 schema
4. 专用 `nop-flow-editor` 组件

## 11. CRUD wiring 速记

当前仓库里最常见的 wiring 组合：

1. 列表取数：`<api url="@query:...__findPage" gql:selection="{@pageSelection}"/>`
2. 表单初始化：`<initApi url="@query:...__get?id=$id" gql:selection="{@formSelection}"/>`
3. 表单提交：`<api url="@mutation:...__update/id?id=$id" withFormData="true"/>`

最适合看的基线：

1. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallRegion/_gen/_LitemallRegion.view.xml`
2. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobSchedule/NopJobSchedule.view.xml`

## 什么时候不要来这里找答案

1. 只是普通字段增删改，先回 `view-and-page-customization.md`。
2. 还没确认模型/XMeta 是否已经具备目标字段，先回模型和 meta 文档。
3. 只是想知道 REST contract，先看 `api-and-graphql.md`。

## 相关文档

- `./view-and-page-customization.md`
- `./external-app-development.md`
- `./api-and-graphql.md`
- `../04-reference/source-anchors.md`
