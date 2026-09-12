# dsh-D9 会话持久化与恢复对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D9 子机制 D9-1..D9-4）、02-terminology-map.md（T15/T16："session"与"checkpoint"词同义异警示）; 机制事实引用 03（P16/P20-21）、06（崩溃恢复合成）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`

## ① 结论摘要

- 总裁定：**等价（范式差异主导）**。数据模型与存储层 dsh 领先（事件溯源 vs 可变聚合快照；增量 append vs 全量重写）；恢复与治理层 nop 领先（发散检测/主动恢复扫描/跨进程接管锁为 nop 独有）。
- 关键差异 1（D9-1）：dsh Session 是事件溯源聚合（append-only log 即真相，消息历史是派生投影）；nop AgentSession 是可变聚合状态（全量 JSON 快照，"session 是可变聚合状态、checkpoint journal 才是 append-only"）——**双方对"真相在哪"的回答相反**。
- 关键差异 2（D9-2）：nop 保存=全量覆写（session.json 原子移动 / DB CLOB MERGE）——大会话写放大；dsh=增量 append（JSONL zstd）+write-behind 有界批+flush barrier。
- 关键差异 3（D9-4）：nop 恢复面独有三件——幂等键发散检测（工具调用指纹重算）、60s ScheduledRecoveryManager 主动扫描（超时/orphan/锁清理）、CAS+lease 跨进程接管锁；dsh 的 interruptedTurnClosers 确定性合成收尾更优雅但为被动载入时触发。
- 可吸收增量建议一句话：nop 可吸收 dsh 的增量 append 写路径（消大会话写放大）与 session 记录版本化（见 ⑥）。

## ② nop 侧机制与锚点

按 02 T15/T16 与 2026-09-12 nop 侧补充调研（D9 任务），对比增量：

- **数据模型**（D9-1）：`AgentSession` 可变聚合——持久字段 sessionId/agentName/messages/totalTokensUsed/totalIterations/status/metadata/parentSessionId/planId/compactedAt/tenantId（`session/AgentSession.java:18-429`）；**无版本化字段**（无乐观锁）；activeTags 不持久化；消息写入双路径（appendMessages/replaceMessages 全量替换幂等，:147-180）。
- **存储后端**（D9-2）：三实现——FileBacked（`{root}/{sessionId}/session.json`，Jackson 多态 role 标签，**每次 save 全量覆写**，.tmp+ATOMIC_MOVE crash-safe，`session/SessionFileWriter.java:30-95`）；DBSessionStore（`ai_agent_session` 表，SESSION_DATA CLOB 存整个 session JSON，MERGE upsert，`session/DBSessionStore.java:296-308`）；InMemory（缓存，save no-op）。执行中保存点：LLM_TURN 后/每工具后/WAIT_FOR/执行结束四处。
- **resume/fork**（D9-3）：恢复三路（resumeSession 清 denial-pause / wakeSession 清条件等待 / restoreSession 崩溃恢复，`engine/AgentSessionLifecycle.java:219-696`）；fork=`sessionStore.forkSession`（**独立快照复制**非引用共享，inheritContext 可选+消息过滤器 Predicate+parentSessionId 链接+立即 save，`FileBackedSessionStore.java:269-294`、`ISessionStore.java:107-113`）；SESSION_FORKED 事件。
- **checkpoint 与恢复**（D9-4）：checkpoint 分录存摘要与计数**不含消息内容**（`reliability/Checkpoint.java:45-333`）；journal 为 Markdown 人读格式（`## CP-{seq}` 节+key:value 行，`CheckpointJournalWriter.java:86-104`）+snapshot.json 加速（每 10 个 checkpoint 重写）；幂等键=sha256(toolName|callId|inputSummary) 仅 TOOL_EXECUTION（`Checkpoint.computeIdempotencyKey:210-225`）；发散检测两级（messageCount 粗校验+liveKey 重算比对，不符则拒该 checkpoint 降级 session 重放，`engine/AgentSessionLifecycle.java:527-567`）；接管锁 CAS+lease（无锁 INSERT/同 owner 续租/过期抢占，`runtime/lock/DbSessionTakeoverLock.java:197-261`）；ScheduledRecoveryManager 60s 五步扫描（锁清理→超时→orphan 判定→恢复/abort→团队任务，`runtime/recovery/ScheduledRecoveryManager.java:109-`）。

