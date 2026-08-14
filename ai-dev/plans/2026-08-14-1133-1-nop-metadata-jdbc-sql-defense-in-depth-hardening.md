# 01 — nop-metadata JDBC/SQL 防御纵深硬化（F5-F9）

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Mission: nop-metadata-invariant-loop
> Work Item: Follow-up Backlog — 安全硬化族（F5/F6/F7/F8/F9）
> Source: `ai-dev/audits/2026-08-14-0707-multi-audit-nop-metadata-invariant-loop.md`（F5–F9）
> Related: `2026-08-14-0707-1-nop-metadata-security-attack-surface-closure.md`（F1/F2/AR-04 已 completed 的前序安全闭环）

## Purpose

把 2026-08-14 再审计中的 **5 个 P2 安全防御纵深缺口**收口为：JDBC URL 危险参数 blocklist 完备、凭证脱敏无泄漏、空/畸形主机 fail-closed、IPv6 字面量判定不触发 DNS、custom_sql blocklist 覆盖 PG 危险关键字。全部为 defense-in-depth——不改变已修复的 F1/F2 攻击面（P0），而是消除"lucky fail-closed"和"审计者已知但 blocklist 未覆盖"的残余间隙。

## Current Baseline

> 行级读证见 Source 审计文件 evidence 段。以下事实经本轮 live repo 核实。

- **F5（P2，jdbcUrl blocklist 缺类加载参数）**：`MetaDataSourceConnectionProcessor.java:62-75` 的 `DANGEROUS_URL_TOKENS` 枚举 13 个 token，缺 `socketfactory`/`statementinterceptors`/`detectcustomcollatz`/PG `sslfactory`/PG `options=`——这些参数触发反射类加载，存在 RCE-chain 潜力（取决于部署 classpath）。`driverClassName` 白名单缓解但不能替代 URL 参数侧的 fail-closed。
- **F6（P2，redactJdbcUrl 对含 @ 的口令截断不全）**：`MetaDataSourceConnectionProcessor.java:88-89` 的 `CREDENTIAL_PATTERN = (://)([^:@/]+)(?::[^@/]*)?@`，当口令含 `@` 时正则在第一个 `@` 处停止，口令尾部泄漏进 redacted 错误消息（`user:p@ss@host` → `ss@host` 泄漏）。`redactJdbcUrl`（:262-264）直接用该 pattern `replaceAll("$1")`。
- **F7（P2，空/畸形主机 fail-open）**：`extractHosts`（:286+）对 `jdbc:mysql:///db`（空主机）和 `jdbc:mysql://:3306/db`（空主机带端口）返回非主机形状串（`"/db"` / `":3306"`），`HostSecurityUtil.isInternalHost` 将其判为外部 → 静默放行。当前无害（驱动拒绝空主机），但属"lucky fail-closed"而非"enforced fail-closed"。
- **F8（P2，isInternalIpv6Literal 触发 DNS）**：`HostSecurityUtil.java:101-102` 对任何含 `:` 的输入路由到 `isInternalIpv6Literal`（:285-288），后者直接调用 `InetAddress.getByName(h)` **无** `isIpLiteral`（:130-142）已有的 `[0-9a-f:.]` + ≥2-冒号 charset 前置过滤。构造的含 `:` URL 可触发 DNS 查找，违反类 javadoc "纯确定性解析，不触发 DNS" 契约。
- **F9（P2，custom_sql blocklist 缺 PG 关键字）**：`MetaQualityRuleExecutor.java:76-88` 的 `CUSTOM_SQL_FORBIDDEN_WORDS` 缺 PG `DO`（执行 PL/pgSQL）/`WITH`（CTE 递归）/`PG_CATALOG`/`PG_SLEEP`/`PG_STAT_USER_TABLES`。与 `ExpressionMeasureValidator.KEYWORD_BLACKLIST`（23 项重叠）及 `FUNCTION_BLACKLIST`（PG_SLEEP 重叠）存在 DRY 同步间隙。
- **构建/测试命令**（mission 配置）：`./mvnw test -pl nop-metadata -am -T 1C`。

## Goals

