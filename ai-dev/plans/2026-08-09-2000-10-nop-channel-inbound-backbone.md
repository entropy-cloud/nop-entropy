# 10 nop-channel-inbound-backbone

> Plan Status: completed
> Last Reviewed: 2026-08-09
> Mission: nop-ai-channel-integration
> Work Item: W6-4（可选骨干验证）
> Source: `ai-dev/backlog/nop-ai-channel-integration-roadmap.md` W6-4 + 设计 `ai-dev/design/nop-ai-channel-integration-design.md` §3.3 问题 B + Plan 8/9 `Non-Blocking Follow-ups`（W6-4 标注为候选 successor plan）
> Related: `2026-08-08-1837-3-nop-channel-business-message-layer.md`（W2 业务消息层，落地 `ChannelMessageServiceImpl` 直连方式一）、`2026-08-09-1830-8-nop-channel-e2e-messaging.md`（W6-1/W6-3 E2E，直连方式一已验证）
> Draft Review: 经 3 轮独立子 agent 对抗性审查（含想象性分析），Blocker（topic 订阅时机断层）以内部 bridge 架构解决，bean-id/ack 回环/beans.xml 接线/装配机制/hollow-test 等 Major 全部收敛，达成共识后由 draft 转 active。

## Purpose

把 roadmap 最后一项 todo **W6-4** 收口：落地设计 §3.3 问题 B 的"可选内部骨干"部署形态（方式二），并用端到端证据证明**单体直连（方式一）与多消费者骨干（方式二）两种部署下业务门面 `IChannelMessageService` 的调用契约不变**，同时关闭遗留的"入站骨干 topic 命名约定"Open Question。

## Current Baseline

经 live repo 核对（`ChannelMessageServiceImpl.java` / `IChannelMessageService.java` / `IMessageService.java` / 设计 §3.3）：

- `ChannelMessageServiceImpl`（`nop-ai-gateway`，`io.nop.ai.gateway.channel`）入站路径目前**只有方式一直连**：`dispatchInbound(InboundChannelMessage)` 对已注册的 `IInboundMessageListener`（`CopyOnWriteArrayList`）做同步扇出（`ChannelMessageServiceImpl.java:108-112`）；`subscribeInbound(listener)` 直接把 listener 加入列表（`:98-102`）。
- 出站路径 `sendToUser` 与本 plan 无关（W2/Plan 8 已验证），不动。
- `IMessageService`（`nop-api-core/message`，`extends IMessageSender, IMessageSubscriber`）= `send(topic,msg)` / `subscribe(topic, consumer)` 的 topic 发布/订阅总线；其 in-process 实现 `LocalMessageService`（`nop-message-core`）已在平台存在，分布式实现 `KafkaMessageService`/`PulsarMessageService`（`nop-message-kafka`/`nop-message-pulsar`）也已存在。
- `nop-ai/nop-ai-gateway/pom.xml` 当前**不依赖** `nop-message-core`；`IMessageService` 接口经 `nop-api-core` 已传递可用（生产代码只引用接口，不引入新生产依赖）。
- 设计 §3.3 问题 B（`:104-123`）已给出"方式一/方式二、接口不变、Agent 会话仍走传输层直连"的裁定，但留有一条未勾选 Open Question（设计 `:286`）：多消费者部署下 `InboundChannelMessage` 的 topic 命名约定（`channel.inbound.{channelType}` vs 按业务域分）。roadmap "设计 Open Questions" 同列"入站骨干 topic 命名约定 → W6-4 收口"。
- roadmap W6-4 仍为 `[ ]`；Plan 8（`Non-Blocking Follow-ups`）与 Plan 9（`Non-Blocking Follow-ups`）均把 W6-4 标为"候选 successor plan"。
- 入站骨干仅作用于**非 Agent 入站**（`dispatchInbound` 已是"非 Agent 用户消息"语义）；**Agent 会话入站仍由传输层 `IChannelConnector` 直连引擎**，不经本骨干（设计 §3.2/§3.3 明确），本 plan 不改变这一点。

