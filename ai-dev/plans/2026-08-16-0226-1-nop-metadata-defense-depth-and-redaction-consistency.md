# nop-metadata 防御纵深与脱敏一致性清扫（P2-06 / P2-07 / P2-08 / P2-04）

> Plan Status: completed
> Last Reviewed: 2026-08-16
> Mission: nop-metadata-invariant-loop
> Work Item: 2026-08-15 multi-audit Follow-up Backlog — 安全/脱敏/防御纵深族（P2 批次清扫）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md` Follow-up Backlog（安全/脱敏/防御纵深族）；审计源 `ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P2-04/P2-06/P2-07/P2-08）
> Related: 先例 `2026-08-14-1133-1`（F5-F9 安全硬化）、`2026-08-15-1913-1`（P0 SSRF + P1-8 sql 日志脱敏，其 Follow-up 显式将 P2-06/P2-08 移交 backlog）。本计划为族批次清扫（同 1133/1448 先例），非 per-item 独立 remediation plan。

## Purpose

把 2026-08-15 multi-audit follow-up backlog 中"安全/脱敏/防御纵深族"4 项一次性收口：webhook 主机形状校验补齐 F7 同款 fail-closed、custom_sql 持久化面与 AR-16 日志脱敏语义对齐、SQLException 原始消息进 error param 前过滤、KEYWORD_BLACKLIST 函数调用形态的裁定显式化并钉死测试。收口后该族不再悬挂，且不引入新的用户可见行为变更（除安全拒绝面扩大与脱敏增强——两者都是已裁定契约方向的一致性补齐）。

## Current Baseline

> 事实为 2026-08-16 live repo 实测（1913-1/-2/-3 落地后）。

- **P2-06 webhook 主机形状校验缺失**：`CheckpointActionDispatcher.validateWebhookUrl`（`quality/CheckpointActionDispatcher.java:284-307`）仅当 host 非 null 非空时才做内网校验（:300 `if (host != null && !host.isEmpty() && isInternalHost(host) ...`），否则**静默跳过**校验分支。`extractWebhookHost`（:310-350）对畸形 URL 的实际行为（2026-08-16 逐分支实证）：`https:///hook` → 返回 `"/hook"`（slash=0 使 `end=0` 走 else 取整个 rest，**非 null**）；`https://:8080/hook` → 返回 `":8080"`（首冒号处 `colon>0` 为 false 不截断，**非 null**）；未闭合 IPv6 `https://[::1/hook` → 返回 null。即畸形向量多数产出**垃圾 host 字符串**（非 null/空），穿过 null/空守卫、被 `isInternalHost` 判为非内网后放行——lucky fail-closed（垃圾 host 建连失败兜底），但与 JDBC 侧 F7 `isPlausibleHostShape` 形状校验（`MetaDataSourceConnectionProcessor`，plan `2026-08-14-1133-1` Phase 2 落地）防御纵深不对称。既有测试 `TestCheckpointActionDispatcher`（6 处 `new CheckpointActionDispatcher(null, null)` 构造）未覆盖畸形 host 形态。
- **P2-07 custom_sql 全文落库**：`MetaQualityRuleExecutor.judgeCustomSql`（`quality/MetaQualityRuleExecutor.java:325`）`j.getDetails().put("sql", sql)` 把用户 SQL 全文写入 QualityResult.details 落库。同方法 :321 已写 `sqlHash`（AR-16 修复，审计追溯用）；全文 SQL 的权威存储已在规则本体（`NopMetaQualityRule.sqlExpression` 列 / params JSON），details.sql 属重复落盘。AR-16 已裁定同数据族日志面只记 sqlHash（plan `2026-08-15-1913-1`），持久化面未同步。`rg 'get\("sql"\)' nop-metadata/` 零代码消费（仅生产点自身）；`TestMetaQualityRuleExecutorCustomSqlSandbox` 引用 sqlHash。
- **P2-08 SQLException 原始消息未过滤**：`MetaDataSourceConnectionProcessor.newNopConnectException`（`connection/MetaDataSourceConnectionProcessor.java:794-799`）`.param("error", e.getMessage())` 原样放入。驱动异常消息可回显完整 JDBC URL（userinfo 形式含口令，如 MySQL Connector/J 连接失败消息），击穿同文件 `redactJdbcUrl`（:364，public static，R6.2/F6 修复）对 jdbcUrl 参数的脱敏。同类面：`MetaQualityRuleExecutor` 内 `messageOf(e)`（:885 helper）共 6 个落消息/param 面（:335/:560/:605/:613/:690/:707）——建连点（URL 凭据回显）风险集中在 `newNopConnectException`，已建连面（SQL 文本回显）风险较低但同族。
- **P2-04 KEYWORD_BLACKLIST 函数形态跳过**：`ExpressionMeasureValidator.scanBlacklist`（`field/ExpressionMeasureValidator.java:480-500`）对 IDENTIFIER token 查 `KEYWORD_BLACKLIST`、对 FUNCTION_CALL token **只查** `FUNCTION_BLACKLIST`。KEYWORD_BLACKLIST（:63-76，26 条）中 `REPLACE`/`TRUNCATE`/`INSERT` 存在 callable 函数同形词（MySQL 字符串/数值函数：`REPLACE(str,from,to)`、`TRUNCATE(n,d)`、`INSERT(str,pos,len,newstr)`），其函数调用形态绕过关键字检查；语句形态（`REPLACE INTO`/`TRUNCATE TABLE`/`INSERT INTO`）以 IDENTIFIER token 命中。审计复核裁定：表达式上下文无 DML/DDL 逃逸路径（聚合包裹、`;`/注释已禁、参数化完整），函数形态是合法用法——**hardening 项非漏洞**。缺的是显式裁定记录 + 钉死测试（防未来"修复"破坏合法用法，或审计者重复误报）。

