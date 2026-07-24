# nop-entropy 理论基石与设计哲学：可逆计算 / XLang / XDSL / XDef

> Status: resolved
> Date: 2026-07-24
> Scope: `docs/theory/`（GRC 论文与 XLang 设计）、`nop-kernel/nop-xlang`、`nop-kernel/nop-xdefs`、`nop-kernel/nop-core`；理论公理 → 工程机制 → 代码锚点的端到端梳理 + 4 方向联网对标
> Conclusion: 可逆计算（GRC）的中心命题 `Y = F(X) ⊕ Δ` 在 nop-entropy 中落地为 XDef（元模型/坐标系）+ XDSL（可叠加差量语言）+ xpl（编译期 Generator）三件套；其差异化定位在于"差量与全量同构 + 结构层通用合并 + 条件化结合律"，使其区别于 MDSD（一次性 codegen）、MPS（AST projectional 组合）、DOP（语言绑定 delta）和 bx/lenses（view-source 双向同步）。
> Mission: nop-deep-analysis（Work Item A1）
> Superseded By: （本分析为 A2–A7 提供统一词汇表基线；若 A7 capstone 重新组织理论章节，则被替代）

## Context

- **要回答的问题**：可逆计算（Generalized Reversible Computation, GRC）原理如何在 nop-entropy 中落地？XLang/XDSL/XDef 三件套如何对应 GRC 的公理体系？与同类/下一代框架（MDSD、Language Workbench、Delta-Oriented Programming、双向变换）的差异在哪？
- **涉及模块/子系统**：`nop-kernel/nop-xlang`（DSL 解析与 Delta）、`nop-kernel/nop-xdefs`（元模型）、`nop-kernel/nop-core`（VFS/Delta 资源）。
- **约束**：本分析仅产出理论映射与差异化定位，不剖析核心引擎实现细节（A2）、不讲解代码生成管线（A3）。
- **来源基线**：`docs/theory/` 理论语料（充分但偏理论阐述）、`docs-for-ai/04-reference/source-anchors.md`（EXT-001~006, XLANG-001~008 工程锚点）。本分析通过 3 个并行子 agent 通读理论文档 + 11 个锚点源码交叉核对（全部 PASS）建立。

## 1. 可逆计算（GRC）公理体系

GRC 的中心命题（`docs/theory/generalized-reversible-computation-paper-v2.md` Abstract, line 10；§2.2 lines 55–79）：

```
Y = F(X) ⊕ Δ
```

- `X`：源模型 / 领域模型 / 配置模型（上游表示）
- `F`：确定性生成器 / 加载器 / 解释器
- `F(X)`：基线结构（baseline）
- `Δ`：在同一语义坐标系中表达的结构化差量（delta）
- `⊕`：差量合并/叠加算子（右覆盖 / Last-Write-Wins）
- `Y`：合并、规范化、验证后的目标模型/可运行制品

GRC 不是一组 Hilbert 式编号公理，而是分三层：方法论命题（构造关系）、设计学说（坐标系 + 潜在空间 + 终端验证）、形式命题（条件化结合律）。以下提炼 9 条核心公理，每条标注对应的代码锚点。

### 1.1 公理 A：构造关系自递归（创世差量）

公式递归适用；空基线即"创世差量" `M = ∅ ⊕ M`，多阶段管线可级联：

```
XMeta = F_orm(XORM) ⊕ Δ_meta
XView = F_meta(XMeta) ⊕ Δ_view
```

> 来源：`docs/theory/generalized-reversible-computation-paper-v2.md` §2.2 lines 72–77, §3.4 lines 170–182。

### 1.2 公理 B：差量分解（一等资产 + 差量与全量同构）

Δ 是可独立命名、存储、组合、审计的变化对象，表示新增/覆盖/删除/替换/追加/环绕。"差量与全量应尽量在同一坐标空间中同构表达，并由同一 schema 约束"——因此"差量的差量仍然是普通差量"。

> 来源：`docs/theory/generalized-reversible-computation-paper-v2.md` §2.4 lines 99–105；`docs/theory/grc-delta-associativity-formal-proof-v2.md` §1.3 lines 110–111（"基础模型和差量在代数层没有本质区别，都是 P 中对象"）。
>
> **工程映射**：`XNode = XNode + Delta`，`Model = Parser(XNode)`（见 §2.3）。差量与全量同构在 XDSL 体现为：一个 `.xdef` schema 同时约束基线与差量，差量文件本身就是一个合法的（稀疏）模型。

