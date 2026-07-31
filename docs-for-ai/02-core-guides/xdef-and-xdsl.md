# XDef 与 XDSL 规则

## XDef 是什么

XDef 是 Nop 平台的**统一元模型语言**。Nop 平台中的所有 DSL（ORM、IoC beans、工作流、页面、xBiz 等）都通过 XDef 定义，定义文件统一存放在 `nop-xdefs` 模块的 `_vfs/nop/schema/` 下。

如果你要先理解“为什么 XDef/XDSL 是平台通用可扩展机制的根”，先读 `../06-extensibility/platform-extensibility-mechanism.md`。本页只负责把 XDef/XDSL 的局部规则讲清楚。

XDef 的目标不是再造一层与最终 DSL 完全不同的 schema，而是让模型结构与最终 XML 基本同构，只是把具体值替换为类型声明。定义了 XDef 元模型后，自动获得：

- XML 和 JSON 的双向转换
- 差量合并（Delta 定制）
- 编译期元编程（`x:gen-extends` / `x:post-extends`）
- IDEA 插件的提示、补全、校验

## 所有 XDSL 文件的基本格式

根节点必须同时包含：

```xml
<your-tag x:schema="/nop/schema/your-xdef.xdef"
          xmlns:x="/nop/schema/xdsl.xdef">
    ...
</your-tag>
```

## 硬性前提：读 XDSL 必须先读 xdef

理解或修改任何 XDSL 文件（`.view.xml`、`.xmeta`、`.orm.xml`、`.beans.xml` 等）时，**必须先读取其 `x:schema` 引用的 `.xdef` 文件**。xdef 是该 DSL 结构的唯一权威来源——元素名、属性类型、子节点结构、key-attr 等全部由 xdef 定义。不要跳过 xdef 直接在 Java 代码中 grep 类名来猜测结构，这会走弯路。

典型路径：看到 `x:schema="/nop/schema/xui/xview.xdef"` → 读该 xdef → 理解 `<objMeta>v-path</objMeta>` 等定义 → 再按需查 Java 实现。

## 最适合 AI 记住的几条规则

### 0. 节点默认可带名字空间扩展属性

这是理解 Nop 元编程的关键前提：

- XDSL 中的任意节点，默认都可以增加带名字空间的扩展属性
- **除非特殊指定要检查的名字空间，否则这类属性通常不参与 xdef 校验**
- 这些属性经常在编译期被 `x:gen-extends` / `x:post-extends` / XLib / XPL 读取，用来生成正式 DSL 结构

因此，很多扩展的第一步不是新增节点，而是先在现有节点上挂一个扩展属性。

真实例子：

- `model-first-development.md` 中的 `biz:moduleId`
- `workflow-configuration.md` 中的 `wf:upLevel`、`wf:permissions`
- `generate-report.md` 中的 `xpt:*` 扩展属性
- `control.xlib`、`meta-gen.xlib`、`meta-prop.xlib` 一类编译期库会读取这些属性并展开元编程结果

### 1. 简单标量优先写成属性

推荐：

```xml
<user id="!string" name="string" age="int">
    <description>string</description>
</user>
```

### 2. 长文本或复杂结构优先写成子节点

典型如：

- `description`
- 嵌套对象
- 集合结构

### 3. 列表结构使用 `xdef:body-type` 与 `xdef:key-attr`

```xml
<services xdef:body-type="list" xdef:key-attr="name">
    <service name="!var-name" className="!class-name"/>
</services>
```

**重要规则：`key-attr` 指定的属性必须在子节点上显式声明，且类型必须与 `xdef:ref` 继承的类型一致**

`xdef:key-attr="name"` 要求每个子节点必须有 `name` 属性。这个校验发生在 `xdef:ref` 解析之前，因此必须显式声明。声明的类型必须与 `xdef:ref` 继承的类型完全一致：

```xml
<!-- ✅ 正确：类型 !xml-name 与 pattern.xdef 中的 name="!xml-name" 一致 -->
<patterns xdef:key-attr="name" xdef:body-type="list">
    <pattern name="!xml-name" xdef:ref="pattern.xdef"/>
</patterns>

<!-- ❌ 错误：类型 !string 与 pattern.xdef 中的 name="!xml-name" 不一致 -->
<patterns xdef:key-attr="name" xdef:body-type="list">
    <pattern name="!string" xdef:ref="pattern.xdef"/>
</patterns>

<!-- ❌ 错误：缺少 name 声明，key-attr 校验会失败 -->
<patterns xdef:key-attr="name" xdef:body-type="list">
    <pattern xdef:ref="pattern.xdef"/>
</patterns>
```

