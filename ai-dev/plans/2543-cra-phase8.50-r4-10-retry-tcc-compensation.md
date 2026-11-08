# 2543 CRA Phase 8.50 — R4-10 1.6 补偿/护栏束 G17（14 工单全 Fix：4 项 2515 部分缓解残留 + 10 项完整存活；C7-011 拆出至 R4-32）

> Plan Status: completed
> Last Reviewed: 2026-11-08
> Draft Review R1: **PASS-with-revisions**（独立子 agent fresh session，只读 @ `7d97f12b3e`，57 次工具调用——**2 Blocker + 8 Major + 5 Minor**，全部随轮落盘：B-1 W-01 误标「2515 已闭合」被 `2515:80` 原文推翻→改 14 项全 Fix；B-2 C10-002/004/009 验收前置被误判为「无」→ 三宿主同 diff 入批；M 组：W-02 computeIfAbsent 需 api-core（改模块内载体）/W-03 缺孤儿分支恢复腿/W-07 缺 suppressed+恢复方向/W-10 接线验证不可满足（零生产调用方）/W-12 时长腿缺时钟 seam/W-13 无界重载=api 面决策/W-09 装载期需 BizModel（改运行期 depth）/零重开须含裁定②③⑥+5 项部分缓解钉须对基线先红。放行 Phase 1）
> Design Draft Review R1（Phase 1 设计文档）: **PASS-with-revisions**（独立子 agent fresh session，只读 @ `3005ae8afa`，119 次工具调用——**2 Blocker + 9 Major + 6 Minor**，全部随轮落盘：B-1 W-06「未到终态一律 FAILED」会误判 KILLED/CONFIRM_SUCCESS → 父事务永不进 finished、反向摧毁 2515 裁定⑧ 清理链 → 改**逐状态结算表**（§3.1.6）；B-2 红⑥「折叠 CONFIRM_SUCCESS」在 2515 基线**不可复现**（裁定③ err==null 门控已阻断）→ 红⑥ 缺陷态红改为「后续分支零派发」（§4.1）；M1 W-05 只包 lambda 捕不到 `begin*Async()` 同步抛 → 外层 try + lambda try + `exceptionally` 三通道；M2 W-10 仅连续超时熔断不构成耗时有界 → 增单轮总时长预算；M3 W-03 腿2 缺方向筛/隔离且「变体B不可达」过强 → 复用 cancelAll/confirmAll + 残余窗诚实声明；M4 W-13 自调用事务传播 → 私有批助手 + 单事务显式裁定；M5 W-02 `synchronized(context)` 对 wrapper 上下文不互斥 → 锁 `getAttrs()` + 加包级 `instance(IContext)` 测试 seam；M6 W-04 `endDriving` 与「纪元只增」矛盾、缺条目语义未定义 → 显式「条目缺失=inactive」+ 不变式；M7 W-08 写侧无 seam 且 `nextCheckTime` 混源 → `nowForRead`/`nowForWrite` 双 seam；M8 W-01 非 UK 异常重读判据不可靠 → 非 UK 分支退为纯失败传播（不死信）；M9 plan Non-Goals 与 W-01= Fix 自相矛盾 → 本行下方 Non-Goals 已改。Minor m1 锚点 ~15 处漂移 + `fetchPendingRecords` 锚点错（改方法级锚点）/m2 depth 按 `(_callback)+$` 计/m3 批数口径 = 非空批/m5 C10-004 口径降为「保护力就位、生产接线缺位」/m6 错误码落 `TccCoreErrors`）
> Source: roadmap 工作项 8.50（`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` live :221，Status=**todo**；Owner Doc 列=`docs-for-ai/03-modules/nop-retry.md`；`docs-for-ai/03-modules/nop-tcc.md`；Deps=「batch-proposal.md §二 R4-10（14 项）；硬依赖 8.21——**8.21 已 done（plan 2515），硬依赖解除**，裁定①–⑩可引用为既有裁定（R2-12）；硬依赖 8.8（R1-07）」；Skill=none）；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 :125 R4-10 行（14 finding ID / 模块 nop-retry+nop-tcc / 验证命令 `./mvnw test -pl nop-retry -am` / owner doc 两份 / **PA=无**）+ §二.5 :172 G17 组行「15 | R4-10（14）+ R4-32（C7-011）」；成员发现详情 = `ai-dev/audits/2026-10/2026-10-15-1400-deep-audit-nop-retry-nop-tcc-concurrency/`（C1-shared-mutable-state.md C1-002 :121/C1-004 :211；C1-round2.md C1-007 :13；C3-async-chains.md C3-008 :225/C3-009 :248/C3-010 :274；C6-round2.md C6-015 :57；C7-long-running.md C7-007 :225/C7-008 :252/C7-010 :299；C7-round2.md C7-014 :158/C7-015 :190；C8-backpressure.md C8-003 :83；C8-round2.md C8-013 :74）+ 判级权威 = 同目录 merge-adjudication.md §3.1（**14 项全 P2**）+ inventory-p2-c10.md §二 WI 1.6 节（C10 映射：C1-002→C10-005 / C1-004→C10-009 / C8-003→CL-005 同位点主处置 / C3-008→条件面捆绑 CL-004）+ cra-index live :402 **P2-G17 束行**（15 成员=14+C7-011；状态「修→R4+」=open）
> Related: **plan 2515（completed @ 2026-10-31，closure audit APPROVED[agent_526666b9]）——同单元前批+零重开边界权威**：①**裁定① CAS 传播与补偿门控**（C3-008 接缝：2515 只封双驱动源 `future.cancel(true)` 已落，「弃链追踪本体」显式归 R4-10）；②**裁定④ A2 执行令牌语义**（C7-015 租约跨窗可引用）；③**裁定⑤ OVERWRITE 原子替换+代际号**（**C1-002 机制已由 2515 `replaceRecord`+deadletter 闭合**——本批 W-01 仅为核验登记，非重开）；④**裁定⑦ 终态化**（C7-003 僵尸终态化已落，C7-014 仅剩 per-record 批次隔离）；⑤**裁定⑧**（清理有界批删形态已提供 `removeCompletedRecords(...,batchSize)`，C8-003 剩余面=unbounded `cleanCompletedTransactions` 仍可达）。2515 Deferred 节明文「R4-10 成员（G17 十四项）…Successor Path: R4-10 批 plan（roadmap 8.50）」为本批承接依据。**零重开面为 2515 裁定①–⑨ 全部九条**（draft review R1-C3 勘误：初稿仅列①④⑤⑦⑧；②CAS 权威聚合/③终态写/⑥future 拓扑与簿记隔离三条直接压在 W-04/W-07/W-11 回归面上）。**既有裁定**（2515 裁定①④⑤⑦⑧ / plan 2502 R1-07 / merge-adjudication §3.1）仅引用不重建。
> Protected Area 授权：批次 PA=「无」（batch-proposal R4-10 行授权列）——普通修复 plan（非 plan-first/ask-first）。站点面 = `nop-retry-engine`（RetryEngineImpl/RetryScannerImpl/RetryRecordStoreImpl）+`nop-retry-dao`（NopRetryPolicy）+`nop-tcc-core`（TccEngine/TccTransaction/TccRunner/TccTransactionRegistry/TccBranchTransaction）+`nop-tcc-dao`（TccRecordStore）+ 测试树 + 文档面。**零 orm 模型改动预设**（C7-007 时钟改用既有 `getDbEstimatedClock` 投影；C8-013 参数校验在 Java setter）；**零 `_` 生成物**；**零 xdef**；**零平台设施改动**（`DbEstimatedClock` 为既有投影只读引用）。

