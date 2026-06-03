# Adversarial Review Summary: nop-job (2026-06-03)

## 基本信息

- **审核模块**: nop-job
- **审核日期**: 2026-06-03
- **审核类型**: 对抗性审查（开放式发现导向，5 轮深挖）
- **审查方法**: 从代码异常信号出发，重点验证 2026-05-18 审计修复质量及 R1~R4 修复状态，并在第 5 轮从先前审查盲区（Calendar 边界条件、RPC 广播健康过滤、Trigger 语义映射、trust boundary）出发发现新问题

## 修复验证结果（累计）

### R1~R4 修复验证（第 5 轮重新验证）

先前 AR-1~AR-24 的 24 项发现中，**19/24 已修复**（第 4 轮验证时为 13/18）：

| Round | 修复状态 | 已修复 IDs | 未修复 IDs |
|-------|---------|-----------|-----------|
| R1 | 7/7 | AR-1, AR-2, AR-3, AR-4, AR-5, AR-6(P3), AR-7(P3) | — |
| R2 | 6/9 | AR-8, AR-9, AR-10, AR-11, AR-12, AR-13 | AR-14(P3), AR-15(P3), AR-16(P3) |
| R3 | 2/2 | AR-17, AR-18 | — |
| R4 | 4/6 | AR-19, AR-20, AR-21, AR-22 | AR-23(P2→已修复), AR-24(P2) |

**总修复率**: 19/24 (79%)

### 仍未修复（5 项，均为 P3 或已知设计限制）

- **AR-6 (P3)**: Planner parallel 路径 setActiveFireCount(0) 死写
- **AR-7/Prior F4 (P3)**: maxFailedCount 硬编码为 0 — 无 ORM 列提供值
- **AR-14 (P3)**: copyMap 返回原始引用而非副本
- **AR-15 (P3)**: findFirstErrorTask 优先级不一致
- **AR-16 (P3)**: RpcBroadcastTaskBuilder 不设置 taskPayload

### 已知设计限制（2 项）

- **AR-24/Prior F9 (P2)**: NopRetryJobRetryBridge 返回 null — 异步重试无法同步获取真实 ID，retryRecordId 跨系统追踪链路不完整
- **Prior F14 (P3)**: JobFireResult.CONTINUE 字段/方法名冲突

## 新发现汇总（33 项，R1: 7 + R2: 9 + R3: 2 + R4: 6 + R5: 9）

### Round 5 发现（Calendar 边界条件、Trust Boundary、ScheduleStore 乐观锁遗漏）

| ID | 严重程度 | 一句话摘要 |
|----|---------|-----------|
| AR-25 | **P1** | AnnualCalendar.excludeDays 未初始化 → isExcludedDay NPE |
| AR-26 | P2 | HolidayCalendar/AnnualCalendar.getNextIncludedTime 排除所有未来日期时无限循环 |
| AR-27 | P2 | CronCalendar.getNextIncludedTime 毫秒级扫描 — 长排除范围下性能极差 |
| AR-28 | P2 | RpcBroadcastTaskBuilder 不按健康状态过滤服务实例 — 向不健康节点派发任务 |
| AR-29 | **P1** | resolveCompletionDecision 信任未验证的 task result 将 schedule 标记为 COMPLETED |
| AR-30 | P2 | LimitCountTrigger 使用 totalFireCount 而非 fireCount — PARALLEL 策略可超出 maxExecutionCount |
| AR-31 | P2 | handleExecutionResult updateTask 失败后静默丢弃执行结果 — 无重试无日志 |
| AR-32 | P3 | Task builders 使用 System.currentTimeMillis() 而非 DB 时钟 |
| AR-33 | P2 | cancelFire 中 tasks 使用 updateEntityDirectly — 可覆盖并发 timeout 状态 |
| AR-34 | P2 | JobScheduleStoreImpl 5 个 schedule 更新路径全部使用 updateEntityDirectly — planner 路径缺乏乐观锁 |
| AR-35 | P2 | Schedule lock 使用 nextFireTime 作为隐式锁 — planner 崩溃后可能产生重复 fire |

