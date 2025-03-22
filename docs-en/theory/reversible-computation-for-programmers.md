# Analysis of Reverse Computing Theory for Programmers

Reverse computing theory is the underlying software construction rule behind a series of incremental-based technologies such as Docker, React, and Kustomize. Its theoretical content is relatively abstract, leading some programmers to have many misunderstandings about how this theory relates to programming in practice and what problems it can solve.

In this article, I will attempt to use concepts familiar to programmers to explain the concept of delta and delta merging, and analyze some common misunderstandings.

If you are not familiar with reverse computing theory, please first read the article

[Reverse Computing: The Next Generation Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026)

## 一. Understanding Class Inheritance from Delta Perspective

First, Java's class inheritance mechanism is a built-in technical means in Java language for modifying existing logic through delta changes. For example:

```java
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> {
    @Override
    protected void defaultPrepareSave(EntityData<NopAuthUser> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        user.setStatus(NopAuthConstants.USER_STATUS_ACTIVE);
        user.setDelFlag(DaoConstants.NO_VALUE);
    }
}
```

Many people find it difficult to understand the concept of reverse computing. What is a delta? How can we reverse it? Does it mean reversing execution at runtime? **Here, "reverse" does not refer to runtime reversal of execution but rather structural transformations performed during compilation**. For example, using class inheritance allows us to modify a class's behavior without modifying the base class's source code.

```javascript
CrudBizModel<?> crud = loadBizModel("NopAuthUser");
crud.save(entityData);
```

The same CrudBizModel type can correspond to different Java classes, resulting in different business logic executions. Inheritance can be seen as补充Delta信息到现有的基类中。

1. **B = A + Delta**: The delta refers to adding information to A without modifying it, transforming it into B.

2. **Reverse refers to using delta to delete existing structures in the base class**.

Of course, Java itself does not support deleting methods from the base class via inheritance, but we can hope that the JIT compiler can identify such situations during runtime compilation and remove calls to empty functions in the compiled output, thus achieving complete deletion of the base class structure.

Another example: suppose you have a bank core system and a client wants customized development. The client may want to remove certain redundant fields from the account while adding some inline business-related custom fields. If modifying the foundation product's code is not allowed, we can adopt the following approach:

```Java
class BankAccountEx extends BankAccount {
    String refAccountId;

    public String getRefAccountId() {
        return refAccountId;
    }

    public void setRefAccountId(String refAccountId) {
        this.refAccountId = refAccountId;
    }
}
```

We can create an extended account object that inherits from the original account object, thus retaining all fields of the original account. Then, we can add extension fields to the extended object and use it in ORM configurations:

```xml
  <entity name="bank.BankAccount" className="mybank.BankAccountEx">...</entity>
```

This configuration maintains the original entity name while changing the corresponding Java entity class to BankAccountExt. Thus, if you previously used methods like:

```javascript
 BankAccount account = dao.newEntity();
 或者
 BankAccount acount = ormTemplate.newEntity(BankAccount.class.getName());
```

Then, the actual objects we create are instances of extended class objects. Moreover, since the ORM engine internally knows the specific implementation class corresponding to each entity class name, all Account objects loaded via association object syntax are also of the extended type.

```javascript
BankAccount parentAccount = account.getParent(); // parent returns BankAccountEx type
```

The original code does not need to be modified if it uses the BankAccount type. However, new code that uses extended fields can cast the account object to BankAccountEx for usage purposes.

Regarding the deletion of a field in Java, since Java does not support deleting fields from the base class, how can we achieve this? In fact, we can implement a form of field deletion through a custom ORM model.

```xml
<orm x:extends="super">
  <entity name="bank.BankAccount" className="mybank.BankAccountEx">
    <columns>
       <column name="refAccountId" code="REF_ACCOUNT_ID" sqlType="VARCHAR" length="20" />
       <column name="phone3" code="PHONE3" x:override="remove" />
    </columns>
  </entity>
</orm>
```

