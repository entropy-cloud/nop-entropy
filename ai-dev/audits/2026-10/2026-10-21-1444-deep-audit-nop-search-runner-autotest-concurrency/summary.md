# 深度审核汇总报告 — nop-search + nop-runner + nop-autotest 并发·异步·资源专项审计（CRA WI 4.5）

## 基本信息

- **审核单元**: `nop-search-runner-autotest`（3 模块 8 有效源码根 112 files/11,116 lines src/main 口径：nop-search api 8f/652+core 4f/324+lucene 5f/1,586、nop-runner cli-core 29f/2,447+cli 1f/11+tool 11f/795、nop-autotest core 47f/4,822+junit 7f/479；dbtool 零主源码显式登记；`_gen/` 残留 0）
- **审核日期**: 2026-10-21
- **执行维度**: C1 共享可变状态、C4 线程池治理、C6 资源生命周期（roadmap 4.5 裁剪集）+ C10 测试保护力（C2/C3/C5/C7/C8/C9 不在维度集，overlay 线索 CL×12）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit d3b05606de（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2481-cra-wi4.5-nop-search-runner-autotest.md`（两轮对抗性审查通过后执行；R1 agent_fdefed75 2 Major + 4 Minor 全修——dbtool 零主源码登记/autotest 并行执行预注失真订正[零 ExecutorService/真 Thread]/锚点底稿；R2 agent_8035e4d0 0 Major 置 active）
- **执行说明**: 全部批次串行派发（运营者要求）——C1+C4+C6 合并批单次派发 + 深挖/复核合并任务单次派发 + C10/裁定合并任务单次派发；**并发面极稀疏**（10/112 文件原语命中——预校准成立未凑数）；**owner doc 零覆盖**（module-groups.md 对 nop-search 零提及，按 drift 登记）。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 7（P2×4/P3×3） | +2（P3×2） | 9 | 0 | 0 |
| C4 | 2 | 5（**P1×1**/P2×2/P3×2） | +2（P2×2） | 7 | 0 | 0 |
| C6 | 2 | 6（P2×4/P3×2） | +2（P2×2） | 8 | 0 | 0 |
| C10 | 1 + 独立复核 | —（输入=复核后缺陷清单） | — | 24 行矩阵全维持 + 缺保护 9（全 P2） | 0 | 0 |
| **合计** | — | **18** | **+6 = 24** | **24** | 0 | 0 |

R2 深挖三维度全部显式终止（+6 全 P2/P3 且收敛证据齐备——载体清单收敛、异步站点总账闭环、六深挖方向三面核对通过）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 24 条保留缺陷 → 合并后 **15 张工单 = 5 缺陷族（MA-01…05，吸收 14 条）+ 9 独立立案**；watch-only 1（C4-005）。逐条落位无遗漏。
- 工单判级：**P0×0 / P1×1 / P2×6 / P3×7 + watch-P3×1**。
- 冲突裁定 3 处（任务输入分组与证据出入，均按证据裁定并留痕）。
- 跨单元对账：与 cra-index WI 1.1–4.4 台账（156 条 P1）**零同站点重复**；家族关联 3 处单列（C4-001↔WI 2.1 C3-001 周期停摆族、C6-003↔WI 2.2 C1-009 destroy-vs-rebuild 族、C6-002↔F4 无界账本五案族）；移交触点 8 处均首报。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | —（即时通道显式空执行） |
| P1 | 1 | C4-001 CliRunCommand scheduleWithFixedDelay Future 丢弃——周期任务任一次脚本异常即静默停摆且进程挂在 stdin（F9 定向命中） |
| P2 | 6 | MA-01 Lucene 四资源族（indexDirs/indexWriters/searcherManagers 竞态 + 只增不逐出）、MA-02 watch 注销族、MA-03 zip 产物损坏族、MA-04 Mock 替身族、C4-006 Mock 取消语义缺失、C4-007 watch 模式注入路径 |
| P3 | 7 | MA-05 autotest 清理对称族 + I-C1-006/I-C1-007/I-C1-008/I-C4-004/I-C6-005/I-C6-006 |

C10 另有缺保护登记 9 条（全 P2，覆盖 24/24 闭合，全部映射随本体工单）：保护力矩阵 24 行**会红 0/假保护 12/无测试 12**——P1×1 全部无保护；反向钉死 0 硬钉 + 1 正向修复约束（S-04 钉 rollback 自愈）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 24 条保留发现全部为**新发现**（cra-index WI 1.1–4.4 台账对三模块零条目——本单元首审）；已知未修复 0、已修复 0。

## 关键发现摘要（P1 清单，详见 merge-adjudication.md）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C4-001 | CliRunCommand scheduleWithFixedDelay 返回 Future 丢弃——周期任务任一次脚本异常即静默停摆且进程挂在 stdin（F9 定向命中；watch 模式按实际触达面判级） | nop-runner/nop-cli-core |

## 盲区自评

- C2/C3/C5/C7/C8/C9 为 roadmap 4.5 裁剪维度：仅 overlay 线索登记（CL×12：并入工单 2/独立线索 1/关闭确认 1/移交 8/驳回 0），未做完整口径审计；若 7.1 需完整面需补审。
- dbtool 零主源码显式登记（生产本体归 nop-dbtool-core WI 2.5 已审）。
- autotest 并行执行面在消费方 JUnit 平台配置，不在本单元源码——预校准成立。
- SyncThreadPoolExecutor 严守 usage-site only（本体归 WI 2.1）。
- R2 深挖三维度全终止（+6 全 P2/P3），无口径偏差。

## 与其他 roadmap 的重叠

- full-code-review-roadmap：无新增重叠面。
- 与 WI 1.1–4.4 台账（156 条 P1）零同站点重复；家族关联 3 处单列 + 移交触点 8 处均首报。
- security-audit roadmap：无重叠面。
