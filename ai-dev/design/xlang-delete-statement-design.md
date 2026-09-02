# XLang `delete` 语句设计与实现（XScript 侧）

**日期**：2026-09-02
**范围**：`nop-kernel/nop-xlang`（语法/编译前端/解释器执行节点）、下游 `nop-xlang-java`（转译器映射——追加任务）
**状态**：草案（plan-first，待 plan audit）

---

## 一、设计结论

1. 在 XScript 中新增 `delete <memberExpression>` 一元表达式（**沿用 JavaScript 语义**：返回 boolean；`true` 表示属性存在并被删除，`false` 表示属性不存在或删除无效）。
2. 保持 `delete` 在语法上是 **expression**（不是 statement），与 JavaScript 一致——可以出现在 `if`、三元、函数实参、`console.log(...)` 等所有表达式上下文。
3. **支持的被操作对象类型**（与 `Map`/`Bean`/`List`/`$scope` 对应语义）：
   - `Map<K,V>` → `map.remove(key)`，旧值非 null → true（**唯一走"删除条目"语义**）
   - Java Bean（普通属性 + 扩展属性）→ 统一走 "setter 设为 null + 检查原值" 语义（**清空值，不删除 key**）；不允许 null 的字段（如基本类型）抛 `ERR_EXEC_DELETE_NOT_SUPPORTED`
   - `List<T>` → attr 为 `Integer` 走 `list.remove(intIndex)`（**删除条目，长度减 1**）；attr 为其他走 `list.remove(object)`（按值首次匹配删除）
   - `IEvalScope`（即 `$scope`）→ `scope.removeLocalValue(name)`，旧值非 null → true（**仅影响当前帧**）
   - 数组不支持（不可变长），抛 `ERR_EXEC_DELETE_ON_ARRAY`

   **统一取舍**：仅 `Map` 与 `List` 走"删除条目"语义（因为这两个容器类型有原生的 `remove` API）；`Bean` 一律 `set null`（Java 对象没有"删除 key"的概念，硬要反射删除字段会破坏类不变量）；`$scope` 通过 `removeLocalValue` 提供当前帧隔离删除。
4. **不允许** `delete <bareIdentifier>`（裸标识符）—— 与 XLang 现有的"局部变量→scope 变量→错误"读取规则冲突，编译期报错 `ERR_XLANG_DELETE_NOT_MEMBER_EXPR`（由 TypeInferenceProcessor 校验可达；grammar 故意放松为 `Delete expression_single`，让错误码在语义层落地）。
5. **不允许**链式 `delete a.b.c` —— 只接受单级 `MemberExpression` 作为 argument，编译期报错 `ERR_XLANG_DELETE_NOT_SINGLE_LEVEL`，避免与 JavaScript 的"沿原型链删除"歧义。
6. **不支持** `delete obj?.x`（OptionalDot）—— JavaScript 自身也不支持 `delete obj?.x`（`?.` 是 ES2020，可选链；与 delete 组合在 JS 中也是语法错误），与既有 `memberExpression` rule 不接受 `?.` 的现状一致。设计层面无需扩 grammar。
7. AST/编译/执行复用现有 `DeleteStatement`（已存在但未启用），不新增 AST 节点；新增 3 个 Executable 节点：`DeletePropertyExecutable`、`DeleteAttrExecutable`、`DeleteScopeVarExecutable`。
8. grammar 改动：将 `nop-kernel/nop-xlang/model/antlr/XLangParser.g4` 的 `// | Delete memberExpression   # DeleteExpression` 改为 `| Delete expression_single # DeleteExpression`（**删除注释同时改 `memberExpression` 为 `expression_single`**），让所有编译期校验在语义层可达。

## 二、背景与动机

### 2.1 当前痛点

XLang 当前在 XScript 中缺少通用的"删除"语义。开发者被迫使用以下替代：