### 4. 复用结构使用 `xdef:name` / `xdef:ref`

- `xdef:name` 给结构命名，可在同文件或外部文件中通过 `xdef:ref` 引用
- `xdef:ref` 继承被引用结构的 body-type、子节点定义等
- 属性声明必须在使用 `xdef:ref` 的节点上显式写出，且**类型必须与被引用定义一致**

### 5. 逗号分隔字符串集合优先用 `csv-set`

不要把这类值继续写成普通 `string`。

### 6. 简单文本元素推荐直接写类型内容

推荐：

```xml
<description>string</description>
```

而不是为了简单文本额外使用 `xdef:body-type="string"`。

### 7. Java 枚举作为 XDef 域时，要让文本值对得上

如果 `.xdef` 中引用的是 `enum:全限定类名`，要优先保证枚举的可读文本与 XDef 中实际使用的值一致。

最常见的两种做法：

1. 给枚举项加 `@Option("text")`。
2. 如果文本值和 `name()` 不一致，覆写 `toString()` 返回协议值。

仓库里的很多枚举还会提供 `@StaticFactoryMethod` 的解析入口；如果周边代码已经这样写，继续沿用该风格。

**枚举类型语法**：

```xml
<!-- 可选枚举，无默认值 -->
<partition name="enum:io.nop.stream.core.execution.plan.PartitionPolicy"/>

<!-- 可选枚举，带默认值 -->
<partition name="enum:io.nop.stream.core.execution.plan.PartitionPolicy=FORWARD"/>

<!-- 必填枚举，无默认值 -->
<partition name="!enum:io.nop.stream.core.execution.plan.PartitionPolicy"/>

<!-- 必填枚举，带默认值 -->
<partition name="!enum:io.nop.stream.core.execution.plan.PartitionPolicy=FORWARD"/>
```

注意：`!` 必须放在 `enum:` 前面，不能放在 `=` 后面。默认值不是必须的，Java 代码可以自行处理。

### 8. `xdef:bean-*` 属性族：XDSL 节点到 Java Bean 的属性映射

XDef 不仅定义 XML 结构约束，还控制 XDSL 文件解析为 Java 对象时的**属性映射**——即"节点的哪部分内容（标签名 / 属性 / 子节点 / 文本 / 注释）写入 bean 的哪个属性"。`xdef:bean-*` 属性族就是这套映射开关。

核心实现链路：`XDefinitionParser`（解析 xdef 时读取这些属性）→ `DslBeanModelParser`（解析 XDSL 实例时按映射规则填充 bean）→ `XDefToObjMeta`（从 xdef 生成 ObjSchema 元数据）。

#### 属性总览

| 属性 | 作用 | 默认值 |
|------|------|--------|
| `xdef:bean-class` | 本节点解析生成的 Java Bean 类（全限定名） | 配合根节点 `xdef:bean-package` + `xdef:name` 自动拼接 |
| `xdef:bean-body-type` | body 集合的泛型类型（如 `List<Foo>`） | 由 `body-type` 推断 |
| `xdef:bean-prop` | 当前节点在**父 bean** 中的属性名 | tagName 经 camelCase 转换；声明了 `unique-attr` 时为 `tagName+"s"` |
| `xdef:bean-body-prop` | **复杂 body**（子节点集合）写入的 bean 属性名 | `body`（仅当节点非 simple 时自动设此默认值） |
| `xdef:bean-value-prop` | **简单值**（纯文本 body 或 `xdef:value`）写入的 bean 属性名 | 无，需显式指定 |
| `xdef:bean-tag-prop` | 将节点**标签名**（tagName）写入的 bean 属性名 | 无 |
| `xdef:bean-comment-prop` | 将节点**注释**解析为 `XDefComment` 后写入的 bean 属性名 | 无 |
| `xdef:bean-child-name` | 列表 body 的元素子属性名 | 单一子节点类型时为该 tagName 转换 |
| `xdef:bean-sub-type-prop` | union 结构的**判别字段**属性名 | 回退到子节点的 `bean-tag-prop` |
| `xdef:bean-unknown-attrs-prop` | 未明确定义的属性集合（`Map`）写入的 bean 属性名 | 无 |
| `xdef:bean-unknown-children-prop` | 未明确定义的子节点集合写入的 bean 属性名 | 无 |

