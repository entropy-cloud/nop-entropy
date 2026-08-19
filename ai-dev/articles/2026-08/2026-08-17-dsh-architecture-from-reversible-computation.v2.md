# 从可逆计算看 DeepSeek Harness 的架构设计（v2）

> 日期：2026-08-17
> 版本：v2（依据 `2026-08-17-dsh-architecture-from-reversible-computation.suggestions.md` 修订；原 v1 稿未改动）
> 关联论文：[A Programming Paradigm for Spatiotemporal Composability](../../references/cordis-paper/README.md)（Cordis 设计论文，dsh 的架构基础）
> 理论背景：`docs/theory/` 下可逆计算系列文章

## 姊妹篇导览

本文与 `2026-08-17-grc-dsh-theory-mapping.md` 构成一对姊妹篇，分工如下：

- **本文（v2，工程视角）**：基于 dsh 源码核实，给出"dsh 可逆性的范围、粒度、坐标、机制"的具体工程判断；与 Nop 平台做对照，论证"结构空间可逆 + 运行时结构空间可逆 + 运行时具体状态不可逆"的互补图景。
- **姊妹篇 grc-mapping（理论视角）**：把本文的工程结论上升到 GRC（广义可逆计算）形式化层面，逐项映射 Cordis 论文核心概念到 GRC 文献中的对应表述，并论证"GRC 与 dsh 是互补关系而非'总理论—应用'单向关系"。

**两文的对应关系**：

| 本文（v2） | 姊妹篇 grc-mapping |
|---|---|
| §5.1/§5.2 配置层缺字段级差量代数的工程证据 | §4 第 1 条（GRC 有、dsh 没有）的具体例证 |
| §5.5/§5.6 时间性对偶的工程描述 | §6 形式化对照（`D/≈` vs `≃`、⊗ vs 𝔗Γ） |
| §3 effect 代数的运行实例 | §5（dsh 有、GRC 缺）的运行时定理展开 |
| §5.8 Nop 注册机制前提到结构空间的实现 | §4 第 3 条（DSL 图册与多阶段生成）的工程实例 |

**建议阅读顺序**：先读本文的 §4-§5 建立工程认识，再读姊妹篇 grc-mapping 的 §3-§6 上升到理论形式化，最后回到本文 §5.4 与姊妹篇 §8 的"未来工作"小节。

## 摘要

本文从可逆计算理论出发，澄清"可逆"的常见误解，并基于对 dsh 源码的核实，精确分析 dsh 的可逆性范围与粒度：它作用于**运行时结构空间**（注册/监听/资源层）而非业务数据空间；坐标是 **plugin id 级**而非字段级；配置层是**整段 config 覆盖**而非差量代数。在此基础上，本文讨论三层空间划分、运行时坐标（服务/事件）上的顺序与交互语义、registration 作为 generator 展开 delta 与"时间静止"的关系、可逆性（能力）与响应式（治理）的正交性、"注册机制前提到结构空间"的 Nop 完整实现（xbiz.xdef / xwf.xdef / task.xdef 三种策略谱系），以及运行时注册为何仍是必须的（agent 级 scope 差异：注册内容何时可知，决定结构空间与运行时空间的分界线）。

## 目录

- 〇、背景与问题
- 一、引子：一种让社区困惑的架构
- 二、可逆计算中的"可逆"到底指什么
  - 2.1 可逆不是逆向执行
  - 2.2 可逆的本质：信息可追溯性与可分离性
  - 2.3 结构空间与运行时空间：软件世界的"相空间"
- 三、dsh 的可逆机制：revertible effects 是怎么工作的
- 四、仔细分析：dsh 的可逆到底是"每个行为都可逆"，还是"scope 隔离"？
  - 4.1 机制层面：每个行为都是"可逆的"——但可逆性是契约而非验证
  - 4.2 语义层面：可逆是观测等价意义下的，不是字面恢复
  - 4.3 边界层面：系统边界之外不可逆
  - 4.4 scope 层面：plugin 划分的作用域隔离是信息可管理性的前提
  - 4.5 三层空间：结构空间 → 运行时结构空间 → 运行时具体状态
  - 4.6 结构坐标：plugin id 是什么级别的坐标？
  - 4.7 运行时空间的重叠处理：按 realm 隔离，而非全局唯一
  - 4.8 运行时结构坐标系：坐标点上的顺序与交互语义
  - 4.9 总结：dsh 的可逆是什么
- 五、与 Nop 的对照：结构空间可逆与运行时空间可逆的互补
  - 5.1 一个重要澄清：dsh 的 YAML override 不是 Nop 的 Delta
  - 5.2 论文没有理清的关系：静态 patch 层与运行时 loader 层要分开看
  - 5.3 两条路线的互补性
  - 5.4 插件若支持 delta 定制，原则上仍要回到结构空间的差量代数
  - 5.5 被动 loader 与主动组装：时间性的对偶
  - 5.6 registration 作为 generator：在运行时结构坐标处展开 delta
  - 5.7 可逆性与响应式是正交的两个维度
  - 5.8 注册机制前提到结构空间：Nop 的完整实现
  - 5.9 运行时注册为什么仍是必须的：agent 级 scope 差异
- 六、回到"可逆"的哲学：分离到可逆，控制熵增的位置
- 七、结论
- 参考文献
- 附录：源码核实方法与关键文件对应

## 〇、背景与问题

本文最初是对一组读者问题的回应。为使后文中反复出现的"用户"指代有据，先把问题本身列出。读者/社区主要追问三件事：

1. **dsh 的可逆到底指什么？** 是"每个行为都可逆"，还是只是"plugin 划分了 scope，仅仅保证 scope 之间隔离，从而让信息在粗粒度上可管理"？
2. **dsh 的文章是否明确理清了结构层与运行时层的关系？** 运行时结构坐标系上的覆盖顺序、累加无删除、顺序无关假定分别是否成立？结构空间（源码）与运行时结构空间（实例化展开）的对应关系是怎样的？
3. **注册机制能否完全前提到结构空间？** 可逆计算主张把注册展开为结构空间中的 DSL，通过编译期 delta 组合，运行时只应用 delta，展开动作放到 loader 中；既然如此，dsh 为什么仍在运行时做注册？"全局注册"的困难到底在哪里？

下文中的"用户"均指提出这些问题的读者。这三个问题分别对应第四、五节的展开。

## 一、引子：一种让社区困惑的架构

DeepSeek Harness（dsh）的架构设计非常独特。它的 README 用一句话概括了架构——"一切皆插件"（everything is a plugin），而驱动它的框架是 Cordis——一个其设计论文题为《A Programming Paradigm for Spatiotemporal Composability》的元框架。

这篇论文把软件系统的动态组合（dynamic composition）分解为两个正交维度：

- **时间可组合性（temporal composability）**：组件卸载时，它对共享环境所做的修改必须被完全、安全地逆转。论文用"可逆 effect"（revertible effects）形式化这一要求。
- **空间可组合性（spatial composability）**：组件能够以结构化、可验证的方式声明、发现并解析相互依赖。论文用"响应式 coeffect"（reactive coeffects）形式化这一要求。

社区对这套设计最常见的反应是怀疑："副作用（side effects）是必然存在的，软件怎么可能可逆？"——在传统的图灵机世界观下，状态一旦被写入就是事实，不可抹去；程序执行沿着时间箭头的方向单向推进，所谓可逆只能是痴人说梦。

这个怀疑本身是合理的，但它建立在望文生义的基础上：把"可逆"理解为"可逆运行"（即让程序沿时间逆行、把所有副作用原样撤销）。可逆计算理论（以及 Cordis 论文）中的"可逆"从来不是这个意思。

## 二、可逆计算中的"可逆"到底指什么

### 2.1 可逆不是逆向执行

可逆计算理论（Reversible Computation）由 canonical（Canonical Entropy）于 2007 年前后提出，其思想源头不是计算机科学本身，而是理论物理学——统计物理的熵增原理和量子力学中的狄拉克图景。注意这里的 Reversible Computation 需要与硬件领域的"可逆计算机"（Reversible Computing，如 Landauer、Toffoli 等可逆逻辑门研究）相区分：可逆计算理论强调的是高层的抽象结构（差量、逆元、坐标系），而不是底层的物理实现（见 [5]）。它提出的软件构造公式是：

```
App = Delta x-extends Generator<DSL>
```

"可逆"在这里体现在**逆元**（inverse element）与**逆运算**的存在上：Delta 是正负原子元素的混合体，可以表达"减"；`x-extends` 有配套的逆运算 `x-diff`，使得 `Delta = App x-diff Base` 可以从合并结果中反向提取出差量。这与逆向执行没有任何关系——事实上大部分可逆计算发生在编译期，此时程序根本还没有运行。

正如理论文章 [2] 所强调的：

> 可逆计算中的可逆并不是逆向执行的意思。实际上，因为一般情况下我们总是在编译期应用可逆计算，此时根本没有到运行期，也就不存在什么逆向执行的问题。

### 2.2 可逆的本质：信息可追溯性与可分离性

那"可逆"到底指什么？在我看来，可逆的本质是两条能力：

1. **信息可追溯性**：任何施加到系统上的变化，都可以找到其来源、记录其作用范围，并拥有一个与之配对的撤销机制；
2. **信息可分离性**：系统可以在不破坏整体结构的前提下，把某个组成部分及其影响"完整地"拿出来——既能加进去，也能拿出来，且拿出之后剩余部分保持可理解、可运行。