## Purpose

关闭 R4-10 批的 14 项 P2 工单。**关键前置事实（本批起草期 live 核验 + draft review R1 勘误，非照抄审计基线）**：14 项**全部未闭合**——**4 项（C1-002 / C7-014 / C3-008 / C8-003）已被 2515 部分缓解但本体按 2515 明文归本批**（`2515:80`「C1-002 本体归 R4-10」/「C3-008 弃链追踪面归 R4-10」；C7-014 仅剩 per-record 隔离；C8-003 有界形态已有但无界重载仍公开可达），**10 项完整存活**。本批**全部按 Fix 处置**（draft review R1-Blocker-1 勘误：初稿把 C1-002 误标「已闭合」并降为 Proof——被 2515 Non-Goals 原文推翻）：

| # | 工单 | finding ID | live 状态（起草期亲证） | 处置 |
|---|---|---|---|---|
| 1 | W-01 | 1.6-C1-002 | **本体未闭合**（2515:80 明文「只落原子替换包络并登记暴露面收窄接缝，C1-002 本体归 R4-10」）。live 残留：双并发 OVERWRITE 双双通过 findRecordByIdempotentId → 输家 `replaceRecord` 删 0 行 → `saveEntityDirectly` 撞 UK → catch(:338-347) `moveToDeadLetter(stale)`+`failedStage(ERR_RETRY_RECORD_OVERWRITTEN)`——**未按 blockStrategy 分流**（契约要求「输家按 blockStrategy 处理，不以裸约束冲突暴露」），且对已被合法替换掉的 stale 记录做死信快照 | **Fix**（钉=红①：双 OVERWRITE 竞速；断言输家按 blockStrategy 处理且不对 stale 行造死信） |
| 2 | W-02 | 1.6-C1-004 | 存活：`TccTransactionRegistry.instance()` get→new→set 无原子性（:18-26）；TccEngine 四组 put/restore 叠放 | Fix |
| 3 | W-03 | 1.6-C1-007 | 存活：分支创建守卫读内存副本（`TccEngine:446` `txn.getTccStatus()`）；终态父事务下非终态分支无恢复入口 | Fix |
| 4 | W-04 | 1.6-C3-008 | **部分缓解**：`future.cancel(true)` 已在位（:484-489）；残留=弃链无 driving-epoch/abandoned 标记 | Fix（残留面） |
| 5 | W-05 | 1.6-C3-009 | 存活：`TccBranchTransaction:158` `response.getBizFatal()` 在 response==null 时 NPE；`TccRunner.runBranchCancelAsync` 回调体同步异常使手工 future 永不完成 | Fix |
| 6 | W-06 | 1.6-C3-010 | **部分缓解**：`confirmAllAsync` 已跳过 null-status 并 WARN（:139-145）；残留=非空越界状态仍在 `beginConfirmAsync` 同步抛，排后分支不调度 | Fix（残留面） |
| 7 | W-07 | 1.6-C6-015 | 存活：`TccEngine:399-400` CANCELLING 落库失败→`thenCompose` 短路；`TccTransaction:115` | Fix |
| 8 | W-08 | 1.6-C7-007 | 存活：`TccRecordStore.getCurrentTime` 返回 `CoreMetrics.currentTimeMillis()`（:387-389），全链原始墙钟 | Fix |
| 9 | W-09 | 1.6-C7-008 | 存活：`RetryEngineImpl:818` `idempotentId + "_callback"` 逐环追加无深度/环护栏 | Fix |
| 10 | W-10 | 1.6-C7-010 | 存活：`TccEngine:467` pageSize=100 × 逐条 `get(expireGap)`（:483）串行；`fetchExpiredRecords` 无 try/catch | Fix |
| 11 | W-11 | 1.6-C7-014 | **部分缓解**：`executeRetryFromScanner` 已包 try/catch→`failedStage`（:376-390）、`executeWithRetry` catch(Throwable)（:424-428）；残留=scanner 流式 `records.stream().map(...)` 无 per-record 隔离（:912-920） | Fix（残留面） |
| 12 | W-12 | 1.6-C7-015 | 存活：`RetryScannerImpl:186-217` do-while 排水整积压，无单轮批次数/时长上界 | Fix |
| 13 | W-13 | 1.6-C8-003 | **部分缓解**：有界三参 `removeCompletedRecords(...,batchSize)` 已存在且生产清理路径已用（`TccEngine:212`）；残留=无界两参重载仍可达（`TccEngine:509` `cleanCompletedTransactions`） | Fix（残留面） |
| 14 | W-14 | 1.6-C8-013 | 存活：`RetryScannerImpl.setScanIntervalMs/setBatchSize` 零校验；`RetryRecordStoreImpl:235` limit 透传 | Fix |

