# 深度审核汇总报告 — nop-rule + nop-metadata 并发·异步·资源专项审计（CRA WI 5.5）

## 基本信息

- **审核单元**: `nop-rule-metadata`（2 模块 314f/34,352 lines，src/main/java 排除 `_gen` 目录口径——nop-rule 72f/5,221 [api 16f/1,154 + core 34f/3,076 + dao 13f/612 + service 8f/341 + app 1f/38] + nop-metadata 242f/29,131 [api 30f/1,747 + core 2f/636 + dao 81f/1,527 + service 128f/25,189 + app 1f/32]）
- **审核日期**: 2026-10-22（初审/深挖/复核/C10/合并裁定/汇总收口同日完成）
- **执行维度**: C1 共享可变状态、C4 线程池与异步执行、C5 内存有界性、C7 长时运行稳定性（roadmap 5.5 裁剪集）+ C10 测试保护力；C2/C3/C6/C8/C9 overlay 线索形态
- **目标范围**: `src/main/java` 全部手写代码（`_` 前缀生成常量文件在口径内保留）；基线 commit 81a7c228e7
- **方法论**: 盲审初审（按功能组批 A-D，7 批次串行）→ 深挖迭代（C1 一轮终止/C4、C5 各一轮/C7 四轮收敛）→ 独立复核 → C10 → 合并裁定
- **Plan**: `ai-dev/plans/2486-cra-wi5.5-nop-rule-metadata.md`（R1 agent_dc62f129 2 Major+4 Minor 全修；R2 agent_14a72e20 0 Blocker/0 Major）
- **Protected Area**: nop-rule-api 16f/1,154 + nop-metadata-api 30f/1,747 plan-first——审计只读不受限，**0 张工单触碰 api 叶**
- **执行说明**: 全部子代理严格串行派发（初审 7 + 深挖 5 + 复核 4 + C10 2 + 裁定 1 = 19 派，逐条记录于 dispatch-log）；roadmap 点名四件套（cron 调度/质量检查点/webhook 分发/对账）全部落 nop-metadata-service 且全部命中实质缺陷面或已防护判定

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2（第 2 轮终止） | 5（批 A+B×3 + 批 C+D×2） | +0 | 5 | 1（C1-101） | 0 |
| C4 | 2 | 2 | +2 | 4 | 0 | 0 |
| C5 | 2 | 13（两批 6+7） | +2 | 15→14 实体 | 2（C5-103/105） | 0 |
| C7 | 4（R2 触发 R3、R3 触发 R4，R4 终止） | 10 | +5 | 15 | 1（C7-R3-001） | 0 |
| C10 | 1 | — | — | 38 行矩阵 + 缺保护 38 | 0 | 0 |
| **合计** | — | **30** | **+9 = 39 立案** | **38 实体（C5-106 并入）** | 4 | 0 |

## 合并裁定

- 39 立案 → 38 实体 → **29 工单 = P1×3 + P2×9 + P3×17**（7 缺陷族主 + 22 独立，9 条吸收；三态：维持 29/跨维度调整 9/移交 0）。
- **P0×0 显式空执行**；P1×3 = 3 独立缺陷，全部走 7.1 台账：W-01 外部 JDBC 无超时族（C7-102 主 + C7-001 次 + C4-R2-001 跨维关联——判级暂计 P1、C4 侧 P2 分歧登记 7.1）；W-02 retention 族（C7-004 主 + C7-101）；W-03 热更新失效族（C7-R2-001）。
- 跨维度归并 9 条：ensure* 族（C1-001/C1-102/C4-001→W-04 主 C1-102）、maxCrossDbRows 族（C1-101/C5-102→W-13）、C5-106→C5-001、C5-R2-002→C5-R2-001、C7-101→C7-004、C7-001→C7-102、C4-R2-001→C7-102 关联、lineage 族双维度合并（C5-R2-001+CL-604→W-23）。

## 按严重程度分布（工单级）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 3 | 外部 JDBC 全链无超时（查询挂死→质量监控静默停摆+checkpoint 永久锁死+调度池控制面锁死）；7 张 append-only 时序表零保留路径（F4 无界增长）；规则热更新失效传播缺口（traceDepends 只钉定义行，节点级编辑后陈旧规则无限期服务） |
| P2 | 9 | ensure* 惰性初始化族、DriverManager 全局态（反向钉定）、RuleRuntime 容器契约、检查点逐规则建连族、对账逐行重载族、外部 catalog 全量物化、同库 JOIN 无硬闸+注释契约漂移、Error 击穿自愈契约、wf.start 孤儿半程实例（降级终态） |
| P3 | 17 | maxCrossDbRows 族、lineage churn 族、各尾部条目（含降级终态 4：C1-101/C5-103/C5-105/C7-R3-001→W-12 P2） |

