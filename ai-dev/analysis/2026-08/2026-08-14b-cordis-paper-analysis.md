# Cordis 论文分析：时空可组合性的形式化基础及其与可逆计算的对应

> Status: open
> Date: 2026-08-14
> Scope: 论文 _A Programming Paradigm for Spatiotemporal Composability_（cordiverse/paper，Draft of August 13, 2026，88 页）的精读，及其与 Nop 可逆计算理论的形式化对应
> Conclusion: 论文把动态可组合性形式化为 **monoid（单群）+ 左逆（left inverse）+ 观测等价商**，与可逆计算理论的 **扩展空间（含逆元）+ 差量合并 + 伴随函子等价** 数学骨架高度同构。两者都**明确承认"正向再逆向不完全恢复原样"**——都在某个等价关系下丢弃无关信息使可逆成立，只是判据不同：Cordis 按观测性（遗忘不可观察部分），可逆计算按业务相关性（保留结构、丢失样式等，如 report 例子）。`(data, ext_data)` 只是需逼近严格等式时的可选手段，并非强制保留所有信息。真正的差异在于可逆发生的空间：Cordis 是运行时 context 变换空间 `Γ→Γ`（活的状态），可逆计算是结构空间 XDSL（静态领域坐标系，可独立于运行时）。

## Context

- DeepSeek Harness（dsh）底层是 vendored 的 Cordis，其设计由论文 _A Programming Paradigm for Spatiotemporal Composability_ 描述（`~/ai/deepseek-harness/README.md` 引用 `github.com/cordiverse/paper`）。
- 论文 PDF 已下载至 `_tmp/cordis-paper.pdf`（88 页，2.14MB），全文提取至 `_tmp/cordis-paper.txt`（280637 字符）。本分析所有引用均标注 txt 行号。
- 本文回答：论文到底如何形式化"可逆"？等价性问题（正向再逆向不完全恢复）论文怎么处理？它与可逆计算的形式化有何同构与差异？
- 判断依据：论文 §1 Introduction（txt:92–220）、§3 Revertible Effects（txt:305–490）、§3.3.2 Observational Equivalence（txt:949–999）、§4 Metatheory、§8 Conclusion（txt:3577–3602）；可逆计算 `docs/theory/explanation-of-delta.md`、`docs/theory/faq-about-theory-of-nop.md`。

---

## 一、论文核心：两个正交维度

论文（§1.1，txt:105–120）把动态可组合性分解为两个正交维度：

- **Temporal composability（时间可组合性）**：组件移除时，其对共享环境的修改必须被**完全且安全地逆转**（completely and safely reversed）。需跟踪每个资源分配、事件注册、状态变更，并保证有序回收。
  - 静态退化为词法作用域（RAII、bracket pattern）；动态下须处理**非词法界定的长生命周期有状态 effect**。
- **Spatial composability（空间可组合性）**：组件能以结构化、可验证方式声明、发现、解析彼此依赖。
  - 静态退化为模块导入解析；动态下须处理运行时**出现、消失、变更身份**的依赖。

动机示例（§1.2）：VSCode 插件（temporal 缺陷：无法运行时卸载单个插件代码，须重启 host；spatial 缺陷：扩展间依赖近乎不用、无类型契约）、**Self-Evolving Agent Harnesses（自演化 agent harness）**——dsh 自身的定位。论文明确把 agent harness 的连续自修改作为动态可组合性的旗舰场景（§1.2.2，txt:151–164；Conclusion 再次强调，txt:3595–3602）。

---

## 二、Revertible Effects 的形式化：monoid + 左逆

这是论文的理论核心（§3.1，txt:315–354），也是与可逆计算最直接的可比点。

### 2.1 Effect 即"context 变换 + 显式逆函数"

> "We model an effect as a function of type **Γ → Γ × (Γ → Γ)**: applied to the current context, it yields the modified context together with **an explicit inverse**."（txt:319–322）

- 返回 inverse 让 effect 可被 **revert**，交给 runtime 让 effect 可被 **track**。称这样的 effect 为 **revertible（可逆）**。
- context 状态记 `γ`，inverse 累积器记 `φ`，effect context `∂Γ ≔ Γ × (Γ→Γ)`，初值 `(γ₀, idΓ)`（txt:346–352）。

### 2.2 单群结构（关键！）

Effects 生活在变换的 **monoid（单群）** `(Γ→Γ, ∘)` 下（txt:328–333），三条公理直读为 effect 性质：

- **Closure（封闭性）**：两 effect 顺序复合仍是 effect；
- **Associativity（结合律）**：复合 effect 与括号方式无关；
- **Identity（单位元）**：`idΓ`。

注意：论文用的是 **monoid**，不是 **group（群）**。为建模可撤销，每个变换 `f` 配一个 **left inverse（左逆）** `g`（txt:334–336）：

> "Undoing is one-sided: what an inverse is held to is **g ∘ f and never f ∘ g**."（txt:336）

定义 **twisted composition（扭曲复合）**（txt:338–342）：

```
(f₁,g₁) ∘ (f₂,g₂) := (f₁∘f₂, g₂∘g₁)     // 逆元反序累积
```

