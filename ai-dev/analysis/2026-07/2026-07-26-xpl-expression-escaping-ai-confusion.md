# XPL 表达式转义的 AI 混淆问题

> Status: open
> Date: 2026-07-26
> Scope: nop-entropy 全模块 — XPL 模板、Codegen、view.xml、xlib 中的 `${}` 表达式转义
> Conclusion: （待评审后填写）

## Context

Nop 平台的 Xpl 模板语言使用 `${}` 作为表达式求值语法，而前端框架（AMIS/Flux）也使用 `${}` 来表示前端运行时表达式。当 Xpl 模板用于生成前端代码（view.xml、控制 JSON 等）时，需要正确转义以避免 Xpl 引擎将前端表达式当成服务端表达式求值。

标准转义模式为 `${'$'}{expr}`，即用 `${'$'}` 求值得到字符 `$`，再输出字面量 `{expr}` 给前端。这个规则本身不复杂，但由于 Nop 存在多层代码生成管线（codegen → `_gen/` → view.xml → x:gen-extends → gen-control），不同上下文中 `${}` 的语义不同，导致 AI 在实现 Nop 代码时频繁出错。

## 系列事件回溯

| 时间 | 事件 | 状态 |
|------|------|------|
| 2026-06-19 | 首次系统分析 AMIS `$xxx` vs `${xxx}` 语法统一问题 | 完成 |
| 2026-07-15 | 补充分析 amis `$xxx` 简写 vs 表达式语法差异 | 完成 |
| 2026-07-16 | Plan 291 执行全仓库 `$xxx` → `${'$'}{xxx}` 迁移 | 已完成 |
| 持续 | AI 在新写/修改代码时仍频繁出现转义错误 | **未解决** |

Plan 291 完成了存量代码的一次性迁移，但**没有解决 AI 产出的源头问题**。只要 AI 不理解 Nop 的多层编译管道，新写代码就会继续产生转义错误。

## 问题分析

### 1. 多层处理上下文的语义断裂

Nop 中同一个 `${...}` 语法在不同文件中语义完全不同：

| 文件类型 / 上下文 | `${expr}` 语义 | 写 `${id}` 安全吗？ |
|---|---|---|
| `.xgen` 代码生成模板 | **Xpl 求值** | ❌ 被当作变量 `id` 求值 |
| `.xpl` / `.xlib` 模板 | **Xpl 求值** | ❌ 同上 |
| `.view.xml` 主体（grids/forms/pages） | **字符串字面量** | ✅ 安全，透传 AMIS |
| `.view.xml` `<x:gen-extends>` 块内 | **Xpl 求值** | ❌ |
| `.view.xml` `<x:post-extends>` 块内 | **Xpl 求值** | ❌ |
| `.view.xml` `<gen-control>` 内 | **Xpl-xjson 编译** | ❌ |
| `.view.xml` `<c:script>` 内 | **Xpl 脚本** | ❌（但此处用 JS 语法而非 `${}`） |
| `.page.yaml` 主体 | **JSON 字面量** | ✅ 安全 |
| `.page.yaml` `x:gen-extends:` YAML 字符串 | **Xpl 求值** | ❌ |
| `visibleOn` / `disabledOn` / `requiredOn` 属性值 | SchemaExpression（透传） | ✅ 写 `${status == 1}` 安全（不经过 Xpl） |

**核心断裂点**：AI 需要区分"这份代码是否处于 Xpl 处理管道中"，但同一 `.view.xml` 文件内部既有非 Xpl 区域（主体）又有 Xpl 区域（`x:gen-extends`、`gen-control`）。

### 2. 嵌套编译管线（最难理解的场景）

代码生成流水线：

```
.xgen (Xpl模板, 所有${}都求值)
  ↓ 代码生成
_gen/_Xxx.view.xml (也是 XDSL 模型)
  ↓ x:extends + Xpl 编译
最终 View 模型
  ↓ GenPage 运行时执行
框架 JSON 给前端
```

在 `.xgen` 模板中写 `${'$'}{id}`：
1. **第一层（.xgen）**：Xpl 求值 `${'$'}` → `$`，输出 `${id}` 到 `_gen/_Xxx.view.xml`
2. **第二层（_gen/.view.xml）**：主体中 `${id}` 是字符串字面量 **不** 求值，透传给 AMIS
3. **AMIS 运行时**：解释 `${id}` 为变量引用

但如果这段代码在 `_gen/.view.xml` 的 `<x:gen-extends>` 块内，第二层也会求值，就需要更深一层转义。

**AI 常见反应**：不知道当前编译第几层，于是"能跑就行"地试错。

