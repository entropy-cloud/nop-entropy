
# Third Round of Q&A on the XLang Language

## 1. Is XLang a framework or a programming language?

XLang is not a traditional programming language, but if you ask DeepSeek, DeepSeek would answer:

> XLang combines the high abstraction of fourth-generation languages with the theoretical innovations of fifth-generation languages. Its core positioning is a “meta-language supporting Reversible Computation,” which boosts development efficiency through low-code and domain-specific syntax while redefining the underlying rules of program construction via structural space theory and Delta operations. Therefore, XLang can be regarded as an evolutionary form of a fourth-generation language—call it a “Fourth-Generation+ Language”—achieving a unique balance between low-code and theoretical innovation.

Mainstream programming languages today are so-called third-generation programming languages (3GL), which typically use a main function as the program’s entry point. Fundamentally, the language design is about writing that executable main function, and the compiler translates the execution logic written in the programming language into instructions executable by a hardware model. Fourth-generation programming languages (4GL), however, began to emphasize descriptiveness, simplifying development through highly abstract declarative syntax and graphical programming tools. In other words, the core of 4GL is “non-procedural,” emphasizing higher-level abstraction rather than adaptation to lower-level hardware models.

With the rise of LLVM, hardware-level optimization is essentially a separate concern, and different programming languages can share a common compilation backend. This does not constitute an essential requirement of a programming language. I expect new languages like `Moonbit` to offer a very compact, lightweight toolchain. As long as XScript’s AST is translated to `Moonbit`’s AST, the rest is handled automatically.
XLang includes a sub-language XScript, whose syntax deliberately adopts a subset of JavaScript plus minor extensions, and the type system mimics a simplified Java-like type system. This ensures lossless translation at the AST level to all mainstream programming languages.

Although the Nop platform is a complete low-code platform built on the XLang language, this does not mean that XLang itself is a framework. While XLang currently runs on the JVM and relies on certain helper libraries and implementation classes provided by the underlying Java language, it does not depend on Java. The entire content of XLang can be ported to other programming languages such as `Lisp` or `Rust`. When writing business code, you can use only the XLang language without touching the underlying Java language.

## 2. The fundamental reason a Turing machine achieves Turing completeness is that a Turing machine can be viewed as a virtual machine that can simulate all other automatic computing machines.
For this statement, those with formal training may object: Turing completeness is defined via the Turing machine, so “a Turing machine is Turing-complete” is a conclusion by definition. My view is: if you get entangled in the mathematical definition and say Turing completeness is defined via the Turing machine, that’s fine; but this is merely due to a historical contingency.

Conceptually, Turing completeness and NP-completeness are similar classifications in computation. The computational complexities of every NP-complete problem are comparable; solving any one of them allows you to solve all NP-complete problems. Yet we do not define NP-completeness as “knapsack-completeness.”

Similarly, Turing completeness is an abstract computational capability. All computing machines are equivalent at this capability boundary; no particular machine has a more special capability. By historical accident, this capability was named Turing completeness. Turing completeness can be defined as a computing system’s ability to execute any computable function; it could just as well have been named Lambda Calculus completeness.

In physics, all concepts are independent of any particular problem or choice of reference frame. If one insists that Turing completeness is defined via the Turing machine, that is merely a concrete manifestation of using a specific idealized model to describe an abstract computational capability. What we care about is not the concrete form, but the universal capability itself.

## 3. Is the development goal of XLang to replace general-purpose languages like Java?

XLang’s development goal is not to replace mainstream languages such as `C++` or `Java`; in fact, they should be complementary.

```xml
<c:script lang="groovy">
  Groovy syntax can be used here
</c:script>
```

XLang uses XML syntax. When execution logic needs to be expressed, it employs the Xpl template language, a sub-language that is Turing-complete. Within it, you can embed XScript via the `<c:script>` tag. If `lang="groovy"` is specified, you can implement the script in Groovy. Similarly, you can integrate virtually any other language.

Existing programming languages already address a large problem space effectively; there is often no need to invent new syntax for expressing procedural computation logic—reusing existing language syntax or even language runtimes will suffice.
XLang focuses on areas not effectively handled by existing languages. By introducing the concepts of Delta and Reversible Computation, XLang can solve many problems that require the computing pattern `F(X)+Delta` to be handled effectively. In other words, the problem space XLang addresses is largely non-overlapping with that of existing languages. However, this does not mean XLang is a DSL; its syntax and semantics are general-purpose and not tied to any specific business domain. Ultimately, in usage, XLang can cooperate with any third-generation programming language: the XScript part can be replaced with any other third-generation programming language.

