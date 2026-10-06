# 33 WI33 gated 真实 broker E2E

> Plan Status: completed
> Last Reviewed: 2026-10-06
> Source: roadmap（WI33，deps M3✓；MSG-F-44）
> Related: 17-wi17-kafka-batch-capability.md（charter 承接：慢 handler 踢出场景）、31-wi31-module-cleanup-bom.md
> Supersession Note: 原 deferred 裁定（"当前环境无 broker 基础设施"）于 2026-10-06 失效——环境实测 Docker daemon 可用（linux/arm64 29.4.3）且 `testcontainers-bom` 2.0.5（既有 `nop-stream-connector-debezium` 先例）可正常解析，Kafka/Pulsar broker 容器经 `docker.m.daocloud.io/` 镜像前缀可拉取。门控机制由「系统属性 + 外部 broker」改为「Docker 可达性 assume + Testcontainers 容器」，详见 Goals。修订稿已过一轮独立对抗审查（agent_331fac8d，1 Blocker + 4 Major 已吸收：Pulsar 重投调参、DLQ Shared 订阅前提、design 归口编号更正、真 seek 场景补强、兼容性回退序）。

## Purpose

把 WI33 从"脚手架就位待启用"收口为"真实 broker 上实际执行且全绿"：Kafka/Pulsar 各一个 Testcontainers E2E 测试类，覆盖五类场景（重启恢复/毒消息/DLQ 投递/seek/请求-响应）+ charter 承接的慢 handler 踢出时序场景；Docker 不可达时可见 SKIP（不伪造通过）。本环境内真实执行，满足 roadmap「不得以跳过态标记 done」。

## Current Baseline

- gated E2E 脚手架已就位：`KafkaBrokerE2ETest` / `PulsarBrokerE2ETest`，`@EnabledIfSystemProperty` 门控（`nop.test.message.e2e.kafka` / `nop.test.message.e2e.pulsar`），五场景方法就位，默认跳过、编译通过。**但从未在真实 broker 上执行过**，且无慢 handler 踢出场景。
- Docker daemon 可用：`docker version` 返回 linux/arm64 29.4.3（Docker Desktop）。daemon **未配置 registry mirror**，Docker Hub 直连超时；`docker.m.daocloud.io/` 前缀拉取可用且 `--platform linux/arm64` 实测可取到 arm64 变体（kafka:3.7.0、pulsar:3.0.11 均已缓存并 smoke 启动成功）。
- Maven：`org.testcontainers:testcontainers-kafka:2.0.5` 与 `testcontainers-pulsar:2.0.5` 已实测 `dependency:get` 解析成功；版本经 `testcontainers-bom` 2.0.5 单点（既有先例：`nop-stream-connector-debezium/pom.xml`）。本仓 Testcontainers 先例模式：`DockerClientFactory.instance().isDockerAvailable()` + `Assumptions.assumeTrue`（SKIP 可见，不伪造通过），见 `TestDebeziumRealPostgresCdc`。
- 客户端版本：kafka-clients **3.9.2**、pulsar-client-original 2.8.0（`nop-kernel/nop-dependencies/pom.xml:46,54`）。broker 镜像选型（均实测拉起）：`docker.m.daocloud.io/apache/kafka:3.7.0`（**arm64**，KRaft 单节点 smoke 启动成功，与 3.9.2 客户端同代）、`docker.m.daocloud.io/apachepulsar/pulsar:3.0.11`（**arm64**，standalone smoke 启动成功「messaging service is ready」；3.0 LTS，2.8.0 客户端跨大版本连通性属"通常兼容无保证"——Phase 2 首个连通性失败即降级回退，见 Phase 2 回退序）。注：`apachepulsar/pulsar-all:2.11.4` 仅 amd64（镜像源单架构），弃用；Testcontainers 2.0.5 `PulsarContainer.DEFAULT_IMAGE_NAME = apachepulsar/pulsar`（字节码实测），构造器 assert 接受 `pulsar` 与 `pulsar-all` 两个名字，用 `asCompatibleSubstituteFor("apachepulsar/pulsar")` 声明即可。ryuk sidecar `testcontainers/ryuk:0.14.0` 已本地缓存（Hub 直连超时环境不卡启动）。
- Testcontainers API（2.0.5 实测 javap）：`org.testcontainers.kafka.KafkaContainer(DockerImageName)` + `getBootstrapServers()`；`org.testcontainers.pulsar.PulsarContainer(DockerImageName)` + `getPulsarBrokerUrl()`。
- design doc 三处明文将真机行为归口 WI33：Pulsar DLQ 真机投递（**3.3.1 推论(c)**）、seek 缓冲窗口（**3.1 末条**："2.8 的 seek 不清空已缓冲 incoming 队列"）、maxPollIntervalMs 慢 handler 踢出（**3.6.1**，charter 承接 WI17）。（首轮审查 F3 更正编号。）
- kafka 服务侧可配置：`KafkaClientConfig.bootstrapServers`（客户端）+ `KafkaConsumerConfig.maxPollIntervalMs`（service 级 defaultConsumerConfig，作用于该服务全部订阅，默认 300000，慢 handler 场景的踢出预算）；pulsar 服务侧：`PulsarClientConfig.serviceUrl` + `PulsarConsumerConfig.deadLetterMaxRedeliverCount/deadLetterTopic`（字段实测存在）。kafka 消费提交为手动 `commitSync()`（KafkaConsumeTask:422/725）；慢 handler 超预算后客户端心跳线程主动 LeaveGroup（非 broker 会话过期），单条路径 commit 失败被吞（log only，无显式 seek rewind），重投来自"该 offset 从未提交 + 重新入组后按 auto.offset.reset=earliest 回退"。

