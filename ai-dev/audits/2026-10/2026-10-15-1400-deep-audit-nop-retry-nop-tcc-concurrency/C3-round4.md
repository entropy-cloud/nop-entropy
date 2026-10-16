# C3 异步链路正确性审计 — nop-retry + nop-tcc（第 4 轮验证）

- 审计单元：`nop-retry`（8 jar）+ `nop-tcc`（9 jar）
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation）
- 审计日期：2026-10-15；审计维度：C3 异步链路正确性（第 4 轮，验证轮，盲审）
- 输入：第 1 轮 C3-async-chains.md（C3-001~011）+ 第 2 轮 C3-round2.md（C3-012~015）+ 第 3 轮 C3-round3.md（C3-016/017）（三者为本轮唯一豁免读取文件）+ live code + grep 基线；未读取 ai-dev/ 下其他任何文件（含其他维度文件）。
- 本轮定位：第 3 轮已宣告聚合矩阵 9 格闭合、审计面穷尽，C3-016（P1）机械触发本轮。按验证轮范围执行三项独立复核（C3-016 逐环重验、矩阵闭合表抽验、CAS 写入点计数复现），不做横展。

---

## 1. 本轮发现

**无。** 三项验证全部通过，未推翻第 3 轮闭合结论，未产生新立案（无 C3-018 及以后编号）。

验证中发现一处**前轮文本算术笔误**（第 3 轮 §2.4 头部「合计 19 处」应为其自身逐项清单之和 20 处，见 §2.3），属记录勘误而非审计面缺口——逐项清单本身经 grep 精确复现、完整无遗漏，不推翻闭合，不构成立案对象。

---

## 2. 验证结果

### 2.1 C3-016 独立重验（逐环，全部成立）

**第 1 环：分支 CAS 失败 → 内存状态不回写。**
`TccRecordStore.updateTccBranchStatusAsync`（TccRecordStore.java:220-283）的两种失败模式均不触碰内存 status：

```java
// :274-281
long updated = branchDao().updateByQuery(query, props);
if (updated == 0) {
    LOG.warn("nop.tcc.skip-branch-status-update-due-to-conflict:...");
    return null;                    // 0 行：值正常完成，内存保持旧值
}
record.setStatus(status.getCode()); // 仅 1 行命中才回写
```

异常完成路径：整个 lambda 包裹在 `FutureHelper.futureCall`（:223）内，lambda 内任何 DB 异常被 `futureCall` 捕获后 reject（FutureHelper.java:122-129 `catch (Throwable t) { return reject(t); }`），`:280` 根本执行不到。两种失败（0 行值完成 / 异常完成）内存均不回写——环 1 成立。

**第 2 环：begin 失败 → RPC 不下发；finish 失败 → 链以异常完成。**
`TccRunner.runBranchCancelAsync`（TccRunner.java:98-122）与 `runBranchConfirmAsync`（:72-96）同构：

```java
return branchTxn.beginCancelAsync(timeout).thenCompose(arg -> {   // :105 begin失败→failed stage→lambda不执行
    ...
    serviceInvoker.invokeAsync(...getCancelMethod(), ...)          // :108 RPC在thenCompose内，未下发
        .whenComplete((ret, ex) -> {
            branchTxn.finishCancelAsync(timeout, ret, ex).whenComplete((ret2, ex2) -> {
                if (ex != null)          future.completeExceptionally(ex);
                else if (ex2 != null)    future.completeExceptionally(ex2);  // :113 finish写失败→ex2路径
```

begin 的 failed stage 使 `thenCompose` 整体跳过（手工 future 从未创建，RPC 零下发）；finish 写失败经 ex2 使手工 future 异常完成——环 2 成立。且 begin 的同步守卫（beginCancelAsync 守卫 TccBranchTransaction.java:131-135 / beginConfirmAsync 白名单 TccBranchTransaction.java:97-102）在 DB 写之前、于调用线程同步执行：TRY_SUCCESS 在两个白名单内均放行，失败发生在其后的异步 DB 写——与 C3-010 的「同步守卫抛出」（写前、同步）机制不重叠。

**第 3 环：分支失败透传至聚合。**
`FutureHelper.waitAll` = `CompletableFuture.allOf`（FutureHelper.java:389-391、397-413，:412 `all ? allOf : anyOf`）——任一分支 future 异常完成即令聚合 future 失败，不短路、不吞并。`doCancelAsync`/`doConfirmAsync` 的聚合挂在 `FutureHelper.whenCompleteAsync`（TccTransaction.java:113-116、134-137）：

