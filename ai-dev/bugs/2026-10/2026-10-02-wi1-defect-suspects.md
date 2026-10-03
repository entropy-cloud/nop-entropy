# 2026-10-02 WI1 测试暴露的产品缺陷嫌疑清单（已修复，plan 2306 收口）

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


## Fix（2026-10-03 plan 2306 回填）

- **1 dateBetween max 失效：`fixed`**。根因：FilterOpHelper.dateBetween L269 `m2` 误用 `min`（对照 L293 dateTimeBetween 正确用 max，笔误实锤）。修复：`toLocalDate(min)`→`toLocalDate(max)`。爆炸半径：`grep "FilterOpHelper::dateBetween|FilterOpHelper.dateBetween"` 全仓仅 nop-core 内 FilterOp（L362）方法引用，模块外零直接消费方（▲ 项裁定：nop-core 自身测试当期跑，496 全绿）。回归：TestFilterOpHelper.testDateBetweenMaxBoundEnforced（含 bug 复现输入 "2026-01-15"/"2026-01-01"/"2026-02-01"）。
- **2 like 方向反：`fixed`**。根因：L70-71 把 value（s1）转 regex 再自匹配，pattern 只参与空判断。修复：`sqlToRegexLike(s2)` 匹配 s1。爆炸半径：同项 1 口径（FilterOp L352 唯一方法引用）。回归：TestFilterOpHelper.testLikePatternMatching（含 bug 复现 like("abc","xyz%")==false）。
- **3 ModifierBuilder PUBLIC_MASK：`fixed`**。`&`→`|`。回归：TestModifierBuilder.testAccessModifiersAreMutuallyExclusive（isPublic().isPrivate() 必须仅余 PRIVATE）。
- **4 GlobalStatManager 排序反：`fixed`**。`-Long.compare(b,a)` 数学上等价升序，删除负号恢复降序（3 处：sql/rpc client/rpc server）。回归：TestGlobalStatManager.testGetAllJdbcSqlStatOrderByAvgTimeDesc（正向顺序断言）+ testRpcStatsOrderByAvgTimeDesc。
- **5 AStar scoreMap：`fixed`**。scoreMap 全程无 put：路径重建恒空、松弛失效（openSet 门把更优路径丢弃）。修复：scoreMap 写入 + closedSet stale-entry 跳过 + reconstructPath 按 from 边回溯。回归：TestGraphAlgorithms.testAStarWeightedRelaxationChoosesCheaperPath（bug 复现：a→c=10 vs a→b→c=4 应取 4）+ 退化断言翻转为正向路径断言。
- **6 低危三项：`fixed`（3/3）**。
  - compareTo 懒加载：改经 `getSqlHash()` 触发懒计算（此前直接读字段，未调用 getSqlHash 前两实例比较恒 0）。
  - AnnotationData 噪声键：过滤 `Annotation` 接口声明的 `annotationType()`（与 Object 声明方法一并排除）。
  - FilterBeanFormatter 缺右括号：useFunctionCall 分支补 `)`。

## Notes For Future Refactors

- （已按此执行：1/2/4/5 项的记录性断言已翻转为正确语义正向断言，见 Fix 段。）
- （已执行：TestGraphAlgorithms 路径/代价正向断言已落地。）

## Affected Files

- nop-kernel/nop-core/src/main/java/io/nop/core/model/query/FilterOpHelper.java
- nop-kernel/nop-core/src/main/java/io/nop/core/model/query/FilterBeanFormatter.java
- nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/ModifierBuilder.java
- nop-kernel/nop-core/src/main/java/io/nop/core/stat/GlobalStatManager.java
- nop-kernel/nop-core/src/main/java/io/nop/core/stat/JdbcSqlStat.java
- nop-kernel/nop-core/src/main/java/io/nop/core/reflect/impl/AnnotationData.java
- nop-kernel/nop-core/src/main/java/io/nop/core/model/graph/AStarPathFinder.java
- 对应 src/test 下 TestFilterOpHelper / TestModifierBuilder / TestGlobalStatManager / TestGraphAlgorithms
