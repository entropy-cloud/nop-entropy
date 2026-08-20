# 2026-08-20 typeof null NPE（TypeOfExecutable 误引用枚举常量）

## Problem

`typeof null` 在 XLang 解释器执行时抛 NullPointerException（经程序入口 `CallFuncExecutable` 包装为
`nop.err.xlang.exec.call-func-fail`），而非设计意图的 `"undefined"`。

- 最小复现：动态表达式 `typeof null`（CompileTool `compileFullExpr` + `allowUnregisteredScopeVar`）。
- 位置：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/TypeOfExecutable.java#execute`。

## Diagnostic Method

I3 plan Phase 1 产生路径盘点（corpus 源语法 live 诊断）时发现：`typeof 3` 正常返回
`java.lang.Integer`，而 `typeof null` 抛错。读源码即见 `return value == null ? "undefined" : v.getClass().getTypeName();`
——条件里的 `value` 是**静态导入的枚举常量** `io.nop.xlang.xpl.XplInputFormat.value`（IDE 误自动补全的典型产物），
永不为 null，导致 null 分支死代码、恒走 `v.getClass()` 触发 NPE。诊断难点仅在于该静态导入藏在 import 列表尾部，
表达式本身看似正确。

## Root Cause

条件表达式误引用静态导入的枚举常量（`XplInputFormat.value`），应为局部变量 `v`。null 判断恒假。

## Fix

I3 Phase 2 将该语义提取为共享 helper `XLangSemantics.typeOf(Object)`（意图语义：`null -> "undefined"`），
解释器 `TypeOfExecutable.execute` 改为委托调用，同时删除误导入。java 后端生成代码同调该 helper
（同一实现来源，对拍三层断言直接成立）。

## Regression Protection

`nop-kernel/nop-xlang-java/src/test/java/io/nop/xlang/java/translator/TestExecToJavaTranslatorCoverageA.java#testTypeOfNullReturnsUndefined`：
生成代码 `typeof null` == `"undefined"`、`typeof 3` == `"java.lang.Integer"`，并附解释器侧同语义对照断言
（共享 helper 单一实现锚点）。
