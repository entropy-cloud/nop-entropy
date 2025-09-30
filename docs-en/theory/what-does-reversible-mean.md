# What exactly does “reversible” mean in the theory of Reversible Computation?

The theory of Reversible Computation is a next-generation software construction theory I proposed around 2007, inspired by physics and mathematics. The term “reversible” is closely related to the concept of entropy in physics: the direction of entropy increase determines the arrow of time in the physical world. Reversible Computation studies the construction laws of evolution-oriented, coarse-grained software structures, so “reversibility” is the linchpin of the theory. Some readers unfamiliar with thermodynamics and statistical physics may be unaware of the concept of entropy; seeing the term “reversible” may naturally cause confusion. Is reversibility important? How can software be reversible? Does it mean reverse execution? What’s the point? In this article, I briefly explain what “reversible” actually means in the theory of Reversible Computation.

## I. The core equation of Reversible Computation

Reversible Computation proposes a core equation for software construction:

```
   App = Delta x-extends Generator<DSL>
```

It is important to emphasize that Reversible Computation is a scientifically rigorous theory with precise definitions. It is not a methodology open to subjective interpretation. The equation here is not a loose analogy; it actually encompasses the following precisely defined content:

1. Delta, Generator, and DSL all have Tree structures, and we explicitly define a coordinate system for precise positioning and dependency tracking within Tree structures.
2. `x-extends` is a general Delta merge operator over Tree structures. It has a precise mathematical definition and can be mathematically proven to satisfy associativity.
3. Generator is a functor mapping over the DSL structural space. It maps one class of structures to another (note: it is not tied to a single object but applies to a large class of structures within a domain).

The Nop platform is a reference implementation of Reversible Computation; it simply translates abstract mathematical symbols and definitions into concrete implementation code.

Traditional software engineering also has the notion of incremental development, but there is no systematic, precise technical formalism to express such increments—especially lacking clearly defined inverse operations and inverse elements. In common parlance, “increment” just means “add,” which is essentially different from the concept of Delta in Reversible Computation.

Contrary to what many might think, “reversible” in Reversible Computation does not mean reverse execution. In fact, we generally apply Reversible Computation at compile time; the system hasn’t even reached runtime, so reverse execution is not an issue.

Because the core equation of Reversible Computation has an explicit mathematical definition, the so-called reversibility is entirely embodied in the mathematical operations implied by this equation.

## II. Inverse elements: Achieving removal by addition

The first obvious manifestation of reversibility in Reversible Computation is the basic structural unit that includes inverse elements: Delta. Traditionally, the basic units of software construction have no notion of inverse elements—everything we operate on is a positive element, whereas an inverse element is something that, when added, cancels what was previously there.

Delta in Reversible Computation is a complex delta-structured entity; decomposed to the finest granularity, it is effectively a mixture of various positive atomic elements and negative atomic elements.

> In our physical world, all matter can be decomposed into a small number of fundamental particles, and every particle has a corresponding antiparticle. Particles and antiparticles can emerge from the vacuum and can annihilate into a flash of light when they meet.

Assuming the existence of an identity element, any element can always be viewed as some Delta acting on the identity: $0 + A = A$. Thus, a full object is a special case of a Delta. We can reinterpret and rebuild all software structures on top of the Delta concept.

Mathematically, elements and the operations acting on them are defined as a whole; that is, Delta and the `x-extends` operator must be considered together. Only by making the action of `x-extends` explicit can we clearly explain what inverse elements inside a Delta actually mean.

## III. Subtraction: Solving equations in reverse

When we understand inverse elements from an operational perspective, subtraction naturally follows from the concept of inverses.

```
  A = B + (-C) = B - C
```

With subtraction introduced, we can solve equations in reverse by transposing terms:

```
 A = B + C ==>  B = A - C
```

In the Nop platform, the concrete implementation is:

```
App = Delta x-extends Base ==>  Delta = App x-diff Base
```

[DeltaMerger.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/delta/DeltaMerger.java) and [DeltaDiffer.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/delta/DeltaDiffer.java) implement the forward `x-extends` and the reverse `x-diff` operations, respectively. In other words, after merging via the Delta merge algorithm to produce the whole, the Nop platform can reverse-compute via a diff algorithm to split out the Delta components. This capability lets us construct software in a delta-ized manner—just like solving equations.

