# 205 差量之塔分类定理与 patch/lens 定理导入

> Plan Status: completed
> Last Reviewed: 2026-08-26
> Source: 用户讨论（"lens 和 darcs 的定理在 grc 的局部都是可以直接使用的"→ 深层定理应落在 GRC 独有的全局结构）；`ai-dev/articles/grc-maximal-transportable-substructure.md`（定理 5/6 自作用与共轭）
> Related: `docs/theory/proof-v2.md`、`ai-dev/articles/dsh-architecture-from-reversible-computation.md`

## Purpose

把 GRC 的深层定理落在 patch/lens 不研究的结构上：**(1) 二阶差量塔的分类**——可逆型差量 actegory 的二阶结构（D 自作用 + 共轭传输）恰好是差量群的内自同构 crossed module，因此是 2-group，塔深恰为 2、三阶无新内容、混合链有规范形（confluence）；**(2) patch/lens 定理的精确导入**——group-like 空间 merge 恒存在且对称 up to conjugation（冲突 = 逆缺失，conflictor 与 tombstone 同构观点）；delta-conservative 转换器 = 良态 lens（GetPut/PutGet/PutPut 逐条验证，补语 = 上游结构）。

## Current Baseline

- 已成立（前文，经审计）：差量 actegory 公理 A1-A4；定理 5（自作用：D 对自身成 actegory）；定理 6（R_δ 的传输 = 共轭 δ⁻¹εδ、L_δ 传输 = id、交换判据）；推论 6.1（插链 = 共轭等式）；引理 0/1；D_tr 理论（最大可传输子结构）。
- 未触及（本计划承担）：crossed module 识别与塔深分类；merge 导入与冲突同构；lens 良态对应。
- 强度预判（写入文章）：crossed module 是 Whitehead 现成理论、merge 定理是 Darcs 的 group 情形——贡献在**识别**（GRC 塔 = 内自同构 crossed module、"三阶无新内容"、冲突⇔逆缺失、lens 补语 = 上游结构），不声称新数学。

## Goals

- 文章 `ai-dev/articles/grc-delta-tower-classification.md`：
  1. **定理 A（塔的分类）**：可逆型忠实差量 actegory 中，(D, ∂=id, 共轭作用 b(δ)(ε)=δεδ⁻¹) 是内自同构 crossed module；两条 crossed module 公理分别对应共轭公式（定理 6b）与 Peiffer 恒等；故二阶塔 = 2-group。
  2. **定理 B（塔深与规范形）**：自作用的自作用 = 自作用（塔在元素层稳定于两层）；混合链经"右推共轭"归约为单一差量，不同归约顺序结果 ≈ 相等（confluence，直接证明：共轭与 ⊗ 的结合律 + 推论 6.1）。
  3. **定理 C（merge 导入）**：group-like 空间对任意基点与 d₁,d₂，merge 恒存在：`m ≈ s⊕d₁⊕(d₁⁻¹⊗d₂) ≈ s⊕d₂⊕(d₂⁻¹⊗d₁)`，两路径差量互为共轭对（Darcs 换位方阵的 group 情形）；冲突 ⇔ 逆缺失（tombstone/conflictor 同构观点，如实标注为结构性对照非定理）。
  4. **定理 D（lens 导入）**：delta-conservative 转换器 (G,T) 定义 `get=G, put(s,y)=s⊕T⁻¹(y⊖G(s))` 满足 GetPut/PutGet/PutPut；补语 = 上游结构 s 本身。
- 树差量（不可逆）对照：tagged 规范化（proof-v2 组合表）= 无逆时的"弱 crossed module 数据"（分类学陈述，标注非定理）。
- 诚实边界节：逐条标注"搬运 vs 识别"。

## Non-Goals

- 不证细化存在性定理、定量传输（仍是开放问题）。
- 不改代码、docs-for-ai/、docs/theory/。
- 不做逆半群胚的完整公理化（只做 group-like 情形 + 对照）。

## Scope

### In Scope

- `ai-dev/plans/205-grc-delta-tower-classification.md`
- `ai-dev/articles/grc-delta-tower-classification.md`
- `ai-dev/logs/2026/08-26.md`

### Out Of Scope

- 其余开放问题；代码；docs-for-ai/；docs/theory/

## Execution Plan

### Phase 1 - 草稿与审计迭代

Status: completed
Targets: `ai-dev/articles/grc-delta-tower-classification.md`

