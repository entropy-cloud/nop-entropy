# 维度 02/05/08：零问题维度

## 维度 02：模块职责与文件边界 — 4个低级发现

- F-02-01 (P3): WindowAggregationOperator(core) 与 WindowOperator(runtime) 功能重叠但定位不清
- F-02-02 (P3): ShardPrefixedKey 在 core 内有两个同义类
- F-02-03 (信息): core execution 包含完整执行逻辑，与 runtime 职责重叠（有意分层）
- F-02-04 (信息): 4个空占位子模块
- F-02-05 (信息): 大文件体积评估（1099行 NFACompiler/WindowOperator）但职责单一
- F-02-06 (无问题): _gen/ 下4个生成文件无手写修改痕迹

## 维度 05：生成管线完整性 — 零问题

CepPattern 模型生成管线（pattern.xdef → gen-cep-xdsl.xgen → _CepPattern*.java）完整正确，所有生成产物与源模型一致。其他子模块无生成代码。

## 维度 08：IoC 与 Bean 配置 — 零问题

nop-stream 完全不使用 NopIoC，采用 Java SPI + IConfigReference 标准模式。无 beans.xml、无 @Inject、无 Spring 依赖。4个生成文件无手写修改痕迹。
