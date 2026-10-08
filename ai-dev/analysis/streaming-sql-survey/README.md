# Streaming SQL 开源项目调研（nop-stream-sql 功能参考）

> Status: resolved
> Date: 2026-10-07
> Scope: nop-stream-sql 功能完善参考——对照 14 个 streaming SQL 开源项目
> Goal: 在 nop-stream-sql 窄范围 SQL 接口已收口（roadmap 31/31 done）的前提下，调研开源实现，产出可实现/完善功能的分级建议
> Conclusion: 产出两份综合——[架构对比](./2026-10-07-streaming-sql-architectures-comparison.md)（五流派 + 五个可迁移谱系）与 [gap 分级建议](./2026-10-07-nop-stream-sql-gap-and-recommendations.md)（Tier A 不触门 15 项 / Tier B 触门 7 项 / Tier C 已裁排除 6 项）；P0 首选 = watermark/lateness 声明上浮 `<sql>` 面。

## 调研范围

### 本地源码（下载落点 `~/sources/streaming-sql/`，脚本 `~/sources/download-streaming-sql.sh`，10/10 OK 浅克隆）

| 项目 | 类型 | 技术栈 | 深度分析 |
|------|------|--------|----------|
| ksqlDB (confluentinc/ksql) | SQL-on-Kafka | Java, ANTLR, Kafka Streams 底座 | [分析](./2026-10-07-ksql-analysis.md) |
| RisingWave | 流式数据库 | Rust, 自研 frontend | [分析](./2026-10-07-risingwave-analysis.md) |
| Materialize | 增量物化视图数据库 | Rust, timely/differential（BSL 1.1） | [分析](./2026-10-07-materialize-analysis.md) |
| Arroyo | 流式 SQL 引擎 | Rust, sqlparser-rs + DataFusion | [分析](./2026-10-07-arroyo-analysis.md) |
| Feldera | 增量视图维护（DBSP） | Rust, Calcite + DBSP | [分析](./2026-10-07-feldera-analysis.md) |
| Hazelcast（Jet SQL） | 嵌入式流 SQL | Java, Calcite + IMDG | [分析](./2026-10-07-hazelcast-jet-sql-analysis.md) |
| Esper (espertechinc/esper) | CEP/EPL 连续查询 | Java 纯库 | [分析](./2026-10-07-esper-analysis.md) |
| Nussknacker (TouK/nussknacker) | 低代码流编排 + Flink SQL | Scala | [分析](./2026-10-07-nussknacker-analysis.md) |
| Apache Paimon | 流式湖仓（changelog） | Java | [分析](./2026-10-07-paimon-analysis.md) |
| Debezium | CDC source | Java | [分析](./2026-10-07-debezium-analysis.md) |

### 复用既有本地仓库（`~/sources/` 已有，不重复下载）

| 项目 | 分析焦点 | 深度分析 |
|------|--------|----------|
| Apache Flink | Table/SQL 层（flink-table/*，不含 runtime） | [分析](./2026-10-07-flink-sql-analysis.md) |
| Apache Calcite | streaming 扩展 / MATCH_RECOGNIZE / 可复用前端 | [分析](./2026-10-07-calcite-analysis.md) |
| Apache Kafka | Kafka Streams DSL / EmitStrategy / 命名治理 | [分析](./2026-10-07-kafka-streams-analysis.md) |
| Apache SeaTunnel | SQL transform（Zeta/Calcite 双引擎 SPI） | [分析](./2026-10-07-seatunnel-sql-analysis.md) |

## 综合产出

1. [架构对比与流派归纳](./2026-10-07-streaming-sql-architectures-comparison.md) —— 五流派；changelog/水位声明/窗口语法/emit 节奏/fail-fast 五个可迁移谱系；nop-stream-sql 定位评语。
2. [gap 分析与分级功能建议](./2026-10-07-nop-stream-sql-gap-and-recommendations.md) —— 按 roadmap 门控判据分 Tier A/B/C；每条带参考出处与去重结论（对齐 sql roadmap FU、multi-engine WI、eql-cross-db WI）。

## 方法与口径

- 能力基线 = `ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4a/§4b（WI16 定稿）+ `docs-for-ai/03-modules/nop-stream-sql.md`；分级标准 = sql roadmap Purpose 门控判据 (a)-(e)。
- 每篇分析带统一十维度模板 + 「对照 nop-stream-sql」小节；全部引用为本地源码路径并经存在性校验（综合校验 112 条路径零悬空）。
- 与既有调研的分工：`../2026-10/2026-10-06a-nop-stream-vs-flink-tdengine-hazelcast-seatunnel-comparison.md`（引擎/产品面五方对比，multi-engine roadmap 依据）与本 survey（SQL 语法/语义/声明面）互补不重叠；`../2026-09/2026-09-01-*productization-analysis.md`（产品化视角）同。
