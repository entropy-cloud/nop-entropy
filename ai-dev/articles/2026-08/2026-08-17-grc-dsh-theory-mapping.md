# 可逆计算（GRC）与 dsh/Cordis 论文：dsh 作为 GRC 在运行时空间的细化

> 日期：2026-08-17
> 关联文档：
> - Cordis/dsh 论文副本：[A Programming Paradigm for Spatiotemporal Composability](../../references/cordis-paper/README.md)（下文简称 Cordis 论文或 dsh 论文）
> - GRC 主论文（英文）：`docs/theory/paper/generalized-reversible-computation-paper-en.md`
> - GRC 中文长文：`docs/theory/generalized-reversible-computation-paper-v2.md`
> - GRC 形式证明：`docs/theory/proof-v2.md`、`docs/theory/grc-delta-associativity-formal-proof.md`

## 摘要

本文论证一个核心观点：**GRC（广义可逆计算）是统一的总理论，dsh/Cordis 论文是 GRC 在运行时空间的一个细化（refinement），不是另一套互补理论。** 核心模式 `App = F(X) ⊕ Δ`（Δ 有逆、F 满足结合律）普适于结构空间与运行时空间。

1. **GRC 的 `F(X)+Δ` 模式是普适的**——结构空间：X=结构树、Δ=树差量、F=x-extends、逆=x-diff（Nop 路线）；运行时空间：X=运行时上下文（coeffect table / fiber state）、Δ=带逆的 effect、F=twisted composition、逆=dispose/accumulator（dsh 路线）。**两者同源**，差异仅在 X 与算子的具体化。
2. **GRC 的核心创新是「主动构造 Delta 结构空间」（Active Space Design）**——见 GRC 主论文 §B.1.3 与 §B.2 评价判据第 2 条；这一原则同时适用于结构域与运行时域。**Nop 在结构域主动设计了 XDef / XDSL 语义坐标系（`x:extends` 作用空间）**；**dsh 在运行时域主动设计了 fiber / scope / realm / service key / event name 的运行时坐标系（`ctx.effect` 作用空间）**。两者不是"被动的执行工具"，而是各自领域的"主动设计变化空间"。
3. **GRC 的"Loader = Generator"原则**（见 `counterintuitive-software-design-insights.md:252`）在两个域同样成立：Nop 的 `ResourceComponentManager` 在结构域把加载器升级为生成器（合并 Delta ⊕ Base 构造完整模型）；dsh 的 `ctx.effect` + fiber 生命周期在运行时域把 effect 注册升级为生成器（注册 Δ + 收集逆 = 在运行时坐标系上叠加 delta）。
4. **dsh 不是 GRC 的应用实例而是细化实例**——fibers / scopes / realm / inertia / committed view / Theorem 61/63/66/73 都是 GRC 概念在运行时域的具体形态，不是"另一套理论"。
5. **Nop 与 dsh 是 GRC 在两个不同域的细化形态**——Nop 把 F(X)+Δ 实例化到结构空间（`proof-v2.md` 与 `grc-delta-associativity-formal-proof.md` 给出了结合律形式证明）；dsh 把 F(X)+Δ 实例化到运行时空间（Theorem 5/61/63/66/73 给出了运行时域的形式化）。
6. **dsh 的运行时形式化应被 GRC 主论文吸收为"运行时章"**——目前 GRC 的运行时部分是机制描述（`reversible-computation-runtime-evolution.md`），未给出定理级形式化；dsh 恰好补上了这一章。

dsh 论文的几乎每个核心观念（时间可组合性、空间可组合性、twisted composition、track 幺半群同态、recover、观测等价、系统边界、withhold/compensation、局部可逆复合）都能在 `docs/theory` 下的 GRC 文献中找到先行表述。本文逐项映射 Cordis 论文核心概念到 GRC 文献中的对应表述，区分哪些是"GRC 概念在运行时域的细化"、哪些是"dsh 对 GRC 模式的具体实现选择"。

**姊妹篇**：与本文配套的 `2026-08-17-dsh-architecture-from-reversible-computation.v2.md` 是工程视角——基于 dsh 源码核实给出具体工程判断；本文是理论视角——把这些工程判断对应到 GRC 总理论的运行时域细化形态。

---

## 一、结论先行

