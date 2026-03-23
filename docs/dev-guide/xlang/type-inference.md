# XLang类型推导设计说明

## 目标

XLang的类型推导目标不是实现TypeScript级别的静态类型系统，而是提供一种面向脚本开发的、实用且保守的类型推导能力。

整体定位如下：

- 比Java局部变量推断更积极
- 明显弱于TypeScript的结构类型和全控制流求解
- 优先覆盖脚本日常高频场景
- 优先保证规则简单、局部、可解释
- 推不稳时宁可退化为`any`或较宽的`union`

这意味着XLang类型推导更像一个“局部语义增强器”，而不是一个完整的编译型静态类型系统。

## 设计原则

### 局部优先

类型推导主要发生在单个函数体、单个语句块和当前词法作用域中，不做跨函数、跨文件、跨模块的全局求解。

### 保守合并

当多个分支给出不同类型时，优先合并为`union`，而不是激进选择某个更窄类型。

### 结构简单

只对少量高价值语法节点提供专门规则，避免引入复杂CFG、约束传播器、结构类型兼容矩阵等高复杂度机制。

### 容器优先于结构对象

数组字面量优先推导为`List<T>`，对象字面量优先推导为`Map<String,V>`，而不是精确对象shape。

### 规则可解释

每一条推导规则都应该能用一句直观的话解释。例如：

- 变量类型来自初始化表达式
- `if`两侧赋值不一致时合并为`union`
- `typeof`结果总是`string`
- `x instanceof T`结果总是`boolean`

## 总体架构

当前实现主要由以下几个部分组成。

### `TypeInferenceProcessor`

文件：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java`

这是类型推导的主入口。它基于AST节点类型逐个处理表达式、语句和声明，并负责：

- 递归遍历AST
- 推导当前节点类型
- 把类型结果回填到表达式节点的`returnTypeInfo`
- 维护局部作用域中的变量类型
- 在分支处做保守合并
- 调用缩窄和泛型推导等辅助组件

### `TypeInferenceState`

文件：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceState.java`

它表示当前推导上下文，主要保存：

- 当前作用域变量类型
- 当前分支中的缩窄类型
- 父作用域引用
- 当前函数的显式返回类型约束

`newChild()`用于进入子作用域，例如块、分支、循环体和函数体。

### `UnionTypeNarrower`

文件：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/UnionTypeNarrower.java`

它负责从条件表达式中提取局部缩窄信息，例如：

- `x != null`
- `typeof x == "string"`
- `x instanceof Foo`
- `Array.isArray(x)`
- `if (x)` 这种truthy判断

它只负责收集某个分支内可用的缩窄结果，不负责全局传播。

### `GenericTypeInferencer`

文件：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/GenericTypeInferencer.java`

它负责轻量级泛型推导，主要能力是：

- 从调用实参推导类型变量绑定
- 在返回类型中替换类型变量
- 对简单容器类型、函数类型和返回类型做递归替换

它不尝试实现TypeScript那种复杂的泛型约束求解。

### `ReturnTypeInfo`

文件：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/ReturnTypeInfo.java`

它是当前实现中用于传递推导结果的轻量载体，主要承载：

- 节点推导得到的类型
- 返回语句相关信息
- 分支返回缺失标记

目前它同时服务于“表达式类型传播”和“返回流汇总”，这是一个有意保持简单、但需要注意边界的设计点。

## 当前支持的推导范围

### 1. 字面量和基础表达式

支持：

- `string`
- `boolean`
- `int`
- `long`
- `double`
- `null`
- 基础数值二元运算
- 比较表达式
- `typeof`
- `instanceof`
- 基础一元运算
- 自增自减
- 序列表达式
- cast表达式
- 模板字符串字面量

示例：

```xlang
let a = 1        // int
let b = "x"     // string
let c = -a       // int
let d = typeof b // string
```

### 2. 变量声明和赋值

支持：

- 显式类型声明
- 从初始化器推导变量类型
- 多个declarator顺序处理
- 普通赋值回写变量类型
- 复合赋值类型更新
- 数组/对象解构的基础绑定

示例：

```xlang
let a = 1
let b = "x"
a += 2
```

### 3. 分支与局部数据流

支持：

- `if/else`真分支和假分支的局部缩窄
- 分支结束后对同名变量进行保守合并
- 无`else`分支时保留原类型并和真分支结果合并

示例：

```xlang
let x = cond ? 1 : "a"

