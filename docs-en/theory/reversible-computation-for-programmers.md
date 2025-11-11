
# A Theoretical Analysis of Reversible Computation for Programmers

The theory of Reversible Computation is the unifying software construction principle behind a series of Delta-based technical practices such as Docker, React, and Kustomize. Its content is relatively abstract, which leads to many misunderstandings among programmers, making it hard to grasp how this theory relates to software development and which practical problems it can solve.
In this article, I will explain the concepts of Delta and Delta merging using terms familiar to programmers and analyze why some common understandings are incorrect.

If you are unfamiliar with the theory of Reversible Computation, please read

[Reversible Computation: The Next-Generation Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026)

## I. Understanding Class Inheritance from the Perspective of Delta

First, Java’s class inheritance mechanism is a built-in technical means in the language for applying Delta corrections to existing logic. For example:

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

Many people find the concept of “reversible” hard to grasp—what is Delta, how exactly is it “reversed,” does it mean reverse execution? Here, “reversible” does not refer to runtime reverse execution; rather, it is a structural transformation performed at compile time. For instance, we can change a class’s behavior through inheritance without modifying the base class’s source code.

```javascript
CrudBizModel<?> crud = loadBizModel("NopAuthUser");
crud.save(entityData);
```

With the same CrudBizModel type, if the actual corresponding Java class differs, the business logic executed will also differ. Inheritance can be seen as supplementing Delta information to an existing base class.

1. B = A + Delta. Delta means adding information to A without modifying A itself, thereby transforming it into B.

2. “Reversible” means we can use Delta to delete structures that already exist in the base class.

Of course, Java itself does not support deleting methods from a base class via inheritance, but we can override such a method to an empty function and rely on the runtime JIT compiler to recognize this case and eliminate the call in the JIT-compiled result, ultimately achieving the effect of completely removing the base-class structure.

Another example: suppose we have already developed a bank core system, and during deployment at a particular bank the client demands customization—deleting some redundant fields on accounts while adding some custom fields needed by that bank’s business. If modifying the base product’s code is not allowed, we can adopt the following approach:

```Java
class BankAccountEx extends BankAccount{
   String refAccountId;

   public String getRefAccountId(){
      return refAccountId;
   }

   public void setRefAccountId(String refAccountId){
      this.refAccountId = refAccountId;
   }
}
```

We can add an extended account object that inherits from the original account object, thus gaining all the original account’s fields, and then introduce extended fields on the extended object. We then use the extended object in the ORM configuration:

```xml
  <entity name="bank.BankAccount" className="mybank.BankAccountEx">...</entity>
```

The above configuration keeps the original entity name unchanged while changing the Java entity class corresponding to the entity to BankAccountEx. If we previously created entity objects using:

```javascript
 BankAccount account = dao.newEntity();
 or
 BankAccount acount = ormTemplate.newEntity(BankAccount.class.getName());
```

then the actual entity object created is an instance of the extended class. Moreover, since the ORM engine knows the concrete implementation class corresponding to each entity name, all Account objects loaded via association syntax are also of the extended type.

```javascript
BankAccount parentAccount = account.getParent(); // parent returns a BankAccountEx type
```

Existing code that uses the BankAccount type does not need to change, while new code that uses the extended fields can cast account to BankAccountEx.

As for the requirement to delete fields, Java does not support deleting fields from a base class—so what do we do? In practice, we can achieve a “delete field” effect by customizing the ORM model.

```xml
<orm x:extends="super">
  <entity name="bank.BankAccount" className="mybank.BankAccountEx"  >
    <columns>
       <column name="refAccountId" code="REF_ACCOUNT_ID" sqlType="VARCHAR" length="20" />
       <column name="phone3" code="PHONE3" x:override="remove" />
    </columns>
  </entity>
</orm>
```

The root attribute `x:extends="super"` indicates that this file inherits from the ORM model file in the base product (omitting it would mean creating a new model and completely discarding the previous configuration). The field phone3 is marked with `x:override="remove"`, which means it is deleted from the base model.

If a field is deleted in the ORM model, the ORM engine will ignore the corresponding field on the Java entity class and will not generate create-table statements, insert statements, update statements, etc. Thus, in effect, the field is deleted.