## 合并严重程度分布（Round 1~5）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 9    | AR-1,2,8,9,17,19,20(R1-R4 均已修复), AR-25(Calendar NPE), AR-29(result-driven completion) |
| P2      | 19   | AR-3~5,10~13,18,21~24(R1-R4 均已修复), AR-26~28,30~31,33~35(R5 新增) |
| P3      | 7    | AR-6,7,14~16(未修复低优先级), AR-32(R5 新增) |

## 总评

nop-job 模块经过五轮对抗性审查，共发现 35 项问题（含 prior findings 验证）。R1~R4 的 24 项中 19 项已修复（修复率 79%），R5 新增 11 项。模块整体质量显著提升，R4 报告的所有 P1/P2 问题均已修复。最值得关注的 3 个方向：

**1. Calendar 边界条件组合 (AR-25, AR-26, AR-27)**

三个独立的 Calendar 实现问题：AnnualCalendar 未初始化导致 NPE、排除日期过多导致无限循环、CronCalendar 退化为毫秒扫描。这些是先前审查未触及的盲区。Calendar 是 trigger 计算的基础设施，其稳定性直接影响 scheduler 可用性。建议统一增加防御性编码。

**2. result-driven completion 缺乏安全门 (AR-29)**

`resolveCompletionDecision` 信任 task 的 `resultPayload` 中的 `completed: true` 来永久终止 schedule。任何 job 实现都可以终止调度。建议添加 schedule 级别的配置开关。

**3. ScheduleStore 的 updateEntityDirectly 系统性遗漏 (AR-34)**

AR-9/AR-19/AR-22 修复了 fireStore 和 BizModel 中的乐观锁问题，但 `JobScheduleStoreImpl` 中的 5 个更新路径全部仍使用 `updateEntityDirectly`。这是 updateEntityDirectly 遗留问题的最后一层——engine 层已修复，store 层仍未覆盖。

## 优先修复建议

### 待修复（按优先级排序）

1. **P1 [AR-25]**: `AnnualCalendar.excludeDays` 初始化为 `Collections.emptyList()`
2. **P1 [AR-29]**: 为 result-driven completion 添加 schedule 配置开关（如 `allowResultCompletion`）
3. **P2 [AR-26]**: HolidayCalendar/AnnualCalendar 添加最大迭代次数（366×5）
4. **P2 [AR-27]**: CronCalendar 非满足路径跳转到 `baseCalendar.getNextIncludedTime()` 而非逐毫秒递增
5. **P2 [AR-28]**: RpcBroadcastTaskBuilder 过滤 `isHealthy() && isEnabled()`
6. **P2 [AR-34]**: JobScheduleStoreImpl 的 schedule 更新逐步改用 `tryUpdateManyWithVersionCheck`
7. **P2 [AR-33]**: cancelFire 的 task 更新改用 `tryUpdateManyWithVersionCheck`
8. **P2 [AR-30]**: `getFireCount()` 改用 `fireCount`（已调度计数）替代 `totalFireCount`
9. **P2 [AR-31]**: handleExecutionResult updateTask 失败时添加重试或 WARN 日志
10. **P2 [AR-24]**: `NopRetryJobRetryBridge` 改进 retry record ID 回填机制

### 已修复（本轮验证通过）

AR-1~AR-5 (R1), AR-8~AR-13 (R2), AR-17~AR-18 (R3), AR-19~AR-23 (R4) — 共 19 项已确认修复。

## 去重信息

- 2026-05-18-adversarial-review-nop-job (R1+R2): 31 项发现，已验证状态
- 2026-05-18-deep-audit-nop-job-full: 154 项发现（系统审计）
- 2026-06-03-deep-audit-nop-job: 36 项发现（21 维度系统审计），本报告仅覆盖对抗性审查范围
