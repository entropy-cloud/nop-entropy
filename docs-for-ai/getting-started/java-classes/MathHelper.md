# MathHelper 使用指南

## 概述

MathHelper是Nop平台提供的**数学处理工具类**，包含全面的数学运算方法，用于统一处理各种数学相关操作。MathHelper设计简洁易用，提供了丰富的数学运算API，涵盖随机数生成、位运算、比较操作、算术运算、数学函数、数值转换和数值比较等功能，是Nop平台中处理数学计算的核心组件。

## 核心功能

### 1. 随机数生成
- `random()`：获取随机数生成器实例，默认使用ThreadLocalRandom
- `secureRandom()`：获取安全随机数生成器实例
- `randomizeDouble(current, randomizationFactor)`：在当前值的基础上进行随机化，返回一个范围内的随机值
- `randomizeLong(current, randomizationFactor)`：在当前值的基础上进行随机化，返回一个范围内的随机长整数值
- `randomSelect(n, m)`：从0到n-1中随机选择m个不重复的整数

### 2. 位运算
- `bor(v1, v2)`：二进制或运算
- `bxor(v1, v2)`：二进制异或运算
- `band(v1, v2)`：二进制与运算
- `bneg(value)`：二进制取反运算
- `sl(v1, v2)`：左移运算
- `sr(v1, v2)`：右移运算
- `usr(v1, v2)`：无符号右移运算

### 3. 比较操作
- `lt(v1, v2)`：小于比较
- `gt(v1, v2)`：大于比较
- `le(v1, v2)`：小于等于比较
- `ge(v1, v2)`：大于等于比较
- `eq(v1, v2)`：等于比较
- `xlangEq(v1, v2)`：XLANG语言的等于比较，处理不同类型的比较
- `compareWithConversion(v1, v2)`：带类型转换的比较
- `compareWithDouble(o1, o2, tolerance)`：带容差的双精度比较

### 4. 算术运算
- `add(v1, v2)`：加法运算
- `minus(v1, v2)`：减法运算
- `multiply(v1, v2)`：乘法运算
- `divide(v1, v2)`：除法运算
- `divide_int(v1, v2)`：整数除法运算
- `mod(v1, v2)`：取模运算

### 5. 数学函数
- `abs(value)`：绝对值
- `ceil(value)`：向上取整
- `floor(value)`：向下取整
- `sqrt(value)`：平方根
- `pow(value, scale)`：幂运算
- `sin(value)`：正弦函数
- `cos(value)`：余弦函数
- `square(value)`：平方
- `exp(value)`：指数函数
- `log(value)`：自然对数
- `log10(value)`：常用对数
- `neg(value)`：取反

### 6. 数值转换
- `getNumericType(value)`：获取数值类型
- `getNumericType(v1, v2)`：获取两个数值的公共类型
- `newInteger(type, value)`：创建整数Number对象
- `newReal(type, value)`：创建实数Number对象
- `roundHalfEven(value, scale)`：银行家算法四舍五入

### 7. 数学工具函数
- `nextSeq()`：获取下一个序列值
- `nextPowerOfTwo(value)`：获取大于等于给定值的下一个2的幂
- `safeNextPowerOfTwo(value)`：安全地获取下一个2的幂
- `nextPowerOfTwoForLong(value)`：获取大于等于给定长整数值的下一个2的幂
- `isPowerOfTwoForLong(x)`：判断是否为2的幂
- `modPowerOfTwo(a, b)`：对2的幂取模
- `modPowerOfTwoForLong(a, b)`：对2的幂取模（长整数）
- `gcd(n, m)`：求最大公约数
- `gcd(arr)`：求数组的最大公约数
- `toShortHash(value)`：转换为短哈希值

## 示例代码

```java
import io.nop.commons.util.MathHelper;

// 随机数生成
IRandom random = MathHelper.random();
double randomDouble = random.nextDouble();
long randomLong = random.nextLong();

// 随机化
long randomized = MathHelper.randomizeLong(30, 0.1); // 在27-33之间随机
List<Integer> selected = MathHelper.randomSelect(10, 3); // 从0-9中随机选择3个数字

// 位运算
Number result1 = MathHelper.bor(5, 3); // 7 (二进制 111)
Number result2 = MathHelper.bxor(5, 3); // 6 (二进制 110)
Number result3 = MathHelper.band(5, 3); // 1 (二进制 001)
Number result4 = MathHelper.sl(1, 2); // 4 (二进制 100)

// 比较操作
boolean isLess = MathHelper.lt(5, 10); // true
boolean isGreater = MathHelper.gt(10, 5); // true
boolean isEqual = MathHelper.eq(5, 5); // true

// 算术运算
Number sum = MathHelper.add(5, 3); // 8
Number difference = MathHelper.minus(5, 3); // 2
Number product = MathHelper.multiply(5, 3); // 15
Number quotient = MathHelper.divide(5, 3); // 1.666...
Number remainder = MathHelper.mod(5, 3); // 2

// 数学函数
Number absolute = MathHelper.abs(-5); // 5
Number ceiling = MathHelper.ceil(1.2); // 2.0
Number floorValue = MathHelper.floor(1.8); // 1.0
Number squareRoot = MathHelper.sqrt(16); // 4.0
Number power = MathHelper.pow(2, 3); // 8.0
Number sine = MathHelper.sin(MathHelper.PI / 2); // 1.0

// 数学工具函数
int nextPower = MathHelper.nextPowerOfTwo(5); // 8
boolean isPower = MathHelper.isPowerOfTwoForLong(8); // true
int gcd = MathHelper.gcd(12, 18); // 6
```

## 最佳实践

1. **优先使用**：所有数学运算优先使用MathHelper，避免直接使用Java Math类或第三方库
2. **类型安全**：MathHelper处理了不同类型之间的转换，确保运算结果类型正确
3. **空值处理**：MathHelper会处理null值，避免空指针异常
4. **溢出保护**：部分方法提供了溢出保护，确保运算结果正确
5. **选择合适方法**：根据场景选择最优方法，例如需要整数结果时使用divide_int而不是divide

## 注意事项

- 所有方法都是静态的，直接调用
- null值处理：大部分方法会处理null值，返回合理结果
- 类型转换：MathHelper会自动处理不同数值类型之间的转换
- 除零处理：当除数为零时，返回NaN而不是抛出异常
- 精度问题：浮点数运算可能存在精度问题，如需高精度计算，建议使用BigDecimal
- 性能考虑：频繁调用的场景，建议使用原始类型运算，避免装箱拆箱开销

## 替代方案

避免使用以下第三方库：
- Apache Commons Math
- Google Guava Math
- 自定义数学工具类

MathHelper提供了全面的数学运算功能，能够满足大多数场景的需求，同时保持了代码的简洁性和易用性。