## Goals

- 两后端各一个 Testcontainers E2E 测试类（复用原类名 `KafkaBrokerE2ETest` / `PulsarBrokerE2ETest`，原系统属性门控整体替换为 Docker 可达性 assume 门控），六场景 @Test：重启恢复、毒消息 TerminateConsume、DLQ 投递、seek 重投递、请求-响应、慢 handler 踢出（Kafka 独有，charter 承接）。
- 门控语义：主门 = `DockerClientFactory.isDockerAvailable()`（不可达时 assume 跳过，SKIP 可见）；退出门 = `nop.test.message.e2e.kafka` / `nop.test.message.e2e.pulsar` 设为 `false` 时强制跳过（CI 无 Docker 场景的显式退出口）。属性名在测试类 javadoc、design doc、owner doc 三处一致声明。
- 镜像引用：默认 `docker.m.daocloud.io/` 前缀（本环境 daemon 无 mirror、Hub 直连超时的实测结论），经 `nop.test.message.e2e.registry` 属性可覆盖（直连 Hub 的环境设为空串即可）；镜像名以 `asCompatibleSubstituteFor("apache/kafka")` / `("apachepulsar/pulsar")` 声明兼容性。
- 本环境内 `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar` 两测试类实际执行且全绿（真实 broker，非 mock、非 SKIP）。

## Non-Goals

- 不建 CI broker 基础设施、不改 CI 流水线。
- 不验证多 broker 集群拓扑、跨机房容灾、SASL/TLS。
- 不覆盖 nop-stream-runtime 的端到端流处理链路（WI33 范围仅 nop-message 两后端服务层）。
- ~~不改产品代码（src/main 零变更）~~ **修订（2026-10-06 执行期）**：Pulsar DLQ 实测暴露一处已确认 live defect（见 Phase 2 Fix 项 F-DLQ）——roadmap 规则「已确认 live defect 只能属于 Fix，不得降级」+ 本 plan 的存在目的即真机验证；故允许一项最小产品修复，且仅限该 defect，其余 src/main 仍零变更。

## Scope

### In Scope

