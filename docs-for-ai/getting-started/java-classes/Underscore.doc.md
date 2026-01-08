# Underscore 使用指南

## 概述

Underscore是Nop平台提供的**集合处理工具类**，仿照JavaScript的underscore库设计，提供了全面的集合操作方法，用于统一处理各种集合相关操作。
Underscore设计简洁易用，提供了丰富的集合运算API，涵盖集合操作、搜索与筛选、排序与分组、映射与转换、统计与计算、对象操作、数组操作、连接与合并等功能，是Nop平台中处理集合数据的核心组件。

## 核心功能

### 1. 集合操作

- `isEmpty(obj)`：检查对象是否为空
- `first(list)`：获取集合的第一个元素
- `first(list, n)`：获取集合的前n个元素
- `last(list)`：获取集合的最后一个元素
- `last(list, n)`：获取集合的后n个元素
- `unique(list)`：去除集合中的重复元素
- `flatten(list)`：扁平化集合
- `chunk(list, size)`：将集合分割成指定大小的块
- `chunkIt(iterator, size)`：将迭代器分割成指定大小的块
- `compact(list)`：移除集合中的假值元素

### 2. 搜索与筛选

- `findWhere(list, props)`：查找符合条件的第一个元素
- `findWhere(list, key, value)`：根据指定键值查找第一个元素
- `findIndex(list, filter)`：查找符合条件的元素索引
- `where(list, props)`：查找所有符合条件的元素
- `where(list, key, value)`：根据指定键值查找所有元素
- `partition(list, predicate)`：将集合分割为符合条件和不符合条件的两部分

### 3. 排序与分组

- `sortBy(list, key)`：根据指定属性排序
- `sortByFn(list, fn)`：根据函数结果排序
- `groupBy(list, key)`：根据指定属性分组
- `groupByFn(list, fn)`：根据函数结果分组
- `countBy(list, key)`：根据指定属性统计元素个数
- `countByFn(list, fn)`：根据函数结果统计元素个数

### 4. 映射与转换

- `pluck(list, propName)`：提取集合元素的指定属性值
- `pluckUnique(list, propName)`：提取集合元素的指定属性值，并去重
- `pluckThenJoin(list, propName)`：提取集合元素的指定属性值并连接成字符串
- `pluckAsMap(list, keyProp, valueProp)`：将集合转换为Map，指定键和值的属性
- `indexBy(list, key)`：将集合转换为Map，指定键属性
- `indexByFn(list, fn)`：将集合转换为Map，使用函数生成键
- `indexByFields(list, fields)`：根据多个字段生成键，将集合转换为Map
- `toKeyedList(list, keyProp)`：将集合转换为KeyedList
- `toJObject(obj)`：将对象转换为JObject

### 5. 统计与计算

- `sum(list)`：计算集合元素的总和
- `sum(list, fn)`：根据函数结果计算总和
- `sumBy(list, prop)`：根据指定属性计算总和
- `avg(list)`：计算集合元素的平均值
- `avg(list, fn)`：根据函数结果计算平均值
- `max(list)`：获取集合中的最大值元素
- `max(list, fn)`：根据函数结果获取最大值元素
- `min(list)`：获取集合中的最小值元素
- `min(list, fn)`：根据函数结果获取最小值元素

### 6. 对象操作

- `pairs(map)`：将Map转换为键值对列表
- `invert(map)`：反转Map的键值对
- `filterNull(obj)`：过滤Map或List中的null值
- `omit(map, names)`：从Map中移除指定属性
- `delete(map, names)`：从Map中删除指定属性
- `pick(mapOrObj, names)`：从Map或对象中提取指定属性
- `pickNotNull(mapOrObj, names)`：从Map或对象中提取非null的指定属性
- `rename(map, mapping)`：重命名Map中的属性
- `mapFields(obj, mapping)`：根据映射关系转换对象属性
- `mergeMap(mapA, mapB)`：合并两个Map

### 7. 数组操作

- `join(list, sep)`：将集合元素连接成字符串
- `removeWhere(list, key, value)`：移除符合条件的元素
- `removeAllWhere(list, key, values)`：移除属性值在指定集合中的元素
- `retainAllWhere(list, key, values)`：保留属性值在指定集合中的元素，移除其他元素

### 8. 连接与合并