这与熵密切相关。物理世界中熵增是时间箭头，是不可回避的本质约束；可逆计算理论并不幻想消灭熵增，而是主张**控制熵增发生的地方**——把不可逆的偶然因素集中到可抛弃的差量 Δ 中，保护核心架构不被侵蚀（见 [3]）。"可逆"因此是一个分层的、有边界的、相对的概念，而不是全有全无的绝对属性。

### 2.3 结构空间与运行时空间：软件世界的"相空间"

要理解 dsh 的可逆性，需要引入一个关键区分：软件的构造与运行发生在两个性质不同的空间，它们合在一起构成软件世界的"相空间"：

- **结构空间**：程序、模型、DSL、配置所构成的空间。这是"软件是什么"的层面——代码是编译期被读、被合并、被生成的结构化对象。
- **运行时空间**：进程状态、内存、依赖图、生命周期所构成的空间。这是"软件在做什么"的层面——状态在运行期被创建、被修改、被销毁。

可逆计算理论明确指出：**可逆并不要求全量**——不要求在整个相空间的每个层次上都可逆。Nop 平台目前的实践就是如此：

- **结构空间的可逆**是 Nop 的主体做法：所有 DSL 支持差量合并 `x-extends`，支持逆向求解 `x-diff`，合并运算满足结合律。这些运算可以发生在编译期和模型加载期，完全不触及运行时的执行轨迹。
- **运行时空间的可逆**：Nop 目前的运行时演化主要依靠"无状态 + 不可变 + 重新生成"——模型加载器在检测到依赖变化后整体重新执行 Delta ⊕ Base 合并，用全新生成的模型替换旧模型（相当于 JIT 式的"动态即时生成"）；涉及状态迁移时采用"时间静止"策略（冻结→修正→恢复）。这是一种粗粒度的、结构层面的运行时演化，而非对逐条 effect 的逆跟踪。
- **运行时空间的部分可逆**：Nop 计划通过 plugin 机制实现（见 `ai-dev/design/nop-plugin/` 设计文档），这正是在向 dsh/Cordis 论文所形式化的方向靠拢。

理解了这一点，再回头看 dsh，就会发现它选择了一条与 Nop 互补的路径：**在运行时空间实现可逆**——不是通过重新生成整个结构，而是通过为每个 effect 显式配备逆函数，由运行时跟踪并组合这些逆。

## 三、dsh 的可逆机制：revertible effects 是怎么工作的

Cordis 论文的核心构造（[1, §3.1]）非常精巧。它把"类型上下文"提升为"上下文类型"（context type），把 effect 建模为：

```
effect : Γ → Γ × (Γ → Γ)
```

即：一次 effect 不仅产生新的上下文状态 γ′，还同时返回一个**逆函数** g：Γ → Γ。运行时不需要猜测如何撤销，逆由 effect 的制造者当场给出。这就是"可逆 effect"——每个上下文变换都携带显式逆，并且逆的构成遵循一个精确定义的代数：

- **扭曲复合（twisted composition）**：`(f₁, g₁) ∘ (f₂, g₂) = (f₁∘f₂, g₂∘g₁)`——逆以相反顺序累积。这是 LIFO（后进先出）恢复原则的代数表达。
- **track 是幺半群同态**（[1, Theorem 5]）：`trackΓ(f₁∘f₂, g₂∘g₁) = trackΓ(f₁,g₁) ∘ trackΓ(f₂,g₂)`——**逆的复合等于复合的逆**，恢复操作与 effect 组合可交换。这是"可逆性可复合"的数学基础。
- **recover**（[1, Definition 6]）：`recoverΓ(γ, φ) = (φ(γ), idΓ)`——应用累积的逆函数 φ，把上下文恢复到初始状态。

在实现层面（[1, §5.1.1]），dsh/Cordis 把这一切收敛为一个单一原语 `ctx.effect(callback)`。但**"一切变更都经过 ctx.effect"这个表述需要精确化**——我核对了 dsh 源码（`~/ai/deepseek-harness`，2026-08-17 工作区）中的 `ctx.effect` 调用：在全部 .ts/.tsx 文件中共 **244 处**，排除 `test/`、`tests/` 目录后为 **205 处**。它们并不覆盖任意的业务数据写入，而是高度集中在同一个类别：**把"结构"挂载到共享运行时之上的动作**：

- **服务发布**：`ctx.provide(name, value)`（reflect.ts）—— 一个 key 只能由当前 fiber 提供，同一 realm 内同名重复注册直接抛错；
- **事件监听注册**：`ctx.on(event, listener)`（events.ts）—— 监听器作为 effect 存入 fiber，卸载时自动注销；
- **资源获取与释放配对**：`domain.close`、sqlite 连接关闭、pty teardown、worker teardown、webServer 路由注册；
- **注册表写入**：工具注册（`ctx.tools`）、agent 注册、命令注册、locale 字典注册、系统提示区块注册、typert 描述符注册；
- **会话结构创建**：`sessions.create` 通过 `ctx.effect` 包裹 store 条目挂入 + 发布通告。

而**业务数据流本身并不经过 effect**：dsh 的会话日志（session log）是 append-only 的不可变事件流（`this.log.push` 直接追加、deep-frozen），它是"事实记录"而非"可逆操作"——日志只能追加，不能撤销（这正是 [1, §6.1] 系统边界外 emission 的形态：写日志即对外发表，不可逆，只能靠保留与补偿来管理）。

配合这一机制的是**组件/纤维（fiber）**结构（[1, §4.1]）：每个组件声明它依赖什么（coeffect specification d）、提供什么（provision p）、执行什么 effect（e）。每个 fiber 拥有自己的：

- **accumulator**：本 fiber 累积的逆函数复合（`fiber.dispose`）；
- **committed view**：本 fiber 激活时所依赖的解析结果（`fiber.committed`）；
- **coeffect table**：本 fiber 写入的绑定（`fiber.ctx[@@store]`）。

生命周期由 `refresh → reload/unload` 的互递归驱动（[1, Algorithm 5]）：依赖满足则 reload（执行 effect、记录视图、合成逆），依赖丧失则 unload（先通知依赖者撤离，再执行逆、清空视图）。整个过程带有**惯性**（inertia）：一旦进入转换，就运行到完成，避免中间状态风暴。

## 四、仔细分析：dsh 的可逆到底是"每个行为都可逆"，还是"scope 隔离"？

这是理解 dsh 的关键问题。我的结论是：**两者都是，但都不是社区想象的那个意思。** 我们需要逐层拆解。

### 4.1 机制层面：每个行为都是"可逆的"——但可逆性是契约而非验证

在机制层面，dsh 要求**每一个作用于运行时结构空间的 effect** 都携带逆——注册、监听、资源获取等结构性变更必须经过 `ctx.effect`，由运行时强制跟踪。但注意（如第三节核实所示）：这里的"一切变更"指的是**运行时结构空间的变更**，业务数据流（session log）并不经过此机制。论文说得很诚实（[1, §5.1.1]）：

> What the operation does not check is the witness that 𝔈Γ* carries: the callback supplies an inverse, and that the inverse recovers the effect it accompanies is an obligation on the component author rather than a property the runtime verifies.

即：**运行时并不验证逆是否正确**。逆是否真的撤销了 effect，是组件作者的义务（obligation），不是运行时的性质（property）。可逆性是**机制上的普遍性 + 语义上的契约性**——运行时保证"你的逆会被保存、会被按正确顺序执行、不会被执行两次"（armed 标志、LIFO、guard 中断），但不保证"你的逆真的有用"。这正是可逆计算的"分离到可逆"思想：框架负责可逆的机制结构，作者负责可逆的语义内容。

> **结论标签**：可逆性 = 机制普遍性 × 语义契约性；运行时管"如何撤"，作者管"撤得对不对"。

### 4.2 语义层面：可逆是观测等价意义下的，不是字面恢复

论文 [1, §3.3.2] 直截了当地承认了字面可逆的不可能性：

> The recovery guarantee of Section 3.1 asserts an equality of states (Theorem 7), which is an idealization, because the physical state cannot be recovered as it stood. For example, `free` releases a block to the allocator without restoring the layout the heap had before `malloc`; and a generative name is not restored by the inverse that discards it...

所以可逆是**相对于观测等价关系 ≃** 的：两个状态只要没有任何观察者能区分它们，就算等价。"恢复到初始状态"实际意思是"恢复到不可区分的状态"。这本质上就是物理学的熵约束在软件中的映射——信息可以被抹除到不可观测的程度，但不可能被原样还原；我们追求的是可观测层面的一致，而不是微观层面的同一。

> **结论标签**：可逆的目标是 ≃（观测等价），不是字面相同；这是 dsh 对"全量可逆"朴素理解的诚实纠正。

### 4.3 边界层面：系统边界之外不可逆

论文 [1, §6.1] 明确引入了**系统边界**（system boundary）概念，这是回答"副作用怎么可能可逆"的最重要部分：

- **边界内（inside）**：系统能够独占修改、且能够在修改前恢复的位置。这里的操作被跟踪进上下文 Γ，可以被恢复。
- **边界外（outside）**：系统无法独占修改或无法恢复的位置（如网络写入、共享文件、外部进程可见的状态）。这里的操作被视为 `idΓ`——**既不跟踪，也不恢复**。

