# Streaming SQL 开源项目架构对比——streaming-sql-survey 综合

> Status: resolved
> Date: 2026-10-07
> Scope: 14 个 streaming SQL 开源项目的横向对比与流派归纳（功能建议见 [gap 文档](./2026-10-07-nop-stream-sql-gap-and-recommendations.md)）
> Conclusion: 14 项目收敛为五个流派（Calcite 系 / 自研前端系 / 流式数据库系 / 库形态系 / 低代码编排系）；可迁移的架构模式集中在五个谱系——changelog 表达、watermark/lateness 声明、窗口语法、emit 节奏、fail-fast 风格；nop-stream-sql 的"单一 parser + 三注册表 + 双目标 + 闭环 fail-fast"组合在嵌入式定位上是差异化优势，无需对齐任何一家的重栈路线。

## Context

本文件汇总 `streaming-sql-survey/` 下 14 篇逐项目分析的横向结论。调研动机、能力基线与分级建议见 [gap 文档](./2026-10-07-nop-stream-sql-gap-and-recommendations.md)。逐项目结论（Conclusion 行）：

| 项目 | 一句话结论 |
|---|---|
| [Flink SQL](./2026-10-07-flink-sql-analysis.md) | ChangelogMode 做成 planner 可推导 trait；窗口 TVF 化 |
| [Calcite](./2026-10-07-calcite-analysis.md) | 插槽式 parser 扩展面；MATCH_RECOGNIZE 压成纯数据节点四层解耦 |
| [ksqlDB](./2026-10-07-ksql-analysis.md) | push/pull 双查询 + EMIT CHANGES\|FINAL 一级语法；LookupConstraint 三档 |
| [Kafka Streams](./2026-10-07-kafka-streams-analysis.md) | EmitStrategy 两档降维 trigger；内部资源命名即稳定 API |
| [RisingWave](./2026-10-07-risingwave-analysis.md) | 迟到量写进水位表达式；StreamKind 规划期属性 |
| [Materialize](./2026-10-07-materialize-analysis.md) | MapFilterProject 规范化 IR；temporal filter 有界状态 |
| [Arroyo](./2026-10-07-arroyo-analysis.md) | DDL 级 WATERMARK 约束 + 默认 idle；内部 retract 标记列升级 changelog |
| [Feldera](./2026-10-07-feldera-analysis.md) | 列级 LATENESS + 单调性分析自动插 GC；输出档位 + 二次拒绝层 |
| [Hazelcast Jet SQL](./2026-10-07-hazelcast-jet-sql-analysis.md) | IMPOSE_ORDER lag 第三参数；语句级 fail-fast 规则拦截 |
| [SeaTunnel SQL](./2026-10-07-seatunnel-sql-analysis.md) | SQLEngine SPI 双引擎（解释器→编译型）；open 期 schema 静态推导 |
| [Esper](./2026-10-07-esper-analysis.md) | changelog 做成语言层一等公民（ISTREAM/RSTREAM）；OUTPUT EVERY 发射节奏 |
| [Debezium](./2026-10-07-debezium-analysis.md) | snapshot→streaming 单 offset 交接；op+snapshot+ts 统一事件信封 |
| [Paimon](./2026-10-07-paimon-analysis.md) | changelog-producer 四档表属性；水位/计算列序列化进 schema options |
| [Nussknacker](./2026-10-07-nussknacker-analysis.md) | SQL 作场景图节点（变量→虚表→查询→回流）；DDL 与查询分层 |

## 一、五个流派

