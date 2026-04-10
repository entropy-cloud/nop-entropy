# 构建树形 CRUD 页面

## 适用场景

- 对象本身是树形结构，如菜单、分类、区域、规则节点。
- 需要树表展示、查看详情、编辑、以及“新增子节点”。

## AI 决策提示

- 优先基于已有 `_gen/_Xxx.view.xml` 扩展，而不是手写整页。
- 树形列表优先通过 `@TreeChildren` 返回 children。
- “新增子节点”优先做成 `simple name="add-child"`，并在 `<data>` 里回填父节点上下文。

## 最小闭环

### 1. 让列表返回树结构

```xml
<grid id="tree-list" x:prototype="list">
    <selection>children @TreeChildren(max:5)</selection>
</grid>
```

### 2. 用树表作为主页面 grid

```xml
<crud name="main" grid="tree-list">
    <table loadDataOnce="true" sortable="false" pager="none">
        <api url="@query:Region__findList?filter_pid=__null" gql:selection="{@listSelection}"/>
    </table>
</crud>
```

### 3. 增加 add-child 页面

```xml
<simple name="add-child" form="add">
    <api url="@mutation:Region__save/id" withFormData="true"/>
    <data>
        <_ j:key="pid">$id</_>
    </data>
</simple>
```

## 两个关键细节

1. 树页常见组合是 `loadDataOnce="true" + pager="none" + filter_pid=__null`。
2. 一旦 `simple/add-child` 自己声明了 `<data>`，外部上下文不会自动完整继承；需要的父节点字段要手工传入。

## 最值得抄的真实例子

1. `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthResource/NopAuthResource.view.xml`
   适合看 `@TreeChildren`、`loadDataOnce`、`row-add-child-button`、树形 `tree-select`。
2. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallRegion/_gen/_LitemallRegion.view.xml`
   适合看树形 CRUD 基线和 `add-child` 预填父节点。
3. `nop-rule/nop-rule-web/src/main/resources/_vfs/nop/rule/pages/NopRuleNode/NopRuleNode.view.xml`
   适合看 `add-child` 时如何显式回填 `parentId`、`ruleId` 等上下文。

## 常见坑

1. 只把 grid 改成树表，却忘了 `selection>children @TreeChildren(...)`。
2. `add-child` 页面手工设置了 `<data>`，却忘了把父上下文重新传进去。
3. 树形页还沿用普通分页表格设置，结果交互不对。

## 相关文档

- `../02-core-guides/view-and-page-customization.md`
- `../02-core-guides/page-dsl-pattern-catalog.md`
- `./add-field-and-validation.md`
