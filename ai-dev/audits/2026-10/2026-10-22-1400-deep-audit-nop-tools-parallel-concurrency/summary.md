# 深度审核汇总报告 — nop-tools 并行面（nop-rg + nop-find + nop-bytecode）并发审计（CRA WI 6.1）

## 基本信息

- **审核单元**: `nop-tools-parallel`（84f/6,796 lines——nop-rg 43f/3,274 [含 benchmark 8f/742 计数面] + nop-find 14f/901 + nop-bytecode 27f/2,621；立案面 ≈35f/2,532）
- **审核日期**: 2026-10-22（初审/深挖/复核/C10/合并裁定/汇总收口同日完成）
- **执行维度**: C1 共享可变状态、C4 线程池与异步执行、C6 资源生命周期、C9 并发高性能（roadmap 6.1 裁剪集）+ C10；C2/C3/C5/C7/C8 仅 CL 线索（无独立抽查批）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 5e84b88a37；nop-rg-benchmark 按误报校准不立案
- **方法论**: 盲审初审（4 批串行）→ 深挖（2 合并批）→ 独立复核（2 合并批）→ C10 → 合并裁定
- **Plan**: `ai-dev/plans/2488-cra-wi6.1-nop-rg-find-bytecode.md`（R1 agent_23c1e694 0 Blocker、1 Major+4 Minor 全修）
- **Protected Area**: 无 api 叶（R1 实测）；W-01~W-17 全部 impl 面
- **执行说明**: 全部子代理严格串行派发（初审 4 + 深挖 2 + 复核 2 + C10 2 + 裁定 1 = 11 派）；**campaign 首个零 P1 单元**

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 2 | +1 | 3 | 0 | 0 |
| C4 | 2 | 3 | +2 | 5（1 并入 C1-001） | 0 | 0 |
| C6 | 2 | 5 | +1 | 6 | 2（C6-001/002） | 0 |
| C9 | 2 | 4 | +2 | 6（C9-R2-001/C9-004 合面） | 1（C9-001） | 0 |
| C10 | 1 | — | — | 20 行矩阵 + 缺保护 10 | 0 | 0 |
| **合计** | — | **14** | **+6 = 20 实体** | **20 全保留 → 17 工单** | 3 | 0 |

## 合并裁定

- 20 实体 → 复核全保留（0 驳回）→ 吸收 3 → **17 工单 = P0×0 / P1×0 / P2×4 / P3×13**（维持 17/跨维度调整 3/移交 0）。
- **campaign 首个零 P1 单元**；P2×4 全走 7.1 裁决台账：W-01 patternBytes 惰性缓存族（C1-001 主，吸 C4-003/CL-301）、W-03 FFM×外部截断 TOCTOU（C1-R2-001，同机复现取证）、W-05 invokeAll 首错弃全（C4-002——CLI cause 不可见实锤）、W-14 bytecode JarFile 逐 artifact 重开（C9-002）。
- 取消族并卷：W-06（C4-R2-001 主案 + C6-002 对齐降 P3，附嵌入前提回升 P2 条款）。
- 降级 3 项复核裁定：C6-001（chunkSize 不可注入恒默认 256MiB）、C6-002（对齐取消族）、C9-001（二阶量化自注可降）。

## 按严重程度分布（工单级）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 0 | —（campaign 首例） |
| P2 | 4 | patternBytes 数据竞争、FFM×截断 TOCTOU（InternalError 复现）、invokeAll 首错弃全（与 rg 契约背离）、JarFile O(N×central-dir) |
| P3 | 13 | 取消通道族、arena 累积包络、LineCursor 行级水位族、Glob 回溯、CollectManifest 非原子写、池命名、--jfr SIGINT、测试卫生等 |

C10 缺保护登记 10 项：矩阵 **会红 0/部分 7/不会红 13**——campaign 第 10 例，**「部分保护」为本单元特色**（LargeFileSearchTest/ParallelFileWalkerTest 相邻面钉只防回归侧）；全部测试为功能语义断言，并发/资源/热路径缺陷面零直接断言；3 项建议「先红后绿」。

## P0 记录

本单元 **P0 = 0**（显式空执行）。全链无 P0 判级。

## 现状标注摘要

- 20 实体全部为首报（cra-index 对三模块 0 提及）。
- FFM/Arena 面审计结论为正面：confined arena + TWR 主动设计、零 owner-thread 兜底依赖、SIMD 三层 fallback 一致——roadmap 点名的「FFM/Arena 并行遍历」实现质量良好，缺陷集中在**聚合语义与取消通道**而非 FFM 本体。
- owner doc drift：nop-rg/nop-find 无 owner doc（占 62% 行数）。

## 关键发现摘要（P2 全清单，本单元无 P1）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | W-01/C1-001 | SearchCommand.patternBytes 惰性缓存非原子 check-then-act+无安全发布——跨线程复用 command 可静默产生错误搜索结果 | nop-rg SearchCommand |
| 2 | W-03/C1-R2-001 | FFM 映射段存活期×外部文件截断 TOCTOU——JDK 26 InternalError（同机复现）经 invokeAll 首错弃全层丢失全部结果 | nop-rg MappedFileReader×聚合层 |
| 3 | W-05/C4-002 | invokeAll 首错全弃——单文件瞬时错误（TOCTOU 删除）→ 整次搜索 exit 2 零结果，与 rg「报错继续」背离且 search 侧契约未声明 | nop-rg SearchCoordinator |
| 4 | W-14/C9-002 | nop-bytecode read-back 逐 artifact 重开 JarFile O(N×central-dir)——30s 级量化链 | nop-bytecode NopBytecodeMain |

## 盲区自评

- benchmark 计数面>立案面口径已声明（8f/742 计入不立案）；反向钉定 0。
- 会红 0 第 10 例；「部分保护 7」为特色形态（相邻功能钉存在但缺陷面正交）。
- C10 复核修正 2 处（truncat 命中数/P3 聚合漏 C6-R2-001 补记）。

## 与其他 roadmap 的重叠

- 零同站点重复；家族关联 4 组单列：F6 非原子落盘族（C6-R2-001）、TOCTOU 族（W-03/W-05——与 5.3 W-01 判-删族相邻：读侧窗口 vs 写侧判-删）、聚合弃全族（W-05——3.3 C3-001 终态冲刷截断对偶面）、取消通道族（W-06——3.1 C3-011/4.3 C3-002/5.6 W-04 先例）。
- security-audit roadmap：无重叠面。
