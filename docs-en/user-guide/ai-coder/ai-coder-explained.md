# AI Disrupts Development: The Open-Source Framework NopAiCoder Realizes "Input Requirements Document, Output a Complete Application"

## qwen3:8b Results

1. When the output is too long, some XML may be malformed, mainly due to difficulties with namespaced attributes. For example, it generated `orm"ref-prop`.

What is the function that AI provides in program space? AI is a multi-input and multi-output function.

If a status is ENABLED/DISABLED, you can use a general active-status dictionary, and generic disable and enable functions are already provided.

Projection choice; independent part A + independent part B + mixed part (Delta)

Y = A + B + .. + Delta
If they are not completely independent, then the Delta part concentrates the mixed information.

Interaction information between Agents should not be a black-box model; instead, it should be an analyzable vector decomposed along dimensions, and can be organized as analyzable + NLP-style chunks.

In cases where the input explicitly requires it, if we still cannot guide the model to a symmetry-breaking situation.

Inevitability of hallucinations
An AI model outputs a set of feasible solutions across all logically possible worlds, whereas the physical world humans inhabit is only a single sample resulting from specific random events (symmetry breaking). Demanding that AI output only facts of this universe is essentially asking it to select a unique solution from infinite possibilities—an information-theoretic impossibility.

Solvability of local hallucinations
When the context provides sufficient information (symmetry-breaking conditions), errors that persist stem from defects in the cognitive structure:

- Inability to treat the prompt as a symmetry-breaking perturbation factor
- Lack of a strict mapping capability to algebraic structures (compositionality/reversibility)


Reasoning collapse from a group-theoretic perspective:

Ideal case: The model should construct a reasoning group $G = \langle \text{premises}, \oplus \rangle$, where $\oplus$ is the composition operator.

Practical deficiencies:

- Compositionality breakdown: when $A \oplus B$ should yield $C$, the model outputs $C'$
  Example: Given “A is B’s grandfather” and “B is C’s father,” it outputs “A is C’s uncle”
- Loss of reversibility: unable to backtrack from $C$ to verify $A \oplus B$
  Example: After asserting “A is C’s grandfather,” it cannot list the reasoning path

Root cause:
The model parameter space fails to form a homomorphic mapping $h: G_{\text{real}} \to G_{\text{model}}$, leading to distortion of the group structure.


Non-uniqueness of truth: Logically, there may exist multiple self-consistent versions of facts (e.g., “the Earth is flat” could hold in a universe with different physical laws); the current world is merely a contingent realization among many possibilities (a result of spontaneous symmetry breaking).

Reasonableness of AI: When the model outputs a “wrong” fact, it may actually be correctly describing another possible world (a truth in a parallel universe). Thus, so-called “hallucinations” are essentially a misalignment between the model’s output and observed facts of this universe, rather than logical errors.

Root cause of inevitability: Humans cannot logically exclude other possibilities completely (e.g., cannot prove “the physical laws are identical across all universes”), hence AI cannot absolutely guarantee outputs that only match facts of our universe.

2. My original misunderstanding
   Faulty assumption: I assumed there exists an absolute frame of reference (“the only truth”), and treated outputs deviating from this standard as “errors caused by model defects.”

Correct perspective:

- Within a modal logic framework, AI outputs the set of true propositions across all possible worlds
- “Hallucination” is a failure to locate the index of this world (@actual_world), rather than the proposition itself being invalid.

For raw data, can we optimize training by injecting group structures via an algebraic enhancer? At least the Delta concept can be trained separately with targeted methods.

The document itself is imprecise; depending on different background knowledge it can be interpreted in many ways. A DSL model, however, is precise.

# deepseek-r1

deepseek-r1 outperforms deepseek-v3 in long outputs; V3 tends to omit similar parts of the output, for example,

When initially organizing requirements, do not specify the detailed structure of requirements, otherwise information may be missed.

The requirement to output `Requirement Chapters (numbers only)` cannot be strictly followed.
V3 is more prone than R1 to missing information.

Have the AI establish an analytical framework to help assess whether the current DSL design is conducive to AI generation, and explain why in detail.

I/O placement optimization

