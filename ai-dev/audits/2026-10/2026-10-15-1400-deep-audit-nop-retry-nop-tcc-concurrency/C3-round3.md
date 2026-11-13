# C3 异步链路正确性审计 — nop-retry + nop-tcc（第 3 轮收口）

- 审计单元：`nop-retry`（8 jar）+ `nop-tcc`（9 jar）
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15；审计维度：C3 异步链路正确性（第 3 轮收口轮，盲审）
- 输入：第 1 轮 C3-async-chains.md（C3-001~011）+ 第 2 轮 C3-round2.md（C3-012~015）（唯一豁免读取文件）+ live code + owner doc（nop-retry.md / nop-tcc.md）+ grep 基线（_tmp/cra-baseline-nop-retry-nop-tcc.txt）；未读取 ai-dev/ 下其他任何文件。
- 本轮收口范围：TCC 聚合矩阵剩余单元格闭合（分支部分成功/失败聚合语义、全局成功但分支写失败残留、确认无第 4 个未立案错误终态/悬挂单元格）+ 状态机两步 CAS 全部调用点穷尽（F3 矩阵）。

---

## 1. 本轮发现

### [nop-retry-nop-tcc-C3-016]
- **编号**：nop-retry-nop-tcc-C3-016
- **严重度**：P1
- **缺陷族**：F2（半个成功）+ F9（异常归因错误）
- **标题**：分支级 CAS（begin/finish 任一跳）以异常完成时内存状态不回写，聚合把「写失败分支」的中间态折叠为全局 SUCCESS——单链一次 DB 瞬时故障即产出「全局正常完结 + 分支补偿/确认未生效」的错误终态（聚合矩阵第 4 个、亦是最后一个未立案单元格）
- **位置**：nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:274-281（updateTccBranchStatusAsync：0 行仅告警后正常完成、异常完成直接向上传播，两种失败均不回写内存 status——`record.setStatus` 仅 1 行命中才执行）；nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:105-121（begin 失败→thenCompose 整体跳过→补偿 RPC 不下发；finish 失败→手工 future 以 ex2 异常完成）；nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccRunner.java:178-196、198-217（两个聚合函数的失败态白名单不含 TRY_SUCCESS/CANCELLING/BEFORE_TIMEOUT）；nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java:113-116、134-137（whenCompleteAsync 回调 err!=null 照常聚合并写终态，FutureHelper.java:354-370）
- **证据片段**：
  ```java
  // TccRecordStore.updateTccBranchStatusAsync——只有 1 行命中才回写内存
  long updated = branchDao().updateByQuery(query, props);
  if (updated == 0) { LOG.warn("nop.tcc.skip-branch-status-update..."); return null; }
  record.setStatus(status.getCode());        // 异常完成时根本执行不到这里
  // TccRunner.runBranchCancelAsync——begin 跳失败则 RPC 不下发，链以异常完成
  return branchTxn.beginCancelAsync(timeout).thenCompose(arg -> {
      ...
      serviceInvoker.invokeAsync(branchRecord.getServiceName(), branchRecord.getCancelMethod(), ...)
  // TccTransaction.doCancelAsync——err!=null 照常聚合，TRY_SUCCESS/CANCELLING 被折叠
  return FutureHelper.whenCompleteAsync(future, (response, err) -> {
      TccStatus status = TccRunner.aggregateCancelBranchStatus(branchTxns);
      return getRepository().updateTccStatusAsync(tccRecord, status, err);
  });
  ```
