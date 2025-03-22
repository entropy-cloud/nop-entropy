# How to Develop a Platform for Low-Code Development

Assume we have a platform X capable of developing low-code platforms. We can examine its operating principles at the abstract level.

1. **Scope of a Low-Code Platform**  
   A low-code platform is not limited to visual form editing but should include all technologies that enable programming through model abstraction. This means our platform X should possess shared knowledge among all platforms and be able to implement this knowledge as a universal implementation. For example, developing an ORM model and a workflow model should share over 50% of common knowledge for platform X to claim it can develop both models. The question then arises: What exactly constitutes these commonalities?

2. **Developing Workflow Designers**  
   If we use platform X to develop workflows, `WorkflowDesigner = PlatformX<WorkflowDesignerSpec>` at the conceptual level. In this context, platform X acts as a generator that creates a WorkflowDesigner based on the WorkflowDesignerSpec definition. The WorkflowDesignerSpec corresponds to a workflow designer model, and platform X can also provide a designer for this Spec. Further, `WorkflowDesignerSpecDesigner = PlatformX<SpecSpec>` means that platform X generates a SpecDesigner from the Spec's definition. This implies that models are defined by meta-models, which in turn are defined by higher-level meta-models. This chain can continue indefinitely. In the Nop platform, xdef defines meta-models, while xdef.xdef defines higher-level meta-models, thereby breaking the chain. Ultimately, we only need xdef, as it defines itself.

3. **Visual Designers and Their Underlying Models**  
   Every visual designer has an underlying mental model that corresponds to the designer's input and output. The visual designer provides content that is seen as the visualization of the model (VisualModel). This content is stored in a text-based format (Text DSL) that can be reverse-converted back to the VisualModel. Thus, there is an invertible relationship between the VisualModel and the Text DSL.

4. **Systemizing Concepts**  
   Systematize the concepts by filling in the gaps between different abstraction levels. This involves bridging information granularity differences across various abstraction layers. By doing so, we can achieve seamless integration of multiple DSLs into a unified framework. This naturally leads to deriving an invertible mathematical formula at the core of computational theory.

```
  App = Delta x-extends Generator<DSL>
```
