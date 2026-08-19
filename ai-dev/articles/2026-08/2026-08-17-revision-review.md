# 对修订稿的审阅意见（v2 与 grc-dsh-theory-mapping 第二次修订）

> 日期：2026-08-17
> 审阅对象：
> - `2026-08-17-dsh-architecture-from-reversible-computation.v2.md`（工程视角）
> - `2026-08-17-grc-dsh-theory-mapping.md`（理论视角）
>
> 本文只给意见，不直接修改上述文件。

## 〇、本次审阅的 GRC 文献范围

这次意见是在仔细阅读以下文献之后写的，重点核对了 GRC 自己的论文与形式证明，而不只是 `docs/theory` 下的随笔文章：

- **GRC 英文主论文**：`docs/theory/paper/generalized-reversible-computation-paper-en.md`（含正文 1–9 节、附录 A–F）
- **GRC 中文全本**：`docs/theory/generalized-reversible-computation-paper.md`（含正文 1–9 节、附录 A–G；**§B.1.3、§B.2 十判据、附录 C、附录 D 最小代数核都在这份里**）
- **GRC 中文长文 v2**：`docs/theory/generalized-reversible-computation-paper-v2.md`
- **GRC 形式证明**：`docs/theory/proof-v2.md`、`docs/theory/grc-delta-associativity-formal-proof.md`
- **GRC 支撑文章**：`methodology-source.md`、`what-does-reversible-mean.md`、`explanation-of-delta.md`、`reversible-computation-runtime-evolution.md`、`counterintuitive-software-design-insights.md`、`reversible-computation.md`、`grc-question-answer-audit.md`

## 一、总体判断

这次修订有实质性进步：**术语表、结论标签、姊妹篇导览、Entry.update 字段级策略表、映射表的对应度图例（◼/▣/▢）、下一步工作分组**，都让两篇文章更好读、更可核。

但理论定位被改得**过强**了：从"GRC 与 dsh 互补"改成了"GRC 是统一总理论，dsh 只是细化，不是互补理论，没有任何 dsh 概念是 GRC 未覆盖的"。这个说法与 GRC 论文自己的表述（"优良代数性质是空间设计获得的工程结果，不是刚性前提"）有出入，也与修订稿自己保留的 §5/§6（"dsh 有而 GRC 缺或明显更弱"）相矛盾。

## 二、必须修正的问题（A 类）

### A1. 引用错位：§B.1.3 / 判据 #2 / 附录 C.2 不在英文主论文里

修订稿（mapping 文档 §摘要、§一、§三、§七，以及 v2 §5.3）多次引用"GRC 主论文 §B.1.3"、"§B.2 判据 #2"、"附录 C.2"。但：

- **这些章节只存在于中文全本 `generalized-reversible-computation-paper.md`**（附录 B、C），不在 `docs/theory/paper/generalized-reversible-computation-paper-en.md` 中。
- 英文主论文的对应内容是 **附录 F.3 "Active Selection and Design of Delta Space"** 和 **F.5 "Space Selection Evaluation Criteria"**；它没有 B.1.3。
- mapping 文档的"关联文档"把"GRC 主论文"标为英文主论文，参考文献却未收录中文全本 `generalized-reversible-computation-paper.md`；它还引用了 `counterintuitive-software-design-insights.md` 和 `reversible-computation.md`，也不在参考文献里。
- v2 的参考文献 [1]–[6] **没有收录任何一份 GRC 论文**，但正文 §5.3 引用了"GRC 主论文 §B.1.3"。

**建议**：
1. 在参考文献中补充 `generalized-reversible-computation-paper.md`（中文全本），并说明英文主论文、中文全本、v2 三份的关系；
2. 正文引用改为"中文全本 `generalized-reversible-computation-paper.md` §B.1.3 / §B.2 / 附录 C.2"或"英文主论文 F.3 / F.5"，不要笼统写"GRC 主论文"。

### A2. "Δ 有逆"是对 GRC 形式化的过度简化

修订稿把 GRC 核心模式写成"`App = F(X) ⊕ Δ`（**Δ 有逆**、F 满足结合律）"。但 GRC 论文自己的表述是**条件化的局部逆**：

