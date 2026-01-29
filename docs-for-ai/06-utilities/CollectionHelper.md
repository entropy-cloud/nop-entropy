# CollectionHelper - 集合工具类

## 概述

`CollectionHelper` 是Nop平台的核心集合工具类，提供了丰富的集合操作方法，包括集合创建、转换、判空、过滤等常用功能。

包路径：`io.nop.commons.util.CollectionHelper`

## 核心功能

### 1. 集合判空和安全操作

#### isEmpty()

检查集合或Map是否为空。

```java
// 检查Collection是否为空
boolean isEmpty = CollectionHelper.isEmpty(collection);

// 检查Map是否为空
boolean isEmptyMap = CollectionHelper.isEmptyMap(map);

// 检查集合或Map是否为空（通用方法）
boolean isEmpty = CollectionHelper.isEmpty(c);

// 检查集合是否不为空
boolean notEmpty = CollectionHelper.notEmpty(list);
```

**使用示例**：

```java
List<String> list = getNames();

if (CollectionHelper.isEmpty(list)) {
    log.info("No names found");
    return Collections.emptyList();
}

// 安全获取集合大小
int size = CollectionHelper.safeGetSize(list);
if (size == -1) {
    // list为null
}
```

#### toNotNull()

将null集合转换为空集合，避免NPE。

```java
List<String> list = null;
List<String> safeList = CollectionHelper.toNotNull(list);
// safeList 现在是 Collections.emptyList()
```

### 2. 元素访问

#### first() / last()

获取集合的第一个或最后一个元素。

```java
// 获取集合第一个元素
String first = CollectionHelper.first(list);

// 获取List最后一个元素
String last = CollectionHelper.last(list);

// 如果集合为空，返回null
```

**使用示例**：

```java
List<User> users = dao().findAllByQuery(query);

if (CollectionHelper.notEmpty(users)) {
    User firstUser = CollectionHelper.first(users);
    User lastUser = CollectionHelper.last(users);

    log.info("First user: {}, Last user: {}", firstUser, lastUser);
}
```

### 3. 集合创建

#### newHashMap()

创建HashMap，可指定初始大小。

```java
// 创建默认HashMap
Map<String, String> map = CollectionHelper.newHashMap();

// 创建指定初始大小的HashMap
Map<String, User> userMap = CollectionHelper.newHashMap(expectedSize);
```

#### newLinkedHashMap()

创建LinkedHashMap，保持插入顺序。

```java
Map<String, User> orderedMap = CollectionHelper.newLinkedHashMap();
```

#### newHashSet() / newLinkedHashSet()

创建Set集合。

```java
// 创建HashSet（无序，快速查找）
Set<String> set = CollectionHelper.newHashSet();

// 创建LinkedHashSet（有序）
Set<String> orderedSet = CollectionHelper.newLinkedHashSet();
```

#### newArrayList()

创建ArrayList。

```java
List<String> list = CollectionHelper.newArrayList();
```

### 4. 集合转换

#### toList()

将任意集合转换为List。

```java
// 从Collection转List
List<String> list = CollectionHelper.toList(collection);

// 从Enumeration转List
List<String> list = CollectionHelper.toList(enumeration);
```

#### toSet()

将任意集合转换为Set。

```java
Set<String> set = CollectionHelper.toSet(collection);
```

#### propertiesToMap()

将Properties对象转换为Map。

```java
Properties props = new Properties();
props.setProperty("key1", "value1");

Map<String, String> map = CollectionHelper.propertiesToMap(props);
```

#### mapToProperties()

将Map转换为Properties对象。

```java
Map<String, String> map = new HashMap<>();
map.put("key1", "value1");

Properties props = CollectionHelper.mapToProperties(map);
```

### 5. 集合过滤

#### filter()

根据条件过滤集合元素。

```java
// 过滤出符合条件的元素
List<User> activeUsers = CollectionHelper.filter(users,
    user -> user.getStatus() == 1);

// 过滤并转换为不同类型
List<String> names = CollectionHelper.filter(users,
    user -> user.getStatus() == 1,
    User::getName);
```

#### reject()

排除符合条件的元素。

```java
// 排除不符合条件的元素
List<User> activeUsers = CollectionHelper.reject(users,
    user -> user.getStatus() == 0);
```

### 6. 集合映射