1. **GRC 是统一的总理论**，核心模式 `App = F(X) ⊕ Δ`（Δ 有逆、F 满足结合律）普适于结构空间与运行时空间。Nop 与 dsh 都是该模式在不同域的细化实例，**没有"两个理论"的问题**。
2. **GRC 的核心创新是「主动构造 Delta 结构空间」（Active Space Design）**——见 GRC 主论文 §B.1.3 与 §B.2 评价判据第 2 条：「是否引导开发者主动构造一个承载变化的、具有优良性质的表达空间」。**这一原则同时适用于结构域与运行时域**：Nop 在结构域主动设计了 XDef / XDSL 语义坐标系（`x:extends` 作用空间），dsh 在运行时域主动设计了 fiber / scope / realm / service key / event name 的运行时坐标系（`ctx.effect` 作用空间）。**两者不是"被动的执行工具"，而是各自领域的"主动设计变化空间"**。
3. **GRC 的"Loader = Generator"原则**（见 `counterintuitive-software-design-insights.md:252`：「在可逆计算的架构下，加载器已经被赋予了生成器的全部职责，它的行为已经从被动读取转变为主动构造」）在两个域同样成立：Nop 的 `ResourceComponentManager` 在结构域把加载器升级为生成器（合并 Delta ⊕ Base 构造完整模型）；dsh 的 `ctx.effect` + fiber 生命周期在运行时域把 effect 注册升级为生成器（注册 Δ + 收集逆 = 在运行时坐标系上叠加 delta）。
4. **dsh 是 GRC 在运行时空间的细化**：X = 运行时上下文（coeffect table / fiber state）、Δ = 带逆的 effect、F = twisted composition、逆 = dispose / accumulator。fibers / scopes / realm / inertia / committed view / Theorem 61/63/66/73 都是 GRC 概念在运行时域的具体形态。
5. **Nop 是 GRC 在结构空间的细化**：X = 结构树、Δ = 树差量、F = x-extends、逆 = x-diff。`proof-v2.md` 与 `grc-delta-associativity-formal-proof.md` 给出了 GRC 在结构域的结合律形式证明。
6. **GRC 的现有形式证明集中在结构域**（`proof-v2.md`、`grc-delta-associativity-formal-proof.md`），运行时域目前只有机制描述（`reversible-computation-runtime-evolution.md`）。dsh 论文的 Theorem 61/63/66/73 恰好是 GRC 在运行时域需要的定理级形式化，应被 GRC 主论文吸收为"运行时章"。
7. **形式化对象不同不构成理论不同**：Nop 与 dsh 的差异是"对什么 X、什么 F、什么 Δ 的逆"——这是细化层面的选择，不是理论框架层面的取舍。

---

## 二、GRC 自身的论文与形式证明基础

在对比之前，先明确 GRC 侧有哪些"正式"文献，而不仅是博客式文章：

| 文献 | 路径 | 性质 |
|---|---|---|
| GRC 主论文（英文） | `docs/theory/paper/generalized-reversible-computation-paper-en.md` | 系统提出 GRC：`App = Generator<DSL> ⊕ Δ`、Delta Algebra、R/I 边界治理 |
| GRC 中文长文 | `docs/theory/generalized-reversible-computation-paper-v2.md` | 以语义坐标系为主线，给出"可逆"的分级含义、差量空间质量表、S-N-V 阶段分离 |
| Tree-Delta 结合律证明 | `docs/theory/proof-v2.md` | 证明 tree-delta carrier `D` 对 `⊗` 封闭，且 `⊗` 在语义等价商 `D/≈` 上满足结合律 |
| 条件化结合律证明 | `docs/theory/grc-delta-associativity-formal-proof.md` | 证明三类抽象 carrier（有限潜在偏函数、逐坐标局部操作幺半群、潜在树状态空间端函数）的结合律，并给出 Nop/XLang 继承定理的实现符合性条件 |
| 方法论来源 | `docs/theory/methodology-source.md` | 熵增原理、狄拉克图景、"分离到可逆"、可逆性的复合性 |
| 核心概念澄清 | `docs/theory/what-does-reversible-mean.md` | `App = Delta x-extends Generator<DSL>`、`Delta = App x-diff Base`、逆元与逆运算 |
| 差量概念辨析 | `docs/theory/explanation-of-delta.md` | 结构空间、坐标系、稳定 key、差量与全量同构 |
| 运行时演化 | `docs/theory/reversible-computation-runtime-evolution.md` | Delta Loader、依赖追踪与缓存失效、时间静止、无状态与不可变 |
| 自我审计 | `docs/theory/grc-question-answer-audit.md` | 系统记录 GRC 的开放问题与"部分回答"边界 |