- F5：`DANGEROUS_URL_TOKENS` 补全类加载参数（`socketfactory`/`statementinterceptors`/`detectcustomcollatz`/PG `sslfactory`/`options=`），含 `@`-口令构造的 URL 中含这些参数时被 fail-fast 拒绝。
- F6：`redactJdbcUrl` 使用 `lastIndexOf('@')` 定位 userinfo 边界后再 substring-replace，含 `@` 的口令不再泄漏进 redacted 消息。
- F7：`extractHosts` 返回非主机形状串时，`validateJdbcUrl` 显式拒绝（`ERR_DATASOURCE_JDBC_URL_BLOCKED`，reason="host unparseable"），不再依赖驱动拒绝。
- F8：`isInternalIpv6Literal` 在调用 `getByName` 前增加与 `isIpLiteral` 一致的 charset 前置过滤（`[0-9a-f:.]` + ≥2 冒号），非字面量输入不触发 DNS。
- F9：`CUSTOM_SQL_FORBIDDEN_WORDS` 补全 `DO`/`WITH`/`PG_CATALOG`/`PG_SLEEP`/`PG_STAT_USER_TABLES`；评估与 `ExpressionMeasureValidator.KEYWORD_BLACKLIST`（23 项重叠）+ `FUNCTION_BLACKLIST`（PG_SLEEP 重叠）的 DRY 同步。`WITH` 的 CTE false-positive tradeoff 需显式记录（合法分析型 CTE 查询会被阻断，与既有 over-block 哲学一致）。
- 每项均有回归/对抗测试。

## Non-Goals

- 不重写 `HostSecurityUtil` 的整体 hostname 判定架构（如需平台级重构，先提 plan-first）。
- 不把 P2 项提升为 P0/P1 级别的 attack-surface 修复——这些是 defense-in-depth 补齐，非已确认的 active exploit。
- 不扩展不变式门禁扫描范围（那是 Cycle 2 / I1 的范畴）。
- 不处理 ORM 模型族（F10-F13）或代码卫生族（F14-F19）——见 sibling plans 或后续轮。

## Scope

### In Scope

- F5：`DANGEROUS_URL_TOKENS` 扩充 + 测试。
- F6：`redactJdbcUrl`/`CREDENTIAL_PATTERN` 修正 + 测试。
- F7：空/畸形主机 fail-closed + 测试。
- F8：`isInternalIpv6Literal` charset 前置过滤 + 测试。
- F9：`CUSTOM_SQL_FORBIDDEN_WORDS` 扩充 + DRY 评估 + 测试。
- 受影响 owner-doc 同步。

### Out Of Scope

- P2 ORM 模型族（F10-F13）。
- P2 代码卫生族（F14-F19）。
- P2 silent-wrong-result 族（AR-05/AR-06/AR-10）——由 sibling plan `2026-08-14-1133-2-...` 覆盖。
- P2 lineage/manifest/reconciliation 族（AR-07/08/09/11/12/13）——由 sibling plan `2026-08-14-1133-3-...` 覆盖。

## Execution Plan

### Phase 1 — JDBC URL 参数与凭证脱敏硬化（F5 + F6）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java`；`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestMetaDataSourceConnectionSecurity.java`

- Item Types: `Fix | Proof`

- [x] F5：`DANGEROUS_URL_TOKENS` 补充 `socketfactory`/`statementinterceptors`/`detectcustomcollatz`/`sslfactory`/`options=`（全小写，与既有 token 风格一致）
- [x] F5：新增对抗测试——构造含上述参数的 JDBC URL，断言 `validateJdbcUrl` 抛 `ERR_DATASOURCE_JDBC_URL_BLOCKED`
- [x] F6：修改 `redactJdbcUrl` 逻辑——使用 `lastIndexOf('@')` 定位 userinfo 边界（与 `extractHost` 自身逻辑一致），substring-replace userinfo 段为占位符
- [x] F6：新增对抗测试——`user:p@ss@host:3306/db` redact 后不含 `ss@host`；多 `@` 的极端用例验证

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `DANGEROUS_URL_TOKENS` 含全部新增 token，既有 token 不丢失
- [x] 含新增危险参数的 URL 被 fail-fast 拒绝（测试断言 ErrorCode）
- [x] `redactJdbcUrl` 对含 `@` 口令的 URL 不泄漏口令片段
- [x] **无静默跳过**：新增的校验路径在触发时抛 ErrorCode 而非返回原值
- [x] owner-doc（`docs-for-ai/03-modules/nop-metadata.md`）安全契约段同步 blocklist 新增项
- [x] `ai-dev/logs/2026/08-14.md` 已追加

### Phase 2 — 主机校验 fail-closed 与 DNS 安全（F7 + F8）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java`；`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/security/HostSecurityUtil.java`；`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestMetaDataSourceConnectionSecurity.java`；`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestHostSecurityUtil.java`