if (flag) {
  x = 2
} else {
  x = "b"
}
// x -> int | string
```

### 4. 容器字面量

支持：

- 数组字面量推导为`List<elementType>`
- 数组元素不一致时合并为`union`
- 对象字面量推导为`Map<String, valueType>`
- 对象属性值类型不一致时合并为`union`
- spread的基础保守传播

示例：

```xlang
let a = [1, 2, 3]        // List<int>
let b = [1, "x"]        // List<int|string>
let c = {a: 1, b: "x"}  // Map<String, int|string>
```

### 5. 函数与返回类型

支持：

- 函数声明推导
- 箭头函数推导
- 参数显式类型读取
- 参数默认值推导
- `return`与显式返回类型兼容性检查
- 表达式体箭头函数返回类型推导

示例：

```xlang
let f = (x: int) => x + 1
// f: (x:int)=>int
```

### 6. 函数调用与轻量泛型

支持：

- 从callee的函数类型中获取返回类型
- 从调用实参推导简单类型变量绑定
- 将类型变量替换到返回类型中

示例：

```xlang
identity<T>(x: T): T
identity("a") // string
```

### 7. 成员访问

支持：

- `list.length`
- `list.size`
- `map.size`
- `string.length`
- 参数化`List<T>`的元素类型
- 参数化`Map<K,V>`的值类型
- 通过Java反射读取bean property和field类型

### 8. 低成本补齐的节点

为了在不显著增加复杂度的情况下提升实用性，当前额外补齐了以下规则：

- `UnaryExpression`
- `UpdateExpression`
- `TypeOfExpression`
- `InstanceOfExpression`
- `CastExpression`
- `SequenceExpression`
- `TemplateStringLiteral`
- `TemplateStringExpression`
- `AwaitExpression`
- `NewExpression`
- `ForOfStatement`
- `ForInStatement`
- `ConcatExpression`
- `BraceExpression`
- `ChainExpression`
- `InExpression`
- `WhileStatement`
- `DoWhileStatement`
- `ForStatement`
- `RegExpLiteral`
- `DeleteStatement`
- `TryStatement`
- `CatchClause`
- `SwitchCase`
- `CompareOpExpression`
- `AssertOpExpression`
- `BetweenOpExpression`
- `SpreadElement`
- `UsingStatement`
- `TextOutputExpression`
- `EscapeOutputExpression`
- `CollectOutputExpression`
- `GenNodeExpression`
- `GenNodeAttrExpression`
- `EmptyStatement`
- `BreakStatement`
- `ContinueStatement`
- `ThrowStatement`
- `ThisExpression`
- `SuperExpression`
- `ObjectBinding`
- `PropertyBinding`
- `RestBinding`
- `ArrayBinding`
- `ArrayElementBinding`
- `ImportDefaultSpecifier`
- `ImportNamespaceSpecifier`

这些规则的共同特点是：

- 结构简单
- 类型结果直观
- 不依赖复杂控制流求解
- 出错时可自然退化为`any`或宽类型

## 当前刻意不支持的能力

以下能力当前不作为目标：

### 精确对象结构类型

不会把对象字面量推导为类似：

```ts
{name: string, age: int}
```

而是统一使用`Map<String,V>`表示。

### 全控制流求解

不做：

- 跨循环不动点分析
- 完整CFG分析
- 可达性/支配关系求解
- 跨块复杂路径合并

### TypeScript级泛型与结构类型

不做：

- 条件类型
- 判别联合
- 结构兼容矩阵
- 上下文敏感高阶函数推导
- 完整泛型边界求解

### 全局跨函数推导

函数调用依赖已知签名和局部调用点信息，不尝试做跨项目全局反演。

## 关键算法说明

### 1. 类型合并

`mergeTypes()`遵循以下策略：

1. 两边都为空时退化为`any`
2. 任意一边为空时返回另一边
3. 相同类型直接返回
4. 不同类型展平后合并为`union`

这是一个偏保守、偏信息保留的策略。

更接近实现代码的描述如下：

```text
mergeTypes(t1, t2)
  if t1 == null and t2 == null -> any
  if t1 == null -> t2
  if t2 == null -> t1
  if t1.equals(t2) -> t1
  if t1.typeName == t2.typeName -> t1

  result = []
  把 t1/t2 中已经存在的 union 展平
  按 typeName 去重

  if result 为空 -> any
  if result 只有一个元素 -> 该元素
  else -> Union(result)
```

示例：

```xlang
let x = cond ? 1 : 2L
// mergeTypes(int, long) -> int|long

