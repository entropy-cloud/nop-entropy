# 208 差量空间线性化：谱定理与主差量定理（第六篇）

> Plan Status: completed
> Last Reviewed: 2026-08-26
> Source: 用户要求"深挖 Koopman 研究路线，找真的非平凡的定理证明"；DMD 主模态 = Perron–Frobenius 谱内容的 GRC 对应
> Related: 系列前五篇（transport theory / D_tr / 塔分类 / Koopman 深层 / 分离合并）

## Purpose

Koopman 谱理论阶段（Mezić/DMD）的真正内容需要**线性结构**——GRC 差量空间是幺半群，缺线性。本文做关键一步：**线性化** `V(D) := K(D) ⊗_ℤ ℝ`（Grothendieck 群化的实系数基变换），使传输成为线性算子 `T̄`，然后证明：

1. **线性化函子定理**：`(G,T) ↦ T̄` 是 ΔAct → Vec 的函子；T̄ 保正锥（正性自动）；不可传输差量无像（线性化边界 = D_tr）。
2. **主差量定理（Perron–Frobenius 的 GRC 版）**：字典传输封闭（每个字典差量的传输可表为字典内差量的积）⟹ 非负矩阵 M ⟹ 存在主差量 d*（正本征向量）与定制放大率 ρ（谱半径）——**DMD 主模态的 GRC 对应物**；不可约 ⟹ 唯一正、本原 ⟹ 迭代收敛到主分量。
3. **谱半径 = 增长率定理（Gelfand）**：ρ = 字典重数的 m 步指数增长率——沿生成链第 m 级的差量多集规模以 ρ^m 增长；与轨道/望远镜理论的衔接。
4. **特征差量分解（DMD 模态分解对应）**：本原情形下任意字典组合的长程行为 = 主分量 + 瞬态——升级迁移评估的定量工具。

诚实台账：PF/Gelfand/群化均为搬运；识别（字典封闭条件、ρ = 定制放大率、d* = 主定制模式、线性化丢非交换信息的对偶代价）为本文贡献。

## Current Baseline

- 已成立：传输 T 是幺半群同态（第一篇引理 2）；ΔAct 函子性（第一篇定理 2）；D_tr/核商分解（第二篇）；望远镜/导数函子（第四篇）；Grothendieck 群化对应（第五篇 §1.3）。
- 已核查空白：仓库无任何"差量空间线性化/谱半径/Perron–Frobenius/主差量"内容（本文开工前将 grep 复核）。

## Goals

- 文章 `ai-dev/articles/grc-delta-space-linearization.md`：四个定理 + 线性化构造 + 工程推论 + 诚实台账。

## Non-Goals

- 不处理非平稳/概率化的谱理论（开放问题）；不做数值实验。
- 不改代码、docs-for-ai/、docs/theory/。

## Scope

### In Scope

- `ai-dev/plans/208-grc-delta-space-linearization.md`
- `ai-dev/articles/grc-delta-space-linearization.md`
- `ai-dev/logs/2026/08-26.md`

### Out Of Scope

- 代码；docs-for-ai/；docs/theory/

## Execution Plan

### Phase 1 - 草稿与审计迭代

Status: completed
Targets: `ai-dev/articles/grc-delta-space-linearization.md`

- Item Types: `Proof`

- [x] 新颖性 grep 复核（谱半径/Perron/主差量/向量空间线性化——仓库零命中，仅"线性化"作链定序义）
- [x] 草稿：V(D) 构造、T̄ 定义、四定理与证明
- [x] 自细化第一轮（d* 为模式非单个差量的说明、K(D) 关系良定义、PF 三档条件）
- [x] 独立审计第一轮（ses_fc157027fffebWic2bnO8pm1hk）：定理 1-3 + 推论数学全部成立；1 实质 FAIL（V(D_tr)/V(Ker(T)) ≅ V(Im(T)) 在幺半群一般情形为假，ℕ² 反例）已修为恒真版本 + 反例 + 群条件；4 minor + 1 措辞全部修复
- [x] 自细化第二轮 + 独立审计第二轮（ses_fc14df1f7ffeLJy171If2SPEf7）：7 项全 PASS，无新错误，可发布
- [x] 诚实台账（线性化丢非交换信息、正性自动、有限性是真假设）+ 开放问题

Exit Criteria:

- [x] 四定理经独立审计无实质缺陷（两轮：线性化函子/主差量 PF 三档/增长率 Gelfand/模态分解全部成立）
- [x] PF/Gelfand/群化的引用与条件精确（弱 PF 不要求不可约；正性需不可约；收敛需本原；ℕ² 反例互证）
- [x] 线性化丢非交换信息的代价如实标注（交换影子、与塔结构互补）
- [x] 引用真实（前五篇编号、Perron 1907/Frobenius 1912/Gelfand 1941/Schmid 2010/Mezić 2005/Oseledets 1968 经核验）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 条目已更新

## Closure Gates

- [x] 文章成文且证明经审计收敛
- [x] 文本一致性：Plan Status / Phase Status / Exit Criteria / Closure Gates / log
- [x] 独立 closure audit 完成并写入 Evidence
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### 非平稳/概率化谱理论

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要随机矩阵乘积理论（Oseledets），是独立课题。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 数值实验（对真实生成链计算传输矩阵与 ρ）。
- 用户裁定正式化路径。

## Closure

Status Note: 按"草稿 → 自细化 → 两轮独立审计"流程完成。本文完成 Koopman 谱理论阶段（Mezić/DMD）的 GRC 落地：差量空间线性化 V(D)=K(D)⊗ℝ 使传输成为线性算子，Perron–Frobenius 给出主差量与定制放大率 ρ（DMD 主模态对应），Gelfand 给出 ρ 的增长解释。审计循环纠出一处实质错误（谱商分解在幺半群一般情形的错误同构声明，被 ℕ² 反例证伪，修为恒真版本 + 群条件）。
Completed: 2026-08-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（pangu，两轮 fresh session）
- Audit Session: ses_fc157027fffebWic2bnO8pm1hk（第一轮：定理 1-3 + 推论数学全部成立；1 实质 FAIL + 4 minor + 1 措辞）、ses_fc14df1f7ffeLJy171If2SPEf7（第二轮：7 项全 PASS）
- Evidence:
  - Exit Criteria 逐条：定理无实质缺陷 ✓；PF/Gelfand 条件精确（弱 PF/不可约/本原三档 + ℕ² 反例与恒真版本互证）✓；丢非交换信息如实标注 ✓；引用真实 ✓；No owner-doc update ✓；日志已更新 ✓。
  - Closure Gates：成文与审计收敛 ✓；文本一致 ✓；独立 audit 证据在本段 ✓。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/208-grc-delta-space-linearization.md --strict` 退出码 0。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
  - Anti-Hollow：纯文档计划，不适用。
  - Deferred 分类：非平稳/概率化谱理论为 out-of-scope improvement（开放问题 1），无降级。

Follow-up:

- 非平稳链 Oseledets 谱、可约字典分块谱、数值实验（开放问题 1-3）。
- 用户裁定正式化路径。
