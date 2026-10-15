# C3 异步链路正确性 — nop-wf + nop-task 第 3 轮深挖（收口轮，盲审）

- 审计单元：`nop-wf`（10 jar）+ `nop-task`（10 jar），与前两轮同口径（379 files / 36,107 lines）
- 仓库：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 concurrency-audit-remediation）
- 本轮定位：收口轮。任务（1）WorkflowEngineImpl「快照读→判定→写」站点穷尽核对（C3-001/011/012 之外）；（2）C3-011 失效面扩展（isAllStepsHistory 其他调用方、流程自动终结其他路径，手动完结 API 除外）；（3）C3-012 join 族全类型（and 之外）激活/复用判定核对。
- 盲审纪律：未读取 `ai-dev/audits|plans|bugs|lessons/` 下除指令列出的本维度第 1、2 轮文件（`C3-async-chains.md`、`C3-round2.md`，按序完整读取）外的任何文件；未读取其他维度审计文件；仅审当前 live code。
- 审计日期：2026-10-15
- 发现编号：本轮无新增（备用起点 C3-016 未动用）。

---

## 1. 本轮发现

**无。** 本轮三项收口任务全部闭合，未发现达到立案标准的新问题。定向核查中产生的两处前轮发现影响面修正（不另立 ID，与第 2 轮 vote-group 修正同例）见第 2 节第 1、2 项；其余站点归入排除项。

---

## 2. 排除项/定向核查

### 2.1 [任务 1] WorkflowEngineImpl「快照读→判定→写」站点穷尽核对

对 `WorkflowEngineImpl`（全文 1949 行逐行复读）判定-推进站点建立完整清单，逐站点分类。**除已立案的 C3-001（exec-group 判定 :1264-1275）、C3-011（checkEnd :1578-1595）、C3-012（join 复用 :337-364 / 激活 :491-520）及 C3-015（endSubFlow 跨实例写 :1622-1633）外，其余站点全部落入"同行乐观锁显式裁决"或"语义自洽"两类，无新的跨行无锁判定站点。**

站点清单与分类（行号均核自当前 live code）：

