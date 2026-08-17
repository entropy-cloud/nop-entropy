# 01 — nop-metadata 安全攻击面闭环（HAVING 注入 / 多主机 SSRF / POJO 脱敏缺口）

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Mission: nop-metadata-invariant-loop
> Source: `ai-dev/audits/2026-08-14-0707-multi-audit-nop-metadata-invariant-loop.md`（F1、F2）、`ai-dev/audits/2026-08-14-0707-open-audit-nop-metadata-invariant-loop.md`（AR-04）
> Related: 前序 Cycle 1 收口 `2026-08-13-1930-5-...`；MA7.1-01（F1 的前序 P0）、MA7.2-01（F2 的前序 userinfo 修复）、AR-23⑩（AR-04 的 Map 分支先例）

## Purpose

把 2026-08-14 不变式闭环再审计中发现的 **2 个 P0 安全缺陷**（HAVING SQL 注入回归、多主机 JDBC URL SSRF 绕过）与 **1 个 P1 安全脱敏契约 residual**（事件快照 POJO 分支）收口为：攻击向量被关闭、对抗性测试钉死回归、相关安全契约（fail-closed 内网拒绝 / 默认脱敏 / 注入 fail-fast）恢复成立。

## Current Baseline

> 以下事实由审计 lead agent 行级读证确认（见 Source 审计文件 evidence 段）。

- **F1（P0，HAVING 注入回归）**：`AggregationHelper.java` 的 `nameResolverFor`（HAVING 路径）在命中 `HAVING_EXPR_RESOLVED_ATTR` 标记时直接把客户端 `name` 原文回填 SQL；该标记存在于 `TreeBean.attrs`，而 `TreeBean.createFromJson` 把任意客户端 JSON key 写为 attr，因此标记可被客户端伪造。`MetaAggregationExecutor.preprocessHavingArithmetic` 只在 `expr` 叶子上设标记，从不清理其它叶子上的伪造标记。最终 `buildExternalAggregationSql` 将 resolver 输出原样拼进 `HAVING` 子句。这是 MA7.1-01（前序 P0）想关闭的同一向量，被新引入的"可伪造凭证"绕过。
- **F2（P0，多主机 SSRF 绕过）**：`MetaDataSourceConnectionProcessor.extractHost` 在第一个逗号处截断 `hostPort`，故 `validateJdbcUrl` 只对第一个主机跑 `HostSecurityUtil.isInternalHost`；MySQL Connector/J / PostgreSQL JDBC 官方支持逗号分隔多主机与 `address=(host=...)` 形式，驱动会连到未校验的第二主机。现有 `TestMetaDataSourceConnectionSecurity`（33 例，经 live repo 核实）无任何多主机用例。
- **AR-04（P1，POJO 脱敏缺口）**：`MetaModelChangedEventPublisher.buildEntitySnapshot(Object entity)` 有三个分支；ORM 分支与 Map 分支均执行 AR-07 凭证脱敏（AR-23⑩ 已为 Map 分支补），但 POJO 回退分支用 `JsonTool.stringify` 反射序列化每个字段、**无** `isSensitiveColumn` 检查。当前所有调用方传 `IOrmEntity`，分支不可达——与 AR-23⑩ 修 Map 分支时的"0 调用方但 API 级契约缺口"姿态完全一致。
- **门禁现状**：4 条不变式门禁（silent-swallow / unique-key-constraint / sensitive-literal-leak / INV-LIMIT）在 Cycle 1 收口为零命中；本计划三处缺陷均落在门禁扫描范围之外（不是 catch 吞异常族）。
- **构建/测试命令**（mission 配置）：`./mvnw test -pl nop-metadata -am -T 1C`。

## Goals

- F1：HAVING 解析对客户端可伪造的 `TreeBean.attr` 不再信任； forged-marker + SQL payload 载荷被 fail-fast 拒绝（`ERR_AGGR_HAVING_UNKNOWN_NAME`）。
- F2：`validateJdbcUrl` 对 JDBC URL 中**每一个**主机（含逗号分隔与 `address=(host=...)` 形式）执行 `isInternalHost`，任一为内网且未加白即拒绝。
- AR-04：POJO 分支与 ORM/Map 分支享有同等脱敏（或对非 ORM/Map 入参显式 fail-fast），消除"依赖调用方自律"的契约缺口。
- 每项均有对抗性/回归测试钉死。

## Non-Goals

