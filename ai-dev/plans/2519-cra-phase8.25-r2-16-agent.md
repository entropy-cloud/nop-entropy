# 2519 CRA Phase 8.25 — R2-16 3.1 agent 编排/会话批（10 项：引擎注入登记窗口 + F4 终态清理 29 站点（gate3 反向钉死改写同批）+ spawn 池 close 接线/drain + 成员/spawn 超时取消契约 + 会话 journal 累积上界 + 工具超时取消传播 + 方言流式协议族 + DBMessageService 治理 + 接管锁 abort 语义 + spawn 池 drain 搭车）

> Plan Status: active（Phase 1 done；Phase 2 in_progress——组 A–G main 面全落 + 红①–红⑩留痕全[红③④⑤⑥⑦ focused 钉缺口已清零：六类 18 钉 + 变异探针红证 + 恢复绿复跑 EXIT=0]，见 Phase 2 checklist 与 `_tmp/red-green-2519-summary.md`）
> Last Reviewed: 2026-10-31
> Draft Review R1: agent_2c67f62f @ 2026-10-31 —— 无 Blocker；2 Major（M1 同号异站防混、M2 gate3 证据锚点 :274→:58）+4 Minor——M1/M2/m1/m2 协调方修复闭环，m3/m4 转设计文档 Phase 1 义务；修订转 active
> Design Review R1: agent_5ee01926 @ 2026-10-31 —— FAIL（1 Blocker[B1 裁定①终态摘除序自相矛盾/丢写窗未闭合] + 3 Major[AR-3 事实前提漂移/活跃段超 cap 无 fallback/查询面行为变化未登记] + 6 Minor）→ 撰写者修订：B1 采备选(b) 精确化（terminal 标记+drain 同锁临界区→锁外 persist→remove→标记后锁内拒绝），M1–M3/m1–m6 全落
> Design Review R2: agent_5ee01926 @ 2026-10-31 —— PASS（0B/0M；2 watch-only W1 persist 失败序句级补文[已落设计文档]/W2 Goals 判据指针[已落本 plan]）
> Source: roadmap 工作项 8.25（R2-16，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L167，Status=todo[本 plan 立项时回写为「todo（plan 2519 drafted，待 draft review）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-16 行（L90 全文：finding ID 清单 10 项[3.1-C1-008；3.1-C5-015；3.1-C4-010；3.1-C7-022；3.1-C5-008；3.1-C3-011；3.1-C3-028；3.1-C3-021；3.1-C7-012；3.1-C4-015] / 模块与验证命令 nop-ai `./mvnw test -pl nop-ai -am` / Owner Doc=`docs-for-ai/03-modules/nop-ai.md` / PA=无 / 依赖「R1-04（取消契约原语）；软依赖 R1-02/R1-03（globalTimer 约束解除）；软依赖 R1-06（同仓沙箱面先行）」/ exit criteria 增补：**MA-03 F4 终态清理 29 站点（gate3 反向钉死改写同批：remove no-op 钉+F4 断言补全）；MA-04 spawn 池 close 接线（C4-015 同批）；MA-05/MA-08 取消契约补齐（依赖 R1-04 bindCancel）；MA-09 方言协议族单修复批；MA-11 与 R1-08 consumeAwaitTimeout 契约对齐；MA-19 接管锁 abort 语义（R12 fencing）**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 3.1 审计目录 `ai-dev/audits/2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency/`（C1-shared-mutable-state.md + C1-round2.md[C1-008]、C5-memory-bounds.md[C5-008/015]、C4-thread-pools.md[C4-010/015]、C7-long-running.md[C7-012/022]、C3-async-chains.md + C3-round2.md[C3-011/028] + C3-round3.md[C3-030/031/032]、merge-adjudication.md §一 MA-02/03/04/05/06/08/09/11/19 + §五 裁定 + 台账、C10-test-protection.md[C10-002/003/005/006/007/009/010/017]）；C10 映射 = 跨模块裁定 `inventory-p2-c10.md` §二 WI 3.1 节 :789（**3.1（19，随本体全映射）**——本批仅承载 10 主 ID 对应成员面 8 条 C10，其余归 R4-21/R4-32/G49/已 fixed 各本体]）；P1 台账 = cra-index `ai-dev/audits/cra-index.md` :240–248（live 2026-10-31 回读：R2-16 open 九行 = :240[C1-008]/:241[C5-015]/:242[C4-010]/:243[C7-022]/:244[C5-008]/:245[C3-011]/:246[C3-028]/:247[C3-021]/:248[C7-012]；C4-015 为 P2 台账 G51 行 :435 搭车成员不占 P1 行；:239[C3-001]/:249[C4-011]/:250[C7-001]/:251[C7-002]/:252[MA-17] 已 fixed[R2-02/R1-03/R1-06] 不属本批）；执行基线 @ `3cf64bc3d7`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.24 已 done，plan 2495–2518 均已完成或收口；**本 Baseline 全部行号为该基线 live 回读值，2026-10-31 亲读，执行前如再漂移以 live 为准并登记**）
> Related: plan 2499（completed——R1-04 bindCancel 单点改造+上下文传播统一：**本批硬依赖已解除**，MA-05/MA-08 取消契约补齐直接消费 `ICancelToken`/`FutureHelper.bindCancel` 原语，SPI ×11 回归面留证在案）；plan 2497/2498（completed——R1-02/R1-03 池设施/消费方批：globalTimer 约束解除已生效[MA-16 形态收窄裁定]，`AgentDispatchExecutor` C4-011 已修为有界队列+fail-loud 先例）；plan 2501（completed——R1-06 沙箱族批：同仓沙箱面先行已满足，MA-17/C7-014 已 fixed 不属本批）；plan 2505（completed——R2-02 流式取消/期限两侧同批：3.1-C3-001/C7-001/C7-002 已 fixed，流式主链取消契约原语与本批 MA-05/MA-08 消费同源）；plan 2503（completed——R1-08 Debezium 批：**MA-11 consumeAwaitTimeout 契约对齐互注在案**[cra-index :247/:268 双侧]——4.1-C8-002 已落地 Local 失败派发契约[attempt=0/guarded 回调]，本批 MA-11 实施时按 4.1 merge-adjudication §五-6 对齐）；plan 2496（completed——MA-07 平台绝缘层批：`SessionLockRenewal.renewOnceSafe` catch 已由 RuntimeException 扩为 Throwable[护栏面，见勘误 3]——本批 MA-19 在其上补 abort 语义不回退该护栏）；**下游**：R4-21（8.61，G52 前半 10 项，**硬依赖本批**——批末须回写就绪注记）与 R4-24（8.5x，G62）consumeAwaitTimeout 对齐另一侧（4.1-MA-01）；结构模板 = plan 2518（PA 判定表/勘误登记/红绿判据/C10 半边口径/条件申报条款）与 plan 2517
> Protected Area 授权：批次 PA=**无**（batch-proposal R2-16 行 PA 列显式「无」）。**批内条件申报条款（两处，条件触发制）**：① **MA-02/MA-05 修复若需 `io.nop.api.core.*`（nop-api-core）新增设施或 SPI 签名变更**——预设 = 纯消费 R1-04 已落地原语（`FutureHelper.bindCancel`/`ICancelToken.appendOnCancel`）零签名变更；若 Phase 1 裁定确需新增平台 API，触发 AGENTS.md「nop-api-core plan-first」，须在本 plan PA 行 + 设计文档 + draft review 完成申报并列全 SPI 回归面（bindCancel/ICancelToken 全实现逐类列举）方可实施；② **MA-09 方言修复若需演进 `ILlmDialect`/`ChatStreamChunk` 契约**——预设 = nop-ai-core 模块内 `parseStreamChunk` 多 chunk 语义收敛（返回 List 或多次回调，一次定夺）+ `ChatStreamChunk` 现有字段承载，`ILlmDialect` 接口零签名变更；若裁定演进为接口签名/新增抽象方法（全方言实现 + 外部实现方回归面），须在 draft review 申报并登记迁移方案。**头部预设：10/10 成员零 orm.xml 触碰、零 `_` 生成物触碰**（PA 判定表逐一核实）；站点分布于 `nop-ai/nop-ai-agent`（八单）与 `nop-ai/nop-ai-core`（方言一单九成员）两模块，E3 命令 `./mvnw test -pl nop-ai -am` 触达 nop-ai 聚合器及上游链。

## Purpose

