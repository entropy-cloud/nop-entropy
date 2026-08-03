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

### view.xml 指定 Flux 控件库（通常不需要手动设置）

```xml
<controlLib>/nop/web/xlib/flux-control.xlib</controlLib>
```

> **大多数情况下不需要手动设置这一行**。当 `nop.web.render-mode=flux` 时，view 模型加载管线中的 `x:post-extends`（`view-gen.xlib:DefaultViewPostExtends`）会自动将 view.xml 中的 `<controlLib>` 从 `control.xlib` 替换为 `flux-control.xlib`。详见下文「自动切换机制」。

## 自动切换机制

### 切换触发点

当后端配置 `nop.web.render-mode=flux`（通过 `-Dnop.web.render-mode=flux` 或 application.yaml）时，系统通过两层 XDSL 元编程完成从 AMIS 到 Flux 的切换，**无需修改任何 view.xml 或 page.yaml**：

1. **xlib 级别的 post-extends**（`web.xlib` → `web/impl_flux_mode.xpl`）：在 `web.xlib` 编译期，检测到 `renderMode == 'flux'` 后，用 `x:override="replace"` 将 `GenPage`、`GenForm`、`GenGrid` 等标签的实现替换为 `flux-web.xlib` 版本。

2. **view 模型级别的 post-extends**（`view-gen.xlib:DefaultViewPostExtends`）：在 XDSL view 模型加载期，检测到 `renderMode == 'flux'` 后，通过 `_dsl_root.childByTag('controlLib')` 找到 `<controlLib>` 子节点，将其内容从 `/nop/web/xlib/control.xlib` 重写为 `/nop/web/xlib/flux-control.xlib`。

### 关键源码位置

- 替换 GenPage/GenForm/GenGrid 等标签：`nop-web/src/main/resources/_vfs/nop/web/xlib/web/impl_flux_mode.xpl`
- 替换 controlLib：`nop-web/src/main/resources/_vfs/nop/web/xlib/view-gen.xlib` 中的 `DefaultViewPostExtends` 标签（第 12-26 行）
- 替换后的 Flux 控件库：`nop-web/src/main/resources/_vfs/nop/web/xlib/flux-control.xlib`（75 个控件映射标签）

### 这个设计的意义

这是 Nop 平台 XDSL 统一元编程机制的具体应用：不是在 GenForm 的实现代码里写条件分支判断渲染模式，而是在**模型加载的编译期**通过 `x:post-extends` 做声明式变换。两层 post-extends 各司其职：

| 层次 | 作用域 | 职责 |
|------|--------|------|
| xlib post-extends（`impl_flux_mode.xpl`） | `web.xlib` 标签库 | 替换页面生成器（GenPage → flux-web:GenPage） |
| XDSL post-extends（`DefaultViewPostExtends`） | 每个 view.xml 模型实例 | 替换控件库引用（controlLib → flux-control） |

这样一来，`flux-web/impl_GenForm.xpl` 等实现中完全不需要写 `if (renderMode == 'flux')` 类条件判断——它们读取到的 `viewModel.controlLib` 已经在加载期被正确设置为 `flux-control.xlib`。

## flux.yaml 页面文件：Flux 专属页面覆盖

除了上面「自动切换机制」对**同一个 `page.yaml`** 做模型加载期变换之外，平台还支持一种**按文件覆盖**的机制：为某个页面额外提供一个 `flux.yaml`，作为该页面在 Flux 模式下的专属实现。

### 命名约定

`flux.yaml` 与 `page.yaml` 位于**同一目录**，文件名仅复合扩展名不同：

```
pages/NopAuthUser/
  ├── main.page.yaml   ← 默认页面（AMIS），或 Flux 模式下自动切换的入口
  └── main.flux.yaml   ← 可选：Flux 模式下优先使用
```

即把 `main.page.yaml` 的复合扩展名 `page.yaml` 整体替换为 `flux.yaml`，得到 `main.flux.yaml`（**不是** `main.page.flux.yaml`）。

### 覆盖规则（fallback）

当 `nop.web.render-mode=flux` 时，后端在任何位置加载一个 `*.page.yaml` 之前，都会先检查同目录是否存在对应的 `*.flux.yaml`：

- **存在** `*.flux.yaml` → 加载 `flux.yaml`（**优先使用 Flux 页面**）。
- **不存在** → 回退到加载 `page.yaml`，并由上文「自动切换机制」走 `flux-web:GenPage`。

该规则在两个加载入口都生效：

