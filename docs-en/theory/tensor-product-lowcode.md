# From Tensor to Low-Code Platform Design

In software design, one of the fundamental problems is scalability. A basic strategy for handling scalability is to treat new elements as new dimensions and analyze their interactions with existing dimensions.

For example, when dealing with the Order object in the OrderProcess logic, if this is to be released as a SaaS product, a tenant dimension must be added. In the simplest case, the tenant is just an additional filter field at the database level, allowing it to remain relatively independent of the business logic (tenant-related logic remains separate from specific business process handling).

However, for more complex scalability requirements, each tenant may need its own customized business logic. At this point, the tenant dimension can no longer remain independent; it must interact with other technical dimensions. This document will present an inspired view that compares this type of scalability problem to tensor space through tensor multiplication and combines reversible computation theory to provide a unified solution for such scalability issues.

## 1. Linear Systems and Vector Spaces

In mathematics, the simplest class of systems is the linear system, which follows the additive rule:

$$
f(\lambda_1 v_1 + \lambda_2 v_2) = \lambda_1 f(v_1) + \lambda_2 f(v_2)
$$

We know that any vector can be decomposed into a linear combination of basis vectors:

$$
\mathbf{v} = \sum_i \lambda_i \mathbf{e}_i
$$

Therefore, the action of a linear function on a vector space is inherently simple and is entirely determined by its values on the basis vectors:

$$
f(\mathbf{v}) = \sum_i \lambda_i f(\mathbf{e}_i)
$$

Knowing the values of $f$ on all basis vectors allows direct computation of $f$ for any vector in the space spanned by $\{\mathbf{e}_i\}$.

Following the spirit of mathematics, if a mathematical property is well-defined, we can define the corresponding mathematical object (mathematical properties define objects, not the other way around). In the software framework domain, if we actively require the framework to satisfy the additive rule, its design should reflect that.

From a less mathematical perspective, let's reconsider the meaning of a linear system:

1. $f(\mathbf{v})$ can be seen as an operation on a structured object.
2. $\mathbf{v} = \sum_i \lambda_i \mathbf{e}_i$ decomposes the vector into components.
3. Some parameters are fixed (like $\mathbf{e}_i$), while others vary with each request ($\lambda_i$).

For example, when processing a specific request:

$$
request = \{ obj1: data1, obj2: data2, ... \}
$$

Expressed as a vector:

$$
request = data1* \mathbf{obj1} + data2* \mathbf{obj2} + ...
$$

When studying all possible requests, we observe that all requests form a vector space, with each $objName$ corresponding to a basis vector.

The backend framework's logic corresponds to:

$$
process(request) = data1* route(\mathbf{obj1}) + data2* route(\mathbf{obj2}) + ...
$$

This translates to:

$$
process(request) = route(\mathbf{obj1}).handle(data1) + route(\mathbf{obj2}).handle(data2) + ...
$$

Here, we need to note that $\lambda_i f(\mathbf{e}_i)$ essentially becomes $\langle \lambda_i, f(\mathbf{e}_i)\rangle$, meaning the combination of $λ_i$ and $f(𝑒_𝑖)$ is not necessarily simple multiplication but can be seen as an inner product in software terms, leading to a function call.

## 2. Tensor Product and Tensor Space

In mathematics, one basic problem is how to construct complex structures from simpler ones. The tensor product ($\text{Tensor Product}$) provides a natural way to build larger mathematical structures from smaller ones. It is defined as follows:

Given two vector spaces $A$ and $B$, their tensor product $A \otimes B$ consists of all bilinear forms $u \otimes v$ where $u \in A$ and $v \in B$. The tensor product is a natural extension of the inner product, providing a way to construct more complex mathematical structures.

The process begins with smaller vector spaces and builds up through successive tensor products. For example:

1. $\mathbb{R} \otimes \mathbb{R}$ is the set of all bilinear forms on $\mathbb{R}^2$.
2. $\mathbb{R}^2 \otimes \mathbb{R}^2$ consists of all $2 \times 2$ matrices.
3. $\mathbb{R}^n \otimes \mathbb{R}^m$ corresponds to the space of $n \times m$ matrices.

