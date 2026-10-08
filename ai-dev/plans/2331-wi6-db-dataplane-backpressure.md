# 2331 WI6 DB 数据面生产者背压（credit 式分片限流）

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI6（M1 P0 主线）+ 对比分析 P0-6/Hazelcast 对照行 + WI0 裁定记录 P0-6（DB 通道整形，非 A 类禁入面）
> Related: `ai-dev/design/nop-stream/dataplane-transport-design.md`（D2 有界等待语义、send 侧表行数缺口留给本 WI）；WI16（DB backlog gauge 归属）

## Purpose

给 DB（SysDao）数据面通道加生产者背压：生产者发送前检查每数据 topic 的未消费积压深度，超上限则有界等待（超时 typed 失败，复用 D2 口径），替代「消费滞后 → `nop_sys_event` 表无限增长」的无界写入。范围严格限定 DB 通道整形（WI0 P0-6 裁定），不触碰 vision §四「credit 式全量跨 JVM 数据面」禁入面。

## Current Baseline（研究 + audit 双重核实）

- 生产者链路：`RemoteResultPartition.write` → 编码 → `sendUnderLock`（sendLock 内同步 send）——类注释明示 bypass 队列与 IBufferPool，producer-side bound 留给后端（G53）。plan 2279 Q2 已把编码移出锁（慢 backend 不钉 monitor）；锁内 finished 再检查承载 EOS 排序契约。
- DB 后端 = `SysDaoMessageService`（nop-sys-dao）：同步 JDBC insert 落 `nop_sys_event`；消费轮询置 PROCESSED **留痕不删**（7 天清理）——无界 = 表行数随消费滞后无限增长。`eventId` 全局递增；每 topic `WAITING+CLAIMED` COUNT = 积压深度（索引 `IX_NOP_SYS_EVENT_TOPIC_STATUS` 覆盖）。
- **模块方向**：sys-dao 生产依赖仅 api-core/orm/cluster-core/message-core；runtime 是其 **test** scope（pom 注释钉死「production deps remain clean」）——接口归属必须两模块共同可达且零新增依赖方向。
- **adapter 包装**：embedded/rpc 主路径把裸 messageService 包成 `DataPlaneMessageServiceAdapter` 再喂 builder——能力识别必须解包 delegate，否则配了门控静默空转。
- **配置现实**：`EdgeConfig` 生产来源一律 `defaultConfig()`；XDSL `<edge>` 属性被 P1-XDSL-5 fail-fast（`ERR_STREAM_EDGE_ATTR_UNSUPPORTED`）——per-edge 字段当前**无写入路径**。
- 消费侧本地队列有界等待 10s→typed 溢出（D2）；`FlowControlPolicy` 仅剩 BLOCKING_QUEUE（Stage 28）；channel.queue.size gauge 不含 DB 表积压；`PollingJdbcMessageService`（runtime **main sources**，随 jar 发布）行不删除——其 countRows 是累计数**不是积压信号**（跨 JVM 游标在消费方内存）。
- `RemoteResultPartition` 5 参构造调用点 15+（含 nop-benchmark-stream 主源码）——扩构造必须保旧重载。
- 测试：`TestDataPlaneSysDaoBackendE2E`（nop-sys-dao，手动 drain 驱动消费——「暂停」= 停 drain，「恢复」= 恢复 drain，零新机制）；`TestRemoteResultPartition` 等 runtime 单测。

## Goals

- `ChannelBacklogProbe` 能力接口落 **nop-message-core**（`io.nop.message.core`——sys-dao 与 runtime 均已 compile 依赖，零新增依赖方向；不选 api-core 避开 plan-first 保护面）：`long backlog(String topic)`。
- `SysDaoMessageService implements ChannelBacklogProbe`（COUNT 查询）。**harness 不实现 probe**（countRows 非积压信号，M3 裁定）——背压 E2E 锚定 SysDao 后端。
- `RemoteResultPartition` 门控（裁定见「门控语义裁定」节）+ **保留 5 参构造**（probe=null），新增带 probe/limit 重载（M4——nop-benchmark-stream 不破）。
- 配置面（B2 裁定 = 方案 b）：job 级全局配置 `nop.stream.data.db.backlog.limit`（经注入配置读取，缺省 0 = 关闭）；**不动 xdef/EdgeConfig 模型面**（生成管线保护 + P1-XDSL-5 既有裁定不碰）。per-edge 细分记 Follow-up。
- 装配规格（B1）：`RemoteGraphExecutionPlanBuilder` 构造 partition 处——对持有的 messageService **先解包**（instanceof DataPlaneMessageServiceAdapter → getDelegate()，adapter 增 delegate 访问器）再 `instanceof ChannelBacklogProbe`；probe 非空且配置值 > 0 才启用。`sameGateConfig` 两份（GraphExecutionPlan/RemoteGraphExecutionPlanBuilder）不涉新 per-edge 字段（全局配置不进 EdgeConfig），零改动。
- 观测：`nop.stream.io.db.backlog` gauge（门控启用时注册；取值复用门控的带 TTL 缓存值）。
- 查询节流（M2）：backlog 值带 TTL 缓存（默认 200ms）——通行态不逐条 COUNT；等待态按分片复用缓存值；gauge 同源。

