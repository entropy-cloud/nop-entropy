# R6-2 webhook 重定向跟随显式禁用 + rawJdbcUrl 明文凭据脱敏（P2-11/P2-12）

> Plan Status: active
> Last Reviewed: 2026-08-05
> Draft Review: R1 `ses_02d97428cffetkoueYxPWr41Ri`（1 Blocker：路径 B 与 Goal 1/Closure Gate 1 矛盾——已裁定路径 A 为唯一合格路径，路径 B 降为 3xx 显式拒绝互补面；1 Major：路径 A 落点不可行选项 + 门禁触发点/null 语义/构造点影响面未定——已预裁定 @InjectValue 注入 + per-dispatchWebhook 门禁 + null 默认放行 + 8 处构造点影响面入 Baseline；3 Minor 已修）；R2 `ses_02d8beadaffeneooEC8yhR05oU`（1 Major：Goal 1 残留"门禁或 3xx"表述与 Decision 矛盾——已改为门禁[唯一合格路径] + 3xx[互补面]；4 Minor 已修：Closure Gate 1 条件化、接线验证范围收窄 + @InjectValue seam 代码审查确认、Deferred 分类 resolved、configureRedirectPolicy 新增 setter 零影响 + 4 处调用点影响声明）；R3 `ses_02d83d242ffeCdlf9AX0UHasxb`（结论：**可以直接执行**，0 Blocker / 0 Major；3 条 Minor 备忘已吸收——setter 单一路径优先、错误码复用 NON_2XX 时 reason 裸参数断言口径、IHttpClient 4+2 方法表述）。consensus 达成。
> Mission: nop-metadata-audit-remediation
> Work Item: MR6（R6.2）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR6 段 R6.2 行 + Follow-up Backlog P2-11/P2-12）、`ai-dev/audits/arm-index-nop-metadata.md`（§P2 MR6 裁决记录）
> Related: 执行顺序 `{2}` of 3 — 与 R6.1（`2026-08-05-2157-1`）、R6.3（`2026-08-05-2157-3`）文件域不重叠（R6.2：quality/dispatch + connection），可独立执行；Deps 门禁（R6.0 done）已解除

## Purpose

按 MR6 R6.2 行收口两项 Backlog finding（2026-08-05 两轮审计登记，R6.0 live 复核提级）：

1. **P2-11**：webhook 请求未显式关闭重定向跟随——HttpRequest 无 per-request 字段，跟随与否完全由全局 `HttpClientConfig.followRedirects` 决定（默认 false），一旦部署全局开启即 302 跳转目标不复核（经典重定向 SSRF 绕过），防御纵深缺口。
2. **P2-12**：`MetaDataSourceConnectionProcessor` 三处错误路径把原始 jdbcUrl（可含 `user:pass@` 明文凭据）作为错误参数保留——NopException params 随异常序列化进日志/错误响应，凭据落盘面真实（`TestMetaDataSourceConnectionSecurity.java:341-343` 明确断言含完整凭据 URL）。

目标状态：webhook 投递 fail-closed 不依赖全局默认值；错误参数仅保留脱敏 URL；回归测试钉死两侧行为。

## Current Baseline

2026-08-05 live repo 核对（R6.0 裁决记录 + 本次复核）：