## ③ 对方侧机制与锚点

按 02 T15/T16 与 03 §2.2，对比增量：

- **数据模型**（D9-1）：`Session` 事件溯源聚合——私有 append-only log+`append()` 校验 lossless JSON+深冻结（`packages/core/session/src/index.ts:425,604`）；消息历史是派生（SurfaceManager 增量 fold 投影，仅 user/assistant/tool-result 三类，`surface.ts:398`）；`ignorable` 信封=未知类型跳过（词汇增长不 bump 版本，`types.ts:408-440`）；每 session 与 agent 1:1 同 id。
- **存储后端**（D9-2）：双后端 JSONL（`.jsonl.zstd`，`session-persistence-jsonl/src/format.ts:17,24`）+SQLite，共用后端无关 coordinator（PersistenceBackend 原语+torn-tail 修复 token，`coordinator.ts:119-128`）；`SessionWriteBehind` 有界批量写（deadline/active write/barrier/失败保留，`write-behind.ts:22-60`）；`flush()` 返回持久化完成 barrier（parallel mode）；turn 边界不 await flush（检查点归 checkpoint-policy 管）。
- **fork**（D9-3）：`SessionStore.fork`（index.ts:1203）+ `session/end-seed{inherited}` 标记（types.ts:400）+ 内存 `firstLiveSeq`（index.ts:497,584；types.ts:382 注记）——seedLength 头字段已在 c291e7961a 移除（index.ts:97-98 显式拒绝）。
- **崩溃恢复**（D9-4）：载入时 `interruptedTurnClosers` 确定性合成收尾——未决 tool call 补 error result→补 step/end→turn/end{interrupted}，时间戳复用最后真实事件（`packages/core/session/src/repair.ts:27`、`coordinator.ts:903,981`）；TOOL_OUTCOME_UNKNOWN 携带"勿盲目重试"模型可读指引。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | dsh 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D9-1 session 数据模型 | 可变聚合（消息列表+状态字段）；无版本化；消息历史即真相 | 事件溯源聚合（append-only log 即真相）；消息=派生投影；ignorable 版本演进 | 对方领先 | nop `AgentSession.java:18-429`；dsh `session/src/index.ts:425`——事件溯源赋予重放/审计/多投影能力；nop 无版本化字段（并发写靠外部接管锁补偿）；⚠️ 词同义异：三方"session"都非 HTTP session（02 T15） |
| D9-2 存储格式与后端 | 全量 JSON 快照（文件原子移动/DB CLOB MERGE）三实现；执行中 4 保存点 | 增量 JSONL(zstd)+SQLite 双后端+write-behind 有界批+flush barrier+后端无关 coordinator | 对方领先 | nop `SessionFileWriter.java:30-95`；dsh `format.ts:17`、`write-behind.ts:22-60`——大会话下 nop 全量重写写放大 O(n²) 累计；dsh append O(1) 摊销；dsh 显式 durability barrier（flush 可等）vs nop save 即同步完成（语义不同：nop 简单、dsh 高吞吐） |
| D9-3 resume/fork/branch | 恢复三路（resume/wake/restore 语义分离）；fork=独立快照复制+过滤器+谱系链接 | fork=种子事件+`session/end-seed{inherited}` 标记（durable）+内存 `firstLiveSeq` 双轨谱系记账（seedLength 已在 c291e7961a 移除）；resume=进程重启载入（无治理恢复面） | 等价 | nop `AgentSessionLifecycle.java:219-696`、`FileBackedSessionStore.java:269-294`；dsh `session/index.ts:1203`——nop 恢复语义分类更细（治理/条件/崩溃三路），dsh 谱系记账更精细（end-seed 标记 durable+firstLiveSeq 内存双轨）；fork 本体等价（快照复制 vs 种子复制） |
| D9-4 checkpoint 与崩溃恢复 | journal 消费+幂等键（工具指纹 sha256）+发散检测降级重放+60s 主动扫描（超时/orphan）+CAS+lease 接管锁 | interruptedTurnClosers 确定性合成收尾（时间戳复用）+torn-tail 修复 token+write-behind 失败保留 | nop 领先 | nop `AgentSessionLifecycle.java:527-567`、`ScheduledRecoveryManager.java:109-`、`DbSessionTakeoverLock.java:197-261`；dsh `repair.ts:27`、`coordinator.ts:903`——nop 恢复纵深（主动扫描+发散检测+跨进程锁）dsh 全无；dsh 合成收尾确定性设计优雅但被动触发；⚠️ "checkpoint"按 T16 能力面对齐非名词对齐 |

