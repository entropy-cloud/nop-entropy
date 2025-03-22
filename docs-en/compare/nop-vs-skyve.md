# Reversible Computing and the Design of Open-Source Low-Code Platforms

[Skyve](https://github.com/skyvers/skyve) is an open-source business application platform written in Java. It supports no-code and low-code rapid development and is compatible with various database engines such as MySQL, SQL Server, and H2 Database. Skyve adopts a relatively traditional backend low-code implementation approach, which is also one of the most popular solutions for low-code and no-code platforms. In this article, we will compare the design of Skyve with that of the Nop platform to help understand the unique features of the Nop platform.

## 1. Multi-Tenant Customization

Skyve is a multi-tenant system that provides a feature called "Tenant Override." This allows each tenant to have their own specific configurations, enabling them to possess unique functional implementations.

Skyve's approach involves creating files in the directory `/src/main/customers/tenantId/{ModelPath}` which override the corresponding files in `/src/main/module moduleId/{file}`. This design is similar to Docker's layered filesystem concept, where each tenant acts as a layer that can override lower layers. Many low-code platforms adopt similar schemes. However, when compared with Nop platform's delta mechanism based on reversible computing, Skyve's solution appears quite primitive and ad-hoc.

1. Skyve customizes file types for specific files rather than using a general-purpose delta filesystem concept. To add a new file type, Skyve requires modifications to the `FileSystemRepository` implementation.
2. Loading model objects based on file paths has not been abstracted into a unified `ResourceLoader` mechanism. It lacks features like model parsing caching and resource dependency tracking (which would invalidate caches when dependent files change).
3. Customization files completely overwrite the original files, unlike the Nop platform's approach where custom files inherit from the base file and only contain delta changes.

In the Nop platform, **all model files are loaded through a unified mechanism**:

```java
model = ResourceComponentManager.instance().loadModelModel(resourcePath)
```

> Reference [custom-model.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/guide/model/custom-model.md) can configure the file type and its corresponding model loader.

For the file `/nop/auth/model/NopAuthUser/NopAuthUser.xmeta`, we can add a `_delta/default/nop/auth/model/NopAuthUser/NopAuthUser.xmeta` file. During loading, priority is given to files in the `_delta` directory. By default, only the `default` layer is enabled; however, this can be specified using the `nop.core.vfs-delta-layer-ids` parameter. This means that **delta layers can be multi-layered**, unlike Skyve's single-layer delta customization.

> Historically, we have used customizations such as:
> - `platform`: Customize and fix platform-built-in features
> - `product`: Basic general-purpose features
> - `app`: Specific application features

In model files, we can use `x:extends="super"` to indicate inheritance from the upper layer. In this file, only delta changes need to be described.

```xml
<meta x:extends="super">
    <props>
        <!-- Remove fields from the base model -->
        <prop name="fieldA" x:override="remove" />
        <prop name="fieldB">
            <!-- Add dictionary configuration for fieldB -->
            <schema dict="xxx/yyy" />
        </prop>
    </props>
</meta>
```

In addition to using `x:extends="super"`, we can explicitly specify the base model, for example:

```xml
<meta x:extends="/nop/app/base.xmeta">
</meta>
```

**The `x:extends` mechanism is an effective tree structure decomposition approach**, which can also be **applied to JSON files**. For instance, a large page interface in the frontend can be decomposed into multiple sub-files using a similar approach.


```json
{
  "type": "page",
  "body": {
     ...
     {
        "type": 'action',
        "dialog": {
           "x:extends" : "xxx/formA.page.json",
           "title" : "zzz", // This can override the properties inherited from x:extends
         }
      }
   }
}
```


## 二. Domain-Specific Models

The goal of **Veve** is to define as much as possible through metadata rather than code, providing document types such as Document、View、and multiple XML formats for domain-specific models. This enables us to describe a significant portion of the business logic using XML files without writing Java code.

**Veve** uses XSD (XML Schema) language to standardize the format of XML file and implements XML parsing through JAXB (Java Architecture for XML Binding). Similarly, the Nop platform adopts the meta-model definition language **XDefinition** (referred to as **XDef**) to define model file formats. However, its design philosophy differs significantly from XSD.


### 1. Homomorphic Design

**XDef** explicitly adopts the concept of homomorphic mapping, where the structure of the XDef meta-model aligns with the structure of the model itself, but adds some annotation information on top of the model syntax. For example, [view.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/m/nop-xsrcs/src/resources/resources/vfs/nop/schema/xui/xview.xdef)

```xml
<!--
Includes form definitions, table definitions, and page organization
-->
<view BizObjName="string" x:schema="/nop/schema/xui/xview.xdef" xdef:check-ns="auth"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="xdef">

    <grids xdef:key-attr="id" xdef:body-type="list">
        <grid id="!xml-name" xdef:ref="grid.xdef"/>
    </grids>
   ...
</view>
```

The model structure it describes is as follows:

```xml
<view x:schema="/nop/schema/xui/xview.xdef" BizObjName="NopAuthUser"
      xmlns:x="/nop/schema/xdsl.xdef" xmlns:j="j">
    <grids>
        <grid id="list" >
            <cols>
                <!-- Username -->
                <col id="userName" mandatory="true" sortable="true"/>

                <!-- Nickname -->
                <col id="nickName" mandatory="true" sortable="true"/>
            </cols>
        </grid>
    </grids>
</view>
```

It is sufficient to use the original model file as a template and replace specific values with corresponding **StdDomain** definitions. For example, `id="!xml-name"` indicates that the `id` property is a required attribute, and its format must comply with the `xml-name` definition requirements, i.e., it must conform to XML specifications.

> Custom **StdDomain** can be registered using `StdRegistry.RegisterDomainHandler(handler)`.

The simplicity and intuitiveness of **XDef** meta-model are such that even OpenAI's ChatGPT can directly understand its definitions, as demonstrated in [GPT-Driven Low-Code Platform for Production Applications: Verified Strategies](https://zhuanlan-zhihu.com/p/617455000).


### 2. Executable Types

In schema definition languages such as XSD or JSON Schema, only basic data types are defined, but no executable code semantics are specified. In **XDef** meta-models, we can specify `StdDomain=expr xpl` and other types to automatically parse XML text into expression objects or Xpl template objects.

By leveraging this mechanism, we can embed Turing-complete scripting languages or template languages into domain-specific languages (DSL). Conversely, by utilizing the compile-time processing capabilities of Xpl template language, we can seamlessly integrate any DSL into templates. This achieves **seamless integration between general-purpose and domain-specific languages**.

# 3. Domain Coordinate System

In Kyve, XSD is merely used as an XML serialization tool and serves no other purpose. However, in the Nop platform, the XDef metamodel definition not only defines the domain structure but also **establishes a coordinate system for domain-specific concepts within the domain model space**!

Within the Nop platform's domain model, every node corresponds to a unique path starting from the root (its absolute coordinates), such as `/view/grids[id="list"]/cols/col[id="fieldA"]/label`, which represents the label attribute of the column with id "fieldA" in the table with id "list".

> While XPath syntax can be used to locate nodes in a tree structure, it may match multiple nodes or attributes, making it unsuitable for one-to-one identification required for coordinate purposes.

In XDef definitions, for each element collection, we typically configure an `xdef:key-attr` attribute to specify the identifier of its child nodes. For example, the definition corresponding to the `view`'s `grids` element in the earlier example is as follows:

```xml
<grids xdef:key-attr="id" xdef:body-type="list">
    <grid id="!xml-name" xdef:ref="grid.xdef"/>
</grids>
```

This approach is analogous to the `key` property in frameworks like Vue/React, which is used by their virtual DOM diffing algorithms for efficient updates.

Based on the `xdef:key-attr` configuration, if we want to add properties to an existing table column, we can do so as follows:

```xml
<view x:extends="_NopAuthUser.view.xml">
    <grids>
      <grid id="list">
        <cols>
           <!-- Remove existing columns -->
           <col id="fieldB" x:override="remove" />
           <id id="fieldA" width="new configuration">
           </id>
        </cols>
      </grid>
    </grids>
</view>
```

> **Class inheritance mechanisms generally cannot achieve overriding specific attributes of a list within a base class!**

Based on domain model differential computation, various functionalities involving abstraction can be uniformly implemented by the platform without embedding them into specific domain models. For instance, the NopIoC dependency injection container, which uses configuration syntax similar to Spring 1.0, can uniformly leverage its Delta mechanism to remove system-internal bean definitions without incorporating any logic for handling bean exclusion in the engine. As a result, NopIoC achieves BootStrap's dynamic configuration capabilities with approximately 4,000 lines of code.

```xml
<beans x:schema="/schema/beans.xdef" xmlns:x="/schema/xsl.xdef"
       x:extends="base" x:dump="true">
    <bean id="nopDataSource" x:override="remove" />

    <bean id="nopHikariConfig" x:override="remove" />

    <name name="DynamicDataSource" alias="nopDataSource" />
</beans>
```

# 4. Metaprogramming

All models in Ve are either manually written or generated during the first build. If we encounter recurring structural patterns, it is difficult to abstract them out. That is, **Ve does not provide a mechanism for further abstraction and generalization based on built-in models**.

The theory of invertible computation indicates that software construction can follow the formula:

```
App = Delta x-Extends Generator<DSL>
```

In this formula, `Generator` plays a very crucial role in the context of invertible computation. The domain model of the Nop platform is equipped with meta-programming mechanisms such as `x:gen-extends` and `x:post-extends`. These mechanisms enable dynamic generation during the parsing and loading process. By leveraging this mechanism, general-purpose structural transformations are moved from the runtime engine to the compile-time execution, simplifying the engine development and improving system performance.

For example, in workflow implementations, we typically need to add specific logic for joint signatures within the engine. However, joint signatures can be considered redundant design patterns: they can be decomposed into a regular step plus an implicit branching step. In the Nop.Workflow design, support for joint signatures is achieved by adding a call to `<wf:CounterSignSupport>` in the `x:post-extends` section. This mechanism automatically identifies joint signature steps and expands them into two step nodes based on their configuration.

This meta-programming approach is extremely powerful, similar to mathematical theorem derivation. It only requires considering how to transform symbols to achieve the desired result, without concerning runtime dependencies.

In the NopORM engine, support for JSON objects and extended fields is implemented through compile-time generation techniques. The ORM engine itself does not inherently contain such knowledge. For more details, refer to [orm-gen.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm/src/resources/resources/_vfs/nop/orm/xlib/orm-gen.xlib).

---

### 5. Custom Extensions

In Ve, the properties of an object are fixed, and we can only accept its design as is. Without modifying the core Ve code, it is impossible to add custom extended properties to objects. However, the Nop platform adheres to the principle that **differential (delta) approaches should be applied everywhere**. Therefore, in any design, a paired structure of `(base, delta)` must be adopted. Consequently, space for extensions must be reserved in object designs.

The general convention in Nop is: All properties with namespace qualifiers are considered extended properties by default, except those defined in the XDef meta-model. For example:

```xml
<prop name="fieldA" ui:show="C"></prop>
```

In the XDef meta-model, `ui:show` is not predefined, but since it has a namespace qualifier, it is directly saved as an extended property during parsing and is not treated as a validation error.

> The `(base, delta)` paired design is reflected in all aspects of the Nop platform. For instance, the message structure for all transmitted messages follows the `(data, headers)` pattern. In many cases, metadata can be viewed as additional information that supplements data. Data and metadata can be converted into each other depending on the usage context. If current processing logic does not involve certain information, it can be stored and transmitted as metadata. When processing is required in the next stage, existing data can be converted to metadata, while previous metadata can be treated as data for processing.

In practice, the notion that "metadata is merely data" is not entirely accurate. Metadata can include additional information unrelated to the current application but harmless (**useless yet harmless**).

---

### 6. Domain-Specific Language Workspace

# 3. Specific Model Comparison

In addition to the differences in general mechanisms, there are specific differences in the implementation of field models between Nop Platform and Kyve.

## 1. Data Model

In Kyve, the Document model describes object structure and inter-object relationships. It is responsible for both the frontend interface structure and the storage layer persistence structure. In contrast, the Nop Platform uses XMeta model and ORM (Object-Relational Mapping) models to achieve similar functionality.

Kyve's underlying architecture is based on Hibernate technology, which means it inherits Hibernate's strengths and weaknesses. The Nop Engine, however, is built from scratch using reversible computation principles, making it a new generation of ORM engine. It defines EQL (Extended Query Language) as the minimal extension of SQL grammar: EQL = SQL + ActiveJoin. This approach addresses some inherent limitations of Hibernate while retaining its native capabilities. For specific design details, refer to [Low-Code Platforms and Their Required ORM Engines (1)](https://zhuanlan.zhihu.com/p/433252423).

Kyve does not distinguish between layer-based structural models and storage-layer structural models. In complex business scenarios, it becomes difficult to isolate the impacts of different levels or adapt to long-term structural evolution. At the storage level, we aim to reduce data redundancy, while at the application layer, we may need to generate multiple derived datasets from the same base data.

The Nop Platform can **automatically generate RESTful services based on data models**, and it integrates a range of common business features:

- Support for composite primary keys
- Automatic encryption/decryption for fields
- Generation of masked values for sensitive fields like card numbers
- Automatic generation of label fields based on dictionary tables (implemented using metaprogramming in XMeta configuration)
- Batch loading optimization to address the "N+1" problem common in Hibernate
- Soft delete functionality
- Optimistic locking
- Automatic recording of last modified user and timestamp
- Recording of field values before and after modification
- Built-in approval mechanism (Checker pattern), where modifications require approval before being committed
- Support for master-slave table transactions
- Recursive deletion of child table data
- Extension field support
- Sharding and partitioning
- Distributed transactions

For specific design details, refer to [What Kind of ORM Engine Does a Low-Code Platform Need? (2)](https://zhuanlan.zhihu.com/p/455033021).

## 2. Backend Service Extension

Kyve implements backend service extension through Bizlet.

```java
class Bizlet {
    public void preSave(T bean) throws Exception {
    }

    public void preDelete(T bean) throws Exception {
    }

    public void postPersist(T bean, WebContext webContext) {
    }
}
```


The design is clearly tied to CRUD operations, but it is not complete. We can intercept query operations in a simple way and add additional behavior before and after queries. Queries are executed directly by calling the storage layer and are not processed by Bizlet.

In the Nop platform, the Nop Engine decomposes into the object layer as ModelModel model, which is a generic model and is not limited to the implementation of CRUD services. CrapModelModel only provides a base class with default action definitions. Using metadata information from XMeta, CrapModelModel can automatically implement very complex parameter validation and master-sub-detail table saving, among other features. The Nop Engine integrates a highly flexible data filtering function, allowing precise control over data access permissions on complex object diagrams through simple configuration. For specific details, please refer to the video: [Nop Platform Configuration of List Filtering Conditions and Data Permissions](https://www.bilibili.com/v/BV14411H7my/).

Another key design point in the Nop platform is its emphasis on **framework-independent expression of business logic**. Service implementations are dependent on specific framework components, such as how backend services in Skyve rely on the WebContext object, which includes HttpServletRequest and HttpServletResponse objects. This ties them to the web runtime environment, making it difficult to migrate business code to non-web environments. In contrast, the Nop platform's Engine entry parameters and return objects are POJOs with no specific runtime environment dependencies. The Nop Graphical Language (NopGL) can be seen as a pure logic execution engine whose inputs can come from various sources, such as reading request objects from batch files and automatically converting online services into batch services (based on the Nop Engine's automatic batch submission optimization). Additionally, it can directly integrate with Kafka message queues, transforming web services into message processing services (responses can be sent to a specific Topic).

![BizModel.svg](../arch/BizModel.svg)

The POJO-based design also reduces the difficulty of unit testing, as individual services can be tested without integration.

For more details on NopDesign, please refer to [Engine in Low-Code Platform](https://zhuanlan.zhihu.com/p/899633344).

---


### 2. Display Model

Skyve uses the View model to describe the structure of the interface, which can be seen as a framework with only a few fixed components.

```xml
<view xmlns="http://www.skyve.org/xml/view"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    name="_residentInfo" title="Resident Info"
    xsi:schemaLocation="http://www.skyve.org/xml/view ../../../../views.view.xsd">
    <form border="true" borderTitle="Resident Info">
        <columnPercentageWidth="30" responsiveWidth="4" />
        <column />
        <row>
            <item>
                <default binding="parent.residentName" />
            </item>
        </row>
    </form>
    <form border="true" borderTitle="Resident Photo">
        <columnPercentageWidth="30" responsiveWidth="4" />
        <column />
        <row>
            <item showLabel="false">
                <contentImage binding="parent.photo" />
            </item>
        </row>
    </form>
</view>
```

The XView model in the Nop platform is positioned similarly to Skyve's View model, but it adopts a more business-oriented abstraction approach. It abstracts concepts such as forms, tables, layouts, actions, and pages, allowing for the separation of form layout information and specific control content through NopLayout layout language. For example:

```xml
<view>
    <forms>
        <form id="edit" size="lg">
            <layout>
                ========== intro[Product Introduction] ================
                GoodsSn[Product Number] name[Product Name]
                counterPrice[Market Price]
                isNew[Is New Release] isHot[Is Hot Recommendation]
                isOnSale[Is On Sale]
                picUrl[Product Image URL]
                gallery[Product Banner Images, Use JSON Format]
                unit[Product Unit, e.g., piece, box]
                keywords[Product Keywords, separated by commas]
                categoryId[Product Category ID] brandId[Brand ID]
                brief[Product Introduction]
                detail[Product Detailed Description, Rich Text Format]

                =========specs[Product Specifications]=======
                !specifications

                =========products[Inventory Information]=======
                !products

                =========attrs[Product Parameters]========
                !attributes

            </layout>
            <cells>
                <cell id="specifications">
                    <gen-control>
                        <input-table addable="@:true" editable="@:true"
                                     removable="@:true" required="@:false">
                            <columns list="true">
                                <input-text name="specification" label="Specification Name" required="true"/>
                                <input-text name="value" label="Specification Value" required="true"/>
                                <input-text name="picUrl" label="Image URL" required="true"/>
                            </columns>
                        </input-table>
                    </gen-control>
                    <selection>id,specification,value,picUrl</selection>
                </cell>
              </cells>
          </form>
      </forms>
    </view>
```

NopLayout layout language uses an extremely concise format to express complex layout rules. Meanwhile, the display controls for individual fields are automatically inferred based on data model definitions and data item information, without explicit expression. If the automatically inferred controls do not meet requirements, we can use the gen-control configuration under cell to explicitly specify the display control for that field.

> Interestingly, this NopLayout syntax is also easily understandable and imitable by ChatGPT. See [How to Overcome GPT's Input Token Limitations and Generate Complex DSL](https://zhuanlan.zhihu.com/p/616855144)

For specific rules of the NopLayout syntax, please refer to [Form Layout Language in Low-Code Platforms: NopLayout](https://zhuanlan.zhihu.com/p/592131885)

The translated content is as follows:

---

Another issue exists with the handling of ve's View model: what if the default view model cannot meet the requirements? Ve offers no solution in such cases; if the original design of the model falls short, we are left with no choice but to abandon the entire page and rewrite it from scratch using other technologies. However, in the Nop platform, by leveraging a differential merging mechanism, partial inheritance can be implemented, supplemented by additional delta information.

The frontend of the Nop platform uses the Baidu AMIS framework, an excellent lightweight frontend framework. For more details about it, please refer to [Why is the Baidu AMIS Framework an Excellent Design?](https://zhuanlan.zhihu.com/p/997739555). The page descriptions in the frontend are generated during compilation based on the XView model's JSON configuration. On top of this automatic generation, minor delta customizations can be made. Therefore, as long as the page falls within AMIS's capabilities, partial inheritance can be utilized to leverage the XView model's capabilities without starting from scratch.

```yaml
# Default generation for main.page.yaml file based on XView model

x:gen-extends: |
  <web:Page view="NopAuthUser.view.xml" page="main"
        xpl:lib="/nop/x/xlib.x.xlib" />
```

Another critical question arises: what if AMIS is also insufficient for describing the frontend page structure? First, custom components can be used to extend AMIS's capabilities. Since all frontend widget structures can ultimately be expressed as an Abstract Syntax Tree (AST), which can then be serialized into JSON format, AMIS's JSON form is theoretically complete and capable of handling any description (in extreme cases, the entire page can be rendered using a single custom component that reads body configurations and interprets them as specific interface content). Additionally, we can leverage code generation and metaprogramming mechanisms to create an interpreter for the XView model, translating it into Vue or React source code.

### 4. Code Generation

Skyve provides a Maven plugin that generates entity class code based on XML models. However, Skyve's code generator is relatively simple, outputting results through string concatenation in code. In contrast, Nop's code generator, XCode Generator, offers a more systematic solution.

Firstly, XCode Generator supports incremental code generation, separating generated code from manually modified code. This ensures that regenerating based on the model does not overwrite manual changes.

Secondly, XCode Generator is data-driven, controlling the generation process through template directory structures and conditional statements (e.g., `{!enabled}{ModelModel.name}.java` indicates that the corresponding Java file will only be generated if the `enabled` setting is true).

Thirdly, like other models in Nop, XCode Generator supports delta customization. This means custom logic can be added without modifying the core generation templates by appending files to the `delta` directory.

Fourthly, XCode Generator supports custom model generation and can operate independently outside of the Nop platform. For example, alongside data models and API interfaces, we can define a domain-specific Excel format model for our business needs. By simply adding an `imp.xml` file, this model can automatically parse Excel files into domain objects and then apply custom code templates to generate target files.

For more details about XCode Generator, please refer to [A Data-Driven Incremental Code Generator](https://zhuanlan.zhihu.com/p/440022244).

### 5. Reporting Tools

Skyve uses JasperReport for reporting, which is insufficient for handling complex Chinese-style reporting requirements. The Nop platform provides a Chinese-style report engine, NopReport, which implements the hierarchical coordinate mechanism unique to Chinese-style reports with approximately 3,000 lines of code. For more details, please refer to [NopReport: An Open-Source Chinese-Style Report Engine Using Excel as the Designer](https://zhuanlan.zhihu.com/p/620250440). [Video](https://www.bilibili.com/video/BV1Sa4y1K7tD/).

For Word template exports, the Nop platform also offers a Word engine that uses the XPL template language built into the platform. With just around 800 lines of code, it converts Word files into templates capable of generating output. For more details, please refer to [How to Use 800 Lines of Code to Achieve Similar Functionality to poi-tl for Visualizing Word Templates](https://zhuanlan.zhihu.com/p/374333355).

### 6. Automation Testing

Skyve provides support for automated testing, generating WebDriver test cases based on data models and View models.

---

```xml
<ux uxui="external" userAgentType="tablet" testStrategy="Assert"
    xsi:schemaLocation="http://www.skyve.org/xml/sail ../../../skyve/schemas/sail.xsd"
    xmlns="http://www.skyve.org/xml/sail"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

    <action name="Document Numbers">
        <method>
            <navigateDocument document="DocumentNumber" module="admin"/>
            <listGridNew document="DocumentNumber" module="admin"/>
            <testDataInput/>
            <save/>
            <testDataInput/>
            <save/>
            <delete/>
        </method>
    </action>
</ux>
```

The platform leverages model information in a more sophisticated manner. In addition to applying the model at the input端, it fully utilizes the advantages of model-driven approaches to capture all side effects within the system and records them. This enables the conversion of test cases dependent on complex states into stateless logical test cases. For specific details, please refer to [Automated Testing in Low-Code Platforms](https://zhuanlan.zhihu.com/p/693316003).

## 4. The Value of Theory

The fundamental distinction between the Nop platform and all other low-code frameworks lies in its foundation on a new software construction theory - reversible computing theory. This theory begins by establishing a minimal set of concepts, then rigorously builds a comprehensive technical体系 through logical derivations. Reversible computing theory overcomes the limitations of component-based theories at the theoretical level, breaking down barriers to achieving system-level and coarse-grained software reusability. As an implementation of reversible computing theory, the Nop platform provides unified technological tools to address common issues encountered in domain modeling across various fields.

The approach taken by the Nop platform to solve problems fundamentally differs from other platforms. Taking Excel models as an example, traditional methods typically define a specific model format for particular business requirements and write an Excel parser function accordingly. Different model files would require separate parsing functions. In contrast, the Nop platform defines rules for mapping Excel structures to domain object structures and develops a unified generic parser. Using category theory terminology, the Excel model parser in the Nop platform can be described as a functor (functor) that maps from the Excel category (comprising infinitely many different Excel file formats) to the domain category (comprising infinitely many different domain object structures). If we further define a reverse functor from the domain model category to the Excel category for report generation, they form a pair of adjoint functors (adjoint functors).

A **functor** refers to a "structure-preserving" mapping that associates each object in Domain A with an object in Domain B. Category theory primarily addresses problems through the concept of functors. Specifically, when solving a problem, it is first generalized into a functor mapping issue. By resolving a comprehensive set of related problems encompassed by this functor mapping, specific individual problems are indirectly addressed. This approach of enlarging problems might seem risky, but its success hinges on the domain exhibiting mathematically well-defined and reliable scientific laws. As one proponent remarked, **"Nothing is more practical than a good theory."** The reversible computing theory expands our solution space when tackling problems, revealing numerous previously unimaginable possibilities.

Some may lose interest in reversible theory, perceiving it as an abstract academic concept detached from engineering practice. However, the father of machine learning, Vapnik, once noted that **"A good theory is nothing but practical."** The reversible computing theory broadens our problem-solving perspective and reveals unprecedented potential capabilities. Guided by reversible computing theory, the Nop platform achieves significant results with minimal technical effort (currently around 100,000 lines of code). It captures uniform construction patterns within structural spaces and defines a feasible path toward intelligent low-code development. With this theory, we can clearly envision our direction for advancement and understand our current position. In the coming years, terms like differential (Delta), reversible, and generative will increasingly appear across various technical domains. Their comprehensive application will inevitably lead us to embrace reversible computing theory.

> An interesting thing is that the forward pass formula of deep learning theory corresponds to `Y = Sigma(W * X + B) + Delta`. Considering residual connections, the deep learning formula becomes identical to the construction formulas of reversible computing theory. In solving problems with reversible computing itself, multiple layers of nested models are inevitably involved, much like the multi-layer neural networks in deep learning.

# Build Nop Platform Exchange Group

In the exchange group, I often receive feedback: "Oh, so it's like that." This is quite normal; one cannot understand what they do not comprehend. Actually operating the development examples of the Nop platform might help us better understand reversible computing theory. Only when specific business needs require customization of functions or mechanisms from the platform (or existing foundational products) will one appreciate the significant differences between the Nop platform and all other公开 technologies.

The open-source addresses for Nop Platform are as follows:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [cloud/entropy/nop-entropy](https://github.com/cloud/entropy/nop-entropy)
- Development Examples: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Introduction to Reversible Computing Principles and Nop Platform, Q&A - Bilibili Video](https://www.bilibili.com/video/BV1u4y1w7kX/)

It is suggested that VueVue standardize the logic for model customization. This would facilitate the addition of new model types in the future.

