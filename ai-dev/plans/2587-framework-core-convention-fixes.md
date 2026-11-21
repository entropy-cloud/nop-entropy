# 2587 框架核心与持久层收敛修复（nop-orm/nop-dao/nop-core）+ nop-rg 预滤缺陷

> Plan Status: active
> Last Reviewed: 2026-11-21
> Adversarial Review: 2026-11-21 独立子代理 1 轮通过（6 先修已吸收）
> Source: `ai-dev/analysis/2026-11/2026-11-21-monthly-new-code-deep-audit.md`（框架核心分区 + nop-ib/rg 分区 finding 3）
> Related: `ai-dev/plans/2583-nop-ib-correctness-fixes.md`

## Purpose

修复 ORM/DAO 本轮并发修复中残留的窄窗缺陷与行为变更登记缺口，统一错误处理规范，并修复 nop-rg 候选行预滤假阴性。

## Current Baseline

- `BatchActionQueueImpl.java:256`：flush finally 无条件清空新容器，重入 enqueue 静默丢弃；whenComplete 只处理快照集合，flush 期间新增集合动作不执行也不通知（与 2.3-C2-009 修复声明不符）。
- `EntityPersisterImpl.java:597-632`：pendingTxnEvictKeys 以强引用 ITransaction 为 key，未完成事务 close 时泄漏。
- `AbstractTransaction.java`：close() 无条件置 CLOSED 无 CAS 门；rollback 状态机错误复用 ERR_TXN_ALREADY_STARTED；rollback-only commit 由抛错改自动回滚 + WARN（行为变更未登记迁移）。
- `GenerationFence.java:45-53`：容量触顶整体 clear 可使孤儿 fence CAS 放行迟到回填。
- `OrmSessionImpl.java:139`、`DaoWorkflowStore`（归 2585）、`PollingJdbcMessageService`（归 2585）等裸异常；本计划管 nop-orm 的 OrmSessionImpl。
- `ResourceDependsManager.java:104-121`：mtime 失配重执行 task 最多 3 次，编译型 task 身份副作用可能重复注册。
- `nop-rg RegexLiteralExtractor.java:137-186`：字符类内行终止构造漏检 → 候选行预滤静默丢匹配结果。

## Goals

- flush 重入语义 fail-fast 或移交下一轮（不再静默丢弃）；事务状态机与错误码语义收敛；nop-rg 搜索结果恢复正确性。

## Non-Goals

- 不重做 ResourceComponentManager 缓存架构；不回滚已裁定的 rollback-only 行为变更（只做登记与钉）；不动 MDX fail-fast 方向（只补迁移说明）。

## Scope

### In Scope

- nop-persistence/nop-orm（BatchActionQueueImpl、EntityPersisterImpl、GenerationFence、OrmSessionImpl、OrmSessionRegistry、OrmTemplateImpl）
- nop-persistence/nop-dao（AbstractTransaction）
- nop-kernel/nop-core（ResourceDependsManager）
- nop-tools/nop-rg/nop-rg-core（RegexLiteralExtractor、RegexSearcher）

### Out Of Scope

- DaoWorkflowStore / PollingJdbcMessageService 错误码（归 2585）；ExecutorHelper 等粗扫区域重审。

## Execution Plan

### Phase 1 - ORM/DAO 语义收敛

Status: completed
Targets: `BatchActionQueueImpl.java`、`AbstractTransaction.java`、`GenerationFence.java`、`EntityPersisterImpl.java`、`OrmSessionImpl.java`

- Item Types: `Fix | Decision`

- [x] flushing 期间重入 enqueue 裁定为「移交下一轮」（finally 不清空新容器、留待下轮 flush；不选 fail-fast：重入点多经 delay task 吞异常路径 :307 不可达）；删除误导注释；collectionActions 快照不含 flush 期新增项一并处理（钉：TestBatchActionQueueSwapCapture#testReentrantEnqueueCarriedToNextFlushRound + TestBatchDelayTaskCleared 按 2587 契约改写）
- [x] AbstractTransaction：close() 加 CAS 门——形态为「任意状态 CAS 到 CLOSED，双 close no-op」（close 多经 IoHelper.safeClose 在 finally 调用，不得抛异常）；状态机误用错误码：裁定复用既有 `DaoErrors.ERR_TXN_ALREADY_FINISHED`，不新增 ERR_TXN_INVALID_STATE（open 双开保留 ERR_TXN_ALREADY_STARTED）；rollback-only 行为变更登记 owner doc 迁移说明（transaction-boundaries.md 既有条目已含 commitAsync/beforeCommit 限定，plan 2587 补错误码与 close 门两条），并显式裁定：commitAsync（:405-406）与 beforeCommit（:379-380）抛错保留点与 sync 路径的分叉作为已知行为登记（不扩大行为变更面），补覆盖两路径的钉测试（TestTransactionStateCas pin_2587_a..e，9 tests 全绿）
- [x] GenerationFence：前提探针钉验证结论——**前提不成立，本项撤销**（探针 TestCra2517GenerationFence#testPlan2587_probe_capClearOrphanFenceCannotPassCas：clear 后 fence 重建走 alloc 单调分配器，新初始世代严格大于全部历史世代，孤儿旧 readGen CAS 必败；审查预判成立）。勘误已登记底账 `ai-dev/analysis/2026-11/2026-11-21-monthly-new-code-deep-audit.md` Erratum 节；探针钉保留为防回归证据
- [x] pendingTxnEvictKeys 在 close 路径同步清理（listener 增 onClose 致 `pendingTxnEvictKeys.remove(t)`；钉：TestPlan2587PendingTxnEvictCleanup pin-a/pin-b）
- [x] OrmSessionImpl 裸 IllegalStateException 改 NopException + 新错误码（`OrmErrors.ERR_ORM_MODEL_ALREADY_CLOSED`）

