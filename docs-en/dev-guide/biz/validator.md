  
  # Validation Model
  
  In the Nop platform, you can validate request data or business object states using the validation model. This can be done through the validator model.

  Video: [How to Embed Validator DSL into Your Program to Implement Validation Logic](https://www.bilibili.com/video/BV1cs4y1k7pN/)

  Steps to Follow:

  ## Defining the Validation Model

  The validation logic can be defined in the `[validator.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-quarkus-demo/src/main/resources/_vfs/nop/demo/validator/process-card.validator.xml)` file within the Nop demo project. The validator model internally consists of multiple check steps, each corresponding to a specific condition. When the condition does not match, it throws an associated error code and message.

  ## Defining Conditions

  The conditions for validation are defined by the meta-model `[filter.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/query/filter.xdef)`. You can use nested conditions like `and`, `or`, and `not` within the filter. Comparisons such as `eq`, `gt`, and `ge` are supported to implement comparison-based validations. The `name` attribute in the validator corresponds to variables and their properties in the context environment.

  ## Contextual Environment

  When invoking the validation model, you need to pass the contextual environment object which contains the necessary variables and their attributes.

  ```xml
  <validator fatalSeverity="100" x:schema="/nop/schema/validator.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <check id="checkTransferCode" errorCode="test.not-transfer-code" errorDescription="The code is not transferable">
      <eq name="entity.flowMode" value="1"/>
    </check>
  </validator>
  ```

  ## Invoking the Validator in Java

  In Java, you can invoke the validator model using the `BizValidatorHelper` class's `runValidator` method. The `vars` parameter represents the contextual environment object that needs to be passed to the validator.

  ```java
  Map<String, Object> vars = new HashMap<>();
  vars.put("entity", myEntity);
  BizValidatorHelper.runValidator("/nop/demo/validator/process-card.validator.xml",
                                vars,
                                context);
  ```

  ## Invoking the Validator in Biz Models

  In Nop's business models (`BizModel`), you can define backend service functions either within Java classes (during code development) or directly within `xBiz` model files during no-code development. The `BizValidator` model supports dynamic loading and online editing of validation logic without requiring downtime.

  For instance, if you define a specific validation function in the `[Demo.xbiz](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-quarkus-demo/src/main/resources/_vfs/nop/demo/model/Demo/Demo.xbiz)` model, it can be dynamically extended using the `Delta` customization mechanism without modifying the `Demo.xbiz` file.

  ## Equivalent Functions in Java and BizModel

  The `testValidator2` and `testValidator3` functions in the `[DemoBizModel](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-demo/nop-quarkus-demo/src/main/java/io/nop/demo/biz/DemoBizModel.java)` class perform equivalent validation operations to those defined in `Demo.xbiz`. The `testValidator3` function, however, utilizes the `biz:RunValidator` tag function to load external validator model files and execute the associated validation logic.

  This approach allows for future customization of validation logic through the Delta mechanism without the need to modify the `Demo.xbiz` file.
  
```

## Biz Model Configuration


## XML Structure Overview
The Biz模型 is defined in XML format. The main components include:
- `<biz:RunValidator>` component
- `validatorPath` property
- `obj` property for object binding


## Example Usage
You can drag and drop the `<biz:RunValidator>` component into your design canvas and configure it using the properties panel.


## Macro Programming
For advanced configuration, you can use macro programming by including the Validator DSL within the Biz模型. This allows for embedding custom validation logic directly into the model definition.




- `errorCode`: Mapping of error codes to messages
- `errorDescription`: Textual description of errors
- `severity`: Error severity level
- `fatalSeverity`: Fatal error threshold


You can pass additional parameters to the validator using:
```xml
<check id="checkMaterial" errorParams="materialId=entity.materialId">
```


Extend the validation logic by adding custom rules in the XML configuration file located at:
[register-model.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/resources/_vfs/nop/core/registry/validator.register-model.xml)


The registration of Biz models is controlled through the `[register-model.xml]` configuration file. This file defines the validation rules and bindings for each model variant.

  
  You can register a custom model loader to the Nop platform. This allows you to load all registered models using the `ResourceComponentManager.loadComponentModel` function.

  ```xml
  <model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
        name="orm">
    <loaders>
      <xdsl-loader fileType="validator.xml" schemaPath="/nop/schema/validator.xdef"/>
    </loaders>
  </model>
  ```

  The `register-model.xml` file can register specific suffix files, such as `validator.xml`. This corresponds to a specific meta-model.

  - Multiple suffix files can be parsed into a single model object. For example:
    - The `api` model can be defined using the `api.xml` file.
    - The `Excel` model can be defined using the `api.xlsx` file or the `api.imp.xml` file.

  ```xml
  <model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
        name="api">
    <loaders>
      <xlsx-loader fileType="api.xlsx" impPath="/nop/graphql/imp/api.imp.xml"/>
      <xdsl-loader fileType="api.xml" schemaPath="/nop/schema/api.xdef"/>
    </loaders>
  </model>
  ```
  