## ⑤ 语义差异与取舍

- **真相位置的范式分野**：dsh"事件即真相"（消息/投影/计数全部可从 log 重建——inbox、runtime-context、llm-retry 计数都这么做）；nop"快照即真相"（journal 只做恢复校验不重建状态）。取舍：事件溯源换来重放与审计能力但要求所有状态变更事件化；快照模型简单直接但历史细节（中间事件）丢失。这是 D3-4 裁定（对方领先）在本维的延伸，但 D9-4 的恢复工程 nop 反超——说明"真相模型"与"恢复能力"可以解耦。
- **写策略与一致性**：nop save 同步完成（返回即持久）——语义简单，调用方无 durability 心智；dsh write-behind 异步批+flush barrier——高吞吐但消费者必须理解"何时可等"。dsh turn 边界不等 flush 的取舍（崩溃丢尾部→closers 修复）vs nop 每保存点同步（崩溃丢最后一段，journal 校验兜底）。
- **词同义异集中区**（02 伪差异警示 T15/T16）：nop session≠dsh Session（可变聚合 vs 事件账本）；nop checkpoint（journal 分录子系统）≠dsh flush barrier/checkpoint-policy；dsh Surface（模型可见投影）在 nop 无名词对位（nop 的"模型可见历史"就是 session.messages 本身）。对比任何恢复语义前必须先对齐这三个词。
- **不可比项**：dsh 双后端抽象（PersistenceBackend 原语+torn-tail token）依赖其 JSONL 格式；nop 的 DB CLOB 方案在关系库生态下是合理对位——存储介质选择属平台基础设施差异，不硬比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：等价**——D9-1/D9-2 dsh 领先（事件溯源+增量存储），D9-4 nop 领先（恢复纵深），D9-3 等价；范式差异（真相位置）主导评分而非工程质量——双方在各自范式内都是完备实现。

可吸收增量建议（仅记录，不实施）：

1. 【来源 dsh；针对 nop 全量重写】FileBackedSessionStore 评估增量 append 路径（消息级 JSONL 追加+周期快照压缩），消大会话 O(n²) 写放大；DB CLOB 方案可先加 VERSION 列乐观锁。
2. 【来源 dsh；针对 nop 无版本化】AgentSession 增加 version/etag 字段（save 时 CAS），与 ISessionTakeoverLock 互补防并发写丢失。
3. 【来源 dsh；针对 nop 恢复被动性反差】nop 的 ScheduledRecoveryManager 主动扫描是优势，可再吸收 dsh 的确定性合成语义：orphan 恢复时对未决工具调用生成"结果未知"合成 response（当前 restoreSession 重放后由发散检测兜底，粒度较粗）。
4. 【来源 nop；反向记录供 dsh 参考】CAS+lease 接管锁与幂等键发散检测可移植 dsh 多进程部署场景（当前 dsh 单进程模型无此需求——记录适用前提）。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D9 定义）、`02-terminology-map.md`（T15/T16）、`03-flow-agent-loop.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../session/`、`reliability/{Checkpoint,CheckpointJournalWriter,ICheckpointManager}.java`、`runtime/{lock,recovery}/`
- dsh：`packages/core/session/src/`、`packages/session/session-persistence{,-jsonl,-sqlite}/`（`~/ai/deepseek-harness` @ c291e7961a）