关闭 R2-16 批的 10 项已确认缺陷（P1×9 + P2×1[C4-015 为 P2-G51 搭车成员，batch-proposal 明文「C4-015 与 MA-04 同批」]；cra-index open 九行 :240–248 批次列=R2-16，live 2026-10-31 回读全 open；WI 3.1 单元 50 工单中本批 = 修复优先级 P1 全集中仍 open 的 9 张工单 + 1 张 P2 同批搭车——P1 已 fixed 三张[C3-001/C7-001/C7-002→R2-02、C4-011→R1-03、MA-17→R1-06]，MA-07 已移交 WI 2.1，其余 P2×29 归 R4-21/R4-32/G49/deferred 各本体）。核心结果面有九：①**引擎注入目标登记窗口**（C1-008←C3-014）——resume/wake/restore 三路径零注册 + 暂存分支裸 ArrayList，CME/撕裂持久化/注入丢失。②**F4 引擎终态清理 29 站点**（C5-015 族首）——session store put-only + checkpoint/账本/注册表无终态释放，历史会话堆内永驻；gate3 反向钉死改写同批（remove no-op 钉 + F4 断言补全，C10-003）。③**spawn 编排池生命周期**（C4-010←C3-022/C7-032 + C4-015 同批 + CL-036）——per-invocation orchestrator 全仓无 close 每次泄漏 max(2,NCPU) 池 + close 无 drain 丢已提交工作。④**成员/spawn 超时取消契约**（C7-022←C7-023/C3-025/C4-014）——5 路径仅 2 实现 cancelSession，挂死成员僵尸化 + RECLAIM 双执行；消费 R1-04 bindCancel 原语。⑤**长会话累积上界**（C5-008←C5-010/011+C7-031 拆分面+CL-006/063/026/070/077）——journal 恢复 ~4× 物化 + byWatermark 全量常驻 + 磁盘无界 + spill/归档线程安全。⑥**超时取消传播**（C3-011←C3-004/007/012+C4-005+C7-024+CL-005）——机械超时/中断不向底层工具执行传播，kill 通道无超时路径接入，cancelCall javadoc 契约无实现，幽灵 bash/容器/HTTP 持续运行。⑦**方言流式协议族单修复批**（C3-028←C3-030/031/032）——三站点 tool_call 截断 + 流式 usage 全链失活 + Ollama 空串抢占 + 带内错误通道缺失，缺省 stream=true 主路径确定性丢数据。⑧**DBMessageService 治理四轴**（C3-021←C7-025+C1-019+CL-040/024/069）——单线程 poller 内联消费 call-agent 120s 全模块冻结 + 毒消息无 DLQ 停摆 + 订阅顶替/窃取 + claim 无世代 fencing；与 R1-08 consumeAwaitTimeout 契约对齐。⑨**接管锁 abort 语义**（C7-012）——续期异常仅 WARN 无限容忍，非对称分区永不自 abort，fencing 失效双执行窗口。批末 roadmap 8.25 → done，cra-index 九行收敛 + P2 搭车行注记 + C10 成员面注记，R4-21 硬依赖就绪注记，MA-11↔R4-24 对齐注记双侧闭合。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-16 = 10 项成员（C1-008/C5-015/C4-010/C7-022/C5-008/C3-011/C3-028/C3-021/C7-012 = P1×9；C4-015 = P2[G51 行明文「与 MA-04 池上提同批」「C4-015 随 P1 3.1-C4-010 同批」]）；cra-index live 2026-10-31 回读 R2-16 批次列 open 九行 = :240–248，C4-015 见 P2 台账 G51 行 :435；roadmap 8.25 行 Status=todo。依赖核对（live 2026-10-31）：**硬依赖 8.5（R1-04）已 done（plan 2499 closure audit PASS with corrections）**；软依赖 8.3（R1-02）done（plan 2497）、8.4（R1-03）done（plan 2498）、8.7（R1-06）done（plan 2501）——全部依赖解除，基线门禁满足。
- **同单元批外面登记（Non-Goal 零触碰）**：WI 3.1 其余工单归 R4-21（8.61，G52 前半 10 项：MA-10/12/13/14/15/16/18/20/21+C1-010）、R4-32（2.5-C7-013 拆出 + 3.1-C7-004 拆出[R1 勘误：3.1-C7-013 为本批 C5-015 族吸收成员，与 R4-32 拆出面 2.5-C7-013 同号异站防混]）、G49 deferred 束与 watch-only（C1-027/C3-008）、已 fixed 五张（C3-001/C7-001/C7-002/C4-011/MA-17）——本批 diff 不触碰其任何站点。**接缝转达义务**：①C7-018（P2，MA-19 族内租约期限墙钟成员）不在本批 finding 清单，归 P2 束——本批裁定⑨若触及 expiresAt 语义须登记接缝不代做；②R4-21 的 MA-16「不得指向 globalTimer」约束已随 R1-03 解除[plan 2498 收敛注]，本批新增池占用面同样遵守收窄后形态（「阻塞/长任务体不得指向 globalTimer；满足 §3.1.1 豁免判据的轻量直连合法」）。
- **吸收成员（随主条目同 diff，不单列工单；merge-adjudication §一 + §五 裁定）**：C1-008←C3-014（P2 恢复路径注入目标缺失，MA-02 成员）；C5-015←C5-009/012/013/020 + C1-006/011/014/016/017/018/026 + C7-013/017/028/030/031（F4 族 29 站点，**gate3 反向钉死改写同批**[batch-proposal L274 + cra-index :58（R1 勘误：原引 :274 为 network C3-002 行，订正）]）；C4-010←C3-022（P1 异步终局不对称）+ C7-032（P2 三重登记统一 P1，§五.7 裁定）+ CL-036（resolveSpawnExecutor 惰性建池 check-then-act，随池上提加同步）；C7-022←C7-023/C3-025/C4-014（MA-05 族内取最严 P1 消解，§五.8 裁定；三案同 diff）；C5-008←C5-010（P1 每次压缩固化全量历史副本）+ C5-011（P2 spill 全文本驻留）+ C7-031（复合登记按 C7 复核子面拆分：快照面随 C5-010、spill 面随 C5-011，§五.2 裁定）+ CL-006/CL-063（journal 无轮转 + ioLock 声明失真）+ CL-026/CL-070（spill/双归档 HashMap 线程安全随案 CHM 化）+ CL-077（归档 put 在容错 try 外）；C3-011←C3-004（P2 幽灵重试）/C3-007（P2 requestTimeout 死契约）/C3-012（P2 LLM 幽灵调用）/C4-005（P2 embedding 链无取消通道）/C7-024（P2 call-agent async 无取消协调）+ CL-005（AgentToolDispatcher orTimeout 面 = C3-011 同点）；C3-028←C3-030（P1 usage 全链失活）/C3-031（P1 Ollama 空串抢占，**与 C3-028 Ollama 面两案必居其一同批**[C3 round3 复核]）/C3-032（P2 带内错误路由缺失）；C3-021←C7-025（P1 毒消息无限重投+队列停摆）+ C1-019（P2 consumers 静默替换+身份窃取）+ CL-040（单线程互饿 = C3-021 主输入）/CL-024（订阅三缺陷）/CL-069（claim 无世代 fencing + suspend 死标志）；C7-012←（族内 C7-018 为批外接缝，见上）。
- **族登记（family-clustering）**：MA-09 为「方言流式协议族单修复批」——C3-028/030/031/032 四案一次裁决（Anthropic/Responses 免疫声明复核为设计输入，C3 round2 第 3 轮建议面 a）；MA-19 与 WI 1.6 fencing 族家族关联不并案（merge-adjudication §八 :268）；C3-011 与 C3-009/C3-012 的 CL-021 移交面具体化站点[WI 2.1 平台绝缘层]登记协同不代做；MA-06 与 MA-03 共享引擎终态清理站点族——C5-008 修复面（journal/spill/归档上界）与 C5-015 修复面（终态释放接线）分属不同裁定但共享 gate3 清单扩容面（裁定②统一承载清单扩容，裁定⑤承载上界语义）；CL-IND-01（DB 自建表保留策略族）与 C5-009 修复批对齐注记随裁定②登记，本体归独立线索工单不代做。
- **C10 验收前置入批集（映射 SSOT = `inventory-p2-c10.md` :789 小结行 3.1（19）+ `C10-test-protection.md` 逐条；随本体同 diff 落地[E2]）**：
  - C10-005→C1-008（引擎注入/装配窗/恢复前置零并发用例；**含 gate3 反向钉死改写随裁定②**）；
  - C10-003→C5-015（F4 终态清理断言止于 checkpoint 单件 + **gate3 钉不完整清单**——`TestInvariantGate3EntryPointCleanup.java:87`/:136 字面钉 `"default void remove"` marker 且清理清单四组九串[R1 勘误：:130-138 实为 9 条 String 四组关注点]不含 sessionStore/memory/mailbox/backgroundRegistry，修复同 diff 扩清单+补全断言，**本批唯一允许的既有钉改写义务**）；
  - C10-007→C4-010/C7-022/C4-015（team-flow/spawn 僵尸与池泄漏零断言[正确兄弟被钉]）；
  - C10-006→C3-011（超时「会触发」被钉、「底层是否停」零断言）；
  - C10-002→C3-028（方言流式 fixture 矩阵：单 entry/零 usage/零 error/空串掩蔽）；
  - C10-009→C3-021（DBMessageService 治理四轴零用例[sweep 被钉]）；
  - C10-010→C7-012（租约异常分支与墙钟轴零用例；MA-16 半边归 R4-21 不属本批——半边口径登记）；
  - C10-017→C5-008（会话内归档与 journal 物化容量上界零断言）。
- **3.1 批前已收口面（live 形态登记，零触碰）**：C3-001/C7-001/C7-002（R2-02，plan 2505——流式主链取消闭合/双级 request 窗/聚合双上界/全程期限 watchdog/ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING fail-loud 在案，本批 MA-08 工具超时传播消费同一取消语义不回改）；C4-011（R1-03，plan 2498——AgentDispatchExecutor 有界队列+fail-loud 先例，本批裁定③池治理引用形态）；MA-17（R1-06，plan 2501——Docker 沙箱超时清理已修，本批 C3-011 幽灵执行面与其互补不重复）；plan 2496（MA-07 平台绝缘层——`SessionLockRenewal` Throwable 护栏在案）；C7-014（R1-06，提交 b14a502a0b——heartbeat 预算面已收口）。
- **平台设施与测试资产盘点（live 实读 @ `3cf64bc3d7`）**：
  - **先例范式可引用不重开**：R1-04 bindCancel 单点改造（取消原语消费面，plan 2499 裁定）；R1-03 有界队列+fail-loud 拒绝范式（C4-011/CL-025 先例）；plan 2505 R03「受管承载 cachedThreadPool/盘写异步化」范式（裁定⑤ journal 异步化引用）；`registry-lifecycle-contract.md` 裁定⑥（REGISTRY 契约域——裁定⑧ DBMessageService consumers 治理对齐注记零触碰，沿 2511/2512 先例口径）；DaoTaskStateStore 条带锁/租约形态菜单（2514 裁定⑥，裁定⑨ fencing 形态引用不重建）。
  - **owner doc 现状**（2026-10-31 亲读）：`docs-for-ai/03-modules/nop-ai.md`——已有引擎超时/可靠性配置表（:119-123）、shell pipeline 池契约（plan 2501 面）、ToolManagerImpl 并发契约节（plan 2501 面）；**无 agent 编排/会话生命周期并发契约节（注入登记/终态清理/spawn 池生命周期/成员超时取消/工具超时传播/方言流式协议/DB 传输治理/接管锁 abort 语义）、无 journal 累积上界契约句**——裁定①–⑨ owner doc 补节主落点。
  - **测试资产（live 核实）**：`TestInvariantGate3EntryPointCleanup`（gate/，:87/:136 反向钉）、`TestDefaultAgentEngineCancel`/`TestDefaultAgentEngineConcurrencyGuard`/`TestEngineLifecycleAndCheckpointBounded`/`TestSessionLifecycleLockGate`/`TestSessionLifecycleSaveBeforeRelease`/`TestFanOutFutureLifecycle`（engine/）；`TestInMemorySessionStore`（:56 remove 钉）/`TestDBSessionStore`（20 @Test）/`TestFileBackedSessionStore`（session/）；`TestMemberSpawner`（team/）、`TestTeamTaskFlowOrchestratorAsync`/`TestAsyncSpawnStep*` 族/`TestAsyncTeamTaskFlowEndToEnd`（team/flow/）；`TestFileBackedCheckpointManager`（17 @Test）/`TestDBCheckpointManager`/`TestCheckpointJournalSnapshotFormat`（reliability/）；`TestOpenAiDialect`/`TestOllamaDialect`（:191 tool_calls 参数保留钉）/`TestGeminiDialect`/`TestAnthropicDialect`/`TestResponsesDialect`/`TestLlmDialectErrorResponse`（nop-ai-core dialect/）；`TestDBMessageService`/`TestDBMessageServiceCrossInstance`/`TestDBMessageServiceInsulation`/`TestDBMessageServiceReliability`（message/）。