- `leftjoinMerge(list, refList, leftProp, rightProp, refProp)`：在内存中执行左连接，将右侧数据合并到左侧
- `hashJoin(list1, list2, leftProp, rightProp, processor)`：执行哈希连接，根据指定属性关联两个集合

## 示例代码

```java
import io.nop.commons.util.Underscore;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 定义测试数据
class User {
    private String name;
    private int age;
    private String city;

    // 构造函数、getter和setter省略
}

List<User> users = Arrays.asList(
    new User("Alice", 25, "New York"),
    new User("Bob", 30, "London"),
    new User("Charlie", 25, "New York"),
    new User("David", 35, "London")
);

// 集合操作
boolean empty = Underscore.isEmpty(users); // false
User first = Underscore.first(users); // Alice
List<User> firstTwo = Underscore.first(users, 2); // [Alice, Bob]
User last = Underscore.last(users); // David
List<User> lastTwo = Underscore.last(users, 2); // [Charlie, David]

// 搜索与筛选
User user = Underscore.findWhere(users, "name", "Bob"); // Bob
List<User> newYorkUsers = Underscore.where(users, "city", "New York"); // [Alice, Charlie]
int index = Underscore.findIndex(users, u -> u.getAge() > 30); // 3 (David)

// 排序与分组
List<User> sortedByAge = Underscore.sortBy(users, "age"); // 按年龄排序
Map<String, List<User>> groupedByCity = Underscore.groupBy(users, "city"); // 按城市分组
Map<String, Integer> countByCity = Underscore.countBy(users, "city"); // 按城市统计个数

// 映射与转换
List<Object> names = Underscore.pluck(users, "name"); // [Alice, Bob, Charlie, David]
Map<String, User> userMap = Underscore.indexBy(users, "name"); // 按名称索引
String joinedNames = Underscore.pluckThenJoin(users, "name"); // "Alice,Bob,Charlie,David"

// 统计与计算
Number sumAge = Underscore.sumBy(users, "age"); // 115
Number avgAge = Underscore.avg(users, u -> u.getAge()); // 28.75
User oldest = Underscore.max(users, u -> u.getAge()); // David
User youngest = Underscore.min(users, u -> u.getAge()); // Alice

// 对象操作
Map<String, Object> userMap = new HashMap<>();
userMap.put("name", "Alice");
userMap.put("age", 25);
userMap.put("city", "New York");

Map<String, Object> picked = Underscore.pick(userMap, Arrays.asList("name", "age")); // {name=Alice, age=25}
Map<String, Object> renamed = Underscore.rename(userMap, Map.of("name", "fullName")); // {fullName=Alice, age=25, city=New York}

// 数组操作
List<String> cities = Arrays.asList("New York", "London", "Paris", "New York");
List<String> uniqueCities = Underscore.unique(cities); // [New York, London, Paris]
List<String> compactList = Underscore.compact(Arrays.asList("a", null, "b", "", "c")); // [a, b, c]

// 连接与合并
List<User> refList = Arrays.asList(
    new User("Alice", 25, "New York"),
    new User("Bob", 30, "London")
);

Underscore.leftjoinMerge(users, refList, "name", "name", "refData"); // 将refList合并到users
```

## 最佳实践

1. **优先使用**：所有集合操作优先使用Underscore，避免直接使用Java集合API或第三方库
2. **类型安全**：Underscore处理了不同类型之间的转换，确保运算结果类型正确
3. **空值处理**：Underscore会处理null值，避免空指针异常
4. **性能考虑**：对于大数据集，建议使用数据库查询或流式处理，而非内存操作
5. **选择合适方法**：根据场景选择最优方法，例如需要唯一键时使用indexBy而非groupBy
6. **链式操作**：可以将多个Underscore方法链式调用，提高代码可读性

## 注意事项

- 所有方法都是静态的，直接调用
- null值处理：大部分方法会处理null值，返回合理结果
- 性能限制：对于超大数据集，内存操作可能导致性能问题
- 线程安全：Underscore方法本身是线程安全的，但集合对象的线程安全性取决于输入集合
- 不可变操作：大部分方法返回新的集合，不会修改原集合

## 替代方案

避免使用以下第三方库：

- Apache Commons Collections
- Google Guava Collections
- 自定义集合工具类

Underscore提供了全面的集合操作功能，能够满足大多数场景的需求，同时保持了代码的简洁性和易用性。
