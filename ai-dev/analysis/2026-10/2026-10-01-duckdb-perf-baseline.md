# DuckDB 执行层性能基线（WI7）

> Status: baseline record（只立基线不设竞速指标）
> Date: 2026-10-01
> Plan: `ai-dev/plans/2289-duckdb-wi7-perf-baseline.md`
> Reproduce: `./mvnw test -pl nop-duckdb -Dtest=TestDuckDbPerfBaseline`（前置：上游模块已 install；若加 `-am` 须同时加 `-Dsurefire.failIfNoSpecifiedTests=false`，否则上游模块因测试过滤无匹配而失败）

## 数据集与语义

- 1M 行 × 2 列 CSV（实测 ~7MB）：`g` = i%1000（低基数分组列，1000 组 × 每组恰 1000 行）、`k` = i%977
- 聚合语义三方同构：`GROUP BY g` + count/sum（duckdb/h2 两腿另测 avg；tablesaw 腿计时不做 mean——与 SQL avg 存在末位 ulp 差异，见 plan 2289 裁定）；golden 由数据集数学直接复算（组内 sum 累加），每轮跑每轮断言（正确性优先于计时）
- 组间 sum 有碰撞（977 个 distinct 值 / 1000 组）：sum 不唯一标识组，仅作数值正确性断言

## 环境与配置档位

| 项 | 值 |
| --- | --- |
| OS / CPU | macOS（Apple Silicon M 系列） |
| JDK | 26（release=17 字节码） |
| DuckDB | duckdb_jdbc 1.5.6.0，threads=4、memory_limit=1GB（回读 953.6 MiB，容器 bean @InjectValue 注入，current_setting 校验） |
| tablesaw | 0.43.1，默认配置 |
| H2 | 2.4.240，nop-dao 默认池（application.yaml mem 库），逐 run 变换 SQL 文本防会话结果缓存（H2 2.4.240 无 SET QUERY_CACHE_SIZE SQL） |

## 基线数值（2026-10-01 本机实测，min of 4 runs，1 warmup + 3 measure 记录但 warmup 未计入 min）

| leg | run1 | run2 | run3 | run4 | min |
| --- | --- | --- | --- | --- | --- |
| duckdb（nop-duckdb 执行层） | 6ms | 2ms | 2ms | 1ms | **1ms** |
| tablesaw | 342ms | 174ms | 290ms | 167ms | **167ms** |
| h2-pushdown（nop-dao → H2） | 210ms | 190ms | 188ms | 185ms | **185ms** |

## 读数说明

- min 为参考值，非门槛、非竞速指标；测试对耗时零断言（防硬件差异假失败），只断言每轮聚合正确性；每 leg 4 个同构 run 全部计时，min 取 4 run 最小值（无独立 warmup run，run1 兼作预热）
- DuckDB 的 1-2ms 量级为进程内列存 + 向量化执行形态；H2 为行存 RDB 下推形态；tablesaw 为 JVM 内存列表面——三者定位不同（执行层/业务库/表计算），本基线仅记录量级差异供后续场景选型参照
- ingest 阶段（read_csv / CSVREAD / Table.read）未计入聚合计时；DuckDB ingest 一次完成后表驻留 .duckdb 文件
- 复测时环境与配置若变（线程数/内存预算/数据量档位），应在本文档追加新小节而非覆写历史读数

## 关联

- 数据面与执行层 API：`nop-duckdb`（docs-for-ai 收口归 WI9）
- 单写者与外存语义：plan `ai-dev/plans/2286-duckdb-wi4-single-writer-and-resume.md`（64MB 过紧 / 128MB 溢出档 / 20M 行外存链实测）
