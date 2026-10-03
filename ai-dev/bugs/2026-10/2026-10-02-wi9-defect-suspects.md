# 2026-10-02 WI9 测试暴露的产品缺陷嫌疑清单（已修复/裁定，plan 2306 收口）

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


## Fix（2026-10-03 plan 2306 回填）

- **1 UnitsHelper FixedPoint 负数不对称：`fixed`**。encode 按 `floor` 拆分整数/小数（小数恒非负），与 decode 的"符号整数+无符号小数"语义对称。回归：TestUnitsHelper.testFixedPointNegativeNumbersRoundtrip（-1.5/-0.5/-2.25/-0.0）。
- **2 parseYYYYMMDDDate 校验缺失：`fixed`**。第 5/8 字符分隔符必须为 '/'（对齐声明的 YYYY/MM/DD 格式）；Calendar setLenient(false) 拒绝 2 月 30 日等无效日期。无生产调用方（grep 证据，仅测试引用），分隔符收紧无兼容风险。回归：TestExcelDateHelper.testParseYYYYMMDDDate 扩展（"2024/02/30"、"2024-02-29" 必须拒绝）。
- **3 PredefinedColors 索引冲突：`fixed`（裁定：首注册优先）**。indexColors/indexMap 统一 putIfAbsent——主索引与先声明 ARGB 胜出，index2 别名与重复 ARGB（BLACK/AUTOMATIC 同 FF000000）不再覆盖；查找结果由枚举声明顺序唯一确定。回归：TestPredefinedColorsIndex.testColorIndexRoundtrip 特征化断言调整（getByIndex(0x19)==MAROON、getColorIndex("FF000000")==0x08）。
- **4 FLS 空值缺省不一致：`fixed`**。文本路径 encode null 缺省 "0"→""，与二进制路径一致（不伪造数值内容）。回归：TestFixedLengthStringCodec.testTextEncodeNullUsesEmptyDefault。
- **5 RecordTemplateManager vars 无防御拷贝：`fixed`**。prepareVars 防御性拷贝入新 HashMap 再建 scope，调用方不可变 Map（Map.of）不被回写。回归：TestRecordTemplateManager.testBuildRecordWithImmutableVarsMap。
- **6 文本路径类型反推缺失：`fixed`**。无 codec 时按字段声明 stdDataType 转换（STRING/ANY 透传），与 FLS 二进制路径类型语义对称。回归：TestRecordTextRoundtrip 全部 "a" 字段断言翻转为 Integer（含边界用例 99999）。
- **7 record-template 记录级 generator：`adjudicated-not-a-defect`（探针证伪，两轮）**。探针测试 testBuildRecordWithRecordLevelGenerator（record-generator.record-template.xml）。第一轮用裸 `{...}` 起始的 Map 字面量被 xpl 文本域拒绝（`nop.err.xlang.xdef.illegal-content-value-for-std-domain`）——该失败恰与源条目"表达式形式无法编译"的观察吻合，一度支持死代码指控；第二轮改用括号形式 `({"extra": a + 1})` 编译通过且 buildRecordWithGenerator 合并生效。结论：记录级 generator 可用，指控不成立；裸 `{` 起始被拒属 xpl 文本域"节点语法 vs 表达式"的解析约定（需括号消除歧义），建议后续在 xdef 文档中标注该约定（watch-only）。nop-record 测试全绿。

## Affected Files

- nop-format/nop-excel/src/main/java/io/nop/excel/util/UnitsHelper.java
- nop-format/nop-excel/src/main/java/io/nop/excel/format/ExcelDateHelper.java
- nop-format/nop-excel/src/main/java/io/nop/excel/model/color/PredefinedColors.java
- nop-format/nop-record/src/main/java/io/nop/record/codec/impl/AbstractFixedLengthAsciiCodec.java
- nop-format/nop-record/src/main/java/io/nop/record/template/RecordTemplateManager.java
- nop-format/nop-record/src/main/java/io/nop/record/serialization/ModelBasedTextRecordDeserializer.java
- 对应 src/test 下 TestUnitsHelper / TestExcelDateHelper / TestPredefinedColorsIndex / TestFixedLengthStringCodec / TestRecordTemplateManager（新增 record-generator.record-template.xml 探针资源）/ TestRecordTextRoundtrip
