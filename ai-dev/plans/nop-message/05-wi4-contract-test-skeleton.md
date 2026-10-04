# 05 WI4 共享契约测试骨架

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI4，deps WI2；对应 MSG-F-49 与 MSG-F-46）
> Related: `ai-dev/design/nop-message/message-contract-design.md`（D1 三态契约）、`ai-dev/design/nop-message/backend-semantics-design.md`（D5 返回协议）

## Purpose

新增 broker 无关的 `AbstractMessageServiceContractTest` 契约测试骨架，覆盖 send、五种 `onMessage` 返回值语义、ConsumeLater、batch arity、suspend-resume-cancel、destroy 幂等；`LocalMessageService` 作为首个 extend 者全绿。骨架成为 WI6 至 WI28 每个代码类工作项的必跑回归面。

## Current Baseline

- WI1-WI3、WI5 已 done；D1-D11 已落档。契约测试的期望语义以两篇 design 文档为准：五种返回值协议（D5）+ 逐后端能力声明（D1）。
- 现有测试：`TestLocalMessageService` / `TestLocalMessageServiceAsync`（nop-message-core），仅覆盖基本收发，无契约面。
- **Local 现状与契约的差距**：`handleMessageResult`（`LocalMessageService.java:184-192`）不识别 `ConsumeLater`——按 F-14 它会被当作普通 reply 发到 ack topic。契约要求 ConsumeLater = 延迟重投且不发 reply。**本 plan 包含 F-14 的最小修复**（Local 侧识别 ConsumeLater → 调度延迟重投）；F-14 的完整收口（含 Local 之外的语义对齐验证）仍归 WI23。
- Local 的 suspend/resume（volatile 标志 + 分发循环跳过）与 cancel 幂等（removeConsumer 安全重入）已在位；`clearConsumers()` 可作 close 语义。
- 模块图（F-49）：kafka/pulsar 不依赖 nop-message-core，本骨架放 `nop-message-core` 的 test scope；后续后端 extend 时的模块图变更（test-jar 或下沉）归 WI32 显式声明。

## Goals

- `nop-message-core` test scope 新增：
  - `AbstractMessageServiceContractTest`：抽象骨架，通过受保护钩子方法（如 supportsBatchConsume / supportsSuspendResume / closeService）表达「实现声明什么，契约就断言什么」——与 D1 三态同构；WI6 引入 `MessageCapability` 后钩子可切换为能力集驱动。
  - `LocalMessageServiceContractTest extends AbstractMessageServiceContractTest`：Local 全绿。
- 契约覆盖（roadmap WI4 完成判定的逐项分解）：
  1. send：sendAsync 完成且消息送达订阅者；send 同步送达；sendMultiAsync 逐条送达。
  2. 五种返回值：null→无 reply（ack 语义）；CompletionStage→解包后再分派；Acknowledge→replyMessage 落 ack topic；其他非 null→落 ack topic；ConsumeLater→**不落 ack topic** 且延迟后重投（delay 被兑现：断言 delay 期间无立即重投）。
  3. batch arity：supportsBatchConsume()=false 的实现（Local）以 JUnit assumption 显式跳过并留说明；=true 的实现断言 onMessageBatch 被调用且响应列表与消息列表 arity 一致（骨架内置，Kafka/Pulsar extend 时生效）。
  4. suspend-resume-cancel：suspend 后不送达、resume 后送达；cancel 后不送达且 isCancelled=true；**cancel 幂等**（二次 cancel 无异常无副作用）。
  5. destroy 幂等：closeService()（Local 映射 clearConsumers；broker 后端映射 destroy）调用两次无异常、二次调用后 subscribe/send 行为符合该实现声明（Local：重新 subscribe 后可继续收发）。
- LocalMessageService 最小修复（F-14 一部分）：识别 ConsumeLater → 用共享单线程 daemon ScheduledExecutorService（lazy 初始化）延迟重投（max(delay,0)），不向 ack topic 发送。两条实现裁定：
  - **重投范围 = 返回 ConsumeLater 的那个订阅**（per-subscription），非 topic 级全订阅重投——后者会把已 ack 的订阅者再投一次；该选择登记于修复 Javadoc，WI23 收口 F-15 时保持一致。
  - **fire-time 状态再检查**：调度任务触发时必须复查该订阅的 cancelled / suspended 以及是否仍注册于 consumers map；已取消/已销毁则静默放弃该次重投（订阅取消即放弃 pending 重投是契约行为，非吞错误）。

