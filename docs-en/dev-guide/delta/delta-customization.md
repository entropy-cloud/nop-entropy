# How to Implement Customized Development Without Modifying the Base Product Source Code

In the ToB market, software product development is often haunted by the “customization” curse. Typically, customized development requires extensive modifications to the product’s source code to meet the specific needs of specific users, which severely corrodes the generality of the product code. If the relationship between customized development and standardized product development cannot be properly balanced, it may seriously slow down the overall progress of the company’s products. Since competitiveness at the business level largely stems from differentiation, high-value mid-to-high-end customers inevitably have a large number of customization requirements—requirements that can be hard to abstract into a standardized, configurable pattern. To minimize the cost of conducting customized development alongside base product R&D, ideally customization should not modify the base product’s code. However, under current software engineering theory and general-purpose frameworks, achieving this is fraught with difficulties or incurs very high costs. In this article, I analyze the technical reasons why customized development gets into trouble and introduce how the Nop platform leverages the principles of Reversible Computation to offer an innovative customization capability, enabling application-layer code to gain fully incremental (delta-based) customization without any special design (such as pre-abstracted extension interfaces). The delta customization code is completely independent of the base product code; customizing the base product or Nop platform functionality requires no changes to the original code.

For concrete customization examples, see the sample project [nop-app-mall/app-mall-delta](https://gitee.com/canonical-entropy/nop-app-mall/tree/master/app-mall-delta). For the theory of Reversible Computation, see [Reversible Computation: Next-Generation Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026).

## I. The Predicament of Customized Development

Traditionally, we mainly adopt two technical approaches to address customized development:

## 1.1 Code Branching

The most common approach is to create a dedicated code branch for each customer and then periodically merge from the trunk. Based on our observations, once there are more than five branches, confusion arises easily—especially when the same team maintains multiple branches with major differences simultaneously—leading even to erroneous commits and releasing the wrong versions.

Base products are generally complex and have numerous dependencies. Each branch containing similar yet not identical copies of the base product’s code causes development environment maintenance costs to skyrocket. When issues arise and diagnosis is needed, it’s often hard to quickly determine whether the root cause lies in the modified base product code or in the newly developed customization code because the base product code has been frequently altered.

The base product typically has a large codebase, and the effort and expertise required to perform diff analysis during code synchronization are both significant. If a bug is fixed or new functionality is added in the base product, synchronizing these changes downstream often becomes a prolonged process that must be carried out by developers who clearly understand the reasons behind the changes. Conversely, when an excellent feature in a customization branch is to be reverse-extracted and merged back into the main trunk, peeling off general-purpose code from that customization branch is also quite complex. **Base product code and bespoke customization code get entangled and intermingled, lacking clear formal boundaries, easily forming spaghetti dependencies that are hard to disentangle.**

## 1.2. Configurability and Pluggability

Configurability and pluggability constitute the other major technical route for supporting customized development. A mature productized offering must be highly configurable, with a large number of customer demands abstracted into cross-combinations of configuration items. For demand variations that are hard to exhaustively enumerate upfront, if we can anticipate where changes will occur, we can reserve extension points (extension interfaces) in the base product and then inject special plugin implementations during customization.

The main problem with this approach is that predictions can be inaccurate. Especially when the product itself is immature, it’s possible that none of the anticipated variations occur, and changes happen in unanticipated places. This leads to an awkward situation: **when we need extensibility the most to reduce product evolution costs, it may not even exist.**

High flexibility typically comes with increased complexity and performance overhead at runtime. Some uncommon requirements may leave deep and disproportionate scars inside the base product, leaving later developers puzzled: why is there such a convoluted design here? The requirement is only one sentence—how does it translate into so many interfaces and implementation classes? If we consider specific business requirements as a logical path, then configurability amounts to embedding multiple logical paths into the product to form a crisscrossed network, controlled by numerous switches to enable specific path connections. Without global guiding principles and design planning, configuration itself easily becomes a new source of complexity—hard to understand and hard to reuse.

Based on existing software engineering theory, such as Software Product Line engineering, technical means to enhance software flexibility can be categorized into adaptation, replacement, and extension. They can all be seen as additions to the core architecture. However, customization is not always about adding new functionality; oftentimes it involves hiding or simplifying existing functionality. Current techniques struggle to efficiently achieve the goal of removing existing features.

## II. Reversible Computation Theory

Upfront predictions are unreliable; ex-post separation is costly. If we want lightweight customized development, then ideally the customization code and the base product code should be physically separated and, without reserved interfaces, a general mechanism should enable pruning and extension of base product functionality. To achieve this goal, we need to revisit the theoretical foundations of customized development.

Assume we have built a base product `X` with multiple components, expressed as:

```
   X = A + B + C
```

We aim to develop a target product `Y`, which also has multiple components:

```
   Y = A + B + D
```

**Developing product Y based on product X, at the abstract level, corresponds to establishing an operation from X to Y:**

```
   Y = A + B + D = (A + B + C) + (-C + D) = X + Delta
```

If we truly can avoid modifying the base product `X`, a natural theoretical conclusion is: **customization code amounts to a Delta correction applied to the base product.**

Furthermore, we can derive the following:

1. Delta should be a **first-class concept** in architectural design—so it can be independently identified, managed, and stored. Customization code should be physically separated from the base product code and versioned independently.

2. `X = 0 + X`. Any quantity applied to the identity element yields itself; therefore **a full set is a special case of Delta**. Delta’s definition and form need no special design at a theoretical level: any existing formal expression can be seen as a delta expression as long as we define the rules for delta operations.

3. `Y = X + Delta1 + Delta2 = X + (Delta1 + Delta2) = X + Delta`. **Delta should satisfy associativity**, allowing multiple Deltas to be merged independently of the base product—packaging multiple deltas into one.

4. `Delta = -C + D`. Besides new components, **Delta must include inverses** to enable pruning of the original system. Delta should be a mixture of additions, modifications, and deletions.

5. `Y = (A + dA) + (B + dB) + (C + dC) = A + B + C + (dA + dB + dC) = X + Delta`. If changes can occur anywhere in the original system, the Delta mechanism must collect changes across the system’s fine-grained parts and aggregate them into an overall Delta. **This implicitly requires the original system to have a stable coordinate system.** After `dA` separates from `A` to be stored in an independent Delta, it must retain some locating coordinates. Only then can the Delta, when combined with `X`, find the original structure `A` and integrate with it.

Some highly flexible SaaS products store form configurations and workflow configurations in database tables and achieve customized development per specific users by adjusting configurations. In this approach, the configuration tables and the primary keys of configuration items essentially form a coordinate system. Based on this, one can add version fields to configuration items to enable version management and even version inheritance.

Hot patch mechanisms offered by some software are essentially a Delta correction mechanism. Successful patch application relies on coordinate system positioning provided at the infrastructure level and a delta merge algorithm executed after locating. However, compared to customized development, hot updates impose lower structural requirements on patches: patches need not have relatively stable business semantics, and they may not correspond to source code directly understandable to developers.

Before Docker, virtual machine technology already supported incremental backups, but the VM-level deltas are defined in the binary byte space, where even a minor business change may lead to massive byte-level changes—highly unstable and devoid of business semantics, rarely of standalone value. **Docker, by contrast, defines delta merge rules in the file system space. Docker images have clear business semantics, can be dynamically constructed via the DockerFile DSL, and can be uploaded to a central registry for storage and retrieval—thus opening a complete technical route to application construction based on the delta concept.**

Reversible Computation theory posits that behind various delta-based technical practices lies a unified principle of software construction, expressible as:

```
  App = Delta x-extends Generator<DSL>
```

For a detailed introduction to Reversible Computation, see [Reversible Computation: Next-Generation Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026).

The Nop platform is a reference implementation of Reversible Computation. With Nop’s Delta customization mechanism, at zero extra cost we can achieve fully incremental customized software development. The next section details how this works in Nop.

## III. Delta Customization in the Nop Platform

All applications developed with the Nop platform are automatically delta-customizable. Using an e-commerce application as an example, we demonstrate how to add, modify, and delete functionality across various layers of the system without changing base product source code. See the sample code in [nop-app-mall/app-mall-delta](https://gitee.com/canonical-entropy/nop-app-mall/tree/master/app-mall-delta).

## 3.1 Dedicated Delta Module

All delta customization code can be stored in a dedicated module, such as [app-mall-delta](https://gitee.com/canonical-entropy/nop-app-mall/tree/master/app-mall-delta).

In the `app-mall-codegen` module, add the following calls to [gen-orm.xgen](https://gitee.com/canonical-entropy/nop-app-mall/blob/master/app-mall-codegen/postcompile/gen-orm.xgen), indicating that delta customization code will be generated under the `app-mall-delta` module:

```javascript
codeGenerator.withTargetDir("../app-mall-delta").renderModel('../../model/nop-auth-delta.orm.xlsx','/nop/templates/orm-delta', '/',$scope);
codeGenerator.withTargetDir("../app-mall-delta").renderModel('../../model/nop-auth-delta.orm.xlsx','/nop/templates/meta-delta', '/',$scope);
```

In other modules, such as `app-mall-app`, simply depend on the `app-mall-delta` module to customize built-in Nop platform features.

## 3.2 Delta Customization of Data Models

The `nop-auth` module is the Nop platform’s default access control module. Nop automatically generates ORM model definitions and GraphQL type definitions based on the data model [nop-auth.orm.xlsx](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/model/nop-auth.orm.xlsx). If we need to add fields to the system’s built-in user table, we can add a delta model [nop-auth-delta.orm.xlsx](https://gitee.com/canonical-entropy/nop-app-mall/blob/master/model/nop-auth-delta.orm.xlsx) containing only the tables and fields to be extended.

![delta-table.png](delta-table.png)

We added a `MALL_USER_ID` field to the `NopAuthUser` table, linking to the `LitemallUser` table defined in the `nop-app-mall` project.

1. The [Index] column for `MALL_USER_ID` must specify a unique identifier. Typically, choose values starting from “max index in the base model + 50” to avoid conflicts with newly added fields in the base model.

2. To ensure structural integrity in the model, we must include the primary key definition in the `NopAuthUser` table. To avoid duplicate code generation, add the `not-gen` tag in the [Tags] column, indicating that this field is defined in the base class and corresponding property definition code need not be generated.

3. Set the table’s [Object Name] to `io.nop.auth.dao.entity.NopAuthUser` to preserve the entity name defined in the base model so that existing code remains unaffected.

4. Set the table’s [Base Class] to `io.nop.auth.dao.entity.NopAuthUser` and the [Class Name] to `NopAuthUserEx`, so the generated entity class inherits from `NopAuthUser`.

If the delta model references entity classes defined in other modules, you must use the full entity name, such as `app.mall.dao.entity.LitemallUser`.

![external-table](external-table.png)

Since no code needs to be generated for these external tables, add the `not-gen` tag to the [Tags] of the `LitemallUser` table, and retain only the primary key definition for the table’s fields to satisfy model integrity checks.

![delta-config](delta-config.png)

**Note: appName must match the name of your customized module; otherwise customization will fail and runtime errors about duplicate entity definitions will occur.**

In the data model configuration, set `deltaDir=default`, so generated model files go to `/_vfs/_delta/{deltaDir}/{originalPath}`. During model loading, files under the delta directory are loaded first to override base product definitions.

The actual generated ORM model structure is:

```xml
<orm x:extends="super,default/nop-auth.orm.xml">
  <entities>
     <entity className="app.mall.delta.dao.entity.NopAuthUserEx" displayName="用户"
             name="io.nop.auth.dao.entity.NopAuthUser">
             ...
     </entity>
  </entities>
</orm>
```

Under this configuration, `entityDao` or `ormTemplate` will return `NopAuthUserEx` as the implementation type when creating entities, while preserving the entity name as `NoptAuthUser`.

```javascript
 IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
 NopAuthUserEx user = (NopAuthUser)dao.newEntity();

Or
 NopAuthUserEx user = (NopAuthUserEx) ormTemplate.newEntity(NopAuthUser.class.getName());
```

The generated entity class structure:

```java
class NopAuthUserEx extends _NopAuthUserEx{

}

class _NopAuthUserEx extends NopAuthUser{

}
```

In the extended entity class, you inherit all features of the base model’s entity class, and you can add new field information via the generated `_NopAuthUserEx` class.

If you want to streamline database fields by removing certain field definitions, simply add the `del` tag in the field’s [Tags] configuration. It generates the following configuration:

```xml
<orm>
   <entities>
      <entity name="io.nop.auth.dao.entity.NopAuthUser">
         <columns>
            <!-- x:override=remove indicates deletion of this field definition -->
            <column name="clientId" x:override="remove" />
         </columns>
      </entity>
   </entities>
</orm>
```

Using delta-based data models makes it easy to track database differences between the customized version and the base product version.

> The data model documentation can clearly annotate the reasons for customization and the time of changes, along with other supplementary information.

### 3.3 Delta Customization of the IoC Container

The Nop platform includes an IoC container, [NopIoC](https://zhuanlan.zhihu.com/p/579847124), compatible with Spring 1.0 configuration syntax.

#### 1. Conditional Switches
   On top of Spring 1.0’s XML syntax, NopIoC adds conditional assembly capabilities similar to Spring Boot. You can use configuration variable switches to enable or disable beans participating in assembly:

```xml
    <bean id="nopAuthHttpServerFilter" class="io.nop.auth.core.filter.AuthHttpServerFilter">
        <ioc:condition>
            <if-property name="nop.auth.http-server-filter.enabled" enableIfMissing="true"/>
        </ioc:condition>
        <property name="config" ref="nopAuthFilterConfig"/>
    </bean>
```

#### 2. Default Implementations
   NopIoC can provide a default implementation for a bean with a specified name. If another bean with the same name exists in the container, the default implementation is automatically ignored—similar to Spring Boot’s `ConditionOnMissingBean` mechanism.
   
```xml
<bean id="nopActionAuthChecker" class="io.nop.auth.service.auth.DefaultActionAuthChecker" ioc:default="true"/>

<!-- Beans marked with ioc:default="true" are overridden by same-named beans defined in other files -->
<bean id="nopActionAuthChecker" class="com.ruoyi.framework.web.service.PermissionService" />
```

You can also add `primary=true` to a new bean. Its priority will be higher than any bean not marked `primary`.

#### 3. x-extends Inheritance

NopIoC is more powerful because it supports the delta customization mechanism built into the XLang language. We can add a same-named `beans.xml` configuration file under the delta directory to override an existing configuration in the base product. For example, in the `app-mall-delta` module at `/_vfs/_delta/default/nop/auth/auth-service.beans.xml`:

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super">

    <bean id="nopAuthFilterConfig">
        <property name="authPaths">
            <list x:override="append">
                <value>/mall*</value>
            </list>
        </property>
    </bean>

</beans>
```

The above configuration indicates that we inherit the existing model (`x:extends="super"`) and then modify the `authPaths` property of the `nopAuthFilterConfig` bean by adding one item.

Beyond overriding bean configurations, we can remove bean configurations via delta customization. For example, when integrating Nop with the Ruoyi framework, we need to delete the built-in `dataSource` configuration:

```xml
    <bean id="nopDataSource" x:override="remove" />
```

See the specific configuration in [dao-defaults.beans.xml under the delta directory](https://gitee.com/canonical-entropy/nop-entropy/blob/master).

Delta customization is simple and intuitive—**it applies to all model files and can customize down to the finest granularity of individual properties.** Compared to the equivalent in Spring Boot, we find notable limitations in Spring Boot’s customization: first, to implement Bean exclusion and Bean override, Spring must add a lot of processing code in the engine and introduces many special usage patterns. Second, Spring’s customization mechanisms target single-bean configurations (e.g., disabling a bean) but lack suitable means to customize individual properties. Without good upfront planning, it’s hard to override existing bean definitions across the system in a simple way.

> Because the IoC container can search for matching beans by name, type, annotation, etc., and perform assembly, we generally do not need to additionally design a plugin mechanism.

> When starting in debug mode, NopIoC outputs all bean definitions to `/_dump/{appName}/nop/main/beans/merged-app.beans.xml`, where you can see the source location corresponding to each bean’s definition.

### 3.4 Delta Customization of GraphQL Objects

In the Nop platform, GraphQL services typically correspond to `BizModel` objects. For example, `NopAuthUser__findPage` refers to calling the `findPage` method on the `NopAuthUserBizModel` class. We can customize GraphQL services by overriding the `BizModel` registration class. Steps:

#### 1. Inherit an existing `BizModel` class, adding new service methods or overriding existing ones.

```java
public class NopAuthUserExBizModel extends NopAuthUserBizModel {
    static final Logger LOG = LoggerFactory.getLogger(NopAuthUserExBizModel.class);

    @Override
    protected void defaultPrepareUpdate(EntityData<NopAuthUser> entityData, IServiceContext context) {
        super.defaultPrepareUpdate(entityData, context);

        LOG.info("prepare update user: {}", entityData.getEntity().getUserId());
    }
}
```

#### 2. Override the original bean definition in `beans.xml`.
   
   ```xml
    <bean id="io.nop.auth.service.entity.NopAuthUserBizModel"
          class="app.mall.delta.biz.NopAuthUserExBizModel"/>
   ```
   
   Auto-generated bean definitions are marked `ioc:default="true"`, so registering a bean with the same `id` will override the default.

Besides extending existing `BizModel` classes, we can override service methods defined in Java objects via the `XBiz` model. For example, customize the `NopAuthUser.xbiz` file and add a method definition:

```xml
<biz x:schema="/nop/schema/biz/xbiz.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="super">

    <actions>
        <query name="extAction3" displayName="Test function 3 defined in the biz file">
            <source>
                return "result3"
            </source>
        </query>
    </actions>
</biz>
```

The Nop GraphQL engine automatically collects all biz files and beans annotated with `@BizModel`, and groups them by `bizObjName` to form the final service object. This approach is reminiscent of the [ECS Architecture (Entity-Component-System)](https://zhuanlan.zhihu.com/p/30538626) in game development. In such an architecture, uniquely identified objects are composed of stacked slices, so customization doesn’t necessarily require modifying original slices; instead, adding a new slice to override existing functionality suffices. Functions defined in `XBiz` files have the highest priority and override functions defined in `BizModel`.

## 3.5 Delta Customization of Front-End Pages

Front-end pages in Nop are mainly defined in two model files: `view.xml` and `page.yaml`. The former is the technology-neutral `XView` view outline model, using coarse-grained concepts—form, table, page, button—to describe page structure, sufficient for typical admin pages. The `page.yaml` model corresponds to the JSON schema of Baidu AMIS; in practice, the content delivered to the front end comes from `page.yaml`. Using the meta-programming mechanism `x:gen-extends`, `page.yaml` dynamically generates page content from the `XView` model.

By customizing these two model files, we can adjust form layouts, set display controls for individual fields, add or remove buttons on pages, and even completely override page content in the base product.

## 3.6 Delta Customization of Tag Functions

The Nop platform extensively uses the Xpl template language for code generation and meta-programming, and all script execution areas in executable models (such as workflow models) use Xpl. The Xpl template language has a tag library mechanism to encapsulate functions (each tag is akin to a static function). Tag library `.xlib` files can be customized via Delta. For example, we can customize [control.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master) to adjust the default display controls for field types, or customize [ddl.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/ddl/ddl_mysql.xlib) to fix SQL syntax in create-table statements for a specific database version.

## 3.7 Delta Customization of Rule Models, Report Models, etc.

All models in the Nop platform—workflow models, report models, rule models, etc.—are constrained by the XDef meta-model and comply with XDSL domain syntax rules (see [XDSL: General Domain-Specific Language Design](https://zhuanlan.zhihu.com/p/612512300)). Therefore, all models automatically support Delta customization: add model files under the `/_vfs/_delta/{deltaDir}` directory with corresponding paths to customize base product models.

Unlike typical report engines and workflow engines, Nop engines extensively use the Xpl template language as the executable script, allowing custom tag libraries for extension. For instance, typical report engines might offer built-in data loading mechanisms such as JDBC/CSV/JSON/Excel. If we want to add a new loading method, we typically need to implement specialized interfaces built into the engine and register them using special mechanisms; modifying the visual designer to support custom configurations is generally non-trivial.

In the `NopReport` model, we provide an Xpl template section named `beforeExecute`, which acts as an extension point based on a universal interface ([IEvalAction](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/IEvalAction.java)). In `beforeExecute`, we can introduce a new data loading mechanism as follows:

```xml
<beforeExecute>
   <spl:MakeDataSet xpl:lib="/nop/report/spl/spl.xlib" dsName="ds1" src="/nop/report/demo/spl/test-data.splx" />
</beforeExecute>
```

> By inspecting the XDef meta-model, it’s easy to discover which nodes are Xpl template configuration nodes—no need to define or understand special plugin interfaces.

Tag invocations are both function calls and easily parsed XML configurations. We can add an `XView` model file to automatically generate a visual editor for the `beforeExecute` section. If the platform already provides a visual designer for models, custom extensions can be easily achieved by customizing the designer’s corresponding model files.

Another approach is to utilize XDSL’s built-in extension property configuration. All Nop model files automatically support extension properties: beyond attributes and nodes defined in the XDef meta-model, namespaced attributes and nodes are, by default, not validated and are stored as extension properties (similar to allowing arbitrary annotations in Java classes). We introduce extension property nodes for configuration, then use the meta-programming mechanism `x:post-extends` to parse the extension configuration at compile time, dynamically generating the `beforeExecute` section. This approach requires no built-in “data source” concept in the report model and no special runtime interfaces in the report engine. It achieves integration with any external data source purely via **localized compile-time transformation**.

```xml
<x:post-extends>
   <xpt-gen:DataSetSupport/> <!-- Parses ext:dataSets extension config, dynamically generates code, and appends to beforeExecute -->
</x:post-extends>

<ext:dataSets>
   <spl name="ds1" src="/nop/report/demo/spl/test-data.splx" />
</ext:dataSets>

<beforeExecute> Other initialization code can be written here </beforeExecute>
```

## 3.8 Compile-Time Feature Switches

A highly configurable product should strive to evaluate feature switches at compile time to preserve runtime performance, simplifying the final generated code. In the Nop platform, all XDSL domain model files support `feature:on` and `feature:off` feature switches. For example:

```xml
<form id="view" feature:on="!nop.auth.use-ext-info"> ...</form>
```

You can set `feature:on` and `feature:off` on any XML node. `feature:on="!nop.auth.use-ext-info"` means the node exists only when the `nop.auth.use-ext-info` configuration variable is `false`; otherwise it is automatically removed.

Compared to Spring Boot’s conditional switches: Nop’s built-in `feature` switches can apply to any node in any model file. The model itself requires no special design for conditional switches, and the runtime engine needs no added code. Feature filtering is realized when loading XML. Spring Boot’s conditional switches, by contrast, require dedicated code and cannot be applied to other model files.

## IV. Conclusion

Based on Reversible Computation principles, the Nop platform implements a Delta customization mechanism, enabling comprehensive customization of both front-end and back-end functionality without modifying the base product at all.

Open-source repositories for the Nop platform:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible Computation Principles and Nop Platform Introduction & Q&A_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
<!-- SOURCE_MD5:e58134e9f62b5cd88af9209e28ad85ec-->
