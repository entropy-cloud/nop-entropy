# 深度审核汇总报告 — nop-tools 解析面（nop-treesitter + nop-lint + nop-refactor + nop-jq）并发审计（CRA WI 6.2）

## 基本信息

- **审核单元**: `nop-tools-parse`（357f/52,738 lines——nop-treesitter 92f/18,213 + nop-lint 160f/22,543 [core 126f/17,842 为主] + nop-refactor 33f/4,010 + nop-jq 72f/7,982）
- **审核日期**: 2026-10-22 起审（初审/深挖）；2026-10-23（C10 复核后段配额中断恢复：CS 复核重派/合并裁定/汇总收口）
- **执行维度**: C1 共享可变状态、C6 资源生命周期（roadmap 6.2 裁剪集）+ 抽查批（C2/C3/C4/C5/C7/C8/C9 七形态 CS-sweep）+ C10
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 0d131797dd；bench/deepwiki 面不在计数面
- **方法论**: 盲审初审（C1×2/C6×2/抽查×1 = 5 批串行，101 段编号隔离）→ 深挖（3 维各 R2，均因 P1 必派）→ 独立复核 → C10 → 合并裁定
- **Plan**: `ai-dev/plans/2489-cra-wi6.2-nop-tools-parse.md`（R1 agent_df5051e6 0 Blocker、1 Major[2026-09 G10 前审未清点]+3 Minor 全修）
- **Protected Area**: 无 api 叶（R1 实测）；**W-02（C1-101）Route-B 触 nop-core 属 plan-first 已标注**
- **跨单元闭环**: **2026-09 全仓审计 G10 承接**——G10-01-02（LanguageRegistry）三态裁定=仍成立→并入 C1-104（P2 超集）；G10-01-03 仍成立→并入 CL-204；G10-01-01 非并发维持原轨；回填注记终稿见 merge-adjudication §四.3
- **执行说明**: 全部子代理严格串行派发（初审 5 + 深挖 3 + 复核 3[CS 首派配额中断零产物重派] + C10 2 + 裁定 1 = 14 派）；CL 编号冲突 1 处（C1-R2 CL-201 撞 C6 批 A）重编 CL-206

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 6（批 A×1+批 B×5） | +0（终止） | 6 | 0 | 0 |
| C6 | 2 | 11（批 A×6+批 B×5） | +0（终止） | 11 | 2（C6-004/102） | 0 |
| CS 抽查 | 2 | 3 | +1 | 4 | 1（CS-002） | 0 |
| C10 | 1 | — | — | 21 行矩阵 + 缺保护 19 | 0 | 0 |
| **合计** | — | **20** | **+1 = 21 实体** | **21 全保留 → 19 工单** | 3 | 0 |

## 合并裁定

- 21 实体 → 复核全保留（0 驳回）→ 吸收/转引用 2 → **19 工单 = P0×0 / P1×2 / P2×7 / P3×10**（维持 19/跨维度调整 2/移交 0）。
- **P1×2 全走 7.1 台账**：W-01 NodeTscBridge 代际栅栏族（C6-101 主——重启无栅栏 stale 帧击穿 restart 预算/孤儿进程/杀健康新进程；吸 C1-102/CS-001 转引用，挂 C6-103/104 失效面）；W-02 LintDeadlineExecutor.install 全局槽（C1-101——非 volatile 槽丢更新致护栏静默失效；Route-B 触 nop-core plan-first）。
- P2×7：C1-001 treesitter provider cache 竞态、C1-103 TscTypeResolver.bridge、C1-104 LanguageRegistry（吸 G10-01-02）、C1-105 RuleResultCache 跨进程踩踏、C6-001 EditPlanApplier 写盘-守卫序、C6-002 refactor「atomically」契约矛盾、CS-R2-001 error 帧无校验。
- 降级 3（C6-004 量化链修正 ~192KB 封顶/C6-102 面拆分生命周期面/CS-002 在编未接线校准）；RuleResultCache 为面拆分姊妹案（C1-105 并发面 P2 主 + C6-102 生命周期面 P3，修复同点）。

## 按严重程度分布（工单级）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 2 | 外部进程桥代际栅栏缺失（时序敏感假保护下存活）；全局 executor 槽丢更新（护栏静默失效+反向锁定） |
| P2 | 7 | 见上 |
| P3 | 10 | SCRATCH 池常驻（量化修正）、SubtreeArena free 死代码、ScannerVM 池壳、原子写无 fsync、JavaTypeResolver 缓存无界（在编未接线）、XmlTagKinds 并发同 id 等 |

C10 缺保护登记 19 项：矩阵 **会红 0/21**——campaign 第 11 例；**反向保护 4 处**（campaign 新高平历史高点——修复将使现有测试红：install 全局槽钉定/部分落地断言/池化常驻/free() 机制钉死，修复工单必须捆绑测试改写）；hang-once-then-ok 定性=时序敏感假保护；两个 P1 并发场景覆盖均为 0。

## P0 记录

本单元 **P0 = 0**（显式空执行）。全链无 P0 判级。

## 现状标注摘要

- 21 实体中 treesitter 为真首报；lint/refactor/jq 为 CRA 内首报 + 2026-09 G10 前审承接（两条并发案仍成立并入）。
- 缺陷密度热点：**外部进程桥（NodeTscBridge 五连案：C1-102/C6-101/103/104/CS-001/CS-R2-001）**与 lint 引擎全局槽（C1-101）——跨进程协作面是本单元的结构性薄弱带；nop-jq 为正面结论（四重守卫齐全 0 立案）。
- owner doc drift：nop-lint/nop-jq 无 owner doc（占 58% 行数）。

## 关键发现摘要（P1 全清单）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | W-01/C6-101 | NodeTscBridge 重启无代际栅栏：inbound.clear() 与旧 reader 迟到帧竞态——stale EOF 毒化新会话误判 EXHAUSTED 永久不可用、stale 数据帧杀健康新进程、孤儿 node 进程；现有 hang-once-then-ok 测试为时序敏感假保护 | nop-lint NodeTscBridge |
| 2 | W-02/C1-101 | LintDeadlineExecutor.install 每次从请求线程写 EvalExprProvider._executor（非 volatile 全局槽、无卸载）——与调试器 attach 写者跨 monitor 丢更新，在途 lint 的 deadline/深度护栏静默失效；现有测试反向钉定全局槽设计 | nop-lint×nop-core 交界（Route-B plan-first） |

## 盲区自评

- 覆盖核验：四源码根全闭合（jq 面 0 立案为正面结论；ProcessBuilder/new Thread 单站点重演确认）。
- 会红 0/21 第 11 例 + 反向保护 4 处新高：本单元测试基建「功能语义断言为主 + 部分钉死缺陷行为」——修复批次须捆绑测试改写。
- CS 首派配额中断零产物重派（处置留痕 dispatch-log）。
- CL 编号冲突 1 处（重编 CL-206）——多批次并行编号纪律教训登记。

## 与其他 roadmap 的重叠

- 零同站点重复；家族关联单列：NodeTscBridge 族（外部进程管理）、全局槽族（2.1 EvalExprProvider 设施面上下游）、F6 写盘序族（6.1 C6-R2-001 同族）、G10 承接链（2026-09 审计闭环）。
- security-audit roadmap：无重叠面。
