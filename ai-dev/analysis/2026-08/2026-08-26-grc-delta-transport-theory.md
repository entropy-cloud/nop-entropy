# 差量传输理论：生成器演算（Delta Transport Theory / Generator Calculus）

> Status: resolved
> Date: 2026-08-26
> Scope: GRC 形式核心的扩展——把 `transport_G` 从原则提升为可检查的定理系统（仿 Koopman 算子研究纲领）
> Conclusion: 成立且自洽的形式系统（经三轮独立审计收口）：7 定理 + 3 引理 + 1 算法 + 分类学表。核心结果——传输存在 ⟺ 变化导数基点无关（定理 1）；ΔAct 范畴函子性（定理 2）；上游可表达闭包（定理 3）；delta-conservative 转换器下 x-diff 交换（定理 4，附 tree-delta 逐点忠实限定）；自作用 + 共轭定理（定理 5/6，`δ⁻¹εδ`）；有损 ⇒ 不可传输（定理 7 单向，反向被 ℤ mod-2 反例证伪），S-N-V 次序由此成为代数必然。

## Context

- 项目已把**宽松同态定律** `G(X ⊕ ΔX) ≈ G(X) ⊕ transport_G(ΔX)` 陈述为原则（`docs/theory/reversible-computation-a-paradigm-manifesto.md`；`ai-dev/articles/grc-universal-software-construction-theory.md` §8.6 第 888 行），但项目自身审计文档 `docs/theory/grc-question-answer-audit.md:400-406` 判定："跨 DSL 的实际 ΔX→ΔY 如何构造、何时保真、何时有损，目前更像工程准则而不是完整形式系统。状态：部分回答"；`:950-958` 把它列为"合理的新理论补充点"。
- 现有形式化成果只覆盖**结合律**：`docs/theory/proof-v2.md`（tree-delta carrier，条件化结合律）、`docs/theory/grc-delta-associativity-formal-proof.md`（三 carrier 预合并结合律，其中 A8 明言"任意 End(S) 函数复合只给出语义组合，不自动给出可保存的 DSL 差量文本"）。
- 本文回答的问题：**给定生成器 G，差量能否穿过它？何时能、是否唯一、如何复合、穿过之后还剩下什么？** 这是 Koopman 算子研究纲领在 GRC 中的同构版本——Koopman 把一个非线性流 φ 提升为观测空间上的线性算子 K（`K g = g ∘ φ`，线性性把"非线性"从障碍转化为坐标选择问题）；本文把一个生成器 G 提升为差量空间上的传输 T（等变性把"差量能否穿过生成器"从模糊经验转化为可检查的方程）。

## 1. 定义

### 1.1 差量 actegory（delta actegory）

设 `S` 为结构空间、`D` 为差量空间，`≈` 为两者上的等价关系。一个**（结合）差量 actegory** `A = (S, D, ⊕, ⊗, ≈, 1)` 由以下数据组成：

- 作用 `⊕ : S × D → S`（把差量 d 施加到结构 s 上，记 `s ⊕ d`）；
- 组合 `⊗ : D × D → D`（`d₁ ⊗ d₂` 读作"先施加 d₁，再施加 d₂"）；
- 单位元 `1 ∈ D`；

满足（对所有涉及对象）：

$$
\text{(A1 恒等)}\quad 1 \otimes d \approx d \approx d \otimes 1,\qquad s \oplus 1 \approx s
$$

$$
\text{(A2 作用律)}\quad s \oplus (d_1 \otimes d_2) \approx (s \oplus d_1) \oplus d_2
$$

$$
\text{(A3 结合律)}\quad (d_1 \otimes d_2) \otimes d_3 \approx d_1 \otimes (d_2 \otimes d_3)
$$

$$
\text{(A4 同余)}\quad s \approx s' \Rightarrow s \oplus d \approx s' \oplus d;\quad d \approx d' \Rightarrow s \oplus d \approx s \oplus d';\quad d_1 \approx d_1',\, d_2 \approx d_2' \Rightarrow d_1 \otimes d_2 \approx d_1' \otimes d_2'
$$

**实例锚定（均为仓库内既有形式化成果的重新组织）：**

- **tree-delta 空间**：`proof-v2.md` 的 `(S_p, D_p, Apply, ⊗)` 在语义等价商上满足 A2（其定理 1）与 A3（其定理 2）；`≈` 恰由 `Apply` 定义（`d₁ ≈ d₂ ⟺ ∀s, Apply(s,d₁)=Apply(s,d₂)`）。
- **Cordis 运行时空间**：dsh 文章 §4.1-4.2 的 coeffect 表（σ: K ⇀ V_k）与 effect 对；A3 对应 twisted composition 的语义结合；其每个 effect 自带逆（下述"可逆型"）。