要点：**GRC 不是只有哲学类比，它有明确的代数对象和形式证明。** 只是其形式证明的对象是**结构空间 tree-delta 的组合代数**，而不是运行时状态机。

---

## 三、dsh 论文核心观念 → GRC 表述映射

逐项对照 Cordis 论文的核心概念，看 GRC 文献中是否已有表述。

**对应度图例**（基于"GRC 是统一总理论、dsh 是其运行时域细化"的视角）：

| 标记 | 含义 |
|---|---|
| ◼ 形式化同构 | GRC 的同一概念，结构域与运行时域两端都给出定理级形式化 |
| ◼ 概念同构 | GRC 的同一概念，结构域与运行时域两端均已表达，形式化深度不同 |
| ▣ GRC 概念 + dsh 运行时域形式化 | GRC 概念在 dsh 运行时域的具体形态；dsh 提供定理级形式化，GRC 目前只有机制描述或部分形式化 |
| ▣ 机制同源 | 同一思想，机制可对应但形式化层次不同 |
| ▢ 概念对应 | GRC 概念已表达但双方均未形式化 |

| dsh / Cordis 论文 | GRC / docs-theory 对应表述 | 对应度 |
|---|---|---|
| **【GRC 判据 #2：主动空间设计】**：dsh 主动设计了 fiber / scope / realm / service key / event name 的运行时坐标系，effect 注册是在该坐标系上叠加 Δ | GRC 主论文 §B.1.3：「主动地设计一个承载变化的、具有优良语义坐标的结构空间」；§B.2 判据 #2「Active Space Design」；附录 C.2：「主动地构造一个更适合当前领域的差量空间」 | ◼ 概念同构（Nop 在结构域主动设计 XDef/XDSL 坐标系；dsh 在运行时域主动设计 fiber/scope/realm 坐标系——两者是 GRC 同一原则在不同 X 上的实例化） |
| **【Loader = Generator】**：`ctx.effect` + fiber 生命周期 = dsh 在运行时域把 effect 注册升级为生成器（注册 Δ + 收集逆 = 在运行时坐标系上叠加 delta） | `counterintuitive-software-design-insights.md:252`：「在可逆计算的架构下，加载器已经被赋予了生成器的全部职责，它的行为已经从被动读取转变为主动构造」；`reversible-computation.md:256`：「主动构造一个指定的差量切片出来...差量构成了一个异常丰富的结构空间」 | ◼ 概念同构（Nop 的 `ResourceComponentManager` 是结构域的 Loader=Generator；dsh 的 `ctx.effect` 是运行时域的 Loader=Generator） |
| **时间可组合性**：每个 effect 携带逆函数，卸载时恢复 | `methodology-source.md`："任何增加的功能都应该有配对的逆向取消机制"；Command 模式 `execute/undo` 配对，BatchCommand 复合 | ◼ 概念同构（GRC 是设计原则，dsh 是运行时强制） |
| effect 类型 `Γ → Γ × (Γ → Γ)`：变换 + 逆成对出现 | `explanation-of-delta.md` 的逆元讨论；`what-does-reversible-mean.md` 的 `x-extends`/`x-diff` 双向运算 | ◼ 概念同构（X 不同：GRC 是结构树，dsh 是上下文） |
| **twisted composition**：`(f₁,g₁)∘(f₂,g₂)=(f₁∘f₂,g₂∘g₁)` | `proof-v2.md`：`⊗` 是 delta 之间的内部组合，不是外在函数复合；`methodology-source.md`："可逆性可以复合" | ◼ 形式化同构（GRC 有结构域 `⊗` 结合律证明，dsh 有运行时 `𝔗Γ` 幺半群同态定理） |
| **track 是幺半群同态**（Theorem 5）：逆的复合等于复合的逆 | `proof-v2.md`：`⊗` 在 `D/≈` 上满足结合律；`grc-delta-associativity-formal-proof.md` 的逐坐标局部操作幺半群 | ◼ 概念同构（结构域证明的是 `⊗` 结合律，运行时域证明的是 track 同态——两者是 F(X)+Δ 中 F 在不同 X 上的可逆性形式化） |
| **recover**：应用累积逆恢复到初始状态 | `what-does-reversible-mean.md`：`Delta = App x-diff Base`；GRC 主论文：`Base = App - Δ`（semantic rebase / safe stripping） | ◼ 概念同构 |
| **观测等价 ≃**：不要求物理状态字面恢复（free/malloc 例子） | `proof-v2.md` 的语义等价商 `D/≈`；GRC v2 论文的 Lax Lens `≈` 语义往返；v2 §2.5："可逆不是运行时指令级双射……是一组构造期能力和工程承诺" | ◼ 概念同构（结构域的语义等价 `≈` 与运行时域的观测等价 `≃` 是 GRC 等价关系在不同 X 上的实例化） |
| **系统边界**：acquisition 在界内、emission 跨界不可逆 | GRC 主论文 §5.4："R/I Partitioning：把系统划分为可逆核心与不可逆边界；Boundary Management：审计所有跨越 I-Boundary 的操作并生成证据对象"；`embracing-grc-first-principles.md` 的 R-Core/I-Boundary | ◼ 概念同构（GRC 的 R/I 边界是工程治理概念，dsh 的系统边界是其在运行时域的具体化） |
| **withhold / compensation**：保留或补偿 | GRC 主论文 §5.3："Compensation operations based on evidence objects"；v2 论文 §8.4："删除无前像、外部副作用、数据库迁移和第三方系统调用都可能不可逆。GRC 对这些边界的策略是记录证据、构造补偿" | ◼ 概念同构 |
| **局部可逆复合为整体可逆**（Theorem 61/63/66 的目标） | `methodology-source.md`："如果每个部分都可逆，且部分之间的结合关系也可逆，则系统整体可逆"；`grc-and-nop-a-new-software-construction-paradigm.md` 的"局部可逆性"（记录前像可构造 Δ⁻¹） | ▣ GRC 概念 + dsh 运行时域形式化（GRC 在结构域有 Δ⁻¹ 局部性的讨论，dsh 在运行时域给出 Theorem 61/63/66/73 的定理级证明） |
| **spatial composability / 依赖声明与解析** | GRC 的"语义坐标系 + 稳定 key"：变化必须先落在可寻址坐标上；`xdsl-design.md`、`deep-dive-into-xdef.md` 的 XDef 唯一属性 | ▣ GRC 概念 + dsh 运行时域形式化（GRC 有坐标系思想但无 coeffect 规范/解析/隔离的形式化；dsh 给出 coeffect specification + realm 隔离的具体形式化） |
| **reactive coeffects**：依赖变化主动通知、自动卸载依赖者 | `reversible-computation-runtime-evolution.md` 的 Delta Loader 依赖追踪 + 缓存失效 + 重新生成 | ▣ GRC 概念 + dsh 运行时域形式化（GRC 有依赖失效时的失效重算机制，dsh 给出主动响应式 + 自动卸载依赖者的形式化） |
| **声明式配置 / reconciliation / HMR** | `reversible-computation-runtime-evolution.md` 的 `FinalModel = Loader(Delta) ⊕ Loader(Base)`；GRC v2 的 S-N-V 阶段分离；Nop `ResourceComponentManager` | ◼ 概念同构（dsh 的 per-field reconciliation 与 GRC 的 `FinalModel = Loader(Delta) ⊕ Loader(Base)` 是同一思想在不同域的实现） |
| **坐标**：entry id / 服务 key / 事件名 | GRC v2 §3.1 的差量空间质量表：文本行坐标 → 文件层坐标 → DSL 语义坐标；"语言即坐标系" | ◼ 概念同构（dsh 的 entry id 落在 GRC 的"文件系统层/插件行"这一粗粒度档位；运行时域的服务 key / 事件名是 GRC "坐标系"思想在动态结构上的细化） |
| **组件/纤维实例化**：一个组件多个 fiber | `reversible-computation-runtime-evolution.md` 的柯里化：`Component = curriedRender(schema)`，`Component(data)` 是实例化 | ▣ GRC 概念 + dsh 运行时域形式化（GRC 有"组件=部分应用的生成器"思想，dsh 给出 fiber 生命周期 + 多次实例化的形式化） |
| **运行时结构空间的可逆性**（dsh 论文的主题） | `reversible-computation-runtime-evolution.md` 的"无状态 + 不可变 + 重新生成 + 时间静止"；GRC 主论文的 S-N-V 阶段分离 | ▣ GRC 概念 + dsh 运行时域形式化（GRC 在结构域以"重新生成"实现运行时可逆；dsh 在运行时域以可逆 effect + fiber 状态机实现——同一个 GRC 模式的不同实例化） |
| **inertia / epoch / committed view / accumulator** | GRC 主论文的"时间静止"原则（`reversible-computation-runtime-evolution.md`）；`methodology-source.md` 的"分离到可逆"——每个 fiber 的 accumulator 是 GRC Δ⁻¹ 在 fiber 卸载时的累积，committed view 是 F(X)+Δ 中"Δ 应用后 X 的固化状态"，inertia 是"转换窗口内冻结 X"的工程实现 | ▣ GRC 概念 + dsh 运行时域形式化 |
| **Theorem 61/63/66/73 元理论四条**（恢复精确性 / 排序 / 进度 / 合流） | GRC 主论文的"局部可逆复合为整体可逆"原则（`methodology-source.md`、`grc-and-nop-a-new-software-construction-paradigm.md` 的"局部可逆性"）；`proof-v2.md` 在结构域证明了 `⊗` 在 `D/≈` 上满足结合律——这是 Theorem 66（合流）的结构域对应 | ▣ GRC 概念 + dsh 运行时域形式化（dsh 给出运行时域的四条定理，GRC 在结构域以 `⊗` 结合律 + "局部可逆性"原则给出对应论证） |
| **dispatch mode 五类（emit/parallel/serial/bail/waterfall）** | GRC 的"作用域 + 范围约束"原则——按 dispatch mode 分类事件消费是 GRC 概念（"差异在哪个域由谁裁决"）在事件分发场景的细化 | ▣ GRC 概念 + dsh 运行时域形式化 |

