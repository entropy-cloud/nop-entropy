# DeepSeek Harness 与可逆计算的思想异同分析

> Status: open
> Date: 2026-08-14
> Scope: 外部项目 `~/ai/deepseek-harness`（dsh / Cordis）与 Nop 可逆计算理论的概念对比
> Conclusion: **相似多于差异**。dsh 有**双层**：(1) **YAML 配置层**——`App = ⊕ Δᵢ` 有序差量叠加，声明式、离线、与 effect 系统完全无关，**正是可逆计算的标准处理方案**；(2) **运行时 effect 层**——论文的 revertible effects（disposer 为特异性逆元）。两者都承认"正向再逆向只达等价、非完全恢复"（伴随函子 / quiescence）。真正差异是**粒度与逆元完整性**，非有无分离：可逆计算用**稳定静态的领域坐标系**（XDef）把结构层差量做到**节点级**细粒度且含结构化逆元（`x:override`）；dsh 配置层停在**配置行级**（entry id）、缺逆元（无 remove/deep-merge），且 **plugin 是粗粒度边界**（revertible effects 以 plugin 为单元整体回退）。

## Context

- DeepSeek Harness（`dsh`）是 DeepSeek 开源的 AI agent harness，底层是 vendored 的 Cordis 插件框架，其设计纲领是 _A Programming Paradigm for Spatiotemporal Composability_。
- Nop 可逆计算是从理论物理学（微积分的差量思想）引入软件构造的理论，核心公式 `App = Delta x-extends Generator<DSL>`。
- 两者都使用 "reversible" 一词，极易混淆。本分析回答：它们到底在哪一层做"可逆"？差量是否有逆元？结构空间与运行时空间是否被分离？这对 Nop 的设计取舍有何启示。
- 判断依据：`~/ai/deepseek-harness` 的 `README.md`、`docs/architecture.md`、`docs/cordis-primer.md`、`docs/glossary.md`、`docs/defensive-patterns.md`、`packages/boot/app-boot/README.md`；Nop 的 `docs/theory/reversible-computation.md`、`docs/theory/decoding-reversible-computation.md`、`docs/theory/generic-delta-composition.md`。

---

## 一、各自的核心思想

### 1.1 dsh / Cordis：reversible effects（可逆副作用）

dsh 五大思想（`docs/cordis-primer.md` "Cordis In Five Ideas"）：

- **Plugin 是实现 Service 的对象**，挂载进当前 context。
- **Context 是服务仓库**，插件通过稳定 key（`ctx.tools`、`ctx.llm`）相互发现，而非导入具体实现。
- **通过 `inject` 声明依赖**，加载顺序由服务需求决定，而非手工编排。
- **Typed Events 通信**（`emit`/`waterfall`/`parallel`/`serial`）。
- **Registrations are reversible effects**：prompt section、tool schema、adapter、provider、listener 都通过 `ctx.effect()` / `ctx.on()` 安装，返回 disposer，reload 与 teardown 时"unwind predictably"。

要点（`docs/architecture.md`）：

- "There is no privileged core to patch"——agent loop 本身也是插件，一切皆可从配置替换。
- 运行时是 boot 时由有序层（profile → bundle → `cordis.patch.yml` → `--patch`）组合的插件树；"whatever it inserts stays patchable by the layers above it"。
- capability seam（能力接缝）= Service Definition + Provider + Consumer 三角色，一个 provider swap 改变整个产品。
- append-only session log 是真相源，"Model-visible ⟺ logged"。

### 1.2 可逆计算：reversible computation（结构层可逆）

核心公式（`docs/theory/reversible-computation.md`）：

```
Y = F(X) ⊕ Δ          # 启发式：建模必须区分已知/未知，故存在差量
App = Delta x-extends Generator<DSL>   # 落实方案
```

差量三要求：**独立存在 / 相互作用 / 具有结构**；且差量**必然含逆元**（负元素），`Y = X - F + G = X + (-F + G)`，全量 = 单位元 + 全量。

可逆性的真实含义（`docs/theory/decoding-reversible-computation.md`）：

