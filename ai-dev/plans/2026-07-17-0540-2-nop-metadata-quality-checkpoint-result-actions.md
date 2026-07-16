# 0540-2 nop-metadata 质量检查点结果动作（notify / webhook）

> Plan Status: active
> Mission: nop-metadata
> Work Item: Quality Checkpoint Result Actions（notify / webhook）
> Last Reviewed: 2026-07-17
> Draft Review: 两轮独立对抗性审查（ses_0931cb4f5ffeRnGbz7kMpt86XK → ses_09312d707ffeQ7RQYIWO9CiDOa）。首轮 REJECT（3 Blocker：pom 依赖/IoC 注入路径/事务边界）；次轮 APPROVE-WITH-MINORS（无 Blocker，已修正 N1 事务隔离=真 post-commit via onAfterCommit、N2 IHttpClient null 对称处理）。共识达成。
> Source: deferred item from `2026-07-17-0027-1`（notify/webhook/update_docs 动作），`Successor Required: yes`；设计意图来源 `06-data-quality-extended.md` §4.3 执行动作表
> Related: `2026-07-17-0027-1-nop-metadata-quality-checkpoint-orchestration.md`、`2026-07-17-0540-1-nop-metadata-quality-checkpoint-auto-scoring.md`

## Purpose

把质量检查点（`NopMetaQualityCheckpoint`）的执行结果动作从「仅 store」收口到「可向外部投递」：实现 `webhook`（HTTP POST 执行摘要）与 `notify`（消息通道投递）两类动作，替换当前对非 store 动作的显式拒绝（`ERR_CHECKPOINT_ACTION_NOT_SUPPORTED`）。本 plan 关闭 0027-1 中 notify/webhook 的 deferred 项（update_docs 仍 open，见 Deferred）。

> **draft review 收口**：经独立对抗性审查（task ses_0931cb4f5ffeRnGbz7kMpt86XK）识别 3 个 Blocker（pom 依赖缺失 / IoC 注入路径与现有手工 `new` executor 模式冲突 / 事务边界）与若干 Major，本 draft 已逐条修正（见 Phase Decision 与 Scope）。

## Current Baseline

已落地（live repo 核实）：

- **手动检查点执行**：`NopMetaQualityCheckpointBizModel.executeCheckpoint`（`@BizMutation`）→ `MetaQualityCheckpointExecutor.execute()`。执行循环逐条 `resultWriter.append` + `orm.flushSession()` 落盘 `NopMetaQualityResult`，返回摘要 map（`:156-164`，不含 skipCount）。该 `@BizMutation` 经 nop-biz `TransactionActionDecorator` 包在 `runInTransaction(REQUIRED)` 内（事务边界事实）。
- **动作当前仅 store 生效**：`MetaQualityCheckpointExecutor.validateActionsOrThrow()`（`:290-320`）解析 `NopMetaQualityCheckpoint.actions`（JSON 列），任何 `actionType != "store"` 且 `enabled=true` → 显式失败抛 inline ErrorCode `metadata.checkpoint-action-not-supported`（D4）。store 为隐式默认。
- **executor 不是 IoC bean**：在 `NopMetaQualityCheckpointBizModel.ensureCheckpointExecutor()`（`:100-106`）手工 `new`，注入六个依赖（ruleExecutor/tableRefResolver/tableRefExecutor/resultWriter/daoProvider/orm）。`_service.beans.xml` 只装 BizModel 注册，不装 service-layer bean。
- `NopMetaQualityCheckpoint.actions` JSON 列已存在（`nop-metadata/model/nop-metadata.orm.xml:1602-1604`，`domain="json-4000"`）。
- actionType 常量 `_NopMetadataCoreConstants.CHECKPOINT_ACTION_TYPE_STORE = "store"`（`.../core/_NopMetadataCoreConstants.java:294`）；`checkpoint-action-type.dict.yaml` 当前**只有 `store`**。
- 既有测试 `TestNopMetaQualityCheckpointBizModel.testExecuteCheckpointUnknownActionFails`（`:136`）配置 `{actionType:"notify",enabled:true}` 并断言 `hasError()`——本 plan 让 notify 合法后**该测试语义需更新**（改用 genuinely-unknown actionType 如 `foo_bar` 保留「未知动作失败」覆盖）。
- **平台 HTTP 客户端**就绪：`io.nop.http.api.client.IHttpClient` + `HttpRequest`/`IHttpResponse`（`nop-network/nop-http/nop-http-api`）。`IHttpClient.fetch(req, cancellationContext)` 为同步包装（`fetchAsync` 返回 `CompletionStage`），`HttpRequest` 有 setUrl/setMethod/setHeaders/setBody，`IHttpResponse` 有 getHttpStatus/getBodyAsString。参考 `nop-ai-toolkit/HttpRequestExecutor`。**但 `nop-metadata-service/pom.xml` 当前无任何 `nop-http-*` 依赖**——需新增。
- **平台消息服务**就绪：`io.nop.api.core.message.IMessageService`/`IMessageSender`（`nop-kernel/nop-api-core`），`IMessageSender.send(String topic, Object message)`。
- **事务传播**可控：`@Transactional(propagation = ...)` 支持 `NOT_SUPPORTED`/`REQUIRES_NEW`/`REQUIRED`（`TransactionPropagation.java`）。**post-commit 钩子存在**：`ITransaction.addListener(ITransactionListener)`（`nop-dao/.../txn/ITransaction.java`），`ITransactionListener.onAfterCommit(txn)`（`ITransactionListener.java:34`）在事务成功提交后回调——可用于「store 先提交、再投递」的真 post-commit dispatch。
- **AutoTest mock 惯例**：Nop AutoTest **不用** Mockito 注解（`@JunitMockBean`/`@Mock` 全仓零命中），而是写 `test-mock.beans.xml` + 自实现 mock 类（参考 `nop-ai/nop-ai-toolkit/src/test/resources/_vfs/nop/ai/beans/test-mock.beans.xml` + `nop-ai/nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/mock/MockHttpClient.java`）。

