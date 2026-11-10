# 2554 CRA Phase 8.60 — R4-20 2.5 链复制/PubSub 关联束（13 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-10
> Source: roadmap 工作项 8.60；批次定义 = batch-proposal.md §二 :135 R4-20 行；成员发现 = `ai-dev/audits/2026-10/2026-10-19-0624-deep-audit-nop-persistence-peri-concurrency/`
> Design Rulings: A-M + N（CL-025 移出）——`../design/concurrency/persistence-peri-chain-pubsub-batch.md`
> Related: plan 2518（8.53 组 A-D，本单元前批）、plan 2553（R4-19）；设计文档 = `ai-dev/design/concurrency/persistence-peri-chain-pubsub-batch.md`（裁定 A-M+N）
> **PA**：无（13 项全 Java 层，零 orm.xml/model 触碰）
> **特殊约束**：① MA-08 携带历史 D03-02 收口义务；② `TestLettucePubSubConcurrency`（2518）零回退；③ `awaitLagZeroChains` RP-2 双钉零改写（`IncrementalLagProbe.java:57-72` javadoc）；④ C7-008 墙钟面拆出 R4-32（deferred 登记 successor）
> 成员取证（fresh session @ 2026-11-10）：原样 10 + 部分覆盖 3（C1-008、C7-008、C7-009）+ 已修 0

## Purpose

关闭 R4-20 的 **13 个 P2 工单**：校验规模包络族（MA-07 流式 keyset 归并）、Redis 原子族（MA-08 写-TTL Lua 原子化[D03-02 收口]、MA-09 computeIfAbsent SET NX 原子化）、驱动批写族（MA-11 td 三因子 chunk + rpc 拆分）、会话契约族（C1-001 回调线程契约、C1-002 注册面并发化）、迁移编排残余（C1-008 volatile 收口、C3-010 qs 对齐、C3-011 taskKey qs、C3-012 位点三方对齐、C7-006 abort 覆盖面、C7-008 namespace 过滤[墙钟面→R4-32]、C7-009 link 复位）。

## Baseline（live 亲读 @ 2026-11-10，取证 agent 独立核验）

- `ConsistencyVerifier.java:132-135,160-188`（nop-orm-migration）双侧 scanHashes 全量驻堆 + offset 深分页。
- `LettuceHashOperations.java:193-196,209-217,227-236` + `LettuceSessionStore.java:62-73`（nop-nosql-lettuce）两步写未原子化；原子兄弟在册。
- `LettuceMessageService.java:62-70,156-166` + `LettuceHashOperations.java:43-57,115-118` GET→SET 非原子；`NosqlCache.java:63-65,90-92`（nop-nosql-core）委托违约。
- `TdEntityPersistDriver.java:128-170` 整批单 SQL 无上界；`RpcEntityPersistDriver.java:310-341,368-411` 整批单请求；上游接缝 `BatchActionQueueImpl:172,202` 直传（零改动）。
- `RpcEntityPersistDriver.java:270-286,378-411` RPC 传输线程变更会话状态；接口 javadoc 无契约。
- `MigrationEngine.java:68-69,109-121` 裸 HashMap 注册面；`MigrationExecutor.java:28-34` 非 final。
- `OrmMigrationOrchestrator.java:87-92,152,162,506` 五惰性字段非 volatile + doAbort 锁外直读（访问器已 synchronized；stop 路径 :268/:333/:636 故意直读=豁免面）。
- `IncrementalLagProbe.java:119-130` 观测走 stateQuerySpace（sink 提交走 target qs，`ShardedUpsertConsumerProvider.java:90,100`）；`FullCopyBatchJob.java:61,97-100,135` taskKey 无 qs 维度。
- `CdcOffsetSeedBuilder.java:61-63,95-108` + `IncrementalJobManager.java:176,188` 三方无对齐校验。
- `OrmMigrationOrchestrator.java:493-518` doAbort 不覆盖 FULL 批任务；`FullCopyBatchJob.java:107-109` 同步到底无取消。
- `IncrementalLagProbe.java:86-96,119-130` 墙钟 sleep（→R4-32）+ 全表 COUNT/MAX 无 namespace 过滤。
- `LettucePubSubService.java:97,122-135,182-228` doStop 不复位 link、getOrCreateLink 无有效性校验。
- 基建：nop-nosql-lettuce testcontainers 真 Redis（`TestLettuceNosqlService` 77 例 + `TestLettucePubSubConcurrency`）；orm-migration H2+fake；rpc/tdengine fake 通道。

## Goals