> No matter how you operate on the deleted field phone3, you will not observe any change in the system. A quantity that cannot be observed and has no effect on the outside world can be considered nonexistent.

Furthermore, the GraphQL engine in the Nop platform automatically generates GraphQL type base classes from the ORM model. If a field is deleted in the ORM model, it is also automatically deleted in the GraphQL service, and no DataLoader is generated for it.

## Trait: Delta That Exists Independently

`class B extends A` supplements Delta information based on class A, but this Delta is attached to A—i.e., the Delta defined in B is implemented only for A and has no meaning if detached from A. Given this, some programmers may question the Reversible Computation theory’s requirement that Delta “exists independently.” Since Delta modifies the base, how can it possibly exist independently of the base?

With this question in mind, let’s look at one of Scala’s core innovations: the trait mechanism. An introduction can be found online, for example [Scala Trait Explained (Examples)](https://blog.csdn.net/Godfrey1/article/details/70316850).

```scala
trait HasRefId{
  var refAccountId:String = null;

  def getRefAccountId() = refAccountId;

  def setRefAccountId(accountId: String): Unit ={
    this.refAccountId = accountId;
  }
}

class BankAccountEx extends BankAccount with HasRefId{
}

class BankCardEx extends BankCard with HasRefId{
}
```

The trait HasRefId is essentially a Delta: it adds a refAccountId property to a base object. When declaring the BankAccountEx class, we only need to mix in this trait to automatically add the property on top of BankAccount.

It is particularly worth noting that HasRefId is independently compiled and managed. That is, even if BankAccount does not exist at compile time, the trait HasRefId still has its own business meaning and can be analyzed and stored. Moreover, the same trait can be applied to multiple different base objects and is not bound to any one base class. For example, BankCardEx mixes in the same HasRefId.

When programming, we can also write code against trait types without using any base-object information:

```scala
def myFunc(acc: HasRefId with HasUserId): Unit = {
    print(acc.getRefAccountId());
  }
```

The above function accepts a parameter acc that only needs to satisfy the structural requirements of two traits.

From a mathematical perspective, class inheritance corresponds to B > A, meaning B contains more than A, but the extra cannot be separated out. Scala traits correspond to B = A with C, where C is explicitly abstracted and can be applied to multiple base classes—for example, D = E with C. In this sense, we can certainly say that C exists independently of A or E.

Scala’s trait mechanism was later adopted and expanded by Rust, becoming one of the secret weapons behind its so-called zero-cost abstractions.

## DeltaJ: Delta with Deletion Semantics

From the perspective of Delta, Scala’s trait is functionally incomplete—it cannot implement the deletion of fields or functions. A German professor, Schaefer, realized the lack of deletion semantics in software engineering and proposed a Delta definition that includes deletion operations: the [DeltaJ language](https://deltaj.sourceforge.net/), along with the concept of Delta Oriented Programming.

![deltaj](https://pic1.zhimg.com/80/v2-0f302d143afd51877e4080a8dcd21480_720w.webp)

A detailed introduction can be found in [Delta Oriented Programming Through the Lens of Reversible Computation](https://zhuanlan.zhihu.com/p/377740576)

## Differences Between Delta Merging and Inheritance

The Reversible Computation theory introduces a Delta merge operator similar to inheritance but with essential differences.

Traditional programming theory emphasizes encapsulation, but Reversible Computation is evolution-oriented, and evolution inevitably breaks encapsulation. In Reversible Computation, encapsulation is less important, so Delta merging can include deletion semantics and treat the base model as a white-box structure rather than an unanalyzable black-box object. The degree to which Delta merging ultimately breaks encapsulation is constrained by the XDef metamodel, preventing it from exceeding the final formal constraints.

Inheritance yields a new class name, while the object structure associated with the original type does not change. In contrast, Delta customization designed under Reversible Computation directly modifies the model structure addressed by the model path and does not produce new model paths. For example, for the model file /bank/orm/app.orm.xml, we can add a file with the same subpath under the delta directory to override it and then use `x:extends="super"` within that file to inherit the original model. Everywhere that loads /bank/orm/app.orm.xml will, in fact, load the customized model.

```xml
<!-- /_delta/default/bank/orm/app.orm.xml -->
<orm x:extends="super">
  ...
</orm>
```

Since Delta customization does not change the model path, the conceptual networks built on model paths and object names do not get distorted or shifted by customization. This ensures customization is a completely localized operation. By contrast, with general object-oriented inheritance, if we cannot modify source code, it is impossible to locally replace a hardcoded base class name with a derived class name. This forces us to widen the override scope, such as overriding an entire function or replacing an entire page. In many cases, we cannot effectively control the impact scope of local requirement changes. We call this phenomenon “abstraction leakage.” Once abstraction leaks, the impact scope can keep expanding and may even lead to architectural collapse.

A third difference is that inheritance is defined on short-range associations. Structurally, object-oriented inheritance can be viewed as an override among Maps: each class is akin to a Map whose keys are property and method names. A Map is a typical short-range association with only one level of container-element relationship. Delta customization, however, is defined on tree structures, a typical long-range association: a parent node controls all recursively contained child nodes; if the parent is deleted, all recursively contained child nodes are deleted. Structurally, Delta customization can be seen as an override among trees: Tree = Tree x-extends Tree. In later sections, I will theoretically explain why tree structures have advantages over Map structures.

## II. Docker as an Instance of Reversible Computation

Reversible Computation points out that beyond Turing machine theory and lambda calculus, there exists a third intermediate path to Turing completeness, expressed by the formula:

```
App = Delta x-extends Generator<DSL>
```

- x-extends is a coined word that represents an extension of object-oriented extends. Some people mistakenly read it as “x minus extends,” which leads to confusion.

- `Generator<DSL>` uses a generic-like syntax to indicate that the Generator adopts techniques akin to [C++ template metaprogramming](https://zhuanlan.zhihu.com/p/137853957), treating the DSL as a data object at compile time, and dynamically generating the base class that Delta will override.

  > A complex, structured type declaration automatically becomes a DSL (Domain Specific Language) if execution semantics are further introduced. The Generator is like a template macro function: it accepts a DSL resembling a type definition and dynamically generates a base class at compile time.

The overall construction pattern of Docker images can be viewed as:

```
App = DockerBuild<DockerFile> overlay-fs BaseImage
```

DockerFile is a DSL, and the Docker image build tool is akin to a Generator. It interprets DSL statements such as apt install in DockerFile and dynamically expands them into a delta-like modification of the filesystem (creating, modifying, deleting files, etc.).

[OverlayFS](https://blog.csdn.net/qq_15770331/article/details/96702613) is a stacked filesystem. It depends on and is built on other filesystems (such as ext4fs and xfs). It does not directly participate in the partitioning of disk space; it simply “merges” different directories from underlying filesystems and presents them to the user—this is the union mount technique. OverlayFS searches for files first in the upper layer, then in the lower layer if not found. When listing all files in a directory, it merges files from both upper and lower directories to return a unified list. If we implemented a virtual filesystem similar to OverlayFS in Java, the code would resemble [DeltaResourceStore in the Nop platform](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStore.java). OverlayFS’s merging process is a standard tree-structured Delta merge. In particular, we can represent deleting a file or directory by adding a Whiteout file, so it meets the `x-extends` operator’s requirements.

## Docker Images vs Virtual Machine Incremental Backups

Some programmers misunderstand Docker as merely a lightweight virtualization packaging technology or a convenient application packaging tool—what does it have to do with Delta? Of course, from a usage perspective Docker indeed acts like a lightweight virtual machine—but the key question is: what technology enables it to be lightweight? And what fundamental advantages does Docker provide as a packaging tool compared to others?

Before Docker, virtual machine technology could implement incremental backups, but VM increments are defined in byte space. VM delta files, when detached from their base images, have no business meaning; they cannot be independently constructed or independently managed. Docker differs in that Docker images’ Delta is defined in filesystem space, where the minimal unit of Delta is a file, not a byte. For example, if we have a 10MB file and we add one byte to it, the image grows by 10MB because OverlayFS goes through a [copy up](https://blog.csdn.net/qq_15770331/article/details/96702613) process, copying the entire file from the lower layer to the upper layer before modifying it in the upper layer.

In the Delta space defined by Docker, “half of file A + half of file B” is not a valid Delta definition, and there is no Docker tool to construct such a delta image. All images contain complete files; their specialty is that they can include “negative files.” For example, an image can represent `(+A, -B)`, adding file A while deleting file B. The concept of modifying part of a file cannot be directly expressed; it is replaced with adding file A2, where A2 is the result file after modification.

Docker images are Deltas that exist independently of the base image. We can make an application image without downloading the base image at all. In fact, a Docker image is a tar file; if we open it with a zip tool, we see each layer corresponds to a directory. We can simply copy files into the respective directories, compute the hash, generate metadata, and then call tar to package it into an image file. In contrast, for VM byte-space incremental backup files, we lack suitable, stable, and reliable technical operational means targeting the VM’s byte space. But in filesystem space, all command-line programs that generate, transform, and delete files automatically become operators in this Delta space.

> In mathematics, different structural spaces permit different levels of operator richness. In a barren structural space like byte space, we lack powerful structural transformation tools.

## Docker Images vs Git Versions

Some may wonder whether Docker’s Delta is similar to Git’s. Indeed, Git is also a Delta management technology, but it manages Delta defined in the text-line space. From the perspective of Reversible Computation, the difference lies only in the Delta space they correspond to, which leads to different operators available in those spaces. The filesystem space corresponding to Docker has many available operators—every command-line tool automatically becomes a valid operator on this Delta space. In text-line space, arbitrary operations can easily ruin the source file’s syntax structure, failing compilation. Therefore, in Docker’s Delta space, we have many Generators that can produce Delta, while in Git’s Delta space all changes are made manually and not automatically generated by a program. For example, to add field A to a table, we manually modify source files instead of hoping for a Git-integrated tool to automatically modify the source. Interestingly, if we equip Git with a structured diff and merge tool—for instance, integrating the Nop platform’s Delta merging tooling—then Git’s target Delta space can be changed to the domain model space for DSL, rather than the text-line space. In the domain model space, transformations via the Delta merge operator ensure the resulting format is valid XML, and all attribute and node names are valid names defined in the XDef metamodel.

Let me emphasize again: whether the Delta concept is useful depends on which Delta model space it is defined in and how many useful Delta operations we can establish within that space. Deltas defined in a barren structural space have limited value; not all Deltas are born equal.

Some people suspect Delta is just adding a version number to a JSON and replacing the old version with the new one. The problem is not that simple. Delta processing must first define the space in which Delta exists. In the Nop platform, Delta is defined in the domain model space, not the file space. It is not about versioning the entire JSON file and replacing it wholesale; rather, within the file, we can customize each node and each attribute individually. That is, in the Nop platform’s Delta space, every smallest element is a concept stable at the business layer with domain semantics. Another difference is that, according to the XDSL specification, all domain models in the Nop platform support the compile-time metaprogramming mechanisms `x:gen-extends` and `x:post-extends`, allowing domain models to be generated at compile time and then Delta merged. Overall, this satisfies the computational paradigm `DSL = Delta x-extends Generator<DSL0>`. Clearly, general JSON-related technologies, including JSON Patch, do not have a built-in concept of Generators.

## III. Theoretical and Practical Significance of Delta Customization

Guided by the theory of Reversible Computation, the Nop platform achieves the following in practice: a complex banking core application can be customized for a specific bank without modifying the base product’s source code, by applying Delta customization to implement fully customized data structures, backend logic, and frontend interfaces.

From a software engineering perspective, Reversible Computation solves the coarse-grained, system-level software reuse problem—that is, we can reuse entire software systems without decomposing them into separate modules or components. Component technology has theoretical deficiencies: in “component reuse,” we reuse what’s identical, i.e., the common part between A and B, but the common part is smaller than either A or B, which directly prevents reuse granularity from scaling to macro levels. The larger a thing is, the harder it is to find something exactly identical to it.

Reversible Computation points out that X = A + B + C, Y = A + B + D = X + (-C + D) = X + Delta. With inverse elements introduced, any X and any Y can establish an operational relationship. Thus, without modifying X, we can reuse X by supplementing Delta. In other words, Reversible Computation extends the principle of software reuse from “reuse identical” to “reuse related.” Component reuse is based on whole-part composition. Reversible Computation further points out that, beyond composition, objects can establish more flexible transformation relationships.

Y = X + Delta. From the perspective of Reversible Computation, Y is obtained by supplementing Delta to X, and it may be smaller than X; it is not necessarily larger than X. For instance, Y = X + (-C) means deleting a C from X to obtain Y, which is strictly smaller than X.

The Software Engineering Institute at Carnegie Mellon University is a leading authority in software engineering. It proposed the [Software Product Line Engineering theory](https://resources.sei.cmu.edu/library/asset-view.cfm?assetid=513819), which states that the development of software engineering is a journey of increasing the degree of software reuse: from function-level reuse, object-level reuse, component-level reuse, and module-level reuse, ultimately achieving system-level reuse. Software product lines attempt to build a theoretical foundation for the industrialized production of software, enabling software products to be built as on production lines. However, they have not found a good technological approach to achieve system-level reuse at low cost. Traditional software product line construction uses mechanisms like C-language macro switches, which are costly to maintain. The theory of Reversible Computation provides a feasible technical route to implement software product line engineering goals. For a detailed analysis, see [Delta Oriented Programming Through the Lens of Reversible Computation](https://zhuanlan.zhihu.com/p/377740576)

To illustrate Delta customization specifically, I provide a sample project [nop-app-mall](https://gitee.com/canonical-entropy/nop-app-mall),
an introductory article [How to Implement Custom Development Without Modifying Base Product Source Code](https://zhuanlan.zhihu.com/p/628770810)
and a demo video on [Bilibili](https://www.bilibili.com/video/BV16L411z7cH/)

## Differences Between Delta Customization and Pluginization

Some programmers wonder: with traditional “orthogonal decomposition,” “modularization,” and “plugin systems” clustering related functionality into libraries or packages, can’t we also achieve reuse? What is special about Reversible Computation’s reuse? The difference lies in reuse granularity. Traditional reuse cannot achieve system-level reuse. Imagine a system with over 1,000 pages. A client asks to add button B and delete button C on page A. How would traditional reuse handle this? Add a runtime control switch for each button? Can we achieve the client’s requirement without modifying the page’s source code? If the base product later upgrades and adds a new shortcut interaction to the frontend, will our customized code automatically inherit the base product’s functionality, or must the programmer manually merge code?

Traditional extensibility depends on a reliable prediction of future change points. For example, in a plugin system, we must define where plugins are attached to extension points. But in reality, it is impossible to design every potentially extensible area into extension points. For instance, it is difficult to design a control variable switch for every property of every button. The lack of fine-grained switches easily enlarges the extensibility granularity. For example, a client only wants to change the display widget of a particular field on a certain page, yet we have to customize the entire page. A field-level customization becomes a page-level one due to the system’s lack of flexible customization capabilities.

After Kubernetes v1.14, Kustomize declarative configuration management was promoted to address similar extensibility issues. It can be viewed as an application instance of Reversible Computation. Based on Reversible Computation, we can easily see where Kustomize may be improved in the future. For details, see [Reversible Computation and Kustomize](https://zhuanlan.zhihu.com/p/64153956)

## The Relationship Between Delta and Data Delta Processing

The idea of Delta is not uncommon in data processing. For example:

1. The [LSM tree (Log-Structured-Merge-Tree)](https://zhuanlan.zhihu.com/p/181498475) uses hierarchical Delta management. Each query checks all levels and returns the aggregated Delta results. Compaction can be viewed as a Delta merge process.

2. The [Map-side Combiner optimization in MapReduce](https://blog.csdn.net/heiren_a/article/details/115480053) uses the associativity of operations to pre-merge Deltas, reducing the burden on the Reduce phase.

3. In [Event Sourcing](https://zhuanlan.zhihu.com/p/38968012), we record an object’s modification history and aggregate all Delta records to obtain the current state.

4. Today’s hot trend of streaming-batch unification, the [Stream Table Duality](https://developer.aliyun.com/article/667566), transforms table modifications into a Delta change data stream via binlog, while aggregating these Deltas yields the snapshot known as a dynamic table.

5. In data warehousing, Apache Doris has a built-in [Aggregate data model](https://doris.apache.org/zh-CN/docs/dev/data-table/data-model/) that performs Delta pre-merge during data import, greatly reducing computation during queries. Databricks even names its data lake technology [Delta Lake](https://learn.microsoft.com/zh-cn/azure/databricks/delta/), with storage-level support for incremental data processing.

6. Even in frontend programming, the Redux framework treats each action as a Delta change to the State and implements time travel by recording all these Deltas.

Programmers are now accustomed to the concept of immutable data, so changes on immutable data naturally become Deltas. But as I have pointed out before, data and functions are dual. Data can be seen as functionals (functions of functions) acting on functions. We likewise need to establish the concept of immutable logic. If we view code as a resource representation of logic, then we should also be able to apply Delta corrections to logical structures. Most programmers are not yet aware that logical structures, like data, can be manipulated by programs and adjusted via Delta. Although Lisp established the “code-as-data” design philosophy early on, it did not propose a systematic technical solution supporting reversible Delta operations.

In software practice, the concepts of Delta and Reversible Computation are increasingly applied. In 5 to 10 years, we can expect an industry-wide paradigm shift from full to Delta—I call it the Delta Revolution.

> Interestingly, in deep learning, concepts like reversible and residual connections are already standard theory, and each layer of a neural network can be viewed as a computation pattern of Y = F(X) + Delta.

## IV. Conceptual Clarifications in Reversible Computation

## What Is a Domain Model Coordinate System

When introducing Reversible Computation, I repeatedly mention the concept of a domain model coordinate system. The independent existence of Deltas implicitly requires the stable existence of domain coordinates. So what are domain coordinates? Programmers are familiar only with planar or three-dimensional coordinates and may find an abstract, mathematical notion of coordinates hard to understand. Below, I will explain in detail what the domain coordinates in Reversible Computation consist of and how their introduction changes our worldview.

First, in Reversible Computation, by “coordinates” we refer to the unique identifier used to access values. Any unique identifier that supports the following two operations can be considered a coordinate:

1. value = get(path)
2. set(path, value)

A coordinate system is one that assigns a unique coordinate to every value involved in the system.

Concretely, for the following XML structure, we can flatten it into a Map:

```xml
<entity name="MyEntity" table="MY_ENTITY">
  <columns>
     <column name="status" sqlType="VARCHAR" lenght="10" />
  </columns>
</entity>
```

Corresponding to:

```
{
  "/@name"： "MyEntity",
  "/@table": "MY_ENTITY",
  "/columns/column[@name='status']/@name": "status",
  "/columns/column[@name='status']/@sqlType": "VARCHAR"
  "/columns/column[@name='status']/@length": 10
}
```

Each attribute value has a unique XPath that can directly locate it. By calling get(rootNode, xpath) we can read the corresponding attribute’s value.
In databases like MongoDB that support JSON fields, a JSON object is essentially flattened into a similar Map structure for storage, enabling indexes on values within the JSON object. JSON uses JSON Path rather than XPath. Here, XPath is what we call the domain coordinate.

> The XPath specification allows matching multiple nodes, but in the Nop platform we only use XPath with unique locating functionality; for collection elements we only support locating children by unique key fields.

For the Map structure above, we can also write it as a multi-dimensional vector in shorthand:

```
['MyEntity','MY_ENTITY', 'status','VARCHAR',10]
```

We only need to remember that the first dimension of this vector corresponds to the value at `/@name`, the second dimension corresponds to the value at `/@table`, and so on.

> You can imagine that the coordinate system for all possible DSLs is an infinite-dimensional vector space. For example, a list can have arbitrarily many child elements added, which corresponds to infinitely many varying dimensions in the vector representation of the domain coordinate system.

If the DSL model object defines a domain semantic space, then each value in the DSL description is the value at some position in this semantic space, and the coordinate for that position is the XPath. Every part of the XPath is a concept meaningful within the domain. Since the entire XPath has explicit business meaning in the domain semantic space, we call it a domain coordinate, emphasizing that it is a coordinate representation with domain significance. In contrast, Git diff locates differences by “which file and which line,” a coordinate representation that has nothing to do with domain concepts; therefore, Git’s Delta space is not a domain semantic space, and its locator is not a domain coordinate.

In physics, when we assign a coordinate to every point in a phase space, we move from Newtonian particle-based worldview to a field-theory worldview. Subsequent developments in electrodynamics, relativity, and quantum mechanics all adopt the field-theory worldview. Simply put, under a field-theory worldview, the focus shifts from how individual objects interact to how the attributes of objects change at given coordinate points within an omnipresent coordinate system.

Based on the domain coordinate system concept, regardless of how business logic evolves, the DSL object that describes the business has a unique representation in the domain coordinate system. For example, suppose the initial representation is \['MyEntity','MY\_ENTITY', 'status','VARCHAR',10\], and it later evolves into \['MyEntity','MY\_ENTITY', 'status','VARCHAR',20\]. The coordinate corresponding to 20 is "/columns/column\[@name='status'\]/@length", indicating that the length value of the status field has been adjusted to 20.

When we need to customize an existing system, we only have to find the corresponding position in the domain model vector and directly modify its value. This is like finding a point on a plane by x-y coordinates and changing the value at that position. This customization does not depend on whether the system already has built-in extension interfaces or plugin systems. Since all business logic is defined in the domain coordinate system, all business logic changes are a Delta established on domain coordinates.

## Proof That Delta Merging Satisfies the Associative Law

In functional programming, there is a concept with a lofty origin: Monad. Understanding it is a hallmark of entering the realm of functional programming enthusiasts. From abstract mathematics, a Monad essentially corresponds to a monoid: a structure with an identity element that satisfies the associative law. The associative law means the grouping of operations does not affect the result:

```
  a + b + c = (a + b) + c = a + (b + c)
```

Using a plus sign to denote the operation is somewhat misleading because addition also satisfies commutativity (a + b = b + a), while general associative operations do not require commutativity. For example, function composition satisfies associativity, but f(g(x)) does not generally equal g(f(x)). To avoid misunderstanding, I will use the symbol ⊕ to denote the combining operation between two quantities.

> For more on Monad, see my article [A Beginner’s Guide to Monad](https://zhuanlan.zhihu.com/p/65449477). Some readers have commented that it is the most accessible introduction to the State Monad on the web.

First, we can prove: if each dimension of a vector satisfies associativity, then operations between entire vectors also satisfy associativity.

```
([A1, A2] ⊕ [B1,B2]) ⊕ [C1,C2] = [A1 ⊕  B1, A2 ⊕ B2] ⊕ [C1,C2]
                                = [(A1 ⊕ B1) ⊕ C1, (A2 ⊕ B2) ⊕ C2]
                                = [A1 ⊕ (B1 ⊕ C1), A2 ⊕ (B2 ⊕ C2)]
                                = [A1, A2] ⊕ ([B1, B2] ⊕ [C1,C2])
```

In light of the domain coordinate system defined in the previous section, to prove that Delta merging satisfies associativity, we only need to show that merging at a single coordinate satisfies associativity.

The simplest case is the commonly seen “overwrite update”: each operation uses the later value to overwrite the earlier one. We can choose a special value to represent deletion, thereby including deletion in overwrite updates. In databases, the BinLog mechanism uses this approach: each row modification produces a change record with the row’s latest values. Upon receiving the change record, we can discard the earlier values. Mathematically, this corresponds to A ⊕ B = B. Clearly:

```
 (A ⊕ B) ⊕ C  = B ⊕ C = C = B ⊕ C = A ⊕ (B ⊕ C)
```

Overwrite operations satisfy associativity.

Another slightly more complex combining operation resembles AOP: we can prepend and append content around a base structure.

```
 B = a super b,  C = c super d
```

B references the base structure via super, adding a before and b after.

 ```
  (A ⊕ B) ⊕ C = (a A b) ⊕ C = c a A b d = A ⊕ ( c a super b d) = A ⊕ (B ⊕ C)
 ```

This proves that Delta merge operations satisfy the associative law.

## How to Understand That Delta Is Independent

Some programmers struggle with the idea that Delta exists independently: can deletion operate independently of the base structure? If the base table lacks a certain field, wouldn’t deletion fail? If a Delta modifies a field type in a base table, can it exist independently of the base table? If applied to a table that doesn’t even have that field, wouldn’t it fail?

Such questions are normal because negative Deltas as inverse elements are hard to grasp. Even in science, negative numbers were recognized late. Leibniz, the inventor of calculus, complained in letters that the logical foundation of negative numbers was shaky. See [A Short History of Negative Numbers: Recognizing Negatives Was an Intellectual Leap](https://mp.weixin.qq.com/s?__biz=MzU0NDQzNDU1NQ==&mid=2247491026&idx=1&sn=59f777aaabb8a242cac192d4e914b058&chksm=fb7d6dc6cc0ae4d0d7d79282226b7aaac1466e9ff4b43ed45f3e51704e131a1eff94343de8a6&scene=27)

To understand this concept, we must first distinguish the abstract logical world from our real physical world. In the abstract logical world, we can accept the following definition as valid:

```
Table A (add field A, modify type of field B to VARCHAR, delete field C)
```

Even if Table A does not have field B or C, the definition remains valid. If we accept this, we can prove that applying any Delta to Table A yields a legitimate element in this space (in mathematics, this is called closure).

Without considering what fields Table A has, we can logically merge multiple Deltas that operate on Table A, for example:

```
Table A (add field A, modify type of field B to VARCHAR, delete field C) + Table A (_, modify type of field B to INTEGER, _) =
    Table A (add field A, modify type of field B to INTEGER, delete field C)
```

> This is somewhat akin to lazy evaluation in functional languages. In a functional language, range(0, Infinity).take(5).take(2) cannot execute the first step, but take(5) and take(2) can compose first and then apply to range(0, Infinity) to yield a finite result.

In a Delta space with an identity element, a full state can be viewed as a special case of Delta, e.g.:

```
Table A (field A, field B) = empty + Table A (add field A, add field B)
```

How do we resolve the practical difficulty that in reality we cannot delete field C from a table that does not have it? The answer is simple: we introduce an observation projection operator, stipulating that, when projecting from logical space to physical space, all non-existent fields are automatically removed. For example:

```
Table A (add field A, modify type of field B to VARCHAR, delete field C) ->  Table A (field A)
```

That is, if the operation is modify or delete but Table A lacks the corresponding field, the operation can be ignored.

This may sound abstract. In the Nop platform, the concrete approach is as follows:

```xml
<entity name="test.MyEntity">
  <columns>
    <column name="fieldB" sqlType="VARCHAR" x:virtual="true" />
    <column name="fieldC" x:override="remove" />
  </columns>
</entity>
```

The Nop platform’s Delta merge algorithm stipulates: after all Deltas are merged, inspect all nodes with `x:override="remove"` and automatically delete these nodes. Also inspect all nodes with `x:virtual="true"`; since merging automatically deletes the x:virtual attribute as soon as coverage onto a base node occurs, if `x:virtual` remains at the end, it indicates the merge never found a corresponding node in the base model, and these nodes are also automatically deleted.

> The space spanned by Delta operation results is very large—we can think of it as the space of all feasible operation outcomes. But the physical world we actually observe is only a projection of this feasible space. This perspective resembles the notion of wavefunction collapse in quantum mechanics: quantum state evolution happens in an abstract mathematical space, but physical facts we observe are post-collapse results. In mathematical space, Schrödinger’s cat can be in a superposition of dead and alive, but we observe only that the cat is either dead or alive.

The low-code platform NopPlatform, designed based on the theory of Reversible Computation, is open source:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Principles of Reversible Computation and Introduction/Q&A for the Nop Platform on Bilibili](https://www.bilibili.com/video/BV14u411T715/)

<!-- SOURCE_MD5:70d93cbfc6fcd6223f5db5c6eba657a2-->
