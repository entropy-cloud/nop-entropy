# DateHelper 使用指南

## 概述

DateHelper是Nop平台提供的日期时间处理工具类，用于统一处理日期时间相关操作，替代JDK原生API和第三方库。

## 核心功能

### 1. 日期格式化
- `formatDate(date, pattern)`：格式化LocalDate, 返回String
- `formatDateTime(dateTime, pattern)`：格式化LocalDateTime, 返回String
- `formatTime(time, pattern)`：格式化Time，返回String
- `formatTimestamp(timestamp, pattern)`：格式化Timestamp，返回String

### 2. 日期解析
- `parseDate(str, pattern)`：解析日期字符串，返回LocalDate类型
- `parseDateTime(str, pattern)`：解析日期时间字符串，返回LocalDateTime类型
- `parseTime(str, pattern)`：解析时间字符串，返回Time类型

### 3. 日期计算
- `firstDayOfMonth(date)`：当月第一天
- `lastDayOfMonth(date)`：当月最后一天
- `firstDayOfNextMonth(date)`：下个月的第一天
- `firstDayOfYear(date)`：当年的第一天
- `lastDayOfYear(date)`：当年的最后一天
- `dateDiff(date1, date2)`：计算日差
- `yearDiff(date1, date2)`：计算年差
- `monthDiff(date1, date2)`：计算月差

### 4. 时区转换
- `toTimeZone(localDateTime, zoneId)`：转换时区
- `toUTC(localDateTime)`：转换为UTC时间
- `fromUTC(localDateTime)`：从UTC转换为本地时间

## 示例代码

```java
// 格式化日期
String dateStr = DateHelper.formatDate(LocalDate.now(), "yyyy-MM-dd"); // 2026-01-01

// 解析日期
LocalDate date = DateHelper.parseDate("2026-01-01"); // LocalDate对象

// 获取当月第一天
LocalDate firstDay = DateHelper.firstDayOfMonth(LocalDate.now()); // 当月1日

// 计算日差
long days = DateHelper.dateDiff(LocalDate.now(), LocalDate.of(2026, 12, 31)); // 剩余天数


LocalDateTime utcTime = DateHelper.toUTC(LocalDateTime.now()); // 转为UTC
LocalDateTime localTime = DateHelper.fromUTC(utcTime); // 转回本地时区
```

## 最佳实践

1. **优先使用**：所有日期时间操作优先使用DateHelper
2. **使用标准格式**：优先使用`yyyy-MM-dd HH:mm:ss`格式
3. **类型安全**：使用LocalDate、LocalDateTime等新日期类型

## 注意事项

- 所有方法都是静态的，直接调用
- 默认时区：使用系统时区，可通过参数指定
- 线程安全：所有方法都是线程安全的
- 支持JDK8+日期类型：LocalDate、LocalDateTime、Instant等

## 替代方案

避免使用以下类：
- `SimpleDateFormat`：线程不安全
- `Calendar`：API复杂，不易使用
- Joda-Time：已被JDK8+日期API替代
