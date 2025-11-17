# Designing Low-Code Platforms Through the Lens of Tensor Products

A fundamental problem in software design is scalability. A basic strategy for handling scalability is to treat new sources of variation as new dimensions and then examine the interactions between these dimensions and existing ones.

For example, suppose we have implemented an OrderProcess for the Order object. If we release it as a SaaS product, we need to add a tenant dimension. In the simplest case, the tenant is merely a filter field at the database layer, i.e., the tenant dimension is relatively independent and its introduction does not affect the specific business processing logic (tenant-related logic is independent of specific business processes and can be defined and addressed uniformly at the storage layer).

However, in more complex scenarios, each tenant may have customized business logic. In this case, the tenant dimension cannot remain independent and must necessarily interact with other business and technical dimensions. This article introduces a heuristic perspective that likens tenant-based extensions—and other common scalability issues—to the expansion of tensor spaces via tensor products. Combined with Reversible Computation theory, it provides a unified technical solution for such extensibility problems.

## I. Linear Systems and Vector Spaces

The simplest systems in mathematics are linear systems, which satisfy the principle of linear superposition:

$$
f(\lambda_1 v_1 + \lambda_2 v_2) = \lambda_1 f(v_1) + \lambda_2 f(v_2)
$$

We know that any vector can be decomposed into a linear combination of basis vectors:

$$
\mathbf v = \sum_i \lambda_i \mathbf e_i
$$

Therefore, linear functions acting on vector spaces are fundamentally simple in structure: they are completely determined by the function’s values on the basis vectors.

$$
f(\mathbf v) = \sum_i \lambda_i f(\mathbf e_i)
$$

As long as we know the function f’s values on all basis vectors, $f(\mathbf e_i)$, we can directly compute f’s value at any vector in the span of $\mathbf e_i$.

In the spirit of mathematics, if a mathematical property is desirable, we define the objects of study directly under that property as a premise (mathematical properties define mathematical objects, rather than objects merely possessing certain properties). Translating this into software framework design: if we deliberately require a framework to satisfy linear superposition, what should its design look like?

First, we need to re-examine the meaning of linear systems from a less mathematical perspective.

1. $f(\mathbf v)$ can be viewed as performing an operation on a parameter object with complex structure.

2. $\mathbf v = \sum_i$ volatile parameter × identifying parameter. Some parameters are relatively fixed and serve a distinctive identifying role, while others are volatile and vary from request to request.

3. f first acts on the identifying parameters (this action’s result can be predetermined), obtains a computation result, and then combines this result with the other parameters.

A concrete example: a frontend submits a request that triggers operations on a set of backend objects.

$$
request = \{ obj1：data1, obj2: data2, ... \}
$$

Rewriting in vector form:

$$
request = data1* \mathbf {obj1} + data2* \mathbf {obj2} + ...
$$

When we study all possible requests, we find that they form a vector space, and each objName corresponds to a basis vector in that space.

The backend framework’s processing logic corresponds to:

$$
\begin{aligned}
process(request) &= data1* route(\mathbf {obj1}) + data2* route(\mathbf {obj2}) + ...\\
&= route(\mathbf {obj1}).handle(data1) + route(\mathbf {obj2}).handle(data2) + ...
\end{aligned}
$$

The framework ignores the volatile data parameters and first acts on the object-name parameter; it routes to a specific handler based on the object name, then invokes that handler with the data parameter.

> Note that $\lambda_i f(\mathbf e_i)$ is essentially $\langle \lambda_i, f(\mathbf e_i)\rangle$: the combination of parameters with $f(\mathbf e_i)$ need not be simple scalar multiplication; it can be generalized to the result of an inner product operation. In code, this manifests as a function call.

## II. Tensor Products and Tensor Spaces

In mathematics, a basic question is how to automatically construct larger, more complex structures from smaller, simpler ones. The concept of the tensor product is a natural result of such automated constructions (this naturality has a precise definition in category theory).

First, consider a generalization of linear functions: multilinear functions.

$$
f(\lambda_1 u_1+\lambda_2 u_2,v) = \lambda_1 f(u_1,v) + \lambda_2 f(u_2,v) \\
f(u,\beta_1 v_1+ \beta_2 v_2) = \beta_1 f(u,v_1) + \beta_2 f(u,v_2)
$$