The tensor product is perhaps the most natural way to construct composite mathematical structures and has precise definitions in various mathematical theories, ensuring that such constructions are well-defined and valid across different spaces.

Mathematically, if we have linear maps between vector spaces, their tensorization provides a way to lift these maps to higher-dimensional spaces. This process is fundamental in many areas of mathematics and theoretical physics, as it allows the construction of more complex structures from simpler ones while preserving certain properties.

Here’s how the process unfolds:

1. Start with basic spaces like $\mathbb{R}^n$.
2. Apply tensor products to combine these spaces into higher-dimensional structures.
3. Define linear maps on these combined spaces by "tensorizing" the original maps.

This approach is particularly useful in quantum mechanics, where operators and states are represented as tensors, allowing for a consistent framework to describe complex systems.

In software implementation, the tensor product translates to function composition and state aggregation. Each step of tensorization corresponds to combining smaller modules into larger ones, ensuring that their interactions are properly captured.

The key insight is that the tensor product provides a systematic way to handle compositions of functions and states, making it a powerful tool for constructing complex systems from simpler components.

## 3. Linear Systems and Tensor Products

A linear system can be seen as a special case of the tensor product where we combine vectors through addition and scalar multiplication. This perspective is particularly useful when dealing with data that can be represented as vectors.

For instance, consider a dataset consisting of multiple features (vectors). The goal is to find a function that can represent these features in a compressed form. Using linear systems:

1. Each feature vector $\mathbf{x}_i$ is associated with a scalar output $y_i$.
2. We aim to find coefficients $\lambda_j$ such that:
   $$
   y_i = \sum_j \lambda_j f(\mathbf{e}_j) + \epsilon
   $$
   where $\epsilon$ is the error term.

This setup mirrors the tensor product structure, allowing us to leverage its properties for efficient computation. Specifically, the coefficients $\lambda_j$ can be seen as weights in a neural network or as coefficients in a regression model.

By treating the problem as a linear system within the tensor product framework, we gain access to powerful optimization techniques and theoretical results that ensure sparsity, interpretability, and generalization.

# Multilinear Functions and Tensor Products

A **multilinear function** is a linear function acting on multiple vector spaces. It can be seen as an extension of a single-parameter (single-vector space) linear function to handle multiple parameters, each associated with its own vector space.

## Definition
Given two finite-dimensional vector spaces \( U \) and \( V \), a multilinear function \( f: U \times V \rightarrow \mathbb{R} \) is defined by:
\[ f(\lambda_1 u_1 + \lambda_2 u_2, \beta_1 v_1 + \beta_2 v_2) = \lambda_1 f(u_1, v_1) + \lambda_2 f(u_2, v_2) \]
\[ f(u, \beta_1 v_1 + \beta_2 v_2) = \beta_1 f(u, v_1) + \beta_2 f(u, v_2) \]

Here, \( \lambda_i \) and \( \beta_j \) are scalars, and the function \( f \) is linear in each of its arguments.

## Tensor Product
The **tensor product** of two vectors \( u \in U \) and \( v \in V \) is defined as:
\[ u \otimes v = (\sum_i \lambda_i u_i) \otimes (\sum_j \beta_j v_j) = \sum_{ij} \lambda_i \beta_j (u_i \otimes v_j) \]

The tensor product \( U \otimes V \) is the vector space whose basis consists of all tensors \( u_i \otimes v_j \), where \( u_i \in U \) and \( v_j \in V \). This space has dimension \( |U| \times |V| \).

## Key Properties
1. **Multilinear Function as Tensor Product**:
   The value of a multilinear function can be expressed using its tensor product representation:
   \[ f(u, v) \cong f(\text{tuple}(u, v)) \cong f(u \otimes v) \]
   Here, \( f(\text{tuple}(u, v)) \) represents the function applied to the concatenated vector or tuple.