- 不是"执行过程可逆"，而是**结构表象之间的可逆变换**（DSL 表象 ⇄ UI 表象），且这种可逆性**可复合**。
- "可逆计算"明确对标图灵机、Lambda 演算，自称软件构造的第三条路径，是一种**元理论**。

---

## 二、共同点（工程共鸣）

1. **组合性（Composability）是第一价值**
   - dsh：Cordis 论文标题即 _...for **Spatiotemporal Composability**_，"局部可逆 → 整体可逆"是承诺。
   - 可逆计算：差量相互作用 + 表象变换的复合（`报表模板 = Excel模型 + 报表配置`）是其生产力来源。
2. **层叠覆盖的增量模型**
   - dsh：profile/bundle/patch 有序叠加，上层可覆盖下层。
   - 可逆计算：`x:extends` / `x:override`，后来者居上。
3. **拒绝特权核心，强调可替换**
   - dsh：no privileged core，一切皆插件。
   - 可逆计算：Delta 赋予对"黑箱"的最终解释权和修改权，主版本无需拆解。
4. **明确区分"真相源"与衍生**
   - dsh：session log（Model-visible ⟺ logged）。
   - 可逆计算：DSL + Delta（`dump` 工具可追溯每个节点来源）。

---

## 三、用代数框架统一审视：Git / Docker / dsh / Nop 的差量代数性质

`docs/theory/explanation-of-delta.md` 提供了一个分析**任何**差量系统的代数框架——群结构四要素：封闭性、结合律、单位元、逆元。用它同时审视 Git、Docker、dsh、Nop，会发现 dsh 与可逆计算的**相似性远比表面看起来的多**，差异更多是"代数性质完善程度"的连续光谱，而非"有无"之二分。

| 代数性质 | Git | Docker | dsh（Cordis reversible effects） | Nop（XDSL 差量） |
|---|---|---|---|---|
| **空间 / 坐标系** | 行文本空间（坐标=行号，**不稳定**：增删行导致后续坐标漂移） | 文件系统空间（坐标=文件路径，**稳定**：改一文件不影响他文件坐标） | 运行时插件注册空间（坐标=context/scope **对象身份**，运行时稳定但非静态） | 领域坐标系（XDef 树形空间，**稳定且静态**，`x:id` 唯一定位） |
| **封闭性** | ✗ 合并冲突产生 `<<<<<<<` 异常标记，**突破原空间**（需上帝之手介入） | ✓ 任意两层按名覆盖可合并，永不冲突 | ✓ 注册/注销结果仍在 context 中 | ✓ 合并结果仍是合法 XDSL（扩展空间含负元素，投影后才去负） |
| **结合律** | ✗ patch 必须逐个应用到固定 base，无独立合并；顺序敏感 | ✓ 覆盖 `A⊕B=B` 自动满足，`(A⊕B)⊕C=A⊕(B⊕C)=C` | △ 注册顺序影响 emit/waterfall 执行链；disposal 是 LIFO unwind，与注册顺序相关 | ✓ x:extends 合并顺序确定（类 MRO），可独立预合并、可缓存 |
| **单位元** | 空 patch | 空镜像层 | no-op effect（空 disposer） | 空 Delta |
| **逆元** | 弱：`git apply -R` 存在但缺结合律，乏善可陈 | Whiteout 文件=逆元，但**缺特异性**（幂等 `Del*Del=Del`，与群结构冲突） | **disposer=逆元，且具特异性**（每个 effect 返回自己的 disposer，精确撤销自身副作用） | `x:override="remove"`=逆元，结构化、可精确定位 |

**三个关键洞察**：

1. **dsh 的 disposer 在"逆元特异性"上比 Docker 的 Whiteout 更接近群结构**。Docker 的删除是幂等的、无差别的占位（任何内容同路径一律删除），数学上与群冲突（`explanation-of-delta.md` §4）；而 dsh 每个 effect 有**专属** disposer，精确撤销自己的注册，这已接近群定义中"每个 a 有专属 a⁻¹"的要求。这是 dsh 与可逆计算一个**被低估的相似点**——它已演化出特异性逆元的雏形，只是作用在运行时而非结构层。

