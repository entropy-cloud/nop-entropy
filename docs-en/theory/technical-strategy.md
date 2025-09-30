# Why the Nop Platform Is a One-of-a-Kind Open Source Software Development Platform

Compared with other open source software development platforms, the most fundamental difference is that Nop is derived step by step from the first principles of mathematics through rigorous mathematical derivation to obtain detailed designs at every level. Its components exhibit an intrinsic consistency in a mathematical sense. This directly leads to an implementation that is far more compact than other platforms and, moreover, achieves a level of flexibility and extensibility that surpasses all known public technologies, enabling system-level coarse-grained software reuse. In contrast, mainstream technologies are primarily designed around the idea of component assembly; their theoretical underpinnings impose an upper bound on overall software reusability.

The Nop platform follows the technology strategy formulated by Reversible Computation theory

```
 App = Delta x-extends Generator<DSL>
```

To realize this technology strategy, Nop employs the following concrete tactics.

## I. DSL Forest

In the Nop platform, if a piece of logic can be described using a DSL (Domain Specific Language), it will be described using a DSL. This is fundamentally different from the design philosophy of mainstream frameworks. For example,

1. In Hibernate, introducing a database dialect object requires specifying a Dialect class, whereas in Nop we add a dialect.xml model file.
2. In Hibernate, defining mappings between objects and database tables is primarily done via JPA annotations in code (Hibernate 6.0 has dropped hbm support), while in Nop we use an orm.xml model file.
3. Spring Boot implements complex conditional assembly through annotations, whereas Nop extends beans.xml for the Spring 1.0 syntax by adding conditional constructs, enabling Spring Boot–like conditional assembly in the beans.xml model file.
4. Parsing Excel model files typically either supports only simple structures (like EasyExcel) or relies on developers hand-writing parser code for a fixed format; in Nop, we can describe complex domain object structures stored in Excel via an imp model and automatically perform parsing and Excel generation.

Turing Award laureate Michael Stonebraker, in his critique of MapReduce, pointed out three key lessons learned in the database field over the past four decades:

1. Schemas are good.
2. Separation of the schema from the application is good.
3. High-level access languages are good.

If we want to generalize these three lessons to broader programming domains, we necessarily need to create custom high-level languages—DSLs. DSLs increase information density while boosting the analyzability and portability of business logic. We can analyze declarative DSL structures to reverse-extract a large amount of information and migrate it to new models. For example, the AMIS framework often stresses that its page descriptions have remained stable over time while the underlying implementation framework has been upgraded and replaced many times. We can even write a converter to transform AMIS descriptions into some Vue component framework of our own.

> Under the conceptual framework of Reversible Computation theory, we emphasize the reversibility of information expression: information expressed in one form should be invertible and transformable into other forms. This is in contrast to today’s software where information is solidified in specific languages and frameworks, making upgrades of underlying frameworks an expensive luxury.

Traditionally, a schema constrains a particular data object. When we systematically use DSLs to solve problems, we are no longer dealing with a single DSL but a DSL forest composed of many DSLs. In this context, it becomes necessary to introduce independent schema definitions for DSLs, i.e., metamodels: models that describe models.

## II. Unified Meta-Meta Model

Mainstream frameworks tend to be designed independently: workflow engines, reporting engines, rule engines, GraphQL engines, IoC engines, ORM engines, etc. Each writes its own model parsing and serialization code and its own validation logic. If a visual design tool is needed, each framework implements one on its own. Generally, unless the framework comes from a large company or is part of a commercial product, it is rare to see a framework with corresponding IDE plugins and usable visual design tools.

If all models share a strictly defined intrinsic consistency at the bottom, we can automatically implement a large amount of functionality through logical deduction without having to write separate code for each kind of model. A unified meta-meta model is the key means to achieve intrinsic consistency.

1. A model (metadata) is data that describes data.
2. A metamodel is a model (data) that describes models.
3. A meta-meta model is a model (data) that describes metamodels.

For example,

