# 352 nop-stream + 基线模块 typed errors 收口

> Plan Status: completed
> Last Reviewed: 2026-09-12
> Source: `ai-dev/audits/2026-09/2026-09-12-2130-nop-platform-conformance/`（04 ST-2/ST-4/ST-5/ST-9/ST-10/ST-11/ST-12/ST-13、02 §F-B datav 部分、01 §3 基线 11 IAE）
> Related: 350/351（已完成）、353-357。Plan 已过一轮对抗审查（agent_9d4ae9af，1 Blocker + 5 Major 已吸收：job Errors 归宿 JobCoreErrors、复用既有码、catch 改写全貌、backoff 走 AlertService config key、跨模块 install、351 先提交前置已满足；计数勘误 145 处/core 41 文件/runtime 17 文件/cep 6 处/datav 3 文件 7 处）。

## Purpose

把审计 P1-5（nop-stream 293 处裸异常）与关联 P2/P3（ST-4 重试、ST-5 手写 JSON、ST-9/10/11/12/13）以及基线模块 11 处裸 IAE（01 §3）、datav 裸异常（F-B 部分）收口为模块 typed ErrorCode 体系，消除 REST 边界的异常类型嗅探。

## Current Baseline

- 异常体系现状：`StreamException extends StreamRuntimeException`（`io.nop.stream.core.exceptions`），`NopStreamErrors` 约 80 个 typed ErrorCode（含 checksum/fencing/2PC/凭据语义）；错误消息全英文（1 处中文例外 `GraphModelCheckpointExecutor.java:1163`）。
- 裸异常分布（`throw new IAE|ISE|UOE`，main，排除 fraud-example）：
  - stream-runtime：`OpsJobManager` 11、`StreamStateResetTool` 6、`WebhookAlertChannel` 5、`ClusterLaunchConfig` 4、`StreamOpsHttpServer` 3 等，合计 16 文件
  - stream-core：`ConnectorCapabilityDescriptor` 9（讽刺点：registry 侧已有 `ERR_STREAM_CONNECTOR_DESCRIPTOR_INVALID` 却不用）、`KeyGroupAssignment` 9、`KeyGroupReshard` 6、`LocalSourceCoordinator` 4 等，合计 32 文件
  - stream-cep 4 处：`CepOperator:638` ISE（不变式）、`RichIterativeCondition:56,62` ISE+UOE（运行时上下文未初始化 fast-fail）、`GroupPattern:46` UOE（Flink API 契约声明式不支持——参照 `IApprovableBiz` 设计声明 fast-fail 先例）
- **REST 边界嗅探**（ST-2 加重因素）：`StreamOpsHttpServer.java:336-341` `catch (IllegalArgumentException | IllegalStateException e)` + `e instanceof IllegalStateException && String.valueOf(e).contains("already hosted")` 判 409——需专用 ErrorCode（如 `ERR_STREAM_JOB_ALREADY_HOSTED`）后按码判定。
- ST-4：`WebhookAlertChannel.deliverWithRetries` 手写 for 重试 + 固定 `RETRY_BACKOFF_MS`（owner doc 载明 200ms 属有意决策）。
- ST-5：同文件 `:133-136` 手写 JSON 拼接（`severity` 直接内插转义不全）。
- ST-9：`StreamMaintenanceMain.java:64` `e.printStackTrace(System.err)`。ST-10：`GraphModelCheckpointExecutor.java:1163` 消息体内嵌中文设计文档节名。
- ST-11：`SharedBuffer.java:25-29` import Guava `Cache/CacheBuilder/RemovalListener/RemovalNotification`——**审计修复方向（"应换 StringHelper"）对性质判断有误**：这是缓存库使用非字符串工具；平台 `io.nop.commons.cache.LocalCache` 自身就是 Guava 引擎的包装（commons 是平台依赖）。
- ST-12：`TaskProcessingTimeService.java:155` 内部类 `ProcessingTimeCallbackException extends RuntimeException`（计时器回调控制流包装）。
- ST-13：`throw new AssertionError` 复核为 **0 处**（审计 44 处口径待复核，可能含 assert 关键字/其他模块——执行时以复核数为准）。
- 基线 11 IAE（01 §3）：`WfModelHelper:28`（消息已是错误码风格）、`TaskStepReturn:186`、`WebAuthnAuthenticator:399`、job 8 处（`JobFireStoreImpl:242`、`JobTaskStoreImpl:227`、`JobDispatcherScannerImpl:95,109`、`JobPlannerScannerImpl:67`、`JobTimeoutCheckerImpl:132`、`RpcPollTaskManager:83`、`JobWorkerScannerImpl:98`）。
- datav：`NopDatavErrors` 存在；裸异常 5 文件（`PanelComponentRegistry:127`、`NopDatavReportScheduler:208`、`NopDatavDashboardBizModel:602,625,637` 等）。
- 各基线模块 Errors 体系：`NopWfErrors`/`TaskErrors`/`NopJobErrors`/`NopAuthErrors`（ErrorCode.define + .param 模式）。