真正剩余 gap：

1. `webhook` 动作未实现（当前被显式拒绝）。
2. `notify` 动作未实现（当前被显式拒绝）。
3. 动作投递与 store 的事务隔离未裁定（dispatch 若在同事务，投递失败/超时会回滚或长时间占用 store 事务）。

## Goals

- 检查点执行成功落盘后，可按 `actions` 配置向配置的 webhook URL 投递执行摘要（HTTP POST JSON），经 `IHttpClient`。
- 检查点执行可按 `actions` 配置通过平台 `IMessageService` 向消息通道投递执行摘要（notify）。
- **投递在 store 提交之后执行（post-commit）**：store（`NopMetaQualityResult`）事务成功提交后才触发 webhook/notify 投递，因此投递失败/超时不可能回滚已落盘 store，HTTP/消息调用也不占用 store 事务（经 `ITransactionListener.onAfterCommit`）。
- 未知动作类型（含 update_docs）仍显式失败（不静默跳过）。

## Non-Goals

- **不**实现 `update_docs` 动作（依赖文档渲染层，独立结果面，保持 deferred；配置 update_docs 且 enabled 时仍显式失败）。
- **不**实现定时调度（属 `2026-07-17-0540-1` 的 Deferred）。
- **不**实现 SMTP/邮件渲染细节：notify 复用平台 `IMessageService` 通道抽象。
- **不**做 webhook 投递的重试/死信队列（首版同步投递 + 显式失败记录；可靠投递为 follow-up）。
- **不**改动 `NopMetaQualityCheckpoint` ORM schema（`actions` JSON 列已够）。

## Scope

### In Scope

- 新增 `nop-http-api` 依赖到 `nop-metadata-service/pom.xml`（webhook 用）。
- 动作分发机制 + webhook handler（`IHttpClient` POST）+ notify handler（`IMessageService.send`）。
- 投递与 store 事务隔离（Decision 见下）。
- `IHttpClient`/`IMessageService` 经 IoC 注入到 BizModel（`@Inject`），dispatch 在 BizModel 层执行。
- `checkpoint-action-type.dict.yaml` 增加 `webhook`/`notify` 值 + `_NopMetadataCoreConstants` 对应常量。
- 更新既有 `testExecuteCheckpointUnknownActionFails` 用 genuinely-unknown actionType 保留覆盖。

### Out Of Scope

- update_docs、SMTP、webhook 可靠投递。

## Execution Plan

### Phase 1 - 动作分发框架 + webhook 动作（含 pom/IoC/事务裁定）

Status: planned
Targets: `nop-metadata-service/pom.xml`、`nop-metadata-service/.../service/entity/NopMetaQualityCheckpointBizModel.java`、`.../service/quality/MetaQualityCheckpointExecutor.java`（validateActionsOrThrow）、`checkpoint-action-type.dict.yaml`、`_NopMetadataCoreConstants`、测试 mock 资源

- Item Types: `Fix`（webhook 缺口 + pom 缺口）、`Decision`

