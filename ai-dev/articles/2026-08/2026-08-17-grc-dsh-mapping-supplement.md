# 补充说明：GRC 与 dsh 映射的正确理解

> 日期：2026-08-17
> 性质：**补充材料，不替代既有文章**。本文用于澄清此前 v2/v3 及映射文档中一处关键误读，并给出修正后的完整对照。
> 关联文件：
> - `2026-08-17-dsh-architecture-from-reversible-computation.v3.md`
> - `2026-08-17-grc-dsh-theory-mapping.v3.md`
> - GRC 中文全本：`docs/theory/generalized-reversible-computation-paper.md`
> - GRC 英文主论文：`docs/theory/paper/generalized-reversible-computation-paper-en.md`
> - Cordis/dsh 论文：`ai-dev/references/cordis-paper/spatiotemporal-composability.md`

## 一、这份补充在纠正什么

此前的文章在把 GRC 公式映射到 dsh 时，把 **F 与 ⊕ 混为一谈**，导致论证骨架偏了。本文只纠正这一个关键点及其连锁结论，不改动既有文章。

## 二、核心修正：F 是 Generator，⊕ 才是合并算子

GRC 的公式是：

```
App = F(X) ⊕ Δ
```

其中：

- **F 是生成器（Generator）**：从 DSL / 配置生成"主干结构"；
- **⊕ 是差量合并算子**：把 Δ 叠加到主干上，x-extends 是其在 Nop 的实现；
- **Δ 是结构化差量**，逆由 x-diff / 前像补偿给出。

此前把 dsh 的 twisted composition 写成"F"是错的。**twisted composition 对应的是 ⊕**。修正后的完整映射如下：

| GRC 公式元素 | GRC 通用含义 | Nop（结构域） | dsh（运行时域） |
|---|---|---|---|
| X | 域对象 | 结构树 / DSL 模型 | 运行时上下文（cordis.yml 配置 + fiber 状态 + coeffect table） |
| F | Generator | XDSL 生成器 / `ResourceComponentManager` | 插件 loader / fiber 实例化机制（`ctx.use` / `ctx.effect` 的注册执行） |
| Δ | 结构化差量 | 树差量（x-extends 差量模型） | 单个带逆 effect（`ctx.provide` / `ctx.on` / 资源注册） |
| ⊕ | 差量合并算子 | `x-extends` | twisted composition（effect 的复合与累积） |
| 逆 | Δ 的逆 | `x-diff` / 前像补偿 | `dispose` / accumulator（LIFO 执行逆） |

所以，dsh 做的是：**把 GRC 的 ⊕ 在运行时上下文上形式化为 twisted composition，并证明 track 是幺半群同态**。这是域细化，不是发明另一个 F。

## 三、dsh 是 GRC 的运行时域细化，不是另一套理论

总理论给出的是模式：`App = F(X) ⊕ Δ`，要求 Δ 有配对的逆、⊕ 满足可复合性。每个域都要补充自己域内的具体定理：

- **结构域**：Nop 补的是 `⊗` 结合律（`proof-v2.md`、`grc-delta-associativity-formal-proof.md`）；
- **运行时域**：dsh 补的是 effect 跟踪代数（Theorem 5）与 fiber 状态机元理论（Theorem 61/63/66/73）。

"结构域证明推不出运行时定理"是正常的——**推不出来正是"细化"的含义**。不能因此说 dsh 是另一套理论。

## 四、reactive coeffects 的正确定位

在 dsh 论文内部，reactive coeffects 是 spatial composability 的支柱，这没有问题。但放在 GRC 框架下：

- **spatial composability 在 GRC 中的对应物是"稳定坐标 + 依赖声明"**：stable key、DSL 引用、delta 层依赖，这些 GRC 本来就有。
- dsh 的 reactive coeffects 是把"依赖变化如何传导"做成了运行时机制。在 Loader 抽象下，它只是**主动推送路线**的形式化：
  - **主动推送**：dsh 的 `notify` / `refresh`，类似 Vite 检测到文件变化后主动推送 HMR 更新；
  - **被动失效重算**：Nop Delta Loader 的时间戳节流检查 + 整体重新合并。