构成 **twisted composition monoid 𝔗Γ** = 变换单群与其反向的积。

### 2.3 与可逆计算理论的共鸣（决定性发现）

| 代数性质 | Cordis 论文 | 可逆计算（`explanation-of-delta.md`） |
|---|---|---|
| 代数结构 | **Monoid + 左逆**（非群） | 扩展空间（含负元素） |
| 是否真群 | 否：用左逆 `g∘f`，单向 | 否：幂等删除与群冲突，"不是真正的群结构，只是支持可逆运算的结构" |
| 逆元形式 | 左逆 `g`，过程式函数 | `-F`，结构化差量 |

**结论**：两者都**明确承认实际可逆结构不是严格群**。Cordis 用 monoid + 单向左逆；可逆计算用含逆元的扩展空间但承认幂等性与群冲突（`explanation-of-delta.md` §4 数学证明）。这是数学骨架层面的深层共鸣——殊途同归地认识到"软件中的可逆只能是 monoid 级，达不到 group 级"。

---

## 三、Observational Equivalence：等价性问题（回应用户关切）

§3.3.2（txt:949–999）是论文中与可逆计算等价性讨论最直接对应的一节，也正面回应了"正向再逆向不一定恢复原样"。

### 3.1 论文明确承认 recovery 是理想化

> "The recovery guarantee of Section 3.1 asserts an equality of states (Theorem 7), which is an **idealization**, because **the physical state cannot be recovered as it stood**. For example, free releases a block to the allocator without restoring the layout the heap had before malloc; and a generative name is not restored by the inverse that discards it."（txt:950–954）

> "The equalities of Section 3 are therefore to be read **up to an equivalence ≃**, and we take ≃ to be an **observational equivalence**: two states are related when **no observer can distinguish them**."（txt:954–956）

### 3.2 处理策略：遗忘不可观察部分

> "The part of a state that no key binds is thereby **forgotten**, and **forgetting it is what lets Theorem 7 be read up to ≃ at all**."（txt:968–969）

- 论文选择**商掉**（quotient）不可观察的部分——state 中没有被任何 coeffect key 绑定的字段被遗忘。
- 观测等价 ≃ 由各 coeffect 自带的等价 `≃ₖ` 组装而成（Definition 33，txt:962–967）。

### 3.3 与可逆计算：同一思路，不同丢弃判据（核心洞察）

两者面对**同一个"正向变换信息损失"问题，都明确承认等价而非完全恢复**——并非"遗忘 vs 保留"的对立。差异只在于**判定什么可以丢弃**的标准：

| | Cordis 论文 | 可逆计算（`faq-about-theory-of-nop.md` §2、`explanation-of-delta.md`） |
|---|---|---|
| 等价性来源 | **观测等价 ≃**（no observer can distinguish） | **伴随函子**（`A ≃ Export(Import(A))`） |
| 可丢弃的部分 | **不可观察的**（未被 coeffect key 绑定） | **业务无关的附加信息**（样式、单元格相对位置等） |
| 保留的部分 | 观测者能区分的 | **整体结构信息** |

可逆计算的经典例证正是报表引擎（`faq-about-theory-of-nop.md` §2）：Excel 正向解析为对象、再逆向导出为 Excel，**会丢失/改变部分样式信息**，但"整体结构信息一致"——这本身就是等价，不是完全保留。`(data, ext_data)` 只是**当需要逼近严格等式时**的可选补充手段（补差量 `A+dA = F(B+dB)` 使近似等价成为等式），**并非强制保留所有信息**。

所以两者是**同一思路的不同实例化**：都在某个等价关系下丢弃"无关信息"以使可逆成立，只是"无关"的判据不同——Cordis 按观测性，可逆计算按业务/结构相关性。

---

## 四、空间维度的真正差异

§3 把 effect context（时间）与 coeffect context（空间）统一为单一 **context type**（§3.3，txt:308–314）。这里"空间"指**依赖拓扑**（组件间声明/发现/解析依赖），由 reactive coeffects 实现。

这与可逆计算的"空间"是**不同概念**：

| | Cordis 的 spatial | 可逆计算的结构空间 |
|---|---|---|
| 含义 | **依赖拓扑**（组件间依赖的声明与响应式解析） | **领域坐标系**（XDef 树形结构空间，差量在此运算） |
| 机制 | reactive coeffects（context 变更按 spec 通知组件） | x-extends 差量合并（声明式，在结构层） |
| 可逆发生处 | 运行时 context `Γ`（活的状态空间） | 结构层 XDSL（静态、可独立于运行时） |

**论文的形式化范围 vs dsh 实际架构（重要区分）**：论文 §3 的 revertible effects 只覆盖**运行时层**——context 变换空间 `Γ→Γ`，effect 是过程式函数。但 **dsh 实际还有一个论文未形式化的 YAML 配置层**：profile/bundle/patch 有序差量叠加 `ConfigTree = ⊕ Δᵢ`，声明式、离线、与 effect 系统无关，**正是可逆计算 `App = Delta x-extends Generator<DSL>` 的标准方案**（`composeEntries`/`renderConfigDump` 可不运行任何 effect 纯离线合并）。所以"论文的可逆只在运行时"是对**论文**的准确描述，但**不代表 dsh 没有结构层差量**——dsh 的配置层本身就是结构层差量，只是：(1) 论文未将其纳入形式化范围；(2) 粒度停在**配置行级**（entry id），非领域坐标节点级；(3) 逆元不完整（无 remove/deep-merge）。