- **P2-11（confirmed，防御纵深缺口）**：`CheckpointActionDispatcher.dispatchWebhook`（`quality/CheckpointActionDispatcher.java:207-215`）构建 `HttpRequest` 仅设 url/method/header/body/timeout，**未设置任何重定向策略**。平台 `HttpRequest`（`nop-http-api`）**无 per-request 重定向字段**（已核实字段集：url/method/headers/params/body/timeout/dataType/attrs，无 followRedirects）；跟随与否完全由全局 `HttpClientConfig.followRedirects`（`HttpClientConfig.java:33`，`boolean` 默认 false）在客户端构造时决定——`JdkHttpClient.java:92`（`Redirect.NEVER`）、`OkHttpClientProvider.java:111`（`false`）均读该配置。即：当前默认安全，但**部署一旦开启 `nop.http.client.follow-redirects=true`，302 将被自动跟随且跳转目标不经 `validateWebhookUrl` 复核**（SSRF 回归缺口）。HttpRequest.attrs 可作为扩展属性携带，但 JdkHttpClient/OkHttp 均不消费 attrs 控制重定向（已核实）。**配置注入面（已核实可行）**：`NopMetaQualityCheckpointBizModel` 既有 `@InjectValue(value = "@cfg:nop.metadata.checkpoint.webhook-allowed-hosts|")` 模式（:106-108），可沿同一模式读取 `nop.http.client.follow-redirects`；dispatcher 有 `configureWebhookSsrf` 注入点（:92-96）可扩展携带该配置。**影响面**：`TestCheckpointActionDispatcher`（6 处）与 `TestCheckpointActionDispatcherConcurrency`（2 处）以 `new CheckpointActionDispatcher(null, null)` 构造（dispatcher 构造不读配置，门禁若放构造期将破坏这些测试——Phase 1 已裁定门禁放 per-dispatchWebhook，构造期不受影响）。
- **P2-12（confirmed，凭据泄露面）**：`MetaDataSourceConnectionProcessor.validateJdbcUrl`（`connection/MetaDataSourceConnectionProcessor.java:225-247`）三处错误路径（协议白名单 :225-228 / 危险参数黑名单 :233-236 / 主机白名单 :243-246）均为 `.param("jdbcUrl", redactJdbcUrl(jdbcUrl))`（脱敏）+ `.param(NopMetadataErrors.ARG_RAW_JDBC_URL, jdbcUrl)`（**原始 URL 并存**）双参数；`NopMetadataArgs.ARG_RAW_JDBC_URL = "rawJdbcUrl"`（`NopMetadataArgs.java:52`）。`TestMetaDataSourceConnectionSecurity.java:341-343`（`testErrorResponseContainsRedactedUrl`）**显式断言 rawJdbcUrl 参数含完整凭据 URL**——该断言与脱敏目标冲突，Phase 2 必须改写。
- **既有回归测试**：`TestCheckpointActionDispatcherWebhookSsrf.java`（19 个 @Test，MR5 后实际计数，MA7.6-04/MA7.2-01/R5.1 修复族）；`TestMetaDataSourceConnectionSecurity.java`（25 个 @Test，含凭据脱敏族 :300-347）。
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` → nop-metadata 子树 **895 tests / 0 failures / 0 errors / 0 skipped**（service 894 + web 1，R6.0 收口口径）。

## Goals

- webhook 投递的重定向行为**显式 fail-closed**：不依赖 `HttpClientConfig` 默认值，部署开启全局跟随也不产生未复核跳转（**显式配置门禁为唯一合格路径** + 响应 3xx 显式拒绝为互补面，见 Phase 1 Decision）
- 错误路径不再携带 rawJdbcUrl 明文凭据参数（仅保留脱敏 `jdbcUrl` 参数），`ARG_RAW_JDBC_URL` 常量按使用面处置（删除或保留为文档化占位，见 Phase 2 Decision）
- 双侧回归测试钉死：webhook 3xx 显式拒绝 + 错误参数不含凭据
- arm-index §P2 对应行终态（fixed）+ roadmap R6.2 行 → done

## Non-Goals

- 不修改 `nop-http-api`/`nop-http-client-jdk`/`nop-http-client-okhttp` 平台代码（跨模块公共 API Protected Area 不在 scope；HttpRequest 字段扩展属平台级变更，另立计划）
- 不改变 webhook URL 主机白名单 / userinfo 剥离 / method 白名单 / timeout 机制（R5.1 + MA7.6-04 已收口，仅重定向跟随策略在 scope 内）
- 不处理 R6.1（custom_sql 黑名单）、R6.3（upsert/守卫）文件域
- 不引入全局配置变更建议（`nop.http.client.follow-redirects` 全局行为由宿主部署决定，本 plan 只保证 webhook 路径不受其影响）

## Scope

### In Scope

- `CheckpointActionDispatcher` webhook 重定向跟随显式禁用（Decision + Fix）
- `MetaDataSourceConnectionProcessor` 三处错误路径 rawJdbcUrl 参数移除（Fix）
- `TestCheckpointActionDispatcherWebhookSsrf` + `TestMetaDataSourceConnectionSecurity` 对应回归测试（Fix）
- arm-index §P2 + roadmap R6.2 行终态更新（Fix）
- `ai-dev/logs/2026/08-05.md`（或执行当日日志）更新（Follow-up）

### Out Of Scope

- P2-10/P2-13（R6.1）、AR-07/AR-08（R6.3）、P2-06/07/09（R6.4）、P2-01/02/04（R6.5）、R6.6 批量
- 平台 HttpRequest 增加 per-request 重定向字段（跨模块公共 API 变更，需独立 plan）
- webhook 通知通道（IMessageService）行为

## Execution Plan

### Phase 1 - webhook 重定向跟随显式禁用 + 回归测试

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/CheckpointActionDispatcher.java` + `TestCheckpointActionDispatcherWebhookSsrf.java`