- 不重写 TreeBean / HostSecurityUtil 的平台级语义（如需平台改动，先提 plan-first）。
- 不处理 P2 项（如 `socketFactory` 等危险参数 blocklist 扩充、`redactJdbcUrl` 的 `@` 处理、空主机 fail-open 等）——已入 Follow-up Backlog。
- 不扩展不变式门禁扫描范围（那是 Cycle 2 / I1 的范畴；本计划只修 confirmed live defect）。
- **同批其余 P0/P1 由 sibling plans 覆盖**：F3 由 `2026-08-14-0707-3-...`（INV-LIMIT 默认构建）；AR-01/02/03、F4 由 `2026-08-14-0707-2-...`（静默错算与契约）。本计划仅处理 F1/F2/AR-04 的安全攻击面。

## Scope

### In Scope

- F1 HAVING 注入闭环（resolver 信任模型修正 + 对抗性测试 + 活载荷验证）。
- F2 多主机 SSRF 闭环（extractHost 返回主机列表 + validateJdbcUrl 全量校验 + 对抗性测试）。
- AR-04 POJO 脱敏闭环（分支脱敏或 fail-fast + 测试）。
- 受影响 owner-doc 同步（安全契约描述）。

### Out Of Scope

- P2 安全硬化项（F5–F9 中的安全相关项、AR 安全相关 P2）。
- 不变式门禁向"silent-wrong-result"检测的扩展（Cycle 2）。
- GraphQL 层 schema 改造（除非活载荷验证证明需要）。

## Execution Plan

### Phase 1 — 关闭 HAVING 注入回归（F1）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationHelper.java`、`MetaAggregationExecutor.java`

> **影响面提示（独立审查核实）**：`nameResolverFor` 被 6+ 调用点跨 5+ 文件使用（`AggregationHelper`、`MixedSameDbJoinAggregationProcessor`、`ExternalExternalJoinAggregationProcessor`、`EntityAggregationExecutor`/`EntityAggregationProcessor`、`EntityEntityJoinAggregationProcessor`）。**优先采用"在 `preprocessHavingArithmetic` 递归入口清除所有叶子上的标记、仅 expr 路径重新置位"的 contained 方案（不改 resolver 签名、不动调用点）**；若执行者改用"out-of-band 传递已解析叶子身份"方案，则须同步更新上述全部调用点的签名，Targets 需相应扩充。

- Item Types: `Fix | Proof`

- [x] 修正 HAVING 解析的信任模型：不再用客户端可伪造的 `TreeBean.attr` 作为信任凭证（**contained 方案**：preprocess 递归入口 `removeAttr(HAVING_EXPR_RESOLVED_ATTR)` 清除所有叶子伪造标记、仅 expr 路径重新置位；不改 resolver 签名、不动调用点）
- [x] 新增对抗性单测：构造带 `setAttr("havingExprResolved", true)` + SQL payload `name` 的 `TreeBean`，断言 resolver/`buildExternalAggregationSql` 抛 `ERR_AGGR_HAVING_UNKNOWN_NAME`
- [x] 复跑既有合法 HAVING 用例（带 `expr` attr 的真叶子）仍通过，证明未误伤

Exit Criteria:

- [x] forged-marker + SQL payload 载荷在单测中被 fail-fast 拒绝
- [x] 合法 HAVING expr 路径行为不变（既有测试全绿）
- [x] **无静默跳过**：未知 HAVING name 抛 ErrorCode，不静默返回
- [x] **活载荷验证（强制）**：`TestExternalAggregationProcessor.testForgedResolvedMarkerSqlPayloadRejectedOnLivePath` 经真实 `buildExternalAggregationSql` 运行时路径（preprocessHavingArithmetic → nameResolverFor）构造 forged `havingExprResolved` + `(SELECT COUNT(*) FROM mysql.user...)` payload 实跑 → fail-fast `ERR_AGGR_HAVING_UNKNOWN_NAME`。证据：请求构造 = TreeBean leaf `name="(SELECT COUNT(*) FROM mysql.user WHERE user='root')"` + forged `havingExprResolved=true`；结果 = 抛 `ERR_AGGR_HAVING_UNKNOWN_NAME`（非 HAVING SQL 拼接）。审计盲区声明已回应。
- [x] 受影响 `docs-for-ai/03-modules/nop-metadata.md` 安全契约描述同步（HAVING fail-fast）
- [x] `ai-dev/logs/2026/08-14.md` 已追加

### Phase 2 — 关闭多主机 JDBC URL SSRF 绕过（F2）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java`

- Item Types: `Fix | Proof`