**关键判断**：在"GRC 是统一总理论"的视角下，**没有任何 dsh 概念是"GRC 未覆盖"的**——所有 dsh 概念都是 GRC 模式在运行时域的具体形态，差异只在形式化深度（GRC 在结构域有定理级证明如 `⊗` 结合律；dsh 在运行时域有定理级证明如 Theorem 61/63/66/73）。两者合起来构成 GRC 在两个域的完整形式化图景，而不是"两个互补理论"。

---

## 四、GRC 有、而 dsh 论文没有或明显更弱的部分

这是"GRC 更完善"的主要依据：

1. **结构空间差量代数**。GRC 的核心是 `App = Generator<DSL> ⊕ Δ` 以及 `x-extends`/`x-diff` 的代数结构；dsh 论文的结构层只有 entry 树与粗粒度 patch，没有字段级差量、负元素（`x:override="remove"`）或逆向提取。
2. **差量的结合律形式证明**。`proof-v2.md` 和 `grc-delta-associativity-formal-proof.md` 证明了 tree-delta 在语义等价商上的结合律。这是 dsh 论文完全没有覆盖的问题（它的配置层合并甚至不满足结合律，只有"顺序即优先级"）。
3. **领域结构坐标系理论**。GRC 明确讨论坐标质量谱系、稳定 key、差量与全量同构、差量的差量仍是差量；dsh 论文只有 entry id 和服务/事件 key，没有把"坐标"上升为一般理论。
4. **DSL 图册与多阶段生成**。GRC 有横向多 DSL 组合与纵向多阶段软件生产线（`XPage = Generator<XView> ⊕ Δ_page` 等），dsh 论文不涉及。
5. **产品线工程与 DDD 重释**。GRC 能统一解释 Docker、Kustomize、OpenUSD、DDD 聚合、FeatureHouse 等；dsh 论文只解释插件系统与自修改 harness。
6. **R/I 边界的工程治理**。GRC 主论文 §5.4 和 `embracing-grc-first-principles.md` 给出了 R-Core/I-Boundary、证据对象、熵预算、KPI 度量；dsh 论文的 §6.1 只是同一个思想在运行时场景的局部实例。
7. **对"可逆"概念的分级与去歧义**。GRC v2 §2.5 把"可逆"明确分成代数层、表达层、过程层、边界层四级；dsh 论文只在运行时语义层展开。