| # | 站点 | 判定读 | 分类 |
|---|------|--------|------|
| 1 | `start` :181-183 状态守卫 → newSteps + saveStarted | wf 行快照 status | **同行裁决**：双并发 start 均写 `nop_wf_instance` 同一行（version 列），输家整体回滚（含其创建的起始步骤行），错误可见，重试被 `ERR_WF_ALREADY_STARTED` 拒绝。无跨行判定。 |
| 2 | `suspend` :645-646 / `resume` :671-672 状态守卫 → saveWfRecord | wf 行快照 | **同行裁决**：suspend/resume/action/kill 全部写同一 wf 行，乐观锁显式裁决，输家事务整体回滚、错误可见。 |
| 3 | `kill` :719-720 `wf.isEnded()` 守卫 → killSteps :1658-1666 + doEndWorkflow(KILLED) | wf 行 + 全部非 history 步骤快照 | **同行裁决（强化型）**：killSteps 对快照内**所有**非 history 步骤（含 WAITING）逐行写 KILLED，与任何并发 action 的步骤行写必然冲突，并发 action 整体回滚。kill 与 kill 并发时在步骤行上冲突、输家全回滚。第 2 轮排除项 10 结论维持。 |
| 4 | `turnSignalOn` :748 addSignals → checkWaitingSteps :758-762 激活判定 :764-793 | wf 行 signals + WAITING 步骤快照 | **同行裁决 + 自愈**：信号写与激活写在同一事务，跨请求在 wf 行冲突（输家含信号整体回滚、错误可见）；非 join WAITING 步骤的激活由下一次信号天然重试（第 2 轮 C3-012 字段13 结论维持）。SEQ_GROUP/finishTime 双守卫（:775-778）已阻断 signal 误激活序控步骤。 |
| 5 | `doInvokeAction` :1277-1289 doTransition（条件求值 :1471、分支路由 :1449-1484、TO_END markEnd :1532） | 事务内求值 scope + wf/步骤快照 | **事务内自洽 / 同行裁决**：split 路由与条件求值读的是本事务 own-write + 快照，单事务内确定性；跨请求冲突最终在 wf 行或步骤行由乐观锁显式裁决（TO_END 路径经 killSteps 全行覆盖，见 2.2 第 3 项）。无跨行无锁判定。 |
| 6 | `doInvokeAction` :1301-1305 SEQ activateNextSeqStep（ExecGroupSupport:146-171） | 同组 WAITING 成员快照 | **同行裁决**：第 2 轮排除项 6 结论维持并补全——并发双退出只能命中同一 ACTIVATED 实例行；addActor 产生的同组双 ACTIVATED 变体中，双触发 activateNextSeqStep 在目标 WAITING 行上冲突（一个转 ACTIVATED 后另一快照判定失效或写冲突），显式裁决。 |
| 7 | OR/VOTE 组 skipExecGroupMembers（ExecGroupSupport:135-140） | 同组兄弟步骤快照 | **同行裁决**：并发双判定 complete 后双 skip 写同一兄弟行 → 冲突 → 输家（含自身退出）整体回滚，错误可见；重试后快照更新自愈。 |
| 8 | `runStepAutoTransition` :825-864（DefaultWorkflowExecutor:46/51-59 每请求事务尾驱动） | 步骤 status 快照（:832） | **同行裁决 + 已声明边界邻接**：两并发事务对同一 ACTIVATED 步骤双跑 source 后在同一步骤行冲突，输家整体回滚——DB 状态收敛正确（步骤仅被完成一次），残余为输家 source 外部副作用多跑一次（重试可幂等化，与"步骤副作用与终态无事务原子性"同族的有界边界，不达立案标准）。 |
| 9 | `doReject` :1335-1376（rejectSteps 定位 :1348、isHistory 判定 :1380、and-join 复位 :1372-1375） | 前驱步骤 history 快照 | **语义自洽**：驳回目标在快照中非 history 时跳过重建，与该步骤并发完成的交叉结果为"R1=REJECTED + R2=ACTIVATED（并发完成方新建）"——流程状态一致、无滞留、无双跑；顺序到达亦产生等价终态。and-join 复位写本步骤行，跨请求冲突显式裁决。 |
| 10 | `doWithdraw` :1389-1408（nextSteps history 判定 :1392） | 后继步骤快照 | **同行裁决**：withdraw 对非 history 后继逐行写 WITHDRAWN，与后继的并发完成必然同一行冲突，显式裁决、错误可见。 |
| 11 | `transferToActor` :1001-1007 / `changeActor` :941-942 / `changeOwner` :954-955 | 本步骤行 | **同行裁决**：全部写本步骤行，跨请求冲突显式裁决。addActor 只增新行（无共享行写），双并发 add 语义即双成员，无竞态对象。 |
| 12 | `changeBizEntityState` :1508-1514（doTransition 入口） | biz 实体行 | **排除（业务表层语义）**：biz 实体经 ORM 会话内加载写入，其并发一致性由业务表自身锁声明决定（有 version 列则显式裁决回滚，无则 last-commit-wins）；汇聚分支通常配置同一目标 state，写冲突面窄；非 wf 引擎自身判定站点，不立案。 |
| 13 | `notifySubFlowEnd` :1137 `step.isHistory()` 守卫 | 父流程步骤快照 | **C3-015 同族变体**：并发 kill 父流程时 killSteps 写父步骤行，与本事务 setSubWfResultStatus 同行冲突 → 子流结束事务整体回滚（C3-015 同一机制：子流终态与父实例写同事务耦合），触发面更窄（kill 恰在子流结束窗口），并入 C3-015 修复面，不另立 ID。 |
| 14 | `remove` :705-706（ended wf 删除）vs forFlowEnded action | wf/步骤行 | **低于立案阈值**：仅 ended 流程的管理员删除与 forFlowEnded action 交叉，残余为孤儿 action 行或 action 报错，低频理论风险，不立案。 |
| 15 | `runAutoTransitions` :811-823 遍历 getActivatedSteps 快照 | 步骤快照集合 | 见第 8 项；集合读自 ORM 事务内集合（DaoWorkflowStore:305-307），事务内 own-write 可见，单事务推进语义正确。 |

**结论：wf 引擎内「快照读→判定→写」站点已穷尽。全部未立案站点均满足"写路径与判定所依赖的行重合 → 乐观锁显式裁决、错误可见、重试自愈"，与 C3-001/011/012 的"判定读 A 组行、写 B 行、跨行无锁可依"有本质区别。第 2 轮第 3 节"wf 引擎内不再存在未检查的跨请求判定站点"的声明经本轮逐站点复核成立。**

### 2.2 [任务 2] C3-011 失效面扩展核对——已闭合，无扩展

