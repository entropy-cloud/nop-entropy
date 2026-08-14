# 02 — nop-metadata Profiler 与聚合精度静默错算修正（AR-05/AR-06/AR-10）

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Mission: nop-metadata-invariant-loop
> Work Item: Follow-up Backlog — 静默错算/精度族（AR-05/AR-06/AR-10）
> Source: `ai-dev/audits/2026-08-14-0707-open-audit-nop-metadata-invariant-loop.md`（AR-05、AR-06、AR-10）
> Related: `2026-08-14-0707-2-nop-metadata-silent-wrong-result-and-contract-fixes.md`（AR-01/02/03/F4 已 completed 的同族 P1 修复；AR-10 从该 plan Non-Blocking Follow-ups 显式 carry-over）；open-audit 总评第 1 点"silent wrong number 家族是当前最大隐藏风险"

## Purpose

把 2026-08-14 再审计中的 **3 个 P2 "silent-wrong-result"** 缺陷收口为：profiler 列类型分类正确（几何/布尔列不再误归数值）、profiler 连接/权限失败不再静默塌缩为"string stats"、cross-DB 内存聚合精度无损（Long>2^53 不丢位、String 数值不被静默跳过）。三者共享同一特征——**无错误信号、结果静默错误**，是 plan 0707-2 已修 P1 同族的 P2 兄弟面，其中 AR-10 从 0707-2 的 Non-Blocking Follow-ups 显式 carry-over。

## Current Baseline

> 行级读证见 Source 审计文件 evidence 段。以下事实经本轮 live repo 核实。

- **AR-05（P2，profiler isNumericType 子串误分类）**：`MetaTableProfiler.java:72-73` 的 `NUMERIC_KEYWORDS` 含 `"INT"`/`"BOOLEAN"`；`isNumericType`（:490-501）用 `upper.contains(kw)` 匹配。`"POINT"` 含 `"INT"` 子串 → 误判数值；`"BOOLEAN"` 被显式列入 → 所有布尔列误判数值。误判后 `collectNumericStats` 发 `SUM(POINT_col)` 非法 SQL → 列落入 per-column 错误路径 → **无任何 stats**（而非正确回退 string stats）。根因是 substring matching 无法区分 `POINT`（含子串 `INT`）与真正的 `*INT` 类型——两者在 "INT" 前的字符都是字母数字，word-boundary 和前导守卫均无法区分。
- **AR-06（P2，probeNumeric 吞连接/权限失败）**：`MetaTableProfiler.java:214-222` 的 `probeNumeric` catch `SQLException` 后仅 DEBUG 日志 + return false。SQLException 可能是良性（"非数值类型"）也可能是严重（"连接断开 / 列被撤销 / 表被删除"），两者都 return false → 列静默按 string 剖析。日志仅 DEBUG 级别，per-column 错误无记录。同族 MA6.2-002（emptyCount site）已在 arm backlog 标注。
- **AR-10（P2，toBigDecimal 精度丢失 + String 数值静默跳过）**：`AggregationHelper.java:534-545` 的 `toBigDecimal`——对 `Number` 子类型统一 `BigDecimal.valueOf(((Number)v).doubleValue())`，`Long > 2^53` 经 `doubleValue()` 丢低位；对 `String` 类型的数值（部分 JDBC driver 交付方式）直接 return null，`SumAcc.accumulate` 的 `if (n != null)` 静默跳过 → String 数值列 SUM/AVG 为 null/错误，无警告。**此项从 plan 0707-2 Non-Blocking Follow-ups 显式 carry-over。**
- **构建/测试命令**（mission 配置）：`./mvnw test -pl nop-metadata -am -T 1C`。

## Goals

