# What does 'Reversible' Mean in Reversible Computing?

Reversible computing is a software construction theory that I was inspired by physics and mathematics, around 2007. The term "reversible" is closely related to the concept of entropy in physics. Entropy increase determines the direction of time arrows in the physical world, and reversible computing research focuses on the rules for constructing coarser-grained software structures, particularly those related to the laws governing entropy.

For those unfamiliar with thermodynamics or statistical mechanics, this concept may be confusing. Is "reversible" important? How can software be made reversible? Does it mean reversing execution? What does it signify in this context? In this document, I will explain these concepts step by step.

---

## 1. The Core Formula of Reversible Computing

Reversible computing introduced a core formula for software construction:

```
App = Delta x-extends Generator<DSL>
```

This formula needs to be clearly understood. Reversible computing is not a vague methodology; it's a well-defined scientific theory. Unlike some "reversed engineering" approaches, this formula does not rely on analogies or metaphors. Instead, it provides precise definitions for the following:

1. **Delta**, Generator, and DSL all have Tree structures.
2. A coordinate system is defined for precise identification and tracking within Tree structures.
3. The `x-extends` operator is a generic difference-algebraic operation with a precise mathematical definition.

The Nop platform serves as a reference implementation of this theory. Essentially, it translates abstract symbols and definitions into concrete code.

---

## 2. Inverse Elements: Achieving Reduction Through Addition

In the context of reversible computing, the first noticeable aspect is **Delta**. This term represents a difference structure that can be applied to any element in a Tree structure.

1. **Delta** in Trees:
   - Every node in a Tree has children and attributes.
   - Delta captures differences between parent nodes and their children.
   - The Nop platform uses Delta to track changes and dependencies throughout the software structure.

2. **x-extends**:
   - This is a generic difference-algebraic operator.
   - In mathematical terms, it combines additive and subtractive operations.
   - For example:
     ```
     A = B + (-C) = B - C
     ```
     This operation allows for precise inversion of changes.

3. **Generator**:
   - A Functor that operates on DSL structures.
   - It transforms one type of structure into another while preserving the essential properties.
   - The Nop platform uses Generators to build complex systems from modular components.

---

## 3. Subtraction: Solving Equations in Reverse

When dealing with equations, subtraction plays a crucial role:

```
A = B + C
==> B = A - C
```

This process allows for the inversion of additive operations. By applying `x-extends` and Delta, we can reverse engineer the relationships between components.

For example:
```
App = Delta x-extends Generator<DSL>
==> Delta = App - (x-extends Generator<DSL>)
```

This demonstrates how reversible operations enable precise control over software evolution.

---

## 4. The Nop Platform: A Practical Implementation

The Nop platform provides a concrete realization of these principles:

```
App = Delta x-extends Generator<DSL>
==> Delta = App - (x-extends Generator<DSL>)
```

This implementation serves as a foundation for exploring reversible computing concepts. It demonstrates that the abstract theories can be translated into functional, working code.

---


[DeltaMerger.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/delta/DeltaMerger.java) and [DeltaDiffer.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/delta/DeltaDiffer.java) implement the `x-extends` (forward operation) and `x-diff` (reverse operation), respectively. This means that in the Nop platform, after merging using DeltaMerger's delta merge algorithm, we can apply the diff algorithm to decompose the delta into its constituent parts. This capability enables us to perform delta-based model generation and fine-tuning of generated views.


## Specific Use Case

A concrete use case is the support for both automated model generation and visual design of frontend pages in the Nop platform. The frontend page is automatically generated based on the data model, and after generation, we can manually refine it using the visual designer. When saving back to the DSL file, only the delta (localized changes) is stored instead of the entire page, allowing for efficient version control and collaboration.



```
PageInEditor = DeltaPage x-extends Generator<ViewModel>
DeltaPage = PageInEditor x-diff Generator<ViewModel>
```