- **触发时序**（四个子变体，同一根因）：(a) confirm 方向 begin 写失败：分支 TRY_SUCCESS 在 beginConfirmAsync 白名单内（同步守卫通过，非 C3-010 的越界状态同步抛出路径），REQUIRES_NEW 写 CONFIRMING 遭遇瞬时 DB 故障（FutureHelper.futureCall 对同步异常 reject，FutureHelper.java:122-129）→ thenCompose 跳过，confirm RPC 从未下发 → 分支 stage 异常完成 → waitAll（allOf）失败 → doConfirmAsync 回调（err≠null）聚合：该分支内存保持 TRY_SUCCESS（非 CONFIRM_FAILED、非 cancelled）→ 其余分支正常 → 折叠 CONFIRM_SUCCESS → 全局 CAS CONFIRMING→CONFIRM_SUCCESS（DB 已恢复则 1 行命中，endTime 落库）→ finished。(b) cancel 方向 begin 写失败：对称，cancel RPC 从未下发 → 折叠 CANCEL_SUCCESS → try 预留资源永不释放。(c) finish 写失败且 RPC 异常（ex≠null、ex2≠null）：finishCancelAsync 对 ex 的既定意图是「结果未知按 CANCEL_FAILED 记录以便恢复循环重试」（TccBranchTransaction.java:146-148 注释自声明）——该记录写自身失败时内存保持 CANCELLING/BEFORE_TIMEOUT（均非失败态白名单成员）→ 折叠 CANCEL_SUCCESS → 恢复重试被终态掩埋；参与方若未实际执行 cancel 则预留悬挂。(d) finish 写失败且 RPC 成功：无害簿记残留（分支行永久停留 CANCELLING，全局 CANCEL_SUCCESS），且 removeCompletedRecords 按已完结父记录级联删除分支行（TccRecordStore.java:341-354），残留证据保留期后物理消失。危害 sticking 均要求「分支写失败 + 毫秒级后全局终态写成功」的双写窗口：若终态写同样失败，全局停留 CANCELLING/CONFIRMING（未 finished），恢复扫描下周期重驱动、分支按 DB 行（仍 TRY_SUCCESS/CANCELLING）续跑 → 自愈——与 C3-012 的双写窗口形状完全同型。
- **影响**：与 C3-012 同向但落点在分支级——C3-012 是「分支整体未被驱动」（全局首跳失败），本发现是「分支被驱动但其状态机推进被簿记失败静默回滚」（分支级 begin/finish 失败），两者合并覆盖分支驱动全路径的异常完成面。终局均为 finished 全局 + 未生效分支：try 预留未释放（b/c）或未确认（a，参与方超时自动回滚则业务数据缺失而协调器报成功）；恢复扫描按全局 status≥11 永久排除（TccRecordStore.java:301），无任何机制再驱动该分支。
- **与已声明契约的对照**：owner doc 崩溃恢复对称续跑契约以「分支状态机真实落库」为前提；finishCancelAsync 注释自声明的「结果未知必须按失败记录以便重试」意图被同函数的写失败路径静默击穿——语义缺口，缺陷而非声明边界。
- **误报校准说明**：单链可达（无需双链对撞与 RPC 结果分歧——与 C3-002 的关键区别）；触发窗口与 C3-012/C3-006 同级（两次相邻 REQUIRES_NEW 写之间 DB 恢复）；后果方向性最严重（未补偿/未确认→报成功）；按 campaign 口径「罕见但可具体化 + 半个成功」判 P1。
- **修复方向建议**：聚合函数引入「本链已驱动」概念——begin/finish 写失败（0 行或异常）时将该分支以 failedStage 汇入 waitAll，聚合遇「驱动未确认」分支返回 CANCEL_FAILED/CONFIRM_FAILED 而非折叠 SUCCESS；updateTccBranchStatusAsync 的失败信号显式传播给调用方（对齐 C3-001/C3-012 的修复方向：CAS 未生效即短路）。
- **置信度**：高（机制链每一环均经实现核对：futureCall 同步异常 reject → thenCompose 跳过 → allOf 失败透传 → whenCompleteAsync err 照常回调 → 聚合白名单逐状态核对）。
- **交叉引用**：C3-002（同单元格的 0 行变体，需双链对撞）、C3-010（begin 同步守卫抛异常变体）、C3-012（全局首跳异常变体——本发现补齐分支级异常完成单元格，聚合矩阵至此闭合）、C3-008（恢复重驱动既是自愈面也是对撞源）。
- **复核状态**：保留 P1；新发现；复核子代理五环独立复验全部成立——(1) 分支 CAS 双失败模式均不回写内存（TccRecordStore.java:274-281 + futureCall 同步异常 reject FutureHelper.java:122-129）；(2) begin 失败 thenCompose 跳过 RPC 零下发 / finish 失败经 ex2 异常完成手工 future（TccRunner.java:79-121）；(3) waitAll=allOf 失败透传 + whenCompleteAsync err 照常回调（FutureHelper.java:354-370/:388-413）；(4) 两聚合函数失败态白名单均不含 TRY_SUCCESS/CANCELLING/BEFORE_TIMEOUT（TccRunner.java:178-217）；(5) 全局终态 CAS from 自洽（首跳成功即回写 :194）+ 恢复谓词 status<11 永久排除（TccRecordStore.java:301）。单链可达 + 双写窗口与 C3-006/012 同级 + 后果方向最严重，P1 维持；四个子变体 (a)-(d) 走查与 round4 §2.1 一致。