- **模块聚合形态与勘误**：审计单元标签「nop-ai-core-nop-ai-agent」覆盖双工程——repo 实际 = `nop-ai/` 聚合器下 `nop-ai-core`（方言族站点 + LLM 基础面）与 `nop-ai-agent`（其余九单站点）；**「nop-ai-core dialect 站点在 nop-ai-agent 审计目录内登记」为覆盖分区口径非模块归属**（C3 round2/round3 明文 dialect 文件属 nop-ai-core X1 分区邻接面），E3 命令 `./mvnw test -pl nop-ai -am` 触达两模块及上游链。

### 对齐纪律显式条款

- **MA-04 × C4-015 同变更集**：spawn 池 close 接线（per-invocation 泄漏修复）与 drain 语义（shutdownNow 丢 queued 工作→有界 drain+dispatch future settle）同一变更集承载——池生命周期一次修（batch-proposal R2-16 行增补段明文「MA-04 spawn 池 close 接线（C4-015 同批）」；G51 行明文「C4-015 与 MA-04 同批」）；CL-036 惰性建池同步随池上提同 diff。
- **MA-05/MA-08 取消契约消费 R1-04 原语（硬依赖已解除）**：成员/spawn 超时与工具超时的底层取消一律经 plan 2499 已落地的 bindCancel/ICancelToken 通道，**禁止新造并行取消机制**；若现有原语不覆盖（如进程级 kill 通道接入），在 Phase 1 裁定⑥中一次定夺并检查条件申报①是否触发。
- **MA-09 方言协议族单修复批（C3-028 × C3-031 两案必居其一）**：Ollama 站点截断（:341 get(0)）与空串抢占（:327 分支阻断）共享同一 else-if 阶梯——修复必须一次裁决消解两案（C3 round3 复核明文「两案必居其一同批」）；usage 四方言站点与错误通道五方言站点同变更集落地（族单修复批裁定）；Anthropic/Responses 免疫声明复核为设计输入（C3 round2 第 3 轮建议面 a）。
- **MA-11 × R1-08 consumeAwaitTimeout 契约对齐（双侧互注在案）**：本批裁定⑧实施时按 4.1 merge-adjudication §五-6 对齐——4.1-C8-002 已落地 Local 失败派发契约（attempt=0/guarded 回调，plan 2503 Phase 4），4.1-MA-01（R4-24）为 consumeAwaitTimeout 对齐另一侧；本批落侧后须在 cra-index :247/:268 双行注记闭合；4.1 REGISTRY 契约域（`registry-lifecycle-contract.md` 裁定⑥）对齐注记零触碰。
- **MA-19 与 WI 1.6 fencing 族家族关联不并案**：abort 语义修复模式对齐 WI 1.6 族（续租缺失/CAS 守卫），载体与模块不同零重复立项；plan 2496 Throwable 护栏零回退（裁定⑨在护栏之上补「异常升级→abort」分支，不得退回纯 WARN）；C7-018（P2 墙钟）批外接缝登记。
- **MA-02 × gate3 反向钉死改写同批（唯一允许的既有钉改写）**：`TestInvariantGate3EntryPointCleanup` :87/:136 字面钉 `"default void remove"` 且清理清单四件（runningExecutions/takeover lock/renewal/checkpoint.remove）不含 sessionStore——修复终态清理接线后**同 diff 改写**：清单扩容（sessionStore/memory/mailbox/backgroundRegistry 等 F4 29 站点承载面）+ F4 断言补全（各容器终态 isEmpty 参数化断言，C10-003 建议口径）；改写语义对照留痕。
- **跨单元同号防混**：C7-012（2.5-C7-012 已由 plan 2518 收口[fencing 版本谓词面]与本批 3.1-C7-012[接管锁 abort]同号异站零关联）；C1-008/C5-008/C3-011/C3-021 等均带 3.1 前缀——cra-index 回填时逐行带 3.1 前缀核对；C10-010 的 MA-16 半边归 R4-21（G52），本批只承载 MA-19 半边。