---

## 五、dsh 论文有、而 GRC 缺或明显更弱的部分

这是"GRC 并非在所有维度都更完善"的主要依据：

1. **运行时纤维演算（fiber calculus）**。dsh 论文给出了一套完整的操作语义：fiber 生命周期状态（Inactive/Loading/Active/Unloading）、L-Begin/L-Unload 等规则、inertia、accumulator、committed view、coeffect table。GRC 没有对应的运行时状态机。
2. **可逆 effect 的运行时代数**。dsh 论文的 twisted composition 幺半群、track 幺半群同态（Theorem 5）、recover 定义，是把"逆的复合"落实到运行时跟踪与恢复的代数；GRC 的代数在结构空间，运行时的 effect 跟踪代数没有对应物。
3. **reactive coeffects 的形式化**。coeffect specification、解析、隔离（Definition 28）、interception、变更分类（activating/deactivating/neutral）在 GRC 文献中没有出现。GRC 处理依赖变化的方式是 Delta Loader 的被动失效重算，而不是组件级主动通知。
4. **运行时元理论四条定理**：
   - Theorem 61（恢复精确性）：某 fiber 的 accumulator 只撤回自己的贡献；
   - Theorem 63（排序）：提供者晚于依赖者卸载；
   - Theorem 66（进度）：任何非静止状态都能继续演化并最终静止；
   - Theorem 73（合流）：动态历史不留下痕迹，静止状态等于从最终配置静态组装。
   
   GRC 文献没有这一组运行时定理。GRC 的运行时演化文章（`reversible-computation-runtime-evolution.md`）是机制说明，不是定理级形式化。