而外部操作通常分两个阶段：**获取阶段**（acquisition，如 open 一个描述符、malloc 一块内存）发生在边界内，是可逆的；**发出阶段**（emission，如把字节写入文件、把数据报发到网络）跨越边界，不可逆。对于不可逆的 emission，论文给出了两条出路：**保留**（withhold，直到状态确定持久化再发出，即 output commit）或**补偿**（compensation，Saga 式地执行一个业务层面的反向操作，如删除已创建的文件、退还已收的款项）。注意：补偿本身并不满足元理论的交换条件（commutation），它的可逆性是"应用层提供的、更粗的等价"意义上的。

**所以，dsh 的可逆从来不是"每个行为在物理上可逆"。** 它说的是：每个行为要么在边界内被机制性地跟踪和恢复，要么在边界外被显式地标记为不可逆，并通过保留/补偿来管理后果。**可逆性是分层、有边界的，这正是"可逆并不要求全量"的工程化表达。**

> **结论标签**：可逆性由系统边界划分——边界内机制跟踪 + 边界外保留/补偿；"可逆并不要求全量"的工程含义在此落地。

### 4.4 scope 层面：plugin 划分的作用域隔离是信息可管理性的前提

现在回到用户问题中最核心的部分：dsh 的可逆，到底是不是"plugin 划分了 scope，仅仅保证 scope 之间隔离，从而让信息在粗粒度上可管理"？

我的分析是：**scope 隔离不是可逆性的替代品，而是可逆性的载体——没有 scope 隔离，可逆机制根本无法建立。** 但同时必须承认：**dsh 的可逆确实只作用于"运行时结构空间"，而不是"运行时具体状态空间"。**

Cordis 论文的元理论并非无条件成立，它建立在三条结构性约束之上（[1, §4]）：

1. **限制性（confinement, Definition 48）**：一个 fiber 的 effect 只能写自己的 co-effect 表 σₙ，只能读自己声明的依赖 key——不能读写其他 fiber 的表，不能窥探其他 fiber 的生命周期控制字段。这**限制了信息组合范围**：任何两个插件之间的交互都必须通过显式声明的 co-effect 进行，不存在隐形的全局共享。
2. **供应不相交（disjoint provisions, Definition 43）**：同一个 key 至多由一个 fiber 提供（在 dsh 实现中是 per-realm 的互斥，见 4.7）。依赖拓扑是良构的，不存在两个提供者互相覆盖的暧昧状态。
3. **依赖无环（acyclic precedence, §4.4.4）**：依赖偏序 ≺ 无环，这是无死锁（Theorem 66, Progress）的前提。

在这三条约束下，元理论才成立：

- **恢复精确性（Theorem 61, Recovery exactness）**：在 pairwise independence 条件下，运行某 fiber 的 accumulator 能够"撤回该 fiber 的贡献，且仅撤回该 fiber 的贡献"——其他 fiber 的贡献不受影响（up to control fields）。
- **排序（Theorem 63, Ordering）**：提供者只能在依赖者全部卸载之后才撤销自己的供应。
- **进度（Theorem 66, Progress）**：任何非静止状态都能继续演化，最终到达静止状态。

换言之：**局部可逆（每个 fiber 的 accumulator 撤回自己的贡献）在 scope 隔离的条件下复合为整体可逆（整个系统的卸载序列精确、有序、必然终止）。** 这就是"局部可逆的复合可以保证整体的可逆性，可逆性的复合性非常重要"在 dsh 中的具体形态——它不是口头原则，而是以定理形式证明了的性质。而 scope 隔离本身也是一种"严格自律后形成的整体性"：每个插件自律地只通过上下文交互、只声明所需、只提供所承诺，系统作为一个整体才获得了"任何一部分都可以被安全拿走"的性质。**插件即信息污染的最小单位**——VSCode 87/100 的扩展需要重启宿主才能卸载（[1, §1.2.1]，数据取自 VSCode Marketplace，2026-06-09），正是因为它的扩展缺少这种 scope 隔离与可逆机制，导致信息在共享进程中大面积扩散，无法定点回收。

> **结论标签**：scope 隔离不是可逆性的替代品，而是可逆性的载体；局部可逆（per-fiber accumulator）在 confinement + 供应不相交 + 依赖无环的前提下，复合为整体可逆。

### 4.5 三层空间：结构空间 → 运行时结构空间 → 运行时具体状态

把前面核实的事实放回可逆计算的分层框架，dsh 实际上把软件的"相空间"切成了三层：

1. **结构空间（配置层）**：`cordis.yml` + bundle patch 层。这是声明式的、静态的，用 YAML 描述"系统该由哪些插件行构成"。
2. **运行时结构空间（注册层）**：`ctx.effect` 所管理的一切——服务注册、监听器、工具、命令、资源句柄、fiber 树本身。**这是 dsh 可逆机制真正作用的范围**：全部 244 处 `ctx.effect` 调用（非测试代码 205 处）都落在这里。
3. **运行时具体状态空间（数据层）**：session log（append-only 不可变事件流）、数据库内容、外部世界的状态。**这一层在 dsh 中是不可逆的**——日志只追加不撤销，数据库写入通过系统边界外语义处理。

所以 dsh 的可逆性主张是精确的：**它不要求"每个行为"都可逆，而是要求"每一个改变运行时结构的注册行为"都可逆。** 运行时的"具体状态"（业务数据）走的是另一条路——append-only 事件日志 + 重放，这与可逆是正交的另一种信息管理策略（在 Nop 的术语中，事件流是状态空间的差量序列 `NewState = OldState ⊕ Event`，可逆计算中的 Event Sourcing 本身就是一种"差量管理"而非"逆执行"）。

这回答了用户问题的第一部分：**dsh 强调的不是"所有动作都可逆"，而是"结构管理"**——对运行时结构空间（谁注册了什么、谁监听了什么、谁占用了什么资源）做系统化的可逆规划。运行时结构空间是"运行时空间本身的一个规划"，把它管理好，插件才能安全装卸；而具体的业务状态，交给不可变日志去管。

> **结论标签**：三层空间划分回答"dsh 可逆作用于哪一层"——**仅中间层（运行时结构空间）可逆**；上下两层分别由结构空间差量代数与不可变事件流管理。

### 4.6 结构坐标：plugin id 是什么级别的坐标？

可逆计算理论强调：要在不修改主系统源码的情况下通过定制实现修改，必须建立**领域结构坐标系**——否则差量无法精确定位。dsh 的结构坐标是什么？

答案：**entry id（插件行 id）是 dsh 的结构坐标，它只精确到"一个插件的整个配置"这一级，不深入到配置内部。**

从源码看，dsh 的静态 patch 机制（`applyEntryPatches`，dsh/vendor/include）是：

- 以 `id` 为 key 在 entry 树中定位一行；
- patch 对该行的处理是**对 patch 中出现的顶层字段做整体赋值**：`target[key] = value`（被指定的 `config` 等字段整体覆盖，未指定的字段保留）或 `insert` 新行；
- 文档明确写着："A patch replaces whole row configs — profile overrides must restate every field a row keeps; there is no deep-merge layer"（bundle/base/README.md）、"A user patch replaces the whole matched config — an id-targeted patch does not deep-merge"（app-boot/README.md）。

也就是说：**dsh 的配置定制坐标 = plugin id，粒度 = 整段 config 覆盖**。它没有 Nop 那样的"Tree 结构 + 唯一属性"的字段级坐标（Nop 可以精确到 `<form-item name="password">` 这一个节点，dsh 只能整段覆盖 `my-plugin` 的全部 config）。这正好印证了用户的判断：**plugin 划分 scope 的本质是把运行时空间按插件 id 划分为粗粒度区域**——可逆性和可管理性都是在"插件"这个粗粒度上建立的，而不是在"任意字段"的细粒度上。

> **结论标签**：结构坐标 = entry id；粒度 = 整段 config 覆盖（非字段级）；可逆性建立在插件级而非字段级。

### 4.7 运行时空间的重叠处理：按 realm 隔离，而非全局唯一

那么对于运行时空间的重叠（两个插件都想提供同一个 key）有处理吗？有，dsh 的处理比"按 plugin id 隔离"更精细一层：**按 isolation realm（隔离域）隔离**。

- 在**同一 realm 内**，供应是互斥的：`ctx.provide` 重复注册直接抛错（`service "name" has been registered at <fiber.name>`），这对应论文的 disjoint provisions（Definition 43）。
- 跨 realm 则允许同名共存：`isolate: true` 给 entry 一个以 `#id` 为后缀的私有 realm（entry-local）；`isolate: <label>` 给共享同标签的 entry 一个 `@label` 全局 realm。同名的 key 在不同 realm 解析到不同的绑定（对应论文的 coeffect isolation, Definition 28）。
- 拦截（intercept）是另一种重叠处理：不改绑定值，只改访问方式（权限、元数据），且拦截属于 derived 上下文，不需要逆。

所以严格来说：dsh 的隔离**不是**简单的"按 plugin id 隔离"，而是**按 realm 隔离，realm 默认与 plugin id 绑定但可以被共享标签改写**。这一点比"plugin id 隔离"更精细——它允许两个插件在同一物理命名下各自维护独立的绑定空间，是"对重叠的显式管理"。