### 门控语义裁定（audit M1）

- 等待在 **sendLock 之外**（不持锁睡眠/查询——plan 2279 Q2 契约保持）；进入 sendLock 后依赖既有 finished 再检查拒绝「晚于 EOS 的写入」（close 先行不被门控阻塞放大）。
- **barrier/watermark 与数据同门（in-band）**：`RecordWriter.emitBarrier` 走同一 `partition.write`，持续过载下 barrier 排队属预期背压（与 Flink 对齐屏障同型）；**持续过载的显式语义 = 门控 typed 失败 → task 失败 → 作业重启重放**（checkpoint timeout 600s >> 门控 10s，先触发门控失败）。豁免 barrier 会引入越过已门控数据的乱序，不做。
- 等待分片可中断（write 声明 InterruptedException，任务取消依赖）。
- **cold-start 竞态裁定**：部署窗口内下游未订阅、WAITING 行全部计入 backlog，突发可触发门控失败重启——与上条同语义（可见失败 + 重启收敛），docs 写明「启用门控时应先起消费者或接受启动窗口重试」。

### scope 内测试树裁定（M3）

`PollingJdbcMessageService` **不实现 probe**（行不删，countRows 非积压）；背压 E2E 唯一锚点 = SysDao 后端 E2E；门控单元测试用 fake probe（零 DB 依赖）。

## Non-Goals

- 不复活 `FlowControlPolicy.CREDIT_BASED`；不做全量 credit 数据面（vision §四）。
- 不动 xdef/EdgeConfig 模型面/生成管线（B2 方案 b 裁定；per-edge 配置记 Follow-up）。
- 不做消费位点心跳回传（同库 COUNT 首版够用）；不改消费侧轮询/确认语义。
- 不做 Kafka/Pulsar 通道背压（probe null = 门控关闭，显式语义 docs 写明）。

## Scope

### In Scope

- `nop-message-core`：`ChannelBacklogProbe` 接口。
- `nop-sys-dao`：`SysDaoMessageService` 实现 probe（COUNT 查询）。
- `nop-stream-runtime`：`RemoteResultPartition` 重载 + 门控（锁外等待/分片/typed 超时）+ `DataPlaneMessageServiceAdapter` delegate 访问器 + builder 装配（解包识别 + 全局配置读取 + 查询 TTL 缓存）+ `nop.stream.io.db.backlog` gauge + 新错误码 `ERR_STREAM_SEND_GATE_TIMEOUT`。
- 测试：runtime 门控单元（fake probe：等待/超时 typed/关闭/probe-null 不查询/锁外等待契约）+ nop-sys-dao E2E 扩展（停 drain → 深度 ≤ 上限且生产者阻塞；恢复 drain → 收敛排空）。
- docs：`docs-for-ai/03-modules/nop-stream.md`（背压配置/语义/cold-start 注意/观测）+ `dataplane-transport-design.md` WI6 收口注记。

### Out Of Scope

- 心跳位点回传、消费侧语义变更、其他通道背压、FlowControlPolicy 枚举、xdef/EdgeConfig 模型面、harness probe。

## Execution Plan

### Phase 1 - probe 能力与门控

Status: completed
Targets: `nop-message-core`、`nop-sys-dao`、`nop-stream-runtime/transport`

- Item Types: `Feature`

