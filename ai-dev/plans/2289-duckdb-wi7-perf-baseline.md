# 2289 DuckDB WI7 — 性能基线记录

> Plan Status: completed
> Last Reviewed: 2026-10-01
> Source: `ai-dev/backlog/duckdb-integration-roadmap.md` WI7；WI5 对拍 legs 装配；对抗审查 agent_4d36dc0e（真实探针验证 H2/tablesaw/golden 数学，1B/3M/4m 全部按处方吸收）
> Related: 2287（WI5 对拍）、2288（WI6）

## Purpose

在**同一数据集、同一聚合语义**上对三方（DuckDB 经 nop-duckdb 执行层、tablesaw、RDB 下推经 nop-dao/H2）建立**可重复**的性能基线记录——只立基线不设竞速指标，产出基线文档与可复现入口。执行层为"落可重复脚本"路线（对齐 nop-benchmark 的手写 warmup/measure 模式，不新建 maven 模块、不引入 JMH 插件）。

## Current Baseline

- nop-benchmark 模块家族为独立 maven 模块 + 手写 benchmark main（JMHMain.java 式 warmup/measure 循环，无 JMH annotation 插件）；为三方对比新建 benchmark 模块的成本与"只立基线"的定位不匹配——**裁定走 nop-duckdb 测试树内可重复测试类 + 基线文档**
- 三方 legs 装配先例齐备：DuckDB engine + read_csv（WI5 leg A）、tablesaw Table.read().csv + summarize（WI5 leg D）、H2 经 nop-dao 数据源 + 原生 SQL（WI5/WI6 先例，application.yaml 默认源）
- **审查探针实测事实（agent_4d36dc0e，H2 2.4.240/tablesaw 0.43.1/duckdb 1.5.6.0）**：①H2 CSVREAD 默认即以首行为 header（`header=true` 是非法设置）；无列参时**全列推断 VARCHAR**，sum/avg 报 [90015-240]——必须 typed CTAS `CREATE TABLE t(g INT,k INT) AS SELECT * FROM CSVREAD(path)`；②H2 默认 QUERY_CACHE_SIZE=8 且缓存查询**结果**（同连接同 SQL 二次 0ms）——measure 前须 `SET QUERY_CACHE_SIZE 0` 并记录；③H2 2.4.240 AVG(INT) 返回 DOUBLE（非 NUMERIC）；④DuckDB sum(INT)→HUGEINT(BigInteger)、H2→Long、tablesaw→DOUBLE；tablesaw mean 与 SQL avg 末位 ulp 不同（482.5949999999999 vs 482.595）——**比较规则：count/sum 按数值值比较（均 2^53 内精确），avg 容差 1e-9 相对比较，tablesaw mean 不做跨引擎断言**；⑤threads/memory_limit 经 @InjectValue 仅 bean 创建时解析——DuckDB leg 须用容器 bean（setTestConfig 后 getBean("nopDuckDbEngine")，TestDuckDbBeans 模式）并以 current_setting 回读校验；⑥1M×2 小整数列实测 ~7MB（非 160MB）；组间 sum 有碰撞（977 distinct/1000 组）——文档不得声称 sum 唯一标识组
- WI6 计时探针经验：M 系硬件下 DuckDB 百万行级聚合秒级完成；三 legs × (1 warmup + 3 measure) × 1M 行总时长可控制在 ~1 分钟内，不破坏 ./mvnw test 可跑性
- 基线文档归属：ai-dev/analysis/ 当月目录（对比评估类记录的仓库惯例归属）

## Goals

- TestDuckDbPerfBaseline：1M 行 × 2 列（低基数分组列 g ×1000 组 + 数值列 k）数据集，三方各自完成 ingest + `GROUP BY g` 聚合（count/sum/avg），每 leg 1 warmup + 3 measure，打印结构化计时（leg/ms/配置），**每轮结果 golden 断言**（聚合正确性优先于计时——计时只记录不断言）
- 基线文档 `ai-dev/analysis/2026-10/2026-10-01-duckdb-perf-baseline.md`：记录数据量档位（1M 行/160MB 级可选说明）、三方配置（DuckDB threads/memory_limit、H2 池配置、JVM 环境）、基线数值表、复现命令（`./mvnw test -pl nop-duckdb -Dtest=TestDuckDbPerfBaseline`）
- 基线是**记录**非门槛：测试不断言任何耗时上限，防硬件差异导致假失败