- 中文全本术语表对 ⊕ 的定义是："在**记录必要前像且无信息丢失的条件下**，可以支持局部逆运算或变更剥离"。
- 中文全本附录 D.2.2：
  - **P10 局部补偿可逆性**：若所有被 Δ 覆盖或删除的坐标的**前像集合 Pre 被记录**，则可构造补偿差量 Δ⁻¹ 恢复；若存在未记录前像的覆盖或删除，则**一般不能保证无损恢复**。
  - **P11 不可逆删除**：若 Δ 含删除标记 ⊥ 且未记录前像，则**不存在**一个差量 Δ′ 能从 `P ⋄ Δ` 中无损恢复出 P。
- 英文主论文 §2.2 明确说：GRC 的代数性质"should be understood as referring to **an ideal property achievable through sound design, not a rigid prerequisite for all scenarios**"。

**建议**：把"Δ 有逆"改为"Δ 在记录前像、无删除信息丢失的条件下具备局部可剥离性/可补偿性"；否则读者会误以为 GRC 主张无条件逆元。

### A3. "F 满足结合律"同样需要加限定

- 英文主论文附录 C 自己声明："This appendix provides **an intuitive argument and design rationale for associativity, rather than a fully formal mathematical proof**. A complete formal system will be presented in future work."
- 中文全本附录 D 的严格结论是：**核心覆盖语义（LWW）直接满足结合律（P3）**；扩展语义（append、around、`replace/remove/merge` 与 children tree）**必须嵌入局部幺半群或统一端函数 denotation 后才继承结合律**；并发差量、图结构 Delta 代数仍未覆盖（`grc-question-answer-audit.md` 多处记录"部分回答"）。
- 真正的形式证明是 `proof-v2.md` 与 `grc-delta-associativity-formal-proof.md`，它们都针对**受限 carrier** 并列出实现符合性前提。

**建议**：把"F 满足结合律"改为"F 在受限 carrier 上具有结合律：LWW 覆盖语义直接成立，扩展语义条件化成立"。

### A4. "没有任何 dsh 概念是 GRC 未覆盖的"过强

mapping 文档 §三末尾的关键判断是："**没有任何 dsh 概念是'GRC 未覆盖'的**——所有 dsh 概念都是 GRC 模式在运行时域的具体形态，差异只在形式化深度"。

这个判断在**概念原型层面**大体成立，但作为客观结论有两个问题：

1. **reactive coeffects 的形式机制在 GRC 文献中确实没有对应**：coeffect specification、coeffect isolation（Definition 28）、interception、activating/deactivating/neutral 变更分类，这些不是"形式化深度不同"，而是"GRC 侧没有这个机制"。GRC 只有 `reversible-computation-runtime-evolution.md` 里的被动失效重算。修订稿自己在 §五 第 3 条也承认这一点。
2. 把这类情形全部标记为"▣ GRC 概念 + dsh 运行时形式化"，是一种**解释立场**（先把 dsh 概念重新描述成 GRC 概念，再说 GRC 覆盖了它），而不是可直接核验的文献对应。如果保留这个说法，应明确标注"这是本文作者的诠释框架"。

**建议**：改为"在概念原型层面，dsh 的每个核心观念都能在 GRC 文献中找到对应；在形式化层面，dsh 在运行时域贡献了 GRC 目前没有的机制与定理"。

### A5. "不是互补理论"与自己的 §5/§6、v2 结论矛盾

mapping 文档标题/摘要说"dsh 是 GRC 在运行时空间的细化，**不是另一套互补理论**"；但同一文档 §五、§六仍然列"dsh 论文有、而 GRC 缺或明显更弱的部分"，并且 §六说"**目前没有一个理论同时覆盖两层**；这是下一步理论工作最自然的接口"。v2 §5.3 说"不是两套互补的理论"，但 v2 结论和 §5.5 仍说"两者合在一起才构成完整的可逆计算图景"。

"缺一块、补一块"就是互补。"同一范式内的两个域"与"形式化互补"并不冲突，不必否定"互补"这个词。

**建议**：统一为"**Nop 与 dsh 是 GRC 同一范式在结构域和运行时域的两个实例化；两个域的形式化目前互补（结构域 GRC 已证，运行时域 dsh 已证），未来可统一**"。

## 三、需要讨论的论点（B 类）

### B1. "dsh 不是 GRC 的应用实例"是措辞过度

细化（refinement）本来就是"应用实例"的一种。用户原始问题"dsh 是否可以看作 GRC 的具体应用实例"，更准确的回答是："**可以，但它是运行时域的细化实例，而且贡献了 GRC 缺的运行时形式化**"。现在 mapping 文档的措辞（"不是应用实例也不是互补理论"）容易让读者误以为 dsh 与 GRC 无关。

