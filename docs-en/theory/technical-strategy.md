# Nop Platform: A Unique and Unparalleled Open-Source Development Platform

The **Nop platform** stands out compared to other open-source development platforms due to its unique foundation based on **rigorous mathematical derivations** and a **detailed step-by-step design approach**. Its components exhibit an inherent mathematical consistency, leading to significantly shorter and more efficient code implementations compared to other platforms. In terms of flexibility and expandability, it surpasses all known technologies, enabling system-level coarse-grained reuse.

The platform adheres to **reversible computation theory**, guiding its strategic technical decisions. To implement this strategy, Nop employs specific tactical methods:

```
App = Delta x-extends Generator<DSL>
```

### Section 1: DSL Forest

Within the Nop platform, any logic described using **Domain-Specific Language (DSL)** will be implemented using DSL. This approach is fundamentally different from mainstream frameworks, which typically follow their own design philosophies.

#### Examples:
1. In **Hibernate**, mapping database objects to tables requires defining a `Dialect` class and using a `dialect.xml` file.  
   On Nop, we instead use an `orm.xml` file for mappings, compatible with JPA annotations (e.g., Hibernate 6.0 no longer supports `hbm.xml`, but Nop uses `orm.xml`).
   
2. **SpringBoot** achieves configuration through annotations, while Nop extends this functionality using `beans.xml` files, supporting similar conditional extensions as SpringBoot.
   
3. For handling Excel data, Nop supports **EasyExcel-like** structures either through manual code implementation for specific formats or by using `imp.xml` models to describe complex domain structures and generate corresponding parsing and generation code automatically.

#### Key Insight from Turing Award Winner Michael Stonebraker:
Over the past 40 years, three critical lessons have been learned in database development:
1. Schemas are good.
2. Separating schema concerns from application logic is beneficial.
3. High-level query languages are preferable.

To generalize these insights across broader programming domains, **DSLs** (Domain-Specific Languages) are essential. DSLs enhance information density and improve the analyzability and migratability of business logic. For example, Baidu's AMIS framework emphasizes long-term stability of its domain descriptions, while the underlying implementation has evolved significantly over time. Nop further enhances this by enabling the creation of custom transformers to map AMIS descriptions to Vue components.

#### Reversibility in Computing:
In the context of reversible computation theory, information should remain expressible in any form. Unlike contemporary software, which often fixes information in specific languages or frameworks, Nop ensures that domain descriptions are decoupled from implementation details. This allows for easy migration and adaptation as technologies evolve.

Traditionally, **Schema** served primarily to constrain data. However, with the advent of DSLs like those used in Nop, we move beyond mere constraints to full system-wide modeling. In this context, a **forest of DSLs** emerges, where multiple DSLs coexist and interoperate, forming a unified meta-model.

### Section 2: Unified Meta-Model

Modern frameworks typically follow their own design philosophies, each with its own model of data handling (e.g., workflow engines, report generators, rule engines, GraphQL engines, etc.). Each framework often requires custom code for mapping and serialization, leading to fragmentation in the development landscape.

Nop's unified meta-model provides a foundation for integrating diverse frameworks by enabling a single, consistent modeling language. This approach eliminates the need for domain-specific mappings and reduces code duplication. The key principle is that if a model is well-defined at the meta-level, it can be implemented consistently across different frameworks without significant changes to the domain logic.

#### Example:
- **ORM Models**: Defined in `orm.xml` files, these models describe database schemas and object mappings.
- **Code Generation**: Nop's built-in code generation tools (e.g., for `orm.xml`) ensure that schema changes are reflected across all layers of an application without manual intervention.

### Section 3: Implementation Details

1. **Hibernate Example**:
   ```
   <hbm-xml id="Customer" entity="com.example.Customer">
       <property name="name">varchar(255)</property>
       <many-to-many id="orders"/>
   </hmxb-xml>
   ```
   In Nop, this becomes:
   ```
   <orm-xml id="Customer" entity="com.example.Customer">
       <property name="name">varchar(255)</property>
       <many-to-many id="orders"/>
   </orm-xml>
   ```
   (Note: JPA annotations are used instead of Hibernate-specific mappings in Nop 6.0+.)

2. **SpringBoot Example**:
   - Conditional configuration using `@Conditional` annotations is extended through `beans.xml` files, enabling similar SpringBoot-like behavior but with a more flexible and extensible structure.

3. **Excel Handling**:
   - Using `imp.xml` models to describe Excel structures:
     ```
     <imp-xml id="CustomerData">
         <sheet name="CustomerList" type="xlsx"/>
         <row pattern="CustomerID,Name,Email"/>
         <column mapping="CustomerID" type="integer"/>
         <!-- Additional columns can be added similarly -->
     </imp-xml>
     ```
   - Nop automatically generates parsing and generation code based on these descriptions.

### Section 4: Generalization of Key Insights

1. **Schemas Are Good**:
   - They provide clear constraints and improve data integrity.
   
2. **Separation of Schema from Application Logic**:
   - This decoupling enables more maintainable and scalable systems.

3. **High-Level Query Languages Are Good**:
   - DSLs like those used in Nop enable higher-level abstractions, improving both performance and developer productivity.

### Conclusion

The Nop platform represents a significant leap forward in software development, offering unprecedented capabilities in code brevity, flexibility, and scalability. By embracing mathematical principles and meta-modeling, it enables developers to build systems that are not only more efficient but also easier to maintain and extend over time.

2. ORM Model Structure is described by its Meta Model [orm.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm.xdef).
3. In the Nop platform, all models are described using a unified XDef Meta Model Language, where the definition of the XDef Meta Model is given by [xdef.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xdef.xdef). This is referred to as a double meta model. Interestingly, XDef is defined by XDef itself, so we no longer need a triple meta model.

The Nop platform automatically generates parsers, validators, etc., based on the XDef Meta Model through a unified IDE plugin. Furthermore, the Nop platform can generate visualizers for model files based on the XDef Meta Model and extended Meta descriptions. With the help of public meta models and embedded Xpl template language, we can achieve seamless integration of multiple DSLs in workflows like embedding rule engines in workflow engines or ETL engines in report engines. Additionally, **the unified double meta model enables the reuse of meta models**, such as [designer.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/designer/graph-designer.xdef) and [view.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/xui/xview.xdef), which reference common [form.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/form/form.xdef) models. The double meta model significantly improves internal consistency and reduces concept conflicts, enhancing system extensibility.

The Nop platform provides the ability to work with a Domain-Specific Workbench (DSW). We can leverage the Nop platform to rapidly develop new DSLs or extend existing ones and then use DSLs to develop specific business logic.

As a Low-Code Platform, Nop itself is developed using a Low-Code development approach. A notable outcome is that the amount of manually written code in the Nop platform is significantly reduced compared to traditional frameworks like Hibernate, which has over 300,000 lines of code but lacks support for subqueries in FROM clauses, association properties outside of mapped entities, and efficient lazy loading optimization. The Nop ORM Engine [NopORM](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm.xdef) implements all core functionalities of Hibernate+MyBatis, supporting most SQL query languages while adding features like logical deletion, multi-tenant support, database sharding, field encryption, and field modification history tracking, along with asynchronous mode and GraphQL-like batch optimization. **With all these features, the amount of manually written code in NopORM is approximately 10,000 lines**. Similarly, the Nop container [NopIoC](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/orm.xdef) is implemented with around 4,000 lines of code and supports conditional assembly of the NopIoC container, enabling gray-scale releases. The distributed RPC framework [NopRPC](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/rpc/rpc.xdef) is implemented with around 3,000 lines of code and supports灰度发布。类似地，中国式报表引擎[NopReport](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/report/report.xdef)也是基于约3000行代码实现的。

For more details, please refer to the following articles:

* [What ORM engine is needed for a Low-Code Platform](https://zhuanlan.zhihu.com/p/543252423)
* [If we rewrite SpringBoot, what choices do we have](https://zhuanlan.zhihu.com/p/579847124)
* [Open-source China-style Report Engine: NopReport](https://zhuanlan.zhihu.com/p/620250740)
* [Distributed RPC Framework in a Low-Code Platform](https://zhuanlan.zhihu.com/p/631686718)

## 3. Quantization

In software development, extensibility refers to the ability to add new functionality without modifying existing code by adding additional code or delta information. From an abstract mathematical perspective, this corresponds to the formula:

```
Y = X + Delta
```

- **X** represents the existing base code that does not change with new requirements.
- **Delta** represents the additional code or delta information added.

From this perspective, extensibility in software development is equivalent to the definition and operation of Delta.

Mainstream software development practices commonly use the following extension mechanism:

1. **Predict where extensions will occur and define good extension interfaces and mechanisms in the base code**
2. **Each component must provide its own extension capabilities and be designed differently**
3. **Extension mechanisms often negatively impact performance, with more extensions leading to worse system performance**

For example, using SpringBoot as an example: if we create two beans, beanA and beanB in the base code, and want to remove beanA while extending beanB in the extended code, we need to modify the base code and add SpringBoot-specific annotations to the factory method. In addition to directly using annotations, SpringBoot also provides mechanisms to fine-tune the creation process of beans by implementing internal interfaces and understanding the details of the IoC container. If we abandon the Spring container and switch to other IoC frameworks like Quarkus, the extension mechanisms designed for the Spring container lose their significance.

```java
@Configuration
class MyConfig {
    @Bean
    @ConditionalOnProperty(name = "beanA.enabled", matchIfMissing = true)
    public BeanA beanA() {
        return new BeanA();
    }

    @Bean
    @ConditionalOnMissingBean
    public BeanB beanB() {
        return new BeanB();
    }
}
```

The Nop platform is based on reversible computation principles and has established a comprehensive system of delta decomposition and merging mechanisms. It can be used in a very unified and generic way to implement delta-based expansion. Specifically, all DSL model files support the Delta customization mechanism. By adding files with the same name to the `_delta` directory, you can override existing base code files. For example:

```xml
<!-- /_delta/default/beans/my.beans.xml will override /beans/my.beans.xml -->
<beans x:extends="super" x:schema="/nop/schema/beans.xdef">
    <!-- Remove the definition of beanA from the base code -->
    <bean id="beanA" x:override="remove" />

    <!-- Add fldA settings to the existing beanB configuration -->
    <bean id="beanB">
        <property name="fldA" value="123" />
    </bean>
</beans>
```

This delta merging process at the DSL level is suitable for all lower-level engines in the Nop platform, such as ORM engines, workflow engines, rule engines, and page engines. These can be expanded in a similar manner. **No specialized knowledge of expandability is required at runtime.** For example, in the NopIoC engine, we do not design numerous extension interfaces like SpringBoot does, nor do we perform extensive logic checks at runtime. Instead, we attempt to implement dynamic expansion as uniformly as possible within the DSL model during compilation.

Based on the Nop platform, software products do not need special design at the application level to achieve system-level reuse. When customizing development, the entire base product can be reused. For example, a core banking system developed using the Nop platform can be customized for different banks without modifying the base code's source files; instead, you only need to add Delta difference code to the `_delta` directory. Through this approach, we can achieve **parallel evolution between the base product and its customized versions**.

Specific implementation plans can be found in the following articles:

* [How to implement customization without modifying the base product's source code](https://zhuanlan.zhihu.com/p/628770810)
* [XDSL: Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300)
* [From Reversible Computation to Open-Source Low-Code Platform Skyve's Design](https://zhuanlan.zhihu.com/p/625523422)

The concept of reversible differences is relatively novel and abstract, leading some developers to misunderstand it. Therefore, I wrote the following analysis articles:

* [Reversible Computation Explained for Developers](https://zhuanlan.zhihu.com/p/632876361)
* [Supplement to Reversible Computation Theory for Developers](https://zhuanlan.zhihu.com/p/634682195)

## Four. Templates

Current mainstream software development practices are essentially still labor-intensive. A large portion of program logic is manually written by developers. To achieve automated production, we must replace manual coding with intelligent code generation mechanisms.

# Nop Platform

The **Nop platform** is based on the principles of **reversible computing** and combines **difference programming** with **generative programming**. It supports collaborative operation between automatically generated code and manually corrected code, leveraging advanced meta-programming techniques and code generation tools to significantly expand the scope of generative programming paradigms.

In traditional code generation approaches, once auto-generated code fails to meet requirements, manual corrections are necessary, which forces the code into a maintenance-heavy process. Over time, this can lead to technical debt and ultimately reduce the platform's efficiency as a technology asset.

Code generation in the Nop platform can be implemented using various methodologies, but for maximum flexibility and maintainability, template-based code generation is highly recommended. This approach allows seamless integration of dynamic expressions (e.g., `${xxx}`) into the code structure while preserving its integrity.

## Reversible Computing

Reversible computing refers to a programming paradigm where both the forward and reverse operations are designed to be efficient. The Nop platform places significant emphasis on maintaining this property between templates and their target structures, ensuring that template logic aligns with operational requirements.

### Advantages of Template-Based Code Generation

1. **Ease of Integration**: Facilitates the inclusion of custom programming logic while maintaining code readability.
2. **Flexibility in Meta-Programming**: Enables the use of XNode structures to describe DSL syntax, allowing for seamless transformation and manipulation of domain-specific objects.
3. **Traceability and Maintainability**: Makes it easier to track changes and dependencies across the generated code, reducing the risk of errors during updates.

### Example Implementation

Consider the following XML-based template:

```xml
<orm>
    <c:for var="entity" items="${ormMode.entities}">
        <orm-gen:GenEntity entity="${entity}" />
    </c:for>
</orm>
```

This template demonstrates how dynamic expressions can be embedded into the code structure while maintaining proper formatting and functionality.

## Homomorphism

Homomorphism is a key concept in programming that ensures structural similarity between templates and their generated outputs. In the context of the Nop platform, this principle is applied to achieve:

1. **Consistency**: Ensures that template logic aligns with domain-specific requirements.
2. **Scalability**: Allows for the gradual integration of complex structures into the code generation process.

### Example of Homomorphism in Action

For instance, consider a template where the output is an Excel report. The structure of the template mirrors the data hierarchy of Excel spreadsheets:

```xml
<report>
    <row>
        <cell>${header}</cell>
        <cell>${data}</cell>
    </row>
</report>
```

This example illustrates how homomorphism can be applied to create highly structured and maintainable code.

## DSL Design and Prefix Notation

The Nop platform leverages **domain-specific language (DSL)** design principles, particularly prefix notation, to enhance both functionality and readability. By using prefix-based syntax, the platform ensures that operations are explicitly defined, reducing ambiguity and improving maintainability.

### Example of DSL Implementation

For example:

```xml
<dsl:operation name="calculate">
    <dsl:arg type="number">${value}</dsl:arg>
    <dsl:arg type="operator">${operator}</dsl:arg>
</dsl:operation>
```

This code snippet demonstrates how prefix notation can be used to define operations and their arguments, making the DSL structure clear and easy to extend.

## Conclusion

The Nop platform's reliance on reversible computing principles and template-based code generation offers a robust framework for creating efficient and maintainable solutions. By emphasizing homomorphism and DSL design, the platform minimizes technical debt and maximizes the value of auto-generated code while maintaining flexibility for manual adjustments.


The Nop Platform extensively utilizes code generation mechanisms driven by automated reasoning to produce code. However, when the reasoning chain becomes complex, using a step-by-step code generation approach can lead to overly complex model definitions, causing information from different abstraction levels to become disorganized.

To address this challenge, the Nop Platform provides a standardized approach:

1. **Embedded Meta-Programming and Code Generation**: Any structure between entities A and C can establish a reasoning pipeline.
2. **Decomposing the Reasoning Pipeline**: The reasoning pipeline can be broken down into multiple steps: A → B → C.
3. **Further Deltaization**: The reasoning pipeline can be deltaized as follows: A → `_B` → B → `_C` → C.
4. **Transparent Extension Information**: Each step allows for temporary storage and transparent transmission of extension information that is not required for the current step.

In the Nop Platform, we have embedded a complete logic generation pipeline from backend to frontend:

![Delta Pipeline](../tutorial/delta-pipeline.png)

Specifically, the logical reasoning chain can be decomposed into four key components:

1. **XORM**: Dedicated to the storage layer.
2. **XMeta**: Tailored for the GraphQL interface layer.
3. **XView**: Focused on frontend logic at the business level, utilizing forms, tables, and buttons while being UI-agnostic.
4. **XPage**: Designed for page modeling using a specific frontend framework.

Based on an Excel model, we can automatically generate `_XORM` models. From there, additional delta configuration information is added to form the final XORM model. The next step involves generating `_XMeta` models based on XORM, and this process continues similarly. In mathematical terms:

```
XORM  = CodeGen<Excel> + DeltaORM
XMeta = CodeGen<XORM>  + DeltaMeta
XView = CodeGen<XMeta> + DeltaView
XPage = CodeGen<XView> + DeltaPage
```

Each step in the reasoning relationship is optional:  
**Start at any step**: You can begin directly from any point in the chain.  
**Skip previous steps**: All information derived from preceding steps can be discarded without issues.

For instance, we can manually add an XView model even if it lacks specific XMeta support. This allows us to directly create `page.yaml` files according to AMIS component standards, writing JSON code that adheres to AMIS conventions. Notably, the AMIS framework remains unaffected by the reasoning pipeline.

Finally, based on reversible computation theory, the open-source NopPlatform (NopPlatform) is designed for a low-code environment:

- **GitHub**: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- **Gitee**: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- **Documentation Example**: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- **Reversible Computation Principles and NopPlatform Introduction**: [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)

