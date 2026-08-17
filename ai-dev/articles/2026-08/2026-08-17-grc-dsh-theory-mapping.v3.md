# 可逆计算（GRC）与 dsh/Cordis 论文：GRC 是总理论，dsh 是运行时域的细化与形式化补充（v3）

> 日期：2026-08-17
> 版本：v3（替代 `2026-08-17-grc-dsh-theory-mapping.md`；依据 `2026-08-17-revision-review.md` 修订）
> 关联文档：
> - Cordis/dsh 论文副本：[A Programming Paradigm for Spatiotemporal Composability](../../references/cordis-paper/README.md)（下文简称 Cordis 论文或 dsh 论文）
> - GRC 英文主论文：`docs/theory/paper/generalized-reversible-computation-paper-en.md`
> - GRC 中文全本：`docs/theory/generalized-reversible-computation-paper.md`（含 §B.1.3、§B.2 十判据、附录 C/D 的最小代数核）
> - GRC 中文长文 v2：`docs/theory/generalized-reversible-computation-paper-v2.md`
> - GRC 形式证明：`docs/theory/proof-v2.md`、`docs/theory/grc-delta-associativity-formal-proof.md`

## 摘要

本文回答一个问题：**可逆计算（GRC）与 dsh/Cordis 论文是什么关系？**

结论是：**GRC 是通用理论；dsh 是 GRC 在运行时域的一个细化实例，并补充了该域目前最完整的定理级形式化。** 核心模式 `App = F(X) ⊕ Δ` 在结构域实例化为 Nop（X=结构树、Δ=树差量、F=x-extends、逆=x-diff），在运行时域实例化为 dsh（X=运行时上下文、Δ=带逆的 effect、F=twisted composition、逆=dispose/accumulator）。两者不是两套理论，也不是"总理论—普通应用"的弱关系，而是**同一范式在两个域的实例化；两个域的形式化目前互补，未来可统一**。

需要特别澄清一点：**reactive coeffects 不是 GRC 的空白，而是 Loader 抽象之下的一个次要实现选择。** GRC 的"Loader = Generator"原则已经隐含"检测变化 → 失效/重载/重建"的抽象；具体采用主动推送（如 Vite 检测到文件变化后主动推送 HMR 更新，dsh 的依赖失效主动 notify）还是被动检查（如 Nop Delta Loader 的时间戳节流检查后整体重新生成），是实现策略问题。GRC 原文没有展开主动推送路线，因为它的核心是差量代数与坐标空间，通知机制属于工程实现层；dsh 在运行时插件图这一特定场景下把主动推送路线形式化了，这是细化，不是对 GRC 的"补缺"。

---

## 一、结论先行

1. **GRC 是通用理论**：`App = F(X) ⊕ Δ` 是软件构造与演化的统一模式；X、F、Δ 的具体选择由"域"决定。
2. **dsh 是 GRC 在运行时域的细化实例**：X = 运行时上下文（coeffect table / fiber state）、Δ = 带逆的 effect、F = twisted composition、逆 = dispose / accumulator。
3. **dsh 补充了运行时域的定理级形式化**：track 幺半群同态（Theorem 5）、恢复精确性（Theorem 61）、排序（Theorem 63）、进度（Theorem 66）、合流（Theorem 73）。GRC 现有文献在运行时域只有机制描述（`reversible-computation-runtime-evolution.md`），没有这些定理。
4. **reactive coeffects 是 Loader 抽象下的次要实现选择，不是 GRC 的空白**：主动推送（dsh/Vite 式）与被动失效重算（Nop 式）都是"变化检测 → 重载"的合法实现；GRC 未展开是因为其核心在差量代数，而非通知机制。
5. **GRC 的形式化不是无条件的**：F 的结合律在受限 carrier 上成立（LWW 覆盖语义直接成立，扩展语义条件化成立）；Δ 的逆是条件化局部逆（记录前像则可剥离，删除无前像则不可逆，中文全本附录 D 的 P10/P11）。这是 GRC 的严谨之处，不是缺陷。
6. **Nop 与 dsh 是 GRC 同一范式在两个域的实例化，形式化目前互补**：结构域由 `proof-v2.md` 与 `grc-delta-associativity-formal-proof.md` 给出结合律证明；运行时域由 dsh 论文给出四条元理论。未来工作是把两者统一到同一分层等价框架。