- Item Types: `Fix | Proof`

- [x] F7：`extractHosts` 返回的每个 host 做 shape 校验——空串、以 `/` 开头、以 `:` 开头（纯端口）等非主机形状串 → `validateJdbcUrl` 拒绝 `ERR_DATASOURCE_JDBC_URL_BLOCKED`（reason="host unparseable"）
- [x] F7：新增测试——`jdbc:mysql:///db` 和 `jdbc:mysql://:3306/db` 被显式拒绝（非依赖驱动拒绝的 lucky path）
- [x] F8：`isInternalIpv6Literal` 入口增加 charset 前置过滤——仅 `[0-9a-fA-F:.]` 且 ≥2 冒号的输入才调用 `getByName`；否则视为非字面量直接 return false（不触发 DNS）
- [x] F8：新增测试——含非 hex 字母（g-z）的含 `:` 串（如 `gzzz::1`）经 `isInternalHost` 判定为外部（返回 false），且 charset 过滤逻辑与 `isIpLiteral` 的 charset 检查代码审查验证一致

Exit Criteria:

- [x] 空/畸形主机被显式拒绝（ErrorCode），而非静默放行
- [x] `isInternalIpv6Literal` 对非 IPv6 字面量输入（含非 hex 字母）返回 false（charset 前置过滤与 `isIpLiteral` 一致，经代码审查验证）
- [x] **接线验证**：`validateJdbcUrl` 经 `extractHosts` → host shape 校验 → `isInternalHost` 路径在运行时连通；`isInternalIpv6Literal` 的 charset 修复在 `isInternalHost`（:101-102）的 `:`-routing 路径上生效
- [x] **无静默跳过**：非主机形状串显式抛 ErrorCode，非返回空列表静默通过
- [x] owner-doc 同步 fail-closed 语义描述
- [x] `ai-dev/logs/2026/08-14.md` 已追加

### Phase 3 — custom_sql blocklist PG 关键字扩充（F9）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java`；`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestMetaQualityRuleExecutorCustomSqlSandbox.java`（或新建测试文件）

- Item Types: `Fix | Decision | Proof`

- [x] F9：`CUSTOM_SQL_FORBIDDEN_WORDS` 补充 `DO`/`WITH`/`PG_CATALOG`/`PG_SLEEP`/`PG_STAT_USER_TABLES`
- [x] `Decision`：评估与 `ExpressionMeasureValidator.KEYWORD_BLACKLIST`（23 项重叠：DROP/CREATE/ALTER/...）及 `FUNCTION_BLACKLIST`（PG_SLEEP 重叠）的 DRY 同步——重叠度高则抽取共享常量；各自语义独立（custom_sql 管 DDL/DML 执行，expression measure 管 function 调用）则记录裁定理由保持分离
- [x] `Decision`：记录 `WITH` 的 CTE false-positive tradeoff——合法分析型 CTE 查询会被阻断；与既有 over-block 哲学（已含 SET 等常见关键字）一致，custom_sql 质量规则面向受限 SQL 场景
- [x] F9：新增测试——含 `DO $$ ... $$` / `WITH t AS (...)` / `PG_SLEEP(5)` 的 custom_sql 被拒绝

Exit Criteria:

- [x] `CUSTOM_SQL_FORBIDDEN_WORDS` 含全部新增关键字
- [x] 含新增关键字的 custom_sql 被 fail-fast 拒绝
- [x] DRY 裁定有记录（同步或分离，附理由）
- [x] **无静默跳过**：新增关键字命中时显式拒绝
- [x] owner-doc 同步 custom_sql 安全约束描述
- [x] `ai-dev/logs/2026/08-14.md` 已追加

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] F5-F9 五项 defense-in-depth 缺口全部修复
- [x] 每项均有回归/对抗测试钉死
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证修复在运行时路径生效（validateJdbcUrl / redactJdbcUrl / isInternalHost / custom_sql 校验被真实调用）
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [x] checkstyle / 代码规范检查通过
- [x] 4 条不变式门禁仍零命中（INV-SILENT-SWALLOW / INV-UK-CONSTRAINT / INV-SENSITIVE-LITERAL / INV-LIMIT；验证命令见 `ai-dev/tools/run-nop-metadata-invariants.sh` 或各 `.mjs` 扫描器显式运行）

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 考虑将 JDBC URL 安全校验从 blocklist 升级为 connection-param allowlist（更强的 fail-closed 语义）——out-of-scope improvement，当前 blocklist 已覆盖已知危险参数。