### 1.3 公理 C：Delta 结合律（条件化）

`docs/theory/grc-delta-associativity-formal-proof-v2.md` 在三个 carrier（承载空间）上证明差量链可换括号：

1. **坐标-tombstone carrier** `(P, ⊕, ∅)` — Theorem 1（lines 140–228）
2. **逐坐标局部运算 carrier** `(P_O, ⊗, ∅)` — Theorem 4（lines 685–713）
3. **树态端函数 carrier** `(NOp_p/≡_p, ⊙, [Id])` — Theorem 5 + 商幺半群 §9.4（lines 1625–1796）

**Corollary D**（§12.4 lines 2102–2126）：在满足假设 A1–A9（§0.1 lines 24–38）+ 10 项实现符合性（§13 lines 2130–2145）时，任意括号化得同一 `Ok(Model)` 或 `Err(ErrorSet)`。

> **工程含义**：结合律是 delta 栈可**预合并**（`Base ⊕ Δ_1 ⊕ ... ⊕ Δ_n ≡ Base ⊕ NF(Δ_1..Δ_n)`）、独立版本化、缓存、重新叠加到新基线的数学许可。没有它，客户定制、平台升级、本地补丁无法独立组合。
>
> **代码锚点**：合并执行在 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslExtender.java`（EXT-002），合并算子实现于 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/delta/DeltaMerger.java`（在 `XDslExtender` 构造器 L78 实例化，L457 调用 `merger.merge(...)`）。

### 1.4 公理 D：逆元 = 删除语义

> **精度提示**：口号"生成即逆元"**并非**本仓库三篇核心文档（paper-v2 / proof-v2 / overview）的字面引用。逆概念体现为：(a) 复用公式 `B = A + (-C)`——变化必含正元素与逆元素（`docs/theory/reversible-computation-theory-overview.md` lines 30–33, 85）；(b) 形式证明中的 tombstone `⊥_c`（proof-v2 §1.2）与 `Remove` 端函数（proof-v2 §5.4 line 852）。生成器 `F` 是确定性基线生产者，未被形式化为"逆"。

演化必然包含删除：传统继承/扩展"一般都没有提供真正的删除语义"（`docs/theory/delta-vs-extension.md` §2.2 line 97）。

> **代码锚点**：删除语义对应 `x:override="remove"`，定义于 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefOverride.java:22`（`REMOVE`，"删除基类中的节点"）。完整 8 种 override 模式见 §2.2。

### 1.5 公理 E：语义坐标系（stable key 由元模型声明）

坐标系是"由 DSL 元模型或领域结构提供的一组稳定寻址规则"，满足唯一性/稳定性/层级性/可规范化/可验证（`docs/theory/generalized-reversible-computation-paper-v2.md` §2.3 lines 81–97）。

> **关键否定**：**"类型系统和类-成员结构是有用的分类机制，但不是充分的坐标系统"**（§2.3 line 97）——数组下标在插入/排序后漂移。因此 stable key 必须由 XDef 元模型**显式声明**，而非默认所有 name/id 都有身份语义。
>
> **代码锚点**：坐标系来源是 XDef——`xdef:unique-attr` / `xdef:key-attr` 声明列表元素的稳定身份；`super:` 资源解析在 `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStore.java:251-294`（EXT-003, `getSuperResource`）。

### 1.6 公理 F：合并算子 ⊕ = 右覆盖（Last-Write-Wins）

```
(p ⊕ q)(c) = q(c)        若 c ∈ Dom(q)        // 右侧有定义则覆盖
(p ⊕ q)(c) = p(c)        若 c ∉ Dom(q) 且 c ∈ Dom(p)   // 右侧未定义则保留左侧
```

> 来源：`docs/theory/grc-delta-associativity-formal-proof-v2.md` §1.4 lines 116–134；§1.6 `NF_LWW` 规范形（lines 232–264）。

### 1.7 公理 G：XDef = 元模型 = 坐标系来源（语言即坐标系）

"XDef：语言即坐标系。XDef 是定义 XDSL 的元 DSL"（`docs/theory/generalized-reversible-computation-paper-v2.md` §5.2 line 259）。XDef 描述节点结构、属性、约束、stable key、对象映射、工具提示。

> **代码锚点**：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef:14-19`（EXT-001）是平台所有 DSL 的元模型入口。

### 1.8 公理 H：分级可逆性

