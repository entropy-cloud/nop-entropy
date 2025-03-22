

# Points of Discussion
@taowen organized a WeChat group named "Business Logic Decomposition Writing Group" and presented a point of view in the discussion.

> > > 

The business logic ultimately needs to be decomposed into files, folders, and Git repositories. Should we approach this from the perspective of: what is suitable for a file, what is suitable for a folder, and what is suitable for a Git repository?

Is the essence of software engineering reflected in this decomposition process? Does a generalizable rule exist that can guide us to perform the correct business logic decomposition? In this text, I would like to discuss my opinion by combining reversible computation theory.

---


## 1. Tree Structure: Long-term Correlation

We can understand the role of tree structure from an information cognition perspective. When information is scarce, we can only recognize **the existing one**. As information gradually increases, we identify differences and realize **one split into many**. If cognitive complexity further increases, we recognize **differences within differences**, which eventually forms a nested structure.

$$
Tree = List + Nested 
$$

Thus, the tree structure is a natural cognitive framework. One notable characteristic of this framework is that it effectively expresses **controlled long-term correlations**. This means that when control is applied at a parent node, it influences all child nodes and grandchild nodes, such as controlling access permissions in the root directory. Additionally, each node has a unique influence path, like DOM message bubbling in HTML.

The tree structure represents the composition relationship between the whole and its parts. A specific example is that both the parent node and child node have similar structures, such as directories consisting of subdirectories and files. When the whole and its parts exhibit self-similarity, mastering a limited number of core structures allows us to understand the overall structure through reasoning. For instance, in programming language theory, recursive application of finite syntax rules can generate infinitely many legal program statements. This phenomenon is widespread in nature and is known as fractals (Fractals).

Each node in the tree has a locally distinguishable name, such as filenames, while maintaining **a unique path** in the entire tree structure, which is useful for global positioning. For example, in a binary tree, each node can be assigned a unique ID using binary representation, where 0 represents a left branch and 1 represents a right branch (e.g., 1011 would reach the node reached by following the right, right, left path).

When we recognize the world and exert control over it, we need an effective coordinate system and an efficient propagation mechanism. Therefore, tree structures are often a natural choice.

---



First, we must recognize that multiple coordinate systems may exist under specific conditions. For example, in a plane, we can choose an infinite number of X-Y orthogonal coordinate systems. To find an entry point for understanding, it's not always essential from which angle we approach. For instance, different X-Y coordinate systems allow us to establish similar analytical geometry equations using unified algebraic methods.

For specific problems, certain optimal expression methods may exist. For example, a circle is a two-dimensional structure in Cartesian coordinates, but its true nature lies in one-dimensional representation in polar coordinates. Thus, the most effective expression is **dimension reduction**. When decomposing, we naturally hope to separate along stable boundaries, such as keeping r constant in polar coordinates. However, this is only possible through evolution over time. To observe temporal changes, we must witness the evolution at time t-1, which can only be done at time t.

For all problems that make up a set, it's clear that no optimal solution exists. When dealing with multiple tree structures, choosing the right decomposition method becomes crucial. A decision tree mechanism provides a standard: choose features that maximize information gain (reduce uncertainty to the greatest extent). However, depending on usage purposes, our focus may shift between features B and C, etc. This is constrained by human cognitive abilities, historical habits, and environmental limitations.

In an ideal scenario, abstracting the structure should reflect the essential relationships between the whole and its parts. However, in reality, structures grow organically over time, rooted in their environment and influenced by it. For example, religious beliefs form the foundation of vast cultural, economic, and political systems, despite their irrational nature.

Ultimately, what is suitable for a file? What is suitable for a folder? And what is suitable for a Git repository? From an abstract perspective, perhaps a unified management mechanism exists that aligns across these dimensions. However, in practice, each has its unique strengths and weaknesses. For instance, files are great for static data, folders for hierarchical organization, and Git repositories for collaborative version control.

But here's the catch: while we can manage these dimensions separately, the interplay between them can complicate matters. For example, a file might belong to multiple folders or be managed by different Git repositories. This interdependency requires careful navigation.

---



