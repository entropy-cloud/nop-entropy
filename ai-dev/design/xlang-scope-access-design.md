# XLang `$scope.x` 读写语法设计

**日期**：2026-06-25
**范围**：`nop-kernel/nop-xlang`
**状态**：active

---

## 一、设计结论

1. XLang 保持现有的裸标识符读取规则：`x` 先解析局部变量，再回退到 scope 变量；若 scope 中不存在则报错。
2. 新增并推荐显式 scope 访问语法：`$scope.x` 读取 scope 变量，`$scope.x = expr` 写入 scope 变量。
3. `assign(name, value)` 保留作为动态变量名写入的补充接口，不再作为主推荐写法。
4. 不引入把 `var` 或 `global` 重新解释为 scope 写入的语法。
5. 该能力可以通过局部改动实现，不需要新增 grammar 规则。

## 二、背景与动机

当前 XLang 已有两套相关语义：

- 裸标识符读取时，若不是局部变量，会回退到 scope 查找。
- 写入 scope 变量主要依赖 `assign(name, value)`。

这导致读写风格不对称：

```js
x
assign("x", 1)
```

对人类开发者来说，这需要额外记忆“读是变量语法，写是 helper 函数”。对 AI 来说，`assign("x", 1)` 也比对象属性赋值更难自然产出，因为它引入了额外的字符串层和特殊 helper 约定。

而系统变量已经采用 `$` 前缀约定，且 `ExprConstants.SYS_VAR_SCOPE = "$scope"` 已经存在。因此，把 scope 显式暴露为 `$scope` 对象，并允许 `$scope.x` / `$scope.x = expr`，能在不改变现有读取心智的前提下补上最自然的写法。

## 三、核心设计

### 3.1 语义约定

保留现有语义：

```js
x      // 读：局部优先，否则从 scope 读取；不存在则报错
```

新增显式语义：

```js
$scope.x      // 显式从 scope 读取变量 x
$scope.x = 3  // 显式写入 scope 变量 x
```

推荐的整体心智模型：

- `let` / 局部变量：脚本内部临时绑定
- `x`：按现有规则读取值
- `$scope.x`：显式访问外部 scope 变量
- `assign(name, value)`：仅用于变量名动态计算的场景

### 3.2 为什么不是 `var`

不把 `var` 解释为 scope 级别变量，原因是：

- 主流语言中 `var` 的默认心智是“声明局部变量”
- XLang/XPL 中已经存在 `var` 作为绑定名的用法，例如 `<c:for var="item">`
- 若把 `var x = 1` 解释为写入 scope，会和现有语言经验直接冲突
- 对 AI 来说，这种重载极易被误解成普通局部声明

### 3.3 为什么不是 `global`

不引入 `global` 关键字，原因是：

- `global` 容易被理解成进程级或模块级全局状态
- 当前语义实际上是“当前执行上下文的共享变量空间”，并不是真正 global
- 对 AI 来说，`global` 会触发 Python/JavaScript 的全局变量联想，预期过重

### 3.4 为什么不是继续只用 `assign`

`assign(name, value)` 可以保留，但不适合作为主语法：

- 它是 helper 函数，不是直接语法
- 变量名需要写成字符串，增加额外噪音
- 对补全、静态理解和模型生成都不如属性赋值直观

因此推荐关系应为：

1. 首选 `$scope.x = expr`
2. 动态变量名时使用 `assign(name, expr)`

### 3.5 与现有实现模型的关系

现有实现已经具备 scope 变量专用执行节点：

- `ScopeIdentifierExecutable`：按变量名从运行时 scope 读取，并在不存在时抛 `ERR_EXEC_SCOPE_VAR_IS_UNDEFINED`
- `ScopeAssignExecutable`：按变量名写入运行时 scope

同时，编译期也已有 scope 变量定义与标识：

- `ExprConstants.SYS_VAR_SCOPE = "$scope"`
- `IdentifierKind.SCOPE_VAR_REF`
- `ScopeVarDefinition`
- `XLangCompileScope.resolveScopeVarDefinition(...)`