---

## 二、GRC 的文献与形式化基础

GRC 侧有三份论文和两份独立证明，引用时必须区分：

| 文献 | 路径 | 性质 |
|---|---|---|
| GRC 英文主论文 | `docs/theory/paper/generalized-reversible-computation-paper-en.md` | 系统提出 GRC；含 F.3 "Active Selection and Design of Delta Space"、F.5 判据、附录 C 结合律的直觉论证、§5.4 R/I 边界治理 |
| GRC 中文全本 | `docs/theory/generalized-reversible-computation-paper.md` | 与英文主论文对应但更完整；**含 §B.1.3"主动的差量空间设计"、§B.2 十判据、附录 C"差量空间的选择与构造"、附录 D 最小代数核（P1–P11）** |
| GRC 中文长文 v2 | `docs/theory/generalized-reversible-computation-paper-v2.md` | 以语义坐标系为主线，给出"可逆"的分级含义、差量空间质量表、S-N-V 阶段分离 |
| Tree-Delta 结合律证明 | `docs/theory/proof-v2.md` | 证明 tree-delta carrier `D` 对 `⊗` 封闭，且 `⊗` 在语义等价商 `D/≈` 上满足结合律 |
| 条件化结合律证明 | `docs/theory/grc-delta-associativity-formal-proof.md` | 证明三类抽象 carrier 的结合律，并给出 Nop/XLang 继承定理的实现符合性条件 |

**引用规范**：本文引用"§B.1.3 / §B.2 判据 #2 / 附录 C.2 / 附录 D"均指中文全本 `generalized-reversible-computation-paper.md`；引用"F.3 / F.5 / §5.4"均指英文主论文。不再笼统使用"GRC 主论文"。

---

## 三、GRC 核心模式的精确表述

v2 曾把 GRC 模式写成"`App = F(X) ⊕ Δ`（Δ 有逆、F 满足结合律）"。这个写法过于粗略，按 GRC 论文自己的表述应精确为：

### 3.1 F 的结合律是条件化的

- **核心覆盖语义（LWW）**：直接满足结合律（中文全本附录 D，P3）。
- **扩展语义**：append、around、`replace/remove/merge` 与 children tree，**必须嵌入局部幺半群或统一端函数 denotation 后才继承结合律**（中文全本附录 D.4）。
- 英文主论文附录 C 自己声明：该处是"intuitive argument and design rationale"，完整形式证明留给未来工作；真正的证明在 `proof-v2.md` 与 `grc-delta-associativity-formal-proof.md`，且都针对受限 carrier。

### 3.2 Δ 的逆是条件化的局部逆

- 中文全本术语表：⊕"在**记录必要前像且无信息丢失的条件下**，可以支持局部逆运算或变更剥离"。
- 中文全本附录 D：
  - **P10 局部补偿可逆性**：记录前像则可构造补偿差量 Δ⁻¹ 恢复；否则一般不能保证无损恢复。
  - **P11 不可逆删除**：删除标记 ⊥ 且未记录前像，则不存在 Δ′ 能从 `P ⋄ Δ` 无损恢复 P。
- 英文主论文 §2.2：GRC 的代数性质是"an ideal property achievable through sound design, **not a rigid prerequisite for all scenarios**"。

### 3.3 边界治理

GRC 不追求完全可逆的乌托邦（英文主论文 §5.4）：把系统划分为 **R-Core（可逆核心）** 与 **I-Boundary（不可逆边界）**，跨越边界的操作审计并生成证据对象，用于补偿。这与 dsh 论文 §6.1 的 acquisition/emission 边界在思想上同源，dsh 给出的是运行时版本。

### 3.4 主动设计差量空间

