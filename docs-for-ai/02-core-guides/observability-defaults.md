# Observability Defaults（Micrometer Prometheus v1 / OTel）

> Status: active
> Source: JDK25+SB4+Q4 升级路线 WI15（2026-10-12，plan 2449；过程记录见 `ai-dev/` 内部档案）

本 doc 记录平台在 JDK25 + Spring Boot 4.1 + Quarkus 4.0.Beta1 基线（roadmap ★ 里程碑）上**有意识采纳**的可观测默认，以及"不回钉"政策。任何改动这些默认的提议都必须先修订本 doc。

## Micrometer Prometheus：v1 registry（已就位）

平台全部 Prometheus 暴露面都在 Micrometer **v1**（`io.micrometer.prometheusmetrics`，基于 Prometheus client 1.x）上，版本由 BOM 管理（实测 1.17.1）：

| 暴露面 | 模块 | 说明 |
|---|---|---|
| `/q/metrics` | `nop-quarkus/nop-quarkus-web-starter` | `quarkus-micrometer-registry-prometheus`（无版本，Q4 BOM 管理） |
| ops server scrape | `nop-stream/nop-stream-runtime`（`StreamOpsHttpServer`） | 直接 import `io.micrometer.prometheusmetrics.PrometheusMeterRegistry`；wire 名规范（`.`→`_`、counter `_total`、TextFormat 0.0.4/OpenMetrics 协商）见 `03-modules/nop-stream.md` |
| `/actuator/prometheus` | `nop-demo/nop-spring-simple-demo` | `micrometer-registry-prometheus`（无版本，SB4 BOM 管理）+ `spring-boot-starter-actuator` |

- 平台库 `nop-kernel/nop-commons` 也声明 micrometer-registry-prometheus + micrometer-core（BOM 管理 v1），`GlobalMeterRegistry` 静态默认 SimpleMeterRegistry；Quarkus 集成路径（`nop-quarkus-core-starter` 的 `QuarkusIntegration`）在启动时注入容器 MeterRegistry——含 prometheus 扩展的应用运行期即为 Prometheus-backed（`GlobalMeterRegistry` 自身无 scrape 端点）。
- 旧 simpleclient 线（`micrometer-registry-prometheus-simpleclient` / `io.micrometer.prometheus.<legacy>` 包）**全仓零引用**——不得重新引入。
- 指标命名规则与 stream 面实测 wire 名见 `03-modules/nop-stream.md`。

## OpenTelemetry：Q4 默认 + 不回钉政策

- **现状**：全仓无任何 opentelemetry 依赖（零消费方）；application 配置全面无 OTLP exporter/sampler 覆盖（plan 2449 实测 grep 零命中）。
- **采纳的默认（Decisions 已裁决，no pin-back）**：Q4 的 OTLP exporter `http/protobuf` / port **4318** 默认与 **10%** trace sampling 默认。未来引入 `quarkus-opentelemetry` 时（WI16 GA 后按需求驱动引入，非 WI16 交付面），**不得**配置回 grpc/4317 或调整 sampling 以"还原旧行为"。
- 若某环境确需覆盖 endpoint，只允许覆盖 `quarkus.otel.exporter.otlp.*` 的 endpoint 值本身，协议与端口默认不动。

## ORM/DAO 观测现状与缺口

- **指标**：`IOrmMetrics`（`nop.orm.sessions.*` / `nop.orm.entities.*`）、`IDaoMetrics`（连接/查询/更新/批处理）、`ITransactionMetrics`（事务 open/success/failure）、`GlobalStatManager` 的 `JdbcSqlStat`（JVM 级 per-SQL 统计：耗时/并发/直方图/行数）。
- **日志**：SQL logger `io.nop.dao.sql`（`nop.jdbc.run:usedTime=...`）。
- **其他统计面（各自 owner，不并入 ORM 指标）**：`IWithStats<T>`（`nop-commons` 通用 stats 接口）、`CacheStats`、`ThreadPoolStats`、`PoolStats`、`TaskQueueStats`（`nop-core`）、task-flow 的 `resetGlobalStats`（限流器/信号量统计重置，`nop-task`）。
- **缺口（未实现、无默认）**：N+1 检测、慢查询捕获（`JdbcSqlStat.lastSlowParameters` 无生产 setter）、SQL 审计、OTel tracing（全仓无 `opentelemetry` 依赖）。
- **补全约束**：不得新建第二套统计——优先扩展 `GlobalStatManager` / `IOrmMetrics` / `IDaoMetrics`，并复用 `GlobalMeterRegistry`；OTel 引入须遵守上文 Q4 默认（http/protobuf 4318、10% sampling）。

## 变更纪律

1. 改动本 doc 记录的任何默认 = 改变运行时行为，须走 roadmap/plan 流程并在本 doc 留痕。
2. 新增暴露面（新 starter/模块引 metrics）时更新上表。