2. **四者构成连续光谱**：Git（行空间，几乎无好的代数性质）→ Docker（文件空间，满足封闭/结合律但逆元幂等）→ dsh（运行时空间，逆元特异但无结合律、空间非静态）→ Nop（领域坐标系，代数性质最完备）。**"dsh 无逆元、可逆计算有逆元"的二分法不精确**——dsh 有逆元（disposer），只是没有把它提升为结构层的一等代数运算，也缺少 Nop 那样稳定的静态领域坐标系。

3. **可逆计算的护城河不在"有没有逆元"，而在"坐标系是否稳定 + 逆元是否结构化"**。`explanation-of-delta.md` 反复强调：差量系统的质量取决于**结构空间的坐标系稳定性**（行号 vs 文件路径 vs 对象身份 vs 领域坐标）。dsh 用运行时对象身份做坐标（活但非静态），Nop 用 XDef 领域坐标做坐标系（稳定且静态），这才是决定性差距。

---

## 四、等价性问题：正向再逆向不一定恢复原样

这是可逆计算理论中常被忽略、却与 dsh **高度相关**的深刻问题。`docs/theory/faq-about-theory-of-nop.md` 第2题与 `docs/theory/explanation-of-delta.md` 的伴随函子章节给出了明确论述：

- **可逆 ≠ 完全恢复**。可逆计算中的"可逆"指**表象之间的可逆变换**，对应范畴论的**伴随函子**（Adjoint Functor）：左伴随 `L` 与右伴随 `R` 通过自然同构"逆转"，而非严格互为逆运算。`Excel ≃ Export(Import(Excel))`——正向变换再逆向，得到的是**等价**对象而非原样（样式变化、空字符串变 null、缺省值字段丢失等）。
- **实际变换多为近似等价**：`A ~ F(B)`，`G(A) ~ B`。要使近似等价成为严格等式，可**补充差量**：`A + dA = F(B + dB)`。Nop 的 `(data, ext_data)` 是为此提供的可选手段（`faq-about-theory-of-nop.md` 第2题）——**并非强制保留所有信息**：报表例子中样式等业务无关信息仍会丢失，只保证整体结构一致。
- **幂等性与群冲突**：`explanation-of-delta.md` 证明，删除的幂等性（`Delete*Delete=Delete`）与群结构矛盾，故实际差量空间"并不是真正的群结构，只是某种支持可逆运算的结构"。

**dsh 的 disposer 同样面临等价性问题，而且更严峻**：

- 理论上 disposer 精确撤销注册，但运行时外部副作用（已发网络请求、已写文件、已 spawn 的子进程）不可逆。这正是 `~/ai/deepseek-harness/docs/defensive-patterns.md` 的核心戒律：**"Dispose must reach quiescence, not just request it"**——teardown 追求的是"**静默（等价）**"而非"**完全恢复（相等）**"；kill → await done、关闭监听器后再 kill，都只为逼近等价，不奢求精确复原。
- 所以 dsh 的 reversible effects 同样是"近似可逆"：`register ∘ dispose ≈ identity`（等价，非相等）。这与可逆计算的伴随函子等价性是**同构的问题**——都是"正向再逆向，结果在某种意义上等价但不完全相同"。

**共鸣升级**：两者都在处理"可逆性的等价性边界"，都按各自判据丢弃无关信息以逼近等价——可逆计算丢弃业务无关信息（保留结构，如 report 丢样式），dsh 用 quiescence-based disposal 逼近静默等价（丢弃不可恢复的外部副作用）。`(data, ext_data)` 只是可选的逼近手段，不是强制保留。这是二者深层、被表面差异掩盖的共鸣——**没有谁能做到严格的、信息守恒的完全可逆，都是在某个等价关系下逼近可逆**。

---

## 五、本质差异

### 5.1 核心分野：可逆发生在哪一层

