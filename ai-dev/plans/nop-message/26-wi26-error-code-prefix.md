# 26 WI26 错误码前缀对齐

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Draft Review: 一轮独立子代理对抗审查（agent_d36ca950）发现 2 Blocker——一致性门禁 check-error-param-consistency 全仓现 EXIT=1 且 --module 对本模块族不可用（门禁重写为 scoping 断言 + debezium 三码 Deferred 裁定）、既有测试 producerSendSyncExceptionBecomesFailedFuture 必被包装打破（迁移列入清单）；5 Major（工具存在性勘误/引用形式钉死/测试③ seam/grep pattern 排除日志 key/嵌套模块路径）+ 3 Minor 全修
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI26，deps WI3✓；对应 MSG-F-40 / MSG-F-55；D9 裁定）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（D9 一节 + 3.5）

## Purpose

按 D9 迁移错误码前缀至 `nop.err.message.*` 平台规范，处置六个从未引用的错误码（F-55），填充空接口 `MessageCoreErrors`（F-40）。完成判定（roadmap 原文）：按 D9 裁定迁移至 `nop.err.message.*`；六个从未引用的错误码删除或接线；`MessageCoreErrors` 填充非空；确认无 i18n 与 properties 引用旧前缀；错误码一致性门禁通过。

## Current Baseline

- **F-40 在场**：`KafkaErrors.PREFIX = "nop.message.kafka."`、`PulsarErrors.PREFIX = "nop.message.pulsar."`，不符 `nop.err.[模块].[子域].[错误]`；`MessageCoreErrors` 为空接口。
- **F-55 实证（grep 2026-10-05）**：`ERR_KAFKA_CONSUMER_CREATE_FAILED` / `ERR_KAFKA_SERIALIZATION_FAILED` / `ERR_KAFKA_SEND_FAILED` / `ERR_PULSAR_CONSUMER_CREATE_FAILED` / `ERR_PULSAR_CLIENT_CREATE_FAILED` / `ERR_PULSAR_PRODUCER_CREATE_FAILED` 六码零引用（仅定义处）。`KafkaHelper.adapt = NopException.adapt`（send 失败为无类型适配）。
- **旧前缀引用面（grep 实证）**：全仓 src（java/yaml/properties/xml）零 i18n/properties 引用；测试全部经常量引用（`KafkaErrors.ERR_*.getErrorCode()`），无字面量断言——前缀迁移对测试透明。`target/` surefire 报告含旧前缀字符串（构建产物，非源码引用）。
- **D9 裁定**：直接迁移不做双前缀共存期（迁移提交内核对零引用）。
- **一致性门禁（审查 B1/M1 勘误）**：`ai-dev/tools/check-error-param-consistency.mjs` **存在且当前全仓 EXIT=1**（40 条 define-face 违规；其中 nop-message 族 9 条 = kafka 3 + pulsar 3 + debezium 3——六码 dead-define 的机器佐证）；`--module` 作用域形参不适用于 nop-message（要求 NopMetadataArgs.java，EXIT=2）。门禁重写为可执行断言：**全仓运行该工具，`nop-message/nop-message-kafka|pulsar|core` 三路径零 finding**（对输出 grep 验证）。Debezium 的 3 个 dead define 不在 F-55 六码范围 → Deferred But Adjudicated（见下）。旧前缀字面量 grep 需排除日志 key（src/main 约 25 处 `nop.message.kafka/pulsar.*` 为日志事件 key 非错误码——审查 M4）：精确 pattern = `PREFIX = "nop\.message\.` 零命中 + `ErrorCode.define` 首参核验。

## Goals

- **前缀迁移**：`KafkaErrors.PREFIX → "nop.err.message.kafka."`、`PulsarErrors.PREFIX → "nop.err.message.pulsar."`、`MessageCoreErrors.PREFIX = "nop.err.message.core."`（填充非空，见下）。**零改名**：常量名（ERR_*）全部保持。
- **F-55 六码处置（逐个裁定）**：
  - **接线**（有自然失败点）：`ERR_KAFKA_CONSUMER_CREATE_FAILED`（`doSubscribe` 的 `createConsumer` 窄包 try/catch）；`ERR_KAFKA_SEND_FAILED`（**两处**——审查 m1 裁定：callback 失败的 `KafkaHelper.adapt(exception)` 与同步失败 catch，非 NopException 的原始异常包为该码并 `.cause(e)` 保留链，NopException 原样透传）；`ERR_PULSAR_CLIENT_CREATE_FAILED`（`createClient` catch）；`ERR_PULSAR_PRODUCER_CREATE_FAILED`（`buildProducer` catch）；`ERR_PULSAR_CONSUMER_CREATE_FAILED`（`doSubscribe` 的 `builder.subscribe()` **窄包**——内层 catch 包该码，外层既有 catch 照旧资源清理，seek 失败不得误标，审查 m2）。
  - **删除**：`ERR_KAFKA_SERIALIZATION_FAILED`（无自然挂点——`encodeValue` 委托共享 codec，JSON 失败携带自身错误；保留即继续违反「禁止声明但从不引用」）。