#### map()

将集合元素映射为另一种类型。

```java
// 映射为字符串列表
List<String> names = CollectionHelper.map(users, User::getName);

// 映射并过滤
List<String> activeNames = CollectionHelper.map(
    users,
    User::getName,
    user -> user.getStatus() == 1
);
```

#### mapToInt() / mapToLong() / mapToDouble()

映射为数值列表。

```java
// 映射为整数列表
List<Integer> ages = CollectionHelper.mapToInt(users, User::getAge);

// 映射为长整型列表
List<Long> ids = CollectionHelper.mapToLong(users, User::getId);

// 映射为浮点数列表
List<Double> scores = CollectionHelper.mapToDouble(users, User::getScore);
```

### 7. 集合去重

#### distinct()

对集合去重。

```java
// 去重
List<String> uniqueNames = CollectionHelper.distinct(names);

// 基于某个属性去重
List<User> uniqueUsers = CollectionHelper.distinct(users, User::getEmail);
```

### 8. 集合分组

#### groupBy()

根据某个属性分组。

```java
// 按部门分组用户
Map<String, List<User>> byDept = CollectionHelper.groupBy(
    users,
    User::getDepartment
);

// 按状态分组
Map<Integer, List<User>> byStatus = CollectionHelper.groupBy(
    users,
    User::getStatus
);
```

### 9. 集合排序

#### sortBy()

根据属性排序。

```java
// 按名称升序排序
List<User> sorted = CollectionHelper.sortBy(users, User::getName);

// 按年龄降序排序
List<User> sortedDesc = CollectionHelper.sortByDesc(users, User::getAge);
```

### 10. 集合分割

#### partition()

将集合分割为指定大小的块。

```java
// 每个块最多100个元素
List<List<User>> batches = CollectionHelper.partition(users, 100);

// 分批处理
for (List<User> batch : batches) {
    dao().batchSaveEntities(batch);
}
```

### 11. 集合连接

#### join()

将集合元素连接为字符串。

```java
// 使用逗号连接
String result = CollectionHelper.join(names, ",");

// 使用自定义分隔符
String result = CollectionHelper.join(ids, "-");

// 使用连接器处理元素
String result = CollectionHelper.join(users, "|", User::getName);
```

### 12. 集合比较

#### isEqual()

比较两个集合是否相等（考虑顺序）。

```java
boolean equal = CollectionHelper.isEqual(list1, list2);
```

#### containsAll()

检查一个集合是否包含另一个集合的所有元素。

```java
boolean containsAll = CollectionHelper.containsAll(list1, list2);
```

#### intersection()

获取两个集合的交集。

```java
List<String> intersection = CollectionHelper.intersection(list1, list2);
```

#### union()

获取两个集合的并集。

```java
List<String> union = CollectionHelper.union(list1, list2);
```

#### difference()

获取两个集合的差集。

```java
List<String> diff = CollectionHelper.difference(list1, list2);
```

### 13. 特殊集合类型

#### CaseInsensitiveMap

创建不区分大小写的Map。

```java
Map<String, String> map = CollectionHelper.newCaseInsensitiveMap();

map.put("Key", "value");
String value = map.get("key"); // 可以获取到
```

#### IntHashMap

创建以Integer为键的高效Map。

```java
Map<Integer, String> map = CollectionHelper.newIntHashMap();

map.put(1, "value1");
map.put(2, "value2");
```

### 14. 位集合操作

#### newFixedBitSet() / newIndexBitSet()

创建位集合（用于标记大量布尔状态）。

```java
// 创建固定大小的位集合
IBitSet bitSet = CollectionHelper.newFixedBitSet(1000);

// 设置位
bitSet.set(10);
bitSet.set(20);

// 检查位
boolean isSet = bitSet.get(10); // true
```

**使用场景**：标记大量状态，比使用boolean数组或List更节省内存。

## 实用示例

### 示例1：安全处理空集合

```java
public List<String> getUserNames(List<User> users) {
    if (CollectionHelper.isEmpty(users)) {
        return Collections.emptyList();
    }

    return CollectionHelper.map(users, User::getName);
}
```

### 示例2：集合分批处理