批末 roadmap 8.50 → done 回写 + cra-index P2-G17 束行收敛注（14 成员 fixed[C1-002 为 2515 已修核验] + C10-005/009 兑现括注 + C8-003=CL-005 同位点主处置关闭注记）+ owner doc 落账（E5）。

## Current Baseline

- **判级与立案**：14 项全为 merge-adjudication §3.1 **P2**；仅 C1-002 入单元内族 `1.6-OVERWRITE`，其余 13 为「无族独立行」；无 §2.1 族成员、无 P3。
- **C10 / CL 映射与验收前置（draft review R1-Blocker-2 勘误：初稿「本批无 C10 验收前置」错误）**：`batch-proposal:125` + `cra-index:402` + `inventory-p2-c10.md:775` 三方一致要求「C10-002~009、010 中映射本组成员之面随本体（**验收前置，同 diff**）」；`2515:24` 明列 C10-002/C10-004/C10-009 不在 2515、留给 R4-10。**三条 C10 验收前置入批**：
  - **C10-002 → 宿主 W-11（C7-014）+ W-12（C7-015）同 diff**：扫描/批处理保护力（per-record 隔离真异常注入 + 单轮上界真截断）。
  - **C10-004 → 宿主 W-04（C3-008）+ W-10（C7-010）同 diff**：弃链/超时恢复保护力（超时取消后不双驱动 + 页内串行耗时有界）。
  - **C10-009 → 宿主 W-02（C1-004）+ W-05（C3-009）同 diff**：注册表身份/回调终结保护力（并发 instance() 单例 + null response 不发散）。
  - C1-002→C10-005（2515 已兑现，覆盖面无缺口——本批仅消费不重造，见 C4 勘误）；C8-003→CL-005 同位点主处置（关闭注记在 Phase 4）。