### [nop-retry-nop-tcc-C3-017]
- **编号**：nop-retry-nop-tcc-C3-017
- **严重度**：P3
- **缺陷族**：F3（状态守卫读链内内存快照 + 无条件插入）+ F1（一次性分支清单快照）
- **标题**：分支准入守卫读链内内存、分支插入无父状态条件、endAsync 分支清单仅入口加载一次——非组合（fire-and-forget）分支用法下插入晚于清单加载即成孤儿分支：try 副作用已发生且永无补偿，全局终态掩埋，保留期后证据被级联删除
- **位置**：nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:295-299（runBranchTransactionAsync 守卫读 txn.getTccStatus()=链内内存快照，非 DB 权威值）；nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:206-218（saveBranchRecordAsync 无条件 saveEntityDirectly 插入 TRYING，无父状态条件）；nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccTransaction.java:59（endAsync 仅在入口 loadBranchTransactionsAsync 加载一次分支清单）；nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:341-354（removeCompletedRecords 按已完结父记录 txnId 级联删除分支行）
- **证据片段**：
  ```java
  // TccEngine.runBranchTransactionAsync——守卫读链内内存：并发 endAsync 已把 DB 写成
  // CONFIRMING/终态时，本链内存仍为 TRYING，守卫照常放行
  if (txn.getTccStatus() != TccStatus.TRYING)
      return FutureHelper.reject(new NopException(ERR_TCC_TRANSACTION_NOT_ALLOW_START_BRANCH)...);
  // TccRecordStore.saveBranchRecordAsync——插入不校验父事务当前 DB 状态
  record.setStatus(initStatus.getCode());
  branchDao().saveEntityDirectly(record);
  ```
