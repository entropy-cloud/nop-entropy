# 维度 19：命名与术语一致性

## 第 1 轮（初审）

### [维度19-01] sourceIDs/sinkIDs 不符合 Java camelCase 约定

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/graph/StreamGraph.java`
- **证据片段**:
```java
private final List<Integer> sourceIDs;   // 应为 sourceIds
private final List<Integer> sinkIDs;     // 应为 sinkIds

public List<Integer> getSourceIDs() { ... }
public void addSourceID(int sourceId) { ... }
```
- **严重程度**: P3
- **现状**: 连续大写缩写词 ID 使用了全大写形式（sourceIDs），Java 约定应仅首字母大写（sourceIds）。
- **风险**: 容易被误读为全大写常量，12 处引用需同步修改。
- **建议**: 统一改为 sourceIds/sinkIds。
- **信心水平**: 确定
- **误报排除**: Java 约定连续缩写词仅首字母大写（如 XmlHelper 而非 XMLHelper），这是可验证的风格问题。
- **复核状态**: 未复核

---

### [维度19-02] ShardPrefixedKey 同名类出现在两个包中

- **文件**: `nop-stream-core/.../state/backend/memory/ShardPrefixedKey.java`（package-private）、`nop-stream-core/.../state/shard/ShardPrefixedKey.java`（public）
- **证据片段**:
两个类简单名相同，字段结构相同（shardId + key），但可见性、接口和 toString 格式不同。
- **严重程度**: P3
- **现状**: 两个同名类共存于同一模块的不同包中，增加维护混淆风险。
- **风险**: 开发者可能误用另一个版本的 ShardPrefixedKey。
- **建议**: 合并为一个类或重命名其中一个。
- **信心水平**: 确定
- **误报排除**: 两个同名类在同一个模块中，即使在不同包中，也是维护风险。
- **复核状态**: 未复核

---

## 维度复核结论

（待复核）

## 最终保留项

（待复核后填写）
