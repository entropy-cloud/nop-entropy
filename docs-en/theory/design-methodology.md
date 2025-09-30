Some discussions on the "Business Logic Split Patterns: file/dir/repository"
@taowen organized a WeChat group "Business Logic Split Patterns Authoring Group," and during the discussion he put forward a view

> > > 

Business logic ultimately has to be decomposed into files, directories, and git repositories. Should we approach it from this angle: what is suitable to split by file, what is suitable to split by directory, and what is suitable to split by git repository?

Does the essence of software engineering manifest in this very decomposition process? Do general, operational rules exist to guide us in correctly splitting business logic? In this article, I’d like to share my thoughts from the perspective of Reversible Computation theory.

# I. Tree Structure: Long-Range Associations

We can understand the role of tree structures from the perspective of informational cognition. When information is scarce, we can only perceive the existence of the one. As information increases, we recognize differences, realizing that the one splits into many. If cognitive complexity increases further, we recognize sameness within difference, and grouping and aggregation effectively form a nested structure.

$$
	   Tree  = List + Nested 
$$

Thus, the tree is a very natural cognitive framework. A remarkable derivative property of this framework is that a tree can effectively express a controlled form of long-range association. That is, when we exert some control at a parent node, we produce corresponding effects on all its children and grandchildren, such as controlling access permissions at the root directory. Meanwhile, a node has a definite and unique pathway to influence its parent and ancestors, for example, DOM event bubbling.

A tree expresses the composition relationship between the whole and the parts. A special case is when parent and child nodes have similar structures, such as a directory consisting of subdirectories and files. When such self-similarity between whole and parts exists, we only need to master a few core structures to extrapolate and understand the system’s overall structure. For example, in programming language theory, recursively applying a finite set of grammar rules can produce infinitely many valid program statements. This phenomenon is widespread in nature and is known as fractals.

Each node in a tree has a locally distinguishable name, such as a filename, and it has a unique path within the entire tree structure, which serves as a coordinate for global localization. For a binary tree, for instance, we can assign a unique identifier to each node using binary digits: 0 for the left branch, 1 for the right branch, and 1011 denotes the node reached by following Right-Left-Right-Right.

When we perceive the world and exert control over it, we need an effective coordinate system as well as an effective means of control propagation. Thus, the tree structure often becomes a necessary choice.

## II. How to evaluate the pros and cons of splitting methods?

First, we need to recognize that, in a given situation, multiple coordinate systems are often available. For example, in a plane we can choose infinitely many X–Y orthogonal coordinate systems. To escape from a tangled mess, we need a cognitive entry point; where we enter may not be that important. For instance, in different X–Y coordinate systems we can set up similar analytic geometry equations and solve them using a unified algebraic method.

For specific problems, there may exist an optimal representation. For example, a circle is a two-dimensional structure in Cartesian coordinates, and when the circle’s center is at the origin, we can clearly recognize the symmetry of up-down-left-right. However, the essence of a circle is one-dimensional, and only in polar coordinates can we achieve the simplest expression. Thus, the most effective representation is dimension reduction. When we split, we naturally hope to separate along invariant boundaries, such as keeping the coordinate r constant. Yet it’s often only during the evolutionary process that we discover patterns of change. And an inconvenient fact is that only at time t can we observe the evolution result at time t-1. Therefore, to achieve the most effective split, an ultimate requirement is \*\*to transport future knowledge to the present\*.

Across the set of all problems, there is clearly no globally optimal representation scheme. When choosing among multiple tree structures, decision tree mechanisms provide a criterion: select features to maximize information gain (the greatest reduction in uncertainty), which simply means reducing impurity after the split. However, our focus may change depending on usage scenarios. In some cases we value feature B; in other cases we value feature C, etc. Constrained by human cognition, historical habits, environmental limitations, and more, we tend to choose a single primary splitting approach—ultimately a compromise.

Ideally, structural abstractions should simply and clearly reflect the essential relationships in the domain. But any complex structure is not created outright; it grows gradually. Growth depends on its surrounding ecosystem; it does not happen out of thin air. The growth of all things feeds back into the ecosystem, further complicating matters. For example, as an atheist, you may think religion is utter falsehood—useless nonsense. Yet human societies have built enormous economic, cultural, and political systems around religion. A vast amount of human civilizational treasure hinges on what may appear to be an unreliable conceptual framework, and most of the time society functions well.