let y = cond ? null : "a"
// mergeTypes(null, string) -> null|string
```

这里故意不做更激进的“公共父类提升”或“数值自动最小上界推导”，因为当前目标是规则稳定且容易解释。

### 2. 分支变量合并

`mergeConditionalVariableTypes()`只关注分支中实际写回过的局部变量，不尝试重新求解整个环境。

这样做的好处是：

- 规则简单
- 性能稳定
- 行为容易解释
- 不会演化为复杂数据流引擎

更具体地说，分支合并分三步：

1. 进入 `if`/`else` 时分别创建两个子作用域。
2. 每个子作用域只记录“这个分支里真正改动过的变量”。
3. 分支结束后，只对这些变量做合并；没有被修改的变量保持外层原值。

示例：

```xlang
let x = 1
let y = "a"

if (flag) {
  x = "b"
}

// x -> int|string
// y -> string
```

这里 `y` 不会被重新计算，因为它在分支里没有发生写回。

### 3. 缩窄模型

缩窄只在当前分支的子状态中生效，通过`narrowedTypes`覆盖原始变量类型。

退出分支后，缩窄结果不会直接污染外层作用域，而是只影响该分支内的推导。

当前实现能识别的高价值条件主要包括：

- `typeof x == "string"`
- `x instanceof Foo`
- `x == null` / `x != null`
- `Array.isArray(x)`
- `if (x)` / `if (!x)` 这种基础 truthy / falsy 场景

示例：

```xlang
let x = cond ? "a" : null

if (x != null) {
  // 分支内把 x 缩窄为 string
  x.length
}

// 分支外 x 仍然是 string|null
```

要注意：当前缩窄是“分支内局部可用信息”，不是完整控制流求解，所以它不会在离开分支后永久改变外层变量类型。

### 4. 泛型推导模型

泛型推导只处理下列模式：

- 形参与实参位置一一对应
- 容器类型递归匹配
- 函数返回类型中的类型变量替换

当同一类型变量出现多次且绑定不一致时，优先宽化为更宽类型或`union`，以保持脚本开发的容错性。

更接近当前实现的流程如下：

```text
inferTypeArguments(typeParams, paramTypes, argTypes)
  对每一对 (形参类型, 实参类型) 做递归匹配
  如果形参位置出现类型变量 T，则记录 T -> 实参类型
  如果 T 已经绑定过：
    - 相同则保持不变
    - 不同则合并为更宽类型或 union

调用完成后：
  把得到的 {T -> 实际类型} 替换回返回类型
```

示例：

```xlang
identity<T>(x: T): T
identity("a")
// T -> string
// 返回类型 -> string
```

再比如：

```xlang
pair<T>(a: T, b: T): T
pair(1, "x")
// T 在两个位置分别绑定为 int 和 string
// 当前保守结果: T -> int|string
```

这也是本实现和很多更严格静态类型系统的区别：冲突时优先保守容错，而不是直接让推导失败。

### 5. 变量声明与解构绑定

`VariableDeclarator`、`PropertyBinding`、`ArrayElementBinding` 等节点都遵循“先看显式信息，再看 initializer，再做保守回写”的思路。

普通变量声明的顺序是：

1. 先处理 initializer。
2. 如果有显式类型，优先采用显式类型。
3. 如果没有显式类型，就使用 initializer 的推导结果。
4. 两者都没有时退化为 `any`。
5. 把最终类型写回当前作用域。

示例：

```xlang
let a = 1
// a -> int

let b: long = 1
// b -> long
// 同时检查 initializer(int) 是否兼容 long
```

对解构 binding，当前实现不做精确 shape 推导，而是做“最小可用”的传播：

- 数组模式默认把元素看成统一的 `elementType`
- 对象模式默认把属性值看成统一的 `map valueType`
- `rest` 则退化为 `List<elementType>` 或 `Map<String, valueType>`

示例：

```xlang
let [a, b] = [1, 2]
// a -> int, b -> int

let {name} = {name: "x", age: 1}
// 当前不是精确 shape 推导
// 而是按 Map<String, string|int> 传播
// name -> string|int（保守结果）
```

### 6. 容器与成员访问

数组字面量和对象字面量的算法都比较直接：

- 数组：遍历每个元素，逐个 `mergeTypes`，最后得到 `List<mergedType>`
- 对象：遍历每个属性值，逐个 `mergeTypes`，最后得到 `Map<String, mergedType>`
- 如果遇到 spread / computed key / 非标准属性节点，则进一步向 `any` 方向保守退化

示例：

```xlang
let a = [1, "x", true]
// List<int|string|boolean>