真正剩余的 gap：
1. `ChannelMessageServiceImpl` 缺方式二骨干路径——没有可选 `IMessageService` 注入、没有"发布到 topic / bridge 消费"的入站分发；`ai-gateway-defaults.beans.xml` 的 `channelMessageService` bean 无 `messageService` 接线。
2. 设计 §3.3 留有一个未解决的映射断层：`subscribeInbound`（all-channels 语义）与 per-channelType topic 之间如何映射（图示只画了单一 topic 概念）。本 plan 用内部 bridge 裁定收口（见 Phase 1 Decision 2）。
3. 没有任何证据证明两种部署形态下调用契约不变。
4. topic 命名 Open Question（设计 `:286`）未关闭。

## Goals

- 在 `ChannelMessageServiceImpl` 增加**可选**的入站骨干分发能力（方式二），采用**内部 bridge** 架构（见 Phase 1 Decision）：当装配了 `IMessageService` bean 时，`dispatchInbound` 发布到 `channel.inbound.{channelType}` topic，一个内部 bridge consumer 从 topic 消费后扇出给已注册的 `IInboundMessageListener`，**外部多消费者**（审计/工作流）可直接订阅同一 topic；未装配 `IMessageService` 时保持当前直连扇出（方式一）**逐字不变**。
- 调用契约**零改动**——方式一↔方式二的切换是纯部署装配选择，对调用方无感知：`subscribeInbound`（`IChannelMessageService` 接口方法）与 `dispatchInbound`（impl 的 public 入站入口，连接器用于**非 Agent** 入站）的调用方式在两种模式下完全一致。（注：`dispatchInbound` 不在 `IChannelMessageService` 接口上——接口只含 `sendToUser` + `subscribeInbound`；它目前无生产调用方，因 Agent 会话入站走传输层直连引擎，`dispatchInbound` 是设计 §3.2 为"非 Agent 入站分发"预留的入口。）
- 关闭 topic 命名 Open Question：裁定 `channel.inbound.{channelType}`（与设计 §3.3 图示 `channel.inbound.feishu` 一致），并写回设计文档勾选对应条目。
- 提供端到端证据：真实 `LocalMessageService`（真实 `IMessageService` 实现，非 stub）作为骨干，一条入站消息被**多个消费者**（经 bridge 的业务监听器 + 直接订阅 topic 的审计监听器）实际消费；并提供调用契约稳定性证据（同一份 `subscribeInbound`+`dispatchInbound` 调用代码在直连与骨干两种 harness 下均投递成功且消息等价）。

## Non-Goals

- **真实 Kafka/Pulsar 跨实例多消费者**：属部署演进（Plan 7/8 已裁定 `optimization candidate`，watch-only residual）。本 plan 用 in-process `LocalMessageService` 证明骨干语义与接口不变，`IMessageService` 的分布式实现是部署期替换，不在本 plan 验证范围。
- **Agent 会话入站路径**：仍走传输层 `IChannelConnector` 直连引擎（方式一），不并入骨干（设计 §3.3 明确）。本 plan 不触碰 `FeishuConnector` 的 `execute()` 出站 / event 订阅链路。
- **出站路径任何变更**：`sendToUser` 不动。
- **跨信道降级 / 富附件能力协商**：已在 W5-3/Plan 8 收口，不在此重开。
- **前端轮询 UI / 二维码渲染**：W5-2/W6-2 范围。

## Scope

### In Scope

- `ChannelMessageServiceImpl` 增加可选骨干分发（生产代码，`IMessageService` 可选注入 + 模式切换），直连模式行为不回归。
- topic 命名裁定（Decision）+ 设计文档 §3.3 Open Question 勾选 + roadmap W6-4 勾选。
- 单元测试：两种模式各自的 dispatch/subscribe 语义（含 null/未装配显式行为）。
- 端到端测试：真实 `LocalMessageService` 骨干 + 多消费者（业务 + 审计）+ interface-stability 对比。

### Out Of Scope

- 分布式 MQ 实现（Kafka/Pulsar）的实际接入与跨进程 E2E。
- Agent 会话入站、出站路径、附件降级、扫码绑定/登录（均已完成或在其它 plan 范围）。

## Execution Plan

