# GRC 抽象差量演算的条件化结合律证明：修正版

## 0. 证明目标与范围

本文证明以下条件化命题：

> 若一条 GRC 差量链被确定性线性化，并且整条链被解释在同一个数学承载空间 carrier 中，则只改变括号、不改变线性顺序时，预合并结果的潜在语义或端函数 denotation 不变。若最终投影、规范化和验证均为确定性函数，并且只在合并链完成后统一执行，则最终可观测结果也不变。

本文区分三类 carrier：

1. **坐标 tombstone carrier**  
   有限 typed partial map 空间 $P$，合并为右覆盖 $\oplus$。

2. **逐坐标局部操作 carrier**  
   每个坐标有局部操作幺半群 $(O_c,\star_c,e_c)$，模型级操作组合为 $\otimes$。

3. **树状态端函数 carrier**  
   潜在树状态空间 $S_p$ 上的确定性端函数 $S_p\to S_p$，差量表达式通过 denotation 解释为函数复合。

本文不证明任意文本 patch、任意真实工程实现、任意 Nop/XLang 当前代码分支都无条件满足结合律。真实实现若要引用本文结论，必须证明其语义映射到本文某一个 carrier，并满足相应闭包、确定性和规范化前提。

---

## 0.1 全局假设

以下假设适用于所有正式定理。

| 编号 | 假设 |
|---|---|
| A1 | 所有模型和差量先被确定性线性化为有限序列；结合律只改变括号，不改变顺序。 |
| A2 | 同一条证明链中的对象必须位于同一个 carrier，不得混用。 |
| A3 | 语义坐标或 stable key 由 DSL/XDef/schema 提供；物理数组下标不是稳定身份。 |
| A4 | 所有潜在模型、差量表达式、children 映射均为有限对象。 |
| A5 | 生成器、排序、规范化、投影、验证在给定输入下确定。随机数、时间、外部 IO 必须显式纳入输入，否则不在定理范围内。 |
| A6 | 合并链在潜在空间中完成；中间不得执行丢弃 tombstone、顺序约束、virtual node 等语义证据的有损投影。 |
| A7 | 最终错误结果用规范化 `ErrorSet` 表示，不包含对象地址、调用栈文本、未规范化 source location、哈希遍历顺序等非语义字段。 |
| A8 | 若要求得到可序列化 delta artifact，必须额外证明差量语言对预合并闭包；任意 $End(S)$ 函数复合只保证语义组合，不自动保证可保存为有限 DSL 文本。 |
| A9 | 纳入预合并定理的操作组合必须在所选 carrier 中是 total 且闭包。实现级 forbidden/null/partial failure 只有被建模为 total error carrier 并证明结合律时，才可进入定理。 |

---

# 一、坐标 tombstone carrier

## 1.1 语义坐标集合

令 $C$ 为语义坐标集合。元素 $c\in C$ 表示 DSL 结构中的稳定语义位置，例如：

\[
/orm/entities/entity[@name='Order']/columns/column[@name='status']/@length
\]

$C$ 带有前缀偏序 $\preceq$，满足自反、传递、反对称。若 $d\preceq c$，则称 $d$ 是 $c$ 的祖先坐标。由于自反，每个坐标也是自身祖先。

树形结构中，路径与坐标区分如下：

- $Path$ 为 stable-key 路径集合；
- 每个路径 $\pi$ 有节点根坐标 $root(\pi)\in C$；
- 节点本地信息、属性、文本和子节点可编码为：
  \[
  root(\pi)/\#node,\quad root(\pi)/@attr,\quad root(\pi)/\#text,\quad root(\pi\cdot k)
  \]
- 前缀关系要求：
  \[
  root(\pi)\preceq root(\pi)/\#node
  \]
  \[
  root(\pi)\preceq root(\pi)/@attr
  \]
  \[
  root(\pi)\preceq root(\pi\cdot k)
  \]

因此，在 $root(\pi)$ 上写 tombstone 会屏蔽整棵子树。

---

## 1.2 值域与 tombstone

对每个坐标 $c\in C$，令 $V_c$ 为该坐标允许的普通值集合。引入删除标记：

\[
\bot_c\notin V_c
\]

定义扩展值域：

\[
A_c=V_c\cup\{\bot_c\}
\]

下文常简写为 $\bot$，但类型上应理解为每个坐标有自己的 $\bot_c$。

注意：

- $\bot_c$ 表示“该坐标被定义为删除”；
- `undef` 表示偏函数在该坐标上没有定义；
- $\bot_c\neq undef$。

---

## 1.3 潜在模型空间

定义潜在模型空间 $P$ 为所有有限 typed partial map：

\[
P=\{p\mid Dom(p)\subseteq_{\mathrm{fin}} C,\ \forall c\in Dom(p),\ p(c)\in A_c\}
\]

若 $c\notin Dom(p)$，则 $p$ 在 $c$ 上没有表达任何信息。

基础模型和差量在代数层没有本质区别，都是 $P$ 中对象。工程上称某个稀疏对象为差量，只是因为它通常只定义少量坐标。

---

## 1.4 右覆盖合并

定义 $\oplus:P\times P\to P$：

\[
(p\oplus q)(c)=
\begin{cases}
q(c), & c\in Dom(q)\\
p(c), & c\notin Dom(q)\land c\in Dom(p)\\
undef, & c\notin Dom(p)\cup Dom(q)
\end{cases}
\]

即：右侧有定义时覆盖左侧；右侧未定义时保留左侧。

显然：

\[
Dom(p\oplus q)=Dom(p)\cup Dom(q)
\]

空潜在模型 $\varnothing$ 满足 $Dom(\varnothing)=\varnothing$。

---

## 1.5 定理 1：$(P,\oplus,\varnothing)$ 是幺半群

### 结论

对任意 $p,q,r\in P$：

\[
(p\oplus q)\oplus r=p\oplus(q\oplus r)
\]

并且：

\[
p\oplus\varnothing=p
\]

\[
\varnothing\oplus p=p
\]

### 证明

两个潜在模型相等，当且仅当它们在每个坐标上取值相同。固定任意 $c\in C$，分三种情况讨论。

#### 情况 1：$c\in Dom(r)$

左侧：

\[
((p\oplus q)\oplus r)(c)=r(c)
\]

右侧中，因为 $c\in Dom(r)$，所以：

\[
(q\oplus r)(c)=r(c)
\]

因此：

\[
(p\oplus(q\oplus r))(c)=r(c)
\]