## Non-Goals

- 不实现 WI6 的 `MessageCapability`/validator（钩子方法是其前身，届时替换）。
- 不修 F-15（重复订阅交叉取消）与 F-56（Local 选项零处置）——归 WI23。
- 不让 Kafka/Pulsar extend 骨架（模块图变更归 WI32）；本 plan 不改两后端任何代码。
- 不引入新依赖（JUnit 5 已在 nop-autotest-junit 体系内）。

## Scope

### In Scope

- `nop-message/nop-message-core/src/test/java/io/nop/message/core/contract/`（新建 2 个测试文件）。
- `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java`（ConsumeLater 最小修复）。
- `ai-dev/logs/2026/10-04.md`、roadmap WI4 状态行。

### Out Of Scope

- `nop-message-kafka`、`nop-message-pulsar`、`nop-api-core` 的任何改动。
- 其他文档（design 已在 D5/D1 中声明契约语义，无需更新——`No owner-doc update required`，理由：行为变更方向与已落档 design 一致）。

## Execution Plan

### Phase 1 - 骨架与 Local 修复

Status: completed
Targets: `nop-message/nop-message-core`

- Item Types: `Proof`（骨架）+ `Fix`（Local ConsumeLater，对应 MSG-F-14 最小修复）

- [x] 写 `AbstractMessageServiceContractTest`：钩子（supportsBatchConsume/supportsSuspendResume/closeService）+ 上述 5 组契约测试；batch 组用 `Assumptions.assumeTrue` 显式跳过并输出原因。Flaky 防护：ConsumeLater 测试 delay ≥ 200ms，重投等待用 latch + 充裕超时（delay + ≥2s 余量）；负断言（delay 窗口内无立即重投）在 send 同步返回后立即采样；测试 handler 仅首次返回 ConsumeLater、其后返回 null 防自我续期
- [x] 写 `LocalMessageServiceContractTest`：Local 绑定（closeService→clearConsumers，supportsBatchConsume=false，supportsSuspendResume=true）
- [x] 修 `LocalMessageService.handleMessageResult`：ConsumeLater 分支 → 按「per-subscription 范围 + fire-time 状态再检查」两裁定调度延迟重投（共享 daemon scheduler，lazy 初始化），不落 ack topic
- [x] `./mvnw test -pl nop-message/nop-message-core -am` 全绿（含既有 TestLocalMessageService 两文件不回归）

Exit Criteria:

- [x] 契约覆盖 5 组齐全，每组有独立可读的测试方法名与断言（ConsumeLater 组断言：ack topic 无消息 + delay 后重投到达 + delay 窗口内无立即重投）
- [x] Local extend 全绿：`./mvnw test -pl nop-message/nop-message-core -am` 退出 0
- [x] **无静默跳过**：Local 修复不引入空方法体/吞异常；batch 跳过为显式 assumption 并有日志说明，非静默
- [x] **新功能必有测试**：Local 的 ConsumeLater 重投行为由契约测试本身覆盖（新行为与测试一一对应）
- [x] 既有测试零回归
- [x] `ai-dev/logs/2026/10-04.md` 追加条目
- [x] No owner-doc update required（行为变更方向与已落档 design D5 一致）

### Phase 2 - 骨架登记为必跑回归面

Status: completed
Targets: roadmap

- Item Types: `Proof`

- [x] roadmap WI4 状态行 todo→planned→done（复跑 parseRoadmapMarkdown 校验 35+6）
- [x] 「必跑回归面」机制声明：本 plan Closure 记录裁定——后续 WI 的 plan 须在验证命令含 `-pl nop-message/nop-message-core`（落实情况由各后续 plan 的 closure audit 检查，不在本 plan 内验证）

Exit Criteria:

- [x] roadmap 解析 35+6
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 不新增断链

## Closure Gates