- [ ] 新增 `nop-http-api` 依赖到 `nop-metadata-service/pom.xml`。
- [ ] **Decision（IoC 注入路径）**：裁定 `IHttpClient` 经 `@Inject` 注入 BizModel（不碰 `_service.beans.xml` 的 BizModel 注册结构，不改 executor 构造依赖）；动作 dispatch 在 BizModel 层（executor 返回摘要后）执行。executor 仅负责把合法 actionType 透传/或 dispatch 入口返回所需信息；store 仍在 executor 内完成。
- [ ] **Decision（事务隔离 = 真 post-commit）**：裁定动作 dispatch 经 `ITransaction.addListener` 注册的 `ITransactionListener.onAfterCommit(txn)` 回调执行——store（QualityResult）在 `executeCheckpoint` 事务**成功提交之后**才触发 webhook/notify 投递。由此（a）store 已落盘提交，投递失败/超时**不可能回滚** store；（b）HTTP/消息调用**不占用** store 事务（dispatch 在 commit 之后运行）。dispatch 不 inline 在 `executeCheckpoint` 方法体内执行（方法返回时事务尚未提交）。若运行时无活跃事务（无 listener 可挂），dispatch 退化为 execute 返回后同步执行 + 穷尽 per-action try/catch 兜底（仍保证投递失败不阻断返回）。
- [ ] **Decision（分发位置）**：在「所有规则执行 + store 完成、摘要组装后」统一分发（每 checkpoint 一次投递，非每规则），避免重复投递。
- [ ] 动作分发入口：按 `actions` 配置遍历，按 actionType 路由 handler；webhook/notify/store 视为合法，未知 actionType（含 update_docs）显式失败抛 inline ErrorCode。
- [ ] webhook handler：经 `IHttpClient.fetch`（同步）向 `config.url`（method 默认 POST）投递执行摘要 JSON（headers `Content-Type: application/json`）；config 缺失 `url` → 显式失败；HTTP 非 2xx/异常记入摘要 errors。
- [ ] `IHttpClient` 注入为 null/未注册（宿主未拉 HTTP client impl，`nopHttpClient` bean 带条件）时 webhook 显式失败（抛 ErrorCode），不 NPE/不启动失败（对称 Phase 2 的 IMessageService 处理）。
- [ ] 改写 `validateActionsOrThrow`：store/webhook/notify 合法；未知 actionType（含 update_docs）仍显式失败。
- [ ] `checkpoint-action-type.dict.yaml` 增加 `webhook`/`notify`；`_NopMetadataCoreConstants` 增加 `CHECKPOINT_ACTION_TYPE_WEBHOOK`/`CHECKPOINT_ACTION_TYPE_NOTIFY` 常量。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 配置 `{actionType:"webhook", config:{url}, enabled:true}` 的 checkpoint 执行后，`MockHttpClient.fetch` 被调一次，请求 body 为执行摘要 JSON。
- [ ] **事务隔离硬验证（post-commit）**：配置 webhook 指向失败端点（mock `fetch` 抛错/返回非 2xx）；执行后 `NopMetaQualityResult` store 行仍存在（已提交、未回滚），失败动作记入摘要 errors（验证 onAfterCommit post-commit dispatch 成立——投递在 store 提交之后，不回滚 store）。
- [ ] webhook config 缺失 `url` → 显式失败（对称 Phase 2 channel 校验）。
- [ ] `IHttpClient` 注入为 null/未注册时 webhook 显式失败（ErrorCode），不 NPE/不启动失败。
- [ ] 未知 actionType（含 `update_docs`）仍显式失败（抛 ErrorCode）；既有 `testExecuteCheckpointUnknownActionFails` 改用 genuinely-unknown actionType（如 `foo_bar`）保留覆盖，并**新增一条 `update_docs` actionType → 显式失败**的专门测试（钉住 update_docs deferred 契约）。
- [ ] **新增功能测试覆盖（Rule #25）**：列出新增测试——(a) webhook 投递成功（mock verify fetch 调用 + payload）；(b) webhook 失败 → store 存活 + errors 记录；(c) webhook config 缺 url → 失败；(d) IHttpClient 为 null → 显式失败；(e) 未知 actionType（`foo_bar`）→ 失败；(f) `update_docs` → 显式失败。
- [ ] **mock 方式（Rule #25 落地）**：测试通过新增 `test-mock.beans.xml` + 自实现 `MockHttpClient` 类记录 `fetch` 调用（Nop AutoTest 惯例，非 Mockito 注解），断言 `fetch` 被调一次且 payload（摘要）正确。
- [ ] **端到端验证**：测试从 `executeCheckpoint`（入口）到 `MockHttpClient.fetch` 收到摘要（出口）完整跑通。
- [ ] **接线验证**：断言 webhook handler 运行时被动作分发调用（mock verify `IHttpClient.fetch` 被调），证明分发链连通非空壳。
- [ ] **无静默跳过**：HTTP 失败不吞异常——记入 errors 或抛 ErrorCode；无空 catch/continue。
- [ ] owner doc `01-architecture-baseline.md` §2.7.3 D4 更新（webhook 动作 + 事务隔离 + IoC 裁定），`06-data-quality-extended.md` §4.3 状态更新，`nop-metadata-roadmap.md` 更新。
- [ ] `ai-dev/logs/2026/07-17.md` 对应条目已更新。