The `x:extends="super"` at the root node indicates inheritance from the base product's ORM model file (if not specified, it creates a new model that discards previous configurations). The `phone3` field is marked with `x:override="remove"`, indicating the deletion of this field from the base model.

If a field is deleted in the ORM model, the ORM engine will ignore the corresponding field in the Java entity class and will not generate table creation statements, insert statements, update statements, etc., for it. In practical terms, this achieves the effect of deleting the field.

> Regardless of how phone3 is operated after deletion, we observe no changes in the system, and since this quantity cannot be observed or affect external interactions, we can consider it non-existent.

Furthermore, the Nop platform's GraphQL engine will automatically generate the base class for GraphQL types based on the ORM model. Therefore, if a field is deleted from the ORM model, it will also be automatically deleted in the GraphQL service, and no DataLoader will be generated for it.

## Trait: Independently Existing Delta Difference

`class B extends A` adds Delta information to class A, but this Delta is tied to A's existence. In other words, the Delta defined in B is only applicable to A, and without A, the Delta has no meaning. This raises questions among some programmers about "reversible computation theory," as the Delta seems to modify the base rather than existing independently.

With this question in mind, let's examine one of Scala's core innovations: the Trait mechanism. For example, see [Scala Trait Explanation with Examples](https://blog.csdn.net/Godfrey1/article/details/70316850).

```scala
trait HasRefId {
  var refAccountId: String = null;

  def getRefAccountId(): RefAccountId = refAccountId;

  def setRefAccountId(accountId: String): Unit = {
    this.refAccountId = accountId;
  }
}

class BankAccountEx extends BankAccount with HasRefId {}
class BankCardEx extends BankCard with HasRefId {}
```

The `HasRefId` trait acts as a Delta, adding a `refAccountId` property to the base object. When declaring the `BankAccountEx` class, it only needs to mix in this trait to automatically add the property to the `BankAccount` base class.

It is important to note that **`HasRefId` is independently compilable and managed**. This means that even if the BankAccount object is not present during compilation, the `HasRefId` trait still has its own business meaning and can be analyzed or stored. Moreover, the same trait can be applied to multiple different base objects and is not tied to a specific base class. For example, the `BankCardEx` class also mixes in the same `HasRefId`.

In programming, we can also perform operations based on the trait type without relying on any base object information.
```scala
def myFunc(acc: HasRefId with HasUserId): Unit = {
    print(acc.getRefAccountId());
}
```

The above function takes a parameter `acc` that must satisfy the requirements of two traits.

From a mathematical perspective, one can observe that class inheritance corresponds to `B > A`, indicating that B has more but the extra features cannot be independently extracted. However, Scala's trait mechanism effectively represents this as `B = A with C`, where the explicitly abstracted `C` can be applied to multiple base classes like `D = E with C`. In this sense, we can indeed state that `C` exists independently of `A` or `E`.

The trait mechanism in Scala later inspired Rust language and was further developed as a key component of so-called "zero-cost abstraction."

## DeltaJ: A Delta Difference with Deletion Semantics

From the perspective of Delta difference, Scala's trait functionality is incomplete because it cannot implement deletion of fields or functions. German professor Shaefer noticed the lack of deletion semantics in software engineering and proposed a Delta definition that includes deletion operations: [DeltaJ语言](https://deltaj.sourceforge.net/), along with the concept of Delta Oriented Programming.

![deltaj](https://pic1.zhimg.com/80/v2-0f302d143afd51877e4080a8dcd21480_720w.webp)

For detailed information, please refer to [Delta Oriented Programming from Reversible Computing Perspective](https://zhuanlan.zhihu.com/p/377740576).

## Differences Between Delta Merge and Inheritance

The Delta merge operator introduced by reversible computing theory resembles the concept of inheritance but has some fundamental differences.

Traditional programming theory emphasizes encapsulation, while reversible computing is designed for evolution. **Evolution necessarily breaks encapsulation**. In reversible computing theory, encapsulation is not as important, allowing Delta merge to have deletion semantics and treating the base model as a white-box structure rather than an impenetrable black box. The extent to which Delta merge destroys encapsulation is constrained by the XDef meta-model, preventing it from exceeding final form constraints.

Inheritance creates new class names while leaving the original type references unchanged. However, based on reversible computing theory, the Delta customization mechanism effectively modifies the model structure corresponding to the model path without creating new paths. For example, for a file at `/model/bank/orm/app.orm.xml`, we can add a file with the same subpath in the `delta` directory and use `x:extends="super"` within that file to inherit the original model. All references to `/model/bank/orm/app.orm.xml` will instead load the customized model.

```xml
<!-- /_delta/default/bank/orm/app.orm.xml -->
<orm x:extends="super">
  ...
</orm>
```

Since Delta customization does not alter model paths, all concept networks established based on model paths and object names remain unaltered. This ensures **customization is a completely localized operation**. It's evident that without modifying the original code, traditional object-oriented inheritance cannot locally replace the hard-coded base class name with a derived class name, leading to scenarios like function overloading or replacing entire pages. Many times, it's difficult to effectively control the impact range of local requirements changes, a phenomenon we've termed "abstract leakage." Once abstract leakage occurs, the impact range may expand indefinitely, potentially even causing architectural collapse.

The third difference between Delta customization and inheritance lies in **the fact that inheritance is defined on short-term associations**. Object-oriented inheritance can be viewed as a mapping at the structural level: each class is akin to a map where keys are property names and method names. A map is a typical short-term association with only container-element relationships at one level. However, **Delta customization is defined on tree-shaped structures**, which are a typical long-term association: parent nodes control all recursively included child nodes, and deleting a parent node deletes all its recursively included children. From a structural perspective, Delta customization can be viewed as a tree structure coverage: `Tree = Tree x-extends Tree`. In subsequent chapters, I will elaborate on the theoretical advantages of tree-shaped structures compared to map structures.

## II. Docker as an Example of Reversible Computing Theory

Reversible computing theory states that besides Turing machines and lambda calculus, there exists a third path leading to Turing completeness, which can be expressed with a formula:

```
App = Delta x-extends Generator<DSL>
```

* **x-extends** is a term that extends the traditional `extends` mechanism in object-oriented programming. Some might misinterpret it as "x minus extends," leading to considerable confusion.
* `Generator<DSL>` is a syntax similar to generic programming in C++ metaprogramming, which processes the DSL as a data object at compile time and dynamically generates Delta to cover the base class.

> A complex type declaration that introduces an execution semantics will automatically become a DSL (Domain Specific Language). Thus, Generator can be seen as a template macro function that accepts a type-like definition of the DSL and dynamically generates a base class at compile time.

The overall structure pattern of Docker images can be viewed as

```
App = DockerBuild<DockerFile> overlay-fs BaseImage
```

Here, `DockerFile` is a DSL language, while the Docker build tool acts as a generator that interprets the `apt install` and other DSL statements in `DockerFile`, dynamically expanding them into modifications to the file system (e.g., creating, modifying, or deleting files). 

[OverlayFS](https://blog.csdn.net/qq_15770331/article/details/96702613) is a **layered filesystem** that depends on and builds upon other file systems (such as `ext4fs` and `xfs`). It does not directly partition disk space but instead merges different directories from the underlying file system, presenting them to users as a union mount. When searching for files, OverlayFS first looks in the upper layer and only falls back to the lower layer if the file is not found. For directory listing, it combines both layers' directories into a unified result. Implementing a similar OverlayFS virtual file system in Java would resemble the `DeltaResourceStore` from the [Nop platform](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStore.java). The merging process in OverlayFS is a standard tree-based delta merge, especially since it supports deletion by introducing a "whiteout" file, aligning with the `x-extends` operator.

## Comparison of Docker Images and Virtual Machine Incremental Backups

Some developers misunderstand Docker as merely a lightweight virtualization wrapper or an application packaging tool. How does Delta differ? From a usage perspective, Docker behaves like a lightweight virtual machine. However, what technology enables this lightweight implementation? And how does Docker as a packaging tool differ fundamentally from others?

Before Docker, virtual machines could already implement incremental backups. However, these increments were defined in terms of byte-level changes. Incremental files lose context once separated from the base image and cannot be managed or constructed independently. In contrast, Docker defines Delta differences at the file system level, with the smallest unit of change being a file rather than individual bytes.

For example, if you have a 10M file and add a single byte, the resulting image increases by 10M because OverlayFS performs a [copy up](https://blog.csdn.net/qq_15770331/article/details/96702613) operation: it copies the entire lower layer file to the upper layer before making modifications.

> In Docker's delta space, half of file A plus half of file B is not a valid delta definition. Nor can such a difference be constructed using Docker tools. All images contain complete files, with their uniqueness lying in certain **negative files**. For instance, an image might represent `(+A,-B)`, indicating the addition of file A and deletion of file B. However, modifying specific parts of a file is not directly expressible; instead, it results in a new file (e.g., A2), which reflects the modified outcome.

> Docker deltas are independent of base images, allowing you to create application images without downloading the base image. In reality, Docker images are tar files. Unzipping them reveals each layer as a directory. By copying files into their corresponding directories and computing hash codes, you can generate metadata, then use `tar` to create a new image file. Compared to virtual machine byte-level incrementals, we lack suitable, reliable tools for the file system space.

In contrast, all operations (file creation, modification, deletion) are automatic Delta transformations in the file system space.
## Docker镜像与Git版本的对比

Some classmates may wonder if Docker's approach is similar to Git? Indeed, **Git is also a kind of incremental management technology**, but it manages differences at the line level within text files. From the perspective of reversible computing, these two technologies differ only in terms of their corresponding incremental spaces, which in turn lead to different operators (Operators) existing within each space. Docker corresponds to a file system space that has an abundance of available operators. Each command-line tool automatically becomes a legitimate operation within this incremental space. In contrast, if we perform random operations within the text line space, it's easy to result in source code files with mixed-up syntax structures, making them unable to be compiled successfully.

In Docker's incremental space, we have many generators that can generate Delta changes. However, all change operations in Git's incremental space are manually performed by humans, rather than being automatically generated by a program. For example, adding a field A to a table would require manual modifications to the source code files, not relying on tools integrated with Git for automatic modifications. An interesting point is that if we equip Git with a structured comparison and merging tool, such as integrating Nop's Delta merge tools, then Git's Delta space can be transformed into a domain model space instead of remaining within the text line space. **In the domain model space, Delta merge operations ensure that the resulting format is always valid XML, and all property names and node names are defined as legitimate names in the XDef meta-model files**.

Once again, it's crucial to emphasize that **the utility of the difference concept hinges on which incremental model space it is defined within and how many useful incremental operation relationships can be established in that space**. Defining differences within a sparse structure space offers limited value; the concept of difference does not inherently arise equal.

Some people may wonder if Delta difference simply involves adding a version number to a JSON file and replacing the old version with the new one? The question is more complex than that. Incremental processing first requires defining where the Delta resides. In the Nop platform, differences are defined within the domain model space, not within the file space. This means **we can perform individual Delta customization for each node and attribute within a JSON file**, i.e., in the Nop platform's incremental space, every minimal element is a conceptually stable business-level idea on the domain semantics level. Another key difference is that according to XDSL specifications, all domain models in the Nop platform support `x:gen-extends` and `x:post-extends` compile-time meta-programming mechanisms. These mechanisms allow dynamic generation of domain models during compilation and subsequent Delta merging. This overall satisfies the computational paradigm requirement of `DSL = Delta x-extends Generator<DSL0>`. Clearly, general JSON-related technologies, including JSON Patch, do not natively support the concept of generators.

## Three. The theoretical and practical significance of Delta customization

Guided by reversible computing theory, the Nop platform achieves the following in practice: **A complex core banking application can be customized without modifying the source code of the underlying product, enabling tailored development for specific banks to implement fully customized data structures, backend logic, and frontend interfaces**.

From a software engineering perspective, **reversible computing theory addresses the issue of coarse-grained system-level software reuse**, allowing entire software systems to be reused without decomposing them into separate modules or components. Component technology has theoretical shortcomings, as so-called component reuse is based on the idea that only identical components can be reused. However, we actually reuse the common parts between A and B, which are smaller than A and B themselves. This limits the granularity of component reuse to a relatively narrow scope. Reversible computing theory states X = A + B + C, Y = A + B + D = X + (-C + D) = X + Delta. By introducing inverses, any X and Y can establish an operation relationship without modifying X, enabling reuse of X by supplementing Delta information. In other words, **reversible computing extends the software reuse principle from "identical reuse" to "related reuse"**. Component reuse is based on the combination relationship between wholes and parts, while reversible computing indicates that more flexible transformation relationships can be established beyond mere combinations.

Y = X + Delta, viewed through the lens of reversible computing, **means Y is derived by supplementing Delta information to X**, but Y may be smaller than X rather than merely larger. This perspective differs fundamentally from component theory. Imagine Y = X + (-C), which represents removing C from X to get Y. The resulting Y is a smaller structure than X.

The Software Engineering Institute at Carnegie Mellon University is a reputable authority in the software engineering field. They introduced what is known as [Software Product Line Engineering Theory](https://resources.sei.cmu.edu/library/asset-view.cfm?assetid=513819), which outlines the evolution of software engineering as an incremental improvement in software reuse, starting from function-level reuse, moving through object-level, component-level, module-level reuse, and ultimately achieving system-level reuse. The Software Product Line Theory aims to establish a theoretical foundation for software industrialization, enabling the continuous generation of software products similar to how assembly lines operate in manufacturing. However, it has not yet found an effective technical approach to achieve system-level reuse at a low cost.

The traditional method of constructing software product lines requires mechanisms similar to those in C-language macros, resulting in high maintenance costs. The invertible computing theory essentially provides a feasible technical path for realizing the technical objectives of software product line engineering. For specific details, please refer to [From Invertible Computing to Delta-Oriented Programming](https://zhuanlan.zhihu.com/p/377740576).

To illustrate how Delta customization is implemented, I provided an example project, [nop-app-mall](https://gitee.com/canonical-entropy/nop-app-mall), along with an explanatory article [How to Implement Customized Development Without Modifying the Source Code of the Base Product](https://zhuanlan.zhihu.com/p/628770810) and a demo video on Bilibili ([https://www.bilibili.com/video/BV16L411z7cH/](https://www.bilibili.com/video/BV16L411z7cH/)).

## The Difference Between Delta Customization and Pluginization

Some programmers have wondered whether traditional "orthogonal decomposition," "modularity," and "plugin systems," which group related functionalities into a library or package, can also achieve reuse. What is the special significance of reuse in invertible computing? **The key difference lies in the granularity of reuse**. Traditional reuse methods cannot achieve system-level reuse. Imagine a system with over 1,000 pages. If a customer requests adding button B to page A and removing button C, how would traditional reuse techniques handle this? Would they require writing runtime controls for each button? And without modifying the source code of the corresponding base product page, can the customer's requirements be met? Furthermore, if the base product later undergoes an upgrade that adds a new shortcut operation in the front-end, would our customized code automatically inherit the functionality already implemented by the base product, or would it require manual code merging by developers?

**Traditional scalable technologies rely on our ability to predict future change points, such as defining where plugins can be attached in plugin systems.** However, in reality, we cannot design every possible extension point in a system. For example, it's difficult to create a control variable for each attribute of every button. The lack of fine-grained controls often forces the granularity of scalability to expand due to technical limitations, such that even a simple field-level customization requirement becomes a page-level customization problem.

K8s introduced a declarative configuration management feature called [Kustomize](https://kubernetes.io/docs/concepts/cluster-configuration/kustomize/) in version 1.14, aimed at addressing similar scalability issues. This technology can be viewed as an application of invertible computing theory and provides a clear direction for future improvements in Kustomize technology. For detailed analysis, please refer to [From Invertible Computing to Kustomize](https://zhuanlan.zhihu.com/p/64153956).

## The Relationship Between Delta Differences and Data Differences

The Delta difference concept is not uncommon in data processing domains.

1. **In the Data Storage Domain**: The [LSM-tree](https://zhuanlan.zhihu.com/p/181498475) uses a layered approach for delta management, where each query checks all layers and returns merged delta results to the caller. The LSM-tree's compression operation can be seen as a process of merging Delta data.

2. **In MapReduce Algorithm**: The [Map-side Combiner optimization](https://blog.csdn.net/heiren_a/article/details/115480053) leverages the associativity of operations to pre-merge Delta differences, reducing the load on the Reduce phase.

3. **Event Sourc ing Architecture**: This pattern records modification histories for specific objects and uses Aggregate aggregation operations to merge all Delta modification records when querying the current state data, producing the final result.

4. **In the Big Data Domain**: The currently popular [Stream Table Duality](https://developer.aliyun.com/article/667566) concept involves using binlogs to track table modifications as Delta change streams and then merging these Deltas to create dynamic tables that represent the current state.
5. The Apache Doris system for data warehouses implements what is known as the [Aggregate Data Model](https://doris.apache.org/zh-CN/docs/dev/data-table/data-model/). During data import, it performs delta incremental merging calculations to pre-consolidate the data, thereby significantly reducing the computational load during queries. In contrast, DataBricks company directly named its core data lake technology as [Delta Lake](https://learn.microsoft.com/zh-cn/azure/databricks/delta/), supporting incremental data processing natively in the storage layer.

6. Even in the frontend programming domain, the Redux framework treats each action as an incremental change to the state. By recording all these deltas, it enables time travel functionality.

Since programmers have become accustomed to the concept of immutable data, any changes made to immutable data naturally become deltas. However, as I mentioned in a previous article, data and functions are inversely related—data can be seen as a higher-order function acting on functions. **We similarly need to establish the concept of immutable logic**. If we view code as a resourceized representation of logic, then we should also be able to perform delta corrections on logical structures. **Most programmers today are unaware that logic structures, just like data, can be manipulated by programs and adjusted using deltas**. While Lisp language established the "code as data" design principle decades ago, it has not yet proposed a systematic approach to support reversible incremental operations.

In software practice, concepts such as Delta, incremental changes, and reversibility are increasingly being applied. **Within the next 5 to 10 years, we can expect the industry to undergo a paradigm shift from full data to delta-based approaches, which I would refer to as the Delta Revolution**.

> Interestingly, in the deep learning field, concepts like reversibility and residual connections have become standard theoretical components. Each layer of neural networks can be viewed as follows: Y = F(X) + Delta.

## Four. Analysis of the Concept of Domain Model Coordinate System

When introducing the concept of reversible computing theory, I repeatedly mention the domain model coordinate system. This implies an implicit requirement for the stable existence of domain coordinates. What exactly is a domain coordinate? Programmers typically encounter only plane coordinates and three-dimensional coordinates in their work. They may find it challenging to understand the abstract, mathematical concept of coordinates. In the following sections, I will provide a detailed explanation of what domain coordinates entail in reversible computing theory and the impact this concept has on our worldview.

First, within reversible computing theory, when we refer to "coordinates," we are talking about a unique identifier used during value access operations:

1. value = get(path)
2. set(path, value)

A **coordinate system** is defined as a system where every value in the system is assigned a unique coordinate. Specifically, for the following XML structure:

```xml
<entity name="MyEntity" table="MY_ENTITY">
  <columns>
     <column name="status" sqlType="VARCHAR" lenght="10" />
  </columns>
</entity>
```

This can be expanded into a map-like structure:

```json
{
  "/@name": "MyEntity",
  "/@table": "MY_ENTITY",
  "/columns/column[@name='status']/@name": "status",
  "/columns/column[@name='status']/@sqlType": "VARCHAR",
  "/columns/column[@name='status']/@length": 10
}
```

Each attribute value has a unique XPath that directly locates it. By calling `get(rootNode, xpath)`, we can retrieve the corresponding attribute value. In databases like MangoDB that support JSON format fields, JSON objects are stored as expanded map structures to allow indexing of JSON values. The difference lies in that JSON Path is used for indexing instead of XPath here. Here's what we mean by XPath: it is capable of matching multiple nodes according to XPath specifications, but in the Nop platform, we only use XPath expressions with unique identification capabilities, and for elements within collections, we support locating sub-elements based on unique key fields.

The above map structure can also be simplified into a multi-dimensional vector form:

```json
['MyEntity', 'MY_ENTITY', 'status', 'VARCHAR', 10]
```

We simply need to remember that the first dimension corresponds to the value at `/@name`, the second dimension corresponds to `/@table`, and so on.
# Delta合并满足结合律的证明

In functional programming languages, there's a concept as noble as a Duke: **Monad**. Whether you understand this concept marks the transition from a casual enthusiast to a true functional programmer.

From an abstract mathematical perspective, Monad corresponds to the algebraic structure known as a **semigroup**, which possesses both a unit element and satisfies the **associativity property** (结合律). The associativity property is defined as follows:

```
  a + b + c = (a + b) + c = a + (b + c)
```

The use of addition in this example might be misleading because addition itself satisfies commutativity (a + b = b + a), but the general concept of combining two quantities doesn't require commutativity. For instance, function composition satisfies associativity but not necessarily commutativity (f(g(x)) is not always equal to g(f(x))). To avoid confusion, I'll use the symbol `⊕` to represent the combination operation.

> For a deeper understanding of Monads, refer to my article [《写给小白的Monad指北》](https://zhuanlan.zhihu.com/p/65449477). Some readers have reported that this article provides one of the most straightforward introductions to the **State Monad** online.

### Proof of Associativity

First, let's prove that if each dimension of a vector satisfies associativity, then the entire vector operation also satisfies it:

```
([A1, A2] ⊕ [B1,B2]) ⊕ [C1,C2]
                            = [A1 ⊕  B1, A2 ⊕ B2] ⊕ [C1,C2]
                            = [(A1 ⊕ B1) ⊕ C1, (A2 ⊕ B2) ⊕ C2]
                            = [A1 ⊕ (B1 ⊕ C1), A2 ⊕ (B2 ⊕ C2)]
                            = [A1, A2] ⊕ ([B1, B2] ⊕ [C1,C2])
```

### Special Case: Overwrite Update

The most common type of update is the **overwrite update**, where each operation overwrites the previous value with the new one. To handle deletions, we can represent them using a special value. If the new value represents a deletion, it will override any existing value, effectively deleting it. This approach is exemplified by the **Binary Log (BinLog)** mechanism in databases, where each modification of a database row generates an event record containing the row's most recent value. Once this record is received, the previous value can be discarded. Mathematically, this corresponds to:

```
A ⊕ B = B
```

This operation clearly satisfies the associativity property:
```
(A ⊕ B) ⊕ C = B ⊕ C = B (since C overwrites B)
A ⊕ (B ⊕ C) = A ⊕ B = B
```
Thus, both operations yield **B**, satisfying the associativity property.
```
(A ⊕ B) ⊕ C = B ⊕ C = C = B ⊕ C = A ⊕ (B ⊕ C)
```

The coverage operation satisfies the associativity property.

Another slightly more complex form of associative operation is similar to AOP operations. We can prepend and append additional content around the base structure.

```
B = a super b,  C = c super d
```

B references the base structure via `super`, prepending `a` before it and appending `b` after it.

```
(A ⊕ B) ⊕ C = (a A b) ⊕ C = c a A b d = A ⊕ ( c a super b d ) = A ⊕ (B ⊕ C)
```

This demonstrates that the Delta merge operation satisfies the associativity property.

## Understanding the Independence of Delta

Some developers are perpetually puzzled by the concept of Delta differences being independent. Is it possible for an operation to exist independently from the base structure? If the base table doesn't even have this field, deleting a non-existent field won't cause an error. If a Delta represents a modification to a specific field's type in the base table, can it exist independently? Applying such a Delta to a table that doesn't have this field would result in an error.

These questions are normal because, as inverses, negative Deltas are difficult to grasp. In the scientific world, even the understanding of negative numbers was a late development. Leibniz himself complained about the shaky logical foundation of negative numbers in his letters. See [A Brief History of Negatives: Acknowledging Negative Numbers Was a Leap of Thought](https://mp.weixin.qq.com/s?__biz=MzU0NDQzNDU1NQ==&mid=2247491026&idx=1&sn=59f777aaabb8a242cac192d4e914b058&chksm=fb7d6dc6cc0ae4d0d7d79282226b7aaac1466e9ff4b43ed45f3e51704e131a1eff94343de8a6&scene=27).

To understand this concept, we must distinguish between the abstract logical world and our physical reality. In the abstract logical world, the following definition is legally permissible:

```
Table A (add field A, modify field B's type to VARCHAR, delete field C)
```

Even if Table A doesn't have fields B and C, this definition remains valid. If we accept this, we can prove that applying any Delta operation on Table A will result in a valid element within this space (a property known as closure).

Under the condition of not considering what fields Table A actually has, we can merge multiple Deltas that operate on Table A, such as:

```
Table A (add field A, modify field B's type to VARCHAR, delete field C) + Table A (_, modify field B's type to INTEGER, _) =
    Table A (add field A, modify field B's type to INTEGER, delete field C)
```

> This approach is somewhat similar to lazy evaluation in functional programming languages. In such languages, `range(0, Infinity).take(5).take(2)` would immediately fail the first step, but in reality, `take(5)` and `take(2)` can be composed first, then applied to `range(0, Infinity)` to produce a finite result.

In a Delta space that has an identity element, the entire set can be viewed as a special case of Deltas, such as:

```
Table A (field A, field B) = empty + Table A (add field A, add field B)
```

How do we solve the problem in practice where we cannot delete a non-existent field C from a table that doesn't have it? The answer is straightforward: introduce an observation projection operator. When projecting from the logical space to the physical space, automatically delete all non-existent fields. For example:

```
Table A (add field A, modify field B's type to VARCHAR, delete field C) → Table A (field A)
```

This means that if the operation is a modification or deletion but the corresponding field doesn't exist in Table A, it can be directly ignored.

This explanation may seem abstract. Here's how it is implemented in the Nop platform:

```xml
<entity name="test.MyEntity">
  <columns>
    <column name="fieldB" sqlType="VARCHAR" x:virtual="true" />
    <column name="fieldC" x:override="remove" />
  </columns>
</entity>
```

The Delta merge algorithm in the Nop platform specifies that after all Deltas are merged, it automatically deletes any nodes with the `x:override="remove"` attribute. Additionally, it checks for any nodes with `x:virtual="true"` because during the merge process, if a base node is covered, the `x:virtual` attribute will be automatically deleted. If there are still `x:virtual` attributes remaining after the merge, it indicates that no corresponding node was found in the base model, and these nodes are also automatically deleted.

The space spanned by the results of differential operations is vast, and we can consider it as the result space produced by all feasible operations. However, the physical world we observe is merely a projection of this feasible space. This perspective resembles the concept of wave packet collapse in quantum mechanics: while quantum states evolve in an abstract mathematical space, all physical observations are results of wave packets collapsing into definite states. Thus, Schrödinger's cat can exist in a superposition of live and dead states within the abstract quantum space, but our physical observations only yield either the cat being dead or alive.

Based on the design of the reversible computing theory, the low-code platform **NopPlatform** has been open-sourced:

- [Gitee: canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- [GitHub: entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- [Development Example: docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible Computing Principles and Nop Platform Introduction & Q&A on Bilibili](https://www.bilibili.com/video/BV14u411T715/)