"可逆"是分级复杂性治理原则，非运行时指令双射、非 Landauer 物理可逆。分四层：代数层（可组合差量）、表达层（多表示围绕一核条件往返）、过程层（后到差量可修正先到）、边界层（不可逆副作用显式隔离补偿）（§2.5 lines 107–116）。

### 1.9 公理 I：潜在结构空间 + S-N-V 阶段分离

合并须在潜在结构空间完成，保留 tombstone/顺序约束/虚拟节点等"中间信息"；**中途有损投影会破坏结合律**（proof-v2 §2.4 反例 lines 354–404）。S-N-V 三阶段：

| 阶段 | 名称 | 职责 |
|---|---|---|
| S | Structure Merge | 解析 + delta 合并 + 生成式扩展，**保留潜在证据** |
| N | Normalization | 展开简写、默认值、归约顺序约束，产规范结构 |
| V | Validation / Compilation | 业务校验 + 编译为强类型静态运行时模型 |

> **核心工程边界**："运行时面对的是已经被'烘焙'好的静态模型，无 delta 历史知识"（§5.6 lines 334–344）。
>
> **代码锚点**：解析链 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/DslModelParser.java`（EXT-004）；`INeedInit.init()` 调用点在 `DslModelParser.java:133-134`（`if (!disableInit && model instanceof INeedInit) ((INeedInit) model).init();`）。

### 公理→代码锚点对照总表

| 公理 | 含义 | 代码锚点（source-anchors.md ID） |
|---|---|---|
| A 构造关系自递归 | `Y = F(X) ⊕ Δ` 级联 | codegen 模板 `*_codegen/gen-orm.xgen`（GEN-002）；`GlobalFunctions.loadDeltaJson`（EXT-005） |
| B 差量与全量同构 | 差量即稀疏模型，同 schema | `XDslExtender.java`（EXT-002）；`xdsl.xdef` 同时约束基线与差量 |
| C 结合律（条件化） | delta 栈可预合并 | `DeltaMerger.java` + `XDslExtender.java:457` |
| D 逆元=删除 | `x:override="remove"` | `XDefOverride.java:22`（REMOVE） |
| E 坐标系 | stable key 显式声明 | `xdsl.xdef`（EXT-001）；`DeltaResourceStore.java`（EXT-003） |
| F 右覆盖合并 | LWW | `DeltaMerger.java` |
| G XDef=坐标系 | 语言即坐标系 | `nop/schema/xdsl.xdef`（EXT-001, XLANG-003） |
| H 分级可逆 | 治理原则 | （设计学说，无单一锚点） |
| I S-N-V 阶段 | 加载期合并，运行时静态 | `DslModelParser.java`（EXT-004）+ `INeedInit` |

## 2. XLang / XDSL / XDef 三件套工程映射

`docs/theory/xlang-explained3.md` §3 line 38 给出 XLang 的组成：

> "XLang = XScript + Xpl + XDef + MetaProgramming + DeltaProgramming"

而 `docs/theory/why-xlang-is-innovative.md` §一 line 13 定位：

> "XLang 是世界上第一个在语言中明确定义领域结构坐标并内置通用的差量计算规则的程序语言。"

### 2.1 XDef：元模型驱动（语言即坐标系）

**同态设计**：元模型与模型使用完全相同的语法结构——元模型本身就是一个嵌入了生成规则（通过 `def-type` 微语言）的"模板/示例"。这与 XSD 的异构设计形成对比（`docs/theory/deep-dive-into-xdef.md` §四 lines 251–288）。

- `!` 非空、`=default` 默认值、`enum:` 枚举、`v-path` 资源路径、标准类型 `string`/`int`/`boolean`/`xpl` 等。
- DSL 根节点声明 `x:schema="/nop/schema/orm/orm.xdef"` 即唯一入口，驱动 IDE 提示与运行时解析。

**自举**：`xdef.xdef` 自定义自身（其 `x:schema` 指向自己），闭环，无需更高元元模型（`docs/theory/xdsl-design.md` §二 lines 29–31；`docs/theory/deep-dive-into-xdef.md` §三 lines 213–249）。

> **成本从 O(N) 降到 O(1)**：开发一套能理解 XDef 的通用工具，即可处理所有当前和未来的 DSL（`docs/theory/deep-dive-into-xdef.md` §五 line 306）。
>
> **代码锚点**：`xpl.xdef`（XLANG-001）、`xlib.xdef`（XLANG-002）、`xdsl.xdef`（XLANG-003, EXT-001）均存在于 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/`。XDef 驱动的 codegen 链见 `nop-kernel/nop-xlang/precompile/gen-xlang-xdsl.xgen`（XLANG-008）。

