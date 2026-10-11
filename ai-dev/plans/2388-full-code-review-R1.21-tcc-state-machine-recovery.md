# 2388 full-code-review R1.21 — nop-tcc 状态机恢复族（P1-3.8-004/005）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Completed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.21；findings `P1-3.8-004`（原 SM-07）+ `P1-3.8-005`（原 SM-08），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.8-tcc-retry-file-deep-audit.md`
> Related: R1.20（同文件族 xbiz auth 收敛已落地）；R1.23（已提前完成，runtime 门禁恢复——本 plan 的 -am gate 链可达）
> Owner Doc: `docs-for-ai/03-modules/nop-tcc.md`

## Purpose

收口 TCC 状态机恢复族两条 P1：①全局 CANCELLING/CANCEL_FAILED 事务崩溃恢复死锁——endAsync 缺 cancel 半区对称路由，恢复轮询耗尽后被扫描器永久遗弃（CONFIRMING 镜像已修而 CANCELLING 未修）；②参与方 inbound TccContext 桥接缺失——唯一装配点是死代码，多跳链路嵌套 TCC 调用静默 fork 成独立事务。

## Current Baseline

- **P1-3.8-004**：`TccTransaction.endAsync:58-77` 只对 CONFIRMING/CONFIRM_FAILED 显式续跑 confirm（:63-66，plan 347 修复）；全局 CANCELLING/CANCEL_FAILED 落入分支证据判定——恢复调用（`endAsync(true,null,null)`，isFailed=false）+ 分支无回滚证据（全 TRY_SUCCESS 或部分 CANCEL_SUCCESS+其余 TRY_SUCCESS，`isRollbackOnly=TccStatus.isInTransaction && !isAllowConfirm` 恒 false）→ 路由 doConfirmAsync → :117-119 守卫 `CANCELLING` 静默 no-op → 每轮被扫描器重拾 bump retryTimes（TccRecordStore:313-316）→ `lt(retryTimes,maxRetryCount)`（:303，默认 100 轮）后永久排除。doCancelAsync 守卫（:95-100）不拦截 CANCELLING/CANCEL_FAILED（isCancelled 四态不含 CANCELLING），CAS 同值重入可行（复核 :7 实证自洽）。镜像先例：testConfirmRecoveryAfterCrash（TestTccEngine.java:280-315）+ javadoc "事务永久卡死"。锚定测试 testCancellingStateRejectsConfirm（TestTccTransactionEndStates.java:148-160）用存活调用方场景（endAsync(false, ok)）断言 no-op——无意中把「恢复路径也不得发出任何 RPC」钉死，须随修复改写。
- **P1-3.8-005**：出站传播已存在（TccRunner.runBranchTryAsync:48-53 写 txn 头）；入站桥接缺失——`TccRpcServiceInterceptor.interceptAsync:38` 只读 `TccContext.getCurrent()`（thread-local），从不读入站请求头；`TccContext.buildFromRequest(ApiRequest)` 已存在（网关拦截器 TccGatewayInterceptor invoke 是唯一活装配点）；`TccContextInvoker`（nop-graphql-core，IAsyncFunctionInvoker 装饰器）实现完整但全仓零引用=死代码。后果：服务 B 收到携带 txnId 头的请求后嵌套 TCC 调用 txnId=null → 静默新建独立事务。`IRpcServiceInvocation.isInbound()`（服务端调用=true）为桥接的自然作用域限定。
- **测试基建**：nop-tcc-core 有 FakeTccRecordStore/FakeBranchRecord + FakeInvoker 测试族（TestTccTransactionEndStates）；nop-tcc-dao 测试容器（app-test.beans.xml MockRpcServiceInvoker）+ testConfirmRecoveryAfterCrash 真库恢复测试先例；TccMethodMeta 为 4 参构造 @DataBean 可直构。

## Goals

- endAsync 增加与 CONFIRMING/CONFIRM_FAILED 对称的显式路由：`CANCELLING || CANCEL_FAILED → doCancelAsync(timeout, branchTxns)`（TCC 语义：cancel 开始后不允许 confirm，存活调用方与恢复路径同路由）。
- 参与方 inbound TccContext 框架级桥接：TccRpcServiceInterceptor 在 inbound 且 thread-local 无上下文时从请求头 build+setCurrent+完成时恢复（网关拦截器先例形态）。
- testCancellingStateRejectsConfirm 断言范围同步（rejects-confirm 语义保持；续跑 cancel）；补「全局 CANCELLING+全 TRY_SUCCESS 分支+checkExpiredTransactions→CANCEL_SUCCESS」真库回归；补拦截器桥接判别测试。
- nop-tcc-core/nop-tcc-dao 测试全绿；owner doc 边界声明（含 TccContextInvoker 定位）。