A practical scenario: a front-end page designer that supports both automated model generation and visual design. In the Nop platform, front-end pages are inferred and generated automatically from data models. After auto-generation, we can tweak the result with a visual editor and then, when saving back to the page DSL, apply the diff algorithm to compute only the delta part corresponding to the visual modifications. That is, if the visual editor only performs local adjustments, what is actually saved to the DSL file is a small amount of Delta information, not the entire page. This approach enables synergy between automated model generation and visual design.

```
PageInEditor = DeltaPage x-extends Generator<ViewModel>
DeltaPage = PageInEditor x-diff Generator<ViewModel>
```

What is edited in the visual editor is always the full page PageInEditor, but the DSL saved to the file system has the following structure, which highlights only the manually modified local Delta:

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthUser.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />

title: 修改后的标题
```

## IV. Reversible transformations

Mainstream software construction theories are mostly based on composition/assembly, and few systematically examine construction from the perspective of transformations. However, as I pointed out in my Zhihu article [Decoupling goes far beyond dependency injection](https://zhuanlan.zhihu.com/p/550923860), to achieve decoupling, the most fundamental and powerful technique should be representation transformation; interfaces are merely the simplest form of representation transformation.

> Recall Fourier transform theory in mathematical physics: multiple signals of different frequencies superposed together are completely mixed in the time domain—at each time point, multiple signals are present. But via Fourier transform, we obtain completely separated signals in the frequency domain!

In the theory of Reversible Computation, the unit `Generator<DSL>` is explicitly included as a computational unit corresponding to generative programming. DSL efficiently expresses domain information, and the Generator applies a formal transformation to the information expressed by the DSL to obtain other representations of the same information (potentially introducing new information or pruning existing information). A special case here is when the Generator admits an inverse transformation.

Interestingly, although reversible transformations are a special case of general formal transformations, in practice they can systematically solve a large number of real problems.

> In fact, at a theoretical level, we can solve all computational problems within the framework of reversible transformations—after all, quantum computation is a reversible transformation.

Assume a transformation G has an inverse F; then we can realize bidirectional conversion between A and B:

```
B = G(A), A = F(B)
```

The visual design problem in low-code platforms can be automatically solved under the framework of reversible transformations.

```
Visual representation = Designer(Textual representation),   Textual representation = Serializer(Visual representation)
```

A visual editor reads the textual representation and produces a visual representation, enabling users to edit the model visually. When users save, the model information in the visual editor is serialized back to text and exported to a DSL model file.

Reversibility composes: if each subpart of a structure supports a reversible transformation, we can automatically derive a reversible transformation for the entire structure. For example, if we define both a visual representation and a textual representation for each field-level structure, we can automatically derive a bidirectional conversion between the whole DSL text and the visual design form.

$$
\left(\begin{array}{c} a\\ a^{-1} \end{array} \right)*\left( \begin{array}{c} b\\ b^{-1} \end{array} \right) =\left( \begin{array}{c} ab\\ b^{-1}a^{-1} \end{array} \right) \\
$$

From the inverses of a and b, we can automatically derive that the inverse of ab is $b^{-1}a^{-1}$.

Note that in practical applications, we often encounter similarity transformations, not exact equivalences. For instance, in DSL visual editing, for better presentation the visual representation may contain layout information used only for visualization, which is entirely redundant for the DSL textual representation. In this case, to allow free switching between textual editing and visual editing, we can apply the delta-ization idea of Reversible Computation: augment the textual representation with extended Delta structures dedicated to storing these extra pieces of information, thereby turning an approximate similarity into a strict equality.

$$
B \approx G (A) , A \approx F(B) \Longrightarrow 
B + dB = G(A + dA), A + dA = F(B + dB)
$$

In the Nop platform, the delta-ization concept is fully embraced: all structures have a built-in extension-attributes mechanism—i.e., we always adopt a paired design of the form `(data, ext_data)`. Technically, ext_data often serves as metadata. For example, in message object design it appears as `data + headers`; in Java class design, as `field + annotations`; and in DSL model design, as `props + ext_props`.

It is also worth emphasizing that mathematical reversible transformations are far more sophisticated and powerful than the simple idea of invertible functions. Most reversible transformations in the Nop platform correspond to functor transformations in category theory. That is, they are not applied to a single object, but are defined for every object in a domain (i.e., a category). For example, the Nop platform provides reversible conversion between DSL objects and Excel: without programming, you can parse an Excel file into a DSL object, and you can also export a DSL object to an Excel file.

```
DslModel = Importor<Excel>, Excel = ReportExporter<DslModel>
```

In traditional programming practice, we always write parsing code for a specific Excel file to obtain a particular data object; conversely, we implement an Excel export function for a specific business object. If we now implement bidirectional conversion functions for a given requirement, later changes or new products allow us only to reference the old import/export code—we cannot directly reuse them and must rewrite them for the new object structures.

However, in the Nop platform, the import model and the report model form a very general pair of reversible transformations. They can automatically export any data object to Excel and can parse any Excel (as long as it meets fairly broad Tree-structure conditions) into a corresponding data object. In category-theoretic terms, the Nop platform establishes a pair of adjoint functors between the DSL category and the Excel category. If such functorial reversible transformations are further composed, they yield an unprecedentedly powerful technical means.

In real-world business applications, if we consistently pursue minimal information expression and emphasize framework neutrality in business development, it naturally leads to reversible formal transformations. For detailed analysis, see my article [The path to freedom in business development: How to break framework constraints and achieve true framework neutrality](https://zhuanlan.zhihu.com/p/682910525).

## V. Going backward along the timeline

Traditional software engineering constructs proceed forward along the timeline. Conceptually, we develop base class A first, then derived class B; A always precedes B. When extending, we usually add different derived classes to introduce new information structures. In rare cases we modify base class A, which requires recompilation, effectively reconstructing all derived classes. Viewed along the timeline, information is layered and accretive—old information below, new information above. To inject new information into the system’s foundation, we must modify source code, effectively breaking apart the structure and rebuilding it.

The construction equation of Reversible Computation is a purely mathematical formal expression; its parts have no explicit assumptions about time dependency.

```
App[t] = Delta[t-1] x-extends Generator[t]<DSL[t]>
```

The system needed at time t can be composed by applying the Delta determined at time t-1 to the structure `Generator[t]<DSL[t]>` that is only determined at time t. Because at time t (e.g., deployment time) we already know all business-relevant information, we can avoid guessing at numerous potential scenarios; we only need to describe the information currently required and use a Generator customized by time-t information to dynamically expand it.

In concrete code, the Nop platform provides a unique Delta customization capability. Without modifying existing source code, it can delta-adjust all DSL structures used across the system. Comparing with Java makes the uniqueness of Delta customization clearer. Suppose we have built a system `A extends B extends C` and packaged it into a JAR. Without modifying this JAR, there is no way in Java to alter the existing structure—we can only add new information externally:

```
E extends A extends B extends C
```

But with the Delta customization mechanism, by supplying a Delta description we can arbitrarily replace existing code structures. For example, replace B with `E + F`, obtaining `A extends E extends F extends C`. Mathematically, this is:

```
X = A + B + C
Y = X + (-B + E + F) = A + E + F +C
```

The power of Delta customization is reflected in the following derivation of the Reversible Computation equation:

```
App = B + G<S>
App + dApp = B + G<S> + dApp = B + dB + (G + dG)<S + dS>
```

In the construction equation of Reversible Computation, perturbation Deltas can arise in any part; they can flow across existing structures and be aggregated into an external correction dApp. A special application is to inject information only known at time t, in delta-ized form, directly into preexisting program structures solidified at time t-1—without modifying the existing source code. It is as if we always have a “second chance” to travel back to time t-1 and adapt the program structure arbitrarily to ultimately solve the problems we encounter at time t.

Note that although Docker technology also adopts a computation pattern of the form `App = Delta x-extends Generator<DSL>`—a typical application instance of Reversible Computation—it does not support the kind of dynamic Delta customization available on the Nop platform. Docker images are immutable, and the relationships between layers are bound: a later layer explicitly specifies the hash digest of the prior layer it depends on, akin to a blockchain, forming an immutable timeline.
<!-- SOURCE_MD5:253a61cc505fa09b818a36db2af9f27d-->
