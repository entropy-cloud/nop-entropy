# 前端渲染管线（框架无关）

本文档描述 Nop 平台的页面生成管线概念，**与具体前端框架无关**。
当前有两个渲染实现：`AMIS`（默认）和 `Flux`（渐进替代），分别见 `amis-rendering.md` 和 `flux-rendering.md`。

> **如果你已经知道要改 `view.xml` / `page.yaml`**，快速参考见 `view-and-page-customization.md`。
> **如果你需要 form layout 分组/折叠/Tab/查询运算符等 DSL 语法**，见 `layout-syntax-reference.md`。
> **如果你需要复杂页面模式**，见 `page-dsl-pattern-catalog.md`。
> **如果不确定该走哪条文档路径**，先看 `../03-runbooks/admin-page-development-roadmap.md`。

## 两阶段生成

页面生成分两个阶段：

| 阶段 | 时机 | 输入 | 输出 | 触发点 |
|------|------|------|------|--------|
| 构建时 codegen | `mvn install` 的 `precompile` 阶段 | `*.xmeta` | `_gen/_Xxx.view.xml`、`Xxx.view.xml`、`main.page.yaml` | `*-web/precompile/gen-page.xgen` |
| 运行时渲染 | 前端请求 `PageProvider__getPage` | `view.xml` + `xmeta` | 框架 JSON（AMIS 或 Flux） | `main.page.yaml` 的 `x:gen-extends` → `GenPage` 标签 |

### 构建时：从 xmeta 生成 view/page 骨架

`*-web/precompile/gen-page.xgen`（如 `nop-auth/nop-auth-web/precompile/gen-page.xgen`）调用：

```js
codeGenerator.withTplDir('/nop/templates/orm-web').execute("/",{ moduleId: "nop/auth" },$scope);
```

模板位于 `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-web/`，从 `*.xmeta` 自动生成：

| 模板 | 生成产物 | 作用 |
|------|---------|------|
| `_gen/_{objName}.view.xml.xgen` | `_gen/_Xxx.view.xml` | view 基线，含 grid/form/crud/picker/simple 全套，从 xmeta 字段元数据自动推导 |
| `{objName}.view.xml.xgen` | `Xxx.view.xml` | 保留层，仅 `x:extends` 继承 _gen，留给手写定制 |
| `main.page.yaml.xgen` | `main.page.yaml` | 入口 wrapper |

生成的 `main.page.yaml` 极其简单，只有 1 行核心内容：

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthUser.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
```

### 运行时：从 view.xml + xmeta 渲染框架 JSON

前端通过 GraphQL 请求 `GET /p/PageProvider__getPage?path=.../main.page.yaml`。后端调用链：

1. `PageProviderBizModel.getPage()` → `PageProvider.getPage()`
2. 加载 `main.page.yaml`，触发 `x:gen-extends` 执行 `GenPage` 标签
3. `GenPage`（如 `web:GenPage` 或 `flux-web:GenPage`）加载 `view.xml` + `xmeta`，按 `pageModel.type` 分发：
   - `crud` → `page_crud.xpl`
   - `picker` → `page_picker.xpl`
   - `simple` → `page_simple.xpl`
   - `tabs` → `page_tabs.xpl`
4. 子模板读取 view 的 grid/form/page 配置 + objMeta 字段元数据，组装最终 JSON
5. `PageProvider` 再做 i18n 解析、`xui:permissions` → `xui:roles` 权限转换、清理空值

### 三层 Delta 架构

```
xmeta (实体元数据,源)
  ↓ [构建时 codegen]
_gen/_Xxx.view.xml  (自动生成的 view 基线,会被覆盖)
  ↓ x:extends
Xxx.view.xml        (保留层,手写定制)
  ↓ 运行时被 GenPage 读取
main.page.yaml      (入口 wrapper)
  ↓ x:gen-extends 触发