| 入口 | 说明 | 实现位置 |
|------|------|---------|
| **顶层页面加载** | 前端 `PageProvider__getPage` 请求 `/.../main.page.yaml` | `PageModelLoader.loadObjectFromPath` 在 Flux 模式下检测同名 `flux.yaml`，命中则改加载它 |
| **子页面加载** | `tabs`/`wizard` 等页面通过 `LoadPage` 引用子 `page` | `flux-web.xlib:LoadPage` 在 Flux 模式下优先查找 `*.flux.yaml`，命中则 `x:extends` 它 |

> 非 Flux 模式（`render-mode=amis`，默认）下，`flux.yaml` **不会被自动使用**，`page.yaml` 按原逻辑渲染为 AMIS JSON。

### 如何启用

仅需启用 Flux 模式（启用方式见本仓库已有文档）：通过 `-Dnop.web.render-mode=flux` 或 `application.yaml` 设置 `nop.web.render-mode: flux`。无需为 `flux.yaml` 做额外配置——`flux.yaml` 已注册为标准页面文件类型（`xpage` 模型），Flux 模式开启后覆盖规则自动生效。

### 与自动切换机制的关系

两种机制**互补、不冲突**：

| 场景 | 行为 |
|------|------|
| 只有 `page.yaml`，开启 Flux 模式 | 走「自动切换机制」：同一个 `page.yaml` 在加载期把 `web:GenPage` 替换为 `flux-web:GenPage` |
| 同时有 `page.yaml` 和 `flux.yaml`，开启 Flux 模式 | **优先加载 `flux.yaml`**（覆盖规则）；`flux.yaml` 可以是完全手写的 Flux 页面，也可以用 `flux-web:GenPage` 生成后再追加 Flux 专属定制 |
| 同时有两者，但处于 AMIS 模式 | 用 `page.yaml`，忽略 `flux.yaml` |

`flux.yaml` 的价值在于：当一个页面的 Flux 形态与 AMIS 形态差异较大、仅靠「自动切换」难以表达时（例如完全不同的容器结构、原生 `ActionSchema` 编排），可以用 `flux.yaml` 单独承载 Flux 实现，而保持 `page.yaml` 的 AMIS 实现不受影响。

### 直接访问 flux.yaml

`flux.yaml` 与 `page.yaml` 一样可通过 `PageProvider__getPage` 直接按路径请求（例如 `/nop/auth/pages/NopAuthUser/main.flux.yaml`），也可作为 `LoadPage` 的 `page` 参数显式传入以 `.flux.yaml` 结尾的路径。直接请求时**不**受 Flux 模式开关影响——只要文件存在即加载。

### 示例

最简 `flux.yaml`（用 Flux 管线生成后追加标题包装）：

```yaml
# main.flux.yaml
title: 用户管理（Flux）
x:gen-extends: |
  <flux-web:GenPage view="NopAuthUser.view.xml" page="main"
                    xpl:lib="/nop/web/xlib/flux-web.xlib" />
```

### 实现锚点

- 页面文件类型注册（含 `flux.yaml`）：`nop-frontend-support/nop-web/src/main/resources/_vfs/nop/core/registry/page.register-model.xml`
- 顶层 fallback：`nop-frontend-support/nop-web/src/main/java/io/nop/web/page/PageModelLoaderFactory.java`（`PageModelLoader.loadObjectFromPath`）
- 子页面 fallback：`nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`（`LoadPage` 标签）
- Flux 模式开关：`nop-frontend-support/nop-web/src/main/java/io/nop/web/WebConfigs.java`（`CFG_WEB_RENDER_MODE` = `nop.web.render-mode`）
- 复合扩展名工具：`StringHelper.fileType(...)`（`main.flux.yaml` → `flux.yaml`）

## ORM 模型级启用

在 ORM 模型文件中通过扩展属性指定渲染器：

```xml
<entity name="NopAuthUser" ext:web-renderer="flux" ...>
```

代码生成模板会根据 `ext:web-renderer` 自动选择 `flux-control.xlib` / `flux-web.xlib` 或默认的 `control.xlib` / `web.xlib`。

也可以在 `<orm>` 根级别设置 `ext:web-renderer="flux"` 以启用模块全局的 Flux 渲染。

## 数据访问约定（强制）：REST `/r/`，不使用 GraphQL

Flux 渲染模式下的**页面数据访问不使用 GraphQL**，全部经 REST `/r/` 端点调用（`POST /r/OperationName`）。
这与 AMIS 时代的 GraphQL 直调约定不同：服务端仍由同一 RPC 端点承载，只是前端调用侧统一走 REST。

### URL 前缀约定