左右相等。

#### 情况 2：$c\notin Dom(r)$ 且 $c\in Dom(q)$

左侧：

\[
((p\oplus q)\oplus r)(c)=(p\oplus q)(c)=q(c)
\]

右侧中：

\[
(q\oplus r)(c)=q(c)
\]

因此：

\[
(p\oplus(q\oplus r))(c)=q(c)
\]

左右相等。

#### 情况 3：$c\notin Dom(r)$ 且 $c\notin Dom(q)$

此时：

\[
c\notin Dom(q\oplus r)
\]

若 $c\in Dom(p)$，则左右两侧均为 $p(c)$。

若 $c\notin Dom(p)$，则左右两侧均为 `undef`。

三种情况覆盖所有可能，因此：

\[
(p\oplus q)\oplus r=p\oplus(q\oplus r)
\]

单位元性质由 $\varnothing$ 的定义域为空直接得到。

故 $(P,\oplus,\varnothing)$ 是幺半群。证毕。

---

## 1.6 推论：核心覆盖差量链的唯一规约结果

给定有限序列：

\[
p_1,\dots,p_n\in P
\]

定义：

\[
NF_{LWW}(p_1,\dots,p_n)(c)=p_k(c)
\]

其中 $k$ 是满足 $c\in Dom(p_k)$ 的最大下标。若不存在这样的 $k$，则结果为 `undef`。

则任意括号化的链式合并结果都等于：

\[
NF_{LWW}(p_1,\dots,p_n)
\]

尤其：

\[
(((P_0\oplus\Delta_1)\oplus\Delta_2)\cdots\oplus\Delta_n)
=
P_0\oplus NF_{LWW}(\Delta_1,\dots,\Delta_n)
\]

### 证明

由定理 1，$\oplus$ 结合。对每个坐标 $c$，最终值必然来自线性序列中最右侧有定义的对象。因此任意括号化结果一致。证毕。

---

# 二、删除语义与最终投影

## 2.1 删除闭包

定义删除闭包谓词：

\[
D^+(p)=\{c\in C\mid \exists d\in Dom(p),\ d\preceq c,\ p(d)=\bot_d\}
\]

因为 $\preceq$ 自反，所以若 $p(c)=\bot_c$，则 $c\in D^+(p)$。

---

## 2.2 投影函数

定义坐标级投影：

\[
Pr:P\to Obs
\]

其中：

\[
Pr(p)(c)=
\begin{cases}
p(c), & c\in Dom(p),\ c\notin D^+(p),\ p(c)\in V_c\\
undef, & otherwise
\end{cases}
\]

也就是说：

- tombstone 自身不可见；
- tombstone 的后代不可见；
- 只有未被删除闭包屏蔽的普通值可见。

若最终需要真实树模型，还需要确定性的：

\[
Norm_P:P\to P'_P
\]

\[
Pr_P:P'_P\to Obs_P
\]

\[
Validate_P:Obs_P\to Result
\]

并定义：

\[
Final_P=Validate_P\circ Pr_P\circ Norm_P
\]

---

## 2.3 定理 2：最终投影保持结合律结果

对任意 $p,q,r\in P$：

\[
Pr((p\oplus q)\oplus r)=Pr(p\oplus(q\oplus r))
\]

更一般地，对任意确定性 $Final_P:P\to Result$：

\[
Final_P((p\oplus q)\oplus r)=Final_P(p\oplus(q\oplus r))
\]

### 证明

由定理 1：

\[
(p\oplus q)\oplus r=p\oplus(q\oplus r)
\]

两边是同一个潜在模型。确定性函数作用于同一输入，结果相同。证毕。

---

## 2.4 中间投影一般不保持等价

以下命题一般不成立：

\[
Pr(I(Pr(p\oplus q))\oplus r)=Pr(p\oplus q\oplus r)
\]

其中 $I:Obs\to P$ 是某个重新嵌入函数。

取 $c,d\in C$，满足 $c\preceq d$。令：

\[
p(d)=u,\quad q(c)=\bot_c,\quad r(d)=v
\]

一次性合并：

\[
p\oplus q\oplus r=\{c\mapsto\bot_c,\ d\mapsto v\}
\]

由于 $c\preceq d$ 且 $c$ 上有 tombstone，投影后 $d$ 不可见。

但若先投影：

\[
p\oplus q=\{c\mapsto\bot_c,\ d\mapsto u\}
\]

投影得到空可观测模型。若 $I$ 将其嵌回不含 tombstone 的潜在模型，则：

\[
I(Pr(p\oplus q))=\varnothing
\]

再合并 $r$：

\[
Pr(\varnothing\oplus r)
\]

在 $d$ 上有值 $v$。

因此：

\[
Pr(I(Pr(p\oplus q))\oplus r)\neq Pr(p\oplus q\oplus r)
\]

这说明合并链必须在潜在空间中完成，不能中途有损投影。

---

# 三、理想化坐标树编码

## 3.1 树的坐标化编码

设 $Tree^P$ 为潜在树集合，允许 tombstone、virtual node、顺序证据等可被坐标 carrier 表示的信息。

定义偏编码函数：

\[
E:Tree^P\rightharpoonup P
\]

$E$ 只在满足 stable-key 唯一性、schema 约束和有限性条件的树上有定义。

典型编码：

- 路径 $\pi$ 的根：
  \[
  root(\pi)\mapsto present
  \]
- 节点 tag：
  \[
  root(\pi)/\#node\mapsto tagName
  \]
- 属性：
  \[
  root(\pi)/@a\mapsto value
  \]
- 文本：
  \[
  root(\pi)/\#text\mapsto text
  \]
- 删除子树：
  \[
  root(\pi)\mapsto\bot_{root(\pi)}
  \]

若后续重新声明同一根路径 $\pi$，必须显式写：

\[
root(\pi)\mapsto present
\]

否则旧 tombstone 会继续屏蔽后代坐标。

---

## 3.2 replace-like delta 的有限展开限制

坐标 tombstone carrier 可以表达有限展开的 replace-like delta，但前提很强。

若要用坐标 LWW 表达“把子树 $\pi$ 替换为新树 $T$”，则必须：

1. 写入新树中所有需要保留的坐标；
2. 对旧树中所有需要清除、且可能重新暴露的有限旧后代坐标写入 tombstone；
3. 若要解除根 tombstone，必须写入：
   \[
   root(\pi)\mapsto present
   \]

