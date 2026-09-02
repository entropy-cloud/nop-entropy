# nop-stream 快速起步脚手架

> 定位：nop-stream 入门脚手架——一条命令生成包含 **3 个入门示例拓扑** 的可运行 Maven 工程。
> 产品文档入口：用户指南 `docs-for-ai/03-modules/nop-stream-user-guide.md`；连接器目录 `docs-for-ai/03-modules/nop-stream-connectors.md`。
> 进阶复合示例（CDC→CEP→2PC 等产品级场景）见 `nop-stream-fraud-example`，与本入门层分层互补。

## 三个入门拓扑（两扇正门全覆盖）

| # | 拓扑 | 入口形态 | 教会什么 |
|---|---|---|---|
| 1 | `Topology1MinimalPipeline` | **Java DataStream API** | 最小链路 source → transform → sink（fromElements → map → filter → sink） |
| 2 | `topology2-window-aggregation.stream.xml` | **XDSL 声明式** | keyBy 分区、事件时间滚动窗口聚合、keyed state（`ValueState` 逐用户计数富化）、声明式 checkpoint 配置 |
| 3 | `topology3-cep-pattern.stream.xml` | **XDSL 声明式** | CEP 模式匹配（30 秒内严格相邻两笔 >1000 触发告警）、`<patterns>` 声明 + 内联 xpl 谓词 |

每个拓扑都有独立测试（`Topology1/2/3*Test`）对输出做精确断言——生成工程 `mvn test` 全绿即三条链路端到端跑通。

## 使用

### 前置条件

- JDK 17+，Maven 3.9+（或复用本仓库 `./mvnw`）
- 本地 maven 仓库已安装 nop-stream 构件（在**本仓库**执行一次）：

  ```bash
  ./mvnw install -pl nop-stream -am -DskipTests -T 1C
  ```

### 生成并运行

```bash
# 1. 生成工程（参数可换自己的 groupId/artifactId/package）
nop-stream/quickstart/generate.sh ~/work/my-stream-job \
    --group-id com.example --artifact-id my-stream-job

# 2. 运行 3 个拓扑的测试
cd ~/work/my-stream-job && mvn test

# 3.（可选）直接跑拓扑 1 的 main
mvn compile exec:java -Dexec.mainClass=com.example.mystreamjob.Topology1MinimalPipeline
```

### 一键验证（仓库侧）

```bash
nop-stream/quickstart/verify.sh
# 端到端：检测/补齐本地构件 → 生成到 _tmp/quickstart-verify → mvn test → 3 拓扑全绿
# 任何步骤失败非零退出
```

## 生成的工程长什么样

```
my-stream-job/
├── pom.xml                                     # 三依赖：nop-stream-flow / nop-stream-runtime / junit
└── src/
    ├── main/java/<package>/
    │   ├── QuickstartSupport.java              # XDSL 加载执行惯用法（DslModelParser + StreamModelDslBuilder）
    │   ├── TradeEvent.java                     # 示例事件类型（XDSL xpl 谓词经 getter 访问）
    │   ├── Topology1MinimalPipeline.java       # 拓扑 1（含 main）
    │   ├── Topology2Beans.java                 # 拓扑 2 bean 集（source/水位/富化/聚合）
    │   └── Topology3Beans.java                 # 拓扑 3 bean 集（source/CEP select）
    ├── main/resources/_vfs/quickstart/
    │   ├── topology2-window-aggregation.stream.xml
    │   └── topology3-cep-pattern.stream.xml
    └── test/java/<package>/
        ├── Topology1MinimalPipelineTest.java
        ├── Topology2WindowAggregationTest.java
        └── Topology3CepPatternTest.java
```

## 下一步

- 把拓扑 1 的 source 换成真实连接器（见连接器目录：file / message / jdbc / debezium / batch 桥接及其交付语义标注）
- 在拓扑 2 上开启 `STRICT_EXACTLY_ONCE`（需 REPLAYABLE source + 2PC sink 组合，规划期校验）
- 用 Delta（`x:extends`）派生你自己的 `.stream.xml`，不改基座
- 运维（指标 / REST / 健康状态机 / 告警 / 状态重置）见 owner doc `docs-for-ai/03-modules/nop-stream.md`