When editing in the visual editor, the entire PageInEditor is modified. However, when saving to the file system, only the localized changes (delta) are stored, highlighting manual modifications.

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthUser.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
```

After generating the main page using the visual designer, the delta (localized changes) is stored in the DSL file. This approach ensures that only the differences are tracked, facilitating efficient collaboration and version control.

---



The reverse transformation plays a crucial role in modern software development. While traditional approaches focus on assembly-like operations, the Nop platform leverages the power of DeltaMerger for forward transformations and DeltaDiffer for reverse operations. This allows developers to decompose complex systems into manageable components and reassemble them as needed.

---



In software development, inversion of control (IoC) is a common technique used to decouple dependencies. While dependency injection is the most widely recognized form of IoC, other mechanisms such as delegate injection or property injection also exist. The Nop platform provides robust support for IoC through its sophisticated configuration management and plugin architecture.

---



- **DeltaMerger**: responsible for merging deltas into a unified model.
- **DeltaDiffer**: capable of decomposing the merged model back into individual deltas.
- **x-extends**: forward operation used to merge deltas.
- **x-diff**: reverse operation used to extract deltas.

--- 



The ability to perform delta operations is a powerful feature in software development. It allows for atomic rollbacks, incremental builds, and precise debugging. By leveraging DeltaMerger and DeltaDiffer, developers can achieve a higher level of control over their applications, enabling efficient maintenance and evolution of complex systems.


The inverse of a matrix \( A \) is denoted as \( A^{-1} \). When multiplying two matrices, the inverse of their product is the product of their inverses in reverse order:

\[
(A^{-1}) (B^{-1}) = (AB)^{-1}
\]


## Example Calculation

Consider the following matrices:
\[
A = \begin{pmatrix} a \\ a^{-1} \end{pmatrix}, \quad B = \begin{pmatrix} b \\ b^{-1} \end{pmatrix}
\]

Their product is:
\[
AB = \begin{pmatrix} ab \\ b^{-1}a^{-1} \end{pmatrix}
\]

The inverse of \( AB \) is:
\[
(AB)^{-1} = \begin{pmatrix} a^{-1}b^{-1} \\ 1 \end{pmatrix}
\]

This demonstrates that the inverse of a product of matrices is the product of their inverses in reverse order.



In practical applications, we often encounter approximate transformations rather than exact equivalences. For example, in the visualization layer of a DSL (Domain-Specific Language), additional layout information may be included for visual purposes. This information is irrelevant to the textual representation of the DSL. To maintain flexibility between text editing and visualization, we can extend the Delta structure to store these auxiliary details, effectively converting approximate similarities into exact equivalences.



The following algebraic operations are supported:

\[
B \approx G(A), \quad A \approx F(B) \implies B + dB = G(A + dA), \quad A + dA = F(B + dB)
\]

Here, \( dB \) represents the derivative of \( B \) with respect to some variable.



The Nop platform incorporates a Delta-based design approach. This means that all structures within the system are equipped with an extension mechanism for storing additional information. For instance:
- In data processing: \( data + headers \)
- In Java classes: \( field + annotations \)
- In DSL models: \( props + ext_props \)



In mathematical terms, the operations described can be viewed as functors. Specifically, they map domain elements to elements of a codomain while preserving certain structure. For example:
- The Nop platform provides a bijective mapping between DSL objects and Excel objects via their respective inverses.

This relationship is formally expressed as:
\[
DslModel = Importor<Excel>, \quad Excel = ReportExporter<DslModel>
\]



Traditionally, programming involves writing code tailored to specific Excel files. This approach requires parsing and transforming the file into a data object. Reverse operations are similarly manual and often require recompiling derived classes.

However, the Nop platform automates these processes:
- Importing models and exporting data becomes seamless.
- Any Excel file can be mapped to a DSL model without manual code changes.



When new requirements emerge, traditional practices demand rewriting existing code and adjusting imports. This is where the Nop platform excels by allowing dynamic extension of existing models. For example:
\[
DslModel = Importor<Excel>, \quad Excel = ReportExporter<DslModel>
\]

This allows for flexible extensions while maintaining compatibility with existing systems.



The Nop platform supports reverse engineering through its Delta mechanism. This enables:
- Automatic generation of derived classes based on existing models.
- Dynamic updates to these classes as the base models evolve.

This capability significantly reduces development and maintenance overhead.



From a temporal perspective, software development often requires balancing between stability and change. The Nop platform's design allows for incremental updates without disrupting existing functionality.

For instance:
\[
App[t] = Delta[t-1] \times Generator[t] < DSL[t]
\]

This relationship ensures that each version of the application is built upon the previous one, minimizing regression risks.



The Nop platform leverages advanced mathematical concepts to provide a robust framework for software development. By automating many aspects of data transformation and reverse engineering, it simplifies the creation and maintenance of complex systems. This approach not only enhances productivity but also ensures that the resulting applications are both reliable and scalable.


The system under construction can be determined at the t time by the Delta determined at t-1. The structure `Generator[t] < DSL[t]>` is formed as a whole.

At the t time (e.g., deployment time), all business-related information is already known, allowing us to avoid excessive guessing about application scenarios. As a result, we only need to describe the information currently required, which in turn enables us to use a `Generator` customized based on t-time information to dynamically expand these details.

When applied to specific code implementation, the Nop platform provides a unique Delta customization capability. This capability allows for differential adjustments of all DSL structures within the system without modifying existing source code. This characteristic is analogous to Java's implementation, clearly demonstrating the uniqueness of Delta customization.

Assume we have built a system `A extends B extends C`. These components are bundled into jar files. Without altering this jar file, Java lacks any means of modifying the existing structure. Therefore, external补充新的信息是唯一的选择。



```
E extends A extends B extends C
```

However, by leveraging the Delta customization mechanism, we can supplement a delta description to replace existing structures. For example, replacing B with `E + F` results in:

```
A extends E extends F extends C
```

Mathematically, this is represented as:

```
X = A + B + C
Y = X + (-B + E + F) = A + E + F + C
```

The capability of Delta customization is evident in reversible computations. Perturbations can arise from any part, and these variations are then aggregated into a single external fix for `dApp`. This mathematical construction ensures that perturbation information flows freely between existing structures without altering the original source code.

For instance:

```
App = B + G<S>
App + dApp = B + G<S> + dApp = B + dB + (G + dG)<S + dS>
```

This demonstrates how Delta customization enables differential adjustments across various system components, ultimately addressing issues encountered at the t time.



While Docker also employs a similar structure with `App = Delta x-extends Generator<DSL>`, it is not compatible with Nop's dynamic Delta customization. Docker containers are immutable, and their layered relationships resemble a blockchain-like structure, ensuring an unalterable timeline of updates. However, this approach does not support Nop's external fixes for `dApp`.