### 10 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `3cf64bc3d7` 2026-10-31 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级；吸收成员） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 3.1-C1-008（P1，MA-02 主；←C3-014；C10-005） | `DefaultAgentEngine.java` + `AgentSessionLifecycle.java` + `AgentSession.java`（nop-ai-agent `engine`/`session`） | `runningInjectionTargets` CHM 声明 :127；`injectInput` get :617（get→addPendingInjection :621 非复合原子）；`doExecute` put :774；终局 finally remove :1010；**resumeSession :245 / wakeSession :450 / restoreSession :605 三路径全程零注册**（live grep `runningInjectionTargets` 于 AgentSessionLifecycle 零命中）；裸 ArrayList `AgentSession:77` + `addPendingInjection` :83-89 + clear :92 | 引擎注入目标登记窗口：三恢复路径零注册→注入静默丢失；暂存分支裸 ArrayList 并发变异 CME；get→enqueue 与 remove→terminal-save 无复合原子性→撕裂持久化 | 裁定①（C10-005 随同 diff）：三恢复路径在执行窗对称注册/注销（与 doExecute :774/:1010 同型 finally 对）；注入链复合原子化（get→enqueue 原子化或 per-session 注入锁——一次定夺）；pendingInjections 载体同步化（synchronizedList/CopyOnWrite/会话锁内变更——一次定夺）；injectInput 摘除先于终态保存的丢写窗口（←C3-014/CL-061 面）闭环声明 |
| 2 | 3.1-C5-015（P1，MA-03 族首；F4 族 29 站点←C5-009/012/013/020+C1-006/011/014/016/017/018/026+C7-013/017/028/030/031；C10-003） | `InMemorySessionStore.java` + `DBSessionStore.java` + `FileBackedSessionStore.java` + `DefaultAgentEngine.java` + gate 测试（nop-ai-agent `session`/`engine`/`gate`） | 三实现 put-only：InMemory `sessions` :21-25（remove :34-35 **已实现未接线**）；DBSessionStore `sessions` :79 / remove :198-203（已实现）；FileBackedSessionStore `sessions` :106 / remove :177-182（已实现）；引擎终态清理清单 gate3 钉四件不含 sessionStore（`TestInvariantGate3EntryPointCleanup.java:87`/:136 字面钉 `"default void remove"`） | F4 引擎终态清理族：session store 三实现 put-only（remove 纯接线）+ checkpoint/账本/注册表无终态释放——历史会话（含完整消息历史）堆内永驻 | 裁定②（C10-003 随同 diff + **gate3 反向钉死改写同批**）：终态清理清单扩容（sessionStore 三实现接线 + F4 29 站点承载面逐容器登记）；**gate3 改写**：清单扩容 + F4 断言补全（参数化遍历引擎装配容器清单、终态执行断言全部容器 isEmpty——C10-003 建议口径，改写语义对照留痕）；终态 save 反向 pin（C5-015 族内「终态 save 反向 pin」子面）随改写消解；CL-IND-01 对齐注记登记 |
| 3 | 3.1-C4-010（P1，MA-04 主；←C3-022+C7-032[三重登记统一 P1]；CL-036；C10-007） | `TeamExecuteFlowExecutor.java` + `TeamTaskFlowOrchestrator.java`（nop-ai-agent `tool`/`team.flow`） | per-invocation 构造 :234 `new TeamTaskFlowOrchestrator(...)` 后丢弃，**无 close 调用**（live grep `close()` 于 TeamExecuteFlowExecutor 零命中）；`TeamTaskFlowOrchestrator.close()` :448-458 存在但生产零调用；`resolveSpawnExecutor` :468-478 惰性建池 check-then-act 无同步（CL-036；对照 `DefaultAgentEngine.getAgentExecutor` synchronized 先例） | spawn 编排池泄漏：每次含 spawn 节点的 team-execute-flow 成功执行泄漏 max(2,NCPU) 固定池常驻线程 | 裁定③（C10-007 随同 diff；**与裁定④[C4-015] 同变更集**）：池上提一次定夺——(a) 预设=executor 生命周期上提至 TeamExecuteFlowExecutor 单例/注入载体（池与持有者生命周期对称）；(b) 备选=per-invocation try/finally close（泄漏窗口收窄为调用期）；CL-036 惰性建池 synchronized/double-check 随池上提同 diff；C3-022 异步终局不对称面 + C7-032 分叉收敛面闭环声明 |
| 4 | 3.1-C4-015（P2 搭车[G51]；C10-007） | `TeamTaskFlowOrchestrator.java` + `TaskDispatchCoordinator.java`（nop-ai-agent `team.flow`/`team.scheduler`） | `close()` :448-458 `shutdownNow` 无 drain——queued spawn 任务被丢弃、其 dispatch future 永不 settle → `inFlightDispatches` 条目泄漏、任务滞留 CLAIMED；`TaskDispatchCoordinator.shutdownOwnSpawnExecutor` :102-107 `shutdownNow` 无 awaitTermination（`TeamTaskSchedulerDaemon.stop` :257 调用） | spawn 池 stop 无 drain：取消/关闭路径不可达已提交 spawn 工作，inFlightDispatches javadoc 不变式恰被 stop 打破 | 裁定④（C10-007 随同 diff；**与裁定③同变更集**）：drain 语义一次定夺——(a) 预设=有界 drain（shutdown → awaitTermination 上界 → 残余任务显式 fail/settle[dispatch future completeExceptionally]，禁静默丢弃——rule 24）；(b) 备选=shutdown 后逐任务取消回调 settle；daemon 侧 :102-107 补 awaitTermination 对称面；`TestFanOutFutureLifecycle` 既有钉保持绿 |
| 5 | 3.1-C7-022（P1，MA-05 主；←C7-023/C3-025/C4-014[族取最严 P1，§五.8]；C10-007） | `DefaultMemberSpawner.java` + `TeamTaskFlowOrchestrator.java` + `MemberFanOutDispatcher.java`（nop-ai-agent `team`/`team.flow`） | `DefaultMemberSpawner` bounded get :188、**TimeoutException 分支 :191-198 仅 SPAWN_FAILED 返回、spawnedSessionId 未引用未取消**（live 亲读核实）；`TeamTaskFlowOrchestrator` overall deadline :634 `orTimeout` 只断 DAG 不取消成员会话；【勘误 5】`MemberFanOutDispatcher.executeBoundMember` :313-331 **已有 orTimeout+cancelSession(forced=true)**——fan-out 路径已实现取消，原审计「5 路径仅 2 实现」的站点分布以 live 为准收窄 | 成员/spawn 超时取消契约 F3 漏修族：超时不取消 spawned 会话——挂死成员僵尸化（永久占引擎槽与 LLM 配额）+ RECLAIM 后同任务双执行 | 裁定⑤（C10-007 随同 diff；**消费 R1-04 bindCancel 原语**）：超时→cancelSession 补齐一次定夺——(a) 预设=DefaultMemberSpawner TimeoutException 分支补 `agentEngine.cancelSession(spawnedSessionId, reason, forced)`（吸收 C7-023 同型面）；(b) overall deadline 到期汇聚取消全部未 settle 成员会话（TeamTaskFlowOrchestrator :634 侧）；C3-025 双执行窗面 + C4-014 单 bound 裸 execute 面随族闭环；**MA-16 约束继承**（新增池/线程占用面遵守 R1-03 收窄后形态） |
| 6 | 3.1-C5-008（P1，MA-06 主；←C5-010+C5-011+C7-031[拆分面]+CL-006/063/026/070/077；C10-017） | `FileBackedCheckpointManager.java` + `CheckpointJournalWriter.java`/`CheckpointJournalReader.java` + `InMemorySpillStore.java` + `InSessionCompactionSnapshotArchive.java`/`InSessionCompactionArchive.java`（nop-ai-agent `reliability`/`compact`） | `byWatermark` 全量常驻 :102（put :159 / remove 仅随 session 清理 :211）；journal 磁盘无界（类 javadoc :80 自认「no rotation/cap」）；恢复全量物化 ~4×（`CheckpointJournalReader.parseAll` 全文 String+substring+sections+lines/fields）；`snapshotCache` :103 / `loadedSessions` :105；spill `InMemorySpillStore.store` HashMap 无同步（CL-026/070）；归档 put 在容错 try 外（CL-077）；ioLock 声明失真（CL-006/063） | 长会话归档/spill/journal 无界累积 + 恢复全量物化 4× 放大：防压缩 OOM 机制自身成为 OOM 源 | 裁定⑥（C10-017 随同 diff；**plan 2505 盘写异步化范式引用不重开**）：(a) journal 轮转/上界（尺寸或条数 cap+归档截断——参数化上界，一次定夺）；(b) byWatermark 会话级淘汰（终态/压缩后清理，与裁定②终态清理清单协同）；(c) 恢复流式化或分批解析（消解 4× 放大峰值——一次定夺）；(d) spill/归档容器 CHM 化（CL-026/070）+ 归档 put 入容错 try（CL-077）+ ioLock javadoc 修订（CL-006/063 声明面）；C5-010/C5-011/C7-031 拆分面闭环声明 |
| 7 | 3.1-C3-011（P1，MA-08 主；←C3-004/007/012+C4-005+C7-024；CL-005；C10-006） | `AgentToolDispatcher.java` + `LlmCallCoordinator.java` + `SingleTurnExecutor.java` + `ReActAgentExecutor.java` + `BashSandboxProgressTracker.java`（nop-ai-agent `engine`/`tool`） | slice 等待循环 `f.cancel(true)` :205-210/:263-268；`cancelCall` :328-336 仅 `future.cancel(true)` 无进程级接入；工具超时 cap :413-427；`LlmCallCoordinator.callChatWithTimeout` :785 / `f.cancel(true)` :806；`SingleTurnExecutor.callWithTimeout` :183；**kill 通道 `tracker.cancelExecution` 生产调用仅两处**：`DefaultAgentEngine.cancelBackgroundViaTracker` :693-698（background stop）+ `ReActAgentExecutor` :1677（heartbeat abandon）——超时/中断路径零接入（【勘误 6】原审计「仅 heartbeat 一处」→ live 两处，机制结论不变）；cancelCall javadoc 进程级 kill 契约无实现 | 超时取消传播断链：机械超时/中断不向底层工具执行传播——幽灵 bash/容器/HTTP 持续运行；cancelCall javadoc 契约无实现 | 裁定⑦（C10-006 随同 diff；**消费 R1-04 原语 + kill 通道现有接线**）：超时→底层取消传播一次定夺——(a) 预设=cancelCall/wait-loop cancel 路径接入 `IToolProgressTracker.cancelExecution`（复用 heartbeat 同一通道，判据=tracker 已装配则超时/取消必经 kill）；(b) HTTP/LLM 面消费 bindCancel/请求级取消（C3-004/007/012/C4-005/C7-024 吸收面逐一定夺：实现或显式声明边界）；cancelCall javadoc 与实现对齐（契约句修订）；「底层是否停」断言补齐（C10-006） |
| 8 | 3.1-C3-028（P1，MA-09 主；←C3-030+C3-031+C3-032[族单修复批]；C10-002） | `OpenAiDialect.java` + `GeminiDialect.java` + `OllamaDialect.java` + `AnthropicDialect.java` + `ResponsesDialect.java` + `ToolCallAccumulator`（nop-ai-core `io.nop.ai.core.dialect`） | OpenAI tool_call 单 entry return :345 + buildBody :151-184 未启用 `stream_options.include_usage`（live grep 零命中）+ parseStreamChunk :248-305 无 usage；Gemini 首 part return :496/:512 + 无 usageMetadata；Ollama `content != null` 空串抢占 :327（thinking/tool_calls 分支不可达，C3-031）+ `get(0)` 截断 :341；Anthropic error→`"[ERROR] …" text DELTA` :646-661 污染 assistant 内容 + usage 残缺 :538-546；Responses 免疫对照（:515-540/:571-574）；五方言带内错误通道缺失（C3-032） | 方言流式协议族：缺省 stream=true 主路径确定性丢数据——并行工具调用截断、usage 四链消费整体失活、Ollama 思考/工具全量丢失、错误信号静默丢或内容污染 | 裁定⑧（C10-002 随同 diff；**族单修复批一次裁决**；条件申报②适用面）：(a) 多 tool_call 单事件全枚举（parseStreamChunk 多 chunk 语义收敛——OpenAI/Gemini/Ollama 三站点同改，Anthropic/Responses 免疫声明复核）；(b) Ollama 阶梯判定修正（空串掩蔽消解——C3-028×C3-031 两案一次消解）；(c) usage 四方言接入（OpenAI include_usage 启用 + Anthropic message_start/cache token 补齐 + Gemini usageMetadata + Ollama eval_count——消费链四条复原）；(d) 带内错误通道（类型化错误 chunk/终止信号，禁文本污染——Anthropic :646-661 改型）；方言 fixture 矩阵钉（C10-002 四轴） |
| 9 | 3.1-C3-021（P1，MA-11 主；←C7-025+C1-019；CL-040/024/069；C10-009；**R1-08 对齐互注**） | `DBMessageService.java`（nop-ai-agent `message`） | 单线程 poller :208-213（`newSingleThreadScheduledExecutor("db-message-poller-…")` + `scheduleWithFixedDelay(this::pollAllTopics,…)`）内联执行全部 topic 消费回调——call-agent handler 阻塞期间全模块消息冻结；毒消息无限重投队列停摆（C7-025，无 DLQ）；`consumers` 静默替换/身份窃取（C1-019/CL-024）；claim 无世代 fencing + suspend 死标志（CL-069）；sweep 与投递同线程互饿（CL-040） | DBMessageService 单线程 poller 内联消费：call-agent join 120s 期间全模块消息冻结 + 毒消息无 DLQ 停摆 | 裁定⑨（C10-009 随同 diff；**R1-08 consumeAwaitTimeout 契约对齐**[4.1 merge-adjudication §五-6；4.1-C8-002 已落地 attempt=0/guarded 回调契约]）：治理四轴一次裁决——(a) 投递与 poll 分离（poller 只取消息、独立投递执行器有界化——参照裁定④池形态与 R1-03 有界拒绝范式，拒绝策略显式）；(b) 毒消息处置（重投上界+DLQ/死信落账+guarded 失败回调对齐 4.1 契约）；(c) consumers 注册治理（顶替显式拒绝/身份校验/cancel 对称——C1-019/CL-024）；(d) claim 世代 fencing（CL-069——形态引用 2514 裁定⑥菜单不重建）；consumeAwaitTimeout 对齐注记落 cra-index :247/:268 双侧 |
| 10 | 3.1-C7-012（P1，MA-19 主；族内 C7-018 批外接缝；C10-010） | `SessionLockRenewal.java` + `DbSessionTakeoverLock.java`（nop-ai-agent `engine`/`runtime.lock`） | `renewOnceSafe` catch Throwable→LOG.warn 无升级 :74-85（【勘误 3】plan 2496 已把 catch 自 RuntimeException 扩为 Throwable[MA-07/C4-007 护栏]，行号 :74-77→:74-85，**仅 false 返回触发 handleLeaseLost :86-98 的协议窄化不变**）；`DbSessionTakeoverLock.tryRenew` SQLException→NopAiAgentException（RuntimeException 子类）:300-318——异常结局落入无限容忍分支；lease 30min/renew 10min 缺省（DefaultAgentEngineConfig :154-155） | 接管锁续期异常无限容忍：SQLException 仅 WARN 无限重试——非对称分区期间永不自 abort，fencing 语义整体失效双执行窗口 | 裁定⑩（C10-010 随同 diff；**WI 1.6 fencing 族家族关联不并案**；plan 2496 护栏零回退）：续期异常升级→abort 语义一次定夺——(a) 预设=连续 N 次异常或不可分类异常（DB 不可达 vs 瞬时抖动判据）升级 `handleLeaseLost` 同型 abort（lease 未知即视为可能丢失——fail-safe 方向）；(b) 瞬时异常豁免窗口（计数+上界）显式配置化；C10-010 异常分支/墙钟轴断言补齐（墙钟半边 C7-018 批外接缝登记不代做） |