- [x] 骨架 + Local 契约测试全绿（命令与结果记录于日志）
- [x] 契约 5 组覆盖完整、ConsumeLater 三断言齐全
- [x] Local 最小修复无静默跳过、不回归既有测试
- [x] 无 kafka/pulsar/api-core 改动（git diff 证明）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-core -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/05-wi4-contract-test-skeleton.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- F-14 残余（Local 之外后端的 ConsumeLater 语义验证）→ WI23。
- 骨架向 Kafka/Pulsar 扩展的模块图变更 → WI32。

## Closure

Status Note: WI4 骨架与 Local 修复均已落地并经独立子代理 closure audit（含独立复跑测试）：5 组契约与 roadmap WI4 完成判定逐项对应、12 run / 1 显式 skip（batch assumption）/ 0 fail、既有测试零回归、kafka/pulsar/api-core 零改动、无静默跳过。audit 的 2 条 Minor（Phase 2 checkbox 超前置位按本收口补齐；ConsumeLater 负断言形态在 Kafka/Pulsar extend 时需按后端语义重设计）分别以「收口即翻转」与「登记 WI32 输入」处置。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_761b6a2d-bbf6-460d-8a7e-6549eb5d98be（fresh session，独立复跑测试）
- Evidence:
  - 契约覆盖 PASS：WI4 完成判定逐项对应测试方法（send 3 + 五返回值 5 + batch 1 + suspend/cancel 2 + close 1）
  - ConsumeLater 三断言 PASS：ack topic 空（L209-210）/ delay 后重投且 payload 一致（L216-219）/ delay 窗口内无立即重投（L211-212）
  - Local 修复 PASS：拦截点、fire-time 再查、两条裁定 Javadoc 登记、无静默跳过
  - 独立复跑 PASS：`./mvnw test -pl nop-message/nop-message-core -am` 退出 0；契约 12/1skip/0fail；既有 3 用例零回归
  - git 范围 PASS：kafka/pulsar/api-core diff 为空
  - roadmap 解析 35+6；check-doc-links --strict 退出 0
  - WI32 输入登记：ConsumeLater 负断言在异步后端需按其消费语义重设计（audit Minor-2）
  - Deferred 分类检查：无 deferred- [x] WI4 共享契约测试骨架

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI4，deps WI2；对应 MSG-F-49 与 MSG-F-46）
> Related: `ai-dev/design/nop-message/message-contract-design.md`（D1 三态契约）、`ai-dev/design/nop-message/backend-semantics-design.md`（D5 返回协议）

## Purpose

新增 broker 无关的 `AbstractMessageServiceContractTest` 契约测试骨架，覆盖 send、五种 `onMessage` 返回值语义、ConsumeLater、batch arity、suspend-resume-cancel、destroy 幂等；`LocalMessageService` 作为首个 extend 者全绿。骨架成为 WI6 至 WI28 每个代码类工作项的必跑回归面。

## Current Baseline

- WI1-WI3、WI5 已 done；D1-D11 已落档。契约测试的期望语义以两篇 design 文档为准：五种返回值协议（D5）+ 逐后端能力声明（D1）。
- 现有测试：`TestLocalMessageService` / `TestLocalMessageServiceAsync`（nop-message-core），仅覆盖基本收发，无契约面。
- **Local 现状与契约的差距**：`handleMessageResult`（`LocalMessageService.java:184-192`）不识别 `ConsumeLater`——按 F-14 它会被当作普通 reply 发到 ack topic。契约要求 ConsumeLater = 延迟重投且不发 reply。**本 plan 包含 F-14 的最小修复**（Local 侧识别 ConsumeLater → 调度延迟重投）；F-14 的完整收口（含 Local 之外的语义对齐验证）仍归 WI23。
- Local 的 suspend/resume（volatile 标志 + 分发循环跳过）与 cancel 幂等（removeConsumer 安全重入）已在位；`clearConsumers()` 可作 close 语义。
- 模块图（F-49）：kafka/pulsar 不依赖 nop-message-core，本骨架放 `nop-message-core` 的 test scope；后续后端 extend 时的模块图变更（test-jar 或下沉）归 WI32 显式声明。

## Goals