因此，这种 replace-like delta 只有在旧后代集合已知且有限可枚举时成立。

若需要表达“清空未知旧子树”或“与基线无关地整体替换子树”，则不能仅用有限坐标 LWW 表达，应使用后文端函数 carrier 中的：

\[
Replace(T)
\]

---

## 3.3 理想化潜在树合并

定义规范反编码偏函数：

\[
E^{-1}_{can}:P\rightharpoonup Tree^P
\]

满足：

\[
E(E^{-1}_{can}(p))=p
\]

在其定义域上成立。

定义理想化潜在树合并：

\[
MergeTree^P(T,D)=E^{-1}_{can}(E(T)\oplus E(D))
\]

仅当：

\[
T,D\in Dom(E)
\]

且：

\[
E(T)\oplus E(D)\in Dom(E^{-1}_{can})
\]

时有定义。

---

## 3.4 引理：理想化树合并与坐标合并等价

若 $MergeTree^P(T,D)$ 有定义，则：

\[
E(MergeTree^P(T,D))=E(T)\oplus E(D)
\]

### 证明

由定义：

\[
MergeTree^P(T,D)=E^{-1}_{can}(E(T)\oplus E(D))
\]

因此：

\[
E(MergeTree^P(T,D))
=
E(E^{-1}_{can}(E(T)\oplus E(D)))
=
E(T)\oplus E(D)
\]

证毕。

---

## 3.5 定理 3：理想化坐标树合并满足结合律

若 $A,B,C\in Tree^P$ 可编码，且以下对象均在 $E^{-1}_{can}$ 定义域中：

\[
E(A)\oplus E(B)
\]

\[
E(B)\oplus E(C)
\]

\[
(E(A)\oplus E(B))\oplus E(C)
\]

\[
E(A)\oplus(E(B)\oplus E(C))
\]

则：

\[
E(MergeTree^P(MergeTree^P(A,B),C))
=
E(MergeTree^P(A,MergeTree^P(B,C)))
\]

### 证明

由引理：

\[
E(MergeTree^P(MergeTree^P(A,B),C))
=
(E(A)\oplus E(B))\oplus E(C)
\]

由定理 1：

\[
(E(A)\oplus E(B))\oplus E(C)
=
E(A)\oplus(E(B)\oplus E(C))
\]

再由引理反向应用：

\[
E(A)\oplus(E(B)\oplus E(C))
=
E(MergeTree^P(A,MergeTree^P(B,C)))
\]

证毕。

注意：该定理证明的是理想化坐标 tombstone carrier 下的潜在树合并，不是对当前任何具体 XLang/Nop 实现的自动证明。

---

# 四、逐坐标局部操作 carrier

## 4.1 局部操作幺半群

对每个坐标 $c$，令：

- $W_c$ 为该坐标的局部值空间；
- $O_c$ 为该坐标的局部操作集合；
- $\star_c:O_c\times O_c\to O_c$ 为操作组合；
- $e_c\in O_c$ 为单位元。

要求：

\[
(x\star_c y)\star_c z=x\star_c(y\star_c z)
\]

\[
e_c\star_c x=x=x\star_c e_c
\]

即：

\[
(O_c,\star_c,e_c)
\]

是幺半群。

还要求存在右作用：

\[
act_c:W_c\times O_c\to W_c
\]

满足：

\[
act_c(w,e_c)=w
\]

\[
act_c(act_c(w,x),y)=act_c(w,x\star_c y)
\]

---

## 4.2 操作潜在模型

令 $P_O$ 为有限操作偏函数空间：

\[
P_O=\{p\mid Dom(p)\subseteq_{\mathrm{fin}}C,\ \forall c\in Dom(p),\ p(c)\in O_c\}
\]

定义：

\[
\widehat p(c)=
\begin{cases}
p(c), & c\in Dom(p)\\
e_c, & c\notin Dom(p)
\end{cases}
\]

定义模型级操作组合：

\[
(p\otimes q)(c)=
\begin{cases}
\widehat p(c)\star_c\widehat q(c), & \widehat p(c)\star_c\widehat q(c)\neq e_c\\
undef, & \widehat p(c)\star_c\widehat q(c)=e_c
\end{cases}
\]

即单位元不写入稀疏表示。显式单位元和省略单位元必须被视为同一规范表示。

---

## 4.3 定理 4：$(P_O,\otimes,\varnothing)$ 是幺半群

### 证明

固定任意坐标 $c$。

左侧：

\[
\widehat{(p\otimes q)\otimes r}(c)
=
(\widehat p(c)\star_c\widehat q(c))\star_c\widehat r(c)
\]

右侧：

\[
\widehat{p\otimes(q\otimes r)}(c)
=
\widehat p(c)\star_c(\widehat q(c)\star_c\widehat r(c))
\]

二者由 $\star_c$ 的结合律相等。

若结果为 $e_c$，两侧稀疏表示中均为 `undef`；若结果非 $e_c$，两侧写入同一操作值。

单位元性质由 $e_c$ 的左右单位元律得到。

证毕。

---

## 4.4 预合并操作后再作用等价于逐步作用

定义值状态空间。给定默认值 $d_c\in W_c$，令：

\[
ValState_O=
\{s:C\to\bigsqcup_c W_c\mid s(c)\in W_c,\ \{c\mid s(c)\neq d_c\}\text{有限}\}
\]

定义：

\[
Apply(s,\Delta)(c)=act_c(s(c),\widehat\Delta(c))
\]

要求 $Apply$ 对 $ValState_O$ 封闭。

则对任意 $s_0\in ValState_O$ 和有限操作序列 $\Delta_1,\dots,\Delta_n\in P_O$：

\[
Apply(\cdots Apply(Apply(s_0,\Delta_1),\Delta_2)\cdots,\Delta_n)
=
Apply(s_0,Fold_\otimes(\Delta_1,\dots,\Delta_n))
\]

### 证明

对每个坐标 $c$，由作用律：

\[
act_c(act_c(w,x),y)=act_c(w,x\star_c y)
\]

归纳推广到有限序列。逐坐标相等推出状态相等。证毕。

---

# 五、树状态端函数 carrier

本节处理一般树级：

- `merge`
- `bounded-merge`
- `remove`
- `replace`
- ancestor/descendant 重叠
- mixed override 组合

核心思想：所有差量表达式解释为同一个状态空间上的确定性端函数。结合律来自函数复合。

---

## 5.1 潜在树状态空间

对每个路径 $p$，令：

