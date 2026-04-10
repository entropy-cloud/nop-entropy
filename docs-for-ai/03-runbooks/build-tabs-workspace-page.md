# 构建 Tabs 工作台页面

## 适用场景

- 一个后台页需要组合“概览 + 关联列表 + 运行态”。
- 同一对象需要按状态拆成多个列表，但不想拆成多个菜单。

## AI 决策提示

- 优先把每个 tab 对应的子页先定义成 `simple` 或 `crud`。
- 再用 `<tabs>` 把它们拼起来。
- 如果多个 tab 只是主页面的变体，优先用 `x:prototype` 派生。

## 最小闭环

### 1. 先定义子页

```xml
<simple name="runtimeSummary" form="runtimeSummaryForm">
    <initApi url="@query:Job__get?id=$id" gql:selection="{@formSelection}"/>
</simple>

<crud name="runtimeTasks" x:prototype="view-list">
    <table>
        <api url="@query:Task__findPage/{@pageSelection}?filter_jobId=$jobId"/>
    </table>
</crud>
```

### 2. 再组装 tabs

```xml
<tabs name="runtimeTabs" tabsMode="vertical" mountOnEnter="true" unmountOnExit="true">
    <tab name="runtimeSummary" page="runtimeSummary" title="概览"/>
    <tab name="runtimeTasks" page="runtimeTasks" title="关联任务"/>
</tabs>
```

## 两种常见类型

1. `simple + crud` 工作台
   例如概览页加关联列表。
2. 多状态 CRUD tabs
   例如“全部 / 待审批 / 待退款”。

## 最值得抄的真实例子

1. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobSchedule/NopJobSchedule.view.xml`
   适合看 `runtimeSummary + runtimeFires + vertical tabs`。
2. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobFire/NopJobFire.view.xml`
   适合看 `runtimeSummary + runtimeTasks + row action 钻取`。
3. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallAftersale/LitemallAftersale.view.xml`
   适合看多状态 CRUD tabs。

## 常见坑

1. 还没把每个 tab 对应页面拆出来，就直接往 `<tabs>` 里堆大段配置。
2. 多个 tab 其实只是 filter 不同，却没有用 `x:prototype` 复用。
3. 忘了把父页面上下文传进 drawer/tab 工作台页面。

## 相关文档

- `../02-core-guides/page-dsl-pattern-catalog.md`
- `../02-core-guides/view-and-page-customization.md`
