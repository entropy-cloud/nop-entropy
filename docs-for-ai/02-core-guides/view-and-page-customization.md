# View 与 Page 定制

当前仓库里的页面定制默认不是直接手写前端框架代码，而是围绕 `view.xml` 和 `page.yaml` 展开。

如果你已经知道要改 `view.xml` / `page.yaml`，但需要查“复杂配置应该抄哪一种现成模式”，直接看 `./page-dsl-pattern-catalog.md`。如果你还不确定当前页面任务该走哪条文档路径，先看 `../03-runbooks/admin-page-development-roadmap.md`。

## 默认结论

1. 页面结构优先从 XView 的 `view.xml` 理解。
2. 生成物通常在 `_gen/` 下，日常定制优先改非下划线 `view.xml` / `page.yaml`。
3. 视图层改动仍然优先遵守 Delta 思路，而不是复制整份生成页面。
4. 影响字段显示、表单布局、按钮和 CRUD 页面结构时，优先改 XView，而不是先写 AMIS JSON。

## 页面生成机制总览

`page.yaml` **本身不是 AMIS 配置**，只是入口 wrapper。真正生成 AMIS JSON 的是 `web:GenPage` XPL 标签，**输入是 `view.xml` + `xmeta`，输出是 AMIS schema**。整个过程分两个阶段。

### 两阶段生成

| 阶段 | 时机 | 输入 | 输出 | 触发点 |
|------|------|------|------|--------|
| 构建时 codegen | `mvn install` 的 `precompile` 阶段 | `*.xmeta` | `_gen/_Xxx.view.xml`、`Xxx.view.xml`、`main.page.yaml` | `*-web/precompile/gen-page.xgen` |
| 运行时渲染 | 前端请求 `PageProvider__getPage` | `view.xml` + `xmeta` | AMIS JSON | `main.page.yaml` 的 `x:gen-extends` → `web:GenPage` |

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

### 运行时：从 view.xml + xmeta 渲染 AMIS

前端通过 GraphQL 请求 `GET /p/PageProvider__getPage?path=.../main.page.yaml`。后端调用链：

1. `PageProviderBizModel.getPage()` → `PageProvider.getPage()`
2. 加载 `main.page.yaml`，触发 `x:gen-extends` 执行 `web:GenPage`
3. `web:GenPage`（实现见 `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web/impl_GenPage.xpl`）加载 `view.xml` + `xmeta`，按 `pageModel.type` 分发：
   - `crud` → `page_crud.xpl`
   - `picker` → `page_picker.xpl`
   - `simple` → `page_simple.xpl`
   - `tabs` → `page_tabs.xpl`
4. 子模板读取 view 的 grid/form/page 配置 + objMeta 字段元数据，组装最终 AMIS JSON
5. `PageProvider` 再做 i18n 解析、`xui:permissions` → `xui:roles` 权限转换、清理空值

### 三层 Delta 架构

```
xmeta (实体元数据,源)
  ↓ [构建时 codegen]
_gen/_Xxx.view.xml  (自动生成的 view 基线,会被覆盖)
  ↓ x:extends
Xxx.view.xml        (保留层,手写定制)
  ↓ 运行时被 web:GenPage 读取
main.page.yaml      (入口 wrapper)
  ↓ x:gen-extends 触发
AMIS JSON           (运行时输出,可缓存)
```

### 关键事实

- **手写顺序**：优先改非下划线 `Xxx.view.xml`，**绝不**手改 `_gen/_*` 文件（下次 `mvn install` 会被覆盖）
- **`page.yaml` 通常不动**：只有在需要 page 级 `x:gen-extends`、自定义 title/body 包装、`fixedProps` 子表关联时才改（见 `./external-app-examples.md` 第 9 节）
- **复杂页面**（设计器、编辑器）：用 `x:gen-extends` 混合生成 + 大块手写 AMIS，见 `nop-wf/.../designer.page.yaml` 和 `../03-runbooks/build-designer-or-special-page.md`
- **`web:GenPage` 定义**：在 `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib`，具体 page 类型实现在同目录 `page_*.xpl`

## 当前仓库里的真实位置

| 你要找什么 | 典型位置 |
|-----------|---------|
| 保留层 view 文件 | `*-web/src/main/resources/_vfs/.../Xxx.view.xml` |
| 生成 view 文件 | `*-web/src/main/resources/_vfs/.../_gen/_Xxx.view.xml` |
| 页面文件 | `*-web/src/main/resources/_vfs/.../*.page.yaml` |
| XView 元模型 | `/nop/schema/xui/xview.xdef` |

可直接参考：

1. `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthUser/NopAuthUser.view.xml`
2. `nop-auth/nop-auth-web/src/main/resources/_vfs/nop/auth/pages/NopAuthUser/_gen/_NopAuthUser.view.xml`

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

AI 需要先记住三层：

1. `grid`：列表 / 表格。
2. `form`：新增 / 编辑 / 查询表单。
3. `page`：把 grid / form 组织成 `crud`、`simple`、`tabs`、`picker` 等页面。

## 默认修改路径

| 需求 | 默认修改位置 |
|------|-------------|
| 列表列顺序、显隐、标签 | `grid/cols` |
| 表单布局、只读、必填、子表 | `form/layout` 和 `form/cells` |
| 查询表单 | `form id="query"` |
| 列表按钮、行按钮 | `pages/crud/listActions`、`rowActions` |
| 页面初始化 API、跳转、弹窗 | `pages/*` 下对应 page 定义 |

## 常见结构

### Grid

```xml
<grid id="list">
    <cols x:override="bounded-merge">
        <col id="id"/>
        <col id="userName" label="用户名"/>
        <col id="status"/>
    </cols>
</grid>
```

### Form

```xml
<form id="edit" size="lg">
    <layout>
        userName[用户名] nickName[昵称]
        email[邮箱] status[状态]
    </layout>

    <cells>
        <cell id="userName" mandatory="true"/>
        <cell id="email" readonly="false"/>
    </cells>
</form>
```

### Form cells 与 objMeta 校验

View 绑定了 `objMeta` 后，`<layout>` 中出现的每个字段 ID 默认要求在 objMeta 的 props 中有定义。
如果字段不属于实体（如来自 DTO、前端计算字段、密码确认等），必须在 `<cells>` 中声明 `custom="true"` 跳过校验：

```xml
<form id="statsForm" editMode="view" title="索引统计">
    <cells>
        <!-- custom=true 表示此字段不需要在 objMeta 中定义 -->
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
3. `custom` 属性定义在 `<cell>` 节点上，属于 `disp.xdef` schema。

### 无 objMeta 的表单必须配置 domain

当表单没有 `objMeta`（既不在 view 上也不在 form 上配置）时，字段缺少类型信息，控件生成器无法确定 AMIS 控件类型，会回退到 `view-any` → `type: "static"`（只读文本）。

**必须在 `<cells>` 中为每个字段配置 `domain` 和 `label` 属性**：
- `domain` — 控件生成器才能匹配到正确的 AMIS 控件（`domain="string"` → `input-text`，`domain="int"` → `input-text` + `isInt` 校验等）
- `label` — 没有实体 `propMeta.displayName` 兜底，不配 `label` 则字段无标签

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

**控件匹配链**（`XuiHelper.getControlTag`）：`control` → `domain` → `stdDomain` → `stdDataType`，从 `control.xlib` 中查找 `{mode}-{type}` 标签。常见 domain → 控件映射：`string` → `input-text`、`int/long` → `input-text` + `isInt`、`double/decimal` → `input-number`、`enum` → `select`。

**注意**：`editMode="query"` 会自动给字段名添加 `filter_` 前缀（如 `indexId` → `filter_indexId`），如果 API 参数名不含前缀则不要用 `query` 模式，改用 `editMode="edit"`。

### BizModel 方法命名不得与标准 CRUD 重名

前端 `graphql.ts` 的 `operationRegistry` 注册了标准 CRUD 动作名（`get`、`findPage`、`save`、`update`、`delete` 等）。action 名按最后一个 `_` 分割取后缀。**如果后缀匹配到标准动作名，前端会使用预定义的参数签名，忽略自定义参数**。

完整的标准动作列表和参数签名见 `api-and-graphql.md` 的"前端 `operationRegistry` 标准动作签名"表。

后端 `GraphQLBizModel` 在注册时会检查同名方法，同优先级下直接抛 `ERR_GRAPHQL_DUPLICATE_ACTION`。自定义方法必须用不同的名字。

**常见使用场景**：

| 场景 | 示例 |
|------|------|
| 密码确认（前端专用） | `__password2` — `nop-auth/.../NopAuthUser.view.xml` |
| 导入文件开关（前端状态） | `__useImportFile` — `nop-rule/.../NopRuleDefinition.view.xml` |
| initApi 返回 DTO 数据 | `statsForm` 中的 `indexId`/`fileCount` — `nop-code/.../dashboard.view.xml` |

**约定**：前端专用字段以 `__`（双下划线）为前缀，配合 `notSubmit="true"` 防止提交到后台。

### Page

```xml
<crud name="main" grid="list" filterForm="query">
    <rowActions x:override="bounded-merge">
        <action id="row-update-button" actionType="drawer"/>
        <action id="row-delete-button"/>
    </rowActions>