- **MessageCoreErrors 填充**：`ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED`（Local `scheduleRedelivery` 捕获 `RejectedExecutionException` → WARN 留证）。**引用形式钉死（审查 M2）**：日志语句必须引用常量 `MessageCoreErrors.ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED.getErrorCode()`——纯日志 key 会让新码沦为 dead-define，F-40/F-55 完成判定自我挫败。**测试 seam（审查 M3）**：`redeliveryExecutor` 字段放宽为包私有 + 包私有 `setRedeliveryExecutorForTest`（对齐 producersForTest/createConsumer 先例），测试 shutdown 后注入再触发即得 RejectedExecutionException。
- **一致性门禁**：grep 断言旧前缀字面量（`"nop.message.kafka."` / `"nop.message.pulsar."` 作为错误码字符串）在 src 零残留；`check-error-param-consistency.mjs` 退出 0（如覆盖 message 模块）。

## Non-Goals

- 不改任何既有错误码的语义/触发条件（仅前缀与接线点）；除 `ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED` 外不新增错误码；不做 i18n 资源补齐（原无引用）；日志 key（`nop.message.kafka.*` 等非 ErrorCode 字符串）不在迁移范围。

## Scope

### In Scope

- `nop-message-kafka` / `nop-message-pulsar` / `nop-message-core`：三个 Errors 接口 + 五个接线点。
- design（D9 落实现态一行）、日志、roadmap WI26 状态行。

### Out Of Scope

- WI27-WI35；SPI 变更；其他模块错误码。

## Execution Plan

### Phase 1 - 前缀迁移与六码处置

Status: completed
Targets: `nop-message/nop-message-kafka`、`nop-message/nop-message-pulsar`、`nop-message/nop-message-core`
Audit Fixes: 审计 FAIL（agent_9b0691e5）抓出虚报勾选——测试③（Local REE）未实际编写、Pulsar subscribe 接线点无类型化断言。已补齐：TestLocalMessageService.testRedeliveryScheduleRejectionIsWarnedNotThrown（public seam 注入 shutdown scheduler）、TestPulsarServiceLifecycle.subscribeFailureCarriesTypedConsumerCreateCode；seam 改 public（跨包消费）。日志计数勘误。

- Item Types: `Fix`

