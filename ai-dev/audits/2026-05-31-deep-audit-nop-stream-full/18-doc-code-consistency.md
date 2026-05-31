# 维度 18：文档-代码一致性

## P1 发现（2个）

### [维度18-01] window-design.md "已知限制"严重过时
- **文件**: ai-dev/design/nop-stream/window-design.md:354
- **严重程度**: P1
- **现状**: Plan 51 已修复 WindowedStreamImpl.apply/aggregate/reduce 但文档仍标注为限制。

### [维度18-02] time-model-design.md 内部自相矛盾
- **文件**: ai-dev/design/nop-stream/time-model-design.md:199-233
- **严重程度**: P1
- **现状**: §7 声称 TimestampsAndWatermarksOperator 已集成但 §9 否认。

## P2 发现（6个）
- time-model-design.md 错误标注模块位置（runtime→core）
- README "五层管线"省略 StreamModel
- module-groups.md 子模块描述与 README 不一致
- architecture.md 声称 core→api 但 api 为空
- time-model-design.md Flink 对比表 "watermarkInterval=0" 过时
- 多份设计文档"已知限制"缺与 component-roadmap 交叉引用

## P3 发现（2个）
- source-anchors.md 完全缺少 nop-stream 锚点
- error-handling.md 对 StreamException 构造器描述不完整
