# C6 资源生命周期与泄漏 — 第 2 轮深挖（blind round 2）

- **维度**: C6 资源生命周期与泄漏（一切分配的资源在成功/失败/取消/中断/超时路径上正确释放）
- **批次**: 深挖合并批（第 2 轮，全部 5 源码根）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **盲审纪律声明**: 本轮为盲审第 2 轮。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；唯一例外为本目录 7 个维度初审文件（第 1 轮，必要输入）。所有验证基于当前 live code。
- **本轮目标**（按深挖指令）: C6-001（drainTo 池耗尽）与 C3-006（同位点）关系裁定——合并 or 独立；附带对第 1 轮配对表的高风险站点做失败路径抽验。
- **新发现编号**: 本轮 C6 维度**无新增发现**（C6-004 起编号保留）。

---

## 裁定：C6-001 与 C3-006 的关系 —— **同一位点、同一根因、双维度投影：合并修复（单一 work item），台账双锚点互引保留**

**事实层（本轮 live code 逐点核实）**：
- 两位点指向**同一方法**：`DaoEntityBlockingSource.drainTo`（nop-orm-data:161-214）上的 `@Transactional(REQUIRES_NEW)` + `FutureHelper.waitUntil` 轮询（默认 100ms 间隔）。
- 连接获取时点核实：`TransactionTemplateImpl.createNewTransaction`（:338-347）→ `txn.open()` → `JdbcTransaction.doOpen`（:128-134）→ `getConnection0()`——**事务打开即刻从池取连接**（第 1 轮 C6-001 的关键前提，本轮验证成立）；`cleanupTransaction` → `txn.close()` → `releaseConnection` 归还。
- K 量化（本轮补充，详见 `C3-round2.md` 深挖 2）：`DataSourceConfig.maxSize` 默认 8 → K≥8 个阻塞消费者即池整体饥饿；两种消费骨架（批 loader 有界 maxWait 但占空比近 100%；consume task 可无限 maxWait）均已追证到具体文件行。

**维度投影差异（为何当初双立案是合理的）**：
| | C3-006（异步链路视角） | C6-001（资源生命周期视角） |
|---|---|---|
| 立案重心 | REQUIRES_NEW 横跨等待窗口 + **单次 DB 调用无超时**（F5：慢查询挂死消费线程，waitUntil interval 不作用于 DB 调用本身） | **连接/事务/抢占行锁持有窗口无上界**（正常稳态非异常路径）+ 池耗尽传播面 |
| 独有修复子项 | Statement queryTimeout / 平台超时设施接入 | 事务边界内移（每 tick 短事务）+ 最大空闲持连时长守卫指标 |

**裁定**：
1. **合并修复**：两案归并为**一个 work item**（「drainTo 事务边界内移 + 单 tick 超时」），因为修复动作重叠度 >80%——事务边界收窄到单次 `loadItems` 同时消解 C6-001 的持有窗口与 C3-006 的事务横跨窗口；queryTimeout 子项挂同一 work item 的验收清单。禁止分两个 PR（第二个 PR 必然与第一个在同一方法上冲突）。
2. **台账保留双锚点**：C3-006 与 C6-001 均保留（各自维度审计链完整、判级独立成立），标注「同位点合并修复」互引；remediation 计数去重（计数 1 项，维度归属 C3/C6 双挂）。
3. **与 C2-005 的边界**：`tryClaim` 的 CAS 谓词退化（NULL 旧值不进 WHERE）是**独立缺陷**（正确性/重复投递，非资源面），不随本合并吞并——但其 UPDATE 也运行在 drainTo 事务内，事务边界内移时需保证 tryClaim 与 loadItems 的 SELECT 仍同事务（否则抢占原子性进一步退化），此为合并修复的实现约束。

## 附带抽验：第 1 轮配对表高风险站点失败路径复核

| 站点 | 抽验内容 | 结论 |
|---|---|---|
| P17 session close 配对（runInNewSessionAsync whenComplete） | 取消路径（future cancelled）是否触发 close | 维持第 1 轮判定：whenComplete 对取消亦触发，配对完整 |
| `createNewTransaction` 失败路径（TransactionTemplateImpl:339-347） | `groupTxn.open()` 抛异常（如池耗尽）时已建事务是否泄漏 | **配对完整**（本轮新验证）：catch 中 `groupTxn.close()`（连带 closeSubTransactions）+ `safeCloseObject(txn)`，state 字段尚未赋值故 cleanup 无双关面 |
| `cleanupTransaction` 抛 ERR_TXN_NOT_REGISTERED 的时点（:435-437） | 资源是否先释放后抛错 | 配对完整：close/unregister 全部完成后才抛，资源无泄漏（伪异常语义面归 C2-013，不在 C6 计数） |
| P11 JdbcTransaction eagerRelease 默认值 | 复核 `JdbcTransactionFactory.java:23`（eagerReleaseConnection=true） | 维持：commit/rollback 后立即归还，缩小跨线程持有窗口（C2-017 的僵尸连接面为该默认值下的例外形态，已在 C2 立案） |
| P6 元数据探测（getIndexInfo/getColumns 等） | try-with-resources 覆盖 | 维持配对完整 |

## 新发现

无（C6-004 起编号保留）。本轮裁定与抽验均为既有台账的收敛动作，未发现新的泄漏/持有窗口位点。

## 终止判定

- **本轮新增**: 0 条；新增 CL：0。
- **终止信号**: (a) C6-001/C3-006 同位点裁定完成（合并修复、双锚点保留）；(b) 第 1 轮 21 站点配对表中全部 P1/P2 相关站点的失败路径抽验通过（唯一不对称点 C6-002 已立案）；(c) 5 源码根内无新增资源获取点（本轮事务/session 追踪未经过任何未登记的 acquire 站点）。
- **判定**: C6 维度第 2 轮**终止**，不建议第 3 轮。修复轮入口：C6-001+C3-006 单一 work item；C6-002（cancelToken 反注册）与 C6-003（旧模型 close 不对称）按第 1 轮建议独立执行。