两条路线同属 GRC 的 "Loader = Generator" 抽象（检测变化 → 失效/重载/重建）。因此 reactive coeffects 在 dsh 内部重要，但在 GRC 坐标系里是**次要实现层问题**，不是 GRC 的空白。

## 五、条件化可逆性：GRC 与 dsh 同核

此前把"GRC 的逆是条件化的（P10/P11）"当成 GRC 与 dsh 的差异，这是误用。实际上：

- GRC：Δ 的逆是条件化局部逆——记录前像则可剥离（P10），删除且无前像则不可逆（P11）；
- dsh：逆的正确性是作者义务、运行时恢复是观测等价 ≃ 而非字面还原。

**两者是同一立场**：可逆是条件化的、分层的、有边界的。这恰恰证明两者同核，而不是"GRC 不完善、dsh 独立"的证据。

## 六、修正后的结论

1. **GRC 是通用构造理论**：`App = F(X) ⊕ Δ`，其中 F 是 Generator、⊕ 是差量合并算子。
2. **dsh 是 GRC 在运行时域的细化**：X=运行时上下文、F=插件 loader、Δ=带逆 effect、⊕=twisted composition、逆=dispose/accumulator。
3. **dsh 补充了运行时域的定理级形式化**：Theorem 5（track 同态）与 Theorem 61/63/66/73（恢复精确性/排序/进度/合流）。
4. **reactive coeffects 是 Loader 抽象下主动推送路线的形式化**，属次要实现选择；GRC 未展开是因为其核心在差量代数与坐标空间。
5. **GRC 与 dsh 不是两套理论**，而是总理论与运行时域细化的关系；结构域与运行时域的形式化目前互补，未来可统一到同一分层等价框架。

## 七、补充：Delta 特殊性质的必然性与作用（与具体 Δ 运算实现无关）

GRC 文献反复强调一组关于 Delta 的**设计必然性**。它们不是某一种 Δ 实现（如 x-extends、LWW 覆盖、twisted composition）带来的偶然结果，而是从"差量作为第一性概念"出发必然推出的要求。任何域的 Δ 机制，包括 dsh 的带逆 effect，都应当用这组必然性去衡量。

### 7.1 单位元的必然性：构造即演化

中文全本 `generalized-reversible-computation-paper.md` §3.2 用恒等式 `A = ∅ ⊕ A` 表达了这一点：

- ∅ 是**零模型 / 空基线**，是差量代数中的单位元；
- 右侧的 A 是**创世差量（Genesis Delta）**，包含从"无"到"有"创建整个应用所需的全部信息。

由此推出：

```
构造：App_V₁ = ∅ ⊕ Δ_Genesis
演化：App_V₂ = App_V₁ ⊕ Δ_Incremental
```

两者是同一操作——**应用差量**。构造不过是在零基线上的演化，演化不过是在非零基线上的局部构造。这解释了为什么差量代数里单位元不是可有可无的空操作：**有了单位元，全量才能被重新解释为差量的特例，构造与演化才统一为一种操作**。`explanation-of-delta.md` 进一步指出：空操作是自然存在的单位元，因为"空操作和任何其他操作结合在一起都等价于这个操作本身"。

### 7.2 全量是差量的特例 → 差量与全量同构

从 `A = ∅ ⊕ A` 直接推出：**全量是差量的一个特例**。其关键推论是（`explanation-of-delta.md`、`delta-oriented-programming.md`）：

> 原则上全量可以采用和差量一模一样的形式，没有必要为了表达差量单独设计一个不同的差量形式。

- 反例：JSON Patch 的差量格式与 JSON 全量格式不同，是"特制差量形式"；这正是 GRC 反对的设计。
- 正例：Nop 的差量模型与全量模型同构，因此**差量的差量仍然是一个普通差量**，可以继续参与合并、继续被定制。
- 推论：base 与 patch 是对偶关系，差量具有独立存在的价值，不依附于 base 才能被理解。