- `nop-message/nop-message-kafka/pom.xml`、`nop-message/nop-message-pulsar/pom.xml`：import `testcontainers-bom` 2.0.5 + test 作用域依赖（testcontainers、testcontainers-junit-jupiter、testcontainers-kafka / testcontainers-pulsar）。
- `KafkaBrokerE2ETest` / `PulsarBrokerE2ETest` 重写为 Testcontainers 版（六场景）。
- design doc（`backend-semantics-design.md` 三处归口点 + 门控声明）、owner doc（`docs-for-ai/03-modules/nop-message.md` 门控与启用方式）、daily log、roadmap 状态翻转（WI33→done，M5/M6 解锁核验后翻转）。

### Out Of Scope

- `nop-message-debezium`（其真实 PG CDC 已有 `TestDebeziumRealPostgresCdc` 先例覆盖）。
- `nop-message-core` Local 实现（无 broker 语义）。

## Execution Plan

### Phase 1 - Kafka Testcontainers E2E

Status: completed
Targets: `nop-message/nop-message-kafka/pom.xml`、`nop-message/nop-message-kafka/src/test/.../KafkaBrokerE2ETest.java`

- Item Types: `Proof`

- [x] pom：import testcontainers-bom 2.0.5 + 三个 test 依赖（对齐 debezium 先例写法）
- [x] 重写 `KafkaBrokerE2ETest`：Docker assume 门控 + 退出口属性 + `KafkaContainer`(apache/kafka:3.7.0) 静态生命周期 + 六场景（重启恢复 = 服务重建后同 groupId 从已提交位移续读不重不丢；毒消息 = TerminateConsume 后续消息继续消费、订阅仍活；DLQ = 配置 deadLetterTopic + attempt 上限后消息出现在 DLQ topic；seek = ConsumeLater 触发已提交位移 rewind 重投递且同一消息至少消费两次；请求-响应 = Acknowledge 回包落 ack topic；慢 handler = `maxPollIntervalMs≈2500`（须 > 空闲轮 pollTimeoutMs=1000）+ handler 首投睡眠 2 倍预算触发客户端 LeaveGroup 踢出，第二次投递快速返回——断言锚"同一消息最终被再次投递/服务恢复投递"，避免裸时序断言）
- [x] 本机实际执行全绿（非 SKIP）

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-kafka -am` 全绿且 `KafkaBrokerE2ETest` 六个 @Test 实际执行（surefire 报告可见 6 tests run，0 skipped）
- [x] Docker 停止时可验证 SKIP 可见（`-Dnop.test.message.e2e.kafka=false` 退出口生效，surefire 报告 skipped 有因）——以属性退出验证一次即可，不强求停 daemon
- [x] 无产品代码变更（`git diff --stat` 仅 pom + test + 文档）
- [x] No owner-doc update required（Phase 3 统一收口文档）——本 Phase 仅代码
- [x] `ai-dev/logs/` 对应日期条目已更新

执行注记（2026-10-06，Pulsar）：
1. 首轮 5/5 Errors——pulsar-common 被 quarkus-bom 导入管到 4.0.9 与 pulsar-client-original 2.8.0 混版，构造真实 PulsarClient 即 NoSuchMethodError（PulsarByteBufAllocator→bookkeeper allocator API 漂移）。全仓唯一 pulsar 消费方是本模块，mock 单测从未构造真实 client 故潜伏至今。修复：nop-dependencies 显式对齐 pulsar-common/pulsar-transaction-common/pulsar-client-admin-api 至 ${pulsar.version}=2.8.0（平台级 dependencyManagement 对齐，非本模块可解）。
2. 二轮 4/5 绿、DLQ 败——raw-client 探针实证 2.8.0 分流发生在 nack 时（rc≥max 的接收登记 + getRedeliveryMessageIdData 拦截改道），终结 ack 不改道（F-DLQ，见上）。修复后 5/5 全绿（Tests run: 5, Failures: 0, Skipped: 0），日志可见 "native dead-letter diversion takes over"。
3. 退出口验证：-Dnop.test.message.e2e.pulsar=false → 5 skipped（可见跳过）。
4. 全模块门禁：./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am -T 1C → BUILD SUCCESS（两模块 E2E 6+5 实跑全绿）。

执行注记（2026-10-06）：首轮实跑 4/6 败——`defaultConsumerConfig == null` 时服务不设 `auto.offset.reset`，kafka-clients 默认 `latest` 静默跳过消费者首次分配前发送的消息（`buildConsumerProps` :213-228 仅在 config 非空时落 managed keys）。产品行为本身符合"config 可选"契约，不判缺陷；E2E 修正为显式传入 `new KafkaConsumerConfig()`（默认 `autoOffsetReset=earliest`，与真实部署姿态一致）。修正后 6/6 全绿（Tests run: 6, Failures: 0, Skipped: 0）。

### Phase 2 - Pulsar Testcontainers E2E

Status: completed
Targets: `nop-message/nop-message-pulsar/pom.xml`、`nop-message/nop-message-pulsar/src/test/.../PulsarBrokerE2ETest.java`

- Item Types: `Proof`

- [x] pom：同 Phase 1（testcontainers-pulsar）
- [x] 重写 `PulsarBrokerE2ETest`：`PulsarContainer`(apachepulsar/pulsar:3.0.11 arm64) + 五场景（Pulsar 无 maxPollIntervalMs 踢出语义，慢 handler 场景不适用——Kafka 独有，与 design doc 3.6.1 归口径一致）。**重投调参为硬前提**（2.8 客户端 `negativeAckRedeliveryDelay` 默认 60s、`maxConsumeAttempts` 默认 10，不调参则断言必挂）：测试经 `PulsarConsumerConfig` 配 `negativeAckRedeliveryDelay≈100-200ms`、`maxConsumeAttempts=2~3`、`consumeErrorBackoffMs≈50ms`。场景定义：重启恢复（订阅重建后续读）；毒消息（TerminateConsume——Pulsar 侧裁定为直接 ack 终结、**不进 DLQ**，与 Kafka 分轨一致）；**DLQ = Shared/Key_Shared 订阅前提**（`subscriptionMode=LOAD_BALANCED` 或 `ORDERED_BY_KEY`，非 Shared 订阅配 DLQ 是 warn-once + no-op）+ `deadLetterMaxRedeliverCount == maxConsumeAttempts` 衔接（本地 attempt cap 命中的终结 ack 使计数入表、原生分流接管），订阅 DLQ topic 断言收到；**seek = 真 seek 语义**：INIT_SEEK(Earliest) 新订阅名在存量 backlog 上从头收 + ALWAYS_SEEK_TO_END 只收增量（真实 broker 仲裁订阅光标，而非 ConsumeLater 重投）；请求-响应 = Acknowledge 回包落 deriveAckTopic
- [x] **兼容性预检与回退序**（F5）：Phase 2 首个用例若客户端-协议握手失败（2.8 client ↔ 3.0 broker 跨大版本），降级镜像至 `apachepulsar/pulsar-all:2.11.4`（amd64，Rosetta 模拟运行，启动慢但可用），记 daily log——属基础设施选型调整，不算 plan 失败、不触发"停手另立 plan"条款；仅当两代镜像均无法完成五场景才停手上报
- [x] **F-DLQ（Fix，执行期裁定的新增项）**：raw-client 探针实证 2.8.0 客户端的 DLQ 分流发生在 **nack 时**（`getRedeliveryMessageIdData` 管道拦截重投请求），而非 design 3.3.1 原判定的"ack 时改道"——WI22 失败终点的终结 ack 使已配置的 `deadLetterMaxRedeliverCount` 策略成为静默 no-op（消息被 ack 后原生分流永不触发）。修复（三文件：PulsarConsumeTask.runFailureEndpoint 改道 + PulsarConsumerConfig javadoc 更正 + PulsarMessageService 包私有 defaultConsumerConfig 访问器）：`PulsarConsumeTask.runFailureEndpoint` 在 `deadLetterMaxRedeliverCount > 0 && message.getRedeliveryCount() >= deadLetterMaxRedeliverCount` 时改走 `negativeAcknowledge`（原生分流接管，失败终点仍派发 ConsumeFailure 回调 + WARN），否则保持终结 ack。同步更正 `PulsarConsumerConfig.deadLetterMaxRedeliverCount` javadoc 与 design 3.3.1 的机制描述。实证链：字节码（processPossibleToDLQ 唯一 caller = lambda$getRedeliveryMessageIdData$31）+ raw 探针（ack at rc≥2 → DLQ 无消息；nack at rc≥2 → DLQ 收到原文）
- [x] 本机实际执行全绿（非 SKIP）

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-pulsar -am` 全绿且 `PulsarBrokerE2ETest` 五个 @Test 实际执行（surefire 5 tests run，0 skipped）
- [x] 门控属性 `nop.test.message.e2e.pulsar` 退出口验证一次
- [x] src/main 变更仅限 F-DLQ（`PulsarConsumeTask` 分流改道 + `PulsarConsumerConfig` javadoc 更正 + `PulsarMessageService` 包私有 defaultConsumerConfig 访问器），其余产品代码零变更
- [x] No owner-doc update required（Phase 3 统一收口）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 文档收口与 roadmap 翻转