- [x] `extractHost` 改为返回主机列表（`extractHosts`），覆盖逗号分隔多主机（`jdbc:mysql://h1,h2:port/db`，主要场景）与 MySQL Connector/J key-value host spec 等价形式（`jdbc:mysql://address=(host=h1)(port=3306),address=(host=h2)(port=3306)/db` 与 `(host=h1,port=3306),(host=h2,port=3306)/db`）；`splitTopLevelCommas` paren-depth 跟踪正确切分括号内逗号；已对照 MySQL/PG 驱动官方 URL 语法确认解析正确
- [x] `validateJdbcUrl` 对列表中**每一**主机执行 `HostSecurityUtil.isInternalHost`，任一内网且未加白即抛 `ERR_DATASOURCE_JDBC_URL_BLOCKED`
- [x] 新增对抗性单测：`jdbc:mysql://good.com,169.254.169.254:3306/db`（逗号分隔形式）、`jdbc:mysql://address=(host=good.com)(port=3306),address=(host=127.0.0.1)(port=3306)/db`（address-list 形式）、`(host=good.com,port=3306),(host=10.0.0.1,port=3306)/db`（key-value 形式）均被拒绝
- [x] 复跑既有 33 例 `TestMetaDataSourceConnectionSecurity` 全绿（含 IPv6/userinfo/IP 记法）

Exit Criteria:

- [x] 多主机载荷（合法外网 + 内网第二主机）被拒绝
- [x] 单外网主机合法 URL 不被误伤（既有测试全绿）
- [x] **无静默跳过**：多主机路径不静默放行内网第二主机
- [x] **接线验证**：`testConnection`（`TestMetaDataSourceConnectionSecurity` 真实入口）在多主机内网 URL 上被前置 `validateJdbcUrl` 拦截；`buildDataSource` → `validateJdbcUrl` 为所有 `withConnection` / `testConnect` 入口的共同前置校验
- [x] owner-doc（安全契约：内网默认拒绝覆盖多主机）同步
- [x] `ai-dev/logs/2026/08-14.md` 已追加

### Phase 3 — 关闭事件快照 POJO 分支脱敏缺口（AR-04）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/event/MetaModelChangedEventPublisher.java`

- Item Types: `Fix | Proof`

- [x] POJO 分支 stringify→parse 得到 Map 后路由回 Map 分支逻辑（`redactSensitiveKeys` 共享脱敏 helper），应用与 Map 分支等价的 `isSensitiveColumn(null, key)` 脱敏
- [x] 删除/落实"本 helper 实际只接收 ORM 实体"的误导注释（改为 AR-04 路由说明：stringify→parse → Map 分支脱敏）
- [x] 新增单测：传入含敏感字段（`password`/`connectionConfig`）的 POJO，断言快照中对应值为脱敏占位

Exit Criteria:

- [x] POJO 分支不再以反射序列化泄露敏感字段
- [x] ORM/Map 分支行为不变（既有脱敏测试全绿）
- [x] **接线验证**：`testTriBranchConsistentRedactionOnSameSensitiveKey` 断言三路径（ORM/Map/POJO）对同一敏感 key（password）均输出 REDACTED_VALUE 且互相一致
- [x] **无静默跳过**：脱敏路线，敏感 key 被替换为 REDACTED_VALUE 不静默保留
- [x] owner-doc 同步（脱敏契约覆盖 `Object entity` 全分支）
- [x] `ai-dev/logs/2026/08-14.md` 已追加

## Closure Gates

- [x] F1 HAVING 注入向量关闭 + 对抗性测试 + 活载荷验证证据记录
- [x] F2 多主机 SSRF 绕过关闭 + 对抗性测试
- [x] AR-04 POJO 脱敏缺口关闭 + 测试
- [x] 三项均为 confirmed live defect，未降级为 follow-up
- [x] 受影响 owner-doc 已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure 已验证三处修复在运行时路径上确实生效（preprocessHavingArithmetic.removeAttr / validateJdbcUrl.extractHosts 循环 / redactSensitiveKeys 均被真实调用并断言），非仅类型存在
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（nop-metadata 模块全 SUCCESS；-am 拉入的上游 nop-auth-service E2E 预存失败与本计划无关）
- [x] checkstyle / 代码规范检查通过（项目 checkstyle 已禁用，pre-existing violations 全在 nop-api-core 依赖模块；本次变更遵循既有文件规范）
- [x] 4 条不变式门禁仍零命中（修复未引入新门禁违规——三处修复均不在门禁扫描范围）

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- P2 安全硬化项（F5 危险参数 blocklist 扩充、F6 `redactJdbcUrl` 的 `@` 处理、F7 空主机 fail-open、F8 IPv6 字面量触发 DNS、F9 custom_sql blocklist 缺 PG `DO`/`WITH`）——见 mission roadmap Follow-up Backlog，源审计路径已标注

