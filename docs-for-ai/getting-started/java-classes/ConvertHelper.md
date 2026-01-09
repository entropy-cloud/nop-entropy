# ConvertHelper 使用指南

## 概述
ConvertHelper 是 Nop 平台提供的**类型转换工具类**，用于统一、安全地处理各种类型转换操作，替代 JDK 原生转换方法，提供灵活的错误处理机制。

## 核心功能

### 1. 基本类型转换
- `toInt(obj, errFactory)`：转换为 Integer
- `toLong(obj, errFactory)`：转换为 Long  
- `toDouble(obj, errFactory)`：转换为 Double
- `toBoolean(obj, errFactory)`：转换为 Boolean（支持"true"/"yes"/"1"等多种格式）
- `toString(obj, errFactory)`：转换为 String

### 2. 日期时间转换
- `toLocalDate(obj, errFactory)`：转换为 LocalDate
- `toLocalDateTime(obj, errFactory)`：转换为 LocalDateTime
- `toTimestamp(obj, errFactory)`：转换为 Timestamp
- `toDuration(obj, errFactory)`：转换为 Duration

### 3. 集合类型转换
- `toList(obj, errFactory)`：转换为 List（支持List、Iterable、Iterator）
- `toSet(obj, errFactory)`：转换为 Set（支持Set、Iterable、Iterator）
- `toCsvList(obj, errFactory)`：转换为 List，字符串按逗号分隔
- `toCsvSet(obj, errFactory)`：转换为 Set，字符串按逗号分隔

### 4. 通用转换
- `convertTo(targetType, obj, errFactory)`：通用类型转换
- `convertToWithDefault(targetType, obj, defaultValue, errFactory)`：带默认值的转换

## 使用示例

### 基础用法
```java
import io.nop.api.core.convert.ConvertHelper;

// 简单转换
Integer num = ConvertHelper.toInt("123", NopException::new);
LocalDate date = ConvertHelper.toLocalDate("2026-01-01", NopException::new);
List<String> list = ConvertHelper.toCsvList("a,b,c", NopException::new);
```

### 错误处理与异常信息增强
errFactory 允许传递详细的错误上下文信息，便于问题定位：

```java
// 示例1：添加字段名和原始值信息
String fieldName = "age";
String inputValue = "abc";

Integer age = ConvertHelper.toInt(inputValue, 
    err -> new NopException(err)
        .param(ARG_PROP_NAME, fieldName)      // 参数名：age
        .param(ARG_VALUE, inputValue)         // 原始值：abc
        .param(ARG_EXPECTED_TYPE, "Integer")  // 期望类型
);

// 示例2：抛出自定义异常码
String configValue = "invalid_date";

LocalDate expireDate = ConvertHelper.toLocalDate(configValue,
    err -> new NopException(ERR_CONFIG_INVALID_FORMAT)  // 自定义错误码
        .param(ARG_CONFIG_KEY, "system.expire_date")
        .param(ARG_CONFIG_VALUE, configValue)
        .param(ARG_REQUIRED_FORMAT, "yyyy-MM-dd")
);

```

## 错误处理模式对比

```java
// 传统方式：信息有限
try {
    Integer.parseInt("abc");
} catch (NumberFormatException e) {
    // 只能获得"For input string: \"abc\""这样简单的信息
}

// ConvertHelper 方式：信息丰富
ConvertHelper.toInt("abc", 
    err -> new NopException("ERR_INVALID_NUMBER")
        .param(ARG_FIELD, "userScore")
        .param(ARG_INPUT, "abc")
        .param(ARG_MIN, 0)
        .param(ARG_MAX, 100));
// 可获得包含字段名、业务规则等完整上下文的错误信息
```

## 使用规则

1. **统一使用**：所有类型转换都应使用 ConvertHelper
2. **必须处理异常**：每个转换都需要提供 errFactory 参数
3. **类型明确**：优先使用具体转换方法（如 toInt），而非通用 convertTo
4. **空值安全**：方法会合理处理 null 值（通常返回 null）

## 注意事项

- 所有方法均为静态方法，线程安全
- 转换失败时通过 errFactory 抛出异常
- 集合转换支持多种输入类型（字符串、数组、Collection等）
- CSV 转换默认使用逗号分隔符

## 替代方案对比

**推荐使用：**
```java
ConvertHelper.toInt("123", NopException::new)
ConvertHelper.toBoolean("yes", NopException::new)
```

**避免使用：**
```java
Integer.parseInt("123")           // 无统一异常处理
Boolean.parseBoolean("yes")       // 仅识别"true"
手动类型转换代码                 // 易出错，维护困难
```