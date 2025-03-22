Here is the translation of the Chinese technical document into English, preserving the original Markdown format including headers, lists, and code blocks:


# Understanding Metamodels
You are a computer expert who understands the role of metamodels. Below is a definition of a metamodel.


# Example Metamodel Definition

```xml
<prompt displayName="string">
    <description>string</description>
    <defaultChatOptions xdef:ref="chat-options.xdef"/>
    <vars>
        <!-- Variable declaration for template usage -->
        <var name="!var-name" displayName="string" xdef:name="PromptVarModel">
            <schema xdef:ref="../schema/schema.xdef"/>
            <description>string</description>
        </var>
    </vars>
    
    <!-- Template generation using XPL template language -->
    <template>xpl-text</template>

    <!-- Post-processing of AI results -->
    <processResultMessage>JS function, use resultMessage variable</processResultMessage>
</prompt>
```


# Chat Options Definition

```xml
<options model="string" seed="string"
         temperature="float"
         topP="float" topK="int"
         maxTokens="int" contextLength="int"
         stop="comma separated string"/>
```


# Analysis of the Metamodel
Analyze the above metamodel definition and generate an example prompt that complies with the metamodel requirements, which will be sent to ChatGPT for translation into English.

1. The `body` node in the metamodel represents the data type of the part.
2. The `!string` indicates a non-empty attribute, and this character should not appear in the final result.
3. The `xpl` template language is similar to JavaScript's template string.
4. `xdef` refers to the namespace attribute in the metamodel, which should not appear in the final XML.
5. The output should be in XML format that meets the metamodel requirements and should only contain XML content without additional explanations.
6. The case of XML tags and attributes must match the metamodel's specifications.
7. `xdef:ref` indicates a reference to an existing metamodel definition, which defines the node's structure in the final XML.

The following example prompt complies with the metamodel requirements:

```yaml
displayName: string
description: string
defaultChatOptions:
  xdef:ref: chat-options.xdef

vars:
  - name: !var-name
    displayName: string
    xdef:name: PromptVarModel
    schema:
      xdef:ref: ../schema/schema.xdef
    description: string

template: xpl-text

processResultMessage: xpl-fn: (resultMessage) => void
```

```markdown
# Model Parameters
```xml
model: string
seed: string
temperature: float
topP: float
topK: int
maxTokens: int
contextLength: int
stop: comma separated string
```

## Requirements:
1. Analyze the above meta-model definition and generate an example Prompt that meets the meta-model requirements, which will be sent to chatgpt for translation into English.
2. The meta-model defines that the body part represents the data type.
3. The `!string` in `!string` indicates that this attribute is non-empty, and the `!` character should not appear in the final result.
4. `xpl` template language is similar to JavaScript's template strings.
5. `xdef` refers to namespace attributes in the meta-model, which should not appear in the final YAML.
6. The output should be in YAML format that meets the meta-model requirements and only contain YAML, without additional explanations.
7. Node names and attribute names should match the case of the meta-model.

### Example Prompt for Translation:
```json
{
  // Basic information
  displayName: "string",
  description: "string",

  // Chat options defined (linked to XML schema)
  defaultChatOptions: {
    "xdef:ref": "chat-options.xdef"
  },

  // Variable declaration configuration
  vars: [
    {
      // Declares variables used in the template, mainly for template management
      name: "!string",
      displayName: "string",
      schema: {
        "xdef:ref": "../schema/schema.xdef"
      },
      description: "string"
    }
  ],

  // Using xpl template language to generate prompt
  template: "Here is an example using the xpl template language",

  // Post-processing configuration
  processResultMessage: "xpl-fn:(resultMessage)=>void"
}
```

### Chat-Options.xdef Definition:
```json
{
  model: "string"
}
```
9. 7B will be confused between `xdef:name` and `name`, it is unclear which one is the actual name attribute.
10. 32B can identify the YAML format's meta-model, but sometimes `template` is not generated correctly.
11. The model `deepseek-r1:8b-32k` can run on a local machine and recognize JSON's meta-model while generating it correctly.
12. 7B has difficulty understanding `var-name`.
13. The 14B model occupies 13G of GPU memory, and 14B-4K consumes 10.7G of memory, while 14B-32K uses 16G of memory. 8B-32k also uses 10G of memory, and 8B is around 8G.
14. The 14B model can generate `template` correctly and identify `var-name`, but it cannot reliably recognize `xdef:ref` definitions.
15. The 14B model runs translation functionality on my machine with a temperature setting of 0.6. For a long content, the `think` section becomes empty, and the output is truncated.

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

