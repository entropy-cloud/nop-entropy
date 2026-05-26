# 维度15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] BFS 遍历使用 String[] 编码 (nodeId, depth) 二元组

- **文件**: `CodeIndexService.java:1606-1612,2638-2645`, `ImpactAnalyzer.java:233-241`
- **证据片段**:
```java
Queue<String[]> queue = new LinkedList<>();
queue.add(new String[]{start, "0"});
String[] current = queue.poll();
int d = Integer.parseInt(current[1]);
```
- **严重程度**: P3
- **现状**: 使用 `String[]` 传递结构化数据，数组长度无编译期约束。
- **建议**: 使用 `record QueueEntry(String nodeId, int depth) {}` 替代。
- **复核状态**: 未复核

---

### [维度15-02] ImpactAnalyzer 定义了 RiskLevel 枚举但未使用，返回魔法字符串

- **文件**: `nop-code-graph/.../ImpactAnalyzer.java:144,272-283`
- **证据片段**:
```java
public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL } // 定义了但从未使用
private static String evaluateRisk(...) {
    if (totalImpacted > 50 || maxDepth > 5) return "critical"; // 魔法字符串
}
```
- **严重程度**: P3
- **现状**: 枚举是死代码，实际返回字符串比较无编译期保障。
- **建议**: 让 evaluateRisk() 返回 RiskLevel 枚举。
- **复核状态**: 未复核

---

### [维度15-03] deleteFileRecords 使用 List<?> 通配符参数加 instanceof 运行时判断

- **文件**: `CodeIndexService.java:2188-2209`
- **证据片段**:
```java
private void deleteFileRecords(String indexId, List<?> filePaths) {
    for (Object pathObj : filePaths) {
        String filePath = pathObj instanceof Path
                ? ((Path) pathObj).toString() : pathObj.toString();
    }
}
```
- **严重程度**: P3
- **现状**: 私有方法使用通配符类型加 instanceof，丢失编译期类型检查。
- **建议**: 统一为 `List<Path>` 或提供两个重载方法。
- **复核状态**: 未复核

---

## 审计结论

3 个 P3 级发现。核心域模型类型安全良好（枚举、集合泛型参数完整），DTO 层在序列化边界的类型放松属于合理取舍。
