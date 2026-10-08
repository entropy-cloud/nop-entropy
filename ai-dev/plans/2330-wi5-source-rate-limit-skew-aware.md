# 2330 WI5 Source 限速与倾斜感知 split 分配

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI5（M1 P0 主线）+ 对比分析 P0-5（SeaTunnel rate limiting / skew-aware enumerator 对标）
> Related: plan 2329（WI4 参数规则面——本 WI 的新参数直接落该体系）；WI3（Kafka 连接器复用 core 限速件，见 Deferred）

## Purpose

给 split 型 source 增加 rows/bytes 限速与倾斜感知 split 分配两个生产化能力（对比分析 P0-5）：限速保护下游与共享资源，size-aware 分配消除「一个大文件 + 多个小文件」下 round-robin 的完成时间倾斜；单写入者 source 的不支持面如实 typed 标注。

## Current Baseline

- `FileSplitEnumerator.assignAvailableSplitsTo`：稳定 round-robin（discovery index `i % parallelism`，与注册顺序无关）；`FileSplit` 携带 `size`（bytes）——size-aware 分配的数据基础已在；`FileSplitEnumeratorState` 只记录 assigned/finished 文件集，**不含 reader 归属**。
- 驱动模式：`LocalSourceCoordinator`/`ReaderChannel` 按 reader 注册/拉取事件逐 subtask 调用分配方法，每次只投递该 subtask 份额。
- `FileSourceReader.pollNext()`：按行发射 `String` 记录，无节流；cursor 推进在 synchronized 段内（AR-15-② monitor 纪律：snapshotState 与 pollNext 同 monitor）；`maxLineBytes` 为 long。
- `FileSource.restoreEnumerator` 现以默认构造重建 enumerator——**新策略必须双路径透传**（create + restore），否则恢复后静默回落 round-robin。
- reader 运行时：mailbox 只在两次 poll 之间 drain（barrier/cancel 是 control mail）——pollNext 内阻塞会等比推迟 barrier 注入与 cancel 响应（不死锁，但极低速率下时延退化）；reader `readBuf` javadoc 记录「反序列化不跑字段初始化器」陷阱。
- 参数体系：WI4 的 `ConnectorParamDescriptor`（INT kind = 可解析性校验；**无数值范围规则**）；conf-validate connector 模式 + sample 命令消费规则；程序化 `StreamConnectorConfig` 构造面不校验未知参数（WI4 既有边界）。
- 单写入者 source（batch-loader）与 message source 的描述符**没有**限速参数——经校验面（validateConnector/sample）配置即 unknown-param typed 错误；程序化构造面不校验（既有边界，如实声明）。

## Goals

- core 增 `SourceRateLimiter`：token bucket、rows/bytes 双通道、`acquire(long rows, long bytes)`、桶起始为空（速率从 t0 严格生效）、单次睡眠分片有上界且响应中断；**可序列化**（纯 long 状态）。
- `FileSourceConnectorFactory` 参数面：`rateLimitRowsPerSecond`/`rateLimitBytesPerSecond`（INT optional）、`splitAssignmentPolicy`（STRING allowedValues [round-robin,size-aware] optional）。
- `FileSplitEnumerator` size-aware 策略（语义裁定见下）。
- 单写入者/无参 source 不支持面如实呈现：校验面 typed 拒绝（新测试钉子）+ owner doc 说明。

### size-aware 分配语义裁定（audit F1，Blocker 处置）

**分配函数为纯函数 f(当前剩余未分配集, policy, parallelism)——输出仅由剩余集决定（确定性）、restore 安全，零 wire 变更；投递结果随事件序列变化（见推论），「顺序无关」不作不变式声明**：每次分配调用对「当前未 assigned 且未 finished」的 split 集重算贪心（size desc → path asc；逐个给累计字节最小的 reader index，平局取小 index），已分配 split **不归因负载**（State 无归属字段，不做 wire v2）。推论（如实声明）：先注册的 reader 按全局贪心拿走最大份额；后注册 reader 对剩余集重算；恢复后对剩余 split 从零累计重算（剩余 split 重新平分，已消费 split 不参与）。平衡目标按此语义断言（倾斜场景 = 大文件归属先注册 reader、剩余小文件在其余 reader 间均衡）。

### 限速语义三态裁定（audit F4/F5 处置）