Status: completed
Targets: `ai-dev/design/nop-message/backend-semantics-design.md`、`docs-for-ai/03-modules/nop-message.md`、`ai-dev/backlog/nop-message-encapsulation-roadmap.md`、`ai-dev/logs/`

- Item Types: `Decision`、`Proof`

- [x] design doc：三处归口点补记实测结论——Pulsar DLQ 真机（3.3.1 推论(c)）、慢 handler 踢出（3.6.1）；seek 段（3.1）如实声明：INIT_SEEK/ALWAYS_SEEK_TO_END 真机仲裁已验证，**"2.8 seek 不清空已缓冲 incoming 队列"的微观窗口观测显式改归 Follow-up**（时序窗口非确定性，工程价值低于维护成本）；门控机制改为 Docker assume + 退出口属性的最终声明（属性名三处一致）
- [x] owner doc：E2E 启用方式章节改为 Testcontainers 语义（Docker 可达即执行；`nop.test.message.e2e.registry` 镜像前缀覆盖；`nop.test.message.e2e.kafka/pulsar=false` 退出口）
- [x] roadmap：WI33 `todo`→`done`（删除 deferred 注记，完成判定按新门控语义核验）；M5 解锁条件（WI30-WI33）核验后 `todo`→`done`；M6 解锁条件（M4+M5+WI34+WI35）核验后 `todo`→`done`；每次翻转跑 roadmap parser 校验
- [x] 本 plan 自身 closure（Exit Criteria/Closure Gates 全勾 + checklist 工具退出码 0）

