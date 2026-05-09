# 附录：GRC/XLang Tree-Delta 结合律的精简形式化证明

## 摘要

本文给出一个可独立审查的附录证明。证明对象不是任意文本 patch，也不是把 delta 直接当作函数后的平凡函数复合结合律，而是一个递归定义的 tree-delta carrier：delta 本身是空间中的元素，而不是空间上的外在变换。我们考虑一个受限的 XLang delta 片段，其中 override 仅取 `remove`、`replace`、`merge`、`bounded-merge`，children 按 stable key 匹配，delta 链已确定性线性化。本文首先定义潜在树状态空间 $S$、递归 tree-delta 空间 $D$、以及 delta 对状态的作用 $Apply$；随后定义 delta 之间的内部组合运算 $\otimes$，其核心是带 tag 的规范化规则，例如 `Remove + Merge -> Replace(...)`，并将这一规则递归提升到树结构。本文证明：$D$ 对 $\otimes$ 封闭，$Apply$ 对状态空间封闭，且 $\otimes$ 在语义等价商 $\bar D=D/{\approx}$ 上满足结合律；因此 tree-delta 可以在不改变线性顺序的前提下安全预合并。最后给出三个带合并结果的具体示例。

## 1. 目标与边界

设一条 delta 链已经按某个确定性顺序线性化为 $\Delta_1,\Delta_2,\ldots,\Delta_n$。本文只允许改变括号，不允许改变顺序。证明目标是：

1. 定义一个递归 tree-delta carrier $D$，其中 delta 本身就是空间中的元素。
2. 定义 delta 的内部组合运算 $\otimes:D\times D\to D$。
3. 证明该组合在语义等价商 $\bar D$ 上满足结合律。
4. 证明对任意基线状态 $s\in S$，预合并后再执行与逐步执行得到同一最终结果。

本文不证明以下命题：

1. 任意文本 patch 的结合律。
2. 一般树级 `replace/remove` 可归约为逐坐标 LWW 的结论。
3. 任意组合结果都能重新序列化为同类 DSL delta 文件。
4. 当前 XLang/Nop 代码库中所有 override 分支已经完成实现符合性证明。

因此，本文的结论是一个抽象语义定理。若要把它直接归到当前实现，还需要单独证明：真实 XLang delta 链到本文受限片段的翻译与实现行为一致。

## 2. 前提、记号与潜在空间

### 2.1 前提

下文默认以下前提成立：

1. delta 链已按某个确定性顺序线性化，证明只改变括号，不改变顺序。
2. children 的匹配依据是 stable key，而不是物理下标或偶然遍历顺序。
3. 所有参与证明的 delta 和状态都是有限对象。
4. 本地作用 $act_p$、规范化 $Norm$、投影 $Pr$、验证 $Validate$ 都是确定性函数。
5. 合并链中途不做有损投影；$Norm/Pr/Validate$ 只在整条链结束后执行。
6. 本文固定采用 $empty_p$ 提升语义：对不存在父节点上的 `merge` 或 descendant patch，先提升到 $empty_p$ 再解释。
7. 所有状态、delta 片段与 XML 输入都满足 well-formedness：同一 sibling set 中每个 stable key 至多出现一次。

### 2.2 记号

1. $p$：一条稳定路径。
2. $K_p$：路径 $p$ 下 children 的 stable-key 集合。
3. $L_p$：路径 $p$ 处节点允许出现的本地信息集合。
4. $M_p$：路径 $p$ 处本地信息补丁集合。
5. $S_p$：路径 $p$ 处的潜在树状态空间。
6. $D_p$：路径 $p$ 处的 tree-delta 空间。
7. $S=S_\epsilon$，$D=D_\epsilon$：根路径上的状态空间与 delta 空间。
8. $Result$：最终可观测结果类型，例如 $Ok(Model)$ 或 $Err(ErrorSet)$。

### 2.2b 路径类型与 stable identity 不变式

仅有 $K_p/L_p/M_p/S_p/D_p$ 这些记号还不够；为了避免 local patch 篡改 child 匹配所依赖的身份字段，本文再固定如下 typed-path 不变式。

对每个路径 $p$，给定一个路径描述子

$$
Desc(p)=(tag_p,\sigma_p)
$$

其中 $tag_p$ 是路径 $p$ 处节点的根 tag，$\sigma_p$ 是该路径的 stable identity 数据，例如 `(tag,id)` 中由路径固定下来的那部分身份。

并要求：

1. 每个 $\ell\in L_p$ 都唯一分解为

$$
\ell=(\sigma_p,\alpha)
$$

其中 $\alpha\in A_p$ 只包含允许被本地补丁修改的可变本地信息。
2. 每个本地补丁 $\mu\in M_p$ 只作用于可变部分 $\alpha$；等价地，存在函数 $act_p^{var}:A_p\times M_p\to A_p$，使得

$$
act_p((\sigma_p,\alpha),\mu)=(\sigma_p,act_p^{var}(\alpha,\mu))
$$

因此 `tag`、`id`、child 匹配所依赖的 stable-key 字段以及其他 path-defining identity 字段都不允许被 $\mu$ 改写。
3. 若 $k\in K_p$，则子路径 $p\cdot k$ 的描述子 $Desc(p\cdot k)$ 已固定；因此 $S_{p\cdot k}$ 与 $D_{p\cdot k}$ 中的对象根身份都由该子路径决定。
4. `Replace(T)` 作为 $D_p$ 的构造子时要求 $T\in S_p$；因此被替换进去的整棵子树根身份也必须满足路径 $p$ 的 typed 约束，而不能是任意外来节点。

这组不变式正是本文把 child 匹配建立在 stable key 上所需的最小形式化约束。后文所有闭包与结合律论证都默认在该 typed universe 中进行。

### 2.2a $M_p$、$S_p$、$D_p$ 分别装的是什么

