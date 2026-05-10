# 常用 Java Helper

本页只保留当前仓库最常用、最值得 AI 默认优先选择的 helper。

简单到一眼就能写对的 JDK 调用仍然可以直接用；但当仓库里已经有统一 helper 时，优先跟随这里，避免散用第三方库或自造转换逻辑。

## 速查表

| Helper | 适用场景 | 常用方法 |
|------|---------|---------|
| `CoreMetrics` | 获取当前时间戳、日期、可测试时钟 | `currentTimeMillis()`、`currentTimestamp()`、`currentDate()`、`currentDateTime()`、`nanoTime()` |
| `StringHelper` | 字符串判空、拆分、命名转换、文件类型处理 | `isEmpty`、`isBlank`、`join`、`splitToArray`、`camelCaseToUnderscore`、`camelCase`、`generateUUID`、`fileType` |
| `DateHelper` | 日期解析、格式化、月初月末、时区转换 | `parseDate`、`parseDateTime`、`formatDate`、`formatDateTime`、`firstDayOfMonth`、`lastDayOfMonth`、`dateDiff`、`toUTC` |
| `ConvertHelper` | 类型转换、CSV 转列表、字符串转日期时间 | `toInt`、`toLong`、`toDouble`、`toBoolean`、`toLocalDate`、`toLocalDateTime`、`toList`、`toSet`、`toCsvList`、`convertTo` |
| `JsonTool` | JSON / YAML 解析、序列化、资源加载 | `parseMap`、`parseBeanFromText`、`parseBeanFromYaml`、`stringify`、`serialize`、`loadBean` |

## `CoreMetrics`

获取当前时间戳、日期。**禁止使用 `System.currentTimeMillis()` 或 `System.nanoTime()`**，一律改用 `CoreMetrics`，这样单元测试可以通过 `IClock` 注入可控时钟。

```java
long now = CoreMetrics.currentTimeMillis();
java.sql.Timestamp ts = CoreMetrics.currentTimestamp();
LocalDate today = CoreMetrics.currentDate();
LocalDateTime dt = CoreMetrics.currentDateTime();
```

定义锚点：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/time/CoreMetrics.java`

## `StringHelper`

适合字符串工具逻辑，不要默认引入 Apache Commons `StringUtils`。

```java
if (StringHelper.isBlank(name)) {
    return;
}

String[] parts = StringHelper.splitToArray("a,b,c", ',');
String tableName = StringHelper.camelCaseToUnderscore("userName", true);
String className = StringHelper.camelCase("user_name", true);
String id = StringHelper.generateUUID();
String fileType = StringHelper.fileType("a.orm.xml");
```

定义锚点：`nop-kernel/nop-commons/src/main/java/io/nop/commons/util/StringHelper.java`

## `DateHelper`

适合统一日期解析、格式化和常见区间计算。

```java
LocalDate day = DateHelper.parseDate("2026-04-09");
LocalDateTime ts = DateHelper.parseDateTime("2026-04-09 10:30:00", "yyyy-MM-dd HH:mm:ss");

String text = DateHelper.formatDate(day, "yyyy-MM-dd");
LocalDate monthStart = DateHelper.firstDayOfMonth(day);
LocalDate monthEnd = DateHelper.lastDayOfMonth(day);
Long diff = DateHelper.dateDiff(day, monthStart);
```

定义锚点：`nop-kernel/nop-commons/src/main/java/io/nop/commons/util/DateHelper.java`

## `ConvertHelper`

适合统一类型转换。不要手写一堆 `parseInt` / `instanceof` / CSV split 逻辑。

最简例子可以先用 `NopException::new`；如果是业务输入转换，优先传自定义 `errorFactory` 补充字段信息。

```java
Integer pageNo = ConvertHelper.toInt(input, NopException::new);
Boolean enabled = ConvertHelper.toBoolean(flag, NopException::new);
LocalDate expireDate = ConvertHelper.toLocalDate(dateText, NopException::new);
List<String> ids = ConvertHelper.toCsvList("a,b,c", NopException::new);
Set<String> scopes = ConvertHelper.toCsvSet(scopeText, NopException::new);
```

定义锚点：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/ConvertHelper.java`

## `JsonTool`

适合 JSON / YAML 读写和资源加载。默认不要直接切到别的 JSON 库。

```java
LoginRequest request = JsonTool.parseBeanFromText(text, LoginRequest.class);
Map<String, Object> raw = JsonTool.parseMap(text);

String compact = JsonTool.stringify(result);
String pretty = JsonTool.serialize(result, true);
String yaml = JsonTool.serializeToYaml(result);
```

定义锚点：`nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonTool.java`

## 默认避免

1. 默认引入 Apache Commons / Guava / 另一套 JSON 工具完成仓库已有 helper 能做的事。
2. 手写重复的 CSV split、布尔转换、日期转换逻辑。
3. 因为未核对的方法名而误用仓库中不存在的 helper API。

## 相关文档

- `../02-core-guides/dto-json-and-message-beans.md`
- `../02-core-guides/domain-logic-and-ddd.md`
- `./source-anchors.md`