> **结论标签**：隔离粒度 = realm（默认 per plugin id，可被 isolate label 改写）；重叠显式管理（per-realm 互斥 + 跨 realm 共存 + interception）。

### 4.8 运行时结构坐标系：坐标点上的顺序与交互语义

上面几节回答了"坐标是什么"，但还有一个更深的问题：**同一个坐标点上，多个插件的操作如何保证顺序关系和交互关系？** 这需要区分"结构空间"与"运行时结构空间"的对应关系。

**结构空间（源码）→ 运行时结构空间（实例化展开）**。用户的观察完全正确：结构空间是源码的结构，而运行时结构空间是源码在运行时展开后的结构——**同一个组件在运行时可能被实例化为多个 fiber**（每个 fiber 是一次独立实例化，有自己的生命周期状态）。例如一个"数据库驱动"组件可以在同一进程中被两个 realm 分别实例化，各自维护独立连接。但**一旦展开（fiber 进入 ACTIVE），其注册的结构就固定下来**：fiber 的 effect 列表、committed view、提供的 key 都不再变化，直到依赖或配置变化触发 reload/unload。这就是"惯性"（inertia）机制的意义——运行时在两次转换之间给插件提供的是一个**稳定快照**，而不是不断流动的流体。这证实了用户的判断："运行时在很大程度上它的基本结构，也就是插件所面对的结构，是稳定的。"

**服务与事件是两种不同性质的坐标**：

1. **服务（service key + realm）= 单写多读坐标**。同一 realm 内一个 key 只有一个提供者（重复 `provide` 直接抛错），消费者通过 `inject` 声明后读取。因为是单写者，**同一坐标点上不存在"两个写操作的顺序问题"**——写者唯一，读者是纯消费。提供者的变更（卸载旧 fiber + 装载新 fiber）由运行时序列化，且 epoch 机制（`fiber.uid` 拼接的依赖指纹）保证消费者只在提供者身份变化时才重新解析。

2. **事件（event name）= 多写坐标**。多个插件可以在同一事件名上注册多个 handler，**顺序 = 注册顺序**（hooks 数组 push 顺序，可 `prepend` 提前），交互语义由**五类 dispatch mode** 显式定义（见 dsh 仓库 vendored Cordis 的 `events.ts`）：

| dispatch mode | 语义 | 顺序相关性 |
|---|---|---|
| `emit` | 同步执行、忽略返回值 | 结果与顺序无关 |
| `parallel` | 并发执行、等待全部 | 假定独立 |
| `serial` | 按序执行、遇 bail 值停止 | 顺序相关 |
| `bail` | 同步按序、遇 bail 值停止 | 顺序相关 |
| `waterfall` | 链式组合，每个 listener 必须 `next()` 委派 | 强顺序相关，未调用 next 即否决 |

所以 dsh **并不假定"同一事件的 handler 顺序无关"**——`emit`/`parallel` 模式的结果与顺序无关，但 `serial`/`bail`/`waterfall` 模式明确依赖顺序，`waterfall` 甚至要求每个 handler 显式委派。**顺序的语义由坐标（事件）的声明决定，而非由插件决定**：插件无法控制其他插件 handler 的相对位置（只能影响自己的注册时机），这正是"运行时处理在插件处理之外"——事件分发、服务解析、生命周期转换全部由 Cordis 运行时（reflect.ts/events.ts/fiber.ts）实现，插件只贡献 effect 和 listener。

**如果把插件内部结构声明式表达**（像 Nop 的 DSL 那样），那它就相当于是"动态覆盖到运行时结构坐标系上"——这正是用户想象的形态。dsh 目前**没有走到这一步**：cordis.yml 只声明到 entry 级（插件行），插件内部的服务注册/事件监听仍是指令式代码（`ctx.on`/`ctx.provide` 在 apply 回调中执行）。因此 dsh 的"覆盖"止步于插件边界，插件内部没有结构坐标。如果未来插件内部也声明式化，那么：

- **覆盖顺序**：不同插件按"entry 树声明顺序 + 依赖就绪的拓扑约束"确定加载顺序——依赖不满足的 fiber 停在 L-Begin 等待（`_refresh`/`_setEpoch`），所以是**声明序 + 拓扑序的混合**，而非严格的纯拓扑排序；
- **累加无删除**：成立。confinement 保证每个 effect 只能添加自己的贡献、卸载时撤销自己的贡献，**不存在"替别人删除"的能力**——这正是 dsh 不需要"负元素"（如 Nop 的 `x:override="remove"`）的原因，但也意味着它无法像 Nop 那样修改基座中已有的节点；
- **顺序无关的假定**：不成立，如上表所示——顺序语义由 dispatch mode 声明，需要顺序的坐标（serial/waterfall）必须显式处理顺序。

> **结论标签**：顺序语义由坐标声明决定（dispatch mode + realm），而非插件决定；插件只能影响自身注册时机，不能控制其他插件的相对位置。

### 4.9 总结：dsh 的可逆是什么

综合 4.1-4.8，dsh 的可逆可以精确表述为：

- **范围**：运行时结构空间（注册/监听/资源获取），不是业务数据状态空间；
- **粒度**：以 plugin/fiber 为单位的 scope，坐标是 entry id，不深入到字段；
- **稳定性**：fiber 一旦展开（ACTIVE），其注册结构即固定，直到依赖/配置变化触发转换——插件面对的是稳定快照而非流动状态；
- **坐标**：服务（单写多读，per-realm 互斥）+ 事件（多写，顺序=注册顺序，语义由 dispatch mode 声明）两类运行时坐标；顺序语义由坐标声明决定而非插件决定；
- **累加性**：effect 只添加自己的贡献、只撤销自己的贡献，不存在"替别人删除"——因此不需要负元素，但也无法修改基座；
- **语义**：观测等价（≃）而非字面恢复，逆的正确性是作者契约而非运行时验证；
- **边界**：系统边界外（日志、网络、外部世界）显式不可逆，用保留/补偿管理；
- **复合**：在 confinement + 供应不相交（per realm）+ 依赖无环的前提下，局部可逆复合为整体可逆。

这与"每个行为都可逆"的朴素理解相去甚远，但恰恰是"可逆并不要求全量"的工程化实现。

## 五、与 Nop 的对照：结构空间可逆与运行时空间可逆的互补

Nop 与 dsh 构成了可逆计算谱系上的两个互补样本：

| 维度 | Nop 平台 | dsh / Cordis |
|------|---------|-------------|
| 可逆的主要空间 | 结构空间（编译期/加载期） | 运行时结构空间（运行期注册层） |
| 可逆的基本单元 | Delta 差量（结构对象） | 逆函数（行为对象） |
| 合并/复合运算 | `x-extends`（满足结合律） | twisted composition（幺半群同态） |
| 逆运算 | `x-diff`（从结果提取差量） | dispose/accumulator（从 effect 提取逆） |
| 恢复的时机 | 加载期重新生成 | 卸载期执行逆序列 |
| 坐标粒度 | 字段/节点级（Tree + 唯一属性） | 插件行级（entry id） |
| 隔离机制 | Delta 分层 + 领域坐标系 | fiber scope + co-effect realm |
| 边界处理 | 生成结果外的黑魔法被显式隔离 | 系统边界外 emission 用保留/补偿 |

### 5.1 一个重要澄清：dsh 的 YAML override 不是 Nop 的 Delta

我此前容易产生一个印象：dsh 的 `cordis.patch.yml` overlay 机制与 Nop 的 Delta 定制是"同构"的。**核实源码后，这个类比不成立，两者差别很大。**

dsh 的静态 patch 机制（`applyEntryPatches`）语义是：

- **以 entry id 为坐标**，patch 覆盖该行的整个 `config`（或 `disabled`、`inject` 等字段）；
- 更精确地说：对非 insert patch，`applyEntryPatches` 遍历 patch 中出现的顶层字段并执行 `target[key] = value`——**被指定的字段（尤其 `config`）整体覆盖，未指定的字段保留**；它不是把整行对象替换掉；
- **没有深合并**：源码注释与文档反复强调 "a patch replaces the targeted row's whole config rather than merging into it"、"there is no deep-merge layer"；
- 只支持两种操作：**整段字段覆盖 + 插入新行**。不支持节点级增删改，不支持 `x:override="remove"` 这样的负元素，不支持 `x:gen-extends` 这样的生成机制，也没有结合律保证（overlay 的顺序即优先级，最后写的赢）。

而 Nop 的 Delta 是：**任意深度的 Tree 结构差量**，有明确的节点坐标（name/id 唯一属性）、支持增/删/改/替换指令、满足结合律、支持 `x-diff` 逆向提取、`x:gen-extends` 动态生成。dsh 的"插件级整段 config 覆盖"在 Nop 的坐标系里连"字段级差量"都算不上——它相当于只能对整个 `page.yaml` 做整体覆盖，而不能只改其中的一个字段。

**换句话说：dsh 的结构空间定制能力是"粗粒度替换"，Nop 的结构空间定制能力是"细粒度差量"。** 这正是为什么 dsh 论文把重点完全放在运行时空间（revertible effects）上——它的结构层没有形成差量代数，能做的只是整段覆盖。

### 5.2 论文没有理清的关系：静态 patch 层与运行时 loader 层要分开看

用户的问题非常尖锐：**dsh 的文章是否明确理清了结构层与运行时层的关系？** 我的核实结论是：**论文在概念层面没有完全理清，但下这个判断之前必须先把 dsh 的两个层面分开，否则会打错靶子。**

论文 [1, §5.2] 确实描述了 declarative configuration（entry 树、reconciliation、keyed diff over child ids），但它：

1. 没有把配置层的 override 形式化为差量代数——论文从未讨论"配置层的合并运算是否满足结合律、是否有逆元"这类问题，而这正是可逆计算理论最关心的；
2. 没有说明"配置层（结构空间）"与"运行时 effect 层（运行时空间）"之间坐标的关系——entry id 如何映射到 fiber、配置的修改如何变成 effect 序列，论文只有算法级描述（Algorithm 5/7），没有概念层面的统一；
3. 在实现层面，dsh 实际上有**两套不同粒度的"配置 → 运行时"通道**，必须分开评估：
   - **静态 patch 文件层**：`cordis.patch.yml`、bundle 层、user 层、`--patch` overlay 共享 `dsh/vendor/include/src/index.ts` 的 `applyEntryPatches` 语义——以 entry id 为坐标，对 patch 中出现的顶层字段（尤其 `config`）做**整体覆盖**，外加 `insert` 新行；没有深合并，没有节点级增删改。**这一层才是"结构空间粗粒度替换"的直接证据。**
   - **运行时 loader reconciliation 层**：`dsh/vendor/loader/src/config/entry.ts` 的 `Entry.update()` 按字段分发。论文 §5.2 的 reconciliation 对应的是这一层，而不是静态 patch 文件层。具体字段处理如下表（基于源码核实，未列出的字段沿用浅合并或组件自定义）：

      | 顶层字段 | 处理方式 | 是否深合并 | 备注 |
      |---|---|---|---|
      | `id` / `name` / `url` | 重建（rebuild） | 否 | 标识类字段，变化即视为不同插件行 |
      | `isolate` | 重配 realm（重新派生 `#id` / `@label`） | 否 | 作用域标识变化会触发 fiber 重实例化 |
      | `intercept` | 原地更新（in-place） | 否 | 不改绑定值，只改访问方式；属 derived context |
      | `disabled` | 卸载 / 重载（unload + reload） | 否 | 真值翻转直接驱动生命周期转换 |
      | `config` | 交给组件决定 | **取决于组件** | 普通组件整体替换；`group` 的 `config` 是 child 列表，**做 keyed diff**（这是论文 §5.2 reconciliation 的真正落点） |

      **关键判断**：运行时 loader 的 reconciliation **不是统一的"整段覆盖"**，它对 5 个字段分别采用 5 种不同的策略——其中只有 `config` 在 group 场景下做了字段级的 keyed diff。这是 dsh 比静态 patch 文件层更精细的地方，但精细不等于形成了差量代数：(1) `config` 之外没有字段级增删改；(2) keyed diff 只对 group 的 child 列表，不是对任意嵌套结构；(3) 没有 `x:override="remove"` 这类负元素，没有结合律证明。

因此，准确的表述是：**论文描述了运行时 loader 的 per-field reconciliation（其上有 group 的 keyed diff），但它没有把这一机制抽象成结构空间的差量代数；而 dsh 的静态 patch 文件层——用户实际做配置定制的入口——更是停留在"整段 config 覆盖 + insert"的粗粒度。** 这印证了 dsh 的结构层尚未达到可逆计算理论所要求的"结构坐标系 + 差量代数"标准。

所以，dsh 论文的真正贡献集中在运行时空间：它给出了"运行时结构空间的可逆"的完整形式化（逆的代数、复合条件、系统边界），但**结构空间这一层在 dsh 中仍停留在朴素的整段覆盖阶段**——这恰好是 Nop 最擅长的领域。两者合在一起，才构成完整的可逆计算图景：结构空间用差量代数管理（Nop），运行时结构空间用可逆 effect 管理（dsh），运行时具体状态用不可变事件流管理（两者都在朝这个方向走）。

### 5.3 两个域的细化形态都验证 F(X)+Δ 的普适性

Nop 与 dsh 都是 GRC `App = F(X) ⊕ Δ` 模式在不同域的细化形态，**不是两套互补的理论**——差异仅在"X 是什么、F 用什么算子、逆如何构造、主动设计的坐标是什么"。

- **Nop（GRC 结构域细化）**：X = 结构树、Δ = 树差量、F = x-extends、逆 = x-diff。结构空间天然是"静态的、可重放"的，字段级坐标 + 结合律 + 逆元让它拥有完整的差量代数形式证明（见姊妹篇 `2026-08-17-grc-dsh-theory-mapping.md` §六 提到的 `proof-v2.md`、`grc-delta-associativity-formal-proof.md`）。Nop 的 `ResourceComponentManager` 是 GRC「Loader = Generator」原则在结构域的具体化（见 `counterintuitive-software-design-insights.md:252`）——加载器把 Delta ⊕ Base 合并升级为生成器，主动构造完整模型。
- **dsh（GRC 运行时域细化）**：X = 运行时上下文（coeffect table / fiber state）、Δ = 带逆的 effect、F = twisted composition、逆 = dispose / accumulator。运行时结构空间是"动态生成的、有状态的"，需通过为每个注册行为显式配备逆、由运行时跟踪组合——坐标粒度停留在 entry id（插件行）而非字段级，必须依赖系统边界来划分可逆/不可逆。dsh 的 `ctx.effect` + fiber 生命周期是 GRC「Loader = Generator」原则在运行时域的具体化——effect 注册同时产生 Δ 与 Δ⁻¹，在运行时坐标系上叠加 delta。

**两者互补的是形式化对象，不是理论框架**：
- Nop 的差量代数证明（结构域 `⊗` 结合律）+ dsh 的 Theorem 61/63/66/73（运行时域定理）= GRC 在两个域的完整形式化图景
- Nop 若要扩展到运行时可逆（如 plugin 机制），直接套用 dsh 的逆的代数（twisted composition）与系统边界即可——这就是 GRC 模式跨域复用的体现
- dsh 若要把配置层从整段覆盖推到字段级，直接引入 Nop 的 `x-extends` / `x-diff` 即可——这就是 GRC 模式在结构域已被验证的工具

**对 Nop 而言**，dsh 论文最大的借鉴价值是：**它补上了 GRC 在运行时域的定理级形式化**——给出了逆的代数（twisted composition）、逆的复合条件（pairwise independence）、以及最重要的概念工具"系统边界"。Nop 若通过 plugin 机制实现运行时可逆，面临的正是论文 [1, §6] 中讨论的问题（边界划分、依赖声明与解析、卸载顺序保证），而 dsh 已给出答案。**对 dsh 而言**，Nop 的差量代数是它在配置层缺失的那一块——直接引入即可得到 GRC 结构域的形式化保证。

**更深一层的联系——GRC「主动构造 Delta 结构空间」原则（判据 #2）**：
GRC 主论文 §B.1.3 把这一原则列为 GRC 的核心创新——从"被动处理源码变更"转向"主动设计承载变化的优良坐标结构空间"。Nop 与 dsh 都遵循这一原则，只是设计对象不同：
- Nop 主动设计结构域坐标：XDef / XDSL 元模型 + `xdef:key-attr` 唯一属性 + x:extends 算子 + S-N-V 加载流程
- dsh 主动设计运行时域坐标：fiber / scope / realm / service key / event name + 5 类 dispatch mode + ctx.effect 算子 + committed view / accumulator

两者都是"主动设计"而非"被动接受"——这就是它们能在各自域内支持精细差量合并与可逆操作的共同理论根源。详见姊妹篇 `2026-08-17-grc-dsh-theory-mapping.md` §三 映射表的"主动空间设计"行与 §七 的展开。

### 5.4 插件若支持 delta 定制，原则上仍要回到结构空间的差量代数

那么一个自然的问题：如果 dsh 的插件本身要支持 delta 定制（不只是整段覆盖），是否原则上仍然要采用 Nop 结构空间的 delta 方案？**答案是肯定的，而且这几乎是逻辑必然。**

原因在于：差量合并要求被修改的对象拥有**结构坐标系**——否则无法定位"在哪一点上施加差量"。dsh 的插件内部是命令式 TypeScript 代码（`ctx.on`/`ctx.provide` 在 apply 回调中执行），命令式代码的"结构"是控制流与闭包，没有稳定可寻址的坐标。这正是 dsh 的 patch 只能停留在 entry id 级的根本原因：**插件内部没有坐标，所以插件内部没有差量**。

要让插件内部支持 delta 定制，只有两条路：

1. **插件内部声明式化**：把插件的贡献（注册什么服务、监听什么事件、提供什么资源）表达为 DSL 结构（如 cordis.yml 式声明 + 更深层的 schema），建立字段级坐标，然后就可以用 Nop 的 `x-extends` 那套机制做任意深度的增删改。这正是 Nop 的路线：DSL 森林 + 领域坐标系 + 差量代数。dsh 论文的 §5.2 其实隐约朝这个方向看了一眼（declarative configuration），但止步于 entry 级——插件内部仍然是命令式的。
2. **运行时坐标化的差量**：在运行时结构空间上定义差量代数（对服务 key、事件名、realm 等运行时坐标施加增删改）。但这与 dsh 的累加性设计冲突——dsh 的"单写者 + 累加 + 无删除"正是为了避免跨插件的负元素操作。要做运行时的负元素，就必须回答论文回避的问题：谁能删除谁的贡献？删除是否满足结合律？这正是结构空间差量代数在运行时空间的翻版，比结构空间更难（运行时坐标是动态的、有状态的）。