### 1.2 性质

- **faithful**（作用逐点忠实）：对任意 `s, d, d'`，若 `s ⊕ d ≈ s ⊕ d'`，则 `d ≈ d'`。这是差量在同一基点上的作用可区分的条件，也是 ⊖ 唯一性的直接来源。比特空间的 XOR 忠实；可逆型空间中忠实等价于左可消。**注意**：tree-delta 的 `≈` 按全局作用定义（proof-v2 §8），给出的是**全局忠实**（商 `D/≈` 单射进 `End(S)`），逐点忠实**不自动成立**——两个差量可在某一基点上作用相同而在其他基点不同（例如仅影响不存在子树的差量在空树上与恒等不可区分）。因此逐点忠实须逐实例验证或显式假设，tree-delta 实例不能默认。
- **可逆型（group-like）**：每个 `d` 有逆 `d⁻¹`，满足 `d ⊗ d⁻¹ ≈ 1 ≈ d⁻¹ ⊗ d`。Cordis（"Δ 出生即带逆"）与比特空间属此型；tree-delta 不属此型（tombstone 无全局逆）。
- **强可逆（strongly invertible）**：`∀ s₁, s₂ ∈ S, ∃d: s₂ ≈ s₁ ⊕ d`。即任意两结构之间差量总存在。比特空间与 tree-delta 满足（后者取 `Replace(s₂)`）；Cordis 仅对可达差成立。

### 1.3 结构差 ⊖ 与变化导数

若 A 强可逆且忠实，则对 `s₁, s₂` 存在**唯一**（精确到 ≈）的 `d` 使 `s₂ ≈ s₁ ⊕ d`，记为

$$
s_2 \ominus s_1 \;:=\; \text{该唯一的 } d
$$

**引理 0（单位元与差）**：`s ⊖ s ≈ 1`；可逆型时 `(d₁⊗d₂)⁻¹ ≈ d₂⁻¹⊗d₁⁻¹`，且 `s₂ ≈ s₁ ⊕ d ⟺ s₁ ≈ s₂ ⊕ d⁻¹`。

证明：由 A1 与唯一性立得；逆公式由 A3 展开 `(d₁⊗d₂)⊗(d₂⁻¹⊗d₁⁻¹) ≈ 1` 与左消（可逆型下左乘 d⁻¹）得证。∎

### 1.4 生成器、传输、变化导数

设源 `A_X = (S_X, D_X, …)`、目标 `A_Y = (S_Y, D_Y, …)`。

- **生成器**：`G : S_X → S_Y`，保 ≈（`s ≈ s' ⇒ G(s) ≈ G(s')`）。
- **差量传输（delta transport）**：`T : D_X → D_Y` 满足

$$
\text{(T1)}\; T(1_X) \approx 1_Y,\qquad
\text{(T2 等变性)}\; \forall s,d:\; G(s \oplus_X d) \approx G(s) \oplus_Y T(d)
$$

若 G 存在传输 T，称 G **可传输（transportable）**；称对 `(G, T)` 为差量 actegory 之间的**态射**。

- **变化导数（change-action derivative）**（目标强可逆 + 忠实时可定义）：

$$
\partial G_s(d) \;:=\; G(s \oplus_X d) \;\ominus_Y\; G(s)
$$

即"从基点 s 出发，输入变化 d 经 G 传播后在目标空间中的差量"——这正是 Change Actions 文献中 `∂f(a, δa)` 的 GRC 记法（见 §9 桥接）。

## 2. 基本引理

**引理 1（传输唯一性）**：若 A_Y 逐点忠实，则 G 的传输（若存在）在 ≈ 下唯一。

证明：设 T₁, T₂ 皆满足 T2。对任意 d，任取一个 s ∈ S_X，由 T2：

$$
G(s) \oplus_Y T_1(d) \;\approx\; G(s \oplus_X d) \;\approx\; G(s) \oplus_Y T_2(d)
$$

在基点 `G(s)` 处由逐点忠实性得 `T₁(d) ≈ T₂(d)`。∎

**引理 2（传输乘法性）**：在引理 1 的约定下，传输是差量幺半群的同态：

$$
T(d_1 \otimes_X d_2) \;\approx\; T(d_1) \otimes_Y T(d_2)
$$

证明（逐行核对，两步都用 T2 与 A2）：

$$
G(s) \oplus_Y T(d_1 \otimes d_2)
\;\overset{\text{T2}}{\approx}\; G(s \oplus_X (d_1 \otimes d_2))
\;\overset{\text{A2}}{\approx}\; G((s \oplus_X d_1) \oplus_X d_2)
$$

