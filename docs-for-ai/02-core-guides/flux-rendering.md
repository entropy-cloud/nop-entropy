# Flux 渲染管线 (Flux)

> **本文档内容专属 Flux 框架**，为 `nop-chaos-flux` 前端框架的渲染管线。
> 通用页面生成管线概念见 `frontend-rendering-pipeline.md`。

Flux 渲染管线（`flux-web.xlib` + `flux-control.xlib`）与 AMIS 渲染管线消费**完全相同的** `view.xml` 模型，输出 `nop-chaos-flux` 前端框架所需的 Flux JSON Schema。

## 使用方式

### page.yaml 切换渲染库

在 page.yaml 中将 `xpl:lib` 从 `web.xlib` 切换为 `flux-web.xlib`：

```xml
<!-- Flux 模式 -->
x:gen-extends: |
  <flux-web:GenPage view="Xxx.view.xml" page="main" xpl:lib="/nop/web/xlib/flux-web.xlib" />
```

### view.xml 指定 Flux 控件库

```xml
<controlLib>/nop/web/xlib/flux-control.xlib</controlLib>
```

## ORM 模型级启用

在 ORM 模型文件中通过扩展属性指定渲染器：

```xml
<entity name="NopAuthUser" ext:web-renderer="flux" ...>
```

代码生成模板会根据 `ext:web-renderer` 自动选择 `flux-control.xlib` / `flux-web.xlib` 或默认的 `control.xlib` / `web.xlib`。

也可以在 `<orm>` 根级别设置 `ext:web-renderer="flux"` 以启用模块全局的 Flux 渲染。

## NormalizeAction 的 onClick 优先规则

`flux-web.xlib:NormalizeAction` 实现了 AMIS actionType 到 Flux ActionSchema 的转换，且遵循 **onClick 优先规则**：

- 如果 action 中已有 `onClick`（Flux 原生 ActionSchema），直接透传，不做任何转换。
- 如果没有 `onClick`，则从 `api`/`actionType`/`dialog`/`drawer` 自动转换为 Flux ActionSchema。

这使得代码生成的页面走自动转换，手工定制的高级页面可以直接写 Flux 原生 `onClick`（DAG、重试、防抖等）。

## AMIS vs Flux 关键差异

| 维度 | AMIS (`web.xlib`) | Flux (`flux-web.xlib`) |
|------|-------------------|----------------------|
| 条件属性 | `visibleOn`/`disabledOn`/`staticOn` | `visible`/`disabled`（无 `staticOn`） |
| 容器 | `group`/`fieldSet`/`divider` | `flex`/`fieldset`/`separator` |
| 显示控件 | `static`/`static-mapping`/`static-image` | `text`/`mapping`/`image` |
| 富文本 | `input-rich-text`（HTML） | `markdown-editor`（Markdown） |
| 动作系统 | `actionType` 扁平结构 | `onClick` ActionSchema DAG |
| Cell 条件必填 | `requiredOn`（disp.xdef 子元素） | `required`（表达式字符串） |
| Cell 条件只读 | `readonlyOn`（disp.xdef 子元素） | `readOnly`（表达式字符串） |
| Cell 隐藏清空 | `clearValueOnHidden`（form.xdef 属性） | `hiddenFieldPolicy.clearValueWhenHidden`（嵌套对象） |
| Form 自动初始化 | `initFetch` | `autoInit` |
| Page aside 可调整大小 | `asideResizor` | `asideResizable` |

**layout `*` 必填修饰符、`@` 只读、`!` 隐藏标签、cell 的 `requiredOn`/`readonlyOn`/`clearValueOnHidden`、查询必填 `*` 在 Flux 中完全支持**。上表中的 AMIS → Flux 映射是**具体属性的具体命名差异**，不存在统一规律（`visibleOn` 删除 On 后缀、`clearValueOnHidden` 结构重组为嵌套对象、`asideResizor` 改为形容词形式）。

### Flux 已确认不支持的 form 属性

以下 `form.xdef` 属性在 Flux `FormSchema` 中确实无对应，配置在 view.xml 中会被 `FluxFormDefaultAttrs` 静默忽略（不输出到 Flux JSON）：

| AMIS 属性 | 含义 | Flux 状态 |
|-----------|------|-----------|
| `silentPolling` | 轮询时隐藏 loading | 不支持（AMIS 特有概念） |
| `wrapWithPanel` | 用 panel 包裹表单 | 不支持（AMIS 特有布局） |
| `canAccessSuperData` | 能否访问父级数据 | 不支持（Flux 用显式 ScopeRef） |

> 注：Flux 用 `data-source` + `initAction`/`loadAction` 处理数据流，AMIS 的轮询/loading 控制等概念不直接映射。Flux 用 flex/fieldset 布局组合，无 panel 包裹概念。

### Flux Page aside 支持

Flux `PageSchema` **完全支持** aside 相关属性（与 AMIS 命名差异：`asideResizor` → `asideResizable`）：

| Flux 属性 | AMIS 对应 | 说明 |
|-----------|-----------|------|
| `aside` | `<aside>` slot | 侧边栏内容（Flux 是 schema slot，AMIS 是 page 子元素） |
| `asidePosition` | （无 AMIS 对应） | 侧边栏位置：`left` / `right` |
| `asideResizable` | `asideResizor` | 可拖拽调整宽度（**命名差异**：AMIS 名词形式 → Flux 形容词形式） |
| `asideMinWidth` | `asideMinWidth` | 最小宽度（默认 200px） |
| `asideMaxWidth` | `asideMaxWidth` | 最大宽度（默认 600px） |
| `asideSticky` | `asideSticky` | 粘性定位（不随内容滚动） |
| `asideClassName` | `asideClassName` | 额外 CSS 类 |

> `FluxPageDefaultAttrs` 已 pick 上述 6 个可配置属性（含 `asideResizor → asideResizable` 命名映射）。`asidePosition` 因 `xview.xdef` UiPageModel 未定义，view.xml 模型层暂无配置入口。

## 相关文件

- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib` — Flux 页面生成库（37 个标签）
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-control.xlib` — Flux 控件映射库（75 个标签）

## 相关文档

- `frontend-rendering-pipeline.md` — 通用页面生成管线
- `view-and-page-customization.md` — 快速参考
- `amis-rendering.md` — AMIS 渲染管线 `(AMIS)`
- `../04-reference/source-anchors.md`（`EXT-008`、`EXT-009`）
