# GRC 差量合并结合律的条件化形式化证明

本文给出广义可逆计算（GRC）中差量合并算子满足结合律的形式化证明。证明目标不是断言任意 patch、任意后处理过程、任意业务语义都天然满足结合律，而是精确定义一个适合 GRC 的结构合并空间，并证明在该空间中差量可以独立于基线进行预合并。

核心结论如下：

$$
(P_a \oplus P_b) \oplus P_c = P_a \oplus (P_b \oplus P_c)
$$

其中 $P_a,P_b,P_c$ 是定义在同一语义坐标系上的潜在模型或差量，$\oplus$ 是核心的右侧覆盖合并算子。对追加、环绕、顺序约束、replace/remove 等扩展语义，本文不把它们强行塞入 LWW 规约，而是给出两种严格处理方式：若扩展只发生在互不冲突的单坐标上，可用局部幺半群提升出的 $\otimes$；若存在混合操作或祖先/后代重叠作用域，则统一解释为潜在状态空间上的确定性端函数，用函数复合保证结合律。最终可观测模型由投影、规范化和验证阶段得到。结合律要求合并链先在潜在结构空间中完成，不能在每一步之后立刻丢弃删除标记或执行有损投影。

## 一、证明范围

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

坐标集合带有前缀关系 $\preceq$。若 $d \preceq c$，则 $d$ 是 $c$ 的祖先坐标。例如，列节点坐标是该列属性坐标的祖先。

稳定坐标来自 DSL 元模型，而不是物理文本位置。对树中同级节点，应优先使用 `name`、`id`、`value`、`x:id` 或 XDef 指定的唯一键。若只能使用数组下标，则坐标会随插入和重排漂移，不属于本文证明的稳定坐标空间。

### 2.2 值域与删除标记

对每个坐标 $c \in C$，令 $V_c$ 为该坐标允许出现的值集合。引入一个不属于任何 $V_c$ 的特殊标记 $\bot_c$，表示删除该坐标处的信息。

为简化记号，下文统一写作 $\bot$。但类型上应理解为每个坐标有自己的删除标记。

定义扩展值域：

$$
A_c = V_c \cup \{\bot\}
$$

注意：$\bot$ 表示“定义为删除”，不同于 `undef`。`undef` 表示该潜在模型在某坐标上没有定义。

### 2.3 潜在模型

潜在模型是一个有限偏函数：

$$
p: C \rightharpoonup \bigcup_{c \in C} A_c
$$

且当 $p(c)$ 有定义时，必须满足 $p(c) \in A_c$。

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

对潜在模型 $p$，定义删除闭包谓词：

$$
D^+(p)=\{c \in C \mid \exists d \in Dom(p), d \preceq c \land p(d)=\bot\}
$$

这里 $D^+(p)$ 可视为谓词集合，不要求实际枚举整棵无限坐标树。实际投影只会在 $Dom(p)$ 的有限定义域上产生输出，因此投影结果仍是有限表示。

投影结果为：

$$
Pr(p)(c)=
\begin{cases}
p(c), & c \in Dom(p) \land c \notin D^+(p) \land p(c) \in V_c \\
\text{undef}, & \text{otherwise}
\end{cases}
$$

含义是：如果某坐标或其祖先坐标在最终潜在模型中被标记为删除，则该坐标在可观测模型中不存在。

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

需要强调，定理 2 证明的是“最终投影”的结合律可观测一致性，而不是以下更强但一般不成立的式子：

$$
Pr(Pr(p \oplus q) \oplus r)=Pr(p \oplus q \oplus r)
$$

原因是 $Pr$ 可能丢弃删除标记和被删除子树等中间证据。

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

- 节点存在：$c/\#node \mapsto tagName$。
- 属性值：$c/@attr \mapsto value$。
- 文本内容：$c/\#text \mapsto text$。
- 子节点：用父坐标、子标签和稳定键形成子坐标，例如 $c/column[@name='status']$。
- 顺序信息：不直接用数组下标做身份坐标，而是用稳定键加顺序约束或规范化排序规则表达。

设潜在树集合为 $Tree^P$，其中允许 tombstone、virtual 节点和顺序约束等结构证据。编码函数为偏函数：

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

为了与第四节保持一致，这里的树合并函数必须理解为**坐标 tombstone carrier 上的潜在树合并**，记为 $MergeTree^P(T,D)$。它的结果不是已经丢弃 tombstone 的可观测树，而是仍保留 `remove`、`virtual`、顺序约束等中间证据的潜在树。工程实现若在某一步直接返回物理树或 `NULL` 并丢弃删除证据，则不能把该结果继续作为本节结合律中的中间项；它只能作为整条合并链结束后的投影实现，或转入第六节的端函数 carrier 重新解释。

