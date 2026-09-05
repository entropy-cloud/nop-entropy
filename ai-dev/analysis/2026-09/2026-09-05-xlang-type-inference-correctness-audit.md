# XLang 类型推导算法完整性与正确性审计

> Status: open
> Date: 2026-09-05
> Scope: nop-kernel/nop-xlang（TypeInferenceProcessor、GenericTypeInferencer、UnionTypeNarrower、TypeInferenceState 及对应测试）
> Conclusion: 算法可用但不完整——存在多个未覆盖的 AST 分发分支（switch/class/for-range 等整棵子树被跳过）、控制流合并不健壮（block/循环内的赋值丢失）、错误结果从不对外暴露；建议先修复集成与 SwitchStatement 两个 P1 缺陷，再按 §6 清单补测试。

## 1. Context

- 入口：`XLangExprParser.buildExecutable()` 在 `CFG_XLANG_TYPE_INFERENCE_ENABLED` 开启时执行
  `new TypeInferenceProcessor().processAST(expr, new TypeInferenceState())`。
- 涉及文件：
  - `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java`（1689 行，AST 分发 + 控制流合并）
  - `.../GenericTypeInferencer.java`（泛型类型变量推导）
  - `.../UnionTypeNarrower.java`（if 条件窄化）
  - `.../TypeInferenceState.java`（作用域链）
- 测试：`TestTypeInferenceProcessor.java`（约 70 个用例，单节点为主）、`TestGenericTypeInferencer.java`（约 28 个用例）。

## 2. 总体结论

| 维度 | 评价 |
|---|---|
| 正确性（已覆盖路径） | 大体正确。字面量/二元/成员/泛型返回值推导等核心路径与测试一致 |
| 正确性（控制流） | 有实质缺陷：block 作用域丢弃赋值、循环不做 widening、函数返回类型混入非 return 语句类型、`&&`/`||` 右操作数在子作用域中求值 |
| 完整性（AST 覆盖） | 不完整。`SwitchStatement`、`ClassDefinition`、`EnumDeclaration`、`ForRangeStatement`、`EvalExpression`、`CustomExpression`、`FilterOpExpression` 等未覆写，`defaultProcess` 返回 null 且不递归，整棵子树不做推导 |
| 结果可用性 | 最大问题：`TypeErrorCollector` 收集的错误在 parser 集成点被直接丢弃，推导结果/错误对用户不可见 |
| 测试覆盖 | 单元级较全，但端到端（完整 Program + 窄化 + 泛型 + 错误上报）几乎为零，且存在断言空泛的"空转测试" |

## 3. 正确性缺陷清单

### P1（影响主流程）

1. **错误从不暴露**：`XLangExprParser.buildExecutable()` 中 `new TypeInferenceProcessor()` 用完即弃，
   `getErrors()` 无人读取——类型不匹配既不抛出也不记日志。整个推导目前只产出 AST 上的
   `returnTypeInfo`，无诊断输出。
2. **SwitchStatement 完全未处理**：`TypeInferenceProcessor` 没有 `processSwitchStatement` 覆写，
   基类 `defaultProcess` 返回 null 且**不访问子节点**——switch 内所有语句（包括其中的 return、赋值、
   变量声明）都不参与推导。`processSwitchCase` 是死代码（仅测试直接调用）。
3. **block 赋值丢失**：`processBlockStatement` 用 `context.newChild()` 且丢弃 child。顺序语言里
   `{ x = 1 }` 这种非声明性赋值应写回外层；当前实现等价于把每个 block 当作隔离作用域，导致
   block 之后 `x` 的类型仍是旧值。
4. **函数返回类型混入杂音**：`processSequentialNodes` 对**每条语句**的结果做 union，
   `inferFunctionBodyReturnType` 直接取该 union。`function f() { let x = 1; return "s" }` 的返回类型
   是 `int|string` 而非 `string`。应只收集 ReturnStatement 的类型；并且遇到 return/throw 终结语句后
   应停止 union（`otherBranchNoReturn` 已有字段但顺序路径未利用）。
5. **循环无 widening**：`while/for/do-while` 的 body 在一次性 child state 中推导后丢弃。
   `let x = 1; while(c) { x = "s" }` 循环后 `x` 仍推导为 `int`。至少应把 body 内对已有变量的赋值
   merge 回外层。

### P2（语义偏差 / 死代码）

6. **`&&`/`||` 右操作数作用域错误**：`processLogicalExpression` 对 right 用 `context.newChild()`，
   `a && (b = 1)` 之后 `b` 的类型更新丢失；且 `a || b` 的返回类型是两者 union，未结合真值窄化
   （TS 语义是 `a 的 non-falsy 部分 | b 的类型`）。