| 流派 | 项目 | SQL 前端 | 流语义承载 | 对 nop 的参照意义 |
|---|---|---|---|---|
| **Calcite 系** | Flink、Hazelcast、Feldera（、Beam/Druid 同族） | Calcite parser+规则 | 流语义在 planner/规则层（ChangelogMode、IMPOSE_ORDER 视图包裹） | 重栈路线；nop 不走，但借其词汇与边界判定（SqlWindow） |
| **自研前端系** | ksqlDB（ANTLR）、Arroyo（sqlparser-rs+DataFusion 改写）、RisingWave（自研 frontend） | 各自parser | 流语义在专属 planner/执行计划 | 证明"自研窄前端"可行；nop 的 EQL 复用口径更省 |
| **流式数据库/物化视图系** | RisingWave、Materialize、Feldera、Paimon（存储侧） | SQL 即 DDL/DML | changelog 内建、增量视图维护 | chagelog/有界状态的语义素材库；均不可嵌入，只作设计参照 |
| **库形态/嵌入式系** | Kafka Streams（DSL 无 SQL）、Esper（EPL 纯库）、SeaTunnel（transform 内嵌 SQL） | 无 SQL 或内嵌 SQL 片段 | 库内 DSL/单行表达式求值 | 与 nop 嵌入式定位同族；EmitStrategy/UDF SPI 直接可抄 |
| **低代码编排系** | Nussknacker、（nop `<sql>` 同构物） | SQL 作为场景节点 | 场景图→引擎作业 | `变量→虚表→sqlQuery→回流`回路与 `<sql>` SPI 展开互为印证 |

## 二、大矩阵（14 项目 × 8 维度）

单元格为摘要；依据（源码路径）在各项目分析内。

| 项目 | 前端 | changelog/retract | watermark/late 声明 | 窗口 | Join | CEP | 嵌入性 | 容错 |
|---|---|---|---|---|---|---|---|---|
| Flink | Calcite+FMPP | ChangelogMode 推导 | DDL WATERMARK + computed col | TVF 四窗口+named+frame | equi/interval/temporal/lookup | MATCH_RECOGNIZE | planner 可嵌入但重 | checkpoint 全套 |
| Calcite | 自身 | 无流语义 | 无 | SqlWindow/frame 词汇 | rel/core/Join+Correlate | Match 纯数据节点 | 库（Java） | 不适用 |
| ksqlDB | ANTLR | 持续聚合=changelog；EMIT FINAL | TIMESTAMP 列+GRACE | TUMBLING/HOPPING/SESSION+named | WITHIN 非对称/FK join/LookupConstraint | 无 | 服务端形态 | Kafka 底座 |
| Kafka Streams | 无（DSL） | KTable Change（retract） | TimestampExtractor+grace | TimeWindows+grace+EmitStrategy/Suppress | equi+GlobalKTable lookup | 无 | 纯库 | offset+EOS |
| RisingWave | 自研 frontend | StreamKind 规划期 | WATERMARK FOR ... AS col - INTERVAL | tumble/hop/session/cumulate( TVF) | equi+ASOF 倾向 | 无 | 不可嵌入 | 物化+barrier |
| Materialize | 自研+differential | 增量视图差分 | mz_now() temporal filter | TemporalBucketing | 增量 join | 无 | 不可嵌入（BSL 1.1） | timely 数据流 |
| Arroyo | sqlparser-rs+DataFusion | 内部 `_updating_meta` 标记列→Debezium changelog | DDL WATERMARK 约束+默认 idle 5min | tumble/session/hop | equi+interval+watermark 补齐 | 无 | workspace 不可嵌入 | checkpoint(RocksDB/S3) |
| Feldera | Calcite+二次拒绝层 | insert/delete 默认+emit_final/append_only/soft_delete 档位 | 列级 LATENESS+waterline 单调性推导 | TUMBLE/HOP/SESSION+window_start/end | equi+LEFT ASOF(仅>=) | 无 | 不可嵌入 | DBSP 总序 |
| Hazelcast | Calcite+白名单+规则拦截 | 无（映射/窗口聚合 append） | IMPOSE_ORDER lag+DropLateItems | tumble/hop/session | equi（流-流需 IMPOSE_ORDER 双侧） | 无 | 库（IMDG 内嵌） | snapshot |
| SeaTunnel | JSqlParser/Calcite 双引擎 SPI | 无（单行 transform） | 不适用 | 无聚合/窗口（显式拒绝） | 无（显式拒绝） | 无 | 引擎无关插件 | 引擎承担 |
| Esper | EPL 自研（编译字节码） | ISTREAM/RSTREAM/IRSTREAM | 引擎时间/事件时间 | 时间/长度窗口+OUTPUT EVERY | 流-流/命名窗口/非等值表达式 | pattern 子语言+skip 策略 | 纯库 | 内部一致（单节点） |
| Debezium | 不适用 | op+snapshot 信封（changelog 源头） | ts_ms/ts_us/ts_ns | 不适用 | 不适用 | 不适用 | 连接器库 | offset+schema history |
| Paimon | 无（表格式） | changelog-producer 四档 | schema.watermark.* 表属性 | 不适用（消费侧窗口属引擎） | lookup join 缓存 | 不适用 | 表格式+SDK | 快照+ LSM |
| Nussknacker | Flink SQL 借用（三类 DDL 白名单） | 引擎侧（Flink） | SOURCE_WATERMARK()/record_time 虚表列 | 引擎侧 | 引擎侧 | 组件级 filter/switch | 平台形态 | Flink 承担 |

