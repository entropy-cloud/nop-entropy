# Why XLang is considered an innovative programming language?

> Our physical world exists in four-dimensional spacetime, and quantum field theory along with relativity are its underlying construction rules. String theory attempts to break through the limitations of the lower-dimensional structural space by establishing a unified construction rule within 11-dimensional spacetime.

## 一. 为什么需要设计XLang语言

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/tutorial/simple/images/xlang.png)

XLang is a key supporting technology within the Nop platform, both in form and function incorporating multiple sub-languages such as XDef, Xpl, and XScript. Due to its significant differences from other single-paradigm programming languages, many may initially wonder whether it qualifies as a traditional programming language or if it merely adds scattered extensions to existing languages. 

Here’s my understanding of the essence of programming languages: **A programming language defines a specific program structure space, and it is the set of rules that govern how programs are constructed within this space**. In other words, all feasible computations occur within the defined structure space, encompassing every possible program configuration and their allowable evolutions.

Based on this understanding, **XLang qualifies as an innovative programming language because it creates a new program structure space where the theoretical computation framework of reversible computing (`Y = F(X) + Delta`) can be easily implemented**. While XLang may be viewed as comprising multiple sub-languages like XDef, XPL, and XScript, it’s their collective functionality that enables reversible computing. **XLang is the world's first programming language to explicitly define a domain structure coordinate system and natively support general delta computation rules**.

**The uniqueness of XLang lies in its ability to facilitate continuous aggregation through delta operations**, which contrasts with traditional approaches that rely on atomization and hierarchical assembly, like those based on reductionism.

### 1.1 From a structural perspective: program languages

General-purpose high-level programming languages have evolved over decades since FORTRAN, reaching a certain plateau. The truly innovative features introduced by new languages have become increasingly scarce. Modern languages have converged into multi-paradigm environments, with syntax features like object-oriented structures, lambda functions, custom annotations for meta-programming, and asynchronous programming all becoming common across many languages.

An intriguing question arises: are there still universally applicable syntactic features of sufficient technical value to warrant the introduction of a new programming language? XLang’s innovation lies in identifying that while existing programming languages exhibit significant differences in syntax, their underlying structural layers share profound similarities. The potential for meaningful structural innovation at this foundational level remains vast.

**A program's fundamental structure is essentially defined by data and functions**, where related data and functions are grouped together to form custom types. In typical programming languages, this corresponds to classes (Classes) or interfaces (Interfaces). From a structural perspective, a class is akin to a Map, where properties and methods can be accessed via names.

```javascript
type MyClass = {
  name: string,
  myMethod: (arg1:string) => number
}

or

interface MyClass{
  name: string,
  myMethod: (arg1:string) => number
}
```

When creating a new type based on an existing custom type, inheritance or Traits mechanisms are employed.

```javascript
type MySubClass = MyClass & {
  subName: string
}

or

interface MySubClass extends MyClass {
   subName: string
}
```

From a conceptual perspective, this is akin to:

```javascript
Map = Map extends Map
```

In terms of structure, class inheritance resembles two Maps being overlaid, with properties from the parent Map potentially being overridden by those in the child Map.

Traditional object-oriented languages leverage inheritance to reuse base classes. For example, constructing MapX and MapY both inherit from Map1, allowing reuse of the inheritance hierarchy's lower levels.

```javascript
MapX = Map2 extends Map1
MapY = Map3 extends Map1
```


Inheritance can be expressed as the structure construction formula mentioned above. After that, many problems will become very natural and intuitive. For example, can we swap Map1 and Map2's relative positions? That is, when constructing MapX and MapY, we still reuse Map1 but do not treat it as a base class, instead choosing different base classes while using the same Map1 to cover them.

```javascript
MapX = Map1 extends Map2
MapY = Map1 extends Map3
```

Interestingly, many object-oriented programming languages do not support the above operation, **object-oriented does not directly support reusing the upper part of the inheritance tree!**

Further thinking reveals that traditional object-oriented programming has structural difficulties in answering many questions, such as what problems arise when there are multiple instances of the same object in an inheritance chain?

```javascript
MapX = Map1 extends Map2 extends Map1
```

In C++, multiple inheritance faces significant conceptual challenges at a fundamental level. The basic reason is the difficulty caused by merging the same Map1 after reusing it from different inheritance paths.

Modern programming languages have overcome these issues using the Traits mechanism. For example, in Scala:

```scala
trait Map1 {
  val name: String = "Map1" // Same attribute name
  def method1(): Unit = {
    println(s"Method 1 from $name")
  }
}

trait Map2 {
  val name: String = "Map2" // Same attribute name
  def method2(): Unit = {
    println(s"Method 2 from $name")
  }
}

class MapX extends Map1 with Map2 {
}

class MapY extends Map1 with Map3 {
}
```

> In Scala, multiple Traits can define the same attribute names. The compiler will automatically merge these attribute definitions, resulting in only one variable at runtime. However, in Java or C++, identical attributes defined in different classes will not be merged into a single variable.

Traditional object-oriented programming languages represent `A extends B` as meaning that derived class A has more than base class B, but they do not isolate what exactly is added (delta). Therefore, we cannot directly reuse this added part. Traits explicitly express this delta.

Compared to the inheritance concept, the Traits mechanism forms a more complete delta semantics. **`type MapX = Map1 with Map2 with Map1` is a valid Scala type definition!**

For the problems caused by multiple inheritance, Scala's solution is to introduce linearization rules, arranging all classes and traits in the inheritance chain into a specific order, with upper elements overriding lower ones.

```
MapX -> Map2 -> Map1
```

### 1.2 Generic as Generator

In Java, generics are only used for type checking, and the compiler does not perform any special actions based on generic parameters. However, in C++, generics are implemented through templates, where the compiler generates different code for the same template class based on its parameters.

At the 1994 C++ Standards Committee meeting, Erwin Unruh demonstrated a groundbreaking example. He wrote a template metaprogram that could determine if a number is prime at compile time. If it is, the compiler outputs the prime number in the error message. This code became known as "Unruh Prime Calculation" and is a classic example of C++ template metaprogramming.
Unruh's demonstration proved that C++ templates are Turing-complete at compile time, meaning theoretically any computation can be performed during compilation. This discovery marked the beginning of the era of generative programming, where developers use compile-time computations to generate code or optimize programs.
C++ template metaprogramming has become a crucial tool for implementing generative programming. Through templates, developers can perform complex calculations, type deductions, and code generation at compile time, leading to higher runtime performance and flexibility.

