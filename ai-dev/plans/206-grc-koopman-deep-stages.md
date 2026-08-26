# 206 沿 Koopman 路径的深层挖掘（望远镜引理/导数函子/信息论谱）

> Plan Status: completed
> Last Reviewed: 2026-08-26
> Source: 用户要求"沿着 Koopman 算子的研究路径，穷尽数学知识，深度挖掘发展方向和定理，为 GRC 补充更深的定理"
> Related: 系列前三篇（transport theory / D_tr / 差量塔分类）

## Purpose

把 Koopman 研究路径的深层阶段（谱结构 → 函子/Carleman 提升 → 数据驱动收敛与误差 → 验证）逐一映射到 GRC 并给出可证定理。本计划的中心新结果（作者已自证，待审计）：

1. **望远镜引理**：任意强可逆 + 忠实空间中 `(a⊖b)⊗(c⊖a) ≈ c⊖b`——差量的链式法则，不需要逆。
2. **导数函子定理**：∂G 总构成作用群胚 `S_X⋊D_X → S_Y⋊D_Y` 之间的**函子**；传输性 = 该函子在 d-纤维上常值；`D_tr` = 常值纤维集。
3. **群作用谱**：强可逆+可逆型+忠实 ⟹ `S ≅ D/Stab(s₀)`（齐性空间，orbit-stabilizer 导入）；总稳定子 = `Ker(T_{id})`。
4. **信息论谱**：DPI 链定理（信息沿生成链单调不增）；delta-conservative ⟺ 零条件熵；KS 熵率因子定理导入（守恒判定，单向诚实陈述）；传输性检验误判率公式。

## Current Baseline

- 已成立（前三篇）：公理 A1-A4、⊖、∂G_s、定理 1（可传输 ⟺ 基点无关）、D_tr 理论、塔分类（crossed module/2-group）、merge/lens 导入。
- 已核查空白：上述四个方向均无仓库内形式化；"细化存在性"（Open Problem 1）将在本文以导数函子语言被**精确化**（作用群胚 = 无穷维包络，常值降落 = 有限逼近），但有限字典的存在性定理仍未证明（如实标注）。

## Goals

- 文章 `ai-dev/articles/grc-koopman-deep-stages.md`，四部分：
  - **§一 群作用谱**：ΔAct = 幺半群作用范畴的识别；齐性空间定理；总稳定子与 Ker 一致性；D_tr 的 oplax 函子性。
  - **§二 望远镜与导数函子（本文最深处）**：望远镜引理（无逆链式法则）+ 导数函子定理（D(G) 总为函子）+ 传输性重述（常值纤维）+ Carleman 对应（作用群胚 = 无穷维线性化包络；细化问题精确化）。
  - **§三 信息论谱**：DPI 链定理；守恒判定；KS 熵率导入（Rokhlin 公式注意：有限到一因子保熵，故逆向不成立——单向诚实）；检验误判率公式。
  - **§四 诚实台账**：每条的"搬运 vs 识别"标注；开放问题（有限字典收敛、非平稳、连续谱/rigged 类比）。
- 两轮独立审计迭代收敛。

## Non-Goals

- 不证有限字典/有限逼近的收敛定理（明确列为开放）。
- 不改代码、docs-for-ai/、docs/theory/。

## Scope

### In Scope

- `ai-dev/plans/206-grc-koopman-deep-stages.md`
- `ai-dev/articles/grc-koopman-deep-stages.md`
- `ai-dev/logs/2026/08-26.md`

### Out Of Scope

- 有限逼近收敛；代码；docs-for-ai/；docs/theory/

## Execution Plan

### Phase 1 - 草稿与审计迭代

Status: completed
Targets: `ai-dev/articles/grc-koopman-deep-stages.md`

- Item Types: `Proof`

