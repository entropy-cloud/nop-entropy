# Nop Platform - Code Generation Framework (nop-codegen)

The nop-codegen module provides the core code generation framework for the Nop Platform. It supports generating code in multiple languages, including Java, JavaScript, and Vue, and provides tools for working with GraalVM configurations and Antlr parsers.

## Core Features


### Code Generation Framework
- **XCodeGenerator**: The main entry point for code generation tasks
- **CodeBlock**: Represent code blocks with proper indentation and formatting
- **MethodBlock**: Generate method declarations and implementations
- **AbstractGenCode**: Base class for code generators with common functionality

### GraalVM Integration
- **ReflectConfigGenerator**: Generate GraalVM reflection configuration
- **ProxyConfigGenerator**: Generate GraalVM proxy configuration
- **ResourceConfigGenerator**: Generate GraalVM resource configuration

### Antlr4 Integration
- **AntlrParserConfig**: Configuration for Antlr4 parser generation

### Maven Support
- **MavenModelHelper**: Utilities for working with Maven projects

### Code Generation Tasks
- **CodeGenTask**: Base class for code generation tasks
- **GenAopProxy**: Generate AOP proxy classes

## Installation

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-codegen</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Examples

### Basic Java Code Generation

```java
import io.nop.codegen.java.GenJava;
import io.nop.api.core.util.SourceLocation;

// Create a new Java class generator
GenJava gen = new GenJava();

// Add imports
gen.addImport(SourceLocation.UNKNOWN_LOCATION, "java.util.List");
gen.addImport(SourceLocation.UNKNOWN_LOCATION, "java.util.ArrayList");

// Add methods
// Note: The complete Java code generation API is more complex and typically used through XCodeGenerator

// Example of method creation
// MethodBlock method = gen.addMethod(SourceLocation.UNKNOWN_LOCATION, "getItems");
```

### GraalVM Configuration Generation

```java
import io.nop.codegen.graalvm.ReflectConfigGenerator;
import io.nop.codegen.graalvm.ReflectConfig;

// Create a reflection config generator
ReflectConfigGenerator generator = new ReflectConfigGenerator();

// Add classes to reflect configuration
generator.addClass("com.example.MyClass");

// Generate the config
ReflectConfig config = generator.build();

// Serialize to JSON
String json = config.toJson();
System.out.println(json);
```

### XPL to Vue Conversion

```java
import io.nop.codegen.vue.XplToVueTransformer;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;

// Create a transformer
XplToVueTransformer transformer = new XplToVueTransformer();

// Convert XPL template to Vue component
String xpl = "<div><span xpl:text='title'></span></div>";
XNode xplNode = XNodeParser.instance().parseFromText(xpl, "xpl-template");
XNode vueNode = transformer.transformNode(xplNode);

System.out.println(vueNode.xml());
```

## Directory Structure

```
io.nop.codegen/
├── antlr/             # Antlr4 parser configuration
├── common/            # Common code generation utilities
├── graalvm/           # GraalVM configuration generation
├── initialize/        # Initialization classes
├── java/              # Java code generation
├── js/                # JavaScript code generation
├── maven/             # Maven project support
├── task/              # Code generation tasks
├── utils/             # Utility classes
└── vue/               # Vue code generation
```

## Key Classes

### Code Generation Framework
- **XCodeGenerator**: Main code generation entry point
- **AbstractGenCode**: Base class for code generators
- **CodeBlock**: Represents code blocks with indentation
- **MethodBlock**: Generates method declarations

### Java Code Generation
- **GenJava**: Java code generator
- **ImportClassBlock**: Manages Java imports
- **StaticImportBlock**: Manages Java static imports

### GraalVM Integration
- **ReflectConfigGenerator**: Generates reflection configuration for GraalVM
- **ProxyConfigGenerator**: Generates proxy configuration for GraalVM
- **ResourceConfigGenerator**: Generates resource configuration for GraalVM

### Vue Integration
- **XplToVueTransformer**: Converts XPL templates to Vue components

## Dependencies

- **nop-core**: Core platform utilities
- **nop-xlang**: XPL and XLang support
- **nop-antlr4-common**: Common Antlr4 utilities
- **nop-antlr4-tool**: Antlr4 tool integration