- **删除 Map 的 key**：`map.remove(key)` —— 通过 XLang 的 CallExpression 调用 `java.util.Map.remove` 即可，但语义不直观（"调用 remove 方法"读起来不像删除）。
- **从 scope 移除变量**：用 `assign(name, null)` 或把 scope 当 Map 调用 `scope.remove(name)` —— 但 `$scope` 在 XScript 里不是普通 Map，不能直接 `.remove()`。
- **从 List 删除元素**：`list.remove(index)` / `list.remove(obj)` —— 同样以"方法调用"形式存在。
- **删除 Bean 扩展属性**：无任何内建机制，只能调用 `beanModel.removeProperty(...)` 或反射修改内部 ext map。

这些替代写法对人类开发者不友好，对 AI 生成脚本更不友好：AI 在面对 `delete obj.x` 的常见需求时，要么写出错误的 `obj.x = undefined`（XLang 无 undefined），要么写出 `delete obj.x` 后依赖运行时容错。

### 2.2 既有"骨架"未启用

代码考古发现以下残留：

- `nop-kernel/nop-xlang/.../ast/DeleteStatement.java` + `_DeleteStatement.java` —— AST 节点已 codegen 完成
- `nop-kernel/nop-xlang/.../compile/BuildExecutableProcessor.java:803-805` —— `processDeleteStatement` 仅 stub，调用 `super.processDeleteStatement`（默认实现返回 null，会导致 `NullPointerException` 在执行期炸出，但目前 grammar 不接受 `delete`，所以不会被触发）
- `nop-kernel/nop-xlang/.../compile/XLangASTOptimizer.java:1004` —— `optimizeDeleteStatement` 存在但 `argument` 仅做 validate 后返回
- `nop-kernel/nop-xlang/.../compile/TypeInferenceProcessor.java:486` —— `processDeleteStatement` 类型推断 stub
- `nop-kernel/nop-xlang/.../ast/print/XLangExpressionPrinter.java:363` —— `visitDeleteStatement` 默认空实现
- `nop-kernel/nop-xlang/.../ast/XLangASTVisitor.java:114` —— visitor 框架已预留
- `nop-kernel/nop-xlang/.../expr/ExprConstants.java:39` —— `KEY_DELETE = "delete"` 常量已定义
- `nop-kernel/nop-xlang/model/antlr/XLangLexer.g4:133` —— `Delete: 'delete'` 关键字已定义（保留字）
- `nop-kernel/nop-xlang/model/antlr/XLangParser.g4:542` —— `DeleteExpression` 被注释掉（`//    | Delete memberExpression   # DeleteExpression`）

**结论**：骨架基本完整（AST/visitor/lexer/keyword/optim/print/type-stub 全到位），只是 grammar 接受后 BuildExecutableProcessor 没有实际产出。本次任务的核心是"接通最后一公里"——打开 grammar、写 executable、补 type inference。

### 2.3 与 JavaScript `delete` 操作的兼容性

JavaScript 中 `delete` 是 expression（不是 statement），返回 boolean：

```javascript
delete obj.x    // true/false
delete obj["x"] // true/false
delete x        // true（仅在非严格模式；严格模式抛错）
```