```java
// FutureHelper.java:360-368
future.whenComplete((ret, err) -> {
    try { handler.apply(ret, err)...   // err != null 时 handler 照常执行（聚合不被跳过）
    } catch (Exception e) { promise.completeExceptionally(e); }   // :365-367 聚合自身throw才跳过后续写
});
```

err≠null 时聚合照跑——环 3 成立（该实现同时反证第 2 轮失效面单元格 4 的「聚合 throw → 终态写跳过」路径，见 §2.2）。

**第 4 环：聚合白名单不含中间态 → 折叠 SUCCESS。**
逐状态核对两个聚合函数：

- `aggregateCancelBranchStatus`（TccRunner.java:178-196）：阻断集 = {BIZ_CANCEL_FAILED(:186), CANCEL_FAILED(:188), TIMEOUT_FAILED(:192→CANCEL_FAILED)}；**TRY_SUCCESS / CANCELLING / BEFORE_TIMEOUT 均不在阻断集**，落到 `:195 return TccStatus.CANCEL_SUCCESS` 默认折叠。
- `aggregateConfirmBranchStatus`（:198-217）：阻断集 = {CONFIRM_FAILED(:206)} + `isCancelled()` 分支 throw(:208-214)；**TRY_SUCCESS / CONFIRMING 既非 CONFIRM_FAILED、按 TccStatus.isCancelled()（TccStatus.java:97-101 = {TRY_FAILED, CANCEL_SUCCESS, TIMEOUT_SUCCESS, BIZ_CANCEL_FAILED}）亦非 cancelled**，落到 `:216 return CONFIRM_SUCCESS` 默认折叠。

C3-016 标题所列三个中间态（TRY_SUCCESS/CANCELLING/BEFORE_TIMEOUT）逐一核对确在折叠集合内——环 4 成立。

**第 5 环：双写窗口时序（分支写失败 + 毫秒级后全局写成功 → 危害stick）。**
全局首跳成功时内存已回写（TccRecordStore.java:194 `tccRecord.setStatus(...)` 仅在 updateTccStatusAsync 1 行命中后执行），故终态 CAS 的 from（内存 CONFIRMING/CANCELLING）与 DB 当前值一致、前置条件自洽；DB 已恢复则 1 行命中 → `status.isFinished()` 分支（:172-176）写入 endTime → finished。终局排除面：`fetchExpiredRecords` 的拾取谓词 `status < CONFIRM_SUCCESS(11)`（TccRecordStore.java:301）与 finished 集合（CONFIRM_SUCCESS=11/CANCEL_SUCCESS=12/BIZ_CANCEL_FAILED=13/TIMEOUT_SUCCESS=14/KILLED=15，TccStatus.java:52-68）核对一致——CANCEL_SUCCESS(12) 永不再被拾取。
反向（终态写同样失败 → 自愈）核对成立：全局停留 CANCELLING/CONFIRMING（<11）→ 下周期重拾；恢复驱动每记录新建 TccTransaction（TccEngine.java:343-347 `new TccTransaction(false, record, this)` + `endAsync(true,null,null)`），陈旧内存不跨周期存活；路由 CONFIRMING→doConfirmAsync / CANCELLING→doCancelAsync（TccTransaction.java:63-74）；分支按 DB 行新鲜重载（TccEngine.java:125-127 → TccRecordStore.getBranchRecordsAsync:141-147 findAllByExample）：TRY_SUCCESS 经 beginConfirmAsync 白名单(:97-98)重驱动；CANCELLING 分支经 `isBranchCancellable`（TccRunner.java:158-162）——CANCELLING 不在 isCancelled 集合（TccStatus.java:97-101）→ true → 重驱动（begin 同值/跨值 CAS 命中 DB 仍存的 CANCELLING 行）。C3-016 触发时序 (a)-(d) 四个子变体逐一走查全部成立，其中 (c) 的 finishCancelAsync 意图自证（TccBranchTransaction.java:147-148 注释「结果未知，按取消失败记录以便恢复循环重试」）被其写失败路径静默击穿的对照亦核实无误。

**C3-016 重验结论：链路五环全部独立复核成立，立案质量合格，P1 判级（单链可达 + 双写窗口与 C3-012/C3-006 同级 + 后果方向最严重）维持。**