### 7.3 差量应用需要结构坐标系

差量要"施加到某一点"，就必须有稳定的坐标。GRC 把坐标系选择视为差量空间设计的第一问题：

- Git 的行文本空间：坐标是行号，格式化/插入行都会导致大量坐标漂移，噪声高、组合性差（中文全本附录 C.1）；
- Docker 的文件系统空间：文件路径是稳定坐标，局部变化不影响其他文件的坐标（`explanation-of-delta.md`）；
- GRC/Nop 的 DSL 语义空间：由 XDef 定义的领域坐标（`entity`/`column`/`name`），稳定且带业务语义，差量表达最稀疏、最可组合（中文全本附录 C.2）。

中文全本附录 C 的结论是：GRC 的核心挑战不是去"发明" `Y = F(X) ⊕ Δ` 这个关系，而是去**发现并设计一个最优的表达空间**，使 F 和 Δ 具有最大的工程价值和最强的代数性质。所以**坐标系是差量应用的前提，而不是差量实现的一个可选细节**。

### 7.4 这组必然性与具体 Δ 运算实现无关

上述三点（单位元、差量/全量同构、结构坐标系）回答的是"Δ 应该长什么样、作用在哪个空间"，而不是"⊕ 具体怎么算"。具体运算——LWW 覆盖、x-extends、JSON Merge Patch、twisted composition——都只是在这组必然性约束下的实现选择：

- LWW 覆盖满足结合律，是 GRC 核心覆盖语义的实现；
- x-extends 是 Nop 在树结构空间上的实现；
- twisted composition 是 dsh 在运行时上下文空间上的实现。

三者共享同一组前提（单位元存在、差量与全量同型、稳定坐标），只是 X 和 ⊕ 不同。**这正说明 GRC 是总理论：它规定的是 Δ 的性质必然性，而不是某一具体 Δ 运算。**

### 7.5 这组必然性在 dsh 运行时域的体现

用这组必然性看 dsh，可以看得很清楚：

- **单位元**：`idΓ`（恒等上下文变换）是 effect 空间的单位元；fiber 未注册任何 effect 时的 accumulator 就是 `idΓ`；
- **差量与全量同型**：effect 是 `Γ → Γ × (Γ → Γ)`，上下文变换与带逆变换同属一个空间；注册动作（Δ）与上下文状态（全量）共享 Γ 空间，不需要另一套类型；
- **结构坐标系**：dsh 主动设计的 service key（单写多读）、event name（多写 + dispatch mode）、realm（隔离域）就是运行时域的稳定坐标。

因此，dsh 的 effect 机制不是"另一种理论"，而是 GRC 关于 Delta 的这组设计必然性在运行时上下文空间上的实例化。

## 八、补充：F(X)+Δ 作为一个整体的必然性

上一节讲的是 Δ 自身必须具备的性质；这一节讲更根本的问题——**为什么软件系统的描述必然是 `F(X) + Δ` 这个两部分的整体，而不是 F(X) 或 Δ 单独成立。** 这组必然性同样与具体实现无关。

### 8.1 狄拉克分解：自由部分 + 微扰部分

中文全本 §4.1 用物理学的三种绘景给软件范式定位：

- 图灵机范式 ≈ 薛定谔绘景：程序不变，状态演化；
- λ 演算范式 ≈ 海森堡绘景：状态不变，算子演化；
- **GRC ≈ 狄拉克（相互作用）绘景：把系统分解为"可精确求解的自由部分"和"作为微扰处理的相互作用部分"。**

映射到软件构造：**自由部分是 `F(X)`（从 DSL 确定性地生成的理想主干），微扰部分是 `Δ`（对理想主干的结构化偏离）。** 这个分解不是偏好，而是"可模型化的复杂系统"唯一的最小分解——可生成的规律放进 F(X)，不可生成的变化放进 Δ。

### 8.2 最小信息表达原则：三个推论合起来就是 F(X)+Δ

中文全本 §4.2 从"表达且仅表达需要表达的信息"推出三个构造策略：

