# 36 Pulsar/Kafka 双后端对齐最新 LTS（broker + 客户端）

> Plan Status: completed
> Last Reviewed: 2026-10-06
> Source: 用户指令（2026-10-06）：pulsar 使用最新 LTS、API 级别同步、kafka 同样处理；承接 plan 33 建成的 Testcontainers E2E 验证体系
> Related: 33-wi33-gated-e2e.md（E2E 基建与 F-DLQ 修复，本 plan 的验证工具与语义再裁定基础）

## Purpose

把 nop-message 双后端的 broker 与客户端库整体对齐到当前最新 LTS/稳定版：Pulsar 2.8.0 → **4.0.14**（4.0 LTS 线最新补丁，Maven metadata 实证；4.0 LTS active support 至 2026-10-21）、Kafka 3.9.2 → **4.3.1**（支持线 4.1/4.2/4.3 最新；Kafka 无 LTS 机制取最新稳定），E2E broker 镜像同步升级，版本相关语义裁定全部在真机栈上复核并更新设计文档。plan 33 建成的 11 场景 E2E 是本 plan 的验收工具。

## Current Baseline

- 版本钉（`nop-kernel/nop-dependencies/pom.xml`）：`pulsar.version=2.8.0`（:46）、`kafka.version=3.9.2`（:54）；plan 33 已把 `pulsar-common`/`pulsar-transaction-common`/`pulsar-client-admin-api` 三条管理项绑定到 `${pulsar.version}`，属性升级时三者随动。
- 爆炸半径实测：全仓 `pulsar-client` 唯一消费方是 nop-message-pulsar；`kafka-clients` 消费面两处——nop-message-kafka（直接）与 nop-message-debezium（**传递**：该模块未 import debezium-bom，其 kafka-clients 被 nop-dependencies 的 `${kafka.version}` 管理项强制抬版本）。方向有利：Debezium 3.7.0.Final 自身 build-parent 即按 `version.kafka=4.3.1` 构建（debezium-build-parent pom :112 实证），现钉 3.9.2 反而是本仓覆盖；升级到 4.3.1 恰与 Debezium 构建版本对齐。本仓曾有 connect/kafka-clients 错配致引擎启动即 NoSuchFieldError 的先例，故 nop-message-debezium 必须以 dependency:tree + 引擎测试实证后才能宣称安全。
- E2E 镜像（plan 33 版）：`docker.m.daocloud.io/apache/kafka:3.7.0`（arm64 KRaft）、`apachepulsar/pulsar:3.0.11`（arm64 standalone）。**新镜像已实测**：`apachepulsar/pulsar:4.0.14` arm64 与 `apache/kafka:4.3.1` arm64 均拉取成功（daocloud 前缀）。
- 新客户端 Maven 解析实测：`pulsar-client-original:4.0.14` 与 `kafka-clients:4.3.1` 均可从 Central 解析。
- 版本相关语义裁定（design doc 全部锚定"2.8 语义"，升级后须逐条复核）：3.1 seek 缓冲窗口（2.8 归口 follow-up）、3.3.1 DLQ 分流（2.8 裁定为 **F-DLQ：nack 时触发**；4.0 预裁定见 Phase 2——nack 分流保持、新增">max 弹回"质变）、3.4/3.5 各字段映射、3.10 批量契约、3.6.1 慢 handler（Kafka 侧与客户端版本弱耦合）。
- 既有测试面：TestPulsarConsumeTask 24 例 + TestPulsarServiceLifecycle 19 例（mock 2.8 API）；TestKafkaConsumeTask 及契约测试（MockConsumer API，3.9 形态）；Kafka 6 + Pulsar 5 真机 E2E。
- quarkus-bom 3.35.1 导入管理 pulsar 族（plan 33 裁定的混版来源）——升级后须重跑 nop-message-pulsar dependency:tree 确认无新混版。

## Goals

