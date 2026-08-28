# 从搬运到核心：hunk 换位、merge 对称与良态 lens 组合子的机器可核证明

> 本文是 GRC 形式化系列第八篇。前文：[《差量传输理论》](../../ai-dev/analysis/2026-08/2026-08-26-grc-delta-transport-theory.md)、[《生成器看见了什么》](./grc-maximal-transportable-substructure.md)、[《差量之塔有多深》](./grc-delta-tower-classification.md)、[《沿 Koopman 路径的深层挖掘》](./grc-koopman-deep-stages.md)、[《正负分离合并与双向伴随》](./grc-split-merge-and-bidirectional-ladder.md)、[《差量空间的线性化》](./grc-delta-space-linearization.md)、[《可逆不是逆向运行》](./dsh-architecture-from-reversible-computation.md)。
>
> 机器证明：`ai-dev/articles/formal/PatchHunk.v` 与 `ai-dev/articles/formal/LensCombinators.v`，共 472 行，**全部经 Rocq（Coq）9.2.0 编译通过**。

## 摘要

此前系列有一个诚实的自我评估：所有证明停留在“公理展开”到“多步链但无非平凡归纳”，Darcs 的 hunk 换位正确性与 merge 对称、Foster 的组合子定律归纳证明、双向化完备性——**三个核心结果一个都没有达到，也没有尝试达到**。本文直接做这三件事，并把每一件事落到机器可核证明：

1. **Darcs 式 primitive patch 核心**：定义带名字与上下文的 hunk patch、换位关系与 merge 构造，机器证明换位方图（commute square）、换位对合性、逆对偶性、merge 正确性与 **merge 对称性**（`merge(q,p) = swap(merge(p,q))`）。
2. **Foster 式 lens 组合子核心**：对 identity、composition、product、定长向量 map 四种结构组合子，机器证明它们保持 lens 三律（GetPut/PutGet/PutPut）；证明是**对组合子结构的归纳**，不是逐例测试。
3. **双向化的完整性与边界**：对一个 `get` 定义语言（id、组合、积、map）构造**语法导向的双向化器 BFF**，机器证明它是全函数、对每个 `get` 定义返回良态 lens、且不改变视图；再用一个精确的反例证明**任意语义 get 的双向化不完备**（`andb : bool×bool → bool` 不存在任何良态 put）。

与前七篇“搬运 + 识别”不同，本文的全部主定理都在仓库中作为 Coq 源文件存在，读者可以重新编译与独立核查。诚实边界也照旧列出：本文证明的是 primitive hunk 宇宙（无 conflictor），lens 组合子覆盖结构核心（定长对齐），不是 Darcs 全仓库理论与 Foster 原始论文中递归树变换的全部语法。

## 0. 三处旧缺口与本文的关闭方式

| 旧缺口 | 本文结果 | 机器证明名 | 状态 |
|---|---|---|---|
| Darcs hunk 换位正确性 | 换位方图：`p;q` 与 `q';p'` 都合法且终点相同 | `commute_hunk_square` | ✅ 关闭（primitive hunk 层） |
| Darcs merge 对称 | `merge(p,q)=(r,s) ⟹ merge(q,p)=(s,r)` | `merge_hunk_symmetric` | ✅ 关闭（primitive hunk 层） |
| Foster 组合子定律归纳证明 | id/∘/×/map 四组合子保持三律 | `id_lens_laws` 等 4 个 | ✅ 关闭（结构核心） |
| 双向化完备性 | 结构 get 语言上 BFF 全函数且保律、保视图 | `bff_laws`, `bff_get_correct` | ✅ 关闭（语法片段） |
| 双向化的真实边界 | `andb` 反例：任意语义 get 无 BFF | `no_lens_for_and_get` | ✅ 精确否定 |

“关闭”的精确范围在 §4 的诚实台账中逐条限定；本文没有把困难部分再假设掉。

## 1. Primitive patch 宇宙

### 1.1 Hunk、上下文与应用

行是自然数，文件是行的列表：

```coq
Definition Line := nat.
Definition File := list Line.

Record Hunk := MkHunk {
  hname : nat;
  hoff  : nat;
  hold  : File;
  hnew  : File;
}.
```

一个 hunk 是**带名字**的区间替换：在偏移 `hoff` 处把上下文 `hold` 替换为 `hnew`。应用采用关系式定义，上下文匹配不是运行时 `option` 判断，而是构造器的前置分解：