| | dsh | 可逆计算 |
|---|---|---|
| "可逆"的对象 | **配置层 = 结构差量(⊕Δᵢ) + 运行时层 = 副作用(disposer)** | **结构层差量（节点级）** |
| 可逆的维度 | **时间维度**：加载 ⇄ 卸载、注册 ⇄ 注销 | **空间/结构维度**：合并 ⇄ 分解、表象 ⇄ 表象 |
| 核心机制 | 配置层:patch 有序叠加(声明式离线); 运行时层:`ctx.effect()` 返回 disposer | 差量代数（含逆元）的声明式合并 |
| 实现风格 | 配置层**声明式**(与可逆计算同构); 运行时层**过程式**(记住如何撤销) | **声明式/代数**：声明最终状态，系统合并，代数吸收保证健壮性 |
| 理论野心 | 一种编程范式（插件可组合性） | 自称继图灵/Lambda 后的第三条软件构造路径 |

一句话：dsh 的**配置层**（YAML 有序叠加）已与可逆计算同构（`App = ⊕ Δᵢ`），只是粒度停在配置行级、逆元不完整；其**运行时层**额外用 reversible effects（disposer）处理副作用。可逆计算则把结构层差量做到领域坐标**节点级**细粒度。论文 §3 的 revertible effects 只形式化了 dsh 的**运行时层**，未覆盖其配置层。

### 5.2 差量逆元：特异性的层次差异（非有无之分）

- **可逆计算：必须有逆元**。`Δ` 是"有"与"没有"的混合体，存在 `-F`，可做代数减法 `Y = X - F + G`（`docs/theory/reversible-computation.md`）。`x:override="remove"` 是一等公民。
- **dsh：有逆元（disposer），但分两层，且 patch 层缺逆元**。需区分 dsh 的两个层次：(1) **运行时 effect 层**——disposer 即特异性逆元（见第三节洞察1），数学性质不差；(2) **配置 patch 层**——patch 语义仅"替换整行 config 或插入新行"（`packages/boot/app-boot/README.md`：id-targeted patch replaces the whole config, does not deep-merge / insert adds entries），无结构化减法，"删除"只能靠替换整行或卸载插件。所以 dsh 的"缺逆元"仅指**配置/结构层**，运行时层并不缺。

真正的区分点是：可逆计算把差量提升为**结构层第一性的、含逆元的代数对象**，且坐标系稳定静态、粒度达节点级；dsh 的配置层（patch）已是声明式差量叠加（可逆计算式），但逆元不完整（无 remove/deep-merge）、粒度停配置行级，其特异逆元（disposer）只活在运行时层。

### 5.3 结构空间 vs 运行时空间：是否被分离（本分析的核心论点）

这是用户提出的、也是可逆计算最深刻的设计选择。`docs/theory/generic-delta-composition.md` 给出了明确论证：

- 可逆计算"在标准化的无冗余的**结构层**进行操作，而不是在按照类型出现分化的对象层上进行操作"，差量合并统一定义在 **XDSL 层**（`docs/theory/generic-delta-composition.md`）。
- 它类比关系模型：关系数据库之所以标准化，是放弃天然关联、退到原子化无冗余数据；可逆计算同样退到标准化结构（XDSL/XNode），在结构层定义差量运算。
- 它类比微服务无状态设计：**逻辑处理结构与运行时状态空间解耦**。"DSL 模型的动态更新同样可以独立于状态空间中的数据迁移"（`docs/theory/generic-delta-composition.md`）。
- 模型处理被分解为正交的阶段：`modelPath => XDSL => DslModel => arguments => scope => Result`。结构空间（XDSL）与运行时空间（arguments/scope/Result）是可分离的两段。

**对比 dsh（重大修正）**：dsh **确实分离了**配置（结构）层与运行时层——它有**两个相互独立的系统**：

1. **YAML 配置层（声明式，正是可逆计算的标准方案）**：profile → bundle → `cordis.patch.yml` → `--patch` 的有序差量叠加，即 `ConfigTree = ⊕ Δᵢ`（`architecture.md`：layers apply to an empty entry list in order）。这一层是**离线、声明式**的——`composeEntries` / `applyEntryPatches` / `renderConfigDump` 可在**不运行任何 effect** 的情况下纯离线合并配置（`app-boot/README.md`：renderConfigDump composes offline ... so the result equals what `boot()` mounts）。**这与可逆计算 `App = Delta x-extends Generator<DSL>` 同构**：DSL=cordis.yml 配置行，Delta=bundle/patch，x-extends=applyEntryPatches。
2. **运行时 effect 层（论文的 revertible effects）**：`boot()` 在配置合并**之后**才 mount entry tree、激活 fiber、注册 effect/disposer。这一层才是论文 §3 形式化的对象。

