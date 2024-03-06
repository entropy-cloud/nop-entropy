Nop平台支持自定义的模型驱动代码生成机制，这个机制可以在Nop平台之外独立使用。这里以消息对象模型为例，演示如何通过配置实现对消息对象模型的解析和代码生成。

# 一. Excel模型文件

我们在Excel模型文件中定义一系列消息对象的结构，然后再根据结构定义生成对象的消息类。

![](msg-model-excel.png)



```java
public class IsoMessage{
    private BigDecimal amount;
    private int value;
    private String name;

    public String getBigDecimal(){
       return ...  
    }
    ....
}
```

# 二. 定义导入模型

无需编程，只需要增加一个imp导入模型定义，即可自动实现Excel文件的解析。

在本示例中，我们建立一个app-templates模块，然后在其中加入msg.imp.xml导入模型定义文件。

![](codegen-templates.png)

为了让Nop平台的模型加载器识别msg.xlsx这样的文件后缀名，实现对模型的自动加载和解析，我们需要定义增加一个注册模型，即上面的msg.register-model.xml。

```xml
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="imp">
    <loaders>
        <xlsx-loader fileType="msg.xlsx" impPath="/app/imp/msg.imp.xml"/>
    </loaders>
</model>
```

在这个注册模型中，我们设置了`msg.xlsx`文件后缀与`msg.imp.xml`导入模型的关联。

在导入模型中我们只需要定义Excel模型文件的Sheet模式，以及字段之间的父子关系。

```xml
<imp x:schema="/nop/schema/excel/imp.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:c="c" xmlns:xpt="xpt"
     templatePath="template.msg.xlsx" defaultStripText="true">
    <sheets>
        <sheet name="目录" ignore="true"/>
        <sheet name="配置">
            <fields>
                <field name="basePackageName" displayName="basePackageName" mandatory="true">
                    <schema stdDomain="string"/>
                </field>
                ...
            </fields>
        </sheet>

        <sheet name="message" namePattern=".*" field="messages" multiple="true" keyProp="name" sheetNameProp="name">
            <fields>
                <field name="name" displayName="名称" mandatory="true">
                    <schema stdDomain="prop-name"/>
                </field>
                ...
                <field name="fields" displayName="字段列表" list="true" keyProp="name">
                    <fields>
                        <field name="name" displayName="字段名" mandatory="true">
                            <schema stdDomain="var-name"/>
                        </field>
                        ...
                    </fields>
                </field>
            </fields>
        </sheet>
    </sheets>
</imp>
```

上面的配置中，`namePattern=“.*"`结合`multiple="true"`表示有多个sheet都会匹配message这个解析模板。fields="messages"表示解析得到的所有对象被收集到一个名为messages的列表中。【字段列表】这个节点设置了`list="true"`，它表示解析得到一个对象列表。

# 三. 使用NopCli工具实现Excel模型的解析

> Nop平台中的nop-cli命令行工具可以直接执行模型解析、代码生成等指令，具体文档参见[cli.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/cli.md)

```
java -Xbootclasspath/a:app-templates/src/main/resources/ 
     -jar ../nop-cli/target/nop-cli-2.0.0-BETA.1.jar  
     extract model/test.msg.xlsx -o=target/test.msg.json
```

* -Xbootclasspath配置将app-templates这个工程下的模型文件引入到classpath中，后面就可以通过虚拟文件路径来访问所有模型文件

* extract指令表示解析test.msg.xlsx模型，解析的结果输出到test.msg.json文件中
  
  > msg.register-model.xml中已经定义了后缀名为msg.xlsx的模型对象使用msg.imp.xml导入模型来解析

得到的json结果为

```json
{
  "appName": "LIMS",
  "basePackageName": "app.test",
  "mavenGroupId": "com.demo",
  "mavenArtifactId": "app-test",
  "mavenVersion": "1.0.0-SNAPSHOT",
  "messages": [
    {
      "name": "IsoMessage",
      "fields": [
        {
          "propId": 1,
          "name": "amount",
          "displayName": null,
          "type": "java.math.BigDecimal",
          "stdDomain": null,
          "codec": null,
          "length": 3,
          "offset": 1
        },
        {
          "propId": 2,
          "name": "value",
          "displayName": null,
          "type": "int",
          "stdDomain": null,
          "codec": null,
          "length": 12,
          "offset": 4
        },
        {
          "propId": 3,
          "name": "name",
          "displayName": null,
          "type": "java.lang.String",
          "stdDomain": null,
          "codec": null,
          "length": 10,
          "offset": 16
        }
      ]
    }
  ]
}
```

# 四. 使用NopCli工具根据JSON生成Excel模型

```
java -Xbootclasspath/a:app-templates/src/main/resources/ 
     -jar ../nop-cli/target/nop-cli-2.0.0-BETA.1.jar 
      gen-file target/test.msg.json 
      -t=/app/imp/msg.imp.xml -o=target/out.msg.xlsx
```

* gen-file指令将解析test.msg.json文件得到JSON对象，然后从msg.imp.xml取到其中配置的`template.msg.xlsx`作为导出模板，生成Excel文件为out.msg.xlsx

# 五. 使用NopCli工具执行代码生成

```
java -Xbootclasspath/a:app-templates/src/main/resources/ 
     -jar ../nop-cli/target/nop-cli-2.0.0-BETA.1.jar  
     gen model/test.msg.xlsx 
     -t=/app/templates/msg -o=target
```

* gen指令将解析`test.msg.xlsx`文件，应用app-templates模块下的`/app/templates/msg`模板，生成的文件保存到target目录下

具体代码生成模板的配置说明，参见[codegen.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/codegen.md)
