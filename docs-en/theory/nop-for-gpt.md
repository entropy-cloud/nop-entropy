# GPT for Complex Code Generation: Necessary Conditions

Currently, many people are attempting to use GPT directly to generate code, aiming to guide GPT in performing traditional coding tasks through natural language. However, **almost no one has seriously considered the long-term maintenance issue of the generated code**.

Based on the basic concept of reversible computation, we can make the following reasoning:

1. If a complex business logic code is expected to run stably over the long term, it is definitely not feasible for each change in requirements to regenerate the entire code from scratch. **We must apply a controlled differential approach to correct the existing logic incrementally**. Therefore, we need to define a Delta space and ensure that this space can be processed programmatically through difference merging operations.

2. Given the inherent ambiguity of natural language, it is not suitable as a stable carrier for complex business logic. Imagine this: even if today's natural language descriptions are unambiguous in their current context, shifts in societal environment will cause changes in the meanings of words over time, leading to different interpretations in future contexts. To maintain stable and precise expressions of business logic, we need to use human intelligence that has reached its peak development over the past 100 years; this calls for formal language (formal methods). To ensure that GPT-generated formal logic can be understood and verified/validated by humans quickly, we should implement a **formal language: Domain-Specific Language (DSL)**. This DSL should allow **programmatic verification** and **reverse extraction** of information for other purposes.

3. The generated code contains a vast amount of business knowledge, but previous programming technologies often lock this knowledge within specific implementation details, preventing us from having general reverse engineering tools. Even if GPT can understand existing code and generate natural language explanations, it is difficult to programmatically extract accurate and reliable knowledge from system source code. GPT-generated code explanations can at best provide some reference value; they are prone to delusions and idle talk.

4. If GPT does not use a simple Q&A approach but instead calls external plugins or designs complex execution plans, then from both safety and interaction stability perspectives, we must **confine GPT's outputs within our predefined semantic space**.

Some programmers may underestimate theoretical analysis, considering the training of AI models as mere practical experience accumulation. I disagree with this view. Based on the earlier reversible computation theory analysis, we can naturally derive that GPT should be treated as a serious software production tool that meets certain **necessary conditions**:

**GPT's input and output should be based on a differential DSL (Domain-Specific Language).**


## Establishing a DSL Forest on Unified Meta-Model

Many believe that GPT can understand complex business logic and generate accurate, error-free general code. But why can't GPT master simpler, more structured DSL languages? A common misconception is that DSLs use custom syntax that is too niche for GPT's training data, which lacks sufficient examples of such syntax. In reality, the true value of a DSL lies in its **established domain semantics space**. For example, to describe an approval workflow, we only need a minimal set of concepts like "flow," "step," "action," and "approver." Each token in the DSL has clear business meaning, not arbitrary meanings due to technical limitations.

However, if one were to use general programming languages for definition, it would involve importing libraries, declaring variables, etc., which introduces irrelevant concerns (e.g., variable scoping). This is why **using a formal language: Domain-Specific Language (DSL)** is essential. While the general syntax of GPT may not be suitable for DSLs due to its training data limitations, we can still design custom DSLs that align with our needs.