- `nop-message-core` test scope 新增：
  - `AbstractMessageServiceContractTest`：抽象骨架，通过受保护钩子方法（如 supportsBatchConsume / supportsSuspendResume / closeService）表达「实现声明什么，契约就断言什么」——与 D1 三态同构；WI6 引入 `MessageCapability` 后钩子可切换为能力集驱动。
  - `LocalMessageServiceContractTest extends AbstractMessageServiceContractTest`：Local 全绿。
- 契约覆盖（roadmap WI4 完成判定的逐项分解）：
  1. send：sendAsync 完成且消息送达订阅者；send 同步送达；sendMultiAsync 逐条送达。
  2. 五种返回值：null→无 reply（ack 语义）；CompletionStage→解包后再分派；Acknowledge→replyMessage 落 ack topic；其他非 null→落 ack topic；ConsumeLater→**不落 ack topic** 且延迟后重投（delay 被兑现：断言 delay 期间无立即重投）。
  3. batch arity：supportsBatchConsume()=false 的实现（Local）以 JUnit assumption 显式跳过并留说明；=true 的实现断言 onMessageBatch 被调用且响应列表与消息列表 arity 一致（骨架内置，Kafka/Pulsar extend 时生效）。
  4. suspend-resume-cancel：suspend 后不送达、resume 后送达；cancel 后不送达且 isCancelled=true；**cancel 幂等**（二次 cancel 无异常无副作用）。
  5. destroy 幂等：closeService()（Local 映射 clearConsumers；broker 后端映射 destroy）调用两次无异常、二次调用后 subscribe/send 行为符合该实现声明（Local：重新 subscribe 后可继续收发）。
- LocalMessageService 最小修复（F-14 一部分）：识别 ConsumeLater → 用共享单线程 daemon ScheduledExecutorService（lazy 初始化）延迟重投（max(delay,0)），不向 ack topic 发送。两条实现裁定：
  - **重投范围 = 返回 ConsumeLater 的那个订阅**（per-subscription），非 topic 级全订阅重投——后者会把已 ack 的订阅者再投一次；该选择登记于修复 Javadoc，WI23 收口 F-15 时保持一致。
  - **fire-time 状态再检查**：调度任务触发时必须复查该订阅的 cancelled / suspended 以及是否仍注册于 consumers map；已取消/已销毁则静默放弃该次重投（订阅取消即放弃 pending 重投是契约行为，非吞错误）。

## Non-Goals

- 不实现 WI6 的 `MessageCapability`/validator（钩子方法是其前身，届时替换）。
- 不修 F-15（重复订阅交叉取消）与 F-56（Local 选项零处置）——归 WI23。
- 不让 Kafka/Pulsar extend 骨架（模块图变更归 WI32）；本 plan 不改两后端任何代码。
- 不引入新依赖（JUnit 5 已在 nop-autotest-junit 体系内）。

## Scope

### In Scope

- `nop-message/nop-message-core/src/test/java/io/nop/message/core/contract/`（新建 2 个测试文件）。
- `nop-message/nop-message-core/src/main/java/io/nop/message/core/local/LocalMessageService.java`（ConsumeLater 最小修复）。
- `ai-dev/logs/2026/10-04.md`、roadmap WI4 状态行。

### Out Of Scope

- `nop-message-kafka`、`nop-message-pulsar`、`nop-api-core` 的任何改动。
- 其他文档（design 已在 D5/D1 中声明契约语义，无需更新——`No owner-doc update required`，理由：行为变更方向与已落档 design 一致）。

## Execution Plan

### Phase 1 - 骨架与 Local 修复

Status: completed
Targets: `nop-message/nop-message-core`

- Item Types: `Proof`（骨架）+ `Fix`（Local ConsumeLater，对应 MSG-F-14 最小修复）

- [x] 写 `AbstractMessageServiceContractTest`：钩子（supportsBatchConsume/supportsSuspendResume/closeService）+ 上述 5 组契约测试；batch 组用 `Assumptions.assumeTrue` 显式跳过并输出原因。Flaky 防护：ConsumeLater 测试 delay ≥ 200ms，重投等待用 latch + 充裕超时（delay + ≥2s 余量）；负断言（delay 窗口内无立即重投）在 send 同步返回后立即采样；测试 handler 仅首次返回 ConsumeLater、其后返回 null 防自我续期
- [x] 写 `LocalMessageServiceContractTest`：Local 绑定（closeService→clearConsumers，supportsBatchConsume=false，supportsSuspendResume=true）
- [x] 修 `LocalMessageService.handleMessageResult`：ConsumeLater 分支 → 按「per-subscription 范围 + fire-time 状态再检查」两裁定调度延迟重投（共享 daemon scheduler，lazy 初始化），不落 ack topic
- [x] `./mvnw test -pl nop-message/nop-message-core -am` 全绿（含既有 TestLocalMessageService 两文件不回归）

