# nop-stream

Nop 平台的流处理引擎，定位为**声明式图模型驱动的可分布式执行引擎**。

核心模型是 StreamModel（可序列化的算子图及其组件注册表），可由 XDSL 声明式定义、Java DataStream API 编程构造、或 Delta 定制合成。三种入口最终生成同一套 canonical StreamModel，经统一的五层执行管线（StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology）编译执行。

## 模块

| 模块 | 状态 | 说明 |
|------|------|------|
| `nop-stream-core` | 活跃 | StreamModel、StreamGraph/JobGraph、执行引擎、算子、状态管理 |
| `nop-stream-runtime` | 活跃 | 窗口算子、Checkpoint 协调器、存储实现、分布式执行框架 |
| `nop-stream-cep` | 活跃 | CEP 引擎（NFA + Pattern API + 声明式模型） |
| `nop-stream-connector` | 活跃 | Source/Sink 连接器（nop-batch 桥接、CDC、消息队列） |
| `nop-stream-fraud-example` | 活跃 | 欺诈检测示例 |
| `nop-stream-api` | 规划中 | 公共 API 提取 |
| `nop-stream-checkpoint` | 规划中 | Checkpoint 独立模块 |
| `nop-stream-flink` | 规划中 | Flink 执行引擎后端 |
| `nop-stream-flow` | 规划中 | XDSL 声明式流编排 |

## 设计文档

- `ai-dev/design/nop-stream/` — 完整设计文档目录（架构、核心子系统、关键设计决策）

## 快速开始（Java DataStream API 方式）

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
env.fromElements(1, 2, 3, 4, 5)
   .map(x -> x * 2)
   .filter(x -> x > 4)
   .print();
env.execute("simple-pipeline");  // 统一走图模型路径：StreamGraph → JobGraph → TaskExecutor
```

> DataStream API 是 StreamModel 的编程构造器，不是最终用户的主入口。主入口是 XDSL 声明式图模型定义（规划中，见 nop-stream-flow 模块）。

## 构建

```bash
./mvnw clean install -pl nop-stream -am -DskipTests -T 1C
```