# 验证模型

在Nop平台中对请求数据或者业务对象状态进行验证可以通过validator模型进行。

视频: [如何将Validator DSL嵌入到程序中实现验证逻辑](https://www.bilibili.com/video/BV1cs4y1k7pN/)

具体做法:

## 定义验证模型

在[validator.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-quarkus-demo/src/main/resources/_vfs/nop/demo/validator/process-card.validator.xml)
模型文件中定义验证逻辑。validator内部可以分为多个check步骤，
每个步骤对应一个判断条件，条件不匹配的时候将抛出对应的异常码和异常消息。

具体判断条件的格式由元模型[filter.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/query/filter.xdef)
规定。
在其中可以写and/or/not等嵌套条件，可以通过eq/gt/ge等实现比较判断。name属性对应于上下文环境中的变量以及变量上的属性。

在调用validator模型的时候需要传入上下文环境对象。

```xml

<validator fatalSeverity="100" x:schema="/nop/schema/validator.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <check id="checkTransferCode" errorCode="test.not-transfer-code" errorDescription="扫入的码不是流转码">
        <eq name="entity.flowMode" value="1"/>
    </check>
</validator>
```

## 在Java中调用validator模型

在Java中可以通过BizValidatorHelper类上的帮助函数runValidator来调用validator模型。vars为传入的上下文环境对象。
validator模型中执行条件判断时，所使用的name属性对应于vars中的变量及其属性。

```
Map<String,Object> vars = new HashMap<>();
vars.put("entity", myEntity);
BizValidatorHelper.runValidator("/nop/demo/validator/process-card.validator.xml",
                vars, context);
```

## 在Biz模型中调用validator模型

Nop平台中的后台服务函数不一定在Java类中实现。在进行无代码开发的时候，后台服务函数可以写在xbiz模型文件中，biz模型支持在线编辑、动态加载，
在不停机的情况下可以增加后台GraphQL服务函数，并立刻起效。

比如[Demo.xbiz](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-quarkus-demo/src/main/resources/_vfs/nop/demo/model/Demo/Demo.xbiz)
模型中定义的
testValidator2和testValidator3函数，它们的作用和[DemoBizModel](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-simple-demo/src/main/java/io/nop/demo/biz/DemoBizModel.java)
类中定义的testValidator函数的作用完全等价。

testValidator3函数通过`biz:RunValidator`标签函数来装载外部的validator模型文件，并执行验证逻辑。这样做的好处是未来可以通过Delta定制机制来修改验证逻辑，而不用修改Demo.xbiz文件。

```xml

<biz:RunValidator xpl:lib="/nop/core/xlib/biz.xlib"
                  validatorPath="/nop/demo/validator/process-card.validator.xml"
                  obj="${{entity,firstProductionOrder:entity.productionOrder,firstMaterial}}"/>

```

> Biz模型是XML格式，因此可以通过一个可视化设计器来在线设计Biz模型。对于具体的Action，也可以通过可视化拖拽的方式来实现。
> 例如，将`<biz:RunValidator>`标签看作是一个组件，validatorPath等是组件的属性，可以从面板中拖拽对应的组件到Action容器中，从而实现可视化的逻辑编排

## 通过元编程将Validator DSL嵌入到Biz模型中

Demo.xbiz文件中的testValidator2函数演示了另外一种执行Validator验证逻辑的方式。它通过宏标签将Validator模型嵌入到Biz模型中，
在编译宏标签的时候将会把对应节点的内容传入ValidatorParser中进行解析得到ValidatorModel，在运行期直接使用作为全局变量存在的ValidatorModel即可。

```xml

<biz:Validator xpl:lib="/nop/core/xlib/biz.xlib" fatalSeverity="100"
               obj="${{entity,firstProductionOrder:entity.productionOrder,firstMaterial}}">

    <check id="checkTransferCode" errorCode="test.not-transfer-code"
           errorDescription="扫入的码不是流转码">
        <eq name="entity.flowMode" value="1"/>
    </check>
</biz:Validator>
```

## Validator配置

* 一般情况下validator执行的时候会收集所有check的错误，然后一次性抛出异常消息。如果需要发现错误就抛出，则可以配置fatalSeverity属性，
  当check的severity\>=fatalSeverity的时候就会中断执行，直接抛出异常。

* errorCode对应的是异常码，可以在i18n国际化文件中配置异常码所对应的异常消息，它会覆盖errorDescription配置

* 在errorDescription中可以通过`{paramName}`这种形式来引用异常参数。通过errorParams="a=expr,b"这种形式来捕获上下文环境中的变量信息作为异常参数。

```
    <check id="checkMaterial" errorCode="test.inconsistent-material"
           errorDescription="扫入的流转码物料不一致:物料ID={materialId}" errorParams="materialId=entity.materialId" severity="100">
        <eq name="entity.materialId" valueName="firstMaterial.materialId"/>
    </check>
```

## 扩展验证规则

在/nop/core/xlib/biz!check.xlib标签库中可以定义扩展标签。然后在check段中就可以使用。

```xml
    <check id="checkStatus" errorCode="test.invalid-status" errorDescription="错误的状态码">
        <biz:InDict name="entity.status" dictName="core/active-status" />
    </check>

```

## 模型注册

通过[register-model.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-core/src/main/resources/_vfs/nop/core/registry/validator.register-model.xml)
可以将自定义模型的加载器注册到Nop平台中，这样通过统一的ResourceComponentManager.loadComponentModel函数就可以加载平台中注册的所有种类的模型。

```xml

<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="orm">
    <loaders>
        <xdsl-loader fileType="validator.xml" schemaPath="/nop/schema/validator.xdef"/>
    </loaders>
</model>
```

register-model.xml中注册特定后缀的文件，比如validator.xml后缀，对应于哪个元模型。

* 多种后缀的模型文件可以解析得到同一种模型对象。比如api模型可以采用api.xml文件定义，也可以通过Excel模型文件来定义。

```xml

<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="api">
    <loaders>
        <xlsx-loader fileType="api.xlsx" impPath="/nop/graphql/imp/api.imp.xml"/>
        <xdsl-loader fileType="api.xml" schemaPath="/nop/schema/api.xdef"/>
    </loaders>
</model>
```