7. **UnionTypeNarrower 假分支取错来源**：`handleInstanceOfExpression` 与 `handleEquality` 的
   `isTrue=false` 分支从 `result`（本次条件已收集的窄化 map）取"当前类型"，而不是
   `state.getVariableType(varName)`。独立条件（前面没有其它窄化写入同一 map）时 `currentType == null`
   直接什么都不做——`if (x instanceof A) {...} else { /* x 仍为 A|B */ }` 的 else 分支窄化失效。
8. **`typeof x === 'object'` → MAP_TYPE 过强**：任意 Java 对象都满足，窄化为 Map 不安全。
   `x === true` 字面量窄化（类注释声称支持）在 `handleEquality` 中并未实现——只处理了 NULL。
9. **字符串判断用引用相等**：`inferBinaryType` 中 `left == PredefinedGenericTypes.STRING_TYPE` 是
   身份比较；resolved 的 `java.lang.String` raw type 或含 string 的 union 会落入 ANY 分支。
10. **assignment 写入当前层而非定义层**：`setVariableType` 总是写本 state 的 map，内层对同名外层
    变量的赋值在外层不可见（与缺陷 3 叠加后，只有 if-merge 路径能部分弥补）。
11. **mergeConditionalVariableTypes 的 else 分支语义**：`hasElseBranch && branch2 无该变量 && 无 base`
    时 `type2 = null`，merge 结果取 type1——若 else 分支可能不执行赋值，此处应为 `type1 | undefined`
    或至少 ANY（目前偏乐观）。
12. **GenericTypeInferencer 死代码/桩**：
    - `validateTypeBounds` 恒返回 true（边界约束未实现）；
    - `TypeVarBindings.isCompatible` 未被调用；`bind()` 恒返回 true；
    - `ERR_TYPE_INFER_TYPE_VAR_CONFLICT` 被 import 但从未抛出——类型变量冲突静默 merge 为 union；
    - `inferFromComplexType` 中 raw type 不兼容时静默 `return true`，不上报。
13. **call 参数不校验**：`processCallExpression` 只做返回值泛型替换，未检查实参个数、实参类型与
    `getFuncArgTypes()` 的兼容性；varargs/rest 参数与 call-site spread 均未处理
    （`Math.min(len, size)` 直接截断）。
14. **作用域未与编译器 scope 桥接**：根 state 从空开始，`LexicalScopeAnalysis` 已解析的外部
    变量/全局函数在推导中全部是 ANY——`identity<T>` 这类真实库函数只有在测试手工注入时才生效。

### P3（一致性 / 健壮性）

15. `union()` 会原地修改传入的 `ReturnTypeInfo`（`setOtherBranchNoReturn`），共享对象时产生隐式副作用。
16. `processMemberExpression` 不处理 computed 属性、不处理 Map 上的具体 key 类型；`new Foo()` 中
    `Foo` 为 Identifier 时返回 ANY（构造函数返回类型未推导）。
17. `processSwitchCase` 对 consequent 逐条 union，未考虑 fall-through 与 break 的控制流。
18. `handleBinaryExpression` 把 `=`（ASSIGN）当作相等性窄化条件处理，`if (x = null)` 会被窄化为
    null——语义存疑，至少应加注释或移除。

## 4. 完整性评估（AST 覆盖矩阵）

基于 `XLangASTProcessor` 的分发 + `TypeInferenceProcessor` 覆写情况：

| 类别 | 状态 |
|---|---|
| 字面量/一元/二元/三元逻辑/模板串/正则 | ✅ 覆盖 |
| 成员/调用/new/cast/instanceof/typeof/序列/展开 | ✅ 覆盖（computed member、构造类型为 P3-16 缺陷） |
| if + union 窄化 | ✅ 覆盖（窄化本身有 P2-7/8 缺陷） |
| for / for-of / for-in / while / do-while | ✅ 覆盖（for-range ❌） |
| 函数声明 / 箭头函数 / 参数 / 返回检查 | ✅ 覆盖（返回类型有 P1-4 缺陷） |
| 解构绑定（数组/对象/rest） | ✅ 覆盖（属性级类型不精确，统一取 value/component 类型） |
| **switch** | ❌ 整棵子树跳过 |
| **class / method / field** | ❌ 跳过 |
| **enum 声明** | ❌ 跳过 |
| **for-range** | ❌ 跳过 |
| **eval / custom 表达式** | ❌ 跳过（注：`FilterOpExpression` 为抽象基类，其具体子类 CompareOp/AssertOp/BetweenOp 已覆写返回 boolean——2026-09-05 plan 348 审查勘误：原文将其列入未覆盖为误报） |
| import 说明符 | ⚠️ 覆盖但恒 ANY（未从导入目标取类型） |
| this / super | ⚠️ 恒 ANY（可接受） |
| 泛型推导 | ⚠️ 参数位推导 + 返回类型替换可用；逆变、边界、冲突上报、varargs 未实现 |