- Item Types: `Proof`

- [x] 草稿：定理 A-D 陈述与证明（共轭方向约定、c_δ 反同态方向、crossed module 公理逐条核对、merge 差异差量计算、lens 三律逐条验证、confluence 两步证明）
- [x] 自细化第一轮：逐行重推——**发现并修正定理 C 草稿的实质错误**（原"共同合并点 m 恒存在"为假：两分支差异点 m₁≠m₂，由忠实性相等当且仅当 d₁≈d₂；改为正确的"冲突消解"表述——分支差异差量 d₁⁻¹⊗d₂ 精确可算且互逆）；补 ⊖ 存在性由显式构造保证、定理 D 目标强可逆自动成立 + 忠实需假设
- [x] 独立审计第一轮（ses_fc1b56543ffe2YTCGV83vpjgCp）：定理 A-D 全部成立、无实质缺陷；2 FAIL 级措辞（lens 方向性夸大 ×2）+ 4 轻微 + Newman 引理建议，全部修复
- [x] 自细化第二轮 + 独立审计第二轮（ses_fc1a9d893ffeHRW1XjWxh5VX0Q）：8 项修复全 PASS + 终检 PASS；2 cosmetic nits（引文"普通"缺字、proof-v2 下标）已修
- [x] 诚实边界节 + 树差量对照 + 工程推论

Exit Criteria:

- [x] 定理 A-D 经独立审计无实质缺陷（两轮审计确认：crossed module 公理、塔稳定、confluence、差异差量公式、lens 三律全部成立）
- [x] "搬运 vs 识别"边界逐条如实标注（审计逐行核对 8 行表）
- [x] 引用真实（前文定理 5/6、推论 6.1、引理 0/1、proof-v2 §6.3 十六规则）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 条目已更新

## Closure Gates

- [x] 文章成文且证明经审计收敛
- [x] 文本一致性：Plan Status / Phase Status / Exit Criteria / Closure Gates / log
- [x] 独立 closure audit 完成并写入 Evidence
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### 逆半群胚完整公理化（非 group 情形）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要与 Mimram–Di Giusto 形式化的完整对接，是独立课题；本文只做 group 情形 + 诚实对照。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 细化存在性定理、定量传输（延续开放问题）。
- 用户裁定正式化路径。

## Closure

Status Note: 按"草稿 → 自我细化 → 审计迭代"流程完成。自细化阶段发现并修正了定理 C 草稿的实质错误（错误的"共同合并点"表述——被忠实性反证——改为正确的冲突消解/差异差量公式）。两轮独立审计确认定理 A-D 成立、无实质缺陷，措辞层问题（lens 方向性、merge 对称残留）全部修复。本文把 GRC 二阶塔识别为内自同构 crossed module（2-group），塔深 = 2；并精确导入 Darcs merge（group 实例 d₁⁻¹⊗d₂）与 Foster lens 三律（delta-conservative ⟹ 良态 lens）。
Completed: 2026-08-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（pangu，两轮 fresh session）
- Audit Session: ses_fc1b56543ffe2YTCGV83vpjgCp（第一轮：定理 A-D 逐步核对全 PASS，2 FAIL 级措辞 + 4 轻微 + 建议）、ses_fc1a9d893ffeHRW1XjWxh5VX0Q（第二轮：8 项修复全 PASS + 终检 + 链接实测 0 错误）
- Evidence:
  - Exit Criteria 逐条：定理 A-D 无实质缺陷（两轮审计独立重验 crossed module 公理、塔稳定、confluence 两步、差异差量公式、lens 三律含 T⁻¹ 同态与 ⊖ 唯一性用法）✓；搬运/识别 8 行表逐条核对 ✓；引用（定理 5/6(b)、推论 6.1、引理 0/1、proof-v2 §6.3 十六规则、主文章 §5/§7.5）核实 ✓；No owner-doc update required ✓；日志已更新 ✓。
  - Closure Gates：成文与审计收敛 ✓；文本一致 ✓；独立 audit 证据在本段 ✓。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/205-grc-delta-tower-classification.md --strict` 退出码 0。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
  - Anti-Hollow：纯文档计划，不适用。
  - Deferred 分类：逆半群胚完整公理化为 out-of-scope improvement，诚实标注。

Follow-up:

- 逆半群胚完整公理化（非 group 情形）为独立课题。
- 细化存在性、定量传输仍开放。
- 用户裁定正式化路径。
