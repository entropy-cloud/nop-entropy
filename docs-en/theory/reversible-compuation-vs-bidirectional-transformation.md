# From the Synchronization Paradigm to the Compositional Paradigm: A Reversible Computation Theory as a Generalization of Bidirectional/δ‑lenses

**Abstract**

Bidirectional/δ‑lenses (Bidirectional Transformations, BX) provide a formal skeleton for maintaining consistency between two models and benignly propagating updates from one side to the other; they are the classic answer to the “view update problem.” Reversible Computation (here specifically referring to the software construction paradigm based on coordinates and change algebra) establishes “Y = F(X) ⊕ Δ” as a system invariant: use generator F to project across models, non‑intrusively compose Delta Δ via superposition ⊕, and apply this recursively both horizontally (across a multi‑DSL atlas) and vertically (across multi‑level derivations). It simultaneously introduces axioms such as “intrinsic coordinates,” “S‑N‑V layering,” and “homomorphic propagation,” forming a self‑consistent engineering closed loop from modeling, merging, and generation to runtime. This paper compares the two along four dimensions—object granularity, mathematical structure, system scope, and process engineering—and argues that the essential innovation of Reversible Computation lies in: institutionalizing “generator + Delta” additive superposition and homomorphic propagation as an architectural contract, paired with intrinsic coordinates and a unified IR, thereby achieving a scalability general solution for tree‑structured spaces; it leaps from a “point‑wise update propagation theory” to a “unified methodology for system construction and evolution.”

***

## **Introduction: Two Seemingly Similar Ways of Change**

In software engineering, managing consistency across different representations and handling changes is an everlasting theme. Developers encountering bidirectional/δ‑lenses (BX) and Reversible Computation (RC) for the first time often loosely classify them together—as techniques that deal with “synchronization, Delta, generation.” Both are tightly related to “change,” and both aim to make system evolution more orderly and controllable. However, this surface similarity masks fundamental differences in worldview, basic axioms, and engineering scope.

BX is like a translator fluent in two languages, focused on accurate, lossless mutual translation—“faithfulness, expressiveness, elegance”—between two concrete texts (Model A and Model B). Reversible Computation is like a city planner who designs not the correspondence between two buildings, but an entire city blueprint (system architecture), stipulating how any new building (Delta Δ) should integrate with the existing foundation (base X) using standardized means (superposition ⊕), and ensuring this integration pattern is replicable, traceable, and sustainable across the entire city (the whole system).

This article aims to lift the veil for beginners, first introducing the core ideas of BX and Reversible Computation, then conducting a systematic comparative analysis, and finally explaining why Reversible Computation represents a more systemic and engineering‑complete essential innovation.

**Special Note:** The term “Reversible Computation” has multiple meanings in computer science. The “Reversible Computation” discussed here has absolutely no relation to “thermodynamic reversible computing” in physics and logic gate layers (e.g., Landauer’s principle, Toffoli gates). To avoid confusion, you can understand “Reversible Computation” in this context as an alias for its core idea, namely the “Generator + Delta Paradigm” or more precisely the “Homomorphic Generation Paradigm.” This paradigm focuses on algebraic reversibility, transformation reversibility, and process reversibility at the software construction level.

**Historical Context and Related Work**