## Non-Goals

- 恢复调度器（SM-01 无生产接线）编排改造；P2-3.8 台账行；TccContextInvoker 死类的删除/装配（保留为可选扩展点，owner doc 声明其与拦截器桥接的分工）；retryTimes 上限语义变更（死锁修复后 no-op 循环消失，100 轮上限回归为一般重试边界）。

## Scope

### In Scope

- `TccTransaction.endAsync` cancel 半区对称路由 + 恢复续跑日志观测点
- `TccRpcServiceInterceptor` inbound TccContext 桥接（buildFromRequest→setCurrent→thenOnContext→whenComplete 恢复，先例：TccGatewayInterceptor/TccContextInvoker）
- 测试：TestTccTransactionEndStates.cancelling 用例改写；TestTccEngine 新增 testCancelRecoveryAfterCrash；TccRpcServiceInterceptor 桥接测试（stub metaLoader/engine/invocation）
- owner doc：nop-tcc.md 恢复语义节 + 多跳 TCC 上下文传播节

### Out Of Scope

- Non-Goals 全部；nop-retry 面（R1.20 已覆盖其 auth 面；本 plan 的"retry"仅指 roadmap 行文中的恢复重试机制）

## Execution Plan

### Phase 1 - 双修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] TccTransaction.endAsync：CONFIRMING 路由块后增加 `CANCELLING || CANCEL_FAILED → doCancelAsync(timeout, branchTxns)` 对称路由 + 注释（cancel 开始后不允许 confirm；恢复路径续跑）；doCancelAsync 内恢复续跑时打可观测日志（遗弃防线=死锁消除后 no-op 循环不再发生）
- [x] TestTccTransactionEndStates.testCancellingStateRejectsConfirm 改写：CANCELLING+TRY_SUCCESS+endAsync(false, ok) → 断言 cancel 方法被调用、confirm 方法未被调用、全局状态收敛 CANCEL_SUCCESS（rejects-confirm 语义保持）；**类 javadoc「零 RPC」表述随改写微调（审查问题 4）**
- [x] TestTccEngine 新增 testCancelRecoveryAfterCrash（镜像 testConfirmRecoveryAfterCrash：全局 CANCELLING+分支 TRY_SUCCESS 真库布景 → checkExpiredTransactions〔恢复入口固定 timeout=true〕→ 断言：全局 CANCEL_SUCCESS（TIMEOUT_SUCCESS 经 aggregateCancelBranchStatus 折叠）+ **分支 TIMEOUT_SUCCESS**（finishCancelAsync 走 BEFORE_TIMEOUT 分支，TccBranchTransaction:153-156）+ 分支 retryTimes 0→1（BEFORE_TIMEOUT 时 store bump，TccRecordStore:232-236）+ 全局 retryTimes 0→1（fetch bump :313-316）——审查问题 1 勘正）
- [x] TccRpcServiceInterceptor.interceptAsync：inbound 且 `TccContext.getCurrent()==null` 时 `TccContext.buildFromRequest(inv.getRequest())` 桥接——**setCurrent 无条件（镜像网关裁定，审查问题 2）**：请求头描述入站事务，组检查只决定本方法是否 join（mismatch 时桥接上下文保留在 thread 供嵌套调用 join 其同组事务），`thenOnContext` 包裹 + whenComplete 恢复 old/remove；txnGroup 不匹配守卫只影响本地引擎路由
- [x] 新增 TccRpcServiceInterceptor 桥接测试：stub ITccServiceMetaLoader（返回 TccMethodMeta）+ ITccEngine（捕获 runInTransactionAsync 入参与回调内 TccContext.getCurrent() 快照；11 抽象方法空实现）+ IRpcServiceInvocation stub（inbound=true，ApiRequest 携带 txn 头）→ 断言：①引擎回调内 TccContext 可见（txnId/txnGroup/branchId）且完成后 thread-local 恢复；②无头请求不建上下文；③outbound 不桥接；④inbound+已有 thread-local 时**请求头不覆盖**（thread-local 赢——与网关相反的优先级钉死，审查问题 3）；⑤桥接成功+isInitiator=false → 参与方不 startBranch、proceedAsync 恰一次（no-fork 正面锚定）
- [x] **判别验证（双腿 stash）**：①stash TccTransaction 路由改动→改写后的 cancelling 用例+testCancelRecoveryAfterCrash FAIL；②stash 拦截器桥接改动→桥接测试 FAIL（上下文 null）——各腿恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 双修全落地（对称路由/inbound 桥接）
- [x] 判别测试双腿绿 + 既有测试零回归
- [x] nop-tcc-core/nop-tcc-dao（及 nop-tcc-service 回归）测试全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-tcc.md`

- Item Types: `Fix`

- [x] nop-tcc.md：恢复语义节（CANCELLING/CANCEL_FAILED 崩溃恢复续跑 cancel，对称于 CONFIRMING 镜像）+ 多跳上下文传播节（inbound 框架级桥接机制、TccContextInvoker 可选装配点定位、网关拦截器分工）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.21 翻 done；index P1-3.8-004/005 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-3.8-004/005）
- [x] 行为/契约结果已达成（判别双腿 + 零回归）
- [x] 必要 focused verification 已完成（nop-tcc 三模块测试全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-tcc/nop-tcc-core,nop-tcc/nop-tcc-dao,nop-tcc/nop-tcc-service` 全绿（执行轮 57/33/2 + closure audit 独立重跑 exit 0 双重验证）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- TccContextInvoker 死类处置（删除或装配为 GraphQL seam 装饰器）——随 GraphQL 引擎演进裁定，非本 plan 阻塞项
- SM-01 恢复调度器生产接线（checkExpiredTransactions 无生产驱动者——平台级编排决策）
- TccBranchTransaction.finishCancelAsync:158 在 (ex==null && response==null) 时 NPE（现有 invoker 不可达——审查观察项）