XLang 在语法层面遵循 ECMA-262 风格（见 `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 第 113 行 "XLang 脚本（JavaScript 兼容语法）"）。因此 `delete` 在 XLang 中也应是 **expression**，不是 statement，**返回值语义沿用 JS**。

## 三、核心设计

### 3.1 语法形式

**XScript 表达式**（在 `expression_single` 中允许）：

```javascript
delete obj.x              // 删除 obj 的属性 x（不计算 x）
delete obj["key"]         // 删除 obj 的 key（动态 key）
delete map.userId         // Map.remove("userId")
delete map["userId"]      // Map.remove("userId")
delete list[2]            // List.remove(2)  按索引
delete list["tom"]        // List.remove("tom") 按对象值（attr 是非整数）
delete $scope.temp        // 删除 scope 变量 temp
delete $scope["tmp_1"]    // 同上，computed 形式
```

返回 `boolean`：
- `true` = 属性/key 存在且被删除
- `false` = 属性/key 不存在，或对象类型不支持该操作（如 Bean 上不存在 setter 且无扩展属性通道）

允许出现的上下文：
- `let x = delete obj.a;` （赋值）
- `if (delete obj.a) { ... }` （条件）
- `console.log(delete obj.a);` （实参）
- 三元、链式、return —— 任意 expression_single 上下文

### 3.2 操作对象类型的语义

| 对象类型 | 删除路径 | 返回值语义 |
|----------|----------|----------|
| `java.util.Map<K,V>` | `((Map) obj).remove(key)` | `remove` 旧值 `!= null` 即 true（注意：旧值为 null 时 Map 仍然可能包含此 key —— 但 XLang 弱类型语义下无法区分 "containsKey=true, value=null" 和 "containsKey=false"，因此退化为"旧值非 null 即 true"） |
| Java Bean（普通属性 + 扩展属性） | 1. 普通 setter 路径：`getter` 读旧值 → `setter(null)`；2. 扩展属性路径：`beanModel.setExtProperty(bean, name, null)`（前提 `isAllowSetExtProperty()`）；3. 两条路径都不存在时抛 `ERR_EXEC_DELETE_NOT_SUPPORTED` | 旧值 `!= null` 即 true（**无论走哪条路径**——因为扩展属性也是"清空值"语义，不删除 key） |
| `List<T>` | attr 为 `Integer` → `list.remove(intIndex)`；attr 为其他 → `list.remove(object)` | 返回 `remove` 结果的 boolean（`List.remove` 本身就返回 boolean） |
| `IEvalScope` | `scope.removeLocalValue(name)` | 旧值（被移除前 `scope.getValue(name)`）`!= null` 即 true |
| 数组（`T[]`） | — | 抛 `ERR_EXEC_DELETE_ON_ARRAY`（XLang 决策：数组长度不可变，不暴露"删除元素"语义；需要时转 List） |
| 其他 | — | 抛 `ERR_EXEC_DELETE_NOT_SUPPORTED`（带 class 信息） |

### 3.3 不允许的语义

- **禁止 `delete x`**（裸标识符）—— 编译期报错 `ERR_XLANG_DELETE_NOT_MEMBER_EXPR`。
  - 理由：XLang 中 `x` 的语义是"局部优先 → scope → 错误"，删除局部变量无法表达（局部变量是栈帧级别的，无法运行时移除）；删除 scope 变量应使用 `delete $scope.x` 显式。
  - 不引入"自动判断是删局部还是 scope"的歧义规则。
- **禁止链式 `delete a.b.c`** —— 编译期报错 `ERR_XLANG_DELETE_NOT_SINGLE_LEVEL`。
  - 理由：与 JavaScript 不同，XLang 不暴露"沿原型链查找"概念，单级语义更易静态检查和类型推断。
- **禁止 `delete this.x`、`delete super.x`** —— `this`/`super` 已在 `expression_single` 中作为单 token 处理，与 MemberExpression 结构不兼容，编译期报错。
- **禁止删除 class 静态字段**（`delete MyClass.FOO`）—— Java 反射无法移除静态字段；编译期检测到 object 为 ImportClassRef 时报错。

### 3.4 与现有执行链的关系

执行链：`AST (DeleteStatement) → BuildExecutableProcessor.processDeleteStatement → IExecutableExpression → 解释器 execute`

`processDeleteStatement(node, context)` 的实现策略：

```text
argument 必须是 MemberExpression（编译期保证）
  1. argument.object 是 $scope 标识符（isScopeVarAccess）
     → DeleteScopeVarExecutable(loc, propName / attrExpr)
  2. argument.computed == false
     → DeletePropertyExecutable(loc, ownerExpr, propName)
  3. argument.computed == true
     → DeleteAttrExecutable(loc, ownerExpr, attrExpr)