结论：**不完整**。凡是未覆写的 kind 都因 `defaultProcess` 不递归而整棵丢失，这比"推导成 ANY"更糟，
因为子树里的 return 语句也不会被函数返回类型收集到。

## 5. 现有测试评估

`TestTypeInferenceProcessor`：
- 优点：按 AST 节点逐个覆盖，含数值提升、if-merge、解构、泛型返回值替换等关键语义。
- 问题：
  - **纯手搓 AST**，几乎不走 parser → 未覆盖集成路径（也就测不出 P1-1 错误被丢弃）；
  - 若干**空转断言**：`testUnionTypeNarrowingFromCondition`、`testLogicalExpressionType` 只
    `assertNotNull(narrowed)`，不检查内容；`testConfigDisabled/Enabled` 只验证配置读写；
  - **没有 SwitchStatement 用例**（缺陷 2 因此长期潜伏）；
  - 没有完整函数体返回类型（多语句 + return）的用例；没有循环后变量类型的用例；
    没有 block 内赋值传播的用例；没有 typeof/instanceof 窄化走完整 `processIfStatement` 的断言。

`TestGenericTypeInferencer`：
- 覆盖单变量/多变量/容器/函数返回/冲突 merge 为 union，较全面；
- `testValidateTypeBounds` 断言恒真的桩实现——**空转测试**，真实边界逻辑落地后必须重写。

## 6. 建议补充的测试用例（按优先级）

P1（修复缺陷 2/3/4/1 的配套回归）：
1. `switch` 完整推导：discriminant 窄化、case 内 return/赋值参与函数返回类型、break 终结。
2. 函数体多语句返回类型：`let x=1; return "s"` → 期望 `string`（当前为 `int|string`，应先修复）。
3. block 赋值传播：`{ x = 1 }` 之后 `x` 为 int（当前失败）。
4. 端到端（经 parser）触发类型错误，断言错误被 collector 暴露/上报（当前必然失败，配合 P1-1 修复）。

P2：
5. 循环 widening：`while` body 中对 `x` 赋 string，循环后 `x` 至少为 `int|string`。
6. `a && (b = 1)` 之后 `b` 类型可见；`x || "default"` 的结果类型。
7. 窄化 else 分支：`x: A|B`，`if (x instanceof A) ... else ...` 中 else 分支 `x` 应为 `B`（当前失败，P2-7）。
8. `typeof x === 'string'` 走完整 if 语句后 true 分支内 `x.length` 推导为 int（集成级断言）。
9. `x != null` 完整 if 语句后 true 分支 `x` 为 `string`（去掉 null）。
10. 字符串 ADD 的非身份比较：变量声明为 resolved `java.lang.String` 类型后 `+` 推导为 string。
11. 泛型：varargs 参数、call-site spread、`Map<K,V>` 双变量、`inferTypeArgumentsWithReturn` 端到端。
12. call 实参类型不匹配上报（配合 P2-13）。

P3：
13. 用真实编译 scope 注入全局函数后的推导（桥接 P2-14）。
14. `new Foo()`（Identifier callee）返回类型。
15. 删除/替换空转断言：`testUnionTypeNarrowingFromCondition`、`testLogicalExpressionType`、
    `testValidateTypeBounds` 改为强断言。

## 7. 建议的修复顺序

1. 集成点暴露错误（P1-1）——先让推导结果可观测，其余缺陷才有回归判据。
2. 补 `processSwitchStatement` 覆写（复用 `processSwitchCase` + discriminant 窄化 + 分支 merge）。
3. 修 block 赋值传播与函数返回类型收集（只取 ReturnStatement、识别终结语句）。
4. 循环 widening（merge body 赋值回外层即可，无需完整不动点）。
5. UnionTypeNarrower 假分支从 `state` 取原类型；`&&`/`||` 右操作数改用同 scope。
6. 其余 P2/P3 与配套测试按 §6 清单推进。

## References

- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceProcessor.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/GenericTypeInferencer.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/UnionTypeNarrower.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/compile/TypeInferenceState.java`
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/XLangExprParser.java`（集成点，L69-72）
- `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compile/TestTypeInferenceProcessor.java`
- `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compile/TestGenericTypeInferencer.java`