The ideal decomposition should simplify complexity while preserving essential information. However, as systems evolve, new requirements and interactions emerge, often complicating the structure further. The challenge lies in maintaining clarity without overcomplicating the decomposition.

For instance, managing a project with multiple teams and tools may require a mix of file-based workflows for small tasks and Git repository-based processes for larger, collaborative efforts. This hybrid approach can be effective but may also introduce complexity.

Ultimately, the choice depends on specific needs and constraints. There is no one-size-fits-all solution, but by thoughtfully evaluating each option, we can make informed decisions that align with our goals.

---


File storage is a static form of expression, or more precisely, a serialization of information. It is not the entirety of our knowledge. For example, in molecular biology, biological information related to genetic inheritance is essentially constructed from a limited set of abstract symbols such as ATGC (Adenine, Thymine, Guanine, Cytosine). However, to truly understand how this information functions and why it is organized in such a way, we must refer to its runtime structure, which occurs at different spatial locations during transcription, splicing, and folding processes.


## The Inversion of Splitting: New Insights



Reverse engineering theory has introduced a novel software construction methodology:

```
App = Biz x-extends Generator<DSL>
```

Essentially, it corresponds to the decomposition pattern:
```
Y = F(X) + Delta
```
where Y is the final product, F(X) is the base function, and Delta represents the incremental change.





DSL (Domain-Specific Language) is not directly used but can be reinterpreted **post hoc** via generators/translators like x-extends. When changes occur, these modifications can be input into the DSL model and propagated throughout the system via its structured mechanisms.



In the context of invertible computation, DSL serves as a structured representation of information. Beyond mere generation, it requires a series of processing techniques such as transformation, merging, and generation (e.g., Git for delta management in version control). Invertible computation extends this concept to the file level, treating the entire application as a hierarchical structure that can be controlled at a fine granularity.



Delta represents the natural outcome of invertibility. It is the cornerstone of invertible computing, enabling the reversal of operations through delta management mechanisms. The invertibility of functions hinges on their decomposable nature into base functions and incremental changes.

Mathematically, this can be expressed as:
```
A = B + C ==> C = A - B = A + (-B)
```

The invertibility of a function is directly tied to its representability in terms of Taylor series expansions (for analysis) and Newton's backward differences (for synthesis). In the physical realm, this manifests as iterative corrections using higher-order terms.



Based on the principles of invertible computation, two key strategies emerge:

1. **Maximizing Invertibility**: 
   - Preserve the system's invertible nature by maintaining delta tracking.
   - Ensure that all components are decomposable into base functions and incremental changes.

2. **Delta-Based Entropy Control**:
   - Isolate entropy generation within a specific delta scope.
   - Utilize delta mechanisms to encapsulate and manage entropy at a fine granularity.



Invertible computing shifts the focus from "what to build" to "how to reverse." It introduces new design dimensions:

- **Design for Reversibility**: 
  - Ensure that all aspects of a system are designed with reversibility in mind.
  - Implement delta mechanisms that allow for backward and forward operations.

- **Design for Maintainability**:
  - Enable incremental updates without disrupting the system's core functionality.
  - Facilitate easier debugging by allowing step-by-step reversal of changes.



In large-scale systems, entropy generation becomes a critical vulnerability. According to the Second Law of Thermodynamics, natural systems tend toward entropy increase (e.g., mutations). However, in software development, we can control entropy through delta mechanisms:

- **Delta Isolation**: 
  - Encapsulate unintended changes within specific deltas.
  - Minimize the impact of accidental modifications.

- **Version Control with Delta**:
  - Use delta-based versioning to track and revert changes efficiently.
  - Implement automated checks for delta impacts on core functionalities.



Invertible computing envisions a future where software construction is no longer a one-way process. Developers can not only build but also reverse-engineer applications, enabling unprecedented control over software evolution:

```
App1 = A + B + C
App2 = A + B + D = App1 - C + D = App1 + Delta
```

This shift from "build" to "construct and control" opens new possibilities for maintainability, extensibility, and adaptability in software development.



The invertible computing framework provides a powerful foundation for modern software development. By focusing on delta-based mechanisms and structured information representation, developers can build systems that are not only functional but also inherently reversible. This approach allows for more robust version control, easier debugging, and greater flexibility in system design.

