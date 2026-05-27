# 维度 17 审计报告：nop-stream 代码风格与规范

> 审计日期: 2026-05-27
> 范围: 407个main java文件

## 发现

### [17-01] NopStreamErrors 全部 10 条错误消息使用中文
- **文件**: `nop-stream-core/.../exceptions/NopStreamErrors.java:24-53`
- **严重程度**: P1
- **建议**: 全部改为英文消息模板。

### [17-02] NopCepErrors 全部 3 条错误消息使用中文
- **文件**: `nop-stream-cep/.../NopCepErrors.java:19-27`
- **严重程度**: P1
- **建议**: 全部改为英文消息模板。

### [17-03] 导入语句组内未按字母顺序排列（~205个文件）
- **严重程度**: P2
- **建议**: 使用 IDE "Optimize Imports" 批量修复。

### [17-04] 导入组间顺序违反（1个文件）
- **文件**: `FraudDetectionDemo.java:10-13`
- **严重程度**: P2

### [17-05] 大量使用通配符导入（57个文件）
- **严重程度**: P3

### [17-06] 注释掉的代码残留
- **文件**: `SharedBuffer.java:122-151` (~30行)
- **严重程度**: P3

### [17-07] 注释掉的异常抛出逻辑
- **文件**: `StreamRecord.java:82-85`
- **严重程度**: P3

### [17-08] 注释中使用 tab 缩进（5个文件）
- **严重程度**: P3

### [17-09] 中文注释出现在非 _gen 文件中
- **严重程度**: P3

## 通过项
- PascalCase 类名 ✅、camelCase 方法/变量 ✅、UPPER_SNAKE_CASE 常量 ✅
- io.nop.stream.* 包名 ✅、无 TODO/FIXME/HACK ✅

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 17-01 | P1 | NopStreamErrors.java | ErrorCode 中文消息 |
| 17-02 | P1 | NopCepErrors.java | ErrorCode 中文消息 |
| 17-03 | P2 | ~205文件 | 导入组内无序 |
| 17-04 | P2 | FraudDetectionDemo.java | 导入组间顺序错误 |
| 17-05 | P3 | 57文件 | 通配符导入 |
| 17-06 | P3 | SharedBuffer.java | 注释掉的代码 |
| 17-07 | P3 | StreamRecord.java | 注释掉的异常 |
| 17-08 | P3 | 5文件 | tab缩进 |
| 17-09 | P3 | 多文件 | 中文注释 |