$$
\;\overset{\text{T2}}{\approx}\; G(s \oplus_X d_1) \oplus_Y T(d_2)
\;\overset{\text{T2}}{\approx}\; (G(s) \oplus_Y T(d_1)) \oplus_Y T(d_2)
\;\overset{\text{A2}}{\approx}\; G(s) \oplus_Y (T(d_1) \otimes_Y T(d_2))
$$

由忠实性得证。∎

## 3. 定理 1：传输条件（Transport Condition）

**定理 1（基点无关性 = 可传输性）**。设 A_Y 强可逆且忠实。则 G 可传输 ⟺ 变化导数 `∂G_s(d)` 对一切 s, d 均有定义，且**与基点 s 无关**（≈ 意义下）。此时唯一的传输为：

$$
T(d) \;=\; \partial G_{s_0}(d) \qquad(\text{任意固定基点 } s_0)
$$

证明。

(⇐) 固定 s₀，令 `T(d) := ∂G_{s₀}(d)`。由基点无关性，对任意 s 有 `∂G_s(d) ≈ ∂G_{s₀}(d)`。而 `∂G_s(d)` 按定义满足

$$
G(s \oplus_X d) \;\approx\; G(s) \oplus_Y \partial G_s(d)
$$

故 `G(s ⊕ d) ≈ G(s) ⊕ T(d)`，即 T2 成立。`T(1) ≈ 1` 由引理 0。∎

(⇒) 若 T 是传输，则对每个 s，`T(d)` 满足 `G(s⊕d) ≈ G(s) ⊕ T(d)`，由 ⊖ 的唯一性（忠实）得 `∂G_s(d) ≈ T(d)`——与 s 无关。∎

**意义（Koopman 对应）**：Koopman 的线性化判据是"存在有限维不变子空间"；GRC 的传输判据是"变化导数与基点无关"。两者都把"能否友好地表示变化传播"从经验问题变成**可检查的方程**。

**反例（N 阶段操作）**：`G = 按字母序排序 children`。`∂G_s(d)` 依赖 s 的兄弟集合（排序行为取决于整体），基点无关性失败，故排序不可传输——因此它只能存在于规范化阶段（§8）。

**例（比特空间中的检查）**：比特空间强可逆、可逆型、交换、忠实，故 `∂G_s(d) = G(s \,\text{XOR}\, d)\;\text{XOR}\; G(s)` **总是可定义**。此时传输条件化为纯方程：`∂G_s(d)` 是否与 s 无关。例如 `G = 逐字节加一`：`∂G_s(d) = (s⊕d⊕1)⊕(s⊕1) = d`——可传输，`T = id`。`G = 字节排序`：`∂G_s(d)` 依赖 s——不可传输。**锚点的精确作用在此显形**：比特空间不能替 G 找到传输，但它把"G 是否差量友好"这个模糊问题**降维为一个良定的独立性检查**——这正是 Koopman 中"无穷维函数空间的精确线性化"所起的许可（licensing）作用。

## 4. 定理 2：函子性——ΔAct 范畴

**定理 2（传输复合）**。若 `(G, T_G)` 与 `(H, T_H)` 是态射（A_X → A_Y → A_Z），**且 A_Z 逐点忠实**，则 `(H∘G, T_H∘T_G)` 是态射。恒等生成器以恒等传输为态射。故差量 actegory 与可传输生成器构成范畴 **ΔAct**。

证明。T2（组合的等变性链，一步 T_G、一步 T_H）：

$$
(H\circ G)(s \oplus d) \;\approx\; H\big(G(s) \oplus T_G(d)\big) \;\approx\; H(G(s)) \oplus T_H(T_G(d))
$$

T1：由 A_Z 逐点忠实，任意传输保 ≈（观察 2.1，见下），故 `T_H(T_G(1_X)) ≈ T_H(1_Y) ≈ 1_Z`（T1 对 T_G、T_H 各用一次 + T_H 保 ≈）。由引理 1 的忠实性约定，此传输唯一。∎

**观察 2.1（忠实目标下传输自动保 ≈）**：若 A_Y 逐点忠实且 (G, T) 是态射，则 T 保 ≈：`d ≈ d' ⟹ G(s⊕d) ≈ G(s⊕d')`（A4 + G 保 ≈）⟹ `G(s)⊕T(d) ≈ G(s)⊕T(d')`（T2）⟹ `T(d) ≈ T(d')`（逐点忠实）。这是 ΔAct 态射在商集上良定义的唯一支柱；**非忠实目标下传输保 ≈ 不自动成立**（例如 A_Y 为单点结构 + 平凡作用的退化空间，T 可在 ≈ 类之间任意取值）。