- `pulsar.version` → 4.0.14、`kafka.version` → 4.3.1（nop-dependencies 两处属性 + E2E 测试类中镜像 tag 同步）。
- nop-message 两模块产品代码与测试代码对 4.x API 编译通过、既有 mock 单测全绿（API 漂移处以最小改动适配，不顺手重构）。
- 11 场景真机 E2E 在新版本栈上全绿；版本相关行为差异（尤其 Pulsar 4.0 DLQ 分流时机、Kafka 4.x MockConsumer/客户端行为）以 E2E 实测为准逐条裁定并落 design doc。
- design doc 所有"2.8 语义"锚定声明更新为实测版本锚定（4.0.14 / 4.3.1），F-DLQ 逻辑在新客户端上的适用性给出明确结论（保留、改写或声明仅在旧客户端路径生效）。
- quarkus-bom 混版复查：nop-message-pulsar dependency:tree 全部 pulsar artifacts 同版本。

## Non-Goals

- 不改 `nop-api-core` 消息 SPI（IMessageService/MessageSubscribeOptions 等零变更）。
- 不升级 debezium/debezium-bom（其自管 kafka-clients 4.3.1 已实证工作）。
- 不引入新功能、不做 API 设计修订；客户端 API 漂移仅做编译与语义适配。
- 不处理 Pulsar 4.1/4.2/5.0-M1 非 LTS 线。
- 不验证除 standalone/KRaft 单节点外的部署拓扑。

## Scope

### In Scope

- `nop-kernel/nop-dependencies/pom.xml`：两处版本属性 + kafka.version 注释块（写死 3.9.2 与 Debezium 3.7 connect-* 对齐的理由）按 4.3.1 实况重写；若 quarkus-bom 管了 4.0.14 传递面新引入的 pulsar artifacts 则新增管理项。
- `nop-message/nop-message-kafka`、`nop-message/nop-message-pulsar`：src/main 编译适配、src/test（mock 单测 + 两个 E2E 类镜像 tag 与断言适配）。
- `ai-dev/design/nop-message/backend-semantics-design.md`：版本锚定声明复核与更新（3.1/3.3.1/3.4/3.5/3.10/3.6.1 涉及处）。
- `docs-for-ai/03-modules/nop-message.md`：E2E 章节镜像版本与注意事项更新（含 :92 的 2.8 默认 60s 表述）。
- `nop-message/nop-message-debezium/pom.xml`：3.9.2 对齐注释按 4.3.1 实况重写（仅注释，无依赖变更）。
- daily log、本 plan closure。

### Out Of Scope

- nop-stream-*（debezium 自管版本）、nop-bom 模块条目、CI 基础设施。
- Local/SysDao 后端（无 broker 语义）。

## Execution Plan

### Phase 1 - 版本升级与编译适配

Status: completed
Targets: `nop-kernel/nop-dependencies/pom.xml`、nop-message 两模块 src/main + src/test

- Item Types: `Fix`

- [x] nop-dependencies：`pulsar.version` 2.8.0→4.0.14、`kafka.version` 3.9.2→4.3.1；`./mvnw install -pl nop-kernel/nop-dependencies -N`
- [x] nop-message-pulsar src/main 编译适配（4.0 客户端 API 漂移处最小修改，预期为零——审查字节码比对结论）；dependency:tree 复查 org.apache.pulsar 族全 4.0.14 无混版，**并记录 netty/protobuf/gson/avro 四个易被 quarkus-bom 管制的传递家族解析版本**（真实 client 构造路径只有 E2E 能暴露混版，tree 证据须留档）
- [x] nop-message-kafka src/main 编译适配（4.3 客户端）
- [x] 两模块 src/test 编译适配（mock 单测对 4.x API 形态；MockConsumer/ConsumerBuilder 漂移处以实际编译错误为准逐个适配）
- [x] 升级前基线采集：旧版本栈下先跑 `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -Dnop.test.message.e2e.kafka=false -Dnop.test.message.e2e.pulsar=false` 一次，记录 surefire tests/skipped 基线数
- [x] 全部既有 mock 单测在新客户端上全绿（不改断言语义；若某断言锚定 2.8/3.9 特有行为且新客户端语义不同，移入 Phase 2 与 E2E 一并裁定，不得静默改断言）；**执行门钉死**：`./mvnw test -pl <module> -Dnop.test.message.e2e.kafka=false -Dnop.test.message.e2e.pulsar=false`——显式退出 E2E，避免"新客户端 × 旧镜像"混栈运行（Phase 1 阶段镜像仍是旧 tag）