```coq
Inductive ApplyH : Hunk -> File -> File -> Prop :=
| mkApplyH : forall (h : Hunk) (pre post : File),
      length pre = hoff h ->
      ApplyH h (pre ++ hold h ++ post) (pre ++ hnew h ++ post).
```

即 `ApplyH h s t` 当且仅当 `s` 可以唯一地写成 `pre ++ old ++ post` 且 `|pre| = off`，此时 `t = pre ++ new ++ post`。两条基础性质被机器证明：

- `applyH_deterministic`：同一 hunk 同一输入至多一个输出——**应用是函数**；
- `applyH_inv_hunk`：`inv_hunk h`（把 `new` 换回 `old`）从 `t` 回到 `s`——**hunk 自带逆**。

### 1.2 换位：把“不重叠”编码为上下文分解

Darcs 的 hunk 换位只允许两种情形：第二个 hunk 完全在第一个之前，或完全在第一个之后；任一重叠（包含空区间边界上的依赖）都不换位。本文不写减法偏移的 case 分支，而把两种情形的**共同不变量**——中间三段的上下文分解——写成关系：

```coq
Inductive CommuteHunkAt : File -> Hunk -> Hunk -> Hunk -> Hunk -> Prop :=
| commute_q_before_p_at :
    forall (pre mid post : File) (np nq : nat)
           (oldp newp oldq newq : File),
      CommuteHunkAt
        (pre ++ oldq ++ mid ++ oldp ++ post)          (* A *)
        (MkHunk np (length pre + length oldq + length mid) oldp newp)  (* p *)
        (MkHunk nq (length pre) oldq newq)            (* q *)
        (MkHunk nq (length pre) oldq newq)            (* q' *)
        (MkHunk np (length pre + length newq + length mid) oldp newp)  (* p' *)
| commute_q_after_p_at :
    forall (pre mid post : File) (np nq : nat)
           (oldp newp oldq newq : File),
      CommuteHunkAt
        (pre ++ oldp ++ mid ++ oldq ++ post)          (* A *)
        (MkHunk np (length pre) oldp newp)            (* p *)
        (MkHunk nq (length pre + length newp + length mid) oldq newq)  (* q *)
        (MkHunk nq (length pre + length oldp + length mid) oldq newq)  (* q' *)
        (MkHunk np (length pre) oldp newp).           (* p' *)
```

把偏移写回 Darcs 记法，两个构造器正是教科书里的两条 offset 调整公式：

- `q` 在 `p` 之前：`q'` 不动，`p'` 的偏移从 `op` 调整为 `op + (|newq| − |oldq|)`；
- `q` 在 `p` 之后：`p'` 不动，`q'` 的偏移从 `oq` 调整为 `oq − (|newp| − |oldp|)`。

Coq 中不出现自然数减法，因为 `pre/mid/post` 三个见证把“调整后的偏移”直接写成长度和；**这正是 Darcs 实现中要维护的上下文不变式**，此处被提升为构造器的类型。

### 1.3 定理 1（换位方图，hunk 换位正确性）

**定理（`commute_hunk_square`）**。若 `CommuteHunkAt A p q q' p'`，则存在 `B B' C` 使

```text
ApplyH p A B  ∧  ApplyH q B C  ∧  ApplyH q' A B'  ∧  ApplyH p' B' C
```

即 `p;q` 与 `q';p'` 两条路径都可应用，且终点同为 `C`。

**证明**（机器核对，两个构造器逐一分派）：

- `q` 在 `p` 之前：取
  `B = pre ++ oldq ++ mid ++ newp ++ post`，
  `B' = pre ++ newq ++ mid ++ oldp ++ post`，
  `C = pre ++ newq ++ mid ++ newp ++ post`。
  四条 `ApplyH` 各由一段 `pre0/post0` 见证：`p` 用 `pre0 = pre++oldq++mid`，`q` 与 `q'` 用 `pre0 = pre`，`p'` 用 `pre0 = pre++newq++mid`；长度等式由 `length_app` 与 `lia` 关闭。
- `q` 在 `p` 之后对称：`p` 与 `p'` 用 `pre0 = pre`，`q` 用 `pre0 = pre++newp++mid`，`q'` 用 `pre0 = pre++oldp++mid`。

两个分支的公共终点 `C` 就是两条路径的同一状态。∎

**这一定理的内容就是 Darcs hunk 换位正确性的核心**：不是“算法返回了某个结果”，而是“返回的 `q',p'` 在新的上下文中合法，且方图闭合”。换位前后的补丁名与 `old/new` 内容保持不变，只有偏移按上下文分解调整。