**推论 2.1（多阶段生成链的传输）**：纵向管线 `XORM → XMeta → XView → XPage`（每段 `Gᵢ` 均可传输时）的整体传输 = 各段传输的复合：

$$
T_{G_3 \circ G_2 \circ G_1} \;=\; T_{G_3} \circ T_{G_2} \circ T_{G_1}
$$

即上游差量沿管线传播是**确定且可复合的**；反之，链中任一环节不可传输，上游差量在该环节就必须"落回"结构层重新求差（与主文章 §7.3"不必把信息强行塞入源 DSL"一致，此处给出了精确的形式边界）。

**推论 2.2（不可传输 = 非 ΔAct 态射）**：不可传输的映射在差量世界中不是态射——其行为无法在给定目标空间中被任何差量模拟。这给"哪些操作属于差量体系、哪些属于编译器/运行时"划出了一条**代数边界**（§8 展开）。

## 5. 定理 3：上游可表达性闭包

**定理 3（像为子幺半群）**。设 `(G, T)` 是态射。则 `Im(T) ⊆ D_Y` 是 `(D_Y, ⊗)` 的子幺半群（≈ 意义下）。

证明：由 T1 有 `1_Y ≈ T(1_X) ∈ Im(T)`；由引理 2 有 `T(d₁) ⊗ T(d₂) ≈ T(d₁⊗d₂) ∈ Im(T)`。∎

**定义**：G **delta-complete**：T 满（≈ 意义下）——一切目标空间定制都可上游表达；G **delta-conservative**：T 双射（≈ 意义下）——上游表达唯一且可逆。

**推论 3.1（差量包的上游吸收）**：目标侧一组定制 `{δᵢ}` 若能各自上游表达（`δᵢ ∈ Im(T)`），则其任意合并组合仍能上游表达。工程含义：客户差量包如果只触及上游可表达坐标，可整体吸收进上游模型，**组合不逃逸**——这给主文章"F ↔ X ↔ Δ 信息迁移"的反复重构给出了闭包保证：迁移进 X 的信息不会因组合而泄漏回 Δ。

**推论 3.2（pullback 判据）**：目标侧差量 `ΔY` 能等价改写为源侧差量 `ΔX`（`G(X ⊕ ΔX) ≈ G(X) ⊕ ΔY`）当且仅当 `ΔY ∈ Im(T)`。这精确化了主文章 §7.3 的"能在上游表达的变化放在 ΔX 中"——其范围恰为 `Im(T)`。

## 6. 定理 4：表象等价——多表象 x-diff 的代数基础

**引理 3（逆与逆传输）**：若 `G : S_X → S_Y` 双射、`G⁻¹` 保 ≈（即 G 反映 ≈：`G(s₁)≈G(s₂) ⇒ s₁≈s₂`——表象转换器双向保持语义等价，属可验证的工程契约），且 `(G, T)` 是态射、T 在 ≈ 意义下双射且保 ≈（delta-conservative，即 T 诱导商集同构，从而 `T⁻¹` 保 ≈），则 `(G⁻¹, T⁻¹)` 是态射，即 delta-conservative 的转换器是 ΔAct 中的**同构**。

证明：令 `y = G(s)`，由 T2 与 G⁻¹ 保 ≈：

$$
G^{-1}(y \oplus T(d)) \;\approx\; G^{-1}(G(s) \oplus T(d)) \;\approx\; G^{-1}(G(s \oplus d)) \;\approx\; s \oplus d \;\approx\; G^{-1}(y) \oplus d
$$

即 `T⁻¹` 满足 T2（对 G⁻¹）。T1 对 `T⁻¹`：由 T 保 ≈ 且 `T(1_X) ≈ 1_Y`，两边作用 `T⁻¹`（保 ≈）得 `1_X ≈ T⁻¹(1_Y)`。故 `(G⁻¹, T⁻¹)` 为态射。∎

**定理 4（x-diff 与转换器交换）**。在引理 3 条件下（G 双射、T 在 ≈ 意义下双射，源、目标空间均强可逆 + 逐点忠实）：

$$
\text{diff}_Y\big(G(s_1),\, G(s_2)\big) \;\approx\; T\big(\text{diff}_X(s_1, s_2)\big)
$$

证明：`G(s₂) ≈ G(s₁ ⊕ (s₂⊖s₁)) ≈ G(s₁) ⊕ T(s₂⊖s₁)`（T2）；又 `G(s₂) ≈ G(s₁) ⊕ (G(s₂)⊖G(s₁))`（⊖ 定义）；由 ⊖ 唯一性（忠实）得 `diff_Y = T ∘ diff_X`。∎

