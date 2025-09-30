# Why Is NopReport a Truly Unique Reporting Engine?

Unlike typical reporting engines, NopReport can directly use Excel and Word as templates, without necessarily relying on a dedicated visual designer.

> For an introduction to NopReport, see [An open-source, China-style reporting engine that uses Excel as the designer: NopReport](https://zhuanlan.zhihu.com/p/620250740) and [How to implement a visual Word template similar to poi-tl in 800 lines of code](https://zhuanlan.zhihu.com/p/537439335)

However, some readers still don’t perceive what’s unique about it and raise questions like:

> Hasn't this approach existed for a long time? Everyone knows Word is basically XML and can be filled with a template engine.
> Is there anything truly paradigm-shifting here? jxls also uses Excel comments to write template syntax.

To grasp the innovative aspects of NopReport’s design, you need to step out of the weeds of concrete features and think in terms of more abstract mathematical structures.
The most fundamental difference between the Nop platform and other existing open-source frameworks is that the Nop platform is derived from first-principles mathematical reasoning. Its approach is not just a clever design pattern,
but a manifestation of mathematical inevitability—an optimal design that can be rigorously proven in a certain mathematical sense.

## A General Visual Template Approach

NopReport’s core design idea is based on the following mathematical formula:

```
Template = BaseModel + ExtModel
```

In a mathematical sense, any raw model can be regarded as a valid generative template. When richer template behavior is needed, we simply supplement the raw information with additional extension configuration.
For example, an ordinary Word file can be treated as a Word report template. The reporting engine can read a normal Word file and convert it into a report template; its runtime behavior is essentially an identity transformation:
no matter what report parameters we input, the output remains the fixed content—the original content of the Word file we read.

When we need a report template containing complex dynamic content such as conditionals and loops, many people’s first instinct is to design a new report format from scratch to meet dynamic configuration requirements. But per the mathematical analysis above,
since a plain Word file is already a valid report template, the representational capacity of our report template must be a superset of the raw model’s expressive power. In the simplest case, we should adopt a linear design:
we fully retain the design elements of the base model and introduce dynamic template capabilities by adding new extension model information. Note that we emphasize not modifying the existing BaseModel at all; the extension model corresponds to an independently existing Delta expression.

When we need visual design, at the mathematical level, we are introducing a bidirectional, reversible transformation:

```
 VisualView = Editor(Model)
 Model = Serializer(VisualView)
```

The visual designer presents model information as a visual view and provides corresponding editing capabilities. Conversely, given the visual view, we can serialize the information in it to obtain a model representation that can be persisted in text (or binary) form.
The visual view and the textual representation of the model are two different manifestations of the same information, between which a bidirectional conversion can be established.

An ideal visual mapping should satisfy the principle of linear mapping, namely:

```
 Editor(Template) = Editor(BaseModel + ExtModel) 
                  = Editor(BaseModel) + Editor(ExtModel)
```

When a Base+Ext linear decomposition exists at the model level, we want to preserve this relationship at the visual design level as well, yielding a linear decomposition into a base designer plus an extension designer. Mathematically,
we want the visual mapping to be a homomorphism. In the language of category theory, we can say this is a functor mapping.

```
 TemplateEditor = BaseModelEditor + ExtModelEditor
```

Mapping the mathematical formulas above to concrete Office software, our goal is to leverage some built-in extension mechanism in Office to store extension information and automatically provide a visual design interface for it.
If a piece of software is built according to the principles of Reversible Computation, it will inevitably adopt a paired design scheme of (data, ext_data), allowing extension data to be introduced at any DSL syntax node.
With an extended xdef metamodel, we can define the meta semantics of extension data and automatically generate a visual editor based on the xdef model definition.
However, because Office was not designed according to Reversible Computation theory, we can only reinterpret its built-in features via certain hacks, repurposing some of them into metadata extension mechanisms.
NopReport’s concrete approach is to use Word’s hyperlink mechanism and interpret hyperlinks of the form `xpl:xxx` as extension information nodes.

![word-report.png](../dev-guide/report/word-template/word-report.png)

Using hyperlinks is just a minor technical detail. We could also choose to use Word’s comment mechanism to store extension information.

> It’s important to emphasize that with the approach above, we can implement templated extensions for any model, requiring only a small amount of Delta information.
> For further analysis of the linear mapping principle, see my article [Designing Low-Code Platforms Through the Lens of Tensor Products](https://zhuanlan.zhihu.com/p/531474176)

## General Structural-Layer Construction Principles

It’s intuitive to many that ever since Office adopted OOXML for XML storage, handling Office formats has become much simpler. There are now many Office template libraries like jxls and poi-tl; under the hood they still operate on Office documents via Apache POI,
and they provide simpler, more intuitive template generation by deeply encapsulating POI. Yet NopReport can replace the functionality of these libraries with very little code while offering better extensibility—why?

Essentially, it’s because the POI library operates at a type-specialized object layer, whereas NopReport operates at a uniform XML layer. XML can be regarded as a general structural representation.
Reversible Computation emphasizes that before information is transformed into business objects, there exists a unified structural representation layer where many general operations can be performed directly, without pushing processing down to the object layer. At the object layer, each object type differs, leading to differing processing rules.
Just as the diversity of architectural works is underpinned by unified engineering mechanics, from the perspective of the structural layer, many things that differ at the business level are fundamentally the same, obeying the same structural construction principles and amenable to the same tools and methods.

In this process, the XPL template sublanguage in the XLang language is of particular importance. What is XPL’s mathematical role? It is responsible for transforming arbitrary AST expression trees into executable logic via syntax-directed translation.
Syntax-directed translation means that when we encounter a particular grammar node, we automatically trigger the corresponding translation rule. This is essentially a custom component mechanism based on context-free grammars.

```xml
<doc>
   <orm:GenPackageDiagram />
   <orm:ForEachEntity>
      ...
   </orm>
</doc>
```

Each tag like `<orm:ForEachEntity>` triggers the corresponding component definition and executes the executable logic within that component.

For further analysis of structural-layer construction principles, see my article [A General Delta Mechanism](https://zhuanlan.zhihu.com/p/681801076)

The low-code platform NopPlatform, designed based on Reversible Computation theory, is open source:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development examples: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible Computation principles and Nop platform introduction and Q&A_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
<!-- SOURCE_MD5:16111c30b9784af88a576dea303212a6-->
