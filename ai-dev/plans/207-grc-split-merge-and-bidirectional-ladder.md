# 207 正负分离合并与双向伴随阶梯（第五篇）

> Plan Status: completed
> Last Reviewed: 2026-08-26
> Source: 用户讨论（"(a,−b)+(c,−d)=(a+c,−b−d) 在范畴论中的对应"、"F(A+dA)=B+dB 双向设定可推导的结论"）
> Related: 系列前四篇（transport theory / D_tr / 塔分类 / Koopman 深层挖掘）

## Purpose

两条线成文：

1. **正负分离合并**：证明坐标载体上分离合并与直接合并的等价刻画——**差异恰为"复活坐标集" dom(p₂⁺)∩dom(p₁⁻)**（后到正覆盖先到墓碑）；直积情形分离免费（定理 A）；Grothendieck 群化对应（签名对 = 未消元形式差、投影 = 消元共等化子）；Zappa–Szép 互作用与 proof-v2 十六规则的对照（分类学，如实标注）。
2. **双向伴随阶梯**：F(A+dA)=B+dB、A+dA=G(B+dB) 设定下按假设强度递增的五个可证结论（右逆传输/传输互逆共轭残差/伴随传输恒等式/闭包不动差量/强等价），并给出 Kelly doctrinal adjunction 的框架识别。

## Current Baseline

- 已成立：公理 A1-A4、⊖、∂G_s、传输理论（定理 1/2/3）、D_tr、塔分类（interchange/Peiffer）、Koopman 深层（望远镜、导数函子）、proof-v2 十六规则（tree-delta tagged 组合表）、coordinate tombstone carrier（formal-proof 第四/五节）。
- 已核查空白：正负分离合并的等价刻画、双向伴随阶梯的五条结论均无仓库内形式化。

## Goals

- 文章 `ai-dev/articles/grc-split-merge-and-bidirectional-ladder.md`：
  - **定理 A（直积分裂）**：D ≅ D⁺×D⁻（幺半群直积）⟹ 混合链合并 = 正链预合并 + 负链预合并 + 单次投影，任意交错序投影后一致。
  - **定理 B（复活刻画）**：坐标载体上分离合并 = 直接合并 ⟺ dom(p₂⁺)∩dom(p₁⁻) = ∅；差异恰为复活坐标集（逐坐标 case analysis 证明）；树级对应 = proof-v2 规则 3（Remove⊗Merge→Replace）。
  - **定理 C（右逆传输）**：G∘F ≈ id ⟹ T_G 在 Im(T_F) 上自动存在且 ≈ T_F⁻¹ mod Ker（doctrinal adjunction 的传输转移）。
  - **定理 D（共轭残差族）**：T_G∘T_F ≈ id ⟹ (G∘F) 有恒等传输，残差 κ_A 满足 κ_{A⊕d} ≈ d⁻¹κ_A d（群情形共轭族）。
  - **定理 E（伴随传输恒等式）**：η_{A+dA} ≈ dA⁻¹ ⊗ η_A ⊗ T_G(T_F(dA))；推论：单位差量常值 ⟹ 传输互逆。
  - **定理 F（闭包不动差量）**：(G∘F)² ≈ G∘F ⟹ T = T_G∘T_F 幂等，Fix(T) 是子幺半群，其上传输互逆。
  - **定理 G（强等价）**：F∘G≈id ∧ G∘F≈id ⟹ delta-conservative（前三篇引理 3 的阶梯顶点）。
- Grothendieck 群化、Zappa–Szép、Kelly doctrinal adjunction 的范畴对应节（搬运/识别逐条标注）。

## Non-Goals

- 不做 Zappa–Szép 唯一分解的完整形式化证明（对照陈述）；不证 Eilenberg–Moore 代数的完整对应。
- 不改代码、docs-for-ai/、docs/theory/。

## Scope

### In Scope

- `ai-dev/plans/207-grc-split-merge-and-bidirectional-ladder.md`
- `ai-dev/articles/grc-split-merge-and-bidirectional-ladder.md`
- `ai-dev/logs/2026/08-26.md`

### Out Of Scope

- 代码；docs-for-ai/；docs/theory/；Zappa–Szép 完整形式化

## Execution Plan

### Phase 1 - 草稿与审计迭代

Status: completed
Targets: `ai-dev/articles/grc-split-merge-and-bidirectional-ladder.md`