- **依赖**：硬依赖 8.21（plan 2515）**已 done**；硬依赖 8.8（plan 2502 R1-07）**已 done**。两项解除。
- **live 站点文件与行数（HEAD `7d97f12b3e` 亲证）**：`RetryEngineImpl.java` 1023 行 / `RetryRecordStoreImpl.java` 525 行 / `RetryScannerImpl.java` 243 行 / `NopRetryPolicy.java` 67 行 / `TccRecordStore.java` 491 行 / `TccEngine.java` 510 行 / `TccTransaction.java` 258 行 / `TccRunner.java` 218 行 / `TccTransactionRegistry.java` 31 行 / `TccBranchTransaction.java` 168 行 / `TccStatus.java` 123 行。
- **基线测试签名（起草期亲跑）**：`./mvnw test -pl nop-retry` = **91 tests 0F 0E 0S**（api 20 / engine 66 / service 4 / web 1）；`./mvnw test -pl nop-tcc` = **105 tests 0F 0E 0S**（core 64 / dao 38 / service 2 / web 1）。
- **既有钉族（零回退锚）**：`TestTccCasPropagation_C10_003`(3) / `TestTccLedgerCleanup_C10_007`(4) / `TestTccLedgerHygiene_C7_005`(3) / `TestTccStoreCasConflict_C10_003`(2) / `TestTccTransactionEndStates`(6) / `TestRetryScannerInsulation` / `TestRetryLedgerRetention_C7_001`。
- **owner doc**：`nop-retry.md` 144 行（14 个 `##` 节，有「并发与租约契约（plan 2515 R2-12）」「扫描链」「账本保留策略」，**无参数边界节**）；`nop-tcc.md` 96 行（9 个 `##` 节，有「并发与账本卫生契约（plan 2515 R2-12）」，**无时钟/期限节**）。

## Goals

- 14 工单全部收口：**全部 14 项为 Fix**（含 4 项 2515 部分缓解的残留面），各带 ≥1 条验收载体；**5 项部分缓解项（W-01/W-04/W-06/W-11/W-13）的钉必须对 2515-缓解后基线先红**（否则仅钉住 2515 已绿行为，构成伪绿）。
- 设计裁定（分组 A–D）一次裁决，无临场发明空间；既有裁定（2515 ①④⑤⑦⑧ / 2502）引用不重建。
- 关键工单先红后绿留痕（确定性优先；概率腿未命中诚实登记）。
- E5 owner doc 落账：`nop-retry.md`（参数边界节 + 回调环护栏 + 扫描上界 + C7-014 隔离）、`nop-tcc.md`（时钟/期限节 + 补偿链韧性 + 收尾方向语义）+ `source-anchors.md` 按实际。
- 下游就绪：roadmap 8.50 → done + cra-index P2-G17 收敛注。

## Non-Goals

- **不重开 plan 2515 已修面**（13 成员 + **裁定①–⑨ 全部九条**产物 + 既有钉族）；**C1-002（W-01）经 draft review R1-B1 与设计 R1 勘误确认为「部分缓解 + 本体归本批」，按 Fix 改码**（撤销初稿「仅核验不改码」表述）——改动限于 `handleBlockStrategy` 的 catch 分支分流，不回改 2515 裁定⑤ 的原子包络、代际号与 `replaceRecord` 本体。
- **不做 C7-011 本体**（G17 第 15 成员——R4-32 墙钟批次单列；本批 W-08 的 tcc 侧时钟改用为独立面，不与 C7-011 的跨单元单调时钟注入合并）。
- **不做 R4-32 全部墙钟注入**（本批 W-08 仅 tcc 期限/租约改 `getDbEstimatedClock`，不引入 IClock 平台注入）。
- **不做 orm 模型改动 / 新列 / 新索引**（C8-003 分页依赖既有 beginTime 列）。
- **不做 nop-job / nop-kernel / nop-commons 平台本体改动**。
- **不做 `nop-tcc-api` / `nop-retry-api` 公共签名变更**（draft review R1-C2：W-03 分支恢复查询与 W-13 清理重载均可能诱使改 api；AGENTS.md「跨模块公共 API → plan-first」）。**如实施中发现某项必须改 api 签名 → 停止并在本批内以非 api 方案收敛（store 实现内新增方法/模块内查询），不得静默扩权**。
- **不重开历史审计报告原文**（收敛注记落本 plan Closure + roadmap + cra-index）。

## Scope

### In Scope

- `nop-retry/nop-retry-engine`：`RetryEngineImpl`（W-01 核验/W-09/W-11）、`RetryScannerImpl`（W-12/W-14）、`RetryRecordStoreImpl`（W-14）。
- `nop-retry/nop-retry-dao`：`NopRetryPolicy`（W-09 环检测只读引用）。
- `nop-tcc/nop-tcc-core`：`TccEngine`（W-03/W-04/W-07/W-10/W-13）、`TccTransaction`（W-07）、`TccRunner`（W-05/W-06）、`TccTransactionRegistry`（W-02）、`TccBranchTransaction`（W-05/W-06）。
- `nop-tcc/nop-tcc-dao`：`TccRecordStore`（W-03/W-08/W-13）。
- 测试树（两聚合器）+ owner doc 两份 + `source-anchors.md`。
- 设计文档：`ai-dev/design/concurrency/retry-tcc-compensation-ring-guard-and-scan-bound-concurrency-contract.md`（新建）。

### Out Of Scope

Non-Goals 所列全部；C7-011 与 R4-32 其余成员；2515 十三成员修复面；G17 之外各批；`_` 生成物/orm/xdef/平台设施本体。

