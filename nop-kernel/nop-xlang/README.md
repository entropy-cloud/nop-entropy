# nop-xlang

nop-xlang is a powerful advanced expression language framework that provides a complete programming language implementation, including parsing, compilation, execution, and extension mechanisms. It is one of the core components of the nop platform, providing flexible expression calculation and script execution capabilities.

## Features

### Core Functions
- **Complete Programming Language Implementation**: Includes parser, compiler, execution engine, and type system
- **Rich Expression Syntax**: Supports conditional expressions, loops, function definitions, object creation, etc.
- **Dynamic Execution**: Supports dynamic compilation and execution of expressions
- **Template Support**: Built-in XPL template engine, supporting HTML, XML, and other output formats
- **Extension Mechanism**: Supports custom functions, tag libraries, and extension points

### Technical Highlights
- **ANTLR4 Driven**: Uses ANTLR4 as the parser generator, supporting complex syntax definitions
- **Janino Integration**: Supports compiling expressions to Java bytecode for improved execution efficiency
- **Type Inference**: Supports static type inference and dynamic type checking
- **Scope Management**: Provides flexible scope management mechanisms
- **Error Handling**: Complete error handling and location mechanisms

## Architecture Design

nop-xlang adopts a layered architecture design, mainly including the following layers:

1. **API Layer**: Provides public API interfaces, such as the XLang class
2. **Parsing Layer**: Responsible for parsing source code into Abstract Syntax Trees (AST)
3. **Compilation Layer**: Responsible for compiling AST into executable code
4. **Execution Layer**: Responsible for executing compiled code
5. **Extension Layer**: Provides various extension mechanisms, such as custom functions and tag libraries

### Core Modules

#### AST Module (`ast/`)
Contains various abstract syntax tree node types, such as:
- Expression nodes: BinaryExpression, UnaryExpression, etc.
- Statement nodes: IfStatement, ForStatement, etc.
- Type nodes: ArrayTypeNode, ObjectTypeDef, etc.

#### Compilation Module (`compile/`)
Contains compilation-related functionality, such as:
- Type inference: TypeInferenceProcessor
- Scope analysis: LexicalScopeAnalysis
- Code optimization: ExpressionOptimizer

#### Execution Module (`exec/`)
Contains various executable code implementations, such as:
- Expression execution: BinaryExecutable, LiteralExecutable, etc.
- Statement execution: BlockExecutable, IfExecutable, etc.
- Function execution: CallFuncExecutable, FunctionExecutable, etc.

#### API Module (`api/`)
Provides public API interfaces, such as:
- XLang: Core API entry class
- IXLangProvider: XLang provider interface
- XLangCompileTool: Compilation tool class

## Usage Examples

### Basic Expression Execution

```java
// Create execution context
IEvalScope scope = XLang.newEvalScope(Map.of("a", 10, "b", 20));

// Compile and execute expression
IExecutableExpression expr = XLang.newCompileTool().compileExpression("a + b * 2");
Object result = expr.execute(scope);
System.out.println(result); // Outputs 50
```

### Function Definition and Call

```java
// Compile and execute function definition and call
String code = """
function add(a, b) {
    return a + b;
}
add(10, 20);
""";

IEvalAction expr = XLang.newCompileTool().compileFullExpr(loc, code);
Object result = expr.invoke(XLang.newEvalScope());
System.out.println(result); // Outputs 30
```

### Template Parsing and Execution

```java
// Parse XPL template
IResource resource = ResourceHelper.resolve("/templates/test.xpl");
XplModel model = XLang.parseXpl(resource);

// Execute template
IEvalScope scope = XLang.newEvalScope(Map.of("name", "World"));
Object result = model.invoke(scope);
System.out.println(result); // Outputs template execution result
```

## Dependencies

Main dependencies:

- **nop-commons**: Provides common utility classes
- **nop-core**: Provides core functionality support
- **nop-xdefs**: Provides XDefinition support
- **nop-antlr4-common**: Provides ANTLR4-related tools
- **janino**: Used for dynamic Java bytecode compilation
- **jakarta.validation-api**: Used for data validation

## Directory Structure

```
nop-xlang/
├── src/
│   ├── main/
│   │   ├── java/io/nop/xlang/
│   │   │   ├── api/          # Public API interfaces
│   │   │   ├── ast/          # Abstract Syntax Tree nodes
│   │   │   ├── compile/      # Compilation-related functionality
│   │   │   ├── delta/        # Delta merging functionality
│   │   │   ├── exec/         # Execution-related implementations
│   │   │   ├── expr/         # Expression-related functionality
│   │   │   ├── feature/      # Feature expression support
│   │   │   ├── filter/       # Filter expression functionality
│   │   │   ├── functions/    # Built-in functions
│   │   │   ├── initialize/   # Initialization-related
│   │   │   ├── janino/       # Janino integration
│   │   │   ├── parse/        # Parser
│   │   │   ├── scope/        # Scope management
│   │   │   ├── script/       # Script language intergration
│   │   │   ├── utils/        # Utility classes
│   │   │   ├── xdef/         # XDefinition language
│   │   │   ├── xdsl/         # XDSL support
│   │   │   ├── xmeta/        # Metadata support
│   │   │   ├── xpath/        # XPath language
│   │   │   ├── xpkg/         # XLang Package management
│   │   │   ├── xpl/          # XPL template engine
│   │   │   └── xt/           # XT transformation language 
│   │   └── resources/        # Resource files
│   └── test/                 # Test code
├── pom.xml                   # Maven configuration file
└── README.md                 # Project documentation
```