**两个层面完全独立**：配置合并不产生 effect，effect 系统不参与配置合并。所以旧判断"dsh 把可逆绑定在运行时、结构未分离"是**错误的**——dsh 的 YAML 配置层本身就是一个独立的、声明式的结构层差量系统，**正是可逆计算的标准处理方案**。

**真正的差异是粒度与逆元完整性，不是有无分离**：

| | dsh YAML 配置层 | 可逆计算结构层 |
|---|---|---|
| 坐标系 | **plugin entry id**（配置行级，粗） | **XDef 领域坐标**（树节点级，细，`x:id` 定位到任意字段） |
| 逆元 | 弱：仅替换整行/插入，无 remove、无 deep-merge | 强：`x:override=remove/merge`，结构化逆元 |
| 定位粒度 | 一个 plugin 的整段 config | DSL 内任意节点（一个属性、一个字段） |

**粒度谱系**（从粗到细，三者都各有其可逆机制）：

```
plugin 整体      ← Cordis revertible effects 的边界（加载注册全部effect，卸载整体回退）
   ↓ 更细
配置行 / entry   ← dsh YAML patch（按 entry id 替换/插入，声明式离线叠加）
   ↓ 更细
领域坐标系节点   ← 可逆计算 XDef（定位到 DSL 内任意节点，merge/remove，细粒度 delta）
```

- **plugin 是粗粒度边界**：Cordis 的 revertible effects 以 component/plugin 为单元——加载时注册全部 effect，卸载时按 disposer 整体回退。标准模式下无法在 plugin 内部单独撤销某个 effect 而保留其余（scope = per-agent/per-plugin）。
- **可逆计算是节点级细粒度**：通过 XDef 在 DSL **文件内部**建立领域坐标系，精确定位到任意节点，可独立增删改每个字段——这正是"引入领域结构坐标系"带来的**非常细粒度的 delta 计算**，远细于配置行级。

**结论**：dsh 的 YAML 配置层已是可逆计算式的结构层差量（声明式、离线、有序叠加、与 effect 无关），只是粒度停在配置行级、逆元不完整；可逆计算把同样的"层叠差量"思路推进到领域坐标系节点级、并补全结构化逆元。两者在"配置层 = 可逆计算标准方案"上**高度一致**，差距是粒度与逆元完整性。

修正后的图：

```
dsh（双层：配置层是可逆计算式，运行时层是论文式）:
   配置层(声明式/离线):  ∅ ─⊕bundle₁─⊕bundle₂─⊕patches─▸ entryTree
      │ boot() mount（配置层不产生 effect）        [这正是 App = ⊕ Δᵢ]
   运行时层(论文式):      mount entries → 激活 fiber → effect/disposer（revertible effects）

可逆计算（结构层细粒度，两层分离）:
   结构空间:  XDSL_tree ─(Delta 合并/分解, 含逆元, 节点级)─▸ XDSL_tree'
      │ 投影(Generator + x-extends)                   │ 可独立重算
   运行时空间: DslModel → args → scope → Result         (运行时状态可独立于结构更新)
```

**可逆计算的落实：loader 抽象（被动模式）**：上述分离被封装为一个 **loader 抽象**——这是可逆计算的**被动模式**处理方案（对应 `generic-delta-composition.md` 的 `ResourceComponentManager.loadComponentModel`）：