- **触发时序**：关键边界（本轮代码路径核对结论）——完全组合的用法（task future 包含分支 future）下本单元格不可达：驱动链自身的 endAsync 在 task 完成之后才加载分支清单，自身创建的分支必然已插入并被拾取；双链对撞下亦自愈（存活调用方自己的 endAsync 重载分支并以分支级 CAS 补驱动——全局 CAS 0 行不阻断分支驱动，见 C3-001 机制）。可达前提是「分支 future 未被组合进 task」：task 内异步发起 runBranchTransactionAsync 后立即返回 → endAsync 的 loadBranchTransactionsAsync 先于该分支 beginTryAsync 的 REQUIRES_NEW 插入提交执行 → 分支清单不含它 → 全局写终态 finished → 分支稍后插入 TRYING 并执行 try RPC → 无任何驱动方再读该分支行（恢复扫描 fetchExpiredRecords 只查 nop_tcc_record 主表，分支仅经 endAsync 的清单加载被驱动）→ 预留资源永久悬挂；cleanCompletedTransactions 保留期后按已完结父 txnId 级联删除该分支行（TccRecordStore.java:350-352），证据消失。变体：插入恰在加载前提交 → 分支以 TRYING 参与 isRollbackOnly 判定走 cancel 路径 → cancel RPC 与在途 try RPC 在参与方竞态（参与方侧行为在本单元之外）。
- **影响**：孤儿分支（try 副作用已发生、永不 confirm/cancel），且是可观测性最低的一类（主表已 finished、分支行保留期后被物理删除）。需要「非组合用法 + 插入晚于加载」两个前提，触发频率低。
- **与已声明契约的对照**：owner doc（nop-tcc.md）未声明「分支 future 必须组合进 task 返回的 future」，运行时亦无强制或告警；API 签名约定（返回 CompletionStage）隐含组合预期但零防御——未定义用法下的实现缺口，非已声明边界。
- **误报校准说明**：触发依赖使用方式越出隐含契约 + 毫秒级窗口；组合用法下经代码路径核对确认自愈（显式写出以防过度申报）；按判级规则（低频路径 + 可具体化）P3，与 C3-015（死信重放幂等盲区）同级。
- **修复方向建议**：分支插入改条件插入（按父记录 status=TRYING 的 CAS，0 行拒绝并告警——同时收窄 C3-007 的孤儿面）；或在 owner doc 显式声明组合契约，并在 endAsync 写终态前对未 finish 分支行做二次查询校验告警。
- **置信度**：高（代码事实直接）/低-中（实际触发频率评估，依赖使用方式）。
- **交叉引用**：C3-007（孤儿分支的另一机制：registry LIFO 错位，其修复不覆盖本单元格）、C3-001（双链对撞下组合用法的自愈面）、C3-016（同族「终态掩埋分支」）。
- **复核状态**：保留 P3；新发现；守卫读链内内存快照（TccEngine.java:295）+ 分支插入无父状态条件（TccRecordStore.java:208-218）+ endAsync 分支清单仅入口加载一次（TccTransaction.java:59）+ 已完结父级联删分支（TccRecordStore.java:341-354）核实成立；非组合用法前提与组合用法自愈边界经路径走查确认，维持 P3。

---

## 2. 排除项/定向核查

以下为本轮按收口指令逐项定向核查后确认干净、或已归并/已立案的条目：

1. **TCC 聚合矩阵全单元格闭合表**（首跳结果 × 分支驱动 × 聚合 × 终态写，两方向合并列示）：

| # | 单元格 | 处置 |
|---|---|---|
| 1 | 全局首跳 CAS **0 行（值正常完成）**→ 陈旧 from 继续驱动分支 → 双向重复补偿 RPC | C3-001（第 1 轮） |
| 2 | 全局首跳 CAS **异常完成**→ 分支零驱动 → 折叠 SUCCESS → 陈旧 from 直写终态 | C3-012（第 2 轮） |
| 3 | 分支 finish CAS **0 行**（双链结果分歧）→ 内存不回写 → 聚合读到旧值 → 误报终态 | C3-002（第 1 轮） |
| 4 | 聚合 **throw**（confirm 方向遇 cancelled 分支）→ 终态写跳过 → 卡 CONFIRMING | C3-010 + 第 2 轮失效面扩展（本轮复核归并成立：FutureHelper.java:354-370 的 handler 内异常被 catch（365-368）→ promise 异常完成 → `updateTccStatusAsync` 整体跳过） |
| 5 | 分支 begin/finish CAS **异常完成（单链）**→ 内存不回写 → 折叠 SUCCESS → 错误终态 | **本轮立案 C3-016（矩阵最后一个未立案单元格）** |
| 6 | 分支**部分成功部分失败**的聚合语义 | 干净（见条目 2） |
| 7 | **全局成功但分支写失败的残留** | 即单元格 3+5 的终局形态（C3-002/C3-016）；残留证据保留期后级联删除已计入 C3-016 影响面 |
| 8 | 全局**终态写自身** 0 行/异常 | 干净（见条目 3） |
| 9 | 聚合遇 **null/未知状态**分支 | 第 2 轮 §2.2 已登记不判级（脏数据前提的接受残留），维持 |

   结论：矩阵无第 5 个未立案的错误终态/悬挂单元格。