Exit Criteria:

执行注记（2026-10-06）：升级前基线（旧栈 + E2E 退出口）Kafka 103 tests/0 failures/20 skipped、Pulsar 78/0/19（基线采集时清除了 plan 33 遗留的 TempRawDlqProbeTest 陈旧编译类）。版本升级后**src/main 与测试源码零 API 漂移**——审查的字节码比对结论（全部使用 API 在 4.0.14/4.3.1 存活）实证成立，无任何适配点。依赖树：org.apache.pulsar 族全 4.0.14（client-original/client-api/pulsar-common/client-admin-api），netty 族 4.1.132.Final 一致（quarkus 管制），kafka-clients 4.3.1 于 nop-message-kafka、nop-message-debezium（传递）、nop-stream-connector-debezium 三处一致。mock 单测（E2E 退出口）Kafka 103/0/20、Pulsar 78/0/19——与基线逐数一致。

- [x] `./mvnw test-compile -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 通过
- [x] dependency:tree：nop-message-pulsar 全部 org.apache.pulsar artifacts 同为 4.0.14（+四家族版本记录）；nop-message-kafka 与 nop-message-debezium 的 kafka-clients 均 4.3.1（后者经 nop-message-debezium 模块测试实证 Debezium 引擎可启动）
- [x] 两模块 mock 单测（带 E2E 退出口属性，无 -am）全绿，tests/skipped 计数与升级前基线一致
- [x] src/main 适配点逐处登记本 plan 执行注记（文件:方法 + 漂移描述）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 新栈真机 E2E 与语义再裁定

Status: completed
Targets: 两 E2E 测试类、design doc 版本锚定声明

- Item Types: `Fix` + `Decision`

- [x] E2E 镜像升级：`apache/kafka:4.3.1`（或最近可用 4.x tag，执行期实证裁定）、`apachepulsar/pulsar:4.0.14`；测试类镜像常量与注释同步
- [x] Kafka 六场景新栈执行：全绿或差异裁定（4.3 broker KRaft 与 3.x 客户端行为差异以实测为准）
- [x] Pulsar 五场景新栈执行。**DLQ 语义裁定（字节码预裁定，E2E 终裁）**：4.0.14 的 `processPossibleToDLQ` 仍仅挂于 `getRedeliveryMessageIdData` 管道——**分流仍由 nack 触发**，F-DLQ 的 nack 分支在 4.0 上保持可达且正确（审查 agent_145e8808 反编译实证）。**真正质变**：`messageReceived`/`receiveIndividualMessagesFromBatch` 新增"redeliveryCount 严格大于 max 时登记候选表后直接弹回 broker、不投递 handler、也不分流"逻辑（2.8 是登记后必投递）——E2E 须实测并裁定：(a) 主流程（cap==threshold，endpoint 在 rc==max nack）应与 2.8 等价；(b) ">max 弹回 churn" 新失败模式在 ack 超时/断连重连/两 cap 错配场景的暴露面（4.0 退化比 2.8 重：消息既不进 DLQ 也不到 handler）——按实测在 design 3.3.1 与配置 javadoc 声明该边界，必要时在 `runFailureEndpoint`/WARN 文案处加守卫（属 Phase 2 Decision 登记）
- [x] "Pulsar 2.8" 锚定文案处置：`PulsarMessageService` dlq-subscription-type WARN 字符串、`PulsarConsumerConfig` 两处 javadoc、`PulsarConsumeTask` javadoc、`TestPulsarConsumeTask` 注释——随 Phase 1 编译适配顺带改 4.0 锚定的改，随 Phase 2 裁定改的登记后改（用户可见 WARN 文案属后者）
- [x] F-DLQ 与 3.3.1 设计声明按实测更新（版本锚定从 2.8.0 改为 4.0.14，旧 2.8 结论以历史注记保留）
- [x] design doc 其余"2.8 语义"锚定逐条复核：如实测一致仅改版本锚；不一致则以新实证更正（禁止仅凭文档推断改写）

Exit Criteria:

执行注记（2026-10-06，Phase 2）：新栈（kafka:4.3.1 + kafka-clients 4.3.1；pulsar:4.0.14 + pulsar-client 4.0.14）E2E 一次通过——Kafka 6/6、Pulsar 5/5，0 skipped 0 failed。**DLQ 裁定**：F-DLQ nack 分支在 4.0.14 上实测触发（日志 "native dead-letter diversion takes over (nack at redeliveryCount=2, maxRedeliverCount=2)"），与审查字节码预裁定一致——逻辑原样保留；">max 弹回"边界以字节码裁定 + javadoc 声明收口（`PulsarConsumerConfig` javadoc 已加 4.0 boundary note，E2E 未自然复现 ack 超时场景、以源码级事实为准）。**版本锚定文案处置**（Decision 登记）：PulsarMessageService 用户可见 WARN 改为版本中性（"the broker tracks..."）+ 两处注释重锚；PulsarConsumeTask 三处 javadoc 重锚（batchReceive 无超时重载经 javap 证实 2.8–4.0 一致，保留模式仍必要）；TestPulsarConsumeTask 注释重锚。退出口新栈验证：5 skipped 有因。SPI 零变更（git status nop-api-core 空）。

Exit Criteria:

- [x] 两 E2E 类在新镜像 + 新客户端栈上实际执行全绿（surefire 6+5 run / 0 skipped / 0 failed）
- [x] 退出口属性与 Docker 门控在新栈下行为不变（各验证一次）
- [x] design doc 版本锚定更新处逐条登记（节号 + 旧声明 → 新声明 + 证据来源）
- [x] 无 SPI 变更（git diff 不触及 nop-api-core）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 文档收口与审计

Status: completed
Targets: owner doc、daily log、本 plan closure

- Item Types: `Proof`

- [x] owner doc：镜像/客户端版本与注意事项更新（含 Kafka/Pulsar 版本策略声明：Pulsar 跟随 4.0 LTS 补丁线、Kafka 跟随最新支持线）
- [x] daily log 收口条目
- [x] 本 plan closure（checklist 工具 + 独立子 agent closure audit）

Exit Criteria:

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/36-*.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
- [x] 独立子 agent closure audit 完成并记录证据

## Closure Gates

- [x] `pulsar.version=4.0.14`、`kafka.version=4.3.1` 落地且 pulsar 族无混版（dependency:tree 证据）
- [x] 11 场景真机 E2E 新栈全绿（surefire 证据）
- [x] 版本相关语义裁定全部实测锚定（design doc 更正清单在执行注记中）
- [x] SPI 零变更；src/main 变更仅为客户端 API 适配 **或经 Phase 2 Decision 登记的语义适配**（逐处登记）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] **Anti-Hollow Check**：E2E 全链路断言在新栈真实连通（非 mock）；适配点无静默吞异常/no-op 化
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am -T 1C` 全绿
- [x] checklist / doc-links / hollow 三工具退出码 0