- **按张量坐标加载**：应用的各领域维度（工作流、报表、权限…）构成特征向量/张量坐标（可逆计算理论中 `App = (工作流, 报表, 权限, ...)`，见 `reversible-computation.md` §二）。Loader 接收坐标（modelPath），按 MRO 顺序加载并合并各维度的 delta，得到**静态 DslModel**。
- **两段严格分离**：`modelPath => XDSL => DslModel`（loader，**被动**——只按坐标组装结构，不驱动运行时）**‖** `DslModel => arguments => scope => Result`（运行时，**外部参数独立施加**）。Loader 输出纯静态结构产物，运行时参数在 model **外部**施加。
- **依赖追踪 + 自动缓存失效（被动模式的第二层含义）**：Loader 内置基于依赖的缓存——合并后的 DslModel 被缓存，Loader 自动跟踪每个 Delta 文件及其依赖关系；一旦任何依赖的 DSL/Delta 被修改，相关缓存**自动失效并重算静态 model**（`decoding-reversible-computation.md`：智能缓存机制；`generic-delta-composition.md`：一旦 DSL 文件被修改，DslModel 就自动重新解析）。不需外部主动通知，Loader 通过依赖图自行感知变化。关键是：失效只重算**静态结构**，运行时参数（arguments/scope）独立、不受影响。
- **对比 dsh 的 loader**：dsh 配置层也是一个 loader（`composeEntries`/`renderConfigDump`），也有类似的缓存失效机制——`watchUserPatches` 监听 patch 文件变化、重新 compose 配置（`app-boot/README.md`）。但**本质差异在失效的代价**：dsh 重组后须**重新激活整棵插件树**（mount entries → 重激活 fiber → 处理活资源 teardown，`defensive-patterns.md`：dispose must reach quiescence），因为加载与激活耦合；可逆计算的缓存失效只**重算静态 DslModel**（纯结构），运行时是独立第二阶段、无需 teardown。这正是"被动模式"的完整含义：loader 被动加载 + 被动响应变化（依赖追踪自动失效），全程不主动驱动运行时。

### 5.4 其它差异

| 维度 | dsh | 可逆计算 |
|---|---|---|
| 思想来源 | Cordis 时空可组合性（工程化插件框架） | 理论物理学（差量的微分/积分） |
| 应用域 | AI agent 运行时编排（loop/tools/session/LLM） | 全栈应用/低代码平台的工业化生产与系统级复用 |
| "减法"能力 | 弱（替换整行 / 卸载） | 强（`-F`，不拆解主版本即消除功能） |
| 可逆复合方式 | 注册-注销的可逆对，随 scope 自动 unwind | 表象变换 F/G 的复合，局部可逆自动复合为整体可逆 |
| 软件复用观 | 插件组合（仍受"相同方可复用"约束） | "相关即可复用"，系统级粗粒度复用 |

---

## 六、对 Nop 的启示与可借鉴点

1. **dsh 的 reversible effects 是可逆计算思想在"运行时插件生命周期"子问题上的一个特例**，但未上升到差量代数高度。这反向印证了可逆计算的普适性：即使没有逆元概念，工程实践也会自然演化出"注册-注销"的可逆对。
2. **可借鉴：可逆副作用的精细化**。可逆计算在**结构层**把差量逆元做到极致（XLang/Delta/x-extends），但在**运行时 effect 的生命周期管理**上，并不像 dsh/Cordis 那样精细（每个注册都带 disposer、随 scope 自动 unwind、teardown 必须达到静默——`docs/defensive-patterns.md`）。Nop 的 IoC 更偏传统 bean 管理，运行时可逆副作用的系统化程度低于 Cordis。这是 Nop 可向 dsh 学习的方向。
3. **dsh 的 YAML 层叠 patch 已是可逆计算式结构差量**（`App = ⊕ Δᵢ`，声明式离线叠加，与 Nop Delta 同构）。差距仅在粒度与逆元：dsh patch 按 entry id 替换/插入、不 deep-merge（`app-boot/README.md`），Nop 用 `x:override="merge"` 提供节点级深度合并与 remove。即 dsh 配置层停在"配置行级"，Nop 推进到"领域坐标节点级"。
4. **"结构/运行时分离"是 Nop 的护城河**。dsh 因为没分离这两个空间，其 HMR 重组必须处理大量活资源一致性；而 Nop 因结构层独立，"DSL 模型的动态更新可独立于状态空间的数据迁移"——这是低代码/平台级产品长期演化的关键优势，dsh 这类运行时框架不具备。