2. **Linearity in Each Parameter**:
   The function \( f \) is linear in each of its arguments when considered separately. For example:
   \[ f(\sum_i \lambda_i u_i, \sum_j \beta_j v_j) = \sum_{ij} \lambda_i \beta_j f(u_i \otimes v_j) \]

3. **Equivalence**:
   While \( f(u, v) \) and \( f(u \otimes v) \) may not be the same function in all contexts, they are equivalent in the sense that:
   \[ f(u, v) = f(\text{tuple}(u, v)) = f(u \otimes v) \]
   This equivalence allows us to treat \( f \) as a single-parameter function acting on the tensor product space.

## Example
Consider the vector spaces \( U \) and \( V \) with dimensions \( m \) and \( n \), respectively. The tensor product space \( U \otimes V \) has dimension \( m \times n \). A multilinear function \( f: U \times V \rightarrow \mathbb{R} \) can be represented as:
\[ f(u, v) = \sum_{ij} a_{ij} (u_i \otimes v_j) \]
where \( a_{ij} \in \mathbb{R} \).


> Programmer asks function: From where do you come, to where are you going?
> 
> Function answers: Born from Loader,归于Data.

In programming, everything is a function. **Everything is a function**. However, considering expandability, this function cannot remain static in different scenarios. In practice, the basic structure of the program is f(data), but we can transform it into loader("f")(data). Many frameworks and plugins can be viewed from this angle.


## Three. Ioc Container

> Build asks: How to build a container for managing beans.
> 
> Answer: Follow these steps:
> 1. Use `buildBeanContainer(beansFile)` to create the container.
> 2. Inject dependencies using `getBean(beanName, beanScope)`.
> 3. Access methods via `methodA(data)`.

The Ioc container is a core concept in many frameworks. It allows you to manage dependencies and modularize your code. The process can be summarized as:

$$
process(request) = data * route(objName \otimes tenantId)
$$

However, introducing the tenant concept may require changes to all business objects' logic within the system. On the framework level, we only need to enhance the `route` function to accept `objName` and `tenantId`, allowing dynamic loading of corresponding handlers.

If you delve deeper into this logic, you'll realize that the core of many frameworks is a **Loader** function that operates on tensor products:

$$
process(request) = data \otimes data * route(objName \otimes tenantId)
$$

The Loader concept has revolutionized software development. It allows for dynamic loading of components without hardcoding dependencies. This approach is widely used in Node.js, where modules are loaded using `require(path)`.

In the context of reversible computing, the Loader function gains even more significance. It provides a unified theoretical foundation and brings forth a universal implementation strategy:

> For any multi-linear function $\phi: U \times V \times W \times ... \rightarrow X$, there exists a unique linear function $\psi$ in the tensor space $U \otimes V \otimes W \times ...$ such that:
> 
$$
\phi(\mathbf{u}, \mathbf{v}, \mathbf{w}, ...) = \psi(\mathbf{u} \otimes \mathbf{v} \otimes \mathbf{w}, ...)
$$

This principle simplifies the development of complex systems by allowing for modular construction and efficient composition.


Loader(beansFile\otimes beanName\otimes beanScope \otimes methodName)
$$

* Plugin System

  serviceLoader(extensionPoint).methodA(data)
  
  $$
  Loader(extensionPoint \otimes methodName)
  $$

* Workflow:
  
  getWorkflow(wfName).getStep(stepName).getAction(actionName).invoke(data)
  
  $$
  Loader(wfName\otimes stepName \otimes actionName)
  $$

When we identify similar Loader structures across all layers of the system, an interesting question arises: How consistent are the Loaders in terms of their internal consistency? Can we reuse their code? Workflows like workflow engines, IoC engines, report engines, ORM engines... In the end, every engine seems to load its own specific model. Currently, they are mostly isolated from each other. Can we abstract a system-level, unified Loader to handle model loading?

The design goal of a low-code platform is to modelize code logic into models. When these models are serialized and saved, they form model files. Visualization's input and output are essentially model files. Therefore, visualization is merely an additive benefit of modeling. The core work of a unified low-code platform should be to manage all models and resourceize them. The Loader mechanism is a crucial component in this platform.

Let's look at a common function in daily development:

```java
JsonUtils.readJsonObject(String classPath, Class beanClass)
```

This is a generic Java configuration object loading function. It reads a JSON file from the classpath and uses JSON deserialization to convert it into a specific Java object. If the configuration file has errors, such as incorrect field names or data formats, they can be detected during the type conversion phase. Some validators like @Max and @NotEmpty can even perform business-related checks during deserialization. Compared to raw JSON parsing, model loaders usually have the following enhancements:

1. They can load from a database, not limited to loading from a specific file in the classpath.
2. They may support formats other than JSON, such as XML.
3. They can configure executable scripts within model files, rather than just storing strings, booleans, or numbers.
4. Their format validation is more rigorous, checking if attribute values fall within enumeration ranges, meet specific format requirements, etc.

Nop Platform 2.0 is an open-source implementation of reversible computation theory and can be considered a low-code platform tailored for domain-specific language (DSL) development. It defines a unified model loader in the Nop platform:

```java
interface IResourceComponentManager{
    IComponentModel loadComponent(String componentPath);
}
```

1. Model files' suffixes identify their types, so you don't need to pass componentClass.
2. Models can reference schema files using x:schema="xxx.xdef", enabling stricter schema validation and semantic checks compared to standard Java types.
3. By adding field types like expr, you can directly define executable code blocks within model files and automatically resolve them into executable function objects.
4. A virtual file system supports multiple storage options for model files, such as those stored in a database.
5. Loaders automatically collect dependencies during model parsing based on the model's structure and update the parsing cache accordingly.
6. If you have a FileWatcher, changes in model dependencies can trigger automatic updates of the parsed model.
7. DeltaMerger and DslExtender enable differential analysis and assembly of models. These are covered in detail in Section 5.

In Nop Platform, all models are loaded through a unified model loader, and all model objects are generated using a meta-model (Meta Model). This makes it easy to review the workflow model handling process we discussed earlier.

```java
getWorkflow(wfName).getStep(stepName).getAction(actionName).invoke(data)
```

The `getWorkflow` function loads the workflow (`wfName`), and through its component model loader, automatically generates the necessary methods for each step and action without requiring manual coding. Similarly, the `getStep` and `getAction` methods are also generated by the meta-model and do not require special implementation.

Thus, the entire loader can be considered fully automated in its functionality.

```java
Loader(wfName ⊗ stepName ⊗ actionName)
```

From another perspective, the parameters passed to the `Loader` can be viewed as a multi-dimensional coordinate (all information that can be uniquely identified is considered a coordinate). For each `wfName`, there is a corresponding virtual file path (`path`), which in turn points to specific coordinates within the virtual file system. The `stepName/actionName` pair also corresponds to specific coordinate parameters within the model files. 

The `Loader` receives a coordinate and returns a value, making it essentially a function that defines a coordinate system.

```java
 Loader(wfName ⊗ stepName ⊗ actionName)
```

In terms of invertible computation theory, the primary goal is to establish and maintain such a coordinate system and study the evolution and development of model objects within it.

## Four. Loader as Multiple Dispatch

A function represents a form of static computation (the code itself is deterministic), and the `Loader` provides a mechanism that returns functions based on its parameters, making it a higher-order function. If the `Loader` simply maps input parameters to existing code blocks, it behaves like a monadic computation. However, if it dynamically generates corresponding function content based on incoming parameters, it serves as an entry point for meta-programming.

In programming language theory, there is an inherent meta-programming mechanism called multiple dispatch, which has been extensively utilized in Julia. The `Loader` mechanism shares many similarities with multiple dispatch and can be seen as an extension of the concept beyond Julia's implementation.

Consider a function call `f(a, b)`, if implemented using an object-oriented approach, the first parameter `a` is represented by a `this` pointer (an instance of class `A`). The method `f` is defined in class `A`, and `b` is passed as an argument to this method. Object-oriented function calls like `a.f(b)` are examples of single dispatch, where only the type of `a` is considered at runtime to determine which function to call.