- $K_p$ 为路径 $p$ 下 children 的 stable key 集合；
- $L_p$ 为路径 $p$ 的本地潜在信息空间；
- $M_p$ 为路径 $p$ 的本地差量集合。

要求 $K_p$ 由 stable key 决定，不得使用物理数组下标。

定义状态空间 $S_p$ 为最小归纳集合：

\[
S_p ::= \varnothing_p \mid Node_p(\ell,\chi)
\]

其中：

- $\varnothing_p$ 表示路径 $p$ 处子树不存在；
- $\ell\in L_p$；
- $\chi:K_p\rightharpoonup_{\mathrm{fin}} S_{p\cdot k}$ 是有限 children map；
- 若 $k\notin Dom(\chi)$，约定：
  \[
  \chi(k)=\varnothing_{p\cdot k}
  \]

定义空虚拟节点：

\[
empty_p=Node_p(\ell^0_p,\varnothing)
\]

其中 $\ell^0_p\in L_p$ 是本地空值。

---

## 5.2 children 更新

定义：

\[
put_k(\chi,t)
\]

若 $t\neq \varnothing_{p\cdot k}$，则更新 $k\mapsto t$；若 $t=\varnothing_{p\cdot k}$，则从有限 map 中移除 $k$。

因此 children map 始终有限。

---

## 5.3 本地 carrier

对每个路径 $p$，要求：

\[
(M_p,\diamond^{loc}_p,\mathbf 1_p)
\]

是幺半群，并存在右作用：

\[
act_p:L_p\times M_p\to L_p
\]

满足：

\[
act_p(\ell,\mathbf 1_p)=\ell
\]

\[
act_p(act_p(\ell,\mu_1),\mu_2)
=
act_p(\ell,\mu_1\diamond^{loc}_p\mu_2)
\]

---

## 5.4 正规操作

修正版中加入真正恒等操作 `Id`：

\[
NOp_p ::= Id \mid Remove \mid Replace(T) \mid Merge(D) \mid BoundedMerge(D)
\]

其中：

\[
T\in S_p
\]

\[
D=(\mu,\delta)
\]

\[
\mu\in M_p
\]

\[
\delta:K_p\rightharpoonup_{\mathrm{fin}} NOp_{p\cdot k}
\]

`Id` 的加入是必要的，因为：

\[
Merge((\mathbf 1_p,\varnothing))
\]

在本文的 absent-lift 语义下不是恒等函数。事实上：

\[
Merge((\mathbf 1_p,\varnothing))(\varnothing_p)=empty_p
\]

而真正的恒等函数应满足：

\[
Id(\varnothing_p)=\varnothing_p
\]

---

## 5.5 merge 与 bounded-merge 语义

令 $D=(\mu,\delta)$。

定义：

\[
merge_p:S_p\times D_p\to S_p
\]

若输入为不存在状态：

\[
merge_p(\varnothing_p,D)=merge_p(empty_p,D)
\]

若输入为节点：

