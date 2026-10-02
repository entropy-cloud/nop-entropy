# 2026-10-02 WI1 测试暴露的产品缺陷嫌疑清单（未修，待独立立项）

> 来源：plan 2293（WI1 nop-core 补强）执行期发现。各项均以编译后产品类直接探针复现（非推断），按 roadmap 硬边界记录，修复独立立项。

## 1. FilterOpHelper.dateBetween max 参数失效（P1 嫌疑）

- Problem：`io.nop.core.model.query.FilterOpHelper` `dateBetween`（FilterOpHelper.java:269）`m2 = ConvertHelper.toLocalDate(min)` 误写，应为 `toLocalDate(max)`。后果：max 上界永不生效，且 value > min 时恒返回 false。
- 复现：`dateBetween("2026-01-15","2026-01-01","2026-02-01",false,false)` → 期望 true 实际 false。
- 影响：使用 dateBetween 过滤的查询（含报表/数据权限场景）结果错误。

## 2. FilterOpHelper.like 匹配方向反了（P1 嫌疑）

- Problem：同文件 :70-71，`like` 把 value（s1）转成 regex 再拿 value 自匹配，pattern（s2）只参与空判断。
- 复现：`like("abc","xyz%")` → 期望 false 实际 true。
- 影响：like 过滤条件几乎恒真，查询过滤失效。

## 3. ModifierBuilder.PUBLIC_MASK 恒为 0

- Problem：`io.nop.core.reflect.impl.ModifierBuilder`（:13）`PUBLIC & PROTECTED & PRIVATE == 0`（应为 `|`），is* 系列不清旧访问位。
- 复现：`new ModifierBuilder().isPublic().isPrivate().end()` = 3（PUBLIC|PRIVATE 非法组合，期望 2）。
- 影响：经 ModifierBuilder 生成修饰符的代码生成路径可能产出非法修饰符组合。

## 4. GlobalStatManager getAll*Stats 排序方向与语义相反

- Problem：`io.nop.core.stat.GlobalStatManager`（:69/:89/:109）`-Long.compare(b.getExecuteAvgTime(), a.getExecuteAvgTime())` 数学上等价于 `compare(a,b)`（升序），与"按平均时间降序"语义相反。
- 复现：slow(100ms)/fast(1ms) 排序后 fast 在前。
- 影响：慢 SQL/慢服务定位功能失效（恰好把最慢的排最后）。相关测试有意不锁定该缺陷行为（TestGlobalStatManager 注释说明）。

## 5. AStarPathFinder scoreMap 从未写入

- Problem：`io.nop.core.model.graph.AStarPathFinder` `find()` 全程无 `scoreMap.put`——路径重建恒返回空列表、`findAll` 恒返回空 map，且因 `toNode` 永远为 null 带权松弛失效（退化为无权代价）。
- 复现：a→c 直连代价 10、a→b→c 代价 4，`find("a","c").getCost()` 返回 10（期望 4）、path 长度 0。
- 影响：AStar 最短路在有权图上不成立；相关测试按当前退化行为记录（缺陷未锁定）。

## 6. 低危观察（3 项）

- `JdbcSqlStat.compareTo` 依赖懒加载的 `sqlHash`，未调 `getSqlHash()` 前两实例比较恒为 0。
- `AnnotationData.fromAnnotation` 捕获 `annotationType`/`toString` 噪声键。
- `FilterBeanFormatter.visitCompareOp` 的 useFunctionCall 分支缺右括号（渲染 `like(name,"a%"`）。

## Notes For Future Refactors

- 1/2/4 项的修复均应以现有测试的反向断言（把"缺陷行为"断言改为正确语义）作为回归测试，修复前测试保持对现状的记录性断言并注释缺陷编号。
- 5 项修复后 TestGraphAlgorithms 中记录退化行为的断言需同步改为正确路径/代价断言。