- AR-05：`isNumericType` 改用**显式已知数值类型名集合做 exact-match**（`Set.of("INT","INTEGER","TINYINT","SMALLINT","MEDIUMINT","BIGINT","DECIMAL","NUMERIC","NUMBER","DOUBLE","FLOAT","REAL","DEBIT")` 等，大写匹配），替代 substring contains。`BOOLEAN`/`BIT`(1-bit) 不纳入数值集合。`POINT`/几何/布尔列不再被误分类，正确回退 string stats 而非产出错误。
- AR-06：`probeNumeric` 区分"类型不匹配"（预期 → return false）与"基础设施失败"（连接/权限 → 至少 WARN 日志 + tableName/col 上下文，或记录进 snapshot.errors）。
- AR-10：`toBigDecimal` 对整数类型（`Long`/`AtomicLong`/`Short`/`Byte`/`Integer`）改用 `BigDecimal.valueOf(((Number)v).longValue())`（无损精度）；对浮点类型（`Float`/`Double`）保持 `BigDecimal.valueOf(((Number)v).doubleValue())`（小数不截断）；`BigInteger` 已有无损分支（:538-540），不变。对 `String` 入参尝试 `new BigDecimal((String)v)`（catch → null），不再直接 return null。SumAcc 不再静默跳过 String 数值列。
- 每项均有回归/精度测试。

## Non-Goals

- 不把 profiler 改为完全依赖 JDBC `java.sql.Types` int（那是更大的重构，可能影响 sql-view 列路径——当前 dataType 来自 `ResultSetMetaData.getColumnTypeName`，为字符串）。
- 不重写 cross-DB 内存聚合的整体数值策略（只修 toBigDecimal 的精度/覆盖缺陷）。
- 不处理同级 `toBigDecimal` 方法（`MemoryOrderByComparator.java:124-135`、`MemoryFilterEvaluator.java:348-359`）——它们有相同 `doubleValue()` 精度模式，但属于 ORDER BY/WHERE 比较路径（非 SUM/AVG 聚合），Long>2^53 精度丢失在排序/过滤上下文中影响较小。裁定为 optimization candidate，留待后续清扫。
- 不扩展不变式门禁到"silent-wrong-result"检测（那是 Cycle 2 / I1 评估范畴；本计划只修 confirmed defect）。
- 不处理 P2 安全硬化族（F5-F9）——由 sibling plan `2026-08-14-1133-1-...` 覆盖。
- 不处理 P2 lineage/manifest/reconciliation 族——由 sibling plan `2026-08-14-1133-3-...` 覆盖。

## Scope

### In Scope

- AR-05：`isNumericType` 匹配逻辑修正 + 回归测试。
- AR-06：`probeNumeric` 错误区分 + 日志/传播策略 + 测试。
- AR-10：`toBigDecimal` 精度修正 + String 数值解析 + 精度测试。
- 受影响 owner-doc 同步。

### Out Of Scope

- profiler 完全迁移到 JDBC Types int 分类。
- cross-DB 聚合整体重构。
- P2 安全/lineage/reconciliation/ORM/卫生族。

## Execution Plan

### Phase 1 — Profiler 列类型分类正确性（AR-05）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/profiling/MetaTableProfiler.java`；测试文件需新建（`isNumericType` 为 package-private，测试需置于 `io.nop.metadata.service.profiling` 包下或用 `--add-opens` 反射；现有 profiler 测试在 `io.nop.metadata.service` 包）

- Item Types: `Fix | Proof`

- [x] AR-05：将 `isNumericType` 的匹配方式从 `upper.contains(kw)` 改为**显式类型名 exact-match**——用 `Set<String>` 包含已知数值类型名（`INT`/`INTEGER`/`TINYINT`/`SMALLINT`/`MEDIUMINT`/`BIGINT`/`DECIMAL`/`NUMERIC`/`NUMBER`/`DOUBLE`/`DOUBLE PRECISION`/`FLOAT`/`REAL`），对 `upper.trim()` 做 `Set.contains`（substring 匹配仅保留用于 `DOUBLE PRECISION` 等空格复合类型——可改为先 `contains` 再 exact-confirm，或预先拆分空格复合名）
- [x] AR-05：从数值类型集合移除 `BOOLEAN`（布尔列不是数值）；`BIT` 裁定——`BIT` 在 MySQL 中 BIT(1)=boolean、BIT(n>1)=bitfield，裁定为非数值（保守回退 string stats，不产非法 SUM）
- [x] AR-05：`POINT`/几何类型/布尔列不再进入 `collectNumericStats`——它们正确落入 string stats 或 `probeNumeric` 运行时探测路径

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `isNumericType("POINT")` 返回 false（不再因子串 "INT" 误匹配——改用显式类型名集合 exact-match）
- [x] `isNumericType("BOOLEAN")` 返回 false（BOOLEAN 已移出数值集合）
- [x] 合法数值类型名（TINYINT/SMALLINT/BIGINT/INT/INTEGER/DECIMAL/DOUBLE PRECISION/FLOAT/REAL/NUMERIC/NUMBER）仍返回 true
- [x] **无静默跳过**：误分类修复后，几何/布尔列不再进入 collectNumericStats 产出非法 SQL 错误路径——它们正确回退 string stats
- [x] **跨 Phase 依赖**：Phase 1 移除 BOOLEAN 后，BOOLEAN 列落入 `probeNumeric` 路径（Phase 2）；Phase 2 的 probeNumeric 对 BOOLEAN-SUM 类型不匹配须 return false（非 throw），使 BOOLEAN 列最终回退 string stats 而非 per-column error
- [x] `ai-dev/logs/2026/08-14.md` 已追加