See [C++ Compile-Time Programming](https://accu.org/journals/overload/32/183/wu/)

```cpp
template <int p, int i> struct is_prime {
  enum {
    prim = (p == 2) ||
           (p % i) && is_prime<(i > 2 ? p : 0), 
                             i - 1>::prim };
};

template<>
struct is_prime<0, 0> { enum { prim = 1 }; };

template<>
struct is_prime<0, 1> { enum { prim = 1 }; };

template <int i> struct D { D(void*); };

template <int i> struct Prime_print {
  Prime_print<i - 1> a;
  enum { prim = is_prime<i, i - 1>::prim };
  void f() { D<i> d = prim ? 1 : 0; a.f() }
};

template<>
struct Prime_print<1> {
  enum { prim = 0 };
  void f() { D<1> d = prim ? 1 : 0; };
};

int main() {
  Prime_print<18> a;
  a.f();
}
```

The following errors were encountered during compilation:
- `unruh.cpp:20:19: error: no viable conversion from ‘int’ to ‘D<17>’`
- `…`
- `unruh.cpp:20:19: error: no viable conversion from ‘int’ to ‘D<13>’`
- `…`
- `unruh.cpp:20:19: error: no viable conversion from ‘int’ to ‘D<11>’`
- `…`
- `unruh.cpp:20:19: error: no viable conversion from ‘int’ to ‘D<7>’`
- `…`
- `unruh.cpp:20:19: error: no viable conversion from ‘int’ to ‘D<5>’`
- `…`
- `unruh.cpp:20:19: error: no viable conversion from ‘int’ to ‘D<3>’`
- `…`
- `unruh.cpp:20:19: error: no viable conversion from ‘int’ to ‘D<2>’`

From a structural perspective, template metaprogramming can be understood through the following construction formula:

```
Map = Generator<Map> = Map<Map>
```

- `A<X,Y>` can be interpreted as `A<B>, struct B{ using T1=X; using T2=Y; }`
- Note that here, "Map" refers to the structure seen by the compiler at compile-time. Each member variable, whether it is a property, method, or type declaration, is considered an entry in the Map from the perspective of the compiler.
- Even if the compiler manages parameters as a List, it can still be viewed as a Map with keys accessed via indices. Interestingly, using arrays for management generally does not allow for more advanced composition mechanisms like inheritance. In general, we typically choose to merge by name rather than by index.

The template class `Prime_print` from a structural perspective (as seen by the compiler) can also be viewed as a Map. Combining this with the differential traits concept discussed in the previous section, the most advanced form of object-oriented programming from a structural perspective can be expressed as:

```
Map = Map extends Map<Map>
```

### 1.3 From Map Structure to Tree Structure

From the compiler's perspective, classes, template classes, and template parameters are all considered Maps, and this is how they are typically managed in practice. As for isolated function definitions and variable declarations, they also belong to some form of Map, such as module objects, which can be viewed as a Map containing a set of variables, functions, and types defined within the module. Even if they do not belong to any module, independent functions will still belong to some implicit global namespace.

> Lisp's internal structure is a List, fundamentally utilizing indices to manage elements (even primitive Lisp does not use indices but relies on `car` and `cdr` mechanisms for sequential traversal). However, modern Lisp dialects have introduced Associated List structures that use names for element access rather than indices. From a conceptual level (disregarding the performance advantages of von Neumann machines accessing by index), List can be viewed as a Map using indices.
> 
> Lisp's core S-expression can be seen as a generalized Tree structure, and Lisp provides macros for operating on these Tree structures as part of its inherent mechanisms. However, Lisp does not implement a concept of differential Trees. XLang represents an advancement and deepening of this general processing mechanism for S-expressions.


Current mainstream programming languages provide various syntactic rules that can be viewed as constructing new Map structures in a space based on Map. The innovation of the XLang language lies in its choice to extend the Map structure into a Tree structure and rethink software structure construction on top of this Tree structure. This leads to the generalized software structure construction formula:

```
Tree = Tree x-extends Tree<Tree>
```

> Extending Map to Tree requires extending the extends operation for Map structures into an x-extends operation on Tree structures.

Clearly, Map is a special case of Tree, and each node in the Tree structure can be viewed as a Map. Thus, `Tree = Map + Nested`, making the above formula a generalized form of the `Map extends Map<Map>` construction pattern.
However, from another perspective, Tree structures can be constructed by nesting multiple Maps, with Map being a more basic and finer-grained structure. Therefore, it is necessary to emphasize the Tree structure. Ultimately, all operations on Tree structures can be decomposed into operations at each level of Map structures.

The XLang language addresses this issue by stating that the software structure space (and its construction rules) in complex Tree structures cannot be simply categorized under a Map-based software structure space. In other words, **the holistic and decomposable nature of Tree's construction rules loses some critical information when reduced to Map's construction rules**.

To truly understand XLang's innovation, one must grasp the next-generation software construction theory behind its design: reversible computing theory. Reversible computing explicitly introduces the concepts of inverses and differences, noting that the total (A=0+A) is a special case of difference. It proposes rebuilding all understanding of software based on a difference concept that includes reverses. Reversible computing presents a universal, Turing-complete software construction formula:

```
App = Delta x-extends Generator<DSL>
```

The XLang language implements this technology strategy at the programming language level.

For more information on reversible computing theory, please refer to my public WeChat articles:
1. [Reversible Computing: Next-Generation Software Construction Theory](https://mp.weixin.qq.com/s/CwCQgYqQZxYmlZcfXEWlgA)
2. [Reversible Computing for Programmers: An Analysis](https://mp.weixin.qq.com/s/aT99VX6ecmZXdemBPnBcoQ)
3. [Reversible Computing for Programmers: An Additional Analysis](https://mp.weixin.qq.com/s/zGfo7pvKjOCa11PYLJHzzA)
4. [Difference Concept Analysis with Git and Docker as Examples](https://mp.weixin.qq.com/s/D5bDNkMJ9gYrFb0uDj2EzQ)

Based on reversible computing theory, the uniqueness of Tree structures lies in their corresponding to a global coordinate system: each node and attribute in the Tree structure corresponds to a unique XPath.

```xml
/tasks/task[name='test']/@name
```

The above XPath represents the name attribute of the "test" child node under the "tasks" node. First, let's clarify the role of the coordinate system: **every value relevant to business has a unique coordinate in the coordinate system**, enabling precise reading and modification of values through this coordinate.

```
value = get(path);
set(path,value);
```

The Map structure's limitation is that it only provides two-level coordinates: the first level locates to the Map, and the second level navigates within the Map to locate attributes or methods. However, this simplistic coordinate system cannot implement precise business distinctions. For example:

```java
class Dialog {
    String title;
    List<Button> actions;
    List<Component> body;
}
```

A Dialog object has a set of action buttons. In existing programming languages, there is no straightforward way to locate and modify the label attribute of the "Submit" button if you want to customize it in a specific scenario (e.g., adding an attribute). Using standard AOP mechanisms also fails because **AOP's location system is type-based**, whereas XLang allows direct use of the following description:

```xml
<dialog>
   <actions>
      <button name="submit" label="确定" />
   </actionss>
</dialog>
```

Current programming language research generally focuses on type systems, with types studied because different objects can have the same type. This makes studying types simpler and less problematic concerning object lifecycles. **This leads to a flaw in type systems: identical objects cannot be distinguished in a type-based coordinate system, preventing further fine-grained difference constructions**.


Some people may be confused about the Tree structure: why not just use a graph structure? On a graph, if we select a main observation direction and choose a fixed node as the root, we can naturally convert a graph structure into a tree structure. For example, in Linux operating systems, everything is represented as files, and many logical relationships are incorporated into the file tree's hierarchical structure. However, by leveraging the file system's symbolic link mechanism, the underlying structure can represent a graph. **The so-called "tree" is merely due to our choice of observation direction on the graph.**

For example, structures like flowcharts can be expressed in XML format simply by introducing node ID references: `<step nextTo="nextStepId" />`.

Tree structure **unifies relative coordinates with absolute coordinates**: From the root node, any given node has a unique path, which can act as its absolute coordinate. On the other hand, within a subtree, each node has a unique path from the subtree's root, which can serve as its relative coordinate within the subtree. Based on the node's relative coordinate and the absolute coordinate of the subtree's root, we can easily compute the node's absolute coordinate (by simply concatenating them).

### 1.4 The necessity for an expandable design to have a software structure coordinate system

In software development, "expandability" refers to the ability to meet new requirements or implement new functions by adding additional code or difference information without modifying the original source code. From a completely abstract mathematical perspective, this corresponds to the formula:

```
Y = X + Delta
```

* **X** represents the foundation of pre-written base code that doesn't change with evolving requirements.
* **Delta** represents the additional configuration information or differential code added.

From this viewpoint, research into expandability is equivalent to studying the definitions and operational relationships of Delta differences.

```
X = A + B + C
Y = (A + dA) + (B + dB) + (C + dC)  // Delta exists everywhere
   = (A + B + C) + (dA + dB + dC)  // Deltas can be aggregated together and are independent of the base code storage
   = X + Delta // Deltas satisfy the associativity property and can be implemented independently of the Base
```

If X is composed of A, B, C, etc., and a change in requirements leads to differential modifications scattered throughout the system, **if we require that all disjoint modifications can be managed and stored independently of the original system source code (i.e., Delta independence)**, and smaller deltas can also be combined into a larger granularity (Delta compositeness), then a coordinate system is necessary for precise location. Specifically, after separating `dA` from `A` and storing it in an independent Delta, it inherently retains some form of positioning coordinates. Only then can the Delta recombine with `X` to reconstruct the original structure `A` and integrate with it.

### 1.5 The differences between Delta and Patch, as well as plugin mechanisms

First, it's important to note that mechanisms like Git's Patch and branch management do not satisfy the independence and compositeness of Delta. Patches are always tied to a specific base code version; without knowing the base, multiple patches cannot be merged into a single larger patch. For detailed analysis, refer to the article [Difference Concepts: A Comparison Between Git and Docker for Programmers](https://mp.weixin.qq.com/s/D5bDNkMJ9gYrFb0uDj2EzQ).

Second, it's crucial to emphasize that Delta differs fundamentally from traditional programming field extension points and plugin mechanisms.

```
X = A + B + C
Y = A + B + D
  = X + (-C) + D
  = X + Delta
```

Delta is not merely about adding content to the system. To implement coarse-grained, system-level reuse, the corresponding Delta must include reduction semantics (e.g., removing a Bean defined in the base product). In reality, any coarse-grained Delta will always involve both addition and subtraction.

Additionally, it's important to note that **plugin mechanisms only support a limited number of predetermined extension points**. We cannot use plugins to customize existing system functionality beyond the original design. However, the Delta concept is different: as long as there's a global structural coordinate system, any point in this system can introduce a Delta difference.

For example, Kubernetes introduced Kustomize using Delta differences to enable comprehensive customization. This can be viewed as an application of reversible computation theory, as detailed in [Kustomize from a Reversible Computing Perspective](https://mp.weixin.qq.com/s/48LWMYjEoRr3dT_HSHP0jQ).

# 1.6 Stable Domain Structure Coordinate System

The mainstream programming languages are general-purpose and do not inherently contain knowledge specific to a particular business domain. Therefore, the internal structure coordinate system of these languages can only be based on the built-in class-method two-level structure, with the maximum refinement being the introduction of annotation mechanisms for certain domain-specific fine-grain optimizations at this two-level structure. For structures below the method level, there is generally no suitable technical means to define the coordinate.

When business requirements change, the code typically needs to be modified in multiple locations. At its core, this is because the structural mapping from problem space to solution space in general business environments is inherently non-trivial, leading to a lack of effective alignment between the two descriptive models. Drawing parallels to artificial intelligence terminology, we can say that **all useful features are distributed (distributed)**.

> In physics, the same physical phenomenon can be described using an infinite number of coordinate systems, but there may exist a particularly specialized coordinate system tailored to the specific problem within physics, referred to as the "intrinsic coordinate system." Descriptions made in this coordinate system can highlight the core physical meaning and simplify related descriptions. For example, while a physical phenomenon occurring on a spherical surface can be described in the general three-dimensional Cartesian coordinate system, using spherical coordinates often results in simplification.

The reversible computation theory suggests that a specialized DSL (Domain Specific Language) can be developed for specific business domains. This DSL language allows for the natural establishment of a domain-specific structure coordinate system, which in turn defines the difference structure space. Because this domain-specific coordinate system is tailored to domain-specific problems, it often achieves minimal difference expression. For instance, if a field at the business level needs to be added due to a change, using a general-purpose language may require adjustments across multiple areas such as frontend, backend, and database, whereas using a domain model would localize the change to just a single field level, with the underlying framework automatically translating the domain description into executable logic.

XLang's core functionality lies in its ability to quickly define multiple DSLs, which are then utilized as domain-specific structure coordinate systems for difference definition, structure generation, and transformation.

The fundamental distinction between XLang and other languages is that XLang is based on reversible computation theory and is designed specifically for DSL development. General-purpose languages are directly aimed at application development, where business modeling and implementation of business logic are performed using these languages. However, with XLang, you first establish one or more DSLs before using them to describe the business. **XLang significantly reduces the cost of developing a DSL**, with the most basic case requiring only the use of XDef language to define XDef meta-model files, thereby automatically generating parsers, validators, IDE plugins, visualization editors, and other advanced development tools such as syntax highlighting and debugging capabilities.

> JetBrains' MPS (Meta Programming System) is another product that supports developing DSLs before using them to describe business logic. MPS uses its own defined underlying language mechanisms. The Nop platform is a low-code development platform with a similar structure to MPS, but its underlying theory is based on reversible computation, which differs fundamentally from MPS's technical approach and guiding principles.
> However, the overall technological development goals are similar.

## 2. XLang Specific Syntax Design

XLang is designed as a language oriented toward Tree structures. Its syntax composition can be compared to SQL, which is oriented toward table structures.



| SQL Language                   | XLang language                                   |
| --------------------------- | ----------------------------------------- |
| DDL Data Definition Language   | XDef meta-model definition syntax           |
| Unredundant table data          | Uninformation-redundant tree structure information: XNode |
| Instant computation based on standardized data structures: SQL Select | Runtime and compile-time computation based on general XNode data structure: Xpl/XTransform |
| Table data merge and difference: Union/Minus        | Tree structure Delta differences: x-extends/x-diff   |
| Extend SQL through functions and stored procedures         | Extend XLang through Xpl library and XScript |

First, it's important to note that XLang is designed for tree structures. A natural choice for its syntax representation is XML syntax, so XLang files are typically valid XML files. However, this isn't the only option. Traditional programming languages emphasize syntax forms, but XLang is based on reversible computation theory, which emphasizes that different syntax forms are merely different representations of the same information and that these can be reversibly converted. Therefore, XLang can use any syntax that directly expresses tree structures, such as JSON or YAML. Even Lisp's S-expressions can be extended to serve as XLang's syntax.

> The Nop platform also implements a bidirectional mapping between tree structures and Excel files, enabling the expression of DSL model objects using Excel without writing Excel parsing or generation code, such as using `app.orm.xlsx` Excel files to represent ORM DSL, which is equivalent to `app.orm.xml` XML format DSL files.

### 2.1 Basic Syntax Structure of XDSL

The XLang language itself is Turing-complete, but its primary design purpose is not to serve as a general programming language. Instead, it is used as a meta-language for rapidly developing new DSLs embedded within the Java language environment. In other words, while XLang can be used as a glue language, its main use case is to develop DSLs that are integrated into the Java language environment.

All DSLs developed using XLang share some unified syntax structure, collectively referred to as XDSL.

```xml
<state-machine x:schema="/nop/schema/state-machine.xdef"
     x:extends="base.state-machine.xml">
    <x:gen-extends>
       <app:GenStateMachineDelta1/>
       <app:GenStateMachineDelta2/>
    </x:gen-extends>

    <x:post-extends>
       <app:PostProcessGeneratedModel />
    </x:post-extends>

    <!-- x:override=remove indicates the removal of this node in the final merged result -->
    <state id="commit" x:override="remove" />

    <on-exit>
       <c:if test="${abc}">
           <c:log info="${xyz}" />
        </c:if>
    </on-exit>
</state-machine>
```

The example above demonstrates the following syntax elements in all XDSL implementations:

1. `x:schema` imports an XDef meta-model, similar to JSON Schema, for constraining the syntax structure of the DSL.
2. `x:extends` indicates inheritance from existing DSL files, merging DSL models into a single tree structure through hierarchical merging.
3. `x:override` specifies how corresponding nodes are merged during `x:extends`, with `x:override=remove` indicating the removal of semantic elements.
4. `x:gen-extends` dynamically generates multiple tree structure nodes using Xpl template language and merges them sequentially using Delta algorithm.
5. `x:post-extends` also uses Xpl template language for dynamic generation, but its execution timing differs from `x:gen-extends`.

 6. In DSL, if you want to embed script code, you can directly use the Xpl template language, such as the `on-exit` callback function.

```xml
<model x:extends="A,B">
   <x:gen-extends>
      <C/>
      <D/>
   </x:gen-extends>
   <x:post-extends>
      <E/>
      <F/>
   </x:post-extends>
</model>
```

The complete merge order is:

```plaintext
F -> E -> Model -> D -> C -> B -> A
```

Any XML or JSON file format can introduce the above XLang incremental operation syntax. For example, we introduced the following decomposition plan for AMIS (a open-source JSON format frontend language by Baidu):

```yaml
x:gen-extends: |
   <web:GenPage view="NopSysCheckerRecord.view.xml" page="main"
        xpl:lib="/nop/web/xlib/web.xlib" />

body:
   x:extends: add-form.page.yaml
   title: Override the title defined in add-form.page.yaml
```

AMIS's JSON format does not provide decomposition and merging mechanisms, leading to large and difficult-to-maintain JSON files for complete applications. By introducing the `x:gen-extends` syntax in XLang, you can automatically generate the basic page structure based on View models, and within the page, you can use `x:extends` to import existing files.

XLang supports reversible computation, following the pattern `App = Delta x-exends Generator<DSL>`. The `x:gen-extends` and `x:post-extends` correspond to Generator, a meta-programming mechanism that generates model nodes at compile time as built-in code generators. The `x:extends` syntax is used for merging two model nodes.

For further details, see [General Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300).

The Nop platform, a next-generation low-code platform, has defined multiple DSLs, such as Workflow (workflows), Rule (rules), ORM (data models), BeanDefinition (component orchestration), Batch (batch processing), and Record (binary message models). In general, you do not need to specifically design runtime engines for custom DSLs. Instead, you can use XLang's meta-programming mechanism to translate custom DSLs into existing DSL languages at compile time or integrate multiple DSLs seamlessly to form a new DSL. See [Why SpringBatch is a Bad Design](https://mp.weixin.qq.com/s/1F2Mkz99ihiw3_juYXrTFw) for details about the DSL forest solution.

### 2.2 XDef Meta-Model Definition Language

XML has several international standards, such as XSD (XML Schema Definition) and XSLT (Extensible Stylesheet Language), but these standards are based on a DOM model and assume text structure handling with properties as strings. This makes them unsuitable for general Tree structure processing.

XLang introduced the XDef meta-model definition language to replace XSD. XDef is much simpler and more intuitive than XSD while providing significantly stronger structural constraints compared to XSD.

```xml
<state-machine x:schema="/nop/schema/xdef.xdef">
   <state id="!var-name" displayName="string" xdef:unique-attr="id" />
   <on-exit xdef:value="xpl" />
</state-machine>
```

 Unlike XSD and JSON Schema, XDef uses an atopic design where the structure defined by the meta-model is essentially consistent with the XML format it constrains. It replaces XML node attribute values with corresponding type declarations. For example:

- `id="!var-name"` indicates that the `id` attribute follows the `var-name` format requirements, disallowing special characters and not allowing numbers as prefixes, and `!` denotes that the attribute value cannot be empty.

- `<on-exit xdef:value="xpl"/>` means the content of the `on-exit` node is in Xpl template language. When reading model files, this will automatically be parsed into an executable function of type IEvalAction.

- `xdef:unique-attr="id"` indicates that the current node can appear multiple times, forming a list, with elements identified by their `id` attribute.

It is important to note that the XDef meta-model definition language is defined by `xdef.xdef`. This means that `state-machine.xml` is a Domain-Specific Language (DSL) and its syntax structure is defined by the meta-model `state-machine.xdef`, which has an `x:schema` attribute pointing to `/nop/schema/xdef.xdef`, indicating that this meta-model file is constrained by `xdef.xdef`. Ultimately, `xdef.xdef` still adheres to constraints imposed by `xdef.xdef`, thereby completing the loop.

The shared XDSL-specific syntax across all XDSL domain languages is defined by the `xdsl.xdef` meta-model. The IDEA plugin automatically identifies elements such as `x:extends` and `x:gen-extends` based on definitions in `xdsl.xdef`, enabling features like syntax suggestions and file navigation.

### 2.3 Xpl Template Language

XLang requires a template language for code generation during compilation, but it does not use common template languages like Velocity or FreeMarker. Instead, it implements a new Xpl template language designed specifically for this purpose.

The Xpl template language is Turing-complete, providing syntax nodes such as `c:for`, `c:if`, `c:choose`, `c:break`, and `c:continue`.

```xml
<c:for var="num" items="${numbers}">
    <!-- Check if the number is 7 -->
    <c:if test="${num == 7}">
        <p>Encountering the number 7, stop traversal.</p>
        <c:break /> <!-- Terminate the loop -->
    </c:if>

    <!-- Use c:choose to determine if the number is even or odd -->
    <c:choose>
        <when test="${num % 2 == 0}">
            <p>${num} is even.</p>
        </when>
        <otherwise>
            <p>${num} is odd.</p>
        </otherwise>
    </c:choose>
</c:for>
```

Within Xpl templates, expressions are embedded using `${expr}`. Additionally, the `c:script` node is provided to execute XScript statements.

```xml
<c:script>
  import my.MyDSLParser;
  let model = new MyDSLParser().parseFromNode(path);
</c:script>
```

XScript syntax resembles JavaScript but includes additional extensions, such as the ability to import Java classes using `import` statements.

#### Embedding XML Syntax and Expression Syntax

XLang does not implement JSX syntax for XML-like languages. Instead, it maintains XML syntax while extending JavaScript's Template literal syntax.

```javascript
let result = xpl `<my:MyTag a='${data}' />`
const y = result + 3;
```

This is equivalent to:

```xml
<my:MyTag a="${data}" xpl:return="result" />
<c:script>
  const y = result + 3;
</c:script>
```

XLang modifies the syntax of JavaScript Template literals by changing how quoted strings are parsed. Instead of treating them as Expression lists, XLang interprets the content between backticks as a string to be compiled and evaluated at build time. This allows for greater flexibility in supporting various DSL formats, such as those inspired by C#'s LINQ syntax.

```javascript
const result = linq `select sum(amount) from myList where status > ${status}`;
```

#### Multiple Output Modes

 Unlike traditional template languages designed for code generation, Xpl Template Language is optimized specifically for generating compiled代码. Traditional template languages directly output text content, which can lose the original code's positional information during code generation. To address this, they often require additional mechanisms like SourceMaps to map generated代码 back to its source. In contrast, Xpl Template Language employs multiple output modes when generating compiled代码. When using the `outputMode=node` mode, it does not directly output text but instead outputs XNode nodes.


```java
class XNode {
    SourceLocation loc;
    String tagName;
    Map<String, ValueWithLocation> attributes;
    List<XNode> children;
    ValueWithLocation content;

    XNode parent;
}

class ValueWithLocation {
    SourceLocation location;
    Object value;
}
```

The `XNode` structure records the source code locations of attributes and nodes while converting the types of attribute and content values to `Object`, thereby addressing the limitations of XML's original design, which was only suitable for text documents. This allows for more efficient expression of complex business object structures.

### 2.4 Extensible Syntax

Similar to the Lisp language, XLang can be extended through macro functions and tag functions. New syntax nodes can be introduced using `<c:lib>`, followed by structural transformations implemented within those nodes using macro functions.

```xml
<c:lib from="/nop/core/xlib/biz.xlib" />
<biz:Validator fatalSeverity="100"
               obj="${entity}">

    <check id="checkTransferCode" errorCode="test.not-transfer-code"
           errorDescription="扫入的码不是流转码">
        <eq name="entity.flowMode" value="1"/>
    </check>
</biz:Validator>
```

The `<biz:Validator>` tag introduces a domain-specific language (DSL) for validation. During compilation, this tag uses macro function mechanisms to parse node content and translate it into executable XLang expressions.

## Three. XLang Application Example: Component Model Based on Differential Computing

All software practices involving differential concepts can follow the reversible computing theory's technical path. In many cases, XLang can be directly used for differential merging and decomposition, thereby avoiding the introduction of differential concepts in runtime engines and simplifying runtime implementations. This section introduces an example of its application in a component model within a front-end low-code/no-code platform.

Currently, front-end no-code/low-code platforms essentially implement component nesting and combination through a visual interface. However, **wrapping components often poses challenges: wrapped components are difficult to directly meet specific needs, while developing a completely new component from scratch is also too costly**. The UIOTOS platform addresses this issue with its page inheritance approach.

![nop/uiotos.webp](https://www.yuque.com/liuhuo-nc809/uiotos/fa6vnvggwl9ubpwg#rsHSa)

Specifically, UIOTOS allows existing pages to be used as base pages, with properties in upper-level components overriding those of lower-level pages. For detailed information, refer to [UIOTOS's documentation](https://www.yuque.com/liuhuo-nc809/uiotos/fa6vnvggwl9ubpwg#rsHSa).

To implement this feature, UIOTOS has made extensive customizations and introduced significant related code into the runtime engine. However, by leveraging XLang, differential computations can be entirely executed during compilation, with the runtime engine only needing to know standard component structures without requiring any knowledge of differential decomposition or merging.


```xml
<component x:schema="component.xdef">
  <import from="comp:MyComponent/1.0.0"/>

  <component name="MyComponent" x:extends="comp:MyComponent/1.0.0">
    <state>
      <a>1</a>
    </state>
    <props>
      <prop name="a" x:override="remove"/>
      <prop name="b"/>
    </props>

    <component name="SubComponent" x:extends="ss">
      <prop name="ss"/>
    </component>

    <template x:override="merge">
      Here you can display only the Delta修正的部分

      <form x:extends="a.form.xml">
        <actions>
          <action name="ss" x:id="ss"/>
        </actions>
      </form>
    </template>
  </component>

  <template>
    <MyComponent/>
    <MyComponentEx/>
  </template>
</component>
```

* The `template` segment in Component is used to express how child components are combined.
* When using Subcomponents, you can import existing ones using the `import` syntax or define a local component using the `component` syntax.
* If you implement the Component model as XLang's XDSL, you can use the `x:extends` syntax to customize Delta based on existing components. This eliminates the need for UIOTOS-style special designs and allows Delta customization to be implemented directly using `x:extends`.
* Local components within a component can also contain their own local components, which can also be customized. This means that Delta customization can modify the entire component tree rather than just properties or methods of a specific component class.
* For Delta merging, each node must have a unique coordinate. If DSL nodes do not have usable `id` or `name` attributes, you can use XLang's built-in `x:id` extended attribute. These attributes are automatically deleted after Delta merging is complete, so they do not affect the runtime DSL engine.
* The `x:extends` is executed during model loading and all XNamespaces properties are processed and automatically deleted before being sent to the runtime engine. This means the runtime engine does not require any knowledge of `x:extends`, which contrasts sharply with UIOTOS's approach: Delta differences can be implemented by a generic engine without needing specialized mechanisms for each specific requirement.
* The use of `comp:MyComponent/1.0.0` as a virtual file path allows components to be referenced and isolated by tenant and version when loaded through the virtual file system.

For a complete explanation, refer to the Bilibili video [Discussion with UIOTOS author and design of a low-code platform supporting Delta concepts](https://www.bilibili.com/video/BV1ask2YhEfp/).

Introducing XLang allows Delta components to be implemented with minimal effort, and this approach can be generalized to all DSL models that require Delta editing. For example, similar component models are used in the development of backend service applications.

Based on reversible computing theory, the low-code platform NopPlatform has been open-sourced:

- On Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- On GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development examples: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible computing principles, Nop platform introduction and Q&A\_Bilibili](https://www.bilibili.com/video/BV14u411T715/)