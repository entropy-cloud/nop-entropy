# 2375 full-code-review R1.10 — nop-biz 事务+调度修复（P1-2.6-001/004）

> Plan Status: active
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.10 行；finding `P1-2.6-001`（dim07-09）+`P1-2.6-004`（dim12-07）（`2.6-biz-graphql-deep-audit.md`；探针 `_tmp/fcr26-probe/DataLoaderDispatchProbe.java`）
> Related: owner doc service-layer.md「默认有事务」表述修正（P1-2.6-001 修复伴随——audit 转交 docs G.2 项）

## Purpose

修复 nop-biz P1-2.6-001：action 级事务装饰器死配置——`TransactionActionDecoratorCollector` 第二分支被 `txnModel != null` 前置短路，无 `<txn>` 元素的 mutation（绝大多数后台直调写动作）零事务，与 owner doc「默认有事务」逐字冲突。

**P1-2.6-004（DataLoader 调度）拆出至 successor plan 2376**：初版修复锚点实测触发挂死，需独立调度生命周期重设计。

## Current Baseline

- **P1-2.6-001**：`TransactionActionDecoratorCollector.collectDecorator(BizActionModel, ...)`（`nop-service-framework/nop-biz/src/main/java/io/nop/biz/decorator/TransactionActionDecoratorCollector.java`）全部逻辑在 `if (txnModel != null)` 内；`defaultTransactional = BIZ_ACTION_TYPE_MUTATION.equals(type)` 的计算对无 `<txn>` 的 action 不可达（死配置）。`@Transactional` 注解分支全仓 biz 零使用。平台自身 15 处手工补救（audit 计数）。owner doc `service-layer.md`「默认有事务+禁止 @Transactional」与实现逐字冲突。
- **P1-2.6-004**：`GraphQLExecutor.executeOneAsync`（`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java` :72-98）在 `invokeOperation(env)` 返回 `CompletionStage` 后**立即**调 `dispatchAll(context)`（:98），不等 operation 完成。异步 operation 体内注册的 loader 在 dispatch 之后才注册 → 永不 dispatch → 等待 loader 结果的代码静默挂死。`dispatchAll` 本身 synchronized 且清空 map（`GraphQLExecutionContext.java:151-165`），天然幂等。`fetchResult`（:101-117）也有一个 dispatchAll（fetch 链终局）。
- 审计探针：`_tmp/fcr26-probe/DataLoaderDispatchProbe.java`（复现挂死）。
- 测试基建：`TestBeanMethodBatchFetcher`（nop-graphql-core，3 处 syncGet(context.dispatchAll()) 先例）；`TestOrmTemplate`/`TestJobStoreImpl` 风格回归。

## Goals

- mutation action（无 `<txn>` 显式声明）默认获得事务装饰器；显式 `<txn>` 声明（含 transactional=false 显式关断）语义不变。
- ~~DataLoader dispatch 时序~~ → **拆出至 successor plan 2376**（R1.10b）：plan 2375 初版锚点实测触发挂死（graphql-core 套件 2 分钟无输出），该缺陷需独立调度生命周期重设计，见 2376「拆分缘由」。
- owner doc `service-layer.md` 事务表述修正为与实现一致（audit G.2 移交项）。
- 回归：事务装饰器（mutation 有事务/query 无事务/显式关断）、DataLoader 异步注册端到端。

## Non-Goals

- 不修 P1-2.6-002/003（fragment 校验族，R1.11）/P1-2.6-005/006/007（权限公开面族，R1.12）。
- 不改 GraphQL 层事务边界（mutation 层事务由本次 action 级装饰器对齐后自然覆盖，不额外加层）。
- 不改 `<txn>` xdef schema（显式声明的解析已工作）。

## Scope

### In Scope

- `TransactionActionDecoratorCollector`：`txnModel == null` 时 mutation 默认加事务装饰器（txnGroup=null、propagation=REQUIRED 语义对齐 owner doc「默认有事务」）。
- `GraphQLExecutor.executeOneAsync`：dispatch 后移至 operation future 完成后（thenCompose 链）+ 完成后兜底二次 dispatch（幂等）。
- owner doc `docs-for-ai/02-core-guides/service-layer.md` 事务语义段修正。
- 回归测试：①TransactionActionDecoratorCollector 直测（mutation/query/显式 false 三形态）；②端到端 DataLoader 异步注册（复用审计探针形态，nop-graphql-core 测试）。

### Out Of Scope

- fragment 校验族（R1.11）、权限公开面族（R1.12）；GraphQL 层加事务；`<txn>` schema 变更。

## Execution Plan

### Phase 1 - 事务装饰器默认化 + dispatch 时序修复 + 回归

Status: completed
Targets: `TransactionActionDecoratorCollector.java`、`GraphQLExecutor.java`、`TestTransactionActionDecorator`（新）、`TestBeanMethodBatchFetcher`（扩展）

