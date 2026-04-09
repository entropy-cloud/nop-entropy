# View 与 Page 定制

当前仓库里的页面定制默认不是直接手写前端框架代码，而是围绕 `view.xml` 和 `page.yaml` 展开。

## 默认结论

1. 页面结构优先从 XView 的 `view.xml` 理解。
2. 生成物通常在 `_gen/` 下，日常定制优先改非下划线 `view.xml` / `page.yaml`。
3. 视图层改动仍然优先遵守 Delta 思路，而不是复制整份生成页面。
4. 影响字段显示、表单布局、按钮和 CRUD 页面结构时，优先改 XView，而不是先写 AMIS JSON。

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

## 什么时候改 `page.yaml`

优先改 `page.yaml` 的场景：

1. 页面级 `x:gen-extends` 或手工补充配置。
2. 需要改 page 级 title、body 包装、少量 AMIS 层参数。
3. 需要单独定制某个页面入口，而不是整个 view 模型。

如果需求本质上是列表列、表单字段、按钮结构或 CRUD 页面拼装，通常先改 `view.xml` 更对路。

## 常见坑

1. 直接改 `_gen/_Xxx.view.xml`。
2. 只是想隐藏几列，却复制整份生成 view。
3. 该改 `grid/form/page` 的地方没分清，结果把配置写散。
4. 表单字段来自 `objMeta` / XMeta，却只改 view 不回看模型或 meta。
5. 只记得 AMIS，忘了当前仓库的主入口仍然是 XView。

## 相关文档

- `../01-repo-map/where-things-live.md`
- `./model-first-development.md`
- `./delta-customization.md`
- `../03-runbooks/add-field-and-validation.md`