```java
public void processLargeDataset(List<DataItem> items) {
    // 每批处理1000条
    List<List<DataItem>> batches = CollectionHelper.partition(items, 1000);

    for (List<DataItem> batch : batches) {
        txn(() -> {
            for (DataItem item : batch) {
                processItem(item);
            }
        });
    }
}
```

### 示例3：数据分组和统计

```java
public Map<String, Integer> countByDepartment(List<User> users) {
    Map<String, List<User>> byDept = CollectionHelper.groupBy(
        users,
        User::getDepartment
    );

    Map<String, Integer> result = CollectionHelper.newHashMap();
    for (Map.Entry<String, List<User>> entry : byDept.entrySet()) {
        result.put(entry.getKey(), entry.getValue().size());
    }

    return result;
}
```

### 示例4：复杂过滤和转换

```java
public List<String> getActiveUserNames(List<User> users) {
    return CollectionHelper.map(
        CollectionHelper.filter(
            users,
            user -> user.getStatus() == 1 // 过滤活跃用户
        ),
        User::getName // 转换为名称列表
    );
}
```

### 示例5：集合去重和排序

```java
public List<String> getSortedUniqueNames(List<User> users) {
    // 去重
    List<User> uniqueUsers = CollectionHelper.distinct(users, User::getEmail);

    // 转换为名称
    List<String> names = CollectionHelper.map(uniqueUsers, User::getName);

    // 排序
    return CollectionHelper.sortBy(names, Function.identity());
}
```

## 性能建议

### 1. 预设集合大小

当知道集合大致大小时，预设初始大小可以提高性能。

```java
// ✅ 推荐：预设大小
Map<String, User> userMap = CollectionHelper.newHashMap(1000);

// ❌ 不推荐：默认大小
Map<String, User> userMap = new HashMap(); // 可能需要多次扩容
```

### 2. 选择合适的集合类型

- **HashMap**: 快速查找，不关心顺序
- **LinkedHashMap**: 保持插入顺序
- **TreeMap**: 排序顺序，查找稍慢
- **HashSet**: 快速查找，无序
- **LinkedHashSet**: 保持插入顺序
- **TreeSet**: 排序集合

### 3. 避免不必要的转换

```java
// ✅ 推荐：直接使用
List<String> result = new ArrayList<>();
for (User user : users) {
    result.add(user.getName());
}

// ❌ 不推荐：多次转换
List<String> result = CollectionHelper.toList(
    CollectionHelper.map(users, User::getName)
);
```

### 4. 使用位集合处理大量布尔状态

```java
// ✅ 推荐：使用位集合
IBitSet bitSet = CollectionHelper.newFixedBitSet(100000);

// ❌ 不推荐：使用boolean数组
boolean[] flags = new boolean[100000]; // 占用更多内存
```

## 常见问题

### Q1: CollectionHelper.isEmpty() 与 list.isEmpty() 有什么区别？

`CollectionHelper.isEmpty()` 可以安全处理null：

```java
List<String> list = null;

// ❌ 会抛出NPE
boolean empty1 = list.isEmpty();

// ✅ 安全，返回true
boolean empty2 = CollectionHelper.isEmpty(list);
```

### Q2: 如何安全地遍历集合？

```java
List<String> list = null;

// ✅ 推荐：先判空
if (CollectionHelper.notEmpty(list)) {
    for (String item : list) {
        // 处理
    }
}

// ✅ 推荐：转换为非空集合
for (String item : CollectionHelper.toNotNull(list)) {
    // 处理
}
```

### Q3: 如何高效地合并两个Map？

```java
// 方法1：使用putAll
Map<String, User> merged = CollectionHelper.newHashMap(map1);
merged.putAll(map2);

// 方法2：使用stream（Java 8+）
Map<String, User> merged = Stream.of(map1, map2)
    .flatMap(m -> m.entrySet().stream())
    .collect(Collectors.toMap(
        Map.Entry::getKey,
        Map.Entry::getValue,
        (v1, v2) -> v1 // 键冲突时保留第一个值
    ));
```

### Q4: 如何按多个字段排序？

```java
// 先按部门，再按姓名排序
List<User> sorted = CollectionHelper.sortBy(users,
    Comparator.comparing(User::getDepartment)
        .thenComparing(User::getName)
);
```

## 相关文档

- [StringHelper](./StringHelper.md)
- [ConvertHelper](./ConvertHelper.md)
- [BeanTool](./BeanTool.md)