下面的等价性只对一种显式的坐标化潜在树成立：`remove` 不物理删除左侧子树坐标，而是在被删除子树根坐标写入 $\bot$；旧后代坐标仍可保留在潜在表示中，并由最终 $Pr$ 的删除闭包屏蔽。若某递归树合并实现直接从 children map 中移除节点并丢弃其旧后代，则它不满足本节的 $E(MergeTree^P(T,D))=E(T)\oplus E(D)$，只能作为最终投影或第六节端函数语义的一种实现。

定义潜在树合并函数 $MergeTree^P(T,D)$。对核心 merge/remove 语义，有：

$$
E(MergeTree^P(T,D)) = E(T) \oplus E(D)
$$

这里右侧的 $\oplus$ 是第 2.4 节定义的坐标覆盖合并。

#### 证明思路

对树的高度做结构归纳。

基础情形：叶子节点没有子节点。树合并只涉及节点自身、属性和文本。它们都被编码为固定坐标上的值，合并规则就是右侧覆盖左侧，因此等价于 $\oplus$。

归纳假设：高度小于 $h$ 的任意子树，递归树合并等价于坐标合并。

归纳步骤：考虑高度为 $h$ 的节点。其属性集合按属性名逐坐标覆盖，等价于 $\oplus$。其子节点先按稳定键分组；对同键子节点调用递归合并，由归纳假设等价于对应子坐标子空间上的 $\oplus$；差量中只存在于右侧的子节点在坐标偏函数中表现为新增定义；删除节点表现为该子节点坐标的 $\bot$。由于各子坐标空间互不重叠，整体合并就是这些坐标子空间合并的并集。故高度为 $h$ 的树合并也等价于 $\oplus$。

由数学归纳法，任意有限树的核心递归合并都等价于坐标偏函数合并。

### 定理 3：核心 `x:extends` 潜在树合并满足结合律

对任意三棵可编码为同一稳定坐标空间中潜在模型的 XDSL 树或差量 $A,B,C$，若合并只使用核心 merge/remove 语义且中间结果保留 tombstone 等潜在证据，则：

$$
E(MergeTree^P(MergeTree^P(A,B),C))
=
E(MergeTree^P(A,MergeTree^P(B,C)))
$$

这里直接以编码后的潜在模型相等作为结论，不声明源文本、属性顺序、空白、注释或语法糖层面的相等。最终可观测树在一次性投影后也相等。

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

因此核心 `x:extends` 潜在树合并满足结合律。

## 六、扩展合并语义的形式化处理

真实 XDSL 不只有覆盖和删除，还可能包含追加、前置、环绕、替换、有序插入等语义。扩展语义的证明不能只列举若干孤立例子后直接推广到全部 XLang 语义；必须说明这些异质操作如何落到同一个结合结构中。

本文采用两层处理：

- 对互不重叠、只在单坐标上组合的扩展操作，使用局部幺半群提升定理。
- 对 `replace/remove/merge`、祖先/后代重叠、混合操作等跨坐标语义，统一解释为潜在状态空间上的确定性端函数，结合律来自函数复合。

### 6.1 局部结合算子定理

对每个坐标 $c$，设存在一个局部操作集合 $O_c$，并有二元组合 $\star_c:O_c \times O_c \to O_c$ 和单位元 $e_c \in O_c$，使 $(O_c,\star_c,e_c)$ 构成幺半群，即满足闭包、结合律和左右单位元律：

$$
(x \star_c y) \star_c z = x \star_c (y \star_c z)
$$

$$
e_c \star_c x = x \star_c e_c = x
$$

此时潜在模型存储的是局部操作而不一定是最终值。普通基础值可通过嵌入函数 $\eta_c:V_c \to O_c$ 放入同一个操作空间。

为了处理偏函数的未定义情况，将未定义解释为单位元：

$$
\widehat{p}(c)=
\begin{cases}
p(c), & c \in Dom(p) \\
e_c, & c \notin Dom(p)
\end{cases}
$$

定义模型级合并 $\otimes$：

$$
(p \otimes q)(c)=
\begin{cases}
\widehat{p}(c) \star_c \widehat{q}(c), & \widehat{p}(c) \star_c \widehat{q}(c) \ne e_c \\
\text{undef}, & \text{otherwise}
\end{cases}
$$

等价地，也可以显式保留定义域并集上的单位元；本文采用“单位元不写入稀疏模型”的规范表示。

则 $\otimes$ 满足结合律。

#### 证明

仍按任意坐标 $c$ 做逐点证明。对左侧有：

$$
\widehat{(p \otimes q) \otimes r}(c)=(\widehat{p}(c) \star_c \widehat{q}(c)) \star_c \widehat{r}(c)
$$

对右侧有：

$$
\widehat{p \otimes (q \otimes r)}(c)=\widehat{p}(c) \star_c (\widehat{q}(c) \star_c \widehat{r}(c))
$$

二者由 $\star_c$ 的结合律相等。若结果等于单位元，两边在稀疏规范表示中同为 `undef`；若不等于单位元，两边写入同一个操作值。故模型级合并 $\otimes$ 满足结合律。

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

### 6.3 around/super 环绕语义

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

当不同操作会影响同一棵子树，或祖先坐标与后代坐标同时出现操作时，逐坐标独立性不再是一个安全假设。此时应选择一个潜在树状态空间，并把差量解释为这个状态空间上的确定性端函数。

对任意路径 $p$，令 $K_p$ 为该路径下同级 children 的稳定 key 集合。一个 key 可以形式化为 `(role, tag, keyName, keyValue)`，其中 `keyValue` 来自 XDef 或 DSL 元模型指定的 `name`、`id`、`value`、`x:id` 等稳定身份。物理数组下标不属于 $K_p$。

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

注意：本节的 $S_p$ 端函数语义与第四、五节的坐标 tombstone 语义是两种不同 carrier。第四节的 $\bot$ 用于在坐标偏函数中保留“删除祖先后屏蔽旧后代”的证据；本节的 $\varnothing_p$ 是端函数作用后的子树状态，不承诺区分“从未存在”和“被删除”。因此，不能把第六节的中间 $S_p$ 状态再当作第四节的 tombstone 潜在模型使用。若某工程实现需要在树状态自身中保留删除证据，应把状态空间扩展为 $S_p ::= Absent_p \mid Tomb_p \mid Node_p(\ell,\chi)$，并重新定义 `put_k`、`Pr` 和 `Norm`。

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

其中 `ErrorSet` 是规范化后的错误集合，不依赖遍历顺序、对象地址或调用栈文本。最终可观测结果由一个总的确定性函数给出：

$$
Final = Validate \circ Pr \circ Norm : S \to Result
$$

其中 $Norm$ 必须作用于潜在状态并保留会影响后续 $Pr$ 或排序的 tombstone、virtual、顺序约束等证据。

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

该定义是良基的：在 $D_1\diamond_pD_2$ 的递归分支中，$NF$ 只作用于两个外层 `Merge` 的严格 child 子项 $\delta_1(k)$ 和 $\delta_2(k)$。以有限语法树大小为度量，每次递归都去掉至少一个祖先层级，因此不会产生循环定义。

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

`diamond`、`Compose` 与 `NF` 是按子树深度和表达式大小做的同步归纳定义：固定最大深度 $h$，先假设所有严格 child 路径上的 $NF$ 已定义且保持 denotation，再定义当前路径上的 $diamond$、`Compose` 和 $NF$。由于每个实际差量只包含有限深度的语法树，归纳从叶子路径开始，逐层回到根路径。

下面三个引理按“子树最大深度优先、同一深度内按表达式大小”的良基顺序同步证明。引理 1 在严格 child 路径上使用引理 3 的归纳假设；引理 3 在同一路径组合分支使用已由引理 1 建立的 `Compose` 正确性。

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

引理 2 和 denotation 等价关系保证该定义与代表元选择无关：等价代表元有相同端函数，复合后仍有相同端函数。函数复合结合律保证 $\bar\odot$ 在商代数上严格结合。因此，`merge/remove/replace` 以及通过 stable-key children 表达的祖先/后代 patch，在语义商上构成结合的预合并代数。

工程实现若还要求 byte-for-byte 的规范 delta 文本，需要额外提供确定性的 $Canon_p:\overline{NOp}_p\to NOp_p$，例如固定本地差量代表元、按 stable key 排序 children、把 denotation 相同的常量删除折叠到同一代表元。此时可得到：

$$
Canon_p([NF_p((e_1\odot e_2)\odot e_3)])
=
Canon_p([NF_p(e_1\odot(e_2\odot e_3))])
$$

这个字面相等是规范化工程层的额外性质；本文结合律证明所需的数学结论是上面的 $\equiv_p$ 和商代数结合律。任意等价的预合并结果作用到同一个基础潜在树 $s\in S_p$ 上，得到同一个潜在树；再经过确定性的 $Final:S\to Result$，得到同一个 `Ok(Model)` 或同一个规范化 `Err(ErrorSet)`。