## Goals

- `validateWebhookUrl` 对 null/空/**非主机形状**的提取结果显式 fail-closed（沿 F7 `isPlausibleHostShape` 先例），不再静默跳过；畸形向量有对抗测试钉死。
- `judgeCustomSql` 的 QualityResult.details 不再持久化 SQL 全文（保留 sqlHash 审计追溯），与 AR-16 日志面语义对齐；测试断言 details 无原文。
- 驱动/底层异常消息进入 error param / judgment message 前经过滤（jdbc: URL 形态经 `redactJdbcUrl` 类脱敏；`MetaQualityRuleExecutor.messageOf` 统一过滤覆盖其全部调用面），凭据不因驱动回显泄漏；对抗测试用含口令 URL 的模拟驱动消息钉死。
- P2-04 裁定显式化：KEYWORD_BLACKLIST 函数形态豁免（REPLACE/TRUNCATE/INSERT 为合法函数同形词）以注释 + 钉死测试 + invariant 记录固化，其余 KEYWORD 条目无 callable 函数同形词的结论经枚举验证。

## Non-Goals

- **P2-05（元数据实体行级数据权限边界）** —— 产品级裁定项（audit 建议下轮派生优先裁决），归后续裁定计划，本计划不动 data-auth 面。
- **P2-33（审批流/状态机字段 updatable 收紧裁定）** —— 同为显式裁定需求项（与平台基线一致），归后续。
- **webhook 重定向策略 / allowed-hosts 配置面扩展** —— 已由既有 R6.2/P2-11 先行裁定覆盖，不重开。
- **custom_sql 白名单条目增删** —— F9 已收口，KEYWORD_BLACKLIST/FUNCTION_BLACKLIST 条目集不变（P2-04 只固化裁定，不改黑名单）。

## Scope

### In Scope

- `CheckpointActionDispatcher.validateWebhookUrl` + `extractWebhookHost` 形状校验（P2-06）。
- `MetaQualityRuleExecutor.judgeCustomSql` details.sql 移除 + 相关测试/owner-doc 同步（P2-07）。
- SQLException/驱动消息过滤 helper + `newNopConnectException`（及同族 judgeCustomSql message 面）接入（P2-08）。
- `ExpressionMeasureValidator` KEYWORD_BLACKLIST 函数形态裁定注释 + 钉死测试 + invariant 记录（P2-04）。

### Out Of Scope

- P2-05 / P2-33 裁定（见 Non-Goals）。
- 其余 2026-08-15 multi-audit P2 族（错误处理族 → 计划 2；BizModel 行为族 → 计划 3；ORM/IoC/文档测试卫生族 → 后续轮次）。
- 任何 `_gen/` 生成产物手工编辑。

## Execution Plan

### Phase 1 — webhook 主机形状校验 fail-closed（P2-06）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/CheckpointActionDispatcher.java`（:284-350）；测试 `TestCheckpointActionDispatcher`

- Item Types: `Fix | Proof`

> 沿 JDBC 侧 F7 先例（`isPlausibleHostShape` enforced fail-closed，plan `2026-08-14-1133-1` Phase 2）：提取结果为 null/空串/**非主机形状**（`/` 前缀垃圾串、`:8080` 端口无 host 形态等——见 Current Baseline 实证行为）时显式抛 `ERR_CHECKPOINT_WEBHOOK_URL_BLOCKED`（reason 注明 implausible host shape），不再依赖"垃圾 host 建连失败"的 lucky fail-closed。实现方式（dispatcher 内私有形状判定 or 复用 HostSecurityUtil 侧共享）由执行者裁定，核心要求：null/空/非形状提取结果不再静默跳过校验分支。

- [x] `validateWebhookUrl`：`extractWebhookHost` 返回 null/空串/非主机形状时显式抛 `ERR_CHECKPOINT_WEBHOOK_URL_BLOCKED`（带 checkpointId/url/reason 参数，与既有 throw 形态一致）
- [x] 形状判定覆盖：`https:///hook`（→提取出 `/hook`）、`https://:8080/hook`（→提取出 `:8080`）、未闭合 IPv6 `https://[::1/hook`（→null）、空串等全部畸形形态
- [x] 既有合法向量零误伤：正常域名、IPv4、`[::1]` IPv6、带 userinfo、带端口、allowed-hosts 白名单命中路径全部保持原判定结果
- [x] 对抗测试：上述畸形向量逐个断言抛出且 reason 可识别；合法向量逐个断言通过（含 allowed-hosts 内网放行）

Exit Criteria:

- [x] 畸形 host 向量全部显式拒绝（测试断言错误码 + reason），无静默跳过分支
- [x] 合法向量行为零变化（既有 `TestCheckpointActionDispatcher` 全绿 + 新增零误伤断言）
- [x] **无静默跳过**：新校验分支不吞异常、不返回默认放行
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -Dtest='TestCheckpointActionDispatcher*' -Dsurefire.failIfNoSpecifiedTests=false` 通过（含 WebhookSsrf 主防线套件与 Locale 套件——合法向量零误伤的真正回归面）
- [x] owner doc 更新：`docs-for-ai/03-modules/nop-metadata.md` webhook/SSRF 契约段补形状校验 fail-closed 表述（如该段已有 F7 表述则对齐引用）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 驱动异常消息脱敏过滤（P2-08）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java`（:794-799）；`MetaQualityRuleExecutor.java`（judgeCustomSql ERROR message 面）；新增/复用脱敏 helper

- Item Types: `Fix | Proof`

> 过滤语义：消息中出现的 `jdbc:` URL 形态子串（匹配 `jdbc:\S+` 后**剥除尾部标点** `.,;!?)]}'"` 再脱敏，避免吞句号）经 `redactJdbcUrl`（同文件 :364，public static）替换后回填；无命中则原样返回（不丢诊断信息）。**首选落点**：过滤逻辑收敛进 `MetaQualityRuleExecutor.messageOf`（:885 helper）内——单点覆盖该文件全部 6 个 messageOf 面（:335/:560/:605/:613/:690/:707）；`newNopConnectException`（建连点，URL 凭据回显风险最高）单独接入同一过滤。Phase 内枚举其余裸 `e.getMessage()` 进 param/message 的面（`rg -n 'getMessage\(\)' nop-metadata/.../main`）并逐个裁定接入或排除（排除理由：消息源不可能含 URL/SQL 回显）。注意 `.param` 键沿该处既有形态（"error"），不改键名（P2-11 双轨裁定在计划 2）。

- [x] 新增消息过滤 helper：`jdbc:\S+` 匹配 + 尾部标点剥离 + `redactJdbcUrl` 脱敏回填；无命中原样返回
- [x] 过滤收敛进 `MetaQualityRuleExecutor.messageOf`（覆盖其全部调用面）+ `newNopConnectException` 的 `.param("error", ...)` 接入
- [x] 枚举其余裸 `getMessage()` 进 param/message 面，逐个裁定（接入 or 排除+理由），清单写入 daily log
- [x] 对抗测试：模拟驱动消息 `Failed to connect to jdbc:mysql://root:secret@10.0.0.1/db`（userinfo 口令形态）→ 断言 param/message 不含 `secret` 与原始 URL、且保留脱敏后形态与异常类名可诊断性
- [x] 零误伤测试：不含 jdbc: 形态的普通驱动消息原样保留
- [x] 已知边界记录：`redactJdbcUrl` 只剥 authority userinfo——query 形态口令（`?user=x&password=y`）与无 `://` 的 Oracle thin 形态不在脱敏范围（F6 已裁定的既有语义边界）；本 Phase 枚举裁定清单中显式记录该边界，防第三轮审计复发

Exit Criteria:

- [x] 两个接入点均过滤；含口令 URL 的模拟消息脱敏后断言通过（grep 不到口令原文）
- [x] 无 URL 消息零改写（诊断信息不丢）
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -Dtest='TestMetaDataSourceConnection*,TestMetaQualityRuleExecutor*' -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] **无静默跳过**：过滤失败/异常不吞（helper 对 null 消息返回类名/空串语义，沿用 :797-798 既有 null 处理）
- [x] owner doc：错误处理/脱敏段（AR-16 同段）补一句"驱动消息进 error param 前经 URL 脱敏"
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — custom_sql 持久化面与 AR-16 对齐（P2-07）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java`（:325）；测试 `TestMetaQualityRuleExecutorCustomSqlSandbox` 等

- Item Types: `Fix | Proof`

> 权威源论证：SQL 全文已持久化于规则本体（`NopMetaQualityRule.sqlExpression` 列优先 / params.sql JSON），QualityResult.details 的 `sql` 字段是每结果行重复落盘；sqlHash（:324）已提供规则侧追溯。删除 details.sql 不损失可追溯性。

- [x] 删除 `j.getDetails().put("sql", sql)`（:325）；保留 `sqlHash`
- [x] 全量核对测试消费面：`rg -n '"sql"' nop-metadata/nop-metadata-service/src/test` 逐个核对，断言 details.sql 的测试改为断言 sqlHash 存在 + details 不含 SQL 原文
- [x] 新增/改造断言：custom_sql 判定（PASS/FAIL/ERROR 各路径）details 均不含 SQL 全文
- [x] 核对 QualityResult.details 的 UI/查询消费面（xmeta/页面/GraphQL 查询无 details.sql 专属消费——`rg 'details' nop-metadata/nop-metadata-meta` 复核）

Exit Criteria:

- [x] `rg -n 'put\("sql"' nop-metadata/nop-metadata-service/src/main` 零命中
- [x] custom_sql 相关测试全绿且新增"details 无原文"断言通过
- [x] **端到端验证**：既有 custom_sql 判定集成路径（executor → judgment → details/sqlHash）测试完整跑通，sqlHash 可与规则 sqlExpression 对账
- [x] owner doc：custom_sql 段（F9/AR-16 同节）补"持久化面只落 sqlHash，全文在规则本体"表述
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — KEYWORD_BLACKLIST 函数形态裁定显式化（P2-04）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/field/ExpressionMeasureValidator.java`（:63-100 注释区）；测试 `TestExpressionMeasureValidator`；invariant 记录

- Item Types: `Decision | Proof`

> 裁定内容（审计复核结论的显式化）：FUNCTION_CALL token 只查 FUNCTION_BLACKLIST 是**有意设计**——KEYWORD_BLACKLIST 中 `REPLACE`/`TRUNCATE`/`INSERT` 存在合法函数同形词（字符串替换/数值截断/MySQL INSERT(str,pos,len,newstr) 字符串函数），表达式上下文无 DML/DDL 逃逸路径（聚合包裹、`;`/注释已禁、参数化完整），语句形态（`REPLACE INTO`/`TRUNCATE TABLE`/`INSERT INTO`）以 IDENTIFIER token 命中 KEYWORD_BLACKLIST。不改黑名单、不改扫描逻辑。

- [x] 枚举验证：逐条核对 KEYWORD_BLACKLIST 全部 26 条目，确认 callable 函数同形词仅 REPLACE/TRUNCATE/INSERT（其余条目无函数语义）；结论写入注释（若枚举发现更多同形词，按同一豁免逻辑并入裁定并记录）
- [x] `scanBlacklist`/KEYWORD_BLACKLIST 注释区补裁定说明（函数形态豁免理由 + 语句形态已覆盖 + 引用本裁定）
- [x] 钉死测试：`REPLACE(name,'a','b')` / `TRUNCATE(1.23,1)` / `INSERT(name,1,2,'x')` 函数形态**通过**；`REPLACE INTO t ...` / `TRUNCATE TABLE t` / `INSERT INTO t ...` 语句形态**拒绝**；`SLEEP(1)` 等 FUNCTION_BLACKLIST 命中**拒绝**
- [x] invariant 记录：`ai-dev/audits/nop-metadata-invariants/invariant-catalog.md` 相应条目（或 AR-16/F9 同族记录处）补 P2-04 裁定一行

Exit Criteria:

- [x] 注释含完整裁定理由；枚举结论（仅 REPLACE/TRUNCATE/INSERT 有函数同形词，或枚举修正后的全集）有核对证据（清单写入 daily log）
- [x] 钉死测试 9 类向量（3 函数过、3 语句拒、FUNCTION_BLACKLIST 拒 3——如 SLEEP/BENCHMARK/LOAD_FILE）全绿
- [x] **接线验证**：钉死测试直接调用 `ExpressionMeasureValidator` 校验入口（与生产聚合路径同一入口），非测内部私有方法
- [x] No owner-doc update required beyond invariant 记录（裁定属审计层记录；`docs-for-ai` F9 段已有 DRY 裁定背景，不重复）——执行时复核，若 owner doc F9 段表述与本裁定冲突则同步一句
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划为安全/脱敏一致性清扫（3 Fix + 1 Decision/Proof），含用户可见的安全拒绝面扩大（P2-06）与脱敏增强（P2-07/P2-08）——均为已裁定契约方向（F7/AR-16）的一致性补齐。

- [x] P2-06/P2-07/P2-08 三项 live 修复落地且对抗测试钉死；P2-04 裁定显式化并钉死
- [x] `./mvnw compile -pl nop-metadata -am -T 1C` 通过
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）
- [x] `node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata` 0 新增命中（pre-existing 除外，git diff 范围核对）
- [x] `node ai-dev/tools/check-error-param-consistency.mjs --module nop-metadata` exit 0（P2-06 新增 throw 点参数齐全）
- [x] `node ai-dev/tools/check-sensitive-literal-leak.mjs --module nop-metadata` exit 0（脱敏改动不引入新泄漏面）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：改动前后各跑一次，error 数不增（pre-existing 基线计数与 0 新增结论写入 daily log——`--strict` 对 pre-existing error 会 exit 1，以计数 diff 为判据）
- [x] 不存在被静默降级到 deferred 的 in-scope 项
- [x] 受影响 owner docs（nop-metadata.md 脱敏/SSRF 段）已同步
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）畸形 host 确实被新分支拒绝（跑对抗测试非读代码）；（b）details.sql 删除后 custom_sql 判定链完整；（c）消息过滤 helper 真实接入两接入点（非仅存在）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] roadmap Follow-up Backlog 对应条目标注处置结果

## Deferred But Adjudicated

（本计划无 deferred 项。P2-05/P2-33 为 Non-Goals 显式移出，非本计划 in-scope 降级。）

## Non-Blocking Follow-ups

- P2-05（行级数据权限边界）/ P2-33（状态机字段 updatable 收紧）—— 产品级裁定项，留待后续裁定轮（audit 建议"下轮派生时优先裁决"）。
- ORM/IoC/文档测试卫生族 P2 项 —— 留待后续批次清扫计划。

## Closure

Status Note: 4 Phase 全部落地且经独立 closure audit（fresh session，review-only）逐项 live 核对判定 READY_TO_CLOSE：3 Fix（P2-06 webhook 形状校验 fail-closed / P2-07 details.sql 移除 / P2-08 驱动消息脱敏三接入点）+ 1 Decision/Proof（P2-04 函数形态裁定显式化）均为已裁定契约方向（F7/AR-16）的一致性补齐，无 in-scope 项降级 deferred，无 scope creep。P2-05/P2-33 为 Non-Goals 显式移出（产品级裁定项，留待后续裁定轮）。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session `ses_ff91dda05ffeGW5weOZyHjAXTJ`，review-only 零文件修改）
- Evidence:
  - Phase 1-4 Exit Criteria 全部 PASS（live 核对）：P2-06 throw 位于 isInternalHost 之前、无静默分支、参数齐全（CheckpointActionDispatcher.java:305-310）；P2-08 三接入点接线实证（redactJdbcUrlsInText :419 → newNopConnectException :860 / parseConnectionConfig :813 抑制+cause :848-852 / MetaQualityRuleExecutor.messageOf :900 覆盖 :338/:563/:608/:616/:693/:710 六面）；P2-07 `rg 'put\("sql"'` main 零命中 + sqlHash 保留（:322）；P2-04 注释-only diff + 黑名单 26 条目逐条计数核对（5+5+7+3+3+3）、条目集零改动。
  - Closure Gates 全 PASS：`./mvnw compile -pl nop-metadata -am -T 1C` exit 0 + `./mvnw test -pl nop-metadata -am -T 1C` BUILD SUCCESS（service 1248/0/0，+21 新增测试）；check-silent-swallow 0/126 命中、check-error-param-consistency exit 0（豁免面 6 条全部既有）、check-sensitive-literal-leak exit 0、scan-hollow --severity high exit 0；check-doc-links --strict 17 errors = 既有基线（本计划改动文件 0 新增，nop-metadata.md 唯一命中为 :253 既有 BOUNDARY，先于本计划 hunk @@ -262）。
  - Anti-Hollow 检查（audit 实跑非读码）：(a) 变异验证——禁用形状校验 throw 后 `testMalformedHostShapeFailClosed` 变红（1 Failure/BUILD FAILURE），恢复后复绿，文件 shasum 零残留；(b) custom_sql 判定链（PASS/FAIL/ERROR + sqlExpression 优先/params.sql 回退对账）5/5 绿 + checkpoint/rule/result BizModel 集成链全绿；(c) 过滤 helper 接线经调用链 grep 实证（非仅存在）。审计附加回归：`TestCheckpointActionDispatcher*,TestMetaQualityRuleExecutor*,TestMetaDataSourceConnection*` 155/155。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（全 checklist 勾选 + 本 Evidence 写入）。
  - Deferred 项分类检查：无 in-scope defect 降级（P2-05/P2-33 在 Non-Goals/Non-Blocking Follow-ups 均附裁定理由，Non-Decision 归属明确）。

Follow-up:

- P2-05（行级数据权限边界）/ P2-33（状态机字段 updatable 收紧）—— 产品级裁定项，留待后续裁定轮（Non-Goals 显式移出，非本计划 in-scope 降级）。
- 其余 2026-08-15 multi-audit P2 族（错误处理族 → 计划 2026-08-16-0226-2；BizModel 行为族 → 计划 2026-08-16-0226-3；ORM/IoC/文档测试卫生族 → 后续轮次）。
- no remaining plan-owned work（本计划 in-scope 4 项全部收口）。
