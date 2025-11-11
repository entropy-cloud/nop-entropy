
# Validation Model

In the Nop platform, you can validate request data or the state of business objects via a validator model.

Video: [How to embed the Validator DSL into a program to implement validation logic](https://www.bilibili.com/video/BV1cs4y1k7pN/)

Specific approach:

## Define a validation model

Define validation logic in the [validator.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-quarkus-demo/src/main/resources/_vfs/nop/demo/validator/process-card.validator.xml)
model file. A validator can be divided into multiple check steps,
each corresponding to a condition. When the condition is not met, the corresponding error code and error message are thrown.

The exact format of the conditions is defined by the metamodel [filter.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/query/filter.xdef).
You can write nested conditions such as and/or/not, and use eq/gt/ge for comparisons. The name attribute corresponds to variables in the context and their properties.

When invoking the validator model, you need to pass in the context object.

```xml

<validator fatalSeverity="100" x:schema="/nop/schema/validator.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <check id="checkTransferCode" errorCode="test.not-transfer-code" errorDescription="The scanned code is not a transfer code">
        <eq name="entity.flowMode" value="1"/>
    </check>
</validator>
```

## Invoke the validator model in Java

In Java, you can call the validator model via the helper function runValidator on the BizValidatorHelper class. vars is the context object to pass in.
When the validator model evaluates conditions, the name attributes refer to variables in vars and their properties.

```
Map<String,Object> vars = new HashMap<>();
vars.put("entity", myEntity);
BizValidatorHelper.runValidator("/nop/demo/validator/process-card.validator.xml",
                vars, context);
```

## Invoke the validator model in a Biz model

Backend service functions in the Nop platform are not necessarily implemented in Java classes. In no-code development, backend service functions can be written in xbiz model files. Biz models support online editing and dynamic loading, allowing you to add backend GraphQL service functions without downtime and have them take effect immediately.

For example, in the [Demo.xbiz](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-quarkus-demo/src/main/resources/_vfs/nop/demo/model/Demo/Demo.xbiz)
model, the functions
testValidator2 and testValidator3 are functionally equivalent to the testValidator function defined in the [DemoBizModel](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-spring-simple-demo/src/main/java/io/nop/demo/biz/DemoBizModel.java)
class.

The testValidator3 function uses the `biz:RunValidator` tag function to load an external validator model file and execute the validation logic. The advantage is that, in the future, you can modify the validation logic via the Delta customization mechanism without changing the Demo.xbiz file.

```xml

<biz:RunValidator xpl:lib="/nop/core/xlib/biz.xlib"
                  validatorPath="/nop/demo/validator/process-card.validator.xml"
                  obj="${{entity,firstProductionOrder:entity.productionOrder,firstMaterial}}"/>

```

> Biz models are in XML format, so they can be designed online with a visual designer. For specific Actions, visual drag-and-drop can be used.
> For example, treat the `<biz:RunValidator>` tag as a component; validatorPath and other attributes are component properties. You can drag the corresponding component from the panel into the Action container to achieve visual orchestration.

## Embed the Validator DSL into a Biz model via metaprogramming

The testValidator2 function in the Demo.xbiz file demonstrates another way to run Validator logic. It embeds the Validator model into the Biz model via a macro tag.
When compiling the macro tag, the contents of the corresponding node are passed to the ValidatorParser for parsing to obtain a ValidatorModel; at runtime, the ValidatorModel, stored as a global variable, is used directly.

```xml

<biz:Validator xpl:lib="/nop/core/xlib/biz.xlib" fatalSeverity="100"
               obj="${{entity,firstProductionOrder:entity.productionOrder,firstMaterial}}">

    <check id="checkTransferCode" errorCode="test.not-transfer-code"
           errorDescription="The scanned code is not a transfer code">
        <eq name="entity.flowMode" value="1"/>
    </check>
</biz:Validator>
```

## Validator configuration

* By default, when a validator runs, it collects all check errors and then throws an exception with the messages at once. If you need to throw as soon as an error is found, configure the fatalSeverity attribute; when a check's severity >= fatalSeverity, execution is interrupted and an exception is thrown immediately.

* errorCode is the error code. You can configure its corresponding error message in i18n internationalization files, which will override the errorDescription setting.

* In errorDescription, you can reference error parameters in the form `{paramName}`. Use errorParams="a=expr,b" to capture variables from the context as error parameters.

```
    <check id="checkMaterial" errorCode="test.inconsistent-material"
           errorDescription="The material for the scanned transfer code is inconsistent: materialId={materialId}" errorParams="materialId=entity.materialId" severity="100">
        <eq name="entity.materialId" valueName="firstMaterial.materialId"/>
    </check>
```

## Extend validation rules

You can define extension tags in the /nop/core/xlib/biz!check.xlib tag library. They can then be used within check blocks.

```xml
    <check id="checkStatus" errorCode="test.invalid-status" errorDescription="Invalid status code">
        <biz:InDict name="entity.status" dictName="core/active-status" />
    </check>

```

## Model registration

Via [register-model.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-core/src/main/resources/_vfs/nop/core/registry/validator.register-model.xml)
you can register loaders for custom models in the Nop platform. This allows the unified ResourceComponentManager.loadComponentModel function to load all types of models registered in the platform.

```xml

<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="orm">
    <loaders>
        <xdsl-loader fileType="validator.xml" schemaPath="/nop/schema/validator.xdef"/>
    </loaders>
</model>
```

In register-model.xml, you register which metamodel corresponds to files with specific suffixes, such as the validator.xml suffix.

* Model files with multiple suffixes can be parsed into the same model object. For example, an API model can be defined via an api.xml file or via an Excel model file.

```xml

<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="api">
    <loaders>
        <xlsx-loader fileType="api.xlsx" impPath="/nop/graphql/imp/api.imp.xml"/>
        <xdsl-loader fileType="api.xml" schemaPath="/nop/schema/api.xdef"/>
    </loaders>
</model>
```

<!-- SOURCE_MD5:b4feab37e709b84f246a45eba2d8ce17-->
