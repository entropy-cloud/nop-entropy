# Understanding the Difference Concepts for Programmers: Git and Docker as Examples

The reversible computation theory introduces a general software construction formula:

```
App = Delta x-extends Generator<DSL>
```

The Nop platform's implementation can be seen as a concrete realization of this formula. Here, we focus on the difference concept (Delta) within the context of Git and Docker.

> For detailed information, refer to [Reversible Computing: The Next Generation Software Construction Theory](https://mp.weixin.qq.com/s/CwCQgYqQZxYmlZcfXEWlgA).

Reversible computing can be viewed as a systematic theory for the difference concept. In this theory, all practices based on the difference concept in various domains can be explained within its framework.

> **Question:** What is Delta? Is each commit of Git considered a delta?
> 
> **Answer:** Git operates on the vector space of rows, making it unsuitable for domain-specific problems. Adjustments in the row space lead to merge conflicts, which are inherent to Git's design. Docker, on the other hand, operates on a structured file space.

The statement "similarly, there is Docker technology" seems confusing. Is there a fundamental difference between Git and Docker?

> **Question:** What is Delta? Is each commit of Git considered a delta?
> 
> **Answer:** Git defines Delta as changes in the row space, which can lead to instability in domain-specific applications. In contrast, Docker operates on a structured file space.

The confusion arises from the difference concept itself. While both Git and Docker involve Delta (difference) calculations, their implementations differ fundamentally. The fine distinction between these tools requires mathematical analysis.

> **Understanding Reversible Computing:**  
> - Reversible computing is a theoretical framework for the difference concept.
> - It applies to domain-specific problems but not directly to general data management.

For programmers unfamiliar with mathematical concepts, this can be challenging. However, many terms are misunderstood due to vague definitions rather than inherent complexity.

> **Join our discussion group** to ask questions and share insights. The QR code for our WeChat public account provides more details.

---

## 1. Universality and Existence of Difference

In mathematics and physics, when introducing a new concept, the first step is to prove its universality and existence.

For reversible computing, the first principle is:
**Difference is universally existent.**

```
A = 0 + A
```

Any system with a unit element can naturally define a difference. For example, in an empty operation scenario, the delta is equivalent to the identity operation. Combining with other operations results in the same operation.

---

## 2. Example: Delta in Git and Docker

### **Git's Delta**
- Defined over row vectors.
- Not suitable for domain-specific problems due to instability.

### **Docker's Delta**
- Defined over structured file metadata.
- Designed for virtual machine management.

```
[
  {
    "op": "add",
    "path": "/columns/-",
    "value": {
      "name": "newColumn",
      "type": "string",
      "primary": false
    }
  },
  {
    "op": "remove",
    "path": "/columns/2"
  }
]
```

This JSON structure is used to define differences in Docker, ensuring clear and efficient operations.

---

## 3. XML Example for Nop Platform

The Nop platform implements differences using XML:

```xml
<table name="my_table">
  <columns>
    <column name="newColumn" type="string" primary="false"/>
    <column name="column2" x:override="remove"/>
  </columns>
</table>
```

```markdown

## JSON Patch Example

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

The format of JSON Patch differs significantly from standard JSON and only allows marking list elements with indexes. In the Nop platform, rows are defined using unique attributes like `name` or `x:override`, ensuring semantic stability (inserting a row does not affect subsequent row positioning). Additionally, JSON Patch introduces an extra attribute (`x:override`) alongside standard JSON structure.

In the Nop platform, we have systematically implemented **quantity is a particular case of difference**. For areas like frontend modifications to backend data submissions or backend data changes to database updates, we consistently apply structured design. We use the term "convergence" for this approach, where submissions are treated similarly to standard domain structures with minimal extensions for add/update/delete operations.

However, recognizing **quantity is a particular case of difference** also allows us to dispel a common misconception: that differences are only minor adjustments. In reality, differences can encompass substantial changes within the system. When inverses exist, differences can even surpass the overall magnitude (**difference exceeds whole**).



Modern abstract mathematics introduces a critical realization: **operations are tied to specific spaces**. While most people learn math operations (like addition or multiplication) in number systems (integers, rationals, reals), they remain unaware that these operations only function effectively within their respective domains. This leads to a misunderstanding: operators like "+" and "*" are not universally applicable across mathematical structures.



- **Closure**: For all $a, b \in \mathbb{R}$, $a + b$ is also in $\mathbb{R}$.
- **Associativity**: For all $a, b, c \in \mathbb{R}$, $(a + b) + c = a + (b + c)$.
- **Identity Element**: There exists an element $0 \in \mathbb{R}$ such that for all $a \in \mathbb{R}$, $0 + a = a + 0 = a$.
- **Inverse Elements**: For every $a \in \mathbb{R}$, there exists an element $(-a) \in \mathbb{R}$ satisfying $a + (-a) = (-a) + a = 0$.

Thus, the real number system $\mathbb{R}$ forms a group under addition, known as the **addition group**.



> Note: The multiplicative group does not include zero because zero does not have a multiplicative inverse. Therefore, we are considering the set of non-zero real numbers $\mathbb{R}^*$.
   
   - **Closure**: For all $a, b \in \mathbb{R}^*$, the product $a \cdot b$ is also in $\mathbb{R}^*$.
   - **Associativity**: For all $a, b, c \in \mathbb{R}^*$, $(a \cdot b) \cdot c = a \cdot (b \cdot c)$.
   - **Identity Element**: There exists an element $1 \in \mathbb{R}^*$ such that for all $a \in \mathbb{R}^*$, $1 \cdot a = a \cdot 1 = a$. This element $1$ is the multiplicative identity.
   - **Inverse Element**: For each $a \in \mathbb{R}^*$, there exists an element $a^{-1} \in \mathbb{R}^*$ such that $a \cdot a^{-1} = a^{-1} \cdot a = 1$. This element $a^{-1}$ is the multiplicative inverse of $a$.
     Therefore, the set of non-zero real numbers $\mathbb{R}^*$ forms a group under multiplication, known as the multiplicative group.

The concept of difference in reversible computation is fundamentally inspired by the idea of groups. For every difference, we can always mimic the definition of a group from the following aspects:

1. In which space is the difference operation defined?
2. Is the result of the difference operation still within this space?
3. Does the difference operation satisfy associativity? Can we perform local operations first and then combine them into a global operation?
4. What is the identity element for the difference operation?
5. Does the difference operation support inverse operations? What is the form of the inverse?

## Group Structure in Git

### 1. Where is the difference operation defined in Git?

Git's `diff` functionality first splits the text file into lines and then compares these lines. Therefore, Git's difference structure space can be considered as the line-based text space. This is a general difference structure space. Each text file can be mapped to this line-based text space as a list of lines, meaning each text file has its own representation within this space.

### 2. Does the result of Git's difference operation remain within the line-based text space?

Git's `apply` functionality applies a patch file to the current text file and produces another "valid" text file. However, upon closer examination, this validity is not entirely reliable.

First, Git's patch has strong specificity; it is tightly coupled with its target context, such as from project A to project B. For example, a patch generated from project A cannot be directly applied to an unrelated project B. A patch is specifically tied to a particular version (state) of a base file. In this sense, we can hardly consider the patch as an independent entity in terms of concept.

Second, applying multiple patches to the same base file may result in conflicts. In such cases, the original structure of the source file will be altered by inserting specific markers:

- `<<<<<<< HEAD`: Marks the start of the current branch (usually your target branch, i.e., where HEAD points to).
- `=======`: Separates the current branch's content from the merged branch's content.
- `>>>>>>> [branch-name]`: Marks the start of the merged branch (usually the branch you are merging into).

**The fundamental cause of conflicts is that the difference operation in Git exceeds the defined structure space, resulting in an abnormal structure.**

Even if multiple patches do not conflict, the merged result may disrupt the syntax of the source file. To ensure that the merged result maintains valid syntax, we must perform the merge within the abstract syntax tree's structure space.

In the definition of a group, closure is undoubtedly the most fundamental property. While it might seem trivial to those without an abstract mathematics background, closure is crucial for maintaining the integrity of mathematical operations. If closure were not satisfied, the entire group structure would collapse like a house of cards, leaving us with nothing but empty set.

Associativity is next in importance. While addition and multiplication are commutative, they are also associative. However, not all operations are commutative or associative. For example, matrix multiplication is neither commutative nor fully associative. This is where the abstract syntax tree comes into play, allowing for structured and predictable parsing.

### 3. Does Git's difference operation satisfy associativity?

If we have multiple patches: patch1, patch2, patch3, ..., can these smaller patches be merged into a single larger patch? If they can be merged, does the order of merging matter? Is $(patch1 + patch2) + patch3$ equal to $patch1 + (patch2 + patch3)$?

If Git's difference operation satisfies associativity, it implies that we can perform local operations first and then combine them into a global operation. This is essential for maintaining consistency across the entire structure.

### 4. What is the identity element for Git's difference operation?

In Git, the `diff` command compares two versions of a file and shows the differences between them. The identity element in this context can be considered as the state before any changes are applied, which is typically represented by `<<<<<<< HEAD`.

### 5. Does Git's difference operation support inverse operations? What is the form of the inverse?

In Git, if you have applied a patch using `git apply`, you can remove it using `git reset`. This effectively undoes the difference operation. The inverse operation in this case would be to revert back to the previous state, which is represented by `>>>>>>> [branch-name]`.

```
git merge-diff patch1.patch,patch2.patch > combined.patch
```

But unfortunately, the above command does not exist. When merging multiple patches in Git, you must apply each patch individually to the base file and then generate a diff.

```
git apply --3way patch1.patch
git apply --3way patch2.patch
git diff > combined.patch
```

Associativity is one of the fundamental mathematical principles that applies universally. Most major mathematical theories inherently assume the existence of associativity (including the so-called most pure and generalized abstract algebra). In the world of mathematics, without associativity, progress would be nearly impossible.

> The only mathematical object I know of that does not satisfy associativity is the octonion (oceanion in some languages). The octonion is an extension of the quaternion (which is itself an extension of the complex number). The octonion currently has very few applications.

Why is associativity important? First, **associativity allows for localized understanding**. When associativity exists, we don't need to worry about the actual existence of the object's body, **we only need to focus on the objects that directly interact with it**, ensuring that the order in which they operate does not affect the final result. Once we understand how an object can combine with others, we effectively master its properties (as interpreted in Marxist philosophy, this corresponds to understanding all production relationships that the object is involved in).

Second, **associativity makes commutativity possible**. We can freely choose whether to associate neighboring objects from left or right, forming a complete and meaningful new element.

```
x = x + b + c = x + (b + c) = x + bc
y = y + b + c = y + bc
```

In the above example, during the construction of `x` and `y`, we can use commutativity to freely combine neighboring elements. However, **in Git's diff operations, patches lack this freedom**; you can only apply a patch to a specific version of the base file, making true associativity impossible.

```
... + a + b + c + ...
    == ... + (a + (b + c)) + ...
    == ... + ((a + b) + c) + ...
```

If a set of operations satisfies associativity, we can freely insert or delete parentheses in the sequence without changing the final result. This allows for significant performance improvements through parallel processing.

```function f(x){
    return g(h(x))
}
```

Function operations typically satisfy associativity, allowing compilers to directly analyze the code of `g` and `h` separately. This enables optimizations that can significantly improve performance.

```a + b + c + d = (a + b) + (c + d) = ab + cd
```

In the above example, we can simultaneously compute `a + b` and `c + d`, which is essential for many optimization algorithms.

### 4. What is the unitary and inverse in Git's diff operations?

The unitary in Git's diff operations is clearly an empty patch file; it represents no changes. Some may wonder why a unitary exists if it does nothing. First, understanding the uniqueness of the unitary: **once a unitary exists, it becomes omnipresent**.

$$
a \cdot b = e \cdot e \cdot a \cdot e \cdot b = a \cdot b
$$

Any object can be multiplied by any number of unitaries on either side. This seems to suggest that the unitary operates indirectly through multiplication.

$$
e = e \cdot a \cdot a^{-1} = a^{-1} \cdot a
$$

Once the unitary exists, it no longer represents an empty set but rather a space for indirect operations. The unitary's inverse is itself:

$$
e = e \cdot e^{-1} = e^{-1} \cdot e
$$

With the unitary in place, we move from an empty space to a universe of intermediate operations. The result is that most meaningful operations can now be represented through combinations of unitaries and other elements.

```acd \cdot d^{-1} \cdot c^{-1} \cdot b = ab
```

By applying the unitary appropriately, even complex expressions like `acd` can be simplified to something as simple as `ab`. This ability to transform arbitrary operations into simpler forms is a direct result of associativity.

The unitary's role in performance optimization cannot be overstated. By allowing operations to be reordered or grouped in ways that optimize cache utilization, associativity enables significant speed improvements.

> In the physical world we live in, within the quantum vacuum that human ingenuity can't even begin to fathom, everything appears empty and serene. Yet, beneath this calm surface lies a dynamic equilibrium between particles and antiparticles. If there's precisely one black hole nearby, its strong gravitational pull could lead to a random expansion and creation of particle-antiparticle pairs. Ultimately, one of them falls into the black hole's event horizon, while the other escapes from it. This is the famous Hawking radiation, or black hole evaporation.

In reversible computation theory, the introduction of inverse elements is key to achieving coarse-grained reversibility.

```
X = A + B + C
Y = A + B + D
  = A + B + C + (-C) + D
  = X + (-C + D) = X + Delta
```

Assuming \( X \) is composed of \( A + B + C \), we now want to produce \( Y \) composed of \( A + B + D \). If inverse elements and unitary elements exist, we can start from \( X \) under the condition that \( X \) is not decomposed. Through a series of differential operations, \( X \) can be transformed into \( Y \). By leveraging associativity, we can combine \( -C \) and \( D \) together to form a complete and independent Delta difference. From the perspective of reversible computation, the fundamental principle undergoes a significant transformation: from component-wise reversibility (same reusable components) to system-level reversibility (related but reusable components), enabling a transformation relationship between any \( Y \) and \( X \) through Delta, thus achieving reversibility while avoiding traditional composite relationships.

Inverse elements and unitary elements are essential for solving equations of this complexity.

```
A = B + C
-B + A = -B + B + C = 0 + C
C = -B + A
```

Solving equations allows for such reordering, which essentially means adding the inverse to both sides and then omitting the generated unitary elements.

Git can apply reverse operations, and it can also use patch commands in reverse order:

```
git apply -R original.diff

patch -R -o reversed.diff < original.diff
```

However, without associativity, Git's application of reverse patches becomes somewhat lacking in structure.

Based on this analysis, **while Git provides some form of differential operations, its mathematical properties make it unsatisfactory for large-scale automated handling, often requiring manual intervention.** In comparison, Docker's delta operations are perfect.

## Four. Docker's Differential Operations

### 1. Where is Docker's differential operation defined?

Docker relies on the OverlayFS filesystem as its core technology, which in turn depends on other underlying file systems (like ext4fs and xfs). OverlayFS does not directly partition the disk structure but instead **merges directories from different layers into a single unified view** for the user. This is achieved through union mounting, which first looks at the top layer before checking lower layers.

Docker images' Delta differences are defined in the file system space. The smallest unit of Delta is not a byte but a file. For example, if you have a 10M-byte file and add one byte to it, the Docker image increases by 10M bytes because OverlayFS undergoes a [copy up](https://blog.csdn.net/qq_15770331/article/details/96702613) operation: it copies the entire lower layer file (10M bytes) into the upper layer before making changes.

Some may think Git and Docker differ in that one uses linear lists while the other uses tree structures. However, this is not their fundamental difference. The real distinction lies in **Docker's Delta structure maintaining stable coordinates**: each file's full path is treated as a unique coordinate within the file system space. This stability ensures that local changes (like adding or removing a file) do not affect other files' coordinates. In contrast, Git uses line numbers as coordinates, so any added or removed lines will disrupt many other coordinates.

> From a philosophical perspective, Docker's structure is composed of two aspects: name and reality. Each file has its own name, represented by its full path, which can be used for unique positioning within the Delta structure. The actual meaning of this `name` lies in how it's combined with `real` elements during Delta operations. This `name` doesn't particularly matter to users; simply covering existing files with new ones suffices. From Git's perspective, line numbers are merely temporary names because they're constantly being added or removed, affecting the coordinates of adjacent lines. Thus, Git's coordinates are inherently unstable.

> In Docker's design, filenames can be used as unique identifiers within the Delta structure because each file's path is a stable coordinate. This ensures that operations like adding or removing a file won't destabilize other files' coordinates. However, in Git, line numbers serve as temporary identifiers because they're frequently changed, leading to frequent coordinate shifts among adjacent lines.

**Docker's coordinate system only handles files at the file level. If we want to perform unique identification and implement incremental updates within a file, how should we proceed?** The Nop platform uses an XDef meta-model in its domain model files, enabling precise location of any node within XML or JSON files. Additionally, the Nop platform includes an embedded virtual file system resembling OverlayFS, stacking multiple Delta layers into a unified DeltaFileSystem.

Some may wonder if a tree structure is necessarily the right choice. Not necessarily. For example, AOP technology uses a `package-class-method` three-tier structure rather than supporting arbitrary nesting in a tree structure. Similarly, in relational databases, we use a `table-row-column` three-tier structure. As long as the coordinate system remains stable, we can develop incremental update mechanisms based on it.

>**Tree Structure Advantages:**
> 1. **Unification of Relative and Absolute Coordinates:** Starting from the root node, any node has a unique path, which can be treated as its absolute coordinate. Within a subtree, each node also has a relative coordinate representing its position within that subtree. By combining the relative coordinates of a node with the absolute coordinates of the subtree's root, we can easily determine the node's absolute coordinate (just concatenate them).
> 
> 2. **Ease of Control:** Each parent node can act as a control node. It can propagate shared attributes and operations to its children, ensuring consistency across the subtree.

**Another key point about incremental structures:**
When an inverse exists, the incremental structure must be a combination of positive and negative elements, creating an expanded space rather than just a positive space. When we say "Docker's incremental operations are defined within the file system space," this implies that the file system must support negative files. Docker achieves this using a special convention with Whiteout files to represent deletions. Essentially, we transform transient actions like deletions into static, persistent, and manipulable objects (files or directories). In mathematical terms, it's akin to converting `A - B` into `A + (-B)`, a crucial yet often overlooked concept.

**"Incremental structure space is inherently an expanded space composed of positive and negative elements."**
This statement might sound abstract, but let’s illustrate it with an example. Suppose we're dealing with a multi-dimensional space:

```
X = [A, 0],
Y = [0, -B],
X + Y = [A, -B]
```

Here, X increases A in the first dimension and leaves the second unchanged, while Y decreases B in the second dimension. If we allow operations between X and Y, the result is a combination of `+A` and `-B`. If we only permit positive values in each dimension, this combination becomes an illegal operation. To ensure that incremental operations always succeed, we must extend the positive space to include both positive and negative elements.

For instance, in Nop's ORM model, the incremental structure is defined as a DSL (Domain Specific Language) within its metadata files:

```
X = increase table A(+字段1,+字段2)
Y = modify table A(+字段3)
Z = modify table A(-字段3,+字段4)
X + Y = increase table A(+字段1,+字段2,+字段3)
X + Y + Z = increase table A(+字段1,+字段2,+字段3,+字段4)
X + Z = ?
```

In this example:
- X represents an increase operation on table A, adding fields 1 and 2.
- Y adds field 3 to table A.
- Z decreases field 3 while adding field 4.
- Combining X and Y results in a table A with fields 1, 2, and 3 added.
- Combining X and Z results in fields 1, 2, 3 (added), and 4 (added). However, the exact behavior of Z depends on how negative operations are handled.

The final result of X + Y + Z is:

```
X + Y + Z = increase table A(+字段1,+字段2,+字段3,+字段4)
```

If we only allow positive fields in our database, combining X and Z would attempt to delete a field that wasn't added by X. This could lead to errors. To avoid this, we define the incremental structure as a DSL within Nop's metadata:

```
U = increase table A(+字段1,+字段2,-字段3,+字段4)
```

This allows us to express operations like `X + Z` as `U`, where:
- U increases fields 1 and 2.
- It decreases field 3.
- It increases field 4.

Thus, the result of `X + Z` is:

```
X + Z = U
```

If our database doesn't allow negative fields, we can project this abstract space back into a physical space using a projection operation:

```
P(U) = increase table A(+字段1,+字段2,+字段4)
```

This projection removes all negative operations, ensuring that only positive changes are reflected in the final database table.


In mathematics, this is a standard approach: adding illegal operations to the original space to form an extended space, making the operations legal within the extended space. For example, we must add complex numbers to the solution space so that quadratic equations have general solutions in the extended space, whereas they may have no real solutions in the original space.

$$
x^2 + 1 = 0 \implies x = ?
$$


### 2. Docker's Delta Operations and File System

Does Docker's delta operation still reside within the file system?

 Unlike Git, Docker's delta operations always produce valid merge results. First, **any two Delta layers can be freely merged**, unlike Git, which has strong restrictions on allowed deltas.

```
FROM registry.access.redhat.com/ubi8/ubi-minimal:8.8
WORKDIR /work/
RUN chown 1001 /work && chmod "g+rwX" /work && chown 1001:root /work
COPY --chown=1001:root target/*-runner /work/application

EXPOSE 8080
USER 1001

ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]
```



You can freely modify the FROM configuration in Dockerfile. When upgrading the system image, you do not need to modify the application's Dockerfile or any files; simply rebuild the image is sufficient. However, if you directly modify the Base image in Git, all patches based on the original Base image will become invalid.



After building the image into a container, the upper layer image binds to the lower layer image using a unique Hash code. This is a practical security measure in real-world applications. Conceptually, upper and lower images are independent. If the lower image changes, only the lower image needs to be rebuilt; the upper image remains unchanged.



The core rule for merging Docker images is to merge by name, which ensures deterministic, conflict-free merging. For example:

- If image A depends on image B, and both are available, Docker will automatically pull B when building A.
- If you modify image B, Docker will cache the changes so that building A remains efficient.



Docker's delta mechanism allows for efficient image construction by only fetching necessary changes from the source image. This is achieved through a unique Hash code system that tracks all dependencies and ensures consistency.



In mathematics, this is akin to defining a Generator function that maps one space to another. In programming terms, it's like having a function that transforms one data structure into another, ensuring compatibility and maintainability.



While Git relies on a diff-based approach, Docker uses a delta-based mechanism. This means:

- **Docker**: Any two Delta layers can be merged directly without complex conflict resolution.
- **Git**: Merging requires understanding the diff context and potentially resolving conflicts.



In mathematics, defining a Generator function allows for the creation of an extended space from any input. This is analogous to how Docker constructs images by applying deltas on top of base images.



Here, DSL refers to a Domain-Specific Language used to define how deltas are applied. In programming terms, this could be a domain-specific tool or framework that abstracts the delta application process.



App = Delta x-extends Generator<DSL>

Where:
- **App**: Application layer
- **Delta**: Delta operation
- **x-extends**: Extension mechanism
- **Generator<DSL>**: Domain-specific language generator



### 1. Domain-specific language (DSL) and Abstract Syntax Tree (AST)

The domain-specific language (DSL) implicitly defines a delta structure space. A programming language can describe all structures, forming an abstract syntax tree (AST). Unlike the file system, there is no unified AST across different programming languages. However, in the Nop platform, we do not directly use general-purpose languages to write business logic; instead, we heavily rely on DSLs.

For example:
- IoC containers use `beans.xml`
- ORM engines use `app.orm.xml`
- Logic engines use `task.xml`

These are all specialized DSLs for specific domains. In the Nop platform, all XDSLs (eXtended Domain Languages) use the XLang language's XDef meta-model to define syntax structures. This ensures that all DSLs using XDef support delta merging operations and automatically define a delta structure space.

In other words, while there is no unified AST across different languages, on the delta operation level, we can define a unified delta merge operation and implement it uniformly.



The Generator in reversible computing theory represents all possible structures within the delta structure space. It can be an independent code generator or a built-in compiler mechanism (e.g., macros). The Nop platform standardizes the compilation process for all DSLs with standardized syntax.

This means that we do not need to define meta-modeling syntax for each DSL; instead, we can reuse XLang's standardized syntax and libraries.



```xml
<workflow>
    <x:gen-extends>
        <oa:GenWorkflowFromDingFlow xpl:lib="/nop/wf/xlib/oa.xlib">
            {
                // Generated JSON by NopWorkflowDesigner
            }
        </oa:GenWorkflowFromDingFlow>
    </x:gen-extends>
    <!-- This comment will be merged with x:gen-extends-generated content -->
</workflow>
```



```java
// Automatically generated part by NopCodeGenerator
class NopAuthUser extends _NopAuthUser {
    // Manually added methods and properties mimic a Delta in the base class
    public boolean isAdminUser() {
        return getRoles().contains("admin");
    }
}
```




Git uses a layered structure similar to OverlayFS, combining multiple layers (branches, commits) into a single view.


Docker uses OverlayFS to merge multiple layers (base image, diffs), creating a unified filesystem.



In OverlayFS:
- `A ⊕ B = B`
- `(A ⊕ B) ⊕ C = C`

OverlayFS's delta operation is essentially a simple overlay operation that satisfies the associative property.

```plaintext
A ⊕ B = B
(A ⊕ B) ⊕ C = A ⊕ (B ⊕ C) = C
```


A Docker image is essentially a compressed tar archive. When you unpack it, it becomes a few directories and files within your filesystem. With just a handful of rules, you can skip over the `docker` application entirely and directly use the `tar` command to create a Docker image.

The independence of Docker images on the underlying OS allows for centralized management and distribution via tools like **Docker Hub**. This central repository hosts and manages these Delta snapshots, ensuring consistency across environments.


### 4. What is the Identity and Inverse Elements in Docker's Delta Operations?


#### 4.1 What is the Identity Element in Docker's Delta Space?

The identity element in Docker's delta space is what does nothing when you apply it to a file or directory. This is represented by `Delete` operation.



In group theory, each element has an inverse that "undoes" its effect. In Docker's delta space, this concept translates to having a way to revert changes made by a delta snapshot.

However, there's a subtle difference here. While every element in a group has an inverse (by definition), the `Delete` operation in Docker's delta space is designed to be idempotent:

$$
\text{Delete} \times \text{Delete} = \text{Delete}
$$

This means that applying `Delete` twice on the same file or directory is the same as applying it once.



Mathematically, idempotent elements (elements where \( a \times a = a \)) conflict with the group structure because groups require closure under multiplication. If an element is idempotent, it breaks the fundamental property of groups.



Let's assume we have an element \( a \) in our group that satisfies:

$$
a \times a = a
$$

This means applying \( a \) twice has no additional effect beyond the first application. If such an element exists, it violates the group axioms because groups require every element to be invertible and non-idempotent.



In practice, this means that if you have an operation in Docker's delta space that is idempotent, it cannot belong to a group structure. This limitation is by design, as groups are not naturally suited to handle such operations.



A **monoid** is the simplest non-trivial algebraic structure, consisting of a set with an associative binary operation and an identity element. While monoids are more general than groups (groups require inverses), they still lack the ability to handle idempotent operations.

The concept of **adjoint functors** extends this idea further, allowing for a more flexible relationship between two categories in category theory.



Take the `nop` platform as an example. Its report generator uses monoids to manage data transformations. Despite its name, it's not just about "no operations" but rather about a specific type of transformation that lacks inverses.



Adjoint functors are like a bridge between two worlds in category theory. They allow for the exchange of information between different structures, enabling complex interactions that would be impossible otherwise.



In the `nop` platform, data transformations are managed using monoids. This allows for consistent and predictable outcomes when applying these transformations, even in distributed systems.



While monoids provide a useful abstraction for certain types of operations, their limitations in handling inverses make them unsuitable for scenarios requiring full group structure. However, they still find applications in specific contexts like data transformation and management.

Remember, the key takeaway is that while groups are powerful, they have strict requirements that not all operations can satisfy. Monoids offer a middle ground but still lack some of the flexibility needed for complex real-world systems.

Given an arbitrary legal Excel（satisfying certain requirements but not fixed format），the import engine can automatically parse this Excel to obtain object data. In other words, instead of writing a specialized parser for each individual Excel, we developed a general-purpose Excel parser that can automatically parse any Excel within the Excel domain. Therefore, this import engine is not just an ordinary function; it is a functor operating on every specific Excel file within the Excel domain.

Similarly, for any object within the object domain, we can automatically generate an Excel file based on certain rules, analogous to exporting an object as JSON. When exporting object data to an Excel file via the report engine, no additional information is required.

> - In the set domain, a function is a mapping from one set to another. These mappings are referred to as homomorphisms in category theory. Each set within this domain is treated as an object.
> - A functor is a mapping from one category to another. It not only maps objects between categories but also preserves the structure of the categories, including the composition of morphisms. For example, if there are two consecutive homomorphisms f: A → B and g: B → C, then in the target category, F(g∘f) = F(g)∘F(f).
> - The role of a functor is global; it involves all objects and all morphisms within a category, not just individual objects or morphisms. In other words, functors are higher-order descriptions, while functions are first-order descriptions. Functors define relationships between categories at a higher level, mapping entire structures (including their morphisms) to corresponding structures in another category.
> - For example, if we have two consecutive homomorphisms f: A → B and g: B → C, then F(g∘f) = F(g)∘F(f).

An Excel file is imported into the import engine, which parses it into object data. The parsed Excel is then exported back to an Excel file via the report engine. The resulting Excel may not be identical to the original one because some style information or cell positions might have changed, depending on the business requirements.

Similarly, a JSON object is exported to Excel via the report engine and then imported back into another JSON object. Some property values might change slightly during this process (e.g., empty strings become null, or fields with default values might be lost). However, in general, there is an equivalence relation between the input Excel and output Excel: $Excel \simeq Export * Export(Excel)$.

Just like functors, morphisms can be extended to entire categories. For instance, a morphism L: C → D can not only map objects but also preserve the category structure, including the composition of morphisms. This is different from simple invertible operations; instead, it involves natural transformations that "reverse engineer" the original structure.

## Five. One physical change can correspond to multiple differences

A difference is defined on a model space that supports some kind of difference operation. Different model spaces may have different forms of differences.

> - In the set domain, functions are mappings between sets. These mappings are known as morphisms in category theory.
> - A difference operation $\oplus$ is defined on a vector space such that $a \oplus b = c$, where $c$ is the result of applying the operation to $a$ and $b$. The operation must satisfy certain properties, such as idempotence: $a \oplus a = 0$.
> - For example, in binary spaces, the difference operation $\oplus$ corresponds to the bitwise XOR operation.

First, all structures can be represented in a universal bit vector space. For instance, if function A is stored in file A, its corresponding binary representation might be 1011... While we want to transform it into function B's binary representation, which could be 0100.... In mathematical terms, we are solving the equation $A \oplus X = B$.

```
A ⊕ X = A ⊕ (A ⊕ X) = (A ⊕ A) ⊕ X = 0 ⊕ X = X
```

In the proof, we used properties of the XOR operation, including its commutativity and associativity.

Although in binary spaces we can always solve $A \oplus X = B$, the resulting difference $X$ may not have any business value. We cannot easily manipulate or visualize it like typical compression software might allow. We lack intuitive understanding and practical tools to work with it directly.

For functions, if we project them into a text space, their differences become meaningful. For example, two functions A and B can be compared as strings of text:

```
A = "functionA"
B = "functionB"
```

Their difference is $A \oplus B = ""$ in this case.

> - In the set domain, functions are mappings from one set to another. These mappings are known as morphisms in category theory.
> - A difference operation $\oplus$ is defined on a vector space such that $a \oplus b = c$, where $c$ is the result of applying the operation to $a$ and $b$. The operation must satisfy certain properties, such as idempotence: $a \oplus a = 0$.
> - For example, in binary spaces, the difference operation $\oplus$ corresponds to the bitwise XOR operation.

First, all structures can be represented in a universal bit vector space. For instance, if function A is stored in file A, its corresponding binary representation might be 1011... While we want to transform it into function B's binary representation, which could be 0100.... In mathematical terms, we are solving the equation $A \oplus X = B$.

```
A ⊕ X = A ⊕ (A ⊕ X) = (A ⊕ A) ⊕ X = 0 ⊕ X = X
```

In the proof, we used properties of the XOR operation, including its commutativity and associativity.

Although in binary spaces we can always solve $A \oplus X = B$, the resulting difference $X$ may not have any business value. We cannot easily manipulate or visualize it like typical compression software might allow. We lack intuitive understanding and practical tools to work with it directly.

For functions, if we project them into a text space, their differences become meaningful. For example, two functions A and B can be compared as strings of text:

```
A = "functionA"
B = "functionB"
```

Their difference is $A \oplus B = ""$ in this case.

**Since there are so many options for the difference structure space, which one is the optimal choice?** This question can be compared to the coordinate system selection problem. The same physical phenomenon can be described using numerous coordinate systems, but there may be a special, tailored coordinate system in physics called the intrinsic coordinate system that is custom-made for a specific problem. Descriptions established within this coordinate system can better highlight the core physical meaning and simplify related descriptions. For example, physical phenomena occurring on a sphere can indeed be described in a general three-dimensional Cartesian coordinate system, but using spherical coordinates often leads to simplification.

The reversible computation theory states that a specialized DSL (Domain-Specific Language) can be developed for specific business domains. Using this DSL naturally establishes a domain-specific coordinate system, which is tailored to address the specifics of the domain. This coordinate system is designed to minimize the expression of differences because it is specialized for domain-specific issues. For instance, if a change occurs at the business level and requires adding a new field, using a general language may necessitate adjustments across multiple areas, including frontend, backend, and database. However, employing a domain model can limit such changes to a local field-level modification, which can then be automatically translated by the underlying engine framework into executable logical functions.

## Summary

The ultimate reality of the world is unobservable. Physics seeks understanding through observation—only ripples on the surface of the deep, unknowable world. To deeply understand the concept of difference, one must start from mathematics and group theory: closure, associativity, identity, and inverse elements. Among these, the inverse element is a crucial concept. In software development, particularly in functional programming, the term Monad has been popularized. While it may satisfy some aspects of group theory (such as closure and associativity), it lacks the identity element.

A set that satisfies closure and associativity is called a semigroup. Adding an identity element to this makes it a monoid. However, the concept of inverse elements is missing here.

## Six. Conclusion

The essence of the world is unobservable. Physics seeks knowledge by observing ripples on the surface of an unknown, incomprehensible world.

To deeply understand the difference concept, one must delve into mathematics and group theory: closure, associativity, identity, and inverse elements. In the software realm, functional programming has popularized the Monad concept. While it may satisfy some aspects of group theory (closure and associativity), it lacks the identity element.

A set that satisfies closure and associativity is called a semigroup. Adding an identity element makes it a monoid. However, the concept of inverse elements is missing here.

## Six. Conclusion

The ultimate reality of the world is unobservable. Physics seeks understanding through observation—only ripples on the surface of the deep, unknowable world.

Reversible computation theory explicitly highlights the importance of the inverse concept in software construction and functional programming. It proposes a systematic approach to difference calculation:

```
App = Delta x-extends Generator<DSL>
```

Under the guidance of reversible computation theory, we must rethink the foundation of software construction, reconstructing our understanding of lower-level software structures based on the difference concept.

In the next five to ten years, we can expect the entire industry to undergo a paradigm shift from holistic to differential thinking. This represents a significant evolution in software intelligence, characterized by a focus on differences rather than similarities. The differential revolution in software development will likely dominate the industry landscape over the coming decade.