- Item Types: `Proof`

- [x] 草稿：定理 A-G 陈述与证明（定理 B 的 case analysis 六类坐标、定理 C 的代表元选择与乘法性、定理 E 的两种计算路径）
- [x] 自细化第一轮——修正定理 E 推论（"单位差量常值 ⟹ 互逆"为假，改"η≈1 ⟹ 互逆 + 一般常值只给换位关系"）
- [x] 独立审计第一轮（ses_fc1713330ffexL2kcYgZbPZmPB）：定理 A-G 数学全部成立（定理 B 六类被独立重算）；14 处表述级缺陷全部修复
- [x] 自细化第二轮 + 独立审计第二轮（ses_fc1667e1bffeggHNiJhFb9h8fs）：10/11 修复确认；唯一残留（引言"按假设强度递增"与正文"部分嵌套部分独立"矛盾）已修，另修摘要措辞、台账 E 行、定理 G 引理分工
- [x] 范畴对应节 + 诚实台账

Exit Criteria:

- [x] 定理 A-G 经独立审计无实质缺陷（两轮：六类 case analysis、右逆传输、共轭残差、伴随恒等式、闭包幂等全部成立）
- [x] 定理 B 的逐坐标 case analysis 完整（六类互斥穷尽，复活坐标唯一不一致类）
- [x] 搬运/识别标注如实（Grothendieck 群化代表元/消元商映射、Zappa–Szép 对照陈述、Kelly 单位恒等特例均精确标注）
- [x] 引用真实（前四篇编号经交叉核对、proof-v2 规则 3、formal-proof 坐标载体、Kelly 1974、Zappa/Szép、HPW 2011）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 条目已更新

## Closure Gates

- [x] 文章成文且证明经审计收敛
- [x] 文本一致性：Plan Status / Phase Status / Exit Criteria / Closure Gates / log
- [x] 独立 closure audit 完成并写入 Evidence
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### Zappa–Szép 唯一分解的形式化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要 tree-delta 语法的正负分解定义与十六规则的互作用律提取，是独立课题；本文仅作对照陈述。
- Successor Required: `no`

## Non-Blocking Follow-ups

- Eilenberg–Moore 代数完整对应；Fix(T) 与 D_tr 的定量关系。
- 用户裁定正式化路径。

## Closure

Status Note: 按"草稿 → 自细化 → 两轮独立审计"流程完成。本文把用户提出的两个问题（正负分离合并的范畴对应、双向设定 F(A+dA)=B+dB 的结论阶梯）形式化为 7 个定理：定理 B（复活刻画——分离合并 = 直接合并 ⟺ 无复活坐标，六类逐坐标 case analysis）是全文最扎实部分；定理 C-G 构成 doctrinal adjunction 的差量阶梯。自细化阶段修正了定理 E 推论的方向性错误（η 常值只给换位关系，互逆需 η≈1 或中心化）。两轮审计确认数学全部成立，14 处表述缺陷 + 1 处残留矛盾全部修复。
Completed: 2026-08-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（pangu，两轮 fresh session）
- Audit Session: ses_fc1713330ffexL2kcYgZbPZmPB（第一轮：定理 A-G 数学全部成立，定理 B 六类被独立重算；14 处表述缺陷）、ses_fc1667e1bffeggHNiJhFb9h8fs（第二轮：10/11 确认，唯一残留矛盾已修）
- Evidence:
  - Exit Criteria 逐条：定理无实质缺陷 ✓（两轮，含定理 F 幂等推导的独立重建）；定理 B case analysis 完整 ✓；搬运/识别精确标注 ✓（Grothendieck 未消元代表元、Kelly 单位恒等特例）；引用真实 ✓；No owner-doc update ✓；日志已更新 ✓。
  - Closure Gates：成文与审计收敛 ✓；文本一致 ✓（引言/正文/结论三处"阶梯"表述统一）；独立 audit 证据在本段 ✓。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/207-grc-split-merge-and-bidirectional-ladder.md --strict` 退出码 0。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
  - Anti-Hollow：纯文档计划，不适用。
  - Deferred 分类：Zappa–Szép 唯一分解形式化为 out-of-scope improvement（开放问题 1），无降级。

Follow-up:

- Zappa–Szép 形式化、Fix(T) 与 D_tr 定量关系、Eilenberg–Moore 完整对应（§三开放问题）。
- 用户裁定正式化路径。