### 2.2 矩阵闭合表抽验（3 个已归并单元格，均确认覆盖所述变体且互不重叠）

**抽验 1 —— 单元格 2「全局首跳 CAS 异常完成」（C3-012，第 2 轮）：**
重走查 `doCancelAsync`（TccTransaction.java:110-116）：首跳 `updateTccStatusAsync(CANCELLING)` 异常 → 内存 from 未回写（:194 未执行，保持 fresh-load 的 TRYING）→ `thenCompose` 跳过（cancelAllAsync 从未执行，分支零驱动，内存全为 TRY_SUCCESS）→ whenCompleteAsync err 路径聚合 → 折叠 CANCEL_SUCCESS → 终态写以**陈旧 from=TRYING** 直写 `TRYING→CANCEL_SUCCESS`。与 C3-016 的区分核实：失败点在**全局表**首跳（分支整体未驱动）vs C3-016 在**分支表** begin/finish（分支被驱动但其推进被回滚）；与 C3-001 的区分核实：首跳 0 行是值完成（链继续驱动分支、下发重复 RPC）vs 异常完成（链跳过驱动）。三者占据「写点 × 失败模式」的不同区域，互不重叠。成立。

**抽验 2 —— 单元格 3「分支 finish CAS 0 行、双链结果分歧」（C3-002，第 1 轮）：**
机制重走查成立：0 行时 `updateTccBranchStatusAsync` 以值正常完成（:275-278 return null，非异常）→ runBranchCancelAsync 的手工 future 走 `ex==null && ex2==null → complete(ret)` 正常完成 → waitAll 成功、聚合 err==null → 聚合读到链内**未回写的旧内存值**（非失败态）→ 折叠 SUCCESS。双链前提的必要性核实：单链下 finish 的 from（内存）与 DB 一致必然 ≥1 行，0 行必须有并发写方改行——归因「需双链对撞」准确（这正是它与 C3-016 单链异常完成的关键分轴）。一条记录性瑕疵：第 1 轮时序叙述「B 的 finish 以 B 内存 from=CANCELLING」在 B 的 begin 同样 0 行丢失时严格说应为 TRY_SUCCESS（0 行不回写），但两个可能内存值（TRY_SUCCESS 或 CANCELLING）均非失败态、折叠结果相同，结论不变。单元格覆盖所述变体，成立。

**抽验 3 —— 单元格 4「begin 同步守卫抛出 + 聚合 throw」（C3-010 + 第 2 轮失效面扩展）：**
重走查：`beginConfirmAsync` 对越界状态**同步** throw（TccBranchTransaction.java:99-102，写前、调用线程）→ 该 throw 发生在 `doConfirmAsync` 的 thenCompose lambda 内（TccTransaction.java:133）→ 按 CompletionStage 契约复合 stage 异常完成。两个子出路均按 TccStatus 谓词核实：坏分支属 cancelled 类（TRY_FAILED 等，isCancelled()=true）→ 聚合在 :208-214 throw → `whenCompleteAsync` 的 catch（FutureHelper.java:365-367）使 promise 异常完成、`updateTccStatusAsync` 整体跳过 → 全局卡 CONFIRMING（第 2 轮扩展格）；坏分支为 CANCELLING/TRY_UNKNOWN（非 cancelled、非 CONFIRM_FAILED）→ 聚合折叠 CONFIRM_SUCCESS → 误报终态（C3-010 主格）。与 C3-016 的区分核实：同步 throw 在任何 DB 写之前 vs C3-016 的失败在守卫放行后的异步写期间。单元格（含扩展）覆盖所述变体，成立。

**互不重叠总检**：9 格沿「写点（全局/分支）× 失败模式（0 行值完成 / 异常完成 / 同步守卫 throw / 聚合 throw）× 链数（单链/双链）」三轴分布，抽验 3 格与其相邻格（C3-001、C3-016）边界清晰，未发现重叠申报或漏格。维持第 3 轮「矩阵无第 5 个未立案错误终态/悬挂单元格」的结论。

### 2.3 两步 CAS 写入点计数复现（grep 全仓库，排除 `_gen/`、test）