5. **系统边界的运行时语义**。dsh 论文把 acquisition（界内、可逆）与 emission（跨界、不可逆）落实到运行时操作分类，并讨论 output commit 与补偿；GRC 的 R/I 边界是工程治理概念，没有运行时规则。
6. **观测等价作为独立性来源**。dsh 论文用 coeffect 上的观测等价 ≃ 为 effect 独立性提供基础（Definition 33 → Definition 60），这是 GRC 没有展开的构造。

---

## 六、两组形式证明的对比

两者都有自己的形式证明，但证明对象不同，不构成竞争：

| 维度 | GRC 侧 | dsh/Cordis 侧 |
|---|---|---|
| 证明对象 | 结构空间 tree-delta 的组合代数 | 运行时上下文变换的 effect 代数与 fiber 状态机 |
| 核心结构 | `D`（tree-delta carrier）、`⊗`（内部组合）、`Apply`（作用）、`D/≈`（语义等价商） | `𝔗Γ`（twisted composition 幺半群）、`𝜕Γ`（effect context）、fiber registry |
| 核心结论 | `⊗` 在 `D/≈` 上满足结合律；预合并与逐步应用同结果 | track 是幺半群同态；恢复精确性；排序；进度；合流 |
| 等价关系 | 语义等价 `≈`（Lax Lens 语义往返） | 观测等价 `≃`（observer 不可区分） |
| 边界条件 | stable key、确定性线性化、中间不做有损投影、链尾统一验证 | confinement、供应不相交、依赖无环、pairwise independence |
| 空间 | 编译期/加载期结构空间 | 运行时结构空间 |

**两者的关系**：GRC 证明了"结构差量可以安全预合并"；dsh 证明了"运行时注册 effect 可以安全跟踪、恢复与重排"。把两者拼起来，正好是 v2 文章里说的完整图景——**结构空间用差量代数（GRC 已形式化），运行时结构空间用可逆 effect + 响应式 coeffect（dsh 已形式化）**。目前没有一个理论同时覆盖两层；这是下一步理论工作最自然的接口。

---

## 七、dsh 是 GRC「主动构造 Delta 结构空间」原则在运行时域的具体化

**dsh 是 GRC 在运行时空间的细化实例**，与 Nop 是 GRC 在结构空间的细化实例对偶——但 dsh 的真正理论定位不止于"算子对位"，而是 **GRC「主动构造 Delta 结构空间」原则（Active Space Design，GRC 判据 #2）的运行时域具体化**。

