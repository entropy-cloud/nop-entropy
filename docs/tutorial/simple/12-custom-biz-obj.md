# Nop入门-如何通过配置扩展服务函数的返回对象

讲解视频：[https://www.bilibili.com/video/BV12KZSYNEyT/](https://www.bilibili.com/video/BV12KZSYNEyT/)

Nop平台的服务框架是下一代RPC服务框架NopGraphQL。NopGraphQL与普通的REST框架不同，它采用了最小化信息表达的架构设计，将GraphQL的任务分解组合能力推广到了所有RPC服务调用场景。一个POJO的服务函数（只需增加少量注解）可以同时支持GraphQL、REST和gRPC等多种调用方式。

依托于NopGraphQL，采用Nop平台实现的后端服务拥有远超普通Web服务的可扩展性。本文将介绍在不修改DTO类也不修改服务函数的情况下，如何通过配置来扩展服务函数的返回值。

> 关于NopGraphQL的设计详解，参见[为什么在数学的意义上GraphQL严格的优于REST？](https://mp.weixin.qq.com/s/7Ou1h7NwyI4eAX4m_Zbftw),[业务开发自由之路：如何打破框架束缚，实现真正的框架中立性](https://mp.weixin.qq.com/s/v2_x4gre4uMfz3yYNPe9qA)

使用NopGraphQL实现服务函数非常简单，它使用的注解个数和隐含约定远少于SpringMVC。比如，在下面的示例中，我们定义了一个查询操作

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

* `@BizModel`注解用于定义服务对象名。多个不同的BizModel类可以对应于同一个服务对象，它们的服务函数会聚合到一起对外暴露。
* `@BizQuery`注解表示这是一个查询操作，如果是`@BizMutation`则表示是修改操作。修改操作会自动打开事务环境，一般不需要手工标注`@Transactional`。
* `@Name`注解用于指定服务函数的参数名。与SpringMVC不同，NopGraphQL并没有假定它在Web环境中调用，因此所有概念都与Web无关。具体通过REST服务调用时，可以通过url参数传递参数，也可以通过HTTP JSON Body来传递参数。
* 服务函数上无需指定REST链接。NopGraphQL在REST调用模式下固定使用`/r/{bizObjName}__{bizAction}`这一链接模式。返回值会被自动包装为`ApiResponse<T>`对象。

在上面的示例中，CustomObj具有两个属性name和status。现在要求返回一个`status_label`字段，它对应于status属性的中文描述。比如，如果status为1，则status_label返回“已启用”，如果status为0，则返回“已禁用”。

要实现这一功能，对于传统的RPC框架或者Web框架，肯定是需要修改服务函数的源码，在其中加载字典表然后实现文本翻译。返回的结果对象CustomObj上也需要主动增加`status_label`字段。
但是，在NopGraphQL中，我们无需修改testCustomObj函数，也不需要修改CustomObj类。生成`status_label`的逻辑是一个按照确定性的规则进行衍生计算的逻辑，所以它本质上与服务函数中如何构建CustomObj的逻辑是解耦的。
服务函数只需要知道如何加载、构建CustomObj对象就好了，并不需要将所有返回属性都计算出来。**在NopGraphQL引擎中，服务函数的返回值并不是是直接返回给前端的DTO数据**。服务函数返回的结果对象还需要进行GraphQL引擎的后处理，执行各种各样的DataLoader，最终才会将DataLoader的返回结果合成为一个JSON数据。

如果要扩展返回值对象，增加status_label属性，我们只需要标记CustomObj是一个BizObject，然后在model目录下增加一个Meta元数据定义。

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

* 在XMeta文件中可以增加一个自定义属性status_label，通过getter段来编写它的计算逻辑。
* 在XMeta文件中没有定义的字段，即使在CustomObj上存在，也不会返回到前台。比如CustomObj对象上具有isActive函数，但是`CustomObj.xmeta`文件中并没有定义active字段，返回结果中也就不会包含这个属性。

一旦启用了XMeta这个DSL模型，那么就可以进一步利用Nop平台提供的元编程能力，通过`x:gen-extends`和`x:post-extends`等内置的编译期代码生成器来动态生成扩展逻辑。

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

在上面的示例中，我们没有显式添加`status_label`字段，而是为status字段指定了字典表`core/active-status`。`x:post-extends`段调用了`<meta-gen:DefaultMetaPostExtends>`标签，这个标签引入了一系列的编译期生成规则，会自动查找所有配置了dict的字段，逐个为它们生成对应的label字段。


完整的示例可以参见 [`nop-spring-simple-demo`](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-spring-simple-demo)这个示例项目



Nop平台中所有自动生成数据库实体对象都已经标记为BizObject，并且自动生成了对应的XMeta元数据描述文件。因此所有的增删改查操作的输入输出对象都是可以通过XMeta进行扩展的。