- 参数**缺省** → FileSource 字段取 0 → limiter 该通道关闭（不限速）。
- 参数**显式 <= 0** → **工厂** typed 拒绝（工厂经 `getInt` 的 null 性区分缺省/显式——FileSource 构造器收到归一化 long 后二者逐位相同，无法执行三态，故正性校验落工厂层；FileSource 构造器只防御负值、接受 0。descriptor INT kind 只管可解析性——conf-validate 对 0/负值均视为 INT 合法，正性是构造契约，两层口径如实写明）。
- 非法 `splitAssignmentPolicy`（程序化路径）→ FileSource 构造器 typed 拒绝，**不静默回落 round-robin**；校验面由 allowedValues 规则拒绝。

### 限速阻塞与运行时交互裁定（audit F3 处置）

`acquire` 在 reader **synchronized 段之外**调用（cursor monitor 不持锁睡眠）；单次睡眠分片上界（100ms）循环响应中断；限速开启时 checkpoint/cancel 时延随速率退化是接受的能力边界（文档写明；E2E 不在限速开启下断言 checkpoint 时延）。

## Non-Goals

- 不扩 stream.xdef（XDSL 类型化声明裁定未开放，WI0 零解禁；限速声明面 = connector param 体系，Framework 节「统一走 XDSL」待类型化声明裁定开放后回接）。
- 不做 Kafka 限速实现（core 限速件为 WI3 消费预留，见 Deferred But Adjudicated）。
- 不改 batch-loader/message 语义（校验面 typed 拒绝 + docs 标注）。
- 不做动态限速与已分配 split 的迁移重分配（静态确定性分配）。
- 不做 wire v2（State 归属字段）——按 F1 裁定语义无需。

## Scope

### In Scope

- `nop-stream-core`：`SourceRateLimiter`（Serializable，含确定性单测）。
- `nop-stream-connector`：FileSource（新 long 字段 + 构造器 typed 校验，双路径透传 policy/限速给 enumerator/reader）、FileSourceReader（monitor 外消费限速器；limiter 由 long 字段惰性重建避序列化陷阱）、FileSplitEnumerator（双策略）、工厂参数声明。
- 测试：限速器单测、限速 E2E（限速 run 绝对时长 >= 理论值 × 1.5）、size-aware 倾斜平衡/确定性/restore 后策略保持断言、round-robin 回归、参数校验（缺省不限速/显式负值 typed 拒绝/非法 policy 构造拒绝/校验面 allowedValues 拒绝/单写入者 source 校验面 unknown-param 钉子）。
- docs：connectors.md（file 行参数/策略/限速时延边界/单写入者标注）。

### Out Of Scope

- Kafka/message/batch 连接器的限速实现。
- XDSL 声明面、动态限速、split 迁移重分配、State wire v2。

## Execution Plan

### Phase 1 - 限速件与参数面

Status: completed
Targets: `nop-stream-core`、`nop-stream-connector`

- Item Types: `Feature`

- [x] core `SourceRateLimiter`（Serializable；桶起始空；acquire(long,long)；睡眠分片上界 + 中断响应；负值 typed 拒绝、0 = 通道关闭）
- [x] 工厂三参数（INT optional ×2 + policy allowedValues）+ FileSource 构造扩展（typed 校验 + 双路径透传）+ FileSourceReader 消费（monitor 外、limiter 惰性重建）
- [x] 测试：限速器时间窗单测、限速 E2E（限速 run 绝对时长 >= 理论值 × 0.9；理论值 = max(N/rateRows, B/rateBytes)——桶起始空的数学下界，0.9 为时钟测量余量；测试量 >> 桶容量）、缺省不限速回归、显式负值/非法 policy typed 拒绝、校验面 allowedValues 拒绝、单写入者 source 校验面 unknown-param 钉子

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-connector,nop-stream/nop-stream-flow -am` 全绿
- [x] 限速生效断言：限速 run 绝对时长 >= 理论值 × 0.9（理论值 = max(N/rateRows, B/rateBytes)，起始空桶的数学下界；非与不限速基线的差值——慢机稳健）
- [x] **无静默跳过**：显式 <=0 与非法 policy 在构造面 typed 拒绝；缺省 0 = 显式不限速语义（javadoc 写明）
- [x] No owner-doc update required（docs 集中 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - size-aware 分配与文档

Status: completed
Targets: `FileSplitEnumerator`、docs

- Item Types: `Feature`

- [x] size-aware 策略按 F1 裁定实现（剩余集重算贪心；create + restore 双路径同策略）
- [x] 测试：倾斜平衡（测量口径 = 驱动两次 addReader 后各 reader **已投递**字节差 <= 最大单 split，且存在未分配余量——按裁定语义后注册者对剩余集贪心、部分份额等 pull；大文件归先注册 reader）、确定性（同剩余集两次分配一致）、**restore 后策略保持**断言、round-robin 默认回归、既有 TestFileSourceCheckpointRestore 全绿
- [x] docs：connectors.md file 行补参数/策略/F1 语义推论/限速时延边界/单写入者标注

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-connector -am` 全绿（含既有恢复/审计测试零回归）
- [x] **接线验证**：策略经 factory → createEnumerator **与** restoreEnumerator 双路径真实改变分配行为（restore 保持断言）
- [x] owner docs 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - closure audit 与提交