1. **isAllStepsHistory 调用方穷尽**（grep 全模块 + 人工核对）：生产代码**唯一调用点** = `WorkflowEngineImpl.checkEnd:1587`（`IWorkflowStore:99` 接口声明 + `AbstractWorkflowStore:178-180` 实现，无其他调用方）。C3-011 的判定站点无可扩展面。
2. **checkEnd 调度点穷尽**：生产代码恰 3 处——`doInvokeAction:1315-1317`（每个非 local action 后）、`transitionTo` TO_END 分支 `:1534-1536`、`exitStep` API `:1924-1926`（经 `WorkflowStepImpl.exitStep:260-265` 对 biz 代码开放，与前两处同族同暴露面，已被 C3-011"任意 mutation 触发 checkEnd"的表述覆盖）。第 4 处为注释掉的 killStep（:1095-1097），非 live code。
3. **流程自动终结的其他路径**：`doEndWorkflow` 生产调用方恰 2 处——`checkEnd:1592`（自动）与 `kill:732`（手动，指令排除范围外）。服务层 8 个 `@BizMutation` 入口（WorkflowServiceImpl:73-213）全部经 `DefaultWorkflowExecutor.execute` 汇入上述站点，无旁路终结入口。**C3-011 即自动终结族面的完整暴露。**
4. **结构性佐证（为什么恰好只有 C3-011 一处暴露）**：TO_END 终结路径在 `doEndWorkflow` 之前先执行 `killSteps`（`checkEnd:1583-1586` → `willEnd()` 分支），对快照内全部非 history 步骤逐行写 KILLED——任何并发 action 必然与其中本步骤行冲突而整体回滚，**天然受同行乐观锁保护**；而 `isAllStepsHistory` 自动终结路径只写 wf 实例行（`:1592`），与其他步骤行上的并发写无冲突点——这正是 C3-011 存在且唯一的原因。该不对称性确认 C3-011 修复后自动终结面即无残留。
5. **rescue 出口复核**（维持 C3-011 字段7 结论）：due/remind 扫描仅查 ACTIVATED（DaoWorkflowStore:181-194），auto-transitions 仅遍历 ACTIVATED（DefaultWorkflowExecutor 经 `getActivatedSteps` → `AbstractWorkflowStore:58-70` 过滤 history），WAITING/history 步骤不可达，全 history 未终结实例确无自动补救出口。

### 2.3 [任务 3] C3-012 join 族全类型核对——复用竞态面需扩展至 or-join（前轮影响面修正，不另立 ID）

`WfJoinType = {and, or}`（枚举全量核实）。joinType 在引擎中的全部 6 处消费点（grep 穷尽：:337、:402、:420、:766、:1372、:1931）逐一核对：

1. **复用判定（:337-364，`getJoinType() != null` 门控）——and 与 or 共用同一站点**。`getNextJoinStepRecord`（唯一生产调用点 :343）按 stepName+joinGroup 匹配非 history 实例（AbstractWorkflowStore:183-197，先滤 `>= HISTORY_BOUND`）。**or-join 的并发到达同样互相看不到对方未提交的新实例 → 双双走 :370 新建 → 两个同时活跃的 or-join 实例 → 下游双跑**。此形态直接违反 wf.xdef:261-263 的已声明契约："joinType=or 时只要有一个步骤到达就激活，**后续步骤再到达时如果 join 步骤还处于活动状态，则不会产生新的 join 步骤**"——复用机制本身就是该契约的实现，快照盲区使其在并发到达时失效（F2：下游业务副作用重复执行）。**修正：C3-012 字段11 "需要 joinType=\"and\" 配置"的限定不完整，修复面必须覆盖 or-join**（同一站点、同一根因、同一修复——wfId 条带锁或 join 实例创建幂等化均同时覆盖两类型），按第 2 轮 vote-group 修正先例不另立 ID。
2. **初始状态（:399-410）**：仅 `joinType == and` 强制 WAITING（:402-403）；or-join 无 waitSignals 时（`isAllSignalOn(empty)=true`）直接 ACTIVATED——**or-join 无"永久 WAITING 不激活"失效模式**，C3-012 的滞留后果仍为 and 独有。
3. **激活判定（checkWaitingJoinStep :491-520）**：调度点 3 处（:358、:421、:767）均已covered；对 or-join，`getJoinWaitSteps`（:1930-1948）非 and 返回空集 → `allWaitFinished=true` → 立即激活，退化为 no-op/直通，无新增竞态后果。
4. **doReject（:1372-1375）**：仅 and-join 驳回后复位 WAITING（等待重新激活）；or-join 驳回即 history，语义自洽。
5. **WfModelAnalyzer:215**：模型静态分析，非运行时站点。

### 2.4 其他排除（本轮复读中核实的邻近形态）

1. **`WorkflowImpl.reload()` :65-72**：重载清空 commandQueue/steps，仅实例内生效（每请求独享实例），无跨线程共享面（C3-010 结论维持）。
2. **`willEnd`/`markEnd`**（NopWfInstance:82-88）：非持久化瞬态标志，随 per-request 记录对象生灭，仅本事务 checkEnd 消费，无跨请求可见性假设。
3. **`handleError` 对 auto-transition 异常的传播**（:860-863 + :1694-1695）：任意步骤的 auto-transition 失败会回滚整个 action 事务——单事务内顺序错误传播，非竞态，不属 C3 族面（其原子性耦合取舍与 C3-015 同为设计面，不立案）。
4. **`transitionToStep` 忽略 `newSteps` 返回值**（:1573）：`ignoreNoAssign=true` 时静默不建下游步骤为 xdef 已声明语义，非并发问题。
5. **`getNextJoinStepRecord` 签名中的 actor 参数在默认实现中未消费**（AbstractWorkflowStore:183-197 仅按 stepName+joinGroup 匹配）：非并发问题，不影响 C3-012 分析成立性（匹配集更大只会使复用更易命中），备查。