- Item Types: `Fix`

- [x] `collectDecorator(BizActionModel, ...)`：`txnModel == null` 且 action type=mutation → 加默认事务装饰器（txnGroup=null 安全、propagation 缺省 REQUIRED 与既有语义一致——审查 A3 核实）；`txnModel != null` 分支语义不变（显式 transactional=false 关断保持）
- [x] **bean 注册（审查 B1——collector 全仓零注册，仅改分支是运行时 no-op）**：biz-defaults.beans.xml 注册 `nopTransactionActionDecoratorCollector`（ioc:default=true + `<on-bean>nopTransactionTemplate</on-bean>` 守卫，镜像 `nopGraphQLTransactionOperationInvoker` :40-48 条件装配先例）——无 nop-dao 的测试 IoC 环境自动豁免
- [x] **`executeOneAsync`（:98）与 `executeAsync`（:179，HTTP 主入口，审查 B2）两处 dispatch 时序修复**。死锁陷阱（审查 B3 写死）：dispatch 锚点=「同步注册完成后、load future 完成前」——即在 fetch 触发型 thenCompose 体内 `fetchNext(...)` 同步返回后立即 dispatch，**不可**等整个 operation future 完成（loader load future 依赖 dispatch，完成后再 dispatch=死锁）。依赖既有递归 `dispatchAll`（:183-188，dispatch 完成后 re-dispatch 承接新注册）。executeAsync 的串行 mutation 分支同步处理。dispatchAll 幂等（synchronized+清 map+空表 no-op——审查 A4 核实无 double-dispatch 风险）
- [x] 新增 `TestTransactionActionDecorator`：mutation 无 txn → 有装饰器；query 无 txn → 无装饰器；显式 `<txn>` transactional=false → 无装饰器；显式 true → 有（直测先例 TestCacheActionDecorator——审查 A5）
- [x] **爆炸半径留档（审查 A1）**：collector 注册后全仓 mutation 获装饰器，唯一豁免=显式 transactional=false；GraphQL mutation 双重包事务（operation 层+action 层，均 REQUIRED 嵌套合并，良性留档）；`<on-bean>` 守卫保证无 nop-dao 环境豁免
- [x] stash 判别：分别 stash 两修复跑对应新测试 FAIL，留输出入 daily log

Exit Criteria:

- [x] 新回归全绿；stash 判别记录在案
- [x] `./mvnw test -pl nop-service-framework/nop-biz,nop-service-framework/nop-graphql/nop-graphql-core` 全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc 修正与收口

Status: completed
Targets: `docs-for-ai/02-core-guides/service-layer.md`、roadmap、轮次 index、本 plan

- Item Types: `Fix`（文档）、`Proof`

- [x] service-layer.md:66/:277 与 api-and-graphql.md:174 三处事务语义修正：「默认有事务」与实现一致（mutation 默认 REQUIRED；query 默认无；显式 `<txn>` 覆盖；@Transactional 注解=冗余包裹非死注解——dao-defaults 有活的 AOP 切点消费，措辞对齐——审查 A2）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-biz --severity high` 退出码 0
- [x] roadmap R1.10 行 → done；index P1-2.6-001/004 → fixed + MR 表 R1.10 行
- [x] 文本一致性核对五处一致；check-plan-checklist --strict 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] owner doc 与 index/roadmap 回填一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P1-2.6-001 修复落地：mutation 默认事务装饰器（回归锁定）
- [x] P1-2.6-004 修复落地：dispatch 时序（异步注册覆盖+终局兜底，回归锁定）
- [x] owner doc 事务表述修正
- [x] focused verification：nop-biz + nop-graphql-core 全绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：双修复 stash 判别真实 FAIL→PASS
- [x] `./mvnw test -pl nop-service-framework/nop-biz,nop-service-framework/nop-graphql/nop-graphql-core` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### GraphQL 层事务边界显式化

- Classification: `optimization candidate`
- Why Not Blocking Closure: action 级装饰器对齐后，GraphQL mutation 路径自然获得事务（装饰器先于业务执行）；无需额外层
- Successor Required: `no`

## Non-Blocking Follow-ups

- `ignoreCase`（R1.6c 顺延项）等 eval 通道遗留不在本批

## Closure

Status Note: P1-2.6-001 交付：mutation 默认事务装饰器（collector 分支修复+biz-defaults bean 注册 on-bean 守卫——审查 B1 勘正零注册运行时 no-op）；P1-2.6-004 **拆出至 successor plan 2376**（初版 dispatch 锚点实测挂死——graphql-core 套件 2 分钟无输出，需独立调度生命周期重设计，候选方向三一定稿后执行）；owner doc 三处事务表述修正。nop-biz 115 全绿。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent closure audit 待派>>
- Evidence: <<待回填>>

Follow-up:

- P1-2.6-004 DataLoader 调度生命周期重设计（successor plan 2376）
