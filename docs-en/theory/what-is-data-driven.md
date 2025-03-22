# What is Data-Driven? How does it differ from Model-Driven, Domain-Driven, Meta-Driven, and DSL-Driven?

Many low-code platforms claim their platforms are data-driven. This has led to questions: What exactly is data? Is the form not data? Is meta-data also data? Is the entire program considered data? Even Java programs can be seen as data. If all these things are considered data, what does the term "data-driven" even mean anymore?

This discussion does not pertain to the context of data analysis under "data-driven." In that sense, data refers to raw, unabstracted data, and programs analyze it to derive implicit data patterns.

"XX-Driven" is a common term in software engineering. Translating it literally, it becomes "X-Driven," where X can be replaced with various terms like "Data," "Model," "Domain," "Meta," or "DSL." Replacing XX with other letters gives us terms like "Data-Driven," "Model-Driven," etc. The natural question arises: What is the difference between all these different "-Driven" terms? Why do we create so many different concepts?

In this document, I will explain these concepts using examples from the Nop Platform.

## 1. What is Data-Driven?
Data-driven can be seen as **driven by data**. At its core, it means that decisions and operations are based on data rather than hardcoded rules. For example, in logic development, if our functions do not explicitly define when to perform judgment or loops but instead determine these actions based on external data inputs, this is considered a form of data-driven development. From another perspective, the functions we write can be seen as a kind of "virtual machine" that reads data and processes it at an abstract level.

### Examples of Data-Driven
1. **Code Generators**: A general code generator must produce which files? What is the directory structure like? For example, there's a MyBatisCodeGenerator class. The Nop Platform's data-driven code generator uses template data to drive the XCodeGenerator, allowing users to freely organize the code generation templates using template data. You can determine whether a specific file should be generated and how often it loops through the packageModel.name and entityModel.name variables in the path `/{packageModel.name}/{enabledWeb}{entityModel.name}BizModel.java`. The actual path is represented as `{/packageModel.name}/{enabledWeb}{entityModel.name}BizModel.java`.

   This path format indicates that files will be generated for each combination of packageModel.name and entityModel.name where enabledWeb is true. If you want to generate BizModel.java, you can use this template data.

2. **Unit Testing**: In general unit testing, specific test data needs to be prepared and results validated within the code. The Nop Platform's data-driven automated testing framework follows a pattern: a generic test engine + JSON/Csv data + database table initialization + result validation. For example:
   ```java
   request = input("request.json");
   response = myService.myMethod(request);
   output("response.json", response);
   ```
   In the recording phase, NopAutoTest records the response as JSON data and replaces variables with their names in the test case. During validation, it compares the recorded results with the actual execution results.

## 2. What is not Data-Driven?
From another perspective, all programs are data-driven because binary code itself is data. It's like a universal Turing machine capable of performing all possible computations. The difference lies in whether we're driving general-purpose machines or specific business logic machines.

The term "data-driven" refers to using custom data forms to drive specific business logic (specific compute models) rather than the entire general-purpose Turing machine. For example:
- Data-Driven: Uses custom data formats to drive business-specific logic.
- Model-Driven: Focuses on domain-specific models, often with MDA (Model Driven Architecture), distinguishing between PIM (Platform Independent Model) and PSM (Platform Specific Model).
- Some contexts emphasize model visibility through visualization, while others focus on binary forms, making them difficult to understand.

### Example:
```java
request = input("request.json");
response = myService.myMethod(request);
output("response.json", response);
```
In this case, the code is data-driven because it reads request.json and processes it with myService.myMethod before writing the response to response.json. This is a simple function call.

# DSL and Data-Driven Approaches in Nop Platform

## 1. DSL (Domain-Specific Language)

- **DSL generally emphasizes the textual representation of domain-specific logic**.  
  - It is noted that a **DSL can be intuitively read and written**, without necessarily requiring specialized tools for each DSL.  
  - The **Nop platform leverages the invertible computation principle**, which relies on a generic pattern such as $Y = F(X) + \Delta$.  
  - This allows the platform to automate the generation of both **DSL text** and **visualization tools**, eliminating the need for manual tool creation.  
  - For reference, please see the [nop-idea-plugin plugin](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-idea-plugin).

## 2. Metadata-Driven Practices

- **Metadata-driven approaches currently focus on data structures and type definitions**.  
  - Metadata is considered as **data about data**, thus serving as an extension of type concepts in programming languages.  
  - For example, database table definitions often include metadata about the underlying data.  
  - Many DSLs incorporate complete logic definitions, including custom functions, external libraries, and abstract mechanisms for dynamic logic support.  
  - However, **dynamic logic support is generally weak in current metadata-driven practices**.  

## 3. Domain-Driven Approach

- **Domain-driven approaches prioritize concepts that align with user understanding of the domain**, rather than programmer-specific concepts.  
  - The goal is to define a DSL tailored to the current business domain.  
  - Defining such an exhaustive language, however, requires significant effort and resources.  
  - It typically involves rich syntax rules and combinable elements, but very few teams possess the capability to abstract these combinations effectively.  
  - In practice, **domain-driven so-called languages often degenerate into a set of domain-specific terms (术语表)**.  
  - These are then combined using limited procedural functions in a vague manner, as seen in many real-world implementations.  

## 4. Data-Driven Principle

- The **data-driven principle generally includes metadata-driven aspects**.  
  - While programs are written by developers, data is typically entered by users.  
  - **Metadata is crucial for user understanding and management**, enabling data transformation into formats that align with business needs.  
  - In today's big data era, "data" often refers to raw, unstructured information that requires abstraction to reveal hidden patterns.  

## 5. Nop Platform Metadata-Driven

- The **Nop platform has implemented the invertible computation principle**, which is based on a generic pattern such as $Y = F(X) + \Delta$.  
  - This allows for automation of both DSL text definition and visualization tool generation.  
  - No specialized tools are required for each DSL, enabling centralized implementation.  

$$
\DeclareMathOperator{\Extends}{\mathcal{x-extends}}
App = Delta \Extends Generator\langle DSL\rangle
$$

## Code Examples

### Example 1: Simple DSL Structure

$$
\begin{aligned}
& \text{// Horizontal decomposition, generating multiple } DSL \\
App &= Delta + G_1\langle DSL_1\rangle + G_2\langle DSL_2\rangle + ... \\
\\
& \text{// Vertical decomposition, generating multiple } DSL \\
App &= Delta + G_1\langle Delta_2 + G_2\langle Delta_3 + G_3\langle DSL_3\rangle\rangle\rangle\rangle
\end{aligned}
$$

### Example 2: Nop Platform Invertible Computation

- The **Nop platform systematically converts complex problems into DSL**.  
- This is achieved through the invertible computation principle, where $Y = F(X) + \Delta$ represents a generic pattern.

## Summary

- **DSLs are intuitive and require minimal tools for implementation** due to the Nop platform's invertible computation capability.  
- **Metadata-driven approaches focus on data structures and logic definitions**, though dynamic logic support is limited in current practices.  
- **Domain-driven approaches aim to align concepts with user understanding but face challenges in abstraction and implementation**.  
- The **data-driven principle emphasizes user-friendly data management through metadata**, enabling effective transformation and analysis.


The Nop platform introduces a large number of shared meta-models across multiple levels, enabling collaborative work through DSL (Domain-Specific Language). By leveraging the abstraction capabilities of the XPL template language, it achieves the modularization of logical structures (logic flow orchestration). The platform advances the application of meta-data and meta-models to a new height. Additionally, it provides an implementation plan for Domain-Driven Design (DDD), seeking a natural approach to model-driven solutions.