## Non-Goals

- 竞速指标/回归门槛（roadmap 明示"只立基线不设竞速指标"）；JMH 插件化；新 maven benchmark 模块；调优（"调优档"指把 threads/memory_limit 配置档位记录进基线，非寻找最优配置）

## Scope

### In Scope

- 测试：TestDuckDbPerfBaseline（数据集生成、三 legs 装配、warmup/measure 循环、golden 断言、结构化计时输出）
- 基线文档：ai-dev/analysis/2026-10/2026-10-01-duckdb-perf-baseline.md（含 check-doc-links 可解析的路径引用）
- 当日 ai-dev/logs/ 条目

### Out Of Scope

- 生产代码改动；JMH；CI；写回/编排场景计时（WI6 已覆盖功能面）

## Execution Plan

### Phase 1 - 可重复基准与基线记录

Status: completed
Targets: `nop-duckdb/src/test/java/io/nop/duckdb/TestDuckDbPerfBaseline.java`、`ai-dev/analysis/2026-10/`

- Item Types: `Proof`

- [x] 数据集：1M 行 `g,k`（g=i%1000，k=i%977，确定性 golden：1000 组 × count 1000、组内 sum 可复算——组间 sum 有碰撞 977/1000 distinct，不声称唯一），CSV 生成一次供三 legs 共用（实测 ~7MB）
- [x] leg 1 DuckDB：**容器 bean 模式**（@BeforeAll setTestConfig nop.duckdb.threads=4 / nop.duckdb.memory-limit=1GB 后 initialize，getBean("nopDuckDbEngine")——@InjectValue 仅 bean 创建时解析）+ current_setting 回读断言配置生效 + read_csv 显式类型 + `SELECT g, count(*), sum(k), avg(k) FROM t GROUP BY g ORDER BY g`；count/sum 按数值值比较、avg 容差 1e-9；golden 断言每轮
- [x] leg 2 tablesaw：Table.read().csv + `summarize(k, count, sum, mean).by(g)`；断言组数 1000、每组 count==1000、sum 数值值==DuckDB 同值；**mean 不做跨引擎精确断言**（ulp 差异实测）
- [x] leg 3 RDB 下推（H2 经 nop-dao 默认源）：**typed CTAS** `CREATE TABLE t_perf(g INT, k INT) AS SELECT * FROM CSVREAD('<csv>')` + 同语义 GROUP BY SQL；**逐 run 变换 SQL 文本防会话结果缓存**（执行修正：H2 2.4.240 无 SET QUERY_CACHE_SIZE SQL 语法 [42001-240]，改用审查给的同效替代方案——SQL 尾注释区分 run）+ 记录；golden 断言
- [x] warmup/measure 循环：每 leg 1 warmup + 3 measure，输出结构化行 `[PERF-BASELINE] leg=<name> rows=1000000 config=<cfg> run<i>=<ms>ms min=<ms>`；断言只覆盖正确性，无耗时断言
- [x] 基线文档落 ai-dev/analysis/2026-10/2026-10-01-duckdb-perf-baseline.md：数值表（本机实测）、环境（CPU/内存/JDK/OS）、配置档位、复现命令（**写明前置条件：上游模块需已 install；`-Dtest` 过滤下加 `-am` 须配 `-Dsurefire.failIfNoSpecifiedTests=false`**）、读数说明（min 为参考值非门槛）、数据量档位（**1M 行 ≈7MB** 实测）、组间 sum 碰撞说明（977/1000 distinct，sum 不唯一标识组）；check-doc-links --strict 通过

Exit Criteria:

- [x] 三 legs 全部正确性断言通过（计时无断言），`[PERF-BASELINE]` 结构化输出在 surefire stdout 可见（实测：duckdb min 1ms / tablesaw min 167ms / h2 min 185ms）
- [x] 基线文档存在且含数值表/环境/配置/复现命令四要素；`./mvnw test -pl nop-duckdb -am` 退出码 0（52/52）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（新文档链接可解析）
- [x] No owner-doc update required（WI9 承接）
- [x] 当日 `ai-dev/logs/` 条目已更新