### 2.2 XDSL：可叠加语言（三阶段 extends + 8 种 override）

**三阶段 extends**（`docs/theory/xdsl-design.md` §三 lines 59–61；`docs/theory/why-xlang-is-innovative.md` §2.1 lines 379–410）：

| 属性 | 角色 | 对应 GRC |
|---|---|---|
| `x:extends` | 继承已有 DSL 文件，两模型在 Tree 上分层合并 | Delta 叠加 |
| `x:gen-extends` | 用 Xpl 动态生成多个 Tree 节点再逐一合并 | **Generator**（`F(X)`） |
| `x:post-extends` | 同 Xpl 机制，但在已合并结果上后处理 | 后置差量 |

**完整合并顺序**（`docs/theory/why-xlang-is-innovative.md` §2.1 lines 408–410）：

```
F -> E -> Model -> D -> C -> B -> A
```

即 A 是最深的基；B 合并到 A；gen-extends（C 然后 D）介于基与 body 之间；body `Model` 合并；**post-extends（E 然后 F）最后应用**。

**`x:override` 合并模式**——源码确认实际有 **8 种**（`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefOverride.java:19-50`）：

| 模式 | 值 | 语义 |
|---|---|---|
| REMOVE | `remove` | 删除基类中的节点（逆元） |
| REPLACE | `replace` | 完全覆盖原有节点 |
| PREPEND | `prepend` | 合并属性，前插子节点 |
| APPEND | `append` | 合并属性，后插子节点 |
| MERGE | `merge` | 合并属性，按标签名合并子节点（**默认**） |
| MERGE_REPLACE | `merge-replace` | 合并属性，覆盖子节点 |
| BOUNDED_MERGE | `bounded-merge` | 只保留派生节点中定义过的子节点 |
| MERGE_SUPER | `merge-super` | 合并属性，嵌入 super |

> 默认 `merge`（`xdsl.xdef:70`：`x:override="enum:io.nop.xlang.xdef.XDefOverride=merge"`）。理论文档只内联命名 `replace`/`merge`/`remove`，完整算法指向 `docs/dev-guide/xlang/x-override.md`。

**`super:` 语义**：`x:extends="super"` 表示继承上一 Delta 层的模型；若 delta 文件**不设** `x:extends`，则**覆盖**（overwrite）而非合并（`docs/theory/delta-vs-extension.md` §2.4 lines 151–153）。

> **代码锚点**：`DeltaResourceStore.java:251-294`（EXT-003）解析 `super:`；合并执行 `DeltaMerger.java`；常量 `EXTENDS`/`GEN_EXTENDS`/`POST_EXTENDS`/`OVERRIDE` 在 `XDslKeys.java:72,74,75,85`（EXT-002）。

### 2.3 Delta 在结构层而非对象层（通用性的根源）

> "并不是 `EntityModel = EntityModel + Delta`，而是 `XNode = XNode + Delta`，`EntityModel = Parser(XNode)`。"
> —— `docs/theory/delta-vs-extension.md` §2.1 line 45

> "XLang 是在对象之下的结构层实现 Delta 合并运算……脱离语义，正是 Delta 合并运算通用性的表现。"
> —— `docs/theory/xlang-explained.md` §1 line 103

这是 nop Delta 区别于金蝶云苍穹/odoo 扩展（Extension）的本质：扩展是针对具体模型的 AdHoc 补丁（每个模型需自己的 `MergerFor*`），Delta 是在无语义的 XNode 结构层的通用数学运算——"定理证一次，所有模型复用"（`docs/theory/delta-vs-extension.md` §2.1 line 47）。

## 3. xpl / xlib：编译期元编程（Generator）

### 3.1 xpl 模板语言

Xpl 是 Turing-complete 模板语言（`c:for`/`c:if`/`c:choose`/`c:break`/`c:continue` + `${expr}` XScript 表达式 + `<c:script>` 语句），专为**编译期代码生成**重新设计（非复用 Velocity/Freemarker）（`docs/theory/why-xlang-is-innovative.md` §2.3 lines 459–492）。

**关键特性 `outputMode=node`**：直接产 XNode 树而非文本，保留 `SourceLocation`，使生成代码无需独立 SourceMap 即有源映射（lines 518–539）。XNode 记录 SourceLocation、ValueWithLocation，属性/内容为 Object 类型（克服 XML 纯文本限制）。

