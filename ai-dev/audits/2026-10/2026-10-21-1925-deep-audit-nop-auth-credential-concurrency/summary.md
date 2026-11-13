# 深度审核汇总报告 — nop-auth + nop-credential 并发·异步·资源专项审计（CRA WI 5.1）

## 基本信息

- **审核单元**: `nop-auth-credential`（2 模块 ~325f/~35,514 lines src/main 口径——nop-auth 含嵌套 nop-oauth + nop-credential；`_gen/` PkBuilder 5 文件审计排除）
- **审核日期**: 2026-10-21
- **执行维度**: C1 共享可变状态、C2 锁与同步、C6 资源生命周期（roadmap 5.1 裁剪集）+ C10 测试保护力（其余维度 overlay CL×17）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit b6fe555935
- **方法论**: 盲审初审 → 深挖迭代 → 独立复核 → 合并裁定（共享前缀+维度正文）
- **Plan**: `ai-dev/plans/2482-cra-wi5.1-nop-auth-credential.md`（R1 agent_b139157b 4 Major+7 Minor 全修；R2 agent_ca38b1d8 1 Major+4 Minor 全修）
- **执行说明**: 全部批次串行派发——C1×2 批次 + C2 合并批 + C6 合并批 + 深挖/复核/C10/裁定四阶段合并任务。**Protected Area ask-first**（nop-auth P0 须先询问用户）。**并发面极稀疏**（R1 实测仅 2 文件原语命中——C4/ThreadLocal/orTimeout 零命中）。

## 执行统计

| 维度 | 初审 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|------|---------|---------|------|------|
| C1 | 5（P2×2/P3×3） | +1（P3） | 6 | 0 | 0 |
| C2 | 6（P2×3/P3×3） | +2（P3×2） | 8 | 0 | 0 |
| C6 | 4（P1×1/P2×3） | +1（P2） | 5 | 0 | 0 |
| C10 | — | — | 19 行矩阵（会红 0/反向钉死 1/无测试 13）+ 登记 7 | 0 | 0 |
| **合计** | **15** | **+6 = 21** | **19** | 0 | 0 |

R2 深挖三维度全部显式终止（边际收益 < 1 条/轮）。

## 合并裁定

- 合并前 19 条 → **14 张工单**（8 缺陷族 MA-01~08 吸收 8 + 6 独立）+ watch 台账 4 条。
- 工单判级：**P0×0 / P1×2 / P2×5 / P3×7**。
- 跨单元关联 5 处零同站点重复；Protected Area：MA-06 触碰 orm 模型（plan-first）+ nop-auth（ask-first）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | —（即时通道显式空执行；nop-auth ask-first） |
| P1 | 2 | MA-01 六族 DB 清理配对族（NopAuthSession/MfaChallenge/SmsCode/EmailCode/RateLimitCounter/LoginAttempt——惰性删除+零回收）；MA-02 Redis TTL 附着族（六处 INCR+setTimeoutAsync 非原子——永久锁号） |
| P2 | 5 | MA-03 OAuth 刷新半个成功（IdP 轮换→本地回滚→凭证永久失效）、MA-04 JWK 锁内 HTTP（IdP 慢时全 SSO 停顿 30s）、MA-05/06/07/08 等 |
| P3 | 7 | 各尾部条目 |

C10 缺保护登记 7 条：矩阵 19 行会红 0/反向钉死 1/无测试 13——会红=0 第 5 例 campaign 同型确认。

## P0 记录

本单元 **P0 = 0**（显式空执行；nop-auth ask-first——如发现 P0 须先询问用户）。

## 现状标注摘要

19 条全部为新发现（cra-index 对 auth/credential 零先期登记）。复核勘误 4 项（C6-001 DELETE 路径证伪不降级/CL-013 volatile 证伪/CL-015 UK 存在关闭/CL-005 TOTP 时序修正）。

## 关键发现摘要（P1 全清单）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | MA-01 | 六族 DB 状态存储惰性删除+零回收：NopAuthSession（含 accessToken/refreshToken 永久驻留）+ MfaChallenge + SmsCode + EmailCode + RateLimitCounter + LoginAttempt | nop-auth dao/service |
| 2 | MA-02 | Redis 六处 INCR+setTimeoutAsync 非原子——TTL 附着失败致 RedisLoginAttemptStore 永久锁号 | nop-auth-service |

## 盲区自评

- 其余 6 维度为裁剪维度：仅 overlay 线索登记（CL×17：并入 3/独立 6/移交 8/驳回 0），未做完整口径审计。
- 并发面极稀疏（R1 实测 2 文件原语命中）为实测事实——预校准成立未凑数。
- Protected Area：MA-06 触碰 orm 模型（plan-first）+ nop-auth（ask-first）——实施前须走 plan audit。
- R2 深挖三维度全终止，无口径偏差。

## 与其他 roadmap 的重叠

- 与 WI 1.1–4.5 台账（157 条 P1）零同站点重复；跨单元关联 5 处单列。
- security-audit roadmap：无重叠面。
