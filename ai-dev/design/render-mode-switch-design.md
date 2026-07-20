# 前端渲染模式全局切换设计

**日期**：2026-07-20
**范围**：`nop-web` 模块（`nop-frontend-support/nop-web`），涉及 `web.xlib`、`flux-web.xlib`、`view-gen.xlib`、`control.xlib`、`flux-control.xlib`、`WebConfigs.java`、`WebPageHelper.java`
**状态**：已实现
**灵感来源**：`x:post-extends` 机制

---

## 一、设计结论

1. **新增配置项 `nop.web.render-mode`**，取值 `amis`（默认）或 `flux`。当为 `flux` 时，强制所有页面使用 flux-web 的渲染管线。
2. **`controlLib` 覆盖（层2）**：通过 `x:post-extends` 在 `DefaultViewPostExtends`（`view-gen.xlib`）中实现，对已加载的 view 模型执行 `_dsl_root.controlLib` 的覆盖。
3. **页面模板切换（层1）**：在 `web.xlib` 的 `<x:post-extends>` 中通过 `<c:include src="web/impl_flux_mode.xpl"/>` 加载替换规则。当 `renderMode == 'flux'` 时输出替换后的 5 个入口标签定义（`GenPage`、`GenForm`、`GenGrid`、`GenInputTable`、`GenTable`），原始标签通过 `x:override="replace"` 被替换。`${view}` 等 tag attr 变量通过 `<c:print>` 保护，延迟到标签被调用时再求值。
4. **后处理适配**：`WebPageHelper.fixPage()` 增加 Flux 模式分支，避免 AMIS 特定后处理污染 Flux 输出。
5. **控制库选择**：`controlLib` 路径只影响具体控件的 JSON 输出形状，页面结构由 `web/` vs `flux-web/` 子目录下的模板决定。两个维度必须同步切换。

## 二、背景与动机

### 当前架构

前端同时支持 AMIS 和 Flux 两种渲染引擎。`view.xml` 是中性描述，分别通过两套独立的 XPL 模板翻译为 AMIS 或 Flux 的 JSON 输出：

```
.page.yaml → x:gen-extends → web:GenPage (xpl:lib="/nop/web/xlib/web.xlib")
                              ↓
                          web/impl_GenPage.xpl
                              ↓
                          web/page_crud.xpl + control.xlib
                              ↓
                          AMIS JSON

.page.yaml → x:gen-extends → flux-web:GenPage (xpl:lib="/nop/web/xlib/flux-web.xlib")
                              ↓
                          flux-web/impl_GenPage.xpl
                              ↓
                          flux-web/page_crud.xpl + flux-control.xlib
                              ↓
                          Flux JSON
```

两套管线的选择完全由 `.page.yaml` 中的 `xpl:lib` 属性决定。现有 100+ 个 `.page.yaml` 文件全部使用 `web.xlib`（AMIS）。

### 问题

当前没有运行时切换机制。要切换到 Flux 渲染，需要：
- 逐一修改所有 `.page.yaml` 的 `xpl:lib` 引用
- 逐一修改所有 `_*.view.xml` 的 `controlLib` 设置
- 无法在部署后通过配置动态切换

## 三、核心设计

### 3.1 配置项

在 `WebConfigs.java` 中新增：

| 配置名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `nop.web.render-mode` | `String` | `amis` | 渲染模式，`amis` 或 `flux` |

### 3.2 两层切换架构

切换需要同时在两个层次进行：

```
┌─────────────────────────────────────────────────┐
│  层1：页面结构模板                               │
│  web/page_crud.xpl ↔ flux-web/page_crud.xpl     │
│  web/impl_GenPage.xpl ↔ flux-web/impl_GenPage.xpl│
│  web/impl_GenForm.xpl ↔ flux-web/impl_GenForm.xpl│
│  web/impl_GenGrid.xpl ↔ flux-web/impl_GenGrid.xpl│
│  web/page_{simple|picker|tabs}.xpl ↔ 对应       │
│  web/grid_crud.xpl ↔ flux-web/grid_crud.xpl      │
│                                                   │
│  切换机制：web.xlib 入口标签代理转发              │
├─────────────────────────────────────────────────┤
│  层2：控件映射库                                 │
│  control.xlib ↔ flux-control.xlib                │
│                                                   │
│  切换机制：x:post-extends 覆盖 view.controlLib   │
└─────────────────────────────────────────────────┘
```

### 3.3 层2：controlLib 覆盖（x:post-extends）

#### 原理