### 3. AI 错误的典型模式

#### 模式 A：在 Xpl 上下文中漏转义

在 `.xgen` 模板或 `gen-control` 中直接写 `${id}`：

```xml
<!-- .xgen 模板：Xpl 会求值 ${id}，但 id 未定义 -->
<api url="@mutation:Xxx__delete?id=${id}"/>
```

#### 模式 B：在非 Xpl 上下文中过度转义

在 `.view.xml` 主体中使用 `${'$'}{id}`（其实不需要）：

```xml
<!-- view.xml 主体中 ${} 不被 Xpl 求值，写 ${id} 即可 -->
<visibleOn>${'$'}{status == 1}</visibleOn>  <!-- 错误！AMIS 收到 ${status == 1} 无法求值 -->
```

真实案例：`visibleOn` / `disabledOn` / `requiredOn` 属性值是 SchemaExpression 类型，不经过 Xpl 编译，直接透传给 AMIS。AI 常常也对这些属性做转义。

#### 模式 C：使用 `$$` 转义

`$${id}` 被 Xpl 解析为 `$` 字面量 + `${id}` 求值，结果产生 `$id`（旧版 AMIS 简写），而非 `${id}`（新版标准语法）。Plan 291 已确认 `$$` 是 anti-pattern，但 AI 仍会写出这个模式。

#### 模式 D：无视 `gen-control` 的编译语义

`gen-control` 内容被 xdef 声明为 `xpl-xjson` 域，编译为 `IEvalAction`。AI 经常把 `gen-control` 当普通 XML 配置写入 `${id}`，期望它透传；实际上它会被编译执行，`${id}` 在无 `id` 变量时抛错。

#### 模式 E：错误理解 `page.yaml` 的 `x:gen-extends:` 字符串

`.page.yaml` 的 `x:gen-extends:` 值是 YAML 多行字符串，实际由 Xpl 编译。AI 常把它当成纯 YAML 处理。

### 4. 存量残留问题

Plan 291 完成后，剩余未迁移项：

| 位置 | 数量 | 说明 |
|------|------|------|
| `_{objName}.view.xml.xgen` | **6 处** `$id`/`$ids` | 代码生成模板源头，影响所有模块的 `_gen/_*.view.xml` |
| `_gen/_*.view.xml`（各模块） | ~82 处 | 生成产物，修复源头后重新生成即可 |
| `nop-auth/test.json` | ~20 处 | 手工 AMIS fixture，已裁定 watch-only residual |

代码生成模板的具体位置：

```
nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-web/
  src/main/resources/_vfs/{moduleId}/pages/{metaInfo.forEntity}{metaInfo.baseObjName}/
    _gen/_{metaInfo.objName}.view.xml.xgen
```

| 行号 | 当前写法 | 应改为 |
|------|---------|--------|
| 157 | `ids=$ids` | `ids=${'$'}{ids}` |
| 182 | `id=$id` | `id=${'$'}{id}` |
| 200 | `id=$id` | `id=${'$'}{id}` |
| 222 | `id=$id` | `id=${'$'}{id}` |
| 227 | `id=$id` | `id=${'$'}{id}` |
| 234 | `$id` | `${'$'}{id}` |

### 5. 现有文档覆盖不足

当前关于表达式转义的文档：

| 文档 | 内容 | 不足 |
|------|------|------|
| `docs-for-ai/02-core-guides/amis-rendering.md:67-114` | AMIS 变量的 `${'$'}{xxx}` 转义规则 | 只覆盖 AMIS 场景，未覆盖 codegen 通用管线 |
| `ai-dev/analysis/2026-06-19-amis-expression-syntax-unification.md` | AMIS 表达式语法统一分析 | 历史分析，Plan 291 后已部分过时 |
| `docs-for-ai/02-core-guides/frontend-rendering-pipeline.md` | 前端渲染管线 | 没有表达式转义章节 |

缺少：
- 一份**独立于 AMIS 的通用 Xpl 表达式转义指南**（codegen、xlib、view.xml 各上下文）
- 每种上下文的**决策流程图**或**查表**
- `gen-control` 中表达式的处理规则
- `.xgen` 代码生成模板特有的双层转义规则

## 改进建议

### 建议 1：创建通用 Xpl 表达式转义参考文档

在 `docs-for-ai/02-core-guides/` 下新增 `xpl-escaping-reference.md`，独立于 AMIS 具体框架。按"文件类型 × 上下文位置"的矩阵给出转义规则。

### 建议 2：提供 AI 可执行的决策流程