A linear function acting on a vector space can be regarded as a single-argument function: it receives a vector and produces a value. Analogously, multilinear functions have multiple parameters, each corresponding to a vector space (which can be seen as an independent dimension of variation). When fixing one parameter (e.g., fixing u and varying v, or fixing v and varying u), multilinear functions satisfy the linear superposition principle. Like linear functions, the values of multilinear functions are determined by their values on basis vectors:

$$
f(\sum_i \lambda_i \mathbf u_i,\sum_j \beta_j \mathbf v_j)= 
\sum_{ij} \lambda_i \beta_j f(\mathbf u_i,\mathbf v_j)
$$

$f(\mathbf u_i,\mathbf v_j)$ is essentially equivalent to passing in a tuple:

$$
f(\mathbf u_i, \mathbf v_j)\cong f(tuple(\mathbf u_i,\mathbf v_j)) \cong f(\mathbf u_i\otimes  \mathbf v_j )
$$

That is, we can forget that f is a multi-parameter function and view it as a single-parameter function receiving the complex parameter $\mathbf u_i \otimes \mathbf v_j$. Returning to the original multilinear function $f(\mathbf u,\mathbf v)$, we can now regard it, from a new perspective, as a linear function on a new vector space:

$$
f(\mathbf u\otimes \mathbf v)=\sum _{ij} \lambda_i \beta_j f(\mathbf u_i \otimes \mathbf v_j)
$$

$$
\mathbf u \otimes \mathbf v = (\sum_i \mathbf \lambda_i \mathbf u_i) 
\otimes (\sum_j \beta _j \mathbf v_j)  
= \sum _{ij} \lambda_i \beta_j \mathbf u_i \otimes \mathbf v_j
$$

> The f in $f(\mathbf u,\mathbf v)$ and $f(\mathbf u\otimes \mathbf v)$ is not literally the same function; they are only equivalent in a certain sense. We denote both by f for convenience.

$\mathbf u \otimes \mathbf v$ is the tensor product of vectors $\mathbf u$ and $\mathbf v$. It can be viewed as a vector in a new vector space—the tensor space—whose basis is $\mathbf u_i \otimes \mathbf v_j$.

If $\mathbf u \in U$ is an m-dimensional vector space and $\mathbf v \in V$ is an n-dimensional vector space, then the tensor space $U\otimes V$ contains all vectors of the form \\sum _i T_{ij} \\mathbf u\_i \\otimes \\mathbf v\_j. It corresponds to an $m\\times n$-dimensional vector space (it is also called the tensor product space of $U$ and $V).

> $U\otimes V$ is the space spanned by all tensor products $\mathbf u\otimes \mathbf v$; “spanned” here means linear span—the set of all linear combinations of these vectors. The elements in this space are more general than simple $\mathbf u \otimes \mathbf v$ forms; not every vector in the tensor space can be written as $\mathbf u \otimes \mathbf v$. For example:
> 
> $$
> \\begin{aligned}
> \\mathbf u\_1 \\otimes \\mathbf v\_1 + 4 \\mathbf u\_1 \\otimes \\mathbf v\_2
>
>  + 3 \\mathbf u\_2 \\otimes \\mathbf v\_1
>  + 6 \\mathbf u\_2 \\otimes \\mathbf v\_2
>    \&= (2\\mathbf u\_1 + 3 \\mathbf u\_2)\\otimes (\\mathbf v\_1
>  + 2 \\mathbf v\_2) \\
>    \&=
>    \\mathbf u \\otimes \\mathbf v
>    \\end{aligned}
>
> $$
> 
> But $2 \mathbf u_1 \otimes \mathbf v_1 + 3 \mathbf u_2 \otimes \mathbf v_2$ cannot be decomposed into the form $\mathbf u \otimes \mathbf v$ and must remain as a linear combination.
> 
> In physics, this corresponds to the notion of a quantum entangled state.

The tensor product is a “free” strategy (in the categorical sense) to construct complexity from simplicity. “Free” means the construction adds no new operational rules; it merely forms pairs by taking one element from each set.