所有生成的 `_*.view.xml` 均包含 `<x:post-extends>` 段，调用 `<view-gen:DefaultViewPostExtends>`。

在 XDSL 模型加载管线中，`x:post-extends` 的执行顺序在 `x:extends` 和 `x:gen-extends` 完成之后（`XDslExtender.java:472`），可以在此阶段修改已加载模型的任意属性。

#### 实现

修改 `view-gen.xlib:DefaultViewPostExtends` 的 `<source>`，当 `nop.web.render-mode == 'flux'` 时，将 `_dsl_root.controlLib` 赋值为 `/nop/web/xlib/flux-control.xlib`。

#### 为什么选 x:post-extends

- **无侵入**：不改 `impl_GenPage.xpl` / `impl_GenForm.xpl` 等模板，不修改 `view.xml` 文件
- **覆盖任何显式设置**：即使 view.xml 中写死了 `<controlLib>/nop/web/xlib/control.xlib</controlLib>`，post-extends 也能覆盖掉
- **单一修改点**：只在 `view-gen.xlib` 一处修改，影响所有 view 模型

### 3.4 层1：页面模板切换（web.xlib x:post-extends + c:print）

#### 原理

所有 `.page.yaml` 都引用 `xpl:lib="/nop/web/xlib/web.xlib"`，无需任何改动。在 `web.xlib` 的 `<x:post-extends>` 中通过 `<c:include src="web/impl_flux_mode.xpl"/>` 加载替换规则。当 `nop.web.render-mode == 'flux'` 时，`impl_flux_mode.xpl` 输出替换后的标签定义。

关键难点：`x:post-extends` 编译时 `${view}`、`${page}` 等 tag attr 变量不存在，直接输出会报编译错误。解决方案是用 `<c:print>` 包裹整块输出，让 `${view}` 保持字面量文本，延迟到 tag 被调用时再求值。

```
<source>
    <flux-web:GenPage view="${view}" page="${page}" xpl:lib="/nop/web/xlib/flux-web.xlib"/>
</source>
```

外层 `<c:print>` 在 post-extends 执行时保护 `${view}` 不被编译；当 `GenPage` 被调用时，`<source>` body 正常编译，`${view}` 求值为 tag attr 的实际值。

#### 实现

新增 `web/impl_flux_mode.xpl`，当 `renderMode == 'flux'` 时通过 `<c:print>` 输出替换后的标签定义。输出必须包裹 `<lib>` 根节点（因为 x:post-extends 的 `genCpExtends` 机制会剥去 `c:print` 产生的 `<_>` 占位节点，并将子节点作为独立 XDSL source 按根节点 xdef 校验）。

#### 为什么选 x:post-extends（而非 inline 分支）

x:post-extends 方案比 inline `<c:if>` 分支更干净：
- 不污染 tag 的 `<source>` body，原始 AMIS 代码保持不变
- 替换逻辑集中在一个文件（`impl_flux_mode.xpl`），新增或修改替换规则无需改动 `web.xlib` 本身
- `<c:print>` 机制解决了变量作用域问题，使 post-extends 可以安全输出包含 `${var}` 的延迟求值文本

### 3.5 `GenDispView` 的递归问题

`GenDispView` 内部会调用 `thisLib:GenPage`、`thisLib:GenInputTable`、`thisLib:GenTable` 来生成嵌套页面。在 `web.xlib` 的 `x:post-extends` 方案中：

- AMIS 模式：`GenPage` 保持原有 AMIS 实现（`<c:include src="web/impl_GenPage.xpl">`），`GenDispView` 调用的 `thisLib:GenPage` 走 AMIS 路径
- Flux 模式：`GenPage` 在 xlib 加载时已被替换为委托到 `flux-web:GenPage` 的版本，`thisLib:GenPage` 走 Flux 路径

递归正确，无需额外处理。

### 3.6 后处理适配

`WebPageHelper.fixPage()` 当前做了 AMIS 特定的后处理（添加 `amis` CSS class、确保 `group.body` 为 Array 等）。GraphQL URL 转义是通用的。

需修改 `fixPage()`，当 `nop.web.render-mode == 'flux'` 时跳过 AMIS 特有处理，只执行 GraphQL 转义等无关渲染引擎的后处理。

### 3.7 模块关系图

