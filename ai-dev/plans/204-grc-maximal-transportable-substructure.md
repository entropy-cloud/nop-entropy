# 204 GRC 最大可传输子结构定理（Koopman 第二阶段对应）

> Plan Status: completed
> Last Reviewed: 2026-08-26
> Source: `ai-dev/analysis/2026-08/2026-08-26-grc-delta-transport-theory.md`（其 Open Problem 1 的方向之一）；用户要求"先草拟草稿，然后不断细化证明细节，另外写一篇文章"
> Related: `docs/theory/proof-v2.md`、`docs/theory/grc-question-answer-audit.md`

## Purpose

上一份研究文档（差量传输理论）停留在 Koopman 谱系的"1931 定义性观察"阶段。本计划推进到第二阶段：**不变子空间定理的 GRC 对应**——给定任意（可能不可传输的）生成器 G，证明基点无关差量集合 `D_tr` 构成最大可传输子幺半群（可逆型时是子群），并给出核/商分解与多阶段管道判别，最终成文为 `ai-dev/articles/` 下的一篇自包含文章。

## Current Baseline

- 已成立（前文，经三轮审计）：delta actegory 公理 A1-A4（含 ⊕ 第一参数同余）；⊖ 与 ∂G_s 的良定义条件（目标强可逆 + 逐点忠实）；定理 1（可传输 ⟺ 基点无关）；定理 2/3（ΔAct 函子性、Im(T) 子幺半群）；ℤ mod-2 反例。
- 已核查的缺口：前文 Open Problems 1-4 全部未证。本计划只承担其中之一（最大可传输子结构），其余保持 out-of-scope。
- **草稿阶段核心推导（作者已自推，待审计）**：
  - 闭包性：d₁,d₂ ∈ D_tr ⇒ d₁⊗d₂ ∈ D_tr，且 T(d₁⊗d₂) ≈ T(d₁)⊗T(d₂)——关键一步是 `G(s⊕d₁⊕d₂) ≈ G(s⊕d₁) ⊕ ∂G_{s⊕d₁}(d₂)`（⊖ 在中间基点 s⊕d₁ 的定义）再接基点无关性。
  - 极大性：任何 d ∉ D_tr 与等变性矛盾（⊖ 唯一性）。
  - 群情形：可逆型下 d ∈ D_tr ⇒ d⁻¹ ∈ D_tr 且 ∂G_s(d⁻¹) ≈ T(d)⁻¹——用 `s ≈ (s⊕d⁻¹)⊕d` 与引理 0 反向。
  - 分解：Ker(T) = 全点稳定子（G 不可见的差量）；T 诱导 D_tr/~_T ≅ Im(T) ≤ D_Y。
  - 管道判别：d ∈ D_tr(H∘G) 的充分条件 = d ∈ D_tr(G) 且 T_G(d) ∈ D_tr(H)，传输复合。

## Goals

- 文章《生成器看见了什么》（文件名 `ai-dev/articles/grc-maximal-transportable-substructure.md`），自包含重述公理并证明：
  1. **定理 A（最大可传输子幺半群）**：D_tr 含 1、对 ⊗ 封闭、T 为其上同态；d ∉ D_tr 不能被任何传输覆盖。
  2. **定理 B（群情形）**：可逆型下 D_tr 是子群、T 群同态、∂G_s(d⁻¹) ≈ T(d)⁻¹。
  3. **定理 C（核与商分解）**：Ker(T) = {d : ∀s, G(s⊕d) ≈ G(s)}；D_tr/~_T ≅ Im(T) ≤ D_Y；差量世界三分（不可见核 / 忠实传输商 / 基点敏感残差）。
  4. **定理 D（管道判别）**：多阶段链逐级判据与传输复合。
- 三个实例：mod-2（完全盲目）、子树提取器（三类差量的具体判定）、XORM→XMeta→XView 链。
- Koopman 第二阶段对应表；诚实的强度边界声明（仍是初等幺半群代数，非新数学；细化/定量留作后续）。
- 过程要求（用户指定）：先草稿 → 逐轮细化证明细节（自我重推 + 独立审计迭代）→ 成文。

## Non-Goals

- 不证 Open Problem 1 的细化定理（存在性）、OP2 定量传输、OP3 分次统一元定理。
- 不改代码、不改 docs-for-ai/、不动 docs/theory/。
- 不声称结果比 patch/lens 理论的证明强度更高（边界声明写入文章）。

## Scope

### In Scope

- `ai-dev/plans/204-grc-maximal-transportable-substructure.md`（本文件）
- `ai-dev/articles/grc-maximal-transportable-substructure.md`（文章本体）
- `ai-dev/logs/2026/08-26.md`（日志）

### Out Of Scope

