# 给后台页增加导出或批量操作

## 适用场景

- 列表页需要“批量发布 / 批量删除 / 批量添加关联 / 批量导出”。
- 希望直接在页面上批量调用 BizModel 或 Provider 接口。

## AI 决策提示

- 批量修改优先用 `batch="true"` + `<data><ids>$ids</ids></data>`。
- 导出优先看 `actionType="download"`，常见走 `/p/...` 端点。
- 如果是“关联对象批量添加/删除”，通常会配合 picker dialog + reload。

## 最小闭环

### 1. 普通批量动作

```xml
<action id="publish-button" label="发布" batch="true">
    <api url="@mutation:Module__publish">
        <data>
            <ids>$ids</ids>
        </data>
    </api>
</action>
```

### 2. 导出动作

```xml
<action id="export-button" label="导出Excel" actionType="download" batch="true">
    <api url="/p/Module__exportExcel">
        <data>
            <ids>$ids</ids>
        </data>
    </api>
</action>
```

### 3. picker + batch add

```xml
<action id="batch-add-button" batch="true" close="select-items" reload="items-grid">
    <api url="@mutation:Role__addUsers">
        <data>
            <userIds>$ids</userIds>
        </data>
    </api>
</action>
```

## 最值得抄的真实例子

1. `nop-dyn/nop-dyn-web/src/main/resources/_vfs/nop/dyn/pages/NopDynModule/NopDynModule.view.xml`
   适合看：批量发布、导入、批量导出、单行导出、关联模块批量增删。
2. `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthUser/NopAuthUser.view.xml`
   适合看：picker + batch add + batch delete + reload。
3. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallAftersale/LitemallAftersale.view.xml`
   适合看：派生 tab 页上的 batch approve / batch reject。

## 常见坑

1. 忘了 `batch="true"`，结果按钮拿不到 `$ids`。
2. 导出还走普通 dialog/submit，而不是 `actionType="download"`。
3. picker 批量提交后忘了 `close` 和 `reload`。

## 相关文档

- `./add-page-business-action.md`
- `./build-related-drawer-page.md`
- `../02-core-guides/page-dsl-pattern-catalog.md`