### B2. "完整形式化图景"与"目前没有一个理论同时覆盖两层"自相矛盾

v2 §5.3 说"Nop 的差量代数证明 + dsh 的运行时定理 = GRC 在两个域的**完整形式化图景**"；mapping §六说"目前没有一个理论同时覆盖两层"。应统一为："**各自域的形式化已完成初步工作，跨域统一接口待做**"。

### B3. 映射表中"dispatch mode 五类"的 GRC 出处不足

mapping 文档把 emit/parallel/serial/bail/waterfall 对应为"GRC 的'作用域 + 范围约束'原则"，但 GRC 文献中没有直接讨论事件分发模式。这行建议标注为"**本文作者的诠释**"，或移到"dsh 具体实现选择"类别。

### B4. 三份 GRC 论文的关系应先说明

`docs/theory/paper/generalized-reversible-computation-paper-en.md`、`generalized-reversible-computation-paper.md`（中文全本，附录 A–G）、`generalized-reversible-computation-paper-v2.md`（中文长文 v2）三者内容、详略、形式化深度都不同。mapping 文档应在一开始说明哪份是"主论文"、哪份用于哪个引用，避免读者按图索骥找不到 §B.1.3。

## 四、值得保留的改进（肯定清单）

1. **v2 的术语表**：按出现顺序解释 effect/fiber/coeffect/realm/inertia 等，降低门槛，建议保留并继续扩充。
2. **v2 §4.1–4.8 的"结论标签"**：每节一句话收束，长文导航效果好。
3. **v2 §5.2 的 Entry.update 字段策略表**：这是本次修订最扎实的增量，把"per-field reconciliation"落到了字段级证据。
4. **mapping 文档的对应度图例（◼/▣/▢）**：比原来的"完全/部分"更清晰。
5. **mapping 文档的 §八 下一步工作**：按 Nop/GRC/dsh/本研究四方分组，可执行性强。

## 五、附：本次核对的关键原文（供修订者直接使用）

以下引文可直接用于修正上述问题。

1. **中文全本 `generalized-reversible-computation-paper.md` §B.1.3**：
   > "GRC的一个关键创新在于，它将软件工程的核心挑战之一，从'被动地处理源码变更'，引导至'**主动地设计一个承载变化的、具有优良语义坐标的结构空间**'。"

2. **中文全本 §B.2 判据 #2**：
   > "**主动空间设计 (Active Space Design)**: 是否引导开发者主动构造一个承载'变化'的、具有优良性质的表达空间。"

3. **中文全本 附录 C.2**：
   > "因此，GRC的本质不是被动地接受一个给定的表示空间，而是**主动地构造一个更适合当前领域的差量空间**，使得软件的构造与演化过程变得更加精确、可控和自动化。"

4. **中文全本 术语表（⊕ 词条）**：
   > "它被设计为**非侵入性**的；在记录必要前像且无信息丢失的条件下，可以支持局部逆运算或变更剥离。"

5. **中文全本 附录 D.2.2**：
   > "**P10 - 局部补偿可逆性**：若所有被 `Δ` 覆盖或删除的坐标在 `P` 中的前像集合 `Pre` 被记录，则可构造一个补偿差量……若存在未记录前像的覆盖或删除，则一般不能保证无损恢复。"
   > "**P11 - 不可逆删除**：若 `Δ` 中包含任何删除标记 `⊥`，且未记录被删除节点的前像，则不存在一个差量 `Δ′` 能从 `P ⋄ Δ` 中无损地恢复出 `P`。"

6. **英文主论文 §2.2**：
   > "subsequent discussion of GRC's 'algebraic nature' should be understood as referring to an ideal property achievable through sound design, **not a rigid prerequisite for all scenarios**."

7. **英文主论文 附录 C 开头**：
   > "this appendix provides an intuitive argument and design rationale for associativity, **rather than a fully formal mathematical proof**. A complete formal system will be presented in future work."

8. **英文主论文 F.3**：
   > "The paradigm innovation advocates: 1. Actively construct/select structural spaces with semantic coordinates… 2. Define minimal primitives for Delta within this space, giving local operations good properties (determinism, reversible stripping, compositional closure)…"

9. **`counterintuitive-software-design-insights.md:252`**：
   > "在可逆计算的架构下，加载器已经被赋予了生成器的全部职责，它的行为已经从被动读取转变为主动构造。"

10. **`reversible-computation.md:256`**：
    > "人们却没有合适的手段去主动构造一个指定的差量切片出来……差量构成了一个异常丰富的结构空间……"