Exit Criteria:

- [x] 契约覆盖 5 组齐全，每组有独立可读的测试方法名与断言（ConsumeLater 组断言：ack topic 无消息 + delay 后重投到达 + delay 窗口内无立即重投）
- [x] Local extend 全绿：`./mvnw test -pl nop-message/nop-message-core -am` 退出 0
- [x] **无静默跳过**：Local 修复不引入空方法体/吞异常；batch 跳过为显式 assumption 并有日志说明，非静默
- [x] **新功能必有测试**：Local 的 ConsumeLater 重投行为由契约测试本身覆盖（新行为与测试一一对应）
- [x] 既有测试零回归
- [x] `ai-dev/logs/2026/10-04.md` 追加条目
- [x] No owner-doc update required（行为变更方向与已落档 design D5 一致）

### Phase 2 - 骨架登记为必跑回归面

Status: completed
Targets: roadmap

- Item Types: `Proof`

- [x] roadmap WI4 状态行 todo→planned→done（复跑 parseRoadmapMarkdown 校验 35+6）
- [x] 「必跑回归面」机制声明：本 plan Closure 记录裁定——后续 WI 的 plan 须在验证命令含 `-pl nop-message/nop-message-core`（落实情况由各后续 plan 的 closure audit 检查，不在本 plan 内验证）

Exit Criteria:

- [x] roadmap 解析 35+6
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 不新增断链

## Closure Gates

- [x] 骨架 + Local 契约测试全绿（命令与结果记录于日志）
- [x] 契约 5 组覆盖完整、ConsumeLater 三断言齐全
- [x] Local 最小修复无静默跳过、不回归既有测试
- [x] 无 kafka/pulsar/api-core 改动（git diff 证明）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `./mvnw test -pl nop-message/nop-message-core -am` 退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/05-wi4-contract-test-skeleton.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- F-14 残余（Local 之外后端的 ConsumeLater 语义验证）→ WI23。
- 骨架向 Kafka/Pulsar 扩展的模块图变更 → WI32。

## Closure

Status Note: WI4 骨架与 Local 修复均已落地并经独立子代理 closure audit（含独立复跑测试）：5 组契约与 roadmap WI4 完成判定逐项对应、12 run / 1 显式 skip（batch assumption）/ 0 fail、既有测试零回归、kafka/pulsar/api-core 零改动、无静默跳过。audit 的 2 条 Minor（Phase 2 checkbox 超前置位按本收口补齐；ConsumeLater 负断言形态在 Kafka/Pulsar extend 时需按后端语义重设计）分别以「收口即翻转」与「登记 WI32 输入」处置。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_761b6a2d-bbf6-460d-8a7e-6549eb5d98be（fresh session，独立复跑测试）
- Evidence:
  - 契约覆盖 PASS：WI4 完成判定逐项对应测试方法（send 3 + 五返回值 5 + batch 1 + suspend/cancel 2 + close 1）
  - ConsumeLater 三断言 PASS：ack topic 空（L209-210）/ delay 后重投且 payload 一致（L216-219）/ delay 窗口内无立即重投（L211-212）
  - Local 修复 PASS：拦截点、fire-time 再查、两条裁定 Javadoc 登记、无静默跳过
  - 独立复跑 PASS：`./mvnw test -pl nop-message/nop-message-core -am` 退出 0；契约 12/1skip/0fail；既有 3 用例零回归
  - git 范围 PASS：kafka/pulsar/api-core diff 为空
  - roadmap 解析 35+6；check-doc-links --strict 退出 0
  - WI32 输入登记：ConsumeLater 负断言在异步后端需按其消费语义重设计（audit Minor-2）
  - Deferred 分类检查：无 deferred