## Closure

Status Note: 两条 P1 修复在 live 代码逐行落地且语义正确：对称路由由唯一恢复入口（checkExpiredAsync:347→endAsync(true,null,null)）可达；inbound 桥接在活 bean（nopRpcServiceInterceptor_tcc）主路径。判别双腿精确（route stash：dao 恢复用例+core 改写用例双 FAIL 且复现 misroute 症状；bridge stash：唯一可见性用例 FAIL、其余 4 钉定点不受扰）。三模块 57/33/2 全绿经审计独立重跑复现（exit 0）。审计 Minor 三项已处理：全局 retryTimes 断言补齐（Minor-2）、owner doc retryTimes 措辞精确化（Minor-3）、daily log 标题去重（Minor-4）。

Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）agent_c50923ef-2f16-4e81-881e-ae1cb0dd465a
- Evidence:
  - P1-3.8-004 PASS：TccTransaction.java:71-74 对称路由+resume-cancel 日志；TccStatus 四/五态亲证 doCancelAsync 守卫对 CANCELLING/CANCEL_FAILED 真放行；CAS 同值重入由真库测试实证
  - P1-3.8-005 PASS：TccRpcServiceInterceptor.java:40-53 setCurrent 无条件（组检查不参与 setCurrent 判定）；组匹配仅影响本地路由不回撤 thread-local；活装配点 tcc-core-defaults.beans.xml:14；removeCurrent 按 value 移除不误删
  - 测试证据：TestTccTransactionEndStates 6/6（改写断言 :163/:165/:167）；TestTccEngine 24/24（含 testCancelRecoveryAfterCrash 三断言+全局 retryTimes 断言补齐）；TestTccRpcServiceInterceptorBridge 5/5 五钉定点一一对应；模块全量 core 57/dao 33/service 2 独立重跑 exit 0（_tmp/audit-tcc-gate.log）
  - 判别证据：_tmp/disc-tcc-route.log（route stash 后 dao 1 FAIL "expected 12 but was 7"+core 1 FAIL 且日志现误导性 nop.tcc.confirm 症状）；_tmp/disc-tcc-bridge.log（bridge stash 后唯一可见性用例 :200 FAIL，其余 4 不受扰）
  - Anti-Hollow：路由可达性（唯一恢复入口+stash 因果实证）；桥接在活 bean 主路径+完成恢复被断言；零 @Disabled/静默跳过、stub 显式 UnsupportedOperationException
  - 收口完整性：roadmap R1.21 done；index :136-137 fixed；daily log R1.21 条目；owner doc nop-tcc.md 两节与 live 逐点一致（折叠表述/thread-local 赢/TccContextInvoker 定位/resume-cancel 锚点）
  - 工具门禁：doc-links --strict 0；checklist --strict 0（completed 态复跑）；hollow 扫描（nop-tcc/core/dao）退出码均 0
  - Follow-ups 诚实性：TccContextInvoker 零引用属实保留为可选点；SM-01 无生产驱动者如实申报；finishCancelAsync NPE 现有 invoker 不可达性合理

Follow-up:

- TccContextInvoker 死类处置（随 GraphQL 引擎演进裁定）；SM-01 恢复调度器生产接线（平台级编排决策）；TccBranchTransaction.finishCancelAsync:158 NPE 观察项（现有 invoker 不可达）