**意义**：这给主文章 §7.5"多重表象：推导加修正"提供代数支柱——XML/JSON/XLSX 对同一 XDef 模型是表象转换，**若**转换器被验证为 delta-conservative 且目标空间逐点忠实（工程符合性义务，与 proof-v2 声明义务同性质），则差量提取、合并、往返在所有表象间交换，`x-diff` 可在任一表象完成并自由翻译。**限定**：tree-delta 目标不逐点忠实（见 §1.2），其规范 x-diff 的唯一性是规范化选择的产物而非定理结论——Nop 的 x-diff 属此补充假设范畴，即"规范化差量代表元"被显式固定后定理 4 才直接适用。Nop 的 `nop-file-converter` 是候选验证对象（列为未来工程验证，不在本文范围）。

## 7. 定理 5/6：二阶差量——自作用与共轭

### 7.1 自作用（"差量的差量仍然是差量"的代数语句）

**定理 5（自作用封闭）**。每个结合差量 actegory `(S, D, ⊕, ⊗, ≈, 1)` 都给出 D 自身上的差量 actegory：

$$
(D,\; D,\; \oplus_2 := \otimes,\; \otimes_2 := \otimes,\; \approx,\; 1)
$$

证明：A1：`1 ⊗ d ≈ d ≈ d ⊗ 1`（即 `d ⊕₂ 1 ≈ d`）；A2：`d₁ ⊕₂ (d₂ ⊗₂ d₃) = d₁ ⊗ (d₂ ⊗ d₃) ≈ (d₁ ⊗ d₂) ⊗ d₃`（A3）；A3、A4 同源于 A3、A4。∎

三阶差量 = 该自作用的自作用，逐级塔式展开全部是同一代数——"差量的差量"不需要任何新机制（主文章 §5 的递归性主张由此获得代数载体；dsh 文章 §4.6"Plugin 是差量，但差量的差量没有技术载体"的 DSH 缺陷，在本框架中恰是自作用封闭的**技术载体**——可序列化、可独立审计的差量文本——在运行时空间缺失的表现，对应 formal-proof 的 A8 语义闭包与语法闭包之别）。

### 7.2 共轭定理（顺序依赖的代数解剖）

在可逆型 + 忠实的差量 actegory 中，考察二阶生成器（即 D 上的映射）：

$$
L_\delta(d) := \delta \otimes d \quad(\text{前插 } \delta),\qquad
R_\delta(d) := d \otimes \delta \quad(\text{追加 } \delta)
$$

**定理 6（共轭定理）**：

- **(a)** `L_δ` 恒可传输，且 `T_{L_δ} = id`。其可传输性**正是** A3（结合律）本身。
- **(b)** `R_δ` 恒可传输，且其唯一传输为**共轭**：

$$
T_{R_\delta}(\varepsilon) \;=\; \delta^{-1} \otimes \varepsilon \otimes \delta
$$

- **(c)** `T_{R_δ} = id ⟺ δ 中心`（`∀ε: ε⊗δ ≈ δ⊗ε`）；全部追加传输平凡 ⟺ D 交换（≈ 意义下）。

证明。

(a) `L_δ(d ⊕₂ ε) = δ ⊗ (d ⊗ ε) ≈ (δ ⊗ d) ⊗ ε`（A3），即 `L_δ(d) ⊕₂ ε`；T2 取 `T = id` 成立，唯一性由左消（可逆型下左乘 δ⁻¹）给出。∎

(b) 逐行核对：需要 `T(ε)` 满足

$$
R_\delta(d \oplus_2 \varepsilon) \;\approx\; R_\delta(d) \oplus_2 T(\varepsilon)
\;\Longleftrightarrow\;
(d \otimes \varepsilon) \otimes \delta \;\approx\; (d \otimes \delta) \otimes T(\varepsilon)
$$

左乘 `(d⊗δ)⁻¹ = δ⁻¹⊗d⁻¹`（引理 0）：

$$
\delta^{-1} \otimes d^{-1} \otimes d \otimes \varepsilon \otimes \delta \;\approx\; T(\varepsilon)
\;\Longrightarrow\;
T(\varepsilon) \;\approx\; \delta^{-1} \otimes \varepsilon \otimes \delta
$$

结果与基点 d 无关（基点无关性自动成立，与定理 1 一致）。验证 T1：`T(1) = δ⁻¹⊗1⊗δ ≈ 1` ✓；验证乘法性：`T(ε₁⊗ε₂) = δ⁻¹ε₁ε₂δ ≈ (δ⁻¹ε₁δ)⊗(δ⁻¹ε₂δ) = T(ε₁)⊗T(ε₂)` ✓（中段 `δδ⁻¹≈1`）。∎

(c) `T_{R_δ} = id ⟺ ∀ε: δ⁻¹εδ ≈ ε`，左乘 δ 得 `εδ ≈ δε`，即 δ 中心。∎