## 三、可迁移的五个架构谱系

1. **changelog 表达谱系**（窄→宽）：nop last-value-wins → Feldera `emit_final`/`append_only` 两档 → Arroyo 内部标记列 → ksqlDB EMIT CHANGES|FINAL → Esper ISTREAM/RSTREAM → Flink ChangelogMode 推导 → Paimon 四档声明。**迁移建议**：从左往右按需取档，见 gap 文档 B1。
2. **watermark/lateness 声明谱系**：查询级属性（Hazelcast lag）→ 窗口子句旁（ksqlDB GRACE）→ DDL 约束（Arroyo/RisingWave/Flink）→ 列注解（Feldera LATENESS）→ 表属性（Paimon）。**迁移建议**：nop 已有运行时管线（D9），缺的只是声明面上浮，见 gap 文档 A7。
3. **窗口语法谱系**：FROM 内伪表函数（nop TUMBLE、Calcite/Flink TVF 同源）→ WINDOW 子句（ksqlDB）→ named window（ksqlDB/EQL 侧 FU-2）。window_start/window_end 输出列命名三家一致（Feldera/Flink/Calcite），nop 可直接对齐。
4. **emit 节奏谱系**：固定 fire（nop 现状）→ 两档 EmitStrategy（Kafka Streams）/EMIT CHANGES|FINAL（ksqlDB）→ OUTPUT EVERY/WHEN（Esper）→ 多档 Trigger（Flink，nop 已裁定不追）。**结论**：D11 若解，锁死两档（gap 文档 B2）。
5. **fail-fast 风格谱系**：错误码钉码（nop，§4d 已成体系）→ 规则表 validator（ksqlDB PullQueryValidator.RULES）→ 语句级物理规则拦截（Hazelcast）→ 带文档锚点的二次拒绝层（Feldera RejectUnsupportedPlans）。**迁移建议**：nop 保持错误码契约，补"规则表+文档锚点"形态（gap 文档 A10）。

## 四、对 nop-stream-sql 的定位评语

- **不必对齐的**：Flink 式多层计划（SqlNode→RelNode→ExecNodeGraph）与代价优化器（Doris-Nereids 裁定：优化器语义层不可跨引擎移植）；流式数据库的部署形态（均不可嵌入）；Calcite 全家桶（仅当第二引擎需要时经 SPI 引入）。
- **应当保持的差异化**：单一 EQL parser（与 ORM 同源，source-of-truth 一致）；产物是可读可 Delta 的流模型 XML（人审友好，Calcite 的 rel2sql 反证了"模型↔SQL 往返"价值）；三注册表（schemas/aggregators/joins）与 Flink OperatorTable/FunctionCatalog/hint 三分面同构但更轻；9 类型闭集 fail-fast 契约。
- **合理且业机理据充分的裁定**：D10/D11 fail-fast 与 Flink Table 不暴露 trigger 一致；UNION bag 语义与 Calcite multiset 一致；ORDER BY fail-fast 比 Feldera 的"忽略+告警"更不易出静默错误（[feldera](./2026-10-07-feldera-analysis.md) §对照）。

## 引用出处

- 逐项目分析：本目录 14 篇 `2026-10-07-*-analysis.md`（含各自源码引用与验证记录）。
- 能力基线与建议：[gap 文档](./2026-10-07-nop-stream-sql-gap-and-recommendations.md)。
- 既有分析（未重复其内容）：`../nop-stream/09e-flink2.3-compare-api-connectors.md`、`../nop-stream-flink-comparison-deep-dive.md`、`../2026-10/2026-10-06a-nop-stream-vs-flink-tdengine-hazelcast-seatunnel-comparison.md`。
