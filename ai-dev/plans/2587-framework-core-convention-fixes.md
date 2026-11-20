# 2587 框架核心与持久层收敛修复（nop-orm/nop-dao/nop-core）+ nop-rg 预滤缺陷

> Plan Status: draft
> Last Reviewed: 2026-11-21
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

Status: planned
Targets: `BatchActionQueueImpl.java`、`AbstractTransaction.java`、`GenerationFence.java`、`EntityPersisterImpl.java`、`OrmSessionImpl.java`

- Item Types: `Fix | Decision`

- [ ] flushing 期间 enqueue fail-fast（类型化拒绝）或重入内容移交下一轮（二选一，随实现裁定，删除误导注释）
- [ ] AbstractTransaction：CLOSED 转换加 CAS 门；新增 ERR_TXN_INVALID_STATE 替换状态机误用错误码；rollback-only 行为变更登记 owner doc 迁移说明
- [ ] GenerationFence 触顶改按 key 淘汰或触顶后禁用回填一轮
- [ ] pendingTxnEvictKeys 在 close 路径同步清理（或弱引用）
- [ ] OrmSessionImpl 裸 IllegalStateException 改 NopException + 新错误码

Exit Criteria:

- [ ] 钉：flush 期间 enqueue 要么被显式拒绝（异常断言）要么在下一轮执行，无静默丢失
- [ ] 钉：并发 close/commit 状态断言；错误码断言更新
- [ ] 核查外部调用方是否依赖 rollback-only 抛错语义（grep + 登记 open question 结论）
- [ ] `ib test -pl :nop-orm,:nop-dao` 全绿
- [ ] owner doc/design 同步（rollback-only 迁移说明、错误码），`ai-dev/logs/` 已更新

### Phase 2 - nop-rg 预滤正确性 + core 杂项

Status: planned
Targets: `RegexLiteralExtractor.java`、`RegexSearcher.java`、`ResourceDependsManager.java`

- Item Types: `Fix`

- [ ] mayMatchAcrossLines 进入 `[...]` 后继续扫描转义序列（\n \r \x \012 \u 及真实行终止字符即拒绝）；extract 对含此类构造的类不拼接 run
- [ ] ResourceDependsManager 重执行路径对身份型 task 登记豁免/告警
- [ ] 三语料 rg 对拍回归（0 skipped 硬门禁口径）覆盖跨行字符类用例

Exit Criteria:

- [ ] 钉：`a[\n]b`、`a[\x0A]b`、`a\u000Db` 三用例搜索结果与 native rg 语义一致（跨行匹配不被丢弃）
- [ ] 变异探针红绿（回退提取器 → 钉必红）
- [ ] `ib test -pl :nop-rg-core,:nop-core` 全绿
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] BatchActionQueueImpl 静默丢弃面修复并有钉
- [ ] nop-rg 跨行字符类缺陷修复并对拍通过
- [ ] rollback-only 行为变更登记完成
- [ ] focused tests 全绿
- [ ] 独立子 agent closure audit 完成，evidence 写入本文件
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm --severity high` 退出码 0

## Deferred But Adjudicated

### OrmSessionRegistry unregisterPendingClose O(n) 扫描与 instance() 热路径 sweep

- Classification: `optimization candidate`
- Why Not Blocking Closure: 性能优化项，无正确性影响
- Successor Required: `no`

## Non-Blocking Follow-ups

- MdxQuerySplitter 行为变更迁移说明补录；ResourceDependsManager 重执行语义 design 登记

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:
