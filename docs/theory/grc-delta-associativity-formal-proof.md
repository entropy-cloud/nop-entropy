# GRC 抽象差量演算的条件化结合律证明与 Nop/XLang 语义边界

## 摘要

本文是《广义可逆计算：面向软件构造与演化的语义坐标系和差量代数》的伴随技术报告，目标是给出 GRC 差量预合并命题的可审查形式化证明。本文不证明任意文本补丁（patch）、任意业务补丁、任意真实工程实现都无条件满足结合律；本文证明的是三类明确定义的抽象承载空间（carrier）：语义坐标上的有限潜在偏函数、逐坐标局部操作幺半群，以及潜在树状态空间上的确定性端函数。Nop/XLang 的实际 `x:extends` 语义必须先映射到其中某个承载空间，并满足本文列出的证明义务，才可继承相应定理。

核心结论是：在稳定语义坐标、有限潜在表达、确定性线性化、合并链中间不做有损投影、最终统一规范化与验证等前提下，差量链只改变括号而不改变顺序时，其潜在结果或端函数 denotation 不变。因此，差量可以在不依赖具体基线细节的情况下预合并；若最终验证接受，得到同一个可观测模型；若最终验证拒绝，得到同一个规范化错误集合。

关键词：广义可逆计算，差量代数，结合律，语义坐标，潜在模型，tombstone，端函数语义，XLang，Nop

## 0. 论文定位与贡献

本文采用与 FOP/DOP/Feature Algebra 类似的形式化标准：先定义一个核心制品/差量演算（artifact/delta calculus），再证明该演算的组合性质。它不是对当前 Nop 代码库中每一个 `x:override` 分支、每一个排序细节、每一种错误对象字段给出机械化证明。工程实现若要引用本文定理，需要完成第 6.11 节覆盖矩阵中的实现符合性证明。

本文贡献如下：

1. 定义语义坐标上的有限潜在模型空间，并证明核心右覆盖合并 $\oplus$ 构成幺半群。
2. 形式化删除标记与最终投影，说明结合律成立于潜在空间，而不是每步投影后的物理模型空间。
3. 给出理想化坐标 tombstone carrier 下树形 `x:extends` 核心 merge/remove 语义到偏函数合并的编码关系。
4. 给出逐坐标局部操作幺半群的提升定理，并补充操作 carrier 对值 carrier 的作用律。
5. 给出 `merge/remove/replace`、ancestor/descendant 重叠等混合树操作的端函数语义、有限表达式规范化和商代数结合律。
6. 明确 Nop/XLang 实际操作与本文抽象 carrier 的对应边界，区分已证明结论、证明义务和未覆盖语义。

## 0.1 全局假设

为避免后文重复，本文所有正式定理均默认以下假设。若某节需要更强条件，会在该节局部声明。

| 编号 | 假设 |
|---|---|
| A1 | 所有模型和差量先被确定性线性化为一个有限序列；结合律只改变括号，不改变该线性顺序。 |
| A2 | 参与同一条证明链的对象位于同一个 carrier：坐标 tombstone carrier、局部操作 carrier 或树状态端函数 carrier，三者不得混用。 |
| A3 | 语义坐标由 DSL/XDef 元模型或等价 schema 提供，stable key 在每个父路径内确定且单射；物理数组下标不是稳定身份坐标。 |
| A4 | 所有潜在模型、差量表达式和 children 映射都是有限对象。 |
| A5 | 生成器、排序、规范化、投影和验证在给定输入下确定；随机数、时间、外部 IO 或全局可变状态必须显式纳入输入，否则不在定理范围内。 |
| A6 | 合并链在潜在空间中完成；中间步骤不得执行会丢弃 tombstone、顺序约束、virtual node 或其他影响后续语义证据的有损投影。 |
| A7 | 最终错误结果按错误码、规范坐标和规范参数形成 `ErrorSet`；不包含对象地址、调用栈文本、遍历偶然顺序、未规范化 source location 等非语义字段。 |
| A8 | 若声称得到可序列化 delta artifact，必须证明差量语言对预合并闭包；任意 $End(S)$ 函数复合只给出语义组合，不自动给出可保存的 DSL 差量文本。 |
| A9 | 所有纳入预合并定理的操作组合必须在所选 carrier 中是全函数并保持闭包；实现级“禁止组合”或局部失败只有被建模为确定性 total error carrier 并证明结合律时才可进入定理，否则必须排除或推迟到最终统一验证。 |

## 0.2 记号表

| 记号 | 含义 |
|---|---|
| $C$ | 语义坐标集合，带自反、传递、反对称的前缀偏序 $\preceq$。 |
| $P$ | 坐标 tombstone carrier 中的有限 typed partial map 空间。 |
| $p,q,r$ | $P$ 中的潜在模型。后文若 $p$ 表示树路径，会显式说明为“路径 $p$”。 |
| $\bot_c$ | 坐标 $c$ 上的删除标记；省略下标时写作 $\bot$。 |
| `undef` | 偏函数在某坐标无定义，不同于删除标记 $\bot_c$。 |
| $\oplus$ | 坐标潜在模型上的右覆盖合并。 |
| $\otimes$ | 逐坐标局部操作 carrier 上的操作组合。 |
| $\odot$ | 差量表达式组合，方向为“先左后右”，denotation 为函数复合。 |
| $S_p$ | 树状态端函数 carrier 中路径 $p$ 处的潜在状态空间。 |
| $End(S)$ | $S\to S$ 的确定性端函数集合。 |
| $Pr,Norm,Validate,Final$ | 终端投影、规范化、验证和最终可观测结果函数。 |

下文会保留 $\bullet$ 作为非正式说明中的“当前选定结构合并算子”占位符；正式定理不再用单个 $\bullet$ 混合三种 carrier。

## 0.3 定理依赖索引

| 结论 | 依赖 | 说明 |
|---|---|---|
| 定理 1 核心结合律 | 第 2 节偏函数定义 | 证明 $(P,\oplus,\varnothing)$ 是幺半群。 |
| 定理 2 最终投影一致性 | 定理 1、$Pr$ 确定性 | 只允许最终投影，不允许中间有损投影。 |
| 引理 5.2 树编码等价 | 第 5.1 节编码、理想化 $MergeTree^P$ | 只覆盖坐标 tombstone carrier，不是当前 Nop 实现证明。 |
| 定理 3 树合并结合律 | 引理 5.2、定理 1 | 编码后潜在模型相等。 |
| 定理 6.1 局部操作提升 | 局部幺半群和作用律 | 支撑定理 4B。 |
| 引理 6.7.1-6.7.3 | 端函数 denotation、良基同步归纳 | 支撑定理 4C 和商代数。 |
| 定理 4A | 定理 1、推论 2 | 坐标 tombstone carrier 的预合并。 |
| 定理 4B | 定理 6.1 | 局部操作 carrier 的预合并与作用等价。 |
| 定理 4C | 函数复合结合律、引理 6.7.1-6.7.3 | 树状态端函数 carrier 的预合并。 |
| 第 6.11 节覆盖矩阵 | 非定理 | 记录 Nop/XLang 实现需要额外完成的证明义务。 |

## 一、证明范围

本文证明以下核心命题的条件化版本：

$$
(P_a \oplus P_b) \oplus P_c = P_a \oplus (P_b \oplus P_c)
$$

其中 $P_a,P_b,P_c$ 是定义在同一语义坐标系上的潜在模型或差量，$\oplus$ 是核心的右侧覆盖合并算子。对追加、环绕、顺序约束、replace/remove 等扩展语义，本文不把它们强行塞入 LWW 规约，而是给出两种严格处理方式：若扩展只发生在互不冲突的单坐标上，可用局部幺半群提升出的 $\otimes$；若存在混合操作或祖先/后代重叠作用域，则统一解释为潜在状态空间上的确定性端函数，用函数复合保证结合律。最终可观测模型由投影、规范化和验证阶段得到。结合律要求合并链先在潜在结构空间中完成，不能在每一步之后立刻丢弃删除标记或执行有损投影。

本文证明以下对象的结合律：

- 结构层合并，而不是运行时副作用。
- 潜在模型空间中的合并，而不是每步投影后的物理模型合并。
- 稳定语义坐标上的差量，而不是以行号、数组下标等脆弱坐标为基础的文本 patch。
- 确定性合并策略，包括覆盖、删除、递归树合并，以及可归约为局部幺半群或统一端函数 denotation 的追加、环绕、替换和有序约束收集。

本文不证明以下对象无条件满足结合律：

- Git 文本 patch 的三方合并。
- 依赖时钟、随机数、外部 IO 或全局可变状态的生成器。
- 每一步合并后立即执行业务校验并丢弃中间证据的流程。
- 以物理列表下标为唯一坐标的数组 patch。
- 并发编辑没有线性化顺序且未使用 CRDT/OT 等收敛合并结构的场景。

## 二、基础定义

### 2.1 语义坐标集合

令 $C$ 为一个可数集合，元素称为语义坐标。每个坐标标识 DSL 结构空间中的一个稳定语义位置，例如：

$$
/orm/entities/entity[@name='Order']/columns/column[@name='status']/@length
$$

坐标集合带有前缀关系 $\preceq$。本文要求 $\preceq$ 是自反、传递、反对称的偏序。若 $d \preceq c$，则 $d$ 是 $c$ 的祖先坐标；由于关系自反，每个坐标也是自身的祖先。例如，列节点坐标是该列属性坐标的祖先。

稳定坐标来自 DSL 元模型，而不是物理文本位置。对树中同级可重复节点，应由 XDef、DSL schema 或等价元模型显式指定 stable key，例如 `xdef:key-attr`、`xdef:unique-attr`、`x:key-attr`、`x:unique-attr`、`x:id` 等。`name`、`id` 或 `value` 只有在 schema 或通用 XNode fallback 规则把它们声明为身份字段时才是 stable key；特别是 `value` 不是无条件默认身份字段。若只能使用数组下标，则坐标会随插入和重排漂移，不属于本文证明的稳定坐标空间。

对树形结构，本文区分路径和坐标。令 $Path$ 为稳定 key 路径集合；对每个路径 $\pi\in Path$，存在一个节点根坐标 $root(\pi)\in C$。节点 tag、属性、文本和子节点会进一步编码为 $root(\pi)/\#node$、$root(\pi)/@attr$、$root(\pi)/\#text$ 和 $root(\pi\cdot k)$ 等坐标。删除一个子树时，tombstone 写在 $root(\pi)$ 上，而不是写在某个属性或文本坐标上。前缀关系要求：

$$
root(\pi) \preceq root(\pi)/\#node,
\quad root(\pi) \preceq root(\pi)/@attr,
\quad root(\pi) \preceq root(\pi\cdot k)
$$

并按传递闭包覆盖所有后代坐标。这样，$root(\pi)$ 上的 $\bot$ 会在投影时屏蔽该子树的节点存在、属性、文本和全部后代。

### 2.2 值域与删除标记

对每个坐标 $c \in C$，令 $V_c$ 为该坐标允许出现的值集合。对普通属性/文本坐标，$V_c$ 是对应类型的值集合；对节点根坐标 $root(\pi)$，$V_{root(\pi)}$ 可取一个节点存在摘要或只含单位值 $\mathsf{present}$，用于允许同一坐标上的 tombstone 被后续同根定义覆盖。引入一个不属于任何 $V_c$ 的特殊标记 $\bot_c$，表示删除该坐标处的信息。

为简化记号，下文统一写作 $\bot$。但类型上应理解为每个坐标有自己的删除标记。

定义扩展值域：

$$
A_c = V_c \cup \{\bot\}
$$

注意：$\bot$ 表示“定义为删除”，不同于 `undef`。`undef` 表示该潜在模型在某坐标上没有定义。

### 2.3 潜在模型

为避免不同坐标值域重叠造成类型歧义，先定义带标签的全局值域：

$$
\mathcal A=\{(c,a)\mid c\in C, a\in A_c\}
$$

潜在模型空间 $P$ 是所有满足类型约束的有限偏函数集合。即 $p\in P$ 当且仅当 $p$ 是一个有限偏函数：

$$
p: C \rightharpoonup \mathcal A
$$

且当 $p(c)$ 有定义时，必须存在唯一 $a\in A_c$ 使得 $p(c)=(c,a)$。为简化记号，后文把 $(c,a)$ 直接写为 $a$；所有公式都应按该 tagged value 解释。

记 $Dom(p)$ 为 $p$ 的定义域。若 $c \notin Dom(p)$，则 $p$ 在 $c$ 上没有表达任何信息。

在 GRC 的代数层，“基础模型”和“差量”没有本质类型区别。它们都是潜在模型。工程上称某个稀疏潜在模型为差量，只是因为它通常只定义少量坐标。

### 2.4 核心覆盖合并算子

定义核心二元合并算子 $\oplus$。对任意两个潜在模型 $p,q$，$p \oplus q$ 表示“先有 $p$，再叠加 $q$；若二者在同一坐标都有定义，右侧 $q$ 覆盖左侧 $p$”。形式化定义为：

$$
(p \oplus q)(c)=
\begin{cases}
q(c), & c \in Dom(q) \\
p(c), & c \notin Dom(q) \land c \in Dom(p) \\
\text{undef}, & c \notin Dom(p) \cup Dom(q)
\end{cases}
$$

这个定义等价于 Last-Write-Wins 的逐坐标覆盖，但作用对象是语义坐标上的结构化信息，而不是文本行。后文的 append、around、顺序约束等扩展语义不再使用这个 LWW 规约定义，而使用第六节的 $\otimes$ 或 $\odot$。

由定义可得：

$$
Dom(p \oplus q)=Dom(p) \cup Dom(q)
$$

空潜在模型 $\varnothing$ 满足 $Dom(\varnothing)=\varnothing$。

## 三、核心结合律证明

### 定理 1：$\oplus$ 满足结合律

对任意潜在模型 $p,q,r$，有：

$$
(p \oplus q) \oplus r = p \oplus (q \oplus r)
$$

#### 证明

两个潜在模型相等，当且仅当它们在每个坐标 $c \in C$ 上的取值都相等。因此只需证明对任意 $c \in C$，左右两侧取值相等。

固定任意坐标 $c$。按 $c$ 是否属于 $Dom(r)$、$Dom(q)$、$Dom(p)$ 分情况讨论。

第一种情况，$c \in Dom(r)$。

左侧：

$$
((p \oplus q) \oplus r)(c)=r(c)
$$

右侧中，由于 $c \in Dom(r)$，根据 $q \oplus r$ 的定义：

$$
(q \oplus r)(c)=r(c)
$$

且 $c \in Dom(q \oplus r)$，所以：

$$
(p \oplus (q \oplus r))(c)=(q \oplus r)(c)=r(c)
$$

左右相等。

第二种情况，$c \notin Dom(r)$ 且 $c \in Dom(q)$。

左侧：

$$
((p \oplus q) \oplus r)(c)=(p \oplus q)(c)=q(c)
$$

右侧中，由于 $c \notin Dom(r)$ 且 $c \in Dom(q)$，有：

$$
(q \oplus r)(c)=q(c)
$$

且 $c \in Dom(q \oplus r)$，所以：

$$
(p \oplus (q \oplus r))(c)=q(c)
$$

左右相等。

第三种情况，$c \notin Dom(r)$ 且 $c \notin Dom(q)$。

此时 $c \notin Dom(q \oplus r)$。

若 $c \in Dom(p)$，左侧为：

$$
((p \oplus q) \oplus r)(c)=(p \oplus q)(c)=p(c)
$$

右侧为：

$$
(p \oplus (q \oplus r))(c)=p(c)
$$

若 $c \notin Dom(p)$，左右两侧均为 `undef`。

三种情况覆盖所有可能，因此对任意 $c \in C$，左右两侧取值相等。故：

$$
(p \oplus q) \oplus r = p \oplus (q \oplus r)
$$

证毕。

### 推论 1：空潜在模型是单位元

对任意潜在模型 $p$，有：

$$
p \oplus \varnothing = p
$$

$$
\varnothing \oplus p = p
$$

证明直接来自 $Dom(\varnothing)=\varnothing$ 和 $\oplus$ 的定义。

### 推论 2：核心覆盖差量链存在唯一规约结果

给定有限序列 $p_1,p_2,\dots,p_n$，定义核心覆盖语义下的链式规约 $NF_{LWW}(p_1,\dots,p_n)$ 为：

$$
NF_{LWW}(p_1,\dots,p_n)(c)=p_k(c)
$$

其中 $k$ 是满足 $c \in Dom(p_k)$ 的最大下标。若不存在这样的 $k$，则结果为 `undef`。

则在核心覆盖语义下，任意括号化方式得到的结果都等于该 $NF_{LWW}$。

#### 证明

由定理 1 可知 $\oplus$ 满足结合律，因此任意括号化方式可通过有限次结合律变换互相转换。又由 $\oplus$ 的定义可知，对于每个坐标，最终取值必然来自最右侧有定义的潜在模型。故链式规约结果唯一。

这说明多个核心覆盖差量可以先独立预合并为一个差量，再统一作用于基础模型：

$$
(((P_0 \oplus \Delta_1) \oplus \Delta_2) \cdots \oplus \Delta_n)
=
P_0 \oplus NF_{LWW}(\Delta_1,\Delta_2,\dots,\Delta_n)
$$

## 四、删除语义与最终投影

### 4.1 为什么需要潜在模型

删除操作容易引起误解。一个差量可以表达“删除字段 C”，即使当前基础模型里没有字段 C。这个表达在潜在模型空间中是合法的，因为它只是定义了某个坐标的删除意图：

$$
p(c)=\bot
$$

它是否对应一个真实存在的字段，是投影到可观测模型时才处理的问题。

因此，GRC 的结构合并不是直接在“已经验证合法的最终模型”之间运算，而是在更大的潜在结构空间中运算。这个空间允许短暂存在业务上不完整、物理上不可直接执行、但代数上封闭的中间表达。

### 4.2 投影算子

定义投影算子 $Pr$，将潜在模型转化为可观测模型。

为便于书写，定义 $val(p(c))=a$ 当且仅当 $p(c)=(c,a)$。对潜在模型 $p$，定义删除闭包谓词：

$$
D^+(p)=\{c \in C \mid \exists d \in Dom(p), d \preceq c \land val(p(d))=\bot_d\}
$$

这里 $D^+(p)$ 可视为谓词集合，不要求实际枚举整棵无限坐标树。实际投影只会在 $Dom(p)$ 的有限定义域上产生输出，因此投影结果仍是有限表示。

投影结果为：

$$
Pr(p)(c)=
\begin{cases}
val(p(c)), & c \in Dom(p) \land c \notin D^+(p) \land val(p(c)) \in V_c \\
\text{undef}, & \text{otherwise}
\end{cases}
$$

含义是：如果某坐标或其祖先坐标在最终潜在模型中被标记为删除，则该坐标在可观测模型中不存在。

这里的 $Pr$ 首先是坐标级可观测偏函数投影。若最终输出需要真实树模型，还必须把 `present`、`#node` 等编码哨兵的过滤、从坐标重建树、well-formedness 检查和语法糖消解纳入确定性的 $Norm_P/Pr_P/Validate_P$ 链条；这些后处理可以拒绝不良输入，但不能在合并链中间丢弃会影响后续语义的证据。

### 定理 2：最终投影保持结合律的可观测结果

对任意潜在模型 $p,q,r$，有：

$$
Pr((p \oplus q) \oplus r)=Pr(p \oplus (q \oplus r))
$$

#### 证明

由定理 1 得：

$$
(p \oplus q) \oplus r = p \oplus (q \oplus r)
$$

等式两边是同一个潜在模型。对同一个潜在模型应用确定性函数 $Pr$，结果必然相同。因此定理成立。

### 4.3 不能在每一步之后做有损投影

需要强调，定理 2 证明的是“最终投影”的结合律可观测一致性，而不是“每一步投影后再继续合并”仍然等价。若把可观测模型重新嵌入潜在空间的确定性函数记作 $I:Obs\to P$，则以下更强式子一般不成立：

$$
Pr(I(Pr(p \oplus q)) \oplus r)=Pr(p \oplus q \oplus r)
$$

如果没有显式嵌入 $I$，表达式 $Pr(p\oplus q)\oplus r$ 本身就存在类型错误：$Pr$ 的结果是可观测模型，不一定仍是潜在模型。即使给定某个 $I$，等式仍可能失败，原因是 $Pr$ 可能丢弃删除标记和被删除子树等中间证据。

考虑如下坐标：

- $a=/A$
- $b=/A/B$

令基础模型 $p$ 定义 $b$，差量 $q$ 删除 $a$，差量 $r$ 只新增 $b$ 的另一个属性而没有重新定义 $a$。

若先合并所有潜在模型，则 $q(a)=\bot$ 仍在最终潜在模型中，投影时 $a$ 的删除闭包会删除 $b$。

若在 $p \oplus q$ 后立即投影，删除标记 $q(a)=\bot$ 被丢弃。随后再叠加 $r$，系统已经失去“整棵 `/A` 被删除”的证据，可能得到不同结果。

这正是 S-N-V 阶段分离的数学理由：S 阶段完成结构合并，N 阶段进行规范化，V 阶段进行最终验证。合并链不能在中间被业务投影或验证过程截断。

## 五、从坐标偏函数到树形 `x:extends`

### 5.1 树结构的坐标化编码

一个 XDSL 树可以编码为语义坐标上的有限偏函数。典型编码如下：

- 节点根：路径 $\pi$ 编码为根坐标 $root(\pi)$，可写入 $\mathsf{present}$ 或 $\bot$。
- 节点存在：$root(\pi)/\#node \mapsto tagName$。
- 属性值：$root(\pi)/@attr \mapsto value$。
- 文本内容：$root(\pi)/\#text \mapsto text$。
- 子节点：用父路径、子标签和稳定键形成子路径，例如 $\pi\cdot column[@name='status']$，再映射为 $root(\pi\cdot column[@name='status'])$。
- 顺序信息：本节的坐标 LWW 证明只允许两种处理方式：一是最终顺序由 stable key 和规范化排序规则确定；二是把每条原子顺序约束编码到带稳定 source id 的独立坐标。若多个约束需要在同一坐标上累积，不能使用本节的 $\oplus$ 证明，而应使用第 6.10 节的约束幺半群或第六节的端函数 carrier。

若某差量删除路径 $\pi$ 处的子树，则编码包含 $root(\pi)\mapsto \bot_{root(\pi)}$。若某差量以坐标 tombstone carrier 重新声明同一子树根，则必须同时写入 $root(\pi)\mapsto \mathsf{present}$ 或节点摘要，从而在同一坐标上覆盖旧 tombstone；否则旧 tombstone 会继续屏蔽后代坐标。这一规则只属于第五节的理想化 carrier。

因此，在坐标 tombstone carrier 中，“重新声明同一根坐标”只是解除祖先 tombstone 的屏蔽，不是自动的子树替换。旧后代坐标可能重新暴露；若工程语义要求删除后重建必须清空旧子树，应使用 `Replace(T)` 或第六节的端函数 carrier。

#### 5.1a 坐标 carrier 的跨层次交错示例

以下示例验证坐标 tombstone carrier 在 `merge/remove/replace` 跨层次交错时仍然保持结合律。基础模型为：

```xml
<Page>                          <!-- π = Page -->
  <Panel id="p1">               <!-- π = Page/Panel[@id='p1'] -->
    <Field id="f1" />           <!-- π = Page/Panel[@id='p1']/Field[@id='f1'] -->
    <Field id="f2" />
  </Panel>
  <Panel id="p2">
    <Field id="f3" />
  </Panel>
</Page>
```

**示例 1：remove 后 merge 重建。** Δ₁ 在 Page 层 merge，但 remove Panel p1；Δ₂ 在 Page 层 merge，merge Panel p1 并新增后代。

```xml
<!-- Δ₁ -->
<Page x:override="merge">
  <Panel id="p1" x:override="remove" />
</Page>

<!-- Δ₂ -->
<Page x:override="merge">
  <Panel id="p1" x:override="merge">
    <Button id="btn" color="red" x:override="merge" />
  </Panel>
</Page>
```

坐标编码中，Δ₁ 在 $root(\text{p1})$ 写入 $\bot$。Δ₂ 在 $root(\text{p1})$ 写入 $\mathsf{present}$，在 $root(\text{p1}/\text{btn})$ 写入节点内容。由 LWW：

$$
E(\Delta_1)\oplus E(\Delta_2):\quad
root(\text{p1})\mapsto\mathsf{present},\;
root(\text{p1}/\text{btn})\mapsto\{\ldots\}
$$

Δ₂ 的 $\mathsf{present}$ 覆盖 Δ₁ 的 $\bot$，Panel p1 被重建，Button btn 作为新后代出现。两种括号化给出相同的 LWW 结果。

**示例 2：replace 清空后代后 merge 恢复部分内容。** Δ₁ replace Panel p1 为仅含 Field f1 的子树（清除 f2）；Δ₂ merge Panel p1，恢复 f2 并新增 f2 上的属性。

```xml
<!-- Δ₁ -->
<Page x:override="merge">
  <Panel id="p1" x:override="replace">
    <Field id="f1" />
  </Panel>
</Page>

<!-- Δ₂ -->
<Page x:override="merge">
  <Panel id="p1" x:override="merge">
    <Field id="f2" label="备注" />
  </Panel>
</Page>
```

坐标编码中，Δ₁ 的 `replace` 等价于：写入 $root(\text{p1})\mapsto\mathsf{present}$，写入 f1 后代坐标，不写入 f2 后代坐标（或显式在 $root(\text{p1}/\text{f2})$ 写入 $\bot$）。Δ₂ 在 f2 坐标写入 merge 内容。由 LWW，f2 坐标由 Δ₂ 决定（若 Δ₁ 未写入则为新增，若 Δ₁ 写了 $\bot$ 则 Δ₂ 的值覆盖 $\bot$）。两种括号化结果一致。

**示例 3：三级交错——ancestor replace、middle remove、descendant merge。**

```xml
<!-- Δ₁：replace Page 全部内容 -->
<Page x:override="replace">
  <Panel id="p1">
    <Field id="f1" />
  </Panel>
</Page>

<!-- Δ₂：remove Panel p1 -->
<Page x:override="merge">
  <Panel id="p1" x:override="remove" />
</Page>

<!-- Δ₃：merge Panel p1 并添加后代 -->
<Page x:override="merge">
  <Panel id="p1" x:override="merge">
    <Field id="f1" required="true" />
    <Button id="btn" />
  </Panel>
</Page>
```

预合并 $E(\Delta_1)\oplus E(\Delta_2)\oplus E(\Delta_3)$：
- Page 层：Δ₁ 的 replace 在 $root(\text{Page})$ 写入 $\mathsf{present}$，Δ₂/Δ₃ 同样写入 $\mathsf{present}$，LWW 无冲突。
- Panel p1：Δ₁ 写入 present（含 f1），Δ₂ 写入 $\bot$，Δ₃ 写入 present（含 f1+btn）。LWW 链：present → $\bot$ → present。最终 present。
- Field f1：Δ₁ 写入，Δ₂ 不写，Δ₃ 写入 required=true。最终 Δ₃ 胜出。
- Button btn：仅 Δ₃ 写入。新增。

任意括号化后逐坐标 LWW 都得到同一结果。坐标 tombstone carrier 通过"每个坐标独立 LWW"天然支持这种跨层次交错。

设潜在树集合为 $Tree^P$，其中允许 tombstone、virtual 节点和可被 LWW 坐标表达的结构证据。编码函数为偏函数：

$$
E: Tree^P \rightharpoonup P
$$

其中 $P$ 是潜在模型空间。$E$ 只在满足 stable-key 唯一性和元模型约束的潜在树上有定义。本文只主张编码后的潜在模型相等；除非额外证明 $E$ 对所选规范树表示是单射，否则不主张源树语法对象、属性顺序、空白、注释或语法糖层面的字面相等。

只要 DSL 元模型为同级可重复节点定义了稳定键，树中的递归结构就可以转化为坐标集合上的有限偏函数。

### 5.2 递归合并算法与坐标合并等价

Nop/XLang 中常见的树合并算法可以概括为：

- 属性按名称覆盖。
- 子节点按稳定键匹配。
- 匹配到的子节点递归合并。
- 差量中新出现的子节点视为新增。
- `x:override="remove"` 记录删除意图。

为了与第四节保持一致，这里的树合并函数必须理解为**坐标 tombstone carrier 上的潜在树合并**，记为 $MergeTree^P(T,D)$。它的结果不是已经丢弃 tombstone 的可观测树，而是仍保留 `remove` tombstone、`virtual` 标记以及第 5.1 节允许的 LWW 可编码证据的潜在树。累积型顺序约束不属于本节的 LWW 证明范围，必须转入第 6.10 节的约束 carrier 或第六节的端函数 carrier。工程实现若在某一步直接返回物理树或 `NULL` 并丢弃删除证据，则不能把该结果继续作为本节结合律中的中间项；它只能作为整条合并链结束后的投影实现，或转入第六节的端函数 carrier 重新解释。

下面的等价性只对一种显式的坐标化潜在树成立：`remove` 不物理删除左侧子树坐标，而是在被删除子树根坐标写入 $\bot$；旧后代坐标仍可保留在潜在表示中，并由最终 $Pr$ 的删除闭包屏蔽。若某递归树合并实现直接从 children map 中移除节点并丢弃其旧后代，则它不满足本节的 $E(MergeTree^P(T,D))=E(T)\oplus E(D)$，只能作为最终投影或第六节端函数语义的一种实现。

在本节中，$MergeTree^P$ 不是当前 Nop `DeltaMerger` 的逐行规范，而是由编码等式定义出的理想化潜在树合并：

$$
MergeTree^P(T,D)=E^{-1}_{can}(E(T)\oplus E(D))
$$

其中 $E^{-1}_{can}$ 是对编码后潜在模型选择一个确定性规范树代表元的偏函数，满足 $E^{-1}_{can}(p)\in Dom(E)$ 且 $E(E^{-1}_{can}(p))=p$。它只在 stable-key 唯一、元模型约束满足、以及所有附加证据都可用 LWW 坐标表示的输入上有定义。等价地，可以把 $MergeTree^P$ 看作以下递归算法的规范化实现：属性和文本按坐标右覆盖，同 key child 递归合并，新增 child 写入新子路径坐标，删除 child 在 $root(\pi)$ 写入 $\bot$ 且不物理删除旧后代坐标，可被 LWW 编码的本地潜在信息按坐标覆盖。这个定义确保合并结果仍在 $Tree^P$ 中，并保留最终投影需要的 tombstone 证据。

形式化地，本节把 $MergeTree^P$ 当作偏操作处理：只有当 $T,D\in Dom(E)$ 且 $E(T)\oplus E(D)\in Dom(E^{-1}_{can})$ 时，$MergeTree^P(T,D)$ 才有定义。后文定理 3 只对所有需要的 $E$ 和 $E^{-1}_{can}$ 调用均有定义的 $A,B,C$ 陈述，而不是把闭包结论预先塞进 $Tree^P$ 的定义。

反例边界很重要：如果把父节点下所有顺序约束都编码到同一个坐标，例如 $root(\pi)/\#orderConstraints$，并期望合并时收集约束，那么 $\Delta_1$ 写入 “$A$ after $B$”、$\Delta_2$ 写入 “$C$ after $D$” 时，$E(\Delta_1)\oplus E(\Delta_2)$ 只会保留后者。这不是顺序约束收集语义。因此，累积型顺序约束必须使用第 6.10 节的自由幺半群、独立 source-id 坐标，或端函数 carrier。

由定义可得以下编码引理。

#### 引理 5.2：理想化潜在树合并与坐标合并等价

若 $T,D\in Dom(E)$ 且 $E(T)\oplus E(D)\in Dom(E^{-1}_{can})$，则对核心 merge/remove 语义有：

$$
E(MergeTree^P(T,D)) = E(T) \oplus E(D)
$$

这里右侧的 $\oplus$ 是第 2.4 节定义的坐标覆盖合并。

#### 证明

若采用 $MergeTree^P(T,D)=E^{-1}_{can}(E(T)\oplus E(D))$ 作为定义，则结论由 $E(E^{-1}_{can}(p))=p$ 在 $E^{-1}_{can}$ 定义域上的性质直接得到。

若采用等价递归算法，则对树的高度做结构归纳。

基础情形：叶子节点没有子节点。树合并只涉及节点自身、属性和文本。它们都被编码为固定坐标上的值，合并规则就是右侧覆盖左侧，因此等价于 $\oplus$。

归纳假设：高度小于 $h$ 的任意子树，递归树合并等价于坐标合并。

归纳步骤：考虑高度为 $h$ 的节点。其属性集合按属性名逐坐标覆盖，等价于 $\oplus$。其子节点先按稳定键分组；对同键子节点调用递归合并，由归纳假设等价于对应子坐标子空间上的 $\oplus$；差量中只存在于右侧的子节点在坐标偏函数中表现为新增定义；删除节点表现为该子节点坐标的 $\bot$。由于各子坐标空间互不重叠，整体合并就是这些坐标子空间合并的并集。故高度为 $h$ 的树合并也等价于 $\oplus$。

由数学归纳法，任意有限树的核心递归合并都等价于坐标偏函数合并。

### 定理 3：理想化坐标 tombstone carrier 下的核心树合并满足结合律

对任意三棵可编码为同一稳定坐标空间中潜在模型的 XDSL 树或差量 $A,B,C$，若合并使用本节定义的理想化核心 merge/remove 语义，中间结果按坐标 tombstone carrier 保留删除和第 5.1 节允许的 LWW 可编码证据，并且 $E(A)\oplus E(B)$、$E(B)\oplus E(C)$、$(E(A)\oplus E(B))\oplus E(C)$、$E(A)\oplus(E(B)\oplus E(C))$ 都属于 $Dom(E^{-1}_{can})$，则：

$$
E(MergeTree^P(MergeTree^P(A,B),C))
=
E(MergeTree^P(A,MergeTree^P(B,C)))
$$

这里直接以编码后的潜在模型相等作为结论，不声明源文本、属性顺序、空白、注释或语法糖层面的相等。最终可观测树在一次性投影后也相等。该定理不是对当前 Nop `DeltaMerger` 所有 `x:override` 分支的实现符合性证明；当前 Nop 的 `remove/replace/append/prepend/merge-super/bounded-merge` 等工程语义更适合按第六节的端函数 carrier 或第 6.11 节的覆盖矩阵逐项解释。

#### 证明

由 5.2 节可知，核心树合并等价于坐标合并：

$$
E(MergeTree^P(MergeTree^P(A,B),C))
=
(E(A) \oplus E(B)) \oplus E(C)
$$

由定理 1：

$$
(E(A) \oplus E(B)) \oplus E(C)
=
E(A) \oplus (E(B) \oplus E(C))
$$

再由 5.2 节反向应用：

$$
E(A) \oplus (E(B) \oplus E(C))
=
E(MergeTree^P(A,MergeTree^P(B,C)))
$$

因此，本节理想化坐标 tombstone carrier 下的核心潜在树合并满足结合律。

## 六、扩展合并语义的形式化处理

真实 XDSL 不只有覆盖和删除，还可能包含追加、前置、环绕、替换、有序插入等语义。扩展语义的证明不能只列举若干孤立例子后直接推广到全部 XLang 语义；必须说明这些异质操作如何落到同一个结合结构中。

本文采用两层处理：

- 对互不重叠、只在单坐标上组合的扩展操作，使用局部幺半群提升定理。
- 对 `replace/remove/merge`、祖先/后代重叠、混合操作等跨坐标语义，统一解释为潜在状态空间上的确定性端函数，结合律来自函数复合。

### 6.1 局部操作 delta 结合算子定理

逐坐标扩展语义必须区分“值空间”和“操作空间”。对每个坐标 $c$，令 $W_c$ 为该坐标最终可被作用的局部值空间，令 $O_c$ 为局部操作集合。设存在二元组合 $\star_c:O_c \times O_c \to O_c$ 和单位元 $e_c \in O_c$，使 $(O_c,\star_c,e_c)$ 构成幺半群，即满足闭包、结合律和左右单位元律：

$$
(x \star_c y) \star_c z = x \star_c (y \star_c z)
$$

$$
e_c \star_c x = x \star_c e_c = x
$$

同时给定右作用：

$$
act_c:W_c\times O_c\to W_c
$$

满足：

$$
act_c(w,e_c)=w
$$

$$
act_c(act_c(w,x),y)=act_c(w,x\star_c y)
$$

此时差量潜在模型存储的是局部操作，而不一定是最终值。若普通基础值也要与差量一起写成 $P_0\otimes\Delta$ 的模型级形式，还需要一个嵌入 $\eta_c:V_c\to O_c$，并且要求 $act_c(w,\eta_c(v))$ 与该 DSL 对“设置为值 $v$”的语义一致。若不存在这样的嵌入，例如 around/super 更自然地表示为 $W_c\to W_c$ 的函数，则只能折叠操作后用 $act$ 作用于基础值，不能把基础值也当作同类 $\otimes$ 操作。

为了处理偏函数的未定义情况，将未定义解释为单位元：

$$
\widehat{p}(c)=
\begin{cases}
p(c), & c \in Dom(p) \\
e_c, & c \notin Dom(p)
\end{cases}
$$

定义操作潜在模型之间的模型级组合 $\otimes$：

$$
(p \otimes q)(c)=
\begin{cases}
\widehat{p}(c) \star_c \widehat{q}(c), & \widehat{p}(c) \star_c \widehat{q}(c) \ne e_c \\
\text{undef}, & \text{otherwise}
\end{cases}
$$

等价地，也可以显式保留定义域并集上的单位元；本文采用“单位元不写入稀疏模型”的规范表示。该规范表示是本定理的一部分：后续组合、序列化、规范化和验证都不得区分“显式写入单位元”和“省略单位元”。若某 DSL 需要保留显式 no-op 的来源位置、注释或审计信息，则这些信息必须进入新的操作值或 provenance carrier，并重新证明对应的幺半群律。

则 $\otimes$ 在操作潜在模型上满足结合律；并且由 $act_c$ 的作用律，先逐步应用操作与先预合并操作再应用到基础值等价。

#### 证明

仍按任意坐标 $c$ 做逐点证明。对左侧有：

$$
\widehat{(p \otimes q) \otimes r}(c)=(\widehat{p}(c) \star_c \widehat{q}(c)) \star_c \widehat{r}(c)
$$

对右侧有：

$$
\widehat{p \otimes (q \otimes r)}(c)=\widehat{p}(c) \star_c (\widehat{q}(c) \star_c \widehat{r}(c))
$$

二者由 $\star_c$ 的结合律相等。若结果等于单位元，两边在稀疏规范表示中同为 `undef`；若不等于单位元，两边写入同一个操作值。故模型级操作组合 $\otimes$ 满足结合律。再由 $act_c(act_c(w,x),y)=act_c(w,x\star_c y)$，对任意基础局部值 $w$，逐步应用 $x$ 后再应用 $y$ 与先组合 $x\star_c y$ 后应用得到同一值。逐坐标推广后，预合并操作链再作用于基础模型与逐步作用得到同一潜在结果。

覆盖语义是该定理的特例。令 $O_c=A_c \cup \{e_c\}$，其中 $e_c$ 表示“无操作”，定义：

$$
x \star_c y =
\begin{cases}
x, & y=e_c \\
y, & y\ne e_c
\end{cases}
$$

即右侧非单位元操作覆盖左侧。这个算子满足结合律，且在稀疏表示中等价于第二节的 $\oplus$。

### 6.2 追加与前置

本节只覆盖抽象的纯序列片段追加/前置 carrier，不直接覆盖当前 Nop `APPEND/PREPEND` 在 XNode 树节点级别上的完整实现。当前实现还会合并属性、规范化 XPL 内容、处理 `merge-super`、移动 children，并通过 `OverrideHelper` 处理与其他 override 的组合；这些工程语义必须按第 6.11 节的证明义务另行验证。

若某坐标值是列表或字符串片段，可定义追加操作：

$$
x \star y = concat(x,y)
$$

由于序列连接满足结合律：

$$
concat(concat(x,y),z)=concat(x,concat(y,z))
$$

所以追加语义满足局部结合律。

前置可以视为追加的对偶，或通过相反顺序的片段连接定义，同样满足结合律。

### 6.3 抽象单洞环绕语义

本节的 around/super 是一个抽象单洞上下文例子，不等同于当前 Nop `XDefOverride` 标准枚举中的 `merge-super`。当前 Nop 标准 override 包括 `remove`、`replace`、`prepend`、`append`、`merge`、`merge-replace`、`bounded-merge`、`merge-super`；其中 `merge-super` 需要按 XNode 子树端函数语义解释，不能直接套用本节二元组证明。

令一个环绕操作表示为二元组 $(L,R)$，其作用为：

$$
op_{L,R}(x)=L \cdot x \cdot R
$$

其中 $\cdot$ 是序列连接。

若先应用 $(L_1,R_1)$，再应用 $(L_2,R_2)$，复合操作为：

$$
(L_1,R_1) \star (L_2,R_2)=(L_2 \cdot L_1, R_1 \cdot R_2)
$$

验证结合律：

$$
((L_1,R_1) \star (L_2,R_2)) \star (L_3,R_3)
$$

$$
=(L_3 \cdot L_2 \cdot L_1, R_1 \cdot R_2 \cdot R_3)
$$

另一方面：

$$
(L_1,R_1) \star ((L_2,R_2) \star (L_3,R_3))
$$

$$
=(L_3 \cdot L_2 \cdot L_1, R_1 \cdot R_2 \cdot R_3)
$$

两边相等。因此，单洞、恰一次 `super`、无副作用、无上下文依赖的 around/super 环绕语义满足结合律。注意它一般不满足交换律，因为前后顺序有业务意义。若真实语义允许零次、多次或条件式引用 `super`，则应改用下一节的 $S \to S$ 函数复合模型，而不能直接套用二元组 $(L,R)$ 的证明。

### 6.4 children 树状态空间与本地 carrier

第 6.4-6.9 节的证明路线如下：先定义稳定 key 索引的树状态空间 $S_p$，再把正规操作和表达式解释为 $S_p\to S_p$ 的指称语义，随后构造 $NF$ 把有限表达式规约为正规操作，并证明 $NF$ 保持 denotation，最后在 denotation 商上得到结合的预合并代数。

当不同操作会影响同一棵子树，或祖先坐标与后代坐标同时出现操作时，逐坐标独立性不再是一个安全假设。此时应选择一个潜在树状态空间，并把差量解释为这个状态空间上的确定性端函数。

对任意路径 $p$，令 $K_p$ 为该路径下同级 children 的稳定 key 集合。一个 key 可以形式化为 `(role, tag, keyName, keyValue)`，其中 `keyName/keyValue` 来自 XDef 或 DSL 元模型指定的身份字段；`name`、`id`、`value`、`x:id` 等属性只有在 schema 或通用 XNode fallback 规则把它们声明为身份字段时才可进入 key。物理数组下标不属于 $K_p$。

为使下面的端函数严格定型，本文采用“路径定型”的抽象：每个路径 $p$ 的可接受节点 sort、tag、可用本地字段和 child key 宇宙已经由 schema 固定在 $L_p$ 和 $K_p$ 中，所有 $T\in S_p$ 都满足该类型约束，`Replace(T)` 也必须保持 $T\in S_p$。若真实 DSL 允许某路径处替换为会改变 child key 提取规则的不同 sort，则应把状态空间改写为按 sort/tag 索引的依赖和类型，例如 $S_p=\sum_{\sigma\in\Sigma_p}S_{p,\sigma}$，并为跨 sort 的 `Replace` 重新给出类型规则和后续证明；不能直接套用本节的固定 $K_p/L_p$ 表述。

本文还假设 key 提取函数在每个父路径内是确定且单射的：同一父节点下不得存在两个具有相同 stable key 的 children；若输入违反唯一性，编码函数 $E$ 未定义或验证失败。作为 stable key 的字段必须被视为身份字段，不允许通过普通属性覆盖改变其身份；所谓“改名”必须编码为删除旧 key 并新增新 key，或编码为包含完整新子树的 `Replace(T)`。

定义路径 $p$ 处的潜在子树状态空间：

$$
S_p ::= \varnothing_p \mid Node_p(\ell,\chi)
$$

其中：

- $\varnothing_p$ 表示路径 $p$ 处子树不存在，或已被删除。
- $\ell \in L_p$ 是节点本地潜在信息，包括 tag、属性、文本、virtual 标记、顺序约束等。
- $\chi:K_p \rightharpoonup S_{p\cdot k}$ 是按稳定 key 索引的有限 children 映射。
- 若 $k\notin Dom(\chi)$，则约定 $\chi(k)=\varnothing_{p\cdot k}$。

为保持 children 映射有限，定义更新算子 $put_k$：若 $t\ne\varnothing_{p\cdot k}$，则 $put_k(\chi,t)$ 在 key $k$ 处取 $t$；若 $t=\varnothing_{p\cdot k}$，则 $put_k(\chi,t)$ 从有限映射中移除 $k$。在两种情况下，未列出的 key 仍按约定取 $\varnothing$。

定义空虚拟节点：

$$
empty_p = Node_p(\ell^0_p,\varnothing)
$$

其中 $\ell^0_p\in L_p$ 是本地信息的空值，例如空属性、空文本、无顺序约束。本文固定采用“将不存在子树提升为空虚拟节点后再 merge 或 lift”的 denotation。若某 DSL 希望“不存在子树上 merge”保持不存在，也可以选择另一套 denotation；但必须重新固定对应的 $merge$、$Lift$ 和 $NF$ 规则。

注意：本节的 $S_p$ 端函数语义与第四、五节的坐标 tombstone 语义是两种不同 carrier。第四节的 $\bot$ 用于在坐标偏函数中保留“删除祖先后屏蔽旧后代”的证据；本节的 $\varnothing_p$ 是端函数作用后的子树状态，不承诺区分“从未存在”和“被删除”。因此，不能把第六节的中间 $S_p$ 状态再当作第四节的 tombstone 潜在模型使用。本节也不证明坐标 tombstone 的屏蔽语义。若某工程实现需要在树状态自身中保留删除证据，应把状态空间扩展为 $S_p ::= Absent_p \mid Tomb_p \mid Node_p(\ell,\chi)$，并重新定义 `Remove`、`put_k`、`Pr`、`Norm` 和后续全部引理。

本地字段差量也必须有自己的 carrier。对每个路径 $p$，设本地差量集合为 $M_p$，并给定幺半群：

$$
(M_p,\diamond^{loc}_p,\mathbf{1}_p)
$$

以及右作用：

$$
act_p:L_p\times M_p\to L_p
$$

满足：

$$
act_p(\ell,\mathbf{1}_p)=\ell
$$

$$
act_p(act_p(\ell,\mu_1),\mu_2)
=
act_p(\ell,\mu_1\diamond^{loc}_p\mu_2)
$$

且 $\diamond^{loc}_p$ 结合。属性按名称右覆盖、顺序约束作为潜在证据收集、固定 tie-breaker 的 ordered multiset 连接等，都可以作为 $M_p$ 的具体实例；如果某一本地操作不满足上述作用律，则它不属于本节定理的前提。

根状态空间记为 $S=S_\epsilon$。定义最终结果类型：

$$
Result ::= Ok(Model) \mid Err(ErrorSet)
$$

其中 `ErrorSet` 是规范化后的错误集合，不依赖遍历顺序、对象地址或调用栈文本。端函数 carrier 的最终可观测结果由一组按 carrier 定型的确定性函数给出：

$$
Norm_S:S\to S'_S,
\quad Pr_S:S'_S\to Obs_S,
\quad Validate_S:Obs_S\to Result
$$

$$
Final_S=Validate_S\circ Pr_S\circ Norm_S:S\to Result
$$

其中 $Norm_S$ 必须作用于端函数 carrier 的潜在状态，并保留会影响后续 $Pr_S$ 或排序的证据。在本节的两分支 $S_p ::= \varnothing\mid Node$ 状态中，这些证据可以包括本地字段 $\ell$ 中的 virtual 标记、顺序约束和其他规范化证据，但不包括第四节坐标 tombstone 的祖先屏蔽证据，除非状态空间按上一段显式扩展为含 `Tomb` 的三分支 carrier。

对应地，坐标 tombstone carrier 使用 $Norm_P:P\to P'_P$、$Pr_P:P'_P\to Obs_P$、$Validate_P:Obs_P\to Result$ 和 $Final_P:P\to Result$。局部操作 carrier 若需要可观测结果，则使用 $Final_O$ 作用于 $Apply$ 后的值状态。下文在非正式段落中可用 $Final$ 统称这些函数；正式定理必须写出 $Final_P$、$Final_O$ 或 $Final_S$。

结合律只在 $S$ 或其端函数空间 $End(S)$ 上证明，不能在每一步后投影到最终物理树。

### 6.5 正规操作、merge body 与递归 children 语义

在每个路径 $p$ 上，同时定义正规操作语法 $NOp_p$ 和 merge 差量体 $D_p$：

$$
NOp_p ::= Remove \mid Replace(T) \mid Merge(D)
$$

$$
D_p ::= (\mu,\delta)
$$

其中 $T\in S_p$，$\mu\in M_p$，$\delta:K_p\rightharpoonup_{fin} NOp_{p\cdot k}$ 是按稳定 key 索引的有限 child 操作映射。该定义是沿树路径的归纳族：一个 $D_p$ 只能引用严格后代路径 $p\cdot k$ 上的有限正规操作，因此任何实际差量语法项都是有限对象。

定义递归合并函数 $merge_p:S_p\times D_p\to S_p$。若 $D=(\mu,\delta)$，则：

$$
merge_p(\varnothing_p,D)=merge_p(empty_p,D)
$$

$$
merge_p(Node_p(\ell,\chi),(\mu,\delta))=Node_p(act_p(\ell,\mu),\chi')
$$

其中 $\chi'$ 是从有限 map $\chi$ 出发，对每个 $k\in Dom(\delta)$ 以任意固定稳定顺序应用 $put_k$ 得到的有限 map。由于每一步只更新不同 key，更新顺序不影响语义。按约定访问未定义 key 时取 $\varnothing$，因此 $\chi'$ 满足：

$$
\chi'(k)=
\begin{cases}
\llbracket \delta(k)\rrbracket_{p\cdot k}(\chi(k)), & k\in Dom(\delta) \\
\chi(k), & k\notin Dom(\delta)
\end{cases}
$$

因为 $\delta$ 是有限映射，且每个 child 操作由归纳假设返回 $S_{p\cdot k}$ 中的状态，所以 $merge_p$ 返回 $S_p$ 中的有限潜在树。新增 child 就是对 $\varnothing_{p\cdot k}$ 执行 child 操作；删除 child 则得到 $\varnothing_{p\cdot k}$，并可由 $put_k$ 在规范化表示中移出有限 map。

### 6.6 操作表达式、`Lift_q` 与 denotation

为了处理祖先/后代重叠，需要把正规操作扩展为有限表达式：

$$
Expr_p ::= n \mid Expr_p\odot Expr_p \mid Lift_k(e)
$$

其中 $n\in NOp_p$，$k\in K_p$，$e\in Expr_{p\cdot k}$。组合 $e_1\odot e_2$ 的方向固定为“先 $e_1$ 后 $e_2$”。

相对路径 $q=k_1\cdots k_m$ 是稳定 key 的有限序列。定义：

$$
Lift_\epsilon(e)=e
$$

$$
Lift_{k\cdot q}(e)=Lift_k(Lift_q(e))
$$

其中 $e$ 的类型位于路径 $p\cdot k\cdot q$，$Lift_{k\cdot q}(e)$ 的类型位于路径 $p$。

所有表达式的 denotation 都是确定性端函数。对正规操作：

$$
\llbracket Remove\rrbracket_p(s)=\varnothing_p
$$

$$
\llbracket Replace(T)\rrbracket_p(s)=T
$$

$$
\llbracket Merge(D)\rrbracket_p(s)=merge_p(s,D)
$$

对组合表达式：

$$
\llbracket e_1\odot e_2\rrbracket_p
=
\llbracket e_2\rrbracket_p\circ\llbracket e_1\rrbracket_p
$$

对单步 lift：

$$
\llbracket Lift_k(e)\rrbracket_p(\varnothing_p)
=
\llbracket Lift_k(e)\rrbracket_p(empty_p)
$$

$$
\llbracket Lift_k(e)\rrbracket_p(Node_p(\ell,\chi))
=
Node_p(\ell, put_k(\chi,\llbracket e\rrbracket_{p\cdot k}(\chi(k))))
$$

因此，不存在的中间节点会被提升为空虚拟节点，后代操作总能在同一个祖先状态空间 $S_p$ 内解释。若后代操作最终得到 $\varnothing$，对应 child key 会从有限 children map 中移除；是否删除空虚拟祖先由最终确定性的 $Norm/Pr$ 决定。

### 6.7 `NF`、同路径组合与 `diamond` 的构造性规约

为了把有限表达式重新写成只含 `Merge/Remove/Replace` 的正规操作，定义：

$$
NF_p:Expr_p\to NOp_p
$$

先定义同路径正规操作组合 $Compose_p:NOp_p\times NOp_p\to NOp_p$。下表中仍采用“先左后右”的顺序：

| 组合 | $Compose_p$ 结果 |
|---|---|
| $n\odot Remove$ | `Remove` |
| $n\odot Replace(T)$ | `Replace(T)` |
| $Remove\odot Merge(D)$ | `Replace(merge_p(empty_p,D))` |
| $Replace(T)\odot Merge(D)$ | `Replace(merge_p(T,D))` |
| $Merge(D_1)\odot Merge(D_2)$ | `Merge(D_1\diamond_p D_2)` |

其中 $D_1\diamond_p D_2$ 是 merge body 的递归组合。若 $D_i=(\mu_i,\delta_i)$，则：

$$
D_1\diamond_pD_2=(\mu_1\diamond^{loc}_p\mu_2,\delta)
$$

$$
\delta(k)=
\begin{cases}
NF_{p\cdot k}(\delta_1(k)\odot\delta_2(k)), & k\in Dom(\delta_1)\cap Dom(\delta_2) \\
\delta_1(k), & k\in Dom(\delta_1)\setminus Dom(\delta_2) \\
\delta_2(k), & k\in Dom(\delta_2)\setminus Dom(\delta_1)
\end{cases}
$$

该定义是良基的。为避免同步递归的循环性，定义一个同时作用于正规操作、merge body 和表达式的秩。先定义 $rank_S(T)$ 为有限树状态 $T\in S_p$ 的最大子树高度；然后定义正规操作秩 $rank_N$：

$$
rank_N(Remove)=rank_N(Merge((\mu,\varnothing)))=(0,1)
$$

$$
rank_N(Replace(T))=(rank_S(T),1)
$$

$$
rank_N(Merge((\mu,\delta)))=(1+\max_{k\in Dom(\delta)}rank_N(\delta(k))_1,
1+\sum_{k\in Dom(\delta)}rank_N(\delta(k))_2)
$$

其中空最大值取 $-1$，$rank_N(x)_1$ 和 $rank_N(x)_2$ 分别表示第一、第二分量。表达式秩 $rank_E$ 定义为：

$$
rank_E(n)=rank_N(n)\quad(n\in NOp_p)
$$

$$
rank_E(e_1\odot e_2)=(\max(rank_E(e_1)_1,rank_E(e_2)_1),1+rank_E(e_1)_2+rank_E(e_2)_2)
$$

$$
rank_E(Lift_k(e))=(1+rank_E(e)_1,1+rank_E(e)_2)
$$

merge body 的秩定义为 $rank_D(D)=rank_N(Merge(D))$。所有秩按自然数对的字典序比较。直观地，第一分量记录从当前路径向下可触及的最大 child 深度，第二分量记录语法大小。

等价地，对任意有限表达式 $e$，可写作：

$$
\rho_p(e)=(depth(e),size(e))
$$

其中 $depth(e)$ 是第一分量，$size(e)$ 是第二分量。对二元对象使用组合秩：

$$
rank_{DD}(D_1,D_2)=rank_E(Merge(D_1)\odot Merge(D_2))
$$

$$
rank_{NN}(n_1,n_2)=rank_E(n_1\odot n_2)
$$

在 $D_1\diamond_pD_2$ 的递归分支中，$NF$ 只作用于两个外层 `Merge` 的严格 child 子项 $\delta_1(k)$ 和 $\delta_2(k)$，其表达式秩的第一分量严格小于 $rank_{DD}(D_1,D_2)$ 的第一分量；在同一路径处理 $e_1\odot e_2$ 时，归纳调用只作用于真子表达式 $e_1,e_2$，因而第一分量不增且第二分量严格下降。`Compose_p(n_1,n_2)` 的非 `Merge/Merge` 分支不递归；`Merge/Merge` 分支只调用具有同一组合秩的 $D_1\diamond_pD_2$，而 $diamond$ 的内部递归再转入严格更小的 child 表达式。由自然数字典序的良基性，`diamond`、`Compose` 与 `NF` 的同步定义不会循环。

现在定义 $NF_p$：

$$
NF_p(n)=n\quad(n\in NOp_p)
$$

$$
NF_p(e_1\odot e_2)=Compose_p(NF_p(e_1),NF_p(e_2))
$$

$$
NF_p(Lift_k(e))=Merge((\mathbf{1}_p,\{k\mapsto NF_{p\cdot k}(e)\}))
$$

这里 $\mathbf{1}_p$ 是本地差量幺元。最后一个等式说明：对子节点 $k$ 的 lift，在父节点上就是一个本地无变化、仅 child map 含 $k$ 的 `Merge`。这一定义同时覆盖了不存在 child、删除 child、替换 child 和递归 merge child 的情况。

`diamond`、`Compose` 与 `NF` 是按上述良基度量做的同步归纳定义：固定最大深度 $h$，先假设所有严格 child 路径上的 $NF$ 已定义且保持 denotation，再定义当前路径上的 $diamond$、`Compose` 和 $NF$。由于每个实际差量只包含有限深度的语法树，归纳从叶子路径开始，逐层回到根路径。

由构造可得以下闭包性质：若 $D_1,D_2\in D_p$，则 $D_1\diamond_pD_2\in D_p$；若 $n_1,n_2\in NOp_p$，则 $Compose_p(n_1,n_2)\in NOp_p$；若 $e\in Expr_p$，则 $NF_p(e)\in NOp_p$。闭包依赖 $M_p$ 对 $\diamond^{loc}_p$ 封闭、child 操作映射有限、以及严格 child 路径上的归纳闭包假设。

下面三个引理是同一个同步归纳定理的三个分量。归纳命题 $\mathcal P(r)$ 为：对所有秩小于等于 $r$ 的对象，`diamond` 闭包且保持 denotation、`Compose` 闭包且保持 denotation、`NF` 闭包且保持 denotation。证明按秩字典序归纳。引理 1 在严格 child 路径上使用引理 3 的较小秩归纳假设；引理 3 在同一路径组合分支使用同一秩阶段已由引理 2 建立的 `Compose` 正确性；引理 2 的 `Merge/Merge` 情形使用引理 1。由于 `Merge/Merge` 只调用已定义的 $D_1\diamond D_2$，而 $diamond$ 的递归调用都转入严格 child 或较小表达式，循环依赖被秩归纳消除。

#### 引理 1：`diamond` 保持 denotation

对任意 $D_1,D_2\in D_p$，有：

$$
\llbracket Merge(D_1\diamond_pD_2)\rrbracket_p
=
\llbracket Merge(D_2)\rrbracket_p\circ\llbracket Merge(D_1)\rrbracket_p
$$

证明。令 $D_i=(\mu_i,\delta_i)$。若输入为 $\varnothing_p$，两侧都先按定义提升到 $empty_p$，因此只需证明输入为 $Node_p(\ell,\chi)$ 的情况。

本地部分由 $M_p$ 的作用律得到：

$$
act_p(act_p(\ell,\mu_1),\mu_2)
=
act_p(\ell,\mu_1\diamond^{loc}_p\mu_2)
$$

children 部分逐 key 比较。若 $k$ 同时属于 $Dom(\delta_1)$ 和 $Dom(\delta_2)$，左后右的结果为：

$$
\llbracket \delta_2(k)\rrbracket_{p\cdot k}
(\llbracket \delta_1(k)\rrbracket_{p\cdot k}(\chi(k)))
$$

根据 $NF$ 的归纳假设，它等于：

$$
\llbracket NF_{p\cdot k}(\delta_1(k)\odot\delta_2(k))\rrbracket_{p\cdot k}(\chi(k))
$$

这正是 $(D_1\diamond_pD_2)$ 在 key $k$ 的 child 操作。若 $k$ 只在 $\delta_1$ 中出现，第二次 merge 不触及该 key；若只在 $\delta_2$ 中出现，第一次 merge 不触及该 key；若二者都不出现，child 保持 $\chi(k)$。四种情况穷尽 $K_p$，因此两个端函数相等。

#### 引理 2：`Compose` 保持 denotation

对任意 $n_1,n_2\in NOp_p$，有：

$$
\llbracket Compose_p(n_1,n_2)\rrbracket_p
=
\llbracket n_2\rrbracket_p\circ\llbracket n_1\rrbracket_p
$$

证明按上表五种情形逐项检查。若右侧操作是 `Remove`，复合结果是常量函数 $s\mapsto\varnothing_p$；若右侧操作是 `Replace(T)`，复合结果是常量函数 $s\mapsto T$；若左侧是 `Remove` 且右侧是 `Merge(D)`，则结果为常量 $merge_p(empty_p,D)$；若左侧是 `Replace(T)` 且右侧是 `Merge(D)`，则结果为常量 $merge_p(T,D)$；若两侧都是 `Merge`，由引理 1 得证。

#### 引理 3：`NF` 保持 denotation

对任意 $e\in Expr_p$，有：

$$
\llbracket NF_p(e)\rrbracket_p=\llbracket e\rrbracket_p
$$

证明按 $e$ 的语法结构归纳。$e$ 为正规操作时结论显然。$e=e_1\odot e_2$ 时，由归纳假设和引理 2 得证。$e=Lift_k(e')$ 时，$NF_p(e)$ 是 `Merge((\mathbf{1}_p,{k\mapsto NF(e')}))`。对 $\varnothing_p$，两侧都先提升为 $empty_p$；对 $Node_p(\ell,\chi)$，本地部分由 $act_p(\ell,\mathbf{1}_p)=\ell$ 保持不变，key $k$ 处由归纳假设等于 $\llbracket e'\rrbracket(\chi(k))$，其他 key 不变。因此 denotation 相同。

### 6.8 祖先/后代重叠操作的规范化

有了 $Lift$ 和 $NF$ 的构造性定义后，祖先/后代重叠不再是额外假设，而是普通表达式规范化的特例。

令：

$$
D_k(e)=(\mathbf{1}_p,\{k\mapsto NF_{p\cdot k}(e)\})
$$

则：

$$
NF_p(Lift_k(e))=Merge(D_k(e))
$$

后代操作发生在 ancestor `Merge(D)` 之后时：

$$
NF_p(Merge(D)\odot Lift_k(e))=Merge(D\diamond_pD_k(e))
$$

展开到 child map，就是：

$$
(D\diamond_pD_k(e)).\delta(k)=
\begin{cases}
NF_{p\cdot k}(D.\delta(k)\odot NF_{p\cdot k}(e)), & k\in Dom(D.\delta) \\
NF_{p\cdot k}(e), & k\notin Dom(D.\delta)
\end{cases}
$$

后代操作先发生、ancestor `Merge(D)` 后发生时：

$$
NF_p(Lift_k(e)\odot Merge(D))=Merge(D_k(e)\diamond_pD)
$$

若 $D$ 触及同一个 child key，则该 key 下得到 $NF(NF(e)\odot D.\delta(k))$；若 $D$ 不触及该 key，则 lift 出来的 child 操作保留，并与 $D$ 的其他 child 操作按稳定 key 合并到同一个有限 map 中。

后代操作发生在 ancestor `Replace(T)` 之后时：

$$
NF_p(Replace(T)\odot Lift_q(e))
=
Replace(\llbracket Lift_q(e)\rrbracket_p(T))
$$

后代操作发生在 ancestor `Remove` 之后时：

$$
NF_p(Remove\odot Lift_q(e))
=
Replace(\llbracket Lift_q(e)\rrbracket_p(\varnothing_p))
$$

这是一种端函数语义下的设计选择，含义是“删除之后的后代操作可以在空虚拟祖先上重建子树”。它不同于第四节坐标 tombstone 语义中“祖先 $\bot$ 持续屏蔽后代，直到同一祖先坐标被覆盖”的规则。实际 DSL 必须选择其中一种语义并保持一致；不能在同一证明链中混用二者。

一个容易误读的特例是：

$$
NF_p(Remove\odot Lift_k(Remove))=Replace(empty_p)
$$

而不是 `Remove`。因为 `Lift_k` 在 $\varnothing_p$ 上会先提升为空虚拟祖先，再删除 child $k$。最终这个 $empty_p$ 是否投影为不存在节点，必须由确定性的 $Norm_S/Pr_S$ 规定；若工程语义要求“父节点删除后，后代 remove 不重建父节点”，则需要采用另一套 denotation 并重新证明 `NF` soundness。

反过来，若 ancestor `Remove` 或 `Replace(T)` 发生在后代操作之后，它们是常量函数，因此覆盖此前后代操作：

$$
NF_p(Lift_q(e)\odot Remove)=Remove
$$

$$
NF_p(Lift_q(e)\odot Replace(T))=Replace(T)
$$

若两个 lift 作用于不同稳定 key，例如 $k_1\ne k_2$，则它们分别更新有限 children map 的不同 key，denotation 可交换：

$$
\llbracket Lift_{k_1}(e_1)\odot Lift_{k_2}(e_2)\rrbracket_p
=
\llbracket Lift_{k_2}(e_2)\odot Lift_{k_1}(e_1)\rrbracket_p
$$

规范化结果是一个 child map 同时包含 $k_1,k_2$ 的 `Merge`。数学上 map 无顺序；序列化时按稳定 key 的全序排序，不能使用物理数组下标或遍历哈希表的偶然顺序。

这些规则保证规范形中不会出现“ancestor `Remove/Replace` 与 descendant patch 并列悬挂”的非法形态。所有重叠操作都被吸收到同一个祖先操作的 denotation 中。

### 6.9 `merge/remove/replace` 与 children 树结构的结合律定理

定义语义等价关系：

$$
e\equiv_p e' \quad\Longleftrightarrow\quad
\llbracket e\rrbracket_p=\llbracket e'\rrbracket_p
$$

设 $S_p$ 是第 6.4 节的稳定 key 潜在树状态空间，$Expr_p$ 是第 6.6 节定义的有限表达式语言，且本地 carrier $(M_p,\diamond^{loc}_p,\mathbf{1}_p)$ 满足第 6.4 节的作用律。则对任意 $e_1,e_2,e_3\in Expr_p$：

$$
(e_1\odot e_2)\odot e_3 \equiv_p e_1\odot(e_2\odot e_3)
$$

证明如下：

$$
\llbracket (e_1\odot e_2)\odot e_3\rrbracket_p
=
\llbracket e_3\rrbracket_p\circ\llbracket e_2\rrbracket_p\circ\llbracket e_1\rrbracket_p
$$

$$
=
\llbracket e_1\odot(e_2\odot e_3)\rrbracket_p
$$

中间等式只使用函数复合结合律。由引理 3，任意括号化后再执行 $NF$ 都保持同一个 denotation，因此：

$$
NF_p((e_1\odot e_2)\odot e_3)
\equiv_p
NF_p(e_1\odot(e_2\odot e_3))
$$

如果希望把“语法等于”也作为定理陈述，应在 $NOp_p$ 上取 denotation 商：

$$
\overline{NOp}_p=NOp_p/{\equiv_p}
$$

并定义：

$$
[n_1]\,\bar\odot\,[n_2]=[Compose_p(n_1,n_2)]
$$

引理 2 和 denotation 等价关系保证该定义与代表元选择无关。具体地，若 $n_1\equiv_p n'_1$ 且 $n_2\equiv_p n'_2$，则：

$$
\llbracket Compose_p(n_1,n_2)\rrbracket_p
=
\llbracket n_2\rrbracket_p\circ\llbracket n_1\rrbracket_p
=
\llbracket n'_2\rrbracket_p\circ\llbracket n'_1\rrbracket_p
=
\llbracket Compose_p(n'_1,n'_2)\rrbracket_p
$$

因此 $Compose_p(n_1,n_2)\equiv_p Compose_p(n'_1,n'_2)$，商代数操作 $\bar\odot$ 与代表元选择无关。函数复合结合律保证 $\bar\odot$ 在商代数上严格结合。因此，`merge/remove/replace` 以及通过 stable-key children 表达的祖先/后代 patch，在语义商上构成结合的预合并代数。

工程实现若还要求 byte-for-byte 的规范 delta 文本，需要额外提供确定性的 $Canon_p:\overline{NOp}_p\to NOp_p$，例如固定本地差量代表元、按 stable key 排序 children、把 denotation 相同的常量删除折叠到同一代表元。此时可得到：

$$
Canon_p([NF_p((e_1\odot e_2)\odot e_3)])
=
Canon_p([NF_p(e_1\odot(e_2\odot e_3))])
$$

这个字面相等是规范化工程层的额外性质；本文结合律证明所需的数学结论是上面的 $\equiv_p$ 和商代数结合律。任意等价的预合并结果作用到同一个基础潜在树 $s\in S_p$ 上，得到同一个潜在树；在根状态上再经过确定性的 $Final_S:S\to Result$，得到同一个 `Ok(Model)` 或同一个规范化 `Err(ErrorSet)`。

### 6.9a 端函数 carrier 的跨层次交错示例

以下示例与第 5.1a 节对应，验证端函数 carrier 在 `merge/remove/replace` 跨层次交错时保持结合律。基础模型同前：Page 含 Panel p1（含 Field f1、f2）和 Panel p2（含 Field f3）。

**示例 1：remove 后 merge 重建（同一 stable key）。** 使用第 6.8 节的 NF 规则：

$$
NF_p(\text{Remove}\odot\text{Lift}_{\text{p1}}(e))
=
\text{Replace}(\llbracket\text{Lift}_{\text{p1}}(e)\rrbracket_p(\varnothing_p))
$$

其中 $e$ 是对 p1 子树的 merge 操作（包含新增 Button btn）。$\text{Lift}_{\text{p1}}$ 在 $\varnothing_p$ 上创建空虚拟 Panel，然后 Merge 写入 btn。最终 `Replace` 包含一个带 btn 的 Panel p1。

两种括号化的验证：
- 左结合 $(\Delta_1\odot\Delta_2)\odot\Delta_3$：先 NF 前两个得到 `Replace(Node({btn}))`，再与 Δ₃ 复合。
- 右结合 $\Delta_1\odot(\Delta_2\odot\Delta_3)$：先 NF 后两个，再与 Δ₁ 复合。

两者 denotation 都是 $\llbracket\Delta_3\rrbracket\circ\llbracket\Delta_2\rrbracket\circ\llbracket\Delta_1\rrbracket$，由函数复合结合律必然相等。

**示例 2：replace 后 merge 覆盖。** Δ₁ 的 `Replace(T)` 将 Panel p1 替换为仅含 f1 的子树；Δ₂ merge Panel p1 恢复 f2。

$$
NF_p(\text{Replace}(T)\odot\text{Lift}_{\text{p1}}(\text{Merge}(\{f2\})))
=
\text{Replace}(\llbracket\text{Lift}_{\text{p1}}(\text{Merge}(\{f2\}))\rrbracket_p(T))
$$

NF 把后代 merge 吸收到 `Replace` 的 denotation 内：先在 $T$（含 f1）上执行 merge f2，得到含 f1+f2 的子树。两个方向的 NF 规则一致：

$$
NF_p(\text{Replace}(T)\odot\text{Lift}_q(e))
=
\text{Replace}(\llbracket\text{Lift}_q(e)\rrbracket_p(T))
$$

无论 $\text{Replace}$ 在左还是在右（但右侧时为常量覆盖），NF 都给出确定结果。

**示例 3：三级交错——ancestor replace、middle remove、descendant merge。** 与第 5.1a 节示例 3 同构，但用端函数解释。

Δ₁ = Page 层 `Replace(T_page)`（含 p1/f1）；Δ₂ = Page 层 `Lift_p1(Remove)`；Δ₃ = Page 层 `Lift_p1(Merge({f1', btn}))`。

逐步应用：
1. $\llbracket\Delta_1\rrbracket(s_0)$：Page 被 Replace 为 $T_{\text{page}}$（含 p1/f1，无 p2）。
2. $\llbracket\Delta_2\rrbracket$：在 p1 上 Remove → p1 变为 $\varnothing$。
3. $\llbracket\Delta_3\rrbracket$：Lift p1 从 $\varnothing$ 创建空虚拟节点，Merge 写入 f1'（required=true）和 btn。

最终状态：Page 含 Panel p1（含 Field f1 with required=true + Button btn），无 p2。

预合并 NF 逐步：
$$
NF(\Delta_1\odot\Delta_2):\quad
\text{Replace}(T_{\text{page}})\odot\text{Lift}_{\text{p1}}(\text{Remove})
$$
由 NF 规则，`Replace` 的后代 `Lift(Remove)` 被吸收：
$$
= \text{Replace}(\llbracket\text{Lift}_{\text{p1}}(\text{Remove})\rrbracket(T_{\text{page}}))
= \text{Replace}(T_{\text{page}}[\text{p1}\mapsto\varnothing])
$$

然后与 Δ₃ 复合：
$$
NF(\text{Replace}(T')\odot\text{Lift}_{\text{p1}}(\text{Merge}(\{f1',\text{btn}\})))
= \text{Replace}(\llbracket\text{Lift}_{\text{p1}}(\text{Merge}(\{f1',\text{btn}\}))\rrbracket(T'))
$$

其中 $T'=T_{\text{page}}[\text{p1}\mapsto\varnothing]$。$\text{Lift}_{\text{p1}}$ 在 p1=$\varnothing$ 上创建虚拟空节点，然后 Merge 写入 f1' 和 btn。最终 `Replace` 包含 p1 含 f1'+btn 的 Page。

右结合 $NF(\Delta_1\odot(\Delta_2\odot\Delta_3))$ 得到的 denotation 完全相同，因为：
$$
\llbracket\Delta_3\rrbracket\circ\llbracket\Delta_2\rrbracket\circ\llbracket\Delta_1\rrbracket
=
\llbracket(\Delta_1\odot\Delta_2)\odot\Delta_3\rrbracket
=
\llbracket\Delta_1\odot(\Delta_2\odot\Delta_3)\rrbracket
$$

**示例 4：remove 后代后 merge 恢复、但 ancestor replace 覆盖全部。**

```xml
<!-- Δ₁：replace Panel p1 为空面板 -->
<Page x:override="merge">
  <Panel id="p1" x:override="replace" />
</Page>

<!-- Δ₂：remove Field f1 -->
<Page x:override="merge">
  <Panel id="p1" x:override="merge">
    <Field id="f1" x:override="remove" />
  </Panel>
</Page>

<!-- Δ₃：merge Field f1 恢复，新增 Field f3 -->
<Page x:override="merge">
  <Panel id="p1" x:override="merge">
    <Field id="f1" x:override="merge" label="名称" />
    <Field id="f3" />
  </Panel>
</Page>
```

端函数逐步：
1. Δ₁ 的 `Replace(empty)` 清空 p1（f1、f2 均消失）。
2. Δ₂ 的 `Lift_{f1}(Remove)` 在 p1 上执行：从空 children map 中 lift f1 得虚拟空节点，再 Remove → $\varnothing$。实际无效果。
3. Δ₃ 的 `Lift_{f1}(Merge)` 和 `Lift_{f3}(Merge)`：f1 从虚拟空节点 merge 得到带 label 的 Field，f3 新增。

预合并 $(\Delta_1\odot\Delta_2)$：
$$
NF(\text{Lift}_{\text{p1}}(\text{Replace}(\varepsilon))\odot\text{Lift}_{\text{p1}}(\text{Lift}_{\text{f1}}(\text{Remove})))
$$

后代 `Replace(ε)` 与后代 `Lift_{f1}(Remove)` 复合时，`Replace` 是常量函数：
$$
NF(\text{Replace}(\varepsilon)\odot\text{Lift}_{\text{f1}}(\text{Remove}))
=
\text{Replace}(\llbracket\text{Lift}_{\text{f1}}(\text{Remove})\rrbracket(\varepsilon))
= \text{Replace}(\varepsilon)
$$

空子树 f1 Remove 后仍是空。再与 Δ₃ 复合：
$$
NF(\text{Replace}(\varepsilon)\odot\text{Lift}_{\text{f1}}(\text{Merge}(\{label\}))\odot\text{Lift}_{\text{f3}}(\text{Merge}))
$$

`Replace(ε)` 的后代 `Lift_{f1}(Merge)` 在空子树上执行：f1 不存在 → 提升为虚拟空 → merge 写入 label。`Lift_{f3}(Merge)` 新增 f3。最终 p1 含 f1（带 label）和 f3。

右结合路径：先 $NF(\Delta_2\odot\Delta_3)$，再与 Δ₁ 复合。Δ₂ 的 `Lift_{f1}(Remove)` 与 Δ₃ 的 `Lift_{f1}(Merge)` 先复合为 `Lift_{f1}(Remove⊙Merge)`，由 NF 规则：在基础有 f1 时，Remove 后 Merge 等价于 Replace(空+merge)=Replace(f1{label})。再与 Δ₁ 的 `Replace(ε)` 复合：`Replace(ε)` 的后代吸收所有操作 → `Replace(ε[f1→虚拟空{label}, f3→新增])` = 同一结果。

函数复合结合律保证两种路径的 denotation 完全一致。

### 6.10 有序列表与插入约束

有序列表是最容易破坏结合律的地方。若用物理下标作为坐标，则在列表头部插入一个元素会改变后续所有元素的坐标，导致差量不再稳定。因此 GRC 需要把列表拆成两部分：

- 元素身份：由稳定键确定，使用映射空间合并。
- 元素顺序：由顺序约束表达，例如 `x:after="stockChecking"`。若某 DSL 或 Nop 规范化路径把 `x:before` / `x:after` 规约到通用 merge 语义中，则顺序约束不应创建第二个同 key 节点；多个约束应共同参与确定性规约，最终每个 stable key 仍只保留一个节点。

顺序约束可以作为一个局部操作集合。若组合方式是“收集约束列表，再在最终规范化阶段执行确定性拓扑排序或稳定排序”，则约束收集本身满足结合律，因为列表连接满足结合律。

若约束之间矛盾，例如同时要求 `A after B` 和 `B after A`，则结构合并仍可得到确定的约束集合，最终规范化或验证阶段报告错误。只要验证函数是确定性的，任意括号化方式都会得到同一个错误结果。删除 anchor、删除目标元素、清理失效约束或把约束转化为错误，也必须在最终统一 $Norm/Validate$ 中完成，除非清理规则本身已经被建模为一个与括号无关的 carrier 操作并完成证明。

因此，有序列表的结合律成立需要以下条件：

- 元素 key 唯一，元素身份不用物理下标表示。
- 顺序操作先收集为结构化约束集合或有序 multiset。
- 删除元素时，其相关顺序约束的清理或保留规则确定。
- 排序和冲突检测在合并链完成后统一执行。
- 同一 anchor 下多个插入约束的 tie-breaker 固定。
- 冲突处理规则确定，或把冲突作为确定的验证失败。

如果约束载体使用集合而不是有序 multiset，则 tie-breaker 不能依赖收集顺序；如果使用有序 multiset，则其连接顺序本身必须作为潜在证据保留到最终 `Norm`。

一个可直接用于本证明的 carrier 是约束记录的自由幺半群：

$$
Ord_p = List(Constraint_p),\quad x\diamond^{ord}_p y = x ++ y,\quad \mathbf{1}^{ord}_p=[]
$$

每个 $Constraint_p$ 必须引用 stable key，而不是物理下标。可发表级证明需要把约束记录至少建模为：

$$
Constraint_p=(sourceOrdinal,localOrdinal,targetKey,relation,anchorKey)
$$

其中 $sourceOrdinal$ 来自 A1 中已确定的差量线性化顺序，$localOrdinal$ 来自同一个差量文件内部的确定性解析顺序，二者都不能由括号化过程生成。若子链先预合并，预合并 artifact 必须保留原子约束的原始 $sourceOrdinal/localOrdinal$ 或等价的稳定 source id/order pair；组合只搬运这些标签，不重新编号。最终 $Norm$ 将完整列表作为输入，按固定算法解析 anchor、删除已失效约束、检测环，并用 stable key、$sourceOrdinal$、$localOrdinal$ 等稳定字段打破并列。若改用集合 carrier，则 $Norm$ 的 tie-breaker 不得依赖收集顺序。

因此，`x:after` 的证明义务不是把列表下标操作证明为结合，而是证明“stable-key 节点合并 + 顺序约束收集 + 终端确定性规约”整体保持同一 denotation。若多个差量触及同一 stable key，节点内容按对应 merge/replace/remove 语义规约，顺序证据按上述 carrier 合并，最终仍投影为单一节点。

这里的约束列表是证明层抽象。Nop 工程实现若在规范化阶段把 `x:before` / `x:after` 规约到通用 merge 语义中，则必须证明规约对同一输入约束集合确定，并且最终仍按 stable key 投影为唯一节点；否则本节只能作为证明义务，不能作为当前实现顺序合并已经结合的证明。

### 6.11 Nop/XLang 操作覆盖矩阵

为避免把抽象演算误读为当前 Nop 实现的全量证明，本节列出标准 `XDefOverride` 与本文定理的对应关系。表中的“已覆盖”指抽象语义已在本文证明；“证明义务”指工程实现还需证明自身确实满足该抽象语义。

| XLang/Nop 特性 | 本文覆盖状态 | 证明义务或边界 |
|---|---|---|
| `merge` 核心递归合并 | 坐标 tombstone carrier 和端函数 carrier 都可覆盖抽象语义 | 当前实现需证明 children stable-key 匹配、属性合并和顺序规约满足相应 carrier 的定义。 |
| 普通 keyed child 顺序合并 | 未由 `x:before/x:after` 约束 carrier 自动覆盖 | 当前实现若使用列表合并或插入算法，需要证明该算法对同一 stable-key 序列和同一线性化差量不依赖括号化；否则只能把输出顺序视为额外证明义务。 |
| `remove` | 坐标 tombstone 与端函数 `Remove` 是两种不同语义 | 当前 XNode 层 `remove` 不等同于第 4/5 节的理想坐标 tombstone carrier。坐标 carrier 中祖先 tombstone 屏蔽后代；端函数 carrier 中 `Remove` 后后续 descendant `Lift` 可重建空虚拟祖先。实际 DSL 必须选定一种语义；当前 Nop 若要引用结合律，应按端函数/override 组合表语义证明，而不是直接引用坐标 tombstone 证明。 |
| `replace` | 端函数常量操作已覆盖 | 若工程语义要求清空旧子树未声明内容，应映射为 `Replace(T)`，不能只用父坐标普通值覆盖。 |
| `append` / `prepend` | 纯序列 concat/pre-concat 抽象 carrier 已覆盖 | 当前 XNode 级实现还合并属性、移动 children、处理 XPL，并且 `APPEND/PREPEND` 与既有 `APPEND/PREPEND` 组合可能经 `OverrideHelper` 规约为 `MERGE_SUPER`。其预合并闭包依赖 `x:super` 存在性、缺失时的错误 denotation，以及相关组合表证明。 |
| `merge-replace` | 可作为“本地属性 merge + body replace”的端函数建模 | 需证明属性 carrier 和 body 常量替换组合满足 `Compose` 表。 |
| `bounded-merge` | 不能由第 6.5 节的普通 `Merge(D)` 自动覆盖 | 若语义是“只保留右侧声明 children”，它会删除输入中未列出的既有 key，而普通 `Merge(D)` 只更新 $Dom(\delta)$。需扩展为 `FilterThenMerge(retainKeys,D)`、wildcard/filter 端函数，或在有限 key 宇宙中显式列出删除，并重新证明闭包与 `Compose/NF` soundness。 |
| `merge-super` / `x:super` | 不由第 6.3 抽象单洞二元组自动覆盖 | 应按 XNode 子树端函数建模；若允许条件、多次或缺失 `super`，需要定义错误 denotation 或排除。 |
| `x:before` / `x:after` | 约束自由幺半群抽象已覆盖 | 需证明实际规范化算法收集完整约束，tie-breaker 来自稳定来源序号，不依赖括号化或哈希遍历。 |
| `x:virtual` | 可作为 carrier 中的潜在本地证据 | 数学 virtual node 不等同于 Nop 实现中最终会移除的 `x:virtual` 属性；若当前实现中 `x:virtual` 的移除或过滤会影响后续 matching、append/prepend 或 remove，则必须纳入端函数状态，或证明其移除时机不影响括号化。 |
| `OverrideHelper` override 组合表 | 本文不证明当前表中所有组合均满足结合律 | 表中 `null` 或注释“存在问题”的组合不应纳入无条件定理；若要覆盖，需为每个组合给出端函数 denotation 与闭包证明。 |

因此，第 6.11 节是实现证明义务清单，不构成对当前 `DeltaMerger`、`OverrideHelper` 或具体 XDef 规范化流程的实现级结合律证明。

## 七、生成器与合并顺序

GRC 的完整公式包含生成器。主文常用 $Y=F(X)\oplus\Delta$ 表示“生成式基线加结构差量”的核心直觉；在本技术报告中，该公式必须按所选 carrier 重新解释：坐标 tombstone carrier 中使用 $\oplus$，局部操作 carrier 中使用 $Apply(s,Fold_{\otimes}(...))$，树状态端函数 carrier 中使用端函数复合作用于生成结果。混合扩展语义的 $\odot$ 组合的是差量函数；端函数最终作用到基础状态上，而不是把基础状态也当作 $\odot$ 的左操作数。为避免 carrier 混用，下面分别记坐标编码为 $E_P$，树状态编码为 $E_S$。

$$
Y = Final_P(E_P(F(X))\oplus \Delta_P) \quad\text{或}\quad
Y = Final_O(Apply(s_F,\Delta_O)) \quad\text{或}\quad
Y = Final_S((\llbracket e\rrbracket)(E_S(F(X))))
$$

结合律证明本身只针对结构合并算子或端函数复合。若生成器参与合并链，需要满足以下条件：

- 生成器在给定输入下确定。
- 生成结果被编码到所选 carrier 的同一空间：$E_P(F(X))\in P$、值状态 $s_F\in ValState_O$，或 $E_S(F(X))\in S$。
- 生成器输出可以视为该 carrier 中的普通潜在模型、值状态、差量或确定性端函数，且类型不能跨 carrier 混用。
- 生成阶段与结构合并阶段的顺序由加载规则确定。

对 Nop/XLang 而言，`x:extends`、`x:gen-extends`、当前节点和 `x:post-extends` 必须先被确定性线性化。例如主论文中给出的顺序在坐标 carrier 下可写为：

$$
A \oplus B \oplus C \oplus D \oplus currentNode \oplus E \oplus F
$$

其中 $A,B$ 来自多值 `x:extends`，$C,D$ 来自 `x:gen-extends`，$E,F$ 来自 `x:post-extends`。在端函数 carrier 下，令 $G_S^0\in S$ 为加载链开始前的初始树状态，同一顺序应写为：

$$
Final_S((\llbracket F\rrbracket\circ\llbracket E\rrbracket\circ\llbracket currentNode\rrbracket\circ\llbracket D\rrbracket\circ\llbracket C\rrbracket\circ\llbracket B\rrbracket\circ\llbracket A\rrbracket)(G_S^0))
$$

结合律只允许改变这个序列的括号，不允许交换元素顺序或让括号化影响生成器执行输入。

在这些条件下，若使用坐标 tombstone carrier，令 $G_P=E_P(F(X))\in P$，后续证明退化为定理 4A 的实例：

$$
(G_P \oplus \Delta_1) \oplus \Delta_2
=
G_P \oplus (\Delta_1 \oplus \Delta_2)
$$

若使用局部操作 carrier，令生成结果为值状态 $s_F\in ValState_O$，只能写为：

$$
Apply(Apply(s_F,\Delta_1),\Delta_2)
=
Apply(s_F,\Delta_1\otimes\Delta_2)
$$

除非额外存在第 6.1 节所述嵌入 $\eta_c$，否则不能把 $s_F$ 或基础模型写成 $\otimes$ 的左操作数。若使用端函数语义，令 $G_S=E_S(F(X))\in S$，先把差量折叠为端函数，再作用到生成结果：

$$
Fold_{\odot}(\Delta_1,\Delta_2)(G_S)
=
(\llbracket \Delta_2 \rrbracket \circ \llbracket \Delta_1 \rrbracket)(G_S)
$$

如果生成器在合并过程中读取外部状态、产生随机结果、依赖当前时间或执行副作用，则它不再是单纯的结构值生成器，不能纳入本文的结合律证明。

## 八、最终定理

本节不再使用单个 $\bullet$ 把不同 carrier 混写成一个定理，而是分别陈述三个定理，并给出一个 GRC 综合推论。每个定理都默认第 0.1 节全局假设。

### 定理 4A：坐标 tombstone carrier 的预合并结合律

设 $P$ 是第二节定义的有限 typed partial map 空间，$\oplus:P\times P\to P$ 是右覆盖合并，$Final_P:P\to Result$ 是一次性作用于最终潜在模型的确定性后处理函数。对任意基础潜在模型 $P_0\in P$ 和任意有限差量序列 $\Delta_1,\dots,\Delta_n\in P$，任意括号化的链式合并都得到同一个潜在模型，并且：

$$
Final_P((((P_0 \oplus \Delta_1) \oplus \Delta_2)\cdots\oplus\Delta_n))
=
Final_P(P_0\oplus NF_{LWW}(\Delta_1,\dots,\Delta_n))
$$

空序列时 $NF_{LWW}$ 为空潜在模型 $\varnothing$。

#### 证明

由定理 1，$(P,\oplus,\varnothing)$ 是幺半群。由推论 2，差量序列的任意括号化都等于逐坐标最右有定义值规约 $NF_{LWW}$。因此潜在模型相同。对相同潜在模型应用同一个确定性 $Final_P$，得到同一个 `Ok(Model)` 或规范化 `Err(ErrorSet)`。

### 定理 4B：局部操作 carrier 的预合并结合律

对每个坐标 $c$，设 $(O_c,\star_c,e_c)$ 是第 6.1 节的局部操作幺半群，且存在右作用 $act_c:W_c\times O_c\to W_c$ 满足作用律。令 $P_O$ 为有限局部操作偏函数空间，$\otimes:P_O\times P_O\to P_O$ 是第 6.1 节定义的稀疏操作组合。

为正式定义作用对象，给定默认值族 $d_c\in W_c$。与第 2.3 节相同，下面的 $\bigcup_c W_c$ 按 tagged union 理解，避免不同坐标值域重叠造成类型歧义。定义值状态空间：

$$
ValState_O=\{s:C\to\bigcup_c W_c\mid s(c)\in W_c,
\{c\mid s(c)\ne d_c\}\text{ 有限}\}
$$

未显式存储的坐标按 $d_c$ 解释。对 $s\in ValState_O$ 和 $\Delta\in P_O$，定义：

$$
Apply(s,\Delta)(c)=act_c(s(c),\widehat{\Delta}(c))
$$

本文要求 $Apply$ 对 $ValState_O$ 封闭：若输入状态和操作差量有限 support，则输出仍只有有限多个坐标偏离默认值。这个闭包可由具体 carrier 证明，例如操作只触及有限坐标且 $act_c(d_c,e_c)=d_c$；若某操作能从默认状态生成无限 support，则不属于本定理前提。若需要可观测结果，令 $Final_O:ValState_O\to Result$ 为确定性后处理函数。

对任意 $s_0\in ValState_O$ 和任意有限操作差量序列 $\Delta_1,\dots,\Delta_n\in P_O$，逐步应用差量与先预合并差量再应用等价：

$$
Apply(\cdots Apply(Apply(s_0,\Delta_1),\Delta_2)\cdots,\Delta_n)
=
Apply(s_0,Fold_{\otimes}(\Delta_1,\dots,\Delta_n))
$$

其中 $Fold_{\otimes}$ 用 $\otimes$ 折叠操作差量，空序列结果为全坐标单位元的稀疏空操作模型。若需要比较最终可观测结果，则对上式两侧应用同一个确定性 $Final_O$。若还存在满足“设置值”语义的嵌入 $\eta_c:V_c\to O_c$，则基础模型也可嵌入 $P_O$，此时才可把上式写成 $P_0\otimes Fold_{\otimes}(\Delta_1,\dots,\Delta_n)$。

#### 证明

第 6.1 节已经证明 $\otimes$ 在操作潜在模型上满足结合律。对任意坐标 $c$，$act_c(act_c(w,x),y)=act_c(w,x\star_c y)$ 说明逐步应用两个操作与先组合再应用等价。对有限序列按长度归纳，并逐坐标应用该作用律，得到上式。若存在 $\eta_c$，基础值嵌入只是把“设置为当前值”视作普通操作，不改变结论。

### 定理 4C：树状态端函数 carrier 的预合并结合律

设 $S=S_\epsilon$ 是第 6.4 节的根状态空间，$Expr_\epsilon$ 是第 6.6 节的有限表达式语言。对任意 $s_0\in S$ 和任意 $e_1,e_2,e_3\in Expr_\epsilon$：

$$
\llbracket (e_1\odot e_2)\odot e_3\rrbracket_\epsilon(s_0)
=
\llbracket e_1\odot(e_2\odot e_3)\rrbracket_\epsilon(s_0)
$$

更一般地，对任意有限表达式序列 $e_1,\dots,e_n$，任意括号化得到同一个 $S\to S$ 端函数。若每个差量都属于 $Expr_\epsilon$，则由第 6.7-6.9 节，预合并结果可规范化为 $NF_\epsilon$ 所给出的正规操作，且与原表达式 denotation 相同。若差量只是任意 $End(S)$ 中的确定性端函数，则仍有函数复合结合律，但不保证存在有限、可序列化、可审计的 DSL delta artifact。

#### 证明

第 6.6 节定义 $\llbracket e_1\odot e_2\rrbracket=\llbracket e_2\rrbracket\circ\llbracket e_1\rrbracket$。函数复合结合律给出任意括号化的端函数相等。若表达式属于 $Expr_\epsilon$，引理 3 给出 $NF$ soundness，商代数证明给出正规操作层面的语义结合。任意 $End(S)$ 函数只使用函数复合结合律，因此只有语义闭包，不自动具有语法闭包。

### 推论 4D：GRC 结构差量合并的可观测一致性

若某条 GRC 合并链先被确定性线性化，并且整条链在同一个 carrier 中解释为定理 4A、4B 或 4C 的对象；若所有投影、规范化、排序和验证都在合并链完成后统一执行，且所选 carrier 的最终函数 $Final_P$、$Final_O$ 或 $Final_S$ 确定，则任意括号化产生同一个最终可观测结果：同一个 `Ok(Model)`，或同一个规范化 `Err(ErrorSet)`。

#### 证明

定理 4A、4B、4C 分别给出所选 carrier 中括号无关的潜在结果、操作作用结果或端函数作用结果。由于对应的 $Final_P$、$Final_O$ 或 $Final_S$ 是确定性函数，对同一潜在结果应用同一后处理必然得到同一可观测结果。树形 `x:extends` 的理想化坐标 carrier 情况由定理 3 嵌入定理 4A；真实 `replace/remove/append/prepend/merge-super/x:before/x:after` 等工程特性只有在按第 6.11 节完成 carrier 映射和证明义务后，才可引用本推论。

## 九、反例边界

为了避免误用定理，下面列出典型不满足本文前提的场景。

### 9.1 文本行 patch

若差量坐标是“第 20 行”，则另一个差量在第 1 行插入文本后，原第 20 行变成第 21 行。坐标漂移导致两个差量无法独立预合并，结合律通常不成立。

### 9.2 每步投影

如果每一步合并后都执行 $Pr$ 并丢弃 tombstone，则后续差量无法再感知先前删除意图。此时结合律可能被破坏。正确方式是在潜在空间中完成整条合并链，再统一投影。

同样，`Norm` 必须作用在潜在模型上，并且在 `Pr` 之前不得丢弃会影响投影或排序的 tombstone、顺序约束、virtual 等证据。否则不同括号化路径可能看到不同的中间证据集。

### 9.3 业务验证插入合并中间

若 $P_0 \oplus \Delta_1$ 暂时不满足业务约束，但 $P_0 \oplus \Delta_1 \oplus \Delta_2$ 最终满足约束，那么在第一步后立即验证会错误中断计算。GRC 的结构合并阶段必须允许“虚时间”中的暂时不一致。最终验证失败的“相同”也应理解为规范化后的错误码、错误坐标或错误集合相同，而不是依赖遍历顺序、对象地址、调用栈等偶然文本完全一致。

### 9.4 非确定生成器

若生成器每次执行都读取当前时间或随机数，则 `F(X)` 不是固定潜在模型。此时不同括号化或不同加载时机可能对应不同输入值，结合律证明不适用。

### 9.5 无稳定键列表

若列表元素只能通过下标定位，则插入、删除、排序都会改变其他元素坐标。此时应先通过 XDef 或显式 `x:id` 引入稳定键，再谈结构差量合并。

### 9.6 父节点删除后的重建语义

如果基础模型有 `/A/B=old`，差量 $\Delta_1$ 删除 `/A`，差量 $\Delta_2$ 重新声明 `/A` 并新增 `/A/C=new`，则在逐坐标 LWW 下，$\Delta_2$ 若覆盖了 `/A` 这个同一坐标的 tombstone，旧的 `/A/B=old` 可能重新暴露。这个结果不破坏结合律，因为任意括号化都会得到同一个潜在模型；但它可能不符合“删除后重建应清空旧子树”的工程直觉。

若需要“重建父节点时清空旧子树”的语义，必须把该操作表示为 `Replace(T)` 或子树级 $S \to S$ 端函数，而不能仅仅表达为父坐标的普通值覆盖加若干子坐标新增。换言之，结合律证明要求工程语义被完整编码到潜在操作中。

### 9.7 覆盖表中未闭包或禁止的操作组合

若工程实现的 override 组合表对某些操作对返回“不允许合并”，或某些组合被标注为存在问题，则这些操作对不属于本文闭包定理的前提。一个最小反例是：若 $A\odot B$ 被判为 forbidden，但 $B\odot C$ 可先规约为 $D$ 且 $A\odot D$ 可成功，则 $(A\odot B)\odot C$ 失败而 $A\odot(B\odot C)$ 成功，结合律立即破坏。

因此，禁止组合只有三种安全处理方式：第一，直接排除出可预合并闭包定理；第二，不在预合并过程中提前失败，而是在最终统一 $Validate$ 中对完整规范表达报告同一个错误集合；第三，把结果类型扩展为 total carrier，例如 `Ok(op) | Err(ErrorSet)`，并定义确定、闭包且结合的错误组合规则，通常需要 `Err` 吸收或错误集合按规范方式合并。若预合并阶段报告错误，错误是否出现和错误集合必须只由原始线性化输入决定，不能由括号化路径决定。

## 十、限制与发表级证明义务

本文证明的是抽象演算的条件化结合律。若将本文作为独立论文或补充制品（supplementary artifact）提交，仍应把以下义务作为限制或未来机械化验证目标明确列出：

- 真实 Nop/XLang 实现符合性：需要逐项证明 `DeltaMerger`、`OverrideHelper`、children matching、顺序规约和 XDef 规范化确实实现第 6.11 节所选 carrier。
- 可序列化 delta artifact 闭包：对任意 $End(S)$ 的提升只保证语义函数可复合，不保证复合结果仍能保存为有限、可审计的 XDSL 差量文本。
- 字面规范形：若要求 byte-for-byte 的规范 delta 输出，需要额外定义 $Canon$，并证明代表元选择、children 排序、本地差量排序都确定。
- 错误等价：本文只要求规范化 `ErrorSet` 相等，不要求调用栈、对象地址、未规范化源码位置或遍历顺序文本相等。
- 机械化证明：本文是手写形式化证明；若面向更严格的理论会议，可把定理 1、6.1、6.7-6.9 和 4A-4C 迁移到 Lean/Coq/Isabelle 或可执行语义测试中。

## 十一、相关工作与参考文献提示

本文是主论文的伴随技术报告，完整相关工作比较见主论文第 8 节。与本文证明最直接相关的方向包括：FOP/FeatureHouse 的 feature structure tree 和 superimposition、Delta-Oriented Programming 的 delta module 与产品线演算、Feature Algebra 对特征组合代数性质的研究、term rewriting 与 denotational semantics 中用规范形和指称等价证明组合性质的方法，以及 CRDT/OT 对并发编辑下收敛合并的不同问题设定。本文处理的是线性化结构差量链的结合律，不处理 CRDT/OT 的并发收敛性。

可与主论文参考文献对应阅读：Apel 等关于 FeatureHouse 和 feature algebra 的工作，Schaefer 等关于 DOP 和 Abstract Delta Modeling 的工作，Foster 等关于 bidirectional transformations/lenses 的工作，以及 Kustomize、OpenUSD 等分层差量工程系统。本文的证明义务也可视为这些工作中“先定义制品演算，再证明组合律”传统在 GRC/XLang 语境下的专门化。

## 十二、结论

GRC 差量合并满足结合律的严格含义是：在由 DSL/XDef 提供的稳定语义坐标系中，把基础模型和差量都表示为同一个 carrier 中的对象，并在结构层完成确定性的逐坐标 LWW 覆盖合并、局部操作预合并，或完成树状态端函数复合，则差量链的括号化不影响最终潜在结果或端函数 denotation。最终投影、规范化和验证只要在合并完成后统一执行，就不会破坏这一结论。

因此，本文完成的是一个**条件化的形式化证明**：核心 LWW 覆盖语义已被直接证明；扩展语义则必须先被嵌入同一个局部幺半群或同一个潜在状态空间端函数代数，才享有同样的结合律。它不是对任何尚未形式化的实际 `x:extends` 特性作无条件承诺，也不是对当前 Nop `XDefOverride` 所有组合分支的实现级证明。

这一定理解释了 GRC 的关键工程收益：

- 满足闭包义务的差量可以独立于基线进行预合并。
- 多个客户定制、版本升级和局部补丁可以先按同一合并语义规约为单一差量。
- 合并复杂度可以局部化到受影响坐标。
- 运行时只需要面对已经规范化和验证后的静态模型。

因此，结合律不是 GRC 的修辞性比喻，而是其“结构空间设计正确时”能够成立的核心代数性质。它成立的代价也很清楚：必须主动设计稳定坐标系，必须在潜在结构空间中保持删除和顺序约束等中间证据，必须把业务验证与结构合并分阶段隔离。