- [x] 草稿：四个部分的全部定义、定理、证明
- [x] 自细化第一轮——修正三处：定理 6 的 ⟺ 改单向（T 单射不满射反例 T(d)=2d）；定理 3 证明补 A2/引理 1 桥接步；§2.4 merge 关系改为"中间因子"表述
- [x] 独立审计第一轮（ses_fc1961007ffe09AgMEzsr3D0Le）：定理 1-7 全部成立、无实质数学错误；发现定理 1 包装过度（逐点忠实 ⟹ Stab 平凡）、定理 7 本质/逐点混同、§2.3 细化/范畴化混同，全部修复
- [x] 自细化第二轮 + 独立审计第二轮（ses_fc18a0b39ffeZifzWr6npvq7n8）：发现修复引入的实质缺陷（推论 1.1(ii) 以 tree-delta 为实例但缺可逆型）——改为 S₃ 群作用实例 + tree-delta 免责注记；3 必改（定理 3 逐点忠实措辞、定理 7 并列极大者、台账编号）+ 2 可选全部落实
- [x] 终审（ses_fc17ff203ffeiNXI4W0ER9MHZA）：全部 PASS，数学事实经独立验算；唯一残留 L176 引用悬空已修
- [x] 诚实台账 + 开放问题

Exit Criteria:

- [x] 全部定理经独立审计无实质缺陷（三轮审计：望远镜引理、导数函子、齐性空间、DPI、守恒、误判率全部成立）
- [x] 望远镜引理与导数函子定理的每一步可独立核对（含桥接步与 A4 的 ⊗ 同余）
- [x] "搬运 vs 识别"逐条标注；逆向不成立的声明诚实（定理 6 单向、KS 熵率 Rokhlin 反方向、定理 7 全支撑条件）
- [x] 引用真实（前三篇编号、外部文献 Brown–Spencer/Colbrook–Townsend 等经核验）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 条目已更新

## Closure Gates

- [x] 文章成文且证明经审计收敛
- [x] 文本一致性：Plan Status / Phase Status / Exit Criteria / Closure Gates / log
- [x] 独立 closure audit 完成并写入 Evidence
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### 有限字典逼近的收敛定理（EDMD 收敛的 GRC 版）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要拓扑/测度结构的额外假设与逼近论的专门论证，超出本计划；本文给出问题精确化（常值降落）。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 非平稳变化过程、连续谱（rigged 类比）。
- 用户裁定正式化路径。

## Closure

Status Note: 按"草稿 → 自细化 → 三轮独立审计"流程完成。本文沿 Koopman 路径深层阶段（谱结构→函子/Carleman 提升→信息论收敛→验证）产出两个引擎性新定理：**望远镜引理**（无逆链式法则，逐行核对通过）与**导数函子定理**（任意生成器的导数总为作用群胚函子，传输性=纤维常值性），加上齐性空间、DPI 链、守恒判定、误判率四个方向。三轮审计确认定理 1-7 全部成立；审计循环还纠出三处陈述层缺陷（定理 1 包装过度、本质/逐点混同、细化/范畴化混同）及一处修复引入的错误（tree-delta 缺可逆型却作陪集商实例——改为 S₃ 群作用实例）。
Completed: 2026-08-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（pangu，三轮 fresh session）
- Audit Session: ses_fc1961007ffe09AgMEzsr3D0Le（第一轮）、ses_fc18a0b39ffeZifzWr6npvq7n8（第二轮）、ses_fc17ff203ffeiNXI4W0ER9MHZA（终审）
- Evidence:
  - Exit Criteria 逐条：定理无实质缺陷（三轮，望远镜引理全五项核对、导数函子合成保持含桥接步、定理 7 渐近与并列极大者）✓；搬运/识别台账逐条核对 ✓；逆向声明诚实（定理 6 单向 + T(d)=2d 反例、Rokhlin、全支撑条件）✓；引用真实（前三篇编号 + 外部文献经网络核验）✓；No owner-doc update ✓；日志已更新 ✓。
  - Closure Gates：成文与审计收敛 ✓；文本一致 ✓；独立 audit 证据在本段 ✓。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/206-grc-koopman-deep-stages.md --strict` 退出码 0。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
  - Anti-Hollow：纯文档计划，不适用。
  - Deferred 分类：有限字典逼近收敛定理为 out-of-scope improvement（§四 Open Problem 1 精确陈述），无降级。

Follow-up:

- 有限字典逼近（EDMD 收敛的 GRC 版）、非平稳过程、连续谱/rigged 类比、ΔAct 的 2-范畴完整形式化——均已在 §四 精确陈述为开放问题。
- 用户裁定正式化路径。