2. **分支部分成功/部分失败的聚合语义（单元格 6）——逐状态核对，干净**：cancel 方向失败态优先级 BIZ_CANCEL_FAILED > CANCEL_FAILED > TIMEOUT_FAILED→CANCEL_FAILED（TccRunner.java:186-193），任一失败态即阻断 SUCCESS 折叠；全局 CANCEL_FAILED 未 finished → 恢复扫描重拾 → doCancelAsync 续跑（终态分支被 isBranchCancellable 跳过、失败分支按 DB 状态续驱动）——TCC「cancel 失败必须续跑」语义成立。confirm 方向 CONFIRM_FAILED 阻断、CONFIRM_SUCCESS 跳过重入（confirmAllAsync:145-146 + beginConfirmAsync 白名单含 CONFIRMING/CONFIRM_FAILED）——「confirm 开始后必须到成功」语义成立。BIZ_CANCEL_FAILED 为不可重试终态系已声明边界（TccStatus.java:59-62 注释自声明）。被预筛跳过的 cancelled/confirmed/KILLED 分支折叠入 SUCCESS 为正确语义（第 2 轮已核，维持）。
3. **全局终态写自身失败（单元格 8）——自愈，干净**：第二步 CAS 0 行或异常 → DB 停留 CANCELLING/CONFIRMING（未 finished）→ 恢复扫描下周期重拾重驱动；链内 TccTransaction 对象方法域丢弃（恢复路径每记录每周期新建，TccEngine.java:345-347），陈旧内存状态不跨周期存活。无悬挂。
4. **两步 CAS 全部调用点穷尽（F3 矩阵）**：grep 全模块（排除 _gen//test/）核对，TCC 状态机写入方合计 19 处，无遗漏：
   - 全局表：saveTccRecordAsync ×1（TccTransaction.java:54，begin→TRYING 插入无 CAS；主键冲突即失败→task 不执行→registry 恢复，干净）+ updateTccStatusAsync ×4（:110 →CANCELLING、:115 →cancel 终态、:132 →CONFIRMING、:136 →confirm 终态）。
   - 分支表：saveBranchRecordAsync ×1（TccBranchTransaction.java:53，beginTry→TRYING 插入；无条件插入的准入面立案 C3-017）+ updateTccBranchStatusAsync ×14（finishTry :64/:66/:72/:74/:79；beginConfirm :103；finishConfirm :115/:119/:121；beginCancel :136；finishCancel :149-150/:154-155/:159/:162-163）。
   - 逐守卫核对：beginConfirm 同步白名单守卫（越界同步抛→C3-010 单元格）、异步写失败→C3-016；beginCancel 的 isBranchCancellable 守卫与 cancelAllAsync:171 预筛为同一谓词、同线程同步连续读同一内存对象、中间无 await 点——第 1 轮 C3-010 注明的「check 与 begin 之间 TOCTOU 窄窗」实为空窗，关闭；finish* 三函数无 from 守卫（内存值为 from 的无条件 CAS）——0 行→C3-002、异常→C3-016，其中 finishTry 写失败方向核对为 failsafe（写失败→task future 异常→endAsync 走 cancel 路由→DB 分支仍 TRYING→rollbackOnly→补偿方向安全），干净；doCancelAsync/doConfirmAsync 入口守卫（TccTransaction.java:103-108/:125-130）读链内内存→陈旧内存绕过→C3-001 族（已立案）。
   - 全局 DB 状态域穷举：{TRYING, CONFIRMING, CONFIRM_FAILED, CANCELLING, CANCEL_FAILED} ∪ finished{CONFIRM_SUCCESS, CANCEL_SUCCESS, BIZ_CANCEL_FAILED}；endAsync 路由（:63-74）覆盖全部持久化非终态，无不可路由持久化状态（TIMEOUT_FAILED 仅存在于分支级，全局无写入点——经调用点穷举核实）；CREATED 仅存在于插入前内存（saveTccRecordAsync 同步置 TRYING）。
