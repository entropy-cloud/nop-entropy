# XDef 与 XDSL 规则

## XDef 是什么

XDef 是 Nop 平台的**统一元模型语言**。Nop 平台中的所有 DSL（ORM、IoC beans、工作流、页面、xBiz 等）都通过 XDef 定义，定义文件统一存放在 `nop-xdefs` 模块的 `_vfs/nop/schema/` 下。

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

### 4. 复用结构使用 `xdef:name` / `xdef:ref`

- `xdef:name` 给结构命名
- `xdef:ref` 在同文件或外部文件中复用结构

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

## x-extends 合并算法：App = Delta x-extends Generator\<DSL\>

这是可逆计算理论的核心公式。Nop 平台与传统框架（Spring 等）的本质区别就在于此：**应用不是通过代码组装出来的，而是通过差量合并从生成器产出中推导出来的**。

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

典型场景：工作流引擎的 OA 会签节点——底层引擎只有普通步骤节点 + Join 合并节点，会签的 UI 简化配置由 `x:post-extends` 在编译期展开为底层引擎可识别的模型。

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

## 相关文档

- `./delta-customization.md` — Delta 定制的文件位置和操作方式
- `./debugging-and-diagnostics.md` — `_dump` 输出和属性来源追踪
- `./model-first-development.md` — 从模型到代码的生成链路
- `../04-reference/source-anchors.md`