### 6.10 有序列表与插入约束

有序列表是最容易破坏结合律的地方。若用物理下标作为坐标，则在列表头部插入一个元素会改变后续所有元素的坐标，导致差量不再稳定。因此 GRC 需要把列表拆成两部分：

- 元素身份：由稳定键确定，使用映射空间合并。
- 元素顺序：由顺序约束表达，例如 `x:after="stockChecking"`。

顺序约束可以作为一个局部操作集合。若组合方式是“收集约束列表，再在最终规范化阶段执行确定性拓扑排序或稳定排序”，则约束收集本身满足结合律，因为列表连接满足结合律。

若约束之间矛盾，例如同时要求 `A after B` 和 `B after A`，则结构合并仍可得到确定的约束集合，最终规范化或验证阶段报告错误。只要验证函数是确定性的，任意括号化方式都会得到同一个错误结果。

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

每个 $Constraint_p$ 必须引用 stable key，而不是物理下标。最终 $Norm$ 将完整列表作为输入，按固定算法解析 anchor、删除已失效约束、检测环，并用 stable key 与约束记录中的确定性来源序号打破并列。若改用集合 carrier，则 $Norm$ 的 tie-breaker 不得依赖收集顺序。

## 七、生成器与合并顺序

GRC 的完整公式包含生成器。下面用 $\bullet$ 作为“当前选定的模型级结构合并算子”的占位符；在核心覆盖语义下 $\bullet=\oplus$，在单坐标局部操作语义下 $\bullet=\otimes$。混合扩展语义使用端函数复合 $\odot$，它组合的是差量函数；端函数最终作用到基础状态上，而不是把基础状态也当作 $\odot$ 的左操作数。

$$
Y = F(X) \oplus \Delta
$$

结合律证明本身只针对结构合并算子或端函数复合。若生成器参与合并链，需要满足以下条件：

- 生成器在给定输入下确定。
- 生成结果被编码到同一语义坐标空间。
- 生成器输出可以视为一个普通潜在模型、差量或确定性端函数。
- 生成阶段与结构合并阶段的顺序由加载规则确定。

在这些条件下，令：

$$
G = E(F(X))
$$

若 $G$ 是潜在模型，后续证明退化为：

$$
(G \bullet \Delta_1) \bullet \Delta_2 = G \bullet (\Delta_1 \bullet \Delta_2)
$$

若使用端函数语义，则先把差量折叠为端函数，再作用到生成结果：

$$
Fold_{\odot}(\Delta_1,\Delta_2)(G)
=
(\llbracket \Delta_2 \rrbracket \circ \llbracket \Delta_1 \rrbracket)(G)
$$

如果生成器在合并过程中读取外部状态、产生随机结果、依赖当前时间或执行副作用，则它不再是单纯的结构值生成器，不能纳入本文的结合律证明。

## 八、最终定理

### 定理 4：GRC 结构差量合并的结合律

设 $C$ 是带稳定前缀关系的语义坐标集合。设所有参与合并的基础模型和差量都可表示为 $C$ 上的有限潜在模型，或差量可解释为潜在状态空间 $S$ 上的确定性端函数。选择以下三种语义之一，并在同一证明链中保持 carrier 一致：

- 核心覆盖语义：模型级算子 $\bullet=\oplus$，其定义见第二节。
- 单坐标扩展语义：模型级算子 $\bullet=\otimes$，每个坐标的操作空间 $(O_c,\star_c,e_c)$ 都是幺半群，且跨坐标副作用已被排除或提升到下一种语义。
- 混合扩展语义：每个差量要么是 $Expr_p$ 中的有限表达式，并由第 6.6-6.9 节给出 denotation 与 $NF$；要么被显式提升为 $End(S)$ 中的确定性端函数，此时只证明函数复合的结合律，不再声称存在第 6.7 节形式的语法规范形。

同时，若使用第五节坐标 tombstone carrier，则树形合并使用第五节定义的潜在树合并 $MergeTree^P$，中间结果保留 tombstone、顺序约束等证据。若使用第六节端函数 carrier，则树形合并使用 $S_p$ 与 $Expr_p$ 的 denotation，不再把中间状态解释为第四节的坐标 tombstone 模型。所有投影、规范化、排序和验证都在合并链完成后统一执行，且最终后处理统一表示为确定性函数 $Final:S\to Result$ 或其坐标模型对应物。