- 其余 Open Problems；代码；docs-for-ai/；docs/theory/

## Execution Plan

### Phase 1 - 草稿

Status: completed
Targets: `ai-dev/articles/grc-maximal-transportable-substructure.md`

- Item Types: `Proof`

- [x] 文章草稿：公理重述 + 定理 A-D 陈述与证明草稿 + 三个实例草算
- [x] 自细化第一轮：逐行重推每个证明（⊖ 定义方向、A2 方向、引理 0 反用、商同余合法性）——修正定理 A(2) 结合记号、A2 反向对称性说明、3.2 示意性声明
- [x] 独立审计第一轮（ses_fc1e7168bffeB0i0qudSkZu8rU）：定理 A-D 全部成立；发现 1 实质（§3.3"等价"→"蕴涵"+"常数生成器反例"+"保守判据"）+ 5 轻微（= → ≈、G((s⊕d₁)⊕d₂)、A4 双参数括注、新增显式引理 1 并接入定理 D、引理 0 方向表述），全部修复
- [x] 自细化第二轮 + 独立审计第二轮（ses_fc1c8fd97ffejvwIkKVnuf0NJu）：6 项修复全部 PASS + 全文终检无新错误；采纳其非阻断润色（定理 B 证明括注补 A1/A4）
- [x] Koopman 对应表、强度边界声明、工程推论节

Exit Criteria:

- [x] 定理 A-D 的证明经独立审计无实质缺陷（两轮独立审计确认：闭包性七步、群逆方向、同余与商分解、管道判别均成立）
- [x] 三个实例的计算正确（mod-2 的 D_tr=2ℤ、T≡0、Ker=2ℤ、Im={0} 逐项核对；3.2 示意性声明诚实；3.3 修正为保守判据）
- [x] 强度边界声明诚实（明确低于 patch/lens 证明强度、对应 Koopman 第二阶段入门命题——审计确认判断准确）
- [x] 引用路径与行号真实（前篇引理 0/§1.2/§10/定理 3 与五条延伸阅读均核实）
- [x] No owner-doc update required（纯 ai-dev 文档）
- [x] `ai-dev/logs/` 条目已更新

## Closure Gates

- [x] 文章成文且全部证明经审计收敛
- [x] 文本一致性：Plan Status / Phase Status / Exit Criteria / Closure Gates / log
- [x] 独立 closure audit 完成并写入 Evidence
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### 细化定理（D_tr 外的差量能否经空间细化变可传输）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 是 Open Problem 1 的核心困难部分，需新构造，超出本计划。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 定量传输（信息损失度量）与 D_tr 大小的关系。
- 用户裁定是否正式化到 docs/theory/。

## Closure

Status Note: 按用户要求的"先草稿 → 不断细化证明细节 → 写文章"流程完成：作者自推核心推导（闭包性五步链、群逆、商分解、管道判别）→ 成文 → 自细化 → 两轮独立审计迭代收敛。第一轮审计确认定理 A-D 数学上全部成立、仅示例节一处过度声明与五处轻微缺陷；第二轮确认六项修复全部到位、全文终检无新错误。文章强度边界声明（初等幺半群代数、低于 patch/lens 证明强度、对应 Koopman 不变子空间定理层级）经审计认定如实。
Completed: 2026-08-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（pangu，两轮 fresh session）
- Audit Session: ses_fc1e7168bffeB0i0qudSkZu8rU（第一轮：逐条核对定理 A-D 证明链，PASS 除 6 项；其中实质项 = §3.3 把定理 D 单向结论写成"等价"）、ses_fc1c8fd97ffejvwIkKVnuf0NJu（第二轮：6 项修复逐条 PASS + 全文终检 + 引用核实）
- Evidence:
  - 每条 Exit Criterion 验证结果：定理 A-D 无实质缺陷（两轮审计，第二轮确认修复后链条完整）PASS；实例计算 PASS（mod-2 逐项、3.2 示意性声明、3.3 保守判据）；强度声明诚实 PASS；引用真实 PASS（前篇引理 0/§1.2/§10/定理 3 与五条延伸阅读链接）。
  - Closure Gates：文章成文 ✓；文本一致性 ✓；独立 audit 完成且证据在本段 ✓。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/204-grc-maximal-transportable-substructure.md --strict` 退出码 0。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
  - Anti-Hollow 检查：纯文档计划，无代码组件，不适用（plan guide 纯文档条款）。
  - Deferred 项分类检查：细化定理为 `out-of-scope improvement`，与文章 §四 的开放问题声明一致，无 in-scope 内容降级。

Follow-up:

- 细化存在性定理（D_tr 外差量经空间细化变可传输）与定量传输（传输损失下界）仍为开放问题（文章 §四 已声明）。
- 用户裁定是否正式化到 docs/theory/。