✅ Centralized parameter management: move input/output to a dedicated <parameters> section; semantics are clearer (the original metadata scope was too broad)

✅ Increased salience: as top-level elements, the AI can more easily prioritize interface definitions during generation

✅ Referential predictability: when generating procedural logic, the AI can accurately predict parameter locations (/flow/parameters/input)

AI feedback mechanism separation

✅ Single-responsibility: a dedicated <aiFeedback> section resolves the metadata pollution issue in the original design

✅ Generation-friendly: the AI can freely output refactoring suggestions without affecting the core DSL structure

✅ Better collaboration: human developers can quickly locate AI feedback (separated from parameters/steps)

## Experience

A key design decision is to disable else statements: intentionally disallow the model from using an 'else' block and use only 'if' blocks. This requires the model to define explicit conditions for each path, a design that significantly improved performance in evaluation.
From Parahelp’s practice we can distill the core principle for professional-grade prompts: an explicit order of thinking

- Specify a structured format for the model’s processing steps
- Use markdown and XML to organize information and role definitions
- Assign clear roles (e.g., “Manager”); emphasize key directives
- Use words like “Important” and “Always” to highlight critical requirements

- An if block can be used anywhere in steps and plans, and should be simply wrapped with an <if_block condition=''> tag. <if_block> should always have a condition.
  Use redundancy to ensure that key directives are followed

## Prompt Changes

1. Core database table design --> Database table design. Limiting the requirement to core tables leads to omission of some tables.

## Doubao model `doubao-1.5-pro`

1. Incorrect attributes for child tables in database design

## Technical Roadmap

Core ideas:

1. Coordinate mapping

2. Local functions + adaptive correction

3. The metamodel provides semantic validation; syntax adopts a general grammar

4. Pair data + metadata, store information compactly. For example, associate useCaseNo with business menus

Predicament of AIGC: hallucinations + limited context

Analysis: structure space is too large; reasoning depth is limited

Solution: match the complexity of solution space with problem space, project to subspaces, and then glue results from multiple subspaces.

Different logical agents modify the same code structure. Blueprint superposition.

Generate skeleton + local polishing

Supplement information via MCP.

Place curated normative example code in a specific directory, then build a RAG index.

Establish automated validation standards and require Delta-based changes. Automatically compress the dialogue process.

1. Decide which of multiple solutions is better

2. Compare generations across multiple AI models

3.

Problem space in which the requirements reside --> software structure space of the programming languages and frameworks we use --> feasible space of all programming knowledge across different languages (and versions) known to AI foundation models.

These spaces are inherently mismatched. The hallucination problem: if you analyze carefully, you may find the AI’s output would be reasonable in some parallel universe—there exists such a fact in that universe. Judging purely from logic cannot tell whether it is a hallucination.
The world we inhabit is the result of spontaneous symmetry breaking.

Insufficient context window: attention can only focus on a limited scope.

Solution: project into different subspaces, solve one local problem at a time, then stitch them into a whole. Orthogonality is not required. However, overlapping regions must be recognizable, and formal conversion should be performed automatically.

Reversible Computation theory provides a systematic projection–gluing scheme.

For problems with well-established solutions, ask for direct answers.
For complex logic implementation problems, require the AI to choose an XML format it deems most appropriate to express the solution, and to repeatedly check and improve that XML format. Finally, map it to the corresponding TaskFlow implementation.

XDef metamodel for human users: primarily expresses formatting information.

For AI: certain formatting information can be inferred automatically from semantics and need not be specified in detail.

If local corrections are possible, there is no need to repeatedly argue details with the AI. In fact, it is hard to strictly adhere to them anyway—for example, field case conventions.

If redundant output is detected, you can specify a field with related semantics to steer it.

Adjust expression format to avoid duplicate expressions or complex matching. For example, the orm:ref_table attribute.

The XDef metamodel is particularly suitable for AI understanding.