1. The ORM model file [app.orm.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-dao/src/main/resources/_vfs/nop/auth/orm/_app.orm.xml) defines a database storage model.
2. The ORM model’s structure is described by its metamodel [orm.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm.xdef).
3. In Nop, all models are described using the unified XDef metamodel language, whose own definition [xdef.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef) is the meta-meta model. Interestingly, XDef is defined by XDef itself, so we no longer need a meta-meta-meta model.

Based on the XDef metamodel, Nop automatically derives model parsers, validators, etc., and provides a unified IDE plugin for code completion, go-to definition, and breakpoints with step-through debugging. Furthermore, Nop can, using the XDef metamodel and extended Meta descriptions, automatically generate visual designers for model files. With a shared metamodel and the embedded Xpl template language, we can achieve seamless embedding across multiple DSLs—for example, embedding a rule engine into a workflow engine or embedding an ETL engine into a reporting engine. In addition, a unified meta-meta model makes metamodel reuse possible. For instance, both the [designer model](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/designer/graph-designer.xdef) and the [view model](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/xview.xdef) reference the common form model. Reusing metamodels significantly enhances semantic consistency across the system, reducing conceptual conflicts and improving reuse at the root.

Nop provides the capabilities of a so-called Domain Language Workbench (DSL Workbench). We can rapidly develop new DSLs or extend existing DSLs using Nop, and then develop specific business logic using those DSLs.

As a low-code platform, Nop’s own development approach is low-code. A striking outcome is that the amount of hand-written code in Nop is drastically lower than in traditional frameworks. For example, Hibernate has at least 300,000+ lines of code yet has long-standing limitations such as not supporting subqueries in the FROM clause, not supporting joins beyond association properties, and difficulty optimizing lazy property loading. The NopORM engine implements all core features of Hibernate + MyBatis, supports most SQL join syntaxes, supports WITH and LIMIT clauses, and adds commonly used application-layer features such as soft delete, multi-tenancy, database/table sharding, field encryption/decryption, and field change history tracking. It supports asynchronous invocation, GraphQL-like batch loading optimizations, and generating GraphQL services directly from Excel models. To implement all of these, NopORM has only around 10,000 lines of effective hand-written code. Similarly, Nop implements the NopIoC container with conditional assembly in around 4,000 lines of code, a distributed RPC framework with canary (gray) releases in around 3,000 lines, and NopReport—a Chinese-style reporting engine that uses Excel as the designer—in around 3,000 lines. For details, see:

* [What kind of ORM engine does a low-code platform need](https://zhuanlan.zhihu.com/p/543252423)
* [If we rewrote Spring Boot, what different choices would we make](https://zhuanlan.zhihu.com/p/579847124)
* [NopReport: An open-source Chinese-style reporting engine that uses Excel as the designer](https://zhuanlan.zhihu.com/p/620250740)
* [The distributed RPC framework in a low-code platform](https://zhuanlan.zhihu.com/p/631686718)

## III. Delta-ization

In software development, extensibility refers to the ability to meet new requirements or implement new features without modifying the original code, by adding additional code or differential information. At an abstract mathematical level, we can regard the extension mechanism as corresponding to the following equation:

```
 Y = X + Delta
```

* X corresponds to the base code we have already written; it does not change with evolving requirements.
* Delta corresponds to additional configuration or differential code.

From this perspective, research into extensibility boils down to defining Delta and studying its operations and relations.

Mainstream extension mechanisms in software development have the following issues:

1. You must predict in advance where extensions might occur and define extension interfaces and mechanisms in the base code accordingly.
2. The extension mechanisms and capabilities that each component provides must be designed individually; every component is different.
3. Extension mechanisms often impact performance: the more extension points, the worse the system performance.

Take Spring Boot as an example: if the base code creates two beans, beanA and beanB, and in the extension code we want to remove beanA and extend beanB, we must modify the base code by adding Spring Boot–specific annotations on factory methods in configuration classes. Besides annotations, Spring also provides other mechanisms to fine-tune bean creation, but these approaches require implementing Spring’s internal interfaces and understanding IoC container execution details. If we abandon the Spring container and switch to another IoC framework like Quarkus, extension mechanisms designed for Spring lose relevance.

```java
@Configuration
class MyConfig{
    @Bean
    @ConditionalOnProperty(name="beanA.enabled",matchIfMissing=true)
    public BeanA beanA(){
        return new BeanA();
    }

    @Bean
    @ConditionalOnMissingBean
    public BeanB beanB(){
        return new BeanB();
    }
}
```

Based on Reversible Computation principles, Nop establishes a systematic mechanism for Delta decomposition and merging that enables Delta-ized extensions in a highly unified, general way. In particular, all DSL model files support the Delta customization mechanism: you can add same-named files under the `_delta` directory to override files present in the base code. Using the bean customization logic above as an example,

```
<!-- /_delta/default/beans/my.beans.xml will override /beans/my.beans.xml -->
<beans x:extends="super" x:schema="/nop/schema/beans.xdef">

    <!-- Remove beanA defined in the base code -->
    <bean id="beanA" x:override="remove" />

    <!-- Extend the existing beanB configuration by adding a setting for fldA -->
    <bean id="beanB" >
        <property name="fldA" value="123" />
    </bean>
</beans>
```

This Delta merging process at the DSL level applies to all foundational engines in Nop: ORM, workflow, rule, page, etc., can all be extended in similar fashion. The runtime engine does not need to embed any knowledge about such extensibility. For example, in the NopIoC engine we did not design a large number of extension interfaces like Spring Boot, nor did we execute extensive runtime checks. Instead, we implement dynamic extensions as uniformly as possible at the DSL model compilation stage.

Software products built on Nop can achieve system-level software reuse without any special design at the application layer; during customization, the entire base product can be reused. For example, for a bank core system built on Nop, when deploying and customizing for different banks, there is no need to modify the base product’s source code—just add Delta code. In this way, we can achieve parallel evolution of the base product and the customized versions.

For the detailed technical approach, see:

* [How to implement customization without modifying base product source code](https://zhuanlan.zhihu.com/p/628770810)
* [XDSL: General-purpose domain-specific language design](https://zhuanlan.zhihu.com/p/612512300)
* [Design of the open-source low-code platform Skyve through the lens of Reversible Computation](https://zhuanlan.zhihu.com/p/625523422)

The concept of Reversible Delta is relatively novel and abstract, which has led to misunderstandings among some developers. I wrote the following conceptual clarifications:

* [A clarifying note on Reversible Computation theory for programmers](https://zhuanlan.zhihu.com/p/632876361)
* [Supplementary clarifications on Reversible Computation theory for programmers](https://zhuanlan.zhihu.com/p/634682195)

## IV. Templating

Mainstream software development practice remains essentially a manual craft, with large amounts of program logic hand-written by developers. To achieve automated software production, we must replace manual coding with intelligent code generation mechanisms.

Based on Reversible Computation principles, the Nop platform organically combines Delta-ized programming with [Generative Programming](https://developer.aliyun.com/article/409958), enabling automatically generated code and hand-tuned code to work together. Through metaprogramming and code generation tools, Nop introduces code generation capabilities progressively, greatly expanding the applicability of generative programming paradigms. In traditional code generation, once automatically generated code fails to meet requirements and requires manual changes, the modified code exits the automated production flow and becomes part of manually maintained technical assets. Over long-term system evolution, large amounts of auto-generated code with non-intuitive structure yet requiring manual maintenance are likely to become technical debt—a liability.

In principle, code generation can use a wide variety of approaches, as long as the final output meets specification requirements. However, if we want code generation to remain intuitive and seamlessly integrate with the structural expressiveness of the DSL itself, we need a templated code generation approach. A template is a target structure with templated processing: augmenting it with additional annotations or replacing certain parts with dynamic expressions like `${xxx}`.

Nop places strong emphasis on the homomorphism and [homoiconicity](https://baike.baidu.com/item/%E5%90%8C%E5%83%8F%E6%80%A7) of templates relative to their targets.

## Homomorphism

Homomorphism means the template itself shares a similar structure with the output target; ideally, the template is even a valid instance of the target structure. The most intuitive examples are Excel and Word report design in Nop. Both are fundamentally based on OfficeXML, augmented with annotations and other built-in extension mechanisms to carry expression information, thereby transforming original domain objects into template objects. If we strip some extension attributes from the template object, we actually get a valid Office file that can be edited in Office software! Conversely, when implementing a visual designer, if we embed extension mechanisms aligned with templating, we can upgrade a regular domain object designer into a template designer.

For details, see:

* [How to implement a visual Word template like poi-tl in 800 lines of code](https://zhuanlan.zhihu.com/p/537439335)
* [NopReport: An open-source Chinese-style reporting engine that uses Excel as the designer](https://zhuanlan.zhihu.com/p/620250740)

To preserve structural similarity between templates and outputs, Nop systematically uses prefix-guided syntax, applied in the template-matching syntax for automated testing and the test data generator. See:

* [Layered DSL syntax design and prefix-guided syntax](https://zhuanlan.zhihu.com/p/548314138)
* [Automated testing in a low-code platform](https://zhuanlan.zhihu.com/p/569315603)

When a template is highly structured, we can use XDSL’s general-purpose Delta customization mechanism to customize the template itself.

Note that a model object can itself be regarded as a template: we can generate a Model directly from a Model, `Model = Model<EmptyConfig>`, which is essentially an identity function; marking or processing the original object transforms it into a complex generator, `Model = ModelTemplate<Config>`.

## Homoiconicity

Homoiconicity is a property originating from Lisp functional languages: the template language’s syntactic structure is isomorphic to its abstract syntax tree (AST), so executable template code can be generated directly using the template language. Nop uses XML as the base structural syntax; the template language is XML, and its output is also valid XML. For example,

```xml
<orm>
    <c:for var="entity" items="${ormMode.entities}">
        <orm-gen:GenEntity entity="${entity}" />
    </c:for>
</orm>
```

Benefits of maintaining homoiconicity include:

1. Easy introduction of custom program syntax; at the usage level, custom syntax is fully equivalent to built-in language syntax.
2. During metaprogramming, DSL syntax can be represented via a unified XNode structure; transforming program structures is as straightforward as operating on ordinary tree data.
3. Easy mapping from generated code back to template source locations. The template language effectively uses an AST form; its output is not plain text but new AST nodes (XNode). If it were just text, further structured processing would be impossible, and tracking the template source corresponding to generated code would be non-trivial.

## V. Multi-Stage Decomposition

Nop extensively uses code generation to produce code through automatic inference. However, if the inference chain is long, a one-shot generation approach can lead to overly complex model definitions and a jumble of information across different abstraction levels. For these cases, Nop provides a standard technical pathway:

1. Using embedded metaprogramming and code generation, an inference pipeline can be established between any structures A and C.
2. Decompose the inference pipeline into multiple steps: A => B => C
3. Further Delta-ize the pipeline: A => `_B` => B => `_C` => C
4. Each stage can stage and pass through extension information not needed by that step.

For example, Nop includes a built-in inference pipeline that automatically generates the complete backend and frontend stack from an Excel data model.
![](../tutorial/delta-pipeline.png)

Specifically, from backend to frontend the logical inference chain can be decomposed into four main models:

1. XORM: storage-layer–oriented domain model
2. XMeta: GraphQL interface–oriented domain model
3. XView: business-level frontend logic using a small set of UI elements—forms, tables, buttons—independent of frontend frameworks
4. XPage: page model bound to a specific frontend framework

From an Excel model we can automatically generate the `_XORM` model; on that basis, we add Delta configuration to form the final XORM. Next, we generate the `_XMeta` model from XORM, and so on. Written as equations:

```
   XORM  = CodeGen<Excel> + DeltaORM
   XMeta = CodeGen<XORM>  + DeltaMeta
   XView = CodeGen<XMeta> + DeltaView
   XPage = CodeGen<XView> + DeltaPage
```

Every step in the inference chain is optional: we can start at any step, and we can entirely discard all information inferred by previous steps.

For instance, we can manually add an XView model without requiring any particular XMeta support; we can also directly create a page.yaml file and write JSON code following the AMIS component specification—the capabilities of the AMIS framework are not constrained by the inference pipeline.

The low-code platform NopPlatform, designed based on Reversible Computation theory, is open source:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Introduction and Q&A on Reversible Computation principles and the Nop platform_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
<!-- SOURCE_MD5:65cc36d543f7917a59421480928a9051-->
