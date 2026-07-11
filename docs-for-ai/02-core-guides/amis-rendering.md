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
- `api-and-graphql.md` — GraphQL API 暴露方式
