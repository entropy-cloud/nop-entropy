# 2571 锁表单调时间域的墙钟锚点投影（可观测性补全）

> Plan Status: completed
> Last Reviewed: 2026-11-14
> Source: 用户质询「必须要有到底是对应墙钟哪个时间的记录」+ 同族审计（NEED-FIX 仅 ResourceLockState 诊断面 1 族）
> Related: plan 2566 R4-32（单调换源，判定语义不动）；plan 2570（信号型完结语义，无关）

## Purpose

R4-32 换源后锁表时间域是裸纳秒单调值，日志/toString 无法回答「对应墙钟哪个时刻」。引入锚点对（anchorNano+anchorWall 相邻捕获）与墙钟投影，**仅服务诊断可观测性；租约判定域保持单调比较一行不改**。

## Current Baseline

- `ResourceLockState.toString()`（:97-104）输出 lockTime/createTime/expireTime 裸纳秒，经 `LocalResourceLockManager` 4 个 INFO 站点（dump/remove-expired-lock/release-fail/force-unlock-success）外露；:129 debug 站点输出 lastCleanUpTime 裸纳秒。
- 判定面（expireTime 租约过期、waitTime 等待）全部单调比较，R4-32 契约正确，不动。
- 全库无锚点对先例；WfTaskScanner 双缝模式不适用（按域分流非投影）。

## Goals

- 每条锁记录可回答「对应墙钟哪个时间」：toString 输出投影墙钟（ISO-8601）+ 保留原始纳秒。
- lastCleanUpTime debug 日志同样可读。
- 判定域零改动：expireTime/lockTime 单调语义与既有测试不回退。

## Non-Goals

- 不改租约判定逻辑与 IResourceLockState 判定接口语义。
- 不为 OK 清单站点（TimeOut/ResourceCacheEntry/ThresholdBreaker 等 10+ 处，无外露面）加锚点。
- 不做持久化/跨机器墙钟恢复（租约恢复走权威墙钟+容差，本 plan 不涉及分布式锁存储）。

## Execution Plan

### Phase 1 - 锚点对与投影（nop-commons）

Status: completed
Targets: `ResourceLockState.java`、`LocalResourceLockManager.java`

- Item Types: `Fix | Proof`

- [x] 先写回归钉（红）：toString 含投影墙钟（ISO 格式、与当前墙钟误差 < 5s）+ `wallMillisOf` 投影正确性 + 未设锚点回退原始值
- [x] `ResourceLockState` 增加 `anchorNano/anchorWall` 字段 + `setWallAnchor(nano, wall)` + `wallMillisOf(nanoValue)` 投影（未设锚点返回 -1，toString 回退原始值）；`cloneInstance` 复制锚点
- [x] `LocalResourceLockManager.newLock` 相邻捕获锚点对；manager 级锚点对服务 lastCleanUpTime debug 投影
- [x] `toString` 输出投影墙钟 + 原始纳秒双值
- [x] 跑钉转绿；`TestLocalLockManager`/`TestLocalResourceLockManager`/`TestLocalClockSensitivity_C7_004` 零回退

Exit Criteria:

- [x] 回归钉先红后绿（变异：去掉 setWallAnchor 调用 → 钉红）
- [x] 判定域零改动：既有三测试类全绿
- [x] `./mvnw test -pl nop-kernel/nop-commons` 全绿
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 诊断面可读性达成（toString/debug 均含墙钟投影）
- [x] 判定域与 R4-32 契约零回退
- [x] 独立子 agent closure audit + 证据写入
- [x] `./mvnw test -pl nop-kernel/nop-commons` 全绿

## Deferred But Adjudicated

### OK 清单站点不加锚点

- Classification: `watch-only residual`
- Why Not Blocking Closure: 10+ 站点无诊断外露面（审计逐点核实）；`ResourceCacheEntry.getLastLoadTime` 为潜在外露面，未来进 API 时需补锚（登记）
- Successor Required: no
- Successor Path: analysis 报告随附

## Closure

Status Note: 锚点对（anchorNano+anchorWall）与投影 wallMillisOf 落 ResourceLockState；LRLM newLock 相邻捕获 + manager 级锚点服务 lastCleanUpTime debug 投影；toString 输出 ISO 墙钟+保留原始纳秒；判定域零改动（既有 lock 测试族零回退）。变异探针：去掉 setWallAnchor → 集成钉红，恢复 → 2/2 绿，nop-commons 全模块 483/483 绿。
Completed: 2026-11-14

Closure Audit Evidence:

- Reviewer / Agent: 独立 Explore 子代理 agent_84bf4d6a-fc5c-4156-9bd7-eaa9dfd82576
- Audit Session: agent_84bf4d6a-fc5c-4156-9bd7-eaa9dfd82576
- Evidence:
  - 判定域零改动：git diff 全量核查 48+/5- 全为锚点/投影/日志，tryResetLease/isHoldingLock/checkTimeout 过期判定行零触碰（LRLM :271,297）— PASS
  - 锚点 API 与投影：ResourceLockState :25-26/:43-46/:53-56/:36/:130-133 — PASS
  - 两钉断言真实（±500ms/raw 保留/-1 回退/集成走 mgr.locks）— PASS
  - checklist --strict exit 0；结论 APPROVED（2 Minor 不阻塞：文件尾换行 pre-existing、managerAnchor 非 volatile 诊断可接受）
- Evidence(原始): TestResourceLockStateWallAnchor 2 钉（先红[编译级 API 缺失]后绿）；变异探针恢复后 grep 校验 setWallAnchor 调用行真实存在（探针纪律）；`./mvnw test -pl nop-kernel/nop-commons` 483/483

Follow-up:

- ResourceCacheEntry.getLastLoadTime 为潜在外露面（当前无消费者），未来进 API 时需补锚点——analysis 登记为 watch-only