所以结论是：**插件级 delta 定制本质上绕不开结构空间的差量代数**。Nop 通过"插件内部也是 DSL"绕过了"命令式插件如何定制"的难题——因为 Nop 的插件（Delta 包）本身就是结构，不是代码。dsh 的插件是代码，所以它的定制只能停留在"代码的入口"（entry id 整段覆盖）。**这是架构选择的分水岭：插件是结构（可定制）还是代码（只能替换），决定了定制能力的上限。**

### 5.5 被动 loader 与主动组装：时间性的对偶

最后一个问题涉及两种完全不同的"变化处理"机制：

- **Nop 的结构空间合成**：`FinalModel = Loader(Delta ⊕ Base)`。合成是**被动**的——`Delta Loader`（ResourceComponentManager）懒加载、记录依赖时间戳、节流检查、失效后重新执行完整合并、生成全新模型实例。合成**只影响最终结果**：中间状态无关紧要，因为合并是纯函数式的（满足结合律），随时可以从头重放；所有复杂逻辑（依赖计算、合并、缓存、失效）都收敛在 loader 内部，业务代码只面对加载完成的最终模型。
- **dsh 的运行时组装**：依赖计算是**主动**的——每个 fiber 主动解析 target view、`notify` 主动通知依赖者、`refresh` 主动触发 reload/unload、惯性机制保证转换原子完成。中间状态至关重要（LOADING/UNLOADING、committed view、逆的执行顺序），每一次 co-effect 变化都在运行时空时实重组。

这两者确实构成**对偶关系**，可以精确表述为：**时间性在哪个空间显式化**。

- Nop 在结构空间**消除时间性**：合并是纯函数，结果与执行顺序无关（结合律），中间状态可丢弃。时间只在"何时触发重新加载"这一点上存在（loader 的节流检查），而合成本身是无时间的。
- dsh 在运行时空间**显式管理时间性**：逆的执行顺序（LIFO）、依赖的激活顺序（先依赖后提供者）、转换的原子性（惯性）——时间的每一个细节都是要形式化保证的（Theorem 61/63/66 全部是关于顺序与终止的定理）。

用物理图像说：Nop 的 loader 是"状态无关的重计算"（像在势能面上重新找最小值，路径无关）；dsh 的运行时是"状态相关的演化"（像动力学轨迹，路径即历史）。可逆计算理论中，Nop 的做法对应"变换可逆性"（合并 ↔ 拆分，`x-extends` ↔ `x-diff`，双向可重放），dsh 的做法对应"过程可逆性"（沿轨迹逆行，逆序列按序执行）。

对偶的深层根源在于**依赖信息的所在空间不同**：

- Nop 的依赖关系定义在结构空间（delta 链、import、x-extends 引用），是**静态可知**的——loader 可以在任意时刻重放合成，因为依赖图不随运行而改变。
- dsh 的依赖关系定义在运行时（服务注册是 effect 的副作用），是**动态生成**的——依赖图本身就是运行时状态的一部分，必须实时计算，必须响应式（reactive coeffects 正是为此而生）。

因此"被动 loader"与"主动组装"不是任意的设计选择，而是各自空间的本性决定的：**结构空间的依赖静态可知 → 可以被动、可以重放、可以忽略中间状态；运行时空间的依赖动态生成 → 必须主动、必须实时、必须管理中间状态**。而如果插件内部声明式化（5.4），插件内部的依赖就从运行时提前到结构空间——插件的"被动 loader"部分也就随之出现了：声明式插件的组装可以预先计算，运行时只负责激活与卸载。这正是两者融合的方向：**结构空间用差量代数做被动合成（Nop 已证明可行），运行时结构空间用可逆 effect 做主动管理（dsh 已形式化）**。

### 5.6 registration 作为 generator：在运行时结构坐标处展开 delta

把可逆计算的核心公式与 dsh 的注册机制对照，会发现一个精确的对应：

```
App = Delta x-extends Generator<DSL>        （Nop，结构空间）
RuntimeStructure = ⊕_i Register_i           （dsh，运行时结构空间）
```

这里用 ⊕ 而不是 Σ，是为了强调它不是普通求和：**每次 registration 都在某个运行时结构坐标上叠加一个带逆的 delta**，卸载时按 LIFO 把该 delta 连同其逆一起撤下；叠加的代数就是 effect 的 twisted composition（[1, §3.1]）。

在 dsh 中，**一次 registration 就是一个作用于运行时结构坐标系的小型 generator**：它把"配置 + 代码"展开为一组 delta，每个 delta 落在某个结构坐标（服务 key、事件名、realm）上。`ctx.provide(name, value)` 是在"服务坐标 (name, realm)"处加入 delta；`ctx.on(event, listener)` 是在"事件坐标 event"处加入 delta。运行时结构坐标系上的全部结构，就是所有这些注册 delta 叠加的结果。

由此可以想象一种更纯粹的形态：**registration 可以不发生任何实际注册动作，仅仅返回一个"运行时修改要求"（delta 描述），然后由单独的 delta override 机制完成运行时结构修改。** 这等价于把 generator（生成 delta）与应用 delta 分离——先生成 delta 列表（不落地），再统一应用（落地）。在可逆计算的框架里，这完全成立：dsh 论文的 effect 模型（Γ → Γ × (Γ → Γ)）本质上就是"返回修改要求 + 返回逆"，应用的时机（立即或延后）并不影响其数学结构。

那么，"一边生成 delta，一边应用 delta"（dsh 当前的做法）与"先生成后应用"（分离做法）的区别是什么？**区别在于对"时间静止"的要求。** 如果注册动作在展开 delta 的同时就地应用（execute 立即运行、disposers 就地收集，见 fiber.ts），那么要保证"应用之后可恢复到未应用状态"，就必须保证在该 registration 执行期间没有其他变化发生——否则逆函数所捕获的"应用时状态"可能已被并发修改污染，恢复时无法回到原始状态。这就是为什么 dsh 的 async effect iterator 在每一步都检查 `runner.epoch !== oldEpoch`（依赖一旦变化立即中止展开），以及 inertia 机制保证一次转换原子完成——**这两者合起来，正是"时间静止"的工程化实现：在单个 registration 的展开窗口内冻结相关坐标的变化。**

用户提出了一个关键论断："因为是一个原子动作，所以分成两步还是一步执行是没有关系的。" 这在理论上完全正确：**如果 registration 的"生成 delta + 应用 delta"是一个原子动作（期间无并发变化），那么一步执行（生成即应用）与两步执行（先返回修改要求、后统一应用）在观测上等价**——原子性抹平了执行方式的差别。dsh 选择了一步执行 + 局部时间静止（epoch 检查 + inertia），Nop 选择了编译期合并（天然时间静止，因为根本没有运行并发）。两种选择殊途同归，都是在保证"应用窗口内无干扰"。

### 5.7 可逆性与响应式是正交的两个维度

还有一个重要的概念澄清：**动态检查依赖、主动触发激活/停用，这个操作与可逆性本身无关。**

从 dsh 的实现可以看得很清楚：`_refresh`（reactive 依赖计算：遍历 inject、拼接 epoch）与 `_unload`（revertible 卸载：反序执行 disposables）是**相互独立的方法**。reactive 机制只负责"何时触发"——依赖变化时计算新 epoch、调用 `_setEpoch`、启动转换；revertible 机制负责"如何执行"——转换中按 LIFO 顺序执行逆。**即便完全没有响应式机制，可逆性依然成立**：手工调用 `fiber.dispose()` 同样会反序执行全部逆，卸载效果一致。响应式只是给"触发"加了一个自动化的策略。

那么自动触发（reactive coeffects）的价值是什么？**它保证的是运行时的有效性（validity），而不是可逆性（revertibility）**。可逆性保证"撤得回来"，响应式保证"不会跑到无效的运行时状态空间"——当某个依赖失效时，依赖它的 fiber 若继续运行，就会读取不存在的服务、调用已失效的绑定，进入一个"结构上无效"的状态。自动触发在依赖失效的瞬间主动卸载依赖者，确保任何运行中的 fiber 都处于依赖满足的合法状态（论文 [1, Theorem 63] 的 ordering 保证：提供者晚于依赖者卸载）。这正是"运行时有效性"的含义：**可逆性是能力（能否撤销），响应式是治理（何时撤销）；能力保证撤得回来，治理保证不该跑的时候不跑。**

这也解释了为什么 Nop 不需要响应式而 dsh 需要：Nop 的结构空间是静态的，合成结果要么合法要么编译失败，不存在"运行中突然失效"的中间态；而 dsh 的运行时结构空间是动态的，fiber 持续运行在依赖图上，依赖消失是一个真实且高频的事件，必须主动治理。**自动触发让 dsh 可以在可逆性保证之上，额外获得"任何时刻都处于有效状态"的运行时安全性。**

### 5.8 注册机制前提到结构空间：Nop 的完整实现

前面几节反复出现一个方向：把 dsh 的运行时注册机制"前提到结构空间"。用户指出，这个方向在 Nop 中**已经是完整实现的现实**——参考 `xbiz.xdef`、`xwf.xdef`、`task.xdef` 三个元模型就能看到全部机制。