C10 缺保护登记 38 条全量：矩阵 **会红 0/38**——campaign 第 8 例同型确认，但形态升级：本单元**存在** 3 个真并发测试类（TestCheckpointActionDispatcherConcurrency 等），保护目标却全部是历史已修缺陷回归面（R4.3 运行标记/AR-07/MA7.5-01 等），无一覆盖本轮 38 个缺陷面；**反向钉定 1**（C1-002——TestMetaDataSourceConnectionSecurity:776 断言全局 setLoginTimeout==5，移除全局写必红，转为 W-05 同 diff 改写约束）。

## P0 记录

本单元 **P0 = 0**（显式空执行）。四维初审+深挖+复核全链无 P0 判级；两 api 叶零触碰。

## 现状标注摘要

- 38 实体全部为新发现（cra-index 对 nop-rule/nop-metadata 0 提及，跨单元查重 0 同站点重复）。
- 平台 job 执行模型 9 项盘点：7 项判「已防护」（cron 严格串行 fire/checkpointRunLocks fail-fast/webhook 30s+per-action 隔离/对账纯同步/nop-rule 全同步等）——本单元调度骨架健壮，缺陷集中在**外部依赖边界**（无超时/无保留/无失效传播）而非调度原语。
- 缺陷密度热点：nop-metadata-service quality+reconciliation 主战场（W-01/02/08/11/12 等）；nop-rule 侧仅 RuleRuntime 契约面 1 条 P2，执行面经四轮 C7 清扫零缺陷。

## 关键发现摘要（P1 全清单）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | W-01/C7-102（主） | 外部 JDBC 全链无超时：withConnection 不设 queryTimeout/networkTimeout、SimpleDataSource.setLoginTimeout 为 no-op、DriverManager.setLoginTimeout(5) 仅界建连——外部源挂起→查询线程+调度池线程永久阻塞：质量监控静默停摆（RUNNING 标记被占+tick 静默跳过）、checkpoint 永久锁死、suspend/cancel 控制面锁死 | nop-metadata-service connection/quality 面 + nop-job 平台调度交互 |
| 2 | W-02/C7-004（主） | 7 张 append-only 时序表零保留路径：QualityResult/QualityScore/Catalog/ProfilingResult/ReconResult/ModelChangedEvent（mediumtext 双快照）+ 告警工作流实例，cron+auto-score 驱动无界增长（F4） | nop-metadata-service 时序结果表族 |
| 3 | W-03/C7-R2-001 | 规则热更新失效传播缺口：DaoRuleModelLoader:84 traceDepends 只钉定义行，判定树内容在 NopRuleNode 子行且一等 CRUD 可直接改写——节点级编辑不触发定义行 updateTime，RuleModel 缓存永不失效，陈旧规则无限期服务、无日志无告警 | nop-rule 规则编译缓存链 |

## 盲区自评

- 其余 5 维度（C2/C3/C6/C8/C9）为 overlay 形态：CL 线索 26 条全确认 0 驳回，未做完整口径审计。
- **覆盖缺口登记（不补审）**：C1 维度 security 包/field 包 2 文件零具名痕迹、nop-metadata-api/core 仅隐含覆盖；C5 维度 api/core/dao/app 声明性抽查；C7 维度 nop-metadata-api 30f 无逐面痕迹；72f vs 71f 差 = `_NopRuleDaoConstants.java` 保留文件（口径已注明）。
- 会红 0/38 的结构性成因（真并发测试存在但护历史面）已登记为第 8 例同型——「测试基建只向后看」形态首次与「测试栈单线程化」区分登记。
- C4 判级分歧（C4-R2-001 P2 vs C7-102 P1 同缺陷）登记 7.1 终裁。

## 与其他 roadmap 的重叠

- 与 WI 1.1–5.4 台账零同站点重复；家族关联 4 组单列：超时族=「外部 I/O 无超时」族新成员（4.3 C3-024 先例）、append-only 族=F4 无界账本族新成员（1.4 C7-006/5.1 MA-01/2.5 C7-012 先例）、C7-R2-001=模型缓存失效传播族（2.1 C7-007 同链上下游）、C7-002=Error 逃逸绝缘族具体化站点（2.1 C3-001）。
- security-audit roadmap：无重叠面。