```
  xlib 加载管线：

  web.xlib 加载
      │ x:post-extends 编译执行
      │ <c:include src="web/impl_flux_mode.xpl"/>
      ▼
  impl_flux_mode.xpl
      │ <c:if test="${renderMode == 'flux'}">
      │   <c:print> 输出整块替换标签定义
      ▼
  genCpExtends 剥去 _ 占位节点
      │ 子节点 <lib> 按 xlib xdef 校验
      │ <tags> 中的 <GenPage x:override="replace"> 等替代原标签
      ▼
  标签定义替换完成

  页面加载管线（每个请求）：

  .page.yaml
      │ x:gen-extends 调用 <web:GenPage ...>
      │ xpl:lib="/nop/web/xlib/web.xlib"
      ▼
  web.xlib:GenPage.<source>（已通过 post-extends 替换）
      │
      ├── renderMode == amis ──→ <c:include src="web/impl_GenPage.xpl">
      │                              ↓
      │                          web/page_crud.xpl + control.xlib
      │                              ↓
      │                          AMIS JSON
      │
      └── renderMode == flux ──→ <flux-web:GenPage ...>
                                     ↓
                                 flux-web/impl_GenPage.xpl
                                     ↓
                                 flux-web/page_crud.xpl + flux-control.xlib
                                     ↓
                                 Flux JSON

配置 → 模型管线：

  nop.web.render-mode ──→ web.xlib:x:post-extends → impl_flux_mode.xpl (xlib 加载时)
                      ──→ view-gen.xlib:DefaultViewPostExtends (view.xml 加载时)

  两层切换同步进行，由同一个配置项驱动。
```

## 四、拒绝了什么

### 4.1 ~~用 `x:post-extends` 替换入口标签~~（已实现）

初始方案尝试使用 `web.xlib` 的 `<x:post-extends>` 来替换标签定义。但 `x:post-extends` 的执行上下文是 xlib 加载时的全局 scope，无法访问被替换标签的局部属性（`${view}`、`${page}` 等 tag attr）。导致编译时变量未定义错误。

**攻克方案**：用 `<c:print>` 包裹 post-extends 输出。`c:print` 不编译 body 中的 `${...}` 表达式，保持字面量文本。配合 `<lib>` 根节点包装（满足 xdsl source 校验），实现了 post-extends 延迟求值替换。

已作为正式方案采用，详见 3.4 节。

### 4.2 在 `impl_GenPage.xpl` 中加 config 判断

每个 `impl_GenPage.xpl`、`impl_GenForm.xpl`、`impl_GenGrid.xpl`、`init_grid_gen_scope.xpl` 都加一个 config 分支。

**拒绝理由**：改动分散（4 个文件），且 page-level 模板本身在 AMIS 和 Flux 之间有大量差异（`page_crud.xpl`、`grid_crud.xpl` 等），光改 controlLib 不足以完成切换。

### 4.3 在 `xview.xdef` schema 层定义 `x:post-extends`

在 `xview.xdef` 中直接写入覆盖规则。

**拒绝理由**：`xview.xdef` 是平台框架核心，不应依赖业务配置项。`DefaultViewPostExtends` 在业务模块的 `view-gen.xlib` 中，更靠近配置层。

### 4.4 全套 `.page.yaml` 替换

将所有 `.page.yaml` 的 `xpl:lib` 改为 `flux-web.xlib`。

**拒绝理由**：改动量大（100+ 文件），且无法运行时切换。

### 4.5 VFS Delta 替换 `web.xlib`

通过 delta 机制提供替换版本的 `web.xlib`。

**拒绝理由**：需要额外的 delta 模块，增加了部署复杂度。且 delta 替换是静态的，无法实现运行时配置切换。

### 4.6 只用 `x:post-extends` 覆盖 controlLib，页面模板不切换

**拒绝理由**：页面模板（如 `GenFormSimpleCell`）生成的 cell 属性在 AMIS 和 Flux 中不同（`visibleOn` vs `visible`、`disabledOn` vs `disabled` 等）。仅切换 controlLib 但保持 AMIS 页面模板会产生混合 JSON，前后端都不识别。

## 五、与已有设计的关系

| 组件 | 关系 |
|------|------|
| `control.xlib` / `flux-control.xlib` | 被切换的控件库，本设计不修改其内容 |
| `web.xlib` / `web/*.xpl` | AMIS 模板集，通过 `x:post-extends` + `<c:print>` 实现 xlib 加载时标签替换 |
| `flux-web.xlib` / `flux-web/*.xpl` | Flux 模板集，本设计不修改其内容 |
| `view-gen.xlib:DefaultViewPostExtends` | controlLib 覆盖的注入点 |
| `WebConfigs.java` | 新增 `nop.web.render-mode` 配置 |
| `WebPageHelper.fixPage()` | 增加 Flux 模式分支 |
| `xview.xdef` | 仅引用，不修改 |