- [x] `ChannelBacklogProbe`（message-core）+ `SysDaoMessageService` COUNT 实现
- [x] `RemoteResultPartition` 重载 + 门控（锁外等待/100ms 分片/typed 超时含 topic+limit+backlog/可中断/probe-null 与 limit<=0 关闭语义）+ adapter delegate 访问器 + builder 装配（解包识别 + `nop.stream.data.db.backlog.limit` 读取 + TTL 200ms 查询缓存）+ gauge + 新错误码
- [x] 门控单元测试（fake probe 五路径：通行/等待/超时 typed/关闭/probe-null 不查询）+ 锁外等待契约断言（等待期间 sendLock 可被 close 获取）

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-runtime,nop-sys/nop-sys-dao,nop-message/nop-message-core -am` 全绿 + `nop-benchmark-stream` 编译核对（构造兼容）
- [x] **无静默跳过**：关闭 = 显式语义（javadoc/docs）；启用路径超时 = typed 失败；adapter 未实现 probe 的通道（Kafka/Pulsar）配了 limit = 门控静默关闭**并在启动日志声明一次**（诚实呈现，区别于无声空转）
- [x] No owner-doc update required（docs 集中 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - E2E、观测与文档

Status: completed
Targets: nop-sys-dao E2E、docs

- Item Types: `Feature` + `Proof`

- [x] `TestDataPlaneSysDaoBackendE2E` 扩展：停 drain → 生产者被门控（表深度 ≤ 上限、生产者阻塞可观测）；恢复 drain → 收敛排空
- [x] docs：nop-stream.md（配置键/语义/cold-start 注意/观测/通道差异表）；dataplane-transport-design.md WI6 收口注记

Exit Criteria:

- [x] **端到端验证**：生产者写入 → DB 表深度受控 → 消费恢复收敛排空全链已断言
- [x] **接线验证**：门控经 builder 装配链（解包识别 + 全局配置）真实生效（E2E 即消费面）
- [x] owner docs 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - closure audit 与提交

Status: completed
Targets: roadmap WI6、plan 2331

- Item Types: `Proof`

- [x] 独立子 agent closure audit；roadmap WI6 勾选；git commit
- [x] No owner-doc update required（Phase 2 已更新）

Exit Criteria:

- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI6 已勾选、提交完成

## Closure Gates

- [x] 门控语义齐备（锁外等待/typed 超时/关闭显式/同门 barrier 裁定）且 SysDao E2E 证明表深度受控
- [x] 默认关闭零回归（未配置 limit 的既有测试全绿）+ 构造兼容（benchmark 编译）
- [x] 模块边界不破坏（接口落 message-core；sys-dao/runtime 零新增依赖方向）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime,nop-sys/nop-sys-dao,nop-message/nop-message-core -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-import-order.mjs` 受影响模块无新增违规
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` 与 `--module nop-sys/nop-sys-dao --severity high` 均退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### per-edge 背压配置（xdef 声明面）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: job 级全局配置已达成「表深度受控」目标；per-edge 细分需动 xdef + `_StreamEdgeModel` 生成管线 + P1-XDSL-5 校验矩阵（Protected Area plan-first），范围/收益不匹配首版
- Successor Required: `no`（需求出现时另立 plan）

### 消费位点心跳回传

- Classification: `optimization candidate`
- Why Not Blocking Closure: 同库 COUNT + TTL 缓存已达标；回传省查询属延迟优化
- Successor Required: `no`

## Closure

Status Note: WI6 完成：`ChannelBacklogProbe`（message-core，零新增依赖方向）+ SysDao COUNT 实现 + `RemoteResultPartition` 门控（锁外等待/本地自增记账/typed 超时/barrier 同门）+ builder 装配链（adapter 解包 + 系统 property + 诚实 no-op 日志 + backlog gauge）+ gated E2E 真后端端到端证明；默认关闭零回归，benchmark 构造兼容。
Completed: 2026-10-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_b89aa99b-01e2-4135-9467-f3b8b57b681d）
- Audit Session: agent_b89aa99b-01e2-4135-9467-f3b8b57b681d（REJECTED 轻度 4 项：M1 装配链证据（方案 a 补 TestBackpressureGateWiring 3/3 绿）/M2 悬空 javadoc 归还/L1 docs 用词/L2 提交卫生——修复后按审计员裁定免复审闭卷）
- Evidence:
  - A/B 全 PASS：接口归属零新增依赖（两 pom diff 空）、probe COUNT 实现（索引覆盖）、7 参重载 + 5 参保留（benchmark 用 5 参）、锁外等待（单测断言 close <2s）、typed 超时带三参、builder 解包、gauge holder 模式、E2E 实质断言（精确一次/落表计数/深度 ≤ limit+slack）
  - C 工具：doc-links --strict exit 0；hollow 扫描 runtime+sys-dao 双 0；import-order 触及文件 0 违规（builder 2 条为 HEAD 既有）；TestRemoteResultPartitionBackpressureGate 审计独立重跑 6/6；TestBackpressureGateWiring 3/3
  - D Anti-Hollow PASS：write → gateOnBacklog → probe.backlog → SysDao COUNT 全连通；E2E 数字断言非空跑
  - E/F PASS：三裁定三处口径一致；FlowControlPolicy/EdgeConfig/xdef 零 diff
  - 已知 flake（既有 watch-only）：TestBatchTaskCheckpoint（nop-batch-core，非 WI6 改动面）在本轮并行/顺序构建各触发一次，隔离 8/8 绿
- Deferred 项分类检查：per-edge 配置（生成管线 plan-first）与心跳位点回传均为裁定性 Deferred，非缺陷降级

Follow-up:

- per-edge 背压配置（xdef 声明面）——需求出现时另立 plan（Deferred 已裁定）
- 消费位点心跳回传省查询（optimization candidate）
- RemoteGraphExecutionPlanBuilder 的行内 FQN 与既有 import 乱序（HEAD 既有）——随下次触碰该文件时顺手收