**推论 6.1（差量包间插入 = 共轭）**。把二阶修改 ε 插入既有链 `Δᵢ` 与 `Δᵢ₊₁` 之间，等价于把它**移到下游之后**再共轭：

$$
(\Delta_i \otimes \varepsilon) \otimes \Delta_{i+1}
\;\approx\; \Delta_i \otimes (\varepsilon \otimes \Delta_{i+1})
\;\approx\; \Delta_i \otimes \big(\Delta_{i+1} \otimes (\Delta_{i+1}^{-1} \otimes \varepsilon \otimes \Delta_{i+1})\big)
\;\approx\; (\Delta_i \otimes \Delta_{i+1}) \otimes (\Delta_{i+1}^{-1} \otimes \varepsilon \otimes \Delta_{i+1})
$$

即"在 Δᵢ 之后插入 ε"与"先保持原链、再追加共轭差量 `Δᵢ₊₁⁻¹εΔᵢ₊₁`"产生同一结果。**二阶修改穿过既有差量链的代价是共轭**——共轭由被越过的下游差量决定。

**推论 6.2（顺序依赖判据）**。主文章 §4"组合具有方向和顺序"（`A ⊕ B ≠ B ⊕ A`）与差量幺半群的非交换性是同一现象：

$$
A \oplus B \approx B \oplus A \;(\text{对一切结构}) \;\Longleftrightarrow\; A \otimes B \approx B \otimes A
$$

证明。(⇐) 由 A2 与 A4。(⇒) 设 `∀s: (s⊕A)⊕B ≈ (s⊕B)⊕A`，由 A2 得 `∀s: s⊕(A⊗B) ≈ s⊕(B⊗A)`，再由逐点忠实得 `A⊗B ≈ B⊗A`。∎

共轭 `δ⁻¹εδ` 即非交换度的代数载体。CRDT 的 join 恰是**交换 + 幂等**的差量幺半群——即定理 6(c) 障碍完全消失的特例，CRDT 的收敛保证由此在 GRC 分类学中获得位置。

**推论 6.3（transportable ⊋ delta-expressible）**。定义 P **delta-expressible**：`∃δ_P: P(s) ≈ s ⊕ δ_P`（P 是"追加一个固定差量"）。则 `R_δ` 是可表达且传输为共轭；`L_δ` 可传输但一般不可表达（`δ⊗s = s ⊕ (s⁻¹δs)` 依赖 s）。可传输性是比可表达性更宽的一类——**差量世界中的"合法变换"多于"单个差量能写出的变换"**。

## 8. 定理 7：边界定理——可传输 ⇒ PDP（有损 ⇒ 不可传输）

设 `P : S → S` 为确定性映射（保 ≈），并定义 **PDP（potential-delta-preserving）**：对一切 s₁, s₂, d，

$$
P(s_1) \approx P(s_2) \;\Longrightarrow\; P(s_1 \oplus d) \;\approx\; P(s_2 \oplus d)
$$

**定理 7（单向边界）**：A 强可逆 + 逐点忠实时，`P 可传输 ⟹ P 满足 PDP`。

证明：若 `(P, T_P)` 是态射且 `P(s₁) ≈ P(s₂)`，则

$$
P(s_1 \oplus d) \approx P(s_1) \oplus T_P(d) \approx P(s_2) \oplus T_P(d) \approx P(s_2 \oplus d)
$$

（第二步由 A4 第一参数同余，第三步由 T2 反向。）∎

**注意（反向不成立）**：PDP ⇏ 可传输。反例：`S = D = ℤ`，`⊕ = ⊗ = +`，`≈ = 相等`，`1 = 0`（满足 A1-A4、强可逆、逐点忠实）；`P(s) = s mod 2`。PDP 成立：`P(s₁)=P(s₂) ⟺ s₁≡s₂ (mod 2)`，故 `P(s₁+d) = P(s₂+d)`。但 P 不可传输：若 T 存在，基点 s=0 要求 `P(d) = 0 + T(d)` 即 `T(d) = d mod 2`；基点 s=1、d=1 要求 `P(2) = P(1) + T(1)` 即 `0 = 2`，矛盾。**完整的可传输判据是定理 1 的基点无关性**（此处 `∂P_s(d) = P(s+d) − P(s)` 在 d 为偶时恒为 0、d 为奇时随 s 在 {1, −1} 间交替）；PDP 是传输的必要条件而非充分条件。