### 1.4 定理 2（换位对合）

**定理（`commute_hunk_self_inverse`）**。

```text
CommuteHunkAt A p q q' p'  ⟹  CommuteHunkAt A q' p' p q
```

**证明**。构造器互换即可：`q-before-p` 的正向换位恰是 `q'-after-p'` 的反向换位，反之亦然。∎

这是 Darcs 的“commute is an involution”：把换位结果再换位一次，回到原来的 `(p,q)` 与同一上下文 `A`。

### 1.5 定理 3（逆对偶，带真实终点）

对逆补丁，Darcs 有一个容易在纸面证明中漏掉终点条件的对偶律：若 `p;q` 换位为 `q';p'`，则 `inv q; inv p` 换位为 `inv p'; inv q'`，且起点必须是 `p;q` 的**真实公共终点**。Coq 证明中我们区分两个版本：

- `commute_hunk_inv_dual`：存在某个终点 `C` 使对偶换位成立；
- `commute_hunk_inv_dual_ctx`：**给定** `ApplyH p A B` 与 `ApplyH q B C`，对偶换位恰在 `C` 成立。

后者是 merge 对称证明的真正前提。证明流程：对 `CommuteHunkAt` 反演，得到两个构造器之一；用 `applyH_inv_iff` 构造该分支的规范 `B0,C0`；由 `applyH_deterministic` 得 `B=B0`、`C=C0`；代入后应用逆对偶的构造器。∎

### 1.6 Merge 的构造与两个定理

**定义**。`MergeAt O p q r s` 表示 `p,q` 从同一上下文 `O` 出发，存在公共终点 `C` 使 `p;r` 与 `q;s` 都合法：

```text
∃ A B C, ApplyH p O A ∧ ApplyH q O B ∧ ApplyH r A C ∧ ApplyH s B C
```

标准 Darcs 构造：先对 `inv p` 与 `q` 换位：

```coq
Definition merge_by_commute (O : File) (p q r s : Hunk) : Prop :=
  exists A B,
    ApplyH p O A /\
    ApplyH q O B /\
    CommuteHunkAt A (inv_hunk p) q r (inv_hunk s).
```

**定理 4（merge 构造正确，`merge_by_commute_correct`）**。

```text
merge_by_commute O p q r s  ⟹  MergeAt O p q r s
```

**证明**。由换位方图，`inv p;q` 与 `r;inv s` 终点相同；再由应用确定性与逆补丁往返，得到 `p;r` 与 `q;s` 的公共终点。∎

**定理 5（merge 对称，`merge_hunk_symmetric`）**。

```text
merge_by_commute O p q r s  ⟹  merge_by_commute O q p s r
```

即：以 `q` 为主分支、`p` 为副分支做 merge，得到的结果恰是交换后的 `(s,r)`。**证明**。由定理 3 的真实终点版本：

```text
CommuteHunkAt A (inv p) q r (inv s)
⟹ CommuteHunkAt B (inv q) p s (inv r)
```

其中 `B` 是 `q` 的落点；再展开 `merge_by_commute` 即得。∎

**这就是 Darcs merge either way 在 hunk 层的机器证明**：merge 结果不依赖你把哪个补丁当作“主分支”。注意对称性不是定义上的平凡重命名——两个方向使用的是不同的上下文 `A` 与 `B`，证明必须携带对方分支的落点。

## 2. Foster 式 lens 组合子

### 2.1 Lens 与三律

```coq
Record Lens (S V : Type) : Type := {
  lget : S -> V;
  lput : S -> V -> S;
}.

Record LensLaws {S V} (l : Lens S V) : Prop := {
  law_getput : forall s, lput s (lget s) = s;
  law_putget : forall s v, lget (lput s v) = v;
  law_putput : forall s v1 v2, lput (lput s v1) v2 = lput s v2;
}.
```

### 2.2 四个结构组合子与定律

**Identity**：`get s = s`，`put _ v = v`。`id_lens_laws`。

**Composition** `comp_lens l1 l2`：

```text
get s = get₂ (get₁ s)
put s v = put₁ s (put₂ (get₁ s) v)
```

`comp_lens_laws` 的 PutPut 是 Foster 证明中唯一非平凡的一步：先用 `law_putget l1` 把内层 `get₁ (put₁ s w₁)` 化简为 `w₁`，再用 `law_putput l2` 与 `law_putput l1` 收口。这正是组合子定律归纳证明的标准形态：**每个组合子的证明只依赖子 lens 的三律，不依赖子 lens 的实现**。