let b = {a: 1, b: "x"}
// Map<String, int|string>
```

成员访问的顺序则是：

1. 先判断是不是 `List` / `Map` / `String` 这类内建规则。
2. 命中内建规则则直接返回，如 `list.length -> int`。
3. 否则再退到 Java 反射，查 bean property / field。
4. 还查不到就退化为 `any`。

### 7. 函数调用与返回类型传播

`CallExpression` 的当前实现是“先求 callee，再求参数，再根据函数签名取返回类型”：

1. 先推导 `callee`。
2. 再顺序推导每个 argument。
3. 如果 `callee` 是函数类型，则取它的 `funcReturnType`。
4. 如果返回类型中含有类型变量，则尝试用实参做一次轻量泛型替换。
5. 如果仍拿不到结果，则退化为 `any`。

示例：

```xlang
let f = (x: int) => x + 1
f(2)
// 返回 int
```

### 8. 低成本节点补齐的统一方法

后面补的很多节点，其实遵循的是同一套“小规则模板”：

- 先递归处理子节点
- 再根据节点语义给出一个固定或半固定结果
- 必要时把结果写回作用域
- 不稳定时退化为 `any`

例如：

- `typeof x` -> 固定 `string`
- `x instanceof Foo` -> 固定 `boolean`
- `a in b` -> 固定 `boolean`
- `text output` / `gen-node` -> 固定 `void`
- `await x` -> 先不解包 Promise，直接透传 `x` 的类型
- `new T(...)` -> 先取 callee 上声明的类型，取不到就 `any`

这类节点之所以适合当前阶段补齐，就是因为它们不需要引入新的全局算法，只要把局部语义写清楚即可。

## 典型示例表

下面给出一组“输入形式 -> 推导结果”的速查示例。它们不是完整语法手册，而是用于帮助理解当前实现的实际行为边界。

### 1. 基础表达式

| 输入 | 推导结果 | 说明 |
|---|---|---|
| `1` | `int` | 整数字面量 |
| `1L` | `long` | 长整数字面量 |
| `1.5` | `double` | 浮点字面量 |
| `"x"` | `string` | 字符串字面量 |
| `true` | `boolean` | 布尔字面量 |
| `null` | `null` | 空值字面量 |
| `-a` | `a` 的数值型或 `number` | 一元负号保守处理 |
| `typeof a` | `string` | 固定规则 |
| `a instanceof Foo` | `boolean` | 固定规则 |
| `(a, b, c)` | `c` 的类型 | 序列表达式返回最后一项 |

### 2. 变量声明与赋值

| 输入 | 推导结果 | 说明 |
|---|---|---|
| `let a = 1` | `a: int` | 由 initializer 推导 |
| `let a: long = 1` | `a: long` | 以显式类型为准，同时检查兼容性 |
| `let a` | `a: any` | 无类型、无 initializer 时退化 |
| `a = "x"` | `a: string` | 赋值会回写局部变量类型 |
| `a += 1` | 数值提升后类型 | 复合赋值复用二元运算规则 |
| `++a` | 原数值型或 `number` | 同时回写变量类型 |

### 3. 分支与缩窄

| 输入 | 分支内结果 | 分支外结果 |
|---|---|---|
| `if (x != null)` | `x` 可缩窄为去掉 `null` 后的类型 | 外层仍保留原 union |
| `if (typeof x == "string")` | `x: string` | 外层不直接改写 |
| `if (x instanceof Foo)` | `x: Foo` | 外层不直接改写 |
| `if (flag) { x = 1 } else { x = "a" }` | 各分支内分别为 `int` / `string` | `x: int|string` |
| `if (flag) { x = 1 }` | 真分支内 `x: int` | 与原类型保守合并 |

### 4. 容器与解构

| 输入 | 推导结果 | 说明 |
|---|---|---|
| `[1, 2, 3]` | `List<int>` | 同类元素 |
| `[1, "x"]` | `List<int|string>` | 元素类型合并 |
| `{a: 1, b: "x"}` | `Map<String, int|string>` | 不做精确 shape |
| `let [a, b] = [1, 2]` | `a: int`, `b: int` | 数组解构基础传播 |
| `let {name} = {name: "x", age: 1}` | `name: string|int` | 通过 `Map<String,V>` 保守传播 |
| `...items` | `items` 的推导结果 | spread 节点本身透传 argument |

### 5. 函数与泛型

| 输入 | 推导结果 | 说明 |
|---|---|---|
| `(x: int) => x + 1` | `(x:int)=>int` | 表达式体箭头函数 |
| `function f(x: int) { return x }` | `f: (x:int)=>int` | 从 return 汇总 |
| `identity<T>(x: T): T; identity("a")` | `string` | 单一类型变量替换 |
| `pair<T>(a: T, b: T): T; pair(1, "x")` | `int|string` | 多次绑定冲突时宽化 |
| `f(unknownArg)` | 已知签名则取返回类型，否则 `any` | 调用点保守传播 |

### 6. 成员访问与宿主反射

| 输入 | 推导结果 | 说明 |
|---|---|---|
| `list.length` | `int` | 内建规则 |
| `list.size` | `int` | 内建规则 |
| `map.size` | `int` | 内建规则 |
| `map[key]` / `map.valueLikeAccess` | `Map` 的 valueType 或 `any` | 保守处理 |
| `bean.name` | 反射得到的属性类型 | 先查 bean property，再查 field |
| `unknown.prop` | `any` | 查不到就退化 |

### 7. 低成本补齐节点

| 输入 | 推导结果 | 说明 |
|---|---|---|
| `` `hello ${x}` `` | `string` | 模板字符串整体固定为字符串 |
| `await x` | `x` 的当前类型 | 暂不做 Promise 解包 |
| `new Foo()` | `Foo` 的声明类型 | 取 callee 上的显式类型 |
| `a in b` | `boolean` | 固定规则 |
| `/a+/` | `IRegex` | 正则字面量 |
| `delete x` | `boolean` | 固定规则 |
| `text output` / `escape output` | `void` | 纯输出副作用 |
| `collect output` in `text` mode | `string` | 按 output mode 映射 |
| `collect output` in `node` mode | `XNode` | 按 output mode 映射 |
| `collect output` in `sql` mode | `SQL` | 按 output mode 映射 |
| `throw e` | `e` 的类型 | 当前实现透传 argument 类型 |
| `this` / `super` | `any` | 当前阶段保守退化 |
| `import foo from ...` | `foo: any` | 只登记到当前作用域 |
| `import * as ns from ...` | `ns: any` | 只登记到当前作用域 |

### 8. 典型退化案例

| 输入 | 当前结果 | 原因 |
|---|---|---|
| 复杂对象字面量带 computed key | `Map<String, any>` 或更宽类型 | 不做精确结构类型 |
| 无签名函数调用 | `any` | callee 无法确定返回类型 |
| 离开 `if` 后继续保留缩窄结果 | 不支持 | 缩窄只在分支子作用域内生效 |
| `await Promise<T>` 自动解包为 `T` | 不支持 | 当前未引入 async 类型系统 |
| `{name: string, age: int}` 精确对象 shape | 不支持 | 当前统一按 `Map<String,V>` 处理 |

## 容错与退化策略

XLang类型推导默认允许不完整信息存在。

常见退化策略包括：

- 推不出来时使用`any`
- 多类型冲突时使用`union`
- 对复杂宿主对象优先依赖反射信息
- 对尚未覆盖的复杂语法不引入激进错误推断

这保证了类型推导是“帮助开发”的，而不是“阻塞脚本”的。

## 测试策略

当前测试主要位于：

- `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compile/TestTypeInferenceProcessor.java`
- `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compile/TestGenericTypeInferencer.java`

测试重点覆盖：

- 字面量和基础表达式
- 数值运算和字符串拼接
- 变量声明和赋值
- if分支合并
- 数组/对象字面量
- 解构绑定
- 函数与箭头函数
- 轻量泛型返回替换
- 新补齐节点的一元、typeof、cast、sequence、await、for-of、for-in等场景

建议后续测试继续坚持以下原则：

- 以简单JUnit单测为主
- 优先覆盖高频语法路径
- 对每个新增节点至少增加一个正向案例和一个退化案例
- 不引入依赖复杂宿主环境的脆弱测试

## 后续演进建议

在保持“低复杂度、保守实用”目标不变的前提下，下一阶段建议按以下顺序推进：

### 1. 继续补齐低成本节点

优先考虑：

- `while/for`中的局部传播
- `try/catch`中的保守类型传播
- 更完整的`logical/null-coalescing`表达式结果推导

### 2. 收紧宽松规则

特别是容器成员访问规则，应逐步从“默认元素类型/值类型”改为“白名单特化 + 反射查询 + 保守退化”。

### 3. 稳定缩窄规则

把`typeof`、`instanceof`、`null`过滤、truthy规则继续做稳，而不是盲目扩展新语法。

### 4. 保持边界清晰

明确以下红线：

- 不升级为TypeScript级控制流求解器
- 不引入精确结构对象类型系统
- 不引入复杂的跨函数全局泛型约束系统

## 总结

XLang类型推导的核心思想可以概括为：

> 在脚本最常用的局部语法路径上，提供足够有用、足够保守、足够容易解释的类型信息。

它的价值不在于“最强”，而在于：

- 能覆盖日常脚本开发高频路径
- 能服务编辑器提示、静态检查和代码理解
- 不把实现复杂度推向不可维护的方向

这也是当前和未来一段时间内应当坚持的设计边界。
