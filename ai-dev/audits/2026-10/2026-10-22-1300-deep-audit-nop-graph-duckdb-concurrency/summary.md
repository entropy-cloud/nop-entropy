# 深度审核汇总报告 — nop-graph + nop-duckdb 并发·异步·资源专项审计（CRA WI 5.6）

## 基本信息

- **审核单元**: `nop-graph-duckdb`（campaign 最小单元：33f/2,932 lines——nop-graph 27f/2,095 [api 14f/613 + core 13f/1,482] + nop-duckdb 6f/837）
- **审核日期**: 2026-10-22（初审/深挖/复核/C10/合并裁定/汇总收口同日完成）
- **执行维度**: C1 共享可变状态、C6 资源生命周期（roadmap 5.6 裁剪集）+ 抽查批（C2/C3/C4/C5/C7/C8/C9 七形态，CS-sweep.md）+ C10 测试保护力
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 6934df7ad3
- **方法论**: 盲审初审（C1/C6/抽查 3 批串行）→ 深挖迭代（C6 三轮/CS 两轮/C1 两轮+豁免注记）→ 独立复核 → C10 → 合并裁定
- **Plan**: `ai-dev/plans/2487-cra-wi5.6-nop-graph-duckdb.md`（R1 agent_52ade26f 0 Blocker/0 Major、6 Minor 全修）
- **Protected Area**: nop-graph-api 14f/613 plan-first——审计只读；**W-06（C1-003 Edge.attrs）为唯一触碰 api 叶的工单，修复须 plan-first**
- **跨单元移交闭环**: WI 2.1 C6-CL-031（LeidenDetector 非 daemon+无 awaitTermination）三态裁定=**边界变化**——站点事实仍成立但 awaitTermination 属负修复（CWTS 无中断检查点），并入 CS-001（W-04）关闭；2.1 台账回填注记见 merge-adjudication §二.2
- **执行说明**: 全部子代理严格串行派发（初审 3 + 深挖 4 + 复核 3 + C10 2 + 裁定 1 = 13 派）；C1 深挖第 3 轮豁免（R2 新增 P1 为已登记 C6-001/CS-002 跨维度重述+清扫全闭）留痕 dispatch-log 并经 C1 复核正当性确认

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2（豁免注记） | 3 | +1 | 4（2 并入 C6 族） | 0 | 0 |
| C6 | 3（R2 P1 触发 R3） | 3（P1×2） | +3 | 6 | 0 | 0 |
| CS 抽查 | 2（R1 P1 触发 R2） | 5（P1×1） | +4 | 9（3 并入） | 0 | 0 |
| C10 | 1 | — | — | 19 行矩阵 + 缺保护 12 | 0 | 0 |
| **合计** | — | **11** | **+8 = 19 实体** | **19 全保留 → 12 工单** | 0 | 0 |

## 合并裁定

- 19 实体 → 复核全保留（0 驳回 0 降级）→ 吸收 7 → **12 独立工单 = P1×4/P2×4/P3×4**（5 族主 + 7 独立；维持 12/跨维度调整 7/移交 0）。
- **P0×0 显式空执行**；P1×4 全走 7.1 台账：W-01 账本泄漏族（C6-001 主，吸 C6-R2-001/CS-002/C1-R2-001——OPEN_FILES+connections 双载体）；W-02 竞态族（C6-002 主，吸 C1-002——track 迟到连接进已关闭引擎，判级终裁 P1）；W-03 CTAS 必败（C6-R2-002——materialize 无守卫+可重试分类→持久 dbPath 重放永久必败）；W-04 不可中断族（CS-001 主，吸 CS-R2-001+2.1 移交别名——LeidenDetector 超时幽灵线程+5 调用点零守卫）。

## 按严重程度分布（工单级）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 4 | 见上 |
| P2 | 4 | Edge.attrs 可见性缺口（api 叶 plan-first）、readXlsx 失败路径临时 CSV+deleteOnExit 族、PageRank 分配放大族（含 CodeCallGraph 载体）、Betweenness 无超时（同族相邻独立） |
| P3 | 4 | OPEN_FILES 非原子检查、TopologicalSort Θ(V²)、ShardBuildCoordinator 事务内物化（潜伏）、重放契约未声明 caller-SQL 幂等性 |

C10 缺保护登记 12 项：矩阵 **会红 0/19**——campaign 第 9 例；共性根因=duckdb 测试全为「先开-后用-后关」顺序形态（单例长驻/交错/失败重放三类生产形态零用例）+ graph 测试 0 并发/0 超时断言；相邻契约锚（TestDuckDbEngine close 侧断言）只防回归侧不防缺陷本体；优先补测 W-01/W-03（确定性触发最易回退必红）。

## P0 记录

本单元 **P0 = 0**（显式空执行）。全链无 P0 判级。

## 现状标注摘要

- 19 实体全部为首报（nop-duckdb cra-index 0 提及；nop-graph 仅 2.1 移交提及，已闭环）。
- 小单元高缺陷密度：2,932 行检出 P1×4——DuckDB 单写者约束的「登记面」（OPEN_FILES/connections）与其「锁面」（synchronized open/close）互不覆盖是结构性根因；nop-graph 算法面「外部库不可中断×调用面无守卫」成族。
- owner doc drift：nop-graph 无 owner doc（占 63% 行数）+ nop-duckdb.md L75 发现级 drift 1 项。

## 关键发现摘要（P1 全清单）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | W-01/C6-001 | OPEN_FILES+connections 账本仅 engine.close() 释放——单例 bean 长驻期每任务净增永不回收（F4）+ 过期指纹误拒同路径异配置重开（registry 自述场景 100% 假阳性 bizFatal） | nop-duckdb DuckDbEngine |
| 2 | W-02/C6-002 | open/close 竞态：checkClosed→慢速 connect 窗口内 close() 早退，track 迟到连接进已关闭引擎——native 句柄+单写者文件锁泄漏（IDuckDbEngine L16 契约明文违约） | nop-duckdb DuckDbEngine |
| 3 | W-03/C6-R2-002 | materialize CTAS 无 table-exists 守卫（对照 readExternal 有）——CTAS 提交后失败留孤儿表，非 bizFatal 可重试→持久 dbPath 重试/WI4 恢复重放永久必败（F2 半个成功+F3 守卫漏配） | nop-duckdb DuckDbSqlTaskStep |
| 4 | W-04/CS-001 | LeidenDetector runWithTimeout 超时幽灵线程：CWTS 1.3.0 全库零 interrupt 引用（反汇编取证）shutdownNow 必然无效，非 daemon 线程未命名不可辨识；5 个调用点零规模守卫且 7 个 @BizQuery 请求线程同步触发（2.1 移交项 C6-CL-031 边界变化闭环并入） | nop-graph-core LeidenDetector |

## 盲区自评

- 覆盖零缺口（33f 全覆盖核验通过，三源码根逐字吻合）；反向钉定 0。
- 会红 0/19 第 9 例；小单元测试基建同型（顺序形态+零并发断言）。
- C1 深挖第 3 轮豁免（跨维度重述+清扫全闭）留痕并经复核确认——处置透明。

## 与其他 roadmap 的重叠

- 与 WI 1.1–5.5 台账零同站点重复；家族关联 3 组单列：账本族↔F4 无界账本族（1.1/4.5/5.1/5.5 先例）、不可中断族↔5.5 W-01 超时族后果层+2.3 CL-022 Delayer、CTAS 必败↔F2/F3/错误以成功交付对偶族。
- security-audit roadmap：无重叠面。
