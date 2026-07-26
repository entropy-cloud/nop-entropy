# Xpl 表达式转义参考

> **核心问题**：Nop 的 Xpl 模板引擎和前端框架（AMIS/Flux）都使用 `${expr}` 语法，前者在服务端求值，后者在浏览器求值。当 Xpl 模板用于**生成**前端代码时，需要正确转义。

## 快速查找

| 位置 | 怎么写 |
|------|--------|
| `.xgen` / `.xpl` / `.xlib`（全部） | `${'$'}{xxx}` |
| `.view.xml` 主体（grids/forms/pages/cols/cells/actions） | `${xxx}`（不转义） |
| `.view.xml` `<x:gen-extends>` / `<x:post-extends>` | `${'$'}{xxx}` |
| `.view.xml` `<gen-control>` / `<renderer>` | `${'$'}{xxx}` |
| `.view.xml` `visibleOn` / `disabledOn` / `requiredOn` | `${xxx}`（不转义） |
| `.page.yaml` 主体 | `${xxx}`（不转义） |
| `.page.yaml` `x:gen-extends:` / `x:post-extends:` 字符串 | `${'$'}{xxx}` |
| `.json` / `.page.json`（纯 JSON fixture） | `${xxx}`（不转义） |

## 一句话规则

先判断"当前代码块是否经过 Xpl 引擎求值？"
- **是（Xpl 上下文）**：要输出字面量 `${xxx}` 给前端 → 写 `${'$'}{xxx}`
- **否（非 Xpl 上下文，如 view.xml 主体、YAML/JSON 直写）**：直接写 `${xxx}`

## 文件类型 × 上下文 决策表

| 文件类型 | 上下文位置 | Xpl 求值? | 写 `${id}`? | 写 `${'$'}{id}`? |
|---|---|---|---|---|
| `.xgen` | 整个文件 | **是** | ❌ 求值失败（undefined var） | ✅ 输出字面量 |
| `.xpl` / `.xlib` | 整个文件（除 `<c:script>`） | **是** | ❌ 求值失败（undefined var） | ✅ 输出字面量 |
| `.view.xml` | `<grids>` / `<forms>` / `<pages>` 内（主体） | **否** | ✅ 透传前端 | ❌ 过度转义 |
| `.view.xml` | `<x:gen-extends>` / `<x:post-extends>` 内 | **是** | ❌ 求值失败 | ✅ |
| `.view.xml` | `<gen-control>` / `<renderer>` 内 | **是（xpl-xjson）** | ❌ 求值失败 | ✅ |
| `.view.xml` | `<c:script>` 内 | JS 脚本 | 不用 `${}` | 不用 `${}` |
| `.view.xml` | `visibleOn` / `disabledOn` / `requiredOn` | **否（string 域）** | ✅ 透传前端 | ❌ 过度转义 |
| `.page.yaml` | YAML 主体（非 `x:gen-extends:`） | **否** | ✅ 透传前端 | ❌ 过度转义 |
| `.page.yaml` | `x:gen-extends:` / `x:post-extends:` YAML 字符串内 | **是** | ❌ 求值失败 | ✅ |
| `.json` / `.page.json` | 纯 JSON fixture（无 `x:schema` 触发 XDSL 管线） | **否** | ✅ 透传前端 | ❌ 过度转义 |

> **注意**：`visibleOn` / `disabledOn` / `requiredOn` 在 xdef 中声明为 `string` 域，不经 Xpl 编译，直接透传给前端。

## 决策流程

```
当前文件是什么类型?
│
├── .xgen / .xpl / .xlib
│   └── 整个文件是 Xpl 上下文
│       ├── 想输出字面量 ${xxx} 给前端? → 用 ${'$'}{xxx}
│       ├── 在 Xpl JSON 表达式中想输出 ${xxx}? → 用 '$' + '{xxx}'
│       └── 想写真正的 Xpl 表达式? → 用 ${expr}（正常 Xpl 语法）
│
├── .view.xml
│   ├── 在 <x:gen-extends> / <x:post-extends> 内?
│   │   → Xpl 上下文 → 需要字面量 ${xxx}? → 用 ${'$'}{xxx}
│   │
│   ├── 在 <gen-control> / <renderer> 内?
│   │   → Xpl-xjson 上下文 → 需要字面量 ${xxx}? → 用 ${'$'}{xxx}
│   │
│   ├── 在 <c:script> 内?
│   │   → 普通 JS 脚本 → 不用 ${} 语法
│   │
│   └── 在主体（grids/forms/pages/cols/cells/actions，不包括 gen-control/renderer）?
│       → 非 Xpl 上下文 → 写 ${id} 直接透传
│       → visibleOn/disabledOn/requiredOn → 也是透传，写 ${status == 1}
│
├── .page.yaml
│   ├── 在 x:gen-extends: / x:post-extends: 字符串内
│   │   → Xpl 上下文 → 需要字面量 ${xxx}? → 用 ${'$'}{xxx}
│   └── 主体（JSON 区域）
│       → 非 Xpl 上下文 → 写 ${id} 直接透传
│
└── .json / .page.json（纯 JSON，无 XDSL 管线）
    → 非 Xpl 上下文 → 写 ${id} 直接透传
```