Exit Criteria:

- [x] design/owner doc 中门控属性名三处一致且与测试类声明一致（grep 核验）
- [x] roadmap parser（`parseRoadmapMarkdown`）35 WI + 6 milestone 全解析，WI33/M5/M6 状态翻转无误
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

- [x] 两后端 E2E 在真实 broker 上实际执行全绿（surefire 报告 11 tests run，0 skipped，0 failed）
- [x] 门控语义落地：Docker 不可达 → 可见 SKIP；`nop.test.message.e2e.*=false` → 强制跳过；三处文档声明一致
- [x] src/main 变更仅限 F-DLQ 修复面（PulsarConsumeTask + PulsarConsumerConfig javadoc + PulsarMessageService 访问器），其余零变更；nop-dependencies 的 pulsar 版本对齐已声明
- [x] roadmap WI33/M5/M6 翻转 + parser 校验通过
- [x] 受影响 owner docs 已同步（design/owner doc/INDEX 如需）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] **Anti-Hollow Check**：audit 验证端到端路径真实连通——消息经真实 broker 从 send 到 handler 收到的全链路断言存在且非 mock（surefire 报告 + 测试源码核验）
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/33-wi33-gated-e2e.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0

## Deferred But Adjudicated

### Pulsar "seek 不清空已缓冲 incoming 队列" 微观窗口观测（design 3.1 归口项之一）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 该观测要求在 seek 调用瞬间恰好存在已取缓冲未消费条目，时序窗口非确定性、断言易碎；seek 的用户可见语义（INIT_SEEK/ALWAYS_SEEK_TO_END 仲裁、seekToTime 定位）已由本 plan 真机场景覆盖，窗口行为属 2.8 客户端内部实现细节，不改用户契约。
- Successor Required: `no`
- Successor Path: 无独立 plan；若 2.8 → 客户端升级后行为变化，在后续版本升级工作中顺带观测。