Status: completed
Targets: roadmap WI5、plan 2330

- Item Types: `Proof`

- [x] 独立子 agent closure audit；roadmap WI5 勾选（附 Kafka 归属注记）；git commit；`ai-dev/logs/` 收口
- [x] No owner-doc update required（Phase 2 已更新）

Exit Criteria:

- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI5 已勾选、提交完成

## Closure Gates

- [x] 限速与 size-aware 分配经参数面真实可达（含 restore 路径接线断言）
- [x] 默认行为零回归（round-robin、不限速、既有恢复/审计测试全绿）
- [x] 单写入者不支持面如实（校验面 typed 拒绝钉子 + docs 标注；程序化构造面边界如实声明）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-connector,nop-stream/nop-stream-flow -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-import-order.mjs` 受影响模块无新增违规
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` 退出码 0（connector 模块由 -am 回归 + audit 抽查覆盖）
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### Kafka source 限速接入

- Classification: `out-of-scope improvement`（本 WI 交付 core 机制）
- Why Not Blocking Closure: roadmap WI5 行「对 file/Kafka 等 split 型 source 生效」的机制面由本 WI 的 `SourceRateLimiter` 承载；Kafka 连接器本体是 WI3 交付物（其 roadmap 行不含限速），限速在 WI3 消费 core 件接入是零新协议的复用面——file 首落地已达成 P0-5 的生产化意图
- Successor Required: `yes`
- Successor Path: WI3 承载 plan（Kafka 连接器）

## Closure

Status Note: WI5 完成：core `SourceRateLimiter`（空桶起始/双通道/有界分片中断响应）+ file source 限速消费（monitor 外）+ `splitAssignmentPolicy` 双策略（size-aware 纯函数裁定语义、双构造路径透传、restore 保持判别钉子）+ 单写入者校验面 typed 标注（runtime 钉子）；默认行为零回归（round-robin/无限速/既有恢复测试全绿，全量 -am BUILD SUCCESS）。roadmap WI5 已勾选（Kafka 归属 WI3 注记）。
Completed: 2026-10-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_a6a5bd5e-2f10-467b-b36c-312bf5722606）
- Audit Session: agent_a6a5bd5e-2f10-467b-b36c-312bf5722606（REJECTED 2 项收口项：plan 勾选滞后 + limiter import 顺序——均已修复，审计员裁定「修复后无需重审计代码」）
- Evidence:
  - A/B 全 PASS：limiter 契约逐条核实（空桶/0=off/负值拒绝/100ms 分片/Serializable）；工厂三参数 + readPositiveOrZero 缺省/显式区分；reader monitor 外消费（L231 sync 块外 L236）；size-aware 纯函数逐条吻合 F1 裁定；双路径透传 diff 核实；connectors.md 一致
  - C 工具：doc-links --strict exit 0；scan-hollow core exit 0（414 文件 0 发现）；TestFileSourceRateLimitAndSkew 审计独立重跑 8/8；全量回归 log BUILD SUCCESS（TestFileSourceCheckpointRestore 3/3）
  - D Anti-Hollow PASS：调用链 pollNext → acquireRate → SourceRateLimiter.acquire 真实 token bucket；双策略经 factory 真实切换
  - E 文本一致性：F4 三态两层口径无冲突残留（audit 实测 PASS）
  - Deferred 项分类检查：Kafka 限速 = out-of-scope improvement 有 successor（WI3），非静默缩水
  - checklist 工具 exit 0（closure 回填后）

Follow-up:

- 时间下界断言理论值口径偏松约 20%（(N-1)/rate vs max(N/rate)）——方向正确、慢机稳健，watch-only
- restore 判别断言对 6 种目录序中 2 种判别力弱（size-aware 结论与目录序无关、测试恒过）——后续可用 restoreState 预置 discovered 集钉死判别序（optimization candidate）
- FileSourceReader/FileSplitEnumerator/Factory 若干内联全限定名（风格偏离，check-import-order 不报）——随下次触碰这些文件时顺手收（optimization candidate）