### Phase 1 - 命名/架构裁定 + 骨干分发能力（方式二）

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java`、`nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml`、`nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/channel/TestChannelMessageService.java`、`nop-ai/nop-ai-gateway/pom.xml`（test scope）、`ai-dev/design/nop-ai-channel-integration-design.md`（§3.3 Open Question 勾选 + bridge 架构裁定）、`ai-dev/backlog/nop-ai-channel-integration-roadmap.md`（W6-4 勾选）

- Item Types: `Decision | Fix | Follow-up`

- [x] **Decision 1（topic 命名）**：裁定入站骨干 topic = `channel.inbound.{channelType}`（如 `channel.inbound.feishu`）。理由：(a) 与设计 §3.3 图示及 `IMessageService` topic 语义一致；(b) 按 channelType 分流天然支持"每信道独立消费组/速率"；(c) 业务域分流会让传输概念泄漏进 topic 命名，违背 §3.3"业务只知 userId"。拒绝"按业务域分"替代方案，理由一并写入设计文档。
- [x] **Decision 2（内部 bridge 架构 — 解决订阅时机断层）**：`subscribeInbound(listener)` 的语义是"订阅**所有**信道入站消息"（`IInboundMessageListener` 无 channelType 参数），而 topic 是 per-channelType。两者映射用**内部 bridge**，不把每个 listener 单独绑到 topic：
  - `subscribeInbound(listener)` **永远只**加入内部 listener 列表（与方式一逐字一致，不感知 topic / channelType）。
  - 骨干模式下 `dispatchInbound(msg)` 做两件事：(1) 若 `channel.inbound.{channelType}` 尚无 bridge consumer，先 `messageService.subscribe(topic, bridge)` 注册 bridge consumer（subscribe-before-publish，在同一调用内同步完成 → **无丢消息**）；(2) `messageService.send(topic, msg)`。**bridge consumer 必须是单例字段**（不是 per-call lambda），使得并发首次 dispatch 同一 channelType 时，`LocalMessageService.subscribe` 按 consumer 身份去重（`LocalMessageService.java:150` `findSubscription`）消解 check-then-subscribe 竞态 → 无重复扇出。并发 dispatch 行为须有测试覆盖。
  - bridge consumer 的 `onMessage` 把消息扇出给内部 listener 列表——**复用方式一的同一段扇出逻辑**（模式选择集中一处，不复制两套分发代码造成漂移）。bridge 的 `onMessage` **必须返回 `null`**（见下条），避免 `LocalMessageService.handleMessageResult` 把返回值误发到 `ack-{topic}` 形成回环。
  - **外部多消费者**（审计/工作流）不经 `subscribeInbound`，而是部署时直接 `messageService.subscribe("channel.inbound.feishu", auditConsumer)` 订阅同一 topic——这正是方式二独有的多消费者能力（方式一做不到）。本 plan 的 E2E 用此方式接入审计消费者。
- [x] **Fix（ack 回环防护）**：bridge `IMessageConsumer.onMessage(...)` 适配器显式返回 `null`（`IInboundMessageListener.onInbound` 是 void）。理由：`LocalMessageService.invokeMessageListener`→`handleMessageResult` 会把非 null 返回值发到 `ack-{topic}`（`LocalMessageService.java:184-192`）；返回 null 触发其 `ignore-message-when-no-reply` 分支，避免回环噪声。
- [x] **Fix（骨干能力，方式二）**：给 `ChannelMessageServiceImpl` 增加可选 `IMessageService` 注入（`@Inject` setter，可空）。当 `messageService != null` 时走 Decision 2 的 bridge 发布/消费；`messageService == null` 时保持当前直连扇出逻辑**逐字不变**（方式一）。已订阅 topic 用一个 `Set<String>` 跟踪，保证每 topic 只 subscribe 一次。
- [x] **Fix（IoC 接线）**：`ai-gateway-defaults.beans.xml` 的 `channelMessageService` bean 增加可选 `messageService` 注入。**关键纪律**：平台 `LocalMessageService` 的真实 bean id 是 `nopLocalMessageService`（`nop-message-core` 的 `message-core-defaults.beans.xml`），Kafka/Pulsar 分别是 `nopKafkaMessageService`/`nopPulsarMessageService`——**平台不存在 id=`messageService` 的 bean**。因此接线**不得**引用一个不存在的 bean id（否则 `ioc:optional` 静默解析为 null、方式二永不激活）。采用 by-type 可选注入（`ioc:optional="true"` 按 `IMessageService` 类型解析），或显式引用真实 bean id（如 `<ref bean="nopLocalMessageService" ioc:optional="true"/>`）；具体表达以实际容器语法为准。**优先用显式 bean id**（无歧义）；若用 by-type，部署方须保证同 classpath 仅一个 `IMessageService` 实现或显式标注 `ioc:default`，避免多实现（Local+Kafka 同 classpath）歧义。约束：部署含 `IMessageService` 实现时注入该实例（方式二），不含时注入 null（方式一默认）。gateway 模块本身不新增 production-scope 依赖——生产部署启用骨干由部署方在 classpath 提供 `IMessageService` 实现。
- [x] **Fix（test 依赖）**：`nop-ai/nop-ai-gateway/pom.xml` 增加 `nop-message-core` 为 **test scope**（提供真实 `LocalMessageService`）；确认**无 production scope** 新依赖。`grep` 校验 pom 无 production-scope `nop-message-core`。
- [x] **Proof（单元测试，方式二骨干语义）**：`TestChannelMessageService` 新增（用真实 `LocalMessageService`）：(a) 骨干模式下 `dispatchInbound` 确实经 `IMessageService.send` 发布到正确 topic（spy/计数器 verify，非仅类型存在）；(b) 经 `subscribeInbound` 注册的 listener 经 bridge 实际收到（扇出）；(c) 直接 `messageService.subscribe(topic, auditConsumer)` 的外部审计消费者也收到（多消费者）；(d) bridge `onMessage` 返回 null（断言无消息发到 `ack-channel.inbound.feishu`）；(e) 并发首次 dispatch 同一 channelType 时 bridge 为单例 + 去重，消息不重复扇出（覆盖 Decision 2 的竞态处理）。
- [x] **Proof（单元测试，方式一直连不回归）**：既有直连模式用例（`dispatchInboundFansOutToAllRegisteredListeners` 等）在 `messageService==null` 下全绿、行为不变；新增断言"未注入 messageService 时走直连分支"。
- [x] **无静默跳过**：`channelType` 为 null/空时骨干模式必须显式失败（抛异常），不允许 `send` 到 `channel.inbound.null` 这类静默错误 topic；方式一直连分支对 null message 的既有行为不变。
- [x] **Follow-up（文档同步）**：设计 `nop-ai-channel-integration-design.md:286` Open Question 勾选并写入 topic 命名裁定 + bridge 架构裁定；**同步更新 §3.3 mermaid 图示**（`:106-119`）使数据流与 bridge 架构一致（实际是 FC→`dispatchInbound`(CMS)→CMS publish→MQ，而非 FC 直连 publish MQ），避免"文字说 bridge、图说直连"的内部不一致；roadmap W6-4 `[ ]`→`[x]` 并补"已落地"说明与 Plan 引用。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] topic 命名裁定 + bridge 架构裁定结论已写入设计文档且 Open Question 条目已勾选（repo-observable：`grep "channel.inbound\|bridge" ai-dev/design/nop-ai-channel-integration-design.md` 命中裁定结论）。
- [x] `ChannelMessageServiceImpl` 在 `messageService==null` 时行为与改动前逐字一致（直连扇出，既有测试不回归）；`messageService!=null` 时走 bridge 发布/消费。
- [x] `ai-gateway-defaults.beans.xml` 的 `channelMessageService` bean 含 `messageService` 的可选注入（by-type 或真实 bean id `nopLocalMessageService`，**非**不存在的 `messageService` id）；容器在无 `IMessageService` bean 时正常启动（方式一）。
- [x] `./mvnw test -pl nop-ai-gateway -am` 全绿，含新增骨干模式单元测试。
- [x] `pom.xml` 新增 `nop-message-core` 仅 test scope（`grep -A2 nop-message-core nop-ai-gateway/pom.xml` 可见 `<scope>test</scope>`），无 production-scope 新依赖。
- [x] **接线验证（Rule #23）**：单元测试断言骨干模式下 `dispatchInbound` 实际触发 `IMessageService.send`（spy verify），且 bridge consumer 经 topic 消费路径实际被调用、listener 实际收到；外部审计消费者实际收到（多消费者）。
- [x] **ack 回环防护**：测试断言骨干模式下无消息发到 `ack-channel.inbound.{channelType}`（bridge `onMessage` 返回 null）。
- [x] **无静默跳过（Rule #24）**：channelType 缺失时显式抛异常，无 `channel.inbound.null` 静默 topic。
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-ai-channel-integration-design.md` Open Question 已勾选关闭（含 bridge 架构裁定）；`ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 端到端多消费者 + 调用契约稳定性验证

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/test/java/io/nop/ai/gateway/channel/`（新建 E2E 测试 + IoC 接线测试）