```xml

<orm x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:xdef="/nop/schema/xdef.xdef"
     xmlns:orm="orm" xmlns:ext="ext">

  <entities xdef:body-type="list" xdef:key-attr="name">
    <entity name="!english" displayName="chinese">
      <comment>description</comment>
      <columns xdef:body-type="list" xdef:key-attr="name">
        <column name="!english" displayName="chinese" mandatory="boolean" primary="boolean"
                ext:dict="dict-name"
                stdDomain="std-domain" stdSqlType="!sql-type" precision="int" scale="int"
                orm:ref-table="table-name"
                orm:ref-prop="parent-to-children-prop" orm:ref-prop-display-name="chinese"/>
      </columns>
    </entity>
  </entities>
</orm>
```

Simply put, remove duplicate content from the DSL and keep only the unique parts. Then replace values with format-constraint descriptions.

Note that the xdef file defines attributes like `xdef:body-type` and `xdef:key-attr`. These are part of the XDef metamodel, used to precisely specify attribute semantics and automatically validate them during parsing.
However, for large AI models, this information is redundant and can be distracting.

```xml

<orm>
  <entities>
    <entity name="english" displayName="chinese">
      <comment>description</comment>
      <columns>
        <column name="english" displayName="chinese" mandatory="boolean" primary="boolean" ext:dict="dict-name"
                stdDomain="std-domain" stdSqlType="sql-type" precision="int" scale="int"
                orm:ref-table="table-name" orm:ref-prop="parent-to-children-prop"
                orm:ref-prop-display-name="chinese"/>
      </columns>
    </entity>
  </entities>
</orm>
```

The XDef metamodel used for AI is specially customized to emphasize semantics. It is not the same as the one built into the Nop platform, but the format remains compatible.

## Prompt Model Design

The prompt template needs the following capabilities:

1. Ability to perform preprocessing—we may need to derive data
2. Ability to perform postprocessing

By switching promptName, you can obtain prompt templates with dynamic processing capabilities.

A prompt is essentially a function implementation, so it should have inputs and outputs. Currently, all composable functions on the Nop platform are in the Map func(Map) format
inputs => outputs map to map

## AI Integration

Because it is a function abstraction, it is easy to integrate into the Nop platform via metaprogramming.

### NopTaskFlow Orchestration

```xml

<task>
  <steps>
    <step name="designOrm" customType="ai:TaskStep" ai:promptName="coder/orm-design">
      <description>根据需求文档的描述，设计ORM模型</description>

      <input name="requirements"/>

      <output name="ormModelText" value="${RESULT.xml()}"/>
    </step>
  </steps>
</task>
```

### NopGraphQL Service Integration

Encapsulate calls to the large AI model as service functions

```
<query name="designOrm" ai:promptName="coder/orm-design">
  <ai:chatOptions provider="ollama" model="deepseek-r1:14b" />
</query>
```

Automatically generate the corresponding action definitions based on the inputs and outputs defined in the prompt model.

## DSL Structure Space Partitioning Based on Domain Knowledge

First, decompose by object. Concepts related to the same object name belong to a group.
Vertical decomposition: the primary decomposition dimension.

Separate overall layout from field presentation.

## Free Conversion Among Multiple Representations

The domain model can freely extract information. For example, the ORM model can be simplified to include only necessary fields, or presented in Java form, etc.

However, in general, we can enforce XML for outputs; this facilitates automatic parsing and partial validation.

XML has self-validation capabilities. If a tag is not closed, parsing fails and a retry will be automatically triggered.

# AI Disrupts Development: The Open-Source Framework NopAiCoder Realizes "Input Requirements Document, Output a Complete Application"

## Introduction: A New Paradigm of AI Programming

A revolutionary change is underway in software development—thanks to the open-source framework NopAiCoder, developers can now simply input a requirements document and have the system automatically output a complete application. This breakthrough is built on innovations in Reversible Computation theory and the XDef metamodel, providing a systematic solution to the “hallucination problem” and “context window limitations” in AI programming.

## Technical Architecture: Bridging Three Cognitive Spaces

NopAiCoder’s core innovation lies in establishing mappings across three key spaces:

1. Problem Space: the business domain where user requirements reside
2. Structure Space: technological implementations in specific programming languages and frameworks
3. Feasible Space: all programming knowledge mastered by large AI models

The fundamental challenge for traditional AI programming tools is that these three spaces are naturally mismatched. NopAiCoder solves this via a “project-and-stitch” mechanism:

- Projection mechanism: decompose complex problems into different subspaces for processing
- Gluing mechanism: use Reversible Computation theory to organically integrate local solutions
- Overlap identification: automatically detect overlapping parts of solutions in different subspaces and perform formal conversions

## XDef Metamodel: The Key to Human–AI Collaboration

NopAiCoder designs two XDef metamodel variants, each optimized for collaboration:

### Human-oriented XDef

```xml

<orm x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     xmlns:xdef="/nop/schema/xdef.xdef" xmlns:orm="orm" xmlns:ext="ext">
  <entities xdef:body-type="list" xdef:key-attr="name">
    <entity name="!english" displayName="chinese">
      <comment>description</comment>
      <columns xdef:body-type="list" xdef:key-attr="name">
        <column name="!english" displayName="chinese" mandatory="boolean"
                primary="boolean" ext:dict="dict-name" stdDomain="std-domain"
                stdSqlType="!sql-type" precision="int" scale="int"
                orm:ref-table="table-name" orm:ref-prop="parent-to-children-prop"
                orm:ref-prop-display-name="chinese"/>
      </columns>
    </entity>
  </entities>
</orm>
```

### AI-oriented Simplified XDef

```xml

<orm>
  <entities>
    <entity name="english" displayName="chinese">
      <comment>description</comment>
      <columns>
        <column name="english" displayName="chinese" mandatory="boolean"
                primary="boolean" ext:dict="dict-name" stdDomain="std-domain"
                stdSqlType="sql-type" precision="int" scale="int"
                orm:ref-table="table-name" orm:ref-prop="parent-to-children-prop"
                orm:ref-prop-display-name="chinese"/>
      </columns>
    </entity>
  </entities>
</orm>
```

Key differences:

- The human version includes complete format constraints and validation information
- The AI version emphasizes semantic expression and removes redundant metadata
- The two remain format-compatible to ensure seamless conversion

## Prompt Engineering: Dynamic Functional Design

NopAiCoder designs prompts as functional units with complete input–output specifications:

1. Preprocessing: supports data derivation and transformation
2. Postprocessing: automatic result formatting and validation
3. Dynamic switching: implement different processing logic by switching promptName

Function signature is unified as a Map-to-Map transformation:

```typescript
inputs: Map → outputs: Map
```

## System Integration: Seamless Inclusion in the Development Workflow

### Task Flow Orchestration Example

```xml

<task>
  <steps>
    <custom name="designOrm" customType="ai:TaskStep" ai:promptName="coder/orm-design">
      <description>根据需求文档的描述，设计ORM模型</description>
      <input name="requirements"/>
      <output name="ormModelText" value="${RESULT.xml()}"/>
    </custom>
  </steps>
</task>
```

### GraphQL Service Integration

```xml

<query name="designOrm" ai:promptName="coder/orm-design">
  <ai:chatOptions provider="ollama" model="deepseek-r1:14b"/>
</query>
```

The system automatically generates the corresponding API interfaces based on the inputs and outputs defined by the prompt, requiring no additional coding by developers.

## Structured Handling of Domain Knowledge

NopAiCoder adopts an innovative DSL space partitioning strategy:

1. Object-centric decomposition: concepts related to the same object name are automatically grouped
2. Vertical decomposition: used as the primary decomposition dimension to ensure logical consistency
3. Presentation separation: decouple the core model from presentation logic

## Free Conversion Across Modalities

The system supports lossless conversion of domain models across different forms:

- Can be simplified to an ORM model that only contains necessary fields
- Can be converted into a Java class implementation
- By default outputs standardized XML for automatic validation

XML’s self-validation features (such as closed-tag checks) provide an automatic error-correction mechanism for AI outputs, significantly improving the reliability of generated code.

## Conclusion: A New Era of Software Development

Through an innovative technical roadmap, the NopAiCoder framework achieves fully automatic generation from requirements documents to complete applications. This breakthrough not only greatly improves development efficiency but also redefines the boundary of human–AI collaborative programming. As the technology continues to evolve, AI-assisted development will evolve from a “tool” to a “collaborator,” ushering in a new era of software development.
<!-- SOURCE_MD5:b26dc0df14291a86d819285bbae6f702-->