> Essentially, in $\mathbf u \otimes \mathbf v$, $\mathbf u$ and $\mathbf v$ do not directly interact; the influence of $\mathbf v$ on $\mathbf u$ only manifests when an external function $f$ acts on $\mathbf u\otimes \mathbf v$. That is, we only detect $\mathbf v$’s effect when $f(\mathbf u \otimes \mathbf v) \ne f(\mathbf u)$.

Using the concept of tensor products, we can say—non-rigorously—that multilinear functions are equivalent to ordinary linear functions on tensor spaces. A slightly more rigorous formulation is:

For every multilinear function $\phi: U\times V\times W ...\rightarrow X$, there exists a unique linear function $\psi$ on the tensor space $U\otimes V\otimes W...$ such that $\phi(\mathbf u, \mathbf v,\mathbf w,...) = \psi(\mathbf u \otimes \mathbf v\otimes \mathbf w...)$.

In other words, any multilinear function acting on the product $U\times V\times W...$ can be factored into two mappings: first map to the tensor product, then apply a linear function on the tensor space.

In the previous section, we introduced linear systems and vector spaces and noted that software frameworks can emulate the operational behavior of linear systems. Combined with the notion of tensor products, we arrive at a general extensibility design: extend from receiving vector parameters to receiving tensor parameters—ever-growing variability can be absorbed via tensor products. For example,

$$
process(request) = data * route(\mathbf {objName} \otimes \mathbf {tenantId})
$$

Introducing the tenant concept may require changing the handling logic for all business objects in the system. But at the framework level, we only need to enhance the route function to accept the tensor product of objName and tenantId, then dynamically load the corresponding handler.

Thinking further, if we implement the software framework as a linear system, at its core it is a Loader function that takes a tensor product as its parameter.

Loader functions are ubiquitous in software systems, yet their significance is often underappreciated. Consider Node.js: every library function is, in form, loaded via require(path). When we call f(a), we essentially execute require("f").call(null, a). If we enhance require to allow dynamic loading based on additional identifying parameters, we can achieve function-level extensibility. The HMR mechanism used in Webpack and Vite can be seen as a reactive Loader: it monitors changes in dependency files, then re-bundles, reloads, and replaces the current function pointers.

Reversible Computation offers a new theoretical interpretation for Loader functions and provides a unified, general technical implementation. Later, I will outline the approach used in Nop Platform 2.0 (an open-source implementation of Reversible Computation) and its basic principles.

## III. Everything is Loader

> Programmer asks the function: Whence do you come, and whither do you go?
> 
> The function replies: Born of the Loader, returning to data.

The maxim of functional programming is “everything is a function.” But considering extensibility, that function cannot be immutable; in different scenarios, the actual function applied must differ. If the program’s basic structure is f(data), we can systematically refactor it to

loader("f")(data). Many framework and plugin designs can be re-examined from this perspective.

- IoC container:
  
  buildBeanContainer(beansFile).getBean(beanName, beanScope).methodA(data)
  
  $$
  Loader(beansFile\otimes beanName\otimes beanScope \otimes methodName)
$$

- Plugin system
  
  serviceLoader(extensionPoint).methodA(data)
  
  $$
  Loader(extensionPoint \otimes methodName)
$$

- Workflow:
  
  getWorkflow(wfName).getStep(stepName).getAction(actionName).invoke(data)
  
  $$
  Loader(wfName\otimes stepName \otimes actionName)
$$

Once we identify similar Loader structures across system layers, an interesting question arises: how internally consistent are these Loaders? Can they share code? Workflow engines, IoC engines, reporting engines, ORM engines—each needs to load its own model. Most currently operate independently. Can we abstract a system-level, unified Loader to handle model loading? If so, what common logic can this unified Loader implement?

A low-code platform’s design goal is to turn code logic into models; when models are stored in serialized form, they become model files. Visual design’s inputs and outputs are model files—visualization is merely a by-product of modeling. The most basic task of a unified low-code platform should be to centrally manage all models and turn all models into resources. The Loader mechanism is inevitably a core component in such a low-code platform.

Consider a commonly used function in daily development:

```java
JsonUtils.readJsonObject(String classPath, Class beanClass)
```

This is a general Java configuration object loader. It reads a JSON file from the classpath and deserializes it into a Java object of the specified type, after which we can use the object directly in code. If the configuration file format is incorrect—say, a field name is wrong or a data format is invalid—it can be detected during type conversion. With validator annotations such as @Max and @NotEmpty, we can even perform business-related validation during deserialization. Evidently, the loading and parsing of various model files can be viewed as variants of this function. Taking workflow model loading as an example:

```java
workflowModel = workflowLoader.getWorkflow(wfName);
```

Compared to raw JSON parsing, workflow model loaders generally provide the following enhancements:

1. They may load from a database rather than from a file under the classpath.

2. The model file format may be XML rather than JSON.

3. Model files can include executable script code, not just primitive types like string/boolean/number.

4. Model file validation is more stringent—for example, checking that attribute values fall within an enumerated set or satisfy specific formatting requirements.

Nop Platform 2.0 is an open-source implementation of Reversible Computation. It can be viewed as a low-code platform supporting domain-specific language (DSL) development. In Nop, a unified model loader is defined:

```java
interface IResourceComponentManager{
    IComponentModel loadComponent(String componentPath);
}
```

1. The model type can be inferred from the file suffix, so there is no need to pass componentClass-type information.

2. Model files use x:schema="xxx.xdef" to import the schema definitions they must satisfy, enabling more rigorous format and semantic validation than Java type constraints.

3. By adding field types such as expr, model files can directly define executable code blocks, which are automatically parsed into callable function objects.

4. Via a virtual file system, multiple storage options for model files are supported. For example, we can define a path format that points to model files stored in a database.

5. The loader automatically collects dependencies arising during model parsing and updates the parsing cache based on dependency changes.

6. With a FileWatcher, the system can proactively push updates to models when their dependencies change.

7. Delta decomposition and assembly of models are implemented via DeltaMerger and XDslExtender. This will be elaborated in Section V (it’s also a major technical difference between Nop and other platforms).

In Nop, all model files are loaded through the unified model loader, and all model objects are automatically generated from meta-model (Meta Model) definitions. Returning to the workflow example:

```java
getWorkflow(wfName).getStep(stepName).getAction(actionName).invoke(data)
```

getWorkflow is implemented via the unified component model loader—no custom code is needed. Likewise, getStep/getAction methods are auto-generated from meta-model definitions—again, no custom code is needed. Consequently, the Loader implementation is completely automated:

$$
Loader(wfName\otimes stepName \otimes actionName)
$$

From another angle, Loader parameters can be seen as a multidimensional coordinate (any information that uniquely locates something is a coordinate): each wfName corresponds to a virtual file path, and the path is the coordinate parameter used to locate within the virtual file system, while stepName/actionName are coordinate parameters used for unique location inside the model file. A Loader receives a coordinate and returns a value, so it can also be viewed as defining a coordinate system.

In a sense, Reversible Computation is precisely about establishing and maintaining such a coordinate system and studying the evolution and development of model objects within it.

## IV. Loader as Multiple Dispatch

Functions represent a certain static computation (code itself is deterministic), while a Loader provides a computation mechanism whose result is a returned function. Hence, a Loader is a higher-order function. If a Loader does more than simply locate an existing code block based on parameters—if it can dynamically generate function content based on its inputs—then a Loader can serve as an entry point for metaprogramming.

In programming language theory, multiple dispatch is a language-embedded metaprogramming mechanism widely used in Julia. Multiple dispatch and the Loader mechanism defined here share many similarities; indeed, a Loader can be seen as an extension of multiple dispatch beyond the type system.

Consider a function call f(a,b). In an object-oriented language, we might implement a as an instance of class A, define f as a member function on A, and pass b as its argument. The object-oriented invocation a.f(b) is single dispatch: it selects an implementation based on the run-time type of the first argument a (the this pointer) by consulting A’s virtual function table. That is,

```text
Under the OO view: f::A->(B->C)
```

> At the implementation level, a.f(b) corresponds to a function f(a,b), with a implicitly passed as the this pointer.

Multiple dispatch, by contrast, selects the “best-fitting” implementation based on the run-time types of all arguments:

```text
Under multiple dispatch: f:: A x B -> C, AxB is the tuple of A and B
```

> In Julia, the compiler can generate specialized code versions based on the types of arguments at the call site to optimize performance. For instance, f(int,int) and f(int,double) may result in two different binary versions.