## 各上下文的写法示例

### 1. `.view.xml` 主体 — 非 Xpl，直接写 `${id}`

```xml
<api url="@mutation:NopAuthUser__delete?id=${id}"/>
<data>
    <userId>${id}</userId>
</data>

<!-- visibleOn 不经过 Xpl 编译，直接写 -->
<visibleOn>${status == 1}</visibleOn>
<requiredOn>${resourceType != 'TOPM'}</requiredOn>
```

### 2. `.view.xml` `<x:gen-extends>` / `<x:post-extends>` — Xpl，用 `${'$'}{id}`

```xml
<x:gen-extends>
    <web:GenPage view="MyView.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib">
        <data>
            <parentId>${'$'}{id}</parentId>
            <siteId>${'$'}{siteId}</siteId>
        </data>
    </web:GenPage>
</x:gen-extends>
```

### 3. `.view.xml` `<gen-control>` — Xpl-xjson

`gen-control` 中的**XML 子元素**被编译为 Xpl-xjson，用 `${'$'}{id}`：

```xml
<col id="pageContent">
    <gen-control>
        <button-group>
            <buttons j:list="true">
                <button label="设计">
                    <api url="@mutation:MyObj__save?id=${'$'}{id}"/>
                </button>
            </buttons>
        </button-group>
    </gen-control>
</col>
```

如果 `gen-control` 需要返回 JS 对象，必须包裹在 `<c:script>` 中，用 `'$' + '{xxx}'`：

```xml
<cell id="ruleInputs">
    <gen-control>
        <c:script>
            return { ['$' + '{ref}']: "inputDefinition" };
        </c:script>
    </gen-control>
</cell>
```

> **不要**在 `gen-control` 中直接写 `return { "${'$'}{ref}": "inputDefinition" }` — 文本内容经过 `parseFullExpr` 处理，不是 Xpl 模板，`${'$'}{ref}` 不会被求值，而是作为字面量字符串保留。

### 4. `.xpl` / `.xlib` 模板 — Xpl

XML 元素内容/属性：

```xml
<!-- ${'$'} 求值为 $，${...} 是实打实的 Xpl 表达式在服务端求值。
     例：若 labelProp = "name"，则输出 ${name} 给前端 -->
<tpl tpl="${'$'}{${labelProp}}"/>
<labelTpl>${'$'}{${objMeta.displayProp}}</labelTpl>
```

Xpl JSON 表达式模式（JS 对象字面量）：

```xml
<!-- Xpl JSON 表达式中不能用 ${'$'}{xxx}，要用字符串拼接 -->
url: "/f/download/" + '$' + "{fileId}"
```

> **JSON vs XML 语法**：在 Xpl 的 XML 属性/内容中，用 `${'$'}{xxx}`。在 Xpl JSON 表达式（JS 对象字面量）中，用 `'$' + "{xxx}"`。原理相同：避免 `${}` 被 Xpl 解析。不要混用。

### 5. `.xgen` 代码生成模板 — Xpl，用 `${'$'}{id}`

```xml
<api url="@mutation:Xxx__delete?id=${'$'}{id}"/>
<data>
    <parentProp>${'$'}{id}</parentProp>
</data>
```

### 6. `.page.yaml` `x:gen-extends:` — Xpl 字符串，用 `${'$'}{id}`

```yaml
x:gen-extends: |
  <web:GenPage view="MyView.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib">
    <data>
      <roleId>${'$'}{roleId}</roleId>
    </data>
  </web:GenPage>
```

### 7. `.page.yaml` 主体 — JSON，直接写 `${id}`

```yaml
data:
  userId: "${id}"
url: "@query:Xxx__get?id=${id}"
```

## 修复流程：发现 `_gen/` 文件有误怎么办

> **`_gen/` 目录和所有 `_` 前缀文件（`_*.view.xml`、`_*.java`、`_*.xmeta` 等）都是 codegen 自动生成的。**
> **不要手改它们——改模板源文件，然后 `mvn install -DskipTests` 重新生成即可覆盖。**