上面三个记号是全文最关键的三个对象。仅仅说“集合”或“空间”不够，必须说明其中元素到底长什么样。

1. $M_p$ 不是整棵子树，也不是 delta 链，而是**路径 $p$ 处当前节点自身的本地补丁**。
2. $S_p$ 不是 delta，而是**路径 $p$ 处整棵子树的潜在状态**。
3. $D_p$ 不是状态，而是**作用于路径 $p$ 处整棵子树的 tree-delta 元素**。

三者的关系可以概括为：

1. $M_p$ 只描述“当前节点自己怎么改”。
2. $S_p$ 描述“当前路径这棵子树现在是什么状态”。
3. $D_p$ 描述“当前路径这棵子树要怎么改”。

更具体地说：

#### $M_p$：本地补丁集合

$M_p$ 的元素只作用于当前节点本身，不递归进入 children。典型内容包括：

1. 属性修改，例如 `label="备注"`。
2. 文本修改。
3. 其他只影响当前节点本地信息的补丁。

在本文示例采用的标准实例里，$M_p$ 可以具体化为“有限属性补丁映射”。例如：

$$
\mu = \{label\mapsto \text{备注},\; required\mapsto true\}
$$

这就是一个 $M_p$ 元素。

#### $S_p$：潜在树状态空间

$S_p$ 的元素是路径 $p$ 处整棵子树的潜在状态。它的元素形状只有两种：

$$
\varnothing_p
\quad\text{或}\quad
Node_p(\ell,\chi)
$$

其中：

1. $\ell$ 是当前节点自己的本地信息。
2. $\chi$ 是 child-key 到各 child 子树状态的有限映射。

所以，$S_p$ 的元素可以直观理解为：

1. “这棵子树不存在”；或
2. “当前节点本地信息 + 每个 child 的子树状态”。

#### $D_p$：tree-delta 空间

$D_p$ 的元素不是状态，而是 delta。其元素只有四类：

$$
Remove,\quad Replace(T),\quad Merge(\mu,\delta),\quad BoundedMerge(\mu,\delta)
$$

其中：

1. $T\in S_p$，表示“用一棵完整子树状态替换当前子树”。
2. $\mu\in M_p$，表示“当前节点本地信息怎么改”。
3. $\delta$ 是 child-key 到子 delta 的有限映射，值属于更深一层的 $D_{p\cdot k}$。

所以，$D_p$ 的元素可以直观理解为：

1. 删除整棵当前子树；
2. 用一棵完整子树替换当前子树；
3. 修改当前节点自身，同时递归修改若干 child；
4. 只保留右侧声明的那些 child，并递归修改它们。

#### 一个并排小例子

考虑如下子树：

```xml
<Panel id="p1">
  <Field id="f1"/>
  <Field id="f2" label="备注"/>
</Panel>
```

在本文标准实例里：

1. 一个可能的 $M_p$ 元素是

$$
\mu_{note}=\{label\mapsto \text{备注}\}
$$

它只表示“当前节点本地属性 `label` 被设置为 `备注`”。

2. 对应的一个 $S_p$ 元素可以写成

$$
Node_p(\ell_{panel},\{f1\mapsto T_{f1},\; f2\mapsto T_{f2}\})
$$

意思是：当前路径上存在一个 `Panel p1`，其 children 中有 `f1` 和 `f2` 两棵子树状态。

3. 一个可能的 $D_p$ 元素可以写成

$$
Merge(\mathbf 1_p,\{f2\mapsto Merge(\mu_{note},\varnothing)\})
$$

意思是：

1. 当前节点自己不改；
2. 递归进入 child `f2`；
3. 在 `f2` 节点自身写入本地补丁 $\mu_{note}$。

这个并排例子说明：

1. $M_p$ 是“节点级补丁”；
2. $S_p$ 是“子树级状态”；
3. $D_p$ 是“子树级 delta”。

### 2.3 潜在空间是什么

本文所称“潜在空间”是一个固定的数学对象，而不是口语化描述。它满足三点：

1. 它位于最终 $Norm/Pr/Validate$ 之前。
2. 它允许保留后续 delta 仍可能依赖的中间语义状态。
3. 在本附录中，这个空间固定为树状态空间 $S$ 及其局部子空间 $S_p$。

因此，潜在空间中的对象不是“已经完成投影和验证的最终物理树”，而是“仍可继续承受后续 delta 作用的中间语义状态”。在本附录采用的树状态 carrier 中，这种潜在性具体体现在：

1. 子树可以暂时处于 $\varnothing_p$，随后仍可被后续 delta 继续作用。
2. 对不存在父节点的后代操作，解释由 $empty_p$ 给出，因此合并链不必在中途终止到最终可观测树。

### 2.4 什么是“有损投影”

设 $\pi:S\to X$ 是合并链中间某一步施加的状态变换。若存在两个潜在状态 $s_1,s_2\in S$ 和某个后续 delta $d\in D$，使得

$$
\pi(s_1)=\pi(s_2)
$$

但后续作用产生不同最终结果，即

$$
Final(Apply(s_1,d))\neq Final(Apply(s_2,d))
$$

则称 $\pi$ 对后续语义是有损的。直观地说，它把后续 delta 本来还能区分的两个潜在状态压成同一个中间结果。本文要求合并链中途不执行这类变换。

## 3. 潜在树状态空间

对每个稳定路径 $p$，定义潜在树状态空间：

$$
S_p ::= \varnothing_p \mid Node_p(\ell,\chi)
$$

其中：

1. $\varnothing_p$ 表示路径 $p$ 处子树不存在。
2. $\ell\in L_p$ 是该节点的本地潜在信息。
3. $\chi$ 是有限 partial map，且 $Dom(\chi)\subseteq K_p$；对每个 $k\in Dom(\chi)$，有 $\chi(k)\in S_{p\cdot k}$。
4. 若 $k\notin Dom(\chi)$，约定 $\chi(k)=\varnothing_{p\cdot k}$。