> **代码锚点**：`xpl:is` 用法见 `nop-task/nop-task-core/src/main/resources/_vfs/nop/task/xlib/task.xlib:18`（`<query xpl:is="${stepModel.graphqlOperationType}">`，XLANG-005）。

### 3.2 xlib 标签库：总是规约回基础 DSL 形式

xlib 标签在**编译期**展开为 DSL 节点。关键区别于 Spring 2.0 自定义命名空间：

> Spring 2.0 的 `NamespaceHandler` 运行任意 Java，"无法规约回 1.0 语法"；而 xpl 标签**总是规约回基础 DSL 形式**，因为它只生成 DSL 节点（`docs/theory/xlang-explained.md` §1 lines 176–184）。

这一不变量（"Delta 规约为 DSL"）是可逆计算可叠加性的前提。

> **代码锚点**：`XplLibTagCompiler.java:267-289`（EXT-006）`isAllowedUnknownAttr` 对带名字空间的属性放宽校验——除显式 `checkNs` 外，这些属性可作为扩展属性参与编译期元编程。真实案例见 `meta-gen.xlib` / `meta-prop.xlib` / `control.xlib`（EXT-007）。

### 3.3 Loader as Generator + 加载期/运行期分离

> "Loader 可以看作是一种即时编译器，它加载模型文件时进行的结构转换可看作编译过程的一部分。"
> —— `docs/theory/xlang-explained.md` §1 line 95

加载期承担所有复杂性，唯一目标是生成最终的、静态的、被"压平"的内存模型；运行期操作这个"烘焙"好的静态模型（`docs/theory/deep-dive-into-xdef.md` §二 lines 485–486）。这对应公理 I 的 S-N-V 阶段分离。

## 4. 统一术语表（供 A2–A7 引用）

### 4.1 GRC / 可逆计算维度

| 术语 | 英文 / 符号 | 定义要点 |
|---|---|---|
| 广义可逆计算 | Generalized Reversible Computation (GRC) | `Y = F(X) ⊕ Δ` 范式 |
| 差量 | Delta (Δ) | 可独立命名/存储/组合/审计的结构化变化对象 |
| 结合律 | Associativity | 差量链换括号结果不变（条件化，需 A1–A9 + 实现符合性） |
| 逆元素 / 正元素 | Inverse / Positive element | Δ 必同时含增与减；删除=逆元 |
| 合并 / 叠加 | Merge / Superposition (⊕) | 右覆盖（Last-Write-Wins） |
| 坐标系 / 语义坐标系 | Coordinate system | 元模型提供的稳定寻址规则 |
| 稳定 key | Stable key | 由 XDef 显式声明的节点身份（非数组下标） |
| 元模型 | Metamodel | XDef，定义 XDSL 的元 DSL |
| 潜在结构空间 | Latent structure space (P) | 合并期保留 tombstone/顺序/虚拟节点的中间空间 |
| tombstone / 删除标记 | Tombstone (⊥_c) | "该坐标定义为删除"，区别于 undef |
| 端函数 | Endofunction | 树态空间上的确定性函数，delta 表达式的指称 |
| 承载空间 | Carrier | 差量解释所在的代数结构（3 种） |
| 幺半群 | Monoid | `(P, ⊕, ∅)` 等结合律证明对象 |
| 规范形 | Normal form (NF, NF_LWW) | 差量栈的等价归约 |
| 创世差量 | Genesis delta | `M = ∅ ⊕ M`（空基线） |
| 分级可逆性 | Graded reversibility | 代数/表达/过程/边界四层治理原则 |
| S-N-V 阶段 | Structure-Normalize-Validate | 加载期合并→规范化→验证编译 |
| 可预合并性 | Pre-mergeability | 差量可在施加前合并（结合律的工程后果） |

### 4.2 XDSL 维度

| 术语 | 定义要点 |
|---|---|
| XDSL | 基于 XLang 共享统一扩展语法的 DSL 族 |
| `x:extends` | 继承基模型文件（Delta 叠加）；值 `super` 指上一 Delta 层 |
| `x:gen-extends` | 编译期 Generator，生成基线节点（`F(X)` 的 F） |
| `x:post-extends` | 编译期后处理器，作用于已合并结果 |
| `x:override` | 合并模式；8 种：remove/replace/prepend/append/merge/merge-replace/bounded-merge/merge-super（默认 merge） |
| `x:id` | 辅助稳定坐标，合并后自动删除 |
| `super:` | VFS 名字空间，指上一 Delta 层的同名资源 |
| Delta 层 | `_delta/{deltaId}/` 目录，经 `nop.core.vfs.delta-layer-ids` 叠加 |
| `feature:on` / `feature:off` | 条件加载开关（替代 `@ConditionalOnProperty`） |