Exit Criteria:

- [x] 钉：flush 期间 enqueue 要么被显式拒绝（异常断言）要么在下一轮执行，无静默丢失（TestBatchActionQueueSwapCapture#testReentrantEnqueueCarriedToNextFlushRound：缺陷态 finally 清空 → 动作丢失 → 红；修复后下一轮 flush 执行）
- [x] 钉：并发 close/commit 状态断言；错误码断言更新（pin_2587_c/d 双 close no-op + 并发 close 恰一次 doClose；pin_2587_a/b ERR_TXN_ALREADY_FINISHED 断言）
- [x] 核查外部调用方是否依赖 rollback-only 抛错语义（grep + 登记 open question 结论）——结论已登记底账 Open Questions：全仓无外部调用方依赖抛错语义
- [x] `ib test -pl :nop-orm,:nop-dao` 全绿（共享 worktree 受并行 2584 代理 nop-ai-agent 半成品测试编译阻塞，经授权等价面 `ib mvn -pl :nop-orm,:nop-dao -am install`（含 test）完成：nop-dao 359/0/0、nop-orm 553/0/0/77 skipped（skip 为既有基线））
- [x] owner doc/design 同步（rollback-only 迁移说明、错误码），`ai-dev/logs/` 已更新（transaction-boundaries.md 状态机语义节新增 ERR_TXN_ALREADY_FINISHED 与 close CAS 门两条；check-doc-links --strict 0 错误）

### Phase 2 - nop-rg 预滤正确性 + core 杂项

Status: completed
Targets: `RegexLiteralExtractor.java`、`RegexSearcher.java`、`ResourceDependsManager.java`

- Item Types: `Fix`

- [x] mayMatchAcrossLines 进入 `[...]` 后继续扫描转义序列（\n \r \x \012 及真实行终止字符即拒绝，新 helper `skipClassScanLineTerminators`）；extract 对含此类构造的类不拼接 run（既有行为已满足——类内容从不进入 run，钉 testPlan2587_extractDoesNotConcatenateClassContentAcrossRuns 固化）。裁定补记：mayMatchAcrossLines=true 的 pattern（literal=null）原落整文件路径会产出 rg 行模式不可能给出的跨行命中——RegexSearcher 新增逐行匹配路径 `findAllByLines`（按 \n 切行逐行跑 pattern，^$ 行边界锚定，与 rg 行模式语义一致；接线钉 testPlan2587_searcherRoutesClassLineTerminatorToLineScan 断言路由；原 2574 钉 testCrossLinePatternFallsBackToWholeFile 按 2587 契约改写 + 新增行内真命中保留对照钉）
- [ ] （移除：ResourceDependsManager 项降级为 follow-up——Supplier 无身份元数据，「身份型 task 识别」机制不存在，:118 已有 WARN）
- [x] 三语料 rg 对拍回归（0 skipped 硬门禁口径）覆盖跨行字符类用例（RgEncodingParityTest#testCrossLineCharClassParityWithRg：`a[\n]b`/`a[\x0A]b` 双方 0 命中、a<CR>b 双方命中且非空防恒空假绿；rg 15.1.0 环境实测 3 tests / 0 failures / 0 skipped）

Exit Criteria:

- [x] 钉：`a[\n]b`、`a[\x0A]b`、`a<CR>b` 三用例结果与 native rg 完全一致（rg 行模式下前两者双方均为 0 命中）+ 提取器对含行终止构造的字符类不拼接 literal run（testPlan2587_extractDoesNotConcatenateClassContentAcrossRuns）
- [x] 变异探针红绿（回退提取器 → 钉必红：skipClassScanLineTerminators 接线回退为 skipClass → RegexLiteralExtractorTest 13 tests / 2 failures 红（lineTerminatorEscapesInsideCharClassRejected + searcherRoutesClassLineTerminatorToLineScan）→ 恢复（grep 断言可执行行存在）→ 23 tests 全绿复绿）
- [x] `ib test -pl :nop-rg-core,:nop-rg-cli,:nop-core` 全绿（共享 worktree 受并行代理阻塞面同上，经授权等价面 `ib mvn -pl :nop-rg-core,:nop-rg-cli -am install`（含 test，含 nop-commons/nop-core 依赖图）完成：rg-core 115 tests 全绿（Skipped 2 为既有基线）、rg-cli 44 tests / 0 failures / 0 skipped；nop-core 483+ tests 全绿随依赖图通过）
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] BatchActionQueueImpl 静默丢弃面修复并有钉
- [x] nop-rg 跨行字符类缺陷修复并对拍通过
- [x] rollback-only 行为变更登记完成
- [x] focused tests 全绿（nop-dao 359 / nop-orm 553 / nop-rg-core 115 / nop-rg-cli 44，全 0 failures；skipped 均为既有基线）
- [ ] 独立子 agent closure audit 完成，evidence 写入本文件（主控负责）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（主控负责）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm --severity high` 退出码 0（主控负责）

## Deferred But Adjudicated

### ResourceDependsManager mtime 失配重执行 task 的身份副作用

- Classification: `watch-only residual`
- Why Not Blocking Closure: 无识别机制可落地，:118 已有 WARN 兜底；登记为已知残留
- Successor Required: `no`

### OrmSessionRegistry unregisterPendingClose O(n) 扫描与 instance() 热路径 sweep

- Classification: `optimization candidate`
- Why Not Blocking Closure: 性能优化项，无正确性影响
- Successor Required: `no`

## Non-Blocking Follow-ups

- MdxQuerySplitter 行为变更迁移说明补录；ResourceDependsManager mtime 重试身份副作用登记为已知残留（见 Deferred）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