框架 JSON           (运行时输出,可缓存,AMIS 或 Flux)
```

### 关键事实

- **手写顺序**：优先改非下划线 `Xxx.view.xml`，**绝不**手改 `_gen/_*` 文件（下次 `mvn install` 会被覆盖）
- **`page.yaml` 通常不动**：只有在需要 page 级 `x:gen-extends`、自定义 title/body 包装、`fixedProps` 子表关联时才改
- **复杂页面**（设计器、编辑器）：用 `x:gen-extends` 混合生成 + 大块手写框架 JSON
- **框架选择**：默认使用 AMIS 渲染管线。切换到 Flux 见 `flux-rendering.md`

## XView 的最小理解模型

```xml
<view x:schema="/nop/schema/xui/xview.xdef"
      xmlns:x="/nop/schema/xdsl.xdef">
    <objMeta>/nop/auth/model/NopAuthUser/NopAuthUser.xmeta</objMeta>

    <grids>
        <grid id="list"/>
    </grids>

    <forms>
        <form id="edit"/>
    </forms>

    <pages>
        <crud name="main" grid="list"/>
    </pages>
</view>
```

需要先记住三层：

1. `grid`：列表 / 表格。
2. `form`：新增 / 编辑 / 查询表单。
3. `page`：把 grid / form 组织成 `crud`、`simple`、`tabs`、`picker` 等页面。

## 控件匹配链

`XuiHelper.getControlTag` 按以下优先级匹配控件标签：
`control` → `domain` → `stdDomain` → `stdDataType`

从控件库（`control.xlib` / `flux-control.xlib`）中查找 `{mode}-{type}` 标签。

常见 domain → 控件映射：`string` → `input-text`、`int/long` → `input-text` + `isInt`、`double/decimal` → `input-number`、`enum` → `select`。

## 最常用的 Delta / override 写法

### 只保留部分列或按钮

```xml
<cols x:override="bounded-merge">
    <col id="id"/>
    <col id="name" label="名称"/>
</cols>
```

`bounded-merge` 的直觉是：只保留你显式列出的这部分结构。

### 删除继承来的布局

```xml
<layout x:override="remove"/>
```

### 复用已有 form 结构

```xml
<form id="add" x:prototype="edit" editMode="add"/>
```

`x:prototype="edit"` 的含义是：在同一父节点的子节点中，找到 `id="edit"` 的兄弟节点，克隆它作为当前节点的模板，然后将当前节点的自定义内容合并到克隆结果上。

### prototype 变体的合并控制

`x:prototype` 克隆发生在 `x:extends` 继承合并**之后**。合并分为两个阶段：

1. **继承阶段**：`x:extends` 链合并，用 `x:override` 控制合并行为
2. **原型阶段**：`x:prototype` 从同层兄弟节点克隆，用 `x:prototype-override` 控制合并行为

需要两个属性是因为同一个节点会经历两次合并，可能在两个阶段需要不同的合并策略。

```xml
<!-- 从 "main" crud 克隆，但裁剪其按钮 -->
<crud name="wait-approve" x:prototype="main">
    <listActions x:prototype-override="remove"/>
    <rowActions x:prototype-override="bounded-merge">
        <action id="row-approve-button"/>
    </rowActions>
</crud>
```

## 无 objMeta 的表单配置

当表单没有 `objMeta` 时，字段缺少类型信息，控件生成器无法确定控件类型。

**必须在 `<cells>` 中为每个字段配置 `domain` 和 `label` 属性**：

```xml
<form id="queryForm" editMode="edit" title="类型层级查询">
    <layout>
        indexId[索引ID] qualifiedName[全限定类型名]
        direction[方向] maxDepth[最大深度]
    </layout>
    <cells>
        <cell id="indexId" label="索引ID" domain="string"/>
        <cell id="qualifiedName" label="全限定类型名" domain="string"/>
        <cell id="direction" label="方向" domain="string"/>
        <cell id="maxDepth" label="最大深度" domain="int"/>
    </cells>
