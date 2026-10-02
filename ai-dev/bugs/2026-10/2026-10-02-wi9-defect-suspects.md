# 2026-10-02 WI9 测试暴露的产品缺陷嫌疑清单（未修，待独立立项）

> 来源：plan 2301（WI9 excel/record 补强）执行期发现。按 roadmap 硬边界记录，修复独立立项。

## 1. UnitsHelper FixedPoint 转换负数不对称（P2 嫌疑）

- Problem：`doubleToFixedPoint(-1.5)` → `fixedPointToDouble` 得 -0.5（encode 按 floor 拆分、decode 算术右移，符号语义不一致）。

## 2. ExcelDateHelper.parseYYYYMMDDDate 校验缺失（P3 嫌疑）

- Problem：不校验分隔符（"2024-02-29" 合法）；2 月 30 日经 lenient Calendar 静默归一化为 3 月 1 日。

## 3. PredefinedColors 索引双向映射冲突（P2 嫌疑）

- Problem：`getColorIndex("FF000000")` 被 AUTOMATIC 后写覆盖为 0x40；`getByIndex(0x19)` 被 PLUM 的 index2 抢注返回 PLUM 而非 MAROON——注册顺序敏感的映射不稳。

## 4. AbstractFixedLengthAsciiCodec 空值缺省不一致（P3 嫌疑）

- Problem：文本路径 encode 缺省 "0"，二进制路径缺省 ""。

## 5. RecordTemplateManager vars 无防御性拷贝（P3 嫌疑）

- Problem：传入不可变 Map（Map.of）时 evalScope 原地写抛 UnsupportedOperationException。

## 6. ModelBasedTextRecordDeserializer 文本路径类型反推缺失（P3 嫌疑）

- Problem：字段未配 codec 时 `type="int"` 字段 roundtrip 回 String（binary FLS 路径会转换），两路径类型语义不对称。

## 7. record-template.xdef 记录级 generator 疑似死代码（P2 嫌疑）

- Problem：tag-body 编译产物 `invoke()` 抛 UnsupportedOperationException、表达式形式无法按 full-expr 编译——`buildRecordWithGenerator` 的 Map 合并分支疑似死代码。
