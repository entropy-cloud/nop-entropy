# nop-stream

Nop 平台的流处理引擎。基于 Apache Flink 核心算法的简化实现，定位为可分布式执行的流处理框架。

## 模块

| 模块 | 状态 | 说明 |
|------|------|------|
| `nop-stream-core` | 活跃 | 核心抽象、API、执行引擎、算子、状态管理 |
| `nop-stream-runtime` | 活跃 | 窗口算子、Checkpoint 协调器、存储实现 |
| `nop-stream-cep` | 活跃 | CEP 引擎（NFA + Pattern API + 声明式模型） |
| `nop-stream-connector` | 活跃 | Source/Sink 连接器 |
| `nop-stream-fraud-example` | 活跃 | 欺诈检测示例 |
| `nop-stream-api` | 规划中 | 公共 API 提取 |
| `nop-stream-checkpoint` | 规划中 | Checkpoint 独立模块 |
| `nop-stream-flink` | 规划中 | Flink 执行引擎后端 |
| `nop-stream-flow` | 规划中 | 声明式流编排 |

## 设计文档

- [DESIGN.md](DESIGN.md) — 完整设计文档（架构、核心子系统、关键设计决策）

## 快速开始

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
env.fromElements(1, 2, 3, 4, 5)
   .map(x -> x * 2)
   .filter(x -> x > 4)
   .print();
env.execute("simple-pipeline");  // 统一走图模型路径：StreamGraph → JobGraph → TaskExecutor
```

## 构建

```bash
./mvnw clean install -pl nop-stream -am -DskipTests -T 1C
```