#### `bean-body-prop` vs `bean-value-prop`

二者都控制"节点内容写入哪个属性"，区别在于内容的**形态**：

- `bean-body-prop`：节点有子节点、声明了 `xdef:body-type`，body 解析为**复杂结构**（List/Map/Union）后写入此属性。
- `bean-value-prop`：节点是叶子、声明了 `xdef:value`，解析为**简单标量值**后写入此属性。

当一个节点**同时声明两者**时，解析器按实际内容形态路由：无子节点走 value 路径，有子节点走 body 路径，分别写入不同属性。这是 union 类型节点（同一节点既可以是字面量也可以是复杂结构）的标准模式。

真实示例（`beans.xdef:15`，`BeanPropValue` 可为字面量或复杂结构）：

```xml
<xdef:define xdef:name="BeanPropValue" xdef:body-type="union"
             xdef:bean-body-prop="body" xdef:bean-value-prop="value" ...>
```

只声明 `bean-body-prop`（不声明 `bean-value-prop`）时，简单值也会落到 `bean-body-prop` 指定的属性（见 `XDefToObjMeta.valueToProp` 的回退逻辑）。

#### `bean-tag-prop` 与 `$type` / `$body` 约定

`bean-tag-prop` 把 XML 标签名记录为 bean 的一个 String 属性，主要用于 union/多态结构中判别具体子类型。

两种典型用法：

**1. 专用 bean 类——用普通属性名**

```xml
<!-- 记录标签名到 bean 的 tagName 属性 -->
<and xdef:bean-tag-prop="tagName" ...>
```

**2. 通用 `TreeBean`——用 `$type` / `$body` 特殊约定**

当 `xdef:bean-class="io.nop.api.core.beans.TreeBean"` 时，XDSL 不映射到专用 bean，而是映射到通用树结构。此时固定使用 `$type` / `$body` 这两个特殊属性名（定义于 `ApiConstants.TREE_BEAN_PROP_TYPE / TREE_BEAN_PROP_BODY`）：

```xml
<!-- filter.xdef:7-8 -->
<xdef:define xdef:name="FilterCondition" xdef:body-type="list"
             xdef:bean-tag-prop="$type" xdef:bean-body-prop="$body">
```

`$` 前缀是合法 Java 标识符。`$type` 对应 XML 标签名，`$body` 对应节点内容。JSON↔XML 转换器（`DefaultJsonToXNodeAdapter`、`BuildXNodeJsonHandler`）会识别这两个键，将它们还原为 XML 的 tag 和 body，而不是普通数据字段。

#### 常见组合：容器节点 `xdef:ref` + `bean-body-prop`

复用已命名结构（`xdef:name` 定义、`xdef:ref` 引用）时，配合 `bean-body-prop` 指定子节点集合写入的目标属性。这是 `xview.xdef` 等容器型 DSL 的常见写法：

```xml
<!-- group 的子节点解析为列表后，写入 UiContainerModel bean 的 body 属性 -->
<group name="!string" xdef:ref="UiContainerModel"
       xdef:bean-body-prop="body" xdef:body-type="list"/>
```

#### 判别与默认逻辑要点

- `bean-body-prop` 仅对**非 simple 节点**自动设默认值 `body`（simple 节点指无属性、仅有 body-type 或 value、且未声明任何 bean-* prop 的节点，见 `IXDefNode.isSimple()`）。
- `bean-prop`（节点在父 bean 中的属性名）默认为 tagName 的 camelCase；声明了 `unique-attr` 时默认为 `tagName + "s"`。
- union schema 必须有判别字段：优先用 `bean-sub-type-prop`，否则回退到子节点声明的 `bean-tag-prop`，都没有则报错。

> 实现 anchor：`XDefinitionParser.java:333-389`（读取 bean-\* 属性与默认值推断 `:452-471`）、`DslBeanModelParser.java:130-152`（按映射填充 bean）、`XDefToObjMeta.java:230-243,694-737`（生成 ObjSchema 属性）。