**Product**：`(lget₁ a, lget₂ b)` / `(lput₁ a v, lput₂ b w)`。`prod_lens_laws` 逐分量调用子律。

**Map（定长向量）**：Foster 的 `map` 组合子按位置对齐。本文用 `Vector.t A n` 把“等长对齐”变成类型条件，不给长度不匹配留后门：

```text
get(v)  = Vector.map lget v
put(s,v)= Vector.map2 lput s v
```

`map_lens_laws` 的证明逐位置进行：用 `Vector.eq_nth_iff` 把向量等式化为任意指标 `p : Fin.t n` 上的元素等式，再用 `nth_map`/`nth_map2` 展开，最后应用子 lens 对应定律。三条律都如此关闭，本质是对 `n` 维向量上的 map/map2 的结构归纳。

**定理 6（组合子保持三律）**：上述四个构造器都把良态 lens 映为良态 lens。∎

## 3. 双向化：语法片段上的完备性与语义边界

### 3.1 一个 get 定义语言

```coq
Inductive GetExpr : Type -> Type -> Type :=
| GId   : forall A, GetExpr A A
| GComp : GetExpr A B -> GetExpr B C -> GetExpr A C
| GProd : GetExpr A1 B1 -> GetExpr A2 B2 -> GetExpr (A1*A2) (B1*B2)
| GMap  : forall {A B} (n:nat), GetExpr A B -> GetExpr (Vector.t A n) (Vector.t B n).
```

`eval_get` 按语法求值；`bff` 按语法构造 lens：

```text
bff(GId)        = id_lens
bff(GComp e1 e2)= comp_lens (bff e1) (bff e2)
bff(GProd e1 e2)= prod_lens (bff e1) (bff e2)
bff(GMap n e)   = map_lens (bff e)
```

### 3.2 定理 7 与定理 8（BFF 完备）

**定理 7（`bff_laws`）**：对任意 `S V` 与任意 `e : GetExpr S V`，

```text
LensLaws (bff e)
```

**证明**：对 `e` 结构归纳，逐构造器应用定理 6。这就是“双向化完备性”在结构片段上的精确形式：**BFF 是全函数，对语言中每一个 get 定义都返回良态 lens**。∎

**定理 8（`bff_get_correct`）**：`lget (bff e) s = eval_get e s`。BFF 不改变视图。∎

### 3.3 定理 9（语义 BFF 不完备）

把“双向化完备”理解为“任意 `get : S→V` 都有良态 put”，则它是假的。反例取 `S = bool × bool`、`V = bool`、`get = andb`。

**定理（`no_lens_for_and_get`）**：不存在 `put : S→bool→S` 同时满足 GetPut、PutGet、PutPut。

**证明**（机器核对）。

1. `and_get s = true` 当且仅当 `s = (true,true)`（`and_get_eq_true`）。
2. 对三个 false-纤维源 `(false,false)`、`(false,true)`、`(true,false)`，PutGet 迫使
   `put s true = (true,true)`。
3. 对每个这样的 `s`，PutPut 与 GetPut 给出

```text
put (true,true) false = put (put s true) false = put s false = s
```

左边与 `s` 无关，右边分别等于三个不同的 `s`，矛盾。∎

**读法**：BFF 的完备性属于**语法**（你可以从 get 的定义构造 put），不属于任意**语义函数**；任意 get 可双向化当且仅当源能被分解为“视图 × 补语”。这个精确边界同时回答了前文“任意良态 lens 是否都来自 delta-conservative 生成器”所缺的否定侧：不是。

## 4. 诚实台账与边界

| 结果 | 性质 | 说明 |
|---|---|---|
| `commute_hunk_square` | **本文机器自证** | hunk 换位方图；两构造器覆盖 q-before-p / q-after-p |
| `commute_hunk_self_inverse` | **本文机器自证** | Darcs commute involution |
| `commute_hunk_inv_dual_ctx` | **本文机器自证** | 逆对偶在真实终点成立；旧文未触及 |
| `merge_by_commute_correct` | **本文机器自证** | 由换位构造 merge |
| `merge_hunk_symmetric` | **本文机器自证** | Darcs merge either way（hunk 层） |
| 四个 lens 组合子定律 | **本文机器自证（结构归纳）** | Foster 结构核心；map 采用定长向量 |
| `bff_laws`, `bff_get_correct` | **本文机器自证** | 语法导向 BFF 完备 |
| `no_lens_for_and_get` | **本文机器自证** | 语义 BFF 的精确反例 |