- **dsh 主动设计了运行时域的 Delta 结构空间**：fiber / scope / realm / service key / event name / 5 类 dispatch mode——这些不是"运行时偶然产生的状态"，而是 dsh **主动设计**的"承载运行时变化的优良结构空间"。service key 是单写多读坐标，event name 是多写坐标加 dispatch mode 显式声明顺序语义，realm 提供 per-key 互斥的隔离粒度——这正是 GRC 主论文 §B.1.3 所说的"主动地设计一个承载变化的、具有优良语义坐标的结构空间"在运行时域的实例化。
- **dsh 的 `ctx.effect` + fiber 生命周期是「Loader = Generator」原则在运行时域的具体化**：每一个 effect 注册都同时产生 Δ（注册动作本身）与 Δ⁻¹（disposable 收集的逆函数），运行时坐标系上的全部结构就是所有注册叠加的结果——这与 GRC 主论文 §B.1.2「分形自相似性」原则一致：F(X)+Δ 模式在四个维度（垂直流水线 / 水平 DSL 族 / 时间演化链 / 元层工具）中递归展开。
- **理论同源**：GRC 的 `App = F(X) ⊕ Δ` 模式在运行时域的具体化——X = 运行时上下文（coeffect table / fiber state）、Δ = 带逆的 effect、F = twisted composition、逆 = dispose / accumulator。Nop 与 dsh 都是该模式的域细化，**没有"哪个是主理论、哪个是应用"的不对等关系**。
- **形式化补全**：dsh 的 Theorem 61/63/66/73、twisted composition 幺半群同态、recovery exactness，是 GRC 在运行时域需要的定理级形式化——目前 GRC 现有文献对运行时演化的处理是机制描述（`reversible-computation-runtime-evolution.md`），未给出定理。dsh 恰好补上了这一章。
- **不构成"两个理论"**：dsh 的形式化语言（effect `Γ→Γ×(Γ→Γ)`、track 幺半群、fiber 状态机）是 GRC 概念在运行时域的实例化——F 的代数结构（结合律）、Δ 的逆元存在、复合的可逆性保持，这三点在 dsh 中以运行时定理（Theorem 5/61/63/66/73）的形式存在，在 GRC 中以结构域定理（tree-delta 结合律）的形式存在；两者不是不同理论，是同一理论在不同 X 上的形式化。
- **dsh 的配置层未继承 GRC 最成熟的字段级差量代数**——这是 dsh 的实现选择（停在 entry id 粗粒度），不是 GRC 的限制。配置层本身也是 GRC「主动构造 Delta 结构空间」原则作用的一个子空间（`cordis.yml` 是配置域的 DSL 坐标系）；如果 dsh 把 GRC 结构域的 `x-extends` / `x-diff` / `x:override="remove"` 引入到该配置域，就能获得 GRC 结构域的字段级差量代数与结合律保证。

**所以结论是**：dsh 不是 GRC 的"应用实例"也不是"互补理论"，而是 **GRC 「主动构造 Delta 结构空间」原则 + 「Loader = Generator」原则 + F(X)+Δ 模式** 这三大核心原则在运行时域的统一具体化。差异只在于"X 是什么、F 用什么算子、逆如何构造、主动设计的坐标是什么"——这是细化层面的选择，不是理论层面的取舍。

---

## 八、下一步工作

本节给出具体的下一步工作清单，按"哪一方应做什么"分组。

### 8.1 Nop 平台应做的事

1. **把 dsh 论文的 Theorem 61/63/66/73 引入 `nop-plugin/` 子系统**。Nop 已有的 plugin 机制缺少运行时可逆 effect 的正确性判据；可参照 dsh 论文的 confinement + 供应不相交 + 依赖无环三条结构性约束，建立"Nop plugin 正确性四定理"（恢复精确性、排序、进度、合流），作为插件可逆卸载的形式化保证。
2. **把 `x-extends`/`x:override="remove"`/`x-diff` 差量代数引入插件配置层**。若要让 dsh 这类 harness 的插件支持字段级 delta 定制，最直接的路就是把 Nop 已有的结构空间差量代数引入插件配置层；这一点 dsh 论文没有做，而 Nop 已经做了。

### 8.2 GRC 主论文应做的事

1. **吸收 dsh 的运行时形式化为"运行时章"**。GRC v2 的运行时部分（`reversible-computation-runtime-evolution.md`）目前是机制描述；可直接引入 dsh 论文的 fiber calculus 与 Theorem 61/63/66/73 作为 GRC 运行时章的核心，标明"X=上下文、Δ=effect、F=twisted composition"的实例化选择，使 GRC 在运行时域与结构域有对称的形式化深度。
2. **在主论文中明确"理论统一性"**：GRC 是统一的总理论，结构域（X=树、Δ=树差量、F=x-extends）与运行时域（X=上下文、Δ=effect、F=twisted composition）都是 `App = F(X) ⊕ Δ` 模式的不同细化。两个域的差异是细化层面的选择，不构成"两套理论"。建议在主论文中给出两域的形式化对照表，避免读者误读为"GRC 与 dsh 互补"。