```
当前文件是 .xgen / .xpl / .xlib?
  → 整个文件是 Xpl 上下文
  → 需要输出 AMIS 字面量 ${xxx}? → 使用 "${'$'}{xxx}"（元素内容/属性）
  → 需要输出 AMIS 字面量 ${xxx}? → 在 JSON 表达式中使用 '$' + '{xxx}'（JS对象属性）
当前文件是 .view.xml?
  ↓
代码在 <x:gen-extends> 或 <x:post-extends> 内?
  → Xpl 上下文，规则同上
代码在 <gen-control> 内?
  → Xpl-xjson 编译上下文，规则同上
代码在 <c:script> 内?
  → 普通 JS，不需要 ${}
代码在主体（grids/forms/pages/cols/cells 等）?
  → 非 Xpl 上下文，写 ${id} 直接透传
  → visibleOn/disabledOn/requiredOn → 也是透传，写 ${status == 1}
当前文件是 .page.yaml?
  → 主体是 JSON，写 ${id} 直接透传
  → x:gen-extends: 字符串内是 Xpl 上下文
```

### 建议 3：修复代码生成模板的残留 `$id`/`$ids`

按上表修改 `_{objName}.view.xml.xgen` 的 6 处 `$id`/`$ids` → `${'$'}{id}`/`${'$'}{ids}`，重新生成所有 `_gen/_*.view.xml`。

此项为 Plan 291 的遗留残余，完成后可关闭那项 deferred 的 follow-up。

### 建议 4：在 AGENTS.md 中增加表达式转义的 Hard Rule

在 AGENTS.md 的"Code Conventions"部分增加一条：

> **Xpl 表达式转义规则（高频出错点）**：Nop 使用 `${}` 作为 Xpl 表达式语法。在 `.xgen`/`.xpl`/`.xlib` 模板文件、`.view.xml` 的 `<x:gen-extends>`/`<x:post-extends>`/`<gen-control>` 块内，要输出字面量 `${xxx}` 给前端时必须使用 `${'$'}{xxx}` 转义。`.view.xml` 主体（`<grids>`/`<forms>`/`<pages>` 内）和 `visibleOn`/`disabledOn`/`requiredOn` 属性不受 Xpl 处理，直接写 `${id}` 即可。**切勿使用 `$$` 转义**（它产生 AMIS 旧版 `$xxx` 简写）。

### 建议 5：添加测试可检查的 lint 规则

利用 Nop 的 XDef schema 验证机制或自定义检查脚本，在 build 时检测常见转义错误。最简单快速的方式：

```bash
# 检查 .xgen 模板中是否含有未经转义的 ${id} 模式
rg '\$\{id\}' --include='*.xgen' nop-kernel/nop-codegen/
```

（但此类检查必须有白名单机制，因为 `.view.xml` 主体中的 `${id}` 是合法的）

## 结论

（待评审）

Plan 291 迁移了存量代码的 `$xxx` → `${'$'}{xxx}`，但**没有解决 AI 持续产出的源头问题**。根本原因在于 Nop 的多层编译管线使同一个 `${}` 在不同上下文中语义不同，而 AI 缺乏判断上下文类型的决策框架。

建议通过"文档决策树 + AGENTS.md hard rule + 代码生成模板修复 + lint 辅助"四管齐下解决。

## Open Questions

- [ ] 是否需要为 `_gen/` 目录中的文件引入更明确的 Xpl 处理说明（或者让 codegen 模板自动使用正确转义，使 AI 不需要理解双层管线）？
- [ ] `visibleOn` / `disabledOn` / `requiredOn` 这类 SchemaExpression 属性是否有可能在未来的框架升级中变成 Xpl 编译域？如果会，当前"直接写透传"的规则就需要更新。
- [ ] `gen-control` 中既有 Xpl 元素写法（XML 标签）又有 JS 返回写法（`return {...}`），两者的转义规则是否需要分别说明？

## References

- `docs-for-ai/02-core-guides/amis-rendering.md` — AMIS 变量引用转义规则
- `docs-for-ai/02-core-guides/frontend-rendering-pipeline.md` — 前端渲染管线
- `ai-dev/analysis/2026-06-19-amis-expression-syntax-unification.md` — AMIS 表达式语法统一分析
- `ai-dev/plans/291-migrate-dollar-shorthand-to-expression-syntax.md` — Plan 291 迁移执行
- `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-web/.../_{objName}.view.xml.xgen` — 代码生成模板（含残留）
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslExtender.java` — Xpl 编译管线核心
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/domain/XplStdDomainHandlers.java` — `gen-control` 的 `xpl-xjson` 域实现