- Item Types: `Decision | Fix | Proof`

- [ ] **重定向处置裁定（Decision）**：**路径 A 为唯一合格路径（fail-closed 门禁）**——路径 B（仅 3xx 显式拒绝）不满足本 plan Goal 1 / Closure Gate 1（"部署开启全局跟随也不产生未复核跳转"），且与 roadmap R6.2 行"webhook 重定向跟随显式禁用（fail-closed，不依赖 HttpClientConfig 默认）"的语义不符，**不作为独立选项**。路径 A 落点裁定（本次审查已预裁定，执行时仅验证可行性）：
  - 配置注入：沿 `NopMetaQualityCheckpointBizModel` 既有 `@InjectValue("@cfg:...")` 模式（:106-108 实测 :112-113）读取 `nop.http.client.follow-redirects`（默认 false），经 `configureWebhookSsrf` 扩展点（`CheckpointActionDispatcher.java:92-96`）或**优先新增 `configureRedirectPolicy(boolean)` setter**（新增 setter 对既有 4 处 `configureWebhookSsrf(url, timeout)` 调用点零影响；若改扩展现有方法签名，须同步 WebhookSsrf 测试文件 4 处 2 参调用点 :149/:236/:287/:296）注入 dispatcher——**不选**"按注入 client 实例行为验证"（IHttpClient 接口仅 fetch/fetchAsync/downloadAsync/uploadAsync 4 方法，无重定向策略访问器，测试无法经接口观察，属空壳验证，禁用）
  - 门禁触发点：**per-dispatchWebhook**（`dispatchWebhook` 内、fetch 前）——配置开启跟随（followRedirects=true）时显式抛错（`ERR_CHECKPOINT_WEBHOOK_REDIRECT_NOT_ALLOWED` 新错误码或复用 `ERR_CHECKPOINT_WEBHOOK_NON_2XX` + reason=redirect，执行时按 QualityErrors 家族裁定），错误进入 summary.errors（经既有 per-action try/catch，不影响同检查点其他动作）——**不选**构造期门禁（会破坏 `new CheckpointActionDispatcher(null, null)` 的 8 处既有测试构造点，且使无 webhook 的检查点整体不可用）
  - null/缺省配置语义：`@cfg:...|false` 缺省 = false（默认安全放行），与平台默认一致；显式 true = fail-closed 拒绝
  - **3xx 响应显式拒绝（与门禁互补，非替代）**：`dispatchWebhook` 在 `fetch` 后对 3xx（300-399）显式归类为投递失败（抛 `ERR_CHECKPOINT_WEBHOOK_NON_2XX` 或新错误码，reason 标记 redirect）——门禁覆盖"全局开启跟随"面，3xx 拒绝覆盖"客户端不跟随时直返 3xx"面（默认配置下既有的非 2xx 分支已拒绝，本次将其显式化+可断言）
  - 裁定约束：不允许"维持现状（依赖默认值且无任何显式标记）"的模糊态；裁定结果写入本 plan + arm-index §P2