### 8.3 dsh 论文应做的事

1. **把 §5.2 reconciliation 推到字段级差量代数**。当前 dsh 论文 §5.2 描述了 entry 树与 reconciliation，但配置层 override 仍停留在"整段 config 覆盖 + insert"的粗粒度，没有形式化为差量代数，也没有说明配置坐标（entry id）与运行时 effect 坐标的概念关系（详见姊妹篇 v2 §5.2 的具体诊断）。这是 dsh 对 GRC 结构域细化的引入选择问题——可直接引入 GRC 的 `x-extends` / `x-diff` 到配置层。
2. **明确"细化实例"定位**：在 dsh 论文中说明 fibers / scopes / realm / inertia / Theorem 61/63/66/73 都是 GRC `App = F(X) ⊕ Δ` 在运行时域的具体形态，避免读者把它们误读为"另一套理论"。

### 8.4 本研究的后续工作

1. **接口点 1（语义等价 vs 观测等价）**：GRC 的 `D/≈`（结构域语义等价商，Lax Lens 语义往返）与 dsh 的 `≃`（运行时观测等价，observer 不可区分）都是 GRC 等价关系在不同 X 上的实例化——结构域的 `≈` 是 GRC "语义往返"原则在树上的实例，运行时域的 `≃` 是同一原则在上下文上的实例。未来 GRC 主论文可统一这两者为"GRC 等价关系在 X 上的实例化"。
2. **接口点 2（差量代数 vs effect 代数）**：GRC 的结构域差量代数 `⊗`（已证 `D/≈` 上结合律）与 dsh 的运行时 effect 跟踪代数 `𝔗Γ`（已证 track 同态）都是 `F(X)+Δ` 中 `⊗` 的域细化——结构域中 `⊗` 是树差量的内部组合，运行时域中 `⊗` 是 effect 的 twisted composition。两者形式上同源，差异在 X 与 F 的具体化选择。
3. **接口点 3（"分离到可逆"的运行时实例化）**：GRC 的"分离到可逆"原则在运行时域的具体化就是 dsh 的 fiber confinement（Definition 48）——每个 fiber 的 effect 只能写自己的 coeffect table，正是 GRC "作用域 + 范围约束"原则在 fiber 模型上的细化形态。
4. **保持谦逊**：`grc-question-answer-audit.md` 已经记录了 GRC 的开放问题清单（坐标迁移演算、cancellation calculus、并发差量、图结构 Delta 代数等）。dsh 论文补上的是"运行时域定理级形式化"这一块，不是 GRC 的全部缺口。

---

## 参考文献

1. Yifan Shi, Wei Zhang, Tianyi Cui. *A Programming Paradigm for Spatiotemporal Composability*. Preprint, 2026. 副本：`ai-dev/references/cordis-paper/spatiotemporal-composability.md`
2. Canonical Entropy. *Generalized Reversible Computation: A New Paradigm for Unifying Software Construction and Evolution*. `docs/theory/paper/generalized-reversible-computation-paper-en.md`
3. Canonical Entropy. *广义可逆计算：以语义坐标系组织软件构造与演化*. `docs/theory/generalized-reversible-computation-paper-v2.md`
4. *附录：GRC/XLang Tree-Delta 结合律的精简形式化证明*. `docs/theory/proof-v2.md`
5. *GRC 抽象差量演算的条件化结合律证明与 Nop/XLang 语义边界*. `docs/theory/grc-delta-associativity-formal-proof.md`
6. *可逆计算的方法论来源*. `docs/theory/methodology-source.md`
7. *可逆计算理论中的可逆到底指的是什么？*. `docs/theory/what-does-reversible-mean.md`
8. *差量概念辨析（不同结构空间的差量）*. `docs/theory/explanation-of-delta.md`
9. *可逆计算如何赋能软件的运行时演化*. `docs/theory/reversible-computation-runtime-evolution.md`
10. *GRC 问题、回答与理解审计记录*. `docs/theory/grc-question-answer-audit.md`