| 写入方法 | 调用点 | grep 复现位置 |
|---|---|---|
| saveTccRecordAsync | ×1 | TccTransaction.java:54 |
| updateTccStatusAsync | ×4 | TccTransaction.java:110、:115、:132、:136 |
| **全局小计** | **5** | |
| saveBranchRecordAsync | ×1 | TccBranchTransaction.java:53 |
| updateTccBranchStatusAsync | ×14 | TccBranchTransaction.java:64、:66、:72、:74、:79、:103、:115、:119、:121、:136、:149、:154、:159、:162 |
| **分支小计** | **15** | |
| **合计** | **20** | |

- 除接口声明（ITccRecordStore.java:22/24/26/28）与方法实现（TccRecordStore.java:151/162/208/222）外，全仓库无其他调用点。
- 反向排查：nop-tcc-core/api 层无任何绕过 store 的状态写（`.setStatus(` 在 core 层 grep 为空）；实体引用仅余 entity 自身、api DTO/beans 与 nop-tcc-service 的 BizModel CRUD（后者为第 3 轮已声明的非 C3 排除面）。
- **勘误**：第 3 轮 §2.4 头部「合计 19 处」与其自身逐项清单（1+4+1+14）及本轮 grep 结果均不符，正确总数为 **20 处**（任务委托文本中「全局 5 + 分支 15 = 19」同为算术笔误，5+15=20）。逐项清单本身完整精确，闭合结论（「无遗漏」）不受影响——这是汇总算术错误，不是覆盖缺口。

**计数复现结论：调用点穷举清单经 grep 精确复现、无遗漏无多记；仅前轮汇总数字 19 应更正为 20。**

---

## 3. 终止声明

**未发现新的高价值问题。C3 维度深挖终止。**

三项验证全部通过：C3-016 链路五环逐环独立复核成立（含四个子变体与双写窗口正反两面）；矩阵闭合表抽验 3 格全部确认覆盖所述变体且与相邻格互不重叠；CAS 写入点计数经 grep 精确复现（合计 20 处，前轮汇总数字 19 为算术笔误、已勘误，不影响穷尽性）。第 3 轮的收口结论——聚合矩阵 9 格闭合、19（更正为 20）个 CAS 调用点全部处置、可探明审计面穷尽——经独立复核维持。C3 维度残余工作属于修复验证（C3-016 等已立案项的修复落点核验），不属于审计盲区。

---

## 4. 覆盖声明

**本轮验证覆盖（不新增横展面）**：
- C3-016 全链路五个环节的实现级重验：TccRecordStore.updateTccBranchStatusAsync 双失败模式（:274-281 + futureCall 异常 reject）、TccRunner 三个 runBranch*Async 的 begin/finish 失败传播（:72-122）、FutureHelper.waitAll 的 allOf 语义（:389-413）、whenCompleteAsync 的 err 透传与 handler 异常 catch（:354-370）、两个聚合函数的逐状态输入域（:178-217）+ TccStatus 谓词定义（isCancelled/isFinished/isAllowConfirm/isInTransaction，:80-105）、全局终态 CAS 的 from 自洽性与恢复排除谓词（TccRecordStore.java:194、:301 + TccStatus.java:52-68）、自愈反向路径（fetchExpiredRecords 重拾 + TccEngine.java:343-347 每周期新建 TccTransaction + :125-127 分支新鲜加载 + isBranchCancellable 对 TRY_SUCCESS/CANCELLING 的放行）。
- 矩阵闭合表抽验：单元格 2（C3-012 全局首跳异常）、单元格 3（C3-002 分支 finish 0 行双链）、单元格 4（C3-010 同步守卫 throw + 聚合 throw 双子出路），并核对与 C3-001/C3-016 的边界不重叠。
- 两步 CAS 写入点计数 grep 复现（全仓库范围）+ 反向排查（core 层无绕过 store 的状态写）。
- 平台设施行号级复核（FutureHelper.futureCall/whenCompleteAsync/waitAll）仅用于校验前轮引用准确性，不作为发现对象。

**仍排除（与前 3 轮一致）**：平台设施自身内部实现（GlobalExecutors、IRpcServiceInvoker 具体实现、ORM updateByQuery/saveEntityDirectly 内部）；测试代码与 `_gen/` 生成物；nop-tcc-service/web 的 biz 层 CRUD（非 C3 面）；消费方模块（nop-job-retry-adapter 等）；参与方侧行为。盲审纪律：本轮仅读取 3 个豁免前轮文件 + live code + grep 基线，未读取 ai-dev/ 下其他任何文件。
