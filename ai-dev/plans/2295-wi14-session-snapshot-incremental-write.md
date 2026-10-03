# 2295 WI14 session 快照写路径增量化 + 条目版本化字段（refactor roadmap M4）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI14（Deliverable: 裁定记录 + 代码变更 + 测试；deps: 无）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI14 本 roadmap 吸收；journal 面显式排除）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`
> Adversarial Review: round 1 子代理 agent_8819c29e-7994-48ad-8f06-7a616fcbb08b——1 Blocker（mutate-after-persist 证伪纯引用 diff）+ 4 Major（DB 标量列失真/并发竞态/租户键/Phase2 缺文档项）已全部吸收进本稿（Ruling 节、diff 判据、DB 协议、并发纪律、Exit Criteria）；round 2 子代理 agent_c8078b42-586f-4d98-a595-68587050a947——裁定可执行（无 Blocker），1 Major（fold 崩溃窗口 journal 毒化——读侧自愈裁定）+ 8 Minor 已吸收

## Purpose

WI14 要求先裁定"session 快照写路径增量化"的收益是否成立，成立则实现消息级 append 写路径 + 条目版本化字段。本 plan 内嵌裁定记录（见 Ruling 节），裁定结论：**收益成立，实现**。靶点是 `FileBackedSessionStore` 全量覆写与 `DBSessionStore` CLOB 全量更新；checkpoint journal（已 append-only）不在本项范围。

## Ruling — 增量收益裁定（Deliverable ①）

**结论：收益成立，实现消息级 append 写路径 + 条目版本化字段。**

依据（执行时 HEAD `f31089c68c`，2026-10-03 实测）：

1. **磁盘/DB 写放大是 O(N²) 且热点密集**：`sessionStore.save` 全量重写调用点——每 LLM turn 一次（`engine/ReActAgentExecutor.java:1014` 调 `saveLlmTurnCheckpoint`（:1061），其内 `:1101-1107` `replaceMessages(ctx.getMessages())`+save）、每次工具调用一次（`engine/AgentToolDispatcher.java:838`，per-tool-call 而非 per-batch）、每次注入 drain 一次（`engine/ReActAgentExecutor.java:1399` persistInjectedMessages）、round 边界/终态多次（`engine/AgentSessionLifecycle.java:161/412/575/975`、`engine/DefaultAgentEngine.java:705/793/1051`）。每轮 T 个工具调用 = T+2 次全量 O(N) 序列化+写盘；session 生命周期累计 O(N²)。长会话（数百消息、大 tool output 完整入史）下单次 save 达 MB 级。
2. **生产形态使大 session 成为规范形态而非异常**：roadmap M1 定位说明——飞书连接器等 channel 驱动自主 agent 已是生产场景；M1 异步工具等待/心跳/steering 全部延长单 session 存活期与消息数。dsh-D9 建议的靶点在 nop 生产形态下真实存在。
3. **DB 侧放大更重**：CLOB 全量 MERGE = 行重写 + redo/undo/binlog 放大；owner doc §16.4 已登记 `max_allowed_packet` 部署注意（64MB 默认 vs 需 ≥256MB）——全量 CLOB 正是推送单行体积增长的主因。
4. **反面检验（为什么不"移出"）**：快照是权威真相的范式不被破坏——增量条目只是优化层，周期 fold 回快照，无双真相源（与 WI15 的双真相源裁定正交：WI15 管压缩产物在 session 历史的存储形态，本项管 store 层物理写路径）；"test scale 安全"论据已被 ①② 否定——写路径成本随 channel 生产会话规模线性恶化。
5. **收益不成立的出口已检验不触发**：唯一支持的移出理由是"nop 无长会话场景"，与 roadmap Purpose 的 channel 定位直接矛盾，不成立。

**诚实成本口径**：本项是**写路径**增量化（磁盘字节与 DB 行重写从 O(N) 降为 O(delta)），不是 CPU 优化——为保正确性（见 Diff 判据），append 常态下每次 save 仍需对已持久化前缀做 O(N) 指纹重算（序列化级 CPU，与现状全量序列化同级但省去 I/O）。

**Diff 判据（round 1 审查 Blocker 修复后的定案）**：纯引用前缀 diff 被证伪——`ChatMessage` 是全可变对象（`setContent` abstract public，`ChatMessage.java:66`；子类 content/callId/arguments/result/attachments/parts/summary/detail 全部有 setter），且存在两条**持久化后原地改写**的真实主线：