## Execution Plan

### Phase 1 - 设计裁定文档（分组 A–D）+ 独立评审

Status: completed
Targets: `ai-dev/design/concurrency/retry-tcc-compensation-ring-guard-and-scan-bound-concurrency-contract.md`（新建）

- Item Types: `Decision`、`Proof`

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法），含：
  - [x] **既有裁定引用核对节**：2515 **裁定①–⑨ 全部九条**逐条核对落款（①C3-008 接缝/②CAS 权威聚合/③终态写/④A2 令牌/⑤OVERWRITE 原子替换+代际/**C1-002 本体归本批明文核对（2515:80）**/⑥future 拓扑与簿记隔离/⑦C7-003 终态化/⑧清理有界批删形态[新增与 W-06 结算表的对齐条款]/⑨）+ 2502 R1-07 + 零重开清单
  - [x] **裁定 A 补偿链韧性域**（W-01/W-02/W-03/W-04/W-05/W-06/W-07）：W-01 四分支判据（UK×重读身份 + 非 UK 纯失败传播）/ W-02 `synchronized(getAttrs())` + 包级 `instance(IContext)` seam + 条件恢复 / W-03 双腿（DB 复核 fail-close 三拒绝面 + 孤儿收敛复用批量助手 + 残余窗诚实声明）/ W-04 纪元双 API + 条目缺失=inactive 不变式 + 写点闸残余 / W-05 三通道兜底 + null 判空 / W-06 **逐状态结算表**（KILLED/CONFIRM_SUCCESS 为已结算）+ 逐分支隔离 / W-07 三件套（有限重试 + suppressed + 超时恢复默认 cancel）
  - [x] **裁定 B 时钟与期限域**（W-08）：`nowForRead`(min)/`nowForWrite`(max) 双 seam、9 个调用点全覆盖、`nextCheckTime` 归写侧
  - [x] **裁定 C 扫描与批量上界域**（W-10/W-11/W-12/W-13/W-14）：单轮总预算 + 连续超时熔断双上界 + 双计数 + protected seam / 显式 for 循环 per-record 隔离 / 批数与时长双上界（非空批口径 + 可覆写 `currentTimeMillis`）/ W-13 私有批助手 + 单事务显式裁定 / 参数 fail-fast 三面 + 存量升级注记
  - [x] **裁定 D 回调环护栏域**（W-09）：`(_callback)+$` 尾部计数 + max-depth 缺省 3 + 拒孵三动作（ERROR/计数/return）
  - [x] **裁定 E 附：红钉总表（红①–红⑭，一一对应）+ 零回退钉全列 + C10/CL 注记表**——每条钉指定可构造 seam；红①允「确定性分支腿为主 + 并发腿为辅」；**红⑥ 缺陷态红已按设计 R1-B2 改为「后续分支零派发」**（聚合严格化断言另记为直接调 `TccRunner.aggregate*` 的单元钉）
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析）通过后 Phase 2 方可启动 —— R1 PASS-with-revisions，2B+9M+6m 全落盘，设计文档已按全部条目修订，**0 Blocker**
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 归 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在、最终状态写法、含裁定 A–D+E 附且每项唯一预设分支 + 既有裁定引用核对节 + C1-002 已修核验在案
- [x] 独立 draft review 记录在案（0 Blocker）
- [x] `ai-dev/logs/` 已更新

### Phase 2 - 14 工单实现 + 回归测试（按组 A–D）

Status: completed
Targets: nop-retry-engine/dao + nop-tcc-core/dao + 测试树

- Item Types: `Fix`（W-01..W-14 全部十四项）、`Proof`（全部钉）

- [x] 组 A 补偿链韧性域（W-02/W-03/W-04/W-05/W-06/W-07）〔裁定 A；钉=红②③④⑤⑥⑦〕
- [x] 组 B 时钟与期限域（W-08）〔裁定 B；钉=红⑧〕
- [x] 组 C 扫描与批量上界域（W-10/W-11/W-12/W-13/W-14）〔裁定 C；钉=红⑩⑪⑫⑬⑭〕
- [x] 组 D 回调环护栏域（W-09）〔裁定 D；钉=红⑨〕
- [x] 组 A' 合流：W-01（C1-002 OVERWRITE 输家 blockStrategy 分流）并入组 A 补偿链韧性域〔裁定 A；钉=红①〕
- [x] 回归测试（rule 25 + 红①–红⑭ 先红后绿/变异论证，落 `_tmp/red-green-ledger-2543.md`）；零回退钉核对（`TestTccCasPropagation_C10_003`/`TestTccLedgerCleanup_C10_007`/`TestTccLedgerHygiene_C7_005`/`TestTccStoreCasConflict_C10_003`/`TestTccTransactionEndStates`/`TestRetryScannerInsulation`/`TestRetryLedgerRetention_C7_001` 全绿）
- [x] **端到端验证**（rule 22）：≥1 条 tcc 补偿链端到端（异常→CANCELLING→分支补偿→终态）；≥1 条 retry 扫描端到端（积压→单轮上界→下一轮继续）
- [x] **接线验证**（rule 23）：十四项逐项断言修复真实被消费链调用（W-10 例外口径：单测直调 + 生产接线可行性声明，`checkExpiredTransactions` 现零生产调用方）
- [x] **无静默跳过**（rule 24）：新增分支显式失败/留痕；无空 catch/静默 return
- [x] 零触碰核对（git diff：orm/_/xdef/平台设施零改动；2515 产物面零重开）