## Goals

- stream 全部 IAE/ISE（参数校验/非法状态语义）→ `StreamException(NopStreamErrors.ERR_...).param(...)`；UOE 按"参数校验/状态语义转换、API 契约声明式 fast-fail 保留并注释"分流。
- 新增通用码（参数缺失/非法/非法状态/不支持操作）+ 边界专用码（`ERR_STREAM_JOB_ALREADY_HOSTED` 等），消除 StreamOpsHttpServer 嗅探。
- ST-5：webhook payload 定义 `@DataBean` + `JsonTool.stringify`。ST-4：退避时长可配（@InjectValue）+ Decision 记录不引 nop-retry 的理由（webhook 是 best-effort ops 路径，nop-retry 是持久化分布式重试，语义不匹配；owner doc 已载固定退避决策）。
- ST-9/10/11/12/13：printStackTrace→LOG.error；中文消息改英文；SharedBuffer Guava 裁定；ProcessingTimeCallbackException 裁定；AssertionError 复核后裁定。
- 基线 11 IAE → 各模块 Errors 码（job 8 处可共享 1-2 个专用码如 ERR_JOB_CURSOR_REQUIRES_TIME）。
- datav 裸异常 → NopDatavErrors 码。

## Non-Goals

- 不处理 ST-1/ST-3/ST-6/ST-7/ST-8（分属 351/357）。
- 不处理 nop-ai 裸异常（356）。
- 不重写 cep 移植算子的 Flink API 契约形态（UOE fast-fail 保留）。

## Scope

### In Scope

- `nop-stream-core/cep/runtime/connector-jdbc` main、`nop-datav-service`、`nop-wf-core`、`nop-task-core`、`nop-job-{dao,coordinator,worker}`、`nop-auth-service`（WebAuthnAuthenticator 1 处）

### Out Of Scope

- fraud-example、src/test、其他模块。

## Execution Plan

### Phase 1 - NopStreamErrors 扩码与分流准则

Status: completed
Targets: `nop-stream-core/.../exceptions/NopStreamErrors.java`

- Item Types: `Fix | Decision`

- [x] **复用既有码**：ERR_STREAM_INVALID_STATE(:44)/ERR_STREAM_UNSUPPORTED(:50, ARG_OPERATION)/ERR_STREAM_INVALID_ARG(:66)/ERR_STREAM_NULL_ARG(:41)（审查发现已存在，勿重复定义）；仅新增 ERR_STREAM_JOB_ALREADY_HOSTED 与确需的缺口码
- [x] 分流准则入注释：IAE(参数校验)→PARAM_*；ISE(运行时状态)→INVALID_STATE；UOE 若为 API 契约声明（移植面）保留 + javadoc 标注 fast-fail by design

Exit Criteria:

- [x] 新码集中声明且带 ARG 常量、英文消息
- [x] No new test required: 错误码定义本身（消费方测试在后续 Phase）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - stream-core 裸异常 typed 化（32 文件）

Status: completed
Targets: `nop-stream-core` main

- Item Types: `Fix`

- [x] 参数校验类（ConnectorCapabilityDescriptor 9、KeyGroupAssignment 9、KeyGroupReshard 6 等）→ Phase1 通用码/专用码
- [x] 状态类 ISE → ERR_STREAM_INVALID_STATE 或现场更贴切的既有码（优先复用 80 个既有码）
- [x] `./mvnw install -DskipTests -pl nop-stream/nop-stream-core -q` 后 `./mvnw test -pl nop-stream/nop-stream-core`（runtime 依赖新码需先 install——审查 Major）