- [x] 三接口前缀迁移（常量名不动）；`ERR_KAFKA_SERIALIZATION_FAILED` 删除
- [x] 五个接线点：Kafka createConsumer/sendAsync-catch；Pulsar createClient/buildProducer/subscribe
- [x] `MessageCoreErrors` 填充 `ERR_LOCAL_REDELIVERY_SCHEDULE_FAILED` 并接线 `scheduleRedelivery`
- [x] 测试① 五个接线点的类型化断言（Kafka：mock createConsumer 抛错 → `ERR_KAFKA_CONSUMER_CREATE_FAILED`；sendAsync 同步与 callback 原始异常 → `ERR_KAFKA_SEND_FAILED` 且 cause 保留；Pulsar：createClient/subscribe/buildProducer 抛错 → 对应码）
- [x] 测试迁移（审查 B2）：`producerSendSyncExceptionBecomesFailedFuture` 断言迁移为 `ERR_KAFKA_SEND_FAILED` + cause 链（旧 `instanceof IllegalStateException` 断言必被包装打破）
- [x] 测试③ Local：经包私有 seam shutdown executor → scheduleRedelivery → WARN 不抛（引用常量 getErrorCode）
- [x] 门禁：`grep -rn 'PREFIX = "nop\.message\.' --include="*.java"` 零命中（日志 key 排除——审查 M4）；`node ai-dev/tools/check-error-param-consistency.mjs` 全仓运行输出中 `nop-message/nop-message-kafka|pulsar|core` 三路径零 finding；design D9 实现态一行

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-message/nop-message-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-debezium -am` 退出 0 全绿（kafka 82/0F、pulsar 52/0F、core 23/0F。**偏离记录**：首轮回归在 nop-orm-eql 的 TestEqlAndChainDepth 失败——栈深度敏感的 watch-only 测试（提交 bce3fd8a 自裁定），与本计划零相关（错误码字符串 vs EQL 编译器），隔离+复跑均绿确认为负载抖动）
- [x] **新功能必有测试**：①②③ 全部存在且通过——① 是六码接线的直接证明；② 是前缀迁移透明性证明
- [x] **无静默跳过**：删除的 serialization 码为显式裁定（design 记录理由）；五个接线点均有类型化断言
- [x] **接线验证**：① 的 mock 抛错断言即运行时证据
- [x] Hard constraint：常量名零改名；`No Silent No-Op`——接线后的错误路径抛类型化错误而非裸 RuntimeException
- [x] Owner-doc：design D9 实现态一行已落档
- [x] `ai-dev/logs/2026/` 当日条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0

## Closure Gates

- [x] MSG-F-40/MSG-F-55 已修复且有测试证明（①-③；审计整改后 5/5 接线点均有类型化断言）
- [x] 独立子 agent closure audit 已完成并记录证据（agent_9b0691e5，FAIL→整改闭环）
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --module <m>` 四个模块逐一退出 0，嵌套路径 `nop-message/nop-message-core|kafka|pulsar|debezium`（审查 M5）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/26-wi26-error-code-prefix.md --strict` 退出码 0

## Deferred But Adjudicated

### Debezium 三个 dead-define（DebeziumErrors.java 的 CREATE_FAILED 族）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 不在 F-55 六码范围（roadmap 原文限定 Kafka 3 + Pulsar 3）；一致性门禁断言已 scoping 到 kafka/pulsar/core 三路径；debezium 模块的接线属机械迁移，登记后由后续模块巡检处置。
- Successor Required: `no`
- Successor Path: 无（登记于本节即唯一去向；若未来做仓库级错误码巡检再纳入）。

## Non-Blocking Follow-ups

- 仓库级错误码一致性 lint 工具（out-of-scope improvement：当前 grep 断言够用；若其他模块迁移可工具化）。

## Closure

Status Note: WI26 收口：D9 前缀迁移完成（三接口 nop.err.message.*、常量名零改名、全仓旧前缀字面量零残留 [日志 key 豁免 25 处经审计逐条核验]）；F-55 五码接线（Kafka consumer-create/send 双路径、Pulsar client/producer/consumer-create 窄包）+ serialization 删除；F-40 MessageCoreErrors 填充且 getErrorCode 引用形式落地。审计初判 FAIL 抓出虚报勾选（测试③未写、subscribe 断言缺失）——已补齐两个测试并勘误日志后闭环。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_9b0691e5-07a3-4287-8bdf-2eb9c544b48a（fresh session，亲跑全部验证命令；初判 FAIL 附 4 项整改）
- Evidence:
  - 审计确认项：前缀迁移彻底（三文件 PREFIX 实测）；23 处旧前缀用法全部为日志 key（豁免成立）；五码接线 grep 主源码引用实证；serialization 删除 diff 干净；一致性工具减量 6 恰吻合；包装惯例（调用点包装/窄包/透传）亲读证实；回归 82/52/23/26 全 0F 亲跑。
  - 整改项（全部完成）：①测试③ Local REE（public seam + shutdown 注入 + assertDoesNotThrow）已补并绿；②Pulsar subscribe 类型化断言（stub 抛 PulsarClientException → ERR_PULSAR_CONSUMER_CREATE_FAILED）已补并绿；③日志计数勘误（+4→+5，虚报声明已标注）；④Closure 回填后 checklist 复验 0。
  - 审计 Info-4（subscribe 窄包仅捕 PulsarClientException）登记为后续硬化观察，非回归。
  - 门禁：旧前缀 PREFIX grep 零命中；一致性工具 kafka/pulsar/core 三路径零 finding；scan-hollow 0；silent-wrong-result ×4 = 0；doc-links 0；checklist 复验 0。
  - Deferred 分类检查：debezium 三码 Deferred 经审计确认登记在案；无其他 in-scope live defect 被降级。

Follow-up:

- Debezium 三 dead-define（Deferred But Adjudicated 已登记）
- Pulsar subscribe 窄包扩展至 RuntimeException（硬化观察）
- 无 remaining plan-owned work
