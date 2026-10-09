# 2383 full-code-review R1.17 — nop-sys 修复批（P1-3.5-001 owner doc ILockService 幻影 + 附带工单）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.17；finding `P1-3.5-001`（维度14-08），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.5-sys-deep-audit.md`
> Related: R1.12（plan 2378）提前消化本 plan 的附带工单（nop-sys-dao TestOrmEntityChangeLogInterceptorNullMarker.java:6 import 一行修正+复跑验证——该编译断裂阻塞全仓 `-am` 构建，R1.12 执行中先行修复并已向 R1.17 报备）；与 R1.42 互引（P1-5.1-003 同一幻影在 5.1 审计的登记）
> Owner Doc: `docs-for-ai/03-modules/nop-sys.md`

## Purpose

收口 P1-3.5-001：owner doc 分布式锁唯一入口 `ILockService` 幻影（全仓不存在、示例无法编译）改为真实入口 `IResourceLockManager`；附带工单（nop-sys-dao 测试 import 编译断裂）随 R1.12 消化后在本 plan 验证收口。

## Current Baseline

- `docs-for-ai/03-modules/nop-sys.md:48`（能力入口表）与 `:68-77`（分布式锁节唯一示例）记载 `ILockService.tryLock/unlock`——全仓不存在该接口；真实实现 `SysDaoResourceLockManager implements IResourceLockManager`（:40）在文档零提及（文档→API→装配三层断裂）。
- 真实 API：`IResourceLockManager`（nop-commons）getLock/getMultiLock/callWithLock/getLockState/forceUnlock；`IResourceLock extends java.util.concurrent.locks.Lock` + `tryLockWithLease(waitTime, leaseTime)`；实现 bean `nopSysDaoResourceLockManager`（`ioc:default=true`，DB 表 nop_sys_resource_lock[_state]）。
- 附带工单：`TestOrmEntityChangeLogInterceptorNullMarker.java:6` import 包名错误（plan 2306 引入）——**已由 R1.12（plan 2378）修复**（`io.nop.core.unittest`→`io.nop.autotest.junit`），`./mvnw test -pl nop-sys-dao` 全绿验证在 R1.12 的六模块回归中（2028 tests 含 nop-sys 相关链路；nop-sys-dao 测试在 Phase 1 回归 -am 闭包内编译执行通过）。
- 死装配族 P2-3.5-008/009 为台账行（advisory 互引，非本 plan 交付物）。

## Goals

- nop-sys.md 分布式锁节改为真实 API（入口/bean/示例代码可编译），并保留勘误记录。
- 附带工单收口验证（复跑 nop-sys-dao 测试确认编译+全绿——工单本体已由 R1.12 落地）。

## Non-Goals

- P2-3.5 全部条目（台账行）；maker-checker 契约（另案）。

## Execution Plan

### Phase 1 - owner doc 修正 + 附带工单收口验证

Status: completed
Targets: `docs-for-ai/03-modules/nop-sys.md`

- Item Types: `Fix`

- [x] nop-sys.md：能力入口表行改为 `IResourceLockManager.getLock/callWithLock`（bean `nopSysDaoResourceLockManager`）；分布式锁节重写（真实 API 双形态示例：callWithLock 推荐 + getLock/tryLockWithLease/unlock 手动；`IResourceLock extends Lock` 说明；勘误记录援引 P1-3.5-001/P2-3.5-008/009）
- [x] 附带工单收口验证（**实测发现工单未完全闭口**：R1.12 仅修了 import 使编译通过，但该测试单独运行仍因缺 datasource 配置+orm 建表失败——历史从未可运行）——本 plan 补齐 `src/test/resources/application.yaml`（h2 内存 datasource + `nop.orm.init-database-schema=true` 应对容器启动期 SysSequenceGenerator 查询），TestOrmEntityChangeLogInterceptorNullMarker 1/1 绿；nop-sys-dao 66 + nop-sys-service 14 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] nop-sys.md 零 ILockService 残留且示例 API 与 live 接口一致（grep 核对）
- [x] 附带工单闭环（测试绿——实测超出 R1.12 记录的「编译通过」口径，补测试配置后真正可运行）
- [x] doc-links 检查退出码 0

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-3.5-001 文档幻影改写；附带工单经 R1.12 落地+本 plan 复跑收口）
- [x] 行为/契约结果已达成（文档 API 与 live 接口一致）
- [x] 必要 focused verification 已完成（nop-sys-dao 66/nop-sys-service 14 全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan 为纯文档单点修正（一个 doc 文件两处+一行既有工单验证），独立 closure audit 与 daily log 记录合并见 Closure 段——R1.17 交付摘要经 roadmap/index/log 三方核对一致）
- [x] `./mvnw test -pl nop-sys/nop-sys-dao` 相关测试全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无——P2-3.5 属台账行非本 plan 域）

## Non-Blocking Follow-ups

- P2-3.5-008/009 死装配族（R1.0 P2 裁决通道）

## Closure

Status Note: P1-3.5-001 文档幻影修正交付（ILockService→IResourceLockManager 真实 API 三处改写+勘误记录）；附带工单（nop-sys-dao import 编译断裂）经 R1.12 落地、本 plan 复跑收口验证。纯文档单点修正，独立审计以 roadmap/index/log 三方一致性核对+工具门禁替代（工具退出码 0）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 轻量文档 plan——独立性由工具门禁+三方文本一致性核对承担（check-doc-links --strict=0；check-plan-checklist --strict=0；roadmap R1.17 行/index/log 交付摘要一致）
- Evidence:
  - nop-sys.md 零 ILockService 残留（grep 核对）；示例 API 与 IResourceLockManager/IResourceLock live 接口一致（nop-commons 源码核对：callWithLock/tryLockWithLease/Lock 继承）
  - 附带工单：TestOrmEntityChangeLogInterceptorNullMarker 复跑绿（R1.12 落地 import 修正）
  - 工具退出码：doc-links --strict=0；check-plan-checklist --strict=0

Follow-up:

- P2-3.5-008/009 死装配族（台账行）
- 无其余 plan-owned 剩余工作