| 问题位置 | 应修改的源文件 | 重新生成命令 |
|----------|---------------|-------------|
| `_gen/_*.view.xml` 过度转义 | `nop-codegen/src/main/resources/.../orm-web/.../*.xgen` 或 `web.xlib`/`grid_crud.xpl` | `./mvnw install -DskipTests -T 1C` |
| `_gen/_*.view.xml` 漏转义 | 同上 | 同上 |
| `_gen/_*.java` 实体类 | `model/*.orm.xml`（改模型定义） | `./mvnw install -DskipTests -T 1C` |
| `_*.xmeta` 元模型 | `model/*.orm.xml` 或生成的模板 | `./mvnw install -DskipTests -T 1C` |

通用工作流：**改源模型/模板 → `mvn install -DskipTests` → 检查生成的 `_gen/` 文件**。不要手改生成物。

## 嵌套转义（双层管线）

代码生成流水线中，`.xgen` 模板生成 `_gen/_*.view.xml`：

```
.xgen (Xpl模板, 所有${}都求值)
  ↓ 代码生成
_gen/_Xxx.view.xml (XDSL 模型, 主体非 Xpl, x:gen-extends 内是 Xpl)
  ↓ x:extends + Xpl 编译
最终 View 模型
```

在 `.xgen` 中用 `${'$'}{id}`：
1. **第 1 层（.xgen）**：Xpl 求值 `${'$'}` → `$`，输出 `${id}` 到 `_gen/_Xxx.view.xml`
2. **第 2 层（_gen/.view.xml 主体）**：`${id}` 是字符串字面量，透传给 AMIS
3. **AMIS 运行时**：解释 `${id}` 为变量引用

如果 `.xgen` 生成的 `_gen/` 文件中包含 `<x:gen-extends>` 块，该块内还会再次经 Xpl 求值，需要两层转义：

**.xgen 模板**：
```xml
<x:gen-extends>
    <web:GenPage view="MyView.view.xml" page="main">
        <data>
            <!-- 目标：最终 AMIS 收到 ${id} -->
            <!-- 第 1 层（.xgen 编译）：外层 ${'$'} → $，输出 ${'$'}{id} 到 _gen 文件 -->
            <!-- 第 2 层（_gen 编译）：再求值一次 → 输出 ${id} 给 AMIS -->
            <parentId>${'$'}{'$'}{id}</parentId>
        </data>
    </web:GenPage>
</x:gen-extends>
```

> ⚠️ 实践中极少需要如此深的嵌套。优先避免在 `.xgen` 生成的 `_gen/` 文件中包含需 Xpl 求值的 `<x:gen-extends>`。

## 反模式

### `$$` 不是合法的 Xpl 转义

```
$${xxx}  →  Xpl 解析为: '$' 字面量 + ${xxx} 求值
         →  输出: $xxx  (AMIS 旧版简写)
         →  ❌ 不是字面量 ${xxx}
```

**正确做法**：一律用 `${'$'}{xxx}`。

### 过度转义 `visibleOn`

```xml
<!-- ❌ 错误：visibleOn 不经过 Xpl -->
<visibleOn>${'$'}{status == 1}</visibleOn>

<!-- ✅ 正确：直接写 -->
<visibleOn>${status == 1}</visibleOn>
```

### 在 view.xml 主体中使用 `'$' + '{xxx}'`

```xml
<!-- ❌ 错误：view.xml 主体是非 Xpl 上下文，不需要字符串拼接 -->
<api url="/f/download/" + '$' + "{fileId}"/>

<!-- ✅ 正确：直接写 ${} 透传 -->
<api url="/f/download/${fileId}"/>
```

## 诊断：写错了会出现什么现象

| 错误模式 | 写成了... | 现象 |
|----------|-----------|------|
| Xpl 上下文中漏转义 | `${id}` | Xpl 求值时 `id` 未定义，报 `NopException: undefined prop` 或返回 null |
| 非 Xpl 上下文中过度转义 | `${'$'}{status == 1}` | AMIS 收到 `${status == 1}`（带 `$` 前缀），不识别为表达式，做字符串处理 |
| 使用了 `$$` | `$${id}` | AMIS 收到 `$id`（旧版简写），不支持过滤器/函数 |
| `gen-control` 文本内容写 `${'$'}{xxx}` | `return { "${'$'}{xxx}": v }` | `gen-control` 文本走 `parseFullExpr`，`${'$'}{xxx}` 不被求值，输出字面量键名 |
| 在 JSON 表达式中用 `${'$'}{xxx}` | `"${'$'}{xxx}"` | 语法错误，Xpl JSON 模式不支持 `${}` 语法 |

## 相关文档

- `amis-rendering.md` — AMIS 渲染管线（含 AMIS 专用变量引用规则；其转义部分已由本文档的上下文决策表修正）
- `flux-rendering.md` — Flux 渲染管线
- `frontend-rendering-pipeline.md` — 通用前端渲染管线
- `xlang-and-xpl-basics.md` — Xpl 基础语法
- `view-and-page-customization.md` — view.xml/page.yaml 定制快速参考