What this paper calls “Reversible Computation” (Generator + Delta Paradigm / Homomorphic Generation Paradigm) was first proposed in 2007 in a series of technical blogs and platform products [[RC-2007](https://gitee.com/canonical-entropy/blogs/raw/master/canonical-blog-20090228.pdf)]. Its core tenets are constraining the positioning of changes via intrinsic coordinates, implementing additive composition via superposition `⊕`, and running homomorphic contracts through a generation pipeline spanning multiple DSLs and multiple levels of derivation.

Chronologically, it exhibits interesting parallels and intersections with related academic work:

* It belongs to the same era window as Foster et al.’s POPL 2007 work on bidirectional tree transformations, but focuses differently: Lenses emphasize formal laws of update propagation between two models and the alignment problem; RC has from the outset aimed to build a complete engineering closed loop spanning development–build–runtime across DSLs and layers by combining “intrinsic coordinates + ⊕ + homomorphism + unified IR + S‑N‑V.”
* It postdates Feature‑Oriented Programming (FOP) and AHEAD/FeatureHouse (2004–2006). FOP/AHEAD pioneered the paradigm of constructing software product lines through feature superimposition. RC can be seen as integrating and concretizing this idea in broader engineering scenarios, especially by introducing “intrinsic coordinates” and a “unified IR” to solve cross‑DSL feature composition.
* It predates the systematic proposal of Delta‑Oriented Programming (DOP) (around 2010 and beyond). RC established as early as 2007 the core idea of treating “Delta (Δ)” as a first‑class citizen for composition and evolution, making it a precursor or parallel in direction to DOP.

Therefore, the uniqueness of RC does not lie in “inventing” Delta or generation in isolation, but in having, at the 2007 time point, proactively integrated these ideas into a highly engineering‑complete, systematic methodology. For a concrete implementation of Reversible Computation, see the open‑source project [Nop Platform 2.0](https://github.com/entropy-cloud/nop-entropy).

## **I. BX/δ‑lenses: An Elegant Theory of Update Propagation**

The core goal of BX is to solve the “view update problem”: when you have two data representations A (source) and B (view) and they need to remain consistent, how do you “benevolently” propagate modifications to B back to A? Over years of development, the theory has evolved into several representative layers.

**1. Basic Lenses: The Art of Get and Put**

Early asymmetric lenses define two core operations:

* `get`: A → B, deterministically generating view B from source A.
* `put`: A × B → A, putting the modified view B back into source A to produce a new A.

To ensure these back‑and‑forth operations are “well‑behaved” and free of surprises, lenses must satisfy a set of axioms known as the “well‑behaved laws.” The most central laws include:

* **PutGet**: `get(put(A, v)) = v`  
  If you save a modified view `v` back to the source, then regenerate the view from this new source, you should get exactly the `v` you just saved.
* **GetPut**: `put(A, get(A)) = A`  
  If you generate a view from the source and then put it back unchanged, the source should not change at all.
* **PutPut**: `put(put(A, v1), v2) = put(A, v2)` (a common strengthening law)  
  Performing two consecutive puts is equivalent to performing only the last put. This ensures idempotence and stability.

**Classic challenge: information loss.** When `get` is “lossy”—for instance, projecting `employee(id, name, department, salary)` down to `employee(id, name, department)`—the `put` operation faces a difficulty: how to fill in the lost `salary` information? This often requires introducing a “complement/trace”—i.e., silently saving the discarded information during `get`—or preset strategies (e.g., default values, preserve original values).

**2. Symmetric Lenses and Hippocraticness**

In some scenarios, A and B are peers rather than master and slave (e.g., two address books that need bidirectional synchronization). Symmetric lenses are designed for this and follow an important law of **Hippocraticness**: if `A` and `B` are already consistent, then synchronization should not change either side. This embodies the principle of minimal disturbance—“do no harm unless necessary.”

**3. δ‑lenses: Elevating “edits” to First‑Class Citizens**

Traditional lenses propagate entire modified states (`v`), which lose the user’s “editing intent” (e.g., was it a rename or delete + insert?). δ‑lenses (aka edit lenses) solve this by directly propagating “edits” or “Delta (Δ).”

The core idea is:

* Modifications to A and B are represented by sequences of atomic edits (e.g., `insert`, `delete`, `update`, `move`), i.e., (ΔA, ΔB).
* Define a propagation function `T` that maps the edit sequence `ΔA` on side A to an equivalent edit sequence `ΔB` on side B.
* To preserve structure, the propagation function `T` must be a **homomorphism**, satisfying:
  * `T(id) = id` (empty edit remains empty after propagation)
  * `T(δ1 ∘ δ2) = T(δ1) ∘ T(δ2)` (compose edits then propagate ≡ propagate each and then compose)

**Core challenge: alignment.** To propagate edits correctly, δ‑lenses must determine “who corresponds to whom” across two models. For example, if a node’s position and name both change, is it `move + rename` or `delete + insert`? This often requires strategies to disambiguate:

* **Key/ID alignment**: leveraging unique identifiers in the model (if present).
* **Similarity matching**: heuristic algorithms based on content or structural similarity.
* **Trace retention**: recording correspondences between source and view elements during `get` for later propagation.
* **Conflict handling strategies**: preset rules for irreconcilable conflicts, such as timestamp priority or side preference.

**4. Engineering of BX and Reference Pointers**

BX is not purely theoretical. The academic and industrial communities have developed various engineering languages and tools around it. For example, `Boomerang` and `BiGUL` are programming languages dedicated to writing bidirectional transformations. In model‑driven engineering (MDE), `Triple Graph Grammars (TGG)` are widely used to establish and maintain consistency across different models (e.g., graph models, Ecore models). These practices demonstrate BX’s feasibility and power within specific domains, but their commonality is that the goal is primarily focused on “two‑model consistency,” rather than “system‑level generative superposition and assetized Delta governance.”

> **Reference pointers:** Readers interested in BX can explore the following key works and research directions:
>
> * **Combinators for Bidirectional Tree Transformations (POPL 2007)**: bidirectional tree transformation combinators, one of the classical starting points of BX.
> * **Symmetric Lenses (POPL 2011)**: a key work introducing symmetry and Hippocraticness.
> * **Boomerang: Resourceful Lenses for String Processing (ICFP 2007)**: early language‑level exploration oriented toward strings/documents.
> * **Edit/Delta Lenses**: research threads on edit propagation and alignment/trace.
> * **BiGUL**: a Haskell‑based bidirectional transformation language, oriented to the programmable putback paradigm.
> * **Triple Graph Grammars (TGG)**: a mature technique for model consistency/synchronization in MDE.

## **II. Reversible Computation: A System‑Level Doctrine of Generative Superposition**

Reversible Computation elevates the perspective from “two‑model synchronization” to “system construction and evolution.” It does not merely care how one update propagates; it proposes an architectural paradigm for building evolvable software systems.

**1. Core Invariant and Threefold “Reversibility”**

The cornerstone of Reversible Computation’s worldview is an algebraic invariant permeating the system:

**Y = F(X) ⊕ Δ**

* **X (Base)**: the system’s foundational model, core product, or upstream version.
* **F (Generator)**: a deterministic generator that projects (transforms) the source model `X` into the “ideal” part of the target model `F(X)`.
* **Δ (Delta)**: a Delta package that encapsulates all non‑intrusive modifications, extensions, or customizations to the generated result—i.e., the “residual.”
* **⊕ (Superposition)**: an algebraic superposition operator that deterministically and replayably merges Delta `Δ` on top of `F(X)` to produce the final form `Y`.

This invariant is required to hold recursively both horizontally (across models of multiple domain‑specific languages) and vertically (across multi‑level derivations). The word “reversible” carries three profound systemic capabilities:

1. **Algebraic reversibility**: introduce an engineering inverse for `⊕` so the construction equation is “solvable.” Not only can we compute `Δ = App - Base`, more importantly we can “peel off” a change: `Base = App - Δ`.
2. **Transformation reversibility**: require generator `F` and its inverse `F⁻¹` to support cross‑representation, information‑preserving “round‑trip” engineering, breaking barriers between representations via mechanisms such as “ghost metadata” (e.g., metadata carried with models for round‑trip and provenance but invisible to business logic, such as source file locations, generator traces, etc.).
3. **Process reversibility**: allow a “future” Delta `Δ_patch` to fix a “past” compiled system `M_base`, i.e., `M_final = M_base ⊕ Δ_patch`, enabling non‑intrusive runtime hotfixes and hot updates, breaking the linear construction timeline.

**2. Four Fundamental Postulates: Pillars from Theory to Engineering**

To make the above invariant land in engineering practice, Reversible Computation rests on four principles, tightly coupled to their engineering implementations.

**Principle I: The Coordinate System Principle**

This is the most fundamental difference from BX. BX traditionally does not axiomatize a coordinate system, with positioning often relying on runtime alignment algorithms. Reversible Computation postulates: every meaningful semantic unit in the system must have a stable, unique **intrinsic coordinate** (e.g., `id`, `name`, or a unique label under the parent).

This principle demands we proactively choose to operate in the “domain model space.” All changes (Δ) are precisely anchored to these intrinsic coordinates. Delta merging is simplified into a series of coordinate‑based, deterministic **insert, delete, and update** operations, fundamentally sidestepping the “alignment” problem.

> **Mental anchor: Delta operations based on intrinsic coordinates**
>
> Suppose the base model defines a UI form:
>
> ```xml
> <!-- Base X (base.page.xml) -->
> <form id="user-form">
>   <field id="username" label="用户名"/>
>   <field id="age" label="年龄"/>
>   <button id="submit-btn" label="提交"/>
> </form>
> ```
>
> Now we need to implement customization via Delta Δ:
>
> 1. **Delete** the `age` field.
> 2. **Update** the label of `submit-btn`.
> 3. **Insert** an `email` field.
>
> The corresponding Δ file anchors all operations precisely through intrinsic coordinate `id`:
>
> ```xml
> <!-- Delta Δ file (delta.page.xml) -->
> <form id="user-form">
>   <!-- 1. Locate by id and delete the node -->
>   <field id="age" x:override="remove"/>
>
>   <!-- 2. Locate by id and update node attributes -->
>   <button id="submit-btn" label="确认提交"/>
>
>   <!-- 3. Insert a new node -->
>   <field id="email" label="邮箱"/>
> </form>
> ```
>
> The `⊕` operator (namely `x:extends`) will perform the following operations based on coordinate `id`:
>
> 1. Find the node with `id="age"` and delete it according to instruction `x:override="remove"`.
> 2. Find the node with `id="submit-btn"` and overwrite its `label` attribute with the new value `确认提交`.
> 3. Insert a new node with `id="email"` under `id="user-form"`.
>
> The entire process is entirely based on stable intrinsic coordinates, with clear operation semantics (restricted to insert, delete, update), and is deterministic.

**Principle II: The Superposition Operation Principle**

This principle formalizes “applying changes” as a superposition operation `⊕` with good algebraic properties. Its quality can be assessed by the axioms of a **monoid**, each corresponding to a key engineering capability:

| Axiom      | Engineering value                                                                                | Consequences of missing (with common technologies)                                                                                                 |
| :--------- | :----------------------------------------------------------------------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Closure**  | **Predictable automated merging**: guarantees the result of `A ⊕ B` is always a valid model of the same kind, enabling unattended automation.                                | **Git merge conflicts**: `git merge` may produce intermediate artifacts with conflict markers upon conflicts, which downstream tools cannot directly consume, requiring manual intervention. |
| **Associativity**  | **Composable, distributable Deltas**: allows precombining multiple deltas `Δ_pack = Δ₁ ⊕ Δ₂` into independently distributable, versioned “feature packs.”                     | **Limitations of JSON Patch**: operation instructions are highly dependent on base state and application order, lacking base‑independent normalization and rebase semantics, making multiple patches hard to safely, deterministically premerge off‑base and limiting their composability as independent assets. |
| **Unit element**  | **Unifying model and Delta**: existence of a “no‑change” empty Delta `0` means the full model `Base` can be viewed as `0 ⊕ Base`, unifying tools and algorithms for handling models and Deltas. | **Carrier split**: JSON documents and JSON Patch are two different data structures, requiring two sets of processing logic.                                                                  |
| **Engineering inverse**  | **Reversible, peel‑off**: idempotent deletion via “tombstone” mechanisms, supporting “peel‑off” operations like `Base = App - Δ`, which is key for version rollback and feature migration.              | -                                                                                                              |

Note that the `⊕` operation is generally **non‑commutative**, but by defining a deterministic superposition order we can ensure uniqueness of results. The “engineering inverse” here is not a strict mathematical group inverse; rather, it refers to an engineering capability to “undo” changes. Its implementation depends on retaining sufficient provenance, tombstone marks, or complement information to avoid irreversible information loss.

**Principle III: The Superposition‑Normalize‑Verify (S‑N‑V) Layering Criterion**

To decouple generic “structural merge” from complex “domain semantics,” Reversible Computation decomposes a complete merge process into a three‑stage pipeline:

* **S (Structure)**: perform purely domain‑agnostic **structural merging**.
* **N (Normalize)**: carry out domain‑specific **normalization**.
* **V (Verify)**: perform final global **legality checks**.

The core insight of S‑N‑V is the introduction of “virtual time” (or “deferred validation”). This tolerance reduces the design complexity of the generic merge algorithm in stage S to O(1) (one algorithm for all models), while its runtime cost becomes proportional to the change size `|Δ|` or the number of affected coordinates `k` (`O(k)`), avoiding full scans over the entire model `|X|`.

**Principle IV: The Homomorphic Propagation Principle**

Reversible Computation places a system‑level design criterion and target contract on generator `F`: **`F(X ⊕ ΔX) ≡ F(X) ⊕ ΔY`**. This means that a change `ΔX` on source model `X`, through a “disciplined” generator `F`, can automatically project into an equivalent change `ΔY` on the target model.

This principle is the natural requirement of a Delta‑structured space and yields two deep architectural corollaries:

1. **DSL Atlas**: complex systems should be composed of a series of small, focused DSLs, glued together by generators that satisfy homomorphism.
2. **Loader homomorphism**: the platform’s own construction process also follows this principle, `Loader(DeltaPath + BasePath) ≡ Loader(DeltaPath) ⊕ Loader(BasePath)`. Here `+` denotes union of file path sets, and this formula is exactly how the Nop platform implements Delta customization.

**Beyond the Postulates: The Fractal Nature of the Recursive Invariant**

At this point we have elaborated the four engineering postulates underpinning Reversible Computation. To truly grasp its essence as a “system construction doctrine,” we must perceive the deepest trait of the invariant `Y = F(X) ⊕ Δ`: **recursiveness**. It is not merely a formula but a principle of **self‑similarity** recursively applied across the software world.

This self‑similarity manifests at least along three nested dimensions:

**1. Vertical recursion: self‑similarity of multi‑level derivations**

Any entity in the system can itself be seen as the result of its earlier version (base) plus a Delta superposition. This forms an infinitely extending evolution chain:
* `Product V3 = Product V2 ⊕ Δ_v3`
* And `Product V2` itself is `Product V1 ⊕ Δ_v2`
* ...
Every element in the formula (`X`, `F`, `Δ`) can itself be decomposed again, e.g., a complex Delta package `Δ` can be composed of a base Delta `Δ_base` and a patch targeting it `Δ_patch`: `Δ = Δ_base ⊕ Δ_patch`. This makes change itself a manageable, evolvable asset.

**2. Horizontal recursion: isomorphism across the DSL atlas**

Within an “atlas” composed of multiple DSLs—UI models, data models, business logic models—they all follow **exactly the same** superpositional evolution rules. A cross‑domain business requirement can be decomposed into a set of “isomorphic” Deltas acting on different DSL models `{Δ_ui, Δ_data, Δ_logic, ...}`. Because the superposition operator `⊕` is generic, these Deltas can be applied uniformly and deterministically within their respective domains. This is the macro‑level embodiment of the “homomorphic propagation” principle in system architecture.

**3. Meta‑recursion: bootstrapping the construction system itself**

This is the most disruptive aspect of Reversible Computation: **the toolchains, rules, and platform used to construct software themselves evolve entirely under the same invariant.**
* **Evolution of DSL definitions (metamodels)**: `MyDSL_v2 = MyDSL_v1 ⊕ Δ_meta`
* **Evolution of build tools**: `Compiler_Pro = Compiler_Base ⊕ Δ_feature`
* **Even evolution of merge rules themselves**: `MergeRule_New = MergeRule_Old ⊕ Δ_rule`

The entire software world—from the final product to intermediate models, and further to the construction system itself—becomes a vast, self‑similar Delta‑structured space connected by the `⊕` operator. In this “fractal space,” entities at any level and granularity share the same philosophy of construction and evolution. This achieves true “one method to rule them all,” elevating software engineering from the “handicraft era” of repeatedly inventing extension mechanisms for different domains and layers into a unified, self‑consistent, industrialized new epoch. This is the very foundation for Reversible Computation’s claim as an “essential innovation.”

**3. Unified Engineering Bedrock**

* **Unified IR (XNode)**: an enhanced XML tree that leverages XML namespaces to implement “localized metadata.” With implementations of indices from coordinates to nodes and model‑localized loading, the runtime cost of generic merging algorithms becomes **proportional to the change size rather than the overall model size**.
* **Metamodel (XDef)**: a DSL for defining DSLs. Similar to EMF/Ecore in the MDE ecosystem, XDef drives toolchain generation via metamodels, and it itself is fully integrated into the Delta superposition evolution system.
* **End‑to‑end traceability (_dump)**: thanks to metadata carried by XNode, the source and merge history of any attribute value can be precisely traced.

## **III. Comparative Analysis: From Point‑wise Propagation to System Construction**

| Dimension       | BX/δ‑lenses                                        | Reversible Computation                                            |
| :-------------- | :------------------------------------------------- | :---------------------------------------------------------------- |
| **Goal & perspective**  | **Local consistency**: how to benignly propagate updates from one side to the other (A↔B).                   | **Global construction doctrine**: the system should uniformly abide by the construction/evolution invariant `Y = F(X) ⊕ Δ`.       |
| **Basic postulates**    | **Propagation laws** (`Put/Get`) and homomorphic **composition of edits**. Positioning depends on **alignment algorithms/trace**. | **Intrinsic coordinates** and **superposition algebra (⊕)** as first principles; generators must meet the **homomorphic propagation** contract.   |
| **Delta (Δ) identity** | **Ephemeral input**: a procedural concept serving one synchronization computation.                          | **First‑class asset**: can be independently packaged, versioned, distributed, and migrated across the entire lifecycle.             |
| **System scope**    | **Point‑to‑point** (A↔B). Chaining requires additional proofs.                             | **System‑level closed loop**: via a “DSL atlas” with unified IR, `⊕`, and homomorphic contracts, it naturally supports full‑chain evolution and traceability. |
| **Applicability**      | **Synchronizing existing models**: round‑trip engineering, data sync, view update.                         | **Generative system construction**: software product lines, multi‑tenant customization, low‑code platforms.                  |

## **IV. Integration, Limitations, and Outlook**

BX and Reversible Computation are not mutually exclusive but complementary ideas at different levels of abstraction.

**1. Integration: Combine strengths for mutual benefit**

* **Use δ‑lenses to formally define and verify homomorphic generators**: The core of RC’s homomorphic contract is the propagation function `T_F` (`F(ΔX) = T_F(ΔX)`), whose design and verification can fully borrow from δ‑lenses theory, providing a solid theoretical foundation for RC generators.

**2. Limitations and Open Problems**

* **Delta rot**: Accumulated Δ files over long‑term evolution increase the system’s “historical burden.” Mitigation involves governance strategies such as periodic **Delta flattening** or **refactoring**.

* **Coordinate system refactoring**: When large‑scale refactors change coordinates (IDs) themselves, Deltas based on old coordinates become invalid. This is a critical open problem. Future solutions may involve defining a **coordinate transformation** or **Delta migration** mechanism.

## **V. Why Reversible Computation Is an Essential Innovation**

1. **A new invariant and worldview**: `Y = F(X) ⊕ Δ` is established as a **recursively applied architectural invariant**, fundamentally changing our paradigm for software reuse, extension, and evolution.

2. **Coordinates and algebra as first principles**: **Intrinsic coordinates** uproot BX’s alignment challenge; the **associativity** of `⊕` is the mathematical guarantee that Delta can become a “packable, composable, distributable asset.” Unlike FOP/DOP, which focus more on modular composition of features/Deltas, RC makes stable “addresses” (coordinates) the foundation for achieving all this.

3. **From point‑wise theory to a systemic engineering closed loop**: via **unified IR, metamodels, S‑N‑V layering, and end‑to‑end traceability**, RC forms a **complete, self‑consistent engineering closed loop**. This systemic completeness is the core distinction from the more theoretical formalization path of BX.

4. **A scalability general solution and a rewritten cost structure**: by assetizing Delta, the cost of change becomes primarily proportional to the change size (`|Δ|`), and weakly related to the base system size (`|X|`).

## **VI. Conclusion**

While both bidirectional/δ‑lenses and Reversible Computation dance with “change,” they answer questions at two different levels.

* **Bidirectional/δ‑lenses** answer “how to benignly propagate updates from one side to the other,” providing a deep and elegant formal skeleton for the “two‑model consistency” problem.

* **Reversible Computation** elevates the problem to “how to unify system construction and evolution into additive superposition of generation and Delta, and ensure composability, replayability, and verifiability across multiple domains, multiple layers, and the full lifecycle.” With intrinsic coordinates, superposition algebra, S‑N‑V layering, and homomorphic propagation as rigorous postulates and contracts—and paired with a unified IR and toolchain—it provides a scalability general solution for tree‑structured spaces. Under this unified architecture, any DSL implemented will automatically (Automagically) gain endogenous extensibility, without needing any bespoke extension mechanism (such as plugin frameworks, inheritance syntax, or merge algorithms). This fundamentally liberates software extension from the “handicraft era” of reinventing wheels for every DSL.

The low‑code platform NopPlatform, designed based on Reversible Computation theory, is open‑source:

- gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- gitcode: [https://gitcode.com/canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
- Development tutorial: [https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- Principles of Reversible Computation and introduction/Q&A of the Nop platform: [https://www.bilibili.com/video/BV14u411T715/](https://www.bilibili.com/video/BV14u411T715/)
- International site: [https://nop-platform.github.io/](https://nop-platform.github.io/)
- Community site by Crazydan Studio sharing Nop development practices: [https://nop.crazydan.io/](https://nop.crazydan.io/)
<!-- SOURCE_MD5:5b09c2c3938d01284aad167af56bc7f1-->