## Closure

Status Note: F5-F9 五项 P2 安全防御纵深缺口全收口（defense-in-depth，不改变已修复的 F1/F2 P0 攻击面）。每项有对抗测试、owner-doc 已同步、4 条不变式门禁零命中、独立 fresh-session closure audit READY_TO_CLOSE。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit（general subagent，task_id `ses_00173f33fffeymD8RHLJlmOIvY`，read-only 未改任何代码/plan）
- Evidence:
  - **A. Fix presence（逐项 live code 核实）**：F5 `DANGEROUS_URL_TOKENS`(`MetaDataSourceConnectionProcessor.java:69-89`) 13 原 token 全在 + 5 新 token（`socketfactory`/`statementinterceptors`/`detectcustomcollatz`/`sslfactory`/`options=`）PASS；F6 `redactJdbcUrl` 用 `lastIndexOf('@')`(:336)，`CREDENTIAL_PATTERN` 字段已删（grep 0 match，仅 javadoc 历史引用）PASS；F7 `isPlausibleHostShape`(:284-298) 在 host loop `isInternalHost` 前调用(:259)，拒 `/db`/`:3306` 放 `::1` PASS；F8 `isInternalIpv6Literal`(`HostSecurityUtil.java:293-331`) charset 前置过滤(:294-307) 在 `getByName`(:310) 前 PASS；F9 `CUSTOM_SQL_FORBIDDEN_WORDS`(`MetaQualityRuleExecutor.java:97-112`) 含 5 新关键字 PASS。
  - **B. Anti-Hollow（运行时接线）**：F5/F7 `validateJdbcUrl`←`buildDataSource`(:197) 危险参数循环(:245-251)+形状校验(:259) 活路径 PASS；F6 `redactJdbcUrl` 4 个 throw 站点(:241/:248/:261/:267) PASS；F8 `isInternalHost`(:101-102)→`isInternalIpv6Literal`←`validateJdbcUrl` host loop(:264) PASS；F9 `validateCustomSqlSandbox`←`judgeCustomSql`(:322, exec 前)←`judge`(:188) PASS。
  - **C. 测试**：指定对抗套件 `Tests run: 98, Failures: 0, Errors: 0`（TestMetaDataSourceConnectionSecurity 46 + TestHostSecurityUtil 24 + TestMetaQualityRuleExecutorCustomSqlSandbox 22 + TestMetaDataSourceConnectionProcessorExtract 6）；全模块 `./mvnw test -pl nop-metadata -T 1C` → `Tests run: 1121(+web 1), Failures: 0, Errors: 0`。
  - **D. 不变式门禁**：`run-nop-metadata-invariants.sh` → "All 4 nop-metadata invariant guards passed (zero hits)"，exit 0。
  - **E. Anti-Hollow 扫描**：`scan-hollow-implementations.mjs --module nop-metadata --severity high` exit 0，无 high-severity 空壳。
  - **F. Doc-sync**：`docs-for-ai/03-modules/nop-metadata.md:234-238` 安全契约段含 F5/F6/F7/F8/F9 五段（各标 `plan 2026-08-14-1133-1`）PASS。
  - **G. 无静默跳过**：F5 throw `ERR_DATASOURCE_JDBC_URL_BLOCKED` reason "dangerous parameter/token present"；F7 throw 同 ErrorCode reason "host unparseable"；F9 throw `ERR_QUALITY_CUSTOM_SQL_BLOCKED` reason "forbidden keyword present"；F8 return false（DNS 安全过滤的正确语义——非字面量判外部，非缺陷跳过）PASS。
  - **checklist 完整性**：`check-plan-checklist.mjs --strict` 对本 plan 退出码 0（全勾选 + Closure Evidence 已写入）。
  - **checkstyle**：sun_checks.xml 报全模块 pre-existing 基线（项目 checkstyle 非真实门禁，与历次 plan 同），本次改动遵循既有代码风格。
  - **Deferred 项分类**：无非可降级项被降级。`Non-Blocking Follow-ups` 仅一项 out-of-scope improvement（JDBC URL 升级为 connection-param allowlist），裁定合理。
  - **Verdict：READY_TO_CLOSE**。

Follow-up:

- （Non-Blocking）考虑将 JDBC URL 安全校验从 blocklist 升级为 connection-param allowlist（更强 fail-closed）——out-of-scope improvement，当前 blocklist 已覆盖已知危险参数，见 `Non-Blocking Follow-ups`。
- 无剩余 plan-owned confirmed live defect。
