# 给字段换复杂自定义控件

> **Flux 时代注记（2026-08-24）**：本 runbook 的 gen-control 写法在 **AMIS 与 Flux 双模式通用**——gen-control 返回的 schema 会原样进入页面 JSON，由当前渲染引擎消费。因此：
>
> 1. **新增定制控件**：按本文写法没问题，但返回的 schema 必须是**当前渲染目标引擎的契约格式**——不仅是字段名（flux 模式下 picker 用 `valueKey`/`labelKey` 而非 AMIS 的 `valueField`/`labelField`），**组件类型名同样须换算**（如 AMIS `tree-select` ↔ flux `type:'tree'`），换算逻辑同型对照见 `../02-core-guides/flux-rendering.md §picker 字段级 schema 契约`。
> 2. **迁移期清理**：存量 view.xml 中"只返回 AMIS 风格 picker schema（source/joinValues/extractValue）且无 onEvent 等业务逻辑"的 `<gen-control>` 块属于迁移遗留——应**直接删除**而非改写。删除后 DefaultControl 的 mode 退化链会经 controlLib 自动产出正确 schema；逐个改写 gen-control 内容是在重复控件库已有的能力。注意退化链的前提：只有**显式声明了 `editMode="list-edit"` 的子表 grid 列**才退到 edit-to-one → edit-relation 产出可编辑 picker；默认 list-view 的只读列退 view-* 形态（完整退化链见 `../02-core-guides/frontend-rendering-pipeline.md §mode 退化链`）。
> 3. **判断标准**：gen-control 里只有控件形态声明（无跨字段联动/自定义校验）= 可删候选；含 `onEvent`、`validations`、业务专用 columns = 保留并转换字段名与组件名。

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

## 常见坑

1. 字段只是要换控件，却重写整份页面。
2. 复杂控件依赖上下文参数，但没把 `$id`、`$siteId`、`$ruleId` 之类参数传进去。
3. 该用外部片段或子表 view 的场景，硬塞到一个 `gen-control` 里。

## 相关文档

- `./add-child-table-editor-to-page.md`
- `../02-core-guides/page-dsl-pattern-catalog.md`
- `../02-core-guides/view-and-page-customization.md`