在核心覆盖语义或单坐标扩展语义下，对任意基础模型 $P_0$ 和任意有限差量序列 $\Delta_1,\dots,\Delta_n$，任意括号化方式得到的潜在合并结果相同，并且最终可观测结果相同：

$$
Final((((P_0 \bullet \Delta_1) \bullet \Delta_2) \cdots \bullet \Delta_n))
$$

等于使用同一 $\bullet$ 对差量序列先做任意确定括号化规约后再作用于 $P_0$ 的结果：

$$
Final(P_0 \bullet Fold_{\bullet}(\Delta_1,\dots,\Delta_n))
$$

其中 $Fold_{\bullet}$ 表示用同一结合算子 $\bullet$ 折叠差量序列。若 $\bullet=\oplus$，则 $Fold_{\bullet}$ 正是推论 2 中的 $NF_{LWW}$；若 $\bullet=\otimes$，则它是按局部幺半群进行的折叠，而不是“取最右侧值”。空序列的 $Fold_{\bullet}$ 是对应语义的单位元：核心覆盖语义为空潜在模型，局部幺半群语义为全坐标单位元的稀疏空模型。

在混合扩展语义下，端函数复合的正确表达不是 $P_0 \odot \Delta$，而是折叠差量后作用于基础状态：

$$
Final((Fold_{\odot}(\Delta_1,\dots,\Delta_n))(P_0))
$$

其中 $Fold_{\odot}$ 表示按 denotation 复合差量端函数，空序列结果为恒等函数 $id_S$。任意括号化的端函数复合得到同一个 $S\to S$ 函数，因此作用于同一个 $P_0$ 后得到同一个潜在状态。

若最终验证接受，则二者产生同一个 `Ok(Model)`。若最终验证拒绝，则二者产生同一个规范化的 `Err(ErrorSet)`。

#### 证明

首先，对每个坐标或潜在状态空间，合并语义满足结合律。覆盖语义由定理 1 证明，单坐标扩展语义由 6.1 节证明；混合扩展语义中的差量组合由第 6.6-6.9 节的端函数复合与规范化引理保证。

其次，在 $\oplus$ 或 $\otimes$ 语义下，模型级合并是逐坐标合并。坐标之间除了前缀删除和最终规范化之外没有隐藏的中间副作用。前缀删除由最终潜在模型中的 $\bot$ 决定；规范化和验证在合并链完成后统一执行。因此潜在合并结果的任意括号化相等。在 $\odot$ 语义下，模型级合并不再假设逐坐标独立，而是先由整体端函数复合得到同一个函数，再作用于 $P_0$；因此最终潜在状态也相等。

再次，树形 `x:extends` 的核心潜在合并可编码为同一坐标空间中的潜在模型合并，核心递归合并等价性由 5.2 节的结构归纳证明。`replace`、`remove`、追加、环绕、有序约束等扩展操作只有在已嵌入第 6 节的局部幺半群或统一端函数语义后，才被定理覆盖；未完成这种嵌入的真实工程语义不自动属于本定理。

最后，$Pr$、$Norm$ 和 $Validate$ 都是确定性函数。对同一个潜在合并结果应用同一组确定性后处理，必然得到同一个可观测模型或同一个验证失败。因此最终定理成立。

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

## 十、结论

GRC 差量合并满足结合律的严格含义是：在由 DSL/XDef 提供的稳定语义坐标系中，把基础模型和差量都表示为潜在模型，并在结构层完成确定性的逐坐标 LWW 覆盖合并，或完成由局部幺半群/函数复合定义的扩展合并，则差量链的括号化不影响最终潜在结果。最终投影、规范化和验证只要在合并完成后统一执行，就不会破坏这一结论。

因此，本文完成的是一个**条件化的形式化证明**：核心 LWW 覆盖语义已被直接证明；扩展语义则必须先被嵌入同一个局部幺半群或同一个潜在状态空间端函数代数，才享有同样的结合律。它不是对任何尚未形式化的实际 `x:extends` 特性作无条件承诺。

这一定理解释了 GRC 的关键工程收益：

- 差量可以独立于基线进行预合并。
- 多个客户定制、版本升级和局部补丁可以先按同一合并语义规约为单一差量。
- 合并复杂度可以局部化到受影响坐标。
- 运行时只需要面对已经规范化和验证后的静态模型。

因此，结合律不是 GRC 的修辞性比喻，而是其“结构空间设计正确时”能够成立的核心代数性质。它成立的代价也很清楚：必须主动设计稳定坐标系，必须在潜在结构空间中保持删除和顺序约束等中间证据，必须把业务验证与结构合并分阶段隔离。