Exit Criteria:

- [x] 14 工单全部 landed（14 Fix），各带 ≥1 可辨识验收载体；5 项部分缓解项钉对 2515 基线先红
- [x] 红①–红⑭ 先红后绿或变异/回退论证留痕（概率腿未命中诚实登记）
- [x] 零回退实证：`./mvnw test -pl nop-retry` + `./mvnw test -pl nop-tcc` BUILD SUCCESS，签名=基线+新钉
- [x] 端到端/接线/无静默跳过三项已核验
- [x] `ai-dev/logs/` 已更新

### Phase 3 - 全量验证 + owner doc 落账

Status: completed
Targets: 双聚合器全量 + owner doc

- Item Types: `Proof`

- [x] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` + 聚焦测试组合，理由随记录声明）
- [x] E3 批行验证：`./mvnw test -pl nop-retry -am` 至阻断面留痕 + 等价分解通道（`-pl nop-retry` / `-pl nop-tcc`）验收
- [x] owner doc 落账：`nop-retry.md`（参数边界节 + 回调环护栏 + 扫描上界 + C7-014 隔离）+ `nop-tcc.md`（时钟/期限节 + 补偿链韧性 + 收尾方向语义）+ `source-anchors.md` 按实际
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--module nop-retry|nop-tcc --severity high` EXIT=0
- [x] `ai-dev/logs/` 已更新

Exit Criteria:

- [x] E1 + E3 验收且逐条登记
- [x] owner doc 落账核对（两文件各落点在案）
- [x] 零触碰确认登记
- [x] `ai-dev/logs/` 已更新

### Phase 4 - closure audit + 收口

Status: completed
Targets: 本 plan、roadmap 8.50 行、cra-index P2-G17 束行

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 成立性，含 Anti-Hollow；C1-002 已修核验复核；2515 零重开核对；零回退钉全列核对
- [x] cra-index 台账回填：P2-G17 束行收敛注（14 成员 fixed，C1-002 标注「2515 已修核验」+ C10-005/009 兑现 + C8-003=CL-005 同位点主处置关闭）；roadmap 8.50 → done
- [x] 双门禁：check-plan-checklist --strict EXIT=0（Closure Evidence 写入后复跑）+ scan-hollow 复核登记；Closure Evidence 写入本 plan 末尾；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [x] closure audit 证据已写入 plan 末尾 Closure 段
- [x] cra-index P2-G17 收敛注、roadmap 8.50 = done 在案
- [x] 双门禁退出码 0 登记在案
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