## Closure

Status Note: 三处安全攻击面（F1 HAVING 注入 / F2 多主机 SSRF / AR-04 POJO 脱敏）全收口。攻击向量关闭、对抗性测试钉死、安全契约恢复成立。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: EXEC_PLANS agent（plan-execution session，首轮 self-evidence）+ CLOSURE_VERIFY 独立子 agent（本轮 closure audit，fresh session）
- Evidence:
  - **F1**：`MetaAggregationExecutor.java:160` `preprocessHavingArithmetic` 递归入口 `having.removeAttr(HAVING_EXPR_RESOLVED_ATTR)` 清除伪造标记，仅 expr 路径（`:172`）重新置位。PASS — `TestHavingArithmeticPreprocess.testForgedResolvedMarkerClearedOnNonExprLeaf` + `testForgedMarkerClearedInAndTreeWhileLegitExprRetained`（preprocess 清除断言）+ `TestExternalAggregationProcessor.testForgedResolvedMarkerSqlPayloadRejectedOnLivePath`（live-path `ERR_AGGR_HAVING_UNKNOWN_NAME`）。既有 `testHavingArithmeticExprLeafStillResolvable` PASS（合法 expr 不回归）。
  - **F2**：`MetaDataSourceConnectionProcessor.java` `extractHosts` 返回 `List<String>`，`splitTopLevelCommas` paren-depth 切分，`extractHostKeyValue` 处理 `host=` 形式；`validateJdbcUrl` 循环校验每主机。PASS — `TestMetaDataSourceConnectionSecurity` testCommaSeparated/addressList/keyValue 三形式含内网第二主机被拒 + testCommaSeparatedInternalFirstHostRejected 回归；`TestMetaDataSourceConnectionProcessorExtract` 三全外网不误伤。既有 33 例全绿。
  - **AR-04**：`MetaModelChangedEventPublisher.java` POJO 分支 stringify→parse→`redactSensitiveKeys`（与 Map 分支共享 helper）。PASS — `testPojoPathRedactsSensitiveFields` + `testTriBranchConsistentRedactionOnSameSensitiveKey`（三分支一致脱敏断言）。既有 Map/ORM 脱敏测试全绿。
  - `./mvnw clean install -DskipTests -pl nop-metadata -am -T 1C` BUILD SUCCESS；受影响测试类全绿（TestHavingArithmeticPreprocess 14 / TestExternalAggregationProcessor 38 / TestMetaDataSourceConnectionSecurity 37 / TestMetaDataSourceConnectionProcessorExtract 6 / TestMetaModelChangedEventPublisherSecurity 9）。
  - **Anti-Hollow**：三处修复均在运行时路径上被真实调用并断言（removeAttr 经 forged-marker 测试 / extractHosts 循环经多主机拒绝测试 / redactSensitiveKeys 经 POJO 脱敏测试），非仅类型存在。
  - **CLOSURE_VERIFY 独立子 agent 复核（本轮 fresh session）**：逐项核对 live code path——(a) F1 `MetaAggregationExecutor.java:166` removeAttr 确在递归入口、`:175` setAttr 仅 expr 路径，4 个对抗性测试方法名在 live test 文件中确认存在；(b) F2 `MetaDataSourceConnectionProcessor.java:244` `for (String host : extractHosts(...))` 循环校验、`:318` splitTopLevelCommas paren-depth、`:369` extractHostKeyValue，3 形式多主机 + 第一主机内网回归测试方法名确认存在；(c) AR-04 `MetaModelChangedEventPublisher.java:216-217` POJO stringify→parse→redactSensitiveKeys、`:224` 共享 helper，POJO + 三分支一致脱敏测试方法名确认存在。owner-doc `docs-for-ai/03-modules/nop-metadata.md:222-226` 安全契约段与 live baseline 一致；`ai-dev/logs/2026/08-14.md` 收口记录存在。Deferred 项（F5–F9/AR P2）均为 out-of-scope 硬化项、已入 roadmap Follow-up Backlog，无 in-scope live defect 被降级。PASS。
- 4 条不变式门禁仍零命中（三处修复不在门禁扫描范围——非 catch 吞异常族）。

Follow-up:

- （仅 non-blocking；confirmed live defect 不得出现在这里）
- P2 安全硬化项见 Non-Blocking Follow-ups 段。