### 4.3 XDef 维度

| 术语 | 定义要点 |
|---|---|
| XDef | 元模型定义语言（meta-DSL），定义 XDSL；替代 XSD |
| 同态设计 | 元模型与模型同构（元模型即嵌 def-type 的"示例"） |
| 自举 | `xdef.xdef` 自定义自身，闭环 |
| `def-type` 微语言 | `!`非空 / `=default` / `enum:` / `v-path` / 标准类型 |
| `xdef:unique-attr` | 列表元素的稳定身份属性 |
| `xdef:key-attr` | 列表 body 的匹配键 |
| `xdef:name` | 节点对应的 Java Bean 类名 |
| `xdef:ref` | 引用另一 XDef 复用 |
| `xdef:body-type` | body 形状（list 等） |

### 4.4 xpl 维度

| 术语 | 定义要点 |
|---|---|
| Xpl | Turing-complete 编译期模板语言（Generator） |
| `outputMode=node` | 直接产 XNode 树并保留 SourceLocation |
| `xpl:lib` | 导入 xlib 标签库 |
| xlib | 标签库；标签在编译期展开为 DSL 节点，**总是规约回基础 DSL 形式** |
| `xpl:is` | 标签身份动态化（见 task.xlib:18） |
| XScript | XLang 的脚本子语言（Java-like 类型） |
| XNode | 通用树节点/AST，Object 类型属性，带 SourceLocation |
| Loader as Generator | 资源加载器即即时编译器 |
| 加载期 / 运行期分离 | 所有 delta 复杂性在加载期，运行时面对静态烘焙模型 |

## 5. 联网对标与差异定位

四个方向的"该框架做什么 / nop 做什么 / 差异点"对照。

### 5.1 Model-Driven Software Development (MDSD) / MDA

| | MDSD / MDA | nop-entropy |
|---|---|---|
| **做什么** | 以模型（UML/图形符号）为一等制品，输入建模工具，生成目标语言代码。OMG MDA 基于 UML，分离业务逻辑与平台 | 以结构化 DSL（XML/JSON/YAML 的 XDSL）为模型，XDef 元模型驱动解析/校验/Java Bean 生成 |
| **差异点** | (a) nop 的"模型"是**可执行的结构化差量载体**，非一次性 codegen 输入——`Y = F(X) ⊕ Δ` 中 Δ 可独立于基线组合；(b) nop 内置 Delta 合并使模型**可演化**（客户定制可重新叠加到新基线），MDSD 生成后代码即"冻结"；(c) nop 不依赖图形/projectional 编辑器 |

> 来源：Martin Fowler, "Model Driven Software Development", bliki 2008 — https://martinfowler.com/bliki/ModelDrivenSoftwareDevelopment.html（访问 2026-07-24）；OMG MDA — https://www.omg.org/mda/（访问 2026-07-24）

### 5.2 Language Workbench / Projectional Editing（JetBrains MPS）

| | JetBrains MPS | nop-entropy |
|---|---|---|
| **做什么** | Projectional 编辑器——直接编辑 AST 而非文本，绕过解析器限制；支持近乎无限的语言扩展与组合（language composition） | XDef 元模型驱动 + 文本/XML DSL（解析式，非 projectional）；"语言即坐标系" |
| **差异点** | (a) MPS 聚焦**语言组合**（多语言在 AST 层混合），nop 聚焦**模型差量组合**（同构 Delta 在结构层叠加）；(b) MPS 用 projectional 编辑器解决"无法解析的语言扩展"，nop 用 XDef + `x:extends`/`x:override` 在结构层（XNode）实现通用差量，无需 projectional 编辑器；(c) MPS 扩展难以规约回基础形式，nop 的 xpl/xlib 元编程**总是规约回基础 DSL 形式**（保 Delta 可叠加性） |

> 来源：JetBrains MPS — https://www.jetbrains.com/mps/、https://www.jetbrains.com/mps/concepts/（访问 2026-07-24）；Wikipedia: JetBrains MPS — https://en.wikipedia.org/wiki/JetBrains_MPS（访问 2026-07-24）

### 5.3 Delta-Oriented Programming (DOP, Schaefer 等, SPL 领域)