- [ ] 按裁定落地（Fix）：`NopMetaQualityCheckpointBizModel` 配置读取 + `CheckpointActionDispatcher` 门禁/3xx 拒绝分支（门禁 per-dispatchWebhook，3xx 显式分支 :221-227 前置）
- [ ] **回归测试（Fix）**：`TestCheckpointActionDispatcherWebhookSsrf` 补——(a) MockHttpClient 返回 302 → 断言投递失败（错误码 + reason 含 redirect，**不依赖 Location 头**——MockHttpResponse 硬编码 headers，dispatcher 只读 status :221）；(b) 门禁配置 followRedirects=true → 断言 dispatchWebhook 显式失败（错误码），配置 false/缺省 → 正常投递；(c) 既有 19 个用例不回归（MR5 后实际计数 19，非 16；尤其 R5.1 的 SSRF 向量）
- [ ] **接线验证**：测试经真实 `dispatchWebhook` 分派路径（非直接构造错误码）；门禁行为经 `dispatch` → `dispatchWebhook` 断言（覆盖 dispatcher 读取配置 → 拒绝的链路；**BizModel 侧 `@InjectValue` 接线由代码审查确认**——单元测试经 setter 注入无法覆盖 IoC 注入 seam，接线验证声明不夸大）；确认 `TestCheckpointActionDispatcher`/`TestCheckpointActionDispatcherConcurrency` 的 8 处 `new CheckpointActionDispatcher(null, null)` 构造点不受影响（构造期无门禁，且优先新增 setter 方案不动构造签名）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**：webhook 3xx 响应从 `dispatch` 入口 → `fetch` 返回 → 显式拒绝的完整路径成立（测试断言错误码 + reason）
- [ ] **无静默跳过**：3xx 不再落入"非 2xx 才报错"的隐含分支（显式 3xx 分支存在）；门禁配置开启时显式失败而非静默跟随；无 catch-and-continue 引入
- [ ] 既有 19 个 webhook SSRF 用例不回归 + `TestCheckpointActionDispatcher`（6 处）/`TestCheckpointActionDispatcherConcurrency`（2 处）`new CheckpointActionDispatcher(null, null)` 构造点不受影响；`./mvnw test -pl nop-metadata -T 1C` 相关测试类全绿
- [ ] 裁定记录（路径 A 唯一合格路径 + 落点预裁定）已写入本 plan + arm-index §P2
- [ ] `No owner-doc update required`（docs-for-ai 无 webhook 重定向细节章节；若新增错误码，按模块惯例登记于 QualityErrors）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - rawJdbcUrl 明文凭据参数移除 + 回归测试

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java` + `TestMetaDataSourceConnectionSecurity.java`

- Item Types: `Decision | Fix | Proof`

- [ ] **ARG_RAW_JDBC_URL 处置裁定（Decision）**：移除三处 `.param(ARG_RAW_JDBC_URL, jdbcUrl)`（:227/:235/:245）后，`NopMetadataArgs.ARG_RAW_JDBC_URL` 常量（:52）处置二选一：删除（全仓 grep 确认无其他消费方后，测试 :341-343 同步改写）；或保留为文档化"禁止使用"占位（若外部 API 消费者依赖该参数名，需先 grep 消费方）。裁定记录写入本 plan
- [ ] 移除三处 rawJdbcUrl 参数（Fix）：仅保留 `.param("jdbcUrl", redactJdbcUrl(jdbcUrl))` 脱敏参数（:226/:234/:244）
- [ ] **回归测试改写（Fix）**：`TestMetaDataSourceConnectionSecurity.testErrorResponseContainsRedactedUrl`（:332-347）改写——断言错误响应**不含** `rawJdbcUrl` 参数（或断言参数中不含 `admin:secret` 凭据），并保留脱敏 URL 断言（:338-340 不回归）；若删除常量，同步移除 :341-343 的 rawJdbcUrl 断言
- [ ] 补强：三处错误路径（协议/参数/主机）各保留一条"错误响应不含明文凭据"断言（若既有测试已覆盖协议路径，参数/主机路径按既有模式补或声明等价覆盖）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `grep -rn "ARG_RAW_JDBC_URL"` 无残留使用（或仅保留文档化占位声明）；`grep "rawJdbcUrl"` 测试断言与实现一致
- [ ] **端到端验证**：`testConnect` 错误路径产出的 NopException params 不含明文凭据（`jdbc:oracle:thin://admin:secret@...` 场景断言）
- [ ] **无静默跳过**：无 catch 吞异常引入；移除参数不改变错误码/错误语义（仅参数面）
- [ ] 既有 25 个连接安全用例不回归（MR5 后实际计数 25，非 22；`testErrorResponseContainsRedactedUrl` 按新语义改写，其余不回归）
- [ ] `./mvnw test -pl nop-metadata -T 1C` 相关测试类全绿
- [ ] `No owner-doc update required`（docs-for-ai 无错误参数细节章节；凭据脱敏语义与既有 javadoc 一致）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 收口（arm-index 终态 + closure audit）