- 13 工单全部落地且断言载体先红后绿或变异论证（≥3 链）。
- 2518 回归面零回退（`TestLettucePubSubConcurrency`、`TestChainStopProtocol`、`TestStartFallbackChainsCompensation`、`TestMigrationHistoryGuard`、`TestIncrementalLagProbe`）。
- owner doc（`model-first-development.md` 2.5 面 + `data-migration.md` 视触达）落账。

## Non-Goals

- C7-008 墙钟 sleep 单调化（→ R4-32，deferred 登记 successor）。
- C7-006 跨实例注册表（→ watch-only，状态行 fencing 承载拒绝面）。
- MA-08 string 键原子兄弟面改造（已在册合格）。
- **C3-CL-025 FullCopyBatchJob 深分页 keyset 化**（需扩展 nop-batch-jdbc `JdbcPageBatchLoaderProvider`，超本批模块边界——Deferred 登记 successor）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-N + fresh-session 取证 10 原样/3 部分覆盖 + 三接缝约束落位）

- [x] 设计文档（裁定 A-N + 13 载体表 + 回归接缝；CL-025 移出=裁定 N）
- [x] 成员取证（fresh session：10 原样 / 3 部分覆盖 / 0 已修；C7-008 新形态专节重推导）
- [x] 独立 draft review（R1 must-fix-first 5M+5m 全闭环[CL-025 入 Deferred/裁定 K context.cancel(STOP)+表间检查/裁定 L 前缀谓词/裁定 D 2553 守卫不变式/Minor 5 项]；R2 ready-to-execute）

### Phase 2 - 13 工单实现 + 回归测试

Status: completed（13 工单生产面+载体全落盘 @ 2026-11-10；三腿变异红证 `_tmp/red-green-ledger-2554.md`）
Targets: nop-orm-migration / nop-nosql-lettuce / nop-nosql-core / nop-orm-rpc / nop-orm-tdengine / nop-db-migration + 测试树

- Item Types: `Fix`（13）

**实施顺序**

- 组 1（Redis 原子族）：MA-08 → MA-09 → C7-009
- 组 2（迁移编排）：MA-07 → C1-008 → C3-010 → C3-011 → C3-012 → C7-006 → C7-008
- 组 3（驱动/契约）：MA-11 → C1-001 → C1-002

Exit Criteria:

- [x] 13 工单全部实施
- [x] 载体表逐条存在且断言目标行为
- [x] 关键项先红后绿或变异论证（≥3：MA-08 TTL 两步、MA-09 非原子 SET、C7-009 link 复用）
- [x] **D03-02 收口义务**：session 入口原子化断言可观察
- [x] **2518 回归面零回退**：TestLettucePubSubConcurrency + TestChainStopProtocol + TestStartFallbackChainsCompensation + TestMigrationHistoryGuard + TestIncrementalLagProbe 全绿
- [x] **RP-2 双钉零改写**：awaitLagZeroChains 既有钉断言零改动复绿
- [x] **2553 守卫不变式**：MA-11 rpc chunk 路径 batchExecuteAsync 返回 future 同步完成（未 done 即 ERR_ORM_BATCH_FLUSH_ABORTED 守卫零回退）
- [x] E1 全量编译门禁 BUILD SUCCESS（排除 nop-cli 已知面；`_tmp/e1-2554-full-build-excli.log` EXIT=0）
- [x] E3：逐模块归因绿（orm-migration 100/nosql-core 2/nosql-lettuce 109[**复现条件 `-Dnop.test.docker.enabled=true`**，缺省形态 skip 85 含全部新载体]/orm-rpc 15/tdengine 18/db-migration 104 全 0F0E；nop-orm 接缝零改动[git diff 确认]）
- [x] owner doc 落账：`model-first-development.md`（PubSub/迁移契约节）+ 触达的 owner doc
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow 五叶子 EXIT=0 + tdengine 两处 pre-existing 保安定 fail-fast 豁免登记（TdMessageService 能力声明，本批零触碰）
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（13 工单）
- [x] 所有 in-scope confirmed contract drifts 已收敛（ICache 契约/线程契约）
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（C7-008 墙钟面=successor 明确的 deferred 类）
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [x] `./mvnw compile`（六模块）
- [x] `./mvnw test`（六模块逐模块）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### C7-008 墙钟 sleep 单调化
- Classification: `watch-only residual`（P2 live defect 面，successor 明确承接——moved to explicit successor ownership）
- Why Not Blocking Closure: 墙钟敏感跨单元批 R4-32 统一单调时钟注入（C7-013/C8-005 同批设计，roadmap 8.72 在册成员）；本批 namespace 过滤面已收敛跨作业污染面，时钟面单独承接不产生半修。
- Successor Required: `yes`
- Successor Path: roadmap 8.72（R4-32）