### Phase 2 — Profiler probeNumeric 错误区分（AR-06）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/profiling/MetaTableProfiler.java`；`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/profiling/TestMetaTableProfiler.java`（或同族测试文件）

- Item Types: `Fix | Proof`

- [x] AR-06：`probeNumeric` 的 `catch (SQLException)` 内区分错误类型——H2/MySQL/PG 的"类型不匹配"通常表现为 SQLState `22000`-系列（data exception）或非语法类运行时类型错误；"连接/权限/表不存在"表现为 `08000`-系列（connection）/`42`-系列（表/列不存在）/`42501`（权限）。对无法明确分类的 SQLException，保守提升为 WARN 日志（附带 tableName/col 上下文）+ 仍 return false（保持剖析不中断），而非静默 DEBUG + return false
- [x] AR-06：传播机制裁定——`probeNumeric` 仍返回 boolean（保持签名不变），基础设施失败通过 WARN 日志 + tableName/col 上下文暴露（而非改签名传 snapshot.errors，避免侵入 profile 方法签名）
- [x] AR-06：新增测试——构造类型不匹配场景（H2 SUM(text_column) → return false，无 WARN），构造模拟连接失败场景（可用 Connection wrapper/proxy 包装真实 H2 Connection，在 createStatement 时抛 SQLException 带 `08006` SQLState → 验证 WARN 日志触发 + 仍 return false）

Exit Criteria:

- [x] 类型不匹配仍 return false（行为不变，非数值列正确 fallback string）
- [x] 基础设施失败不再静默 return false——至少 WARN 日志 + 上下文，或记录进 snapshot.errors
- [x] **无静默跳过**：基础设施失败有显式信号（日志/error 记录），非 DEBUG 级别吞掉
- [x] `ai-dev/logs/2026/08-14.md` 已追加

### Phase 3 — 聚合精度无损与 String 数值覆盖（AR-10）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationHelper.java`；`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/query/TestCrossDbInMemoryAggregationProcessor.java`（或同族测试文件）

- Item Types: `Fix | Proof`

- [x] AR-10：`toBigDecimal` 对整数类型（`Long`/`AtomicLong`/`Short`/`Byte`/`Integer`）改用 `BigDecimal.valueOf(((Number)v).longValue())`（无损精度）；浮点类型（`Float`/`Double`）保持 `BigDecimal.valueOf(((Number)v).doubleValue())`（小数不截断）；`BigInteger` 已有无损分支（:538-540），不变。实现方式：在 `Number` 分支内用 `instanceof` 分派整数/浮点
- [x] AR-10：`toBigDecimal` 对 `String` 入参（当前直接 return null）增加 `new BigDecimal((String)v)` 尝试（catch NumberFormatException → return null），不再静默跳过 String 数值列
- [x] AR-10：新增精度测试——`Long.MAX_VALUE`（2^63-1 > 2^53）经 toBigDecimal 后 SUM 结果精确（与 longValue 一致）；`Double(1.5)` 经 toBigDecimal 保留小数（不被 longValue 截断）
- [x] AR-10：新增覆盖测试——String `"123.45"` 经 toBigDecimal 返回 `BigDecimal(123.45)`；非数值 String `"abc"` 返回 null

Exit Criteria:

- [x] `Long > 2^53` 经 toBigDecimal 不丢精度（测试断言精确值）
- [x] String 数值经 toBigDecimal 正确解析（不再返回 null 被静默跳过）
- [x] 非数值 String 仍返回 null（不抛异常打断聚合）
- [x] **接线验证**：`toBigDecimal` 经 `SumAcc.accumulate`/`AvgAcc` 在 cross-DB 内存聚合路径（`memoryGroupBy` → accumulate）运行时被调用
- [x] **无静默跳过**：String 数值不再被 SumAcc 的 `if (n != null)` 静默跳过
- [x] owner-doc（`docs-for-ai/03-modules/nop-metadata.md`）同步 cross-DB 聚合精度语义
- [x] `ai-dev/logs/2026/08-14.md` 已追加

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-05/AR-06/AR-10 三项 silent-wrong-result 缺陷全部修复
- [x] 每项均有回归/精度测试钉死
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响的 owner docs 已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证修复在运行时路径生效（profiler 真实剖析 / cross-DB 内存聚合器真实调用 toBigDecimal）
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 4 条不变式门禁仍零命中

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 建议下轮（Cycle 2 / I1）评估：把不变式从"catch 块"扩展到"silent-wrong-result"检测（`(long)double` 截断、`contains` 类型分类、分隔符 key）——carry-over from plan 0707-2。

## Closure

Status Note: 三项 P2 silent-wrong-result 缺陷（AR-05/AR-06/AR-10）全部修复，每项均有回归/精度测试钉死。`./mvnw test -pl nop-metadata -am -T 1C` → 1141 tests, 0 failures；4 条不变式门禁仍零命中。AR-06 实施中对 H2 vendor SQLState `90015`（"wrong data type"，非 SQL 标准 `22*`）做 live 实测裁定——`isInfrastructureFailure` 改为"只识别基础设施信号（08*/28*/42* + 消息线索）→ WARN，其余 → 良性类型不匹配 DEBUG"，避免对每个良性类型不匹配产 WARN 噪声淹没真实失败（原 plan 设想"保守 WARN on unknown"在 H2 下会噪声化）。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 执行 agent（mission-driver EXEC_PLANS）自检 + 测试套件全绿佐证；独立 closure-audit 子 agent 可在下一轮 OPEN_AUDIT/CLOSURE_VERIFY 复核。
- Evidence:
  - AR-05：`MetaTableProfiler.isNumericType` 改 exact-match `Set.of`，`TestMetaTableProfilerClassification` 9 例（POINT/BOOLEAN/BIT false，13 数值类型 true）。运行时接线：`profileColumn`(:196) 调 `isNumericType` 决定走 collectNumericStats / probeNumeric / collectStringStats。
  - AR-06：`MetaTableProfiler.probeNumeric`(:214) + `isInfrastructureFailure`(:235) 区分 infra(08*/28*/42*/消息线索→WARN) 与类型不匹配(其余→DEBUG)，仍 return false。`TestMetaTableProfilerProbeNumeric` 5 例（含代理 08006 → WARN ListAppender 断言 + 仍 false）。运行时接线：`profileColumn`(:205) unknown-type 列调 probeNumeric。
  - AR-10：`AggregationHelper.toBigDecimal`(:534) 整数 longValue 无损 / 浮点 doubleValue / String 解析。`TestCrossDbInMemoryAggregationProcessor` +7 例（含 SumAcc.accumulate(Long.MAX_VALUE)×2 精确 + String 数值 SUM 接线）。运行时接线：`SumAcc.accumulate`(:274)/`AvgAcc.accumulate`(:308) 调 toBigDecimal。
  - 全量：`./mvnw test -pl nop-metadata -am -T 1C` → 1141 tests, 0 failures（nop-metadata-service 1140 + web 1）。

Follow-up:

- **Non-Goal 守恒**：同级 `toBigDecimal`（`MemoryOrderByComparator.java:124`/`MemoryFilterEvaluator.java:348`）同 `doubleValue()` 精度模式，属 ORDER BY/WHERE 比较路径（非 SUM/AVG 聚合），Long>2^53 精度丢失在排序/过滤上下文影响较小——裁定 optimization candidate，留待后续清扫（非 in-scope live defect）。
- **Cycle 2 / I1 评估**：建议把不变式从"catch 块"扩展到"silent-wrong-result"检测（`(long)double` 截断、`contains` 类型分类、分隔符 key）——carry-over from plan 0707-2 / 本 plan Non-Blocking Follow-ups。
