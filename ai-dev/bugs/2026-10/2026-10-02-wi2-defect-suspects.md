# 2026-10-02 WI2 测试暴露的产品缺陷嫌疑清单（已修复，plan 2306 收口）

> 来源：plan 2294（WI2 kernel 小模块补强）执行期发现。按 roadmap 硬边界（测试暴露的产品缺陷一律走独立 bug 流程）记录，修复独立立项。各项均为**嫌疑**（有钉住证据，未做根因确认与修复）。

## 1. GraphQLResponseBean.toErrorBean null Boolean 拆箱 NPE

- Problem：`nop-api-core` 的 `io.nop.api.core.beans.graphql.GraphQLResponseBean.toErrorBean()`（约 L123）`error.setBizFatal(getBizFatal())` 在错误响应未设 `nop-biz-fatal` 扩展时对 null Boolean 拆箱 → NPE。
- 证据：`TestGraphQLResponseBean.testToErrorBeanWithoutBizFatalExtensionShouldNotThrow` 以 `@Disabled("product defect: toErrorBean NPE when bizFatal extension absent")` 钉住（surefire skipped=1）；解除即红。
- 修复方向：null 安全处理（未设扩展 → bizFatal 默认 false 或保持 null 语义，按契约裁定）。

## 2. SetFunctions.concat 标量分支丢弃接收集合

- Problem：`nop-commons` `SetFunctions.concat(Collection, Object)` 标量分支只返回 source、丢弃接收集合内容，与 JS `concat` 语义及同方法集合分支不一致。
- 证据：`TestSetFunctions` 中以注释钉住当前行为的测试。
- 修复方向：按 JS 语义合并（需先确认无既有消费方依赖当前行为）。

## 3. SetFunctions.flatMap 标量分支疑似笔误

- Problem：`SetFunctions.flatMap` 标量分支 `ret.add((K) item)` 加入原元素而非映射结果（疑似笔误）。
- 证据：执行记录（未写固化断言，避免钉住错误行为）。
- 修复方向：确认意图后改为加入映射值并补回归测试。

## 4. MutableIntArray.addAll 边界校验不一致

- Problem：`MutableIntArray.addAll(int[], offset, length)` 缺少 `addAll(IntArray,…)` 具有的前置边界校验——越界时抛 `ArrayIndexOutOfBoundsException` 而非 `IllegalArgumentException`，两变体行为不一致。
- 证据：`TestMutableIntArray` 边界用例。
- 修复方向：补齐前置校验统一异常契约（注意既有调用方是否依赖 AIOOBE）。


## Fix（2026-10-03 plan 2306 回填）

- **1 toErrorBean null Boolean NPE：`fixed`**。`error.setBizFatal(getBizFatal())` 对 null Boolean 拆箱。修复：`Boolean.TRUE.equals(getBizFatal())`（未设扩展 → bizFatal=false，与 ErrorBean 原始类型字段缺省一致）。回归：解除 @Disabled 的 testToErrorBeanWithoutBizFatalExtensionShouldNotThrow（增强断言 bizFatal==false）。
- **2 concat 标量分支：`fixed`**。结果 = list + source（JS Array.concat 语义，与集合分支一致）。消费方 grep：Java 直调仅 Underscore.flatMap(identity)（不受影响）；SetFunctions 经 ReflectionManager 注册为 Set/Collection helper 供脚本调用——语义修正面向 JS 兼容契约，行为变化记录于此。回归：testConcatScalarBranchKeepsReceiverList（原特征化断言翻转）。
- **3 flatMap 标量分支：`fixed`**。`ret.add((K) v)` 加入映射结果而非原元素。消费方 grep 同上。回归：testFlatMapScalarBranchUsesMappedValue（String::length 映射断言）。
- **4 MutableIntArray.addAll 校验：`fixed`**。int[] 变体补前置校验（AIOOBE→IAE，异常类型契约变化），并给 IntArray 变体补负值防御。_cases 对该异常类型零命中（plan 预检成立）；全 reactor 验证通过。回归：TestMutableIntArray.testAddAllAndBounds 扩展（越界/负 offset/负 length → IAE，边界内正常）。

## Notes For Future Refactors

- （已执行：@Disabled 已解除并转绿。）
- （已执行：消费方 grep 记录见 Fix 段。）

## Affected Files

- nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/graphql/GraphQLResponseBean.java
- nop-kernel/nop-commons/src/main/java/io/nop/commons/collections/SetFunctions.java
- nop-kernel/nop-commons/src/main/java/io/nop/commons/collections/MutableIntArray.java
- 对应 src/test 下 TestGraphQLResponseBean / TestSetFunctions / TestMutableIntArray
