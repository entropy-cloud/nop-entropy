# 2026-07-13 FilterBeanVisitor 不识别嵌套 filter 标签导致 orm-reader query 失败

## Problem

- 在 batch DSL 中使用 `<orm-reader><query><filter>...</filter></query></orm-reader>` 时，运行时报 `nop.err.core.filter.unknown-op: op=filter`
- 错误位置指向 `.batch.xml` 中的 `<filter>` 元素
- 同样的 `<filter>` XML 格式在 `TestBeanTool.testQueryBean` 中可以正确构建 QueryBean（仅测试序列化，不测试 filter 求值）

## Diagnostic Method

- **诊断难点**：`<filter>` 是 QueryBean 的标准 XML 序列化格式（有 `TestBeanTool` 测试和 `query.xml` 样本为证），`FilterBeanVisitor.visitRoot()` 第 47 行也明确处理了 `FILTER_TAG_NAME` 作为隐式 AND。表面上看不应该报 `unknown-op`。
- **错误假设一**：认为是 `BeanTool.buildBeanFromTreeBean` 把 `<filter>` 元素的 tagName 泄漏到了 TreeBean 中。写单元测试手动构建 XNode → QueryBean，确认 filter TreeBean 的 tagName 确实是 `"filter"`，但该路径下 filter 结构正确（children 是 `eq` 和 `in`）。
- **错误假设二**：认为是 `generateNode()` 返回的节点结构与手动构建不同。写调试测试通过 `DslModelParser` 加载 batch DSL 并调用 `IXNodeGenerator.generateNode()`，确认返回的 dummy 节点结构正确：root tagName=`"_"`，children 是 `<filter>` 和 `<orderBy>`，`buildBeanFromTreeBean` 产出的 QueryBean.filter 也正确（tagName=`"filter"`, children=`[eq, in]`）。
- **被排除的假设**：nop-core jar 过期（重新 install 后问题依旧）、XPL 编译器修改了 tagName（调试证明没有）。
- **决定性证据**：重新审视完整 stack trace，发现调用链是 `visitRoot(49) → visit(75) → visitAnd(276) → visit(68) → visitUnknown(352)`。`visitRoot` 确实走了 else 分支（line 49），但不是因为 `"filter"` 不匹配 `FILTER_TAG_NAME`——而是因为到达 `visitRoot` 的 filter 根本不是 tagName=`"filter"` 的节点，而是 tagName=`"and"` 的节点。
- **根因定位**：检查 `OrmQueryBatchLoaderProvider.newLoaderState()` 第 112-113 行，发现当 `partitionIndexField != null && partitionRange != null` 时，会调用 `state.query.addFilter(FilterBeans.between(...))`。`QueryBean.addFilter()` 会把已有 filter 和新增 filter 包装进 `FilterBeans.and(existingFilter, newFilter)`。这导致原始的 tagName=`"filter"` TreeBean 成为了外层 `and` 的子节点。当 `visitAnd` 遍历子节点调用 `visit()`（而非 `visitRoot()`）时，`visit()` 不识别 `"filter"` 标签——只有 `visitRoot()` 有 `FILTER_TAG_NAME` 检查。

## Root Cause

- `FilterBeanVisitor.visitRoot()` 第 47-48 行处理了 `FILTER_TAG_NAME`（`"filter"`）和 `DUMMY_TAG_NAME`（`"_"`）作为隐式 AND。但 `visit()` 方法没有同样的检查。当 `"filter"` TreeBean 不是作为根 filter 出现、而是作为另一个 `and`/`or` 的子节点出现时，它走 `visit()` 路径，`FilterOp.fromName("filter")` 返回 null，触发 `visitUnknown("filter")`。
- `OrmQueryBatchLoaderProvider.newLoaderState()` 通过 `QueryBean.addFilter(between)` 追加分区过滤条件时，会把 DSL `<query>` 中构建的原始 `<filter>` TreeBean 包装进外层 `and`，使其从根位置变成嵌套位置。

## Fix

- 在 `FilterBeanVisitor.visit()` 的 `filterOp == null` 分支中增加 `DUMMY_TAG_NAME` / `FILTER_TAG_NAME` 检查，使其与 `visitRoot()` 行为一致——遇到 `"filter"` 或 `"_"` 标签时作为隐式 AND 处理。
- 修改文件：`nop-kernel/nop-core/src/main/java/io/nop/core/model/query/FilterBeanVisitor.java`，在 `visit()` 方法的 `if (filterOp == null)` 块内、`return visitUnknown(...)` 之前，增加 `DUMMY_TAG_NAME` / `FILTER_TAG_NAME` → `visitAnd` 的 fallback。

## Tests

- `nop-batch/nop-batch-sys/src/test/java/io/nop/batch/sys/TestSysEventBatchTrigger.java` — 端到端验证 `<orm-reader><query><filter>` 格式在 `OrmQueryBatchLoaderProvider` + `partitionRange` 条件下正常工作（2 个测试覆盖多分区独立处理和 ConsumeLater 重排队）

## Affected Files

- `nop-kernel/nop-core/src/main/java/io/nop/core/model/query/FilterBeanVisitor.java`（1 行修改）

## Notes For Future Refactors

- `"filter"` 和 `"_"`（dummy）标签只在 `visitRoot()` 中作为隐式 AND 是不够的。任何通过 `QueryBean.addFilter()` 或 `FilterBeans.and/or/not` 组合 filter 的代码路径都可能把它们变成嵌套位置，从而走 `visit()` 而非 `visitRoot()`。
- 如果未来有新的 filter 组合方式（如 batch loader 追加分区条件、租户过滤、逻辑删除过滤等），都可能在已有 filter 上叠加，使根 filter 变成嵌套 filter。`visit()` 中的 `FILTER_TAG_NAME` 检查保证这种叠加安全。
- 本次 bug 的诊断过程中走了大量弯路：没有第一时间检查 `OrmQueryBatchLoaderProvider.newLoaderState()` 中 `addFilter` 对 filter 结构的影响，而是先怀疑 XML 解析、XPL 编译、`buildBeanFromTreeBean` 等环节。教训是：**遇到 filter 求值错误时，首先检查 filter 到达求值点之前经过了哪些转换（addFilter、and/or 包装等）**。