In contrast, multiple dispatch allows for multiple methods to be selected based on the types of **all** parameters. This is often implemented using a virtual function table that looks up the most specific function based on the types of the arguments. For example:

```java
A::A->(B::B->C::C)
```

This can correspond to method calls in object-oriented programming, where `a.f(b)` maps to a specific implementation based on both the type of `a` and `b`.

Julia's multiple dispatch is a prominent example of this concept. It dynamically generates and optimizes code at compile time based on the exact types of function arguments, allowing for efficient execution while maintaining flexibility.

From a vector space perspective, different types can be seen as distinct basis vectors. For instance, type `3` (an integer) corresponds to a basis vector akin to `3 int`, and type `"a"` (a string) corresponds to a basis vector similar to `λ_i e_i`. In this view, incompatible types are like orthogonal vectors that do not interfere with each other. This notion aligns with the idea of multiple dispatch, where functions are dispatched based on all parameter types, ensuring that only compatible implementations are executed.

For example, calling `f(3, "a")` can be seen as `[3, "a"] ⋅ Loader(int ⊗ string)`.

## Five. Loader as Generator

A general model loader can be viewed as follows:

```java
Loader :: Path -> Model
```

A universal design must consider not only current needs but also future changes and the evolution of the system over time. This understanding implies that programming should address all possible scenarios, embracing a universe of possibilities rather than just meeting present requirements.

Introducing a `Possible` concept allows us to define this broader context:

```java
Loader :: Possible Path -> Possible Model

Possible Path = stdPath + deltaPath
```

Here, `stdPath` represents the current path, while `deltaPath` accounts for potential future changes or deviations. The `Loader` is then extended to handle not just existing paths but also all possible variations, effectively becoming a meta-generator capable of adapting to any future development.


In the Nop platform, `stdPath` refers to the standard path corresponding to a model file, while `deltaPath` refers to the delta path used when customizing existing models. For example, within the Base product, we have a business processing flow defined in `main.wf.xml`. When customizing for a specific client A, we need a different processing flow without modifying the Base product's code. In this case, we can add a delta model file at `$/_delta/a/main.wf.xml`, which represents the `main.wf.xml` tailored for client A. The Loader will automatically detect this file and use it, leaving existing business code untouched.

If we only need to fine-tune existing models rather than replacing them entirely, we can use the `x:extends` mechanism to inherit from the original model.

```java
Loader<Possible Path> = Loader<stdPath + deltaPath>
                      = Loader<deltaPath> x-extends Loader<stdPath>
                      = DeltaModel x-extends Model
                      = Possible Model
```

In the Nop platform, the model loader is implemented in two steps:

```java
interface IResource {
    String getStdPath(); // Standard path
    String getPath(); // Actual file path
}

interface IVirtualFileSystem {
    IResource getResource(String stdPath);
}

interface IResourceParser {
    IComponentModel parseFromResource(IResource resource);
}
```

`IVirtualFileSystem` provides a delta file system similar to Docker's overlayfs, while `IResourceParser` is responsible for parsing specific model files.

The reversible computation theory introduces a general software construction formula:

```java
App = Delta x-extends Generator<DSL>
```

Based on this theory, the Loader can be seen as a special case of the Generator. The Path is minimized as a DSL. Once a model object is loaded using the path, we can further apply reversible computation to transform and delta-update the model object.

For example:

```java
App = Delta x-extends Generator<DSL>
```

Based on this theory, the Loader can be seen as a special case of the Generator. The Path is minimized as a DSL. Once a model object is loaded using the path, we can further apply reversible computation to transform and delta-update the model object.

In the Nop platform, we define an ORM entity definition file `orm.xml`, similar to Hibernate's `hbm.xml`. Its structure is as follows:

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

To provide a visual designer for this model file, we need to:

```xml
<orm>
   <x:gen-extends>
      <orm-gen:GenFromExcel path="my.xlsx" xpl:lib="/nop/orm/xlib/orm-gen.xlib" />
   </x:gen-extends>
    ...
</orm>
```