What belongs in a file? What belongs in a folder? What belongs in a git repository? In the fully abstract sense, what we need might be a consistent, universal management mechanism that is unified across layers. In reality, off-the-shelf functionality exists only at certain layers. For instance, we might hope the basic unit of permission control is any directory, but git doesn’t support that. Sure, you can build it yourself, but that’s a lot of work, plus a whole set of supporting tools, plus user training issues—so we give up.

File storage is a static representation—in other words, a serialized form of information—which is not the entirety of our knowledge. For example, molecular biology has found that genetic information is essentially stored and transmitted as a DNA sequence composed of a few abstract symbols such as A, T, G, and C. But to truly understand how this information works and why it is organized this way, we ultimately have to refer to its runtime structures: transcription, splicing, folding, and so on, occurring across space and time.

## III. New insights into splitting from Reversible Computation

Reversible Computation theory proposes a new software construction paradigm:

```
	App = Biz x-extends Generator<DSL>
```

Essentially, it corresponds to a decomposition of the form Y = F(X) + Delta.

First, this is a generative system. The DSL information we express is not used directly; rather, it can be reinterpreted **after the fact** through transformers/generators. When circumstances change, we can simply feed the changes into the system via the DSL model, which will then automatically propagate throughout the system. Such wide information propagation facilitates the localization of splits.

Second, a DSL is a structured representation of information. In concrete implementations of Reversible Computation, we not only introduce structured expressions, but also a suite of processing capabilities for such structured expressions, such as transform, merge, and generate. git manages Delta updates at the file level, whereas Reversible Computation pushes the granularity below the file level and treats the entire application as a single tree-structured representation for overall structural governance.

Third, Delta and automated Delta merge mechanisms are the core of Reversible Computation. Delta is a natural consequence of reversibility:

$$
     A = B + C ==> C = A - B = A + (-B)
$$

The ability to perform Delta decomposition is a fundamental reason why our world is comprehensible. Mathematically, any analytic function can be decomposed into a Taylor series. Newtonian mechanics essentially corresponds to the first-order linear term, and in physics we can continually add second- and third-order terms to deepen our understanding of the system.

According to the conception of Reversible Computation, to effectively control entropy increase, we adopt two strategies:

1. Strive to maintain system reversibility
2. Separate uncontrollable chaotic parts from the main body via Delta

Reversibility manifests as the ability to easily separate and delete information added to the system, such as easily disabling or removing a feature. During development, this may appear as version control; in deployment and operations, it shows up as automatic rollbacks, canary/gray releases, etc. Reversibility can serve as a criterion for separation of concerns and also as a systematic structural construction method.

A large system decays as its structure continually absorbs shocks from incidental requirements. According to the second law of thermodynamics, a naturally evolving system inevitably increases in entropy (unless it is a dissipative system with constant metabolism—i.e., constantly rewritten). While we cannot control the increase in entropy, we can control where entropy accumulates. Based on Delta mechanisms, we can isolate incidental requirements into a specific Delta. In practical Reversible Computation, all logic can be customized via Deltas. Thus, after developing a product, we can finely customize the mainline logic using stored Delta descriptions without modifying the mainline code at all.

From the perspective of Reversible Computation, software construction is no longer a gradual part-to-whole assembly relationship; rather, it is a transformation relationship based on structural operations.

```
      App1 = A + B + C
	  App2 = A + B + D = App1 - C + D = App1 + Delta
```

Without disassembling App1 at all, we can construct App2 through a purely \*\*"append"\*\* operation. From a production model standpoint, Reversible Computation changes the traditional "production-as-assembly" model into an "computation-as-production" model that better suits the construction of abstract logical structures.

Therefore, based on Reversible Computation, we can ask the following three questions more often when splitting business logic: Is it reversible? Is it a Delta? Have you DSL-ed today?
<!-- SOURCE_MD5:af4dca5c96e1822fd752320a30c359bb-->