## x-extends 合并算法：App = Delta x-extends Generator\<DSL\>

这是可逆计算理论的核心公式。这里不展开整个平台的理论背景，只聚焦它在 XDef/XDSL 合并中的具体含义；平台级解释见 `../06-extensibility/platform-extensibility-mechanism.md`。

### 合并顺序

所有 XDSL 模型加载时都经过一条确定的合并链：

```xml
<model x:extends="A,B">
    <x:gen-extends>
        <C/>
        <D/>
    </x:gen-extends>

    <x:post-extends>
        <E/>
        <F/>
    </x:post-extends>
</model>
```

合并结果为：

```
F x-extends E x-extends model x-extends D x-extends C x-extends B x-extends A
```

即：**后面的覆盖前面的**，合并方向从右到左。

| 层 | 时机 | 作用 | 谁覆盖谁 |
|----|------|------|---------|
| `x:extends="A,B"` | 首先 | 引入外部基础模型，A 和 B 先后合并 | B 覆盖 A |
| `x:gen-extends` | 编译期执行 | XPL 模板动态生成模型节点，结果作为新的基础 | D 覆盖 C |
| 当前模型体 | 合并到 gen-extends 结果上 | 手写的差量内容 | 当前模型覆盖 gen-extends 结果 |
| `x:post-extends` | 编译期执行 | XPL 模板再次生成，覆盖当前模型 | F 覆盖 E |
| `x:config` | 合并完成后执行 | 通过 `<c:import>` 引入标签库和 Java 类定义 | — |
| `x:post-parse` | 模型解析后执行 | 领域特定的验证或模型增强 | — |

### `x:gen-extends` vs `x:post-extends`

- **`x:gen-extends`**：生成基础结构，当前模型可以在其基础上做增删改。适合：从 ORM 模型生成默认的 xbiz/view、从 PDMan 生成 ORM 等。
- **`x:post-extends`**：在当前模型之上再做一次变换。适合：对已有 DSL 做二次扩展而不修改运行时引擎。例如 ORM 中标记 `tagSet="json"` 的字段自动生成 JsonOrmComponent。

关键区别：当前模型**覆盖** `x:gen-extends` 的结果，但被 `x:post-extends` **覆盖**。

### `x:post-extends` 的设计意图

对已有 DSL 进行可逆计算分解，得到扩展 DSLx：

```
App = Delta x-extends Generator<DSL>
DSL = Delta x-extends Generator<DSLx>
```

描述业务时使用扩展语法 DSLx，`x:post-extends` 负责将其转化为已有 DSL 语法。**合并完成后，所有 x 名字空间的属性和子节点都会被自动删除**——底层运行时引擎完全不需要知道扩展语法的存在。

注意：这里的“扩展语法”不一定表现为新增节点，也经常表现为“现有节点 + 一组名字空间扩展属性”。编译期库先读这些属性，再生成标准节点/属性结构。

典型场景：工作流引擎的 OA 会签节点——底层引擎只有普通步骤节点 + Join 合并节点，会签的 UI 简化配置由 `x:post-extends` 在编译期展开为底层引擎可识别的模型。

**真实案例：Flux 模式下自动替换 controlLib**

`nop-web` 中 `view-gen.xlib:DefaultViewPostExtends` 是 `x:post-extends` 的一个典型应用。每个 view.xml 模型加载时都会经过这个 post-extends：

```xml
<!-- _gen/_NopAuthUser.view.xml 中声明： -->
<x:post-extends>
    <view-gen:DefaultViewPostExtends xpl:lib="/nop/web/xlib/view-gen.xlib"/>
</x:post-extends>
```

`DefaultViewPostExtends` 的实现（`view-gen.xlib:12-26`）：

```javascript
let renderMode = $config.var('nop.web.render-mode', 'amis');
if (renderMode == 'flux') {
    let child = _dsl_root.childByTag('controlLib');
    if (child != null) {
        child.content('/nop/web/xlib/flux-control.xlib');
    }
}
```

它在 view 模型加载期（`x:post-extends` 阶段）直接修改 DOM 树：将 `<controlLib>/nop/web/xlib/control.xlib</controlLib>` 重写为 `/nop/web/xlib/flux-control.xlib`。当后续 `impl_GenForm.xpl` 读取 `viewModel.controlLib` 时，拿到的已经是正确的 Flux 控件库路径。