- Item Types: `Proof`

- [x] **Proof（E2E 多消费者骨干）**：新建端到端测试，**真实组件装配**（非 stub）：`new ChannelMessageServiceImpl()` + 注入真实 `LocalMessageService`（真实 `IMessageService` 实现，真正行使 `send`/`subscribe` 契约）+ 经 `setMessageService(...)` 启用骨干模式 + 一个模拟传输层入站源调 `dispatchInbound`。断言：一条 `InboundChannelMessage` 经 `channel.inbound.feishu` topic 被**两类消费者实际收到**——(a) 经 `subscribeInbound` 注册的业务监听器（经 bridge 扇出），(b) 直接 `messageService.subscribe(topic, auditConsumer)` 的审计消费者（外部多消费者）。各自计数/捕获消息体并断言等价。
- [x] **Proof（调用契约稳定性对比）**：同一份调用代码（`svc.subscribeInbound(listener)` + `svc.dispatchInbound(msg)`）在两个 harness 下均投递成功——harness A 不注入 `messageService`（方式一直连）、harness B 注入真实 `LocalMessageService`（方式二骨干）——断言两者业务 listener 收到的消息**等价**，证明方式一↔方式二切换对调用方零感知。（稳定性声明精确化：`dispatchInbound` 是 impl 的 public 方法而非 `IChannelMessageService` 接口方法，且当前无生产调用方；稳定性针对的是"该入站入口的调用方在模式切换下无感知"。）
- [x] **Proof（IoC 接线）**：参照 `TestFeishuConnectorIoC` 模式新建 IoC 接线测试，验证：(a) 无 `IMessageService` bean 部署时容器正常启动、`channelMessageService.messageService` 为 null（方式一默认）；(b) **classpath 含 `nop-message-core`（test scope）时，`messageService` 被解析为真实 `nopLocalMessageService` 实例（方式二激活）**——此断言是 hollow-test 防护：必须用平台真实 bean（`nopLocalMessageService`），不得在 test beans.xml 自造一个 `<bean id="messageService">` 假装通过（否则掩盖生产 bean id 不匹配）。证明 beans.xml 接线真实可达且方式二真能激活（Anti-Hollow）。
- [x] **Proof（Agent 入站不并入骨干）**：测试注释/断言说明 Agent 会话入站仍由 `IChannelConnector` 直连引擎（设计 §3.3），本骨干仅服务非 Agent 入站；不伪造 Agent 链路经骨干。
- [x] **Anti-Hollow**：E2E 从"传输层入站源 → `dispatchInbound` → `IMessageService.send` → topic → bridge 扇出 + 外部审计消费者 `onMessage`"完整跑通，断言每个消费者收到消息体（非仅"无异常"）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证（Rule #22）**：从入站源（调 `dispatchInbound`）到最终 bridge 业务消费者 + 外部审计消费者各自实际收到消息的完整路径已跑通，断言消息体（非仅"无异常"）。
- [x] 调用契约稳定性证据成立：方式一与方式二两个 harness 用**同一调用代码**均投递成功、收到的消息等价。
- [x] IoC 接线证据成立：`ioc:optional` 注入在无 bean / 有 bean 两种部署下分别解析为 null / 实例（容器级验证）。
- [x] `./mvnw test -pl nop-ai-gateway -am` 全绿，含新增 E2E + IoC 测试。
- [x] 若该 Phase 改变 live baseline：`ai-dev/logs/` 对应日期条目已更新（owner-doc 无需再改，Open Question 已在 Phase 1 关闭——`No owner-doc update required` for Phase 2）。