</form>
```

**注意**：`editMode="query"` 会自动给字段名添加 `filter_` 前缀，如果 API 参数名不含前缀则不要用 `query` 模式，改用 `editMode="edit"`。

## BizModel 方法命名不得与标准 CRUD 重名

前端 `graphql.ts` 的 `operationRegistry` 注册了标准 CRUD 动作名（`get`、`findPage`、`save`、`update`、`delete` 等）。action 名按最后一个 `_` 分割取后缀。**如果后缀匹配到标准动作名，前端会使用预定义的参数签名，忽略自定义参数**。

完整的标准动作列表和参数签名见 `api-and-graphql.md` 的"前端 `operationRegistry` 标准动作签名"表。

## Form cells 与 objMeta 校验

View 绑定了 `objMeta` 后，`<layout>` 中出现的每个字段 ID 默认要求在 objMeta 的 props 中有定义。
如果字段不属于实体（如来自 DTO、前端计算字段、密码确认等），必须在 `<cells>` 中声明 `custom="true"` 跳过校验：

```xml
<form id="statsForm" editMode="view" title="索引统计">
    <cells>
        <cell id="indexId" custom="true"/>
        <cell id="fileCount" custom="true"/>
    </cells>
    <layout>
        indexId[索引ID] fileCount[文件数量]
    </layout>
</form>
```

**校验规则**（`UiFormModel.validate()`）：

1. 如果 view 没有配置 `<objMeta>`（且 form 也没有自己的 objMeta），**所有字段跳过实体属性校验**。
2. 如果有 objMeta，字段默认必须对应实体属性。标记 `custom="true"` 的 cell 除外。

## 前端专用字段约定

前端专用字段以 `__`（双下划线）为前缀，配合 `notSubmit="true"` 防止提交到后台。

| 场景 | 示例 |
|------|------|
| 密码确认（前端专用） | `__password2` |
| 导入文件开关（前端状态） | `__useImportFile` |
| initApi 返回 DTO 数据 | `statsForm` 中的 `indexId`/`fileCount` |

## objMeta 与 bizObjName

- `<objMeta>` 指向 xmeta 文件的 VFS 路径，如 `/nop/auth/model/NopAuthUser/NopAuthUser.xmeta`。
- `bizObjName` 是业务对象名（如 `NopAuthUser`），用于自动推导 page URL、picker URL 等。
- **CRUD 页面**（`<crud>`）需要 objMeta。
- 如果 view 没有配置 `<objMeta>`，grid 和 form 的 validate 方法在 `objMeta == null` 时直接 return，不报错。

## 什么时候改 `page.yaml`

优先改 `page.yaml` 的场景：

1. 页面级 `x:gen-extends` 或手工补充配置。
2. 需要改 page 级 title、body 包装、少量框架层参数。
3. 需要单独定制某个页面入口，而不是整个 view 模型。

如果需求本质上是列表列、表单字段、按钮结构或 CRUD 页面拼装，通常先改 `view.xml` 更对路。

## 常见坑

1. 直接改 `_gen/_Xxx.view.xml`。
2. 只是想隐藏几列，却复制整份生成 view。
3. 该改 `grid/form/page` 的地方没分清，结果把配置写散。
4. 表单字段来自 `objMeta` / XMeta，却只改 view 不回看模型或 meta。
5. 忘了当前仓库的主入口仍然是 XView，不是框架 JSON。
6. `<simple>` 页面缺少 `form` 属性导致启动报错 — 每个 `<simple>` 节点必须有 `form` 指向已定义的 form ID。
7. CRUD 页面引用了 `x:abstract="true"` 的 grid — abstract grid 是模板，CRUD 页面需要非 abstract 的 grid 或通过 objMeta 由代码生成器具体化。
8. form layout 里写了不属于 objMeta 的字段却没在 `<cells>` 中加 `custom="true"` — 报错 `cell-not-prop`。

## 相关文档

- `view-and-page-customization.md` — 快速参考
- `page-dsl-pattern-catalog.md` — DSL 模式目录
- `amis-rendering.md` — AMIS 渲染管线 `(AMIS)`
- `flux-rendering.md` — Flux 渲染管线 `(Flux)`
- `../01-repo-map/where-things-live.md`
- `../02-core-guides/model-first-development.md`
- `../02-core-guides/delta-customization.md`
- `../02-core-guides/external-app-development.md`
- `../02-core-guides/external-app-examples.md`
