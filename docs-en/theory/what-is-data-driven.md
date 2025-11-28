# What is data-driven? How does it differ from model-driven, domain-driven, metadata-driven, and DSL-driven?

Nowadays, many low-code platforms claim their platforms are data-driven. Someone in a group raised a question: What is “data”? Aren’t forms data? Metadata is data too. Isn’t the entire program data? Since all of these are data, and even a Java program can be regarded as data, what meaning does the term data-driven still have?

> We are not discussing data-driven in the context of data analysis here. In that context, “data” refers to raw data without schema abstraction, and programs assist the analysis to derive implicit data distribution patterns.

“XX Driven” is one of the common buzzwords in software engineering: replace XX and you get data-driven, model-driven, domain-driven, metadata-driven, DSL-driven, and a whole bunch of other drivens. A natural question is: what distinguishes these different drivens? Is there any need to artificially invent so many different concepts?

In this article, I will explain the relevant concepts using concrete practices in the Nop platform.

## I. What does data drive?

Data-driven can be seen as being determined by data. The essence of “driven” is interpretive execution at some level of abstraction. For example, when expressing logic, we need to perform conditionals and loops. If, in our pre-implemented functions, we do not directly encode exactly when to branch or loop, but instead let some externally supplied data determine that, then this is a form of data-driven. From another perspective, the functions we write can be viewed as a kind of virtual machine: they read data and interpret-execute that data as program code at an abstract level.

Here are some concrete examples of data-driven approaches:

1. In a typical code generator, what files to generate and what directory structure they belong to are written into control code, for instance, a class like MyBatisCodeGenerator. In the Nop platform, the data-driven code generator is structured as a general-purpose XCodeGenerator plus freely organizable code generation templates, where template data drives XCodeGenerator to generate code. Whether a particular file is generated and how to loop can be expressed via template paths:
   `/{packageModel.name}/{enabledWeb}{entityModel.name}BizModel.java`

   The above path encodes double loops over packageModel and entityModel, and only generates the corresponding BizModel Java class when enabledWeb is true. For a detailed introduction, see [Data-driven Delta-based code generator](https://zhuanlan.zhihu.com/p/540022264)

2. In typical unit testing, both test data preparation and result verification must be coded. The Nop platform’s data-driven automated testing framework adopts the pattern of a general-purpose automated test engine plus a set of json and csv data; it then automatically initializes database tables and performs result verification. The specific test case code degenerates to a simple function call.

   ```java
   request = input("request.json");
   response = myService.myMethod(request);
   output("response.json", response);
   ```

   During the recording phase, the NopAutoTest framework automatically records the response as json data and replaces variables with their names; during verification, it automatically compares the recorded results to the execution results. For details, see [Automated testing in a low-code platform](https://zhuanlan.zhihu.com/p/569315603)

## II. What is not data-driven?

In a sense, all programs are data-driven, because binary code is indeed a kind of data that drives a general Turing machine to perform all possible computations (Turing-complete). Generally, however, when we say data-driven, we mean using some custom form of data to drive specific business logic (a computational model that can only solve certain domain-specific problems), rather than driving a general Turing machine computational model. Whenever we implicitly define a specific computational model through a set of functions—one that executes different logic based on input parameter data—and that set of parameter data can be understood independently of our functions (constituting some semantic independence and completeness), we have effectively introduced a new data-driven approach. In everyday development, we target the general Turing machine model, so we simply don’t call it data-driven.

In other words, `DataDriven = DomainDriven = MetaDataDriven = ModelDriven = DSL`: these concepts are fundamentally the same—they boil down to establishing a specific, non-general logical model within a particular domain.

However, in practice, these concepts emphasize different aspects:

* Model-driven often emphasizes the technical neutrality of models; the same model expression can correspond to multiple different concrete implementations. For example, Model-Driven Architecture (MDA) distinguishes between Platform-Independent Model (PIM) and Platform-Specific Model (PSM). Some scenarios also emphasize visual model design: the model may be saved in binary form and cannot be directly understood.

* DSL generally emphasizes the textual expression of domain-specific logic: you can read and write DSL text directly, and a corresponding visual editing tool is not necessarily provided. (Based on the principle of Reversible Computation, the Nop platform emphasizes that DSL text and visual editing are merely two information-equivalent presentations of the same model; it can automatically establish bidirectional reversible transformations between them and automatically generate a corresponding visual designer from a designer definition. In other words, you don’t need to handcraft a design tool for each DSL—this can be standardized. See [nop-idea-plugin plugin](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-idea-plugin) for details.)

* Metadata-driven, in current practice, typically includes only data structure and data type definitions. Metadata is “data that describes data,” so it is often regarded as an extended expression of type concepts in programming languages; for example, database table schema definitions are metadata for table data. Many DSLs include complete logical function definitions and logical abstraction mechanisms such as custom functions and external function libraries, whereas support for such dynamic logic is generally weak in metadata practice. Moreover, interpreting DSLs as “data that describes data” can be confusing for most people.

* Domain-driven emphasizes that the concepts we use to express business should be as close as possible to the user’s domain concepts, rather than the technical concepts familiar to programmers—akin to defining a DSL specific to the current business domain (a domain-specific language). In practice, however, defining a conceptually complete language is costly, and real languages require rich grammar rules with highly composable syntax elements. Few people have the capability to abstract compositional patterns. As a result, in practice, the so-called domain language in domain-driven design often degenerates into a domain-specific glossary (a list of terms), and the composition of these concepts is loosely chained through a small number of process functions. Few people systematically consider completeness of composition rules between concepts, and so on. (A language is jointly defined by vocabulary and grammar composition rules, whereas domain-driven approaches often have only simple domain vocabulary and a handful of ad-hoc service functions.)

* Data-driven, in principle, includes metadata-driven. In most people’s minds, programs are written by programmers, while data is entered by customers—it is what customers can understand and need to understand, and consequently manage. Therefore, data-driven generally requires organizing data into forms that are easy for customers to understand and simplifying it as much as possible at the operational level. In the era of widespread big data analysis, data can also refer to raw data without schema abstraction, with programs assisting the analysis to derive implicit distribution patterns.

## III. Data-driven in the Nop platform

The Nop platform systematically applies the principle of Reversible Computation, that is, by leveraging the general computational pattern Y=F(X)+Δ, it transforms a large number of problems into ones defined and solved with DSLs.

App = Δ ⊕ Generator⟨DSL⟩

What the Nop platform provides is a programming paradigm known as Language Oriented Programming: before solving business problems, we first define a DSL for the current business domain, and then use that DSL to solve the business problems.

The design in the Nop platform is not aimed at a single DSL dedicated to low-code, but instead systematically provides a way to create new DSLs and a technical approach to seamlessly fuse different DSLs.

// Horizontal decomposition, producing multiple DSL
App = Δ ⊕  G₁⟨DSL₁⟩ ⊕ G₂⟨DSL₂⟩ + ...

// Deep decomposition, producing multiple DSL
App = Δ ⊕ G₁⟨Δ₂ ⊕ G₂⟨Δ₃ ⊕ G₃⟨DSL₃⟩⟩⟩

A large number of DSLs form a DSL forest that collaborates to solve problems.

In daily development, much of our work is not writing business-essential logic with intrinsic business value, but rather writing various business-agnostic format transformation logic. For example, exporting data to Excel format, importing Excel format into the database, and translating front-end data objects into SQL query conditions. We keep busy solving form problems rather than solving business problems.

Reversible Computation, at the theoretical level, indicates the technical route needed for mutual conversion and fusion among DSLs. Based on the principle of Reversible Computation, the Nop platform automatically derives front-end visual presentation and visual designers from metamodel definitions, automatically converts data query objects to SQL query conditions, and automatically implements bidirectional conversion between data objects and Excel files, among other capabilities.

By introducing numerous shared metamodels and interoperable DSLs at various layers, and by leveraging the abstraction capabilities of the XPL template language to template logical structures (logical flow orchestration), the Nop platform advances the application of metadata and metamodels to a new level and finds a natural model-driven implementation path for Domain-Driven Design (DDD).
<!-- SOURCE_MD5:a5d932d3741d9568aee94184b44de4a5-->