所有状态都要求有限深度、有限分支，并满足 sibling stable-key 唯一性。存储在 $Node_p(\ell,\chi)$ 中的 $\chi$ 只包含取值非 $\varnothing$ 的 key；未存储 key 一律按约定解释为 $\varnothing$。

记

$$
empty_p = Node_p(\ell_p^0,\varnothing)
$$

为路径 $p$ 处的空虚拟节点，其中

$$
\ell_p^0=(\sigma_p,\alpha_p^0)\in L_p
$$

是由路径描述子 $Desc(p)$ 唯一决定的规范本地信息：$\sigma_p$ 固定保留该路径的根身份，$\alpha_p^0\in A_p$ 是可变部分的中性空值。于是 $empty_p$ 不是“任意空节点”，而是“带有路径 $p$ 身份的 canonical 空节点”；这保证了对不存在父节点应用 descendant patch 时，$Apply_p(empty_p,\cdot)$ 仍然是良定型的。

定义有限 map 更新算子 $put_k$：

1. 若结果 $t\neq\varnothing_{p\cdot k}$，则 $put_k(\chi,t)$ 在 key $k$ 处写入 $t$。
2. 若结果 $t=\varnothing_{p\cdot k}$，则 $put_k(\chi,t)$ 从有限 map 中移除 key $k$。

**引理 A（不同 key 上的更新可交换）** 若 $k_1\neq k_2$，$t_i\in S_{p\cdot k_i}$，则对任意有限 partial map $\chi$，有

$$
put_{k_1}(put_{k_2}(\chi,t_2),t_1)
=
put_{k_2}(put_{k_1}(\chi,t_1),t_2)
$$

**证明** 两次更新只接触不同 key。无论 $t_i$ 是写入非空子树还是删除该 key，对另一个 key 的值都没有影响，因此二者交换。证毕。

**推论 A（child 更新顺序无关）** 在 `Merge` 或 `BoundedMerge` 的定义中，对有限集合 $Dom(\delta)$ 的逐 key 更新结果与遍历顺序无关。

**证明** 任取两个相邻且不同的 key，可由引理 A 交换其顺序；有限次交换即可把任意遍历序列变为另一序列，最终结果不变。证毕。

## 4. Tree-Delta Carrier

### 4.1 本地补丁

对每个路径 $p$，给定一个本地补丁幺半群：

$$
(M_p,\diamond^{loc}_p,\mathbf 1_p)
$$

以及右作用：

$$
act_p:L_p\times M_p\to L_p
$$

满足：

$$
act_p(\ell,\mathbf 1_p)=\ell
$$

$$
act_p(act_p(\ell,\mu_1),\mu_2)=act_p(\ell,\mu_1\diamond^{loc}_p\mu_2)
$$

### 4.2 Delta 元素本身就是 carrier 元素

对每个路径 $p$，定义 tree-delta 空间 $D_p$ 为以下递归集合：

$$
D_p ::= Remove \mid Replace(T) \mid Merge(\mu,\delta) \mid BoundedMerge(\mu,\delta)
$$

其中：

1. $T\in S_p$。
2. $\mu\in M_p$。
3. $\delta$ 是有限 partial map，且 $Dom(\delta)\subseteq K_p$；对每个 $k\in Dom(\delta)$，有 $\delta(k)\in D_{p\cdot k}$。

delta 不是“状态上的外在函数”，而是空间 $D_p$ 中的递归元素。`Remove`、`Replace(T)`、`Merge(\mu,\delta)`、`BoundedMerge(\mu,\delta)` 都是 carrier 的构造子。

下文会借助 $Apply$ 来定义某些组合规则并证明其正确性，但这并不改变 delta 的地位：$Apply$ 的类型是 $S_p\times D_p\to S_p$，它是 delta 对状态的解释函数；而 $\otimes$ 的定义域和值域都在 $D_p$ 内部。函数语义只是证明 tree-delta 组合规则 soundness 的工具，而不是把 delta 本身改定义为函数。

## 5. Delta 对状态的作用

定义 $Apply_p:S_p\times D_p\to S_p$。对四种 tag 分别定义：

$$
Apply_p(s,Remove)=\varnothing_p
$$

$$
Apply_p(s,Replace(T))=T
$$

对 $d=Merge(\mu,\delta)$：

$$
Apply_p(\varnothing_p,d)=Apply_p(empty_p,d)
$$

对 $Node_p(\ell,\chi)$，从有限映射 $\chi$ 出发，对每个 $k\in Dom(\delta)$ 依次执行

$$
\chi := put_k(\chi, Apply_{p\cdot k}(\chi(k),\delta(k)))
$$

得到有限映射 $\chi'$，然后定义