From a vector-space viewpoint, we can regard different types as different basis vectors. For example, 3 corresponds to 3 int, and "a" corresponds to "a" string (analogous to $\lambda_i \mathbf e_i$). Values of different types are, in principle, disjoint; type mismatches forbid interaction (ignoring implicit conversions), just as different basis vectors are independent. In this sense, multiple dispatch f(3, "a") can be understood as $[3,"a"]\cdot Loader(int \otimes string)$.

Type information is descriptive metadata attached to data at compile time; fundamentally, it’s nothing special. In this light, a Loader can be viewed as a more general multiple dispatch mechanism acting on tensor products of arbitrary basis vectors.

## V. Loader as Generator

A general model loader can be given the following type:

```
    Loader :: Path -> Model
```

For a general design, we must recognize that coding is not just about current needs; it must also account for future changes and the system’s evolution in time and space. In other words, programming is not aimed at the current, unique world, but at all possible worlds. Formally, we can introduce a Possible operator to describe this:

```
    Loader :: Possible Path -> Possible Model
    Possible Path = stdPath + deltaPath
```

stdPath refers to the model file’s standard path, while deltaPath is the Delta customization path used to tailor an existing model file. For example, suppose our base product includes a business process main.wf.xml; when customizing for customer A, we need a different process but do not want to modify the base product’s code. We can add a Delta model file at `/_delta/a/main.wf.xml`, indicating the customer A-specific main.wf.xml. The Loader will automatically detect and use this file, and no changes are needed to existing business code.

If we merely want minor tweaks rather than a full replacement, we can use the x:extends mechanism to inherit the original model:

```java
Loader<Possible Path> = Loader<stdPath + deltaPath> 
                      = Loader<deltaPath> x-extends Loader<stdPath>
                      = DeltaModel x-extends Model
                      = Possible Model
```

In Nop, the model loader is implemented in two steps:

```java
interface IResource{
    String getStdPath(); // standard file path
    String getPath(); // actual file path
}

interface IVirtualFileSystem{
    IResource getResource(Strig stdPath);
}


interface IResourceParser{
    IComponentModel parseFromResource(IResource resource);
}
```

IVirtualFileSystem provides a Delta file system similar to Docker’s overlayfs, while IResourceParser parses a specific model file.

Reversible Computation proposes a general software construction formula:

```java
App = Delta x-extends Generator<DSL>
```

Building on this theory, we can regard the Loader as a special case of a Generator and treat Path as a minimal DSL. After loading a model object via the path, we can continue to apply the reversible construction formula to transform and Delta-revise the model, ultimately yielding the desired model object. For example:

Nop defines an ORM entity definition file, orm.xml, similar in role to Hibernate’s hbm files, with the rough format:

```xml
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <entities>
    <entity name="xxx" tableName="xxx">
       <column name="yyy" code="yyy" stdSqlType="VARCHAR" .../>
       ...
    </entity>
  </entities>
</orm>
```

Suppose we want to provide a visual designer for this model file. What needs to be done? In Nop, we merely add the following line:

```xml
<orm>
   <x:gen-extends>
      <orm-gen:GenFromExcel path="my.xlsx" xpl:lib="/nop/orm/xlib/orm-gen.xlib" />
   </x:gen-extends>
    ...
</orm>
```

x:gen-extends is XLang’s built-in metaprogramming mechanism, a compile-time code generator that can dynamically generate a model’s base class. `<orm-gen:GenFromExcel>` is a custom tag function that reads and parses an Excel model and generates an orm definition file according to orm.xml’s format. The Excel file format is shown below:

[excel-orm](excel-orm.png)

The Excel model file format is very close to the requirement-document formats we use daily (the example Excel file’s format was literally copied from a requirements document). Designing ORM entity models can be done by editing the Excel file, and such design changes take effect immediately! (With IResourceComponentManager’s dependency tracking, any change to the Excel model file triggers a recompilation of the orm model.)

Some may prefer a graphical designer like PowerDesigner over Excel. No problem! Just swap the metaprogramming generator—it’s literally a one-line change:

```xml
<orm>
   <x:gen-extends>
      <orm-gen:GenFromPdm path="my.pdm" xpl:lib="/nop/orm/xlib/orm-gen.xlib" />
   </x:gen-extends>
    ...
</orm>
```

Now you can happily design entity models in PowerDesigner.