GRC 的核心创新之一是"**主动地设计一个承载变化的、具有优良语义坐标的结构空间**"（中文全本 §B.1.3；英文主论文 F.3）。这是 Nop 与 dsh 的共同点：Nop 主动设计了 XDef/XDSL 结构坐标系；dsh 主动设计了 fiber/scope/realm/service key/event name 的运行时坐标系。

### 3.5 Loader = Generator

`counterintuitive-software-design-insights.md:252`："在可逆计算的架构下，加载器已经被赋予了生成器的全部职责，它的行为已经从被动读取转变为主动构造。" Nop 的 `ResourceComponentManager` 是结构域的 Loader=Generator；dsh 的 `ctx.effect` + fiber 生命周期是运行时域的 Loader=Generator。

---

## 四、dsh 论文核心观念 → GRC 表述映射

对应度图例：

| 标记 | 含义 |
|---|---|
| ◼ 形式化同构 | 同一概念在结构域与运行时域都有定理级形式化 |
| ◼ 概念同构 | 同一概念在两域均已表达，形式化深度不同 |
| ▣ GRC 概念 + dsh 运行时细化 | GRC 概念在运行时域的具体形态；dsh 给出定理级形式化 |
| ▢ 实现选择 | GRC 概念之下的具体实现策略，不构成理论差异 |

| dsh / Cordis 论文 | GRC 对应表述 | 对应度 |
|---|---|---|
| **主动空间设计**：dsh 主动设计 fiber/scope/realm/service key/event name 运行时坐标系 | 中文全本 §B.1.3、§B.2 判据 #2、附录 C.2；英文主论文 F.3 | ◼ 概念同构 |
| **Loader = Generator**：`ctx.effect` + fiber 生命周期把注册升级为生成器 | `counterintuitive-software-design-insights.md:252`；`reversible-computation.md:256` | ◼ 概念同构 |
| **时间可组合性**：每个 effect 携带逆，卸载时恢复 | `methodology-source.md`："任何增加的功能都应该有配对的逆向取消机制" | ◼ 概念同构（dsh 为运行时强制） |
| effect 类型 `Γ → Γ × (Γ → Γ)` | `what-does-reversible-mean.md` 的 `x-extends`/`x-diff` 双向运算；中文全本附录 D 的 P10/P11 | ◼ 概念同构（X 不同：结构树 vs 上下文） |
| **twisted composition** | `proof-v2.md` 的 `⊗`；`methodology-source.md`："可逆性可以复合" | ◼ 形式化同构（结构域证 `⊗` 结合律，运行时域证 track 同态） |
| **track 幺半群同态**（Theorem 5） | `proof-v2.md`：`⊗` 在 `D/≈` 上满足结合律；`grc-delta-associativity-formal-proof.md` 的逐坐标局部操作幺半群 | ◼ 形式化同构 |
| **recover** | `Delta = App x-diff Base`；`Base = App - Δ`（semantic rebase） | ◼ 概念同构 |
| **观测等价 ≃** | `proof-v2.md` 的 `D/≈`；GRC v2 论文的 Lax Lens `≈`；中文全本附录 D 的"逻辑世界/物理世界"区分 | ◼ 概念同构 |
| **系统边界 / withhold / compensation** | 英文主论文 §5.4（R/I Partitioning + Boundary Management + evidence objects）；中文全本 D 的 P10/P11 | ◼ 概念同构（GRC 是工程治理，dsh 是运行时语义） |
| **局部可逆复合为整体可逆** | `methodology-source.md`："如果每个部分都可逆，且部分之间的结合关系也可逆，则系统整体可逆" | ▣ GRC 概念 + dsh 运行时细化（dsh 给出 Theorem 61/63/66/73） |
| **spatial composability / 依赖声明与解析** | GRC"语义坐标系 + 稳定 key"；`xdsl-design.md`、`deep-dive-into-xdef.md` | ▣ GRC 概念 + dsh 运行时细化（dsh 给出 coeffect specification + realm 隔离） |
| **reactive coeffects**：依赖变化主动通知 | GRC"Loader = Generator"抽象下的**实现选择**：主动推送（Vite HMR 式）vs 被动失效重算（Nop Delta Loader 时间戳检查式）；见 §五 | ▢ 实现选择（GRC 未展开主动推送路线，dsh 在运行时插件图场景将其形式化） |
| **声明式配置 / reconciliation / HMR** | `reversible-computation-runtime-evolution.md` 的 `FinalModel = Loader(Delta) ⊕ Loader(Base)`；GRC v2 的 S-N-V | ◼ 概念同构 |
| **坐标**：entry id / 服务 key / 事件名 | GRC v2 §3.1 差量空间质量表；中文全本附录 C | ◼ 概念同构（entry id 是粗粒度坐标；服务/事件 key 是运行时坐标） |
| **组件/纤维实例化** | `reversible-computation-runtime-evolution.md` 的柯里化：`Component = curriedRender(schema)` | ▣ GRC 概念 + dsh 运行时细化（dsh 给出 fiber 生命周期形式化） |
| **dispatch mode 五类** | 这是 dsh 对"同一坐标上多写者顺序语义"的具体实现选择；GRC 把顺序约束放在规范化（Norm）阶段统一处理（中文全本附录 D.5）。本文标注为**本文作者的对应诠释** | ▢ 实现选择（作者诠释） |
| **Theorem 61/63/66/73** | GRC 结构域的 `⊗` 结合律证明是"复合可重排"的结构域对应；运行时域四条定理由 dsh 补齐 | ▣ GRC 概念 + dsh 运行时细化 |