（其余：无——原 deferred 项"实际 broker 执行"即本 plan 主体，已被 Supersession Note 解锁。）

## Closure

Status Note: WI33 收口——Testcontainers 真实 broker E2E 落地并实际执行全绿（Kafka 6/6 + Pulsar 5/5，surefire 0 skipped 0 failed，当日时间戳），满足 roadmap「不得以跳过态标记 done」；deferred 裁定经 Supersession Note 解除（Docker + testcontainers-bom 2.0.5 实测可用）。执行期发现并修复两项已确认 live 缺陷：F-DLQ（Pulsar 2.8 DLQ 分流在 nack 时而非 ack 时触发，失败终点改走 negativeAcknowledge，raw 探针 + 字节码双重实证）与 nop-dependencies pulsar-common 族版本对齐（quarkus-bom 混版 4.0.9 使真实 client 构造即 NoSuchMethodError）。roadmap 35/35 WI + 6/6 milestone 全 done（parser 实跑验证）。M5/M6 达成。
Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_78f65290-6b4e-4e22-ae9b-30cfeed2911d（fresh session，非实现会话）
- Audit Session: agent_78f65290-6b4e-4e22-ae9b-30cfeed2911d
- Evidence:
  - Kafka surefire：Tests run 6 / Failures 0 / Skipped 0（mtime 当日 09:26:37）；Pulsar surefire：5 / 0 / 0（09:26:47）——真实执行非陈旧报告
  - @Test 计数对齐（Kafka 6、Pulsar 5）；退出口属性与 assumeTrue 逻辑源码核验；门控属性三处逐字一致（测试 javadoc / design 3.12 / owner doc）
  - src/main 变更面核验：仅 PulsarConsumeTask（F-DLQ 分流改道）、PulsarConsumerConfig（javadoc）、PulsarMessageService（包私有访问器）三文件；nop-dependencies 三条 ${pulsar.version} 管理项在位
  - roadmap：WI33=done（注记含 F-DLQ）、M5=done、M6=done；tools/mission-driver roadmap-check 实跑 35 WI + 6 milestone all done
  - **Anti-Hollow 检查**（audit 执行）：两端 E2E 均经 Testcontainers 容器 bootstrap 全链路（send → 真实 broker → handler 断言），非 mock；slowHandlerEviction 预算 2500ms/6000ms 超 2 倍；Pulsar DLQ 场景 LOAD_BALANCED + maxConsumeAttempts==deadLetterMaxRedeliverCount==2；`scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
  - F-DLQ 修复实证：nack 条件与两分支 + ConsumeFailure 公共派发路径源码核验；e2e 日志命中 "native dead-letter diversion takes over (nack at redeliveryCount=2, maxRedeliverCount=2)"
  - `check-plan-checklist.mjs --strict` 退出码 0；`check-doc-links.mjs --strict` 退出码 0
  - Deferred 分类检查：seek 微观窗口 watch-only residual 与 design 3.1 更正一致，无 in-scope live defect 被降级（F-DLQ 已 Fix 落地）
  - 审计附带的 4 条非阻塞残留已当场处置 3 条（design 3.3.1 :50 残留旧表述更正、PulsarConsumeTask 叠置 javadoc 去重、plan 修复面清单补 PulsarMessageService）；第 4 条（TempRawDlqProbeTest 的 target/ 编译残留）为未跟踪构建产物，不入库，无需处置

Follow-up:

- 「2.8 seek 不清空已缓冲 incoming 队列」微观窗口观测：watch-only residual（见 Deferred But Adjudicated），无 successor plan；客户端升级时顺带观测
- no remaining plan-owned work