**xbiz.xdef（业务模型）**：action/mutation/query/subscription 全部是结构空间声明（`<actions xdef:key-attr="name">`），每个 action 是一个结构节点，以 `name` 为坐标。业务方法的"注册"根本不是运行时操作——它就是写一个 `<action name="xxx"><source>...</source></action>` 结构。多个 biz 模型通过 `x:extends`/`x:override` 做 delta 合并（如继承 CrudBizModel 的所有方法），编译期完成组合，运行时只是加载合并后的模型。

**xwf.xdef（工作流模型）**：`<steps xdef:key-attr="name">`、`<listeners xdef:key-attr="id">`、`<actions xdef:key-attr="name">`——工作流的每一步、每个监听器、每个动作都是结构空间中的坐标节点。工作流定义本身就是结构，step 之间的依赖（`when-steps`、`ref-actions`）是结构空间的关系，不是运行时注册。

**task.xdef（任务流模型）**：`<steps xdef:key-attr="name">` 支持任意嵌套（simple/step/xpl/script/sequential/graph），step 以 name 为坐标，全部是声明式结构。

这三个模型的共同点是：**"注册什么"的信息全部在结构空间以 DSL 表达，delta 合并（x-extends）在编译期/加载期完成组合，运行时只是加载展开后的模型并解释执行。** 展开动作（`x:gen-extends` 生成 + delta 合并）收敛在 `ResourceComponentManager`（模型加载器）中——它缓存模型、按模型类型分派 loader、执行变换，业务代码面对的是加载完成的最终模型。这正是用户所说的"完全将注册等机制展开为结构空间中的 DSL，然后通过编译期 delta 进行组合，运行时应用这些 delta，展开可以继续放到 loader 这个模型加载器中"。

**把这一图景与 dsh 对照，可以看到三种"注册"策略的谱系**：

| 策略 | 注册的位置 | delta 的位置 | 代表 |
|------|-----------|-------------|------|
| 运行时注册 | 运行时结构空间（命令式） | 运行时 effect 逆跟踪 | dsh |
| 声明式注册 + 运行时组装 | 结构空间（entry 级） | 配置层整段覆盖 + 运行时激活 | dsh 的 cordis.yml |
| 声明式注册 + 编译期展开 | 结构空间（字段/节点级 DSL） | 结构空间差量代数（x-extends/x-diff） | Nop（xbiz/xwf/task） |

用户的核心洞察是：**第三种策略原则上可以承载前两种的全部能力**——因为"注册什么"一旦成为结构，delta 定制（组合、撤销、逆向提取）就都成为结构空间运算，而运行时只需应用展开结果。dsh 之所以停留在第一种（命令式运行时注册），是因为它的插件内部没有结构坐标（§5.4）；而 Nop 之所以能完全前提到结构空间，是因为它的"插件"（biz 模型、工作流定义、任务流定义）本身就是 DSL 结构。

**但反过来也要看到 dsh 对 Nop 的补充价值**：Nop 的"编译期展开"假设结构在加载后基本不变（运行中变化靠重新加载模型实现）；dsh 证明了如果结构需要**高频动态变化**（自修改 harness 场景），运行时结构空间的可逆机制（逆跟踪 + 响应式触发）是必要的补充。理想形态是两者的结合：**结构空间负责"注册什么"的差量代数（Nop 已证明），运行时结构空间负责"结构如何激活/卸载"的可逆管理（dsh 已形式化）——展开动作放在 loader 中（编译期），激活/卸载的逆跟踪放在运行时。**

### 5.9 运行时注册为什么仍是必须的：agent 级 scope 差异

那么，是否一切注册都可以前提到结构空间？**不能。** 存在一类注册，其内容依赖运行时才知道的信息——最典型的例子是 dsh 的 agent 级 scope。

**机制**：dsh 为每个 agent 通过 `createScope` mint 一个独立的 scope 上下文（`agent.ctx`），它继承插件的依赖 API，并拥有该 scope 内的一切注册。同一插件代码（如 tools 插件）在不同 agent 的 scope 中执行时，注册的具体内容可能完全不同——`tools.presentAs(mode)` 只对当前 scope 生效（一个 scope 一种 presentation 模式）、`tools.restrict()` 只限制当前 agent 的工具集、`tools.guard()` 只给当前 agent 增加执行守卫。**注册什么，取决于这个 agent 是谁、它的配置是什么——这些信息在编译期/加载期并不存在，只在运行期创建 agent 时才出现。**

scope 链的解析顺序是 **agent → preset → global**（nearest shadowing farthest，近者遮蔽远者）。`agent.ctx` 由 `createScope` 以 session id 为 key mint 出来；agent-presets 则更进一步：每个 preset 的 `agent.cordis.yml` 在进程级只挂载一次（standing scope），每个会话的 agent scope 通过 scope 父链加入该挂载点。因此 preset 贡献的工具、提示区块和投影单元只存在一份，却覆盖所有加入该 preset 的 agent（见 `dsh/packages/preset/agent-presets/README.md`）。

这正是"全局注册有困难"的根源：如果把所有 agent 的注册都提前到结构空间，就无法表达"同一个插件代码在不同 agent 下注册不同内容"的差异——除非把这种差异也变成结构（每个 agent 一个 DSL 配置），但这本质上就是把 agent 配置前提到结构空间，而 agent 配置本身又是运行时数据（可能来自持久化 session、用户设置、动态创建）。

**用户提出的折中方案**：把动态条件作为一个代码段，执行注册前先执行动态判断条件——即"条件式注册"（conditional registration）：注册动作本身仍是一个结构（代码），但它带有一个运行期求值的守卫，守卫为真才实际注册。这与 dsh 现成的机制可以对照：

| 方案 | 判断时机 | 特点 | dsh 现状 |
|------|---------|------|---------|
| 声明式选择 | 加载期 | 结构空间解决差异（每个 agent 选不同 preset/配置） | agent-presets：进程级 standing scope 挂载，agent scope 经父链加入 |
| 注册前条件 | 注册时 | 动态条件代码段，真才注册 | 部分（scope 内按配置注册） |
| 注册后条件 | 执行时 | 先注册，运行时 guard 裁决 | `tools.guard()`：已注册但按条件放行/拒绝 |

三者其实是对"注册内容何时确定"的三个不同回答：**结构空间确定（声明式）、注册时刻确定（条件注册）、执行时刻确定（guard）**。越靠右，动态性越强，但可逆性/可定制性越弱——因为结构空间的差量代数无法作用在"执行时刻才确定"的内容上。

**对可逆计算的意义**：这三者并非互斥，而是分层共存。静态可定的部分（插件集、服务定义、事件监听声明）前提到结构空间，用差量代数管理；动态可定的部分（agent 级 guard、presentation、restriction）保留在运行时，用可逆 effect + scope 隔离管理。**结构空间与运行时空间的分界线，不是由"能不能前提前"的偏好决定，而是由"注册内容何时可知"的认识论边界决定**——这是一个非常实用的判据：凡是在结构空间中可确定的内容，就提到结构空间；凡是在运行期才可确定的内容，就留在运行时并用可逆机制管理。dsh 的 agent scope 机制（每个 agent 一个可逆的注册上下文）正是这一判据的工程化体现：它让"运行时才可知的注册差异"拥有了一个可逆、可隔离、可撤销的载体。

## 六、回到"可逆"的哲学：分离到可逆，控制熵增的位置

最后，把 dsh 放回可逆计算的理论框架中，可以看到它印证了可逆计算的几条核心命题：

1. **关注点分离的判据是"分离到可逆"**。传统的"高内聚低耦合"是模糊的审美判断，可逆计算给出了可操作标准：**任何增加的功能都应该有配对的逆向取消机制，任何输入系统的信息都应该存在自动反向提取的方法**。dsh 的 `ctx.effect` 与 fiber 结构正是这一标准的实现——每个功能的增加（effect）都当场返回撤销机制（dispose），每个信息输入（co-effect 绑定）都被记录在 fiber 的表里并随卸载自动清除。分离得好不好，看它能不能被完整拿走。

2. **可逆性与熵的关系体现物理世界的本质约束**。熵增不可回避，但熵增的位置可以被选择。dsh 的选择是：把可逆性建立在机制结构上（一切 effect 带逆），把不可逆性显式收编（系统边界、保留、补偿），把"可能造成污染的信息"限制在 scope 内。这与可逆计算"将熵增集中在差量 Δ 中"的主张同构——dsh 的 Δ 就是"可被卸载的插件"。

3. **可逆本质上是保持信息可追溯性与可分离性**。accumulator 是追溯结构（记录了一切该撤销什么），fiber 是分离单元（定义了撤销的边界），co-effect 声明是信息组合的受控接口。三者合一，构成一个"信息可追溯、可分离"的系统——这比"可逆运行"要深刻得多，也现实得多。

4. **局部可逆的复合性是最重要的工程性质**。dsh 的元理论（[1, Theorem 61/63/66]）证明：当每个组件在受限 scope 内可逆，且 scope 之间通过声明式接口隔离、通过无环依赖排序时，系统的整体卸载是精确、有序、必然终止的。可逆性因此从"个例的优雅"上升为"系统的性质"。

## 七、结论

DeepSeek Harness 的架构之所以独特，不在于它宣称"副作用可以完全撤销"，而在于它给出了一套**关于可逆性的完整且诚实的工程化方案**。经过对 dsh 源码的逐一核实（`ctx.effect` 全部 244 处调用、非测试代码 205 处；patch 机制；realm 隔离；事件分发模式；agent scope），可以给出如下精确结论：

