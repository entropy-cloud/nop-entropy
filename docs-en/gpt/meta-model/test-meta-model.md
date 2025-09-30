You are a computer expert who understands the role of meta-models. Below is a meta-model definition.

```xml
<prompt displayName="string">

    <description>string</description>

    <defaultChatOptions xdef:ref="chat-options.xdef"/>

    <vars>
        <!--
        声明模板中使用的变量信息。主要用于模板管理
        -->
        <var name="var-name" displayName="string" xdef:name="PromptVarModel">
            <schema xdef:ref="../schema/schema.xdef"/>
            <description>string</description>
        </var>
    </vars>

    <!--
    通过xpl模板语言生成prompt，可以利用xpl的扩展能力实现Prompt的结构化抽象
    -->
    <template>生成的Prompt提示词</template>

    <!--
    执行完AI模型调用后得到AiResultMessage对象，可以通过模板内置的后处理器对返回结果进行再加工。
    这样在切换不同的Prompt模板的时候可以自动切换使用不同的后处理器。
    比如Prompt中可以额外增加一些特殊的标记提示，用于简化结果解析，在processResultMessage中自动识别这些标记并做处理。
    -->
    <processResultMessage>JS函数，可以使用resultMessage变量</processResultMessage>
</prompt>
```

The definition of chat-options.xdef is as follows:
```xml
<options model="string" seed="string"
         temperature="float"
         topP="float" topK="int"
         maxTokens="int" contextLength="int" stop="comma seperated string"
/>
```

Requirements: Analyze the above meta-model definition and automatically generate a sample Prompt that conforms to the meta-model. It describes a prompt sent to ChatGPT and functions to translate Chinese into English.

1. In the meta-model, the body of a node indicates the data type for that section.
2. In !string, the exclamation mark indicates that the attribute is non-null; the “!” character should not appear in the final result.
3. The xpl template language is similar to JavaScript template strings.
4. Attributes in the xdef namespace are meta-model attributes and must not appear in the final XML.
5. The output must be in XML format that conforms to the meta-model, and only include the XML—do not add extra explanations.
6. Tag and attribute names must match the case used in the meta-model.
7. xdef:ref indicates a reference to an existing meta-model definition; the format of the current node is defined by the referenced meta-model.


==========================
You are a computer expert who understands the role of meta-models. Below is a meta-model definition.

```yaml
displayName: string
description: string
defaultChatOptions:
  xdef:ref: chat-options.xdef

vars:
  # 声明模板中使用的变量信息。主要用于模板管理
  - name: !var-name
    displayName: string
    xdef:name: PromptVarModel
    schema:
      xdef:ref: ../schema/schema.xdef
    description: string

# 通过xpl模板语言生成prompt，可以利用xpl的扩展能力实现Prompt的结构化抽象
template: xpl-text

# 执行完AI模型调用后得到AiResultMessage对象，可以通过模板内置的后处理器对返回结果进行再加工。
# 这样在切换不同的Prompt模板的时候可以自动切换使用不同的后处理器。
# 比如Prompt中可以额外增加一些特殊的标记提示，用于简化结果解析，在processResultMessage中自动识别这些标记并做处理。
processResultMessage: xpl-fn:(resultMessage)=>void
```

The definition of chat-options.xdef is as follows:

```xml
model: string
seed: string
temperature: float
topP: float
topK: int
maxTokens: int
contextLength: int
stop: comma seperated string
```

Requirements: Analyze the above meta-model definition and automatically generate a sample Prompt that conforms to the meta-model. It describes a prompt sent to ChatGPT and functions to translate Chinese into English.

1. In the meta-model, the body of a node indicates the data type for that section.
2. In !string, the exclamation mark indicates that the attribute is non-null; the “!” character should not appear in the final result.
3. The xpl template language is similar to JavaScript template strings.
4. Attributes in the xdef namespace are meta-model attributes and must not appear in the final YAML.
5. The output must be in YAML format that conforms to the meta-model, and only include the YAML—do not add extra explanations.
6. Node and attribute names must match the case used in the meta-model.