### PA 判定表（10 成员逐一，orm/_生成物/跨模块公共 API/nop-api-core 触碰与否；live 2026-10-31 核实）

| # | Finding | 站点模块 | 站点形态 | 触 `model/*.orm.xml`？ | 触 `_` 生成物？ | 触跨模块公共 API / nop-api-core？ | 判定依据与条件申报 |
|---|---|---|---|---|---|---|---|
| 1 | C1-008 | nop-ai-agent main java | 注入注册窗口 | 否 | 否 | 否（DefaultAgentEngine/AgentSession 模块内） | 纯 Java 并发修复；注入链模块内 |
| 2 | C5-015 | nop-ai-agent main java + gate 测试 | 终态清理接线+gate3 改写 | 否 | 否 | 否（ISessionStore impl 模块内；remove 已在接口） | 接线面纯模块内；gate 测试改写随 C10-003 义务 |
| 3 | C4-010 | nop-ai-agent main java | 池上提/close 接线 | 否 | 否 | 否（TeamExecuteFlowExecutor 模块内） | 生命周期模块内；globalTimer 只读消费不新增占用 |
| 4 | C4-015 | nop-ai-agent main java | drain 语义 | 否 | 否 | 否 | 同 #3 |
| 5 | C7-022 | nop-ai-agent main java | 超时取消补齐 | 否 | 否 | **否（纯消费 R1-04 原语）**——cancelSession 为 IAgentEngine 既有方法 | 条件申报①：若需 nop-api-core 新增设施→批内 PA 申报①（列全 SPI 回归面） |
| 6 | C5-008 | nop-ai-agent main java | journal 上界/流式化 | 否 | 否 | 否 | 模块内容器/IO 治理；2505 异步化范式引用 |
| 7 | C3-011 | nop-ai-agent main java | 取消传播/kill 接入 | 否 | 否 | **否（IToolProgressTracker 为模块内接口）** | 条件申报①同 #5；沙箱 kill 通道消费 R1-06 已修面零回改 |
| 8 | C3-028 | nop-ai-core main java | 方言协议族 | 否 | 否 | **nop-ai-core 模块内 SPI（ILlmDialect），非 nop-api-core** | 条件申报②：若 ILlmDialect 接口签名演进→draft review 申报+迁移方案；预设零签名变更 |
| 9 | C3-021 | nop-ai-agent main java | DB 传输治理四轴 | 否 | 否 | **否（IMessageService/IMessageConsumer 纯消费，R1-08 契约对齐面）** | 条件申报①同 #5；4.1 REGISTRY 契约域对齐注记零触碰 |
| 10 | C7-012 | nop-ai-agent main java | 续期异常升级 | 否 | 否 | 否（ISessionTakeoverLock 模块内） | fail-safe 语义模块内；plan 2496 护栏零回退 |

**结论：头部零 orm 触碰、零 `_` 生成物触碰声明成立**（10/10 = `src/main/java` 纯代码修复 + gate 测试改写；站点分布于 nop-ai-agent/nop-ai-core 两模块，无 ORM 模型与生成物——live find 核实）。两处条件申报条款见头部 PA 行（C7-022/C3-011/C3-021 nop-api-core 形态 / C3-028 ILlmDialect 签名演进形态）。

### Baseline 勘误（输入不一致登记）

1. **模块归属勘误**：审计单元标签「nop-ai-core-nop-ai-agent」为双工程覆盖口径——方言族站点（C3-028 全部成员）实际在 `nop-ai/nop-ai-core`（`io.nop.ai.core.dialect`），其余九单在 `nop-ai/nop-ai-agent`；C3 round2/round3 的 X 分区叙述以「本批分区」表述，非模块归属声明；E3 命令 `./mvnw test -pl nop-ai -am` 触达两模块，与 batch-proposal 一致。
2. **C1-008 站点行号**：审计引 `doExecute put:774 / remove:1010 / injectInput:617`、`AgentSession:77/:83-89`——live 回读全部吻合（:127/:617/:621/:774/:1010；:77/:83-89/:92）；resume :245、wake :450、restore :605（审计引 :299/:488/:892 为三路径锁门行号——live 锁门为 :296/:485 及 restore 对应行，±10 内机制零漂移，以本表站点为准）。
3. **C7-012 站点已被 plan 2496 部分改写**：审计引 `SessionLockRenewal.java:74-77`（catch RuntimeException→WARN）——live 回读为 `:74-85` catch **Throwable**（plan 2496 Phase 4 MA-07/C4-007 护栏扩展，注释在案）；缺陷机制（异常仅 WARN 无升级、协议窄化为仅 false→handleLeaseLost）**live 复核成立未修复**；`DbSessionTakeoverLock.tryRenew` SQLException→NopAiAgentException :300-318（审计引 :312-315，+5 内漂移）。裁定⑩设计输入以 live 形态为准，护栏零回退。
4. **C3-011 主站点形态重构**：审计引 `AgentToolDispatcher L690 orTimeout`——live `AgentToolDispatcher` 无 orTimeout 站点（WI4/plan 2285/2505 系列改造后为 slice 等待循环 + futures map + `f.cancel(true)` :205-210/:263-268 + cancelCall :328-336 + 工具超时 cap :413-427）；取消不传播机制结论不变（cancel(true) 不接进程级 kill）；kill 通道生产调用点 live 两处（DefaultAgentEngine :693-698 background stop + ReActAgentExecutor :1677 heartbeat abandon）——原审计「仅 heartbeat 一处」口径订正为两处，超时/取消路径零接入的缺陷面不变；CL-005（orTimeout 同点）吸收面按 live 形态改挂在等待循环站点。
5. **C7-022 站点分布收窄**：审计/cra-index「5 路径仅 2 实现 cancelSession」——live 回读 `MemberFanOutDispatcher.executeBoundMember` :313-331 **已有 orTimeout+cancelSession(forced=true)**（fan-out 路径已实现取消）；本批真实残余面 = DefaultMemberSpawner TimeoutException 分支（:191-198 spawnedSessionId 未取消，live 亲读核实）+ TeamTaskFlowOrchestrator overall deadline :634 不取消成员会话 + C3-025/C4-014 吸收面；站点分布勘误登记，缺陷族结论（僵尸成员+RECLAIM 双执行）不变；plan 252–259 系 nop-ai-agent 生命周期早期修复批先例，执行时核对是否已覆盖残余面（若已覆盖则按勘误登记收敛该子面，红⑤判据相应收窄）。
  - [R2 W2 指针] 红⑤/红⑥判据以勘误 12/13 + 设计文档 §5.1 为准（红⑤两层断言：图已断/会话已终态；红⑥增量面：终态五容器清理接线缺失，非容器逻辑——对既有 remove 实现设红即假红）。
6. **cra-index 行号基线**：R2-16 open 九行 = :240–248（C4-015 见 P2 台账 G51 行 :435；:239/:249/:250/:251/:252 已 fixed 行不属本批）；cra-index 若在执行前被其他批回填致行号漂移，以「批次列=R2-16 + finding ID」定位为准并登记。
7. **C10 映射口径**：`inventory-p2-c10.md` :789 小结行 3.1（19）为全量映射；本批仅承载 10 主 ID 对应成员面（C10-002/003/005/006/007/009/010/017 八条；C10-010 仅 MA-19 半边，MA-16 半边归 R4-21），其余 11 条归 R4-21/R4-32/G49/已 fixed/watch 各本体——半边口径以本节清单为准，Phase 4 收敛注记按此书写。
8. **MA-03 族站点计数**：cra-index 记「族 29 站点」且注记「remove 已实现纯接线」（live 三实现 remove 均在：InMemory :34-35 / DBSessionStore :198-203 / FileBackedSessionStore :177-182）——缺陷主体为引擎终态清理不接线 + gate3 清单不含 sessionStore，非 remove 方法缺失；设计裁定②以「接线 + 清单扩容 + 断言补全」为准核实与成文。
9. **环境项预登记（批前既有）**：`./mvnw test -pl nop-ai -am` 触达上游模块链（nop-kernel/nop-api-core/nop-core/nop-commons/nop-auth/nop-stream/nop-message/nop-orm 等）——上游环境抖动（nop-auth MFA flake、debezium fork timeout、**nop-ai-agent 负载时序类 flake**[plan 2513/2514/2515 先例裁定，本批为站点所在模块须重点预登记]、nop-core `TestResourceCacheInvalidationChain` 间歇红[2517/2518 执行期留痕先例]、nop-task-core 逐出测试负载间歇误红[2518]、surefire fork 启动崩溃类，沿 plan 2512–2518 已知面）按「stash 基线同红 / solo 绿 / 恢复复跑全绿」**三联裁定**处理并留痕，不得静默重跑或跳过。
10. **docker-gated / 外部依赖测试面登记**：`TestDockerSandboxBackend` 等沙箱测试 docker-gated 默认不跑——本批 C3-011 kill 通道钉须 mock tracker/fake 进程层非 docker 可跑；`TestDBMessageService*` 族依赖内嵌 DB（H2/derby 面，构建内可跑）——执行时如遇环境缺位按三联口径登记。
11. **[设计文档起草增补条款]** 裁定①②⑤⑦等涉及执行窗语义改动者，设计文档须给出与 `TestDefaultAgentEngineCancel`/`TestSessionLifecycleSaveBeforeRelease`/`TestEngineLifecycleAndCheckpointBounded` 既有钉的兼容性核对表；gate3 改写为唯一允许的既有钉改写义务，其余钉零回退。
12. **[设计评审 R2 修订——C7-022 事实前提再收窄]** 本表成员 5 行「overall deadline :634 `orTimeout` 只断 DAG 不取消成员会话」为不完整口径——live :646-667 TimeoutException 分支**已执行 `taskRt.cancel(ICancellable.CANCEL_REASON_TIMEOUT)`**（AR-3 注释在案，防图级 zombie）；缺陷残余面 = **会话级**（成员会话不随图取消终止）+ spawner TimeoutException 分支不取消 spawnedSessionId。裁定⑤增量 = 图取消之上补会话级 cancelSession（per-flow 成员登记表归 orchestrator built 阶段收集，数据面非取消机制），红⑤分「图已断」/「会话已终态」两层断言。
14. **[Phase 2 执行期偏差登记——裁定①②「completed」边界收窄（live 2026-10-31）]** 设计文档裁定①②终态摘除序原文挂「cancelled/failed/completed 收敛点」——实施核实 completed 为执行终态而非会话生命周期终态（多轮原地继续执行依赖 store 驻留与注入 staging 通道；TestDefaultAgentEngineMultiTurn 等 15+ 既有钉实证）。已落形态：新增 `AgentSessionLifecycle.isSessionLifecycleTerminal`（= isTerminalStatus − completed），store 逐出/terminal 标记+drain/journal purge 仅在 failed/cancelled/forced_stopped/escalated/truncated 触发；checkpoint 内存清理保持 HEAD 既有行为零变化；checkpoint 磁盘面新增 `ICheckpointManager.purgeTerminal` default 方法（模块内接口）。设计文档 §3.1/§3.2 口径修订随 Phase 3 owner doc 落账；F4 completed 段无界增长面移交 CL-IND-01 保留策略族 successor。
15. **[Phase 2 执行期登记——裁定⑩ Error 轮计数窗]** 设计裁定⑩「不可分类异常升级 abort」若按字面应用于非 SQL 异常（Error/RuntimeException）将回退 plan 2496 绝缘钉语义（TestSessionLockRenewalInsulation 断言 Error 轮不得触发 lease-lost abort，零回退钉清单语义）。已落形态：Error 轮入计数豁免窗（WARN + per-handle 计数），连续 N=3 超界必 abort（禁无限 WARN 兑现）；根因 SQLException/SQLRecoverableException 即 lease 未知立即 abort（C7-012 缺陷本面）。
13. **[设计评审 R2 修订——C5-008 既有面登记]** `FileBackedCheckpointManager.remove`（live :199-215）**已实现**终态五容器清理（bySession + 关联 byWatermark 遍历清除 + snapshotCache/saveCounters/loadedSessions）——本表成员 6 行「byWatermark remove 仅随 session 清理」即为该实现；裁定②对终态面为接线调用非清理逻辑新增，裁定⑥真实增量 = 仅「压缩完成会话淘汰」+ journal cap 轮转（活跃段超 cap 环形截断最旧 + WARN，归档不受 cap 约束）+ 分批解析与峰值观测器；红⑥对既有 remove 实现设红为假红——缺陷态红判据只挂增量面与接线缺失两面（设计文档裁定⑥红⑥增量断言修正条款）。