---

## 3. 终止声明

**未发现新的高价值问题。C3 维度深挖终止。**

本轮三项收口任务的闭合证据：

1. **站点穷尽**（2.1）：WorkflowEngineImpl 判定-推进站点 15 项全清单核对完毕，未立案站点全部为"同行乐观锁显式裁决"或"语义自洽"，与 C3-001/011/012 的跨行无锁判定形态有本质区别；wf 引擎内不存在第 4 个跨行快照判定站点。
2. **C3-011 失效面**（2.2）：isAllStepsHistory 唯一调用点、checkEnd 恰 3 个调度点、doEndWorkflow 恰 2 个生产调用方，自动终结面已完整收敛于 C3-011 单点，且结构性不对称分析确认修复该点后无残留。
3. **join 族**（2.3）：WfJoinType 全类型核对完毕，or-join 复用竞态并入 C3-012 修复面（含 xdef 契约违反佐证），激活滞留面确认为 and 独有。

前两轮立案清单（C3-001 P1、C3-002 P1、C3-011 P1、C3-012 P1、C3-013 P2、C3-003~006 P2、C3-007~010/014/015 P3）在本轮复核中无一被推翻；影响面修正两处（C3-012 字段11 须覆盖 or-join；第 2 轮 vote-group 修正维持）均已记录于第 2 节。**C3 维度对 nop-wf/nop-task 的深挖自本报告起终止，不建议再发起第 4 轮。**

## 4. 覆盖声明

本轮人工逐行核读（非仅 grep）的代码面：

- **nop-wf-core**：`WorkflowEngineImpl` 全文复读（重点：start/suspend/resume/kill/turnSignalOn/off/runAutoTransitions/runStepAutoTransition/invokeAction/doInvokeAction/doReject/doRejectStep/doWithdraw/transferToActor/addActor/moveStepToWaiting/notifySubFlowEnd/doTransition/transitionTo/transitionToStep/checkEnd/doEndWorkflow/endSubFlow/killSteps/checkWaitingStep/checkWaitingJoinStep/getJoinWaitSteps/newStepForActor/newSteps/doExitStep/handleError 全部判定分支）；`WorkflowImpl` 全文（含 reload/delayExecute/executeNow/getWaitingSteps/getActivatedSteps/isAllSignalOn）；`WorkflowStepImpl` 全文（状态谓词委托链、getStepsInSameExecGroup、executeNow 门面）；`WfRuntime` 全文（willEnd/markEnd/saveWfRecord/triggerEvent）；`ExecGroupSupport` 全文复读；`AbstractWorkflowStore` 全文（isAllStepsHistory/getNextJoinStepRecord/getJoinWaitStepRecords/getStepRecords/addSignals/updateBizEntityState）；`IWorkflowStepRecord`/`IWorkflowRecord` 接口（状态谓词语义、HISTORY_BOUND）；`WorkflowRecordBean`（willEnd 瞬态）。
- **nop-wf-dao**：`DaoWorkflowStore` 全文（getSteps=ORM 事务集合、findDueActivatedSteps/findRemindActivatedSteps 的 ACTIVATED 过滤、saveWfRecord/logError REQUIRES_NEW）；`NopWfInstance`（willEnd/markEnd 字段）。
- **nop-wf-core 服务/执行面**：`WorkflowServiceImpl`（8 个 @BizMutation 入口清单）、`DefaultWorkflowExecutor` 全文（runAutoTransitionsWithLimit）。
- **模型声明**：`WfJoinType` 枚举全量；`wf.xdef:261-265`（joinType 契约原文）；`NopWfCoreConstants`（WF_STEP_STATUS_HISTORY_BOUND）。
- **grep 定向扫描**（均排除 target/ 与生成物）：`isAllStepsHistory`（1 个生产调用点）、`checkEnd(`（3 个 live 调度点）、`doEndWorkflow(`（2 个生产调用方）、`getNextJoinStepRecord`（1 个生产调用点）、`checkWaitingJoinStep`（3 个调度点）、`getJoinType()`（6 个运行时消费点 + 1 个分析器）、`exitStep(/transitTo(`（服务层无直接调用方，经 IWorkflowStep 门面对 biz 开放）。
- **前序输入**：第 1 轮 `C3-async-chains.md`、第 2 轮 `C3-round2.md` 按序完整读取；其 5 项已声明契约与 10 项排除项逐条对遇（除 2.4 第 3 项为新增邻近形态外，其余结论全部维持）。