**推论 7.1（S-N-V 次序是被定理强制的）**。proof-v2 §2.4 的"有损投影"定义（`π(s₁)=π(s₂)` 但后续差量区分二者）正是 PDP 的否定。由定理 7 的逆否：**有损 ⟹ 不可传输**。规范化（展开简写、应用默认值）、验证、投影 Pr 一般而言有损（它们的输出压掉了后续差量可能依赖的证据），因而**被本定理证明为不可差量表达**——它们不是"暂时没有对应的差量语法"，而是"在差量代数的边界之外"。这就是 S-N-V 三阶段次序的代数必然性：所有差量合并必须先于任何规范化/验证/投影。`x:gen-extends`（合并前生成基线，可传输时其效果可上游表达）与 `x:post-extends`（合并后统一增强，一般不可传输，其效果**原则上**不能写成上游差量）的差别由此获得判据——`x:post-extends` 是合法元编程，但它的产物在差量体系中"无坐标"。

**注**：反例同时给出了一个重要区分：存在**不丢 PDP 证据但不可传输**的映射（mod 2）。因此"差量体系边界"比"证据丢失边界"更窄：不是只有丢证据的操作才必须留在差量体系之外，凡变化导数随基点漂移的操作（即使保留 PDP）同样不可传输。

## 9. Koopman 对应与经验传输（DMD 类比）

### 9.1 对应表

| Koopman 谱系 | 差量传输理论 | 仓库锚点 |
|---|---|---|
| 流 φ_t : M → M | 生成器 G : S_X → S_Y | `Generator<DSL>` |
| 可观测量 g | 差量 d | `Δ` |
| Koopman 算子 K g = g∘φ | 传输 T_G | `transport_G`（本文使之可计算） |
| 线性性 K(g₁+g₂)=Kg₁+Kg₂ | 等变性 G(s⊕d)≈G(s)⊕T(d) | T2 |
| 本征函数坐标（对角化） | 稳定坐标 / 交换差量幺半群 | XDef stable key / 定理 6(c) |
| 有限维不变子空间 | Im(T) 子幺半群（上游可表达闭包） | 定理 3 |
| DMD：数据驱动估计 K | 经验传输估计（见 9.2） | 本文 |
| 精确线性化锚点（无穷维函数空间） | 比特空间 XOR 锚点（强可逆+群+交换） | 主文章 §1.4 |
| 线性化判据 | 传输条件（∂G_s 基点无关） | 定理 1 |

### 9.2 经验传输估计（DMD 式算法）

**算法（x-diff through generator）**：给定生成器 G 与采样基点 s₀ 及候选差量 d₁…dₖ：

1. 计算 `T̂(dᵢ) := G(s₀ ⊕ dᵢ) ⊖ G(s₀)`（即"通过生成器的结构 x-diff"）；
2. **一致性检验**：换基点 s₁ ≠ s₀ 重算；若 `T̂` 与基点无关，则由定理 1 它就是真正的传输 T，对**一切**基点有效（注意：抽样一致性是工程检查手段，严格保证由定理 1 的全称陈述承担，抽样不足以构成证明）；
3. 若不一致，G 不可传输——此时 `T̂` 的基点方差本身就是传输失败的定量信号（未来工作的度量起点）。

这与 DMD 从轨迹数据估计 Koopman 算子、以谱一致性检验其有效性的结构完全同构。

### 9.3 与 Change Actions 的桥接

Change Actions 的 `f(a ⊕ δa) = f(a) ⊕ ∂f(a, δa)` 中，`∂f(a, δa)` 即本文 `∂G_s(d)` 的逐点版本。**定理 1 把 Change Actions 全局化**：变化导数与基点无关 ⟺ 存在全局差量传输；逐点导数可全局化时，`T = ∂G`。这使主文章 §8.1"Change Actions 是 GRC 的局部"的论断有了精确的桥接定理。

## 10. 差量空间分类学

| 空间 | 强可逆(⊖) | 可逆型(逆) | 交换 | 逐点忠实 | 工程对应 |
|---|---|---|---|---|---|
| 比特空间（XOR） | ✓ | ✓ | ✓ | ✓ | 存在性锚点（定理 1 检查域） |
| tree-delta（proof-v2） | ✓（Replace） | ✗（tombstone） | ✗ | ✗（仅全局忠实，见 §1.2） | Nop x-extends |
| Cordis effect | 仅可达差 | ✓（disposer） | 按坐标（其 Thm 40） | ✓（外部断言，仓库内无可验证证据） | DSH 运行时结构空间 |
| CRDT join | ✗（仅 s₁⊑s₂ 时有解） | ✗（幂等） | ✓ | ✗（`s⊔d=s=s⊔d'` 但 `d≠d'`） | 协同编辑（定理 6(c) 障碍消失特例） |

## 11. 与仓库既有材料的关系（新颖性台账）

