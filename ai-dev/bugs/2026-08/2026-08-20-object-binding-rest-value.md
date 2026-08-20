# 2026-08-20 对象解构 rest 绑定取值错误（tail 收集误放整 map）

## Problem

XLang 对象解构的 rest 绑定（`let {x, ...rest} = {x:1, y:2}`）中，`rest` 的每个键的值都是**整个源 map**，
而不是各键自身的值。

- 最小复现：解释器执行对象解构 rest 路径，`rest` 形如 `{y={x=1, y=2}}`（期望 `{y=2}`）。
- 位置：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/ObjectBindingAssignExecutable.java#execute`
  rest 分支。

## Diagnostic Method

I3 plan Phase 2 语义盘点（A 族绑定族逐分支三类标注）时读源码发现：循环体
`tail.put(entry.getKey(), value)`——`value` 是**外层的解构源值**（整个 map），应为 `entry.getValue()`。
数组解构的对应路径（`ArrayBindingAssignExecutable` 的 `copyTail`）正确，仅对象分支笔误。属"表面在绑定语义、
实际是变量名笔误"的一行修复。

## Root Cause

rest 收集循环误引用外层变量 `value`（整个源 Map），应为当前迭代的 `entry.getValue()`。

## Fix

`tail.put(entry.getKey(), entry.getValue())`。修复后解释器与 java 后端生成代码（I3 转译器按修复后语义
生成逐键收集代码）同享正确语义；I3 corpus 对象解构单元含 rest 路径覆盖。

## Regression Protection

- `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/compare/`（CorpusCoverageA 对象解构单元，解释器基线
  + java 列对拍断言 rest 值）；
- `TestExecToJavaTranslatorCoverageA#testObjectBindingSourceShape`（生成源码逐键收集形态断言）。
