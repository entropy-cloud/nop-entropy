# 深度审核汇总报告 — nop-stream 编排薄层并发·异步·资源专项审计（CRA WI 1.3）

## 基本信息

- **审核单元**: `nop-stream-orchestration-thin`（nop-stream-flow 60 files/6,202 lines + nop-stream-sql 17/4,334 + nop-stream-fraud-example 21/3,256，合计 98 files/13,792 lines；**手写口径，不含 `_` 前缀生成文件**）
- **审核日期**: 2026-10-14
- **执行维度**: C1 共享可变状态、C3 异步链路、C6 资源生命周期（全维度）+ C2/C4/C5/C7/C8/C9 抽查批次（单合并文件，操作化见 plan 2463「抽查批次裁定」）+ C10 测试保护力（C9 全维度按 roadmap 1.3 裁剪入抽查）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 8dcfa4cd0c（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2463-cra-wi1.3-nop-stream-orchestration-thin.md`（两轮对抗性审查通过后执行）
- **执行说明**: 全部批次串行派发（运营者要求，一次一个子代理）；对审计 prompt 归档规则「每维度一文件」的一次豁免（抽查批次合并单文件，按维度分套复核结论），依据 roadmap 1.3 抽查裁定。

## 执行统计

| 维度/批次 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|----------|---------|---------|---------|-------------------|------|------|
| C1 | 2 | 7 | +0 | 7 | 0 | 0 |
| C3 | 3 | 7 | +2（C3-008/009 均 P1） | 8 | 1（C3-001→P2） | 0 |
| C6 | 4 | 5 | +2（C6-006/007 均 P1） | 5 | 1（C6-001→P2） | 1（C6-006 sharded 源逐 run 新实例证伪） |
| 抽查批次（C2/C4/C5/C7/C8/C9） | 2（C2/C7/C9） | 6 | +1（C9-004） | 7 | 0 | 0 |
| C10 | 1 | —（输入=复核后缺陷清单） | — | 8（缺保护登记，全 P2） | 0 | 0 |
| **合计** | — | **25** | **+5 = 30** | **29（含降级 2；27 原判保留 + 2 降级）+ 8 缺保护** | 2 | 1 |

全部维度均有显式终止声明（深挖轮次上限 10 轮内收敛，无触顶）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 29 条保留缺陷 → 合并后 **20 项 = 6 多成员缺陷族（MA-01~MA-06）+ 14 独立立案**；自洽核对 15 入族 + 14 独立 = 29 ✓，4+10+6 = 20 ✓。
- 与复核提议不一致裁定：0 处推翻；5 处有证据的追加动作（4 处追加归并 C3-002/003/004/006 + 1 处分类学精化 MA-02 同族相邻）。
- 跨单元对账：与 WI 1.1（10 条 P1）+ WI 1.2（10 条 P1）台账**零重复立项**；4 条跨单元关联单列（C3-009→core ProcessOperator 覆盖空洞、C3-008→runtime restartRegion Phase-3 编排面、C1-003/C6-007→恢复路径、C3-005 缺陷类关联）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 4 | MA-01 UDF 注册表竞态（逐记录无锁读×任意线程写+运行期静默换绑）、C3-008 region 重启游标清零→恢复重放、C3-009 共享 UDF 键控状态跨子任务系统性收敛单一 backend、C6-007 cancelled 标志无复位→重启零事件假成功 EOS |
| P2 | 10 | sideInput 冻结快照零强制（MA-02 三失效面）、ReplayEngine 失败伪装 EOS（MA-03，可用而未用的 pendingError 通道）、ReplaySubscription 取消契约三重偏离（MA-04）、进程级静态缓存无上界（MA-05 双载体）+ ROOT_SCOPE 无守卫、CAS 守卫非粘性、build() 就地改写、GETTER_CACHE 锁倒置隐患、resolver close 空壳、subscribe 无门 |
| P3 | 6 | BETWEEN/CASE 重复求值（MA-06 双通道）、惰性初始化良性竞争、活视图冒充快照、取消无中断响应点、双跳 scope 链、重复缓存查找 |

C10 另有缺保护登记 8 条（全 P2，与缺陷族映射见 merge-adjudication.md §4.5）：6 推演组缺陷本体会红 **0/6**——恢复/重跑测试全落全新实例、E2E 全 P=1、零失败注入三个结构性盲区叠加；C10-002（restore-vs-run）与 C10-004（run 中 cancel 后同实例重跑）为 P1 修复收口的最小回归面。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审与深挖阶段无 P0 立案，复核终裁亦无升级）。

## 现状标注摘要

- 全部 29 条保留缺陷均为「新发现，未修复」（本单元无前置历史审计覆盖；WI 1.2 曾三次将 fraud-example 登记为范围外；基线 b3a9c76bed 后无触及本单元 src/main 的提交）。
- 跨单元同族对照：C1-001 与 WI 1.1 已裁启动路径豁免的 setDefaultFactory 形态不同；C3-009 根因载体（ProcessOperator 共享 RichFunction 契约）在 WI 1.1 范围外未覆盖。
- 文档漂移移交：owner doc `nop-stream-sql.md` §3a「编译期一次解析（非逐记录查找）」与 live 注册表路径不符（MA-01 附属 docs bug）；WI 1.2 遗留的 DirectoryFileSourceFunction「非 REPLAYABLE」表述与 live REPLAYABLE 返回值不符（归 docs 维护）。

## 关键发现摘要（P1 全清单，详见 merge-adjudication.md §4.2）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C1-001（MA-01） | UDF 注册表非线程安全：逐记录 call() 无锁读 × 公开 register() 任意线程写，运行期静默换绑 | nop-stream-sql 函数解析 |
| 2 | C3-008 | region 重启 Phase-3 流水线下兄弟 initializeState REPLACE 清零已推进游标 → 恢复重放已反映窗口 | fraud CDC 源状态语义（编排面关联 runtime） |
| 3 | C3-009 | 共享 UDF × 各子任务 open() 覆写共享 RuntimeContext → 并行 ≥2 键控状态收敛单一 backend | fraud UDF 状态（根因载体 core ProcessOperator） |
| 4 | C6-007 | cancelled volatile 跨 run 永不复位 → 区域重启共享实例零事件静默 EOS 假成功 | fraud 文件源生命周期 |

## 盲区自评

- `_gen/` 生成类按纪律排除；flow `model/` 36 个小文件（≤5 行有效代码）经兜底清查判定无资源/并发面。
- fraud-example 按「暴露生产代码缺陷或违反 owner doc 已声明契约」口径立案——本单元 4 条 P1 中 3 条实为 fraud-example 的生产化代码缺陷（该模块承载 CDC replay 契约载体），立案边界在复核阶段经逐条检验。
- 抽查批次（C2/C4/C5/C7/C8/C9）为定向核对而非全量盘点：站点圈定依据（基线节 + 补充 grep + 热路径路径）与未盘点面声明见 `C2-C9-spot-checks.md` 抽查范围节；C7 的 metrics/账本面在三模块零命中（0 metric 站点）为单元特性而非盘点缺口。
- C9 全维度裁剪入抽查（roadmap 1.3 裁定）：热路径性能面未做全量扫描，已登记基准缺口 3 项（scope 分配、sql 求值管线、水位逐事件体）。
- C3-008/C1-003/C6-007 的触发时序依赖 nop-stream-runtime/core 的恢复编排语义（跨单元载体），修复设计需与对应模块维度协同（已在跨单元关联登记）。

## 与其他 roadmap 的重叠

- nop-stream-independent-audit-roadmap（流引擎专项）：C3-009 根因载体 ProcessOperator、C3-008 编排面 restartRegion 属该 roadmap 的 core/runtime 范围，7.1 合并裁定时 cross-reference（本单元零重复立项已对账）。
- security-audit / full-code-review roadmap：无重叠面。