| | DOP (SPL) | nop-entropy |
|---|---|---|
| **做什么** | 面向软件产品线：产品线 = 核心模块 + delta 模块；delta 操作对已有代码做增加/删除/修改。Schaefer & Bettini 2010 提出，含组合类型检查基础 | 结构层（XNode）通用 Delta 合并；差量与全量同构（同 schema）；满足结合律使差量可预合并、独立于基线组合 |
| **差异点** | (a) DOP 的 delta 用**与全量不同的形式**（修改动作）表达，"delta 的 delta"问题未解决（`docs/theory/delta-oriented-programming.md` §四 line 131）；nop 使 delta 与全量同构、同 schema；(b) DOP 绑定特定编程语言（如 DeltaJava），nop 的 Delta 在无语义结构层通用；(c) nop 显式形式化了结合律（3 carrier 证明），DOP 未提供等价的差量预合并代数保证 |

> 来源：Schaefer & Bettini, "Delta-Oriented Programming of Software Product Lines", SPLC 2010 — https://www.researchgate.net/publication/220789518、https://flore.unifi.it/bitstream/2158/1039579/2/Delta-Oriented%20Programming%20of%20Software%20Product%20Lines%20LNCS-splc-2010.pdf（访问 2026-07-24）；DeltaJava 案例 Nieke 2022 — https://pure.itu.dk/files/86606380/DeltaJava_Teamprojekt_Paper.pdf（访问 2026-07-24）

### 5.4 双向变换（bx / lenses / synchronization）

| | bx / lenses | nop-entropy |
|---|---|---|
| **做什么** | source 与 view 间一对映射：`get` 抽取 view，`putback` 反向传播回 source；满足 lens laws（GetPut/PutGet/PutPut）。用于 view-source 同步 | 可逆计算的"可逆"**不是** bx 的 view-source 双向同步；是构造期**差量组合代数** |
| **差异点** | (a) bx 关注**两表示间双向同步**（get/putback 闭环），nop 关注**单向构造链上的差量组合**（`Y = F(X) ⊕ Δ`）；(b) bx 的 lens laws 约束单对 get/putback 一致性，nop 的结合律约束**多条差量链**的可重排性（预合并许可）；(c) bx 通常处理数据同步（运行时），nop 的差量合并全部在加载期完成，运行时无差量历史 |

> 来源：Matsuda et al., "Applicative Bidirectional Programming with Lenses", ICFP 2015 — https://www2.sf.ecei.tohoku.ac.jp/~kztk/papers/kztk_icfp2015.pdf（访问 2026-07-24）；Haskell lens 库 — https://hackage.haskell.org/package/lens（访问 2026-07-24）。本仓库 `docs/theory/reversible-compuation-vs-bidirectional-transformation.md`（注意仓库内文件名拼写为 "compuation"）已有系统对比。

### 5.5 差异化定位总结

nop-entropy 的独特性可浓缩为一句话：**"差量与全量同构（同 schema） + 结构层通用合并（XNode） + 条件化结合律（可预合并） + 加载期烘焙（运行时静态）"**。这四点组合使其同时具备：

- 比 MDSD 更强的**可演化性**（差量可独立组合，非一次性生成）
- 比 MPS 更轻的**元模型驱动**（文本 DSL + XDef，无需 projectional 编辑器）
- 比 DOP 更彻底的**同构性**（delta 与全量同形，"delta 的 delta"有解）+ 形式化结合律
- 比 bx/lenses 更聚焦**构造期组合**（非运行时 view-source 同步）

## 6. 开放问题

- [ ] **"生成即逆元"口号溯源**：该口号在 `docs/theory/` 三篇核心文档（paper-v2 / proof-v2 / overview）中无字面出处，可能源自 canonical 早期博文（refs [21]–[24]）。本分析将其严谨化为"差量编码逆运算（delete/remove）作用于生成基线"。是否需要在 `docs/theory/` 补一条术语校准，由 A7 capstone 评估。
- [ ] **`x:override` 完整模式文档化**：源码确认 8 种模式（`XDefOverride.java`），但 `docs/theory/` 仅内联命名 3 种（replace/merge/remove），完整算法指向 `docs/dev-guide/xlang/x-override.md`。建议在 `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`（或 xdef-and-xdsl.md）补全 8 种模式清单——属独立文档维护任务，不在本 plan 修复。
- [ ] **合并执行类名准确性**：实际合并类为 `DeltaMerger`（`nop-xlang/.../delta/DeltaMerger.java`），非 `XNodeMergeProcessor`。source-anchors.md EXT-002 描述（`XDslExtender` 为执行链入口）准确，但未点名 `DeltaMerger`——建议 EXT-002 补一行指向 `DeltaMerger`。属 source-anchors 完整性补充，非事实性偏差。
- [ ] **XLANG-008 渲染列表**：`gen-xlang-xdsl.xgen` 实际渲染 `xdef.xdef`/`xlib.xdef`/`schema/*.xdef`/`xmeta.xdef` 等，不直接渲染 `xdsl.xdef` 本身。文档标签"XDef 驱动 codegen"仍准确，但若需精确可补充渲染清单。
- [ ] **结合律的形式覆盖范围**：proof-v2 证明的是抽象 carrier 的条件化结合律，**不是**当前 nop/XLang 实现的无条件证明（proof-v2 §13 line 2132）。实际实现若要引用结论，需证明自身语义映射到某一 carrier 并满足实现符合性——这是 A2（核心引擎剖析）可深入的方向。