这个案例说明：
- `x:post-extends` 不限于 xlib 级别的变换，同样适用于**单个 XDSL 模型实例**的编译期修改
- 变换发生在模型加载期而非运行时，对下游代码完全透明
- 不需要在 GenForm 实现中写渲染模式判断——模型数据已经被正确预处理

## Union schema 的 subtype 约定

当 schema kind 为 `UNION` 时，运行时对象与 XDSL transform 都依赖显式 subtype 字段路由到具体子 schema：

- `subTypeProp` 指定判别字段名。
- 子 schema 的 `typeValue` 是可匹配的 subtype 值。
- 路由顺序为：先精确匹配 `typeValue`，再回退到 `typeValue="*"` 的 fallback schema。
- `subTypeProp` 缺失或找不到匹配子 schema 时，属于契约错误，应报错而不是静默跳过。

这意味着 union 不是“逐个 oneOf 试跑直到通过”的宽松校验模式，而是显式判别字段驱动的单路由模式。

### `x:config` — 引入标签库和常量

`<x:config>` 是所有 XDSL 模型的公共语法（定义在 `xdsl.xdef` 中），在合并过程完成后执行，用于通过 `<c:import>` 引入标签库和 Java 类定义，使其在模型的 XPL 表达式中可用：

```xml
<task x:schema="/nop/schema/task/task.xdef">
    <x:config>
        <c:import from="/nop/rule/xlib/rule.xlib"/>
    </x:config>

    <steps>
        <step name="checkRule">
            <source>
                <rule:Execute ruleName="my-rule" inputs="${{amount: order.totalAmount}}"/>
            </source>
        </step>
    </steps>
</task>
```

`<c:import>` 将标签库注册到编译作用域，`<source>` 中的 `rule:Execute` 标签即可直接使用，无需在每个标签上写 `xpl:lib`。**合并完成后，所有 `x:` 名字空间的属性和子节点都会被自动删除**——`<x:config>` 引入的定义只在编译期有效，不影响运行时。

### `x:post-parse` — 模型解析后回调

根据 xdef 元模型将 XML 解析为具体的模型对象之后执行，可用于领域特定的验证或模型增强。

### 合并算子 `x:override`

| 值 | 语义 |
|----|------|
| `merge`（缺省） | 逐级合并子节点：同名属性覆盖，同名子节点递归合并，新增子节点追加 |
| `replace` | 当前节点完全替换基础模型中的对应节点 |
| `remove` | 从结果中删除该节点 |
| `bounded-merge` | 与 `merge` 类似，但只保留基础模型和派生模型中都存在的子节点 |
| `merge-replace` | 合并属性，但子节点或内容完全替换 |

### `_dump` 调试

设置 `x:dump="true"` 或在 `nop.debug=true` 模式下启动，合并后的最终模型会输出到 `_dump/{appName}/...`。输出文件通过 **XML 注释**（`<!--LOC:[行:列]/vfs路径-->`）记录每个节点和属性的实际来源源码位置，方便定位 Delta 是否生效、某个值来自哪个层。详见 `./debugging-and-diagnostics.md`。

## 什么时候你应该先想起本页

1. 需要新增或修正 `.xdef` 文件。
2. 需要理解 `x:gen-extends`、`x:post-extends` 的合并顺序和设计意图。
3. 需要解释 `x:schema`、`x:extends`、`x:override`。
4. 需要设计一个新 DSL 或修正旧 DSL 结构。
5. 需要理解"为什么改了 Delta 文件但运行时没生效"（合并顺序不对）。
6. 需要判断一个 XML DSL 文件为什么不符合仓库惯例。
7. 需要理解 `xdef:bean-body-prop`、`xdef:bean-tag-prop`、`xdef:bean-value-prop` 等属性如何控制 XDSL→Java Bean 的属性映射。

## 相关文档

- `../06-extensibility/platform-extensibility-mechanism.md` — 平台级可扩展机制总览
- `./delta-customization.md` — Delta 定制的文件位置和操作方式
- `./debugging-and-diagnostics.md` — `_dump` 输出和属性来源追踪
- `./model-first-development.md` — 从模型到代码的生成链路
- `../04-reference/source-anchors.md`