Status: planned
Targets: `ai-dev/audits/arm-index-nop-metadata.md` + `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`

- Item Types: `Fix | Proof`

- [ ] arm-index §P2 对应行（P2-11/P2-12）终态 = fixed + 本 plan 引用 + 修复摘要 + 测试证据
- [ ] roadmap MR6 R6.2 行 → done（注明 plan 引用 + 测试计数）
- [ ] 独立子 agent closure audit（fresh session）逐项核对 Phase Exit Criteria + Closure Gates，证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（涉及 arm-index/roadmap 变更后）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] arm-index + roadmap 终态一致可追溯（P2-11/P2-12 两行 fixed）
- [ ] 独立 closure audit PASS，evidence 已写入本 plan Closure 段
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）
- [ ] 无静默降级：两项安全 finding 为 fixed，无 live defect 被降级
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] P2-11：webhook 重定向跟随显式禁用（**显式配置门禁[唯一合格路径] + 3xx 显式拒绝[互补面]**），不依赖 HttpClientConfig 默认值
- [ ] P2-12：错误路径不再携带 rawJdbcUrl 明文凭据，错误响应/日志无凭据落盘面
- [ ] 必要 focused verification 已完成（两测试文件全绿 + 既有用例不回归）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）3xx 拒绝分支在运行时确实被 `dispatchWebhook` 调用（非仅存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw test -pl nop-metadata -am -T 1C`
- [ ] checkstyle / 代码规范检查通过（nop-metadata 无独立 checkstyle 命令，以 mvn 构建默认检查为准；历史惯例 "checkstyle N/A"）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（closure 时）

## Deferred But Adjudicated

### 平台级 per-request 重定向字段（HttpRequest 扩展）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 路径 A（per-dispatchWebhook 配置门禁 + 3xx 显式拒绝）已把 webhook 路径 fail-closed——门禁覆盖"全局开启跟随"面、3xx 拒绝覆盖"客户端不跟随"面，不依赖平台字段；平台 HttpRequest per-request 重定向字段属跨模块公共 API 变更（Protected Area），非本 plan scope
- Successor Required: `no`
- Successor Path: —

### ARG_RAW_JDBC_URL 常量

- Classification: `out-of-scope improvement`（若保留为占位）/ `resolved`（若删除，无残留）
- Why Not Blocking Closure: 常量本身不携带数据，风险全在参数使用点（本 plan 已移除）；删除后全仓无残留、保留为占位亦不影响行为
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- webhook 通知通道（IMessageService）若存在类似凭据/重定向面，后续审计复核（当前无 finding）
- 工作树提交由 mission 流程/用户决定（本 plan 执行不代提交）

## Closure

Status Note: 待执行完成后填写（为什么这个 plan 可以关闭）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent，fresh session>>
- Evidence: <<逐条 Exit Criterion / Closure Gate 验证结果 + 工具退出码 + Anti-Hollow 调用链追踪>>

Follow-up:

- <<执行完成后填写；或明确写 no remaining plan-owned work>>