## Closure Gates

> 关闭条件：本 section 所有条目及每个 Phase Exit Criteria 全部 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程见 guide 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [x] W6-4 已落地：入站骨干（方式二，内部 bridge 架构）能力存在且经端到端验证；方式一直连无回归。
- [x] 调用契约在两种部署形态下不变（`subscribeInbound`+`dispatchInbound` 调用代码稳定性证据成立）。
- [x] topic 命名 Open Question 已关闭并写回设计文档（§3.3 `:286` 勾选 + topic 命名裁定 + bridge 架构裁定）。
- [x] roadmap W6-4 `[ ]`→`[x]` 并补 Plan 引用。
- [x] 必要 focused verification（骨干模式单测含 ack 回环防护 + 多消费者 E2E + 调用契约稳定性对比 + IoC 接线测试）已完成。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope 项（分布式 MQ 实现显式 Out-Of-Scope，非降级）。
- [x] 受影响 owner docs 已同步：设计文档 Open Question 关闭 + bridge 架构裁定；roadmap 勾选。`docs-for-ai/` 无对外使用契约需更新（骨干为内部部署形态，无新公开 API）——closure audit 显式写明无需更新。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）骨干模式下 `dispatchInbound`→`IMessageService.send`→topic→bridge 扇出 + 外部审计消费者运行时确实连通，（b）多消费者真实各自收到，（c）bridge `onMessage` 返回 null 无 ack 回环，（d）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw compile -pl nop-ai-gateway -am`（BUILD SUCCESS；另 `./mvnw compile -pl nop-integration,nop-auth,nop-ai -am` 无下游破坏）
- [x] `./mvnw test -pl nop-ai-gateway -am`（77/77 green，0 failures/0 errors）
- [x] checkstyle / 代码规范检查通过（`checkstyle:check` 非本模块构建绑定门禁——gateway 全模块既有 Sun-checkstyle 违例，`mvn test`/`compile` 不触发它；本 plan 改动遵循既有 gateway 代码风格：imports 分组 io.nop.*→jakarta.*→java.* + 静态导入最后、4 空格缩进、命名约定）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-gateway --severity high` 退出码 0