- [x] 14 工单全部收口（W-01..W-14 全 Fix）〔closure audit 逐工单 live 核对〕
- [x] 每工单 ≥1 条验收载体（rule 25，工单号可辨识）；红①–红⑭ 均有先红后绿或变异/回退论证
- [x] 2515 零重开：裁定①–⑨ 产物与 13 成员修复面零回退（git diff 核对）；既有钉族全绿
- [x] 全量构建绿：E1 留痕 + E3 双聚合器验收
- [x] owner doc 落账完成（`nop-retry.md` + `nop-tcc.md` + `source-anchors.md`）
- [x] scan-hollow nop-retry/nop-tcc `--severity high` EXIT=0
- [x] 不存在被静默降级的 in-scope live defect（C7-011 为批次定义层排除）
- [x] 独立子 agent closure-audit 完成并记录证据
- [x] **Anti-Hollow Check**：修复真实生效 + 无空方法体/静默跳过
- [x] `./mvnw compile` + `./mvnw test`（双聚合器）；checkstyle/imports 纪律
- [x] `check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0
- [x] cra-index P2-G17 收敛注 + roadmap 8.50 → done

## Deferred But Adjudicated

### C7-011（G17 第 15 成员——墙钟敏感跨单元）

- Classification: `out-of-scope improvement`（批次定义层拆分：G17 = R4-10（14）+ R4-32（C7-011））
- Why Not Blocking Closure: 已拆出 R4-32（R28 墙钟批次，cra-index P2-G17 行注记在案）；本批 W-08 仅收敛 tcc 期限/租约的时钟源，不做跨单元单调时钟注入。
- Successor Required: `yes`
- Successor Path: R4-32（roadmap 8.72）。

## Non-Blocking Follow-ups

- tcc 过期扫描页内并行化（若裁定采熔断形态则并行化归优化轮）
- retry 扫描积压深度指标（本批只补上界与完成时间，深度 gauge 归可观测轮）
- `DbEstimatedClock` 在 tcc 侧的精度/漂移治理（本批只切换时钟源）

## Risks And Rollback

- **W-02 Registry 原子化**：`computeIfAbsent` 式改造若与现有 put/restore 叠放语义冲突会改变嵌套事务行为——裁定 A 须推演四组 put/restore；回滚=单点可逆。
- **W-03 守卫改 DB 复核**：每分支创建多一次 DB 读，高频分支场景成本上升；且若 DB 读失败需 fail-open/fail-close 权衡；回滚=单点可逆。
- **W-04/W-05/W-06/W-07 补偿链改造**：触及 TCC 核心状态机，误改会导致「失败事务以 confirm 方向释放预留资源」（最严重业务后果）；每条须显式论证方向语义不变，`TestTccTransactionEndStates` 为回归门。
- **W-08 时钟切换**：`getDbEstimatedClock` 依赖 DB 时钟，多库/异地部署下与本地时钟有偏移，可能改变 expire 判定时点；须声明部署语义。
- **W-12 扫描上界**：上界过小导致积压消化不足；须可配+缺省值论证。
- **W-14 参数校验 fail-fast**：存量部署若配了 0 值/负值，升级后启动失败——须升级注记（对齐 2541 throttle 校验先例）。

## Closure

Status Note: R4-10 批 14 工单全按 Fix 收口（W-01..W-14），四项 2515 部分缓解残留本体（W-01/W-04/W-06/W-11/W-13）全部落地且钉对缓解后基线先红；红①–红⑭ 十三条以「单点变异 → 钉确定性 FAIL → `git checkout` 恢复 → md5 一致」逐条留证（红⑬ 为结构/语义钉，诚实登记无 H2 行为差异）。2515 裁定①–⑨ 与十三成员修复面零重开（`git diff 3005ae8afa..HEAD -- '*/src/main/*'` 逐块核对：`RetryRecordStoreImpl` 纯新增 limit 防御、`TccRecordStore` 两参重载改写即 W-13 目标本体）。所有 in-scope live defect 已修，无降级 deferred。
Completed: 2026-11-08

Closure Audit Evidence:

- Reviewer / Agent: agent_518b3883-3b31-4006-a36a-41055816d452（独立 fresh session；R1 133 次工具调用 / R2 34 次）
- Audit Session: **R1 REJECTED — 2 Blocker / 1 Major / 2 Minor** → 全部修复 → **R2 APPROVED-with-corrections — 0 Blocker / 0 Major / 2 Minor**。R2 独立复现了全部关键变异（含旧失败模式已消失、KILLED 孤儿已结算、跨轮到达的第二孤儿可收敛、写侧 min/max 混源回归可捕获）。
- R2 Minor 处置：m-1 取消方向孤儿筛的冗余判定已删（`isBranchCancellable` 已覆盖）；m-2 共享 worktree 过程注记 → 本批 `nop-batch/` 未提交改动属并发 plan 2545 工作流，非本 plan 范围，已在本日志与 plan 中交叉说明。
- Evidence:
  - **A 工单落地（14/14 PASS）**：W-01 `RetryEngineImpl:386-424` 四分支分流（非 UK 分支 :391 纯失败传播、不造死信）；W-02 `TccTransactionRegistry:47-77`（锁 `getAttrs()` + 双检 + 条件恢复 + 包级 `instance(IContext)`）；W-03 腿1 `TccEngine:461-490` 三拒绝面（读失败 fail-close `:472` / 缺失 `:478` / 状态非 TRYING `:483`）、腿2 `TccTransaction:247-350` 孤儿收敛；W-04 `TccTransaction:228-233` 写点闸 + `TccEngine:514-533` 纪元（缺失=inactive `:525`）；W-05 `TccBranchTransaction:158-167` null 判空 + `TccRunner:47-187` 三处三通道兜底；W-06 `TccRunner:215-332` 逐分支隔离 + **结算表（KILLED/CONFIRM_SUCCESS 计已结算 `:286/:323`——B-1 blocker 修复面）**；W-07 `TccTransaction:139-211`（有限重试非 CAS `:198-206`）+ `TccEngine:406-414` suppressed + `:96-99` 超时默认 cancel；W-08 `TccRecordStore:402-423` 双 seam 覆盖全部时钟点（`nextCheckTime` 归写侧 `:363-364`）；W-09 `RetryEngineImpl:890-942` 尾部 `_callback` 计数；W-10 `TccEngine:558-638` 预算+熔断+双计数+`checkExpiredAsync` protected；W-11 `RetryEngineImpl:1035-1054` 显式循环（`doStart:1017` 真实接线）；W-12 `RetryScannerImpl:261-338`（非空批口径 `:317`、时长 `:279`、RoundEndReason、时钟 seam）；W-13 `TccRecordStore:435-480` 私有批助手分页；W-14 `RetryScannerImpl:85-144` + `RetryRecordStoreImpl:236-240`。
  - **R1 发现处置（5/5 闭环）**：
    - **B-1（plan 未收口）** → 本段 + Phase 1–4 状态/勾选/Closure Gates/roadmap/cra-index 全部落盘。
    - **B-2（W-03 腿2 语义破损 + 钉伪绿）** → `findOrphanBranches` 以入口快照求差且不扣已结算孤儿，导致孤儿每轮都被当孤儿重驱动、3 轮后必 `CONVERGENCE_EXHAUSTED`（终态永不折叠）；且聚合输入 `authoritative` 完全不含孤儿。重构为 `buildAllViews`（聚合输入覆盖 DB 全部行）+ `findUnsettledOrphans`（只挑「快照外 ∧ 本方向仍可驱动」）+ `RowOnlyBranchTxnView`；钉 `pin03b` 改为**第 2 次装载**才注入孤儿并双断言（孤儿被驱动为 CANCEL_SUCCESS **且** 终态折叠为 CANCEL_SUCCESS）。变异复核：把 pending 置空 → `expected: <CANCEL_SUCCESS> but was: <TRY_SUCCESS>` **RED**。
    - **M-1（W-08/红⑧ 无钉子）** → 新增 `TestCraR410TccClockPins`（nop-tcc-dao）：**不覆写 seam**，用 stub `IOrmEntityDao`（`getDbEstimatedClock()` 返回已知 min/max）断言基类取哪个时钟；变异 `nowForRead` 回退本地墙钟 → `expected: <1111111> but was: <1794139417974>` **RED**。
    - **m-1（未用 import）** → 删除 `TccRecordStore` 的 `CoreMetrics` import。**m-2（死 seam）** → 删除遗留 `getCurrentTime(dao)`。
  - **B Anti-Hollow PASS**：diff 内无空方法体 / 静默 `continue` / `return null` 占位 / 吞异常；`dispatchScannerBatch` ← `doStart:1017` 与 `nowForRead` ← `fetchExpiredRecords:363` 真实接线；`scan-hollow-implementations --module nop-retry --severity high` **EXIT=0**、`--module nop-tcc` **EXIT=0**。
  - **C 2515 零重开 PASS**：产物零删除零弱化；两个改动测试文件为**契约正确性更新非弱化**——`TestTccEngine` 两处期望值按 W-07 新契约校正（`CONFIRM_SUCCESS→CANCEL_SUCCESS`、`CONFIRMING→BEFORE_TIMEOUT`，E-2543-1 登记，断言强度保持精确状态+retryTimes）；`FakeTccRecordStore` 构造器仅放宽可见性以支持跨包 seam。既有钉族全绿（`TestTccRunner` 10 / `TestTccTransactionEndStates` 6 / `TestTccCasPropagation_C10_003` 3 / `TestTccStoreCasConflict_C10_003` 2 / `TestTccLedgerCleanup_C10_007` 4 / `TestTccLedgerHygiene_C7_005` 3 / `TestRetryScannerInsulation` 3 / `TestRetryLedgerRetention_C7_001` 7）。
  - **D 红绿诚实性 PASS**：审计方独立复现 4 条变异（结算表回退 / null 判空删除 / 批数上界关闭 / 连续超时熔断关闭）全部 **RED**；红⑬ 无 H2 行为差异的诚实声明经其复核**确认准确**。红③b 与红⑧ 的伪绿/缺钉已按 B-2/M-1 重做并单独变异复核。
  - **E 文本一致性 PASS**：Plan Status=completed；Phase 1–4 全 completed；全部 Exit Criteria 与 12 条 Closure Gates 已勾；roadmap 8.50=**done**；cra-index P2-G17=**fixed** 含收敛注。
  - **F 测试实况**：`./mvnw -o test -pl nop-retry,nop-tcc` **BUILD SUCCESS**——nop-retry 20/76/4/1、nop-tcc 87（core）/41（dao，含新增 2 钉）/2（service）/1（web），全部 **0F 0E 0S**。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2543-...md --strict` → **EXIT=0**（本段写入后复跑）。
  - `node ai-dev/tools/check-doc-links.mjs --strict` → **EXIT=0**。
  - **Deferred 分类检查**：C7-011 为批次定义层拆分（G17 = R4-10 14 + R4-32 C7-011），successor = roadmap 8.72，非降级；无非 blocking 化处理的 in-scope live defect。

Follow-up:

- W-03 腿2 残余窗（分支在终态 CAS 之后才提交）需 DB 级约束才能彻底闭合 → watch-only，归 ORM 扩面治理（R-G）。
- W-04 弃链闸不阻止已发起的分支补偿 RPC（由 CAS 短路 + 参与方幂等性承接）→ watch-only。
- W-10 `checkExpiredTransactions` 零生产调用方（C6-005 dormant，G19 批外）→ 恢复扫描宿主装配归后续批次。
- C7-011 跨单元单调时钟注入 → successor roadmap 8.72（R4-32）。