view.xml / page.yaml 中的 `api`、`url` 支持以下前缀，由浏览器壳层统一转换（实现见 nop-chaos-next 仓库的 `nopRpcResolver.ts`，请求层见同仓库的 `http.ts`）：

| 前缀 | 含义 | 转换结果 |
|------|------|---------|
| `@query:OperationName` | 查询操作 | `POST /r/OperationName` |
| `@mutation:OperationName` | 变更操作 | `POST /r/OperationName` |
| `@rpc:OperationName` | RPC 操作 | `POST /r/OperationName` |
| `/r/OperationName` | 显式 REST 路径 | 原样 `POST /r/OperationName` |

示例：

```text
@query:NopAuthUser__findPage?page=1&perPage=10   →  POST /r/NopAuthUser__findPage
@mutation:ErpMdMaterial__batchUpdate             →  POST /r/ErpMdMaterial__batchUpdate
```

### 参数与请求体变换

1. **`@selection` 字段裁剪**：可用 `@selection` 参数指定返回字段子集（`?@selection=field1,field2` 或请求体 `@selection` 键）。显式参数 > URL path > URL `?@selection=` 的优先级裁决，见 `nopRpcResolver.ts`。返回完整 Bean 的服务（如 `DictProvider__getDict`）**无需** `@selection`。
2. **请求体清洗**：递归过滤 `__`、`@`、`v_` 前缀键（运行时元数据）；顶层 `$` 前缀键（如 `$form`）为运行时系统参数一并过滤；**内嵌** `$` 键（如 TreeBean 结构中的 `$body`/`$type`）属于业务数据结构，保留。
3. **作用域注入**：Flux 不隐式携带表单数据，需显式 `includeScope` 或 `data: {...}` 模板映射（见下文「作用域（scope）传递规则」）。

### 对测试的影响

浏览器层 E2E 对页面数据访问应断言 **REST `/r/` 调用**（`nop-chaos-next/packages/e2e-shared` 提供 `RpcClient` / `rpc()`），GraphQL 仅用于服务端集成测试或非页面路径。禁止在 flux 页面上断言 GraphQL 请求。

## NormalizeAction 的 onClick 优先规则

`flux-web.xlib:NormalizeAction` 实现了 AMIS actionType 到 Flux ActionSchema 的转换，且遵循 **onClick 优先规则**。

此规则对应两种用法：

### 模式 A：简单 API 调用 — 走 `api`，自动转换

view.xml action 中只写 `api`，不写 `onClick`。NormalizeAction 自动转换为 single-step ActionSchema：

```xml
<action id="submit" label="提交" api="/r/NopAuthUser__save"/>
```

转换结果：`{ type: 'api', url: '/r/NopAuthUser__save', method: 'POST' }` 包裹在 `onClick` 中。适用于绝大多数 CRUD 按钮。

### 模式 B：复杂编排 — 写 `onClick`，原生透传

在 page.yaml 的 action 中直接写 Flux 原生 `ActionSchema`（`action` + `args` + `then` DAG 编排），NormalizeAction 检测到已有 `onClick` 则原样透传，不做任何转换：

```xml
<action id="batchOp" label="批量操作">
  <onClick>
    {
      action: "confirm",
      args: { message: "确认批量操作？" },
      then: {
        action: "ajax",
        args: { url: "/r/NopAuthUser__batchOp", method: "post", includeScope: "*" },
        then: {
          action: "refreshSource",
          targetId: "list",
          then: { action: "showToast", args: { level: "success", message: "操作成功" } }
        }
      }
    }
  </onClick>
</action>
```

### 作用域（scope）传递规则

**Flux 不自动传递表单数据或页面上下文到 API 调用**，需要显式指定。AjaxActionSchema 的 `args` 支持以下方式：

| 方式 | 说明 |
|------|------|
| `includeScope: "*"` | 注入当前作用域全部字段 |
| `includeScope: ["field1", "field2"]` | 仅注入指定字段 |
| `data: { name: "${name}" }` | 显式映射请求体（支持模板表达式） |

> **模板表达式语法**：统一使用 `${expr}` 格式（如 `${userName}`、`${status}`）。
> 旧的 `$propName` 语法（如 `$userName`、`$status`）正在被逐步废弃。
> XPL 生成代码时如果使用 `formData[name] = '$' + name` 生成的 `$fieldName`，
> 应改为 `formData[name] = '${' + name + '}'` 生成 `${fieldName}`。
> 这适用于 AMIS 和 Flux 双渲染模式，两种运行时的后续版本都将仅支持 `${expr}` 语法。