### C3-CL-025 FullCopyBatchJob 深分页 keyset 化
- Classification: `out-of-scope improvement`（R1-Major-1/2：需扩展 nop-batch-jdbc 通用 `JdbcPageBatchLoaderProvider`，含断点语义兼容，超本批模块边界）
- Why Not Blocking Closure: MA-07 族成员（ConsistencyVerifier 双侧驻堆）本批全收敛；CL-025 为同模式线索站点非族成员，FullCopyBatchJob 现行 offset 分页功能正确（规模包络属优化面）。
- Successor Required: `yes`
- Successor Path: nop-batch 治理批（触发条款：同族无界账本批或 nop-batch-jdbc 治理时随批 keyset 化）

### C7-006 跨实例 abort 注册表
- Classification: `watch-only residual`
- Why Not Blocking Closure: 换实例 abort 面由 2518 状态行 fencing 拒绝承载（runVerify/runIncremental 入口 ABORT 相位类型化拒绝）；跨实例句柄注册表属设施增强。
- Successor Required: `no`

## Review 记录

**Draft review R1（fresh session，2026-11-10，must-fix-first）**：Major-1/2 CL-025 设计内计划外+机制不可落地→裁定 N 显式移出 scope+Deferred successor 登记；Major-3 裁定 L 谓词事实错误（sink_namespace=jobId|vertexId 非 jobId）→前缀匹配谓词+fallback WARN 回落档修订；Major-4 裁定 K 取消原语定位错误（context.cancel(STOP) 非 IBatchTask）+FULL 多表缝隙→FullCopyBatchJob 暴露 context 句柄+orchestrator abort 标志表间检查；Major-5 裁定 D 未声明 2553 同步完成守卫不变式→显式写死；Minor-6 判空模式/Minor-7 stop 路径豁免/Minor-8 裁定 M 收敛（删校验半句）/Minor-9 行号刷新/Minor-10 deferred 分类改 watch-only residual——全闭环。

## Closure

Status Note: 8.60/R4-20 全 13 工单落地（13 Fix），载体 17 类 40+ 钉全绿（真 Redis testcontainers + 真 H2 nop-batch abort），三腿变异红证，D03-02 义务收口，2518 回归面/RP-2 双钉零回退，2553 守卫不变式落死，owner doc + source-anchors 三行落账，E1 排除形态 + E3 六模块逐模块绿。closure audit APPROVED-with-corrections（1 Minor 文档级 + 2 cosmetic 非缺陷）修正后收口。
Completed: 2026-11-10

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit 子 agent（2026-11-10）
- Evidence:
  - 13/13 机制 live 命中（逐一 file:line 取证：keyset 流式 :240-332 无全量驻堆；Lua 四脚本+SESSION_SET D03-02 收口；SET NX/HSETNX 原子；doStop link=null :189-192；ConcurrentHashMap 注册面；volatile 五字段+访问器；doAbort→requestStop→context.cancel(STOP)+表间检查；qs 对齐；taskKey 三段；sink_namespace 前缀过滤**且 awaitLagZeroChains :89-100 字节级不变**[RP-2]；三方对齐+新错误码；td ChunkAccumulator+配置键；rpc chunk join done-on-return+判空投递）
  - 独立复跑：五模块 EXIT=0 + lettuce `-Dnop.test.docker.enabled=true` 109/109 0 skipped（审计者本机 docker 复现）；TestLettucePubSubConcurrency 文件零改动（2518 面零回退）
  - Anti-Hollow 三链追踪 PASS（abort 链/chunk done-on-return 链/keyset SQL 链）；无空体/静默 no-op
  - Deferred 诚实性 PASS（C7-008 墙钟面 successor=R4-32/CL-025 out-of-scope/C7-006 跨实例 fencing 承载）
  - 工具门禁：doc-links EXIT=0、scan-hollow orm-migration EXIT=0 + TdMessageService 豁免核验（零 diff，pre-existing 3a87df66a0）
  - 修正闭环：Minor-1 lettuce 复现条件（docker flag）已补入 E3 证据行与本日志；cosmetic 2/3（namespaceFallbackWarned 非 volatile 重复 WARN、IncrementalJobManager join/split 往返）登记为非阻塞 follow-up（非 live defect）
  - `check-plan-checklist.mjs --strict` EXIT=0（收口后复跑确认）

Follow-up:

- IncrementalLagProbe.namespaceFallbackWarned 可 volatile 化（重复 WARN 日志面，无行为影响）
- IncrementalJobManager:179 表清单 join/split 往返可用集合传递替代（表名含逗号为假设性风险）
