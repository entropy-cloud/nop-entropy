# nop-metadata SSRF 主机提取闭环 + sql 路径日志脱敏（2026-08-15 multi-audit P0 + P1-8）

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 3 / 再审计 remediation（执行顺序 1/3）
> Source: `ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P0 F2 SSRF 绕过 + P1-8 sql 路径 INFO 全文日志）
> Related: 后继 `2026-08-15-1913-2`（API 契约语义）、`2026-08-15-1913-3`（错误码参数与守卫测试）；先例 plan `2026-08-14-0707-1`（F2 首修）、`2026-08-14-1133-1`（F5-F9 纵深）
> Draft Review: R1（Blocker×1+Major×3+Minor×5）→ 修订 → R2（R1 全 Resolved；新增 Major×1+Minor×2 均为 R2 预定义 consensus 条件项，已全部修订并 live 复核）→ consensus 达成，2026-08-15 转 active

## Purpose

关闭 2026-08-15 multi-audit 确认的 1 个 P0（F2 SSRF 防御的两个驱动语义级绕过）和 1 个 P1（P1-8 sql 视图路径 INFO 级 SQL 全文落日志），使 F2 "对 URL 中每一主机执行内网校验" 契约与 AR-16 "INFO 只记 sqlHash" 契约在 live 代码中重新成立，并用对抗性回归测试钉死。

## Current Baseline

> 事实为 2026-08-15 live repo 实测（rg 逐条核对；R1 审查子代理独立复核通过）。

- **P0 修复对象**：`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java`
  - `extractHosts`（:364-390）：authority 截断于首个 `?`（:373-375 `minPositive(slash, q)`）——query string 完全不参与主机提取；
  - `extractSingleHost`（:424-437）→ `extractHostKeyValue`（:447+，`HOST_KEY_VALUE_PATTERN` :445 为大小写敏感的 `host=`）/`extractPlainHost`（:468+）：不含 `host=` 键的属性组段（如 `(port=3306)`、`address=(port=3306)`）整段按"主机名"原样返回；`redactJdbcUrl` 为 public static 可供错误参数脱敏使用；
  - `isPlausibleHostShape`（:285-299）：首字符 `(` 不在任何拒绝分支 → 放行；`isInternalHost` 无前缀命中判外部 → 放行；
  - `DANGEROUS_URL_TOKENS`（:70 起）经 `validateJdbcUrl`（:246-252）做**整个 URL 的 `contains`** 检查——不含 `host=`/`hostaddr=`；注意该机制无 query 作用域，直接加入 `host=` 会误杀 authority 中合法的 `(host=good.com,port=3306)` 形态并打挂既有 F2 测试（见 Phase 1 方案裁定）；
  - `extractSingleHost`/`extractHosts` 为 static，链路内拿不到 jdbcUrl/redactJdbcUrl 上下文——新增拒绝需在 `validateJdbcUrl` 层附加（或下传脱敏后 URL 作 `.param`）。
  - **实机验证（audit 复核）**：4 个攻击向量全部通过 `validateJdbcUrl`：`jdbc:mysql://(port=3306)/db`、`jdbc:mysql://address=(port=3306)/db`（MySQL Connector/J 9.2.0 对 hostless 属性组默认 localhost）、`jdbc:postgresql://public.example.com/db?host=127.0.0.1`、`jdbc:postgresql://public.example.com:5432/db?host=169.254.169.254`（pgjdbc 42.7.9 query `host=` 完全覆盖 authority 主机）。
  - 既有回归防线：`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestMetaDataSourceConnectionSecurity.java`（F2 测试 :351-413 主体为显式 `host=` 键形态（:360/:407 为纯逗号主机形态）；`testAddressListMultiHostInternalSecondHostRejected`/`testKeyValueMultiHostInternalSecondHostRejected` 的 reason 断言依赖逐主机校验语义——修复不得改变这两个用例的通过结果与 reason 内容形态）。
- **P1-8 修复对象**（audit 复核确认 8 处 sql 路径，live rg 核对；另 3 处同源/相邻预期并入，见下）：
  - `service/query/MetaTableQueryExecutor.java:100`、`service/query/ExternalAggregationProcessor.java:84`、`service/query/MixedSameDbJoinAggregationProcessor.java:160`、`service/query/MetaJoinExecutor.java:362`、`MetaJoinExecutor.java:662`（经 `buildTableFromClause`(:562)/`tableFromForJoin`(:582) 于 :571/:591 嵌入 sourceSql，确认为 sql 路径）、`service/query/ExternalExternalJoinAggregationProcessor.java:125`、`service/profiling/MetaTableProfiler.java:484`、`MetaTableProfiler.java:495`。
  - `MetaTableProfiler.java:515/:534`：R1 审查调用图核实——`queryNullableLong`(:515) ← `collectStringStats`(:320-321) 与 `queryString`(:534) 消费的 `fromClause` 与 :484/:495 **完全同源**（`buildFromClause` :590-595 对 sql 表返回 `"(" + sourceSql + ") _t"`）——四个 helper 结构等价，sql 表被 profile 时全部落 sourceSql 全文。
  - `MetaJoinExecutor.java:300`（entity-entity EQL 路径，audit 裁定仅白名单标识符、无害不计入）：**为使机械 rg gate 与修复清单一致，并入修复口径**（形态统一，不改变 audit 裁定的无害性结论）。
  - **本计划按 11 处修复口径执行**（8 确认 + 2 profiler 同源 + 1 :300 形态统一；执行时若发现 515/534 反证可显式裁定降回并记录理由）。
  - **先例形态（AR-16，plan 2026-08-14-1133-1 同族）**：`service/quality/MetaQualityRuleExecutor.java:675-676` — `LOG.info("... sqlHash={}", sqlHashOf(sql))` + `LOG.debug("... SQL: {}", sql)`；`sqlHashOf` 为该类 `:454` public static 方法。
- **风险面**：`testConnection`/`syncExternalTables` 为 `@BizMutation`，可登录用户创建自己的数据源即可触发建连——SSRF 内网探测原语 + 云元数据端点直连。

## Goals

- F2 契约对两个已验证绕过向量 fail-closed：hostless 属性组（隐式 localhost）被拒绝；query 中的 `host=`/`hostaddr=` 主机（含编码/大小写/多值变体）纳入逐主机内网校验。
- 4 个实机验证攻击向量 + 编码/大小写/多值/hostaddr 变体全部转为回归测试并断言被拒；合法 query 主机与良性参数不误伤。
- sql 路径 11 处（8 确认 + 2 同源 + 1 形态统一）INFO 日志改为 `sqlHash` 摘要，SQL 全文降 DEBUG，与 AR-16 形态一致。
- owner 文档安全契约叙述与 live 行为同步（F2 契约条目补充 query 参数主机/hostless 属性组处置语义）。

## Non-Goals

- 不重构 `extractHosts` 家族以外的 JDBC URL 解析逻辑（userinfo 剥离、IPv6 归一化等已在 F2/F6/F7/F8 先例中收口）。
- 不处理 P2-06（webhook 侧 CheckpointActionDispatcher 无 F7 形状校验）——已登记 backlog。
- 不处理 P2-08（SQLException 原始消息进 error param）——已登记 backlog。
- 不验证 MariaDB/Oracle JDBC 等其他驱动的等价语义（audit 自评盲区，超出本轮 closure 面）。

## Scope

### In Scope

- `MetaDataSourceConnectionProcessor.java` 主机提取与校验逻辑（含 query 部分主机提取）。
- 上述 11 处日志站点及其调用路径上的日志语句。
- `TestMetaDataSourceConnectionSecurity.java` 新增对抗向量回归；日志脱敏回归测试（沿模块内 logback ListAppender 日志断言既有模式，如 `TestNopMetaQualityCheckpointBizModel`）。
- `docs-for-ai/03-modules/nop-metadata.md` 安全契约条目同步（F2 契约位于 :247 附近）。

### Out Of Scope

- `NopMetaDataSourceBizModel` 及 GraphQL 面（P1-1 归 `2026-08-15-1913-2`）。
- 错误码参数族（P1-6/P1-7 归 `2026-08-15-1913-3`）。

## Execution Plan

### Phase 1 - P0：主机提取 fail-closed + query 主机纳入校验

Status: completed
Targets: `MetaDataSourceConnectionProcessor.java`、`TestMetaDataSourceConnectionSecurity.java`

- Item Types: `Fix | Decision | Proof`

- [x] **[Fix]** `extractSingleHost`：对含 `=`/`(`/`)` 的属性组段在无 `host=` 键命中时使该校验路径显式失败（hostless 属性组 = 驱动隐式 localhost，fail-closed）。实现位置裁定（static 链路无 jdbcUrl 上下文）：在 `extractSingleHost` 返回哨兵值由 `validateJdbcUrl` 层统一抛 `ERR_DATASOURCE_JDBC_URL_BLOCKED`（附 `.param("reason", ...)`，jdbcUrl 经 `redactJdbcUrl` 脱敏后传入）或等价下传方案——取其一，理由记 daily log。键匹配大小写口径：现状 `HOST_KEY_VALUE_PATTERN` 大小写敏感；`(HOST=...)` 段因含 `=` 无键命中会被本条 fail-closed 拒绝——该误伤面**接受**（驱动侧键大小写不敏感，fail-closed 方向安全），在 owner doc 一并说明。
- [x] **[Fix]** query 部分的 `host=`/`hostaddr=` 主机提取并纳入逐主机 `isInternalHost` 校验。**解析语义（防绕过，硬要求）**：按 pgjdbc `Driver.parseURL` 语义对齐——(a) query 参数**值做 percent-decode** 后校验；(b) 参数名**大小写不敏感**（`Host=`/`HOST=` 同 `host=`）；(c) **重复参数逐值校验**（`?host=a&host=b` 两个值都过校验）；(d) `hostaddr=` 同 `host=` 处置。
  - **方案裁定（已预裁定，执行时确认）**：采用"提取校验"方案。**否决 over-block 方案**（把 `host=` 加入 `DANGEROUS_URL_TOKENS`）：该集合做整 URL `contains` 匹配，会误杀 authority 中合法的 `(host=good.com,port=3306)`/`address=(host=...)` 形态、打挂既有 F2 测试的 reason 断言，直接违反 owner doc :247 逐主机校验契约。若执行时发现提取校验不可行须回到 over-block，必须仅对 query 子串作用域匹配并显式接受"拒绝一切 query host=（含合法外网）"的兼容性裁定（owner doc 记载 + 补一条 `?host=public.example.com` **被拒**的钉死测试）。
- [x] **[Proof]** 回归测试（新增，全部断言抛 ErrorCode 异常）：
  - 4 个实机验证向量：`jdbc:mysql://(port=3306)/db`、`jdbc:mysql://address=(port=3306)/db`、`jdbc:postgresql://public.example.com/db?host=127.0.0.1`、`jdbc:postgresql://public.example.com:5432/db?host=169.254.169.254`；
  - 编码/大小写/多值变体：`?host=%31%32%37%2e%30%2e%30%2e%31`（percent-decode 后 127.0.0.1）、`?Host=10.0.0.1`（大小写不敏感）、`?host=169.254.169.254&host=x`（多值逐校验）、`?hostaddr=127.0.0.1`；
  - 不误伤用例：`?connectTimeout=10000`、`?applicationName=x`（良性参数不受影响——注意不得用 `?useSSL=false` 作向量：该 token 已在 `DANGEROUS_URL_TOKENS` 中被 F5 时代有意拒绝）、`?host=public.example.com`（query 合法外网主机放行）。
- [x] **[Proof]** 既有 F2 测试全绿（显式 `host=` 键的多主机形态不回退，reason 断言不变红）。

Exit Criteria:

- [x] 上述全部攻击向量 + 变体 + 不误伤用例在 `./mvnw test -pl nop-metadata -am` 下全绿，测试方法名可在仓库中定位。（`testHostlessAttributeGroupRejected` / `testQueryHostOverridesAuthorityRejected` / `testQueryHostVariantsRejected` / `testBenignQueryParamsAndExternalQueryHostNotBlocked`；service 1211/0/0）
- [x] `validateJdbcUrl` 对 hostless 属性组与 query-host 两类形态（含编码/大小写/多值）均显式失败，无静默放行路径。
- [x] 方案裁定（实现位置、大小写口径）及理由已记录于 `ai-dev/logs/` 对应日期条目。（2026-08-15 log Phase 1 条目：哨兵+集中抛出实现位置、HOST_KEY_VALUE_PATTERN 大小写敏感误伤面接受、提取校验方案确认）
- [x] `docs-for-ai/03-modules/nop-metadata.md` F2 契约叙述更新：明确 query 参数主机参与逐主机校验、hostless 属性组 fail-closed、`(HOST=...)` 类大小写变体拒绝语义（本 Phase 改变 live 校验行为，owner-doc 更新为强制项）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - P1-8：sql 路径日志 sqlHash 化（AR-16 形态对齐）

Status: completed
Targets: `MetaTableQueryExecutor.java`、`ExternalAggregationProcessor.java`、`MixedSameDbJoinAggregationProcessor.java`、`MetaJoinExecutor.java`、`ExternalExternalJoinAggregationProcessor.java`、`MetaTableProfiler.java`

- Item Types: `Fix | Decision | Proof`

- [x] **[Fix]** 11 处 `LOG.info(... SQL: {}, sql/sqlText)` 全部改为 INFO 级 `sqlHash={}` 摘要 + DEBUG 级全文（复用/提权 `sqlHashOf`，沿 `MetaQualityRuleExecutor.java:675-676` 先例形态；`MetaJoinExecutor.java:300` 为 entity 路径形态统一，无泄漏面变更）。
- [x] **[Decision]** 若执行时发现 `MetaTableProfiler.java:515/:534` 反证（非 sql 路径），记录不修理由（daily log）并降回 8 处口径。（执行确认无反证：queryNullableDouble/queryNullableLong 与 queryLong/queryString 的 fromClause 完全同源，四 helper 全修，维持 11 处口径——见 daily log Phase 2 条目）
- [x] **[Proof]** 回归测试：至少覆盖 `queryTableData`（sql 视图路径）与 `profileTable`（sql 路径）两条入口的日志脱敏断言（INFO 级输出含 sqlHash 不含 sourceSql 全文；沿模块内 logback ListAppender 断言既有模式）。

Exit Criteria:

- [x] `rg -n 'LOG\.info\([^)]*\{\}[^)]*,\s*(sql|sqlText)\s*\)' <6 个目标文件>` 零命中（机械可判定：INFO 级不再直接打 sql/sqlText 变量；实测该模式当前命中 11 处，与修复清单一一对应）。（2026-08-15 live 实测零命中）
- [x] profiler :515/:534 按 11 处口径修复，或有显式反证裁定记录。（按 11 处口径修复，无反证）
- [x] 脱敏回归测试全绿。（`TestSqlPathLogRedaction` 2/2）
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> 本计划为代码变更计划，构建验证条目适用。

- [x] P0 两个绕过向量（hostless 属性组、query host= 含编码/大小写/多值变体）在 live 校验代码中显式 fail-closed（confirmed live defect 已修复）
- [x] 全部攻击向量 + 不误伤回归测试存在且全绿
- [x] 11 处（或降级裁定后口径）sql 路径 INFO 日志零 SQL 全文残留（机械 rg 判定零命中）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] `docs-for-ai/03-modules/nop-metadata.md` 安全契约叙述与 live 行为一致
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证攻击向量从 `@BizMutation` 入口到 `validateJdbcUrl` 拒绝路径连通（不是只改了未被调用的私有方法）
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [x] checkstyle 对本计划改动文件零新增违规（上游 `nop-api-core` 存在 9164 条 pre-existing 基线、整体 `checkstyle:check` 历史性 exit 1，见 2026-08-15 log——以改动文件零新增为基准）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0