## Core API Description

### XLang Class

XLang is the core API entry class for nop-xlang, providing the following main methods:

- `execute(IExecutableExpression expr, EvalRuntime rt)`: Executes an expression
- `newEvalScope()`: Creates a new execution scope
- `newCompileTool()`: Creates a compilation tool
- `parseXpl(IResource resource)`: Parses an XPL template
- `loadTpl(String path)`: Loads a template

### XLangCompileTool Class

Provides compilation-related functionality:

- `compileFullExpr(SourceLocation loc, String expr)`: Compiles an expression
- `compileScript(SourceLocation loc, String lang, String script)`: Compiles a script
- `compileTemplateExpr(SourceLocation loc, String template)`: Compiles a template expression

### IExecutableExpression Interface

Represents an executable expression:

- `execute(IExpressionExecutor executor, EvalRuntime rt)`: Executes the expression in the specified runtime environment

## Extension Mechanisms

nop-xlang provides multiple extension mechanisms:

### Custom Functions

You can extend built-in functions by implementing the `IFunctionProvider` interface:

```java
public class ReportFunctionProvider extends DefaultFunctionProvider {
    public static final ReportFunctionProvider INSTANCE = new ReportFunctionProvider();

    static {
        ReportFunctionProvider.INSTANCE.registerStaticFunctions(ReportFunctions.class);
    }
}
```

### Custom Tag Libraries

You can extend XPL tag libraries by creating custom `.xlib` files that define your tags:

**Example: `/test/my.xlib`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lib namespace="my" displayName="My Custom Tag Library"
     xmlns:x="/nop/schema/xdsl.xdef"
     xmlns:xpl="/nop/schema/xpl.xdef"
     xmlns:c="/nop/schema/core.xlib">

    <description>My custom tag library containing useful tags</description>

    <tags>
        <!-- Custom tag with attributes and return value -->
        <HelloTag displayName="Hello World Tag">
            <attr name="name" type="string" displayName="Name" mandatory="true">
                <description>Name to greet</description>
            </attr>
            
            <attr name="count" type="int" displayName="Count" defaultValue="1">
                <description>Number of times to greet</description>
            </attr>
            
            <return type="string">
                <description>Formatted greeting message</description>
            </return>
            
            <source>
                <c:set name="greeting" value="Hello, ${name}!"/>
                <c:if test="${count > 1}">
                    <c:set name="greeting" value="${greeting} (${count} times)"/>
                </c:if>
                <c:return value="${greeting}"/>
            </source>
        </HelloTag>
        
        <!-- Custom tag with slot -->
        <Alert displayName="Alert Box Tag" outputMode="html">
            <attr name="type" type="string" displayName="Alert Type" defaultValue="info">
                <description>Alert type: info, success, warning, error</description>
            </attr>
            
            <!-- Define a slot for content -->
            <slot name="content" mandatory="true">
                <description>Content to display in the alert</description>
            </slot>
            
            <source>
                <div class="alert alert-${type}">
                    <!-- Call the slot to render content -->
                     <c:slot slot:name="content" />
                </div>
            </source>
        </Alert>
    
    </tags>
</lib>
```

**Using the custom tag library in XPL templates:**

```xml
<!-- Option 1: Import the tag library using xpl:lib attribute -->
<my:HelloTag name="World" count="3" xpl:lib="/test/my.xlib"/>

<!-- Option 2: Import the tag library explicitly -->
<c:import lib="/test/my.xlib"/>
<my:Alert type="success">
    <content>
        Operation completed successfully!
    </content>
</my:Alert>
```

## Performance Optimization

nop-xlang provides multiple performance optimization mechanisms:

- **Compilation Cache**: Caches compiled expressions to avoid repeated compilation
- **Janino Compilation**: Supports compiling expressions to Java bytecode for improved execution efficiency
- **Code Optimization**: Provides various compile-time optimizations, such as constant folding and dead code elimination
- **Lazy Evaluation**: Supports lazy evaluation to avoid unnecessary calculations

## Summary

nop-xlang is a powerful, well-designed expression language framework that provides flexible expression calculation and script execution capabilities for the nop platform. Its layered architecture design and rich extension mechanisms make it suitable for various complex business scenarios, and it is an ideal choice for building flexible and extensible applications.

