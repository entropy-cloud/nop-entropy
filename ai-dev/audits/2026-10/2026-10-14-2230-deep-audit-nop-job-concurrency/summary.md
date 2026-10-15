# 深度审核汇总报告 — nop-job 定时调度引擎并发·异步·资源专项审计（CRA WI 1.4）

## 基本信息

- **审核单元**: `nop-job` 聚合器（12 个 jar 子模块：api/core/local/codegen/dao/worker/coordinator/meta/service/web/app/retry-adapter；164 files/17,664 lines，手写口径不含 `_` 前缀与 target/）
- **审核日期**: 2026-10-14/15
- **执行维度**: C1 共享可变状态、C2 锁与同步、C4 线程池治理、C5 内存有界性、C7 长时运行（roadmap 1.4 裁剪集）+ C10 测试保护力（C3/C6/C8/C9 不在维度集；维度集外线索 0 条）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 335e77caec（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）；vendored 源码（CronExpression/ICronExpression/BaseCalendar 约 1,016 行）仅审集成边界
- **Plan**: `ai-dev/plans/2464-cra-wi1.4-nop-job.md`（两轮对抗性审查通过后执行）
- **执行说明**: 全部批次串行派发（运营者要求）；owner doc 已知 drift（LocalJobScheduler import 路径）按 plan 预注登记为文档漂移不立案。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 6 | +0 | 6 | 0 | 0 |
| C2 | 2 | 6 | +2（C2-007/008） | 8 | 0 | 0 |
| C4 | 2 | 8 | +4（C4-009~012） | 12 | 0 | 0 |
| C5 | 2 | 4 | +1（C5-005） | 5 | 0 | 0 |
| C7 | 2 | 8 | +4（C7-009~012） | 12 | 0 | 0 |
| C10 | 1 | —（输入=复核后缺陷清单） | — | 9（缺保护登记，全 P2） | 0 | 0 |
| **合计** | — | **32** | **+11 = 43** | **43 + 9 缺保护** | 0 | 0 |

全部 5 个缺陷维度均为 2 轮收敛（第 2 轮新增均为 P2/P3 且带终止信号，无 P1 触发的第 3 轮）；全部发现经独立复核 43/43 保留（本轮零降级零驳回，各复核以从严验证 + 反证关闭方式执行）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 43 条缺陷 → 合并后 **34 项 = 7 缺陷族（MA-01~07）+ 27 独立立案**；自洽核对 7 族吸纳 16 成员 + 27 独立 = 43 ✓。
- 与复核提议不一致：推翻 0 项；1 项新并（MA-07：C5-003=C1-006 CronExpression ThreadLocal，复核双向漏登，C1 侧 P3 异议留档）+ 2 项细化。
- 跨单元对账：与 cra-index WI 1.1–1.3 台账 24 条 P1 **零重复立项**；3 条跨单元关联单列（globalWorker 共租 ↔ WI1.2 MA-06、nop-commons 平台类根载体 BindScheduledExecutor/ExecutorHelper 可上提平台修复、WI1.1 同号撞名澄清）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 2 | MA-01 rpcPoll 条目无终结驱动（注册表/句柄永久泄漏+无退避重试风暴+WARN 放大+零观测，三视角归并 C4-001/C5-001/C7-001/C5-005，修复须同盖失败预算/deadline 兜底/DB 回传/PollEntry 精简/gauge 五面，受 cancelToken 恒空前置约束）、C2-001 per-job 监视器内调用外部 invoker（管理面/停机无限期阻塞+可构造 ABBA 死锁） |
| P2 | 23 | getJobDetail 无锁读（MA-02）、addJob raced 静默丢注册（MA-03）、ExecutionContext 逃逸（MA-04）、Error 吞没族三站点（MA-05）、派发 promise 无超时容量侵蚀（MA-06）、CronExpression ThreadLocal（MA-07）+ 17 项独立（scanner 停摆说谎、globalWorker 无上限、SUSPICIOUS 棘轮误杀、三表无归档、reconciler 窗口饥饿、单次异常永久 FAILED、无 stall 检测、触发器计算失败无隔离、planner fence 双执行、CLAIMED 零回收等） |
| P3 | 9 | timer 滞留、cursor 迭代无守卫、关闭无等待、maxPoolSize 无效、墙钟不一致、WARN 无采样等 |

C10 另有缺保护登记 9 条（全 P2，映射见 merge-adjudication.md §4.5）：保护力矩阵 6 推演组**会红 0/假保护 0/无测试红 6**——全部 P1 缺陷在现测试面回归保护力为零；结构性发现：异步面 100% 即时完成 mock 系统性排除「慢/挂」形态，全模块零真实多线程竞争测试，时序敏感命中 2 处（缺席断言形态）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 全部 43 条保留发现均为「新发现，未修复」：rpcPoll 特性（C4-001 族）2026-08-28 引入、reconciler（C7-005）2026-08-13 引入，均晚于全部历史 nop-job 审计（最晚 2026-06-19）；其余机制在历史各轮（check/check2/2026-05/06 共 11 目录）summary/结论节无同机制登记。
- 历史修复区分：72b770b094（每次执行泄漏空转句柄终结方向 + addJob fail-fast）已修方向与本轮发现（future 永不终结方向、raced 分支）正交；35623e49cf（scanCache remove）为 C1-006 同族先例但 remove 角度未登记。

## 关键发现摘要（P1 全清单，详见 merge-adjudication.md §4.2）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C4-001（MA-01，成员 C5-001/C7-001/C5-005） | rpcPoll 条目无终结驱动：永久泄漏+重试风暴+WARN 放大+零观测 | nop-job-coordinator 轮询管理 |
| 2 | C2-001 | per-job 监视器内调用外部 invoker：管理面/停机无限期阻塞+ABBA 死锁 | nop-job-local 调度器锁边界 |

## 盲区自评

- `_gen/` 与 target/（含 AOP 生成副本）按纪律排除；vendored cron/calendar 仅审集成边界，CronExpression 算法逐分支未做（第 1 轮残留边界，第 2 轮按集成口径抽验）。
- C3/C6/C8/C9 为 roadmap 1.4 裁剪维度：各维度初审均执行了维度集外线索 overlay，候选线索全部 ≤P2 形态未登记（各报告覆盖度节有注记）；若 7.1 需要这些维度的完整口径，需补审计（本单元不背书其覆盖度）。
- C4-011（非 daemon 线程阻断 JVM 退出）仅覆盖嵌入式显式停止路径，SIGTERM hook 路径不受影响。
- C7-011 的 hostId 持久化形态、IRpcServiceInvoker 无限挂起形态列入不确定项（不影响判级）。
- 测试基座统计经 C10 复核勘误（61 测试类而非 67 等），不影响定性结论。

## 与其他 roadmap 的重叠

- 与 WI 1.1–1.3 台账零重复立项（逐条对账）；globalWorker 共租问题（C4-005）与 WI 1.2 MA-06（globalWorker 池）为同一平台设施的不同使用方，7.1 合并裁定时 cross-reference 并考虑平台级修复（专用 executor）。
- security-audit / full-code-review roadmap：无重叠面。
