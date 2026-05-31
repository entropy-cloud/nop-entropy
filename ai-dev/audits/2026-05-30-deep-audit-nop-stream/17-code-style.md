# 维度 17：代码风格与规范

## P2 发现

| 编号 | 文件 | 行号 | 摘要 |
|------|------|------|------|
| 17-09 | NFA.java | 188,201 | IterativeCondition raw type（丢失泛型参数 <T>） |
| 17-11 | WindowAggregationOperator.java | 37 | trigger 字段缺少访问修饰符（package-private vs 同类 private） |

## P3 发现

共 13 项 P3，主要包括：

- **static import 夹杂在普通 import 之间** (17-01~17-06): NFACompiler、WindowAggregationOperator、WindowOperator、GraphModelCheckpointExecutor、StreamExecutionEnvironment — 模块级系统性问题，建议 IDE formatter 统一修正
- **通配符 import** (17-07, 17-08): WindowAggregationOperator `java.util.*`、GraphModelCheckpointExecutor `checkpoint.*`
- **FQN 使用** (17-14, 17-15): GraphModelCheckpointExecutor 方法内大量 FQN、WindowOperator LOG 字段 FQN
- **raw type** (17-10): NFA.java 使用已弃用 Collections.EMPTY_LIST
- **接口命名** (17-12): 数十公共接口未遵循 I 前缀（Flink 迁移遗留）
- **System.out** (17-13): PrintSink/PrintSinkFunction（功能意图明确，P3）

未发现风格问题掩盖真实逻辑错误的情形。