---

## Conclusion

- **相似多于差异**。dsh 与可逆计算共享组合性、非侵入扩展、层叠覆盖、特异性逆元、等价性逼近五大共鸣。**dsh 的 YAML 配置层本身就是可逆计算的标准方案**（`App = ⊕ Δᵢ`，声明式、离线、与 effect 系统完全无关），其 disposer 已是比 Docker Whiteout 更接近群逆元的特异性逆元；两者都承认"正向再逆向只达等价、非完全恢复"（伴随函子 / quiescence）。
- 真正的差异是**粒度与逆元完整性**，非有无分离：(1) dsh 有**双层**——配置层（结构差量，可逆计算式）与运行时层（revertible effects），两层独立；可逆计算把结构层差量做到**领域坐标节点级**细粒度，dsh 配置层停在**配置行级**。(2) 逆元：dsh 配置层缺结构化逆元（无 remove/deep-merge），可逆计算有（`x:override`）。(3) 坐标系：dsh 配置层 = entry id，可逆计算 = XDef 领域坐标（稳定静态）。(4) **plugin 是粗粒度边界**——revertible effects 以 plugin 为单元整体回退，无法节点级细粒度；可逆计算的领域坐标系让 delta 可定位到 DSL 内任意节点。
- 可借鉴：Nop 可吸收 dsh 在运行时可逆副作用（disposer/scope/teardown-to-quiescence）上的精细化经验，作为结构层差量之外的运行时层补充——补齐"运行时逆元特异性"，使两层都可达可逆。
- 这是一份单方面调研，结论可被推翻；暂无后续 plan/design 接手。

## Open Questions

- [ ] Cordis 的 "reversible effects" 是否可形式化为某种代数结构（如 effect monoid）？若是，能否在 Nop 的运行时层引入类似的代数化可逆副作用？
- [ ] dsh 配置层（patch）已是可逆计算式结构差量，若补全 deep-merge 与 remove 语义，是否就等价于可逆计算的节点级 Delta？其瓶颈是坐标系（entry id 过粗）还是合并算法？
- [ ] Nop 的 IoC（beans）层是否值得引入"每个 bean 注册带 disposer、随 scope unwind"的机制，以补齐运行时可逆短板？

## References

- 外部（dsh）：
  - `~/ai/deepseek-harness/README.md`
  - `~/ai/deepseek-harness/docs/architecture.md`
  - `~/ai/deepseek-harness/docs/cordis-primer.md`（"Cordis In Five Ideas"、reversible effects、waterfall semantics）
  - `~/ai/deepseek-harness/docs/glossary.md`（capability-seam / agent-scope / turn / Ralph）
  - `~/ai/deepseek-harness/docs/defensive-patterns.md`（dispose must reach quiescence）
  - `~/ai/deepseek-harness/packages/boot/app-boot/README.md`（profile/bundle/patch 层叠、id-targeted patch 不 deep-merge、watchUserPatches HMR）
  - `~/ai/deepseek-harness/AGENTS.md`（registrations are effects、model-visible ⟺ logged）
- Nop（本仓库）：
  - `docs/theory/reversible-computation.md`（核心公式、差量三要求、逆元、Docker/React 解构）
  - `docs/theory/decoding-reversible-computation.md`（表象变换、复合性、可逆性的真实含义）
  - `docs/theory/generic-delta-composition.md`（**结构层 vs 对象层、关系模型类比、微服务无状态类比、结构/运行时空间分离**）
  - `docs/theory/explanation-of-delta.md`（**群结构四要素分析框架、Git/Docker 差量代数对比、稳定坐标系、Whiteout 幂等与群冲突、伴随函子、扩展空间含负元素**）
  - `docs/theory/faq-about-theory-of-nop.md`（**同态/函子映射、近似等价与补差量成等式、`(data,ext_data)` 设计、坐标系稳定性**）
  - `docs/theory/reversible-computation-theory-overview.md`（理论概览、FOP/DOP 脉络、特性向量/多阶段分解、波动 vs 粒子世界观）