==========================
You are a computer expert who understands the role of meta-models. Below is a meta-model definition.

```json5
{
  // 基本信息
  displayName: "string",
  description: "string",

  // 聊天选项定义（关联 XML schema）
  defaultChatOptions: {
    "xdef:ref": "chat-options.xdef"  // 对应 XML 定义:
  },

  // 变量声明配置
  vars: [
    {
      // 声明模板中使用的变量信息。主要用于模板管理
      name: "!string",
      displayName: "string",
      schema: {
        "xdef:ref": "../schema/schema.xdef"
      },
      description: "string"
    }
  ],

  // 通过xpl模板语言生成prompt，可以利用xpl的扩展能力实现Prompt的结构化抽象
  template: "这里使用xpl模板语言",

  // 后处理配置
  /*
  执行完AI模型调用后得到AiResultMessage对象，可以通过模板内置的后处理器对返回结果进行再加工。
  这样在切换不同的Prompt模板的时候可以自动切换使用不同的后处理器。
  比如Prompt中可以额外增加一些特殊的标记提示，用于简化结果解析，在processResultMessage中自动识别这些标记并做处理。
  */
  processResultMessage: "xpl-fn:(resultMessage)=>void"
}
```

The definition of chat-options.xdef is as follows:

```json5
{
	model: "string"
	seed: "string"
	temperature: "float"
	topP: "float"
	topK: "int"
	maxTokens: "int"
	contextLength: "int"
	stop: "comma seperated string"
}
```

Requirements: Analyze the above meta-model definition and automatically generate a sample Prompt that conforms to the meta-model. It describes a prompt sent to ChatGPT and functions to translate Chinese into English.

1. In the meta-model, the body of a node indicates the data type for that section.
2. In !string, the exclamation mark indicates that the attribute is non-null; the “!” character should not appear in the final result.
3. The xpl template language is similar to JavaScript template strings.
4. Attributes in the xdef namespace are meta-model attributes and must not appear in the final YAML.
5. The output must be in JSON format that conforms to the meta-model, and only include the JSON—do not add extra explanations.
6. Node and attribute names must match the case used in the meta-model.

==============
1. xdef:name="PromptModel" can mislead the model into thinking this is the name of the output XML node.
2. xdef:value is used to indicate that the content of the body segment is difficult for the model to understand; 32B models also often output it as an attribute.
3. xdef:body-type and xdef:key-attr are not important for code generation and can be removed.
4. A 32B model can understand the meaning of <template>xpl-text</template>, but a 7B model cannot.
5. A 32B model can understand xdef:ref and generate a correct model, but a 7B model cannot.
6. Understanding the exclamation mark in !string requires detailed explanation; even 32B models often get it wrong.
7. The 7B model’s think segment appears to understand some content, but the generation is still incorrect.
8. The 32B model can recognize the YAML-formatted meta-model, but sometimes the output format becomes messy.
9. The 7B model gets confused between xdef:name and name and is unsure which is the actual name attribute.
10. The 32B model can recognize the YAML-formatted meta-model, but sometimes the template is not generated correctly.
11. deepseek-r1:8b-32k running locally can recognize the JSON-formatted meta-model and generate it correctly.
12. The 7B model has difficulty understanding var-name.
13. The 14B model uses 13G of GPU memory; 14B-4K uses 10.7G; 14B-32K uses 16G. The 8B-32k uses 10G, while 8B uses about 8G.
14. The 14B model can correctly generate the template and recognize var-name, but cannot reliably recognize xdef:ref definitions.
15. On my machine, when running the 14B model with temperature=0.6 for translation of a relatively long content, the think segment becomes empty and the output is truncated.

```
 You are a professional translator.
	Translate the following Chinese text to natural English while:
	1. Maintaining original terminology
	2. Preserving contextual nuances
	3. Using appropriate idioms where applicable

	Chinese Input:
	${inputText}

	Respond ONLY with the translated text without additional commentary.

```
<!-- SOURCE_MD5:24ab7f87559c73d6c1bb296c1f1f8213-->