\[
merge_p(Node_p(\ell,\chi),(\mu,\delta))
=
Node_p(act_p(\ell,\mu),\chi')
\]

其中：

\[
\chi'(k)=
\begin{cases}
\llbracket\delta(k)\rrbracket_{p\cdot k}(\chi(k)), & k\in Dom(\delta)\\
\chi(k), & k\notin Dom(\delta)
\end{cases}
\]

有限表示中通过 $put_k$ 更新。

定义：

\[
bmerge_p:S_p\times D_p\to S_p
\]

若输入为不存在状态：

\[
bmerge_p(\varnothing_p,D)=bmerge_p(empty_p,D)
\]

若输入为节点：

\[
bmerge_p(Node_p(\ell,\chi),(\mu,\delta))
=
Node_p(act_p(\ell,\mu),\chi^b)
\]

其中：

\[
\chi^b(k)=
\begin{cases}
\llbracket\delta(k)\rrbracket_{p\cdot k}(\chi(k)), & k\in Dom(\delta)\\
\varnothing_{p\cdot k}, & k\notin Dom(\delta)
\end{cases}
\]

因此 `BoundedMerge(D)` 会删除未声明 child。

---

## 5.6 操作 denotation

定义：

\[
\llbracket Id\rrbracket_p(s)=s
\]

\[
\llbracket Remove\rrbracket_p(s)=\varnothing_p
\]

\[
\llbracket Replace(T)\rrbracket_p(s)=T
\]

\[
\llbracket Merge(D)\rrbracket_p(s)=merge_p(s,D)
\]

\[
\llbracket BoundedMerge(D)\rrbracket_p(s)=bmerge_p(s,D)
\]

---

## 5.7 表达式语言

定义表达式：

\[
Expr_p ::= n \mid Expr_p\odot Expr_p \mid Lift_k(e)
\]

其中：

\[
n\in NOp_p
\]

\[
k\in K_p
\]

\[
e\in Expr_{p\cdot k}
\]

组合方向为“先左后右”：

\[
\llbracket e_1\odot e_2\rrbracket_p
=
\llbracket e_2\rrbracket_p\circ \llbracket e_1\rrbracket_p
\]

单步 lift：

\[
\llbracket Lift_k(e)\rrbracket_p(\varnothing_p)
=
\llbracket Lift_k(e)\rrbracket_p(empty_p)
\]

\[
\llbracket Lift_k(e)\rrbracket_p(Node_p(\ell,\chi))
=
Node_p(\ell,put_k(\chi,\llbracket e\rrbracket_{p\cdot k}(\chi(k))))
\]

注意：

\[
Lift_k(Id)
\]

不是全局恒等函数，因为在 $\varnothing_p$ 上它会提升出 $empty_p$。

---

# 六、Compose、diamond 与 NF

## 6.1 同路径组合 Compose

定义：

\[
Compose_p:NOp_p\times NOp_p\to NOp_p
\]

方向仍为“先左后右”。

首先加入 `Id` 规则：

\[
Compose_p(Id,n)=n
\]

\[
Compose_p(n,Id)=n
\]

其他规则如下：

| 组合 | 结果 |
|---|---|
| $n\odot Remove$ | `Remove` |
| $n\odot Replace(T)$ | `Replace(T)` |
| $Remove\odot Merge(D)$ | `Replace(merge_p(empty_p,D))` |
| $Remove\odot BoundedMerge(D)$ | `Replace(bmerge_p(empty_p,D))` |
| $Replace(T)\odot Merge(D)$ | `Replace(merge_p(T,D))` |
| $Replace(T)\odot BoundedMerge(D)$ | `Replace(bmerge_p(T,D))` |
| $Merge(D_1)\odot Merge(D_2)$ | `Merge(D_1\diamond^{MM}_pD_2)` |
| $Merge(D_1)\odot BoundedMerge(D_2)$ | `BoundedMerge(D_1\diamond^{MB}_pD_2)` |
| $BoundedMerge(D_1)\odot Merge(D_2)$ | `BoundedMerge(D_1\diamond^{BM}_pD_2)` |
| $BoundedMerge(D_1)\odot BoundedMerge(D_2)$ | `BoundedMerge(D_1\diamond^{BB}_pD_2)` |

---

## 6.2 带模式 body 组合 diamond

令：

\[
D_i=(\mu_i,\delta_i)
\]

定义：

\[
D_1\diamond^{xy}_pD_2=(\mu_1\diamond^{loc}_p\mu_2,\delta^{xy})
\]

其中：

\[
x,y\in\{M,B\}
\]

分别表示左、右是普通 `Merge` 还是 `BoundedMerge`。

child map 定义域：

\[
Dom(\delta^{MM})=Dom(\delta_1)\cup Dom(\delta_2)
\]

\[
Dom(\delta^{MB})=Dom(\delta_2)
\]

\[
Dom(\delta^{BM})=Dom(\delta_1)\cup Dom(\delta_2)
\]

\[
Dom(\delta^{BB})=Dom(\delta_2)
\]

引入元操作 `Skip`，仅用于定义 $\diamond$，不属于 $NOp$。其 denotation 为恒等函数，表示“不触及该 child”。

对每个 $k\in Dom(\delta^{xy})$，定义第一步实际 child 操作：

\[
op_1(k)=
\begin{cases}
\delta_1(k), & k\in Dom(\delta_1)\\
Skip, & k\notin Dom(\delta_1),\ x=M\\
Remove, & k\notin Dom(\delta_1),\ x=B
\end{cases}
\]

定义第二步实际 child 操作：

\[
op_2(k)=
\begin{cases}
\delta_2(k), & k\in Dom(\delta_2)\\
Skip, & k\notin Dom(\delta_2),\ y=M\\
Remove, & k\notin Dom(\delta_2),\ y=B
\end{cases}
\]

然后定义：

\[
NormSeq(a,b)=
\begin{cases}
b, & a=Skip\\
a, & b=Skip\\
NF(a\odot b), & a,b\in NOp
\end{cases}
\]

并令：

\[
\delta^{xy}(k)=NormSeq(op_1(k),op_2(k))
\]

说明：

- 在 $BM$ 情况下，若 $k\in Dom(\delta_2)\setminus Dom(\delta_1)$，则第一步 bounded-merge 已删除该 key，因此：
  \[
  op_1(k)=Remove
  \]
  后续普通 merge 在空状态上重建该 child。
- 这正是 `bounded-merge` 与后续普通 `merge` 组合时保持结合律所需的语义。

---

## 6.3 NF 定义

定义：

\[
NF_p:Expr_p\to NOp_p
\]

递归如下：

\[
NF_p(n)=n
\]

\[
NF_p(e_1\odot e_2)
=
Compose_p(NF_p(e_1),NF_p(e_2))
\]

\[
NF_p(Lift_k(e))
=
Merge((\mathbf 1_p,\{k\mapsto NF_{p\cdot k}(e)\}))
\]

注意：

即使 $NF_{p\cdot k}(e)=Id$，这个 lift 仍不能简单省略，因为：

\[
Lift_k(Id)
\]

在 $\varnothing_p$ 上会提升出 $empty_p$。不过作为 `Merge` body 中声明的 child `Id`，它与上述 lift denotation 一致。

---

# 七、良基性与闭包证明

## 7.1 状态高度

定义状态高度：

\[
rank_S(\varnothing_p)=0
\]

\[
rank_S(Node_p(\ell,\chi))
=
1+\max(\{0\}\cup\{rank_S(\chi(k))\mid k\in Dom(\chi)\})
\]

所有状态有限，因此高度为自然数。

---

## 7.2 操作高度

定义：

\[
rank_N(Id)=(0,1)
\]

\[
rank_N(Remove)=(0,1)
\]

\[
rank_N(Replace(T))=(rank_S(T),1)
\]

若：

\[
D=(\mu,\delta)
\]

则：

\[
rank_N(Merge(D))
=
rank_N(BoundedMerge(D))
=
\left(
1+\max(\{0\}\cup\{rank_N(\delta(k))_1\mid k\in Dom(\delta)\}),
\ 1+\sum_{k\in Dom(\delta)}rank_N(\delta(k))_2
\right)
\]

表达式高度：

\[
rank_E(n)=rank_N(n)
\]

\[
rank_E(e_1\odot e_2)
=
\left(
\max(rank_E(e_1)_1,rank_E(e_2)_1),
1+rank_E(e_1)_2+rank_E(e_2)_2
\right)
\]

\[
rank_E(Lift_k(e))
=
(1+rank_E(e)_1,1+rank_E(e)_2)
\]

---

## 7.3 引理：merge/bmerge 封闭并有高度界

### 引理

对任意 $T\in S_p$、$D\in D_p$：

\[
merge_p(T,D)\in S_p
\]

\[
bmerge_p(T,D)\in S_p
\]

并且：

\[
rank_S(merge_p(T,D))
\le
\max(rank_S(T),rank_N(Merge(D))_1)
\]

\[
rank_S(bmerge_p(T,D))
\le
\max(rank_S(T),rank_N(BoundedMerge(D))_1)
\]

### 证明

对 $D$ 的最大 child 深度归纳。

若 $D$ 不含 child 操作，则 `merge` 只更新本地信息，children 不变；`bmerge` 至多删除 children。闭包和高度界显然成立。

若 $D$ 含有限 child map $\delta$，则对每个 $k\in Dom(\delta)$，由归纳假设：

\[
\llbracket\delta(k)\rrbracket_{p\cdot k}(\chi(k))\in S_{p\cdot k}
\]

并且其高度不超过原 child 高度与 child 操作触达高度的最大值。有限次 $put_k$ 不会产生无限 children，也不会超过定义中的最大高度界。

输入为 $\varnothing_p$ 时先提升为 $empty_p$，仍落入上述情况。

证毕。

---

## 7.4 同步定义的良基性

`diamond`、`Compose`、`NF` 采用同步良基定义。

外层按第一高度分量 $h$ 归纳。

假设所有严格小于 $h$ 的 child 路径上的：

- $\diamond$
- `Compose`
- `NF`

均已定义且保持 denotation。

在高度 $h$ 层：

1. 先定义所有 $\diamond^{xy}$。  
   其递归调用只发生在严格 child 路径，因此第一高度分量严格下降。

2. 再定义所有 `Compose`。  
   merge/bounded-merge 分支只引用刚定义好的 $\diamond$；其他分支非递归。

3. 最后定义所有 $NF$。  
   对 $e_1\odot e_2$，递归调用发生在真子表达式上；第一高度不增，第二分量严格下降。  
   对 $Lift_k(e)$，递归调用发生在严格 child 路径，第一高度下降。

自然数良基性保证定义终止。

---

# 八、NF soundness

## 8.1 引理：diamond 保持 denotation

令 $D_1,D_2\in D_p$，$x,y\in\{M,B\}$。令：

\[
z=x\vee y
\]

其中只有 $x=y=M$ 时 $z=M$，否则 $z=B$。

记：

\[
Op_M(D)=Merge(D)
\]

\[
Op_B(D)=BoundedMerge(D)
\]

则：

\[
\llbracket Op_z(D_1\diamond^{xy}_pD_2)\rrbracket_p
=
\llbracket Op_y(D_2)\rrbracket_p
\circ
\llbracket Op_x(D_1)\rrbracket_p
\]

### 证明

设：

\[
D_i=(\mu_i,\delta_i)
\]

先考虑输入：

\[
Node_p(\ell,\chi)
\]

本地部分：

\[
act_p(act_p(\ell,\mu_1),\mu_2)
=
act_p(\ell,\mu_1\diamond^{loc}_p\mu_2)
\]

由本地作用律成立。

children 部分逐 key 比较。

- 在 $MM$ 情况下，最终触及 key 为：
  \[
  Dom(\delta_1)\cup Dom(\delta_2)
  \]
  未被任一方触及的 key 保持不变。被触及 key 上，`Skip` 表示恒等，非 `Skip` 情况由严格 child 路径上的 $NF$ 归纳假设保证。

- 在 $MB$ 情况下，第二步是 bounded-merge，最终只保留：
  \[
  Dom(\delta_2)
  \]
  这与 $\delta^{MB}$ 定义域一致。

- 在 $BM$ 情况下，第一步 bounded-merge 先删除不在 $Dom(\delta_1)$ 的旧 key；第二步普通 merge 可重建 $Dom(\delta_2)$ 中 key。因此最终可能保留：
  \[
  Dom(\delta_1)\cup Dom(\delta_2)
  \]
  与 $\delta^{BM}$ 一致。

- 在 $BB$ 情况下，第二步 bounded-merge 决定最终保留集合：
  \[
  Dom(\delta_2)
  \]

每个保留 key 上，$\delta^{xy}(k)$ 由：

\[
NormSeq(op_1(k),op_2(k))
\]

定义。若某一步为 `Skip`，它是恒等；若两步都是 $NOp$，由严格 child 路径上的 $NF$ soundness 得到同一 denotation。

输入为 $\varnothing_p$ 时，两侧都先提升为 $empty_p$，同理成立。

证毕。

---

## 8.2 引理：Compose 保持 denotation

对任意 $n_1,n_2\in NOp_p$：

\[
\llbracket Compose_p(n_1,n_2)\rrbracket_p
=
\llbracket n_2\rrbracket_p\circ\llbracket n_1\rrbracket_p
\]

### 证明

按 `Compose` 表逐项验证。

- 若左或右为 `Id`，由恒等函数性质直接成立。
- 若右侧为 `Remove`，复合结果为常量 $\varnothing_p$，成立。
- 若右侧为 `Replace(T)`，复合结果为常量 $T$，成立。
- 若左侧为 `Remove`，右侧为 `Merge(D)`，则先得到 $\varnothing_p$，再 merge；由于 merge 在 $\varnothing_p$ 上提升为 $empty_p$，结果为：
  \[
  Replace(merge_p(empty_p,D))
  \]
  `BoundedMerge` 同理。
- 若左侧为 `Replace(T)`，右侧为 `Merge(D)`，则结果为：
  \[
  Replace(merge_p(T,D))
  \]
  `BoundedMerge` 同理。
- 若两侧均为 `Merge/BoundedMerge`，由上一引理得证。

证毕。

---

## 8.3 引理：NF 保持 denotation

对任意 $e\in Expr_p$：

\[
\llbracket NF_p(e)\rrbracket_p
=
\llbracket e\rrbracket_p
\]

### 证明

按第 7.4 节的同步良基归纳。

#### 情况 1：$e=n\in NOp_p$

\[
NF_p(n)=n
\]

显然成立。

#### 情况 2：$e=e_1\odot e_2$

由归纳假设：

\[
\llbracket NF_p(e_1)\rrbracket_p=\llbracket e_1\rrbracket_p
\]

\[
\llbracket NF_p(e_2)\rrbracket_p=\llbracket e_2\rrbracket_p
\]

由 Compose soundness：

\[
\llbracket Compose_p(NF(e_1),NF(e_2))\rrbracket_p
=
\llbracket NF(e_2)\rrbracket_p
\circ
\llbracket NF(e_1)\rrbracket_p
\]

因此：

\[
\llbracket NF(e_1\odot e_2)\rrbracket_p
=
\llbracket e_2\rrbracket_p\circ\llbracket e_1\rrbracket_p
=
\llbracket e_1\odot e_2\rrbracket_p
\]

#### 情况 3：$e=Lift_k(e')$

由归纳假设：

\[
\llbracket NF_{p\cdot k}(e')\rrbracket_{p\cdot k}
=
\llbracket e'\rrbracket_{p\cdot k}
\]

而：

\[
NF_p(Lift_k(e'))
=
Merge((\mathbf 1_p,\{k\mapsto NF_{p\cdot k}(e')\}))
\]

对输入 $\varnothing_p$，两侧都提升为 $empty_p$。对输入：

\[
Node_p(\ell,\chi)
\]

左侧本地信息由 $\mathbf 1_p$ 保持不变，child $k$ 被更新为：

\[
\llbracket NF(e')\rrbracket(\chi(k))
\]

右侧 lift 更新为：

\[
\llbracket e'\rrbracket(\chi(k))
\]

二者由归纳假设相等。其他 child 不变。

故成立。

证毕。

---

# 九、端函数结合律

## 9.1 语义等价

定义：

\[
e\equiv_p e'
\quad\Longleftrightarrow\quad
\llbracket e\rrbracket_p=\llbracket e'\rrbracket_p
\]

这是 denotation 等价，不是语法相等。

以后：

- `=` 表示语法定义等式；
- `\equiv_p` 表示端函数相等。

例如：

\[
Lift_k(e_1)\odot Lift_k(e_2)
\equiv_p
Lift_k(e_1\odot e_2)
\]

一般不是语法等式。

---

## 9.2 定理 5：表达式组合满足结合律

对任意 $e_1,e_2,e_3\in Expr_p$：

\[
(e_1\odot e_2)\odot e_3
\equiv_p
e_1\odot(e_2\odot e_3)
\]

### 证明

左侧 denotation：

\[
\llbracket (e_1\odot e_2)\odot e_3\rrbracket_p
=
\llbracket e_3\rrbracket_p
\circ
\llbracket e_2\rrbracket_p
\circ
\llbracket e_1\rrbracket_p
\]

右侧 denotation：

\[
\llbracket e_1\odot(e_2\odot e_3)\rrbracket_p
=
\llbracket e_3\rrbracket_p
\circ
\llbracket e_2\rrbracket_p
\circ
\llbracket e_1\rrbracket_p
\]

二者由函数复合结合律相等。

证毕。

---

## 9.3 任意有限序列

由于 `Id` 已加入 $NOp_p$，因此空表达式序列可解释为：

\[
Id_p
\]

非空序列按原始线性顺序复合。

对任意有限序列：

\[
e_1,\dots,e_n
\]

任意括号化的 denotation 均为：

\[
\llbracket e_n\rrbracket_p
\circ
\cdots
\circ
\llbracket e_1\rrbracket_p
\]

空序列 denotation 为恒等函数：

\[
\llbracket Id\rrbracket_p
\]

---

## 9.4 商代数

在 $NOp_p$ 上定义：

\[
n\equiv_p n'
\quad\Longleftrightarrow\quad
\llbracket n\rrbracket_p=\llbracket n'\rrbracket_p
\]

取商：

\[
\overline{NOp}_p=NOp_p/{\equiv_p}
\]

定义：

\[
[n_1]\ \bar\odot\ [n_2]
=
[Compose_p(n_1,n_2)]
\]

### 代表元无关性

若：

\[
n_1\equiv_p n_1'
\]

\[
n_2\equiv_p n_2'
\]

则由 Compose soundness：

\[
\llbracket Compose(n_1,n_2)\rrbracket
=
\llbracket n_2\rrbracket\circ\llbracket n_1\rrbracket
\]

\[
=
\llbracket n_2'\rrbracket\circ\llbracket n_1'\rrbracket
=
\llbracket Compose(n_1',n_2')\rrbracket
\]

因此：

\[
Compose(n_1,n_2)\equiv_p Compose(n_1',n_2')
\]

操作良定义。

### 结合律

由函数复合结合律：

\[
([n_1]\bar\odot[n_2])\bar\odot[n_3]
=
[n_1]\bar\odot([n_2]\bar\odot[n_3])
\]

### 单位元

由于 `Id` 已加入，且：

\[
Compose(Id,n)=n
\]

\[
Compose(n,Id)=n
\]

所以：

\[
[Id]
\]

是单位元。

因此：

\[
(\overline{NOp}_p,\bar\odot,[Id])
\]

是幺半群。

---

# 十、祖先/后代重叠操作

有了 `Lift` 与 `NF`，ancestor/descendant 重叠不需要额外特殊证明。

例如：

\[
NF_p(Lift_k(e))
=
Merge((\mathbf 1_p,\{k\mapsto NF_{p\cdot k}(e)\}))
\]

若后代操作发生在 ancestor merge 之后：

\[
Merge(D)\odot Lift_k(e)
\]

则：

\[
NF_p(Merge(D)\odot Lift_k(e))
=
Compose_p(Merge(D),Merge((\mathbf 1_p,\{k\mapsto NF(e)\})))
\]

\[
=
Merge(D\diamond^{MM}_pD_k(e))
\]

其中：

\[
D_k(e)=(\mathbf 1_p,\{k\mapsto NF(e)\})
\]

若 ancestor 是 `Replace(T)`：

\[
Replace(T)\odot Lift_q(e)
\]

则：

\[
NF_p(Replace(T)\odot Lift_q(e))
=
Replace(\llbracket Lift_q(e)\rrbracket_p(T))
\]

这是语法上的 NF 结果。

若 ancestor 是 `Remove`：

\[
Remove\odot Lift_q(e)
\]

则：

\[
NF_p(Remove\odot Lift_q(e))
=
Replace(\llbracket Lift_q(e)\rrbracket_p(\varnothing_p))
\]

这体现本文端函数 carrier 的语义选择：删除后，后代操作可以在空虚拟祖先上重建子树。

这不同于坐标 tombstone carrier 中“祖先 tombstone 持续屏蔽后代，直到同坐标覆盖 tombstone”的语义。两者是不同 carrier，不能混用。

---

# 十一、有序列表与顺序约束

有序列表不能用物理下标作为坐标。应拆分为：

1. stable key 决定元素身份；
2. 顺序约束作为潜在证据收集；
3. 最终统一规范化排序和验证。

可用 carrier：

\[
Ord_p=List(Constraint_p)
\]

\[
x\diamond^{ord}_p y=x++y
\]

\[
\mathbf 1^{ord}_p=[]
\]

列表连接满足结合律。

每条约束至少包含：

\[
Constraint_p=(sourceOrdinal,localOrdinal,targetKey,relation,anchorKey)
\]

其中：

- `sourceOrdinal` 来自全局线性化顺序；
- `localOrdinal` 来自差量文件内部确定性解析顺序；
- 二者不能由括号化过程重新生成。

预合并 artifact 必须保留这些稳定顺序标签。若预合并时重新编号、哈希去重导致顺序丢失，或 tie-breaker 依赖遍历偶然顺序，则本证明不适用。

最终：

\[
Norm
\]

统一执行：

- anchor 解析；
- 删除失效约束；
- 环检测；
- 拓扑排序；
- stable tie-break；
- 错误集合规范化。

只要该过程确定，括号化不影响最终结果。

---

# 十二、三个最终定理

## 12.1 定理 A：坐标 tombstone carrier 的预合并结合律

设：

\[
(P,\oplus,\varnothing)
\]

为第 1 节定义的有限 typed partial map 幺半群，且：

\[
Final_P:P\to Result
\]

为确定性最终函数。

则对任意：

\[
P_0,\Delta_1,\dots,\Delta_n\in P
\]

有：

\[
Final_P((((P_0\oplus\Delta_1)\oplus\Delta_2)\cdots\oplus\Delta_n))
=
Final_P(P_0\oplus NF_{LWW}(\Delta_1,\dots,\Delta_n))
\]

### 证明

由定理 1，$\oplus$ 结合且有单位元。由 LWW 规约推论，任意括号化得到同一潜在模型。确定性 $Final_P$ 作用于同一潜在模型，结果相同。证毕。

---

## 12.2 定理 B：局部操作 carrier 的预合并结合律

设每个坐标 $c$ 上：

\[
(O_c,\star_c,e_c)
\]

为幺半群，且存在满足作用律的：

\[
act_c:W_c\times O_c\to W_c
\]

令：

\[
(P_O,\otimes,\varnothing)
\]

为第 4 节定义的稀疏操作模型幺半群。

则对任意：

\[
s_0\in ValState_O
\]

和有限操作序列：

\[
\Delta_1,\dots,\Delta_n\in P_O
\]

有：

\[
Apply(\cdots Apply(Apply(s_0,\Delta_1),\Delta_2)\cdots,\Delta_n)
=
Apply(s_0,Fold_\otimes(\Delta_1,\dots,\Delta_n))
\]

若：

\[
Final_O:ValState_O\to Result
\]

确定，则两侧最终结果相同。

### 证明

由第 4 节，$\otimes$ 结合。由每个坐标上的作用律：

\[
act_c(act_c(w,x),y)=act_c(w,x\star_c y)
\]

按序列长度归纳，逐坐标得到结论。确定性 $Final_O$ 保持相等。证毕。

---

## 12.3 定理 C：树状态端函数 carrier 的预合并结合律

设 $S=S_\epsilon$ 为根状态空间，$Expr_\epsilon$ 为第 5 节定义的有限表达式语言，所有操作类型正确且状态空间封闭。

则对任意：

\[
e_1,\dots,e_n\in Expr_\epsilon
\]

任意括号化得到同一个端函数：

\[
S\to S
\]

具体为：

\[
\llbracket e_n\rrbracket_\epsilon\circ\cdots\circ\llbracket e_1\rrbracket_\epsilon
\]

空序列为：

\[
\llbracket Id\rrbracket_\epsilon
\]

此外：

\[
NF_\epsilon
\]

给出与原表达式 denotation 相同的正规操作代表元：

\[
\llbracket NF_\epsilon(e)\rrbracket_\epsilon
=
\llbracket e\rrbracket_\epsilon
\]

商代数：

\[
(\overline{NOp}_\epsilon,\bar\odot,[Id])
\]

是幺半群。

### 证明

表达式组合 denotation 定义为函数复合：

\[
\llbracket e_1\odot e_2\rrbracket
=
\llbracket e_2\rrbracket\circ\llbracket e_1\rrbracket
\]

函数复合结合，因此任意括号化 denotation 相同。

由 NF soundness：

\[
\llbracket NF(e)\rrbracket=\llbracket e\rrbracket
\]

商代数结合律和单位元性质由 Compose soundness 与 `Id` 规则得到。证毕。

---

## 12.4 推论 D：GRC 结构差量合并的可观测一致性

若一条 GRC 合并链：

1. 已被确定性线性化；
2. 整条链解释在同一个 carrier 中；
3. 所有操作在该 carrier 中 total 且闭包；
4. 中间不执行有损投影；
5. 最终 $Final_P$、$Final_O$ 或 $Final_S$ 确定；

则任意括号化产生同一个最终结果：

\[
Ok(Model)
\]

或同一个规范化：

\[
Err(ErrorSet)
\]

### 证明

由定理 A、B、C，在对应 carrier 中潜在结果、值状态或端函数作用结果相同。确定性最终函数作用于同一输入，结果相同。证毕。

---

# 十三、工程边界

本文证明的是抽象 carrier 的条件化结合律，不是当前 Nop/XLang 实现的无条件结合律证明。

若真实实现要引用本文结论，必须额外证明：

1. children matching 使用 stable key，而非物理下标；
2. `remove` 映射到所选 carrier 中的 tombstone 或端函数 `Remove`；
3. `replace` 确实映射为端函数 `Replace(T)`，或在坐标 carrier 中有限展开了所有需清除旧后代；
4. `bounded-merge` 实现满足 $MB/BM/BB$ 的 child-level rewrite；
5. 被 bounded 裁剪后重新声明的 child 从空状态或等价 tombstone 证据重建；
6. `append/prepend` 的 XNode 级语义确实符合相应序列 carrier 或端函数 carrier；
7. `merge-super/x:super` 被建模为确定性端函数，且多次、零次、条件 super 有明确 total denotation 或被排除；
8. `x:before/x:after` 的约束收集保留稳定 source order，不依赖哈希遍历或括号化；
9. `virtual`、顺序约束、tombstone 等潜在证据在合并链中途不被丢弃；
10. forbidden/null/partial failure 操作要么排除，要么进入 total error carrier 并重新证明结合律。

---

# 十四、结论

修正后的证明得到以下严格结论：

1. 坐标 tombstone carrier 中：
   \[
   (P,\oplus,\varnothing)
   \]
   是幺半群，预合并满足结合律。

2. 局部操作 carrier 中：
   若每个坐标上的操作构成幺半群并满足作用律，则操作预合并满足结合律，并且“先预合并再作用”等价于“逐步作用”。

3. 树状态端函数 carrier 中：
   加入真正 `Id` 后，差量表达式通过 denotation 解释为确定性端函数。结合律来自函数复合。`NF/Compose/diamond` 给出可序列化正规操作的语义保持规约；商代数构成幺半群。

4. 只要最终投影、规范化、排序和验证在合并链完成后统一执行且确定，最终可观测结果不依赖括号化。

因此，GRC 差量预合并的结合律不是无条件命题，而是以下条件下成立的数学事实：

- 稳定语义坐标或 stable key；
- 同一 carrier 内解释；
- total 且闭包的操作语义；
- 潜在空间中完成合并；
- 最终统一确定性规范化与验证；
- 真实实现满足对应 carrier 的实现符合性证明。