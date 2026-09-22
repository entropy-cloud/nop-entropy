# 待审互斥与并发设计

**日期**：2026-09-22
**范围**：nop-sys（锁存储与 guard 实现）/ nop-biz（guard 调用点）/ nop-job（过期扫描）
**状态**：active
**上游**：`01-architecture-baseline.md` §2/§4/§6；`02-snapshot-nested-data.md` §3

---

## 一、设计结论

1. **互斥载体 = 记录行上的 lockKey 唯一索引**，不引入独立锁表、不做分布式锁：`{tenantId}:{bizObjName}:{bizObjId}` 在 PENDING 期间非空，终态置空；唯一索引（tenantId, lockKey）保证一对象至多一条待审。NULL 不参与唯一性冲突（主流数据库语义），create 型（无 bizObjId）天然不加对象锁。
2. **防修改 = 变更管道统一前置 guard**：guard 是动作执行前的服务端检查，不依赖前端；重复提交与修改/删除被拒共用同一检查。
3. **staleness 双保险**：待审锁保证"正常通道内无人能改"；baseVersion 校验兜住绕过 biz 层的带外写入。二者缺一不可（锁管体验与确定性，版本校验管正确性）。

## 二、Guard 契约与拦截位置

```
onMutation(action, input, ctx):
    if not makerCheckerParticipates(bizObj): pass        # 未参与机制的对象零开销
    ids = lockScopeExtractor(action, input)              # 缺省取 input.id / input.ids / 声明表达式
    if pendingLockExists(bizObj, ids):                   # 单查（status=PENDING AND lockKey IN ...）
        throw nop.err.sys.makerchecker.pending-lock      # 报文含记录编号与 maker，引导先撤回
    if isEmergencyBypass(ctx): audit(BYPASS); pass       # bypass 放行但强审计
```

- **拦截位置**：biz 管道统一装饰点（`IActionDecoratorCollector` 机制，与事务/缓存装饰器同层），使 guard 对所有变更动作（含 CRUD save/update/delete、批量操作、自定义 mutation）一次生效；GraphQL tryAction 替换发生在其上游，提交路径自身也被 guard 覆盖（重复提交在到达落库前即被拦）。
- **lockScope 表达式**：批量/特殊动作可在 xbiz `<maker-checker>` 上声明目标 id 提取表达式；未声明且无法提取 id 的 mutation（无对象语义）不受对象锁约束，仅受幂等键约束。
- **豁免动作**：审批记录自身的动作（approve/reject/withdraw 等）操作的是 `NopSysCheckerRecord`，不经 guard。

## 三、关键并发场景逐一裁定

| 场景 | 结局 | 保证机制 |
|------|------|---------|
| 两 maker 同时提交同对象 | 后者被拒（提示待审记录编号与 maker） | lockKey 唯一索引（提交事务内 check-then-insert，竞态由索引兜底） |
| 待审期间任何人（含 maker）修改对象 | 被拒 | guard 前置检查 |
| 待审期间删除对象 | 被拒（delete 动作同样过 guard） | 同上 |
| 待审期间绕过 biz 层直接改库 | approve 时 baseVersion 比对失败 → STALE（释放锁、通知重报） | staleness 校验 |
| approve 与 withdraw 并发 | 二者都以"状态从 PENDING 迁出"为前提的乐观更新（UPDATE ... WHERE status=PENDING），只有一个成功 | 记录行乐观更新 |
| 重放执行期间另一事务修改对象 | 重放本身以 baseVersion 乐观锁写业务库，版本不符则该次写失败 → EXECUTE_FAILED | 实体 version 乐观锁 |
| 同一 create 型表单重复点击提交 | 第二次被幂等键拦截 | (bizObjName, action, idempotencyKey) 唯一索引 |
| checker A approve 与 checker B approve 并发 | 同上乐观更新，仅一人进入 EXECUTING | 同上 |
| 记录 TTL 到期与 approve 并发 | 过期扫描与 approve 均为条件更新，仅一方成功 | 同上 |

**拒绝了"仅乐观校验不加锁"**：会把不确定性推迟到提交后（checker 打开页面才见 STALE）， maker/checker 双方都做无用功；锁把冲突提前到提交一刻并给出确定性排队语义。

## 四、approve 执行语义（事务边界）

```
approve(recordId, comment, ctx):
    tx1:
        record = load(recordId) WHERE status=PENDING      # 乐观更新占位
        SoD: ctx.userId != record.makerId                 # 硬校验（豁免开关见 01 §五）
        integrity: sha256(canonical(record.requestData)) == record.requestHash
        staleness: currentVersion(bizObj, record.bizObjId) == record.baseVersion
        record.status = EXECUTING                          # 条件更新占位，防并发审批
    commit tx1
    tx2 (REQUIRES_NEW，内部系统身份、租户保持):
        result = replay(bizObjName, action, requestData)   # 走原动作管道，幂等键=recordId
        record.status = APPROVED; record.lockKey = null; record.result = result
    on tx2 异常:
        record.status = EXECUTE_FAILED; cbErrCode/cbErrMsg = 异常信息   # 锁保留
```

- **tx1/tx2 分离**：审批动作与业务重放事务隔离，重放失败不回滚审批事实（Syncope 的 REQUIRES_NEW 经验）；崩溃恢复：EXECUTING 超时未终态的记录由运维任务置回 EXECUTE_FAILED。
- **重放幂等**：重放以 recordId 为幂等键；retryExecute 重入时上层业务动作靠自身幂等/乐观锁保证不重复生效。
- **STALE 释放锁而非保留**：基线已漂移，重试无意义，正确出路是重新送审（对应 Terraform "Saved plan is stale" 后重新 plan）。

## 五、撤回 / 过期 / 失败出口

- **withdraw**：仅 maker 本人或 checker-super-user；仅 PENDING 态；触发 cancelMethod（释放 try 预留）+ 释放锁 + WITHDRAW 审计。
- **过期**：`expireTime` 由提交时按配置 TTL 写入；nop-job 周期扫描 `status=PENDING AND expireTime < now` → EXPIRED + cancelMethod + 释放锁 + EXPIRE 审计 + 通知 maker。扫描与 approve 的并发由条件更新裁定。
- **EXECUTE_FAILED 双出口**：`retryExecute`（重放）与 `forceCancel`（checker-super-user，强审计 + 通知 maker），保证锁不永久死锁（硬约束 4）。

## 六、可观测性

- 指标：提交数、待审积压数（按 bizObjName 分维度）、平均待审时长、STALE 率、EXECUTE_FAILED 率、bypass 次数。
- `pending-lock` 拒绝计数按业务对象维度暴露——高频拒绝说明该对象的复核人配置或流程设计有问题。
