# AMIS 渲染管线 (AMIS)

> **本文档内容专属 AMIS 框架**，随着 Flux 逐步替代将被删除。
> 通用页面生成管线概念见 `frontend-rendering-pipeline.md`。

## AMIS 渲染入口

默认的 AMIS 渲染管线使用 `web.xlib` + `control.xlib`：

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthUser.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
```

## AMIS 特有的 `@query:` API URL 机制

AMIS 默认使用 `@query:` / `@mutation:` 前缀来调用 GraphQL API：

| 前缀 | 对应 GraphQL 请求 |
|------|-------------------|
| `@query:` | Query 操作 |
| `@mutation:` | Mutation 操作 |

示例：

```xml
<api url="@query:NopAuthUser__findPage" gql:selection="{@pageSelection}"/>
```

详见 `api-and-graphql.md`。

## AMIS actionType 系统

AMIS 使用扁平的 `actionType` 结构（`drawer`、`dialog`、`ajax`、`link` 等）：

```xml
<action id="row-update-button" actionType="drawer"/>
<action id="row-delete-button" actionType="ajax"/>
```

Flux 侧对应的转换规则见 `flux-rendering.md` 的 NormalizeAction 说明。

## AMIS 关键差异（vs Flux）

| 维度 | AMIS (`web.xlib`) | Flux (`flux-web.xlib`) |
|------|-------------------|----------------------|
| 条件属性 | `visibleOn`/`disabledOn`/`staticOn` | `visible`/`disabled` |
| 容器 | `group`/`fieldSet`/`divider` | `flex`/`fieldset`/`separator` |
| 显示控件 | `static`/`static-mapping`/`static-image` | `text`/`mapping`/`image` |
| 富文本 | `input-rich-text`（HTML） | `markdown-editor`（Markdown） |
| 动作系统 | `actionType` 扁平结构 | `onClick` ActionSchema DAG |

完整对照表见 `flux-rendering.md`。

## 相关文件

- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib` — AMIS 页面生成库
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/control.xlib` — AMIS 控件映射库
- `nop-frontend-support/nop-web/src/main/java/.../impl_GenPage.xpl` — GenPage 实现

## 相关文档

- `frontend-rendering-pipeline.md` — 通用页面生成管线
- `view-and-page-customization.md` — 快速参考
- `flux-rendering.md` — Flux 渲染管线 `(Flux)`

## 变量引用语法规范

> **`$xxx` 简写已废弃**，所有 AMIS 上下文中的变量引用统一使用 `${xxx}` 表达式语法。

| 语法 | 状态 | 说明 |
|------|------|------|
| `${xxx}` | ✅ 推荐 | amis-formula 表达式，支持过滤器、函数、运算符 |
| `$xxx` | ❌ 废弃 | amis 遗留简写，仅支持纯变量查找，不支持过滤器/函数/运算符 |

**适用范围**：`url`、`source`、`data` 子元素、`labelTpl`、`tpl`、`visibleOn`/`disabledOn`/`requiredOn` 等 AMIS JSON 属性。

### 在 view XML / XPL 模板中的转义

view XML（`*.view.xml`）和 XPL 模板（`*.xlib`、`*.xpl`）中 `${expr}` 是 XLang 表达式求值。要输出字面量 `${xxx}` 给 AMIS 前端运行时，必须用 `${'$'}{xxx}` 转义：

| 上下文 | 写法 | 原理 |
|--------|------|------|
| view XML 元素文本/属性 | `${'$'}{xxx}` | `${'$'}` 求值得 `$`，`{xxx}` 是纯文本 |
| XPL JSON 表达式 | `'$' + '{xxx}'` | 字符串拼接避免 `${}` 触发求值 |
| 纯 JSON 文件（fixture） | `${xxx}` | 无 XPL 求值，直接写 |

**view XML 正确写法**：

```xml
<!-- url 插值：${'$'}{id} 输出字面量 ${id} 给 AMIS -->
<api url="@query:Foo__findPage?id=${'$'}{id}"/>

<!-- data 绑定 -->
<data>
    <id>${'$'}{id}</id>
</data>

<!-- 条件表达式（visibleOn 内容不经过 XPL 求值，直接写） -->
<visibleOn>${status == 1}</visibleOn>
```

**XPL 模板正确写法**：

```xml
<!-- xlib JSON 表达式模式 -->
url: "/f/download/" + '$' + "{fileId}"

<!-- xpl XML 属性 -->
<labelTpl>${'$'}{${labelProp}}</labelTpl>
```

> **注意**：`$$` 不是合法的 XPL 转义。`$${xxx}` 实际被解析为 `$` 字面量 + `${xxx}` 求值，产出 AMIS 简写 `$value`，而非字面量 `${xxx}`。
```
- `api-and-graphql.md` — GraphQL API 暴露方式