If we regard TypeScript as an extension of JavaScript, `TypeScript = JavaScript + JSX + TypeSystem`, then XLang can also be viewed as an extension of JavaScript, `XLang = XScript + Xpl + XDef + MetaProgramming + DeltaProgramming`. Xpl is an XML-form template language, similar in purpose to JSX.
The distinctive parts here are DeltaProgramming and MetaProgramming. XLang does not strongly depend on JavaScript syntax; the XScript sub-language can be replaced by any other third-generation programming language.

Some misunderstand XLang’s capabilities because it adopts an XML syntactic form and thus fail to associate it with a conventional programming language. But if you think carefully, TypeScript embeds XML-like JSX syntax inside JavaScript, and JavaScript code blocks can be embedded inside JSX. It is a bona fide programming language. Conversely, embedding JavaScript syntax inside XML format is equivalent to what TypeScript does, isn’t it?

XLang’s outermost entry point is not a simple main function; rather, it is a variety of DSLs with diverse structures and semantics, and even visual models.

## 4. Can XLang build its own ecosystem?

Certainly. But the content in XLang’s ecosystem is not the usual generic functionality that every new language seems destined to re-implement, such as JSON parsers or HTTP clients. XLang is typically used with a host language (e.g., Java), allowing direct reuse of functionality implemented in the host, at most with a wrapper over standard interfaces. This is similar to how TypeScript directly reuses the underlying JavaScript host ecosystem.
Future directions include cross-language code reuse across multiple syntactic forms, e.g., all based on WASM bytecode, or via GraalVM’s polyglot interop mechanisms.

What should be shared within XLang’s ecosystem are primarily XDef meta-model definitions for various DSLs, as well as code generators and meta-programming structural transformations written in the Xpl template language.

To reiterate, XLang’s primary use is to rapidly develop and extend domain-specific languages, realizing the so-called Language Oriented Programming paradigm. Business development is essentially independent of any specific programming language—akin to physical facts being independent of coordinate systems. A general-purpose language is like a general Euclidean coordinate system; locally, we can adopt more efficient specialized coordinate systems, i.e., DSLs. As technology advances, descriptive programming and imperative programming can be better integrated, and the descriptive subspace occupied by DSLs can grow larger. Multiple DSLs can be seamlessly bonded via `G<DSL1> + G<DSL2> +Delta`; the concept of Delta must be added to break the limitation that a DSL applies only to a single domain.

## 5. Are there intuitive examples showing how XLang is used?

