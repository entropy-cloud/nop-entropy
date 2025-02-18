你是计算机专家，理解元模型的作用。下面是一个元模型的定义。

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

chat-options.xdef的定义如下：
```xml
<options model="string" seed="string"
         temperature="float"
         topP="float" topK="int"
         maxTokens="int" contextLength="int" stop="comma seperated string"
/>
```

要求：分析上面的元模型的定义，并自动生成一个符合元模型要求的示例Prompt，它描述一个发送给chatgpt的提示词，功能是将中文翻译为英文。

1. 元模型中节点的body部分表示的是该部分的数据类型
2. !string中的!表示这个属性非空，!这个字符在最终的结果中不需要出现
3. xpl模板语言类似JavaScript的模板字符串
4. xdef为名字空间的属性是元模型属性，在最后的XML中不出现。
5. 输出结果为满足元模型要求的XML格式，且仅包含XML，不要额外解释
6. XML标签名和属性名的大小写要与元模型保持一致
7. xdef:ref表示引用已经存在的元模型定义，本节点的格式由引入的元模型来定义


==========================
你是计算机专家，理解元模型的作用。下面是一个元模型的定义。

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

chat-options.xdef的定义如下：

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

要求：分析上面的元模型的定义，并自动生成一个符合元模型要求的示例Prompt，它描述一个发送给chatgpt的提示词，功能是将中文翻译为英文。

1. 元模型中节点的body部分表示的是该部分的数据类型
2. !string中的!表示这个属性非空，!这个字符在最终的结果中不需要出现
3. xpl模板语言类似JavaScript的模板字符串
4. xdef为名字空间的属性是元模型属性，在最后的YAML中不出现。
5. 输出结果为满足元模型要求的YAML格式，且仅包含YAML，不要额外解释
6. 节点名和属性名的大小写要与元模型保持一致


==========================
你是计算机专家，理解元模型的作用。下面是一个元模型的定义。

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

chat-options.xdef的定义如下：

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

要求：分析上面的元模型的定义，并自动生成一个符合元模型要求的示例Prompt，它描述一个发送给chatgpt的提示词，功能是将中文翻译为英文。

1. 元模型中节点的body部分表示的是该部分的数据类型
2. !string中的!表示这个属性非空，!这个字符在最终的结果中不需要出现
3. xpl模板语言类似JavaScript的模板字符串
4. xdef为名字空间的属性是元模型属性，在最后的YAML中不出现。
5. 输出结果为满足元模型要求的JSON格式，且仅包含JSON，不要额外解释
6. 节点名和属性名的大小写要与元模型保持一致

==============
1. `xdef:name="PromptModel"`会误导大模型，让它误以为这是输出的xml节点的名字
2. `xdef:value`用于表示body段的内容很难被模型理解，32B模型也经常作为属性输出。
3. `xdef:body-type`和`xdef:key-attr`对于生成代码而言并不重要，可以去除
4. 32B可以理解`<template>xpl-text</template>`的含义，但7B不能。
5. 32B可以理解`xdef:ref`，生成模型正确，但7B不能。
6. 理解`!string`中的!需要详细解释，32B也经常搞错。
7. 7B的think部分看着也理解了一些内容，但是生成不正确。
8. 32B可以识别YAML格式的元模型，但是输出的时候有时会出现格式混乱。
9. 7B会被`xdef:name`和`name`搞混，不清楚哪个是真正的名称属性
10. 32B可以识别YAML格式的元模型，但是template有时没有正确生成。
11. `deepseek-r1:8b-32k`在本机运行可以识别JSON格式的元模型，并正确生成。
12. 7B理解var-name存在困难。
13. 14B模型占用13G的GPU内存，14B-4K占用10.7G内存, 14B-32K占用16G内存。8B-32k占用10G。而8B占用8G左右。
14. 14B模型可以正确生成template，也可以识别var-name，不能稳定识别xdef:ref定义。

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