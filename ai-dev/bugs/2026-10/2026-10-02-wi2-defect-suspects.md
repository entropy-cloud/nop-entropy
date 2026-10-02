# 2026-10-02 WI2 测试暴露的产品缺陷嫌疑清单（未修，待独立立项）

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

## Notes For Future Refactors

- 第 1 项的 @Disabled 测试即回归测试：修复后解除 @Disabled 应转绿。
- 第 2/3 项修复前先全仓 grep 消费方，确认无人依赖现行行为。