```

`XLangSemantics.delete*` 静态方法作为生成代码入口（与现有 `setProp`/`setAttr`/`setScopeValue` 模式对称），便于 `nop-xlang-java` 转译器直接调用。

### 3.5 错误码（新增）

| 错误码 | 含义 |
|--------|------|
| `ERR_XLANG_DELETE_NOT_MEMBER_EXPR` | `delete` 后面不是 MemberExpression（裸标识符/字面量/调用） |
| `ERR_XLANG_DELETE_NOT_SINGLE_LEVEL` | `delete a.b.c` 链式（argument 是 MemberExpression 但其 object 也是 MemberExpression） |
| `ERR_XLANG_DELETE_ON_CLASS_REF` | `delete MyClass.field` —— 不支持删除静态字段 |
| `ERR_EXEC_DELETE_ON_NULL_OBJ` | 被操作对象求值为 null（与 setProp 的 `ERR_EXEC_WRITE_PROP_OBJ_NULL` 风格一致） |
| `ERR_EXEC_DELETE_ON_ARRAY` | 数组不支持 delete 操作 |
| `ERR_EXEC_DELETE_NOT_SUPPORTED` | 对象类型不支持 delete（带 className 参数） |
| `ERR_EXEC_DELETE_ATTR_EXPR_RETURN_NULL` | `delete obj[expr]` 中 expr 求值为 null（与 `ERR_EXEC_READ_ATTR_EXPR_RETURN_NULL` 风格一致；map-like 仍允许 null key 移除路径） |

## 四、AST 与 Executable 节点

### 4.1 复用现有 `DeleteStatement`

不新增 AST 节点。现有结构：

```text
DeleteStatement
  └─ argument: Expression   // 必须是 MemberExpression（编译期约束）