You can refer to the following articles:
- [Looking at Kustomize through Reversible Computation](https://mp.weixin.qq.com/s/48LWMYjEoRr3dT_HSHP0jQ)
- [Design Comparison: NopTaskFlow Logic Orchestration Engine vs. SolonFlow](https://mp.weixin.qq.com/s/rus4sPKvO-C78cOjSd0ivA)
- [XDSL: A General-Purpose Domain-Specific Language Design](https://mp.weixin.qq.com/s/usInt7_odzvFzuiIUPw4iQ)
- [A Theoretical Analysis of Reversible Computation for Programmers](https://mp.weixin.qq.com/s/aT99VX6ecmZXdemBPnBcoQ)
- [Addendum: A Theoretical Analysis of Reversible Computation for Programmers](https://mp.weixin.qq.com/s/zGfo7pvKjOCa11PYLJHzzA)

Detailed syntax can be found in the Nop platform documentation [XLang Language](https://nop-platform.github.io/projects/nop-entropy/docs/dev-guide/xlang/)

In most cases we do not develop business applications directly with XLang. Instead, we first use XLang to define a DSL, and then develop the actual business using that DSL. XLang defines DSL structure via the XDef meta-model language; the Nop platform’s `nop-xdefs` module collects all meta-model files of already-defined DSLs.
When developing your own DSL, you generally do not have to start from scratch. You can compose these existing XDef meta-model definitions directly—for example, reuse the variable definition model `var-define.xdef` via `xdef:ref` in a rules model.

```xml
<rule>
  <input name="!var-name" xdef:ref="schema/var-define.xdef" xdef:name="RuleInputDefineModel"
         computed="!boolean=false" mandatory="!boolean=false" xdef:unique-attr="name"/>
  ...
</rule>
```

In fact, XLang’s concrete merge algorithm is very simple—essentially akin to the virtual DOM diff algorithms in React and Vue, only simpler. XLang stipulates that elements in a list have unique identifiers such as name or id, thereby ensuring stable XPath that can serve as domain coordinates. During diff and merge computation, items are merged directly by coordinates.

Syntactically, XLang is essentially ordinary XML augmented with a small set of special annotations such as `x:schema`, `x:extends`, and `x:override`. The underlying language engine understands these annotations and performs Delta merge after parsing. `x:schema` imports the XDef meta-model; via XDef we can define the types of nodes and attributes in XML. If an attribute’s type is specified as xpl, that attribute is parsed using the Xpl template language.

```xml
<task x:schema="/nop/schema/xdef.xdef">
  <steps xdef:body-type="list" xdef:key-attr="name">
    <xpl name="!string">
      <source xdef:value="xpl" />
    </xpl>
  </steps>
</task>
```

The meta-model above defines the structure of `task.xml`. It states that steps are a set of step definitions, each step has type xpl, and it has a source attribute, which is parsed using the Xpl template language.

An example of a concrete task.xml:

```xml
<task x:schema="/nop/schema/task.xdef">
  <steps>
    <xpl name="test">
      <source>
        logInfo("hello world");
      </source>
    </xpl>
  </steps>
</task>
```

Note that the structure of the `task.xdef` meta-model and the `task.xml` model file it describes are essentially identical. `task.xdef` is like an XML template with annotations indicating the types of the template’s attributes. The concrete `task.xml` is the result of filling the template’s attributes and child nodes with specific values.

`x:schema="/nop/schema/xdef.xdef"` on the root of the meta-model indicates that `task.xdef` is a meta-model definition file whose structure is constrained by `xdef.xdef`. Meanwhile, `x:schema="/nop/schema/task.xdef` on the root of `task.xml` indicates that `task.xml` is a model file whose structure is constrained by `task.xdef`.
If you look at the definition of `xdef.xdef`, you will find its `x:schema` also points to `xdef.xdef`. In other words, model structure is defined by the XDef meta-model, and the XDef meta-model itself is defined using the XDef meta-model.

Here is one community member’s understanding:

> I finally fully grasped your principle of Delta-based merging today. XLang is a set of standard attribute definitions used to add, delete, or modify node definitions. Delta-based merging is the process of merging a main file with a Delta, and the merge rules are defined by the XLang language specification. After merging, you get a new DSL description (which can be XML, JSON, or any tree structure). You then hand this new DSL to an execution engine; how it parses and handles the DSL is the execution engine’s responsibility.

XLang defines DSLs and automatically implements DSL decomposition, merging, and Delta customization. In principle, once you obtain the merged DSL, it is no longer tied to XLang; the execution engine can use any other technology to parse XML/YAML for subsequent processing. If you use the Nop platform deeply, the execution engine can leverage the XDef meta-model to automatically parse the DSL, and executable code segments can directly reuse the Xpl template language.

## 6. How does XLang define Delta at the language level? (not a formula)
XLang defines XDSL via the XDef meta-model; every syntactic element in an XDSL has a unique, stable domain coordinate. Business is expressed in XDSL, effectively projecting business into the coordinate system defined by XDSL. A Delta can be defined at any point in this coordinate system. Furthermore, Deltas that arise at many coordinates can be factored out and recognized and managed conceptually as one large Delta—an integrated change pattern that becomes an independent cognitive entity.

XLang can be viewed as a meta-language. It does not directly express Deltas; it first defines a DSL, and then defines language-level Deltas within that DSL. At a more abstract level, all sub-languages in XLang are defined via the XDef meta-model language, and XDef itself is defined via XDef as well. Consequently, all its sub-languages automatically inherit the concept of language-level Deltas.

Many find this difficult to grasp because they do not realize the meta-level nature here. It is not directly about “A solves problem X,” but rather solving it at a higher meta-level through a logical ladder, then projecting it down to the next level where it manifests concretely.

<!-- SOURCE_MD5:afd8453aca84e37269b7d33af71ff0a6-->