**关键判断**：在概念原型层面，dsh 的每个核心观念都能在 GRC 文献中找到对应；在形式化层面，dsh 在运行时域贡献了 GRC 尚未展开的定理。这不是"GRC 未覆盖 dsh"，也不是"两套互补理论"，而是**同一范式在两个域的实例化 + 形式化分工**。

---

## 五、reactive coeffects 的定位：Loader 抽象下的实现选择

这是 v3 与 v2 最重要的表述差异。

### 5.1 问题本身属于 Loader 抽象

GRC 的"Loader = Generator"原则要求：加载器在依赖或源模型变化时，**检测变化 → 失效/重载/重建**。`reversible-computation-runtime-evolution.md` 把这个抽象实现为：

```
业务代码 → 模型路径 → Delta Loader →（依赖时间戳检查 → 失效 → Delta ⊕ Base 重新合并）→ 返回新模型
```

这是**被动检查（pull / lazy invalidation）**：请求到来时，若超过节流窗口，检查依赖时间戳，变化则重新生成。

dsh 的 reactive coeffects 是同一抽象的**主动推送（push / eager notification）**实现：provider 的变化立即 `notify` 依赖者，依赖者 `refresh`/卸载。类似 Vite：文件变化后 watcher 主动通过 WebSocket 推送 HMR 更新，而不是等浏览器下次请求再发现变化。

### 5.2 为什么 GRC 原文没有展开

因为 GRC 的核心是**差量代数与坐标空间**（Δ 怎么表达、F 怎么合并、坐标怎么设计），而"变化如何通知到需要重载的组件"是 Loader 工程实现层的次要问题。在 Nop 的结构域场景中，模型加载频率低、依赖变化不频繁，被动检查已经足够；主动推送的收益有限。因此 GRC 原文只在 `reversible-computation-runtime-evolution.md` 描述了被动路线，没有把主动路线作为理论对象展开。

### 5.3 dsh 的贡献：把主动推送路线形式化

dsh 面对的是高频动态的插件依赖图，主动推送从"可选的优化"变成了"正确性所需"（依赖失效后若继续运行会进入无效状态）。于是 dsh 把这条路线形式化：coeffect specification、变更分类（activating/deactivating/neutral）、realm 隔离、interception、以及 Theorem 63（排序）和 Theorem 66（进度）保证主动卸载/重载的正确性。