- **transport_G 律的陈述**（非形式化）：`reversible-computation-a-paradigm-manifesto.md:100`、`grc-universal-software-construction-theory.md:888`、`reversible-compuation-vs-bidirectional-transformation.md:244`。本文不重复陈述，而给出其**存在性判据、唯一性、复合律、闭包律与边界**。
- **审计确认的缺口**：`grc-question-answer-audit.md:400-406`（"更像工程准则而不是完整形式系统。状态：部分回答"）、`:950-958`（变化经济性"合理的新理论补充点"）。本文即该缺口的填补提案。
- **与 proof-v2 的关系**：其 ≈ 是按全局作用定义的商等价（全局忠实，见 §1.2 的逐点忠实限定）；其定理 1/2 即本文 A2/A3——本文以 tree-delta carrier 为定理 5 自作用与分类学第二行的实例。
- **与 grc-delta-associativity-formal-proof 的关系**：其 A8（"任意 End(S) 复合只给出语义组合，不自动给出可序列化 DSL 差量文本"）与本文定理 3/推论 6.3 互补：A8 警告语义闭包 ≠ 语法闭包；本文给出语法层传输存在的精确条件（定理 1）。
- **与 dsh 文章的关系**：其 §4.2 坐标独立性（Cordis Theorem 40）是本文"交换差量幺半群"的运行时实例；其 §4.4"有逆而无独立负元素"即本文分类学第三行。
- **本文新增（仓库中此前不存在）**：定理 1-7 全部、引理 0-3、ΔAct 范畴、共轭公式 `δ⁻¹εδ`、PDP 边界条件、经验传输算法、分类学表、Koopman 对应表。底层代数（actegory/群作用）是初等数学，正如 Koopman 的线性性是初等数学——贡献在于组织、判据与工程后果，这一点在论文定位中应如实陈述。

## 12. Open Problems

1. **细化单调性（猜想，精确陈述）**：设细化映射 `π : (S', D') → (S, D)`（S' 细于 S，D' 细于 D）。猜想：存在 G 在粗空间 (S, D) 不可传输、但在细化空间 (S', D') 中可传输（对应"提升可观测量以线性化"）。比特空间不是万能的：它使 ∂G 恒可定义，但传输存在性仍取决于 G 本身（§3 例）——"锚点提供可检查性，不提供可传输性"。
2. **定量传输**：在概率变化模型下定义信息损失（如 `1 − I(d; T(d))/H(d)`），delta-conservative ⟺ 零损失；§9.2 的基点方差是候选度量。
3. **统一元定理**：tree-delta 与 Cordis 作为同一个"分次（graded）差量 actegory"（坐标层 + 组合层分离）的两个实例的完整证明。
4. **与 Lens 的桥接**：δ-lens 的编辑传播与 T 的关系；定理 4 的表象等价可借 Lens 定律强化。

## Conclusion

本文把 GRC 中被陈述为原则的 `transport_G` 发展为一套可检查的定理系统：传输存在 ⟺ 变化导数基点无关（定理 1）；传输随生成器复合（定理 2，ΔAct 范畴）；上游可表达差量闭包为子幺半群（定理 3）；delta-conservative 转换器使 x-diff 与表象转换交换（定理 4）；差量的差量是幺半群自作用，左平移传输平凡而右平移传输为共轭（定理 5/6）；有损 ⇒ 不可传输（定理 7，单向；反向被 ℤ mod-2 反例证伪，完整判据回到定理 1），从而 S-N-V 次序是代数必然。这是对 Koopman 研究纲领的结构性仿写：锚点（比特空间）提供可检查性，判据（定理 1）提供检验方程，函子结构（ΔAct）提供组合律，实例（tree-delta/Cordis）提供工程锚定。

## References

- `docs/theory/proof-v2.md`（tree-delta carrier 条件化结合律）
- `docs/theory/grc-delta-associativity-formal-proof.md`（三 carrier 预合并结合律，A8 语义/语法闭包区分）
- `docs/theory/generalized-reversible-computation-paper-v2.md`（形式核心与工程映射）
- `docs/theory/grc-question-answer-audit.md`（缺口认定：:400-406、:950-958）
- `docs/theory/reversible-computation-a-paradigm-manifesto.md`（宽松同态定律陈述）
- `docs/theory/reversible-compuation-vs-bidirectional-transformation.md`（同态传递原则与 δ-lens）
- `ai-dev/articles/grc-universal-software-construction-theory.md`（§8.6 transport 陈述、§4 顺序依赖、§7.5 多重表象、§8.1 Change Actions）
- `ai-dev/articles/dsh-architecture-from-reversible-computation.md`（Cordis effect/coeffect 运行时空间分析）
- Koopman 谱系：Mezić (2005), Budišić et al. (2012), Schmid (2010) DMD（本报告仅取其研究纲领结构，未引入其技术细节）