The `x:gen-extends` is a built-in meta-programming mechanism in XLang, which is executed at compile time to generate code. It dynamically generates the base class for the model. The `<orm-gen:GenFromExcel>` custom tag reads and parses an Excel file, then generates an ORM definition file according to `orm.xml` format.

The Excel model file's structure closely resembles everyday documentation formats (e.g., Excel files are often copied and pasted from documentation). By editing the Excel file, you can visually design the ORM entity, and this design is immediately effective (thanks to dependency tracking by `IResourceComponentManager`). If the Excel model file is modified, the ORM model will be recompiled accordingly.

# Replacing Excel with a Graphical Design Tool: A Technical Document

## Problem Statement
Some users may prefer the Excel editing interface over graphical design tools. However, this can be easily resolved by switching to a tool like **PowerDesigner**. The process is straightforward and only requires a simple change in the modeling engine.

## Technical Explanation

### 1. Understanding Representation Transformation
The concept of **Representation Transformation** is central here. It involves converting one representation (e.g., Excel) into another (e.g., PowerDesigner). Unlike traditional tools, this transformation allows for a more flexible and scalable design process.

### 2. Key Concepts in ORM Modeling
- **ORM Model**: The core of any ORM system lies in its ability to map domain concepts to database structures.
- **Visual Design**: While Excel is functional, a tool like PowerDesigner offers a more intuitive way to design and manage complex relationships.

### 3. Code Example: Interface Definition
```java
interface ExcelModelParser {
    XNode parseExcelModel(ExcelWorkbook wk, XDefinition xdefModel);
}
```

## Implementation Strategy

1. **No Need for Custom Parsers**
   - In the Nop platform, there is no requirement to develop custom parsers for `GenFromExcel`.
   - The built-in parser can handle Excel files with the required structure.

2. **Model Structure as a Tree**
   - The ORM model is essentially a tree structure.
   - This structure allows for easy manipulation and extension of the model without deep programming knowledge.

3. **Differential Customization**
   - With `xdef` files, you can define custom extensions.
   - These extensions are automatically applied during the modeling process.

## Example in Excel File
In an Excel file:
- The format is largely unrestricted.
- You can freely add or remove rows and columns as needed.
- Special formatting (e.g., for relationships) is handled automatically by the parser.

## Technical Points to Emphasize

1. **All Is a Kind of Difference**
   - The entire model is based on differences.
   - This starts from configuration files (`xdef`) down to the lowest level of the model structure.

2. **Unified Type System**
   - A unified type system ensures consistency across all layers.
   - This includes data types, domain concepts, and mappings.

3. **No Runtime Dependencies**
   - The transformation process is purely definition-based.
   - It does not rely on runtime environments or specific tooling.

4. **Reverse Engineering Capabilities**
   - Tools like PowerDesigner allow for reverse engineering of database schemas.
   - This capability eliminates the need for manual schema updates.

## Conclusion
The key takeaway is that **representation transformation** is a powerful concept that enables design flexibility without compromising functionality. By leveraging tools like PowerDesigner, you can achieve a more efficient and user-friendly modeling experience compared to traditional Excel-based approaches.

3. Any function, such as `loader`, `resolver`, or `require`, that loads data, objects, or structures can become an entry point for reversible computation. On the surface, a path name appears to be the simplest and most straightforward concept, but according to reversible computation, it reveals an intrinsic evolutionary force within any quantity. Instead of viewing a path name as a static symbol pointing to a static object, we should see it as a dynamic symbol that points to a computational result, potentially representing the future world.

## Path -\> Possible Path -\> Possible Model

## Summary

Here is a simple summary of the content introduced in this document:

1. Linear systems are good
2. Non-linear linear systems can be reduced to linear systems
3. The core of linear systems lies in `Loader::Path -\> Model`
4. `Loader` can be extended as `Possible Path -\> Possible Model`, where loading = composition

5. Reversible computation theory provides a deeper theoretical explanation.

Based on reversible computation theory, the low-code platform `NopPlatform` has been open-sourced:

- **Gitee**: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- **GitHub**: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- **Development Example**: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- **Reversible computation principles and `Nop` platform introduction on Bilibili**: [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)