$$
Apply_p(Node_p(\ell,\chi), Merge(\mu,\delta)) = Node_p(act_p(\ell,\mu),\chi')
$$

对 $d=BoundedMerge(\mu,\delta)$：

$$
Apply_p(\varnothing_p,d)=Apply_p(empty_p,d)
$$

对 $Node_p(\ell,\chi)$，从空有限映射开始。对每个 $k\in Dom(\delta)$，令

$$
t_k = Apply_{p\cdot k}(\chi(k),\delta(k))
$$

再执行

$$
\chi^b := put_k(\chi^b,t_k)
$$

最终定义

$$
Apply_p(Node_p(\ell,\chi), BoundedMerge(\mu,\delta)) = Node_p(act_p(\ell,\mu),\chi^b)
$$

因此，`BoundedMerge` 的语义不是“为每个未声明 key 显式写入 $\varnothing$”，而是“最终只保留经右侧声明集合计算后仍非空的 key”。这保证结果仍然是有限 partial map，因而确实落在 $S_p$ 中。

**引理 B（祖先 BoundedMerge 的裁剪支配）** 设

$$
Apply_p(Node_p(\ell,\chi), BoundedMerge(\mu,\delta)) = Node_p(\ell',\chi^b)
$$

则对任意 $k\notin Dom(\delta)$，都有

$$
\chi^b(k)=\varnothing_{p\cdot k}
$$

因此原先位于 child $k$ 之下的整棵旧子树以及其所有更深后代都会被同时裁剪，而不只是“当前层少了一个 key”。

**证明** $\chi^b$ 是从空映射开始，只对 $Dom(\delta)$ 中的 key 通过 $put_k$ 构造得到的。故 $k\notin Dom(\delta)$ 时，$\chi^b$ 中从未为该 key 写入任何值，于是按 $S_p$ 的约定有 $\chi^b(k)=\varnothing_{p\cdot k}$。子树根已为 $\varnothing$，其所有后代自然一并消失。证毕。

这里要特别强调：$Apply_p$ 只是把一个 delta 元素解释为“它如何作用于潜在状态”的语义函数，用来证明组合规则的 soundness。主证明的 carrier 仍然是 $D_p$，不是 $End(S_p)$。换句话说，本文不是先把 delta 定义成函数，再直接援用函数复合结合律；而是先定义 delta 自身的内部组合 $\otimes$，再用 $Apply_p$ 证明这个内部组合确实正确地代表了连续执行的效果。

一个最小例子可以帮助避免误解。设在路径 $p$ 上有一个 delta

$$
d = Merge(\mu,\varnothing)
$$

那么：

1. 作为 carrier 元素，它只是 $D_p$ 中的一个对象；
2. 只有当给定某个状态 $s\in S_p$ 时，$Apply_p(s,d)$ 才产生一个状态结果；
3. 而 $d\otimes d'$ 的结果必须仍然是 $D_p$ 中的元素，而不能提前退化成某个状态。

这正是本文与“把 delta 直接等同于函数”路线的根本区别。

## 6. Delta 之间的内部组合运算

### 6.1 辅助元操作

为了定义组合，临时引入一个仅用于定义的元操作 $Keep$，表示“该路径不产生任何 delta”。它不是 $D_p$ 的构造子，不会出现在最终结果中。

定义

$$
NormSeq(a,b)=
\begin{cases}
b, & a=Keep \\
a, & b=Keep \\
a\otimes b, & a,b\in D_p
\end{cases}
$$

### 6.2 为什么要 tagged 组合

同一路径上的两个 delta 不能简单并列保留，因为祖先 tag 会改写整棵子树的含义。核心规则正是这种 tagged 规约思想在树上的递归提升：

$$
Remove \otimes Merge(\mu,\delta)
=
Replace(Apply_p(empty_p,Merge(\mu,\delta)))
$$

意思是：先删掉整棵子树，再在空树上 merge，新结果不再是“删除 + merge”两个操作并列，而是一个新的 `Replace(...)`。这样才能保持闭包。

这个规则最容易被误解，所以下面给出两个微妙示例。

**微妙示例 A：为什么 `Remove \otimes Merge` 不能继续写成两个并列操作**

考虑路径 $p1$ 上的两步操作：

```xml
<Panel id="p1" x:override="remove"/>
```

随后：

```xml
<Panel id="p1" x:override="merge">
  <Button id="btn" color="red"/>
</Panel>
```

若仍把结果写成“`Remove` 与 `Merge` 并列”，那么后续第三步若再来一个

```xml
<Panel id="p1" x:override="merge">
  <Button id="btn" text="OK"/>
</Panel>
```

就无法在 delta carrier 内部唯一决定它究竟应该接在“被删除的旧树”上，还是接在“第二步重建出来的新树”上。把前两步规约成

$$
Replace(Apply_p(empty_p,Merge(\mu,\delta)))
$$

之后，这个歧义被消除：第三步只能接在新的 `Replace(...)` 结果上继续组合。

**微妙示例 B：为什么 `BoundedMerge \otimes Merge` 必须允许重新声明 key**

考虑在路径 $p1$ 上先有：

```xml
<Panel id="p1" x:override="bounded-merge">
  <Field id="f2" label="备注"/>
</Panel>
```

再有：

```xml
<Panel id="p1" x:override="merge">
  <Field id="f3" label="恢复"/>
</Panel>
```

若 $BM$ 模式仍只保留右侧 bounded 的 support，那么 `f3` 将永远无法在第二步重新出现，这显然不符合“先裁剪旧 child，再由后续 merge 重新声明新 child”的直观语义。正因为如此，$BM$ 必须取并集 support，并把缺失左项编码成 `Remove`，即在 child `f3` 上按“先删再 merge”的规则规约。这就是

$$
Supp^{BM}=Dom(\delta_1)\cup Dom(\delta_2)
$$

的必要性。

### 6.3 同一路径上的组合表

对 $x,y\in D_p$，定义 $x\otimes y$ 如下：

1. $Remove\otimes Remove = Remove$
2. $Remove\otimes Replace(T) = Replace(T)$
3. $Remove\otimes Merge(\mu,\delta)=Replace(Apply_p(empty_p,Merge(\mu,\delta)))$
4. $Remove\otimes BoundedMerge(\mu,\delta)=Replace(Apply_p(empty_p,BoundedMerge(\mu,\delta)))$

5. $Replace(T)\otimes Remove = Remove$
6. $Replace(T)\otimes Replace(T') = Replace(T')$
7. $Replace(T)\otimes Merge(\mu,\delta)=Replace(Apply_p(T,Merge(\mu,\delta)))$
8. $Replace(T)\otimes BoundedMerge(\mu,\delta)=Replace(Apply_p(T,BoundedMerge(\mu,\delta)))$

9. $Merge(d_1)\otimes Remove = Remove$
10. $Merge(d_1)\otimes Replace(T)=Replace(T)$
11. $Merge(d_1)\otimes Merge(d_2)=Merge(d_1\diamond^{MM}d_2)$
12. $Merge(d_1)\otimes BoundedMerge(d_2)=BoundedMerge(d_1\diamond^{MB}d_2)$

13. $BoundedMerge(d_1)\otimes Remove = Remove$
14. $BoundedMerge(d_1)\otimes Replace(T)=Replace(T)$
15. $BoundedMerge(d_1)\otimes Merge(d_2)=BoundedMerge(d_1\diamond^{BM}d_2)$
16. $BoundedMerge(d_1)\otimes BoundedMerge(d_2)=BoundedMerge(d_1\diamond^{BB}d_2)$

其中 $d_i=(\mu_i,\delta_i)$ 是 merge body。

### 6.4 merge body 的递归组合

设 $d_i=(\mu_i,\delta_i)$。四种模式的本地部分统一为：

$$
\mu = \mu_1\diamond^{loc}_p\mu_2
$$

child 部分的结果模式记为 $z$：只有 $MM$ 得到普通 `Merge`，其余三种都得到 `BoundedMerge`。

支持集合定义为：

$$
Supp^{MM}=Dom(\delta_1)\cup Dom(\delta_2)
$$

$$
Supp^{MB}=Dom(\delta_2)
$$

$$
Supp^{BM}=Dom(\delta_1)\cup Dom(\delta_2)
$$

$$
Supp^{BB}=Dom(\delta_2)
$$

这四个支持集合正是 tree 结构中祖先 tag 支配后代语义的递归编码：

1. 右侧若是 `BoundedMerge`，其未声明 key 被裁剪，因此 $MB$ 与 $BB$ 的最终 support 由右侧声明集合控制。
2. 左侧若是 `BoundedMerge` 而右侧是 `Merge`，则先前被裁剪的 key 可以被右侧重新声明，因此 $BM$ 的 support 必须取并集。

对 $k\in Supp^{xy}$，定义左右两步在 child $k$ 上的操作：

$$
op_1(k)=
\begin{cases}
\delta_1(k), & k\in Dom(\delta_1) \\
Keep, & x=M,\; k\notin Dom(\delta_1) \\
Remove, & x=B,\; k\notin Dom(\delta_1)
\end{cases}
$$

$$
op_2(k)=
\begin{cases}
\delta_2(k), & k\in Dom(\delta_2) \\
Keep, & y=M,\; k\notin Dom(\delta_2) \\
Remove, & y=B,\; k\notin Dom(\delta_2)
\end{cases}
$$

然后定义结果 child 映射：

$$
\delta^{xy}(k)=NormSeq(op_1(k),op_2(k))
$$

若结果为 $Keep$，则不写入有限映射。最终定义：

$$
d_1\diamond^{xy}d_2=(\mu,\delta^{xy})
$$

这就把“所有坐标处的操作都是 tagged 的；通过类似 $(c,Remove)+(c,Merge)\to(c,Replace)$ 的规约保持所有信息”这一思想，递归地提升到了树结构上。

## 7. 闭包与作用律

**引理 1** 对任意 $s\in S_p$ 和任意 $d\in D_p$，有 $Apply_p(s,d)\in S_p$。

**证明**

对 $d$ 的树高做归纳。`Remove` 与 `Replace(T)` 的结果显然属于 $S_p$。若 $d=Merge(\mu,\delta)$，则本地部分由 typed-path 不变式落在 $L_p$ 中，且保持根身份 $\sigma_p$ 不变；child 部分从有限映射 $\chi$ 出发，仅对有限集合 $Dom(\delta)$ 进行有限次 $put_k$ 更新。由归纳假设，每个更新值都属于对应的 $S_{p\cdot k}$；由推论 A，更新顺序无关，因此 $\chi'$ 良定义，且仍是只存非空 key 的有限 partial map。`BoundedMerge` 同理，只是从空映射开始构造 $\chi^b$；由引理 B，所有未声明 key 都被裁剪为 $\varnothing$，因此结果定义域至多为那些结果非空的声明 key，仍然有限。故 $Apply_p(s,d)\in S_p$。证毕。

**引理 2** 对任意 $x,y\in D_p$，有 $x\otimes y\in D_p$。

**证明**

对 $x,y$ 的最大子树深度做归纳。若至少有一个是 `Remove` 或 `Replace(T)`，由第 6.3 节定义及引理 1，结果显然仍属于 $D_p$。若二者都是 `Merge/BoundedMerge`，则结果仍是 `Merge(...)` 或 `BoundedMerge(...)`。其本地部分属于 $M_p$ 且保持路径身份不变；child support 有限，且只由 $K_p$ 中的 key 组成；每个 child 的结果要么是递归组合 $a\otimes b$，要么是单个已有 delta，要么被规约为 $Keep$ 后省略。由归纳假设，递归 child 组合仍属于对应的 $D_{p\cdot k}$。故整体仍属于 $D_p$。证毕。

**定理 1** 对任意 $s\in S_p$ 和任意 $x,y\in D_p$，有

$$
Apply_p(Apply_p(s,x),y)=Apply_p(s,x\otimes y)
$$

**证明**

对 $x,y$ 的 tag 做分类，并对树高做递归归纳。

1. 若 $y=Remove$ 或 $y=Replace(T)$，右侧分别化为 $Remove$ 或 $Replace(T)$；左侧先执行 $x$，再被 $y$ 常量覆盖，故相等。
2. 若 $x=Remove$ 且 $y=Merge(\mu,\delta)$，左侧是“先得 $\varnothing_p$，再在 $empty_p$ 上执行 merge”；右侧按定义正是 $Replace(Apply_p(empty_p,Merge(\mu,\delta)))$，故相等。$x=Remove$ 且 $y=BoundedMerge$ 同理。
3. 若 $x=Replace(T)$ 且 $y=Merge/BoundedMerge$，左侧是“先把状态变为 $T$，再执行后续操作”；右侧定义为 $Replace(Apply_p(T,\cdot))$，故相等。
4. 若 $x,y$ 都是 `Merge/BoundedMerge`，比较根节点本地部分与 child 部分：
   - 本地部分由 $act_p(act_p(\ell,\mu_1),\mu_2)=act_p(\ell,\mu_1\diamond^{loc}_p\mu_2)$ 得到一致；同时 typed-path 不变式保证根身份 $\sigma_p$ 在两边都不被改写。
   - child 部分逐 key 比较。由推论 A，可以先固定任意一种遍历顺序；对每个 support 中的 key，左侧是先执行 $op_1(k)$ 再执行 $op_2(k)$；右侧是执行 $NormSeq(op_1(k),op_2(k))$。若其中之一为 $Keep$，结论显然；若二者都是真实 delta，则由归纳假设在子路径 $p\cdot k$ 上成立。
   - 对 $MB/BB$ 中不在最终 support 的 key，特别是 $k\in Dom(\delta_1)\setminus Dom(\delta_2)$，左侧虽然可能先在该 key 下产生任意复杂的中间子树，但由引理 B，第二步祖先 `BoundedMerge` 会把整个遗漏 child 子树连同其所有更深后代一并裁剪为 $\varnothing$。右侧把这些 key 从 support 中直接省略，语义上正对应同一个最终结果，因此是 sound 的。
   - 对 $BM$ 中 $k\in Dom(\delta_2)\setminus Dom(\delta_1)$，左侧第一步因 bounded 语义把该 key 当作已被裁剪为空；右侧通过令 $op_1(k)=Remove$、$op_2(k)=\delta_2(k)$，把“先裁剪旧子树，再从空节点重声明”的语义显式编码为同一条 tagged 规约链，因此一致。

故所有情况均成立。证毕。

## 8. 结合律

定义语义等价关系：对 $x,y\in D_p$，

$$
x\approx_p y
\quad\Longleftrightarrow\quad
\forall s\in S_p,
Apply_p(s,x)=Apply_p(s,y)
$$

记商空间为

$$
\bar D_p = D_p / {\approx_p}
$$

**引理 3** 关系 $\approx_p$ 是 $\otimes$ 的同余关系。若 $x\approx_p x'$ 且 $y\approx_p y'$，则

$$
x\otimes y \approx_p x'\otimes y'
$$

**证明**

对任意 $s\in S_p$，由定理 1 有

$$
Apply_p(s,x\otimes y)=Apply_p(Apply_p(s,x),y)
$$

由 $x\approx_p x'$ 得 $Apply_p(s,x)=Apply_p(s,x')$，于是

$$
Apply_p(Apply_p(s,x),y)=Apply_p(Apply_p(s,x'),y)
$$

再由 $y\approx_p y'$，对任意中间状态 $t$ 都有 $Apply_p(t,y)=Apply_p(t,y')$，因此

$$
Apply_p(Apply_p(s,x'),y)=Apply_p(Apply_p(s,x'),y')
$$

从而

$$
Apply_p(s,x\otimes y)=Apply_p(s,x'\otimes y')
$$

故 $x\otimes y \approx_p x'\otimes y'$。证毕。

**定理 2** 组合运算 $\otimes$ 在商空间 $\bar D_p$ 上满足结合律。即对任意 $x,y,z\in D_p$，有

$$
[(x\otimes y)\otimes z] = [x\otimes (y\otimes z)]
$$

**证明**

由引理 3，$\otimes$ 在商空间上的定义

$$
[x]\otimes [y] := [x\otimes y]
$$

与代表元选择无关。对任意 $s\in S_p$，由定理 1 反复应用得：

$$
Apply_p(s,(x\otimes y)\otimes z)
=
Apply_p(Apply_p(Apply_p(s,x),y),z)
$$

同样，

$$
Apply_p(s,x\otimes (y\otimes z))
=
Apply_p(Apply_p(Apply_p(s,x),y),z)
$$

故二者对任意 $s$ 的作用相同，于是

$$
(x\otimes y)\otimes z \approx_p x\otimes (y\otimes z)
$$

因此它们在商空间中的等价类相等。证毕。

这说明：真正成立结合律的对象不是“中间先执行出来的 tree”，而是 tree-delta carrier 的语义等价类。delta 本身是空间中的元素；树状态只是它们所作用的潜在空间。

## 9. 预合并与最终结果一致性

令

$$
Final = Validate\circ Pr\circ Norm : S\to Result
$$

其中 $Norm$、$Pr$、$Validate$ 都是确定性函数，并且只依赖最终潜在状态；它们只在整条 delta 链完成后执行。

**推论 1** 对任意基线状态 $s\in S$ 和任意 $x,y\in D$，有

$$
Final(Apply(Apply(s,x),y)) = Final(Apply(s,x\otimes y))
$$

由此，对任意有限链 $\Delta_1,\ldots,\Delta_n$，任意括号化方式都得到同一个最终结果。

## 10. 受限 XLang 片段与翻译

记 $Frag_p$ 为以下受限 XLang 片段在路径 $p$ 上的抽象语法：

$$
Frag_p ::= Remove_s \mid ReplaceXml_s(X) \mid Merge_s(\mu,\Gamma) \mid BoundedMerge_s(\mu,\Gamma)
$$

其中：

1. $X\in XmlSubtree_p$，并满足 rooted at $p$ 的 typed/well-formedness 约束。
2. $\mu\in M_p$ 表示该节点本地属性补丁。
3. $\Gamma$ 是有限 partial map，且 $Dom(\Gamma)\subseteq K_p$；对每个 $k\in Dom(\Gamma)$，有 $\Gamma(k)\in Frag_{p\cdot k}$。

定义翻译函数 $Tr_p:Frag_p\to D_p$：

$$
Tr_p(Remove_s)=Remove
$$

$$
Tr_p(ReplaceXml_s(X))=Replace(ReadState_p(X))
$$

$$
Tr_p(Merge_s(\mu,\Gamma)) = Merge(\mu,\delta)
$$

$$
Tr_p(BoundedMerge_s(\mu,\Gamma)) = BoundedMerge(\mu,\delta)
$$

其中对每个 $k\in Dom(\Gamma)$，有

$$
\delta(k)=Tr_{p\cdot k}(\Gamma(k))
$$

对 `replace` 中出现的 XML 子树，需要显式把它解释为状态而不是 delta。为此定义递归读入函数

$$
ReadState_p: XmlSubtree_p \to S_p
$$

其中 $XmlSubtree_p$ 表示满足以下条件的 XML 子树集合：

1. 根节点的 tag 与 stable identity 满足 $Desc(p)$；
2. 显式 child 的 stable key 全都属于 $K_p$；
3. 同一 sibling set 中每个 stable key 至多出现一次；
4. 对每个显式 child key $k$，对应子树属于 $XmlSubtree_{p\cdot k}$。

若一个 XML 子树在路径 $p$ 上具有根本地信息 $\ell=(\sigma_p,\alpha)$，并且其显式 children 的 stable-key 映射为 $k\mapsto X_k$，则定义

$$
ReadState_p(X)=Node_p\bigl((\sigma_p,\alpha),\{k\mapsto ReadState_{p\cdot k}(X_k)\}\bigr)
$$

未显式出现的 child key 不写入映射，按 $S_p$ 的约定解释为 $\varnothing$。这保证了 `replace` 放入的对象并不是口头上的“某棵 XML 树”，而是 typed carrier 中的一个良定型状态 $ReadState_p(X)\in S_p$。

因此本文不是“先证明函数结合，再口头说它像 XLang”，而是明确给出了一个受限 XLang 片段到 tree-delta carrier 的翻译。

在后文示例中，为了避免记号过长，固定以下约定：

1. stable key 取为 $(tag,id)$。
2. 若同一 sibling set 中 tag 唯一，则把完整 key $(tag,id)$ 简写为其 `id` 部分。
3. 外层 `<Page x:override="merge">...</Page>` 只表示根路径 $\epsilon$ 上的一个 `Merge_s` 包装。
4. 未显式写出 `x:override` 的嵌套子节点，一律解释为对应路径上的 `Merge_s`。
5. 对 `replace`，语法层写作 `ReplaceXml_s(X)`；其 XML 子树通过上面的 $ReadState_p$ 递归解释后，再翻译为语义层的 `Replace(ReadState_p(X))`。

## 11. 示例

下列示例使用如下标准实例：

1. 节点本地信息 $\ell$ 取为“固定 tag + 属性映射”。
2. 本地补丁 $\mu$ 取为有限属性补丁。
3. 本地作用 $act_p$ 取为按属性名右覆盖：补丁中提及的属性覆盖旧值，未提及属性保持不变。
4. $Norm=id$，$Pr$ 删除最终仍为空的虚拟节点，$Validate=Ok$。

### 11.1 示例 1：`remove` 后 `merge` 同 key 重建

基础模型：

```xml
<Page>
  <Panel id="p1">
    <Button id="btn" color="blue"/>
  </Panel>
  <Panel id="p2"/>
</Page>
```

三个 delta：

```xml
<!-- Δ1 -->
<Page x:override="merge">
  <Panel id="p1" x:override="remove"/>
</Page>

<!-- Δ2 -->
<Page x:override="merge">
  <Panel id="p1" x:override="merge">
    <Button id="btn" color="red"/>
  </Panel>
</Page>

<!-- Δ3 -->
<Page x:override="merge">
  <Panel id="p1" x:override="merge">
    <Button id="btn" text="OK"/>
  </Panel>
</Page>
```

翻译为根路径上的 delta 元素：

$$
x = Merge(\mathbf 1,\{p1\mapsto Remove\})
$$

$$
y = Merge(\mathbf 1,\{p1\mapsto Merge(\mathbf 1,\{btn\mapsto Merge(\mu_{red},\varnothing)\})\})
$$

$$
z = Merge(\mathbf 1,\{p1\mapsto Merge(\mathbf 1,\{btn\mapsto Merge(\mu_{ok},\varnothing)\})\})
$$

其中 $\mu_{red}$ 写入 `color=red`，$\mu_{ok}$ 写入 `text=OK`。

在 child `p1` 上，

$$
Remove\otimes Merge(\mathbf 1,\{btn\mapsto Merge(\mu_{red},\varnothing)\})
=
Replace(T_{btn,red})
$$

其中 $T_{btn,red}=Apply_{p1}(empty_{p1}, Merge(\mathbf 1,\{btn\mapsto Merge(\mu_{red},\varnothing)\}))$。因此

$$
x\otimes y = Merge(\mathbf 1,\{p1\mapsto Replace(T_{btn,red})\})
$$

再与 $z$ 组合：

$$
Replace(T_{btn,red})\otimes Merge(\mathbf 1,\{btn\mapsto Merge(\mu_{ok},\varnothing)\})
=
Replace(T_{btn,red+ok})
$$

于是

$$
(x\otimes y)\otimes z \approx x\otimes (y\otimes z)
$$

最终合并结果示例为：

```xml
<Page>
  <Panel id="p1">
    <Button id="btn" color="red" text="OK"/>
  </Panel>
  <Panel id="p2"/>
</Page>
```

### 11.2 示例 2：`replace` 后 `merge`

基础模型：

```xml
<Page>
  <Panel id="p1">
    <Field id="f1"/>
    <Field id="f2"/>
  </Panel>
</Page>
```

两个 delta：

```xml
<!-- Δ1 -->
<Page x:override="merge">
  <Panel id="p1" x:override="replace">
    <Field id="f1"/>
  </Panel>
</Page>

<!-- Δ2 -->
<Page x:override="merge">
  <Panel id="p1" x:override="merge">
    <Field id="f2" label="备注"/>
  </Panel>
</Page>
```

令 $T_{f1}$ 为只含 child `f1` 的 `p1` 状态，令 $\mu_{note}$ 写入 `label=备注`。则：

$$
x = Merge(\mathbf 1,\{p1\mapsto Replace(T_{f1})\})
$$

$$
y = Merge(\mathbf 1,\{p1\mapsto Merge(\mathbf 1,\{f2\mapsto Merge(\mu_{note},\varnothing)\})\})
$$

在 child `p1` 上有：

$$
Replace(T_{f1})\otimes Merge(\mathbf 1,\{f2\mapsto Merge(\mu_{note},\varnothing)\})
=
Replace(T_{f1,f2})
$$

其中

$$
T_{f1,f2}=Apply_{p1}(T_{f1}, Merge(\mathbf 1,\{f2\mapsto Merge(\mu_{note},\varnothing)\}))
$$

因此组合完成后，结果仍是 tree-delta carrier 中的元素：

$$
x\otimes y = Merge(\mathbf 1,\{p1\mapsto Replace(T_{f1,f2})\})
$$

最终合并结果示例为：

```xml
<Page>
  <Panel id="p1">
    <Field id="f1"/>
    <Field id="f2" label="备注"/>
  </Panel>
</Page>
```

### 11.3 示例 3：`bounded-merge` 裁剪后再 `merge` 重声明

基础模型：

```xml
<Page>
  <Panel id="p1">
    <Field id="f1"/>
    <Field id="f2"/>
    <Field id="f3"/>
  </Panel>
</Page>
```

三个 delta：

```xml
<!-- Δ1 -->
<Page x:override="merge">
  <Panel id="p1" x:override="merge">
    <Field id="f1" label="名称"/>
    <Field id="f3" label="临时"/>
  </Panel>
</Page>

<!-- Δ2 -->
<Page x:override="merge">
  <Panel id="p1" x:override="bounded-merge">
    <Field id="f2" label="备注"/>
  </Panel>
</Page>

<!-- Δ3 -->
<Page x:override="merge">
  <Panel id="p1" x:override="merge">
    <Field id="f3" label="恢复"/>
  </Panel>
</Page>
```

令 $\mu_{name},\mu_{note},\mu_{tmp},\mu_{restore}$ 分别表示对 `label` 的四个写入。则：

$$
x = Merge(\mathbf 1,\{p1\mapsto Merge(\mathbf 1,\{f1\mapsto Merge(\mu_{name},\varnothing),\;f3\mapsto Merge(\mu_{tmp},\varnothing)\})\})
$$

$$
y = Merge(\mathbf 1,\{p1\mapsto BoundedMerge(\mathbf 1,\{f2\mapsto Merge(\mu_{note},\varnothing)\})\})
$$

$$
z = Merge(\mathbf 1,\{p1\mapsto Merge(\mathbf 1,\{f3\mapsto Merge(\mu_{restore},\varnothing)\})\})
$$

先看 $x\otimes y$：在 child `p1` 上是 $MB$ 模式，因此结果模式为 `BoundedMerge`，support 由右侧决定：

$$
Supp^{MB}=\{f2\}
$$

故 `f1` 与旧 `f3` 被裁剪。再与 $z$ 组合：在 child `p1` 上是 $BM$ 模式，因此 support 取并集，允许右侧重新声明 `f3`：

$$
Supp^{BM}=\{f2,f3\}
$$

于是

$$
(x\otimes y)\otimes z
\approx
Merge(\mathbf 1,\{p1\mapsto BoundedMerge(\mathbf 1,\{f2\mapsto Merge(\mu_{note},\varnothing),\; f3\mapsto Replace(T_{f3,restore})\})\})
$$

其中

$$
T_{f3,restore}=Apply_{p1\cdot f3}(empty_{p1\cdot f3}, Merge(\mu_{restore},\varnothing))
$$

因为在 $BM$ 模式下，`f3` 对左项而言是“被 bounded 裁剪后缺失的 key”，所以该坐标上的组合不是普通 `Merge`，而是

$$
Remove\otimes Merge(\mu_{restore},\varnothing)=Replace(T_{f3,restore})
$$

这正是“先裁剪旧后代，再从空节点重声明”的 tagged 规约。最终可观测结果仍然与直接看到的 XML 一致。

这与 $x\otimes (y\otimes z)$ 语义等价。最终合并结果示例为：

```xml
<Page>
  <Panel id="p1">
    <Field id="f2" label="备注"/>
    <Field id="f3" label="恢复"/>
  </Panel>
</Page>
```

这个例子正体现了 tree 结构的真正复杂性：祖先路径 `p1` 上的 `BoundedMerge` 会支配所有后代 key 的保留/裁剪语义，因此组合规则必须递归进入 child map，而不能按扁平坐标独立组合。

## 12. 结论与实现边界

本附录得到的严格命题可以收束为一句话：

在 stable-key 树状态空间上，只要 delta 被建模为递归 tree-delta carrier 中的元素，并且组合规则按 tagged 规范化递归定义，那么 `remove`、`replace`、`merge`、`bounded-merge` 的组合在语义商空间上满足结合律，因而可以安全做语义级预合并；最终结果只依赖最终状态，不依赖括号化方式。

同时，本附录清楚保留以下实现边界：

1. 本文没有证明任意文本 patch 的结合律。
2. 本文没有把一般树级 `replace/remove` 退化为逐坐标 LWW。
3. 本文没有证明所有组合结果都能重新序列化为同类 DSL delta。
4. 本文没有替代实现符合性证明；若要将定理直接用于当前 XLang/Nop 实现，还需要额外证明真实 child 匹配、$empty_p$ 提升语义和各 override 分支都与本文 carrier 及组合规则对齐。