**结论**：reactive coeffects 不是 GRC 的空白，而是 GRC Loader 抽象下一次要实现选择的主动推送变体；dsh 在运行时域将其细化并给出定理。这是对 GRC 的细化，不是补缺。

---

## 六、GRC 有、而 dsh 论文没有或明显更弱的部分

1. **结构空间差量代数**：`x-extends`/`x-diff` 的代数结构；dsh 配置层只有 entry 树与粗粒度 patch。
2. **字段级坐标与负元素**：`x:override="remove"`、`x:virtual` 等；dsh 的 patch 不支持节点级增删改。
3. **差量结合律形式证明**：`proof-v2.md`、`grc-delta-associativity-formal-proof.md`；dsh 配置层合并只有"顺序即优先级"。
4. **领域结构坐标系理论**：坐标质量谱系、稳定 key、差量与全量同构；dsh 没有把坐标上升为一般理论。
5. **DSL 图册与多阶段生成**：横向多 DSL、纵向生产线；dsh 不涉及。
6. **产品线工程与 DDD 重释**：Docker、Kustomize、OpenUSD、DDD 聚合的统一解释；dsh 只解释插件系统。
7. **R/I 边界工程治理**：R-Core/I-Boundary、证据对象、熵预算、KPI；dsh 的 §6.1 是同一思想的运行时局部实例。
8. **"可逆"的分级与去歧义**：GRC v2 §2.5 分代数层/表达层/过程层/边界层；dsh 只在运行时语义层展开。

---

## 七、dsh 在运行时域的补充

dsh 的贡献不在于提出新理论，而在于**把 GRC 的运行时域从机制描述推进到定理级形式化**：

1. **运行时纤维演算（fiber calculus）**：生命周期状态、L-Begin/L-Unload、inertia、accumulator、committed view、coeffect table。
2. **可逆 effect 的运行时代数**：twisted composition 幺半群、track 幺半群同态（Theorem 5）、recover。
3. **reactive coeffects 的主动推送形式化**：coeffect specification、isolation（Definition 28）、interception、变更分类；这是 §五 所述 Loader 抽象下主动推送路线的形式化。
4. **运行时元理论**：Theorem 61（恢复精确性）、Theorem 63（排序）、Theorem 66（进度）、Theorem 73（合流）。
5. **系统边界的运行时语义**：acquisition（界内、可逆）与 emission（跨界、不可逆）的操作分类，output commit 与补偿。
6. **观测等价作为独立性来源**：coeffect 上的观测等价 ≃ 为 effect 独立性提供基础（Definition 33 → Definition 60）。

---

## 八、两组形式证明的对比

| 维度 | GRC 侧 | dsh/Cordis 侧 |
|---|---|---|
| 证明对象 | 结构空间 tree-delta 组合代数 | 运行时上下文 effect 代数与 fiber 状态机 |
| 核心结构 | `D`、`⊗`、`Apply`、`D/≈` | `𝔗Γ`、`𝜕Γ`、fiber registry |
| 核心结论 | `⊗` 在 `D/≈` 上满足结合律 | track 同态；恢复精确性；排序；进度；合流 |
| 等价关系 | 语义等价 `≈` | 观测等价 `≃` |
| 边界条件 | stable key、确定性线性化、链尾统一验证 | confinement、供应不相交、依赖无环、pairwise independence |
| 空间 | 编译期/加载期结构空间 | 运行时结构空间 |

两者证明对象不同，不构成竞争。GRC 证明了"结构差量可以安全预合并"；dsh 证明了"运行时注册 effect 可以安全跟踪、恢复与重排"。**目前尚没有一个理论同时覆盖两层；把两者统一到同一分层等价框架，是下一步理论工作的接口。**

---

## 九、dsh 是不是 GRC 的应用实例？

分三个层次回答：

