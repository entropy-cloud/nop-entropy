# About "Why XLang is an Innovative Programming Language" - Q&A

In my previous article [Why XLang is an Innovative Programming Language](https://mp.weixin.qq.com/s/O4VeA7Dw8cRF7HTHxi6pNw), I introduced the design philosophy of the XLang language and explained why it qualifies as a innovative programming language. The reason is that it creates a new structural space in which invertible computation theory's proposed formula `Y = F(X) + Delta` can be easily implemented.

This article serves as further clarification on some feedback received after the initial publication.

---

## 1. How to Compress Delta Calculation into Compilation with XLang?

### Question:
To achieve property inheritance, UIOTOS made a lot of special designs and introduced a lot of related code into the runtime engine for property inheritance. However, if we base our implementation on the XLang language, can we compress delta calculation entirely into the compilation phase? If so, will the runtime engine still need to know about component structures?

### Answer:
Yes, by leveraging the XLang language, we can compress all delta calculations into the compilation phase. The runtime engine only needs to be aware of the component hierarchy structure and does not require any knowledge of delta decomposition or merging techniques.

---

## 2. Key Features of UIOTOS

UIOTOS is a no-code platform specifically designed for the IoT domain. It introduces a container component within this framework, allowing existing pages to be applied in this container. By utilizing the property mechanism, it can overlay properties of an inherited page without re-implementing the entire page. This allows for flexible customization while maintaining the original structure.

### Code Example:
```json
{
  "type": "container",
  "baseUrl": "a.page.json",
  "overrideProps": {
    "form/title": "sss",
    "actions/0/label": "vvv"
  }
}
```

This code snippet demonstrates how to override specific properties of a base page using the `overrideProps` mechanism. By following this structure, developers can easily customize any nested content within the page using a syntax similar to JsonPath.

---

## 3. Difference Between UIOTOS and Traditional Component Frameworks

In traditional component frameworks, property inheritance is typically handled by introducing a special container (`container`) component. While this approach allows for some level of customization, it often requires developers to manually manage how properties are inherited and applied across components.

UIOTES, on the other hand, takes this a step further by fully encapsulating the property inheritance mechanism within its core framework. This means that:

1. UIOTOS can override any property of an inherited page without altering its structure.
2. Customization is more flexible due to built-in property management.
3. The difference between a base component and its child becomes less relevant in terms of implementation.

---

## 4. How Overwrite Mechanism Works

The overwrite mechanism in UIOTES allows for arbitrary property adjustments at the component level without requiring any changes to the base page's structure. This is achieved by recording each adjustment within the `overwrite` section of a component's configuration.

### Code Example:
```json
{
  "component": "MyComponent",
  "version": "1.0",
  "properties": {
    "a": 1 // Direct property setting
  },
  "overwrite": [
    "Actions to modify the component in the visualization editor"
  ]
}
```

In this example, developers can adjust any number of properties for a specific component by specifying them within the `properties` section. For more complex adjustments that require dynamic behavior or conditional logic, they can utilize the `overwrite` mechanism.

---

## Summary

UIOTES's property inheritance mechanism provides a powerful way to handle delta calculations during compilation rather than runtime. By encapsulating all necessary logic within its framework, UIOTES simplifies property management while maintaining full flexibility for developers.

```markdown

### Component Definition

```xml
<component x:schema="component.xdef">
  <import from="comp:MyComponent/1.0.0"/>
</component>

<component name="MyComponentEx" x:extends="comp:MyComponent/1.0.0">
  <props>
    <prop name="a" x:override="remove"/>
    <prop name="b"/>
  </props>

  <template x:override="merge">
    Here can only show Delta correction part

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
```



```javascript
function loadDeltaModel(path) {
  rootNode = VirtualFileSystem.loadXml(path);
  for each node with x:extends attribute  // Recursively traverse rootNode and its child nodes
    baseNode = loadDeltaNode(node.removeAttr('x:extends'));
    genNodes = processGenExtends(node);

    for each genNode in genNodes
      baseNode = new DeltaMerger().merge(baseNode, genNode);
    node = new DeltaMerger().merge(baseNode, node);

  processPostExtends(node);
  return node;
}
```



```javascript
DslNodeLoader.loadDeltaModel("comp:MyComponent/1.0.0") returns XNode which is the final merged node containing no x name space attributes and child nodes.
```



The Loader can be seen as a just-in-time compiler that performs structural transformations when loading model files. This transformation is considered part of the compilation process.



In the structure layer, not the object layer, Delta operations are defined.

> "Language boundaries define our world's boundaries." Reverse computation theory further explains: **A programming language defines a software structure space, and various mechanisms based on existing structures to produce new structures for reuse resemble this structural transformation.**



A practical DSL must address scalability concerns by incorporating decomposition, merging, and reuse mechanisms. However, most current DSL developers are accustomed to defining Delta operations within the object layer, resulting in isolated, ad-hoc designs lacking generality and internal consistency.

XLang offers a comprehensive, standardized approach to address all scalability issues. The DSL engine only needs to handle minimal runtime concerns. XLang operates entirely during compilation (model analysis and loading), not at runtime.

The key lies in **XLang implementing Delta merging operations within the object layer's structure layer**. The structure layer refers to the XNode level, similar to Lisp's S-expressions, which inherently lack meaning on their own. This meaninglessness is what enables **XLang's Delta merging operations to demonstrate their generality and internal consistency.

For example:
- Spring's `beans.xml` serves as a DSL in the component deployment domain.
- Spring 1.0 introduced the `parent` attribute for inheritance, along with an `Import` syntax for file decomposition and reuse.
- Spring 2.0 introduced custom nodes to streamline complex configuration of structure beans.
- Spring Boot introduced `@ConditionalOnProperty`, enabling selective enablement of beans via configuration.

To implement these functionalities, Spring's core framework requires specialized handling through dedicated code.

```xml
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:myns="http://www.example.com/schema/myns"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans.xsd
           http://www.example.com/schema/myns
           http://www.example.com/schema/myns/myns.xsd">

    <import resource="classpath:config/services.beans.xml"/>

    <!-- Parent Bean -->
    <bean id="parentBean" class="com.example.ParentClass">
        <property name="commonProperty" value="commonValue"/>
    </bean>

    <!-- Child Bean, inherits configuration from parent bean -->
    <bean id="childBean" parent="parentBean">
        <property name="extProp" value="extValue"/>
    </bean>

    <!-- Custom namespace configuration for beans -->
    <myns:customBean id="customBean" customProperty="customValue"/>
</beans>
```

```java
@Component
@ConditionalOnProperty(name = "mycomponent.enabled", havingValue = "true", matchIfMissing = false)
public class MyComponent {

    public MyComponent() {
        System.out.println("MyComponent is initialized!");
    }

    public void doSomething() {
        System.out.println("MyComponent is doing something!");
    }
}
```

* First, we notice that without introducing any extensible mechanisms, Spring 1.0 treats the definition of a bean as a comprehensive component packaging model. This means that any bean that can be configured using `get/set` methods and constructors can be described using the `beans.xml` DSL for its packing logic. In mathematical terms, Spring 1.0 defines a complete set of packing operations.

* XLang's approach is to introduce a set of Delta difference operations on top of an existing DSL. However, these **Delta operations** result in merging the `DSL + Delta` structure back into the original DSL structure. The interesting part is that Spring 2.0's custom namespace approach cannot be mapped back to Spring 1.0's syntax, meaning that a bean configured with Spring 2.0 syntax may not always work with Spring 1.0 syntax, even though Spring 1.0's syntax forms a complete set of operations. For instance, the custom namespace `<myns:customBean>` in Spring 2.0 triggers a NamespaceHandler in Java, whose logic can be arbitrarily complex and might even implicitly introduce order dependencies (bean declaration order could affect packing results), effectively breaking Spring 1.0's POJO-based packing design.

* The `<x:extends>` element can inherit existing DSL files, similar to Spring 1.0's `import` syntax.

* The `<x:gen-extends>` element uses Xpl template language to dynamically generate bean definitions. Here, the `<c:include>` element within Xpl can include external XNode nodes, serving as an alternative to Spring 1.0's `import` syntax.

* The `<x:gen-extends>` segment leverages Xpl custom tags to simulate Spring 2.0's custom namespace mechanism. However, Xpl tags function through code generation, regardless of how complex the logic may seem. As long as they can generate the expected bean configuration definitions, it works. For example, the `<myns:customBean>` tag might generate multiple bean definitions. The actual effectiveness depends on the tag function's output during runtime. The `<x:gen-extends>` element itself is executed at compile time.

```xml
<myns:customBean id="customBean" customProperty="customValue"
                  xpl:lib="/example/myns.xlib" />

This expands into:

<bean id="customBean" class="com.example.CustomBean">
    <property name="customProperty" value="customValue" />
    <property name="otherProperty" ref="otherBean" />
</bean>

<bean id="otherBean" class="com.example.OtherBean" />
```

* In XLang, sibling nodes can use the `<x:prototype>` attribute to define inheritance relationships. This replaces Spring 1.0's `parent` attribute for bean definitions. Additionally, during node merging in XLang, you can fine-tune the merging logic using `<x:override>`, determining whether to override, merge, or delete attributes. Importantly, XLang's mechanism allows this kind of control on any node, such as within a `<property>` node where you can specify `<x:prototype>` to inherit other property configurations. However, Spring's `parent` attribute is limited to bean definitions.

* The `<feature:on>` and `<feature:off>` attributes in XLang allow conditional loading of nodes. If the feature switch doesn't meet its conditions, the corresponding node is automatically deleted, effectively avoiding runtime execution. This mechanism replaces Spring Boot's conditional beans. Similarly, the `<feature:on>` attribute can be applied to any node, such as within a `<property>` node to conditionally configure an attribute. However, in Spring Boot, conditional switches only affect bean creation, without any declarative mechanism for runtime configuration.

* The `<x:extends>` element can inherit existing DSL files, mimicking Spring 1.0's `import` syntax.

* The `<x:gen-extends>` segment uses Xpl template language to dynamically generate bean definitions. Here, the `<c:include>` element within Xpl can include external XNode nodes, serving as an alternative to Spring 1.0's `import` syntax.

* The `<x:gen-extends>` segment leverages Xpl custom tags to simulate Spring 2.0's custom namespace mechanism. However, Xpl tags function through code generation, regardless of how complex the logic may seem. As long as they can generate the expected bean configuration definitions, it works. For example, the `<myns:customBean>` tag might generate multiple bean definitions. The actual effectiveness depends on the tag function's output during runtime. The `<x:gen-extends>` element itself is executed at compile time.

```xml
<myns:customBean id="customBean" customProperty="customValue"
                  xpl:lib="/example/myns.xlib" />

This expands into:

<bean id="customBean" class="com.example.CustomBean">
    <property name="customProperty" value="customValue" />
    <property name="otherProperty" ref="otherBean" />
</bean>

<bean id="otherBean" class="com.example.OtherBean" />
```

* In XLang, sibling nodes can use the `<x:prototype>` attribute to define inheritance relationships. This replaces Spring 1.0's `parent` attribute for bean definitions. Additionally, during node merging in XLang, you can fine-tune the merging logic using `<x:override>`, determining whether to override, merge, or delete attributes. Importantly, XLang's mechanism allows this kind of control on any node, such as within a `<property>` node where you can specify `<x:prototype>` to inherit other property configurations. However, Spring's `parent` attribute is limited to bean definitions.

* The `<feature:on>` and `<feature:off>` attributes in XLang allow conditional loading of nodes. If the feature switch doesn't meet its conditions, the corresponding node is automatically deleted, effectively avoiding runtime execution. This mechanism replaces Spring Boot's conditional beans. Similarly, the `<feature:on>` attribute can be applied to any node, such as within a `<property>` node to conditionally configure an attribute. However, in Spring Boot, conditional switches only affect bean creation, without any declarative mechanism for runtime configuration.

* XLang introduces a comprehensive set of mechanisms for extending and customizing the packing logic, all of which require specialized knowledge specific to component packaging in Spring frameworks. Once migrated to another runtime engine like Quarkus, these mechanisms must be reimplemented accordingly. For instance, Quarkus supports bean definitions similar to Spring's, but its extension system differs significantly from Spring's. As a result, after migrating away from Spring, you would need to recreate these extensions using Quarkus-specific APIs.

* In conclusion, Spring frameworks come with built-in mechanisms for increasing extensibility, all of which are tailored specifically for component packaging in the context of Spring. Migrating to another runtime engine like Quarkus requires reimplementing these mechanisms, as they are fundamentally different from Spring's. For example, after moving to Quarkus, you would need to redefine how components are packaged and managed using Quarkus-specific APIs.


The core of XLang is the ability to perform **delta computation** on either XML or JSON data. Instead of converting an XNode into a strong-typed BeanDefinition and then performing delta operations, it directly operates on XNodes at the node level. This capability makes it highly applicable to other DSLs like MyBatis's mapper files and Hibernate's hbm files.


### Similar Extendable Systems

Many domains face similar extensibility challenges. For instance, GraphQL introduced type extension syntax by Facebook.


## Example of GraphQL Extension Syntax

```graphql
type User {
  id: ID!
  name: String!
  email: String!
}

extend type User {
  age: Int
  email: String @deprecated(reason: "Use 'contactEmail' instead")
  contactEmail: String!
}
```

In the `graphql-java` package, this process involves parsing GraphQL into TypeDefinition and TypeExtensionDefinition before merging types at the object level.



The NopGraphQL framework leverages XMeta metadata modeling to define types. Unlike other systems that require manual type extension definitions, XLang's built-in difference mechanism allows for seamless type extensions without additional syntax. At runtime, no knowledge of type extensions is required; the system just works with the defined types.



In XLang's ecosystem, the **Loader** abstract class holds significant importance because it handles the conversion from various DSLs to XLang, enabling Delta operations on XNodes. This abstraction allows third-party frameworks like MyBatis and Hibernate to integrate effortlessly by providing their own specific Loader implementations.



A typical model loader might look like this:

```
Loader :: Path -> Model
```



When designing for universality, it's essential to consider not just immediate needs but also future evolution. Programming is no longer limited to addressing only current requirements; it must encompass all possible scenarios. This is where **universal applicability** comes into play.



The Loader can be seen as a generator that transforms input into output. For example:

```
Loader :: Possible Path -> Possible Model
Possible Path = deltaPath + stdPath
```

Here, `stdPath` refers to the standard path (e.g., `main.wf.xml`), while `deltaPath` is the customized path (e.g., `/_delta/a/main.wf.xml`). The Loader automatically identifies and uses these paths without modifying existing business logic.



The Loader's role extends beyond mere data handling. It abstracts the complexity of managing Delta operations, allowing for a clear separation between what needs to be customized and what can remain unchanged. This abstraction also makes it easier to integrate with other systems, ensuring scalability and adaptability.



Programming should not confine itself to current requirements but must account for future developments. By designing with the entire universe of possibilities in mind, we ensure that our solutions remain relevant and adaptable over time. The goal is no longer about addressing specific needs but about creating a foundation that can accommodate all future challenges.



```
Loader<Possible Path> = Loader<deltaPath + stdPath>
                          = Loader<deltaPath> x-extends Loader<stdPath>
                          = DeltaModel x-extends Model
                          = Possible Model
```

Here, the Loader composes multiple models (DeltaModel and Model) to create a unified model (Possible Model). This demonstrates how **extensible programming** allows for dynamic adjustments while maintaining core functionalities.



Loaders are particularly useful in managing multi-tenant systems. They can dynamically adjust configurations based on tenant-specific parameters, ensuring that each tenant's data is isolated and managed separately without altering the core system.

```
X = A + B + C
Y = A + B + D
  = X + (-C + D)
  = X + Delta
```

In the traditional software engineering framework, even if some form of incremental development is achievable, it often still requires special design of many extension points. Not all modifications can be handled through Delta methods, especially in traditional software engineering where incremental generally implies adding new functionality rather than reducing existing features. Delta customization allows us to **add while subtracting**.

Systems developed with XLang require no additional effort to support Delta customization. This significantly reduces productization development costs. For example, a core banking system can be packaged into a jar without modifying its underlying code. All custom modifications and incremental developments can be stored as separate Delta differences in the `resources/_vfs` directory. By switching between Deltas, multiple customized versions can be managed. The same mechanism applies to multi-tenant customization.

Delta customization enables precise attribute-level customization. Traditional software engineering typically provides only a limited number of pre-defined extension points, making fine-grained customization difficult. For example, if I want to define a specific attribute of a button, I often have to add an entirely new component or page. **Continuous customization** across all business aspects is challenging.

## Can XLang be introduced into existing standard systems?

> XLang is an innovative technology. Can it be applied to existing systems? When encountering customization requirements, we can use XLang to express differences and generate customized versions based on standard systems and Delta differences.

First, it must be clarified that differences need to be expressed in a Delta-aware structure. Traditional software uses general-purpose languages for expression, which defines its structural space. Languages like `Java` or `C#` are limited when expressing differences because they rely on general-purpose programming paradigms.

In object-oriented programming languages, the only difference mechanism available is inheritance. The Nop platform employs a "three-layer" architecture for its code generation, using the following approach:

```java
class NopAuthUser extends _NopAuthUser {

    // Additional methods can be added here, inheriting from base classes.
}

class _NopAuthUser extends OrmEntity {
}
```

This means that model-driven classes inherit from system-defined base classes. The outermost class is generated based on the code generation logic, while inner classes are derived directly from system-provided classes. This allows manually modified code to remain isolated from automatically generated code. During code generation, we follow these rules: **Files with underscores as prefixes and `_gen` directory files will be automatically overwritten only if they don't exist**. Other files are only generated if they don't already exist. When the model changes, we can regenerate without risking manual modifications being lost.

While object-oriented languages do not inherently support extensive difference mechanisms beyond inheritance, architectural layers allow for more flexible Delta implementation at the structural level. The simplest approach is to use XML/JSON/YAML configuration or model files wherever differences need to be managed. This is where XLang shines.

For example, in the provided image (`../tutorial/simple/images/solon-chain.png`), the Chain model is defined using a JSON file. Using XLang, we can replace `Chain.parseByUrl` with `ResourceComponentManager.loadComponentModel(path)` and store the JSON file in the `resources/_vfs` directory. The XLang Delta syntax (`x:extends`, `x:post-extends`, `x:override`) allows precise customization.

The Nop platform provides a `nop-spring-delta` module that extends Spring's `beans.xml` and MyBatis' `mapper.xml` with Delta customization support. These XML files can be stored in the `resources/_vfs` directory for Delta-based customization.

Specific implementation steps are as follows:
```java
@Service
@ConditionalOnProperty(name = "nop.spring.delta.mybatis.enabled", matchIfMissing = true)
public class NopMybatisSessionFactoryCustomizer implements SqlSessionFactoryBeanCustomizer {
    @Override
    public void customize(SqlSessionFactoryBean factoryBean) {

        List<IResource> resources = ModuleManager.instance().findModuleResources(false, "/mapper", ".mapper.xml");

        if (!resources.isEmpty()) {
            List<Resource> locations = new ArrayList<>(resources.size());
            for (IResource resource : resources) {
                // Ignore auto-generated mapper files, they should exist as base classes only
                if (resource.getName().startsWith("_"))
                    continue;

                XDslExtendResult result = DslNodeLoader.INSTANCE.loadFromResource(resource);
                XNode node = result.getNode();
                node.removeAttr("xmlns:x");

                String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\\n" +
                        "<!DOCTYPE mapper\n" +
                        "        PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n" +
                        "        \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" + node.xml();
                locations.add(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)), resource.getPath());
            }
            factoryBean.addMapperLocations(locations.toArray(new Resource[0]));
        }
    }
}
```

* `ModuleManager.instance().findModuleResources(false, "/mapper", ".mapper.xml")` will search for `mapper.xml` files in the mapper directories across modules. This process automatically considers Delta directories. If a file with the same name exists in the `_delta/{deltaId}` directory within the VirtualFileSystem, it will be used instead. The Nop platform uses a VirtualFileSystem similar to Docker's layering, where parent layers can override child layers' files.

* When loading XML files using XLang's `DslNodeLoader`, the root node's `x:schema` attribute is read to retrieve the corresponding XDef definition. This allows for Delta merging based on the defined schema.

* After merging, the resulting XNode is serialized back into XML format and added to MyBatis' factory bean. MyBatis itself does not require any modification; this extension only adds a new way to load mapper files.

## 4. Delta Mechanism in XLang

While using XLang's Delta mechanism can enhance flexibility and customization, it may introduce additional overhead due to the nature of Delta merging and versioning.



## 1. Introduction to XLang Implementation
The XLang implementation supports delta merging and customization during model loading, primarily through the `ResourceComponentManager.loadComponentModel` function. This process includes:
- **Model Caching**: Models are cached for efficient reuse.
- **Model Compilation Tracking**: Dependencies are tracked so that the cache is invalidated when changes occur.


## 2. Optimization in Development Phase
In the development phase, techniques such as lazy loading, immediate compilation, and parallel loading can reduce system initialization time.


For production releases:
- Use Maven or similar packaging tools to bundle components during compilation.
- The resulting merged model is placed in the `_delta` directory.
- Mark the root node with `x:validated="true"`.

At runtime, the system prioritizes loading from the `_delta` directory, ensuring efficient delta merging. This process skips redundant merges when models are already validated.


Even complex delta merging logic does not impact runtime performance due to efficient validation and merging mechanisms.


XLang is essentially a set of annotations that the underlying engine understands. These annotations enable:
- **Delta Operations**: Defined using `x:extends` and `x:override`.
- **Customizable Syntax Rules**: Such as `x:extends` for extending existing models and `x:override` for customizing specific nodes.


In second-generation development:
- Models are managed in the `_vfs` virtual file system.
- XDef metadata ensures consistent DSL syntax representation.
- Tools like IDEA with dedicated plugins provide integrated support, including syntax highlighting and debugging capabilities.


During runtime:
- Delta changes are stored in the `_delta` directory.
- The final merged model is placed at the root level, marked with `x:validated="true"`.
- This ensures efficient delta merging without redundant operations.


For detailed debugging:
- Review the contents of the `_delta` directory for merged results.
- Use `x:dump="true"` to log full details of delta operations and node origins.

Refer to [debug.md](../dev-guide/debug.md) for comprehensive debugging information.