### Phase 2 - notify 动作（IMessageService 通道投递）

Status: planned
Targets: notify handler、`_service.beans.xml`/IoC（IMessageService 注入 BizModel）、动作分发扩展、测试 mock 资源

- Item Types: `Fix`（notify 缺口）、`Proof`

- [ ] notify handler：经 `IMessageService`（`IMessageSender.send`）投递；`config.channel` 为 topic，message 信封为 `{checkpointId, summary, recipients}`（recipients 来自 config）。
- [ ] 动作分发扩展支持 notify；config 缺失 `channel` → 显式失败抛 ErrorCode。
- [ ] `IMessageService` 经 `@Inject` 注入 BizModel；运行时无消息实现（注入为 null）时 notify 动作显式失败（抛 ErrorCode），不 NPE/不静默。
- [ ] notify 投递异常记入摘要 errors，不影响 store 与其他动作（同 Phase 1 事务隔离）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 配置 `{actionType:"notify", config:{channel, recipients}, enabled:true}` 的 checkpoint 执行后，mock `IMessageService` 收到一次 `send(channel, message)`，message 信封含 checkpointId + summary。
- [ ] notify config 缺失 `channel` → 显式失败。
- [ ] `IMessageService` 注入为 null 时 notify 显式失败（ErrorCode），不 NPE/不静默。
- [ ] **新增功能测试覆盖（Rule #25）**：列出新增测试——(a) notify 投递成功（mock beans.xml + `MockMessageService` verify send 调用 + 信封）；(b) notify config 缺 channel → 失败；(c) IMessageService 为 null → 显式失败；(d) notify 失败 → store 存活 + errors 记录。
- [ ] **端到端验证**：测试从 `executeCheckpoint`（入口）到 `IMessageService.send`（出口）完整跑通。
- [ ] **接线验证**：断言 notify handler 运行时被分发调用、`IMessageService.send` 被调（mock verify），连通非空壳。
- [ ] **无静默跳过**：IMessageService 未配置/投递失败时显式失败或记 errors，无空方法体/吞异常。
- [ ] owner doc §2.7.3 D4 更新（notify 动作），`nop-metadata-roadmap.md` 更新。
- [ ] `ai-dev/logs/2026/07-17.md` 对应条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 0027-1「notify/webhook 动作」deferred 项已落地并从 follow-up 收口（update_docs 仍 open，记录于 Deferred，Successor Required: no）
- [ ] webhook 与 notify 两类动作可真实投递执行摘要；非 store 动作不再被一刀切拒绝；未知动作（含 update_docs）仍显式失败
- [ ] 投递与 store 事务隔离经测试硬验证（store 在投递失败时存活）
- [ ] 不存在被静默降级到 deferred 的 in-scope 缺口
- [ ] owner docs（`01-architecture-baseline.md` §2.7.3 D4、`06-data-quality-extended.md` §4.3、`nop-metadata-roadmap.md`）已同步到 live baseline
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）动作分发→webhook/notify handler→IHttpClient/IMessageService 调用链运行时连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-metadata -am`
- [ ] `./mvnw test -pl nop-metadata/nop-metadata-service -am`
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### update_docs 动作

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: update_docs 依赖文档渲染层与输出路径策略（html/outputPath），独立于结果投递结果面。store/webhook/notify 已覆盖「结果可查询 + 可外部投递」核心需求。配置 update_docs 且 enabled 时仍显式失败（保留 `validateActionsOrThrow` 的 update_docs 显式失败分支，不静默跳过）。
- Successor Required: `no`

### webhook 可靠投递（重试/死信/异步队列）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版同步投递（经事务隔离）+ 显式失败记录已使 webhook 结果可观测、可追溯；可靠投递属运维增强，独立复杂度。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 动作执行模板化（用户自定义投递 payload 结构）。
- webhook 签名/鉴权 header 配置（首版仅 Content-Type）。
- webhook 投递超时阈值可配置（缓解 HTTP 占用问题，事务隔离已保证 store 不受影响）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<session ID>>
- Evidence: <<逐条 Exit Criterion / Closure Gate 验证结果>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