- checkOutputGuardrail：assistant 消息先经 `saveLlmTurnCheckpoint` 落盘（ReActAgentExecutor.java:1014→:1101-1107），后经 `AgentPromptAssembly.checkOutputGuardrail:328/334` 对**同一实例** `setContent(blockText/modifiedContent)`（Block/Modify 主线必经）；
- checkInputGuardrail：steering/注入消息 drain 后立即持久化（persistInjectedMessages），下一轮 `runPreReasoningGate`（ReActAgentExecutor.java:856）经 `AgentPromptAssembly.java:75` 对该**已持久化** user 消息 `setContent(modifiedContent)`。

因此 append 判据 = **引用前缀稳定 ∧ 前缀内容指纹不变**：store 为每条已持久化消息缓存其序列化形态指纹（64-bit hash of `JsonTool.stringify(message)`——覆盖全部可序列化字段，子类无关）；每次 save 对前缀逐消息重算指纹，任一不匹配或引用不一致 → fold（全量重写）。指纹重算即上述诚实成本口径的来源。

**条目版本化语义**（应"条目版本化字段"交付项）：每个 append 条目携带 `baseVersion`（所基于快照的版本号）+ `seq`（journal 内连续序号）；快照携带 `snapshotVersion` 字段。读侧校验 `baseVersion==snapshotVersion && seq 连续`，不匹配即丢弃 journal 尾（快照自足，安全降级为旧状态），使 append 撕裂行无需额外恢复协议。

**fold 崩溃窗口的自愈裁定（round 2 Major-1）**：fold = 写快照（先）→ 清 journal（后），两语句间崩溃则残留旧 `baseVersion` 条目且位于 journal 头部——版本校验只能整卷丢弃（保证不回退，够正确性），但此后新 append 追加在残留之后，每次重放都在残留头部截断、新条目重启后不可见，直到下次 fold 才收敛。裁定：**读侧重放遇到校验失败/结构非法条目时自愈清除**——file store 删除 journal 文件、DB store 删除该 session 全部 journal 行（warn 登记后重置），使后续 append 从干净基线重启积累；副作用（残留条目代表的增量丢失）与"快照已含 fold 后全量状态"事实一致，无状态损失。

**DB journal 表租户键裁定**：journal 表 PK = `(SESSION_ID, SEQ)`，含 `TENANT_ID` 列并在 SELECT/DELETE 按主表同款租户过滤；"同 sessionId 跨租户"为不受支持场景（主表 PK 实际也只有 SESSION_ID，`AiAgentSessionTable.java:50`；session 归属单一租户，TenantSql 过滤已保证租户上下文下只见本租户行）。此裁定随代码注释与 owner doc 登记。

## Current Baseline

（执行时 HEAD `f31089c68c`，2026-10-03）