</crud>
```

## 与生成物的关系

最常见模式是：

```xml
<view x:extends="_gen/_NopAuthUser.view.xml"
      x:schema="/nop/schema/xui/xview.xdef"
      xmlns:x="/nop/schema/xdsl.xdef">
    ...
</view>
```

含义：

1. `_gen/_Xxx.view.xml` 是生成基线。
2. `Xxx.view.xml` 是保留层定制文件。
3. 日常修改优先放在保留层文件，而不是 `_gen`。

## objMeta 与 bizObjName

- `<objMeta>` 指向 xmeta 文件的 VFS 路径，如 `/nop/auth/model/NopAuthUser/NopAuthUser.xmeta`。
- `bizObjName` 是业务对象名（如 `NopAuthUser`），用于自动推导 page URL、picker URL 等。
- 生成 view（`_gen/`）同时设置了 `objMeta` 和 `bizObjName`。
- **CRUD 页面**（`<crud>`）需要 objMeta，因为 `grid_crud.xpl` 模板访问 `objMeta.displayProp`。
- **Simple 页面**（`<simple>`）不强制要求 objMeta，没有时表单校验自动跳过实体属性检查。
- 如果 view 没有配置 `<objMeta>`，grid 和 form 的 validate 方法在 `objMeta == null` 时直接 return，不报错。
- 目前 objMeta 不会从 bizObjName 自动推导，需要在 view 中显式配置 `<objMeta>` 路径。

## 最常用的 Delta / override 写法

### 只保留部分列或按钮

```xml
<cols x:override="bounded-merge">
    <col id="id"/>
    <col id="name" label="名称"/>
</cols>
```

`bounded-merge`` 的直觉是：只保留你显式列出的这部分结构。

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

## 什么时候改 `page.yaml`

优先改 `page.yaml` 的场景：

1. 页面级 `x:gen-extends` 或手工补充配置。
2. 需要改 page 级 title、body 包装、少量 AMIS 层参数。
3. 需要单独定制某个页面入口，而不是整个 view 模型。

如果需求本质上是列表列、表单字段、按钮结构或 CRUD 页面拼装，通常先改 `view.xml` 更对路。

## 外部应用里的高价值复杂样例

以下模式在外部应用中常见。完整代码片段见 `./external-app-examples.md`，对应章节编号如下。

| 模式 | 详见 |
|------|------|
| `bounded-merge` 只保留指定列 | `external-app-examples.md` 第 1 节 |
| `x:prototype` 克隆 grid + 覆盖 filter | `external-app-examples.md` 第 2 节 |
| `x:prototype-override` 克隆 crud + 局部覆盖 action | `external-app-examples.md` 第 3 节 |
| `tabs` 多状态工作台组装 | `external-app-examples.md` 第 4 节 |
| `gen-control` 服务端动态控件 / input-table 内联子表 | `external-app-examples.md` 第 5-6 节 |
| 外部子表 view / page 片段引用 | `external-app-examples.md` 第 7-8 节 |
| 薄 `page.yaml` wrapper | `external-app-examples.md` 第 9 节 |
| Delta 覆盖平台页面 + `feature:on` 条件布局 | `external-app-examples.md` 第 10 节 |
| 树形 CRUD + `add-child` 预填父节点 | `external-app-examples.md` 第 11 节 |
| CRUD wiring 速记 | `external-app-examples.md` 第 12 节 |

## 常见坑

1. 直接改 `_gen/_Xxx.view.xml`。
2. 只是想隐藏几列，却复制整份生成 view。
3. 该改 `grid/form/page` 的地方没分清，结果把配置写散。
4. 表单字段来自 `objMeta` / XMeta，却只改 view 不回看模型或 meta。
5. 只记得 AMIS，忘了当前仓库的主入口仍然是 XView。
6. `<simple>` 页面缺少 `form` 属性导致启动报错 — 每个 `<simple>` 节点必须有 `form` 指向已定义的 form ID。
7. CRUD 页面引用了 `x:abstract="true"` 的 grid — abstract grid 是模板，CRUD 页面需要非 abstract 的 grid 或通过 objMeta 由代码生成器具体化。
8. form layout 里写了不属于 objMeta 的字段却没在 `<cells>` 中加 `custom="true"` — 报错 `cell-not-prop`。

## 相关文档

- `../01-repo-map/where-things-live.md`
- `./model-first-development.md`
- `./delta-customization.md`
- `./external-app-development.md`
- `./external-app-examples.md`
- `./page-dsl-pattern-catalog.md`
- `../03-runbooks/add-field-and-validation.md`