```xml
<!-- 显式传递上下文 -->
<onClick>
  {
    action: "ajax",
    args: {
      url: "/r/NopAuthUser__save",
      method: "post",
      includeScope: "*"
    }
  }
</onClick>
```

> 对比 AMIS：AMIS 的 `api.withFormData` 自动携带表单数据；Flux 无此隐式行为，必须显式声明。

### 常用 action 类型

完整 ActionSchema 结构见 `nop-chaos-flux` 项目的 `flux-guide` 文档。

| action | args | 说明 |
|--------|------|------|
| `ajax` | `{ url, method, data, includeScope, headers }` | HTTP 请求 |
| `confirm` | `{ message, title }` | 确认弹窗，通过 `then` 链后续动作 |
| `showToast` | `{ level, message }` | 提示消息 |
| `alert` | `{ message, title }` | 提示弹窗 |
| `openDialog` | `{ title, body }` | 打开弹窗 |
| `closeSurface` | — | 关闭当前弹窗 |
| `refreshSource` | `{ targetId }` | 刷新指定数据源 |
| `refreshTable` | — | 刷新表格 |
| `setValue` | `{ path, value }` | 设置变量值 |
| `component:*` | 按组件 | 调用组件方法（如 `component:submit`） |
| `navigate` | `{ url, replace, back }` | 页面导航 |

支持 `when` 条件守卫、`control.retry`/`control.debounce` 重试防抖、`onError`/`onSettled` 分支。详见 `flux-guide` 文档。

### 自动转换逻辑概要

- 如果 action 中已有 `onClick`（Flux 原生 ActionSchema），直接透传，不做任何转换。
- 如果没有 `onClick`，则从 `api`/`actionType`/`dialog`/`drawer` 自动转换为 Flux 原生 ActionSchema：
  1. 直接输出 `action` 字段（而非 `type` 简洁格式），无需前端归一化。映射表详见 `flux-web.xlib:NormalizeAction`。
  2. 有 `confirmText` 时套 `{ action: 'confirm', args: { message }, then: [...] }`；单步时直接返回该 step；多步时用 `then: [...]` 数组。

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

## 页面级容器：tabs / wizard / group / complex 的 Flux JSON 映射

`flux-web.xlib` 支持 xview.xdef 的四种容器化页面类型（`tabs`/`wizard`/`group`/`complex`），输出直接对齐 nop-chaos-flux 的权威 schema。**字段名是硬契约**：Flux 编译期按定义表过滤未知字段，字段名错误表现为静默空白而非报错。

### 容器 body 渲染与分派

- `tab`/`step` 节点支持内嵌 `body` 容器（`xdef:bean-body-prop="body"`），渲染优先级：**`page` > `body` > `name` 兜底**（`name` 在 xview.xdef 中必填，兜底恒命中）。
- `page` 存在 → `LoadPage(page)` 结果包入数组；`body` 非空 → body 中每个 `UiContainerModel` 经共享分派标签 `GenContainerModel` 渲染；两者皆无 → `LoadPage(name)`。
- `GenContainerModel` 是页面级与 body 级共用的容器分派标签（`flux-web.xlib`）：按 `type` 分派 `crud`/`simple`/`tabs`/`wizard`/`group`，均不包 `page` 外壳；其余类型抛 `nop.err.web.unknown-page-type`（body 内配置 `picker` 同样抛错——Flux 无页面级 picker schema）。
- `items[].body` / `steps[].body` 直接写 JSON 数组，由 Flux 编译期 `deepFields.nestedRegions` 提取为 region 渲染，不需要也不应该输出 `bodyRegionKey`。

### tabs 页面（Flux `TabsSchema`）

```json
{
  "type": "page",
  "body": {
    "type": "tabs",
    "items": [
      { "key": "tabA", "title": "Tab A", "body": [ { "type": "form", "name": "edit", "...": "..." } ] }
    ]
  }
}
```

- **`items` 数组**（Flux `TabsSchema` 字段）——不是 AMIS 的 `tabs` 字段；输出 `tabs` 字段在 Flux 下为 silent no-op。
- `items[].key` ← tab 的 `name`（Flux 用 key/value 定位激活 tab）；`title`/`icon` 等属性沿用 `FluxTabDefaultAttrs` 透传。
- `items[].body`：`page` 属性 → `LoadPage` 结果包数组；`body` 属性 → 容器分派 JSON 数组。

### form 级 tabs（`layoutControl="tabs"`）