与可逆计算的真正差距是**粒度与逆元**：可逆计算用稳定静态的**领域坐标系**（XDef）把结构层差量做到**节点级**细粒度且含结构化逆元；dsh 配置层停在配置行级、逆元不完整，且 **plugin 是粗粒度边界**（论文的 revertible effects 以 component/plugin 为单元整体回退，无法节点级细粒度）。粒度谱系：plugin 整体（粗）→ 配置行/entry（dsh patch）→ 领域坐标系节点（可逆计算 XDef，细）。

---

## 五、元理论保证（§4.4）

论文给出动态组合演算的元理论（txt 目录 §4.4），把时空可组合性从单组件扩展到交错组件系统：

- **Preservation（保持性）**：类型安全在转换中保持；
- **Temporal Composability**：组件移除时环境可恢复（前述观测等价下）；
- **Spatial Composability**：依赖变更正确传播；
- **Progress（进展性）**：系统不会死锁；
- **Confluence（汇聚性）**：交错组件的最终结果与调度顺序无关。

可逆计算没有对应的形式化演算/元理论证明——它的"保证"来自**结构层差量合并的代数性质**（封闭性、结合律、代数吸收）和稳定坐标系，而非运行时演算的 metatheory。这是方法论差异：Cordis 走 **PL 理论（effect/coeffect systems + 演算 + 元理论）** 路线；可逆计算走 **物理学/代数（差量 + 坐标系 + 合并规则）** 路线。

---

## Conclusion

- 论文把动态可组合性的形式化基础确立为：**revertible effects（monoid + 左逆）+ reactive coeffects + 统一 context type + 观测等价商**，配以演算元理论（Preservation/Temporal/Spatial/Progress/Confluence）。
- **与可逆计算的三层同构**：(1) 都用 monoid 级（非 group 级）可逆结构；(2) 都承认"正向再逆向不完全恢复"，都按各自判据丢弃无关信息使等价成立（Cordis 按观测性，可逆计算按业务/结构相关性——report 例子丢样式保结构）；(3) 都有"逆元"概念（Cordis 左逆 `g` vs 可逆计算 `-F`）。
- **真正差异在空间**：Cordis 可逆于运行时 context `Γ→Γ`（活状态，过程式）；可逆计算可逆于结构空间 XDSL（静态坐标系，声明式，可独立于运行时）。Cordis 的 spatial 是依赖拓扑，可逆计算的结构空间是领域坐标系——两者"空间"非同一概念。
- 方法论：Cordis = PL 理论（effect/coeffect + 演算 + 元理论）；可逆计算 = 物理代数（差量 + 坐标系 + 合并规则）。
- 论文 PDF 及全文存于 `_tmp/cordis-paper.pdf` / `_tmp/cordis-paper.txt`，供后续引用。
- 单方面调研，结论可被推翻；暂无后续 plan/design 接手。

## Open Questions

- [ ] Cordis 的 twisted composition monoid 𝔗Γ 与可逆计算的"扩展空间（含负元素）"能否建立形式化映射？左逆 `g`（单向）与逆元 `-F`（双向但幂等）的代数关系如何？
- [ ] Cordis 的观测等价判据（可丢弃 = 不可观察）与可逆计算的业务相关性判据（可丢弃 = 业务无关，如 report 的样式）——能否统一为同一等价框架？两者的"可丢弃集"有何代数关系？`(data, ext_data)` 作为可选的逼近手段，是否对应 Cordis 中"为某个 key 绑定以避免被遗忘"？
- [ ] 可逆计算能否借鉴 Cordis 的 Confluence（汇聚性）证明思路，为结构层差量合并建立"合并顺序无关"的形式化保证？

## References

- 论文原文：
  - `_tmp/cordis-paper.pdf`（88 页，Draft of August 13, 2026）
  - `_tmp/cordis-paper.txt`（全文文本提取）；源 `https://github.com/cordiverse/paper/blob/main/paper.pdf`
  - 关键引用：§1.1 Dimensions（txt:105–120）、§3.1 Revertible Effects（txt:315–354）、§3.3.2 Observational Equivalence（txt:949–999）、§8 Conclusion（txt:3577–3602）
- Nop 可逆计算：
  - `docs/theory/explanation-of-delta.md`（群结构四要素、Whiteout 幂等与群冲突、左逆/逆元、扩展空间含负元素）
  - `docs/theory/faq-about-theory-of-nop.md`（第2题：近似等价、补差量成等式、`(data,ext_data)` 设计）
  - `docs/theory/generic-delta-composition.md`（结构层 vs 对象层、结构/运行时空间分离）
- 关联分析：
  - `ai-dev/analysis/2026-08/2026-08-14-deepseek-harness-vs-reversible-comparison.md`（基于 dsh 实现文档的对比）