1. **DSL 的必要性** → 给出了 `X`：用领域概念表达领域逻辑，剥离执行细节；
2. **语义唯一性与可逆变换** → 给出了 `F` 与"可往返"：核心语义到多重表象应当是条件化可逆的；
3. **演化的最小信息单元是 Δ** → 给出了 `Δ`：描述变化的最经济方式不是重述整体，而是只描述差异。

把三条合起来，系统的最经济描述只能是：**一个可生成的基干 `F(X)`，加上一个稀疏的差量 `Δ`。** 这就是 `App = F(X) ⊕ Δ` 作为整体的第一性原理来源。

### 8.3 为什么 F(X) 或 Δ 单独都不成立

- **只有 F(X)、没有 Δ**：就是传统 MDA——生成强大，但生成结果不可定制；要表达偏离只能改生成器或复制全量，信息量爆炸，回到"标准与定制"的死结。
- **只有 Δ、没有 F(X)**：就是 Git/DOP 式差量——差量是一等公民了，但没有稳定坐标和理想主干，差量依附于具体 base，无法独立组合，代数性质弱。
- **F(X)+Δ 合体**：主干提供稳定坐标和默认结构，差量提供定制与演化；并且 `A = ∅ ⊕ A` 使二者**对偶**——base 可以看作 patch 的 patch，F 和 Δ 的角色可以互换。因此 §3.1 说这是从 OOP 的 `Map = Map extends Map` 升维到 GRC 的 `Tree = Tree x-extends Tree`。

### 8.4 F 与 Δ 是互相定义的

F 给 Δ 提供可作用的坐标系；Δ 给 F 提供可变性与演化。离开 F 的 Δ 退化为脆弱 diff；离开 Δ 的 F 退化为刚性生成器。**这个整体是一个分形不变量**：每一层产物 `App` 又可以在下一层被当作 `F(X)`，再叠加新的 Δ。

### 8.5 这个整体在 Nop 与 dsh 中的实例化

- **Nop（结构域）**：`FinalModel = Loader(Delta ⊕ Base)`。F(X) = XDSL 生成器 + `ResourceComponentManager`；Δ = 树差量；⊕ = `x-extends`。
- **dsh（运行时域）**：`RuntimeStructure = Loader(config) ⊕ effects`。F(X) = 插件 loader / fiber 实例化；Δ = 带逆 effect；⊕ = twisted composition。

两者都是 `F(X)+Δ` 整体在不同域的实现；差别只在于 X、F、Δ、⊕ 各自落在哪个空间。

### 8.6 必然性的边界

这组必然性是**条件化必然性**：对"可模型化、需长期演化、需管理复杂性"的软件系统成立。对于一次性脚本或拒绝建模的场景，GRC 自己承认边界在于"你愿意在多大程度上把业务世界模型化"。因此它是 GRC 所针对问题类的构造必然性，不是对一切软件的绝对律令。


## 附：关键文献位置

- GRC 公式与 F/⊕ 定义：中文全本 `generalized-reversible-computation-paper.md` 核心术语表、§3.1、附录 D；英文主论文 `paper/generalized-reversible-computation-paper-en.md` 术语表与 §3.1。
- Δ 条件化逆：中文全本附录 D.2.2 的 P10/P11。
- Loader = Generator：`docs/theory/counterintuitive-software-design-insights.md:252`。
- dsh 的 effect 代数与 fiber 演算：Cordis 论文 §3.1、§4、Theorem 5/61/63/66/73。
- dsh 的 reactive coeffects：Cordis 论文 §3.2、§5.1.2。
- `A = ∅ ⊕ A`、创世差量、构造即演化：中文全本 §3.2。
- 差量与全量同构、JSON Patch 反例、单位元重要性：`explanation-of-delta.md`。
- 全量是差量的特例、差量与全量可互相转化：`what-does-reversible-mean.md`、`delta-oriented-programming.md`。
- 差量空间选择、稳定坐标、语义模型空间：中文全本附录 C（C.1/C.2）。
- 潜在模型中"不存在模型和差量的类型区分"：中文全本附录 D.1。