**未覆盖，如实声明**：

1. **没有 conflictor**。本文是 primitive hunk 宇宙：重叠区间不进入 `CommuteHunkAt`，merge 对冲突没有构造结果。Darcs 的冲突表示（conflictor/merger）与全仓库 patch 序列的 commute 归纳仍开放。
2. **没有可执行 commute 算法的提取与失败判据证明**。本文换位是关系式定义，两个构造器**就是**算法的规范；从关系生成 `option` 算法并证明 `None ⟺ 区间重叠` 是下一步。
3. **map lens 是定长向量**，不是 Foster 论文中作用于任意递归树、允许子节点增删的 `map`。结构核心的定律归纳已经覆盖，任意递归树与对齐迹仍开放。
4. **BFF 语言不含递归与常数**。它证明的是 id/∘/×/map 片段上的全函数性与正确性；Voigtländer 的 parametricity BFF 不在此文。
5. 换位定理的两个构造器允许 `old/new` 为空区间；没有为“空 hunk 是否合法”额外立法，因此结论对空区间边界也成立。

**与前文的关系**：第三篇定理 D 证明 delta-conservative 生成器导出良态 lens；本文定理 6/7 证明良态性在结构组合子下封闭、并可语法导向地构造。两者方向互补：前文给**语义充分条件**，本文给**语法构造与完备性**。Darcs 部分则第一次把“冲突源于不可换位而非逆缺失”从对照陈述推进到可核验的换位/merge 定律。

## 5. 机器证明如何复核

```bash
cd ai-dev/articles/formal
coqc -q PatchHunk.v
coqc -q LensCombinators.v
```

`PatchHunk.v` 只依赖标准库 `List`/`Arith`/`Lia`；`LensCombinators.v` 额外使用 `Bool`/`Vector`/`Fin`。全部主定理名见 `ai-dev/articles/formal/README.md`。

## 6. 工程推论

1. **差量包 merge 对称可机器验证**：对可逆、坐标不相交的差量（hunk 宇宙），客户差量包从公共基线向两分支合并的结果与分支主次无关；GRC 的 Delta VFS 预合并可把该性质作为静态检查项。
2. **换位方图是升级重放的正确性契约**：`p;q = q';p'` 表明把上游差量在生成链上换位不会改变终态；这比前文的“右推共轭 confluence”更接近 Darcs 实现层。
3. **结构生成器的反向传播可以语法合成**：凡 get 由 id/∘/×/map 表达，BFF 自动给出满足三律的 put——设计器同步、ORM→View 投影等结构映射不必手写 put。
4. **任意生成器不可自动双向化的边界可检测**：`andb` 式“多源一像”的 get 无法有良态 put；此时必须先显式化补语（即第三篇的 `S ≅ V × C` 路线），不能指望通用 BFF。

## 开放问题

1. 可执行 hunk commute 算法：从 `CommuteHunkAt` 提取 `option (Hunk×Hunk)`，并机器证明 soundness（返回即满足关系）与 completeness（`None` 当且仅当区间重叠/上下文依赖）。
2. Primitive patch 序列的 commute/merge 归纳与 conflictor：把 hunk 宇宙扩成含 conflictor 的命名补丁语言，重做 merge 对称。
3. Foster 原始递归树语言的全部组合子定律：在含递归、对齐迹与子节点增删的 tree 宇宙中机器重做 `map`/`fork` 等定律。
4. Voigtländer parametricity BFF：把本文语法 BFF 升级为对多态 get 的 free theorem 双向化，并给出收敛/完备条件。
5. GRC 不可逆型（tree-delta/tombstone）上的弱换位与弱 merge 对称：以 tagged 规范化组合表为 coherence 数据，求不可逆宇宙中定理 1–5 的对应物。

## 结论

本文把系列从“搬运 + 识别”推进到**核心证明层**：Darcs hunk 换位方图、merge 对称、Foster 结构组合子三律、BFF 语法完备性及其语义反例，全部是机器可核的定理。472 行 Rocq 证明不是“数百行不变式维护”的逐行复刻，而是把 Darcs 实现中最贵的不变式——上下文分解与偏移调整——提升到关系构造器中，使正确性证明短而完整。下一篇文章应做两件事之一：从该关系提取可执行 commute 并证明失败判据，或把 conflictor 引入序列层；两者都已在本文的开放问题中给出了精确的机器可陈述目标。