表单内分组渲染为 `<tabs>`：`items[].body` 为表单行 JSON 数组（`flex`/`separator` 节点），`items[].key` ← 分组 id。**内容必须写入 `items[].body`**——写在其他字段（如 `tab`）会被 Flux 静默丢弃。

### wizard 页面（Flux `WizardSchema`）

```json
{
  "type": "page",
  "body": {
    "type": "wizard",
    "steps": [
      { "key": "stepA", "title": "Step A", "body": [ { "type": "form", "name": "step1" } ] }
    ]
  }
}
```

- `steps[].key` ← step 的 `name`；step 内容优先级同 tab（`page` > `body` > `name`）。
- 属性映射：`mode`/`actionPrevLabel`/`actionNextLabel`/`actionNextSaveLabel`/`actionFinishLabel` 透传；`startStep` **暂不映射**（Flux 0-based 实现 vs xview 1-based 模板串，语义错位）；`className`/`actionClassName`/`initFetch`/`initFetchOn`/`initApi`/`reload`/`redirect`/`target` 丢弃。

### group 页面（Flux `GridSchema`）

```json
{
  "type": "page",
  "body": {
    "type": "grid",
    "columns": 2,
    "gap": 8,
    "autoFlow": "row dense",
    "items": [
      { "key": "grid-a", "colSpan": 2, "rowSpan": 1, "body": [ { "type": "crud", "name": "grid-a" } ] }
    ]
  }
}
```

- `columns`/`gap` 透传；`autoFlow` 枚举映射：`row-dense`→`row dense`、`column-dense`→`column dense`。
- `alignItems`/`justifyItems` **过滤 `normal` 与 `baseline`**（不在 Flux 枚举）；`responsiveColumns` 暂不输出（xview string 与 Flux `{sm,md,lg}` 类型不匹配）。
- `items[].body` 为子容器分派 JSON 数组；`items[].colSpan/rowSpan` 来自子容器（仅 crud/tabs 容器支持，simple/wizard/group 无此字段）；`items[].key` ← 子容器 `name`（Flux 运行时 React key 契约）。

### complex 页面（Flux `PageSchema` 四槽位）

`complex` 与其他页面类型不同：它**不**在 `page.body` 内嵌一个容器节点，而是直接把 `page` 的四个槽位暴露给 view.xml 配置，正好对齐 Flux `PageSchema` 的四区域结构。

```json
{
  "type": "page",
  "name": "main",
  "header": [ { "type": "form", "name": "header-form" } ],
  "aside":  [ { "type": "crud", "name": "aside-table" } ],
  "body":   [ { "type": "form", "name": "body-form" } ],
  "footer": [ { "type": "form", "name": "footer-form" } ]
}
```

- view.xml 的 `<complex>` 下 `<header>`/`<footer>`/`<aside>`/`<body>` 四个子元素，每个是 `UiContainerModel`（容器列表），分别映射到 Flux `PageSchema` 的 `header`/`footer`/`aside`/`body` 数组。
- 每个槽位的容器列表经 `GenContainerModel` 分派渲染（`crud`/`simple`/`tabs`/`wizard`/`group` 五类，与 body 渲染共用同一分派）；空槽位不输出（Flux `PageSchema` 四槽位均可选）。
- `aside` 默认在左侧（Flux `PageSchema` 默认 `asidePosition='left'`；当前 complex schema 未暴露 `asidePosition`，无法配置为右侧）。
- 页面级属性（`title`/`className`/`asideMinWidth` 等）经 `FluxPageDefaultAttrs` 透传，与 crud/simple/tabs 页面一致。
- 实现于 `flux-web/page_complex.xpl`。

### 不支持的类型

- 页面级 `picker`：Flux 无页面级 picker schema（`PickerSchema` 仅为表单字段类型），`page_picker.xpl` 为 AMIS 遗留，暂不处理。

## 相关文件

- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib` — Flux 页面生成库（28 个标签，含共享容器分派 `GenContainerModel`）
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-control.xlib` — Flux 控件映射库（75 个标签）
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/page_tabs.xpl` / `page_wizard.xpl` / `page_group.xpl` — 页面级容器模板（包 `page` 外壳）
- `nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web/container_tabs.xpl` / `container_wizard.xpl` / `container_group.xpl` / `container_crud.xpl` / `container_simple.xpl` — 容器级模板（不包外壳，页面级与 body 级共用）

## 相关文档

- `frontend-rendering-pipeline.md` — 通用页面生成管线
- `view-and-page-customization.md` — 快速参考
- `amis-rendering.md` — AMIS 渲染管线 `(AMIS)`
- `../04-reference/source-anchors.md`（`EXT-008`、`EXT-009`）