Exit Criteria:

- [x] `rg -c 'throw new (IllegalArgumentException|IllegalStateException|UnsupportedOperationException)' nop-stream/nop-stream-core/src/main` 仅剩裁定保留的 UOE（有 fast-fail 注释）
- [x] 模块测试通过
- [x] No new test required: 异常类型替换，既有测试断言消息文本的按需同步（如有）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - stream-runtime typed 化 + 消除 409 嗅探（16 文件）

Status: completed
Targets: `nop-stream-runtime` main（OpsJobManager 11、StreamStateResetTool 6、WebhookAlertChannel 5、ClusterLaunchConfig 4、StreamOpsHttpServer 3 等）

- Item Types: `Fix`

- [x] 全部 IAE/ISE → typed；`OpsJobManager` 的 "already hosted" 抛点改 `ERR_STREAM_JOB_ALREADY_HOSTED`
- [x] `StreamOpsHttpServer` catch 改写全貌：handleSubmit(:337-341) 整个 `catch (IAE|ISE)` 换 `catch (StreamException e)` 按码分流（**字符串比较** `ERR_STREAM_JOB_ALREADY_HOSTED.getErrorCode().equals(e.getErrorCode())`→409，其余→400——NopException.getErrorCode() 返回 String）；**同步处理 handleStop(:367-369)**：其 ISE 来源 JobHealthStateMachine:99 typed 后 409 JOB_STATE_CONFLICT 契约必须保留（该文件在 Phase 3 范围内）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime`

Exit Criteria:

- [x] 复扫 runtime 仅剩裁定保留项；StreamOpsHttpServer 无消息内容嗅探（rg 'contains("already hosted")' 为空）
- [x] 新增/扩展测试：submit 重复 jobId 返回 409 的判定走 ErrorCode（若已有 e2e/单测覆盖则核验并注明，否则补一个 OpsJobManager 层断言 ERR 码的测试）
- [x] 模块测试通过
- [x] No owner-doc update required（REST 契约 409 语义不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - WebhookAlertChannel JSON/重试/杂项（ST-4/5/9/10）

Status: completed
Targets: `nop-stream-runtime/.../alert/WebhookAlertChannel.java`、`maintain/StreamMaintenanceMain.java`、`checkpoint/GraphModelCheckpointExecutor.java`

- Item Types: `Fix | Decision`

- [x] ST-5：定义 `WebhookAlertPayload` @DataBean（jobId/severity/eventType/message/timestamp），`JsonTool.stringify` 替代手写拼接
- [x] ST-4：退避可配走 **AlertService 新增 config key**（`nop.stream.alert.webhook.backoff-ms`，默认 200）+ WebhookAlertChannel 构造器传参（该类由 fromProperties 编程式构造，非 beans 装配，@InjectValue 无效——审查 Major）；Decision 注释：不引 nop-retry（best-effort ops 投递 vs 持久化分布式重试语义不匹配，owner doc 已载固定退避决策）；config key 属 owner-doc 用户可见契约 → 更新 `docs-for-ai/03-modules/nop-stream.md` 对应配置键表
- [x] ST-9：printStackTrace → LOG.error
- [x] ST-10：中文设计文档节名移出消息体（改英文引用）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime`

Exit Criteria:

- [x] WebhookAlertChannel 无手写 JSON 拼接（rg '"\{\\"jobId' 形态为空）；退避可配且有默认值测试或编译验证
- [x] 模块测试通过
- [x] New test required: webhook payload 序列化断言（severity 含特殊字符时正确转义）——若既有 WebhookAlertChannel 测试存在则扩展，否则新增最小单测
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - cep 与 ST-11/12/13 裁定

Status: completed
Targets: `nop-stream-cep`（4 处）、`SharedBuffer.java`、`TaskProcessingTimeService.java`

- Item Types: `Fix | Decision`