- `FileBackedSessionStore.save`（session/FileBackedSessionStore.java:255-262）：`writer.write(sessionFile, session)` 全量覆写 `session.json`（SessionFileWriter.write:69-111 tmp+ATOMIC_MOVE 原子替换，无 fsync——增量路径保持同等耐久级，不引入新 fsync 契约）。
- `DBSessionStore.save`（session/DBSessionStore.java:288-340）：`SessionFileWriter.serialize` 全量 JSON → CLOB `MERGE INTO`（键 SESSION_ID，租户时 (SESSION_ID,TENANT_ID)）；主行另有可查询标量列 AGENT_NAME/STATUS/UPDATED_AT + `IDX_AI_AGENT_SESSION_STATUS` 索引（AiAgentSessionTable.java:39-54）——增量 append 路径必须同步维护这些列（否则监控/清理查询失真）。
- 序列化契约：`SessionFileWriter.serialize(AgentSession)`（:113-137，字段名常量 :44-58）+ `SessionFileReader.deserialize(String)`（:71-183）；`ChatMessage` 多态经 `type` discriminator（ChatMessage.java:25-33，`getType()` 恒定输出）；`listAllSessions`（FileBacked:203-244 / DB:237-275）各自直读快照，**不经过统一 load 例程**——journal 重放必须同时接入两处，否则 discovery 返回缺尾状态。
- `AgentSession` 消息变更语义：`appendMessages` / `replaceMessages`（AgentSession.java:243-276，replaceMessages 为引擎主同步语义，拷贝引用）→ 引擎热路径下 session 消息列表相对上次持久化常态是"引用前缀稳定 + 尾部追加"（LLM turn/工具结果尾部追加），例外三类：①内容原地改写（guardrail，见 Ruling Diff 判据）；②列表结构性插入——本 roadmap WI3 已落地的原位插入 `AgentToolDispatcher.insertToolResultInList`（:960-983，`messages.add(end+1, result)`，等待期心跳史中非尾插入；tail 时退化为尾部追加）；③截断/重建（压缩 replaceMessages、fork）。三类例外统一走 fold 兜底。
- 压缩路径安全佐证：`ReferenceCompactionStrategy`/`MicroCompressionCompactor` 摘要走 `.copy()` 新建实例，无共享实例原地改写。
- `AgentExecutionContext.getMessages()`（engine/AgentExecutionContext.java:133）返回 live 列表，ctx 级原位插入/清改会透传到后续 replaceMessages。
- 进程内并发 save 真实存在：`DefaultAgentEngine.java:705`（injectInput/steering 调用方线程）与 ReAct 执行线程的 per-tool/LLM-turn save 可并发——现状靠 SessionFileWriter.ioLock + 原子替换收敛为"整文件二选一"；增量模式必须 per-session 显式串行化（见 Phase 1 并发纪律）。
- 既有崩溃恢复测试面：`TestFileBackedSessionStore` / `TestDBSessionStore` / `TestSessionStoreIntraExecutionPersistence`（engine 接线）/ `TestDBSessionStoreEngineWiring`。
- store-layer 边界（审计 AI-2/AI-19）：自建表/本地文件为引擎内部运行时状态，不注册 ORM（SessionFileReader.java:39-41 注释；`store-layer-contract.md`）——journal 表 DDL 自管（模式对齐 `AiAgentSessionTable`），不触 Protected Areas 的 ORM 模型面。
- autonomous WI9（RETRY_PLAN checkpoint journal 条目）在 `reliability/CheckpointJournalWriter` 面，本项不触碰。
- DB 租户模式旁路缓存（DBSessionStore.java:121-127 cacheEnabled）——per-session 持久化状态（指纹/版本/journal 计数）必须**独立于 session 缓存**的按 sessionId 结构，否则租户模式下 append 永不生效（行为仍正确但收益归零）。

## Goals

- **File store**：`save` 常态走消息级 append（journal sidecar `{root}/{sessionId}/session.journal.jsonl`），仅 fold 时全量重写 `session.json`；快照带 `snapshotVersion`，条目带 `baseVersion`+`seq`。
- **DB store**：新增自管 journal 表 `ai_agent_session_journal`（PK (SESSION_ID,SEQ)，含 TENANT_ID），同一 append/fold 协议；**append 路径附带轻量标量列 MERGE（AGENT_NAME/STATUS/UPDATED_AT，不含 CLOB）**保持索引列真实；fold = CLOB MERGE（先）→ journal 行 DELETE（后），崩溃窗口由版本校验兜底。
- **读路径统一**：快照 + journal 重放（版本/序号校验，撕裂尾/版本不匹配丢弃 + warn）在 `loadSessionFile` 与 `listAllSessions` 共用；`remove` 先清 journal 再清快照与空目录判定。
- **并发纪律**：per-session 锁串行化"diff→写→状态更新"全序（进程内双写者安全）；状态结构独立于 session 缓存。
- **零回归**：旧 `session.json`（无 `snapshotVersion` 字段）照常加载（version=0）；`ISessionStore` 接口不变；引擎/executor 调用点零改动。
- 既有耐久语义不降级：append 路径崩溃后恢复的状态 = 快照 + journal 有效前缀（可能旧于最后一条撕裂 save——与现全量覆写"tmp 未完成则整步丢失"同级，如实登记）。

## Non-Goals

- checkpoint journal（`reliability/CheckpointJournalWriter`）任何改动（autonomous WI9 面）。
- WI15 压缩产物条目化（session 历史形态，另一 WI）。
- fsync/group-commit 耐久升级、跨进程 journal 合并协议（保持 last-writer-wins，与现状同级）。
- guardrail 改写时序的引擎侧改造（改写后置是既有合同——AR-11/plan 277 语境；本项以 store 层指纹校验适配之，不改引擎）。
- `InMemorySessionStore`（无磁盘写路径）。
- orm.xml / `_gen/`（journal 表 DDL 自管，store-layer 边界已裁定不注册 ORM）。