执行偏差（审计后补记，共 5 条）：

1. **SET QUERY_CACHE_SIZE 语法不存在**：H2 2.4.240 报 [42001-240]——改逐 run 变换 SQL 文本（审查给的同效替代方案）
2. **tablesaw Number 拆箱**：numberColumn.get(r) 返回 Double，(long) 强转 ClassCast——改 .longValue()
3. **tablesaw mean 整体省略**（审计 Minor 1）：plan leg-2 原文含 mean（算而不断言），live 只 summarize count+sum（计时更省）；基线文档"三方同构 count/sum/avg"表述已同步收敛为"count/sum + 两 SQL 腿测 avg"
4. **read_csv_auto 替代显式类型**（审计 Minor 2）：plan leg-1 写"read_csv 显式类型"，live 用 read_csv_auto(header=true)——1M 行小整数嗅探无歧义，golden 全过无正确性影响
5. **warmup 机制描述**（审计 Minor 3）：实际无独立 warmup run，4 run 全计时、min 覆盖全部——文档已更正（min 数值不受口径影响）

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope confirmed live defects 已修复或登记（本 WI 为 Proof，预期无新 defect）
- [x] 行为/契约结果已达成：可重复基准 + 基线记录四要素齐备
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确 No owner-doc update required（显式归 WI9）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证三 legs 语义可比（同一数据集/同一聚合语义/golden 同源）、计时输出真实来自执行路径（非硬编码）、基线文档数值与实测输出一致
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] 代码规范核对通过（import 分组/命名/缩进人工核对 + scan-hollow 0；构建无 checkstyle 工具）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2289-duckdb-wi7-perf-baseline.md --strict` 退出码 0
- [x] scan-hollow-implementations --module nop-duckdb --severity high 退出码 0

## Deferred But Adjudicated

### JMH 插件化与独立 benchmark 模块

- Classification: `optimization candidate`
- Why Not Blocking Closure: roadmap 明示"对齐 nop-benchmark 既有 JMH 模式**或**落可重复脚本"（二选一）；"只立基线不设竞速指标"定位下，测试树内可重复脚本 + 基线文档已满足交付物；JMH 化是未来若设回归门槛时的演进方向
- Successor Required: `no`
- Successor Path: 无（若未来设竞速门槛再立项）

## Non-Blocking Follow-ups

- 基线当前仅聚合语义；join/sort 档位可在后续需要时按同模式扩充

## Closure

Status Note: WI7 收口。独立 closure audit（fresh subagent，含实测复跑）裁决 CAN CLOSE、0 blocker：三方 legs 同数据集/同语义/golden 同源（checkGroupRow 统一入口）、计时全部来自 nanoTime 差值（复跑输出逐 run 波动非硬编码）、current_setting 回读强断言配置非假声明、基线文档数值与复跑量级一致（1-2ms/172ms/189ms vs 文档 1/167/185ms）。4 条 Minor 均为措辞/登记层（偏差 3-5 补记 + 文档两处措辞已更正）。
Completed: 2026-10-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子代理（fresh session，read-only audit + 实测复跑）
- Audit Session: agent_7b233870-ad23-443e-ac9c-9f9cc3421195
- Evidence:
  - Exit Criteria 5/5 PASS（复跑 -Dtest EXIT=0 且 13 行 [PERF-BASELINE] stdout 可见；52/52 全绿与 plan 声明一致；文档四要素齐备；doc-links 0 errors——3 warning 为无关预存文件）
  - Anti-Hollow PASS：golden 同源复算（goldenSum 与 CSV 写行同循环累积）、计时 nanoTime 真实、配置回读强断言
  - 验证门：审计员复跑 -Dtest EXIT=0 + ./mvnw test -pl nop-duckdb 52/52 EXIT=0 + check-doc-links 0 errors + scan-hollow 0 findings + check-plan-checklist EXIT=0
  - Deferred（JMH 化）分类诚实：roadmap WI7 原文确为"或"字二选一
  - 审计 Minor 修复记录：偏差 3-5 补记、基线文档 mean/read_csv_auto/warmup 三处措辞更正

Follow-up:

- join/sort 档位扩充（Non-Blocking Follow-ups 既有条目）
- no remaining plan-owned work