因此本设计不是新增一种全新的运行时能力，而是把已有 scope 读写能力，通过成员访问语法显式暴露给脚本作者。

## 四、局部改动点

### 4.1 不需要改 grammar

现有 parser 已支持：

- `MemberExpression_dot2Context` 对应 `obj.prop`
- `AssignmentExpression` 的左值允许 `MemberExpression`

因此 `$scope.x` 与 `$scope.x = 3` 在语法树层面已经能被解析成：

- `MemberExpression(object=$scope, property=x)`
- `AssignmentExpression(left=MemberExpression(...), right=...)`

结论：**无需修改 ANTLR grammar、生成 visitor 或 AST 结构。**

### 4.2 主要改动：`BuildExecutableProcessor`

核心局部改动点：

- `processMemberExpression(MemberExpression node, IXLangCompileScope context)`
- `buildMemberAssign(XLangASTNode node, MemberExpression left, XLangOperator operator, IExecutableExpression expr, IXLangCompileScope context)`

当前默认逻辑会把 `obj.prop` 编译为普通对象属性访问：

- 读取走 `GetPropertyExecutable`
- 写入走 `SetPropertyExecutable`

这对 `$scope.x` 不合适，因为 `$scope` 在语义上不是“把 IEvalScope 当 Java Bean 读属性 `x`”，而是“按名字 `x` 读写 scope 变量”。

因此这里需要增加一个 special case：

- 当 `MemberExpression.object` 是标识符 `$scope`
- 且该标识符已被解析为系统 scope 变量
- 且属性访问是非 computed 的简单标识符时

则编译为：

- 读取：`new ScopeIdentifierExecutable(loc, propName)`
- 写入：`new ScopeAssignExecutable(loc, propName, expr)`

这属于非常局部的 dispatch 分支，不影响普通 `obj.prop` 的行为。

### 4.3 类型推断需要同步一处

`TypeInferenceProcessor.processMemberExpression(...)` 当前把 `obj.prop` 统一当成对象成员类型推断。

若支持 `$scope.x`，建议补一条专门分支：

- 当 object 为 `$scope` 时，优先从 `XLangCompileScope.resolveScopeVarDefinition(propName, ...)` 获取 `ScopeVarDefinition`
- 若存在定义，则返回该 scope 变量声明的类型
- 否则回退为 `ANY`

这样 `$scope.x` 的类型体验可以与裸变量 `x` 保持一致或接近，而不是一律退化为普通 `IEvalScope` 成员推断。

### 4.4 测试与文档同步

建议补充 focused tests，至少覆盖：

1. `$scope.x` 读取已有 scope 变量
2. `$scope.x` 读取不存在变量时报错
3. `$scope.x = 3` 写入后，后续裸变量 `x` 可直接读到 `3`
4. `assign("x", 3)` 与 `$scope.x = 3` 语义一致
5. 普通 `obj.x` 仍然走对象属性访问，不受影响

如需打印/调试输出更友好，可同步检查 `XLangExpressionPrinter` 是否需要额外调整；但这不是能力实现的主阻塞点。

## 五、拒绝了什么

### 5.1 拒绝：`x = 3` 自动写 scope

不采用该方案，原因是赋值语义会变得歧义：

- 是修改局部变量？
- 还是局部不存在时自动创建 scope 变量？
- 还是始终写 scope？

对 AI 来说，这种规则最容易误判。

### 5.2 拒绝：`var x = 3` 代表 scope 变量

不采用该方案，原因是它与主流语言及现有 XLang/XPL 使用习惯冲突最大。

### 5.3 拒绝：只保留 `assign(name, value)`

不采用该方案，原因是它不是最直观、最少认知负担的主语法。

## 六、与已有设计的关系

- 与现有裸标识符读取规则兼容：本设计不改变 `x` 的现有读取语义
- 与系统变量前缀约定兼容：`$scope` 已是既有系统变量名
- 与现有 helper 兼容：`assign(name, value)` 保留作为动态变量名写入接口

相关实现锚点：

- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/ExprConstants.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/LexicalScopeAnalysis.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/BuildExecutableProcessor.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/ScopeIdentifierExecutable.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/ScopeAssignExecutable.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java`
