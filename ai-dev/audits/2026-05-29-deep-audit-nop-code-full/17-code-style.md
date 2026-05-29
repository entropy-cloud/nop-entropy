# 维度17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] 多个 BizModel 文件在 package 声明前有空行

- **文件**: 10 个 BizModel 文件（NopCodeIndexBizModel.java 等）
- **行号**: L1-2
- **证据片段**:
  ```java
                      ← 空行
  package io.nop.code.service.entity;
  ```
- **严重程度**: P3
- **现状**: 10 个 BizModel 文件第一行是空行，package 在第二行。
- **建议**: 删除所有文件首行的空行。
- **信心水平**: 确定
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度17-02] 多个 BizModel 文件 { 前缺少空格

- **文件**: 6 个 BizModel 文件
- **行号**: L11
- **证据片段**:
  ```java
  public class NopCodeFlowBizModel extends CrudBizModel<NopCodeFlow> implements INopCodeFlowBiz{
  ```
- **严重程度**: P3
- **现状**: implements XxxBiz{ 处缺少空格。
- **建议**: 在 { 前添加空格。
- **信心水平**: 确定
- **误报排除**: 无。
- **复核状态**: 未复核

## 通过项

1. 无 System.out/System.err 使用
2. import 分组遵循 java.* → jakarta.* → third-party → io.nop.* 约定
3. 命名规范遵循 PascalCase/camelCase/UPPER_SNAKE_CASE
4. NopCodeConfigs 和 NopCodeConstants 为空壳（P3，已记录在维度02）