This example encapsulates the notion of Representation Transformation in Reversible Computation. The truly important artifact is the core ORM model object; the visual designer merely uses one representation of it, and different representations can be reversibly transformed. Representations are not unique! Note also that representation transformation is entirely independent of runtime (the designer need not know anything about the ORM engine). It is a purely formal transformation (akin to a mathematical change of form). Many low-code platform designers today are inseparable from specific runtime support—this is an unnecessary limitation.

One more interesting question: To support `<orm-gen:GenFromExcel>`, do we need a dedicated Excel parser for the example format? In Nop, the answer is: no.

The orm model is essentially a tree-structured object. The constraints that this tree must satisfy are defined in orm.xdef. The Excel model is a visual representation of the orm model; it must also map to a tree structure. If this mapping can be described by deterministic rules, we can use a unified Excel parser to complete the model parsing:

```java
interface ExcelModelParser{
    XNode parseExcelModel(ExcelWorkbook wk, XDefinition xdefModel);
}
```

Thus, as long as an xdef meta-model file is defined, we can design model files using Excel. With xdef meta-models, parsing, decomposition, merging, Delta customization, IDE hints, breakpoints, and more are automatic—no extra programming needed.

Nop’s fundamental technical strategy is: xdef is the origin of the world. Once you have an xdef meta-model, you automatically gain both frontend and backend capabilities. If you’re not satisfied, Delta customization helps you fine-tune and improve.

> In the example Excel model file, the format is relatively free-form. You can arbitrarily add or delete rows and columns, as long as they can be converted to a tree structure in a “natural” way. In highbrow categorical terms, ExcelModelParser is not a function that maps a single Excel model object to a single tree model object; rather, it is a functor acting on the entire Excel category, mapping it to the Tree category (a functor acts on every object in a category and maps them to objects in the target category). Category theory solves problems in an “excessive” way: it solves every problem in a category and then declares the specific problem solved. If such an audacious approach succeeds, the only reason is: It’s science.

Finally, let’s re-emphasize the key points of Reversible Computation:

1. A full model is a special case of a Delta, so existing configuration files are already valid Delta descriptions. A Reversible Computation retrofit requires no changes to existing configuration files. For example, in Baidu’s amis framework, enabling Reversible Computation for amis JSON files in Nop simply switches the loading interface from JsonPageLoader to IResourceComponentManager; there is, in principle, no need to change the original configuration files or any application logic.

2. Before entering the strongly typed world, there is a unified weakly typed structural layer. Reversible Computation applies to any tree structure (including, but not limited to, JSON, YAML, XML, Vue). Reversible Computation is essentially a formal transformation problem that can be entirely independent of any runtime framework, serving as the upstream stage in a multi-phase compilation process. It provides foundational infrastructure for constructing, compiling, and transforming domain-specific languages and models. Using built-in merge and dynamic generation operations, domain models can be decomposed, merged, and abstracted in a general way. This mechanism applies to back-end Workflow and BizRule, front-end pages, AI models, distributed computation models, etc. The sole requirement is that these models be expressed in some structured tree form. Applying this to k8s is, in essence, consistent with kustomize. [Kustomize from the Perspective of Reversible Computation](https://zhuanlan.zhihu.com/p/64153956)

3. Any interface that loads data, objects, or structures by name—such as loader, resolver, require—can be an entry point for Reversible Computation. Although a path name appears to be the simplest atomic concept with no internal structure, Reversible Computation observes that any quantity is the result of a Delta computation and carries internal evolutionary dynamics. We needn’t treat a path name as a symbol pointing to a static object; we can treat it as a symbol pointing to a computation result—to a possible future world. Path -> Possible Path -> Possible Model

## Summary

A brief summary of the content introduced in this article:

1. Linear systems are good.

2. Multilinear systems can be reduced to linear systems.

3. The core of a linear system is Loader:: Path -> Model.

4. A Loader can be extended to Possible Path -> Possible Model; loading equals composition.

5. Reversible Computation provides a deeper theoretical explanation.

The low-code platform NopPlatform, designed based on Reversible Computation, is open-source:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development example: [docs-en/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs-en/tutorial/tutorial.md)
- [Principles of Reversible Computation and Introduction/Q&A for the Nop Platform_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
<!-- SOURCE_MD5:7da01370dd075b7ca5387e9aa837917b-->