- [x] cep 4 处分流：`CepOperator:638` ISE（不变式）→ typed 或裁定内部不变式保留（参照 AssertionError 惯例）；`RichIterativeCondition:56` ISE → typed；`:62`/`GroupPattern:46` UOE 为 Flink API 契约 fast-fail → 保留 + 注释
- [x] ST-11 Decision：SharedBuffer 的 Guava Cache **保留**——审计修复方向性质误判（是缓存库非字符串工具），平台 LocalCache 本身是 Guava 引擎包装，移植类改写风险大于收益；登记 Deferred
- [x] ST-12 Decision：`ProcessingTimeCallbackException` 裁定（控制流包装 vs 用户可见错误——按消费方现场定；转换则 extends StreamRuntimeException 或保留 + 注释）
- [x] ST-13 复核：`rg 'throw new AssertionError'` 实测 0——以复核数登记审计勘误；`assert` 关键字另行计数并裁定（内部不变式惯例）
- [x] `./mvnw test -pl nop-stream/nop-stream-cep`

Exit Criteria:

- [x] cep 分流完成且保留项均有 fast-fail 注释；三个 Decision 结论入 Deferred
- [x] 模块测试通过（如有）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 基线 11 IAE typed 化

Status: completed
Targets: `WfModelHelper:28`、`TaskStepReturn:186`、`WebAuthnAuthenticator:399`、job 8 处

- Item Types: `Fix`

- [x] 各处改所在模块 Errors 码：WfModelHelper→**NopWfCoreErrors**（wf-core 无法依赖 wf-service 的 NopWfErrors——审查 Minor 钉死）新码 invalid-wf-file-path；TaskStepReturn→TaskErrors **新增码**（"nop.err.step-result-is-async" 非既有码）；WebAuthnAuthenticator→NopAuthErrors 新码（保 cause）；job cursor 两处 + scanner 5 处 + config 校验 6 处→**JobCoreErrors**（nop-job-core，dao/coordinator/worker 共同依赖；NopJobErrors 在 nop-job-service 会循环依赖——审查 Blocker）新码（cursor/config-key 校验）
- [x] `./mvnw test -pl nop-wf/nop-wf-core,nop-task/nop-task-core,nop-job/nop-job-dao,nop-job/nop-job-coordinator,nop-job/nop-job-worker,nop-auth/nop-auth-service`

Exit Criteria:

- [x] `rg 'throw new IllegalArgumentException'` 上述 11 处归零
- [x] 模块测试通过
- [x] No new test required: 异常类型替换语义等价（既有测试覆盖调用路径）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - datav 裸异常 typed 化（F-B 部分）

Status: completed
Targets: `nop-datav-service`（3 文件 7 处：NopDatavDashboardBizModel:602/625/637/660、PanelComponentRegistry:127、NopDatavReportScheduler:208）

- Item Types: `Fix`

- [x] NopDatavErrors 复用 ERR_DATAV_QUERY_FAILED(:117) 覆盖 panel query failed 族；新增 duplicate component type / missing reportTaskId / interrupted 码——**描述用中文**（该文件语言契约 :10 统一中文，审查 Minor），全部转 typed
- [x] `./mvnw test -pl nop-datav/nop-datav-service`

Exit Criteria:

- [x] datav-service main 裸 IAE/ISE 归零（biz/model 内部不变式如有保留则注释）
- [x] 模块测试通过
- [x] No new test required: 异常类型替换
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 8 - 全量验证与收口

Status: completed
Targets: 全部受影响模块

- Item Types: `Proof`

- [x] 复扫（glob 用 `!**/nop-stream-fraud-example/**`）：stream（除裁定保留项）+ 基线模块 + datav 的裸 IAE/ISE/UOE 归零证据；同步核对测试侧 ~151 处 assertThrows(IAE|ISE|UOE) 中受影响断言已更新（重点 TestAlertChannels:158-164 构造器校验断言）
- [x] 抽样回归：stream-core/runtime、job 族、datav 全量测试绿
- [x] 独立 closure audit（fresh subagent）

Exit Criteria:

- [x] 归零证据 + 裁定保留清单对照，无未解释残留
- [x] 回归通过记录
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/352-nop-stream-typed-errors.md --strict` 退出码 0
- [x] 独立 closure audit 证据写入 Closure 段

## Closure Gates

- [x] ST-2（293 处，除裁定保留）typed 化 + 409 嗅探消除
- [x] ST-4/5/9/10 修复；ST-11/12/13 裁定登记
- [x] 基线 11 IAE 与 datav 裸异常归零
- [x] 受影响模块测试通过
- [x] 无 in-scope live defect 降级
- [x] 独立 closure audit 完成且证据已写入

## Deferred But Adjudicated

### ST-11 SharedBuffer 的 Guava Cache 保留

- Classification: `watch-only residual`
- Why Not Blocking Closure: 审计修复方向性质误判（Guava Cache 是缓存库非字符串工具；平台 LocalCache 自身即 Guava 引擎包装，Guava 属平台既有依赖）。Flink 移植类改写缓存语义的正确性风险大于一致性收益。
- Successor Required: no

### cep UOE fast-fail（RichIterativeCondition:62 / GroupPattern:46）

- Classification: `watch-only residual`
- Why Not Blocking Closure: Flink API 契约的声明式不支持（参照 IApprovableBiz 设计声明 fast-fail 先例），保留原语义并注释。
- Successor Required: no

### quickstart/template 脚手架模板（2 处）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `@packagePath@` 占位符模板文件（scaffold 生成源，非可编译 Java 产物），不属 src/main 运行时代码。
- Successor Required: no

### ST-13 AssertionError 计数勘误

- Classification: `watch-only residual`
- Why Not Blocking Closure: 复核 stream main 中 AssertionError 引用为 **0**（审计"44 处"为口径混入测试代码/其他模块）；无需处理。
- Successor Required: no

## Non-Blocking Follow-ups

- 无

## Closure

Status Note: 全部 8 Phase 完成；独立 closure audit（agent_0b63f23a）判定 CONDITIONAL——技术实质面全部 PASS（typed 化/409 消除/ST-4/5/9/10/12/13/复用不重复/测试全绿），3 项收尾（datav-dao 4 处补救修复、日志条目、Closure 证据形态修正）已同日完成。
Completed: 2026-09-12

Closure Audit Evidence:

- Reviewer / Agent: agent_0b63f23a（独立 closure audit subagent，fresh session）
- Evidence:
  - 复扫归零：stream 仅剩 4 处裁定保留 UOE（RichIterativeCondition:67 + GroupPattern:50/55/60，均带 fast-fail 注释）；基线 wf/task/auth/job 0；datav service+dao 0（dao 4 处为本 audit 发现的补救修复，新建 dao 层 NopDatavDaoErrors）
  - 409 嗅探消除：`rg 'contains("already hosted")'` 全 nop-stream 为空；StreamOpsHttpServer submit/stop 均 catch StreamException 按 ErrorCode 字符串比较分流；OpsJobManager:123 ERR_STREAM_JOB_ALREADY_HOSTED(jobId)；JobHealthStateMachine:103 ERR_STREAM_ILLEGAL_HEALTH_TRANSITION
  - ST-4/5：WebhookAlertPayload @DataBean + JsonTool.stringify；4 参构造 backoff 可配（默认 200）；AlertService KEY_WEBHOOK_BACKOFF_MS；owner doc 配置键表已更新（nop-stream.md:229）
  - ST-9/10：printStackTrace 归零（main）；错误消息中文节名已英文化
  - 新码纪律：NopStreamErrors 仅新增 2 码复用既有 4 码；JobCoreErrors/NopWfCoreErrors/TaskErrors/NopAuthErrors/NopDatavErrors(+en i18n)/NopDatavDaoErrors 各就位
  - 测试（surefire 汇总，最终轮 23:49-23:51）：stream-core 1592/0、runtime 1060/0、cep 362/0、job-dao 87/0、coordinator 200/0、worker 44/0、task-core 144/0、auth-service 424/0、datav-service 630/0（+dao 补救后复测绿）；wf-core 无测试源码（空真，注明）；TestAlertChannels 7/7 含 ST-5 转义回归
  - ST-12 保留 RuntimeException 有裁定注释；ST-13 AssertionError 复核 0（勘误登记）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/352-nop-stream-typed-errors.md --strict` 退出码 0

Follow-up:

- no remaining plan-owned work