If we focus solely on the semantics of DSLs, we can adopt either XML or JSON as a common syntax for DSLs. In terms of expressiveness, both AST-based approaches (like Lisp's S-expressions) and XML/JSON tags are acceptable. The advantage of XML over JSON is its more structured format, but JSON's readability and flexibility make it more suitable for modern applications.

By adopting a reversible change approach, we can ensure that DSLs built on XML and JSON formats allow **reversible transformations**. For example, the Nop platform provides mechanisms to convert between XML and JSON with reversible transformations. This means both formats can represent the same DSL effectively.

Once a unified representation (XML/JSON) is adopted for DSLs, multiple DSLs can be integrated into a **forested DSL structure**, where each tree in the forest corresponds to a specific DSL. The root of this forest would be the unified meta-model, ensuring semantic consistency across all DSLs. This allows seamless embedding of one DSL into another without losing meaning.

In summary, GPT's role as a serious software production tool hinges on adhering to **necessary conditions**:

- **Input/Output should be based on a differential DSL (Domain-Specific Language).**



Many believe that GPT can understand and generate accurate, error-free general code for complex business logic. However, why can't GPT master simpler, more structured DSL languages? A common misconception is that DSLs use custom syntax that is too niche for GPT's training data, which lacks sufficient examples of such syntax. In reality, the true value of a DSL lies in its **established domain semantics space**.

For example, to describe an approval workflow, we only need concepts like "flow," "step," "action," and "approver." Each token in the DSL has clear business meaning, not arbitrary meanings due to technical limitations. However, using general programming languages for definition involves irrelevant concerns (e.g., variable scoping), which is why **using a formal language: Domain-Specific Language (DSL)** is essential.

While GPT's general syntax may not be suitable for DSLs due to its training data limitations, we can still design custom DSLs that align with our needs. The key is to ensure that these DSLs are built on a **unified meta-model** that allows seamless integration and semantic consistency across all DSLs in the forest.

This unified meta-model would act as the root of the DSL forest, ensuring that each tree (DSL) can be understood and validated within this context. By adopting reversible change mechanisms, we can maintain the integrity and adaptability of this structure over time.

In conclusion, GPT's role as a serious software production tool depends on meeting **necessary conditions**:

- **Input/Output should be based on a differential DSL (Domain-Specific Language).**



## Traditional Programmers' Perceptions

Many programmers have a traditional impression that designing a new DSL (Domain Specific Language) requires writing parsers, compilers, and maintaining IDE plugins, which involves substantial work. However, on the Nop platform, you only need to define an XDef meta-model to automatically obtain parsers, validators, and syntax highlighting functions. Additionally, you can directly debug in **IDEA** with breakpoints! The Nop platform also allows for automatic implementation of bidirectional data exchange between domain objects and Excel templates (e.g., converting Excel documents into DSL descriptions or exporting DSL descriptions into Excel documents). Furthermore, it provides a concept called the Domain Language Workbench, enabling rapid development and extension of DSL languages. This design objective is similar to JetBrains's [MPS product](https://www.jetbrains.com/mps/), but Nop platform is built on reversible computation theory. Its technical approach is more concise and clear, with significantly lower complexity compared to MPS. On the other hand, in terms of flexibility and extensibility, Nop platform surpasses MPS by a large margin.





Many programmers have not personally designed an XML-based DSL language but have only heard legends about how later generations supposedly replaced ancient XML with more modern technologies. This has led to a common misconception that XML is too verbose and unsuitable for human-machine interaction. However, this perspective is based on outdated XML dogmatism and incorrect usage patterns, as well as the influence of international XML standards.

When programmers think of using XML to express logic, they may have the following impression:

```xml
<function>
  <name>myFunc</name>
  <args>
    <arg>
      <arg>
        <name>arg1</name>
        <value>3</value>
      </arg>
      <arg>
        <name>arg2</name>
        <value>aaa</value>
      </arg>
    </arg>
  </args>
</function>
```

However, in reality, we can achieve the same effect using the following XML format:

```xml
<myFunc arg1="3" arg2="aa" />
```

If we want to indicate that `arg1` is of integer type rather than string type, we can extend the XML syntax as follows:

```xml
<myFunc arg1=3 arg2="aa" />
```

Or we can use a prefix system similar to the Vue framework by adding specific prefixes. For example, we can define a prefix like `@:` to distinguish between string and non-string values:

```xml
<myFunc arg1="@:3" arg2="aa" />
```

In the Nop platform, we have defined rules for bidirectional conversion between JSON and XML. For example, consider the following AMIS description:

```xml
<myFunction arg1="3" arg2="aa" />
```

We can automatically convert this into Excel using the Nop platform's capabilities. The platform also supports generating a visualization designer (e.g., for reverse engineering or code generation).

## JSON Representation
```json
{
  "type": "crud",
  "draggable": true,
  "bulkActions": [
    {
      "type": "button",
      "label": "Bulk Delete",
      "actionType": "ajax",
      "api": "delete:/amis/api/mock2/sample/${ids|raw}",
      "confirmText": "Are you sure you want to delete?"
    },
    {
      "type": "button", 
      "label": "Bulk Modify",
      "actionType": "dialog"
    }
  ]
}
```


```xml
<crud draggable="@:true">
  <bulkActions j:list="true">
    <button label="批量删除" actionType="ajax" confirmText="确定要批量删除?">
      <api>delete:/amis/api/mock2/sample/${ids|raw}</api>
    </button>
    <button label="批量修改" actionType="dialog">
      <dialog title="批量编辑" name="sample-bulk-edit">
        <body>
          <form>
            <api>/amis/api/mock2/sample/bulkUpdate2</api>
            <body>
              <hidden name="ids" />
              <input-text name="engine" label="Engine" />
            </body>
          </form>
        </body>
      </dialog>
    </button>
  </bulkActions>
</crud>
```


Using **XML** compared to **JSON** offers advantages in easily introducing XML-specific tags for code generation, where both the structure and result of the generated code are in XML format. This characteristic is known as **同像性 (Similarity)** in Lisp programming.

```xml
<columns>
  <c:for var="col" items="${entityModel.columns}">
    <column name="${col.name}" sqlType="${col.sqlType}" />
  </c:for>
</columns>
```

For further discussion on the equivalence between **JSON** and **XML**, including their respective function ASTs, please refer to [This Article](https://zhuanlan.zhihu.com/p/554294376).




The reason for the stir caused by large AI models is essentially due to their demonstration of complex logical reasoning capabilities beyond simple pattern recognition. Under the support of this capability, large AI models should require almost no large amounts of program data for learning a DSL (Data Handling Interface). Instead, they only need to be informed about the internal structural constraints of the language.


## The Revolution in Mathematical Field

The discovery of meta-models and metalanguages is considered one of the most revolutionary discoveries in the mathematical field over the past 100 years. Meta-models and metalanguages hold special significance in mathematics. The development of category theory, model theory, and metalanguage studies are closely related. In the field of programing, through meta-models, we can precisely transfer DSL syntax knowledge and local semantic knowledge to large AI models.

In particular, a meta-model can be considered similar to a JSON Schema-like definition. This is because it defines the structure and constraints of the data in a way that aligns closely with the actual data structure.



On the Nop platform, we emphasize the sameotopy relation between meta-models and specific model objects. This means that the form of the schema should be consistent with the actual data structure, rather than being overly fragmented like XML Schema. Instead of breaking down hierarchical structures into numerous object-attribute relationships using complex syntax, we aim for a more straightforward representation.

For example:

```xml
<entity name="test.MyEntity" table="my_entity">
  <columns>
    <column name="SID" sqlType="VARCHAR" length="30" />
    <column name="TITLE" sqlType="VARCHAR" length="200" />
  </columns>
</entity>
```

The corresponding XDEF meta-model is defined as:

```xml
<entity name="!class-name" table="!string">
  <columns xdef:body-type="list" xdef:key-attr="name">
    <column name="!prop-name" sqlType="!std-sql-type" length="int" />
  </columns>
</entity>
```

Essentially, XDEF replaces specific values with the stdDomain definition while retaining only a single entry for list elements. It is similar to type declarations but can be customized and extended by users.



The strategy for interaction between GPT and the Nop platform involves:

1. Utilizing the current DSL's xdef meta-model to assist GPT in quickly understanding and accurately grasping DSL syntax.
2. Employing reversible computational rules to guide GPT in directly returning delta descriptions.
3. Combining these returns into the current model, forming a new model based on this combination for continuous interaction with GPT.

4. For complex logical reasoning, which cannot typically be addressed by a single DSL, we can decompose the problem into multiple steps using multiple DSLs to establish a delta pipeline. This is particularly useful when dealing with problems that require iterative analysis.



The delta pipeline for AI and human collaboration on the Nop platform involves:

1. AI generates the top-level DSL based on requirements.
2. Human coding generators expand this into lower-level DSLs.
3. Custom Delta rules are applied by humans to fine-tune AI-generated DSLs.
4. The most detailed aspects of the delta pipeline can be further refined by AI based on local knowledge.



In summary, while large models excel at filling in the blanks, they still lack true understanding and contextual awareness. To truly leverage their potential, we need a more structured approach to feeding them domain knowledge through meta-models. This way, we can ensure they produce accurate and contextually relevant outputs without requiring vast amounts of program data.


Traditional AI code generation by many developers involves generating interfaces, classes, and properties. However, Nop platform takes a completely different approach. As I have consistently emphasized, the class-attribute abstraction is limited by underlying implementation technologies, leading to mismatches between domain structure and model mapping, especially at the type level. For instance, domain coordinate concepts often fail to map accurately to type-level definitions due to missing adjustments, making precise delta corrections impossible.

Nop's Domain-specific Language (DSL) is designed for tree structures, enabling the generation of complete logic trees in one step.


## Nop's Advantages

1. **Mergeable Changes**  
   Multiple Delta changes can be combined into a single result during the merge process, discarding redundant modifications. However, using APIs to apply changes sequentially results in inefficiencies because individual operations cannot be automatically merged or simplified. Without applying Deltas beforehand, it becomes impossible to fully understand how the system will be altered.

2. **Reversible Changes**  
   Domain model Deltas are defined and can be automatically analyzed by programs. While using APIs for Delta implementation may lack tools for precise analysis of specific components, without prior Delta application, the scope of impacts remains unclear. Reverse information extraction abilities, crucial for understanding changes, are also a key focus of reversible computing theory.

3. **Precise Modifications**  
   Deltas can be applied to the foundation model due to their ability to define exactly where changes occur, such as the "phone3" field in entity models. This ensures meaningful adjustments with clear business significance and unique identification paths. However, MyEntity model's line-by-line description for positions 10-20 lacks stability and clarity, making it unsuitable for precise modifications.



The following code demonstrates how AI-generated API calls can be used to delete the "phone3" field from a data model:

```plaintext
entityModel.getColumns().remove("phone3");
```

Comparing Nop's Delta merge operator reveals why traditional API approaches are suboptimal:

```plaintext
<columns>
  <column name="phone3" x:override="remove"/>
  <column name="status" sqlType="INTEGER"/>
</columns>
```



By leveraging Nop's approach, developers can achieve efficient and precise code modifications through Deltas. This method not only simplifies the application of changes but also enhances overall system understanding.

```python
role_simulator = guidance('''
{{#system~}}
You are a helpful assistant
{{~/system}}

{{#user~}}
You will answer the user as {{role}} in the following conversation. At every step, I will provide you with the user input, as well as a comment reminding you of your instructions. Never talk about the fact that you are an AI, even if the user asks you. Always answer as {{role}}.
{{#if first_question}}You can also start the conversation.{{/if}}
{{~/user}}

{{~! The assistant either starts the conversation or not, depending on if this is the first or second agent }}
{{#assistant~}}
Ok, I will follow these instructions.
{{#if first_question}}Let me start the conversation now:
{{role}}: {{first_question}}{{/if}}
{{~/assistant}}

{{~! Then the conversation unrolls }}
{{~#geneach 'conversation' stop=False}}
{{#user~}}
User: {{set 'this.input' (await 'input')}} 
Comment: Remember, answer as a {{role}}. Start your utterance with {{role}}:
{{~/user}}
```

Clearly, if we introduce a normalized, systematized tree-like structure representation method, we can say that the machine saves labor, and we also save labor.

Based on the reversible calculation theory design, the low-code platform NopPlatform has been open-sourced:

- Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible calculation principle and Nop platform introduction, as well as Bilibili video](https://www.bilibili.com/video/BV1u84y1w7kX/)