```

`XLangASTBuilder.delete(loc, MemberExpression)` 工厂方法新增，模仿 `XLangASTBuilder.let(...)` 的 API 风格。

### 4.2 新增 Executable 节点

| 类 | 职责 | 关键方法 |
|----|------|----------|
| `DeletePropertyExecutable extends AbstractExecutable` | `delete obj.prop` —— non-computed 属性删除 | `execute` 走 `XLangSemantics.deleteProperty(loc, display, propName, obj, scope)` |
| `DeleteAttrExecutable extends AbstractExecutable` | `delete obj[key]` —— computed 删除 | `execute` 走 `XLangSemantics.deleteAttr(loc, display, attrExpr, obj, scope)` |
| `DeleteScopeVarExecutable extends AbstractExecutable` | `delete $scope.x` / `delete $scope["x"]` | `execute` 走 `XLangSemantics.deleteScopeValue(loc, scope, varName)` |

### 4.3 `XLangSemantics` 新增静态方法

```text
// 三个入口方法签名（伪代码）
Object deleteProperty(SourceLocation loc, String display, String propName, Object obj, IEvalScope scope)
Object deleteAttr      (SourceLocation loc, String display, IExecutableExpression attrExpr, Object obj, IEvalScope scope)
Object deleteScopeValue(SourceLocation loc, IEvalScope scope, String varName)
```

每个方法内部按 §3.2 表分派：
1. null 检查（`ERR_EXEC_DELETE_ON_NULL_OBJ`）
2. 类型分派（Map / Bean / List / Scope / 数组 / 其他）
3. 调用对应底层 API（`Map.remove` / `beanModel.removeProperty` / `List.remove` / `scope.removeLocalValue`）
4. 返回 boolean

### 4.4 `TypeInferenceProcessor.processDeleteStatement`

充实实现：`DeleteStatement` 类型推断结果恒为 `boolean`。

## 五、ANTLR Grammar 改动

### 5.1 最小化改动

**只改一处**：`nop-kernel/nop-xlang/model/antlr/XLangParser.g4:542`

```diff
- //    | Delete memberExpression   # DeleteExpression
+     | Delete expression_single   # DeleteExpression
```

理由：
- 关键字已存在（`XLangLexer.g4:133`），无需改 lexer
- 故意把 `memberExpression` 改为 `expression_single`：让所有 `delete <something>` 都能被解析，再由 TypeInferenceProcessor 在语义层校验 `argument` 必须是 `MemberExpression`——确保三个编译期错误码（裸标识符 / 链式 / class ref）可达
- 保留 `delete` 在 `expression_single` 内的优先级不变

**不建议**：把 `delete` 提升为 statement（`deleteStatement : Delete expression_single eos__`）—— 这会破坏"delete 作为 expression 出现在 if/三目/链式调用中"的用法。

### 5.2 AST 构造函数同步更新

`_XLangASTBuildVisitor.java` 自动生成部分由 ANTLR codegen 负责。手动实现层（`XLangASTBuilder`）新增：

```text
public static DeleteStatement delete(SourceLocation loc, MemberExpression argument)
```

`_XLangASTBuildVisitor` 中需要新增 `visitDeleteExpression(DeleteExpressionContext)`，把 parse tree 转成 `DeleteStatement.valueOf(loc, MemberExpression)`。**这是 codegen 范围**（`nop-antlr4/nop-antlr4-tool`），需要在工具链调用时一并重新生成。

## 六、语义对齐与执行链细节

### 6.1 Bean 删除的语义（统一为 set null）

**设计决策**（用户 2026-09-02 裁定）：Java Bean 的"删除"语义统一为"set null"，**不区分普通属性与扩展属性**。
- `Map` 是唯一走 `remove`（删除条目）语义的容器（因为它有原生 remove API 且语义清晰）
- `Bean` 一律 `set null`（"清空值"，不是"删除 key"）——Java 对象没有"删除字段"概念，硬要反射删除字段会破坏类不变量（final 字段、字段数固定等）
- `List` 走 `remove(int)` / `remove(object)`（删除条目，长度减 1）

**实现路径**（`XLangSemantics.deleteProperty` 内）：

```text
1. null 检查 → ERR_EXEC_DELETE_ON_NULL_OBJ
2. 数组 → 抛 ERR_EXEC_DELETE_ON_ARRAY
3. Map-like → ((Map) obj).remove(propName)；旧值非 null 即 true
4. Bean 普通属性路径：getter 读旧值 → setter(null)
   → 若不存在 setter 或 setter 拒绝 null → 抛 ERR_EXEC_DELETE_NOT_SUPPORTED
5. Bean 扩展属性路径（普通属性失败时尝试）：beanModel.setExtProperty(obj, name, null)
   → 若 isAllowSetExtProperty == false → 抛 ERR_EXEC_DELETE_NOT_SUPPORTED