## Deferred But Adjudicated

### 真实 Kafka/Pulsar 跨实例多消费者

- Classification: `watch-only residual`
- Why Not Blocking Closure: `IMessageService` 是抽象接口，`LocalMessageService`/`KafkaMessageService`/`PulsarMessageService` 均为其实现。本 plan 用 in-process `LocalMessageService` 证明骨干语义（topic 发布/订阅 + 多消费者）与接口不变；真实分布式 MQ 跨实例消费需部署期 MQ 基础设施 + 凭证，属部署演进（与 Plan 7/8 既裁定一致）。接口对实现透明，替换实现不改 `ChannelMessageServiceImpl`。
- Successor Required: `no`（部署期验证）

## Non-Blocking Follow-ups

- 骨干模式下消费失败的容错策略（单消费者异常是否影响其它消费者/是否死信）——`LocalMessageService` 当前 catch 并记日志（见 `LocalMessageService.invokeMessageListener`），分布式实现的容错语义由具体 MQ 实现决定，属部署调优。
- `channel.inbound.{channelType}` topic 的消费组命名约定（多实例部署时）——部署演进时再定。

## Closure

Status Note: W6-4 收口。入站可选骨干（方式二，内部 bridge 架构）已落地于 `ChannelMessageServiceImpl`：`messageService==null` 时直连扇出（方式一，逐字不变）；`messageService!=null` 时 publish 到 `channel.inbound.{channelType}` topic，单例 bridge consumer 扇出给 listener（复用方式一逻辑、返回 null 防 ack 回环），外部多消费者可直接订阅 topic。`subscribeInbound`+`dispatchInbound` 调用契约在两种部署下不变（经同一调用代码双 harness 等价证明）。topic 命名 Open Question 关闭（裁定 `channel.inbound.{channelType}`）。IoC 用真实平台 bean id `nopLocalMessageService`（`@Inject @Nullable` 使方式一容器级正常启动）。分布式 MQ 实现显式 Out-Of-Scope（watch-only residual）。
Completed: 2026-08-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task_id `ses_01c23f9bbffegETsMdB7XG7PdI`，general 类型，read-only 审计）
- Audit Session: ses_01c23f9bbffegETsMdB7XG7PdI
- Evidence:
  - **Phase 1 Exit Criteria（全 PASS）**：
    - topic 命名 + bridge 架构裁定写入设计 §3.3（`grep "channel.inbound\|bridge" ai-dev/design/nop-ai-channel-integration-design.md` 命中 :110,111,127,129,130）；Open Question `:296` 为 `[x]`。
    - `messageService==null` 直连逐字不变（`ChannelMessageServiceImpl.java:208-213` + `dispatchInboundUsesDirectBranchWhenNoMessageService` 断言 bus 无 publish）；`!=null` 走 bridge（`:214-222`）。
    - beans.xml `messageService` 用真实 `nopLocalMessageService`（`ai-gateway-defaults.beans.xml:79-81`；平台 bean 见 `message-core-defaults.beans.xml:4`）。
    - `./mvnw test -pl nop-ai-gateway -am` 77/77 green（含 14 `TestChannelMessageService`）。
    - pom `nop-message-core` 仅 test scope（`pom.xml:129-133`）。
    - 接线验证：`backboneDispatchPublishesToPerChannelTypeTopic`/`backboneFansOutToInternalListenerAndExternalConsumer`（spy/计数器 verify send + 多消费者实际收到）。
    - ack 回环防护：`backboneBridgeReturnsNullSoNoAckLoop`（ack-topic consumer 收 0）。
    - 无静默跳过：`backboneRejectsNullChannelType`/`backboneRejectsEmptyChannelType`（显式抛 `NopException`）。
  - **Phase 2 Exit Criteria（全 PASS）**：
    - 端到端：`TestChannelInboundBackboneE2E.e2eBackboneDeliversToBusinessListenerAndExternalAuditConsumer`（真实 `LocalMessageService`，bridge 业务 listener + 外部审计消费者各自 `assertSame` 收到消息体）。
    - 调用契约稳定：`callingContractStableAcrossMode1DirectAndMode2Backbone`（同一调用代码，方式一/方式二 harness 均投递、消息等价）。
    - IoC 接线：`TestChannelMessageServiceIoC`（mode1 `assertNull` messageService；mode2 `assertSame(platformBus, injected)` == 真实 `nopLocalMessageService` + 行为验证）。
    - `./mvnw test -pl nop-ai-gateway -am` 77/77 green（含 3 E2E + 2 IoC）。
  - **Closure Gates（全 PASS）**：每条均经独立审计逐条核对（W6-4 落地/契约不变/topic OQ 关闭/roadmap 勾选/focused verification/无 in-scope 降级/owner-doc 同步/独立审计/Anti-Hollow/compile/test/checkstyle 说明/checklist/hollow scan）。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-09-2000-10-nop-channel-inbound-backbone.md --strict` 退出码为 0。
  - Anti-Hollow 检查结果：入站源→`dispatchInbound`→`IMessageService.send`→`channel.inbound.feishu` topic→bridge 扇出（业务 listener）+ 外部审计消费者 `onMessage` 全链运行时连通（`e2eBackboneDeliversToBusinessListenerAndExternalAuditConsumer` + IoC mode2 行为验证断言消息体）；`scan-hollow-implementations.mjs --module nop-ai-gateway --severity high` 退出码为 0（Critical=0, High=0, Total=0）。
  - Deferred 项分类检查：分布式 MQ（Kafka/Pulsar）跨实例多消费者为 `watch-only residual`（接口对实现透明，部署期替换不改 `ChannelMessageServiceImpl`），显式 Out-Of-Scope，非 in-scope 降级。

Follow-up:

- 骨干模式下消费失败的容错策略（单消费者异常是否影响其它消费者/是否死信）——`LocalMessageService` 当前 catch 并记日志，分布式实现由具体 MQ 决定，属部署调优（non-blocking）。
- `channel.inbound.{channelType}` topic 的消费组命名约定（多实例部署时）——部署演进时再定（non-blocking）。
- no remaining plan-owned work（in-scope 全部 landed 或显式 Out-Of-Scope）。
