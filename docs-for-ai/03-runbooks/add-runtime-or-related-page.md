# 给页面增加运行态或关联页

## 适用场景

- 一个对象需要增加“运行态概览”“关联任务”“关联触发记录”之类页面。
- 需要从列表 row action 钻取到运行态工作台或关联列表页。

## AI 决策提示

- 先把运行态页或关联页定义成独立 `simple` / `crud`。
- 再从 row action 打开它们。
- 关联列表页如果只是固定外键过滤，优先用 `ref-*.page.yaml` 薄 wrapper。

## 最小闭环

### 1. 定义运行态子页

```xml
<simple name="runtimeSummary" form="runtimeSummaryForm">
    <initApi url="@query:Job__get?id=$id" gql:selection="{@formSelection}"/>
</simple>
```

### 2. 定义关联列表页

```xml
<crud name="runtimeTasks" x:prototype="view-list">
    <table>
        <api url="@query:Task__findPage/{@pageSelection}?filter_jobId=$jobId"/>
    </table>
</crud>
```

### 3. 从 row action 打开

```xml
<action id="runtime-summary-button" actionType="drawer">
    <dialog page="runtimeTabs" size="xl">
        <data>
            <id>$id</id>
            <jobId>$jobId</jobId>
        </data>
    </dialog>
</action>
```

## 最值得抄的真实例子

1. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobSchedule/NopJobSchedule.view.xml`
   适合看：运行态概览、关联 fires、row action 钻取。
2. `nop-job/nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobFire/NopJobFire.view.xml`
   适合看：运行态概览、关联 tasks、跨对象 viewSchedule/viewTasks。
3. `nop-sys/nop-sys-web/src/main/resources/_vfs/nop/sys/pages/NopSysDict/NopSysDict.view.xml`
   适合看：最简关联 drawer drill-down。

## 常见坑

1. 还没定义子页，就先在 row action 里拼一堆大配置。
2. 关联列表其实只差一个固定外键，但没有抽成 `ref-*.page.yaml`。
3. 忘了把父页上下文传给运行态页或关联页。

## 相关文档

- `./build-tabs-workspace-page.md`
- `./build-related-drawer-page.md`
- `../02-core-guides/page-dsl-pattern-catalog.md`
