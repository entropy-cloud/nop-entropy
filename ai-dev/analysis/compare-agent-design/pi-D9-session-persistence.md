# pi-D9 会话持久化与恢复对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、pi=c49906ec7（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D9 子机制 D9-1..D9-4）、02-terminology-map.md（T15 双会话栈精确化口径/T16 checkpoint 对齐口径）; 机制事实引用 03 §2.3（P10 持久化时机）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`

## ① 结论摘要

- 总裁定：**等价（数据模型 vs 恢复治理互补）**。pi 强在类型化条目+版本迁移链+树形会话+原子写；nop 强在恢复纵深（发散检测/主动扫描/接管锁，同 dsh-D9 D9-4 结论）。
- 关键差异 1（D9-1）：pi v3 `SessionEntry` 9 成员判别联合（含 compaction/branch_summary/label 等一等条目）+`migrateV1ToV2/V2ToV3` 显式迁移链；nop 全量 JSON（Jackson role 多态标签）无版本字段。
- 关键差异 2（D9-1 架构事实）：pi **双会话栈**——生产 v3（coding-agent SessionManager）+未接线 v4 骨架（harness lane/operation 日志+SQLite+writer lease，除少数方法外全部 `unavailable()` 抛 HarnessNotImplemented）——对比与裁决以 v3 为准，v4 登记为演进方向（02 T15 精确化口径）。
- 关键差异 3（D9-2）：pi 原子写三级（.tmp+rename / 未确认追加按有效前缀发布 / 首条 assistant 前缓冲）比 nop 的全量覆写+ATOMIC_MOVE 更细粒度。
- 可吸收增量建议一句话：nop 可吸收 pi 的显式版本迁移链与树形会话（见 ⑥）。

## ② nop 侧机制与锚点

nop 事实基线与 dsh-D9 报告 ② 节共享：AgentSession 可变聚合（无版本化，`session/AgentSession.java:18-429`）；FileBacked 全量覆写+ATOMIC_MOVE / DB CLOB MERGE（`SessionFileWriter.java:30-95`）；恢复三路（resume/wake/restore）+journal 幂等键发散检测（`AgentSessionLifecycle.java:527-567`）+60s 扫描+CAS+lease 接管锁（`ScheduledRecoveryManager.java:109-`、`DbSessionTakeoverLock.java:197-261`）。

## ③ pi 侧机制与锚点

按 02 T15 与 03 §2.3（P10），对比增量：

- **数据模型**（D9-1）：v3 生产栈——`SessionEntry` 判别联合 9 成员（message/thinking_level_change/model_change/compaction/branch_summary/custom/custom_message/label/session_info，`packages/coding-agent/src/core/session-manager.ts:144-154`）；头 `SessionHeader{type:"session",version:3,id,timestamp,cwd,parentSession}`，`CURRENT_SESSION_VERSION=3`（:30-42）；迁移链 `migrateV1ToV2`/`migrateV2ToV3`（:283-288）；entry `id/parentId` 树（getTree/getBranch，:1284-1313）；labels 是追加型 LabelEntry（latest-wins 解析，:1232-1250,967-974）。
- **存储**（D9-2）：单 JSONL 文件；`.tmp`+rename 原子发布（`packages/agent/src/harness/session/jsonl/storage.ts:24-47`）；追加未确认时按有效前缀原子发布（:86）；首条 assistant 前缓冲、到达时 `openSync("wx")` 一次性全量写出（`session-manager.ts:1015-1042`）——写时机与 LLM 调用严格交错（每条消息 loop 继续前落盘，`agent-session.ts:651-669`）。
- **fork/branch**（D9-3）：`forkFrom` 复制内容到新文件新 id（`parentSession` 记来源，:1580-1603）；`navigateTree(targetId)` 同文件树内移动（可带 branch_summary 摘要）；labels 书签；v4 栈有完整 `SessionRepo{create,open,list,delete,fork}`+`findOpenOperations` 崩溃扫描+SQLite writer lease（`harness/session/jsonl/repo.ts`、`session-backends/sqlite-node/src/sqlite/repo.ts:669`）——**未接入生产**。
- **checkpoint/崩溃恢复**（D9-4）：v3 无 checkpoint 概念（同步逐条落盘即持久化，崩溃丢失=最后未完成消息）；v4 `findOpenOperations`（:290-326）+SuspendedOperation 为恢复设计但未接线；无接管锁/主动扫描对位。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | pi 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D9-1 session 数据模型 | 可变聚合全量 JSON（role 多态标签）；无版本化 | 类型化条目判别联合 9 成员+显式版本迁移链+id/parentId 树+labels | 对方领先 | nop `AgentSession.java:18-429`；pi `session-manager.ts:30-154,283-288`——pi 的压缩/分支/标签都是一等条目且版本演进有迁移契约；nop 新字段=改 Writer+Reader 双处 |
| D9-2 存储格式与后端 | 全量 JSON 快照（文件/DB CLOB）+4 保存点 | 增量 JSONL+三级原子写（tmp+rename/有效前缀发布/首条缓冲）+同步交错落盘 | 对方领先 | nop `SessionFileWriter.java:30-95`；pi `storage.ts:24-47,86`——增量 append 写放大优势同 dsh-D9 D9-2 结论；pi 的"有效前缀发布"处理追加中断比 nop 全量覆写更细；⚠️ pi 无多后端切换（v3 单文件），nop 的 DB 后端是 pi 缺的 |
| D9-3 resume/fork/branch | 恢复三路语义分离（治理/条件/崩溃）+fork 独立快照复制+消息过滤器 | forkFrom 复制+navigateTree 树内移动+labels 书签+branch_summary | 等价 | nop `AgentSessionLifecycle.java:219-696`、`FileBackedSessionStore.java:269-294`；pi `session-manager.ts:1580-1603,1284-1313`——nop 恢复分类细、pi 树导航/书签独有；fork 本体等价（快照复制同构） |
| D9-4 checkpoint 与崩溃恢复 | journal 消费+幂等键发散检测+60s 主动扫描+CAS+lease 接管锁 | v3 逐条同步落盘（崩溃丢尾部）；v4 findOpenOperations+SuspendedOperation **未接线**；无接管锁/扫描 | nop 领先 | nop `AgentSessionLifecycle.java:527-567`、`ScheduledRecoveryManager.java:109-`；pi `repo.ts:290-326`（未接线）——pi 生产栈崩溃恢复最薄（丢尾部无合成收尾，dsh interruptedTurnClosers 对位缺失）；v4 骨架登记为演进方向 |