## Goals

- Phase 1 设计裁定文档一次裁决 10+1 项裁定（裁定①–⑪，含九个吸收族面与四组同变更集/对齐绑定）+ 测试缝（独立 draft review 通过），无临场发明空间：①注入登记窗口（C1-008）②F4 终态清理接线+gate3 反向钉死改写（C5-015，C10-003 义务）③spawn 池上提/close 接线（C4-010，CL-036 同步）④spawn 池 drain（C4-015，与③同变更集）⑤成员/spawn 超时取消契约（C7-022，消费 R1-04 bindCancel）⑥journal/spill 累积上界（C5-008）⑦工具超时取消传播（C3-011，kill 通道接入）⑧方言流式协议族（C3-028，C3-028×C3-031 两案一次消解）⑨DBMessageService 治理四轴（C3-021，R1-08 对齐）⑩接管锁 abort 语义（C7-012，plan 2496 护栏零回退）⑪测试缝与零回退钉清单——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。设计文档拟名 `ai-dev/design/concurrency/agent-orchestration-session-lifecycle-concurrency.md`（Phase 1 产出物，前向引用——落盘前 doc-link warning 为预期已登记；命名裁定：引擎注入/终态清理/会话存储（编排+会话生命周期域）+ spawn 池/超时取消/工具传播（执行域）+ 方言协议/DB 传输/接管锁（交付与 fencing 域）三域合一，与现有 20 份命名风格一致；如内容演进更适可调，调整时在本 plan 登记命名裁定）。
- 10 项成员全部修复并各带回归测试（rule 25）；C10 收口门（C10-002/003/005/006/007/009/010/017 八条成员面，C10-010 仅 MA-19 半边）随本体同 diff 落地（E2）；gate3 反向钉死改写义务随批履行并留痕（唯一允许的既有钉改写）。
- 关键工单先红后绿或变异/回退论证留痕（E2），红①–红⑩逐成员登记：红① 注入竞态红（N 线程 resume/wake×injectInput 压力断言无 CME/无注入丢失——缺陷态竞态红[压力窗]→注册+原子化绿）；红② 终态清理红（终态执行后 sessionStore/checkpoint 等容器 isEmpty 断言——缺陷态 put-only 红→接线+清单扩容绿；gate3 改写后新钉保持绿）；红③ 池泄漏红（K 次 team-execute-flow 后 spawn 池线程数守恒断言——缺陷态线性增长红→close 接线/池上提绿；惰性建池并发双建断言）；红④ drain 红（queued 任务在 stop 后 dispatch future 落定断言——缺陷态永不 settle 红→有界 drain+显式 fail 绿）；红⑤ 超时取消红（spawner 超时后 spawnedSessionId 会话终态断言——缺陷态僵尸执行红→cancelSession 绿；RECLAIM 后无双执行断言）；红⑥ 累积上界红（超 cap journal 轮转/淘汰断言 + 恢复峰值物化上界断言——缺陷态无界红→上界绿；spill 并发写断言）；红⑦ 取消传播红（工具超时后底层进程/tracker 收到 cancelExecution 断言——缺陷态幽灵执行红→kill 接入绿）；红⑧ 方言协议红（单事件多 tool_call 全枚举断言 + Ollama 空串帧 thinking/tool_call 可达断言 + usage 四方言非空断言 + error 事件类型化断言——缺陷态逐项红→修复绿；Anthropic/Responses 免疫钉保持绿）；红⑨ DBMessageService 红（call-agent 长回调期间其他 topic 投递不断链断言 + 毒消息 DLQ/停摆上界断言 + consumers 顶替拒绝断言——缺陷态冻结/停摆/顶替红→治理绿）；红⑩ 续期异常升级红（连续 SQLException 后本地执行 abort 断言——缺陷态无限 WARN 红→升级 abort 绿；瞬时抖动豁免窗断言）。
- 联调纪律显式守约（本批七条）：**MA-04×C4-015 同变更集**（池 close 接线+drain 一批）；**MA-05/MA-08 消费 R1-04 原语禁新造取消机制**；**MA-09 族单修复批（C3-028×C3-031 两案必居其一同批消解）**；**MA-11×R1-08 consumeAwaitTimeout 对齐（:247/:268 双侧注记闭合）**；**MA-19×WI 1.6 家族关联不并案 + plan 2496 护栏零回退**；**MA-02×gate3 反向钉死改写同批**；**MA-16 约束继承（R1-03 收窄后形态）**。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-ai.md`（新增「agent 编排与会话生命周期并发契约」节：注入登记/终态清理/spawn 池生命周期/成员超时取消/工具超时传播语义 + 方言流式协议契约句[多 chunk/usage/错误通道] + DB 传输治理契约句[投递分离/毒消息/claim fencing] + 接管锁 abort 语义 + journal 累积上界契约句、源码锚点补行）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- 下游就绪义务：批末向 R4-21（8.61，硬依赖本批）回写就绪注记（含本批池/取消/清理裁定指向，消费不重建）；MA-11↔R4-24 consumeAwaitTimeout 对齐注记双侧闭合。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-21 成员**（G52 前半 10 项：MA-10/12/13/14/15/16/18/20/21+C1-010）——特别地 MA-16（调度器体不得指向 globalTimer）只继承 R1-03 收窄后约束不代做；MA-10 会话存储并发装载面只登记裁定②终态清理接缝。
- **不做 C7-004/C7-013 拆出面**（R4-32）；不做 G49 deferred 束与 watch-only（C1-027/C3-008）；不做 C7-018（P2 租约墙钟，批外接缝登记）；不做 CL-IND-01 本体（DB 保留策略独立线索工单，裁定②只登记对齐注记）。
- 不做平台层改码：`nop-api-core`（io.nop.api.core.*）零触碰（R1-04 已收口面；条件申报①仅条件触发）；nop-commons/nop-core 平台 executor 设施本体零触碰（只消费）；nop-ai-shell 沙箱面（R1-06 已收口）零回改；4.1 REGISTRY 契约域零触碰（对齐注记关系）。
- 不做 WI 3.1 已 fixed 面（C3-001/C7-001/C7-002/C4-011/MA-17）回改；不做 MA-07 移交面（WI 2.1 已收口，plan 2496 护栏消费零回退）；不做 CL 线索独立立案展开面（随主单吸收面已逐条列入成员表）。
- 不触碰 `_` 前缀生成物；R4-21/R4-32/deferred 批外成员本体；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-ai/nop-ai-agent/src/`：`engine/`（DefaultAgentEngine/AgentSessionLifecycle/AgentToolDispatcher/LlmCallCoordinator/SingleTurnExecutor/ReActAgentExecutor/SessionLockRenewal）、`session/`（三 store 实现 + AgentSession）、`team/`（DefaultMemberSpawner）与 `team/flow/`（TeamTaskFlowOrchestrator/MemberFanOutDispatcher/SpawnMemberAgentTaskStep）与 `team/scheduler/`（TaskDispatchCoordinator）、`tool/`（TeamExecuteFlowExecutor/BashSandboxProgressTracker 消费面）、`reliability/`（FileBackedCheckpointManager/journal reader/writer）、`compact/`（InMemorySpillStore/归档两类）、`message/`（DBMessageService）、`runtime/lock/`（DbSessionTakeoverLock）、`gate/`（TestInvariantGate3EntryPointCleanup 改写）各自 main + test 树。
- `nop-ai/nop-ai-core/src/`：`io/nop/ai/core/dialect/` 五方言 + ToolCallAccumulator + 既有 dialect 测试族（main + test 树）。
- 设计文档：`ai-dev/design/concurrency/agent-orchestration-session-lifecycle-concurrency.md`（新建；裁定①–⑪+测试缝+零回退钉+R1-04/R1-08 对齐条款，最终状态写法；命名调整须登记裁定）。Owner doc：`docs-for-ai/03-modules/nop-ai.md`、`docs-for-ai/04-reference/source-anchors.md`（按实际）。