**dsh 的可逆作用于运行时结构空间，而不是业务数据空间。** 注册、监听、资源获取、服务发布是 `ctx.effect` 的集中所在；session log 是 append-only 不可变事件流，明确不可逆。这印证了"可逆是对运行时空间的一种规划"的判断。

**dsh 的结构坐标是 entry id，粒度是整段 config 覆盖而非字段级。** 静态 patch 文件层（`applyEntryPatches`）是粗粒度替换，不是 Nop 意义上的差量代数；同时要看到运行时 loader 层（`Entry.update`）有 per-field reconciliation 的雏形——两者不在一个层面，不能混为一谈。

**fiber 一旦展开即保持结构固定，插件面对的是稳定快照。** "运行时结构空间"确实如用户所料，是"源码结构在运行时的实例化展开 + 基本稳定的结构"。

**运行时坐标有明确的顺序语义。** 服务坐标单写多读（同 realm 互斥，无顺序问题）；事件坐标多写（顺序 = 注册顺序，语义由 dispatch mode 声明）——顺序语义由坐标声明决定，运行时处理在插件处理之外。

**可逆性与响应式正交，注册即 generator。** 可逆性是能力（撤得回来），响应式是治理（依赖失效时自动卸载、保证运行有效性）；一次 registration 是运行时结构坐标上的一个小型 generator，"生成 + 应用"的原子性由 epoch 检查 + inertia 实现（运行时的时间静止）。

**系统边界之外显式不可逆，局部可逆在 scope 约束下复合为整体可逆。** 边界外行为（日志、网络、外部世界）用保留与补偿管理；在 confinement + 供应不相交（per realm）+ 依赖无环的前提下，dsh 的元理论保证了卸载的精确、有序与终止。

最后回到用户的核心问题：**dsh 的文章有没有理清结构层与运行时层的关系？** 分两层回答：

1. **论文层面**：没有完全理清。论文 [1, §5.2] 描述了 declarative configuration 与 reconciliation，但没有把配置层 override 形式化为差量代数，也没有统一配置坐标（entry id）与运行时 effect 坐标的概念关系。
2. **实现层面**：dsh 的静态 patch 层（`applyEntryPatches`）是整段 config 覆盖 + insert；运行时 loader 层（`Entry.update`）有 per-field 雏形，但插件内部仍是命令式代码，没有内部结构坐标。因此 dsh 的结构层仍未达到可逆计算理论所要求的"结构坐标系 + 差量代数"标准。

社区对"副作用怎么可能可逆"的怀疑，其实是对"全量可逆"的怀疑——而这个怀疑本身是成立的：没有任何系统能做到全量可逆。但可逆计算与 dsh 的正确主张是：**可逆并不要求全量，可逆是分层的、有边界的、在 scope 内复合的**。把"可逆"从"逆向运行"的望文生义中解放出来，理解它是信息可追溯性与可分离性的系统化表达，才能真正读懂 dsh 架构设计的精髓。对 Nop 而言，下一步通过 plugin 机制实现运行时空间部分可逆时，最值得带过去的资产，正是它在结构空间深耕多年的差量代数——那是 dsh 论文留白的地方。

## 术语表

按首次出现顺序排列，不熟悉 Cordis 论文的读者可在此查阅。

| 术语 | 含义 |
|---|---|
| **effect** | 作用于上下文的一次操作，可能携带副作用；本文特指带逆函数的"可逆 effect" |
| **可逆 effect / revertible effect** | 形式 `Γ → Γ × (Γ → Γ)`：一次 effect 同时产生新状态与逆函数 |
| **fiber** | 组件在运行时的一次实例化，拥有独立的生命周期状态、accumulator、committed view |
| **coeffect** | 组件对外部依赖的声明（specification d）；与 effect 相对，前者是消费、后者是产出 |
| **coeffect table** | fiber 内 `fiber.ctx[@@store]`，记录本 fiber 写入的所有绑定 |
| **accumulator** | fiber 内累积的逆函数复合（`fiber.dispose`），卸载时按 LIFO 反序执行 |
| **committed view** | fiber 激活时所依赖的解析结果（`fiber.committed`） |
| **provision** | fiber 向上下文发布的服务（`ctx.provide`），同 realm 内互斥 |
| **inject** | fiber 声明依赖的方式（`ctx.inject` 或 inject 字段），对应 coeffect 规范 |
| **realm** | 隔离域，决定同 key 互斥性的范围；默认与 entry id 绑定，可被 `isolate` 改写 |
| **scope** | 运行时继承上下文链；agent scope、standing scope、global scope 形成 `agent → preset → global` 链 |
| **standing scope** | 进程级只挂载一次的 scope（agent-presets 用此机制） |
| **inertia** | fiber 转换一旦开始就运行到完成，避免中间状态风暴 |
| **epoch** | fiber.uid + 依赖指纹的拼接，用于检测依赖变化（`runner.epoch !== oldEpoch`） |
| **acquisition** | 边界内操作（open 描述符、malloc 内存），可逆 |
| **emission** | 跨界操作（写文件、send 数据报），不可逆，仅靠保留/补偿 |
| **withhold** | emission 的两种管理策略之一：推迟发出直到状态确定持久化 |
| **compensation** | emission 的两种管理策略之一：业务层反向操作（Saga 模式） |
| **disposable** | fiber 内任一 effect 注册的逆函数封装；accumulator 由 disposable 链构成 |
| **armed** | disposable 的执行守卫标志，防止逆被执行两次 |
| **guard** | 运行时对 effect 的拦截（`tools.guard()`），可放行/拒绝；属注册后条件 |
| **interception** | 改访问方式不改绑定值的派生机制（`intercept` 字段），不需要逆 |
| **L-Begin / L-Unload** | fiber 生命周期状态（Algorithm 5）；依赖不满足时停在 L-Begin 等待 |
| **disjoint provisions** | 论文 Definition 43：同一 key 至多由一个 fiber 提供（per realm 互斥） |
| **confinement** | 论文 Definition 48：限制 fiber 的 effect 只能写自己的 coeffect 表 |
| **pairwise independence** | 元理论的前提条件之一：fiber 之间 effect 互不干扰，保证 accumulator 只撤回自己的贡献 |
| **观测等价 ≃** | 论文 Definition 33：两个状态在所有观察者下等价；可逆恢复的目标不是字面相同而是 ≃ |

## 参考文献

1. Yifan Shi, Wei Zhang, Tianyi Cui. *A Programming Paradigm for Spatiotemporal Composability*. Preprint, 2026. 本仓库副本：`ai-dev/references/cordis-paper/spatiotemporal-composability.md`
2. `docs/theory/what-does-reversible-mean.md` — 可逆计算中的可逆到底指什么
3. `docs/theory/methodology-source.md` — 可逆计算的方法论来源（熵增原理与狄拉克图景）
4. `docs/theory/reversible-computation-runtime-evolution.md` — 可逆计算如何赋能运行时演化
5. `docs/theory/discussion-about-reversible-computation.md` — 关于可逆计算的讨论（逆元、坐标系、结构空间；与硬件可逆计算机的区分）
6. `docs/theory/explanation-of-delta.md` — 差量概念辨析（不同结构空间的差量）

## 附录：源码核实方法与关键文件对应

本文对 dsh 的结论基于以下核实方式，读者可自行复算：

- dsh 仓库本地路径：`~/ai/deepseek-harness`（统计日期 2026-08-17，工作区当前状态）
- `ctx.effect` 统计：

```bash
# 全部 .ts/.tsx 文件
grep -rn "ctx\.effect" --include='*.ts' --include='*.tsx' . | wc -l
# 244

# 排除 test/tests 目录
grep -rn "ctx\.effect" --include='*.ts' --include='*.tsx' . | grep -v '/tests/' | grep -v '/test/' | wc -l
# 205
```

关键源码文件与文章结论的对应关系：

| 文章结论 | 源码位置 |
|---|---|
| 服务发布与同 realm 互斥（`service "..." has been registered`） | `dsh/vendor/cordis/src/reflect.ts` |
| 事件监听注册、五类 dispatch mode | `dsh/vendor/cordis/src/events.ts` |
| fiber 生命周期、epoch 检查、inertia | `dsh/vendor/cordis/src/fiber.ts` |
| 静态 patch 文件层（`applyEntryPatches`） | `dsh/vendor/include/src/index.ts` |
| 运行时 loader reconciliation（`Entry.update`） | `dsh/vendor/loader/src/config/entry.ts` |
| realm 隔离（`isolate: true` → `#id`，`isolate: <label>` → `@label`） | `dsh/vendor/loader/src/config/isolate.ts` |
| agent scope（`createScope`） | `dsh/packages/client/runtime/src/client/agents/scope.ts` |
| `tools.presentAs` / `tools.restrict` / `tools.guard` | `dsh/packages/core/tools/src/index.ts` |
| agent-presets（standing scope 挂载） | `dsh/packages/preset/agent-presets/README.md` |
| patch 文档引文 | `dsh/packages/bundle/base/README.md`、`dsh/packages/boot/app-boot/README.md` |
| Nop 字段级坐标（`xdef:key-attr`） | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/biz/xbiz.xdef`、`.../schema/wf/wf.xdef`、`.../schema/task/task.xdef` |