## Deferred But Adjudicated

### 其他驱动语义等价性验证（MariaDB/Oracle JDBC）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: audit 自评盲区；本轮 closure 面限于已实机验证的两个向量；其他驱动在本模块方言白名单（MySQL/PG/H2）之外，不可达。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- P2-06 webhook 侧主机提取无形状校验（lucky fail-closed 纵深不对称）——见 roadmap Follow-up Backlog。
- P2-08 SQLException 原始消息进 error param——见 roadmap Follow-up Backlog。

## Closure

Status Note: P0（F2 双驱动语义绕过）与 P1-8（sql 路径 INFO 全文日志）均已在 live 代码修复并被对抗性回归与机械 rg 门禁钉死；owner doc、daily log、roadmap 同步完成；独立 closure audit 12/12 检查全 PASS（approved）。后继 P1-1..P1-7/P1-9 已路由至 plan 2026-08-15-1913-2 / -3，非本计划遗留。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，review-only 零文件修改），session `ses_ffa74be50ffesPAQkXfOMGnovF`
- Evidence:
  - Phase 1 Exit Criteria 4/4 PASS：哨兵+集中抛出（`MetaDataSourceConnectionProcessor.java:588-591`/`:276-279`/`:440`/`:469-515`）；4 个对抗测试方法 + 既有 F2 reason 断言不回退（`TestMetaDataSourceConnectionSecurity.java:467/:493/:518/:548/:376/:394`）；owner doc `nop-metadata.md:247-249`；daily log Phase 1 条目（`08-15.md:14-25`，含三项方案裁定记录）。
  - Phase 2 Exit Criteria 4/4 PASS：机械 rg 门禁 6 文件 0 命中（独立复跑）；INFO sqlHash 站点恰 11 处（1+1+1+3+1+4，:103/:87/:163/:303+:369+:673/:128/:487+:501+:524+:546）且每处配对 LOG.debug 全文；`TestSqlPathLogRedaction` 双入口断言（INFO 含 hash 不含 sourceSql/敏感字面量、DEBUG 保留全文）；daily log Phase 2 条目（11 处口径 + :515/:534 无反证裁定）。
  - Closure Gates 11/11 PASS：含 `./mvnw test -pl nop-metadata -am -T 1C` 全绿（service 1211/0/0，TestMetaDataSourceConnectionSecurity 50/50、TestSqlPathLogRedaction 2/2）；checkstyle 改动文件零新增违规（nop-metadata 模块 964 条 pre-existing 全在 api/dto 未改动层）；`scan-hollow-implementations --severity high` exit 0。
  - Anti-Hollow 检查：运行时调用链独立追踪证实——`NopMetaDataSourceBizModel#testConnection`（@BizMutation :119→:128）与 `#syncExternalTables`（@BizMutation :177→:196）→ `@Inject IMetaDataSourceConnectionProcessor`（beans.xml:12-13 接线）→ `buildDataSource` → **`validateJdbcUrl` :212（先于 SimpleDataSource 构造与 getConnection）**；8 改动文件无空方法体/静默 no-op（唯一 `{}` 命中为 MetaTableQueryExecutor 既有私有构造器惯用法，不在本计划 diff 内）。
  - Deferred 项分类检查：P2-06/P2-08 与 MariaDB/Oracle 语义验证均为 draft 期 Non-Goals 声明，非 live defect 降级；日志站点口径实际 8→11 扩张（显式裁定记录）。
  - `check-plan-checklist --strict` exit 0（见 daily log 收口条目复跑记录）。

Follow-up:

- P1-1..P1-5（API 写路径与契约语义）→ plan `2026-08-15-1913-2`
- P1-6/P1-7/P1-9（错误码参数与守卫测试）→ plan `2026-08-15-1913-3`
- P2-06 / P2-08 → roadmap Follow-up Backlog（non-blocking）