### Out Of Scope

- Non-Goals 所列全部项；R4-21/R4-32/dormant/G49/已 fixed 批次工单；nop-api-core/nop-commons/nop-core 平台设施本体（条件申报①除外）；`model/*.orm.xml` 与 `_` 前缀生成物（预设零触碰）；`ILlmDialect` 接口签名演进（条件申报②除外）。

## Execution Plan

### Phase 1 - agent 编排/会话生命周期并发设计裁定文档 + 独立评审

Status: done（2026-10-31，R2 评审 PASS）
Targets: `ai-dev/design/concurrency/agent-orchestration-session-lifecycle-concurrency.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下裁定+测试缝，每条唯一预设默认分支：
  - [x] **裁定① C1-008 注入登记窗口**：三恢复路径对称注册/注销形态（resume/wake/restore 执行窗注册[预设] vs injectInput 侧会话锁内查询）；注入链复合原子化一次定夺；pendingInjections 载体同步化一次定夺；C3-014/CL-061 吸收面闭环声明
  - [x] **裁定② C5-015 F4 终态清理 + gate3 改写**：终态清理清单扩容（F4 29 站点承载面逐容器登记）+ sessionStore 三实现接线；**gate3 反向钉死改写方案**（清单扩容 + 参数化 isEmpty 断言 + 改写语义对照留痕）；终态 save 反向 pin 子面消解；CL-IND-01 对齐注记；MA-10 接缝登记（R4-21 不代做）
  - [x] **裁定③ C4-010 spawn 池上提**：executor 生命周期落点（上提单例/注入载体[预设] vs per-invocation try/finally close）一次定夺；CL-036 惰性建池同步随同 diff；C3-022/C7-032 收敛面声明；**MA-16 约束继承声明**
  - [x] **裁定④ C4-015 drain**：有界 drain 形态（shutdown→awaitTermination 上界→残余显式 fail/settle[预设] vs 逐任务取消回调）；daemon 侧对称面；inFlightDispatches 不变式恢复声明
  - [x] **裁定⑤ C7-022 超时取消契约**：spawner TimeoutException→cancelSession 补齐（预设）+ overall deadline 汇聚取消；**R1-04 bindCancel 消费声明（禁新造机制）**；C7-023/C3-025/C4-014 吸收面；勘误 5 残余面与 plan 252–259 先例兼容核对条款
  - [x] **裁定⑥ C5-008 累积上界**：journal 轮转/上界参数化（尺寸/条数 cap 一次定夺）；byWatermark 会话级淘汰（与裁定②协同）；恢复流式化/分批形态一次定夺；spill/归档 CHM 化 + CL-077/CL-006/063 声明面；C5-010/011/C7-031 拆分面闭环
  - [x] **裁定⑦ C3-011 取消传播**：超时/取消路径接入 IToolProgressTracker.cancelExecution（预设，复用 heartbeat 通道）判据成文；HTTP/LLM 吸收面（C3-004/007/012/C4-005/C7-024）逐项实现或显式边界声明；cancelCall javadoc 契约句修订；**条件申报①触发检查条款**
  - [x] **裁定⑧ C3-028 方言协议族**：多 chunk 语义收敛形态（List 返回 vs 回调——一次定夺）；Ollama 阶梯修正（两案一次消解）；usage 四方言接入矩阵；带内错误通道类型化形态；Anthropic/Responses 免疫复核结论；**条件申报②触发检查条款**
  - [x] **裁定⑨ C3-021 DBMessageService 治理四轴**：投递/poll 分离与执行器有界化（拒绝策略显式，禁静默丢弃）；毒消息处置（重投上界+DLQ 形态，**consumeAwaitTimeout/attempt=0/guarded 回调对齐声明**）；consumers 注册治理；claim 世代 fencing（引用 2514 裁定⑥菜单不重建）；R1-08 对齐注记落点声明
  - [x] **裁定⑩ C7-012 续期异常升级**：异常分类判据（连续 N 次/不可分类异常[预设] vs 即时 abort）+ 升级 handleLeaseLost 同型 abort；瞬时豁免窗配置化；**plan 2496 护栏零回退声明**；C7-018 批外接缝登记
  - [x] **裁定⑪ 测试缝与零回退钉清单**：红①–红⑩ 测试形态逐条（含缺陷态红判据 + docker-gated 规避[mock tracker/fake 进程层]）；零回退钉 = `TestInvariantGate3EntryPointCleanup`（唯一允许改写=裁定②义务）、`TestDefaultAgentEngineCancel`/`TestDefaultAgentEngineConcurrencyGuard`/`TestEngineLifecycleAndCheckpointBounded`/`TestSessionLifecycleLockGate`/`TestSessionLifecycleSaveBeforeRelease`/`TestFanOutFutureLifecycle`、`TestInMemorySessionStore`（:56 remove）/`TestDBSessionStore`/`TestFileBackedSessionStore`、`TestMemberSpawner`/`TestTeamTaskFlowOrchestratorAsync`/`TestAsyncSpawnStep*` 族、`TestFileBackedCheckpointManager`/`TestDBCheckpointManager`/`TestCheckpointJournalSnapshotFormat`、`TestOpenAiDialect`/`TestOllamaDialect`（:191 参数保留钉）/`TestGeminiDialect`/`TestAnthropicDialect`/`TestResponsesDialect`/`TestLlmDialectErrorResponse`、`TestDBMessageService` 四族、`TestShellPipelinePoolIsolation_C4_004_3_2`/`TestShellPipelineDeadlockFamily_C4_003`/`TestToolManagerQueueChain_C1_001`（2501 钉零触碰）；补测类标注「新增断言即新钉、缺陷态红→修复后绿」
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑪全部项且每条有唯一预设默认分支，独立 draft review 记录在案（文件命名如调整已登记裁定）
- [x] 裁定②含 gate3 改写方案与改写语义对照；裁定③④含同变更集绑定；裁定⑤⑦含 R1-04 原语消费与条件申报①检查；裁定⑧含两案必居其一同批消解与条件申报②检查；裁定⑨含 R1-08 对齐声明；裁定⑩含 plan 2496 护栏零回退声明
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 10 成员实现 + 回归测试（按子域分组）

Status: planned
Targets: `nop-ai/nop-ai-{agent,core}/src/`（各自 main + test 树）

- Item Types: `Fix`（9 项 P1 + 1 项 P2 同批搭车均为已确认 live defect/裁定同批面，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [x] 组 A 引擎注入与终态清理（nop-ai-agent engine/session/gate）：C1-008（裁定①，含 C3-014 吸收面）+ C5-015（裁定②，F4 29 站点承载面 + **gate3 反向钉死改写履行**）+ C10-005/C10-003（gate3 三行加法扩容 + 参数化运行时终态钉绿；红①钉 TestDefaultAgentEngineInjectionWindow + 红② runtime 钉绿；completed 边界偏差勘误 14）
- [x] 组 B spawn 池生命周期与超时取消（nop-ai-agent tool/team/team.flow/team.scheduler）：C4-010（裁定③，**与裁定④同变更集**）+ C4-015（裁定④）+ C7-022（裁定⑤，消费 R1-04 原语，C7-023/C3-025/C4-014 吸收面）+ C10-007（main 面全落；红③④⑤ focused 钉缺口登记于 `_tmp/red-green-2519-summary.md`）
- [x] 组 C 累积上界（nop-ai-agent reliability/compact）：C5-008（裁定⑥，C5-010/011/C7-031 拆分面 + CL-006/063/026/070/077 吸收面）+ C10-017（双 cap 环形截断 + purgeTerminal 磁盘清除；红⑥ focused 钉缺口登记）
- [x] 组 D 取消传播（nop-ai-agent engine/tool）：C3-011（裁定⑦，C3-004/007/012/C4-005/C7-024/CL-005 吸收面）+ C10-006（main 面全落；红⑦ focused 钉缺口登记）
- [x] 组 E 方言协议族（nop-ai-core dialect）：C3-028（裁定⑧，C3-030/031/032 同变更集）+ C10-002（TestDialectProtocolFixtures_C3_028 十钉；m3 机械验收式兑现——红8 mutation 9/10 红 + Responses 免疫钉绿，`_tmp/red-green-2519-red8.log`/`green8.log`）
- [x] 组 F DB 传输治理（nop-ai-agent message）：C3-021（裁定⑨，C7-025/C1-019/CL-040/024/069 吸收面 + R1-08 对齐）+ C10-009（治理四轴全落 + 前置盘点 `_tmp/2519-inventory-subscribe-callsites.md` 零顶替依赖；红⑨六钉 + 5 mutation 红，red9/green9 留痕）
- [x] 组 G 接管锁（nop-ai-agent engine/runtime.lock）：C7-012（裁定⑩，plan 2496 护栏零回退——绝缘钉单独复跑绿 `red-green-2519-guard-check10.log`）+ C10-010[MA-19 半边]（红⑩四钉 + 缺陷态重建红 red10/green10 留痕；Error 轮计数窗偏差登记）
- [x] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：红①②⑧⑨⑩ 留痕完整（summary 总册）；**红③④⑤⑥⑦ focused 钉缺口已清零（2026-10-31 续作）**：TestTeamExecuteFlowExecutorPoolLifecycle[③，5 钉]/TestSpawnPoolDrain[④，2 钉]/TestMemberSpawnerTimeoutCancel+TestTeamFlowDeadlineConvergenceCancel[⑤，3+1 钉两层断言]/TestCheckpointJournalCap_C5_008[⑥，4 钉增量面防假红]/TestAgentToolDispatcherKillPropagation[⑦，3 钉 mock tracker 非 docker]——红证 `_tmp/red-green-2519-red3..7.log`（变异探针 `_tmp/red-green-2519-red3-7-mutations.py`，备份 `red3-7/`，restore cmp 字节核对），绿复跑 `_tmp/red-green-2519-green3-7.log` 18/18 EXIT=0（Goals 节逐条判据）；红绿快照落 `_tmp/` 并登记；**变异探针纪律**：触碰取消/停止/abort 路径的改动（裁定⑤⑦⑩）收口前必须运行含完整取消-恢复流程的端到端测试（恢复动作以断言方式确认目标可执行行真实存在）——红⑤⑦变异探针触碰取消路径，恢复后六类绿复跑留痕；含完整取消-恢复流程的端到端链（rule 22）仍挂下一条独立义务，不由此条替代
- [x] **端到端验证**（rule 22）：≥1 条会话生命周期全链场景（resume/wake 注入并发→执行→终态清理全容器 isEmpty→恢复会话不含已释放状态，覆盖裁定①②）；≥1 条 team-flow 全链场景（spawn→成员超时→cancelSession→池 close 有界 drain→dispatch future 全落定，覆盖裁定③④⑤）；≥1 条交付链场景（方言流式多 tool_call+usage+error 帧全枚举→DBMessageService 投递分离下毒消息 DLQ→续期异常升级 abort，覆盖裁定⑧⑨⑩）
- [x] **接线验证**（rule 23）：裁定②终态清理真实接线断言（终态执行后 sessionStore.remove 真实被调用[非仅方法存在]）；裁定③池上提后 TEE 构造路径真实复用共享 executor 断言（线程名/计数证据）；裁定⑤spawner 超时分支真实调用 cancelSession 断言；裁定⑦kill 通道真实被超时路径消费断言（零接入现状反转）；裁定⑨独立投递执行器真实承载回调断言（线程名证据）
- [x] **无静默跳过**（rule 24）：裁定④残余任务显式 fail/settle（禁静默丢弃）；裁定⑤⑦取消失败显式 WARN+计数（禁吞）；裁定⑨毒消息 DLQ 落账+拒绝策略显式（禁静默重投无上界）；裁定⑩豁免窗上界后显式 abort（禁无限 WARN——现状即缺陷面）；新增公共分支无空方法体/TODO

Exit Criteria:

- [ ] 10 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 收口门（8 条成员面）同 diff 落地；gate3 反向钉死改写履行留痕（改写语义对照在案）
- [ ] 必红证据（E2）：红①–红⑩均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [ ] 零回退实证：零回退钉清单全部保持绿（钉零回改逐条 git diff 为空核对；唯一允许的钉改写 = gate3 清单扩容+断言补全[语义对照留痕]，登记在案）
- [x] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [ ] `./mvnw test -pl nop-ai -am` BUILD SUCCESS；上游环境抖动按 Baseline 勘误 9 三联裁定处理并留痕；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: nop-ai 聚合全量测试；`docs-for-ai/03-modules/nop-ai.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2518 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-ai -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；环境抖动按三联裁定留痕（**nop-ai-agent 负载 flake 先例重点核对**）；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `_` 前缀生成物手改、不含 `model/*.orm.xml`、不含 nop-api-core/nop-commons/nop-core 平台设施（条件申报①除外）、不含 R4-21/R4-32/G49 批外成员本体、2505/2501/2496 既有钉面零 diff 核对登记
- [ ] Owner doc 更新：`nop-ai.md`——新增「agent 编排与会话生命周期并发契约」节（注入登记/终态清理清单语义/spawn 池生命周期与 drain/成员超时取消契约/工具超时传播与 kill 通道判据/接管锁 abort 语义/journal 累积上界）+ 方言流式协议契约句 + DB 传输治理契约句 + §源码锚点补行；`source-anchors.md` 新增锚点（按实际）；check-doc-links --strict 退出码 0
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0（批前既有发现逐条裁定登记[2518 先例口径]）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 批行验证命令 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] nop-ai.md owner 面 + source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（生成物/orm 模型/平台设施/R4-21/R4-32/G49 批外成员本体/2505/2501/2496 钉面）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.25 行、cra-index P1 台账 + P2 G51 行、R4-21 就绪注记、MA-11↔R4-24 对齐注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（注入注册/终态清理接线/池 close/drain/超时取消/累积上界/kill 接入/方言协议/DB 治理/abort 升级运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：R2-16 九行状态收敛（open→fixed，注记 plan 2519；吸收成员随各主行括注）+ P2 G51 行 C4-015 成分收敛注记；C10 成员面收敛注记（8 条按勘误 7 口径 + gate3 反向钉死改写注记）；roadmap 8.25 行 Status → done；R4-21（8.61）硬依赖就绪注记（含裁定②③⑤指向，消费不重建）；MA-11↔R4-24 consumeAwaitTimeout 对齐注记双侧闭合（cra-index :247/:268）（含 :243/:245 行文本按勘误 5/6 口径同步改写[R1 m2]）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 九行收敛 + P2 G51 搭车注记 + 吸收成员括注 + C10/gate3 注记、roadmap 8.25 = done、R4-21 就绪注记、MA-11↔R4-24 双侧注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 10 项成员全部修复：C1-008（注入登记窗口）、C5-015（F4 终态清理 29 站点+gate3 改写）、C4-010（spawn 池 close 接线）、C4-015（spawn 池 drain）、C7-022（成员/spawn 超时取消契约）、C5-008（journal/spill 累积上界）、C3-011（超时取消传播/kill 接入）、C3-028（方言流式协议族）、C3-021（DBMessageService 治理四轴）、C7-012（接管锁 abort 语义）
- [ ] batch-proposal R2-16 行 exit criteria 增补逐条达成并留证：MA-03 F4 29 站点（gate3 反向钉死改写同批）；MA-04 spawn 池 close 接线（C4-015 同批）；MA-05/MA-08 取消契约补齐（R1-04 bindCancel 消费）；MA-09 方言协议族单修复批；MA-11 consumeAwaitTimeout 对齐；MA-19 接管锁 abort 语义
- [ ] 对齐纪律守约：MA-04×C4-015 同变更集兑现；R1-04 原语消费禁新造机制兑现；MA-09 两案必居其一同批消解；MA-11↔R1-08/R4-24 双侧注记闭合；MA-19×WI 1.6 不并案 + plan 2496 护栏零回退；MA-16 约束继承；PA 条款闭环（零 orm/零生成物声明兑现或条件申报面落地）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红⑩均有先红后绿或变异/回退论证（E2）；C10 收口门（8 条成员面）同 diff 落地；gate3 反向钉死改写义务履行留痕
- [ ] 设计文档裁定①–⑪成文且经独立 draft review；既有钉（零回退钉清单全列）零回退实证
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-ai -am` BUILD SUCCESS（E1/E3；上游环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：nop-ai.md（agent 编排/会话 owner 面）+ source-anchors.md 按实际（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）注入注册/清理接线/drain/取消传播/kill 接入/协议枚举/DB 治理/abort 升级运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-ai 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index R2-16 九行收敛 + roadmap 8.25 → done + R4-21 就绪注记 + MA-11↔R4-24 双侧注记（E6）

## Deferred But Adjudicated

### R4-21/R4-32 成员（G52 前半 10 项 + C7-004/C7-013 拆出面，含 C10-010 MA-16 半边与 C10 载体面）

- Classification: `out-of-scope improvement`（batch-proposal §二 R4-21/R4-32 显式安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-21（roadmap 8.61，硬依赖 8.25）/R4-32
- Why Not Blocking Closure: 判级对齐零重复——cra-index G52 行批次列=R4-21；本批已收敛 3.1 单元全部 open P1（9 张）+ P2 同批搭车 1 张（C4-015），下游实施以本批后的 live 形态为基线，且可引用本批裁定②③⑤接缝（终态清理清单/spawn 池生命周期/超时取消契约）为既有裁定不重开体。

### C7-018（P2，MA-19 族内租约期限墙钟成员）与 G49 deferred 束、watch-only（C1-027/C3-008）

- Classification: `watch-only residual`（批前裁定：C7-018 条件面 P2 未入 R2-16 finding 清单；G49 零调用方/休眠面）；Successor Required: `yes`（C7-018 归 P2 束；触发条款见台账）；Successor Path: R4-21 及触发条款所在批
- Why Not Blocking Closure: 批前裁定批外面；本批裁定⑩已登记 expiresAt 语义接缝，C7-018 实施时消费不重建。

## Non-Blocking Follow-ups

- **R4-21 对接注记**：本批落地后向 roadmap 8.61 行作就绪注记（硬依赖解除落点；含裁定②③⑤接缝引用指向、C7-018 接缝转达）。
- **CL-IND-01 长线治理**：DB 自建表保留策略族（ai_agent_message CONSUMED 行/usage/team 行 append-only 无 TTL）——独立线索工单承载，本批裁定②只落对齐注记。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （收口时填写）
Completed: —

Closure Audit Evidence:

- Reviewer / Agent: （待独立 closure audit）
- Evidence: （待填）

Follow-up:

- （待填；或明确写 no remaining plan-owned work）

## Risks And Rollback

- **裁定②终态清理的行为面风险**：终态释放接线使「终态会话仍可查询/恢复」的既有行为变化——设计文档须给出终态语义矩阵（内存即时释放 vs 持久层保留 vs restore 路径补偿加载）并与 `TestSessionLifecycleSaveBeforeRelease` 钉核对；回滚 = 清单扩容行 revert 单点可逆（gate3 改写随行回退）。
- **裁定⑤⑦取消传播的误伤风险**：超时→cancelSession/kill 若误伤慢而未挂死的执行，会放大失败率——设计文档须给判据（超时即取消为契约语义 + forced 标志使用边界）与豁免/观测面；变异探针纪律适用。
- **裁定⑧方言协议改型的兼容风险**：parseStreamChunk 语义收敛可能影响既有消费方（AgentToolDispatcher/LlmCallCoordinator 聚合逻辑与 2505 聚合双上界交互）——设计文档须给五方言×三产出类型矩阵与聚合端适配方案；条件申报②触发时先申报后实施；回滚 = 方言行 revert，免疫方言（Anthropic/Responses）零 diff。
- **裁定⑨投递分离的时序语义风险**：内联改异步使既有 TestDBMessageService 断言时序化——同 diff await 化改写（如有）登记语义对照（参照 2518 MA-02 await 化先例口径）；R1-08 契约对齐不得单侧偏离（双侧注记闭合前不标完成）。
- **裁定⑩abort 升级的可用性风险**：DB 抖动被误判为 lease 丢失导致健康执行自 abort——瞬时豁免窗（计数+上界）为默认形态，判据与参数配置化；回滚 = 豁免窗放大需 plan 修订不得静默。
- **上游链环境噪声风险**：`./mvnw test -pl nop-ai -am` 触达上游多模块，nop-ai-agent 负载类 flake 有先例（2513/2514/2515）——三联裁定执行并留痕；本批新增压力钉自带负载面时须设压力窗上界防噪声化。
