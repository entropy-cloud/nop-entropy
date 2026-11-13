# 深度审核汇总报告 — nop-code 索引面（core + flow + service）并发审计（CRA WI 6.3）

## 基本信息

- **审核单元**: `nop-code-index`（116f/16,898 lines——nop-code-core 51f/3,865 + nop-code-flow 12f/1,840 + nop-code-service 53f/11,193）
- **审核日期**: 2026-10-23（初审/深挖/复核/C10/合并裁定/汇总收口同日完成）
- **执行维度**: C1 共享可变状态、C5 内存有界性、C6 资源生命周期、C7 长时运行稳定性（roadmap 6.3 裁剪集）+ C10；C2/C3/C4/C8/C9 overlay
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 7b0ccc89d4；nop-code-api/lang-*/dao/app/meta/codegen 归 6.4
- **方法论**: 盲审初审（4 批串行）→ 深挖（2 合并批）→ 独立复核（2 合并批）→ C10 → 合并裁定
- **Plan**: `ai-dev/plans/2490-cra-wi6.3-nop-code-index.md`（R1 agent_04f01b2d 1 Blocker[规模数字订正 116f/16,898]+2 Major[ProjectAnalyzer executor 预留未用/2026-09 G11 预登记]+3 Minor 全修）
- **Protected Area**: 零 api 叶（nop-code-api 归 6.4）
- **跨单元闭环**: ① **2026-09 G11-15-02 承接**（SymbolTable×CodeCacheManager 数据竞争 P2）——三态裁定=仍成立→C1-001 吸并（36 站点×5 读类超集，防双计数）；② **5.6 W-04 消费面边界裁定**——C7-001（自愈物化无单飞）为独立缺陷+交叉引用（5.6 仅立 detector 本体与调用面守卫不对称，单飞/退避未立案）
- **执行说明**: 全部子代理严格串行派发（初审 4 + 深挖 2 + 复核 2 + C10 2 + 裁定 1 = 11 派）

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 5 | +0（收尾穷举） | 5 | 0 | 0 |
| C5 | 2 | 9（P1×2） | +7 | 16 | 0 | 0 |
| C6 | 2 | 10 | +1 | 11 | 0 | 0 |
| C7 | 2 | 11（P1×1） | +1 | 12 | 0 | 0 |
| C10 | 1 | — | — | 44 行矩阵 + 缺保护 41 | 0 | 0 |
| **合计** | — | **35** | **+9 = 44 实体** | **44 全保留 → 40 工单** | 0 | 0 |

## 合并裁定

- 44 实体 → 复核全保留（0 驳回 0 降级）→ 吸收/别名 5 → **40 工单 = P1×3 / P2×16 / P3×21**（维持 40/跨维度调整 4/移交 0；裁定级新增归并 C6-007→C5-007 LlmEdgeCache 同站点三重登记防双计）。
- **P1×3 全走 7.1 台账**：W-01 C5-001 公开图查询三动作每请求全量物化 4 关系表（守卫声称不存在）；W-02 C5-002 ProjectAnalyzer 死守卫+全仓库 sourceCode 驻留（admin-only 下限注）；W-03 C7-001 读路径自愈物化无单飞→并发 Leiden 重算风暴（与 5.6 W-04 独立+交叉引用；TP-001~003 验收前置）。
- 别名归并：git 超时族主 C6-001（吸 C7-003）、搜索双写族主 C7-005（吸 C6-005）、C7-011→C6-009、C7-006 拆解（(a)独立/(b)交叉 C1-003+5.6/(c)归 C6-003）。

## 按严重程度分布（工单级）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 3 | 见上 |
| P2 | 16 | SymbolTable 混合同步（吸 G11-15-02）、缓存失效次序、git 超时族、clone 残留毒化、shard_ledger 漏删击穿守卫、单页截断孤儿行、搜索双写非原子、集群工作区零 retention、已删文件依赖边残留、物化读装配 finder 无 LIMIT 族等 |
| P3 | 21 | 各尾部条目（含 LlmEdgeCache 无界三重登记归并、全局锁内重建粒度、RPC 无超时 dormant 等） |

C10 缺保护登记 41 项：矩阵 **会红 0/44**——campaign 第 12 例；TestConcurrentIndexing 定性=真并发执行形态+功能冒烟断言（非并发正确性测试）；P1×3 全无保护；C7-009 断开的现成契约测试（Orm 子类接入一行即红）为最高性价比补测项。

## P0 记录

本单元 **P0 = 0**（显式空执行）。全链无 P0 判级。

## 现状标注摘要

- 44 实体中 2026-09 G11 承接 1 条（其余全首报）；自愈面正面项已核对登记（analyze-before-delete/失败文件指纹保留/单事务原子回滚/缓存三处有界）。
- 缺陷密度热点：**公开查询面无界物化族（C5）与长驻索引一致性（C7）**为本单元双主战场；wiring 双口径验证 3 站点未接线（ShardBuildCoordinator/ClusterWorkspaceManager/LlmSemanticEdgeExtractor）。
- owner doc drift：发现级 1 项（nop-code.md 未声明 LlmEdgeCache 边界）。

## 关键发现摘要（P1 全清单）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | W-01/C5-001 | 公开图查询三动作（surprise/questions/wiki）每请求全量物化 4 关系表（CodeRelationGraphLoader 自述 "Deliberately unbounded"，声称守卫不存在）+ GraphMetricStore.findByMetric 无 LIMIT——并发叠加无护栏 | nop-code-service graph 面 |
| 2 | W-02/C5-002 | ProjectAnalyzer 输入面守卫全部失效：MAX_FILE_SIZE_BYTES/MAX_FILE_COUNT 声明即死代码（全单元零引用）+「流式」BatchQueue 消费端全量累积——全仓库 sourceCode 驻留巨事务（admin-only 触发为 P1 下限注） | nop-code-core analyzer |
| 3 | W-03/C7-001 | 读路径自愈物化无单飞——并发首触触发 Leiden 重算风暴（与 5.6 W-04 幽灵线程叠加面 6 触发点+4 守卫空洞；5.6 立 detector 本体，本条立消费侧单飞/退避缺失，独立+交叉引用） | nop-code-service impl 面 |

## 盲区自评

- 覆盖核验通过（116f/16,898 四维精确吻合、缺口 0）；反向钉定 0（2 处掩蔽/watch 注记）。
- 会红 0/44 第 12 例；真并发测试存在（TestConcurrentIndexing/TestProjectAnalyzerConcurrency）但均为冒烟断言——「并发执行≠并发验证」形态与 6.2「只向后看」区分登记。
- R1 拦截规模 Blocker 1 处（service 计数误加 flow）——数字链经 R1 订正后全程无返工。

## 与其他 roadmap 的重叠

- 零同站点重复；家族关联单列：git 子进程超时族（对照 6.2 W-01 外部进程桥/4.3 C3-024）、无界物化族（对照 5.4 C5-001/5.6 W-01）、自愈单飞族（对照 4.5 C4-001 周期停摆/取消通道族）、G11 承接链（2026-09 审计闭环）。
- security-audit roadmap：无重叠面。