## Scope

### In Scope

- `session/` 包：新增 journal 条目编解码 + 重放 + 消息指纹；`FileBackedSessionStore` 指纹校验 diff/append/fold 写路径与统一 load + per-session 锁；`DBSessionStore` 同协议 + `AiAgentSessionJournalTable` DDL + 标量列 MERGE；`SessionFileWriter`/`SessionFileReader` 的 `snapshotVersion` 字段与可复用状态应用例程。
- 测试：append 常态（快照不变+journal 追加）、fold 触发（guardrail 原地改写、原位插入、截断、阈值 64、首次保存）、撕裂尾与版本不匹配降级、legacy 无版本字段兼容、remove 清理顺序、restart 新实例恢复完整状态（含标量 tokens/status/seenInputKeys/pendingInjections）、并发双写者、DB journal 行为对称 + STATUS 列即时真实 + 租户对称。
- owner doc `nop-ai-agent-session-and-storage.md` 增量写路径契约节 + log。

### Out Of Scope

- 引擎/executor save 调用点语义变更；`ISessionStore` 接口变更；`_vfs` ORM 模型。

## Execution Plan

### Phase 1 - 收益裁定落档 + file store 增量写路径

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/`（FileBackedSessionStore、SessionFileWriter、SessionFileReader、新增 journal 编解码与指纹）

- Item Types: `Feature` | `Decision`（Ruling 节落档为 Decision）

- [x] Ruling 节（本文件）随 plan 定稿落档（含 round 1 审查的 Diff 判据修复）；roadmap WI14 的裁定引用在 closure 时经 log 登记
- [x] `SessionFileWriter.serialize(session, snapshotVersion)` + `SessionFileReader` 读 `snapshotVersion`（缺省 0）；提取"可变标量状态应用"例程供快照与 journal 条目共用——**绝对值语义**（last-writer-wins，不得用 addTokensUsed 等累加器照搬）、`pendingInjections` 先 `clearPendingInjections` 再 addAll（无 set 访问器）、`tenantId` 用捕获时 session 的绝对值（不因 null 抹掉 DB 列回填）（实现注：restoreTotalTokensUsed/restoreTotalIterations/truncateMessages 包内访问器新增；执行修复 PendingInjection 缺 @DataBean 的 WI8 潜在序列化缺陷）
- [x] 消息指纹：`ChatMessageFingerprints.fingerprint`（JsonTool.stringify → FNV-1a 64），persist 时缓存、append 候选时对全前缀重算比对
- [x] journal 条目编解码：`SessionDeltaJournal`（`{baseVersion, seq, totalMessageCount, appendedMessages[], state}`）；重放校验 `baseVersion==snapshotVersion && seq 连续`，撕裂行/不匹配自愈（heal = 对重放态 fold，执行中发现的记账不变式修复——纯记账重置会让下次 append 基于"已持久化"的错误信念丢数据）
- [x] `FileBackedSessionStore`：`SessionPersistenceState`（version + 前缀指纹 + journal 条目数，独立于 session 缓存）；per-session 锁串行化；save = 无快照必 fold → 指纹校验前缀 diff → append（<64 条）或 fold（version+1 原子重写 → journal 删除）；load 统一（getOrCreate/get/listAllSessions 共用 loadPersisted）；remove 先删 journal
- [x] 单测：append 常态断言 session.json 内容不变且 journal 增行；fold 触发含 **guardrail 序列回归**（assistant 消息 save 后 `setContent` 改写 → 下次 save → 新实例 reload 内容为改写值——Blocker B-1 的直接回归测试）与原位插入、截断、阈值、首次保存；撕裂尾丢弃恢复；stale journal（baseVersion 不匹配）整卷丢弃 + 自愈清除后新 append 重启可见；legacy 文件兼容；remove 后 journal 不残留；restart 新实例恢复 messages+tokens+status+metadata+seenInputKeys+pendingInjections；listAllSessions 含 journal 尾；**并发双写者**（ExecutorService(2) + 共享 store 交错 `get→replaceMessages→save`，断言口径 = journal 逐行可解析无撕裂 + 新实例重放成功 + 终态等于某合法串行化终态，不断言"等于最后 save 者"）

Exit Criteria:

- [x] 上述单测全绿，且既有 `TestFileBackedSessionStore`/`TestSessionStoreIntraExecutionPersistence` 零回归（后者适配：mid-execution 持久化检查改走 crash-recovery 读路径——journal 尾在 sidecar，裸 session.json 不再单独是恢复源，契约精神不变）
- [x] **接线验证**：save→指纹 diff→append 路径由 store 内部驱动被运行时真实走到（append 断言即证明），guardrail 改写被指纹捕获转 fold（回归测试断言即证明）
- [x] **无静默跳过**：journal 重放遇结构非法条目（非撕裂、校验失败）warn + 自愈 fold（Ruling 裁定的文档化降级），IO 失败 fail-loud 抛 `NopAiAgentException`；无空方法体/no-op
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（全量套件见 Closure Gates 复核）
- [x] owner-doc 统一更新归 Phase 3（本 Phase 改变 live baseline，显式声明归属）；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - DB store journal 表 + 同协议

Status: completed
Targets: `session/DBSessionStore`、`session/AiAgentSessionJournalTable`（新增）

- Item Types: `Feature`

- [x] `AiAgentSessionJournalTable`：DDL（PK (SESSION_ID,SEQ)，列 TENANT_ID/BASE_VERSION/ENTRY_JSON/CREATED_AT），模式对齐 `AiAgentSessionTable`；租户键裁定落类 Javadoc
- [x] `DBSessionStore`：save diff/append/fold（append = journal INSERT 先 + 标量列 UPDATE 后（不含 CLOB、不建主行，UPDATE=0 行即 fail-loud）；(SESSION_ID,SEQ) 跨进程竞速 SQLException 降级 fold（last-writer-wins 对齐旧 MERGE 语义）；fold = CLOB MERGE（version+1）先、journal DELETE 后；loadFromDb/listAllSessions 重放 + heal=重放态 fold；remove 先删 journal 行；租户过滤对齐主表）
- [x] 单测（TestDBSessionIncrementalWrite）：append 插行 + STATUS 列即时真实、fold 清行 + CLOB 版本+1、stale 行 heal + 后续 append 重启可见、discovery 重放、remove 清理、租户 scoped（对齐"同 sessionId 跨租户不受支持"裁定——执行中实测确认 plan 232 的 MERGE KEY (SESSION_ID,TENANT_ID) 对 SESSION_ID 单键 PK 表在跨租户同 id 时 H2 自冲突超时，为存量限制已登记 Known Residuals）

Exit Criteria:

- [x] 上述单测全绿，既有 `TestDBSessionStore`/`TestDBSessionStoreEngineWiring` 零回归
- [x] **接线验证**：DB journal 行在 append 后可被 SELECT 到、fold 后清空、STATUS 列 append 后即为新值（直接 SQL 断言）
- [x] **无静默跳过**：同 Phase 1 降级/失败语义
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（全量套件见 Closure Gates 复核）
- [x] `ai-dev/logs/` 对应日期条目已更新（owner doc 统一更新归 Phase 3，本 Phase 显式声明归属）

### Phase 3 - owner doc 与收口

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`、log、roadmap