- **作为观念实例：是。** dsh 的"每个 effect 配逆"是 GRC"分离到可逆"的运行时实现；scope 隔离是 GRC 坐标思想的粗粒度实现；系统边界是 GRC R/I 边界的运行时特例。
- **作为机制实例：是。** `ctx.effect` + fiber 生命周期是 GRC"Loader = Generator"在运行时域的实例化；fiber/scope/realm 是 GRC"主动设计差量空间"在运行时域的实例化。
- **作为形式化实例：部分是。** dsh 的运行时定理不是 GRC 结构域定理的推论，而是同一模式在另一域的新定理；所以更准确的说法是"**同一理论的运行时域细化与形式化补充**"。

因此，对用户原始问题"dsh 是否可以看作 GRC 的具体应用实例"，v3 的答案是：**可以，而且不止于此——它是 GRC 在运行时域的细化实例，并补上了该域目前最完整的定理级形式化。**

---

## 十、下一步工作

### 10.1 Nop 平台

1. 把 dsh 的 Theorem 61/63/66/73 引入 `nop-plugin/` 子系统，作为插件可逆卸载的正确性判据。
2. 把 `x-extends`/`x:override="remove"`/`x-diff` 引入插件配置层，补上字段级 delta 定制。

### 10.2 GRC 论文

1. 把 dsh 的运行时定理吸收为"运行时章"，标明 X=上下文、Δ=effect、F=twisted composition 的实例化选择。
2. 在主论文中明确 Loader 抽象下"主动推送 vs 被动失效重算"两种实现路线，并说明 reactive coeffects 属于前者、属次要实现问题。
3. 给出结构域与运行时域的形式化对照表，避免读者误读为两套理论。

### 10.3 dsh 论文

1. 把 §5.2 reconciliation 推到字段级差量代数（可直接引入 GRC 的 `x-extends`/`x-diff`）。
2. 明确自身是 GRC `App = F(X) ⊕ Δ` 在运行时域的实例化，Theorem 61/63/66/73 是该域的形式化。

### 10.4 统一理论

1. 统一 GRC 的 `D/≈`（结构域语义等价）与 dsh 的 `≃`（运行时观测等价）为同一分层等价框架。
2. 建立跨域接口：结构域差量如何编译为运行时 effect 序列，运行时 effect 逆如何反映射回结构域差量。

---

## 参考文献

1. Yifan Shi, Wei Zhang, Tianyi Cui. *A Programming Paradigm for Spatiotemporal Composability*. Preprint, 2026. 副本：`ai-dev/references/cordis-paper/spatiotemporal-composability.md`
2. Canonical Entropy. *Generalized Reversible Computation: A New Paradigm for Unifying Software Construction and Evolution*. `docs/theory/paper/generalized-reversible-computation-paper-en.md`
3. Canonical Entropy. *广义可逆计算：统一软件构造与演化的新范式*. `docs/theory/generalized-reversible-computation-paper.md`
4. Canonical Entropy. *广义可逆计算：以语义坐标系组织软件构造与演化*. `docs/theory/generalized-reversible-computation-paper-v2.md`
5. *附录：GRC/XLang Tree-Delta 结合律的精简形式化证明*. `docs/theory/proof-v2.md`
6. *GRC 抽象差量演算的条件化结合律证明与 Nop/XLang 语义边界*. `docs/theory/grc-delta-associativity-formal-proof.md`
7. *可逆计算的方法论来源*. `docs/theory/methodology-source.md`
8. *可逆计算理论中的可逆到底指的是什么？*. `docs/theory/what-does-reversible-mean.md`
9. *差量概念辨析（不同结构空间的差量）*. `docs/theory/explanation-of-delta.md`
10. *可逆计算如何赋能软件的运行时演化*. `docs/theory/reversible-computation-runtime-evolution.md`
11. *反直觉的软件设计洞察*. `docs/theory/counterintuitive-software-design-insights.md`
12. *可逆计算*. `docs/theory/reversible-computation.md`
13. *GRC 问题、回答与理解审计记录*. `docs/theory/grc-question-answer-audit.md`
14. *XDSL 设计*. `docs/theory/xdsl-design.md`
15. *深入 XDef：元模型定义与差量机制*. `docs/theory/deep-dive-into-xdef.md`