## Conclusion

- 本分析建立了可逆计算（GRC）公理体系（9 条）到 nop-entropy 工程机制（XDef/XDSL/xpl 三件套）的完整映射，并用 11 个 source-anchor 源码交叉核对（全部 PASS）验证了文档论断与实际代码一致。
- 统一术语表（§4，覆盖 GRC/XDSL/XDef/xpl 四维度）可作为 A2–A7 后续章节的直接引用基线。
- 联网对标（4 方向：MDSD / MPS / DOP / bx-lenses）明确了 nop 的差异化定位：**差量与全量同构 + 结构层通用合并 + 条件化结合律 + 加载期烘焙**。
- 被否决/澄清的表述：(a) "生成即逆元"非核心文档字面引用，已严谨化；(b) "可逆"非 bx 双向同步，已澄清。
- 后续工作：A2（核心引擎剖析）深入 `XDslExtender`/`DeltaMerger`/`DslModelParser` 实现细节；A3（模型驱动/代码生成）展开 codegen 管线；是否将统一术语表迁移到 `docs-for-ai/` 由 A7 capstone 综合评估。

## References

### 平台内部（file 锚点）

- 理论文档：`docs/theory/generalized-reversible-computation-paper-v2.md`、`docs/theory/grc-delta-associativity-formal-proof-v2.md`、`docs/theory/reversible-computation-theory-overview.md`、`docs/theory/xlang-explained.md`、`docs/theory/xlang-explained3.md`、`docs/theory/xdsl-design.md`、`docs/theory/deep-dive-into-xdef.md`、`docs/theory/delta-oriented-programming.md`、`docs/theory/delta-vs-extension.md`、`docs/theory/why-xlang-is-innovative.md`、`docs/theory/reversible-compuation-vs-bidirectional-transformation.md`
- 实现锚点：`docs-for-ai/04-reference/source-anchors.md`（EXT-001~006, XLANG-001~008）
- 代码：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xdsl.xdef`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslExtender.java`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/delta/DeltaMerger.java`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefOverride.java`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/DslModelParser.java`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStore.java`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/xlib/XplLibTagCompiler.java`
- 计划：`ai-dev/plans/nop-deep-analysis/2026-07-24-1907-1-a1-theory-foundation.md`
- 路线图：`ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md`（Work Item A1）
- 交叉核对记录：`ai-dev/logs/2026/07-24.md`

### 外部（联网调研，访问日期 2026-07-24）

- Martin Fowler, "Model Driven Software Development" (bliki, 2008): https://martinfowler.com/bliki/ModelDrivenSoftwareDevelopment.html
- OMG Model Driven Architecture (MDA): https://www.omg.org/mda/
- JetBrains MPS: https://www.jetbrains.com/mps/ ; Concepts: https://www.jetbrains.com/mps/concepts/
- Wikipedia: JetBrains MPS: https://en.wikipedia.org/wiki/JetBrains_MPS
- Schaefer & Bettini, "Delta-Oriented Programming of Software Product Lines" (SPLC 2010): https://www.researchgate.net/publication/220789518 ; PDF: https://flore.unifi.it/bitstream/2158/1039579/2/Delta-Oriented%20Programming%20of%20Software%20Product%20Lines%20LNCS-splc-2010.pdf
- Nieke et al., DeltaJava case study (2022): https://pure.itu.dk/files/86606380/DeltaJava_Teamprojekt_Paper.pdf
- Matsuda et al., "Applicative Bidirectional Programming with Lenses" (ICFP 2015): https://www2.sf.ecei.tohoku.ac.jp/~kztk/papers/kztk_icfp2015.pdf
- Haskell lens library: https://hackage.haskell.org/package/lens
