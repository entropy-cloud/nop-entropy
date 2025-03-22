# Why NopReport is a Unique Report Engine?

NopReport differs from common report engines because it can directly use Excel and Word as templates, without requiring a dedicated visualization designer.

> For more details about NopReport, please refer to [Open-Source China-Style Report Engine: NopReport](https://zhuanlan.zhihu.com/p/620250740) and [How to Implement a POI-like Visual Word Template with Only 800 Lines of Code](https://zhuanlan.zhihu.com/p/537439335).

However, some people who read the introduction feel that it does not highlight its unique aspects. They may wonder:

> This method has been around for a while, right? Everyone knows that Word is essentially XML, so we can directly use template engines to fill in data.
> Is there anything particularly revolutionary about this approach? Jxls also uses Excel's annotations to define template syntax.

To truly understand the innovative aspects of NopReport's design, you need to step back from specific functional details and consider it from a more abstract mathematical structure perspective. The core difference between Nop and other open-source frameworks lies in its foundation: Nop's design is based on first-order mathematics, not clever design patterns. Instead, it reflects a certain inevitability rooted in mathematics, which can be rigorously proven.


## General Template Visualization Approach

NopReport's core design philosophy revolves around the following mathematical formula:

```
Template = BaseModel + ExtModel
```

From a mathematical standpoint, **any raw model can be considered a valid template**. When you need more complex template behavior, simply extend the base information with additional configurations. For example, a regular Word document can be treated as a template. The report engine reads this Word file and converts it into a report template, essentially performing an identity transformation: whatever data you input will directly reflect in the output, which is just the raw content of the Word file.

When you need a template that includes conditional statements, loops, or complex dynamic content, many people's initial thought is to start from scratch, designing a new template structure. However, based on our earlier mathematical analysis, if a regular Word file qualifies as a valid template, then our template's expressive power should inherently be a superset of the base model's capabilities. In the simplest case, **we should use a linearized design**:
- Preserve the fundamental elements of the base model.
- Extend it by adding additional configuration parameters to support dynamic behavior.

When you need a template that includes conditional statements or complex dynamic content, some people might think of starting from scratch, designing a new structure. However, based on our earlier mathematical analysis, if a regular Word file qualifies as a valid template, then our template's expressive power should inherently be a superset of the base model's capabilities.

In terms of visualization design:
- From a mathematical perspective, **any visualization can be considered a bijective mapping**.
- When you need to design a visualization, you introduce additional configuration parameters without altering the base model. This is akin to adding delta changes on top of the base model.

For visualization design:
```
VisualView = Editor(Model)
Model = Serializer(VisualView)
```

The visualization designer's role is to define how raw data should be transformed into visual representations. The editor handles the transformation, while the serializer ensures that the transformation is reversible for further modifications. This approach maintains a bidirectional relationship between the model and the visualization.

An ideal visualization mapping should satisfy the linearity principle:
```
Editor(Template) = Editor(BaseModel + ExtModel)
= Editor(BaseModel) + Editor(ExtModel)
```

From a mathematical perspective, this is akin to a **linear transformation**. When decomposing the model into base and extension components, the editor's behavior on the composite model is merely the sum of its behaviors on each component.

For example:
```
TemplateEditor = BaseModelEditor + ExtModelEditor
```

Translating this into Office software terms:
- The mathematical formula corresponds to using Excel's formula bar or built-in functions.
- **Any function (Functor) that can be represented in Excel can be used**.
- For example, if a function processes `(data, ext_data)`, it can be represented as ` data + ext_data` in the template.

However, Office applications are not inherently designed to handle reversible transformations. This limitation means that while you can use formula-based templates for data processing, certain complex requirements may necessitate workarounds or custom implementations.

NopReport's design does not adhere to this limitation because it is grounded in first-order mathematics. It allows for a **bidirectional mapping** between the model and the template:
```
VisualView = Editor(Model)
Model = Serializer(VisualView)
```

This ensures that changes to the model automatically reflect in the visualization, and vice versa.

![word-report.png](../dev-guide/report/word-template/word-report.png)

Using hyperlinks is merely a minor technical detail. We can also choose to use Word's comment feature to store additional information.

> It is important to note that by using the above method, we can implement templateization for any model, requiring only minimal Delta information.
> For further analysis of the linear mapping principle, please refer to my article [Tensor Tensor: Low-Code Platform Design](https://zhuanlan.zhihu.com/p/531474176).

## Common Structural Layer Construction Rules

Many people can intuitively feel that since Microsoft Office switched to using OOXML (XML-based) storage formats, handling Office documents has been significantly simplified. Currently, many Office template libraries like jxls and poi-tl operate on top of Apache POI technology, which encapsulates Office documents in a deep way, providing more straightforward template generation solutions. However, NopReport can replace the functionality of these template libraries with minimal code while offering better expandability. Why is this the case?

The fundamental reason lies in how different libraries handle data. The POI library operates at the object type level, where each object type has its own set of rules for processing. In contrast, NopReport processes data at a normalized XML layer. XML can be considered a more universal way of representing structure.

Reverse engineering emphasizes that before converting information into business objects, there exists a unified structural representation layer. This allows direct manipulation of data in this standardized layer without needing to move it down to the object layer. Each object type has its unique characteristics and corresponding processing rules, which may differ significantly across types.

Just as diverse architectural designs share underlying engineering principles, many seemingly different business aspects at various levels exhibit similar structural patterns. These can be governed using the same construction rules and processed with analogous tools.

In this context, XLang's XPL template language stands out. What is the mathematical positioning of XPL? It excels in **converting any AST (Abstract Syntax Tree) into executable logic** through syntax-directed translation. This is what we mean by syntax-directed translation: when encountering a specific syntactic node, predefined rules automatically trigger to translate it accordingly. Essentially, this is a mechanism for context-free grammar customization.

Here, syntax-directed translation refers to the automatic triggering of corresponding translation rules upon encountering a specific syntactic node. This is akin to a self-contained, context-independent custom component mechanism.

```xml
<doc>
  <orm:GenPackageDiagram />
  <orm:ForEachEntity>
    ...
  </orm>
</doc>
```

Each `<orm:ForEachEntity>` tag triggers corresponding component definitions and executes executable logic within the component.

For further analysis of structural construction rules, please refer to my article [Common Delta Mechanism](https://zhuanlan.zhihu.com/p/681801076).

Based on reversible computing principles, the low-code platform NopPlatform has been open-sourced:

- Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible Computing Principles and NopPlatform Introduction (Bilibili)](https://www.bilibili.com/video/BV1u84y1w7kX/)