- Item Types: `Proof`

- [x] owner doc 新增"快照增量写路径"契约节（布局、条目格式与版本化、Diff 判据与 guardrail 适配、fold 触发、崩溃窗口与降级语义、DB 标量列维护、租户键裁定、已知 residual）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error
- [x] refactor roadmap WI14 checkbox 勾选 + Current Baseline 补一条 WI14 落地锚点
- [x] 独立 closure audit + plan checklist/hollow 扫描 + git commit

Exit Criteria:

- [x] owner doc 在位且与 live 代码一致；全部验证门通过

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI14 checkbox → git commit → 本 plan 标 completed。

- [x] 消息级 append 写路径端到端连通（save→指纹 diff→append/fold→load 重放→状态一致，restart 与 guardrail 改写场景覆盖）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（3503 全绿，3484→3503）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2295-wi14-session-snapshot-incremental-write.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件；无关杂散 .rels 改动已排除）

## Known Residuals（如实登记，non-blocking）

- **append 路径崩溃耐久 = 快照+journal 有效前缀**：最后一条撕裂 save 丢失（含其标量块），恢复态旧于最后一步——与现全量覆写"tmp 未完成则整步丢失"同级；fsync/group-commit 为后续优化（out-of-scope 已声明）。
- **跨进程并发写**：两实例同时 save 同一 session 保持 last-writer-wins（与现状 MERGE/覆写同级）；stale 实例的 journal 条目由版本校验在读侧中和，不新增分布式协议。
- **DB 租户模式 + engine 线程 save 的收益边界**：`DefaultAgentEngine.java:705/:793` 的 save 不经 replaceMessages，租户模式（缓存旁路）下该实例来自 fresh 反序列化、消息实例必不同——但 diff 判据基于内容指纹而非引用（执行细化），content-equal 的 fresh 实例仍走 append；仅在内容真实变化时 fold——收益退化窗口比预估小。
- **同 sessionId 跨租户为不受支持场景（存量限制，非本项引入）**：plan 232 的主表 `MERGE KEY (SESSION_ID, TENANT_ID)` 作用于 `PRIMARY KEY (SESSION_ID)` 单键表，跨租户同 id 时 H2 实测自冲突超时（"Concurrent update in table"）；WI14 的 fold 路径复用该 MERGE，继承同一语义。journal 表按裁定沿用 SESSION_ID 单键 + TENANT_ID 过滤；修复需动主表键结构（平台级，登记 follow-up）。
- **append 常态 CPU 与全量序列化同级**（前缀指纹重算）——本项交付的是磁盘/DB 写路径 O(delta)，CPU 优化（如增量序列化缓存）为后续 optimization candidate。
- **journal 条目携带标量块的体积**：seenInputKeys（≤1024 键）/pendingInjections（≤256 条）随每条 append 重复直至 fold（阈值 64 条封顶放大倍数）——典型量级 KB 级，病理场景受阈值有界。

