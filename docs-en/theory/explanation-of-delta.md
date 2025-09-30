
# A Clarification of the Delta Concept for Programmers, Using Git and Docker as Examples

The theory of Reversible Computation proposes a general formula for software construction

```
App = Delta x-extends Generator<DSL>
```

The entire implementation of the Nop platform can be viewed as a concrete realization of this formula. Among the elements, the most critical—and also the most easily misunderstood—is the Delta concept in the theory of Reversible Computation, namely the Delta in the formula above.

> For a detailed introduction, see [Reversible Computation: The Next-Generation Theory of Software Construction](https://mp.weixin.qq.com/s/CwCQgYqQZxYmlZcfXEWlgA)

Reversible Computation can be regarded as a comprehensive and systematic theory targeting the Delta concept. Therefore, common industry practices based on Delta can be interpreted within the framework of Reversible Computation. I often cite git and docker as examples, such as:

> Q: What is Delta? Is each git commit a delta?
> 
> A: git is defined over a general line space, so it is unstable for domain problems—domain-level equivalent adjustments will lead to merge conflicts in the line space. Docker technology is similar in this regard. Many years ago, Python tools could manage dependencies and dynamically generate virtual machines. The essential difference is that a VM’s delta is defined over an unstructured byte space, whereas docker’s delta is defined over a structured file space.

Some readers feel confused after reading the above. Does the sentence “Docker technology is similar in this regard” mean that the two are essentially the same, or that they are essentially different?

Indeed, this requires more detailed explanation. Although both are called Delta, there are profound differences between one Delta and another. In general, both git and docker essentially involve delta computation, but the Deltas they correspond to are fundamentally different; these fine-grained distinctions need to be explained from a mathematical perspective. Most people’s understanding of Delta is based on superficial impressions, and there are many ambiguities. Many debates are essentially due to unclear definitions, not inherent contradictions in the matter.

> The mathematical understanding and use of Delta in Reversible Computation are actually very simple; it’s just that its objects are not the numbers or data most people are used to. Those without specialized abstraction training may find it hard to shift gears at first.

Since some readers reported feeling confused by the mathematical terms mentioned in earlier articles, I will attempt to add more detailed concept definitions and case analyses here. If anything remains unclear, feel free to join the Nop platform discussion group and continue asking questions—the QR code for the discussion group can be found in the WeChat public account’s menu.

## I. Universality and Existence of Delta

In mathematics and physics, when we propose a new concept, the first step is to argue for its universality and existence. The first key understanding of Delta in Reversible Computation is: Delta is ubiquitous.

```
A = 0 + A
```

Any system with an identity element can naturally define a Delta: any total quantity is equivalent to the identity element + itself; in other words, any total quantity is a special case of Delta. The identity element exists naturally in most cases—for example, inaction corresponds to a no-op, and the no-op combined with any other operation is equivalent to that operation; thus, the no-op is a naturally existing identity element.

Many people might be puzzled by “the total is a special case of Delta.” It seems obvious, but what’s the point? People often fail to connect mathematical principles to the real physical world, leading them to think precise mathematical definitions are useless. A key corollary here is: Since a total is a special case of Delta, in principle the total can adopt exactly the same form as Delta; there’s no need to design a separate Delta form just to express Delta. For example, a Delta for JSON can be expressed using JSON Patch syntax, but JSON Patch’s format is very different from the original JSON format; it is a custom delta form, not consistent with the full format.

```
[
  {
    "op": "add",
    "path": "/columns/-",
    "value": {
      "name": "newColumn",
      "type": "string", // or other data types
      "primary": false // whether it's the primary key
    }
  },
  {
     "op": "remove",
     "path": "/columns/2"
   }
]
```

The approach in the Nop platform is:

```xml
<table name="my_table">
   <columns>
     <column name="newColumn" type="string" primary="false" />
     <column name="column2" x:override="remove" />
   </columns>
</table>
```

Or using JSON format

```
{
    "type": "table",
    "name": "my_table",
    "columns": [
       {
          "type": "column",
          "name": "newColumn",
          "type": "string",
          "primary": false
       },
       {
         "name": "column2",
         "x:override": "remove"
       }
    ]
}
```

JSON Patch’s format is entirely different from original JSON, and it can only use indexes to mark list rows, whereas Delta merges in Nop are defined by unique properties like name, which is more stable semantically (inserting a row does not affect the positioning of all subsequent rows). Moreover, the Delta format is exactly the same as the original format—it merely introduces an extra `x:override` attribute.

In the Nop platform we systematically implement the mathematical idea that “the total is a special case of Delta.” In all places that require expressing Delta—such as modification data submitted from the frontend to the backend, or change data submitted from the backend to the database—we adopt an isomorphic design: the submitted Delta data structures are basically consistent with ordinary domain object structures, and a few extra fields express additions, deletions, etc.

Recognizing that the total is a special case of Delta also dispels a common misunderstanding: Delta consists of small local changes. In reality, a Delta can be as large as the entire system. When inverses exist, a Delta can even be larger than the whole!

## II. Different Spaces Have Different Delta Definitions

A key insight from modern abstract mathematics is: Operation rules are bound to a particular space. Because the math rules learned in middle school are applied to familiar number spaces—natural numbers, rational numbers, real numbers—most people don’t realize that operation rules are valid only in their corresponding spaces, and that when defined, the operation rules and the spaces they act upon are defined as a whole. In mathematics, the minimal structure that includes identity and inverse concepts is a group (Group). Let’s examine the group definition. The standard definition provided by Zhipu Qingyan AI is as follows:
A group (G, *) consists of a set G and a binary operation *: G × G → G such that the following four conditions hold:

1. Closure: For all a, b ∈ G, a * b is also in G.
2. Associativity: For all a, b, c ∈ G, (a * b) * c = a * (b * c).
3. Identity Element: There exists e ∈ G such that for all a ∈ G, e * a = a * e = a. This element e is the identity of the group.
4. Inverse Elements: For each a ∈ G, there exists a−1 ∈ G such that a * a−1 = a−1 * a = e. This element a−1 is the inverse of a.

Note first that in the group definition, the base set G and its operation * are a whole; neither G nor the operation alone can form a group. But in everyday communication we abbreviate the group (G, *) as group G, which can mislead some people.

In the definition, * is merely an abstract symbol; it does not mean multiplication. We can define different operations in different spaces, and identities and inverses differ under different operations. For example, both addition and multiplication on the real number space ℝ form groups, but they are not operations of the same group. Below is the detailed definition provided by Zhipu Qingyan AI,

1. Additive group (ℝ, +):
   
   - Closure: For all a, b ∈ ℝ, a + b is in ℝ.
   - Associativity: For all a, b, c ∈ ℝ, (a + b) + c = a + (b + c).
   - Identity: There exists 0 ∈ ℝ such that for all a ∈ ℝ, 0 + a = a + 0 = a. The element 0 is the identity for addition.
   - Inverse: For each a ∈ ℝ, there exists −a ∈ ℝ such that a + (−a) = (−a) + a = 0. The element −a is the additive inverse of a.
     Therefore, ℝ under addition forms a group, called the additive group.

2. Multiplicative group (ℝ*, ·):
   
   > Note, the multiplicative group excludes zero because zero has no multiplicative inverse. So we consider the set ℝ* of nonzero real numbers.
   
   - Closure: For all a, b ∈ ℝ*, a · b is in ℝ*.
   - Associativity: For all a, b, c ∈ ℝ*, (a · b) · c = a · (b · c).
   - Identity: There exists 1 ∈ ℝ* such that for all a ∈ ℝ*, 1 · a = a · 1 = a. The element 1 is the identity for multiplication.
   - Inverse: For each a ∈ ℝ*, there exists a−1 ∈ ℝ* such that a · a−1 = a−1 · a = 1. The element a−1 is the multiplicative inverse of a.
     Therefore, ℝ* under multiplication forms a group, called the multiplicative group.

The Delta concept in Reversible Computation is inspired by the group idea; thus for every kind of Delta, we can analyze it by analogy to the group definition, from the following aspects:

1. In which space is the Delta operation defined?

2. Does the result of the Delta operation remain in this space?

3. Does the Delta operation satisfy associativity? Can we perform local operations first and then combine with the whole?

4. What is the identity element of the Delta operation?

5. Does the Delta operation support inverse operations? What is the form of the inverse?

## III. Delta Operations in Git

### 1. In which space is Git’s Delta operation defined?

Git’s diff splits a text file into lines and compares lists of text lines. Therefore, Git’s Delta structure space can be viewed as a line-text space. This is a general-purpose Delta structure space. Every text file can be mapped into the line-text space as a list of lines; in other words, every text file has a representation in the line-text space.

### 2. Does Git’s Delta operation result remain in the line-text space?

Git’s apply feature can apply a patch delta file to the current text file, yielding a “legal” text file. But if we scrutinize it, that legality is not very robust.

First, Git patches are highly specific—they are tightly bound to their application targets. For instance, a patch constructed from project A cannot be directly applied to an unrelated project B. A patch targets a specified base file version (state). In this situation, it’s hard to regard a patch as a conceptually independent entity with standalone value.

Second, applying multiple patches to the same base file may produce conflicts; in conflicting files we change the file’s original structure by inserting the following markers:

- `<<<<<<< HEAD`: Marks the beginning of content from the current branch (usually your target branch, i.e., the branch HEAD points to).
- `=======`: Separates content from the current branch and the merged branch.
- `>>>>>>> [branch name]`: Marks the beginning of content from the merged branch (usually the branch you’re merging).

The essence of conflicts is that Delta operations have exceeded the original structural space, producing some abnormal structure. This structure lies outside the original definition of legal structures.

Third, even if multiple patches do not conflict, the merged result may break the source file’s expected syntax structure. The merge may yield a “legal” line-text file, but it might not be a legal source code file. To guarantee that the merge result has a valid syntax structure, we must perform the merge in the abstract syntax tree (AST) structure space.

In the four “commandments” of group definition, closure comes first, underscoring its indisputable importance. Yet those without abstract math training often overlook it. Is closure important? Will we fail without closure? The power of mathematics comes from continuous automated reasoning. If the reasoning process can at any time break out of the known space into some unknown state, then the edifice of mathematical reasoning may collapse at any time; the only hope is the favor of Lady Luck and the salvation of God (a programmer manually editing conflict content is akin to the intervention of the hand of God).

### 3. Does Git’s Delta operation satisfy associativity?

Suppose we have multiple patch files: patch1, patch2, patch3... Can these small patches be merged into one large patch? If they can be merged, does the order matter? Are (patch1 + patch2) + patch3 and patch1 + (patch2 + patch3) equivalent?

If Git’s Delta satisfied associativity, it would mean we could merge patch files independently of the base file, e.g., via a command like:

```
git merge-diff patch1.patch,patch2.patch > combined.patch
```

Unfortunately, this command does not exist. To merge multiple patches in Git, you must apply them one by one to the base file, and then generate the diff in reverse.

```
git apply --3way patch1.patch
git apply --3way patch2.patch
git diff > combined.patch
```

Associativity is a particularly fundamental universal law in mathematics; all major existing mathematical theories presuppose it (including category theory, which is hailed as the most pure and generalized). In the mathematical world, it’s almost impossible to proceed without associativity.

> The only mathematical object I know that does not satisfy associativity is octonions—an extension of quaternions, which themselves are an extension of complex numbers. Octonions currently have only niche applications.

Why is associativity important? First, associativity enables localized cognition. Where associativity exists, we do not need to consider the existence of a “substrate,” nor do we need to worry about what happens far away along the chain of reasoning—we need only focus our attention on objects that interact with us directly. As long as we understand which elements a given object can combine with, and what results the combinational operations yield, then in the sense of category theory we have thoroughly mastered all knowledge about that object (in Marxist philosophy, this corresponds to “man is the sum of all his social relations”).

Second, associativity makes reuse possible. We can pre-combine adjacent objects to form a complete new element with independent semantics.

```
x = a + b + c = a + (b+c) = a + bc
y = m + b + c = m + bc
```

In the example above, we can reuse the pre-combined object bc when constructing x and y. Interestingly, if reuse is to be effective, the same object should be able to combine with many other objects—for example, a+bc and m+bc. However, in Git’s Delta operations, patches lack this freedom of combinability: a patch can only be applied to a fixed version of a base file, so it is essentially non-reusable.

```
... + a + b + c + ...
    == ... + (a + (b + c)) + ...
    == ... + ((a + b) + c) + ...
```

Satisfying associativity means you may freely choose whether to combine with an adjacent object, decide to combine left first or right first, or not combine and wait for others to combine with you. Formally, this means we can insert or remove parentheses in computation sequences at any time, and the computation order does not affect the final result. Therefore, the third role of associativity is that it creates possibilities for performance optimization.

```
function f(x){
    return g(h(x))
}
```

Function operations satisfy associativity; thus, at compile time, the compiler can directly analyze g and h, extract their implementation instructions, and merge-optimize g and h’s instructions without any knowledge of f’s call environment. Associativity also enables parallel optimization.

```
a + b + c + d = (a + b) + (c + d) = ab + cd
```

In the example above, we can compute a+b and c+d simultaneously. Many fast algorithms rely on optimization possibilities afforded by associativity.

### 4. What are the identity and inverse elements of Git’s Delta operation?

The identity element of Git’s Delta operation is clearly an empty patch file—it represents doing nothing. Some might wonder: If the identity does nothing, is it even necessary? First, we should understand the identity’s peculiarity: Once an identity exists, it is everywhere.

$$
a*b = e*e*e*a*e*e*b*e*e*e
$$

You can insert any number of identities before and after any object. This means that although a and b appear to interact directly, they actually interact indirectly, immersed in an ocean of identity elements. So what—can this ocean of identities stir any trouble? To truly understand the identity’s importance, we must consider it together with the existence of inverses.

$$
e = a*a^{-1} = a^{-1}*a
$$

Now the ocean of identities is not empty; it provides infinitely many intermediate computational processes whose result ultimately returns to nothing.

$$
a*b = a *e * b = a * c*d * d^{-1} * c^{-1} * b
$$

Suppose we have already constructed a*c*d; then we can reuse this construction to form a*b:

$$
acd * d^{-1} * c^{-1} * b = ab
$$

> In our physical world, in the quantum vacuum beyond human reach, while it appears empty, it is actually a dynamic balance of positive and negative particles repeatedly arising and annihilating. If a black hole is nearby, its strong gravitational field can separate the spontaneously fluctuated particle-antiparticle pair, with one falling into the event horizon and the other escaping. This is the famed Hawking radiation and black hole evaporation.

In Reversible Computation, the introduction of inverses is precisely the key to achieving coarse-grained reuse.

```
X = A + B + C
Y = A + B + D
  = A + B + C + (-C) + D
  = X + (-C+D) = X + Delta
```

Suppose X consists of A+B+C and we want to produce Y = A + B + D. If identities and inverses exist, we can start from X and, without disassembling X at all, transform X into Y through a sequence of Delta operations. Using associativity, we can cluster −C and D together to form a complete, independent Delta. Under the perspective of Reversible Computation, the principle of software reuse undergoes a fundamental shift: from “identical reusable” component reuse to “related reusable” in Reversible Computation—any Y and any X can be transformed via a Delta, thereby achieving reuse without requiring traditional part-whole composition relationships (Composition).

Inverses and identities are also indispensable for solving equations—this more complex mode of reasoning.

```
A = B + C
-B + A = -B + B + C = 0 + C
C = -B + A
```

When solving equations, moving terms across the equality essentially adds inverse elements to both sides and then omits the generated identity.

Git can apply patches in reverse and can use the patch command to produce reversed patches

```
git apply -R original.diff

patch -R -o reversed.diff < original.diff
```

But due to the lack of associativity, applications of reversed patches in Git are rather limited.

Based on the analysis in this section, although Git provides a kind of Delta operation, its mathematical properties are not satisfactory. That implies Git-based Deltas are difficult to process at scale automatically; operations can fail at any time, requiring human intervention. By contrast, Docker fares much better—its Delta operations are exemplary.

## IV. Delta Operations in Docker

### 1. In which space is Docker’s Delta operation defined?

One of Docker’s core underlying technologies is the stacked file system OverlayFS. It relies on other file systems (e.g., ext4fs and xfs) and does not participate directly in the partitioning of disk space; it merely “merges” different directories from underlying file systems and presents them to the user—this is the union mount technique. When OverlayFS looks up a file, it searches the upper layer first; if not found, it then searches the lower layer. If it needs to list all files in a folder, it merges all files from the upper and lower directories and returns them together.

Docker image Delta is defined in the file system space; the minimal unit of Delta is not a byte but a file. For example, suppose we have a 10 MB file and we add a single byte to it. The image increases by 10 MB because OverlayFS undergoes a [copy up](https://blog.csdn.net/qq_15770331/article/details/96702613) process—copying the entire lower-layer file up to the upper layer, and then modifying it there.

Some might think the difference between Git and Docker Deltas is that one is a linear list and the other a tree structure. But that is not their essential difference. The truly important distinction is that Docker’s Delta structure space has stable coordinates for unique positioning: each file’s full path is a unique coordinate in the file structure space. This coordinate system is stable because local changes to the file system—such as adding or removing a file—do not affect other files’ coordinates. In Git, however, line numbers are used as positioning coordinates; adding or deleting lines causes massive coordinate shifts in subsequent lines. Therefore, Git’s coordinate system is unstable.

> From a philosophical angle, Docker’s structure design consists of both “name” and “reality”: every file has its own name—its path—which uniquely locates it within the Delta structure. The “reality” corresponding to this “name” is irrelevant to Delta merging and nobody cares—just overwrite it. In Git’s design, line numbers are merely temporary names susceptible to other lines. To precisely locate a line, a patch also includes the content of adjacent lines, effectively using “reality” as an auxiliary means of positioning. Using “reality” as “name” directly limits a patch’s independence.

Docker’s coordinate system only controls down to the file level. If we want to uniquely locate and perform Delta computation inside a file, what should we do? The Nop platform builds a domain coordinate system inside DSL domain model files via the XDef metamodel, allowing precise location of any node in XML or JSON files. In addition, Nop includes a virtual file system similar to OverlayFS that stacks multiple Delta layers into a unified DeltaFileSystem.

One might ask: Must the structure space be a tree? Not necessarily. For example, AOP operates in a fixed three-layer structure space of package-class-method rather than a tree with arbitrary depth. Similarly, in the relational database domain, we use table-row-column, another three-layer structure space. As long as positioning coordinates are stable, we can develop systematic Delta operation mechanisms on top of them.

> Trees have many advantages. First, they unify relative and absolute coordinates: from the root to any node there is a unique path, which can be used as the node’s absolute coordinate; meanwhile, within any subtree, each node has a unique path relative to that subtree, its relative coordinate. From the node’s relative coordinate and the subtree root’s absolute coordinate, it is easy to compute the node’s absolute coordinate (just concatenate them).
> 
> Another advantage of trees is ease of governance: every parent node can act as a control node, automatically propagating shared attributes and operations down to each child.

Another detail about Delta structure spaces: when inverses exist, the space must be an extended space composed of positive and negative elements, not just the positive elements we are used to. When we say “Docker’s Delta operation is defined in the file system space,” that file system must support “negative files.” Docker’s specific approach is to use a special Whiteout file to indicate deletion of a file or directory. In other words, we convert the ephemeral action of deletion into a static, persistent, operable object (file or directory). Mathematically, this is equivalent to converting A − B into A + (−B)—a seemingly trivial but crucial conceptual shift.

“Delta structure spaces must be extended spaces composed of positive and negative elements”—this may sound puzzling, so here is an example. Suppose we are dealing with a somewhat complex multidimensional space,

```
X = [A, 0],
Y = [0, -B],
X + Y = [A, -B]
```

If X represents increasing A in the first dimension and Y represents decreasing B in the second, and if we allow combining X and Y, the result is a mixture containing +A and −B simultaneously. If each dimension were only allowed to hold positive objects, this mixture would be illegal. To ensure Delta merges can always succeed, our only choice is to extend the original positive-object space to allow simultaneous presence of positive and negative objects. For another example, in the Nop platform’s ORM model definition, we stipulate that the Delta structure space is a DSL syntax space, where we can express the following Deltas:

```
X = add table A(+field C1, +field C2)
Y = modify table A(+field C3)
Z = modify table A(-field C3, +field C4)
X + Y = add table A(+field C1, +field C2, +field C3)
X + Y + Z = add table A(+field C1, +field C2, +field C4)
X + Z = ?
```

In the example above, X represents adding a table A with two fields C1 and C2. Y represents modifying table A by adding field C3. Combining X and Y yields: add table A with fields C1, C2, and C3. Combining further with Z yields: add table A with fields C1, C2, and C4. Here, field C3 is added in Y and reduced (removed) in Z; the end result is that C3 has been deleted. Now here’s the problem: What is the result of combining X and Z? According to X, table A has no field C3 at that point. Then Z requires deleting a nonexistent field from table A—how can that be executed? If we attempt this in a database, it will fail. To ensure Delta operations are always executable, we choose to accept the following as a legal Delta:

```
U = add table A(+field 1, +field 2, -field 3, +field 4)
```

Thus we can express the result of combining X and Z:

```
X + Z = U
```

One might ask: When we ultimately generate create-table statements, negative fields are not allowed—what should we do? It’s simple: the extended space is a feasible abstract mathematical space that allows computations to occur; it contains all potential operations, but these happen in the abstract space and do not affect our physical world. When we really need to generate SQL from the result, to create actual database tables, we can add a projection operation to project objects from the feasible extended space into the physical space that contains only positive objects.

```
P(U) = add table A(+field 1, +field 2, +field 4)
```

This projection operation removes all negative objects and retains all positive ones.

> This is a standard mathematical routine: add illegal results to the original space to form an extended space, making operations in the extended space legal. For example, we must add complex numbers to the solution space for quadratic equations to have general solutions; otherwise, some are unsolvable in the real number space.

$$
x^2 + 1= 0 \Longrightarrow x = ?
$$

### 2. Does Docker’s Delta operation result remain in the file system space?

Unlike Git, Docker’s Delta operations always produce legal merge results. First, any two Delta layers can be freely merged, unlike Git’s strict constraints.

```
FROM registry.access.redhat.com/ubi8/ubi-minimal:8.8
WORKDIR /work/
RUN chown 1001 /work \
    && chmod "g+rwX" /work \
    && chown 1001:root /work
COPY --chown=1001:root target/*-runner /work/application

EXPOSE 8080
USER 1001

ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
```

We can freely change the FROM configuration in the Dockerfile. When upgrading the OS image layer, we do not need to modify the application layer’s Dockerfile or any application files; we can simply rebuild the image. In Git, if we change the original base file directly, all patches built against that original base will fail.

> After packaging into an image, Docker’s upper-layer image is bound to its dependent lower-layer images via unique hash codes, which can be viewed as a practical security measure. Conceptually, upper and lower images are independent—when the lower image changes, the upper image’s content (including the Dockerfile) need not change; we simply rebuild.

Second, Docker’s Delta merges never produce conflicts. This greatly boosts the automation of image construction. Combined with the prior section, this implies that in Docker’s Delta structure space, any two Deltas can interact.

> The core rule of Docker image merging is name-based overwrite, with a deterministic direction from top to bottom; conflicts clearly never occur.

If some Deltas already exist, Delta operations produce more new Deltas. Naturally we ask: where do these base Deltas come from? We hope to have as many available Deltas as possible. In mathematics, a standard routine is to first prove a certain mathematical object has many excellent properties, and then find ways to construct large numbers of them. In software, this corresponds to using generators to create Deltas.

Docker’s masterstroke is the introduction of Dockerfiles and the Docker Build tool. It leverages Linux’s decades of heritage—every command-line tool for file manipulation immediately becomes a valid Generator in Docker’s Delta structure space. Each Generator has clear business meaning, can be understood and used independently, and can be chained together into more complex Generators. From a mathematical perspective, Docker’s Delta structure space defines basic addition and subtraction; can we further define functions in this space to map one structure to another? Generators can be viewed as mapping functions in the Delta structure space, and they can indeed be expressed as functions in programming languages.

By contrast, Git’s Delta structure space lacks a meaningful Generator mapping mechanism. Since the underlying Deltas lack independent significance, building a meaningful function mapping mechanism atop them is even harder. Although patches are textual, and in any programming language we can write text-processing functions to generate patches, due to the lack of generality, we typically only write custom Generators or Transformers for specific scenarios rather than combining large numbers of pre-built Generators as in Docker.

> A good mathematical space needs solid basic units and operational properties, and it should support rich dynamics. Introducing Generators ensures we can continuously produce new Delta structures and adapt Deltas at different abstraction levels.

Reversible Computation summarizes the construction rules of Generators and Deltas into a general formula for software construction, and provides a standard technical route for its realization

```
App = Delta x-extends Generator<DSL>
```

DSL here stands for Domain Specific Language, and it implicitly defines a Delta structure space. All structures describable by a programming language collectively form a Delta structure space (the AST space). Unlike the file system space, there is no unified AST space; we must study each language’s AST space separately. However, in the Nop platform we do not directly use general-purpose languages to write business logic; we extensively use DSLs. For example, the IoC container uses beans.xml, the ORM engine uses app.orm.xml, and the logic orchestration engine uses task.xml—each specialized for its domain. In Nop, all XDSLs use the XDef metamodel language in XLang to define language syntax structures. These share the general Delta merge rules defined in XLang, ensuring all DSLs defined via XDef support Delta merge operations and automatically define a Delta structure space. That is, although there is no unified AST space, we can define unified Delta merge operations and implement them uniformly.

Generators in Reversible Computation broadly refer to all structure generation and transformation functions executed within the Delta structure space, be they standalone code generation tools or language-embedded compile-time metaprogramming mechanisms such as macros. Nop adds standardized compile-time metaprogramming syntax to all DSLs. This means DSLs need not define their own metaprogramming syntax; they can reuse the standard syntax and libraries defined by XLang.

```xml
<workflow>
   <x:gen-extends>
      <oa:GenWorkflowFromDingFlow xpl:lib="/nop/wf/xlib/oa.xlib">
        {
          // JSON similar to what the DingTalk-like workflow designer generates
        }
      </oa:GenWorkflowFromDingFlow>
   </x:gen-extends>
   <!-- The content here will be Delta-merged with what x:gen-extends generates -->
</workflow>
```

* You can use XLang’s built-in x:gen-extends syntax to convert the DingTalk-like workflow JSON format into the NopWorkflow DSL format.
* On top of what x:gen-extends generates, you can make local modifications.

After a Generator acts on a DSL, the result may be a new DSL; then we can continue using XLang’s mechanisms to perform Delta processing. But it might also leave Nop and generate code in general-purpose languages like Java or Vue; in that case, we can only leverage the language’s built-in extends and similar mechanisms to implement partial Delta merge capabilities. Although general-purpose languages’ AST spaces can define Delta merges, their mathematical properties are not as favorable as those of XDSLs. For more on XDSL, see [XDSL: General-Purpose Design of Domain-Specific Languages](https://mp.weixin.qq.com/s/usInt7_odzvFzuiIUPw4iQ).

```java
// _NopAuthUser is the auto-generated part; handwritten code and generated code are isolated via extends
class NopAuthUser extends _NopAuthUser{
   // In handwritten code you can add new functions and properties; they are Deltas on top of the base class
   public boolean isAdminUser(){
      return getRoles().contains("admin");
   }
}
```

* Java’s extends syntax does not support deletion semantics, nor does it support deep nested merges akin to trees.

Git and Docker can both be seen as leveraging familiar, existing Delta structure spaces (line-text space and file system space). Reversible Computation summarizes the related laws and supports systematically constructing specialized Delta structure spaces. The Nop platform provides a series of reusable foundational architectural tools for this. Docker can be regarded as a concrete application of Reversible Computation.

```
App = DockerBuild<DockerFile> overlay-fs BaseImage
```

### 3. Does Docker’s Delta operation satisfy associativity?

Docker uses OverlayFS to divide the file system into multiple layers; upper-layer files automatically overwrite lower-layer files. Deleting a file in Docker actually adds a special Whiteout file in the upper layer to mask the lower-layer file. This allows the container to appear as if the file has been deleted, even though it still exists in the lower layer.
Mathematically, Docker’s Delta operation is a simple overwrite that automatically satisfies associativity.

```
  A ⊕ B = B
  (A ⊕ B) ⊕ C = A ⊕ (B ⊕ C) = C
```

With associativity, Docker image construction can, to some extent, be divorced from the base image. In fact, a Docker image is just a tarball which, once unpacked, becomes directories and files in the file system. Knowing a few rules, you can bypass the docker application entirely and build Docker images directly using tar.

Docker images gain conceptual independence; thus they can be managed and distributed centrally via DockerHub. All Delta structures satisfying associativity can establish similar independent management and distribution mechanisms.

### 4. What are the identity and inverse elements of Docker’s Delta operation?

Clearly, doing nothing is the identity element in Docker’s Delta space. The Whiteout file can be regarded as an inverse. But there is a subtlety: In the group definition, inverses are defined per element—each a has its own a−1, and these inverses are different (equal inverses imply equal elements). That is, inverses in a group have a certain specificity: a−1 is dedicated to canceling a’s effect. However, Docker’s Whiteout file is only for placement; its content is empty. Once a Whiteout exists, it automatically indicates deletion of the lower-level file with the same path, no matter what the file contents are. Therefore, when used as an inverse, the Whiteout lacks specificity—it can cancel a lower-level file of the same path regardless of its content.

Mathematically, this lack of specificity appears as the idempotence of deletion:

$$
Delete * Delete = Delete
$$

Deleting a file twice is equivalent to deleting it once.

Mathematically, idempotence conflicts with group structure. If an idempotent element exists, the structure cannot be a group.

Assume idempotence: a*a = a, then

$$
\begin{aligned}
a     &= a*e = a * (a*a^{-1}) \\
      &= a* a * a^{-1} = (a* a) * a^{-1} \\
      &= a* a^{-1} \\
      &= e
\end{aligned}
$$

The above derivation shows that if a group structure contains an idempotent element, it must be the identity. By contraposition, if there exists a non-identity idempotent element, the structure cannot be a group. Because in implementing inverse operations we often adopt idempotent deletions to minimize recorded information, the mathematical structure we define is not truly a group—only some kind of structure that supports reversible operations.

#### Monad and Adjoint Functors

One might ask: What if there are no inverses? Then you get the Monad so beloved in functional programming. The group definition has four “commandments”: closure, associativity, identity, and inverses. Satisfying the first two yields a semigroup; satisfying the first three yields a monoid (the identity is also called the unit), which is the Monad. Thus, Monad is a relatively impoverished structure mathematically and not so remarkable. Even in category theory, the truly core concept is the adjoint functor, which is a generalization of reversible operations.

> For an introduction to Monad, see my article [A Beginner’s Guide to Monad](https://zhuanlan.zhihu.com/p/65449477)

The following is the definition of adjoint functors provided by Zhipu Qingyan AI:

The definition of adjoint functors involves a pair of functors and a special relationship between them. Specifically, given two categories C and D, if there is a pair of functors:

$$
L: \mathcal{C} \rightarrow \mathcal{D}
$$

$$
R: \mathcal{D} \rightarrow \mathcal{C}
$$

and for all objects c in C and d in D there exists a natural bijection

$$
\text{Hom}_{\mathcal{D}}(L(c), d) \cong \text{Hom}_{\mathcal{C}}(c, R(d))
$$

then L and R are adjoint functors, with L the left adjoint of R and R the right adjoint of L.

For example, the Nop platform’s report engine generates Excel from object data, while the import engine parses Excel into object data; they can be viewed as an adjoint pair between the Excel category and the object category.

Given any legal Excel in the Excel category (meeting certain specifications, but not fixed format), the import engine can automatically parse it into object data. That is, we do not write a special parser for each Excel; instead we implement a general Excel parser that can automatically parse every Excel in the Excel category. Thus, the import engine is not a mere function; it is a functor acting on every Excel file (object) in the Excel category. Similarly, for each object in the object category, we can automatically export an Excel file based on certain rules. Like exporting an object to JSON, the report engine does not require any extra information to export object data to Excel.

> - In the category of sets, functions are mappings from one set to another, and these mappings are morphisms in category theory. Each set is an object in this category. Thus, functions are morphisms from one object to another in the category of sets.
> - A functor is a mapping from one category to another; it maps objects to objects and morphisms to morphisms, preserving composition and the category’s structure. That is, given two composable morphisms f: A → B and g: B → C, in the target category F(g ∘ f) = F(g) ∘ F(f).
> - A functor’s action is global; it involves all objects and morphisms in the category, not just a single object or morphism. In other words, a functor is a higher-order description, while a function is lower-order; a single point and arrow in the higher-order description corresponds to a whole set of processing rules in the lower-order description.

An Excel parsed into object data by the import engine, and then exported back to Excel by the report engine, does not necessarily yield an Excel identical to the original—it is an Excel equivalent to the original in some sense (e.g., style information or business-irrelevant layout adjustments may have changed). In other words, the input Excel and output Excel satisfy a form of equivalence relation: Excel ≃ Export * Import(Excel).

Similarly, a JSON object exported to Excel by the report engine and then re-parsed by the import engine might have slight changes in some property values—for example, some values initially empty strings may become null after round-tripping, or some fields with default values may be missing in the result. But in some sense the object still satisfies an equivalence relation: Obj ≃ Import * Export (Obj)

Adjoint functors can be seen as a generalization of reversible operations, but this generalization is within the abstract framework of category theory, involving functors and morphism composition rather than the reversibility of a single operation. In the context of adjoint functors, L and R are not directly inverses of each other; instead, they “reverse” each other via natural isomorphisms. Specifically, L and R are, in some sense, mutual inverses: R maps images of L back to objects in C and L maps images of R back to D, but these mappings are “corrected” via natural isomorphisms.

## V. The Same Physical Change Can Correspond to Multiple Deltas

A Delta is a change defined in a model space that supports some Delta merge operation; different model spaces have different Delta forms. Corresponding to the same change in the physical world, the Delta definition is not unique. Projected into different representation spaces, we obtain different Delta operation results.

First, all structures can be represented in a general binary bit space—for example, function A stored in a file corresponds to binary data 10111..., while function B corresponds to 010010.... Abstractly, finding a Delta X that transforms function A into function B corresponds to solving A ⊕ X = B. Clearly, if ⊕ is defined as bitwise XOR, we can solve X = A ⊕ B.

```
 A ⊕ B = A ⊕ (A ⊕ X) = (A ⊕ A) ⊕ X = 0 ⊕ X = X
```

> In the proof we used the [zeroing law, associativity, and identity law](https://baike.baidu.com/item/%E5%BC%82%E6%88%96/10993677) of XOR.

Although we can always solve for function Delta in the bit space, that Delta has little business value (though it is valuable for binary-level compression software). We cannot intuitively understand it and we lack convenient means to manipulate it intuitively.

For the same function projected into the line-text space, the function’s Delta corresponds to the line-text diff result.

Since there are many candidate Delta structure spaces, which one is optimal? The answer can be analogized to coordinate system selection. The same physical reality can be described using innumerable coordinate systems, but for a particular problem there may be a special, tailored coordinate system—the intrinsic coordinate system in physics. In such a system, the description highlights the core physical meaning and simplifies related expressions. For example, phenomena on a sphere can be described in the general three-dimensional Cartesian coordinate system, but spherical coordinates often simplify matters.

Reversible Computation indicates that for a specific business domain, we can establish a dedicated DSL and use it to naturally build a domain coordinate system, then express Delta within the Delta structure space defined by that domain coordinate system. Because this coordinate system is tailored to the domain problem, it often achieves minimal Delta expression. For instance, a business-level change that adds a field would require many adjustments in a general-purpose language—frontend, backend, and database all modified. In a domain model description, the change may manifest as a local field-level change, and then the underlying engine framework automatically translates the domain description into executable logic.

## VI. Summary

The ontology of the world is unobservable. Physics seeks knowledge through the investigation of things; what we can perceive is only a ripple (Delta) excited upon the unfathomable world-ontology.

To understand the Delta concept deeply, start from the group definition in mathematics: closure, associativity, identity, and inverses. Among these, the inverse is particularly crucial. In software, functional programming has popularized the term Monad, which basically satisfies the first three “commandments” of the group structure (it’s a monoid), but lacks inverses.

> Satisfying closure and associativity yields a semigroup; adding an identity yields a monoid.

Reversible Computation explicitly points out the importance of the inverse concept in software construction. Combined with generative programming, it proposes a systematic technical route for implementing Delta computation:

```
App = Delta x-extends Generator<DSL>
```

Guided by Reversible Computation, we need to rethink the foundations of software construction and rebuild our understanding of underlying software structures based on the Delta concept. Within 5 to 10 years, we can expect a paradigm shift across the industry from totals to Deltas—I call it the Delta Revolution in intelligent software production.

<!-- SOURCE_MD5:4a5fa11b93db187fef4d00af9d41e036-->