6. 其他 → 抛 ERR_EXEC_DELETE_NOT_SUPPORTED
```

**为什么扩展属性也是 `set null` 而非"删除 key"**：
- `IBeanModel` 当前**没有** `removeExtProperty` 接口（避免引入 nop-core 跨模块改动）
- 现有 `setExtProperty(null)` 已能实现"清空值"语义
- Bean 调用方无法通过 `Map`-style 的"key 是否被删除"判断 Bean 属性是否仍存在（Bean 总有字段），所以"清空"与"删除"对 Bean 调用方不可观测差异

### 6.2 IEvalScope 删除的语义边界

`IEvalScope.removeLocalValue(name)` 只在**当前 scope 帧**中删除变量；如果同名变量存在于父 scope，删除后子 scope 重新读取会命中父 scope 的值。

XLang 的设计选择：
- **不递归向上查找并删除**：避免破坏 scope 层级（父 scope 可能属于调用方，不属于当前脚本作者能控制的边界）
- **不返回 boolean 与"是否真删除了"**：因为在多层 scope 下"删除"与"遮蔽"语义不同——当前实现 `removeLocalValue` 只影响当前帧

返回值定义为"被移除值是否非 null"，与 Map/Bean 语义对称，**不**等价于 JavaScript `delete` 在多层 scope 时的"是否真删除了可见条目"。

### 6.3 List 删除的索引 vs 值分派

```javascript
delete list[2]            // Integer → list.remove(2)  按索引
delete list["tom"]       // 非 Integer → list.remove("tom") 按对象值
```

分派判据：执行期检查 `attr instanceof Integer`。`List.remove(int)` 与 `List.remove(Object)` 在 JDK 中**是两个不同的重载**——前者总是按索引，后者总是按对象值（即使对象自身是 Integer 也按 .equals 匹配）。

**注意点**：
- `delete list[0]` 与 `delete list[0L]` 不同：0 是 Integer，按索引；0L 是 Long，按对象值
- `delete list[-1]` 不抛错：`List.remove(-1)` 抛 `IndexOutOfBoundsException`，透传（不包成 XLang 错误码——保留 Java 标准异常）
- 空 List 上 `delete list[0]`：同上，透传 `IndexOutOfBoundsException`

### 6.4 null attr 与 null obj 的处理

| 情形 | 行为 |
|------|------|
| `obj == null`，prop/key 任意 | 抛 `ERR_EXEC_DELETE_ON_NULL_OBJ`（与 `SetPropertyExecutable` 一致） |
| `obj != null`，key 求值为 null，obj 是 Map | 调用 `map.remove(null)` —— Java `HashMap` 允许 null key，行为按 JDK 语义；返回"旧值非 null 即 true" |
| `obj != null`，key 求值为 null，obj 是 Bean | 抛 `ERR_EXEC_DELETE_ATTR_EXPR_RETURN_NULL`（bean attr 名不能为 null） |
| `obj != null`，key 求值为 null，obj 是 List | 抛 `ERR_EXEC_DELETE_ATTR_EXPR_RETURN_NULL`（List attr 不能为 null） |

### 6.5 Bean 普通属性 setter 拒绝 null 时的行为

`setter.setProperty(bean, name, null, scope)` 在以下情形可能抛错或拒绝：
- 字段是基本类型（如 `int age`）：setter 内部可能做 null 检查并抛 NullPointerException
- 字段是带 `@NonNull` 注解的自定义类型：setter 可能抛 IllegalArgumentException

**XLang 设计**：透传 Java 异常（不包成 XLang 错误码）。理由：保留 Java 标准异常语义便于脚本作者诊断；包成 XLang 错误码反而掩盖了字段约束信息。

## 七、多后端影响

| 后端 | 影响 |
|------|------|
| **解释器**（`nop-xlang`） | 完整实现：ANTLR 改动 + BuildExecutableProcessor.processDeleteStatement 充实 + 3 个新 Executable + 错误码 + 测试 |
| **nop-xlang-java**（转译器） | 后续任务：把 `DeleteStatement`/`Delete*Executable` 映射为转译输出；当前 java 后端对 statement 类型覆盖范围仍有限，本次任务**不阻塞 java 后端落地**——执行期降级到解释器即可，runtime 观测已有降级机制（见 `ai-dev/design/xlang-execution/01-architecture-baseline.md` 降级观测） |
| **nop-xlang-truffle** | 解释器语义即正确性基线；truffle 后端遵循对拍不变式自动同步 |
| **native image** | 同 java 后端，转译器未覆盖时降级解释器，行为一致 |

本设计**不阻塞**多后端落地。优先级是"解释器侧语义接通、测试通过、错误码完整"。

## 八、测试策略

补充 focused tests，至少覆盖：

1. **Map 路径**：`delete map.userId` 后 `containsKey == false`，原值非 null → 返回 true；原值为 null → 返回 false
2. **Map dynamic key**：`delete map[computeKey()]` 等价于 `map.remove(computeKey())`
3. **Bean 路径**：普通 setter 可写属性的删除语义（删除 = set null，原值非 null → true）
4. **Bean 扩展属性路径**：`isAllowSetExtProperty` 开启时，`delete bean.extField` 走 `setExtProperty(null)`，扩展 map 中该 key 的 value变为 null（key 仍存在）
5. **Bean 不支持路径**：bean 既无 setter 也不允许扩展属性 → 抛 `ERR_EXEC_DELETE_NOT_SUPPORTED`
6. **List 按索引**：`delete list[2]` 后 size 减 1，索引 2 元素被移除
7. **List 按对象**：`delete list["tom"]` 删除第一次出现的 "tom"
8. **List 越界**：`delete list[100]`（size=5）→ 透传 `IndexOutOfBoundsException`
9. **Scope 路径**：`delete $scope.temp` 后 `scope.containsValue("temp") == false`
10. **不存在 key**：`delete obj.missingKey` 返回 false，不抛错
11. **null 对象**：`delete nullObj.x` 抛 `ERR_EXEC_DELETE_ON_NULL_OBJ`
12. **null attr on Bean**：`delete bean[null]` 抛 `ERR_EXEC_DELETE_ATTR_EXPR_RETURN_NULL`
13. **null attr on Map**：`delete map[null]` 走 `map.remove(null)`，不抛错
14. **数组错误**：`delete arr[0]` 抛 `ERR_EXEC_DELETE_ON_ARRAY`
15. **裸标识符错误**：`delete x` 编译期报错 `ERR_XLANG_DELETE_NOT_MEMBER_EXPR`
16. **链式错误**：`delete a.b.c` 编译期报错 `ERR_XLANG_DELETE_NOT_SINGLE_LEVEL`
17. **class ref 错误**：`delete MyClass.FIELD` 编译期报错 `ERR_XLANG_DELETE_ON_CLASS_REF`
18. **expression 上下文**：在 `if`、`let x = ...`、`console.log(...)` 三类上下文中 delete 均合法
19. **打印/源码位置**：`XLangExpressionPrinter` 正确输出 `delete obj.x`

## 九、拒绝了什么

### 9.1 拒绝：支持 `delete <bareIdentifier>`

不采用，原因：
- 局部变量是栈帧级绑定，无法在解释执行期移除（Java 字节码层面无对应操作）
- 即便是 scope 变量，显式 `$scope.x` 形式让"删除的是哪一级"无歧义
- 引入"自动判别局部/scope"会与现有"局部优先 → scope → 错误"的读取规则冲突（删除和读取语义不对称会引入歧义）

### 9.2 拒绝：支持 `delete a.b.c` 链式

不采用，原因：
- JavaScript 的链式 delete 在运行时沿原型链查找删除第一个匹配属性，XLang 无原型链语义
- 单级语义更易静态分析和类型推断（`obj.b` 删除后 `obj.c` 仍可访问，类型不会被传染）
- 转译器侧只需生成单级 `__delete_prop(obj, "b")`，无需递归展开

### 9.3 拒绝：把 delete 提升为 statement（而非 expression）

不采用，原因：
- JavaScript 中 delete 是 expression（出现在 if/三目/函数实参中都合法）
- XLang 语法遵循 ECMA-262 风格，保持对齐
- statement 形式会引入"if/while/for 中如何写 delete"的额外约束

### 9.4 拒绝：递归向上删除 scope 变量

不采用，原因：
- `IEvalScope.removeLocalValue` 只影响当前帧，向上删除会破坏 scope 层级不变量
- 父 scope 可能属于调用方，跨边界删除会引入不可控副作用

### 9.5 拒绝：把 `delete` 实现为 `assign(name, undefined)` 的反向（"赋 null"）

不采用，原因：
- XLang 无 `undefined` 概念（参考 `ai-dev/design/xlang-scope-access-design.md` §3.4 "为什么不是继续只用 `assign`"）
- 删除"应该消失"和"设为 null"语义不同：Map 区分 containsKey true/false；Bean 区分属性存在与否；List 区分 size 变化
- "设为 null" 是 `obj.x = null` 已经能表达的，不需要 `delete`

**特别澄清**（用户 2026-09-02 裁定）：本设计对 Bean 类型统一采用 "set null" 语义，**不**意味着 `delete` 是 `assign` 的反向——`delete` 的语义**至少**包含"返回 boolean 表示是否存在并被删除"，这是 `assign` 不具备的。Map / List / $scope 仍走"删除条目"语义；仅 Bean 因 Java 类型系统约束退化为 set null。

### 9.6 拒绝：在 XPL 中同步新增 `<c:delete>` 标签

不采用，原因：
- 用户明确表达范围为 XScript 中 delete 语句
- XPL 标签可作为后续独立 task 推进（`<c:assign>` 的对称镜像）；如需批量删除多字段，XScript 中多次 `delete obj.x` 已能覆盖
- 集中精力让 XScript 路径的语义与测试完整，比同时铺两条入口更稳妥

### 9.7 拒绝：Java 反射删除静态字段 / 私有 final 字段

不采用，原因：
- 静态字段、final 字段在 Java 反射层面都不允许删除/重写
- 与现有 `StaticGetterGetPropertyExecutable` 对静态字段的态度一致（仅可读不可写）

### 9.8 拒绝：在 List 上额外提供区间删除（如 `delete list[i..j]`）

不采用，原因：
- 一次只删一个元素/属性，简化语义
- 区间删除是 List 上的业务操作，不是语言级 delete 的范畴；用户可写循环逐个删除

## 十、与已有设计的关系

| 已有设计 | 关系 |
|----------|------|
| `ai-dev/design/xlang-scope-access-design.md` | 同样是对 XLang 语义的小幅增强；本设计**复用** `$scope` 识别逻辑（`isScopeVarAccess`），不修改 `$scope.x = expr` 写入语义 |
| `ai-dev/design/xlang-execution/01-architecture-baseline.md` §降级观测 | java 后端转译器未覆盖 DeleteStatement 时降级解释器，已有的降级观测机制可直接复用 |
| `XLangOperator` 枚举 | 本设计**不新增 operator**（delete 不参与运算），保持 XLangOperator 的语义纯度 |
| `ExprConstants.KEY_DELETE` | 复用（已定义） |

实现锚点（计划实施时修改的文件）：

- `nop-kernel/nop-xlang/model/antlr/XLangParser.g4` —— 解除 `DeleteExpression` 注释
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/XLangASTBuilder.java` —— 新增 `delete(loc, MemberExpression)` 工厂
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/BuildExecutableProcessor.java` —— 充实 `processDeleteStatement`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/` —— 新增 3 个 Executable 类
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/XLangSemantics.java` —— 新增 3 个静态方法
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java` —— 充实 `processDeleteStatement`（返回 boolean）
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangErrors.java` —— 新增 7 个错误码
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/parse/_XLangASTBuildVisitor.java` —— codegen 后新增 `visitDeleteExpression`

## 十一、后续工作（不在本设计范围内）

1. **XPL 标签**（如 `<c:delete>`）—— 与 `<c:assign>` 对称的批量删除标签；如未来有需求，作为独立 task 推进。
2. **nop-xlang-java 转译器**：把 `DeleteStatement`/`Delete*Executable` 映射到生成代码；当前转译器对 statement 类型覆盖范围仍有限，应作为独立 plan 推进。
3. **truffle 后端同步**：解释器语义确定后，truffle 侧的 polyglot 映射可对拍落地。
4. **owner 文档同步**：实施完成后同步到 `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 的"最常见的基础语法"章节，新增运行示例段落。