## ⑤ 语义差异与取舍

- **"session"第三种范式**（02 T15 三方合观）：nop=可变聚合快照、dsh=事件溯源账本、pi=类型化条目文档（树形）。pi 的条目文档介于两者之间：条目类型化（比 dsh 事件语义粗）但树形可导航（比 nop 快照灵活）。
- **持久化时机与崩溃窗口**（三方对照）：dsh 事件即真相+write-behind（崩溃丢尾部→closers 修复）、nop 保存点同步（崩溃丢最后一段→journal 校验）、pi 逐条同步（崩溃丢最后一条，无修复）。pi 的窗口最小但无修复机制——简单性换取恢复能力。
- **双栈的战略含义**：pi v4（lane/operation 日志+SQLite+writer lease+findOpenOperations）是朝 dsh 方向的演进骨架（操作可恢复化）——当前未接线，对比中不计入 pi 能力但登记为方向证据（02 T15 勘误口径）。
- **词同义异**：pi 的 "fork"（新文件复制）vs "navigateTree"（同文件树内移动）是两个不同操作——与其他方的"分支"词对齐时须先问是哪种；nop 的 fork 对位 pi 的 forkFrom 而非 navigateTree。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：等价**——D9-1/D9-2 pi 领先（类型化+迁移链+原子写），D9-4 nop 领先（恢复纵深 pi 生产栈最薄），D9-3 等价；与 dsh-D9 同构的范式互补结论。

可吸收增量建议（仅记录，不实施）：

1. 【来源 pi；针对 nop 无版本化】AgentSession 增加 version 字段+显式迁移函数链（对位 migrateV1ToV2/V2ToV3），支撑未来 session 格式演进的向后兼容承诺。
2. 【来源 pi；针对 nop 消息多态标签】nop 的 Jackson role 多态标签可升级为显式条目判别联合（对位 SessionEntry），使 compaction/标签等元数据条目化（当前 metadata Map 无类型约束）。
3. 【来源 nop；反向记录供 pi 参考】pi v3 崩溃尾部无修复——dsh interruptedTurnClosers 或 nop journal 校验模式可补；pi v4 骨架接线后应优先补齐 findOpenOperations 的恢复动作。
4. 【来源 pi；针对 nop 会话组织】长生命周期多任务场景可参考 pi 树形会话+labels（nop 现有 parentSessionId 单链谱系可升级为树）。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D9 定义）、`02-terminology-map.md`（T15/T16）、`dsh-D9-session-persistence.md`（nop 基线共享）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../session/`、`reliability/`、`runtime/{lock,recovery}/`
- pi：`packages/coding-agent/src/core/session-manager.ts`、`packages/agent/src/harness/session/jsonl/{storage,repo,codec}.ts`（`~/ai/pi` @ c49906ec7）
