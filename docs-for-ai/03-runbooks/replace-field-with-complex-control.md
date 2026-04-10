# 给字段换复杂自定义控件

## 适用场景

- 默认按 domain 推导出的控件不够用。
- 需要树选择、图标选择、条件编辑器、键值编辑器、代码编辑器、表格型控件。

## AI 决策提示

- 优先在 `cell` 或 `col` 上用 `gen-control` 覆盖默认控件。
- 先找当前仓库里已经存在的控件形态，不要凭空发明一套 DSL。
- 如果控件本质上是子集合编辑，优先考虑 `input-table` 或外部子表 view。

## 最小闭环

```xml
<cell id="parentId">
    <gen-control>
        <tree-select clearable="@:true">
            <source>
                <url>@query:Resource__findList/value:id,label:displayName,children @TreeChildren(max:5)</url>
            </source>
        </tree-select>
    </gen-control>
</cell>
```

## 高价值控件类型

1. `tree-select`
2. `button-group-select`
3. `vue-form-item` / `vue-renderer`
4. `editor`
5. `input-kv`
6. `condition-builder`
7. `input-table`

## 最值得抄的真实例子

1. `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthResource/NopAuthResource.view.xml`
   适合看：`button-group-select`、`tree-select`、`icon-picker`、列表 `vue-renderer`。
2. `nop-rule/nop-rule-web/src/main/resources/_vfs/nop/rule/pages/NopRuleNode/NopRuleNode.view.xml`
   适合看：`editor`、`input-kv`、`condition-builder`。
3. `nop-dyn/nop-dyn-web/src/main/resources/_vfs/nop/dyn/pages/NopDynEntityMeta/NopDynEntityMeta.view.xml`
   适合看：列表列用 `gen-control` 打开 schemaApi 对话框测试页面。
4. `C:/can/nop/nop-app-mall/app-mall-web/src/main/resources/_vfs/app/mall/pages/LitemallGoods/LitemallGoods.view.xml`
   适合看：`input-table`、外部 page 片段、子表编辑控件。

## 常见坑

1. 字段只是要换控件，却重写整份页面。
2. 复杂控件依赖上下文参数，但没把 `$id`、`$siteId`、`$ruleId` 之类参数传进去。
3. 该用外部片段或子表 view 的场景，硬塞到一个 `gen-control` 里。

## 相关文档

- `./add-child-table-editor-to-page.md`
- `../02-core-guides/page-dsl-pattern-catalog.md`
- `../02-core-guides/view-and-page-customization.md`
