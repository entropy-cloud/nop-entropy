# Nop Getting Started - How to Extend the Return Object of a Service Function via Configuration

Walkthrough video: [https://www.bilibili.com/video/BV12KZSYNEyT/](https://www.bilibili.com/video/BV12KZSYNEyT/)

The Nop platform’s service framework is the next-generation RPC framework NopGraphQL. Unlike typical REST frameworks, NopGraphQL adopts a minimal information expression architectural design and generalizes GraphQL’s task decomposition and composition capabilities to all RPC invocation scenarios. A POJO service function (with just a few annotations added) can simultaneously support multiple invocation modes such as GraphQL, REST, and gRPC.

Powered by NopGraphQL, back-end services built with the Nop platform have scalability far beyond ordinary Web services. This article explains how to extend a service function’s return value via configuration without modifying DTO classes or service functions.

> For detailed design insights on NopGraphQL, see [Why Is GraphQL Strictly Superior to REST in the Mathematical Sense?](https://mp.weixin.qq.com/s/7Ou1h7NwyI4eAX4m_Zbftw), [The Road to Freedom in Business Development: How to Break Free from Framework Constraints and Achieve True Framework Neutrality](https://mp.weixin.qq.com/s/v2_x4gre4uMfz3yYNPe9qA)

Implementing service functions with NopGraphQL is very simple; it uses fewer annotations and implicit conventions than SpringMVC. For example, in the following example we define a query operation:

```java
@BizModel("Demo")
public class DemoBizModel {
  @BizQuery
  public CustomObj testCustomObj(@Name("name") String name) {
    CustomObj obj = new CustomObj();
    obj.setName(name);
    obj.setStatus(1);
    return obj;
  }
}
```

* The `@BizModel` annotation defines the service object name. Multiple different BizModel classes can correspond to the same service object; their service functions are aggregated and exposed together.
* The `@BizQuery` annotation denotes a query operation; `@BizMutation` denotes a mutation. Mutation operations automatically open a transactional context, so you generally do not need to manually annotate with `@Transactional`.
* The `@Name` annotation specifies the parameter name of the service function. Unlike SpringMVC, NopGraphQL does not assume it is invoked in a Web environment, so all concepts are web-agnostic. When invoked via REST, parameters can be passed through URL parameters or via the HTTP JSON body.
* There is no need to specify a REST endpoint on the service function. In REST mode, NopGraphQL uses a fixed endpoint pattern of `/r/{bizObjName}__{bizAction}`. The return value is automatically wrapped in an `ApiResponse<T>` object.

In the example above, CustomObj has two properties: name and status. Now we want to return a `status_label` field corresponding to the Chinese description of the status property. For example, if status is 1, then status_label returns “已启用” (Enabled); if status is 0, it returns “已禁用” (Disabled).

To achieve this in traditional RPC or Web frameworks, you’d typically need to modify the service function’s source code to load the dictionary and implement the text translation, and also proactively add a `status_label` field to the CustomObj result object.
However, in NopGraphQL, we do not need to modify the testCustomObj function or the CustomObj class. The logic to produce `status_label` follows deterministic rules as a derived computation, which is essentially decoupled from how the service function constructs CustomObj.
The service function only needs to know how to load and construct the CustomObj object—it does not need to compute every returned property. In the NopGraphQL engine, the return value of a service function is not the DTO data sent directly to the frontend. The returned object is further post-processed by the GraphQL engine, various DataLoaders are executed, and their results are finally composed into a JSON payload.

To extend the return object and add the status_label property, we only need to mark CustomObj as a BizObject and add a Meta metadata definition under the model directory.

```java
@BizObjName("CustomObj")
public class CustomObj {
  private String name;
  private int status;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public boolean isActive(){
    return status == 1;
  }
}
```

```xml
<!--  resources/_vfs/nop/demo/model/CustomObj/CustomObj.xmeta -->
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <props>

    <prop name="name" displayName="名称" queryable="true" insertable="true" updatable="true">
      <schema type="String"/>
    </prop>

    <prop name="status" displayName="状态" queryable="true" insertable="true" updatable="true">
      <schema type="Integer"/>
    </prop>

    <prop name="status_label" displayName="状态文本">
      <schema type="String"/>
      <getter>
        <c:script><![CDATA[
            if(entity.status == 1)
                return "已启用";
            return "已禁用";
        ]]></c:script>
      </getter>
    </prop>
  </props>
</meta>
```

* In the XMeta file, you can add a custom property status_label and write its computation logic in the getter section.
* Fields not defined in the XMeta file will not be returned to the frontend even if they exist on CustomObj. For example, the CustomObj has an isActive function, but since the `CustomObj.xmeta` file does not define an active field, the response will not include this property.

Once the XMeta DSL model is enabled, you can further leverage the Nop platform’s metaprogramming capabilities to dynamically generate extension logic using built-in compile-time code generators such as `x:gen-extends` and `x:post-extends`.

```xml
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef"
      xmlns:xpl="xpl" xmlns:meta-gen="meta-gen" xmlns:c="c">

    <x:post-extends>
        <meta-gen:DefaultMetaPostExtends xpl:lib="/nop/core/xlib/meta-gen.xlib"/>
    </x:post-extends>

    <props>

        <prop name="name" displayName="名称" queryable="true" insertable="true" updatable="true">
            <schema type="String"/>
        </prop>

        <prop name="status" displayName="状态" queryable="true" insertable="true" updatable="true">
            <schema type="Integer" dict="core/active-status"/>
        </prop>
    </props>
</meta>
```

In the example above, we did not explicitly add the `status_label` field; instead, we specified the dictionary `core/active-status` for the status field. The `x:post-extends` section invokes the `<meta-gen:DefaultMetaPostExtends>` tag, which introduces a series of compile-time generation rules that automatically scan all fields configured with dict and generate the corresponding label field for each of them.

A complete example can be found in the sample project [`nop-spring-simple-demo`](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-spring-simple-demo)

All auto-generated database entity objects in the Nop platform are already marked as BizObject, and their corresponding XMeta metadata description files are generated automatically. Therefore, the input and output objects for all CRUD operations can be extended via XMeta.
<!-- SOURCE_MD5:e99583fbfe65c853a5d5cc26a1c0b540-->