5. **retry 侧 F3 对照**：retry 引擎无两步 CAS 状态机——扫描器拾取用 tryUpdateManyWithVersionCheck 版本 CAS（RetryRecordStoreImpl.java:204），其余记录写为无条件直更，其交错面已由 C3-003/005/013 覆盖，本轮无新增。
6. **KILLED 状态**：无任何写入方（仅 TccRunner.java:161 守卫引用），死状态，无缺陷面——排除。
7. **endAsync 驱动方穷举**：TccEngine.java:248（task 同步抛异常补偿）、:252（completeAsyncOnContext 完成回调）、:347（恢复扫描）；同步路径经 ITccTransaction.end（FutureHelper.syncGet(endAsync(...))，ITccTransaction.java:38-40）复用同一 endAsync 链——聚合矩阵的输入源已全覆盖。

---

## 3. 终止声明

本轮产出新发现（C3-016 P1、C3-017 P3），故不输出「未发现新的高价值问题。C3 维度深挖终止。」原文终止信号。

但须明确记录收口结论：**C3-016 是 TCC 聚合矩阵最后一个未立案单元格（第 4 格），C3-017 是聚合矩阵之外唯一残留的悬挂机制，两者立案后矩阵 9 个单元格与 19 个 CAS 调用点已全部处置完毕，C3 维度的可探明审计面已穷尽。** 按 campaign 继续条件（前轮新增含 P1 必派下一轮），C3-016（P1）将机械触发下一轮；建议下一轮定位为**验证轮**——独立复核本文 §2 的矩阵闭合表与调用点穷举清单、校验 C3-016/C3-017 的立案质量，而非横展新面：不存在未探明的横展面，残余工作属于修复验证而非审计盲区。

---

## 4. 覆盖声明

**本轮新增覆盖（前两轮未深入的定向面）**：
- TccRunner 两个聚合函数的逐状态输入域核对（cancel 方向失败态优先级与阻断语义、confirm 方向 throw 条件与重入语义、被预筛分支的折叠正确性、组合用法在双链对撞下的自愈边界）——产出单元格 6 干净结论。
- 分支级 begin/finish 写失败的异常完成路径（第 1/2 轮仅覆盖 0 行值完成与同步守卫抛出两类）——产出 C3-016。
- 分支准入/分支清单加载时序（守卫内存快照 + 无条件插入 + 一次性加载 + 级联清理证据）——产出 C3-017。
- 全局/分支两表全部 19 个状态写入调用点穷举（saveTccRecordAsync ×1、updateTccStatusAsync ×4、saveBranchRecordAsync ×1、updateTccBranchStatusAsync ×14，grep 全模块核对无遗漏）及逐守卫分析（含 C3-010 遗留 TOCTOU 窄窗的空窗化关闭）。
- 全局 DB 状态域与 endAsync 路由完备性核对（无不可路由持久化状态、TIMEOUT_FAILED 全局无写入点、endAsync 驱动方穷举）。
- 平台设施行号级复核：FutureHelper.futureCall（122-129，同步异常 reject）、whenCompleteAsync（354-370，err 透传 + handler 异常 catch）、waitAll（389-413，allOf）——确认第 2 轮引用准确，仅用于校准本单元缺陷，不作为发现对象。

**仍排除（与前两轮一致）**：平台设施自身内部实现（GlobalExecutors、IRpcServiceInvoker 具体实现的超时配置、ORM updateByQuery/tryUpdateManyWithVersionCheck/updateDirectly 内部实现）；测试代码与 `_gen/` 生成物；消费方模块（nop-job-retry-adapter 等）；参与方（participant）侧对 late-try/cancel-before-try 的处理（本单元之外）；nop-tcc-service/web 的 biz 层 CRUD 与 xbiz auth 收敛（非 C3 面）。