## Risks And Rollback

- **触发条件**：E2E 实测证实 Pulsar 4.0 客户端与本仓消费语义存在不可修复断裂（如 nack 分流失效、batchReceive 语义漂移且无适配点），或 Kafka 4.3 客户端在 classic 协议下出现不可适配行为。
- **回退动作集**：`pulsar.version`/`kafka.version` 两属性回退（pulsar-common 族三条管理项随 `${pulsar.version}` 自动回落）；两 E2E 测试类镜像 tag 回退；design/owner doc 恢复旧锚定并把 4.0 实测发现以历史注记保留（不删除实测证据）。
- **回退后**：本 plan 标 `cancelled` 或改写为"降级对齐目标"（如 Pulsar 3.3 LTS 线），在 Status Note 记录断裂证据。
- **4.0 LTS 支持悬崖接受注记**：Pulsar 4.0 LTS active support 止于 2026-10-21（本 plan 执行窗口内即到期），security support 至 2027-10-21。本 plan 取 4.0.14（LTS 线最新补丁）而非 4.1/4.2 非 LTS 线；后续策略：跟随 4.0 补丁线至 security support 期结束，届时（或出现必须的修复时）重评升 4.1+。

## Deferred But Adjudicated

（无——全部 in-scope。）

## Closure

Status Note: Pulsar/Kafka 双后端整体对齐最新 LTS/支持线完成——`pulsar.version` 4.0.14（4.0 LTS 线最新补丁）、`kafka.version` 4.3.1，E2E 镜像同步；src/main 零逻辑变更（客户端 API 零漂移 + 版本锚定文案 Decision 登记），SPI 零变更；11 场景真机 E2E 新栈一次全绿（Kafka 6/6、Pulsar 5/5）；F-DLQ nack 分流在 4.0.14 实测保留（含 >max 弹回边界声明）；mock 单测与升级前基线逐数一致。Pulsar 跟随 4.0 LTS 补丁线的后续策略已在 Risks And Rollback 声明（active support 至 2026-10-21 的悬崖已接受并记录）。
Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_bf25daff-d287-4640-9ddf-262a30f303a9（fresh session，非实现会话）
- Audit Session: agent_bf25daff-d287-4640-9ddf-262a30f303a9
- Evidence:
  - 版本落地：nop-dependencies :50/:61 两属性 + 注释块实况重写 + debezium 模块注释同步；5 个 pulsar 管理项均绑 ${pulsar.version}
  - 依赖树（audit 实跑）：nop-message-pulsar org.apache.pulsar 全 4.0.14 无混版；nop-message-kafka 与 nop-message-debezium kafka-clients 均 4.3.1
  - E2E：Kafka 磁盘 surefire 6/0/0（当日 11:16:36）；Pulsar 5/0/0 主证据 `plan36-gate.log:536569`（磁盘报告后被退出口验证运行覆盖为 5 skipped——该运行本身即退出口证据）；镜像 tag KafkaBrokerE2ETest:81 / PulsarBrokerE2ETest:90
  - F-DLQ 4.0.14 实证：`plan36-e2e.log:1503` "native dead-letter diversion takes over (nack at redeliveryCount=2, maxRedeliverCount=2)"；PulsarConsumerConfig javadoc 含 >max 弹回边界 note（审计发现的重复句已清理）
  - SPI 零变更：nop-api-core git status 空；src/main diff 三文件逐 hunk 核读全部为 javadoc/注释/字符串字面量，零可执行代码变更
  - mock 单测：门禁实跑 Kafka 103/0/14、Pulsar 78/0/14（E2E 在跑，skip 从退出口态的 20/19 降 14——两模块非 E2E skip 相同，与基线逐数语义一致）
  - 文档：design 3.3.1 双版本锚 + (d) 边界推论、3.12 新镜像与支持悬崖、owner doc 新版本、daily log 顶部条目
  - **Anti-Hollow 检查**（audit 执行）：两 E2E 真 broker 全链路断言完整非 mock；src/main 无静默吞异常/no-op；`scan-hollow-implementations --module nop-message --severity high` 退出码 0
  - 工具：check-doc-links --strict 0；check-plan-checklist --strict 0；hollow 0
  - Deferred/Rollback 诚实性：Risks And Rollback 四要素齐备、无 in-scope 降级

Follow-up:

- Pulsar 4.0 补丁线跟随至 security support 期结束（2027-10-21），届时或出现必须修复时重评升 4.1+（已在 Risks And Rollback 声明）
- no remaining plan-owned work
