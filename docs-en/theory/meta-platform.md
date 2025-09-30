# How to Develop a Platform that Can Develop Low-Code Platforms

Assume we now have a platform X that can develop low-code platforms. We can then look at how it operates at an abstract level.

1. First, low-code platforms are not limited to visual form editing; they should include any technological platform that simplifies programming through model abstraction. This means our platform X should possess the common knowledge shared across all technology platforms and be able to implement that knowledge as a general technical solution. For example, when developing an ORM model and a workflow model, they should share more than 50% of common knowledge; only then can we say that a platform X can be used to develop these two different models. This raises the question: what exactly is this common knowledge?

2. If we use platform X to develop workflows, `WorkflowDesigner = PlatformX<WorkflowDesignerSpec>`. At the conceptual level, PlatformX acts as a generator: it generates a WorkflowDesigner based on the definition of WorkflowDesignerSpec.

WorkflowDesignerSpec corresponds to a workflow designer model, and PlatformX can likewise provide a designer to design this Spec.

`WorkflowDesignerSpecDesigner = PlatformX<SpecSpec>`, which means that based on the Spec of the Spec, PlatformX should generate a Designer for the Spec. In other words, a model is defined by a metamodel, and a metamodel is defined by a meta-metamodel, and this chain can continue indefinitely. In the Nop platform, xdef defines the metamodel, and xdef.xdef defines the meta-metamodel, thereby truncating this chain. Ultimately, we only need xdef; xdef uses xdef to define itself.

3. Behind every visual designer there is a mental model corresponding to what the designer’s inputs and outputs are. The content provided by the visual designer can be regarded as the WYSIWYG visual form of this model.

```
   WorkflowEditView ~ WorkflowDefinition
```

The visual editing form (VisualModel) and the textually persisted workflow model file (Text DSL) can be regarded as a reversible transformation.

4. By systematizing the above concepts and introducing the notion of Delta to bridge differences in information granularity between abstraction levels—thus enabling seamless composition of multiple DSLs—we naturally arrive at the core formula of Reversible Computation:

```
  App = Delta x-extends Generator<DSL>
```
<!-- SOURCE_MD5:4ae399b7d41371f7ebe815a69c55de3c-->