## Non-Blocking Follow-ups

- journal 文件 fsync（耐久升级，optimization candidate）
- fold 阈值可配置化（当前常量 64，optimization candidate）
- append 路径 CPU 优化：前缀增量序列化缓存（optimization candidate）

## Closure

Status Note: 增量收益裁定成立后，file/DB 两条消息级 append 写路径 + 条目版本化（baseVersion/seq/snapshotVersion）完整落地；round 1 Blocker（guardrail mutate-after-persist 证伪引用 diff）以逐位置内容指纹判据修复并有直接回归测试；自愈=对重放态 fold 的不变式在两个 store 一致实现且 post-heal append 跨重启可见有测试；独立 closure audit 本机复跑 68/68 store 测试全绿 + 全量 3503 全绿 + 三工具 exit 0——WI14 交付全部落地。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_4a9fed62-83bd-4695-bb34-7446fa44619b（fresh session）
- Evidence:
  - Phase 1/2/3 逐条 Exit Criteria 全 PASS（file: SessionFileWriter/Reader 版本化、applyState 绝对值语义、指纹、journal codec、store 协议、统一 load、remove 顺序；DB: journal DDL、INSERT先/UPDATE后、UPDATE=0 fail-loud、竞速降级 fold、heal=重放态 fold、租户过滤）
  - 行为抽查 4 项全 PASS（指纹不匹配→fold / heal 重写快照 v+1 且计数归零 / DB 语句顺序+fail-loud+降级 / seq 自 0 连续）
  - TestSessionIncrementalWrite 13/13 + TestDBSessionIncrementalWrite 6/6 + 既有 49 store 测试零回归；`./mvnw test -pl nop-ai/nop-ai-agent -am` 3503 全绿
  - hollow exit 0；doc-links exit 0；plan-checklist exit 0
  - Anti-Hollow：引擎 12 个 save 调用点→store 增量路径由 append 双向断言（journal 增行 ∧ 快照字节不变）证明真实连通
  - Deferred 诚实性：存量 MERGE 跨租户限制经 `git show HEAD` 核实非本项引入；无 live defect 降级
  - Minor 提醒已处理：commit 排除无关 .rels；journal state 非法枚举 fail-loud（观察项，不违反"损坏必须暴露"）

Follow-up:

- journal 文件 fsync / fold 阈值可配置化 / append CPU 优化（见 Non-Blocking Follow-ups）
- 主表 MERGE KEY (SESSION_ID,TENANT_ID) 对单键 PK 的跨租户语义修复（平台级，plan 2295 Known Residuals 登记）
